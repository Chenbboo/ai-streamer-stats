<template>
  <div class="app-container live-upload-page">
    <!-- 每日提交 -->
    <el-card class="box-card" shadow="never">
      <template #header><b>{{ $t('upload.dailySubmit') }}</b></template>
      <el-form :model="form" label-width="90px" class="submit-form">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item :label="$t('upload.bizDate')" required>
              <el-date-picker v-model="form.bizDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('upload.bizDatePlaceholder')" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item :label="$t('upload.streamer')" required>
              <el-select v-if="streamers.length > 1" v-model="form.streamerId" :placeholder="$t('upload.selectStreamer')" style="width: 100%">
                <el-option v-for="s in streamers" :key="s.streamerId" :label="s.stageName" :value="s.streamerId" />
              </el-select>
              <el-input v-else :model-value="streamers[0]?.stageName || ''" disabled style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item :label="$t('upload.giftScreenshot')">
              <el-upload ref="giftUploadRef" v-model:file-list="giftFiles" :auto-upload="false" multiple accept="image/*" list-type="picture-card">
                <el-icon><Plus /></el-icon>
              </el-upload>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item :label="$t('upload.chatScreenshot')">
              <el-upload ref="chatUploadRef" v-model:file-list="chatFiles" :auto-upload="false" multiple accept="image/*" list-type="picture-card">
                <el-icon><Plus /></el-icon>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('upload.reportText')">
          <el-input v-model="form.rawText" type="textarea" :rows="2" :placeholder="$t('upload.reportPlaceholder')" />
        </el-form-item>
        <el-form-item>
          <el-button class="submit-button" type="primary" :loading="submitting" @click="handleSubmit">{{ $t('upload.submit') }}</el-button>
          <span class="tip">{{ $t('upload.submitTip') }}</span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 查询 + 列表 -->
    <el-card class="box-card" shadow="never" style="margin-top: 12px">
      <el-form :model="query" inline class="query-form">
        <el-form-item :label="$t('upload.date')">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="-" :start-placeholder="$t('upload.startDate')" :end-placeholder="$t('upload.endDate')" />
        </el-form-item>
        <el-form-item :label="$t('upload.streamer')">
          <el-select v-model="query.streamerId" :placeholder="$t('common.all')" clearable style="width: 160px">
            <el-option v-for="s in streamers" :key="s.streamerId" :label="s.stageName" :value="s.streamerId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('upload.type')" v-show="activeTab === 'detail'">
          <el-select v-model="query.uploadType" :placeholder="$t('common.all')" clearable style="width: 140px">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">{{ $t('common.search') }}</el-button>
          <el-button icon="Refresh" @click="resetQuery">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-tabs v-model="activeTab" @tab-change="handleQuery">
        <!-- 按日汇总 -->
        <el-tab-pane :label="$t('upload.dailySummary')" name="daily">
          <el-table v-loading="loading" :data="dailyList" class="desktop-table">
            <el-table-column :label="$t('upload.date')" prop="bizDate" width="120" />
            <el-table-column :label="$t('upload.streamer')" prop="stageName" width="140" />
            <el-table-column :label="$t('upload.giftScreenshot')" align="center">
              <template #default="{ row }">
                <el-tag :type="row.giftCount > 0 ? 'success' : 'danger'">{{ row.giftCount > 0 ? row.giftCount + ' ' + $t('upload.pieces') : $t('upload.notSubmitted') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('upload.chatScreenshot')" align="center">
              <template #default="{ row }">
                <el-tag :type="row.chatCount > 0 ? 'success' : 'danger'">{{ row.chatCount > 0 ? row.chatCount + ' ' + $t('upload.pieces') : $t('upload.notSubmitted') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('upload.reportText')" align="center">
              <template #default="{ row }">
                <el-tag :type="row.reportCount > 0 ? 'success' : 'danger'">{{ row.reportCount > 0 ? $t('upload.submitted') : $t('upload.notSubmitted') }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-loading="loading" class="mobile-list">
            <div v-for="row in dailyList" :key="row.bizDate + '-' + row.streamerId" class="mobile-record">
              <div class="record-main">
                <div>
                  <div class="record-title">{{ row.stageName || '-' }}</div>
                  <div class="record-subtitle">{{ row.bizDate }}</div>
                </div>
              </div>
              <div class="record-tags">
                <el-tag :type="row.giftCount > 0 ? 'success' : 'danger'">{{ $t('upload.gift') }} {{ row.giftCount > 0 ? row.giftCount + $t('upload.pieces') : $t('upload.notSubmitted') }}</el-tag>
                <el-tag :type="row.chatCount > 0 ? 'success' : 'danger'">{{ $t('upload.chat') }} {{ row.chatCount > 0 ? row.chatCount + $t('upload.pieces') : $t('upload.notSubmitted') }}</el-tag>
                <el-tag :type="row.reportCount > 0 ? 'success' : 'danger'">{{ $t('upload.report') }} {{ row.reportCount > 0 ? $t('upload.submitted') : $t('upload.notSubmitted') }}</el-tag>
              </div>
            </div>
            <el-empty v-if="!loading && dailyList.length === 0" :description="$t('upload.noRecords')" />
          </div>
          <pagination v-show="dailyTotal > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="dailyTotal" @pagination="loadDaily" />
        </el-tab-pane>

        <!-- 明细 -->
        <el-tab-pane :label="$t('upload.detailList')" name="detail">
          <el-table v-loading="loading" :data="detailList" class="desktop-table">
            <el-table-column :label="$t('upload.content')" width="110" align="center">
              <template #default="{ row }">
                <el-image v-if="row.filePath" :src="baseApi + row.filePath" :preview-src-list="[baseApi + row.filePath]" preview-teleported fit="cover" style="width: 80px; height: 80px" />
                <span v-else class="report-text">{{ row.rawText }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('upload.date')" prop="bizDate" width="110" />
            <el-table-column :label="$t('upload.streamer')" prop="stageName" width="120" />
            <el-table-column :label="$t('upload.type')" width="110" align="center">
              <template #default="{ row }">{{ typeLabel(row.uploadType) }}</template>
            </el-table-column>
            <el-table-column :label="$t('upload.recognizeStatus')" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.aiStatus)">{{ statusLabel(row.aiStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('upload.uploader')" prop="uploadByName" width="120" />
            <el-table-column :label="$t('upload.uploadTime')" prop="createTime" width="170" />
            <el-table-column :label="$t('common.action')" width="150" align="center">
              <template #default="{ row }">
                <el-button v-if="row.uploadType === '3'" v-hasPermi="['live:upload:add']" link type="primary" icon="Edit" @click="handleCorrectDate(row)">修正日期</el-button>
                <el-button v-hasPermi="['live:upload:remove']" link type="danger" icon="Delete" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-loading="loading" class="mobile-list">
            <div v-for="row in detailList" :key="row.uploadId" class="mobile-record">
              <div class="record-main">
                <el-image v-if="row.filePath" :src="baseApi + row.filePath" :preview-src-list="[baseApi + row.filePath]" preview-teleported fit="cover" class="record-thumb" />
                <div v-else class="record-text-thumb">{{ $t('upload.report') }}</div>
                <div class="record-content">
                  <div class="record-title">{{ typeLabel(row.uploadType) }}</div>
                  <div class="record-subtitle">{{ row.stageName || '-' }} · {{ row.bizDate }}</div>
                  <div v-if="row.rawText" class="record-report">{{ row.rawText }}</div>
                </div>
              </div>
              <div class="record-footer">
                <el-tag :type="statusTag(row.aiStatus)">{{ statusLabel(row.aiStatus) }}</el-tag>
                <el-button v-if="row.uploadType === '3'" v-hasPermi="['live:upload:add']" link type="primary" icon="Edit" @click="handleCorrectDate(row)">修正日期</el-button>
                <el-button v-hasPermi="['live:upload:remove']" link type="danger" icon="Delete" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
              </div>
            </div>
            <el-empty v-if="!loading && detailList.length === 0" :description="$t('upload.noDetailRecords')" />
          </div>
          <pagination v-show="detailTotal > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="detailTotal" @pagination="loadDetail" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup name="LiveUpload">
import { listUpload, dailySummary, uploadImages, submitReport, correctReportDate, delUpload, listStreamers } from '@/api/live/upload'

const { proxy } = getCurrentInstance()
const baseApi = import.meta.env.VITE_APP_BASE_API

const streamers = ref([])
const giftFiles = ref([])
const chatFiles = ref([])
const submitting = ref(false)
const loading = ref(false)
const activeTab = ref('daily')
const dailyList = ref([])
const dailyTotal = ref(0)
const detailList = ref([])
const detailTotal = ref(0)
const dateRange = ref([])

const form = reactive({
  bizDate: proxy.parseTime(new Date(), '{y}-{m}-{d}'),
  streamerId: undefined,
  rawText: ''
})

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  streamerId: undefined,
  uploadType: undefined
})

const typeOptions = [
  { value: '1', label: '打赏榜截图' },
  { value: '2', label: '聊天截图' },
  { value: '3', label: '汇报文本' }
]

function typeLabel(v) {
  return (typeOptions.find(t => t.value === v) || {}).label || v
}
function statusLabel(v) {
  return { '0': '待识别', '1': '已识别', '2': '已校正', '3': '识别失败' }[v] || v
}
function statusTag(v) {
  return { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger' }[v] || 'info'
}

async function handleSubmit() {
  if (!form.bizDate) return proxy.$modal.msgError('请选择业务日期')
  if (!form.streamerId) return proxy.$modal.msgError('请选择主播')
  const hasGift = giftFiles.value.length > 0
  const hasChat = chatFiles.value.length > 0
  const hasReport = form.rawText && form.rawText.trim() !== ''
  if (!hasGift && !hasChat && !hasReport) return proxy.$modal.msgError('请至少填写一项内容')

  submitting.value = true
  try {
    if (hasGift) {
      await doUploadImages('1', giftFiles.value)
      giftFiles.value = []
    }
    if (hasChat) {
      await doUploadImages('2', chatFiles.value)
      chatFiles.value = []
    }
    if (hasReport) {
      await submitReport({ bizDate: form.bizDate, streamerId: form.streamerId, rawText: form.rawText.trim() })
      form.rawText = ''
    }
    proxy.$modal.msgSuccess('提交成功')
    handleQuery()
  } finally {
    submitting.value = false
  }
}

function doUploadImages(uploadType, files) {
  const fd = new FormData()
  fd.append('bizDate', form.bizDate)
  fd.append('streamerId', form.streamerId)
  fd.append('uploadType', uploadType)
  files.forEach(f => fd.append('files', f.raw))
  return uploadImages(fd)
}

function buildParams() {
  const p = { ...query }
  if (dateRange.value && dateRange.value.length === 2) {
    p.beginDate = dateRange.value[0]
    p.endDate = dateRange.value[1]
  }
  return p
}

async function loadDaily() {
  loading.value = true
  try {
    const res = await dailySummary(buildParams())
    dailyList.value = res.rows
    dailyTotal.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await listUpload(buildParams())
    detailList.value = res.rows
    detailTotal.value = res.total
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  query.pageNum = 1
  activeTab.value === 'daily' ? loadDaily() : loadDetail()
}

function resetQuery() {
  dateRange.value = []
  query.streamerId = undefined
  query.uploadType = undefined
  handleQuery()
}

function handleDelete(row) {
  proxy.$modal.confirm('确认删除这条上传记录吗?文件将一并删除').then(() => delUpload(row.uploadId)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    handleQuery()
  }).catch(() => {})
}

function handleCorrectDate(row) {
  proxy.$prompt('请输入正确的业务日期', '修正汇报日期', {
    inputValue: row.bizDate,
    inputPlaceholder: 'YYYY-MM-DD',
    inputPattern: /^\d{4}-\d{2}-\d{2}$/,
    inputErrorMessage: '请输入 YYYY-MM-DD 格式的日期',
    confirmButtonText: '确认修正',
    cancelButtonText: '取消'
  }).then(({ value }) => correctReportDate(row.uploadId, value)).then(() => {
    proxy.$modal.msgSuccess('汇报日期已修正')
    handleQuery()
  }).catch(() => {})
}

listStreamers().then(res => {
  streamers.value = res.data || []
  if (streamers.value.length === 1) {
    form.streamerId = streamers.value[0].streamerId
  }
})
handleQuery()
</script>

<style scoped>
.live-upload-page {
  padding-bottom: 16px;
}

.tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.report-text {
  font-size: 12px;
  color: #606266;
  display: inline-block;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-list {
  display: none;
}

.mobile-record {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
  background: #fff;
}

.record-main {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.record-content {
  min-width: 0;
  flex: 1;
}

.record-title {
  color: #303133;
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}

.record-subtitle {
  color: #909399;
  font-size: 12px;
  line-height: 20px;
}

.record-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.record-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.record-thumb,
.record-text-thumb {
  flex: 0 0 58px;
  width: 58px;
  height: 58px;
  border-radius: 6px;
}

.record-text-thumb {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409eff;
  background: #ecf5ff;
  font-size: 13px;
}

.record-report {
  max-width: 100%;
  color: #606266;
  font-size: 13px;
  line-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .live-upload-page {
    padding: 8px;
    padding-bottom: 72px;
  }

  .live-upload-page :deep(.el-card__body) {
    padding: 12px;
  }

  .submit-form :deep(.el-form-item) {
    display: block;
    margin-bottom: 14px;
  }

  .submit-form :deep(.el-form-item__label) {
    display: block;
    width: auto !important;
    height: 22px;
    line-height: 22px;
    margin-bottom: 6px;
    text-align: left;
  }

  .submit-form :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }

  .live-upload-page :deep(.el-upload--picture-card),
  .live-upload-page :deep(.el-upload-list--picture-card .el-upload-list__item) {
    width: 76px;
    height: 76px;
  }

  .submit-button {
    width: 100%;
    height: 44px;
    font-size: 16px;
    border-radius: 8px;
  }

  .tip {
    display: none;
  }

  .tip {
    display: block;
    margin: 8px 0 0;
    line-height: 18px;
  }

  .query-form {
    display: flex;
    flex-direction: column;
  }

  .query-form :deep(.el-form-item) {
    display: block;
    margin-right: 0;
    margin-bottom: 10px;
  }

  .query-form :deep(.el-form-item__content),
  .query-form :deep(.el-date-editor),
  .query-form :deep(.el-select) {
    width: 100% !important;
  }

  .desktop-table {
    display: none;
  }

  .mobile-list {
    display: block;
  }
}
</style>
