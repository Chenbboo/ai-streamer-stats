<template>
 <el-dialog v-model="opened" :title="tr('从飞书批量关联员工','Liên kết hàng loạt nhân sự Feishu')" width="min(1100px,96vw)" append-to-body :close-on-click-modal="false" :before-close="close">
  <el-alert :title="tr('读取应用已授权员工。姓名仅用于推荐，请核对本地账号后勾选；保存不会新建账号或授予权限。','Đọc nhân sự đã được ứng dụng cấp quyền. Tên chỉ dùng để gợi ý; xác minh tài khoản trước khi chọn. Không tạo tài khoản hoặc cấp quyền mới.')" type="info" :closable="false" show-icon/>
  <div class="batch-toolbar"><el-date-picker v-model="effectiveFrom" type="date" value-format="YYYY-MM-DD" :clearable="false" :disabled="busy" :aria-label="tr('关联生效日期','Ngày liên kết có hiệu lực')" @change="prepareRows"/><el-button :loading="reading" :disabled="saving" @click="readDirectory">{{ tr('重新读取名单','Đọc lại danh sách') }}</el-button><span>{{ tr('已读取','Đã đọc') }} {{ rows.length }} · {{ tr('已勾选','Đã chọn') }} {{ selected.length }}/50</span></div>
  <p>{{ tr('生效日期决定可同步的历史范围；需要补拉本月数据时，请选择月初。','Ngày hiệu lực quyết định phạm vi lịch sử; chọn đầu tháng nếu cần đồng bộ cả tháng.') }}</p>
  <el-alert v-if="readFailed" :title="tr('授权名单读取失败，未使用不完整结果。请检查飞书权限后重试。','Không đọc được đầy đủ danh sách. Kiểm tra quyền Feishu rồi thử lại.')" type="error" :closable="false"/>
  <el-table ref="table" :data="rows" height="390" v-loading="reading" row-key="externalUserId" @selection-change="selected=$event;confirmed=false" :empty-text="tr('暂无可用名单，请读取飞书授权范围','Chưa có danh sách, hãy đọc phạm vi Feishu')">
   <el-table-column type="selection" width="48" :selectable="row=>!busy&&!row.bound&&!row.unavailable"/>
   <el-table-column prop="name" :label="tr('飞书姓名','Tên Feishu')" width="130"/>
   <el-table-column prop="externalUserId" :label="tr('飞书员工标识','Mã nhân sự Feishu')" width="145"/>
   <el-table-column :label="tr('关联本地账号','Tài khoản nội bộ')" min-width="280"><template #default="{row}"><span v-if="row.bound">{{ row.bound.userName }} · {{ tr('已关联','Đã liên kết') }}</span><el-select v-else v-model="row.userId" @change="confirmed=false" filterable clearable :disabled="busy||row.unavailable" :placeholder="tr('请选择并核对账号','Chọn và xác minh tài khoản')" style="width:100%"><el-option v-for="p in localPeople" :key="p.userId" :value="p.userId" :label="`${p.userName} · ${p.loginName || p.userId} · ${p.departmentName || p.companyName || ''}`"/></el-select></template></el-table-column>
   <el-table-column :label="tr('核对提示','Gợi ý kiểm tra')" min-width="210"><template #default="{row}"><el-tag :type="row.bound?'success':row.unavailable?'danger':'info'">{{ row.bound?tr('已有有效期重叠的关联','Đã liên kết trong thời gian này'):row.unavailable?tr('飞书员工不可用','Nhân sự Feishu không khả dụng'):row.ambiguous?tr('存在重名，请手动选择','Trùng tên, cần chọn thủ công'):row.suggested?tr('同名候选，需人工确认','Gợi ý cùng tên, cần xác minh'):tr('未找到唯一同名账号','Không có tài khoản cùng tên duy nhất') }}</el-tag></template></el-table-column>
  </el-table>
  <el-checkbox v-model="confirmed" :disabled="busy">{{ tr('已逐项核对勾选员工的身份及关联生效日期','Đã xác minh danh tính và ngày hiệu lực cho từng nhân sự đã chọn') }}</el-checkbox>
  <template #footer><el-button :disabled="busy" @click="opened=false">{{ tr('取消','Hủy') }}</el-button><el-button type="primary" :loading="saving" :disabled="reading||!confirmed||!selected.length||selected.length>50||readFailed" @click="save">{{ tr('确认保存关联','Xác nhận lưu liên kết') }} ({{ selected.length }})</el-button></template>
 </el-dialog>
</template>
<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { mappingCandidates } from './matching.mjs'
import { getFeishuDirectory, batchFeishuMappings } from '@/api/business/feishu'
const {locale}=useI18n(),tr=(zh,vi)=>locale.value==='vi-VN'?vi:zh
const emit=defineEmits(['saved'])
const opened=ref(false),reading=ref(false),saving=ref(false),busy=computed(()=>reading.value||saving.value),readFailed=ref(false),connectionId=ref(null),effectiveFrom=ref(''),rows=ref([]),localPeople=ref([]),selected=ref([]),confirmed=ref(false),snapshot=ref(null),table=ref(null)
function close(done){if(!busy.value)done()}
async function open(id,from){connectionId.value=id;effectiveFrom.value=from;opened.value=true;snapshot.value=null;rows.value=[];localPeople.value=[];await readDirectory()}
async function readDirectory(){reading.value=true;readFailed.value=false;rows.value=[];snapshot.value=null;selected.value=[];confirmed.value=false;try{snapshot.value=(await getFeishuDirectory(connectionId.value)).data;localPeople.value=snapshot.value.people||[];prepareRows()}catch{readFailed.value=true}finally{reading.value=false}}
function prepareRows(){confirmed.value=false;selected.value=[];table.value?.clearSelection();if(!snapshot.value)return
 rows.value=mappingCandidates(snapshot.value,effectiveFrom.value)
}
async function save(){if(!confirmed.value||!selected.value.length||selected.value.length>50)return
 if(!effectiveFrom.value||selected.value.some(r=>!r.userId)||new Set(selected.value.map(r=>r.userId)).size!==selected.value.length)return ElMessage.warning(tr('请为勾选员工选择不同的本地账号，并填写生效日期','Chọn tài khoản khác nhau cho từng nhân sự và điền ngày hiệu lực'))
 saving.value=true
 try{const result=await batchFeishuMappings(connectionId.value,{confirmed:true,connectionVersion:snapshot.value.connectionVersion,effectiveFrom:effectiveFrom.value,items:selected.value.map(r=>({userId:r.userId,externalUserId:r.externalUserId}))});ElMessage.success(tr('已关联','Đã liên kết')+' '+result.data.createdCount);opened.value=false;emit('saved')}
 catch{confirmed.value=false}
 finally{saving.value=false}
}
defineExpose({open})
</script>
<style scoped>
.batch-toolbar{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:16px 0}.el-checkbox{margin-top:16px;max-width:100%;height:auto}:deep(.el-checkbox__label){white-space:normal}p{color:#637788;line-height:1.6}
</style>
