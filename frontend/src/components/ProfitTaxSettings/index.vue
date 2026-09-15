<template>
  <el-button v-if="canManage" @click="open">设置税率</el-button>
  <el-dialog v-model="visible" title="公司税率设置" width="min(620px,94vw)" append-to-body>
    <el-alert title="扣完业务成本、人员成本、奖金、公共费用和管理费后，仅对正的剩余利润计税。" type="info" :closable="false" show-icon />
    <el-alert v-if="loadFailed" title="税率读取失败，请重新打开后重试。" type="error" :closable="false" show-icon />
    <el-form v-loading="loading" label-width="95px" style="margin-top:20px">
      <el-form-item label="公司" required><el-select v-model="companyId" style="width:100%" @change="selectCompany"><el-option v-for="company in companies" :key="company.companyDeptId" :label="company.companyName" :value="company.companyDeptId" /></el-select></el-form-item>
      <el-form-item label="税率" required><el-input-number v-model="rate" :min="0" :max="100" :precision="4" :step="1" /> <span style="margin-left:8px">%</span></el-form-item>
      <el-form-item label="设置说明" required><el-input v-model="reason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
    </el-form>
    <p class="tax-note">税后盈利＝扣完全部费用后的利润－税额。新税率用于未结算项目；已结算项目保留当时的税率。未设置时暂按0%，0%也可以明确保存。</p>
    <el-empty v-if="!loading&&!companies.length" description="暂无可管理的公司" :image-size="60" />
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="loading||loadFailed||!companyId||rate==null||!reason.trim()" @click="save">保存税率</el-button></template>
  </el-dialog>
</template>
<script setup>
import { computed,ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
const emit=defineEmits(['changed']),user=useUserStore()
const canManage=computed(()=>user.roles.includes('company_owner')||user.permissions.includes('business:boss:view')||user.permissions.includes('*:*:*'))
const visible=ref(false),loading=ref(false),saving=ref(false),companies=ref([]),companyId=ref(null),rate=ref(null),reason=ref(''),version=ref(0)
const loadFailed=ref(false)
let loadSequence=0
function selectCompany(){const row=companies.value.find(c=>c.companyDeptId===companyId.value);rate.value=row?.taxRate==null?null:Number(row.taxRate);version.value=row?.version||0;reason.value=''}
async function open(){const sequence=++loadSequence;visible.value=true;loading.value=true;loadFailed.value=false;rate.value=null;reason.value='';companies.value=[];try{const res=await request({url:'/business/profit-tax/settings'});if(sequence!==loadSequence)return;companies.value=res.data||[];if(!companies.value.some(c=>c.companyDeptId===companyId.value))companyId.value=companies.value[0]?.companyDeptId||null;selectCompany()}catch{if(sequence===loadSequence)loadFailed.value=true}finally{if(sequence===loadSequence)loading.value=false}}
async function save(){if(saving.value||loading.value||loadFailed.value||rate.value==null||!reason.value.trim())return;saving.value=true;try{await request({url:`/business/profit-tax/settings/${companyId.value}`,method:'put',data:{taxRate:rate.value,version:version.value,reason:reason.value.trim()}});visible.value=false;ElMessage.success('税率已保存');emit('changed')}finally{saving.value=false}}
</script>
<style scoped>.tax-note{font-size:13px;color:#7d8795;line-height:1.7}</style>
