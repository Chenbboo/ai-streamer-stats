<template>
  <section class="award-rule-details">
    <h3>{{ label('awardRuleDetails') }}</h3>
    <el-alert :title="label('awardRuleSnapshotHint')" type="info" :closable="false" show-icon />
    <el-descriptions :column="1" border>
      <el-descriptions-item v-if="award.settlementMonth" :label="label('settlementMonth')">{{ award.settlementMonth }} · {{ label('monthlySettlement') }}</el-descriptions-item>
      <el-descriptions-item :label="label('rule')">{{ award.ruleName }} · v{{ award.ruleVersion }}</el-descriptions-item>
      <template v-if="award.policyVersion === 'PROFIT_SHARE_V1'">
        <el-descriptions-item :label="label(award.settlementMonth ? 'afterTaxProfit' : 'legacyAfterTaxProfit')">{{ formatAmount(award.ruleAfterTaxProfit) }} {{ award.currency }}</el-descriptions-item>
        <el-descriptions-item :label="label('mainOwnerShare')">{{ rate(award.ruleMainOwnerBonusRate) }}</el-descriptions-item>
        <el-descriptions-item :label="label('mainOwnerAllocationAmount')">{{ formatAmount(award.ruleMainOwnerBonusAmount) }} {{ award.currency }}</el-descriptions-item>
        <el-descriptions-item :label="label('sponsorOwnerShare')">{{ rate(award.ruleSponsorOwnerBonusRate) }}</el-descriptions-item>
        <el-descriptions-item :label="label('sponsorOwnerAllocationAmount')">{{ formatAmount(award.ruleSponsorOwnerBonusAmount) }} {{ award.currency }}</el-descriptions-item>
      </template>
      <el-descriptions-item :label="label('reason')"><span class="rule-reason">{{ award.ruleReason || '—' }}</span></el-descriptions-item>
    </el-descriptions>
    <el-alert v-if="award.policyVersion !== 'PROFIT_SHARE_V1'" :title="label('legacyRuleDetailsHint')" type="info" :closable="false" />
  </section>
</template>

<script setup>
defineProps({ award: { type: Object, required: true }, label: { type: Function, required: true }, formatAmount: { type: Function, required: true } })
const rate = value => value == null ? '—' : `${Number(value)}%`
</script>

<style scoped>
.award-rule-details{margin-bottom:24px}.award-rule-details h3{margin:0 0 12px;font-size:17px}.award-rule-details .el-alert{margin-bottom:12px}.rule-reason{white-space:pre-wrap;overflow-wrap:anywhere}
</style>
