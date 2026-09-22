<template>
  <div class="app-container business-page">
    <header class="hero">
      <div>
        <span class="eyebrow">OWNER COMMAND CENTER</span>
        <h1>{{ $tr("老板工作台") }}</h1>
        <p>{{ $tr("负责人处理日常经营；这里只呈现经营异常、跨项目风险和需要老板干预的事项。") }}</p>
      </div>
      <div class="hero-actions">
        <CompanyAccessSettings />
        <ProfitTaxSettings @changed="handleTaxSettingsChanged" />
        <el-button type="success" icon="Plus" @click="openAccountingEntry('revenue')">{{ t('bossReview.addRevenue') }}</el-button>
        <el-button type="primary" icon="Plus" @click="openAccountingEntry('spend')">{{ t('bossReview.addCost') }}</el-button>
        <el-button v-hasPermi="['business:attendance:read']" icon="Calendar" @click="router.push('/hcm/attendance')">{{ $tr("员工考勤") }}</el-button>
      </div>
    </header>

    <section class="panel pending-panel">
      <div class="section-title">
        <h2>{{ $tr("需关注与历史待处理") }}</h2>
        <span>· {{ totalPendingCount ? $tr("{0} 项", [totalPendingCount]) : $tr("当前没有阻塞事项") }}</span>
      </div>
      <div v-if="!groupedPendingRows.length && !loading" class="empty-state success-empty">
        <span>✓</span>{{ $tr("负责人流程运行正常，当前无需老板介入 ") }}</div>

      <article
        v-for="row in visiblePendingRows"
        :key="row.itemKey"
        :class="['decision-row', { 'personnel-row': row.category === 'PERSONNEL_COST_GROUP' }]"
      >
        <span :class="['decision-dot', pendingDotClass(row)]"></span>

        <div v-if="row.category === 'PERSONNEL_COST_GROUP'" class="decision-copy">
          <div class="decision-title">
            <b>{{ $tr("人员成本未设置") }}</b>
            <span class="decision-count">{{ $tr("{0} 人", [personnelRows.length]) }}</span>
          </div>
          <p>{{ $tr("{0}· 尚未设置今日生效的月度用人成本", [personnelPreview]) }}</p>

          <div v-if="personnelExpanded" class="personnel-list">
            <div v-for="person in personnelRows" :key="person.itemKey" class="personnel-item">
              <b>{{ person.userName }}</b>
              <span>{{ personnelMeta(person) }}</span>
              <el-button size="small" :type="person.costStatus === 'MISSING_REGION' ? 'warning' : ''" plain @click="openSingleCost(person)">
                {{ person.costStatus === 'MISSING_REGION' ? $tr("设置国家") : $tr("设置") }}
              </el-button>
            </div>
          </div>
        </div>

        <div v-else class="decision-copy">
          <div class="decision-title">
            <b>{{ row.category === 'ACCOUNTING' ? row.projectName : (row.userName || row.projectName) }}</b>
            <span :class="['decision-count', pendingBadgeClass(row)]">{{ pendingLabel(row) }}</span>
          </div>
          <p>{{ pendingMeta(row) }}· {{ pendingDescription(row) }}</p>
        </div>

        <div class="decision-actions">
          <template v-if="row.category === 'PERSONNEL_COST_GROUP'">
            <el-button type="primary" size="small" :disabled="!batchEligibleRows.length" @click="openBatchCost">{{ $tr("批量设置") }}</el-button>
            <el-button size="small" @click="personnelExpanded = !personnelExpanded">{{ personnelExpanded ? $tr("收起") : $tr("查看人员") }}</el-button>
          </template>
          <template v-else-if="row.category === 'PROPOSAL'">
            <el-button size="small" type="success" @click="decideProposal(row, 'APPROVED')">{{ $tr("批准并启动") }}</el-button>
            <el-button size="small" type="warning" plain @click="decideProposal(row, 'RETURNED')">{{ $tr("退回修改") }}</el-button>
            <el-button size="small" @click="openProposal(row)">{{ $tr("详情") }}</el-button>
          </template>
          <template v-else-if="row.category === 'KPI_MISSING'">
            <el-button size="small" type="primary" @click="openKpi(row)">{{ Number(row.targetCount) ? $tr("发布KPI方案") : $tr("设置KPI") }}</el-button>
            <el-button size="small" @click="openProject(row)">{{ $tr("项目详情") }}</el-button>
          </template>
          <template v-else-if="row.category === 'MANAGEMENT_FEE'">
            <el-button size="small" type="primary" @click="openProject(row, 'settlement')">{{ $tr("设置管理费") }}</el-button>
            <el-button size="small" @click="openProject(row)">{{ $tr("项目详情") }}</el-button>
          </template>
          <template v-else-if="row.category === 'BONUS_PAYMENT'">
            <el-button v-hasPermi="['business:incentive:pay']" size="small" type="primary" @click="openBonusPayment(row)">{{ $tr("登记发放") }}</el-button>
          </template>
          <template v-else-if="row.category === 'INCENTIVE_REVIEW'">
            <el-button v-hasPermi="['business:incentive:approve']" size="small" type="primary" @click="openIncentive(row)">{{ $tr("去审核") }}</el-button>
          </template>
          <template v-else-if="row.category === 'KPI_REVIEW'">
            <el-button size="small" type="primary" @click="openKpi(row)">{{ $tr("审核结算") }}</el-button>
            <el-button size="small" @click="openProject(row)">{{ $tr("项目详情") }}</el-button>
          </template>
          <template v-else-if="row.category === 'STAGE_ACCEPTANCE'">
            <el-button v-if="row.attachmentUrls" size="small" type="primary" plain @click="openStageEvidence(row)">{{ $tr("验收文件（{0}）", [evidenceCount(row.attachmentUrls)]) }}</el-button>
            <el-button size="small" type="success" @click="openProject(row, 'stageAcceptance')">{{ $tr("立即验收") }}</el-button>
          </template>
          <template v-else-if="row.category === 'ACCOUNTING'">
            <el-button size="small" type="success" @click="confirmPendingAccounting(row)">{{ $tr("确认入账") }}</el-button>
            <el-button size="small" type="warning" plain @click="returnPendingAccounting(row)">{{ $tr("退回修改") }}</el-button>
            <el-button size="small" @click="openPendingAccounting(row)">{{ $tr("查看明细") }}</el-button>
          </template>
          <template v-else>
            <el-button
              v-for="action in decisionActions(row)"
              :key="action.key"
              size="small"
              :type="action.type"
              :plain="action.plain"
              @click="doTransition(row, action.key)"
            >{{ action.label }}</el-button>
            <el-button size="small" @click="openProject(row)">{{ $tr("详情") }}</el-button>
          </template>
        </div>
      </article>
      <div v-if="groupedPendingRows.length > 2" class="pending-toggle">
        <el-button link type="primary" @click="pendingExpanded = !pendingExpanded">
          {{ pendingExpanded ? $tr("收起待办") : $tr("展开全部 {0} 条待办", [groupedPendingRows.length]) }}
          <span :class="['pending-toggle-arrow', { 'is-expanded': pendingExpanded }]">⌄</span>
        </el-button>
      </div>
    </section>

    <section class="panel accounting-overview" :aria-busy="accountingLoading">
      <div class="section-title section-title--between">
        <div><h2>{{ t('bossReview.title') }}</h2><span v-if="accounting.bizDate">{{ accounting.bizDate }} · {{ t('bossReview.timezone') }}</span></div>
        <div class="panel-actions review-date-controls">
          <el-select v-model="reportPeriod" :aria-label="t('bossReview.period')" @change="changeReportPeriod">
            <el-option :label="t('bossReview.yesterday')" value="yesterday" />
            <el-option :label="t('bossReview.today')" value="today" />
            <el-option :label="t('bossReview.custom')" value="custom" />
          </el-select>
          <el-date-picker v-if="reportPeriod === 'custom'" v-model="customReportDate" type="date" value-format="YYYY-MM-DD" :clearable="false" :disabled-date="futureReportDate" :aria-label="t('bossReview.period')" @change="loadAccounting" />
          <el-button link type="primary" :disabled="accountingLoading || accountingError || !accounting.bizDate" @click="openReviewAccounting()">{{ t('bossReview.details') }}</el-button>
        </div>
      </div>
      <div v-if="accountingLoading" class="empty-state" role="status">{{ t('bossReview.loading') }}</div>
      <div v-else-if="accountingError" class="empty-state" role="alert">
        {{ t('bossReview.failed') }} <el-button link type="primary" @click="loadAccounting">{{ t('bossReview.retry') }}</el-button>
      </div>
      <template v-else>
        <div class="review-readiness">
          <el-tag :type="accounting.dataStatus === 'INCOMPLETE' ? 'warning' : 'info'">{{ t(`bossReview.status${accounting.dataStatus || 'NO_DATA'}`) }}</el-tag>
          <span>{{ t('bossReview.coverage', { count: accounting.readiness?.resultCount || 0 }) }}</span>
          <el-tag v-if="accounting.missingDailyResultCount" type="warning">{{ t('bossReview.missing', { count: accounting.missingDailyResultCount }) }}</el-tag>
          <el-tag v-if="accounting.readiness?.pendingCostCount" type="warning">{{ t('bossReview.pendingCosts', { count: accounting.readiness.pendingCostCount }) }}</el-tag>
          <el-tag v-if="accounting.readiness?.unfinishedFactCount" type="warning">{{ t('bossReview.pendingFacts', { count: accounting.readiness.unfinishedFactCount }) }}</el-tag>
          <el-tag v-if="accounting.readiness?.unfinishedWorkCount" type="warning">{{ t('bossReview.pendingWork', { count: accounting.readiness.unfinishedWorkCount }) }}</el-tag>
          <el-tag v-if="accounting.draftFactCount" type="warning">{{ t('bossReview.allDrafts', { count: accounting.draftFactCount }) }}</el-tag>
        </div>
        <div class="finance-grid">
          <article><span>{{ t('bossReview.revenue') }}</span><strong>{{ accountingTotal('revenueAmount') }}</strong></article>
          <article><span>{{ t('bossReview.cost') }}</span><strong>{{ accountingTotal('costAmount') }}</strong></article>
          <article><span>{{ $tr("税前经营结果") }}</span><strong :class="hasReviewResults ? amountTone(accounting.summary?.profitAmount) : ''">{{ accountingTotal('pretaxProfit') }}</strong></article>
        </div>
        <section class="finance-grid">
          <article><span>{{ $tr("税额") }}</span><strong>{{ accountingTotal('taxAmount') }}</strong></article>
          <article><span>{{ $tr("税后盈利结果") }}</span><strong>{{ accountingTotal('afterTaxProfit') }}</strong></article>
          <article class="tax-rate-card">
            <span>{{ $tr("公司税率") }}</span>
            <div v-if="taxSettingsLoading" class="tax-rate-state">{{ $tr("读取中...") }}</div>
            <div v-else-if="taxSettingsError" class="tax-rate-state is-error">{{ $tr("税率读取失败") }}</div>
            <div v-else-if="taxSettings.length" class="tax-rate-list">
              <div v-for="company in taxSettings" :key="company.companyDeptId">
                <b>{{ company.companyName }}</b>
                <strong :class="{ 'is-unset': company.taxRate == null }">{{ taxRateLabel(company.taxRate) }}</strong>
              </div>
            </div>
            <div v-else class="tax-rate-state">{{ $tr("暂无可管理公司") }}</div>
          </article>
        </section>
        <p class="review-note">{{ t(accounting.dataStatus === 'INCOMPLETE' ? 'bossReview.incompleteNote' : 'bossReview.coverageNote') }}</p>
        <p v-if="accounting.readiness?.dataCutoffFrom" class="review-note">{{ t('bossReview.cutoff') }} {{ accounting.readiness.dataCutoffFrom }} ～ {{ accounting.readiness.dataCutoffTo }}</p>
        <div v-if="accounting.dataStatus === 'AVAILABLE' && !accounting.alerts?.length" class="healthy-banner">{{ t('bossReview.noReportedAlerts') }}</div>
        <div v-if="accounting.alerts?.length" class="alert-section">
          <div class="subsection-head"><div><b>{{ t('bossReview.periodAlerts') }}</b><span>{{ t('bossReview.budgetBasis') }}</span></div><el-tag type="danger">{{ accounting.alerts.length }}</el-tag></div>
          <div class="alert-grid">
            <button v-for="alert in accounting.alerts" :key="`${alert.alertType}-${alert.projectId}`" :class="['alert-card', `alert-card--${alertClass(alert.alertType)}`]" @click="openReviewAccounting(alert)">
              <span class="alert-icon">!</span><span class="alert-content"><b>{{ alert.projectName }}</b><span>{{ reviewAlertText(alert) }}</span></span><span class="alert-arrow">›</span>
            </button>
          </div>
        </div>
        <div v-if="accounting.currentAlerts?.length" class="alert-section">
          <div class="subsection-head"><div><b>{{ t('bossReview.currentAlerts') }}</b><span>{{ t('bossReview.currentBasis') }}</span></div></div>
          <div class="alert-grid">
            <button v-for="alert in accounting.currentAlerts" :key="`${alert.alertType}-${alert.projectId}`" class="alert-card" @click="alert.alertType === 'MISSING_COMPANY' ? openProject(alert) : openReviewAccounting(alert)">
              <span class="alert-icon">!</span><span class="alert-content"><b>{{ alert.projectName }}</b><span>{{ reviewAlertText(alert) }}</span><small>{{ t('bossReview.sourceDate') }} {{ alert.bizDate }}</small></span><span class="alert-arrow">›</span>
            </button>
          </div>
        </div>
      </template>
    </section>

    <section class="panel owner-load-panel">
      <div class="section-title section-title--between">
        <div><h2>{{ $tr("负责人项目负荷") }}</h2><span>{{ $tr("查看每位负责人当前承担的项目和管理费资格") }}</span></div>
        <el-tag type="success" effect="plain">{{ $tr("{0} 人达到条件", [eligibleOwnerCount]) }}</el-tag>
      </div>
      <div v-if="ownerLoads.length" class="owner-load-grid">
        <article v-for="owner in ownerLoads" :key="owner.ownerUserId" class="owner-load-card">
          <div class="owner-load-head">
            <span class="owner-avatar">{{ String(owner.ownerName||$tr("负")).slice(0,1) }}</span>
            <div><b>{{ owner.ownerName || $tr("未指定负责人") }}</b><small>{{ $tr("当前主负责项目") }}</small></div>
            <strong>{{ owner.projectCount }}<small>{{ $tr("个") }}</small></strong>
          </div>
          <div :class="['owner-eligibility', owner.managementFeeEligible?'is-eligible':'is-pending']">
            <span>{{ owner.managementFeeEligible?$tr("已达到管理费条件"):$tr("还差 {0} 个项目", [Math.max(0,3-Number(owner.projectCount||0))]) }}</span>
            <b>{{ owner.projectCount }}/3</b>
          </div>
          <div class="owner-project-preview">
            <button v-for="project in (owner.projects||[]).slice(0,3)" :key="project.projectId" @click="openProject(project)">
              <span>{{ project.projectName }}</span><small>{{ projectStatusLabel(project) }}</small>
            </button>
          </div>
          <el-button class="owner-load-more" link type="primary" @click="openOwnerLoad(owner)">{{ $tr("查看全部项目") }}</el-button>
        </article>
      </div>
      <div v-else-if="!projectLoading" class="empty-state">{{ $tr("当前没有负责人在管项目") }}</div>
    </section>

    <section class="panel project-panel">
      <div class="section-title section-title--between">
        <h2><button class="project-status-toggle" type="button" :aria-expanded="projectsExpanded" aria-controls="boss-project-status" @click="projectsExpanded = !projectsExpanded"><span aria-hidden="true">{{ projectsExpanded ? '▾' : '▸' }}</span>{{ $tr(" 项目状态 ") }}<small>{{ $tr("{0} 个项目 · {1}", [projectPage.total, projectsExpanded ? $tr("收起") : $tr("展开")]) }}</small></button></h2>
        <div class="project-filters">
          <el-input v-model="projectFilters.keyword" clearable :placeholder="$tr(&quot;项目名称 / 编号 / 负责人&quot;)" :aria-label="$tr(&quot;筛选项目&quot;)" @keyup.enter="applyProjectFilters" @clear="applyProjectFilters" />
          <el-select v-model="projectFilters.status" clearable :placeholder="$tr(&quot;全部状态&quot;)" :aria-label="$tr(&quot;项目状态筛选&quot;)" @change="applyProjectFilters"><el-option v-for="(label,value) in projectStatusOptions" :key="value" :label="label" :value="value" /></el-select>
          <el-button type="primary" @click="applyProjectFilters">{{ $tr("查询") }}</el-button>
          <el-button link type="primary" @click="router.push('/business/projects')">{{ $tr("查看全部项目") }}</el-button>
        </div>
      </div>
      <div id="boss-project-status" v-show="projectsExpanded">
      <div v-loading="projectLoading" class="project-grid">
        <article v-for="row in projects" :key="row.projectId" class="project-card">
          <div class="project-card-head">
            <button class="project-link" @click="openProject(row)">{{ row.projectName }}</button>
            <span><el-tag size="small" effect="plain">{{ managementLabel[row.managementMode] || row.managementMode }}</el-tag><el-tag size="small" type="success" effect="plain">{{ closeMethodLabel[row.closeMethod] || row.closeMethod }}</el-tag><BusinessProjectState :project="row" /></span>
          </div>
          <div class="progress-row">
            <span>{{ $tr("进度") }}</span>
            <el-progress :percentage="progress(row)" :status="row.status === 'CLOSED' ? 'success' : undefined" :stroke-width="8" />
            <span :title="progressHint(row)">{{ progressText(row) }}</span>
          </div>
          <div v-if="row.progressReportId" class="latest-progress-report">
            <div><span>{{ $tr("{0} · {1}填报", [row.progressBizDate, row.progressReporterName || row.mainOwnerName]) }}</span><b>{{ row.progressSummary }}</b></div>
            <el-button v-if="row.progressEvidenceUrls || row.progressEvidenceText" size="small" type="primary" plain @click="openProgressEvidence(row)">{{ $tr("成果凭证") }}</el-button>
          </div>
          <div v-else-if="!['CLOSED','CANCELED'].includes(row.status)" class="latest-progress-empty">{{ $tr("负责人尚未填报项目整体进度") }}</div>
          <div class="project-card-foot">
            <span>{{ $tr("{0} 负责", [row.mainOwnerName || $tr("未指定")]) }}</span>
            <el-tag size="small" :type="kpiMeta(row).tone" effect="light">KPI {{ kpiMeta(row).label }}</el-tag>
            <span>{{ $tr("风险：{0}", [row.openRiskCount ? $tr("{0} 项", [row.openRiskCount]) : $tr("无")]) }}</span>
            <span class="project-actions">
              <el-button v-if="kpiMeta(row).label !== $tr('已确认')" size="small" :type="kpiMeta(row).action === $tr('查看KPI') ? 'success' : 'primary'" @click="openKpi(row)">{{ kpiMeta(row).action }}</el-button>
              <el-button size="small" @click="openProject(row)">{{ $tr("详情") }}</el-button>
            </span>
          </div>
        </article>
        <div v-if="!projects.length && !projectLoading" class="empty-state">{{ appliedProjectFilters.projectKeyword || appliedProjectFilters.projectStatus ? $tr("没有符合筛选条件的项目") : $tr("尚未创建项目") }}</div>
      </div>
      <div class="project-pagination">
        <span>{{ $tr("第 {0} 页，共 {1} 个项目", [projectPage.pageNum, projectPage.total]) }}</span>
        <el-pagination v-model:current-page="projectPage.pageNum" v-model:page-size="projectPage.pageSize" :page-sizes="[4,8,12]" :pager-count="5" :total="projectPage.total" layout="sizes, prev, pager, next" background @current-change="loadProjectPage" @size-change="changeProjectPageSize" />
      </div>
      </div>
    </section>

    <el-dialog v-model="costDialogOpen" :title="costDialogTitle" width="min(580px, 94vw)" append-to-body destroy-on-close>
      <el-alert :title="$tr(&quot;仅用于公司内部项目核算，不代表员工工资单。每次保存都会产生新版本，历史成本不会被覆盖。&quot;)" type="warning" :closable="false" show-icon />
      <el-form :model="costForm" label-width="126px" class="cost-form">
        <el-form-item :label="$tr(&quot;人员&quot;)">
          <el-input :model-value="costPersonText" disabled />
          <div v-if="costDialogMode === 'batch'" class="form-help">{{ $tr("保存后将为上述人员各自产生一个新的成本版本。") }}<span v-if="batchUnavailableCount">{{ $tr(" 另有 {0} 人需先设置国家/地区，本次不处理。", [batchUnavailableCount]) }}</span></div>
        </el-form-item>
        <el-form-item :label="$tr(&quot;国家/地区&quot;)">
          <el-input :model-value="costRegionText" disabled />
        </el-form-item>
        <el-form-item :label="$tr(&quot;月度用人成本&quot;)" required>
          <el-input-number v-model="costForm.unitCost" :min="0" :precision="2" :step="100" controls-position="right" style="width:100%" />
          <div class="form-help">{{ $tr("统一使用人民币（CNY）填写。") }}</div>
        </el-form-item>
        <div class="cost-preview">
          <span>{{ $tr("系统折算") }}</span>
          <template v-for="preview in costPreviews" :key="preview.region">
            <b>{{ preview.formula }}</b>
            <small>{{ preview.hint }}</small>
          </template>
        </div>
        <el-form-item :label="$tr(&quot;生效日期&quot;)" required>
          <el-date-picker v-model="costForm.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$tr(&quot;失效日期&quot;)">
          <el-date-picker v-model="costForm.effectiveTo" type="date" value-format="YYYY-MM-DD" clearable style="width:100%" />
        </el-form-item>
        <el-form-item :label="$tr(&quot;调整说明&quot;)">
          <el-input v-model="costForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit :placeholder="$tr(&quot;例如：转正后调整月度用人成本&quot;)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="costDialogOpen = false">{{ $tr("取消") }}</el-button>
        <el-button type="primary" :loading="costSaving" @click="submitCostPolicy">{{ costSubmitLabel }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ownerLoadDialog" :title="$tr(&quot;{0}的在管项目&quot;, [selectedOwnerLoad?.ownerName||$tr(&quot;负责人&quot;)])" width="min(760px, 94vw)" append-to-body destroy-on-close>
      <div v-if="selectedOwnerLoad" class="owner-dialog-summary">
        <span>{{ $tr("当前在管 ") }}<b>{{ selectedOwnerLoad.projectCount }}</b>{{ $tr(" 个项目") }}</span>
        <el-tag :type="selectedOwnerLoad.managementFeeEligible?'success':'info'" effect="light">{{ selectedOwnerLoad.managementFeeEligible?$tr("已达到管理费条件"):$tr("未达到管理费条件（{0}/3）", [selectedOwnerLoad.projectCount]) }}</el-tag>
      </div>
      <el-table :data="selectedOwnerLoad?.projects||[]" stripe>
        <el-table-column prop="projectName" :label="$tr(&quot;项目&quot;)" min-width="210"><template #default="{row}"><b>{{ row.projectName }}</b><small class="owner-project-no">{{ row.projectNo }}</small></template></el-table-column>
        <el-table-column prop="companyName" :label="$tr(&quot;归属公司&quot;)" min-width="150" />
        <el-table-column :label="$tr(&quot;状态&quot;)" width="110"><template #default="{row}"><el-tag size="small" effect="plain">{{ projectStatusLabel(row) }}</el-tag></template></el-table-column>
        <el-table-column :label="$tr(&quot;操作&quot;)" width="90"><template #default="{row}"><el-button link type="primary" @click="ownerLoadDialog=false;openProject(row)">{{ $tr("详情") }}</el-button></template></el-table-column>
      </el-table>
      <template #footer><el-button type="primary" @click="ownerLoadDialog=false">{{ $tr("关闭") }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="evidenceDialog" :title="`${evidencePreview.title || ''} · ${evidencePreview.label || $tr(&quot;项目成果凭证&quot;)}`" width="min(840px, 96vw)" append-to-body destroy-on-close>
      <div class="evidence-dialog-summary"><span>{{ $tr("{0}提交", [evidencePreview.submitter || $tr("项目负责人")]) }}</span><span>{{ evidencePreview.date }}</span><span>{{ $tr("共 {0} 个文件", [evidencePreview.files.length]) }}</span></div>
      <p v-if="evidencePreview.evidenceText" class="evidence-text">{{ evidencePreview.evidenceText }}</p>
      <business-file-upload v-if="evidencePreview.rawUrls"
        :model-value="evidencePreview.rawUrls"
        :project-id="evidencePreview.projectId"
        disabled
        :drag="false"
        :is-show-tip="false"
      />
      <template #footer><el-button type="primary" @click="evidenceDialog=false">{{ $tr("关闭") }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessBoss">
import { translateText } from '@/locales/translate'

import CompanyAccessSettings from '../components/CompanyAccessSettings.vue'
import ProfitTaxSettings from '@/components/ProfitTaxSettings/index.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getBossBusinessDashboard, getBossBusinessPending, transitionBusinessProject } from '@/api/business/project'
import { confirmBusinessOperatingFact, getBusinessBossAccountingOverview, getBusinessProfitTaxSettings, returnBusinessOperatingFact } from '@/api/business/accounting'
import { getProjectKpiOverview } from '@/api/business/kpi'
import { reviewProjectProposal } from '@/api/business/proposal'
import { saveBusinessStaffCostPolicies, saveBusinessStaffCostPolicy } from '@/api/business/staff'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
import BusinessProjectState from '@/components/BusinessProjectState/index.vue'
import { isDeliveryEnded } from '@/utils/businessProjectState'

const router = useRouter()
const { t, locale } = useI18n()
const loading = ref(false)
const projectLoading = ref(false)
const projectsExpanded = ref(true)
const hasReviewResults = computed(() => Number(accounting.value.readiness?.resultCount || 0) > 0 || accounting.value.hasClosedAdjustments === true)
const accountingTotal = key => !hasReviewResults.value ? '—' : (accounting.value.summaryByCurrency?.length ? accounting.value.summaryByCurrency : [accounting.value.summary || {}]).map(row => money(row[key]) + (row.currency ? ' ' + row.currency : '')).join(' / ')
const summary = ref({})
const projects = ref([])
const ownerLoads = ref([])
const ownerLoadDialog = ref(false)
const selectedOwnerLoad = ref(null)
const projectPage = reactive({ pageNum: 1, pageSize: 4, total: 0 })
const projectFilters = reactive({keyword:'',status:''})
const appliedProjectFilters = reactive({projectKeyword:'',projectStatus:''})
const projectStatusOptions = {DRAFT:translateText("草稿"),PLANNING:translateText("规划中"),ACTIVE:translateText("执行中"),PAUSED:translateText("已暂停"),ACCEPTANCE:translateText("待验收"),CLOSED:translateText("已关闭"),CANCELED:translateText("已取消")}
let projectRequestSequence = 0
const kpiOverviews = ref([])
const accounting = ref({})
const accountingLoading = ref(true)
const accountingError = ref(false)
const taxSettings = ref([])
const taxSettingsLoading = ref(true)
const taxSettingsError = ref(false)
const reportPeriod = ref('yesterday')
const customReportDate = ref(null)
let accountingRequestSequence = 0
const pendingRows = ref([])
const pendingCounts = ref({})
const pendingTotal = ref(0)
const pendingExpanded = ref(false)
const personnelExpanded = ref(false)
const costDialogOpen = ref(false)
const costDialogMode = ref('single')
const selectedPersonnel = ref(null)
const costSaving = ref(false)
const evidenceDialog = ref(false)
const evidencePreview = ref({ title: '', label: '', submitter: '', date: '', rawUrls: '', projectId: null, files: [] })
const costForm = reactive({ unitCost: null, effectiveFrom: '', effectiveTo: null, remark: '' })

const statusLabel = { DRAFT: translateText("草稿"), PLANNING: translateText("规划中"), ACTIVE: translateText("执行中"), PAUSED: translateText("已暂停"), ACCEPTANCE: translateText("待验收"), CLOSED: translateText("已关闭"), CANCELED: translateText("已取消") }
const statusTone = { DRAFT: 'info', PLANNING: 'warning', ACTIVE: 'primary', PAUSED: 'info', ACCEPTANCE: 'success', CLOSED: 'success', CANCELED: 'danger' }
const managementLabel={LIGHT:translateText("轻量"),STANDARD:translateText("标准"),KEY_CONTROL:translateText("重点监管"),SIMPLE:translateText("轻量"),DELIVERY:translateText("标准")}
const closeMethodLabel={DIRECT:translateText("直接结项"),RESULT_ACCEPTANCE:translateText("成果验收"),STAGED_ACCEPTANCE:translateText("阶段验收")}
const actionMeta = { START_PLANNING: { label: translateText("进入规划"), type: 'primary' }, CONFIRM_BASELINE: { label: translateText("确认并启动"), type: 'success' }, RETURN_PLAN: { label: translateText("退回计划"), type: 'warning', plain: true }, RESUME: { label: translateText("恢复执行"), type: 'primary' }, REVIEW_ACCEPTANCE: { label: translateText("查看验收资料"), type: 'success' }, CLOSE: { label: translateText("确认结项并冻结"), type: 'success' }, RETURN_ACTIVE: { label: translateText("退回补充"), type: 'warning', plain: true } }
const projectStatusLabel = row => row?.status === 'ACCEPTANCE' && row?.closeMethod === 'STAGED_ACCEPTANCE' ? translateText("待结项") : statusLabel[row?.status] || row?.status

const idKey = value => value === null || value === undefined ? '' : String(value)
const kpiOverviewMap = computed(() => new Map(kpiOverviews.value.map(item => [idKey(item.projectId), item])))
const totalPendingCount = computed(() => pendingTotal.value)
const eligibleOwnerCount = computed(() => ownerLoads.value.filter(owner => owner.managementFeeEligible).length)
const personnelRows = computed(() => pendingRows.value.filter(row => row.category === 'PERSONNEL_COST'))
const batchEligibleRows = computed(() => personnelRows.value.filter(row => row.costStatus !== 'MISSING_REGION'))
const batchUnavailableCount = computed(() => personnelRows.value.length - batchEligibleRows.value.length)
const groupedPendingRows = computed(() => {
  const result = []
  let personnelAdded = false
  pendingRows.value.forEach(row => {
    if (row.category !== 'PERSONNEL_COST') return result.push(row)
    if (!personnelAdded) {
      result.push({ ...row, category: 'PERSONNEL_COST_GROUP', itemKey: 'personnel-cost-group' })
      personnelAdded = true
    }
  })
  return result
})
const visiblePendingRows = computed(() => pendingExpanded.value ? groupedPendingRows.value : groupedPendingRows.value.slice(0, 2))
const personnelPreview = computed(() => {
  const names = personnelRows.value.slice(0, 4).map(row => row.userName).join('、')
  return `${names}${personnelRows.value.length > 4 ? translateText(" 等 {0} 人", [personnelRows.value.length]) : ''} `
})
const costDialogTitle = computed(() => costDialogMode.value === 'batch' ? translateText("批量设置月度用人成本") : translateText("设置月度用人成本"))
const costSubmitLabel = computed(() => costDialogMode.value === 'batch' ? translateText("批量保存成本版本") : translateText("保存成本版本"))
const costTargetRows = computed(() => costDialogMode.value === 'batch' ? batchEligibleRows.value : selectedPersonnel.value ? [selectedPersonnel.value] : [])
const costPersonText = computed(() => costDialogMode.value === 'batch' ? translateText("{0} 名未设置人员", [costTargetRows.value.length]) : selectedPersonnel.value?.userName || '—')
const costRegionCounts = computed(() => costTargetRows.value.reduce((result, row) => {
  const region = row.countryRegion
  if (region === 'CN' || region === 'VN') result[region] = (result[region] || 0) + 1
  return result
}, {}))
const costRegionText = computed(() => {
  if (costDialogMode.value === 'single') return regionLabel(selectedPersonnel.value?.countryRegion)
  return [['CN', translateText("中国")], ['VN', translateText("越南")]].filter(([code]) => costRegionCounts.value[code]).map(([code, label]) => translateText("{0} {1} 人", [label, costRegionCounts.value[code]])).join('、') || '—'
})
const costPreviews = computed(() => {
  const definitions = { CN: { label: translateText("中国"), days: 21.75 }, VN: { label: translateText("越南"), days: 26 } }
  const regions = costDialogMode.value === 'batch' ? Object.keys(costRegionCounts.value) : [selectedPersonnel.value?.countryRegion].filter(Boolean)
  return regions.map(region => {
    const definition = definitions[region]
    if (!definition) return { region, formula: translateText("— ÷ — 天 = — 元/天"), hint: translateText("该国家/地区尚未配置折算规则") }
    const monthly = costForm.unitCost === null || costForm.unitCost === undefined ? '—' : costMoney(costForm.unitCost)
    const daily = costForm.unitCost === null || costForm.unitCost === undefined ? '—' : costMoney(Number(costForm.unitCost) / definition.days)
    const countHint = costDialogMode.value === 'batch' ? translateText("，本次 {0} 人", [costRegionCounts.value[region]]) : ''
    return { region, formula: translateText("{0} ÷ {1} 天 = {2} 元/天", [monthly, definition.days, daily]), hint: translateText("{0}员工按 {1} 天{2}", [definition.label, definition.days, countHint]) }
  })
})

const progress = row => {
  if (row.status === 'CLOSED') return 100
  if (row.status === 'CANCELED') return 0
  const value = Number(row.progressPercent)
  if (Number.isFinite(value)) return Math.min(100, Math.max(0, Math.round(value)))
  return 0
}
const progressText = row => !row.progressReportId && !['CLOSED', 'CANCELED'].includes(row.status) ? translateText("暂无填报") : `${progress(row)}%`
const progressHint = row => row.progressReportId
  ? translateText("项目负责人于 {0} 填报，与一次性任务进度独立", [row.progressBizDate])
  : row.status === 'CLOSED' ? translateText("项目已正式结项") : row.status === 'CANCELED' ? translateText("项目已取消") : translateText("等待项目负责人填报整体完成进度")
const evidencePaths = value => String(value || '').split(',').map(item => item.trim()).filter(Boolean)
const evidenceCount = value => evidencePaths(value).length
const evidenceName = path => { const clean = path.split('?')[0]; try { return decodeURIComponent(clean.slice(clean.lastIndexOf('/') + 1)) || translateText("成果凭证") } catch { return clean.slice(clean.lastIndexOf('/') + 1) || translateText("成果凭证") } }
const evidenceKind = path => { const ext = path.split('?')[0].split('.').pop()?.toLowerCase(); if (['jpg','jpeg','png','gif','webp','bmp'].includes(ext)) return 'image'; if (['mp4','mov','webm','ogg'].includes(ext)) return 'video'; return 'file' }
function buildEvidenceFiles(value) { return evidencePaths(value).map(path => ({ path, name: evidenceName(path), kind: evidenceKind(path) })) }
function openProgressEvidence(row) { evidencePreview.value = { title: row.projectName, label: translateText("项目成果凭证"), submitter: row.progressReporterName || row.mainOwnerName, date: row.progressBizDate, rawUrls: row.progressEvidenceUrls, evidenceText: row.progressEvidenceText, projectId: row.projectId, files: buildEvidenceFiles(row.progressEvidenceUrls) }; evidenceDialog.value = true }
function openStageEvidence(row) { evidencePreview.value = { title: `${row.projectName} · ${row.milestoneName}`, label: translateText("阶段验收文件"), submitter: row.submitterName || row.mainOwnerName, date: row.submittedTime, rawUrls: row.attachmentUrls, projectId: row.projectId, files: buildEvidenceFiles(row.attachmentUrls) }; evidenceDialog.value = true }
function projectIdFromRow(row) {
  if (idKey(row?.projectId)) return row.projectId
  const keyMatch = idKey(row?.itemKey).match(/^kpi-(?:missing|review)-(\d+)$/)
  if (keyMatch) return keyMatch[1]
  return projects.value.find(project => project.projectName === row?.projectName)?.projectId
}
const openProject = (row, tab) => {
  const projectId = projectIdFromRow(row)
  if (!idKey(projectId)) return ElMessage.warning(translateText("未识别到当前项目，请刷新后重试"))
  router.push({ path: '/business/projects', query: { id: projectId, ...(tab ? { tab } : {}) } })
}
const openProposal = row => router.push({ path: '/business/project-proposals', query: { tab: 'review', id: row.proposalId } })
const openAccounting = (query = {}) => router.push({ path: '/business/accounting', query })
const openAccountingEntry = action => openAccounting({ action })
const taxRateLabel = value => value == null ? translateText("未设置（暂按 0%）") : `${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })}%`
const openReviewAccounting = (row = {}) => {
  const date = row.bizDate || accounting.value.bizDate
  if (!date) return
  openAccounting({ ...(row.projectId ? { projectId: row.projectId } : {}), dateFrom: date, dateTo: date })
}
const reviewAlertText = row => locale.value === 'zh-CN' ? row.alertMessage : t(`bossReview.alert${row.alertType}`, { amount: money(row.amount) })
const futureReportDate = date => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
  return local > (accounting.value.currentBizDate || localToday())
}
async function changeReportPeriod() {
  if (reportPeriod.value === 'custom' && !customReportDate.value) customReportDate.value = accounting.value.bizDate || localToday()
  await loadAccounting()
}
async function loadAccounting() {
  const sequence = ++accountingRequestSequence
  const bizDate = reportPeriod.value === 'custom' ? customReportDate.value : reportPeriod.value
  if (!bizDate) return
  accountingLoading.value = true
  accountingError.value = false
  try {
    const result = await getBusinessBossAccountingOverview({ bizDate })
    if (sequence === accountingRequestSequence) accounting.value = result.data || {}
  } catch {
    if (sequence === accountingRequestSequence) accountingError.value = true
  } finally {
    if (sequence === accountingRequestSequence) accountingLoading.value = false
  }
}
async function loadTaxSettings() {
  taxSettingsLoading.value = true
  taxSettingsError.value = false
  try {
    const result = await getBusinessProfitTaxSettings()
    taxSettings.value = result.data || []
  } catch {
    taxSettingsError.value = true
  } finally {
    taxSettingsLoading.value = false
  }
}
async function handleTaxSettingsChanged() {
  await Promise.all([loadTaxSettings(), loadAccounting()])
}
function openOwnerLoad(owner) { selectedOwnerLoad.value = owner; ownerLoadDialog.value = true }
const openPendingAccounting = row => openAccounting({ projectId: row.projectId, dateFrom: row.bizDate, dateTo: row.bizDate })
const openKpi = row => {
  const projectId = projectIdFromRow(row)
  if (!idKey(projectId)) return ElMessage.warning(translateText("未识别到当前项目，请刷新后重试"))
  const overview = kpiOverviewMap.value.get(idKey(projectId))
  const planId = row.planId || overview?.planId
  const path = row.category === 'KPI_MISSING' || !planId ? '/business/kpi-bonus' : '/projects/kpi-results'
  router.push({ path, query: { projectId, ...(planId ? { planId } : {}) } })
}
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const costMoney = value => value === null || value === undefined ? '—' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
const regionLabel = value => value === 'CN' ? translateText("中国") : value === 'VN' ? translateText("越南") : value || translateText("未设置")
const signed = value => `${Number(value || 0) > 0 ? '+' : ''}${money(value)}`
const amountTone = value => Number(value || 0) < 0 ? 'amount-loss' : 'amount-profit'
const alertClass = type => String(type || 'warning').toLowerCase().replaceAll('_', '-')
const kpiMeta = row => {
  const overview = kpiOverviewMap.value.get(idKey(row.projectId))
  if (isDeliveryEnded(row) && (!overview || !overview.planId)) return { label: translateText("无结算方案"), tone: 'info', action: translateText("查看KPI") }
  if (!overview || !Number(overview.targetCount)) return { label: translateText("未设置"), tone: 'warning', action: translateText("设置KPI") }
  if (!overview.planId) return { label: translateText("待发布"), tone: 'warning', action: translateText("继续配置") }
  const meta = { DRAFT: { label: translateText("填报中"), tone: 'primary' }, SUBMITTED: { label: translateText("待确认"), tone: 'warning' }, RETURNED: { label: translateText("已退回"), tone: 'danger' }, CONFIRMED: { label: translateText("已确认"), tone: 'success' } }[overview.settlementStatus]
  return { ...(meta || { label: translateText("方案 v{0}", [overview.planVersion]), tone: 'info' }), action: overview.settlementStatus === 'SUBMITTED' ? translateText("审核结算") : translateText("查看KPI") }
}
const isStagedClosePending = row => row.status === 'ACCEPTANCE' && row.description === 'STAGED_ACCEPTANCE'
const decisionActions = row => row.status === 'DRAFT' ? [{ key: 'START_PLANNING', ...actionMeta.START_PLANNING }] : row.status === 'PLANNING' && row.baselineStatus === 'SUBMITTED' ? [{ key: 'CONFIRM_BASELINE', ...actionMeta.CONFIRM_BASELINE }, { key: 'RETURN_PLAN', ...actionMeta.RETURN_PLAN }] : row.status === 'PAUSED' ? [{ key: 'RESUME', ...actionMeta.RESUME }] : isStagedClosePending(row) ? [{ key: 'CLOSE', ...actionMeta.CLOSE }, { key: 'RETURN_ACTIVE', ...actionMeta.RETURN_ACTIVE }] : row.status === 'ACCEPTANCE' ? [{ key: 'REVIEW_ACCEPTANCE', ...actionMeta.REVIEW_ACCEPTANCE }] : []
const decisionHint = row => row.status === 'DRAFT' ? translateText("历史草稿等待确认进入规划") : row.status === 'PLANNING' ? translateText("历史计划已提交，等待确认或退回") : row.status === 'PAUSED' ? translateText("项目处于暂停状态，决定是否恢复执行") : isStagedClosePending(row) ? translateText("所有里程碑和结项前置条件已完成，负责人申请确认结项") : row.status === 'ACCEPTANCE' ? translateText("验收资料已提交，等待关闭或退回执行") : translateText("需要老板处理")
const accountingValue = row => row.factKind === 'VALUE' ? `${row.quantity ?? '—'} ${translateText(row.unit) || ''}`.trim() : `${money(row.amount)} ${row.currency || ''}`.trim()
const pendingLabel = row => row.category === 'BONUS_PAYMENT' ? translateText("奖金待发放") : row.category === 'MANAGEMENT_FEE' ? translateText("管理费待设置") : row.category === 'INCENTIVE_REVIEW' ? translateText("奖金待核准") : row.category === 'PROPOSAL' ? translateText("历史立项待处理") : row.category === 'ACCOUNTING' ? translateText("收支待确认") : row.category === 'STAGE_ACCEPTANCE' ? translateText("待阶段验收") : row.category === 'KPI_MISSING' ? (Number(row.targetCount) ? translateText("KPI 待发布") : translateText("KPI 待设置")) : row.category === 'KPI_REVIEW' ? translateText("KPI 结算待确认") : isStagedClosePending(row) ? translateText("项目待结项") : translateText("项目状态待处理")
const pendingDotClass = row => ['KPI_MISSING','MANAGEMENT_FEE'].includes(row.category) ? 'dot-danger' : ['PERSONNEL_COST_GROUP', 'PROPOSAL', 'ACCOUNTING', 'STAGE_ACCEPTANCE', 'KPI_REVIEW', 'INCENTIVE_REVIEW', 'BONUS_PAYMENT'].includes(row.category) ? 'dot-warning' : 'dot-info'
const pendingBadgeClass = row => ['KPI_MISSING','MANAGEMENT_FEE'].includes(row.category) ? 'badge-danger' : ['PROPOSAL', 'ACCOUNTING', 'STAGE_ACCEPTANCE', 'KPI_REVIEW', 'INCENTIVE_REVIEW', 'BONUS_PAYMENT'].includes(row.category) ? 'badge-warning' : 'badge-info'
const pendingDescription = row => row.category === 'BONUS_PAYMENT' ? translateText("{0} · 分配单 #{1} · {2} 人待发放 · 待发 {3} {4}", [translateText(row.categoryName) || translateText("项目奖金"), row.allocationId, row.quantity, money(row.amount), row.currency || '']) : row.category === 'MANAGEMENT_FEE' ? row.description : row.category === 'INCENTIVE_REVIEW' ? `${translateText(row.categoryName) || translateText("奖金申请")} #${row.awardId} · ${money(row.amount)} ${row.currency || ''} · ${row.description || translateText("等待核准")}` : row.category === 'PROPOSAL' ? (row.objective || translateText("历史立项申请待处理")) : row.category === 'ACCOUNTING' ? `${translateText(row.categoryName) || translateText("项目收支")}：${row.description || translateText("负责人提交的今日收支")}（${accountingValue(row)}）` : row.category === 'STAGE_ACCEPTANCE' ? translateText("{0} · 交付成果：{1}", [row.resultSummary || translateText("负责人已提交阶段成果"), row.deliverables || '—']) : row.category === 'KPI_MISSING' ? (Number(row.targetCount) ? translateText("已有 {0} 项 KPI 目标，但尚未发布考核与奖金方案", [row.targetCount]) : translateText("项目已进入执行流程，KPI 目标待设置")) : row.category === 'KPI_REVIEW' ? translateText("负责人已提交 KPI 结果，确认后项目奖金会立即计入成本") : decisionHint(row)
const pendingMeta = row => {
  if (row.category === 'PROPOSAL') return translateText("{0} 负责 · {1} ", [row.applicantName, row.companyName || translateText("未设置公司")])
  if (['ACCOUNTING', 'INCENTIVE_REVIEW'].includes(row.category)) return translateText("{0}提交 · {1} · {2} ", [row.submitterName || translateText("项目负责人"), row.bizDate || '—', row.companyName || translateText("未设置公司")])
  if (row.category === 'STAGE_ACCEPTANCE') return translateText("{0}提交 · 里程碑“{1}” · {2} ", [row.submitterName || row.mainOwnerName || translateText("项目负责人"), row.milestoneName || translateText("未命名"), row.submittedTime || '—'])
  if (row.category === 'KPI_REVIEW') return translateText("方案 v{0} · 截止 {1} · 综合得分 {2} ", [row.planVersion, row.cycleEnd || '—', row.totalScore ?? '—'])
  if (row.category === 'MANAGEMENT_FEE') return translateText("{0}负责 · 当前在管 {1} 个项目 ", [row.mainOwnerName || translateText("未指定负责人"), row.projectCount || 0])
  return translateText("{0} 负责 ", [row.mainOwnerName || translateText("未指定负责人")])
}
const personnelMeta = row => `${row.companyName || translateText("未设置所属公司")} · ${row.projectNameText || translateText("尚未加入未结束项目")}`

function openBonusPayment(row) {
  router.push({ path: '/hcm/incentives', query: { projectId: row.projectId, tab: 'distribution', allocationId: row.allocationId } })
}

function openIncentive(row) {
  router.push({ path: '/hcm/incentives', query: { projectId: row.projectId, tab: 'awards', awardId: row.awardId } })
}

function localToday() {
  const parts = new Intl.DateTimeFormat('en', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
  const part = type => parts.find(value => value.type === type).value
  return `${part('year')}-${part('month')}-${part('day')}`
}
function resetCostForm() {
  costForm.unitCost = null
  costForm.effectiveFrom = localToday()
  costForm.effectiveTo = null
  costForm.remark = ''
}
function openSingleCost(row) {
  if (row.costStatus === 'MISSING_REGION') return router.push({ path: '/business/staff', query: { userId: row.userId, action: 'edit' } })
  selectedPersonnel.value = row
  costDialogMode.value = 'single'
  resetCostForm()
  costDialogOpen.value = true
}
function openBatchCost() {
  if (!batchEligibleRows.value.length) return ElMessage.warning(translateText("请先为人员设置国家/地区"))
  selectedPersonnel.value = null
  costDialogMode.value = 'batch'
  resetCostForm()
  costForm.remark = translateText("老板工作台批量设置")
  costDialogOpen.value = true
}
function costPolicyPayload(row) {
  return { userId: row.userId, costMode: 'MONTHLY', unitCost: costForm.unitCost, currency: 'CNY', effectiveFrom: costForm.effectiveFrom, effectiveTo: costForm.effectiveTo, remark: costForm.remark?.trim() || '' }
}
async function submitCostPolicy() {
  if (costForm.unitCost === null || costForm.unitCost === undefined) return ElMessage.warning(translateText("请填写月度用人成本"))
  if (!costForm.effectiveFrom) return ElMessage.warning(translateText("请选择生效日期"))
  if (costForm.effectiveTo && costForm.effectiveTo < costForm.effectiveFrom) return ElMessage.warning(translateText("失效日期不能早于生效日期"))
  costSaving.value = true
  try {
    if (costDialogMode.value === 'batch') {
      await saveBusinessStaffCostPolicies(batchEligibleRows.value.map(costPolicyPayload))
      ElMessage.success(translateText("已为 {0} 名人员设置月度用人成本", [batchEligibleRows.value.length]))
    } else {
      await saveBusinessStaffCostPolicy(costPolicyPayload(selectedPersonnel.value))
      ElMessage.success(translateText("已设置 {0} 的月度用人成本", [selectedPersonnel.value.userName]))
    }
    costDialogOpen.value = false
    await load()
  } finally {
    costSaving.value = false
  }
}

async function loadAllPending() {
  const pageSize = 50
  const firstResult = await getBossBusinessPending({ pageNum: 1, pageSize, category: 'ALL' })
  const first = firstResult.data || {}
  const total = Number(first.total || 0)
  const pages = Math.ceil(total / pageSize)
  let rows = first.rows || []
  if (pages > 1) {
    const results = await Promise.all(Array.from({ length: pages - 1 }, (_, index) => getBossBusinessPending({ pageNum: index + 2, pageSize, category: 'ALL' })))
    rows = rows.concat(...results.map(result => result.data?.rows || []))
  }
  return { rows, total, counts: first.counts || {} }
}
async function loadVisibleProjectKpis(sequence = projectRequestSequence) {
  const projectIds = projects.value.map(row => row.projectId).filter(Boolean)
  if (!projectIds.length) { kpiOverviews.value = []; return }
  const result = await getProjectKpiOverview({ projectIds: projectIds.join(',') })
  if (sequence === projectRequestSequence) kpiOverviews.value = result.data || []
}
async function load() {
  loading.value = true
  try {
    const [, , pending] = await Promise.all([
      loadProjectPage(),
      loadAccounting(),
      loadAllPending(),
      loadTaxSettings()
    ])
    pendingRows.value = pending.rows
    pendingTotal.value = pending.total
    pendingCounts.value = pending.counts
  } finally {
    loading.value = false
  }
}
function applyProjectFilters() {
  Object.assign(appliedProjectFilters, {projectKeyword:projectFilters.keyword.trim(),projectStatus:projectFilters.status || ''})
  projectsExpanded.value = true
  projectPage.pageNum = 1
  loadProjectPage()
}
function changeProjectPageSize() {
  projectPage.pageNum = 1
  loadProjectPage()
}
async function loadProjectPage() {
  const sequence = ++projectRequestSequence
  projectLoading.value = true
  try {
    const result = await getBossBusinessDashboard({ projectPageNum: projectPage.pageNum, projectPageSize: projectPage.pageSize, decisionPageSize: 1, ...appliedProjectFilters })
    if (sequence !== projectRequestSequence) return
    const data = result.data || {}
    const page = data.projectPage || {}
    summary.value = data.summary || {}
    ownerLoads.value = data.ownerLoads || []
    projects.value = page.rows || data.projects || []
    projectPage.total = Number(page.total ?? summary.value.totalCount ?? projects.value.length)
    projectPage.pageNum = Number(page.pageNum || 1)
    await loadVisibleProjectKpis(sequence)
  } finally {
    if (sequence === projectRequestSequence) projectLoading.value = false
  }
}
async function refreshProjectProgress() {
  if (projectLoading.value || document.hidden) return
  await loadProjectPage()
}
async function decideProposal(row, decision) {
  let comment = ''
  if (decision === 'RETURNED') {
    const result = await ElMessageBox.prompt(translateText("请填写退回原因"), translateText("退回立项申请"), { inputValidator: value => !!value?.trim() || translateText("必须填写退回原因") })
    comment = result.value
  } else {
    await ElMessageBox.confirm(translateText("批准“{0}”后将直接创建执行中项目，并由 {1} 负责。确认批准吗？", [row.projectName, row.applicantName]), translateText("批准立项"), { type: 'warning' })
  }
  await reviewProjectProposal(row.proposalId, { decision, comment })
  ElMessage.success(decision === 'APPROVED' ? translateText("已批准立项并启动项目") : translateText("申请已退回修改"))
  await load()
}
async function confirmPendingAccounting(row) {
  await ElMessageBox.confirm(translateText("确认“{0}”的{1} {2} 入账吗？确认后将计入正式日报。", [row.projectName, translateText(row.categoryName) || translateText("今日收支"), accountingValue(row)]), translateText("确认收支入账"), { type: 'warning' })
  await confirmBusinessOperatingFact(row.factId)
  ElMessage.success(translateText("收支已确认入账并生成项目日结果"))
  await load()
}
async function returnPendingAccounting(row) {
  const { value } = await ElMessageBox.prompt(
    translateText("请说明“{0}”的{1}需要修改的内容，提交人将看到该原因。", [row.projectName, translateText(row.categoryName) || translateText("今日收支")]),
    translateText("退回收支修改"),
    { inputValidator: text => !!text?.trim() || translateText("必须填写退回原因"), inputAttributes: { maxlength: 500 }, type: 'warning' }
  )
  await returnBusinessOperatingFact(row.factId, { reason: value.trim() })
  ElMessage.success(translateText("收支已退回提交人修改"))
  await load()
}
async function doTransition(row, action) {
  if (action === 'REVIEW_ACCEPTANCE') return openProject(row, 'acceptance')
  const meta = actionMeta[action] || { label: translateText("执行操作") }
  let comment = ''
  if (['RETURN_PLAN', 'RETURN_ACTIVE', 'CLOSE'].includes(action)) {
    const result = await ElMessageBox.prompt(action === 'CLOSE' ? translateText("请填写项目完成结论。确认后系统将完成最终核算并冻结项目数据。") : translateText("请输入“{0}”的原因，负责人将在项目动态中看到", [meta.label]), translateText("老板决策"), { inputValidator: value => !!value?.trim() || translateText("必须填写说明") })
    comment = result.value
  } else {
    await ElMessageBox.confirm(translateText("确定对“{0}”执行“{1}”吗？", [row.projectName, meta.label]), translateText("老板确认"), { type: 'warning' })
  }
  await transitionBusinessProject(row.projectId, { action, comment })
  ElMessage.success(translateText("操作成功"))
  await load()
}

let progressRefreshTimer
onMounted(() => {
  load()
  progressRefreshTimer = window.setInterval(() => {
    refreshProjectProgress().catch(() => {})
    if (!accountingLoading.value && accounting.value.currentBizDate && accounting.value.currentBizDate !== localToday()) loadAccounting()
  }, 15000)
})
useBusinessRefreshOnReactivated(load)
onBeforeUnmount(() => window.clearInterval(progressRefreshTimer))
</script>

<style scoped>
.review-date-controls .el-select{width:150px}.review-readiness{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.review-note{color:#64748b;font-size:13px;line-height:1.6}.review-readiness{margin:12px 0}
.project-filters{display:flex;align-items:center;justify-content:flex-end;flex-wrap:wrap;gap:8px}.project-filters>.el-input{width:235px}.project-filters>.el-select{width:130px}.project-filters>.el-button{margin:0}.project-pagination :deep(.el-pagination){flex-wrap:wrap;gap:8px}@media(max-width:1100px){.project-panel>.section-title{flex-wrap:wrap;gap:14px}.project-filters{justify-content:flex-start}}
.project-status-toggle{display:flex;align-items:center;flex-wrap:wrap;gap:8px;border:0;padding:0;background:none;color:inherit;font:inherit;cursor:pointer;text-align:left}.project-status-toggle small{font-size:12px;font-weight:400;color:#8492a3}.project-status-toggle:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:4px;border-radius:4px}
.business-page{min-height:calc(100vh - 84px);padding:24px;background:#eef1f5;color:#12213a}.hero{display:flex;align-items:center;justify-content:space-between;min-height:134px;padding:26px 40px;border-radius:18px;background:#1d344f;color:#fff;box-shadow:0 12px 30px rgba(27,48,74,.13)}.eyebrow{font-size:12px;letter-spacing:.28em;color:#78ecd1}.hero h1{margin:15px 0 8px;font-size:30px;line-height:1}.hero p{margin:0;color:#d2deea;font-size:15px}.hero-actions,.panel-actions{display:flex;align-items:center;gap:10px}.hero-actions{flex-wrap:wrap;justify-content:flex-end}.hero-actions :deep(.el-button){height:42px;margin:0;padding:0 20px;border-radius:11px;font-weight:700}.panel{margin-top:20px;padding:24px 26px;border:0;border-radius:17px;background:#fff;box-shadow:0 7px 20px rgba(29,50,75,.06)}.section-title{display:flex;align-items:baseline;gap:7px;margin-bottom:18px}.section-title h2{margin:0;font-size:19px}.section-title>span{color:#8493a7;font-size:13px}.section-title--between{align-items:center;justify-content:space-between}.empty-state{padding:30px;text-align:center;color:#93a0b1}.success-empty{border-radius:10px;background:#edf9f2;color:#18a856}.success-empty span{margin-right:8px;font-weight:800}.decision-row{display:flex;align-items:center;gap:16px;padding:19px 20px;border:1px solid #dfe6ef;border-radius:14px}.decision-row+.decision-row{margin-top:14px}.decision-dot{width:10px;height:10px;flex:none;border-radius:50%}.dot-danger{background:#ef323a}.dot-warning{background:#df7c00}.dot-info{background:#4a83d8}.decision-copy{min-width:0;flex:1}.decision-title{display:flex;align-items:center;gap:10px}.decision-title b{font-size:16px}.decision-count{color:#df7c00;font-weight:700}.badge-danger{color:#e04b00}.badge-warning{color:#df7c00}.badge-info{color:#3f75bd}.decision-copy>p{margin:7px 0 0;color:#8493a7;font-size:14px;line-height:1.55}.decision-actions{display:flex;flex:none;align-self:flex-start;flex-wrap:wrap;justify-content:flex-end;gap:8px}.decision-actions :deep(.el-button){margin:0;font-weight:650}.pending-toggle{display:flex;justify-content:center;padding-top:15px}.pending-toggle :deep(.el-button){font-weight:650}.pending-toggle-arrow{display:inline-block;margin-left:5px;font-size:16px;transition:transform .2s ease}.pending-toggle-arrow.is-expanded{transform:rotate(180deg)}.personnel-list{margin-top:14px;border-top:1px dashed #dce4ee}.personnel-item{display:grid;grid-template-columns:110px minmax(0,1fr) auto;align-items:center;gap:18px;padding:10px 2px;border-bottom:1px dashed #dce4ee}.personnel-item>b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.personnel-item>span{overflow:hidden;color:#8493a7;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.finance-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.finance-grid article{padding:20px;border:1px solid #dfe6ef;border-radius:13px;background:#fafbfd}.finance-grid span,.finance-grid strong{display:block}.finance-grid span{color:#8794a8;font-size:14px}.finance-grid strong{margin-top:12px;font-size:29px;line-height:1}.tax-rate-list{display:grid;gap:9px;margin-top:11px}.tax-rate-list>div{display:flex;align-items:center;justify-content:space-between;gap:12px}.tax-rate-list b{overflow:hidden;color:#435167;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.tax-rate-list strong{flex:none;margin:0;color:#12213a;font-size:17px;line-height:1.25}.tax-rate-list strong.is-unset{color:#c57b12;font-size:13px}.tax-rate-state{margin-top:12px;color:#7f8ca0;font-size:14px}.tax-rate-state.is-error{color:#d84e58}.amount-profit{color:#11a957}.amount-loss{color:#d84e58}.healthy-banner{margin-top:15px;padding:11px 16px;border-radius:10px;background:#e7f7ed;color:#11a957;font-size:14px}.alert-section{margin-top:16px;padding:16px;border:1px solid #e5eaf0;border-radius:12px;background:#f8fafc}.subsection-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}.subsection-head>div{display:flex;align-items:baseline;gap:10px}.subsection-head span{color:#8a95a2;font-size:12px}.alert-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.alert-card{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:11px;padding:14px;border:1px solid #e0e6ec;border-radius:11px;background:#fff;color:inherit;text-align:left;cursor:pointer}.alert-card:hover{border-color:#b9c7d5;box-shadow:0 7px 18px rgba(31,53,74,.09)}.alert-icon{display:flex;width:30px;height:30px;align-items:center;justify-content:center;border-radius:9px;background:#fff0f1;color:#d94e58;font-weight:800}.alert-card--over-budget .alert-icon{background:#fff5e6;color:#c8841c}.alert-card--missing-company .alert-icon{background:#eef4fb;color:#4f78a8}.alert-content{display:flex;min-width:0;flex-direction:column}.alert-content>b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.alert-content>span{margin-top:4px;color:#788695;font-size:12px}.alert-arrow{color:#a3adb8;font-size:24px}.alert-footer{display:flex;justify-content:flex-end;padding-top:8px}.project-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;min-height:60px}.project-card{padding:19px 20px;border:1px solid #dfe6ef;border-radius:14px}.project-card-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.project-link{min-width:0;overflow:hidden;padding:0;border:0;background:none;color:#13213a;font:inherit;font-size:16px;font-weight:700;text-align:left;text-overflow:ellipsis;white-space:nowrap;cursor:pointer}.project-link:hover{color:#3478ef}.progress-row{display:grid;grid-template-columns:auto minmax(80px,1fr) auto;align-items:center;gap:14px;margin-top:18px;color:#8493a7;font-size:13px}.progress-row :deep(.el-progress__text){display:none}.progress-row :deep(.el-progress){width:100%}.project-card-foot{display:flex;align-items:center;gap:10px;margin-top:14px;color:#8493a7;font-size:13px}.project-actions{display:flex;margin-left:auto;gap:8px}.project-actions :deep(.el-button){margin:0}.project-pagination{display:flex;align-items:center;justify-content:space-between;gap:16px;padding-top:18px}.project-pagination>span{color:#7e8a98;font-size:12px}.cost-form{margin-top:18px}.cost-form :deep(.el-form-item){margin-bottom:20px}.form-help{margin-top:6px;color:#8490a0;font-size:12px;line-height:1.5}.cost-preview{display:grid;gap:5px;margin:-4px 0 18px 126px;padding:13px 15px;border:1px solid #cfe3df;border-radius:9px;background:#f0f8f6}.cost-preview span,.cost-preview small{color:#71828c;font-size:12px}.cost-preview b{color:#174f4f;font-size:15px}.cost-preview b:not(:first-of-type){margin-top:7px}
.owner-load-panel .section-title>div{display:flex;align-items:baseline;gap:9px}.owner-load-panel .section-title>div>span{color:#8493a7;font-size:13px}.owner-load-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.owner-load-card{min-width:0;padding:17px;border:1px solid #dfe6ef;border-radius:13px;background:#fbfcfd}.owner-load-head{display:grid;grid-template-columns:38px minmax(0,1fr) auto;align-items:center;gap:11px}.owner-avatar{display:grid;width:38px;height:38px;place-items:center;border-radius:11px;background:#e8f2ff;color:#3576bd;font-weight:700}.owner-load-head>div{display:flex;min-width:0;flex-direction:column;gap:3px}.owner-load-head>div>b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.owner-load-head small{color:#8a97a6;font-size:11px;font-weight:400}.owner-load-head>strong{color:#1f344b;font-size:26px}.owner-load-head>strong small{margin-left:2px}.owner-eligibility{display:flex;align-items:center;justify-content:space-between;margin-top:14px;padding:8px 10px;border-radius:8px;font-size:12px}.owner-eligibility.is-eligible{background:#eaf8f1;color:#21825f}.owner-eligibility.is-pending{background:#f1f4f7;color:#718096}.owner-project-preview{display:grid;gap:6px;margin-top:11px}.owner-project-preview button{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:7px 9px;border:0;border-radius:7px;background:#fff;color:#465568;text-align:left;cursor:pointer}.owner-project-preview button:hover{background:#edf4fc;color:#2f72bb}.owner-project-preview button>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.owner-project-preview small{flex:none;color:#95a0ac}.owner-load-more{margin-top:8px}.owner-dialog-summary{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:15px;padding:12px 14px;border-radius:9px;background:#f5f8fa;color:#64748b}.owner-dialog-summary b{color:#1f344b;font-size:18px}.owner-project-no{display:block;margin-top:3px;color:#95a0ac}
.latest-progress-report{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:12px;padding:11px 12px;border-radius:9px;background:#f0f8f6}.latest-progress-report>div{display:flex;min-width:0;flex-direction:column;gap:4px}.latest-progress-report span,.latest-progress-empty{color:#7c8a96;font-size:12px}.latest-progress-report b{overflow:hidden;color:#40545d;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.latest-progress-report .el-button{flex:none}.latest-progress-empty{margin-top:12px;padding:10px 12px;border-radius:8px;background:#f5f7f9}.evidence-dialog-summary{display:flex;align-items:center;gap:10px;margin-bottom:16px;color:#7a8794;font-size:13px}.evidence-dialog-summary span+span:before{margin-right:10px;color:#c3cbd3;content:'·'}.evidence-preview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.evidence-preview-item{min-width:0;padding:10px;border:1px solid #e0e7ec;border-radius:10px;background:#f7f9fa}.evidence-preview-item>.el-image,.evidence-preview-item>video{display:block;width:100%;height:300px;border-radius:7px;background:#eef1f3}.evidence-preview-item>small{display:block;margin-top:8px;overflow:hidden;color:#75818d;text-overflow:ellipsis;white-space:nowrap}.evidence-file-card{display:flex;min-height:150px;align-items:center;justify-content:center;flex-direction:column;gap:12px;padding:20px;text-align:center}.evidence-file-card>.el-icon{color:#7e8c98;font-size:38px}.evidence-file-card>span{max-width:100%;overflow-wrap:anywhere;color:#4d5965}
@media(max-width:1100px){.alert-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.owner-load-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.project-card-foot{align-items:flex-start;flex-wrap:wrap}.project-actions{width:100%;margin-left:0}}
@media(max-width:860px){.finance-grid,.project-grid{grid-template-columns:1fr}.section-title--between{align-items:flex-start}.panel-actions{align-items:flex-end;flex-direction:column}}
@media(max-width:760px){.business-page{padding:14px}.hero{align-items:flex-start;flex-direction:column;gap:20px;min-height:0;padding:24px}.hero-actions{width:100%}.hero-actions :deep(.el-button){flex:1;margin:0}.panel{padding:18px 14px}.decision-row{align-items:flex-start;flex-wrap:wrap;padding:16px 14px}.decision-copy{width:calc(100% - 26px)}.decision-actions{width:100%;padding-left:26px;justify-content:flex-start}.decision-actions :deep(.el-button){flex:1}.personnel-item{grid-template-columns:1fr auto;gap:4px 10px}.personnel-item>span{grid-column:1/2;white-space:normal}.personnel-item :deep(.el-button){grid-column:2;grid-row:1/3}.panel-actions{align-items:flex-end}.alert-grid,.owner-load-grid{grid-template-columns:1fr}.owner-load-panel .section-title>div{align-items:flex-start;flex-direction:column}.owner-dialog-summary{align-items:flex-start;flex-direction:column}.project-card{padding:16px 14px}.project-card-foot{align-items:flex-start}.project-actions{display:grid;grid-template-columns:1fr 1fr}.project-actions :deep(.el-button){width:100%}.project-pagination{align-items:flex-end;flex-direction:column}.cost-preview{margin-left:0}.latest-progress-report{align-items:flex-start;flex-direction:column}.evidence-dialog-summary{align-items:flex-start;flex-direction:column;gap:4px}.evidence-dialog-summary span+span:before{content:none}.evidence-preview-grid{grid-template-columns:1fr}.evidence-preview-item>.el-image,.evidence-preview-item>video{height:240px}:global(.el-dialog .cost-form .el-form-item){display:block}:global(.el-dialog .cost-form .el-form-item__label){width:auto!important;height:auto;margin-bottom:6px;padding:0}:global(.el-dialog .cost-form .el-form-item__content){margin-left:0!important}}
</style>
<style scoped>
.evidence-text{margin:0 0 16px;padding:12px 14px;border-radius:8px;background:#f5f8fa;color:#405166;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}
</style>
