<template>
  <div class="app-container">
    <el-form inline>
      <el-form-item><el-input v-model="query.keyword" placeholder="编码、达人ID、名称或平台账号" clearable @keyup.enter="load"/></el-form-item>
      <el-form-item><el-select v-model="query.priceStatus" placeholder="全部定价状态" clearable style="width:170px"><el-option label="已有商品定价" value="PRICED"/><el-option label="有待生效价格" value="PENDING"/><el-option label="尚无商品价格" value="UNPRICED"/></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="load">查询</el-button></el-form-item>
    </el-form>
    <el-button type="primary" plain icon="Plus" class="mb8" v-hasPermi="['jewelry:influencer:add']" @click="openProfile()">新增达人/主播</el-button>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="influencerCode" label="达人编码" width="130"/>
      <el-table-column prop="externalInfluencerId" label="达人ID" min-width="140" show-overflow-tooltip/>
      <el-table-column prop="influencerName" label="达人/主播" min-width="160"/>
      <el-table-column prop="platform" label="平台" width="100"/>
      <el-table-column prop="platformAccount" label="平台账号" min-width="150"/>
      <el-table-column prop="salesChannel" label="默认销售渠道" width="140"/>
      <el-table-column label="商品关联" width="255"><template #default="{row}"><el-tag type="success">已定价 {{row.pricedProductCount||0}} 种</el-tag><el-tag v-if="Number(row.pendingProductCount||0)>0" type="warning" class="ml5">待生效 {{row.pendingProductCount}} 种</el-tag><el-tag v-if="Number(row.bundleItemCount||0)>0" type="info" class="ml5">搭售散件 {{row.bundleItemCount}} 种</el-tag></template></el-table-column>
      <el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.status==='0'?'success':'info'">{{row.status==='0'?'启用':'停用'}}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="300" fixed="right"><template #default="{row}">
        <el-button link type="primary" v-hasPermi="['jewelry:influencer:edit']" @click="openProfile(row)">编辑</el-button>
        <el-button link type="primary" @click="openProductPrices(row)">商品价格</el-button>
        <el-button link type="primary" @click="openBundleItems(row)">搭售散件</el-button>
        <el-button link type="primary" @click="openHistory(row)">价格历史</el-button>
      </template></el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>

    <el-dialog v-model="profileDialog" :title="profile.influencerId?'编辑达人/主播':'新增达人/主播'" width="620px">
      <el-form ref="profileRef" :model="profile" :rules="profileRules" label-width="110px">
        <el-form-item label="达人编码"><el-input :model-value="profile.influencerId ? profile.influencerCode : '保存后由系统自动生成'" disabled/></el-form-item>
        <el-form-item label="达人ID"><el-input v-model="profile.externalInfluencerId" placeholder="填写平台侧达人ID"/></el-form-item>
        <el-form-item label="达人/主播名称" prop="influencerName"><el-input v-model="profile.influencerName"/></el-form-item>
        <el-form-item label="平台"><el-input v-model="profile.platform" placeholder="例如：抖音"/></el-form-item>
        <el-form-item label="平台账号"><el-input v-model="profile.platformAccount"/></el-form-item>
        <el-form-item label="默认销售渠道"><el-input v-model="profile.salesChannel" placeholder="例如：抖音"/></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="profile.contactPhone"/></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="profile.status"><el-radio value="0">启用</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="备注"><el-input v-model="profile.remark" type="textarea"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="profileDialog=false">取消</el-button><el-button type="primary" @click="saveProfile">确定</el-button></template>
    </el-dialog>

    <el-dialog v-model="bundleItemDialog" :title="`${bundleItemName}－已绑定搭售散件`" width="1060px">
      <el-alert type="info" :closable="false" title="销售出库审核入账后，组合内的散件会自动绑定到该达人和主商品；这里记录搭售关系，不会把包含组合价的散件误记为有固定售价。" class="mb12"/>
      <el-table :data="bundleItemRows" border max-height="520">
        <el-table-column prop="mainSku" label="主商品SKU" width="150"/>
        <el-table-column prop="mainProductName" label="主商品" min-width="190" show-overflow-tooltip/>
        <el-table-column prop="addonSku" label="散件SKU" width="150"/>
        <el-table-column prop="addonProductName" label="搭售散件" min-width="190" show-overflow-tooltip/>
        <el-table-column label="搭售比例" width="110" align="center"><template #default="{row}">{{row.mainQty}} : {{row.addonQty}}</template></el-table-column>
        <el-table-column label="计价方式" width="125"><template #default="{row}">{{row.pricingMode==='INCLUDED'?'包含组合价':'单独计价'}}</template></el-table-column>
        <el-table-column prop="sourceDocNo" label="最近来源单据" width="180"/>
      </el-table>
      <el-empty v-if="!bundleItemRows.length" description="尚未绑定搭售散件" :image-size="70"/>
    </el-dialog>

    <el-dialog v-model="productPriceDialog" :title="`${productPriceName}－商品固定价`" width="920px">
      <el-alert type="info" :closable="false" title="每个达人按商品分别定价；待生效价格来自制单员保存的销售草稿，审核入账后转为正式固定价。" class="mb12"/>
      <el-table :data="productPriceRows" border max-height="520">
        <el-table-column prop="sku" label="SKU" width="150"/>
        <el-table-column prop="productName" label="商品名称" min-width="220" show-overflow-tooltip/>
        <el-table-column label="固定成交单价" width="145" align="right"><template #default="{row}">¥ {{priceText(row.fixedUnitPrice)}}</template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="row.priceStatus==='PRICED'?'success':'warning'">{{row.priceStatus==='PRICED'?'已生效':'待生效'}}</el-tag></template></el-table-column>
        <el-table-column prop="priceVersion" label="版本" width="70" align="center"/>
        <el-table-column label="来源单据" min-width="165"><template #default="{row}">{{row.priceStatus==='PENDING'?row.pendingSourceDocNo:row.priceSourceDocNo || '—'}}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{row}"><el-button v-if="row.priceStatus==='PRICED'" link type="warning" v-hasPermi="['jewelry:influencer:price']" @click="openPrice(row)">改价</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!productPriceRows.length" description="尚未关联任何商品价格" :image-size="70"/>
    </el-dialog>

    <el-dialog v-model="priceDialog" title="修改达人商品固定成交价" width="520px">
      <el-alert type="warning" :closable="false" title="改价只影响之后新建的销售和无原单退货，历史单据仍使用原价格快照。" class="mb12"/>
      <el-form ref="priceRef" :model="priceForm" :rules="priceRules" label-width="120px">
        <el-form-item label="达人/主播"><el-input :model-value="priceForm.influencerName" disabled/></el-form-item>
        <el-form-item label="商品"><el-input :model-value="`${priceForm.sku} · ${priceForm.productName}`" disabled/></el-form-item>
        <el-form-item label="当前固定价"><el-input :model-value="`¥ ${priceText(priceForm.oldPrice)}`" disabled/></el-form-item>
        <el-form-item label="新固定价" prop="fixedUnitPrice"><el-input-number v-model="priceForm.fixedUnitPrice" :min="0.0001" :precision="4" :step="0.0001" style="width:100%"/></el-form-item>
        <el-form-item label="改价原因" prop="reason"><el-input v-model="priceForm.reason" type="textarea" :rows="3"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="priceDialog=false">取消</el-button><el-button type="primary" @click="savePrice">确认改价</el-button></template>
    </el-dialog>

    <el-dialog v-model="historyDialog" :title="`${historyName}－商品价格历史`" width="980px">
      <el-table :data="historyRows" border>
        <el-table-column prop="sku" label="SKU" width="140"/>
        <el-table-column prop="productName" label="商品名称" min-width="180" show-overflow-tooltip/>
        <el-table-column prop="priceVersion" label="版本" width="70"/>
        <el-table-column label="原价" width="110" align="right"><template #default="{row}">{{row.oldPrice==null?'—':`¥ ${priceText(row.oldPrice)}`}}</template></el-table-column>
        <el-table-column label="新价" width="110" align="right"><template #default="{row}">¥ {{priceText(row.newPrice)}}</template></el-table-column>
        <el-table-column label="来源" width="110"><template #default="{row}">{{row.sourceType==='FIRST_SALE'?'首笔销售入账':'管理员改价'}}</template></el-table-column>
        <el-table-column prop="changeReason" label="原因" min-width="180"/>
        <el-table-column prop="operatorName" label="操作人" width="100"/>
        <el-table-column prop="createTime" label="时间" width="165"/>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup name="JewelryInfluencer">
import {listJewelryInfluencers,saveJewelryInfluencer,getJewelryInfluencerProductPrices,changeJewelryInfluencerPrice,getJewelryInfluencerPriceHistory,getJewelryInfluencerBundleItems} from '@/api/jewelry/erp'
const {proxy}=getCurrentInstance()
const rows=ref([]),total=ref(0),loading=ref(false),profileDialog=ref(false),productPriceDialog=ref(false),priceDialog=ref(false),historyDialog=ref(false),bundleItemDialog=ref(false)
const profileRef=ref(),priceRef=ref(),productPriceRows=ref([]),productPriceName=ref(''),activeInfluencerId=ref(null),historyRows=ref([]),historyName=ref(''),bundleItemRows=ref([]),bundleItemName=ref('')
const query=reactive({pageNum:1,pageSize:10,keyword:'',priceStatus:''})
const blankProfile=()=>({influencerId:null,influencerCode:'',externalInfluencerId:'',influencerName:'',platform:'',platformAccount:'',salesChannel:'',contactPhone:'',status:'0',remark:''})
const profile=reactive(blankProfile())
const priceForm=reactive({influencerId:null,productId:null,influencerName:'',sku:'',productName:'',oldPrice:0,fixedUnitPrice:0,reason:''})
const profileRules={influencerName:[{required:true,message:'请输入达人/主播名称'}]}
const priceRules={fixedUnitPrice:[{required:true,message:'请输入新固定价'}],reason:[{required:true,message:'请填写改价原因'}]}
const priceText=value=>Number(value||0).toFixed(4)
async function load(){loading.value=true;try{const r=await listJewelryInfluencers(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
function openProfile(row){Object.assign(profile,blankProfile(),row||{});profileDialog.value=true}
async function saveProfile(){await profileRef.value.validate();await saveJewelryInfluencer(profile);proxy.$modal.msgSuccess('保存成功');profileDialog.value=false;load()}
async function openProductPrices(row){activeInfluencerId.value=row.influencerId;productPriceName.value=row.influencerName;productPriceRows.value=(await getJewelryInfluencerProductPrices(row.influencerId)).data||[];productPriceDialog.value=true}
async function openBundleItems(row){bundleItemName.value=row.influencerName;bundleItemRows.value=(await getJewelryInfluencerBundleItems(row.influencerId)).data||[];bundleItemDialog.value=true}
function openPrice(row){Object.assign(priceForm,{influencerId:activeInfluencerId.value,productId:row.productId,influencerName:productPriceName.value,sku:row.sku,productName:row.productName,oldPrice:Number(row.fixedUnitPrice),fixedUnitPrice:Number(row.fixedUnitPrice),reason:''});priceDialog.value=true}
async function savePrice(){await priceRef.value.validate();await changeJewelryInfluencerPrice(priceForm.influencerId,priceForm.productId,{fixedUnitPrice:priceForm.fixedUnitPrice,reason:priceForm.reason});proxy.$modal.msgSuccess('商品固定价已更新');priceDialog.value=false;productPriceRows.value=(await getJewelryInfluencerProductPrices(activeInfluencerId.value)).data||[];load()}
async function openHistory(row){historyName.value=row.influencerName;historyRows.value=(await getJewelryInfluencerPriceHistory(row.influencerId)).data||[];historyDialog.value=true}
load()
</script>
