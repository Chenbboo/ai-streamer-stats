<template>
  <div class="app-container">
    <el-form inline><el-form-item><el-input v-model="query.keyword" :placeholder="$tr(&quot;姓名、工号或账号&quot;)" clearable @keyup.enter="load" /></el-form-item>
      <el-form-item><el-select v-model="query.status" :placeholder="$tr(&quot;全部状态&quot;)" clearable style="width:120px"><el-option :label="$tr(&quot;启用&quot;)" value="0"/><el-option :label="$tr(&quot;停用&quot;)" value="1"/></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="load">{{ $tr("查询") }}</el-button><el-button icon="Refresh" @click="reset">{{ $tr("重置") }}</el-button></el-form-item>
    </el-form>
    <el-button type="primary" plain icon="Plus" class="mb8" v-hasPermi="['jewelry:staff:add']" @click="open()">{{ $tr("新增ERP人员") }}</el-button>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="staffNo" :label="$tr(&quot;工号&quot;)" width="120"/><el-table-column prop="realName" :label="$tr(&quot;姓名&quot;)" min-width="120"/>
      <el-table-column prop="userName" :label="$tr(&quot;登录账号&quot;)" min-width="140"/><el-table-column prop="phone" :label="$tr(&quot;联系电话&quot;)" width="140"/>
      <el-table-column prop="roleName" :label="$tr(&quot;ERP角色&quot;)" width="130" :formatter="(row, column, value) => $tr(value)"/><el-table-column prop="joinedDate" :label="$tr(&quot;加入日期&quot;)" width="120"/>
      <el-table-column :label="$tr(&quot;状态&quot;)" width="90"><template #default="{row}"><el-tag :type="row.status==='0'?'success':'info'">{{row.status==='0'?$tr("启用"):$tr("停用")}}</el-tag></template></el-table-column>
      <el-table-column :label="$tr(&quot;操作&quot;)" width="100" fixed="right"><template #default="{row}"><el-button link type="primary" icon="Edit" v-hasPermi="['jewelry:staff:edit']" @click="open(row)">{{ $tr("编辑") }}</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
    <el-dialog v-model="dialog" :title="form.staffId?$tr(&quot;编辑ERP人员&quot;):$tr(&quot;新增ERP人员&quot;)" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item :label="$tr(&quot;工号&quot;)" prop="staffNo"><el-input v-model="form.staffNo" :disabled="!!form.staffId"/></el-form-item>
        <el-form-item :label="$tr(&quot;姓名&quot;)" prop="realName"><el-input v-model="form.realName"/></el-form-item>
        <el-form-item :label="$tr(&quot;登录账号&quot;)" prop="userName"><el-input v-model="form.userName" :disabled="!!form.staffId"/></el-form-item>
        <el-form-item v-if="!form.staffId" :label="$tr(&quot;初始密码&quot;)" prop="password"><el-input v-model="form.password" type="password" show-password/></el-form-item>
        <el-form-item :label="$tr(&quot;ERP角色&quot;)" prop="roleKey"><el-select v-model="form.roleKey" style="width:100%"><el-option :label="$tr(&quot;制单员&quot;)" value="jewelry_maker"/><el-option :label="$tr(&quot;审核员&quot;)" value="jewelry_reviewer"/><el-option :label="$tr(&quot;管理员&quot;)" value="jewelry_admin"/></el-select></el-form-item>
        <el-form-item :label="$tr(&quot;联系电话&quot;)"><el-input v-model="form.phone"/></el-form-item>
        <el-form-item :label="$tr(&quot;加入日期&quot;)"><el-date-picker v-model="form.joinedDate" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item :label="$tr(&quot;状态&quot;)"><el-radio-group v-model="form.status"><el-radio value="0">{{ $tr("启用") }}</el-radio><el-radio value="1">{{ $tr("停用") }}</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" @click="save">{{ $tr("确定") }}</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup name="JewelryStaff">
import { translateText } from '@/locales/translate'

import { listJewelryStaff, addJewelryStaff, updateJewelryStaff } from '@/api/jewelry/erp'
const { proxy } = getCurrentInstance(); const loading=ref(false),rows=ref([]),total=ref(0),dialog=ref(false),formRef=ref()
const query=reactive({pageNum:1,pageSize:10,keyword:'',status:''})
const blank=()=>({staffId:null,userId:null,staffNo:'',realName:'',userName:'',password:'',roleKey:'jewelry_maker',phone:'',joinedDate:'',status:'0'})
const form=reactive(blank()); const rules={staffNo:[{required:true,message:translateText("请输入工号")}],realName:[{required:true,message:translateText("请输入姓名")}],userName:[{required:true,message:translateText("请输入登录账号")}],password:[{required:true,min:6,message:translateText("密码至少6位")}],roleKey:[{required:true,message:translateText("请选择角色")}]}
async function load(){loading.value=true;try{const r=await listJewelryStaff(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
function reset(){Object.assign(query,{pageNum:1,keyword:'',status:''});load()}
function open(row){Object.assign(form,blank(),row||{});dialog.value=true}
async function save(){await formRef.value.validate();if(form.staffId)await updateJewelryStaff(form);else await addJewelryStaff(form);proxy.$modal.msgSuccess(translateText("保存成功"));dialog.value=false;load()}
load()
</script>
