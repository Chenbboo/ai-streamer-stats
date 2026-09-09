<template>
  <section class="settlement-panel" v-loading="loading">
    <div class="settlement-heading">
      <div><h3>{{ t('title') }}</h3><BusinessProjectState :project="displayProject" /></div>
      <div class="settlement-actions">
        <el-button size="small" icon="Refresh" :disabled="loading || closing" @click="loadStatus">{{ t('refresh') }}</el-button>
        <el-button v-if="summary?.canClose" v-hasPermi="['business:accounting:close']" size="small" type="warning" :loading="closing" @click="closeAccounting">{{ t('close') }}</el-button>
      </div>
    </div>
    <p>{{ policyHint }}</p>
    <el-alert v-if="loadFailed" :title="t('loadFailed')" type="warning" :closable="false" show-icon />
    <template v-else-if="summary">
      <div v-if="summary.accountingState === 'OPEN'" class="settlement-counts">
        <span>{{ t('pendingKpi') }} <b>{{ summary.pendingKpiCount || 0 }}</b></span>
        <span>{{ t('pendingFact') }} <b>{{ summary.pendingFactCount || 0 }}</b></span>

        <span>{{ t('pendingAward') }} <b>{{ summary.pendingAwardCount || 0 }}</b></span><span>{{ t('pendingCost') }} <b>{{ summary.pendingCostCount || 0 }}</b></span><span>{{ t('pendingLeave') }} <b>{{ summary.pendingLeaveCount || 0 }}</b></span>
      </div>
      <ul v-if="summary.blockers?.length" class="settlement-blockers">
        <li v-for="(blocker,index) in summary.blockers" :key="`${blocker.code}-${index}`">{{ blockerLabel(blocker) }}<template v-if="Number(blocker.count)>1"> · {{ blocker.count }}</template></li>
      </ul>
      <p v-else-if="summary.canClose" class="settlement-ready">{{ t('ready') }}</p>
      <div v-if="summary.accountingState === 'OPEN'" class="settlement-links">
        <el-button v-hasPermi="['business:kpi:list']" link type="primary" @click="openKpi">{{ t('kpiLink') }}</el-button>
        <el-button v-hasPermi="['business:accounting:list']" link type="primary" @click="openAccounting">{{ t('accountingLink') }}</el-button>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { closeBusinessProjectAccounting, getBusinessProjectSettlementStatus } from '@/api/business/project'
import { isDeliveryEnded, isSeparatedDelivery, projectAccountingState } from '@/utils/businessProjectState'
import BusinessProjectState from '@/components/BusinessProjectState/index.vue'

const props = defineProps({ project: { type: Object, required: true }, refreshKey: { type: [String, Number], default: '' } })
const emit = defineEmits(['closed'])
const router = useRouter()
const loading = ref(false), closing = ref(false), loadFailed = ref(false), summary = ref(null)
let requestSequence = 0
const displayProject = computed(() => ({ ...props.project, ...(summary.value || {}) }))
const policyHint = computed(() => {
  if (projectAccountingState(displayProject.value) === 'CLOSED') return t('closedHint')
  if (!isSeparatedDelivery(displayProject.value)) return t('legacyHint')
  return isDeliveryEnded(displayProject.value) ? t('deliveredHint') : t('separatedHint')
})
const { t, te } = useI18n({ useScope: 'local', messages: {
  'zh-CN': {
    title: '交付与后续结算', refresh: '刷新状态', close: '关闭项目核算',
    pendingAward: '奖金待处理', pendingCost: '人员成本待完善', pendingKpi: 'KPI待结算', pendingFact: '收支待处理', pendingEffort: '投入待确认', pendingLeave: '假勤待处理',
    loadFailed: '暂时无法读取结算待办，请刷新后核对；当前不能办理关账。',
    legacyHint: '本项目沿用原结项与核算规则，历史记录和金额不自动迁移。',
    separatedHint: '交付检查与核算关闭分别办理。已有KPI、奖金及费用待办不会自动阻止成果交付。',
    deliveredHint: '项目执行已结束。既有KPI和执行期间发生的迟报费用可按权限续办；不再新增执行任务。',
    closedHint: '项目核算已关闭，普通结算和费用写入已停止；历史结果保留，不自动重开。',
    ready: '当前关账检查通过。请核对遗漏的业务事实后，由有权限的责任人确认关闭。',
    kpiLink: '办理KPI结算', accountingLink: '查看收支与核算',
    closeTitle: '确认独立关闭项目核算', closePrompt: '关闭后将停止普通KPI结算、费用入账和重算。请确认业务已结清，并填写关账说明。',
    reasonRequired: '请填写关账说明', cancel: '取消', confirm: '确认关账', closed: '项目核算已关闭',
    blockers: { PENDING_AWARD: '奖金奖励单尚待处理或取消', PENDING_COST: '成员工作日缺少有效成本或日历', LEGACY_POLICY: '沿用原结项关账规则', DELIVERY_OPEN: '项目尚未完成交付或取消', ACCOUNTING_CLOSED: '项目核算已关闭', NOT_SPONSOR: '由项目归属责任人办理核算关闭', MISSING_END_DATE: '缺少实际结束日期，请核对', PENDING_KPI: '仍有未完成KPI结算，包括尚未到期的周期', PENDING_EFFORT: '仍有投入待确认', PENDING_LEAVE: '仍有假勤待处理', PENDING_FACT: '仍有收支待确认或退回修改' }
  },
  'vi-VN': {
    title: 'Bàn giao và quyết toán tiếp theo', refresh: 'Làm mới', close: 'Đóng quyết toán dự án',
    pendingAward: 'Thưởng chờ xử lý', pendingCost: 'Công việc chờ định giá', pendingKpi: 'KPI chưa quyết toán', pendingFact: 'Thu chi chờ xử lý', pendingEffort: 'Khối lượng chờ xác nhận', pendingLeave: 'Nghỉ phép chờ xử lý',
    loadFailed: 'Không thể tải công việc quyết toán. Hãy làm mới và kiểm tra; hiện không thể đóng quyết toán.',
    legacyHint: 'Dự án giữ quy trình kết thúc và quyết toán cũ. Lịch sử và số tiền không tự động chuyển đổi.',
    separatedHint: 'Bàn giao và đóng quyết toán được xử lý riêng. KPI, thưởng và chi phí đang chờ không tự động chặn bàn giao.',
    deliveredHint: 'Dự án đã ngừng thực hiện. KPI hiện có và chi phí phát sinh trong thời gian thực hiện được xử lý tiếp theo quyền; không thêm nhiệm vụ mới.',
    closedHint: 'Quyết toán đã đóng. Không tiếp nhận ghi nhận thông thường; giữ nguyên lịch sử và không tự mở lại.',
    ready: 'Các kiểm tra đóng quyết toán đã đạt. Người có trách nhiệm cần kiểm tra nghiệp vụ còn thiếu trước khi xác nhận.',
    kpiLink: 'Xử lý quyết toán KPI', accountingLink: 'Xem thu chi và quyết toán',
    closeTitle: 'Xác nhận đóng quyết toán dự án', closePrompt: 'Sau khi đóng sẽ dừng quyết toán KPI, ghi nhận chi phí và tính lại thông thường. Hãy xác nhận đã xử lý xong và nhập lý do.',
    reasonRequired: 'Vui lòng nhập lý do', cancel: 'Hủy', confirm: 'Xác nhận đóng', closed: 'Đã đóng quyết toán dự án',
    blockers: { PENDING_AWARD: 'Còn đơn thưởng chưa xử lý hoặc hủy', PENDING_COST: 'Công việc đã xác nhận chờ định giá', LEGACY_POLICY: 'Giữ quy tắc kết thúc và quyết toán cũ', DELIVERY_OPEN: 'Dự án chưa bàn giao xong hoặc hủy', ACCOUNTING_CLOSED: 'Quyết toán đã đóng', NOT_SPONSOR: 'Người chịu trách nhiệm quản lý dự án thực hiện đóng quyết toán', MISSING_END_DATE: 'Thiếu ngày kết thúc thực tế, cần kiểm tra', PENDING_KPI: 'Còn KPI chưa quyết toán, bao gồm kỳ chưa đến hạn', PENDING_EFFORT: 'Còn khối lượng chờ xác nhận', PENDING_LEAVE: 'Còn nghỉ phép chờ xử lý', PENDING_FACT: 'Còn thu chi chờ xác nhận hoặc sửa lại' }
  }
} })

function blockerLabel(blocker) {
  const key = `blockers.${blocker.code}`
  return te(key) ? t(key) : blocker.label
}
async function loadStatus() {
  const projectId = props.project?.projectId
  const sequence = ++requestSequence
  summary.value = null
  loadFailed.value = false
  if (!projectId) return
  loading.value = true
  try {
    const response = await getBusinessProjectSettlementStatus(projectId)
    if (sequence === requestSequence) summary.value = response.data || null
  } catch {
    if (sequence === requestSequence) loadFailed.value = true
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
async function closeAccounting() {
  if (!summary.value?.canClose || closing.value) return
  const projectId = props.project.projectId
  const version = summary.value.version
  try {
    const { value } = await ElMessageBox.prompt(t('closePrompt'), t('closeTitle'), {
      type: 'warning', inputType: 'textarea', inputAttributes: { maxlength: 500 },
      inputValidator: value => !!value?.trim() || t('reasonRequired'),
      confirmButtonText: t('confirm'), cancelButtonText: t('cancel')
    })
    if (projectId !== props.project.projectId) return
    closing.value = true
    await closeBusinessProjectAccounting(projectId, { version, reason: value.trim() })
    ElMessage.success(t('closed'))
    await loadStatus()
    emit('closed')
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) await loadStatus()
  } finally {
    closing.value = false
  }
}
function openKpi() { router.push({ path: '/projects/kpi-results', query: { projectId: props.project.projectId } }) }
function openAccounting() { router.push({ path: '/business/accounting', query: { projectId: props.project.projectId } }) }
watch(() => [props.project, props.refreshKey], loadStatus, { immediate: true })
</script>

<style scoped>
.settlement-panel{padding:16px 18px;margin:14px 0;border:1px solid #dce5e8;border-radius:12px;background:#f8fbfb}
.settlement-heading{display:flex;justify-content:space-between;align-items:center;gap:12px}.settlement-heading h3{margin:0 0 9px;font-size:16px}.settlement-actions,.settlement-links{display:flex;flex-wrap:wrap;gap:8px}.settlement-panel p{font-size:13px;line-height:1.6;color:#61717c;margin:12px 0 0}.settlement-counts{display:flex;flex-wrap:wrap;gap:12px 22px;margin-top:12px;font-size:13px}.settlement-counts b{margin-left:4px}.settlement-blockers{margin:10px 0 0;padding-left:20px;color:#86682b;font-size:12px;line-height:1.8}.settlement-panel .settlement-ready{color:#277d65}.settlement-links{margin-top:10px}.settlement-links :deep(.el-button){margin:0}
@media(max-width:640px){.settlement-heading{align-items:flex-start;flex-direction:column}.settlement-actions{width:100%}}
</style>
