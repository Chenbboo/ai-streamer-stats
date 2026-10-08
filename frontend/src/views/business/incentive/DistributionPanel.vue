<template>
 <section class="distribution">
  <div class="product-head"><div><h2>{{ t(data.personal?'personal':'title') }}</h2><p>{{ t(data.personal?'ownHint':'intro') }}</p></div></div>
  <el-table ref="allocationTable" :data="memberRows" :empty-text="t('emptyMembers')" row-key="key" :expand-row-keys="requestedMemberKeys" :row-class-name="({row}) => String(row.batch?.allocationId) === String(requestedAllocationId) ? 'requested-allocation-row' : ''">
   <el-table-column type="expand"><template #default="{row}">
    <div class="batch-details">
     <p v-if="row.line.reason">{{ t('reason') }}: {{ row.line.reason }}</p>
     <p v-if="row.award">{{ t('applicationAllocationTitle') }} · {{ t('reason') }}: {{ row.award.applicationAllocation?.reason }}</p>
     <template v-if="row.batch">
      <p>{{ t('batch') }} #{{ row.batch.allocationId }} · {{ t('sum') }} {{ money(row.batch.amount) }} {{ row.currency }}</p>
      <p v-if="row.batch.reason">{{ t('reason') }}: {{ row.batch.reason }}</p>
      <p v-if="data.canPay && row.batch.status==='APPROVED' && !row.batch.canPay" class="muted">{{ t('costPending') }}</p>
      <h4>{{ t('records') }}</h4>
      <el-table :data="memberPayments(row)" :empty-text="t('noPayment')">
       <el-table-column :label="t('paidAmount')"><template #default="{row:payment}">{{ money(payment.amount) }} {{ row.currency }}</template></el-table-column>
       <el-table-column prop="paidDate" :label="t('date')" />
       <el-table-column :label="t('method')"><template #default="{row:payment}">{{ t(payment.method) }}</template></el-table-column>
       <el-table-column prop="referenceNo" :label="t('reference')" min-width="150" />
       <el-table-column :label="t('voucher')"><template #default="{row:payment}"><el-popover trigger="click" placement="left" :width="190"><template #reference><el-button link type="primary">{{ t('voucher') }}</el-button></template><el-image v-if="isImageVoucher(payment.voucher)" class="voucher-thumbnail" :src="voucherUrl(payment.voucher)" :preview-src-list="[voucherUrl(payment.voucher)]" fit="cover" preview-teleported /><a v-else class="voucher-file" :href="voucherUrl(payment.voucher)" target="_blank" rel="noopener noreferrer">{{ t('voucher') }}</a></el-popover></template></el-table-column>
       <el-table-column prop="recordedUserName" :label="t('operator')" />
      </el-table>
      <el-collapse v-if="row.batch.events?.length"><el-collapse-item :title="t('events')" name="events">
       <el-timeline><el-timeline-item v-for="event in row.batch.events" :key="event.eventId" :timestamp="event.createTime">
        <b>{{ t(event.eventType) }}</b> · {{ event.operatorName }} — {{ event.reason }}
        <div v-for="(line,index) in eventLines(event)" :key="index" class="muted">{{ line.userName }} · {{ money(line.amount) }} {{ row.currency }} · {{ line.reason }}</div>
       </el-timeline-item></el-timeline>
      </el-collapse-item></el-collapse>
     </template>
    </div>
   </template></el-table-column>
   <el-table-column :label="t('person')" min-width="160"><template #default="{row}"><strong>{{ row.userName }}</strong><div class="muted">{{ row.batch?.ruleName || row.award?.ruleName }} #{{ row.batch?.awardId || row.award?.awardId }} · {{ row.batch?.settlementMonth || row.award?.settlementMonth || t('legacyCumulative') }}</div></template></el-table-column>
   <el-table-column :label="t('batch')" min-width="145"><template #default="{row}">{{ row.batch ? '#'+row.batch.allocationId : t('applicationAllocationTitle') }}</template></el-table-column>
   <el-table-column :label="t('memberBonusRate')" min-width="115"><template #default="{row}">{{ row.percentage == null ? '—' : `${row.percentage}%` }}</template></el-table-column>
   <el-table-column :label="t('memberBonusAmount')" min-width="150"><template #default="{row}">{{ money(row.amount) }} {{ row.currency }}</template></el-table-column>
   <el-table-column :label="t('paid')" min-width="130"><template #default="{row}">{{ money(row.paidAmount) }} {{ row.currency }}</template></el-table-column>
   <el-table-column :label="t('unpaid')" min-width="130"><template #default="{row}">{{ money(Number(row.amount)-Number(row.paidAmount)) }} {{ row.currency }}</template></el-table-column>
   <el-table-column :label="t('paymentState')" min-width="115"><template #default="{row}"><el-tag :type="row.paymentStatus==='PAID'?'success':row.paymentStatus==='PARTIAL'?'warning':'info'">{{ t(row.paymentStatus) }}</el-tag></template></el-table-column>
   <el-table-column :label="t('allocationStatus')" min-width="115"><template #default="{row}"><el-tag>{{ t(row.allocationStatus) }}</el-tag></template></el-table-column>
   <el-table-column :label="t('actions')" min-width="260" fixed="right"><template #default="{row}"><div class="row-actions">
    <el-button v-if="row.award" link @click="emit('open-award',row.award)">{{ t('viewAward') }}</el-button>
    <template v-if="row.batch?.canEdit && row.firstInBatch"><el-button v-hasPermi="['business:incentive:apply']" link :disabled="!!rowBonusBlock(row.batch)" @click="openEdit(row.batch)">{{ t('edit') }}</el-button><el-button v-hasPermi="['business:incentive:apply']" link type="primary" :disabled="busy || !!rowBonusBlock(row.batch)" @click="act(row.batch,'SUBMITTED')">{{ t('submit') }}</el-button><el-button v-hasPermi="['business:incentive:apply']" link type="danger" :disabled="busy" @click="act(row.batch,'CANCELED')">{{ t('cancelBatch') }}</el-button></template>
    <template v-if="row.batch?.canReview && row.firstInBatch"><el-button v-hasPermi="['business:incentive:approve']" link type="success" :disabled="busy || !!rowBonusBlock(row.batch)" @click="act(row.batch,'APPROVED')">{{ t('approve') }}</el-button><el-button v-hasPermi="['business:incentive:approve']" link type="warning" :disabled="busy" @click="act(row.batch,'RETURNED')">{{ t('return') }}</el-button></template>
    <el-button v-if="row.batch?.canPay && Number(row.paidAmount)<Number(row.amount)" v-hasPermi="['business:incentive:pay']" link type="primary" :disabled="!!rowBonusBlock(row.batch)" @click="openPayment(row.batch,row.line)">{{ t('pay') }}</el-button>
   </div></template></el-table-column>
  </el-table>
  <el-dialog v-model="editOpen" :title="t('edit')" width="min(950px,95vw)" append-to-body>
   <el-form label-position="top">
    <el-form-item :label="t('award')" required><el-select v-model="form.awardId" :disabled="!!form.allocationId" style="width:100%"><el-option v-for="a in data.awards" :key="a.awardId" :value="a.awardId" :label="a.ruleName+' #'+a.awardId+' · '+money(a.sourceAmount)+' '+a.currency" /></el-select></el-form-item>
    <p>{{ t('settlementMonth') }}: {{ selectedAward?.settlementMonth || t('legacyCumulative') }} · {{ t('monthlySettlement') }}</p><p>{{ t('total') }}: <b>{{ money(selectedAward?.sourceAmount) }} {{ selectedAward?.currency }}</b></p>
    <p class="muted">{{ t('sourceHint') }}</p>
    <el-form-item :label="t('mode')"><el-radio-group v-model="form.mode"><el-radio value="AMOUNT">{{ t('AMOUNT') }}</el-radio><el-radio value="PERCENT">{{ t('PERCENT') }}</el-radio></el-radio-group></el-form-item>
    <p class="muted">{{ t(form.mode==='PERCENT'?'percentHint':'allocationNote') }}</p>
    <div v-for="(l,index) in form.lines" :key="index" class="allocation-line">
     <el-select v-model="l.userId" filterable :placeholder="t('person')" :aria-label="t('person')"><el-option v-for="r in data.recipients" :key="r.userId" :value="r.userId" :label="r.userName" /></el-select>
     <el-input-number v-if="form.mode==='AMOUNT'" v-model="l.amount" :placeholder="t('amount')" :aria-label="t('amount')" :min="0" :precision="2" controls-position="right" />
     <el-input-number v-else v-model="l.percentage" :placeholder="t('percentage')" :aria-label="t('percentage')" :min="0" :max="100" :precision="2" controls-position="right" />
     <el-input v-model="l.reason" :placeholder="t('reason')" maxlength="500" />
     <el-button type="danger" link :disabled="form.lines.length===1" @click="form.lines.splice(index,1)">{{ t('remove') }}</el-button>
     <small v-if="form.mode==='PERCENT'">{{ money(lineAmount(l)) }} {{ selectedAward?.currency }}</small>
    </div>
    <el-button :disabled="form.lines.length>=200" @click="form.lines.push({userId:null,amount:null,percentage:null,reason:''})">{{ t('add') }}</el-button>
    <p><b>{{ t('sum') }}: {{ money(total) }} {{ selectedAward?.currency }}</b> · {{ t('capacity') }}: {{ money(capacity) }}</p>
    <el-form-item :label="t('reason')" required><el-input v-model="form.reason" type="textarea" maxlength="500" /></el-form-item>
   </el-form>
   <template #footer><el-button @click="editOpen=false">{{ t('cancel') }}</el-button><el-button type="primary" :loading="busy" :disabled="!!editBonusBlock" @click="save">{{ t('save') }}</el-button></template>
  </el-dialog>
  <el-dialog v-model="paymentOpen" :title="t('paymentTitle')" width="min(640px,95vw)" append-to-body>
   <el-alert :title="t('payHint')" type="info" :closable="false" />
   <p>{{ t('settlementMonth') }}: {{ paymentBatch?.settlementMonth || t('legacyCumulative') }}</p><p><b>{{ paymentPerson }}</b> · {{ t('unpaid') }}: {{ money(paymentLimit) }} {{ paymentCurrency }}</p>
   <el-form label-position="top">
    <el-form-item :label="t('paidAmount')" required><el-input-number v-model="payment.amount" :min="0" :max="paymentLimit" :precision="2" :aria-label="t('paidAmount')" /></el-form-item>
    <el-form-item :label="t('date')" required><el-date-picker v-model="payment.paidDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
    <el-form-item :label="t('method')" required><el-select v-model="payment.method"><el-option v-for="m in ['BANK','WECHAT','ALIPAY','CASH','OTHER']" :key="m" :label="t(m)" :value="m" /></el-select></el-form-item>
    <el-form-item :label="t('reference')" required><el-input v-model="payment.referenceNo" maxlength="100" /></el-form-item>
    <el-form-item :label="t('voucher')" required><el-upload action="#" :http-request="upload" :show-file-list="false" accept=".png,.jpg,.jpeg,.webp,.pdf" :disabled="uploading"><el-button :loading="uploading">{{ t('upload') }}</el-button></el-upload><span v-if="payment.voucher">{{ t('uploadOk') }}</span></el-form-item>
    <el-form-item :label="t('payReason')" required><el-input v-model="payment.reason" type="textarea" maxlength="500" /></el-form-item>
   </el-form>
   <template #footer><el-button @click="paymentOpen=false">{{ t('cancel') }}</el-button><el-button type="primary" :loading="busy" :disabled="uploading || !!paymentBonusBlock" @click="pay">{{ t('record') }}</el-button></template>
  </el-dialog>
 </section>
</template>
<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { saveBonusAllocation, actBonusAllocation, recordBonusPayment } from '@/api/business/incentive'
import messages from './distributionMessages'
import { recordBonusBlockReason } from './bonusSummary.js'
import { memberAllocationRows } from './memberAllocationRows'
const props=defineProps({data:{type:Object,default:()=>({})},applicationAwards:{type:Array,default:()=>[]},requestedAllocationId:{type:[String,Number],default:null}})
const bonusProfitBlock=computed(()=>props.data.bonusBlockReason ?? 'noProfitResult')
const rowBonusBlock=row=>recordBonusBlockReason(row,bonusProfitBlock.value)
const memberRows=computed(()=>memberAllocationRows(props.applicationAwards,props.data))
const allocationTable=ref(null)
const requestedMemberKeys=computed(()=>memberRows.value
 .filter(row=>row.batch && String(row.batch.allocationId)===String(props.requestedAllocationId))
 .slice(0,1).map(row=>row.key))
watch(()=>[props.data.allocations,props.requestedAllocationId],async()=>{
 await nextTick()
 const row=memberRows.value.find(row=>row.batch && String(row.batch.allocationId)===String(props.requestedAllocationId))
 if(!row)return
 allocationTable.value?.$el.querySelector('.requested-allocation-row')?.scrollIntoView({block:'center'})
},{immediate:true,flush:'post'})
const emit=defineEmits(['refresh','open-award'])
const {t}=useI18n({useScope:'local',messages})
const editOpen=ref(false),paymentOpen=ref(false),busy=ref(false),uploading=ref(false),form=reactive({lines:[]}),payment=reactive({})
const paymentBatch=ref(null)
const editBonusBlock=computed(()=>rowBonusBlock(form))
const paymentBonusBlock=computed(()=>rowBonusBlock(paymentBatch.value))
const paymentPerson=ref(''),paymentLimit=ref(0),paymentCurrency=ref('')
const money=v=>Number(v||0).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})
const key=()=>crypto.randomUUID()
const selectedAward=computed(()=>props.data.awards?.find(a=>a.awardId===form.awardId))
const cents=v=>Math.round(Number(v||0)*100)
const lineAmount=l=>form.mode==='PERCENT'?Math.round(cents(selectedAward.value?.sourceAmount)*Number(l.percentage||0)/100)/100:Number(l.amount||0)
const total=computed(()=>form.lines.reduce((sum,l)=>sum+cents(lineAmount(l)),0)/100)
const capacity=computed(()=>Number(selectedAward.value?.remaining||0)+Number(form.originalAmount||0))
function openEdit(row){if(!row)return;if(rowBonusBlock(row))return ElMessage.warning(t(rowBonusBlock(row)));Object.keys(form).forEach(k=>delete form[k]);Object.assign(form,{...row,originalAmount:row.amount,lines:row.lines.map(l=>({...l}))});editOpen.value=true}
async function save(){
 if(editBonusBlock.value)return ElMessage.warning(t(editBonusBlock.value))
 if(!form.awardId||!form.reason?.trim()||form.lines.some(l=>!l.userId||!l.reason?.trim()||lineAmount(l)<=0))return ElMessage.warning(t('required'))
 if(new Set(form.lines.map(l=>l.userId)).size!==form.lines.length||cents(total.value)>cents(capacity.value)||(form.mode==='PERCENT'&&form.lines.reduce((s,l)=>s+Number(l.percentage||0),0)>100))return ElMessage.warning(t('invalid'))
 busy.value=true;try{await saveBonusAllocation({allocationId:form.allocationId,awardId:form.awardId,version:form.version,mode:form.mode,reason:form.reason,requestKey:form.requestKey,lines:form.lines.map(l=>({userId:l.userId,amount:l.amount,percentage:l.percentage,reason:l.reason}))});editOpen.value=false;emit('refresh');ElMessage.success(t('saved'))}finally{busy.value=false}
}
async function act(row,action){if(busy.value)return;if(['SUBMITTED','APPROVED'].includes(action)&&rowBonusBlock(row))return ElMessage.warning(t(rowBonusBlock(row)));try{const {value}=await ElMessageBox.prompt(t('prompt'),t('confirm'),{inputType:'textarea',inputValidator:v=>!!v?.trim()&&v.trim().length<=500||t('required')});busy.value=true;await actBonusAllocation(row.allocationId,['APPROVED','RETURNED'].includes(action)?'review':'submit',{version:row.version,action,reason:value});emit('refresh')}catch(e){if(!['cancel','close'].includes(e))emit('refresh')}finally{busy.value=false}}
function openPayment(batch,line){if(rowBonusBlock(batch))return ElMessage.warning(t(rowBonusBlock(batch)));paymentBatch.value=batch;Object.assign(payment,{lineId:line.lineId,amount:null,paidDate:new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'}),method:'BANK',referenceNo:'',voucher:'',reason:'',requestKey:key()});paymentPerson.value=line.userName;paymentLimit.value=(cents(line.amount)-cents(line.paidAmount))/100;paymentCurrency.value=batch.currency;paymentOpen.value=true}
async function upload({file}){if(!/\.(png|jpe?g|webp|pdf)$/i.test(file.name)||file.size>10*1024*1024){ElMessage.warning(t('fileError'));throw new Error(t('fileError'))}uploading.value=true;try{const data=new FormData();data.append('file',file);const res=await request({url:'/common/upload',method:'post',headers:{'Content-Type':'multipart/form-data',repeatSubmit:false},data});payment.voucher=res.fileName}finally{uploading.value=false}}
async function pay(){if(paymentBonusBlock.value)return ElMessage.warning(t(paymentBonusBlock.value));if(!payment.amount||payment.amount>paymentLimit.value||!payment.paidDate||!payment.method||!payment.referenceNo?.trim()||!payment.voucher||!payment.reason?.trim())return ElMessage.warning(t('payInvalid'));busy.value=true;try{await recordBonusPayment(payment);paymentOpen.value=false;emit('refresh');ElMessage.success(t('saved'))}finally{busy.value=false}}
function voucherUrl(path){return typeof path==='string'&&path.startsWith('/profile/upload/')&&!path.includes('..')?import.meta.env.VITE_APP_BASE_API+path:'#'}
function isImageVoucher(path){return typeof path==='string'&&/\.(png|jpe?g|webp)(?:\?.*)?$/i.test(path)}
function memberPayments(row){return (row.batch?.payments||[]).filter(payment=>String(payment.lineId)===String(row.line.lineId))}
function eventLines(e){try{return JSON.parse(e.snapshot)?.lines||[]}catch{return []}}
watch(()=>props.data.project?.projectId,()=>{editOpen.value=false;paymentOpen.value=false})
</script>
<style scoped>
.distribution{padding:18px;background:white;border:1px solid #e4e9ef;border-radius:8px}.batch-details{padding:12px 24px}.allocation-line{display:grid;grid-template-columns:1fr 180px 1.4fr 50px;gap:12px;margin-bottom:12px}.allocation-line .el-input-number{width:100%}.allocation-line small{grid-column:2}.muted{font-size:12px;color:#718096}.row-actions{display:flex;flex-wrap:wrap;gap:4px}.el-collapse{margin-top:18px}.voucher-thumbnail{display:block;width:160px;height:110px;border-radius:6px;cursor:zoom-in}.voucher-file{display:flex;min-height:72px;align-items:center;justify-content:center}@media(max-width:650px){.allocation-line{grid-template-columns:1fr 1fr}.batch-details{padding:8px}.distribution{padding:12px}}
</style>
