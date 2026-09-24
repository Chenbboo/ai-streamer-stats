<template>
  <div class="app-container assembly-page">
    <div class="page-head">
      <div>
        <h2>{{ $tr("组装管理") }}</h2>
        <p>{{ $tr("选择散件组装成品，审核员通过后自动完成库存转换") }}</p>
      </div>
      <el-button type="primary" icon="Plus" v-hasPermi="['jewelry:assembly:add']" @click="openCreate">{{ $tr("新建组装单") }}</el-button>
    </div>

    <el-form inline class="filters">
      <el-form-item><el-input v-model="query.docNo" :placeholder="$tr(&quot;组装单号&quot;)" clearable @keyup.enter="load"/></el-form-item>
      <el-form-item>
        <el-select v-model="query.status" :placeholder="$tr(&quot;全部状态&quot;)" clearable style="width:140px">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value"/>
        </el-select>
      </el-form-item>
      <el-form-item><el-button icon="Search" @click="load">{{ $tr("查询") }}</el-button></el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="docNo" :label="$tr(&quot;组装单号&quot;)" width="190"/>
      <el-table-column prop="bizDate" :label="$tr(&quot;业务日期&quot;)" width="120"/>
      <el-table-column prop="totalQty" :label="$tr(&quot;成品数量&quot;)" width="100" align="right"/>
      <el-table-column prop="totalCost" :label="$tr(&quot;组装总成本&quot;)" width="130" align="right">
        <template #default="{row}">{{row.totalCost==null?'—':`¥ ${money(row.totalCost)}`}}</template>
      </el-table-column>
      <el-table-column prop="creatorName" :label="$tr(&quot;制单人&quot;)" width="110"/>
      <el-table-column :label="$tr(&quot;状态&quot;)" width="110">
        <template #default="{row}"><el-tag :type="statusType(row.status)">{{labelOf(statuses,row.status)}}</el-tag></template>
      </el-table-column>
      <el-table-column prop="createTime" :label="$tr(&quot;创建时间&quot;)" min-width="165"/>
      <el-table-column :label="$tr(&quot;操作&quot;)" width="250" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="view(row)">{{ $tr("查看") }}</el-button>
          <el-button v-if="['DRAFT','REJECTED'].includes(row.status)" link type="primary" v-hasPermi="['jewelry:assembly:add']" @click="openEdit(row)">{{ $tr("编辑") }}</el-button>
          <el-button v-if="row.status==='DRAFT'" link type="success" v-hasPermi="['jewelry:document:submit']" @click="submit(row)">{{ $tr("提交审核") }}</el-button>
          <el-button v-if="row.status==='PENDING_FIRST'" link type="warning" v-hasPermi="['jewelry:document:withdraw']" @click="withdraw(row)">{{ $tr("撤回") }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>

    <el-dialog v-model="dialog" :title="form.documentId?$tr(&quot;编辑组装单&quot;):$tr(&quot;新建组装单&quot;)" width="1080px" top="4vh" destroy-on-close>
      <el-alert
        v-if="partProducts.length===0"
        :title="$tr(&quot;当前没有可用散件，请先在商品档案中将商品设为“散件商品”并上传实物图片。&quot;)"
        type="warning"
        :closable="false"
        show-icon
        class="mb16"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <section class="form-section">
          <div class="section-title"><span>01</span><div><b>{{ $tr("成品信息") }}</b><small>{{ $tr("本次组装后增加库存的成品") }}</small></div></div>
          <div class="top-grid">
            <el-form-item :label="$tr(&quot;业务日期&quot;)" prop="bizDate"><el-date-picker v-model="form.bizDate" type="date" value-format="YYYY-MM-DD"/></el-form-item>
            <el-form-item :label="$tr(&quot;成品来源&quot;)"><el-segmented v-model="form.outputMode" :options="outputModes" @change="outputModeChanged"/></el-form-item>
            <el-form-item :label="$tr(&quot;组装数量&quot;)" prop="outputQty"><el-input-number v-model="form.outputQty" :min="1" :precision="0"/></el-form-item>
          </div>
          <el-form-item v-if="form.outputMode==='EXISTING'" :label="$tr(&quot;目标成品&quot;)" prop="outputProductId">
            <el-select v-model="form.outputProductId" filterable :placeholder="$tr(&quot;选择成品SKU&quot;)" class="output-select">
              <el-option v-for="item in finishedProducts" :key="item.productId" :label="`${item.sku} · ${item.productName}`" :value="item.productId"/>
            </el-select>
          </el-form-item>
          <template v-else>
            <el-alert :title="$tr(&quot;保存组装草稿时会同步建立成品档案，初始库存为0；审核通过后才增加成品库存。&quot;)" type="info" :closable="false" show-icon class="mb16"/>
            <div class="new-product-grid">
              <el-form-item :label="$tr(&quot;新成品SKU&quot;)" prop="newOutputProduct.sku"><el-input v-model="form.newOutputProduct.sku" maxlength="64" :placeholder="$tr(&quot;请输入唯一SKU&quot;)"/></el-form-item>
              <el-form-item :label="$tr(&quot;新成品名称&quot;)" prop="newOutputProduct.productName"><el-input v-model="form.newOutputProduct.productName" maxlength="128"/></el-form-item>
              <el-form-item :label="$tr(&quot;单位&quot;)"><el-input v-model="form.newOutputProduct.unit" maxlength="16"/></el-form-item>
              <el-form-item :label="$tr(&quot;库存预警值&quot;)"><el-input-number v-model="form.newOutputProduct.warningQty" :min="0" :precision="0"/></el-form-item>
            </div>
          </template>
          <el-form-item :label="$tr(&quot;成品参考图&quot;)">
            <image-upload v-model="form.outputImages" :limit="5" :file-size="8"/>
          </el-form-item>
        </section>

        <section class="form-section">
          <div class="section-title"><span>02</span><div><b>{{ $tr("散件清单") }}</b><small>{{ $tr("填写本批组装实际消耗的总数量") }}</small></div></div>
          <el-table :data="form.components" border>
            <el-table-column type="index" label="#" width="48"/>
            <el-table-column :label="$tr(&quot;散件&quot;)" min-width="300">
              <template #default="{row}">
                <el-select v-model="row.productId" filterable @change="componentChanged(row)">
                  <el-option
                    v-for="item in partProducts"
                    :key="item.productId"
                    :value="item.productId"
                    :disabled="componentUsed(item.productId,row)"
                    :label="$tr(&quot;{0} · {1} · 可用{2}&quot;, [item.sku, item.productName, available(item)])"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$tr(&quot;图片&quot;)" width="88">
              <template #default="{row}">
                <el-image v-if="componentProduct(row)?.image" :src="imageSrc(componentProduct(row).image)" fit="cover" class="part-thumb" :preview-src-list="[imageSrc(componentProduct(row).image)]" preview-teleported/>
                <span v-else class="no-image">{{ $tr("无图") }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tr(&quot;可用库存&quot;)" width="100" align="right"><template #default="{row}">{{componentProduct(row)?.availableQty??'—'}}</template></el-table-column>
            <el-table-column :label="$tr(&quot;本次用量&quot;)" width="170">
              <template #default="{row}">
                <el-input-number v-model="row.qty" :min="1" :max="Math.max(1,componentProduct(row)?.availableQty||1)" :precision="0"/>
              </template>
            </el-table-column>
            <el-table-column width="60">
              <template #default="{$index}"><el-button link type="danger" icon="Delete" @click="removeComponent($index)"/></template>
            </el-table-column>
          </el-table>
          <el-button class="add-line" icon="Plus" :disabled="!partProducts.length" @click="form.components.push(blankComponent())">{{ $tr("添加散件") }}</el-button>
        </section>

        <section class="form-section fees-section">
          <div class="section-title"><span>03</span><div><b>{{ $tr("组装费用") }}</b><small>{{ $tr("费用总额将计入本批成品成本") }}</small></div></div>
          <div class="fee-grid">
            <el-form-item :label="$tr(&quot;人工费总额&quot;)"><el-input-number v-model="form.laborFee" :min="0" :precision="2"/></el-form-item>
            <el-form-item :label="$tr(&quot;加工费总额&quot;)"><el-input-number v-model="form.processingFee" :min="0" :precision="2"/></el-form-item>
            <el-form-item :label="$tr(&quot;其他费用总额&quot;)"><el-input-number v-model="form.otherFee" :min="0" :precision="2"/></el-form-item>
          </div>
          <el-form-item :label="$tr(&quot;备注&quot;)"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit/></el-form-item>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">{{ $tr("取消") }}</el-button>
        <el-button :loading="saving" @click="save(false)">{{ $tr("保存草稿") }}</el-button>
        <el-button type="primary" :loading="saving" v-hasPermi="['jewelry:document:submit']" @click="save(true)">{{ $tr("保存并提交审核") }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer" :title="$tr(&quot;组装单明细&quot;)" size="72%">
      <template v-if="detail">
        <el-descriptions :column="4" border>
          <el-descriptions-item :label="$tr(&quot;组装单号&quot;)">{{detail.docNo}}</el-descriptions-item>
          <el-descriptions-item :label="$tr(&quot;业务日期&quot;)">{{detail.bizDate}}</el-descriptions-item>
          <el-descriptions-item :label="$tr(&quot;成品数量&quot;)">{{detail.totalQty}}</el-descriptions-item>
          <el-descriptions-item :label="$tr(&quot;状态&quot;)">{{labelOf(statuses,detail.status)}}</el-descriptions-item>
          <el-descriptions-item :label="$tr(&quot;人工费&quot;)">¥ {{money(detail.laborFee)}}</el-descriptions-item>
          <el-descriptions-item :label="$tr(&quot;加工费&quot;)">¥ {{money(detail.processingFee)}}</el-descriptions-item>
          <el-descriptions-item :label="$tr(&quot;其他费用&quot;)">¥ {{money(detail.otherFee)}}</el-descriptions-item>
          <el-descriptions-item :label="$tr(&quot;组装总成本&quot;)">{{detail.totalCost==null?'—':`¥ ${money(detail.totalCost)}`}}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.items" border class="mt20">
          <el-table-column :label="$tr(&quot;用途&quot;)" width="100"><template #default="{row}"><el-tag :type="row.itemRole==='OUTPUT'?'success':'info'">{{row.itemRole==='OUTPUT'?$tr("成品产出"):$tr("散件投入")}}</el-tag></template></el-table-column>
          <el-table-column prop="skuSnapshot" label="SKU" min-width="140"/>
          <el-table-column prop="productNameSnapshot" :label="$tr(&quot;商品&quot;)" min-width="180"/>
          <el-table-column prop="qty" :label="$tr(&quot;数量&quot;)" width="90" align="right"/>
          <el-table-column prop="unitCost" :label="$tr(&quot;单位成本&quot;)" width="120" align="right"><template #default="{row}">{{row.unitCost==null?'—':money(row.unitCost)}}</template></el-table-column>
          <el-table-column prop="costAmount" :label="$tr(&quot;成本金额&quot;)" width="120" align="right"><template #default="{row}">{{row.costAmount==null?'—':money(row.costAmount)}}</template></el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="JewelryAssembly">
import { translateText } from '@/locales/translate'

import {listJewelryDocuments,getJewelryDocument,saveJewelryDocument,submitJewelryDocument,withdrawJewelryDocument,listJewelryProductOptions} from '@/api/jewelry/erp'
const {proxy}=getCurrentInstance()
const loading=ref(false),rows=ref([]),total=ref(0),dialog=ref(false),drawer=ref(false),saving=ref(false),formRef=ref(),detail=ref(null)
const products=ref([])
const statuses=[{value:'DRAFT',label:translateText("草稿")},{value:'PENDING_FIRST',label:translateText("待审核")},{value:'PENDING_SECOND',label:translateText("待审核")},{value:'POSTED',label:translateText("已入账")},{value:'REJECTED',label:translateText("已驳回")}]
const outputModes=[{label:translateText("选择已有成品"),value:'EXISTING'},{label:translateText("新建成品"),value:'NEW'}]
const query=reactive({pageNum:1,pageSize:10,docNo:'',docType:'ASSEMBLY',status:''})
const blankComponent=()=>({productId:null,itemRole:'COMPONENT',qty:1})
const blankNewProduct=()=>({sku:'',productName:'',unit:'件',warningQty:5})
const blankForm=()=>({documentId:null,bizDate:today(),outputMode:'EXISTING',outputProductId:null,newOutputProduct:blankNewProduct(),outputQty:1,outputImages:'',components:[blankComponent()],laborFee:0,processingFee:0,otherFee:0,remark:''})
const form=reactive(blankForm())
const rules={bizDate:[{required:true,message:translateText("请选择业务日期")}],outputProductId:[{required:true,message:translateText("请选择目标成品")}],'newOutputProduct.sku':[{required:true,message:translateText("请输入新成品SKU")}],'newOutputProduct.productName':[{required:true,message:translateText("请输入新成品名称")}],outputQty:[{required:true,message:translateText("请输入组装数量")}]}
const baseUrl=import.meta.env.VITE_APP_BASE_API
const imageSrc=url=>!url?'':/^https?:/i.test(url)?url:baseUrl+url
const firstImage=item=>String(item.imageUrl||item.imageUrls||'').split(',').map(v=>v.trim()).find(Boolean)||''
const available=item=>Number(item.onHandQty||0)-Number(item.reservedOutQty||0)
const partProducts=computed(()=>products.value.filter(item=>item.productType==='PART').map(item=>({...item,image:firstImage(item),availableQty:available(item)})))
const finishedProducts=computed(()=>products.value.filter(item=>item.productType==='FINISHED'))
const componentProduct=row=>partProducts.value.find(item=>item.productId===row.productId)
const componentUsed=(productId,current)=>form.components.some(item=>item!==current&&item.productId===productId)
const labelOf=(options,value)=>options.find(item=>item.value===value)?.label||value
const statusType=value=>value==='POSTED'?'success':value==='REJECTED'?'danger':value?.startsWith('PENDING')?'warning':'info'
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
function today(){const now=new Date(),offset=now.getTimezoneOffset()*60000;return new Date(now-offset).toISOString().slice(0,10)}
async function loadProducts(){const r=await listJewelryProductOptions({status:'0'});products.value=r.data||[]}
async function load(){loading.value=true;try{const r=await listJewelryDocuments(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
async function openCreate(){Object.assign(form,blankForm());await loadProducts();if(!finishedProducts.value.length)form.outputMode='NEW';dialog.value=true}
async function openEdit(row){
  const [detailResponse]=await Promise.all([getJewelryDocument(row.documentId),loadProducts()])
  const source=detailResponse.data
  const output=(source.items||[]).find(item=>item.itemRole==='OUTPUT')
  const components=(source.items||[]).filter(item=>item.itemRole==='COMPONENT')
  Object.assign(form,{
    documentId:source.documentId,
    bizDate:source.bizDate,
    outputMode:'EXISTING',
    outputProductId:output?.productId||null,
    newOutputProduct:blankNewProduct(),
    outputQty:output?.qty||1,
    outputImages:output?.imageUrls||'',
    components:components.length?components.map(item=>({productId:item.productId,itemRole:'COMPONENT',qty:item.qty})):[blankComponent()],
    laborFee:Number(source.laborFee||0),
    processingFee:Number(source.processingFee||0),
    otherFee:Number(source.otherFee||0),
    remark:source.remark||''
  })
  dialog.value=true
}
function outputModeChanged(){form.outputProductId=null;nextTick(()=>formRef.value?.clearValidate(['outputProductId','newOutputProduct.sku','newOutputProduct.productName']))}
function componentChanged(row){const item=componentProduct(row);if(item&&item.availableQty<=0){proxy.$modal.msgWarning(translateText("该散件当前没有可用库存"));row.productId=null}}
function removeComponent(index){if(form.components.length===1){Object.assign(form.components[0],blankComponent());return}form.components.splice(index,1)}
function validateComponents(){
  if(!form.components.length||form.components.some(item=>!item.productId||Number(item.qty)<=0)){proxy.$modal.msgError(translateText("请完整填写散件清单"));return false}
  const insufficient=form.components.find(item=>Number(item.qty)>Number(componentProduct(item)?.availableQty||0))
  if(insufficient){proxy.$modal.msgError(translateText("{0}可用库存不足", [componentProduct(insufficient)?.productName||translateText("散件")]));return false}
  return true
}
function payload(){
  const creating=form.outputMode==='NEW'
  const output={productId:creating?null:form.outputProductId,itemRole:'OUTPUT',qty:Number(form.outputQty),imageUrls:form.outputImages}
  const newOutputProduct=creating?{...form.newOutputProduct,imageUrls:String(form.outputImages||'').split(',')[0]||''}:null
  return {documentId:form.documentId,docType:'ASSEMBLY',bizDate:form.bizDate,laborFee:form.laborFee,processingFee:form.processingFee,otherFee:form.otherFee,remark:form.remark,newOutputProduct,items:[...form.components.map(item=>({...item})),output]}
}
async function save(andSubmit){
  await formRef.value.validate()
  if(!validateComponents())return
  saving.value=true
  try{
    if(andSubmit&&form.documentId){
      const current=(await getJewelryDocument(form.documentId)).data
      if(!['DRAFT','REJECTED'].includes(current.status)){
        proxy.$modal.msgSuccess(translateText("该组装单已经提交，无需重复操作"))
        dialog.value=false
        load()
        return
      }
    }
    const r=await saveJewelryDocument(payload())
    form.documentId=r.data.documentId
    const savedOutput=(r.data.items||[]).find(item=>item.itemRole==='OUTPUT')
    if(savedOutput?.productId){form.outputProductId=savedOutput.productId;form.outputMode='EXISTING';await loadProducts()}
    if(andSubmit){
      try{
        await submitJewelryDocument(form.documentId)
      }catch(error){
        proxy.$modal.msgWarning(translateText("组装草稿已保存，但提交结果未确认。请保留当前页面后重试，系统不会重复新建。"))
        return
      }
    }
    proxy.$modal.msgSuccess(andSubmit?translateText("组装单已提交审核"):translateText("组装草稿已保存"))
    dialog.value=false
    load()
  }finally{saving.value=false}
}
async function view(row){detail.value=(await getJewelryDocument(row.documentId)).data;drawer.value=true}
async function submit(row){await proxy.$modal.confirm(translateText("确认提交组装单 {0}？提交后将冻结所需散件。", [row.docNo]));await submitJewelryDocument(row.documentId);proxy.$modal.msgSuccess(translateText("已提交审核"));load()}
async function withdraw(row){await proxy.$modal.confirm(translateText("确认撤回组装单 {0}？冻结的散件将被释放。", [row.docNo]));await withdrawJewelryDocument(row.documentId);proxy.$modal.msgSuccess(translateText("已撤回"));load()}
loadProducts();load()
</script>

<style scoped>
.assembly-page{color:#1f2937}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head h2{margin:0 0 5px;font-size:22px;letter-spacing:0}.page-head p{margin:0;color:#718096}.filters{padding:12px 14px 0;border:1px solid #e2e7ed;background:#fafbfc}.mb16{margin-bottom:16px}.form-section{padding:2px 0 22px}.form-section+.form-section{padding-top:22px;border-top:1px solid #e3e7ec}.section-title{display:flex;align-items:center;gap:10px;margin-bottom:16px}.section-title>span{display:grid;width:28px;height:28px;place-items:center;border-radius:4px;background:#1f6fb2;color:#fff;font-weight:700}.section-title div{display:flex;flex-direction:column}.section-title b{font-size:16px}.section-title small{margin-top:2px;color:#8390a0}.top-grid{display:grid;grid-template-columns:180px minmax(300px,1fr) 180px;gap:16px}.top-grid :deep(.el-date-editor),.top-grid :deep(.el-select),.top-grid :deep(.el-input-number){width:100%}.part-thumb{width:56px;height:56px;border:1px solid #e0e5eb;border-radius:4px}.no-image{color:#c2413a;font-size:12px}.add-line{margin-top:12px}.fee-grid{display:grid;grid-template-columns:repeat(3,220px);gap:18px}.fee-grid :deep(.el-input-number){width:100%}@media(max-width:800px){.top-grid,.fee-grid{grid-template-columns:1fr}.page-head{align-items:flex-start;gap:12px}}
.top-grid :deep(.el-segmented){width:100%}.output-select{width:100%}.new-product-grid{display:grid;grid-template-columns:repeat(3,minmax(180px,1fr));gap:0 16px}.new-product-grid :deep(.el-input-number){width:100%}
@media(max-width:800px){.new-product-grid{grid-template-columns:1fr}}
</style>
