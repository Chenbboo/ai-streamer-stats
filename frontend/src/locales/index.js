import { createI18n } from 'vue-i18n'
import Cookies from 'js-cookie'
import zhCN from './zh-CN.js'
import viVN from './vi-VN.js'

const messages = {
  'zh-CN': zhCN,
  'vi-VN': viVN
}

const i18n = createI18n({
  legacy: false, // 使用 Composition API
  locale: Cookies.get('language') === 'vi-VN' ? 'vi-VN' : 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages
})

export default i18n
