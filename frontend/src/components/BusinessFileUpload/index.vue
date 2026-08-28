<template>
  <file-upload
    v-bind="$attrs"
    :model-value="modelValue"
    action="/business/file/upload"
    :data="{ projectId }"
    :limit="10"
    :file-size="20"
    :total-size="100"
    :max-concurrency="3"
    :file-type="BUSINESS_FILE_TYPES"
    business-preview
    :disabled="disabled"
    :drag="drag"
    :is-show-tip="isShowTip"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>

<script setup>
defineOptions({ inheritAttrs: false })

defineProps({
  modelValue: [String, Object, Array],
  projectId: [String, Number],
  disabled: { type: Boolean, default: false },
  drag: { type: Boolean, default: true },
  isShowTip: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue'])

const BUSINESS_FILE_TYPES = Object.freeze([
  'jpg', 'jpeg', 'png', 'webp',
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt',
  'zip', 'mp4', 'mov'
])
</script>
