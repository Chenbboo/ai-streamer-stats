<template>
  <el-form ref="basicInfoForm" :model="info" :rules="rules" label-width="150px">
    <el-row>
      <el-col :span="12">
        <el-form-item :label="$tr(&quot;表名称&quot;)" prop="tableName">
          <el-input :placeholder="$tr(&quot;请输入仓库名称&quot;)" v-model="info.tableName" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item :label="$tr(&quot;表描述&quot;)" prop="tableComment">
          <el-input :placeholder="$tr(&quot;请输入&quot;)" v-model="info.tableComment" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item :label="$tr(&quot;实体类名称&quot;)" prop="className">
          <el-input :placeholder="$tr(&quot;请输入&quot;)" v-model="info.className" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item :label="$tr(&quot;作者&quot;)" prop="functionAuthor">
          <el-input :placeholder="$tr(&quot;请输入&quot;)" v-model="info.functionAuthor" />
        </el-form-item>
      </el-col>
      <el-col :span="24">
        <el-form-item :label="$tr(&quot;备注&quot;)" prop="remark">
          <el-input type="textarea" :rows="3" v-model="info.remark"></el-input>
        </el-form-item>
      </el-col>
    </el-row>
  </el-form>
</template>

<script setup>
import { translateText } from '@/locales/translate'

defineProps({
  info: {
    type: Object,
    default: null
  }
})

// 表单校验
const rules = ref({
  tableName: [{ required: true, message: translateText("请输入表名称"), trigger: "blur" }],
  tableComment: [{ required: true, message: translateText("请输入表描述"), trigger: "blur" }],
  className: [{ required: true, message: translateText("请输入实体类名称"), trigger: "blur" }],
  functionAuthor: [{ required: true, message: translateText("请输入作者"), trigger: "blur" }]
})
</script>
