<template>
  <div class="app-container tree-sidebar-manage-wrap">
    <tree-panel :title="$tr(&quot;组织机构&quot;)" :tree-data="deptOptions" :search-placeholder="$tr(&quot;请输入部门名称&quot;)" storage-key="dept-sidebar-width" :defaultExpandAll="true" @node-click="handleNodeClick" @refresh="getDeptTree" ref="deptTreeRef" />
    <div class="tree-sidebar-content">
      <div class="content-inner">
        <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
          <el-form-item :label="$tr(&quot;用户名称&quot;)" prop="userName">
            <el-input v-model="queryParams.userName" :placeholder="$tr(&quot;请输入用户名称&quot;)" clearable style="width: 240px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item :label="$tr(&quot;手机号码&quot;)" prop="phonenumber">
            <el-input v-model="queryParams.phonenumber" :placeholder="$tr(&quot;请输入手机号码&quot;)" clearable style="width: 240px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item :label="$tr(&quot;状态&quot;)" prop="status">
            <el-select v-model="queryParams.status" :placeholder="$tr(&quot;用户状态&quot;)" clearable style="width: 240px">
              <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$tr(&quot;创建时间&quot;)" style="width: 308px">
            <el-date-picker v-model="dateRange" value-format="YYYY-MM-DD" type="daterange" range-separator="-" :start-placeholder="$tr(&quot;开始日期&quot;)" :end-placeholder="$tr(&quot;结束日期&quot;)"></el-date-picker>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">{{ $tr("搜索") }}</el-button>
            <el-button icon="Refresh" @click="resetQuery">{{ $tr("重置") }}</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['system:user:add']">{{ $tr("新增") }}</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['system:user:edit']">{{ $tr("修改") }}</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['system:user:remove']">{{ $tr("删除") }}</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="info" plain icon="Upload" @click="handleImport" v-hasPermi="['system:user:import']">{{ $tr("导入") }}</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['system:user:export']">{{ $tr("导出") }}</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" :columns="columns" storageKey="xxxxxxxx"></right-toolbar>
        </el-row>

        <el-table v-loading="loading" :data="userList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column :label="$tr(&quot;用户编号&quot;)" align="center" key="userId" prop="userId" v-if="columns.userId.visible" />
          <el-table-column :label="$tr(&quot;用户名称&quot;)" align="center" key="userName" v-if="columns.userName.visible" :show-overflow-tooltip="true">
            <template #default="scope">
              <a class="link-type" style="cursor:pointer" @click="handleViewData(scope.row)">{{ scope.row.userName }}</a>
            </template>
         </el-table-column>
          <el-table-column :label="$tr(&quot;用户昵称&quot;)" align="center" key="nickName" prop="nickName" v-if="columns.nickName.visible" :show-overflow-tooltip="true" />
          <el-table-column :label="$tr(&quot;部门&quot;)" align="center" key="deptName" prop="dept.deptName" v-if="columns.deptName.visible" :show-overflow-tooltip="true" />
          <el-table-column :label="$tr(&quot;手机号码&quot;)" align="center" key="phonenumber" prop="phonenumber" v-if="columns.phonenumber.visible" width="120" />
          <el-table-column :label="$tr(&quot;状态&quot;)" align="center" key="status" v-if="columns.status.visible">
            <template #default="scope">
              <el-switch
                v-model="scope.row.status"
                active-value="0"
                inactive-value="1"
                @change="handleStatusChange(scope.row)"
              ></el-switch>
            </template>
          </el-table-column>
          <el-table-column :label="$tr(&quot;创建时间&quot;)" align="center" prop="createTime" v-if="columns.createTime.visible" width="160">
            <template #default="scope">
              <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$tr(&quot;操作&quot;)" align="center" width="150" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-tooltip :content="$tr(&quot;修改&quot;)" placement="top" v-if="scope.row.userId !== 1">
                <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:user:edit']"></el-button>
              </el-tooltip>
              <el-tooltip :content="$tr(&quot;删除&quot;)" placement="top" v-if="scope.row.userId !== 1">
                <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:user:remove']"></el-button>
              </el-tooltip>
              <el-tooltip :content="$tr(&quot;重置密码&quot;)" placement="top" v-if="scope.row.userId !== 1">
                <el-button link type="primary" icon="Key" @click="handleResetPwd(scope.row)" v-hasPermi="['system:user:resetPwd']"></el-button>
              </el-tooltip>
              <el-tooltip :content="$tr(&quot;分配角色&quot;)" placement="top" v-if="scope.row.userId !== 1">
                <el-button link type="primary" icon="CircleCheck" @click="handleAuthRole(scope.row)" v-hasPermi="['system:user:edit']"></el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
      </div>
    </div>

    <!-- 添加或修改用户配置对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form :model="form" :rules="rules" ref="userRef" label-width="80px">
        <el-row>
          <el-col :span="12">
            <el-form-item :label="$tr(&quot;用户昵称&quot;)" prop="nickName">
              <el-input v-model="form.nickName" :placeholder="$tr(&quot;请输入用户昵称&quot;)" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tr(&quot;归属部门&quot;)" prop="deptId">
              <el-tree-select v-model="form.deptId" :data="enabledDeptOptions" :props="{ value: 'id', label: 'label', children: 'children' }" value-key="id" :placeholder="$tr(&quot;请选择归属部门&quot;)" clearable check-strictly />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item :label="$tr(&quot;手机号码&quot;)" prop="phonenumber">
              <el-input v-model="form.phonenumber" :placeholder="$tr(&quot;请输入手机号码&quot;)" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tr(&quot;邮箱&quot;)" prop="email">
              <el-input v-model="form.email" :placeholder="$tr(&quot;请输入邮箱&quot;)" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item v-if="form.userId == undefined" :label="$tr(&quot;用户名称&quot;)" prop="userName">
              <el-input v-model="form.userName" :placeholder="$tr(&quot;请输入用户名称&quot;)" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="form.userId == undefined" :label="$tr(&quot;用户密码&quot;)" prop="password" :rules="pwdValidator">
              <el-input v-model="form.password" :placeholder="$tr(&quot;请输入用户密码&quot;)" type="password" maxlength="20" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item :label="$tr(&quot;用户性别&quot;)">
              <el-select v-model="form.sex" :placeholder="$tr(&quot;请选择&quot;)">
                <el-option v-for="dict in sys_user_sex" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tr(&quot;状态&quot;)">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item :label="$tr(&quot;岗位&quot;)">
              <el-select v-model="form.postIds" multiple :placeholder="$tr(&quot;请选择&quot;)">
                <el-option v-for="item in postOptions" :key="item.postId" :label="item.postName" :value="item.postId" :disabled="item.status == 1"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tr(&quot;角色&quot;)">
              <el-select v-model="form.roleIds" multiple :placeholder="$tr(&quot;请选择&quot;)">
                <el-option v-for="item in roleOptions" :key="item.roleId" :label="$tr(item.roleName)" :value="item.roleId" :disabled="item.status == 1"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item :label="$tr(&quot;备注&quot;)">
              <el-input v-model="form.remark" type="textarea" :placeholder="$tr(&quot;请输入内容&quot;)"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">{{ $tr("确 定") }}</el-button>
          <el-button @click="cancel">{{ $tr("取 消") }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 用户详情抽屉 -->
    <user-view-drawer ref="userViewRef" />
    <!-- 用户导入对话框 -->
    <excel-import-dialog ref="importUserRef" :title="$tr(&quot;用户导入&quot;)" action="/system/user/importData" template-action="/system/user/importTemplate" template-file-name="user_template" :update-support-label="$tr(&quot;是否更新已经存在的用户数据&quot;)" @success="getList" />
  </div>
</template>

<script setup name="User">
import { translateText } from '@/locales/translate'

import TreePanel from "@/components/TreePanel"
import ExcelImportDialog from "@/components/ExcelImportDialog"
import UserViewDrawer from "./view"
import { usePasswordRule } from "@/utils/passwordRule"
import { changeUserStatus, listUser, resetUserPwd, delUser, getUser, updateUser, addUser, deptTreeSelect } from "@/api/system/user"

const router = useRouter()
const { proxy } = getCurrentInstance()
const { pwdValidator, pwdPromptValidator } = usePasswordRule()
const { sys_normal_disable, sys_user_sex } = useDict("sys_normal_disable", "sys_user_sex")

const userList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const dateRange = ref([])
const deptOptions = ref(undefined)
const enabledDeptOptions = ref(undefined)
const initPassword = ref(undefined)
const postOptions = ref([])
const roleOptions = ref([])
// 列显隐信息
const columns = ref({
  userId: { label: translateText("用户编号"), visible: true },
  userName: { label: translateText("用户名称"), visible: true },
  nickName: { label: translateText("用户昵称"), visible: true },
  deptName: { label: translateText("部门"), visible: true },
  phonenumber: { label: translateText("手机号码"), visible: true },
  status: { label: translateText("状态"), visible: true },
  createTime: { label: translateText("创建时间"), visible: true }
})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    userName: undefined,
    phonenumber: undefined,
    status: undefined,
    deptId: undefined
  },
  rules: {
    userName: [{ required: true, message: translateText("用户名称不能为空"), trigger: "blur" }, { min: 2, max: 20, message: translateText("用户名称长度必须介于 2 和 20 之间"), trigger: "blur" }],
    nickName: [{ required: true, message: translateText("用户昵称不能为空"), trigger: "blur" }],
    email: [{ type: "email", message: translateText("请输入正确的邮箱地址"), trigger: ["blur", "change"] }],
    phonenumber: [{ pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/, message: translateText("请输入正确的手机号码"), trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询用户列表 */
function getList() {
  loading.value = true
  listUser(proxy.addDateRange(queryParams.value, dateRange.value)).then(res => {
    loading.value = false
    userList.value = res.rows
    total.value = res.total
  })
}

/** 查询部门下拉树结构 */
function getDeptTree() {
  deptTreeSelect().then(response => {
    deptOptions.value = response.data
    enabledDeptOptions.value = filterDisabledDept(JSON.parse(JSON.stringify(response.data)))
  })
}

/** 过滤禁用的部门 */
function filterDisabledDept(deptList) {
  return deptList.filter(dept => {
    if (dept.disabled) {
      return false
    }
    if (dept.children && dept.children.length) {
      dept.children = filterDisabledDept(dept.children)
    }
    return true
  })
}

/** 节点单击事件 */
function handleNodeClick(data) {
  queryParams.value.deptId = data.id
  handleQuery()
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm("queryRef")
  queryParams.value.deptId = undefined
  proxy.$refs.deptTreeRef.setCurrentKey(null)
  handleQuery()
}

/** 删除按钮操作 */
function handleDelete(row) {
  const userIds = row.userId || ids.value
  proxy.$modal.confirm(translateText("是否确认删除用户编号为\"") + userIds + translateText("\"的数据项？")).then(function () {
    return delUser(userIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess(translateText("删除成功"))
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("system/user/export", {
    ...queryParams.value,
  },`user_${new Date().getTime()}.xlsx`)
}

/** 用户状态修改  */
function handleStatusChange(row) {
  let text = row.status === "0" ? translateText("启用") : translateText("停用")
  proxy.$modal.confirm(translateText("确认要\"") + text + '""' + row.userName + translateText("\"用户吗?")).then(function () {
    return changeUserStatus(row.userId, row.status)
  }).then(() => {
    proxy.$modal.msgSuccess(text + translateText("成功"))
  }).catch(function () {
    row.status = row.status === "0" ? "1" : "0"
  })
}

/** 更多操作 */
function handleCommand(command, row) {
  switch (command) {
    case "handleResetPwd":
      handleResetPwd(row)
      break
    case "handleAuthRole":
      handleAuthRole(row)
      break
    default:
      break
  }
}

/** 跳转角色分配 */
function handleAuthRole(row) {
  const userId = row.userId
  router.push("/system/user-auth/role/" + userId)
}

/** 重置密码按钮操作 */
function handleResetPwd(row) {
  proxy.$prompt(translateText("请输入「{0}」的新密码", [row.userName]), translateText("重置密码"), {
    confirmButtonText: translateText("确定"),
    cancelButtonText: translateText("取消"),
    closeOnClickModal: false,
    inputValidator: pwdPromptValidator
  }).then(({ value }) => {
    resetUserPwd(row.userId, value).then(() => {
      proxy.$modal.msgSuccess(translateText("修改成功，新密码是：") + value)
    })
  }).catch(() => {})
}

/** 选择条数  */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.userId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 详情按钮操作 */
function handleViewData(row) {
  proxy.$refs["userViewRef"].open(row.userId)
}

/** 导入按钮操作 */
function handleImport() {
  proxy.$refs["importUserRef"].open()
}

/** 重置操作表单 */
function reset() {
  form.value = {
    userId: undefined,
    deptId: undefined,
    userName: undefined,
    nickName: undefined,
    password: undefined,
    phonenumber: undefined,
    email: undefined,
    sex: undefined,
    status: "0",
    remark: undefined,
    postIds: [],
    roleIds: []
  }
  proxy.resetForm("userRef")
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  getUser().then(response => {
    postOptions.value = response.posts
    roleOptions.value = response.roles
    open.value = true
    title.value = translateText("添加用户")
    form.value.password = initPassword.value
  })
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const userId = row.userId || ids.value
  getUser(userId).then(response => {
    form.value = response.data
    postOptions.value = response.posts
    roleOptions.value = response.roles
    form.value.postIds = response.postIds
    form.value.roleIds = response.roleIds
    open.value = true
    title.value = translateText("修改用户")
    form.value.password = ""
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["userRef"].validate(valid => {
    if (valid) {
      if (form.value.userId != undefined) {
        updateUser(form.value).then(() => {
          proxy.$modal.msgSuccess(translateText("修改成功"))
          open.value = false
          getList()
        })
      } else {
        addUser(form.value).then(() => {
          proxy.$modal.msgSuccess(translateText("新增成功"))
          open.value = false
          getList()
        })
      }
    }
  })
}

onMounted(() => {
  getDeptTree()
  getList()
  proxy.getConfigKey("sys.user.initPassword").then(response => {
    initPassword.value = response.msg
  })
})
</script>
