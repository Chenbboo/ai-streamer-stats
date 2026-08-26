<template>
  <div class="upload-file">
    <el-upload
      multiple
      :action="uploadFileUrl"
      :before-upload="handleBeforeUpload"
      :file-list="fileList"
      :data="data"
      :limit="limit"
      :on-error="handleUploadError"
      :on-exceed="handleExceed"
      :on-success="handleUploadSuccess"
      :show-file-list="false"
      :headers="headers"
      class="upload-file-uploader"
      ref="fileUpload"
      v-if="!disabled"
    >
      <!-- 上传按钮 -->
      <el-button type="primary">选取文件</el-button>
    </el-upload>
    <!-- 上传提示 -->
    <div class="el-upload__tip" v-if="showTip && !disabled">
      请上传
      <template v-if="fileSize"> 大小不超过 <b style="color: #f56c6c">{{ fileSize }}MB</b> </template>
      <template v-if="fileType"> 格式为 <b style="color: #f56c6c">{{ fileType.join("/") }}</b> </template>
      的文件
    </div>
    <!-- 文件缩略图列表 -->
    <transition-group ref="uploadFileList" class="upload-file-list" name="el-fade-in-linear" tag="ul">
      <li v-for="(file, index) in fileList" :key="file.uid" class="upload-file-card">
        <div class="upload-file-card__preview">
          <el-image
            v-if="isImage(file)"
            :src="fileUrl(file)"
            :preview-src-list="imagePreviewUrls"
            :initial-index="imagePreviewIndex(file)"
            fit="cover"
            preview-teleported
          >
            <template #error>
              <div class="file-type-tile">
                <el-icon><Picture /></el-icon>
                <strong>{{ fileExtension(file) || 'IMG' }}</strong>
              </div>
            </template>
          </el-image>
          <video v-else-if="isVideo(file)" :src="fileUrl(file)" preload="metadata" muted />
          <iframe
            v-else-if="isPdf(file)"
            :src="`${fileUrl(file)}#page=1&view=FitH&toolbar=0&navpanes=0`"
            title="PDF 文件缩略图"
            tabindex="-1"
          />
          <div v-else class="file-type-tile" :class="`is-${fileCategory(file)}`">
            <el-icon><Document /></el-icon>
            <strong>{{ fileExtension(file) || 'FILE' }}</strong>
          </div>

          <div v-if="!isImage(file)" class="upload-file-card__cover">
            <button v-if="isVideo(file)" type="button" aria-label="预览视频" @click="previewVideo(file)">
              <el-icon><VideoPlay /></el-icon><span>预览</span>
            </button>
            <a v-else :href="fileUrl(file)" target="_blank" rel="noopener" aria-label="打开附件">
              <el-icon><View /></el-icon><span>打开</span>
            </a>
          </div>

          <button v-if="!disabled" class="upload-file-card__delete" type="button" aria-label="删除文件" @click="handleDelete(index)">
            <el-icon><Close /></el-icon>
          </button>
        </div>

        <el-tooltip :content="getFileName(file.name || file.url)" placement="top" :show-after="500">
          <a class="upload-file-card__name" :href="fileUrl(file)" target="_blank" rel="noopener">
            {{ getFileName(file.name || file.url) }}
          </a>
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
  }
})

const { proxy } = getCurrentInstance()
const emit = defineEmits()
const number = ref(0)
const uploadList = ref([])
const baseUrl = import.meta.env.VITE_APP_BASE_API
const uploadFileUrl = ref(import.meta.env.VITE_APP_BASE_API + props.action) // 上传文件服务器地址
const headers = ref({ Authorization: "Bearer " + getToken() })
const fileList = ref([])
const videoPreviewVisible = ref(false)
const videoPreviewUrl = ref("")
const showTip = computed(
  () => props.isShowTip && (props.fileType || props.fileSize)
)
const imagePreviewUrls = computed(() => fileList.value.filter(isImage).map(fileUrl))

watch(() => props.modelValue, val => {
  if (val) {
    let temp = 1
    // 首先将值转为数组
    const list = Array.isArray(val) ? val : props.modelValue.split(',')
    // 然后将数组转为对象数组
    fileList.value = list.map(item => {
      if (typeof item === "string") {
        item = { name: item, url: item }
      }
      item.uid = item.uid || new Date().getTime() + temp++
      return item
    })
  } else {
    fileList.value = []
    return []
  }
},{ deep: true, immediate: true })

// 上传前校检格式和大小
function handleBeforeUpload(file) {
  // 校检文件类型
  if (props.fileType.length) {
    const fileName = file.name.split('.')
    const fileExt = fileName[fileName.length - 1]
    const isTypeOk = props.fileType.indexOf(fileExt) >= 0
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
    const isLt = file.size / 1024 / 1024 < props.fileSize
    if (!isLt) {
      proxy.$modal.msgError(`上传文件大小不能超过 ${props.fileSize} MB!`)
      return false
    }
  }
  proxy.$modal.loading("正在上传文件，请稍候...")
  number.value++
  return true
}

// 文件个数超出
function handleExceed() {
  proxy.$modal.msgError(`上传文件数量不能超过 ${props.limit} 个!`)
}

// 上传失败
function handleUploadError(err) {
  proxy.$modal.msgError("上传文件失败")
  proxy.$modal.closeLoading()
}

// 上传成功回调
function handleUploadSuccess(res, file) {
  if (res.code === 200) {
    uploadList.value.push({
      name: file?.name || res.originalFilename || res.fileName,
      url: res.fileName,
      size: file?.size
    })
    uploadedSuccessfully()
  } else {
    number.value--
    proxy.$modal.closeLoading()
    proxy.$modal.msgError(res.msg)
    proxy.$refs.fileUpload.handleRemove(file)
    uploadedSuccessfully()
  }
}

// 删除文件
function handleDelete(index) {
  fileList.value.splice(index, 1)
  emit("update:modelValue", listToString(fileList.value))
}

// 上传结束处理
function uploadedSuccessfully() {
  if (number.value > 0 && uploadList.value.length === number.value) {
    fileList.value = fileList.value.filter(f => f.url !== undefined).concat(uploadList.value)
    uploadList.value = []
    number.value = 0
    emit("update:modelValue", listToString(fileList.value))
    proxy.$modal.closeLoading()
  }
}

// 获取文件名称
function getFileName(name) {
  name = String(name || '').split('?')[0]
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
  const url = String(file?.url || file?.name || '')
  if (!url || isExternal(url) || /^(data:|blob:|\/\/)/i.test(url)) return url
  if (!baseUrl) return url
  if (baseUrl.endsWith('/') && url.startsWith('/')) return baseUrl + url.slice(1)
  if (!baseUrl.endsWith('/') && !url.startsWith('/')) return `${baseUrl}/${url}`
  return baseUrl + url
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

function previewVideo(file) {
  videoPreviewUrl.value = fileUrl(file)
  videoPreviewVisible.value = true
}

// 对象转成指定字符串分隔
function listToString(list, separator) {
  let strs = ""
  separator = separator || ","
  for (let i in list) {
    if (list[i].url) {
      strs += list[i].url + separator
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
</script>
<style scoped lang="scss">
.file-upload-darg {
  opacity: 0.5;
  transform: scale(0.98);
}
.upload-file-uploader {
  margin-bottom: 5px;
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
  margin: 9px 10px 0;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-decoration: none;
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
