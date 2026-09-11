<template>
  <el-dialog v-model="visible" top="5vh" @closed="$emit('closed')" :title="`${data.projectName || '子项目'} · ${mode === 'submit' ? '汇报进度' : '进度与汇报历史'}`" width="min(860px, 96vw)" append-to-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
    <div v-loading="loading" class="progress-content">
      <el-alert v-if="error" title="汇报数据加载失败" type="error" :closable="false"><el-button link @click="load">重新加载</el-button></el-alert>
      <template v-else-if="!loading">
        <el-alert title="每次提交生成新版本，历史汇报和关联快照永久保留。" type="info" :closable="false" show-icon />
        <el-form v-if="mode === 'submit' && data.canSubmit" ref="formRef" :model="form" :rules="rules" label-position="top" class="report-form">
          <el-row :gutter="16"><el-col :span="12"><el-form-item label="汇报人"><el-input :model-value="data.reporterName" disabled /></el-form-item></el-col><el-col :span="12"><el-form-item label="汇报时间"><el-input model-value="提交时由系统自动记录" disabled /></el-form-item></el-col></el-row>
          <el-form-item label="进度百分比" prop="progress"><el-input-number v-model="form.progress" :min="0" :max="100" :precision="0" /><span class="hint">% · 当前 {{ data.progressPercent ?? 0 }}%，纠正允许下调</span></el-form-item>
          <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :prop="field.key"><el-input v-model="form[field.key]" type="textarea" :rows="3" :maxlength="2000" show-word-limit :placeholder="field.placeholder" /></el-form-item>
          <el-checkbox v-model="form.syncTasks">同步一次性任务完成进度</el-checkbox><el-checkbox v-model="form.syncRoutines">同步持续工作执行情况</el-checkbox>
          <p class="hint">以下为自动带入的当前数据，提交时系统保存最新快照；勾选内容随汇报展示。</p>
          <ProgressSnapshot :snapshot="data.snapshot" />
        </el-form>
        <template v-else>
          <p><b>项目整体进度 {{ data.progressPercent ?? 0 }}%</b></p>
          <el-button v-if="data.parentId && data.canSubmit" type="primary" @click="startNew">汇报进度 / 提交纠正版本</el-button>
          <el-collapse v-if="data.children?.length" :model-value="['children']" class="aggregate"><el-collapse-item name="children" title="各子项目进度与最新汇报 · 未配置权重按1计算">
            <el-table :data="data.children" size="small"><el-table-column prop="projectName" label="子项目" min-width="130"/><el-table-column label="权重" :width="data.canConfigureWeights ? 190 : 75"><template #default="{row}"><template v-if="data.canConfigureWeights"><el-input-number v-model="row.progressWeight" :min="0.0001" :max="99999999" :precision="4" :controls="false" size="small" style="width:110px" placeholder="默认1"/><el-button link type="primary" :disabled="saving" @click="saveWeight(row)">保存</el-button></template><span v-else>{{ row.progressWeight ?? 1 }}</span></template></el-table-column><el-table-column label="进度" width="85"><template #default="{row}">{{ row.progressPercent ?? 0 }}%</template></el-table-column><el-table-column prop="progressSummary" label="最新阶段成果" min-width="180"/><el-table-column width="95"><template #default="{row}"><el-button link type="primary" @click="open(row)">查看汇报</el-button></template></el-table-column></el-table>
          </el-collapse-item></el-collapse>
          <template v-if="data.parentId || data.reports?.length"><h3>历次汇报</h3><ReportHistory :reports="data.reports" :selected-report-id="selectedReportId" /></template>
          <template v-if="data.childReports?.length"><h3>子项目汇报档案（含已删除子项目）</h3><ReportHistory :reports="data.childReports" link-project @open-project="open" /></template>
        </template>
      </template>
    </div>
    <template #footer><el-button :disabled="saving" @click="visible=false">关闭</el-button><el-button v-if="mode==='submit' && data.canSubmit && !error && !loading" type="primary" :loading="saving" @click="submit">提交新版本</el-button></template>
  </el-dialog>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectProgress, setProjectProgressWeight, submitBusinessProjectProgressReport } from '@/api/business/project'
import ProgressSnapshot from './ProgressSnapshot.vue'
import ReportHistory from './ReportHistory.vue'
const emit = defineEmits(['submitted','closed'])
const visible=ref(false), loading=ref(false), saving=ref(false), error=ref(false), mode=ref('history'), data=ref({}), formRef=ref(null), selectedReportId=ref(null)
const form=reactive({ progress:0,completionSummary:'',issuesRisks:'',nextPlan:'',syncTasks:true,syncRoutines:true })
const fields=[{key:'completionSummary',label:'阶段成果',placeholder:'本阶段已完成的工作与成果'},{key:'issuesRisks',label:'问题风险',placeholder:'问题、风险及应对措施；无问题请填写“无”'},{key:'nextPlan',label:'下一步计划',placeholder:'下一步行动、时间与预期成果'}]
const rules=Object.fromEntries(fields.map(f=>[f.key,[{required:true,whitespace:true,message:`请填写${f.label}`,trigger:'blur'}]]))
rules.progress=[{required:true,type:'number',min:0,max:100,message:'进度必须为0至100的整数',trigger:'change'}]
let projectId=null, sequence=0
async function load() {
 const seq=++sequence; loading.value=true;error.value=false
 try { const res=await getProjectProgress(projectId); if(seq!==sequence)return;data.value=res.data; if(mode.value==='submit'){ if(data.value.canSubmit)startNew();else mode.value='history' } }
 catch { if(seq===sequence)error.value=true } finally { if(seq===sequence)loading.value=false }
}
async function saveWeight(row){if(saving.value)return;saving.value=true;try{await setProjectProgressWeight(projectId,row.projectId,row.progressWeight??null);emit('submitted',{projectId});ElMessage.success('权重已更新，总项目进度已重新汇总');await load()}finally{saving.value=false}}
function startNew(){Object.assign(form,{progress:data.value.progressPercent??0,completionSummary:'',issuesRisks:'',nextPlan:'',syncTasks:true,syncRoutines:true});mode.value='submit';formRef.value?.clearValidate()}
async function open(row, requestedMode='history'){ if(saving.value)return;projectId=row.projectId;selectedReportId.value=row.reportId||null;mode.value=requestedMode;data.value={};visible.value=true;await load() }
async function submit(){if(saving.value || !formRef.value)return;saving.value=true;try{if(!await formRef.value.validate().catch(()=>false))return;const result=await submitBusinessProjectProgressReport({...form,projectId});mode.value='history';selectedReportId.value=result.data.reportId;ElMessage.success('汇报已提交，历史版本已保留');emit('submitted',{projectId,parentId:data.value.parentId});await load()}finally{saving.value=false}}
defineExpose({open})
</script>
<style scoped>.progress-content{min-height:220px;max-height:calc(90vh - 135px);overflow-y:auto;overflow-x:hidden;padding-right:6px}.report-form{margin-top:18px}.hint{color:#7e8c9d;font-size:12px;margin-left:10px}.aggregate{margin-top:18px}h3{font-size:15px;margin-top:22px}</style>
