<template>
  <div class="app-container">
    <el-form inline><el-form-item><el-input v-model="query.keyword" placeholder="姓名、工号或账号" clearable @keyup.enter="load" /></el-form-item>
      <el-form-item><el-select v-model="query.status" placeholder="全部状态" clearable style="width:120px"><el-option label="启用" value="0"/><el-option label="停用" value="1"/></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="load">查询</el-button><el-button icon="Refresh" @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-button type="primary" plain icon="Plus" class="mb8" v-hasPermi="['jewelry:staff:add']" @click="open()">新增ERP人员</el-button>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="staffNo" label="工号" width="120"/><el-table-column prop="realName" label="姓名" min-width="120"/>
      <el-table-column prop="userName" label="登录账号" min-width="140"/><el-table-column prop="phone" label="联系电话" width="140"/>
      <el-table-column prop="roleName" label="ERP角色" width="130"/><el-table-column prop="joinedDate" label="加入日期" width="120"/>
      <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='0'?'success':'info'">{{row.status==='0'?'启用':'停用'}}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="100" fixed="right"><template #default="{row}"><el-button link type="primary" icon="Edit" v-hasPermi="['jewelry:staff:edit']" @click="open(row)">编辑</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
    <el-dialog v-model="dialog" :title="form.staffId?'编辑ERP人员':'新增ERP人员'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="工号" prop="staffNo"><el-input v-model="form.staffNo" :disabled="!!form.staffId"/></el-form-item>
        <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName"/></el-form-item>
        <el-form-item label="登录账号" prop="userName"><el-input v-model="form.userName" :disabled="!!form.staffId"/></el-form-item>
        <el-form-item v-if="!form.staffId" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password/></el-form-item>
        <el-form-item label="ERP角色" prop="roleKey"><el-select v-model="form.roleKey" style="width:100%"><el-option label="制单员" value="jewelry_maker"/><el-option label="审核员" value="jewelry_reviewer"/><el-option label="管理员" value="jewelry_admin"/></el-select></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone"/></el-form-item>
        <el-form-item label="加入日期"><el-date-picker v-model="form.joinedDate" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio value="0">启用</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">确定</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup name="JewelryStaff">
import { listJewelryStaff, addJewelryStaff, updateJewelryStaff } from '@/api/jewelry/erp'
const { proxy } = getCurrentInstance(); const loading=ref(false),rows=ref([]),total=ref(0),dialog=ref(false),formRef=ref()
const query=reactive({pageNum:1,pageSize:10,keyword:'',status:''})
const blank=()=>({staffId:null,userId:null,staffNo:'',realName:'',userName:'',password:'',roleKey:'jewelry_maker',phone:'',joinedDate:'',status:'0'})
const form=reactive(blank()); const rules={staffNo:[{required:true,message:'请输入工号'}],realName:[{required:true,message:'请输入姓名'}],userName:[{required:true,message:'请输入登录账号'}],password:[{required:true,min:6,message:'密码至少6位'}],roleKey:[{required:true,message:'请选择角色'}]}
async function load(){loading.value=true;try{const r=await listJewelryStaff(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
function reset(){Object.assign(query,{pageNum:1,keyword:'',status:''});load()}
function open(row){Object.assign(form,blank(),row||{});dialog.value=true}
async function save(){await formRef.value.validate();if(form.staffId)await updateJewelryStaff(form);else await addJewelryStaff(form);proxy.$modal.msgSuccess('保存成功');dialog.value=false;load()}
load()
</script>
