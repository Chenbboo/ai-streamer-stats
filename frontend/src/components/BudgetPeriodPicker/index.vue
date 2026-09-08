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
    placeholder="选择周"
    @update:model-value="selectWeek"
  />
  <el-date-picker
    v-else
    :model-value="anchorDate"
    type="month"
    format="YYYY 年 MM 月"
    value-format="YYYY-MM-01"
    style="width: 100%"
    :placeholder="cycle === 'QUARTER' ? '选择起始月份' : '选择月份'"
    @update:model-value="value => emit('update:anchorDate', value)"
  />
  </el-config-provider>
    <small v-if="cycle === 'QUARTER'" class="period-help">{{ quarterRange || '选择起始月份，自动包含连续三个月（可跨年）' }}</small>
    <small v-else-if="cycle === 'WEEK' && weekStart" class="period-help">{{ dateText(weekStart) }} 至 {{ dateText(weekStart.add(6, 'day')) }}（周一至周日）</small>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { dayjs } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

// Match the backend's Monday–Sunday periods and ISO week numbers locally.
dayjs.locale({ ...dayjs.Ls.en, name: 'budget-iso', weekStart: 1, yearStart: 4 }, null, true)
const budgetLocale = { ...zhCn, name: 'budget-iso' }

const props = defineProps({
  cycle: { type: String, required: true },
  anchorDate: { type: String, default: null }
})
const emit = defineEmits(['update:anchorDate'])

const dateText = date => date.format('YYYY-MM-DD')
const weekStart = computed(() => props.anchorDate ? dayjs(props.anchorDate).locale('budget-iso').startOf('week') : null)
const weekFormat = computed(() => {
  if (!weekStart.value) return 'YYYY 第 ww 周'
  const start = weekStart.value, end = start.add(6, 'day')
  const year = start.add(3, 'day').year()
  return `[${year} 第 ${String(start.week()).padStart(2, '0')} 周（${start.format('MM-DD')} 至 ${end.format('MM-DD')}）]`
})
const quarterRange = computed(() => {
  if (!props.anchorDate) return ''
  const start = dayjs(props.anchorDate).startOf('month')
  return `${start.format('YYYY年M月')} 至 ${start.add(2, 'month').format('YYYY年M月')}（连续三个月）`
})
function selectWeek(value) {
  emit('update:anchorDate', value ? dateText(dayjs(value).locale('budget-iso').startOf('week')) : null)
}
</script>

<style scoped>
.budget-period-picker{width:100%;min-width:0}
.period-help{display:block;color:var(--el-text-color-secondary);line-height:1.6;margin-top:5px;overflow-wrap:anywhere}
</style>
