<template>
  <div class="app-container my-page">
    <header class="welcome">
      <div><span>MY WORKSPACE</span><h1>{{ userStore.nickName || userStore.name }}，今天先处理这些事</h1><p>只展示你参与的项目和分配给你的未完成任务。</p></div>
      <el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </header>
    <section class="quick-grid">
      <article><b>{{ summary.totalCount || 0 }}</b><span>参与项目</span></article><article><b>{{ summary.activeCount || 0 }}</b><span>执行中</span></article><article class="warn"><b>{{ summary.overdueProjectCount || 0 }}</b><span>存在逾期</span></article><article class="risk"><b>{{ summary.highRiskProjectCount || 0 }}</b><span>高风险项目</span></article>
    </section>
    <div class="content-grid">
      <section class="panel">
        <div class="panel-head"><div><h2>我的待办任务</h2><p>按截止时间优先排序</p></div></div>
        <div v-if="!tasks.length && !loading" class="empty">当前没有未完成任务</div>
        <button v-for="task in tasks" :key="task.taskId" class="task-row" @click="openProject(task.projectId)">
          <span class="priority" :class="`p-${task.priority?.toLowerCase()}`"></span><span class="task-main"><b>{{ task.taskName }}</b><small>{{ task.projectName }} · {{ task.projectNo }}</small></span><span class="task-state">{{ taskStatusLabel[task.status] || task.status }}<small :class="{ overdue:isOverdue(task.dueDate) }">{{ task.dueDate || '未设期限' }}</small></span>
        </button>
      </section>
      <section class="panel">
        <div class="panel-head"><div><h2>参与的项目</h2><p>最近更新的项目</p></div><el-button link type="primary" @click="router.push('/business/projects')">全部</el-button></div>
        <button v-for="project in projects" :key="project.projectId" class="project-card" @click="openProject(project.projectId)">
          <span class="project-top"><b>{{ project.projectName }}</b><el-tag size="small" :type="statusTone[project.status] || 'info'">{{ projectStatusLabel(project) }}</el-tag></span><small>{{ project.mainOwnerName }} · {{ project.planEndDate ? `计划至 ${project.planEndDate}` : '未设结束日期' }}</small><el-progress :percentage="progress(project)" :stroke-width="6" />
        </button>
        <div v-if="!projects.length && !loading" class="empty">尚未加入项目</div>
      </section>
    </div>
  </div>
</template>

<script setup name="BusinessMy">
import useUserStore from '@/store/modules/user'
import { getMyBusinessDashboard } from '@/api/business/project'
const router=useRouter(), userStore=useUserStore(), loading=ref(false), summary=ref({}),projects=ref([]),tasks=ref([])
const statusLabel={DRAFT:'草稿',PLANNING:'规划中',ACTIVE:'执行中',PAUSED:'已暂停',ACCEPTANCE:'待验收',CLOSED:'已关闭',CANCELED:'已取消'}
const statusTone={DRAFT:'info',PLANNING:'warning',ACTIVE:'primary',PAUSED:'info',ACCEPTANCE:'success',CLOSED:'success',CANCELED:'danger'}
const projectStatusLabel=project=>project?.status==='ACCEPTANCE'&&project?.closeMethod==='STAGED_ACCEPTANCE'?'待结项':statusLabel[project?.status]||project?.status
const taskStatusLabel={TODO:'待开始',DOING:'进行中',BLOCKED:'受阻'}
const progress=p=>p.taskCount?Math.round((p.completedTaskCount||0)*100/p.taskCount):0
const isOverdue=date=>date&&date<new Date().toISOString().slice(0,10)
const openProject=id=>router.push({path:'/business/projects',query:{id}})
async function load(){loading.value=true;try{const{data={}}=await getMyBusinessDashboard();summary.value=data.summary||{};projects.value=data.projects||[];tasks.value=data.tasks||[]}finally{loading.value=false}}
load()
</script>

<style scoped>
.my-page{min-height:calc(100vh - 84px);padding:24px;background:#f4f6f8;color:#172033}.welcome{display:flex;align-items:center;justify-content:space-between;padding:24px 28px;border-radius:15px;background:linear-gradient(125deg,#eff7ff,#f2fbf8);border:1px solid #dce8ef}.welcome span{font-size:11px;letter-spacing:.17em;color:#3778b8}.welcome h1{margin:5px 0;font-size:25px}.welcome p{margin:0;color:#748294}.quick-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:16px 0}.quick-grid article{padding:18px 20px;border:1px solid #e0e5eb;border-radius:12px;background:#fff}.quick-grid b{display:block;font-size:28px}.quick-grid span{color:#7d8998}.quick-grid .warn b{color:#d58227}.quick-grid .risk b{color:#c84851}.content-grid{display:grid;grid-template-columns:1.2fr .8fr;gap:14px}.panel{overflow:hidden;padding:18px;border:1px solid #e0e5eb;border-radius:13px;background:#fff}.panel-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.panel h2{margin:0;font-size:17px}.panel p{margin:3px 0;color:#8994a2;font-size:12px}.task-row,.project-card{display:flex;width:100%;align-items:center;padding:13px 8px;border:0;border-top:1px solid #edf0f3;background:#fff;text-align:left;cursor:pointer}.task-row:hover,.project-card:hover{background:#f7fafc}.priority{width:8px;height:8px;margin-right:12px;border-radius:50%;background:#789}.p-high{background:#d44b54}.p-medium{background:#db912e}.p-low{background:#47917c}.task-main{display:flex;min-width:0;flex:1;flex-direction:column}.task-main b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.task-main small,.task-state small,.project-card>small{margin-top:4px;color:#8994a2}.task-state{display:flex;align-items:flex-end;flex-direction:column;color:#536174;font-size:13px}.task-state small.overdue{color:#d7474f}.project-card{align-items:stretch;flex-direction:column;gap:7px}.project-top{display:flex;align-items:center;justify-content:space-between}.empty{padding:38px 0;text-align:center;color:#9aa4b1}@media(max-width:900px){.content-grid{grid-template-columns:1fr}.quick-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:620px){.my-page{padding:14px}.welcome{align-items:flex-start;flex-direction:column;gap:14px;padding:20px}.welcome .el-button{width:100%}.quick-grid{gap:8px}}
</style>
