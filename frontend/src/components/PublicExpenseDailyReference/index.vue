<template>
  <section v-if="reference?.byCurrency?.length" class="public-daily-reference">
    <div class="reference-heading"><div><h3>{{ $tr("每日公共费用") }}</h3><p>{{ $tr("公共费用已包含在当天成本和经营结果中；月结前为暂估。") }}</p></div><el-button link type="primary" @click="expanded = !expanded">{{ expanded ? $tr("收起项目明细") : $tr("查看项目明细") }}</el-button></div>
    <el-alert v-if="reference.pendingCount" :title="$tr(&quot;部分公共费用尚未下发或提交分摊，成本数据待完善。&quot;)" type="warning" :closable="false" show-icon />
    <div v-for="total in reference.byCurrency" :key="total.currency" class="reference-totals">
      <article><span>{{ total.pendingCount ? $tr("已计入公共费用") : $tr("当日公共费用") }}</span><b>{{ money(total.dailyReference) }} <small>{{ total.currency }}</small></b></article>
      <article><span>{{ $tr("当日税前结果（已含公共费用）") }}</span><b :class="Number(pretax(total)) < 0 ? 'negative' : ''">{{ pretax(total) == null ? $tr("待完善") : money(pretax(total)) }} <small v-if="pretax(total) != null">{{ total.currency }}</small></b></article>
    </div>
    <p class="reference-note">{{ $tr("按项目当月承担费用期间的自然日分摊，最后一天补齐尾差；月结核实实际金额，不再额外扣除整月费用。历史已月结账单保留原记录。") }}</p>
    <el-table v-if="expanded" :data="reference.rows" size="small">
      <el-table-column prop="projectName" :label="$tr(&quot;项目&quot;)" min-width="140" />
      <el-table-column :label="$tr(&quot;月度分摊&quot;)" min-width="135"><template #default="{ row }">{{ money(row.monthAmount) }} {{ row.currency }}</template></el-table-column>
      <el-table-column :label="$tr(&quot;当日已计入成本&quot;)" min-width="135"><template #default="{ row }">{{ money(row.dailyReference) }} {{ row.currency }}</template></el-table-column>
      <el-table-column :label="$tr(&quot;其中暂估&quot;)"><template #default="{ row }">{{ money(row.estimatedAmount) }} {{ row.currency }}</template></el-table-column>
      <el-table-column :label="$tr(&quot;当日税前结果&quot;)" min-width="155"><template #default="{ row }">{{ pretax(row) == null ? $tr("待完善") : `${money(pretax(row))} ${row.currency}` }}</template></el-table-column>
    </el-table>
  </section>
</template>

<script setup>
import { ref } from 'vue'
defineProps({ reference: { type: Object, default: null } })
const expanded = ref(false)
const pretax = row => Object.prototype.hasOwnProperty.call(row, 'pretaxProfit') ? row.pretaxProfit : row.referenceProfit
const money = value => Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
</script>

<style scoped>
.public-daily-reference{margin:16px 0;padding:18px;border:1px solid #cfe1df;border-radius:12px;background:#f5faf9}.reference-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.reference-heading h3{margin:0;color:#254a47;font-size:16px}.reference-heading p,.reference-note{margin:7px 0;color:#697e7b;font-size:12px;line-height:1.7}.reference-totals{display:grid;grid-template-columns:1fr 1.5fr;gap:16px;margin:15px 0}.reference-totals article{display:flex;flex-direction:column;gap:8px}.reference-totals span{color:#667a78;font-size:13px}.reference-totals b{color:#275b53;font-size:22px}.reference-totals small{font-size:12px;font-weight:400}.reference-totals b.negative{color:#c45656}@media(max-width:700px){.reference-heading{align-items:flex-start}.reference-totals{grid-template-columns:1fr}}
</style>
