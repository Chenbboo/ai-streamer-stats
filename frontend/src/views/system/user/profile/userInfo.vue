<template>
   <el-form ref="userRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item :label="$tr(&quot;用户昵称&quot;)" prop="nickName">
         <el-input v-model="form.nickName" maxlength="30" />
      </el-form-item>
      <el-form-item :label="$tr(&quot;手机号码&quot;)" prop="phonenumber">
         <el-input v-model="form.phonenumber" maxlength="11" />
      </el-form-item>
      <el-form-item :label="$tr(&quot;邮箱&quot;)" prop="email">
         <el-input v-model="form.email" maxlength="50" />
      </el-form-item>
      <el-form-item :label="$tr(&quot;性别&quot;)">
         <el-radio-group v-model="form.sex">
            <el-radio value="0">{{ $tr("男") }}</el-radio>
            <el-radio value="1">{{ $tr("女") }}</el-radio>
         </el-radio-group>
      </el-form-item>
      <el-form-item>
      <el-button type="primary" @click="submit">{{ $tr("保存") }}</el-button>
      <el-button type="danger" @click="close">{{ $tr("关闭") }}</el-button>
      </el-form-item>
   </el-form>
</template>

<script setup>
import { translateText } from '@/locales/translate'

import { updateUserProfile } from "@/api/system/user"

const props = defineProps({
  user: {
    type: Object
  }
})

const { proxy } = getCurrentInstance()

const form = ref({})
const rules = ref({
  nickName: [{ required: true, message: translateText("用户昵称不能为空"), trigger: "blur" }],
  email: [{ required: true, message: translateText("邮箱地址不能为空"), trigger: "blur" }, { type: "email", message: translateText("请输入正确的邮箱地址"), trigger: ["blur", "change"] }],
  phonenumber: [{ required: true, message: translateText("手机号码不能为空"), trigger: "blur" }, { pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/, message: translateText("请输入正确的手机号码"), trigger: "blur" }],
})

/** 提交按钮 */
function submit() {
  proxy.$refs.userRef.validate(valid => {
    if (valid) {
      updateUserProfile(form.value).then(() => {
        proxy.$modal.msgSuccess(translateText("修改成功"))
        props.user.phonenumber = form.value.phonenumber
        props.user.email = form.value.email
      })
    }
  })
}

/** 关闭按钮 */
function close() {
  proxy.$tab.closePage()
}

// 回显当前登录用户信息
watch(() => props.user, user => {
  if (user) {
    form.value = { nickName: user.nickName, phonenumber: user.phonenumber, email: user.email, sex: user.sex }
  }
},{ immediate: true })
</script>
