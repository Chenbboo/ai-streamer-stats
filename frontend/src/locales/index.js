import { createI18n } from 'vue-i18n'
import Cookies from 'js-cookie'
import zhCN from './zh-CN'
import viVN from './vi-VN'

const messages = {
  'zh-CN': zhCN,
  'vi-VN': viVN
}

const i18n = createI18n({
  legacy: false, // 使用 Composition API
  locale: Cookies.get('language') || 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages
})

export default i18n
