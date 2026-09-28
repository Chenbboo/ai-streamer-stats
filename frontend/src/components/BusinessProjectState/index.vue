<template>
  <div class="business-project-state">
    <el-tag :type="project.status === 'CANCELED' ? 'danger' : project.status === 'CLOSED' ? 'success' : 'info'" effect="plain">
      {{ t('project') }}：{{ t(`status.${project.status || 'UNKNOWN'}`) }}
    </el-tag>
    <el-tag v-if="accountingState !== 'UNKNOWN'" :type="accountingState==='CLOSED'?'info':'primary'" effect="plain">{{ t('accounting') }}：{{ t(`accountingStatus.${accountingState}`) }}</el-tag>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { computed } from 'vue'
import { projectAccountingState } from '@/utils/businessProjectState'

const props = defineProps({ project: { type: Object, required: true } })
const accountingState = computed(() => projectAccountingState(props.project))
const { t } = useI18n({ useScope: 'local', messages: {
  'zh-CN': {
    project: '项目',
    status: { DRAFT: '草稿', PLANNING: '规划中', ACTIVE: '执行中', PAUSED: '暂停', ACCEPTANCE: '待验收', CLOSED: '已结项', CANCELED: '已取消', UNKNOWN: '未知' },
    accounting: '核算', accountingStatus: {OPEN:'结算开放',CLOSED:'已冻结'}
  },
  'vi-VN': {
    project: 'Dự án',
    status: { DRAFT: 'Bản nháp', PLANNING: 'Lập kế hoạch', ACTIVE: 'Đang thực hiện', PAUSED: 'Tạm dừng', ACCEPTANCE: 'Chờ nghiệm thu', CLOSED: 'Đã kết thúc', CANCELED: 'Đã hủy', UNKNOWN: 'Chưa rõ' },
    accounting: 'Hạch toán', accountingStatus: {OPEN:'Đang mở quyết toán',CLOSED:'Đã khóa'}
  }
} })
</script>

<style scoped>
.business-project-state{display:flex;flex-wrap:wrap;gap:6px;align-items:center}
</style>
