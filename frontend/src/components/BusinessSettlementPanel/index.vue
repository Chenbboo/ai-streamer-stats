<template>
  <section class="settlement-panel" v-loading="loading">
    <div class="settlement-heading">
      <div class="heading-copy">
        <div class="heading-title-row">
          <h3>交付与结算</h3>
          <BusinessProjectState :project="displayProject" />
        </div>
        <p class="heading-description">查看交付进度、结算待办和项目管理费</p>
      </div>
      <div class="actions heading-actions">
        <el-button size="small" link type="primary" @click="expanded=!expanded">{{ expanded?'收起详情':'查看详情' }}</el-button>
        <el-button size="small" icon="Refresh" :disabled="loading||closing" @click="loadStatus">刷新</el-button>
        <el-button v-if="summary?.canClose" v-hasPermi="['business:accounting:close']" size="small" type="warning" @click="openClose">{{ closeActionLabel }}</el-button>
      </div>
    </div>
    <p v-if="expanded" class="policy-hint">{{ policyHint }}</p>
    <el-alert v-if="loadFailed" title="暂时无法读取结项检查，请刷新后核对；当前不能办理结项。" type="warning" :closable="false" show-icon />
    <template v-else-if="summary">
      <div class="management-card">
        <div class="management-head">
          <div class="management-title">
            <span class="management-mark">管</span>
            <div>
              <div class="management-name"><b>项目管理费</b><el-tag size="small" effect="light" :type="feeStatus.type">{{ feeStatus.label }}</el-tag></div>
              <span class="management-subtitle" :title="fee?.projectNames">{{ eligibilityText }}</span>
            </div>
          </div>
          <div v-if="fee?.canConfigure||fee?.canPay" class="management-actions">
            <el-button v-if="fee?.canConfigure" v-hasPermi="['business:accounting:close']" size="small" type="primary" plain @click="openFeeConfig">{{ fee.configured?'修改规则':'设置管理费' }}</el-button>
            <el-button v-if="fee?.canPay" v-hasPermi="['business:incentive:pay']" size="small" type="success" @click="openPayment">登记付款</el-button>
          </div>
        </div>
        <div class="fee-grid">
          <div class="fee-metric"><span>领取人</span><b>{{ fee?.recipientUserName||'—' }}</b></div>
          <div class="fee-metric fee-metric-primary"><span>{{ fee?.settledTime?'最终确认':'当前预计' }}</span><b>{{ money(fee?.settledTime?fee.settledAmount:fee?.estimatedAmount) }} <small>{{ fee?.currency||project.baseCurrency||'' }}</small></b></div>
          <div class="fee-metric"><span>管理费前利润</span><b>{{ money(fee?.preFeeProfit) }} <small>{{ fee?.currency||project.baseCurrency||'' }}</small></b></div>
          <div v-if="fee?.settledTime" class="fee-metric fee-metric-warning"><span>待支付</span><b>{{ money(fee?.remainingAmount) }} <small>{{ fee.currency }}</small></b></div>
        </div>
        <div v-if="fee?.configured" class="fee-note fee-rule"><span></span><p>{{ feeRule }}<template v-if="Number(fee?.eligibilityProjectCount)">；设置时负责人在管 {{ fee.eligibilityProjectCount }} 个项目</template></p></div>
        <div v-else-if="fee?.ownerChanged&&fee?.eligible" class="fee-note fee-pending"><span></span><p>项目负责人已经变更，新负责人符合条件，请重新设置本项目管理费。</p></div>
        <div v-else-if="fee?.eligible" class="fee-note fee-pending"><span></span><p>负责人已达到3个在管项目；归属老板需在核算关闭前设置本项目管理费，或明确选择不发放。</p></div>
        <div v-else class="fee-note fee-ineligible"><span></span><p>负责人当前未达到3个在管项目，本项目暂不设置管理费，也不会因此阻塞结项。</p></div>
        <template v-if="expanded&&fee?.settledTime">
          <div class="payment-head"><b>管理费付款记录</b></div>
          <div v-if="!fee.payments?.length" class="payment-empty">尚未登记付款</div>
          <div v-for="payment in fee.payments||[]" :key="payment.paymentId" class="payment-row">
            <div><b>{{ money(payment.amount) }} {{ fee.currency }}</b><span>{{ payment.paidDate }} · {{ paymentMethods[payment.method]||payment.method }} · {{ payment.referenceNo }}</span><small>{{ payment.reason }}</small></div>
            <business-file-upload :model-value="payment.voucher" :project-id="project.projectId" disabled :drag="false" :is-show-tip="false" />
          </div>
        </template>
      </div>

      <div v-if="summary.accountingState==='OPEN'" class="settlement-progress">
        <span class="progress-label">结算进度</span>
        <div class="settlement-counts">
          <span v-for="item in pendingCounts" :key="item.key">{{ item.label }}<b>{{ item.count }}</b></span>
          <span v-if="!pendingCounts.length" class="progress-clear">暂无结算待办</span>
        </div>
      </div>
      <ul v-if="expanded&&summary.blockers?.length" class="settlement-blockers">
        <li v-for="(blocker,index) in summary.blockers" :key="`${blocker.code}-${index}`">{{ blockerLabels[blocker.code]||blocker.label }}<template v-if="Number(blocker.count)>1"> · {{ blocker.count }}</template></li>
      </ul>
      <p v-else-if="summary.canClose" class="ready">结项检查已通过。核对最终金额后，可一次完成结项、核算并冻结数据。</p>
      <div v-if="expanded&&summary.accountingState==='OPEN'" class="actions links">
        <el-button v-hasPermi="['business:kpi:list']" link type="primary" @click="openKpi">办理KPI结算</el-button>
        <el-button v-hasPermi="['business:accounting:list']" link type="primary" @click="openAccounting">查看收支与核算</el-button>
      </div>
    </template>
    <BusinessClosedAdjustments v-if="projectAccountingState(displayProject)==='CLOSED'" :project="displayProject" @changed="emit('closed')" />

    <el-dialog v-model="feeDialog" title="设置项目管理费" width="min(620px,94vw)" append-to-body destroy-on-close>
      <el-alert :title="`负责人当前在管 ${fee?.projectCount||0} 个项目，已达到管理费设置条件。设置不会审批或暂停项目，关闭核算时才确认成本。`" type="info" :closable="false" show-icon />
      <el-form label-width="130px" class="dialog-form">
        <el-form-item label="领取人"><el-input :model-value="fee?.recipientUserName" disabled /></el-form-item>
        <el-form-item label="计算方式" required><el-radio-group v-model="feeForm.calculationMode"><el-radio-button value="FIXED">固定金额</el-radio-button><el-radio-button value="PROFIT_RATE">按利润比例</el-radio-button><el-radio-button value="WAIVED">不发放</el-radio-button></el-radio-group></el-form-item>
        <el-form-item v-if="feeForm.calculationMode==='FIXED'" label="固定管理费" required><el-input-number v-model="feeForm.fixedAmount" :min="0" :precision="2" :step="100" controls-position="right" /></el-form-item>
        <el-form-item v-if="feeForm.calculationMode==='PROFIT_RATE'" label="利润提取比例" required><el-input-number v-model="feeForm.profitRate" :min="0.0001" :max="100" :precision="4" :step="1" controls-position="right" /><span class="suffix">%</span></el-form-item>
        <el-form-item label="设置说明" required><el-input v-model="feeForm.configReason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="feeDialog=false">取消</el-button><el-button type="primary" :loading="feeSaving" @click="saveFee">保存管理费规则</el-button></template>
    </el-dialog>

    <el-dialog v-model="closeDialog" :title="closeDialogTitle" width="min(680px,94vw)" append-to-body destroy-on-close>
      <el-alert :title="closeDialogHint" type="warning" :closable="false" show-icon />
      <div class="close-calculation">
        <span>确认收入<b>{{ money(fee?.basisRevenue) }}</b></span><span>业务支出<b>- {{ money(fee?.basisBusinessCost) }}</b></span>
        <span>人员成本<b>- {{ money(fee?.basisPersonnelCost) }}</b></span><span>项目奖金<b>- {{ money(fee?.basisBonusCost) }}</b></span>
        <span>核算调整<b>{{ signedMoney(fee?.basisAdjustment) }}</b></span><span>管理费前利润<b>{{ money(fee?.preFeeProfit) }}</b></span>
        <span class="highlight">最终管理费<b>- {{ money(fee?.estimatedAmount) }}</b></span><span class="highlight">管理费后利润<b>{{ money(afterFeeProfit) }}</b></span>
      </div>
      <el-input v-model="closeReason" class="close-reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请填写结项确认说明" />
      <template #footer><el-button @click="closeDialog=false">取消</el-button><el-button type="warning" :loading="closing" @click="closeAccounting">{{ closeActionLabel }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="paymentDialog" title="登记项目管理费付款" width="min(620px,94vw)" append-to-body destroy-on-close>
      <el-form label-width="120px" class="dialog-form">
        <el-form-item label="待支付"><el-input :model-value="`${money(fee?.remainingAmount)} ${fee?.currency||''}`" disabled /></el-form-item>
        <el-form-item label="实付金额" required><el-input-number v-model="paymentForm.amount" :min="0" :max="Number(fee?.remainingAmount||0)" :precision="2" controls-position="right" /></el-form-item>
        <el-form-item label="实付日期" required><el-date-picker v-model="paymentForm.paidDate" type="date" value-format="YYYY-MM-DD" :disabled-date="futureDate" /></el-form-item>
        <el-form-item label="付款方式" required><el-select v-model="paymentForm.method"><el-option v-for="(label,value) in paymentMethods" :key="value" :value="value" :label="label" /></el-select></el-form-item>
        <el-form-item label="流水/收据编号" required><el-input v-model="paymentForm.referenceNo" maxlength="100" /></el-form-item>
        <el-form-item label="付款凭证" required><business-file-upload v-model="paymentForm.voucher" :project-id="project.projectId" :limit="3" /></el-form-item>
        <el-form-item label="付款说明" required><el-input v-model="paymentForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="paymentDialog=false">取消</el-button><el-button type="success" :loading="paymentSaving" @click="savePayment">保存付款记录</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { closeBusinessProjectAccounting, getBusinessProjectSettlementStatus, payBusinessProjectManagementFee, saveBusinessProjectManagementFee } from '@/api/business/project'
import { isDeliveryEnded, isSeparatedDelivery, projectAccountingState } from '@/utils/businessProjectState'
import BusinessClosedAdjustments from '@/components/BusinessClosedAdjustments/index.vue'
import BusinessProjectState from '@/components/BusinessProjectState/index.vue'

const props=defineProps({project:{type:Object,required:true},refreshKey:{type:[String,Number],default:''}})
const emit=defineEmits(['closed']),router=useRouter()
const loading=ref(false),closing=ref(false),loadFailed=ref(false),summary=ref(null),expanded=ref(false)
const feeDialog=ref(false),feeSaving=ref(false),closeDialog=ref(false),closeReason=ref(''),paymentDialog=ref(false),paymentSaving=ref(false)
const feeForm=reactive({calculationMode:'FIXED',fixedAmount:null,profitRate:null,configReason:''})
const paymentForm=reactive({amount:null,paidDate:'',method:'BANK',referenceNo:'',voucher:'',reason:''})
const paymentMethods={BANK:'银行转账',WECHAT:'微信',ALIPAY:'支付宝',CASH:'现金',OTHER:'其他'}
const blockerLabels={PENDING_AWARD:'奖金奖励单尚待处理或取消',PENDING_COST:'成员工作日缺少有效成本或日历',LEGACY_POLICY:'沿用原结项关账规则',DELIVERY_OPEN:'项目尚未完成交付或取消',ACCOUNTING_CLOSED:'项目核算已关闭',NOT_SPONSOR:'由项目归属老板办理核算关闭',MISSING_END_DATE:'缺少实际结束日期，请核对',PENDING_KPI:'仍有未完成KPI结算，包括尚未到期的周期',PENDING_EFFORT:'仍有投入待确认',PENDING_LEAVE:'仍有假勤待处理',PENDING_FACT:'仍有收支待确认或退回修改',MANAGEMENT_FEE_PENDING:'项目管理费尚未设置，请设置规则或明确不发放'}
const fee=computed(()=>summary.value?.managementFee||null)
const pendingCounts=computed(()=>[['pendingKpiCount','KPI待结算'],['pendingFactCount','收支待处理'],['pendingAwardCount','奖金待处理'],['pendingCostCount','人员成本待完善'],['pendingLeaveCount','假勤待处理']].map(([key,label])=>({key,label,count:Number(summary.value?.[key]||0)})).filter(item=>item.count>0))
let requestSequence=0
const displayProject=computed(()=>({...props.project,...(summary.value||{})}))
const policyHint=computed(()=>projectAccountingState(displayProject.value)==='CLOSED'?'项目已经结项并冻结，管理费成本已固定；付款凭证和受控调账仍可继续登记。':!isSeparatedDelivery(displayProject.value)?'本项目沿用原结项规则，历史记录和金额不自动迁移。':isDeliveryEnded(displayProject.value)?'这是合并流程上线前已经结项的项目，完成最终核算后即可冻结。':'KPI达标或周期结束并确认后，由归属老板一次完成结项、最终核算和数据冻结。')
const feeStatus=computed(()=>({INELIGIBLE:{label:'未达到条件',type:'info'},PENDING_CONFIG:{label:'待设置',type:'danger'},ESTIMATED:{label:'预计中',type:'primary'},PENDING_SETTLEMENT:{label:'待结算',type:'warning'},WAIVED:{label:'不发放',type:'info'},UNPAID:{label:'待支付',type:'danger'},PARTIAL:{label:'部分支付',type:'warning'},PAID:{label:'已结清',type:'success'}}[fee.value?.processStatus]||{label:'—',type:'info'}))
const eligibilityText=computed(()=>{const count=Number(fee.value?.projectCount||0),threshold=Number(fee.value?.eligibilityThreshold||3);if(fee.value?.configured&&Number(fee.value?.eligibilityProjectCount)>=threshold&&count<threshold)return `当前在管 ${count} 个项目；设置时为 ${fee.value.eligibilityProjectCount} 个，规则继续有效`;return `负责人当前在管 ${count} 个项目 · ${count>=threshold?'已达到管理费条件':`还差 ${threshold-count} 个达到管理费条件`}`})
const feeRule=computed(()=>fee.value?.calculationMode==='WAIVED'?'本项目明确不发放管理费':fee.value?.calculationMode==='FIXED'?`固定金额 ${money(fee.value.fixedAmount)} ${fee.value.currency}`:`管理费前利润 × ${fee.value?.profitRate||0}%`)
const afterFeeProfit=computed(()=>Number(fee.value?.preFeeProfit||0)-Number(fee.value?.estimatedAmount||0))
const completingOldClosedProject=computed(()=>isDeliveryEnded(displayProject.value)&&projectAccountingState(displayProject.value)!=='CLOSED')
const closeActionLabel=computed(()=>completingOldClosedProject.value?'确认核算并冻结':'确认结项并冻结')
const closeDialogTitle=computed(()=>completingOldClosedProject.value?'确认最终核算并冻结数据':'确认结项、核算并冻结')
const closeDialogHint=computed(()=>completingOldClosedProject.value?'这是合并流程上线前已经结项的项目。系统将确认最终金额并冻结核算数据。':'系统将检查交付、KPI、收支、人员成本、奖金与管理费，并在一次操作中完成结项、核算和数据冻结。')
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const signedMoney=value=>`${Number(value||0)>=0?'+ ':'- '}${money(Math.abs(Number(value||0)))}`
const localToday=()=>{const now=new Date();return new Date(now.getTime()-now.getTimezoneOffset()*60000).toISOString().slice(0,10)}
const requestKey=()=>globalThis.crypto?.randomUUID?.()||`${Date.now()}-${Math.random().toString(16).slice(2)}`
const futureDate=date=>date.getTime()>Date.now()
async function loadStatus(){const id=props.project?.projectId,seq=++requestSequence;summary.value=null;loadFailed.value=false;if(!id)return;loading.value=true;try{const response=await getBusinessProjectSettlementStatus(id);if(seq===requestSequence)summary.value=response.data||null}catch{if(seq===requestSequence)loadFailed.value=true}finally{if(seq===requestSequence)loading.value=false}}
function openFeeConfig(){Object.assign(feeForm,{calculationMode:fee.value?.calculationMode||'FIXED',fixedAmount:fee.value?.fixedAmount??null,profitRate:fee.value?.profitRate??null,configReason:fee.value?.configReason||''});feeDialog.value=true}
async function saveFee(){if(!feeForm.configReason.trim())return ElMessage.warning('请填写设置说明');feeSaving.value=true;try{await saveBusinessProjectManagementFee(props.project.projectId,{...feeForm,version:fee.value?.version});feeDialog.value=false;ElMessage.success('管理费规则已保存');await loadStatus();emit('closed')}finally{feeSaving.value=false}}
async function openClose(){if(!summary.value&&!loading.value)await loadStatus();if(!summary.value?.canClose){expanded.value=true;return ElMessage.warning('结项检查尚未通过，请先处理下方待办')};closeReason.value='';closeDialog.value=true}
async function closeAccounting(){if(!closeReason.value.trim())return ElMessage.warning('请填写结项确认说明');closing.value=true;try{await closeBusinessProjectAccounting(props.project.projectId,{version:summary.value.version,reason:closeReason.value.trim()});closeDialog.value=false;ElMessage.success(completingOldClosedProject.value?'项目核算已确认并冻结':'项目已结项，核算已确认并冻结');await loadStatus();emit('closed')}catch{await loadStatus()}finally{closing.value=false}}
function openPayment(){Object.assign(paymentForm,{amount:Number(fee.value?.remainingAmount||0),paidDate:localToday(),method:'BANK',referenceNo:'',voucher:'',reason:''});paymentDialog.value=true}
async function savePayment(){if(!paymentForm.amount||!paymentForm.paidDate||!paymentForm.referenceNo.trim()||!paymentForm.voucher||!paymentForm.reason.trim())return ElMessage.warning('请完整填写付款信息并上传凭证');paymentSaving.value=true;try{await payBusinessProjectManagementFee(props.project.projectId,{...paymentForm,requestKey:requestKey()});paymentDialog.value=false;ElMessage.success('付款记录已保存');await loadStatus()}finally{paymentSaving.value=false}}
function openKpi(){router.push({path:'/projects/kpi-results',query:{projectId:props.project.projectId}})}
function openAccounting(){router.push({path:'/business/accounting',query:{projectId:props.project.projectId}})}
watch(()=>[props.project,props.refreshKey],loadStatus,{immediate:true});watch(()=>props.project.projectId,()=>{expanded.value=false})
defineExpose({openClose})
</script>

<style scoped>
.settlement-panel{padding:20px;margin:16px 0;border:1px solid #e2e8f0;border-radius:14px;background:#f8fafc;box-shadow:0 1px 2px rgba(15,23,42,.03)}
.settlement-heading,.management-head,.payment-head{display:flex;justify-content:space-between;align-items:center;gap:18px}.management-actions{display:flex;align-items:center;flex-wrap:wrap;justify-content:flex-end;gap:8px}.management-actions :deep(.el-button){margin:0}
.heading-copy{min-width:0}.heading-title-row{display:flex;align-items:center;flex-wrap:wrap;gap:12px}.settlement-heading h3{margin:0;color:#172033;font-size:19px;font-weight:650;line-height:28px}.settlement-panel .heading-description{margin:3px 0 0;color:#94a3b8;font-size:12px}.heading-actions{flex:none}.actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}.policy-hint{padding:10px 13px;border-radius:8px;background:#eef4f8}.settlement-panel p{font-size:13px;line-height:1.65;color:#64748b;margin:12px 0 0}
.management-card{margin-top:18px;padding:18px;border:1px solid #e2e8f0;border-radius:12px;background:#fff;box-shadow:0 4px 14px rgba(15,23,42,.035)}.management-head{align-items:flex-start}.management-title{display:flex;align-items:center;min-width:0;gap:12px}.management-mark{display:grid;place-items:center;width:38px;height:38px;flex:none;border-radius:10px;background:linear-gradient(135deg,#eaf4ff,#edf8f5);color:#2476d2;font-size:15px;font-weight:700}.management-name{display:flex;align-items:center;flex-wrap:wrap;gap:9px;color:#1e293b;font-size:15px;line-height:24px}.management-subtitle{display:block;margin-top:1px;color:#94a3b8;font-size:12px;line-height:20px}
.fee-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px;margin-top:18px}.fee-metric{min-width:0;padding:13px 14px;border:1px solid #edf1f5;border-radius:9px;background:#f8fafc}.fee-metric>span{display:block;margin-bottom:7px;color:#8794a5;font-size:12px;line-height:18px}.fee-metric>b{display:block;overflow:hidden;color:#243247;font-size:16px;font-weight:650;line-height:24px;text-overflow:ellipsis;white-space:nowrap}.fee-metric b small{color:#64748b;font-size:11px;font-weight:500}.fee-metric-primary{border-color:#d8eafe;background:#f5f9ff}.fee-metric-primary>b{color:#2563a9}.fee-metric-warning{border-color:#fde6c7;background:#fff9f0}.fee-metric-warning>b{color:#b76712}
.fee-note{display:flex;align-items:flex-start;gap:9px;margin-top:13px;padding:10px 12px;border-radius:8px}.fee-note>span{width:6px;height:6px;flex:none;margin-top:7px;border-radius:50%;background:#4f9e83}.fee-note p{margin:0}.fee-rule{background:#f1f8f5}.fee-pending{background:#fff8ed}.fee-pending>span{background:#e29331}.fee-pending p{color:#a76415}
.fee-ineligible{background:#f1f5f9}.fee-ineligible>span{background:#94a3b8}.fee-ineligible p{color:#64748b}
.settlement-progress{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-top:14px;padding:12px 14px;border:1px solid #e8edf2;border-radius:10px;background:#fff}.progress-label{padding-right:12px;border-right:1px solid #e5eaf0;color:#475569;font-size:13px;font-weight:600}.settlement-counts{display:flex;flex:1;align-items:center;flex-wrap:wrap;gap:8px;font-size:12px}.settlement-counts>span{display:inline-flex;align-items:center;gap:7px;padding:5px 9px;border-radius:6px;background:#fff6e8;color:#9a621d}.settlement-counts b{display:grid;place-items:center;min-width:18px;height:18px;padding:0 4px;border-radius:9px;background:#f1a94c;color:#fff;font-size:11px}.settlement-counts .progress-clear{background:#eef8f3;color:#338266}
.settlement-blockers{margin:12px 0 0;padding:10px 14px 10px 32px;border-radius:8px;background:#fff9ed;color:#916525;font-size:12px;line-height:1.9}.ready{color:#277d65!important}.links{margin-top:10px}.links :deep(.el-button){margin:0}.payment-head{margin-top:18px;padding-top:16px;border-top:1px dashed #dce5e8}.payment-empty{padding:14px 0;color:#87949b;font-size:12px}.payment-row{display:grid;grid-template-columns:minmax(0,1fr) minmax(180px,280px);gap:14px;padding:14px 0;border-top:1px dashed #e0e7e9}.payment-row>div:first-child{display:flex;flex-direction:column;gap:5px}.payment-row span,.payment-row small{color:#74838b}.dialog-form{margin-top:18px}.dialog-form :deep(.el-input-number),.dialog-form :deep(.el-select),.dialog-form :deep(.el-date-editor){width:100%}.suffix{margin-left:8px}.close-calculation{display:grid;grid-template-columns:repeat(2,1fr);gap:1px;margin-top:16px;overflow:hidden;border:1px solid #e1e7ea;border-radius:9px;background:#e1e7ea}.close-calculation span{display:flex;justify-content:space-between;padding:11px 13px;background:#fff;color:#687780}.close-calculation b{color:#283a43}.close-calculation .highlight{background:#fff8e8;color:#8b6115}.close-reason{margin-top:16px}
@media(max-width:760px){.settlement-panel{padding:16px}.settlement-heading{align-items:flex-start;flex-direction:column}.heading-actions{width:100%;justify-content:flex-end}.management-head{align-items:stretch;flex-direction:column}.management-actions{align-self:flex-end}.fee-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.settlement-progress{align-items:flex-start;flex-direction:column}.progress-label{padding-right:0;border-right:0}.payment-row{grid-template-columns:1fr}.close-calculation{grid-template-columns:1fr}:global(.el-dialog .dialog-form .el-form-item){display:block}:global(.el-dialog .dialog-form .el-form-item__label){width:auto!important;height:auto;margin-bottom:6px;padding:0}:global(.el-dialog .dialog-form .el-form-item__content){margin-left:0!important}}
@media(max-width:480px){.fee-grid{grid-template-columns:1fr}.heading-actions{justify-content:flex-start}.management-subtitle{max-width:240px}}
</style>
