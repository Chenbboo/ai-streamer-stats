<template>
  <el-tooltip placement="top" effect="light" :show-after="150">
    <template #content>
      <div class="internal-income-tooltip">
        <strong>{{ $tr('内部项目收入明细') }}</strong>
        <p v-if="!items.length" class="internal-income-empty">{{ $tr('今日暂无内部项目转入收入') }}</p>
        <template v-else>
          <div v-for="group in totals" :key="group.currency" class="internal-income-total">{{ $tr('内部项目收入：{0} {1}', [money(group.amount), group.currency]) }}</div>
          <article v-for="item in items" :key="`${item.projectId}-${item.factId}`" class="internal-income-item">
            <div class="internal-income-head"><span>{{ showProject ? `${item.projectName} ← ` : '' }}{{ item.sourceProjectName || $tr('来源项目') }}</span><b>{{ money(item.amount) }} {{ item.currency }}</b></div>
            <p>{{ item.description || $tr('内部项目支出') }}</p>
          </article>
        </template>
      </div>
    </template>
    <slot />
  </el-tooltip>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ items: { type: Array, default: () => [] }, showProject: Boolean })
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const totals = computed(() => {
  const groups = new Map()
  for (const item of props.items) {
    const currency = item.currency || 'CNY'
    groups.set(currency, (groups.get(currency) || 0) + Number(item.amount || 0))
  }
  return [...groups].map(([currency, amount]) => ({ currency, amount }))
})
</script>

<style scoped>
.internal-income-tooltip{width:360px;max-width:calc(100vw - 48px);max-height:360px;overflow-y:auto;overflow-wrap:anywhere}
.internal-income-tooltip>strong{display:block;margin-bottom:8px}
.internal-income-empty{margin:0;color:#8a97a5}
.internal-income-total{margin-bottom:7px;color:#24776c;font-weight:600}
.internal-income-item{padding:9px 0;border-top:1px solid #e7edf2}
.internal-income-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}
.internal-income-head span{min-width:0;flex:1}.internal-income-head b{flex:none;white-space:nowrap}
.internal-income-item p{margin:5px 0 0;color:#7a8794;white-space:pre-wrap}
</style>
