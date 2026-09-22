<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
         <el-form-item :label="$tr(&quot;操作地址&quot;)" prop="operIp">
            <el-input
               v-model="queryParams.operIp"
               :placeholder="$tr(&quot;请输入操作地址&quot;)"
               clearable
               style="width: 240px;"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item :label="$tr(&quot;系统模块&quot;)" prop="title">
            <el-input
               v-model="queryParams.title"
               :placeholder="$tr(&quot;请输入系统模块&quot;)"
               clearable
               style="width: 240px;"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item :label="$tr(&quot;操作人员&quot;)" prop="operName">
            <el-input
               v-model="queryParams.operName"
               :placeholder="$tr(&quot;请输入操作人员&quot;)"
               clearable
               style="width: 240px;"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item :label="$tr(&quot;类型&quot;)" prop="businessType">
            <el-select
               v-model="queryParams.businessType"
               :placeholder="$tr(&quot;操作类型&quot;)"
               clearable
               style="width: 240px"
            >
               <el-option
                  v-for="dict in sys_oper_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
               />
            </el-select>
         </el-form-item>
         <el-form-item :label="$tr(&quot;状态&quot;)" prop="status">
            <el-select
               v-model="queryParams.status"
               :placeholder="$tr(&quot;操作状态&quot;)"
               clearable
               style="width: 240px"
            >
               <el-option
                  v-for="dict in sys_common_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
               />
            </el-select>
         </el-form-item>
         <el-form-item :label="$tr(&quot;操作时间&quot;)" style="width: 308px">
            <el-date-picker
               v-model="dateRange"
               value-format="YYYY-MM-DD HH:mm:ss"
               type="daterange"
               range-separator="-"
               :start-placeholder="$tr(&quot;开始日期&quot;)"
               :end-placeholder="$tr(&quot;结束日期&quot;)"
               :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
            ></el-date-picker>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">{{ $tr("搜索") }}</el-button>
            <el-button icon="Refresh" @click="resetQuery">{{ $tr("重置") }}</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <el-col :span="1.5">
            <el-button
               type="danger"
               plain
               icon="Delete"
               :disabled="multiple"
               @click="handleDelete"
               v-hasPermi="['monitor:operlog:remove']"
            >{{ $tr("删除") }}</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="danger"
               plain
               icon="Delete"
               @click="handleClean"
               v-hasPermi="['monitor:operlog:remove']"
            >{{ $tr("清空") }}</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="warning"
               plain
               icon="Download"
               @click="handleExport"
               v-hasPermi="['monitor:operlog:export']"
            >{{ $tr("导出") }}</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table ref="operlogRef" v-loading="loading" :data="operlogList" @selection-change="handleSelectionChange" :default-sort="defaultSort" @sort-change="handleSortChange">
         <el-table-column type="selection" width="50" align="center" />
         <el-table-column :label="$tr(&quot;日志编号&quot;)" align="center" prop="operId" />
         <el-table-column :label="$tr(&quot;系统模块&quot;)" align="center" prop="title" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;操作类型&quot;)" align="center" prop="businessType">
            <template #default="scope">
               <dict-tag :options="sys_oper_type" :value="scope.row.businessType" />
            </template>
         </el-table-column>
         <el-table-column :label="$tr(&quot;操作人员&quot;)" align="center" width="110" prop="operName" :show-overflow-tooltip="true" sortable="custom" :sort-orders="['descending', 'ascending']" />
         <el-table-column :label="$tr(&quot;操作地址&quot;)" align="center" prop="operIp" width="130" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;操作状态&quot;)" align="center" prop="status">
            <template #default="scope">
               <dict-tag :options="sys_common_status" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column :label="$tr(&quot;操作日期&quot;)" align="center" prop="operTime" width="180" sortable="custom" :sort-orders="['descending', 'ascending']">
            <template #default="scope">
               <span>{{ parseTime(scope.row.operTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tr(&quot;消耗时间&quot;)" align="center" prop="costTime" width="110" :show-overflow-tooltip="true" sortable="custom" :sort-orders="['descending', 'ascending']">
            <template #default="scope">
               <span>{{ $tr("{0}毫秒", [scope.row.costTime]) }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tr(&quot;操作&quot;)" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="View" @click="handleDetail(scope.row, scope.index)" v-hasPermi="['monitor:operlog:query']">{{ $tr("详细") }}</el-button>
            </template>
         </el-table-column>
      </el-table>

      <pagination
         v-show="total > 0"
         :total="total"
         v-model:page="queryParams.pageNum"
         v-model:limit="queryParams.pageSize"
         @pagination="getList"
      />

      <operlog-detail v-model:visible="detailVisible" :row="detailRow" />
   </div>
</template>

<script setup name="Operlog">
import { translateText } from '@/locales/translate'

import OperlogDetail from './detail'
import { list, delOperlog, cleanOperlog } from "@/api/monitor/operlog"

const { proxy } = getCurrentInstance()
const { sys_oper_type, sys_common_status } = useDict("sys_oper_type", "sys_common_status")

const operlogList = ref([])
const detailVisible = ref(false)
const loading = ref(true)
const detailRow = ref({})
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const dateRange = ref([])
const defaultSort = ref({ prop: "operTime", order: "descending" })

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    operIp: undefined,
    title: undefined,
    operName: undefined,
    businessType: undefined,
    status: undefined
  }
})

const { queryParams, form } = toRefs(data)

/** 查询登录日志 */
function getList() {
  loading.value = true
  list(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    operlogList.value = response.rows
    total.value = response.total
    loading.value = false
  })
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
  queryParams.value.pageNum = 1
  proxy.$refs["operlogRef"].sort(defaultSort.value.prop, defaultSort.value.order)
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.operId)
  multiple.value = !selection.length
}

/** 排序触发事件 */
function handleSortChange(column, prop, order) {
  queryParams.value.orderByColumn = column.prop
  queryParams.value.isAsc = column.order
  getList()
}

/** 详细按钮操作 */
function handleDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

/** 删除按钮操作 */
function handleDelete(row) {
  const operIds = row.operId || ids.value
  proxy.$modal.confirm(translateText("是否确认删除日志编号为\"") + operIds + translateText("\"的数据项?")).then(function () {
    return delOperlog(operIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess(translateText("删除成功"))
  }).catch(() => {})
}

/** 清空按钮操作 */
function handleClean() {
  proxy.$modal.confirm(translateText("是否确认清空所有操作日志数据项?")).then(function () {
    return cleanOperlog()
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess(translateText("清空成功"))
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("monitor/operlog/export",{
    ...queryParams.value,
  }, `config_${new Date().getTime()}.xlsx`)
}

getList()
</script>
