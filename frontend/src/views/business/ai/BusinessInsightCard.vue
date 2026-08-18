<template>
  <section class="insight-card" :class="`is-${String(card.status || 'normal').toLowerCase()}`">
    <header class="insight-head">
      <div>
        <el-tag size="small" :type="statusType" effect="plain">{{ statusLabel }}</el-tag>
        <h3>{{ card.title }}</h3>
        <p>{{ project.companyName || '未设置归属公司' }} · 负责人 {{ project.mainOwnerName || '待设置' }}</p>
      </div>
      <div class="insight-date"><span>{{ card.type === 'OPERATING_ANALYSIS' ? '经营分析卡' : '项目详情卡' }}</span><b>{{ card.bizDate || project.status || '' }}</b></div>
    </header>

    <template v-if="card.type === 'OPERATING_ANALYSIS'">
      <div class="metric-grid">
        <div><small>今日收入</small><b>{{ money(metrics.revenueAmount) }}</b></div>
        <div><small>业务成本</small><b>{{ money(metrics.businessCost) }}</b></div>
        <div><small>人员成本</small><b>{{ money(metrics.personnelCost) }}</b></div>
        <div :class="{negative:Number(metrics.profitAmount)<0}"><small>今日经营结果</small><b>{{ money(metrics.profitAmount) }}</b></div>
      </div>
      <div v-if="hasBudget" class="budget-block">
        <div><span>累计成本 {{ money(metrics.budgetSpent) }}</span><b>预算 {{ money(metrics.budgetLimit) }}</b></div>
        <el-progress :percentage="budgetPercent" :status="budgetPercent>100?'exception':undefined" :stroke-width="10" />
        <small v-if="Number(metrics.overBudgetAmount)>0">已超出预算 {{ money(metrics.overBudgetAmount) }}</small>
      </div>
      <section class="detail-section">
        <div class="section-title"><h4>人员成本明细</h4><span>对应到具体人员</span></div>
        <div v-if="card.personnelItems?.length" class="person-list">
          <div v-for="(item,index) in card.personnelItems" :key="`${item.componentName}-${index}`">
            <div><b>{{ item.componentName || '未命名人员' }}</b><small>{{ calculationText(item.calculationDetail) }}</small></div>
            <strong>{{ money(item.amount) }}</strong>
          </div>
        </div>
        <p v-else class="empty">当天没有人员成本</p>
      </section>
      <div v-if="card.warnings?.length" class="warning-box"><b>需要老板关注</b><p v-for="item in card.warnings" :key="item">{{ item }}</p></div>
    </template>

    <template v-else>
      <div class="project-summary">
        <div><small>持续工作</small><b>{{ card.routines?.length || 0 }} 项</b></div>
        <div><small>一次性任务</small><b>{{ card.tasks?.length || 0 }} 项</b></div>
        <div><small>参项人员</small><b>{{ card.members?.length || 0 }} 人</b></div>
        <div><small>项目 KPI</small><b>{{ card.kpis?.length || 0 }} 项</b></div>
        <div><small>未关闭风险</small><b>{{ openRiskCount }} 项</b></div>
      </div>
      <div class="objective"><small>项目目标</small><p>{{ project.objective || '尚未设置项目目标' }}</p></div>
      <div class="project-columns">
        <section><div class="section-title"><h4>参项人员</h4></div><div class="mini-list"><div v-for="item in card.members" :key="item.userId"><b>{{ item.userNameSnapshot }}</b><span>{{ roleLabel[item.memberRole] || item.memberRole }}</span></div><p v-if="!card.members?.length" class="empty">暂无参项人员</p></div></section>
        <section><div class="section-title"><h4>工作安排</h4></div><div class="mini-list"><div v-for="item in workItems" :key="item.key"><b>{{ item.name }}</b><span>{{ item.assignee || '未指定' }}</span></div><p v-if="!workItems.length" class="empty">暂无工作安排</p></div></section>
      </div>
    </template>

    <footer v-if="card.actions?.length" class="insight-actions">
      <span>接下来可以直接让我</span>
      <div><el-button v-for="action in card.actions" :key="action.code" size="small" :type="action.code==='ADJUST_BUDGET'?'primary':''" @click="$emit('action',action.prompt)">{{ action.label }}</el-button></div>
    </footer>
  </section>
</template>

<script setup>
const props=defineProps({card:{type:Object,required:true}})
defineEmits(['action'])
const project=computed(()=>props.card.project||{})
const metrics=computed(()=>props.card.metrics||{})
const statusType=computed(()=>props.card.status==='OVER_BUDGET'||props.card.status==='LOSS'?'danger':'success')
const statusLabel=computed(()=>props.card.status==='OVER_BUDGET'?'已超预算':props.card.status==='LOSS'?'今日亏损':props.card.type==='PROJECT_OVERVIEW'?'项目详情':'经营正常')
const hasBudget=computed(()=>metrics.value.budgetLimit!==null&&metrics.value.budgetLimit!==undefined)
const budgetPercent=computed(()=>{const limit=Number(metrics.value.budgetLimit||0);if(limit<=0)return Number(metrics.value.budgetSpent)>0?100:0;return Math.min(999,Math.round(Number(metrics.value.budgetSpent||0)/limit*100))})
const openRiskCount=computed(()=>props.card.risks?.filter(item=>item.status==='OPEN').length||0)
const roleLabel={OWNER:'主负责人',DEPUTY:'副负责人',MEMBER:'成员',OBSERVER:'观察者'}
const money=value=>`${Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})} ${project.value.baseCurrency||'CNY'}`
const calculationText=value=>{if(!value)return '按当天有效投入核算';try{const data=typeof value==='string'?JSON.parse(value):value;const mode=data.allocationValue!==undefined?`投入 ${data.allocationValue}%`:'';return [mode,data.leave===true?'当天请假':''].filter(Boolean).join(' · ')||'按当天有效投入核算'}catch{return '按当天有效投入核算'}}
const workItems=computed(()=>[...(props.card.routines||[]).map(item=>({key:`r-${item.routineId}`,name:item.routineName,assignee:item.assigneeName})),...(props.card.tasks||[]).map(item=>({key:`t-${item.taskId}`,name:item.taskName,assignee:item.assigneeName}))].slice(0,8))
</script>

<style scoped>
.insight-card{margin-top:12px;padding:18px;border:1px solid #bddbd7;border-radius:13px;background:#fbfefd;box-shadow:0 7px 18px rgba(31,101,92,.08);color:#203744}.insight-card.is-over_budget,.insight-card.is-loss{border-color:#e9c7b7;background:#fffdfb}.insight-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding-bottom:14px;border-bottom:1px solid #dfeae8}.insight-head h3{margin:7px 0 3px;font-size:19px}.insight-head p{margin:0;color:#7a8b92;font-size:12px}.insight-date{display:flex;align-items:flex-end;flex-direction:column;gap:5px;color:#6f848a;white-space:nowrap}.insight-date b{color:#1f6f68}.metric-grid,.project-summary{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin:14px 0}.metric-grid>div,.project-summary>div{padding:12px;border-radius:9px;background:#edf6f4}.metric-grid small,.metric-grid b,.project-summary small,.project-summary b{display:block}.metric-grid small,.project-summary small{color:#798c8f}.metric-grid b,.project-summary b{margin-top:6px;font-size:17px}.metric-grid .negative b{color:#d44c4c}.budget-block{padding:12px;border-radius:9px;background:#f6f8f8}.budget-block>div{display:flex;justify-content:space-between;margin-bottom:9px}.budget-block small{display:block;margin-top:7px;color:#d14f46}.detail-section{margin-top:11px;padding:12px;border:1px solid #e1e9e8;border-radius:9px;background:#fff}.section-title{display:flex;justify-content:space-between;gap:10px}.section-title h4{margin:0}.section-title span{color:#8a999e;font-size:11px}.person-list>div,.mini-list>div{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 0;border-top:1px solid #edf1f1}.person-list>div:first-child,.mini-list>div:first-child{margin-top:8px}.person-list>div>div{display:flex;min-width:0;flex-direction:column;gap:4px}.person-list small{color:#89979b}.person-list strong{color:#285f63}.warning-box{margin-top:11px;padding:12px;border-radius:9px;background:#fff1e7;color:#80512c}.warning-box p{margin:7px 0 0}.warning-box p:before{content:'• '}.project-summary{grid-template-columns:repeat(5,minmax(0,1fr))}.objective{padding:12px;border-left:3px solid #37a096;background:#f3f9f8}.objective small{color:#728784}.objective p{margin:5px 0 0}.project-columns{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:11px}.project-columns>section{padding:12px;border:1px solid #e1e9e8;border-radius:9px;background:#fff}.mini-list span{color:#74878c;font-size:12px}.empty{margin:12px 0 0;color:#99a6aa;font-size:12px}.insight-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:13px;padding-top:13px;border-top:1px solid #e0e8e7}.insight-actions>span{color:#71858a;font-size:12px}.insight-actions>div{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:7px}@media(max-width:800px){.metric-grid,.project-summary{grid-template-columns:repeat(2,1fr)}.project-columns{grid-template-columns:1fr}.insight-head{flex-direction:column}.insight-date{align-items:flex-start}.insight-actions{align-items:flex-start;flex-direction:column}.insight-actions>div{justify-content:flex-start}}@media(max-width:520px){.insight-card{padding:14px}.metric-grid,.project-summary{grid-template-columns:1fr 1fr}.person-list>div{align-items:flex-start;flex-direction:column}.budget-block>div{flex-direction:column;gap:5px}.insight-actions .el-button{margin:0}}
</style>
