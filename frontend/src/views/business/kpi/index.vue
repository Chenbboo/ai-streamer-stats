<template>
  <div class="app-container kpi-page" v-loading="loading">
    <header class="kpi-hero">
      <div><span>PROJECT KPI & BONUS</span><h1>项目KPI与奖金</h1><p>老板发布项目目标与人民币奖金阶梯，负责人填报结果，老板确认后立即计入项目成本。</p></div>
      <div class="hero-tools">
        <el-select v-model="selectedProjectId" filterable placeholder="选择项目" @change="switchProject">
          <el-option v-for="project in projects" :key="project.projectId" :label="`${project.projectName} · ${project.mainOwnerName}`" :value="project.projectId" />
        </el-select>
        <el-button icon="Refresh" @click="loadWorkspace(selectedProjectId, selectedPlan?.planId)">刷新</el-button>
      </div>
    </header>

    <el-empty v-if="!selectedProjectId && !loading" description="当前没有可管理的项目" />

    <template v-if="workspace.project">
      <section class="summary-grid">
        <article><span>项目</span><b>{{ workspace.project.projectName }}</b><small>{{ workspace.project.mainOwnerName }}负责</small></article>
        <article><span>当前KPI</span><b>{{ currentTargets.length }} 项</b><small>权重合计 {{ weightTotal }}%</small></article>
        <article><span>考核方案</span><b>{{ selectedPlan ? `v${selectedPlan.planVersion}` : '未发布' }}</b><small>{{ selectedPlan ? `${cycleLabel[selectedPlan.cycleType]} · ${selectedPlan.cycleStart} 至 ${selectedPlan.cycleEnd}` : '老板发布后开始结算' }}</small></article>
        <article><span>结算状态</span><b>{{ settlementLabel[settlement?.status] || '未开始' }}</b><small>{{ settlement?.reviewComment || '项目级结算，不涉及个人奖金' }}</small></article>
        <article><span>{{ settlement?.status === 'CONFIRMED' ? '确认奖金' : '预计奖金' }}</span><b>¥{{ money(settlement?.bonusAmount) }}</b><small>综合得分 {{ settlement?.totalScore ?? '—' }}</small></article>
      </section>

      <div class="content-grid">
        <main>
          <el-card shadow="never" class="section-card">
            <div class="section-head">
              <div><h2>项目KPI目标</h2><p>只考核项目。调整目标会生成新版本，已发布方案继续使用原快照。</p></div>
              <el-button v-if="workspace.canManage" type="primary" @click="openTarget()">新增KPI</el-button>
            </div>
            <el-alert v-if="currentTargets.length && Number(weightTotal)!==100" :title="`当前权重合计 ${weightTotal}%，必须调整为100%后才能发布方案。`" type="warning" :closable="false" show-icon />
            <el-table :data="currentTargets" empty-text="老板尚未设置项目KPI">
              <el-table-column label="指标" min-width="180"><template #default="{row}"><b>{{ row.kpiName }}</b><small>{{ row.kpiCode }} · v{{ row.targetVersion }}</small></template></el-table-column>
              <el-table-column label="目标" min-width="125"><template #default="{row}">{{ row.targetValue }} {{ row.unit || '' }}</template></el-table-column>
              <el-table-column label="方向" width="95"><template #default="{row}">{{ directionLabel[row.direction] }}</template></el-table-column>
              <el-table-column label="权重" width="85"><template #default="{row}">{{ row.weight }}%</template></el-table-column>
              <el-table-column label="周期" width="95"><template #default="{row}">{{ cycleLabel[row.periodType] }}</template></el-table-column>
              <el-table-column v-if="workspace.canManage" label="操作" width="120"><template #default="{row}"><el-button link @click="openTarget(row)">调整</el-button><el-button link type="danger" @click="retireTarget(row)">停用</el-button></template></el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="section-card">
            <div class="section-head">
              <div><h2>项目结果填报</h2><p>项目负责人逐项填写实际值和说明；老板确认后结果锁定并形成项目奖金成本。</p></div>
              <div class="section-actions" v-if="settlement && workspace.canSettle && ['DRAFT','RETURNED'].includes(settlement.status)">
                <el-button :loading="saving" @click="saveResults">保存草稿</el-button>
                <el-button type="primary" :loading="saving" :disabled="!periodEnded" @click="submitResults">提交结算</el-button>
              </div>
            </div>
            <el-alert v-if="settlement?.status==='RETURNED'" :title="`老板退回：${settlement.reviewComment}`" type="warning" :closable="false" show-icon />
            <el-alert v-if="settlement && !periodEnded && ['DRAFT','RETURNED'].includes(settlement.status)" :title="`考核将在 ${settlement.periodEnd} 结束；现在可以保存草稿，周期结束后才能提交。`" type="info" :closable="false" show-icon />
            <el-empty v-if="!selectedPlan" description="尚未发布KPI方案" />
            <div v-else class="result-list">
              <article v-for="item in selectedPlan.items || []" :key="item.itemId" class="result-row">
                <div class="result-target"><b>{{ item.kpiName }}</b><span>目标 {{ item.targetValue }} {{ item.unit || '' }} · 权重 {{ item.weight }}% · {{ directionLabel[item.direction] }}</span></div>
                <template v-if="workspace.canSettle && ['DRAFT','RETURNED'].includes(settlement?.status)">
                  <el-input-number v-model="resultDraft[item.itemId].actualValue" :min="0" :precision="4" controls-position="right" placeholder="实际值" />
                  <el-input v-model="resultDraft[item.itemId].resultNote" maxlength="1000" show-word-limit placeholder="填写数据来源、结果说明或异常原因" />
                  <file-upload v-model="resultDraft[item.itemId].attachmentUrls" :limit="6" :file-size="20" :file-type="['pdf','doc','docx','xls','xlsx','jpg','jpeg','png']" />
                </template>
                <template v-else>
                  <div class="result-value"><strong>{{ resultFor(item.itemId)?.actualValue ?? '—' }} {{ item.unit || '' }}</strong><span>完成率 {{ resultFor(item.itemId)?.completionRate ?? '—' }}% · 加权 {{ resultFor(item.itemId)?.weightedScore ?? '—' }}</span></div>
                  <p>{{ resultFor(item.itemId)?.resultNote || '尚未填报' }}</p>
                </template>
              </article>
            </div>
            <div v-if="settlement?.status==='SUBMITTED' && workspace.canManage" class="review-bar">
              <div><b>负责人已提交，等待老板确认</b><span>确认后 ¥{{ money(settlement.bonusAmount) }} 将立即计入项目成本。</span></div>
              <el-button type="warning" plain :loading="saving" @click="returnSettlement">退回修改</el-button>
              <el-button type="success" :loading="saving" @click="confirmSettlement">确认并计入成本</el-button>
            </div>
            <el-result v-if="settlement?.status==='CONFIRMED'" icon="success" title="项目KPI奖金已确认" :sub-title="`综合得分 ${settlement.totalScore}，项目奖金 ¥${money(settlement.bonusAmount)} 已计入 ${settlement.periodEnd} 项目成本。`" />
          </el-card>
        </main>

        <aside>
          <el-card shadow="never" class="section-card bonus-card">
            <div class="section-head"><div><h2>人民币奖金阶梯</h2><p>按综合得分命中一档，不累计、不分配到个人。</p></div><el-button v-if="workspace.canManage" type="primary" :disabled="Number(weightTotal)!==100 || !currentTargets.length" @click="openPlan">发布新方案</el-button></div>
            <el-empty v-if="!selectedPlan" description="尚未发布奖金阶梯" :image-size="72" />
            <div v-for="tier in selectedPlan?.tiers || []" :key="tier.tierId" class="tier-row"><span><b>{{ tier.tierName }}</b><small>{{ scoreRange(tier) }}</small></span><strong>¥{{ money(tier.bonusAmount) }}</strong></div>
          </el-card>

          <el-card shadow="never" class="section-card">
            <div class="section-head"><div><h2>方案历史</h2><p>发布快照不可覆盖，点击查看对应结算。</p></div></div>
            <div v-if="!workspace.plans?.length" class="empty-text">暂无历史方案</div>
            <div v-for="plan in workspace.plans || []" :key="plan.planId" role="button" tabindex="0" class="plan-row" :class="{active:Number(plan.planId)===Number(selectedPlan?.planId)}" @click="loadWorkspace(selectedProjectId,plan.planId)" @keyup.enter="loadWorkspace(selectedProjectId,plan.planId)">
              <span><b>v{{ plan.planVersion }} · {{ cycleLabel[plan.cycleType] }}</b><small>{{ plan.cycleStart }} 至 {{ plan.cycleEnd }}</small></span>
              <span class="plan-row-actions"><el-tag :type="settlementTone[plan.settlementStatus]">{{ settlementLabel[plan.settlementStatus] }}</el-tag><el-button v-if="workspace.canManage && ['DRAFT','RETURNED'].includes(plan.settlementStatus)" link type="danger" @click.stop="removePlan(plan)">删除</el-button></span>
            </div>
          </el-card>
        </aside>
      </div>
    </template>

    <el-dialog v-model="targetDialog" :title="targetForm.kpiId ? '调整项目KPI目标' : '新增项目KPI'" width="min(680px,94vw)" append-to-body>
      <el-alert title="KPI只评价项目，不指定个人考核对象；已发布方案继续使用原目标快照。" type="info" :closable="false" show-icon />
      <el-form :model="targetForm" label-width="92px" class="dialog-form">
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="系统编码"><el-input v-model="targetForm.kpiCode" disabled placeholder="保存后自动生成" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="指标名称" required><el-input v-model="targetForm.kpiName" /></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="指标类型"><el-select v-model="targetForm.metricType" style="width:100%"><el-option v-for="(label,key) in metricTypeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="考核周期"><el-select v-model="targetForm.periodType" style="width:100%"><el-option v-for="key in ['MONTH','QUARTER','PROJECT']" :key="key" :label="cycleLabel[key]" :value="key" /></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="目标值" required><el-input-number v-model="targetForm.targetValue" :min="0.0001" :precision="4" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="单位"><el-input v-model="targetForm.unit" placeholder="元、个、%等" /></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="权重%" required><el-input-number v-model="targetForm.weight" :min="0" :max="100" :precision="2" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="考核方向"><el-select v-model="targetForm.direction" style="width:100%"><el-option label="越高越好" value="HIGHER_BETTER"/><el-option label="越低越好" value="LOWER_BETTER"/></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item label="最低值"><el-input-number v-model="targetForm.minimumValue" :precision="4" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item label="挑战值"><el-input-number v-model="targetForm.challengeValue" :precision="4" style="width:100%" /></el-form-item></el-col></el-row>
        <el-form-item label="生效日期" required><el-date-picker v-model="targetForm.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="调整说明"><el-input v-model="targetForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="targetDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveTarget">保存KPI版本</el-button></template>
    </el-dialog>

    <el-dialog v-model="planDialog" title="发布项目KPI与奖金方案" width="min(900px,96vw)" append-to-body>
      <el-alert title="发布后将冻结当前KPI目标、权重和奖金阶梯；第一阶段币种固定为人民币CNY。" type="warning" :closable="false" show-icon />
      <el-form :model="planForm" label-width="92px" class="dialog-form">
        <el-row :gutter="12"><el-col :sm="10" :xs="24"><el-form-item label="考核周期" required><el-select v-model="planForm.cycleType" style="width:100%" @change="resetPlanDates"><el-option label="月度" value="MONTH"/><el-option label="季度" value="QUARTER"/><el-option label="项目周期" value="PROJECT"/></el-select></el-form-item></el-col><el-col :sm="14" :xs="24"><el-form-item label="起止日期" required><el-date-picker v-model="planDates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width:100%" /></el-form-item></el-col></el-row>
        <el-form-item label="方案说明"><el-input v-model="planForm.remark" maxlength="500" /></el-form-item>
        <div class="tier-editor-head"><div><b>综合阶梯奖金</b><small>每行代表一个奖金档位，分数范围左侧包含、右侧不包含；最后一档自动设为无上限。</small></div><el-button size="small" @click="addTier">增加阶梯</el-button></div>
        <el-alert class="tier-example" title="填写示例：达标｜最低80分｜最高100分｜奖金1,000元，表示 80 ≤ 综合得分 < 100 时，项目获得1,000元奖金。" type="info" :closable="false" show-icon />
        <div class="tier-editor-columns">
          <span>档位名称</span><span>最低分（包含）</span><span>最高分（不包含）</span><span>项目奖金（人民币元）</span><span>操作</span>
        </div>
        <div v-for="(tier,index) in planForm.tiers" :key="index" class="tier-editor-row">
          <div class="tier-field"><span>档位名称</span><el-input v-model="tier.tierName" placeholder="如：达标" /></div>
          <div class="tier-field"><span>最低分（包含）</span><el-input-number v-model="tier.minScore" :min="0" :precision="2" placeholder="如：80" /></div>
          <div class="tier-field"><span>最高分（不包含）</span><el-input-number v-model="tier.maxScore" :min="0" :precision="2" :disabled="index===planForm.tiers.length-1" :placeholder="index===planForm.tiers.length-1?'末档无上限':'如：100'" /></div>
          <div class="tier-field"><span>项目奖金（人民币元）</span><el-input-number v-model="tier.bonusAmount" :min="0" :precision="2" placeholder="如：1000" /></div>
          <div class="tier-action"><el-button v-if="planForm.tiers.length>2" link type="danger" @click="removeTier(index)">删除</el-button><span v-else>—</span></div>
        </div>
      </el-form>
      <template #footer><el-button @click="planDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="publishPlan">确认发布</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessProjectKpiBonus">
import { ElMessage, ElMessageBox } from 'element-plus'
import { listBusinessProjects, retireBusinessProjectKpi, saveBusinessProjectKpi } from '@/api/business/project'
import { deleteProjectKpiPlan, getProjectKpiWorkspace, publishProjectKpiPlan, reviewProjectKpiSettlement, saveProjectKpiResults, submitProjectKpiSettlement } from '@/api/business/kpi'

const route=useRoute(),router=useRouter()
const loading=ref(false),saving=ref(false),projects=ref([]),projectsLoaded=ref(false),selectedProjectId=ref(null),workspace=reactive({}),targetDialog=ref(false),targetForm=reactive({}),planDialog=ref(false),planForm=reactive({}),planDates=ref([]),resultDraft=reactive({})
let workspaceRequestId=0
const selectedPlan=computed(()=>workspace.selectedPlan||null),settlement=computed(()=>selectedPlan.value?.settlement||null),currentTargets=computed(()=>workspace.currentTargets||[])
const weightTotal=computed(()=>currentTargets.value.reduce((sum,item)=>sum+Number(item.weight||0),0).toFixed(2).replace(/\.00$/,''))
const periodEnded=computed(()=>!settlement.value?.periodEnd||settlement.value.periodEnd<=today())
const cycleLabel={MONTH:'月度',QUARTER:'季度',PROJECT:'项目周期'}
const directionLabel={HIGHER_BETTER:'越高越好',LOWER_BETTER:'越低越好'}
const metricTypeLabel={COUNT:'数量',AMOUNT:'金额',PERCENT:'百分比',DURATION:'时长',SCORE:'评分',MILESTONE:'里程碑'}
const settlementLabel={DRAFT:'填报中',SUBMITTED:'待老板确认',RETURNED:'已退回',CONFIRMED:'已确认'}
const settlementTone={DRAFT:'info',SUBMITTED:'warning',RETURNED:'danger',CONFIRMED:'success'}
const today=()=>new Date().toISOString().slice(0,10)
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
function resultFor(itemId){return settlement.value?.results?.find(result=>Number(result.planItemId)===Number(itemId))}
function scoreRange(tier){return tier.maxScore===null||tier.maxScore===undefined?`${tier.minScore}分及以上`:`${tier.minScore} ≤ 得分 < ${tier.maxScore}`}
function hydrateDraft(){Object.keys(resultDraft).forEach(key=>delete resultDraft[key]);for(const item of selectedPlan.value?.items||[]){const stored=resultFor(item.itemId)||{};resultDraft[item.itemId]={planItemId:item.itemId,actualValue:stored.actualValue??null,resultNote:stored.resultNote||'',attachmentUrls:stored.attachmentUrls||''}}}
function idKey(value){const scalar=Array.isArray(value)?value[0]:value;return scalar===null||scalar===undefined?'':String(scalar)}
async function loadProjects(){const res=await listBusinessProjects({pageNum:1,pageSize:200,status:''});projects.value=res.rows||[];const requested=idKey(route.query.projectId),matched=projects.value.find(item=>idKey(item.projectId)===requested);selectedProjectId.value=matched?.projectId??projects.value[0]?.projectId??null;projectsLoaded.value=true}
async function loadWorkspace(projectId,planId){if(!idKey(projectId))return;const requestId=++workspaceRequestId;loading.value=true;try{const res=await getProjectKpiWorkspace(projectId,planId);if(requestId!==workspaceRequestId)return;Object.keys(workspace).forEach(key=>delete workspace[key]);Object.assign(workspace,res.data||{});selectedProjectId.value=projectId;hydrateDraft();if(route.path==='/business/kpi-bonus')await router.replace({query:{projectId,planId:workspace.selectedPlan?.planId||undefined}})}finally{if(requestId===workspaceRequestId)loading.value=false}}
async function syncWorkspaceFromRoute(){if(route.path!=='/business/kpi-bonus'||!projectsLoaded.value)return;const requested=idKey(route.query.projectId),matched=projects.value.find(item=>idKey(item.projectId)===requested);if(!matched)return;const requestedPlan=idKey(route.query.planId),currentPlan=idKey(selectedPlan.value?.planId),currentProject=idKey(workspace.project?.projectId);if(currentProject===requested&&(!requestedPlan||requestedPlan===currentPlan))return;await loadWorkspace(matched.projectId,route.query.planId)}
function switchProject(projectId){loadWorkspace(projectId)}
function openTarget(row={}){Object.assign(targetForm,{kpiId:null,projectId:selectedProjectId.value,kpiCode:'',kpiName:'',metricType:'COUNT',periodType:'MONTH',targetValue:null,minimumValue:null,warningValue:null,challengeValue:null,unit:'',weight:0,direction:'HIGHER_BETTER',aggregateType:'SUM',sourceType:'MANUAL',effectiveFrom:today(),remark:'',...row,actualValue:null,ownerUserId:null,ownerName:null});targetDialog.value=true}
async function saveTarget(){if(!targetForm.kpiName?.trim())return ElMessage.warning('请填写指标名称');if(!(Number(targetForm.targetValue)>0))return ElMessage.warning('KPI目标值必须大于0');saving.value=true;try{await saveBusinessProjectKpi(targetForm);targetDialog.value=false;await loadWorkspace(selectedProjectId.value,selectedPlan.value?.planId);ElMessage.success(targetForm.kpiId?'KPI新版本已保存':'KPI已创建，编码已自动生成')}finally{saving.value=false}}
async function retireTarget(row){await ElMessageBox.confirm(`确认停用“${row.kpiName}”吗？已发布方案不会受影响。`,'停用KPI',{type:'warning'});await retireBusinessProjectKpi(selectedProjectId.value,row.kpiId);await loadWorkspace(selectedProjectId.value,selectedPlan.value?.planId);ElMessage.success('KPI已停用')}
function monthRange(){const date=new Date(),start=new Date(date.getFullYear(),date.getMonth(),1),end=new Date(date.getFullYear(),date.getMonth()+1,0);return [localDate(start),localDate(end)]}
function quarterRange(){const date=new Date(),month=Math.floor(date.getMonth()/3)*3,start=new Date(date.getFullYear(),month,1),end=new Date(date.getFullYear(),month+3,0);return [localDate(start),localDate(end)]}
function localDate(date){const offset=new Date(date.getTime()-date.getTimezoneOffset()*60000);return offset.toISOString().slice(0,10)}
function resetPlanDates(){planDates.value=planForm.cycleType==='MONTH'?monthRange():planForm.cycleType==='QUARTER'?quarterRange():[workspace.project.planStartDate||today(),workspace.project.planEndDate||today()]}
function openPlan(){Object.assign(planForm,{projectId:selectedProjectId.value,cycleType:'MONTH',remark:'',tiers:[{tierName:'未达标',minScore:0,maxScore:80,bonusAmount:0},{tierName:'达标',minScore:80,maxScore:90,bonusAmount:0},{tierName:'良好',minScore:90,maxScore:100,bonusAmount:0},{tierName:'优秀',minScore:100,maxScore:null,bonusAmount:0}]});resetPlanDates();planDialog.value=true}
function addTier(){const last=planForm.tiers.at(-1),start=Number(last.minScore||0)+10;if(last.maxScore===null)last.maxScore=start;planForm.tiers.push({tierName:`阶梯${planForm.tiers.length+1}`,minScore:start,maxScore:null,bonusAmount:0})}
function removeTier(index){planForm.tiers.splice(index,1);planForm.tiers.at(-1).maxScore=null}
async function publishPlan(){if(!planDates.value?.[0]||!planDates.value?.[1])return ElMessage.warning('请选择考核起止日期');for(let i=0;i<planForm.tiers.length;i++){const tier=planForm.tiers[i],row=i+1,last=i===planForm.tiers.length-1;if(!tier.tierName?.trim()||tier.minScore===null||tier.minScore===undefined||tier.bonusAmount===null||tier.bonusAmount===undefined)return ElMessage.warning(`请完整填写第${row}档的名称、最低分和奖金金额`);if(i===0&&Number(tier.minScore)!==0)return ElMessage.warning('第一档最低分必须从0分开始');if(!last&&(tier.maxScore===null||tier.maxScore===undefined))return ElMessage.warning(`请填写第${row}档的最高分`);if(!last&&Number(tier.maxScore)<=Number(tier.minScore))return ElMessage.warning(`第${row}档最高分必须大于最低分`);if(i>0&&Number(tier.minScore)!==Number(planForm.tiers[i-1].maxScore))return ElMessage.warning(`第${row}档最低分必须等于上一档最高分，确保区间连续`);if(last)tier.maxScore=null}await ElMessageBox.confirm('发布后目标、权重和奖金阶梯将形成快照，不能直接覆盖。确定发布吗？','发布KPI方案',{type:'warning'});saving.value=true;try{const res=await publishProjectKpiPlan({...planForm,cycleStart:planDates.value[0],cycleEnd:planDates.value[1]});Object.keys(workspace).forEach(key=>delete workspace[key]);Object.assign(workspace,res.data||{});hydrateDraft();planDialog.value=false;ElMessage.success('项目KPI与奖金方案已发布')}finally{saving.value=false}}
async function removePlan(row){await ElMessageBox.confirm(`确定删除 v${row.planVersion} · ${cycleLabel[row.cycleType]} 吗？该方案的填报草稿、结果和奖金阶梯将一并删除，且无法恢复。`,'删除KPI方案',{type:'warning',confirmButtonText:'确认删除'});saving.value=true;try{await deleteProjectKpiPlan(row.planId);await loadWorkspace(selectedProjectId.value);ElMessage.success('KPI方案已删除')}finally{saving.value=false}}
function resultPayload(requireAll=false){const rows=Object.values(resultDraft).filter(row=>row.actualValue!==null&&row.actualValue!==undefined);if(!rows.length){ElMessage.warning('请至少填写一项KPI结果');return null}for(const row of rows)if(!row.resultNote?.trim()){ElMessage.warning('每项手工结果都必须填写说明');return null}if(requireAll&&rows.length!==(selectedPlan.value?.items?.length||0)){ElMessage.warning('请完整填写所有KPI结果');return null}return {results:rows}}
async function saveResults(showMessage=true,requireAll=false){const payload=resultPayload(requireAll);if(!payload)return false;saving.value=true;try{const res=await saveProjectKpiResults(settlement.value.settlementId,payload);selectedPlan.value.settlement=res.data;hydrateDraft();if(showMessage)ElMessage.success('KPI结果草稿已保存');return true}finally{saving.value=false}}
async function submitResults(){if(!periodEnded.value)return ElMessage.warning('考核周期尚未结束');if(!await saveResults(false,true))return;await ElMessageBox.confirm('提交后将等待老板确认，确认后项目奖金会立即计入项目成本。确定提交吗？','提交KPI结算',{type:'warning'});saving.value=true;try{const res=await submitProjectKpiSettlement(settlement.value.settlementId);selectedPlan.value.settlement=res.data;ElMessage.success('KPI结算已提交')}finally{saving.value=false}}
async function returnSettlement(){const{value}=await ElMessageBox.prompt('请填写需要负责人修正的内容','退回KPI结算',{inputValidator:value=>!!value?.trim()||'必须填写退回原因',type:'warning'});saving.value=true;try{const res=await reviewProjectKpiSettlement(settlement.value.settlementId,{decision:'RETURNED',comment:value});selectedPlan.value.settlement=res.data;hydrateDraft();ElMessage.success('已退回项目负责人修改')}finally{saving.value=false}}
async function confirmSettlement(){await ElMessageBox.confirm(`确认综合得分 ${settlement.value.totalScore}、项目奖金 ¥${money(settlement.value.bonusAmount)} 吗？确认后将立即计入 ${settlement.value.periodEnd} 项目成本。`,'确认项目KPI奖金',{type:'warning',confirmButtonText:'确认并计入成本'});saving.value=true;try{const res=await reviewProjectKpiSettlement(settlement.value.settlementId,{decision:'CONFIRMED',comment:'确认项目KPI及奖金'});selectedPlan.value.settlement=res.data;await loadWorkspace(selectedProjectId.value,selectedPlan.value.planId);ElMessage.success('项目奖金已确认并计入项目成本')}finally{saving.value=false}}
watch(()=>route.fullPath,syncWorkspaceFromRoute)
onActivated(syncWorkspaceFromRoute)
onMounted(async()=>{loading.value=true;try{await loadProjects();if(selectedProjectId.value)await loadWorkspace(selectedProjectId.value,route.query.planId)}finally{loading.value=false}})
</script>

<style scoped>
.kpi-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f5f8;color:#182537}.kpi-hero{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;padding:26px 30px;border-radius:16px;background:linear-gradient(120deg,#173750,#23655d);color:#fff}.kpi-hero>div:first-child>span{font-size:11px;letter-spacing:.17em;color:#7dd7ca}.kpi-hero h1{margin:5px 0;font-size:28px}.kpi-hero p{margin:0;color:#cbdcdf}.hero-tools{display:flex;align-items:center;gap:10px}.hero-tools .el-select{width:330px}.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin:15px 0}.summary-grid article{min-width:0;padding:16px 18px;border:1px solid #dfe5ea;border-radius:12px;background:#fff}.summary-grid span,.summary-grid small,.summary-grid b{display:block}.summary-grid span,.summary-grid small{color:#7d8997}.summary-grid span{font-size:12px}.summary-grid b{overflow:hidden;margin:7px 0;font-size:20px;text-overflow:ellipsis;white-space:nowrap}.summary-grid small{font-size:12px;line-height:1.45}.content-grid{display:grid;grid-template-columns:minmax(0,1.55fr) minmax(320px,.65fr);gap:14px}.content-grid main,.content-grid aside{display:flex;min-width:0;flex-direction:column;gap:14px}.section-card{border-color:#dfe5ea}.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:14px}.section-head h2{margin:0;font-size:18px}.section-head p{margin:5px 0 0;color:#82909f;font-size:12px;line-height:1.5}.section-actions{display:flex;gap:8px}.section-card small{display:block;margin-top:4px;color:#8793a0}.result-list{border-top:1px solid #edf0f3}.result-row{display:grid;grid-template-columns:minmax(170px,.7fr) minmax(150px,.4fr) minmax(230px,1fr) minmax(170px,.65fr);align-items:start;gap:12px;padding:16px 0;border-bottom:1px solid #edf0f3}.result-target,.result-value{display:flex;flex-direction:column;gap:5px}.result-target span,.result-value span{color:#83909e;font-size:12px}.result-row>p{grid-column:2/-1;margin:0;color:#5f6e7e;line-height:1.6}.review-bar{display:flex;align-items:center;justify-content:flex-end;gap:10px;margin-top:16px;padding:15px;border-radius:10px;background:#f6f8fa}.review-bar>div{display:flex;min-width:0;flex:1;flex-direction:column}.review-bar span{margin-top:5px;color:#7e8b99;font-size:12px}.tier-row,.plan-row{display:flex;width:100%;align-items:center;justify-content:space-between;gap:10px;padding:13px 2px;border:0;border-top:1px solid #edf0f3;background:transparent;color:inherit;text-align:left}.tier-row>span,.plan-row>span{display:flex;min-width:0;flex-direction:column}.tier-row strong{color:#217865}.plan-row{cursor:pointer}.plan-row.active{color:#226f67}.plan-row:hover{background:#f7f9fa}.empty-text{padding:20px;text-align:center;color:#929da8}.dialog-form{margin-top:18px}.tier-editor-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin:18px 0 9px}.tier-editor-head>div{display:flex;flex-direction:column;gap:4px}.tier-editor-head small{color:#7d8997;line-height:1.5}.tier-example{margin-bottom:10px}.tier-editor-columns,.tier-editor-row{display:grid;grid-template-columns:minmax(120px,.8fr) minmax(130px,.72fr) minmax(145px,.8fr) minmax(180px,1fr) 54px;gap:8px}.tier-editor-columns{padding:0 0 6px;color:#657384;font-size:12px}.tier-editor-columns span{padding-left:2px}.tier-editor-row{align-items:center;margin-bottom:8px}.tier-field>span{display:none}.tier-editor-row :deep(.el-input-number){width:100%}.tier-action{text-align:center;color:#a0a8b1}@media(max-width:1180px){.summary-grid{grid-template-columns:repeat(3,1fr)}.content-grid{grid-template-columns:1fr}.result-row{grid-template-columns:minmax(170px,.7fr) minmax(150px,.4fr) minmax(230px,1fr)}.result-row>:last-child{grid-column:2/-1}}@media(max-width:720px){.kpi-page{padding:12px}.kpi-hero{align-items:flex-start;flex-direction:column;padding:21px}.hero-tools{width:100%;align-items:stretch;flex-direction:column}.hero-tools .el-select,.hero-tools .el-button{width:100%}.summary-grid{grid-template-columns:repeat(2,1fr);gap:8px}.summary-grid article:last-child{grid-column:1/-1}.section-head,.review-bar,.tier-editor-head{align-items:stretch;flex-direction:column}.section-actions{display:grid;grid-template-columns:1fr 1fr}.result-row{grid-template-columns:1fr}.result-row>*,.result-row>:last-child,.result-row>p{grid-column:1}.tier-editor-columns{display:none}.tier-editor-row{grid-template-columns:1fr 1fr;padding:12px;border:1px solid #e5e9ed;border-radius:9px}.tier-field{display:flex;min-width:0;flex-direction:column;gap:5px}.tier-field>span{display:block;color:#657384;font-size:12px}.tier-editor-row>*:first-child,.tier-editor-row>*:nth-child(4),.tier-action{grid-column:1/-1}.tier-action{text-align:right}}
.plan-row-actions{align-items:flex-end;gap:4px}
</style>
