<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import useSettingsStore from '@/store/modules/settings'
import { handleThemeStyle } from '@/utils/theme'
import { useI18n } from 'vue-i18n'
import zhLocale from 'element-plus/es/locale/lang/zh-cn'
import viLocale from 'element-plus/es/locale/lang/vi'
import { useDynamicTitle } from '@/utils/dynamicTitle'

const { locale } = useI18n()
const elementLocale = computed(() => locale.value === 'vi-VN' ? viLocale : zhLocale)
watch(locale, value => {
  document.documentElement.lang = value
  useDynamicTitle()
}, { immediate: true })

onMounted(() => {
  nextTick(() => {
    // 初始化主题样式
    handleThemeStyle(useSettingsStore().theme)
  })
})
</script>
