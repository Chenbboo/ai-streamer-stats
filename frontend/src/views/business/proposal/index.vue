<template>
  <div class="proposal-page">
    <header class="page-head">
      <div><h1>立项申请</h1></div>
      <el-button v-hasPermi="['business:project:proposal:add']" type="primary" icon="Plus" @click="openForm()">新建立项申请</el-button>
    </header>



    <el-tabs v-model="activeTab" class="proposal-tabs" @tab-change="loadActive">
      <el-tab-pane label="我的申请" name="mine">
        <el-card shadow="never">
          <el-table :data="mineRows" v-loading="loading" empty-text="还没有立项申请" @row-click="openDetail">
            <el-table-column label="申请" min-width="230"><template #default="{row}"><b>{{ row.projectName || '未命名草稿' }}</b><small>{{ row.proposalNo }}</small></template></el-table-column>
            <el-table-column prop="sponsorOwnerName" label="归属老板" width="140" />
            <el-table-column prop="companyName" label="归属公司" min-width="150" />
            <el-table-column label="治理方式" min-width="170"><template #default="{row}"><b>{{ managementLabel[row.managementMode] || row.managementMode }}</b><small>{{ closeMethodLabel[row.closeMethod] || row.closeMethod }}</small></template></el-table-column>
            <el-table-column label="状态" width="140"><template #default="{row}"><el-tag :type="proposalStatusTone(row)">{{ proposalStatusLabel(row) }}</el-tag></template></el-table-column>
            <el-table-column label="计划周期" width="210"><template #default="{row}">{{ planPeriod(row) }}</template></el-table-column>
            <el-table-column label="操作" width="235" fixed="right"><template #default="{row}"><div class="row-actions" @click.stop>
              <el-button v-if="canEditProposal(row)" link type="primary" @click="openForm(row)">{{ row.parentProjectId ? '继续完善' : '编辑' }}</el-button>
              <el-button v-if="canEditProposal(row)&&canLaunchProposal(row)" link type="success" @click="submitRow(row)">启动项目</el-button>
              <el-button v-if="row.status==='PENDING'" link type="warning" @click="withdrawRow(row)">撤回</el-button>
              <el-button v-if="row.status==='APPROVED'&&row.createdProjectId" link @click="openProject(row)">查看项目</el-button>
            </div></template></el-table-column>
          </el-table>
          <el-pagination v-model:current-page="minePage.pageNum" v-model:page-size="minePage.pageSize" :total="minePage.total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="loadMine" @size-change="minePage.pageNum=1;loadMine()" style="margin-top:16px" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane v-if="canViewDirectory" label="全部立项申请" name="directory">
        <el-card shadow="never"><el-table :data="directoryRows" v-loading="loading" empty-text="暂无立项申请"><el-table-column prop="projectName" label="项目名称" min-width="230"/><el-table-column prop="applicantName" label="主负责人/申请人" width="150"/><el-table-column label="当前处理人" width="150"><template #default="{row}">{{ row.parentProjectId && row.status==='DRAFT' ? (row.assignedOwnerName || '待指定') : (row.applicantName || '—') }}</template></el-table-column><el-table-column prop="sponsorOwnerName" label="归属老板" width="150"/><el-table-column label="状态" width="140"><template #default="{row}"><el-tag :type="proposalStatusTone(row)">{{ proposalStatusLabel(row) }}</el-tag></template></el-table-column><el-table-column label="权限" width="120"><template #default="{row}"><el-button v-if="row.canOpen" link type="primary" @click="openDetail(row)">查看详情</el-button></template></el-table-column></el-table><el-pagination v-model:current-page="directoryPage.pageNum" v-model:page-size="directoryPage.pageSize" :total="directoryPage.total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="loadDirectory" @size-change="directoryPage.pageNum=1;loadDirectory()" style="margin-top:16px" /></el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="formVisible" :title="formDialogTitle" width="min(1180px,97vw)" :before-close="beforeFormClose" append-to-body>
      <el-form ref="formRef" class="proposal-form" :disabled="saving" :model="form" :rules="rules" :validate-on-rule-change="false" label-width="112px" require-asterisk-position="left" scroll-to-error :scroll-into-view-options="{block:'center',behavior:'smooth'}">
        <p class="required-hint"><span>*</span> 启动前必填，可先存草稿</p>
        <el-alert v-if="!isNewTemplate" title="沿用历史成本规则" type="warning" :closable="false" show-icon />
        <el-alert v-if="isChildCreatorPhase" class="phase-alert" title="主负责人设置子项目框架" description="请确定子项目负责人、目标、周期、预算和按月收支。保存后，子负责人会在“我的申请”中选择成员、填写投入比例并启动项目。" type="info" :closable="false" show-icon />
        <el-alert v-else-if="isAssignedSubOwner" class="phase-alert" title="请完成子项目执行配置" description="主负责人已设置项目框架。请核对计划，选择项目成员，填写每人的投入比例并补充执行信息；完成后由你启动项目。" type="success" :closable="false" show-icon />
        <el-row :gutter="16">
          <el-col :span="24"><section class="plan-section form-section basic-information-section">
            <div class="plan-section-head"><div><h3><span class="section-index">01</span>基础信息</h3><p>填写项目归属、治理方式、目标和计划周期</p></div></div>
            <el-form-item v-if="form.parentProjectId" label="主项目"><el-input :model-value="form.parentProjectName || `项目 #${form.parentProjectId}`" disabled /></el-form-item>
            <el-row :gutter="16">
          <el-col v-if="!form.parentProjectId" :span="12"><el-form-item label="申请人/负责人"><el-input :model-value="userStore.nickName || userStore.name" disabled /></el-form-item></el-col>
          <el-col v-if="form.parentProjectId" :span="12"><el-form-item label="主负责人"><el-input :model-value="form.applicantName || userStore.nickName || userStore.name" disabled /></el-form-item></el-col>
          <el-col v-if="form.parentProjectId" :span="12"><el-form-item label="子项目负责人" prop="assignedOwnerUserId"><el-select v-model="form.assignedOwnerUserId" filterable placeholder="选择子项目负责人" style="width:100%" :disabled="isAssignedSubOwner" @change="changeAssignedOwner"><el-option v-for="u in options.owners || []" :key="u.userId" :label="bossOptionLabel(u)" :value="u.userId" /></el-select><small class="field-help">子负责人负责选择成员、补充执行资料并启动项目</small></el-form-item></el-col>
          <el-col v-else :span="12"><el-form-item label="归属老板" prop="sponsorOwnerUserId"><template #label><el-tooltip content="负责项目治理、验收和结算，不参与立项审批。"><span class="help-label" tabindex="0">归属老板 ⓘ</span></el-tooltip></template><el-select v-model="form.sponsorOwnerUserId" style="width:100%" placeholder="选择项目归属老板"><el-option v-for="item in options.bosses" :key="item.userId" :label="bossOptionLabel(item)" :value="item.userId" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="项目名称" prop="projectName"><el-input v-model="form.projectName" maxlength="160" show-word-limit /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="归属公司" prop="companyDeptId">
            <el-select v-model="form.companyDeptId" filterable placeholder="请选择归属公司" no-data-text="暂无可选择的公司" style="width:100%" @change="changeCompany">
              <el-option v-for="company in options.companies" :key="company.deptId" :label="company.deptName" :value="company.deptId" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="所属部门"><el-input :model-value="ownerDepartment" readonly :placeholder="form.parentProjectId && !form.assignedOwnerUserId ? '选择负责人后自动带入' : '负责人未设置所属部门'" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="核算方式" prop="accountingMode" required><el-select :model-value="accountingOptions[form.accountingMode] ? form.accountingMode : null" @update:model-value="form.accountingMode=$event" placeholder="请选择盈利型或价值型" style="width:100%"><el-option v-for="(item,value) in accountingOptions" :key="value" :label="`${item.label}（如：${item.example}）`" :value="value" class="accounting-mode-option"><span>{{ item.label }}（如：{{ item.example }}）</span><small>{{ item.description }}</small></el-option></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="管理模式" prop="managementMode" required><template #label><el-tooltip :content="managementOptions[form.managementMode]?.description"><span class="help-label" tabindex="0">管理模式 ⓘ</span></el-tooltip></template><el-select v-model="form.managementMode" style="width:100%"><el-option v-for="(item,value) in managementOptions" :key="value" :label="item.label" :value="value"><span>{{ item.label }}</span><small class="mode-option-hint">{{ item.hint }}</small></el-option></el-select></el-form-item></el-col>

          <el-col :span="12"><el-form-item label="结项方式" prop="closeMethod" required><el-select v-model="form.closeMethod" style="width:100%"><el-option v-for="(item,value) in closeMethodOptions" :key="value" :label="item.label" :value="value" /></el-select><small class="field-help">{{ closeMethodOptions[form.closeMethod]?.description }}</small></el-form-item></el-col>
          <el-col v-if="form.managementMode==='KEY_CONTROL'" :span="12"><el-form-item label="监管原因" prop="managementReason" required><el-input v-model="form.managementReason" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="监管原因及重点" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="目标模式" prop="goalMode" required><el-radio-group v-model="form.goalMode"><el-radio value="TOTAL">项目总目标</el-radio><el-radio value="NO_TOTAL">持续经营（每日目标）</el-radio></el-radio-group></el-form-item></el-col><el-col :span="24"><el-form-item label="项目目标" prop="objective"><el-input v-model="form.objective" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="填写整体目标；验收标准在下方填写" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="立项理由" prop="applicationReason"><el-input v-model="form.applicationReason" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="现状、问题或机会" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划开始" prop="planStartDate" required><el-date-picker v-model="form.planStartDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width:100%" @change="handleProjectPeriodChange" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划结束" prop="planEndDate" :required="!openEnded"><div class="end-date-line"><el-date-picker v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD" :disabled="openEnded" :disabled-date="disablePlanEndDate" :placeholder="openEnded ? '不限期' : '结束日期'" style="width:100%" @change="handleProjectPeriodChange" /><el-checkbox v-model="openEnded" @change="handleOpenEndedChange">不限期</el-checkbox></div></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="优先级" prop="priority" required><el-select v-model="form.priority" style="width:100%"><el-option label="低" value="LOW"/><el-option label="中" value="MEDIUM"/><el-option label="高" value="HIGH"/></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="主要风险" prop="riskSummary"><el-input v-model="form.riskSummary" type="textarea" :rows="2" maxlength="2000" show-word-limit placeholder="收入、成本或进度风险（选填）" /></el-form-item></el-col>
            </el-row>
          </section></el-col>
          <el-col v-if="canConfigureStaffing" :span="24"><div class="plan-section form-section"><div class="plan-section-head"><div><h3 :class="{'required-section':requiredPlanSections.staffing}"><span class="section-index">02</span>人员与投入比例</h3><p>在立项阶段一次设置，启动后直接形成项目人员计划</p></div><el-button size="small" type="primary" plain @click="addStaffing">增加人员</el-button></div>
            <el-table :data="form.staffingLines" size="small" empty-text="暂无人员，可启动后添加">
              <el-table-column label="人员" label-class-name="required-column" min-width="300"><template #default="{row}"><el-select v-model="row.userId" filterable style="width:100%" :disabled="!form.companyDeptId || isOwnerStaff(row)" :placeholder="form.companyDeptId ? '选择人员' : '请先选择归属公司'" @change="staffChanged(row)"><el-option v-if="row.userId && !options.staff.some(item=>Number(item.userId)===Number(row.userId))" :value="row.userId" :label="row.userName || (isOwnerStaff(row) ? (userStore.nickName || userStore.name) : String(row.userId))" /><el-option v-for="item in options.staff" :key="item.userId" :label="staffOptionLabel(item)" :value="item.userId" :disabled="staffAlreadySelected(item.userId,row)" /></el-select><div v-if="isNewTemplate" style="margin-top:8px"><div class="staff-rate-status"><span :class="staffBudgetStatus(row)?.status==='PENDING'?'danger-text':''">{{ staffBudgetStatus(row)?.issues?.join('；') || (budgetLoading?'计算中…':staffBudgetStatus(row)?'费率完整':'待计算') }}</span><details v-if="staffBudgetStatus(row)?.ratePeriods?.length" class="inline-help"><summary>费率详情</summary><small v-for="period in staffBudgetStatus(row).ratePeriods" :key="period.version + ':' + period.effectiveFrom" class="field-help">v{{ period.version }}：{{ period.effectiveFrom }} 至 {{ period.effectiveTo || '不限期' }}</small></details></div></div></template></el-table-column>

              <el-table-column label="岗位/角色" min-width="125"><template #default="{row}">{{ row.roleName || '—' }}</template></el-table-column>
              <el-table-column label="开始日月成本" min-width="155"><template #default="{row}">{{ staffRateDisplay(row,'monthlyCost') }}</template></el-table-column>
              <el-table-column label="投入比例" label-class-name="required-column" min-width="220">
                <template #default="{row}">
                  <div class="percentage-input"><el-input-number v-model="row.inputQuantity" :min="0.01" :max="100" :precision="2" :step="5" controls-position="right" aria-label="人员投入比例"/><span>%</span></div>
                  <el-popover placement="bottom-start" :width="720" trigger="click" @before-enter="loadStaffAllocationPlan(row,true)">
                    <template #reference><el-button class="allocation-link" link type="primary" :disabled="!row.userId || !row.planStartDate">{{ staffAllocationSummary(row) }}</el-button></template>
                    <div v-loading="staffAllocationLoading(row)" class="allocation-popover">
                      <template v-if="staffPeriodProjects(row).length">
                        <div class="allocation-head"><div><b>{{ row.userName || '所选人员' }}在本项目周期内的项目投入</b><small>周期：{{ row.allocationPlan.periodStartDate }} 至 {{ row.allocationPlan.periodEndDate || '不限期' }}；当前调整生效日：{{ row.allocationPlan.effectiveDate }}</small></div><el-button size="small" type="primary" :loading="saving" :disabled="staffAllocationLoading(row)" @click="saveForm(false,true)">保存</el-button></div>
                        <div class="allocation-row allocation-title"><span>项目 / 周期</span><span>负责人</span><span>投入比例</span></div>
                        <div v-for="item in row.allocationPlan.allocations" :key="`current:${item.projectId}`" class="allocation-row">
                          <span><b>{{ item.projectName }}</b><small>{{ item.projectNo }} · 当前可调整</small></span><span>{{ item.ownerName || '—' }}</span>
                          <span class="percentage-input"><el-input-number v-model="item.allocationValue" :min="0" :max="100" :precision="2" :step="5" controls-position="right"/><em>%</em></span>
                        </div>
                        <div v-for="item in staffPeriodOnlyProjects(row)" :key="`period:${item.projectId}`" class="allocation-row period-only-project">
                          <span><b>{{ item.projectName }}</b><small>{{ item.projectNo }} · {{ projectPeriod(item) }}</small></span><span>{{ item.ownerName || '—' }}</span>
                          <span class="period-allocation"><b>{{ number(item.allocationValue).toFixed(2) }}%</b><small>{{ periodProjectLabel(item) }}</small></span>
                        </div>
                        <template v-if="row.allocationPlan.allocations?.length">
                          <div class="allocation-row current-project"><span><b>{{ form.projectName || '本项目（待启动）' }}</b><small>本次立项</small></span><span>{{ proposalOwner?.nickName || proposalOwner?.userName || form.assignedOwnerName || userStore.nickName || userStore.name }}</span><span class="percentage-input"><el-input-number v-model="row.inputQuantity" :min="0.01" :max="100" :precision="2" :step="5" controls-position="right"/><em>%</em></span></div>
                          <div class="allocation-total" :class="{'danger-text':staffAllocationTotal(row)!==100}"><span>当前生效项目合计</span><b>{{ staffAllocationTotal(row).toFixed(2) }}%</b><small>{{ staffAllocationTotal(row)===100?'分配完整':'合计必须等于 100%' }}</small></div>
                          <div class="allocation-reason"><span class="required-mark" aria-hidden="true">*</span><el-input v-model="row.allocationPlan.reason" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="请填写本次跨项目投入调整原因"/></div>
                          <el-alert class="allocation-alert" type="info" :closable="false" show-icon title="周期内已结束或尚未开始的项目只展示历史投入；当前生效项目可直接调整。涉及其他负责人时，启动后将自动发起确认。"/>
                        </template>
                      </template>
                      <el-empty v-else :image-size="50" description="本项目周期内未参与其他项目，本项目比例可直接设置"/>
                    </div>
                  </el-popover>
                </template>
              </el-table-column>
              <el-table-column v-if="isNewTemplate" label="预计人员成本" min-width="200"><template #default="{row}"><b>{{ staffBudgetStatus(row)?.amount==null ? (budgetLoading?'计算中…':'待完善计划') : money(staffBudgetStatus(row).amount,staffBudgetStatus(row).currency||form.baseCurrency) }}</b><small class="estimated-daily-cost">预计日成本：{{ estimatedDailyCostDisplay(row) }}</small><small>按投入比例及预算期间计算</small></template></el-table-column>
              <template v-if="isNewTemplate">
                <el-table-column label="参与方式" label-class-name="required-column" min-width="145"><template #default="{row}"><el-select v-model="row.participationMode" @change="handleStaffParticipationChange(row)"><el-option label="跟随项目" value="FOLLOW_PROJECT"/><el-option label="自定义时间" value="CUSTOM"/><el-option label="不限期" value="UNLIMITED" :disabled="!openEnded"/></el-select></template></el-table-column>
                <el-table-column label="参与时间" label-class-name="required-column" min-width="320"><template #default="{row}"><span v-if="row.participationMode==='FOLLOW_PROJECT'">{{ planPeriod(form) }}</span><div v-else class="staff-period"><el-date-picker v-model="row.planStartDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disableStaffStartDate" placeholder="开始日期" @change="handleStaffParticipationChange(row)" /><template v-if="row.participationMode==='CUSTOM'"><span>至</span><el-date-picker v-model="row.planEndDate" type="date" value-format="YYYY-MM-DD" :disabled-date="date=>disableStaffEndDate(date,row)" placeholder="结束日期" @change="handleStaffParticipationChange(row)" /></template><span v-else>至 不限期</span></div></template></el-table-column>
              </template>
              <el-table-column v-else label="历史预计成本" min-width="175"><template #default="{row}">{{ row.estimatedCost==null?'由后台核对':money(row.estimatedCost,row.costCurrency||form.baseCurrency) }}</template></el-table-column>
              <el-table-column label="说明" min-width="150"><template #default="{row}"><el-input v-model="row.note" maxlength="500" placeholder="可选" /></template></el-table-column>
              <el-table-column width="55"><template #default="{$index}"><el-button v-if="!isOwnerStaff(form.staffingLines[$index])" link type="danger" @click="form.staffingLines.splice($index,1)">删</el-button><span v-else>负责人</span></template></el-table-column>
            </el-table><details v-if="isNewTemplate" class="inline-help"><summary>人员成本计算说明</summary><p>月成本按有效费率和这里填写的投入比例计入预算，不足整月按参与工作日折算。启动项目时，该比例同时写入项目投入分配；如该人员已有其他项目，请在投入比例下展开调整，全部有效项目合计须等于 100%。</p></details></div></el-col>
          <el-col v-else :span="24"><div class="plan-section form-section"><div class="plan-section-head"><div><h3><span class="section-index">02</span>人员与投入比例</h3><p>由子项目负责人完成执行人员配置</p></div></div><el-alert class="phase-alert" title="人员配置由子项目负责人完成" description="保存项目框架后，子负责人会选择本人及其他成员，并为每个人设置投入比例。" type="info" :closable="false" show-icon /></div></el-col>

          <el-col :span="24"><ProposalPlanDetails section-number="03" :date-type="planDateType" kind="targets" :form="form" :required-plan-sections="requiredPlanSections" :target-type-label="targetTypeLabel" :target-unit-options="targetUnitOptions" :revenue-type-label="revenueTypeLabel" :plan-end-date="openEnded ? null : form.planEndDate" :line-date-issue="lineDateIssue" @add-target="addTarget" @add-revenue="addRevenue" @change-target-type="changeTargetType" /></el-col>
          <el-col :span="24"><ProposalPlanDetails section-number="04" :date-type="planDateType" kind="revenue" :form="form" :required-plan-sections="requiredPlanSections" :target-type-label="targetTypeLabel" :target-unit-options="targetUnitOptions" :revenue-type-label="revenueTypeLabel" :plan-end-date="openEnded ? null : form.planEndDate" :line-date-issue="lineDateIssue" @add-target="addTarget" @add-revenue="addRevenue" @change-target-type="changeTargetType" /></el-col>

          <el-col :span="24"><div class="plan-section form-section"><div class="plan-section-head"><div><h3><span class="section-index">05</span>支出计划<span class="optional-label">（选填）</span></h3><p>可选择项目开始月前 6 个月；有限期项目最晚可选结束月后 6 个月，不限期项目不限制结束月份</p></div><el-button size="small" type="primary" plain @click="addExpense">增加支出</el-button></div><el-table :data="form.expenseLines" size="small" empty-text="暂无外部支出"><el-table-column label="类别" :label-class-name="form.expenseLines?.length ? 'required-column' : ''" width="145"><template #default="{row}"><el-select v-model="row.expenseCategory"><el-option v-for="(label,value) in expenseCategoryLabel" :key="value" :label="label" :value="value"/></el-select></template></el-table-column><el-table-column label="支出项目" :label-class-name="form.expenseLines?.length ? 'required-column' : ''" min-width="150"><template #default="{row}"><el-input v-model="row.itemName" maxlength="160"/></template></el-table-column><el-table-column label="具体用途" :label-class-name="form.expenseLines?.length ? 'required-column' : ''" min-width="190"><template #default="{row}"><el-input v-model="row.purpose" maxlength="500"/></template></el-table-column><el-table-column label="金额" :label-class-name="form.expenseLines?.length ? 'required-column' : ''" width="165"><template #default="{row}"><el-input-number v-model="row.amount" :min="0" :precision="2" controls-position="right"/></template></el-table-column><el-table-column label="发生方式" :label-class-name="form.expenseLines?.length ? 'required-column' : ''" width="125"><template #default="{row}"><el-select v-model="row.occurrenceType" placeholder="一次性"><el-option label="一次性" value="ONE_TIME"/><el-option label="每日" value="DAILY"/><el-option label="每周" value="WEEKLY"/><el-option label="每月" value="MONTHLY"/></el-select></template></el-table-column><el-table-column :label="planDateType==='date' ? '支出日期 / 开始' : '支出月份 / 开始'" width="145"><template #default="{row}"><ProposalMonthPicker :date-type="planDateType" v-model="row.occurDate" :start-date="form.planStartDate" :end-date="openEnded ? null : form.planEndDate" :before-start-months="6" :after-end-months="6" /><small v-if="lineDateIssue(row.occurDate,'expense')" class="danger-text">{{ lineDateIssue(row.occurDate,'expense') }}</small></template></el-table-column><el-table-column label="收款方" min-width="130"><template #default="{row}"><el-input v-model="row.counterparty" maxlength="160"/></template></el-table-column><el-table-column width="55"><template #default="{$index}"><el-button link type="danger" @click="form.expenseLines.splice($index,1)">删</el-button></template></el-table-column></el-table></div></el-col>
          <el-col :span="24"><div class="plan-section form-section budget-plan-section">
            <div class="plan-section-head"><div><h3><span class="section-index">06</span>预算设置</h3><p>设置预算方式并查看本期预计收支</p></div></div>
            <div v-if="form.parentProjectId" class="parent-funding-panel">
              <div class="parent-funding-head"><div><h3>主项目拨款</h3><p>草稿交接后即保留额度；启动后转为正式占用，不重复记为支出</p></div><el-tag type="info">{{ form.baseCurrency }}</el-tag></div>
              <div class="parent-funding-summary">
                <div><span>主项目总预算</span><b>{{ money(parentFunding.totalAmount,parentFunding.currency||form.baseCurrency) }}</b></div>
                <div><span>已启动子项目占用</span><b>{{ money(parentFunding.activeAllocatedAmount,parentFunding.currency||form.baseCurrency) }}</b></div>
                <div><span>其他草稿已保留</span><b>{{ money(parentFunding.reservedAllocatedAmount,parentFunding.currency||form.baseCurrency) }}</b></div>
                <div><span>本次可分配</span><b class="success-text">{{ money(parentFunding.availableAmount,parentFunding.currency||form.baseCurrency) }}</b></div>
              </div>
              <el-row :gutter="16">
                <el-col :span="10"><el-form-item label="拨款额度" prop="parentFundingAmount"><el-input-number v-model="form.parentFundingAmount" :disabled="isAssignedSubOwner" :min="0.01" :max="parentFunding.availableAmount==null?99999999999999.99:Math.max(0.01,Number(parentFunding.availableAmount))" :precision="2" controls-position="right" /><small class="field-help">分配后主项目余额：{{ money(parentFundingRemaining,parentFunding.currency||form.baseCurrency) }}</small></el-form-item></el-col>
                <el-col :span="14"><el-form-item label="拨款说明" prop="parentFundingReason"><el-input v-model="form.parentFundingReason" :disabled="isAssignedSubOwner" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="说明拨款用途和额度依据" /></el-form-item></el-col>
              </el-row>
              <el-alert v-if="parentFunding.budgetMode&&parentFunding.budgetMode!=='TOTAL'" type="error" :closable="false" show-icon title="主项目必须先设置总额预算，才能向子项目拨款" />
              <p class="funding-limit-note">子项目的人员预算＋业务预算不得超过拨款额度。</p>
            </div>
            <el-row :gutter="24" class="budget-section-grid">
              <el-col :span="24"><BudgetControlFields v-model="form.budget" prop-prefix="budget" :startup-required="startupRequired" compact /></el-col>
              <el-col :span="6"><el-form-item :label="form.budget.mode==='TOTAL'?'总预算上限':'本期计划预算'"><el-input :model-value="budgetDisplay('totalAmount')" readonly /><small class="field-help">人员预算＋业务预算</small></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="人员预算"><el-input :model-value="budgetDisplay('personnelAmount')" readonly /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="业务预算" prop="budget.businessAmount" :required="form.budget.mode==='TOTAL'"><div class="business-budget-inputs"><el-input-number :model-value="form.budget.mode==='TOTAL'?businessBudgetAmount:budgetEstimate.plannedBusinessAmount" @update:model-value="businessBudgetAmount=$event" :disabled="form.budget.mode!=='TOTAL'" :min="form.budget.mode==='TOTAL'?businessExpenseTotal:0" :max="99999999999999.99" :precision="2" controls-position="right" /><el-form-item label="币种" prop="baseCurrency" label-width="50px" required class="budget-currency"><el-select v-model="form.baseCurrency" :disabled="!!form.parentProjectId" aria-label="预算币种"><el-option label="人民币（CNY）" value="CNY"/><el-option label="越南盾（VND）" value="VND"/><el-option label="美元（USD）" value="USD"/></el-select></el-form-item></div><small class="field-help">{{ form.budget.mode==='TOTAL' ? `支出计划合计 ${money(businessExpenseTotal,form.baseCurrency)}，仅可在合计基础上增加预算` : '按支出计划计算' }}</small></el-form-item></el-col>
              <el-col v-if="openEnded" :span="8"><el-form-item label="预算周期" prop="budget.cycle" required><el-select v-model="form.budget.cycle" style="width:100%" @change="handleBudgetCycleChange"><el-option label="周度" value="WEEK"/><el-option label="月度" value="MONTH"/><el-option label="季度" value="QUARTER"/><el-option label="年度" value="YEAR"/></el-select></el-form-item></el-col>
              <el-col v-if="openEnded" :span="12"><el-form-item :label="budgetPeriodFieldLabel" prop="budget.anchorDate" required><BudgetPeriodPicker v-model:anchor-date="form.budget.anchorDate" :cycle="form.budget.cycle" compact /></el-form-item></el-col>
              <el-col :span="24"><p class="budget-period-summary">{{ budgetPeriodText }}</p><details class="inline-help"><summary>预算计算说明</summary><p v-if="openEnded">按所选预算周期测算，期满后在计划变更中续编。首期从项目开始日计算。<template v-if="form.budget.cycle==='WEEK'">周度为周一至周日。</template></p><p v-else>按整个项目期间测算。</p><p>人员预算按人员计划、日历和有效费率计算。未填日期的收支计入本期，业务预算未分配部分为计划余量。测算为计划估算，实际成本按项目核算规则计算。</p></details><el-alert v-if="budgetIssues.length" style="margin:10px 0" title="启动前需处理" :description="budgetIssues.join('；')" type="warning" :closable="false" show-icon /></el-col>
              <el-col :span="24"><div v-if="budgetError || budgetLoading" class="budget-refresh-status" role="status" aria-live="polite"><span>{{ budgetError?'测算失败，请重试':'正在更新测算…' }}</span><el-button v-if="budgetError" link type="primary" @click="retryBudgetEstimate">重新测算</el-button></div></el-col>
              <el-col v-if="isNewTemplate && form.staffingLines?.length" :span="24"><p class="field-help">已包含负责人人员成本</p></el-col>
              <el-col :span="24" class="budget-result-block"><h3>本期预计收支</h3><div class="finance-summary"><div><span>预计收入</span><b>{{ estimateMoney(planSummary.revenue) }}</b></div><div><span>业务成本</span><b>{{ estimateMoney(planSummary.external) }}</b></div><div><span>人员成本</span><b>{{ estimateMoney(planSummary.personnel) }}</b></div><div><span>预计总成本</span><b>{{ estimateMoney(planSummary.total) }}</b></div><div><span>预计利润</span><b :class="planSummary.profit==null?'':planSummary.profit<0?'danger-text':'success-text'">{{ estimateMoney(planSummary.profit) }}</b></div><div><span>利润率</span><b>{{ budgetError?'测算未更新':budgetLoading?'计算中…':planSummary.margin===null?'待完善计划':`${planSummary.margin.toFixed(2)}%` }}</b></div></div></el-col>
              <el-col v-if="budgetEstimate.monthlyForecasts?.length" :span="24"><div class="monthly-forecast"><div class="plan-section-head"><div><h3>按月预计收支与盈利</h3><p>从项目开始月逐月展示；不限期项目先展示未来 12 个月</p></div></div><el-table :data="budgetEstimate.monthlyForecasts" size="small" max-height="420"><el-table-column prop="month" label="月份" width="105"/><el-table-column label="预计收入" align="right"><template #default="{row}">{{ estimateMoney(row.revenueAmount) }}</template></el-table-column><el-table-column label="业务支出" align="right"><template #default="{row}">{{ estimateMoney(row.plannedBusinessAmount) }}</template></el-table-column><el-table-column label="人员成本" align="right"><template #default="{row}">{{ estimateMoney(row.personnelAmount) }}</template></el-table-column><el-table-column label="预计总成本" align="right"><template #default="{row}">{{ estimateMoney(row.plannedTotalCost) }}</template></el-table-column><el-table-column label="预计利润" align="right"><template #default="{row}"><b :class="Number(row.profit)<0?'danger-text':'success-text'">{{ estimateMoney(row.profit) }}</b></template></el-table-column></el-table></div></el-col>
            </el-row>
          </div></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="beforeFormClose()">取消</el-button><el-button :type="isChildCreatorPhase?'primary':'default'" :loading="saving" @click="saveForm(false)">{{ isChildCreatorPhase ? '保存并交给子负责人' : '保存草稿' }}</el-button><el-button v-if="canLaunchForm" type="primary" :loading="saving" @click="saveForm(true)">启动项目</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="min(720px,94vw)" append-to-body>
      <template #header><div><span class="eyebrow">{{ detail.proposalNo }}</span><h2>{{ detail.projectName || '未命名草稿' }}</h2></div></template>
      <el-alert v-if="detail.parentProjectId&&detail.status==='DRAFT'" class="phase-alert" :title="Number(detail.assignedOwnerUserId)===currentUserId?'待你继续完善并启动项目':'已转交子项目负责人继续完善'" :description="`当前处理人：${detail.assignedOwnerName || '待指定子项目负责人'}`" type="warning" :closable="false" show-icon />
      <div class="detail-grid"><div><span>主负责人</span><b>{{ detail.applicantName }}</b></div><div v-if="detail.parentProjectId"><span>当前处理人</span><b>{{ detail.assignedOwnerName }}</b></div><div v-if="detail.parentProjectId"><span>主项目拨款</span><b>{{ money(detail.parentFundingAmount,detail.baseCurrency) }}</b></div><div v-if="detail.parentProjectId"><span>拨款说明</span><b>{{ detail.parentFundingReason || '—' }}</b></div><div><span>归属老板</span><b>{{ detail.sponsorOwnerName }}</b></div><div><span>管理模式</span><b>{{ managementLabel[detail.managementMode] || detail.managementMode }}</b></div><div><span>结项方式</span><b>{{ closeMethodLabel[detail.closeMethod] || detail.closeMethod }}</b></div><div><span>计划周期</span><b>{{ planPeriod(detail) }}</b></div><div><span>预算控制</span><b>{{ detail.budgetMode==='NONE'?'不设上限':detail.budgetMode==='DAILY'?money(detail.dailyBudgetLimit,detail.baseCurrency)+' / 日':money(detail.budgetLimit,detail.baseCurrency) }}</b></div></div>
      <section v-if="detail.budget" class="detail-section budget-composition">
        <div class="budget-composition-head"><div><h3>预算构成</h3><p>本预算期间的计划成本组成</p></div><span class="budget-cycle-tag">{{ {PROJECT:'整个项目',WEEK:'周度',MONTH:'月度',QUARTER:'季度',YEAR:'年度'}[detail.budget.cycle] }}</span></div>
        <div class="budget-total-card"><div><span>本期计划预算</span><small>人员预算 + 业务预算</small></div><strong>{{ money(detail.budget.totalAmount,detail.baseCurrency) }}</strong></div>
        <div class="budget-breakdown">
          <div class="budget-breakdown-card personnel"><span>人员预算</span><b>{{ money(detail.budget.personnelAmount,detail.baseCurrency) }}</b><small>按人员计划测算</small></div>
          <div class="budget-breakdown-card business"><span>业务预算</span><b>{{ money(detail.budget.businessAmount,detail.baseCurrency) }}</b><small>按支出计划测算</small></div>
          <div v-if="detail.budgetMode==='DAILY'" class="budget-breakdown-card startup"><span>启动预算</span><b>{{ money(detail.startupBudgetLimit,detail.baseCurrency) }}</b><small>项目启动时的预算额度</small></div>
        </div>
        <div class="budget-meta">
          <div><span>预算期间</span><b>{{ detail.budget.startDate && detail.budget.endDate ? `${detail.budget.startDate} 至 ${detail.budget.endDate}` : '待完善日期并测算' }}</b></div>
          <div v-if="detail.budgetMode==='DAILY'"><span>每日统计口径</span><b>{{ detail.budgetScope==='CASH_EXPENSE'?'仅外部支出':'全成本（外部支出、人员及奖金）' }}</b></div>
          <div v-if="detail.budgetReason" class="budget-reason"><span>预算说明</span><b>{{ detail.budgetReason }}</b></div>
        </div>
        <el-alert v-if="detail.budget.status!=='READY'" :title="(detail.budget.issues||[]).join('；')" type="warning" :closable="false" />
      </section>
      <section v-if="!detailIsIncompleteDraft" class="detail-section"><h3>经营测算</h3><div class="finance-summary compact"><div><span>预计收入</span><b>{{ money(detail.estimatedRevenue,detail.baseCurrency) }}</b></div><div><span>业务成本</span><b>{{ money(detail.estimatedExternalCost,detail.baseCurrency) }}</b></div><div><span>人员成本</span><b>{{ money(detail.estimatedPersonnelCost,detail.baseCurrency) }}</b></div><div><span>预计总成本</span><b>{{ money(detail.estimatedTotalCost,detail.baseCurrency) }}</b></div><div><span>预计利润</span><b>{{ money(detail.expectedProfit,detail.baseCurrency) }}</b></div><div><span>计划人数</span><b>{{ detail.plannedHeadcount || 0 }} 人</b></div></div></section>
      <section v-if="detail.budget?.monthlyForecasts?.length" class="detail-section"><h3>按月预计收支与盈利</h3><el-table :data="detail.budget.monthlyForecasts" size="small" max-height="420"><el-table-column prop="month" label="月份" width="95"/><el-table-column label="收入"><template #default="{row}">{{ money(row.revenueAmount,detail.baseCurrency) }}</template></el-table-column><el-table-column label="总成本"><template #default="{row}">{{ money(row.plannedTotalCost,detail.baseCurrency) }}</template></el-table-column><el-table-column label="盈利"><template #default="{row}">{{ money(row.profit,detail.baseCurrency) }}</template></el-table-column></el-table></section>
      <section v-if="detail.revenueLines?.length" class="detail-section"><h3>收入测算明细</h3><el-table :data="detail.revenueLines" size="small"><el-table-column v-if="detail.revenueLines.some(row=>row.scenario && row.scenario!=='BASE')" label="历史预测"><template #default="{row}">{{ !row.scenario || row.scenario==='BASE' ? '计入测算' : `${scenarioLabel[row.scenario] || '备选'}（未计入测算）` }}</template></el-table-column><el-table-column prop="itemName" label="收入项目"/><el-table-column label="预计金额"><template #default="{row}">{{ money(row.expectedAmount,detail.baseCurrency) }}</template></el-table-column><el-table-column prop="assumptionText" label="测算依据"/></el-table></section>
      <section v-if="detail.expenseLines?.length" class="detail-section"><h3>支出计划明细</h3><el-table :data="detail.expenseLines" size="small"><el-table-column prop="itemName" label="支出项目"/><el-table-column prop="purpose" label="具体用途"/><el-table-column label="金额"><template #default="{row}">{{ money(row.amount,detail.baseCurrency) }}</template></el-table-column><el-table-column prop="counterparty" label="收款方"/></el-table></section>
      <section v-if="detail.staffingLines?.length" class="detail-section"><h3>人员与投入比例</h3><el-table :data="detail.staffingLines" size="small"><el-table-column label="人员"><template #default="{row}">{{ row.userName || '历史岗位汇总' }}</template></el-table-column><el-table-column prop="roleName" label="岗位"/><el-table-column v-if="newTemplate(detail)" label="投入比例"><template #default="{row}">{{ row.inputQuantity ?? 100 }}%</template></el-table-column><template v-if="newTemplate(detail)"><el-table-column label="参与方式"><template #default="{row}">{{ participationLabel[staffParticipationMode(row,detail)] }}</template></el-table-column><el-table-column label="参与时间"><template #default="{row}">{{ shortDate(row.planStartDate) }} 至 {{ row.planEndDate?shortDate(row.planEndDate):'不限期' }}</template></el-table-column></template><el-table-column v-else label="历史预计成本"><template #default="{row}">{{ money(row.estimatedCost,row.costCurrency||detail.baseCurrency) }}</template></el-table-column></el-table><p v-if="newTemplate(detail)">人员与投入比例已在立项阶段确定，启动后直接形成项目人员计划。</p></section>
      <section v-if="detail.targetLines?.length" class="detail-section"><h3>项目验收目标</h3><el-table :data="detail.targetLines" size="small"><el-table-column prop="targetName" label="目标"/><el-table-column label="目标值"><template #default="{row}">{{ row.targetType==='DELIVERY'?'通过验收':`${row.targetValue} ${row.unit}` }}</template></el-table-column><el-table-column prop="dueDate" label="完成日期"/><el-table-column prop="acceptanceEvidence" label="验收依据"/></el-table></section>
      <section class="detail-section"><h3>项目目标</h3><p>{{ detail.objective }}</p></section><section class="detail-section"><h3>立项理由</h3><p>{{ detail.applicationReason }}</p></section><section v-if="detail.managementReason" class="detail-section"><h3>重点监管原因</h3><p>{{ detail.managementReason }}</p></section><section v-if="detail.acceptanceCriteria" class="detail-section"><h3>验收标准</h3><p>{{ detail.acceptanceCriteria }}</p></section><section v-if="detail.reviewComment" class="detail-section review-comment"><h3>审批/撤回意见</h3><p>{{ detail.reviewComment }}</p></section>
      <section class="detail-section"><h3>操作记录</h3><el-timeline><el-timeline-item v-for="event in detail.events || []" :key="event.eventId" :timestamp="event.createTime"><b>{{ eventLabel[event.eventType] || event.eventType }}</b><p>{{ event.operatorName }}<span v-if="event.comment"> · {{ event.comment }}</span></p></el-timeline-item></el-timeline></section>
      <div class="drawer-actions"><el-button v-if="canEditProposal(detail)" type="primary" @click="openForm(detail)">{{ detail.parentProjectId ? '继续完善' : '修改' }}</el-button><el-button v-if="canEditProposal(detail)&&canLaunchProposal(detail)" type="success" @click="submitRow(detail)">启动项目</el-button><el-button v-if="detail.status==='APPROVED'&&detail.createdProjectId" @click="openProject(detail)">查看正式项目</el-button></div>
    </el-drawer>
  </div>
</template>

<script setup name="BusinessProjectProposals">
import { onBeforeRouteLeave } from 'vue-router'
import { newSubmissionId } from '@/utils/submission'
import { sumExpenseAmounts, normalizeBusinessBudget, rebaseBusinessBudget } from '@/utils/proposalBudget'
import ProposalPlanDetails from './ProposalPlanDetails.vue'
import { getBusinessProject } from '@/api/business/project'
import { ElMessage, ElMessageBox } from 'element-plus'
import useUserStore from '@/store/modules/user'
import { listProjectProposals, listProposalDirectory, getProjectProposal, getProjectProposalOptions, getProjectProposalStaffOptions, getProjectProposalStaffAllocationPreview, getProjectProposalParentFunding, estimateProjectProposalBudget, addProjectProposal, updateProjectProposal, submitProjectProposal, withdrawProjectProposal } from '@/api/business/proposal'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
import ProposalMonthPicker from './ProposalMonthPicker.vue'
import BudgetPeriodPicker from '@/components/BudgetPeriodPicker/index.vue'
import BudgetControlFields from '@/components/BudgetControlFields/index.vue'

const route=useRoute(),router=useRouter(), userStore=useUserStore(), loading=ref(false), saving=ref(false), activeTab=ref('mine')
const mineRows=ref([]),directoryRows=ref([]),options=reactive({bosses:[],companies:[],staff:[],calendars:[],unitPolicies:[]})
const formVisible=ref(false),detailVisible=ref(false),formRef=ref(),detail=ref({}),openEnded=ref(false)
const form=ref({})
const parentFunding=ref({})
const currentUserId=computed(()=>Number(userStore.id))
const isAssignedSubOwner=computed(()=>!!form.value.parentProjectId&&Number(form.value.assignedOwnerUserId)===currentUserId.value)
const isChildCreatorPhase=computed(()=>!!form.value.parentProjectId&&!isAssignedSubOwner.value)
const canConfigureStaffing=computed(()=>!isChildCreatorPhase.value)
const canLaunchForm=computed(()=>!form.value.parentProjectId||isAssignedSubOwner.value)
const canLaunchProposal=row=>!row?.parentProjectId||Number(row.assignedOwnerUserId)===currentUserId.value
const parentFundingRemaining=computed(()=>Math.max(0,Number(parentFunding.value.availableAmount||0)-Number(form.value.parentFundingAmount||0)))
const canEditProposal=row=>row?.canEdit===true&&(!row.parentProjectId||Number(row.assignedOwnerUserId)===currentUserId.value||row.parentFundingAmount==null&&Number(row.applicantUserId)===currentUserId.value)
const formDialogTitle=computed(()=>form.value.parentProjectId
  ? form.value.proposalId ? '完善子项目立项申请' : '新建子项目并转交'
  : form.value.proposalId ? '修改立项申请' : '新建立项申请')
const planDateType=computed(()=>openEnded.value&&form.value.budget?.cycle==='WEEK'?'date':'month')
const minePage=reactive({pageNum:1,pageSize:20,total:0}),directoryPage=reactive({pageNum:1,pageSize:20,total:0})
let formBaseline='',pendingCreate=null,closePrompt=null
const formSnapshot=()=>JSON.stringify({form:form.value,openEnded:openEnded.value})
const hasUnsavedChanges=()=>formVisible.value&&formBaseline!==formSnapshot()
async function confirmFormLeave(){
  if(!formVisible.value)return true
  if(saving.value)return false
  if(!hasUnsavedChanges())return true
  if(closePrompt)return closePrompt
  closePrompt=(async()=>{
    try{
      await ElMessageBox.confirm('表单有未保存的修改。保存草稿后离开，或放弃本次修改；关闭提示可继续编辑。','未保存的修改',{confirmButtonText:'保存草稿',cancelButtonText:'放弃修改',distinguishCancelAndClose:true,closeOnClickModal:false})
      return await saveForm(false)===true
    }catch(action){return action==='cancel'}
  })()
  try{return await closePrompt}finally{closePrompt=null}
}
async function beforeFormClose(done){if(await confirmFormLeave()){if(typeof done==='function')done();else formVisible.value=false}}
onBeforeRouteLeave(async()=>{const allowed=await confirmFormLeave();if(allowed)formVisible.value=false;return allowed})
function warnBeforeUnload(event){if(hasUnsavedChanges()){event.preventDefault();event.returnValue=''}}
onMounted(()=>window.addEventListener('beforeunload',warnBeforeUnload))
onBeforeUnmount(()=>window.removeEventListener('beforeunload',warnBeforeUnload))
const forecastPanels=ref([])
const canViewDirectory=computed(()=>userStore.roles.includes('admin')||userStore.permissions.includes('*:*:*')||userStore.permissions.includes('business:project:proposal:review'))
const statusLabel={DRAFT:'草稿',PENDING:'待启动',RETURNED:'已退回',WITHDRAWN:'已撤回',APPROVED:'已启动'}
const statusTone={DRAFT:'info',PENDING:'warning',RETURNED:'danger',WITHDRAWN:'info',APPROVED:'success'}
const proposalStatusLabel=row=>row?.parentProjectId&&row?.status==='DRAFT'
  ? Number(row.assignedOwnerUserId)===currentUserId.value?'待我完善':'待子负责人完善'
  : statusLabel[row?.status]||row?.status||'—'
const proposalStatusTone=row=>row?.parentProjectId&&row?.status==='DRAFT'?'warning':statusTone[row?.status]||'info'
const accountingOptions={PROFIT:{label:'盈利型',example:'电商销售',description:'看收入、成本和赚了多少'},VALUE:{label:'价值型',example:'员工培训',description:'看投入多少，目标有没有完成'}}
const accountingLabel={PROFIT:'盈利型',VALUE:'价值型',COST:'成本型（历史）',HYBRID:'混合型（历史）'}
const revenueTypeLabel={SALES:'商品销售',SERVICE:'服务费',COMMISSION:'佣金',LIVE:'直播收入',OTHER:'其他'}
const expenseCategoryLabel={PROCUREMENT:'采购',MARKETING:'推广',PLATFORM:'平台服务',TRAVEL:'差旅',OUTSOURCING:'外包',EQUIPMENT:'设备',LOGISTICS:'物流',OTHER:'其他'}
const targetTypeLabel={FINANCIAL:'财务',QUANTITY:'数量',SCHEDULE:'进度',QUALITY:'质量',EFFICIENCY:'效率',GROWTH:'增长',CUSTOMER:'客户',COMPLIANCE:'合规',OTHER:'价值 / 其他',DELIVERY:'成果验收'}
const targetUnitOptions=['个','件','条','次','人','单','份','套','台','场','天','小时','分钟','元','万元','%','分']
const scenarioLabel={CONSERVATIVE:'保守',BASE:'正常',OPTIMISTIC:'乐观'}
const managementOptions={LIGHT:{label:'轻量',hint:'短周期、低风险',description:'适合周期短、范围清晰、风险较低的项目；保留任务、成本和KPI，风险按异常登记。'},STANDARD:{label:'标准',hint:'周度跟踪、里程碑',description:'启用周度跟踪、里程碑、风险台账和预算预警，适合多数跨成员项目。'},KEY_CONTROL:{label:'重点监管',hint:'高风险、公司级项目',description:'强化里程碑、风险、预算分级预警和治理变更审批，立项时必须说明监管原因。'}}
const managementLabel={LIGHT:'轻量',STANDARD:'标准',KEY_CONTROL:'重点监管',SIMPLE:'轻量',DELIVERY:'标准'}
const closeMethodOptions={DIRECT:{label:'直接结项',description:'老板检查后结项'},RESULT_ACCEPTANCE:{label:'成果验收',description:'提交交付资料，老板验收后结项'},STAGED_ACCEPTANCE:{label:'阶段验收',description:'逐阶段验收，全部通过后申请结项'}}
const closeMethodLabel=Object.fromEntries(Object.entries(closeMethodOptions).map(([key,item])=>[key,item.label]))
const eventLabel={SELF_AUTHORIZED:'负责人自主启动',CREATE:'创建草稿',HANDOFF:'转交子负责人',EDIT:'修改草稿',SUBMIT:'提交审批',RESUBMIT:'重新提交',WITHDRAW:'撤回申请',RETURN:'退回修改',APPROVE:'批准立项',OWNER_LAUNCH:'负责人启动项目'}
const requiredRule=(message,trigger='change')=>[{required:true,message,trigger,...(trigger==='blur'?{type:'string',whitespace:true}:{})}]
const amountRule=(message,min=0)=>[{required:true,type:'number',min,max:99999999999999.99,message,trigger:'change'}]
const rules=computed(()=>({
  projectName:requiredRule('请输入项目名称','blur'),assignedOwnerUserId:form.value.parentProjectId?requiredRule('请选择子项目负责人'):[],sponsorOwnerUserId:form.value.parentProjectId?[]:requiredRule('请选择归属老板'),companyDeptId:requiredRule('请选择归属公司'),
  goalMode:requiredRule('请选择目标模式'),
  accountingMode:[{validator:(_rule,value,callback)=>callback(accountingOptions[value]?undefined:new Error('请选择盈利型或价值型')),trigger:'change'}],managementMode:requiredRule('请选择管理模式'),closeMethod:requiredRule('请选择结项方式'),
  objective:requiredRule('请填写项目目标','blur'),applicationReason:requiredRule('请填写立项理由','blur'),planStartDate:requiredRule('请选择计划开始日期'),
  planEndDate:openEnded.value?[]:requiredRule('请选择计划结束日期或勾选不限期'),priority:requiredRule('请选择优先级'),baseCurrency:requiredRule('请选择币种'),
  parentFundingAmount:form.value.parentProjectId?amountRule('请填写大于0的子项目拨款额度',0.01):[],
  parentFundingReason:form.value.parentProjectId?requiredRule('请填写子项目拨款说明','blur'):[],
  managementReason:form.value.managementMode==='KEY_CONTROL'?requiredRule('请填写监管原因','blur'):[],
  'budget.mode':requiredRule('请选择预算控制方式'),
  'budget.scope':form.value.budget?.mode==='DAILY'?requiredRule('请选择每日统计口径'):[],
  'budget.dailyLimit':form.value.budget?.mode==='DAILY'?amountRule('请填写大于0的每日预算上限',0.01):[],
  'budget.startupLimit':startupRequired.value?amountRule('本期有一次性支出，请填写大于0的启动预算',0.01):[],
  'budget.reason':form.value.budget?.mode==='NONE'?requiredRule('请说明不设预算上限的原因','blur'):[],
  'budget.businessAmount':form.value.budget?.mode==='TOTAL'?amountRule('业务预算不能低于支出计划合计',businessExpenseTotal.value):[],'budget.cycle':openEnded.value?requiredRule('请选择预算周期'):[],'budget.anchorDate':openEnded.value?requiredRule(`请选择${budgetPeriodFieldLabel.value}`):[],riskSummary:[]
}))

const isOwnerStaff=row=>Number(row.userId)===Number(form.value.parentProjectId?form.value.assignedOwnerUserId:(form.value.applicantUserId||userStore.id))
const proposalOwner=computed(()=>{
  const ownerId=form.value.parentProjectId?form.value.assignedOwnerUserId:(form.value.applicantUserId||userStore.id)
  return (options.owners||[]).find(owner=>String(owner.userId)===String(ownerId))
})
const ownerDepartment=computed(()=>proposalOwner.value?.deptName||'')
function syncOwnerCompany(){
  if(form.value.companyDeptId)return
  form.value.companyDeptId=options.companies.find(company=>String(company.deptId)===String(proposalOwner.value?.companyDeptId))?.deptId||null
}
async function changeCompany(){
  options.staff=[]
  allocationLoadedKeys.value.clear()
  await refreshStaffOptions()
  await refreshStaffAllocationPlans(true)
}
let previousAssignedOwnerId=null
async function changeAssignedOwner(){
  form.value.staffingLines=form.value.staffingLines.filter(row=>Number(row.userId)!==Number(previousAssignedOwnerId))
  previousAssignedOwnerId=form.value.assignedOwnerUserId
  const selected=(options.owners||[]).find(u=>u.userId===form.value.assignedOwnerUserId)
  form.value.assignedOwnerName=selected?.nickName||selected?.userName||''
  syncOwnerCompany();ensureOwnerStaff();await refreshStaffOptions();await refreshStaffAllocationPlans(true)
}
function ensureOwnerStaff(){if(!isNewTemplate.value)return;const id=Number(form.value.parentProjectId?form.value.assignedOwnerUserId:(form.value.applicantUserId||userStore.id));if(!id||form.value.staffingLines.some(row=>Number(row.userId)===id))return;form.value.staffingLines.unshift({...emptyStaffing(),userId:id,userName:form.value.parentProjectId?form.value.assignedOwnerName:(userStore.nickName||userStore.name),roleName:'项目负责人'})}
const emptyStaffing=()=>({participationMode:'FOLLOW_PROJECT',planStartDate:form.value.planStartDate||null,planEndDate:openEnded.value?null:form.value.planEndDate||null,inputUnit:'PERCENTAGE',inputQuantity:100,calendarId:defaultCalendar(),unitPolicyId:defaultUnitPolicy(),userId:null,userName:'',roleName:'',costPolicyId:null,costPolicyVersion:null,monthlyCostSnapshot:null,standardWorkDaysSnapshot:null,dailyCostSnapshot:null,costCurrency:null,estimatedCost:null,note:''})
const freshForm=()=>({assignedOwnerUserId:null,assignedOwnerName:'',parentProjectId:null,parentProjectName:'',parentFundingAmount:null,parentFundingReason:'',projectName:'',templateVersion:'LIGHT_V1',sponsorOwnerUserId:null,companyDeptId:null,projectType:'GENERAL',accountingMode:'PROFIT',managementMode:'LIGHT',closeMethod:'DIRECT',managementReason:'',acceptanceCriteria:'',objective:'',applicationReason:'',planStartDate:null,planEndDate:null,priority:'MEDIUM',baseCurrency:'CNY',budgetLimit:null,noBudget:'0',goalMode:'TOTAL',budget:{mode:'TOTAL',scope:'FULL_COST',dailyLimit:null,startupLimit:null,reason:'',cycle:'MONTH',anchorDate:null,businessAmount:0},revenueModel:'',peakCashNeed:null,riskSummary:'',revenueLines:[],expenseLines:[],staffingLines:[],targetLines:[]})
const isNewTemplate=computed(()=>!!form.value.templateVersion&&form.value.templateVersion!=='LEGACY_V1')
const newTemplate=item=>!!item?.templateVersion&&item.templateVersion!=='LEGACY_V1'
// All accounting modes use the same plan form. Revenue and expense sections are optional.
const requiredPlanSections=computed(()=>({
  staffing:!isNewTemplate.value,
  targets:true,
  revenue:false
}))
const showTargetLines=computed(()=>requiredPlanSections.value.targets)
const detailIsIncompleteDraft=computed(()=>{try{return !!JSON.parse(detail.value.templateSnapshotJson||'{}').draftPlan}catch{return false}})
watch(()=>form.value.accountingMode,()=>nextTick(()=>formRef.value?.clearValidate()))
async function showPlanError(message){
  const section=message.includes('收入')?'revenue':message.includes('目标')?'targets':message.includes('风险')?'risk':message.includes('支出')?'expenses':null
  await nextTick()
  const heading=[...document.querySelectorAll('.proposal-form h3')].find(el=>el.textContent.includes(section==='revenue'?'收入测算':section==='targets'?'项目验收目标':message.includes('人员')?'人员计划':message.includes('预算')?'预算设置':'支出计划'))
  heading?.scrollIntoView({block:'center',behavior:'smooth'})
  ElMessage.warning(message)
}
const defaultCalendar=()=>options.calendars.find(c=>coversWindow(c,form.value.planStartDate,form.value.planEndDate))?.calendarId??null
const defaultUnitPolicy=()=>options.unitPolicies.find(p=>coversWindow(p,form.value.planStartDate,form.value.planEndDate))?.unitPolicyId??null
const shortDate=value=>value?String(value).slice(0,10):''
const coversWindow=(policy,from,to)=>(!from||!policy.effectiveFrom||shortDate(policy.effectiveFrom)<=shortDate(from))&&(!to||!policy.effectiveTo||shortDate(policy.effectiveTo)>=shortDate(to))
const automaticCalendar=row=>options.calendars.find(c=>coversWindow(c,row.planStartDate,row.planEndDate))?.calendarId??null
const participationLabel={FOLLOW_PROJECT:'跟随项目',CUSTOM:'自定义时间',UNLIMITED:'不限期'}
const staffParticipationMode=(row,project)=>row.participationMode||(shortDate(row.planStartDate)===shortDate(project.planStartDate)&&(shortDate(row.planEndDate)||null)===(shortDate(project.planEndDate)||null)?'FOLLOW_PROJECT':!row.planEndDate?'UNLIMITED':'CUSTOM')

const number=value=>Number(value)||0
const businessExpenseTotal=computed(()=>sumExpenseAmounts(form.value.expenseLines||[]))
const businessBudgetAmount=computed({
  get:()=>normalizeBusinessBudget(form.value.budget?.businessAmount,businessExpenseTotal.value),
  set:value=>{form.value.budget.businessAmount=normalizeBusinessBudget(value,businessExpenseTotal.value)}
})
watch([()=>form.value,businessExpenseTotal],([current,total],[previous,previousTotal])=>{
  if(current!==previous||!current.budget)return
  current.budget.businessAmount=rebaseBusinessBudget(current.budget.businessAmount,previousTotal,total)
},{flush:'sync'})
const budgetPolicyPayload=()=>({budgetMode:form.value.budget.mode,budgetScope:form.value.budget.scope,dailyBudgetLimit:form.value.budget.mode==='DAILY'?form.value.budget.dailyLimit:null,startupBudgetLimit:form.value.budget.mode==='DAILY'?form.value.budget.startupLimit:null,budgetReason:form.value.budget.reason})
const budgetEstimate=ref({}),budgetLoading=ref(false),budgetError=ref('')
let budgetTimer,budgetRequest=0
const budgetRetry=ref(0)
const retryBudgetEstimate=()=>{budgetRetry.value++}
const budgetIssues=computed(()=>budgetError.value?[budgetError.value]:(budgetEstimate.value.issues||[]))
const budgetPeriodFieldLabel=computed(()=>({WEEK:'预算所属周',MONTH:'预算所属月份',QUARTER:'预算起始月份',YEAR:'预算所属年度'}[form.value.budget?.cycle]||'预算所属期间'))
// Derive the current period from inputs so required marks never depend on a stale estimate.
const startupRequired=computed(()=>{
  if(form.value.budget?.mode!=='DAILY')return false
  const start=shortDate(form.value.planStartDate),budget=form.value.budget
  let from=start,to=shortDate(form.value.planEndDate)
  if(openEnded.value&&budget.anchorDate){
    const anchor=shortDate(budget.anchorDate),date=new Date(anchor+'T00:00:00')
    if(budget.cycle==='WEEK'){
      date.setDate(date.getDate()-(date.getDay()+6)%7)
      from=localDate(date);date.setDate(date.getDate()+6)
    }else{
      date.setDate(1);from=localDate(date)
      date.setMonth(date.getMonth()+(budget.cycle==='YEAR'?12:budget.cycle==='QUARTER'?3:1),0)
    }
    to=localDate(date);if(start>from)from=start
  }
  return (form.value.expenseLines||[]).some(row=>
    (row.occurrenceType||row.expenseType||'ONE_TIME')==='ONE_TIME'&&Number(row.amount)>0&&
    (!row.occurDate||(!from||shortDate(row.occurDate)>=from)&&(!to||shortDate(row.occurDate)<=to)))
})
function localDate(date){return date.getFullYear()+'-'+String(date.getMonth()+1).padStart(2,'0')+'-'+String(date.getDate()).padStart(2,'0')}
const budgetPeriodText=computed(()=>{const b=budgetEstimate.value;return b.startDate&&b.endDate?`预算期间：${b.startDate} 至 ${b.endDate}（${{PROJECT:'整个项目',WEEK:'周度',MONTH:'月度',QUARTER:'季度',YEAR:'年度'}[b.cycle]||''}）`:'请选择项目日期与预算期间'})
const budgetDisplay=key=>budgetError.value?'测算未更新':budgetLoading.value?'计算中…':budgetEstimate.value[key]==null?'待完善计划':money(budgetEstimate.value[key],form.value.baseCurrency)
// Watch only fields consumed by budget calculation and date validation.
// Editing descriptions must not trigger requests or disturb the form layout.
const budgetCalculationInput=computed(()=>[
  budgetRetry.value,formVisible.value,openEnded.value,businessBudgetAmount.value,form.value.assignedOwnerUserId,form.value.companyDeptId,form.value.planStartDate,form.value.planEndDate,form.value.baseCurrency,
  ['mode','scope','dailyLimit','startupLimit','reason','cycle','anchorDate','businessAmount'].map(key=>form.value.budget?.[key]),
  (form.value.staffingLines||[]).map(row=>[row.userId,row.inputQuantity,row.participationMode,row.planStartDate,row.planEndDate,row.calendarId]),
  (form.value.expenseLines||[]).map(row=>[row.amount,row.occurrenceType,row.expenseType,row.occurDate]),
  (form.value.revenueLines||[]).map(row=>[row.expectedAmount,row.occurrenceType,row.expectedDate,row.scenario]),
  form.value.goalMode,(showTargetLines.value?(form.value.targetLines||[]):[]).map(row=>row.dueDate)
])
watch(()=>JSON.stringify(budgetCalculationInput.value),()=>{
  clearTimeout(budgetTimer);const request=++budgetRequest;budgetError.value=''
  if(!formVisible.value){budgetLoading.value=false;return}
  if(!form.value.planStartDate||!openEnded.value&&!form.value.planEndDate){budgetLoading.value=false;budgetError.value='请先填写项目起止日期，或选择不限期';return}
  if(openEnded.value&&!form.value.budget.anchorDate){budgetLoading.value=false;budgetError.value='请选择预算所属期间';return}
  budgetLoading.value=true
  budgetTimer=setTimeout(async()=>{try{const res=await estimateProjectProposalBudget({...form.value,...budgetPolicyPayload(),budget:{...form.value.budget,businessAmount:businessBudgetAmount.value,cycle:openEnded.value?form.value.budget.cycle:'PROJECT'}});if(request===budgetRequest)budgetEstimate.value=res.data||{}}catch(e){if(request===budgetRequest)budgetError.value=e?.message||'预算计算未完成，请检查输入或稍后重试'}finally{if(request===budgetRequest)budgetLoading.value=false}},450)
})
onBeforeUnmount(()=>{clearTimeout(budgetTimer);budgetRequest++})
const planSummary=computed(()=>{const b=budgetEstimate.value,revenue=b.revenueAmount==null?null:number(b.revenueAmount),external=b.plannedBusinessAmount==null?null:number(b.plannedBusinessAmount),personnel=b.personnelAmount==null?null:number(b.personnelAmount),total=personnel==null||external==null?null:external+personnel,profit=total==null||revenue==null?null:revenue-total;return{revenue,external,personnel,total,profit,margin:profit===null||!revenue?null:profit*100/revenue}})
function addRevenue(){form.value.revenueLines.push({scenario:'BASE',revenueType:'SALES',itemName:'',expectedAmount:0,occurrenceType:'ONE_TIME',expectedDate:null,assumptionText:''})}
function addExpense(){form.value.expenseLines.push({expenseCategory:'OTHER',itemName:'',purpose:'',counterparty:'',amount:0,occurrenceType:'ONE_TIME',occurDate:null,expenseType:'ONE_TIME',hasQuotation:'0'})}
function addStaffing(){form.value.staffingLines.push(emptyStaffing())}
function changeTargetType(row){if(row.targetType==='DELIVERY'){row.targetValue=1;row.unit='项'}}
function addTarget(){form.value.targetLines.push({targetType:['VALUE','HYBRID'].includes(form.value.accountingMode)?'DELIVERY':'QUANTITY',targetName:'',targetValue:['VALUE','HYBRID'].includes(form.value.accountingMode)?1:0,unit:['VALUE','HYBRID'].includes(form.value.accountingMode)?'项':'',dueDate:null,acceptanceEvidence:''})}
async function ensureOptions(force=false){if(!force&&options.bosses.length&&options.companies.length)return;const res=await getProjectProposalOptions();Object.assign(options,res.data||{})}
function plannedDays(){if(!form.value.planStartDate||openEnded.value||!form.value.planEndDate)return null;const start=new Date(`${form.value.planStartDate}T00:00:00`),end=new Date(`${form.value.planEndDate}T00:00:00`);return Math.max(1,Math.floor((end-start)/86400000)+1)}
function syncStaffRow(row,item){if(!item)return;Object.assign(row,{userId:item.userId,userName:item.nickName||item.accountName,roleName:item.positionName||'项目成员'});if(isNewTemplate.value)Object.assign(row,{costPolicyId:null,costPolicyVersion:null,monthlyCostSnapshot:null,standardWorkDaysSnapshot:null,dailyCostSnapshot:null,costCurrency:null,estimatedCost:null})}
async function staffChanged(row){Object.assign(row,{costPolicyId:null,costPolicyVersion:null,monthlyCostSnapshot:null,standardWorkDaysSnapshot:null,dailyCostSnapshot:null,costCurrency:null,estimatedCost:null,allocationPlan:null});syncStaffRow(row,options.staff.find(item=>Number(item.userId)===Number(row.userId)));await loadStaffAllocationPlan(row,true)}
const estimateMoney=value=>budgetError.value?'测算未更新':budgetLoading.value?'计算中…':value==null?'待完善计划':money(value,form.value.baseCurrency)
const staffBudgetStatus=row=>budgetEstimate.value.staffingStatus?.find(item=>String(item.userId)===String(row.userId))
const allocationLoadingKeys=ref(new Set()),allocationLoadedKeys=ref(new Set())
const staffAllocationKey=row=>`${row?.userId||''}:${shortDate(row?.planStartDate)}:${shortDate(row?.planEndDate)}`
const staffAllocationLoading=row=>allocationLoadingKeys.value.has(staffAllocationKey(row))
const staffAllocationTotal=row=>Number((number(row?.inputQuantity)+(row?.allocationPlan?.allocations||[]).reduce((sum,item)=>sum+number(item.allocationValue),0)).toFixed(2))
const staffPeriodProjects=row=>row?.allocationPlan?.periodProjects||row?.allocationPlan?.allocations||[]
const staffPeriodOnlyProjects=row=>staffPeriodProjects(row).filter(item=>!item.editable)
const projectPeriod=item=>`${shortDate(item.projectStartDate)||shortDate(item.joinedDate)||'未记录'} 至 ${shortDate(item.projectEndDate)||shortDate(item.leftDate)||'不限期'}`
const periodProjectLabel=item=>({ENDED:'已结束项目',UPCOMING:'后续项目',PERIOD:'周期内项目'})[item.periodState]||'周期内项目'
function staffAllocationSummary(row){
  const key=staffAllocationKey(row)
  if(allocationLoadingKeys.value.has(key))return '正在加载其他项目投入…'
  const periodProjects=staffPeriodProjects(row)
  if(periodProjects.length){const current=row.allocationPlan.allocations||[],other=current.reduce((sum,item)=>sum+number(item.allocationValue),0);return `周期内 ${periodProjects.length} 个项目 · 当前 ${other.toFixed(2)}%`}
  return allocationLoadedKeys.value.has(key)?'未参与其他项目':'查看其他项目投入'
}
async function loadStaffAllocationPlan(row,force=false){
  const effectiveDate=shortDate(row?.planStartDate),periodEndDate=shortDate(row?.planEndDate)||undefined,userId=row?.userId,companyDeptId=form.value.companyDeptId
  if(!isNewTemplate.value||!userId||!effectiveDate||!companyDeptId)return
  const key=`${userId}:${effectiveDate}:${periodEndDate||''}`
  if(!force&&allocationLoadedKeys.value.has(key))return
  const previous=row.allocationPlan
  allocationLoadingKeys.value.add(key)
  try{
    const res=await getProjectProposalStaffAllocationPreview({companyDeptId,userId,effectiveDate,periodEndDate})
    if(companyDeptId!==form.value.companyDeptId||Number(row.userId)!==Number(userId)||shortDate(row.planStartDate)!==effectiveDate||shortDate(row.planEndDate)!==(periodEndDate||''))return
    const data=res.data||{},projects=data.projects||[],periodProjects=data.periodProjects||projects
    if(!periodProjects.length)row.allocationPlan=null
    else{
      const previousValues=new Map((previous?.allocations||[]).map(item=>[String(item.projectId),item.allocationValue]))
      row.allocationPlan={effectiveDate:data.effectiveDate,periodStartDate:data.periodStartDate||effectiveDate,periodEndDate:data.periodEndDate||null,versionToken:data.versionToken,reason:previous?.reason||'',periodProjects,allocations:projects.map(item=>({...item,editable:true,originalValue:item.allocationValue,allocationValue:previousValues.has(String(item.projectId))?previousValues.get(String(item.projectId)):item.allocationValue}))}
    }
    allocationLoadedKeys.value.add(key)
  }catch(error){ElMessage.warning(error?.message||'其他项目投入加载失败，请稍后重试')}
  finally{allocationLoadingKeys.value.delete(key)}
}
async function refreshStaffAllocationPlans(force=false){await Promise.all((form.value.staffingLines||[]).map(row=>loadStaffAllocationPlan(row,force)))}
function shiftedMonth(value,offset){const date=new Date(`${String(value).slice(0,7)}-01T00:00:00`);date.setMonth(date.getMonth()+offset);return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`}
function lineDateIssue(value,rule='project'){
  if(!value)return ''
  const date=shortDate(value)
  if(rule==='revenue'||rule==='expense'){
    const month=date.slice(0,7),minimum=form.value.planStartDate?shiftedMonth(form.value.planStartDate,-6):'',maximum=!openEnded.value&&form.value.planEndDate?shiftedMonth(form.value.planEndDate,6):''
    const name=rule==='revenue'?'收入':'支出'
    if(minimum&&month<minimum||maximum&&month>maximum)return openEnded.value?`${name}月份不得早于项目开始月前 6 个月`:`${name}月份须在项目开始月前 6 个月至结束月后 6 个月内`
    return ''
  }
  if(date<form.value.planStartDate||!openEnded.value&&form.value.planEndDate&&date>form.value.planEndDate)return '日期须在项目起止范围内'
  if(rule==='after-start'&&form.value.planStartDate&&date.slice(0,7)<=form.value.planStartDate.slice(0,7))return '完成月份须晚于项目开始月份'
  return ''
}
function staffRateDisplay(row,field){
  if(!row.userId)return '—'
  const staff=options.staff.find(item=>Number(item.userId)===Number(row.userId))
  if(!staff)return '—'
  if(!staff.rawCostVisible)return '无成本查看权限'
  if(staff.costMode!=='MONTHLY'||staff[field]==null)return '未配置'
  return money(staff[field],staff.costCurrency||'CNY')
}
function estimatedDailyCostDisplay(row){
  if(!row.userId)return '—'
  const staff=options.staff.find(item=>Number(item.userId)===Number(row.userId))
  if(!staff)return '—'
  if(!staff.rawCostVisible)return '无成本查看权限'
  if(staff.dailyCost==null)return '待完善'
  const allocation=row.inputQuantity==null?100:Number(row.inputQuantity)
  if(!Number.isFinite(allocation)||allocation<=0)return '待填写投入比例'
  return `${money(Number(staff.dailyCost)*allocation/100,staff.costCurrency||form.value.baseCurrency)} / 工作日`
}
function syncParticipation(row){
  if(row.participationMode==='FOLLOW_PROJECT')Object.assign(row,{planStartDate:form.value.planStartDate||null,planEndDate:openEnded.value?null:form.value.planEndDate||null})
  else if(row.participationMode==='UNLIMITED')Object.assign(row,{planStartDate:row.planStartDate||form.value.planStartDate||null,planEndDate:null})
  else Object.assign(row,{planStartDate:row.planStartDate||form.value.planStartDate||null,planEndDate:row.planEndDate||(!openEnded.value?form.value.planEndDate:null)})
  if(!options.calendars.some(c=>String(c.calendarId)===String(row.calendarId)&&coversWindow(c,row.planStartDate,row.planEndDate)))row.calendarId=automaticCalendar(row)
  row.inputUnit='PERCENTAGE';if(row.inputQuantity==null)row.inputQuantity=100;row.unitPolicyId=row.unitPolicyId||defaultUnitPolicy()
}
async function handleStaffParticipationChange(row){syncParticipation(row);await loadStaffAllocationPlan(row,true)}
function syncFollowingStaff(){for(const row of form.value.staffingLines||[])if(row.participationMode==='FOLLOW_PROJECT')syncParticipation(row)}
function disableStaffStartDate(date){const value=date.getTime(),start=form.value.planStartDate?new Date(`${form.value.planStartDate}T00:00:00`).getTime():null,end=!openEnded.value&&form.value.planEndDate?new Date(`${form.value.planEndDate}T00:00:00`).getTime():null;return start!==null&&value<start||end!==null&&value>end}
function disableStaffEndDate(date,row){return disableStaffStartDate(date)||!!row.planStartDate&&date.getTime()<new Date(`${row.planStartDate}T00:00:00`).getTime()}
function recomputeAllStaffCosts(){if(isNewTemplate.value)return;const days=plannedDays();for(const row of form.value.staffingLines||[]){const monthly=row.monthlyCostSnapshot,daily=row.dailyCostSnapshot;row.estimatedCost=monthly==null?null:days===null?Number(monthly):daily==null?null:Number((Number(daily)*days).toFixed(2))}}
let staffRequest=0
async function refreshStaffOptions(){
  const request=++staffRequest,company=form.value.companyDeptId
  if(!company){options.staff=[];return}
  const res=await getProjectProposalStaffOptions({effectiveDate:form.value.planStartDate||undefined,companyDeptId:company})
  if(request!==staffRequest||company!==form.value.companyDeptId)return
  options.staff=res.data||[]
  for(const row of form.value.staffingLines||[]){
    if(!row.userId)continue
    const item=options.staff.find(option=>Number(option.userId)===Number(row.userId))
    if(item)syncStaffRow(row,item)
    else if(isOwnerStaff(row)){ElMessage.warning('负责人不在所选公司的有效人员范围，请核对公司与人员资料')}
    else {row.userId=null;row.userName='';row.roleName='';row.estimatedCost=null;row.allocationPlan=null;ElMessage.warning('部分人员不在所选公司或日期的有效任职范围，请重新选择；参与方式和日期已保留')}
  }
}
const staffOptionLabel=item=>{const name=item.nickName&&item.nickName!==item.accountName?`${item.nickName}（${item.accountName}）`:item.nickName||item.accountName;return `${name} · ${item.companyName||'未归属公司'} · ${item.positionName||'项目成员'}`}
const staffAlreadySelected=(userId,current)=>form.value.staffingLines?.some(row=>row!==current&&Number(row.userId)===Number(userId))
let mineRequest=0,directoryRequest=0
async function loadMine(){const request=++mineRequest;const res=await listProjectProposals({pageNum:minePage.pageNum,pageSize:minePage.pageSize});if(request!==mineRequest)return;minePage.total=Number(res.total)||0;const last=Math.max(1,Math.ceil(minePage.total/minePage.pageSize));if(minePage.pageNum>last){minePage.pageNum=last;return loadMine()}mineRows.value=res.rows||[]}
async function loadDirectory(){if(!canViewDirectory.value)return;const request=++directoryRequest;const res=await listProposalDirectory({pageNum:directoryPage.pageNum,pageSize:directoryPage.pageSize});if(request!==directoryRequest)return;directoryPage.total=Number(res.total)||0;const last=Math.max(1,Math.ceil(directoryPage.total/directoryPage.pageSize));if(directoryPage.pageNum>last){directoryPage.pageNum=last;return loadDirectory()}directoryRows.value=res.rows||[]}
async function loadActive(){loading.value=true;try{if(activeTab.value==='mine')await loadMine();else await loadDirectory()}finally{loading.value=false}}
async function refreshAll(){await loadMine();if(activeTab.value==='directory')await loadDirectory()}
async function loadParentFunding(){
  if(!form.value.parentProjectId){parentFunding.value={};return}
  const params=form.value.proposalId?{proposalId:form.value.proposalId}:{}
  const res=await getProjectProposalParentFunding(form.value.parentProjectId,params)
  parentFunding.value=res.data||{}
  if(parentFunding.value.currency)form.value.baseCurrency=parentFunding.value.currency
}
async function openForm(row,parent){if(!await confirmFormLeave())return;forecastPanels.value=[];allocationLoadingKeys.value.clear();allocationLoadedKeys.value.clear();await ensureOptions(true);const source=row?.proposalId?(await getProjectProposal(row.proposalId)).data:{...freshForm(),...(parent?{parentProjectId:parent.projectId,parentProjectName:parent.projectName,companyDeptId:parent.companyDeptId,sponsorOwnerUserId:parent.sponsorOwnerUserId||parent.initiatorUserId,baseCurrency:parent.baseCurrency||'CNY'}:{})};const legacyMode=source.managementMode==='SIMPLE'?'LIGHT':source.managementMode==='DELIVERY'?'STANDARD':source.managementMode;const legacyClose=source.closeMethod||(source.managementMode==='DELIVERY'?'RESULT_ACCEPTANCE':'DIRECT');openEnded.value=source.budget?.projectOpenEnded ?? (!!row?.proposalId&&!!source.planStartDate&&!source.planEndDate);form.value={...freshForm(),...source,templateVersion:source.templateVersion||(row?.proposalId?'LEGACY_V1':'LIGHT_V1'),managementMode:legacyMode,closeMethod:legacyClose,revenueLines:(source.revenueLines||[]).map(item=>({...item,scenario:item.scenario||'BASE'})),expenseLines:source.expenseLines||[],staffingLines:(source.staffingLines||[]).map(item=>{const from=shortDate(item.planStartDate)||null,to=shortDate(item.planEndDate)||null;const mode=item.participationMode||(from===shortDate(source.planStartDate)&&(to||null)===(shortDate(source.planEndDate)||null)?'FOLLOW_PROJECT':!to?'UNLIMITED':'CUSTOM');return{...item,participationMode:mode,planStartDate:from,planEndDate:to,inputUnit:'PERCENTAGE',inputQuantity:item.inputQuantity??100,unitPolicyId:item.unitPolicyId||options.unitPolicies[0]?.unitPolicyId||null}}),targetLines:(source.targetLines||[]).map(item=>({...item,targetType:({RESULT:'QUANTITY',VALUE:'OTHER'})[item.targetType]||item.targetType}))};form.value.budget={mode:source.budgetMode||'TOTAL',scope:source.budgetScope||'FULL_COST',dailyLimit:source.dailyBudgetLimit,startupLimit:source.startupBudgetLimit,reason:source.budgetReason||'',cycle:openEnded.value?'MONTH':'PROJECT',anchorDate:source.planStartDate?shortDate(source.planStartDate).slice(0,7)+'-01':null,businessAmount:Number(source.estimatedExternalCost)||0,...source.budget};form.value.budget.businessAmount=businessBudgetAmount.value;if(openEnded.value&&!['WEEK','MONTH','QUARTER','YEAR'].includes(form.value.budget.cycle))form.value.budget.cycle='MONTH';await loadParentFunding();budgetEstimate.value={};budgetError.value='';previousAssignedOwnerId=form.value.assignedOwnerUserId;syncOwnerCompany();ensureOwnerStaff();await refreshStaffOptions();await refreshStaffAllocationPlans();pendingCreate=null;if(!form.value.proposalId)form.value.createRequestKey=newSubmissionId();detailVisible.value=false;formVisible.value=true;await nextTick();formBaseline=formSnapshot();formRef.value?.clearValidate()}
function validateDetailLines(){
  const groups=[['收入测算',form.value.revenueLines,[['revenueType','收入方式'],['itemName','收入项目'],['expectedAmount','预计金额'],['occurrenceType','发生方式']]],
    ['支出计划',form.value.expenseLines,[['expenseCategory','类别'],['itemName','支出项目'],['purpose','具体用途'],['amount','金额'],['occurrenceType','发生方式']]],
    ['量化目标',showTargetLines.value?form.value.targetLines:[],[['targetType','类型'],['targetName','目标名称'],['targetValue','目标值'],['unit','单位'],['acceptanceEvidence','验收依据']]]]
  for(const [name,rows,fields] of groups)for(const [index,row] of (rows||[]).entries())for(const [field,label] of fields){const value=row[field];if(value==null||String(value).trim()==='')return name+'第 '+(index+1)+' 行：请填写'+label}
  for(const [name,rows,field,rule] of [['收入测算',form.value.revenueLines,'expectedDate','revenue'],['支出计划',form.value.expenseLines,'occurDate','expense'],['量化目标',showTargetLines.value?form.value.targetLines:[],'dueDate','project']])for(const [index,row] of (rows||[]).entries())if(lineDateIssue(row[field],rule))return name+'第 '+(index+1)+' 行：'+lineDateIssue(row[field],rule)
  for(const [name,rows,field,precision] of [['收入测算',form.value.revenueLines,'expectedAmount',2],['支出计划',form.value.expenseLines,'amount',2],['量化目标',showTargetLines.value?form.value.targetLines:[],'targetValue',4]]){
    if(rows?.length>100)return name+'一次最多100行'
    for(const [index,row] of (rows||[]).entries()){
      const value=Number(row[field])
      if(!Number.isFinite(value)||value<0||value>99999999999999.99||Math.abs(value-Number(value.toFixed(precision)))>1e-8)return name+'第 '+(index+1)+' 行：请填写有效非负数值，最多 '+precision+' 位小数'
    }
  }
  if(!isNewTemplate.value&&form.value.goalMode!=='NO_TOTAL'&&!form.value.targetLines?.length)return '请至少填写一项量化目标'
  return ''
}
async function saveForm(launch=false,keepFormOpen=false){if(saving.value)return;if(launch&&!canLaunchForm.value)return ElMessage.warning('保存后请由子项目负责人补充成员和投入比例并启动项目');saving.value=true;try{if(launch)await refreshStaffAllocationPlans();if(launch||!isNewTemplate.value||isChildCreatorPhase.value){try{await formRef.value.validate()}catch{return;}const detailError=validateDetailLines();if(detailError)return showPlanError(detailError);if(!form.value.planStartDate)return ElMessage.warning('请选择计划开始日期');if(!openEnded.value&&!form.value.planEndDate)return ElMessage.warning('请选择计划结束日期或勾选不限期');if(form.value.planEndDate&&form.value.planStartDate>form.value.planEndDate)return ElMessage.warning('计划结束日期不能早于开始日期');if(form.value.managementMode==='KEY_CONTROL'&&!form.value.managementReason?.trim())return ElMessage.warning('重点监管项目请填写监管原因');const staffing=form.value.staffingLines||[];if(!isNewTemplate.value&&!staffing.length||staffing.some(row=>!row.userId))return ElMessage.warning('请在人员计划中选择具体人员');if(new Set(staffing.map(row=>String(row.userId))).size!==staffing.length)return ElMessage.warning('同一人员不能重复选择');if(isNewTemplate.value)for(const row of staffing){syncParticipation(row);if(!Number.isFinite(Number(row.inputQuantity))||Number(row.inputQuantity)<=0||Number(row.inputQuantity)>100)return ElMessage.warning('每个人的投入比例必须大于0且不超过100%');if(!['FOLLOW_PROJECT','CUSTOM','UNLIMITED'].includes(row.participationMode)||!row.planStartDate||row.participationMode==='CUSTOM'&&!row.planEndDate||row.planEndDate&&row.planStartDate>row.planEndDate||row.planStartDate<form.value.planStartDate||!openEnded.value&&form.value.planEndDate&&row.planEndDate>form.value.planEndDate||row.participationMode==='UNLIMITED'&&!openEnded.value)return ElMessage.warning('请填写有效的人员参与方式和项目范围内的日期');if(!row.calendarId)return ElMessage.warning('当前没有覆盖人员参与期间的有效工作日历，请联系管理员配置');if(row.allocationPlan?.allocations?.length&&staffAllocationTotal(row)!==100)return ElMessage.warning(`${row.userName||'人员'}的全部项目投入比例合计必须等于100%`);if(row.allocationPlan?.allocations?.length&&!row.allocationPlan.reason?.trim())return ElMessage.warning(`请填写${row.userName||'人员'}的跨项目投入调整原因`)}if(launch){
  if(requiredPlanSections.value.targets&&!form.value.targetLines?.length)return showPlanError('请填写至少一项可验收目标')
  if(budgetLoading.value||budgetError.value||budgetIssues.value.length)return showPlanError(budgetLoading.value?'预算正在计算，请稍后启动':budgetIssues.value.join('；')||'请先完成预算测算')
}}
const staffing=form.value.staffingLines||[];const payload={...form.value,...budgetPolicyPayload(),saveAsDraft:!launch&&isNewTemplate.value,targetLines:showTargetLines.value?form.value.targetLines:[],staffingLines:staffing.map(item=>({...item,inputUnit:'PERCENTAGE',inputQuantity:item.inputQuantity??100,unitPolicyId:item.unitPolicyId||defaultUnitPolicy()})),estimatedBonusCost:0,planEndDate:openEnded.value?null:form.value.planEndDate,budget:{...form.value.budget,businessAmount:businessBudgetAmount.value,cycle:openEnded.value?form.value.budget.cycle:'PROJECT',projectOpenEnded:openEnded.value}};let res;
if(payload.proposalId)res=await updateProjectProposal(payload);
else{
  // Reuse the create key on every retry, then apply current edits to the recovered draft.
  const retry=!!pendingCreate;pendingCreate=true;
  res=await addProjectProposal(payload);
  form.value.proposalId=res.data.proposalId;form.value.version=res.data.version;
  pendingCreate=null;
  if(retry)res=await updateProjectProposal({...payload,proposalId:res.data.proposalId,version:res.data.version});
}
form.value.proposalId=res.data.proposalId;form.value.version=res.data.version;formBaseline=formSnapshot();if(launch){try{const started=await submitProjectProposal(res.data.proposalId);ElMessage.success('项目已启动');formVisible.value=false;detailVisible.value=false;await refreshAll();if(started.data?.createdProjectId)openProject(started.data)}catch(error){await showPlanError(error?.message||'启动未完成，草稿已保存');await refreshAll()}}else if(keepFormOpen){ElMessage.success('投入比例及当前表单已保存到立项草稿');await refreshAll()}else{formVisible.value=false;ElMessage.success('草稿已保存');await refreshAll();await openDetail(res.data)}return true}catch(error){return false}finally{saving.value=false}}

async function openDetail(row){const res=await getProjectProposal(row.proposalId);detail.value=res.data||{};detailVisible.value=true}
const submitting=ref(false)
async function submitRow(row){await openForm(row)}
async function withdrawRow(row){const result=await ElMessageBox.prompt('可以填写撤回说明','撤回立项申请',{inputPlaceholder:'可选'});await withdrawProjectProposal(row.proposalId,{comment:result.value});ElMessage.success('申请已撤回');detailVisible.value=false;await refreshAll()}
function openProject(row){router.push({path:'/business/projects',query:{id:row.createdProjectId}})}
const bossOptionLabel=item=>item.nickName&&item.nickName!==item.userName?`${item.nickName}（${item.userName}）`:item.nickName||item.userName
const money=(value,currency='CNY')=>value===null||value===undefined?'—':`${Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})} ${currency}`
const planPeriod=item=>{
  if(!item?.planStartDate)return '待填写开始日期'
  let budget=item.budget
  if(!budget){try{budget=JSON.parse(item.templateSnapshotJson||'{}').budget}catch{}}
  return `${item.planStartDate} 至 ${item.planEndDate||(budget?.projectOpenEnded===false?'待填写结束日期':'不限期')}`
}
async function handleProjectPeriodChange(){if(!form.value.budget.anchorDate&&form.value.planStartDate)form.value.budget.anchorDate=shortDate(form.value.planStartDate);syncFollowingStaff();recomputeAllStaffCosts();await refreshStaffOptions();await refreshStaffAllocationPlans(true)}
async function handleOpenEndedChange(value){if(value){form.value.planEndDate=null;form.value.budget.cycle='MONTH';if(!form.value.budget.anchorDate)form.value.budget.anchorDate=shortDate(form.value.planStartDate)||null}else{form.value.budget.cycle='PROJECT';for(const row of form.value.staffingLines||[])if(row.participationMode==='UNLIMITED')row.participationMode='FOLLOW_PROJECT'}syncFollowingStaff();recomputeAllStaffCosts();await refreshStaffAllocationPlans(true);nextTick(()=>formRef.value?.clearValidate('planEndDate'))}
function handleBudgetCycleChange(){
  const anchor=shortDate(form.value.budget.anchorDate),start=shortDate(form.value.planStartDate)
  const source=anchor>start?anchor:start
  if(!source)return
  const year=source.slice(0,4),month=Number(source.slice(5,7))||1
  if(form.value.budget.cycle==='WEEK'){
    const date=new Date(Number(year),month-1,Number(source.slice(8,10))||1),offset=(date.getDay()+6)%7
    date.setDate(date.getDate()-offset)
    form.value.budget.anchorDate=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`
  }else form.value.budget.anchorDate=form.value.budget.cycle==='YEAR'?`${year}-01-01`:`${year}-${String(month).padStart(2,'0')}-01`
  nextTick(()=>formRef.value?.clearValidate('budget.anchorDate'))
}
function disablePlanEndDate(date){return !!form.value.planStartDate&&date.getTime()<new Date(`${form.value.planStartDate}T00:00:00`).getTime()}
let openingFromRoute=false
async function openRequestedForm(){
  if(route.path!=='/business/project-proposals'||!route.query.create||openingFromRoute)return
  openingFromRoute=true
  try{
    let parent=null
    if(route.query.parentProjectId){
      const id=Number(route.query.parentProjectId)
      if(!Number.isSafeInteger(id)||id<=0)return ElMessage.warning('主项目参数无效')
      parent=(await getBusinessProject(id)).data
      if(!parent||parent.parentId)return ElMessage.warning('仅支持在主项目下新增子项目')
      if(['CLOSED','CANCELED'].includes(parent.status))return ElMessage.warning('已结束的主项目不能新增子项目')
    }
    await openForm(undefined,parent)
  }finally{
    const {create,parentProjectId,...rest}=route.query
    await router.replace({path:route.path,query:rest})
    openingFromRoute=false
  }
}
watch(()=>[route.path,route.query.create,route.query.parentProjectId],openRequestedForm)
onMounted(async()=>{activeTab.value=route.query.tab==='directory'&&canViewDirectory.value?'directory':'mine';await ensureOptions();await refreshAll();if(route.query.id)await openDetail({proposalId:Number(route.query.id)});await openRequestedForm()})
useBusinessRefreshOnReactivated(refreshAll)
</script>

<style scoped>
.accounting-mode-option{height:auto;min-height:58px;padding-top:7px;padding-bottom:7px;line-height:22px}.accounting-mode-option span,.accounting-mode-option small{display:block}.accounting-mode-option small{font-size:12px;font-weight:400;color:#8793a1}

.optional-label{font-size:12px;font-weight:400;color:#657587}
.business-budget-inputs{display:flex;align-items:flex-start;gap:8px;width:100%;flex-wrap:wrap}.business-budget-inputs>.el-input-number{flex:1;min-width:120px}.business-budget-inputs>.budget-currency{flex:0 0 195px;margin-bottom:0}.budget-currency .el-select{width:145px}
.proposal-page{min-height:calc(100vh - 84px);padding:24px;background:#f4f6f8}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:16px}.eyebrow{font-size:11px;letter-spacing:.16em;color:#3977c5}.page-head h1{margin:4px 0;font-size:27px;color:#172033}.page-head p{margin:0;color:#778394}.proposal-tabs{margin-top:16px}.proposal-tabs :deep(.el-tabs__content){overflow:visible}.el-table b,.el-table small{display:block}.el-table small{margin-top:4px;color:#8994a2}.row-actions{display:flex;gap:8px}.row-actions .el-button+.el-button{margin-left:0}.budget-line{display:flex;align-items:center;gap:12px}.detail-grid{display:grid;grid-template-columns:repeat(2,1fr);border:1px solid #e1e6eb;border-radius:10px}.detail-grid div{padding:14px;border-right:1px solid #e1e6eb;border-bottom:1px solid #e1e6eb}.detail-grid div:nth-child(2n){border-right:0}.detail-grid div:nth-last-child(-n+2){border-bottom:0}.detail-grid span,.detail-grid b{display:block}.detail-grid span{color:#8793a1;font-size:12px}.detail-grid b{margin-top:6px}.detail-section{margin-top:18px;padding:16px;border:1px solid #e3e8ed;border-radius:10px;background:#fff}.detail-section h3{margin:0 0 10px;font-size:15px}.detail-section p{margin:0;line-height:1.7;white-space:pre-wrap}.review-comment{border-left:3px solid #d8892f;background:#fffaf2}.drawer-actions{position:sticky;bottom:0;display:flex;flex-wrap:wrap;gap:8px;margin-top:18px;padding:14px 0;background:#fff}.drawer-actions .el-button+.el-button{margin-left:0}.muted{color:#9099a5;font-size:12px}@media(max-width:760px){.proposal-page{padding:14px}.page-head{align-items:flex-start;flex-direction:column;gap:14px}.page-head>.el-button{width:100%}.detail-grid{grid-template-columns:1fr}.detail-grid div,.detail-grid div:nth-child(2n){border-right:0;border-bottom:1px solid #e1e6eb}.detail-grid div:last-child{border-bottom:0}.budget-line{align-items:flex-start;flex-direction:column}.el-dialog .el-col{max-width:100%;flex:0 0 100%}.plan-period-line{grid-template-columns:1fr}.plan-period-line>span{display:none}}
.required-hint{margin:0 0 18px;color:#8793a1;font-size:12px}.required-hint>span{color:var(--el-color-danger)}.required-section::before,.proposal-form :deep(.cell.required-column)::before,.proposal-form :deep(th.required-column .cell)::before{content:'*';color:var(--el-color-danger);margin-right:4px}.end-date-line{display:flex;align-items:center;gap:10px;width:100%}.end-date-line .el-date-editor{flex:1;min-width:0}
.help-label{cursor:help}.mode-option-hint{margin-left:12px;color:var(--el-text-color-secondary);font-size:12px}.inline-help{margin:8px 0;color:var(--el-text-color-secondary);font-size:12px;line-height:1.6}.inline-help summary{cursor:pointer;width:fit-content;color:var(--el-color-primary)}.inline-help p{margin:6px 0}.budget-period-summary{margin:6px 0;color:var(--el-text-color-regular);font-size:13px}.field-help{display:block;margin-top:5px;color:#8793a1;line-height:1.45}.plan-period-line{display:grid;grid-template-columns:minmax(130px,1fr) auto minmax(130px,1fr) auto;align-items:center;gap:10px;width:100%}.plan-period-line>span{color:#7f8a99}
.staff-period{display:flex;align-items:center;gap:8px}.staff-period :deep(.el-date-editor){width:138px}.staff-period>span{white-space:nowrap;color:#657487}
.allocation-link{margin-top:5px;padding:0;height:auto;font-size:12px}.allocation-popover{min-height:90px}.allocation-head{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:12px}.allocation-head b,.allocation-head small{display:block}.allocation-head small{margin-top:4px;color:#8793a1}.allocation-row{display:grid;grid-template-columns:minmax(260px,1fr) 130px 170px;align-items:center;gap:12px;padding:9px 0;border-bottom:1px solid var(--el-border-color-lighter)}.allocation-row>span:first-child b,.allocation-row>span:first-child small{display:block}.allocation-row>span:first-child small{margin-top:3px;color:#8793a1}.allocation-title{padding:6px 0;color:#8793a1;font-size:12px}.current-project{background:#f5f9ff}.period-only-project{background:var(--el-fill-color-lighter);color:var(--el-text-color-regular)}.period-allocation{display:flex;flex-direction:column;align-items:flex-end}.period-allocation small{margin-top:3px;color:#8793a1}.allocation-row .percentage-input{justify-content:flex-end}.allocation-row em{font-style:normal}.allocation-total{display:flex;align-items:center;justify-content:flex-end;gap:12px;padding:12px 0}.allocation-total small{color:inherit}.allocation-alert{margin-top:12px}
.allocation-reason{display:grid;grid-template-columns:12px minmax(0,1fr);align-items:start;gap:4px}.allocation-reason .required-mark{padding-top:8px;color:var(--el-color-danger);font-size:14px;line-height:1}
.phase-alert{margin-bottom:16px}.percentage-input{display:flex;align-items:center;gap:6px}.percentage-input .el-input-number{width:112px}.monthly-forecast{margin-top:12px;padding:16px;border:1px solid #dfe6ee;border-radius:10px;background:#fff}
.estimated-daily-cost{margin-top:6px!important;color:#60748d!important;font-weight:500}
.forecast-details{margin:0 0 18px;padding:0 16px;border:1px solid #dfe6ee;border-radius:8px}.forecast-details :deep(.el-collapse-item__header){font-weight:600}.forecast-details :deep(.el-collapse-item__wrap){border-bottom:0}
.forecast-status{height:64px;overflow-y:auto;display:flex;align-items:flex-start}
.budget-refresh-status{height:28px;display:flex;align-items:center;gap:12px;color:#7d8998;font-size:12px}
.optional-label{font-size:13px;font-weight:500;color:#657587}.plan-section{margin:12px 0 22px;padding:22px;border:1px solid #d5e0ec;border-radius:12px;background:#fff;box-shadow:0 5px 16px rgba(36,74,119,.06)}.plan-section-head{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:12px}.plan-section-head h3{margin:0;color:#26364d}.plan-section-head p{margin:5px 0 0;color:#7d8998;font-size:12px}.form-section>.plan-section-head{margin-bottom:18px;padding-bottom:14px;border-bottom:1px solid #e3eaf2}.form-section>.plan-section-head h3{display:flex;align-items:center;gap:10px;color:#1f3048;font-size:20px;line-height:32px}.form-section>.plan-section-head p{margin-top:6px;color:#718096;font-size:13px}.section-index{display:inline-flex;align-items:center;justify-content:center;min-width:38px;height:28px;padding:0 7px;border-radius:7px;background:#e9f3ff;color:#2874c6;font-size:14px;font-weight:700;letter-spacing:.04em}.form-section>.plan-section-head .required-section::before{margin-right:-6px}.basic-information-section>.el-form-item{margin-bottom:18px}.plan-section :deep(.el-input-number){width:100%}.finance-summary{display:grid;grid-template-columns:repeat(6,1fr);gap:10px;margin:4px 0 18px}.finance-summary>div{padding:13px;border-radius:8px;background:#edf4fb}.finance-summary span,.finance-summary b{display:block}.finance-summary span{font-size:12px;color:#6f7f91}.finance-summary b{margin-top:5px;color:#24364b}.finance-summary.compact{margin:0}.danger-text{color:#d7474f!important}.success-text{color:#16945e!important}
.budget-plan-section{padding:18px 20px;border-color:#d8e3ee;background:#fafbfd}.budget-section-grid{margin-top:2px}.budget-result-block{margin-top:6px;padding-top:16px;border-top:1px solid #dfe7ef}.budget-plan-section .finance-summary{margin-bottom:4px}
.parent-funding-panel{margin:0 0 18px;padding:16px;border:1px solid #bfd7f2;border-radius:10px;background:#f4f9ff}.parent-funding-head{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:12px}.parent-funding-head h3,.parent-funding-head p{margin:0}.parent-funding-head p{margin-top:4px;color:#718096;font-size:12px}.parent-funding-summary{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-bottom:16px}.parent-funding-summary>div{padding:12px;border-radius:8px;background:#fff}.parent-funding-summary span,.parent-funding-summary b{display:block}.parent-funding-summary span{color:#718096;font-size:12px}.parent-funding-summary b{margin-top:5px;color:#24364b}.funding-limit-note{margin:6px 0 0;color:#60748d;font-size:12px}
.budget-composition{padding:18px;background:linear-gradient(180deg,#fbfdff 0%,#fff 100%)}.budget-composition-head{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:14px}.budget-composition-head h3{margin-bottom:4px}.budget-composition-head p{color:#8793a1;font-size:12px}.budget-cycle-tag{padding:4px 10px;border:1px solid #cfe0f5;border-radius:999px;background:#f1f7ff;color:#3977c5;font-size:12px;white-space:nowrap}.budget-total-card{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:17px 18px;border:1px solid #bcd5f3;border-radius:10px;background:linear-gradient(135deg,#edf6ff 0%,#f7fbff 100%);box-shadow:0 5px 16px rgba(50,106,171,.08)}.budget-total-card span,.budget-total-card small,.budget-total-card strong{display:block}.budget-total-card span{color:#2f65a7;font-size:13px;font-weight:600}.budget-total-card small{margin-top:5px;color:#7a8da4}.budget-total-card strong{color:#1f4f88;font-size:22px;line-height:1.2;text-align:right}.budget-breakdown{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:10px}.budget-breakdown-card{position:relative;overflow:hidden;padding:14px;border:1px solid #e2e9f1;border-radius:9px;background:#fff}.budget-breakdown-card::before{position:absolute;top:0;left:0;width:100%;height:3px;background:#6d9fe0;content:''}.budget-breakdown-card.business::before{background:#56b6a2}.budget-breakdown-card.startup::before{background:#e8a955}.budget-breakdown-card span,.budget-breakdown-card b,.budget-breakdown-card small{display:block}.budget-breakdown-card span{color:#738296;font-size:12px}.budget-breakdown-card b{margin-top:7px;color:#24364b;font-size:15px}.budget-breakdown-card small{margin-top:5px;color:#9aa4b1;line-height:1.4}.budget-meta{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));margin-top:12px;padding:2px 14px;border:1px solid #e8edf2;border-radius:9px;background:#fafbfc}.budget-meta>div{padding:10px 0}.budget-meta>div:nth-child(even){padding-left:16px;border-left:1px solid #e8edf2}.budget-meta span,.budget-meta b{display:block}.budget-meta span{color:#8b96a4;font-size:12px}.budget-meta b{margin-top:4px;color:#435266;font-size:13px;line-height:1.55}.budget-meta .budget-reason{grid-column:1/-1;padding-left:0;border-top:1px solid #e8edf2;border-left:0}.budget-composition>.el-alert{margin-top:12px}
@media(max-width:760px){.plan-period-line{grid-template-columns:1fr}.plan-period-line>span{display:none}.budget-total-card{align-items:flex-start;flex-direction:column}.budget-total-card strong{text-align:left}.budget-breakdown,.budget-meta,.parent-funding-summary{grid-template-columns:1fr}.budget-meta>div:nth-child(even){padding-left:0;border-left:0;border-top:1px solid #e8edf2}.budget-meta .budget-reason{grid-column:auto}}
@media(max-width:900px){.finance-summary{grid-template-columns:repeat(2,1fr)}.plan-section{overflow-x:auto}.plan-section .el-table{min-width:850px}}
</style>
