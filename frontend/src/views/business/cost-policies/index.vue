<template>
  <div class="app-container rates-page">
    <header><div><span class="eyebrow">FINANCE · INTERNAL COST</span><h1>{{ t('title') }}</h1><p>{{ t('intro') }}</p></div><el-button icon="Refresh" :loading="loading" @click="loadPeople">{{ t('refresh') }}</el-button></header>
    <el-alert :title="t('boundary')" type="info" :closable="false" show-icon />
    <section class="rates-panel">
      <div class="toolbar"><el-select v-model="selectedUserId" filterable :placeholder="t('person')" style="width:min(440px,100%)" @change="loadPolicies"><el-option v-for="person in people" :key="person.userId" :value="person.userId" :label="`${person.nickName || person.userName} · ${person.companyName || ''}`" /></el-select><el-button v-if="canManage" type="primary" icon="Plus" @click="openCreate">{{ t('add') }}</el-button></div>
      <p v-if="selectedPerson" class="person-line"><b>{{ selectedPerson.nickName || selectedPerson.userName }}</b> · {{ selectedPerson.companyName }}</p>
      <el-empty v-if="!selectedUserId" :description="t('selectPerson')" />
      <el-alert v-else-if="!rawCostVisible" :title="t('forbidden')" type="warning" :closable="false" />
      <el-table v-else :data="policies" v-loading="loading" :empty-text="t('empty')">
        <el-table-column :label="t('version')" width="85"><template #default="{row}">v{{ row.policyVersion }}</template></el-table-column>
        <el-table-column :label="t('rate')" min-width="145"><template #default="{row}"><b>{{ money(row.unitCost) }} {{ row.currency }}</b><small>{{ modeLabel(row.costMode) }}</small></template></el-table-column>
        <el-table-column :label="t('basis')" min-width="190"><template #default="{row}"><template v-if="row.costMode !== 'HOURLY'">{{ row.rateMinutesPerDay || 480 }} {{ t('minutesPerRateDay') }}<small v-if="row.costMode==='MONTHLY'">{{ row.standardWorkDays ?? '—' }} {{ t('daysPerMonth') }}</small></template><span v-else>60 {{ t('minutesPerHour') }}</span></template></el-table-column>
        <el-table-column :label="t('period')" min-width="220"><template #default="{row}">{{ row.effectiveFrom }} → {{ row.effectiveTo || t('ongoing') }}</template></el-table-column>
        <el-table-column :label="t('status')" width="100"><template #default="{row}"><el-tag :type="row.status==='ACTIVE'?'success':'info'">{{ row.status==='ACTIVE'?t('active'):t('voided') }}</el-tag></template></el-table-column>
        <el-table-column :label="t('evidence')" min-width="220"><template #default="{row}">{{ row.remark || '—' }}<small>{{ t('references') }} {{ row.referenceCount || 0 }}</small><small v-if="row.voidReason">{{ row.voidedUserName }} · {{ row.voidReason }}</small></template></el-table-column>
        <el-table-column v-if="canManage" :label="t('action')" width="100" fixed="right"><template #default="{row}"><el-button v-if="row.status==='ACTIVE'" link :type="canDelete(row)?'danger':'warning'" :loading="retiring===row.policyId" @click="retire(row)">{{ canDelete(row)?t('delete'):t('void') }}</el-button></template></el-table-column>
      </el-table>
    </section>
    <el-dialog v-model="dialog" :title="t('newVersion')" width="min(620px,94vw)" append-to-body :close-on-click-modal="false">
      <el-alert :title="t('immutable')" type="info" :closable="false" />
      <el-form :model="form" label-position="top" class="rate-form">
        <div class="form-grid"><el-form-item :label="t('mode')"><el-select v-model="form.costMode"><el-option v-for="mode in ['MONTHLY','DAILY','HOURLY']" :key="mode" :value="mode" :label="modeLabel(mode)" /></el-select></el-form-item><el-form-item :label="t('currency')"><el-input v-model="form.currency" maxlength="3" placeholder="CNY / VND / USD" /></el-form-item></div>
        <el-form-item :label="t('unitCost')"><el-input-number v-model="form.unitCost" :min="0" :precision="4" :controls="false" style="width:100%" /></el-form-item>
        <div class="form-grid"><el-form-item v-if="form.costMode==='MONTHLY'" :label="t('standardDays')"><el-input-number v-model="form.standardWorkDays" :min="0.01" :max="31" :precision="2" :controls="false" /></el-form-item><el-form-item v-if="form.costMode!=='HOURLY'" :label="t('rateDayMinutes')"><el-input-number v-model="form.rateMinutesPerDay" :min="1" :max="1440" :precision="0" /></el-form-item></div>
        <p class="hint">{{ t('conversion') }}</p>
        <div class="form-grid"><el-form-item :label="t('from')"><el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item :label="t('to')"><el-date-picker v-model="form.effectiveTo" type="date" value-format="YYYY-MM-DD" clearable /></el-form-item></div>
        <el-form-item :label="t('remark')"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">{{ t('cancel') }}</el-button><el-button type="primary" :loading="saving" @click="save">{{ t('save') }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessCostPolicies">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import useUserStore from '@/store/modules/user'
import { getBusinessStaffCostOptions, getBusinessStaffCostPolicies, saveBusinessStaffCostPolicy, deleteBusinessStaffCostPolicy, voidBusinessStaffCostPolicy } from '@/api/business/staff'

const { t } = useI18n({ useScope:'local', messages:{
  'zh-CN':{title:'内部费率',intro:'按生效日期维护成本单价与计价单位，用于已确认工作的管理核算。',boundary:'内部费率不等于员工工资。工作日历决定容量，工作量单位决定分钟，费率人天基准决定计价；三者分别保存版本。',refresh:'刷新',person:'选择授权范围内的人员',add:'新增费率版本',selectPerson:'先选择人员查看费率历史',forbidden:'没有查看该人员内部费率的授权',empty:'尚未配置费率，确认投入后将保留待计价状态',version:'版本',rate:'内部费率',basis:'计价换算基准',minutesPerRateDay:'分钟 / 费率人天',daysPerMonth:'标准工作日 / 月',minutesPerHour:'分钟 / 小时',period:'生效区间',ongoing:'长期',status:'状态',active:'有效',voided:'已作废',evidence:'依据与历史',references:'来源引用',action:'操作',delete:'删除',void:'作废',newVersion:'新增内部费率版本',immutable:'已保存的费率以新版本变更。已确认的成本快照保留原依据；缺失费率补齐后可在核算页重试计价。',mode:'计价单位',MONTHLY:'每月',DAILY:'每人天',HOURLY:'每小时',currency:'币种',unitCost:'内部成本单价',standardDays:'月度标准工作天数',rateDayMinutes:'一个费率人天的分钟数',conversion:'费率人天基准独立于填报时选择的小时/人天显示单位，不随日历变化而改价。',from:'生效日期（含）',to:'失效日期（含，可选）',remark:'调整依据',cancel:'取消',save:'保存新版本',saved:'费率版本已保存',required:'请填写单价、币种、生效日期和调整依据',invalidDays:'请填写月度标准工作天数',deleteConfirm:'删除未生效且未被引用的费率版本？',voidPrompt:'请填写作废原因，已核算历史快照仍会保留',confirm:'确认',done:'处理完成'},
  vi:{title:'Đơn giá nội bộ',intro:'Quản lý đơn giá và đơn vị theo ngày hiệu lực để hạch toán công việc đã xác nhận.',boundary:'Đơn giá nội bộ không phải tiền lương. Lịch quyết định năng lực, đơn vị khối lượng quy đổi phút, cơ sở ngày của đơn giá quyết định định giá. Ba phiên bản được lưu riêng.',refresh:'Làm mới',person:'Chọn nhân sự trong phạm vi được phép',add:'Thêm phiên bản đơn giá',selectPerson:'Chọn nhân sự để xem lịch sử đơn giá',forbidden:'Không có quyền xem đơn giá của nhân sự này',empty:'Chưa có đơn giá; công việc đã xác nhận sẽ chờ định giá',version:'Phiên bản',rate:'Đơn giá nội bộ',basis:'Cơ sở quy đổi',minutesPerRateDay:'phút / ngày đơn giá',daysPerMonth:'ngày chuẩn / tháng',minutesPerHour:'phút / giờ',period:'Khoảng hiệu lực',ongoing:'Không giới hạn',status:'Trạng thái',active:'Hiệu lực',voided:'Đã hủy',evidence:'Căn cứ và lịch sử',references:'Số tham chiếu',action:'Thao tác',delete:'Xóa',void:'Hủy hiệu lực',newVersion:'Thêm phiên bản đơn giá nội bộ',immutable:'Thay đổi bằng phiên bản mới. Chi phí đã xác nhận giữ căn cứ cũ; bổ sung đơn giá rồi thử định giá lại trong hạch toán.',mode:'Đơn vị tính',MONTHLY:'Mỗi tháng',DAILY:'Mỗi ngày công',HOURLY:'Mỗi giờ',currency:'Tiền tệ',unitCost:'Đơn giá chi phí nội bộ',standardDays:'Số ngày chuẩn trong tháng',rateDayMinutes:'Số phút trong một ngày đơn giá',conversion:'Cơ sở ngày của đơn giá độc lập với đơn vị giờ/ngày khi ghi công và không thay đổi theo lịch.',from:'Ngày bắt đầu (bao gồm)',to:'Ngày kết thúc (tùy chọn)',remark:'Căn cứ điều chỉnh',cancel:'Hủy',save:'Lưu phiên bản mới',saved:'Đã lưu phiên bản đơn giá',required:'Điền đơn giá, tiền tệ, ngày hiệu lực và căn cứ',invalidDays:'Điền số ngày chuẩn trong tháng',deleteConfirm:'Xóa phiên bản chưa có hiệu lực và chưa được tham chiếu?',voidPrompt:'Nhập lý do hủy; ảnh chụp chi phí lịch sử vẫn được giữ',confirm:'Xác nhận',done:'Đã xử lý'}
} })
const route=useRoute(),user=useUserStore(),people=ref([]),policies=ref([]),selectedUserId=ref(null),loading=ref(false),dialog=ref(false),saving=ref(false),retiring=ref(null),form=reactive({})
const hasCostPermission=computed(()=>user.permissions.includes('*:*:*')||user.permissions.includes('business:staff:cost'))
const selectedPerson=computed(()=>people.value.find(p=>p.userId===selectedUserId.value))
const rawCostVisible=computed(()=>hasCostPermission.value&&selectedPerson.value?.rawCostVisible===true)
const canManage=computed(()=>rawCostVisible.value&&selectedPerson.value?.canManageCost===true)
const modeLabel=mode=>['MONTHLY','DAILY','HOURLY'].includes(mode)?t(mode):mode
const money=value=>value==null?'—':Number(value).toLocaleString(undefined,{maximumFractionDigits:4})
const today=()=>{const date=new Date();return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`}
let sequence=0
async function loadPeople(){if(!hasCostPermission.value)return;loading.value=true;try{people.value=(await getBusinessStaffCostOptions()).data||[];const requested=Number(route.query.userId)||selectedUserId.value;if(people.value.some(p=>p.userId===requested))selectedUserId.value=requested;else selectedUserId.value=people.value[0]?.userId||null;await loadPolicies()}finally{loading.value=false}}
async function loadPolicies(){const current=++sequence;policies.value=[];if(!rawCostVisible.value)return;loading.value=true;try{const response=await getBusinessStaffCostPolicies(selectedUserId.value);if(current===sequence)policies.value=response.data||[]}finally{if(current===sequence)loading.value=false}}
function openCreate(){Object.assign(form,{userId:selectedUserId.value,costMode:'MONTHLY',unitCost:null,currency:'CNY',standardWorkDays:null,rateMinutesPerDay:480,effectiveFrom:today(),effectiveTo:null,remark:''});dialog.value=true}
async function save(){if(!canManage.value)return;const body={...form,currency:form.currency?.trim().toUpperCase()};if(body.unitCost==null||!/^[A-Z]{3}$/.test(body.currency)||!body.effectiveFrom||!body.remark?.trim())return ElMessage.warning(t('required'));if(body.costMode==='MONTHLY'&&!(body.standardWorkDays>0))return ElMessage.warning(t('invalidDays'));saving.value=true;try{await saveBusinessStaffCostPolicy(body);dialog.value=false;ElMessage.success(t('saved'));await loadPolicies()}finally{saving.value=false}}
const canDelete=row=>row.status==='ACTIVE'&&row.effectiveFrom>today()&&!(Number(row.referenceCount)>0)
async function retire(row){if(!canManage.value)return;try{if(canDelete(row)){await ElMessageBox.confirm(t('deleteConfirm'),t('confirm'),{type:'warning',confirmButtonText:t('confirm'),cancelButtonText:t('cancel')});retiring.value=row.policyId;await deleteBusinessStaffCostPolicy(row.policyId)}else{const {value}=await ElMessageBox.prompt(t('voidPrompt'),t('void'),{inputType:'textarea',inputValidator:v=>!!v?.trim()&&v.length<=500,confirmButtonText:t('confirm'),cancelButtonText:t('cancel')});retiring.value=row.policyId;await voidBusinessStaffCostPolicy(row.policyId,value.trim())}ElMessage.success(t('done'));await loadPolicies()}catch(error){if(!['cancel','close'].includes(error))throw error}finally{retiring.value=null}}
onMounted(loadPeople)
</script>

<style scoped>
.rates-page{max-width:1500px;margin:auto}.rates-page header{display:flex;justify-content:space-between;align-items:center;gap:24px;margin-bottom:20px}.eyebrow{font-size:11px;letter-spacing:2px;color:#64827f}.rates-page h1{font-size:28px;margin:8px 0;color:#243e45}.rates-page header p,.hint{color:#74848d;line-height:1.7}.rates-panel{margin-top:22px;border:1px solid #e3e9ed;border-radius:12px;background:#fff;padding:22px}.toolbar{display:flex;justify-content:space-between;gap:14px}.person-line{color:#526971;margin:20px 0}.rates-page small{display:block;color:#89969f;font-size:12px;margin-top:5px}.rate-form{margin-top:20px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.form-grid .el-select,.form-grid :deep(.el-date-editor),.form-grid .el-input-number{width:100%}.hint{font-size:12px;margin-top:0}@media(max-width:650px){.rates-page header{align-items:flex-start}.rates-panel{padding:14px}.toolbar{flex-wrap:wrap}.form-grid{grid-template-columns:1fr;gap:0}}
</style>
