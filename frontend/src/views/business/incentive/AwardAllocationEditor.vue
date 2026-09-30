<template>
  <section class="award-allocation-editor">
    <h3>{{ t('memberAllocationTitle') }}</h3>
    <p class="muted">{{ t('applicationAllocationHint') }}</p>
    <p>{{ t('total') }}: <b>{{ money(sourceAmount) }} {{ currency }}</b></p>
    <p class="muted">{{ t('sourceHint') }}</p>
    <el-form-item :label="t('mode')"><el-radio-group v-model="model.mode"><el-radio value="AMOUNT">{{ t('AMOUNT') }}</el-radio><el-radio value="PERCENT">{{ t('PERCENT') }}</el-radio></el-radio-group></el-form-item>
    <p class="muted">{{ t(model.mode === 'PERCENT' ? 'percentHint' : 'allocationNote') }}</p>
    <p v-if="model.mode === 'PERCENT'" :class="proposalPercentTotal(model) === 100 ? 'percent-complete' : 'percent-incomplete'">{{ t('percentTotal') }}: {{ proposalPercentTotal(model) }}% / 100%</p>
    <div v-for="(line, index) in model.lines" :key="index" class="allocation-line">
      <el-select v-model="line.userId" filterable :placeholder="t('person')" :aria-label="t('person')"><el-option v-for="person in recipients" :key="person.userId" :value="person.userId" :label="person.userName" /></el-select>
      <el-input-number v-if="model.mode === 'AMOUNT'" v-model="line.amount" :min="0" :precision="2" :placeholder="t('amount')" :aria-label="t('amount')" controls-position="right" />
      <el-input-number v-else v-model="line.percentage" :min="0" :max="100" :precision="2" :placeholder="t('percentage')" :aria-label="t('percentage')" controls-position="right" />
      <el-input v-model="line.reason" :placeholder="t('reason')" maxlength="500" />
      <el-button type="danger" link :disabled="model.lines.length === 1" @click="model.lines.splice(index, 1)">{{ t('remove') }}</el-button>
      <small v-if="model.mode === 'PERCENT'">{{ money(proposalLineAmount(model, line, sourceAmount)) }} {{ currency }}</small>
    </div>
    <el-button :disabled="model.lines.length >= 200" @click="model.lines.push({ userId: null, amount: null, percentage: null, reason: '' })">{{ t('add') }}</el-button>
    <p><b>{{ t('sum') }}: {{ money(proposalTotal(model, sourceAmount)) }} {{ currency }}</b> · {{ t('capacity') }}: {{ money(sourceAmount) }} {{ currency }}</p>
    <el-form-item :label="t('reason')"><el-input v-model="model.reason" type="textarea" maxlength="500" /></el-form-item>
  </section>
</template>
<script setup>
import { useI18n } from 'vue-i18n'
import messages from './distributionMessages'
import { proposalLineAmount, proposalPercentTotal, proposalTotal } from './allocationProposal'
defineProps({ model: { type: Object, required: true }, recipients: { type: Array, default: () => [] }, sourceAmount: { type: [String, Number], default: null }, currency: { type: String, default: '' } })
const { t } = useI18n({ useScope: 'local', messages })
const money = value => value == null ? '—' : Number(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
</script>
<style scoped>
.award-allocation-editor{border-top:1px solid #e4e9ef;margin-top:20px;padding-top:8px}.allocation-line{display:grid;grid-template-columns:1fr 180px 1.4fr 50px;gap:12px;margin-bottom:12px}.allocation-line .el-input-number{width:100%}.allocation-line small{grid-column:2}.muted{font-size:12px;color:#718096}.percent-complete{color:#24675a}.percent-incomplete{color:#b56a16}@media(max-width:650px){.allocation-line{grid-template-columns:1fr 1fr}}
</style>
