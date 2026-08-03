<template>
  <div class="app-container">
    <el-form inline><el-form-item><el-input v-model="query.docNo" placeholder="单号" clearable/></el-form-item><el-form-item><el-select v-model="query.docType" placeholder="全部类型" clearable style="width:150px"><el-option v-for="o in types" :key="o.value" :label="o.label" :value="o.value"/></el-select></el-form-item><el-form-item><el-select v-model="query.status" placeholder="全部状态" clearable style="width:140px"><el-option v-for="o in statuses" :key="o.value" :label="o.label" :value="o.value"/></el-select></el-form-item><el-form-item><el-button type="primary" icon="Search" @click="load">查询</el-button></el-form-item></el-form>
    <el-button type="primary" plain icon="Plus" class="mb8" v-hasPermi="['jewelry:document:add']" @click="open()">新建单据</el-button>
    <el-table :data="rows" v-loading="loading" border><el-table-column prop="docNo" label="单号" width="190"/><el-table-column label="类型" width="130"><template #default="{row}">{{labelOf(types,row.docType)}}</template></el-table-column><el-table-column prop="bizDate" label="业务日期" width="110"/><el-table-column label="业务对象" min-width="130"><template #default="{row}">{{row.supplierNameSnapshot || row.salesChannel || (row.docType==='ASSEMBLY'?'手工组装':'—')}}</template></el-table-column><el-table-column prop="totalQty" label="数量" width="80" align="right"/><el-table-column prop="totalAmount" label="金额" width="110" align="right"/><el-table-column v-if="canViewFinance" prop="totalProfit" label="毛利" width="110" align="right"><template #default="{row}"><span :class="{loss:Number(row.totalProfit)<0}">{{money(row.totalProfit)}}</span></template></el-table-column><el-table-column v-if="canViewFinance" label="风险" width="100"><template #default="{row}"><el-tag v-if="row.riskStatus==='LOSS'" type="danger">亏损</el-tag><el-tag v-else-if="row.riskStatus==='REVIEW'" type="warning">需复核</el-tag><span v-else>—</span></template></el-table-column><el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusType(row.status)">{{labelOf(statuses,row.status)}}</el-tag></template></el-table-column><el-table-column prop="creatorName" label="制单人" width="100"/><el-table-column label="操作" width="240" fixed="right"><template #default="{row}"><el-button link type="primary" @click="view(row)">查看</el-button><el-button v-if="['DRAFT','REJECTED'].includes(row.status) && !['REVERSAL','ASSEMBLY'].includes(row.docType)" link type="primary" v-hasPermi="['jewelry:document:edit']" @click="edit(row)">编辑</el-button><el-button v-if="row.status==='DRAFT'" link type="success" v-hasPermi="['jewelry:document:submit']" @click="submit(row)">提交</el-button><el-button v-if="row.status==='PENDING_FIRST'" link type="warning" v-hasPermi="['jewelry:document:withdraw']" @click="withdraw(row)">撤回</el-button><el-button v-if="row.status==='POSTED' && !['REVERSAL','ASSEMBLY'].includes(row.docType)" link type="danger" v-hasPermi="['jewelry:document:reverse']" @click="reverse(row)">红冲</el-button></template></el-table-column></el-table>
    <pagination v-show="total>0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load"/>

    <el-dialog v-model="dialog" :title="readonly?'查看单据':(form.documentId?'编辑单据':'新建单据')" width="94%" top="4vh" destroy-on-close>
      <div class="sheet">
        <el-alert v-if="form.docType === 'REVERSAL'" title="红冲单明细来自原单，不允许修改；提交后由审核员审核通过即可入账。" type="warning" :closable="false" show-icon />
        <el-form :model="form" label-position="top"><div class="sheet-head">
          <el-form-item label="单据类型" required><el-select v-model="form.docType" :disabled="readonly" @change="typeChanged"><el-option v-for="o in editableTypes" :key="o.value" :label="o.label" :value="o.value"/></el-select></el-form-item>
          <el-form-item label="业务日期" required><el-date-picker v-model="form.bizDate" value-format="YYYY-MM-DD" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="needsSupplier" label="供应商" required><el-select v-model="form.supplierId" filterable clearable :disabled="readonly" @change="supplierChanged"><el-option v-for="s in suppliers" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId"/></el-select></el-form-item>
          <el-form-item label="外部单号"><el-input v-model="form.externalNo" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='CUSTOMER_RETURN'" label="原销售单（可选）">
            <el-select v-model="form.sourceDocumentId" filterable clearable :disabled="readonly" @change="sourceChanged">
              <el-option v-for="d in salesDocuments" :key="d.documentId"
                :label="`${d.docNo} · ${d.bizDate} · ${d.salesChannel || '未填写渠道'}`" :value="d.documentId"/>
            </el-select>
          </el-form-item>
          <el-form-item v-if="needsSalesChannel" label="销售渠道" required><el-input v-model="form.salesChannel" :disabled="readonly || !!form.sourceDocumentId"/></el-form-item>
          <el-form-item v-if="form.docType==='SALES_OUT'" label="达人/主播"><el-input v-model="form.influencerName" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='SALES_OUT'" label="平台扣点率（%）"><el-input-number v-model="platformPercent" :min="0" :max="100" :step="1" :precision="2" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='SALES_OUT'" label="达人佣金率（%）"><el-input-number v-model="commissionPercent" :min="0" :max="100" :step="1" :precision="2" :disabled="readonly"/></el-form-item>
          <el-form-item v-if="form.docType==='SALES_OUT'" label="税率（%）"><el-input-number v-model="taxPercent" :min="0" :max="100" :step="1" :precision="2" :disabled="readonly"/></el-form-item>
        </div></el-form>
        <el-alert v-if="canViewFinance && estimatedProfit < 0" title="当前销售单预计亏损，提交后审批页面将显示亏损风险。" type="error" :closable="false" show-icon />
        <div v-if="excelImportSupported && !readonly" class="item-toolbar">
          <div>
            <b>商品明细</b>
            <span>支持通过 Excel 批量填充，导入后仍可修改</span>
          </div>
          <div class="item-toolbar-actions">
            <el-button icon="Download" @click="downloadImportTemplate">下载模板</el-button>
            <el-upload action="#" :accept="form.docType==='PURCHASE_IN'?'.xlsx':'.xls,.xlsx'" :auto-upload="false" :show-file-list="false"
              :on-change="handleImportFile">
              <el-button type="primary" plain icon="Upload" :loading="importLoading">Excel导入</el-button>
            </el-upload>
          </div>
        </div>
        <el-table :data="form.items" border class="item-table">
          <el-table-column type="index" width="50" label="#" />
          <el-table-column label="商品" min-width="320">
            <template #default="{ row }">
              <div class="product-picker">
                <el-select v-model="row.productId" filterable :disabled="readonly" @change="productChanged(row)">
                  <el-option v-for="p in products" :key="p.productId" :label="p.sku + ' · ' + p.productName" :value="p.productId" />
                </el-select>
                <el-button v-if="form.docType==='PURCHASE_IN' && !readonly" type="primary" plain icon="Plus"
                  v-hasPermi="['jewelry:product:add']" @click="openQuickProduct(row)">新增商品</el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="form.docType==='ASSEMBLY'" label="角色" width="90">
            <template #default="{row}"><el-tag :type="row.itemRole==='OUTPUT'?'success':'warning'" effect="plain">{{row.itemRole==='OUTPUT'?'成品产出':'散件投入'}}</el-tag></template>
          </el-table-column>
          <el-table-column v-if="form.docType==='PURCHASE_IN' || (readonly && form.items.some(item=>item.imageUrls))" label="实物图片" width="190">
            <template #default="{row}">
              <image-upload v-model="row.imageUrls" :limit="1" :file-size="8" :disabled="readonly"/>
            </template>
          </el-table-column>
          <el-table-column v-if="showInspectColumns" label="良品数" width="120">
            <template #default="{ row }"><el-input-number v-model="row.goodQty" :min="0" :disabled="readonly" /></template>
          </el-table-column>
          <el-table-column v-if="showInspectColumns" label="次品数" width="120">
            <template #default="{ row }"><el-input-number v-model="row.defectQty" :min="0" :disabled="readonly" /></template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" label="系统库存" width="110">
            <template #default="{ row }">{{ row.systemQty }}</template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" label="实盘库存" width="130">
            <template #default="{ row }"><el-input-number v-model="row.countedQty" :min="0" :disabled="readonly" /></template>
          </el-table-column>
          <el-table-column v-if="showAdjustmentColumn" label="差异数量" width="100">
            <template #default="{ row }">{{ Number(row.countedQty || 0) - Number(row.systemQty || 0) }}</template>
          </el-table-column>
          <el-table-column v-if="showQuantityColumn" label="数量" width="130">
            <template #default="{ row }"><el-input-number v-model="row.qty" :min="1" :disabled="readonly" /></template>
          </el-table-column>
          <el-table-column v-if="showPriceColumn" :label="priceLabel" width="145"><template #default="{row}"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" :disabled="readonly || (form.docType==='CUSTOMER_RETURN' && !!form.sourceDocumentId)"/></template></el-table-column>
          <el-table-column v-if="showCostColumn" label="单位成本" width="120"><template #default="{row}"><span>{{money(row.unitCost)}}</span></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="包装费/件" width="145"><template #default="{row}"><el-input-number v-model="row.packFee" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="物流费/件" width="145"><template #default="{row}"><el-input-number v-model="row.shipFee" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="form.docType==='SALES_OUT'" label="鉴定费/件" width="145"><template #default="{row}"><el-input-number v-model="row.certFee" :min="0" :precision="2" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="showPriceColumn" label="行金额" width="110" align="right"><template #default="{row}">{{money(lineAmount(row))}}</template></el-table-column>
          <el-table-column v-if="canViewFinance && form.docType==='SALES_OUT'" label="预计毛利" width="110" align="right"><template #default="{row}"><span :class="{loss:lineProfit(row)<0}">{{money(lineProfit(row))}}</span></template></el-table-column>
          <el-table-column v-if="showAdjustmentColumn" label="调整原因" min-width="180"><template #default="{row}"><el-input v-model="row.lineReason" :disabled="readonly"/></template></el-table-column>
          <el-table-column v-if="!readonly" width="60"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.items.splice($index,1)"/></template></el-table-column>
        </el-table>
        <el-button v-if="!readonly" plain icon="Plus" class="add-line" @click="form.items.push(blankItem())">增加一行</el-button>
        <div class="document-total">
          <span>SKU {{ form.items.length }} 种</span>
          <span>总件数 <b>{{ estimatedQty }}</b></span>
          <span v-if="showPriceColumn">总金额 <b>¥ {{ money(estimatedAmount) }}</b></span>
          <span v-if="canViewFinance && form.docType==='SALES_OUT'" :class="{loss:estimatedProfit<0}">预计毛利 <b>¥ {{ money(estimatedProfit) }}</b></span>
        </div>
        <div class="sheet-foot"><el-form label-width="110px"><el-form-item v-if="needsReason" :label="reasonLabel" required><el-input v-model="form.returnReason" :disabled="readonly"/></el-form-item><el-form-item v-if="form.docType==='CUSTOMER_RETURN' && !form.sourceDocumentId" label="未关联原单原因" required><el-input v-model="form.unlinkedReason" :disabled="readonly" placeholder="第一阶段未选择原销售单时必须填写"/></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" :disabled="readonly"/></el-form-item></el-form></div>
      </div>
      <template #footer><el-button @click="dialog=false">关闭</el-button><el-button v-if="!readonly" type="primary" @click="save">保存草稿</el-button></template>
    </el-dialog>

    <el-dialog v-model="productDialog" title="新增商品档案" width="640px" append-to-body destroy-on-close>
      <el-form ref="productFormRef" :model="quickProduct" :rules="productRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="SKU" prop="sku"><el-input v-model="quickProduct.sku" placeholder="请输入唯一商品编码"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="商品名称" prop="productName"><el-input v-model="quickProduct.productName"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="商品类型"><el-radio-group v-model="quickProduct.productType"><el-radio-button value="PART">散件</el-radio-button><el-radio-button value="FINISHED">成品</el-radio-button></el-radio-group></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="分类"><el-input v-model="quickProduct.category"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="规格"><el-input v-model="quickProduct.specification"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单位"><el-input v-model="quickProduct.unit"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="预警值"><el-input-number v-model="quickProduct.warningQty" :min="0" style="width:100%"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="实物图片"><image-upload v-model="quickProduct.imageUrls" :limit="1" :file-size="8"/></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="productDialog=false">取消</el-button>
        <el-button type="primary" :loading="productSaving" @click="saveQuickProduct">保存并选中</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialog" title="Excel导入预览" width="88%" top="6vh"
      append-to-body destroy-on-close>
      <div class="import-summary">
        <el-tag type="success">可导入 {{ importPreview.validCount || 0 }} 行</el-tag>
        <el-tag v-if="importPreview.newProductCount" type="warning">新商品 {{ importPreview.newProductCount }} 个</el-tag>
        <el-tag :type="importPreview.errorCount ? 'danger' : 'info'">错误 {{ importPreview.errorCount || 0 }} 行</el-tag>
        <span v-if="importPreview.errorCount">请修正 Excel 中的错误后重新上传。</span>
      </div>
      <el-table :data="importPreview.rows || []" border max-height="520">
        <el-table-column prop="rowNumber" label="Excel行" width="76" align="center"/>
        <el-table-column label="状态" width="92">
          <template #default="{row}">
            <el-tag v-if="row.status==='VALID'" type="success">可导入</el-tag>
            <el-tag v-else-if="row.status==='NEW'" type="warning">新商品</el-tag>
            <el-tag v-else type="danger">有错误</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sku" label="SKU" width="150"/>
        <el-table-column prop="productName" label="商品名称" min-width="160"/>
        <el-table-column v-if="form.docType==='PURCHASE_IN'" label="类型" width="82" align="center">
          <template #default="{row}">{{row.productType==='PART'?'散件':row.productType==='FINISHED'?'成品':'—'}}</template>
        </el-table-column>
        <el-table-column v-if="form.docType==='PURCHASE_IN'" label="图片" width="82" align="center">
          <template #default="{row}">
            <el-image v-if="row.imageUrl" :src="imageSrc(row.imageUrl)" :preview-src-list="[imageSrc(row.imageUrl)]"
              preview-teleported fit="cover" style="width:46px;height:46px"/>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="form.docType!=='STOCK_ADJUST'" prop="qty" label="数量" width="86" align="right"/>
        <el-table-column v-if="form.docType==='STOCK_ADJUST'" prop="countedQty" label="实盘数量" width="100" align="right"/>
        <el-table-column v-if="form.docType!=='STOCK_ADJUST'" prop="unitPrice" :label="priceLabel" width="110" align="right"/>
        <el-table-column v-if="form.docType==='SALES_OUT'" prop="availableQty" label="可用库存" width="100" align="right"/>
        <el-table-column v-if="form.docType==='STOCK_ADJUST'" prop="lineReason" label="调整原因" min-width="150"/>
        <el-table-column prop="errorMessage" label="校验结果" min-width="240">
          <template #default="{row}"><span :class="{ 'import-error': !row.valid }">{{row.errorMessage || '校验通过'}}</span></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="importDialog=false">取消</el-button>
        <el-button type="primary" :loading="applyingImport" :disabled="Number(importPreview.errorCount)>0"
          @click="applyImportRows">导入到当前单据</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup name="JewelryDocument">
import {saveAs} from 'file-saver'
import {listJewelryDocuments,getJewelryDocument,saveJewelryDocument,submitJewelryDocument,withdrawJewelryDocument,createJewelryReversal,listJewelryProducts,listJewelryProductOptions,listJewelrySuppliers,saveJewelryProduct,downloadJewelryDocumentImportTemplate,previewJewelryDocumentImport} from '@/api/jewelry/erp'
import useUserStore from '@/store/modules/user'
const {proxy}=getCurrentInstance(),rows=ref([]),total=ref(0),loading=ref(false),dialog=ref(false),readonly=ref(false),products=ref([]),suppliers=ref([]),salesDocuments=ref([])
const productDialog=ref(false),productSaving=ref(false),productFormRef=ref(),activeProductRow=ref(null)
const importDialog=ref(false),importLoading=ref(false),applyingImport=ref(false),importPreview=ref({})
const blankQuickProduct=()=>({sku:'',productName:'',productType:'FINISHED',category:'',specification:'',imageUrl:'',imageUrls:'',unit:'件',warningQty:5,status:'0',defaultPackFee:0,defaultShipFee:0,defaultCertFee:0})
const quickProduct=reactive(blankQuickProduct())
const productRules={sku:[{required:true,message:'请输入SKU',trigger:'blur'}],productName:[{required:true,message:'请输入商品名称',trigger:'blur'}]}
const userStore=useUserStore()
const canViewFinance=computed(()=>userStore.roles.some(role=>['admin','jewelry_admin','jewelry_reviewer'].includes(role)))
const types=[{value:'PURCHASE_IN',label:'采购入库'},{value:'SALES_OUT',label:'销售出库'},{value:'SUPPLIER_RETURN',label:'供应商退货'},{value:'CUSTOMER_RETURN',label:'客户退货'},{value:'RETURN_INSPECT',label:'退货质检'},{value:'STOCK_ADJUST',label:'库存调整'},{value:'ASSEMBLY',label:'手工组装'},{value:'REVERSAL',label:'红冲单'}]
const editableTypes=types.filter(item=>!['REVERSAL','ASSEMBLY'].includes(item.value))
const statuses=[{value:'DRAFT',label:'草稿'},{value:'PENDING_FIRST',label:'待审核'},{value:'PENDING_SECOND',label:'待审核'},{value:'POSTED',label:'已入账'},{value:'REJECTED',label:'已驳回'},{value:'REVERSED',label:'已红冲'}]
const query=reactive({pageNum:1,pageSize:10,docNo:'',docType:'',status:''})
const blankItem=()=>({productId:null,itemRole:'NORMAL',imageUrls:'',qty:1,goodQty:0,defectQty:0,systemQty:0,countedQty:0,adjustmentQty:0,unitPrice:0,unitCost:0,packFee:0,shipFee:0,certFee:0,lineReason:''})
const blank=()=>({documentId:null,docType:'PURCHASE_IN',bizDate:new Date().toISOString().slice(0,10),supplierId:null,supplierNameSnapshot:'',externalNo:'',salesChannel:'',influencerName:'',platformRate:0,commissionRate:0,taxRate:0,returnReason:'',sourceDocumentId:null,unlinkedReason:'',remark:'',items:[blankItem()]})
const form=reactive(blank())
const showInspectColumns=computed(()=>form.docType==='RETURN_INSPECT'||(form.docType==='REVERSAL'&&form.items?.some(x=>Number(x.goodQty||0)+Number(x.defectQty||0)>0)))
const showAdjustmentColumn=computed(()=>form.docType==='STOCK_ADJUST'||(form.docType==='REVERSAL'&&form.items?.some(x=>Number(x.adjustmentQty||0)!==0)))
const showQuantityColumn=computed(()=>!showInspectColumns.value&&!showAdjustmentColumn.value)
const needsSupplier=computed(()=>['PURCHASE_IN','SUPPLIER_RETURN'].includes(form.docType))
const needsSalesChannel=computed(()=>['SALES_OUT','CUSTOMER_RETURN'].includes(form.docType))
const showPriceColumn=computed(()=>['PURCHASE_IN','SALES_OUT','SUPPLIER_RETURN','CUSTOMER_RETURN'].includes(form.docType))
const showCostColumn=computed(()=>canViewFinance.value&&form.docType!=='PURCHASE_IN')
const excelImportSupported=computed(()=>['PURCHASE_IN','SALES_OUT','STOCK_ADJUST'].includes(form.docType))
const priceLabel=computed(()=>form.docType==='PURCHASE_IN'?'采购单价':form.docType==='SALES_OUT'?'成交单价':form.docType==='SUPPLIER_RETURN'?'退货单价':'退款单价')
const needsReason=computed(()=>['SUPPLIER_RETURN','CUSTOMER_RETURN','STOCK_ADJUST'].includes(form.docType))
const reasonLabel=computed(()=>form.docType==='STOCK_ADJUST'?'调整原因':'退货原因')
const effectiveQty=row=>form.docType==='RETURN_INSPECT'?Number(row.goodQty||0)+Number(row.defectQty||0):form.docType==='STOCK_ADJUST'?Math.abs(Number(row.countedQty||0)-Number(row.systemQty||0)):Number(row.qty||0)
const lineAmount=row=>Number(row.unitPrice||0)*effectiveQty(row)
const lineProfit=row=>{const qty=effectiveQty(row),amount=lineAmount(row),fees=(Number(row.packFee||0)+Number(row.shipFee||0)+Number(row.certFee||0))*qty,rates=Number(form.platformRate||0)+Number(form.commissionRate||0)+Number(form.taxRate||0);return amount-Number(row.unitCost||0)*qty-fees-amount*rates}
const estimatedQty=computed(()=>form.items.reduce((sum,row)=>sum+effectiveQty(row),0))
const estimatedAmount=computed(()=>form.items.reduce((sum,row)=>sum+lineAmount(row),0))
const estimatedProfit=computed(()=>form.docType==='SALES_OUT'?form.items.reduce((sum,row)=>sum+lineProfit(row),0):0)
const percentageModel=key=>computed({get:()=>Number(form[key]||0)*100,set:value=>{form[key]=Number(value||0)/100}})
const platformPercent=percentageModel('platformRate')
const commissionPercent=percentageModel('commissionRate')
const taxPercent=percentageModel('taxRate')
const money=value=>Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const imageSrc=value=>/^https?:/i.test(value||'')?value:import.meta.env.VITE_APP_BASE_API+(value||'')
const labelOf=(list,value)=>list.find(x=>x.value===value)?.label||value;const statusType=s=>s==='POSTED'?'success':['REJECTED','REVERSED'].includes(s)?'danger':s==='DRAFT'?'info':'warning'
async function preload(){const [p,s,d]=await Promise.all([listJewelryProductOptions({status:'0'}),listJewelrySuppliers({pageNum:1,pageSize:500,status:'0'}),listJewelryDocuments({pageNum:1,pageSize:500,docType:'SALES_OUT',status:'POSTED'})]);products.value=p.data||[];suppliers.value=s.rows||[];salesDocuments.value=d.rows||[]}
async function reloadProducts(){const r=await listJewelryProductOptions({status:'0'});products.value=r.data||[]}
async function load(){loading.value=true;try{const r=await listJewelryDocuments(query);rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
function open(){Object.assign(form,blank());readonly.value=false;dialog.value=true}
async function edit(row){Object.assign(form,(await getJewelryDocument(row.documentId)).data);readonly.value=false;dialog.value=true}
async function view(row){Object.assign(form,(await getJewelryDocument(row.documentId)).data);readonly.value=true;dialog.value=true}
function productChanged(row){if(form.items.filter(x=>x.productId===row.productId).length>1){proxy.$modal.msgWarning('同一商品不能重复，请直接修改已有行的数量');row.productId=null;return}const p=products.value.find(x=>x.productId===row.productId);if(p){row.unitCost=Number(p.avgCost||0);row.systemQty=Number(p.onHandQty||0);row.countedQty=Number(p.onHandQty||0);row.packFee=form.docType==='CUSTOMER_RETURN'?0:Number(p.defaultPackFee||0);row.shipFee=Number(p.defaultShipFee||0);row.certFee=Number(p.defaultCertFee||0)}}
function openQuickProduct(row){activeProductRow.value=row;Object.assign(quickProduct,blankQuickProduct());productDialog.value=true}
async function saveQuickProduct(){await productFormRef.value.validate();productSaving.value=true;try{quickProduct.imageUrl=String(quickProduct.imageUrls||'').split(',')[0]||'';await saveJewelryProduct(quickProduct);const r=await listJewelryProducts({pageNum:1,pageSize:500,status:'0'});products.value=r.rows||[];const created=products.value.find(p=>p.sku===quickProduct.sku);if(!created)throw new Error('商品已保存，但未能重新加载，请刷新后选择');activeProductRow.value.productId=created.productId;activeProductRow.value.imageUrls=quickProduct.imageUrls||'';productChanged(activeProductRow.value);productDialog.value=false;proxy.$modal.msgSuccess('商品已新增并自动选中')}finally{productSaving.value=false}}
async function downloadImportTemplate(){
  const blob=await downloadJewelryDocumentImportTemplate(form.docType)
  saveAs(blob,`${labelOf(types,form.docType)}导入模板.xlsx`)
}
async function handleImportFile(uploadFile){
  if(!uploadFile?.raw)return
  importLoading.value=true
  try{
    const r=await previewJewelryDocumentImport(form.docType,uploadFile.raw)
    importPreview.value=r.data||{}
    importDialog.value=true
  }finally{importLoading.value=false}
}
async function applyImportRows(){
  if(Number(importPreview.value.errorCount)>0)return
  if(form.items.some(item=>item.productId)){
    await proxy.$modal.confirm('导入会替换当前已经填写的商品明细，确认继续吗？')
  }
  applyingImport.value=true
  try{
    for(const row of importPreview.value.rows||[]){
      if(row.newProduct){
        await saveJewelryProduct({
          sku:row.sku,productName:row.productName,productType:row.productType,
          imageUrl:row.imageUrl||'',imageUrls:row.imageUrls||row.imageUrl||'',category:row.category||'',
          specification:row.specification||'',unit:row.unit||'件',warningQty:5,status:'0',
          defaultPackFee:0,defaultShipFee:0,defaultCertFee:0
        })
      }
    }
    await reloadProducts()
    const productMap=new Map(products.value.map(product=>[String(product.sku||'').trim().toUpperCase(),product]))
    form.items=(importPreview.value.rows||[]).map(row=>{
      const item=blankItem()
      const product=productMap.get(String(row.sku||'').trim().toUpperCase())
      if(!product)throw new Error(`SKU ${row.sku} 导入后未找到商品档案`)
      item.productId=product.productId
      item.imageUrls=row.imageUrls||row.imageUrl||product.imageUrls||product.imageUrl||''
      item.unitCost=Number(product.avgCost||0)
      item.systemQty=Number(product.onHandQty||0)
      item.countedQty=Number(product.onHandQty||0)
      item.packFee=Number(product.defaultPackFee||0)
      item.shipFee=Number(product.defaultShipFee||0)
      item.certFee=Number(product.defaultCertFee||0)
      if(form.docType==='STOCK_ADJUST'){
        item.countedQty=Number(row.countedQty||0)
        item.lineReason=row.lineReason||''
      }else{
        item.qty=Number(row.qty||0)
        item.unitPrice=Number(row.unitPrice||0)
        if(form.docType==='SALES_OUT'){
          item.packFee=Number(row.packFee||0)
          item.shipFee=Number(row.shipFee||0)
          item.certFee=Number(row.certFee||0)
        }
      }
      return item
    })
    importDialog.value=false
    proxy.$modal.msgSuccess(`已导入 ${form.items.length} 行商品明细`)
  }finally{applyingImport.value=false}
}
function supplierChanged(id){form.supplierNameSnapshot=suppliers.value.find(x=>x.supplierId===id)?.supplierName||''}
async function sourceChanged(id){
  if(!id){form.items=[blankItem()];form.salesChannel='';form.influencerName='';form.platformRate=0;form.commissionRate=0;form.taxRate=0;return}
  const source=(await getJewelryDocument(id)).data
  form.salesChannel=source.salesChannel||''
  form.influencerName=source.influencerName||''
  form.platformRate=0
  form.commissionRate=0
  form.taxRate=0
  form.items=(source.items||[]).map(item=>({...blankItem(),productId:item.productId,qty:1,unitPrice:Number(item.unitPrice||0),unitCost:Number(item.unitCost||0),packFee:0,shipFee:Number(item.shipFee||0),certFee:Number(item.certFee||0),sourceItemId:item.itemId}))
}
function typeChanged(){form.items=[blankItem()];form.supplierId=null;form.supplierNameSnapshot='';form.salesChannel='';form.platformRate=0;form.commissionRate=0;form.taxRate=0;form.returnReason='';form.sourceDocumentId=null;form.unlinkedReason='';importPreview.value={}}
async function save(){if(!form.items.length||form.items.some(x=>!x.productId)){proxy.$modal.msgError('请完整选择商品');return}if(needsSupplier.value&&!form.supplierId){proxy.$modal.msgError('请选择供应商');return}if(needsSalesChannel.value&&!form.salesChannel.trim()){proxy.$modal.msgError('请填写销售渠道');return}if(needsReason.value&&!form.returnReason.trim()){proxy.$modal.msgError(`请填写${reasonLabel.value}`);return}if(form.docType==='CUSTOMER_RETURN'&&!form.sourceDocumentId&&!form.unlinkedReason.trim()){proxy.$modal.msgError('未关联原销售单时必须填写原因');return}await saveJewelryDocument(form);proxy.$modal.msgSuccess('草稿已保存');dialog.value=false;load()}
async function submit(row){await proxy.$modal.confirm(`确认提交单据 ${row.docNo}？`);await submitJewelryDocument(row.documentId);proxy.$modal.msgSuccess('已提交');load()}
async function withdraw(row){await proxy.$modal.confirm(`确认撤回单据 ${row.docNo}？`);await withdrawJewelryDocument(row.documentId);proxy.$modal.msgSuccess('已撤回');load()}
async function reverse(row){await proxy.$modal.confirm(`确认对单据 ${row.docNo} 发起整单红冲？红冲单审核通过后入账。`);await createJewelryReversal(row.documentId);proxy.$modal.msgSuccess('红冲草稿已生成');load()}
preload();load()
</script>
<style scoped>.sheet{border:1px solid #cfd5dc}.sheet-head{display:grid;grid-template-columns:repeat(6,minmax(150px,1fr));gap:12px;padding:14px;background:#f4f6f8}.sheet-head :deep(.el-form-item){margin:0}.sheet-head :deep(.el-input-number),.sheet-head :deep(.el-select),.sheet-head :deep(.el-date-editor){width:100%}.item-toolbar{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-top:1px solid #d9dee5;background:#fafbfc}.item-toolbar>div:first-child{display:flex;align-items:baseline;gap:10px}.item-toolbar b{color:#334155;font-size:14px}.item-toolbar span{color:#8490a0;font-size:12px}.item-toolbar-actions{display:flex;align-items:center;gap:8px}.item-table{border-left:0;border-right:0}.item-table :deep(.el-input-number){width:100%;min-width:0}.product-picker{display:flex;align-items:center;gap:8px}.product-picker .el-select{flex:1;min-width:0}.product-picker .el-button{flex:none}.add-line{margin:12px}.document-total{display:flex;justify-content:flex-end;gap:28px;padding:12px 16px;border-top:1px solid #d9dee5;background:#f8fafc;color:#475569}.document-total b{color:#111827}.loss,.document-total .loss,.document-total .loss b{color:#dc2626;font-weight:700}.sheet-foot{padding:12px 14px 0;border-top:1px solid #d9dee5}.import-summary{display:flex;align-items:center;gap:10px;margin-bottom:14px}.import-summary span:last-child{color:#7c8796}.import-error{color:#c2413a}@media(max-width:1200px){.sheet-head{grid-template-columns:repeat(3,1fr)}}@media(max-width:760px){.sheet-head{grid-template-columns:1fr}.item-toolbar{align-items:stretch;flex-direction:column;gap:10px}.item-toolbar>div:first-child{align-items:flex-start;flex-direction:column;gap:2px}.product-picker{align-items:stretch;flex-direction:column}.document-total{justify-content:flex-start;flex-wrap:wrap;gap:12px 20px}}</style>
