<template>
  <div class="app-container">
    <el-form :model="query" inline class="query-form">
      <el-form-item :label="$t('streamer.stageName')">
        <el-input v-model="query.stageName" :placeholder="$t('streamer.stageNamePlaceholder')" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item :label="$t('common.status')">
        <el-select v-model="query.status" :placeholder="$t('common.all')" clearable style="width: 120px">
          <el-option :label="$t('streamer.active')" value="0" />
          <el-option :label="$t('streamer.inactive')" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">{{ $t('common.search') }}</el-button>
        <el-button icon="Refresh" @click="resetQuery">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['live:streamer:add']">{{ $t('streamer.addStreamer') }}</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="streamerList">
      <el-table-column label="ID" prop="streamerId" width="80" />
      <el-table-column :label="$t('streamer.stageName')" prop="stageName" min-width="150" />
      <el-table-column label="TikTok" prop="tiktokHandle" min-width="150" />
      <el-table-column :label="$t('common.status')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? $t('streamer.active') : $t('streamer.inactive') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.createTime')" prop="createTime" width="180" />
      <el-table-column :label="$t('streamer.remark')" prop="remark" min-width="150" show-overflow-tooltip />
      <el-table-column :label="$t('common.action')" width="200" align="center">
        <template #default="{ row }">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(row)" v-hasPermi="['live:streamer:edit']">{{ $t('common.edit') }}</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(row)" v-if="row.status === '0'" v-hasPermi="['live:streamer:remove']">{{ $t('common.delete') }}</el-button>
          <el-button link type="success" icon="Check" @click="handleEnable(row)" v-else v-hasPermi="['live:streamer:edit']">{{ $t('streamer.enable') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="loadList" />

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.open" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="$t('streamer.stageName')" prop="stageName">
          <el-input v-model="form.stageName" :placeholder="$t('streamer.stageNameExample')" />
        </el-form-item>
        <el-form-item label="TikTok" prop="tiktokHandle">
          <el-input v-model="form.tiktokHandle" :placeholder="$t('streamer.tiktokExample')" />
        </el-form-item>
        <el-form-item :label="$t('streamer.password')" prop="password" v-if="!form.streamerId">
          <el-input v-model="form.password" type="password" :placeholder="$t('streamer.passwordPlaceholder')" show-password />
          <div class="el-form-item__tip">{{ $t('streamer.passwordTip') }}</div>
        </el-form-item>
        <el-form-item :label="$t('streamer.remark')" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" :placeholder="$t('streamer.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.open = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LiveStreamer">
import { listStreamers, addStreamer, updateStreamer, delStreamer } from '@/api/live/streamer'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const streamerList = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  stageName: undefined,
  status: undefined
})

const dialog = reactive({
  open: false,
  title: ''
})

const form = reactive({
  streamerId: undefined,
  stageName: '',
  tiktokHandle: '',
  password: '',
  remark: ''
})

const rules = {
  stageName: [{ required: true, message: '请输入主播名称', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }]
}

async function loadList() {
  loading.value = true
  try {
    const res = await listStreamers(query)
    streamerList.value = res.rows
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  query.pageNum = 1
  loadList()
}

function resetQuery() {
  query.stageName = undefined
  query.status = undefined
  handleQuery()
}

function handleAdd() {
  dialog.title = '新增主播'
  dialog.open = true
  form.streamerId = undefined
  form.stageName = ''
  form.tiktokHandle = ''
  form.password = ''
  form.remark = ''
}

function handleUpdate(row) {
  dialog.title = '修改主播'
  dialog.open = true
  form.streamerId = row.streamerId
  form.stageName = row.stageName
  form.tiktokHandle = row.tiktokHandle
  form.password = ''
  form.remark = row.remark
}

async function submitForm() {
  try {
    if (form.streamerId) {
      await updateStreamer(form)
      proxy.$modal.msgSuccess('修改成功')
    } else {
      await addStreamer(form)
      proxy.$modal.msgSuccess('新增成功')
    }
    dialog.open = false
    loadList()
  } catch (e) {}
}

function handleDelete(row) {
  proxy.$modal.confirm('确认将主播"' + row.stageName + '"设为离职吗？').then(() => {
    return delStreamer(row.streamerId)
  }).then(() => {
    proxy.$modal.msgSuccess('操作成功')
    loadList()
  }).catch(() => {})
}

function handleEnable(row) {
  updateStreamer({ streamerId: row.streamerId, status: '0' }).then(() => {
    proxy.$modal.msgSuccess('启用成功')
    loadList()
  })
}

loadList()
</script>

<style scoped>
.query-form {
  margin-bottom: 16px;
}
.mb8 {
  margin-bottom: 8px;
}
.el-form-item__tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
</style>
