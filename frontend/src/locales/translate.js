import i18n from './index.js'
import { translateCopy, translateMessage } from './text.js'
export { sourceText } from './text.js'

export const translateText = (source, params) => translateCopy(source, i18n.global.locale.value, params)
export const translateServerMessage = source => translateMessage(source, i18n.global.locale.value)
export const getDisplayLocale = () => i18n.global.locale.value
