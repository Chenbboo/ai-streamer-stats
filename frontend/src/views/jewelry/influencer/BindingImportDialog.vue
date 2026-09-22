<template>
  <el-dialog :model-value="modelValue" :title="$tr(&quot;确认达人商品绑定导入&quot;)" width="92%" append-to-body destroy-on-close
    @update:model-value="emit('update:modelValue', $event)">
    <el-alert type="info" :closable="false" class="import-note">
      <template #title>{{ $tr("已载入 {0} 行；可直接修改错误内容。提交时重新校验全部行，全部通过才会一次性录入；相同达人与商品的现有配置会更新。", [draftRows.length]) }}</template>
    </el-alert>
    <div class="import-summary">
      <el-tag type="success">{{ $tr("共 {0} 行", [draftRows.length]) }}</el-tag>
      <el-tag v-if="issueCount" type="danger">{{ $tr("上次校验错误 {0} 行", [issueCount]) }}</el-tag>
      <el-tag v-if="dirtyCount" type="warning">{{ $tr("已修改待复核 {0} 行", [dirtyCount]) }}</el-tag>
    </div>
    <el-table :data="draftRows" border max-height="60vh" :row-class-name="rowClassName" row-key="excelRow">
      <el-table-column prop="excelRow" :label="$tr(&quot;Excel行&quot;)" width="78" fixed="left" />
      <el-table-column label="SKU" width="160">
        <template #default="{ row }">
          <el-input v-model="row.sku" :placeholder="$tr(&quot;请输入SKU&quot;)" @input="productKeyChanged(row)" />
          <small v-if="row.excelSku && row.excelSku !== row.sku">{{ $tr("原Excel：{0}", [row.excelSku]) }}</small>
        </template>
      </el-table-column>
      <el-table-column :label="$tr(&quot;商品类型&quot;)" width="150">
        <template #default="{ row }">
          <el-select v-model="row.productType" clearable :placeholder="$tr(&quot;请选择类型&quot;)" style="width:100%" @change="productKeyChanged(row)">
            <el-option :label="$tr(&quot;成品商品&quot;)" value="FINISHED" />
          </el-select>
          <small v-if="row.excelProductTypeName && row.excelProductTypeName !== typeName(row.productType)">{{ $tr("原Excel：{0}", [row.excelProductTypeName]) }}</small>
        </template>
      </el-table-column>
      <el-table-column :label="$tr(&quot;确认商品档案&quot;)" width="300">
        <template #default="{ row }">
          <el-select v-model="row.productId" filterable clearable :placeholder="$tr(&quot;选择正确商品&quot;)" style="width:100%"
            @change="productChanged(row)">
            <el-option v-for="product in products" :key="product.productId" :value="product.productId"
              :label="`${product.sku} · ${product.productName}（${typeName(product.productType)}）`" />
          </el-select>
          <small v-if="row.productId">{{ row.productName }} · {{ typeName(row.productType) }}</small>
        </template>
      </el-table-column>
      <el-table-column :label="$tr(&quot;直播价&quot;)" width="115"><template #default="{ row }"><el-input v-model="row.fixedUnitPrice" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;佣金率 %&quot;)" width="105"><template #default="{ row }"><el-input v-model="row.commissionPercent" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;平台率 %&quot;)" width="105"><template #default="{ row }"><el-input v-model="row.platformPercent" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;税率 %&quot;)" width="95"><template #default="{ row }"><el-input v-model="row.taxPercent" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;包装费&quot;)" width="105"><template #default="{ row }"><el-input v-model="row.packFee" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;物流费&quot;)" width="105"><template #default="{ row }"><el-input v-model="row.shipFee" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;鉴定费&quot;)" width="105"><template #default="{ row }"><el-input v-model="row.certFee" @input="markDirty(row)" /></template></el-table-column>
      <el-table-column :label="$tr(&quot;状态&quot;)" width="90"><template #default="{ row }"><el-select v-model="row.bindingStatus" @change="markDirty(row)"><el-option :label="$tr(&quot;启用&quot;)" value="0" /><el-option :label="$tr(&quot;停用&quot;)" value="1" /></el-select></template></el-table-column>
      <el-table-column :label="$tr(&quot;备注/改价原因&quot;)" width="190"><template #default="{ row }"><el-input v-model="row.bindingRemark" @input="markDirty(row)" /></template></el-table-column>
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
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ $tr("取消") }}</el-button>
      <el-button type="primary" :loading="saving" :disabled="!draftRows.length" @click="emit('submit', draftRows)">{{ $tr("提交并重新校验") }}</el-button>
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
