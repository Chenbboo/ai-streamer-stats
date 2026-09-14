<template>
  <el-date-picker :model-value="modelValue" :type="dateType" :format="dateType==='date' ? 'YYYY年MM月DD日' : 'YYYY年MM月'" value-format="YYYY-MM-DD"
    :placeholder="dateType==='date' ? '选择日期' : '选择月份'" :disabled-date="disabledMonth" style="width:100%" @update:model-value="selectMonth" />
</template>

<script setup>
const props=defineProps({modelValue:String,startDate:String,endDate:String,deadline:Boolean,dateType:{type:String,default:'month'}})
const emit=defineEmits(['update:modelValue'])
const monthOf=value=>value?.slice(0,7)||''
function disabledMonth(date){
  const month=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`
  if(props.dateType==='date'){
    const day=`${month}-${String(date.getDate()).padStart(2,'0')}`
    return !!props.startDate&&day<props.startDate||!!props.endDate&&day>props.endDate
  }
  return !!props.startDate&&month<monthOf(props.startDate)||!!props.endDate&&month>monthOf(props.endDate)
}
function selectMonth(value){
  if(!value){emit('update:modelValue',null);return}
  if(props.dateType==='date'){emit('update:modelValue',value);return}
  const month=monthOf(value)
  const [year,number]=month.split('-').map(Number)
  // Retain the API's date format: start of the month for cash plans, end for deadlines.
  let date=`${month}-${props.deadline?new Date(year,number,0).getDate():'01'}`
  if(month===monthOf(props.startDate)&&date<props.startDate)date=props.startDate
  if(month===monthOf(props.endDate)&&date>props.endDate)date=props.endDate
  emit('update:modelValue',date)
}
</script>
