<template>
  <div class="app-container public-expenses-page">
    <header class="page-header">
      <div><h1>公司公共费用</h1><p>填好费用，分给负责人，月份结束后结算。</p></div>
      <div class="actions"><el-button :disabled="!canManage || loading || saving" @click="policiesDrawer = true">常用费用</el-button><el-button :disabled="!canManage || loading || saving" @click="historyDrawer = true">历史记录</el-button><el-button icon="Refresh" :loading="loading" :disabled="saving" @click="refresh">刷新</el-button></div>
    </header>

    <section class="expense-panel filters-panel">
      <el-form inline label-position="top" @submit.prevent>
        <el-form-item label="所属公司"><el-select v-model="filters.companyDeptId" filterable placeholder="请选择公司" :disabled="saving || loading" @change="changeFilters"><el-option v-for="company in data.companies" :key="company.companyDeptId" :label="company.companyName" :value="company.companyDeptId" /></el-select></el-form-item>
        <el-form-item label="分摊到哪个月"><el-date-picker v-model="filters.month" type="month" value-format="YYYY-MM" :clearable="false" :disabled="saving || loading" @change="changeFilters" /></el-form-item>
        <el-form-item label="币种"><el-select v-model="filters.currency" :disabled="saving || loading" @change="changeFilters"><el-option v-for="currency in currencies" :key="currency" :label="currency" :value="currency" /></el-select></el-form-item>
      </el-form>
      <span class="filter-note">下面填写和分配的是所选公司 {{ filters.month }} 的费用。部门和负责人随公司切换，各公司、月份及币种分别结算。</span>
    </section>

    <el-alert v-if="error" class="section-gap" :title="error" type="error" :closable="false" show-icon />
    <el-empty v-else-if="loaded && !data.companies.length" description="暂无可管理的公司，请联系管理员配置公司负责人。" />
    <div v-else v-loading="loading" class="workspace">
      <section class="month-summary">
        <div><span>{{ filters.month }} 费用合计 <el-tag :type="billStatusType(bill?.status)">{{ bill ? billStatusLabel(bill.status) : '待确认' }}</el-tag></span><strong>{{ money(displayMonthAmount) }} <small>{{ filters.currency }}</small></strong><p v-if="bill?.adjustments?.length">含月结后调整 {{ money(bill.adjustmentAmount) }} {{ filters.currency }}</p></div>
        <details class="reference-details"><summary>查看年度参考</summary><p>{{ selectedYear }} 年预计费用：{{ money(annualEstimate) }} {{ filters.currency }}</p><p>年度金额按有效费用设置估算；项目分摊提交后按天计入暂估成本，月结核实实际金额。</p></details>
      </section>
      <nav class="flow-steps" aria-label="费用办理步骤">
        <button v-for="(label, index) in ['填写费用', '分给负责人', '分摊进度与月结']" :key="label" type="button" :aria-current="step === index + 1 ? 'step' : undefined" :class="{ current: step === index + 1 }" :disabled="saving || loading || (index === 1 && !bill) || (index === 2 && (!bill || bill.status === 'DRAFT'))" @click="selectStep(index + 1)"><span>{{ index + 1 }}</span>{{ label }}</button>
      </nav>
          <section v-if="step === 1" class="expense-panel fees-panel">
            <div class="section-header"><div><h2>{{ filters.month }} 费用明细</h2><p>{{ bill ? '核对本月金额。尚未确定的费用可以先分配，月结前再确认。' : '已自动带出本月适用的常用费用，确认后即可分配。' }}</p></div><div class="actions"><el-button v-if="(!bill || bill.status === 'DRAFT') && canManage" icon="Plus" :disabled="saving || loading" @click="openPolicy()">添加费用</el-button><el-button v-if="bill?.status === 'DRAFT' && canManage && missingPoliciesCount" :disabled="dirty || loading" :loading="saving" @click="syncNewPolicies">加入新增费用（{{ missingPoliciesCount }}）</el-button><el-button v-if="bill?.status === 'PUBLISHED' && canManage" :disabled="loading || saving" @click="recallMonth">退回修改</el-button><el-button v-if="bill?.status === 'SETTLED' && canManage" :disabled="loading || saving" @click="openAdjustment">登记调整</el-button></div></div>
            <template v-if="!bill">
              <el-table v-if="monthPolicies.length" :data="monthPolicies" empty-text="本月没有费用">
                <el-table-column prop="name" label="费用" min-width="140" />
                <el-table-column label="本月金额" min-width="130" align="right"><template #default="{ row }">{{ money(policyAmountForMonth(row, filters.month)) }} {{ filters.currency }}</template></el-table-column>
                <el-table-column label="金额确认" min-width="120"><template #default="{ row }"><el-tag :type="row.estimated ? 'warning' : 'success'">{{ row.estimated ? '待确认' : '已确定' }}</el-tag></template></el-table-column>
                <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" :disabled="saving || loading" @click="openPolicy(row)">修改</el-button></template></el-table-column>
              </el-table>
              <el-empty v-else description="先添加房租、水电等费用，系统会算出本月金额。" :image-size="70" />
              <div class="table-footer"><p>年费自动分到 12 个月；常用费用在有效期内自动带出。</p><el-button v-if="canManage" type="primary" :disabled="!monthPolicies.length || loading" :loading="saving" @click="generateMonth">下一步：分给负责人</el-button></div>
            </template>
            <template v-else>
              <el-alert v-if="bill.status === 'SETTLED'" title="本月已结算并锁定。后续变动请登记调整，原费用和分摊记录继续保留。" type="success" :closable="false" class="section-gap" />
              <el-alert v-if="estimatedCount" :title="`有 ${estimatedCount} 笔金额待确认。可先分配；下发后要修改，需退回并重新下发。`" type="warning" :closable="false" show-icon class="section-gap" />
              <el-table :data="entryRows" row-key="entryId" empty-text="本月没有费用明细">
                <el-table-column prop="name" label="费用" min-width="140" />
                <el-table-column label="类别" min-width="95"><template #default="{ row }">{{ categoryLabel(row.category) }}</template></el-table-column>
                <el-table-column :label="`月金额（${filters.currency}）`" min-width="170" align="right"><template #default="{ row }"><el-input-number v-if="canEditMonth" v-model="row.amount" :min="0" :max="999999999999" :precision="2" :controls="false" aria-label="月费用金额" /><b v-else>{{ money(row.amount) }}</b></template></el-table-column>
                <el-table-column label="金额确认" min-width="150"><template #default="{ row }"><el-select v-if="canEditMonth" v-model="row.estimated" aria-label="金额是否确定"><el-option :value="false" label="已确定" /><el-option :value="true" label="待确认（暂估）" /></el-select><el-tag v-else :type="row.estimated ? 'warning' : 'success'">{{ row.estimated ? '待确认' : '已确定' }}</el-tag></template></el-table-column>
                <el-table-column label="说明" min-width="200"><template #default="{ row }"><el-input v-if="canEditMonth" v-model="row.remark" maxlength="500" placeholder="账单依据或调整原因" /><span v-else>{{ row.remark || '—' }}</span></template></el-table-column>
              </el-table>
              <div class="table-footer"><span>费用合计：<b>{{ money(entryTotal) }} {{ filters.currency }}</b><em v-if="entriesDirty"> · 明细尚未保存</em></span><div v-if="bill.status === 'DRAFT' && canManage" class="actions"><el-button v-if="entriesDirty" :disabled="saving || loading" @click="restoreEntries">还原</el-button><el-button v-if="entriesDirty" :disabled="ownersDirty || loading" :loading="saving" @click="saveEntries">暂存费用</el-button><el-button type="primary" :disabled="ownersDirty || loading" :loading="saving" @click="continueToOwners">下一步：分给负责人</el-button></div></div>
              <p v-if="entriesDirty && ownersDirty" class="warning-text">请先还原其中一处修改，再分别保存费用明细和负责人比例，避免金额依据发生变化。</p>
              <template v-if="bill.adjustments?.length">
                <h3 class="history-title">月结后的费用调整</h3>
                <el-table :data="bill.adjustments" class="adjustments-table"><el-table-column prop="projectName" label="项目" min-width="130" /><el-table-column label="调整金额" min-width="140" align="right"><template #default="{ row }">{{ Number(row.amount) > 0 ? '+' : '' }}{{ money(row.amount) }} {{ filters.currency }}</template></el-table-column><el-table-column prop="reason" label="调整原因" min-width="180" /><el-table-column prop="operatorName" label="操作人" min-width="110" /><el-table-column prop="createTime" label="时间" min-width="170" /></el-table>
                <div class="table-footer"><span>原月结 {{ money(bill.totalAmount) }} + 调整 {{ money(bill.adjustmentAmount) }} = <b>调整后 {{ money(displayMonthAmount) }} {{ filters.currency }}</b></span></div>
              </template>
            </template>
          </section>

          <section v-if="bill && step > 1" class="expense-panel allocation-panel">
            <div class="section-header"><div><h2>{{ step === 2 ? '这些费用由谁承担？' : '项目分摊进度' }}</h2><p>{{ bill.status === 'DRAFT' ? '填写各负责人承担的比例，合计 100% 后即可下发到工作台。' : '负责人在自己的工作台分配到项目并提交，你在这里查看进度和月结。' }}</p></div><div v-if="canEditMonth" class="actions"><el-button :disabled="saving || dirty" @click="copyOwners">沿用上月比例</el-button><el-button icon="Plus" :disabled="saving" @click="addOwner">添加负责人</el-button></div><div v-else class="actions"><el-button v-if="bill.status === 'PUBLISHED' && canManage" :disabled="saving || loading" @click="recallMonth">退回修改</el-button><el-button v-if="bill.status === 'SETTLED' && canManage" :disabled="saving || loading" @click="openAdjustment">登记调整</el-button></div></div>
            <div v-if="step === 3" class="settlement-bar"><div><b>{{ bill.status === 'SETTLED' ? '本月已结算' : '确认月结' }}</b><p>{{ bill.status === 'SETTLED' ? '公共费用已计入项目成本。后续增减可登记调整，原记录保留。' : settleNotice }}</p><p v-if="bill.status === 'PUBLISHED'">待分给项目 {{ money(projectRemainingAmount) }} {{ filters.currency }} · {{ pendingOwnerCount }} 位负责人待提交</p></div><el-button v-if="bill.status === 'PUBLISHED' && canManage" type="primary" :disabled="!canSettle || loading" :loading="saving" @click="settleMonth">确认月结</el-button></div>
            <el-table :data="ownerRows" empty-text="请添加负责人并填写承担比例">
              <el-table-column v-if="bill.status === 'DRAFT'" label="部门" min-width="180"><template #default="{ row }"><el-select v-model="row.deptId" class="owner-department-select" filterable placeholder="先选择部门" no-data-text="该公司尚未设置启用的部门" :disabled="!canEditMonth" @change="changeOwnerDepartment(row)"><el-option v-for="dept in ownerDepartments" :key="dept.deptId" :label="dept.deptName" :value="dept.deptId" /></el-select></template></el-table-column>
              <el-table-column label="负责人" min-width="170"><template #default="{ row }"><el-select v-if="canEditMonth" v-model="row.ownerUserId" class="owner-person-select" filterable :disabled="!row.deptId" :placeholder="row.deptId ? '选择该部门负责人' : '请先选择部门'" no-data-text="该部门暂无可选负责人，请先完善人员所属部门"><el-option v-if="row.ownerUserId && !ownerOptions(row).some(owner => Number(owner.userId) === Number(row.ownerUserId))" :value="row.ownerUserId" :label="row.ownerName || ownerName(row.ownerUserId)" disabled /><el-option v-for="owner in ownerOptions(row)" :key="owner.userId" :label="owner.userName" :value="owner.userId" /></el-select><b v-else>{{ row.ownerName || ownerName(row.ownerUserId) }}</b></template></el-table-column>
              <el-table-column label="承担比例" min-width="155"><template #default="{ row }"><span v-if="canEditMonth" class="percentage-field"><el-input-number v-model="row.percentage" :min="0" :max="100" :precision="2" :controls="false" aria-label="负责人承担比例" /> %</span><span v-else>{{ row.percentage }}%</span></template></el-table-column>
              <el-table-column :label="`月承担额（${filters.currency}）`" min-width="150" align="right"><template #default="{ row, $index }">{{ money(canEditMonth && ownersDirty ? ownerAmountPreview($index) : row.amount) }}</template></el-table-column>
              <el-table-column label="项目分摊" min-width="170"><template #default="{ row }"><template v-if="bill.status !== 'DRAFT'"><el-tag :type="row.status === 'SUBMITTED' ? 'success' : 'warning'">{{ row.status === 'SUBMITTED' ? '已提交' : '待分摊提交' }}</el-tag><small>待分摊 {{ money(row.remainingAmount) }}</small></template><span v-else>下发后由负责人分配</span></template></el-table-column>
              <el-table-column v-if="canEditMonth" label="操作" width="80"><template #default="{ $index }"><el-button type="danger" link :disabled="saving" @click="ownerRows.splice($index, 1)">移除</el-button></template></el-table-column>
              <el-table-column v-else type="expand"><template #default="{ row }"><el-table class="project-details" :data="row.projects || []" empty-text="负责人尚未分配到项目"><el-table-column prop="projectName" label="项目" /><el-table-column label="比例"><template #default="{ row: project }">{{ project.percentage }}%</template></el-table-column><el-table-column label="月公共费用"><template #default="{ row: project }">{{ money(project.amount) }} {{ filters.currency }}</template></el-table-column></el-table></template></el-table-column>
            </el-table>
            <div class="table-footer"><span :class="percentComplete ? 'success-text' : 'warning-text'">比例合计：<b>{{ percentageTotal.toFixed(2) }}%</b><em v-if="ownersDirty"> · 尚未保存</em></span><div v-if="bill.status === 'DRAFT' && canManage" class="actions"><el-button :disabled="dirty || saving || loading" @click="selectStep(1)">上一步</el-button><el-button v-if="ownersDirty" :disabled="saving || loading" @click="restoreOwners">还原</el-button><el-button :disabled="!ownersDirty || entriesDirty || loading" :loading="saving" @click="saveOwners">暂存比例</el-button><el-button type="primary" :disabled="entriesDirty || !percentComplete || !ownerRows.length || loading" :loading="saving" @click="publishMonth">保存并下发</el-button></div></div>
            <p v-if="bill.status === 'DRAFT'" class="filter-note">部门来自所选公司的组织架构。先选部门，再选负责人；若没有可选人员，请先在人员管理中设置负责人的所属部门。</p>
          </section>
    </div>
        <el-drawer v-model="policiesDrawer" title="常用费用" size="min(1000px, 96vw)" append-to-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
          <section class="expense-panel">
            <div class="section-header"><div><h2>房租、水电等常用费用</h2><p>填一次，在有效期内每月自动带出。修改此处不改动已确认的月账；当月金额请在第一步修改。</p></div><el-button v-if="canManage" type="primary" icon="Plus" :disabled="loading || saving" @click="openPolicy()">添加费用</el-button></div>
            <el-table :data="visiblePolicies" empty-text="尚未添加费用规则">
              <el-table-column label="费用" min-width="150"><template #default="{ row }"><b>{{ row.name }}</b><small>{{ categoryLabel(row.category) }} · v{{ row.version }}</small></template></el-table-column>
              <el-table-column label="填写金额" min-width="160" align="right"><template #default="{ row }">{{ money(row.amount) }} {{ row.currency }}<small>{{ row.periodType === 'ANNUAL' ? '每年' : '每月' }} · 月均 {{ money(row.periodType === 'ANNUAL' ? Number(row.amount) / 12 : row.amount) }}</small></template></el-table-column>
              <el-table-column label="适用月份" min-width="190"><template #default="{ row }">{{ row.startMonth }} 至 {{ row.endMonth }}</template></el-table-column>
              <el-table-column label="状态" min-width="155"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '有效' : '已停用' }}</el-tag><el-tag v-if="row.estimated" type="warning" class="estimate-tag">暂估</el-tag></template></el-table-column>
              <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
              <el-table-column v-if="canManage" label="操作" width="145" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="saving" @click="openPolicy(row)">{{ row.status === 'ACTIVE' ? '修改 / 附件' : '查看' }}</el-button><el-button v-if="row.status === 'ACTIVE'" link type="danger" :disabled="saving" @click="disablePolicy(row)">停用</el-button></template></el-table-column>
            </el-table>
          </section>
        </el-drawer>
        <el-drawer v-model="historyDrawer" title="历史记录" size="min(1000px, 96vw)" append-to-body>
          <section class="expense-panel">
            <div class="section-header"><div><h2>月账历史</h2><p>查看公司的各月费用状态；下方展示所选月份和费用规则的操作依据。</p></div></div>
            <el-table :data="data.history" empty-text="公司暂无月账历史"><el-table-column label="版本" width="90"><template #default="{ row }">v{{ row.version }}</template></el-table-column><el-table-column prop="month" label="月份" /><el-table-column label="费用总额"><template #default="{ row }">{{ money(row.totalAmount) }} {{ row.currency || filters.currency }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }">{{ billStatusLabel(row.status) }}</template></el-table-column><el-table-column label="月结时间" min-width="175"><template #default="{ row }">{{ row.settledTime || '—' }}</template></el-table-column></el-table>
            <h3 class="history-title">操作与调整记录</h3>
            <el-table :data="data.events" empty-text="暂无操作记录"><el-table-column label="操作" min-width="135"><template #default="{ row }">{{ eventLabel(row.eventType) }}</template></el-table-column><el-table-column label="操作人" prop="operatorName" min-width="110" /><el-table-column label="说明" prop="reason" min-width="220" /><el-table-column label="时间" prop="createTime" min-width="175" /><el-table-column label="记录" width="95"><template #default="{ row }"><el-button v-if="row.snapshot" link type="primary" @click="openSnapshot(row)">查看依据</el-button></template></el-table-column></el-table>
          </section>
        </el-drawer>

    <el-dialog v-model="policyDialog" :title="policyForm.policyId ? '修改常用费用' : '添加费用'" width="min(660px, 95vw)" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <p>{{ bill ? '保存后，新增费用可加入本月明细。已有费用的本月金额请在第一步修改。' : '填写金额和适用时间，系统自动计算每个月的费用。' }}</p>
      <el-form :model="policyForm" label-position="top" class="expense-form" :disabled="saving || policyReadOnly">
        <div class="form-grid"><el-form-item label="费用名称" required><el-input v-model="policyForm.name" maxlength="100" placeholder="例如：办公场地房租" /></el-form-item><el-form-item label="费用类别" required><el-select v-model="policyForm.category"><el-option v-for="category in categories" :key="category.value" :label="category.label" :value="category.value" /></el-select></el-form-item></div>
        <div class="form-grid"><el-form-item label="填写方式" required><el-radio-group v-model="policyForm.periodType" @change="updateAnnualEnd"><el-radio-button value="MONTHLY">按月填写</el-radio-button><el-radio-button value="ANNUAL">按年填写</el-radio-button></el-radio-group></el-form-item><el-form-item :label="`${policyForm.periodType === 'ANNUAL' ? '年度总额' : '每月金额'}（${filters.currency}）`" required><el-input-number v-model="policyForm.amount" :min="0.01" :max="999999999999" :precision="2" :controls="false" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="开始月份" required><el-date-picker v-model="policyForm.startMonth" type="month" value-format="YYYY-MM" :clearable="false" @change="updateAnnualEnd" /></el-form-item><el-form-item label="结束月份（含）" required><el-date-picker v-model="policyForm.endMonth" type="month" value-format="YYYY-MM" :clearable="false" :disabled="policyForm.periodType === 'ANNUAL'" /></el-form-item></div>
        <div class="policy-preview"><span>预计每月费用</span><b>{{ money(policyForm.periodType === 'ANNUAL' ? Number(policyForm.amount || 0) / 12 : policyForm.amount) }} {{ filters.currency }}</b><small v-if="policyForm.periodType === 'ANNUAL'">连续 12 个月分摊，最后一个月处理分位尾差。</small></div>
        <el-form-item label="金额确定了吗？"><el-radio-group v-model="policyForm.estimated"><el-radio-button :value="false">已确定</el-radio-button><el-radio-button :value="true">先估算，月底确认</el-radio-button></el-radio-group></el-form-item>
        <el-collapse v-model="optionalFields" class="optional-fields"><el-collapse-item title="备注和附件（选填）" name="attachments">
        <el-form-item label="备注"><el-input v-model="policyForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="合同、账单周期或费用说明" /></el-form-item>
        <el-form-item label="合同或账单附件"><file-upload v-model="policyForm.attachmentUrls" :disabled="saving || policyReadOnly" :limit="5" :file-size="20" :file-type="attachmentTypes" /></el-form-item>
        </el-collapse-item></el-collapse>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="policyDialog = false">{{ policyReadOnly ? '关闭' : '取消' }}</el-button><el-button v-if="!policyReadOnly" type="primary" :loading="saving" @click="savePolicy">保存费用</el-button></template>
    </el-dialog>

    <el-dialog v-model="snapshotDialog" title="历史操作依据" width="min(800px, 95vw)" append-to-body>
      <el-descriptions :column="2" border><el-descriptions-item label="操作">{{ eventLabel(snapshotEvent.eventType) }}</el-descriptions-item><el-descriptions-item label="操作人">{{ snapshotEvent.operatorName || '—' }}</el-descriptions-item><el-descriptions-item label="时间">{{ snapshotEvent.createTime || '—' }}</el-descriptions-item><el-descriptions-item label="版本">{{ snapshot.version == null ? '—' : `v${snapshot.version}` }}</el-descriptions-item><el-descriptions-item label="说明" :span="2">{{ snapshotEvent.reason || '—' }}</el-descriptions-item><el-descriptions-item v-if="snapshot.name" label="费用名称">{{ snapshot.name }}</el-descriptions-item><el-descriptions-item v-if="snapshot.amount != null || snapshot.totalAmount != null" label="记录金额">{{ money(snapshot.totalAmount ?? snapshot.amount) }} {{ snapshot.currency || filters.currency }}</el-descriptions-item><el-descriptions-item v-if="snapshot.startMonth" label="适用月份" :span="2">{{ snapshot.startMonth }} 至 {{ snapshot.endMonth }}</el-descriptions-item></el-descriptions>
      <template v-if="snapshot.entries?.length"><h3 class="history-title">当时的费用明细</h3><el-table :data="snapshot.entries"><el-table-column prop="name" label="费用" /><el-table-column label="金额"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column><el-table-column label="账单状态"><template #default="{ row }">{{ row.estimated ? '暂估' : '已核实' }}</template></el-table-column><el-table-column prop="remark" label="说明" /></el-table></template>
      <template v-if="snapshot.ownerAllocations?.length"><h3 class="history-title">当时的负责人分摊</h3><el-table :data="snapshot.ownerAllocations"><el-table-column label="负责人"><template #default="{ row }">{{ row.ownerName || ownerName(row.ownerUserId) }}</template></el-table-column><el-table-column label="比例"><template #default="{ row }">{{ row.percentage }}%</template></el-table-column><el-table-column label="承担金额"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column><el-table-column label="项目"><template #default="{ row }"><div v-for="project in row.projects || []" :key="project.projectId">{{ project.projectName }} · {{ money(project.amount) }}</div></template></el-table-column></el-table></template>
      <template v-if="snapshot.projects?.length"><h3 class="history-title">当时的项目分摊</h3><el-table :data="snapshot.projects"><el-table-column prop="projectName" label="项目" /><el-table-column label="比例"><template #default="{ row }">{{ row.percentage }}%</template></el-table-column><el-table-column label="金额"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column></el-table></template>
      <el-alert v-if="snapshotError" :title="snapshotError" type="warning" :closable="false" class="section-gap" />
      <template #footer><el-button @click="snapshotDialog = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="adjustmentDialog" title="登记月结费用调整" width="min(540px, 95vw)" append-to-body :close-on-click-modal="false" :show-close="!saving">
      <el-alert title="调整直接计入所选项目的月度公共费用；原月账和分摊比例保留。增加成本填写正数，减少成本填写负数。" type="info" :closable="false" show-icon />
      <el-form :model="adjustmentForm" label-position="top" class="expense-form" :disabled="saving">
        <el-form-item label="项目" required><el-select v-model="adjustmentForm.projectId" filterable placeholder="选择本月承担费用的项目"><el-option v-for="project in adjustmentProjects" :key="project.projectId" :label="project.projectName" :value="project.projectId" /></el-select></el-form-item>
        <el-form-item :label="`调整金额（${filters.currency}）`" required><el-input-number v-model="adjustmentForm.amount" :precision="2" :controls="false" :min="-999999999999" :max="999999999999" /></el-form-item>
        <el-form-item label="调整原因" required><el-input v-model="adjustmentForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="adjustmentDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveAdjustment">确认登记调整</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessPublicExpenses">
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
  { value: 'RENT', label: '场地房租' }, { value: 'UTILITIES', label: '水电费' },
  { value: 'PROPERTY', label: '物业费' }, { value: 'NETWORK', label: '网络通讯' },
  { value: 'OFFICE', label: '办公杂费' }, { value: 'OTHER', label: '其他费用' }
]
const attachmentTypes = ['pdf', 'jpg', 'jpeg', 'png', 'webp', 'doc', 'docx', 'xls', 'xlsx', 'txt']
const routeCompany = Number(route.query.companyDeptId)
const filters = reactive({ companyDeptId: Number.isSafeInteger(routeCompany) && routeCompany > 0 ? routeCompany : null, month: validMonth(route.query.month) ? route.query.month : currentMonth(), currency: currencies.includes(route.query.currency) ? route.query.currency : 'CNY' })
const data = reactive({ companies: [], departments: [], owners: [], projects: [], policies: [], bill: null, history: [], events: [], canManage: false })
const loading = ref(false), loaded = ref(false), saving = ref(false), error = ref('')
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
const ownerPayload = () => ownerRows.value.map(row => ({ ownerUserId: row.ownerUserId, percentage: row.percentage }))
const entriesDirty = computed(() => bill.value?.status === 'DRAFT' && savedEntries !== JSON.stringify(entryPayload()))
const ownersDirty = computed(() => bill.value?.status === 'DRAFT' && savedOwners !== JSON.stringify(ownerPayload()))
const dirty = computed(() => entriesDirty.value || ownersDirty.value)
const entryTotal = computed(() => entryRows.value.reduce((sum, row) => sum + Number(row.amount || 0), 0))
const percentageTotal = computed(() => ownerRows.value.reduce((sum, row) => sum + Number(row.percentage || 0), 0))
const percentComplete = computed(() => Math.abs(percentageTotal.value - 100) < 0.000001)
const estimatedCount = computed(() => (bill.value?.entries || []).filter(entry => entry.estimated).length)
const pendingOwnerCount = computed(() => (bill.value?.ownerAllocations || []).filter(row => row.status !== 'SUBMITTED' && Number(row.amount) > 0).length)
const projectRemainingAmount = computed(() => bill.value?.status === 'DRAFT' ? Number(bill.value.totalAmount) : (bill.value?.ownerAllocations || []).reduce((sum, row) => sum + Number(row.remainingAmount ?? row.amount), 0))
const canSettle = computed(() => bill.value?.status === 'PUBLISHED' && filters.month < currentMonth() && !estimatedCount.value && pendingOwnerCount.value === 0 && Number(projectRemainingAmount.value.toFixed(2)) === 0)
const settleNotice = computed(() => {
  const issues = []
  if (filters.month >= currentMonth()) issues.push(`${addMonths(filters.month, 1)}-01 起可月结`)
  if (estimatedCount.value) issues.push(`${estimatedCount.value} 笔金额待确认，请退回修改后重新下发`)
  if (pendingOwnerCount.value) issues.push(`${pendingOwnerCount.value} 位负责人尚未提交`)
  if (projectRemainingAmount.value > 0) issues.push(`尚有 ${money(projectRemainingAmount.value)} ${filters.currency} 待分到项目`)
  return issues.length ? issues.join('；') : '费用已核实，负责人已完成项目分摊，可确认本月结算。'
})
const policyReadOnly = computed(() => policyForm.status === 'DISABLED' || !canManage.value)
const adjustmentProjects = computed(() => {
  const projectIds = new Set((bill.value?.ownerAllocations || []).flatMap(row => (row.projects || []).map(project => Number(project.projectId))))
  return data.projects.filter(project => projectIds.has(Number(project.projectId)) && project.currency === filters.currency)
})

const money = value => value == null || !Number.isFinite(Number(value)) ? '—' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const categoryLabel = value => categories.find(category => category.value === String(value || '').trim().toUpperCase())?.label || '其他费用'
const billStatusLabel = status => ({ DRAFT: '待下发', PUBLISHED: '已下发', SETTLED: '已结算' }[status] || status || '—')
const billStatusType = status => ({ DRAFT: 'info', PUBLISHED: 'warning', SETTLED: 'success' }[status] || 'info')
const ownerName = id => data.owners.find(owner => Number(owner.userId) === Number(id))?.userName || `负责人 #${id}`
const eventLabel = type => ({ POLICY: '更新费用规则', GENERATED: '生成月账', SYNC_POLICIES: '补充新增费用', ENTRIES: '核实费用明细', OWNERS: '保存负责人比例', COPY_OWNERS: '复制上月比例', PUBLISHED: '下发费用', BEFORE_RECALL: '退回前记录', RECALLED: '退回修改', PROJECTS: '保存项目分摊', COPY_PROJECTS: '复制项目分摊', SUBMITTED: '负责人提交', SETTLED: '确认月结', ADJUSTED: '登记费用调整' }[type] || type || '—')
function openSnapshot(event) {
  snapshotEvent.value = event
  snapshotError.value = ''
  try { const value = typeof event.snapshot === 'string' ? JSON.parse(event.snapshot) : event.snapshot; snapshot.value = Array.isArray(value) ? { projects: value } : value || {} }
  catch { snapshot.value = {}; snapshotError.value = '这条历史明细暂时无法读取，请保留操作时间并联系管理员。' }
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
  const cents = BigInt(Math.round(Number(bill.value?.totalAmount || 0) * 100))
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
  ownerRows.value = (bill.value?.ownerAllocations || []).map(row => {
    const owner = data.owners.find(candidate => Number(candidate.userId) === Number(row.ownerUserId))
    const department = ownerDepartments.value.find(dept => Number(dept.deptId) === Number(owner?.deptId))
    return { ...row, deptId: department?.deptId ?? null, ownerUserId: Number(row.ownerUserId), percentage: Number(row.percentage) }
  })
  savedOwners = JSON.stringify(ownerPayload())
}
function selectStep(next) {
  if (saving.value || loading.value || next === step.value) return
  if (dirty.value) return ElMessage.warning('请先点击暂存或下一步，保存当前修改。')
  if ((next > 1 && !bill.value) || (next === 3 && bill.value?.status === 'DRAFT')) return
  step.value = next
}
async function continueToOwners() {
  if (entriesDirty.value && !await saveEntries()) return
  step.value = 2
}
async function discardChanges() {
  if (!dirty.value) return true
  try { await ElMessageBox.confirm('费用明细或负责人比例尚未保存，继续将放弃这些修改。', '尚未保存', { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning' }); return true } catch { return false }
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
    loadedFilters = { ...filters }
    restoreEntries()
    restoreOwners()
    loaded.value = true
    return true
  } catch (failure) {
    if (sequence === loadSequence) error.value = `费用数据加载失败：${failure?.message || '请刷新重试'}。当前页面不展示未确认金额。`
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
  if (!policyForm.name?.trim() || !policyForm.category || !Number.isFinite(Number(policyForm.amount)) || Number(policyForm.amount) <= 0) return ElMessage.warning('请填写费用名称、类别和大于 0 的金额。')
  if (!validMonth(policyForm.startMonth) || !validMonth(policyForm.endMonth) || policyForm.startMonth > policyForm.endMonth) return ElMessage.warning('请填写有效的起止月份，结束月份不能早于开始月份。')
  const payload = { ...policyForm, companyDeptId: filters.companyDeptId, currency: filters.currency, name: policyForm.name.trim() }
  if (dirty.value && !await discardChanges()) return
  await mutate(() => savePublicExpensePolicy(payload), bill.value ? '费用已保存。新增费用请在第一步点击“加入新增费用”。' : '费用已保存，已算出本月金额。', () => { policyDialog.value = false })
}
async function disablePolicy(policy) {
  if (dirty.value && !await discardChanges()) return
  try { await ElMessageBox.confirm(`停用“${policy.name}”后，未生成月份不再带入该费用。已生成月账继续保留。`, '停用费用规则', { type: 'warning', confirmButtonText: '确认停用', cancelButtonText: '取消' }) } catch { return }
  await mutate(() => savePublicExpensePolicy({ ...policy, status: 'DISABLED' }), '费用规则已停用。')
}
async function generateMonth() { if (await mutate(() => generatePublicExpenseMonth({ ...filters }), '本月费用已保存，请分配负责人。')) step.value = 2 }
async function syncNewPolicies() { if (dirty.value) return ElMessage.warning('请先保存当前修改。'); await mutate(() => generatePublicExpenseMonth({ ...filters, syncNewPolicies: true, version: bill.value.version }), '已补充本月新增费用，原有账单金额继续保留，请核对负责人承担额。') }
async function saveEntries() {
  if (entryRows.value.some(row => row.amount == null || !Number.isFinite(Number(row.amount)) || Number(row.amount) < 0)) { ElMessage.warning('费用金额须为大于或等于 0 的数字。'); return false }
  return mutate(() => savePublicExpenseEntries(bill.value.billId, { version: bill.value.version, entries: entryPayload() }), '费用明细已保存。')
}
function addOwner() { ownerRows.value.push({ deptId: null, ownerUserId: null, percentage: undefined, amount: null, status: 'DRAFT' }) }
function ownerValidationError() {
  if (ownerRows.value.some(row => !row.deptId || !data.owners.some(owner => Number(owner.deptId) === Number(row.deptId) && Number(owner.userId) === Number(row.ownerUserId)))) return '请先选择部门，再选择该部门的有效负责人。'
  if (ownerRows.value.some(row => !row.ownerUserId || row.percentage == null || !Number.isFinite(Number(row.percentage)) || Number(row.percentage) <= 0 || Number(row.percentage) > 100)) return '请选择负责人并填写大于 0、不超过 100 的比例。'
  if (new Set(ownerRows.value.map(row => Number(row.ownerUserId))).size !== ownerRows.value.length) return '同一负责人只能分配一行。'
  if (percentageTotal.value > 100.000001) return '负责人比例合计不能超过 100%。'
  return ''
}
async function saveOwners() {
  const validationError = ownerValidationError()
  if (validationError) { ElMessage.warning(validationError); return false }
  return mutate(() => savePublicExpenseOwners(bill.value.billId, { version: bill.value.version, allocations: ownerPayload() }), '负责人比例已暂存。')
}
async function copyOwners() {
  if (ownerRows.value.length) { try { await ElMessageBox.confirm('用上月的负责人比例替换本月草稿比例？', '复制上月比例', { confirmButtonText: '复制并替换', cancelButtonText: '取消' }) } catch { return } }
  await mutate(() => copyPreviousPublicExpenseOwners(bill.value.billId, { version: bill.value.version }), '已复制上月比例，请核对后下发。')
}
async function publishMonth() {
  if (saving.value || loading.value) return
  if (!percentComplete.value || entriesDirty.value || !ownerRows.value.length) return ElMessage.warning('请确认费用明细已保存，负责人比例合计 100%。')
  const validationError = ownerValidationError()
  if (validationError) return ElMessage.warning(validationError)
  try { await ElMessageBox.confirm(`将 ${filters.month} 的 ${money(bill.value.totalAmount)} ${filters.currency} 下发给 ${ownerRows.value.length} 位负责人，进入项目分摊。`, '下发月费用', { confirmButtonText: '确认下发', cancelButtonText: '取消', type: 'info' }) } catch { return }
  // Saving proportions and publishing remain versioned backend operations. Keep
  // the page locked across both; a failed save must never continue to publish.
  await mutate(async () => {
    let version = bill.value.version
    if (ownersDirty.value) {
      const response = await savePublicExpenseOwners(bill.value.billId, { version, allocations: ownerPayload() })
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
  }, '费用已下发，负责人可在工作台分配到项目。')
}
async function recallMonth() {
  try {
    const { value } = await ElMessageBox.prompt('退回后可修改金额和比例，已有的负责人项目分摊会清空，需重新下发并提交。请填写原因。', '退回修改', { inputType: 'textarea', inputValidator: value => !!value?.trim() && value.trim().length <= 500 || '请填写 1 至 500 字原因', confirmButtonText: '确认退回', cancelButtonText: '取消', type: 'warning' })
    await mutate(() => recallPublicExpenseMonth(bill.value.billId, { version: bill.value.version, reason: value.trim() }), '已退回草稿，请修改后重新下发。')
  } catch { /* Dialog cancellation leaves the published bill unchanged. */ }
}
async function settleMonth() {
  if (!canSettle.value) return
  try { await ElMessageBox.confirm(`确认结算 ${filters.month} 公共费用 ${money(bill.value.totalAmount)} ${filters.currency}？各项目月成本将计入对应承担额，月账随后锁定。`, '确认月结', { confirmButtonText: '确认月结', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  await mutate(() => settlePublicExpenseMonth(bill.value.billId, { version: bill.value.version }), '月结完成，费用与分摊记录已锁定。')
}
function openAdjustment() { Object.assign(adjustmentForm, { projectId: null, amount: undefined, reason: '', requestKey: newSubmissionId() }); adjustmentDialog.value = true }
async function saveAdjustment() {
  if (!adjustmentForm.projectId || adjustmentForm.amount == null || !Number.isFinite(Number(adjustmentForm.amount)) || Number(adjustmentForm.amount) === 0 || !adjustmentForm.reason.trim()) return ElMessage.warning('请选择项目，填写非零调整金额和原因。')
  await mutate(() => adjustPublicExpenseMonth(bill.value.billId, { ...adjustmentForm, version: bill.value.version, reason: adjustmentForm.reason.trim() }), '费用调整已登记，原月账记录保留。', () => { adjustmentDialog.value = false })
}

onBeforeRouteLeave(() => discardChanges())
onMounted(load)
useBusinessRefreshOnReactivated(async () => { if (!dirty.value && !policyDialog.value && !adjustmentDialog.value) await load() })
</script>

<style scoped lang="scss">
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
