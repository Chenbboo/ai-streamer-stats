<template>
  <div class="budget-control-fields">
    <el-form-item :label="$tr(&quot;预算控制&quot;)" :prop="fieldProp('mode')" required>
      <el-select :model-value="modelValue.mode || 'TOTAL'" :aria-label="$tr(&quot;预算控制&quot;)" @update:model-value="set('mode',$event)">
        <el-option :label="$tr(&quot;总额上限&quot;)" value="TOTAL"/><el-option :label="$tr(&quot;每日上限&quot;)" value="DAILY"/><el-option :label="$tr(&quot;不设预算上限&quot;)" value="NONE"/>
      </el-select>
    </el-form-item>
    <template v-if="modelValue.mode === 'DAILY'">
      <el-form-item :label="$tr(&quot;每日预算上限&quot;)" :prop="fieldProp('dailyLimit')" required><el-input-number :model-value="modelValue.dailyLimit" :min="0.01" :max="99999999999999.99" :precision="2" :aria-label="$tr(&quot;每日预算上限&quot;)" @update:model-value="set('dailyLimit',$event)"/></el-form-item>
      <el-form-item :label="$tr(&quot;每日统计口径&quot;)" :prop="fieldProp('scope')" required><el-select :model-value="modelValue.scope || 'FULL_COST'" @update:model-value="set('scope',$event)"><el-option :label="$tr(&quot;全成本（外部支出、人员及奖金）&quot;)" value="FULL_COST"/><el-option :label="$tr(&quot;仅外部支出&quot;)" value="CASH_EXPENSE"/></el-select></el-form-item>
      <details v-if="compact" class="budget-help"><summary>{{ $tr("每日预算计算说明") }}</summary><small>{{ $tr("逐日检查上限；每周支出按金额÷7、每月按金额÷30摊销，人员按工作日计入。一次性支出单独检查启动预算。") }}</small></details><small v-else>{{ $tr("逐日检查上限；每周支出按金额÷7、每月按金额÷30摊销，人员按工作日计入。一次性支出单独检查启动预算。") }}</small>
    </template>
    <el-form-item v-if="modelValue.mode === 'DAILY'" :label="$tr(&quot;一次性启动预算&quot;)" :prop="fieldProp('startupLimit')" :required="startupRequired"><el-input-number :model-value="modelValue.startupLimit" :min="0" :max="99999999999999.99" :precision="2" :aria-label="$tr(&quot;一次性启动预算&quot;)" @update:model-value="set('startupLimit',$event)"/><small>{{ compact ? $tr("须覆盖本期一次性支出") : $tr("本期有一次性支出时必填，额度需覆盖本期一次性支出。") }}</small></el-form-item>
    <el-form-item :label="$tr(&quot;预算说明&quot;)" :prop="fieldProp('reason')" :required="modelValue.mode === 'NONE'"><el-input :model-value="modelValue.reason" type="textarea" maxlength="500" :placeholder="$tr(&quot;不设上限的原因或预算说明&quot;)" @update:model-value="set('reason',$event)"/></el-form-item>
  </div>
</template>
<script setup>
const props=defineProps({modelValue:{type:Object,required:true},propPrefix:String,startupRequired:Boolean,compact:Boolean}),emit=defineEmits(['update:modelValue'])
const fieldProp=key=>props.propPrefix ? `${props.propPrefix}.${key}` : undefined
function set(key,value){
  const next={...props.modelValue,[key]:value}
  if(key==='mode'){
    if(value!=='DAILY')next.dailyLimit=null
    if(value!=='DAILY')next.startupLimit=null
  }
  emit('update:modelValue',next)
}
</script>
<style scoped>.budget-help{font-size:12px;margin-bottom:16px}.budget-help summary{cursor:pointer;color:var(--el-color-primary)}.budget-control-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 20px}.budget-control-fields .el-select{width:100%}.budget-control-fields small{display:block;line-height:1.5;color:#778394}@media(max-width:640px){.budget-control-fields{grid-template-columns:1fr}}</style>
