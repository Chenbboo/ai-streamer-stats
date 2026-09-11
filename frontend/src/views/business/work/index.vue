<template>
  <div class="app-container work-page" v-loading="loading">
    <header class="work-hero">
      <div><span>MY WORK SCHEDULE</span><h1>我的安排</h1><p>只显示分配给你的工作，按今日、本周和本月查看。</p></div>
      <div class="work-hero-actions">
        <el-select v-model="selectedProjectId" class="work-project-select" filterable :disabled="!projectOptions.length" placeholder="暂无参与项目" aria-label="选择项目范围">
          <el-option :label="`全部项目（${projectOptions.length}）`" :value="ALL_PROJECTS" />
          <el-option v-for="project in projectOptions" :key="project.projectId" :label="projectOptionLabel(project)" :value="project.projectId" />
        </el-select>
        <el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <section class="schedule-bar">
      <el-radio-group v-model="period" @change="changePeriod">
        <el-radio-button value="DAY">今日安排</el-radio-button>
        <el-radio-button value="WEEK">本周安排</el-radio-button>
        <el-radio-button value="MONTH">本月安排</el-radio-button>
      </el-radio-group>
      <div class="date-tools"><el-date-picker v-model="anchorDate" type="date" value-format="YYYY-MM-DD" @change="load"/><el-button @click="goToday">回到今天</el-button></div>
    </section>

    <section class="period-summary">
      <div><span>{{ periodTitle }}</span><b>{{ data.dateFrom }}<template v-if="data.dateTo!==data.dateFrom"> 至 {{ data.dateTo }}</template></b></div>
      <div class="project-bonus-card">
        <span>项目总奖金</span>
        <div v-if="selectedBonusTotals.length" class="bonus-values"><b v-for="item in selectedBonusTotals" :key="item.currency">{{ money(item.amount) }} {{ item.currency }}</b></div>
        <small v-if="selectedBonusTotals.length">{{ selectedProjectLabel }} · 已核准 / 已确认累计，非个人实发</small>
        <small v-else>暂无参与项目</small>
      </div>
      <div><span>持续工作</span><b>{{ summary.routineCount || 0 }}</b></div>
      <div><span>一次性任务</span><b>{{ summary.taskCount || 0 }}</b></div>
      <div v-if="isToday"><span>今日已处理</span><b>{{ summary.reportedRoutineCount || 0 }} / {{ summary.routineCount || 0 }}</b></div>
    </section>

    <section class="work-grid">
      <article class="panel">
        <div class="panel-head"><div><h2>持续工作</h2><p>{{ isToday ? '完成后填写今天的实际数量。' : '查看该周期内持续执行的工作和累计完成量。' }}</p></div></div>
        <el-empty v-if="!routines.length" description="这个周期没有分配给你的持续工作" />
        <div v-for="routine in routines" :key="routine.routineId" class="work-card">
          <div class="card-top"><div><el-tag size="small" effect="plain">{{ routine.projectName }}</el-tag><span>{{ routine.initiatorName }}立项</span></div><el-tag size="small">{{ routineTargetModeLabel[routine.targetMode || 'FIXED'] }}</el-tag></div>
          <h3>{{ routine.routineName }}</h3>
          <p class="target">{{ routineTargetDescription(routine) }}</p>
          <p v-if="isToday&&routine.todayRequirement" class="note">客户要求：{{ routine.todayRequirement }}</p>
          <div class="result-line"><span>{{ isToday ? (routine.todayLeaveId ? '今日状态' : '今日完成') : '周期累计' }}</span><b>{{ isToday && routine.todayLeaveId ? '今日请假' : (isToday&&routine.targetMode==='NONE'&&routine.todayReportId?'已填写完成说明':`${isToday ? (routine.todayReportId ? routine.todayActual : '—') : (routine.periodActual || 0)} ${routine.unit}`) }}</b></div>
          <p v-if="isToday && routine.todayLeaveId" class="note">请假说明：{{ routine.todayLeaveReason || '今日无需填报' }}</p>
          <p v-if="routine.todaySummary" class="note">今日说明：{{ routine.todaySummary }}</p>
          <p v-if="routineBelowTarget(routine) && routine.todayIssueReason" class="issue">未达原因：{{ routine.todayIssueReason }}</p>
          <el-button v-if="isToday && !routine.todayLeaveId && !(routine.targetMode==='DAILY_DYNAMIC'&&!routine.todayTargetId)" type="primary" :plain="!!routine.todayReportId" @click="openRoutineReport(routine)">{{ routine.todayReportId ? '修改今日填报' : (routine.targetMode==='NONE'?'填写今日完成说明':'填报今日完成量') }}</el-button>
          <el-alert v-else-if="isToday && routine.targetMode==='DAILY_DYNAMIC'&&!routine.todayTargetId" title="负责人尚未下达今日目标，下达后才能填报。" type="warning" :closable="false" show-icon />
        </div>
      </article>

      <article class="panel">
        <div class="panel-head"><div><h2>一次性任务</h2><p>{{ isToday ? '由任务负责人本人填报今日完成情况和总进度。' : '查看该周期内分配给你的任务。' }}</p></div></div>
        <el-empty v-if="!tasks.length" description="这个周期没有分配给你的一次性任务" />
        <div v-for="task in tasks" :key="task.taskId" class="work-card task-card">
          <div class="card-top"><div><el-tag size="small" effect="plain">{{ task.projectName }}</el-tag><span>{{ task.initiatorName }}立项</span></div><el-tag size="small" :type="taskTone[task.status]">{{ taskStatusLabel[task.status] }}</el-tag></div>
          <h3>{{ task.taskName }}</h3>
          <p class="target">截止日期：{{ task.dueDate || '未设置' }}</p>
          <el-progress :percentage="task.progress || 0" :stroke-width="7" />
          <p v-if="isToday && task.todayLeaveId" class="note">请假说明：{{ task.todayLeaveReason || '今日无需填报' }}</p>
          <p v-if="task.todayTaskReportId" class="note">今日已填报：{{ task.todayProgress }}%<template v-if="task.todayCompletionSummary">，{{ task.todayCompletionSummary }}</template></p>
          <div class="task-actions">
            <el-button v-if="isToday && task.projectStatus==='ACTIVE' && !task.todayLeaveId" type="primary" :plain="!!task.todayTaskReportId" @click="openTaskReport(task)">{{ task.todayTaskReportId ? '修改今日填报' : '填报今日完成量' }}</el-button>
            <span v-else-if="isToday && task.todayLeaveId" class="task-report-tip">今日请假，无需填报</span>
            <span v-else-if="isToday" class="task-report-tip">项目执行中才能填报</span>
          </div>
        </div>
      </article>
    </section>

    <el-dialog v-model="reportDialog" :title="reportForm.reportId?'修改今日完成量':'填报今日完成量'" width="min(620px, 94vw)" append-to-body>
      <el-alert :title="`${reportForm.routineName || ''} · ${data.today || today()}`" type="info" :closable="false" show-icon />
      <el-form :model="reportForm" label-width="92px" class="report-form">
        <el-form-item v-if="reportForm.targetMode!=='NONE'" label="每日目标"><el-input :model-value="`${reportForm.todayTarget || 0} ${reportForm.unit || ''}`" disabled /></el-form-item>
        <el-form-item v-if="reportForm.targetMode!=='NONE'" label="实际完成" required><el-input-number v-model="reportForm.actualValue" :min="0" :precision="4" style="width:100%" /></el-form-item>
        <el-form-item label="今日说明" required><el-input v-model="reportForm.summary" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item v-if="needsReason" label="未达原因" required><el-input v-model="reportForm.issueReason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="成果凭证" :required="reportForm.evidenceRequired==='1'"><business-file-upload v-model="reportForm.evidenceUrls" :project-id="reportForm.projectId" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reportDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitRoutine">保存今日完成量</el-button></template>
    </el-dialog>

    <el-dialog v-model="taskReportDialog" :title="taskReportForm.reportId?'修改今日完成量':'填报今日完成量'" width="min(620px, 94vw)" append-to-body>
      <el-alert :title="`${taskReportForm.taskName || ''} · ${data.today || today()}`" type="info" :closable="false" show-icon />
      <el-form :model="taskReportForm" label-width="108px" class="report-form task-report-form">
        <el-form-item label="任务内容"><el-input :model-value="taskReportForm.taskName" disabled /></el-form-item>
        <el-form-item label="实际完成情况" required><el-input v-model="taskReportForm.completionSummary" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="请用文字说明今日实际完成的内容" /></el-form-item>
        <el-form-item label="任务进度" required><el-slider v-model="taskReportForm.progress" show-input :min="0" :max="100" :disabled="Number(taskReportForm.minimumProgress || 0) >= 100" @input="keepTaskProgress" /><small class="progress-tip">当前进度 {{ taskReportForm.minimumProgress || 0 }}%，只能向上调整。</small></el-form-item>
        <el-form-item label="成果凭证" required><business-file-upload v-model="taskReportForm.evidenceUrls" :project-id="taskReportForm.projectId" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="taskReportDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitTask">保存今日完成量</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessWorkSchedule">
import { getBusinessWorkDashboard, submitBusinessTaskReport, submitBusinessRoutineReport, saveBusinessWorkEffort } from '@/api/business/project'
import { ElMessage } from 'element-plus'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'

const router=useRouter(),route=useRoute()
const ALL_PROJECTS='ALL_PROJECTS'
const loading=ref(false),saving=ref(false),savingEffortId=ref(null),data=ref({}),period=ref('DAY'),anchorDate=ref(today()),selectedProjectId=ref(ALL_PROJECTS),reportDialog=ref(false),reportForm=ref({}),taskReportDialog=ref(false),taskReportForm=ref({})
const projectBonuses=computed(()=>data.value.projectBonuses||[])
const projectOptions=computed(()=>projectBonuses.value)
const projectMatches=item=>selectedProjectId.value===ALL_PROJECTS||String(item.projectId)===String(selectedProjectId.value)
const tasks=computed(()=>(data.value.tasks||[]).filter(projectMatches))
const routines=computed(()=>(data.value.routines||[]).filter(projectMatches))
const efforts=computed(()=>(data.value.efforts||[]).filter(projectMatches))
const summary=computed(()=>({
  taskCount:tasks.value.length,
  routineCount:routines.value.length,
  reportedRoutineCount:routines.value.filter(item=>item.todayReportId||item.todayLeaveId).length,
  plannedEffortPercent:efforts.value.reduce((sum,item)=>sum+Number(item.plannedPercent||0),0),
  actualEffortPercent:efforts.value.reduce((sum,item)=>sum+Number(item.actualPercent||0),0),
  submittedEffortCount:efforts.value.filter(item=>item.reportStatus!=='UNSUBMITTED').length
}))
const selectedProjects=computed(()=>projectBonuses.value.filter(projectMatches))
const selectedBonusTotals=computed(()=>{
  const totals=new Map()
  const add=(currency,amount)=>totals.set(currency,(totals.get(currency)||0)+Number(amount||0))
  selectedProjects.value.forEach(project=>{
    const currency=project.currency||'CNY'
    add(currency,project.totalBonus)
    if(currency!=='CNY'&&Number(project.legacyBonus||0)!==0)add('CNY',project.legacyBonus)
  })
  return [...totals.entries()].sort(([left],[right])=>left==='CNY'?-1:right==='CNY'?1:left.localeCompare(right)).map(([currency,amount])=>({currency,amount}))
})
const selectedProjectLabel=computed(()=>selectedProjectId.value===ALL_PROJECTS?`全部 ${selectedProjects.value.length} 个项目`:selectedProjects.value[0]?.projectName||'暂无参与项目')
const isToday=computed(()=>period.value==='DAY'&&data.value.dateFrom===data.value.today)
const periodTitle=computed(()=>({DAY:'今日',WEEK:'本周',MONTH:'本月'}[period.value]))
const needsReason=computed(()=>reportForm.value.targetMode!=='NONE'&&reportForm.value.actualValue!==null&&reportForm.value.actualValue!==undefined&&Number(reportForm.value.actualValue)<Number(reportForm.value.todayTarget||0))
const routineTargetModeLabel={FIXED:'固定每日目标',AUTO_TOTAL:'自动分配',DAILY_DYNAMIC:'动态日目标',NONE:'无量化'}
const taskStatusLabel={TODO:'待开始',DOING:'进行中',BLOCKED:'受阻',DONE:'已完成'}
const taskTone={TODO:'info',DOING:'primary',BLOCKED:'danger',DONE:'success'}
const effortStatusLabel={UNSUBMITTED:'按计划执行',SUBMITTED:'待负责人确认',CONFIRMED:'已确认',RETURNED:'已退回',LEAVE:'今日请假'}
const effortTone={UNSUBMITTED:'info',SUBMITTED:'warning',CONFIRMED:'success',RETURNED:'danger',LEAVE:'info'}
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const projectOptionLabel=project=>project.projectNo?`${project.projectName} · ${project.projectNo}`:project.projectName
function routineBelowTarget(routine){return routine.targetMode!=='NONE'&&!!routine.todayReportId&&Number(routine.todayActual)<Number(routine.todayTarget||0)}
function routineTargetDescription(routine){if(routine.targetMode==='NONE')return '无量化目标：只需填写今日完成说明';if(isToday.value&&routine.targetMode==='DAILY_DYNAMIC'&&!routine.todayTargetId)return '今日目标：等待负责人下达';if(isToday.value)return `今日目标：${routine.todayTarget ?? 0} ${routine.unit}`;return `周期累计：${routine.periodActual || 0} ${routine.unit}`}
function today(){return new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'})}
async function load(){loading.value=true;try{const payload=(await getBusinessWorkDashboard({period:period.value,anchorDate:anchorDate.value})).data||{};payload.efforts=(payload.efforts||[]).map(item=>({...item,actualPercent:Number(item.actualPercent||0),editing:false,_savedActualPercent:Number(item.actualPercent||0),_savedDeviationReason:item.deviationReason||''}));data.value=payload;const projects=payload.projectBonuses||[];const requested=projects.find(project=>String(project.projectId)===String(route.query.projectId));if(requested)selectedProjectId.value=requested.projectId;else if(selectedProjectId.value!==ALL_PROJECTS&&!projects.some(project=>String(project.projectId)===String(selectedProjectId.value)))selectedProjectId.value=ALL_PROJECTS}finally{loading.value=false}}
watch(()=>route.query.projectId,value=>{const requested=projectOptions.value.find(project=>String(project.projectId)===String(value));selectedProjectId.value=requested?.projectId??ALL_PROJECTS})
function changePeriod(){load()}
function goToday(){anchorDate.value=today();load()}
function openRoutineReport(routine){reportForm.value={reportId:routine.todayReportId||null,routineId:routine.routineId,projectId:routine.projectId,bizDate:data.value.today,routineName:routine.routineName,frequency:routine.frequency,targetMode:routine.targetMode||'FIXED',todayTarget:routine.todayTarget,actualValue:routine.todayReportId?Number(routine.todayActual):null,unit:routine.unit,summary:routine.todaySummary||'',issueReason:routine.todayIssueReason||'',evidenceUrls:routine.todayEvidenceUrls||'',evidenceRequired:routine.evidenceRequired,version:null};reportDialog.value=true}
async function submitRoutine(){
  const form=reportForm.value
  if(form.targetMode!=='NONE'&&(form.actualValue===null||form.actualValue===undefined||Number(form.actualValue)<0))return ElMessage.warning('请填写实际完成量')
  if(!form.summary?.trim())return ElMessage.warning('请填写今日完成说明')
  if(needsReason.value&&!form.issueReason?.trim())return ElMessage.warning('未达到每日目标时请填写原因')
  if(form.evidenceRequired==='1'&&!form.evidenceUrls)return ElMessage.warning('该工作要求上传成果凭证')
  form.actualValue=form.targetMode==='NONE'?0:form.actualValue
  form.issueReason=needsReason.value?form.issueReason.trim():null
  saving.value=true
  try{
    const response=await submitBusinessRoutineReport(form)
    const saved=response?.data||{}
    const routine=routines.value.find(item=>Number(item.routineId)===Number(form.routineId))
    const wasReported=!!routine?.todayReportId
    const previousActual=Number(routine?.todayActual||0)
    const currentActual=Number(saved.actualValue??form.actualValue)
    if(routine){
      routine.todayReportId=saved.reportId||routine.todayReportId||`saved-${form.routineId}`
      routine.todayActual=currentActual
      routine.todaySummary=saved.summary??form.summary
      routine.todayIssueReason=saved.issueReason??form.issueReason
      routine.todayEvidenceUrls=saved.evidenceUrls??form.evidenceUrls
      routine.periodActual=Number(routine.periodActual||0)+(wasReported?currentActual-previousActual:currentActual)
    }
    if(!wasReported&&summary.value)summary.value.reportedRoutineCount=Number(summary.value.reportedRoutineCount||0)+1
    reportDialog.value=false
    await nextTick()
    ElMessage({type:'success',message:'提交成功，今日完成量已更新',duration:3000,showClose:true})
  }finally{saving.value=false}
}
function openTaskReport(task){const minimumProgress=Number(task.progress||0);taskReportForm.value={reportId:task.todayTaskReportId||null,taskId:task.taskId,projectId:task.projectId,bizDate:data.value.today,taskName:task.taskName,minimumProgress,progress:Math.max(minimumProgress,Number(task.todayProgress??minimumProgress)),completionSummary:task.todayCompletionSummary||'',evidenceUrls:task.todayEvidenceUrls||''};taskReportDialog.value=true}
function keepTaskProgress(value){const minimum=Number(taskReportForm.value.minimumProgress||0);if(Number(value)<minimum)taskReportForm.value.progress=minimum}
async function submitTask(){const form=taskReportForm.value;if(!form.completionSummary?.trim())return ElMessage.warning('请填写实际完成情况');if(form.progress===null||form.progress===undefined||Number(form.progress)<Number(form.minimumProgress||0)||Number(form.progress)>100)return ElMessage.warning(`任务进度只能增加，不能低于 ${form.minimumProgress||0}%`);if(!form.evidenceUrls)return ElMessage.warning('请上传成果凭证');saving.value=true;try{await submitBusinessTaskReport(form);taskReportDialog.value=false;await load();ElMessage.success('今日任务完成量已保存')}finally{saving.value=false}}
function beginEffortAdjustment(item){item._savedActualPercent=Number(item.actualPercent||0);item._savedDeviationReason=item.deviationReason||'';item.editing=true}
function cancelEffortAdjustment(item){item.actualPercent=item._savedActualPercent;item.deviationReason=item._savedDeviationReason;item.editing=false}
async function saveEffort(item){if(item.reportStatus==='LEAVE')return ElMessage.info('今日已登记请假，无需填报投入');if(item.reportStatus==='UNSUBMITTED'&&Number(item.actualPercent)===Number(item.plannedPercent)){item.editing=false;return ElMessage.info('实际投入与计划一致，无需申报')}if(Number(item.actualPercent)!==Number(item.plannedPercent)&&!item.deviationReason?.trim())return ElMessage.warning('实际投入与计划不一致时请填写偏差原因');savingEffortId.value=item.projectId;try{await saveBusinessWorkEffort({projectId:item.projectId,bizDate:anchorDate.value,actualPercent:item.actualPercent,deviationReason:Number(item.actualPercent)===Number(item.plannedPercent)?'':item.deviationReason||''});ElMessage.success('投入偏差已提交负责人确认');await load()}finally{savingEffortId.value=null}}
load()
useBusinessRefreshOnReactivated(load)
</script>

<style scoped>
.work-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f6f8;color:#172335}.work-hero{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:25px 28px;border-radius:16px;background:linear-gradient(120deg,#173b59,#1d6d70);color:#fff}.work-hero span{font-size:11px;letter-spacing:.17em;color:#6de0da}.work-hero h1{margin:5px 0;font-size:28px}.work-hero p{margin:0;color:#c1d4de}.work-hero-actions{display:flex;align-items:center;gap:10px}.work-project-select{width:320px}.schedule-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:16px 0;padding:14px;border:1px solid #dfe6eb;border-radius:12px;background:#fff}.date-tools{display:flex;gap:8px}.period-summary{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(0,1.45fr) repeat(4,minmax(0,1fr));gap:10px;margin-bottom:14px}.period-summary>div{box-sizing:border-box;min-width:0;min-height:112px;padding:16px 18px;border:1px solid #dfe6eb;border-radius:12px;background:#fff}.period-summary span,.period-summary b{display:block}.period-summary span{color:#7d8997}.period-summary b{margin-top:7px;font-size:19px}.project-bonus-card small{display:block;overflow:hidden;margin-top:4px;color:#98a2ad;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.project-bonus-card b{color:#167268}.work-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.panel{min-width:0;padding:18px;border:1px solid #dfe6eb;border-radius:13px;background:#fff}.panel-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.panel-head h2{margin:0;font-size:18px}.panel-head p{margin:5px 0 12px;color:#84919f;font-size:12px}.effort-panel{margin-bottom:14px}.effort-panel>.panel-head>strong{color:#167268;font-size:18px}.effort-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.effort-card{padding:15px;border:1px solid #dce8e5;border-radius:11px;background:#f8fbfa}.effort-values{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:14px 0}.effort-values span{color:#73827e}.effort-values b{color:#1d3f3a}.effort-default,.effort-result{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 12px;border-radius:8px;background:#eef6f4;color:#667b76;font-size:13px}.effort-result{align-items:flex-start;flex-direction:column}.effort-result p{margin:0;color:#536a64;line-height:1.6}.effort-result .el-button{align-self:stretch}.effort-editor{display:flex;flex-direction:column;gap:10px}.effort-editor-value,.effort-editor-actions{display:flex;align-items:center;justify-content:space-between;gap:10px}.effort-editor-value span{color:#73827e}.effort-editor-actions{justify-content:flex-end}.work-card{margin-top:10px;padding:15px;border:1px solid #e3e8ed;border-radius:11px;background:#fbfcfd}.card-top,.card-top>div,.result-line,.task-actions{display:flex;align-items:center}.card-top{justify-content:space-between;gap:10px}.card-top>div{min-width:0;gap:8px}.card-top span{color:#8793a0;font-size:12px}.work-card h3{margin:13px 0 7px}.target,.note,.issue{margin:5px 0;color:#788694;font-size:13px}.result-line{justify-content:space-between;margin:13px 0;padding:11px;border-radius:8px;background:#eff7f5}.result-line span{color:#708078}.result-line b{font-size:17px}.issue{color:#c84550}.task-card :deep(.el-progress){margin:13px 0}.task-actions{justify-content:flex-end;gap:8px}.task-actions .el-button{margin:0}.report-form{margin-top:18px}@media(max-width:1400px){.period-summary{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:900px){.period-summary{grid-template-columns:repeat(2,minmax(0,1fr))}.work-grid,.effort-grid{grid-template-columns:1fr}}@media(max-width:640px){.work-page{padding:12px}.work-hero{align-items:flex-start;flex-direction:column;padding:20px}.work-hero-actions{display:grid;width:100%;grid-template-columns:1fr auto}.work-project-select{width:100%}.schedule-bar{align-items:stretch;flex-direction:column}.schedule-bar :deep(.el-radio-group){display:grid;grid-template-columns:repeat(3,1fr)}.schedule-bar :deep(.el-radio-button__inner){width:100%;padding:9px 5px}.date-tools{display:grid;grid-template-columns:1fr auto}.period-summary{grid-template-columns:1fr 1fr}.period-summary>div{min-height:100px;padding:13px}.period-summary b{font-size:15px}.effort-panel>.panel-head{flex-direction:column}.card-top{align-items:flex-start}.card-top>div{align-items:flex-start;flex-direction:column;gap:5px}.effort-values,.effort-default{align-items:stretch;flex-direction:column}.effort-editor-value :deep(.el-input-number){width:100%}}
.effort-result p.effort-returned{color:#c84550}
.task-report-tip{color:#9aa4af;font-size:12px}
.task-report-form :deep(.el-slider){padding:0 12px}
.task-report-form :deep(.el-slider__runway.show-input){margin-right:88px}
.progress-tip{display:block;width:100%;margin-top:6px;color:#909399;font-size:12px}
.bonus-values{display:flex;flex-wrap:wrap;gap:0 12px}
</style>
