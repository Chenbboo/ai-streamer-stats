import fs from 'node:fs'
import { createRequire } from 'node:module'
const require=createRequire(import.meta.url)
const { chromium }=require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const base=process.env.I18N_TEST_URL || 'http://127.0.0.1:4175'
fs.mkdirSync('.localization', { recursive: true })
const browser=await chromium.launch({headless:true,channel:process.env.I18N_BROWSER || 'msedge'})
const modules=[
 ['jewelry/product','商品档案'],['jewelry/document','业务单据'],['jewelry/stock','库存查询'],['jewelry/inventory','库存盘点'],
 ['jewelry/assembly','成品组装'],['jewelry/supplier','供应商管理'],['jewelry/influencer','达人管理'],['jewelry/approval','单据审批'],
 ['business/project','项目中心'],['business/proposal','立项申请'],['business/kpi','项目KPI'],['business/accounting','财务核算'],
 ['business/department','组织部门'],['business/staff','人员管理'],['business/public-expenses','公司公共费用'],
 ['business/work','工作安排'],['business/attendance','考勤记录'],['business/feishu','飞书配置'],
 ['live/upload','图片上传'],['live/review','识别校正'],['live/streamer','主播管理'],['live/kpi','KPI配置'],
 ['system/user','用户管理'],['system/role','角色管理'],['system/menu','菜单管理'],['system/config','参数管理'],
 ['system/dict','字典管理'],['system/post','岗位管理'],['system/notice','通知公告'],
 ['monitor/job','定时任务'],['monitor/logininfor','登录日志'],['monitor/operlog','操作日志'],['monitor/online','在线用户'],
 ['tool/build','表单构建'],['tool/gen','代码生成']
]
const routeData=[{path:'/i18n-check',component:'Layout',name:'I18nCheck',meta:{title:'系统管理'},children:modules.map(([module,title])=>({path:module.replaceAll('/','-'),component:`${module}/index`,name:module.replaceAll('/','_'),meta:{title,noCache:true}}))}]
const results=[]
const catalog=JSON.parse(fs.readFileSync('src/locales/vi-text.json','utf8'))
const productFixture={productId:1,sku:'I18N-QA-001',productName:'测试商品原名',category:'客户自定义分类',productType:'FINISHED',specification:'精品',unit:'件',onHandQty:10,avgCost:25,warningQty:5,status:'0'}
const createButtons={'jewelry/product':'新增商品','jewelry/document':'新建单据','jewelry/inventory':'新建盘点单','jewelry/assembly':'新建组装单','jewelry/supplier':'新增供应商','system/user':'新增','system/role':'新增','system/menu':'新增','system/config':'新增','system/dict':'新增','system/post':'新增','monitor/job':'新增'}
for(const language of ['vi-VN','zh-CN']){
 const context=await browser.newContext({viewport:{width:1440,height:1000}})
 await context.addCookies([{name:'language',value:language,url:base},{name:'Admin-Token',value:'local-i18n-fixture',url:base}])
 await context.route('**/dev-api/**',async route=>{
  const url=new URL(route.request().url()), p=url.pathname.replace('/dev-api','')
  let response={code:200,msg:'操作成功',rows:[],total:0,data:[]}
  if(p==='/getInfo')response={code:200,roles:['admin'],permissions:['*:*:*'],user:{userId:1,userName:'test',nickName:'QA',avatar:''}}
  else if(p==='/getRouters')response={code:200,data:routeData}
  else if(p.includes('/config/configKey/'))response={code:200,msg:'false'}
  else if(/\/dict\/(?:data\/)?type\//.test(p))response={code:200,data:[{dictLabel:'正常',dictValue:'0',listClass:'success'},{dictLabel:'停用',dictValue:'1',listClass:'danger'}]}
  else if(p==='/jewelry/product/list')response={code:200,rows:[productFixture],total:1}
  else if(p.startsWith('/jewelry/')&&p.includes('options'))response.data=[]
  else if(/departments|department-options|staff\/options/.test(p))response.data=[]
  else if(/options|dashboard|summary|workspace|configuration|status/.test(p))response.data={companies:[],people:[],departments:[],projects:[],staff:[],owners:[],bosses:[],calendars:[],unitPolicies:[],roles:[],plans:[],items:[],rows:[],results:[],summary:{},pending:{},mappings:[],connections:[],tasks:[],routines:[]}
  await route.fulfill({json:response})
 })
 const page=await context.newPage()
 page.setDefaultTimeout(7000)
 let errors=[];page.on('pageerror',e=>errors.push(e.message))
 for(const [module] of modules){
  errors=[]
  await page.goto(`${base}/i18n-check/${module.replaceAll('/','-')}`)
  await page.waitForSelector('.app-main',{timeout:20000}).catch(()=>{})
  await page.waitForLoadState('networkidle')
  const chinese=await page.locator('.app-main').evaluate(el=>{
   const texts=[];const walker=document.createTreeWalker(el,NodeFilter.SHOW_TEXT)
   while(walker.nextNode())if(walker.currentNode.parentElement?.getClientRects().length&&/[\u3400-\u9fff]/.test(walker.currentNode.textContent))texts.push(walker.currentNode.textContent.trim())
   for(const e of el.querySelectorAll('[placeholder],[title],[aria-label]'))for(const a of ['placeholder','title','aria-label'])if(/[\u3400-\u9fff]/.test(e.getAttribute(a)||''))texts.push(e.getAttribute(a))
   return [...new Set(texts)].filter(text=>!['测试商品原名','客户自定义分类'].includes(text))
  }).catch(()=>['PAGE_DID_NOT_MOUNT'])
  results.push({language,module,chinese,errors:[...errors]})
  if(module==='jewelry/product'){
   const rowText=await page.locator('.el-table__body').first().innerText()
   for(const text of [productFixture.productName,productFixture.category,language==='vi-VN'?catalog['精品']:'精品',language==='vi-VN'?catalog['件']:'件']){
    if(!rowText.includes(text))throw new Error(`Product row lost expected display value: ${text}`)
   }
  }
  if(language==='vi-VN'&&['jewelry/product','business/project','business/public-expenses'].includes(module))await page.screenshot({path:`.localization/${module.replaceAll('/','-')}.png`,fullPage:true})
  console.log(JSON.stringify({language,module,chinese:language==='vi-VN'?chinese:chinese.length,errors}))
  const source=createButtons[module]
  if(source){
   const button=page.getByRole('button',{name:language==='vi-VN'?catalog[source].trim():source,exact:true}).first()
   if(await button.count()){
    await button.click()
    const dialog=page.locator('.el-dialog:visible').first()
    await dialog.waitFor({state:'visible'})
    if(await dialog.count()){
     const copy=await dialog.innerText()
     const residual=language==='vi-VN'?[...new Set(copy.match(/[\u3400-\u9fff]+/g)||[])]:[]
     results.push({language,module:module+'/create-dialog',chinese:residual,errors:[...errors]})
     console.log(JSON.stringify({language,module:module+'/create-dialog',chinese:residual,errors}))
     if(module==='jewelry/product'){
      if(language==='vi-VN'){
       const inputs=await dialog.locator('input').evaluateAll(elements=>elements.map(e=>e.value))
       if(inputs.some(value=>/[\u3400-\u9fff]/.test(value)))throw new Error('Untranslated product form default')
       await page.screenshot({path:'.localization/product-dialog.png',fullPage:true,animations:'disabled'})
      }
      await dialog.locator('input').nth(0).fill('I18N-QA-NEW')
      await dialog.locator('input').nth(1).fill('测试商品原名')
      const pending=page.waitForRequest(request=>new URL(request.url()).pathname.endsWith('/jewelry/product')&&request.method()==='POST')
      await dialog.getByRole('button',{name:language==='vi-VN'?catalog['确定']:'确定',exact:true}).click()
      const payload=(await pending).postDataJSON()
      if(payload.productName!=='测试商品原名'||payload.unit!=='件'||payload.specification!=='普通'||payload.productType!=='FINISHED')throw new Error('Localization changed product submission values')
      await dialog.waitFor({state:'hidden'})
      results.push({language,module:module+'/save-canonical-values',chinese:[],errors:[...errors]})
     }
    }
   }
  }
 }
 if(language==='vi-VN'){
  await page.goto(`${base}/i18n-check/jewelry-product`)
  await page.waitForLoadState('networkidle')
  await page.locator('.language-switch').click()
  await page.getByRole('menuitem',{name:'Tiếng Trung'}).click()
  await page.waitForLoadState('networkidle')
  await page.getByRole('button',{name:'新增商品',exact:true}).waitFor()
  if(await page.locator('html').getAttribute('lang')!=='zh-CN')throw new Error('Chinese locale did not persist across reload')
  await page.locator('.language-switch').click()
  await page.getByRole('menuitem',{name:'Tiếng Việt'}).click()
  await page.waitForLoadState('networkidle')
  await page.getByRole('button',{name:catalog['新增商品'],exact:true}).waitFor()
  if(await page.locator('html').getAttribute('lang')!=='vi-VN')throw new Error('Vietnamese locale did not persist across reload')
  console.log('Language switch and reload passed in both directions')
 }
 await context.close()
}
fs.writeFileSync('.localization/browser-results.json',JSON.stringify(results,null,2))
await browser.close()
const failures=results.filter(result=>result.errors.length || result.language==='vi-VN'&&result.chinese.length)
console.log(`Browser checks: ${results.length}; failures: ${failures.length}`)
if(failures.length)process.exitCode=1
