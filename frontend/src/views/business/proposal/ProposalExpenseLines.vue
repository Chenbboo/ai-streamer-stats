<template>
  <div class="plan-section form-section">
    <div class="plan-section-head">
      <div><h3><span class="section-index">{{ sectionNumber }}</span>{{ $tr("支出计划") }}<span class="optional-label">{{ $tr("（选填）") }}</span></h3><p>{{ $tr("{0}；金额单位与项目预算币种同步", [description]) }}</p></div>
      <el-button size="small" type="primary" plain @click="$emit('add-expense')">{{ $tr("增加支出") }}</el-button>
    </div>
    <el-table :data="form.expenseLines" size="small" :empty-text="$tr(&quot;暂无外部支出&quot;)">
      <el-table-column :label="$tr(&quot;类别&quot;)" :label-class-name="form.expenseLines?.length ? 'required-column' : ''" width="145"><template #default="{row}"><el-select v-model="row.expenseCategory"><el-option v-for="(label,value) in expenseCategoryLabel" :key="value" :label="label" :value="value"/></el-select></template></el-table-column>
      <el-table-column :label="$tr(&quot;支出项目&quot;)" :label-class-name="form.expenseLines?.length ? 'required-column' : ''" min-width="150"><template #default="{row}"><el-input v-model="row.itemName" maxlength="160"/></template></el-table-column>
      <el-table-column :label="$tr(&quot;具体用途&quot;)" :label-class-name="form.expenseLines?.length ? 'required-column' : ''" min-width="190"><template #default="{row}"><el-input v-model="row.purpose" maxlength="500"/></template></el-table-column>
      <el-table-column :label="$tr(&quot;金额&quot;)" :label-class-name="form.expenseLines?.length ? 'required-column' : ''" width="165"><template #default="{row}"><el-input-number v-model="row.amount" :min="0" :max="99999999999.99" :precision="2" controls-position="right"/></template></el-table-column><el-table-column :label="$tr(&quot;金额单位&quot;)" width="115"><template #default><el-select :model-value="form.baseCurrency" @update:model-value="$emit('change-currency',$event)" :disabled="!!form.parentProjectId" :aria-label="$tr(&quot;支出金额单位&quot;)"><el-option v-if="form.baseCurrency==='USD'" label="USD" value="USD" disabled/><el-option label="CNY" value="CNY"/><el-option label="VND" value="VND"/></el-select></template></el-table-column>
      <el-table-column :label="$tr(&quot;发生方式&quot;)" :label-class-name="form.expenseLines?.length ? 'required-column' : ''" width="125"><template #default="{row}"><el-select v-model="row.occurrenceType" :placeholder="$tr(&quot;一次性&quot;)"><el-option :label="$tr(&quot;一次性&quot;)" value="ONE_TIME"/><el-option :label="$tr(&quot;每日&quot;)" value="DAILY"/><el-option :label="$tr(&quot;每周&quot;)" value="WEEKLY"/><el-option :label="$tr(&quot;每月&quot;)" value="MONTHLY"/></el-select></template></el-table-column>
      <el-table-column :label="dateType==='date' ? $tr(&quot;支出日期 / 开始&quot;) : $tr(&quot;支出月份 / 开始&quot;)" width="145"><template #default="{row}"><ProposalMonthPicker :date-type="dateType" v-model="row.occurDate" :start-date="form.planStartDate" :end-date="planEndDate" :before-start-months="6" :after-end-months="6"/><small v-if="lineDateIssue(row.occurDate,'expense')" class="danger-text">{{ lineDateIssue(row.occurDate,'expense') }}</small></template></el-table-column>
      <el-table-column :label="$tr(&quot;收款方&quot;)" min-width="130"><template #default="{row}"><el-input v-model="row.counterparty" maxlength="160"/></template></el-table-column>
      <el-table-column width="55"><template #default="{$index}"><el-button link type="danger" @click="form.expenseLines.splice($index,1)">{{ $tr("删") }}</el-button></template></el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import ProposalMonthPicker from './ProposalMonthPicker.vue'
defineProps({ form: Object, sectionNumber: String, description: String, expenseCategoryLabel: Object, dateType: String, planEndDate: String, lineDateIssue: Function })
defineEmits(['add-expense','change-currency'])
</script>

<style scoped>
.optional-label{font-size:13px;font-weight:500;color:#657587}.plan-section{margin:12px 0 22px;padding:22px;border:1px solid #d5e0ec;border-radius:12px;background:#fff;box-shadow:0 5px 16px rgba(36,74,119,.06)}.plan-section-head{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:18px;padding-bottom:14px;border-bottom:1px solid #e3eaf2}.plan-section-head h3{display:flex;align-items:center;gap:10px;margin:0;color:#1f3048;font-size:20px;line-height:32px}.plan-section-head p{margin:6px 0 0;color:#718096;font-size:13px}.section-index{display:inline-flex;align-items:center;justify-content:center;min-width:38px;height:28px;padding:0 7px;border-radius:7px;background:#e9f3ff;color:#2874c6;font-size:14px;font-weight:700;letter-spacing:.04em}.plan-section :deep(.el-input-number){width:100%}.danger-text{color:#d7474f}
</style>
