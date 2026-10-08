import defaultSettings from '@/settings'
import useSettingsStore from '@/store/modules/settings'
import { translateText } from '../locales/translate.js'

/**
 * 动态修改标题
 */
export function useDynamicTitle() {
  const settingsStore = useSettingsStore()
  if (settingsStore.dynamicTitle) {
    document.title = translateText(settingsStore.title) + ' - ' + translateText(defaultSettings.title)
  } else {
    document.title = translateText(defaultSettings.title)
  }
}
