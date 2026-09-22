<template>
  <el-dialog :model-value="modelValue" title="确认达人商品绑定导入" width="92%" append-to-body destroy-on-close
    @update:model-value="emit('update:modelValue', $event)">
    <el-alert type="info" :closable="false" class="import-note">
      <template #title>已载入 {{ draftRows.length }} 行；可直接修改错误内容。提交时重新校验全部行，全部通过才会一次性录入；相同达人与商品的现有配置会更新。</template>
    </el-alert>
    <div class="import-summary">
      <el-tag type="success">共 {{ draftRows.length }} 行</el-tag>
      <el-tag v-if="issueCount" type="danger">上次校验错误 {{ issueCount }} 行</el-tag>
      <el-tag v-if="dirtyCount" type="warning">已修改待复核 {{ dirtyCount }} 行</el-tag>
    </div>
    <el-table :data="draftRows" border max-height="60vh" :row-class-name="rowClassName" row-key="excelRow">
      <el-table-column prop="excelRow" label="Excel行" width="78" fixed="left" />
      <el-table-column label="SKU" width="160">
        <template #default="{ row }">
          <el-input v-model="row.sku" placeholder="请输入SKU" @input="productKeyChanged(row)" />
          <small v-if="row.excelSku && row.excelSku !== row.sku">原Excel：{{ row.excelSku }}</small>
        </template>
      </el-table-column>
      <el-table-column label="商品类型" width="150">
        <template #default="{ row }">
          <el-select v-model="row.productType" clearable placeholder="请选择类型" style="width:100%" @change="productKeyChanged(row)">
            <el-option label="成品商品" value="FINISHED" />
          </el-select>
          <small v-if="row.excelProductTypeName && row.excelProductTypeName !== typeName(row.productType)">原Excel：{{ row.excelProductTypeName }}</small>
        </template>
      </el-table-column>
      <el-table-column label="确认商品档案" width="300">
        <template #default="{ row }">
          <el-select v-model="row.productId" filterable clearable placeholder="选择正确商品" style="width:100%"
            @change="productChanged(row)">
            <el-option v-for="product in products" :key="product.productId" :value="product.productId"
              :label="`${product.sku} · ${product.productName}（${typeName(product.productType)}）`" />
          </el-select>
          <small v-if="row.productId">{{ row.productName }} · {{ typeName(row.productType) }}</small>
        </template>
      </el-table-column>
      <el-table-column label="直播价" width="115"><template #default="{ row }"><el-input v-model="row.fixedUnitPrice" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="佣金率 %" width="105"><template #default="{ row }"><el-input v-model="row.commissionPercent" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="平台率 %" width="105"><template #default="{ row }"><el-input v-model="row.platformPercent" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="税率 %" width="95"><template #default="{ row }"><el-input v-model="row.taxPercent" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="包装费" width="105"><template #default="{ row }"><el-input v-model="row.packFee" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="物流费" width="105"><template #default="{ row }"><el-input v-model="row.shipFee" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="鉴定费" width="105"><template #default="{ row }"><el-input v-model="row.certFee" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-select v-model="row.bindingStatus" @change="markDirty(row)"><el-option label="启用" value="0" /><el-option label="停用" value="1" /></el-select></template></el-table-column>
      <el-table-column label="备注/改价原因" width="190"><template #default="{ row }"><el-input v-model="row.bindingRemark" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column label="校验结果" min-width="260" fixed="right">
        <template #default="{ row }">
          <template v-if="row.errors?.length">
            <div v-for="(error, index) in row.errors" :key="index" class="row-error">{{ error }}</div>
            <small v-if="row.dirty">已修改，提交时重新校验</small>
          </template>
          <el-tag v-else-if="row.dirty" type="warning">待复核</el-tag>
          <el-tag v-else type="success">通过</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!draftRows.length" @click="emit('submit', draftRows)">提交并重新校验</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { jewelryProductType } from '@/utils/jewelryProduct'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  rows: { type: Array, default: () => [] },
  products: { type: Array, default: () => [] },
  saving: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'submit'])
const draftRows = ref([])
const issueCount = computed(() => draftRows.value.filter(row => row.errors?.length).length)
const dirtyCount = computed(() => draftRows.value.filter(row => row.dirty).length)
const typeName = type => jewelryProductType(type)?.label || type || '—'

watch(() => props.rows, rows => {
  draftRows.value = (rows || []).map(row => ({ ...row, errors: [...(row.errors || [])], dirty: false }))
}, { immediate: true })

function markDirty(row) { row.dirty = true }
function rowClassName({ row }) { return row.errors?.length ? 'import-error-row' : '' }
function productChanged(row) {
  const product = props.products.find(item => Number(item.productId) === Number(row.productId))
  row.sku = product?.sku || ''
  row.productName = product?.productName || ''
  row.productType = product?.productType || ''
  markDirty(row)
}
function productKeyChanged(row) {
  const sku = String(row.sku || '').trim().toUpperCase()
  const product = props.products.find(item => String(item.sku || '').trim().toUpperCase() === sku && item.productType === row.productType)
  row.productId = product?.productId || null
  row.productName = product?.productName || ''
  markDirty(row)
}
</script>

<style scoped>
.import-note { margin-bottom: 12px; }
.import-summary { display: flex; gap: 8px; margin-bottom: 12px; }
.row-error { color: #d03050; line-height: 1.5; }
small { color: #909399; }
:deep(.import-error-row) { background: #fff8f8; }
</style>
