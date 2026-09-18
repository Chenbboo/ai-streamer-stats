<template>
  <div class="app-container">
    <el-form inline>
      <el-form-item><el-input v-model="query.keyword" placeholder="SKU或商品名称" clearable @keyup.enter="handleQuery"/></el-form-item>
      <el-form-item>
        <el-segmented v-model="query.productType" :options="typeFilters" @change="handleQuery"/>
      </el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="handleQuery">查询</el-button></el-form-item>
      <el-form-item><el-button icon="Download" v-hasPermi="['jewelry:product:list']" @click="handleExport">导出 Excel</el-button></el-form-item>
    </el-form>
    <el-button type="primary" plain icon="Plus" class="mb8" v-hasPermi="['jewelry:product:add']" @click="open()">新增商品</el-button>
    <el-button v-if="canBatchEdit" type="success" plain icon="Edit" class="mb8" :disabled="loading || !selectedRows.length" @click="openBatch">批量编辑</el-button>
    <el-button v-if="canDelete" type="danger" plain icon="Delete" class="mb8" :disabled="loading || !selectedRows.length" :loading="deleteSaving" @click="removeProducts(selectedRows)">批量删除</el-button>
    <span v-if="canBatchEdit || canDelete" class="selection-tip">已选 {{selectedRows.length}} 件（仅当前页）</span>
    <el-table ref="productTable" v-loading="loading || deleteSaving" :data="rows" row-key="productId" border @selection-change="selectionChanged">
      <el-table-column v-if="canBatchEdit || canDelete" type="selection" width="48" :selectable="()=>!loading && !deleteSaving"/>
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
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" icon="Edit" v-hasPermi="['jewelry:product:edit','jewelry:product:basic-edit']" @click="open(row)">编辑</el-button>
          <el-button v-if="canDelete" link type="danger" icon="Delete" :disabled="deleteSaving" @click="removeProducts([row])">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>

    <el-dialog v-model="batchDialog" title="批量编辑商品" width="760px" destroy-on-close
      :close-on-click-modal="false" :close-on-press-escape="!batchSaving" :show-close="!batchSaving">
      <el-alert :title="`将统一修改选中的 ${batchRows.length} 件商品。只修改勾选字段，未勾选的资料保持不变。`"
        type="warning" :closable="false" show-icon class="mb16"/>
      <el-table :data="batchRows" border max-height="160" class="mb16">
        <el-table-column prop="sku" label="已选SKU" min-width="160"/>
        <el-table-column prop="productName" label="商品名称" min-width="240"/>
      </el-table>
      <div v-if="!canFullProductEdit" class="field-tip mb16">当前账号仅可统一修改商品名称和实物图片，无需审批。</div>
      <el-form label-width="140px" :disabled="batchSaving">
        <el-form-item v-for="field in batchFields" :key="field.key">
          <template #label><el-checkbox :model-value="batchSelectedFields.includes(field.key)" @change="checked=>toggleBatchField(field.key,checked)">{{field.label}}</el-checkbox></template>
          <el-select v-if="field.kind==='select'" v-model="batchValues[field.key]" :disabled="!batchSelectedFields.includes(field.key)" placeholder="请选择统一值" style="width:100%">
            <el-option v-for="option in field.options" :key="option.value" :label="option.label" :value="option.value"/>
          </el-select>
          <el-input-number v-else-if="field.kind==='number'" v-model="batchValues[field.key]" :disabled="!batchSelectedFields.includes(field.key)" :min="0" :max="field.max" :precision="field.precision" style="width:100%"/>
          <template v-else-if="field.kind==='image'">
            <image-upload v-if="batchSelectedFields.includes(field.key) && !batchSaving" v-model="batchValues.imageUrls" :limit="1" :file-size="8"/>
            <div class="field-tip">勾选后所有选中商品将使用同一图片；不上传图片表示清空原图片。</div>
          </template>
          <el-input v-else v-model="batchValues[field.key]" :maxlength="field.max" :disabled="!batchSelectedFields.includes(field.key)" placeholder="填写统一值"/>
          <div v-if="field.key==='productName'" class="field-tip">勾选后所有选中商品将使用同一名称，SKU保持不变。</div>
          <div v-if="field.key==='category'" class="field-tip">勾选并留空表示清空分类。</div>
        </el-form-item>
      </el-form>
      <template #footer><el-button :disabled="batchSaving" @click="batchDialog=false">取消</el-button><el-button type="primary" :loading="batchSaving" :disabled="!batchSelectedFields.length" @click="saveBatch">保存 {{batchRows.length}} 件商品</el-button></template>
    </el-dialog>

    <el-dialog v-model="dialog" :title="form.productId?(limitedProductEdit?'修改商品名称和图片':'编辑商品'):'新增商品'" width="720px" destroy-on-close>
      <el-alert v-if="limitedProductEdit" title="当前账号仅可直接修改商品名称和实物图片。"
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
import { listJewelryProducts, saveJewelryProduct, batchUpdateJewelryProducts, deleteJewelryProducts } from '@/api/jewelry/erp'
import useUserStore from '@/store/modules/user'
import { jewelryProductTypes, jewelrySpecifications, jewelryProductType } from '@/utils/jewelryProduct'
import { productBatchFields, buildProductBatchRequest } from '@/utils/jewelryProductBatch'
const userStore=useUserStore()
const canViewFinance=computed(()=>userStore.roles.some(role=>['admin','jewelry_admin','jewelry_reviewer'].includes(role)))
const canFullProductEdit=computed(()=>userStore.permissions.some(permission=>['*:*:*','jewelry:product:edit'].includes(permission)))
const canBatchEdit=computed(()=>canFullProductEdit.value||userStore.permissions.includes('jewelry:product:basic-edit'))
const canDelete=computed(()=>userStore.permissions.some(permission=>['*:*:*','jewelry:product:remove'].includes(permission)))
const deleteSaving=ref(false)
async function removeProducts(selection){
  if(deleteSaving.value || !selection.length)return
  if(selection.length>200){proxy.$modal.msgError('单次最多删除200件商品');return}
  const targets=selection.map(row=>({productId:row.productId,sku:row.sku}))
  deleteSaving.value=true
  try{
    const names=targets.slice(0,5).map(row=>row.sku).join('、')+(targets.length>5?'等':'')
    await proxy.$modal.confirm(`确定永久删除 ${targets.length} 件商品（${names}）？此操作不可恢复。仅允许删除从未使用、所有库存均为零且没有达人关联的商品；任一商品不符合条件，整批不删除，请改用停用。`)
    await deleteJewelryProducts(targets.map(row=>row.productId))
    proxy.$modal.msgSuccess(`已删除 ${targets.length} 件商品`)
    await load()
  }catch(error){/* Cancel keeps selection; the shared client displays API errors. */}
  finally{deleteSaving.value=false}
}
const productTable=ref(),selectedRows=ref([]),batchDialog=ref(false),batchSaving=ref(false),batchRows=ref([]),batchSelectedFields=ref([])
const batchValues=reactive({})
const batchFields=computed(()=>productBatchFields.filter(field=>canFullProductEdit.value||field.basic))
function selectionChanged(selection){selectedRows.value=selection}
function toggleBatchField(key,checked){batchSelectedFields.value=checked?[...batchSelectedFields.value,key]:batchSelectedFields.value.filter(value=>value!==key)}
function openBatch(){
  if(!selectedRows.value.length)return
  batchRows.value=selectedRows.value.map(row=>({...row}))
  batchSelectedFields.value=[]
  for(const field of productBatchFields)batchValues[field.key]=field.kind==='number'?undefined:''
  batchDialog.value=true
}
async function saveBatch(){
  if(batchSaving.value)return
  let payload
  try{payload=buildProductBatchRequest(batchRows.value,batchSelectedFields.value,batchValues,canFullProductEdit.value)}
  catch(error){proxy.$modal.msgError(error.message);return}
  const summary=Object.entries(payload.changes).map(([key,value])=>{
    const field=productBatchFields.find(field=>field.key===key)
    const display=field.options?.find(option=>option.value===value)?.label??(key==='imageUrls'?(value?'统一替换图片':'清空图片'):(value===''?'清空':String(value)))
    return `${field.label}：${display}`
  }).join('；')
  batchSaving.value=true
  try{
    await proxy.$modal.confirm(`确定修改这 ${payload.productIds.length} 件商品？${summary}。未勾选字段保持不变。`)
    await batchUpdateJewelryProducts(payload)
    proxy.$modal.msgSuccess(`已更新 ${payload.productIds.length} 件商品`)
    batchDialog.value=false
    await load()
  }catch(error){/* Cancellation keeps the form; request errors are displayed by the shared client. */}
  finally{batchSaving.value=false}
}
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
function handleQuery(){query.pageNum=1;load()}
let loadSequence=0
async function load(){const sequence=++loadSequence;loading.value=true;selectedRows.value=[];productTable.value?.clearSelection();try{const params={...query};if(!params.productType)delete params.productType;const r=await listJewelryProducts(params);if(sequence===loadSequence){rows.value=r.rows||[];total.value=r.total||0}}finally{if(sequence===loadSequence)loading.value=false}}
function handleExport(){const params={keyword:query.keyword,productType:query.productType};if(!params.productType)delete params.productType;proxy.download('/jewelry/product/export',params,`商品档案_${new Date().getTime()}.xlsx`)}
function open(row){Object.assign(form,blank(),row||{});form.imageUrls=form.imageUrls||form.imageUrl||'';dialog.value=true}
async function save(){await formRef.value.validate();form.imageUrl=String(form.imageUrls||'').split(',')[0]||'';await saveJewelryProduct(form);proxy.$modal.msgSuccess('保存成功');dialog.value=false;load()}
load()
</script>

<style scoped>
.selection-tip{margin-left:12px;color:#8490a0;font-size:13px}
.product-thumb{width:48px;height:48px;border:1px solid #dfe4ea;border-radius:4px}.empty-thumb{display:grid;width:48px;height:48px;place-items:center;border:1px dashed #c8d0da;color:#a7b0bd}.field-tip{margin-top:6px;color:#8490a0;font-size:12px}
</style>
