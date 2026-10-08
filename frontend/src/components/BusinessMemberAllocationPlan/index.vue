<template>
  <section class="member-allocation-plan" v-loading="loading">
    <div class="allocation-heading">
      <div><b>{{ $tr("{0}在本项目周期内的项目投入", [preview.userName || $tr("所选人员")]) }}</b>
        <small v-if="ready">{{ $tr("周期：{0} 至 {1}；调整生效日：{2}", [preview.periodStartDate, preview.periodEndDate || $tr("不限期"), preview.effectiveDate]) }}</small>
      </div>
      <div class="allocation-actions"><el-button v-if="userId" size="small" link :disabled="saving || loading" @click="load">{{ $tr("重新加载") }}</el-button><el-button size="small" type="primary" :loading="saving" :disabled="!ready || loading || preview.hasPendingRequest" @click="$emit('save')">{{ $tr("保存") }}</el-button></div>
    </div>
    <p v-if="!userId" class="allocation-hint">{{ $tr("选择人员后，显示周期内参与的项目及投入比例。") }}</p>
    <div v-else-if="error" class="allocation-error"><span>{{ error }}</span><el-button link type="primary" @click="load">{{ $tr("重新加载") }}</el-button></div>
    <template v-else-if="ready">
      <p class="allocation-hint">{{ $tr("周期内共 {0} 个项目（含本项目）；当前另有 {1} 个项目可调整。", [periodProjectCount, allocations.length]) }}</p>
      <el-alert v-if="preview.hasPendingRequest" type="warning" :closable="false" show-icon :title="$tr(&quot;该人员已有待确认的投入调整，请先处理后重新加载。&quot;)" />
      <el-table :data="tableRows" size="small" :row-class-name="({row})=>row.current?'current-project':''">
        <el-table-column :label="$tr(&quot;项目 / 周期&quot;)" min-width="220">
          <template #default="{row}"><b>{{ row.projectName }}</b><small>{{ row.current ? $tr("本次添加成员") : `${row.projectNo || ''} · ${row.editable ? $tr("当前可调整") : periodLabel(row)}` }}</small>
            <small v-if="!row.current && !row.editable">{{ $tr("{0} 至 {1}", [row.projectStartDate || row.joinedDate || $tr("未记录"), row.projectEndDate || row.leftDate || $tr("不限期")]) }}</small>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" :label="$tr(&quot;负责人&quot;)" min-width="100" />
        <el-table-column :label="$tr(&quot;投入比例&quot;)" width="180" align="right">
          <template #default="{row}">
            <div v-if="row.current" class="allocation-percent"><el-input-number :model-value="modelValue" @update:model-value="$emit('update:modelValue',$event)" :min="0.01" :max="100" :precision="2" :step="5" controls-position="right" :disabled="saving || preview.hasPendingRequest" :aria-label="$tr(&quot;本项目投入比例&quot;)"/><span>%</span></div>
            <div v-else-if="row.editable" class="allocation-percent"><el-input-number v-model="row.allocationValue" :min="0" :max="100" :precision="2" :step="5" controls-position="right" :disabled="saving || preview.hasPendingRequest" :aria-label="$tr(&quot;{0}投入比例&quot;, [row.projectName])"/><span>%</span></div>
            <span v-else>{{ formatPercent(row.allocationValue) }}%</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="allocation-total" :class="{invalid:!validTotal}"><span>{{ $tr("当前生效项目合计") }}</span><b>{{ formatPercent(total) }}%</b><small>{{ allocations.length ? $tr("合计必须等于100%") : $tr("本项目投入比例须大于0且不超过100%") }}</small></div>
      <el-form-item v-if="allocations.length" :label="$tr(&quot;调整原因&quot;)" required label-width="80px">
        <el-input v-model="reason" type="textarea" :rows="2" maxlength="500" show-word-limit :placeholder="$tr(&quot;请填写本次跨项目投入调整原因&quot;)" :disabled="saving || preview.hasPendingRequest" />
      </el-form-item>
      <el-alert type="info" :closable="false" show-icon :title="$tr(&quot;投入比例决定人员在各项目的计划投入和人员成本。同一次保存会添加本项目成员并调整当前项目比例；涉及其他负责人时，确认后生效。&quot;)" />
    </template>
  </section>
</template>

<script setup>
import { translateText } from '@/locales/translate'

import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getBusinessMemberAllocationPreview } from '@/api/business/project'

const props=defineProps({project:{type:Object,required:true},userId:[Number,String],modelValue:Number,saving:Boolean})
defineEmits(['update:modelValue','save'])
const preview=ref({}),allocations=ref([]),reason=ref(''),loading=ref(false),ready=ref(false),error=ref('')
let sequence=0
const total=computed(()=>Math.round((Number(props.modelValue||0)+allocations.value.reduce((sum,row)=>sum+Number(row.allocationValue||0),0))*100)/100)
const validTotal=computed(()=>allocations.value.length?total.value===100:total.value>0&&total.value<=100)
const periodProjectCount=computed(()=>new Set([props.project.projectId,...(preview.value.periodProjects||[]).map(row=>row.projectId)].map(String)).size)
const tableRows=computed(()=>[
  ...allocations.value,
  ...(preview.value.periodProjects||[]).filter(row=>!row.editable),
  {current:true,projectId:props.project.projectId,projectName:props.project.projectName,ownerName:props.project.mainOwnerName}
])
const formatPercent=value=>Number(value||0).toFixed(2)
const periodLabel=row=>({ENDED:translateText("已结束项目"),UPCOMING:translateText("后续项目"),PERIOD:translateText("周期内项目")})[row.periodState]||translateText("周期内项目")

async function load(){
  const request=++sequence
  ready.value=false;error.value='';preview.value={};allocations.value=[];reason.value='';loading.value=false
  if(!props.userId||!props.project.projectId)return
  loading.value=true
  try{
    const res=await getBusinessMemberAllocationPreview(props.project.projectId,props.userId)
    if(request!==sequence)return
    preview.value=res.data||{}
    allocations.value=(preview.value.projects||[]).map(row=>({...row,editable:true}))
    ready.value=true
  }catch(err){if(request===sequence)error.value=err?.message||translateText("项目投入加载失败，请重试")}
  finally{if(request===sequence)loading.value=false}
}
function validate(){
  let message=''
  if(loading.value)message=translateText("正在加载项目投入，请稍后保存")
  else if(!ready.value||error.value)message=translateText("请先加载该成员的项目投入信息")
  else if(preview.value.hasPendingRequest)message=translateText("该人员已有待确认的投入调整，请先处理")
  else if(allocations.value.some(row=>row.allocationValue==null||!Number.isFinite(Number(row.allocationValue))||Number(row.allocationValue)<0||Number(row.allocationValue)>100))message=translateText("各项目投入比例须在0%至100%之间")
  else if(!validTotal.value)message=allocations.value.length?translateText("所有当前生效项目投入比例合计必须等于100%"):translateText("本项目投入比例须大于0且不超过100%")
  else if(allocations.value.length&&!reason.value.trim())message=translateText("请填写跨项目投入调整原因")
  if(message){ElMessage.warning(message);return false}
  return true
}
function getPlan(){return {effectiveDate:preview.value.effectiveDate,versionToken:preview.value.versionToken,reason:reason.value.trim(),allocations:allocations.value.map(row=>({projectId:row.projectId,allocationValue:row.allocationValue}))}}
watch(()=>[props.project.projectId,props.userId],load,{immediate:true})
onBeforeUnmount(()=>{sequence++})
defineExpose({validate,getPlan})
</script>

<style scoped>
.member-allocation-plan{margin:16px 0;padding:16px;border:1px solid #e2e7ed;border-radius:8px;background:#fff}.allocation-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.member-allocation-plan small{display:block;margin-top:5px;color:#8793a1;font-size:12px;line-height:1.5}.allocation-hint{color:#8793a1;font-size:12px;line-height:1.6}.allocation-percent{display:flex;align-items:center;gap:6px}.allocation-percent .el-input-number{width:140px}.allocation-total{display:flex;justify-content:flex-end;align-items:center;flex-wrap:wrap;gap:10px;margin:16px 0;color:#238568}.allocation-total small{color:inherit;margin:0}.allocation-total.invalid,.allocation-error{color:var(--el-color-danger)}.allocation-error{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:12px}.member-allocation-plan :deep(.current-project){--el-table-tr-bg-color:#f3f8ff}.member-allocation-plan :deep(.el-alert){margin-top:12px}@media(max-width:600px){.member-allocation-plan{padding:10px}.allocation-total{justify-content:flex-start}}
</style>
