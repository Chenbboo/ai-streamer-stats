import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'
import { supplierReturnProductRows, refreshSupplierReturnProducts, supplierReturnProductQuantitiesValid, supplierReturnMaxQty } from './jewelrySupplierReturn.js'

test('purchase splits merge for editing without changing different actual prices', () => {
  const rows = supplierReturnProductRows([{productId:1,qty:2,unitPrice:150.1234,sourceItemId:11},
    {productId:1,qty:3,unitPrice:150.1234,sourceItemId:22},{productId:1,qty:1,unitPrice:160}])
  assert.equal(rows.length,2); assert.equal(rows[0].qty,5)
  assert.equal(rows[0].unitPrice,150.1234); assert.equal(rows[1].unitPrice,160)
  assert.equal(rows[0].sourceItemId,null)
})
test('eligibility refresh preserves quantity and actual price; missing products stay visible but cannot save', () => {
  const rows=refreshSupplierReturnProducts([{productId:'1',qty:3,unitPrice:155}],
    [{productId:1,remainingReturnQty:4,referencePurchasePrice:100}])
  assert.equal(rows[0].qty,3); assert.equal(rows[0].unitPrice,155); assert.equal(rows[0].sourceUnitPrice,100)
  assert.equal(supplierReturnProductQuantitiesValid(rows),true)
  const unavailable=refreshSupplierReturnProducts(rows,[])
  assert.equal(unavailable[0].qty,3); assert.equal(unavailable[0].remainingReturnQty,0)
  assert.equal(supplierReturnProductQuantitiesValid(unavailable),false)
})
test('sum across different price rows respects combined quota and positive integer quantities', () => {
  const rows=[{productId:1,qty:2,remainingReturnQty:3},{productId:'1',qty:1,remainingReturnQty:3}]
  assert.equal(supplierReturnProductQuantitiesValid(rows),true)
  rows[1].qty=2; assert.equal(supplierReturnProductQuantitiesValid(rows),false)
  rows[1].qty=0.5; assert.equal(supplierReturnProductQuantitiesValid(rows),false)
})
const vue=readFileSync(new URL('../views/jewelry/document/index.vue',import.meta.url),'utf8')
test('quantity control works without a manual purchase source and retains quota/loading guards',()=>{
  const quantityControl=vue.match(/<el-input-number v-else v-model="row\.qty"[^>]*>/)[0]
  const disabled=new Function('form','row','supplierReturnSourceLoading','supplierReturnProductError',
    `return Boolean(${quantityControl.match(/:disabled="([^"]*)"/)[1]})`)
  const form={docType:'SUPPLIER_RETURN',sourceDocumentId:null,items:[]}
  const row={productId:10,qty:1,remainingReturnQty:4,availableReturnQty:4}
  form.items=[row]
  assert.equal(disabled(form,row,false,false),false)
  assert.equal(supplierReturnMaxQty(row,form.items),4)
  row.qty=4; assert.equal(supplierReturnProductQuantitiesValid(form.items),true)
  row.qty=5; assert.equal(supplierReturnProductQuantitiesValid(form.items),false)
  assert.equal(disabled(form,row,true,false),true)
  assert.equal(disabled(form,row,false,true),true)
  assert.equal(disabled(form,{...row,productId:null},false,false),true)
  assert.equal(disabled(form,{...row,remainingReturnQty:0},false,false),true)
  row.qty=1
  form.items.push({...row,qty:2})
  assert.equal(supplierReturnMaxQty(row,form.items),2)
  form.docType='CUSTOMER_RETURN'
  assert.equal(disabled(form,row,false,false),false)
  assert.equal(disabled(form,{...row,remainingReturnQty:0},false,false),true)
})
function harness(){
  const start=vue.indexOf('async function loadSupplierReturnProducts('),end=vue.indexOf('async function supplierChanged(',start)
  const pending=[]
  const factory=new Function('listSupplierReturnProducts','refreshSupplierReturnProducts','translateText','proxy',`
    const supplierReturnProducts={value:[]},supplierReturnProductError={value:false},supplierReturnSourceLoading={value:false};
    let supplierReturnSourceRequest=0;
    const form={docType:'SUPPLIER_RETURN',influencerId:1,supplierId:9,items:[{productId:10,qty:2,unitPrice:155}]};
    ${vue.slice(start,end)}
    return {form,supplierReturnProducts,supplierReturnProductError,supplierReturnSourceLoading,load:loadSupplierReturnProducts};
  `)
  return {...factory(()=>new Promise((resolve,reject)=>pending.push({resolve,reject})),refreshSupplierReturnProducts,text=>text,{$modal:{msgError(){}}}),pending}
}
test('late responses cannot restore previous influencer products or clear newer loading',async()=>{
  const state=harness(),first=state.load(1,9)
  state.form.influencerId=2; const second=state.load(2,9)
  state.pending[0].resolve({data:[{productId:20,remainingReturnQty:9}]}); await first
  assert.deepEqual(state.supplierReturnProducts.value,[]); assert.equal(state.supplierReturnSourceLoading.value,true)
  state.pending[1].resolve({data:[{productId:10,remainingReturnQty:3}]}); await second
  assert.equal(state.supplierReturnProducts.value[0].productId,10)
  assert.equal(state.form.items[0].unitPrice,155); assert.equal(state.supplierReturnSourceLoading.value,false)
})
test('failed load blocks saving and retry restores eligibility without deleting entries',async()=>{
  const state=harness(),failed=state.load(1,9)
  state.pending[0].reject(new Error('offline')); await failed
  assert.equal(state.supplierReturnProductError.value,true); assert.equal(state.form.items[0].qty,2)
  const retry=state.load(1,9); state.pending[1].resolve({data:[{productId:10,remainingReturnQty:3}]}); await retry
  assert.equal(state.supplierReturnProductError.value,false); assert.equal(supplierReturnProductQuantitiesValid(state.form.items),true)
})
test('real form compiles, manual purchase selector is gone, and Chinese/Vietnamese copy exists',()=>{
  const {descriptor}=parse(vue),script=compileScript(descriptor,{id:'supplier-return-auto'})
  const template=compileTemplate({source:descriptor.template.content,filename:'document.vue',id:'supplier-return-auto',compilerOptions:{bindingMetadata:script.bindings}})
  assert.deepEqual(template.errors,[]); assert.ok(!vue.includes('supplierReturnSourceChanged'))
  assert.ok(vue.includes("supplierReturnAutoAllocate:form.docType==='SUPPLIER_RETURN'"))
  const translations=JSON.parse(readFileSync(new URL('../locales/vi-text.json',import.meta.url),'utf8'))
  for(const text of ['参考采购单价','刷新可退商品','所选达人和供应商暂无可退商品','请等待可退商品加载成功后再保存'])assert.ok(translations[text])
})
