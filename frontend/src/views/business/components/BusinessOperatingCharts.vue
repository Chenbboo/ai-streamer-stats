<template>
  <section class="operating-charts" :aria-busy="loading || busy">
    <div class="chart-controls">
      <span class="chart-period">{{ displayRange }}<span v-if="currency"> · {{ currency }}</span></span>
      <div class="chart-filters">
        <el-radio-group v-model="monthMode" size="small" :aria-label="t('bossCharts.monthMode')">
          <el-radio-button value="single">{{ t('bossCharts.singleMonth') }}</el-radio-button>
          <el-radio-button value="range">{{ t('bossCharts.multipleMonths') }}</el-radio-button>
        </el-radio-group>
        <el-date-picker v-if="monthMode === 'single'" v-model="month" type="month" value-format="YYYY-MM"
          :format="t('bossCharts.monthFormat')" :clearable="false" :disabled-date="futureMonth"
          :aria-label="t('bossCharts.month')" :placeholder="t('bossCharts.month')" size="small" />
        <el-date-picker v-else v-model="monthRange" type="monthrange" value-format="YYYY-MM"
          :format="t('bossCharts.monthFormat')" :clearable="false" :disabled-date="futureMonth"
          :aria-label="t('bossCharts.multipleMonths')" :start-placeholder="t('bossCharts.startMonth')"
          :end-placeholder="t('bossCharts.endMonth')" range-separator="—" size="small" />
        <el-select v-if="currencies.length > 1" v-model="currency" class="currency-select" size="small" :aria-label="t('bossCharts.currency')">
          <el-option v-for="item in currencies" :key="item" :label="item" :value="item" />
        </el-select>
      </div>
    </div>
    <div class="chart-grid">
      <article class="chart-card">
        <header><h2>{{ t('bossCharts.trend') }}</h2><span>{{ t('bossCharts.recognized') }}</span></header>
        <div v-show="hasData" ref="trendElement" class="trend-canvas" role="img" :aria-label="t('bossCharts.trendDescription')" />
        <div v-if="!hasData" class="chart-empty" :role="failed ? 'alert' : 'status'">
          <span>{{ stateText }}</span><el-button v-if="error && ready" link type="primary" @click="load">{{ t('bossReview.retry') }}</el-button>
        </div>
        <div v-if="hasData" class="trend-summary">
          <span>{{ t('bossCharts.revenue') }}<b>{{ format(trendTotal('revenueAmount')) }}</b></span>
          <span>{{ t('bossCharts.cost') }}<b>{{ format(model.netTotal) }}</b></span>
          <span>{{ t('bossCharts.profit') }}<b :class="{ loss: trendTotal('profitAmount') < 0 }">{{ format(trendTotal('profitAmount')) }}</b></span>
        </div>
      </article>
      <article v-for="chart in shareCharts" :key="chart.kind" class="chart-card cost-card">
        <header><h2>{{ t(chart.title) }}</h2><span>{{ t('bossCharts.projectCount', { count: chart.model.projects.length }) }}</span></header>
        <div v-show="hasData" class="cost-visual">
          <div v-show="chart.model.slices.length" :ref="element => shareElements[chart.kind] = element" class="cost-canvas" role="img" :aria-label="t(chart.title)" />
          <div v-if="!chart.model.slices.length" class="no-positive">{{ t(chart.noPositive) }}</div>
          <div class="donut-total"><span>{{ t(chart.total) }}</span><strong>{{ format(chart.model.netTotal) }}</strong><small>{{ currency }}</small></div>
        </div>
        <div v-if="!hasData" class="chart-empty" :role="failed ? 'alert' : 'status'">{{ stateText }}</div>
        <div v-if="hasData" class="cost-list">
          <div v-for="(slice, index) in chart.model.slices" :key="slice.other ? 'others' : slice.projectId" class="cost-row">
            <button type="button" @click="selectSlice(slice, chart.kind)">
              <i :style="{ background: colors[index] }" /><span>{{ slice.other ? t('bossCharts.others', { count: slice.members.length }) : slice.projectName }}</span>
              <b>{{ format(slice.value) }}</b><small>{{ share(slice.value, chart.model) }}</small><span class="arrow">{{ slice.other ? (expanded[chart.kind] ? '⌃' : '⌄') : '›' }}</span>
            </button>
            <div v-if="slice.other && expanded[chart.kind]" class="other-projects">
              <button v-for="row in slice.members" :key="row.projectId" type="button" @click="openProject(row)"><span>{{ row.projectName }}</span><b>{{ format(row[chart.amountKey]) }}</b><small>{{ share(row[chart.amountKey], chart.model) }}</small></button>
            </div>
          </div>
          <div v-if="chart.model.negative.length" class="negative-costs">
            <p>{{ t(chart.reversals) }}</p>
            <button v-for="row in chart.model.negative" :key="row.projectId" type="button" @click="openProject(row)"><span>{{ row.projectName }}</span><b>{{ format(row[chart.amountKey]) }}</b></button>
            <small>{{ t(chart.denominator) }}</small>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getBusinessBossCharts } from '@/api/business/accounting'
import { buildBossChartData, chartCurrencies } from '@/utils/bossCharts'

echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])
const props = defineProps({ bizDate: String, companyDeptId: [Number, String], ready: Boolean, busy: Boolean, upstreamError: Boolean })
const emit = defineEmits(['details', 'range-change', 'selection'])
const { t, locale } = useI18n()
const month = ref(''), currency = ref(''), data = ref({}), loading = ref(false), error = ref(false), expanded = ref({})
const monthMode = ref('single'), monthRange = ref([])
const selectedMonths = computed(() => monthMode.value === 'range' ? monthRange.value : [month.value, month.value])
const trendElement = ref()
const shareElements = {}, shareInstances = {}
let trendChart, observer, sequence = 0, disposed = false
const colors = ['#3289dc', '#f3a354', '#48b992', '#a08ad7', '#ee819f', '#9baec2']
const currencies = computed(() => chartCurrencies(data.value))
const model = computed(() => buildBossChartData(props.ready && !loading.value && !error.value ? data.value : {}, currency.value))
const hasData = computed(() => props.ready && !loading.value && !error.value && model.value.trend.some(Boolean))
const failed = computed(() => props.upstreamError || error.value)
const stateText = computed(() => props.busy || loading.value ? t('bossReview.loading') : failed.value ? t('bossReview.failed') : !props.ready ? t('bossCharts.selectCompany') : t('bossCharts.noData'))
const displayRange = computed(() => props.ready && data.value.dateFrom ? `${data.value.dateFrom} — ${data.value.dateTo}` : '—')
const format = value => Number(value).toLocaleString(locale.value, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const share = (value, shareModel) => `${(value / shareModel.positiveTotal * 100).toFixed(1)}%`
const shareCharts = computed(() => [
  { kind: 'cost', model: model.value, amountKey: 'costAmount', title: 'bossCharts.costShare', total: 'bossCharts.totalCost', noPositive: 'bossCharts.noPositive', reversals: 'bossCharts.reversals', denominator: 'bossCharts.positiveDenominator' },
  { kind: 'revenue', model: model.value.revenue, amountKey: 'revenueAmount', title: 'bossCharts.revenueShare', total: 'bossCharts.totalRevenue', noPositive: 'bossCharts.noPositiveRevenue', reversals: 'bossCharts.revenueReversals', denominator: 'bossCharts.revenueDenominator' }
])
const trendTotal = key => model.value.trend.reduce((sum, row) => sum + Number(row?.[key] || 0), 0)
function openProject(row) { emit('selection',row.projectId); emit('details', { projectId: row.projectId, dateFrom: data.value.dateFrom, dateTo: data.value.dateTo, companyDeptId: props.companyDeptId }) }
function selectSlice(slice, kind) { if (slice.other) expanded.value[kind] = !expanded.value[kind]; else openProject(slice) }
function futureMonth(date) {
  const currentMonth = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit' }).formatToParts(new Date())
  const year = Number(currentMonth.find(part => part.type === 'year').value)
  const number = Number(currentMonth.find(part => part.type === 'month').value)
  return date.getFullYear() * 12 + date.getMonth() > year * 12 + number - 1
}

async function load() {
  const id = ++sequence
  data.value = {}; error.value = false; expanded.value = {}
  if (!props.ready || !selectedMonths.value?.[0] || !selectedMonths.value?.[1] || props.companyDeptId == null) { loading.value = false; return }
  loading.value = true
  try {
    const result = await getBusinessBossCharts({ monthFrom: selectedMonths.value[0], monthTo: selectedMonths.value[1], companyDeptId: props.companyDeptId })
    if (id !== sequence || disposed) return
    data.value = result.data || {}
    if (!currencies.value.includes(currency.value)) currency.value = currencies.value[0] || ''
  } catch { if (id === sequence && !disposed) error.value = true }
  finally { if (id === sequence && !disposed) loading.value = false }
}
function resize() { trendChart?.resize(); Object.values(shareInstances).forEach(chart => chart.resize()) }
async function render() {
  await nextTick()
  if (disposed || !hasData.value) return
  const labels = [t('bossCharts.revenue'), t('bossCharts.cost'), t('bossCharts.profit')]
  trendChart ||= echarts.init(trendElement.value)
  trendChart.setOption({
    color: ['#3289dc', '#f3a354', '#48b992'], animationDuration: 350,
    tooltip: { trigger: 'axis', renderMode: 'richText', valueFormatter: value => value == null ? t('bossCharts.noData') : `${format(value)} ${currency.value}` },
    legend: { data: labels, top: 8, icon: 'roundRect', textStyle: { color: '#687b90' } },
    grid: { left: 12, right: 18, top: 58, bottom: 16, containLabel: true },
    xAxis: { type: 'category', data: model.value.buckets.map(date => data.value.monthly ? date : date.slice(5)), axisTick: { show: false }, axisLine: { lineStyle: { color: '#dce5ef' } }, axisLabel: { color: '#77899d', hideOverlap: true } },
    yAxis: { type: 'value', axisLabel: { color: '#77899d', formatter: value => new Intl.NumberFormat(locale.value, { notation: 'compact', maximumFractionDigits: 1 }).format(value) }, splitLine: { lineStyle: { color: '#edf2f7' } } },
    series: ['revenueAmount', 'costAmount', 'profitAmount'].map((key, index) => ({
      name: labels[index], type: index === 2 ? 'line' : 'bar', barMaxWidth: 18,
      data: model.value.trend.map(row => row ? Number(row[key]) : null),
      connectNulls: false, symbolSize: 5, showSymbol: model.value.buckets.length <= 7,
      itemStyle: { borderRadius: index === 2 ? 0 : [3, 3, 0, 0] }, lineStyle: { width: 2.5 }
    }))
  }, true)
  for (const chart of shareCharts.value) {
    if (!chart.model.slices.length || !shareElements[chart.kind]) continue
    const instance = shareInstances[chart.kind] ||= echarts.init(shareElements[chart.kind])
    instance.setOption({ color: colors, animationDuration: 350,
      tooltip: { trigger: 'item', renderMode: 'richText', formatter: item => `${item.name}\n${format(item.value)} ${currency.value} · ${share(item.value, chart.model)}` },
      series: [{ type: 'pie', radius: ['64%', '86%'], center: ['50%', '50%'], label: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 3 }, emphasis: { scaleSize: 4 },
        data: chart.model.slices.map(slice => ({ name: slice.other ? t('bossCharts.others', { count: slice.members.length }) : slice.projectName, value: slice.value })) }]
    }, true)
    instance.off('click')
    instance.on('click', item => selectSlice(chart.model.slices[item.dataIndex], chart.kind))
  }
  resize()
}
watch(() => props.bizDate, (date, previous) => {
  if (date && (!month.value || date.slice(0, 7) !== previous?.slice(0, 7))) {
    month.value = date.slice(0, 7)
    monthRange.value = [month.value, month.value]
  }
}, { immediate: true })
watch(month, value => { if (monthMode.value === 'single') monthRange.value = [value, value] })
watch(() => [props.companyDeptId, props.ready, ...(selectedMonths.value || [])], load, { immediate: true })
watch([model, locale], render)
watch(() => [data.value.dateFrom, data.value.dateTo, currency.value], ([dateFrom,dateTo,currency]) => emit('range-change', {dateFrom,dateTo,currency}))
onMounted(() => { observer = new ResizeObserver(resize); observer.observe(trendElement.value); render() })
onActivated(() => nextTick(resize))
onBeforeUnmount(() => { disposed = true; sequence++; observer?.disconnect(); trendChart?.dispose(); Object.values(shareInstances).forEach(chart => chart.dispose()) })
</script>

<style scoped>
.operating-charts {
  margin: 0 0 26px;
  color: #223c56
}

.chart-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap
}

.chart-period {
  font-size: 12px;
  color: #7d8da0
}

.chart-filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center
}

.currency-select {
  width: 90px
}

.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) repeat(2, minmax(0, 1fr));
  gap: 20px;
  align-items: stretch
}

.chart-card {
  background: #fff;
  border: 1px solid #e7edf4;
  border-radius: 16px;
  padding: 22px;
  min-width: 0;
  overflow: hidden
}

.chart-card header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center
}

.chart-card h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 700
}

.chart-card header>span {
  color: #8a9aad;
  font-size: 12px
}

.trend-canvas {
  height: 310px;
  width: 100%;
  margin-top: 10px
}

.chart-empty {
  min-height: 354px;
  display: flex;
  gap: 10px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8a9aad;
  font-size: 13px
}

.trend-summary {
  border-top: 1px solid #edf2f7;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding-top: 17px
}

.trend-summary>span {
  font-size: 12px;
  color: #8293a6
}

.trend-summary b {
  display: block;
  margin-top: 6px;
  color: #223c56;
  font-size: 17px;
  word-break: break-word
}

.trend-summary b.loss {
  color: #db656a
}

.cost-visual {
  width: 100%;
  flex-shrink: 0;
  height: 234px;
  position: relative;
  margin: 8px auto 0;
  max-width: 370px
}

.cost-canvas {
  height: 100%;
  width: 100%;
  position: relative;
  z-index: 1
}

.donut-total {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  pointer-events: none
}

.donut-total span,
.donut-total small {
  font-size: 12px;
  color: #8a9aad
}

.donut-total strong {
  font-size: 22px;
  color: #223c56;
  max-width: 60%;
  overflow-wrap: anywhere;
  text-align: center
}

.no-positive {
  position: absolute;
  inset: 0;
  border: 13px solid #f1f5f9;
  border-radius: 50%;
  width: 205px;
  height: 205px;
  margin: auto;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 28px;
  font-size: 11px;
  color: #8a9aad
}

.cost-list {
  font-size: 12px
}

.cost-row button,
.negative-costs button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  padding: 7px 0;
  cursor: pointer;
  text-align: left;
  color: inherit;
  font: inherit
}

.cost-row button:hover,
.negative-costs button:hover {
  background: #f7faff
}

.cost-row i {
  height: 8px;
  width: 8px;
  flex-shrink: 0;
  border-radius: 50%
}

.cost-row button>span:not(.arrow),
.negative-costs button>span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap
}

.cost-row b,
.negative-costs b {
  font-variant-numeric: tabular-nums;
  font-weight: 600
}

.cost-row small {
  width: 48px;
  text-align: right;
  color: #8999aa
}

.arrow {
  color: #8999aa;
  width: 12px;
  text-align: center
}

.other-projects {
  padding-left: 16px;
  border-left: 2px solid #edf2f7;
  margin: 4px 0 8px
}

.negative-costs {
  border-top: 1px solid #edf2f7;
  margin-top: 10px;
  padding-top: 4px
}

.negative-costs p {
  color: #bf6965;
  margin: 8px 0 2px
}

.negative-costs>small {
  color: #8a9aad;
  font-size: 11px
}

.cost-card {
  display: flex;
  flex-direction: column
}

.cost-card .chart-empty {
  flex: 1
}

@media(min-width:1001px) and (max-width:1400px) {
  .chart-grid { grid-template-columns: repeat(2, minmax(0, 1fr)) }
  .chart-grid > .chart-card:first-child { grid-column: 1 / -1 }
}

@media(max-width:1000px) {
  .chart-grid {
    grid-template-columns: 1fr
  }

  .chart-card {
    padding: 20px
  }

  .cost-visual {
    width: 100%
  }

  .cost-list {
    max-width: 600px;
    width: 100%;
    margin: auto
  }
}

@media(max-width:600px) {
  .chart-card {
    padding: 18px 14px
  }

  .chart-card header {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px
  }

  .chart-filters {
    width: 100%;
    justify-content: space-between
  }

  .trend-canvas {
    height: 280px
  }

  .trend-summary {
    gap: 8px
  }

  .trend-summary b {
    font-size: 14px
  }

  .donut-total strong {
    font-size: 20px
  }
}
</style>
