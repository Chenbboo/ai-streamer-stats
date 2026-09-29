import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { ref, computed } from 'vue'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'
import { monthlyProgressPercent, projectProgressLimit, progressSubmissionIssue } from './projectProgress.js'
import { reportedProjectProgress } from './ownerSettlement.js'

const view = path => {
  const { descriptor, errors } = parse(readFileSync(new URL(path, import.meta.url), 'utf8'))
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: path })
  return { descriptor, script }
}
const owner = view('../views/business/owner/index.vue')
const extract = (name, dependencies) => {
  for (const node of owner.script.scriptSetupAst) {
    let source
    if (node.type === 'FunctionDeclaration' && node.id.name === name) source = owner.descriptor.scriptSetup.content.slice(node.start, node.end)
    if (node.type === 'VariableDeclaration') {
      const declaration = node.declarations.find(item => item.id.name === name)
      if (declaration) source = owner.descriptor.scriptSetup.content.slice(declaration.init.start, declaration.init.end)
    }
    if (source) return new Function(...Object.keys(dependencies), `return (${source})`)(...Object.values(dependencies))
  }
  throw new Error(`Missing ${name}`)
}
const fixture = () => {
  const state = { projectProgressForm: ref({}), saving: ref(false), projectProgressDialog: ref(true), selectedProjectId: ref(13), warnings: [], calls: [] }
  state.submit = extract('submitProjectProgress', { ...state, progressSubmissionIssue, projectProgressLimit, translateText: value => value,
    ElMessage: { warning: value => state.warnings.push(value), success: () => {} },
    submitBusinessProjectProgressReport: async value => state.calls.push(value), load: async () => {} })
  return state
}
test('owner form refuses missing standard, bad ranges and decreases without calling the API', async () => {
  const state = fixture()
  for (const input of [
    { progress: 50 }, { completionStandard: 'STANDARD', progress: 101 },
    { completionStandard: 'EXCESS', progress: 301 }, { completionStandard: 'EXCESS', progress: 1.5 },
    { completionStandard: 'EXCESS', progress: 150, minimumProgress: 200 }
  ]) {
    state.projectProgressForm.value = { completionSummary: '成果', ...input }
    await state.submit()
  }
  assert.equal(state.warnings.length, 5)
  assert.equal(state.calls.length, 0)
  assert.equal(state.saving.value, false)
})
test('owner saves a 300% excess report and keeps its completion standard in the payload', async () => {
  const state = fixture()
  state.projectProgressForm.value = { projectId: 13, completionStandard: 'EXCESS', progress: 300, minimumProgress: 200, completionSummary: '成果', evidenceText: ' 凭证 ' }
  await state.submit()
  assert.equal(state.calls.length, 1)
  assert.equal(state.calls[0].completionStandard, 'EXCESS')
  assert.equal(state.calls[0].progress, 300)
  assert.equal(state.calls[0].evidenceText, '凭证')
  assert.equal(state.projectProgressDialog.value, false)
})
test('today edit restores classification, and a fresh month requires a new selection', () => {
  const project = ref({ projectId: 13, projectName: '项目', progressReportId: 9, progressCompletionStandard: 'EXCESS' })
  const todayProjectProgress = ref({ reportId: 9, progress: 220, completionStandard: 'EXCESS' })
  const projectProgressForm = ref({})
  const dependencies = { project, todayProjectProgress, projectProgressForm, projectProgress: ref(220), projectProgressDialog: ref(false), accounting: ref({ bizDate: '2026-09-29' }), today: () => '2026-09-29' }
  const open = extract('openProjectProgressReport', dependencies)
  open()
  assert.equal(projectProgressForm.value.completionStandard, 'EXCESS')
  assert.equal(projectProgressForm.value.progress, 220)
  const limit = extract('projectProgressFormLimit', { projectProgressForm, projectProgressLimit, computed })
  assert.equal(limit.value, 300)
  project.value = { projectId: 13, projectName: '项目' }; todayProjectProgress.value = null; dependencies.projectProgress.value = 0
  open()
  assert.equal(projectProgressForm.value.completionStandard, '')
  assert.equal(projectProgressForm.value.progress, 0)
  assert.equal(limit.value, 100)
})
test('owner summaries preserve excess values only within their matching calendar month', () => {
  const project = { progressReportId: 9, progressCompletionStandard: 'EXCESS', progressPercent: 220, progressBizDate: '2026-09-29' }
  assert.equal(reportedProjectProgress(project, '2026-09'), 220)
  assert.equal(reportedProjectProgress(project, '2026-10'), null)
  assert.equal(monthlyProgressPercent({ ...project, status: 'ACTIVE' }), 220)
})
test('all affected real Vue scripts and templates compile with shared scaled progress bars', () => {
  for (const path of ['../components/BusinessMonthlyProgress/index.vue','../components/BusinessProjectState/index.vue',
    '../components/BusinessProjectProgress/index.vue','../components/BusinessProjectProgress/ReportHistory.vue',
    '../views/business/owner/index.vue','../views/business/owner/components/OwnerProjectSettlement.vue',
    '../views/business/project/index.vue','../views/business/boss/index.vue']) {
    const { descriptor, script } = view(path)
    const result = compileTemplate({ source: descriptor.template.content, filename: path, id: path, compilerOptions: { bindingMetadata: script.bindings } })
    assert.deepEqual(result.errors, [], path)
    if (!path.includes('BusinessMonthlyProgress') && !path.includes('BusinessProjectState')) assert.match(descriptor.template.content, /BusinessMonthlyProgress/)
  }
  assert.match(owner.descriptor.template.content, /v-model="projectProgressForm.completionStandard"/)
  assert.match(owner.descriptor.template.content, /:max="projectProgressFormLimit"/)
})
