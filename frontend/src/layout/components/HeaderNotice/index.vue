<template>
  <div>
    <el-popover ref="noticePopover" placement="bottom-end" :width="320" trigger="click" v-model:visible="noticeVisible" popper-class="notice-popover">
      <!-- 弹出内容 -->
      <div class="notice-header">
        <span class="notice-title">{{ $tr("通知公告") }}</span>
        <span class="notice-mark-all" @click="markAllRead">{{ $tr("全部已读") }}</span>
      </div>
      <div v-if="noticeLoading" class="notice-loading">
        <el-icon class="is-loading"><Loading /></el-icon>{{ $tr(" 加载中... ") }}</div>
      <div v-else-if="noticeList.length === 0" class="notice-empty">
        <el-icon style="font-size:24px;display:block;margin-bottom:6px;"><Postcard /></el-icon>{{ $tr(" 暂无公告 ") }}</div>
      <div v-else>
        <div v-for="item in noticeList" :key="item.noticeId" class="notice-item" :class="{ 'is-read': item.isRead }" @click="previewNotice(item)">
          <el-tag size="small" :type="item.noticeType === '1' ? 'warning' : 'success'" class="notice-tag">
            {{ item.noticeType === '1' ? $tr("通知") : $tr("公告") }}
          </el-tag>
          <span class="notice-item-title">{{ item.noticeTitle }}</span>
          <span class="notice-item-date">{{ item.createTime }}</span>
        </div>
      </div>

<div class="notice-header"><span class="notice-title">{{ $tr("子项目进度汇报") }}</span><span>{{ $tr("{0} 条未读", [progressNotices.filter(n=>!n.readTime).length]) }}</span></div>
      <div v-if="progressError" class="notice-empty">{{ $tr("汇报通知加载失败 ") }}<el-button link @click="loadProgress">{{ $tr("重试") }}</el-button></div>
      <div v-else-if="!progressNotices.length" class="notice-empty">{{ $tr("暂无进度汇报通知") }}</div>
      <div class="progress-notices"><button v-for="item in progressNotices" :key="item.notificationId" class="notice-item progress-notice" :class="{'is-read':item.readTime}" @click="openProgress(item)"><span class="notice-item-title">{{ item.projectName }} · {{ item.progress }}%<small>{{ item.reporterName }} · {{ parseTime(item.createTime) }}</small></span><el-tag v-if="!item.readTime" size="small">{{ $tr("未读") }}</el-tag></button></div>
      <div v-if="deletionNotices.length || deletionError" class="notice-header"><span class="notice-title">{{ $tr("项目删除审核结果") }}</span><span>{{ $tr("{0} 条未读", [deletionUnreadCount]) }}</span></div>
      <div v-if="deletionError" class="notice-empty">{{ $tr("审核通知加载失败 ") }}<el-button link @click="loadDeletion">{{ $tr("重试") }}</el-button></div>
      <div class="progress-notices"><button v-for="item in deletionNotices" :key="item.notificationId" class="notice-item progress-notice" :class="{'is-read':item.readTime}" @click="openDeletion(item)"><span class="notice-item-title">{{ item.projectName }} · {{ item.status === 'APPROVED' ? $tr("删除申请已通过") : $tr("删除申请已驳回") }}<small>{{ item.reviewerName || $tr("审核人") }} · {{ parseTime(item.reviewTime || item.createTime) }}</small></span><el-tag v-if="!item.readTime" size="small">{{ $tr("未读") }}</el-tag></button></div>
      <div v-if="workReportNotices.length || workReportError" class="notice-header"><span class="notice-title">{{ $tr("工作汇报退回") }}</span><span>{{ $tr("{0} 条未读", [workReportUnreadCount]) }}</span></div>
      <div v-if="workReportError" class="notice-empty">{{ $tr("汇报通知加载失败 ") }}<el-button link @click="loadWorkReportNotices">{{ $tr("重试") }}</el-button></div>
      <div class="progress-notices"><button v-for="item in workReportNotices" :key="item.notificationId" class="notice-item progress-notice" :class="{'is-read':item.readTime}" @click="openWorkReportNotice(item)"><span class="notice-item-title">{{ item.projectName }} · {{ item.routineId ? item.routineName : $tr("项目工作汇报") }}<small>{{ $tr("工作汇报已退回") }} · {{ parseTime(item.reviewedTime || item.createTime) }}</small></span><el-tag v-if="!item.readTime" size="small">{{ $tr("未读") }}</el-tag></button></div>
      <!-- 触发器 -->
      <template #reference>
        <div class="right-menu-item hover-effect notice-trigger">
          <svg-icon icon-class="bell" />
          <span v-if="unreadCount + progressNotices.filter(n=>!n.readTime).length + deletionUnreadCount + workReportUnreadCount > 0" class="notice-badge">{{ unreadCount + progressNotices.filter(n=>!n.readTime).length + deletionUnreadCount + workReportUnreadCount }}</span>
        </div>
      </template>
    </el-popover>

    <!-- 预览弹窗 -->
    <notice-detail-view ref="noticeViewRef" />
  </div>
</template>

<script setup>
import { translateText } from '@/locales/translate'

import { getProgressNotifications, readProgressNotification, getBusinessProjectDeletionNotifications, readBusinessProjectDeletionNotification, readAllBusinessProjectDeletionNotifications, getBusinessWorkReportReturnNotifications, readBusinessWorkReportReturnNotification, readAllBusinessWorkReportReturnNotifications } from '@/api/business/project'
import { parseTime } from '@/utils/ruoyi'
import { useRouter } from 'vue-router'
import { h } from 'vue'
import { ElMessageBox } from 'element-plus'
import NoticeDetailView from './DetailView'
import { listNoticeTop, markNoticeRead, markNoticeReadAll } from '@/api/system/notice'

const router = useRouter(), progressNotices = ref([]), progressError = ref(false)
const deletionNotices = ref([]), deletionError = ref(false)
const deletionUnreadCount = computed(() => deletionNotices.value.filter(item => !item.readTime).length)
const workReportNotices = ref([]), workReportError = ref(false)
const workReportUnreadCount = computed(() => workReportNotices.value.filter(item => !item.readTime).length)
let progressTimer
async function loadProgress(){try{const res=await getProgressNotifications();progressNotices.value=res.data||[];progressError.value=false}catch{progressError.value=true}}
async function loadDeletion(){try{const res=await getBusinessProjectDeletionNotifications();deletionNotices.value=res.data||[];deletionError.value=false}catch{deletionError.value=true}}
async function loadWorkReportNotices(){try{const res=await getBusinessWorkReportReturnNotifications();workReportNotices.value=res.data||[];workReportError.value=false}catch{workReportError.value=true}}
async function openProgress(item){noticeVisible.value=false;await router.push({path:'/business/projects',query:{progressProjectId:item.projectId,reportId:item.reportId}});if(!item.readTime){try{await readProgressNotification(item.notificationId);item.readTime=new Date().toISOString()}catch{/* Keep unread state when acknowledgement fails. */}}}
async function openDeletion(item){
  noticeVisible.value=false
  const lines=[
    translateText("项目：{0}", [item.projectName]),
    translateText("结果：{0}", [item.status==='APPROVED'?translateText("审核通过，项目已删除"):translateText("审核驳回，项目保留")]),
    translateText("审核人：{0}", [item.reviewerName || '—']),
    translateText("申请原因：{0}", [item.reason || '—']),
    translateText("审核说明：{0}", [item.reviewComment || translateText("无")])
  ]
  try {
    await ElMessageBox.alert(h('div', lines.map(line => h('p', { style: { margin: '0 0 8px' } }, line))), translateText("项目删除审核结果"), { confirmButtonText:translateText("知道了") })
    if(!item.readTime){await readBusinessProjectDeletionNotification(item.notificationId);item.readTime=new Date().toISOString()}
  } catch {/* Keep unread state if the dialog or acknowledgement is closed or fails. */}
}
async function openWorkReportNotice(item){
  noticeVisible.value=false
  const lines=[
    translateText("项目：{0}", [item.projectName || '—']),
    translateText("汇报事项：{0}", [item.routineId ? item.routineName : translateText("项目工作汇报")]),
    translateText("验收人：{0}", [item.reviewerName || '—']),
    translateText("退回原因：{0}", [item.reviewComment || '—'])
  ]
  try{
    await ElMessageBox.alert(h('div', lines.map(line=>h('p', {style:{margin:'0 0 8px',whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}, line))), translateText("工作汇报已退回"), {confirmButtonText:translateText("前往我的安排")})
  }catch{return}
  if(!item.readTime){try{await readBusinessWorkReportReturnNotification(item.notificationId);item.readTime=new Date().toISOString()}catch{/* Keep unread state when acknowledgement fails. */}}
  await router.push({path:'/business/work-schedule',query:{projectId:item.projectId}})
}
onMounted(()=>{loadProgress();loadDeletion();loadWorkReportNotices();progressTimer=setInterval(()=>{loadProgress();loadDeletion();loadWorkReportNotices()},60000)})
onBeforeUnmount(()=>clearInterval(progressTimer))
const noticePopover = ref(null)
const noticeList = ref([])
const unreadCount = ref(0)
const noticeLoading = ref(false)
const noticeVisible = ref(false)
const { proxy } = getCurrentInstance()

// 加载顶部公告列表
function loadNoticeTop() {
  noticeLoading.value = true
  listNoticeTop().then(res => {
    noticeList.value = res.data || []
    unreadCount.value = res.unreadCount !== undefined ? res.unreadCount : noticeList.value.filter(n => !n.isRead).length
  }).finally(() => {
    noticeLoading.value = false
  })
}

onMounted(() => loadNoticeTop())
watch(noticeVisible, shown => { if(shown){loadProgress();loadDeletion();loadWorkReportNotices()} })

// 预览公告详情
function previewNotice(item) {
  if (!item.isRead) {
    markNoticeRead(item.noticeId).catch(() => {})
    const idx = noticeList.value.indexOf(item)
    if (idx !== -1) noticeList.value[idx] = { ...item, isRead: true }
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  proxy.$refs["noticeViewRef"].open(item.noticeId)
}

// 全部已读
function markAllRead() {
  const ids = noticeList.value.map(n => n.noticeId).join(',')
  if (ids) {
    markNoticeReadAll(ids).catch(() => {})
    noticeList.value = noticeList.value.map(n => ({ ...n, isRead: true }))
    unreadCount.value = 0
  }
  if (deletionUnreadCount.value) readAllBusinessProjectDeletionNotifications().then(() => {
    deletionNotices.value = deletionNotices.value.map(item => ({ ...item, readTime: item.readTime || new Date().toISOString() }))
  }).catch(() => {})
  if (workReportUnreadCount.value) readAllBusinessWorkReportReturnNotifications().then(() => {
    workReportNotices.value = workReportNotices.value.map(item => ({ ...item, readTime: item.readTime || new Date().toISOString() }))
  }).catch(() => {})
}
</script>

<style lang="scss" scoped>
.progress-notices{max-height:320px;overflow:auto}.progress-notice{width:100%;background:transparent;border:0;text-align:left}.progress-notice small{display:block;margin-top:5px;color:#8793a1}
.notice-trigger {
  position: relative;
  transform: translateX(-6px);
  .svg-icon { width: 1.2em; height: 1.2em; vertical-align: -0.2em; }
  .notice-badge {
    position: absolute;
    top: 7px;
    right: -3px;
    background: #f56c6c;
    color: #fff;
    border-radius: 10px;
    font-size: 10px;
    height: 16px;
    line-height: 16px;
    padding: 0 4px;
    min-width: 16px;
    text-align: center;
    white-space: nowrap;
    pointer-events: none;
  }
}
.notice-popover { padding: 0 !important; }
.notice-popover .notice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f7f9fb;
  border-bottom: 1px solid #eee;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}
.notice-popover .notice-mark-all {
  font-size: 12px;
  color: var(--el-color-primary);
  font-weight: normal;
  cursor: pointer;
}
.notice-popover .notice-mark-all:hover { color: #2b7cc1; }
.notice-popover .notice-loading,
.notice-popover .notice-empty {
  padding: 24px;
  text-align: center;
  color: #bbb;
  font-size: 12px;
  line-height: 1.8;
}
.notice-popover .notice-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.15s;
}
.notice-popover .notice-item:last-child { border-bottom: none; }
.notice-popover .notice-item:hover { background: #f7f9fb; }
.notice-popover .notice-item.is-read .notice-tag,
.notice-popover .notice-item.is-read .notice-item-title,
.notice-popover .notice-item.is-read .notice-item-date { opacity: 0.45; filter: grayscale(1); color: #999; }
.notice-popover .notice-tag { flex-shrink: 0; }
.notice-popover .notice-item-title {
  flex: 1;
  font-size: 12px;
  color: #333;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.notice-popover .notice-item-date {
  flex-shrink: 0;
  font-size: 11px;
  color: #bbb;
}
</style>
