<template>
  <el-dialog :model-value="modelValue" :title="$tr(&quot;确认达人商品绑定导入&quot;)" width="92%" append-to-body destroy-on-close
    @update:model-value="emit('update:modelValue', $event)">
    <el-alert type="info" :closable="false" class="import-note">
      <template #title>{{ $tr("已载入 {0} 行；可直接修改错误内容。提交时重新校验全部行，全部通过后一次性建立新商品、新供应商档案并绑定；已有商品更新当前达人的配置，修改图片时会同步更新共用商品档案。", [draftRows.length]) }}</template>
    </el-alert>
    <div class="import-summary">
      <el-tag type="success">{{ $tr("共 {0} 行", [draftRows.length]) }}</el-tag>
      <el-tag v-if="issueCount" type="danger">{{ $tr("上次校验错误 {0} 行", [issueCount]) }}</el-tag>
      <el-tag v-if="dirtyCount" type="warning">{{ $tr("已修改待复核 {0} 行", [dirtyCount]) }}</el-tag>
    </div>
    <el-table :data="draftRows" border max-height="60vh" :row-class-name="rowClassName" row-key="excelRow">
      <el-table-column label="商品SKU" width="160">
        <template #default="{ row }">
          <el-input v-model="row.sku" :placeholder="$tr(&quot;请输入SKU&quot;)" @input="productKeyChanged(row)" />
          <small v-if="row.excelSku && row.excelSku !== row.sku">{{ $tr("原Excel：{0}", [row.excelSku]) }}</small>
        </template>
      </el-table-column>
      <el-table-column label="商品名称" width="300">
        <template #default="{ row }">
          <el-input v-model="row.productName" :disabled="!!row.productId" placeholder="新建商品时必填" @input="markDirty(row)" />
          <el-select v-model="row.productId" filterable clearable placeholder="匹配已有商品（可选）" style="width:100%; margin-top:5px"
            @change="productChanged(row)">
            <el-option v-for="product in products" :key="product.productId" :value="product.productId"
              :label="`${product.sku} · ${product.productName}（${typeName(product.productType)}）`" />
          </el-select>
          <small v-if="!row.productId && row.sku && ['FINISHED','GIFT'].includes(row.productType)">未匹配已有商品，将按本行信息建档</small>
        </template>
      </el-table-column>
      <el-table-column label="商品类型" width="150">
        <template #default="{ row }">
          <el-select v-model="row.productType" clearable :placeholder="$tr(&quot;请选择类型&quot;)" style="width:100%" @change="productKeyChanged(row)">
            <el-option :label="$tr(&quot;成品商品&quot;)" value="FINISHED" />
            <el-option :label="$tr(&quot;赠品商品&quot;)" value="GIFT" />
          </el-select>
          <small v-if="row.excelProductTypeName && row.excelProductTypeName !== typeName(row.productType)">{{ $tr("原Excel：{0}", [row.excelProductTypeName]) }}</small>
        </template>
      </el-table-column>
      <el-table-column label="供应商名称" width="280"><template #default="{ row }">
        <template v-if="row.newSupplier">
          <el-tag type="warning">待建档：{{ row.newSupplier.supplierCode }} · {{ row.newSupplier.supplierName }}</el-tag>
          <div class="supplier-actions"><el-button link type="primary" @click="openNewSupplier(row)">修改</el-button><el-button link @click="clearNewSupplier(row)">选择已有</el-button></div>
        </template>
        <template v-else>
          <el-select v-model="row.preferredSupplierId" filterable remote clearable :remote-method="searchSuppliers"
            :placeholder="row.preferredSupplierName || $tr('选择供应商')" style="width:100%" @change="supplierChanged(row)">
            <el-option v-for="supplier in supplierOptions" :key="supplier.supplierId"
              :label="`${supplier.supplierCode} · ${supplier.supplierName}`" :value="supplier.supplierId" />
            <el-option v-if="row.preferredSupplierId && !supplierOptions.some(s => Number(s.supplierId) === Number(row.preferredSupplierId))"
              :key="row.preferredSupplierId" :label="row.preferredSupplierName || String(row.preferredSupplierId)" :value="row.preferredSupplierId" />
          </el-select>
          <el-button v-if="canCreateSupplier" link type="primary" @click="openNewSupplier(row)">新建供应商</el-button>
        </template>
        <small v-if="row.excelSupplierName && (!row.preferredSupplierId || row.excelSupplierName !== row.preferredSupplierName) && !row.newSupplier">
          {{ $tr("原Excel：{0}", [row.excelSupplierName]) }}
        </small>
      </template></el-table-column>
      <el-table-column label="直播成交价" width="120"><template #default="{ row }"><el-input v-model="row.fixedUnitPrice" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="商品成本价" width="120"><template #default="{ row }"><el-input v-model="row.unitCost" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;采购单价&quot;)" width="120"><template #default="{ row }"><el-input v-model="row.referencePurchasePrice" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="达人佣金率(%)" width="145"><template #default="{ row }"><el-input v-model="row.commissionPercent" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="平台扣点率(%)" width="145"><template #default="{ row }"><el-input v-model="row.platformPercent" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="税率(%)" width="100"><template #default="{ row }"><el-input v-model="row.taxPercent" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;包装费&quot;)" width="105"><template #default="{ row }"><el-input v-model="row.packFee" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;物流费&quot;)" width="105"><template #default="{ row }"><el-input v-model="row.shipFee" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;鉴定费&quot;)" width="105"><template #default="{ row }"><el-input v-model="row.certFee" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="单位" width="100"><template #default="{ row }"><el-input v-model="row.unit" :disabled="!!row.productId" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="图片" width="175"><template #default="{ row }">
        <image-upload v-model="row.imageUrls" :limit="1" :file-size="8"
          :disabled="!!row.productId && !canEditProductImage" @update:model-value="imageChanged(row)" />
        <small v-if="row.productId && canEditProductImage">{{ $tr("修改或删除后将同步更新共用商品档案") }}</small>
        <small v-else-if="row.productId">{{ $tr("无商品图片修改权限") }}</small>
      </template></el-table-column>
      <el-table-column label="备注" width="190"><template #default="{ row }"><el-input v-model="row.bindingRemark" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column prop="excelRow" :label="$tr(&quot;Excel行&quot;)" width="78" />
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-select v-model="row.bindingStatus" @change="markDirty(row)"><el-option :label="$tr(&quot;启用&quot;)" value="0" /><el-option :label="$tr(&quot;停用&quot;)" value="1" /></el-select></template></el-table-column>
      <el-table-column :label="$tr(&quot;校验结果&quot;)" min-width="260" fixed="right">
        <template #default="{ row }">
          <template v-if="row.errors?.length">
            <div v-for="(error, index) in row.errors" :key="index" class="row-error">{{ error }}</div>
            <small v-if="row.dirty">{{ $tr("已修改，提交时重新校验") }}</small>
          </template>
          <el-tag v-else-if="row.dirty" type="warning">{{ $tr("待复核") }}</el-tag>
          <el-tag v-else type="success">{{ $tr("通过") }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="newSupplierDialog" title="新建供应商档案" width="560px" append-to-body>
      <el-alert title="这里只填写待建档信息；提交整张确认表并通过全部校验后，才会建立供应商档案。" type="info" :closable="false" class="import-note" />
      <el-form label-width="105px">
        <el-form-item label="供应商编码" required><el-input v-model.trim="supplierDraft.supplierCode" maxlength="32" /></el-form-item>
        <el-form-item label="供应商名称" required><el-input v-model.trim="supplierDraft.supplierName" maxlength="128" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model.trim="supplierDraft.contactName" maxlength="64" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model.trim="supplierDraft.contactPhone" maxlength="32" /></el-form-item>
        <el-form-item label="结算方式"><el-input v-model.trim="supplierDraft.settlementType" maxlength="64" /></el-form-item>
        <el-form-item label="地址"><el-input v-model.trim="supplierDraft.address" maxlength="255" /></el-form-item>
        <el-form-item><el-checkbox v-model="applySameSupplierName">同时应用到未选供应商的同名行</el-checkbox></el-form-item>
      </el-form>
      <template #footer><el-button @click="newSupplierDialog=false">取消</el-button><el-button type="primary" @click="applyNewSupplier">应用到确认表</el-button></template>
    </el-dialog>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ $tr("取消") }}</el-button>
      <el-button type="primary" :loading="saving" :disabled="!draftRows.length" @click="emit('submit', draftRows)">{{ $tr("提交并重新校验") }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { listJewelrySuppliers } from '@/api/jewelry/erp'
import { jewelryProductType } from '@/utils/jewelryProduct'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  rows: { type: Array, default: () => [] },
  products: { type: Array, default: () => [] },
  suppliers: { type: Array, default: () => [] },
  canCreateSupplier: { type: Boolean, default: false },
  canEditProductImage: { type: Boolean, default: false },
  saving: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'submit'])
const draftRows = ref([])
const supplierOptions = ref([])
const newSupplierDialog = ref(false)
const supplierDraft = reactive({ supplierCode: '', supplierName: '', contactName: '', contactPhone: '', settlementType: '', address: '' })
const activeSupplierRow = ref(null)
const applySameSupplierName = ref(true)
let supplierSearchSequence = 0
const issueCount = computed(() => draftRows.value.filter(row => row.errors?.length).length)
const dirtyCount = computed(() => draftRows.value.filter(row => row.dirty).length)
const typeName = type => jewelryProductType(type)?.label || type || '—'

watch(() => props.rows, rows => {
  draftRows.value = (rows || []).map(row => ({
    ...row,
    imageChanged: row.imageChanged === true || String(row.imageChanged).toLowerCase() === 'true',
    errors: [...(row.errors || [])],
    dirty: false
  }))
}, { immediate: true })
watch(() => props.suppliers, rows => { supplierOptions.value = rows || [] }, { immediate: true })

function markDirty(row) { row.dirty = true }
function imageChanged(row) { row.imageError = ''; row.imageChanged = true; markDirty(row) }
async function searchSuppliers(keyword) {
  const sequence = ++supplierSearchSequence
  if (!String(keyword || '').trim()) { supplierOptions.value = props.suppliers; return }
  const result = await listJewelrySuppliers({ pageNum: 1, pageSize: 100, status: '0', keyword: keyword.trim() })
  if (sequence === supplierSearchSequence) supplierOptions.value = result.rows || []
}
function supplierChanged(row) {
  delete row.newSupplier
  const supplier = supplierOptions.value.find(item => Number(item.supplierId) === Number(row.preferredSupplierId))
    || props.suppliers.find(item => Number(item.supplierId) === Number(row.preferredSupplierId))
  row.preferredSupplierName = supplier?.supplierName || (row.preferredSupplierId ? row.preferredSupplierName : '')
  markDirty(row)
}
function openNewSupplier(row) {
  activeSupplierRow.value = row
  Object.assign(supplierDraft, {
    supplierCode: '', supplierName: row.preferredSupplierName || row.excelSupplierName || '',
    contactName: '', contactPhone: '', settlementType: '', address: ''
  }, row.newSupplier || {})
  applySameSupplierName.value = true
  newSupplierDialog.value = true
}
function clearNewSupplier(row) {
  delete row.newSupplier
  row.preferredSupplierId = null
  markDirty(row)
}
function applyNewSupplier() {
  const code = supplierDraft.supplierCode.trim()
  const name = supplierDraft.supplierName.trim()
  if (!code || !name) return ElMessage.error('请填写供应商编码和名称')
  const target = activeSupplierRow.value
  if (!target) return
  const previousName = String(target.preferredSupplierName || target.excelSupplierName || '').trim()
  const draft = { ...supplierDraft, supplierCode: code, supplierName: name }
  const targets = applySameSupplierName.value && previousName
    ? draftRows.value.filter(row => row === target || (!row.preferredSupplierId && !row.newSupplier && String(row.preferredSupplierName || row.excelSupplierName || '').trim() === previousName))
    : [target]
  targets.forEach(row => {
    row.preferredSupplierId = null
    row.preferredSupplierName = name
    row.newSupplier = { ...draft }
    markDirty(row)
  })
  newSupplierDialog.value = false
}
function rowClassName({ row }) { return row.errors?.length ? 'import-error-row' : '' }
function productChanged(row) {
  const product = props.products.find(item => Number(item.productId) === Number(row.productId))
  if (product) row.imageError = ''
  row.sku = product?.sku || ''
  if (product) row.productName = product.productName || ''
  row.productType = product?.productType || ''
  if (product) { row.unit=product.unit||''; row.imageUrls=product.imageUrls||''; row.imageChanged=false }
  if (!product) { row.productName=''; row.unit=''; row.imageUrls=''; row.imageChanged=false }
  if (row.unitCost === null || row.unitCost === undefined || row.unitCost === '') row.unitCost = product?.avgCost ?? ''
  markDirty(row)
}
function productKeyChanged(row) {
  const hadExisting = !!row.productId
  const sku = String(row.sku || '').trim().toUpperCase()
  const product = props.products.find(item => String(item.sku || '').trim().toUpperCase() === sku && item.productType === row.productType)
  if (product) row.imageError = ''
  row.productId = product?.productId || null
  if (product) row.productName = product.productName || ''
  if (product) { row.unit=product.unit||''; row.imageUrls=product.imageUrls||''; row.imageChanged=false }
  if (!product && hadExisting) { row.productName=''; row.unit=''; row.imageUrls=''; row.imageChanged=false }
  if (row.unitCost === null || row.unitCost === undefined || row.unitCost === '') row.unitCost = product?.avgCost ?? ''
  markDirty(row)
}
</script>

<style scoped>
.import-note { margin-bottom: 12px; }
.import-summary { display: flex; gap: 8px; margin-bottom: 12px; }
.row-error { color: #d03050; line-height: 1.5; }
.supplier-actions { margin-top: 4px; }
small { color: #909399; }
:deep(.import-error-row) { background: #fff8f8; }
</style>
