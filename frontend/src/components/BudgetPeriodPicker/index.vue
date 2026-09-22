<template>
  <div class="budget-period-picker">
  <el-config-provider :locale="budgetLocale">
  <el-date-picker
    v-if="cycle === 'WEEK'"
    :model-value="anchorDate"
    type="week"
    :editable="false"
    :format="weekFormat"
    value-format="YYYY-MM-DD"
    style="width: 100%"
    :placeholder="$tr(&quot;选择周&quot;)"
    @update:model-value="selectWeek"
  />
  <el-date-picker
    v-else-if="cycle === 'YEAR'"
    :model-value="anchorDate"
    type="year"
    :format="$tr(&quot;YYYY 年&quot;)"
    value-format="YYYY-01-01"
    style="width: 100%"
    :placeholder="$tr(&quot;选择年度&quot;)"
    @update:model-value="value => emit('update:anchorDate', value)"
  />
  <el-date-picker
    v-else
    :model-value="anchorDate"
    type="month"
    :format="$tr(&quot;YYYY 年 MM 月&quot;)"
    value-format="YYYY-MM-01"
    style="width: 100%"
    :placeholder="cycle === 'QUARTER' ? $tr(&quot;选择起始月份&quot;) : $tr(&quot;选择月份&quot;)"
    @update:model-value="value => emit('update:anchorDate', value)"
  />
  </el-config-provider>
    <small v-if="cycle === 'YEAR'" class="period-help">{{ annualRange || $tr("选择年度，自动包含该年 1 月至 12 月") }}</small>
    <small v-else-if="cycle === 'QUARTER'" class="period-help">{{ quarterRange || (compact ? $tr("从所选月份起连续三个月") : $tr("选择起始月份，自动包含连续三个月（可跨年）")) }}</small>
    <small v-else-if="cycle === 'WEEK' && weekStart" class="period-help">{{ $tr("{0} 至 {1}（周一至周日）", [dateText(weekStart), dateText(weekStart.add(6, 'day'))]) }}</small>
  </div>
</template>

<script setup>
import { translateText } from '@/locales/translate'

import { computed } from 'vue'
import { dayjs } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

// Match the backend's Monday–Sunday periods and ISO week numbers locally.
dayjs.locale({ ...dayjs.Ls.en, name: 'budget-iso', weekStart: 1, yearStart: 4 }, null, true)
const budgetLocale = { ...zhCn, name: 'budget-iso' }

const props = defineProps({
  cycle: { type: String, required: true },
  anchorDate: { type: String, default: null },
  compact: Boolean
})
const emit = defineEmits(['update:anchorDate'])

const dateText = date => date.format('YYYY-MM-DD')
const weekStart = computed(() => props.anchorDate ? dayjs(props.anchorDate).locale('budget-iso').startOf('week') : null)
const weekFormat = computed(() => {
  if (!weekStart.value) return translateText("YYYY 第 ww 周")
  const start = weekStart.value, end = start.add(6, 'day')
  const year = start.add(3, 'day').year()
  return translateText("[{0} 第 {1} 周（{2} 至 {3}）]", [year, String(start.week()).padStart(2, '0'), start.format('MM-DD'), end.format('MM-DD')])
})
const quarterRange = computed(() => {
  if (!props.anchorDate) return ''
  const start = dayjs(props.anchorDate).startOf('month')
  return translateText("{0} 至 {1}（连续三个月）", [start.format(translateText("YYYY年M月")), start.add(2, 'month').format(translateText("YYYY年M月"))])
})
const annualRange = computed(() => {
  if (!props.anchorDate) return ''
  const year = dayjs(props.anchorDate).year()
  return translateText("{0}年1月 至 {1}年12月（自然年）", [year, year])
})
function selectWeek(value) {
  emit('update:anchorDate', value ? dateText(dayjs(value).locale('budget-iso').startOf('week')) : null)
}
</script>

<style scoped>
.budget-period-picker{width:100%;min-width:0}
.period-help{display:block;color:var(--el-text-color-secondary);line-height:1.6;margin-top:5px;overflow-wrap:anywhere}
</style>
