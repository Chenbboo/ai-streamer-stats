import { onActivated, onDeactivated } from 'vue'

/**
 * RuoYi keeps menu pages alive. Refresh server-backed business data when a
 * cached page is shown again, without issuing a duplicate request on its
 * initial mount.
 */
export function useBusinessRefreshOnReactivated(refresh) {
  let needsRefresh = false

  onDeactivated(() => {
    needsRefresh = true
  })

  onActivated(async () => {
    if (!needsRefresh) return
    needsRefresh = false
    await refresh()
  })
}
