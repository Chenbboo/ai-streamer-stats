<template>
  <el-dialog v-model="visible" class="allocation-schedule-dialog" :title="$tr('设置人员跨项目投入比例')" width="min(980px, 95vw)" top="4vh" :z-index="4100" append-to-body destroy-on-close :close-on-click-modal="false">
    <el-alert :title="$tr('按时间段核对历史、当前和未来投入，同期全部项目合计100%。涉及其他负责人时需确认；已删除项目不能在此修改，已关账项目的金额通过关账后调整处理。')" type="info" show-icon :closable="false" />
    <el-form label-width="92px" class="schedule-form" v-loading="opening || previewing" @submit.prevent>
      <el-form-item :label="$tr('调整人员')" required>
        <el-select v-model="userId" filterable :disabled="saving || opening" :popper-style="{zIndex:4200}" style="width:100%" @change="selectMember()">
          <el-option v-for="member in members" :key="member.userId" :label="member.userName || member.userNameSnapshot" :value="member.userId" />
        </el-select>
      </el-form-item>
      <el-alert v-if="selectedMember?.gapFrom && !pending" :title="$tr('发现历史投入缺口：{0} 至 {1}。可在下方新增时间段核对设置。',[selectedMember.gapFrom,selectedMember.gapTo])" type="warning" :closable="false">
        <el-button link type="primary" :disabled="busy" @click="addGap">{{ $tr('添加缺口时间段') }}</el-button>
      </el-alert>

      <AllocationTimeline v-if="periods.length" :periods="periods" :busy="busy" :pending="!!pending" @adjust="adjustPeriod" />

      <template v-if="pending">
        <el-alert :title="$tr('{0}发起的调整待确认',[pending.applicantName])" :description="pending.reason" type="warning" show-icon :closable="false" />
        <article v-for="(segment,index) in pendingSegments" :key="index" class="schedule-segment">
          <h4>{{ periodLabel(segment) }}</h4>
          <el-table :data="segment.projects || []" size="small">
            <el-table-column prop="projectName" :label="$tr('项目')" min-width="180" />
            <el-table-column prop="ownerName" :label="$tr('项目负责人')" min-width="110" />
            <el-table-column :label="$tr('原投入')" width="100"><template #default="{row}">{{ row.allocationMissing ? $tr('未设置') : percent(row.allocationValue) }}</template></el-table-column>
            <el-table-column :label="$tr('调整后')" width="100"><template #default="{row}">{{ percent(row.requestedValue) }}</template></el-table-column>
          </el-table>
          <CostImpact :segment="segment" :user-name="selectedMember?.userName || selectedMember?.userNameSnapshot || ''" />
        </article>
        <p v-for="review in pending.reviews" :key="review.ownerUserId">{{ review.ownerName }} · {{ statusLabel(review.status) }}<span v-if="review.comment"> · {{ review.comment }}</span></p>
        <el-form-item v-if="pending.canReview" :label="$tr('处理说明')"><el-input v-model="comment" type="textarea" :rows="2" maxlength="500" :placeholder="$tr('退回时必须填写原因')" /></el-form-item>
      </template>

      <template v-else>
        <div class="schedule-toolbar"><b>{{ $tr('投入时间段') }}</b><el-button type="primary" plain :disabled="busy || segments.length>=20" @click="addSegment">{{ $tr('新增时间段') }}</el-button></div>
        <el-collapse v-model="expanded">
          <el-collapse-item v-for="(segment,index) in segments" :key="segment.key" :name="segment.key">
            <template #title><div class="segment-title"><b>{{ $tr('时间段 {0}',[index+1]) }}</b><span>{{ periodLabel(segment) }}</span><el-tag v-if="segment.workspace" :type="segmentTotal(segment)===100?'success':'warning'" size="small">{{ percent(segmentTotal(segment)) }}</el-tag></div></template>
            <section v-loading="segment.loading" class="schedule-segment" :data-allocation-segment="segment.key">
              <div class="segment-dates">
                <el-form-item :label="$tr('开始日期')" required><el-date-picker v-model="segment.dateFrom" type="date" value-format="YYYY-MM-DD" :clearable="false" :disabled="saving" :popper-style="{zIndex:4200}" style="width:100%" @change="loadSegment(segment)" /></el-form-item>
                <el-form-item :label="$tr('结束日期')"><el-date-picker v-model="segment.dateTo" type="date" value-format="YYYY-MM-DD" :placeholder="$tr('留空持续生效')" :disabled="saving" :popper-style="{zIndex:4200}" style="width:100%" @change="loadSegment(segment)" /></el-form-item>
              </div>
              <p v-if="segment.workspace?.preservedFrom" class="schedule-help">{{ $tr('为保留已有记录及参与日期，本段截至 {0}；{1} 起的原记录保留，可另增时间段调整。',[segment.workspace.dateTo,segment.workspace.preservedFrom]) }}</p>
              <p v-else class="schedule-help">{{ $tr('起止日期均包含当天。结束日期留空时持续生效；遇到后续已有记录或参与变化，自动保留后续安排。') }}</p>
              <el-alert v-if="segment.error" :title="segment.error" type="warning" :closable="false" />
              <el-table v-if="segment.workspace" :data="segment.workspace.projects">
                <el-table-column :label="$tr('项目')" min-width="180"><template #default="{row}"><b>{{ row.projectName }}</b><small>{{ row.projectNo }}</small><el-tag v-if="row.frozen" size="small" type="info">{{ lockLabel(row) }}</el-tag></template></el-table-column>
                <el-table-column prop="ownerName" :label="$tr('项目负责人')" min-width="100" />
                <el-table-column :label="$tr('原投入')" min-width="145"><template #default="{row}"><span :class="{'frozen-missing':row.frozen && row.allocationMissing}">{{ row.allocationMissing ? $tr('未设置') : percent(row.originalValue) }}</span><small v-if="row.frozen" class="frozen-missing">{{ lockHint(row) }}</small></template></el-table-column>
                <el-table-column :label="$tr('调整后')" width="170"><template #default="{row}"><div class="schedule-percent"><el-input-number v-model="row.allocationValue" :min="0" :max="100" :precision="2" :step="5" controls-position="right" :disabled="row.frozen || saving" @change="invalidate" /><span>%</span></div></template></el-table-column>
              </el-table>
              <div class="segment-footer"><span :class="{invalid:segmentTotal(segment)!==100}">{{ $tr('投入合计') }} <b>{{ percent(segmentTotal(segment)) }}</b></span><div><el-button link type="primary" :disabled="busy || segments.length>=20" @click="addSegment(segment)">{{ $tr('复制比例到下一段') }}</el-button><el-button link type="danger" :disabled="busy || segments.length===1" @click="removeSegment(segment)">{{ $tr('删除时间段') }}</el-button></div></div>
            </section>
          </el-collapse-item>
        </el-collapse>
        <el-form-item :label="$tr('调整原因')" required><el-input v-model="reason" type="textarea" :rows="2" maxlength="500" show-word-limit :placeholder="$tr('说明各时间段的实际投入及调整依据')" /></el-form-item>
        <section v-if="preview" :ref="element => previewSection = element" class="schedule-preview">
          <div class="preview-heading"><div><h4>{{ $tr('本次调整预览') }}</h4><span>{{ selectedMember?.userName || selectedMember?.userNameSnapshot }}</span></div><el-tag v-if="preview.needsConfirmation" type="warning" effect="plain">{{ $tr('需相关负责人确认') }}</el-tag></div>
          <article v-for="(segment,index) in preview.segments" :key="index" class="preview-period">
            <div class="preview-period__heading"><b>{{ $tr('时间段 {0}',[index+1]) }}</b><span>{{ periodLabel(segment) }}</span></div>
            <CostImpact :segment="segment" :user-name="selectedMember?.userName || selectedMember?.userNameSnapshot || ''" />
          </article>
          <p v-if="preview.needsConfirmation" class="preview-confirmation-note">{{ $tr('全部负责人确认后生效，确认前保留原比例和成本。') }}</p>
        </section>
      </template>
      <el-collapse v-if="records.length || history.length" v-model="recordExpanded" class="schedule-records">
        <el-collapse-item v-if="records.length" :title="$tr('查看原始记录（{0}条）',[records.length])" name="records">
          <p class="schedule-help">{{ $tr('这里保留保存时的原始比例；实际生效分配请以上方时间段汇总为准，未修改的期间保留。') }}</p>
          <el-table :data="records" size="small"><el-table-column :label="$tr('项目')" min-width="180"><template #default="{row}"><b>{{ row.projectName }}</b><small>{{ recordProject(row).projectNo }}</small><el-tag v-if="lockLabel(recordProject(row))" size="small" type="info">{{ lockLabel(recordProject(row)) }}</el-tag></template></el-table-column><el-table-column :label="$tr('期间')" min-width="210"><template #default="{row}">{{ periodLabel({dateFrom:shortDate(row.effectiveFrom),dateTo:shortDate(row.effectiveTo)}) }}</template></el-table-column><el-table-column :label="$tr('原始投入比例')" width="110"><template #default="{row}">{{ percent(row.allocationValue) }}</template></el-table-column><el-table-column :label="$tr('确认状态')" width="90"><template #default="{row}">{{ statusLabel(row.confirmationStatus) }}</template></el-table-column></el-table>
        </el-collapse-item>
        <el-collapse-item v-if="history.length" :title="$tr('最近调整记录')" name="history">
          <article v-for="record in history" :key="record.requestId" class="schedule-segment"><b>{{ record.applicantName }} · {{ statusLabel(record.status) }}</b><p>{{ record.reason }}</p><p v-for="(segment,index) in requestSegments(record)" :key="index">{{ periodLabel(segment) }}：{{ (segment.projects||[]).map(row=>`${row.projectName} ${percent(row.requestedValue)}`).join('；') }}</p><p v-for="review in record.reviews" :key="review.ownerUserId">{{ review.ownerName }} · {{ statusLabel(review.status) }} {{ review.comment }}</p><p v-if="record.closeReason">{{ record.closeReason }}</p></article>
        </el-collapse-item>
      </el-collapse>
    </el-form>
    <template #footer>
      <el-alert v-if="error || blockedReason" :title="error || blockedReason" type="warning" show-icon :closable="false" class="schedule-action-feedback" />
      <el-checkbox v-if="preview && !pending" v-model="confirmed" class="schedule-save-confirm">{{ $tr('已核对各段比例、成本变化及起算日期修正') }}</el-checkbox>
      <p v-if="!pending && !preview && !error && !blockedReason" class="schedule-save-hint" role="status">{{ saveHint }}</p>
      <el-button :disabled="saving" @click="visible=false">{{ $tr('关闭') }}</el-button>
      <template v-if="pending"><el-button v-if="pending.canWithdraw" :disabled="saving" @click="review('WITHDRAWN')">{{ $tr('撤回申请') }}</el-button><el-button v-if="pending.canReview" type="danger" plain :disabled="saving" @click="review('REJECTED')">{{ $tr('退回') }}</el-button><el-button v-if="pending.canReview" type="primary" :loading="saving" @click="review('APPROVED')">{{ $tr('确认本次调配') }}</el-button></template>
      <el-button v-else type="primary" :loading="saving || previewing" :disabled="!ready || !reason.trim() || busy || !!blockedReason || (!!preview && !confirmed)" @click="preview ? save() : runPreview()">{{ !preview ? $tr('预览并确认') : preview.needsConfirmation ? $tr('提交相关负责人确认') : $tr('保存并生效') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import CostImpact from './CostImpact.vue'
import AllocationTimeline from './AllocationTimeline.vue'
import { allocationLockLabel, allocationLockHint, allocationBlockedMessage } from './presentation'
import useUserStore from '@/store/modules/user'
import { translateText as tr } from '@/locales/translate'
import { getAllocationScheduleMembers, getAllocationScheduleState, getAllocationScheduleWorkspace, previewAllocationSchedule, saveAllocationSchedule, getBusinessStaffAllocationWorkspace, reviewBusinessStaffAllocation } from '@/api/business/project'

const props=defineProps({projectId:[Number,String],members:{type:Array,default:()=>[]},canManage:Boolean})
const emit=defineEmits(['changed'])
const user=useUserStore(),visible=ref(false),opening=ref(false),saving=ref(false),previewing=ref(false)
const anchor=ref(null),userId=ref(null),members=ref([]),segments=ref([]),expanded=ref([]),records=ref([]),history=ref([])
const periods=ref([]),recordExpanded=ref([])
const pending=ref(null),preview=ref(null),confirmed=ref(false),reason=ref(''),comment=ref(''),error=ref('')
const previewSection=ref(null)
let sequence=0,key=0
const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
const shortDate=value=>{if(!value)return null;if(typeof value==='number'){const d=new Date(value);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}return String(value).slice(0,10)}
const addDays=(value,n)=>{const d=new Date(`${value}T12:00:00`);d.setDate(d.getDate()+n);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
const percent=value=>`${Number(value||0).toFixed(2)}%`
const lockLabel=row=>tr(allocationLockLabel(row))
const lockHint=row=>tr(allocationLockHint(row))
const recordProject=row=>periods.value.flatMap(p=>p.projects).find(p=>Number(p.projectId)===Number(row.projectId))||row
const periodLabel=segment=>`${segment.dateFrom||tr('请选择开始日期')} — ${segment.dateTo||tr('持续生效')}`
const statusLabel=value=>({PENDING:tr('待确认'),CONFIRMED:tr('已确认'),APPROVED:tr('已确认'),APPLIED:tr('已确认生效'),WITHDRAWN:tr('已撤回'),REJECTED:tr('已退回'),INVALIDATED:tr('已失效，请重新发起')})[value]||value
const failure=e=>e?.msg||e?.message||tr('投入暂未加载，请重试')
const selectedMember=computed(()=>members.value.find(m=>Number(m.userId)===Number(userId.value)))
const busy=computed(()=>opening.value||saving.value||previewing.value||segments.value.some(s=>s.loading))
const segmentTotal=s=>Math.round((s.workspace?.projects||[]).reduce((sum,row)=>sum+Number(row.allocationValue||0),0)*100)/100
const ready=computed(()=>segments.value.length>0&&segments.value.every(s=>s.workspace&&!s.error&&!s.loading&&segmentTotal(s)===100))
const blockedReason=computed(()=>{
  if(pending.value)return ''
  for(const [index,segment] of segments.value.entries()){
    const frozen=(segment.workspace?.projects||[]).find(row=>row.frozen&&(row.allocationMissing||row.startCorrectionDate||Number(row.allocationValue)!==Number(row.originalValue)))
    if(frozen)return tr(allocationBlockedMessage(frozen),[index+1,frozen.projectName,frozen.projectNo||frozen.projectId])
  }
  return ''
})
const saveHint=computed(()=>{
  if(busy.value)return tr('正在加载或测算投入，请稍候')
  if(!ready.value)return tr('请核对各时间段的日期，并确保投入合计100%')
  if(!reason.value.trim())return tr('请填写调整原因')
  if(!preview.value)return tr('点击“预览并确认”，核对比例与成本变化后保存')
  if(!confirmed.value)return tr('请勾选确认已核对各段比例、成本变化及起算日期修正')
  return preview.value.needsConfirmation?tr('涉及其他负责人，提交后待全部确认生效'):tr('已核对，可保存并生效')
})
const requestSegments=req=>req.segments?.length?req.segments:[{dateFrom:shortDate(req.effectiveDate),dateTo:shortDate(req.dateTo),projects:req.projects,impacts:req.impacts}]
const pendingSegments=computed(()=>pending.value?requestSegments(pending.value):[])
function invalidate(){sequence++;previewing.value=false;preview.value=null;confirmed.value=false;error.value=''}
function draft(dateFrom,dateTo=null){return reactive({key:++key,dateFrom,dateTo,workspace:null,loading:false,error:'',request:0})}

async function open(seedUserId,effectiveDate){
  if(!props.canManage||!user.permissions.some(p=>p==='*:*:*'||p==='business:project:allocation'))return
  visible.value=true;invalidate();opening.value=true;pending.value=null;reason.value='';comment.value='';segments.value=[];records.value=[];history.value=[];periods.value=[];recordExpanded.value=[]
  const n=sequence
  try{
    let seed=seedUserId
    if(!seed||typeof seed==='object')seed=props.members.find(m=>m.memberRole!=='OBSERVER')?.userId
    anchor.value=props.projectId
    if(!anchor.value){
      const ws=(await getBusinessStaffAllocationWorkspace({userId:seed,effectiveDate:effectiveDate||today()},{silentError:true})).data
      anchor.value=ws.projects.find(p=>Number(p.ownerUserId)===Number(user.id))?.projectId||ws.projects[0]?.projectId
      if(!anchor.value)throw new Error(tr('该日期没有可调整投入的项目'))
    }
    const data=(await getAllocationScheduleMembers(anchor.value)).data||{}
    if(n!==sequence)return
    members.value=data.members||[]
    userId.value=members.value.find(m=>Number(m.userId)===Number(seed))?.userId||members.value[0]?.userId
    if(!userId.value)throw new Error(tr('当前项目没有可设置投入比例的人员'))
    opening.value=false
    await selectMember(typeof effectiveDate==='string'?effectiveDate:null)
  }catch(e){if(n===sequence)error.value=failure(e)}finally{opening.value=false}
}
async function selectMember(seedDate=null){
  invalidate();pending.value=null;records.value=[];history.value=[];periods.value=[];recordExpanded.value=[];comment.value='';reason.value='';opening.value=true
  const n=sequence
  try{
    const state=(await getAllocationScheduleState(anchor.value,userId.value)).data||{}
    if(n!==sequence||!visible.value)return
    records.value=state.records||[];periods.value=state.periods||[];history.value=state.history||[];pending.value=state.pendingRequest||null
  }catch(e){if(n===sequence)error.value=failure(e);return}finally{opening.value=false}
  if(pending.value){segments.value=[];return}
  const m=selectedMember.value,current=periods.value.find(p=>p.dateFrom<=today()&&(!p.dateTo||p.dateTo>=today()))
  const segment=draft(seedDate||current?.dateFrom||m?.gapFrom||today(),seedDate?null:current?current.dateTo:m?.gapTo||null)
  segments.value=[segment];expanded.value=[segment.key];await loadSegment(segment)
}
async function adjustPeriod(period){
  if(busy.value||pending.value)return
  let segment=segments.value.find(s=>s.dateFrom===period.dateFrom&&(s.dateTo||null)===(period.dateTo||null))
  if(!segment){
    if(segments.value.length>=20)return ElMessage.warning(tr('每次最多调整20个时间段'))
    if(segments.value.some(s=>period.dateFrom<=(s.dateTo||'9999-12-31')&&(!period.dateTo||s.dateFrom<=period.dateTo)))return ElMessage.warning(tr('该期间与正在编辑的时间段重叠，请在已有时间段内调整'))
    segment=draft(period.dateFrom,period.dateTo);segments.value.push(segment);segments.value.sort((a,b)=>a.dateFrom.localeCompare(b.dateFrom))
    await loadSegment(segment)
  }
  if(!expanded.value.includes(segment.key))expanded.value.push(segment.key)
  await nextTick()
  document.querySelector(`[data-allocation-segment="${segment.key}"]`)?.scrollIntoView({block:'start',behavior:'smooth'})
}
async function loadSegment(segment){
  invalidate();const request=++segment.request,staff=userId.value
  segment.error='';segment.workspace=null
  if(!segment.dateFrom){segment.error=tr('请选择开始日期');return}
  if(segment.dateTo&&segment.dateTo<segment.dateFrom){segment.error=tr('结束日期不能早于开始日期');return}
  segment.loading=true
  try{
    const data=(await getAllocationScheduleWorkspace(anchor.value,{userId:staff,dateFrom:segment.dateFrom,...(segment.dateTo?{dateTo:segment.dateTo}:{})})).data
    if(request!==segment.request||staff!==userId.value||!visible.value||!segments.value.includes(segment))return
    if(data.splitDate){
      if(segments.value.length>=20)throw new Error(tr('参与或投入变化较多，请分批设置，每次最多20段'))
      const next=draft(data.splitDate,segment.dateTo);segment.dateTo=addDays(data.splitDate,-1)
      segments.value.splice(segments.value.indexOf(segment)+1,0,next);expanded.value.push(next.key)
      segment.loading=false;await loadSegment(segment);await loadSegment(next);return
    }
    segment.workspace={...data,projects:(data.projects||[]).map(row=>({...row,originalValue:row.allocationValue}))}
    records.value=data.records||[];history.value=data.history||[];pending.value=data.pendingRequest||null
  }catch(e){if(request===segment.request&&staff===userId.value)segment.error=failure(e)}finally{if(request===segment.request)segment.loading=false}
}
async function addSegment(source=null){
  if(busy.value)return
  const last=segments.value.at(-1),end=last?.dateTo||last?.workspace?.dateTo
  if(last&&!end)return ElMessage.warning(tr('请先填写最后一段的结束日期，再新增时间段'))
  const copied=source?.workspace?.projects?.map(row=>({projectId:row.projectId,value:row.allocationValue}))
  const segment=draft(end?addDays(end,1):today()),existing=new Set(segments.value.map(s=>s.key))
  segments.value.push(segment);expanded.value.push(segment.key);await loadSegment(segment)
  if(copied)for(const s of segments.value.filter(s=>!existing.has(s.key)))for(const row of s.workspace?.projects||[]){const prior=copied.find(p=>Number(p.projectId)===Number(row.projectId));if(prior&&!row.frozen)row.allocationValue=prior.value}
}
async function addGap(){
  const m=selectedMember.value;if(!m?.gapFrom)return
  if(segments.value.some(s=>s.dateFrom===m.gapFrom&&s.dateTo===m.gapTo))return
  const segment=draft(m.gapFrom,m.gapTo);segments.value.push(segment);expanded.value.push(segment.key);await loadSegment(segment)
}
function removeSegment(segment){invalidate();segment.request++;segments.value=segments.value.filter(s=>s!==segment);expanded.value=expanded.value.filter(k=>k!==segment.key)}
function payload(){return{userId:userId.value,reason:reason.value.trim(),segments:segments.value.map(s=>({dateFrom:s.dateFrom,dateTo:s.dateTo||null,versionToken:s.workspace.versionToken,allocations:s.workspace.projects.map(row=>({projectId:row.projectId,allocationValue:row.allocationValue}))}))}}
async function runPreview(){
  if(!ready.value||saving.value||blockedReason.value)return
  if(!reason.value.trim())return ElMessage.warning(tr('请填写调整原因'))
  invalidate();const n=sequence;previewing.value=true
  try{const data=(await previewAllocationSchedule(anchor.value,payload())).data;if(n===sequence)preview.value=data}
  catch(e){if(n===sequence)error.value=failure(e)}finally{if(n===sequence)previewing.value=false}
  if(n===sequence&&preview.value){
    await nextTick()
    // Wait for the preview table and fixed footer to finish their layout.
    await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))
    if(n===sequence)previewSection.value?.scrollIntoView({block:'end',behavior:'smooth'})
  }
}
async function save(){
  if(!preview.value||!confirmed.value||!ready.value||busy.value||blockedReason.value)return
  if(!reason.value.trim())return ElMessage.warning(tr('请填写调整原因'))
  saving.value=true
  error.value=''
  try{const result=(await saveAllocationSchedule(anchor.value,{...payload(),impactConfirmed:true,previewToken:preview.value.previewToken})).data
    emit('changed');ElMessage.success(tr(result.outcome==='PENDING'?'已提交相关负责人确认，原分配继续使用':'多段投入已保存并重新核算'))
    if(result.outcome==='PENDING'){pending.value=result;preview.value=null;confirmed.value=false}else visible.value=false
  }catch(e){const message=failure(e);invalidate();error.value=message}finally{saving.value=false}
}
async function review(decision){
  if(saving.value)return
  if(decision==='REJECTED'&&!comment.value.trim())return ElMessage.warning(tr('退回时请填写原因'))
  saving.value=true
  try{const result=(await reviewBusinessStaffAllocation(pending.value.requestId,{decision,comment:comment.value.trim()})).data;emit('changed');if(result.status==='INVALIDATED'){ElMessage.warning(result.closeReason||statusLabel(result.status));await selectMember()}else if(result.status==='PENDING')pending.value=result;else{visible.value=false;ElMessage.success(statusLabel(result.status))}}
  catch(e){error.value=failure(e)}finally{saving.value=false}
}
watch(()=>props.projectId,()=>{visible.value=false})
watch(visible,v=>{if(!v){invalidate();segments.value.forEach(s=>s.request++);opening.value=false}})
defineExpose({open})
</script>

<style scoped>
.schedule-action-feedback{margin-bottom:12px;text-align:left}.schedule-action-feedback :deep(.el-alert__title){line-height:1.6;overflow-wrap:anywhere}.frozen-missing{color:var(--el-color-warning)}
.preview-heading{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:18px}.preview-heading>div{display:flex;align-items:center;gap:12px}.preview-heading h4{margin:0;color:var(--el-text-color-primary);font-size:16px}.preview-heading span{font-size:13px;color:var(--el-text-color-secondary)}.preview-period+.preview-period{margin-top:20px;padding-top:18px;border-top:1px solid var(--el-border-color-light)}.preview-period__heading{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:12px;font-size:13px}.preview-period__heading b{color:var(--el-text-color-primary)}.preview-period__heading span{color:var(--el-text-color-secondary);font-variant-numeric:tabular-nums}.preview-confirmation-note{margin:16px 0 0;padding-top:12px;border-top:1px solid var(--el-border-color-light);font-size:12px;color:var(--el-text-color-secondary);line-height:1.6}
.schedule-save-hint{margin:0 0 10px;font-size:13px;line-height:1.5;text-align:left;color:var(--el-text-color-secondary)}
.schedule-save-confirm{display:flex;white-space:normal;height:auto;line-height:1.5;margin:0 0 10px;text-align:left}.schedule-save-confirm :deep(.el-checkbox__label){white-space:normal;line-height:1.5}
.schedule-form{margin-top:18px}.schedule-form :deep(.el-alert){margin:12px 0}.schedule-toolbar{display:flex;justify-content:space-between;align-items:center;margin:18px 0 10px}.segment-title{display:flex;align-items:center;gap:14px;flex-wrap:wrap;padding:10px 0;line-height:1.5}.segment-title span{color:var(--el-text-color-secondary)}.schedule-segment{padding:14px 0}.schedule-segment h4{margin:0 0 12px}.segment-dates{display:grid;grid-template-columns:1fr 1fr;gap:16px}.schedule-help{font-size:13px;line-height:1.7;color:var(--el-text-color-secondary)}.schedule-segment :deep(.el-table small){display:block;margin-top:4px;color:var(--el-text-color-secondary)}.schedule-percent{display:flex;align-items:center;gap:5px}.schedule-percent :deep(.el-input-number){width:128px}.segment-footer{display:flex;justify-content:space-between;align-items:center;margin:14px 0;color:var(--el-color-success)}.segment-footer .invalid{color:var(--el-color-warning)}.schedule-preview{margin-top:18px;padding:16px;border:1px solid var(--el-border-color);border-radius:10px;background:var(--el-fill-color-lighter)}.schedule-preview>h4{margin-top:0}.schedule-preview :deep(.el-checkbox){white-space:normal;height:auto;line-height:1.6;margin-top:14px}.schedule-records{margin-top:18px}.schedule-records article+article{border-top:1px solid var(--el-border-color-lighter)}
:global(.allocation-schedule-dialog){max-height:92vh;display:flex;flex-direction:column}:global(.allocation-schedule-dialog .el-dialog__body){overflow:auto;overflow-x:hidden;min-height:0}
@media(max-width:640px){.segment-dates{grid-template-columns:1fr;gap:0}.segment-title{gap:6px;font-size:12px}.segment-title span{flex-basis:100%}.schedule-preview{padding:10px}.schedule-form :deep(.el-form-item){display:block}.schedule-form :deep(.el-form-item__label){float:none;width:auto!important}.schedule-form :deep(.el-form-item__content){margin-left:0!important}}
</style>
