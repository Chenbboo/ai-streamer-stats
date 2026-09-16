<template>
  <el-button v-if="companies.length" icon="Setting" @click="visible = true">公司管理授权</el-button>
  <el-dialog v-model="visible" title="公司管理授权" width="820px" append-to-body>
    <p>按公司分别授权。项目负责人、申请人和实际经办人保持独立；修改后立即生效。</p>
    <el-select v-model="companyId" placeholder="选择公司" @change="loadGrants">
      <el-option v-for="company in companies" :key="company.companyDeptId" :value="company.companyDeptId" :label="company.companyName" />
    </el-select>
    <el-table :data="users" v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="userName" label="老板" width="120" />
      <el-table-column label="已授权功能"><template #default="{ row }">{{ (grant(row).capabilities || '').split(',').filter(Boolean).map(key => labels[key]).join('、') || '未授权' }}</template></el-table-column>
      <el-table-column width="90"><template #default="{ row }"><el-button link type="primary" :disabled="!companyId || (!admin && String(row.userId) === String(user.id))" @click="edit(row)">设置</el-button></template></el-table-column>
    </el-table>
    <el-form v-if="selected" label-width="90px" style="margin-top:20px">
      <el-form-item label="授权给">{{ selected.userName }}</el-form-item>
      <el-form-item label="功能权限"><el-checkbox-group v-model="form.capabilities"><el-checkbox v-for="(label, key) in labels" :key="key" :value="key" :label="key" :disabled="!admin && !ownCapabilities.includes(key)">{{ label }}</el-checkbox></el-checkbox-group></el-form-item>
      <el-form-item label="变更原因"><el-input v-model="form.reason" maxlength="500" show-word-limit /></el-form-item>
      <el-form-item><el-button type="primary" :loading="saving" @click="save">保存授权</el-button><el-button @click="selected = null">取消</el-button></el-form-item>
    </el-form>
  </el-dialog>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
const user = useUserStore()
const admin = computed(() => user.roles.includes('admin'))
const labels = { BUSINESS: '项目与经营管理', STAFF: '人员与部门', COST_READ: '成本查看', COST_WRITE: '成本修改', ATTENDANCE_READ: '考勤查询', INTEGRATION: '飞书配置', CUTOVER: '验收与来源切换', AUTHORIZE: '公司授权管理' }
const visible = ref(false), companies = ref([]), users = ref([]), rows = ref([]), companyId = ref(), selected = ref(null), loading = ref(false), saving = ref(false)
const form = reactive({ capabilities: [], version: 0, reason: '' })
let requestSequence = 0
const ownCapabilities = computed(() => (companies.value.find(c => c.companyDeptId === companyId.value)?.capabilities || '').split(','))
function grant(row) { return rows.value.find(g => String(g.userId) === String(row.userId)) || {} }
async function loadGrants() {
  const sequence = ++requestSequence
  selected.value = null; rows.value = []; loading.value = true
  try {
    const result = await request({ url: `/business/company-access/${companyId.value}` })
    if (sequence === requestSequence) rows.value = result.data || []
  } finally { if (sequence === requestSequence) loading.value = false }
}
function edit(row) { selected.value = row; Object.assign(form, { capabilities: (grant(row).capabilities || '').split(',').filter(Boolean), version: grant(row).version || 0, reason: '' }) }
async function save() {
  if (!form.reason.trim()) return ElMessage.warning('请填写变更原因')
  saving.value = true
  try { await request({ url: `/business/company-access/${companyId.value}/${selected.value.userId}`, method: 'put', data: form }); ElMessage.success('公司授权已更新'); await loadGrants() } finally { saving.value = false }
}
onMounted(async () => { const { data } = await request({ url: '/business/company-access' }); companies.value = data.companies || []; users.value = data.users || []; if (companies.value.length) { companyId.value = companies.value[0].companyDeptId; await loadGrants() } })
</script>
