<template>
  <section v-if="allocation" class="application-allocation-details">
    <h3>{{ t('applicationAllocationTitle') }}</h3>
    <p class="muted">{{ t('applicationAllocationSnapshotHint') }}</p>
    <p>{{ t('mode') }}: {{ t(allocation.mode) }} · {{ t('sum') }}: {{ money(allocation.amount) }} {{ currency }}</p>
    <p v-if="allocation.mode === 'PERCENT'" :class="proposalPercentTotal(allocation) === 100 ? 'percent-complete' : 'percent-incomplete'">{{ t('percentTotal') }}: {{ proposalPercentTotal(allocation) }}% / 100%</p>
    <el-table :data="allocation.lines || []">
      <el-table-column prop="userName" :label="t('person')" min-width="100" />
      <el-table-column v-if="allocation.mode === 'PERCENT'" :label="t('percentage')"><template #default="{ row }">{{ row.percentage }}%</template></el-table-column>
      <el-table-column :label="t('amount')" min-width="150"><template #default="{ row }">{{ money(row.amount) }} {{ currency }}</template></el-table-column>
      <el-table-column prop="reason" :label="t('reason')" min-width="130" />
    </el-table>
    <p class="reason">{{ t('reason') }}: {{ allocation.reason }}</p>
  </section>
</template>
<script setup>
import { useI18n } from 'vue-i18n'
import messages from './distributionMessages'
import { proposalPercentTotal } from './allocationProposal'
defineProps({ allocation: { type: Object, default: null }, currency: { type: String, default: '' } })
const { t } = useI18n({ useScope: 'local', messages })
const money = value => Number(value || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
</script>
<style scoped>
.application-allocation-details{margin:24px 0}.muted{font-size:12px;color:#718096}.reason{white-space:pre-wrap;overflow-wrap:anywhere}.percent-complete{color:#24675a}.percent-incomplete{color:#b56a16}
</style>
