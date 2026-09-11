<template>
  <el-empty v-if="!reports?.length" description="尚无进度汇报" />
  <el-collapse v-else v-model="expanded">
    <el-collapse-item v-for="report in reports" :key="report.reportId" :name="String(report.reportId)">
      <template #title><span class="report-title"><b>{{ report.projectNameSnapshot || '项目进度' }} · v{{ report.version }} · {{ report.progress }}%</b><span>{{ report.submittedUserName }} · {{ report.createTime }}</span></span></template>
      <article class="report">
        <el-button v-if="linkProject" link type="primary" @click="$emit('open-project', {projectId:report.projectId, reportId:report.reportId})">跳转子项目汇报</el-button>
        <h4>阶段成果</h4><p>{{ report.completionSummary }}</p>
        <h4>问题风险</h4><p>{{ report.issuesRisks || '未记录' }}</p>
        <h4>下一步计划</h4><p>{{ report.nextPlan || '未记录' }}</p>
        <p class="hint">同步内容：{{ report.syncTasks ? '一次性任务' : '' }} {{ report.syncRoutines ? '持续工作' : '' }}{{ !report.syncTasks && !report.syncRoutines ? '未勾选' : '' }}。汇报只读，纠正请提交新版本。</p>
        <ProgressSnapshot v-if="report.syncTasks || report.syncRoutines" :snapshot="readProgressSnapshot(report)" :show-tasks="!!report.syncTasks" :show-routines="!!report.syncRoutines" />
        <el-collapse><el-collapse-item title="查看完整关联数据快照（提交时归档）" name="snapshot"><ProgressSnapshot :snapshot="readProgressSnapshot(report)" archive-details /></el-collapse-item></el-collapse>
      </article>
    </el-collapse-item>
  </el-collapse>
</template>
<script setup>
import { ref, watch } from 'vue'
import { readProgressSnapshot } from '@/utils/projectProgress'
import ProgressSnapshot from './ProgressSnapshot.vue'
const props = defineProps({ reports: Array, selectedReportId: [Number, String], linkProject: Boolean })
defineEmits(['open-project'])
const expanded = ref([])
watch(() => props.selectedReportId, id => { if(id) expanded.value = [String(id)] }, { immediate: true })
</script>
<style scoped>.report-title{display:flex;flex-wrap:wrap;gap:8px 18px;padding:10px 8px 10px 0;line-height:1.6}.report-title>span,.hint{color:#8491a1;font-size:12px}.report{padding:8px 12px;background:#f8fafc;border-radius:8px}.report h4{margin:12px 0 5px}.report p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}</style>
