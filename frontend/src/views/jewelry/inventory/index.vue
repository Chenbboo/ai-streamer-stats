<template>
  <div class="app-container">
    <el-form inline>
      <el-form-item><el-input v-model="query.docNo" placeholder="盘点单号" clearable /></el-form-item>
      <el-form-item>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="load">查询</el-button></el-form-item>
    </el-form>

    <el-button
      v-hasPermi="['jewelry:document:add']"
      type="primary"
      plain
      icon="Plus"
      class="mb8"
      @click="open"
    >
      新建盘点单
    </el-button>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="docNo" label="盘点单号" width="190" />
      <el-table-column prop="bizDate" label="盘点日期" width="110" />
      <el-table-column prop="totalQty" label="差异件数" width="100" align="right" />
      <el-table-column prop="creatorName" label="制单人" width="110" />
      <el-table-column label="审核员" width="110"><template #default="{ row }">{{ row.firstReviewerName || '—' }}</template></el-table-column>
      <el-table-column label="管理员" width="110"><template #default="{ row }">{{ row.secondReviewerName || '—' }}</template></el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ documentStatusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="盘点说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="view(row)">查看</el-button>
          <el-button
            v-if="['DRAFT', 'REJECTED'].includes(row.status)"
            v-hasPermi="['jewelry:document:edit']"
            link
            type="primary"
            @click="edit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            v-hasPermi="['jewelry:document:submit']"
            link
            type="success"
            @click="submit(row)"
          >
            提交
          </el-button>
          <el-button
            v-if="row.status === 'PENDING_FIRST'"
            v-hasPermi="['jewelry:document:withdraw']"
            link
            type="warning"
            @click="withdraw(row)"
          >
            撤回
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination
      v-show="total > 0"
      v-model:page="query.pageNum"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="load"
    />

    <el-dialog
      v-model="dialog"
      :title="readonly ? '查看盘点单' : (form.documentId ? '编辑盘点单' : '新建盘点单')"
      width="94%"
      top="4vh"
      destroy-on-close
    >
      <div class="count-head">
        <el-date-picker
          v-model="form.bizDate"
          value-format="YYYY-MM-DD"
          :disabled="readonly"
          placeholder="盘点日期"
        />
        <el-input
          v-model="form.remark"
          :disabled="readonly"
          placeholder="盘点说明"
          maxlength="200"
          show-word-limit
        />
        <el-input v-model="itemKeyword" clearable placeholder="筛选 SKU 或商品名称" />
      </div>

      <el-alert
        v-if="!readonly"
        title="系统只保存存在差异的商品；提交后先由审核员初审，再由管理员复核，复核通过后才调整库存。审批中如账面库存发生变化，需要重新编辑盘点结果。"
        type="info"
        :closable="false"
        show-icon
        class="mb12"
      />

      <el-table :data="visibleItems" border height="52vh" row-key="productId">
        <el-table-column prop="skuSnapshot" label="SKU" width="150" fixed />
        <el-table-column prop="productNameSnapshot" label="商品名称" min-width="180" fixed />
        <el-table-column prop="systemQty" label="系统库存" width="105" align="right" />
        <el-table-column label="实盘库存" width="150" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.countedQty" :min="0" :disabled="readonly" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="差异数量" width="105" align="right">
          <template #default="{ row }">
            <span :class="differenceClass(row)">{{ difference(row) > 0 ? '+' : '' }}{{ difference(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="盘盈核定成本" width="160">
          <template #default="{ row }">
            <el-input-number
              v-model="row.unitCost"
              :min="0"
              :precision="2"
              :disabled="readonly || difference(row) <= 0"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="调整原因" min-width="210">
          <template #default="{ row }">
            <el-input
              v-model="row.lineReason"
              :disabled="readonly || difference(row) === 0"
              placeholder="有差异时必填"
              maxlength="100"
            />
          </template>
        </el-table-column>
      </el-table>

      <div class="count-summary">
        <span>盘盈 <b class="positive">+{{ summary.gain }}</b> 件</span>
        <span>盘亏 <b class="negative">-{{ summary.loss }}</b> 件</span>
        <span>净差异 <b>{{ summary.net > 0 ? '+' : '' }}{{ summary.net }}</b> 件</span>
        <span>差异 SKU <b>{{ summary.lines }}</b> 个</span>
      </div>

      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JewelryInventory">
import {
  getJewelryDocument,
  listJewelryDocuments,
  listJewelryStock,
  saveJewelryDocument,
  submitJewelryDocument,
  withdrawJewelryDocument
} from '@/api/jewelry/erp'

const { proxy } = getCurrentInstance()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const readonly = ref(false)
const itemKeyword = ref('')
const statuses = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_FIRST', label: '待审核' },
  { value: 'PENDING_SECOND', label: '待审核' },
  { value: 'POSTED', label: '已入账' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'REVERSED', label: '已红冲' }
]
const query = reactive({ pageNum: 1, pageSize: 10, docNo: '', status: '', docType: 'STOCK_ADJUST' })
const blankForm = () => ({
  documentId: null,
  docType: 'STOCK_ADJUST',
  bizDate: new Date().toISOString().slice(0, 10),
  remark: '',
  returnReason: '库存盘点差异调整',
  platformRate: 0,
  commissionRate: 0,
  taxRate: 0,
  items: []
})
const form = reactive(blankForm())

const difference = row => Number(row.countedQty || 0) - Number(row.systemQty || 0)
const differenceClass = row => difference(row) > 0 ? 'positive' : difference(row) < 0 ? 'negative' : ''
const visibleItems = computed(() => {
  const keyword = itemKeyword.value.trim().toLowerCase()
  if (!keyword) return form.items
  return form.items.filter(item =>
    `${item.skuSnapshot || ''} ${item.productNameSnapshot || ''}`.toLowerCase().includes(keyword)
  )
})
const summary = computed(() => form.items.reduce((result, item) => {
  const diff = difference(item)
  if (diff > 0) result.gain += diff
  if (diff < 0) result.loss += -diff
  if (diff !== 0) result.lines += 1
  result.net += diff
  return result
}, { gain: 0, loss: 0, net: 0, lines: 0 }))

const labelOfStatus = value => statuses.find(item => item.value === value)?.label || value
const documentStatusLabel = row => row.status === 'PENDING_SECOND' ? '待管理员复核'
  : row.status === 'PENDING_FIRST' ? '待审核员审核' : labelOfStatus(row.status)
const statusType = value => value === 'POSTED' ? 'success'
  : ['REJECTED', 'REVERSED'].includes(value) ? 'danger'
    : value === 'DRAFT' ? 'info' : 'warning'

async function load() {
  loading.value = true
  try {
    const response = await listJewelryDocuments(query)
    rows.value = response.rows || []
    total.value = response.total || 0
  } finally {
    loading.value = false
  }
}

async function open() {
  const response = await listJewelryStock({ pageNum: 1, pageSize: 1000 })
  Object.assign(form, blankForm())
  form.items = (response.rows || []).map(item => ({
    productId: item.productId,
    skuSnapshot: item.sku,
    productNameSnapshot: item.productName,
    systemQty: Number(item.onHandQty || 0),
    countedQty: Number(item.onHandQty || 0),
    adjustmentQty: 0,
    qty: 0,
    unitPrice: 0,
    unitCost: Number(item.avgCost || 0),
    packFee: 0,
    shipFee: 0,
    certFee: 0,
    lineReason: ''
  }))
  itemKeyword.value = ''
  readonly.value = false
  dialog.value = true
}

async function edit(row) {
  const detail = (await getJewelryDocument(row.documentId)).data
  Object.assign(form, detail)
  itemKeyword.value = ''
  readonly.value = false
  dialog.value = true
}

async function view(row) {
  const detail = (await getJewelryDocument(row.documentId)).data
  Object.assign(form, detail)
  itemKeyword.value = ''
  readonly.value = true
  dialog.value = true
}

async function save() {
  const changedItems = form.items.filter(item => difference(item) !== 0)
  if (!changedItems.length) {
    proxy.$modal.msgError('当前没有盘点差异，无需保存')
    return
  }
  const invalidReason = changedItems.find(item => !String(item.lineReason || '').trim())
  if (invalidReason) {
    proxy.$modal.msgError(`${invalidReason.productNameSnapshot} 需要填写调整原因`)
    return
  }
  const invalidCost = changedItems.find(item => difference(item) > 0 && Number(item.unitCost || 0) <= 0)
  if (invalidCost) {
    proxy.$modal.msgError(`${invalidCost.productNameSnapshot} 盘盈时需要填写核定单位成本`)
    return
  }
  await saveJewelryDocument({ ...form, items: changedItems })
  proxy.$modal.msgSuccess('盘点草稿已保存')
  dialog.value = false
  load()
}

async function submit(row) {
  await proxy.$modal.confirm(`确认提交盘点单 ${row.docNo}？`)
  await submitJewelryDocument(row.documentId)
  proxy.$modal.msgSuccess('已提交审批')
  load()
}

async function withdraw(row) {
  await proxy.$modal.confirm(`确认撤回盘点单 ${row.docNo}？`)
  await withdrawJewelryDocument(row.documentId)
  proxy.$modal.msgSuccess('已撤回')
  load()
}

load()
</script>

<style scoped>
.count-head {
  display: grid;
  grid-template-columns: 180px minmax(280px, 1fr) 240px;
  gap: 12px;
  margin-bottom: 12px;
}
.count-summary {
  display: flex;
  justify-content: flex-end;
  gap: 28px;
  padding: 14px 4px 0;
  color: #4b5563;
}
.positive {
  color: #15803d;
  font-weight: 700;
}
.negative {
  color: #c2413a;
  font-weight: 700;
}
.mb12 {
  margin-bottom: 12px;
}
@media (max-width: 900px) {
  .count-head {
    grid-template-columns: 1fr;
  }
  .count-summary {
    justify-content: flex-start;
    flex-wrap: wrap;
    gap: 10px 22px;
  }
}
</style>
