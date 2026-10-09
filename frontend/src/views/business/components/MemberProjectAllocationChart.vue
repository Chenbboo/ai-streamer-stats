<template>
  <section class="allocation-chart" v-loading="loading" :aria-label="$tr('成员跨项目投入分布')">
    <header class="allocation-chart__header">
      <div><h2>{{ $tr('成员跨项目投入分布') }}</h2><p>{{ $tr('点击饼图或项目，查看成员投入与跨项目分配详情。') }}</p></div>
      <div class="allocation-chart__actions"><span>{{ $tr('当前投入') }} · {{ effectiveDate }}</span><el-button icon="Refresh" :loading="loading" @click="load">{{ $tr('刷新') }}</el-button></div>
    </header>
    <div class="allocation-chart__toolbar">
      <div class="allocation-chart__stats">
        <span>{{ $tr('成员') }} <b>{{ rows.length }}</b></span>
        <span>{{ $tr('跨项目成员') }} <b>{{ crossProjectCount }}</b></span>
        <span v-if="attentionCount" class="allocation-chart__attention">{{ $tr('需核对投入') }} <b>{{ attentionCount }}</b></span>
      </div>
      <div class="allocation-chart__filters">
        <el-input v-model="query" clearable :placeholder="$tr('搜索成员或项目')" :aria-label="$tr('搜索成员或项目')" />
        <el-checkbox v-model="crossOnly">{{ $tr('只看跨项目') }}</el-checkbox>
      </div>
    </div>
    <el-alert v-if="failedCount" :title="$tr('{0} 名成员的投入暂未加载，已保留其参项信息，请刷新重试。', [failedCount])" type="warning" :closable="false" show-icon />
    <div v-if="activeProject" class="allocation-chart__focus">
      <span><i :style="{background: activeProject.color}"></i>{{ $tr('项目联动') }} <b>{{ activeProject.projectName }}</b><small>{{ $tr('涉及 {0} 名成员', [linkedMembers.length]) }}</small></span>
      <el-button link type="primary" @click="activeProjectId = null">{{ $tr('清除高亮') }}</el-button>
    </div>
    <el-empty v-if="!loading && !visibleRows.length" :description="rows.length ? $tr('没有匹配的成员') : $tr('暂无当前参与项目的成员')" :image-size="70" />
    <div class="allocation-chart__grid">
      <article v-for="member in visibleRows" :key="member.userId" :class="['allocation-chart__member', {'is-linked': isLinked(member), 'is-muted': activeProject && !isLinked(member)}]">
        <header class="allocation-chart__card-header">
          <button type="button" class="allocation-chart__person" @click="openMember(member)">
            <span class="allocation-chart__avatar">{{ member.userName.slice(0, 1) }}</span>
            <span class="allocation-chart__identity"><b>{{ member.userName }}</b><small>{{ member.accountName || $tr('{0} 个项目', [projectCount(member)]) }}</small></span>
          </button>
          <el-tag size="small" effect="light" :type="statusTone(member)">{{ statusLabel(member) }}</el-tag>
        </header>
        <div class="allocation-chart__card-body">
          <MemberAllocationDonut :member="member" :active-project-id="activeProjectId" @select="projectId => openMember(member, projectId)" @browse="openMember(member)" />
          <div class="allocation-chart__projects">
            <span class="allocation-chart__list-label">{{ $tr('参与项目') }} <small>{{ projectCount(member) }}</small></span>
            <button v-for="project in cardProjects(member)" :key="project.projectId" type="button" :class="['allocation-chart__project', {'is-active': String(project.projectId) === String(activeProjectId)}]" :title="project.projectName" @click="openMember(member, project.projectId)">
              <i :style="{background: project.color || '#b7c2d1'}"></i>
              <span>{{ project.projectName }}<small v-if="project.external">{{ $tr('其他负责人项目') }}</small></span>
              <b>{{ member.failed ? '—' : projectPercent(project) }}</b>
            </button>
            <span v-if="!projectCount(member)" class="allocation-chart__unavailable">{{ $tr('暂无当前有效项目投入') }}</span>
            <button v-if="projectCount(member) > cardProjects(member).length" type="button" class="allocation-chart__more" @click="openMember(member)">{{ $tr('另有 {0} 个参项 · 查看全部', [projectCount(member) - cardProjects(member).length]) }}</button>
          </div>
        </div>
        <footer class="allocation-chart__card-footer">
          <span v-if="member.failed" class="allocation-chart__attention">{{ $tr('投入暂未加载') }}</span>
          <span v-else-if="member.incomplete" class="allocation-chart__attention">{{ $tr('{0} 项投入待完善', [member.projects.filter(project => project.percent === null).length]) }}</span>
          <span v-else>{{ $tr('{0} 个项目', [projectCount(member)]) }}<small v-if="member.crossProject"> · {{ $tr('跨项目') }}</small></span>
          <div class="allocation-chart__card-actions"><el-button v-if="canAdjust(member)" link type="primary" size="small" :icon="EditPen" :disabled="allocationOpening" @click="adjustMember(member)">{{ adjustmentLabel(member) }}</el-button><button type="button" @click="openMember(member)">{{ $tr('查看详情') }} <span aria-hidden="true">↗</span></button></div>
        </footer>
        <span v-if="member.pendingRequest" class="allocation-chart__pending-dot" :title="$tr('有投入调整待确认；图表展示当前生效比例。')">{{ $tr('调整待确认') }}</span>
      </article>
    </div>
    <footer class="allocation-chart__note">{{ $tr('每张饼图对应一位成员；数值为实际投入比例，灰色表示未分配或信息待完善。超额分配时保留实际合计。') }}</footer>

    <el-drawer v-model="detailVisible" :title="$tr('成员投入详情')" size="500px" append-to-body class="member-allocation-drawer">
      <div v-if="selectedMember" class="allocation-detail">
        <header class="allocation-detail__identity">
          <span class="allocation-chart__avatar">{{ selectedMember.userName.slice(0, 1) }}</span>
          <div><h3>{{ selectedMember.userName }}</h3><p>{{ selectedMember.accountName }}<span v-if="selectedMember.accountName"> · </span>{{ $tr('{0} 个项目', [projectCount(selectedMember)]) }}</p></div>
          <el-tag :type="statusTone(selectedMember)" size="small">{{ statusLabel(selectedMember) }}</el-tag>
        </header>
        <section v-if="selectedProject" class="allocation-detail__selected" :style="{'--project-color': selectedProject.color || '#b7c2d1'}">
          <div class="allocation-detail__eyebrow">{{ $tr('项目投入比例') }}<el-tag v-if="selectedProject.external" size="small" type="info">{{ $tr('其他负责人项目') }}</el-tag></div>
          <h4>{{ selectedProject.projectName }}</h4>
          <strong>{{ selectedMember.failed ? '—' : projectPercent(selectedProject) }}</strong>
          <dl>
            <div v-if="selectedProject.ownerName"><dt>{{ $tr('项目负责人') }}</dt><dd>{{ selectedProject.ownerName }}</dd></div>
            <div><dt>{{ $tr('查看日期') }}</dt><dd>{{ effectiveDate }}</dd></div>
            <div v-if="selectedProject.allocationEffectiveFrom || selectedProject.effectiveFrom"><dt>{{ $tr('投入生效日期') }}</dt><dd>{{ dateText(selectedProject.allocationEffectiveFrom || selectedProject.effectiveFrom) }}</dd></div>
          </dl>
          <span v-if="selectedProject.autoRedistributed" class="allocation-detail__muted">{{ $tr('自动分配') }}</span>
          <el-button v-if="!selectedProject.external && !selectedMember.failed" link type="primary" @click="viewProject">{{ $tr('查看项目') }} ↗</el-button>
        </section>
        <section>
          <div class="allocation-detail__section-heading"><h4>{{ $tr('全部参项与投入') }}</h4><b>{{ selectedMember.failed || !selectedMember.projects.some(project => project.percent !== null) ? '—' : percent(selectedMember.total) }}</b></div>
          <button v-for="project in detailProjects" :key="project.projectId" type="button" :class="['allocation-detail__project', {'is-active': String(project.projectId) === String(selectedProjectId)}]" @click="chooseProject(project.projectId)">
            <i :style="{background: project.color || '#b7c2d1'}"></i><span>{{ project.projectName }}<small>{{ project.external ? $tr('其他负责人项目') : $tr('负责项目') }}<template v-if="project.autoRedistributed"> · {{ $tr('自动分配') }}</template></small></span><b>{{ selectedMember.failed ? '—' : projectPercent(project) }}</b>
          </button>
          <p v-if="selectedMember.failed" class="allocation-detail__muted">{{ $tr('投入暂未加载') }}</p>
          <p v-if="selectedMember.pendingRequest" class="allocation-detail__warning">{{ $tr('有投入调整待确认；图表展示当前生效比例。') }}</p>
          <p v-if="selectedMember.incomplete" class="allocation-detail__warning">{{ $tr('未配置或待确认的投入不计入已知合计。') }}</p>
        </section>
        <section v-if="selectedProject && linkedMembers.length">
          <div class="allocation-detail__section-heading"><h4>{{ $tr('同项目成员') }}</h4><span>{{ $tr('当前列表') }}</span></div>
          <div class="allocation-detail__members">
            <button v-for="member in linkedMembers" :key="member.userId" type="button" :class="{'is-active': String(member.userId) === String(selectedMemberId)}" @click="openMember(member, selectedProjectId)">
              <span>{{ member.userName.slice(0, 1) }}</span><b>{{ member.userName }}</b><small>{{ projectPercent(member.projects.find(project => String(project.projectId) === String(selectedProjectId))) }}</small>
            </button>
          </div>
        </section>
      </div>
      <template #footer><el-button v-if="selectedMember && canAdjust(selectedMember)" class="allocation-detail__adjust" type="primary" size="large" :icon="EditPen" :loading="allocationOpening" @click="adjustMember(selectedMember)">{{ adjustmentLabel(selectedMember) }}</el-button></template>
    </el-drawer>
    <BusinessProjectWorkPanel ref="allocationPanel" :members="editorMembers" :can-manage="hasAllocationPermission" :show-costs="false" @changed="allocationChanged" />
  </section>
</template>

<script setup>
import { computed, ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { EditPen } from '@element-plus/icons-vue'
import { getBusinessStaffAllocationWorkspace } from '@/api/business/project'
import { allocationMembers, loadMemberAllocations } from '@/utils/memberProjectAllocations'
import { translateText } from '@/locales/translate'
import MemberAllocationDonut from './MemberAllocationDonut.vue'
import BusinessProjectWorkPanel from '@/components/BusinessProjectWorkPanel/index.vue'
import useUserStore from '@/store/modules/user'

const props = defineProps({ workspaces: { type: Array, default: () => [] } })
const emit = defineEmits(['select-project', 'changed'])
const rows = ref([]), loading = ref(false), query = ref(''), crossOnly = ref(false), effectiveDate = ref('')
const detailVisible = ref(false), selectedMemberId = ref(null), selectedProjectId = ref(null), activeProjectId = ref(null)
const user = useUserStore(), allocationPanel = ref(null), allocationOpening = ref(false)
const hasAllocationPermission = computed(() => user.permissions.includes('*:*:*') || user.permissions.includes('business:project:allocation'))
// The workspace endpoint has already checked ownership; write permission is required in addition.
const canAdjust = member => hasAllocationPermission.value && !member.failed && member.projects.length > 0
const adjustmentLabel = member => translateText(member.pendingRequest ? '查看调整' : '调整投入')
const editorMembers = computed(() => rows.value.filter(canAdjust).map(member => ({ userId: member.userId, userNameSnapshot: member.userName, accountName: member.accountName, memberRole: member.memberRole, status: '0' })))
let sequence = 0
const percent = value => Number(value).toFixed(2) + '%'
const dateText = value => String(value).slice(0, 10)
const projectPercent = project => !project || project.percent === null || project.percent === undefined ? translateText(project?.pending ? '待确认' : '未配置') : percent(project.percent)
const projectCount = member => (member.failed ? member.participatingProjects : member.projects).length
const crossProjectCount = computed(() => rows.value.filter(row => row.crossProject).length)
const failedCount = computed(() => rows.value.filter(row => row.failed).length)
const attentionCount = computed(() => rows.value.filter(row => row.failed || !['full', 'empty'].includes(row.status)).length)
const visibleRows = computed(() => {
  const search = query.value.trim().toLowerCase()
  return rows.value.filter(row => (!crossOnly.value || row.crossProject) && (!search || [row.userName, row.accountName, ...(row.failed ? row.participatingProjects : row.projects).map(project => project.projectName)].some(value => String(value || '').toLowerCase().includes(search))))
})
const selectedMember = computed(() => rows.value.find(member => String(member.userId) === String(selectedMemberId.value)))
const detailProjects = computed(() => selectedMember.value ? selectedMember.value.failed ? selectedMember.value.participatingProjects : selectedMember.value.projects : [])
const selectedProject = computed(() => detailProjects.value.find(project => String(project.projectId) === String(selectedProjectId.value)))
const activeProject = computed(() => rows.value.flatMap(member => member.projects).find(project => String(project.projectId) === String(activeProjectId.value)))
const linkedMembers = computed(() => rows.value.filter(member => member.projects.some(project => String(project.projectId) === String(activeProjectId.value))))
const isLinked = member => activeProjectId.value != null && member.projects.some(project => String(project.projectId) === String(activeProjectId.value))
const cardProjects = member => {
  if (member.failed) return member.participatingProjects.slice(0, 2)
  const positive = member.projects.filter(project => project.percent > 0).sort((a, b) => b.percent - a.percent)
  return positive.length ? positive.slice(0, 3) : member.projects.slice(0, 2)
}
const statusLabel = member => member.failed ? translateText('暂未加载') : member.status === 'empty' ? translateText('无有效投入') : member.status === 'incomplete' ? translateText('待完善 / 待确认') : member.status === 'over' ? translateText('超过 100%') : member.status === 'under' ? translateText('未满 100%') : translateText('已分配 100%')
const statusTone = member => member.failed || member.status === 'empty' ? 'info' : member.status === 'full' ? 'success' : member.status === 'over' ? 'danger' : 'warning'

function openMember(member, projectId = null) {
  selectedMemberId.value = member.userId
  const candidates = member.failed ? member.participatingProjects : member.projects
  selectedProjectId.value = projectId ?? candidates.find(project => project.percent > 0)?.projectId ?? candidates[0]?.projectId ?? null
  activeProjectId.value = selectedProjectId.value
  detailVisible.value = true
}
function chooseProject(projectId) { selectedProjectId.value = projectId; activeProjectId.value = projectId }
function viewProject() { const projectId = selectedProject.value?.projectId; detailVisible.value = false; if (projectId != null) emit('select-project', projectId) }
async function adjustMember(member) {
  if (!canAdjust(member) || allocationOpening.value) return
  allocationOpening.value = true
  detailVisible.value = false
  try { await nextTick(); await allocationPanel.value.openAllocation(member.userId, effectiveDate.value) }
  catch { /* The request interceptor displays the failure; keep the page available for retry. */ }
  finally { allocationOpening.value = false }
}
async function allocationChanged() { await load(); emit('changed') }

async function load() {
  const request = ++sequence
  detailVisible.value = false
  loading.value = true
  const now = new Date()
  effectiveDate.value = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0')
  const date = effectiveDate.value
  const members = allocationMembers(props.workspaces, date)
  const projectIds = props.workspaces.map(workspace => workspace.project?.projectId)
  const result = await loadMemberAllocations(members, projectIds, async userId => {
    const response = await getBusinessStaffAllocationWorkspace({ userId, effectiveDate: date }, { silentError: true })
    if (!response.data || !Array.isArray(response.data.projects)) throw new Error(translateText('投入暂未加载'))
    return response.data
  }, () => request === sequence)
  if (request !== sequence) return
  rows.value = result
  if (!activeProject.value) activeProjectId.value = null
  loading.value = false
}
watch(() => props.workspaces, load, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>

<style scoped>
.allocation-chart{container-type:inline-size;margin:0 0 20px;padding:26px;background:var(--el-bg-color);border:1px solid var(--el-border-color-lighter);border-radius:16px;color:var(--el-text-color-primary)}
.allocation-chart__header,.allocation-chart__toolbar,.allocation-chart__actions,.allocation-chart__filters,.allocation-chart__stats{display:flex;align-items:center;justify-content:space-between;gap:18px;flex-wrap:wrap}
.allocation-chart__header h2{font-size:18px;margin:0 0 8px;font-weight:600}.allocation-chart__header p,.allocation-chart__actions>span{font-size:13px;color:var(--el-text-color-secondary);margin:0;line-height:1.7}
.allocation-chart__toolbar{margin:24px 0 20px}.allocation-chart__stats{gap:20px;font-size:13px;color:var(--el-text-color-secondary)}.allocation-chart__stats b{font-size:20px;color:var(--el-text-color-primary);margin-left:6px}.allocation-chart__filters .el-input{width:230px}.allocation-chart__attention{color:var(--el-color-warning)!important}
.allocation-chart__grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.allocation-chart__member{position:relative;min-width:0;border:1px solid var(--el-border-color-lighter);border-radius:14px;padding:18px 18px 0;background:linear-gradient(145deg,var(--el-bg-color) 70%,var(--el-fill-color-extra-light));transition:border-color .2s,box-shadow .2s,opacity .2s}.allocation-chart__member:hover{border-color:var(--el-color-primary-light-7);box-shadow:0 8px 24px rgba(37,70,113,.055)}.allocation-chart__member.is-linked{border-color:var(--el-color-primary-light-5);box-shadow:0 0 0 1px var(--el-color-primary-light-9)}.allocation-chart__member.is-muted{opacity:.55}
.allocation-chart__card-header{display:flex;align-items:center;justify-content:space-between;gap:10px}.allocation-chart__person{display:flex;align-items:center;gap:11px;min-width:0;padding:0;border:0;background:transparent;text-align:left;cursor:pointer;color:inherit}.allocation-chart__avatar{width:40px;height:40px;flex:0 0 40px;display:flex;align-items:center;justify-content:center;border-radius:12px;background:linear-gradient(140deg,var(--el-color-primary-light-9),var(--el-color-primary-light-8));color:var(--el-color-primary);font-size:18px;font-weight:600}.allocation-chart__identity{display:flex;flex-direction:column;min-width:0;gap:4px}.allocation-chart__identity b{font-size:15px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.allocation-chart__identity small{font-size:12px;color:var(--el-text-color-secondary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.allocation-chart__card-header .el-tag{flex-shrink:0;border:0;font-size:11px}
.allocation-chart__card-body{display:flex;align-items:center;gap:8px;min-height:184px;margin:8px -6px}.allocation-chart__projects{flex:1;min-width:0;display:flex;flex-direction:column;gap:3px}.allocation-chart__list-label{font-size:11px;color:var(--el-text-color-secondary);margin-bottom:4px}.allocation-chart__list-label small{margin-left:4px;font-size:11px}.allocation-chart__project{display:flex;align-items:center;gap:7px;width:100%;padding:6px;border:0;border-radius:7px;background:transparent;text-align:left;color:var(--el-text-color-primary);cursor:pointer;transition:background .18s}.allocation-chart__project:hover,.allocation-chart__project.is-active{background:var(--el-color-primary-light-9)}.allocation-chart__project i,.allocation-detail__project i,.allocation-chart__focus i{width:7px;height:7px;border-radius:50%;flex:0 0 7px}.allocation-chart__project>span{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.allocation-chart__project small{display:block;margin-top:3px;font-size:10px;color:var(--el-text-color-secondary)}.allocation-chart__project>b{font-size:11px;font-weight:600;white-space:nowrap;font-variant-numeric:tabular-nums}.allocation-chart__more{padding:6px 0 0 6px;border:0;background:transparent;color:var(--el-color-primary);font-size:11px;cursor:pointer;text-align:left}.allocation-chart__unavailable{font-size:12px;color:var(--el-text-color-secondary);line-height:1.7}
.allocation-chart__card-footer{display:flex;align-items:center;justify-content:space-between;gap:6px;border-top:1px solid var(--el-border-color-extra-light);min-height:43px;font-size:11px;color:var(--el-text-color-secondary)}.allocation-chart__card-footer small{font-size:11px}.allocation-chart__card-footer button{border:0;background:transparent;color:var(--el-color-primary);font-size:11px;cursor:pointer;padding:6px 0}.allocation-chart__card-footer button span{margin-left:5px}.allocation-chart__pending-dot{display:block;font-size:10px;color:var(--el-color-warning);padding-bottom:10px}.allocation-chart__note{margin-top:18px;font-size:12px;color:var(--el-text-color-secondary);line-height:1.8}
.allocation-chart__focus{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 14px;margin-bottom:16px;background:var(--el-color-primary-light-9);border-radius:9px;font-size:12px}.allocation-chart__focus>span{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.allocation-chart__focus small{color:var(--el-text-color-secondary)}
.allocation-detail{display:flex;flex-direction:column;gap:26px;color:var(--el-text-color-primary)}.allocation-detail__identity{display:flex;align-items:center;gap:12px}.allocation-detail__identity>div{flex:1;min-width:0}.allocation-detail__identity h3{margin:0 0 5px;font-size:20px;overflow-wrap:anywhere}.allocation-detail__identity p{margin:0;font-size:12px;color:var(--el-text-color-secondary)}.allocation-detail__selected{padding:22px;border:1px solid var(--el-border-color-lighter);border-left:4px solid var(--project-color);border-radius:12px;background:linear-gradient(135deg,var(--el-color-primary-light-9),var(--el-bg-color))}.allocation-detail__eyebrow{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:12px;color:var(--el-text-color-secondary)}.allocation-detail__selected h4{margin:16px 0 8px;font-size:17px;overflow-wrap:anywhere}.allocation-detail__selected>strong{font-size:34px;letter-spacing:-1px;color:var(--project-color);font-variant-numeric:tabular-nums}.allocation-detail__selected dl{margin:20px 0 12px;display:flex;flex-direction:column;gap:12px;font-size:12px}.allocation-detail__selected dl>div{display:flex;justify-content:space-between;gap:16px}.allocation-detail__selected dt{color:var(--el-text-color-secondary)}.allocation-detail__selected dd{margin:0;text-align:right}.allocation-detail__selected .el-button{display:block;margin-top:12px}
.allocation-detail__section-heading{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:12px}.allocation-detail__section-heading h4{margin:0;font-size:14px}.allocation-detail__section-heading>span{font-size:11px;color:var(--el-text-color-secondary)}.allocation-detail__section-heading>b{font-size:14px;font-variant-numeric:tabular-nums}.allocation-detail__project{display:flex;align-items:center;gap:10px;width:100%;padding:13px 10px;border:1px solid transparent;border-radius:9px;margin-top:4px;background:var(--el-fill-color-extra-light);color:inherit;text-align:left;cursor:pointer}.allocation-detail__project.is-active{border-color:var(--el-color-primary-light-7);background:var(--el-color-primary-light-9)}.allocation-detail__project>span{flex:1;min-width:0;font-size:13px;overflow-wrap:anywhere}.allocation-detail__project small{display:block;margin-top:5px;font-size:11px;color:var(--el-text-color-secondary)}.allocation-detail__project>b{font-size:13px;white-space:nowrap}.allocation-detail__muted{color:var(--el-text-color-secondary);font-size:12px;line-height:1.7}.allocation-detail__warning{font-size:12px;line-height:1.7;color:var(--el-color-warning);padding:10px 12px;border-radius:8px;background:var(--el-color-warning-light-9)}
.allocation-detail__members{display:flex;flex-wrap:wrap;gap:8px}.allocation-detail__members button{display:flex;align-items:center;gap:7px;padding:8px 10px;border:1px solid var(--el-border-color-lighter);background:var(--el-bg-color);border-radius:9px;cursor:pointer;color:inherit}.allocation-detail__members button.is-active{border-color:var(--el-color-primary-light-5);background:var(--el-color-primary-light-9)}.allocation-detail__members button>span{width:23px;height:23px;border-radius:7px;background:var(--el-fill-color-light);color:var(--el-color-primary);display:grid;place-items:center;font-size:11px}.allocation-detail__members b{font-size:12px;font-weight:500}.allocation-detail__members small{font-size:11px;color:var(--el-text-color-secondary)}
button:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:3px}
.allocation-chart__card-actions{display:flex;align-items:center;gap:14px;flex-shrink:0}.allocation-chart__card-actions .el-button{font-size:11px;padding:6px 0}.allocation-detail__adjust{width:100%}
@container(max-width:1100px){.allocation-chart__grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@container(max-width:670px){.allocation-chart__grid{grid-template-columns:minmax(0,1fr)}.allocation-chart__card-body{gap:14px}.allocation-chart__filters{width:100%}.allocation-chart__filters .el-input{width:100%}.allocation-chart__toolbar{gap:12px}}
@media(max-width:600px){.allocation-chart{padding:18px}.allocation-chart__header{align-items:flex-start}.allocation-chart__stats{gap:16px}.allocation-chart__actions{gap:12px}.allocation-chart__member{padding:16px 16px 0}.allocation-chart__card-body{gap:6px}}
</style>
<style>
.member-allocation-drawer{max-width:100vw;border-radius:18px 0 0 18px}.member-allocation-drawer .el-drawer__header{margin-bottom:0;padding:22px 24px 18px;border-bottom:1px solid var(--el-border-color-lighter)}.member-allocation-drawer .el-drawer__body{padding:24px}
@media(max-width:600px){.member-allocation-drawer .el-drawer__body{padding:20px}}
</style>
