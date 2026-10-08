import vietnamese from './vi-text.json' with { type: 'json' }
import serverPatterns from './server-patterns.json' with { type: 'json' }

/** Translate fixed product copy only. Parameters are business data and stay intact. */
export function translateCopy(source, locale, params = []) {
  if (typeof source !== 'string') return source
  const text = locale === 'vi-VN' ? (vietnamese[source] ?? source) : source
  return text.replace(/\{(\w+)\}/g, (match, key) => params[key] == null ? match : String(params[key]))
}

const escapeRegExp = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const patterns = serverPatterns.map(source => {
  const keys = [...source.matchAll(/\{(\d+)\}/g)].map(match => match[1])
  return { source, keys, regex: new RegExp('^' + source.split(/\{\d+\}/).map(escapeRegExp).join('([\\s\\S]+?)') + '$') }
})

/** Only response messages are matched; response records and submitted values are never rewritten. */
export function translateMessage(source, locale) {
  if (locale !== 'vi-VN' || typeof source !== 'string') return source
  if (Object.hasOwn(vietnamese, source)) return vietnamese[source]
  for (const pattern of patterns) {
    const match = pattern.regex.exec(source)
    if (match) return translateCopy(pattern.source, locale, Object.fromEntries(pattern.keys.map((key, index) => [key, match[index + 1]])))
  }
  return source
}

const chineseByVietnamese = new Map(Object.entries(vietnamese).map(([zh, vi]) => [vi, zh]))
export function sourceText(text) { return chineseByVietnamese.get(text) ?? text }
