<template>
  <!-- 导入表 -->
  <el-dialog :title="$tr(&quot;导入表&quot;)" v-model="visible" width="800px" top="5vh" append-to-body>
    <el-form :model="queryParams" ref="queryRef" :inline="true">
      <el-form-item :label="$tr(&quot;表名称&quot;)" prop="tableName">
        <el-input
          v-model="queryParams.tableName"
          :placeholder="$tr(&quot;请输入表名称&quot;)"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item :label="$tr(&quot;表描述&quot;)" prop="tableComment">
        <el-input
          v-model="queryParams.tableComment"
          :placeholder="$tr(&quot;请输入表描述&quot;)"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">{{ $tr("搜索") }}</el-button>
        <el-button icon="Refresh" @click="resetQuery">{{ $tr("重置") }}</el-button>
      </el-form-item>
    </el-form>
    <el-row>
      <el-table @row-click="clickRow" ref="table" :data="dbTableList" @selection-change="handleSelectionChange" height="260px">
        <el-table-column type="selection" width="55"></el-table-column>
        <el-table-column prop="tableName" :label="$tr(&quot;表名称&quot;)" :show-overflow-tooltip="true"></el-table-column>
        <el-table-column prop="tableComment" :label="$tr(&quot;表描述&quot;)" :show-overflow-tooltip="true"></el-table-column>
        <el-table-column prop="createTime" :label="$tr(&quot;创建时间&quot;)"></el-table-column>
        <el-table-column prop="updateTime" :label="$tr(&quot;更新时间&quot;)"></el-table-column>
      </el-table>
      <pagination
        v-show="total>0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-row>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handleImportTable">{{ $tr("确 定") }}</el-button>
        <el-button @click="visible = false">{{ $tr("取 消") }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { translateText } from '@/locales/translate'

import { listDbTable, importTable } from "@/api/tool/gen"

const total = ref(0)
const visible = ref(false)
const tables = ref([])
const dbTableList = ref([])
const { proxy } = getCurrentInstance()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  tableName: undefined,
  tableComment: undefined
})

const emit = defineEmits(["ok"])

/** 查询参数列表 */
function show() {
  getList()
  visible.value = true
}

/** 单击选择行 */
function clickRow(row) {
  proxy.$refs.table.toggleRowSelection(row)
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  tables.value = selection.map(item => item.tableName)
}

/** 查询表数据 */
function getList() {
  listDbTable(queryParams).then(res => {
    dbTableList.value = res.rows
    total.value = res.total
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 导入按钮操作 */
function handleImportTable() {
  const tableNames = tables.value.join(",")
  if (tableNames == "") {
    proxy.$modal.msgError(translateText("请选择要导入的表"))
    return
  }
  importTable({ tables: tableNames, tplWebType: 'element-plus' }).then(res => {
    proxy.$modal.msgSuccess(res.msg)
    if (res.code === 200) {
      visible.value = false
      emit("ok")
    }
  })
}

defineExpose({
  show,
})
</script>
