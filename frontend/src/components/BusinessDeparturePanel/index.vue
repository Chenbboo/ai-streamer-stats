<template>
  <section class="departure-panel" v-loading="loading">
    <div class="heading"><h3>离职与交接</h3><el-button link type="primary" @click="load">刷新交接清单</el-button></div>
    <el-alert v-if="failed" title="交接清单加载失败，请刷新后再办理。" type="error" :closable="false" />
    <template v-else>
      <el-alert title="先完成负责项目、未完任务及持续工作的交接。离职生效时停用账号、撤销登录，并退出项目；计费截至生效日（含当天），保留历史成本。" type="info" :closable="false" />
      <p v-if="data.departure">办理状态：{{ statuses[data.departure.status] }} · 生效日 {{ String(data.departure.effective_date).slice(0,10) }}</p>
      <el-alert v-if="data.departure?.error_message" :title="data.departure.error_message" type="error" :closable="false" />
      <el-table :data="data.projects || []" size="small" empty-text="没有需要交接的在执行项目">
        <el-table-column prop="projectName" label="参与项目" />
        <el-table-column label="交接事项"><template #default="{row}">{{ Number(row.ownerUserId)===Number(person.userId)?'主负责人待更换；':'' }}未完任务 {{ row.taskCount }} 项，持续工作 {{ row.routineCount }} 项</template></el-table-column>
        <el-table-column width="80"><template #default="{row}"><el-button link type="primary" @click="openProject(row)">去交接</el-button></template></el-table-column>
      </el-table>
      <el-button v-if="['SCHEDULED','FAILED'].includes(data.departure?.status)" :loading="saving" @click="cancel">取消本次离职申请</el-button>
      <el-form v-else-if="person.employmentStatus!=='LEFT'" label-width="90px" class="departure-form">
        <el-form-item label="生效日期" required><el-date-picker v-model="form.effectiveDate" value-format="YYYY-MM-DD" :disabled-date="disabledDate" /></el-form-item>
        <el-form-item label="离职说明" required><el-input v-model="form.reason" type="textarea" maxlength="2000" show-word-limit /></el-form-item>
        <el-button type="warning" :disabled="blocked || failed || loading" :loading="saving" @click="submit">{{ blocked?'请先完成交接':'确认办理离职' }}</el-button>
      </el-form>
    </template>
  </section>
</template>
<script setup>
import {ref,reactive,computed,watch} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {getDepartureChecklist,requestDeparture,cancelDeparture} from '@/api/business/flow'
const props=defineProps({person:{type:Object,required:true}}),emit=defineEmits(['changed','navigate'])
const router=useRouter(),loading=ref(false),saving=ref(false),failed=ref(false),data=ref({projects:[]}),form=reactive({effectiveDate:'',reason:''})
const statuses={SCHEDULED:'待生效',COMPLETED:'已办理',FAILED:'交接条件发生变化，待重新办理',CANCELED:'已取消'}
const blocked=computed(()=>data.value.projects?.some(p=>Number(p.ownerUserId)===Number(props.person.userId)||Number(p.taskCount)>0||Number(p.routineCount)>0))
let sequence=0
async function load(){const seq=++sequence;loading.value=true;failed.value=false;try{const r=await getDepartureChecklist(props.person.userId);if(seq===sequence)data.value=r.data}catch{if(seq===sequence)failed.value=true}finally{if(seq===sequence)loading.value=false}}
function disabledDate(d){const now=new Date();now.setHours(0,0,0,0);return d<now||d>new Date(now.getFullYear()+1,now.getMonth(),now.getDate())}
function openProject(p){emit('navigate');router.push({path:'/business/projects',query:{id:p.projectId}})}
async function submit(){if(!form.effectiveDate||!form.reason.trim())return ElMessage.warning('请填写生效日期和离职说明');await ElMessageBox.confirm('确认交接已完成？系统将按生效日期办理退项和账号停用。','确认离职');saving.value=true;try{await requestDeparture(props.person.userId,form);await load();emit('changed');ElMessage.success('离职申请已登记')}finally{saving.value=false}}
async function cancel(){await ElMessageBox.confirm('取消待生效或办理失败的离职申请？','取消申请');saving.value=true;try{await cancelDeparture(props.person.userId);await load()}finally{saving.value=false}}
watch(()=>props.person.userId,()=>{form.effectiveDate='';form.reason='';load()},{immediate:true})
</script>
<style scoped>
.departure-panel{border:1px solid #e4e7ed;border-radius:8px;padding:16px;margin:16px 0}.heading{display:flex;align-items:center;justify-content:space-between}.heading h3{margin:0 0 12px}.departure-form{margin-top:16px}.el-table{margin:12px 0}
</style>
