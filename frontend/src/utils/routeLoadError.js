const RECOVERY_KEY = 'route-load-recovery'
const RECOVERY_INTERVAL = 60 * 1000

export function isRouteAssetError(error) {
  return /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed|Unable to preload CSS|Loading (?:CSS )?chunk .* failed/i.test(error?.message || '')
}

// A tab opened before a release may still request the previous build's lazy chunks.
// Reload the intended URL once, retaining query/hash, to load the current entry file.
export function createRouteLoadErrorHandler({ finishProgress, notify, reload, storage, now = Date.now }) {
  return (error, to) => {
    finishProgress()
    const assetError = isRouteAssetError(error)
    const target = to?.fullPath
    if (assetError && target?.startsWith('/') && !target.startsWith('//')) {
      try {
        const previous = JSON.parse(storage.getItem(RECOVERY_KEY) || 'null')
        const time = now()
        if (!previous || previous.target !== target || time - previous.time >= RECOVERY_INTERVAL) {
          // Persist before reloading so a genuinely unavailable asset cannot loop.
          storage.setItem(RECOVERY_KEY, JSON.stringify({ target, time }))
          reload(target)
          return
        }
      } catch {
        // Without working session storage, automatic reload cannot be bounded.
      }
    }
    notify(assetError ? 'navigation.assetLoadFailed' : 'navigation.failed')
  }
}
