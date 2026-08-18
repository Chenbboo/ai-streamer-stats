<template>
  <div class="app-container owner-page" v-loading="loading">
    <header class="owner-hero">
      <div>
        <span>PROJECT OWNER WORKBENCH</span>
        <h1>项目负责人工作台</h1>
        <p>只展示你作为主负责人承担的项目；每天统一填写项目总花费，提交后立即进入经营核算。</p>
      </div>
      <div class="hero-actions">
        <el-select v-model="selectedProjectId" filterable placeholder="选择负责项目" @change="switchProject">
          <el-option v-for="item in projects" :key="item.projectId" :label="`${item.projectName} · ${item.initiatorName || '未标注老板'} · ${statusLabel[item.status] || item.status}`" :value="item.projectId" />
        </el-select>
        <el-button icon="Refresh" :loading="loading" @click="load(selectedProjectId)">刷新</el-button>
      </div>
    </header>

    <div v-if="!project && !loading" class="no-project">
      <el-empty description="你目前还不是任何项目的主负责人">
        <p>老板任命你为项目负责人后，项目会自动出现在这里。</p>
      </el-empty>
    </div>

    <template v-if="project">
      <section class="metric-grid">
        <article><span>昨日汇报总额</span><b>{{ xu(yesterdayReportedTotalXu) }} <em>Xu</em></b><small>{{ reportedSourceRoutineCount }} 人已提交 · {{ unreportedSourceRoutineCount }} 人未提交</small></article>
        <article><span>持续工作</span><b>{{ todayRoutines.length }}</b><small>{{ sourceRoutineCount }} 项直播同步 · {{ unreportedRoutineCount }} 项未完成</small></article>
        <article><span>未完成任务</span><b>{{ openTasks.length }}</b><small>{{ overdueTaskCount }} 项已逾期</small></article>
        <article><span>今日投入待确认</span><b>{{ pendingTodayEfforts.length }}</b><small>{{ todayEfforts.length }} 名成员参与项目</small></article>
        <article><span>项目进度</span><b>{{ projectProgress }}%</b><small>按一次性任务完成情况计算</small></article>
      </section>

      <section class="workspace-grid">
        <div class="main-column">
          <article class="panel">
            <div class="panel-head">
              <div><h2>持续工作状态</h2><p>手工工作按当天填报；直播主播自动展示昨日的日报提交状态，无需在这里重复提交。</p></div>
              <el-button link type="primary" @click="openProject">进入项目详情</el-button>
            </div>
            <div v-if="!todayRoutines.length" class="empty-block">当前没有持续工作计划，可在项目详情中新增</div>
            <div v-for="routine in todayRoutines" :key="routine.routineId" class="routine-card">
              <div class="routine-main">
                <div class="routine-title"><b>{{ routine.routineName }}</b><el-tag size="small" effect="plain">{{ frequencyLabel[routine.frequency] }}</el-tag><el-tag v-if="routine.sourceManaged" size="small" type="info" effect="plain">直播同步</el-tag></div>
                <small v-if="routine.sourceManaged">{{ routine.assigneeName }} · 数据日期 {{ routine.sourceBizDate }} · {{ routine.supervisorName }}监督</small>
                <small v-else>{{ routine.assigneeName || '未分配' }} · 目标 {{ routine.targetValue }} {{ routine.unit }} · 累计 {{ routine.cumulativeActual || 0 }} {{ routine.unit }}</small>
                <el-progress :percentage="routineRate(routine)" :status="routine.todayReportId && Number(routine.todayActual) >= Number(routine.targetValue) ? 'success' : undefined" :stroke-width="7" />
                <p v-if="routine.todaySummary">今日说明：{{ routine.todaySummary }}</p>
                <p v-if="routine.todayIssueReason" class="danger">未达原因：{{ routine.todayIssueReason }}</p>
              </div>
              <div class="routine-result">
                <span>{{ routine.sourceManaged ? '昨日直播日报' : (routineLeave(routine) ? '今日状态' : '今日完成') }}</span>
                <el-tag v-if="routine.sourceManaged" :type="routine.todayReportId?'success':'warning'">{{ routine.todayReportId ? '已提交' : '未提交' }}</el-tag>
                <b v-if="routine.sourceManaged && routine.todayReportId" class="routine-xu">{{ xu(routine.sourceReportedAmount) }} Xu</b>
                <b v-else-if="!routine.sourceManaged && !routineLeave(routine)">{{ routine.todayReportId ? routine.todayActual : '—' }} {{ routine.unit }}</b><el-tag v-else-if="!routine.sourceManaged" type="info">今日请假</el-tag>
                <el-button v-if="routine.todayEvidenceUrls" size="small" type="primary" plain @click="openEvidence(routine)">查看成果凭证（{{ evidenceCount(routine.todayEvidenceUrls) }}）</el-button>
                <el-button v-if="canSubmitRoutine(routine)" v-hasPermi="['business:project:report']" size="small" :type="routine.todayReportId?'default':'primary'" :disabled="!canReport" @click="openRoutineReport(routine)">{{ routine.todayReportId ? '修改填报' : '填报完成量' }}</el-button>
                <small v-else class="assignee-report-hint">{{ routine.sourceManaged ? '完成状态由直播数据管理自动回传' : (routineLeave(routine) ? '今日请假，无需填报' : `由 ${routine.assigneeName || '实际执行人'} 本人填报`) }}</small>
              </div>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head">
              <div><h2>一次性任务</h2><p>只放有明确完成时点的事项；“每天做多少”请放到持续工作。</p></div>
            </div>
            <div v-if="!openTasks.length" class="empty-block">当前没有未完成任务</div>
            <div v-for="task in openTasks" :key="task.taskId" class="task-card">
              <i :class="`priority-${(task.priority || 'MEDIUM').toLowerCase()}`"></i>
              <div class="task-content">
                <b>{{ task.taskName }}</b>
                <small>{{ task.assigneeName || '未分配' }} · {{ task.dueDate || '未设期限' }}</small>
                <el-progress :percentage="task.progress || 0" :stroke-width="6" />
              </div>
              <div class="task-actions">
                <el-button v-if="task.status === 'TODO'" size="small" @click="updateTask(task, 'DOING')">开始</el-button>
                <el-button v-if="task.status !== 'DONE'" size="small" type="success" plain @click="updateTask(task, 'DONE')">完成</el-button>
              </div>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head">
              <div><h2>今日成员投入确认</h2><p>{{ today() }} · 员工只申报当天投入偏差，负责人逐条确认或退回。</p></div>
              <el-button link @click="openProject">设置计划</el-button>
            </div>
            <div v-if="!todayEfforts.length" class="empty-block">尚未设置今日成员计划投入</div>
            <div v-for="item in todayEfforts" :key="item.allocationId" class="today-effort-card">
              <div class="today-effort-head">
                <span><b>{{ item.userName }}</b><small>{{ effortDayHint(item) }}</small></span>
                <el-tag size="small" :type="effortStatusTone[item.reportStatus]">{{ effortStatusLabel[item.reportStatus] }}</el-tag>
              </div>
              <div class="today-effort-values"><span>计划投入 <b>{{ item.plannedPercent }}%</b></span><span>实际投入 <b>{{ item.actualPercent }}%</b></span></div>
              <p v-if="item.deviationReason" class="today-effort-reason">偏差原因：{{ item.deviationReason }}</p>
              <p v-if="item.reviewComment" class="danger">退回原因：{{ item.reviewComment }}</p>
              <div class="today-effort-actions">
                <template v-if="item.reportStatus==='SUBMITTED'">
                  <el-button type="success" plain :loading="saving" @click="confirmEffort(item)">确认</el-button>
                  <el-button type="danger" plain :loading="saving" @click="openEffortReturn(item)">退回修改</el-button>
                </template>
              </div>
            </div>
          </article>

          <article class="panel week-effort-summary">
            <div class="panel-head"><div><h2>本周投入汇总</h2><p>{{ data.effortWeekFrom }} 至 {{ data.effortWeekTo }} · 仅用于查看，不在这里批量审批偏差。</p></div></div>
            <div v-if="!effortMembers.length" class="empty-block">本周暂无投入安排</div>
            <div v-for="member in effortMembers" :key="member.userId" class="week-effort-row">
              <span><b>{{ member.userName }}</b><small>{{ member.deviationCount }} 天存在投入偏差</small></span>
              <span>计划日均 <b>{{ member.plannedAverage }}%</b></span>
              <span>实际日均 <b>{{ member.actualAverage }}%</b></span>
              <el-tag :type="member.confirmedDays===member.days?'success':'info'">已处理 {{ member.confirmedDays }}/{{ member.days }} 天</el-tag>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head">
              <div><h2>今日项目总花费</h2><p>{{ accounting.bizDate }} · 只填写业务花费，不包含系统自动计算的人员成本</p></div>
              <el-button v-hasPermi="['business:project:report']" type="primary" :icon="accounting.dailySpend?'Edit':'Plus'" :disabled="!canReport" @click="openDailySpend">{{ accounting.dailySpend ? '修改花费' : '填写花费' }}</el-button>
            </div>
            <el-alert v-if="!canReport" :title="reportBlockReason" type="info" :closable="false" show-icon />
            <div v-if="!accounting.dailySpend" class="empty-block">今日尚未填写项目总花费</div>
            <div v-else class="daily-spend-row">
              <span><small>今日业务成本</small><b>{{ money(accounting.dailySpend.amount) }} {{ accounting.dailySpend.currency || project.baseCurrency }}</b></span>
              <span><small>说明</small><b>{{ accounting.dailySpend.description || '无' }}</b></span>
              <el-tag type="success">已计入经营结果</el-tag>
            </div>
          </article>
        </div>

        <aside class="side-column">
          <article class="panel project-summary">
            <div class="project-title"><div><small>{{ project.projectNo }}</small><h2>{{ project.projectName }}</h2></div><el-tag :type="statusTone[project.status] || 'info'">{{ statusLabel[project.status] }}</el-tag></div>
            <p>{{ project.objective || '尚未填写项目目标' }}</p>
            <dl><div><dt>立项老板</dt><dd>{{ project.initiatorName }}</dd></div><div><dt>归属公司</dt><dd>{{ project.companyName || '待设置' }}</dd></div><div><dt>计划周期</dt><dd>{{ project.planStartDate || '—' }} 至 {{ project.planEndDate || '—' }}</dd></div><div><dt>项目进度</dt><dd>{{ projectProgress }}%</dd></div></dl>
          </article>

          <article class="panel">
            <div class="panel-head"><div><h2>项目 KPI</h2><p>目标由老板确认，负责人查看执行差距。</p></div></div>
            <div v-if="!currentKpis.length" class="empty-block compact">尚未设置 KPI</div>
            <div v-for="kpi in currentKpis" :key="kpi.kpiId" class="kpi-row"><span><b>{{ kpi.kpiName }}</b><small>{{ kpi.actualValue ?? '—' }} / {{ kpi.targetValue }} {{ kpi.unit || '' }}</small></span><strong>{{ kpiRate(kpi) }}%</strong></div>
          </article>

          <article class="panel">
            <div class="panel-head"><div><h2>参项人员</h2><p>本项目共 {{ project.members?.length || 0 }} 人，不展示人员成本金额。</p></div></div>
            <div v-if="!project.members?.length" class="empty-block compact">尚未添加参项人员</div>
            <div v-else class="participant-list">
              <div v-for="member in project.members" :key="member.memberId || member.userId" class="participant-row">
                <el-avatar :size="34">{{ (member.userNameSnapshot || '员').slice(0, 1) }}</el-avatar>
                <span><b>{{ member.userNameSnapshot }}</b><small v-if="participantLeave(member)" class="leave-note">今日请假：{{ participantLeave(member).reason || '已登记' }}</small><small v-else>{{ member.joinedDate ? `${member.joinedDate} 加入` : '项目成员' }}</small></span>
                <el-tag size="small" :type="memberRoleTone[member.memberRole] || 'info'">{{ memberRoleLabel[member.memberRole] || member.memberRole }}</el-tag>
                <div class="participant-action">
                  <template v-if="participantLeave(member)">
                    <el-tag size="small" type="warning">今日请假</el-tag>
                    <el-button link type="danger" :loading="saving" @click="cancelLeave(member)">取消</el-button>
                  </template>
                  <template v-else>
                    <el-tag v-if="participantEffort(member)" size="small" :type="effortStatusTone[participantEffort(member).reportStatus]">{{ effortStatusLabel[participantEffort(member).reportStatus] }}</el-tag>
                    <el-tag v-else size="small" type="info">今日无投入计划</el-tag>
                    <el-button link type="primary" :loading="saving" @click="openLeave(member)">今日请假</el-button>
                  </template>
                </div>
              </div>
            </div>
          </article>
        </aside>
      </section>
    </template>

    <el-dialog v-model="routineReportDialog" :title="routineReportForm.reportId?'修改今日完成量':'填报今日完成量'" width="min(620px, 94vw)" append-to-body>
      <el-alert :title="`${routineReportForm.routineName || ''} · ${accounting.bizDate || today()}`" type="info" :closable="false" show-icon />
      <el-form :model="routineReportForm" label-width="92px" class="report-form">
        <el-form-item label="周期目标"><el-input :model-value="`${routineReportForm.targetValue || 0} ${routineReportForm.unit || ''}`" disabled /></el-form-item>
        <el-form-item label="实际完成" required><el-input-number v-model="routineReportForm.actualValue" :min="0" :precision="4" style="width:100%" /></el-form-item>
        <el-form-item label="今日说明"><el-input v-model="routineReportForm.summary" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="可填写成果位置、质量情况或下一步安排" /></el-form-item>
        <el-form-item v-if="routineReportNeedsReason" label="未达原因" required><el-input v-model="routineReportForm.issueReason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明未达到今日目标的原因和改进安排" /></el-form-item>
        <el-form-item label="成果凭证" :required="routineReportForm.evidenceRequired==='1'"><file-upload v-model="routineReportForm.evidenceUrls" :limit="10" :file-size="20" :file-type="['pdf','doc','docx','xls','xlsx','jpg','jpeg','png','mp4','mov']" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="routineReportDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitRoutineReport">保存今日完成量</el-button></template>
    </el-dialog>

    <el-dialog v-model="evidenceDialog" :title="`${evidencePreview.routineName || ''} · 成果凭证`" width="min(840px, 96vw)" append-to-body destroy-on-close>
      <div class="evidence-dialog-summary">
        <span>{{ evidencePreview.assigneeName || '执行人' }}提交</span>
        <span>{{ accounting.bizDate || today() }}</span>
        <span>共 {{ evidencePreview.files.length }} 个凭证</span>
      </div>
      <div class="evidence-preview-grid">
        <div v-for="file in evidencePreview.files" :key="file.path" class="evidence-preview-item">
          <el-image v-if="file.kind==='image'" :src="file.url" :preview-src-list="evidenceImageUrls" :initial-index="file.imageIndex" fit="contain" preview-teleported />
          <video v-else-if="file.kind==='video'" :src="file.url" controls preload="metadata" />
          <div v-else class="evidence-file-card">
            <el-icon><Document /></el-icon>
            <span>{{ file.name }}</span>
            <el-link :href="file.url" target="_blank" type="primary">打开附件</el-link>
          </div>
          <small v-if="file.kind!=='file'">{{ file.name }}</small>
        </div>
      </div>
      <template #footer><el-button type="primary" @click="evidenceDialog=false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="effortReturnDialog" title="退回今日投入申报" width="min(520px, 94vw)" append-to-body>
      <el-alert :title="`${effortReturnForm.userName || ''} · ${effortReturnForm.bizDate || today()} · 退回后由员工修改并重新提交`" type="warning" :closable="false" show-icon />
      <el-form :model="effortReturnForm" label-width="88px" class="report-form">
        <el-form-item label="退回原因" required><el-input v-model="effortReturnForm.reviewComment" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请明确说明需要员工修改的内容" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="effortReturnDialog=false">取消</el-button><el-button type="danger" :loading="saving" @click="submitEffortReturn">确认退回</el-button></template>
    </el-dialog>

    <el-dialog v-model="leaveDialog" title="登记今日请假" width="min(520px, 94vw)" append-to-body>
      <el-alert title="请假按人员和日期统一生效：该员工今天参与的所有项目都不再计算人员成本。负责人不能代填工作完成量。" type="info" :closable="false" show-icon />
      <el-form :model="leaveForm" label-width="88px" class="report-form">
        <el-form-item label="请假人员"><el-input :model-value="leaveForm.userName" disabled /></el-form-item>
        <el-form-item label="请假日期"><el-input :model-value="leaveForm.leaveDate" disabled /></el-form-item>
        <el-form-item label="请假原因" required><el-input v-model="leaveForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="例如：病假、事假；请填写便于核对的说明" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="leaveDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitLeave">确认请假</el-button></template>
    </el-dialog>

    <el-dialog v-model="reportDialog" :title="accounting.dailySpend ? '修改今日项目总花费' : '填写今日项目总花费'" width="min(620px, 94vw)" append-to-body>
      <el-alert title="这里仅填写项目当天发生的业务花费，不含人员成本；保存后立即更新老板工作台。" type="info" :closable="false" show-icon />
      <el-form :model="reportForm" label-width="104px" class="report-form">
        <el-form-item label="归属项目"><el-input :model-value="project?.projectName" disabled /></el-form-item>
        <el-form-item label="业务日期"><el-input :model-value="accounting.bizDate" disabled /></el-form-item>
        <el-form-item label="今日总花费" required><el-input-number v-model="reportForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="费用说明"><el-input v-model="reportForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="选填，例如：投流、采购、物流等合计" /></el-form-item>
        <el-form-item label="凭证附件"><file-upload v-model="reportForm.attachmentUrls" :limit="10" :file-size="20" :file-type="['pdf','doc','docx','xls','xlsx','jpg','jpeg','png']" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reportDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitDailySpend">保存并计入经营结果</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessOwnerWorkbench">
import { getBusinessOwnerWorkbench, saveBusinessTask, submitBusinessRoutineReport, confirmBusinessMemberEffort, returnBusinessMemberEffort, markBusinessMemberLeave, cancelBusinessMemberLeave } from '@/api/business/project'
import { saveBusinessProjectDailySpend } from '@/api/business/accounting'
import useUserStore from '@/store/modules/user'
import { ElMessage, ElMessageBox } from 'element-plus'

const route=useRoute(),router=useRouter()
const userStore=useUserStore()
const loading=ref(false),saving=ref(false),data=ref({}),selectedProjectId=ref(null),reportDialog=ref(false),routineReportDialog=ref(false),leaveDialog=ref(false),effortReturnDialog=ref(false),evidenceDialog=ref(false)
const projects=computed(()=>data.value.projects||[]),project=computed(()=>data.value.project||null)
const operating=computed(()=>data.value.operating||{}),accounting=computed(()=>data.value.accounting||{})
const openTasks=computed(()=>data.value.openTasks||[])
const todayRoutines=computed(()=>(data.value.todayRoutines||[]).filter(item=>(!item.startDate||item.startDate<=today())&&(!item.endDate||item.endDate>=today())))
const sourceRoutines=computed(()=>todayRoutines.value.filter(item=>item.sourceManaged))
const sourceRoutineCount=computed(()=>sourceRoutines.value.length)
const reportedSourceRoutineCount=computed(()=>sourceRoutines.value.filter(item=>item.todayReportId).length)
const unreportedSourceRoutineCount=computed(()=>sourceRoutineCount.value-reportedSourceRoutineCount.value)
const yesterdayReportedTotalXu=computed(()=>sourceRoutines.value.reduce((sum,item)=>sum+Number(item.sourceReportedAmount||0),0))
const currentKpis=computed(()=>(operating.value.kpis||[]).filter(item=>item.status==='CURRENT'))
const canReport=computed(()=>['ACTIVE','ACCEPTANCE'].includes(project.value?.status)&&!!project.value?.companyDeptId)
const unreportedRoutineCount=computed(()=>todayRoutines.value.filter(item=>!item.todayReportId&&!routineLeave(item)).length)
const routineReportNeedsReason=computed(()=>routineReportForm.value.frequency==='DAILY'&&routineReportForm.value.actualValue!==null&&routineReportForm.value.actualValue!==undefined&&Number(routineReportForm.value.actualValue)<Number(routineReportForm.value.targetValue||0))
const reportBlockReason=computed(()=>!project.value?.companyDeptId?'项目尚未设置归属公司，请联系立项老板完善后再填报':'项目进入执行中后才能提交经营数据')
const overdueTaskCount=computed(()=>openTasks.value.filter(task=>task.dueDate&&task.dueDate<today()).length)
const todayEfforts=computed(()=>(data.value.effortWeek||[]).filter(item=>item.bizDate===today()))
const pendingTodayEfforts=computed(()=>todayEfforts.value.filter(item=>item.reportStatus==='SUBMITTED'))
const effortMembers=computed(()=>{
  const grouped=new Map()
  for(const row of (data.value.effortWeek||[]).filter(item=>item.bizDate<=today())){
    const key=Number(row.userId)
    if(!grouped.has(key))grouped.set(key,{userId:key,userName:row.userName,days:0,confirmedDays:0,deviationCount:0,plannedTotal:0,actualTotal:0,todayOnLeave:false,leaveReason:'',deviations:[]})
    const item=grouped.get(key)
    item.days++
    item.plannedTotal+=Number(row.plannedPercent||0)
    item.actualTotal+=Number(row.actualPercent||0)
    if(!['SUBMITTED','RETURNED'].includes(row.reportStatus))item.confirmedDays++
    if(row.reportStatus!=='LEAVE'&&Number(row.actualPercent)!==Number(row.plannedPercent)){
      item.deviationCount++
      item.deviations.push({bizDate:row.bizDate,plannedPercent:Number(row.plannedPercent||0),actualPercent:Number(row.actualPercent||0),deviationReason:row.deviationReason||'',reportStatus:row.reportStatus})
    }
    if(row.bizDate===today()&&row.reportStatus==='LEAVE'){item.todayOnLeave=true;item.leaveReason=row.leaveReason||'已登记请假'}
  }
  return [...grouped.values()].map(item=>({...item,plannedAverage:item.days?Math.round(item.plannedTotal*10/item.days)/10:0,actualAverage:item.days?Math.round(item.actualTotal*10/item.days)/10:0}))
})
const confirmedEffortDays=computed(()=>effortMembers.value.reduce((sum,item)=>sum+item.confirmedDays,0))
const totalEffortDays=computed(()=>effortMembers.value.reduce((sum,item)=>sum+item.days,0))
const projectProgress=computed(()=>project.value?.taskCount?Math.round(Number(project.value.completedTaskCount||0)*100/Number(project.value.taskCount)):0)
const statusLabel={DRAFT:'草稿',PLANNING:'规划中',ACTIVE:'执行中',PAUSED:'已暂停',ACCEPTANCE:'待验收',CLOSED:'已结项',CANCELED:'已取消'}
const statusTone={DRAFT:'info',PLANNING:'warning',ACTIVE:'primary',PAUSED:'info',ACCEPTANCE:'success',CLOSED:'success',CANCELED:'danger'}
const frequencyLabel={DAILY:'每日',WEEKLY:'每周',MONTHLY:'每月'}
const memberRoleLabel={OWNER:'主负责人',DEPUTY:'副负责人',MEMBER:'成员',OBSERVER:'观察者'}
const memberRoleTone={OWNER:'primary',DEPUTY:'success',MEMBER:'info',OBSERVER:'warning'}
const effortStatusLabel={UNSUBMITTED:'按计划执行',SUBMITTED:'待确认',CONFIRMED:'已确认',RETURNED:'已退回',LEAVE:'今日请假'}
const effortStatusTone={UNSUBMITTED:'info',SUBMITTED:'warning',CONFIRMED:'success',RETURNED:'danger',LEAVE:'info'}
const blankReport=()=>({projectId:null,bizDate:today(),amount:null,description:'',attachmentUrls:''})
const reportForm=ref(blankReport())
const routineReportForm=ref({})
const leaveForm=ref({userId:null,userName:'',leaveDate:today(),reason:''})
const effortReturnForm=ref({userId:null,userName:'',bizDate:today(),reviewComment:''})
const evidencePreview=ref({routineName:'',assigneeName:'',files:[]})
const evidenceImageUrls=computed(()=>evidencePreview.value.files.filter(file=>file.kind==='image').map(file=>file.url))
function today(){return new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'})}
function evidencePaths(value){return String(value||'').split(',').map(item=>item.trim()).filter(Boolean)}
function evidenceCount(value){return evidencePaths(value).length}
function evidenceUrl(path){return /^(https?:)?\/\//i.test(path)||path.startsWith('data:')?path:`${import.meta.env.VITE_APP_BASE_API}${path}`}
function evidenceName(path){const clean=path.split('?')[0];try{return decodeURIComponent(clean.slice(clean.lastIndexOf('/')+1))||'成果凭证'}catch{return clean.slice(clean.lastIndexOf('/')+1)||'成果凭证'}}
function evidenceKind(path){const ext=path.split('?')[0].split('.').pop()?.toLowerCase();if(['jpg','jpeg','png','gif','webp','bmp'].includes(ext))return 'image';if(['mp4','mov','webm','ogg'].includes(ext))return 'video';return 'file'}
function openEvidence(routine){let imageIndex=0;const files=evidencePaths(routine.todayEvidenceUrls).map(path=>{const kind=evidenceKind(path);const file={path,url:evidenceUrl(path),name:evidenceName(path),kind,imageIndex:kind==='image'?imageIndex:-1};if(kind==='image')imageIndex++;return file});evidencePreview.value={routineName:routine.routineName,assigneeName:routine.assigneeName,files};evidenceDialog.value=true}
function money(value){return Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})}
function xu(value){return Number(value||0).toLocaleString('zh-CN',{maximumFractionDigits:2})}
function signed(value){const n=Number(value||0);return `${n>0?'+':''}${money(n)}`}
function amountTone(value){return Number(value||0)<0?'amount-loss':'amount-profit'}
function kpiRate(kpi){const target=Number(kpi.targetValue||0);return target?Math.round(Number(kpi.actualValue||0)*100/target):0}
function routineRate(routine){const target=Number(routine.targetValue||0);return target?Math.min(100,Math.round(Number(routine.todayActual||0)*100/target)):0}
function canSubmitRoutine(routine){return !routine.sourceManaged&&!routineLeave(routine)&&Number(routine.assigneeUserId)===Number(userStore.id)}
function effortDayHint(item){if(item.reportStatus==='SUBMITTED')return '员工已申报偏差，请逐条审核';if(item.reportStatus==='CONFIRMED')return '当天实际投入已确认并锁定';if(item.reportStatus==='RETURNED')return '已退回员工修改';if(item.reportStatus==='LEAVE')return `今日请假${item.leaveReason?`：${item.leaveReason}`:''}`;return '未申报偏差，按计划投入自动核算'}
function participantEffort(member){return todayEfforts.value.find(item=>Number(item.userId)===Number(member.userId))}
function participantLeave(member){const stored=(data.value.todayLeaves||[]).find(item=>Number(item.userId)===Number(member.userId));if(stored)return stored;const effort=participantEffort(member);return effort?.reportStatus==='LEAVE'?{userId:member.userId,reason:effort.leaveReason||''}:null}
function routineLeave(routine){return routine.sourceManaged?null:(data.value.todayLeaves||[]).find(item=>Number(item.userId)===Number(routine.assigneeUserId))}
function participantName(member){return member.userNameSnapshot||member.userName||'项目成员'}
async function load(projectId){loading.value=true;try{const{data:payload={}}=await getBusinessOwnerWorkbench(projectId||undefined);data.value=payload;selectedProjectId.value=payload.project?.projectId||null;if(selectedProjectId.value)router.replace({query:{...route.query,projectId:selectedProjectId.value}})}finally{loading.value=false}}
function switchProject(id){load(id)}
function openProject(){router.push({path:'/business/projects',query:{id:project.value.projectId}})}
async function updateTask(task,status){const payload={...task,status,progress:status==='DONE'?100:Math.max(Number(task.progress||0),10)};await saveBusinessTask(payload);ElMessage.success(status==='DONE'?'任务已完成':'任务已开始');await load(selectedProjectId.value)}
async function confirmEffort(item){
  saving.value=true
  try{
    const response=await confirmBusinessMemberEffort(project.value.projectId,item.userId,{bizDate:item.bizDate})
    const saved=response?.data||{}
    item.reportStatus=saved.reportStatus||'CONFIRMED'
    item.confirmedUserName=saved.confirmedUserName||userStore.nickName||userStore.name
    item.confirmedTime=saved.confirmedTime||new Date().toISOString()
    item.reviewComment=''
    await nextTick()
    ElMessage({type:'success',message:`${item.userName} 的今日投入已确认，人员成本已重新计算`,duration:3000,showClose:true})
  }finally{saving.value=false}
}
function openEffortReturn(item){effortReturnForm.value={userId:item.userId,userName:item.userName,bizDate:item.bizDate,reviewComment:''};effortReturnDialog.value=true}
async function submitEffortReturn(){
  const form=effortReturnForm.value
  if(!form.reviewComment?.trim())return ElMessage.warning('请填写退回原因')
  saving.value=true
  try{
    await returnBusinessMemberEffort(project.value.projectId,form.userId,{bizDate:form.bizDate,reviewComment:form.reviewComment.trim()})
    const item=todayEfforts.value.find(row=>Number(row.userId)===Number(form.userId)&&row.bizDate===form.bizDate)
    if(item){item.reportStatus='RETURNED';item.reviewComment=form.reviewComment.trim()}
    effortReturnDialog.value=false
    await nextTick()
    ElMessage({type:'success',message:'已退回员工修改',duration:3000,showClose:true})
  }finally{saving.value=false}
}
function openRoutineReport(routine){routineReportForm.value={reportId:routine.todayReportId||null,routineId:routine.routineId,projectId:project.value.projectId,bizDate:accounting.value.bizDate||today(),routineName:routine.routineName,frequency:routine.frequency,targetValue:routine.targetValue,actualValue:routine.todayReportId?Number(routine.todayActual):null,unit:routine.unit,summary:routine.todaySummary||'',issueReason:routine.todayIssueReason||'',evidenceUrls:routine.todayEvidenceUrls||'',evidenceRequired:routine.evidenceRequired,version:null};routineReportDialog.value=true}
async function submitRoutineReport(){const form=routineReportForm.value;if(form.actualValue===null||form.actualValue===undefined||Number(form.actualValue)<0)return ElMessage.warning('请填写实际完成量');if(routineReportNeedsReason.value&&!form.issueReason?.trim())return ElMessage.warning('未达到每日目标时请填写原因');if(form.evidenceRequired==='1'&&!form.evidenceUrls)return ElMessage.warning('该工作要求上传成果凭证');saving.value=true;try{await submitBusinessRoutineReport(form);routineReportDialog.value=false;ElMessage.success('今日完成量已保存');await load(selectedProjectId.value)}finally{saving.value=false}}
function openLeave(member){leaveForm.value={userId:member.userId,userName:participantName(member),leaveDate:today(),reason:''};leaveDialog.value=true}
async function submitLeave(){if(!leaveForm.value.reason?.trim())return ElMessage.warning('请填写请假原因');saving.value=true;try{const response=await markBusinessMemberLeave(project.value.projectId,leaveForm.value.userId,{leaveDate:leaveForm.value.leaveDate,reason:leaveForm.value.reason.trim()});const stored=response?.data||{userId:leaveForm.value.userId,reason:leaveForm.value.reason.trim()};data.value.todayLeaves=[...(data.value.todayLeaves||[]).filter(item=>Number(item.userId)!==Number(leaveForm.value.userId)),stored];const effort=todayEfforts.value.find(item=>Number(item.userId)===Number(leaveForm.value.userId));if(effort){effort.reportStatus='LEAVE';effort.actualPercent=0;effort.leaveReason=leaveForm.value.reason.trim()}leaveDialog.value=false;await nextTick();ElMessage({type:'success',message:'已登记今日请假，该员工今日所有项目的人员成本均为 0',duration:3000,showClose:true})}finally{saving.value=false}}
async function cancelLeave(member){await ElMessageBox.confirm(`确认取消 ${participantName(member)} 的今日请假吗？取消后系统会重新计算其参与项目的人员成本。`,'取消今日请假',{type:'warning'});saving.value=true;try{await cancelBusinessMemberLeave(project.value.projectId,member.userId,today());data.value.todayLeaves=(data.value.todayLeaves||[]).filter(item=>Number(item.userId)!==Number(member.userId));ElMessage({type:'success',message:'今日请假已取消，人员成本已重新计算',duration:3000,showClose:true});await load(selectedProjectId.value)}finally{saving.value=false}}
function openDailySpend(){reportForm.value={...blankReport(),...(accounting.value.dailySpend||{}),projectId:project.value.projectId,bizDate:accounting.value.bizDate};reportDialog.value=true}
async function submitDailySpend(){if(reportForm.value.amount===null||reportForm.value.amount===undefined)return ElMessage.warning('请填写今日项目总花费');saving.value=true;try{await saveBusinessProjectDailySpend(reportForm.value);reportDialog.value=false;ElMessage.success('今日项目总花费已计入经营结果');await load(selectedProjectId.value)}finally{saving.value=false}}
load(route.query.projectId?Number(route.query.projectId):undefined)
</script>

<style scoped>
.owner-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f6f8;color:#172335}.owner-hero{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:25px 28px;border-radius:16px;background:linear-gradient(120deg,#173b59,#1d6d70);color:#fff}.owner-hero span{font-size:11px;letter-spacing:.17em;color:#6de0da}.owner-hero h1{margin:5px 0;font-size:28px}.owner-hero p{margin:0;color:#c1d4de}.hero-actions{display:flex;align-items:center;gap:10px}.hero-actions .el-select{width:360px}.metric-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin:16px 0}.metric-grid article{min-width:0;padding:17px 19px;border:1px solid #dfe6eb;border-radius:12px;background:#fff}.metric-grid span,.metric-grid small{display:block}.metric-grid span{color:#6f7d8c}.metric-grid b{display:block;margin:6px 0;font-size:26px;white-space:nowrap}.metric-grid b em{color:#697786;font-size:14px;font-style:normal;font-weight:500}.metric-grid small{color:#98a2ad}.amount-profit{color:#198069}.amount-loss,.danger{color:#cf4650}.workspace-grid{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(320px,.65fr);gap:14px}.main-column,.side-column{display:flex;min-width:0;flex-direction:column;gap:14px}.panel{padding:18px;border:1px solid #dfe6eb;border-radius:13px;background:#fff}.panel-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:12px}.panel h2{margin:0;font-size:17px}.panel p{margin:4px 0;color:#84919f;font-size:12px}.routine-card{display:flex;align-items:center;gap:18px;padding:15px 4px;border-top:1px solid #edf0f2}.routine-main{display:flex;min-width:0;flex:1;flex-direction:column;gap:7px}.routine-title{display:flex;align-items:center;gap:8px}.routine-main>small{color:#8b97a4}.routine-main>p{margin:0}.routine-result{display:flex;min-width:128px;align-items:flex-end;flex-direction:column;gap:6px}.routine-result span{color:#7e8b99;font-size:12px}.routine-result b{font-size:18px}.routine-result .routine-xu{color:#198069}.assignee-report-hint{color:#8b97a4}.task-card{display:flex;align-items:center;gap:12px;padding:13px 4px;border-top:1px solid #edf0f2}.task-card>i{width:8px;height:8px;border-radius:50%;background:#8794a3}.task-card>i.priority-high{background:#d44951}.task-card>i.priority-medium{background:#d68b2a}.task-card>i.priority-low{background:#3f9178}.task-content{display:flex;min-width:0;flex:1;flex-direction:column;gap:5px}.task-content small,.kpi-row small,.risk-row small,.fact-row small,.effort-member-row small{color:#8b97a4}.leave-note{color:#7b6a91!important}.task-actions,.effort-actions{display:flex}.effort-member-row{display:grid;grid-template-columns:minmax(150px,1fr) auto auto auto auto;align-items:center;gap:16px;padding:13px 4px;border-top:1px solid #edf0f2}.effort-member-row>span:first-child{display:flex;min-width:0;flex-direction:column}.fact-row{display:flex;align-items:center;gap:10px;padding:13px 4px;border-top:1px solid #edf0f2}.fact-row>span:nth-child(2){display:flex;min-width:0;flex:1;flex-direction:column}.fact-row strong{white-space:nowrap}.project-title{display:flex;align-items:flex-start;justify-content:space-between}.project-title small{color:#81909e}.project-summary>p{margin:14px 0;line-height:1.7}.project-summary dl{margin:0}.project-summary dl>div{display:flex;justify-content:space-between;padding:9px 0;border-top:1px solid #edf0f2}.project-summary dt{color:#7b8997}.project-summary dd{margin:0;text-align:right}.kpi-row,.risk-row{display:flex;align-items:center;gap:9px;padding:11px 2px;border-top:1px solid #edf0f2}.kpi-row>span,.risk-row>span{display:flex;min-width:0;flex:1;flex-direction:column}.empty-block{padding:28px 0;text-align:center;color:#9aa5b0}.empty-block.compact{padding:15px 0}.no-project{margin-top:16px;padding:50px;border:1px solid #dfe6eb;border-radius:14px;background:#fff}.no-project p{color:#8c98a5}.report-form{margin-top:18px}@media(max-width:1300px){.metric-grid{grid-template-columns:repeat(3,1fr)}}@media(max-width:1050px){.metric-grid{grid-template-columns:repeat(2,1fr)}.workspace-grid{grid-template-columns:1fr}}@media(max-width:640px){.owner-page{padding:12px}.owner-hero{align-items:flex-start;flex-direction:column;padding:20px}.hero-actions{width:100%;align-items:stretch;flex-direction:column}.hero-actions .el-select,.hero-actions .el-button{width:100%}.metric-grid{gap:8px}.metric-grid article{padding:14px}.panel-head{align-items:flex-start}.effort-actions{align-items:stretch;flex-direction:column}.effort-member-row{grid-template-columns:1fr 1fr}.effort-member-row>span:first-child,.effort-member-row>.el-tag{grid-column:1/-1}.effort-member-row>.el-button{justify-self:start}.routine-card,.task-card,.fact-row{align-items:flex-start;flex-wrap:wrap}.routine-result{width:100%;align-items:stretch}.routine-result b{font-size:17px}.task-actions{width:100%;justify-content:flex-end}.fact-row strong{margin-left:auto}}
.daily-spend-row{display:grid;grid-template-columns:180px minmax(0,1fr) auto;align-items:center;gap:18px;padding:18px;border-radius:10px;background:#f6faf9}.daily-spend-row>span{display:flex;min-width:0;flex-direction:column;gap:5px}.daily-spend-row small{color:#8793a1}.daily-spend-row b{overflow-wrap:anywhere}@media(max-width:640px){.daily-spend-row{grid-template-columns:1fr}.daily-spend-row .el-tag{justify-self:start}}
.effort-member-card{border-top:1px solid #edf0f2}.effort-member-card .effort-member-row{border-top:0}.effort-deviation-list{margin:0 4px 13px;padding:10px 12px;border-radius:9px;background:#fff7e8}.effort-deviation-row{display:grid;grid-template-columns:100px auto minmax(140px,1fr) auto;align-items:center;gap:12px;padding:7px 0;color:#735c34;font-size:13px}.effort-deviation-row+.effort-deviation-row{border-top:1px solid #f0dfbd}.effort-deviation-reason{min-width:0;overflow-wrap:anywhere;color:#8a6733}@media(max-width:640px){.effort-deviation-row{grid-template-columns:1fr auto}.effort-deviation-row>span{grid-column:1/-1}}
.today-effort-card{margin-top:10px;padding:14px;border:1px solid #dfe6eb;border-radius:10px;background:#fbfcfd}.today-effort-head,.today-effort-values,.today-effort-actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.today-effort-head>span:first-child{display:flex;min-width:0;flex-direction:column}.today-effort-head small,.week-effort-row small{color:#8b97a4}.today-effort-values{justify-content:flex-start;margin:13px 0}.today-effort-values span{min-width:150px;color:#73808e}.today-effort-reason{padding:10px 12px;border-radius:8px;background:#fff7e8!important;color:#795d2f!important}.today-effort-actions{justify-content:flex-end}.week-effort-summary{background:#fafcfd}.week-effort-row{display:grid;grid-template-columns:minmax(150px,1fr) auto auto auto;align-items:center;gap:16px;padding:12px 4px;border-top:1px solid #edf0f2}.week-effort-row>span:first-child{display:flex;flex-direction:column}@media(max-width:640px){.today-effort-head,.today-effort-values{align-items:flex-start;flex-direction:column}.today-effort-values span{min-width:0}.today-effort-actions{align-items:stretch;flex-direction:column}.today-effort-actions .el-button{width:100%;margin:0}.week-effort-row{grid-template-columns:1fr 1fr}.week-effort-row>span:first-child,.week-effort-row>.el-tag{grid-column:1/-1}}
.participant-list{display:flex;flex-direction:column}.participant-row{display:grid;grid-template-columns:auto minmax(0,1fr) auto auto;align-items:center;gap:11px;padding:11px 0;border-top:1px solid #edf0f2}.participant-row>span{display:flex;min-width:0;flex-direction:column;gap:3px}.participant-row b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.participant-row small{color:#8b97a4}.participant-row .el-avatar{background:#e8f4f2;color:#22746d;font-weight:600}.participant-action{display:flex;align-items:center;justify-content:flex-end;gap:5px}.participant-action .el-button{margin:0}@media(max-width:640px){.participant-row{grid-template-columns:auto minmax(0,1fr) auto}.participant-action{grid-column:2/-1;justify-content:flex-start}}
.evidence-dialog-summary{display:flex;align-items:center;gap:10px;margin-bottom:16px;color:#7a8794;font-size:13px}.evidence-dialog-summary span+span:before{margin-right:10px;color:#c3cbd3;content:'·'}.evidence-preview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.evidence-preview-item{min-width:0;padding:10px;border:1px solid #e0e7ec;border-radius:10px;background:#f7f9fa}.evidence-preview-item>.el-image,.evidence-preview-item>video{display:block;width:100%;height:300px;border-radius:7px;background:#eef1f3}.evidence-preview-item>small{display:block;margin-top:8px;overflow:hidden;color:#75818d;text-overflow:ellipsis;white-space:nowrap}.evidence-file-card{display:flex;min-height:150px;align-items:center;justify-content:center;flex-direction:column;gap:12px;padding:20px;text-align:center}.evidence-file-card>.el-icon{color:#7e8c98;font-size:38px}.evidence-file-card>span{max-width:100%;overflow-wrap:anywhere;color:#4d5965}@media(max-width:640px){.evidence-dialog-summary{align-items:flex-start;flex-direction:column;gap:4px}.evidence-dialog-summary span+span:before{content:none}.evidence-preview-grid{grid-template-columns:1fr}.evidence-preview-item>.el-image,.evidence-preview-item>video{height:240px}}
</style>
