<template>
  <div class="app-container work-page" v-loading="loading">
    <header class="work-hero">
      <div><span>MY WORK SCHEDULE</span><h1>我的安排</h1><p>只显示分配给你的工作，按今日、本周和本月查看。</p></div>
      <el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button>
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
      <div><span>持续工作</span><b>{{ summary.routineCount || 0 }}</b></div>
      <div><span>一次性任务</span><b>{{ summary.taskCount || 0 }}</b></div>
      <div v-if="isToday"><span>今日已处理</span><b>{{ summary.reportedRoutineCount || 0 }} / {{ summary.routineCount || 0 }}</b></div>
      <div v-if="period==='DAY'"><span>计划投入</span><b>{{ summary.plannedEffortPercent || 0 }}%</b></div>
    </section>

    <section v-if="period==='DAY'" class="panel effort-panel">
      <div class="panel-head"><div><h2>当天项目投入</h2><p>负责人设置的计划会自动生效；只有实际投入发生变化时才需要申报。</p></div><strong>计划合计 {{ summary.plannedEffortPercent || 0 }}%</strong></div>
      <el-empty v-if="!efforts.length" description="项目负责人尚未设置当天计划投入" />
      <div class="effort-grid">
        <article v-for="item in efforts" :key="item.allocationId" class="effort-card">
          <div class="card-top"><div><el-tag size="small" effect="plain">{{ item.projectName }}</el-tag><span>{{ item.initiatorName }}立项</span></div><el-tag size="small" :type="effortTone[item.reportStatus]">{{ effortStatusLabel[item.reportStatus] }}</el-tag></div>
          <div class="effort-values"><span>计划投入 <b>{{ item.plannedPercent }}%</b></span><span v-if="['SUBMITTED','CONFIRMED','RETURNED'].includes(item.reportStatus)">实际投入 <b>{{ item.actualPercent }}%</b></span></div>
          <el-alert v-if="item.reportStatus==='LEAVE'" :title="`今日已登记请假${item.leaveReason ? `：${item.leaveReason}` : ''}，无需填报投入，也不计算人员成本。`" type="info" :closable="false" show-icon />
          <div v-else-if="item.reportStatus==='UNSUBMITTED' && !item.editing" class="effort-default">
            <span>今天默认按计划核算，无需确认</span>
            <el-button type="primary" plain @click="beginEffortAdjustment(item)">实际投入有变化</el-button>
          </div>
          <div v-else-if="item.reportStatus==='CONFIRMED'" class="effort-result">
            <p v-if="Number(item.actualPercent)!==Number(item.plannedPercent)">偏差原因：{{ item.deviationReason || '未填写' }}</p>
            <span>负责人已确认，当天投入已锁定</span>
          </div>
          <div v-else-if="!item.editing" class="effort-result">
            <p v-if="item.reportStatus==='RETURNED'" class="effort-returned">退回原因：{{ item.reviewComment || '请修改后重新提交' }}</p>
            <p v-if="Number(item.actualPercent)!==Number(item.plannedPercent)">偏差原因：{{ item.deviationReason || '未填写' }}</p>
            <span v-else>已改回计划投入，等待负责人确认</span>
            <el-button type="primary" plain @click="beginEffortAdjustment(item)">修改偏差申报</el-button>
          </div>
          <div v-else class="effort-editor">
            <div class="effort-editor-value"><span>实际投入</span><el-input-number v-model="item.actualPercent" :min="0" :max="100" :precision="1" /></div>
            <el-input v-if="Number(item.actualPercent)!==Number(item.plannedPercent)" v-model="item.deviationReason" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="请说明实际投入与计划不同的原因" />
            <div class="effort-editor-actions"><el-button @click="cancelEffortAdjustment(item)">取消</el-button><el-button type="primary" :loading="savingEffortId===item.projectId" @click="saveEffort(item)">{{ item.reportStatus==='SUBMITTED'?'更新偏差申报':'提交偏差申报' }}</el-button></div>
          </div>
        </article>
      </div>
    </section>

    <section class="work-grid">
      <article class="panel">
        <div class="panel-head"><div><h2>持续工作</h2><p>{{ isToday ? '完成后填写今天的实际数量。' : '查看该周期内持续执行的工作和累计完成量。' }}</p></div></div>
        <el-empty v-if="!routines.length" description="这个周期没有分配给你的持续工作" />
        <div v-for="routine in routines" :key="routine.routineId" class="work-card">
          <div class="card-top"><div><el-tag size="small" effect="plain">{{ routine.projectName }}</el-tag><span>{{ routine.initiatorName }}立项</span></div><el-tag size="small">{{ frequencyLabel[routine.frequency] }}</el-tag></div>
          <h3>{{ routine.routineName }}</h3>
          <p class="target">目标：{{ routine.targetValue }} {{ routine.unit }} / {{ frequencyLabel[routine.frequency] }}</p>
          <div class="result-line"><span>{{ isToday ? (routine.todayLeaveId ? '今日状态' : '今日完成') : '周期累计' }}</span><b>{{ isToday && routine.todayLeaveId ? '今日请假' : `${isToday ? (routine.todayReportId ? routine.todayActual : '—') : (routine.periodActual || 0)} ${routine.unit}` }}</b></div>
          <p v-if="isToday && routine.todayLeaveId" class="note">请假说明：{{ routine.todayLeaveReason || '今日无需填报' }}</p>
          <p v-if="routine.todaySummary" class="note">今日说明：{{ routine.todaySummary }}</p>
          <p v-if="routine.todayIssueReason" class="issue">未达原因：{{ routine.todayIssueReason }}</p>
          <el-button v-if="isToday && !routine.todayLeaveId" type="primary" :plain="!!routine.todayReportId" @click="openRoutineReport(routine)">{{ routine.todayReportId ? '修改今日填报' : '填报今日完成量' }}</el-button>
        </div>
      </article>

      <article class="panel">
        <div class="panel-head"><div><h2>一次性任务</h2><p>只展示分配给你且尚未完成的事项。</p></div></div>
        <el-empty v-if="!tasks.length" description="这个周期没有未完成的一次性任务" />
        <div v-for="task in tasks" :key="task.taskId" class="work-card task-card">
          <div class="card-top"><div><el-tag size="small" effect="plain">{{ task.projectName }}</el-tag><span>{{ task.initiatorName }}立项</span></div><el-tag size="small" :type="taskTone[task.status]">{{ taskStatusLabel[task.status] }}</el-tag></div>
          <h3>{{ task.taskName }}</h3>
          <p class="target">{{ task.planStartDate || '未设开始日期' }} 至 {{ task.dueDate || '长期' }}</p>
          <el-progress :percentage="task.progress || 0" :stroke-width="7" />
          <div class="task-actions"><el-button v-if="task.status==='TODO'" @click="updateTask(task,'DOING')">开始</el-button><el-button v-if="task.status!=='DONE'" type="success" plain @click="updateTask(task,'DONE')">完成</el-button></div>
        </div>
      </article>
    </section>

    <el-dialog v-model="reportDialog" :title="reportForm.reportId?'修改今日完成量':'填报今日完成量'" width="min(620px, 94vw)" append-to-body>
      <el-alert :title="`${reportForm.routineName || ''} · ${data.today || today()}`" type="info" :closable="false" show-icon />
      <el-form :model="reportForm" label-width="92px" class="report-form">
        <el-form-item label="每日目标"><el-input :model-value="`${reportForm.targetValue || 0} ${reportForm.unit || ''}`" disabled /></el-form-item>
        <el-form-item label="实际完成" required><el-input-number v-model="reportForm.actualValue" :min="0" :precision="4" style="width:100%" /></el-form-item>
        <el-form-item label="今日说明"><el-input v-model="reportForm.summary" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item v-if="needsReason" label="未达原因" required><el-input v-model="reportForm.issueReason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="成果凭证" :required="reportForm.evidenceRequired==='1'"><file-upload v-model="reportForm.evidenceUrls" :limit="10" :file-size="20" :file-type="['pdf','doc','docx','xls','xlsx','jpg','jpeg','png','mp4','mov']" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reportDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitRoutine">保存今日完成量</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessWorkSchedule">
import { getBusinessWorkDashboard, saveBusinessTask, submitBusinessRoutineReport, saveBusinessWorkEffort } from '@/api/business/project'
import { ElMessage } from 'element-plus'

const loading=ref(false),saving=ref(false),savingEffortId=ref(null),data=ref({}),period=ref('DAY'),anchorDate=ref(today()),reportDialog=ref(false),reportForm=ref({})
const summary=computed(()=>data.value.summary||{}),tasks=computed(()=>data.value.tasks||[]),routines=computed(()=>data.value.routines||[]),efforts=computed(()=>data.value.efforts||[])
const isToday=computed(()=>period.value==='DAY'&&data.value.dateFrom===data.value.today)
const periodTitle=computed(()=>({DAY:'今日',WEEK:'本周',MONTH:'本月'}[period.value]))
const needsReason=computed(()=>reportForm.value.frequency==='DAILY'&&reportForm.value.actualValue!==null&&reportForm.value.actualValue!==undefined&&Number(reportForm.value.actualValue)<Number(reportForm.value.targetValue||0))
const frequencyLabel={DAILY:'每日',WEEKLY:'每周',MONTHLY:'每月'}
const taskStatusLabel={TODO:'待开始',DOING:'进行中',BLOCKED:'受阻',DONE:'已完成'}
const taskTone={TODO:'info',DOING:'primary',BLOCKED:'danger',DONE:'success'}
const effortStatusLabel={UNSUBMITTED:'按计划执行',SUBMITTED:'待负责人确认',CONFIRMED:'已确认',RETURNED:'已退回',LEAVE:'今日请假'}
const effortTone={UNSUBMITTED:'info',SUBMITTED:'warning',CONFIRMED:'success',RETURNED:'danger',LEAVE:'info'}
function today(){return new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'})}
async function load(){loading.value=true;try{const payload=(await getBusinessWorkDashboard({period:period.value,anchorDate:anchorDate.value})).data||{};payload.efforts=(payload.efforts||[]).map(item=>({...item,actualPercent:Number(item.actualPercent||0),editing:false,_savedActualPercent:Number(item.actualPercent||0),_savedDeviationReason:item.deviationReason||''}));data.value=payload}finally{loading.value=false}}
function changePeriod(){load()}
function goToday(){anchorDate.value=today();load()}
function openRoutineReport(routine){reportForm.value={reportId:routine.todayReportId||null,routineId:routine.routineId,projectId:routine.projectId,bizDate:data.value.today,routineName:routine.routineName,frequency:routine.frequency,targetValue:routine.targetValue,actualValue:routine.todayReportId?Number(routine.todayActual):null,unit:routine.unit,summary:routine.todaySummary||'',issueReason:routine.todayIssueReason||'',evidenceUrls:routine.todayEvidenceUrls||'',evidenceRequired:routine.evidenceRequired,version:null};reportDialog.value=true}
async function submitRoutine(){
  const form=reportForm.value
  if(form.actualValue===null||form.actualValue===undefined||Number(form.actualValue)<0)return ElMessage.warning('请填写实际完成量')
  if(needsReason.value&&!form.issueReason?.trim())return ElMessage.warning('未达到每日目标时请填写原因')
  if(form.evidenceRequired==='1'&&!form.evidenceUrls)return ElMessage.warning('该工作要求上传成果凭证')
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
async function updateTask(task,status){await saveBusinessTask({projectId:task.projectId,taskId:task.taskId,status,progress:status==='DONE'?100:Math.max(Number(task.progress||0),10),version:task.version});ElMessage.success(status==='DONE'?'任务已完成':'任务已开始');await load()}
function beginEffortAdjustment(item){item._savedActualPercent=Number(item.actualPercent||0);item._savedDeviationReason=item.deviationReason||'';item.editing=true}
function cancelEffortAdjustment(item){item.actualPercent=item._savedActualPercent;item.deviationReason=item._savedDeviationReason;item.editing=false}
async function saveEffort(item){if(item.reportStatus==='LEAVE')return ElMessage.info('今日已登记请假，无需填报投入');if(item.reportStatus==='UNSUBMITTED'&&Number(item.actualPercent)===Number(item.plannedPercent)){item.editing=false;return ElMessage.info('实际投入与计划一致，无需申报')}if(Number(item.actualPercent)!==Number(item.plannedPercent)&&!item.deviationReason?.trim())return ElMessage.warning('实际投入与计划不一致时请填写偏差原因');savingEffortId.value=item.projectId;try{await saveBusinessWorkEffort({projectId:item.projectId,bizDate:anchorDate.value,actualPercent:item.actualPercent,deviationReason:Number(item.actualPercent)===Number(item.plannedPercent)?'':item.deviationReason||''});ElMessage.success('投入偏差已提交负责人确认');await load()}finally{savingEffortId.value=null}}
load()
</script>

<style scoped>
.work-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f6f8;color:#172335}.work-hero{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:25px 28px;border-radius:16px;background:linear-gradient(120deg,#173b59,#1d6d70);color:#fff}.work-hero span{font-size:11px;letter-spacing:.17em;color:#6de0da}.work-hero h1{margin:5px 0;font-size:28px}.work-hero p{margin:0;color:#c1d4de}.schedule-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:16px 0;padding:14px;border:1px solid #dfe6eb;border-radius:12px;background:#fff}.date-tools{display:flex;gap:8px}.period-summary{display:grid;grid-template-columns:2fr repeat(4,1fr);gap:10px;margin-bottom:14px}.period-summary>div{padding:16px 18px;border:1px solid #dfe6eb;border-radius:12px;background:#fff}.period-summary span,.period-summary b{display:block}.period-summary span{color:#7d8997}.period-summary b{margin-top:7px;font-size:19px}.work-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.panel{min-width:0;padding:18px;border:1px solid #dfe6eb;border-radius:13px;background:#fff}.panel-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.panel-head h2{margin:0;font-size:18px}.panel-head p{margin:5px 0 12px;color:#84919f;font-size:12px}.effort-panel{margin-bottom:14px}.effort-panel>.panel-head>strong{color:#167268;font-size:18px}.effort-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.effort-card{padding:15px;border:1px solid #dce8e5;border-radius:11px;background:#f8fbfa}.effort-values{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:14px 0}.effort-values span{color:#73827e}.effort-values b{color:#1d3f3a}.effort-default,.effort-result{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 12px;border-radius:8px;background:#eef6f4;color:#667b76;font-size:13px}.effort-result{align-items:flex-start;flex-direction:column}.effort-result p{margin:0;color:#536a64;line-height:1.6}.effort-result .el-button{align-self:stretch}.effort-editor{display:flex;flex-direction:column;gap:10px}.effort-editor-value,.effort-editor-actions{display:flex;align-items:center;justify-content:space-between;gap:10px}.effort-editor-value span{color:#73827e}.effort-editor-actions{justify-content:flex-end}.work-card{margin-top:10px;padding:15px;border:1px solid #e3e8ed;border-radius:11px;background:#fbfcfd}.card-top,.card-top>div,.result-line,.task-actions{display:flex;align-items:center}.card-top{justify-content:space-between;gap:10px}.card-top>div{min-width:0;gap:8px}.card-top span{color:#8793a0;font-size:12px}.work-card h3{margin:13px 0 7px}.target,.note,.issue{margin:5px 0;color:#788694;font-size:13px}.result-line{justify-content:space-between;margin:13px 0;padding:11px;border-radius:8px;background:#eff7f5}.result-line span{color:#708078}.result-line b{font-size:17px}.issue{color:#c84550}.task-card :deep(.el-progress){margin:13px 0}.task-actions{justify-content:flex-end;gap:8px}.task-actions .el-button{margin:0}.report-form{margin-top:18px}@media(max-width:900px){.period-summary{grid-template-columns:repeat(2,1fr)}.work-grid,.effort-grid{grid-template-columns:1fr}}@media(max-width:640px){.work-page{padding:12px}.work-hero{align-items:flex-start;flex-direction:column;padding:20px}.work-hero>.el-button{width:100%}.schedule-bar{align-items:stretch;flex-direction:column}.schedule-bar :deep(.el-radio-group){display:grid;grid-template-columns:repeat(3,1fr)}.schedule-bar :deep(.el-radio-button__inner){width:100%;padding:9px 5px}.date-tools{display:grid;grid-template-columns:1fr auto}.period-summary{grid-template-columns:1fr 1fr}.period-summary>div{padding:13px}.period-summary b{font-size:15px}.effort-panel>.panel-head{flex-direction:column}.card-top{align-items:flex-start}.card-top>div{align-items:flex-start;flex-direction:column;gap:5px}.effort-values,.effort-default{align-items:stretch;flex-direction:column}.effort-editor-value :deep(.el-input-number){width:100%}}
.effort-result p.effort-returned{color:#c84550}
</style>
