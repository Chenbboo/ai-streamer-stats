<template>
  <div class="app-container staff-page">
    <header class="hero">
      <div><span class="eyebrow">COMPANY PEOPLE</span><h1>人员管理</h1><p>统一维护上海与越南公司人员档案、组织关系和系统账号。</p></div>
      <el-button type="primary" icon="Plus" @click="openCreate">新增人员</el-button>
    </header>

    <section class="panel search-panel">
      <el-form :model="query" inline>
        <el-form-item label="人员"><el-input v-model="query.nickName" clearable placeholder="姓名" @keyup.enter="search" /></el-form-item>
        <el-form-item label="组织"><el-tree-select v-model="query.deptId" :data="departments" :props="departmentTreeProps" check-strictly clearable placeholder="全部公司与部门" style="width:210px" /></el-form-item>
        <el-form-item label="账号状态"><el-select v-model="query.status" clearable placeholder="全部" style="width:120px"><el-option label="正常" value="0"/><el-option label="停用" value="1"/></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </section>

    <section class="panel list-panel">
      <div class="panel-head"><div><h2>公司人员</h2><p>共 {{ total }} 个有效人员账号，人员档案与原业务账号共用</p></div><el-button icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button></div>

      <el-table class="desktop-table" :data="rows" v-loading="loading" empty-text="暂无人员">
        <el-table-column label="人员" min-width="155"><template #default="{row}"><button class="person-link" @click="openDetail(row)"><b>{{ row.nickName }}</b><small>{{ row.employeeNo || row.userName }}</small></button></template></el-table-column>
        <el-table-column label="所属公司" min-width="150"><template #default="{row}">{{ row.companyName || '集团层级' }}</template></el-table-column>
        <el-table-column label="所属部门" min-width="150"><template #default="{row}">{{ row.deptName || '未设置' }}</template></el-table-column>
        <el-table-column label="直属负责人" min-width="110"><template #default="{row}">{{ row.managerName || '—' }}</template></el-table-column>
        <el-table-column label="联系方式" min-width="150"><template #default="{row}"><span>{{ formatPhone(row) }}</span><small>{{ row.email || '未设置邮箱' }}</small></template></el-table-column>
        <el-table-column label="任职" width="105"><template #default="{row}"><el-tag :type="employmentTag(row.employmentStatus)">{{ employmentStatusLabel(row.employmentStatus) }}</el-tag><small>{{ employmentTypeLabel(row.employmentType) }}</small></template></el-table-column>
        <el-table-column label="账号" width="90"><template #default="{row}"><el-switch v-model="row.status" active-value="0" inactive-value="1" :disabled="row.protectedAccount" @change="changeStatus(row)" /></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="{row}">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="!row.protectedAccount" link type="primary" @click="resetPassword(row)">密码</el-button>
        </template></el-table-column>
      </el-table>

      <div class="mobile-staff-list" v-loading="loading">
        <article v-for="row in rows" :key="row.userId" class="staff-card" @click="openDetail(row)">
          <div class="card-head"><div><b>{{ row.nickName }}</b><span>{{ row.employeeNo || row.userName }}</span></div><el-tag :type="employmentTag(row.employmentStatus)">{{ employmentStatusLabel(row.employmentStatus) }}</el-tag></div>
          <p>{{ row.companyName || '集团层级' }} · {{ row.deptName || '未设置部门' }}</p>
          <p>直属负责人：{{ row.managerName || '未设置' }}</p>
          <div class="card-foot"><span>{{ formatPhone(row) }}</span><el-button link type="primary" @click.stop="openEdit(row)">编辑</el-button></div>
        </article>
        <el-empty v-if="!loading && !rows.length" description="暂无人员" />
      </div>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    </section>

    <el-dialog v-model="dialogOpen" class="staff-edit-dialog" :title="form.userId ? '编辑人员档案' : '新增人员'" width="760px" append-to-body :close-on-click-modal="false">
      <el-alert v-if="form.protectedAccount" title="这是受保护账号：可以维护基础资料，但不能调整组织归属、直属负责人、任职状态、账号角色和密码。" type="warning" :closable="false" show-icon />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <h3 class="form-section">基本资料</h3>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="人员姓名" prop="nickName"><el-input v-model="form.nickName" maxlength="30" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="员工编号" prop="employeeNo"><el-input v-model="form.employeeNo" maxlength="32" placeholder="可选，保存后唯一" /></el-form-item></el-col>
          <el-col v-if="!form.userId" :span="12"><el-form-item label="登录账号" prop="userName"><el-input v-model="form.userName" maxlength="30" /></el-form-item></el-col>
          <el-col v-if="!form.userId" :span="12"><el-form-item label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="20" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="国家/地区" prop="countryRegion"><el-select v-model="form.countryRegion" style="width:100%" @change="changeRegion"><el-option label="中国" value="CN"/><el-option label="越南" value="VN"/><el-option label="其他" value="OTHER"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="性别"><el-select v-model="form.sex" style="width:100%"><el-option label="男" value="0"/><el-option label="女" value="1"/><el-option label="未知" value="2"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="手机号码" prop="phonenumber"><div class="phone-input"><el-select v-model="form.phoneCountryCode" filterable allow-create default-first-option style="width:92px"><el-option label="+86" value="+86"/><el-option label="+84" value="+84"/></el-select><el-input v-model="form.phonenumber" maxlength="15" placeholder="6至15位数字" /></div></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="50" /></el-form-item></el-col>
        </el-row>

        <h3 class="form-section">组织与任职</h3>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="所属组织" prop="deptId"><el-tree-select v-model="form.deptId" :data="departments" :props="departmentTreeProps" check-strictly clearable :disabled="form.protectedAccount" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="直属负责人"><el-select v-model="form.managerUserId" filterable clearable :disabled="form.protectedAccount" placeholder="请选择" style="width:100%"><el-option v-for="person in managerOptions" :key="person.userId" :value="person.userId" :label="`${person.nickName} · ${person.deptName || person.companyName || '集团'}`" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="工作地点"><el-input v-model="form.workLocation" maxlength="100" placeholder="例如：上海、胡志明市" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="用工类型"><el-select v-model="form.employmentType" style="width:100%"><el-option v-for="item in employmentTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="任职状态"><el-select v-model="form.employmentStatus" :disabled="form.protectedAccount" style="width:100%"><el-option v-for="item in employmentStatuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="入职日期"><el-date-picker v-model="form.hireDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><div class="dialog-actions"><el-button @click="dialogOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></div></template>
    </el-dialog>

    <el-drawer v-model="detailOpen" :title="`${selectedPerson?.nickName || ''} · 人员详情`" size="min(720px, 96vw)" append-to-body>
      <template v-if="selectedPerson">
        <div class="detail-identity"><div><b>{{ selectedPerson.nickName }}</b><span>{{ selectedPerson.userName }}</span></div><el-tag :type="selectedPerson.protectedAccount ? 'warning' : 'info'">{{ selectedPerson.accountType }}</el-tag></div>
        <section class="detail-section"><h3>基本资料</h3><div class="detail-grid">
          <div><span>员工编号</span><b>{{ selectedPerson.employeeNo || '未设置' }}</b></div><div><span>国家/地区</span><b>{{ regionLabel(selectedPerson.countryRegion) }}</b></div>
          <div><span>手机号</span><b>{{ formatPhone(selectedPerson) }}</b></div><div><span>邮箱</span><b>{{ selectedPerson.email || '未设置' }}</b></div>
          <div><span>性别</span><b>{{ sexLabel(selectedPerson.sex) }}</b></div><div><span>工作地点</span><b>{{ selectedPerson.workLocation || '未设置' }}</b></div>
        </div></section>
        <section class="detail-section"><h3>组织与任职</h3><div class="detail-grid">
          <div><span>所属公司</span><b>{{ selectedPerson.companyName || '集团层级' }}</b></div><div><span>所属部门</span><b>{{ selectedPerson.deptName || '未设置' }}</b></div>
          <div><span>直属负责人</span><b>{{ selectedPerson.managerName || '未设置' }}</b></div><div><span>工作地点</span><b>{{ selectedPerson.workLocation || '未设置' }}</b></div>
          <div><span>用工类型</span><b>{{ employmentTypeLabel(selectedPerson.employmentType) }}</b></div><div><span>任职状态</span><b>{{ employmentStatusLabel(selectedPerson.employmentStatus) }}</b></div>
          <div><span>入职日期</span><b>{{ selectedPerson.hireDate || '未设置' }}</b></div><div><span>系统角色</span><b>{{ selectedPerson.roleNames || selectedPerson.accountType }}</b></div>
        </div></section>
        <section v-if="isBoss" class="detail-section cost-policy-section"><div class="detail-section-head"><div><h3>内部核算成本</h3><p>仅老板可见，用于项目成本和盈亏核算，不代表员工真实工资。</p></div><el-button size="small" type="primary" @click="openCostPolicy">设置新版本</el-button></div>
          <el-table :data="costPolicies" size="small" empty-text="尚未设置内部核算成本"><el-table-column label="版本" width="70"><template #default="{row}">v{{ row.policyVersion }}</template></el-table-column><el-table-column label="核算标准"><template #default="{row}"><b>{{ money(row.unitCost) }} {{ row.currency }}</b> / {{ costModeLabel[row.costMode] }}</template></el-table-column><el-table-column label="生效区间" min-width="170"><template #default="{row}">{{ row.effectiveFrom }} 至 {{ row.effectiveTo || '长期' }}</template></el-table-column><el-table-column prop="remark" label="说明" /></el-table>
        </section>
        <section class="detail-section project-responsibility"><h3>项目责任</h3>
          <div class="responsibility-summary" v-loading="projectLoading">
            <div><span>主负责</span><b>{{ projectSummary.ownerCount }}</b></div><div><span>参与项目</span><b>{{ projectSummary.memberCount }}</b></div>
            <div><span>可操作未结束</span><b>{{ projectSummary.openCount }}</b></div><div><span>可见任务完成</span><b>{{ projectSummary.completedTaskCount }}/{{ projectSummary.assignedTaskCount }}</b></div>
          </div>
          <div class="responsibility-list" v-loading="projectLoading">
            <article v-for="project in projectSummary.projects" :key="project.projectId" class="responsibility-card">
              <div class="responsibility-card-head"><div><b>{{ project.projectName }}</b><span>{{ project.projectNo || '暂无项目编号' }}</span></div><el-tag size="small" type="warning">{{ project.initiatorName || '未知老板' }}立项</el-tag></div>
              <div class="responsibility-meta"><span>{{ projectRoleLabel(project) }}</span><span v-if="project.status">{{ projectStatusLabel[project.status] || project.status }}</span><span v-if="project.canOpen">任务 {{ project.completedTaskCount || 0 }}/{{ project.assignedTaskCount || 0 }}</span></div>
              <div class="responsibility-foot"><span v-if="!project.canOpen">仅展示项目归属，运营详情已隔离</span><el-button v-else link type="primary" @click="openPersonProject(project)">打开项目</el-button></div>
            </article>
            <el-empty v-if="!projectLoading && !projectSummary.projects.length" :image-size="72" description="暂未负责或参与项目" />
          </div>
        </section>
        <section class="detail-section"><h3>系统账号</h3><div class="detail-grid">
          <div><span>账号状态</span><b>{{ selectedPerson.status==='0' ? '正常' : '停用' }}</b></div><div><span>最后登录</span><b>{{ formatDate(selectedPerson.loginDate) }}</b></div>
        </div></section>
        <el-button type="primary" plain @click="openEdit(selectedPerson);detailOpen=false">编辑人员资料</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="costDialog" title="设置人员内部核算成本" width="min(560px, 94vw)" append-to-body><el-alert title="每次保存都会产生新版本；为保证历史核算可追溯，生效区间不能与已有版本重叠。" type="warning" :closable="false" show-icon/><el-form :model="costForm" label-width="100px" class="cost-form"><el-form-item label="人员"><el-input :model-value="selectedPerson?.nickName" disabled /></el-form-item><el-form-item label="核算方式" required><el-select v-model="costForm.costMode" style="width:100%"><el-option v-for="(label,key) in costModeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item label="内部金额" required><el-input-number v-model="costForm.unitCost" :min="0" :precision="4" style="width:100%" /></el-form-item><el-form-item label="币种" required><el-input v-model="costForm.currency" maxlength="3" /></el-form-item><el-form-item label="生效日期" required><el-date-picker v-model="costForm.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="失效日期"><el-date-picker v-model="costForm.effectiveTo" type="date" value-format="YYYY-MM-DD" clearable style="width:100%" /></el-form-item><el-form-item label="政策说明"><el-input v-model="costForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="costDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCostPolicy">保存成本版本</el-button></template></el-dialog>
  </div>
</template>

<script setup name="BusinessStaff">
import { ElMessage, ElMessageBox } from 'element-plus'
import { addBusinessStaff, changeBusinessStaffStatus, getBusinessStaffCostPolicies, getBusinessStaffProjects, listBusinessDepartments, listBusinessStaff, listBusinessStaffOptions, resetBusinessStaffPassword, saveBusinessStaffCostPolicy, updateBusinessStaff } from '@/api/business/staff'
import useUserStore from '@/store/modules/user'
import { usePasswordRule } from '@/utils/passwordRule'

const { pwdValidator, pwdPromptValidator } = usePasswordRule()
const employmentTypes=[{label:'全职',value:'FULL_TIME'},{label:'兼职',value:'PART_TIME'},{label:'合同/外包',value:'CONTRACTOR'},{label:'实习',value:'INTERN'}]
const employmentStatuses=[{label:'试用期',value:'PROBATION'},{label:'在职',value:'ACTIVE'},{label:'休假/暂离',value:'ON_LEAVE'},{label:'离职',value:'LEFT'}]
const departmentTreeProps={value:'id',label:'label',children:'children'}
const router=useRouter()
const userStore=useUserStore()
const loading=ref(false),saving=ref(false),dialogOpen=ref(false),detailOpen=ref(false),projectLoading=ref(false)
const costDialog=ref(false),costPolicies=ref([]),costForm=reactive({})
const formRef=ref(),rows=ref([]),total=ref(0),departments=ref([]),staffOptions=ref([]),selectedPerson=ref()
const emptyProjectSummary=()=>({projects:[],ownerCount:0,memberCount:0,openCount:0,assignedTaskCount:0,completedTaskCount:0})
const projectSummary=ref(emptyProjectSummary())
const projectStatusLabel={DRAFT:'草稿',PLANNING:'规划中',ACTIVE:'执行中',PAUSED:'已暂停',ACCEPTANCE:'待验收',CLOSED:'已关闭',CANCELED:'已取消'}
const costModeLabel={DAILY:'日成本',HOURLY:'时成本',MONTHLY:'月成本',FIXED_PROJECT:'项目固定成本',FIXED_TASK:'任务固定成本',VARIABLE:'浮动成本'}
const isBoss=computed(()=>userStore.roles.includes('admin')||userStore.permissions.includes('*:*:*')||userStore.permissions.includes('business:boss:view'))
const query=reactive({pageNum:1,pageSize:10,nickName:'',deptId:null,status:''})
const form=reactive({})
const managerOptions=computed(()=>staffOptions.value.filter(person=>person.userId!==form.userId))
const rules={
  nickName:[{required:true,message:'请输入人员姓名',trigger:'blur'}],userName:[{required:true,message:'请输入登录账号',trigger:'blur'}],password:pwdValidator.value,
  employeeNo:[{max:32,message:'员工编号不能超过32个字符',trigger:'blur'}],deptId:[{required:true,message:'请选择所属公司或部门',trigger:'change'}],countryRegion:[{required:true,message:'请选择国家或地区',trigger:'change'}],
  phonenumber:[{pattern:/^\d{6,15}$/,message:'请输入6至15位数字',trigger:'blur'}],email:[{type:'email',message:'邮箱格式不正确',trigger:'blur'}]
}

function defaultForm(){return {nickName:'',userName:'',password:'',employeeNo:'',deptId:null,phoneCountryCode:'+86',phonenumber:'',email:'',countryRegion:'CN',sex:'2',positionName:'',managerUserId:null,employmentType:'FULL_TIME',employmentStatus:'ACTIVE',hireDate:null,workLocation:'',remark:'',protectedAccount:false}}
function resetForm(row){Object.keys(form).forEach(key=>delete form[key]);Object.assign(form,row?{...defaultForm(),...row,hireDate:row.hireDate?String(row.hireDate).slice(0,10):null}:defaultForm())}
async function load(){loading.value=true;try{const data=await listBusinessStaff(query);rows.value=data.rows||[];total.value=data.total||0;if(selectedPerson.value){selectedPerson.value=rows.value.find(x=>x.userId===selectedPerson.value.userId)||selectedPerson.value}}finally{loading.value=false}}
async function loadReference(){const [deptResult,optionResult]=await Promise.all([listBusinessDepartments(),listBusinessStaffOptions()]);departments.value=deptResult.data||[];staffOptions.value=optionResult.data||[]}
async function loadAll(){await Promise.all([load(),loadReference()])}
function search(){query.pageNum=1;load()}
function resetSearch(){Object.assign(query,{pageNum:1,nickName:'',deptId:null,status:''});load()}
function openCreate(){resetForm();dialogOpen.value=true;nextTick(()=>formRef.value?.clearValidate())}
function openEdit(row){resetForm(row);dialogOpen.value=true;nextTick(()=>formRef.value?.clearValidate())}
async function openDetail(row){selectedPerson.value=row;detailOpen.value=true;projectSummary.value=emptyProjectSummary();costPolicies.value=[];projectLoading.value=true;try{const requests=[getBusinessStaffProjects(row.userId)];if(isBoss.value)requests.push(getBusinessStaffCostPolicies(row.userId));const results=await Promise.all(requests);projectSummary.value=results[0].data||emptyProjectSummary();if(results[1])costPolicies.value=results[1].data||[]}finally{projectLoading.value=false}}
function projectRoleLabel(project){if(project.responsibilityRole==='OWNER')return '主负责人';if(project.responsibilityRole==='DEPUTY')return '副负责人';if(project.responsibilityRole==='OBSERVER')return '观察者';return project.everOwner?'成员（曾任负责人）':'成员'}
function openPersonProject(project){detailOpen.value=false;router.push({path:'/business/projects',query:{id:project.projectId}})}
function changeRegion(value){if(value==='CN')form.phoneCountryCode='+86';if(value==='VN')form.phoneCountryCode='+84'}
async function save(){await formRef.value.validate();saving.value=true;try{form.userId?await updateBusinessStaff(form):await addBusinessStaff(form);ElMessage.success('人员档案已保存');dialogOpen.value=false;await loadAll()}finally{saving.value=false}}
async function changeStatus(row){const previous=row.status==='0'?'1':'0';try{await ElMessageBox.confirm(`确定${row.status==='0'?'启用':'停用'}“${row.nickName}”的账号吗？`,'账号状态',{type:'warning'});await changeBusinessStaffStatus({userId:row.userId,status:row.status});ElMessage.success('状态已更新')}catch(error){row.status=previous;if(error!=='cancel')throw error}}
async function resetPassword(row){const {value}=await ElMessageBox.prompt(`请输入“${row.nickName}”的新密码`,'重置密码',{inputType:'password',inputValidator:pwdPromptValidator,confirmButtonText:'确定',cancelButtonText:'取消'});await resetBusinessStaffPassword({userId:row.userId,password:value});ElMessage.success('密码已重置')}
const money=value=>value===null||value===undefined?'—':Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:4})
function openCostPolicy(){Object.keys(costForm).forEach(key=>delete costForm[key]);Object.assign(costForm,{userId:selectedPerson.value.userId,costMode:'DAILY',unitCost:null,currency:selectedPerson.value.countryRegion==='VN'?'VND':'CNY',effectiveFrom:new Date().toISOString().slice(0,10),effectiveTo:null,remark:''});costDialog.value=true}
async function saveCostPolicy(){if(costForm.unitCost===null||costForm.unitCost===undefined)return ElMessage.warning('请填写内部核算金额');if(!costForm.effectiveFrom)return ElMessage.warning('请选择生效日期');saving.value=true;try{await saveBusinessStaffCostPolicy(costForm);costPolicies.value=(await getBusinessStaffCostPolicies(selectedPerson.value.userId)).data||[];costDialog.value=false;ElMessage.success('内部核算成本新版本已保存')}finally{saving.value=false}}
function formatDate(value){return value?String(value).replace('T',' ').slice(0,19):'尚未登录'}
function formatPhone(row){return row?.phonenumber?`${row.phoneCountryCode||''} ${row.phonenumber}`.trim():'未设置手机'}
function employmentTypeLabel(value){return employmentTypes.find(x=>x.value===value)?.label||'未设置'}
function employmentStatusLabel(value){return employmentStatuses.find(x=>x.value===value)?.label||'未设置'}
function employmentTag(value){return value==='ACTIVE'?'success':value==='PROBATION'?'warning':value==='LEFT'?'info':''}
function regionLabel(value){return value==='CN'?'中国':value==='VN'?'越南':value||'未设置'}
function sexLabel(value){return value==='0'?'男':value==='1'?'女':'未知'}
loadAll()
</script>

<style scoped>
.staff-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f5f8;color:#172033}.hero{display:flex;align-items:flex-end;justify-content:space-between;padding:25px 30px;border-radius:16px;background:linear-gradient(120deg,#12314a,#1a5a62);color:#fff}.eyebrow{font-size:11px;letter-spacing:.18em;color:#8fe0d5}.hero h1{margin:5px 0 4px;font-size:28px}.hero p{margin:0;color:#c9dce0}.panel{margin-top:16px;padding:18px 20px;border:1px solid #e0e5eb;border-radius:14px;background:#fff}.search-panel{padding-bottom:0}.panel-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.panel-head h2{margin:0;font-size:18px}.panel-head p{margin:4px 0 0;color:#8490a0;font-size:13px}.desktop-table small{display:block;margin-top:4px;color:#8994a2}.person-link{padding:0;border:0;background:none;color:inherit;text-align:left;cursor:pointer}.person-link b,.person-link small{display:block}.phone-input{display:flex;width:100%;gap:8px}.form-section{margin:20px 0 14px;padding-bottom:8px;border-bottom:1px solid #edf0f3;color:#26455e;font-size:15px}.dialog-actions{display:flex;justify-content:flex-end;gap:10px}.detail-identity{display:flex;align-items:center;justify-content:space-between;padding:18px;border-radius:12px;background:#eef5f7}.detail-identity b,.detail-identity span{display:block}.detail-identity b{font-size:22px}.detail-identity span{margin-top:4px;color:#788694}.detail-section{margin-top:20px}.detail-section h3{font-size:16px}.detail-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.detail-grid div{padding:13px;border:1px solid #e5e9ee;border-radius:9px}.detail-grid span,.detail-grid b{display:block}.detail-grid span{margin-bottom:5px;color:#85909d;font-size:12px}.detail-grid b{font-size:14px}.mobile-staff-list{display:none}.staff-card{padding:15px;border:1px solid #e2e7ec;border-radius:12px;background:#fff}.card-head,.card-foot{display:flex;align-items:center;justify-content:space-between}.card-head b,.card-head span{display:block}.card-head span{margin-top:3px;color:#8994a2;font-size:12px}.staff-card p{margin:10px 0;color:#5f6e7d;font-size:13px}.card-foot{padding-top:10px;border-top:1px solid #eef1f4;color:#465666;font-size:13px}
.responsibility-summary{display:grid;grid-template-columns:repeat(4,1fr);border:1px solid #e3e8ed;border-radius:10px;background:#f8fafb}.responsibility-summary div{padding:12px;border-right:1px solid #e3e8ed}.responsibility-summary div:last-child{border:0}.responsibility-summary span,.responsibility-summary b{display:block}.responsibility-summary span{color:#85909d;font-size:12px}.responsibility-summary b{margin-top:5px;font-size:18px}.responsibility-list{display:grid;gap:10px;margin-top:10px}.responsibility-card{padding:13px 14px;border:1px solid #e3e8ed;border-radius:10px}.responsibility-card-head,.responsibility-foot{display:flex;align-items:center;justify-content:space-between;gap:10px}.responsibility-card-head b,.responsibility-card-head span{display:block}.responsibility-card-head span{margin-top:3px;color:#8a95a3;font-size:12px}.responsibility-meta{display:flex;flex-wrap:wrap;gap:8px 16px;margin-top:10px;color:#536273;font-size:13px}.responsibility-foot{min-height:24px;margin-top:8px;color:#9a6c25;font-size:12px}
.detail-section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.detail-section-head h3{margin:0}.detail-section-head p{margin:5px 0 12px;color:#8793a1;font-size:12px}.cost-policy-section{padding:14px;border:1px solid #d9e4ef;border-radius:10px;background:#f8fbfe}.cost-form{margin-top:18px}
@media(max-width:760px){.staff-page{padding:14px}.hero{align-items:flex-start;flex-direction:column;gap:16px;padding:22px}.hero .el-button{width:100%}.panel{padding:14px}.panel-head{align-items:flex-start;flex-direction:column;gap:12px}.search-panel :deep(.el-form-item){display:flex;margin-right:0}.search-panel :deep(.el-form-item__content){flex:1}.search-panel :deep(.el-input),.search-panel :deep(.el-select),.search-panel :deep(.el-tree-select){width:100%!important}.desktop-table{display:none}.mobile-staff-list{display:grid;gap:10px}.detail-grid{grid-template-columns:1fr}.responsibility-summary{grid-template-columns:repeat(2,1fr)}.responsibility-summary div:nth-child(2){border-right:0}.responsibility-summary div:nth-child(-n+2){border-bottom:1px solid #e3e8ed}.phone-input{align-items:stretch}.dialog-actions{display:grid;grid-template-columns:1fr 1fr;width:100%;gap:10px}.dialog-actions .el-button{width:100%;margin:0}}
@media(max-width:760px){:global(.el-dialog.staff-edit-dialog:not(.is-fullscreen)){display:flex!important;flex-direction:column;box-sizing:border-box;width:100%!important;max-width:100%!important;height:100vh!important;height:100dvh!important;margin:0!important;border-radius:0!important;overflow:hidden}:global(.staff-edit-dialog .el-dialog__header){flex:none;box-sizing:border-box;margin:0;padding:15px 16px;border-bottom:1px solid #e8ecf0}:global(.staff-edit-dialog .el-dialog__body){flex:1;box-sizing:border-box;width:100%;min-height:0;padding:12px 16px 18px;overflow-x:hidden;overflow-y:auto;overscroll-behavior:contain}:global(.staff-edit-dialog .el-dialog__footer){flex:none;box-sizing:border-box;width:100%;padding:10px 16px calc(10px + env(safe-area-inset-bottom));border-top:1px solid #e2e7ec;background:#fff;box-shadow:0 -6px 18px rgba(23,32,51,.08)}:global(.staff-edit-dialog .el-row){max-width:100%}:global(.staff-edit-dialog .el-col){max-width:100%;flex:0 0 100%}:global(.staff-edit-dialog .el-form-item){display:block;margin-bottom:16px}:global(.staff-edit-dialog .el-form-item__label){width:auto!important;height:auto;margin-bottom:6px;padding:0;line-height:22px}:global(.staff-edit-dialog .el-form-item__content){margin-left:0!important}:global(.staff-edit-dialog .el-alert){margin-bottom:12px}:global(.staff-edit-dialog .form-section:first-of-type){margin-top:8px}}
</style>
