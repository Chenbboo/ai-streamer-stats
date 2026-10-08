<template>
  <div class="monthly-progress">
    <el-progress :percentage="barPercent" :format="formatProgress" :show-text="showText" :stroke-width="strokeWidth" :status="status" />
    <small class="progress-scale">0–300%</small>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { monthlyProgressPercent, projectProgressBarPercent, projectProgressLimit } from '@/utils/projectProgress'
const props = defineProps({
  project: { type: Object, required: true },
  value: { type: Number, default: null },
  showText: { type: Boolean, default: true },
  strokeWidth: { type: Number, default: 8 },
  status: String
})
const actualProgress = computed(() => Math.min(projectProgressLimit(props.project), Math.max(0, props.value ?? monthlyProgressPercent(props.project) ?? 0)))
const barPercent = computed(() => projectProgressBarPercent(props.project, actualProgress.value))
const formatProgress = () => `${actualProgress.value}%`
</script>

<style scoped>
.monthly-progress{width:100%;min-width:0}.progress-scale{display:block;margin-top:3px;color:#909399;font-size:11px;text-align:right}
</style>
