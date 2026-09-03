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
      <div class="panel-head"><div><h2>组织架构</h2><p>可直接调整显示顺序，保存后对人员选择立即生效</p></div><div><el-button icon="Sort" @click="expanded=!expanded">{{ expanded ? '折叠' : '展开' }}</el-button><el-button type="primary" plain :loading="savingSort" @click="saveSort">保存排序</el-button></div></div>
      <el-table v-if="refreshTable" :data="rows" v-loading="loading" row-key="deptId" :default-expand-all="expanded" :tree-props="{children:'children'}">
        <el-table-column prop="deptName" label="组织名称" min-width="240"><template #default="{row}"><b>{{ row.deptName }}</b><small v-if="isRoot(row)">集团根节点</small><small v-else-if="isCompany(row)">公司</small></template></el-table-column>
        <el-table-column label="人员" width="100" align="center"><template #default="{row}"><el-button link type="primary" @click="openPeople(row)">{{ peopleFor(row).length }} 人</el-button></template></el-table-column>
        <el-table-column label="排序" width="110"><template #default="{row}"><el-input-number v-model="row.orderNum" :min="0" controls-position="right" size="small" /></template></el-table-column>
        <el-table-column prop="leader" label="负责人" width="130"><template #default="{row}">{{ row.leader || '—' }}</template></el-table-column>
        <el-table-column prop="phone" label="联系电话" width="125"><template #default="{row}">{{ row.phone || '—' }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '正常' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right"><template #default="{row}">
          <el-button link type="primary" @click="openCreate(row)">{{ isRoot(row) ? '新增公司' : (isCompany(row) ? '新增部门' : '新增下级') }}</el-button>
          <el-button v-if="!isRoot(row)" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="!isProtected(row)" link type="danger" @click="remove(row)">删除</el-button>
          <span v-if="isProtected(row)" class="protected-copy">受保护</span>
        </template></el-table-column>
      </el-table>
    </section>

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
        <el-table-column label="人员" min-width="150"><template #default="{row}"><b>{{ displayName(row) }}</b><small v-if="displayName(row)!==row.userName">{{ row.userName }}</small></template></el-table-column>
        <el-table-column prop="deptName" label="所属组织" min-width="145"><template #default="{row}">{{ row.deptName || '未设置' }}<small>{{ row.deptId===selectedOrganization?.deptId ? '直属' : '下级组织' }}</small></template></el-table-column>
        <el-table-column prop="roleNames" label="角色" min-width="150"><template #default="{row}">{{ row.roleNames || row.accountType || '员工' }}</template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.status==='0' ? 'success' : 'info'">{{ row.status==='0' ? '正常' : '停用' }}</el-tag></template></el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup name="BusinessDepartment">
import { ElMessage, ElMessageBox } from 'element-plus'
import { addBusinessDepartment, listBusinessDepartments, listBusinessDepartmentStaff, removeBusinessDepartment, saveBusinessDepartmentSort, updateBusinessDepartment } from '@/api/business/department'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'

const loading = ref(false)
const saving = ref(false)
const savingSort = ref(false)
const dialogOpen = ref(false)
const peopleOpen = ref(false)
const lockedOrganization = ref(false)
const expanded = ref(true)
const refreshTable = ref(true)
const formRef = ref()
const rows = ref([])
const staffRows = ref([])
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
async function saveSort() {
  const list=flatten(rows.value,[]); savingSort.value=true
  try { await saveBusinessDepartmentSort({deptIds:list.map(x=>x.deptId).join(','),orderNums:list.map(x=>x.orderNum||0).join(',')}); ElMessage.success('排序已保存'); load() } finally { savingSort.value=false }
}
watch(expanded,()=>{refreshTable.value=false;nextTick(()=>refreshTable.value=true)})
load()
useBusinessRefreshOnReactivated(load)
</script>

<style scoped>
.department-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f5f8;color:#172033}.hero{display:flex;align-items:flex-end;justify-content:space-between;padding:25px 30px;border-radius:16px;background:linear-gradient(120deg,#17304d,#375a76);color:#fff}.eyebrow{font-size:11px;letter-spacing:.18em;color:#9ed7ee}.hero h1{margin:5px 0 4px;font-size:28px}.hero p{margin:0;color:#d1deea}.panel{margin-top:16px;padding:18px 20px;border:1px solid #e0e5eb;border-radius:14px;background:#fff}.search-panel{padding-bottom:0}.panel-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.panel-head h2{margin:0;font-size:18px}.panel-head p{margin:4px 0 0;color:#8490a0;font-size:13px}.el-table b,.el-table small{display:block}.el-table small{margin-top:3px;color:#8a95a3}.protected-copy{margin-left:8px;color:#9aa4b1;font-size:13px}.people-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-bottom:18px}.people-summary div{padding:14px;border:1px solid #e3e8ee;border-radius:10px;background:#f7f9fb}.people-summary b,.people-summary span{display:block}.people-summary b{font-size:22px;color:#173b5b}.people-summary span{margin-top:3px;color:#8490a0;font-size:12px}.leader-option{display:flex;align-items:center;justify-content:space-between;gap:18px}.leader-option span,.leader-option b,.leader-option small{display:block}.leader-option small{color:#9aa4b1;font-size:11px;line-height:1.1}.leader-option em{color:#8490a0;font-size:12px;font-style:normal}@media(max-width:760px){.department-page{padding:14px}.hero{align-items:flex-start;flex-direction:column;gap:16px;padding:22px}.hero .el-button{width:100%}.panel{padding:14px}.panel-head{align-items:flex-start;flex-direction:column;gap:12px}.search-panel :deep(.el-form-item){display:flex;margin-right:0}.search-panel :deep(.el-form-item__content){flex:1}.search-panel :deep(.el-input),.search-panel :deep(.el-select){width:100%!important}.people-summary{grid-template-columns:1fr}.el-dialog .el-col{max-width:100%;flex:0 0 100%}}
</style>
