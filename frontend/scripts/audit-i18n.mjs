import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { parse as parseSfc } from 'vue/compiler-sfc'
import zh from '../src/locales/zh-CN.js'
import vi from '../src/locales/vi-VN.js'

const require = createRequire(import.meta.resolve('vue/compiler-sfc'))
const { parse, parseExpression } = require('@babel/parser')
const { parse: parseTemplate } = require('@vue/compiler-dom')
const catalog = JSON.parse(fs.readFileSync('src/locales/vi-text.json', 'utf8'))
const patterns = JSON.parse(fs.readFileSync('src/locales/server-patterns.json', 'utf8'))
const han = /[\u3400-\u9fff]/
const errors = [], references = new Set()
const placeholders = text => [...text.matchAll(/\{(\w+)\}/g)].map(m => m[1]).sort().join(',')
const files = dir => fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? files(path.join(dir, e.name)) : [path.join(dir, e.name)])
function checkPair(source, target, location) {
  if (typeof target !== 'string' || !target.trim() || han.test(target)) errors.push(`${location}: missing Vietnamese text`)
  else if (placeholders(source) !== placeholders(target)) errors.push(`${location}: interpolation parameters differ`)
}
for (const [source, target] of Object.entries(catalog)) checkPair(source, target, `catalog: ${source}`)
for (const source of patterns) if (!Object.hasOwn(catalog, source)) errors.push(`Missing server message: ${source}`)
function checkLocales(a, b, prefix = '') {
  for (const key of new Set([...Object.keys(a), ...Object.keys(b)])) {
    const location = `${prefix}.${key}`
    if (typeof a[key] === 'string') checkPair(a[key], b[key], location)
    else if (a[key] && b[key]) checkLocales(a[key], b[key], location)
    else errors.push(`Locale key differs: ${location}`)
  }
}
checkLocales(zh, vi)

function javascript(code, file, expression = false) {
  const tree = expression ? parseExpression(code, { plugins: ['jsx'] }) : parse(code, { sourceType: 'module', plugins: ['jsx'] })
  function visit(n, ancestors = []) {
    if (!n || typeof n !== 'object') return
    const parent = ancestors.at(-1)
    if (n.type === 'CallExpression') {
      const name = n.callee.name || n.callee.property?.name
      if (['tr', 'tx'].includes(name)) {
        if (n.arguments[0]?.type === 'StringLiteral' && n.arguments[1]?.type === 'StringLiteral') checkPair(n.arguments[0].value, n.arguments[1].value, file)
        return
      }
      if (['translateText', '$tr', 't', '$t'].includes(name)) {
        if (['translateText', '$tr'].includes(name) && n.arguments[0]?.type === 'StringLiteral') {
          const source = n.arguments[0].value
          references.add(source)
          if (han.test(source) && !Object.hasOwn(catalog, source)) errors.push(`${file}: missing catalog entry ${source}`)
        }
        n.arguments.slice(1).forEach(a => visit(a, [...ancestors, n]))
        return
      }
    }
    if (n.type === 'ObjectProperty' && ['zh-CN', 'vi-VN'].includes(n.key.value)) return
    if (n.type === 'StringLiteral' && han.test(n.value)) {
      const key = parent?.type === 'ObjectProperty' && parent.key === n
      const property = parent?.type === 'ObjectProperty' && (parent.key.name || parent.key.value)
      // Canonical business values, message matching and dictionary keys must not be translated.
      const data = ancestors.some(a =>
        a.type === 'ObjectProperty' && ['unit', 'specification', 'dictValue'].includes(a.key.name || a.key.value)
        || a.type === 'AssignmentExpression' && ['unit', 'specification'].includes(a.left.property?.name || a.left.name)
        || a.type === 'VariableDeclarator' && /unit/i.test(a.id.name || '')
        || a.type === 'CallExpression' && (/^(includes|indexOf|startsWith|endsWith|replace|replaceAll|test)$/.test(a.callee.property?.name)
          || /^(changeTargetUnit|changeProjectUnit|changeRoutineUnit)$/.test(a.callee.name || '')
          || ['console', 'JSON'].includes(a.callee.object?.name)))
      const comparison = parent?.type === 'BinaryExpression' && parent.operator !== '+' || parent?.type === 'SwitchCase' && parent.test === n
      if (!key && !data && !comparison && !(property === 'value' && !file.includes('Crontab'))) errors.push(`${file}: untranslated string ${n.value}`)
      return
    }
    if (n.type === 'TemplateLiteral' && n.quasis.some(q => han.test(q.value.cooked || ''))) errors.push(`${file}: untranslated template literal`)
    for (const [key, value] of Object.entries(n)) if (!['loc', 'extra', 'comments', 'tokens'].includes(key)) {
      if (Array.isArray(value)) value.forEach(child => visit(child, [...ancestors, n]))
      else if (value?.type) visit(value, [...ancestors, n])
    }
  }
  visit(tree)
}

function template(source, file) {
  function visit(n) {
    if (n.type === 2 && han.test(n.content)) errors.push(`${file}: untranslated text ${n.content.trim()}`)
    if (n.type === 5) javascript(n.content.content, file, true)
    for (const prop of n.props || []) {
      if (prop.type === 6 && han.test(prop.value?.content || '') && !['value', 'name', 'prop', 'key', 'class', 'style', 'value-format'].includes(prop.name)) errors.push(`${file}: untranslated ${prop.name}`)
      if (prop.type === 7 && prop.exp && han.test(prop.exp.content) && !['model', 'for'].includes(prop.name)) javascript(prop.exp.content, file, true)
    }
    n.children?.forEach(visit)
  }
  visit(parseTemplate(source, { comments: true }))
}
let count = 0
for (const file of files('src')) {
  // Generated source-code templates contain Chinese developer comments, not UI copy.
  if (!/\.(vue|js|mjs)$/.test(file) || /locales|\.test\.|Messages\.js|generator[\\/](js|html)\.js/.test(file)) continue
  const source = fs.readFileSync(file, 'utf8')
  if (source.includes('\ufeff')) errors.push(`${file}: BOM inside module`)
  count++
  if (file.endsWith('.vue')) {
    const { descriptor } = parseSfc(source)
    if (descriptor.template) template(descriptor.template.content, file)
    for (const block of [descriptor.script, descriptor.scriptSetup].filter(Boolean)) javascript(block.content, file)
  } else javascript(source, file)
}
if (errors.length) {
  console.error([...new Set(errors)].join('\n'))
  process.exitCode = 1
} else console.log(`i18n audit passed: ${count} files, ${references.size} referenced messages, ${Object.keys(catalog).length} translations, ${patterns.length} server message patterns.`)
