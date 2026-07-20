<template>
  <div class="daily-page">
    <header class="page-head">
      <div>
        <div class="eyebrow">STREAMER DAILY REPORT</div>
        <h1>主播数据列表</h1>
        <p>按日期查看主播每日完成情况与月度累计数据</p>
      </div>
      <div class="head-actions">
        <el-radio-group v-model="viewMode" @change="loadData">
          <el-radio-button value="daily">每日明细</el-radio-button>
          <el-radio-button value="range">区间汇总</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="viewMode === 'daily'"
          v-model="selectedMonth"
          type="month"
          value-format="YYYY-MM"
          :clearable="false"
          :disabled-date="disableFutureMonth"
          aria-label="选择月份"
          @change="loadData"
        />
        <el-date-picker
          v-else
          v-model="selectedRange"
          class="range-picker"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="false"
          :disabled-date="disableFutureMonth"
          @change="loadData"
        />
        <el-select v-model="selectedStreamer" clearable filterable placeholder="全部主播" @change="loadData">
          <el-option v-for="item in streamers" :key="item.streamerId" :label="item.stageName" :value="item.streamerId" />
        </el-select>
        <el-button icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
        <el-button :icon="compact ? 'Expand' : 'Fold'" @click="compact = !compact">
          {{ compact ? '舒适显示' : '紧凑显示' }}
        </el-button>
      </div>
    </header>

    <div class="summary-band">
      <span v-if="viewMode === 'daily'"><b>{{ dayGroups.length }}</b> 个日期</span>
      <span v-else><b>{{ rangeDays }}</b> 天区间</span>
      <span><b>{{ streamerCount }}</b> 位主播</span>
      <span><b>5</b> 类指标</span>
      <div class="legend">
        <span><i class="legend-dot good" />已完成</span>
        <span><i class="legend-dot warn" />接近目标</span>
        <span><i class="legend-dot bad" />未完成</span>
        <span><i class="legend-dot care" />需维护</span>
      </div>
    </div>

    <el-empty v-if="!loading && !displayRows.length" description="当前范围暂无数据" />

    <main v-loading="loading" :class="['daily-sections', { compact }]">
      <section v-if="viewMode === 'range' && rangeRows.length" class="day-section range-section">
        <div class="date-title">
          <strong>{{ rangeLabel }}</strong>
          <span>区间汇总</span>
        </div>
        <div class="table-scroll">
          <table class="range-table">
            <thead>
              <tr>
                <th class="range-label">{{ rangeLabel }}</th>
                <th colspan="5" class="group gift">钻石</th>
                <th colspan="4" class="group fans">新增粉丝</th>
                <th colspan="4" class="group chat">新增粉丝互动</th>
                <th colspan="3" class="group maintain">粉丝维护（送礼 ≥ 1000钻）</th>
                <th colspan="2" class="group tip">新增粉丝送礼</th>
              </tr>
              <tr>
                <th class="sticky-col streamer-head">主播</th>
                <th>区间收礼</th><th>月度累计钻石</th><th>区间指标完成</th><th>月度KPI完成率</th><th>预计月底完成</th>
                <th>区间新增粉丝</th><th>月累计新增粉丝</th><th>区间指标完成</th><th>月度KPI完成率</th>
                <th>区间新增互动人数</th><th>月累计新增粉丝互动</th><th>区间指标完成</th><th>月度KPI完成率</th>
                <th>送钻≥1000</th><th>有打赏无互动</th><th>有打赏有互动</th>
                <th>区间新增粉丝送礼</th><th>新增粉丝月度打赏</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rangeRows" :key="row.streamerId">
                <th class="sticky-col streamer-name">{{ row.stageName }}</th>
                <td>{{ number(row.weeklyXu) }}</td>
                <td>{{ number(row.monthlyXu) }}</td>
                <td class="range-metric-cell">{{ rate(rangeRate(row, row.weeklyXu, 'giftDaily', 'giftMonthly')) }}</td>
                <td class="monthly-kpi-cell">{{ rate(monthRate(row, 'kpiMonthXu', 'giftMonthly')) }}</td>
                <td :class="forecastCellClass(forecastRate(row, rangeEndDate))">{{ rate(forecastRate(row, rangeEndDate)) }}</td>

                <td>{{ number(row.newFanWeekly) }}</td>
                <td>{{ number(row.newFanMonthly) }}</td>
                <td class="range-metric-cell">{{ rate(rangeRate(row, row.newFanWeekly, 'newFanDaily', 'newFanMonthly')) }}</td>
                <td class="monthly-kpi-cell">{{ rate(monthRate(row, 'newFanMonthly', 'newFanMonthly')) }}</td>

                <td>{{ number(row.chatWeekly) }}</td>
                <td>{{ number(row.chatMonthly) }}</td>
                <td class="range-metric-cell">{{ rate(rangeRate(row, row.chatWeekly, 'chatDaily', 'chatMonthly')) }}</td>
                <td class="monthly-kpi-cell">{{ rate(monthRate(row, 'chatMonthly', 'chatMonthly')) }}</td>

                <td>{{ number(row.maintenanceTotal, false) }}</td>
                <td>{{ number(row.maintenanceRed, false) }}</td>
                <td>{{ number(row.maintenanceGood, false) }}</td>
                <td>{{ number(row.newTipWeeklyAmount) }}</td>
                <td>{{ number(row.newTipMonthlyAmount) }}</td>
              </tr>
              <tr class="total-row">
                <th class="sticky-col streamer-name">合计</th>
                <td>{{ total(rangeRows, 'weeklyXu') }}</td>
                <td>{{ total(rangeRows, 'monthlyXu') }}</td>
                <td></td><td></td><td></td>
                <td>{{ total(rangeRows, 'newFanWeekly') }}</td>
                <td>{{ total(rangeRows, 'newFanMonthly') }}</td>
                <td></td><td></td>
                <td>{{ total(rangeRows, 'chatWeekly') }}</td>
                <td>{{ total(rangeRows, 'chatMonthly') }}</td>
                <td></td><td></td>
                <td>{{ total(rangeRows, 'maintenanceTotal') }}</td>
                <td>{{ total(rangeRows, 'maintenanceRed') }}</td>
                <td>{{ total(rangeRows, 'maintenanceGood') }}</td>
                <td>{{ total(rangeRows, 'newTipWeeklyAmount') }}</td>
                <td>{{ total(rangeRows, 'newTipMonthlyAmount') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <template v-else>
      <section v-for="group in dayGroups" :key="group.bizDate" class="day-section">
        <div class="date-title">
          <strong>{{ formatDate(group.bizDate) }}</strong>
          <span>{{ weekday(group.bizDate) }}</span>
        </div>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th rowspan="2" class="sticky-col streamer-head">主播</th>
                <th colspan="6" class="group gift">钻石</th>
                <th colspan="5" class="group fans">新增粉丝</th>
                <th colspan="5" class="group chat">新增粉丝互动</th>
                <th colspan="5" class="group maintain">粉丝维护（送礼 ≥ 1000钻）</th>
                <th colspan="2" class="group tip">新增粉丝送礼</th>
              </tr>
              <tr>
                <th>当日收礼</th><th>当日完成率</th><th>当日指标完成</th><th>月度累计钻石</th><th>月度KPI完成</th><th>预计月底完成</th>
                <th>当日新增粉丝</th><th>当日完成率</th><th>当日指标完成</th><th>月度累计新增粉丝</th><th>月度KPI完成</th>
                <th>当日新增互动人数</th><th>当日完成率</th><th>当日指标完成</th><th>月度累计新增互动</th><th>月度KPI完成</th>
                <th>月送钻≥1000</th><th>当月有打赏无互动</th><th>当月有打赏有互动</th><th>当日有打赏无互动</th><th>当日有打赏有互动</th>
                <th>当日新增粉丝送礼</th><th>新增粉丝月度打赏</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in group.rows" :key="row.streamerId">
                <th class="sticky-col streamer-name">{{ row.stageName }}</th>
                <td>{{ number(row.dailyXu) }}</td>
                <td :class="rateClass(dailyRate(row, 'dailyXu', 'giftDaily'))">{{ rate(dailyRate(row, 'dailyXu', 'giftDaily')) }}</td>
                <td :class="targetClass(dailyRate(row, 'dailyXu', 'giftDaily'))">{{ targetMark(dailyRate(row, 'dailyXu', 'giftDaily')) }}</td>
                <td>{{ number(row.monthlyXu) }}</td>
                <td :class="rateClass(monthRate(row, 'kpiMonthXu', 'giftMonthly'))">{{ rate(monthRate(row, 'kpiMonthXu', 'giftMonthly')) }}</td>
                <td :class="rateClass(forecastRate(row, group.bizDate))">{{ rate(forecastRate(row, group.bizDate)) }}</td>

                <td>{{ number(row.newFanDaily) }}</td>
                <td :class="rateClass(dailyRate(row, 'newFanDaily', 'newFanDaily'))">{{ rate(dailyRate(row, 'newFanDaily', 'newFanDaily')) }}</td>
                <td :class="targetClass(dailyRate(row, 'newFanDaily', 'newFanDaily'))">{{ targetMark(dailyRate(row, 'newFanDaily', 'newFanDaily')) }}</td>
                <td>{{ number(row.newFanMonthly) }}</td>
                <td :class="rateClass(monthRate(row, 'newFanMonthly', 'newFanMonthly'))">{{ rate(monthRate(row, 'newFanMonthly', 'newFanMonthly')) }}</td>

                <td>{{ number(row.chatDaily) }}</td>
                <td :class="rateClass(dailyRate(row, 'chatDaily', 'chatDaily'))">{{ rate(dailyRate(row, 'chatDaily', 'chatDaily')) }}</td>
                <td :class="targetClass(dailyRate(row, 'chatDaily', 'chatDaily'))">{{ targetMark(dailyRate(row, 'chatDaily', 'chatDaily')) }}</td>
                <td>{{ number(row.chatMonthly) }}</td>
                <td :class="rateClass(monthRate(row, 'chatMonthly', 'chatMonthly'))">{{ rate(monthRate(row, 'chatMonthly', 'chatMonthly')) }}</td>

                <td>{{ number(row.monthHighValue, false) }}</td>
                <td :class="{ 'cell-care': row.monthNoInteraction > 0 }">{{ number(row.monthNoInteraction, false) }}</td>
                <td>{{ number(row.monthMaintained, false) }}</td>
                <td :class="{ 'cell-care': row.dailyNoInteraction > 0 }">{{ number(row.dailyNoInteraction, false) }}</td>
                <td>{{ number(row.dailyMaintained, false) }}</td>

                <td>{{ number(row.newTipDailyAmount) }}</td>
                <td>{{ number(row.newTipMonthlyAmount) }}</td>
              </tr>
              <tr class="total-row">
                <th class="sticky-col streamer-name">合计</th>
                <td>{{ total(group.rows, 'dailyXu') }}</td>
                <td></td><td></td>
                <td>{{ total(group.rows, 'monthlyXu') }}</td>
                <td></td><td></td>
                <td>{{ total(group.rows, 'newFanDaily') }}</td>
                <td></td><td></td>
                <td>{{ total(group.rows, 'newFanMonthly') }}</td>
                <td></td>
                <td>{{ total(group.rows, 'chatDaily') }}</td>
                <td></td><td></td>
                <td>{{ total(group.rows, 'chatMonthly') }}</td>
                <td></td>
                <td>{{ total(group.rows, 'monthHighValue') }}</td>
                <td>{{ total(group.rows, 'monthNoInteraction') }}</td>
                <td>{{ total(group.rows, 'monthMaintained') }}</td>
                <td>{{ total(group.rows, 'dailyNoInteraction') }}</td>
                <td>{{ total(group.rows, 'dailyMaintained') }}</td>
                <td>{{ total(group.rows, 'newTipDailyAmount') }}</td>
                <td>{{ total(group.rows, 'newTipMonthlyAmount') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      </template>
    </main>
  </div>
</template>

<script setup name="StreamerData">
import { computed, onMounted, ref } from 'vue'
import { streamerCardDetail, streamerDailyList, weijiMonthStats } from '@/api/live/stats'
import { listKpiConfig } from '@/api/live/kpi'
import { listStreamers } from '@/api/live/upload'

const loading = ref(false)
const compact = ref(false)
const viewMode = ref('daily')
const selectedStreamer = ref()
const streamers = ref([])
const dayGroups = ref([])
const rangeRows = ref([])
const kpiMap = ref(new Map())

const yesterday = new Date()
yesterday.setDate(yesterday.getDate() - 1)
const selectedMonth = ref(toMonth(yesterday))
const selectedRange = ref([`${toMonth(yesterday)}-01`, toDate(yesterday)])

const rangeEndDate = computed(() => selectedRange.value?.[1] || toDate(yesterday))
const rangeDays = computed(() => {
  if (!selectedRange.value?.[0] || !selectedRange.value?.[1]) return 0
  return dateDiff(selectedRange.value[0], selectedRange.value[1]) + 1
})
const rangeLabel = computed(() => {
  if (!selectedRange.value?.[0] || !selectedRange.value?.[1]) return ''
  return `${shortDate(selectedRange.value[0])}-${shortDate(selectedRange.value[1])}`
})
const displayRows = computed(() => viewMode.value === 'range' ? rangeRows.value : dayGroups.value)

const streamerCount = computed(() => {
  if (viewMode.value === 'range') return rangeRows.value.length
  const ids = new Set()
  dayGroups.value.forEach(group => group.rows.forEach(row => ids.add(row.streamerId)))
  return ids.size
})

onMounted(async () => {
  const response = await listStreamers()
  streamers.value = response.data || []
  await loadData()
})

async function loadData() {
  if (viewMode.value === 'range') {
    await loadRangeData()
    return
  }
  if (!selectedMonth.value) return
  loading.value = true
  try {
    const [year, month] = selectedMonth.value.split('-').map(Number)
    const beginDate = `${selectedMonth.value}-01`
    const monthEnd = new Date(year, month, 0)
    const end = monthEnd > yesterday ? yesterday : monthEnd
    const endDate = toDate(end)
    const [dailyResponse, kpiResponse] = await Promise.all([
      streamerDailyList(beginDate, endDate, selectedStreamer.value),
      listKpiConfig({ kpiYear: year, kpiMonth: month })
    ])
    dayGroups.value = (dailyResponse.data || []).slice().reverse()
    kpiMap.value = new Map((kpiResponse.rows || []).map(item => [String(item.streamerId), item]))
  } finally {
    loading.value = false
  }
}

async function loadRangeData() {
  if (!selectedRange.value?.[0] || !selectedRange.value?.[1]) return
  loading.value = true
  try {
    const [beginDate, endDate] = selectedRange.value
    const monthBegin = `${endDate.slice(0, 7)}-01`
    const months = monthsBetween(beginDate, endDate)
    const [cardResponse, maintenanceResponse, kpiResponses] = await Promise.all([
      streamerCardDetail(selectedStreamer.value, beginDate, endDate),
      weijiMonthStats(monthBegin, endDate),
      Promise.all(months.map(value => {
        const [year, month] = value.split('-').map(Number)
        return listKpiConfig({ kpiYear: year, kpiMonth: month })
      }))
    ])
    const maintenanceMap = new Map((maintenanceResponse.data || []).map(item => [String(item.streamerId), item]))
    rangeRows.value = (cardResponse.data || []).map(row => {
      const maintenance = maintenanceMap.get(String(row.streamerId)) || {}
      const red = Number(maintenance.red || 0)
      const good = Number(maintenance.green || 0) + Number(maintenance.purple || 0)
      return {
        ...row,
        maintenanceRed: red,
        maintenanceGood: good,
        maintenanceTotal: red + good
      }
    })
    const configs = []
    kpiResponses.forEach((response, index) => {
      const month = months[index]
      ;(response.rows || []).forEach(item => configs.push([`${month}-${item.streamerId}`, item]))
    })
    kpiMap.value = new Map(configs)
  } finally {
    loading.value = false
  }
}

function kpi(row, key) {
  if (viewMode.value === 'range') {
    const month = rangeEndDate.value.slice(0, 7)
    return Number(kpiMap.value.get(`${month}-${row.streamerId}`)?.[key] || 0)
  }
  return Number(kpiMap.value.get(String(row.streamerId))?.[key] || 0)
}

function rangeRate(row, actual, dailyKey, monthlyKey) {
  const target = monthsBetween(selectedRange.value[0], selectedRange.value[1]).reduce((total, month) => {
    const config = kpiMap.value.get(`${month}-${row.streamerId}`)
    if (!config) return total
    const segment = monthSegment(month, selectedRange.value[0], selectedRange.value[1])
    const dailyTarget = Number(config[dailyKey] || 0)
    if (dailyTarget > 0) return total + dailyTarget * segment.days
    const monthlyTarget = Number(config[monthlyKey] || 0)
    return monthlyTarget > 0 ? total + monthlyTarget * segment.days / segment.daysInMonth : total
  }, 0)
  return target > 0 ? Number(actual || 0) / target * 100 : null
}

function dailyRate(row, valueKey, kpiKey) {
  const target = kpi(row, kpiKey)
  return target > 0 ? Number(row[valueKey] || 0) / target * 100 : null
}

function monthRate(row, valueKey, kpiKey) {
  const target = kpi(row, kpiKey)
  return target > 0 ? Number(row[valueKey] || 0) / target * 100 : null
}

function forecastRate(row, date) {
  const target = kpi(row, 'giftMonthly')
  if (!target) return null
  const day = Number(date.slice(-2))
  return Math.min(100, Number(row.monthlyXu || 0) / day * 26 / target * 100)
}

function rate(value) {
  return value === null ? '/' : `${value.toFixed(1)}%`
}

function rateClass(value) {
  if (value === null) return ''
  if (value >= 100) return 'cell-good'
  if (value >= 80) return 'cell-warn'
  return 'cell-bad'
}

function forecastCellClass(value) {
  if (value === null) return ''
  return value >= 100 ? 'cell-good' : 'cell-bad'
}

function targetClass(value) {
  return value !== null && value >= 100 ? 'target-done' : 'target-missed'
}

function targetMark(value) {
  if (value === null) return '/'
  return value >= 100 ? '√' : '×'
}

function number(value, slashZero = true) {
  const n = Number(value || 0)
  if (slashZero && n === 0) return '/'
  return n.toLocaleString('zh-CN')
}

function total(rows, key) {
  return (rows || []).reduce((sum, row) => sum + Number(row[key] || 0), 0).toLocaleString('zh-CN')
}

function formatDate(value) {
  const [, month, day] = value.split('-')
  return `${Number(month)}月${Number(day)}日`
}

function shortDate(value) {
  const [, month, day] = value.split('-')
  return `${Number(month)}/${Number(day)}`
}

function weekday(value) {
  return ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][new Date(`${value}T00:00:00`).getDay()]
}

function disableFutureMonth(date) {
  return date > yesterday
}

function dateDiff(beginDate, endDate) {
  return Math.floor((new Date(`${endDate}T00:00:00`) - new Date(`${beginDate}T00:00:00`)) / 86400000)
}

function monthsBetween(beginDate, endDate) {
  const result = []
  const cursor = new Date(`${beginDate.slice(0, 7)}-01T00:00:00`)
  const end = new Date(`${endDate.slice(0, 7)}-01T00:00:00`)
  while (cursor <= end) {
    result.push(toMonth(cursor))
    cursor.setMonth(cursor.getMonth() + 1)
  }
  return result
}

function monthSegment(month, beginDate, endDate) {
  const [year, monthNumber] = month.split('-').map(Number)
  const monthStart = `${month}-01`
  const monthEnd = toDate(new Date(year, monthNumber, 0))
  const start = beginDate > monthStart ? beginDate : monthStart
  const end = endDate < monthEnd ? endDate : monthEnd
  return { days: dateDiff(start, end) + 1, daysInMonth: Number(monthEnd.slice(-2)) }
}

function toMonth(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function toDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.daily-page { min-height: 100%; padding: 28px 30px 50px; background: #f4f6f8; color: #17212b; }
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 2px 0 22px; }
.eyebrow { color: #6c7783; font-size: 11px; font-weight: 700; letter-spacing: 0; }
h1 { margin: 6px 0 4px; font-size: 28px; }
.page-head p { margin: 0; color: #6d7782; }
.head-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; }
.head-actions :deep(.el-select) { width: 150px; }
.head-actions .range-picker { width: 280px; }
.summary-band { display: flex; align-items: center; gap: 26px; padding: 13px 16px; border-top: 1px solid #d8dde3; border-bottom: 1px solid #d8dde3; background: #fff; color: #606b76; }
.summary-band b { color: #17212b; font-size: 17px; }
.legend { display: flex; gap: 18px; margin-left: auto; font-size: 12px; }
.legend span { display: inline-flex; align-items: center; gap: 6px; }
.legend-dot { width: 9px; height: 9px; border-radius: 2px; }
.legend-dot.good, .cell-good { background: #b8df48; }
.legend-dot.warn, .cell-warn { background: #ffd84d; }
.legend-dot.bad, .cell-bad { background: #ff5b5b; }
.legend-dot.care, .cell-care { background: #b8d0f1; }
.daily-sections { min-height: 220px; }
.day-section { padding: 22px 0 8px; }
.date-title { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
.date-title strong { font-size: 17px; }
.date-title span { color: #7b858f; font-size: 12px; }
.table-scroll { overflow-x: auto; border: 1px solid #2e343a; background: #fff; }
table { width: 100%; min-width: 2140px; border-collapse: separate; border-spacing: 0; table-layout: fixed; font-size: 12px; }
.range-table { min-width: 1780px; }
.range-table .range-label { width: 130px; background: #fff; border-bottom: 2px solid #2e343a; font-size: 14px; }
th, td { height: 38px; padding: 5px 7px; border-right: 1px solid #7b838a; border-bottom: 1px solid #7b838a; text-align: center; vertical-align: middle; }
thead th { background: #c9d7ed; color: #17212b; font-weight: 700; }
thead .group { height: 34px; background: #f7f8fa; border-bottom: 2px solid #2e343a; font-size: 14px; }
thead .gift { border-top: 4px solid #3778be; }
thead .fans { border-top: 4px solid #43a36d; }
thead .chat { border-top: 4px solid #de9944; }
thead .maintain { border-top: 4px solid #8065b6; }
thead .tip { border-top: 4px solid #d65d72; }
tr:last-child td, tr:last-child th { border-bottom: 0; }
th:last-child, td:last-child { border-right: 0; }
.sticky-col { position: sticky; left: 0; z-index: 2; width: 130px; min-width: 130px; }
.streamer-head { z-index: 4; background: #c9d7ed; }
.streamer-name { background: #fff; text-align: left; font-weight: 700; box-shadow: 2px 0 0 #616970; }
.target-done { background: #ffd84d; color: #2a2a2a; font-size: 16px; font-weight: 800; }
.target-missed { background: #ff5b5b; color: #5b1111; font-size: 16px; font-weight: 800; }
td.cell-good, td.cell-warn, td.cell-bad, td.cell-care { font-weight: 700; }
.range-table .range-metric-cell { background: #d9e8f7; font-weight: 700; }
.range-table .monthly-kpi-cell { background: #fff1b8; font-weight: 700; }
.total-row th, .total-row td { border-top: 2px solid #343a40; background: #eef1f4; font-weight: 800; }
.total-row .streamer-name { background: #eef1f4; }
.compact th, .compact td { height: 30px; padding-top: 3px; padding-bottom: 3px; }
@media (max-width: 900px) {
  .daily-page { padding: 18px 12px 36px; }
  .page-head { align-items: flex-start; flex-direction: column; }
  .head-actions { justify-content: flex-start; width: 100%; }
  .summary-band { align-items: flex-start; flex-wrap: wrap; gap: 12px 20px; }
  .legend { width: 100%; margin-left: 0; overflow-x: auto; }
}
</style>
