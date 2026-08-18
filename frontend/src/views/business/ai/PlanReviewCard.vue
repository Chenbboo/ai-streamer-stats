<template>
  <section class="plan-card" :class="{compact}">
    <header class="plan-head">
      <div><el-tag size="small" type="success" effect="plain">计划已提交</el-tag><h3>{{ project.projectName }}</h3><p>{{ project.companyName || '未设置归属公司' }} · 负责人 {{ project.mainOwnerName || '待设置' }}</p></div>
      <div class="plan-status"><b>计划审核卡</b><span>{{ project.planStartDate || '—' }} 至 {{ project.planEndDate || '—' }}</span></div>
    </header>

    <div class="plan-overview">
      <div><small>持续工作</small><b>{{ review.routineCount || 0 }} 项</b></div>
      <div><small>一次性任务</small><b>{{ review.taskCount || 0 }} 项</b></div>
      <div><small>参项人员</small><b>{{ review.memberCount || 0 }} 人</b></div>
      <div><small>项目 KPI</small><b>{{ review.kpiCount || 0 }} 项</b></div>
      <div><small>投入计划</small><b>{{ review.allocationCount || 0 }} 人</b></div>
    </div>

    <div class="plan-objective"><small>项目目标</small><p>{{ project.objective || '负责人尚未填写项目目标' }}</p></div>

    <div class="plan-sections">
      <section>
        <div class="section-title"><h4>持续工作</h4><span>每天/每周持续执行</span></div>
        <div v-if="review.routines?.length" class="detail-list">
          <div v-for="item in review.routines" :key="item.routineId" class="detail-row">
            <div><b>{{ item.routineName }}</b><small>{{ item.assigneeName || '未指定执行人' }} · {{ frequencyLabel[item.frequency] || item.frequency || '未设频率' }}</small></div>
            <strong>{{ targetText(item) }}</strong>
          </div>
        </div>
        <p v-else class="empty">未安排持续工作</p>
      </section>

      <section>
        <div class="section-title"><h4>一次性任务</h4><span>有明确完成节点</span></div>
        <div v-if="review.tasks?.length" class="detail-list">
          <div v-for="item in review.tasks" :key="item.taskId" class="detail-row">
            <div><b>{{ item.taskName }}</b><small>{{ item.assigneeName || '未指定执行人' }} · 截止 {{ item.dueDate || '未设置' }}</small></div>
            <el-tag size="small" effect="plain">{{ priorityLabel[item.priority] || item.priority || '普通' }}</el-tag>
          </div>
        </div>
        <p v-else class="empty">没有一次性任务</p>
      </section>

      <section>
        <div class="section-title"><h4>参项人员与投入</h4><span>谁参与、计划投入多少</span></div>
        <div v-if="review.members?.length" class="detail-list">
          <div v-for="item in review.members" :key="item.userId" class="detail-row">
            <div><b>{{ item.userName || item.userNameSnapshot }}</b><small>{{ roleLabel[item.memberRole] || item.memberRole || '成员' }}</small></div>
            <span>{{ allocationText(item.userId) }}</span>
          </div>
        </div>
        <p v-else class="empty">尚未添加参项人员</p>
      </section>

      <section>
        <div class="section-title"><h4>项目 KPI</h4><span>老板批准后用来判断结果</span></div>
        <div v-if="review.kpis?.length" class="detail-list">
          <div v-for="item in review.kpis" :key="item.kpiId" class="detail-row">
            <div><b>{{ item.kpiName }}</b><small>{{ item.ownerName || '未指定负责人' }} · {{ periodLabel[item.periodType] || item.periodType || '项目周期' }}</small></div>
            <strong>{{ valueUnit(item.targetValue,item.unit) }}</strong>
          </div>
        </div>
        <p v-else class="empty">尚未设置项目 KPI</p>
      </section>
    </div>

    <details v-if="review.milestones?.length || review.risks?.length" class="extra-plan">
      <summary>查看项目节点与风险（{{ (review.milestones?.length || 0) + (review.risks?.length || 0) }} 项）</summary>
      <div v-for="item in review.milestones" :key="`m-${item.milestoneId}`" class="extra-row"><b>{{ item.milestoneName }}</b><span>计划 {{ item.planDate || '未设置日期' }}</span></div>
      <div v-for="item in review.risks" :key="`r-${item.riskId}`" class="extra-row"><b>{{ item.riskTitle }}</b><span>{{ severityLabel[item.severity] || item.severity }} · {{ item.status==='OPEN'?'未关闭':'已处理' }}</span></div>
    </details>

    <div class="review-result" :class="review.warnings?.length?'has-warning':'is-ready'">
      <div><b>{{ review.warnings?.length ? `批准前有 ${review.warnings.length} 项需要关注` : '计划要素检查通过' }}</b><span>{{ review.recommendation }}</span></div>
      <ul v-if="review.warnings?.length"><li v-for="warning in review.warnings" :key="warning">{{ warning }}</li></ul>
    </div>
    <div v-if="!compact&&showActions" class="review-actions">
      <el-button :disabled="busy" @click="emit('decision',{decision:'RETURN',project})">退回调整</el-button>
      <el-button type="primary" :loading="busy" @click="emit('decision',{decision:'APPROVE',project})">批准并启动</el-button>
    </div>
  </section>
</template>

<script setup>
const props=defineProps({review:{type:Object,required:true},compact:{type:Boolean,default:false},busy:{type:Boolean,default:false},showActions:{type:Boolean,default:false}})
const emit=defineEmits(['decision'])
const project=computed(()=>props.review.project||{})
const frequencyLabel={DAILY:'每日',WEEKLY:'每周',MONTHLY:'每月'}
const priorityLabel={HIGH:'高优先级',MEDIUM:'中优先级',LOW:'低优先级'}
const roleLabel={OWNER:'主负责人',DEPUTY:'副负责人',MEMBER:'成员',OBSERVER:'观察者'}
const periodLabel={DAILY:'每日',WEEKLY:'每周',MONTHLY:'每月',QUARTERLY:'每季度',PROJECT:'项目周期'}
const severityLabel={CRITICAL:'严重风险',HIGH:'高风险',MEDIUM:'中风险',LOW:'低风险'}
const allocationModeLabel={PERCENTAGE:'计划投入',HOURS:'计划工时',ATTENDANCE:'出勤天数',FIXED_DAILY:'固定日金额',PER_TASK:'任务投入'}
const valueUnit=(value,unit)=>value===null||value===undefined?'未设目标':`${Number(value).toLocaleString('zh-CN')} ${unit||''}`.trim()
const targetText=item=>item.targetValue===null||item.targetValue===undefined?'未设目标':`${valueUnit(item.targetValue,item.unit)} / ${frequencyLabel[item.frequency]||'周期'}`
function allocationText(userId){const item=(props.review.staffAllocations||[]).find(row=>String(row.userId)===String(userId));if(!item)return '尚未设置投入计划';const suffix=item.allocationMode==='PERCENTAGE'?'%':'';return `${allocationModeLabel[item.allocationMode]||item.allocationMode} ${Number(item.allocationValue||0).toLocaleString('zh-CN')}${suffix}`}
</script>

<style scoped>
.plan-card{margin-top:12px;padding:18px;border:1px solid #bcded8;border-radius:13px;background:#fbfefd;box-shadow:0 7px 18px rgba(31,101,92,.08);color:#203744}.plan-card.compact{margin:14px 0 0;border-color:#dce7e3;background:#fff;box-shadow:none}.plan-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;padding-bottom:14px;border-bottom:1px solid #dfecea}.plan-head h3{margin:7px 0 3px;font-size:19px}.plan-head p,.plan-status span{margin:0;color:#7b8d94;font-size:12px}.plan-status{display:flex;align-items:flex-end;flex-direction:column;gap:5px;white-space:nowrap}.plan-status b{color:#24766d}.plan-overview{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px;margin:14px 0}.plan-overview>div{padding:10px;border-radius:8px;background:#eef7f5}.plan-overview small,.plan-overview b{display:block}.plan-overview small{color:#7d908e}.plan-overview b{margin-top:5px;font-size:16px}.plan-objective{padding:12px;border-left:3px solid #39a294;background:#f4faf9}.plan-objective small{color:#738986}.plan-objective p{margin:5px 0 0;line-height:1.6}.plan-sections{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:11px}.plan-sections>section{min-width:0;padding:12px;border:1px solid #e1e9e8;border-radius:9px;background:#fff}.section-title{display:flex;align-items:flex-start;justify-content:space-between;gap:8px}.section-title h4{margin:0;font-size:14px}.section-title span{color:#91a0a3;font-size:10px}.detail-list{margin-top:9px}.detail-row{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:9px 0;border-top:1px solid #edf1f1}.detail-row>div{display:flex;min-width:0;flex-direction:column;gap:4px}.detail-row small{color:#859398}.detail-row strong,.detail-row>span{color:#365d61;font-size:12px;text-align:right}.empty{margin:12px 0 0;color:#99a5a8;font-size:12px}.extra-plan{margin-top:11px;padding:11px;border-radius:8px;background:#f5f8f8}.extra-plan summary{color:#527178;cursor:pointer;font-weight:600}.extra-row{display:flex;justify-content:space-between;gap:12px;padding:8px 0;border-top:1px solid #e3e9e9}.extra-row:first-of-type{margin-top:8px}.extra-row span{color:#7c8b90}.review-result{margin-top:11px;padding:12px;border-radius:9px}.review-result>div{display:flex;flex-direction:column;gap:4px}.review-result span{font-size:12px}.review-result ul{margin:9px 0 0;padding-left:18px}.review-result li{margin-top:5px}.review-result.has-warning{background:#fff5e8;color:#795528}.review-result.is-ready{background:#edf8f2;color:#2f7359}.review-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:14px;padding-top:14px;border-top:1px solid #deebe8}@media(max-width:800px){.plan-head{flex-direction:column}.plan-status{align-items:flex-start}.plan-overview{grid-template-columns:repeat(2,1fr)}.plan-sections{grid-template-columns:1fr}}@media(max-width:520px){.plan-card{padding:14px}.plan-overview{grid-template-columns:1fr 1fr}.section-title{flex-direction:column}.detail-row{align-items:flex-start;flex-direction:column}.detail-row strong,.detail-row>span{text-align:left}.review-actions{display:grid;grid-template-columns:1fr}.review-actions .el-button{width:100%;margin:0}}
</style>
