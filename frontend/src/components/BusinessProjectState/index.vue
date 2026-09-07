<template>
  <div class="business-project-state">
    <el-tag :type="project.status === 'CANCELED' ? 'danger' : project.status === 'CLOSED' ? 'success' : 'info'" effect="plain">
      {{ t('delivery') }}：{{ t(`status.${project.status || 'UNKNOWN'}`) }}
    </el-tag>
    <el-tag :type="accounting === 'OPEN' ? 'warning' : 'info'" effect="plain">
      {{ t('accounting') }}：{{ t(`accountingStatus.${accounting}`) }}
    </el-tag>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { projectAccountingState } from '@/utils/businessProjectState'

const props = defineProps({ project: { type: Object, required: true } })
const accounting = computed(() => projectAccountingState(props.project))
const { t } = useI18n({ useScope: 'local', messages: {
  'zh-CN': {
    delivery: '交付', accounting: '核算',
    status: { DRAFT: '草稿', PLANNING: '规划中', ACTIVE: '执行中', PAUSED: '暂停', ACCEPTANCE: '待验收', CLOSED: '已完成', CANCELED: '已取消', UNKNOWN: '未知' },
    accountingStatus: { OPEN: '开放', CLOSED: '已关闭', UNKNOWN: '待核对' }
  },
  'vi-VN': {
    delivery: 'Bàn giao', accounting: 'Quyết toán',
    status: { DRAFT: 'Bản nháp', PLANNING: 'Lập kế hoạch', ACTIVE: 'Đang thực hiện', PAUSED: 'Tạm dừng', ACCEPTANCE: 'Chờ nghiệm thu', CLOSED: 'Hoàn thành', CANCELED: 'Đã hủy', UNKNOWN: 'Chưa rõ' },
    accountingStatus: { OPEN: 'Đang mở', CLOSED: 'Đã đóng', UNKNOWN: 'Cần xác minh' }
  }
} })
</script>

<style scoped>
.business-project-state{display:flex;flex-wrap:wrap;gap:6px;align-items:center}
</style>
