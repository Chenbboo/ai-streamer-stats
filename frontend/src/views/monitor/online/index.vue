<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true">
         <el-form-item :label="$tr(&quot;登录地址&quot;)" prop="ipaddr">
            <el-input
               v-model="queryParams.ipaddr"
               :placeholder="$tr(&quot;请输入登录地址&quot;)"
               clearable
               style="width: 200px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item :label="$tr(&quot;用户名称&quot;)" prop="userName">
            <el-input
               v-model="queryParams.userName"
               :placeholder="$tr(&quot;请输入用户名称&quot;)"
               clearable
               style="width: 200px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">{{ $tr("搜索") }}</el-button>
            <el-button icon="Refresh" @click="resetQuery">{{ $tr("重置") }}</el-button>
         </el-form-item>
      </el-form>
      <el-table
         v-loading="loading"
         :data="onlineList.slice((pageNum - 1) * pageSize, pageNum * pageSize)"
         style="width: 100%;"
      >
         <el-table-column :label="$tr(&quot;序号&quot;)" width="50" type="index" align="center">
            <template #default="scope">
               <span>{{ (pageNum - 1) * pageSize + scope.$index + 1 }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tr(&quot;会话编号&quot;)" align="center" prop="tokenId" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;登录名称&quot;)" align="center" prop="userName" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;所属部门&quot;)" align="center" prop="deptName" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;主机&quot;)" align="center" prop="ipaddr" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;登录地点&quot;)" align="center" prop="loginLocation" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;操作系统&quot;)" align="center" prop="os" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;浏览器&quot;)" align="center" prop="browser" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;登录时间&quot;)" align="center" prop="loginTime" width="180">
            <template #default="scope">
               <span>{{ parseTime(scope.row.loginTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tr(&quot;操作&quot;)" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Delete" @click="handleForceLogout(scope.row)" v-hasPermi="['monitor:online:forceLogout']">{{ $tr("强退") }}</el-button>
            </template>
         </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="pageNum" v-model:limit="pageSize" />
   </div>
</template>

<script setup name="Online">
import { translateText } from '@/locales/translate'

import { forceLogout, list as initData } from "@/api/monitor/online"

const { proxy } = getCurrentInstance()

const onlineList = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const queryParams = ref({
  ipaddr: undefined,
  userName: undefined
})

/** 查询登录日志列表 */
function getList() {
  loading.value = true
  initData(queryParams.value).then(response => {
    onlineList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  pageNum.value = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 强退按钮操作 */
function handleForceLogout(row) {
  proxy.$modal.confirm(translateText("是否确认强退名称为\"") + row.userName + translateText("\"的用户?")).then(function () {
    return forceLogout(row.tokenId)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess(translateText("删除成功"))
  }).catch(() => {})
}

getList()
</script>
