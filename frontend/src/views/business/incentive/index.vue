<template>
  <div class="business-product" v-loading="loading">
    <header class="product-head"><div><h1>{{ t('title') }}</h1><p>{{ t('profitIntro') }}</p></div><el-button icon="Refresh" @click="load">{{ t('refresh') }}</el-button></header>
    <div class="product-toolbar"><el-select v-model="projectId" filterable :placeholder="t('project')" @change="switchProject"><el-option v-for="p in data.projects || []" :key="p.projectId" :label="p.projectName" :value="p.projectId" /></el-select></div>
    <el-empty v-if="!data.project && !loading" :description="t('chooseProject')" />
    <template v-if="data.project">
      <el-alert v-if="bonusProfitBlock" :title="t(bonusProfitBlock)" type="warning" :closable="false" show-icon />
      <el-tabs v-model="activeTab">
        <el-tab-pane v-if="!data.distributionOnly" :label="t('bonusSettings')" name="rules">
          <section class="product-card">
            <div class="product-head"><div><h2>{{ t('bonusSettings') }}</h2><p>{{ t('scoreRuleHint') }}</p></div><el-button type="primary" :disabled="!canConfigureRules || !!bonusProfitBlock" @click="openRule()">{{ t('newRule') }}</el-button></div>
            <el-alert v-if="!canConfigureRules" :title="ruleBlockReason" type="info" :closable="false" show-icon />
            <div class="bonus-overview" :aria-label="t('bonusOverview')">
              <article class="bonus-overview-profit"><span>{{ t('afterTaxProfit') }}</span><strong>{{ data.profitResult?.available ? amount(data.profitResult.afterTaxProfit) : '—' }}</strong><small>{{ data.profitResult?.month }} · {{ t('monthlySettlement') }} · {{ data.profitResult?.currency || data.project?.baseCurrency }}</small></article>
              <article><span>{{ t('mainOwnerShare') }}</span><div class="share-value"><strong>{{ summaryShares?.mainOwnerBonusRate == null ? t('shareNotSet') : `${Number(summaryShares.mainOwnerBonusRate)}%` }}</strong><span class="bonus-overview-amount">{{ t('expectedBonus') }} {{ amount(summaryOwnerBonus) }} {{ summaryCurrency }}</span></div><small>{{ data.project?.mainOwnerName || '—' }}</small></article>
              <article><span>{{ t('sponsorOwnerShare') }}</span><div class="share-value"><strong>{{ summaryShares?.sponsorOwnerBonusRate == null ? t('shareNotSet') : `${Number(summaryShares.sponsorOwnerBonusRate)}%` }}</strong><span class="bonus-overview-amount">{{ t('expectedBonus') }} {{ amount(summarySponsorBonus) }} {{ summaryCurrency }}</span></div><small>{{ data.project?.sponsorOwnerName || data.project?.initiatorName || '—' }}</small></article>
            </div>
            <p class="bonus-overview-hint">{{ t('bonusOverviewHint') }}</p>
            <el-alert v-if="!data.profitResult?.available" :title="t('noProfitResult')" type="info" :closable="false" show-icon />
            <el-alert v-else-if="data.profitResult?.taxConfigured===false" :title="t('taxRateMissing')" type="warning" :closable="false" show-icon />
            <el-table :data="data.rules || []" :empty-text="t('empty')">
              <el-table-column type="expand"><template #default="{row}"><div class="rule-expanded"><h3>{{ isProfitRule(row) ? t('profitBased') : planLabel(row.kpiPlanId) }}</h3><p>{{ row.reason }}</p><el-table v-if="isScoreRule(row)" :data="row.tiers || []"><el-table-column :label="t('scoreRange')"><template #default="{row:tier}">{{ tierRange(tier) }}</template></el-table-column><el-table-column :label="t('amount')"><template #default="{row:tier}">{{ amount(tier.amount) }} {{ row.currency }}</template></el-table-column></el-table><p v-else-if="isProfitRule(row)">{{ row.settlementMonth || t('legacyCumulative') }} · {{ t('legacyAfterTaxProfit') }} {{ amount(row.afterTaxProfit) }} {{ row.currency }} · {{ t('mainOwnerShare') }} {{ row.mainOwnerBonusRate }}% · {{ t('sponsorOwnerShare') }} {{ row.sponsorOwnerBonusRate }}% · {{ t('estimatedTotalBonus') }} {{ amount(row.amount) }} {{ row.currency }}</p><p v-else>{{ t('fixedRule') }} · {{ amount(row.amount) }} {{ row.currency }} · {{ t('score') }} {{ row.minScore ?? t('noScore') }}</p></div></template></el-table-column>
              <el-table-column prop="ruleName" :label="t('rule')" min-width="170" />
              <el-table-column :label="t('legacyAfterTaxProfit')" min-width="170"><template #default="{row}">{{ amount(row.afterTaxProfit) }} {{ row.afterTaxProfit == null ? '' : row.currency }}</template></el-table-column>
              <el-table-column :label="t('mainOwnerShare')" min-width="145"><template #default="{row}">{{ row.mainOwnerBonusRate == null ? '—' : row.mainOwnerBonusRate + '%' }}</template></el-table-column>
              <el-table-column :label="t('sponsorOwnerShare')" min-width="155"><template #default="{row}">{{ row.sponsorOwnerBonusRate == null ? '—' : row.sponsorOwnerBonusRate + '%' }}</template></el-table-column>
              <el-table-column :label="t('bonusMethod')" min-width="125"><template #default="{row}">{{ isProfitRule(row) ? t('profitBased') : isScoreRule(row) ? t('tierCount',{count:row.tiers?.length||0}) : t('fixedRule') }}</template></el-table-column>
              <el-table-column :label="t('settlementMonth')" min-width="130"><template #default="{row}">{{ row.settlementMonth || t('legacyCumulative') }}</template></el-table-column><el-table-column prop="ruleVersion" :label="t('version')" width="75" />
              <el-table-column :label="t('ruleStatus')" width="110"><template #default="{row}"><el-tag :type="row.status==='ACTIVE'?'success':'info'">{{ state(row.status) }}</el-tag></template></el-table-column>
              <el-table-column :label="t('actions')" min-width="160"><template #default="{row}"><el-button v-if="data.canManageRules" v-hasPermi="['business:incentive:rule','business:kpi:manage']" link type="primary" :disabled="!!bonusProfitBlock" @click="openRule(row)">{{ t('reviseRule') }}</el-button><el-button v-if="data.canRetireRules && row.status==='ACTIVE'" v-hasPermi="['business:incentive:rule','business:kpi:manage']" link type="danger" @click="retireRule(row)">{{ t('retire') }}</el-button></template></el-table-column>
            </el-table>
          </section>
        </el-tab-pane>
        <el-tab-pane v-if="!data.distributionOnly" :label="t('awardProcessing')" name="awards">
          <section class="product-card"><el-alert v-if="!activeRules.length && !bonusProfitBlock" :title="t('monthlyRuleRequired')" type="info" :closable="false" /><div class="product-head"><div><h2>{{ t('awardProcessing') }}</h2><p>{{ t('awardApplicationHint') }}</p></div><el-button v-if="data.canApply" v-hasPermi="['business:incentive:apply']" type="primary" :disabled="!activeRules.length || !!bonusProfitBlock" @click="openAward">{{ t('newAward') }}</el-button></div><el-alert v-if="sameApprover" :title="t('selfApproval')" type="warning" :closable="false" show-icon />
          <div class="award-application-records"><h3>{{ t('awardApplicationList') }}</h3>
            <el-table :data="applicationAwards" :empty-text="t('empty')">
              <el-table-column :label="t('rule')" min-width="165"><template #default="{row}">{{ row.ruleName || `#${row.ruleId}` }}<div class="muted">{{ row.awardNo || `#${row.awardId}` }} · {{ row.settlementMonth || t('legacyCumulative') }}</div></template></el-table-column>
              <el-table-column :label="t('amount')" min-width="135"><template #default="{row}">{{ amount(row.amount ?? row.approvedAmount) }} {{ row.currency }}</template></el-table-column>
              <el-table-column prop="bizDate" :label="t('date')" width="115" />
              <el-table-column :label="t('approval')" min-width="115"><template #default="{row}"><el-tag :type="tone(row.status)">{{ state(row.status) }}</el-tag></template></el-table-column>
              <el-table-column :label="t('mainOwnerShare')" min-width="145"><template #default="{row}">{{ row.ruleMainOwnerBonusRate == null ? '—' : `${Number(row.ruleMainOwnerBonusRate)}%` }}</template></el-table-column>
              <el-table-column :label="t('mainOwnerAllocationAmount')" min-width="175"><template #default="{row}">{{ amount(row.ruleMainOwnerBonusAmount) }} {{ row.currency }}</template></el-table-column>
              <el-table-column :label="t('sponsorOwnerShare')" min-width="155"><template #default="{row}">{{ row.ruleSponsorOwnerBonusRate == null ? '—' : `${Number(row.ruleSponsorOwnerBonusRate)}%` }}</template></el-table-column>
              <el-table-column :label="t('sponsorOwnerAllocationAmount')" min-width="175"><template #default="{row}">{{ amount(row.ruleSponsorOwnerBonusAmount) }} {{ row.currency }}</template></el-table-column>
              <el-table-column :label="t('memberAllocationColumn')" min-width="115"><template #default="{row}"><el-button link type="primary" @click="allocationDetails=row">{{ t('allocationDetails') }}</el-button></template></el-table-column>
              <el-table-column :label="t('actions')" min-width="225" fixed="right"><template #default="{row}">
                <el-button v-hasPermi="['business:incentive:apply']" link type="primary" :disabled="acting || !canEditAward(row) || !!awardProfitBlock(row)" :title="awardProfitBlock(row) ? t(awardProfitBlock(row)) : canEditAward(row) ? '' : t('draftOnlyActions')" @click="editAward(row)">{{ t('editAward') }}</el-button>
                <el-button v-hasPermi="['business:incentive:apply']" link type="danger" :disabled="acting || !canEditAward(row)" :title="canEditAward(row) ? '' : t('draftOnlyActions')" @click="deleteAward(row)">{{ t('deleteAward') }}</el-button>
                <el-button v-if="row.canSubmit" v-hasPermi="['business:incentive:apply']" link type="primary" :disabled="acting || !!awardProfitBlock(row)" @click="act(row,'submit')">{{ t('submit') }}</el-button>
              </template></el-table-column>
            </el-table>
            <p>{{ t('awardReviewHint') }}</p>
          </div></section>
      <section v-if="data.legacyBonuses?.length" class="product-card"><h2>{{ t('legacy') }}</h2><p>{{ t('legacyHint') }}</p><el-table :data="data.legacyBonuses"><el-table-column prop="periodEnd" :label="t('date')"/><el-table-column :label="t('amount')"><template #default="{row}">{{ amount(row.bonusAmount) }} {{ row.currency || 'CNY' }}</template></el-table-column><el-table-column :label="t('approval')"><template #default="{row}">{{ row.status==='CONFIRMED'?t('indicatorConfirmed'):state(row.status) }}</template></el-table-column><el-table-column :label="t('cost')"><template #default="{row}">{{ costState(row.costStatus) }}</template></el-table-column><el-table-column :label="t('payment')"><template #default>{{ t('notRecorded') }}</template></el-table-column></el-table></section>
        </el-tab-pane>
        <el-tab-pane :label="t('distributionTitle')" name="distribution">
          <template v-if="!data.distributionOnly">
            <el-alert :title="t('boundary')" type="info" :closable="false" show-icon />
      <section class="product-card"><h2>{{ t('awardReviewList') }}</h2><p>{{ t('awardHint') }}</p>
        <el-table ref="awardTable" row-key="awardId" highlight-current-row :row-class-name="({row}) => String(row.awardId) === String(route.query.awardId) ? 'requested-award-row' : ''" :data="data.awards || []" :empty-text="t('empty')">
          <el-table-column :label="t('rule')" min-width="165"><template #default="{row}">{{ row.ruleName || `#${row.ruleId}` }}<div class="muted">{{ row.awardNo || `#${row.awardId}` }} · {{ row.settlementMonth || t('legacyCumulative') }}</div></template></el-table-column>
          <el-table-column :label="t('amount')" min-width="135"><template #default="{row}">{{ amount(row.amount ?? row.approvedAmount) }} {{ row.currency }}</template></el-table-column>
          <el-table-column prop="bizDate" :label="t('date')" width="115" />
          <el-table-column :label="t('approval')" min-width="115"><template #default="{row}"><el-tag :type="tone(row.status)">{{ state(row.status) }}</el-tag></template></el-table-column>
          <el-table-column :label="t('mainOwnerShare')" min-width="145"><template #default="{row}">{{ row.ruleMainOwnerBonusRate == null ? '—' : `${Number(row.ruleMainOwnerBonusRate)}%` }}</template></el-table-column>
          <el-table-column :label="t('mainOwnerAllocationAmount')" min-width="175"><template #default="{row}">{{ amount(row.ruleMainOwnerBonusAmount) }} {{ row.currency }}</template></el-table-column>
          <el-table-column :label="t('sponsorOwnerShare')" min-width="155"><template #default="{row}">{{ row.ruleSponsorOwnerBonusRate == null ? '—' : `${Number(row.ruleSponsorOwnerBonusRate)}%` }}</template></el-table-column>
          <el-table-column :label="t('sponsorOwnerAllocationAmount')" min-width="175"><template #default="{row}">{{ amount(row.ruleSponsorOwnerBonusAmount) }} {{ row.currency }}</template></el-table-column>
          <el-table-column :label="t('memberAllocationColumn')" min-width="115"><template #default="{row}"><el-button link type="primary" @click="allocationDetails=row">{{ t('allocationDetails') }}</el-button></template></el-table-column>
          <el-table-column :label="t('actions')" min-width="275" fixed="right"><template #default="{row}"><div class="row-actions">
            <el-button link @click="history=row">{{ t('details') }}</el-button>
            <el-button v-if="row.canReview" v-hasPermi="['business:incentive:approve']" link type="success" :disabled="acting" @click="history=row">{{ t('reviewDetails') }}</el-button>
            <el-button v-if="row.canCancel" v-hasPermi="['business:incentive:apply','business:incentive:approve']" link type="danger" :disabled="acting" @click="act(row,'cancel')">{{ t('cancelAward') }}</el-button>
            <el-button v-if="row.canResubmitCost" v-hasPermi="['business:incentive:approve']" link type="primary" :disabled="acting" @click="act(row,'resubmit-cost')">{{ t('resubmitCost') }}</el-button>
            <el-button v-if="row.accountingFactId" v-hasPermi="['business:accounting:list']" link @click="openAccounting(row)">{{ t('openCost') }}</el-button>
          </div></template></el-table-column>
        </el-table>
        <p>{{ t('awardReviewHint') }}</p>
      </section>
          </template>
          <DistributionPanel :data="distribution" :application-awards="data.distributionOnly ? [] : data.awards || []" :requested-allocation-id="activeTab === 'distribution' ? route.query.allocationId : null" @open-award="history=$event" @refresh="load" />
        </el-tab-pane>
      </el-tabs>
    </template>
    <el-dialog v-model="ruleOpen" :title="t('newRule')" width="min(850px,95vw)" append-to-body>
      <el-form label-position="top" :model="ruleForm">
        <el-form-item :label="t('rule')" required><el-input v-model="ruleForm.ruleName" :placeholder="t('bonusNamePlaceholder')" maxlength="100" /></el-form-item>
        <el-form-item :label="t('afterTaxProfit')">
          <div class="profit-field"><strong>{{ data.profitResult?.available ? amount(data.profitResult.afterTaxProfit) : '—' }}</strong><span>{{ data.profitResult?.currency || ruleForm.currency }}</span></div>
          <p class="muted">{{ t('settlementMonth') }}：{{ data.profitResult?.month }} · {{ t('monthlySettlement') }}</p><p class="muted">{{ t('profitSource') }}</p>
          <el-alert v-if="!data.profitResult?.available" :title="t('noProfitResult')" type="info" :closable="false" show-icon />
          <el-alert v-else-if="data.profitResult?.taxConfigured===false" :title="t('taxRateMissing')" type="warning" :closable="false" show-icon />
        </el-form-item>
        <el-form-item :label="t('bonusShareTitle')" required>
          <div class="rule-share-grid">
            <div><label>{{ t('mainOwnerShare') }}</label><small>{{ data.project?.mainOwnerName || '—' }}</small><div class="percent-input"><el-input-number v-model="ruleForm.mainOwnerBonusRate" :aria-label="t('mainOwnerShare')" :min="0" :max="100" :precision="4" :step="1" controls-position="right" /><span>%</span></div><p class="rule-share-amount" aria-live="polite">{{ t('expectedAllocationAmount') }} <strong>{{ amount(draftOwnerBonus) }} {{ ruleForm.currency }}</strong></p></div>
            <div><label>{{ t('sponsorOwnerShare') }}</label><small>{{ data.project?.sponsorOwnerName || data.project?.initiatorName || '—' }}</small><div class="percent-input"><el-input-number v-model="ruleForm.sponsorOwnerBonusRate" :aria-label="t('sponsorOwnerShare')" :min="0" :max="100" :precision="4" :step="1" controls-position="right" /><span>%</span></div><p class="rule-share-amount" aria-live="polite">{{ t('expectedAllocationAmount') }} <strong>{{ amount(draftSponsorBonus) }} {{ ruleForm.currency }}</strong></p></div>
          </div>
          <p class="muted">{{ t('combinedShare') }} {{ totalBonusShare }}% · {{ t('estimatedTotalBonus') }} {{ amount(totalExpectedBonus) }} {{ ruleForm.currency }}</p>
          <p class="muted">{{ t('profitBonusHint') }}</p>
          <el-alert v-if="bonusShareError" :title="bonusShareError" type="error" :closable="false" show-icon />
        </el-form-item>
        <el-form-item :label="t('reason')" required><el-input v-model="ruleForm.reason" type="textarea" maxlength="500" /></el-form-item>
        <el-alert :title="t('versionHint')" type="info" :closable="false" />
      </el-form>
      <template #footer><el-button @click="ruleOpen=false">{{ t('cancel') }}</el-button><el-button type="primary" :loading="saving" :disabled="!!bonusShareError || !!bonusProfitBlock" @click="saveRule">{{ t('publish') }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="awardOpen" :title="t(editingAward ? 'editAward' : 'newAward')" width="min(950px,95vw)" append-to-body>
      <el-form label-position="top" :model="awardForm"><el-form-item :label="t('rule')" required><el-select v-model="awardForm.ruleId" :disabled="!!editingAward" style="width:100%" @change="changeAwardRule"><el-option v-for="r in awardRuleOptions" :key="r.ruleId" :label="`${r.ruleName} · ${isScoreRule(r)?t('scoreBased'):amount(r.amount)} ${r.currency}`" :value="r.ruleId"/></el-select></el-form-item><el-form-item v-if="!isProfitRule(awardRule)" :label="isScoreRule(awardRule)?t('confirmedEvidence'):t('kpiEvidence')" :required="isScoreRule(awardRule)"><el-select v-model="awardForm.settlementId" :disabled="!!editingAward" clearable style="width:100%"><el-option v-for="k in awardEvidence" :key="k.settlementId" :label="`${k.periodEnd || k.cycleEnd} · ${k.totalScore ?? '—'}`" :value="k.settlementId"/></el-select></el-form-item><el-form-item :label="t('settlementMonth')"><strong>{{ awardFormMonthLabel }}</strong></el-form-item><el-form-item :label="t('date')" required><el-date-picker v-model="awardForm.bizDate" type="date" value-format="YYYY-MM-DD" :disabled-date="futureDate"/></el-form-item><el-form-item :label="t('reason')" required><el-input v-model="awardForm.reason" type="textarea" maxlength="500"/></el-form-item><el-alert v-if="isScoreRule(awardRule) && !awardEvidence.length && !editingAward" :title="t('noConfirmedEvidence')" type="info" :closable="false" /><AwardAllocationEditor :model="applicationAllocation" :recipients="distribution.recipients || []" :source-amount="awardAllocationSource" :currency="awardRule?.currency || data.project?.baseCurrency" /></el-form>
      <template #footer><el-button @click="awardOpen=false">{{ t('cancel') }}</el-button><el-button type="primary" :loading="saving" :disabled="!!(editingAward ? awardProfitBlock(editingAward) : bonusProfitBlock)" @click="saveAward">{{ t('saveDraft') }}</el-button></template>
    </el-dialog>
    <el-dialog :model-value="!!allocationDetails" :title="t('allocationDetails')" width="min(1200px,96vw)" append-to-body @close="allocationDetails=null">
      <template v-if="allocationDetails">
        <AwardAllocationDetails v-if="allocationDetails.applicationAllocation" :allocation="allocationDetails.applicationAllocation" :currency="allocationDetails.currency" />
        <el-empty v-else :description="t('noMemberAllocation')" />
      </template>
    </el-dialog>
    <el-drawer :model-value="!!history" :title="t('details')" size="min(640px,95vw)" @close="history=null"><template v-if="history"><h3>{{ t('awardApplicationDetails') }}</h3><el-descriptions :column="1" border><el-descriptions-item v-if="history.settlementMonth" :label="t('settlementMonth')">{{ history.settlementMonth }} · {{ t('monthlySettlement') }}</el-descriptions-item><el-descriptions-item :label="t('rule')">{{ history.ruleName }} · v{{ history.ruleVersion }}</el-descriptions-item><el-descriptions-item :label="t('amount')">{{ amount(history.amount) }} {{ history.currency }}</el-descriptions-item><el-descriptions-item :label="t('applicant')">{{ history.applicantUserName }}</el-descriptions-item><el-descriptions-item :label="t('approver')">{{ history.approvedUserName || '—' }}</el-descriptions-item><el-descriptions-item :label="t('approvedTime')">{{ history.approvedTime || '—' }}</el-descriptions-item><el-descriptions-item v-if="!isProfitRule(history)" :label="t('scoreSnapshot')">{{ history.scoreSnapshot ?? '—' }}</el-descriptions-item><el-descriptions-item :label="t('applicationReason')">{{ history.reason }}</el-descriptions-item><el-descriptions-item :label="t('allocation')">{{ awardAllocationLabel(history) }}</el-descriptions-item><el-descriptions-item :label="t('approval')">{{ state(history.status) }}</el-descriptions-item></el-descriptions><AwardAllocationDetails :allocation="history.applicationAllocation" :currency="history.currency" /><AwardRuleDetails :award="history" :label="t" :format-amount="amount" /><el-timeline style="margin-top:24px"><el-timeline-item v-for="(event,index) in history.events || []" :key="event.eventId || index" :timestamp="event.createTime || event.eventTime"><b>{{ event.operatorName || event.operatorUserName }}</b> · {{ state(event.toStatus || event.action) }}<p>{{ event.reason || event.comment }}</p></el-timeline-item></el-timeline></template><template #footer><div v-if="activeTab==='distribution' && history?.canReview" class="row-actions"><el-button v-hasPermi="['business:incentive:approve']" type="warning" :disabled="acting" @click="act(history,'review','RETURNED')">{{ t('return') }}</el-button><el-button v-hasPermi="['business:incentive:approve']" type="success" :disabled="acting || !!awardProfitBlock(history)" @click="act(history,'review','APPROVED')">{{ t('approve') }}</el-button></div></template></el-drawer>
  </div>
</template>

<script setup name="BusinessIncentive">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getIncentiveWorkspace, createIncentiveRule, createIncentiveAward, updateIncentiveAward, actIncentiveAward, retireIncentiveRule } from '@/api/business/incentive'
import '@/assets/styles/business-product.scss'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
import scoreMessages from './scoreMessages'
import { isScoreRule, matchTier } from './scoreRules'
import { activeMonthlyRules, recordBonusBlockReason, bonusProfitBlockReason, currentBonusShares, profitShareAmount } from './bonusSummary'
import auth from '@/plugins/auth'
import DistributionPanel from './DistributionPanel.vue'
import AwardRuleDetails from './AwardRuleDetails.vue'
import AwardAllocationEditor from './AwardAllocationEditor.vue'
import AwardAllocationDetails from './AwardAllocationDetails.vue'
import { emptyAllocationProposal, proposalError, proposalPayload } from './allocationProposal'
import { getBonusDistribution } from '@/api/business/incentive'
const distribution=reactive({})

const route=useRoute(), router=useRouter(), projectId=ref(route.query.projectId ? Number(route.query.projectId) : null)
const data=reactive({}), loading=ref(false), saving=ref(false), ruleOpen=ref(false), awardOpen=ref(false), editingAward=ref(null), history=ref(null), allocationDetails=ref(null), refreshKey=ref(0)
const ruleForm=reactive({}), awardForm=reactive({}), acting=ref(false); let sequence=0
const applicationAllocation=reactive(emptyAllocationProposal())
const awardTable=ref(null)
const activeTab=ref(['rules','awards','distribution'].includes(route.query.tab)?route.query.tab:'rules')
const {t,te}=useI18n({useScope:'local',messages:{
  'zh-CN':{...scoreMessages['zh-CN'],title:'奖金激励',version:'版本',retire:'停用方案',indicatorConfirmed:'指标已确认',pendingCost:'待确认',allocation:'个人分配',applicant:'申请人',approver:'核准人',approvedTime:'核准时间',scoreSnapshot:'指标得分快照',maxReason:'说明不得超过500字',selfApproval:'主负责人与归属老板相同时，不能自批奖励；须先明确不同的业务核准责任人。',intro:'按项目 KPI 综合得分设置奖金档位，确认指标后按对应方案申请奖励。',refresh:'刷新',project:'选择项目',chooseProject:'请选择有权查看的项目',newRule:'设置奖金方案',newAward:'新建奖励申请',boundary:'奖励核准、成本入账、个人分配与支付分别记录。这里不会把奖金池标记为已发薪。',awards:'奖金分配与申请',awardHint:'申请人提交，由项目归属责任人核准；核准后生成待确认成本。',empty:'暂无记录',rule:'奖金方案',amount:'奖励金额',date:'业务日期',approval:'核准状态',cost:'成本状态',payment:'支付状态',actions:'操作',details:'详情与记录',submit:'提交核准',approve:'核准',return:'退回',cancelAward:'撤销',resubmitCost:'重新提交成本',openCost:'查看成本',rules:'奖金方案版本',ruleHint:'已发布方案保留原值。调整时发布新方案，既有申请继续引用原版本。',score:'最低综合得分',noScore:'不要求指标分数',reason:'依据与说明',legacy:'历史项目奖金池',legacyHint:'原方案按历史规则结清；没有个人分配和支付记录，不生成新的重复奖励。',notRecorded:'未记录',cancel:'取消',publish:'发布方案',scoreHint:'选填；填写后，奖励申请须引用已确认且达到门槛的独立项目指标。',kpiEvidence:'已确认项目指标（可选）',estimate:'测算奖励',estimated:'测算金额：',estimateHint:'测算不代表核准或入账',saveDraft:'保存草稿',required:'请完整填写必填资料',success:'操作成功',actionTitle:'确认奖励操作',actionReason:'请填写本次操作的依据或原因',states:{ACTIVE:'有效',RETIRED:'已停用',DRAFT:'草稿',SUBMITTED:'待核准',RETURNED:'已退回',APPROVED:'已核准',CONFIRMED:'已入账',CANCELED:'已撤销',CANCELLED:'已撤销',VOIDED:'已作废',REVERSED:'已冲正',NOT_CREATED:'未生成',PENDING:'待确认',NOT_RECORDED:'未记录',NONE:'未生成'}},
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
const activeRules=computed(()=>activeMonthlyRules(data.rules||[],data.profitResult?.month))
const applicationAwards=computed(()=>(data.awards||[]).filter(row=>row.status!=='CANCELED'))
const canEditAward=row=>row.canSubmit && ['DRAFT','RETURNED'].includes(row.status)
const awardRuleOptions=computed(()=>editingAward.value?[{ruleId:editingAward.value.ruleId,ruleName:editingAward.value.ruleName,amount:editingAward.value.amount,currency:editingAward.value.currency,policyVersion:editingAward.value.policyVersion,settlementMonth:editingAward.value.settlementMonth}]:activeRules.value)
const awardFormMonthLabel=computed(()=>{const month=editingAward.value?editingAward.value.settlementMonth:awardRule.value?.settlementMonth||data.profitResult?.month;return month?`${month} · ${t('monthlySettlement')}`:t('legacyCumulative')})
const kpiPlans=computed(()=>data.kpiPlans||[])
const bonusProfitBlock=computed(()=>data.distributionOnly ? distribution.bonusBlockReason ?? 'noProfitResult' : bonusProfitBlockReason(data.profitResult))
const awardProfitBlock=row=>recordBonusBlockReason(row,bonusProfitBlock.value)
const canConfigureRules=computed(()=>data.canManageRules && auth.hasPermiOr(['business:incentive:rule','business:kpi:manage']))
const ruleBlockReason=computed(()=>data.project?.accountingState==='CLOSED'?t('rulesAccountingClosed'):data.project?.status!=='ACTIVE'?t('rulesProjectInactive'):t('rulesReadOnly'))
const positiveAfterTaxProfit=computed(()=>Math.max(0,Number(data.profitResult?.afterTaxProfit)||0))
const totalBonusShare=computed(()=>Number(ruleForm.mainOwnerBonusRate||0)+Number(ruleForm.sponsorOwnerBonusRate||0))
const totalExpectedBonus=computed(()=>Math.round((positiveAfterTaxProfit.value*totalBonusShare.value/100+Number.EPSILON)*100)/100)
const draftOwnerBonus=computed(()=>profitShareAmount(data.profitResult,ruleForm.mainOwnerBonusRate))
const draftSponsorBonus=computed(()=>profitShareAmount(data.profitResult,ruleForm.sponsorOwnerBonusRate))
const bonusShareError=computed(()=>[ruleForm.mainOwnerBonusRate,ruleForm.sponsorOwnerBonusRate].some(v=>v==null||!Number.isFinite(Number(v))||Number(v)<0||Number(v)>100)?t('rateRequired'):totalBonusShare.value>100?t('shareLimit'):'')
const isProfitRule=rule=>rule?.policyVersion==='PROFIT_SHARE_V1'
const summaryShares=computed(()=>currentBonusShares(data.rules||[],data.bonusSetting||{},data.profitResult?.month))
const summaryOwnerBonus=computed(()=>profitShareAmount(data.profitResult,summaryShares.value?.mainOwnerBonusRate))
const summarySponsorBonus=computed(()=>profitShareAmount(data.profitResult,summaryShares.value?.sponsorOwnerBonusRate))
const summaryCurrency=computed(()=>data.profitResult?.currency||data.project?.baseCurrency)
const awardRule=computed(()=>awardRuleOptions.value.find(r=>Number(r.ruleId)===Number(awardForm.ruleId)))
const awardAllocationSource=computed(()=>editingAward.value?(isProfitRule(editingAward.value)?editingAward.value.ruleMainOwnerBonusAmount:editingAward.value.amount):!awardRule.value?null:isProfitRule(awardRule.value)?profitShareAmount({available:awardRule.value.afterTaxProfit!=null,afterTaxProfit:awardRule.value.afterTaxProfit},awardRule.value.mainOwnerBonusRate):isScoreRule(awardRule.value)?matchTier(awardRule.value.tiers,awardEvidence.value.find(k=>Number(k.settlementId)===Number(awardForm.settlementId))?.totalScore)?.amount??null:awardRule.value.amount)
const awardEvidence=computed(()=>(data.confirmedKpis||[]).filter(k=>!isScoreRule(awardRule.value)||Number(k.planId)===Number(awardRule.value.kpiPlanId)))
const tierRange=tier=>tier.maxScore==null?t('finalRange',{min:tier.minScore}):t('boundedRange',{min:tier.minScore,max:tier.maxScore})
function planLabel(id){const p=kpiPlans.value.find(p=>Number(p.planId)===Number(id));return p?`v${p.planVersion} · ${String(p.cycleStart).slice(0,10)} ~ ${String(p.cycleEnd).slice(0,10)}`:id?t('planReference',{id}):t('fixedRule')}
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
  projectId.value=data.project?.projectId||projectId.value
  if(history.value)history.value=(data.awards||[]).find(a=>a.awardId===history.value.awardId)||null
  refreshKey.value++
  await focusRequestedAward()
 }catch(error){if(n===sequence){Object.keys(data).forEach(k=>delete data[k]);Object.keys(distribution).forEach(k=>delete distribution[k]);history.value=null}throw error}
 finally{if(n===sequence)loading.value=false}
}
async function focusRequestedAward(){
 if(data.distributionOnly || !route.query.awardId)return
 activeTab.value='distribution'
 await nextTick()
 const row=(data.awards||[]).find(a=>String(a.awardId)===String(route.query.awardId))
 awardTable.value?.setCurrentRow(row||null)
 if(row)history.value=row
 awardTable.value?.$el.querySelector('.requested-award-row')?.scrollIntoView({block:'center'})
}
async function switchProject(){history.value=null;allocationDetails.value=null;editingAward.value=null;ruleOpen.value=false;awardOpen.value=false;await router.replace({query:{...route.query,projectId:projectId.value,awardId:undefined,allocationId:undefined}});await load()}
function openRule(row={}){if(bonusProfitBlock.value)return ElMessage.warning(t(bonusProfitBlock.value));const source=isProfitRule(row)?row:(data.rules||[]).find(isProfitRule)||data.bonusSetting||{};Object.keys(ruleForm).forEach(k=>delete ruleForm[k]);Object.assign(ruleForm,{projectId:projectId.value,policyVersion:'PROFIT_SHARE_V1',ruleName:row.ruleName||'',currency:data.project?.baseCurrency||'CNY',settlementMonth:data.profitResult?.month,mainOwnerBonusRate:Number(source.mainOwnerBonusRate||0),sponsorOwnerBonusRate:Number(source.sponsorOwnerBonusRate||0),reason:''});ruleOpen.value=true}
function openAward(){if(bonusProfitBlock.value)return ElMessage.warning(t(bonusProfitBlock.value));editingAward.value=null;Object.assign(applicationAllocation,emptyAllocationProposal());Object.assign(awardForm,{projectId:projectId.value,ruleId:null,settlementId:null,bizDate:data.project?.actualEndDate||today(),reason:'',requestKey:globalThis.crypto?.randomUUID?.()||`award-${Date.now()}-${Math.random().toString(36).slice(2)}`});awardOpen.value=true}
function editAward(row){if(awardProfitBlock(row))return ElMessage.warning(t(awardProfitBlock(row)));if(!canEditAward(row))return;editingAward.value=row;Object.assign(applicationAllocation,row.applicationAllocation?{mode:row.applicationAllocation.mode,reason:row.applicationAllocation.reason||'',lines:(row.applicationAllocation.lines||[]).map(line=>({userId:line.userId,amount:line.amount,percentage:line.percentage,reason:line.reason||''}))}:emptyAllocationProposal());Object.assign(awardForm,{projectId:row.projectId,ruleId:row.ruleId,settlementId:row.settlementId,bizDate:row.bizDate,reason:row.reason,version:row.version});awardOpen.value=true}
async function saveRule(){if(bonusProfitBlock.value)return ElMessage.warning(t(bonusProfitBlock.value));if(!ruleForm.ruleName?.trim()||!ruleForm.reason?.trim())return ElMessage.warning(t('required'));if(bonusShareError.value)return ElMessage.warning(bonusShareError.value);saving.value=true;try{await createIncentiveRule({...ruleForm});ruleOpen.value=false;await load();ElMessage.success(t('success'))}finally{saving.value=false}}
function changeAwardRule(){Object.assign(applicationAllocation,emptyAllocationProposal());awardForm.settlementId=null}
async function saveAward(){const blocked=editingAward.value?awardProfitBlock(editingAward.value):bonusProfitBlock.value;if(blocked)return ElMessage.warning(t(blocked));if(!awardForm.ruleId||!awardForm.bizDate||!awardForm.reason?.trim()||(isScoreRule(awardRule.value)&&!awardForm.settlementId))return ElMessage.warning(t('required'));const allocationIssue=proposalError(applicationAllocation,awardAllocationSource.value);if(allocationIssue)return ElMessage.warning(t(allocationIssue==='required'?'required':allocationIssue==='percentTotal'?'allocationPercentTotal':'allocationInvalid'));saving.value=true;try{const payload={...awardForm,applicationAllocation:proposalPayload(applicationAllocation),settlementId:awardForm.settlementId||null};if(editingAward.value)await updateIncentiveAward(editingAward.value.awardId,payload);else await createIncentiveAward(payload);awardOpen.value=false;editingAward.value=null;await load();ElMessage.success(t('success'))}finally{saving.value=false}}
async function deleteAward(row){if(!canEditAward(row)||acting.value)return;try{await ElMessageBox.confirm(t('deleteAwardConfirm'),t('deleteAward'),{type:'warning'});acting.value=true;await actIncentiveAward(row.awardId,'cancel',{version:row.version,reason:t('deleteAwardReason')});await load();ElMessage.success(t('success'))}catch(error){if(!['cancel','close'].includes(error))await load()}finally{acting.value=false}}
const validReason=value=>!value?.trim()?t('required'):value.trim().length>500?t('maxReason'):true
async function act(row,action,decision){if(acting.value)return;if((action==='submit'||decision==='APPROVED')&&awardProfitBlock(row))return ElMessage.warning(t(awardProfitBlock(row)));acting.value=true;try{const label=decision==='APPROVED'?t('approve'):decision==='RETURNED'?t('return'):action==='cancel'?t('cancelAward'):action==='resubmit-cost'?t('resubmitCost'):t('submit');const{value}=await ElMessageBox.prompt(`${label} #${row.awardId} · ${amount(row.amount)} ${row.currency}。${decision==='APPROVED'?t('awardHint'):''} ${t('actionReason')}`,t('actionTitle'),{inputType:'textarea',inputValidator:validReason,type:'warning'});await actIncentiveAward(row.awardId,action,{version:row.version,reason:value.trim(),decision});await load();ElMessage.success(t(action==='submit'?'submittedToBoss':'success'))}catch(error){if(!['cancel','close'].includes(error))await load()}finally{acting.value=false}}
async function retireRule(row){try{const {value}=await ElMessageBox.prompt(t('actionReason'),t('retire'),{inputValidator:validReason});await retireIncentiveRule(row.ruleId,{reason:value.trim()});await load()}catch(e){if(!['cancel','close'].includes(e))await load()}}
function openAccounting(row){router.push({path:'/finance/accounting',query:{projectId:projectId.value,factId:row.accountingFactId}})}
watch(()=>[route.query.projectId,route.query.awardId,route.query.tab],([value,,tab])=>{if(route.path!=='/hcm/incentives')return;const id=Number(value)||null;if(id!==projectId.value){if(['rules','awards','distribution'].includes(tab))activeTab.value=tab;projectId.value=id;history.value=null;load()}else{if(!data.distributionOnly&&['rules','awards','distribution'].includes(tab))activeTab.value=tab;focusRequestedAward()}})
useBusinessRefreshOnReactivated(load)
onMounted(load)
</script>

<style scoped>
.rule-share-amount{margin:0;color:#60776c;font-size:13px}.rule-share-amount strong{color:#24675a;font-size:16px;margin-left:6px;overflow-wrap:anywhere}
.share-value{display:flex;align-items:baseline;flex-wrap:wrap;gap:8px 16px}.bonus-overview .bonus-overview-amount{font-size:16px;font-weight:600;color:#24675a;overflow-wrap:anywhere}
.award-application-records{margin-top:20px}.award-application-records h3{margin:0 0 12px}
.bonus-overview{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:20px 0 10px}.bonus-overview article{display:flex;flex-direction:column;gap:10px;min-width:0;padding:18px;border:1px solid #dfe7ed;border-radius:10px;background:#f7fafb}.bonus-overview span,.bonus-overview small{color:#728494;font-size:13px;overflow-wrap:anywhere}.bonus-overview strong{font-size:26px;color:#344d60;overflow-wrap:anywhere}.bonus-overview .bonus-overview-profit{background:#eef7f3;border-color:#cfe4dc}.bonus-overview-profit strong{color:#24675a}.bonus-overview-hint{color:#728494;font-size:13px;margin:0 0 16px}@media(max-width:600px){.bonus-overview{grid-template-columns:1fr}.bonus-overview article{padding:14px}.bonus-overview strong{font-size:24px}}
.profit-field{display:flex;align-items:baseline;gap:12px;width:100%;padding:16px 18px;border:1px solid #cfe4dc;border-radius:8px;background:#eef7f3}.profit-field strong{font-size:28px;color:#24675a}.profit-field span{color:#60776c}.rule-share-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px;width:100%}.rule-share-grid>div{display:flex;flex-direction:column;gap:8px}.rule-share-grid label{font-weight:600}.rule-share-grid small{color:#728494}.percent-input{display:flex;align-items:center;gap:10px}.percent-input .el-input-number{width:100%}.el-form-item .muted{width:100%;margin:8px 0 0}.rule-expanded{padding:12px 24px}@media(max-width:600px){.rule-share-grid{grid-template-columns:1fr}.rule-expanded{padding:8px}}
</style>
