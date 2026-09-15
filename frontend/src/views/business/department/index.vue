<template>
  <div class="app-container department-page">
    <header class="hero">
      <div><span class="eyebrow">ORGANIZATION</span><h1>集团与部门管理</h1><p>两位老板共享维护上海、越南两家公司及其部门，人员可在人员管理中调整归属。</p></div>
      <el-button type="primary" icon="Plus" @click="openCreate(rootRow())">新增公司</el-button>
    </header>

    <section class="panel search-panel">
      <el-form :model="query" inline>
        <el-form-item label="部门名称"><el-input v-model="query.deptName" clearable placeholder="输入部门名称" @keyup.enter="load" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部状态" style="width:130px"><el-option label="正常" value="0"/><el-option label="停用" value="1"/></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button><el-button @click="resetSearch">重置</el-button></el-form-item>
      </el-form>
    </section>

    <section class="panel">
      <div class="panel-head"><div><h2>组织架构</h2><p>点击公司查看部门，点击部门展开员工；调整顺序后点击保存排序</p></div><div><el-button icon="Sort" @click="toggleExpandAll">{{ expanded ? '全部折叠' : '全部展开' }}</el-button><el-button type="primary" plain :loading="savingSort" @click="saveSort">保存排序</el-button></div></div>
      <el-table :data="tableRows" v-loading="loading" :row-key="organizationKey" :row-class-name="({row})=>row.isDepartmentHeader ? 'department-header-row' : (row.isEmployeeRow || row.isEmptyStaffRow ? 'employee-row' : '')" :expand-row-keys="expandedIds" :tree-props="{children:'children'}" @expand-change="onExpandChange">
        <el-table-column prop="deptName" label="组织名称" min-width="280">
          <template #default="{row}">
            <span v-if="row.isDepartmentHeader" class="department-column-heading">部门名称</span>
            <button v-else-if="row.isEmployeeRow" type="button" class="employee-label employee-link" :aria-label="`查看${displayName(row.person)}的员工详情`" @click.stop="openEmployeeDetail(row.person)"><b>{{ displayName(row.person) }}</b><small>{{ row.person.userName }}</small></button>
            <span v-else-if="row.isEmptyStaffRow" class="empty-staff">暂无员工</span>
            <span v-else class="organization-label">
              <button v-if="row.children?.length" type="button" class="organization-toggle" :aria-expanded="expandedIds.includes(organizationKey(row))" @click.stop="onExpandChange(row,!expandedIds.includes(organizationKey(row)))">
                {{ row.deptName }}
                <small>{{ isRoot(row) ? '集团根节点' : (isCompany(row) ? `公司 · ${row.source.children?.length || 0} 个部门` : `${directStaffFor(row).length} 位直属员工`) }}</small>
              </button>
              <span v-else><b>{{ row.deptName }}</b><small v-if="isRoot(row)">集团根节点</small><small v-else-if="isCompany(row)">公司</small></span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="人员" width="100" align="center"><template #default="{row}"><span v-if="row.isDepartmentHeader" class="department-column-heading">人员</span><el-button v-else-if="row.source" link type="primary" @click="openPeople(row)">{{ peopleFor(row).length }} 人</el-button></template></el-table-column>
        <el-table-column label="排序" width="110"><template #default="{row}"><span v-if="row.isDepartmentHeader" class="department-column-heading">排序</span><el-input-number v-else-if="row.source" v-model="row.source.orderNum" :min="0" controls-position="right" size="small" /></template></el-table-column>
        <el-table-column prop="leader" label="负责人" width="130"><template #default="{row}"><span v-if="row.isDepartmentHeader" class="department-column-heading">负责人</span><template v-else-if="row.source">{{ row.leader || '—' }}</template></template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{row}"><span v-if="row.isDepartmentHeader" class="department-column-heading">状态</span><el-tag v-else-if="!row.isEmptyStaffRow" :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '正常' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right"><template #default="{row}">
          <span v-if="row.isDepartmentHeader" class="department-column-heading">操作</span>
          <template v-else-if="row.isEmployeeRow">
            <el-button link type="primary" @click="openEmployeeDetail(row.person)">查看详情</el-button>
            <el-button v-if="canAssignStaff && !row.person.protectedAccount" link type="danger" :loading="removingStaffId===row.person.userId" :disabled="removingStaffId!==null" @click="removeEmployee(row.person)">移出部门</el-button>
          </template>
          <template v-else-if="row.source">
          <el-button v-if="isRoot(row) || isCompany(row)" link type="primary" @click="openCreate(row)">{{ isRoot(row) ? '新增公司' : '新增部门' }}</el-button>
          <el-button v-else-if="canAssignStaff" link type="primary" :disabled="row.status!=='0'" @click="openAssignStaff(row)">加入员工</el-button>
          <el-button v-if="!isRoot(row)" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="!isProtected(row)" link type="danger" @click="remove(row)">删除</el-button>
          <span v-if="isProtected(row)" class="protected-copy">受保护</span>
          </template>
        </template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="assignStaffOpen" :title="`${assignDepartment?.deptName || ''} · 加入员工`" width="min(680px, 96vw)" append-to-body :close-on-click-modal="!assigningStaff" :close-on-press-escape="!assigningStaff" :show-close="!assigningStaff">
      <div v-loading="loadingStaffOptions">
        <p class="assign-staff-copy">选择已有员工账号加入本部门。已在其他部门的员工，保存后将转入本部门。</p>
        <el-select v-model="assignUserIds" multiple filterable clearable :multiple-limit="200" :disabled="assigningStaff || loadingStaffOptions" placeholder="搜索员工姓名、登录账号或原部门" style="width:100%" no-data-text="暂无可加入的员工账号" aria-label="选择员工账号">
          <el-option v-for="person in assignableStaff" :key="person.userId" :value="person.userId" :label="`${displayName(person)} · ${person.userName} · ${person.deptName || '未设置部门'}`" />
        </el-select>
        <el-table v-if="assignUserIds.length" :data="selectedAssignStaff" max-height="320" style="margin-top:16px">
          <el-table-column label="员工"><template #default="{row}">{{ displayName(row) }}<small>{{ row.userName }}</small></template></el-table-column>
          <el-table-column prop="deptName" label="原部门" />
          <el-table-column label="加入部门"><template #default>{{ assignDepartment?.deptName }}</template></el-table-column>
        </el-table>
      </div>
      <template #footer><el-button :disabled="assigningStaff" @click="assignStaffOpen=false">取消</el-button><el-button type="primary" :loading="assigningStaff" :disabled="!assignUserIds.length || loadingStaffOptions" @click="saveAssignedStaff">确认加入{{ assignUserIds.length ? `（${assignUserIds.length}人）` : '' }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="dialogOpen" :title="dialogTitle" width="620px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-row :gutter="16">
          <el-col :span="24"><el-form-item label="上级组织" prop="parentId"><el-tree-select v-model="form.parentId" :data="parentOptions" :props="{value:'deptId',label:'deptName',children:'children'}" value-key="deptId" check-strictly :disabled="lockedOrganization" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="组织名称" prop="deptName"><el-input v-model="form.deptName" maxlength="30" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="显示顺序" prop="orderNum"><el-input-number v-model="form.orderNum" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="负责人" prop="leaderUserId">
            <el-select v-model="form.leaderUserId" filterable clearable placeholder="请选择现有人员" style="width:100%" @change="bindLeader">
              <el-option v-for="person in leaderOptions" :key="person.userId" :value="person.userId" :label="leaderLabel(person)" :disabled="person.status!=='0'">
                <div class="leader-option"><span><b>{{ displayName(person) }}</b><small>{{ person.userName }}</small></span><em>{{ person.deptName || '未设置组织' }}<template v-if="person.status!=='0'"> · 已停用</template></em></div>
              </el-option>
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系电话"><el-input :model-value="form.phone || ''" readonly placeholder="随负责人自动带出" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="邮箱"><el-input :model-value="form.email || ''" readonly placeholder="随负责人自动带出" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="组织状态"><el-radio-group v-model="form.status" :disabled="lockedOrganization"><el-radio value="0">正常</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialogOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="peopleOpen" :title="`${selectedOrganization?.deptName || ''} · 人员明细`" size="min(680px, 94vw)" append-to-body>
      <div class="people-summary">
        <div><b>{{ selectedPeople.length }}</b><span>全部人员</span></div>
        <div><b>{{ directPeopleCount }}</b><span>直属人员</span></div>
        <div><b>{{ selectedPeople.length-directPeopleCount }}</b><span>下级部门</span></div>
      </div>
      <el-table :data="selectedPeople" empty-text="该组织暂无人员" max-height="calc(100vh - 230px)">
        <el-table-column label="人员" min-width="150"><template #default="{row}"><button type="button" class="employee-label employee-link" :aria-label="`查看${displayName(row)}的员工详情`" @click="openEmployeeDetail(row)"><b>{{ displayName(row) }}</b><small v-if="displayName(row)!==row.userName">{{ row.userName }}</small></button></template></el-table-column>
        <el-table-column prop="deptName" label="所属组织" min-width="145"><template #default="{row}">{{ row.deptName || '未设置' }}<small>{{ row.deptId===selectedOrganization?.deptId ? '直属' : '下级组织' }}</small></template></el-table-column>
        <el-table-column prop="roleNames" label="角色" min-width="150"><template #default="{row}">{{ row.roleNames || row.accountType || '员工' }}</template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.status==='0' ? 'success' : 'info'">{{ row.status==='0' ? '正常' : '停用' }}</el-tag></template></el-table-column>
      </el-table>
    </el-drawer>
    <el-drawer v-model="employeeDetailOpen" :title="`${displayName(selectedEmployee)} · 员工详情`" size="min(680px, 96vw)" append-to-body class="employee-detail-drawer">
      <template v-if="selectedEmployee">
        <div class="employee-identity"><div><h2>{{ displayName(selectedEmployee) }}</h2><span>{{ selectedEmployee.userName }}</span></div><el-tag :type="selectedEmployee.status==='0' ? 'success' : 'info'">{{ selectedEmployee.status==='0' ? '正常' : '停用' }}</el-tag></div>
        <section v-for="section in employeeDetailSections" :key="section.title" class="employee-detail-section">
          <h3>{{ section.title }}</h3>
          <dl class="employee-detail-grid"><div v-for="[label,value] in section.fields" :key="label"><dt>{{ label }}</dt><dd>{{ value || '未设置' }}</dd></div></dl>
        </section>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="BusinessDepartment">
import { ElMessage, ElMessageBox } from 'element-plus'
import { addBusinessDepartment, assignBusinessDepartmentStaff, listBusinessDepartments, listBusinessDepartmentStaff, removeBusinessDepartment, removeBusinessDepartmentStaff, saveBusinessDepartmentSort, updateBusinessDepartment } from '@/api/business/department'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
import { checkPermi } from '@/utils/permission'

const loading = ref(false)
const saving = ref(false)
const savingSort = ref(false)
const dialogOpen = ref(false)
const peopleOpen = ref(false)
const employeeDetailOpen = ref(false)
const selectedEmployeeId = ref()
const assignStaffOpen = ref(false)
const assigningStaff = ref(false)
const loadingStaffOptions = ref(false)
const assignDepartment = ref()
const assignUserIds = ref([])
const removingStaffId = ref(null)
const canAssignStaff = computed(() => checkPermi(['business:department:manage']) && checkPermi(['business:staff:manage']))
const lockedOrganization = ref(false)
const expandedIds = ref([])
const expansionInitialized = ref(false)
const expanded = computed(() => {
  const branches=flatten(tableRows.value,[]).filter(row=>row.children?.length)
  return branches.length>0 && branches.every(row=>expandedIds.value.includes(organizationKey(row)))
})
const formRef = ref()
const rows = ref([])
const tableRows = computed(() => withDepartmentHeaders(rows.value))
const staffRows = ref([])
const assignableStaff = computed(() => staffRows.value.filter(person=>!person.protectedAccount && person.status==='0' && person.employmentStatus!=='LEFT' && String(person.deptId)!==String(assignDepartment.value?.deptId)))
const selectedAssignStaff = computed(() => assignableStaff.value.filter(person=>assignUserIds.value.includes(person.userId)))
const selectedEmployee = computed(() => staffRows.value.find(person=>person.userId===selectedEmployeeId.value))
const employeeDetailSections = computed(() => {
  const person=selectedEmployee.value
  if (!person) return []
  return [
    {title:'基本资料',fields:[['员工编号',person.employeeNo],['性别',({0:'男',1:'女',2:'未知'})[person.sex]],['国家/地区',({CN:'中国',VN:'越南'})[person.countryRegion] || person.countryRegion],['手机号',person.phonenumber ? `${person.phoneCountryCode || ''} ${person.phonenumber}`.trim() : ''],['邮箱',person.email]]},
    {title:'组织与任职',fields:[['所属公司',person.companyName || (person.deptId===firstRootId() ? '集团层级' : '')],['所属部门',person.deptName],['岗位名称',person.positionName],['直属负责人',person.managerName],['用工类型',({FULL_TIME:'全职',PART_TIME:'兼职',CONTRACTOR:'外包',INTERN:'实习'})[person.employmentType] || person.employmentType],['任职状态',({PROBATION:'试用期',ACTIVE:'在职',ON_LEAVE:'休假',LEFT:'离职'})[person.employmentStatus] || person.employmentStatus],['入职日期',person.hireDate],['工作地点',person.workLocation]]},
    {title:'系统账号',fields:[['登录账号',person.userName],['账号类型',person.accountType],['系统角色',person.roleNames],['最后登录',person.loginDate || '暂无登录记录'],['备注',person.remark]]}
  ]
})
const selectedOrganization = ref()
const parentOptions = ref([])
const query = reactive({deptName:'',status:''})
const form = reactive({})
const leaderOptions = computed(() => [...staffRows.value].sort((a,b)=>Number(a.status!=='0')-Number(b.status!=='0') || String(a.deptName||'').localeCompare(String(b.deptName||'')) || displayName(a).localeCompare(displayName(b))))
const selectedPeople = computed(() => selectedOrganization.value ? peopleFor(selectedOrganization.value) : [])
const directPeopleCount = computed(() => selectedPeople.value.filter(person=>person.deptId===selectedOrganization.value?.deptId).length)
const dialogTitle = computed(() => `${form.deptId ? '编辑' : '新增'}${lockedOrganization.value ? '公司' : '部门'}`)
const rules = {
  parentId:[{required:true,message:'请选择上级部门',trigger:'change'}],
  deptName:[{required:true,message:'请输入部门名称',trigger:'blur'}],
  orderNum:[{required:true,message:'请输入显示顺序',trigger:'blur'}],
}

async function load() {
  loading.value=true
  try {
    const [departmentResult, staffResult]=await Promise.all([listBusinessDepartments(query),listBusinessDepartmentStaff()])
    rows.value=departmentResult.data || []
    if (!expansionInitialized.value && rows.value.length) {
      expandedIds.value=rows.value.filter(isRoot).map(organizationKey)
      expansionInitialized.value=true
    }
    staffRows.value=staffResult.data || []
  } finally { loading.value=false }
}
function resetSearch() { Object.assign(query,{deptName:'',status:''}); load() }
function resetForm(data) { Object.keys(form).forEach(key=>delete form[key]); Object.assign(form,data) }
function rootRow() { return rows.value.find(row=>row.parentId===0) }
function firstRootId() { return rootRow()?.deptId }
function isRoot(row) { return row?.parentId===0 }
function isCompany(row) { return !isRoot(row) && row?.parentId===firstRootId() }
function isProtected(row) { return isRoot(row) || isCompany(row) }
function peopleFor(row) {
  if (!row) return []
  const target=String(row.deptId)
  return staffRows.value.filter(person=>person.deptId===row.deptId || String(person.deptAncestors||'').split(',').includes(target))
    .sort((a,b)=>(a.deptId===row.deptId ? -1 : 1)-(b.deptId===row.deptId ? -1 : 1) || String(a.deptName||'').localeCompare(String(b.deptName||'')) || a.userId-b.userId)
}
function openPeople(row) { selectedOrganization.value=row; peopleOpen.value=true }
function openEmployeeDetail(person) { selectedEmployeeId.value=person.userId; employeeDetailOpen.value=true }
async function removeEmployee(person) {
  if (removingStaffId.value!==null) return
  removingStaffId.value=person.userId
  try {
    await ElMessageBox.confirm(`确定将“${displayName(person)}”（${person.userName}）移出“${person.deptName}”吗？移出后归属“${person.companyName || '所属公司'}”，账号和资料保留，可重新加入其他部门。`,'移出部门',{type:'warning',confirmButtonText:'确认移出',cancelButtonText:'取消'})
    await removeBusinessDepartmentStaff(person.deptId,person.userId)
    ElMessage.success('员工已移出部门')
    await load()
  } catch {
    // Cancel leaves membership intact; request errors are displayed by the interceptor.
  } finally { removingStaffId.value=null }
}
async function openAssignStaff(row) {
  assignDepartment.value=row
  assignUserIds.value=[]
  assignStaffOpen.value=true
  loadingStaffOptions.value=true
  try { staffRows.value=(await listBusinessDepartmentStaff()).data || [] }
  catch { assignStaffOpen.value=false }
  finally { loadingStaffOptions.value=false }
}
async function saveAssignedStaff() {
  if (assigningStaff.value || !assignUserIds.value.length) return
  assigningStaff.value=true
  try {
    await assignBusinessDepartmentStaff(assignDepartment.value.deptId,assignUserIds.value)
    ElMessage.success('员工已加入部门')
    assignStaffOpen.value=false
    onExpandChange(assignDepartment.value,true)
    await load()
  } catch {
    // The request interceptor displays the error; preserve the selection for retry.
  } finally { assigningStaff.value=false }
}
function directStaffFor(row) { return staffRows.value.filter(person=>String(person.deptId)===String(row.deptId)).sort((a,b)=>a.userId-b.userId) }
function displayName(person) { return person?.nickName && !/^\?+$/.test(person.nickName) ? person.nickName : person?.userName || '未命名人员' }
function leaderLabel(person) { return `${displayName(person)} · ${person.userName}` }
function bindLeader(userId) {
  const person=staffRows.value.find(item=>item.userId===userId)
  Object.assign(form,person ? {leader:displayName(person),phone:person.phonenumber || '',email:person.email || ''} : {leader:'',phone:'',email:''})
}
function prune(nodes, excludedId) { return (nodes||[]).filter(node=>node.deptId!==excludedId).map(node=>({...node,children:prune(node.children,excludedId)})) }
function openCreate(parent) {
  lockedOrganization.value=isRoot(parent)
  resetForm({parentId:parent?.deptId || firstRootId(),deptName:'',orderNum:0,leaderUserId:null,leader:'',phone:'',email:'',status:'0'})
  parentOptions.value=prune(rows.value)
  dialogOpen.value=true; nextTick(()=>formRef.value?.clearValidate())
}
function openEdit(row) {
  lockedOrganization.value=isCompany(row)
  resetForm({deptId:row.deptId,parentId:row.parentId,deptName:row.deptName,orderNum:row.orderNum,leaderUserId:row.leaderUserId || null,leader:row.leader,phone:row.phone,email:row.email,status:row.status})
  parentOptions.value=prune(rows.value,row.deptId)
  dialogOpen.value=true; nextTick(()=>formRef.value?.clearValidate())
}
async function save() {
  await formRef.value.validate(); saving.value=true
  try { form.deptId ? await updateBusinessDepartment(form) : await addBusinessDepartment(form); ElMessage.success('保存成功'); dialogOpen.value=false; load() } finally { saving.value=false }
}
async function remove(row) {
  await ElMessageBox.confirm(`确定删除部门“${row.deptName}”吗？有人员或下级部门时系统会拒绝删除。`,'删除部门',{type:'warning'})
  await removeBusinessDepartment(row.deptId); ElMessage.success('删除成功'); load()
}
function flatten(nodes, result=[]) { for (const node of nodes||[]) { result.push(node); flatten(node.children,result) } return result }
function withDepartmentHeaders(nodes) {
  return (nodes||[]).map(node=>{
    const children=withDepartmentHeaders(node.children)
    if (!isRoot(node) && !isCompany(node)) {
      const employees=directStaffFor(node).map(person=>({
        rowKey:`employee-${node.deptId}-${person.userId}`,isEmployeeRow:true,person,status:person.status
      }))
      children.push(...employees)
      if (!children.length) children.push({rowKey:`empty-staff-${node.deptId}`,isEmptyStaffRow:true})
    }
    if (String(node.deptId)==='110' && children.length) {
      children.unshift({deptId:`department-header-${node.deptId}`,isDepartmentHeader:true})
    }
    return {...node,source:node,children}
  })
}
async function saveSort() {
  const list=flatten(rows.value,[]); savingSort.value=true
  try { await saveBusinessDepartmentSort({deptIds:list.map(x=>x.deptId).join(','),orderNums:list.map(x=>x.orderNum||0).join(',')}); ElMessage.success('排序已保存'); load() } finally { savingSort.value=false }
}
function onExpandChange(row, isExpanded) {
  const ids=new Set(expandedIds.value)
  const key=organizationKey(row)
  isExpanded ? ids.add(key) : ids.delete(key)
  expandedIds.value=[...ids]
}
// Element Plus stores tree node keys as strings, including numeric department IDs.
function organizationKey(row) { return row.rowKey || String(row.deptId) }
function toggleExpandAll() {
  expandedIds.value=expanded.value ? [] : flatten(tableRows.value,[]).filter(row=>row.children?.length).map(organizationKey)
}
load()
useBusinessRefreshOnReactivated(load)
</script>

<style scoped>
.assign-staff-copy{margin:0 0 18px;color:#65758b;line-height:1.6}
.employee-link{padding:0;border:0;background:none;text-align:left;font:inherit;color:var(--el-color-primary);cursor:pointer}.employee-link:hover b{text-decoration:underline}.employee-link:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:4px;border-radius:3px}.employee-link small{display:block;color:#8a95a3;margin-top:3px;font-size:12px}.employee-identity{display:flex;align-items:center;justify-content:space-between;padding:18px;background:#f3f7fb;border-radius:10px}.employee-identity h2{margin:0 0 6px;font-size:22px}.employee-identity span{color:#718096}.employee-detail-section{margin-top:24px}.employee-detail-section h3{font-size:16px;margin:0 0 14px}.employee-detail-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px;margin:0}.employee-detail-grid dt{font-size:13px;color:#8490a0;margin-bottom:6px}.employee-detail-grid dd{margin:0;color:#172033;overflow-wrap:anywhere}@media(max-width:480px){.employee-detail-grid{grid-template-columns:1fr}}
.department-page :deep(.employee-row td.el-table__cell){background:#f8fafc}.employee-label{display:inline-block;vertical-align:middle}.employee-label b{font-weight:500}.employee-label small{font-size:12px}.empty-staff{color:#8a95a3;font-size:13px}
.department-page :deep(.department-header-row td.el-table__cell){background:#edf3fa!important;border-top:1px solid #dce6f1;border-bottom:1px solid #dce6f1;padding-top:10px;padding-bottom:10px}.department-column-heading{color:#405773;font-size:13px;font-weight:600}
.organization-label{display:inline-block;vertical-align:middle}.organization-toggle{padding:0;border:0;background:none;color:inherit;font:inherit;font-weight:700;text-align:left;cursor:pointer}.organization-toggle:hover{color:var(--el-color-primary)}.organization-toggle:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:4px;border-radius:3px}.organization-toggle small{font-weight:400}
.department-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f5f8;color:#172033}.hero{display:flex;align-items:flex-end;justify-content:space-between;padding:25px 30px;border-radius:16px;background:linear-gradient(120deg,#17304d,#375a76);color:#fff}.eyebrow{font-size:11px;letter-spacing:.18em;color:#9ed7ee}.hero h1{margin:5px 0 4px;font-size:28px}.hero p{margin:0;color:#d1deea}.panel{margin-top:16px;padding:18px 20px;border:1px solid #e0e5eb;border-radius:14px;background:#fff}.search-panel{padding-bottom:0}.panel-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.panel-head h2{margin:0;font-size:18px}.panel-head p{margin:4px 0 0;color:#8490a0;font-size:13px}.el-table b,.el-table small{display:block}.el-table small{margin-top:3px;color:#8a95a3}.protected-copy{margin-left:8px;color:#9aa4b1;font-size:13px}.people-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-bottom:18px}.people-summary div{padding:14px;border:1px solid #e3e8ee;border-radius:10px;background:#f7f9fb}.people-summary b,.people-summary span{display:block}.people-summary b{font-size:22px;color:#173b5b}.people-summary span{margin-top:3px;color:#8490a0;font-size:12px}.leader-option{display:flex;align-items:center;justify-content:space-between;gap:18px}.leader-option span,.leader-option b,.leader-option small{display:block}.leader-option small{color:#9aa4b1;font-size:11px;line-height:1.1}.leader-option em{color:#8490a0;font-size:12px;font-style:normal}@media(max-width:760px){.department-page{padding:14px}.hero{align-items:flex-start;flex-direction:column;gap:16px;padding:22px}.hero .el-button{width:100%}.panel{padding:14px}.panel-head{align-items:flex-start;flex-direction:column;gap:12px}.search-panel :deep(.el-form-item){display:flex;margin-right:0}.search-panel :deep(.el-form-item__content){flex:1}.search-panel :deep(.el-input),.search-panel :deep(.el-select){width:100%!important}.people-summary{grid-template-columns:1fr}.el-dialog .el-col{max-width:100%;flex:0 0 100%}}
</style>
