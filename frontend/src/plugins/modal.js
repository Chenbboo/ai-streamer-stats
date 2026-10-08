import { translateText, translateServerMessage } from '@/locales/translate'
import { ElMessage, ElMessageBox, ElNotification, ElLoading } from 'element-plus'

let loadingInstance

export default {
  // 消息提示
  msg(content) {
    content = translateServerMessage(content)
    ElMessage.info(content)
  },
  // 错误消息
  msgError(content) {
    content = translateServerMessage(content)
    ElMessage.error(content)
  },
  // 成功消息
  msgSuccess(content) {
    content = translateServerMessage(content)
    ElMessage.success(content)
  },
  // 警告消息
  msgWarning(content) {
    content = translateServerMessage(content)
    ElMessage.warning(content)
  },
  // 弹出提示
  alert(content) {
    content = translateServerMessage(content)
    ElMessageBox.alert(content, translateText("系统提示"))
  },
  // 错误提示
  alertError(content) {
    content = translateServerMessage(content)
    ElMessageBox.alert(content, translateText("系统提示"), { type: 'error' })
  },
  // 成功提示
  alertSuccess(content) {
    content = translateServerMessage(content)
    ElMessageBox.alert(content, translateText("系统提示"), { type: 'success' })
  },
  // 警告提示
  alertWarning(content) {
    content = translateServerMessage(content)
    ElMessageBox.alert(content, translateText("系统提示"), { type: 'warning' })
  },
  // 通知提示
  notify(content) {
    content = translateServerMessage(content)
    ElNotification.info(content)
  },
  // 错误通知
  notifyError(content) {
    content = translateServerMessage(content)
    ElNotification.error(content)
  },
  // 成功通知
  notifySuccess(content) {
    content = translateServerMessage(content)
    ElNotification.success(content)
  },
  // 警告通知
  notifyWarning(content) {
    content = translateServerMessage(content)
    ElNotification.warning(content)
  },
  // 确认窗体
  confirm(content) {
    content = translateServerMessage(content)
    return ElMessageBox.confirm(content, translateText("系统提示"), {
      confirmButtonText: translateText("确定"),
      cancelButtonText: translateText("取消"),
      type: "warning",
    })
  },
  // 提交内容
  prompt(content) {
    content = translateServerMessage(content)
    return ElMessageBox.prompt(content, translateText("系统提示"), {
      confirmButtonText: translateText("确定"),
      cancelButtonText: translateText("取消"),
      type: "warning",
    })
  },
  // 打开遮罩层
  loading(content) {
    content = translateServerMessage(content)
    loadingInstance = ElLoading.service({
      lock: true,
      text: content,
      background: "rgba(0, 0, 0, 0.7)",
    })
  },
  // 关闭遮罩层
  closeLoading() {
    loadingInstance.close()
  }
}
