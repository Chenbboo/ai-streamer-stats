<template>
  <div class="proposal-page">
    <header class="page-head">
      <div><span class="eyebrow">PROJECT INITIATION</span><h1>立项申请</h1><p>申请人就是项目负责人，并手动指定一名老板完成一次立项审批。</p></div>
      <el-button v-hasPermi="['business:project:proposal:add']" type="primary" icon="Plus" @click="openForm()">发起立项申请</el-button>
    </header>

    <el-alert title="立项规则" description="你只能为自己负责的项目提交申请；负责人固定为当前账号，审批老板由你从有效老板中手动指定。" type="info" :closable="false" show-icon />

    <el-tabs v-model="activeTab" class="proposal-tabs" @tab-change="loadActive">
      <el-tab-pane label="我的申请" name="mine">
        <el-card shadow="never">
          <el-table :data="mineRows" v-loading="loading" empty-text="还没有立项申请" @row-click="openDetail">
            <el-table-column label="申请" min-width="230"><template #default="{row}"><b>{{ row.projectName }}</b><small>{{ row.proposalNo }}</small></template></el-table-column>
            <el-table-column prop="sponsorOwnerName" label="审批/归属老板" width="150" />
            <el-table-column prop="companyName" label="归属公司" min-width="150" />
            <el-table-column label="治理方式" min-width="170"><template #default="{row}"><b>{{ managementLabel[row.managementMode] || row.managementMode }}</b><small>{{ closeMethodLabel[row.closeMethod] || row.closeMethod }}</small></template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusTone[row.status] || 'info'">{{ statusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
            <el-table-column label="计划周期" width="210"><template #default="{row}">{{ row.planStartDate }} 至 {{ row.planEndDate }}</template></el-table-column>
            <el-table-column label="操作" width="235" fixed="right"><template #default="{row}"><div class="row-actions" @click.stop>
              <el-button v-if="row.canEdit" link type="primary" @click="openForm(row)">编辑</el-button>
              <el-button v-if="row.canEdit" link type="success" @click="submitRow(row)">提交</el-button>
              <el-button v-if="row.status==='PENDING'" link type="warning" @click="withdrawRow(row)">撤回</el-button>
              <el-button v-if="row.status==='APPROVED'&&row.createdProjectId" link @click="openProject(row)">查看项目</el-button>
            </div></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane v-if="canReview" :label="`待我审批${reviewRows.length ? ` (${reviewRows.length})` : ''}`" name="review">
        <el-card shadow="never">
          <el-table :data="reviewRows" v-loading="loading" empty-text="当前没有待审批申请" @row-click="openDetail">
            <el-table-column label="项目" min-width="230"><template #default="{row}"><b>{{ row.projectName }}</b><small>{{ row.proposalNo }}</small></template></el-table-column>
            <el-table-column prop="applicantName" label="申请人/负责人" width="145" />
            <el-table-column prop="companyName" label="归属公司" min-width="150" />
            <el-table-column label="治理方式" min-width="170"><template #default="{row}"><b>{{ managementLabel[row.managementMode] || row.managementMode }}</b><small>{{ closeMethodLabel[row.closeMethod] || row.closeMethod }}</small></template></el-table-column>
            <el-table-column label="预算" width="140"><template #default="{row}">{{ row.noBudget==='1' ? '不设置预算' : money(row.budgetLimit,row.baseCurrency) }}</template></el-table-column>
            <el-table-column label="计划周期" width="210"><template #default="{row}">{{ row.planStartDate }} 至 {{ row.planEndDate }}</template></el-table-column>
            <el-table-column label="操作" width="190" fixed="right"><template #default="{row}"><div class="row-actions" @click.stop><el-button link type="success" @click="reviewRow(row,'APPROVED')">批准</el-button><el-button link type="warning" @click="reviewRow(row,'RETURNED')">退回</el-button><el-button link @click="openDetail(row)">详情</el-button></div></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane v-if="canReview" label="公司申请目录" name="directory">
        <el-card shadow="never"><el-table :data="directoryRows" v-loading="loading" empty-text="暂无立项申请"><el-table-column prop="projectName" label="项目名称" min-width="230"/><el-table-column prop="applicantName" label="申请人/负责人" width="150"/><el-table-column prop="sponsorOwnerName" label="归属老板" width="150"/><el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusTone[row.status] || 'info'">{{ statusLabel[row.status] || row.status }}</el-tag></template></el-table-column><el-table-column label="权限" width="120"><template #default="{row}"><span class="muted">仅目录可见</span></template></el-table-column></el-table></el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="formVisible" :title="form.proposalId ? '修改立项申请' : '发起立项申请'" width="min(900px,96vw)" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="申请人/负责人"><el-input :model-value="userStore.nickName || userStore.name" disabled /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="审批老板" prop="sponsorOwnerUserId"><el-select v-model="form.sponsorOwnerUserId" style="width:100%" placeholder="请选择审批老板"><el-option v-for="item in options.bosses" :key="item.userId" :label="bossOptionLabel(item)" :value="item.userId" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="项目名称" prop="projectName"><el-input v-model="form.projectName" maxlength="160" show-word-limit /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="归属公司" prop="companyDeptId"><el-select v-model="form.companyDeptId" style="width:100%" placeholder="请选择归属公司"><el-option v-for="item in options.companies" :key="item.deptId" :label="item.deptName" :value="item.deptId" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="项目类型"><el-select v-model="form.projectType" style="width:100%"><el-option v-for="(label,value) in typeLabel" :key="value" :label="label" :value="value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="核算方式"><el-select v-model="form.accountingMode" style="width:100%"><el-option v-for="(label,value) in accountingLabel" :key="value" :label="label" :value="value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="管理模式"><el-select v-model="form.managementMode" style="width:100%"><el-option v-for="(item,value) in managementOptions" :key="value" :label="item.label" :value="value" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-alert class="governance-tip" :title="managementOptions[form.managementMode]?.title" :description="managementOptions[form.managementMode]?.description" type="info" :closable="false" show-icon /></el-col>
          <el-col :span="12"><el-form-item label="结项方式"><el-select v-model="form.closeMethod" style="width:100%"><el-option v-for="(item,value) in closeMethodOptions" :key="value" :label="item.label" :value="value" /></el-select><small class="field-help">{{ closeMethodOptions[form.closeMethod]?.description }}</small></el-form-item></el-col>
          <el-col v-if="form.managementMode==='KEY_CONTROL'" :span="12"><el-form-item label="监管原因"><el-input v-model="form.managementReason" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="说明为何需要重点监管及主要监管事项" /></el-form-item></el-col>
          <el-col v-if="form.closeMethod!=='DIRECT'" :span="24"><el-form-item label="验收标准"><el-input v-model="form.acceptanceCriteria" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="列明验收指标、成果清单、通过条件和验收人" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="项目目标" prop="objective"><el-input v-model="form.objective" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="说明要解决的问题和可验收结果" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="立项理由" prop="applicationReason"><el-input v-model="form.applicationReason" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="说明现状/问题与机会是什么" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计划周期" prop="dates"><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="优先级"><el-select v-model="form.priority" style="width:100%"><el-option label="低" value="LOW"/><el-option label="中" value="MEDIUM"/><el-option label="高" value="HIGH"/></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="预算"><div class="budget-line"><el-checkbox v-model="noBudget">不设置预算</el-checkbox><el-input-number v-model="form.budgetLimit" :disabled="noBudget" :min="0" :precision="2" controls-position="right"/><el-select v-model="form.baseCurrency" :disabled="noBudget" style="width:110px"><el-option label="CNY" value="CNY"/><el-option label="VND" value="VND"/><el-option label="USD" value="USD"/></el-select></div></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="formVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveForm">保存草稿</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="min(720px,94vw)" append-to-body>
      <template #header><div><span class="eyebrow">{{ detail.proposalNo }}</span><h2>{{ detail.projectName }}</h2></div></template>
      <div class="detail-grid"><div><span>申请人/负责人</span><b>{{ detail.applicantName }}</b></div><div><span>审批/归属老板</span><b>{{ detail.sponsorOwnerName }}</b></div><div><span>管理模式</span><b>{{ managementLabel[detail.managementMode] || detail.managementMode }}</b></div><div><span>结项方式</span><b>{{ closeMethodLabel[detail.closeMethod] || detail.closeMethod }}</b></div><div><span>计划周期</span><b>{{ detail.planStartDate }} 至 {{ detail.planEndDate }}</b></div><div><span>预算</span><b>{{ detail.noBudget==='1'?'不设置预算':money(detail.budgetLimit,detail.baseCurrency) }}</b></div></div>
      <section class="detail-section"><h3>项目目标</h3><p>{{ detail.objective }}</p></section><section class="detail-section"><h3>立项理由</h3><p>{{ detail.applicationReason }}</p></section><section v-if="detail.managementReason" class="detail-section"><h3>重点监管原因</h3><p>{{ detail.managementReason }}</p></section><section v-if="detail.acceptanceCriteria" class="detail-section"><h3>验收标准</h3><p>{{ detail.acceptanceCriteria }}</p></section><section v-if="detail.reviewComment" class="detail-section review-comment"><h3>审批/撤回意见</h3><p>{{ detail.reviewComment }}</p></section>
      <section class="detail-section"><h3>操作记录</h3><el-timeline><el-timeline-item v-for="event in detail.events || []" :key="event.eventId" :timestamp="event.createTime"><b>{{ eventLabel[event.eventType] || event.eventType }}</b><p>{{ event.operatorName }}<span v-if="event.comment"> · {{ event.comment }}</span></p></el-timeline-item></el-timeline></section>
      <div class="drawer-actions"><el-button v-if="detail.canEdit" type="primary" @click="openForm(detail)">修改</el-button><el-button v-if="detail.canEdit" type="success" @click="submitRow(detail)">提交审批</el-button><el-button v-if="detail.canReview" type="success" @click="reviewRow(detail,'APPROVED')">批准并启动</el-button><el-button v-if="detail.canReview" type="warning" @click="reviewRow(detail,'RETURNED')">退回修改</el-button><el-button v-if="detail.status==='APPROVED'&&detail.createdProjectId" @click="openProject(detail)">查看正式项目</el-button></div>
    </el-drawer>
  </div>
</template>

<script setup name="BusinessProjectProposals">
import { ElMessage, ElMessageBox } from 'element-plus'
import useUserStore from '@/store/modules/user'
import { listProjectProposals, listProposalReviews, listProposalDirectory, getProjectProposal, getProjectProposalOptions, addProjectProposal, updateProjectProposal, submitProjectProposal, withdrawProjectProposal, reviewProjectProposal } from '@/api/business/proposal'

const route=useRoute(),router=useRouter(), userStore=useUserStore(), loading=ref(false), saving=ref(false), activeTab=ref('mine')
const mineRows=ref([]),reviewRows=ref([]),directoryRows=ref([]),options=reactive({bosses:[],companies:[]})
const formVisible=ref(false),detailVisible=ref(false),formRef=ref(),detail=ref({}),dates=ref([]),noBudget=ref(false)
const form=ref({})
const canReview=computed(()=>userStore.roles.includes('admin')||userStore.permissions.includes('*:*:*')||userStore.permissions.includes('business:project:proposal:review'))
const statusLabel={DRAFT:'草稿',PENDING:'待审批',RETURNED:'已退回',WITHDRAWN:'已撤回',APPROVED:'已批准'}
const statusTone={DRAFT:'info',PENDING:'warning',RETURNED:'danger',WITHDRAWN:'info',APPROVED:'success'}
const typeLabel={GENERAL:'通用',LIVE:'直播',JEWELRY:'珠宝',ECOMMERCE:'电商',OPERATIONS:'运营',INTERNAL:'内部',OTHER:'其他'}
const accountingLabel={PROFIT:'盈利型',COST:'成本型',VALUE:'价值型',HYBRID:'混合型'}
const managementOptions={LIGHT:{label:'轻量',title:'轻量管理：保留核心执行闭环',description:'适合周期短、范围清晰、风险较低的项目；保留任务、工时、成本和KPI，风险按异常登记。'},STANDARD:{label:'标准',title:'标准管理：完整的常规项目过程',description:'启用周度跟踪、里程碑、风险台账和预算预警，适合多数跨成员项目。'},KEY_CONTROL:{label:'重点监管',title:'重点监管：高风险或公司级项目',description:'强化里程碑、风险、预算分级预警和治理变更审批，立项时必须说明监管原因。'}}
const managementLabel={LIGHT:'轻量',STANDARD:'标准',KEY_CONTROL:'重点监管',SIMPLE:'轻量',DELIVERY:'标准'}
const closeMethodOptions={DIRECT:{label:'直接结项',description:'老板核对KPI及必要风险后直接关闭项目。'},RESULT_ACCEPTANCE:{label:'成果验收',description:'负责人提交整体验收资料，老板评审通过后关闭。'},STAGED_ACCEPTANCE:{label:'阶段验收',description:'按里程碑逐阶段提交和验收，全部通过后方可关闭。'}}
const closeMethodLabel=Object.fromEntries(Object.entries(closeMethodOptions).map(([key,item])=>[key,item.label]))
const eventLabel={CREATE:'创建草稿',EDIT:'修改草稿',SUBMIT:'提交审批',RESUBMIT:'重新提交',WITHDRAW:'撤回申请',RETURN:'退回修改',APPROVE:'批准立项'}
const rules={projectName:[{required:true,message:'请输入项目名称',trigger:'blur'}],sponsorOwnerUserId:[{required:true,message:'请选择审批老板',trigger:'change'}],companyDeptId:[{required:true,message:'请选择归属公司',trigger:'change'}],objective:[{required:true,message:'请填写项目目标',trigger:'blur'}],applicationReason:[{required:true,message:'请填写立项理由',trigger:'blur'}]}

const freshForm=()=>({projectName:'',sponsorOwnerUserId:null,companyDeptId:null,projectType:'GENERAL',accountingMode:'PROFIT',managementMode:'STANDARD',closeMethod:'DIRECT',managementReason:'',acceptanceCriteria:'',objective:'',applicationReason:'',priority:'MEDIUM',baseCurrency:'CNY',budgetLimit:null,noBudget:'0'})
async function ensureOptions(){if(options.bosses.length&&options.companies.length)return;const res=await getProjectProposalOptions();Object.assign(options,res.data||{})}
async function loadMine(){const res=await listProjectProposals({pageNum:1,pageSize:100});mineRows.value=res.rows||[]}
async function loadReview(){if(!canReview.value)return;const res=await listProposalReviews({pageNum:1,pageSize:100});reviewRows.value=res.rows||[]}
async function loadDirectory(){if(!canReview.value)return;const res=await listProposalDirectory({pageNum:1,pageSize:100});directoryRows.value=res.rows||[]}
async function loadActive(){loading.value=true;try{if(activeTab.value==='mine')await loadMine();else if(activeTab.value==='review')await loadReview();else await loadDirectory()}finally{loading.value=false}}
async function refreshAll(){await Promise.all([loadMine(),canReview.value?loadReview():Promise.resolve()]);if(activeTab.value==='directory')await loadDirectory()}
async function openForm(row){await ensureOptions();const source=row?.proposalId?(await getProjectProposal(row.proposalId)).data:freshForm();const legacyMode=source.managementMode==='SIMPLE'?'LIGHT':source.managementMode==='DELIVERY'?'STANDARD':source.managementMode;const legacyClose=source.closeMethod||(source.managementMode==='DELIVERY'?'RESULT_ACCEPTANCE':'DIRECT');form.value={...freshForm(),...source,managementMode:legacyMode,closeMethod:legacyClose};dates.value=source.planStartDate&&source.planEndDate?[source.planStartDate,source.planEndDate]:[];noBudget.value=source.noBudget==='1';formVisible.value=true;nextTick(()=>formRef.value?.clearValidate())}
async function saveForm(){await formRef.value.validate();if(!dates.value?.length)return ElMessage.warning('请选择计划周期');if(form.value.managementMode==='KEY_CONTROL'&&!form.value.managementReason?.trim())return ElMessage.warning('重点监管项目请填写监管原因');if(form.value.closeMethod!=='DIRECT'&&!form.value.acceptanceCriteria?.trim())return ElMessage.warning('请填写验收标准');if(!noBudget.value&&(form.value.budgetLimit===null||form.value.budgetLimit===undefined))return ElMessage.warning('请填写预算或勾选不设置预算');saving.value=true;try{const payload={...form.value,planStartDate:dates.value[0],planEndDate:dates.value[1],noBudget:noBudget.value?'1':'0',budgetLimit:noBudget.value?null:form.value.budgetLimit};const res=payload.proposalId?await updateProjectProposal(payload):await addProjectProposal(payload);formVisible.value=false;ElMessage.success('立项申请草稿已保存');await refreshAll();if(res.data?.proposalId)await openDetail(res.data)}finally{saving.value=false}}
async function openDetail(row){const res=await getProjectProposal(row.proposalId);detail.value=res.data||{};detailVisible.value=true}
async function submitRow(row){await ElMessageBox.confirm(`确定提交“${row.projectName}”给 ${row.sponsorOwnerName} 审批吗？提交后内容将冻结。`,'提交立项申请',{type:'warning'});await submitProjectProposal(row.proposalId);ElMessage.success('已提交审批');detailVisible.value=false;await refreshAll()}
async function withdrawRow(row){const result=await ElMessageBox.prompt('可以填写撤回说明','撤回立项申请',{inputPlaceholder:'可选'});await withdrawProjectProposal(row.proposalId,{comment:result.value});ElMessage.success('申请已撤回');detailVisible.value=false;await refreshAll()}
async function reviewRow(row,decision){let comment='';if(decision==='RETURNED'){const result=await ElMessageBox.prompt('请填写退回原因，申请人修改后可以重新提交','退回立项申请',{inputValidator:value=>!!value?.trim()||'必须填写退回原因'});comment=result.value}else await ElMessageBox.confirm(`批准“${row.projectName}”后将立即创建正式项目，并由 ${row.applicantName} 负责执行。确认批准吗？`,'批准立项',{type:'warning'});await reviewProjectProposal(row.proposalId,{decision,comment});ElMessage.success(decision==='APPROVED'?'立项已批准，项目已进入执行':'申请已退回修改');detailVisible.value=false;await refreshAll()}
function openProject(row){router.push({path:'/business/projects',query:{id:row.createdProjectId}})}
const bossOptionLabel=item=>item.nickName&&item.nickName!==item.userName?`${item.nickName}（${item.userName}）`:item.nickName||item.userName
const money=(value,currency='CNY')=>value===null||value===undefined?'—':`${Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})} ${currency}`
onMounted(async()=>{if(canReview.value)activeTab.value=['mine','directory'].includes(route.query.tab)?route.query.tab:'review';await ensureOptions();await refreshAll();if(route.query.id)await openDetail({proposalId:Number(route.query.id)})})
</script>

<style scoped>
.proposal-page{min-height:calc(100vh - 84px);padding:24px;background:#f4f6f8}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:16px}.eyebrow{font-size:11px;letter-spacing:.16em;color:#3977c5}.page-head h1{margin:4px 0;font-size:27px;color:#172033}.page-head p{margin:0;color:#778394}.proposal-tabs{margin-top:16px}.proposal-tabs :deep(.el-tabs__content){overflow:visible}.el-table b,.el-table small{display:block}.el-table small{margin-top:4px;color:#8994a2}.row-actions{display:flex;gap:8px}.row-actions .el-button+.el-button{margin-left:0}.budget-line{display:flex;align-items:center;gap:12px}.detail-grid{display:grid;grid-template-columns:repeat(2,1fr);border:1px solid #e1e6eb;border-radius:10px}.detail-grid div{padding:14px;border-right:1px solid #e1e6eb;border-bottom:1px solid #e1e6eb}.detail-grid div:nth-child(2n){border-right:0}.detail-grid div:nth-last-child(-n+2){border-bottom:0}.detail-grid span,.detail-grid b{display:block}.detail-grid span{color:#8793a1;font-size:12px}.detail-grid b{margin-top:6px}.detail-section{margin-top:18px;padding:16px;border:1px solid #e3e8ed;border-radius:10px;background:#fff}.detail-section h3{margin:0 0 10px;font-size:15px}.detail-section p{margin:0;line-height:1.7;white-space:pre-wrap}.review-comment{border-left:3px solid #d8892f;background:#fffaf2}.drawer-actions{position:sticky;bottom:0;display:flex;flex-wrap:wrap;gap:8px;margin-top:18px;padding:14px 0;background:#fff}.drawer-actions .el-button+.el-button{margin-left:0}.muted{color:#9099a5;font-size:12px}@media(max-width:760px){.proposal-page{padding:14px}.page-head{align-items:flex-start;flex-direction:column;gap:14px}.page-head>.el-button{width:100%}.detail-grid{grid-template-columns:1fr}.detail-grid div,.detail-grid div:nth-child(2n){border-right:0;border-bottom:1px solid #e1e6eb}.detail-grid div:last-child{border-bottom:0}.budget-line{align-items:flex-start;flex-direction:column}.el-dialog .el-col{max-width:100%;flex:0 0 100%}}
.governance-tip{margin:-4px 0 16px}.field-help{display:block;margin-top:5px;color:#8793a1;line-height:1.45}
</style>
