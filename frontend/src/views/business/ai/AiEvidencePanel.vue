<template>
  <section v-if="hasContent" class="evidence-panel">
    <div v-if="understandingText" class="understanding-row">
      <span>本次理解</span>
      <strong>{{ understandingText }}</strong>
      <el-tag v-if="needsClarification" size="small" type="warning" effect="plain">需要补充信息</el-tag>
    </div>

    <details v-if="evidenceGroups.length" class="evidence-details">
      <summary>
        <span>查看依据（{{ evidenceGroups.length }}）</span>
        <small>展开查看数据来源</small>
      </summary>
      <div class="evidence-list">
        <article v-for="(group,groupIndex) in evidenceGroups" :key="evidenceKey(group,groupIndex)">
          <header>
            <b>{{ group.label || group.sourceLabel || '系统业务记录' }}</b>
            <span v-if="group.cutoffTime">截至 {{ group.cutoffTime }}</span>
          </header>
          <div v-if="group.facts?.length" class="fact-list">
            <div v-for="(fact,factIndex) in group.facts" :key="factKey(fact,groupIndex,factIndex)" class="fact-row">
              <span>{{ fact.label || fact.name || fact.field || '数据项' }}</span>
              <strong>{{ factValue(fact) }}</strong>
              <small v-if="fact.bizDate || fact.dataDate">{{ fact.bizDate || fact.dataDate }}</small>
            </div>
          </div>
          <p v-else>{{ group.description || '已从系统读取并核对该项数据。' }}</p>
        </article>
      </div>
      <div v-if="scope" class="scope-row">
        <span>数据范围：{{ scope.label || '当前账号可见范围' }}</span>
        <span>{{ scope.dataDate || scope.cutoffTime || '' }}</span>
      </div>
    </details>

    <div v-else-if="scope" class="scope-row is-standalone">
      <span>数据范围：{{ scope.label || '当前账号可见范围' }}</span>
      <span>{{ scope.dataDate || scope.cutoffTime || '' }}</span>
    </div>
  </section>
</template>

<script setup>
const props=defineProps({
  understanding:{type:Object,default:null},
  evidence:{type:Array,default:()=>[]},
  sources:{type:Array,default:()=>[]},
  scope:{type:Object,default:null}
})

const queryLabels={
  ACCOUNTING:'经营情况',
  DETAIL:'项目详情',
  MEMBER_PROGRESS:'成员完成进度',
  PENDING_DECISIONS:'待老板处理事项',
  PROJECT_ACCOUNTING:'项目经营情况',
  PROJECT_BUDGET:'项目预算情况',
  PROJECT_DETAIL:'项目详情',
  PROJECT_PORTFOLIO:'全部项目概况',
  PROJECT_PROGRESS:'项目完成进度',
  PROJECT_STATUS:'项目状态',
  STAFF:'人员情况',
  STAFF_OVERVIEW:'公司人员概况',
  TODAY_ACCOUNTING:'今日经营情况',
  PENDING_DECISION:'待处理事项'
}

const needsClarification=computed(()=>['AMBIGUOUS','NEEDS_CLARIFICATION','UNRESOLVED'].includes(String(props.understanding?.status||'').toUpperCase()))
const understandingText=computed(()=>{
  const data=props.understanding
  if(!data)return ''
  if(data.summary)return String(data.summary)
  const query=data.queryLabel||data.queryTypeLabel||data.intentLabel||queryLabels[data.queryType]||data.queryType
  const project=data.project?.projectName||data.project?.name||data.subject?.name||data.projectName
  const people=(Array.isArray(data.people)?data.people:[]).map(item=>typeof item==='string'?item:item?.userName||item?.name||item?.personName).filter(Boolean).join('、')
  const dateRange=data.dateRange||{}
  const date=dateRange.label||data.dateLabel||(dateRange.start&&dateRange.end
    ? (dateRange.start===dateRange.end?dateRange.start:`${dateRange.start} 至 ${dateRange.end}`)
    : dateRange.start||dateRange.end)
  return [query,project,people,date].filter(Boolean).join(' · ')
})
const evidenceGroups=computed(()=>{
  if(props.evidence.length)return props.evidence
  return props.sources.map(source=>({...source,facts:Array.isArray(source?.facts)?source.facts:[]}))
})
const hasContent=computed(()=>Boolean(understandingText.value||evidenceGroups.value.length||props.scope))

const evidenceKey=(group,index)=>group?.evidenceId||group?.sourceId||`${group?.toolCode||'source'}-${index}`
const factKey=(fact,groupIndex,factIndex)=>fact?.factId||`${fact?.recordType||'fact'}-${fact?.recordId||groupIndex}-${fact?.field||factIndex}-${factIndex}`
function factValue(fact){
  if(fact?.displayValue!==undefined&&fact.displayValue!==null)return String(fact.displayValue)
  if(fact?.actual!==undefined&&fact?.target!==undefined)return `${fact.actual}/${fact.target}${fact.unit?` ${fact.unit}`:''}`
  if(fact?.value===undefined||fact?.value===null)return '—'
  if(Array.isArray(fact.value))return fact.value.join('、')
  if(typeof fact.value==='object')return fact.value?.summary||fact.summary||'已核对'
  if(String(fact?.status||'').toUpperCase()==='MISSING')return String(fact.value)
  return `${fact.value}${fact.unit?` ${fact.unit}`:''}`
}
</script>

<style scoped>
.evidence-panel{max-width:100%;min-width:0;margin-top:8px;color:#36535d}.understanding-row{display:flex;min-width:0;align-items:center;flex-wrap:wrap;gap:7px;padding:9px 11px;border:1px solid #d8e8e5;border-radius:9px;background:#f6fbfa}.understanding-row>span{flex:0 0 auto;padding:3px 6px;border-radius:5px;background:#e2f2ef;color:#26786f;font-size:10px;font-weight:700}.understanding-row>strong{min-width:0;overflow-wrap:anywhere;font-size:12px;line-height:1.55}.evidence-details{margin-top:7px;border:1px solid #e1e9e8;border-radius:9px;background:#fff}.evidence-details summary{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:9px 11px;color:#507078;cursor:pointer;font-size:12px;list-style-position:inside}.evidence-details summary small{color:#91a0a5;font-size:10px}.evidence-list{display:grid;gap:7px;padding:0 10px 10px}.evidence-list article{min-width:0;padding:9px;border-radius:7px;background:#f7f9f9}.evidence-list header{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.evidence-list header b{font-size:12px}.evidence-list header span,.evidence-list article>p{color:#89989d;font-size:10px}.evidence-list article>p{margin:6px 0 0}.fact-list{display:grid;gap:5px;margin-top:7px}.fact-row{display:grid;min-width:0;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:8px;padding-top:6px;border-top:1px solid #e6eceb;font-size:11px}.fact-row>span{min-width:0;overflow-wrap:anywhere}.fact-row>strong{color:#285f63;text-align:right}.fact-row>small{color:#92a0a4;white-space:nowrap}.scope-row{display:flex;justify-content:space-between;gap:10px;padding:8px 11px;border-top:1px solid #e8eeee;color:#84949a;font-size:10px}.scope-row.is-standalone{margin-top:7px;border:1px solid #e1e9e8;border-radius:8px;background:#fafcfc}
@media(max-width:600px){.understanding-row{align-items:flex-start}.evidence-details summary{align-items:flex-start;flex-direction:column}.evidence-list header{flex-direction:column;gap:4px}.fact-row{grid-template-columns:1fr}.fact-row>strong{text-align:left}.fact-row>small{white-space:normal}.scope-row{align-items:flex-start;flex-direction:column;gap:3px}}
</style>
