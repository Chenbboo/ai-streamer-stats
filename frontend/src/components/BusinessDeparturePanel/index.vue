<template>
  <section class="departure-panel" v-loading="loading">
    <div class="heading"><h3>{{ $tr("离职与交接") }}</h3><el-button link type="primary" @click="load">{{ $tr("刷新交接清单") }}</el-button></div>
    <el-alert v-if="failed" :title="$tr(&quot;交接清单加载失败，请刷新后再办理。&quot;)" type="error" :closable="false" />
    <template v-else>
      <el-alert :title="$tr(&quot;先完成负责项目、未完任务及持续工作的交接。离职生效时停用账号、撤销登录，并退出项目；计费截至生效日（含当天），保留历史成本。&quot;)" type="info" :closable="false" />
      <p v-if="data.departure">{{ $tr("办理状态：{0} · 生效日 {1}", [statuses[data.departure.status], String(data.departure.effective_date).slice(0,10)]) }}</p>
      <el-alert v-if="data.departure?.error_message" :title="data.departure.error_message" type="error" :closable="false" />
      <el-table :data="data.projects || []" size="small" :empty-text="$tr(&quot;没有需要交接的在执行项目&quot;)">
        <el-table-column prop="projectName" :label="$tr(&quot;参与项目&quot;)" />
        <el-table-column :label="$tr(&quot;交接事项&quot;)"><template #default="{row}">{{ $tr("{0}未完任务 {1} 项，持续工作 {2} 项", [Number(row.ownerUserId)===Number(person.userId)?$tr("主负责人待更换；"):'', row.taskCount, row.routineCount]) }}</template></el-table-column>
        <el-table-column width="80"><template #default="{row}"><el-button link type="primary" @click="openProject(row)">{{ $tr("去交接") }}</el-button></template></el-table-column>
      </el-table>
      <el-button v-if="['SCHEDULED','FAILED'].includes(data.departure?.status)" :loading="saving" @click="cancel">{{ $tr("取消本次离职申请") }}</el-button>
      <el-form v-else-if="person.employmentStatus!=='LEFT'" label-width="90px" class="departure-form">
        <el-form-item :label="$tr(&quot;生效日期&quot;)" required><el-date-picker v-model="form.effectiveDate" value-format="YYYY-MM-DD" :disabled-date="disabledDate" /></el-form-item>
        <el-form-item :label="$tr(&quot;离职说明&quot;)" required><el-input v-model="form.reason" type="textarea" maxlength="2000" show-word-limit /></el-form-item>
        <el-button type="warning" :disabled="blocked || failed || loading" :loading="saving" @click="submit">{{ blocked?$tr("请先完成交接"):$tr("确认办理离职") }}</el-button>
      </el-form>
    </template>
  </section>
</template>
<script setup>
import { translateText } from '@/locales/translate'

import {ref,reactive,computed,watch} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {getDepartureChecklist,requestDeparture,cancelDeparture} from '@/api/business/flow'
const props=defineProps({person:{type:Object,required:true}}),emit=defineEmits(['changed','navigate'])
const router=useRouter(),loading=ref(false),saving=ref(false),failed=ref(false),data=ref({projects:[]}),form=reactive({effectiveDate:'',reason:''})
const statuses={SCHEDULED:translateText("待生效"),COMPLETED:translateText("已办理"),FAILED:translateText("交接条件发生变化，待重新办理"),CANCELED:translateText("已取消")}
const blocked=computed(()=>data.value.projects?.some(p=>Number(p.ownerUserId)===Number(props.person.userId)||Number(p.taskCount)>0||Number(p.routineCount)>0))
let sequence=0
async function load(){const seq=++sequence;loading.value=true;failed.value=false;try{const r=await getDepartureChecklist(props.person.userId);if(seq===sequence)data.value=r.data}catch{if(seq===sequence)failed.value=true}finally{if(seq===sequence)loading.value=false}}
function disabledDate(d){const now=new Date();now.setHours(0,0,0,0);return d<now||d>new Date(now.getFullYear()+1,now.getMonth(),now.getDate())}
function openProject(p){emit('navigate');router.push({path:'/business/projects',query:{id:p.projectId}})}
async function submit(){if(!form.effectiveDate||!form.reason.trim())return ElMessage.warning(translateText("请填写生效日期和离职说明"));await ElMessageBox.confirm(translateText("确认交接已完成？系统将按生效日期办理退项和账号停用。"),translateText("确认离职"));saving.value=true;try{await requestDeparture(props.person.userId,form);await load();emit('changed');ElMessage.success(translateText("离职申请已登记"))}finally{saving.value=false}}
async function cancel(){await ElMessageBox.confirm(translateText("取消待生效或办理失败的离职申请？"),translateText("取消申请"));saving.value=true;try{await cancelDeparture(props.person.userId);await load()}finally{saving.value=false}}
watch(()=>props.person.userId,()=>{form.effectiveDate='';form.reason='';load()},{immediate:true})
</script>
<style scoped>
.departure-panel{border:1px solid #e4e7ed;border-radius:8px;padding:16px;margin:16px 0}.heading{display:flex;align-items:center;justify-content:space-between}.heading h3{margin:0 0 12px}.departure-form{margin-top:16px}.el-table{margin:12px 0}
</style>
