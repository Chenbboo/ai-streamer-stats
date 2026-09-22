<template>
  <el-dialog v-model="visible" :title="$tr(&quot;「{0}」已读用户&quot;, [noticeTitle])" width="760px" top="6vh" append-to-body @close="handleClose">
    <el-form ref="queryRef" :model="queryParams" size="small" :inline="true" style="margin-bottom: 4px;">
      <el-form-item prop="searchValue">
        <el-input
          v-model="queryParams.searchValue"
          :placeholder="$tr(&quot;登录名称 / 用户名称&quot;)"
          clearable
          :prefix-icon="Search"
          style="width: 220px;"
          @keyup.enter="handleQuery"
          @clear="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" size="small" @click="handleQuery">{{ $tr("搜索") }}</el-button>
        <el-button icon="Refresh" size="small" @click="resetQuery">{{ $tr("重置") }}</el-button>
      </el-form-item>
      <el-form-item style="float: right; margin-right: 0;">
        <span class="read-stat">{{ $tr(" 共 ") }}<strong>{{ total }}</strong>{{ $tr(" 人已读 ") }}</span>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="userList" size="small" stripe height="340px">
      <el-table-column type="index" :label="$tr(&quot;序号&quot;)" width="55" align="center" />
      <el-table-column :label="$tr(&quot;登录名称&quot;)" prop="userName" align="center" :show-overflow-tooltip="true" />
      <el-table-column :label="$tr(&quot;用户名称&quot;)" prop="nickName" align="center" :show-overflow-tooltip="true" />
      <el-table-column :label="$tr(&quot;所属部门&quot;)" prop="deptName" align="center" :show-overflow-tooltip="true" />
      <el-table-column :label="$tr(&quot;手机号码&quot;)" prop="phonenumber" align="center" width="120" />
      <el-table-column :label="$tr(&quot;阅读时间&quot;)" prop="readTime" align="center" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.readTime) }}</span>
        </template>
      </el-table-column>
    </el-table>
    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
      style="padding: 6px 0px;"
    />
  </el-dialog>
</template>

<script setup name="ReadUsers">
import { Search } from "@element-plus/icons-vue"
import { listNoticeReadUsers } from "@/api/system/notice"

const { proxy } = getCurrentInstance()

const visible = ref(false)
const loading = ref(false)
const noticeTitle = ref("")
const total = ref(0)
const userList = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  noticeId: undefined,
  searchValue: undefined
})

function open(row) {
  queryParams.noticeId = row.noticeId
  noticeTitle.value = row.noticeTitle
  queryParams.searchValue = undefined
  queryParams.pageNum = 1
  visible.value = true
  getList()
}

function getList() {
  loading.value = true
  listNoticeReadUsers(queryParams).then(res => {
    userList.value = res.rows
    total.value = res.total
  }).finally(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleClose() {
  userList.value = []
  total.value = 0
  queryParams.searchValue = undefined
}

defineExpose({
  open
})
</script>

<style scoped>
.read-stat {
  font-size: 13px;
  color: #606266;
  line-height: 28px;
}
.read-stat strong {
  color: #409eff;
  font-size: 15px;
  margin: 0 2px;
}
</style>
