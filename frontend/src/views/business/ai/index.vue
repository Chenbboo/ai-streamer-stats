<template>
  <div class="app-container ai-page">
    <header class="ai-header">
      <div class="ai-brand"><span class="ai-orb">AI</span><div><span class="eyebrow">OWNER AI COMMAND</span><h1>老板 AI 助理</h1><p>不用学习系统菜单，直接告诉我你想了解的经营问题。</p></div></div>
      <div class="header-actions"><el-tag type="success" effect="plain">{{ aiEngine }} · 操作需确认</el-tag><el-button @click="router.push('/business/boss')">返回老板工作台</el-button></div>
    </header>

    <main class="ai-workspace">
      <aside class="capability-panel">
        <h2>当前可以替你</h2>
        <button v-for="item in capabilities" :key="item.prompt" :disabled="aiLoading" @click="sendAi(item.prompt)">
          <span>{{ item.icon }}</span><div><b>{{ item.title }}</b><small>{{ item.description }}</small></div>
        </button>
        <div class="safety-card"><b>数据与操作边界</b><p>查询沿用当前账号的数据权限。立项、预算和审批等写操作，未经你确认不会执行。</p></div>
      </aside>

      <section class="chat-panel">
        <div ref="aiMessagesEl" class="ai-messages">
          <div v-if="!aiMessages.length" class="ai-welcome"><span class="ai-mini-orb">AI</span><div><b>你好，我已经准备好了。</b><p>你可以问“今天经营怎么样”，我会读取系统里的收入、成本、项目和异常，整理成一份老板能直接判断的结论。</p></div></div>
          <div v-for="(message,index) in aiMessages" :key="index" class="ai-message" :class="`is-${message.role}`">
            <span v-if="message.role==='assistant'" class="ai-mini-orb">AI</span>
            <div class="message-stack">
              <div class="ai-bubble"><p>{{ message.content }}</p></div>
              <AiWorkflowCard v-if="message.role==='assistant'&&message.workflow" :workflow="message.workflow" />
              <AiDecisionTracePanel v-if="message.role==='assistant'&&message.decisionTrace" :trace="message.decisionTrace" />
              <AiEvidencePanel v-if="message.role==='assistant'" :understanding="message.understanding" :evidence="message.evidence" :sources="message.sources" :scope="message.scope" />
              <BusinessInsightCard v-if="message.businessCard" :card="message.businessCard" @action="sendAi" />
              <PlanReviewCard v-if="message.planReview?.ready" :review="message.planReview" :busy="aiLoading" :show-actions="isLatestPlanReview(index)" @decision="handlePlanReviewDecision" />
              <AcceptanceReviewCard v-if="message.acceptanceReview?.ready" :review="message.acceptanceReview" @action="sendAi" />
              <article v-if="message.actionRequest" class="action-card" :class="`is-${message.actionRequest.status?.toLowerCase()}`">
                <div class="action-head"><div><el-tag size="small" type="warning">需要老板确认</el-tag><h3>{{ actionTitle(message.actionRequest) }}</h3></div><span>#{{ message.actionRequest.actionRequestId }}</span></div>
                <div v-if="message.actionRequest.project?.projectName || message.actionRequest.details?.projectName" class="project-name">{{ message.actionRequest.project?.projectName || message.actionRequest.details?.projectName }}</div>
                <template v-if="isCapabilityAcceptanceDecision(message.actionRequest)"><div class="action-grid"><div><small>项目负责人</small><b>{{ message.actionRequest.details?.mainOwnerName }}</b></div><div><small>验收版本</small><b>第 {{ message.actionRequest.details?.submissionVersion }} 版</b></div><div><small>老板决定</small><b>{{ isCapabilityAcceptanceApproval(message.actionRequest)?'验收通过并结项':'退回负责人补充' }}</b></div><div><small>状态变化</small><b>{{ isCapabilityAcceptanceApproval(message.actionRequest)?'待验收 → 已结项':'待验收 → 执行中' }}</b></div></div><AcceptanceReviewCard v-if="message.actionRequest.details?.acceptanceReview?.ready" :review="message.actionRequest.details.acceptanceReview" class="embedded-review" compact /><div v-if="!isCapabilityAcceptanceApproval(message.actionRequest)" class="objective"><small>退回要求</small><p>{{ message.actionRequest.details?.comment }}</p></div><div class="objective"><small>确认后会发生什么</small><p>{{ isCapabilityAcceptanceApproval(message.actionRequest)?'当前验收版本将被批准，项目正式结项。':'当前验收版本将被退回，项目恢复执行，由负责人补充后重新提交。' }}</p></div></template>
                <template v-else-if="isCapabilityProjectCreate(message.actionRequest)"><div class="action-grid"><div><small>归属公司</small><b>{{ message.actionRequest.details?.companyName }}</b></div><div><small>主负责人</small><b>{{ message.actionRequest.details?.mainOwnerName }}</b></div><div><small>计划周期</small><b>{{ message.actionRequest.details?.planStartDate }} 至 {{ message.actionRequest.details?.planEndDate }}</b></div><div><small>核算方式</small><b>{{ accountingLabel[message.actionRequest.details?.accountingMode] || message.actionRequest.details?.accountingMode }}</b></div><div><small>预算上限</small><b>{{ budgetText(message.actionRequest.details) }}</b></div><div><small>管理模式</small><b>{{ managementLabel[message.actionRequest.details?.managementMode] || message.actionRequest.details?.managementMode }}</b></div></div><div class="objective"><small>项目目标</small><p>{{ message.actionRequest.details?.objective }}</p></div><div class="objective"><small>确认后会发生什么</small><p>项目将正式写入项目中心，并保留本次确认和操作审计记录。</p></div></template>
                <template v-else-if="isCapabilityPlanDecision(message.actionRequest)"><div class="action-grid"><div><small>项目负责人</small><b>{{ message.actionRequest.details?.mainOwnerName }}</b></div><div><small>老板决定</small><b>{{ isCapabilityPlanApproval(message.actionRequest)?'批准计划并启动':'退回负责人调整' }}</b></div></div><PlanReviewCard v-if="message.actionRequest.details?.planReview?.ready" :review="message.actionRequest.details.planReview" compact /><div v-if="!isCapabilityPlanApproval(message.actionRequest)" class="objective"><small>退回要求</small><p>{{ message.actionRequest.details?.comment }}</p></div><div class="objective"><small>确认后会发生什么</small><p>{{ isCapabilityPlanApproval(message.actionRequest)?'项目进入执行，负责人和成员按照已提交计划开始工作。':'项目仍处于规划中，负责人修改后需要重新提交老板审批。' }}</p></div></template>
                <template v-else-if="isCapabilityAction(message.actionRequest)"><div class="objective"><small>准备执行的操作</small><p>{{ message.actionRequest.confirmationSummary }}</p></div></template>
                <template v-else-if="message.actionRequest.actionCode==='BUDGET_ADJUSTMENT'"><div class="action-grid"><div><small>当前预算</small><b>{{ moneyText(message.actionRequest.project?.oldBudgetLimit,message.actionRequest.project?.currency) }}</b></div><div><small>调整后预算</small><b>{{ moneyText(message.actionRequest.project?.budgetLimit,message.actionRequest.project?.currency) }}</b></div></div><div class="objective"><small>调整原因</small><p>{{ message.actionRequest.project?.reason }}</p></div><div class="objective"><small>确认后会发生什么</small><p>项目预算上限将立即更新，并保留本次调整原因和操作审计记录。</p></div></template>
                <template v-else-if="message.actionRequest.actionCode==='PROJECT_TRANSITION'"><div class="action-grid"><div><small>项目负责人</small><b>{{ message.actionRequest.project?.mainOwnerName }}</b></div><div><small>状态变化</small><b>{{ transitionStatusText(message.actionRequest) }}</b></div></div><div class="objective"><small>确认后会发生什么</small><p>{{ transitionEffectText(message.actionRequest) }}</p></div></template>
                <template v-else-if="message.actionRequest.actionCode==='PROJECT_PLAN_DECISION'"><div class="action-grid"><div><small>项目负责人</small><b>{{ message.actionRequest.project?.mainOwnerName }}</b></div><div><small>老板决定</small><b>{{ isPlanApproval(message.actionRequest)?'批准计划并启动':'退回负责人调整' }}</b></div></div><PlanReviewCard v-if="message.actionRequest.project?.planReview?.ready" :review="message.actionRequest.project.planReview" compact /><div v-if="!isPlanApproval(message.actionRequest)" class="objective"><small>退回要求</small><p>{{ message.actionRequest.project?.returnReason }}</p></div><div class="objective"><small>确认后会发生什么</small><p>{{ isPlanApproval(message.actionRequest)?'项目进入执行，负责人和成员按照已提交计划开始工作。':'项目仍处于规划中，负责人修改后需要重新提交老板审批。' }}</p></div></template>
                <template v-else-if="message.actionRequest.actionCode==='PROJECT_ACCEPTANCE_DECISION'"><div class="action-grid"><div><small>项目负责人</small><b>{{ message.actionRequest.project?.mainOwnerName }}</b></div><div><small>验收版本</small><b>第 {{ message.actionRequest.project?.submissionVersion }} 版</b></div><div><small>老板决定</small><b>{{ isAcceptanceApproval(message.actionRequest)?'验收通过并结项':'退回负责人补充' }}</b></div><div><small>状态变化</small><b>{{ isAcceptanceApproval(message.actionRequest)?'待验收 → 已结项':'待验收 → 执行中' }}</b></div></div><AcceptanceReviewCard v-if="message.actionRequest.project?.acceptanceReview?.ready" :review="message.actionRequest.project.acceptanceReview" class="embedded-review" compact /><div v-if="!isAcceptanceApproval(message.actionRequest)" class="objective"><small>退回要求</small><p>{{ message.actionRequest.project?.comment }}</p></div><div class="objective"><small>确认后会发生什么</small><p>{{ isAcceptanceApproval(message.actionRequest)?'当前验收版本将被批准，项目正式结项。':'当前验收版本将被退回，项目恢复执行，由负责人补充后重新提交。' }}</p></div></template>
                <template v-else><div class="action-grid"><div><small>归属公司</small><b>{{ message.actionRequest.project?.companyName }}</b></div><div><small>主负责人</small><b>{{ message.actionRequest.project?.mainOwnerName }}</b></div><div><small>计划周期</small><b>{{ message.actionRequest.project?.planStartDate }} 至 {{ message.actionRequest.project?.planEndDate }}</b></div><div><small>核算方式</small><b>{{ accountingLabel[message.actionRequest.project?.accountingMode] || message.actionRequest.project?.accountingMode }}</b></div><div><small>预算上限</small><b>{{ budgetText(message.actionRequest.project) }}</b></div><div><small>管理模式</small><b>{{ managementLabel[message.actionRequest.project?.managementMode] || message.actionRequest.project?.managementMode }}</b></div></div><div class="objective"><small>项目目标</small><p>{{ message.actionRequest.project?.objective }}</p></div></template>
                <div v-if="message.actionRequest.status==='PENDING'" class="action-buttons"><el-button :disabled="message.actionBusy" @click="handleAction(message,'reject')">取消</el-button><el-button :type="isPlanReturn(message.actionRequest)?'warning':'primary'" :loading="message.actionBusy" @click="handleAction(message,'confirm')">{{ actionConfirmLabel(message.actionRequest) }}</el-button></div>
                <div v-else class="action-result"><el-tag :type="message.actionRequest.status==='EXECUTED'?'success':'info'">{{ message.actionRequest.status==='EXECUTED'?actionDoneLabel(message.actionRequest):'已取消' }}</el-tag><el-button v-if="message.actionRequest.projectId" link type="primary" @click="openProject(message.actionRequest.projectId)">查看项目</el-button></div>
              </article>
            </div>
          </div>
          <div v-if="aiLoading" class="ai-message is-assistant"><span class="ai-mini-orb">AI</span><div class="ai-bubble ai-thinking"><i></i><i></i><i></i><span>正在理解问题并读取经营数据</span></div></div>
        </div>

        <div class="composer-area">
          <div class="quick-row"><button v-for="prompt in aiPrompts" :key="prompt" :disabled="aiLoading" @click="sendAi(prompt)">{{ prompt }}</button></div>
          <div class="ai-composer"><el-input v-model="aiInput" type="textarea" :autosize="{minRows:2,maxRows:6}" resize="none" maxlength="1000" show-word-limit placeholder="直接输入经营问题，例如：今天经营怎么样？哪些项目需要我处理？" @keydown.enter.exact.prevent="sendAi()" /><el-button type="primary" :loading="aiLoading" :disabled="!aiInput.trim()" @click="sendAi()">发送</el-button></div>
          <small class="composer-tip">Enter 发送，Shift + Enter 换行</small>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup name="BusinessBossAi">
import { ElMessage, ElMessageBox } from 'element-plus'
import { chatWithBossAi, getBossAiConversation, confirmBossAiAction, rejectBossAiAction } from '@/api/business/ai'
import PlanReviewCard from './PlanReviewCard.vue'
import BusinessInsightCard from './BusinessInsightCard.vue'
import AiEvidencePanel from './AiEvidencePanel.vue'
import AcceptanceReviewCard from './AcceptanceReviewCard.vue'
import AiWorkflowCard from './AiWorkflowCard.vue'
import AiDecisionTracePanel from './AiDecisionTracePanel.vue'

const router=useRouter()
const aiInput=ref('')
const aiLoading=ref(false)
const aiConversationId=ref(null)
const aiMessages=ref([])
const aiMessagesEl=ref()
const aiEngine=ref('DeepSeek V4 Flash')
const conversationStorageKey='business:boss-ai:active-conversation'
const aiPrompts=['帮我创建一个新项目','帮我审核待审批计划','今天经营怎么样？','有哪些项目需要我处理？']
const accountingLabel={PROFIT:'利润项目',COST:'成本项目',VALUE:'价值项目',HYBRID:'混合核算'}
const managementLabel={SIMPLE:'精简模式',STANDARD:'标准模式',DELIVERY:'交付模式'}
const capabilities=[
  {icon:'建',title:'对话创建项目',description:'收集立项信息，确认后正式创建',prompt:'帮我创建一个新项目'},
  {icon:'¥',title:'查今日经营',description:'收入、业务成本、人员成本与盈亏',prompt:'今天经营怎么样？'},
  {icon:'项',title:'看项目态势',description:'项目状态、进度、逾期和风险',prompt:'现在所有项目的整体情况怎么样？'},
  {icon:'审',title:'审核负责人计划',description:'读懂计划、检查缺项，确认后批准或退回',prompt:'帮我审核待审批计划'},
  {icon:'验',title:'审核项目验收',description:'核对成果、交付凭证，确认后结项或退回',prompt:'帮我审核待验收项目'},
  {icon:'待',title:'找待处理事项',description:'汇总需要老板判断和处理的事情',prompt:'有哪些项目需要我处理？'},
  {icon:'人',title:'看人员概况',description:'两家公司人员数量和分布',prompt:'现在的人员分布怎么样？'}
]
const scrollAi=()=>nextTick(()=>{if(aiMessagesEl.value)aiMessagesEl.value.scrollTop=aiMessagesEl.value.scrollHeight})
function normalizeAssistant(answer={}){
  const metadata=answer.metadata&&typeof answer.metadata==='object'?answer.metadata:{}
  return {
    role:'assistant',
    content:answer.content||'',
    understanding:answer.understanding||answer.queryUnderstanding||metadata.understanding||metadata.queryUnderstanding,
    evidence:answer.evidence||metadata.evidence||[],
    sources:answer.sources||metadata.sources||[],
    scope:answer.scope||metadata.scope,
    businessCard:answer.businessCard||metadata.businessCard,
    planReview:answer.planReview||metadata.planReview,
    acceptanceReview:answer.acceptanceReview||metadata.acceptanceReview,
    workflow:answer.workflow||metadata.workflow,
    decisionTrace:answer.decisionTrace||metadata.decisionTrace,
    actionRequest:answer.actionRequest||metadata.actionRequest,
    traceId:answer.traceId||metadata.traceId,
    runId:answer.runId||metadata.runId,
    provider:answer.provider||metadata.provider,
    model:answer.model||metadata.model
  }
}
async function restoreConversation(){
  const stored=Number(sessionStorage.getItem(conversationStorageKey))
  if(!Number.isSafeInteger(stored)||stored<=0)return
  aiLoading.value=true
  try{
    const result=await getBossAiConversation(stored)
    const messages=Array.isArray(result.data)?result.data:[]
    aiConversationId.value=stored
    aiMessages.value=messages.map(item=>item.role==='assistant'?normalizeAssistant(item):{role:'user',content:item.content||''})
    const lastAssistant=[...aiMessages.value].reverse().find(item=>item.role==='assistant')
    const provider=lastAssistant?.provider
    if(provider)aiEngine.value=provider==='DEEPSEEK'?(lastAssistant.model||'DeepSeek V4 Flash'):'本地安全路由'
    scrollAi()
  }catch{
    sessionStorage.removeItem(conversationStorageKey)
  }finally{aiLoading.value=false}
}
onMounted(restoreConversation)
async function sendAi(preset) {
  const text=(typeof preset==='string'?preset:aiInput.value).trim()
  if(!text||aiLoading.value)return
  aiInput.value=''
  aiMessages.value.push({role:'user',content:text})
  aiLoading.value=true
  scrollAi()
  try {
    const result=await chatWithBossAi({conversationId:aiConversationId.value,message:text})
    const answer=result.data||{}
    aiConversationId.value=answer.conversationId
    if(answer.conversationId)sessionStorage.setItem(conversationStorageKey,String(answer.conversationId))
    const metadata=answer.metadata&&typeof answer.metadata==='object'?answer.metadata:{}
    const provider=answer.provider||metadata.provider
    aiEngine.value=provider==='DEEPSEEK'?(answer.model||metadata.model||'DeepSeek V4 Flash'):'本地安全路由'
    aiMessages.value.push(normalizeAssistant(answer))
  } catch (error) {
    aiMessages.value.push({role:'assistant',content:'这次没有成功读取数据，请稍后重试。'})
  } finally {
    aiLoading.value=false
    scrollAi()
  }
}
async function handlePlanReviewDecision({decision,project}){
  const projectName=project?.projectName
  if(!projectName)return ElMessage.warning('没有取得项目名称，请重新审核计划')
  if(decision==='APPROVE')return sendAi(`批准计划并启动：${projectName}`)
  try{
    const {value}=await ElMessageBox.prompt(`请说明退回“${projectName}”计划的调整要求。`,'退回计划',{confirmButtonText:'生成退回确认单',cancelButtonText:'取消',inputPlaceholder:'例如：补充交付标准和成员投入计划',inputValidator:value=>value?.trim()?true:'必须填写调整要求'})
    return sendAi(`退回项目“${projectName}”的计划。调整要求：${value.trim()}`)
  }catch{return}
}
function isLatestPlanReview(index){
  return !aiMessages.value.slice(index+1).some(item=>item.planReview?.ready||item.actionRequest?.actionCode==='PROJECT_PLAN_DECISION')
}
const budgetText=project=>project?.budgetLimit===null||project?.budgetLimit===undefined?'暂不设置':`${Number(project.budgetLimit).toLocaleString('zh-CN',{minimumFractionDigits:2})} ${project.baseCurrency||'CNY'}`
const moneyText=(value,currency='CNY')=>value===null||value===undefined?'暂未设置':`${Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})} ${currency||'CNY'}`
const openProject=projectId=>router.push({path:'/business/projects',query:{id:projectId}})
const isPlanApproval=request=>request?.actionCode==='PROJECT_PLAN_DECISION'&&request?.project?.decision==='APPROVE'
const isAcceptanceApproval=request=>request?.actionCode==='PROJECT_ACCEPTANCE_DECISION'&&request?.project?.decision==='APPROVED'
const isCapabilityAcceptanceDecision=request=>request?.actionCode==='CAPABILITY:project.acceptance.decide'
const isCapabilityAcceptanceApproval=request=>isCapabilityAcceptanceDecision(request)&&request?.details?.decision==='APPROVED'
const isCapabilityProjectCreate=request=>request?.actionCode==='CAPABILITY:project.create'
const isCapabilityPlanDecision=request=>request?.actionCode==='CAPABILITY:project.plan.decide'
const isCapabilityPlanApproval=request=>isCapabilityPlanDecision(request)&&request?.details?.decision==='APPROVE'
const isPlanReturn=request=>(request?.actionCode==='PROJECT_PLAN_DECISION'&&request?.project?.decision==='RETURN')||(isCapabilityPlanDecision(request)&&request?.details?.decision==='RETURN')
const isResume=request=>request?.actionCode==='PROJECT_TRANSITION'&&request?.project?.transitionAction==='RESUME_PROJECT'
const transitionStatusText=request=>isResume(request)?'已暂停 → 执行中':'草稿 → 规划中'
const transitionEffectText=request=>isResume(request)?'项目将恢复执行，负责人和成员继续按照原计划推进工作。':'负责人可以开始拆解持续工作、一次性任务和成员安排，再提交给老板确认。'
const isCapabilityAction=request=>request?.actionCode?.startsWith('CAPABILITY:')
const actionTitle=request=>isCapabilityProjectCreate(request)?'项目立项确认单':isCapabilityPlanDecision(request)?(isCapabilityPlanApproval(request)?'批准计划确认单':'退回计划确认单'):isCapabilityAcceptanceDecision(request)?(isCapabilityAcceptanceApproval(request)?'验收通过确认单':'退回验收确认单'):isCapabilityAction(request)?'系统操作确认单':request?.actionCode==='BUDGET_ADJUSTMENT'?'预算调整确认单':request?.actionCode==='PROJECT_TRANSITION'?(isResume(request)?'恢复执行确认单':'进入规划确认单'):request?.actionCode==='PROJECT_PLAN_DECISION'?(isPlanApproval(request)?'批准计划确认单':'退回计划确认单'):request?.actionCode==='PROJECT_ACCEPTANCE_DECISION'?(isAcceptanceApproval(request)?'验收通过确认单':'退回验收确认单'):'项目立项确认单'
const actionConfirmLabel=request=>isCapabilityProjectCreate(request)?'确认立项':isCapabilityPlanDecision(request)?(isCapabilityPlanApproval(request)?'确认批准并启动':'确认退回调整'):isCapabilityAcceptanceDecision(request)?(isCapabilityAcceptanceApproval(request)?'确认验收并结项':'确认退回验收'):isCapabilityAction(request)?'确认执行':request?.actionCode==='BUDGET_ADJUSTMENT'?'确认调整预算':request?.actionCode==='PROJECT_TRANSITION'?(isResume(request)?'确认恢复执行':'确认进入规划'):request?.actionCode==='PROJECT_PLAN_DECISION'?(isPlanApproval(request)?'确认批准并启动':'确认退回调整'):request?.actionCode==='PROJECT_ACCEPTANCE_DECISION'?(isAcceptanceApproval(request)?'确认验收并结项':'确认退回验收'):'确认立项'
const actionDoneLabel=request=>isCapabilityProjectCreate(request)?'已创建项目':isCapabilityPlanDecision(request)?(isCapabilityPlanApproval(request)?'已批准并启动':'已退回调整'):isCapabilityAcceptanceDecision(request)?(isCapabilityAcceptanceApproval(request)?'已验收结项':'已退回验收'):isCapabilityAction(request)?'操作已执行':request?.actionCode==='BUDGET_ADJUSTMENT'?'预算已调整':request?.actionCode==='PROJECT_TRANSITION'?(isResume(request)?'已恢复执行':'已进入规划'):request?.actionCode==='PROJECT_PLAN_DECISION'?(isPlanApproval(request)?'已批准并启动':'已退回调整'):request?.actionCode==='PROJECT_ACCEPTANCE_DECISION'?(isAcceptanceApproval(request)?'已验收结项':'已退回验收'):'已创建项目'
function syncActionRequest(actionRequestId,patch){
  aiMessages.value.forEach(item=>{
    if(item.actionRequest?.actionRequestId===actionRequestId)Object.assign(item.actionRequest,patch)
  })
}
async function handleAction(message,decision){
  const request=message.actionRequest
  if(!request||request.status!=='PENDING'||message.actionBusy)return
  if(decision==='confirm'){
    try{
      const transition=request.actionCode==='PROJECT_TRANSITION'
      const planDecision=request.actionCode==='PROJECT_PLAN_DECISION'
      const acceptanceDecision=request.actionCode==='PROJECT_ACCEPTANCE_DECISION'
      const capabilityAcceptanceDecision=isCapabilityAcceptanceDecision(request)
      const capabilityProjectCreate=isCapabilityProjectCreate(request)
      const capabilityPlanDecision=isCapabilityPlanDecision(request)
      const budgetAdjustment=request.actionCode==='BUDGET_ADJUSTMENT'
      const text=capabilityProjectCreate?`确认创建项目“${request.details?.projectName}”吗？确认后将正式写入项目中心。`:capabilityPlanDecision?(isCapabilityPlanApproval(request)?`确认批准项目“${request.details?.projectName}”的计划并立即启动执行吗？`:`确认将项目“${request.details?.projectName}”退回负责人调整吗？`):capabilityAcceptanceDecision?(isCapabilityAcceptanceApproval(request)?`确认通过项目“${request.details?.projectName}”第 ${request.details?.submissionVersion} 版验收并正式结项吗？`:`确认退回项目“${request.details?.projectName}”的验收资料吗？`):isCapabilityAction(request)?`确认执行以下操作吗？\n${request.confirmationSummary}`:budgetAdjustment?`确认将项目“${request.project?.projectName}”的预算调整为 ${moneyText(request.project?.budgetLimit,request.project?.currency)} 吗？`:transition?(isResume(request)?`确认让项目“${request.project?.projectName}”恢复执行吗？`:`确认让项目“${request.project?.projectName}”进入规划吗？确认后负责人可以开始拆解计划。`):planDecision?(isPlanApproval(request)?`确认批准项目“${request.project?.projectName}”的计划并立即启动执行吗？`:`确认将项目“${request.project?.projectName}”退回负责人调整吗？`):acceptanceDecision?(isAcceptanceApproval(request)?`确认通过项目“${request.project?.projectName}”第 ${request.project?.submissionVersion} 版验收并正式结项吗？`:`确认退回项目“${request.project?.projectName}”的验收资料吗？`):`确认创建项目“${request.project?.projectName}”吗？确认后将正式写入项目中心。`
      await ElMessageBox.confirm(text,capabilityProjectCreate?'老板确认立项':capabilityPlanDecision?'老板确认计划审批':capabilityAcceptanceDecision||acceptanceDecision?'老板确认项目验收':isCapabilityAction(request)?'老板确认系统操作':budgetAdjustment?'老板确认预算调整':planDecision?'老板确认计划审批':transition?'老板确认推进':'老板确认立项',{type:'warning',confirmButtonText:actionConfirmLabel(request)})
    }catch{return}
  }
  message.actionBusy=true
  try{
    if(decision==='confirm'){
      const result=(await confirmBossAiAction(request.actionRequestId)).data||{}
      syncActionRequest(request.actionRequestId,{status:'EXECUTED',projectId:result.projectId,projectNo:result.projectNo,projectName:result.projectName})
      const transition=request.actionCode==='PROJECT_TRANSITION'
      const planDecision=request.actionCode==='PROJECT_PLAN_DECISION'
      const acceptanceDecision=request.actionCode==='PROJECT_ACCEPTANCE_DECISION'
      const capabilityAcceptanceDecision=isCapabilityAcceptanceDecision(request)
      const capabilityProjectCreate=isCapabilityProjectCreate(request)
      const capabilityPlanDecision=isCapabilityPlanDecision(request)
      const budgetAdjustment=request.actionCode==='BUDGET_ADJUSTMENT'
      aiMessages.value.push({role:'assistant',content:capabilityProjectCreate?`项目“${result.projectName}”已创建成功，项目编号 ${result.projectNo}。`:capabilityPlanDecision?(result.decision==='APPROVE'?`项目“${result.projectName}”的计划已经批准，项目现已进入执行。`:`项目“${result.projectName}”的计划已退回负责人调整。`):capabilityAcceptanceDecision?(result.decision==='APPROVED'?`项目“${result.projectName}”已通过验收并正式结项。`:`项目“${result.projectName}”的验收资料已退回负责人补充。`):isCapabilityAction(request)?`操作已经执行：${request.confirmationSummary}`:budgetAdjustment?`项目“${result.projectName}”的预算已经调整为 ${moneyText(result.budgetLimit,result.currency)}。`:transition?(result.transitionAction==='RESUME_PROJECT'?`项目“${result.projectName}”已经恢复执行。负责人“${result.mainOwnerName}”可以继续推进工作。`:`项目“${result.projectName}”已经进入规划。负责人“${result.mainOwnerName}”现在可以开始拆解工作计划。`):planDecision?(result.transitionAction==='CONFIRM_BASELINE'?`项目“${result.projectName}”的计划已经批准，项目现已进入执行。`:`项目“${result.projectName}”的计划已退回负责人调整。`):acceptanceDecision?(result.decision==='APPROVED'?`项目“${result.projectName}”已通过验收并正式结项。`:`项目“${result.projectName}”的验收资料已退回负责人补充。`):`项目“${result.projectName}”已创建成功，项目编号 ${result.projectNo}。当前为立项草稿，下一步可以让负责人开始规划。`})
      ElMessage.success(capabilityProjectCreate?'项目已创建':capabilityPlanDecision?(result.decision==='APPROVE'?'计划已批准并启动':'计划已退回'):capabilityAcceptanceDecision?(result.decision==='APPROVED'?'项目已验收结项':'验收已退回'):isCapabilityAction(request)?'操作已执行':budgetAdjustment?'预算已调整':acceptanceDecision?(result.decision==='APPROVED'?'项目已验收结项':'验收已退回'):planDecision?(result.transitionAction==='CONFIRM_BASELINE'?'计划已批准并启动':'计划已退回'):transition?(result.transitionAction==='RESUME_PROJECT'?'项目已恢复执行':'项目已进入规划'):'项目已创建')
    }else{
      await rejectBossAiAction(request.actionRequestId)
      syncActionRequest(request.actionRequestId,{status:'REJECTED'})
      aiMessages.value.push({role:'assistant',content:request.actionCode==='BUDGET_ADJUSTMENT'?'已取消预算调整，项目预算没有变化。':request.actionCode==='PROJECT_TRANSITION'?(isResume(request)?'已取消恢复，项目仍保持暂停状态。':'已取消推进，项目仍保持草稿状态。'):request.actionCode==='PROJECT_PLAN_DECISION'?'已取消这次计划审批，项目状态没有变化。':request.actionCode==='PROJECT_ACCEPTANCE_DECISION'||isCapabilityAcceptanceDecision(request)?'已取消这次验收决定，项目仍保持待验收状态。':isCapabilityAction(request)?'已取消这次系统操作，业务数据没有变化。':'这张立项确认单已取消，没有创建项目。你可以修改信息后重新告诉我。'})
      ElMessage.success('确认单已取消')
    }
  }finally{message.actionBusy=false;scrollAi()}
}
</script>

<style scoped>
.ai-page{display:flex;min-height:calc(100vh - 84px);padding:24px;flex-direction:column;background:#eef2f5;color:#182635}.ai-header{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:22px 28px;border-radius:16px 16px 0 0;background:linear-gradient(120deg,#102b3a,#17665f);color:#fff;box-shadow:0 12px 34px rgba(18,54,63,.16)}.ai-brand{display:flex;align-items:center;gap:16px}.eyebrow{font-size:10px;letter-spacing:.18em;color:#82ddd3}.ai-header h1{margin:4px 0;font-size:27px}.ai-header p{margin:0;color:#cae1df}.header-actions{display:flex;align-items:center;gap:10px}.header-actions :deep(.el-tag){border-color:rgba(133,226,210,.55);background:rgba(255,255,255,.1);color:#bcf0e5}.ai-workspace{display:grid;min-height:650px;flex:1;grid-template-columns:280px minmax(0,1fr);border:1px solid #d9e2e7;border-top:0;border-radius:0 0 16px 16px;background:#fff;box-shadow:0 12px 34px rgba(30,55,70,.08);overflow:hidden}.capability-panel{padding:24px;border-right:1px solid #dfe7eb;background:#f7fafb}.capability-panel h2{margin:0 0 16px;font-size:15px;color:#536b78}.capability-panel>button{display:flex;width:100%;align-items:center;gap:12px;margin-bottom:9px;padding:13px;border:1px solid #dde7ea;border-radius:10px;background:#fff;text-align:left;cursor:pointer}.capability-panel>button:hover{border-color:#50a89e;background:#f1fbf9}.capability-panel>button>span{display:grid;width:32px;height:32px;flex:0 0 32px;place-items:center;border-radius:9px;background:#e5f4f1;color:#18756c;font-size:12px;font-weight:750}.capability-panel b,.capability-panel small{display:block}.capability-panel small{margin-top:4px;color:#84939d;line-height:1.4}.safety-card{margin-top:24px;padding:15px;border-radius:10px;background:#eaf4f2;color:#426761}.safety-card p{margin:8px 0 0;font-size:12px;line-height:1.7}.chat-panel{display:flex;min-width:0;flex-direction:column}.ai-messages{height:calc(100vh - 355px);min-height:420px;padding:30px 36px;overflow:auto;background:linear-gradient(180deg,#fbfdfd,#fff)}.ai-welcome,.ai-message{display:flex;align-items:flex-start;gap:11px;margin-bottom:20px}.message-stack{max-width:82%;min-width:0}.ai-welcome>div,.ai-bubble{max-width:78%;padding:15px 18px;border-radius:5px 16px 16px 16px;background:#edf6f4;color:#293f48}.message-stack>.ai-bubble{max-width:none}.ai-welcome b{font-size:15px}.ai-welcome p,.ai-bubble p{margin:6px 0 0;line-height:1.75;white-space:pre-line}.ai-message.is-user{justify-content:flex-end}.ai-message.is-user .message-stack{max-width:78%}.ai-message.is-user .ai-bubble{border-radius:16px 5px 16px 16px;background:#246d89;color:#fff}.ai-message.is-user .ai-bubble p{margin:0}.ai-orb,.ai-mini-orb{display:grid;place-items:center;border-radius:50%;background:linear-gradient(135deg,#21a493,#245e7e);color:#fff;font-weight:750;box-shadow:0 5px 14px rgba(30,113,109,.22)}.ai-orb{width:48px;height:48px;flex:0 0 48px;font-size:12px}.ai-mini-orb{width:31px;height:31px;flex:0 0 31px;font-size:10px}.action-card{margin-top:12px;padding:18px;border:1px solid #e5d4ae;border-radius:13px;background:#fffdf8;box-shadow:0 7px 18px rgba(80,64,32,.08)}.action-card.is-executed{border-color:#b9ddd4;background:#f8fdfb}.action-card.is-rejected{border-color:#dfe3e5;background:#fafbfb}.action-head{display:flex;align-items:flex-start;justify-content:space-between}.action-head h3{margin:7px 0 0;font-size:16px}.action-head>span{color:#9b8b6d;font-size:11px}.project-name{margin:16px 0 12px;font-size:20px;font-weight:750;color:#203846}.action-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px}.action-grid>div,.objective{padding:10px;border-radius:8px;background:#f8f5ed}.action-grid small,.objective small{display:block;color:#8b826f}.action-grid b{display:block;margin-top:5px}.objective{margin-top:11px}.objective p{margin:6px 0 0;line-height:1.6}.plan-warnings{margin-top:11px;padding:11px;border-radius:8px;background:#fff4e6;color:#7d5424}.plan-warnings small{font-weight:700}.plan-warnings p{margin:7px 0 0;line-height:1.45}.plan-warnings p:before{content:'• ';}.action-buttons,.action-result{display:flex;align-items:center;justify-content:flex-end;gap:9px;margin-top:15px}.ai-thinking{display:flex;align-items:center;gap:4px}.ai-thinking i{width:5px;height:5px;border-radius:50%;background:#4d8d88;animation:pulse 1.1s infinite}.ai-thinking i:nth-child(2){animation-delay:.15s}.ai-thinking i:nth-child(3){animation-delay:.3s}.ai-thinking span{margin-left:6px;color:#6c8189;font-size:12px}@keyframes pulse{0%,70%,100%{opacity:.3;transform:translateY(0)}35%{opacity:1;transform:translateY(-3px)}}.composer-area{padding:16px 24px 14px;border-top:1px solid #e2e8eb;background:#fff}.quick-row{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:11px}.quick-row button{padding:7px 11px;border:1px solid #d0dfe3;border-radius:16px;background:#fff;color:#476a76;font-size:12px;cursor:pointer}.quick-row button:hover{border-color:#35958d;color:#187970}.ai-composer{display:flex;align-items:flex-end;gap:10px;padding:8px;border:1px solid #d3dee3;border-radius:12px}.ai-composer :deep(.el-textarea__inner){padding:7px 9px;border:0;box-shadow:none}.ai-composer .el-button{min-width:80px;height:42px}.composer-tip{display:block;margin-top:7px;color:#9aa6ad;text-align:right}
@media(max-width:900px){.ai-page{padding:14px}.ai-header{align-items:flex-start;flex-direction:column}.header-actions{width:100%;justify-content:space-between}.ai-workspace{grid-template-columns:1fr}.capability-panel{display:none}.ai-messages{height:calc(100vh - 390px);min-height:390px;padding:22px}.ai-welcome>div,.ai-bubble{max-width:88%}}
@media(max-width:600px){.ai-page{padding:10px}.ai-header{padding:18px}.ai-header h1{font-size:23px}.ai-header p{font-size:12px}.header-actions{align-items:stretch;flex-direction:column}.ai-messages{height:calc(100vh - 420px);padding:16px 12px}.composer-area{padding:12px}.quick-row{flex-wrap:nowrap;overflow-x:auto}.quick-row button{flex:0 0 auto}.ai-composer .el-button{min-width:62px}.ai-welcome>div,.ai-bubble{max-width:84%}.message-stack{max-width:90%}.action-grid{grid-template-columns:1fr}.action-buttons .el-button{flex:1}}
</style>
