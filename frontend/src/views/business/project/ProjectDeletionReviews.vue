<template>
  <el-card v-if="requests.length" shadow="never" class="review-card">
    <template #header>
      <div class="review-head"><strong>{{ $tr("项目删除申请 · 管理员或老板审核") }}</strong><el-button link type="primary" :loading="loading" @click="refresh">{{ $tr("刷新") }}</el-button></div>
    </template>
    <el-alert v-if="loadError" :title="$tr(&quot;删除申请读取失败，请重试&quot;)" type="error" :closable="false" show-icon />
    <el-table v-else :data="requests" v-loading="loading">
      <el-table-column prop="projectName" :label="$tr(&quot;项目&quot;)" min-width="180" />
      <el-table-column prop="requestUserName" :label="$tr(&quot;申请人&quot;)" width="110" />
      <el-table-column prop="reason" :label="$tr(&quot;删除原因&quot;)" min-width="220" show-overflow-tooltip />
      <el-table-column prop="requestTime" :label="$tr(&quot;申请时间&quot;)" width="170" />
      <el-table-column :label="$tr(&quot;状态&quot;)" width="110"><template #default><el-tag type="warning">{{ $tr("待审核") }}</el-tag></template></el-table-column>
      <el-table-column :label="$tr(&quot;审核&quot;)" min-width="200"><template #default="{ row }"><el-button link type="danger" :loading="reviewing===row.requestId" @click="review(row,'APPROVED')">{{ $tr("通过并删除") }}</el-button><el-button link :loading="reviewing===row.requestId" @click="review(row,'REJECTED')">{{ $tr("驳回") }}</el-button></template></el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { translateText } from '@/locales/translate'

import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listBusinessProjectDeletionRequests, reviewBusinessProjectDeletion } from '@/api/business/project'

const emit = defineEmits(['reviewed'])
const requests = ref([]), loading = ref(false), loadError = ref(false), reviewing = ref(null)
async function refresh() {
  loading.value = true; loadError.value = false
  try { requests.value = ((await listBusinessProjectDeletionRequests()).data || []).filter(row => row.status === 'PENDING') }
  catch { loadError.value = true }
  finally { loading.value = false }
}
async function review(row, decision) {
  let comment = ''
  try {
    if (decision === 'APPROVED') await ElMessageBox.confirm(translateText("确认通过“{0}”的删除申请？项目将立即从项目中心移除，历史记录保留。", [row.projectName]), translateText("审核删除项目"), { type: 'warning', confirmButtonText: translateText("通过并删除") })
    else {
      const answer = await ElMessageBox.prompt(translateText("请填写驳回“{0}”删除申请的原因", [row.projectName]), translateText("驳回删除申请"), {
        inputType: 'textarea', inputValidator: value => value?.trim() && value.trim().length <= 500 ? true : translateText("请填写驳回原因，且不超过500字")
      })
      comment = answer.value.trim()
    }
  } catch { return }
  reviewing.value = row.requestId
  try {
    await reviewBusinessProjectDeletion(row.requestId, { decision, comment })
    ElMessage.success(decision === 'APPROVED' ? translateText("已审核通过，项目已删除") : translateText("删除申请已驳回"))
    await refresh()
    emit('reviewed', row, decision)
  } catch { /* The request handler displays the server error. */ }
  finally { reviewing.value = null }
}
onMounted(refresh)
const poll = setInterval(() => { if (!loading.value && !reviewing.value) refresh() }, 30000)
onUnmounted(() => clearInterval(poll))
defineExpose({ refresh })
</script>

<style scoped>
.review-card{margin-bottom:16px}.review-head{display:flex;align-items:center;justify-content:space-between}
</style>
