<template>
  <!-- 创建表 -->
  <el-dialog :title="$tr(&quot;创建表&quot;)" v-model="visible" width="800px" top="5vh" append-to-body>
    <span>{{ $tr("创建表语句(支持多个建表语句)：") }}</span>
    <el-input type="textarea" :rows="10" :placeholder="$tr(&quot;请输入文本&quot;)" v-model="content"></el-input>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handleImportTable">{{ $tr("确 定") }}</el-button>
        <el-button @click="visible = false">{{ $tr("取 消") }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { translateText } from '@/locales/translate'

import { createTable } from "@/api/tool/gen"

const visible = ref(false)
const content = ref("")
const { proxy } = getCurrentInstance()
const emit = defineEmits(["ok"])

/** 显示弹框 */
function show() {
  visible.value = true
}

/** 导入按钮操作 */
function handleImportTable() {
  if (content.value === "") {
    proxy.$modal.msgError(translateText("请输入建表语句"))
    return
  }
  createTable({ sql: content.value, tplWebType: 'element-plus' }).then(res => {
    proxy.$modal.msgSuccess(res.msg)
    if (res.code === 200) {
      visible.value = false
      emit("ok")
    }
  })
}

defineExpose({
  show,
})
</script>
