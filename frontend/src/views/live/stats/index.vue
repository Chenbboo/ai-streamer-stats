<template>
  <div class="report-page">
    <!-- Header -->
    <header class="report-header">
      <div class="eyebrow">Weekly Report · {{ $t('stats.weeklyReport') }}</div>
      <h1>{{ $t('stats.weekNumber', { n: currentWeek }) }}</h1>
      <div class="period">{{ $t('stats.dataAsOf', { date: today }) }}</div>
      <div class="meta-row">
        <span class="chip">{{ $t('stats.streamerCount', { n: streamers.length }) }}</span>
        <span class="chip">{{ $t('stats.weeklyDiamond', { n: fmt(overview.totalXu) }) }}</span>
        <span class="chip">{{ $t('stats.monthlyDiamond', { n: fmt(monthlyTotal) }) }}</span>
      </div>
    </header>

    <main>
      <!-- 01 主播卡片 -->
      <section>
        <div class="sec-title">{{ $t('stats.section01') }}</div>
        <div class="streamer-row">
          <div class="sc" v-for="card in cardDetails" :key="card.streamerId" :style="'--c:' + getStreamerColor(card.streamerId)">
            <div class="forecast-badge" :class="getForecastClass(card)">{{ $t('stats.monthEndForecast', { n: getForecastPct(card) }) }}</div>
            <div class="sc-name">{{ card.stageName }}</div>
            <div class="sc-diamond">{{ fmt(card.weeklyXu) }}</div>
            <div class="sc-wow" :class="getWowClass(card)">{{ getWowText(card) }}</div>
            <hr class="sc-divider">
            <!-- 送礼 -->
            <div class="sc-section">
              <div class="sc-section-title">{{ $t('stats.gift') }}</div>
              <div class="sc-fields sc-fields-5">
                <div><div class="sc-stat-label">{{ $t('stats.daily') }}</div><div class="sc-stat-val">{{ fmt(card.dailyXu) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.dailyKPI') }}</div><div class="sc-stat-val">{{ getDailyKpiPct(card.dailyXu, getStreamerKpi(card.streamerId, 'giftDaily')) }}%</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.monthly') }}</div><div class="sc-stat-val">{{ fmt(card.monthlyXu) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.growth') }}</div><div class="sc-stat-val" :class="getWowClass(card)">{{ getWowPct(card) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.kpiComplete') }}</div><div class="sc-stat-val">{{ getMonthlyKpiPct(card.monthlyXu, getStreamerKpi(card.streamerId, 'giftMonthly')) }}%</div></div>
              </div>
            </div>
            <!-- 新增粉丝 -->
            <div class="sc-section">
              <div class="sc-section-title">{{ $t('stats.newFans') }}</div>
              <div class="sc-fields sc-fields-5">
                <div><div class="sc-stat-label">{{ $t('stats.daily') }}</div><div class="sc-stat-val">{{ fmt(card.newFanDaily) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.dailyKPI') }}</div><div class="sc-stat-val">{{ getDailyKpiPct(card.newFanDaily, getStreamerKpi(card.streamerId, 'newFanDaily')) }}%</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.monthly') }}</div><div class="sc-stat-val">{{ fmt(card.newFanMonthly) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.growth') }}</div><div class="sc-stat-val">{{ getGrowthRate(card.newFanWeekly, card.newFanLastWeek) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.kpiComplete') }}</div><div class="sc-stat-val">{{ getMonthlyKpiPct(card.newFanMonthly, getStreamerKpi(card.streamerId, 'newFanMonthly')) }}%</div></div>
              </div>
            </div>
            <!-- 新增互动人数 -->
            <div class="sc-section">
              <div class="sc-section-title">{{ $t('stats.newChat') }}</div>
              <div class="sc-fields sc-fields-5">
                <div><div class="sc-stat-label">{{ $t('stats.daily') }}</div><div class="sc-stat-val">{{ fmt(card.chatDaily) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.dailyKPI') }}</div><div class="sc-stat-val">{{ getDailyKpiPct(card.chatDaily, getStreamerKpi(card.streamerId, 'chatDaily')) }}%</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.monthly') }}</div><div class="sc-stat-val">{{ fmt(card.chatMonthly) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.growth') }}</div><div class="sc-stat-val">{{ getGrowthRate(card.chatWeekly, card.chatLastWeek) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.kpiComplete') }}</div><div class="sc-stat-val">{{ getMonthlyKpiPct(card.chatMonthly, getStreamerKpi(card.streamerId, 'chatMonthly')) }}%</div></div>
              </div>
            </div>
            <!-- 新增用户打赏 -->
            <div class="sc-section">
              <div class="sc-section-title">{{ $t('stats.newTip') }}</div>
              <div class="sc-fields sc-fields-5">
                <div><div class="sc-stat-label">{{ $t('stats.daily') }}</div><div class="sc-stat-val">{{ fmt(card.newTipDailyAmount) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.dailyKPI') }}</div><div class="sc-stat-val">{{ getDailyKpiPct(card.newTipDailyAmount, getStreamerKpi(card.streamerId, 'newTipDaily')) }}%</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.monthly') }}</div><div class="sc-stat-val"><span class="clickable" @click="openNewTippersDialog(card.streamerId)">{{ fmt(card.newTipMonthlyAmount) }}</span></div></div>
                <div><div class="sc-stat-label">{{ $t('stats.growth') }}</div><div class="sc-stat-val">{{ getGrowthRate(card.newTipWeeklyAmount, card.newTipLastWeekAmount) }}</div></div>
                <div><div class="sc-stat-label">{{ $t('stats.kpiComplete') }}</div><div class="sc-stat-val">{{ getMonthlyKpiPct(card.newTipMonthlyAmount, getStreamerKpi(card.streamerId, 'newTipMonthly')) }}%</div></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 02 每日走势 -->
      <section>
        <div class="sec-title">{{ $t('stats.section02') }}</div>
        <div class="chart-box">
          <div class="chart-title">{{ $t('stats.dailyDiamondTrend') }}</div>
          <div class="chart-sub">{{ $t('stats.dailyDiamondSub') }}</div>
          <div class="legend">
            <span v-for="s in streamers" :key="s.streamerId">
              <span class="ldot" :style="{background: getStreamerColor(s.streamerId)}"></span>{{ s.stageName }}
            </span>
          </div>
          <div style="position:relative;height:240px">
            <div ref="trendChartRef" style="width:100%;height:100%"></div>
          </div>
        </div>
      </section>

      <!-- 03 粉丝转化 -->
      <section>
        <div class="sec-title">{{ $t('stats.section03') }}</div>
        <div class="fan-section">
          <div class="fan-card" v-for="item in customerCards" :key="item.streamerId" :style="'--c:' + getStreamerColor(item.streamerId)">
            <div class="danger-tag" v-if="getConversionRate(item) < 30">{{ $t('stats.danger') }}</div>
            <div class="fan-name">{{ item.stageName }}</div>
            <div class="fan-row">
              <span class="fan-label">{{ $t('stats.activeCustomers') }}</span>
              <span class="fan-val">{{ fmt(item.chatCustomers) }}</span>
            </div>
            <div class="fan-row">
              <span class="fan-label">{{ $t('stats.giftCustomers') }}</span>
              <span class="fan-val" :style="{color: getStreamerColor(item.streamerId)}">{{ fmt(item.activeCustomers) }}</span>
            </div>
            <div class="fan-row">
              <span class="fan-label">{{ $t('stats.highValueUsers') }}（≥1000）</span>
              <span class="fan-val clickable" :style="{color: getStreamerColor(item.streamerId)}" @click="openHighValueDialog(item.streamerId)">{{ fmt(item.highValueCustomers) }}</span>
            </div>
            <div class="conv-wrap">
              <div class="conv-label">{{ $t('stats.conversionRate') }}</div>
              <div class="conv-track">
                <div class="conv-fill" :style="{width: getConversionRate(item) + '%', background: getStreamerColor(item.streamerId)}"></div>
              </div>
              <div class="conv-pct">{{ getConversionRate(item) }}%</div>
            </div>
          </div>
        </div>
      </section>

      <!-- 04 当日维系情况 -->
      <section>
        <div class="sec-title">{{ $t('stats.section04') }} <span style="font-size:12px;color:#99998F;font-weight:400">（{{ $t('stats.weijiTip') }}）</span></div>
        <div class="weiji-row" v-if="weijiDayStats.length > 0">
          <div class="weiji-card" v-for="w in weijiDayStats" :key="w.streamerId" :style="'--c:' + getStreamerColor(w.streamerId)">
            <div class="weiji-card-header">{{ w.stageName }}</div>
            <div class="weiji-stats">
              <div class="weiji-stat">
                <div class="weiji-dot red"></div>
                <div><div class="weiji-label">{{ $t('stats.red') }}</div><div class="weiji-val weiji-clickable" style="color:#DC2626" @click="openWeijiDetail(w.streamerId, 'day')">{{ w.red }} <span class="weiji-pct">{{ getWeijiPct(w.red, w.total) }}%</span></div></div>
              </div>
              <div class="weiji-stat">
                <div class="weiji-dot green"></div>
                <div><div class="weiji-label">{{ $t('stats.green') }}</div><div class="weiji-val">{{ w.green }} <span class="weiji-pct">{{ getWeijiPct(w.green, w.total) }}%</span></div></div>
              </div>
              <div class="weiji-stat">
                <div class="weiji-dot yellow"></div>
                <div><div class="weiji-label">{{ $t('stats.yellow') }}</div><div class="weiji-val">{{ w.yellow }} <span class="weiji-pct">{{ getWeijiPct(w.yellow, w.total) }}%</span></div></div>
              </div>
              <div class="weiji-stat">
                <div class="weiji-dot orange"></div>
                <div><div class="weiji-label">{{ $t('stats.orange') }}</div><div class="weiji-val">{{ w.orange }} <span class="weiji-pct">{{ getWeijiPct(w.orange, w.total) }}%</span></div></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 05 月维系情况 -->
      <section>
        <div class="sec-title">{{ $t('stats.section05') }} <span style="font-size:12px;color:#99998F;font-weight:400">（{{ $t('stats.weijiMonthTip') }}）</span></div>
        <div class="weiji-row" v-if="weijiMonthStatsData.length > 0">
          <div class="weiji-card" v-for="w in weijiMonthStatsData" :key="w.streamerId" :style="'--c:' + getStreamerColor(w.streamerId)">
            <div class="weiji-card-header">{{ w.stageName }}</div>
            <div class="weiji-stats">
              <div class="weiji-stat">
                <div class="weiji-dot red"></div>
                <div><div class="weiji-label">{{ $t('stats.red') }}</div><div class="weiji-val weiji-clickable" style="color:#DC2626" @click="openWeijiDetail(w.streamerId, 'month')">{{ w.red }} <span class="weiji-pct">{{ getWeijiPct(w.red, w.total) }}%</span></div></div>
              </div>
              <div class="weiji-stat">
                <div class="weiji-dot green"></div>
                <div><div class="weiji-label">{{ $t('stats.green') }}</div><div class="weiji-val">{{ w.green }} <span class="weiji-pct">{{ getWeijiPct(w.green, w.total) }}%</span></div></div>
              </div>
              <div class="weiji-stat">
                <div class="weiji-dot yellow"></div>
                <div><div class="weiji-label">{{ $t('stats.yellow') }}</div><div class="weiji-val">{{ w.yellow }} <span class="weiji-pct">{{ getWeijiPct(w.yellow, w.total) }}%</span></div></div>
              </div>
              <div class="weiji-stat">
                <div class="weiji-dot orange"></div>
                <div><div class="weiji-label">{{ $t('stats.orange') }}</div><div class="weiji-val">{{ w.orange }} <span class="weiji-pct">{{ getWeijiPct(w.orange, w.total) }}%</span></div></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 06 AI 运营分析 -->
      <section>
        <div class="sec-title">{{ $t('stats.section06') }}</div>
        <div class="ai-chat-row">
          <div class="ai-chat-card" v-for="item in adviceList" :key="item.streamerId" :style="'--c:' + getStreamerColor(item.streamerId)">
            <div class="ai-chat-header">
              <div class="ai-chat-name">{{ item.stageName }}</div>
              <div class="ai-chat-status">{{ item.status }}</div>
            </div>
            <div class="ai-chat-messages" :ref="el => { if (el) chatRefs[item.streamerId] = el }">
              <div v-if="!chatMessages[item.streamerId] || chatMessages[item.streamerId].length === 0" class="ai-msg ai-msg-ai" style="text-align:center;max-width:100%">
                💬 {{ $t('stats.aiChatPlaceholder') }}
              </div>
              <template v-if="chatMessages[item.streamerId]">
                <div v-for="(msg, idx) in chatMessages[item.streamerId]" :key="idx" :class="['ai-msg', msg.role === 'user' ? 'ai-msg-user' : 'ai-msg-ai']">
                  <div class="ai-msg-content">{{ msg.content }}</div>
                </div>
              </template>
              <div v-if="chatLoading[item.streamerId]" class="ai-msg ai-msg-ai ai-typing">
                <div class="ai-msg-content">{{ $t('stats.thinking') }}</div>
              </div>
            </div>
            <div class="ai-chat-input">
              <input
                type="text"
                v-model="chatInputs[item.streamerId]"
                :placeholder="$t('stats.chatPlaceholder')"
                @keyup.enter="sendChat(item.streamerId)"
              />
              <button @click="sendChat(item.streamerId)" :disabled="isChatLoading(item.streamerId)">{{ $t('stats.send') }}</button>
            </div>
          </div>
        </div>
      </section>
    </main>

    <!-- 中高级用户弹窗 -->
    <div class="modal-overlay" :class="{show: highValueDialog.open}" @click="highValueDialog.open = false">
      <div class="modal-box" @click.stop>
        <div class="modal-header">
          <div>
            <div class="modal-title">{{ $t('stats.highValueUsers') }}</div>
            <div class="modal-sub">{{ $t('stats.highValueSub', { count: highValueDialog.data.length }) }}</div>
          </div>
          <button class="modal-close" @click="highValueDialog.open = false">&times;</button>
        </div>
        <div class="modal-body">
          <table>
            <thead><tr><th>#</th><th>{{ $t('stats.fanName') }}</th><th>{{ $t('stats.badge') }}</th><th>{{ $t('stats.monthlyGift') }}</th><th>{{ $t('stats.activeDays') }}</th><th>{{ $t('stats.lastActive') }}</th></tr></thead>
            <tbody>
              <tr v-for="(item, idx) in highValueDialog.data" :key="item.customerId">
                <td class="mono">{{ idx + 1 }}</td>
                <td class="highlight">{{ item.nickname }}</td>
                <td>{{ item.badge }}</td>
                <td class="mono" style="font-weight:600">{{ fmt(item.totalXu) }}</td>
                <td class="mono">{{ item.activeDays }}</td>
                <td class="mono">{{ item.lastActiveDate }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 维系详情弹窗 -->
    <div class="modal-overlay" :class="{show: weijiDetailDialog.open}" @click="weijiDetailDialog.open = false">
      <div class="modal-box" @click.stop>
        <div class="modal-header">
          <div>
            <div class="modal-title">{{ weijiDetailDialog.title }}</div>
            <div class="modal-sub">{{ $t('stats.totalCount', { count: weijiDetailDialog.data.length }) }}</div>
          </div>
          <button class="modal-close" @click="weijiDetailDialog.open = false">&times;</button>
        </div>
        <div class="modal-body">
          <table>
            <thead><tr><th>#</th><th>{{ $t('stats.fan') }}</th><th>{{ $t('stats.badge') }}</th><th>{{ $t('stats.monthlyGift') }}</th><th>{{ $t('stats.redDays') }}</th><th>{{ $t('stats.redDates') }}</th></tr></thead>
            <tbody>
              <tr v-for="(item, idx) in weijiDetailDialog.data" :key="item.customerId">
                <td class="mono">{{ idx + 1 }}</td>
                <td class="highlight">{{ item.nickname }}</td>
                <td>{{ item.badge }}</td>
                <td class="mono" style="font-weight:600">{{ fmt(item.totalXu) }}</td>
                <td class="mono" style="color:#DC2626">{{ item.redDays }}</td>
                <td style="font-size:10px;color:#99998F">{{ item.redDates }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 新增打赏用户弹窗 -->
    <div class="modal-overlay" :class="{show: newTippersDialog.open}" @click="newTippersDialog.open = false">
      <div class="modal-box" @click.stop>
        <div class="modal-header">
          <div>
            <div class="modal-title">{{ $t('stats.newTippers') }}</div>
            <div class="modal-sub">{{ $t('stats.newTippersSub', { count: newTippersDialog.data.length }) }}</div>
          </div>
          <button class="modal-close" @click="newTippersDialog.open = false">&times;</button>
        </div>
        <div class="modal-body">
          <table>
            <thead><tr><th>#</th><th>{{ $t('stats.fanName') }}</th><th>{{ $t('stats.badge') }}</th><th>{{ $t('stats.firstTipDate') }}</th><th>{{ $t('stats.monthlyTip') }}</th></tr></thead>
            <tbody>
              <tr v-for="(item, idx) in newTippersDialog.data" :key="item.customerId">
                <td class="mono">{{ idx + 1 }}</td>
                <td class="highlight">{{ item.nickname }}</td>
                <td>{{ item.badge }}</td>
                <td class="mono">{{ item.firstTipDate }}</td>
                <td class="mono" style="font-weight:600">{{ fmt(item.totalXu) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup name="LiveStats">
import * as echarts from 'echarts'
import { useI18n } from 'vue-i18n'
import { weeklyStats, streamerCardDetail, highValueUsers, newTippers, weijiStats, weijiMonthStats, weijiDetail, adviceData } from '@/api/live/stats'
import { getToken } from '@/utils/auth'
import { listStreamers } from '@/api/live/upload'
import { listKpiConfig, addKpiConfig, updateKpiConfig } from '@/api/live/kpi'

const { t } = useI18n()

const STREAMER_COLORS = {
  100: '#2D8C2D',  // Zhenzhen
  101: '#FF0000',  // Mina
  102: '#6B5CC4',  // Xixi
  103: '#A07020',  // Hana
}

const loading = ref(false)
const cards = ref([])
const cardDetails = ref([])
const customerCards = ref([])
const trend = ref([])
const streamers = ref([])
const overview = ref({})
const monthlyTotal = ref(0)
const trendChartRef = ref(null)
const trendChartRef2 = ref(null)
let trendChart = null
let trendChart2 = null
const handleTrendResize = () => {
  if (trendChart) {
    trendChart.resize()
  }
}

const today = new Date()
today.setDate(today.getDate() - 1)
const currentWeek = Math.ceil(today.getDate() / 7) || 1
const todayStr = (today.getMonth() + 1) + '月' + today.getDate() + '日'

const defaultEnd = formatDate(today)
const defaultBegin = formatDate(new Date(today.getFullYear(), today.getMonth(), 1))
const dateRange = ref([defaultBegin, defaultEnd])

const query = reactive({
  beginDate: defaultBegin,
  endDate: defaultEnd,
  streamerId: undefined
})

const highValueDialog = reactive({ open: false, data: [] })
const newTippersDialog = reactive({ open: false, data: [] })
const weijiDetailDialog = reactive({ open: false, data: [], title: '' })
const weijiDayStats = ref([])
const weijiMonthStatsData = ref([])
const adviceList = ref([])
const chatMessages = reactive({})
const chatInputs = reactive({})
const chatLoading = reactive({})
const chatRefs = reactive({})

// KPI 配置（按主播）
const kpiConfigs = ref({})

// 加载 KPI 配置
async function loadKpiConfig() {
  try {
    const year = today.getFullYear()
    const month = today.getMonth() + 1
    const res = await listKpiConfig({ kpiYear: year, kpiMonth: month })
    if (res.rows && res.rows.length > 0) {
      // 按主播ID分组
      const configs = {}
      res.rows.forEach(c => {
        if (c.streamerId) {
          configs[c.streamerId] = c
        }
      })
      kpiConfigs.value = configs
    }
  } catch (e) {
    console.error('加载 KPI 配置失败:', e)
  }
}

// 获取主播 KPI
function getStreamerKpi(streamerId, metric) {
  const config = kpiConfigs.value[streamerId]
  return config ? (config[metric] || 0) : 0
}

// 初始化主播聊天数据
function initChat(streamerId) {
  if (!chatMessages[streamerId]) {
    chatMessages[streamerId] = []
    chatInputs[streamerId] = ''
    chatLoading[streamerId] = false
  }
}

function formatDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function fmt(n) { return n ? Number(n).toLocaleString() : '0' }

function getStreamerColor(streamerId) {
  return STREAMER_COLORS[streamerId] || '#999'
}

function getWowClass(card) {
  const wow = getWowRate(card)
  return wow > 0 ? 'up' : wow < 0 ? 'dn' : ''
}

function getWowRate(card) {
  if (!card.lastWeekXu || card.lastWeekXu === 0) return card.weeklyXu > 0 ? 100 : 0
  return Math.round((card.weeklyXu - card.lastWeekXu) / card.lastWeekXu * 100)
}

function getWowText(card) {
  const { t } = useI18n()
  const wow = getWowRate(card)
  if (wow === 0) return t('stats.flat')
  return (wow > 0 ? '▲ +' : '▼ ') + Math.abs(wow) + '% ' + t('stats.vsLastWeek')
}

function getWowPct(card) {
  const wow = getWowRate(card)
  return (wow > 0 ? '+' : '') + wow + '%'
}

function getGrowthRate(current, previous) {
  if (!previous || previous === 0) return current > 0 ? '+100%' : '--'
  const rate = Math.round((current - previous) / previous * 100)
  return (rate > 0 ? '+' : '') + rate + '%'
}

function getWeeklyKpiPct(weekly, weeklyKpi) {
  if (!weeklyKpi || weeklyKpi === 0) return '--'
  return Math.round(weekly / weeklyKpi * 100)
}

function getMonthlyKpiPct(monthly, monthlyKpi) {
  if (!monthlyKpi || monthlyKpi === 0) return '--'
  return Math.round(monthly / monthlyKpi * 100)
}

function getDailyKpiPct(daily, dailyKpi) {
  if (!dailyKpi || dailyKpi === 0) return '--'
  return Math.round(daily / dailyKpi * 100)
}

function getForecastPct(card) {
  const monthlyKpi = getStreamerKpi(card.streamerId, 'giftMonthly')
  if (!monthlyKpi || monthlyKpi <= 0) return '--'
  if (!card.monthlyXu || card.monthlyXu === 0) return '0.0'
  const dayOfMonth = today.getDate()
  const dailyAvg = card.monthlyXu / dayOfMonth
  const forecast = Math.min(100, dailyAvg * 26 / monthlyKpi * 100)
  return forecast.toFixed(1)
}

function getForecastClass(card) {
  const pct = parseFloat(getForecastPct(card))
  if (Number.isNaN(pct)) return ''
  if (pct >= 100) return 'forecast-good'
  if (pct >= 70) return 'forecast-warn'
  return 'forecast-danger'
}

function getConversionRate(item) {
  if (!item.chatCustomers || item.chatCustomers === 0) return 0
  return Math.round(item.highValueCustomers / item.chatCustomers * 100)
}

async function loadData() {
  loading.value = true
  try {
    const res = await weeklyStats(query)
    const data = res.data || {}
    overview.value = data.overview || {}
    cards.value = data.cards || []
    customerCards.value = data.customerCards || []
    trend.value = data.trend || []
    monthlyTotal.value = cards.value.reduce((sum, c) => sum + (c.totalXu || 0), 0)
    await nextTick()
    renderTrend()
    await loadCardDetails()
  } finally {
    loading.value = false
  }
}

async function loadCardDetails() {
  const details = []
  for (const card of cards.value) {
    try {
      const res = await streamerCardDetail(card.streamerId)
      if (res.data && res.data.length > 0) {
        details.push(res.data[0])
      }
    } catch (e) { console.error(e) }
  }
  cardDetails.value = details
}

function renderTrend() {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
    window.addEventListener('resize', handleTrendResize)
  }
  const dates = [...new Set(trend.value.map(item => item.bizDate))]
  const names = [...new Set(trend.value.map(item => item.stageName))]
  const series = names.map(name => ({
    name,
    type: 'line',
    smooth: true,
    symbolSize: 7,
    data: dates.map(date => {
      const row = trend.value.find(item => item.bizDate === date && item.stageName === name)
      return row ? Number(row.totalXu || 0) : 0
    })
  }))
  trendChart.setOption({
    color: ['#16a34a', '#2563eb', '#dc2626', '#111827', '#ca8a04', '#7c3aed'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0, left: 0, textStyle: { fontSize: 11 } },
    grid: { left: 36, right: 18, top: 42, bottom: 28 },
    xAxis: { type: 'category', data: dates, axisTick: { show: false } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf0f2' } } },
    series
  })
}

async function openHighValueDialog(streamerId) {
  const month = query.beginDate.substring(0, 7)
  try {
    const res = await highValueUsers(streamerId, month)
    highValueDialog.data = res.data || []
    highValueDialog.open = true
  } catch (e) { console.error(e) }
}

async function openNewTippersDialog(streamerId) {
  const month = query.beginDate.substring(0, 7)
  try {
    const res = await newTippers(streamerId, month)
    newTippersDialog.data = res.data || []
    newTippersDialog.open = true
  } catch (e) { console.error(e) }
}

listStreamers().then(res => { streamers.value = res.data || [] })
loadKpiConfig()
loadData()
loadWeijiData()

async function loadWeijiData() {
  try {
    const dayRes = await weijiStats()
    weijiDayStats.value = dayRes.data || []
  } catch (e) { console.error(e) }
  try {
    const monthRes = await weijiMonthStats()
    weijiMonthStatsData.value = monthRes.data || []
  } catch (e) { console.error(e) }
  try {
    const advRes = await adviceData()
    adviceList.value = advRes.data || []
    // 初始化每个主播的聊天数据
    adviceList.value.forEach(item => initChat(item.streamerId))
  } catch (e) { console.error(e) }
}

function getWeijiPct(value, total) {
  if (!total || total === 0) return 0
  return Math.round(value / total * 100)
}

async function getChatMessages(streamerId) {
  return chatMessages[streamerId] || []
}

function isChatLoading(streamerId) {
  return chatLoading[streamerId] || false
}

async function sendChat(streamerId) {
  initChat(streamerId)
  const message = (chatInputs[streamerId] || '').trim()
  if (!message) return

  chatMessages[streamerId].push({ role: 'user', content: message })
  chatInputs[streamerId] = ''
  chatLoading[streamerId] = true

  // 滚动到底部
  await nextTick()
  const chatEl = chatRefs[streamerId]
  if (chatEl) chatEl.scrollTop = chatEl.scrollHeight

  const history = chatMessages[streamerId].slice(-10)

  try {
    const baseApi = import.meta.env.VITE_APP_BASE_API
    const response = await fetch(baseApi + '/live/stats/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + getToken()
      },
      body: JSON.stringify({ streamerId, message, history })
    })

    if (!response.ok) {
      throw new Error('HTTP ' + response.status)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let aiMessage = ''
    let buffer = ''
    chatMessages[streamerId].push({ role: 'assistant', content: '' })

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed) continue
        if (trimmed.startsWith('data:')) {
          const data = trimmed.substring(5).trim()
          if (data === '[DONE]') break
          try {
            const parsed = JSON.parse(data)
            if (parsed.delta) {
              aiMessage += parsed.delta
              chatMessages[streamerId][chatMessages[streamerId].length - 1].content = aiMessage
            }
          } catch (e) {
            // 兼容纯文本格式
            if (data && !data.startsWith('AI') && !data.startsWith('错误')) {
              aiMessage += data
              chatMessages[streamerId][chatMessages[streamerId].length - 1].content = aiMessage
            }
          }
        }
      }

      await nextTick()
      const el = chatRefs[streamerId]
      if (el) el.scrollTop = el.scrollHeight
    }

    if (!aiMessage) {
      chatMessages[streamerId][chatMessages[streamerId].length - 1].content = '（无回复）'
    }
  } catch (e) {
    console.error('Chat error:', e)
    const msgs = chatMessages[streamerId]
    if (msgs.length > 0 && msgs[msgs.length - 1].content === '') {
      msgs[msgs.length - 1].content = '请求失败: ' + e.message
    } else {
      msgs.push({ role: 'assistant', content: '请求失败: ' + e.message })
    }
  } finally {
    chatLoading[streamerId] = false
  }
}

async function openWeijiDetail(streamerId, mode) {
  const streamer = streamers.value.find(s => s.streamerId === streamerId)
  const stageName = streamer ? streamer.stageName : '未知'
  weijiDetailDialog.title = stageName + ' · 有打赏+无互动'

  let beginDate, endDate
  if (mode === 'day') {
    const yesterday = new Date()
    yesterday.setDate(yesterday.getDate() - 1)
    beginDate = formatDate(yesterday)
    endDate = beginDate
  } else {
    const today = new Date()
    const end = new Date(today)
    end.setDate(end.getDate() - 1)
    endDate = formatDate(end)
    beginDate = end.getFullYear() + '-' + String(end.getMonth() + 1).padStart(2, '0') + '-01'
  }

  try {
    const res = await weijiDetail(streamerId, beginDate, endDate)
    weijiDetailDialog.data = res.data || []
    weijiDetailDialog.open = true
  } catch (e) { console.error(e) }
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleTrendResize)
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
  if (trendChart2) {
    trendChart2.dispose()
    trendChart2 = null
  }
})
</script>

<style scoped>
:root {
  --bg: #F5F5F3;
  --card: #FFFFFF;
  --surface2: #EFEFED;
  --border: rgba(0,0,0,0.08);
  --text: #1A1A18;
  --muted: #555550;
  --dim: #99998F;
  --up: #1E7E3E;
  --dn: #A03030;
}

.report-page {
  min-height: calc(100vh - 84px);
  background: #F5F5F3;
  color: #1A1A18;
  font-family: 'Noto Sans SC', sans-serif;
  font-weight: 300;
  line-height: 1.6;
}

.report-header {
  background: #FFFFFF;
  border-bottom: 1px solid rgba(0,0,0,0.08);
  padding: 44px 52px 32px;
}

.eyebrow {
  font-family: 'DM Mono', monospace;
  font-size: 10px;
  color: #99998F;
  letter-spacing: .16em;
  text-transform: uppercase;
  margin-bottom: 8px;
}

h1 {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -.02em;
  margin-bottom: 4px;
}

.period {
  font-size: 14px;
  color: #555550;
  margin-bottom: 20px;
}

.meta-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.chip {
  font-family: 'DM Mono', monospace;
  font-size: 10px;
  color: #99998F;
  background: #EFEFED;
  border: 1px solid rgba(0,0,0,0.08);
  padding: 3px 10px;
  border-radius: 20px;
}

main {
  padding: 32px 52px 80px;
  max-width: 1260px;
}

section {
  margin-bottom: 40px;
}

.sec-title {
  font-family: 'DM Mono', monospace;
  font-size: 9px;
  color: #99998F;
  letter-spacing: .16em;
  text-transform: uppercase;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.sec-title::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(0,0,0,0.08);
}

.streamer-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.sc {
  background: #FFFFFF;
  border: 1px solid rgba(0,0,0,0.08);
  border-top: 3px solid var(--c);
  border-radius: 8px;
  padding: 16px 16px 14px;
  position: relative;
}

.sc-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--c);
  margin-bottom: 12px;
}

.sc-diamond {
  font-family: 'DM Mono', monospace;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A18;
  line-height: 1;
  margin-bottom: 3px;
}

.sc-wow {
  font-size: 11px;
  font-weight: 500;
}

.sc-wow.up { color: #1E7E3E; }
.sc-wow.dn { color: #A03030; }

.sc-divider {
  border: none;
  border-top: 1px solid rgba(0,0,0,0.08);
  margin: 10px 0;
}

.sc-section {
  margin-bottom: 8px;
}

.sc-section:last-child { margin-bottom: 0; }

.sc-section-title {
  font-size: 10px;
  font-weight: 600;
  color: var(--c);
  margin-bottom: 6px;
  letter-spacing: .04em;
}

.sc-fields {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.sc-fields-5 {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 2px;
}

.sc-fields-5 .sc-stat-label {
  font-size: 7px;
}

.sc-fields-5 .sc-stat-val {
  font-size: 8px;
}

.sc-stat-label {
  font-size: 9px;
  color: #99998F;
  margin-bottom: 1px;
}

.sc-stat-val {
  font-family: 'DM Mono', monospace;
  font-size: 12px;
  font-weight: 500;
}

.sc-stat-val.clickable {
  cursor: pointer;
  border-bottom: 1.5px dashed #99998F;
  color: var(--c);
  transition: opacity .15s;
}

.sc-stat-val.clickable:hover { opacity: .7; }

.forecast-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  border-radius: 4px;
  padding: 3px 8px;
  font-family: 'DM Mono', monospace;
  font-size: 10px;
  font-weight: 500;
  color: #fff;
  letter-spacing: .04em;
}

.forecast-good { background: #1E7E3E; box-shadow: 0 2px 4px rgba(30,126,62,.3); }
.forecast-warn { background: #A07020; box-shadow: 0 2px 4px rgba(160,112,32,.3); }
.forecast-danger { background: #DC2626; box-shadow: 0 2px 4px rgba(220,38,38,.3); }

.chart-box {
  background: #FFFFFF;
  border: 1px solid rgba(0,0,0,0.08);
  border-radius: 8px;
  padding: 20px;
}

.chart-title {
  font-size: 13px;
  font-weight: 500;
  color: #555550;
  margin-bottom: 3px;
}

.chart-sub {
  font-size: 11px;
  color: #99998F;
  margin-bottom: 14px;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}

.legend span {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #555550;
}

.ldot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
}

.fan-section {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

.fan-card {
  background: #FFFFFF;
  border: 1px solid rgba(0,0,0,0.08);
  border-radius: 8px;
  padding: 14px 16px;
  position: relative;
}

.fan-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--c);
  margin-bottom: 10px;
}

.fan-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 0;
  border-bottom: 1px solid rgba(0,0,0,0.08);
}

.fan-row:last-of-type { border-bottom: none; }

.fan-label {
  font-size: 11px;
  color: #99998F;
}

.fan-val {
  font-family: 'DM Mono', monospace;
  font-size: 13px;
  font-weight: 500;
}

.fan-val.clickable {
  cursor: pointer;
  border-bottom: 1.5px dashed;
}

.conv-wrap { margin-top: 10px; }

.conv-label {
  font-size: 10px;
  color: #99998F;
  margin-bottom: 4px;
}

.conv-track {
  height: 5px;
  background: #EFEFED;
  border-radius: 3px;
}

.conv-fill {
  height: 100%;
  border-radius: 3px;
}

.conv-pct {
  font-family: 'DM Mono', monospace;
  font-size: 12px;
  font-weight: 500;
  margin-top: 3px;
  color: var(--c);
}

.danger-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  background: #DC2626;
  border: none;
  border-radius: 4px;
  padding: 3px 8px;
  font-family: 'DM Mono', monospace;
  font-size: 10px;
  font-weight: 500;
  color: #fff;
  letter-spacing: .06em;
  box-shadow: 0 2px 4px rgba(220,38,38,.3);
}

/* Modal */
.modal-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.35);
  z-index: 1000;
  justify-content: center;
  align-items: center;
}

.modal-overlay.show { display: flex; }

.modal-box {
  background: #FFFFFF;
  border-radius: 10px;
  width: min(720px, 90vw);
  max-height: 80vh;
  overflow: hidden;
  box-shadow: 0 8px 40px rgba(0,0,0,0.18);
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px 16px;
  border-bottom: 1px solid rgba(0,0,0,0.08);
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
}

.modal-sub {
  font-size: 11px;
  color: #99998F;
  font-family: 'DM Mono', monospace;
  margin-top: 2px;
}

.modal-close {
  width: 32px;
  height: 32px;
  border: 1px solid rgba(0,0,0,0.08);
  border-radius: 50%;
  background: #FFFFFF;
  cursor: pointer;
  font-size: 16px;
  color: #555550;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  transition: background .15s;
}

.modal-close:hover { background: #EFEFED; }

.modal-body {
  overflow-y: auto;
  padding: 8px 24px 20px;
}

.modal-body table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.modal-body thead {
  background: #EFEFED;
}

.modal-body th {
  padding: 10px 14px;
  text-align: left;
  font-family: 'DM Mono', monospace;
  font-size: 9px;
  color: #99998F;
  letter-spacing: .08em;
  text-transform: uppercase;
  border-bottom: 1px solid rgba(0,0,0,0.08);
  white-space: nowrap;
}

.modal-body td {
  padding: 10px 14px;
  border-bottom: 1px solid rgba(0,0,0,0.08);
  vertical-align: top;
}

.modal-body tr:last-child td { border-bottom: none; }
.modal-body tr:hover td { background: rgba(0,0,0,0.015); }

.mono {
  font-family: 'DM Mono', monospace;
  font-size: 12px;
}

.highlight {
  color: var(--c);
  font-weight: 500;
}

/* 维系追踪卡片 */
.weiji-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}

.weiji-card {
  background: #FFFFFF;
  border: 1px solid rgba(0,0,0,0.08);
  border-top: 3px solid var(--c);
  border-radius: 8px;
  padding: 14px 12px;
}

.weiji-card-header {
  font-size: 11px;
  font-weight: 600;
  color: var(--c);
  margin-bottom: 10px;
}

.weiji-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.weiji-stat {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 10px;
}

.weiji-dot {
  width: 8px;
  height: 8px;
  border-radius: 3px;
  margin-top: 3px;
  flex-shrink: 0;
}

.weiji-dot.red { background: #DC2626; }
.weiji-dot.green { background: #2D8C2D; }
.weiji-dot.yellow { background: #E6C300; }
.weiji-dot.orange { background: #E67E22; }

.weiji-label {
  color: #99998F;
  font-size: 9px;
}

.weiji-val {
  font-family: 'DM Mono', monospace;
  font-size: 14px;
  font-weight: 500;
}

.weiji-pct {
  font-size: 9px;
  color: #99998F;
  font-weight: 400;
}

.weiji-clickable {
  cursor: pointer;
  border-bottom: 1.5px dashed;
}

.weiji-clickable:hover {
  opacity: 0.7;
}

/* 运营建议卡片 */
.adv-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

.adv-card {
  background: #FFFFFF;
  border: 1px solid rgba(0,0,0,0.08);
  border-left: 3px solid var(--c);
  border-radius: 8px;
  padding: 14px 16px;
}

.adv-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--c);
  margin-bottom: 2px;
}

.adv-status {
  font-family: 'DM Mono', monospace;
  font-size: 9px;
  color: #99998F;
  margin-bottom: 10px;
  letter-spacing: .04em;
}

.adv-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.adv-list li {
  font-size: 11px;
  color: #555550;
  padding: 3px 0 3px 12px;
  position: relative;
  line-height: 1.5;
}

.adv-list li::before {
  content: '›';
  position: absolute;
  left: 0;
  color: #99998F;
}

.alert-chip {
  background: rgba(160,48,48,.07);
  border: 1px solid rgba(160,48,48,.2);
  border-radius: 4px;
  padding: 5px 8px;
  font-size: 10px;
  color: #A03030;
  font-family: 'DM Mono', monospace;
  margin-bottom: 8px;
}

/* AI 聊天卡片 */
.ai-chat-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.ai-chat-card {
  background: #FFFFFF;
  border: 1px solid rgba(0,0,0,0.08);
  border-top: 3px solid var(--c);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  height: 400px;
}

.ai-chat-header {
  padding: 12px 14px;
  border-bottom: 1px solid rgba(0,0,0,0.08);
}

.ai-chat-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--c);
}

.ai-chat-status {
  font-family: 'DM Mono', monospace;
  font-size: 9px;
  color: #99998F;
  margin-top: 2px;
}

.ai-chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
}

.ai-msg {
  margin-bottom: 10px;
}

.ai-msg-user {
  text-align: right;
}

.ai-msg-user .ai-msg-content {
  display: inline-block;
  background: var(--c);
  color: #fff;
  padding: 8px 12px;
  border-radius: 12px 12px 4px 12px;
  max-width: 85%;
  text-align: left;
  font-size: 13px;
}

.ai-msg-ai .ai-msg-content {
  display: inline-block;
  background: #f4f5f3;
  color: #1A1A18;
  padding: 8px 12px;
  border-radius: 12px 12px 12px 4px;
  max-width: 85%;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.ai-typing {
  color: #99998F;
  font-style: italic;
}

.ai-chat-input {
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid rgba(0,0,0,0.08);
}

.ai-chat-input input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid rgba(0,0,0,0.08);
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.ai-chat-input input:focus {
  border-color: var(--c);
}

.ai-chat-input button {
  padding: 8px 16px;
  background: var(--c);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  font-weight: 500;
}

.ai-chat-input button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 1100px) {
  .streamer-row { grid-template-columns: repeat(2, minmax(220px, 1fr)); }
  .fan-section { grid-template-columns: repeat(3, minmax(160px, 1fr)); }
  .weiji-row { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 760px) {
  .report-header {
    padding: 16px;
  }

  .report-header h1 {
    font-size: 20px;
  }

  .eyebrow {
    font-size: 8px;
  }

  .meta-row {
    flex-wrap: wrap;
    gap: 6px;
  }

  .chip {
    font-size: 9px;
    padding: 2px 8px;
  }

  main {
    padding: 12px;
  }

  section {
    margin-bottom: 24px;
  }

  .sec-title {
    font-size: 8px;
    margin-bottom: 12px;
  }

  .streamer-row,
  .fan-section,
  .weiji-row,
  .ai-chat-row {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .sc {
    padding: 12px;
  }

  .sc-name {
    font-size: 11px;
    margin-bottom: 8px;
  }

  .sc-diamond {
    font-size: 20px;
    margin-bottom: 2px;
  }

  .sc-wow {
    font-size: 10px;
  }

  .sc-divider {
    margin: 8px 0;
  }

  .sc-section {
    margin-bottom: 6px;
  }

  .sc-section-title {
    font-size: 9px;
    margin-bottom: 4px;
  }

  .sc-fields-5 {
    grid-template-columns: repeat(5, 1fr);
    gap: 2px;
  }

  .sc-fields-5 .sc-stat-label {
    font-size: 6px;
  }

  .sc-fields-5 .sc-stat-val {
    font-size: 7px;
  }

  .chart-box {
    height: auto;
    min-height: 200px;
  }

  .fan-card {
    padding: 12px;
  }

  .fan-name {
    font-size: 13px;
    margin-bottom: 10px;
  }

  .fan-row {
    padding: 6px 0;
  }

  .fan-label {
    font-size: 11px;
  }

  .fan-val {
    font-size: 13px;
  }

  .conv-label {
    font-size: 10px;
  }

  .conv-pct {
    font-size: 12px;
  }

  .weiji-card {
    padding: 12px;
  }

  .weiji-card-header {
    font-size: 13px;
    margin-bottom: 10px;
  }

  .weiji-stats {
    gap: 8px;
  }

  .weiji-label {
    font-size: 10px;
  }

  .weiji-val {
    font-size: 14px;
  }

  .weiji-pct {
    font-size: 10px;
  }

  .ai-chat-card {
    padding: 12px;
  }

  .ai-chat-header {
    padding: 10px 12px;
  }

  .ai-chat-name {
    font-size: 13px;
  }

  .ai-chat-status {
    font-size: 9px;
  }

  .ai-chat-messages {
    padding: 12px;
    min-height: 150px;
    max-height: 250px;
  }

  .ai-msg-content {
    font-size: 12px;
  }

  .ai-chat-input input {
    font-size: 12px;
    padding: 8px 10px;
  }

  .ai-chat-input button {
    padding: 8px 12px;
    font-size: 12px;
  }

  /* 弹窗优化 */
  .modal-box {
    width: 95%;
    max-height: 85vh;
  }

  .modal-title {
    font-size: 14px;
  }

  .modal-sub {
    font-size: 10px;
  }

  .modal-body table {
    font-size: 11px;
  }

  .modal-body th,
  .modal-body td {
    padding: 6px 4px;
  }

  .forecast-badge {
    font-size: 8px;
    padding: 2px 6px;
  }

  .danger-tag {
    font-size: 9px;
    padding: 2px 6px;
  }
}
</style>
