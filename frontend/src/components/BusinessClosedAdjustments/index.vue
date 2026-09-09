<template>
 <section v-if="canView" class="adjustments" v-loading="loading">
  <div class="heading"><h3>关账后调整</h3><div><el-button link @click="load">刷新</el-button><el-button v-if="canApply" type="primary" size="small" @click="open">申请调整</el-button></div></div>
  <p>调整单审核通过后计入审核当日，增加或减少当期经营结果，原关账流水及结果保留。</p>
  <el-alert v-if="failed" title="调整记录加载失败，请刷新后核对。" type="error" :closable="false" />
  <el-table v-else :data="rows" size="small" empty-text="暂无关账后调整">
   <el-table-column prop="id" label="单号" width="70" />
   <el-table-column label="原业务日 / 入账日" min-width="170"><template #default="{row}">{{ String(row.business_date).slice(0,10) }} / {{ row.posting_date?String(row.posting_date).slice(0,10):'待审核' }}</template></el-table-column>
   <el-table-column label="经营结果差额" min-width="140"><template #default="{row}">{{ Number(row.profit_delta)>0?'+':'' }}{{ Number(row.profit_delta).toFixed(2) }} {{ row.currency }}</template></el-table-column>
   <el-table-column prop="reason" label="原因及依据" min-width="180" />
   <el-table-column label="状态" width="90"><template #default="{row}">{{ {PENDING:'待审核',APPROVED:'已入账',REJECTED:'已驳回'}[row.status] }}</template></el-table-column>
   <el-table-column prop="review_comment" label="审核意见" min-width="150" />
   <el-table-column v-if="canReview" label="审核" width="110"><template #default="{row}"><template v-if="row.status==='PENDING'"><el-button link type="primary" :disabled="saving" @click="review(row,'APPROVED')">通过</el-button><el-button link type="danger" :disabled="saving" @click="review(row,'REJECTED')">驳回</el-button></template></template></el-table-column>
  </el-table>
  <el-dialog v-model="visible" title="申请关账后调整" width="min(560px,94vw)" append-to-body :close-on-click-modal="false">
   <el-form label-width="100px">
    <el-form-item label="原业务日期" required><el-date-picker v-model="form.businessDate" value-format="YYYY-MM-DD" :disabled-date="disabledDate" /></el-form-item>
    <el-form-item label="原流水编号"><el-input-number v-model="form.originalFactId" :min="1" :precision="0" /><small>漏记业务没有原流水时可留空</small></el-form-item>
    <el-form-item label="调整方向"><el-radio-group v-model="direction"><el-radio value="INCREASE">增加经营结果</el-radio><el-radio value="DECREASE">减少经营结果</el-radio></el-radio-group></el-form-item>
    <el-form-item label="差额金额" required><el-input-number v-model="amount" :min="0.01" :max="999999999999.99" :precision="2" /><span>{{ project.baseCurrency||project.currency }}</span></el-form-item>
    <el-alert title="例如：补记收入选增加，补记费用选减少；金额仅填写本次差额。" type="info" :closable="false" />
    <el-form-item label="原因及依据" required><el-input v-model="form.reason" type="textarea" maxlength="2000" show-word-limit /></el-form-item>
   </el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">提交归属老板审核</el-button></template>
  </el-dialog>
 </section>
</template>
<script setup>
import {ref,computed,watch} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import useUserStore from '@/store/modules/user'
import {getClosedAdjustments,requestClosedAdjustment,reviewClosedAdjustment} from '@/api/business/flow'
import {newSubmissionId} from '@/utils/submission'
const props=defineProps({project:{type:Object,required:true}}),emit=defineEmits(['changed']),user=useUserStore()
const rows=ref([]),loading=ref(false),failed=ref(false),saving=ref(false),visible=ref(false),form=ref({}),amount=ref(null),direction=ref('DECREASE')
const permitted=p=>user.permissions.includes('*:*:*')||user.permissions.includes(p)
const canView=computed(()=>Number(user.id)===1||[props.project.sponsorOwnerUserId||props.project.initiatorUserId,props.project.mainOwnerUserId].some(id=>id!=null&&Number(id)===Number(user.id)))
const canApply=computed(()=>permitted('business:accounting:add')||permitted('business:project:report'))
const canReview=computed(()=>Number(user.id)===Number(props.project.sponsorOwnerUserId||props.project.initiatorUserId)&&permitted('business:accounting:close'))
let sequence=0
async function load(){const seq=++sequence;if(!canView.value){rows.value=[];loading.value=false;return}loading.value=true;failed.value=false;try{const r=await getClosedAdjustments(props.project.projectId);if(seq===sequence)rows.value=r.data||[]}catch{if(seq===sequence)failed.value=true}finally{if(seq===sequence)loading.value=false}}
function open(){form.value={requestId:newSubmissionId(),businessDate:'',originalFactId:undefined,reason:''};amount.value=null;direction.value='DECREASE';visible.value=true}
function disabledDate(d){const end=new Date(props.project.actualEndDate||Date.now());return d>new Date()||d>end}
async function submit(){if(!form.value.businessDate||!form.value.reason.trim()||!(amount.value>0))return ElMessage.warning('请填写业务日期、金额和原因');saving.value=true;try{await requestClosedAdjustment(props.project.projectId,{...form.value,profitDelta:direction.value==='DECREASE'?-amount.value:amount.value});visible.value=false;await load();emit('changed');ElMessage.success('调整单已提交')}finally{saving.value=false}}
async function review(row,decision){const {value}=await ElMessageBox.prompt(decision==='APPROVED'?'通过后按今天日期入账，原关账结果保留。请填写审核意见。':'请填写驳回原因。','审核调整单',{inputValidator:v=>!!v?.trim()||'请填写意见'});saving.value=true;try{await reviewClosedAdjustment(row.id,{decision,comment:value});await load();emit('changed');ElMessage.success('审核已完成')}finally{saving.value=false}}
watch(()=>props.project.projectId,()=>{visible.value=false;load()},{immediate:true})
</script>
<style scoped>
.adjustments{margin-top:16px;padding:16px;border:1px solid #e2e8f0;border-radius:8px}.heading{display:flex;justify-content:space-between;align-items:center}.heading h3{margin:0}.adjustments p,small{color:#718096;font-size:12px;line-height:1.6}.el-form-item{margin-top:16px}
</style>
