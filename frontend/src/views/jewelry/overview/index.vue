<template>
  <div class="app-container jewelry-page">
    <div class="page-title">
      <div><h2>珠宝ERP概览</h2><p>库存、审批与本月经营数据</p></div>
      <el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>
    <div class="metrics">
      <div v-for="item in metrics" :key="item.key" class="metric" :class="`metric--${item.tone}`">
        <span>{{ item.label }}</span><strong>{{ format(data[item.key], item.money) }}</strong>
      </div>
    </div>
    <section class="todo-section">
      <h3>待处理事项</h3>
      <div class="notice-list">
        <button v-for="item in notices" :key="item.key" type="button" class="notice-item"
          :class="{ 'notice-item--active': Number(data[item.key] || 0) > 0 }"
          :title="`查看${item.name}`" @click="goNotice(item)">
          <span class="notice-icon"><el-icon><component :is="item.icon" /></el-icon></span>
          <span class="notice-copy"><b>{{ data[item.key] || 0 }}</b><small>{{ item.label }}</small></span>
          <el-icon class="notice-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </section>
  </div>
</template>
<script setup name="JewelryOverview">
import { getJewelryDashboard } from '@/api/jewelry/erp'
import useUserStore from '@/store/modules/user'
import { ArrowRight, Clock, DocumentChecked, Warning } from '@element-plus/icons-vue'
const data = ref({})
const loading = ref(false)
const router = useRouter()
const userStore = useUserStore()
const canViewFinance = computed(() => userStore.roles.some(role => ['admin', 'jewelry_admin', 'jewelry_reviewer'].includes(role)))
const allMetrics = [
  { key: 'stockAmount', label: '库存总资产', money: true, tone: 'teal' },
  { key: 'availableQty', label: '可用库存', tone: 'blue' },
  { key: 'inspectionQty', label: '售后待检', tone: 'amber' },
  { key: 'monthPurchase', label: '本月采购额', money: true, tone: 'violet' },
  { key: 'monthSales', label: '本月销售额', money: true, tone: 'cyan' },
  { key: 'monthProfit', label: '本月销售毛利', money: true, tone: 'rose' }
]
const notices = [
  { key: 'pendingFirstCount', label: '张单据待审核', name: '待审核单据', icon: DocumentChecked, path: '/jewelry/approval', query: { status: 'PENDING' } },
  { key: 'quantityWarningCount', label: '个商品库存不足', name: '库存不足商品', icon: Warning, path: '/jewelry/stock', query: { warningOnly: 'true', warningType: 'quantity' } },
  { key: 'ageWarningCount', label: '个商品库龄超期', name: '库龄超期商品', icon: Clock, path: '/jewelry/stock', query: { warningOnly: 'true', warningType: 'age' } }
]
const metrics = computed(() => canViewFinance.value ? allMetrics : allMetrics.filter(item => !item.money))
const format = (v, money) => money ? `¥ ${Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : Number(v || 0).toLocaleString()
const goNotice = item => router.push({ path: item.path, query: item.query })
async function load() {
  loading.value = true
  try { data.value = (await getJewelryDashboard()).data || {} }
  finally { loading.value = false }
}
load()
</script>
<style scoped>
.jewelry-page{background:#f4f6f8;min-height:calc(100vh - 84px);padding:22px 24px}.page-title{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}.page-title h2{margin:0;color:#172033;font-size:22px;font-weight:650}.page-title p{margin:5px 0 0;color:#7a8493;font-size:14px}.metrics{display:grid;grid-template-columns:repeat(6,minmax(130px,1fr));background:#fff;border:1px solid #dce1e7;border-top:0}.metric{position:relative;min-width:0;padding:17px 20px 18px;border-top:3px solid #94a3b8;border-right:1px solid #e6e9ed}.metric:last-child{border-right:0}.metric--teal{border-top-color:#0f8a7b}.metric--blue{border-top-color:#3976c5}.metric--amber{border-top-color:#c98923}.metric--violet{border-top-color:#7656a8}.metric--cyan{border-top-color:#278697}.metric--rose{border-top-color:#b64b65}.metric span{display:block;color:#737e8d;font-size:13px}.metric strong{display:block;overflow:hidden;margin-top:8px;color:#172033;font-size:22px;font-weight:700;line-height:1.25;text-overflow:ellipsis;white-space:nowrap}.todo-section{margin-top:16px;padding:18px 20px 20px;background:#fff;border:1px solid #dce1e7}.todo-section h3{margin:0 0 14px;color:#273142;font-size:16px}.notice-list{display:grid;grid-template-columns:repeat(4,minmax(150px,1fr));border:1px solid #e2e6eb}.notice-item{display:flex;align-items:center;min-height:88px;padding:14px 16px 14px 20px;border:0;border-right:1px solid #e2e6eb;background:#fafbfc;font:inherit;text-align:left;cursor:pointer;transition:background-color .16s ease}.notice-item:hover{background:#f2f6fa}.notice-item:focus-visible{position:relative;z-index:1;outline:2px solid #409eff;outline-offset:-2px}.notice-item:last-child{border-right:0}.notice-icon{display:grid;width:38px;height:38px;flex:0 0 38px;place-items:center;margin-right:14px;border-radius:50%;background:#e9edf2;color:#8792a2;font-size:19px}.notice-copy{display:flex;align-items:baseline;gap:9px;min-width:0}.notice-copy b{color:#536071;font-size:26px;line-height:1}.notice-copy small{color:#6c7787;font-size:14px;white-space:nowrap}.notice-arrow{margin-left:auto;color:#a4adba;font-size:16px}.notice-item--active{background:#fff9f8}.notice-item--active:hover{background:#fff2f0}.notice-item--active .notice-icon{background:#fbe5e2;color:#c2413a}.notice-item--active .notice-copy b{color:#c2413a}.notice-item--active .notice-arrow{color:#c96b62}@media(max-width:1200px){.metrics{grid-template-columns:repeat(3,1fr)}.metric:nth-child(3){border-right:0}.notice-list{grid-template-columns:repeat(2,1fr)}.notice-item:nth-child(2){border-right:0}.notice-item:nth-child(-n+2){border-bottom:1px solid #e2e6eb}}@media(max-width:720px){.jewelry-page{padding:16px}.metrics{grid-template-columns:repeat(2,1fr)}.metric:nth-child(3){border-right:1px solid #e6e9ed}.metric:nth-child(even){border-right:0}.notice-list{grid-template-columns:1fr}.notice-item,.notice-item:nth-child(2){border-right:0;border-bottom:1px solid #e2e6eb}.notice-item:last-child{border-bottom:0}.notice-copy small{white-space:normal}}
.notice-list{grid-template-columns:repeat(3,minmax(150px,1fr))}
@media(max-width:1200px){.notice-list{grid-template-columns:repeat(2,1fr)}}
@media(max-width:720px){.notice-list{grid-template-columns:1fr}}
</style>
