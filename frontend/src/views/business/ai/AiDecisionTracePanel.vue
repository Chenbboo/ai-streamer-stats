<template>
  <details class="decision-panel">
    <summary>
      <span>查看 AI 决策</span>
      <el-tag size="small" :type="corrected ? 'warning' : 'success'" effect="plain">
        {{ corrected ? '系统已纠正' : '校验通过' }}
      </el-tag>
    </summary>
    <div class="decision-body">
      <div class="decision-grid">
        <div><small>识别意图</small><b>{{ label(trace.detectedIntent) }}</b></div>
        <div><small>模型选择</small><b>{{ selectionText }}</b></div>
        <div><small>系统校验</small><b>{{ trace.validationMessage || '已通过安全校验' }}</b></div>
        <div><small>最终处理</small><b>{{ label(trace.finalRoute) }}</b></div>
      </div>
      <div v-if="candidates.length" class="decision-row">
        <small>候选能力</small>
        <span><el-tag v-for="item in candidates" :key="item" size="small" effect="plain">{{ label(item) }}</el-tag></span>
      </div>
      <div v-if="missingFields.length" class="decision-row">
        <small>仍需补充</small>
        <span><el-tag v-for="item in missingFields" :key="item" size="small" type="info" effect="plain">{{ item }}</el-tag></span>
      </div>
      <footer>
        <span>{{ trace.provider || 'LOCAL' }} / {{ trace.model || '安全路由' }} · {{ trace.executionMode || '-' }}</span>
        <span>Run #{{ trace.runId || '-' }} · Trace {{ shortTrace }}</span>
      </footer>
    </div>
  </details>
</template>

<script setup>
const props=defineProps({trace:{type:Object,required:true}})
const labels={
  CREATE_PROJECT:'创建新项目',
  CREATE_PROJECT_WORKFLOW:'创建项目资料收集',
  NO_MODEL_ROUTE:'模型未给出路由',
  'project.create':'正式创建项目',
  'project.draft.update':'更新立项草稿',
  'conversation.safe.respond':'安全对话回复'
}
const label=value=>labels[value]||value||'-'
const corrected=computed(()=>props.trace.validationStatus==='CORRECTED')
const selections=computed(()=>Array.isArray(props.trace.modelSelection)?props.trace.modelSelection:[])
const candidates=computed(()=>Array.isArray(props.trace.candidateCapabilities)?props.trace.candidateCapabilities:[])
const missingFields=computed(()=>Array.isArray(props.trace.missingFields)?props.trace.missingFields:[])
const selectionText=computed(()=>selections.value.length?selections.value.map(label).join('、'):'未返回')
const shortTrace=computed(()=>{
  const value=String(props.trace.traceId||'')
  return value.length>12?`${value.slice(0,12)}…`:(value||'-')
})
</script>

<style scoped>
.decision-panel{border:1px solid #dbe7e5;border-radius:10px;background:#fbfdfd;color:#274542;overflow:hidden}
.decision-panel summary{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:9px 12px;cursor:pointer;list-style:none;font-size:13px;font-weight:600}
.decision-panel summary::-webkit-details-marker{display:none}
.decision-body{border-top:1px solid #e5efed;padding:12px}
.decision-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.decision-grid div{display:flex;flex-direction:column;gap:3px;padding:9px;background:#f2f8f7;border-radius:8px}
small{color:#78908d;font-size:11px}
b{font-size:13px;font-weight:600;line-height:1.5}
.decision-row{display:flex;align-items:flex-start;gap:12px;margin-top:10px}
.decision-row>small{flex:0 0 60px;padding-top:4px}
.decision-row>span{display:flex;flex-wrap:wrap;gap:6px}
footer{display:flex;justify-content:space-between;gap:12px;margin-top:11px;padding-top:9px;border-top:1px dashed #dbe7e5;color:#879b98;font-size:11px}
@media (max-width:700px){.decision-grid{grid-template-columns:1fr}footer{flex-direction:column;gap:3px}}
</style>
