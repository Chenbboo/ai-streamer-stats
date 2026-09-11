<template>
  <section v-loading="loading" class="member-cost-panel">
    <div class="heading"><div><h3>人员工作日成本</h3><p>同一负责人名下直接调整；涉及其他负责人的分配，由相关负责人全部确认后统一生效。</p></div><div class="heading-actions"><el-button v-if="canManage" type="primary" @click="openAllocation">申请调整投入</el-button><el-button icon="Refresh" @click="load">刷新</el-button></div></div>
    <el-alert v-if="data.overdue" title="项目已超过计划结束日，仍参与的成员继续按工作日计费，请更新项目计划。" type="warning" :closable="false" show-icon />
    <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" @change="load" />
    <el-alert v-if="data.pendingCount" title="部分人员成本待完善，请查看下方说明，确认投入分配或完善成本、工作日历。" type="warning" :closable="false" show-icon />
    <p>本期累计：<b>{{ data.totalAmount == null ? '待完善成本' : money(data.totalAmount) + ' ' + (data.currency || '') }}</b><span class="hint">（截至今天）</span></p>
    <el-table :data="data.rows || []" empty-text="所选期间没有应计费的成员工作日">
      <el-table-column prop="userName" label="成员" min-width="100" />
      <el-table-column label="计费日期" min-width="215"><template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template></el-table-column>
      <el-table-column prop="workingDays" label="工作日数" width="110" />
      <el-table-column label="人员成本" min-width="135"><template #default="{ row }">{{ row.amount == null ? '待完善' : money(row.amount) + ' ' + data.currency }}</template></el-table-column>
      <el-table-column label="说明" min-width="180"><template #default="{ row }">{{ row.issues?.join('；') || '已按工作日和投入权重计算' }}</template></el-table-column>
    </el-table>

    <el-dialog v-model="allocationDialog" title="设置人员跨项目投入权重" width="min(720px, 95vw)" :z-index="4000" append-to-body destroy-on-close>
      <el-alert title="填写该员工全部项目的投入比例，合计必须为100%。涉及其他负责人时提交确认；确认完成前继续使用原分配。" type="info" :closable="false" show-icon />
      <el-form label-width="92px" class="allocation-form">
        <el-row :gutter="12">
          <el-col :sm="12" :xs="24"><el-form-item label="调整人员" required><el-select v-model="allocationUserId" :disabled="allocationSaving" :teleported="false" filterable placeholder="选择负责人或普通员工" style="width:100%" @change="loadAllocation"><el-option v-for="member in editableMembers" :key="member.userId" :label="memberLabel(member)" :value="member.userId" /></el-select><small class="person-hint">普通员工也在这里选择；观察者不计人员成本。</small></el-form-item></el-col>
          <el-col :sm="12" :xs="24"><el-form-item label="生效日期" required><el-date-picker v-model="allocationDate" :disabled="!!pendingRequest || allocationSaving" :clearable="false" :teleported="false" type="date" value-format="YYYY-MM-DD" style="width:100%" @change="loadAllocation" /></el-form-item></el-col>
        </el-row>
        <div v-loading="allocationLoading" class="allocation-table">
          <el-table :data="displayAllocations" empty-text="该日期没有参与中的有效项目">
            <el-table-column label="项目" min-width="210"><template #default="{row}"><b>{{ row.projectName }}</b><small>{{ row.projectNo }}</small></template></el-table-column>
            <el-table-column prop="ownerName" label="项目负责人" min-width="110" />
            <el-table-column label="原投入" width="85"><template #default="{row}">{{ pendingRequest ? row.allocationValue : originalValues[row.projectId] }}%<small v-if="row.confirmationStatus==='PENDING'">待确认</small></template></el-table-column>
            <el-table-column label="调整后" width="190"><template #default="{row}"><b v-if="pendingRequest">{{ row.requestedValue }}%</b><template v-else><el-input-number v-model="row.allocationValue" :disabled="allocationLoading || allocationSaving" :min="0" :max="100" :precision="2" :step="5" /><span class="percent">%</span></template></template></el-table-column>
          </el-table>
        </div>
        <div :class="['allocation-total', {valid:allocationTotal===100,invalid:allocationTotal!==100}]"><span>权重合计</span><b>{{ allocationTotal.toFixed(2) }}%</b><small v-if="allocationTotal<100">还差 {{ (100-allocationTotal).toFixed(2) }}%</small><small v-else-if="allocationTotal>100">超出 {{ (allocationTotal-100).toFixed(2) }}%</small><small v-else>{{ pendingRequest ? '等待确认' : '合计正确' }}</small></div>
        <template v-if="pendingRequest">
          <el-alert :title="`${pendingRequest.applicantName}发起的调整待确认`" :description="pendingRequest.reason" type="warning" :closable="false" />
          <p v-for="review in pendingRequest.reviews" :key="review.ownerUserId">{{ review.ownerName }}：{{ requestStatus[review.status] || review.status }}<span v-if="review.comment"> · {{ review.comment }}</span></p>
          <el-form-item v-if="pendingRequest.canReview" label="处理说明"><el-input v-model="reviewComment" type="textarea" :rows="2" maxlength="500" placeholder="退回时必须填写原因" /></el-form-item>
        </template>
        <el-form-item v-else label="调整原因" required><el-input v-model="allocationReason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明本次项目投入权重调整原因" /></el-form-item>
        <el-collapse v-if="allocation.history?.length"><el-collapse-item title="最近调整记录" name="history"><article v-for="record in allocation.history" :key="record.requestId" class="allocation-history"><b>{{ record.applicantName }} · {{ requestStatus[record.status] }} · {{ record.effectiveDate }}生效</b><p>{{ record.reason }}<span v-if="record.closeReason"> · {{ record.closeReason }}</span></p><p v-for="row in record.projects" :key="row.projectId">{{ row.projectName }}：{{ row.allocationValue }}% → {{ row.requestedValue }}%</p></article></el-collapse-item></el-collapse>
      </el-form>
      <template #footer><el-button @click="allocationDialog=false">关闭</el-button><template v-if="pendingRequest"><el-button v-if="pendingRequest.canWithdraw" :disabled="allocationSaving" @click="reviewAllocation('WITHDRAWN')">撤回申请</el-button><el-button v-if="pendingRequest.canReview" type="danger" plain :disabled="allocationSaving" @click="reviewAllocation('REJECTED')">退回</el-button><el-button v-if="pendingRequest.canReview" type="primary" :loading="allocationSaving" @click="reviewAllocation('APPROVED')">确认本次调配</el-button></template><el-button v-else type="primary" :loading="allocationSaving" :disabled="allocationLoading||allocationTotal!==100||!allocation.projects?.length" @click="saveAllocation">{{ needsConfirmation ? '提交相关负责人确认' : '保存并生效' }}</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectWork } from '@/api/business/projectWork'
import { getBusinessStaffAllocationWorkspace, saveBusinessStaffAllocationWorkspace, reviewBusinessStaffAllocation } from '@/api/business/project'
import useUserStore from '@/store/modules/user'
import { parseTime } from '@/utils/ruoyi'
const props = defineProps({ projectId: [Number, String], members: { type: Array, default: () => [] }, canManage: Boolean })
const emit = defineEmits(['changed'])
const now = new Date()
const dates = ref([parseTime(new Date(now.getFullYear(), now.getMonth(), 1), '{y}-{m}-{d}'), parseTime(now, '{y}-{m}-{d}')])
const data = ref({}), loading = ref(false)
const allocationDialog=ref(false),allocationLoading=ref(false),allocationSaving=ref(false),allocation=ref({projects:[]}),allocationUserId=ref(null),allocationDate=ref(parseTime(now,'{y}-{m}-{d}')),allocationReason=ref('')
const editableMembers=computed(()=>(props.members||[]).filter(member=>String(member.status??'0')==='0'&&member.memberRole!=='OBSERVER'))
const userStore=useUserStore(),originalValues=ref({}),reviewComment=ref('')
const pendingRequest=computed(()=>allocation.value.pendingRequest)
const displayAllocations=computed(()=>pendingRequest.value?.projects || allocation.value.projects || [])
const allocationTotal=computed(()=>Math.round(displayAllocations.value.reduce((sum,row)=>sum+Number((pendingRequest.value?row.requestedValue:row.allocationValue)||0),0)*100)/100)
const needsConfirmation=computed(()=>(allocation.value.projects||[]).some(row=>Number(row.ownerUserId)!==Number(userStore.id)&&(Number(row.allocationValue)!==Number(originalValues.value[row.projectId])||row.confirmationStatus==='PENDING')))
const requestStatus={PENDING:'待确认',APPROVED:'已确认',APPLIED:'已确认生效',REJECTED:'已退回',WITHDRAWN:'已撤回',INVALIDATED:'已失效，请重新发起'}
let allocationSequence=0
const money = value => Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const memberRoleLabel={OWNER:'主负责人',DEPUTY:'副负责人',MEMBER:'普通员工'}
const memberLabel=member=>`${member.userNameSnapshot}${member.accountName?`（${member.accountName}）`:''} · ${memberRoleLabel[member.memberRole]||'项目成员'}`
let sequence = 0
async function load() {
  const current = ++sequence
  if (!props.projectId || !dates.value?.length) return
  loading.value = true
  try {
    const response = await getProjectWork(props.projectId, { dateFrom: dates.value[0], dateTo: dates.value[1] })
    if (current === sequence) data.value = response.data || {}
  } finally { if (current === sequence) loading.value = false }
}
async function openAllocation(userId){
  if(!editableMembers.value.length)return ElMessage.warning('当前项目没有可设置投入权重的负责人或成员')
  allocationUserId.value=editableMembers.value.some(member=>Number(member.userId)===Number(userId))?userId:editableMembers.value[0].userId
  allocationDate.value=parseTime(new Date(),'{y}-{m}-{d}')
  allocationReason.value=''
  allocationDialog.value=true
  await loadAllocation()
}
async function loadAllocation(){
  const sequence=++allocationSequence
  allocation.value={projects:[]};reviewComment.value=''
  if(!allocationUserId.value||!allocationDate.value)return
  allocationLoading.value=true
  try{const response=(await getBusinessStaffAllocationWorkspace({userId:allocationUserId.value,effectiveDate:allocationDate.value})).data||{projects:[]};if(sequence!==allocationSequence)return;allocation.value=response;originalValues.value=Object.fromEntries((response.projects||[]).map(row=>[row.projectId,row.allocationValue]));if(response.pendingRequest)allocationDate.value=response.pendingRequest.effectiveDate}
  finally{if(sequence===allocationSequence)allocationLoading.value=false}
}
async function saveAllocation(){
  if(allocationLoading.value||allocationSaving.value||pendingRequest.value)return
  if(allocationTotal.value!==100)return ElMessage.warning('全部项目投入权重合计必须为100%')
  if(!allocationReason.value.trim())return ElMessage.warning('请填写调整原因')
  allocationSaving.value=true
  try{
    allocation.value=(await saveBusinessStaffAllocationWorkspace({userId:allocationUserId.value,effectiveDate:allocationDate.value,reason:allocationReason.value.trim(),versionToken:allocation.value.versionToken,allocations:(allocation.value.projects||[]).map(row=>({projectId:row.projectId,allocationValue:row.allocationValue}))})).data||allocation.value
    const pending=allocation.value.outcome==='PENDING'
    if(!pending)allocationDialog.value=false
    await load()
    emit('changed')
    ElMessage.success(pending?'已提交相关负责人确认，原分配继续使用':'投入分配已保存，按生效日期重新计算')
  }finally{allocationSaving.value=false}
}
async function reviewAllocation(decision){
  if(allocationSaving.value)return
  if(decision==='REJECTED'&&!reviewComment.value.trim())return ElMessage.warning('请填写退回原因')
  allocationSaving.value=true
  try{const response=await reviewBusinessStaffAllocation(pendingRequest.value.requestId,{decision,comment:reviewComment.value.trim()});ElMessage.success(requestStatus[response.data.status]||'已确认，等待其他负责人');await loadAllocation();await load();emit('changed')}
  finally{allocationSaving.value=false}
}
defineExpose({openAllocation})
watch(() => props.projectId, () => { data.value = {}; load() }, { immediate: true })
</script>
<style scoped>
.heading{display:flex;justify-content:space-between;align-items:center;gap:16px}.heading h3{margin:0}.heading p,.hint{color:#8492a3}.heading-actions{display:flex;gap:8px}.member-cost-panel :deep(.el-alert){margin-top:16px}.member-cost-panel :deep(.el-date-editor){max-width:100%}.allocation-form{margin-top:18px}.person-hint{display:block;margin-top:5px;color:#8492a3;line-height:1.5}.allocation-table small{display:block;margin-top:3px;color:#909399}.allocation-table :deep(.el-input-number){width:145px}.percent{margin-left:6px;color:#606266}.allocation-total{display:grid;grid-template-columns:1fr auto auto;align-items:center;gap:14px;margin:14px 0;padding:14px 16px;border-radius:8px;background:#f5f7fa}.allocation-total b{font-size:20px}.allocation-total small{min-width:78px;text-align:right}.allocation-total.valid{background:#edf8f3;color:#237a57}.allocation-total.invalid{background:#fff6e8;color:#c47a13}@media(max-width:640px){.heading{align-items:flex-start;flex-direction:column}.heading-actions{width:100%;flex-wrap:wrap}.allocation-total{grid-template-columns:1fr auto}.allocation-total small{grid-column:1/-1;text-align:left}}
</style>
