<template>
  <article class="previous-month-profit" :class="tone">
    <span>{{ label('项目上月结算税后盈利结果') }}</span>
    <b>{{ failed || !result?.available ? '—' : formatAmount(result.afterTaxProfit) }}</b>
    <small v-if="result?.month">{{ result.month }} · {{ result.currency || currency }}</small>
    <small v-if="failed">{{ label('上月盈利结果暂时无法读取，请刷新重试') }}</small>
    <small v-else-if="!result?.available">{{ label('上月暂无已核算税后盈利结果') }}</small>
    <template v-else>
      <small>{{ label('按上月已核算经营结果汇总，含税额冲回及已核准调整') }}</small>
      <small v-if="result.pendingCostCount > 0">{{ label('上月成本待完善，当前仅显示已核算部分') }}</small>
      <small v-if="result.taxConfigured === false">{{ label('税率未设置，暂按0%') }}</small>
    </template>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  result: { type: Object, default: null },
  failed: { type: Boolean, default: false },
  currency: { type: String, default: 'CNY' },
  label: { type: Function, required: true },
  formatAmount: { type: Function, required: true }
})
const tone = computed(() => {
  if (props.failed || !props.result?.available) return ''
  if (props.result.pendingCostCount > 0) return 'is-warning'
  const amount = Number(props.result.afterTaxProfit)
  return amount < 0 ? 'is-danger' : amount > 0 ? 'is-success' : ''
})
</script>

<style scoped>
.previous-month-profit span,.previous-month-profit b,.previous-month-profit small{display:block}
.previous-month-profit span{color:#7e8b9a;font-size:12px}
.previous-month-profit b{margin:7px 0 5px;color:#21364e;font-size:21px}
.previous-month-profit small{color:#8a95a2;line-height:1.5}
</style>
