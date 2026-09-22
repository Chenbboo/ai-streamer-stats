<template>
  <el-dialog v-model="open" width="500px" :title="$tr(&quot;选择生成类型&quot;)" @open="onOpen" @close="onClose">
    <el-form ref="codeTypeForm" :model="formData" :rules="rules" label-width="100px">
      <el-form-item :label="$tr(&quot;生成类型&quot;)" prop="type">
        <el-radio-group v-model="formData.type">
          <el-radio-button v-for="(item, index) in typeOptions" :key="index" :label="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="showFileName" :label="$tr(&quot;文件名&quot;)" prop="fileName">
        <el-input v-model="formData.fileName" :placeholder="$tr(&quot;请输入文件名&quot;)" clearable />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="onClose">{{ $tr("取消") }}</el-button>
      <el-button type="primary" @click="handelConfirm">{{ $tr("确定") }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { translateText } from '@/locales/translate'

const open = defineModel()
const props = defineProps({
  showFileName: Boolean
})
const emit = defineEmits(['confirm'])
const formData = ref({
  fileName: undefined,
  type: 'file'
})
const codeTypeForm = ref()
const rules = {
  fileName: [{
    required: true,
    message: translateText("请输入文件名"),
    trigger: 'blur'
  }],
  type: [{
    required: true,
    message: translateText("生成类型不能为空"),
    trigger: 'change'
  }]
}
const typeOptions = ref([
  {
    label: translateText("页面"),
    value: 'file'
  },
  {
    label: translateText("弹窗"),
    value: 'dialog'
  }
])
function onOpen() {
  if (props.showFileName) {
    formData.value.fileName = `${+new Date()}.vue`
  }
}
function onClose() {
  open.value = false
}
function handelConfirm() {
  codeTypeForm.value.validate(valid => {
    if (!valid) return
    emit('confirm', { ...formData.value })
    onClose()
  })
}
</script>