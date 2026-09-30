<template>
  <template v-if="!data.personal">
    <el-empty v-if="!data.awards?.length" :description="t('noAwards')" />
    <el-table v-else :data="data.awards">
      <el-table-column :label="t('award')" min-width="200"><template #default="{row}">{{ row.ruleName }} #{{ row.awardId }}</template></el-table-column>
      <el-table-column v-for="key in ['remaining','reserved']" :key="key" :label="t(key)" min-width="145"><template #default="{row}">{{ money(row[key]) }} {{ row.currency }}</template></el-table-column>
      <el-table-column :label="t('allocatedMembers')" min-width="150"><template #default="{row}"><div v-for="member in members(row.awardId)" :key="member.key" class="member-line">{{ member.userName }} <span class="muted">({{ t(member.allocationStatus) }})</span></div><span v-if="!members(row.awardId).length">—</span></template></el-table-column>
      <el-table-column :label="t('memberBonusRate')" min-width="125"><template #default="{row}"><div v-for="member in members(row.awardId)" :key="member.key" class="member-line">{{ member.percentage == null ? '—' : `${member.percentage}%` }}</div><span v-if="!members(row.awardId).length">—</span></template></el-table-column>
      <el-table-column :label="t('memberBonusAmount')" min-width="165"><template #default="{row}"><div v-for="member in members(row.awardId)" :key="member.key" class="member-line">{{ money(member.amount) }} {{ member.currency }}</div><span v-if="!members(row.awardId).length">—</span></template></el-table-column>
    </el-table>
    <p class="muted">{{ t('reservedHint') }}</p>
  </template>
</template>
<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import messages from './distributionMessages'
import { allocatedMembersBySource } from './memberAllocationRows'
const props = defineProps({ data: { type: Object, default: () => ({}) } })
const { t } = useI18n({ useScope: 'local', messages })
const money = v => Number(v || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const allocatedMembers = computed(() => allocatedMembersBySource(props.data))
const members = awardId => allocatedMembers.value.get(String(awardId)) || []
</script>
<style scoped>
.muted{font-size:12px;color:#718096}.member-line{min-height:26px;white-space:nowrap}
</style>
