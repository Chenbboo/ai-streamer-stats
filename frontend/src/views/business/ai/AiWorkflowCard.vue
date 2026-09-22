<template>
  <article v-if="visible" class="workflow-card">
    <header>
      <div>
        <el-tag size="small" type="success" effect="plain">{{ $tr("AI 已记住") }}</el-tag>
        <h3>{{ title }}</h3>
      </div>
      <span>{{ stepLabel }}</span>
    </header>
    <div v-if="collected.length" class="collected-grid">
      <div v-for="item in collected" :key="item.label">
        <small>{{ item.label }}</small><b>{{ item.value }}</b>
      </div>
    </div>
    <div v-if="workflow.missingFields?.length" class="missing-fields">
      <small>{{ $tr("接下来还需要") }}</small>
      <div><el-tag v-for="field in workflow.missingFields" :key="field" size="small" type="warning" effect="plain">{{ field }}</el-tag></div>
    </div>
    <p>{{ $tr("你可以像平时说话一样继续补充，不用重复前面已经说过的内容。") }}</p>
  </article>
</template>

<script setup>
import { translateText } from '@/locales/translate'

const props=defineProps({workflow:{type:Object,default:()=>({})}})
const visible=computed(()=>props.workflow?.workflowCode==='CREATE_PROJECT'&&['COLLECTING','READY','WAITING_CONFIRMATION'].includes(props.workflow?.status))
const title=computed(()=>props.workflow?.status==='WAITING_CONFIRMATION'?translateText("立项资料已经收齐"):translateText("正在创建项目"))
const stepLabel=computed(()=>({BASIC_INFO:translateText("第 1 步：基本信息"),GOAL_AND_PERIOD:translateText("第 2 步：目标与周期"),ACCOUNTING_AND_BUDGET:translateText("第 3 步：核算与预算"),WAITING_CONFIRMATION:translateText("等待老板确认")})[props.workflow?.currentStep]||translateText("继续补充资料"))
const fieldLabels={projectName:translateText("项目名称"),ownerName:translateText("负责人"),companyName:translateText("归属公司"),objective:translateText("项目目标"),planStartDate:translateText("开始日期"),planEndDate:translateText("结束日期"),accountingMode:translateText("核算方式"),budgetLimit:translateText("预算上限"),baseCurrency:translateText("币种")}
const modeLabels={PROFIT:translateText("利润项目"),COST:translateText("成本项目"),VALUE:translateText("价值项目"),HYBRID:translateText("混合核算")}
const collected=computed(()=>Object.entries(props.workflow?.draft||{}).filter(([key,value])=>fieldLabels[key]&&value!==null&&value!==undefined&&String(value).trim()!=='').map(([key,value])=>({label:fieldLabels[key],value:key==='accountingMode'?(modeLabels[value]||value):value})))
</script>

<style scoped>
.workflow-card{margin-top:12px;padding:16px;border:1px solid #b9ddd4;border-radius:12px;background:#f7fcfb;box-shadow:0 6px 18px rgba(30,90,82,.06)}
.workflow-card header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.workflow-card h3{margin:7px 0 0;font-size:16px;color:#1e3d3a}.workflow-card header>span{color:#4c7a74;font-size:12px}
.collected-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;margin-top:14px}.collected-grid>div{padding:9px 10px;border-radius:8px;background:#edf7f5}.collected-grid small,.missing-fields>small{display:block;color:#76908c}.collected-grid b{display:block;margin-top:4px;overflow-wrap:anywhere;color:#263f3c}
.missing-fields{margin-top:13px}.missing-fields>div{display:flex;flex-wrap:wrap;gap:6px;margin-top:7px}.workflow-card>p{margin:13px 0 0;color:#66817d;font-size:12px;line-height:1.6}
@media(max-width:600px){.workflow-card header{flex-direction:column;gap:8px}.collected-grid{grid-template-columns:1fr}}
</style>
