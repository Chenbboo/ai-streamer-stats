<template>
  <div class="app-container kpi-page" v-loading="loading">
    <header class="kpi-hero">
      <div><span>{{ resultsOnly ? 'PROJECT KPI RESULTS' : 'PROJECT KPI SETTINGS' }}</span><h1>{{ resultsOnly ? '项目 KPI 结果' : '项目 KPI 设置' }}</h1><p>{{ resultsOnly ? '填报项目实际结果，查看并确认各期指标。' : '设置项目指标、目标值、权重和考核周期，发布并管理 KPI 方案。' }}</p></div>
      <div class="hero-tools">
        <el-select v-model="selectedProjectId" filterable placeholder="选择项目" @change="switchProject">
          <el-option v-for="project in projects" :key="project.projectId" :label="`${project.projectName} · ${project.mainOwnerName}`" :value="project.projectId" />
        </el-select>
        <el-button icon="Refresh" @click="loadWorkspace(selectedProjectId, selectedPlan?.planId)">刷新</el-button>
      </div>
    </header>

    <el-empty v-if="!selectedProjectId && !loading" description="当前没有可管理的项目" />

    <template v-if="workspace.project">
      <section class="summary-grid" :class="{ 'settings-summary': !resultsOnly }">
        <article><span>项目</span><b>{{ workspace.project.projectName }}</b><small>{{ workspace.project.mainOwnerName }}负责</small></article>
        <article><span>当前KPI</span><b>{{ currentTargets.length }} 项</b><small>权重合计 {{ weightTotal }}%</small></article>
        <article><span>考核方案</span><b>{{ selectedPlan ? `v${selectedPlan.planVersion}` : '未发布' }}</b><small>{{ selectedPlan ? `${cycleLabel[selectedPlan.cycleType]} · ${selectedPlan.cycleStart} 至 ${selectedPlan.cycleEnd}` : '设置指标后发布考核方案' }}</small></article>
        <article v-if="resultsOnly"><span>结算状态</span><b>{{ settlementLabel[settlement?.status] || '未开始' }}</b><small>{{ settlement?.reviewComment || '项目级结算，不涉及个人奖金' }}</small></article>
        <article v-if="resultsOnly"><span>综合得分</span><b>{{ settlement?.totalScore ?? '—' }}</b><small>{{ isLegacyPlan ? '历史奖金联动方案' : '奖金激励单独核准' }}</small></article>
      </section>

      <div class="content-grid">
        <main>
          <el-card v-if="!resultsOnly" shadow="never" class="section-card">
            <div class="section-head">
              <div><h2>项目KPI目标</h2><p>只考核项目。调整目标会生成新版本，已发布方案继续使用原快照。</p></div>
              <el-button v-if="canEditPlan" v-hasPermi="['business:kpi:manage']" type="primary" @click="openTarget()">新增KPI</el-button>
            </div>
            <el-alert v-if="currentTargets.length && Number(weightTotal)!==100" :title="`当前权重合计 ${weightTotal}%，必须调整为100%后才能发布方案。`" type="warning" :closable="false" show-icon />
            <el-table :data="currentTargets" empty-text="项目负责人尚未设置项目KPI">
              <el-table-column label="指标" min-width="180"><template #default="{row}"><b>{{ row.kpiName }}</b><small>{{ row.kpiCode }} · v{{ row.targetVersion }}</small></template></el-table-column>
              <el-table-column label="目标" min-width="125"><template #default="{row}">{{ row.targetValue }} {{ row.unit || '' }}</template></el-table-column>
              <el-table-column label="方向" width="95"><template #default="{row}">{{ directionLabel[row.direction] }}</template></el-table-column>
              <el-table-column label="数据来源" min-width="150"><template #default="{row}"><el-tag :type="row.sourceType==='MANUAL'?'info':'success'">{{ sourceTypeLabel[row.sourceType] || row.sourceType }}</el-tag><small v-if="row.sourceRefId">{{ sourceReferenceLabel(row) }}</small></template></el-table-column>
              <el-table-column label="权重" width="85"><template #default="{row}">{{ row.weight }}%</template></el-table-column>
              <el-table-column label="周期" width="95"><template #default="{row}">{{ cycleLabel[row.periodType] }}</template></el-table-column>
              <el-table-column v-if="canEditPlan" label="操作" width="120"><template #default="{row}"><el-button link @click="openTarget(row)">调整</el-button><el-button link type="danger" @click="retireTarget(row)">停用</el-button></template></el-table-column>
            </el-table>
          </el-card>

          <el-card v-if="!resultsOnly && selectedPlan" shadow="never" class="section-card">
            <div class="section-head"><div><h2>方案指标快照 · v{{ selectedPlan.planVersion }}</h2><p>{{ cycleLabel[selectedPlan.cycleType] }} · {{ selectedPlan.cycleStart }} 至 {{ selectedPlan.cycleEnd }}。发布时的指标设置保留如下。</p></div></div>
            <el-table :data="selectedPlan.items || []" empty-text="暂无指标快照">
              <el-table-column prop="kpiName" label="指标" min-width="180" />
              <el-table-column label="目标" min-width="125"><template #default="{row}">{{ row.targetValue }} {{ row.unit || '' }}</template></el-table-column>
              <el-table-column label="方向" width="95"><template #default="{row}">{{ directionLabel[row.direction] }}</template></el-table-column>
              <el-table-column label="数据来源" min-width="150"><template #default="{row}">{{ sourceTypeLabel[row.sourceType] || row.sourceType }}</template></el-table-column>
              <el-table-column label="权重" width="85"><template #default="{row}">{{ row.weight }}%</template></el-table-column>
            </el-table>
            <p v-if="selectedPlan.remark" class="plan-remark">方案说明：{{ selectedPlan.remark }}</p>
          </el-card>

          <el-card v-if="resultsOnly" shadow="never" class="section-card">
            <div class="section-head">
              <div><h2>项目结果与结算</h2><p>自动指标持续读取系统数据，手工指标由负责人填报；截止日期后确认结果快照；新方案不自动批准奖金或计入成本。</p></div>
              <div class="section-actions" v-if="settlement && workspace.canSettle && ['DRAFT','RETURNED'].includes(settlement.status)">
                <el-button v-if="manualItems.length" v-hasPermi="['business:kpi:settle']" :loading="saving" @click="saveResults">保存手工草稿</el-button>
                <el-button v-hasPermi="['business:kpi:settle']" type="primary" :loading="saving" :disabled="!periodEnded || !canConfirmResults" @click="submitResults">{{ needsLegacyReview ? '重新提交审核' : '确认指标结果' }}</el-button>
              </div>
            </div>
            <el-alert v-if="costPending" title="项目仍有投入待计价，人员成本及利润指标显示待完善。请先完成全项目成本计价，再确认结果；其他手工结果可先保存。" type="warning" :closable="false" show-icon />
            <el-alert v-if="settlement?.status==='RETURNED'" :title="`老板退回：${settlement.reviewComment}`" type="warning" :closable="false" show-icon />
            <el-alert v-if="settlement && !periodEnded && ['DRAFT','RETURNED'].includes(settlement.status)" :title="`考核统计截至 ${settlement.periodEnd} 当天；现在可以保存手工草稿，次日才能确认最终结算。`" type="info" :closable="false" show-icon />
            <el-empty v-if="!selectedPlan" description="尚未发布KPI方案" />
            <div v-else class="result-list">
              <article v-for="item in selectedPlan.items || []" :key="item.itemId" class="result-row">
                <div class="result-target"><b>{{ item.kpiName }}</b><span>目标 {{ item.targetValue }} {{ item.unit || '' }} · 权重 {{ item.weight }}% · {{ directionLabel[item.direction] }}</span><el-tag size="small" :type="isAutomatic(item)?'success':'info'">{{ sourceTypeLabel[item.sourceType] || item.sourceType }}</el-tag></div>
                <template v-if="workspace.canSettle && ['DRAFT','RETURNED'].includes(settlement?.status) && !isAutomatic(item)">
                  <el-input-number v-model="resultDraft[item.itemId].actualValue" :min="0" :precision="4" controls-position="right" placeholder="实际值" />
                  <el-input v-model="resultDraft[item.itemId].resultNote" maxlength="1000" show-word-limit placeholder="填写数据来源、结果说明或异常原因" />
                  <business-file-upload v-model="resultDraft[item.itemId].attachmentUrls" :project-id="selectedProjectId" />
                </template>
                <template v-else>
                  <div class="result-value"><strong>{{ resultFor(item.itemId)?.actualValue ?? '—' }} {{ item.unit || '' }}</strong><span>完成率 {{ resultFor(item.itemId)?.completionRate ?? '—' }}% · 加权 {{ resultFor(item.itemId)?.weightedScore ?? '—' }}</span></div>
                  <p>{{ resultFor(item.itemId)?.resultNote || '尚未填报' }}</p>
                  <div v-if="resultFor(item.itemId)?.attachmentUrls" class="result-attachments">
                    <span>结果附件</span>
                    <business-file-upload :model-value="resultFor(item.itemId).attachmentUrls" :project-id="selectedProjectId" disabled :drag="false" :is-show-tip="false" />
                  </div>
                </template>
              </article>
            </div>
            <div v-if="settlement?.status==='SUBMITTED' && workspace.canReview" class="review-bar">
              <div><b>升级前结算等待处理</b><span>这是旧流程遗留记录，确认后 ¥{{ money(settlement.bonusAmount) }} 将计入项目成本。</span></div>
              <el-button type="warning" plain :loading="saving" @click="returnSettlement">退回修改</el-button>
              <el-button type="success" :loading="saving" @click="confirmSettlement">确认并计入成本</el-button>
            </div>
            <el-result v-if="settlement?.status==='CONFIRMED'" icon="success" :title="isLegacyPlan ? '历史 KPI 与奖金已确认' : '项目指标已确认'" :sub-title="confirmedSummary" />
          </el-card>
        </main>

        <aside>
          <el-card v-if="!resultsOnly" shadow="never" class="section-card">
            <div class="section-head"><div><h2>指标方案</h2><p>将当前指标、目标和权重发布为指定考核周期的方案。</p></div><el-button v-if="canEditPlan" v-hasPermi="['business:kpi:manage']" type="primary" :disabled="Number(weightTotal)!==100 || !currentTargets.length" @click="openPlan">发布新方案</el-button></div>
          </el-card>
          <el-card v-if="resultsOnly && isLegacyPlan" shadow="never" class="section-card">
            <h3>历史奖金阶梯</h3><p>保留旧方案规则与历史金额；不代表已分配或已支付。</p><div v-for="tier in selectedPlan?.tiers || []" :key="tier.tierId" class="tier-row"><span><b>{{ tier.tierName }}</b><small>{{ scoreRange(tier) }}</small></span><strong>¥{{ money(tier.bonusAmount) }}</strong></div>
          </el-card>

          <el-card shadow="never" class="section-card">
            <div class="section-head"><div><h2>方案历史</h2><p>{{ resultsOnly ? '选择方案查看对应结果。' : '选择方案查看发布时的指标、目标和权重。' }}</p></div></div>
            <div v-if="!workspace.plans?.length" class="empty-text">暂无历史方案</div>
            <div v-for="plan in workspace.plans || []" :key="plan.planId" role="button" tabindex="0" class="plan-row" :class="{active:Number(plan.planId)===Number(selectedPlan?.planId)}" @click="loadWorkspace(selectedProjectId,plan.planId)" @keyup.enter="loadWorkspace(selectedProjectId,plan.planId)">
              <span><b>v{{ plan.planVersion }} · {{ cycleLabel[plan.cycleType] }}</b><small>{{ plan.cycleStart }} 至 {{ plan.cycleEnd }}</small></span>
              <span class="plan-row-actions"><el-tag v-if="resultsOnly" :type="settlementTone[plan.settlementStatus]">{{ settlementLabel[plan.settlementStatus] }}</el-tag><el-tag v-else type="info">{{ planStatusLabel[plan.status] || plan.status }}</el-tag><el-button v-if="!resultsOnly && workspace.canVoid && ['DRAFT','RETURNED'].includes(plan.settlementStatus)" v-hasPermi="['business:kpi:manage']" link type="danger" @click.stop="voidPlan(plan)">作废</el-button></span>
            </div>
          </el-card>
        </aside>
      </div>
    </template>

    <el-dialog v-model="targetDialog" :title="targetForm.kpiId ? '调整项目KPI目标' : '新增项目KPI'" width="min(680px,94vw)" append-to-body>
      <el-alert title="KPI只评价项目，不指定个人考核对象；已发布方案继续使用原目标快照。" type="info" :closable="false" show-icon />
      <el-form :model="targetForm" label-width="92px" class="dialog-form">
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="系统编码"><el-input v-model="targetForm.kpiCode" disabled placeholder="保存后自动生成" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="指标名称" required><el-input v-model="targetForm.kpiName" /></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="指标类型"><el-select v-model="targetForm.metricType" style="width:100%"><el-option v-for="(label,key) in metricTypeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="考核周期"><el-select v-model="targetForm.periodType" style="width:100%"><el-option v-for="key in ['MONTH','QUARTER','PROJECT']" :key="key" :label="cycleLabel[key]" :value="key" /></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="目标值" required><el-input-number v-model="targetForm.targetValue" :min="0.0001" :precision="4" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="单位"><el-select v-model="targetForm.unit" filterable allow-create default-first-option clearable placeholder="选择或输入单位" style="width:100%"><el-option v-for="unit in commonKpiUnits" :key="unit" :label="unit" :value="unit" /></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="权重%" required><el-input-number v-model="targetForm.weight" :min="0" :max="100" :precision="2" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="考核方向"><el-select v-model="targetForm.direction" style="width:100%"><el-option label="越高越好" value="HIGHER_BETTER"/><el-option label="越低越好" value="LOWER_BETTER"/></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="数据来源" required><el-select v-model="targetForm.sourceType" style="width:100%" @change="changeSourceType"><el-option v-for="(label,key) in sourceTypeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col><el-col v-if="sourceNeedsReference" :sm="12" :xs="24"><el-form-item label="绑定数据" :required="targetForm.sourceType==='ROUTINE'"><el-select v-model="targetForm.sourceRefId" clearable filterable style="width:100%" :placeholder="targetForm.sourceType==='ROUTINE'?'请选择持续工作':'不选则统计全部'"><el-option v-for="option in sourceReferenceOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item></el-col></el-row>
        <el-alert v-if="targetForm.sourceType!=='MANUAL'" :title="sourceHelpText" type="success" :closable="false" show-icon />
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="最低值"><el-input-number v-model="targetForm.minimumValue" :precision="4" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="挑战值"><el-input-number v-model="targetForm.challengeValue" :precision="4" style="width:100%" /></el-form-item></el-col></el-row>
        <el-form-item label="生效日期" required><el-date-picker v-model="targetForm.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="调整说明"><el-input v-model="targetForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="targetDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveTarget">保存KPI版本</el-button></template>
    </el-dialog>

    <el-dialog v-model="planDialog" title="发布项目指标方案" width="min(900px,96vw)" append-to-body>
      <el-alert title="发布后保存当前指标、目标和权重快照，后续调整仅用于新方案。" type="info" :closable="false" show-icon />
      <el-form :model="planForm" label-width="92px" class="dialog-form">
        <el-row :gutter="12">
          <el-col :sm="10" :xs="24"><el-form-item label="考核周期" required><el-select v-model="planForm.cycleType" style="width:100%" @change="resetPlanDates"><el-option label="月度" value="MONTH"/><el-option label="季度" value="QUARTER"/><el-option label="项目周期" value="PROJECT"/></el-select></el-form-item></el-col>
          <el-col :sm="14" :xs="24">
            <el-form-item v-if="planForm.cycleType==='PROJECT'" label="项目日期">
              <span>{{ workspace.project?.planStartDate || '未设置开始日期' }} 至 {{ workspace.project?.planEndDate || '未设置结束日期' }}</span>
            </el-form-item>
            <el-form-item v-else label="起止日期" required><el-date-picker v-model="planDates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-alert v-if="planForm.cycleType==='PROJECT'" :title="projectPeriodReady ? '自动沿用项目计划起止日期。' : '请先在项目中完善有效的计划起止日期，再发布项目周期方案。'" :type="projectPeriodReady ? 'info' : 'warning'" :closable="false" show-icon />
        <el-form-item label="方案说明"><el-input v-model="planForm.remark" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="planDialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="planForm.cycleType==='PROJECT' && !projectPeriodReady" @click="publishPlan">确认发布</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessProjectKpiBonus">
import { ElMessage, ElMessageBox } from 'element-plus'
import { retireBusinessProjectKpi, saveBusinessProjectKpi } from '@/api/business/project'
import { getProjectKpiOverview, getProjectKpiWorkspace, publishProjectKpiPlan, reviewProjectKpiSettlement, saveProjectKpiResults, submitProjectKpiSettlement, voidProjectKpiPlan } from '@/api/business/kpi'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
import { isDeliveryEnded, projectAccountingState } from '@/utils/businessProjectState'

const props=defineProps({resultsOnly:{type:Boolean,default:false}})

const route=useRoute(),router=useRouter()
const loading=ref(false),saving=ref(false),projects=ref([]),projectsLoaded=ref(false),selectedProjectId=ref(null),workspace=reactive({}),targetDialog=ref(false),targetForm=reactive({}),planDialog=ref(false),planForm=reactive({}),planDates=ref([]),resultDraft=reactive({})
const workspacePaths=props.resultsOnly?['/projects/kpi-results']:['/business/kpi-bonus','/projects/kpi']
let workspaceRequestId=0
const selectedPlan=computed(()=>workspace.selectedPlan||null),settlement=computed(()=>selectedPlan.value?.settlement||null),currentTargets=computed(()=>workspace.currentTargets||[])
const isLegacyPlan=computed(()=>!!selectedPlan.value && selectedPlan.value.rewardPolicyVersion!=='INDEPENDENT_V1')
const costPending=computed(()=>settlement.value?.dataStatus==='PENDING_COST'||(settlement.value?.results||[]).some(r=>r.dataStatus==='PENDING_COST'))
const canConfirmResults=computed(()=>workspace.canConfirm!==false && !costPending.value)
const needsLegacyReview=computed(()=>isLegacyPlan.value && settlement.value?.status==='RETURNED' && !!settlement.value?.reviewedUserId)
const confirmedSummary=computed(()=>isLegacyPlan.value?`综合得分 ${settlement.value?.totalScore}，历史奖金 ¥${money(settlement.value?.bonusAmount)}。${settlement.value?.accountingFactId?'已关联项目成本，可在核算系统查看当前成本状态':'本周期未生成奖金成本'}；未记录个人分配或支付。`:`综合得分 ${settlement.value?.totalScore}。需要奖励时，请前往人力资源系统的奖金激励模块。`)
const canEditPlan=computed(()=>!props.resultsOnly&&workspace.canManage&&!isDeliveryEnded(workspace.project)&&projectAccountingState(workspace.project)==='OPEN')
const weightTotal=computed(()=>currentTargets.value.reduce((sum,item)=>sum+Number(item.weight||0),0).toFixed(2).replace(/\.00$/,''))
const periodEnded=computed(()=>!settlement.value?.periodEnd||settlement.value.periodEnd<today())
const automaticSourceTypes=['REVENUE','BUSINESS_COST','PERSONNEL_COST','PROFIT','ROUTINE','TASK','MILESTONE']
const manualItems=computed(()=>(selectedPlan.value?.items||[]).filter(item=>!isAutomatic(item)))
const cycleLabel={MONTH:'月度',QUARTER:'季度',PROJECT:'项目周期'}
const directionLabel={HIGHER_BETTER:'越高越好',LOWER_BETTER:'越低越好'}
const metricTypeLabel={COUNT:'数量',AMOUNT:'金额',PERCENT:'百分比',DURATION:'时长',SCORE:'评分',MILESTONE:'里程碑'}
const commonKpiUnits=['个','件','项','次','单','条','人','户','元','万元','%','分','小时','天']
const sourceTypeLabel={MANUAL:'负责人手工填报',REVENUE:'确认收入（自动）',BUSINESS_COST:'业务成本（自动）',PERSONNEL_COST:'人员成本（自动）',PROFIT:'经营结果（自动）',ROUTINE:'持续工作上报（自动）',TASK:'完成任务数（自动）',MILESTONE:'完成里程碑数（自动）'}
const settlementLabel={DRAFT:'填报中',SUBMITTED:'历史待确认',RETURNED:'已退回',CONFIRMED:'已确认'}
const settlementTone={DRAFT:'info',SUBMITTED:'warning',RETURNED:'danger',CONFIRMED:'success'}
const planStatusLabel={PUBLISHED:'已发布',CLOSED:'已结束',VOIDED:'已作废'}
const today=()=>localDate(new Date())
const money=value=>value==null?'—':Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
function resultFor(itemId){return settlement.value?.results?.find(result=>Number(result.planItemId)===Number(itemId))}
function isAutomatic(item){return automaticSourceTypes.includes(item?.sourceType)}
const sourceNeedsReference=computed(()=>['ROUTINE','TASK','MILESTONE'].includes(targetForm.sourceType))
const sourceReferenceOptions=computed(()=>{const options=workspace.sourceOptions||{};if(targetForm.sourceType==='ROUTINE')return (options.routines||[]).map(item=>({value:item.routineId,label:`${item.routineName} · ${item.unit||''}`}));if(targetForm.sourceType==='TASK')return (options.tasks||[]).map(item=>({value:item.taskId,label:item.taskName}));if(targetForm.sourceType==='MILESTONE')return (options.milestones||[]).map(item=>({value:item.milestoneId,label:item.milestoneName}));return []})
const sourceHelpText=computed(()=>targetForm.sourceType==='ROUTINE'?'考核周期内该持续工作的每日上报实际值将自动求和，例如每天上报视频数量。':targetForm.sourceType==='TASK'?'按考核周期内完成的任务数量统计；不绑定具体任务时统计项目全部任务。':targetForm.sourceType==='MILESTONE'?'按考核周期内完成的里程碑数量统计；不绑定时统计项目全部里程碑。':'从项目已核算的经营结果自动汇总；未确认或尚未核算的数据不会计入。')
function sourceReferenceLabel(item){const lists=[...(workspace.sourceOptions?.routines||[]),...(workspace.sourceOptions?.tasks||[]),...(workspace.sourceOptions?.milestones||[])],match=lists.find(option=>Number(option.routineId||option.taskId||option.milestoneId)===Number(item.sourceRefId));return match?.routineName||match?.taskName||match?.milestoneName||`记录 #${item.sourceRefId}`}
function changeSourceType(sourceType){targetForm.sourceRefId=null;if(['REVENUE','BUSINESS_COST','PERSONNEL_COST','PROFIT'].includes(sourceType)){targetForm.metricType='AMOUNT';targetForm.unit=workspace.project?.baseCurrency||'CNY'}else if(['TASK','MILESTONE'].includes(sourceType)){targetForm.metricType='COUNT';targetForm.unit='项'}}
function scoreRange(tier){return tier.maxScore===null||tier.maxScore===undefined?`${tier.minScore}分及以上`:`${tier.minScore} ≤ 得分 < ${tier.maxScore}`}
function hydrateDraft(){Object.keys(resultDraft).forEach(key=>delete resultDraft[key]);for(const item of manualItems.value){const stored=resultFor(item.itemId)||{};resultDraft[item.itemId]={planItemId:item.itemId,actualValue:stored.actualValue??null,resultNote:stored.resultNote||'',attachmentUrls:stored.attachmentUrls||''}}}
function idKey(value){const scalar=Array.isArray(value)?value[0]:value;return scalar===null||scalar===undefined?'':String(scalar)}
async function loadProjects(){const res=await getProjectKpiOverview();projects.value=res.data||[];const requested=idKey(route.query.projectId),matched=projects.value.find(item=>idKey(item.projectId)===requested);selectedProjectId.value=matched?.projectId??projects.value[0]?.projectId??null;projectsLoaded.value=true;if(!selectedProjectId.value){workspaceRequestId++;Object.keys(workspace).forEach(key=>delete workspace[key])}}
async function loadWorkspace(projectId,planId){if(!idKey(projectId))return;const requestId=++workspaceRequestId;loading.value=true;try{const res=await getProjectKpiWorkspace(projectId,planId);if(requestId!==workspaceRequestId)return;Object.keys(workspace).forEach(key=>delete workspace[key]);Object.assign(workspace,res.data||{});selectedProjectId.value=projectId;hydrateDraft();if(workspacePaths.includes(route.path))await router.replace({query:{...route.query,projectId,planId:workspace.selectedPlan?.planId||undefined}})}catch(error){if(requestId===workspaceRequestId){Object.keys(workspace).forEach(key=>delete workspace[key]);hydrateDraft()}throw error}finally{if(requestId===workspaceRequestId)loading.value=false}}
async function syncWorkspaceFromRoute(){if(!workspacePaths.includes(route.path)||!projectsLoaded.value)return;const requested=idKey(route.query.projectId),matched=projects.value.find(item=>idKey(item.projectId)===requested);if(!matched)return;const requestedPlan=idKey(route.query.planId),currentPlan=idKey(selectedPlan.value?.planId),currentProject=idKey(workspace.project?.projectId);if(currentProject===requested&&(!requestedPlan||requestedPlan===currentPlan))return;await loadWorkspace(matched.projectId,route.query.planId)}
function switchProject(projectId){loadWorkspace(projectId)}
function openTarget(row={}){Object.assign(targetForm,{kpiId:null,projectId:selectedProjectId.value,kpiCode:'',kpiName:'',metricType:'COUNT',periodType:'MONTH',targetValue:null,minimumValue:null,warningValue:null,challengeValue:null,unit:'',weight:0,direction:'HIGHER_BETTER',aggregateType:'SUM',sourceType:'MANUAL',sourceRefId:null,effectiveFrom:today(),remark:'',...row,actualValue:null,ownerUserId:null,ownerName:null});targetDialog.value=true}
async function saveTarget(){if(!targetForm.kpiName?.trim())return ElMessage.warning('请填写指标名称');if(!(Number(targetForm.targetValue)>0))return ElMessage.warning('KPI目标值必须大于0');if(targetForm.sourceType==='ROUTINE'&&!targetForm.sourceRefId)return ElMessage.warning('请选择要自动汇总的持续工作');saving.value=true;try{await saveBusinessProjectKpi(targetForm);targetDialog.value=false;await loadWorkspace(selectedProjectId.value,selectedPlan.value?.planId);ElMessage.success(targetForm.kpiId?'KPI新版本已保存':'KPI已创建，编码已自动生成')}finally{saving.value=false}}
async function retireTarget(row){await ElMessageBox.confirm(`确认停用“${row.kpiName}”吗？已发布方案不会受影响。`,'停用KPI',{type:'warning'});await retireBusinessProjectKpi(selectedProjectId.value,row.kpiId);await loadWorkspace(selectedProjectId.value,selectedPlan.value?.planId);ElMessage.success('KPI已停用')}
function monthRange(){const date=new Date(),start=new Date(date.getFullYear(),date.getMonth(),1),end=new Date(date.getFullYear(),date.getMonth()+1,0);return [localDate(start),localDate(end)]}
function quarterRange(){const date=new Date(),month=Math.floor(date.getMonth()/3)*3,start=new Date(date.getFullYear(),month,1),end=new Date(date.getFullYear(),month+3,0);return [localDate(start),localDate(end)]}
function localDate(date){const offset=new Date(date.getTime()-date.getTimezoneOffset()*60000);return offset.toISOString().slice(0,10)}
const projectPlanDates=computed(()=>[workspace.project?.planStartDate,workspace.project?.planEndDate])
const projectPeriodReady=computed(()=>!!projectPlanDates.value[0]&&!!projectPlanDates.value[1]&&projectPlanDates.value[0]<=projectPlanDates.value[1])
function resetPlanDates(){planDates.value=planForm.cycleType==='MONTH'?monthRange():planForm.cycleType==='QUARTER'?quarterRange():[]}
function openPlan(){Object.assign(planForm,{projectId:selectedProjectId.value,cycleType:'MONTH',remark:'',tiers:[]});resetPlanDates();planDialog.value=true}
async function publishPlan(){
  if(planForm.cycleType==='PROJECT'&&!projectPeriodReady.value)return ElMessage.warning('请先在项目中完善有效的计划起止日期')
  const dates=planForm.cycleType==='PROJECT'?projectPlanDates.value:planDates.value
  if(!dates?.[0]||!dates?.[1])return ElMessage.warning('请选择考核起止日期')
  await ElMessageBox.confirm('发布后目标和权重将形成不可覆盖的方案快照。确定发布吗？','发布指标方案',{type:'warning'})
  saving.value=true
  try{const res=await publishProjectKpiPlan({...planForm,bonusMode:'NONE',tiers:[],cycleStart:dates[0],cycleEnd:dates[1]});Object.keys(workspace).forEach(key=>delete workspace[key]);Object.assign(workspace,res.data||{});hydrateDraft();planDialog.value=false;ElMessage.success('项目指标方案已发布')}finally{saving.value=false}
}
async function voidPlan(row){await ElMessageBox.confirm(`确定作废 v${row.planVersion} · ${cycleLabel[row.cycleType]} 吗？作废后该方案不再参与项目考核，已发布的指标快照与历史记录仍会保留。`,'作废KPI方案',{type:'warning',confirmButtonText:'确认作废'});saving.value=true;try{await voidProjectKpiPlan(row.planId);await loadWorkspace(selectedProjectId.value);ElMessage.success('KPI方案已作废，审计数据已保留')}finally{saving.value=false}}
function resultPayload(requireAll=false){const rows=Object.values(resultDraft).filter(row=>row.actualValue!==null&&row.actualValue!==undefined);if(!rows.length){ElMessage.warning('请至少填写一项手工KPI结果');return null}for(const row of rows)if(!row.resultNote?.trim()){ElMessage.warning('每项手工结果都必须填写说明');return null}if(requireAll&&rows.length!==manualItems.value.length){ElMessage.warning('请完整填写所有手工KPI结果');return null}return {results:rows}}
async function saveResults(showMessage=true,requireAll=false){const payload=resultPayload(requireAll);if(!payload)return false;saving.value=true;try{const res=await saveProjectKpiResults(settlement.value.settlementId,payload);selectedPlan.value.settlement=res.data;hydrateDraft();if(showMessage)ElMessage.success('KPI结果草稿已保存');return true}finally{saving.value=false}}
async function submitResults(){if(!periodEnded.value)return ElMessage.warning('截止日期次日才能确认结算');if(!canConfirmResults.value)return ElMessage.warning('人员成本尚未完整计价，请先处理待计价投入');if(manualItems.value.length&&!await saveResults(false,true))return;if(!canConfirmResults.value)return ElMessage.warning('人员成本尚未完整计价，请先处理待计价投入');await ElMessageBox.confirm(needsLegacyReview.value?'按原审核流程重新提交，奖金及成本仍需原审核人确认。确定提交吗？':isLegacyPlan.value?'历史方案按原规则确认指标、奖金和成本。确定继续吗？':'按截止日期读取自动指标并形成结果快照。奖金另行核准。确定继续吗？','确认项目指标',{type:'warning'});saving.value=true;try{const res=await submitProjectKpiSettlement(settlement.value.settlementId);const status=res.data?.status;selectedPlan.value.settlement=res.data;await loadWorkspace(selectedProjectId.value,selectedPlan.value.planId);ElMessage.success(status==='SUBMITTED'?'已提交原审核流程，尚未核准奖金或入账':isLegacyPlan.value?'历史项目指标已确认，请查看关联成本状态':'项目指标已确认')}finally{saving.value=false}}
async function returnSettlement(){const{value}=await ElMessageBox.prompt('请填写需要负责人修正的内容','退回KPI结算',{inputValidator:value=>!!value?.trim()||'必须填写退回原因',type:'warning'});saving.value=true;try{const res=await reviewProjectKpiSettlement(settlement.value.settlementId,{decision:'RETURNED',comment:value});selectedPlan.value.settlement=res.data;hydrateDraft();ElMessage.success('已退回项目负责人修改')}finally{saving.value=false}}
async function confirmSettlement(){await ElMessageBox.confirm(`确认综合得分 ${settlement.value.totalScore}、项目奖金 ¥${money(settlement.value.bonusAmount)} 吗？确认后将立即计入 ${settlement.value.periodEnd} 项目成本。`,'确认项目KPI奖金',{type:'warning',confirmButtonText:'确认并计入成本'});saving.value=true;try{const res=await reviewProjectKpiSettlement(settlement.value.settlementId,{decision:'CONFIRMED',comment:'确认项目KPI及奖金'});selectedPlan.value.settlement=res.data;await loadWorkspace(selectedProjectId.value,selectedPlan.value.planId);ElMessage.success('历史项目指标与奖金已确认，请查看关联成本状态')}finally{saving.value=false}}
watch(()=>route.fullPath,syncWorkspaceFromRoute)
onActivated(syncWorkspaceFromRoute)
useBusinessRefreshOnReactivated(async()=>{await loadProjects();if(selectedProjectId.value)await loadWorkspace(selectedProjectId.value,route.query.planId)})
onMounted(async()=>{loading.value=true;try{await loadProjects();if(selectedProjectId.value)await loadWorkspace(selectedProjectId.value,route.query.planId)}finally{loading.value=false}})
</script>

<style scoped>
.kpi-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f5f8;color:#182537}.kpi-hero{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;padding:26px 30px;border-radius:16px;background:linear-gradient(120deg,#173750,#23655d);color:#fff}.kpi-hero>div:first-child>span{font-size:11px;letter-spacing:.17em;color:#7dd7ca}.kpi-hero h1{margin:5px 0;font-size:28px}.kpi-hero p{margin:0;color:#cbdcdf}.hero-tools{display:flex;align-items:center;gap:10px}.hero-tools .el-select{width:330px}.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin:15px 0}.summary-grid article{min-width:0;padding:16px 18px;border:1px solid #dfe5ea;border-radius:12px;background:#fff}.summary-grid span,.summary-grid small,.summary-grid b{display:block}.summary-grid span,.summary-grid small{color:#7d8997}.summary-grid span{font-size:12px}.summary-grid b{overflow:hidden;margin:7px 0;font-size:20px;text-overflow:ellipsis;white-space:nowrap}.summary-grid small{font-size:12px;line-height:1.45}.content-grid{display:grid;grid-template-columns:minmax(0,1.55fr) minmax(320px,.65fr);gap:14px}.content-grid main,.content-grid aside{display:flex;min-width:0;flex-direction:column;gap:14px}.section-card{border-color:#dfe5ea}.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:14px}.section-head h2{margin:0;font-size:18px}.section-head p{margin:5px 0 0;color:#82909f;font-size:12px;line-height:1.5}.section-actions{display:flex;gap:8px}.section-card small{display:block;margin-top:4px;color:#8793a0}.result-list{border-top:1px solid #edf0f3}.result-row{display:grid;grid-template-columns:minmax(170px,.7fr) minmax(150px,.4fr) minmax(230px,1fr) minmax(170px,.65fr);align-items:start;gap:12px;padding:16px 0;border-bottom:1px solid #edf0f3}.result-target,.result-value{display:flex;flex-direction:column;gap:5px}.result-target span,.result-value span{color:#83909e;font-size:12px}.result-row>p{grid-column:2/-1;margin:0;color:#5f6e7e;line-height:1.6}.result-attachments{grid-column:2/-1;min-width:0}.result-attachments>span{display:block;margin-bottom:8px;color:#83909e;font-size:12px}.review-bar{display:flex;align-items:center;justify-content:flex-end;gap:10px;margin-top:16px;padding:15px;border-radius:10px;background:#f6f8fa}.review-bar>div{display:flex;min-width:0;flex:1;flex-direction:column}.review-bar span{margin-top:5px;color:#7e8b99;font-size:12px}.tier-row,.plan-row{display:flex;width:100%;align-items:center;justify-content:space-between;gap:10px;padding:13px 2px;border:0;border-top:1px solid #edf0f3;background:transparent;color:inherit;text-align:left}.tier-row>span,.plan-row>span{display:flex;min-width:0;flex-direction:column}.tier-row strong{color:#217865}.plan-row{cursor:pointer}.plan-row.active{color:#226f67}.plan-row:hover{background:#f7f9fa}.empty-text{padding:20px;text-align:center;color:#929da8}.dialog-form{margin-top:18px}.tier-editor-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin:18px 0 9px}.tier-editor-head>div{display:flex;flex-direction:column;gap:4px}.tier-editor-head small{color:#7d8997;line-height:1.5}.tier-example{margin-bottom:10px}.tier-editor-columns,.tier-editor-row{display:grid;grid-template-columns:minmax(120px,.8fr) minmax(130px,.72fr) minmax(145px,.8fr) minmax(180px,1fr) 54px;gap:8px}.tier-editor-columns{padding:0 0 6px;color:#657384;font-size:12px}.tier-editor-columns span{padding-left:2px}.tier-editor-row{align-items:center;margin-bottom:8px}.tier-field>span{display:none}.tier-editor-row :deep(.el-input-number){width:100%}.tier-action{text-align:center;color:#a0a8b1}@media(max-width:1180px){.summary-grid{grid-template-columns:repeat(3,1fr)}.content-grid{grid-template-columns:1fr}.result-row{grid-template-columns:minmax(170px,.7fr) minmax(150px,.4fr) minmax(230px,1fr)}.result-row>:last-child{grid-column:2/-1}}@media(max-width:720px){.kpi-page{padding:12px}.kpi-hero{align-items:flex-start;flex-direction:column;padding:21px}.hero-tools{width:100%;align-items:stretch;flex-direction:column}.hero-tools .el-select,.hero-tools .el-button{width:100%}.summary-grid{grid-template-columns:repeat(2,1fr);gap:8px}.summary-grid article:last-child{grid-column:1/-1}.section-head,.review-bar,.tier-editor-head{align-items:stretch;flex-direction:column}.section-actions{display:grid;grid-template-columns:1fr 1fr}.result-row{grid-template-columns:1fr}.result-row>*,.result-row>:last-child,.result-row>p{grid-column:1}.tier-editor-columns{display:none}.tier-editor-row{grid-template-columns:1fr 1fr;padding:12px;border:1px solid #e5e9ed;border-radius:9px}.tier-field{display:flex;min-width:0;flex-direction:column;gap:5px}.tier-field>span{display:block;color:#657384;font-size:12px}.tier-editor-row>*:first-child,.tier-editor-row>*:nth-child(4),.tier-action{grid-column:1/-1}.tier-action{text-align:right}}
.settings-summary{grid-template-columns:repeat(3,minmax(0,1fr))}.plan-remark{color:#82909f;font-size:13px;line-height:1.5}.plan-row-actions{align-items:flex-end;gap:4px}@media(max-width:720px){.settings-summary{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
