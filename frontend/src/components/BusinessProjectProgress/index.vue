<template>
  <el-dialog v-model="visible" top="5vh" @closed="$emit('closed')" :title="`${data.projectName || '项目'} · ${mode === 'submit' ? '汇报进度' : '进度与汇报历史'}`" :width="mode === 'submit' ? 'min(860px, 96vw)' : 'min(1120px, 96vw)'" append-to-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
    <div v-loading="loading" class="progress-content">
      <el-alert v-if="error" title="汇报数据加载失败" type="error" :closable="false"><el-button link @click="load">重新加载</el-button></el-alert>
      <template v-else-if="!loading">
        <el-alert v-if="mode === 'submit'" :title="allowSubmit ? '每次提交生成新版本，历史汇报和关联快照永久保留。' : '历史汇报和关联快照永久保留。'" type="info" :closable="false" show-icon />
        <el-form v-if="mode === 'submit' && canSubmit" ref="formRef" :model="form" :rules="rules" label-position="top" class="report-form">
          <el-row :gutter="16"><el-col :span="12"><el-form-item label="汇报人"><el-input :model-value="data.reporterName" disabled /></el-form-item></el-col><el-col :span="12"><el-form-item label="汇报时间"><el-input model-value="提交时由系统自动记录" disabled /></el-form-item></el-col></el-row>
          <el-form-item label="进度百分比" prop="progress"><el-input-number v-model="form.progress" :min="0" :max="100" :precision="0" /><span class="hint">% · 当前 {{ data.progressPercent ?? 0 }}%，纠正允许下调</span></el-form-item>
          <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :prop="field.key"><el-input v-model="form[field.key]" type="textarea" :rows="3" :maxlength="2000" show-word-limit :placeholder="field.placeholder" /></el-form-item>
          <el-form-item label="成果凭证（选填）">
            <div class="evidence-inputs">
              <el-input v-model="form.evidenceText" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="可填写文字成果凭证，也可在下方上传文件" />
              <BusinessFileUpload v-model="form.evidenceUrls" :project-id="projectId" />
            </div>
          </el-form-item>
          <el-checkbox v-model="form.syncTasks">同步一次性任务完成进度</el-checkbox><el-checkbox v-model="form.syncRoutines">同步持续工作执行情况</el-checkbox>
          <p class="hint">以下为自动带入的当前数据，提交时系统保存最新快照；勾选内容随汇报展示。</p>
          <ProgressSnapshot :snapshot="data.snapshot" />
        </el-form>
        <template v-else>
          <section class="progress-overview">
            <div class="overall-progress"><span>项目当前整体进度</span><strong>{{ data.progressPercent ?? 0 }}<small>%</small></strong><el-progress :percentage="Number(data.progressPercent) || 0" :show-text="false" :stroke-width="7" color="#328b80" /></div>
            <div><span>本项目汇报记录</span><strong>{{ data.reports?.length || 0 }}<small>条</small></strong><p>选择历史记录，查看当时的进度与成果</p></div>
            <div class="overview-tip"><b>项目进度由负责人填报</b><p>主项目与子项目分别汇报；最新汇报默认展开。</p></div>
          </section>
          <el-button v-if="canSubmit" type="primary" @click="startNew">汇报进度 / 提交纠正版本</el-button>
          <el-collapse v-if="data.children?.length" :model-value="['children']" class="aggregate"><el-collapse-item name="children" title="各子项目分别填报的进度与最新汇报">
            <el-table :data="data.children" size="small"><el-table-column prop="projectName" label="子项目" min-width="130"/><el-table-column label="进度" width="85"><template #default="{row}">{{ row.progressPercent ?? 0 }}%</template></el-table-column><el-table-column prop="progressSummary" label="最新阶段成果" min-width="180"/><el-table-column width="95"><template #default="{row}"><el-button link type="primary" @click="open(row)">查看汇报</el-button></template></el-table-column></el-table>
          </el-collapse-item></el-collapse>
          <ReportHistory :reports="data.reports" :selected-report-id="selectedReportId" />
          <template v-if="data.childReports?.length"><h3>子项目汇报档案（含已删除子项目）</h3><ReportHistory :reports="data.childReports" link-project @open-project="open" /></template>
        </template>
      </template>
    </div>
    <template #footer><el-button :disabled="saving" @click="visible=false">关闭</el-button><el-button v-if="mode==='submit' && canSubmit && !error && !loading" type="primary" :loading="saving" @click="submit">提交新版本</el-button></template>
  </el-dialog>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectProgress, submitBusinessProjectProgressReport } from '@/api/business/project'
import ProgressSnapshot from './ProgressSnapshot.vue'
import ReportHistory from './ReportHistory.vue'
import BusinessFileUpload from '@/components/BusinessFileUpload/index.vue'
const props = defineProps({ allowSubmit: { type: Boolean, default: true } })
const emit = defineEmits(['submitted','closed'])
const visible=ref(false), loading=ref(false), saving=ref(false), error=ref(false), mode=ref('history'), data=ref({}), formRef=ref(null), selectedReportId=ref(null)
const canSubmit=computed(()=>props.allowSubmit && data.value.canSubmit)
const form=reactive({ progress:0,completionSummary:'',issuesRisks:'',nextPlan:'',evidenceText:'',evidenceUrls:'',syncTasks:true,syncRoutines:true })
const fields=[{key:'completionSummary',label:'阶段成果',placeholder:'本阶段已完成的工作与成果'},{key:'issuesRisks',label:'问题风险（选填）',placeholder:'有问题或风险时填写，无需填写“无”'},{key:'nextPlan',label:'下一步计划（选填）',placeholder:'如有下一步安排，可在此补充'}]
const rules={completionSummary:[{required:true,whitespace:true,message:'请填写阶段成果',trigger:'blur'}]}
rules.progress=[{required:true,type:'number',min:0,max:100,message:'进度必须为0至100的整数',trigger:'change'}]
let projectId=null, sequence=0
async function load() {
 const seq=++sequence; loading.value=true;error.value=false
 try { const res=await getProjectProgress(projectId); if(seq!==sequence)return;data.value=res.data; if(mode.value==='submit'){ if(canSubmit.value)startNew();else mode.value='history' } }
 catch { if(seq===sequence)error.value=true } finally { if(seq===sequence)loading.value=false }
}
function startNew(){if(!canSubmit.value)return;Object.assign(form,{progress:data.value.progressPercent??0,completionSummary:'',issuesRisks:'',nextPlan:'',evidenceText:'',evidenceUrls:'',syncTasks:true,syncRoutines:true});mode.value='submit';formRef.value?.clearValidate()}
async function open(row, requestedMode='history'){ if(saving.value)return;projectId=row.projectId;selectedReportId.value=row.reportId||null;mode.value=props.allowSubmit?requestedMode:'history';data.value={};visible.value=true;await load() }
async function submit(){if(!canSubmit.value || saving.value || !formRef.value)return;saving.value=true;try{if(!await formRef.value.validate().catch(()=>false))return;const result=await submitBusinessProjectProgressReport({...form,evidenceText:form.evidenceText?.trim()||'',projectId});mode.value='history';selectedReportId.value=result.data.reportId;ElMessage.success('汇报已提交，历史版本已保留');emit('submitted',{projectId,parentId:data.value.parentId});await load()}finally{saving.value=false}}
defineExpose({open})
</script>
<style scoped>.progress-content{min-height:220px;max-height:calc(90vh - 135px);overflow-y:auto;overflow-x:hidden;padding-right:6px}.report-form{margin-top:18px}.evidence-inputs{display:flex;width:100%;min-width:0;flex-direction:column;gap:12px}.hint{color:#7e8c9d;font-size:12px;margin-left:10px}.aggregate{margin-top:18px}h3{font-size:15px;margin-top:22px}
.progress-overview{display:grid;grid-template-columns:1fr 1fr 1.1fr;gap:24px;padding:20px 24px;background:#f3f8f8;border:1px solid #deebe9;border-radius:12px;margin-bottom:18px;color:#24384b}.progress-overview>div{min-width:0}.progress-overview>div>span{font-size:13px;color:#657e83}.progress-overview strong{display:block;margin:8px 0 10px;font-size:30px;color:#237d72;line-height:1.2}.progress-overview small{margin-left:6px;font-size:14px;font-weight:400}.progress-overview p{font-size:12px;color:#74868e;margin:8px 0 0;line-height:1.7}.overview-tip{padding-left:24px;border-left:1px solid #dce8e6;align-self:center}.overview-tip b{font-size:14px;font-weight:500}
@media(max-width:760px){.progress-overview{grid-template-columns:1fr 1fr;gap:18px;padding:16px}.overview-tip{display:none}.progress-overview strong{font-size:26px}}
</style>
