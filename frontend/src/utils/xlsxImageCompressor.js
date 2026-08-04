import JSZip from 'jszip'

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
const LOCAL_FILE_LIMIT = 500 * 1024 * 1024
const UPLOAD_FILE_LIMIT = 200 * 1024 * 1024
const IMAGE_SIZE_TRIGGER = 5 * 1024 * 1024
const MAX_IMAGE_DIMENSION = 1600
const WEBP_QUALITY = 0.85

const normalizePath = value => {
  const parts = []
  String(value || '').replace(/^\//, '').split('/').forEach(part => {
    if (!part || part === '.') return
    if (part === '..') parts.pop()
    else parts.push(part)
  })
  return parts.join('/')
}

const ownerPathOfRelationship = relationshipPath =>
  relationshipPath.replace('/_rels/', '/').replace(/\.rels$/i, '')

const resolveTarget = (ownerPath, target) => {
  if (String(target).startsWith('/')) return normalizePath(target)
  const ownerDirectory = ownerPath.slice(0, ownerPath.lastIndexOf('/') + 1)
  return normalizePath(ownerDirectory + target)
}

const relationshipFiles = zip => Object.keys(zip.files).filter(path =>
  /^xl\/(?:drawings\/_rels\/[^/]+\.rels|_rels\/cellimages\.xml\.rels)$/i.test(path))

const parseXml = text => {
  const xmlDocument = new DOMParser().parseFromString(text, 'application/xml')
  if (xmlDocument.querySelector('parsererror')) throw new Error('Excel 图片关系文件格式不正确')
  return xmlDocument
}

const imageRelations = async zip => {
  const relations = []
  for (const path of relationshipFiles(zip)) {
    const xmlDocument = parseXml(await zip.file(path).async('string'))
    const ownerPath = ownerPathOfRelationship(path)
    for (const node of xmlDocument.getElementsByTagNameNS('*', 'Relationship')) {
      const target = node.getAttribute('Target') || ''
      const type = node.getAttribute('Type') || ''
      if (!type.endsWith('/image') || !target) continue
      relations.push({
        path,
        document: xmlDocument,
        node,
        target,
        mediaPath: resolveTarget(ownerPath, target)
      })
    }
  }
  return relations
}

const canvasBlob = (canvas, type, quality) => new Promise((resolve, reject) => {
  canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('浏览器图片编码失败')), type, quality)
})

const compressImage = async blob => {
  const bitmap = await createImageBitmap(blob)
  try {
    if (blob.size <= IMAGE_SIZE_TRIGGER && bitmap.width <= MAX_IMAGE_DIMENSION
      && bitmap.height <= MAX_IMAGE_DIMENSION) return null

    const scale = Math.min(1, MAX_IMAGE_DIMENSION / bitmap.width, MAX_IMAGE_DIMENSION / bitmap.height)
    const width = Math.max(1, Math.round(bitmap.width * scale))
    const height = Math.max(1, Math.round(bitmap.height * scale))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d', { alpha: true })
    if (!context) throw new Error('浏览器无法创建图片画布')
    context.imageSmoothingEnabled = true
    context.imageSmoothingQuality = 'high'
    context.drawImage(bitmap, 0, 0, width, height)
    const webp = await canvasBlob(canvas, 'image/webp', WEBP_QUALITY)
    if (webp.type !== 'image/webp' || webp.size === 0) throw new Error('浏览器不支持 WebP 编码')
    return webp
  } finally {
    bitmap.close()
  }
}

const ensureWebpContentType = async zip => {
  const entry = zip.file('[Content_Types].xml')
  if (!entry) throw new Error('Excel 缺少内容类型配置')
  const xmlDocument = parseXml(await entry.async('string'))
  const defaults = [...xmlDocument.getElementsByTagNameNS('*', 'Default')]
  if (defaults.some(node => String(node.getAttribute('Extension')).toLowerCase() === 'webp')) return

  const node = xmlDocument.createElementNS(xmlDocument.documentElement.namespaceURI, 'Default')
  node.setAttribute('Extension', 'webp')
  node.setAttribute('ContentType', 'image/webp')
  xmlDocument.documentElement.appendChild(node)
  zip.file('[Content_Types].xml', new XMLSerializer().serializeToString(xmlDocument))
}

const updateRelationships = (zip, relations, replacements) => {
  const changedFiles = new Map()
  relations.forEach(relation => {
    if (!replacements.has(relation.mediaPath)) return
    relation.node.setAttribute('Target', relation.target.replace(/\.[^./]+$/, '.webp'))
    changedFiles.set(relation.path, relation.document)
  })
  changedFiles.forEach((xmlDocument, path) =>
    zip.file(path, new XMLSerializer().serializeToString(xmlDocument)))
}

export const formatFileSize = bytes => {
  if (!Number.isFinite(Number(bytes))) return '0KB'
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}

export async function compressXlsxImages(file, onProgress = () => {}) {
  if (!file || !String(file.name || '').toLowerCase().endsWith('.xlsx')) {
    return { file, compressed: 0 }
  }
  if (file.size > LOCAL_FILE_LIMIT) throw new Error('Excel 文件超过500MB，请拆分后导入')

  onProgress({ percentage: 2, text: '读取 Excel' })
  const zip = await JSZip.loadAsync(file)
  const relations = await imageRelations(zip)
  const mediaPaths = [...new Set(relations.map(item => item.mediaPath))]
    .filter(path => zip.file(path) && /\.(png|jpe?g|webp)$/i.test(path))
  const replacements = new Map()
  let originalImageBytes = 0
  let compressedImageBytes = 0
  let skipped = 0

  for (let index = 0; index < mediaPaths.length; index++) {
    const path = mediaPaths[index]
    const bytes = await zip.file(path).async('uint8array')
    onProgress({
      percentage: Math.round(5 + (index / Math.max(1, mediaPaths.length)) * 70),
      text: `压缩图片 ${index + 1}/${mediaPaths.length}`
    })
    try {
      const result = await compressImage(new Blob([bytes]))
      if (!result) continue
      const webpBytes = new Uint8Array(await result.arrayBuffer())
      const webpPath = path.replace(/\.[^./]+$/, '.webp')
      zip.file(webpPath, webpBytes)
      zip.remove(path)
      replacements.set(path, webpPath)
      originalImageBytes += bytes.byteLength
      compressedImageBytes += webpBytes.byteLength
    } catch (error) {
      skipped++
      console.warn(`Excel 图片压缩失败，保留原图：${path}`, error)
    }
  }

  if (replacements.size) {
    updateRelationships(zip, relations, replacements)
    await ensureWebpContentType(zip)
  }
  if (!replacements.size) {
    if (file.size > UPLOAD_FILE_LIMIT) throw new Error('Excel 压缩后仍超过200MB，请拆分后导入')
    onProgress({ percentage: 100, text: '无需压缩' })
    return {
      file,
      compressed: 0,
      imageCount: mediaPaths.length,
      skipped,
      originalSize: file.size,
      outputSize: file.size,
      originalImageBytes: 0,
      compressedImageBytes: 0
    }
  }

  onProgress({ percentage: 82, text: '重新生成 Excel' })
  const output = await zip.generateAsync({
    type: 'blob',
    mimeType: XLSX_MIME,
    compression: 'DEFLATE',
    compressionOptions: { level: 6 }
  }, metadata => onProgress({
    percentage: Math.min(99, 82 + Math.round(metadata.percent * 0.17)),
    text: '重新生成 Excel'
  }))
  if (output.size > UPLOAD_FILE_LIMIT) throw new Error('Excel 压缩后仍超过200MB，请拆分后导入')

  onProgress({ percentage: 100, text: '压缩完成' })
  return {
    file: new File([output], file.name, { type: XLSX_MIME, lastModified: Date.now() }),
    compressed: replacements.size,
    imageCount: mediaPaths.length,
    skipped,
    originalSize: file.size,
    outputSize: output.size,
    originalImageBytes,
    compressedImageBytes
  }
}
