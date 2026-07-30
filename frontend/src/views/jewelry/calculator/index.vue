<template>
  <el-result
    v-if="!canViewFinance"
    icon="warning"
    title="无权查看"
    sub-title="毛利试算仅对审核员和管理员开放"
  />
  <div v-else class="app-container calculator">
    <header class="page-title">
      <h2>前端定价与达人谈判试算台</h2>
      <p>独立测算工具，不生成出库单。自动读取 SKU 平均采购成本与默认履约费用，反推保本底线与佣金上限。</p>
    </header>

    <div class="calculator-layout">
      <section class="input-panel">
        <el-form :model="form" label-position="top">
          <el-form-item label="选择 SKU">
            <el-select v-model="form.productId" filterable placeholder="请选择需要试算的商品"
              class="full-width" @change="productChanged">
              <el-option v-for="item in products" :key="item.productId"
                :label="productLabel(item)" :value="item.productId" />
            </el-select>
          </el-form-item>

          <el-row :gutter="20">
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="拟定成交价（¥）">
                <el-input-number v-model="form.price" :min="0" :precision="2" :controls="false" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item :label="`试算数量（可用库存 ${availableQty}）`">
                <el-input-number v-model="form.quantity" :min="1" :max="Math.max(1, availableQty)"
                  :precision="0" :disabled="!form.productId || availableQty <= 0" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="达人佣金率（%）">
                <el-input-number v-model="form.commissionRate" :min="0" :max="100" :precision="2" :controls="false" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="平台扣点率（%）">
                <el-input-number v-model="form.platformRate" :min="0" :max="100" :precision="2" :controls="false" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="税率（%）">
                <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" :controls="false" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :xs="24" :sm="8">
              <el-form-item label="包装费（¥）">
                <el-input-number v-model="form.packFee" :min="0" :precision="2" :controls="false" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="8">
              <el-form-item label="物流履约费（¥）">
                <el-input-number v-model="form.shipFee" :min="0" :precision="2" :controls="false" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="8">
              <el-form-item label="鉴定检测费（¥）">
                <el-input-number v-model="form.certFee" :min="0" :precision="2" :controls="false" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <div class="formula">
          毛利 = 成交价 ×（1 - 平台扣点 - 达人佣金 - 税率）- 履约固定支出 - SKU 采购成本
        </div>
      </section>

      <aside class="result-panel" v-loading="calculating">
        <div class="result-title">本单试算结果</div>
        <template v-if="result">
          <div class="profit-caption">预计总毛利</div>
          <div class="profit" :class="{ loss: Number(result.totalProfit) < 0 }">¥ {{ money(result.totalProfit) }}</div>
          <div class="result-row strong">
            <span>终端毛利率</span>
            <b :class="{ loss: Number(result.profitRate) < 0 }">{{ percent(result.profitRate) }}</b>
          </div>
          <div class="result-row"><span>试算数量</span><b>{{ result.quantity }} 件</b></div>
          <div class="result-row"><span>单件毛利</span><b>¥ {{ money(result.profit) }}</b></div>
          <div class="result-row"><span>预计成交总额</span><b>¥ {{ money(result.totalRevenue) }}</b></div>
          <div class="result-row"><span>SKU 单件采购价</span><b>¥ {{ money(result.cost) }}</b></div>
          <div class="result-row"><span>平台 + 佣金 + 税 总扣减</span><b>¥ {{ money(result.totalDeductions) }}</b></div>
          <div class="result-row"><span>履约固定总支出</span><b>¥ {{ money(result.totalFixedFees) }}</b></div>
          <div class="result-row stock-row">
            <span>模拟库存</span>
            <b>{{ result.availableQty }} → {{ result.remainingQty }} 件</b>
          </div>
          <div class="result-row emphasis"><span>保本底线售价</span><b>¥ {{ money(result.breakEvenPrice) }}</b></div>
          <div class="result-row emphasis"><span>当前价下佣金上限</span><b>{{ percent(result.maxCommissionRate) }}</b></div>
          <el-alert v-if="Number(result.totalProfit) < 0" class="risk-alert"
            title="当前方案预计亏损，请调整价格、佣金或费用"
            type="error" :closable="false" show-icon />
        </template>
        <el-empty v-else description="请选择 SKU 并填写成交价" :image-size="56" />
      </aside>
    </div>
  </div>
</template>

<script setup name="JewelryCalculator">
import { calculateJewelryProfit, listJewelryProducts } from '@/api/jewelry/erp'
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()
const canViewFinance = computed(() =>
  userStore.roles.some(role => ['admin', 'jewelry_admin', 'jewelry_reviewer'].includes(role))
)
const products = ref([])
const result = ref(null)
const calculating = ref(false)
const form = reactive({
  productId: null,
  price: 0,
  quantity: 1,
  commissionRate: 20,
  platformRate: 5,
  taxRate: 1,
  packFee: 0,
  shipFee: 0,
  certFee: 0
})
let calculateTimer

const money = value =>
  Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const percent = value => `${(Number(value || 0) * 100).toFixed(2)}%`
const productLabel = item =>
  `${item.sku} · ${item.productName}${item.specification ? ` · ${item.specification}` : ''}（可用 ${productAvailable(item)}，成本 ¥ ${money(item.avgCost)}）`
const productAvailable = item =>
  Math.max(0, Number(item?.onHandQty || 0) - Number(item?.reservedOutQty || 0))
const selectedProduct = computed(() => products.value.find(item => item.productId === form.productId))
const availableQty = computed(() => productAvailable(selectedProduct.value))

async function loadProducts() {
  const response = await listJewelryProducts({ pageNum: 1, pageSize: 500, status: '0' })
  products.value = response.rows || []
}

function productChanged(productId) {
  const product = products.value.find(item => item.productId === productId)
  if (!product) return
  form.packFee = Number(product.defaultPackFee || 0)
  form.shipFee = Number(product.defaultShipFee || 0)
  form.certFee = Number(product.defaultCertFee || 0)
  form.quantity = productAvailable(product) > 0 ? 1 : 0
}

function scheduleCalculate() {
  clearTimeout(calculateTimer)
  if (!form.productId || Number(form.price) <= 0 || Number(form.quantity) <= 0 ||
      Number(form.quantity) > availableQty.value) {
    result.value = null
    return
  }
  calculateTimer = setTimeout(calculate, 300)
}

async function calculate() {
  calculating.value = true
  try {
    result.value = (await calculateJewelryProfit(form)).data
  } finally {
    calculating.value = false
  }
}

watch(form, scheduleCalculate, { deep: true })
onBeforeUnmount(() => clearTimeout(calculateTimer))
if (canViewFinance.value) loadProducts()
</script>

<style scoped>
.calculator {
  width: calc(100% - 24px);
  max-width: 1240px;
  margin: 0 auto;
  padding-top: 14px;
}

.page-title {
  margin-bottom: 16px;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 2px 14px;
}

.page-title h2 {
  margin: 0 0 5px;
  color: #1f2937;
  font-size: 20px;
  font-weight: 650;
}

.page-title p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.calculator-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.input-panel,
.result-panel {
  border: 1px solid #dfe4ea;
  border-radius: 8px;
  background: #fff;
  padding: 20px;
  box-shadow: 0 1px 3px rgb(15 23 42 / 4%);
}

.input-panel :deep(.el-form-item) {
  margin-bottom: 14px;
}

.input-panel :deep(.el-form-item__label) {
  padding-bottom: 6px;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.full-width,
.input-panel :deep(.el-input-number) {
  width: 100%;
}

.formula {
  margin-top: 2px;
  border-top: 1px solid #edf0f3;
  padding-top: 14px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.result-title {
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 2px;
}

.result-panel :deep(.el-empty) {
  padding: 38px 0 22px;
}

.result-panel :deep(.el-empty__description) {
  margin-top: 12px;
}

.result-panel :deep(.el-empty__description p) {
  font-size: 13px;
}

.profit-caption {
  margin-top: 12px;
  color: #64748b;
  font-size: 12px;
}

.profit {
  margin: 3px 0 8px;
  color: #24936e;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
}

.loss {
  color: #dc2626 !important;
}

.result-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px dashed #e5e7eb;
  padding: 9px 0;
  color: #64748b;
  font-size: 13px;
}

.result-row b {
  color: #334155;
  text-align: right;
}

.result-row.strong b {
  font-size: 16px;
}

.result-row.stock-row b {
  color: #2563eb;
}

.result-row.emphasis b {
  color: #b7791f;
}

.risk-alert {
  margin-top: 14px;
}

@media (max-width: 1100px) {
  .calculator {
    width: 100%;
  }

  .calculator-layout {
    grid-template-columns: minmax(0, 1fr) 310px;
  }
}

@media (max-width: 900px) {
  .calculator-layout {
    grid-template-columns: 1fr;
  }

  .input-panel,
  .result-panel {
    padding: 16px;
  }

  .profit {
    font-size: 28px;
  }
}
</style>
