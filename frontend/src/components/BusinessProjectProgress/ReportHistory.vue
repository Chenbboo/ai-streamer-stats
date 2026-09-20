<template>
  <el-empty v-if="!reports?.length" description="尚无进度汇报" />
  <div v-else class="report-browser">
    <aside class="history-panel" aria-label="汇报历史列表">
      <div class="history-heading"><h3>汇报记录</h3><span>{{ reports.length }} 条</span></div>
      <el-input v-model="keyword" clearable placeholder="搜索汇报人或内容" aria-label="搜索汇报记录" prefix-icon="Search" />
      <p class="history-caption">按提交时间倒序，点击查看详情</p>
      <div class="history-list">
        <button v-for="report in pageReports" :key="report.reportId" type="button" class="history-item" :class="{ active: String(report.reportId) === activeId }" :aria-pressed="String(report.reportId) === activeId" @click="activeId = String(report.reportId)">
          <span class="history-item-top"><b>第 {{ report.version }} 次汇报</b><span class="progress-number">{{ report.progress }}%</span></span>
          <span v-if="linkProject" class="history-project">{{ report.projectNameSnapshot || '子项目' }}</span>
          <span class="history-meta">{{ report.submittedUserName || '未记录汇报人' }} · {{ report.createTime || '未记录时间' }}</span>
          <span class="history-excerpt">{{ report.completionSummary || '未填写阶段成果' }}</span>
          <span v-if="String(report.reportId) === String(orderedReports[0]?.reportId)" class="latest-mark">最新汇报</span>
        </button>
      </div>
      <el-empty v-if="!filteredReports.length" :image-size="42" description="没有匹配的汇报" />
      <el-pagination v-if="filteredReports.length > pageSize" v-model:current-page="page" :page-size="pageSize" :total="filteredReports.length" layout="prev, pager, next" :pager-count="5" small class="history-pagination" />
    </aside>
    <article v-if="activeReport" :key="activeId" class="report-detail" aria-label="汇报详情">
      <header class="detail-heading">
        <div><span class="detail-eyebrow">{{ linkProject ? activeReport.projectNameSnapshot || '子项目汇报' : '项目进度汇报' }}</span><h3>第 {{ activeReport.version }} 次汇报<el-tag v-if="activeId === String(orderedReports[0]?.reportId)" size="small" effect="plain">最新</el-tag></h3></div>
        <div class="detail-progress"><span>本次汇报进度</span><strong>{{ activeReport.progress }}<small>%</small></strong></div>
      </header>
      <el-progress :percentage="Number(activeReport.progress) || 0" :show-text="false" :stroke-width="7" color="#328b80" />
      <div class="detail-meta"><span>汇报人 <b>{{ activeReport.submittedUserName || '未记录' }}</b></span><span>提交时间 <b>{{ activeReport.createTime || '未记录' }}</b></span></div>
      <el-button v-if="linkProject" link type="primary" @click="$emit('open-project', { projectId: activeReport.projectId, reportId: activeReport.reportId })">查看该子项目全部汇报 →</el-button>
      <section class="content-section"><h4>阶段成果</h4><p class="summary-text">{{ activeReport.completionSummary || '本次未填写阶段成果' }}</p></section>
      <div class="followup-grid">
        <section class="content-section"><h4>问题与风险</h4><p :class="{ muted: !activeReport.issuesRisks }">{{ activeReport.issuesRisks || '本次未填写' }}</p></section>
        <section class="content-section"><h4>下一步计划</h4><p :class="{ muted: !activeReport.nextPlan }">{{ activeReport.nextPlan || '本次未填写' }}</p></section>
      </div>
      <section class="content-section report-evidence">
        <div class="evidence-heading"><h4>成果凭证</h4><span v-if="activeReport.evidenceUrls">点击图片放大，点击文件名查看附件</span></div>
        <p v-if="activeReport.evidenceText" class="summary-text">{{ activeReport.evidenceText }}</p>
        <BusinessFileUpload v-if="activeReport.evidenceUrls" :model-value="activeReport.evidenceUrls" :project-id="activeReport.projectId" disabled :drag="false" :is-show-tip="false" inline-document-preview />
        <p v-else-if="!activeReport.evidenceText" class="muted">本次汇报未填写成果凭证</p>
      </section>
      <el-collapse class="snapshot-sections">
        <el-collapse-item v-if="activeReport.syncTasks || activeReport.syncRoutines" title="本次同步的工作进度" name="synced">
          <ProgressSnapshot :snapshot="activeSnapshot" :show-tasks="!!activeReport.syncTasks" :show-routines="!!activeReport.syncRoutines" />
        </el-collapse-item>
        <el-collapse-item title="提交时的完整项目档案" name="archive"><ProgressSnapshot :snapshot="activeSnapshot" archive-details /></el-collapse-item>
      </el-collapse>
      <p class="archive-note">历史汇报按提交时内容保留；需要纠正时，请提交新的汇报版本。</p>
    </article>
    <el-empty v-else class="empty-detail" description="请搜索或选择一条汇报" />
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { readProgressSnapshot } from '@/utils/projectProgress'
import ProgressSnapshot from './ProgressSnapshot.vue'
import BusinessFileUpload from '@/components/BusinessFileUpload/index.vue'
const props = defineProps({ reports: Array, selectedReportId: [Number, String], linkProject: Boolean })
defineEmits(['open-project'])
const activeId = ref(''), keyword = ref(''), page = ref(1)
const pageSize = 5
const orderedReports = computed(() => [...(props.reports || [])].sort((a, b) => String(b.createTime || '').localeCompare(String(a.createTime || '')) || Number(b.reportId) - Number(a.reportId)))
const filteredReports = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return orderedReports.value.filter(report => !query || [report.projectNameSnapshot, report.submittedUserName, report.completionSummary, report.evidenceText, report.issuesRisks, report.nextPlan, report.createTime].some(value => String(value || '').toLowerCase().includes(query)))
})
const pageReports = computed(() => filteredReports.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const activeReport = computed(() => filteredReports.value.find(report => String(report.reportId) === activeId.value))
const activeSnapshot = computed(() => activeReport.value ? readProgressSnapshot(activeReport.value) : null)
watch([orderedReports, () => props.selectedReportId], ([reports, selectedId]) => {
  keyword.value = ''
  const requested = reports.findIndex(report => String(report.reportId) === String(selectedId))
  const index = requested >= 0 ? requested : 0
  activeId.value = reports[index] ? String(reports[index].reportId) : ''
  page.value = Math.floor(index / pageSize) + 1
}, { immediate: true })
watch(keyword, () => { page.value = 1 })
watch(pageReports, reports => {
  if (!reports.some(report => String(report.reportId) === activeId.value)) activeId.value = reports[0] ? String(reports[0].reportId) : ''
})
</script>
<style scoped>
.report-browser{display:grid;grid-template-columns:280px minmax(0,1fr);gap:22px;align-items:start;margin-top:20px;color:#24384b}
.history-panel{padding:16px;background:#f5f7fa;border:1px solid #e6ebf0;border-radius:12px;min-width:0}
.history-heading,.history-item-top,.detail-heading,.evidence-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}
.history-heading{margin-bottom:14px}.history-heading h3{margin:0;font-size:15px}.history-heading>span,.history-caption{font-size:12px;color:#718194}.history-caption{margin:12px 0}
.history-list{display:flex;flex-direction:column;gap:9px}.history-item{display:block;width:100%;padding:14px;text-align:left;color:inherit;font:inherit;background:white;border:1px solid #e2e8ef;border-radius:9px;cursor:pointer;transition:background .15s,border-color .15s}
.history-item:hover{border-color:#83b6e8}.history-item.active{border-color:#409eff;background:#eef6ff;box-shadow:inset 3px 0 #409eff}.history-item:focus-visible{outline:2px solid #409eff;outline-offset:2px}
.history-item-top b{font-size:14px}.progress-number{font-size:18px;font-weight:700;color:#237d72}.history-project{display:block;margin-top:7px;font-size:13px;font-weight:600;overflow-wrap:anywhere}.history-meta{display:block;margin-top:8px;color:#718194;font-size:12px;line-height:1.7}
.history-excerpt{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;margin-top:8px;font-size:13px;line-height:1.6;overflow-wrap:anywhere}.latest-mark{display:inline-block;margin-top:9px;font-size:11px;color:#2876c8}.history-pagination{justify-content:center;margin-top:15px}
.report-detail{min-width:0;padding:22px;border:1px solid #e3eaf0;border-radius:12px;background:white}.detail-heading{margin-bottom:14px}.detail-eyebrow{font-size:12px;color:#718194;overflow-wrap:anywhere}.detail-heading h3{display:flex;align-items:center;gap:10px;margin:7px 0 0;font-size:21px}.detail-progress{text-align:right;flex-shrink:0}.detail-progress>span{display:block;font-size:12px;color:#718194}.detail-progress strong{display:block;color:#237d72;font-size:32px;line-height:1.4}.detail-progress small{font-size:16px;margin-left:3px}
.detail-meta{display:flex;flex-wrap:wrap;gap:8px 24px;margin:16px 0 20px;color:#718194;font-size:12px;line-height:1.8}.detail-meta b{margin-left:6px;color:#405166;font-weight:500}
.content-section{margin-top:20px}.content-section h4{margin:0 0 10px;font-size:14px;color:#33485e}.content-section p{margin:0;font-size:14px;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.85}.summary-text{padding:15px 17px;border-left:3px solid #6eaa9f;border-radius:0 8px 8px 0;background:#f3f8f7}.followup-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.followup-grid .content-section{padding:14px;background:#f8fafc;border:1px solid #edf1f5;border-radius:8px}.muted{color:#8995a4}
.evidence-heading{flex-wrap:wrap;margin-bottom:10px}.evidence-heading h4{margin:0}.evidence-heading span{font-size:12px;color:#718194}.report-evidence :deep(.upload-file-list){grid-template-columns:repeat(auto-fill,minmax(140px,160px))}.report-evidence :deep(.upload-file-card__preview){height:108px}.report-evidence :deep(.upload-file-card__name){white-space:normal;overflow-wrap:anywhere;line-height:1.5;text-align:left}
.snapshot-sections{margin-top:22px;border-top-color:#e8edf3}.snapshot-sections :deep(.el-collapse-item__header){font-size:13px;color:#52677d}.archive-note{font-size:12px;color:#8995a4;line-height:1.7;margin:14px 0 0}.empty-detail{min-width:0}
@media(max-width:760px){.report-browser{grid-template-columns:1fr;gap:14px}.history-panel{padding:12px}.history-list{flex-direction:row;overflow-x:auto;gap:8px;padding-bottom:5px}.history-item{flex:0 0 230px;padding:12px}.history-excerpt{-webkit-line-clamp:1}.report-detail{padding:16px}.followup-grid{grid-template-columns:1fr;gap:0}.detail-heading h3{font-size:18px}.detail-progress strong{font-size:28px}.detail-meta{gap:4px 16px}}
</style>
