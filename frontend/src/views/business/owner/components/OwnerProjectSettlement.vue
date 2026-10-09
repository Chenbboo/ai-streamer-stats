<template>
  <div class="owner-project-settlement">
    <header class="project-heading">
      <div><div class="title-line"><h2>{{ project.projectName }}</h2><BusinessProjectState :project="displayProject" /></div><p>{{ project.projectNo }} · {{ $tr('{0}负责', [project.mainOwnerName]) }}</p></div>
      <div class="project-heading-actions"><el-button link type="primary" @click="emit('all-projects')">← {{ $tr('全部项目') }}</el-button><el-button v-hasPermi="['business:project:list']" @click="openProject('overview')">{{ $tr('查看项目档案') }}</el-button></div>
    </header>
    <el-tabs v-model="tab" class="project-settlement-tabs">
      <el-tab-pane :label="$tr('项目概览')" name="overview">
        <section class="summary-grid" :aria-label="$tr('项目与结算摘要')">
          <button type="button" class="summary-card" @click="tab='settlement';scope='project'"><span>{{ $tr('项目结算待办') }}</span><b>{{ loadFailed ? '—' : pendingCount ?? '—' }} <small>{{ $tr('项') }}</small></b><em>{{ loadFailed ? $tr('暂未加载') : $tr('查看结算事项') }} →</em></button>
          <button type="button" class="summary-card" @click="tab='settlement';scope='company'"><span>{{ $tr('公司公共费用') }}</span><b>{{ loadFailed ? '—' : summary.pendingPublicExpenseCount ?? '—' }} <small>{{ $tr('项') }}</small></b><em>{{ $tr('项目关联的公司共享事项') }} →</em></button>
          <article class="summary-card"><span>{{ $tr('未完成任务 / 风险') }}</span><b>{{ openTaskCount }} <small>/ {{ openRiskCount }}</small></b><em>{{ $tr('当前项目') }}</em></article>
        </section>
        <div class="overview-grid">
          <section class="project-card">
            <div class="card-heading"><h3>{{ $tr('项目目标与进度') }}</h3></div><p class="objective">{{ project.objective || $tr('尚未填写项目目标') }}</p>
            <div class="card-divider"></div><div class="card-heading"><h4>{{ $tr('本月汇报进度') }}</h4><span v-if="progress !== null">{{ progress }}%</span><el-tag v-else type="info" effect="plain">{{ $tr('尚未汇报') }}</el-tag></div>
            <BusinessMonthlyProgress v-if="progress !== null" :project="project" :value="progress" :show-text="false" :stroke-width="6" />
            <div class="card-bottom"><span>{{ progress !== null ? $tr('最近汇报 {0}', [project.progressBizDate]) : $tr('等待负责人提交本月进度') }}</span><el-button v-hasPermi="['business:project:list']" link type="primary" @click="openReports()">{{ $tr('查看汇报') }}</el-button></div>
          </section>
          <section class="project-card"><div class="card-heading"><h3>{{ $tr('项目信息') }}</h3></div><dl class="project-info"><dt>{{ $tr('项目负责人') }}</dt><dd>{{ project.mainOwnerName || '—' }}</dd><dt>{{ $tr('发起人') }}</dt><dd>{{ project.sponsorOwnerName || project.initiatorName || '—' }}</dd><dt>{{ $tr('计划周期') }}</dt><dd>{{ project.planStartDate ? $tr('{0} 至 {1}', [project.planStartDate, project.planEndDate || $tr('不限期')]) : '—' }}</dd><dt>{{ $tr('归属公司') }}</dt><dd>{{ project.companyName || $tr('待设置') }}</dd></dl></section>
          <section v-if="project.kpiEnabled!==false" class="project-card">
            <div class="card-heading"><h3>{{ $tr('项目指标') }}</h3><el-button v-if="project.kpiEnabled!==false" v-hasPermi="['business:kpi:list']" link type="primary" @click="openKpi()">{{ selectedPlan ? $tr('查看指标结果') : $tr('管理项目指标') }}</el-button></div>
            <el-alert v-if="kpiFailed" :title="$tr('指标数据暂未加载，请刷新重试')" type="warning" :closable="false" />
            <template v-else-if="selectedPlan"><div class="kpi-period"><span>{{ $tr('方案 v{0} · {1} 至 {2}', [selectedPlan.planVersion, selectedPlan.cycleStart, selectedPlan.cycleEnd]) }}</span><el-tag :type="kpiTone(selectedPlan.settlement?.status)" size="small">{{ kpiLabel(selectedPlan.settlement?.status) }}</el-tag></div>
              <div v-for="item in kpiItems" :key="item.itemId" class="kpi-summary-row"><div><b>{{ item.kpiName }}</b><small>{{ $tr('权重 {0}%', [item.weight]) }}</small></div><strong>{{ item.dataStatus === 'PENDING_COST' ? $tr('待计价') : item.actualValue ?? '—' }} / {{ item.targetValue }} {{ $tr(item.unit) }}</strong></div>
            </template>
            <div v-else class="empty-state">{{ $tr('尚未发布考核方案') }}</div>
          </section>
          <section class="project-card"><div class="card-heading"><h3>{{ $tr('收支概况') }}</h3><el-button link type="primary" @click="emit('people')">{{ $tr('详情') }}</el-button></div><template v-if="!loadFailed && summary.managementFee?.preFeeProfit != null && project.accountingMode === 'PROFIT'"><span class="muted">{{ $tr('管理费前利润') }}</span><div class="profit-amount">{{ money(summary.managementFee.preFeeProfit) }} <small>{{ summary.managementFee.currency || project.baseCurrency }}</small></div></template><p v-else class="muted">{{ loadFailed ? $tr('暂未加载') : $tr('查看本项目收入与成本明细') }}</p><p class="muted">{{ $tr('收入、成本明细在人员与收支中查看') }}</p></section>
          <section v-if="canViewProjects && !project.parentId" class="project-card children-card" v-loading="childrenLoading">
            <div class="card-heading"><h3>{{ $tr('子项目') }}</h3><span class="muted">{{ childrenFailed ? $tr('暂未加载') : $tr('{0} 个子项目', [children.length]) }}</span></div>
            <el-alert v-if="childrenFailed" :title="$tr('子项目暂未加载，请刷新重试')" type="warning" :closable="false" />
            <div v-else-if="!children.length" class="empty-state">{{ $tr('暂无子项目') }}</div>
            <div v-for="child in children" :key="child.projectId" class="child-row">
              <div class="child-summary">
                <el-button link type="primary" class="child-name" @click="openProject('overview', child.projectId)">{{ child.projectName }}</el-button>
                <BusinessProjectState :project="child" />
                <p class="muted">{{ $tr('负责人：{0}', [child.mainOwnerName || '—']) }} · {{ $tr(child.closeMethod === 'STAGED_ACCEPTANCE' ? '阶段验收' : child.closeMethod === 'RESULT_ACCEPTANCE' ? '成果验收' : '直接结项') }}</p>
                <p class="child-objective">{{ child.objective || $tr('尚未填写项目目标') }}</p>
              </div>
              <div class="child-period"><span class="muted">{{ $tr('计划周期') }}</span><p>{{ child.planStartDate ? $tr('{0} 至 {1}', [child.planStartDate, child.planEndDate || $tr('不限期')]) : '—' }}</p></div>
              <div class="child-progress">
                <div class="child-progress-heading"><span class="muted">{{ $tr('本月汇报进度') }}</span><strong v-if="projectProgress(child) !== null">{{ projectProgress(child) }}%</strong><el-tag v-else type="info" effect="plain" size="small">{{ $tr('尚未汇报') }}</el-tag></div>
                <BusinessMonthlyProgress v-if="projectProgress(child) !== null" :project="child" :value="projectProgress(child)" :show-text="false" :stroke-width="5" />
                <span class="child-report-date muted">{{ projectProgress(child) !== null ? $tr('最近汇报 {0}', [child.progressBizDate]) : $tr('等待负责人提交本月进度') }}</span>
                <el-button link type="primary" @click="openReports(child.projectId)">{{ $tr('查看汇报') }}</el-button>
              </div>
              <div class="child-actions"><el-button plain type="primary" @click="openProject(deliveryTab(child), child.projectId)">{{ $tr(child.closeMethod === 'STAGED_ACCEPTANCE' ? '查看里程碑验收' : child.closeMethod === 'RESULT_ACCEPTANCE' ? '查看成果验收' : '查看项目详情') }}</el-button></div>
            </div>
            <p v-if="children.length" class="child-note muted">{{ $tr('汇报进度由子项目负责人填报，验收结果请在验收页面查看。') }}</p>
          </section>
        </div>
        <details v-if="project.costPolicyVersion==='MEMBER_DAYS_V1'" class="project-card plan-details"><summary>{{ $tr('项目计划与变更') }}</summary><BusinessProjectPlanPanel v-if="planExpanded" :project="project" @changed="emit('refresh')" /><el-button v-else link type="primary" @click="planExpanded=true">{{ $tr('查看计划与变更记录') }}</el-button></details>
      </el-tab-pane>
      <el-tab-pane name="settlement">
        <template #label><span>{{ $tr('结算办理') }} <el-tag v-if="pendingCount" size="small" type="warning">{{ pendingCount }}</el-tag></span></template>
        <el-alert v-if="loadFailed" :title="$tr('结算事项暂未加载，请刷新重试')" type="warning" :closable="false" show-icon />
        <template v-else>
          <div class="scope-toolbar"><el-radio-group v-model="scope"><el-radio-button value="project">{{ $tr('项目事项') }} · {{ pendingCount ?? '—' }}</el-radio-button><el-radio-button value="company">{{ $tr('公司共享') }} · {{ summary.pendingPublicExpenseCount ?? '—' }}</el-radio-button></el-radio-group><span class="muted">{{ $tr('各项可分别办理，公司公共费用不计入项目待办总数') }}</span></div>
          <section v-if="scope==='project'" class="project-card">
            <div class="card-heading"><h3>{{ $tr('项目结算事项') }}</h3><el-button v-if="project.kpiEnabled!==false" v-hasPermi="['business:kpi:list']" link type="primary" @click="openKpi()">{{ $tr('查看 KPI 结果与历史') }}</el-button></div>
            <el-alert v-if="project.kpiEnabled!==false && kpiFailed && Number(summary.pendingKpiCount)>0" :title="$tr('指标数据暂未加载，请刷新重试')" type="warning" :closable="false" />
            <el-table :data="settlementRows" :empty-text="$tr('当前没有项目结算待办')" row-key="key"><el-table-column :label="$tr('事项 / 周期')" min-width="210"><template #default="{row}"><b>{{ row.title }}</b><small class="table-note">{{ row.detail }}</small></template></el-table-column><el-table-column :label="$tr('当前状态')" min-width="120"><template #default="{row}"><el-tag :type="row.tone" effect="plain">{{ row.status }}</el-tag></template></el-table-column><el-table-column :label="$tr('下一步')" min-width="190"><template #default="{row}">{{ row.next }}</template></el-table-column><el-table-column :label="$tr('操作')" width="135" fixed="right"><template #default="{row}"><el-button type="primary" plain size="small" @click="selectedItem=row;itemDialog=true">{{ $tr('查看并办理') }}</el-button></template></el-table-column></el-table>
          </section>
          <section v-else class="project-card"><div class="card-heading"><h3>{{ $tr('公司公共费用') }}</h3><el-tag effect="plain">{{ $tr('公司共享') }}</el-tag></div><p class="muted">{{ $tr('公司级事项由多个项目引用，待分摊金额不代表当前项目应承担的费用。') }}</p><el-alert v-if="publicExpenseFailed" :title="$tr('公共费用待办暂未加载，请刷新重试')" type="warning" :closable="false" /><div v-for="item in companyTodos" :key="item.key" class="company-row"><div><b>{{ item.title }}</b><small>{{ item.detail }}</small></div><el-button size="small" type="primary" @click="emit('public-expense', item)">{{ $tr('查看并办理') }}</el-button></div><p v-if="!companyTodos.length" class="empty-state">{{ Number(summary.pendingPublicExpenseCount)>0 ? $tr('公司公共费用尚待月结，请查看公司费用状态') : $tr('当前项目没有关联的公共费用待办') }}</p><div class="card-bottom"><el-button @click="emit('public-expense')">{{ $tr('进入公共费用') }}</el-button><el-button v-hasPermi="['business:public-expense:list']" link type="primary" @click="openCompanyExpenses">{{ $tr('查看公司月结状态') }}</el-button></div></section>
        </template>
        <BusinessSettlementPanel :project="project" compact :refresh-key="refreshKey" @closed="emit('refresh')" />
      </el-tab-pane>
    </el-tabs>
    <BusinessProjectProgress ref="progressPanel" :allow-submit="false" />
    <el-drawer v-model="itemDialog" :title="selectedItem?.title" size="min(500px, 94vw)" append-to-body destroy-on-close>
      <template v-if="selectedItem"><el-tag :type="selectedItem.tone">{{ selectedItem.status }}</el-tag><p class="drawer-period">{{ selectedItem.detail }}</p><el-descriptions :column="1" border><el-descriptions-item :label="$tr('归属项目')">{{ project.projectName }}</el-descriptions-item><el-descriptions-item :label="$tr('下一步')">{{ selectedItem.next }}</el-descriptions-item></el-descriptions><div v-if="selectedItem.kind==='kpi' && Number(selectedItem.planId)===Number(selectedPlan?.planId)" class="drawer-metrics"><div v-for="item in kpiItems" :key="item.itemId" class="kpi-summary-row"><div><b>{{ item.kpiName }}</b><small>{{ $tr('权重 {0}%', [item.weight]) }}</small></div><strong>{{ item.dataStatus === 'PENDING_COST' ? $tr('待计价') : item.actualValue ?? '—' }} / {{ item.targetValue }} {{ $tr(item.unit) }}</strong></div></div><el-alert class="drawer-hint" :title="selectedItem.hint" type="info" :closable="false" show-icon /><el-button type="primary" :disabled="!canOpenItem(selectedItem)" @click="handleItem(selectedItem)">{{ $tr('进入办理页面') }}</el-button><p v-if="!canOpenItem(selectedItem)" class="muted">{{ $tr('当前账号没有该事项的查看权限，请联系对应办理人') }}</p></template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { translateText } from '@/locales/translate'
import useUserStore from '@/store/modules/user'
import { getBusinessProjectChildren } from '@/api/business/project'
import { projectSettlementCount, pendingKpiPlans, reportedProjectProgress } from '@/utils/ownerSettlement'
import BusinessProjectState from '@/components/BusinessProjectState/index.vue'
import BusinessSettlementPanel from '@/components/BusinessSettlementPanel/index.vue'
import BusinessProjectPlanPanel from '@/components/BusinessProjectPlanPanel/index.vue'
import BusinessProjectProgress from '@/components/BusinessProjectProgress/index.vue'
import BusinessMonthlyProgress from '@/components/BusinessMonthlyProgress/index.vue'

const props=defineProps({project:{type:Object,required:true},summary:{type:Object,default:()=>({})},kpi:{type:Object,default:null},loadFailed:Boolean,kpiFailed:Boolean,publicExpenseFailed:Boolean,publicExpenseTodos:{type:Array,default:()=>[]},publicExpenseBills:{type:Array,default:()=>[]},openTaskCount:{type:Number,default:0},openRiskCount:{type:Number,default:0},initialTab:{type:String,default:'overview'},refreshKey:{type:Number,default:0}})
const emit=defineEmits(['people','public-expense','refresh','all-projects','update:initialTab'])
const router=useRouter(),userStore=useUserStore()
const tab=ref(props.initialTab),scope=ref('project'),itemDialog=ref(false),selectedItem=ref(null),planExpanded=ref(false)
const children=ref([]),childrenLoading=ref(false),childrenFailed=ref(false)
const progressPanel=ref(null)
const can=key=>userStore.permissions.includes('*:*:*')||userStore.permissions.includes(key)
const canViewProjects=computed(()=>can('business:project:list'))
const displayProject=computed(()=>({...props.project,...props.summary}))
const month=()=>{const date=new Date();return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`}
const projectProgress=project=>reportedProjectProgress(project,month())
const progress=computed(()=>projectProgress(props.project))
const pendingCount=computed(()=>projectSettlementCount(props.summary))
const selectedPlan=computed(()=>props.kpi?.selectedPlan?.status==='VOIDED'?null:props.kpi?.selectedPlan)
const kpiItems=computed(()=>(selectedPlan.value?.items||[]).map(item=>{const result=(selectedPlan.value.settlement?.results||[]).find(result=>Number(result.planItemId)===Number(item.itemId));return {...item,actualValue:result?.actualValue,dataStatus:result?.dataStatus}}))
const kpiLabel=status=>({DRAFT:translateText('填报中'),RETURNED:translateText('已退回'),SUBMITTED:translateText('历史待确认'),CONFIRMED:translateText('已确认')}[status]||translateText('未开始'))
const kpiTone=status=>({DRAFT:'warning',RETURNED:'danger',SUBMITTED:'warning',CONFIRMED:'success'}[status]||'info')
const money=value=>Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const companyTodos=computed(()=>props.publicExpenseTodos.filter(item=>props.publicExpenseBills.some(bill=>String(bill.allocationId)===String(item.allocationId)&&String(bill.companyDeptId)===String(props.project.companyDeptId))))
const settlementRows=computed(()=>{
  const rows=(props.project.kpiEnabled===false?[]:pendingKpiPlans(props.kpi)).map(plan=>({key:`kpi-${plan.planId}`,kind:'kpi',planId:plan.planId,title:translateText('KPI 结果确认'),detail:translateText('方案 v{0} · {1} 至 {2}',[plan.planVersion,plan.cycleStart,plan.cycleEnd]),status:kpiLabel(plan.settlementStatus),tone:kpiTone(plan.settlementStatus),next:plan.settlementStatus==='SUBMITTED'?translateText('由原流程审核人处理'):translateText('核对并填报本期结果'),hint:translateText('周期结束或全部目标达标后可确认；最终条件请在 KPI 结果页面核对。')}))
  if(props.project.kpiEnabled!==false && Number(props.summary.pendingKpiCount)>rows.length)rows.push({key:'kpi-other',kind:'kpi',title:translateText('其他 KPI 结算'),detail:translateText('{0} 项',[Number(props.summary.pendingKpiCount)-rows.length]),status:translateText('待处理'),tone:'warning',next:translateText('核对全部方案与结果'),hint:translateText('周期结束或全部目标达标后可确认；最终条件请在 KPI 结果页面核对。')})
  const definitions=[['pendingEffortCount','effort',translateText('人员投入确认'),translateText('核对并确认人员投入')],['pendingFactCount','fact',translateText('收支待处理'),translateText('核对收支来源与确认状态')],['pendingCostCount','cost',translateText('人员成本待完善'),translateText('核对人员投入与成本配置')],['pendingAwardCount','award',translateText('奖金待处理'),translateText('查看奖金申请与核准状态')],['pendingLeaveCount','leave',translateText('假勤待处理'),translateText('核对假勤来源与同步状态')]]
  for(const [field,kind,title,next] of definitions){const count=Number(props.summary[field]||0);if(count>0)rows.push({key:field,kind,title,detail:translateText('{0} 项',[count]),status:translateText('待处理'),tone:'warning',next,hint:translateText('请在现有办理页面核对明细，操作沿用该页面的权限与状态检查。')})}
  return rows
})
function deliveryTab(project){return project.closeMethod==='STAGED_ACCEPTANCE'?'stageAcceptance':project.closeMethod==='RESULT_ACCEPTANCE'?'acceptance':'overview'}
function openProject(tab='overview',id=props.project.projectId){router.push({path:'/business/projects',query:{id,tab}})}
function openReports(id=props.project.projectId){progressPanel.value?.open({projectId:id})}
function openKpi(planId){if(!can('business:kpi:list'))return;router.push({path:planId||selectedPlan.value?'/projects/kpi-results':'/business/kpi-bonus',query:{projectId:props.project.projectId,...(planId?{planId}:selectedPlan.value?{planId:selectedPlan.value.planId}:{})}})}
function openCompanyExpenses(){router.push({path:'/finance/public-expenses',query:{companyDeptId:props.project.companyDeptId,month:props.summary.actualEndDate?.slice(0,7)||month()}})}
function canOpenItem(item){return item.kind==='kpi'?can('business:kpi:list'):item.kind==='fact'?can('business:accounting:list'):item.kind==='award'?can('business:incentive:list'):item.kind==='leave'?can('business:attendance:self'):true}
function handleItem(item){if(!canOpenItem(item))return;itemDialog.value=false;if(item.kind==='kpi')return openKpi(item.planId);if(item.kind==='fact')return router.push({path:'/business/accounting',query:{projectId:props.project.projectId}});if(item.kind==='award')return router.push({path:'/hcm/incentives',query:{projectId:props.project.projectId}});if(item.kind==='leave')return router.push({path:'/hcm/attendance',query:{companyDeptId:props.project.companyDeptId}});emit('people')}
let childrenRequest=0
watch(()=>[props.project.projectId,props.refreshKey],async(value,previous)=>{
  const request=++childrenRequest;if(!previous||value[0]!==previous[0]){tab.value=props.initialTab;scope.value='project';itemDialog.value=false;planExpanded.value=false}children.value=[];childrenFailed.value=false;childrenLoading.value=false
  if(props.project.parentId||!canViewProjects.value)return
  childrenLoading.value=true
  try{const response=await getBusinessProjectChildren(props.project.projectId);if(request===childrenRequest)children.value=response.data||[]}
  catch{if(request===childrenRequest)childrenFailed.value=true}
  finally{if(request===childrenRequest)childrenLoading.value=false}
},{immediate:true})
watch(()=>props.initialTab,value=>{tab.value=value})
watch(tab,value=>emit('update:initialTab',value))
</script>

<style scoped>
.child-row{display:grid;grid-template-columns:minmax(230px,1.4fr) minmax(170px,.8fr) minmax(190px,.9fr) auto;align-items:center;gap:24px;padding:18px 0;border-top:1px solid var(--el-border-color-lighter);font-size:13px}
.child-row>div{min-width:0}.child-name{font-size:14px;font-weight:600;white-space:normal;text-align:left;line-height:1.5;height:auto;padding:0;margin-bottom:9px}.child-name :deep(span){overflow-wrap:anywhere}.child-summary p{margin:8px 0 0;line-height:1.6}.child-objective{color:var(--el-text-color-regular);overflow-wrap:anywhere}.child-summary :deep(.el-tag){height:22px;font-size:11px}.child-period p{margin:9px 0 0;line-height:1.6}.child-progress-heading{display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:10px}.child-progress-heading strong{font-size:16px;font-variant-numeric:tabular-nums}.child-report-date{display:block;margin:9px 0 4px;line-height:1.5}.child-actions{justify-self:end}.child-note{border-top:1px solid var(--el-border-color-lighter);margin:0;padding-top:12px;line-height:1.6}
@media(max-width:1300px){.child-row{grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:18px 24px}.child-progress{grid-column:1}.child-actions{grid-column:2;justify-self:start}.child-period{align-self:start}}
@media(max-width:600px){.child-row{grid-template-columns:minmax(0,1fr);gap:16px}.child-progress,.child-actions{grid-column:1}.child-actions{justify-self:stretch}.child-actions .el-button{width:100%}.child-progress{max-width:100%}}
.project-heading-actions{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.project-heading-actions .el-button{margin-left:0}
.owner-project-settlement{color:var(--el-text-color-primary)}.project-heading,.title-line,.card-heading,.card-bottom,.scope-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.project-heading{margin:6px 0 18px;align-items:flex-start}.project-heading h2{margin:0;font-size:24px}.title-line{justify-content:flex-start}.project-heading p,.muted,.card-bottom,.project-info dt,.kpi-period,.table-note{color:var(--el-text-color-secondary);font-size:12px}.project-heading p{margin:7px 0 0}.summary-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px;margin:4px 0 18px}.summary-card,.project-card{border:1px solid var(--el-border-color-lighter);border-radius:9px;background:var(--el-bg-color);padding:18px;min-width:0}.summary-card{font:inherit;text-align:left;color:inherit}.summary-card:is(button){cursor:pointer}.summary-card span,.summary-card em{display:block;font-size:12px;color:var(--el-text-color-secondary);font-style:normal}.summary-card b{display:block;margin:8px 0 5px;font-size:27px;font-weight:600}.summary-card small{font-size:13px;font-weight:400;color:var(--el-text-color-secondary)}button.summary-card em{color:var(--el-color-primary)}.overview-grid{display:grid;grid-template-columns:minmax(0,1.55fr) minmax(260px,1fr);gap:16px}.card-heading{margin-bottom:16px}.card-heading h3,.card-heading h4{margin:0;font-size:15px}.objective{font-size:13px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere;margin:0}.card-divider{height:1px;background:var(--el-border-color-lighter);margin:16px 0}.card-bottom{margin-top:15px}.project-info{display:grid;grid-template-columns:76px minmax(0,1fr);gap:12px;font-size:13px;margin:0}.project-info dd{margin:0;overflow-wrap:anywhere}.kpi-period{display:flex;gap:8px;justify-content:space-between;align-items:center;flex-wrap:wrap;margin-bottom:10px}.kpi-summary-row,.company-row{display:flex;align-items:center;justify-content:space-between;gap:15px;border-bottom:1px solid var(--el-border-color-lighter);padding:13px 0;font-size:13px}.kpi-summary-row:last-child,.company-row:last-child{border-bottom:0}.kpi-summary-row small,.company-row small{display:block;margin-top:4px;color:var(--el-text-color-secondary);font-size:12px}.kpi-summary-row strong{font-size:14px;white-space:nowrap}.profit-amount{font-size:26px;font-variant-numeric:tabular-nums;margin:10px 0}.profit-amount small{font-size:12px;color:var(--el-text-color-secondary)}.empty-state{text-align:center;padding:20px 0;color:var(--el-text-color-secondary);font-size:13px}.children-card{grid-column:1/-1}.plan-details{margin-top:16px}.plan-details summary{cursor:pointer;font-size:14px}.plan-details>.el-button{margin-top:12px}.scope-toolbar{margin:4px 0 18px}.table-note{display:block;margin-top:6px}.drawer-period{color:var(--el-text-color-secondary);font-size:13px;margin:16px 0}.drawer-hint{margin:20px 0}.company-row{flex-wrap:wrap}.project-settlement-tabs :deep(.el-tabs__header){margin-bottom:20px}.project-card :deep(.el-table){--el-table-header-bg-color:var(--el-fill-color-light)}
@media(max-width:1100px){.overview-grid{grid-template-columns:1fr}.children-card{grid-column:auto}}
@media(max-width:600px){.project-heading{gap:14px}.project-heading h2{font-size:21px}.project-card{padding:14px}.summary-grid{gap:8px}.summary-card{padding:12px 9px}.summary-card b{font-size:24px}.summary-card em{line-height:1.7}.kpi-summary-row{align-items:flex-start;flex-direction:column}.kpi-summary-row strong{white-space:normal}.scope-toolbar{align-items:stretch}.scope-toolbar :deep(.el-radio-group){display:flex;width:100%}.scope-toolbar :deep(.el-radio-button){flex:1}.scope-toolbar :deep(.el-radio-button__inner){width:100%}.project-info{grid-template-columns:72px minmax(0,1fr)}}
</style>
