<template>
  <div class="business-product" v-loading="loading">
    <header class="product-head"><div><h1>{{ t('title') }}</h1><p>{{ t('intro') }}</p></div><el-button icon="Refresh" @click="load">{{ t('refresh') }}</el-button></header>
    <div class="product-toolbar"><el-select v-model="projectId" filterable :placeholder="t('project')" @change="switchProject"><el-option v-for="p in data.projects || []" :key="p.projectId" :label="p.projectName" :value="p.projectId" /></el-select></div>
    <el-empty v-if="!data.project && !loading" :description="t('chooseProject')" />
    <template v-if="data.project">
      <el-tabs v-model="activeTab">
        <el-tab-pane v-if="!data.distributionOnly" :label="t('bonusSettings')" name="rules">
          <section class="product-card">
            <div class="product-head"><div><h2>{{ t('bonusSettings') }}</h2><p>{{ t('scoreRuleHint') }}</p></div><el-button type="primary" :disabled="!canConfigureRules || !kpiPlans.length" @click="openRule()">{{ t('newRule') }}</el-button></div>
            <el-alert v-if="!canConfigureRules" :title="ruleBlockReason" type="info" :closable="false" show-icon />
            <el-empty v-if="!kpiPlans.length" :description="t('noKpiPlan')"><el-button v-hasPermi="['business:kpi:list']" type="primary" plain @click="openKpiSettings">{{ t('openKpiSettings') }}</el-button></el-empty>
            <el-table :data="data.rules || []" :empty-text="t('empty')">
              <el-table-column type="expand"><template #default="{row}"><div class="rule-expanded"><h3>{{ planLabel(row.kpiPlanId) }}</h3><p>{{ row.reason }}</p><el-table v-if="isScoreRule(row)" :data="row.tiers || []"><el-table-column :label="t('scoreRange')"><template #default="{row:tier}">{{ tierRange(tier) }}</template></el-table-column><el-table-column :label="t('amount')"><template #default="{row:tier}">{{ amount(tier.amount) }} {{ row.currency }}</template></el-table-column></el-table><p v-else>{{ t('fixedRule') }} · {{ amount(row.amount) }} {{ row.currency }} · {{ t('score') }} {{ row.minScore ?? t('noScore') }}</p></div></template></el-table-column>
              <el-table-column prop="ruleName" :label="t('rule')" min-width="170" />
              <el-table-column :label="t('kpiPlan')" min-width="235"><template #default="{row}">{{ planLabel(row.kpiPlanId) }}</template></el-table-column>
              <el-table-column :label="t('bonusMethod')" min-width="125"><template #default="{row}">{{ isScoreRule(row) ? t('tierCount',{count:row.tiers?.length||0}) : t('fixedRule') }}</template></el-table-column>
              <el-table-column prop="ruleVersion" :label="t('version')" width="75" />
              <el-table-column :label="t('ruleStatus')" width="110"><template #default="{row}"><el-tag :type="row.status==='ACTIVE'?'success':'info'">{{ state(row.status) }}</el-tag></template></el-table-column>
              <el-table-column :label="t('actions')" min-width="160"><template #default="{row}"><el-button v-if="data.canManageRules && isScoreRule(row) && kpiPlans.some(p=>Number(p.planId)===Number(row.kpiPlanId))" v-hasPermi="['business:incentive:rule','business:kpi:manage']" link type="primary" @click="openRule(row)">{{ t('reviseRule') }}</el-button><el-button v-if="data.canRetireRules && row.status==='ACTIVE'" v-hasPermi="['business:incentive:rule','business:kpi:manage']" link type="danger" @click="retireRule(row)">{{ t('retire') }}</el-button></template></el-table-column>
            </el-table>
          </section>
          <section v-if="scoreRules.length" class="product-card">
            <h2>{{ t('scorePreview') }}</h2><p>{{ t('previewHint') }}</p>
            <div class="product-toolbar"><el-select v-model="previewRuleId" :placeholder="t('rule')"><el-option v-for="r in scoreRules" :key="r.ruleId" :label="`${r.ruleName} · v${r.ruleVersion}`" :value="r.ruleId" /></el-select><span>{{ t('testScore') }}</span><el-input-number v-model="previewScore" :aria-label="t('testScore')" :min="0" :max="120" :precision="2" /></div>
            <div class="bonus-preview"><span>{{ t('matchedBonus') }}</span><strong>{{ amount(publishedPreview?.amount) }} {{ previewRule?.currency }}</strong><small v-if="publishedPreview">{{ tierRange(publishedPreview) }}</small></div>
          </section>
        </el-tab-pane>
        <el-tab-pane v-if="!data.distributionOnly" :label="t('awardProcessing')" name="awards">
      <div class="product-toolbar"><el-button v-if="data.canApply" v-hasPermi="['business:incentive:apply']" type="primary" :disabled="!activeRules.length" @click="openAward">{{ t('newAward') }}</el-button></div>
      <el-alert :title="t('boundary')" type="info" :closable="false" show-icon /><el-alert v-if="sameApprover" :title="t('selfApproval')" type="warning" :closable="false" show-icon />
      <section class="product-card"><h2>{{ t('awards') }}</h2><p>{{ t('awardHint') }}</p>
        <el-table :data="data.awards || []" :empty-text="t('empty')">
          <el-table-column :label="t('rule')" min-width="165"><template #default="{row}">{{ row.ruleName || `#${row.ruleId}` }}<div class="muted">{{ row.awardNo || `#${row.awardId}` }}</div></template></el-table-column>
          <el-table-column :label="t('amount')" min-width="135"><template #default="{row}">{{ amount(row.amount ?? row.approvedAmount) }} {{ row.currency }}</template></el-table-column>
          <el-table-column prop="bizDate" :label="t('date')" width="115" />
          <el-table-column :label="t('approval')" min-width="115"><template #default="{row}"><el-tag :type="tone(row.status)">{{ state(row.status) }}</el-tag></template></el-table-column>
          <el-table-column :label="t('cost')" min-width="120"><template #default="{row}">{{ costState(row.costStatus) }}</template></el-table-column>
          <el-table-column :label="t('payment')" min-width="125"><template #default="{row}">{{ awardPaymentLabel(row) }}</template></el-table-column>
          <el-table-column :label="t('actions')" min-width="275" fixed="right"><template #default="{row}"><div class="row-actions">
            <el-button link @click="history=row">{{ t('details') }}</el-button>
            <el-button v-if="row.canSubmit" v-hasPermi="['business:incentive:apply']" link type="primary" :disabled="acting" @click="act(row,'submit')">{{ t('submit') }}</el-button>
            <el-button v-if="row.canReview" v-hasPermi="['business:incentive:approve']" link type="success" :disabled="acting" @click="act(row,'review','APPROVED')">{{ t('approve') }}</el-button>
            <el-button v-if="row.canReview" v-hasPermi="['business:incentive:approve']" link type="warning" :disabled="acting" @click="act(row,'review','RETURNED')">{{ t('return') }}</el-button>
            <el-button v-if="row.canCancel" v-hasPermi="['business:incentive:apply','business:incentive:approve']" link type="danger" :disabled="acting" @click="act(row,'cancel')">{{ t('cancelAward') }}</el-button>
            <el-button v-if="row.canResubmitCost" v-hasPermi="['business:incentive:approve']" link type="primary" :disabled="acting" @click="act(row,'resubmit-cost')">{{ t('resubmitCost') }}</el-button>
            <el-button v-if="row.accountingFactId" v-hasPermi="['business:accounting:list']" link @click="openAccounting(row)">{{ t('openCost') }}</el-button>
          </div></template></el-table-column>
        </el-table>
      </section>
      <section v-if="data.legacyBonuses?.length" class="product-card"><h2>{{ t('legacy') }}</h2><p>{{ t('legacyHint') }}</p><el-table :data="data.legacyBonuses"><el-table-column prop="periodEnd" :label="t('date')"/><el-table-column :label="t('amount')"><template #default="{row}">{{ amount(row.bonusAmount) }} {{ row.currency || 'CNY' }}</template></el-table-column><el-table-column :label="t('approval')"><template #default="{row}">{{ row.status==='CONFIRMED'?t('indicatorConfirmed'):state(row.status) }}</template></el-table-column><el-table-column :label="t('cost')"><template #default="{row}">{{ costState(row.costStatus) }}</template></el-table-column><el-table-column :label="t('payment')"><template #default>{{ t('notRecorded') }}</template></el-table-column></el-table></section>
        </el-tab-pane>
        <el-tab-pane :label="t('distributionTitle')" name="distribution"><DistributionPanel :data="distribution" @refresh="load" /></el-tab-pane>
      </el-tabs>
    </template>
    <el-dialog v-model="ruleOpen" :title="t('newRule')" width="min(850px,95vw)" append-to-body>
      <el-form label-position="top" :model="ruleForm">
        <el-form-item :label="t('kpiPlan')" required><el-select v-model="ruleForm.kpiPlanId" style="width:100%"><el-option v-for="p in kpiPlans" :key="p.planId" :label="planLabel(p.planId)" :value="p.planId" /></el-select></el-form-item>
        <el-form-item :label="t('rule')" required><el-input v-model="ruleForm.ruleName" :placeholder="t('bonusNamePlaceholder')" maxlength="100" /></el-form-item>
        <section class="score-formula" :aria-label="t('scoreFormulaTitle')">
          <h3>{{ t('scoreFormulaTitle') }}</h3>
          <p class="score-formula-source">{{ t('scoreFormulaSource') }}</p>
          <div class="score-formula-grid">
            <div><b>{{ t('higherBetter') }}</b><p>{{ t('higherFormula') }}</p></div>
            <div><b>{{ t('lowerBetter') }}</b><p>{{ t('lowerFormula') }}</p></div>
          </div>
          <p>{{ t('scoreCapHint') }}</p>
          <p class="score-formula-total"><strong>{{ t('weightedFormula') }}</strong><br /><strong>{{ t('totalFormula') }}</strong></p>
          <p>{{ t('scoreRoundingHint') }}</p>
          <p class="score-formula-example">{{ t('scoreExample') }}</p>
        </section>
        <el-alert :title="t('tierHint')" type="info" :closable="false" show-icon />
        <div class="tier-editor">
          <div v-for="(tier,index) in ruleForm.tiers || []" :key="index" class="tier-edit-row">
            <div><span>{{ t('scoreFrom') }}</span><b>{{ tier.minScore }}</b></div>
            <div><span>{{ t('scoreUntil') }}</span><el-input-number v-if="index<ruleForm.tiers.length-1" v-model="tier.maxScore" :aria-label="`${t('scoreUntil')} ${index+1}`" :min="0" :max="120" :precision="2" controls-position="right" @change="syncTiers"/><b v-else>{{ t('unlimited') }}</b></div>
            <div><span>{{ t('amount') }} ({{ ruleForm.currency }})</span><el-input-number v-model="tier.amount" :aria-label="`${t('amount')} ${index+1}`" :min="0" :max="99999999999999.99" :precision="2" controls-position="right" /></div>
            <el-button type="danger" link :disabled="ruleForm.tiers.length===1" @click="removeTier(index)">{{ t('removeTier') }}</el-button>
          </div>
        </div>
        <el-button :disabled="ruleForm.tiers?.length>=20 || Number(ruleForm.tiers?.at(-1)?.minScore)>=120" @click="addTier">{{ t('addTier') }}</el-button>
        <el-alert v-if="tierError" :title="tierError" type="warning" :closable="false" />
        <div class="draft-preview"><span>{{ t('testScore') }}</span><el-input-number v-model="draftScore" :aria-label="t('testScore')" :min="0" :max="120" :precision="2"/><strong>{{ t('matchedBonus') }} {{ amount(draftPreview?.amount) }} {{ ruleForm.currency }}</strong></div>
        <p class="muted">{{ t('previewHint') }}</p>
        <el-form-item :label="t('reason')" required><el-input v-model="ruleForm.reason" type="textarea" maxlength="500" /></el-form-item>
        <el-alert :title="t('versionHint')" type="info" :closable="false" />
      </el-form>
      <template #footer><el-button @click="ruleOpen=false">{{ t('cancel') }}</el-button><el-button type="primary" :loading="saving" @click="saveRule">{{ t('publish') }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="awardOpen" :title="t('newAward')" width="min(640px,95vw)" append-to-body>
      <el-form label-position="top" :model="awardForm"><el-form-item :label="t('rule')" required><el-select v-model="awardForm.ruleId" style="width:100%" @change="changeAwardRule"><el-option v-for="r in activeRules" :key="r.ruleId" :label="`${r.ruleName} · ${isScoreRule(r)?t('scoreBased'):amount(r.amount)} ${r.currency}`" :value="r.ruleId"/></el-select></el-form-item><el-form-item :label="isScoreRule(awardRule)?t('confirmedEvidence'):t('kpiEvidence')" :required="isScoreRule(awardRule)"><el-select v-model="awardForm.settlementId" clearable style="width:100%" @change="invalidateEstimate"><el-option v-for="k in awardEvidence" :key="k.settlementId" :label="`${k.periodEnd || k.cycleEnd} · ${k.totalScore ?? '—'}`" :value="k.settlementId"/></el-select></el-form-item><el-form-item :label="t('date')" required><el-date-picker v-model="awardForm.bizDate" type="date" value-format="YYYY-MM-DD" :disabled-date="futureDate"/></el-form-item><el-form-item :label="t('reason')" required><el-input v-model="awardForm.reason" type="textarea" maxlength="500"/></el-form-item></el-form>
      <el-alert v-if="isScoreRule(awardRule) && !awardEvidence.length" :title="t('noConfirmedEvidence')" type="info" :closable="false" /><el-button :disabled="!awardForm.ruleId || (isScoreRule(awardRule) && !awardForm.settlementId)" :loading="estimating" @click="calculate">{{ t('estimate') }}</el-button><p v-if="estimate">{{ t('estimated') }} {{ amount(estimate.amount ?? estimate.estimatedAmount) }} {{ estimate.currency || data.project?.baseCurrency }} · {{ estimate.message || t('estimateHint') }}</p>
      <template #footer><el-button @click="awardOpen=false">{{ t('cancel') }}</el-button><el-button type="primary" :loading="saving" @click="saveAward">{{ t('saveDraft') }}</el-button></template>
    </el-dialog>
    <el-drawer :model-value="!!history" :title="t('details')" size="min(640px,95vw)" @close="history=null"><template v-if="history"><el-descriptions :column="1" border><el-descriptions-item :label="t('rule')">{{ history.ruleName }} · v{{ history.ruleVersion }}</el-descriptions-item><el-descriptions-item :label="t('amount')">{{ amount(history.amount) }} {{ history.currency }}</el-descriptions-item><el-descriptions-item :label="t('applicant')">{{ history.applicantUserName }}</el-descriptions-item><el-descriptions-item :label="t('approver')">{{ history.approvedUserName || '—' }}</el-descriptions-item><el-descriptions-item :label="t('approvedTime')">{{ history.approvedTime || '—' }}</el-descriptions-item><el-descriptions-item :label="t('scoreSnapshot')">{{ history.scoreSnapshot ?? '—' }}</el-descriptions-item><el-descriptions-item :label="t('reason')">{{ history.reason }}</el-descriptions-item><el-descriptions-item :label="t('allocation')">{{ awardAllocationLabel(history) }}</el-descriptions-item><el-descriptions-item :label="t('approval')">{{ state(history.status) }}</el-descriptions-item><el-descriptions-item :label="t('cost')">{{ costState(history.costStatus) }}</el-descriptions-item><el-descriptions-item :label="t('payment')">{{ awardPaymentLabel(history) }}</el-descriptions-item></el-descriptions><el-timeline style="margin-top:24px"><el-timeline-item v-for="(event,index) in history.events || []" :key="event.eventId || index" :timestamp="event.createTime || event.eventTime"><b>{{ event.operatorName || event.operatorUserName }}</b> · {{ state(event.toStatus || event.action) }}<p>{{ event.reason || event.comment }}</p></el-timeline-item></el-timeline></template></el-drawer>
  </div>
</template>

<script setup name="BusinessIncentive">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getIncentiveWorkspace, createIncentiveRule, estimateIncentive, createIncentiveAward, actIncentiveAward, retireIncentiveRule } from '@/api/business/incentive'
import '@/assets/styles/business-product.scss'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
import scoreMessages from './scoreMessages'
import { isScoreRule, validTiers, matchTier } from './scoreRules'
import auth from '@/plugins/auth'
import DistributionPanel from './DistributionPanel.vue'
import { getBonusDistribution } from '@/api/business/incentive'
const distribution=reactive({})

const route=useRoute(), router=useRouter(), projectId=ref(route.query.projectId ? Number(route.query.projectId) : null)
const data=reactive({}), loading=ref(false), saving=ref(false), estimating=ref(false), ruleOpen=ref(false), awardOpen=ref(false), history=ref(null), estimate=ref(null), refreshKey=ref(0)
const ruleForm=reactive({}), awardForm=reactive({}), acting=ref(false); let sequence=0, estimateSequence=0
const activeTab=ref('rules'), previewRuleId=ref(null), previewScore=ref(100), draftScore=ref(100)
const {t,te}=useI18n({useScope:'local',messages:{
  'zh-CN':{...scoreMessages['zh-CN'],title:'奖金激励',version:'版本',retire:'停用方案',indicatorConfirmed:'指标已确认',pendingCost:'待确认',allocation:'个人分配',applicant:'申请人',approver:'核准人',approvedTime:'核准时间',scoreSnapshot:'指标得分快照',maxReason:'说明不得超过500字',selfApproval:'主负责人与归属老板相同时，不能自批奖励；须先明确不同的业务核准责任人。',intro:'按项目 KPI 综合得分设置奖金档位，确认指标后按对应方案申请奖励。',refresh:'刷新',project:'选择项目',chooseProject:'请选择有权查看的项目',newRule:'设置奖金方案',newAward:'新建奖励申请',boundary:'奖励核准、成本入账、个人分配与支付分别记录。这里不会把奖金池标记为已发薪。',awards:'奖励申请与核准',awardHint:'申请人提交，由项目归属责任人核准；核准后生成待确认成本。',empty:'暂无记录',rule:'奖金方案',amount:'奖励金额',date:'业务日期',approval:'核准状态',cost:'成本状态',payment:'支付状态',actions:'操作',details:'详情与记录',submit:'提交核准',approve:'核准',return:'退回',cancelAward:'撤销',resubmitCost:'重新提交成本',openCost:'查看成本',rules:'奖金方案版本',ruleHint:'已发布方案保留原值。调整时发布新方案，既有申请继续引用原版本。',score:'最低综合得分',noScore:'不要求指标分数',reason:'依据与说明',legacy:'历史项目奖金池',legacyHint:'原方案按历史规则结清；没有个人分配和支付记录，不生成新的重复奖励。',notRecorded:'未记录',cancel:'取消',publish:'发布方案',scoreHint:'选填；填写后，奖励申请须引用已确认且达到门槛的独立项目指标。',kpiEvidence:'已确认项目指标（可选）',estimate:'测算奖励',estimated:'测算金额：',estimateHint:'测算不代表核准或入账',saveDraft:'保存草稿',required:'请完整填写必填资料',success:'操作成功',actionTitle:'确认奖励操作',actionReason:'请填写本次操作的依据或原因',states:{ACTIVE:'有效',RETIRED:'已停用',DRAFT:'草稿',SUBMITTED:'待核准',RETURNED:'已退回',APPROVED:'已核准',CONFIRMED:'已入账',CANCELED:'已撤销',CANCELLED:'已撤销',VOIDED:'已作废',REVERSED:'已冲正',NOT_CREATED:'未生成',PENDING:'待确认',NOT_RECORDED:'未记录',NONE:'未生成'}},
  'vi-VN':{...scoreMessages['vi-VN'],title:'Khuyến khích và thưởng',version:'Phiên bản',retire:'Dừng phương án',indicatorConfirmed:'Chỉ tiêu đã xác nhận',pendingCost:'Chờ xác nhận',allocation:'Phân bổ cá nhân',applicant:'Người đề nghị',approver:'Người phê duyệt',approvedTime:'Thời gian phê duyệt',scoreSnapshot:'Điểm chỉ tiêu đã chốt',maxReason:'Lý do không vượt quá 500 ký tự',selfApproval:'Khi người phụ trách cũng là người phê duyệt, không được tự phê duyệt thưởng; cần xác định người chịu trách nhiệm khác.',intro:'Thiết lập các bậc thưởng theo điểm KPI dự án và đề nghị thưởng sau khi xác nhận kết quả.',refresh:'Làm mới',project:'Chọn dự án',chooseProject:'Chọn dự án được phép xem',newRule:'Thiết lập phương án thưởng',newAward:'Tạo đề nghị thưởng',boundary:'Phê duyệt, ghi nhận chi phí, phân bổ cá nhân và thanh toán là các bước riêng biệt.',awards:'Đề nghị và phê duyệt',awardHint:'Người đề nghị gửi, người chịu trách nhiệm dự án phê duyệt rồi tạo chi phí chờ xác nhận.',empty:'Chưa có dữ liệu',rule:'Phương án thưởng',amount:'Số tiền thưởng',date:'Ngày nghiệp vụ',approval:'Phê duyệt',cost:'Chi phí',payment:'Thanh toán',actions:'Thao tác',details:'Chi tiết và lịch sử',submit:'Gửi phê duyệt',approve:'Phê duyệt',return:'Trả lại',cancelAward:'Hủy đề nghị',resubmitCost:'Gửi lại chi phí',openCost:'Xem chi phí',rules:'Phiên bản phương án',ruleHint:'Phương án đã công bố được giữ nguyên. Thay đổi bằng phiên bản mới.',score:'Điểm tổng hợp tối thiểu',noScore:'Không yêu cầu điểm',reason:'Căn cứ và lý do',legacy:'Quỹ thưởng dự án cũ',legacyHint:'Giữ cách quyết toán cũ; chưa ghi nhận phân bổ cá nhân hoặc thanh toán, không tạo thưởng trùng.',notRecorded:'Chưa ghi nhận',cancel:'Hủy',publish:'Công bố',scoreHint:'Tùy chọn; khi có ngưỡng, cần dẫn kết quả chỉ tiêu đã xác nhận đạt ngưỡng.',kpiEvidence:'Chỉ tiêu đã xác nhận (tùy chọn)',estimate:'Ước tính',estimated:'Số tiền ước tính: ',estimateHint:'Ước tính chưa phải phê duyệt hoặc hạch toán',saveDraft:'Lưu bản nháp',required:'Vui lòng điền thông tin bắt buộc',success:'Thành công',actionTitle:'Xác nhận thao tác thưởng',actionReason:'Nhập căn cứ hoặc lý do',states:{ACTIVE:'Có hiệu lực',RETIRED:'Đã dừng',DRAFT:'Bản nháp',SUBMITTED:'Chờ phê duyệt',RETURNED:'Đã trả lại',APPROVED:'Đã phê duyệt',CONFIRMED:'Đã ghi chi phí',CANCELED:'Đã hủy',CANCELLED:'Đã hủy',VOIDED:'Đã vô hiệu',REVERSED:'Đã đảo',NOT_CREATED:'Chưa tạo',PENDING:'Chờ xác nhận',NOT_RECORDED:'Chưa ghi nhận',NONE:'Chưa tạo'}}
}})
const amount=value=>value==null?'—':Number(value).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})
const state=value=>value&&te(`states.${value}`)?t(`states.${value}`):(value||'—')
const costState=value=>value==='DRAFT'?t('pendingCost'):state(value)
const awardBatches=award=>(distribution.allocations||[]).filter(b=>Number(b.awardId)===Number(award.awardId)&&b.status==='APPROVED')
function awardPaymentLabel(award){const batches=awardBatches(award),paid=batches.reduce((sum,b)=>sum+Number(b.paidAmount||0),0);return !batches.length?t('notRecorded'):paid<=0?t('distributionUnpaid'):paid<Number(award.amount)?t('distributionPartial'):t('distributionPaid')}
function awardAllocationLabel(award){return t('distributionAllocated',{amount:amount(awardBatches(award).reduce((sum,b)=>sum+Number(b.amount||0),0)),currency:award.currency})}
const sameApprover=computed(()=>data.project?.mainOwnerUserId && String(data.project.mainOwnerUserId)===String(data.project.sponsorOwnerUserId??data.project.initiatorUserId))
const tone=value=>({APPROVED:'success',CONFIRMED:'success',RETURNED:'warning',SUBMITTED:'warning',CANCELED:'info'}[value]||'info')
const today=()=>new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'})
const futureDate=date=>{const v=new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,10),start=data.project?.actualStartDate||data.project?.planStartDate,end=data.project?.actualEndDate||today();return v>today()||v>end||(start&&v<start)}
const activeRules=computed(()=>(data.rules||[]).filter(r=>r.status==='ACTIVE'))
const kpiPlans=computed(()=>data.kpiPlans||[])
const canConfigureRules=computed(()=>data.canManageRules && auth.hasPermiOr(['business:incentive:rule','business:kpi:manage']))
const ruleBlockReason=computed(()=>data.project?.accountingState==='CLOSED'?t('rulesAccountingClosed'):data.project?.status!=='ACTIVE'?t('rulesProjectInactive'):t('rulesReadOnly'))
const scoreRules=computed(()=>(data.rules||[]).filter(isScoreRule))
const previewRule=computed(()=>scoreRules.value.find(r=>Number(r.ruleId)===Number(previewRuleId.value))||scoreRules.value[0])
const publishedPreview=computed(()=>matchTier(previewRule.value?.tiers,previewScore.value))
const tierError=computed(()=>validTiers(ruleForm.tiers)?'':t('tierInvalid'))
const draftPreview=computed(()=>matchTier(ruleForm.tiers,draftScore.value))
const awardRule=computed(()=>activeRules.value.find(r=>Number(r.ruleId)===Number(awardForm.ruleId)))
const awardEvidence=computed(()=>(data.confirmedKpis||[]).filter(k=>!isScoreRule(awardRule.value)||Number(k.planId)===Number(awardRule.value.kpiPlanId)))
const tierRange=tier=>tier.maxScore==null?t('finalRange',{min:tier.minScore}):t('boundedRange',{min:tier.minScore,max:tier.maxScore})
function planLabel(id){const p=kpiPlans.value.find(p=>Number(p.planId)===Number(id));return p?`v${p.planVersion} · ${String(p.cycleStart).slice(0,10)} ~ ${String(p.cycleEnd).slice(0,10)}`:id?t('planReference',{id}):t('fixedRule')}
function openKpiSettings(){router.push({path:'/business/kpi-bonus',query:{projectId:projectId.value}})}
function syncTiers(){ruleForm.tiers.forEach((tier,index)=>{tier.minScore=index?ruleForm.tiers[index-1].maxScore:0});if(ruleForm.tiers.length)ruleForm.tiers.at(-1).maxScore=null}
function addTier(){const last=ruleForm.tiers.at(-1);if(ruleForm.tiers.length>=20||Number(last.minScore)>=120)return;last.maxScore=Math.min(Number(last.minScore)+10,120);ruleForm.tiers.push({minScore:last.maxScore,maxScore:null,amount:null})}
function removeTier(index){if(ruleForm.tiers.length>1){ruleForm.tiers.splice(index,1);syncTiers()}}
async function load(){
 const n=++sequence;loading.value=true
 try{
  const res=await getBonusDistribution({projectId:projectId.value||undefined})
  if(n!==sequence)return
  const d=res.data||{}
  const original=d.manager&&d.project?await getIncentiveWorkspace({projectId:d.project.projectId}):{data:{project:d.project}}
  if(n!==sequence)return
  Object.keys(distribution).forEach(k=>delete distribution[k]);Object.assign(distribution,d)
  Object.keys(data).forEach(k=>delete data[k]);Object.assign(data,original.data||{},{projects:d.projects,distributionOnly:!d.manager})
  if(data.distributionOnly)activeTab.value='distribution'
  if(!scoreRules.value.some(r=>Number(r.ruleId)===Number(previewRuleId.value)))previewRuleId.value=scoreRules.value[0]?.ruleId||null
  projectId.value=data.project?.projectId||projectId.value
  if(history.value)history.value=(data.awards||[]).find(a=>a.awardId===history.value.awardId)||null
  refreshKey.value++
 }catch(error){if(n===sequence){Object.keys(data).forEach(k=>delete data[k]);Object.keys(distribution).forEach(k=>delete distribution[k]);history.value=null}throw error}
 finally{if(n===sequence)loading.value=false}
}
async function switchProject(){history.value=null;ruleOpen.value=false;awardOpen.value=false;previewRuleId.value=null;invalidateEstimate();await router.replace({query:{...route.query,projectId:projectId.value}});await load()}
function openRule(row={}){Object.assign(ruleForm,{projectId:projectId.value,policyVersion:'SCORE_TIERS_V1',kpiPlanId:row.kpiPlanId||kpiPlans.value[0]?.planId,ruleName:row.ruleName||'',currency:data.project?.baseCurrency||'CNY',reason:'',tiers:row.tiers?.length?row.tiers.map(tier=>({minScore:Number(tier.minScore),maxScore:tier.maxScore==null?null:Number(tier.maxScore),amount:Number(tier.amount)})):[{minScore:0,maxScore:80,amount:0},{minScore:80,maxScore:90,amount:null},{minScore:90,maxScore:100,amount:null},{minScore:100,maxScore:null,amount:null}]});draftScore.value=100;ruleOpen.value=true}
function openAward(){Object.assign(awardForm,{projectId:projectId.value,ruleId:null,settlementId:null,bizDate:data.project?.actualEndDate||today(),reason:'',requestKey:globalThis.crypto?.randomUUID?.()||`award-${Date.now()}-${Math.random().toString(36).slice(2)}`});estimate.value=null;awardOpen.value=true}
async function saveRule(){if(!ruleForm.kpiPlanId||!ruleForm.ruleName?.trim()||!ruleForm.reason?.trim())return ElMessage.warning(t('required'));if(tierError.value)return ElMessage.warning(tierError.value);saving.value=true;try{await createIncentiveRule(ruleForm);ruleOpen.value=false;await load();ElMessage.success(t('success'))}finally{saving.value=false}}
function changeAwardRule(){awardForm.settlementId=null;invalidateEstimate()}
function invalidateEstimate(){estimateSequence++;estimate.value=null;estimating.value=false}
async function calculate(){const n=++estimateSequence;estimating.value=true;try{const res=await estimateIncentive({projectId:projectId.value,ruleId:awardForm.ruleId,settlementId:awardForm.settlementId||null});if(n===estimateSequence)estimate.value=res.data}finally{if(n===estimateSequence)estimating.value=false}}
async function saveAward(){if(!awardForm.ruleId||!awardForm.bizDate||!awardForm.reason?.trim()||(isScoreRule(awardRule.value)&&!awardForm.settlementId))return ElMessage.warning(t('required'));if(estimate.value&&Number(estimate.value.amount)===0)return ElMessage.info(t('noBonus'));saving.value=true;try{await createIncentiveAward({...awardForm,settlementId:awardForm.settlementId||null});awardOpen.value=false;await load();ElMessage.success(t('success'))}finally{saving.value=false}}
const validReason=value=>!value?.trim()?t('required'):value.trim().length>500?t('maxReason'):true
async function act(row,action,decision){if(acting.value)return;acting.value=true;try{const label=decision==='APPROVED'?t('approve'):decision==='RETURNED'?t('return'):action==='cancel'?t('cancelAward'):action==='resubmit-cost'?t('resubmitCost'):t('submit');const{value}=await ElMessageBox.prompt(`${label} #${row.awardId} · ${amount(row.amount)} ${row.currency}。${decision==='APPROVED'?t('awardHint'):''} ${t('actionReason')}`,t('actionTitle'),{inputType:'textarea',inputValidator:validReason,type:'warning'});await actIncentiveAward(row.awardId,action,{version:row.version,reason:value.trim(),decision});await load();ElMessage.success(t('success'))}catch(error){if(!['cancel','close'].includes(error))await load()}finally{acting.value=false}}
async function retireRule(row){try{const {value}=await ElMessageBox.prompt(t('actionReason'),t('retire'),{inputValidator:validReason});await retireIncentiveRule(row.ruleId,{reason:value.trim()});await load()}catch(e){if(!['cancel','close'].includes(e))await load()}}
function openAccounting(row){router.push({path:'/finance/accounting',query:{projectId:projectId.value,factId:row.accountingFactId}})}
watch(()=>route.query.projectId,value=>{const id=Number(value)||null;if(id!==projectId.value){projectId.value=id;history.value=null;load()}})
useBusinessRefreshOnReactivated(load)
onMounted(load)
</script>

<style scoped>
.score-formula{margin:0 0 20px;padding:16px 18px;border:1px solid #d7e8e3;border-radius:8px;background:#f4f9f7;color:#364d46;font-size:13px;line-height:1.65}.score-formula h3{margin:0 0 4px;font-size:15px}.score-formula p{margin:6px 0;overflow-wrap:anywhere}.score-formula-source{color:#60776c}.score-formula-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px 20px;margin-top:12px}.score-formula-total{padding:10px 12px;background:#e7f2ed;border-radius:4px}.score-formula-example{border-top:1px solid #d7e8e3;padding-top:8px}@media(max-width:600px){.score-formula-grid{grid-template-columns:1fr}.score-formula{padding:12px}}
.rule-expanded{padding:12px 24px}.bonus-preview{display:flex;align-items:baseline;flex-wrap:wrap;gap:18px;padding:20px;border-radius:10px;background:#eef7f3}.bonus-preview strong{font-size:28px;color:#24675a}.bonus-preview small{color:#60776c}.tier-editor{margin:18px 0}.tier-edit-row{display:grid;grid-template-columns:110px 1fr 1.2fr 56px;align-items:center;gap:16px;padding:12px 0;border-bottom:1px solid #e8ecef}.tier-edit-row>div{display:flex;min-width:0;flex-direction:column;gap:8px}.tier-edit-row span{font-size:12px;color:#647888}.tier-edit-row .el-input-number{width:100%}.draft-preview{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-top:20px}.draft-preview strong{color:#24675a}@media(max-width:600px){.tier-edit-row{grid-template-columns:1fr 1fr}.tier-edit-row>div:nth-child(3){grid-column:1}.rule-expanded{padding:8px}.bonus-preview strong{font-size:24px}}
</style>
