<template>
  <el-date-picker :model-value="modelValue" :type="dateType" :format="dateType==='date' ? $tr(&quot;YYYY年MM月DD日&quot;) : $tr(&quot;YYYY年MM月&quot;)" value-format="YYYY-MM-DD"
    :placeholder="dateType==='date' ? $tr(&quot;选择日期&quot;) : $tr(&quot;选择月份&quot;)" :disabled-date="disabledMonth" style="width:100%" @update:model-value="selectMonth" />
</template>

<script setup>
const props=defineProps({modelValue:String,startDate:String,endDate:String,deadline:Boolean,afterStartMonth:Boolean,beforeStartMonths:{type:Number,default:0},afterEndMonths:{type:Number,default:0},dateType:{type:String,default:'month'}})
const emit=defineEmits(['update:modelValue'])
const monthOf=value=>value?.slice(0,7)||''
function disabledMonth(date){
  const month=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`
  const startMonth=monthOf(props.startDate)
  const endMonth=monthOf(props.endDate)
  const firstMonth=props.afterStartMonth&&startMonth?shiftMonth(startMonth,1):startMonth?shiftMonth(startMonth,-props.beforeStartMonths):''
  const lastMonth=endMonth?shiftMonth(endMonth,props.afterEndMonths):''
  if(props.dateType==='date'){
    const day=`${month}-${String(date.getDate()).padStart(2,'0')}`
    const firstDay=firstMonth?`${firstMonth}-01`:''
    const lastDay=lastMonth?`${lastMonth}-${new Date(Number(lastMonth.slice(0,4)),Number(lastMonth.slice(5,7)),0).getDate()}`:''
    return !!firstDay&&day<firstDay||!!lastDay&&day>lastDay
  }
  return !!firstMonth&&month<firstMonth||!!lastMonth&&month>lastMonth
}
function shiftMonth(month,offset){const [year,value]=month.split('-').map(Number),date=new Date(year,value-1+offset,1);return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`}
const nextMonth=month=>shiftMonth(month,1)
function selectMonth(value){
  if(!value){emit('update:modelValue',null);return}
  if(props.dateType==='date'){emit('update:modelValue',value);return}
  const month=monthOf(value)
  if(props.afterStartMonth&&props.startDate&&month<nextMonth(monthOf(props.startDate)))return
  const [year,number]=month.split('-').map(Number)
  // Retain the API's date format: start of the month for cash plans, end for deadlines.
  let date=`${month}-${props.deadline?new Date(year,number,0).getDate():'01'}`
  if(month===monthOf(props.startDate)&&date<props.startDate)date=props.startDate
  if(month===monthOf(props.endDate)&&date>props.endDate)date=props.endDate
  emit('update:modelValue',date)
}
</script>
