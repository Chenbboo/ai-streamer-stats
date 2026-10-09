<template>
  <div class="resource-grid">
    <ProjectPersonnelCostChart :project-id="selected" :date-from="range.dateFrom" :date-to="range.dateTo" @changed="budget?.reload()">
      <template #project-selector><el-select v-model="selected" filterable :placeholder="$tr('选择项目')" style="width:240px" :disabled="!options.length"><el-option v-for="row in options" :key="row.projectId" :value="row.projectId" :label="row.projectName" /></el-select></template>
    </ProjectPersonnelCostChart>
    <ProjectBudgetUsage ref="budget" :company-dept-id="companyDeptId" :ready="ready" :currency="range.currency" @loaded="loaded" />
  </div>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import ProjectPersonnelCostChart from './ProjectPersonnelCostChart.vue'
import ProjectBudgetUsage from './ProjectBudgetUsage.vue'
const props=defineProps({companyDeptId:[String,Number],ready:Boolean,range:{type:Object,default:()=>({})},selectedProjectId:[String,Number]})
const projects=ref([]),selected=ref(),budget=ref()
const options=computed(()=>projects.value.filter(row=>!props.range.currency||row.currency===props.range.currency))
function reconcile(){if(!options.value.some(row=>String(row.projectId)===String(selected.value)))selected.value=options.value[0]?.projectId}
function loaded(rows){projects.value=rows;reconcile()}
watch(()=>[props.companyDeptId,props.ready],()=>{projects.value=[];selected.value=null})
watch(()=>props.range.currency,reconcile)
watch(()=>props.selectedProjectId,id=>{const row=options.value.find(row=>String(row.projectId)===String(id));if(row)selected.value=row.projectId})
</script>
<style scoped>
.resource-grid {
  display: grid;
  grid-template-columns: repeat(2,minmax(0,1fr));
  gap: 24px;
  margin-bottom: 24px;
  align-items: stretch;
}
.resource-grid > :deep(.resource-card),
.resource-grid > :deep(.budget-card) { margin-bottom: 0; padding: 24px; }
.resource-grid > :deep(.resource-card) { display: flex; flex-direction: column; }
.resource-grid :deep(.resource-card h2) { font-weight: 700; }
.resource-grid :deep(.personnel-body) { flex: 1; }
.resource-grid :deep(.resource-card > .hint:last-of-type) { margin-bottom: 0; }
@media(max-width:1200px) { .resource-grid { grid-template-columns: 1fr; } }
@media(max-width:600px) {
  .resource-grid { gap: 16px; }
  .resource-grid > :deep(.resource-card),
  .resource-grid > :deep(.budget-card) { padding: 18px 16px; }
}
</style>
