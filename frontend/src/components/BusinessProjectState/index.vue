<template>
  <div class="business-project-state">
    <el-tag :type="project.status === 'CANCELED' ? 'danger' : project.status === 'CLOSED' ? 'success' : 'info'" effect="plain">
      {{ t('project') }}：{{ t(`status.${project.status || 'UNKNOWN'}`) }}
    </el-tag>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

const props = defineProps({ project: { type: Object, required: true } })
const { t } = useI18n({ useScope: 'local', messages: {
  'zh-CN': {
    project: '项目',
    status: { DRAFT: '草稿', PLANNING: '规划中', ACTIVE: '执行中', PAUSED: '暂停', ACCEPTANCE: '待验收', CLOSED: '已完成并冻结', CANCELED: '已取消', UNKNOWN: '未知' }
  },
  'vi-VN': {
    project: 'Dự án',
    status: { DRAFT: 'Bản nháp', PLANNING: 'Lập kế hoạch', ACTIVE: 'Đang thực hiện', PAUSED: 'Tạm dừng', ACCEPTANCE: 'Chờ nghiệm thu', CLOSED: 'Đã hoàn thành và khóa', CANCELED: 'Đã hủy', UNKNOWN: 'Chưa rõ' }
  }
} })
</script>

<style scoped>
.business-project-state{display:flex;flex-wrap:wrap;gap:6px;align-items:center}
</style>
