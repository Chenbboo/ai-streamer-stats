<template>
  <div class="app-container work-page" v-loading="loading">
    <header class="work-hero">
      <div><span>MY WORK SCHEDULE</span><h1>{{ $tr("我的安排") }}</h1><p>{{ $tr("只显示分配给你的工作，按今日、本周和本月查看。") }}</p></div>
      <div class="work-hero-actions">
        <el-select v-model="selectedProjectId" class="work-project-select" filterable :placeholder="$tr(&quot;所有任务&quot;)" :aria-label="$tr(&quot;选择项目范围&quot;)">
          <el-option :label="$tr(&quot;所有任务&quot;)" :value="ALL_PROJECTS" />
          <el-option v-for="project in projectOptions" :key="project.projectId" :label="projectOptionLabel(project)" :value="project.projectId" />
        </el-select>
        <el-button icon="Refresh" :loading="loading" @click="load">{{ $tr("刷新") }}</el-button>
      </div>
    </header>

    <section class="schedule-bar">
      <el-radio-group v-model="period" @change="changePeriod">
        <el-radio-button value="DAY">{{ $tr("今日安排") }}</el-radio-button>
        <el-radio-button value="WEEK">{{ $tr("本周安排") }}</el-radio-button>
        <el-radio-button value="MONTH">{{ $tr("本月安排") }}</el-radio-button>
      </el-radio-group>
      <div class="date-tools"><el-date-picker v-model="anchorDate" type="date" value-format="YYYY-MM-DD" @change="load"/><el-button @click="goToday">{{ $tr("回到今天") }}</el-button></div>
    </section>

    <section class="period-summary">
      <div><span>{{ periodTitle }}</span><b>{{ data.dateFrom }}<template v-if="data.dateTo!==data.dateFrom">{{ $tr(" 至 {0}", [data.dateTo]) }}</template></b></div>
      <div class="project-bonus-card">
        <span>{{ $tr("项目总奖金") }}</span>
        <div v-if="selectedBonusTotals.length" class="bonus-values"><b v-for="item in selectedBonusTotals" :key="item.currency">{{ money(item.amount) }} {{ item.currency }}</b></div>
        <small v-if="selectedBonusTotals.length">{{ $tr("{0} · 已核准 / 已确认累计，非个人实发", [selectedProjectLabel]) }}</small>
        <small v-else>{{ $tr("暂无参与项目") }}</small>
      </div>
      <div><span>{{ $tr("持续工作") }}</span><b>{{ summary.routineCount || 0 }}</b></div>
      <div><span>{{ $tr("一次性任务") }}</span><b>{{ summary.taskCount || 0 }}</b></div>
      <div v-if="isToday"><span>{{ $tr("今日已处理") }}</span><b>{{ summary.reportedRoutineCount || 0 }} / {{ summary.routineCount || 0 }}</b></div>
    </section>

    <section v-if="period==='DAY'" class="panel effort-panel">
      <div class="panel-head"><div><h2>{{ $tr("我的项目投入") }}</h2><p>{{ $tr("查看负责人确定的投入分配，无需每天重复填报；需要调整时请联系项目负责人。") }}</p></div><strong>{{ $tr("计划合计 {0}%", [summary.plannedEffortPercent || 0]) }}</strong></div>
      <el-empty v-if="!efforts.length" :description="$tr(&quot;负责人尚未为你设置该日期的项目投入权重&quot;)" />
      <div v-else class="effort-grid">
        <article v-for="item in efforts" :key="item.allocationId" class="effort-card">
          <div class="card-top"><div><el-tag size="small" effect="plain">{{ item.projectName }}</el-tag><span>{{ $tr("{0}立项", [item.initiatorName]) }}</span></div><el-tag size="small" :type="effortTone[item.reportStatus]">{{ effortStatusLabel[item.reportStatus] }}</el-tag></div>
          <div class="effort-values"><span>{{ item.autoRedistributed ? $tr("项目结束后自动分配") : $tr("负责人设置") }} <b>{{ item.plannedPercent }}%</b></span><span v-if="item.costPolicyVersion!=='MEMBER_DAYS_V1'">{{ $tr("当天实际 ") }}<b>{{ item.actualPercent }}%</b></span><span v-else>{{ $tr("{0}负责", [item.ownerName]) }}</span></div>
          <el-alert v-if="item.costPolicyVersion==='MEMBER_DAYS_V1'" :title="item.confirmationStatus==='PENDING' ? $tr(&quot;人员投入待确认，请联系项目负责人协商分配&quot;) : $tr(&quot;按已确定的投入比例计算，无需重复填报&quot;)" :type="item.confirmationStatus==='PENDING'?'warning':'info'" :closable="false" />
          <el-alert v-else-if="item.reportStatus==='LEAVE'" :title="$tr(&quot;考勤显示当天无需计算投入{0}&quot;, [item.leaveReason ? `：${item.leaveReason}` : ''])" type="info" :closable="false" show-icon />
          <div v-else-if="item.reportStatus==='UNSUBMITTED' && !item.editing" class="effort-default">
            <span>{{ $tr("默认按 {0}% 计算，无需重复填报", [item.plannedPercent]) }}</span>
            <el-button type="primary" plain @click="beginEffortAdjustment(item)">{{ $tr("实际投入有变化") }}</el-button>
          </div>
          <div v-else-if="item.reportStatus==='CONFIRMED'" class="effort-result">
            <p v-if="Number(item.actualPercent)!==Number(item.plannedPercent)">{{ $tr("偏差原因：{0}", [item.deviationReason || $tr("未填写")]) }}</p>
            <span>{{ $tr("负责人已确认，当天投入已锁定") }}</span>
          </div>
          <div v-else-if="!item.editing" class="effort-result">
            <p v-if="item.reportStatus==='RETURNED'" class="effort-returned">{{ $tr("退回原因：{0}", [item.reviewComment || $tr("请修改后重新提交")]) }}</p>
            <p v-if="Number(item.actualPercent)!==Number(item.plannedPercent)">{{ $tr("偏差原因：{0}", [item.deviationReason || $tr("未填写")]) }}</p>
            <span v-if="item.reportStatus==='SUBMITTED'">{{ $tr("已提交，等待负责人确认") }}</span>
            <el-button type="primary" plain @click="beginEffortAdjustment(item)">{{ item.reportStatus==='RETURNED' ? $tr("修改后重新提交") : $tr("修改偏差申报") }}</el-button>
          </div>
          <div v-else class="effort-editor">
            <div class="effort-editor-value"><span>{{ $tr("当天实际投入") }}</span><el-input-number v-model="item.actualPercent" :min="0" :max="100" :precision="1" /></div>
            <el-input v-if="Number(item.actualPercent)!==Number(item.plannedPercent)" v-model="item.deviationReason" type="textarea" :rows="2" maxlength="500" show-word-limit :placeholder="$tr(&quot;请说明实际投入与负责人计划不同的原因&quot;)" />
            <div class="effort-editor-actions"><el-button @click="cancelEffortAdjustment(item)">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="savingEffortId===item.projectId" @click="saveEffort(item)">{{ $tr("提交负责人确认") }}</el-button></div>
          </div>
        </article>
      </div>
    </section>

    <section class="work-grid">
      <article class="panel">
        <div class="panel-head"><div><h2>{{ $tr("持续工作") }}</h2><p>{{ isToday ? $tr("完成后填写今天的实际数量。") : $tr("查看该周期内持续执行的工作和累计完成量。") }}</p></div></div>
        <el-empty v-if="!routines.length" :description="$tr(&quot;这个周期没有分配给你的持续工作&quot;)" />
        <div v-for="routine in routines" :key="routine.routineId" class="work-card">
          <div class="card-top"><div><el-tag size="small" effect="plain">{{ routine.projectName }}</el-tag><span>{{ $tr("{0}立项", [routine.initiatorName]) }}</span></div><el-tag size="small">{{ routineTargetModeLabel[routine.targetMode || 'FIXED'] }}</el-tag></div>
          <h3>{{ routine.routineName }}</h3>
          <p class="target">{{ routineTargetDescription(routine) }}</p>
          <p v-if="isToday&&routine.todayRequirement" class="note">{{ $tr("客户要求：{0}", [routine.todayRequirement]) }}</p>
          <div class="result-line"><span>{{ isToday ? (routine.todayLeaveId ? $tr("今日状态") : $tr("今日完成")) : $tr("周期累计") }}</span><b>{{ isToday && routine.todayLeaveId ? $tr("今日请假") : (isToday&&routine.targetMode==='NONE'&&routine.todayReportId?$tr("已填写完成说明"):`${isToday ? (routine.todayReportId ? routine.todayActual : '—') : (routine.periodActual || 0)} ${$tr(routine.unit)}`) }}</b></div>
          <p v-if="isToday && routine.todayLeaveId" class="note">{{ $tr("请假说明：{0}", [routine.todayLeaveReason || $tr("今日无需填报")]) }}</p>
          <p v-if="routine.todaySummary" class="note">{{ $tr("今日说明：{0}", [routine.todaySummary]) }}</p>
          <p v-if="routineBelowTarget(routine) && routine.todayIssueReason" class="issue">{{ $tr("未达原因：{0}", [routine.todayIssueReason]) }}</p>
          <el-button v-if="isToday && !routine.todayLeaveId && !(routine.targetMode==='DAILY_DYNAMIC'&&!routine.todayTargetId)" type="primary" :plain="!!routine.todayReportId" @click="openRoutineReport(routine)">{{ routine.todayReportId ? $tr("修改今日填报") : (routine.targetMode==='NONE'?$tr("填写今日完成说明"):$tr("填报今日完成量")) }}</el-button>
          <el-alert v-else-if="isToday && routine.targetMode==='DAILY_DYNAMIC'&&!routine.todayTargetId" :title="$tr(&quot;负责人尚未下达今日目标，下达后才能填报。&quot;)" type="warning" :closable="false" show-icon />
        </div>
      </article>

      <article class="panel">
        <div class="panel-head"><div><h2>{{ $tr("一次性任务") }}</h2><p>{{ isToday ? $tr("由任务负责人本人填报今日完成情况和总进度。") : $tr("查看该周期内分配给你的任务。") }}</p></div></div>
        <el-empty v-if="!tasks.length" :description="$tr(&quot;这个周期没有分配给你的一次性任务&quot;)" />
        <div v-for="task in tasks" :key="task.taskId" class="work-card task-card">
          <div class="card-top"><div><el-tag size="small" effect="plain">{{ task.projectName }}</el-tag><span>{{ $tr("{0}立项", [task.initiatorName]) }}</span></div><el-tag size="small" :type="taskTone[task.status]">{{ taskStatusLabel[task.status] }}</el-tag></div>
          <h3>{{ task.taskName }}</h3>
          <p class="target">{{ $tr("截止日期：{0}", [task.dueDate || $tr("未设置")]) }}</p>
          <el-progress :percentage="task.progress || 0" :stroke-width="7" />
          <p v-if="isToday && task.todayLeaveId" class="note">{{ $tr("请假说明：{0}", [task.todayLeaveReason || $tr("今日无需填报")]) }}</p>
          <p v-if="task.todayTaskReportId" class="note">{{ $tr("今日已填报：{0}%", [task.todayProgress]) }}<template v-if="task.todayCompletionSummary">，{{ task.todayCompletionSummary }}</template></p>
          <div class="task-actions">
            <el-button v-if="isToday && task.projectStatus==='ACTIVE' && !task.todayLeaveId" type="primary" :plain="!!task.todayTaskReportId" @click="openTaskReport(task)">{{ task.todayTaskReportId ? $tr("修改今日填报") : $tr("填报今日完成量") }}</el-button>
            <span v-else-if="isToday && task.todayLeaveId" class="task-report-tip">{{ $tr("今日请假，无需填报") }}</span>
            <span v-else-if="isToday" class="task-report-tip">{{ $tr("项目执行中才能填报") }}</span>
          </div>
        </div>
      </article>
    </section>

    <el-dialog v-model="reportDialog" :title="reportForm.reportId?$tr(&quot;修改今日完成量&quot;):$tr(&quot;填报今日完成量&quot;)" width="min(620px, 94vw)" append-to-body>
      <el-alert :title="`${reportForm.routineName || ''} · ${data.today || today()}`" type="info" :closable="false" show-icon />
      <el-form :model="reportForm" label-width="92px" class="report-form">
        <el-form-item v-if="reportForm.targetMode!=='NONE'" :label="$tr(&quot;每日目标&quot;)"><el-input :model-value="`${reportForm.todayTarget || 0} ${reportForm.unit || ''}`" disabled /></el-form-item>
        <el-form-item v-if="reportForm.targetMode!=='NONE'" :label="$tr(&quot;实际完成&quot;)" required><el-input-number v-model="reportForm.actualValue" :min="0" :precision="4" style="width:100%" /></el-form-item>
        <el-form-item :label="$tr(&quot;今日说明&quot;)" required><el-input v-model="reportForm.summary" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item v-if="needsReason" :label="$tr(&quot;未达原因&quot;)" required><el-input v-model="reportForm.issueReason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item :label="$tr(&quot;成果凭证（选填）&quot;)"><business-file-upload v-model="reportForm.evidenceUrls" :project-id="reportForm.projectId" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reportDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="submitRoutine">{{ $tr("保存今日完成量") }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="taskReportDialog" :title="taskReportForm.reportId?$tr(&quot;修改今日完成量&quot;):$tr(&quot;填报今日完成量&quot;)" width="min(620px, 94vw)" append-to-body>
      <el-alert :title="`${taskReportForm.taskName || ''} · ${data.today || today()}`" type="info" :closable="false" show-icon />
      <el-form :model="taskReportForm" label-width="108px" class="report-form task-report-form">
        <el-form-item :label="$tr(&quot;任务内容&quot;)"><el-input :model-value="taskReportForm.taskName" disabled /></el-form-item>
        <el-form-item :label="$tr(&quot;实际完成情况&quot;)" required><el-input v-model="taskReportForm.completionSummary" type="textarea" :rows="4" maxlength="1000" show-word-limit :placeholder="$tr(&quot;请用文字说明今日实际完成的内容&quot;)" /></el-form-item>
        <el-form-item :label="$tr(&quot;任务进度&quot;)" required><el-slider v-model="taskReportForm.progress" show-input :min="0" :max="100" :disabled="Number(taskReportForm.minimumProgress || 0) >= 100" @input="keepTaskProgress" /><small class="progress-tip">{{ $tr("当前进度 {0}%，只能向上调整。", [taskReportForm.minimumProgress || 0]) }}</small></el-form-item>
        <el-form-item :label="$tr(&quot;成果凭证（选填）&quot;)"><business-file-upload v-model="taskReportForm.evidenceUrls" :project-id="taskReportForm.projectId" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="taskReportDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="submitTask">{{ $tr("保存今日完成量") }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessWorkSchedule">
import { translateText } from '@/locales/translate'

import { getBusinessWorkDashboard, submitBusinessTaskReport, submitBusinessRoutineReport, saveBusinessWorkEffort } from '@/api/business/project'
import { ElMessage } from 'element-plus'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'

const router=useRouter(),route=useRoute()
const ALL_PROJECTS='ALL_PROJECTS'
const loading=ref(false),saving=ref(false),savingEffortId=ref(null),data=ref({}),period=ref('DAY'),anchorDate=ref(today()),selectedProjectId=ref(route.query.projectId??ALL_PROJECTS),reportDialog=ref(false),reportForm=ref({}),taskReportDialog=ref(false),taskReportForm=ref({})
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
const selectedProjectLabel=computed(()=>selectedProjectId.value===ALL_PROJECTS?translateText("全部 {0} 个项目", [selectedProjects.value.length]):selectedProjects.value[0]?.projectName||translateText("暂无参与项目"))
const isToday=computed(()=>period.value==='DAY'&&data.value.dateFrom===data.value.today)
const periodTitle=computed(()=>({DAY:translateText("今日"),WEEK:translateText("本周"),MONTH:translateText("本月")}[period.value]))
const needsReason=computed(()=>reportForm.value.targetMode!=='NONE'&&reportForm.value.actualValue!==null&&reportForm.value.actualValue!==undefined&&Number(reportForm.value.actualValue)<Number(reportForm.value.todayTarget||0))
const routineTargetModeLabel={FIXED:translateText("固定每日目标"),AUTO_TOTAL:translateText("自动分配"),DAILY_DYNAMIC:translateText("动态日目标"),NONE:translateText("无量化")}
const taskStatusLabel={TODO:translateText("待开始"),DOING:translateText("进行中"),BLOCKED:translateText("受阻"),DONE:translateText("已完成")}
const taskTone={TODO:'info',DOING:'primary',BLOCKED:'danger',DONE:'success'}
const effortStatusLabel={UNSUBMITTED:translateText("按计划执行"),SUBMITTED:translateText("待负责人确认"),CONFIRMED:translateText("已确认"),RETURNED:translateText("已退回"),LEAVE:translateText("考勤不计费")}
const effortTone={UNSUBMITTED:'info',SUBMITTED:'warning',CONFIRMED:'success',RETURNED:'danger',LEAVE:'info'}
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const projectOptionLabel=project=>project.projectNo?`${project.projectName} · ${project.projectNo}`:project.projectName
function routineBelowTarget(routine){return routine.targetMode!=='NONE'&&!!routine.todayReportId&&Number(routine.todayActual)<Number(routine.todayTarget||0)}
function routineTargetDescription(routine){if(routine.targetMode==='NONE')return translateText("无量化目标：只需填写今日完成说明");if(isToday.value&&routine.targetMode==='DAILY_DYNAMIC'&&!routine.todayTargetId)return translateText("今日目标：等待负责人下达");if(isToday.value)return translateText("今日目标：{0} {1}", [routine.todayTarget ?? 0, translateText(routine.unit)]);return translateText("周期累计：{0} {1}", [routine.periodActual || 0, translateText(routine.unit)])}
function today(){return new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'})}
async function load(){loading.value=true;try{const payload=(await getBusinessWorkDashboard({period:period.value,anchorDate:anchorDate.value})).data||{};payload.efforts=(payload.efforts||[]).map(item=>({...item,actualPercent:Number(item.actualPercent||0),editing:false,_savedActualPercent:Number(item.actualPercent||0),_savedDeviationReason:item.deviationReason||''}));data.value=payload;const projects=payload.projectBonuses||[];const selected=projects.find(project=>String(project.projectId)===String(selectedProjectId.value));selectedProjectId.value=selected?.projectId??ALL_PROJECTS}finally{loading.value=false}}
watch(()=>route.query.projectId,value=>{const requested=projectOptions.value.find(project=>String(project.projectId)===String(value));selectedProjectId.value=requested?.projectId??ALL_PROJECTS})
function changePeriod(){load()}
function goToday(){anchorDate.value=today();load()}
function openRoutineReport(routine){reportForm.value={reportId:routine.todayReportId||null,routineId:routine.routineId,projectId:routine.projectId,bizDate:data.value.today,routineName:routine.routineName,frequency:routine.frequency,targetMode:routine.targetMode||'FIXED',todayTarget:routine.todayTarget,actualValue:routine.todayReportId?Number(routine.todayActual):null,unit:routine.unit,summary:routine.todaySummary||'',issueReason:routine.todayIssueReason||'',evidenceUrls:routine.todayEvidenceUrls||'',version:null};reportDialog.value=true}
async function submitRoutine(){
  const form=reportForm.value
  if(form.targetMode!=='NONE'&&(form.actualValue===null||form.actualValue===undefined||Number(form.actualValue)<0))return ElMessage.warning(translateText("请填写实际完成量"))
  if(!form.summary?.trim())return ElMessage.warning(translateText("请填写今日完成说明"))
  if(needsReason.value&&!form.issueReason?.trim())return ElMessage.warning(translateText("未达到每日目标时请填写原因"))
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
    ElMessage({type:'success',message:translateText("提交成功，今日完成量已更新"),duration:3000,showClose:true})
  }finally{saving.value=false}
}
function openTaskReport(task){const minimumProgress=Number(task.progress||0);taskReportForm.value={reportId:task.todayTaskReportId||null,taskId:task.taskId,projectId:task.projectId,bizDate:data.value.today,taskName:task.taskName,minimumProgress,progress:Math.max(minimumProgress,Number(task.todayProgress??minimumProgress)),completionSummary:task.todayCompletionSummary||'',evidenceUrls:task.todayEvidenceUrls||''};taskReportDialog.value=true}
function keepTaskProgress(value){const minimum=Number(taskReportForm.value.minimumProgress||0);if(Number(value)<minimum)taskReportForm.value.progress=minimum}
async function submitTask(){const form=taskReportForm.value;if(!form.completionSummary?.trim())return ElMessage.warning(translateText("请填写实际完成情况"));if(form.progress===null||form.progress===undefined||Number(form.progress)<Number(form.minimumProgress||0)||Number(form.progress)>100)return ElMessage.warning(translateText("任务进度只能增加，不能低于 {0}%", [form.minimumProgress||0]));saving.value=true;try{await submitBusinessTaskReport(form);taskReportDialog.value=false;await load();ElMessage.success(translateText("今日任务完成量已保存"))}finally{saving.value=false}}
function beginEffortAdjustment(item){item._savedActualPercent=Number(item.actualPercent||0);item._savedDeviationReason=item.deviationReason||'';item.editing=true}
function cancelEffortAdjustment(item){item.actualPercent=item._savedActualPercent;item.deviationReason=item._savedDeviationReason;item.editing=false}
async function saveEffort(item){const actual=Number(item.actualPercent);if(item.reportStatus==='LEAVE')return ElMessage.info(translateText("考勤显示当天不计人员投入，无需填报"));if(!Number.isFinite(actual)||actual<0||actual>100)return ElMessage.warning(translateText("当天实际投入必须在0%到100%之间"));if(item.reportStatus==='UNSUBMITTED'&&actual===Number(item.plannedPercent)){item.editing=false;return ElMessage.info(translateText("实际投入与计划一致，无需申报"))}if(actual!==Number(item.plannedPercent)&&!item.deviationReason?.trim())return ElMessage.warning(translateText("实际投入与计划不一致时请填写偏差原因"));savingEffortId.value=item.projectId;try{await saveBusinessWorkEffort({projectId:item.projectId,bizDate:anchorDate.value,actualPercent:actual,deviationReason:actual===Number(item.plannedPercent)?'':item.deviationReason||''});ElMessage.success(translateText("投入偏差已提交负责人确认"));await load()}finally{savingEffortId.value=null}}
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
