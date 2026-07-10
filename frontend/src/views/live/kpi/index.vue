<template>
  <div class="app-container">
    <!-- 顶部操作栏 -->
    <div class="kpi-header">
      <div class="kpi-header-left">
        <el-select v-model="selectedYear" style="width: 100px" @change="loadKpiData">
          <el-option v-for="y in yearOptions" :key="y" :label="y + $t('kpi.year')" :value="y" />
        </el-select>
        <el-select v-model="selectedMonth" style="width: 80px" @change="loadKpiData">
          <el-option v-for="m in 12" :key="m" :label="m + $t('kpi.month')" :value="m" />
        </el-select>
      </div>
      <el-button type="primary" icon="Plus" @click="handleAddAll">{{ $t('kpi.addAll') }}</el-button>
    </div>

    <!-- 主播 KPI 卡片 -->
    <div class="kpi-cards">
      <!-- 各主播配置卡片 -->
      <div class="kpi-card" v-for="streamer in streamers" :key="streamer.streamerId">
        <div class="kpi-card-header">
          <div class="kpi-card-title">
            <span class="kpi-streamer-dot" :style="getStreamerColorStyle(streamer.streamerId)"></span>
            <span>{{ streamer.stageName }}</span>
          </div>
          <div class="kpi-card-actions">
            <el-button link type="primary" icon="Edit" @click="handleUpdateStreamer(streamer)">{{ $t('common.edit') }}</el-button>
          </div>
        </div>
        <div class="kpi-card-body">
          <div class="kpi-item">
            <span class="kpi-label">{{ $t('kpi.gift') }}</span>
            <span class="kpi-value">{{ getKpiDisplay(streamer.streamerId, 'gift') }}</span>
          </div>
          <div class="kpi-item">
            <span class="kpi-label">{{ $t('kpi.newFan') }}</span>
            <span class="kpi-value">{{ getKpiDisplay(streamer.streamerId, 'newFan') }}</span>
          </div>
          <div class="kpi-item">
            <span class="kpi-label">{{ $t('kpi.chat') }}</span>
            <span class="kpi-value">{{ getKpiDisplay(streamer.streamerId, 'chat') }}</span>
          </div>
          <div class="kpi-item">
            <span class="kpi-label">{{ $t('kpi.newTip') }}</span>
            <span class="kpi-value">{{ getKpiDisplay(streamer.streamerId, 'newTip') }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 添加或修改对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="kpiRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item :label="$t('kpi.streamer')" prop="streamerId">
          <el-select v-model="form.streamerId" :placeholder="$t('kpi.defaultConfig')" clearable style="width: 100%">
            <el-option v-for="s in streamers" :key="s.streamerId" :label="s.stageName" :value="s.streamerId" />
          </el-select>
        </el-form-item>
        <el-divider content-position="center">{{ $t('kpi.gift') }} (Xu)</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('kpi.dailyKPI')" prop="giftDaily">
              <el-input-number v-model="form.giftDaily" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('kpi.monthlyKPI')" prop="giftMonthly">
              <el-input-number v-model="form.giftMonthly" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="center">{{ $t('kpi.newFan') }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('kpi.dailyKPI')" prop="newFanDaily">
              <el-input-number v-model="form.newFanDaily" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('kpi.monthlyKPI')" prop="newFanMonthly">
              <el-input-number v-model="form.newFanMonthly" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="center">{{ $t('kpi.chat') }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('kpi.dailyKPI')" prop="chatDaily">
              <el-input-number v-model="form.chatDaily" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('kpi.monthlyKPI')" prop="chatMonthly">
              <el-input-number v-model="form.chatMonthly" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="center">{{ $t('kpi.newTip') }} (Xu)</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('kpi.dailyKPI')" prop="newTipDaily">
              <el-input-number v-model="form.newTipDaily" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('kpi.monthlyKPI')" prop="newTipMonthly">
              <el-input-number v-model="form.newTipMonthly" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">{{ $t('common.confirm') }}</el-button>
          <el-button @click="cancel">{{ $t('common.cancel') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LiveKpi">
import { useI18n } from 'vue-i18n'
import { listKpiConfig, getKpiConfig, addKpiConfig, updateKpiConfig, delKpiConfig } from '@/api/live/kpi'
import { listStreamers } from '@/api/live/upload'
import { Setting } from '@element-plus/icons-vue'

const { proxy } = getCurrentInstance()
const { t } = useI18n()

const streamers = ref([])
const kpiList = ref([])
const streamerConfigs = ref({})
const open = ref(false)
const title = ref('')

const selectedYear = ref(new Date().getFullYear())
const selectedMonth = ref(new Date().getMonth() + 1)
const yearOptions = ref([2024, 2025, 2026, 2027, 2028])

const STREAMER_COLORS = {
  145: '#2D8C2D',
  141: '#FF0000',
  146: '#6B5CC4',
  142: '#A07020',
  143: '#3B82F6',
  144: '#EC4899'
}

const data = reactive({
  form: {},
  rules: {
    kpiYear: [{ required: true, message: '年份不能为空', trigger: 'blur' }],
    kpiMonth: [{ required: true, message: '月份不能为空', trigger: 'change' }]
  }
})

const { form, rules } = toRefs(data)

/** 获取主播颜色 */
function getStreamerColorStyle(streamerId) {
  return { background: STREAMER_COLORS[streamerId] || '#999' }
}

/** 获取 KPI 显示文本 */
function getKpiDisplay(streamerId, metric) {
  const { t } = useI18n()
  const config = streamerConfigs.value[streamerId]
  if (!config) return t('kpi.notConfigured')
  const daily = config[metric + 'Daily'] ?? '--'
  const monthly = config[metric + 'Monthly'] ?? '--'
  return `${t('kpi.daily')} ${daily} / ${t('kpi.monthly')} ${monthly}`
}

/** 加载 KPI 数据 */
async function loadKpiData() {
  try {
    const res = await listKpiConfig({ kpiYear: selectedYear.value, kpiMonth: selectedMonth.value })
    kpiList.value = res.rows || []

    // 按主播ID分组
    streamerConfigs.value = {}
    kpiList.value.forEach(c => {
      if (c.streamerId) {
        streamerConfigs.value[c.streamerId] = c
      }
    })
  } catch (e) {
    console.error('加载 KPI 配置失败:', e)
  }
}

/** 为所有主播添加 */
function handleAddAll() {
  reset()
  form.value.kpiYear = selectedYear.value
  form.value.kpiMonth = selectedMonth.value
  open.value = true
  title.value = '添加 KPI 配置'
}

/** 编辑主播配置 */
function handleUpdateStreamer(streamer) {
  const config = streamerConfigs.value[streamer.streamerId]
  if (config) {
    handleUpdate(config)
  } else {
    reset()
    form.value.streamerId = streamer.streamerId
    form.value.kpiYear = selectedYear.value
    form.value.kpiMonth = selectedMonth.value
    open.value = true
    title.value = `为 ${streamer.stageName} 添加 KPI 配置`
  }
}

/** 修改配置 */
function handleUpdate(row) {
  reset()
  getKpiConfig(row.kpiId).then(response => {
    form.value = response.data
    open.value = true
    title.value = '修改 KPI 配置'
  })
}

/** 提交表单 */
function submitForm() {
  proxy.$refs['kpiRef'].validate(valid => {
    if (valid) {
      if (form.value.kpiId) {
        updateKpiConfig(form.value).then(response => {
          proxy.$modal.msgSuccess('修改成功')
          open.value = false
          loadKpiData()
        })
      } else {
        addKpiConfig(form.value).then(response => {
          proxy.$modal.msgSuccess('新增成功')
          open.value = false
          loadKpiData()
        })
      }
    }
  })
}

/** 删除配置 */
function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除该 KPI 配置？').then(function () {
    return delKpiConfig(row.kpiId)
  }).then(() => {
    loadKpiData()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 表单重置 */
function reset() {
  form.value = {
    kpiId: undefined,
    streamerId: undefined,
    kpiYear: selectedYear.value,
    kpiMonth: selectedMonth.value,
    giftDaily: 10000,
    giftMonthly: 260000,
    newFanDaily: 10,
    newFanMonthly: 260,
    chatDaily: 5,
    chatMonthly: 130,
    newTipDaily: 5000,
    newTipMonthly: 130000
  }
  proxy.resetForm('kpiRef')
}

/** 取消 */
function cancel() {
  open.value = false
  reset()
}

// 初始化
listStreamers().then(res => { streamers.value = res.data || [] })
loadKpiData()
</script>

<style scoped>
.kpi-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.kpi-header-left {
  display: flex;
  gap: 10px;
}

.kpi-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.kpi-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.2s;
}

.kpi-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.kpi-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.kpi-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  font-size: 14px;
}

.kpi-streamer-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.kpi-card-actions {
  display: flex;
  gap: 4px;
}

.kpi-card-body {
  padding: 16px;
}

.kpi-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.kpi-item:last-child {
  border-bottom: none;
}

.kpi-label {
  color: #606266;
  font-size: 13px;
}

.kpi-value {
  font-family: 'DM Mono', monospace;
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}
</style>
