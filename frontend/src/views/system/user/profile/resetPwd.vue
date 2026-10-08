<template>
  <el-form ref="pwdRef" :model="user" :rules="rules" label-width="80px">
    <el-form-item :label="$tr(&quot;旧密码&quot;)" prop="oldPassword">
      <el-input v-model="user.oldPassword" :placeholder="$tr(&quot;请输入旧密码&quot;)" type="password" show-password />
    </el-form-item>
    <el-form-item :label="$tr(&quot;新密码&quot;)" prop="newPassword" :rules="infoPwdValidator">
      <el-input v-model="user.newPassword" :placeholder="$tr(&quot;请输入新密码&quot;)" type="password" show-password />
    </el-form-item>
    <el-form-item :label="$tr(&quot;确认密码&quot;)" prop="confirmPassword">
      <el-input v-model="user.confirmPassword" :placeholder="$tr(&quot;请确认新密码&quot;)" type="password" show-password />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="submit">{{ $tr("保存") }}</el-button>
      <el-button type="danger" @click="close">{{ $tr("关闭") }}</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { translateText } from '@/locales/translate'

import { usePasswordRule } from "@/utils/passwordRule"
import { updateUserPwd } from "@/api/system/user"

const { proxy } = getCurrentInstance()
const { infoPwdValidator } = usePasswordRule()

const user = reactive({
  oldPassword: undefined,
  newPassword: undefined,
  confirmPassword: undefined
})

const equalToPassword = (rule, value, callback) => {
  if (user.newPassword !== value) {
    callback(new Error(translateText("两次输入的密码不一致")))
  } else {
    callback()
  }
}

const rules = ref({
  oldPassword: [{ required: true, message: translateText("旧密码不能为空"), trigger: "blur" }],
  confirmPassword: [{ required: true, message: translateText("确认密码不能为空"), trigger: "blur" }, { required: true, validator: equalToPassword, trigger: "blur" }]
})

/** 提交按钮 */
function submit() {
  proxy.$refs.pwdRef.validate(valid => {
    if (valid) {
      updateUserPwd(user.oldPassword, user.newPassword).then(() => {
        proxy.$modal.msgSuccess(translateText("修改成功"))
      })
    }
  })
}

/** 关闭按钮 */
function close() {
  proxy.$tab.closePage()
}
</script>
