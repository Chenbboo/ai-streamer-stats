<template>
  <div class="business-product" v-loading="loading">
    <header class="product-head"><div><h1>{{ tr('考勤记录','Bảng chấm công') }}</h1><p>{{ tr('按天查看飞书打卡、排班和请假。请假与补卡仍在飞书办理。','Xem chấm công, ca làm và nghỉ phép theo ngày. Đăng ký nghỉ và bổ sung chấm công trên Feishu.') }}</p></div><el-button icon="Refresh" @click="load">{{ tr('刷新','Làm mới') }}</el-button></header>
    <div class="product-toolbar">
      <el-select v-if="options.companies?.length" v-model="companyDeptId" clearable :placeholder="tr('本人或授权公司','Bản thân hoặc công ty được phép')" @change="load"><el-option v-for="c in options.companies" :key="c.companyDeptId" :label="c.companyName" :value="c.companyDeptId" /></el-select>
      <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" :clearable="false" @change="load" />
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-else :title="tr('仅展示已同步的日期；没有记录不代表缺勤。时间按飞书考勤时区显示。','Chỉ hiển thị ngày đã đồng bộ; không có bản ghi không có nghĩa là vắng mặt. Giờ hiển thị theo múi giờ chấm công.')" type="info" :closable="false" />
    <el-table :data="pageRows" :empty-text="tr('所选日期暂无已同步的考勤记录','Chưa có dữ liệu chấm công đã đồng bộ trong khoảng ngày này')" row-key="key">
      <el-table-column prop="date" :label="tr('日期','Ngày')" width="120" />
      <el-table-column prop="userName" :label="tr('姓名','Họ tên')" min-width="100" />
      <el-table-column :label="tr('排班时间','Giờ theo ca')" min-width="170"><template #default="{row}"><div v-for="(p,i) in schedule(row)" :key="i">{{ time(p.start,p.zone,row.date) }} – {{ time(p.end,p.zone,row.date) }}</div><span v-if="!schedule(row).length">{{ tr('未提供','Chưa cung cấp') }}</span></template></el-table-column>
      <el-table-column :label="tr('上班打卡','Chấm công vào')" min-width="130"><template #default="{row}"><div v-for="(p,i) in row.punches" :key="i">{{ time(p.checkInTime,p.zone,row.date) }}<small>{{ result(p.checkInResult) }}</small></div><span v-if="!row.punches.length">—</span></template></el-table-column>
      <el-table-column :label="tr('下班打卡','Chấm công ra')" min-width="130"><template #default="{row}"><div v-for="(p,i) in row.punches" :key="i">{{ time(p.checkOutTime,p.zone,row.date) }}<small>{{ result(p.checkOutResult) }}</small></div><span v-if="!row.punches.length">—</span></template></el-table-column>
      <el-table-column :label="tr('考勤结果','Kết quả')" min-width="150"><template #default="{row}"><el-tag :type="normal(row)?'success':'info'">{{ outcome(row) }}</el-tag><small v-if="row.warnings.length">{{ tr('部分数据待更新或核实','Một phần dữ liệu cần cập nhật hoặc kiểm tra') }}</small></template></el-table-column>
      <el-table-column :label="tr('请假情况','Nghỉ phép')" min-width="200"><template #default="{row}"><div v-for="(leave,i) in row.leaves" :key="i"><span>{{ leave.status==='CONFIRMED'?tr('已批准','Đã duyệt'):tr('已撤回 / 待核实','Đã rút / cần kiểm tra') }}</span><div v-for="(p,j) in leave.intervals" :key="j">{{ time(p[0],leave.zone,row.date) }} – {{ time(p[1],leave.zone,row.date) }}</div><small v-if="leave.seconds!=null">{{ leave.seconds/3600 }} {{ tr('小时（飞书认可）','giờ (Feishu xác nhận)') }}</small></div><span v-if="!row.leaves.length">{{ tr('未见请假记录','Chưa thấy bản ghi nghỉ phép') }}</span></template></el-table-column>
      <el-table-column :label="tr('已通过补卡','Bổ sung đã duyệt')" min-width="160"><template #default="{row}"><div v-for="(p,i) in row.remedies" :key="i">{{ p.time || tr('已通过','Đã duyệt') }}</div><span v-if="!row.remedies.length">{{ tr('未见已通过记录','Chưa thấy bản ghi đã duyệt') }}</span></template></el-table-column>
    </el-table>
    <div class="product-toolbar daily-pagination"><span>{{ tr('共','Tổng') }} {{ days.length }} {{ tr('条','bản ghi') }}</span><el-button :disabled="page===1" @click="page--">{{ tr('上一页','Trang trước') }}</el-button><span>{{ page }} / {{ Math.max(1,Math.ceil(days.length/20)) }}</span><el-button :disabled="page*20>=days.length" @click="page++">{{ tr('下一页','Trang sau') }}</el-button></div>
  </div>
</template>
<script setup name="BusinessAttendance">
import {computed,onMounted,reactive,ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {getFeishuRecords,getFeishuQueryOptions} from '@/api/business/feishu'
import {dailyAttendance} from './daily.mjs'
import '@/assets/styles/business-product.scss'
const {locale}=useI18n(), tr=(zh,vi)=>locale.value==='vi-VN'?vi:zh
const options=reactive({}), companyDeptId=ref(null), rows=ref([]), loading=ref(false), error=ref(''), page=ref(1)
const fmt=d=>new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,10), start=new Date(); start.setDate(start.getDate()-6)
const dates=ref([fmt(start),fmt(new Date())]), days=computed(()=>dailyAttendance(rows.value)), pageRows=computed(()=>days.value.slice((page.value-1)*20,page.value*20))
let sequence=0
function time(seconds,zone,day){
  if (!seconds) return tr('未提供','Chưa cung cấp')
  try {
    const value=new Date(Number(seconds)*1000), parts=new Intl.DateTimeFormat('en-CA',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(value)
    const get=t=>parts.find(p=>p.type===t)?.value, date=`${get('year')}-${get('month')}-${get('day')}`
    const clock=new Intl.DateTimeFormat('en-GB',{timeZone:zone,hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).format(value)
    return (date===day?'':date+' ')+clock
  }catch{return tr('待核实','Cần kiểm tra')}
}
function result(v){return {Normal:tr('正常','Bình thường'),NoNeedCheck:tr('无需打卡','Không cần chấm công'),SystemCheck:tr('系统打卡','Chấm công hệ thống'),Early:tr('早退','Về sớm'),Late:tr('迟到','Đi muộn'),Lack:tr('缺卡','Thiếu chấm công'),Todo:tr('未打卡','Chưa chấm công')}[v]||tr('待核实','Cần kiểm tra')}
const codes=row=>[...new Set(row.punches.flatMap(p=>[p.checkInResult,p.checkOutResult]))]
const normal=row=>!row.warnings.length&&codes(row).length===1&&codes(row)[0]==='Normal'
function outcome(row){return codes(row).length?codes(row).map(result).join(' / '):tr('暂无打卡结果','Chưa có kết quả chấm công')}
function schedule(row){const scheduled=row.punches.filter(p=>p.scheduledIn&&p.scheduledOut).map(p=>({start:p.scheduledIn,end:p.scheduledOut,zone:p.zone}));return scheduled.length?scheduled:row.shifts}
async function load(){
  const n=++sequence; if(!dates.value?.[0])return
  loading.value=true;error.value='';rows.value=[];page.value=1
  const params={companyDeptId:companyDeptId.value||undefined,dateFrom:dates.value[0],dateTo:dates.value[1],includeHistory:false,pageSize:1000}, collected=[]
  try {
    // Collect complete source pages before grouping: one employee/day must never be split across pages.
    for(let p=1;p<=100;p++){
      const response=await getFeishuRecords({...params,pageNum:p});if(n!==sequence)return
      const batch=Array.isArray(response.data)?response.data:response.data?.rows||[];collected.push(...batch)
      if(batch.length<1000){rows.value=collected;return}
    }
    throw new Error(tr('数据量较大，请缩小日期或公司范围','Quá nhiều dữ liệu, hãy thu hẹp khoảng ngày hoặc công ty'))
  }catch(e){if(n===sequence)error.value=e.message||tr('读取失败，请重试','Không thể tải, vui lòng thử lại')}
  finally{if(n===sequence)loading.value=false}
}
onMounted(async()=>{Object.assign(options,(await getFeishuQueryOptions()).data||{});await load()})
</script>
<style scoped>
small{display:block;color:#7b8494;font-size:12px;line-height:1.7} .daily-pagination{margin-top:20px} :deep(.el-table){margin-top:16px} :deep(.el-table .cell>div+div){margin-top:6px}
</style>
