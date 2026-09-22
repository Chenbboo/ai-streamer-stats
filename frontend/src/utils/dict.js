import useDictStore from '@/store/modules/dict'
import { getDicts } from '@/api/system/dict/data'
import { translateText } from '../locales/translate.js'

/**
 * 获取字典数据
 */
export function useDict(...args) {
  const res = ref({})
  return (() => {
    args.forEach((dictType, index) => {
      res.value[dictType] = []
      const dicts = useDictStore().getDict(dictType)
      if (dicts) {
        res.value[dictType] = dicts.map(item => ({ ...item, label: translateText(item.label) }))
      } else {
        getDicts(dictType).then(resp => {
          const raw = resp.data.map(p => ({ label: p.dictLabel, value: p.dictValue, elTagType: p.listClass, elTagClass: p.cssClass }))
          useDictStore().setDict(dictType, raw)
          res.value[dictType] = raw.map(item => ({ ...item, label: translateText(item.label) }))
        })
      }
    })
    return toRefs(res.value)
  })()
}
