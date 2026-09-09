<template>
 <section class="distribution">
  <div class="product-head"><div><h2>{{ t(data.personal?'personal':'title') }}</h2><p>{{ t(data.personal?'ownHint':'intro') }}</p></div>
   <el-button v-if="data.canAllocate" v-hasPermi="['business:incentive:apply']" type="primary" :disabled="!data.awards?.length" @click="openEdit()">{{ t('newBatch') }}</el-button>
  </div>
  <template v-if="!data.personal">
   <el-empty v-if="!data.awards?.length" :description="t('noAwards')" />
   <el-table v-else :data="data.awards">
    <el-table-column :label="t('award')" min-width="200"><template #default="{row}">{{ row.ruleName }} #{{ row.awardId }}<div class="muted">{{ t('score') }}: {{ row.score ?? '—' }}</div></template></el-table-column>
    <el-table-column v-for="key in ['total','reserved','remaining']" :key="key" :label="t(key)" min-width="145"><template #default="{row}">{{ money(row[key==='total'?'amount':key]) }} {{ row.currency }}</template></el-table-column>
   </el-table>
   <p class="muted">{{ t('reservedHint') }}</p>
  </template>
  <el-table :data="data.allocations||[]" :empty-text="t('empty')" row-key="allocationId">
   <el-table-column type="expand"><template #default="{row}">
    <div class="batch-details">
     <p v-if="row.reason">{{ row.reason }}</p>
     <el-table :data="row.lines">
      <el-table-column prop="userName" :label="t('person')" min-width="100" />
      <el-table-column :label="t('amount')"><template #default="{row:l}">{{ money(l.amount) }}</template></el-table-column>
      <el-table-column :label="t('paid')"><template #default="{row:l}">{{ money(l.paidAmount) }}</template></el-table-column>
      <el-table-column :label="t('state')"><template #default="{row:l}">{{ t(l.paymentStatus) }}</template></el-table-column>
      <el-table-column prop="reason" :label="t('reason')" min-width="160" />
      <el-table-column v-if="row.canPay" :label="t('actions')"><template #default="{row:l}"><el-button v-hasPermi="['business:incentive:pay']" link type="primary" :disabled="Number(l.paidAmount)>=Number(l.amount)" @click="openPayment(row,l)">{{ t('pay') }}</el-button></template></el-table-column>
     </el-table>
     <p v-if="data.canPay && row.status==='APPROVED' && !row.canPay" class="muted">{{ t('costPending') }}</p>
     <h4>{{ t('records') }}</h4>
     <el-table :data="row.payments" :empty-text="t('noPayment')">
      <el-table-column :label="t('person')"><template #default="{row:p}">{{ row.lines.find(l=>l.lineId===p.lineId)?.userName }}</template></el-table-column>
      <el-table-column :label="t('paidAmount')"><template #default="{row:p}">{{ money(p.amount) }}</template></el-table-column>
      <el-table-column prop="paidDate" :label="t('date')" />
      <el-table-column :label="t('method')"><template #default="{row:p}">{{ t(p.method) }}</template></el-table-column>
      <el-table-column prop="referenceNo" :label="t('reference')" min-width="150" />
      <el-table-column :label="t('voucher')"><template #default="{row:p}"><a :href="voucherUrl(p.voucher)" target="_blank" rel="noopener noreferrer">{{ t('voucher') }}</a></template></el-table-column>
      <el-table-column prop="recordedUserName" :label="t('operator')" />
     </el-table>
     <el-collapse v-if="row.events?.length"><el-collapse-item :title="t('events')" name="events">
      <el-timeline><el-timeline-item v-for="e in row.events" :key="e.eventId" :timestamp="e.createTime">
       <b>{{ t(e.eventType) }}</b> · {{ e.operatorName }} — {{ e.reason }}
       <div v-for="(line,index) in eventLines(e)" :key="index" class="muted">{{ line.userName }} · {{ money(line.amount) }} {{ row.currency }} · {{ line.reason }}</div>
      </el-timeline-item></el-timeline>
     </el-collapse-item></el-collapse>
    </div>
   </template></el-table-column>
   <el-table-column :label="t('batch')" min-width="170"><template #default="{row}">#{{ row.allocationId }} · {{ row.ruleName }}</template></el-table-column>
   <el-table-column :label="t('amount')"><template #default="{row}">{{ money(row.amount) }} {{ row.currency }}</template></el-table-column>
   <el-table-column :label="t('paid')"><template #default="{row}">{{ money(row.paidAmount) }}</template></el-table-column>
   <el-table-column :label="t('unpaid')"><template #default="{row}">{{ money(Number(row.amount)-Number(row.paidAmount)) }}</template></el-table-column>
   <el-table-column :label="t('allocationStatus')"><template #default="{row}"><el-tag>{{ t(row.status) }}</el-tag></template></el-table-column>
   <el-table-column :label="t('actions')" min-width="220"><template #default="{row}">
    <template v-if="row.canEdit"><el-button v-hasPermi="['business:incentive:apply']" link @click="openEdit(row)">{{ t('edit') }}</el-button><el-button v-hasPermi="['business:incentive:apply']" link type="primary" :disabled="busy" @click="act(row,'SUBMITTED')">{{ t('submit') }}</el-button><el-button v-hasPermi="['business:incentive:apply']" link type="danger" :disabled="busy" @click="act(row,'CANCELED')">{{ t('cancelBatch') }}</el-button></template>
    <template v-if="row.canReview"><el-button v-hasPermi="['business:incentive:approve']" link type="success" :disabled="busy" @click="act(row,'APPROVED')">{{ t('approve') }}</el-button><el-button v-hasPermi="['business:incentive:approve']" link type="warning" :disabled="busy" @click="act(row,'RETURNED')">{{ t('return') }}</el-button></template>
   </template></el-table-column>
  </el-table>
  <el-dialog v-model="editOpen" :title="t('newBatch')" width="min(950px,95vw)" append-to-body>
   <el-form label-position="top">
    <el-form-item :label="t('award')" required><el-select v-model="form.awardId" :disabled="!!form.allocationId" style="width:100%"><el-option v-for="a in data.awards" :key="a.awardId" :value="a.awardId" :label="a.ruleName+' #'+a.awardId+' · '+money(a.amount)+' '+a.currency" /></el-select></el-form-item>
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
   <template #footer><el-button @click="editOpen=false">{{ t('cancel') }}</el-button><el-button type="primary" :loading="busy" @click="save">{{ t('save') }}</el-button></template>
  </el-dialog>
  <el-dialog v-model="paymentOpen" :title="t('paymentTitle')" width="min(640px,95vw)" append-to-body>
   <el-alert :title="t('payHint')" type="info" :closable="false" />
   <p><b>{{ paymentPerson }}</b> · {{ t('unpaid') }}: {{ money(paymentLimit) }} {{ paymentCurrency }}</p>
   <el-form label-position="top">
    <el-form-item :label="t('paidAmount')" required><el-input-number v-model="payment.amount" :min="0" :max="paymentLimit" :precision="2" :aria-label="t('paidAmount')" /></el-form-item>
    <el-form-item :label="t('date')" required><el-date-picker v-model="payment.paidDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
    <el-form-item :label="t('method')" required><el-select v-model="payment.method"><el-option v-for="m in ['BANK','WECHAT','ALIPAY','CASH','OTHER']" :key="m" :label="t(m)" :value="m" /></el-select></el-form-item>
    <el-form-item :label="t('reference')" required><el-input v-model="payment.referenceNo" maxlength="100" /></el-form-item>
    <el-form-item :label="t('voucher')" required><el-upload action="#" :http-request="upload" :show-file-list="false" accept=".png,.jpg,.jpeg,.webp,.pdf" :disabled="uploading"><el-button :loading="uploading">{{ t('upload') }}</el-button></el-upload><span v-if="payment.voucher">{{ t('uploadOk') }}</span></el-form-item>
    <el-form-item :label="t('payReason')" required><el-input v-model="payment.reason" type="textarea" maxlength="500" /></el-form-item>
   </el-form>
   <template #footer><el-button @click="paymentOpen=false">{{ t('cancel') }}</el-button><el-button type="primary" :loading="busy" :disabled="uploading" @click="pay">{{ t('record') }}</el-button></template>
  </el-dialog>
 </section>
</template>
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { saveBonusAllocation, actBonusAllocation, recordBonusPayment } from '@/api/business/incentive'
import messages from './distributionMessages'
const props=defineProps({data:{type:Object,default:()=>({})}})
const emit=defineEmits(['refresh'])
const {t}=useI18n({useScope:'local',messages})
const editOpen=ref(false),paymentOpen=ref(false),busy=ref(false),uploading=ref(false),form=reactive({lines:[]}),payment=reactive({})
const paymentPerson=ref(''),paymentLimit=ref(0),paymentCurrency=ref('')
const money=v=>Number(v||0).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})
const key=()=>crypto.randomUUID()
const selectedAward=computed(()=>props.data.awards?.find(a=>a.awardId===form.awardId))
const cents=v=>Math.round(Number(v||0)*100)
const lineAmount=l=>form.mode==='PERCENT'?Math.round(cents(selectedAward.value?.amount)*Number(l.percentage||0)/100)/100:Number(l.amount||0)
const total=computed(()=>form.lines.reduce((sum,l)=>sum+cents(lineAmount(l)),0)/100)
const capacity=computed(()=>Number(selectedAward.value?.remaining||0)+Number(form.originalAmount||0))
function openEdit(row){Object.keys(form).forEach(k=>delete form[k]);Object.assign(form,row?{...row,originalAmount:row.amount,lines:row.lines.map(l=>({...l}))}:{awardId:props.data.awards?.[0]?.awardId,mode:'AMOUNT',reason:'',requestKey:key(),lines:[{userId:null,amount:null,percentage:null,reason:''}]});editOpen.value=true}
async function save(){
 if(!form.awardId||!form.reason?.trim()||form.lines.some(l=>!l.userId||!l.reason?.trim()||lineAmount(l)<=0))return ElMessage.warning(t('required'))
 if(new Set(form.lines.map(l=>l.userId)).size!==form.lines.length||cents(total.value)>cents(capacity.value)||(form.mode==='PERCENT'&&form.lines.reduce((s,l)=>s+Number(l.percentage||0),0)>100))return ElMessage.warning(t('invalid'))
 busy.value=true;try{await saveBonusAllocation({allocationId:form.allocationId,awardId:form.awardId,version:form.version,mode:form.mode,reason:form.reason,requestKey:form.requestKey,lines:form.lines.map(l=>({userId:l.userId,amount:l.amount,percentage:l.percentage,reason:l.reason}))});editOpen.value=false;emit('refresh');ElMessage.success(t('saved'))}finally{busy.value=false}
}
async function act(row,action){if(busy.value)return;try{const {value}=await ElMessageBox.prompt(t('prompt'),t('confirm'),{inputType:'textarea',inputValidator:v=>!!v?.trim()&&v.trim().length<=500||t('required')});busy.value=true;await actBonusAllocation(row.allocationId,['APPROVED','RETURNED'].includes(action)?'review':'submit',{version:row.version,action,reason:value});emit('refresh')}catch(e){if(!['cancel','close'].includes(e))emit('refresh')}finally{busy.value=false}}
function openPayment(batch,line){Object.assign(payment,{lineId:line.lineId,amount:null,paidDate:new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'}),method:'BANK',referenceNo:'',voucher:'',reason:'',requestKey:key()});paymentPerson.value=line.userName;paymentLimit.value=(cents(line.amount)-cents(line.paidAmount))/100;paymentCurrency.value=batch.currency;paymentOpen.value=true}
async function upload({file}){if(!/\.(png|jpe?g|webp|pdf)$/i.test(file.name)||file.size>10*1024*1024){ElMessage.warning(t('fileError'));throw new Error(t('fileError'))}uploading.value=true;try{const data=new FormData();data.append('file',file);const res=await request({url:'/common/upload',method:'post',headers:{'Content-Type':'multipart/form-data',repeatSubmit:false},data});payment.voucher=res.fileName}finally{uploading.value=false}}
async function pay(){if(!payment.amount||payment.amount>paymentLimit.value||!payment.paidDate||!payment.method||!payment.referenceNo?.trim()||!payment.voucher||!payment.reason?.trim())return ElMessage.warning(t('payInvalid'));busy.value=true;try{await recordBonusPayment(payment);paymentOpen.value=false;emit('refresh');ElMessage.success(t('saved'))}finally{busy.value=false}}
function voucherUrl(path){return typeof path==='string'&&path.startsWith('/profile/upload/')&&!path.includes('..')?import.meta.env.VITE_APP_BASE_API+path:'#'}
function eventLines(e){try{return JSON.parse(e.snapshot)?.lines||[]}catch{return []}}
watch(()=>props.data.project?.projectId,()=>{editOpen.value=false;paymentOpen.value=false})
</script>
<style scoped>
.distribution{padding:18px;background:white;border:1px solid #e4e9ef;border-radius:8px}.batch-details{padding:12px 24px}.allocation-line{display:grid;grid-template-columns:1fr 180px 1.4fr 50px;gap:12px;margin-bottom:12px}.allocation-line .el-input-number{width:100%}.allocation-line small{grid-column:2}.muted{font-size:12px;color:#718096}.el-collapse{margin-top:18px}@media(max-width:650px){.allocation-line{grid-template-columns:1fr 1fr}.batch-details{padding:8px}.distribution{padding:12px}}
</style>
