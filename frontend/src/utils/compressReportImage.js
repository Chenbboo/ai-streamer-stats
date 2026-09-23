const MAX_EDGE = 2560
const MIN_SAVING_RATIO = 0.95
const WEBP_QUALITIES = [0.82, 0.72, 0.62]

const toWebp = (canvas, quality) => new Promise((resolve, reject) => {
  canvas.toBlob(blob => {
    if (blob?.type === 'image/webp' && blob.size) resolve(blob)
    else reject(new Error('WebP encoding unavailable'))
  }, 'image/webp', quality)
})

export function isCompressibleReportImage(file) {
  return /\.(jpe?g|png|webp)$/i.test(file?.name || '')
}

// Keep the original when the browser cannot make a meaningfully smaller image.
export async function compressReportImage(file) {
  if (!isCompressibleReportImage(file)) return { file, compressed: false }

  const bitmap = await createImageBitmap(file)
  try {
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(bitmap.width * scale))
    canvas.height = Math.max(1, Math.round(bitmap.height * scale))
    const context = canvas.getContext('2d', { alpha: true })
    if (!context) throw new Error('Canvas unavailable')
    context.imageSmoothingEnabled = true
    context.imageSmoothingQuality = 'high'
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)

    let smallest = null
    for (const quality of WEBP_QUALITIES) {
      const blob = await toWebp(canvas, quality)
      if (!smallest || blob.size < smallest.size) smallest = blob
      if (blob.size < file.size * MIN_SAVING_RATIO && blob.size <= 20 * 1024 * 1024) break
    }
    if (!smallest || smallest.size >= file.size * MIN_SAVING_RATIO) return { file, compressed: false }

    const name = file.name.replace(/\.[^.]+$/, '.webp')
    return {
      file: new File([smallest], name, { type: 'image/webp', lastModified: file.lastModified }),
      compressed: true,
      originalSize: file.size,
      outputSize: smallest.size
    }
  } finally {
    bitmap.close()
  }
}
