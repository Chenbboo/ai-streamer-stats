<template>
  <div class="proposal-page">
    <header class="page-head">
      <div><span class="eyebrow">PROJECT APPLICATION</span><h1>立项申请</h1><p>明确目标、管理模式和交付标准，由负责人直接启动项目。</p></div>
      <el-button v-hasPermi="['business:project:proposal:add']" type="primary" icon="Plus" @click="openForm()">新建立项申请</el-button>
    </header>

    <el-alert title="负责人自主立项" description="所有立项申请均由申请人直接启动，无需老板审批。管理模式和结项方式可独立选择，归属老板负责后续治理、验收和结算。" type="success" :closable="false" show-icon />

    <el-tabs v-model="activeTab" class="proposal-tabs" @tab-change="loadActive">
      <el-tab-pane label="我的申请" name="mine">
        <el-card shadow="never">
          <el-table :data="mineRows" v-loading="loading" empty-text="还没有立项申请" @row-click="openDetail">
            <el-table-column label="申请" min-width="230"><template #default="{row}"><b>{{ row.projectName }}</b><small>{{ row.proposalNo }}</small></template></el-table-column>
            <el-table-column prop="sponsorOwnerName" label="归属老板" width="140" />
            <el-table-column prop="companyName" label="归属公司" min-width="150" />
            <el-table-column label="治理方式" min-width="170"><template #default="{row}"><b>{{ managementLabel[row.managementMode] || row.managementMode }}</b><small>{{ closeMethodLabel[row.closeMethod] || row.closeMethod }}</small></template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusTone[row.status] || 'info'">{{ statusLabel[row.status] || row.status }}</el-tag></template></el-table-column>
            <el-table-column label="计划周期" width="210"><template #default="{row}">{{ planPeriod(row) }}</template></el-table-column>
            <el-table-column label="操作" width="235" fixed="right"><template #default="{row}"><div class="row-actions" @click.stop>
              <el-button v-if="row.canEdit" link type="primary" @click="openForm(row)">编辑</el-button>
              <el-button v-if="row.canEdit" link type="success" @click="submitRow(row)">启动项目</el-button>
              <el-button v-if="row.status==='PENDING'" link type="warning" @click="withdrawRow(row)">撤回</el-button>
              <el-button v-if="row.status==='APPROVED'&&row.createdProjectId" link @click="openProject(row)">查看项目</el-button>
            </div></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane v-if="canViewDirectory" label="全部立项申请" name="directory">
        <el-card shadow="never"><el-table :data="directoryRows" v-loading="loading" empty-text="暂无立项申请"><el-table-column prop="projectName" label="项目名称" min-width="230"/><el-table-column prop="applicantName" label="申请人/负责人" width="150"/><el-table-column prop="sponsorOwnerName" label="归属老板" width="150"/><el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusTone[row.status] || 'info'">{{ statusLabel[row.status] || row.status }}</el-tag></template></el-table-column><el-table-column label="权限" width="120"><template #default="{row}"><span class="muted">仅目录可见</span></template></el-table-column></el-table></el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="formVisible" :title="form.proposalId ? '修改立项申请' : '新建立项申请'" width="min(1180px,97vw)" append-to-body>
      <el-form ref="formRef" class="proposal-form" :model="form" :rules="rules" label-width="112px" require-asterisk-position="left" scroll-to-error :scroll-into-view-options="{block:'center',behavior:'smooth'}">
        <p class="required-hint"><span>*</span> 为必填项；明细区可选，添加明细后请填写带星号的列。</p>
        <el-alert v-if="!isNewTemplate" title="历史申请保留原成本核算规则，由申请人直接启动。" type="warning" :closable="false" show-icon />
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="申请人/负责人" required><el-input :model-value="userStore.nickName || userStore.name" disabled /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="归属老板" prop="sponsorOwnerUserId"><el-select v-model="form.sponsorOwnerUserId" style="width:100%" placeholder="选择项目归属老板"><el-option v-for="item in options.bosses" :key="item.userId" :label="bossOptionLabel(item)" :value="item.userId" /></el-select><small class="field-help">用于归属、异常提醒、治理及后续结算责任，不参与立项审批。</small></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="项目名称" prop="projectName"><el-input v-model="form.projectName" maxlength="160" show-word-limit /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="归属公司" prop="companyDeptId"><el-select v-model="form.companyDeptId" style="width:100%" placeholder="请选择归属公司" @change="refreshStaffOptions"><el-option v-for="item in options.companies" :key="item.deptId" :label="item.deptName" :value="item.deptId" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="项目类型" prop="projectType" required><el-select v-model="form.projectType" style="width:100%"><el-option v-for="(label,value) in typeLabel" :key="value" :label="label" :value="value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="核算方式" prop="accountingMode" required><el-select v-model="form.accountingMode" style="width:100%"><el-option v-for="(item,value) in accountingOptions" :key="value" :label="`${item.label}（${item.description}）`" :value="value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="管理模式" prop="managementMode" required><el-select v-model="form.managementMode" style="width:100%"><el-option v-for="(item,value) in managementOptions" :key="value" :label="item.label" :value="value" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-alert class="governance-tip" :title="managementOptions[form.managementMode]?.title" :description="managementOptions[form.managementMode]?.description" type="info" :closable="false" show-icon /></el-col>
          <el-col :span="12"><el-form-item label="结项方式" prop="closeMethod" required><el-select v-model="form.closeMethod" style="width:100%"><el-option v-for="(item,value) in closeMethodOptions" :key="value" :label="item.label" :value="value" /></el-select><small class="field-help">{{ closeMethodOptions[form.closeMethod]?.description }}</small></el-form-item></el-col>
          <el-col v-if="form.managementMode==='KEY_CONTROL'" :span="12"><el-form-item label="监管原因" prop="managementReason" required><el-input v-model="form.managementReason" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="说明为何需要重点监管及主要监管事项" /></el-form-item></el-col>
          <el-col v-if="isNewTemplate || form.closeMethod!=='DIRECT'" :span="24"><el-form-item label="成果与验收" prop="acceptanceCriteria" :required="isNewTemplate || form.closeMethod!=='DIRECT'"><el-input v-model="form.acceptanceCriteria" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="列明验收指标、成果清单、通过条件和验收人" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="目标模式" prop="goalMode" required><el-radio-group v-model="form.goalMode"><el-radio value="TOTAL">按项目总目标跟踪</el-radio><el-radio value="NO_TOTAL">持续经营，按每日目标与成果跟踪</el-radio></el-radio-group></el-form-item></el-col><el-col :span="24"><el-form-item label="项目目标" prop="objective"><el-input v-model="form.objective" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="说明要解决的问题和可验收结果" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="立项理由" prop="applicationReason"><el-input v-model="form.applicationReason" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="说明现状/问题与机会是什么" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划开始" prop="planStartDate" required><el-date-picker v-model="form.planStartDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width:100%" @change="handleProjectPeriodChange" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划结束" prop="planEndDate" :required="!openEnded"><div class="end-date-line"><el-date-picker v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD" :disabled="openEnded" :disabled-date="disablePlanEndDate" :placeholder="openEnded ? '不限期' : '结束日期'" style="width:100%" @change="handleProjectPeriodChange" /><el-checkbox v-model="openEnded" @change="handleOpenEndedChange">不限期</el-checkbox></div></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="优先级" prop="priority" required><el-select v-model="form.priority" style="width:100%"><el-option label="低" value="LOW"/><el-option label="中" value="MEDIUM"/><el-option label="高" value="HIGH"/></el-select></el-form-item></el-col>
          <el-col :span="24"><BudgetControlFields v-model="form.budget" prop-prefix="budget" :startup-required="startupRequired" /></el-col>
          <el-col :span="6"><el-form-item :label="form.budget.mode==='TOTAL'?'总预算上限':'本期计划预算'"><el-input :model-value="budgetDisplay('totalAmount')" readonly /><small class="field-help">自动计算：人员预算＋业务预算</small></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="人员预算"><el-input :model-value="budgetDisplay('personnelAmount')" readonly /><small class="field-help">根据人员计划、日历和有效费率计算</small></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="业务预算" prop="budget.businessAmount" :required="form.budget.mode==='TOTAL'"><div class="business-budget-inputs"><el-input-number :model-value="form.budget.mode==='TOTAL'?form.budget.businessAmount:budgetEstimate.plannedBusinessAmount" @update:model-value="form.budget.businessAmount=$event" :disabled="form.budget.mode!=='TOTAL'" :min="0" :max="99999999999999.99" :precision="2" controls-position="right" /><el-form-item label="币种" prop="baseCurrency" label-width="50px" required class="budget-currency"><el-select v-model="form.baseCurrency" aria-label="预算币种"><el-option label="人民币（CNY）" value="CNY"/><el-option label="越南盾（VND）" value="VND"/><el-option label="美元（USD）" value="USD"/></el-select></el-form-item></div><small class="field-help">总额模式手填业务预算；每日或不设上限模式按收支计划估算。所有金额使用所选币种。</small></el-form-item></el-col>
          <el-col v-if="openEnded" :span="8"><el-form-item label="预算周期" prop="budget.cycle" required><el-select v-model="form.budget.cycle" style="width:100%" @change="handleBudgetCycleChange"><el-option label="周度" value="WEEK"/><el-option label="月度（默认）" value="MONTH"/><el-option label="季度" value="QUARTER"/></el-select></el-form-item></el-col>
          <el-col v-if="openEnded" :span="12"><el-form-item :label="budgetPeriodFieldLabel" prop="budget.anchorDate" required><BudgetPeriodPicker v-model:anchor-date="form.budget.anchorDate" :cycle="form.budget.cycle" /></el-form-item></el-col>
          <el-col :span="24"><el-alert :title="budgetPeriodText" description="固定期限按整个项目计算；不限期按本期周度、月度或季度计算，期满需在计划变更中续编。周度按周一至周日，首期从项目开始日计算；未填日期的收支计入本期。预算是计划估算，实际成本按成员参与期间的工作日自动核算。" type="info" :closable="false" show-icon /><el-alert v-if="budgetIssues.length" style="margin:10px 0" title="预算待完善（可保存草稿，处理后才能启动）" :description="budgetIssues.join('；')" type="warning" :closable="false" show-icon /></el-col>

          <el-col :span="24"><div class="budget-refresh-status" role="status" aria-live="polite"><span>{{ budgetError?'本次测算未完成，请检查输入后重试。':budgetLoading?'正在更新测算…':'修改金额、日期或人员安排后自动更新测算。' }}</span><el-button v-if="budgetError" link type="primary" @click="retryBudgetEstimate">重新测算</el-button></div></el-col>
          <el-col :span="24"><div class="plan-section"><div class="plan-section-head"><div><h3 :class="{'required-section':!isNewTemplate && ['PROFIT','HYBRID'].includes(form.accountingMode)}">收入测算</h3><p>{{ isNewTemplate ? '收入测算可选，用于记录预期和假设。' : '盈利型和混合型项目至少填写一项正常预测。' }}</p></div><el-button size="small" type="primary" plain @click="addRevenue">增加收入</el-button></div><el-table :data="form.revenueLines" size="small" empty-text="成本型或价值型项目可以不填直接收入"><el-table-column label="场景" label-class-name="required-column" width="120"><template #default="{row}"><el-select v-model="row.scenario"><el-option label="保守" value="CONSERVATIVE"/><el-option label="正常" value="BASE"/><el-option label="乐观" value="OPTIMISTIC"/></el-select></template></el-table-column><el-table-column label="收入方式" label-class-name="required-column" width="145"><template #default="{row}"><el-select v-model="row.revenueType"><el-option v-for="(label,value) in revenueTypeLabel" :key="value" :label="label" :value="value"/></el-select></template></el-table-column><el-table-column label="收入项目" label-class-name="required-column" min-width="150"><template #default="{row}"><el-input v-model="row.itemName" maxlength="160" placeholder="商品或服务"/></template></el-table-column><el-table-column label="预计金额" label-class-name="required-column" width="165"><template #default="{row}"><el-input-number v-model="row.expectedAmount" :min="0" :precision="2" controls-position="right"/></template></el-table-column><el-table-column label="发生方式" label-class-name="required-column" width="125"><template #default="{row}"><el-select v-model="row.occurrenceType" placeholder="一次性"><el-option label="一次性" value="ONE_TIME"/><el-option label="每日" value="DAILY"/><el-option label="每周" value="WEEKLY"/><el-option label="每月" value="MONTHLY"/></el-select></template></el-table-column><el-table-column label="预计回款 / 开始" width="145"><template #default="{row}"><el-date-picker v-model="row.expectedDate" value-format="YYYY-MM-DD" :disabled-date="disableStaffStartDate" style="width:100%"/><small v-if="lineDateIssue(row.expectedDate)" class="danger-text">{{ lineDateIssue(row.expectedDate) }}</small></template></el-table-column><el-table-column label="依据" min-width="170"><template #default="{row}"><el-input v-model="row.assumptionText" maxlength="1000" placeholder="单价、数量、转化率等"/></template></el-table-column><el-table-column width="55"><template #default="{$index}"><el-button link type="danger" @click="form.revenueLines.splice($index,1)">删</el-button></template></el-table-column></el-table></div></el-col>

          <el-col :span="24"><div class="plan-section"><div class="plan-section-head"><div><h3>支出计划</h3><p>填写已知的非人员支出；本期明细合计不能超过业务预算，剩余部分作为计划余量。</p></div><el-button size="small" type="primary" plain @click="addExpense">增加支出</el-button></div><el-table :data="form.expenseLines" size="small" empty-text="暂无外部支出"><el-table-column label="类别" label-class-name="required-column" width="145"><template #default="{row}"><el-select v-model="row.expenseCategory"><el-option v-for="(label,value) in expenseCategoryLabel" :key="value" :label="label" :value="value"/></el-select></template></el-table-column><el-table-column label="支出项目" label-class-name="required-column" min-width="150"><template #default="{row}"><el-input v-model="row.itemName" maxlength="160"/></template></el-table-column><el-table-column label="具体用途" label-class-name="required-column" min-width="190"><template #default="{row}"><el-input v-model="row.purpose" maxlength="500"/></template></el-table-column><el-table-column label="金额" label-class-name="required-column" width="165"><template #default="{row}"><el-input-number v-model="row.amount" :min="0" :precision="2" controls-position="right"/></template></el-table-column><el-table-column label="发生方式" label-class-name="required-column" width="125"><template #default="{row}"><el-select v-model="row.occurrenceType" placeholder="一次性"><el-option label="一次性" value="ONE_TIME"/><el-option label="每日" value="DAILY"/><el-option label="每周" value="WEEKLY"/><el-option label="每月" value="MONTHLY"/></el-select></template></el-table-column><el-table-column label="发生时间 / 开始" width="145"><template #default="{row}"><el-date-picker v-model="row.occurDate" value-format="YYYY-MM-DD" :disabled-date="disableStaffStartDate" style="width:100%"/><small v-if="lineDateIssue(row.occurDate)" class="danger-text">{{ lineDateIssue(row.occurDate) }}</small></template></el-table-column><el-table-column label="收款方" min-width="130"><template #default="{row}"><el-input v-model="row.counterparty" maxlength="160"/></template></el-table-column><el-table-column width="55"><template #default="{$index}"><el-button link type="danger" @click="form.expenseLines.splice($index,1)">删</el-button></template></el-table-column></el-table></div></el-col>

          <el-col :span="24"><div class="plan-section"><div class="plan-section-head"><div><h3 :class="{'required-section':!isNewTemplate}">人员计划</h3><p>{{ isNewTemplate ? '这里只确定人员及参与时间。跟随项目会自动继承项目日期；不限期从指定日期开始。人员预算按参与期间的完整工作日计算，人员成本按工作日自动累计。' : '历史申请保留原成本快照，保存时由后台按原规则核对；人员选择不展示其他人的内部费率。' }}</p></div><el-button size="small" type="primary" plain @click="addStaffing">增加人员</el-button></div>
            <el-table :data="form.staffingLines" size="small" empty-text="暂无人员计划，可在项目启动后安排">
              <el-table-column label="人员 / 本期费率状态" label-class-name="required-column" min-width="300"><template #default="{row}"><el-select v-model="row.userId" filterable style="width:100%" :disabled="!form.companyDeptId" :placeholder="form.companyDeptId ? '选择人员' : '请先选择归属公司'" @change="staffChanged(row)"><el-option v-for="item in options.staff" :key="item.userId" :label="staffOptionLabel(item)" :value="item.userId" :disabled="staffAlreadySelected(item.userId,row)" /></el-select><div v-if="isNewTemplate" style="margin-top:8px"><div class="staff-rate-status"><span :class="staffBudgetStatus(row)?.status==='PENDING'?'danger-text':''">{{ staffBudgetStatus(row)?.issues?.join('；') || (budgetLoading?'计算中…':staffBudgetStatus(row)?'本期费率已覆盖':'待计算') }}</span><small v-for="period in staffBudgetStatus(row)?.ratePeriods||[]" :key="period.version + ':' + period.effectiveFrom" class="field-help">v{{ period.version }}：{{ period.effectiveFrom }} 至 {{ period.effectiveTo || '不限期' }}</small></div></div></template></el-table-column>

              <el-table-column label="岗位/角色" min-width="125"><template #default="{row}">{{ row.roleName || '—' }}</template></el-table-column>
              <el-table-column label="开始日月成本" min-width="155"><template #default="{row}">{{ staffRateDisplay(row,'monthlyCost') }}</template></el-table-column>
              <el-table-column label="开始日日成本" min-width="155"><template #default="{row}">{{ staffRateDisplay(row,'dailyCost') }}</template></el-table-column>
              <template v-if="isNewTemplate">
                <el-table-column label="参与方式" label-class-name="required-column" min-width="145"><template #default="{row}"><el-select v-model="row.participationMode" @change="syncParticipation(row)"><el-option label="跟随项目" value="FOLLOW_PROJECT"/><el-option label="自定义时间" value="CUSTOM"/><el-option label="不限期" value="UNLIMITED" :disabled="!openEnded"/></el-select></template></el-table-column>
                <el-table-column label="参与时间" label-class-name="required-column" min-width="320"><template #default="{row}"><span v-if="row.participationMode==='FOLLOW_PROJECT'">{{ planPeriod(form) }}</span><div v-else class="staff-period"><el-date-picker v-model="row.planStartDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disableStaffStartDate" placeholder="开始日期" /><template v-if="row.participationMode==='CUSTOM'"><span>至</span><el-date-picker v-model="row.planEndDate" type="date" value-format="YYYY-MM-DD" :disabled-date="date=>disableStaffEndDate(date,row)" placeholder="结束日期" /></template><span v-else>至 不限期</span></div></template></el-table-column>
                <el-table-column label="工作日历" label-class-name="required-column" min-width="220"><template #default="{row}"><el-select v-model="row.calendarId" filterable><el-option v-for="item in calendarChoices(row)" :key="item.calendarId" :value="item.calendarId" :label="`${item.calendarName} · 日容量 ${Number(item.dailyMinutes)/60} 小时 · v${item.version}`" /></el-select></template></el-table-column>
              </template>
              <el-table-column v-else label="历史预计成本" min-width="175"><template #default="{row}">{{ row.estimatedCost==null?'由后台核对':money(row.estimatedCost,row.costCurrency||form.baseCurrency) }}</template></el-table-column>
              <el-table-column label="说明" min-width="150"><template #default="{row}"><el-input v-model="row.note" maxlength="500" placeholder="可选" /></template></el-table-column>
              <el-table-column width="55"><template #default="{$index}"><el-button link type="danger" @click="form.staffingLines.splice($index,1)">删</el-button></template></el-table-column>
            </el-table><p class="field-help">月成本、日成本仅为项目开始日的参考费率；本期预算按各工作日的有效费率计算，月中调价分段计入。请按真实薪酬资料补齐提示中的费率缺口。</p></div></el-col>

          <el-col v-if="form.goalMode!=='NO_TOTAL'" :span="24"><div class="plan-section"><div class="plan-section-head"><div><h3 :class="{'required-section':!isNewTemplate}">量化目标</h3><p>{{ isNewTemplate ? '量化指标可选；成果与验收标准仍需明确。' : '至少设置一项结果、财务、进度、质量、价值或数量目标。' }}</p></div><el-button size="small" type="primary" plain @click="addTarget">增加目标</el-button></div><el-table :data="form.targetLines" size="small"><el-table-column label="类型" label-class-name="required-column" width="125"><template #default="{row}"><el-select v-model="row.targetType"><el-option v-for="(label,value) in targetTypeLabel" :key="value" :label="label" :value="value"/></el-select></template></el-table-column><el-table-column label="目标名称" label-class-name="required-column" min-width="180"><template #default="{row}"><el-input v-model="row.targetName" maxlength="160"/></template></el-table-column><el-table-column label="目标值" label-class-name="required-column" width="145"><template #default="{row}"><el-input-number v-model="row.targetValue" :min="0" :max="99999999999999.99" :precision="4"/></template></el-table-column><el-table-column label="单位" label-class-name="required-column" width="140"><template #default="{row}"><el-select v-model="row.unit" filterable placeholder="选择单位" aria-label="目标单位" style="width:100%"><el-option v-if="row.unit && !targetUnitOptions.includes(row.unit)" :label="row.unit" :value="row.unit"/><el-option v-for="unit in targetUnitOptions" :key="unit" :label="unit" :value="unit"/></el-select></template></el-table-column><el-table-column label="完成日期" width="145"><template #default="{row}"><el-date-picker v-model="row.dueDate" value-format="YYYY-MM-DD" :disabled-date="disableStaffStartDate" style="width:100%"/><small v-if="lineDateIssue(row.dueDate)" class="danger-text">{{ lineDateIssue(row.dueDate) }}</small></template></el-table-column><el-table-column label="验收依据" label-class-name="required-column" min-width="210"><template #default="{row}"><el-input v-model="row.acceptanceEvidence" maxlength="500" placeholder="报表、合同、交付文件等"/></template></el-table-column><el-table-column width="55"><template #default="{$index}"><el-button link type="danger" @click="form.targetLines.splice($index,1)">删</el-button></template></el-table-column></el-table></div></el-col>

          <el-col v-if="isNewTemplate && form.staffingLines?.length" :span="24"><el-alert title="人员预算按参与期间的完整工作日估算；项目实际成本仍以执行阶段确认的工作量为准。" type="info" :closable="false" show-icon /></el-col>
          <el-col :span="24"><el-alert v-if="isNewTemplate" title="以下为同一预算期间内的计划测算，不代表实际成本或已实现利润。" type="info" :closable="false" show-icon /><div class="finance-summary"><div><span>预计收入</span><b>{{ estimateMoney(planSummary.revenue) }}</b></div><div><span>外部支出</span><b>{{ estimateMoney(planSummary.external) }}</b></div><div><span>人员成本</span><b>{{ estimateMoney(planSummary.personnel) }}</b></div><div><span>预计总成本</span><b>{{ estimateMoney(planSummary.total) }}</b></div><div><span>预计利润</span><b :class="planSummary.profit==null?'':planSummary.profit<0?'danger-text':'success-text'">{{ estimateMoney(planSummary.profit) }}</b></div><div><span>利润率</span><b>{{ budgetError?'测算未更新':budgetLoading?'计算中…':planSummary.margin===null?'待完善计划':`${planSummary.margin.toFixed(2)}%` }}</b></div></div></el-col>
          <el-col v-if="openEnded && budgetEstimate.firstMonth" :span="24"><el-collapse v-model="forecastPanels" class="forecast-details"><el-collapse-item name="next-month" title="查看下月测算"><p class="field-help">首月从项目开始日计算；下月预计按项目开始后的下一个完整自然月推算，排除一次性收支，仅供计划参考。每日按天数、每周按天数÷7、每月按天数÷30折算；人员按日历和当日费率计算。</p><el-table :data="[{label:'首月',...budgetEstimate.firstMonth},{label:'下月预计',...budgetEstimate.steadyMonth}]"><el-table-column prop="label" label="期间"/><el-table-column label="日期"><template #default="{row}">{{ row.startDate }} 至 {{ row.endDate }}</template></el-table-column><el-table-column label="预计收入"><template #default="{row}">{{ estimateMoney(row.revenueAmount) }}</template></el-table-column><el-table-column label="预计成本"><template #default="{row}">{{ estimateMoney(row.plannedTotalCost) }}</template></el-table-column><el-table-column label="预计利润"><template #default="{row}">{{ estimateMoney(row.profit) }}</template></el-table-column><el-table-column label="测算状态 / 待完善事项" min-width="280"><template #default="{row}"><div class="forecast-status"><span :class="row.status==='PENDING'?'danger-text':''">{{ budgetError?'测算未更新，请重试':budgetLoading?'正在更新…':row.issues?.join('；') || (row.status==='READY'?'已完成':'待计算') }}</span></div></template></el-table-column></el-table></el-collapse-item></el-collapse></el-col>
          <el-col :span="24"><el-form-item label="主要风险" prop="riskSummary" :required="!isNewTemplate"><el-input v-model="form.riskSummary" type="textarea" :rows="2" maxlength="2000" show-word-limit placeholder="列出最可能影响收入、成本和进度的风险"/></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="formVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveForm">保存立项申请</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="min(720px,94vw)" append-to-body>
      <template #header><div><span class="eyebrow">{{ detail.proposalNo }}</span><h2>{{ detail.projectName }}</h2></div></template>
      <div class="detail-grid"><div><span>项目负责人</span><b>{{ detail.applicantName }}</b></div><div><span>归属老板</span><b>{{ detail.sponsorOwnerName }}</b></div><div><span>管理模式</span><b>{{ managementLabel[detail.managementMode] || detail.managementMode }}</b></div><div><span>结项方式</span><b>{{ closeMethodLabel[detail.closeMethod] || detail.closeMethod }}</b></div><div><span>计划周期</span><b>{{ planPeriod(detail) }}</b></div><div><span>预算控制</span><b>{{ detail.budgetMode==='NONE'?'不设上限':detail.budgetMode==='DAILY'?money(detail.dailyBudgetLimit,detail.baseCurrency)+' / 日':money(detail.budgetLimit,detail.baseCurrency) }}</b></div></div>
      <section v-if="detail.budget" class="detail-section"><h3>预算构成</h3><p v-if="detail.budgetMode==='DAILY'">每日统计口径：{{ detail.budgetScope==='CASH_EXPENSE'?'仅外部支出':'全成本（外部支出、人员及奖金）' }}；启动预算 {{ money(detail.startupBudgetLimit,detail.baseCurrency) }}</p><p v-if="detail.budgetReason">预算说明：{{ detail.budgetReason }}</p><p>预算期间：{{ detail.budget.startDate }} 至 {{ detail.budget.endDate }} · {{ {PROJECT:'整个项目',WEEK:'周度',MONTH:'月度',QUARTER:'季度',YEAR:'年度（历史）'}[detail.budget.cycle] }}</p><p>人员预算 {{ money(detail.budget.personnelAmount,detail.baseCurrency) }} ＋ 业务预算 {{ money(detail.budget.businessAmount,detail.baseCurrency) }} ＝ 本期计划预算 {{ money(detail.budget.totalAmount,detail.baseCurrency) }}</p><el-alert v-if="detail.budget.status!=='READY'" :title="(detail.budget.issues||[]).join('；')" type="warning" :closable="false" /></section>
      <section class="detail-section"><h3>经营测算</h3><div class="finance-summary compact"><div><span>预计收入</span><b>{{ money(detail.estimatedRevenue,detail.baseCurrency) }}</b></div><div><span>外部支出</span><b>{{ money(detail.estimatedExternalCost,detail.baseCurrency) }}</b></div><div><span>人员成本</span><b>{{ money(detail.estimatedPersonnelCost,detail.baseCurrency) }}</b></div><div><span>预计总成本</span><b>{{ money(detail.estimatedTotalCost,detail.baseCurrency) }}</b></div><div><span>预计利润</span><b>{{ money(detail.expectedProfit,detail.baseCurrency) }}</b></div><div><span>计划人数</span><b>{{ detail.plannedHeadcount || 0 }} 人</b></div></div></section>
      <section v-if="detail.revenueLines?.length" class="detail-section"><h3>收入测算明细</h3><el-table :data="detail.revenueLines" size="small"><el-table-column label="场景"><template #default="{row}">{{ scenarioLabel[row.scenario] }}</template></el-table-column><el-table-column prop="itemName" label="收入项目"/><el-table-column label="预计金额"><template #default="{row}">{{ money(row.expectedAmount,detail.baseCurrency) }}</template></el-table-column><el-table-column prop="assumptionText" label="测算依据"/></el-table></section>
      <section v-if="detail.expenseLines?.length" class="detail-section"><h3>支出计划明细</h3><el-table :data="detail.expenseLines" size="small"><el-table-column prop="itemName" label="支出项目"/><el-table-column prop="purpose" label="具体用途"/><el-table-column label="金额"><template #default="{row}">{{ money(row.amount,detail.baseCurrency) }}</template></el-table-column><el-table-column prop="counterparty" label="收款方"/></el-table></section>
      <section v-if="detail.staffingLines?.length" class="detail-section"><h3>人员计划</h3><el-table :data="detail.staffingLines" size="small"><el-table-column label="人员"><template #default="{row}">{{ row.userName || '历史岗位汇总' }}</template></el-table-column><el-table-column prop="roleName" label="岗位"/><template v-if="newTemplate(detail)"><el-table-column label="参与方式"><template #default="{row}">{{ participationLabel[staffParticipationMode(row,detail)] }}</template></el-table-column><el-table-column label="参与时间"><template #default="{row}">{{ shortDate(row.planStartDate) }} 至 {{ row.planEndDate?shortDate(row.planEndDate):'不限期' }}</template></el-table-column></template><el-table-column v-else label="历史预计成本"><template #default="{row}">{{ money(row.estimatedCost,row.costCurrency||detail.baseCurrency) }}</template></el-table-column></el-table><p v-if="newTemplate(detail)">立项仅确定参与时间；实际工作量在项目执行时填报并计价。</p></section>
      <section v-if="detail.targetLines?.length" class="detail-section"><h3>量化目标</h3><el-table :data="detail.targetLines" size="small"><el-table-column prop="targetName" label="目标"/><el-table-column label="目标值"><template #default="{row}">{{ row.targetValue }} {{ row.unit }}</template></el-table-column><el-table-column prop="dueDate" label="完成日期"/><el-table-column prop="acceptanceEvidence" label="验收依据"/></el-table></section>
      <section class="detail-section"><h3>项目目标</h3><p>{{ detail.objective }}</p></section><section class="detail-section"><h3>立项理由</h3><p>{{ detail.applicationReason }}</p></section><section v-if="detail.managementReason" class="detail-section"><h3>重点监管原因</h3><p>{{ detail.managementReason }}</p></section><section v-if="detail.acceptanceCriteria" class="detail-section"><h3>验收标准</h3><p>{{ detail.acceptanceCriteria }}</p></section><section v-if="detail.reviewComment" class="detail-section review-comment"><h3>审批/撤回意见</h3><p>{{ detail.reviewComment }}</p></section>
      <section class="detail-section"><h3>操作记录</h3><el-timeline><el-timeline-item v-for="event in detail.events || []" :key="event.eventId" :timestamp="event.createTime"><b>{{ eventLabel[event.eventType] || event.eventType }}</b><p>{{ event.operatorName }}<span v-if="event.comment"> · {{ event.comment }}</span></p></el-timeline-item></el-timeline></section>
      <div class="drawer-actions"><el-button v-if="detail.canEdit" type="primary" @click="openForm(detail)">修改</el-button><el-button v-if="detail.canEdit" type="success" @click="submitRow(detail)">启动项目</el-button><el-button v-if="detail.status==='APPROVED'&&detail.createdProjectId" @click="openProject(detail)">查看正式项目</el-button></div>
    </el-drawer>
  </div>
</template>

<script setup name="BusinessProjectProposals">
import { ElMessage, ElMessageBox } from 'element-plus'
import useUserStore from '@/store/modules/user'
import { listProjectProposals, listProposalDirectory, getProjectProposal, getProjectProposalOptions, getProjectProposalStaffOptions, estimateProjectProposalBudget, addProjectProposal, updateProjectProposal, submitProjectProposal, withdrawProjectProposal } from '@/api/business/proposal'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
import BudgetPeriodPicker from '@/components/BudgetPeriodPicker/index.vue'
import BudgetControlFields from '@/components/BudgetControlFields/index.vue'

const route=useRoute(),router=useRouter(), userStore=useUserStore(), loading=ref(false), saving=ref(false), activeTab=ref('mine')
const mineRows=ref([]),directoryRows=ref([]),options=reactive({bosses:[],companies:[],staff:[],calendars:[],unitPolicies:[]})
const formVisible=ref(false),detailVisible=ref(false),formRef=ref(),detail=ref({}),openEnded=ref(false)
const form=ref({})
const forecastPanels=ref([])
const canViewDirectory=computed(()=>userStore.roles.includes('admin')||userStore.permissions.includes('*:*:*')||userStore.permissions.includes('business:project:proposal:review'))
const statusLabel={DRAFT:'草稿',PENDING:'待启动',RETURNED:'已退回',WITHDRAWN:'已撤回',APPROVED:'已启动'}
const statusTone={DRAFT:'info',PENDING:'warning',RETURNED:'danger',WITHDRAWN:'info',APPROVED:'success'}
const typeLabel={GENERAL:'通用',LIVE:'直播',JEWELRY:'珠宝',ECOMMERCE:'电商',OPERATIONS:'运营',INTERNAL:'内部',OTHER:'其他'}
const accountingOptions={PROFIT:{label:'盈利型',description:'看收入、成本和赚了多少'},COST:{label:'成本型',description:'看花了多少，有没有超预算'},VALUE:{label:'价值型',description:'看投入多少，目标有没有完成'},HYBRID:{label:'混合型',description:'既看赚了多少，也看目标完成情况'}}
const accountingLabel=Object.fromEntries(Object.entries(accountingOptions).map(([key,item])=>[key,item.label]))
const revenueTypeLabel={SALES:'商品销售',SERVICE:'服务费',COMMISSION:'佣金',ADVERTISING:'广告',SUBSCRIPTION:'订阅',LIVE:'直播收入',SAVING:'成本节约',OTHER:'其他'}
const expenseCategoryLabel={PROCUREMENT:'采购',MARKETING:'推广',PLATFORM:'平台服务',TRAVEL:'差旅',OUTSOURCING:'外包',EQUIPMENT:'设备',LOGISTICS:'物流',OTHER:'其他'}
const targetTypeLabel={FINANCIAL:'财务',QUANTITY:'数量',SCHEDULE:'进度',QUALITY:'质量',EFFICIENCY:'效率',GROWTH:'增长',CUSTOMER:'客户',COMPLIANCE:'合规',OTHER:'价值 / 其他'}
const targetUnitOptions=['个','件','条','次','人','单','份','套','台','场','天','小时','分钟','元','万元','%','分']
const scenarioLabel={CONSERVATIVE:'保守',BASE:'正常',OPTIMISTIC:'乐观'}
const managementOptions={LIGHT:{label:'轻量',title:'轻量管理：保留核心执行闭环',description:'适合周期短、范围清晰、风险较低的项目；保留任务、成本和KPI，风险按异常登记。'},STANDARD:{label:'标准',title:'标准管理：完整的常规项目过程',description:'启用周度跟踪、里程碑、风险台账和预算预警，适合多数跨成员项目。'},KEY_CONTROL:{label:'重点监管',title:'重点监管：高风险或公司级项目',description:'强化里程碑、风险、预算分级预警和治理变更审批，立项时必须说明监管原因。'}}
const managementLabel={LIGHT:'轻量',STANDARD:'标准',KEY_CONTROL:'重点监管',SIMPLE:'轻量',DELIVERY:'标准'}
const closeMethodOptions={DIRECT:{label:'直接结项',description:'负责人提交结项申请，由老板检验通过后关闭项目。'},RESULT_ACCEPTANCE:{label:'成果验收',description:'负责人提交交付资料，由老板验收通过后关闭项目。'},STAGED_ACCEPTANCE:{label:'阶段验收',description:'负责人逐里程碑提交成果，均由老板验收；全部通过后再申请结项。'}}
const closeMethodLabel=Object.fromEntries(Object.entries(closeMethodOptions).map(([key,item])=>[key,item.label]))
const eventLabel={SELF_AUTHORIZED:'负责人自主启动',CREATE:'创建草稿',EDIT:'修改草稿',SUBMIT:'提交审批',RESUBMIT:'重新提交',WITHDRAW:'撤回申请',RETURN:'退回修改',APPROVE:'批准立项',OWNER_LAUNCH:'负责人启动项目'}
const requiredRule=(message,trigger='change')=>[{required:true,message,trigger,...(trigger==='blur'?{type:'string',whitespace:true}:{})}]
const amountRule=(message,min=0)=>[{required:true,type:'number',min,max:99999999999999.99,message,trigger:'change'}]
const rules=computed(()=>({
  projectName:requiredRule('请输入项目名称','blur'),sponsorOwnerUserId:requiredRule('请选择归属老板'),companyDeptId:requiredRule('请选择归属公司'),
  goalMode:requiredRule('请选择目标模式'),
  projectType:requiredRule('请选择项目类型'),accountingMode:requiredRule('请选择核算方式'),managementMode:requiredRule('请选择管理模式'),closeMethod:requiredRule('请选择结项方式'),
  objective:requiredRule('请填写项目目标','blur'),applicationReason:requiredRule('请填写立项理由','blur'),planStartDate:requiredRule('请选择计划开始日期'),
  planEndDate:openEnded.value?[]:requiredRule('请选择计划结束日期或勾选不限期'),priority:requiredRule('请选择优先级'),baseCurrency:requiredRule('请选择币种'),
  managementReason:form.value.managementMode==='KEY_CONTROL'?requiredRule('请填写监管原因','blur'):[],
  acceptanceCriteria:isNewTemplate.value||form.value.closeMethod!=='DIRECT'?requiredRule('请填写成果清单和验收依据','blur'):[],
  'budget.mode':requiredRule('请选择预算控制方式'),
  'budget.scope':form.value.budget?.mode==='DAILY'?requiredRule('请选择每日统计口径'):[],
  'budget.dailyLimit':form.value.budget?.mode==='DAILY'?amountRule('请填写大于0的每日预算上限',0.01):[],
  'budget.startupLimit':startupRequired.value?amountRule('本期有一次性支出，请填写大于0的启动预算',0.01):[],
  'budget.reason':form.value.budget?.mode==='NONE'?requiredRule('请说明不设预算上限的原因','blur'):[],
  'budget.businessAmount':form.value.budget?.mode==='TOTAL'?amountRule('请填写有效业务预算，无支出填0'):[],'budget.cycle':openEnded.value?requiredRule('请选择预算周期'):[],'budget.anchorDate':openEnded.value?requiredRule(`请选择${budgetPeriodFieldLabel.value}`):[],riskSummary:!isNewTemplate.value?requiredRule('请填写主要风险','blur'):[]
}))

const emptyStaffing=()=>({participationMode:'FOLLOW_PROJECT',planStartDate:form.value.planStartDate||null,planEndDate:openEnded.value?null:form.value.planEndDate||null,inputUnit:'PERCENTAGE',inputQuantity:100,calendarId:defaultCalendar(),unitPolicyId:defaultUnitPolicy(),userId:null,userName:'',roleName:'',costPolicyId:null,costPolicyVersion:null,monthlyCostSnapshot:null,standardWorkDaysSnapshot:null,dailyCostSnapshot:null,costCurrency:null,estimatedCost:null,note:''})
const freshForm=()=>({projectName:'',templateVersion:'LIGHT_V1',sponsorOwnerUserId:null,companyDeptId:null,projectType:'GENERAL',accountingMode:'COST',managementMode:'LIGHT',closeMethod:'DIRECT',managementReason:'',acceptanceCriteria:'',objective:'',applicationReason:'',planStartDate:null,planEndDate:null,priority:'MEDIUM',baseCurrency:'CNY',budgetLimit:null,noBudget:'0',goalMode:'TOTAL',budget:{mode:'TOTAL',scope:'FULL_COST',dailyLimit:null,startupLimit:null,reason:'',cycle:'MONTH',anchorDate:null,businessAmount:0},revenueModel:'',peakCashNeed:null,riskSummary:'',revenueLines:[],expenseLines:[],staffingLines:[],targetLines:[]})
const isNewTemplate=computed(()=>!!form.value.templateVersion&&form.value.templateVersion!=='LEGACY_V1')
const newTemplate=item=>!!item?.templateVersion&&item.templateVersion!=='LEGACY_V1'
const defaultCalendar=()=>options.calendars.find(c=>coversWindow(c,form.value.planStartDate,form.value.planEndDate))?.calendarId??null
const defaultUnitPolicy=()=>options.unitPolicies.find(p=>coversWindow(p,form.value.planStartDate,form.value.planEndDate))?.unitPolicyId??null
const shortDate=value=>value?String(value).slice(0,10):''
const coversWindow=(policy,from,to)=>(!from||!policy.effectiveFrom||shortDate(policy.effectiveFrom)<=shortDate(from))&&(!to||!policy.effectiveTo||shortDate(policy.effectiveTo)>=shortDate(to))
const calendarChoices=row=>options.calendars.filter(c=>coversWindow(c,row.planStartDate,row.planEndDate))
const participationLabel={FOLLOW_PROJECT:'跟随项目',CUSTOM:'自定义时间',UNLIMITED:'不限期'}
const staffParticipationMode=(row,project)=>row.participationMode||(shortDate(row.planStartDate)===shortDate(project.planStartDate)&&(shortDate(row.planEndDate)||null)===(shortDate(project.planEndDate)||null)?'FOLLOW_PROJECT':!row.planEndDate?'UNLIMITED':'CUSTOM')

const number=value=>Number(value)||0
const budgetPolicyPayload=()=>({budgetMode:form.value.budget.mode,budgetScope:form.value.budget.scope,dailyBudgetLimit:form.value.budget.mode==='DAILY'?form.value.budget.dailyLimit:null,startupBudgetLimit:form.value.budget.mode==='NONE'?null:form.value.budget.startupLimit,budgetReason:form.value.budget.reason})
const budgetEstimate=ref({}),budgetLoading=ref(false),budgetError=ref('')
let budgetTimer,budgetRequest=0
const budgetRetry=ref(0)
const retryBudgetEstimate=()=>{budgetRetry.value++}
const budgetIssues=computed(()=>budgetError.value?[budgetError.value]:(budgetEstimate.value.issues||[]))
const budgetPeriodFieldLabel=computed(()=>({WEEK:'预算所属周',MONTH:'预算所属月份',QUARTER:'预算起始月份'}[form.value.budget?.cycle]||'预算所属期间'))
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
      date.setMonth(date.getMonth()+(budget.cycle==='QUARTER'?3:1),0)
    }
    to=localDate(date);if(start>from)from=start
  }
  return (form.value.expenseLines||[]).some(row=>
    (row.occurrenceType||row.expenseType||'ONE_TIME')==='ONE_TIME'&&Number(row.amount)>0&&
    (!row.occurDate||(!from||shortDate(row.occurDate)>=from)&&(!to||shortDate(row.occurDate)<=to)))
})
function localDate(date){return date.getFullYear()+'-'+String(date.getMonth()+1).padStart(2,'0')+'-'+String(date.getDate()).padStart(2,'0')}
const budgetPeriodText=computed(()=>{const b=budgetEstimate.value;return b.startDate&&b.endDate?`预算期间：${b.startDate} 至 ${b.endDate}（${{PROJECT:'整个项目',WEEK:'周度',MONTH:'月度',QUARTER:'季度'}[b.cycle]||''}）`:'请选择项目日期与预算期间'})
const budgetDisplay=key=>budgetError.value?'测算未更新':budgetLoading.value?'计算中…':budgetEstimate.value[key]==null?'待完善计划':money(budgetEstimate.value[key],form.value.baseCurrency)
// Watch only fields consumed by budget calculation and date validation.
// Editing descriptions must not trigger requests or disturb the form layout.
const budgetCalculationInput=computed(()=>[
  budgetRetry.value,formVisible.value,openEnded.value,form.value.companyDeptId,form.value.planStartDate,form.value.planEndDate,form.value.baseCurrency,
  ['mode','scope','dailyLimit','startupLimit','reason','cycle','anchorDate','businessAmount'].map(key=>form.value.budget?.[key]),
  (form.value.staffingLines||[]).map(row=>[row.userId,row.participationMode,row.planStartDate,row.planEndDate,row.calendarId]),
  (form.value.expenseLines||[]).map(row=>[row.amount,row.occurrenceType,row.expenseType,row.occurDate]),
  (form.value.revenueLines||[]).map(row=>[row.expectedAmount,row.occurrenceType,row.expectedDate,row.scenario]),
  form.value.goalMode,(form.value.goalMode==='NO_TOTAL'?[]:form.value.targetLines||[]).map(row=>row.dueDate)
])
watch(()=>JSON.stringify(budgetCalculationInput.value),()=>{
  clearTimeout(budgetTimer);const request=++budgetRequest;budgetError.value=''
  if(!formVisible.value){budgetLoading.value=false;return}
  if(!form.value.planStartDate||!openEnded.value&&!form.value.planEndDate){budgetLoading.value=false;budgetError.value='请先填写项目起止日期，或选择不限期';return}
  if(openEnded.value&&!form.value.budget.anchorDate){budgetLoading.value=false;budgetError.value='请选择预算所属期间';return}
  budgetLoading.value=true
  budgetTimer=setTimeout(async()=>{try{const res=await estimateProjectProposalBudget({...form.value,...budgetPolicyPayload(),budget:{...form.value.budget,cycle:openEnded.value?form.value.budget.cycle:'PROJECT'}});if(request===budgetRequest)budgetEstimate.value=res.data||{}}catch(e){if(request===budgetRequest)budgetError.value=e?.message||'预算计算未完成，请检查输入或稍后重试'}finally{if(request===budgetRequest)budgetLoading.value=false}},450)
})
onBeforeUnmount(()=>{clearTimeout(budgetTimer);budgetRequest++})
const planSummary=computed(()=>{const b=budgetEstimate.value,revenue=b.revenueAmount==null?null:number(b.revenueAmount),external=b.plannedBusinessAmount==null?null:number(b.plannedBusinessAmount),personnel=b.personnelAmount==null?null:number(b.personnelAmount),total=personnel==null||external==null?null:external+personnel,profit=total==null||revenue==null?null:revenue-total;return{revenue,external,personnel,total,profit,margin:profit===null||!revenue?null:profit*100/revenue}})
function addRevenue(){form.value.revenueLines.push({scenario:'BASE',revenueType:'SALES',itemName:'',expectedAmount:0,occurrenceType:'ONE_TIME',expectedDate:null,assumptionText:''})}
function addExpense(){form.value.expenseLines.push({expenseCategory:'OTHER',itemName:'',purpose:'',counterparty:'',amount:0,occurrenceType:'ONE_TIME',occurDate:null,expenseType:'ONE_TIME',hasQuotation:'0'})}
function addStaffing(){form.value.staffingLines.push(emptyStaffing())}
function addTarget(){form.value.targetLines.push({targetType:'QUANTITY',targetName:'',targetValue:0,unit:'',dueDate:null,acceptanceEvidence:''})}
async function ensureOptions(){if(options.bosses.length&&options.companies.length)return;const res=await getProjectProposalOptions();Object.assign(options,res.data||{})}
function plannedDays(){if(!form.value.planStartDate||openEnded.value||!form.value.planEndDate)return null;const start=new Date(`${form.value.planStartDate}T00:00:00`),end=new Date(`${form.value.planEndDate}T00:00:00`);return Math.max(1,Math.floor((end-start)/86400000)+1)}
function syncStaffRow(row,item){if(!item)return;Object.assign(row,{userId:item.userId,userName:item.nickName||item.accountName,roleName:item.positionName||'项目成员'});if(isNewTemplate.value)Object.assign(row,{costPolicyId:null,costPolicyVersion:null,monthlyCostSnapshot:null,standardWorkDaysSnapshot:null,dailyCostSnapshot:null,costCurrency:null,estimatedCost:null})}
function staffChanged(row){Object.assign(row,{costPolicyId:null,costPolicyVersion:null,monthlyCostSnapshot:null,standardWorkDaysSnapshot:null,dailyCostSnapshot:null,costCurrency:null,estimatedCost:null});syncStaffRow(row,options.staff.find(item=>Number(item.userId)===Number(row.userId)))}
const estimateMoney=value=>budgetError.value?'测算未更新':budgetLoading.value?'计算中…':value==null?'待完善计划':money(value,form.value.baseCurrency)
const staffBudgetStatus=row=>budgetEstimate.value.staffingStatus?.find(item=>String(item.userId)===String(row.userId))
function lineDateIssue(value){
  if(!value)return ''
  const date=shortDate(value)
  return date<form.value.planStartDate||!openEnded.value&&form.value.planEndDate&&date>form.value.planEndDate?'日期须在项目起止范围内':''
}
function staffRateDisplay(row,field){
  if(!row.userId)return '—'
  const staff=options.staff.find(item=>Number(item.userId)===Number(row.userId))
  if(!staff)return '—'
  if(!staff.rawCostVisible)return '无成本查看权限'
  if(staff.costMode!=='MONTHLY'||staff[field]==null)return '未配置'
  return money(staff[field],staff.costCurrency||'CNY')
}
function syncParticipation(row){
  if(row.participationMode==='FOLLOW_PROJECT')Object.assign(row,{planStartDate:form.value.planStartDate||null,planEndDate:openEnded.value?null:form.value.planEndDate||null})
  else if(row.participationMode==='UNLIMITED')Object.assign(row,{planStartDate:row.planStartDate||form.value.planStartDate||null,planEndDate:null})
  else Object.assign(row,{planStartDate:row.planStartDate||form.value.planStartDate||null,planEndDate:row.planEndDate||(!openEnded.value?form.value.planEndDate:null)})
  row.inputUnit='PERCENTAGE';row.inputQuantity=100;row.unitPolicyId=row.unitPolicyId||defaultUnitPolicy()
}
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
    else {row.userId=null;row.userName='';row.roleName='';row.estimatedCost=null;ElMessage.warning('部分人员不在所选公司或日期的有效任职范围，请重新选择；参与方式和日期已保留')}
  }
}
const staffOptionLabel=item=>{const name=item.nickName&&item.nickName!==item.accountName?`${item.nickName}（${item.accountName}）`:item.nickName||item.accountName;return `${name} · ${item.companyName||'未归属公司'} · ${item.positionName||'项目成员'}`}
const staffAlreadySelected=(userId,current)=>form.value.staffingLines?.some(row=>row!==current&&Number(row.userId)===Number(userId))
async function loadMine(){const res=await listProjectProposals({pageNum:1,pageSize:100});mineRows.value=res.rows||[]}
async function loadDirectory(){if(!canViewDirectory.value)return;const res=await listProposalDirectory({pageNum:1,pageSize:100});directoryRows.value=res.rows||[]}
async function loadActive(){loading.value=true;try{if(activeTab.value==='mine')await loadMine();else await loadDirectory()}finally{loading.value=false}}
async function refreshAll(){await loadMine();if(activeTab.value==='directory')await loadDirectory()}
async function openForm(row){forecastPanels.value=[];await ensureOptions();const source=row?.proposalId?(await getProjectProposal(row.proposalId)).data:freshForm();const legacyMode=source.managementMode==='SIMPLE'?'LIGHT':source.managementMode==='DELIVERY'?'STANDARD':source.managementMode;const legacyClose=source.closeMethod||(source.managementMode==='DELIVERY'?'RESULT_ACCEPTANCE':'DIRECT');openEnded.value=!!row?.proposalId&&!!source.planStartDate&&!source.planEndDate;form.value={...freshForm(),...source,templateVersion:source.templateVersion||(row?.proposalId?'LEGACY_V1':'LIGHT_V1'),managementMode:legacyMode,closeMethod:legacyClose,revenueLines:source.revenueLines||[],expenseLines:source.expenseLines||[],staffingLines:(source.staffingLines||[]).map(item=>{const from=shortDate(item.planStartDate)||null,to=shortDate(item.planEndDate)||null;const mode=item.participationMode||(from===shortDate(source.planStartDate)&&(to||null)===(shortDate(source.planEndDate)||null)?'FOLLOW_PROJECT':!to?'UNLIMITED':'CUSTOM');return{...item,participationMode:mode,planStartDate:from,planEndDate:to,inputUnit:'PERCENTAGE',inputQuantity:100,unitPolicyId:item.unitPolicyId||options.unitPolicies[0]?.unitPolicyId||null}}),targetLines:(source.targetLines||[]).map(item=>({...item,targetType:({RESULT:'QUANTITY',VALUE:'OTHER'})[item.targetType]||item.targetType}))};form.value.budget={mode:source.budgetMode||'TOTAL',scope:source.budgetScope||'FULL_COST',dailyLimit:source.dailyBudgetLimit,startupLimit:source.startupBudgetLimit,reason:source.budgetReason||'',cycle:openEnded.value?'MONTH':'PROJECT',anchorDate:source.planStartDate?shortDate(source.planStartDate).slice(0,7)+'-01':null,businessAmount:Number(source.estimatedExternalCost)||0,...source.budget};if(openEnded.value&&!['WEEK','MONTH','QUARTER'].includes(form.value.budget.cycle))form.value.budget.cycle='MONTH';budgetEstimate.value={};budgetError.value='';await refreshStaffOptions();formVisible.value=true;nextTick(()=>formRef.value?.clearValidate())}
function validateDetailLines(){
  const groups=[['收入测算',form.value.revenueLines,[['scenario','场景'],['revenueType','收入方式'],['itemName','收入项目'],['expectedAmount','预计金额'],['occurrenceType','发生方式']]],
    ['支出计划',form.value.expenseLines,[['expenseCategory','类别'],['itemName','支出项目'],['purpose','具体用途'],['amount','金额'],['occurrenceType','发生方式']]],
    ['量化目标',form.value.goalMode==='NO_TOTAL'?[]:form.value.targetLines,[['targetType','类型'],['targetName','目标名称'],['targetValue','目标值'],['unit','单位'],['acceptanceEvidence','验收依据']]]]
  for(const [name,rows,fields] of groups)for(const [index,row] of (rows||[]).entries())for(const [field,label] of fields){const value=row[field];if(value==null||String(value).trim()==='')return name+'第 '+(index+1)+' 行：请填写'+label}
  for(const [name,rows,field] of [['收入测算',form.value.revenueLines,'expectedDate'],['支出计划',form.value.expenseLines,'occurDate'],['量化目标',form.value.goalMode==='NO_TOTAL'?[]:form.value.targetLines,'dueDate']])for(const [index,row] of (rows||[]).entries())if(lineDateIssue(row[field]))return name+'第 '+(index+1)+' 行：'+lineDateIssue(row[field])
  for(const [name,rows,field,precision] of [['收入测算',form.value.revenueLines,'expectedAmount',2],['支出计划',form.value.expenseLines,'amount',2],['量化目标',form.value.goalMode==='NO_TOTAL'?[]:form.value.targetLines,'targetValue',4]]){
    if(rows?.length>100)return name+'一次最多100行'
    for(const [index,row] of (rows||[]).entries()){
      const value=Number(row[field])
      if(!Number.isFinite(value)||value<0||value>99999999999999.99||Math.abs(value-Number(value.toFixed(precision)))>1e-8)return name+'第 '+(index+1)+' 行：请填写有效非负数值，最多 '+precision+' 位小数'
    }
  }
  if(!isNewTemplate.value&&form.value.goalMode!=='NO_TOTAL'&&!form.value.targetLines?.length)return '请至少填写一项量化目标'
  return ''
}
async function saveForm(){if(saving.value)return;saving.value=true;try{try{await formRef.value.validate()}catch{return;}const detailError=validateDetailLines();if(detailError)return ElMessage.warning(detailError);if(!form.value.planStartDate)return ElMessage.warning('请选择计划开始日期');if(!openEnded.value&&!form.value.planEndDate)return ElMessage.warning('请选择计划结束日期或勾选不限期');if(form.value.planEndDate&&form.value.planStartDate>form.value.planEndDate)return ElMessage.warning('计划结束日期不能早于开始日期');if(form.value.managementMode==='KEY_CONTROL'&&!form.value.managementReason?.trim())return ElMessage.warning('重点监管项目请填写监管原因');if((isNewTemplate.value||form.value.closeMethod!=='DIRECT')&&!form.value.acceptanceCriteria?.trim())return ElMessage.warning('请填写成果清单和验收依据');const staffing=form.value.staffingLines||[];if(!isNewTemplate.value&&!staffing.length||staffing.some(row=>!row.userId))return ElMessage.warning('请在人员计划中选择具体人员');if(new Set(staffing.map(row=>String(row.userId))).size!==staffing.length)return ElMessage.warning('同一人员不能重复选择');if(isNewTemplate.value)for(const row of staffing){syncParticipation(row);if(!['FOLLOW_PROJECT','CUSTOM','UNLIMITED'].includes(row.participationMode)||!row.planStartDate||row.participationMode==='CUSTOM'&&!row.planEndDate||row.planEndDate&&row.planStartDate>row.planEndDate||row.planStartDate<form.value.planStartDate||!openEnded.value&&form.value.planEndDate&&row.planEndDate>form.value.planEndDate||row.participationMode==='UNLIMITED'&&!openEnded.value)return ElMessage.warning('请填写有效的人员参与方式和项目范围内的日期');if(!row.calendarId)return ElMessage.warning('请选择人员适用的工作日历')}const payload={...form.value,...budgetPolicyPayload(),targetLines:form.value.goalMode==='NO_TOTAL'?[]:form.value.targetLines,staffingLines:staffing.map(item=>({...item,inputUnit:'PERCENTAGE',inputQuantity:100,unitPolicyId:item.unitPolicyId||defaultUnitPolicy()})),estimatedBonusCost:0,planEndDate:openEnded.value?null:form.value.planEndDate,budget:{...form.value.budget,cycle:openEnded.value?form.value.budget.cycle:'PROJECT'}};const res=payload.proposalId?await updateProjectProposal(payload):await addProjectProposal(payload);formVisible.value=false;ElMessage.success(res.data?.budget?.status==='PENDING'?'草稿已保存，请完善预算提示后启动':'立项申请已保存，预算已由后台重新计算');await refreshAll();if(res.data?.proposalId)await openDetail(res.data)}finally{saving.value=false}}

async function openDetail(row){const res=await getProjectProposal(row.proposalId);detail.value=res.data||{};detailVisible.value=true}
const submitting=ref(false)
async function submitRow(row){if(submitting.value)return;submitting.value=true;try{await ElMessageBox.confirm(`确认启动“${row.projectName}”吗？系统将校验资料，保存所选管理模式和结项方式，并直接创建执行中的项目。`,'负责人确认启动',{type:'warning',confirmButtonText:'确认并启动'});const res=await submitProjectProposal(row.proposalId);ElMessage.success('项目已启动');detailVisible.value=false;await refreshAll();if(res.data?.createdProjectId)openProject(res.data)}finally{submitting.value=false}}
async function withdrawRow(row){const result=await ElMessageBox.prompt('可以填写撤回说明','撤回立项申请',{inputPlaceholder:'可选'});await withdrawProjectProposal(row.proposalId,{comment:result.value});ElMessage.success('申请已撤回');detailVisible.value=false;await refreshAll()}
function openProject(row){router.push({path:'/business/projects',query:{id:row.createdProjectId}})}
const bossOptionLabel=item=>item.nickName&&item.nickName!==item.userName?`${item.nickName}（${item.userName}）`:item.nickName||item.userName
const money=(value,currency='CNY')=>value===null||value===undefined?'—':`${Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})} ${currency}`
const planPeriod=item=>item?.planStartDate?`${item.planStartDate} 至 ${item.planEndDate||'不限期'}`:'—'
async function handleProjectPeriodChange(){if(!form.value.budget.anchorDate&&form.value.planStartDate)form.value.budget.anchorDate=shortDate(form.value.planStartDate);syncFollowingStaff();recomputeAllStaffCosts();await refreshStaffOptions()}
function handleOpenEndedChange(value){if(value){form.value.planEndDate=null;form.value.budget.cycle='MONTH';if(!form.value.budget.anchorDate)form.value.budget.anchorDate=shortDate(form.value.planStartDate)||null}else{form.value.budget.cycle='PROJECT';for(const row of form.value.staffingLines||[])if(row.participationMode==='UNLIMITED')row.participationMode='FOLLOW_PROJECT'}syncFollowingStaff();recomputeAllStaffCosts();nextTick(()=>formRef.value?.clearValidate('planEndDate'))}
function handleBudgetCycleChange(){
  const anchor=shortDate(form.value.budget.anchorDate),start=shortDate(form.value.planStartDate)
  const source=anchor>start?anchor:start
  if(!source)return
  const year=source.slice(0,4),month=Number(source.slice(5,7))||1
  if(form.value.budget.cycle==='WEEK'){
    const date=new Date(Number(year),month-1,Number(source.slice(8,10))||1),offset=(date.getDay()+6)%7
    date.setDate(date.getDate()-offset)
    form.value.budget.anchorDate=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`
  }else{
    const normalizedMonth=month
    form.value.budget.anchorDate=`${year}-${String(normalizedMonth).padStart(2,'0')}-01`
  }
  nextTick(()=>formRef.value?.clearValidate('budget.anchorDate'))
}
function disablePlanEndDate(date){return !!form.value.planStartDate&&date.getTime()<new Date(`${form.value.planStartDate}T00:00:00`).getTime()}
onMounted(async()=>{activeTab.value=route.query.tab==='directory'&&canViewDirectory.value?'directory':'mine';await ensureOptions();await refreshAll();if(route.query.id)await openDetail({proposalId:Number(route.query.id)})})
useBusinessRefreshOnReactivated(refreshAll)
</script>

<style scoped>
.business-budget-inputs{display:flex;align-items:flex-start;gap:8px;width:100%;flex-wrap:wrap}.business-budget-inputs>.el-input-number{flex:1;min-width:120px}.business-budget-inputs>.budget-currency{flex:0 0 195px;margin-bottom:0}.budget-currency .el-select{width:145px}
.proposal-page{min-height:calc(100vh - 84px);padding:24px;background:#f4f6f8}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:16px}.eyebrow{font-size:11px;letter-spacing:.16em;color:#3977c5}.page-head h1{margin:4px 0;font-size:27px;color:#172033}.page-head p{margin:0;color:#778394}.proposal-tabs{margin-top:16px}.proposal-tabs :deep(.el-tabs__content){overflow:visible}.el-table b,.el-table small{display:block}.el-table small{margin-top:4px;color:#8994a2}.row-actions{display:flex;gap:8px}.row-actions .el-button+.el-button{margin-left:0}.budget-line{display:flex;align-items:center;gap:12px}.detail-grid{display:grid;grid-template-columns:repeat(2,1fr);border:1px solid #e1e6eb;border-radius:10px}.detail-grid div{padding:14px;border-right:1px solid #e1e6eb;border-bottom:1px solid #e1e6eb}.detail-grid div:nth-child(2n){border-right:0}.detail-grid div:nth-last-child(-n+2){border-bottom:0}.detail-grid span,.detail-grid b{display:block}.detail-grid span{color:#8793a1;font-size:12px}.detail-grid b{margin-top:6px}.detail-section{margin-top:18px;padding:16px;border:1px solid #e3e8ed;border-radius:10px;background:#fff}.detail-section h3{margin:0 0 10px;font-size:15px}.detail-section p{margin:0;line-height:1.7;white-space:pre-wrap}.review-comment{border-left:3px solid #d8892f;background:#fffaf2}.drawer-actions{position:sticky;bottom:0;display:flex;flex-wrap:wrap;gap:8px;margin-top:18px;padding:14px 0;background:#fff}.drawer-actions .el-button+.el-button{margin-left:0}.muted{color:#9099a5;font-size:12px}@media(max-width:760px){.proposal-page{padding:14px}.page-head{align-items:flex-start;flex-direction:column;gap:14px}.page-head>.el-button{width:100%}.detail-grid{grid-template-columns:1fr}.detail-grid div,.detail-grid div:nth-child(2n){border-right:0;border-bottom:1px solid #e1e6eb}.detail-grid div:last-child{border-bottom:0}.budget-line{align-items:flex-start;flex-direction:column}.el-dialog .el-col{max-width:100%;flex:0 0 100%}.plan-period-line{grid-template-columns:1fr}.plan-period-line>span{display:none}}
.required-hint{margin:0 0 18px;color:#8793a1;font-size:12px}.required-hint>span{color:var(--el-color-danger)}.required-section::before,.proposal-form :deep(.cell.required-column)::before,.proposal-form :deep(th.required-column .cell)::before{content:'*';color:var(--el-color-danger);margin-right:4px}.end-date-line{display:flex;align-items:center;gap:10px;width:100%}.end-date-line .el-date-editor{flex:1;min-width:0}
.governance-tip{margin:-4px 0 16px}.field-help{display:block;margin-top:5px;color:#8793a1;line-height:1.45}.plan-period-line{display:grid;grid-template-columns:minmax(130px,1fr) auto minmax(130px,1fr) auto;align-items:center;gap:10px;width:100%}.plan-period-line>span{color:#7f8a99}
.staff-period{display:flex;align-items:center;gap:8px}.staff-period :deep(.el-date-editor){width:138px}.staff-period>span{white-space:nowrap;color:#657487}
.forecast-details{margin:0 0 18px;padding:0 16px;border:1px solid #dfe6ee;border-radius:8px}.forecast-details :deep(.el-collapse-item__header){font-weight:600}.forecast-details :deep(.el-collapse-item__wrap){border-bottom:0}
.forecast-status{height:64px;overflow-y:auto;display:flex;align-items:flex-start}
.budget-refresh-status{height:28px;display:flex;align-items:center;gap:12px;color:#7d8998;font-size:12px}
.plan-section{margin:10px 0 18px;padding:16px;border:1px solid #dfe6ee;border-radius:10px;background:#fafbfd}.plan-section-head{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:12px}.plan-section-head h3{margin:0;color:#26364d}.plan-section-head p{margin:5px 0 0;color:#7d8998;font-size:12px}.plan-section :deep(.el-input-number){width:100%}.finance-summary{display:grid;grid-template-columns:repeat(6,1fr);gap:10px;margin:4px 0 18px}.finance-summary>div{padding:13px;border-radius:8px;background:#edf4fb}.finance-summary span,.finance-summary b{display:block}.finance-summary span{font-size:12px;color:#6f7f91}.finance-summary b{margin-top:5px;color:#24364b}.finance-summary.compact{margin:0}.danger-text{color:#d7474f!important}.success-text{color:#16945e!important}
@media(max-width:760px){.plan-period-line{grid-template-columns:1fr}.plan-period-line>span{display:none}}
@media(max-width:900px){.finance-summary{grid-template-columns:repeat(2,1fr)}.plan-section{overflow-x:auto}.plan-section .el-table{min-width:850px}}
</style>
