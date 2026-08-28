<template>
  <div class="upload-file">
    <el-upload
      multiple
      :action="uploadFileUrl"
      :accept="acceptTypes"
      :before-upload="handleBeforeUpload"
      :file-list="fileList"
      :data="data"
      :limit="limit"
      :on-error="handleUploadError"
      :on-exceed="handleExceed"
      :on-success="handleUploadSuccess"
      :show-file-list="false"
      :headers="headers"
      :http-request="maxConcurrency > 0 ? uploadWithConcurrency : undefined"
      class="upload-file-uploader"
      ref="fileUpload"
      v-if="!disabled"
    >
      <!-- 上传按钮 -->
      <el-button type="primary">选取文件</el-button>
    </el-upload>
    <!-- 上传提示 -->
    <div class="upload-file-tip" v-if="showTip && !disabled">
      <div class="upload-file-tip__summary">
        <span v-if="fileSize">单个文件 <b>≤ {{ fileSize }}MB</b></span>
        <span>最多 <b>{{ limit }} 个</b></span>
        <span v-if="totalSize">总大小 <b>≤ {{ totalSize }}MB</b></span>
      </div>
      <div v-if="fileType?.length" class="upload-file-tip__types">
        <span class="upload-file-tip__label">支持格式</span>
        <span class="upload-file-tip__extensions">{{ formattedFileTypes }}</span>
      </div>
    </div>
    <!-- 文件缩略图列表 -->
    <transition-group ref="uploadFileList" class="upload-file-list" name="el-fade-in-linear" tag="ul">
      <li v-for="(file, index) in fileList" :key="file.uid" class="upload-file-card">
        <div class="upload-file-card__preview">
          <el-image
            v-if="isImage(file)"
            :src="imageCardUrl(file)"
            :preview-src-list="imagePreviewUrls"
            :initial-index="imagePreviewIndex(file)"
            fit="cover"
            preview-teleported
            @error="markOptimizedPreviewFailed(file)"
          >
            <template #error>
              <div class="file-type-tile">
                <el-icon><Picture /></el-icon>
                <strong>{{ fileExtension(file) || 'IMG' }}</strong>
              </div>
            </template>
          </el-image>
          <div v-else class="file-type-tile" :class="`is-${fileCategory(file)}`">
            <el-icon><Document /></el-icon>
            <strong>{{ fileExtension(file) || 'FILE' }}</strong>
          </div>

          <div v-if="!isImage(file)" class="upload-file-card__cover">
            <button v-if="isVideo(file)" type="button" aria-label="预览视频" @click="previewVideo(file)">
              <el-icon><VideoPlay /></el-icon><span>预览</span>
            </button>
            <button v-else type="button" aria-label="打开附件" @click="openFile(file)">
              <el-icon><View /></el-icon><span>打开</span>
            </button>
          </div>

          <button v-if="!disabled" class="upload-file-card__delete" type="button" aria-label="删除文件" @click="handleDelete(index)">
            <el-icon><Close /></el-icon>
          </button>
        </div>

        <el-tooltip :content="getFileName(file.name || file.url)" placement="top" :show-after="500">
          <button class="upload-file-card__name" type="button" @click="openFile(file)">
            {{ getFileName(file.name || file.url) }}
          </button>
        </el-tooltip>
        <span class="upload-file-card__meta">
          {{ fileTypeLabel(file) }}<template v-if="file.size"> · {{ formatFileSize(file.size) }}</template>
        </span>
      </li>
    </transition-group>

    <el-dialog v-model="videoPreviewVisible" title="视频预览" width="min(840px, 94vw)" append-to-body destroy-on-close @closed="videoPreviewUrl = ''">
      <video v-if="videoPreviewUrl" class="video-preview" :src="videoPreviewUrl" controls autoplay />
    </el-dialog>
  </div>
</template>

<script setup>
import axios from 'axios'
import { getToken } from "@/utils/auth"
import { isExternal } from "@/utils/validate"
import Sortable from 'sortablejs'

const props = defineProps({
  modelValue: [String, Object, Array],
  // 上传接口地址
  action: {
    type: String,
    default: "/common/upload"
  },
  // 上传携带的参数
  data: {
    type: Object
  },
  // 数量限制
  limit: {
    type: Number,
    default: 5
  },
  // 大小限制(MB)
  fileSize: {
    type: Number,
    default: 5
  },
  // 所有文件合计大小限制(MB)，0 表示不限制
  totalSize: {
    type: Number,
    default: 0
  },
  // 最大并发上传数，0 表示使用组件默认行为
  maxConcurrency: {
    type: Number,
    default: 0
  },
  // 文件类型, 例如['png', 'jpg', 'jpeg']
  fileType: {
    type: Array,
    default: () => ["doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "pdf"]
  },
  // 是否显示提示
  isShowTip: {
    type: Boolean,
    default: true
  },
  // 禁用组件（仅查看文件）
  disabled: {
    type: Boolean,
    default: false
  },
  // 拖动排序
  drag: {
    type: Boolean,
    default: true
  },
  // 公司经营图片存在同名 WebP 预览和缩略图
  businessPreview: {
    type: Boolean,
    default: false
  }
})

const { proxy } = getCurrentInstance()
const emit = defineEmits()
const number = ref(0)
const pendingBytes = ref(0)
const pendingFileSizes = new Map()
const baseUrl = import.meta.env.VITE_APP_BASE_API
const uploadFileUrl = ref(import.meta.env.VITE_APP_BASE_API + props.action) // 上传文件服务器地址
const headers = ref({ Authorization: "Bearer " + getToken() })
const fileList = ref([])
const videoPreviewVisible = ref(false)
const videoPreviewUrl = ref("")
const failedOptimizedPreviews = ref(new Set())
const requestQueue = []
let activeRequests = 0
const showTip = computed(
  () => props.isShowTip && (props.fileType || props.fileSize)
)
const acceptTypes = computed(() => (props.fileType || []).map(type => `.${String(type).toLowerCase()}`).join(','))
const formattedFileTypes = computed(() => (props.fileType || []).map(type => String(type).toUpperCase()).join(' / '))
const imagePreviewUrls = computed(() => fileList.value.filter(isImage).map(imagePreviewUrl))

function normalizedStoredPath(value) {
  const path = String(value || '')
  return props.businessPreview ? path.replace(/\\/g, '/') : path
}

watch(() => props.modelValue, val => {
  if (val) {
    let temp = 1
    // 首先将值转为数组
    const list = Array.isArray(val) ? val : props.modelValue.split(',')
    // 然后将数组转为对象数组
    const currentFiles = new Map(fileList.value.map(item => [item.url, item]))
    fileList.value = list.map(item => {
      if (typeof item === "string") {
        const normalized = normalizedStoredPath(item)
        item = currentFiles.get(item) || currentFiles.get(normalized) || { name: normalized, url: normalized }
      } else if (props.businessPreview) {
        item = {
          ...item,
          url: normalizedStoredPath(item.url),
          previewUrl: normalizedStoredPath(item.previewUrl),
          thumbnailUrl: normalizedStoredPath(item.thumbnailUrl)
        }
      }
      item.uid = item.uid || new Date().getTime() + temp++
      return item
    })
    if (props.businessPreview) nextTick(hydrateAuthorizedFiles)
  } else {
    fileList.value = []
    return []
  }
},{ deep: true, immediate: true })

// 上传前校检格式和大小
function handleBeforeUpload(file) {
  if (props.businessPreview && !props.data?.projectId) {
    proxy.$modal.msgError('请先选择项目再上传附件')
    return false
  }
  // 校检文件类型
  if (props.fileType.length) {
    const fileName = file.name.split('.')
    const fileExt = fileName[fileName.length - 1].toLowerCase()
    const isTypeOk = props.fileType.map(type => String(type).toLowerCase()).includes(fileExt)
    if (!isTypeOk) {
      proxy.$modal.msgError(`文件格式不正确，请上传${props.fileType.join("/")}格式文件!`)
      return false
    }
  }
  // 校检文件名是否包含特殊字符
  if (file.name.includes(',')) {
    proxy.$modal.msgError('文件名不正确，不能包含英文逗号!')
    return false
  }
  // 校检文件大小
  if (props.fileSize) {
    const isLt = file.size / 1024 / 1024 <= props.fileSize
    if (!isLt) {
      proxy.$modal.msgError(`上传文件大小不能超过 ${props.fileSize} MB!`)
      return false
    }
  }
  if (props.totalSize) {
    const storedBytes = fileList.value.reduce((sum, item) => sum + (Number(item.size) || 0), 0)
    const totalBytes = storedBytes + pendingBytes.value + file.size
    if (totalBytes > props.totalSize * 1024 * 1024) {
      proxy.$modal.msgError(`全部附件总大小不能超过 ${props.totalSize} MB!`)
      return false
    }
  }
  proxy.$modal.loading("正在上传文件，请稍候...")
  pendingFileSizes.set(file.uid, file.size)
  pendingBytes.value += file.size
  number.value++
  return true
}

// 文件个数超出
function handleExceed() {
  proxy.$modal.msgError(`上传文件数量不能超过 ${props.limit} 个!`)
}

// 上传失败
function handleUploadError(err, file) {
  releasePendingFile(file)
  number.value = Math.max(0, number.value - 1)
  proxy.$modal.msgError("上传文件失败")
  if (number.value === 0) proxy.$modal.closeLoading()
}

// 上传成功回调
function handleUploadSuccess(res, file) {
  releasePendingFile(file)
  number.value = Math.max(0, number.value - 1)
  if (res.code === 200) {
    const uploaded = {
      name: file?.name || res.originalFilename || res.fileName,
      url: normalizedStoredPath(res.fileName),
      previewUrl: normalizedStoredPath(res.previewFileName),
      thumbnailUrl: normalizedStoredPath(res.thumbnailFileName),
      size: Number(res.size || file?.size || 0)
    }
    fileList.value.push(uploaded)
    if (props.businessPreview) hydrateAuthorizedFile(uploaded)
    emit("update:modelValue", listToString(fileList.value))
  } else {
    proxy.$modal.msgError(res.msg)
    proxy.$refs.fileUpload.handleRemove(file)
  }
  if (number.value === 0) proxy.$modal.closeLoading()
}

// 删除文件
function handleDelete(index) {
  revokeObjectUrls(fileList.value[index])
  fileList.value.splice(index, 1)
  emit("update:modelValue", listToString(fileList.value))
}

// 上传结束处理
function releasePendingFile(file) {
  const size = pendingFileSizes.get(file?.uid) || 0
  pendingFileSizes.delete(file?.uid)
  pendingBytes.value = Math.max(0, pendingBytes.value - size)
}

function uploadWithConcurrency(options) {
  return new Promise((resolve, reject) => {
    requestQueue.push({ options, resolve, reject })
    drainRequestQueue()
  })
}

function drainRequestQueue() {
  const concurrency = Math.max(1, Number(props.maxConcurrency) || 1)
  while (activeRequests < concurrency && requestQueue.length) {
    const task = requestQueue.shift()
    activeRequests++
    performUpload(task.options).then(task.resolve, task.reject).finally(() => {
      activeRequests--
      drainRequestQueue()
    })
  }
}

async function performUpload(options) {
  const form = new FormData()
  Object.entries(options.data || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null) form.append(key, value)
  })
  form.append(options.filename || 'file', options.file)
  const response = await axios.post(options.action, form, {
    headers: { ...options.headers, 'Content-Type': 'multipart/form-data' },
    onUploadProgress: event => {
      if (event.total && options.onProgress) {
        options.onProgress({ percent: Math.round(event.loaded * 100 / event.total) })
      }
    }
  })
  return response.data
}

// 获取文件名称
function getFileName(name) {
  name = normalizedStoredPath(name).split('?')[0]
  // 如果是url那么取最后的名字 如果不是直接返回
  if (name.lastIndexOf("/") > -1) {
    name = name.slice(name.lastIndexOf("/") + 1)
  }
  try {
    return decodeURIComponent(name)
  } catch {
    return name
  }
}

function fileUrl(file) {
  if (file?.objectUrl) return file.objectUrl
  const url = normalizedStoredPath(file?.url || file?.name)
  if (!url || isExternal(url) || /^(data:|blob:|\/\/)/i.test(url)) return url
  if (!baseUrl) return url
  if (baseUrl.endsWith('/') && url.startsWith('/')) return baseUrl + url.slice(1)
  if (!baseUrl.endsWith('/') && !url.startsWith('/')) return `${baseUrl}/${url}`
  return baseUrl + url
}

function optimizedImagePath(file, kind) {
  const objectUrl = kind === 'thumb' ? file?.thumbnailObjectUrl : file?.previewObjectUrl
  if (objectUrl) return objectUrl
  const explicit = kind === 'thumb' ? file?.thumbnailUrl : file?.previewUrl
  if (explicit) return explicit
  const original = normalizedStoredPath(file?.url || file?.name)
  if (!props.businessPreview || !original || /^(data:|blob:)/i.test(original)) return original
  const queryIndex = original.indexOf('?')
  const path = queryIndex >= 0 ? original.slice(0, queryIndex) : original
  const query = queryIndex >= 0 ? original.slice(queryIndex) : ''
  const dotIndex = path.lastIndexOf('.')
  if (dotIndex < path.lastIndexOf('/')) return original
  return `${path.slice(0, dotIndex)}.${kind === 'thumb' ? 'thumb' : 'preview'}.webp${query}`
}

function imageCardUrl(file) {
  const original = fileUrl(file)
  if (failedOptimizedPreviews.value.has(normalizedStoredPath(file?.url || file?.name))) return original
  return absoluteFileUrl(optimizedImagePath(file, 'thumb'))
}

function imagePreviewUrl(file) {
  const original = fileUrl(file)
  if (failedOptimizedPreviews.value.has(normalizedStoredPath(file?.url || file?.name))) return original
  return absoluteFileUrl(optimizedImagePath(file, 'preview'))
}

function absoluteFileUrl(url) {
  return fileUrl({ url })
}

function markOptimizedPreviewFailed(file) {
  if (!props.businessPreview) return
  const next = new Set(failedOptimizedPreviews.value)
  next.add(normalizedStoredPath(file?.url || file?.name))
  failedOptimizedPreviews.value = next
  ensureOriginalObjectUrl(file)
}

function fileExtension(file) {
  const name = getFileName(file?.name || file?.url)
  const dotIndex = name.lastIndexOf('.')
  return dotIndex > -1 ? name.slice(dotIndex + 1).toUpperCase() : ''
}

function isImage(file) {
  return ['JPG', 'JPEG', 'PNG', 'GIF', 'WEBP', 'BMP', 'SVG', 'AVIF'].includes(fileExtension(file))
}

function isVideo(file) {
  return ['MP4', 'MOV', 'WEBM', 'OGG', 'M4V'].includes(fileExtension(file))
}

function isPdf(file) {
  return fileExtension(file) === 'PDF'
}

function fileCategory(file) {
  const extension = fileExtension(file)
  if (['DOC', 'DOCX', 'RTF'].includes(extension)) return 'word'
  if (['XLS', 'XLSX', 'CSV'].includes(extension)) return 'excel'
  if (['PPT', 'PPTX'].includes(extension)) return 'powerpoint'
  if (['ZIP', 'RAR', '7Z', 'TAR', 'GZ'].includes(extension)) return 'archive'
  return 'default'
}

function fileTypeLabel(file) {
  if (isImage(file)) return '图片'
  if (isVideo(file)) return '视频'
  if (isPdf(file)) return 'PDF 文档'
  const extension = fileExtension(file)
  return extension ? `${extension} 文件` : '附件'
}

function formatFileSize(size) {
  const bytes = Number(size)
  if (!Number.isFinite(bytes) || bytes <= 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function imagePreviewIndex(file) {
  return fileList.value.filter(isImage).findIndex(item => item.uid === file.uid || fileUrl(item) === fileUrl(file))
}

async function previewVideo(file) {
  const url = await ensureOriginalObjectUrl(file)
  if (!url) return proxy.$modal.msgError('附件加载失败')
  videoPreviewUrl.value = url
  videoPreviewVisible.value = true
}

async function openFile(file) {
  const tab = window.open('', '_blank')
  try {
    const url = await ensureOriginalObjectUrl(file)
    if (!url) throw new Error('附件加载失败')
    if (tab) tab.location.href = url
    else window.open(url, '_blank', 'noopener')
  } catch {
    if (tab) tab.close()
    proxy.$modal.msgError('附件加载失败或无权查看')
  }
}

function rawFileUrl(file) {
  const url = normalizedStoredPath(file?.url || file?.name)
  if (!url || isExternal(url) || /^(data:|blob:|\/\/)/i.test(url)) return url
  if (!baseUrl) return url
  if (baseUrl.endsWith('/') && url.startsWith('/')) return baseUrl + url.slice(1)
  if (!baseUrl.endsWith('/') && !url.startsWith('/')) return `${baseUrl}/${url}`
  return baseUrl + url
}

async function fetchAuthorizedBlob(url) {
  if (!url || /^(data:|blob:)/i.test(url)) return null
  const requestUrl = (baseUrl && String(url).startsWith(baseUrl)) || isExternal(url) ? url : absoluteFileUrl(url)
  const response = await axios.get(requestUrl, {
    responseType: 'blob',
    headers: { Authorization: `Bearer ${getToken()}` }
  })
  return URL.createObjectURL(response.data)
}

async function hydrateAuthorizedFile(file) {
  if (!props.businessPreview || !isImage(file) || file?._loadingPreview) return
  const original = normalizedStoredPath(file?.url || file?.name)
  if (!original.startsWith('/profile/')) return
  file._loadingPreview = true
  try {
    try { file.thumbnailObjectUrl = await fetchAuthorizedBlob(absoluteFileUrl(optimizedImagePath(file, 'thumb'))) } catch {}
    try { file.previewObjectUrl = await fetchAuthorizedBlob(absoluteFileUrl(optimizedImagePath(file, 'preview'))) } catch {}
    if (!file.thumbnailObjectUrl && !file.previewObjectUrl) await ensureOriginalObjectUrl(file)
  } catch {
    file.loadFailed = true
  } finally {
    file._loadingPreview = false
  }
}

async function ensureOriginalObjectUrl(file) {
  if (file?.objectUrl) return file.objectUrl
  if (file?._loadingOriginal) return file._loadingOriginal
  const original = normalizedStoredPath(file?.url || file?.name)
  if (!props.businessPreview || !original.startsWith('/profile/')) return rawFileUrl(file)
  file._loadingOriginal = fetchAuthorizedBlob(rawFileUrl(file)).then(url => {
    file.objectUrl = url
    return url
  }).finally(() => { file._loadingOriginal = null })
  return file._loadingOriginal
}

function hydrateAuthorizedFiles() {
  fileList.value.forEach(hydrateAuthorizedFile)
}

function revokeObjectUrls(file) {
  ;['objectUrl', 'thumbnailObjectUrl', 'previewObjectUrl'].forEach(key => {
    if (String(file?.[key] || '').startsWith('blob:')) URL.revokeObjectURL(file[key])
  })
}

// 对象转成指定字符串分隔
function listToString(list, separator) {
  let strs = ""
  separator = separator || ","
  for (let i in list) {
    if (list[i].url) {
      strs += normalizedStoredPath(list[i].url) + separator
    }
  }
  return strs != '' ? strs.substr(0, strs.length - 1) : ''
}

// 初始化拖拽排序
onMounted(() => {
  if (props.drag && !props.disabled) {
    nextTick(() => {
      const element = proxy.$refs.uploadFileList?.$el || proxy.$refs.uploadFileList
      Sortable.create(element, {
        ghostClass: 'file-upload-darg',
        onEnd: (evt) => {
          const movedItem = fileList.value.splice(evt.oldIndex, 1)[0]
          fileList.value.splice(evt.newIndex, 0, movedItem)
          emit('update:modelValue', listToString(fileList.value))
        }
      })
    })
  }
})

onBeforeUnmount(() => fileList.value.forEach(revokeObjectUrls))
</script>
<style scoped lang="scss">
.file-upload-darg {
  opacity: 0.5;
  transform: scale(0.98);
}
.upload-file {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}
.upload-file-uploader {
  margin-bottom: 5px;
}
.upload-file-tip {
  box-sizing: border-box;
  width: 100%;
  max-width: 760px;
  margin-top: 10px;
  padding: 10px 12px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-lighter);
  font-size: 13px;
  line-height: 20px;
  white-space: normal;
}
.upload-file-tip__summary {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 5px 14px;
}
.upload-file-tip__summary span {
  white-space: nowrap;
}
.upload-file-tip b {
  color: var(--el-color-danger);
  font-weight: 600;
}
.upload-file-tip__types {
  display: grid;
  min-width: 0;
  margin-top: 6px;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0 9px;
  align-items: start;
}
.upload-file-tip__label {
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.upload-file-tip__extensions {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
  word-break: break-word;
}
.upload-file-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(138px, 1fr));
  gap: 12px;
  max-width: 760px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}
.upload-file-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: 0 2px 8px rgb(31 45 61 / 5%);
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}
.upload-file-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 5px 16px rgb(31 45 61 / 11%);
}
.upload-file-card__preview {
  position: relative;
  height: 112px;
  overflow: hidden;
  background: var(--el-fill-color-light);
}
.upload-file-card__preview > .el-image,
.upload-file-card__preview > video,
.upload-file-card__preview > iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  object-fit: cover;
}
.upload-file-card__preview > iframe {
  pointer-events: none;
  background: #fff;
}
.file-type-tile {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 7px;
  color: #637381;
  background: linear-gradient(145deg, #f7f9fb, #edf1f4);
}
.file-type-tile .el-icon {
  font-size: 36px;
}
.file-type-tile strong {
  max-width: 88%;
  padding: 2px 7px;
  border-radius: 4px;
  color: #fff;
  background: #718096;
  font-size: 11px;
  line-height: 16px;
}
.file-type-tile.is-word strong { background: #3676c5; }
.file-type-tile.is-excel strong { background: #23825b; }
.file-type-tile.is-powerpoint strong { background: #cf5c36; }
.file-type-tile.is-archive strong { background: #8a67b3; }
.upload-file-card__cover {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(0 0 0 / 0%);
  transition: background 0.2s;
}
.upload-file-card__cover > a,
.upload-file-card__cover > button {
  display: flex;
  visibility: hidden;
  align-items: center;
  gap: 4px;
  padding: 7px 10px;
  border: 0;
  border-radius: 6px;
  color: #fff;
  background: rgb(0 0 0 / 64%);
  cursor: pointer;
  font: inherit;
  opacity: 0;
  text-decoration: none;
  transition: opacity 0.2s;
}
.upload-file-card__preview:hover .upload-file-card__cover {
  background: rgb(0 0 0 / 12%);
}
.upload-file-card__preview:hover .upload-file-card__cover > a,
.upload-file-card__preview:hover .upload-file-card__cover > button,
.upload-file-card__cover > a:focus-visible,
.upload-file-card__cover > button:focus-visible {
  visibility: visible;
  opacity: 1;
}
.upload-file-card__delete {
  position: absolute;
  z-index: 2;
  top: 6px;
  right: 6px;
  display: flex;
  width: 25px;
  height: 25px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid rgb(255 255 255 / 72%);
  border-radius: 50%;
  color: #fff;
  background: rgb(0 0 0 / 58%);
  cursor: pointer;
}
.upload-file-card__name {
  display: block;
  width: calc(100% - 20px);
  margin: 9px 10px 0;
  padding: 0;
  overflow: hidden;
  border: 0;
  color: var(--el-text-color-primary);
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-decoration: none;
  text-align: left;
}
.upload-file-card__name:hover {
  color: var(--el-color-primary);
}
.upload-file-card__meta {
  display: block;
  margin: 1px 10px 9px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.video-preview {
  display: block;
  width: 100%;
  max-height: 72vh;
  border-radius: 8px;
  background: #000;
}

@media (max-width: 520px) {
  .upload-file-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
