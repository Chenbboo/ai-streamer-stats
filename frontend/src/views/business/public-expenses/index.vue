<template>
  <div class="app-container public-expenses-page">
    <header class="page-header">
      <div><h1>{{ $tr("公司公共费用") }}</h1><p>{{ $tr("填好费用，分摊给负责人，月份结束后结算。") }}</p></div>
      <div class="actions"><el-button :disabled="!canManage || loading || saving" @click="policiesDrawer = true">{{ $tr("常用费用") }}</el-button><el-button :disabled="!canManage || loading || saving" @click="historyDrawer = true">{{ $tr("历史记录") }}</el-button><el-button icon="Refresh" :loading="loading" :disabled="saving" @click="refresh">{{ $tr("刷新") }}</el-button></div>
    </header>

    <section class="expense-panel filters-panel">
      <el-form inline label-position="top" @submit.prevent>
        <el-form-item :label="$tr(&quot;所属公司&quot;)"><el-select v-model="filters.companyDeptId" filterable :placeholder="$tr(&quot;请选择公司&quot;)" :disabled="saving || loading" @change="changeFilters"><el-option v-for="company in data.companies" :key="company.companyDeptId" :label="company.companyName" :value="company.companyDeptId" /></el-select></el-form-item>
        <el-form-item :label="$tr(&quot;分摊月份&quot;)"><el-date-picker v-model="filters.month" type="month" value-format="YYYY-MM" :clearable="false" :disabled="saving || loading" @change="changeFilters" /></el-form-item>
        <el-form-item :label="$tr(&quot;币种&quot;)"><el-select v-model="filters.currency" :disabled="saving || loading" @change="changeFilters"><el-option v-for="currency in currencies" :key="currency" :label="currency" :value="currency" /></el-select></el-form-item>
      </el-form>
      <span class="filter-note">{{ $tr("下面填写和分摊的是所选公司 {0} 的费用。部门和负责人随公司切换，各公司、月份及币种分别结算。", [filters.month]) }}</span>
    </section>

    <el-alert v-if="error" class="section-gap" :title="error" type="error" :closable="false" show-icon />
    <el-empty v-else-if="loaded && !data.companies.length" :description="$tr(&quot;暂无可管理的公司，请联系管理员配置公司负责人。&quot;)" />
    <div v-else v-loading="loading" class="workspace">
      <section class="month-summary">
        <div><span>{{ $tr("{0} 费用合计 ", [filters.month]) }}<el-tag :type="billStatusType(bill?.status)">{{ bill ? billStatusLabel(bill.status) : $tr("待确认") }}</el-tag></span><strong>{{ money(displayMonthAmount) }} <small>{{ filters.currency }}</small></strong><p v-if="bill?.adjustments?.length">{{ $tr("含月结后调整 {0} {1}", [money(bill.adjustmentAmount), filters.currency]) }}</p></div>
        <details class="reference-details"><summary>{{ $tr("查看年度参考") }}</summary><p>{{ $tr("{0} 年预计费用：{1} {2}", [selectedYear, money(annualEstimate), filters.currency]) }}</p><p>{{ $tr("年度金额按有效费用设置估算；项目分摊提交后按天计入暂估成本，月结核实实际金额。") }}</p></details>
      </section>
      <nav class="flow-steps" :aria-label="$tr(&quot;费用办理步骤&quot;)">
        <button v-for="(label, index) in ['核对费用', '分摊给负责人', '分摊进度与月结']" :key="label" type="button" :aria-current="step === index + 1 ? 'step' : undefined" :class="{ current: step === index + 1 }" :disabled="saving || loading || (index === 1 && !bill) || (index === 2 && (!bill || bill.status === 'DRAFT'))" @click="selectStep(index + 1)"><span>{{ index + 1 }}</span>{{ label }}</button>
      </nav>
          <el-radio-group v-if="step === 1" v-model="costTab" class="pool-tabs" :disabled="saving || loading || dirty"><el-radio-button value="EXPENSE">{{ $tr("日常公共费用") }}</el-radio-button><el-radio-button value="PERSONNEL">{{ $tr("公共人员成本") }}</el-radio-button></el-radio-group>
          <PersonnelPool v-if="step === 1 && costTab === 'PERSONNEL'" :key="`${filters.companyDeptId}-${filters.month}-${filters.currency}`" :filters="filters" :bill="bill" :editable="canManage && (!bill || bill.status === 'DRAFT')" :disabled="saving || loading || dirty" @busy="saving = $event" @saved="personnelSaved" @recall="recallMonth" />
          <div v-if="step === 1 && costTab === 'PERSONNEL' && bill?.personnel" class="table-footer"><span>{{ $tr("两类费用分别设置分摊比例，统一下发和月结。") }}</span><el-button type="primary" :disabled="saving || loading" @click="costPool = 'PERSONNEL'; restoreOwners(); selectStep(2)">{{ $tr("查看负责人分摊") }}</el-button></div>
          <section v-if="step === 1 && costTab === 'EXPENSE'" class="expense-panel fees-panel">
            <div class="section-header"><div><h2>{{ $tr("{0} 费用明细", [filters.month]) }}</h2><p>{{ bill ? $tr("核对本月金额。尚未确认的费用可以先分摊，月结前再确认。") : $tr("已自动带出本月适用的常用费用，确认后即可分摊。") }}</p></div><div class="actions"><el-button v-if="(!bill || bill.status === 'DRAFT') && canManage" icon="Plus" :disabled="saving || loading" @click="openPolicy()">{{ $tr("添加费用") }}</el-button><el-button v-if="bill?.status === 'DRAFT' && canManage && missingPoliciesCount" :disabled="dirty || loading" :loading="saving" @click="syncNewPolicies">{{ $tr("加入新增费用（{0}）", [missingPoliciesCount]) }}</el-button><el-button v-if="bill?.status === 'PUBLISHED' && canManage" :disabled="loading || saving" @click="recallMonth">{{ $tr("退回修改") }}</el-button><el-button v-if="bill?.status === 'SETTLED' && canManage" :disabled="loading || saving" @click="openAdjustment">{{ $tr("登记调整") }}</el-button></div></div>
            <template v-if="!bill">
              <el-table v-if="monthPolicies.length" :data="monthPolicies" :empty-text="$tr(&quot;本月没有费用&quot;)">
                <el-table-column prop="name" :label="$tr(&quot;费用&quot;)" min-width="140" />
                <el-table-column :label="$tr(&quot;本月金额&quot;)" min-width="130" align="right"><template #default="{ row }">{{ money(policyAmountForMonth(row, filters.month)) }} {{ filters.currency }}</template></el-table-column>
                <el-table-column :label="$tr(&quot;金额状态&quot;)" min-width="120"><template #default="{ row }"><el-tag :type="row.estimated ? 'warning' : 'success'">{{ row.estimated ? $tr("暂估，待确认") : $tr("已确认") }}</el-tag></template></el-table-column>
                <el-table-column :label="$tr(&quot;操作&quot;)" width="100"><template #default="{ row }"><el-button link type="primary" :disabled="saving || loading" @click="openPolicy(row)">{{ $tr("修改") }}</el-button></template></el-table-column>
              </el-table>
              <el-empty v-else :description="$tr(&quot;先添加房租、水电等费用，系统会算出本月金额。&quot;)" :image-size="70" />
              <div class="table-footer"><p>{{ $tr("年费自动分到 12 个月；常用费用在有效期内自动带出。") }}</p><el-button v-if="canManage" type="primary" :disabled="!monthPolicies.length || loading" :loading="saving" @click="generateMonth">{{ $tr("下一步：分摊给负责人") }}</el-button></div>
            </template>
            <template v-else>
              <el-alert v-if="bill.status === 'SETTLED'" :title="$tr(&quot;本月已结算并锁定。后续变动请登记调整，原费用和分摊记录继续保留。&quot;)" type="success" :closable="false" class="section-gap" />
              <el-alert v-if="estimatedCount" :title="$tr(&quot;有 {0} 笔金额待确认。可先分摊；下发后要修改，需退回并重新下发。&quot;, [estimatedCount])" type="warning" :closable="false" show-icon class="section-gap" />
              <el-table :data="entryRows" row-key="entryId" :empty-text="$tr(&quot;本月没有费用明细&quot;)">
                <el-table-column prop="name" :label="$tr(&quot;费用&quot;)" min-width="140" />
                <el-table-column :label="$tr(&quot;类别&quot;)" min-width="95"><template #default="{ row }">{{ categoryLabel(row.category) }}</template></el-table-column>
                <el-table-column :label="$tr(&quot;本月金额（{0}）&quot;, [filters.currency])" min-width="170" align="right"><template #default="{ row }"><el-input-number v-if="canEditMonth" v-model="row.amount" :min="0" :max="999999999999" :precision="2" :controls="false" :aria-label="$tr(&quot;本月金额&quot;)" /><b v-else>{{ money(row.amount) }}</b></template></el-table-column>
                <el-table-column :label="$tr(&quot;金额状态&quot;)" min-width="150"><template #default="{ row }"><el-select v-if="canEditMonth" v-model="row.estimated" :aria-label="$tr(&quot;金额状态&quot;)"><el-option :value="false" :label="$tr(&quot;已确认&quot;)" /><el-option :value="true" :label="$tr(&quot;暂估，待确认&quot;)" /></el-select><el-tag v-else :type="row.estimated ? 'warning' : 'success'">{{ row.estimated ? $tr("暂估，待确认") : $tr("已确认") }}</el-tag></template></el-table-column>
                <el-table-column :label="$tr(&quot;说明&quot;)" min-width="200"><template #default="{ row }"><el-input v-if="canEditMonth" v-model="row.remark" maxlength="500" :placeholder="$tr(&quot;账单依据或调整原因&quot;)" /><span v-else>{{ row.remark || '—' }}</span></template></el-table-column>
              </el-table>
              <div class="table-footer"><span>{{ $tr("费用合计：") }}<b>{{ money(entryTotal) }} {{ filters.currency }}</b><em v-if="entriesDirty">{{ $tr(" · 明细尚未保存") }}</em></span><div v-if="bill.status === 'DRAFT' && canManage" class="actions"><el-button v-if="entriesDirty" :disabled="saving || loading" @click="restoreEntries">{{ $tr("还原") }}</el-button><el-button v-if="entriesDirty" :disabled="ownersDirty || loading" :loading="saving" @click="saveEntries">{{ $tr("暂存费用") }}</el-button><el-button type="primary" :disabled="ownersDirty || loading" :loading="saving" @click="continueToOwners">{{ $tr("下一步：分摊给负责人") }}</el-button></div></div>
              <p v-if="entriesDirty && ownersDirty" class="warning-text">{{ $tr("请先还原其中一处修改，再分别保存费用明细和负责人比例，避免金额依据发生变化。") }}</p>
              <template v-if="bill.adjustments?.length">
                <h3 class="history-title">{{ $tr("月结后的费用调整") }}</h3>
                <el-table :data="bill.adjustments" class="adjustments-table"><el-table-column prop="projectName" :label="$tr(&quot;项目&quot;)" min-width="130" /><el-table-column :label="$tr(&quot;调整金额&quot;)" min-width="140" align="right"><template #default="{ row }">{{ Number(row.amount) > 0 ? '+' : '' }}{{ money(row.amount) }} {{ filters.currency }}</template></el-table-column><el-table-column prop="reason" :label="$tr(&quot;调整原因&quot;)" min-width="180" /><el-table-column prop="operatorName" :label="$tr(&quot;操作人&quot;)" min-width="110" /><el-table-column prop="createTime" :label="$tr(&quot;时间&quot;)" min-width="170" /></el-table>
                <div class="table-footer"><span>{{ $tr("原月结 {0} + 调整 {1} = ", [money(bill.totalAmount), money(bill.adjustmentAmount)]) }}<b>{{ $tr("调整后 {0} {1}", [money(displayMonthAmount), filters.currency]) }}</b></span></div>
              </template>
            </template>
          </section>

          <section v-if="bill && step > 1" class="expense-panel allocation-panel">
            <div class="section-header"><div><h2>{{ step === 2 ? $tr("这些费用由谁承担？") : $tr("项目分摊进度") }}</h2><p>{{ bill.status === 'DRAFT' ? $tr("填写各负责人的分摊比例，合计 100% 后即可下发到工作台。") : $tr("负责人在自己的工作台分摊到项目并提交，你在这里查看进度和月结。") }}</p></div><div v-if="canEditMonth" class="actions"><el-button :disabled="saving || dirty" @click="copyOwners">{{ $tr("沿用上月比例") }}</el-button><el-button icon="Plus" :disabled="saving" @click="addOwner">{{ $tr("添加负责人") }}</el-button></div><div v-else class="actions"><el-button v-if="bill.status === 'PUBLISHED' && canManage" :disabled="saving || loading" @click="recallMonth">{{ $tr("退回修改") }}</el-button><el-button v-if="bill.status === 'SETTLED' && canManage" :disabled="saving || loading" @click="openAdjustment">{{ $tr("登记调整") }}</el-button></div></div>
            <el-radio-group :model-value="costPool" class="pool-tabs" :disabled="saving || loading" @change="changePool"><el-radio-button value="EXPENSE">{{ $tr("日常公共费用 · {0}", [money(expenseAmount)]) }}</el-radio-button><el-radio-button v-if="bill.personnel" value="PERSONNEL">{{ $tr("公共人员成本 · {0}", [money(bill.personnelAmount)]) }}</el-radio-button></el-radio-group>
            <p class="pool-note">{{ $tr("{0} 两类费用均分摊完成后统一下发。", [costPool === 'PERSONNEL' ? $tr("按部门选择负责人分摊公共人员成本；日暂估金额为月分摊金额 ÷ 21.75。") : $tr("日常公共费用按独立比例分摊。")]) }}</p>
            <div v-if="step === 3" class="settlement-bar"><div><b>{{ bill.status === 'SETTLED' ? $tr("本月已结算") : $tr("确认月结") }}</b><p>{{ bill.status === 'SETTLED' ? $tr("公共费用已计入项目成本。后续增减可登记调整，原记录保留。") : settleNotice }}</p><p v-if="bill.status === 'PUBLISHED'">{{ $tr("待分摊到项目 {0} {1} · {2} 位负责人待提交", [money(projectRemainingAmount), filters.currency, pendingOwnerCount]) }}</p></div><el-button v-if="bill.status === 'PUBLISHED' && canManage" type="primary" :disabled="!canSettle || loading" :loading="saving" @click="settleMonth">{{ $tr("确认月结") }}</el-button></div>
            <el-table :data="ownerRows" :empty-text="$tr(&quot;请添加负责人并填写分摊比例&quot;)">
              <el-table-column :label="$tr(&quot;部门&quot;)" min-width="180"><template #default="{ row }"><el-select v-if="canEditMonth" v-model="row.deptId" class="owner-department-select" filterable :placeholder="$tr(&quot;先选择部门&quot;)" :no-data-text="$tr(&quot;该公司尚未设置启用的部门&quot;)" :disabled="!canEditMonth" @change="changeOwnerDepartment(row)"><el-option v-for="dept in ownerDepartments" :key="dept.deptId" :label="dept.deptName" :value="dept.deptId" /></el-select><span v-else>{{ row.deptName || $tr("公司直属") }}</span></template></el-table-column>
              <el-table-column :label="$tr(&quot;负责人&quot;)" min-width="170"><template #default="{ row }"><el-select v-if="canEditMonth" v-model="row.ownerUserId" class="owner-person-select" filterable :disabled="!row.deptId" :placeholder="row.deptId ? $tr(&quot;选择该部门负责人&quot;) : $tr(&quot;请先选择部门&quot;)" :no-data-text="$tr(&quot;该部门暂无可选负责人，请先完善人员所属部门&quot;)"><el-option v-if="row.ownerUserId && !ownerOptions(row).some(owner => Number(owner.userId) === Number(row.ownerUserId))" :value="row.ownerUserId" :label="row.ownerName || ownerName(row.ownerUserId)" disabled /><el-option v-for="owner in ownerOptions(row)" :key="owner.userId" :label="owner.userName" :value="owner.userId" /></el-select><b v-else>{{ row.ownerName || ownerName(row.ownerUserId) }}</b></template></el-table-column>
              <el-table-column :label="$tr(&quot;分摊比例&quot;)" min-width="155"><template #default="{ row }"><span v-if="canEditMonth" class="percentage-field"><el-input-number v-model="row.percentage" :min="0" :max="100" :precision="2" :controls="false" :aria-label="$tr(&quot;负责人分摊比例&quot;)" /> %</span><span v-else>{{ row.percentage }}%</span></template></el-table-column>
              <el-table-column :label="$tr(&quot;月分摊金额（{0}）&quot;, [filters.currency])" min-width="150" align="right"><template #default="{ row, $index }">{{ money(canEditMonth && ownersDirty ? ownerAmountPreview($index) : row.amount) }}</template></el-table-column>
              <el-table-column v-if="costPool === 'PERSONNEL'" :label="$tr(&quot;日暂估金额（÷ 21.75）&quot;)" min-width="145" align="right"><template #default="{ row, $index }">{{ money((canEditMonth && ownersDirty ? ownerAmountPreview($index) : row.amount) / 21.75) }}</template></el-table-column>
              <el-table-column :label="$tr(&quot;项目分摊&quot;)" min-width="170"><template #default="{ row }"><template v-if="bill.status !== 'DRAFT'"><el-tag :type="row.status === 'SUBMITTED' ? 'success' : 'warning'">{{ row.status === 'SUBMITTED' ? $tr("已提交") : $tr("待提交") }}</el-tag><small>{{ $tr("待分摊 {0}", [money(row.remainingAmount)]) }}</small></template><span v-else>{{ $tr("下发后由负责人分摊") }}</span></template></el-table-column>
              <el-table-column v-if="canEditMonth" :label="$tr(&quot;操作&quot;)" width="80"><template #default="{ $index }"><el-button type="danger" link :disabled="saving" @click="ownerRows.splice($index, 1)">{{ $tr("移除") }}</el-button></template></el-table-column>
              <el-table-column v-else type="expand"><template #default="{ row }"><el-table class="project-details" :data="row.projects || []" :empty-text="$tr(&quot;负责人尚未分摊到项目&quot;)"><el-table-column prop="projectName" :label="$tr(&quot;项目&quot;)" /><el-table-column :label="$tr(&quot;分摊比例&quot;)"><template #default="{ row: project }">{{ project.percentage }}%</template></el-table-column><el-table-column :label="$tr(&quot;月分摊金额&quot;)"><template #default="{ row: project }">{{ money(project.amount) }} {{ filters.currency }}</template></el-table-column></el-table></template></el-table-column>
            </el-table>
            <div class="table-footer"><span :class="percentComplete ? 'success-text' : 'warning-text'">{{ $tr("比例合计：") }}<b>{{ percentageTotal.toFixed(2) }}%</b><em v-if="ownersDirty">{{ $tr(" · 尚未保存") }}</em></span><div v-if="bill.status === 'DRAFT' && canManage" class="actions"><el-button :disabled="dirty || saving || loading" @click="selectStep(1)">{{ $tr("上一步") }}</el-button><el-button v-if="ownersDirty" :disabled="saving || loading" @click="restoreOwners">{{ $tr("还原") }}</el-button><el-button :disabled="!ownersDirty || entriesDirty || loading" :loading="saving" @click="saveOwners">{{ $tr("暂存比例") }}</el-button><el-button type="primary" :disabled="entriesDirty || !allPoolsReady || loading" :loading="saving" @click="publishMonth">{{ $tr("保存并下发") }}</el-button></div></div>
            <el-table v-if="costPool === 'PERSONNEL' && departmentTotals.length" :data="departmentTotals" class="section-gap"><el-table-column prop="name" :label="$tr(&quot;部门分摊汇总&quot;)"/><el-table-column :label="$tr(&quot;月分摊金额&quot;)" align="right"><template #default="{ row }">{{ money(row.amount) }} {{ filters.currency }}</template></el-table-column><el-table-column :label="$tr(&quot;日暂估金额（÷ 21.75）&quot;)" align="right"><template #default="{ row }">{{ money(row.amount / 21.75) }} {{ filters.currency }}</template></el-table-column></el-table>
            <p v-if="bill.status === 'DRAFT'" class="filter-note">{{ $tr("部门来自所选公司的组织架构。先选部门，再选负责人；若没有可选人员，请先在人员管理中设置负责人的所属部门。") }}</p>
          </section>
    </div>
        <el-drawer v-model="policiesDrawer" :title="$tr(&quot;常用费用&quot;)" size="min(1000px, 96vw)" append-to-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
          <section class="expense-panel">
            <div class="section-header"><div><h2>{{ $tr("房租、水电等常用费用") }}</h2><p>{{ $tr("填一次，在有效期内每月自动带出。修改此处不改动已确认的月账；当月金额请在第一步修改。") }}</p></div><el-button v-if="canManage" type="primary" icon="Plus" :disabled="loading || saving" @click="openPolicy()">{{ $tr("添加费用") }}</el-button></div>
            <el-table :data="visiblePolicies" :empty-text="$tr(&quot;尚未添加费用规则&quot;)">
              <el-table-column :label="$tr(&quot;费用&quot;)" min-width="150"><template #default="{ row }"><b>{{ row.name }}</b><small>{{ categoryLabel(row.category) }} · v{{ row.version }}</small></template></el-table-column>
              <el-table-column :label="$tr(&quot;填写金额&quot;)" min-width="160" align="right"><template #default="{ row }">{{ money(row.amount) }} {{ row.currency }}<small>{{ $tr("{0} · 月均 {1}", [row.periodType === 'ANNUAL' ? $tr("每年") : $tr("每月"), money(row.periodType === 'ANNUAL' ? Number(row.amount) / 12 : row.amount)]) }}</small></template></el-table-column>
              <el-table-column :label="$tr(&quot;适用月份&quot;)" min-width="190"><template #default="{ row }">{{ $tr("{0} 至 {1}", [row.startMonth, row.endMonth]) }}</template></el-table-column>
              <el-table-column :label="$tr(&quot;状态&quot;)" min-width="155"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? $tr("有效") : $tr("已停用") }}</el-tag><el-tag v-if="row.estimated" type="warning" class="estimate-tag">{{ $tr("暂估") }}</el-tag></template></el-table-column>
              <el-table-column :label="$tr(&quot;备注&quot;)" prop="remark" min-width="160" show-overflow-tooltip />
              <el-table-column v-if="canManage" :label="$tr(&quot;操作&quot;)" width="145" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="saving" @click="openPolicy(row)">{{ row.status === 'ACTIVE' ? $tr("修改 / 附件") : $tr("查看") }}</el-button><el-button v-if="row.status === 'ACTIVE'" link type="danger" :disabled="saving" @click="disablePolicy(row)">{{ $tr("停用") }}</el-button></template></el-table-column>
            </el-table>
          </section>
        </el-drawer>
        <el-drawer v-model="historyDrawer" :title="$tr(&quot;历史记录&quot;)" size="min(1000px, 96vw)" append-to-body>
          <section class="expense-panel">
            <div class="section-header"><div><h2>{{ $tr("月账历史") }}</h2><p>{{ $tr("查看公司的各月费用状态；下方展示所选月份和费用规则的操作依据。") }}</p></div></div>
            <el-table :data="data.history" :empty-text="$tr(&quot;公司暂无月账历史&quot;)"><el-table-column :label="$tr(&quot;版本&quot;)" width="90"><template #default="{ row }">v{{ row.version }}</template></el-table-column><el-table-column prop="month" :label="$tr(&quot;月份&quot;)" /><el-table-column :label="$tr(&quot;费用总额&quot;)"><template #default="{ row }">{{ money(row.totalAmount) }} {{ row.currency || filters.currency }}</template></el-table-column><el-table-column :label="$tr(&quot;状态&quot;)"><template #default="{ row }">{{ billStatusLabel(row.status) }}</template></el-table-column><el-table-column :label="$tr(&quot;月结时间&quot;)" min-width="175"><template #default="{ row }">{{ row.settledTime || '—' }}</template></el-table-column></el-table>
            <h3 class="history-title">{{ $tr("操作与调整记录") }}</h3>
            <el-table :data="data.events" :empty-text="$tr(&quot;暂无操作记录&quot;)"><el-table-column :label="$tr(&quot;操作&quot;)" min-width="135"><template #default="{ row }">{{ eventLabel(row.eventType) }}</template></el-table-column><el-table-column :label="$tr(&quot;操作人&quot;)" prop="operatorName" min-width="110" /><el-table-column :label="$tr(&quot;说明&quot;)" prop="reason" min-width="220" /><el-table-column :label="$tr(&quot;时间&quot;)" prop="createTime" min-width="175" /><el-table-column :label="$tr(&quot;记录&quot;)" width="95"><template #default="{ row }"><el-button v-if="row.snapshot" link type="primary" @click="openSnapshot(row)">{{ $tr("查看依据") }}</el-button></template></el-table-column></el-table>
          </section>
        </el-drawer>

    <el-dialog v-model="policyDialog" :title="policyForm.policyId ? $tr(&quot;修改常用费用&quot;) : $tr(&quot;添加费用&quot;)" width="min(660px, 95vw)" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <p>{{ bill ? $tr("保存后，新增费用可加入本月明细。已有费用的本月金额请在第一步修改。") : $tr("填写金额和适用时间，系统自动计算每个月的费用。") }}</p>
      <el-form :model="policyForm" label-position="top" class="expense-form" :disabled="saving || policyReadOnly">
        <div class="form-grid"><el-form-item :label="$tr(&quot;费用名称&quot;)" required><el-input v-model="policyForm.name" maxlength="100" :placeholder="$tr(&quot;例如：办公场地房租&quot;)" /></el-form-item><el-form-item :label="$tr(&quot;费用类别&quot;)" required><el-select v-model="policyForm.category"><el-option v-for="category in categories" :key="category.value" :label="category.label" :value="category.value" /></el-select></el-form-item></div>
        <div class="form-grid"><el-form-item :label="$tr(&quot;填写方式&quot;)" required><el-radio-group v-model="policyForm.periodType" @change="updateAnnualEnd"><el-radio-button value="MONTHLY">{{ $tr("按月填写") }}</el-radio-button><el-radio-button value="ANNUAL">{{ $tr("按年填写") }}</el-radio-button></el-radio-group></el-form-item><el-form-item :label="`${policyForm.periodType === 'ANNUAL' ? $tr(&quot;年度总额&quot;) : $tr(&quot;每月金额&quot;)}（${filters.currency}）`" required><el-input-number v-model="policyForm.amount" :min="0.01" :max="999999999999" :precision="2" :controls="false" /></el-form-item></div>
        <div class="form-grid"><el-form-item :label="$tr(&quot;开始月份&quot;)" required><el-date-picker v-model="policyForm.startMonth" type="month" value-format="YYYY-MM" :clearable="false" @change="updateAnnualEnd" /></el-form-item><el-form-item :label="$tr(&quot;结束月份（含）&quot;)" required><el-date-picker v-model="policyForm.endMonth" type="month" value-format="YYYY-MM" :clearable="false" :disabled="policyForm.periodType === 'ANNUAL'" /></el-form-item></div>
        <div class="policy-preview"><span>{{ $tr("预计每月费用") }}</span><b>{{ money(policyForm.periodType === 'ANNUAL' ? Number(policyForm.amount || 0) / 12 : policyForm.amount) }} {{ filters.currency }}</b><small v-if="policyForm.periodType === 'ANNUAL'">{{ $tr("连续 12 个月分摊，最后一个月处理分位尾差。") }}</small></div>
        <el-form-item :label="$tr(&quot;金额状态&quot;)"><el-radio-group v-model="policyForm.estimated"><el-radio-button :value="false">{{ $tr("已确认") }}</el-radio-button><el-radio-button :value="true">{{ $tr("暂估，待确认") }}</el-radio-button></el-radio-group></el-form-item>
        <el-collapse v-model="optionalFields" class="optional-fields"><el-collapse-item :title="$tr(&quot;备注和附件（选填）&quot;)" name="attachments">
        <el-form-item :label="$tr(&quot;备注&quot;)"><el-input v-model="policyForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit :placeholder="$tr(&quot;合同、账单周期或费用说明&quot;)" /></el-form-item>
        <el-form-item :label="$tr(&quot;合同或账单附件&quot;)"><file-upload v-model="policyForm.attachmentUrls" :disabled="saving || policyReadOnly" :limit="5" :file-size="20" :file-type="attachmentTypes" /></el-form-item>
        </el-collapse-item></el-collapse>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="policyDialog = false">{{ policyReadOnly ? $tr("关闭") : $tr("取消") }}</el-button><el-button v-if="!policyReadOnly" type="primary" :loading="saving" @click="savePolicy">{{ $tr("保存费用") }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="snapshotDialog" :title="$tr(&quot;历史操作依据&quot;)" width="min(800px, 95vw)" append-to-body>
      <el-descriptions :column="2" border><el-descriptions-item :label="$tr(&quot;操作&quot;)">{{ eventLabel(snapshotEvent.eventType) }}</el-descriptions-item><el-descriptions-item :label="$tr(&quot;操作人&quot;)">{{ snapshotEvent.operatorName || '—' }}</el-descriptions-item><el-descriptions-item :label="$tr(&quot;时间&quot;)">{{ snapshotEvent.createTime || '—' }}</el-descriptions-item><el-descriptions-item :label="$tr(&quot;版本&quot;)">{{ snapshot.version == null ? '—' : `v${snapshot.version}` }}</el-descriptions-item><el-descriptions-item :label="$tr(&quot;说明&quot;)" :span="2">{{ snapshotEvent.reason || '—' }}</el-descriptions-item><el-descriptions-item v-if="snapshot.name" :label="$tr(&quot;费用名称&quot;)">{{ snapshot.name }}</el-descriptions-item><el-descriptions-item v-if="snapshot.amount != null || snapshot.totalAmount != null" :label="$tr(&quot;记录金额&quot;)">{{ money(snapshot.totalAmount ?? snapshot.amount) }} {{ snapshot.currency || filters.currency }}</el-descriptions-item><el-descriptions-item v-if="snapshot.startMonth" :label="$tr(&quot;适用月份&quot;)" :span="2">{{ $tr("{0} 至 {1}", [snapshot.startMonth, snapshot.endMonth]) }}</el-descriptions-item></el-descriptions>
      <template v-if="snapshot.entries?.length"><h3 class="history-title">{{ $tr("当时的费用明细") }}</h3><el-table :data="snapshot.entries"><el-table-column prop="name" :label="$tr(&quot;费用&quot;)" /><el-table-column :label="$tr(&quot;金额&quot;)"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column><el-table-column :label="$tr(&quot;金额状态&quot;)"><template #default="{ row }">{{ row.estimated ? $tr("暂估，待确认") : $tr("已确认") }}</template></el-table-column><el-table-column prop="remark" :label="$tr(&quot;说明&quot;)" /></el-table></template>
      <template v-if="snapshot.ownerAllocations?.length"><h3 class="history-title">{{ $tr("当时的负责人分摊") }}</h3><el-table :data="snapshot.ownerAllocations"><el-table-column :label="$tr(&quot;负责人&quot;)"><template #default="{ row }">{{ row.ownerName || ownerName(row.ownerUserId) }}</template></el-table-column><el-table-column :label="$tr(&quot;分摊比例&quot;)"><template #default="{ row }">{{ row.percentage }}%</template></el-table-column><el-table-column :label="$tr(&quot;月分摊金额&quot;)"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column><el-table-column :label="$tr(&quot;项目&quot;)"><template #default="{ row }"><div v-for="project in row.projects || []" :key="project.projectId">{{ project.projectName }} · {{ money(project.amount) }}</div></template></el-table-column></el-table></template>
      <template v-if="snapshot.projects?.length"><h3 class="history-title">{{ $tr("当时的项目分摊") }}</h3><el-table :data="snapshot.projects"><el-table-column prop="projectName" :label="$tr(&quot;项目&quot;)" /><el-table-column :label="$tr(&quot;分摊比例&quot;)"><template #default="{ row }">{{ row.percentage }}%</template></el-table-column><el-table-column :label="$tr(&quot;金额&quot;)"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column></el-table></template>
      <el-alert v-if="snapshotError" :title="snapshotError" type="warning" :closable="false" class="section-gap" />
      <template #footer><el-button @click="snapshotDialog = false">{{ $tr("关闭") }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="adjustmentDialog" :title="$tr(&quot;登记月结费用调整&quot;)" width="min(540px, 95vw)" append-to-body :close-on-click-modal="false" :show-close="!saving">
      <el-alert :title="$tr(&quot;调整直接计入所选项目的月度公共费用；原月账和分摊比例保留。增加成本填写正数，减少成本填写负数。&quot;)" type="info" :closable="false" show-icon />
      <el-form :model="adjustmentForm" label-position="top" class="expense-form" :disabled="saving">
        <el-form-item :label="$tr(&quot;项目&quot;)" required><el-select v-model="adjustmentForm.projectId" filterable :placeholder="$tr(&quot;选择本月承担费用的项目&quot;)"><el-option v-for="project in adjustmentProjects" :key="project.projectId" :label="project.projectName" :value="project.projectId" /></el-select></el-form-item>
        <el-form-item :label="$tr(&quot;调整金额（{0}）&quot;, [filters.currency])" required><el-input-number v-model="adjustmentForm.amount" :precision="2" :controls="false" :min="-999999999999" :max="999999999999" /></el-form-item>
        <el-form-item :label="$tr(&quot;调整原因&quot;)" required><el-input v-model="adjustmentForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="adjustmentDialog = false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="saveAdjustment">{{ $tr("确认登记调整") }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessPublicExpenses">
import { translateText } from '@/locales/translate'

import PersonnelPool from './PersonnelPool.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newSubmissionId } from '@/utils/submission'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
import {
  getPublicExpenseWorkspace, savePublicExpensePolicy, generatePublicExpenseMonth,
  savePublicExpenseEntries, savePublicExpenseOwners, copyPreviousPublicExpenseOwners,
  publishPublicExpenseMonth, settlePublicExpenseMonth, recallPublicExpenseMonth, adjustPublicExpenseMonth
} from '@/api/business/publicExpense'

const route = useRoute()
const currentMonth = () => { const date = new Date(); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}` }
const validMonth = value => typeof value === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)
const currencies = ['CNY', 'VND', 'USD']
const categories = [
  { value: 'RENT', label: translateText("场地房租") }, { value: 'UTILITIES', label: translateText("水电费") },
  { value: 'PROPERTY', label: translateText("物业费") }, { value: 'NETWORK', label: translateText("网络通讯") },
  { value: 'OFFICE', label: translateText("办公杂费") }, { value: 'OTHER', label: translateText("其他费用") }
]
const attachmentTypes = ['pdf', 'jpg', 'jpeg', 'png', 'webp', 'doc', 'docx', 'xls', 'xlsx', 'txt']
const routeCompany = Number(route.query.companyDeptId)
const filters = reactive({ companyDeptId: Number.isSafeInteger(routeCompany) && routeCompany > 0 ? routeCompany : null, month: validMonth(route.query.month) ? route.query.month : currentMonth(), currency: currencies.includes(route.query.currency) ? route.query.currency : 'CNY' })
const data = reactive({ companies: [], departments: [], owners: [], projects: [], policies: [], bill: null, history: [], events: [], canManage: false })
const loading = ref(false), loaded = ref(false), saving = ref(false), error = ref('')
const costTab = ref('EXPENSE'), costPool = ref('EXPENSE')
const step = ref(1), policiesDrawer = ref(false), historyDrawer = ref(false)
const policyDialog = ref(false), policyForm = reactive({}), adjustmentDialog = ref(false), adjustmentForm = reactive({})
const optionalFields = ref([])
const snapshotDialog = ref(false), snapshotEvent = ref({}), snapshot = ref({}), snapshotError = ref('')
const entryRows = ref([]), ownerRows = ref([])
let loadedFilters = { ...filters }, loadSequence = 0, savedEntries = '', savedOwners = '', loadedBillKey = ''

const bill = computed(() => data.bill)
const canManage = computed(() => loaded.value && !error.value && data.canManage === true)
const canEditMonth = computed(() => canManage.value && bill.value?.status === 'DRAFT' && !saving.value && !loading.value)
const visiblePolicies = computed(() => data.policies.filter(policy => policy.currency === filters.currency))
const activePolicies = computed(() => visiblePolicies.value.filter(policy => policy.status === 'ACTIVE'))
const ownerDepartments = computed(() => data.departments.map(dept => ({ ...dept, deptId: Number(dept.deptId) })))
const monthPolicies = computed(() => activePolicies.value.filter(policy => policy.startMonth <= filters.month && policy.endMonth >= filters.month))
const missingPoliciesCount = computed(() => bill.value?.status === 'DRAFT' ? monthPolicies.value.filter(policy => !bill.value.entries?.some(entry => Number(entry.policyId) === Number(policy.policyId))).length : 0)
const selectedYear = computed(() => filters.month.slice(0, 4))
const monthEstimate = computed(() => activePolicies.value.reduce((sum, policy) => sum + policyAmountForMonth(policy, filters.month), 0))
const displayMonthAmount = computed(() => bill.value ? Number(bill.value.adjustedTotalAmount ?? bill.value.totalAmount) : monthEstimate.value)
const annualEstimate = computed(() => activePolicies.value.reduce((sum, policy) => sum + Array.from({ length: 12 }, (_, month) => policyAmountForMonth(policy, `${selectedYear.value}-${String(month + 1).padStart(2, '0')}`)).reduce((a, b) => a + b, 0), 0))
const entryPayload = () => entryRows.value.map(row => ({ entryId: row.entryId, amount: row.amount, estimated: row.estimated, remark: row.remark || '' }))
const ownerPayload = () => ownerRows.value.map(row => ({ ownerUserId: row.ownerUserId, deptId: row.deptId, costPool: costPool.value, percentage: row.percentage }))
const entriesDirty = computed(() => bill.value?.status === 'DRAFT' && savedEntries !== JSON.stringify(entryPayload()))
const ownersDirty = computed(() => bill.value?.status === 'DRAFT' && savedOwners !== JSON.stringify(ownerPayload()))
const dirty = computed(() => entriesDirty.value || ownersDirty.value)
const entryTotal = computed(() => entryRows.value.reduce((sum, row) => sum + Number(row.amount || 0), 0))
const percentageTotal = computed(() => ownerRows.value.reduce((sum, row) => sum + Number(row.percentage || 0), 0))
const expenseAmount = computed(() => Number(bill.value?.totalAmount || 0) - Number(bill.value?.personnelAmount || 0))
const selectedPoolAmount = computed(() => costPool.value === 'PERSONNEL' ? Number(bill.value?.personnelAmount || 0) : expenseAmount.value)
const percentComplete = computed(() => Math.abs(percentageTotal.value - 100) < 0.000001 || (!ownerRows.value.length && selectedPoolAmount.value === 0))
const allPoolsReady = computed(() => ['EXPENSE', 'PERSONNEL'].every(pool => {
  const amount = pool === 'PERSONNEL' ? Number(bill.value?.personnelAmount || 0) : expenseAmount.value
  const rows = pool === costPool.value ? ownerRows.value : (bill.value?.ownerAllocations || []).filter(row => (row.costPool || 'EXPENSE') === pool)
  return (!rows.length && amount === 0) || Math.abs(rows.reduce((sum, row) => sum + Number(row.percentage || 0), 0) - 100) < 0.000001
}))
const departmentTotals = computed(() => {
  const totals = new Map()
  ownerRows.value.forEach((row, index) => {
    const name = row.deptName || ownerDepartments.value.find(dept => Number(dept.deptId) === Number(row.deptId))?.deptName || translateText("未选择部门")
    const key = row.deptId || name
    if (!totals.has(key)) totals.set(key, { name, amount: 0 })
    totals.get(key).amount += Number(ownersDirty.value ? ownerAmountPreview(index) : row.amount || 0)
  })
  return [...totals.values()]
})
const estimatedCount = computed(() => (bill.value?.entries || []).filter(entry => entry.estimated).length + (bill.value?.personnel?.estimated ? 1 : 0))
const pendingOwnerCount = computed(() => new Set((bill.value?.ownerAllocations || []).filter(row => row.status !== 'SUBMITTED' && Number(row.amount) > 0).map(row => String(row.ownerUserId))).size)
const projectRemainingAmount = computed(() => bill.value?.status === 'DRAFT' ? Number(bill.value.totalAmount) : (bill.value?.ownerAllocations || []).reduce((sum, row) => sum + Number(row.remainingAmount ?? row.amount), 0))
const canSettle = computed(() => bill.value?.status === 'PUBLISHED' && filters.month < currentMonth() && !estimatedCount.value && pendingOwnerCount.value === 0 && Number(projectRemainingAmount.value.toFixed(2)) === 0)
const settleNotice = computed(() => {
  const issues = []
  if (filters.month >= currentMonth()) issues.push(translateText("{0}-01 起可月结", [addMonths(filters.month, 1)]))
  if (estimatedCount.value) issues.push(translateText("{0} 笔金额待确认，请退回修改后重新下发", [estimatedCount.value]))
  if (pendingOwnerCount.value) issues.push(translateText("{0} 位负责人尚未提交", [pendingOwnerCount.value]))
  if (projectRemainingAmount.value > 0) issues.push(translateText("尚有 {0} {1} 待分到项目", [money(projectRemainingAmount.value), filters.currency]))
  return issues.length ? issues.join('；') : translateText("费用已确认，负责人已完成项目分摊，可确认本月结算。")
})
const policyReadOnly = computed(() => policyForm.status === 'DISABLED' || !canManage.value)
const adjustmentProjects = computed(() => {
  const projectIds = new Set((bill.value?.ownerAllocations || []).flatMap(row => (row.projects || []).map(project => Number(project.projectId))))
  return data.projects.filter(project => projectIds.has(Number(project.projectId)) && project.currency === filters.currency)
})

const money = value => value == null || !Number.isFinite(Number(value)) ? '—' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const categoryLabel = value => categories.find(category => category.value === String(value || '').trim().toUpperCase())?.label || translateText("其他费用")
const billStatusLabel = status => ({ DRAFT: translateText("待下发"), PUBLISHED: translateText("已下发"), SETTLED: translateText("已结算") }[status] || status || '—')
const billStatusType = status => ({ DRAFT: 'info', PUBLISHED: 'warning', SETTLED: 'success' }[status] || 'info')
const ownerName = id => data.owners.find(owner => Number(owner.userId) === Number(id))?.userName || translateText("负责人 #{0}", [id])
const eventLabel = type => ({ POLICY: translateText("更新费用规则"), GENERATED: translateText("生成月账"), SYNC_POLICIES: translateText("补充新增费用"), PERSONNEL: translateText("更新公共人员成本"), ENTRIES: translateText("核实费用明细"), OWNERS: translateText("保存负责人比例"), COPY_OWNERS: translateText("复制上月比例"), PUBLISHED: translateText("下发费用"), BEFORE_RECALL: translateText("退回前记录"), RECALLED: translateText("退回修改"), PROJECTS: translateText("保存项目分摊"), COPY_PROJECTS: translateText("复制项目分摊"), SUBMITTED: translateText("负责人提交"), SETTLED: translateText("确认月结"), ADJUSTED: translateText("登记费用调整") }[type] || type || '—')
function openSnapshot(event) {
  snapshotEvent.value = event
  snapshotError.value = ''
  try { const value = typeof event.snapshot === 'string' ? JSON.parse(event.snapshot) : event.snapshot; snapshot.value = Array.isArray(value) ? { projects: value } : value || {} }
  catch { snapshot.value = {}; snapshotError.value = translateText("这条历史明细暂时无法读取，请保留操作时间并联系管理员。") }
  snapshotDialog.value = true
}
function addMonths(month, count) { const [year, value] = month.split('-').map(Number); const date = new Date(year, value - 1 + count, 1); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}` }
function policyAmountForMonth(policy, month) {
  if (!policy.startMonth || !policy.endMonth || month < policy.startMonth || month > policy.endMonth) return 0
  const cents = Math.round(Number(policy.amount) * 100)
  if (policy.periodType !== 'ANNUAL') return cents / 100
  const monthlyCents = Math.floor(cents / 12)
  return (month === policy.endMonth ? cents - monthlyCents * 11 : monthlyCents) / 100
}
function ownerOptions(row) {
  if (!row.deptId) return []
  return data.owners.filter(owner => Number(owner.deptId) === Number(row.deptId) && (Number(owner.userId) === Number(row.ownerUserId) || !ownerRows.value.some(other => Number(other.ownerUserId) === Number(owner.userId))))
}
function changeOwnerDepartment(row) { row.ownerUserId = null }
function ownerAmountPreview(index) {
  const cents = BigInt(Math.round(selectedPoolAmount.value * 100))
  const denominator = 1000000n
  const rates = ownerRows.value.map(row => BigInt(Math.round(Number(row.percentage || 0) * 10000)))
  const portions = rates.map((rate, position) => ({ position, cents: cents * rate / denominator, remainder: cents * rate % denominator }))
  const target = (cents * rates.reduce((sum, rate) => sum + rate, 0n) + denominator / 2n) / denominator
  let tail = target - portions.reduce((sum, portion) => sum + portion.cents, 0n)
  const ranked = [...portions].sort((a, b) => a.remainder === b.remainder ? a.position - b.position : a.remainder > b.remainder ? -1 : 1)
  for (const portion of ranked) { if (tail <= 0n) break; portion.cents += 1n; tail -= 1n }
  return Number(portions[index]?.cents || 0n) / 100
}
function restoreEntries() { entryRows.value = (bill.value?.entries || []).map(row => ({ ...row, amount: Number(row.amount), estimated: row.estimated === true, remark: row.remark || '' })); savedEntries = JSON.stringify(entryPayload()) }
function restoreOwners() {
  ownerRows.value = (bill.value?.ownerAllocations || []).filter(row => (row.costPool || 'EXPENSE') === costPool.value).map(row => {
    const owner = data.owners.find(candidate => Number(candidate.userId) === Number(row.ownerUserId))
    const department = ownerDepartments.value.find(dept => Number(dept.deptId) === Number(owner?.deptId))
    return { ...row, deptId: row.deptId == null ? department?.deptId ?? null : Number(row.deptId), ownerUserId: Number(row.ownerUserId), percentage: Number(row.percentage) }
  })
  savedOwners = JSON.stringify(ownerPayload())
}
function changePool(next) {
  if (dirty.value) return ElMessage.warning(translateText("请先暂存当前修改，再切换费用类型。"))
  costPool.value = next; restoreOwners()
}
function selectStep(next) {
  if (saving.value || loading.value || next === step.value) return
  if (dirty.value) return ElMessage.warning(translateText("请先点击暂存或下一步，保存当前修改。"))
  if ((next > 1 && !bill.value) || (next === 3 && bill.value?.status === 'DRAFT')) return
  step.value = next
}
async function personnelSaved() {
  if (await load()) { costPool.value = 'PERSONNEL'; restoreOwners(); step.value = 2 }
}
async function continueToOwners() {
  if (entriesDirty.value && !await saveEntries()) return
  step.value = 2
}
async function discardChanges() {
  if (!dirty.value) return true
  try { await ElMessageBox.confirm(translateText("费用明细或负责人比例尚未保存，继续将放弃这些修改。"), translateText("尚未保存"), { confirmButtonText: translateText("放弃修改"), cancelButtonText: translateText("继续编辑"), type: 'warning' }); return true } catch { return false }
}
async function load() {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const response = await getPublicExpenseWorkspace({ ...filters })
    if (sequence !== loadSequence) return false
    const result = response.data || {}
    Object.assign(data, { companies: [], departments: [], owners: [], projects: [], policies: [], bill: null, history: [], events: [], canManage: false }, result)
    filters.companyDeptId = result.companyDeptId == null ? null : Number(result.companyDeptId)
    if (result.month) filters.month = result.month
    if (result.currency) filters.currency = result.currency
    const billKey = `${filters.companyDeptId}/${filters.month}/${filters.currency}/${result.bill?.status || 'NONE'}`
    if (loadedBillKey !== billKey) step.value = ['PUBLISHED', 'SETTLED'].includes(result.bill?.status) ? 3 : 1
    loadedBillKey = billKey
    if (!result.bill?.personnel) costPool.value = 'EXPENSE'
    loadedFilters = { ...filters }
    restoreEntries()
    restoreOwners()
    loaded.value = true
    return true
  } catch (failure) {
    if (sequence === loadSequence) error.value = translateText("费用数据加载失败：{0}。当前页面不展示未确认金额。", [failure?.message || translateText("请刷新重试")])
    return false
  } finally { if (sequence === loadSequence) loading.value = false }
}
async function refresh() { if (await discardChanges()) await load() }
async function changeFilters() { if (await discardChanges()) await load(); else Object.assign(filters, loadedFilters) }
async function mutate(action, success, after) {
  if (saving.value || loading.value) return false
  saving.value = true
  try { await action(); after?.(); const refreshed = await load(); if (refreshed) ElMessage.success(success); return refreshed }
  catch { return false /* The shared request interceptor displays the server's actionable error. */ }
  finally { saving.value = false }
}
function openPolicy(policy) {
  Object.assign(policyForm, { policyId: null, version: null, name: '', category: 'RENT', periodType: 'MONTHLY', amount: undefined, currency: filters.currency, startMonth: filters.month, endMonth: filters.month, estimated: false, status: 'ACTIVE', remark: '', attachmentUrls: '' }, policy || {})
  policyForm.amount = policyForm.amount == null ? undefined : Number(policyForm.amount)
  optionalFields.value = policy?.remark || policy?.attachmentUrls ? ['attachments'] : []
  policyDialog.value = true
}
function updateAnnualEnd() { if (validMonth(policyForm.startMonth) && policyForm.periodType === 'ANNUAL') policyForm.endMonth = addMonths(policyForm.startMonth, 11) }
async function savePolicy() {
  if (!policyForm.name?.trim() || !policyForm.category || !Number.isFinite(Number(policyForm.amount)) || Number(policyForm.amount) <= 0) return ElMessage.warning(translateText("请核对费用名称、类别和大于 0 的金额。"))
  if (!validMonth(policyForm.startMonth) || !validMonth(policyForm.endMonth) || policyForm.startMonth > policyForm.endMonth) return ElMessage.warning(translateText("请填写有效的起止月份，结束月份不能早于开始月份。"))
  const payload = { ...policyForm, companyDeptId: filters.companyDeptId, currency: filters.currency, name: policyForm.name.trim() }
  if (dirty.value && !await discardChanges()) return
  await mutate(() => savePublicExpensePolicy(payload), bill.value ? translateText("费用已保存。新增费用请在第一步点击“加入新增费用”。") : translateText("费用已保存，已算出本月金额。"), () => { policyDialog.value = false })
}
async function disablePolicy(policy) {
  if (dirty.value && !await discardChanges()) return
  try { await ElMessageBox.confirm(translateText("停用“{0}”后，未生成月份不再带入该费用。已生成月账继续保留。", [policy.name]), translateText("停用费用规则"), { type: 'warning', confirmButtonText: translateText("确认停用"), cancelButtonText: translateText("取消") }) } catch { return }
  await mutate(() => savePublicExpensePolicy({ ...policy, status: 'DISABLED' }), translateText("费用规则已停用。"))
}
async function generateMonth() { if (await mutate(() => generatePublicExpenseMonth({ ...filters }), translateText("本月费用已保存，请设置负责人分摊。"))) step.value = 2 }
async function syncNewPolicies() { if (dirty.value) return ElMessage.warning(translateText("请先保存当前修改。")); await mutate(() => generatePublicExpenseMonth({ ...filters, syncNewPolicies: true, version: bill.value.version }), translateText("已补充本月新增费用，原有账单金额继续保留，请核对负责人月分摊金额。")) }
async function saveEntries() {
  if (entryRows.value.some(row => row.amount == null || !Number.isFinite(Number(row.amount)) || Number(row.amount) < 0)) { ElMessage.warning(translateText("费用金额须为大于或等于 0 的数字。")); return false }
  return mutate(() => savePublicExpenseEntries(bill.value.billId, { version: bill.value.version, entries: entryPayload() }), translateText("费用明细已保存。"))
}
function addOwner() { ownerRows.value.push({ deptId: null, ownerUserId: null, percentage: undefined, amount: null, status: 'DRAFT' }) }
function ownerValidationError() {
  if (ownerRows.value.some(row => !row.deptId || !data.owners.some(owner => Number(owner.deptId) === Number(row.deptId) && Number(owner.userId) === Number(row.ownerUserId)))) return translateText("请先选择部门，再选择该部门的有效负责人。")
  if (ownerRows.value.some(row => !row.ownerUserId || row.percentage == null || !Number.isFinite(Number(row.percentage)) || Number(row.percentage) <= 0 || Number(row.percentage) > 100)) return translateText("请选择负责人并填写大于 0、不超过 100 的比例。")
  if (new Set(ownerRows.value.map(row => Number(row.ownerUserId))).size !== ownerRows.value.length) return translateText("同一负责人只能分配一行。")
  if (percentageTotal.value > 100.000001) return translateText("负责人比例合计不能超过 100%。")
  return ''
}
async function saveOwners() {
  const validationError = ownerValidationError()
  if (validationError) { ElMessage.warning(validationError); return false }
  return mutate(() => savePublicExpenseOwners(bill.value.billId, { version: bill.value.version, costPool: costPool.value, allocations: ownerPayload() }), translateText("负责人比例已暂存。"))
}
async function copyOwners() {
  if (ownerRows.value.length) { try { await ElMessageBox.confirm(translateText("用上月的负责人比例替换本月草稿比例？"), translateText("复制上月比例"), { confirmButtonText: translateText("复制并替换"), cancelButtonText: translateText("取消") }) } catch { return } }
  await mutate(() => copyPreviousPublicExpenseOwners(bill.value.billId, { version: bill.value.version, costPool: costPool.value }), translateText("已复制上月比例，请核对后下发。"))
}
async function publishMonth() {
  if (saving.value || loading.value) return
  if (!allPoolsReady.value || entriesDirty.value) return ElMessage.warning(translateText("请确认费用明细已保存，两类费用的负责人比例分别合计 100%。"))
  const validationError = ownerValidationError()
  if (validationError) return ElMessage.warning(validationError)
  try { await ElMessageBox.confirm(translateText("将 {0} 的 {1} {2} 统一下发给对应负责人，进入项目分摊。", [filters.month, money(bill.value.totalAmount), filters.currency]), translateText("下发月费用"), { confirmButtonText: translateText("确认下发"), cancelButtonText: translateText("取消"), type: 'info' }) } catch { return }
  // Saving proportions and publishing remain versioned backend operations. Keep
  // the page locked across both; a failed save must never continue to publish.
  await mutate(async () => {
    let version = bill.value.version
    if (ownersDirty.value) {
      const response = await savePublicExpenseOwners(bill.value.billId, { version, costPool: costPool.value, allocations: ownerPayload() })
      version = response.data?.version
      if (version == null) {
        if (!await load()) throw new Error('Unable to reload the saved bill')
        version = bill.value.version
      }
      // Keep the successful draft locally if the following publish fails.
      bill.value.version = version
      if (response.data?.ownerAllocations) { bill.value.ownerAllocations = response.data.ownerAllocations; restoreOwners() }
      else savedOwners = JSON.stringify(ownerPayload())
    }
    await publishPublicExpenseMonth(bill.value.billId, { version })
  }, translateText("费用已下发，负责人可在工作台分摊到项目。"))
}
async function recallMonth() {
  try {
    const { value } = await ElMessageBox.prompt(translateText("退回后可修改金额和比例，已有的负责人项目分摊会清空，需重新下发并提交。请填写原因。"), translateText("退回修改"), { inputType: 'textarea', inputValidator: value => !!value?.trim() && value.trim().length <= 500 || translateText("请填写 1 至 500 字原因"), confirmButtonText: translateText("确认退回"), cancelButtonText: translateText("取消"), type: 'warning' })
    await mutate(() => recallPublicExpenseMonth(bill.value.billId, { version: bill.value.version, reason: value.trim() }), translateText("已退回草稿，请修改后重新下发。"))
  } catch { /* Dialog cancellation leaves the published bill unchanged. */ }
}
async function settleMonth() {
  if (!canSettle.value) return
  try { await ElMessageBox.confirm(translateText("确认结算 {0} 公共费用 {1} {2}？各项目月成本将计入对应承担额，月账随后锁定。", [filters.month, money(bill.value.totalAmount), filters.currency]), translateText("确认月结"), { confirmButtonText: translateText("确认月结"), cancelButtonText: translateText("取消"), type: 'warning' }) } catch { return }
  await mutate(() => settlePublicExpenseMonth(bill.value.billId, { version: bill.value.version }), translateText("月结完成，费用与分摊记录已锁定。"))
}
function openAdjustment() { Object.assign(adjustmentForm, { projectId: null, amount: undefined, reason: '', requestKey: newSubmissionId() }); adjustmentDialog.value = true }
async function saveAdjustment() {
  if (!adjustmentForm.projectId || adjustmentForm.amount == null || !Number.isFinite(Number(adjustmentForm.amount)) || Number(adjustmentForm.amount) === 0 || !adjustmentForm.reason.trim()) return ElMessage.warning(translateText("请选择项目，填写非零调整金额和原因。"))
  await mutate(() => adjustPublicExpenseMonth(bill.value.billId, { ...adjustmentForm, version: bill.value.version, reason: adjustmentForm.reason.trim() }), translateText("费用调整已登记，原月账记录保留。"), () => { adjustmentDialog.value = false })
}

onBeforeRouteLeave(() => discardChanges())
onMounted(load)
useBusinessRefreshOnReactivated(async () => { if (!dirty.value && !policyDialog.value && !adjustmentDialog.value) await load() })
</script>

<style scoped lang="scss">
.pool-tabs { margin-bottom: 18px; }
.pool-note { margin-bottom: 18px; }
.public-expenses-page { max-width: 1580px; margin: 0 auto; color: #243d4b; }
.page-header, .section-header, .table-footer, .settlement-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.page-header { margin-bottom: 22px; }
h1 { margin: 8px 0; font-size: 27px; }
h2 { margin: 0 0 8px; font-size: 17px; }
h2 .el-tag { margin-left: 8px; vertical-align: middle; }
p { margin: 0; color: #72818c; line-height: 1.7; font-size: 13px; }
.expense-panel { border: 1px solid #e2e9ed; border-radius: 12px; background: var(--el-bg-color, #fff); padding: 22px; }
.filters-panel { padding-bottom: 14px; }
.filters-panel .el-form { display: flex; flex-wrap: wrap; gap: 12px 20px; }
.filters-panel .el-form-item { margin: 0; width: 225px; }
.filters-panel .el-select, .filters-panel .el-date-editor { width: 100%; }
.filter-note { display: block; color: #82909a; font-size: 12px; margin-top: 10px; }
.month-summary { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin: 18px 0; padding: 20px 24px; border-radius: 12px; border: 1px solid #dcebe5; background: #f1f8f6; }
.month-summary span { color: #496f69; font-size: 14px; }
.month-summary .el-tag { margin-left: 8px; }
.month-summary strong { display: block; margin-top: 10px; font-size: 30px; color: #224f4d; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.month-summary strong small { font-size: 14px; font-weight: 400; }
.reference-details { max-width: 420px; font-size: 13px; color: #4a716b; }
.reference-details summary { cursor: pointer; padding: 8px 0; }
.reference-details p { margin-top: 6px; }
.flow-steps { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin: 20px 0; }
.flow-steps button { display: flex; gap: 10px; align-items: center; justify-content: center; padding: 14px 8px; border-radius: 8px; border: 1px solid #e2e9ed; color: #647983; background: var(--el-bg-color, #fff); font: inherit; font-size: 14px; cursor: pointer; }
.flow-steps button span { display: grid; place-items: center; width: 24px; height: 24px; flex-shrink: 0; border-radius: 50%; background: #edf2f4; }
.flow-steps button.current { color: #267869; border-color: #91c5b8; background: #f1f8f6; font-weight: 600; }
.flow-steps button.current span { background: #267869; color: #fff; }
.flow-steps button:disabled { cursor: not-allowed; opacity: .55; }
.flow-steps button:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.section-gap { margin-top: 16px; }
.section-header { margin-bottom: 18px; }
.actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.actions .el-button + .el-button { margin-left: 0; }
.table-footer { padding-top: 18px; font-size: 13px; }
.table-footer em { font-style: normal; color: #a77422; }
.percentage-field { display: flex; align-items: center; gap: 6px; }
.percentage-field .el-input-number { width: 108px; }
.el-table .el-input-number { max-width: 100%; }
.el-table small { display: block; margin-top: 5px; color: #83919a; font-size: 11px; }
.project-details { margin: 12px 24px; width: calc(100% - 48px); }
.success-text { color: #25836f; }
.warning-text { color: #a77422; }
.settlement-bar { background: #f5f8fa; margin-bottom: 20px; padding: 18px; border-radius: 8px; }
.settlement-bar p { margin-top: 4px; }
.estimate-tag { margin-left: 5px; }
.history-title { margin-top: 26px; font-size: 15px; }
.expense-form { margin-top: 20px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; }
.expense-form .el-select, .expense-form .el-input-number, .expense-form :deep(.el-date-editor) { width: 100%; }
.policy-preview { display: flex; flex-wrap: wrap; gap: 8px 18px; background: #f1f8f6; border: 1px solid #dcebe5; border-radius: 8px; padding: 16px; margin-bottom: 20px; color: #38665d; font-size: 13px; }
.policy-preview small { flex-basis: 100%; color: #6c8981; }
@media (max-width: 1100px) { .section-header { align-items: flex-start; flex-wrap: wrap; } }
@media (max-width: 650px) { .public-expenses-page { padding: 14px; } .expense-panel { padding: 15px; } .month-summary { padding: 16px; align-items: flex-start; flex-direction: column; gap: 4px; } .month-summary strong { font-size: 25px; } .form-grid { grid-template-columns: 1fr; } .page-header, .table-footer, .settlement-bar { align-items: flex-start; flex-direction: column; } .filters-panel .el-form-item { width: 100%; } .flow-steps { gap: 5px; } .flow-steps button { flex-direction: column; gap: 6px; font-size: 12px; text-align: center; } .table-footer .actions { justify-content: flex-start; } }
</style>
