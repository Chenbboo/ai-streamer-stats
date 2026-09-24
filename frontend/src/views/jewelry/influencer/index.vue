<template>
  <div class="app-container">
    <el-form inline><el-form-item><el-input v-model="query.keyword" :placeholder="$tr(&quot;编码、达人ID、名称或平台账号&quot;)" clearable @keyup.enter="load"/></el-form-item><el-form-item><el-select v-model="query.priceStatus" :placeholder="$tr(&quot;全部定价状态&quot;)" clearable style="width:170px"><el-option :label="$tr(&quot;已有商品定价&quot;)" value="PRICED"/><el-option :label="$tr(&quot;有待生效价格&quot;)" value="PENDING"/><el-option :label="$tr(&quot;尚无商品价格&quot;)" value="UNPRICED"/></el-select></el-form-item><el-form-item><el-button type="primary" icon="Search" @click="load">{{ $tr("查询") }}</el-button></el-form-item></el-form>
    <el-button type="primary" plain icon="Plus" class="mb8" v-hasPermi="['jewelry:influencer:add']" @click="openProfile()">{{ $tr("新增达人/主播") }}</el-button>
    <el-table :data="rows" v-loading="loading" border><el-table-column prop="influencerCode" :label="$tr(&quot;达人编码&quot;)" width="125"/><el-table-column prop="externalInfluencerId" :label="$tr(&quot;达人ID&quot;)" min-width="130" show-overflow-tooltip/><el-table-column prop="influencerName" :label="$tr(&quot;达人/主播&quot;)" min-width="150"/><el-table-column prop="platform" :label="$tr(&quot;平台&quot;)" width="100"/><el-table-column prop="platformAccount" :label="$tr(&quot;平台账号&quot;)" min-width="140"/><el-table-column prop="salesChannel" :label="$tr(&quot;默认销售渠道&quot;)" width="140"/><el-table-column :label="$tr(&quot;商品关联&quot;)" width="225"><template #default="{row}"><el-tag type="success">{{ $tr('已定价 {0} 种', [row.pricedProductCount||0]) }}</el-tag><el-tag v-if="Number(row.pendingProductCount||0)>0" type="warning" class="ml5">{{ $tr('待生效 {0} 种', [row.pendingProductCount]) }}</el-tag></template></el-table-column><el-table-column :label="$tr(&quot;状态&quot;)" width="80"><template #default="{row}"><el-tag :type="row.status==='0'?'success':'info'">{{row.status==='0'?$tr('启用'):$tr('停用')}}</el-tag></template></el-table-column><el-table-column :label="$tr(&quot;操作&quot;)" width="90" fixed="right"><template #default="{row}"><el-button link type="primary" v-hasPermi="['jewelry:influencer:edit','jewelry:influencer:price']" @click="openProfile(row)">{{ canEditProfile?$tr("编辑"):$tr("商品绑定") }}</el-button></template></el-table-column></el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>
    <el-dialog v-model="profileDialog" :title="profile.influencerId?'编辑达人/主播':'新增达人/主播'" :width="profile.influencerId?'1180px':'650px'" destroy-on-close>
      <el-tabs v-model="activeTab"><el-tab-pane :label="$tr(&quot;基本信息&quot;)" name="basic"><el-form ref="profileRef" :model="profile" :rules="profileRules" label-width="125px" class="profile-form"><el-form-item :label="$tr(&quot;达人编码&quot;)"><el-input :model-value="profile.influencerId ? profile.influencerCode : $tr('{0}+流水号，保存后生成', [profile.platformCode||$tr('平台简写')])" disabled/></el-form-item><el-form-item :label="$tr(&quot;平台&quot;)" prop="platformCode"><el-select v-model="profile.platformCode" :disabled="!!profile.influencerId" :placeholder="$tr(&quot;请选择平台&quot;)" style="width:100%" @change="onPlatformChange"><el-option v-for="p in platforms" :key="p.platformCode" :label="`${p.platformName}（${p.platformCode}）`" :value="p.platformCode"/></el-select></el-form-item><el-form-item :label="$tr(&quot;达人ID&quot;)"><el-input v-model="profile.externalInfluencerId" :disabled="!!profile.influencerId&&!canEditProfile" :placeholder="$tr(&quot;平台侧达人ID&quot;)"/></el-form-item><el-form-item :label="$tr(&quot;达人/主播名称&quot;)" prop="influencerName"><el-input v-model="profile.influencerName" :disabled="!!profile.influencerId&&!canEditProfile"/></el-form-item><el-form-item :label="$tr(&quot;平台账号&quot;)"><el-input v-model="profile.platformAccount" :disabled="!!profile.influencerId&&!canEditProfile"/></el-form-item><el-form-item :label="$tr(&quot;默认销售渠道&quot;)"><el-input v-model="profile.salesChannel" :disabled="!!profile.influencerId&&!canEditProfile"/></el-form-item><el-form-item :label="$tr(&quot;联系电话&quot;)"><el-input v-model="profile.contactPhone" :disabled="!!profile.influencerId&&!canEditProfile"/></el-form-item><el-form-item :label="$tr(&quot;状态&quot;)"><el-radio-group v-model="profile.status" :disabled="!!profile.influencerId&&!canEditProfile"><el-radio value="0">{{ $tr("启用") }}</el-radio><el-radio value="1">{{ $tr("停用") }}</el-radio></el-radio-group></el-form-item><el-form-item :label="$tr(&quot;备注&quot;)"><el-input v-model="profile.remark" :disabled="!!profile.influencerId&&!canEditProfile" type="textarea"/></el-form-item></el-form><div v-if="!profile.influencerId||canEditProfile" class="tab-actions"><el-button type="primary" @click="saveProfile">{{ $tr("保存基本信息") }}</el-button></div></el-tab-pane>
      <template v-if="profile.influencerId"><el-tab-pane label="商品绑定" name="bindings"><el-alert title="可新建或绑定成品商品、赠品商品。直播价、成本价、供应商、采购单价、佣金及费用按达人分别保存；采购入库时按达人和供应商选择这些商品。" type="info" :closable="false" class="mb12"/><div class="toolbar" v-if="canPrice"><el-button type="primary" plain @click="openBinding()">录入商品/绑定</el-button><el-button @click="downloadTemplate">下载Excel模板</el-button><el-upload :show-file-list="false" :auto-upload="false" accept=".xlsx,.xls" :on-change="previewExcel"><el-button>批量Excel导入</el-button></el-upload></div><el-table :data="bindings" border max-height="440">
          <el-table-column prop="sku" label="商品SKU" width="140"/>
          <el-table-column prop="productName" label="商品名称" min-width="155" show-overflow-tooltip/>
          <el-table-column label="商品类型" width="105"><template #default="{row}">{{typeName(row.productType)}}</template></el-table-column>
          <el-table-column prop="preferredSupplierName" label="供应商名称" width="140" show-overflow-tooltip/>
          <el-table-column label="直播成交价" width="115" align="right"><template #default="{row}">{{money(row.fixedUnitPrice)}}</template></el-table-column>
          <el-table-column label="商品成本价" width="115" align="right"><template #default="{row}">{{money(row.unitCost)}}</template></el-table-column>
          <el-table-column label="采购单价" width="110" align="right"><template #default="{row}">{{money(row.referencePurchasePrice)}}</template></el-table-column>
          <el-table-column label="达人佣金率(%)" width="140" align="right"><template #default="{row}">{{percent(row.commissionRate)}}</template></el-table-column>
          <el-table-column label="平台扣点率(%)" width="140" align="right"><template #default="{row}">{{percent(row.platformRate)}}</template></el-table-column>
          <el-table-column label="税率(%)" width="100" align="right"><template #default="{row}">{{percent(row.taxRate)}}</template></el-table-column>
          <el-table-column label="包装费" width="100" align="right"><template #default="{row}">{{money(row.packFee)}}</template></el-table-column>
          <el-table-column label="物流费" width="100" align="right"><template #default="{row}">{{money(row.shipFee)}}</template></el-table-column>
          <el-table-column label="鉴定费" width="100" align="right"><template #default="{row}">{{money(row.certFee)}}</template></el-table-column>
          <el-table-column prop="unit" label="单位" width="80"/>
          <el-table-column label="图片" width="85"><template #default="{row}"><el-image v-if="row.imageUrls" :src="imageSrc(row.imageUrls)" :preview-src-list="[imageSrc(row.imageUrls)]" preview-teleported fit="contain" style="width:48px;height:48px"/><span v-else>—</span></template></el-table-column>
          <el-table-column prop="bindingRemark" label="备注" min-width="160" show-overflow-tooltip/>
          <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.priceStatus==='PENDING'?'warning':row.bindingStatus==='1'?'info':'success'">{{row.priceStatus==='PENDING'?'待生效':row.bindingStatus==='1'?'停用':'启用'}}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="75" fixed="right" v-if="canPrice"><template #default="{row}"><el-button v-if="['FINISHED','GIFT'].includes(row.productType)" link type="primary" :disabled="row.priceStatus==='PENDING'" @click="openBinding(row)">编辑</el-button></template></el-table-column>
        </el-table></el-tab-pane>
      <el-tab-pane label="价格历史" name="history"><el-table :data="history" border max-height="430"><el-table-column prop="sku" label="SKU" width="125"/><el-table-column prop="productName" label="商品" min-width="150"/><el-table-column prop="priceVersion" label="版本" width="70"/><el-table-column label="原价" width="110"><template #default="{row}">{{row.oldPrice==null?'—':money(row.oldPrice)}}</template></el-table-column><el-table-column label="新价" width="110"><template #default="{row}">{{money(row.newPrice)}}</template></el-table-column><el-table-column label="来源" width="110"><template #default="{row}">{{sourceName(row.sourceType)}}</template></el-table-column><el-table-column prop="changeReason" label="原因" min-width="160"/><el-table-column prop="operatorName" label="操作人" width="100"/><el-table-column prop="createTime" label="时间" width="165"/></el-table></el-tab-pane>
      </template></el-tabs><template #footer><el-button @click="profileDialog=false">关闭</el-button></template></el-dialog>
    <el-dialog v-model="bindingDialog" :title="binding.editing?'编辑商品绑定':'录入商品并绑定达人'" width="760px" append-to-body>
      <el-form label-width="125px" class="binding-form">
        <el-form-item v-if="!binding.editing" label="录入方式">
          <el-radio-group v-model="bindingMode"><el-radio value="new" :disabled="!canCreateProduct">新建商品档案</el-radio><el-radio value="existing">绑定已有商品</el-radio></el-radio-group>
        </el-form-item>
        <template v-if="bindingMode==='new' && !binding.editing">
          <el-form-item label="商品SKU" required><el-input v-model.trim="binding.sku" maxlength="64" placeholder="请填写商品SKU"/></el-form-item>
          <el-form-item label="商品名称" required><el-input v-model.trim="binding.productName" maxlength="128"/></el-form-item>
          <el-form-item label="商品类型" required><el-select v-model="binding.productType" style="width:100%"><el-option label="成品商品" value="FINISHED"/><el-option label="赠品商品" value="GIFT"/></el-select></el-form-item>
        </template>
        <el-form-item v-else label="商品" required>
          <el-select v-model="binding.productId" filterable :disabled="!!binding.editing" placeholder="选择已有成品或赠品商品" style="width:100%" @change="bindingProductChanged">
            <el-option v-for="p in bindingProducts" :key="p.productId" :label="`${p.sku} · ${p.productName}（${typeName(p.productType)}）`" :value="p.productId"/>
          </el-select>
        </el-form-item>
        <el-alert title="商品档案由所有达人共用；编辑此处只修改当前达人的价格和默认费用。采购单价和成本价会在录单时带入，请在提交前核对。" type="info" :closable="false" class="binding-note"/>
        <el-form-item label="供应商名称"><div class="supplier-picker">
          <el-select v-if="supplierMode==='existing'" v-model="binding.preferredSupplierId" filterable clearable placeholder="选择已有供应商" style="flex:1"><el-option v-for="s in suppliers" :key="s.supplierId" :label="`${s.supplierCode} · ${s.supplierName}`" :value="s.supplierId"/></el-select>
          <span v-else>新增供应商档案</span>
          <el-button v-if="canCreateSupplier" type="primary" plain @click="toggleSupplierMode">{{supplierMode==='existing'?'新增供应商':'选择已有'}}</el-button>
        </div></el-form-item>
        <template v-if="supplierMode==='new'">
          <el-form-item label="供应商编码" required><el-input v-model.trim="newSupplier.supplierCode" maxlength="32"/></el-form-item>
          <el-form-item label="供应商名称" required><el-input v-model.trim="newSupplier.supplierName" maxlength="128"/></el-form-item>
          <el-form-item label="联系人"><el-input v-model.trim="newSupplier.contactName" maxlength="64"/></el-form-item>
          <el-form-item label="联系电话"><el-input v-model.trim="newSupplier.contactPhone" maxlength="32"/></el-form-item>
          <el-form-item label="结算方式"><el-input v-model.trim="newSupplier.settlementType" maxlength="64"/></el-form-item>
          <el-form-item label="地址"><el-input v-model.trim="newSupplier.address" maxlength="255"/></el-form-item>
        </template>
        <el-form-item label="直播成交价" required><el-input-number v-model="binding.fixedUnitPrice" :min="binding.productType==='GIFT'?0:0.0001" :precision="4" style="width:100%"/></el-form-item>
        <el-form-item label="商品成本价" required><el-input-number v-model="binding.unitCost" :min="0" :precision="4" style="width:100%"/></el-form-item>
        <el-form-item label="采购单价"><el-input-number v-model="binding.referencePurchasePrice" :min="0" :precision="4" style="width:100%"/></el-form-item>
        <el-form-item label="达人佣金率(%)"><el-input-number v-model="binding.commissionPercent" :min="0" :max="100" :precision="4" style="width:100%"/></el-form-item>
        <el-form-item label="平台扣点率(%)"><el-input-number v-model="binding.platformPercent" :min="0" :max="100" :precision="4" style="width:100%"/></el-form-item>
        <el-form-item label="税率(%)"><el-input-number v-model="binding.taxPercent" :min="0" :max="100" :precision="4" style="width:100%"/></el-form-item>
        <el-form-item label="包装费"><el-input-number v-model="binding.packFee" :min="0" :precision="4" style="width:100%"/></el-form-item>
        <el-form-item label="物流费"><el-input-number v-model="binding.shipFee" :min="0" :precision="4" style="width:100%"/></el-form-item>
        <el-form-item label="鉴定费"><el-input-number v-model="binding.certFee" :min="0" :precision="4" style="width:100%"/></el-form-item>
        <el-form-item v-if="bindingMode==='new' && !binding.editing" label="单位" required><el-input v-model.trim="binding.unit" maxlength="16"/></el-form-item>
        <el-form-item v-if="bindingMode==='new' && !binding.editing" label="图片"><image-upload v-model="binding.imageUrls" :limit="1" :file-size="8"/></el-form-item>
        <el-form-item label="备注"><el-input v-model="binding.bindingRemark" type="textarea" :rows="2"/></el-form-item>
        <el-form-item label="绑定状态"><el-radio-group v-model="binding.bindingStatus"><el-radio value="0">启用</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="bindingDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveBinding">保存</el-button></template>
    </el-dialog>
    <BindingImportDialog v-model="excelDialog" :rows="excelRows" :products="bindingProducts" :suppliers="suppliers"
      :can-create-supplier="canCreateSupplier" :can-edit-product-image="canEditProductImage"
      :saving="saving" @submit="confirmExcel" />
  </div>
</template>
<script setup name="JewelryInfluencer">
import { translateText } from '@/locales/translate'
import { saveAs } from 'file-saver'
import { jewelryProductType } from '@/utils/jewelryProduct'
import { listJewelryProductOptions, listJewelrySuppliers, listJewelryInfluencers, listJewelryInfluencerPlatforms, saveJewelryInfluencer, getJewelryInfluencerProductPrices, saveJewelryInfluencerBindings, previewJewelryInfluencerBindings, confirmJewelryInfluencerBindings, getJewelryInfluencerPriceHistory } from '@/api/jewelry/erp'
import BindingImportDialog from './BindingImportDialog.vue'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
const { proxy } = getCurrentInstance()
const userStore = useUserStore()
const canEditProfile = computed(() => userStore.permissions?.includes('*:*:*') || userStore.permissions?.includes('jewelry:influencer:edit'))
const canPrice = computed(() => userStore.permissions?.includes('*:*:*') || userStore.permissions?.includes('jewelry:influencer:price'))
const canCreateProduct = computed(() => userStore.permissions?.includes('*:*:*') || userStore.permissions?.includes('jewelry:product:add'))
const canCreateSupplier = computed(() => userStore.permissions?.includes('*:*:*') || userStore.permissions?.includes('jewelry:supplier:add'))
const canEditProductImage = computed(() => userStore.permissions?.includes('*:*:*')
  || userStore.permissions?.includes('jewelry:product:edit')
  || userStore.permissions?.includes('jewelry:product:basic-edit'))
const rows=ref([]),total=ref(0),loading=ref(false),profileDialog=ref(false),bindingDialog=ref(false),excelDialog=ref(false),saving=ref(false)
const profileRef=ref(),activeTab=ref('basic'),platforms=ref([]),products=ref([]),suppliers=ref([]),bindings=ref([]),history=ref([]),excelRows=ref([]),bindingMode=ref('new')
const query=reactive({pageNum:1,pageSize:10,keyword:'',priceStatus:''})
const blankProfile=()=>({influencerId:null,influencerCode:'',platformCode:'',externalInfluencerId:'',influencerName:'',platform:'',platformAccount:'',salesChannel:'',contactPhone:'',status:'0',remark:''})
const profile=reactive(blankProfile())
const blankBinding=()=>({productId:null,editing:false,sku:'',productName:'',productType:'FINISHED',unit:'件',imageUrls:'',fixedUnitPrice:null,unitCost:null,preferredSupplierId:null,referencePurchasePrice:0,commissionPercent:0,platformPercent:0,taxPercent:0,packFee:0,shipFee:0,certFee:0,bindingStatus:'0',bindingRemark:''})
const binding=reactive(blankBinding())
const supplierMode=ref('existing')
const blankSupplier=()=>({supplierCode:'',supplierName:'',contactName:'',contactPhone:'',settlementType:'',address:''})
const newSupplier=reactive(blankSupplier())
const profileRules={influencerName:[{required:true,message:'请输入达人/主播名称'}],platformCode:[{required:true,message:'请选择平台'}]}
const bindingProducts=computed(()=>products.value.filter(p=>['FINISHED','GIFT'].includes(p.productType)))
const typeName=t=>jewelryProductType(t)?.label||t||'—'
const imageSrc=path=>path ? (path.startsWith('http') ? path : import.meta.env.VITE_APP_BASE_API + path) : ''
const money=n=>Number(n||0).toFixed(4)
const percent=n=>Number((Number(n||0)*100).toFixed(4))
const sourceName=t=>({FIRST_SALE:translateText('首笔销售'),ADMIN_CHANGE:translateText('管理员改价'),PROFILE_BINDING:translateText('档案绑定'),PROFILE_UPDATE:translateText('档案更新')}[t]||t||'—')
async function load(){loading.value=true;try{const r=await listJewelryInfluencers(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
function onPlatformChange(code){const p=platforms.value.find(x=>x.platformCode===code);profile.platform=p?.platformName||'';profile.salesChannel=profile.platform}
async function refreshDetails(){if(!profile.influencerId)return;const id=profile.influencerId;const [b,h]=await Promise.all([getJewelryInfluencerProductPrices(id),getJewelryInfluencerPriceHistory(id)]);bindings.value=b.data||[];history.value=h.data||[]}
async function openProfile(row){Object.assign(profile,blankProfile(),row||{});activeTab.value=row&&!canEditProfile.value&&canPrice.value?'bindings':'basic';profileDialog.value=true;const [p,g,s]=await Promise.all([listJewelryInfluencerPlatforms(),listJewelryProductOptions({}),canPrice.value?listJewelrySuppliers({pageNum:1,pageSize:500,status:'0'}).catch(()=>({rows:[]})):Promise.resolve({rows:[]})]);const allProducts=g.data||[];platforms.value=p.data||[];products.value=allProducts.filter(product=>product.status==='0');suppliers.value=s.rows||[];bindings.value=[];history.value=[];if(row)await refreshDetails()}
async function saveProfile(){await profileRef.value.validate();await saveJewelryInfluencer({...profile});proxy.$modal.msgSuccess('保存成功');profileDialog.value=false;load()}
function bindingProductChanged(id){const product=bindingProducts.value.find(p=>Number(p.productId)===Number(id));if(product){binding.productType=product.productType;if(binding.unitCost==null)binding.unitCost=Number(product.avgCost||0)}}
function openBinding(row){Object.assign(binding,blankBinding());Object.assign(newSupplier,blankSupplier());supplierMode.value='existing';bindingMode.value=row?'existing':canCreateProduct.value?'new':'existing';if(row)Object.assign(binding,{...row,editing:true,commissionPercent:percent(row.commissionRate),platformPercent:percent(row.platformRate),taxPercent:percent(row.taxRate),fixedUnitPrice:Number(row.fixedUnitPrice),unitCost:Number(row.unitCost||0),packFee:Number(row.packFee||0),shipFee:Number(row.shipFee||0),certFee:Number(row.certFee||0),referencePurchasePrice:Number(row.referencePurchasePrice||0)});bindingDialog.value=true}
function toggleSupplierMode(){supplierMode.value=supplierMode.value==='existing'?'new':'existing';if(supplierMode.value==='new')binding.preferredSupplierId=null}
async function saveBinding(){
  if(bindingMode.value==='new'&&!binding.editing){
    const sku=String(binding.sku||'').trim()
    if(!sku||!String(binding.productName||'').trim()||!String(binding.unit||'').trim())
      return proxy.$modal.msgError('请填写商品SKU、名称和单位')
    const occupied=products.value.find(p=>(p.productType===binding.productType || !['SAMPLE','GIFT'].includes(p.productType) && binding.productType==='FINISHED')
      &&String(p.sku||'').trim().toUpperCase()===sku.toUpperCase())
    if(occupied)return proxy.$modal.msgError(occupied.productType===binding.productType
      ?'该类型商品SKU已存在，请切换为绑定已有商品':'该SKU已被其他常规商品占用')
  }else if(!binding.productId||!bindingProducts.value.some(p=>Number(p.productId)===Number(binding.productId))){
    return proxy.$modal.msgError('请选择已有成品或赠品商品')
  }
  if(binding.fixedUnitPrice===null||Number(binding.fixedUnitPrice)<0||(binding.productType==='FINISHED'&&Number(binding.fixedUnitPrice)===0))
    return proxy.$modal.msgError('成品直播成交价须大于0，赠品可为0')
  if(binding.unitCost===null||binding.unitCost===undefined||Number(binding.unitCost)<0)
    return proxy.$modal.msgError('请填写不能小于0的商品成本价')
  if(Number(binding.referencePurchasePrice||0)<0)return proxy.$modal.msgError('采购单价不能小于0')
  if(Number(binding.commissionPercent)+Number(binding.platformPercent)+Number(binding.taxPercent)>=100)
    return proxy.$modal.msgError('佣金、平台扣点和税率之和必须小于100%')
  if(supplierMode.value==='new'){
    if(!canCreateSupplier.value)return proxy.$modal.msgError('新增供应商需要供应商新增权限')
    if(!String(newSupplier.supplierCode||'').trim()||!String(newSupplier.supplierName||'').trim())
      return proxy.$modal.msgError('请填写供应商编码和名称')
    if(suppliers.value.some(s=>String(s.supplierCode||'').trim().toUpperCase()===newSupplier.supplierCode.trim().toUpperCase()))
      return proxy.$modal.msgError('供应商编码已存在，请选择已有供应商')
  }
  saving.value=true
  try{
    const payload={...binding}
    if(bindingMode.value==='new'&&!binding.editing)Object.assign(payload,{productId:null,productType:binding.productType})
    if(supplierMode.value==='new')Object.assign(payload,{preferredSupplierId:null,newSupplier:{...newSupplier}})
    await saveJewelryInfluencerBindings(profile.influencerId,[payload])
    proxy.$modal.msgSuccess(supplierMode.value==='new'?'供应商档案和商品绑定已保存'
      :bindingMode.value==='new'&&!binding.editing?'商品档案和达人绑定已保存':'达人商品绑定已保存')
    bindingDialog.value=false
    const [productsResult,suppliersResult]=await Promise.all([
      listJewelryProductOptions({status:'0'}),
      listJewelrySuppliers({pageNum:1,pageSize:500,status:'0'}).catch(()=>({rows:suppliers.value}))
    ])
    products.value=productsResult.data||[]
    suppliers.value=suppliersResult.rows||[]
    await refreshDetails()
    load()
  }finally{saving.value=false}
}
async function downloadTemplate(){const blob=await request({url:'/jewelry/influencer/bindings/template',method:'get',responseType:'blob'});saveAs(blob,`${translateText('达人商品绑定模板')}.xlsx`)}
async function previewExcel(upload){
  const r=await previewJewelryInfluencerBindings(profile.influencerId,upload.raw)
  excelRows.value=r.data||[]
  excelDialog.value=true
  const invalid=excelRows.value.filter(row=>row.errors?.length).length
  if(invalid)proxy.$modal.msgWarning(`已导入 ${excelRows.value.length} 行，其中 ${invalid} 行有错误，请查看表格中的校验结果`)
}
async function confirmExcel(draftRows){saving.value=true;try{const r=await confirmJewelryInfluencerBindings(profile.influencerId,draftRows);if(!r.data?.saved){excelRows.value=r.data?.rows||[];proxy.$modal.msgWarning(translateText('仍有错误，请根据行内提示修改后再次提交'));return}proxy.$modal.msgSuccess(translateText('成功导入 {0} 行', [r.data.count]));excelDialog.value=false;const [productsResult,suppliersResult]=await Promise.all([listJewelryProductOptions({status:'0'}),listJewelrySuppliers({pageNum:1,pageSize:500,status:'0'})]);products.value=productsResult.data||[];suppliers.value=suppliersResult.rows||[];await refreshDetails();load()}finally{saving.value=false}}
load()
</script>
<style scoped>
.profile-form { max-width: 640px; }
.tab-actions { margin: 12px 0; padding-left: 125px; }
.toolbar { display:flex; align-items:center; gap:10px; margin: 12px 0; }
.binding-form { display:grid; grid-template-columns: 1fr 1fr; column-gap:16px; }
.binding-form .el-form-item:first-child, .binding-form .el-form-item:last-child { grid-column: 1 / -1; }
.binding-note { grid-column: 1 / -1; margin-bottom: 16px; }
.supplier-picker { display:flex; align-items:center; gap:8px; width:100%; }
.mb12 { margin-bottom:12px; }
</style>
