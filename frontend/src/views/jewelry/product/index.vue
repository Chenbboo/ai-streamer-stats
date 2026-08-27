<template>
  <div class="app-container">
    <el-form inline>
      <el-form-item><el-input v-model="query.keyword" placeholder="SKU或商品名称" clearable @keyup.enter="load"/></el-form-item>
      <el-form-item>
        <el-segmented v-model="query.productType" :options="typeFilters" @change="load"/>
      </el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="load">查询</el-button></el-form-item>
      <el-form-item><el-button icon="Download" v-hasPermi="['jewelry:product:list']" @click="handleExport">导出 Excel</el-button></el-form-item>
    </el-form>
    <el-button type="primary" plain icon="Plus" class="mb8" v-hasPermi="['jewelry:product:add']" @click="open()">新增商品</el-button>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column label="图片" width="76">
        <template #default="{row}">
          <el-image v-if="firstImage(row)" :src="imageSrc(firstImage(row))" fit="cover" class="product-thumb"
            :preview-src-list="allImages(row).map(imageSrc)" preview-teleported/>
          <div v-else class="empty-thumb"><el-icon><Picture/></el-icon></div>
        </template>
      </el-table-column>
      <el-table-column prop="sku" label="SKU" min-width="160" show-overflow-tooltip/>
      <el-table-column prop="productName" label="商品名称" min-width="240" show-overflow-tooltip/>
      <el-table-column label="商品类型" width="110">
        <template #default="{row}"><el-tag :type="typeTag(row.productType)" effect="plain">{{typeLabel(row.productType)}}</el-tag></template>
      </el-table-column>
      <el-table-column prop="category" label="分类" min-width="130" show-overflow-tooltip/>
      <el-table-column prop="specification" label="规格类型" width="100"/>
      <el-table-column prop="unit" label="单位" width="70"/>
      <el-table-column prop="onHandQty" label="可售库存" width="100" align="right"/>
      <el-table-column v-if="canViewFinance" prop="avgCost" label="平均成本" width="110" align="right"/>
      <el-table-column prop="warningQty" label="预警值" width="90" align="right"/>
      <el-table-column label="状态" width="80"><template #default="{row}">{{row.status==='0'?'启用':'停用'}}</template></el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{row}"><el-button link type="primary" icon="Edit" v-hasPermi="['jewelry:product:edit','jewelry:product:basic-edit']" @click="open(row)">编辑</el-button></template>
      </el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>

    <el-dialog v-model="dialog" :title="form.productId?(limitedProductEdit?'修改商品名称和图片':'编辑商品'):'新增商品'" width="720px" destroy-on-close>
      <el-alert v-if="limitedProductEdit" title="制单员可直接修改商品名称和实物图片，其他商品资料仅管理员可修改。"
        type="info" :closable="false" show-icon class="mb16"/>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="14">
          <el-col :span="12"><el-form-item label="SKU" prop="sku"><el-input v-model="form.sku" :disabled="!!form.productId"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="商品名称" prop="productName"><el-input v-model="form.productName"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="商品类型" prop="productType"><el-select v-model="form.productType" :disabled="limitedProductEdit" style="width:100%"><el-option v-for="item in jewelryProductTypes" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="分类"><el-input v-model="form.category" :disabled="limitedProductEdit"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="规格类型" prop="specification"><el-select v-model="form.specification" :disabled="limitedProductEdit" style="width:100%"><el-option v-for="item in jewelrySpecifications" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单位"><el-input v-model="form.unit" :disabled="limitedProductEdit"/></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="预警值"><el-input-number v-model="form.warningQty" :disabled="limitedProductEdit" :min="0" style="width:100%"/></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="状态"><el-select v-model="form.status" :disabled="limitedProductEdit"><el-option label="启用" value="0"/><el-option label="停用" value="1"/></el-select></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="实物图片">
              <image-upload v-model="form.imageUrls" :limit="1" :file-size="8"/>
              <div class="field-tip">每个商品仅保留一张实物图，散件建议上传清晰图片，便于组装时核对。</div>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="包装费"><el-input-number v-model="form.defaultPackFee" :disabled="limitedProductEdit" :min="0" :precision="2" style="width:100%"/></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="物流费"><el-input-number v-model="form.defaultShipFee" :disabled="limitedProductEdit" :min="0" :precision="2" style="width:100%"/></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="鉴定费"><el-input-number v-model="form.defaultCertFee" :disabled="limitedProductEdit" :min="0" :precision="2" style="width:100%"/></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="JewelryProduct">
import { listJewelryProducts, saveJewelryProduct } from '@/api/jewelry/erp'
import useUserStore from '@/store/modules/user'
import { jewelryProductTypes, jewelrySpecifications, jewelryProductType } from '@/utils/jewelryProduct'
const userStore=useUserStore()
const canViewFinance=computed(()=>userStore.roles.some(role=>['admin','jewelry_admin','jewelry_reviewer'].includes(role)))
const canFullProductEdit=computed(()=>userStore.permissions.some(permission=>['*:*:*','jewelry:product:edit'].includes(permission)))
const {proxy}=getCurrentInstance()
const loading=ref(false),rows=ref([]),total=ref(0),dialog=ref(false),formRef=ref()
const typeFilters=[{label:'全部',value:''},...jewelryProductTypes.map(({label,value})=>({label,value}))]
const query=reactive({pageNum:1,pageSize:10,keyword:'',productType:''})
const blank=()=>({productId:null,sku:'',productName:'',productType:'FINISHED',category:'',specification:'普通',imageUrl:'',imageUrls:'',unit:'件',warningQty:5,status:'0',defaultPackFee:0,defaultShipFee:0,defaultCertFee:0})
const form=reactive(blank())
const limitedProductEdit=computed(()=>Boolean(form.productId)&&!canFullProductEdit.value)
const rules={sku:[{required:true,message:'请输入SKU'}],productName:[{required:true,message:'请输入商品名称'}],productType:[{required:true,type:'enum',enum:jewelryProductTypes.map(item=>item.value),message:'请选择商品类型'}],specification:[{required:true,type:'enum',enum:jewelrySpecifications.map(item=>item.value),message:'请选择规格类型'}]}
const baseUrl=import.meta.env.VITE_APP_BASE_API
const allImages=row=>String(row.imageUrls||row.imageUrl||'').split(',').map(v=>v.trim()).filter(Boolean)
const firstImage=row=>allImages(row)[0]||''
const imageSrc=url=>/^https?:/i.test(url)?url:baseUrl+url
const typeLabel=value=>jewelryProductType(value)?.label||value||'—'
const typeTag=value=>jewelryProductType(value)?.tagType||'info'
async function load(){loading.value=true;try{const params={...query};if(!params.productType)delete params.productType;const r=await listJewelryProducts(params);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
function handleExport(){const params={keyword:query.keyword,productType:query.productType};if(!params.productType)delete params.productType;proxy.download('/jewelry/product/export',params,`商品档案_${new Date().getTime()}.xlsx`)}
function open(row){Object.assign(form,blank(),row||{});form.imageUrls=form.imageUrls||form.imageUrl||'';dialog.value=true}
async function save(){await formRef.value.validate();form.imageUrl=String(form.imageUrls||'').split(',')[0]||'';await saveJewelryProduct(form);proxy.$modal.msgSuccess('保存成功');dialog.value=false;load()}
load()
</script>

<style scoped>
.product-thumb{width:48px;height:48px;border:1px solid #dfe4ea;border-radius:4px}.empty-thumb{display:grid;width:48px;height:48px;place-items:center;border:1px dashed #c8d0da;color:#a7b0bd}.field-tip{margin-top:6px;color:#8490a0;font-size:12px}
</style>
