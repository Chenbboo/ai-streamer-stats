<template>
  <div>
    <el-dialog :title="$tr(&quot;添加选项&quot;)" v-model="open" width="800px" :close-on-click-modal="false" :modal-append-to-body="false"
      @open="onOpen" @close="onClose">
      <el-form ref="treeNodeForm" :model="formData" :rules="rules" label-width="100px">
        <el-col :span="24">
          <el-form-item :label="$tr(&quot;选项名&quot;)" prop="label">
            <el-input v-model="formData.label" :placeholder="$tr(&quot;请输入选项名&quot;)" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item :label="$tr(&quot;选项值&quot;)" prop="value">
            <el-input v-model="formData.value" :placeholder="$tr(&quot;请输入选项值&quot;)" clearable>
              <template #append>
                <el-select v-model="dataType" :style="{ width: '100px' }">
                  <el-option v-for="(item, index) in dataTypeOptions" :key="index" :label="item.label" :value="item.value"
                    :disabled="item.disabled" />
                </el-select>
              </template>

            </el-input>
          </el-form-item>
        </el-col>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="handelConfirm">{{ $tr("确 定") }}</el-button>
          <el-button @click="onClose">{{ $tr("取 消") }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { translateText } from '@/locales/translate'

const open = defineModel()
const emit = defineEmits(['confirm'])
const formData = ref({
  label: undefined,
  value: undefined
})
const rules = {
  label: [
    {
      required: true,
      message: translateText("请输入选项名"),
      trigger: 'blur'
    }
  ],
  value: [
    {
      required: true,
      message: translateText("请输入选项值"),
      trigger: 'blur'
    }
  ]
}
const dataType = ref('string')
const dataTypeOptions = ref([
  {
    label: translateText("字符串"),
    value: 'string'
  },
  {
    label: translateText("数字"),
    value: 'number'
  }
])
const id = ref(100)
const treeNodeForm = ref()

function onOpen() {
  formData.value = {
    label: undefined,
    value: undefined
  }
}

function onClose() {
  open.value = false
}

function handelConfirm() {
  treeNodeForm.value.validate(valid => {
    if (!valid) return
    if (dataType.value === 'number') {
      formData.value.value = parseFloat(formData.value.value)
    }
    formData.value.id = id.value++
    emit('commit', formData.value)
    onClose()
  })
}
</script>
