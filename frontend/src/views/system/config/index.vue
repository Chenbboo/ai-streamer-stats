<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
         <el-form-item :label="$tr(&quot;参数名称&quot;)" prop="configName">
            <el-input
               v-model="queryParams.configName"
               :placeholder="$tr(&quot;请输入参数名称&quot;)"
               clearable
               style="width: 240px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item :label="$tr(&quot;参数键名&quot;)" prop="configKey">
            <el-input
               v-model="queryParams.configKey"
               :placeholder="$tr(&quot;请输入参数键名&quot;)"
               clearable
               style="width: 240px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item :label="$tr(&quot;系统内置&quot;)" prop="configType">
            <el-select v-model="queryParams.configType" :placeholder="$tr(&quot;系统内置&quot;)" clearable style="width: 240px">
               <el-option
                  v-for="dict in sys_yes_no"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
               />
            </el-select>
         </el-form-item>
         <el-form-item :label="$tr(&quot;创建时间&quot;)" style="width: 308px;">
            <el-date-picker
               v-model="dateRange"
               value-format="YYYY-MM-DD"
               type="daterange"
               range-separator="-"
               :start-placeholder="$tr(&quot;开始日期&quot;)"
               :end-placeholder="$tr(&quot;结束日期&quot;)"
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
               type="primary"
               plain
               icon="Plus"
               @click="handleAdd"
               v-hasPermi="['system:config:add']"
            >{{ $tr("新增") }}</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="success"
               plain
               icon="Edit"
               :disabled="single"
               @click="handleUpdate"
               v-hasPermi="['system:config:edit']"
            >{{ $tr("修改") }}</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="danger"
               plain
               icon="Delete"
               :disabled="multiple"
               @click="handleDelete"
               v-hasPermi="['system:config:remove']"
            >{{ $tr("删除") }}</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="warning"
               plain
               icon="Download"
               @click="handleExport"
               v-hasPermi="['system:config:export']"
            >{{ $tr("导出") }}</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="danger"
               plain
               icon="Refresh"
               @click="handleRefreshCache"
               v-hasPermi="['system:config:remove']"
            >{{ $tr("刷新缓存") }}</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="configList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column :label="$tr(&quot;参数主键&quot;)" align="center" prop="configId" />
         <el-table-column :label="$tr(&quot;参数名称&quot;)" align="center" prop="configName" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;参数键名&quot;)" align="center" prop="configKey" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;参数键值&quot;)" align="center" prop="configValue" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;系统内置&quot;)" align="center" prop="configType">
            <template #default="scope">
               <dict-tag :options="sys_yes_no" :value="scope.row.configType" />
            </template>
         </el-table-column>
         <el-table-column :label="$tr(&quot;备注&quot;)" align="center" prop="remark" :show-overflow-tooltip="true" />
         <el-table-column :label="$tr(&quot;创建时间&quot;)" align="center" prop="createTime" width="180">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column :label="$tr(&quot;操作&quot;)" align="center" width="150" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:config:edit']" >{{ $tr("修改") }}</el-button>
               <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:config:remove']">{{ $tr("删除") }}</el-button>
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

      <!-- 添加或修改参数配置对话框 -->
      <el-dialog :title="title" v-model="open" width="500px" append-to-body>
         <el-form ref="configRef" :model="form" :rules="rules" label-width="80px">
            <el-form-item :label="$tr(&quot;参数名称&quot;)" prop="configName">
               <el-input v-model="form.configName" :placeholder="$tr(&quot;请输入参数名称&quot;)" />
            </el-form-item>
            <el-form-item :label="$tr(&quot;参数键名&quot;)" prop="configKey">
               <el-input v-model="form.configKey" :placeholder="$tr(&quot;请输入参数键名&quot;)" />
            </el-form-item>
            <el-form-item :label="$tr(&quot;参数键值&quot;)" prop="configValue">
               <el-input v-model="form.configValue" type="textarea" :placeholder="$tr(&quot;请输入参数键值&quot;)" />
            </el-form-item>
            <el-form-item :label="$tr(&quot;系统内置&quot;)" prop="configType">
               <el-radio-group v-model="form.configType">
                  <el-radio
                     v-for="dict in sys_yes_no"
                     :key="dict.value"
                     :value="dict.value"
                  >{{ dict.label }}</el-radio>
               </el-radio-group>
            </el-form-item>
            <el-form-item :label="$tr(&quot;备注&quot;)" prop="remark">
               <el-input v-model="form.remark" type="textarea" :placeholder="$tr(&quot;请输入内容&quot;)" />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitForm">{{ $tr("确 定") }}</el-button>
               <el-button @click="cancel">{{ $tr("取 消") }}</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="Config">
import { translateText } from '@/locales/translate'

import { listConfig, getConfig, delConfig, addConfig, updateConfig, refreshCache } from "@/api/system/config"

const { proxy } = getCurrentInstance()
const { sys_yes_no } = useDict("sys_yes_no")

const configList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const dateRange = ref([])

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    configName: undefined,
    configKey: undefined,
    configType: undefined
  },
  rules: {
    configName: [{ required: true, message: translateText("参数名称不能为空"), trigger: "blur" }],
    configKey: [{ required: true, message: translateText("参数键名不能为空"), trigger: "blur" }],
    configValue: [{ required: true, message: translateText("参数键值不能为空"), trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询参数列表 */
function getList() {
  loading.value = true
  listConfig(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    configList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    configId: undefined,
    configName: undefined,
    configKey: undefined,
    configValue: undefined,
    configType: "Y",
    remark: undefined
  }
  proxy.resetForm("configRef")
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
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.configId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = translateText("添加参数")
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const configId = row.configId || ids.value
  getConfig(configId).then(response => {
    form.value = response.data
    open.value = true
    title.value = translateText("修改参数")
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["configRef"].validate(valid => {
    if (valid) {
      if (form.value.configId != undefined) {
        updateConfig(form.value).then(response => {
          proxy.$modal.msgSuccess(translateText("修改成功"))
          open.value = false
          getList()
        })
      } else {
        addConfig(form.value).then(response => {
          proxy.$modal.msgSuccess(translateText("新增成功"))
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const configIds = row.configId || ids.value
  proxy.$modal.confirm(translateText("是否确认删除参数编号为\"") + configIds + translateText("\"的数据项？")).then(function () {
    return delConfig(configIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess(translateText("删除成功"))
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("system/config/export", {
    ...queryParams.value
  }, `config_${new Date().getTime()}.xlsx`)
}

/** 刷新缓存按钮操作 */
function handleRefreshCache() {
  refreshCache().then(() => {
    proxy.$modal.msgSuccess(translateText("刷新缓存成功"))
  })
}

getList()
</script>
