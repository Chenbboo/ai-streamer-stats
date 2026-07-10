<template>
  <div class="app-container live-review-page">
    <el-card shadow="never">
      <el-form :model="query" inline>
        <el-form-item :label="$t('review.date')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="-"
            :start-placeholder="$t('review.startDate')"
            :end-placeholder="$t('review.endDate')"
          />
        </el-form-item>
        <el-form-item :label="$t('review.streamer')">
          <el-select v-model="query.streamerId" :placeholder="$t('common.all')" clearable style="width: 160px">
            <el-option v-for="s in streamers" :key="s.streamerId" :label="s.stageName" :value="s.streamerId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('review.type')">
          <el-select v-model="query.uploadType" :placeholder="$t('common.all')" clearable style="width: 140px">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('review.status')">
          <el-select v-model="query.aiStatus" :placeholder="$t('common.all')" clearable style="width: 140px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">{{ $t('common.search') }}</el-button>
          <el-button icon="Refresh" @click="resetQuery">{{ $t('common.reset') }}</el-button>
          <el-button type="warning" icon="Connection" @click="openMergeDialog">{{ $t('review.mergeCustomer') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows">
        <el-table-column :label="$t('review.content')" width="110" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.filePath"
              :src="baseApi + row.filePath"
              :preview-src-list="[baseApi + row.filePath]"
              preview-teleported
              fit="cover"
              class="thumb"
            />
            <span v-else class="report-text">{{ row.rawText }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('review.date')" prop="bizDate" width="110" />
        <el-table-column :label="$t('review.streamer')" prop="stageName" width="120" />
        <el-table-column :label="$t('review.type')" width="120">
          <template #default="{ row }">{{ typeLabel(row.uploadType) }}</template>
        </el-table-column>
        <el-table-column :label="$t('review.recognizeStatus')" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row._recognizing" type="primary" effect="plain">
              <el-icon class="is-loading" style="margin-right: 4px"><Loading /></el-icon>{{ $t('review.recognizing') }}
            </el-tag>
            <el-tag v-else :type="statusTag(row.aiStatus)">{{ statusLabel(row.aiStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('review.recognizeSummary')" min-width="260">
          <template #default="{ row }">
            <span v-if="row.aiResult">{{ resultSummary(row) }}</span>
            <span v-else class="muted">{{ $t('review.notRecognized') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.action')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              v-hasPermi="['live:review:edit']"
              link
              type="primary"
              :icon="row._recognizing ? 'Loading' : 'MagicStick'"
              :loading="row._recognizing"
              :disabled="row.aiStatus === '2' || row.aiStatus === '4' || row._recognizing"
              @click="handleMock(row)"
            >
              {{ row._recognizing ? $t('review.recognizing') + '...' : $t('review.aiRecognize') }}
            </el-button>
            <el-button
              v-hasPermi="['live:review:edit']"
              link
              type="primary"
              icon="Edit"
              :disabled="!row.aiResult || row.aiStatus === '2'"
              @click="openEditor(row)"
            >
              {{ $t('review.correct') }}
            </el-button>
            <el-button
              v-hasPermi="['live:review:confirm']"
              link
              type="success"
              icon="Check"
              :disabled="row.aiStatus !== '1'"
              @click="handleConfirm(row)"
            >
              {{ $t('review.confirm入库') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="loadList" />
    </el-card>

    <el-dialog v-model="editor.open" :title="$t('review.correctTitle')" width="860px" append-to-body>
      <div v-if="editor.row" class="editor-meta">
        <span>{{ editor.row.bizDate }}</span>
        <span>{{ editor.row.stageName }}</span>
        <span>{{ typeLabel(editor.row.uploadType) }}</span>
      </div>

      <template v-if="editor.form.type === 'gift'">
        <el-table :data="editor.form.items" border>
          <el-table-column :label="$t('review.rank')" width="90">
            <template #default="{ row }">
              <el-input-number v-model="row.rankNo" :min="1" :controls="false" class="rank-input" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('review.nickname')" min-width="180">
            <template #default="{ row }">
              <el-input v-model="row.nickname" :placeholder="$t('review.nicknamePlaceholder')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('review.badge')" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.badge" :placeholder="$t('review.badgePlaceholder')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('review.xu')" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.xu" :min="0" :controls="false" class="xu-input" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.action')" width="80" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" icon="Delete" @click="removeItem($index)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button class="add-row-btn" icon="Plus" @click="addItem">{{ $t('review.addCustomer') }}</el-button>
      </template>

      <template v-else-if="editor.form.type === 'chat'">
        <div v-for="(item, idx) in editor.form.items" :key="idx" class="chat-customer-block">
          <div class="chat-customer-header">
            <el-input v-model="item.nickname" :placeholder="$t('review.nicknamePlaceholder')" style="width: 220px" />
            <el-input v-model="item.badge" :placeholder="$t('review.badgeOptional')" style="width: 160px; margin-left: 8px" />
            <el-button link type="danger" icon="Delete" style="margin-left: 8px" @click="removeItem(idx)">{{ $t('common.delete') }}</el-button>
          </div>
          <el-table :data="item.messages" border size="small" style="margin-top: 6px">
            <el-table-column :label="$t('review.sender')" width="100">
              <template #default="{ row: msg }">
                <el-select v-model="msg.sender" size="small">
                  <el-option :label="$t('review.customer')" value="customer" />
                  <el-option :label="$t('review.streamer')" value="streamer" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$t('review.msgType')" width="90">
              <template #default="{ row: msg }">
                <el-select v-model="msg.messageType" size="small">
                  <el-option :label="$t('review.text')" value="text" />
                  <el-option :label="$t('review.video')" value="video" />
                  <el-option :label="$t('review.image')" value="image" />
                  <el-option :label="$t('review.audio')" value="audio" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$t('review.msgContent')">
              <template #default="{ row: msg }">
                <el-input v-model="msg.content" size="small" :placeholder="$t('review.chatContentPlaceholder')" />
              </template>
            </el-table-column>
            <el-table-column label="" width="60" align="center">
              <template #default="{ $index: mi }">
                <el-button link type="danger" icon="Delete" size="small" @click="item.messages.splice(mi, 1)" />
              </template>
            </el-table-column>
          </el-table>
          <el-button link type="primary" icon="Plus" size="small" style="margin-top: 4px" @click="item.messages.push({ sender: 'customer', messageType: 'text', content: '' })">{{ $t('review.addMessage') }}</el-button>
        </div>
        <el-button class="add-row-btn" icon="Plus" @click="addItem">{{ $t('review.addCustomer') }}</el-button>
      </template>

      <template v-else>
        <el-form label-width="90px">
          <el-form-item :label="$t('review.totalXu')">
            <el-input-number v-model="editor.form.totalXu" :min="0" :controls="false" class="report-xu-input" />
          </el-form-item>
          <el-form-item :label="$t('review.rawText')">
            <el-input v-model="editor.form.rawText" type="textarea" :rows="5" :placeholder="$t('review.rawTextPlaceholder')" />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="editor.open = false">取消</el-button>
        <el-button type="primary" @click="handleSave(false)">保存校正</el-button>
        <el-button type="success" @click="handleSave(true)">保存并入库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="merge.open" title="合并客户" width="500px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="主播">
          <el-select v-model="merge.streamerId" filterable placeholder="选择主播" style="width: 100%" @change="loadCustomersForMerge">
            <el-option v-for="s in streamers" :key="s.streamerId" :label="s.stageName" :value="s.streamerId" />
          </el-select>
        </el-form-item>
        <el-form-item label="主客户">
          <el-select v-model="merge.primaryId" filterable placeholder="选择保留的客户" style="width: 100%">
            <el-option v-for="c in mergeCustomers" :key="c.customerId" :label="c.nickname" :value="c.customerId" />
          </el-select>
        </el-form-item>
        <el-form-item label="副客户">
          <el-select v-model="merge.secondaryId" filterable placeholder="选择被合并的客户" style="width: 100%">
            <el-option v-for="c in mergeCustomers" :key="c.customerId" :label="c.nickname" :value="c.customerId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="merge.open = false">取消</el-button>
        <el-button type="primary" @click="handleMerge">确认合并</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LiveReview">
import { Loading } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { listReview, recognizeUpload, saveReviewResult, confirmReview } from '@/api/live/review'
import { listStreamers } from '@/api/live/upload'
import { listCustomers, mergeCustomers as mergeCustomersApi } from '@/api/live/customer'

const { proxy } = getCurrentInstance()
const { t } = useI18n()
const baseApi = import.meta.env.VITE_APP_BASE_API

const loading = ref(false)
const recognizingId = ref(null)
const rows = ref([])
const total = ref(0)
const streamers = ref([])
const dateRange = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  streamerId: undefined,
  uploadType: undefined,
  aiStatus: undefined
})

const editor = reactive({
  open: false,
  row: null,
  form: {
    type: 'gift',
    items: [],
    totalXu: 0,
    rawText: ''
  }
})

const customers = ref([])
const mergeCustomers = ref([])
const merge = reactive({
  open: false,
  streamerId: undefined,
  primaryId: undefined,
  secondaryId: undefined
})

const typeOptions = computed(() => [
  { value: '1', label: t('upload.giftScreenshot') },
  { value: '2', label: t('upload.chatScreenshot') },
  { value: '3', label: t('upload.reportText') }
])

const statusOptions = computed(() => [
  { value: '0', label: t('upload.pending') },
  { value: '1', label: t('upload.recognized') },
  { value: '2', label: t('review.corrected') },
  { value: '3', label: t('upload.failed') },
  { value: '4', label: t('review.recognizing') }
])

function typeLabel(v) {
  return (typeOptions.value.find(t => t.value === v) || {}).label || v
}

function statusLabel(v) {
  return (statusOptions.value.find(s => s.value === v) || {}).label || v
}

function statusTag(v) {
  return { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'primary' }[v] || 'info'
}

function parseResult(row) {
  if (row.aiResult) {
    try {
      return JSON.parse(row.aiResult)
    } catch (e) {
      return defaultResult(row)
    }
  }
  return defaultResult(row)
}

function defaultResult(row) {
  if (row.uploadType === '1') {
    return { type: 'gift', items: [] }
  }
  if (row.uploadType === '2') {
    return { type: 'chat', items: [] }
  }
  return { type: 'report', totalXu: 0, rawText: row.rawText || '' }
}

function resultSummary(row) {
  const result = parseResult(row)
  if (row.uploadType === '3') {
    return `总流水 ${Number(result.totalXu || 0).toLocaleString()}，可校正汇报文本`
  }
  const items = Array.isArray(result.items) ? result.items : []
  if (!items.length) {
    return '暂无明细'
  }
  return items.map(item => {
    if (row.uploadType === '1') {
      return `${item.nickname || '未命名'} ${Number(item.xu || 0).toLocaleString()}虚拟币`
    }
    const msgs = Array.isArray(item.messages) ? item.messages : []
    const preview = msgs.slice(0, 2).map(m => m.content || '').filter(Boolean).join('; ')
    return `${item.nickname || '未命名'}(${msgs.length}条)${preview ? ': ' + preview : ''}`
  }).join('、')
}

function buildParams() {
  const p = { ...query }
  if (dateRange.value && dateRange.value.length === 2) {
    p.beginDate = dateRange.value[0]
    p.endDate = dateRange.value[1]
  }
  return p
}

async function loadList() {
  loading.value = true
  try {
    const res = await listReview(buildParams())
    rows.value = res.rows
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  query.pageNum = 1
  loadList()
}

function resetQuery() {
  dateRange.value = []
  query.streamerId = undefined
  query.uploadType = undefined
  query.aiStatus = undefined
  handleQuery()
}

function handleMock(row) {
  recognizingId.value = row.uploadId
  row._recognizing = true
  recognizeUpload(row.uploadId).then(() => {
    proxy.$modal.msgSuccess('AI识别完成')
    loadList()
  }).catch(() => {}).finally(() => {
    recognizingId.value = null
    row._recognizing = false
  })
}

function openEditor(row) {
  const result = parseResult(row)
  editor.row = row
  editor.form.type = result.type || defaultResult(row).type
  editor.form.items = Array.isArray(result.items) ? result.items.map((item, index) => ({
    rankNo: Number(item.rankNo || index + 1),
    nickname: item.nickname || '',
    badge: item.badge || '',
    xu: Number(item.xu || 0),
    messageCount: Number(item.messageCount || 0),
    messages: Array.isArray(item.messages) ? item.messages.map(m => ({
      sender: m.sender || 'customer',
      messageType: m.messageType || 'text',
      content: m.content || ''
    })) : []
  })) : []
  editor.form.totalXu = Number(result.totalXu || 0)
  editor.form.rawText = result.rawText || row.rawText || ''
  editor.open = true
}

function addItem() {
  editor.form.items.push({
    rankNo: editor.form.items.length + 1,
    nickname: '',
    badge: '',
    xu: 0,
    messageCount: 0,
    messages: editor.form.type === 'chat' ? [{ sender: 'customer', messageType: 'text', content: '' }] : []
  })
}

function removeItem(index) {
  editor.form.items.splice(index, 1)
}

function buildAiResult() {
  if (editor.form.type === 'report') {
    return JSON.stringify({
      type: 'report',
      totalXu: Number(editor.form.totalXu || 0),
      rawText: editor.form.rawText || ''
    })
  }
  const items = editor.form.items
    .filter(item => item.nickname && item.nickname.trim())
    .map((item, index) => {
      const base = {
        rankNo: Number(item.rankNo || index + 1),
        nickname: item.nickname.trim(),
        badge: item.badge || '',
        xu: Number(item.xu || 0),
        messageCount: Number(item.messageCount || 0),
        confidence: 'manual'
      }
      if (editor.form.type === 'chat' && Array.isArray(item.messages)) {
        base.messages = item.messages.filter(m => m.content && m.content.trim()).map(m => ({
          sender: m.sender,
          messageType: m.messageType || 'text',
          content: m.content
        }))
      }
      return base
    })
  return JSON.stringify({
    type: editor.form.type,
    items
  })
}

function handleSave(confirmAfter) {
  const row = editor.row
  if (!row) {
    return
  }
  if (editor.form.type !== 'report' && !editor.form.items.some(item => item.nickname && item.nickname.trim())) {
    proxy.$modal.msgWarning('请至少填写一个客户昵称')
    return
  }
  saveReviewResult(row.uploadId, buildAiResult()).then(() => {
    if (!confirmAfter) {
      proxy.$modal.msgSuccess('校正结果已保存')
      editor.open = false
      loadList()
      return
    }
    confirmReview(row.uploadId).then(() => {
      proxy.$modal.msgSuccess('已确认入库')
      editor.open = false
      loadList()
    })
  })
}

function handleConfirm(row) {
  proxy.$modal.confirm('确认将当前识别结果写入统计表吗？').then(() => confirmReview(row.uploadId)).then(() => {
    proxy.$modal.msgSuccess('已确认入库')
    loadList()
  }).catch(() => {})
}

listStreamers().then(res => {
  streamers.value = res.data || []
})
loadCustomers()
loadList()

function loadCustomers() {
  listCustomers({ pageSize: 100 }).then(res => {
    customers.value = res.rows || []
  })
}

function openMergeDialog() {
  merge.streamerId = undefined
  merge.primaryId = undefined
  merge.secondaryId = undefined
  mergeCustomers.value = []
  merge.open = true
}

function loadCustomersForMerge() {
  if (!merge.streamerId) {
    mergeCustomers.value = []
    return
  }
  listCustomers({ streamerId: merge.streamerId, pageSize: 100 }).then(res => {
    mergeCustomers.value = res.rows || []
  })
}

function handleMerge() {
  if (!merge.primaryId || !merge.secondaryId) {
    proxy.$modal.msgWarning('请选择主客户和副客户')
    return
  }
  if (merge.primaryId === merge.secondaryId) {
    proxy.$modal.msgWarning('不能合并同一个客户')
    return
  }
  const primary = customers.value.find(c => c.customerId === merge.primaryId)
  const secondary = customers.value.find(c => c.customerId === merge.secondaryId)
  proxy.$modal.confirm(`确认将 ${secondary.nickname} 合并到 ${primary.nickname} 吗？合并后不可撤销。`).then(() => {
    mergeCustomersApi(merge.primaryId, merge.secondaryId).then(() => {
      proxy.$modal.msgSuccess('合并成功')
      merge.open = false
      loadCustomers()
      loadList()
    })
  }).catch(() => {})
}
</script>

<style scoped>
.live-review-page :deep(.el-card__body) {
  padding-bottom: 12px;
}

.thumb {
  width: 76px;
  height: 76px;
  border-radius: 6px;
}

.report-text {
  display: inline-block;
  max-width: 90px;
  color: #606266;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.muted {
  color: #909399;
}

.editor-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
  color: #606266;
  font-size: 13px;
}

.rank-input {
  width: 64px;
}

.xu-input {
  width: 110px;
}

.report-xu-input {
  width: 220px;
}

.add-row-btn {
  margin-top: 12px;
}

.chat-customer-block {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
}

.chat-customer-header {
  display: flex;
  align-items: center;
}
</style>
