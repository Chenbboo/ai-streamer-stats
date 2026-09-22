<template>
  <div class="app-container kpi-page" v-loading="loading">
    <header class="kpi-hero">
      <div><span>{{ resultsOnly ? 'PROJECT KPI RESULTS' : 'PROJECT KPI SETTINGS' }}</span><h1>{{ resultsOnly ? $tr("项目 KPI 结果") : $tr("项目 KPI 设置") }}</h1><p>{{ resultsOnly ? $tr("填报项目实际结果，查看并确认各期指标。") : $tr("设置项目指标、目标值和权重，发布方案时统一设置考核时间。") }}</p></div>
      <div class="hero-tools">
        <el-select v-model="selectedProjectId" filterable :placeholder="$tr(&quot;选择项目&quot;)" @change="switchProject">
          <el-option v-for="project in projects" :key="project.projectId" :label="`${project.projectName} · ${project.mainOwnerName}`" :value="project.projectId" />
        </el-select>
        <el-button v-if="resultsOnly && selectedProjectId" :disabled="loading" @click="openSettings">{{ $tr("返回 KPI 设置") }}</el-button>
        <el-button icon="Refresh" @click="loadWorkspace(selectedProjectId, selectedPlan?.planId)">{{ $tr("刷新") }}</el-button>
      </div>
    </header>

    <el-empty v-if="!selectedProjectId && !loading" :description="$tr(&quot;当前没有可管理的项目&quot;)" />

    <template v-if="workspace.project">
      <section class="summary-grid" :class="{ 'settings-summary': !resultsOnly }">
        <article><span>{{ $tr("项目") }}</span><b>{{ workspace.project.projectName }}</b><small>{{ $tr("{0}负责", [workspace.project.mainOwnerName]) }}</small></article>
        <article><span>{{ $tr("当前KPI") }}</span><b>{{ $tr("{0} 项", [currentTargets.length]) }}</b><small>{{ $tr("权重合计 {0}%", [weightTotal]) }}</small></article>
        <article><span>{{ $tr("考核方案") }}</span><b>{{ selectedPlan ? `v${selectedPlan.planVersion}` : $tr("未发布") }}</b><small>{{ selectedPlan ? $tr("{0} · {1} 至 {2}", [cycleLabel[selectedPlan.cycleType], selectedPlan.cycleStart, selectedPlan.cycleEnd]) : $tr("设置指标后发布考核方案") }}</small></article>
        <article v-if="resultsOnly"><span>{{ $tr("结算状态") }}</span><b>{{ settlementLabel[settlement?.status] || $tr("未开始") }}</b><small>{{ settlement?.reviewComment || $tr("项目级结算，不涉及个人奖金") }}</small></article>
        <article v-if="resultsOnly"><span>{{ $tr("综合得分") }}</span><b>{{ settlement?.totalScore ?? '—' }}</b><small>{{ isLegacyPlan ? $tr("历史奖金联动方案") : $tr("奖金激励单独核准") }}</small></article>
      </section>

      <div class="content-grid">
        <main>
          <el-card v-if="!resultsOnly" shadow="never" class="section-card">
            <div class="section-head">
              <div><h2>{{ $tr("项目KPI目标") }}</h2><p>{{ $tr("只考核项目。调整目标会生成新版本，已发布方案继续使用原快照。") }}</p></div>
              <el-button v-if="canEditPlan" v-hasPermi="['business:kpi:manage']" type="primary" @click="openTarget()">{{ $tr("新增KPI") }}</el-button>
            </div>
            <el-alert v-if="currentTargets.length && Number(weightTotal)!==100" :title="$tr(&quot;当前权重合计 {0}%，必须调整为100%后才能发布方案。&quot;, [weightTotal])" type="warning" :closable="false" show-icon />
            <el-table :data="currentTargets" :empty-text="$tr(&quot;项目负责人尚未设置项目KPI&quot;)">
              <el-table-column :label="$tr(&quot;指标&quot;)" min-width="180"><template #default="{row}"><b>{{ row.kpiName }}</b><small>{{ row.kpiCode }} · v{{ row.targetVersion }}</small></template></el-table-column>
              <el-table-column :label="$tr(&quot;目标&quot;)" min-width="125"><template #default="{row}">{{ row.targetValue }} {{ $tr(row.unit) || '' }}</template></el-table-column>
              <el-table-column :label="$tr(&quot;方向&quot;)" width="95"><template #default="{row}">{{ directionLabel[row.direction] }}</template></el-table-column>
              <el-table-column :label="$tr(&quot;数据来源&quot;)" min-width="150"><template #default="{row}"><el-tag :type="row.sourceType==='MANUAL'?'info':'success'">{{ sourceTypeLabel[row.sourceType] || row.sourceType }}</el-tag><small v-if="row.sourceRefId">{{ sourceReferenceLabel(row) }}</small></template></el-table-column>
              <el-table-column :label="$tr(&quot;权重&quot;)" width="85"><template #default="{row}">{{ row.weight }}%</template></el-table-column>
              <el-table-column v-if="canEditPlan" :label="$tr(&quot;操作&quot;)" width="120"><template #default="{row}"><el-button link @click="openTarget(row)">{{ $tr("调整") }}</el-button><el-button link type="danger" @click="retireTarget(row)">{{ $tr("停用") }}</el-button></template></el-table-column>
            </el-table>
          </el-card>

          <el-card v-if="!resultsOnly && selectedPlan" shadow="never" class="section-card">
            <div class="section-head"><div><h2>{{ $tr("方案指标快照 · v{0}", [selectedPlan.planVersion]) }}</h2><p>{{ $tr("{0} · {1} 至 {2}。发布时的指标设置保留如下。", [cycleLabel[selectedPlan.cycleType], selectedPlan.cycleStart, selectedPlan.cycleEnd]) }}</p></div></div>
            <el-table :data="selectedPlan.items || []" :empty-text="$tr(&quot;暂无指标快照&quot;)">
              <el-table-column prop="kpiName" :label="$tr(&quot;指标&quot;)" min-width="180" />
              <el-table-column :label="$tr(&quot;目标&quot;)" min-width="125"><template #default="{row}">{{ row.targetValue }} {{ $tr(row.unit) || '' }}</template></el-table-column>
              <el-table-column :label="$tr(&quot;方向&quot;)" width="95"><template #default="{row}">{{ directionLabel[row.direction] }}</template></el-table-column>
              <el-table-column :label="$tr(&quot;数据来源&quot;)" min-width="150"><template #default="{row}">{{ sourceTypeLabel[row.sourceType] || row.sourceType }}</template></el-table-column>
              <el-table-column :label="$tr(&quot;权重&quot;)" width="85"><template #default="{row}">{{ row.weight }}%</template></el-table-column>
            </el-table>
            <p v-if="selectedPlan.remark" class="plan-remark">{{ $tr("方案说明：{0}", [selectedPlan.remark]) }}</p>
          </el-card>

          <el-card v-if="resultsOnly" shadow="never" class="section-card">
            <div class="section-head">
              <div><h2>{{ $tr("项目结果与结算") }}</h2><p>{{ $tr("自动指标持续读取系统数据，手工指标由负责人填报；全部指标提前达标可立即确认，否则在周期结束后确认。") }}</p></div>
              <div class="section-actions" v-if="settlement && workspace.canSettle && ['DRAFT','RETURNED'].includes(settlement.status)">
                <el-button v-if="manualItems.length" v-hasPermi="['business:kpi:settle']" :loading="saving" @click="saveResults">{{ $tr("保存手工草稿") }}</el-button>
                <el-button v-hasPermi="['business:kpi:settle']" type="primary" :loading="saving" :disabled="!canFinishCycle || !canConfirmResults" @click="submitResults">{{ needsLegacyReview ? $tr("重新提交审核") : $tr("确认指标结果") }}</el-button>
              </div>
            </div>
            <el-alert v-if="costPending" :title="$tr(&quot;项目仍有投入待计价，人员成本及利润指标显示待完善。请先完成全项目成本计价，再确认结果；其他手工结果可先保存。&quot;)" type="warning" :closable="false" show-icon />
            <el-alert v-if="settlement?.status==='RETURNED'" :title="$tr(&quot;老板退回：{0}&quot;, [settlement.reviewComment])" type="warning" :closable="false" show-icon />
            <el-alert v-if="settlement && !periodEnded && allTargetsMet && ['DRAFT','RETURNED'].includes(settlement.status)" :title="$tr(&quot;全部KPI已达标，可立即确认结果；确认后今天作为本期实际结束日。&quot;)" type="success" :closable="false" show-icon />
            <el-alert v-else-if="settlement && !periodEnded && ['DRAFT','RETURNED'].includes(settlement.status)" :title="$tr(&quot;KPI尚未全部达标；若不提前达标，可在 {0} 周期结束后确认。&quot;, [settlement.periodEnd])" type="info" :closable="false" show-icon />
            <el-empty v-if="!selectedPlan" :description="$tr(&quot;尚未发布KPI方案&quot;)" />
            <div v-else class="result-list">
              <article v-for="item in selectedPlan.items || []" :key="item.itemId" class="result-row">
                <div class="result-target"><b>{{ item.kpiName }}</b><span>{{ $tr("目标 {0} {1} · 权重 {2}% · {3}", [item.targetValue, $tr(item.unit) || '', item.weight, directionLabel[item.direction]]) }}</span><el-tag size="small" :type="isAutomatic(item)?'success':'info'">{{ sourceTypeLabel[item.sourceType] || item.sourceType }}</el-tag></div>
                <template v-if="workspace.canSettle && ['DRAFT','RETURNED'].includes(settlement?.status) && !isAutomatic(item)">
                  <div class="manual-result-input">
                    <el-input-number v-model="resultDraft[item.itemId].actualValue" :min="0" :precision="manualAmountInYuan(item)?2:4" controls-position="right" :placeholder="manualAmountInYuan(item)?$tr(&quot;实际金额（元）&quot;):$tr(&quot;实际值&quot;)" />
                    <small v-if="manualAmountInYuan(item)">{{ $tr("输入元；折合 {0} 万元", [actualFor(item) ?? '—']) }}</small>
                    <small v-else-if="item.unit">{{ $tr("输入单位：{0}", [$tr(item.unit)]) }}</small>
                  </div>
                  <el-input v-model="resultDraft[item.itemId].resultNote" maxlength="1000" show-word-limit :placeholder="$tr(&quot;填写数据来源、结果说明或异常原因&quot;)" />
                  <business-file-upload v-model="resultDraft[item.itemId].attachmentUrls" :project-id="selectedProjectId" />
                </template>
                <template v-else>
                  <div class="result-value"><strong>{{ resultFor(item.itemId)?.actualValue ?? '—' }} {{ $tr(item.unit) || '' }}</strong><span>{{ $tr("完成率 {0}% · 加权 {1}", [resultFor(item.itemId)?.completionRate ?? '—', resultFor(item.itemId)?.weightedScore ?? '—']) }}</span></div>
                  <p>{{ resultFor(item.itemId)?.resultNote || $tr("尚未填报") }}</p>
                  <el-button v-if="settlement?.status==='CONFIRMED' && !isLegacyPlan && workspace.canSettle && item.sourceType==='MANUAL'" v-hasPermi="['business:kpi:settle']" link type="primary" @click="openCorrection(item)">{{ $tr("更正已确认结果") }}</el-button>
                  <div v-if="resultFor(item.itemId)?.attachmentUrls" class="result-attachments">
                    <span>{{ $tr("结果附件") }}</span>
                    <business-file-upload :model-value="resultFor(item.itemId).attachmentUrls" :project-id="selectedProjectId" disabled :drag="false" :is-show-tip="false" />
                  </div>
                </template>
              </article>
            </div>
            <div v-if="settlement?.status==='SUBMITTED' && workspace.canReview" class="review-bar">
              <div><b>{{ $tr("升级前结算等待处理") }}</b><span>{{ $tr("这是旧流程遗留记录，确认后 ¥{0} 将计入项目成本。", [money(settlement.bonusAmount)]) }}</span></div>
              <el-button type="warning" plain :loading="saving" @click="returnSettlement">{{ $tr("退回修改") }}</el-button>
              <el-button type="success" :loading="saving" @click="confirmSettlement">{{ $tr("确认并计入成本") }}</el-button>
            </div>
            <el-result v-if="settlement?.status==='CONFIRMED'" icon="success" :title="isLegacyPlan ? $tr(&quot;历史 KPI 与奖金已确认&quot;) : $tr(&quot;项目指标已确认&quot;)" :sub-title="confirmedSummary" />
          </el-card>
        </main>

        <aside>
          <el-card v-if="!resultsOnly" shadow="never" class="section-card result-entry">
            <div class="section-head"><div><h2>{{ $tr("KPI 结果填报") }}</h2><p>{{ $tr("考核完成后，在这里填写实际完成值和结果说明，再确认本期结果。") }}</p></div></div>
            <template v-if="selectedPlan">
              <p class="result-entry-period">{{ $tr("当前选择：v{0} · {1} 至 {2}", [selectedPlan.planVersion, selectedPlan.cycleStart, selectedPlan.cycleEnd]) }}</p>
              <el-button v-hasPermi="['business:kpi:list']" type="primary" :disabled="loading" @click="openResults">{{ workspace.canSettle && ['DRAFT','RETURNED'].includes(settlement?.status) && selectedPlan.status!=='VOIDED' ? $tr("填报 KPI 结果") : $tr("查看 KPI 结果") }}</el-button>
              <small>{{ $tr("下方选择其他历史方案，可查看或办理对应期的结果。") }}</small>
            </template>
            <el-empty v-else :image-size="45" :description="$tr(&quot;先发布考核方案，再填报对应结果&quot;)" />
          </el-card>
          <el-card v-if="!resultsOnly" shadow="never" class="section-card">
            <div class="section-head"><div><h2>{{ $tr("指标方案") }}</h2><p>{{ $tr("将当前指标、目标和权重发布为指定考核周期的方案。") }}</p></div><el-button v-if="canEditPlan" v-hasPermi="['business:kpi:manage']" type="primary" :disabled="Number(weightTotal)!==100 || !currentTargets.length" @click="openPlan">{{ $tr("发布新方案") }}</el-button></div>
          </el-card>
          <el-card v-if="resultsOnly && isLegacyPlan" shadow="never" class="section-card">
            <h3>{{ $tr("历史奖金阶梯") }}</h3><p>{{ $tr("保留旧方案规则与历史金额；不代表已分配或已支付。") }}</p><div v-for="tier in selectedPlan?.tiers || []" :key="tier.tierId" class="tier-row"><span><b>{{ tier.tierName }}</b><small>{{ scoreRange(tier) }}</small></span><strong>¥{{ money(tier.bonusAmount) }}</strong></div>
          </el-card>

          <el-card shadow="never" class="section-card">
            <div class="section-head"><div><h2>{{ $tr("方案历史") }}</h2><p>{{ resultsOnly ? $tr("选择方案查看对应结果。") : $tr("选择方案查看发布时的指标、目标和权重。") }}</p></div></div>
            <div v-if="!workspace.plans?.length" class="empty-text">{{ $tr("暂无历史方案") }}</div>
            <div v-for="plan in workspace.plans || []" :key="plan.planId" role="button" tabindex="0" class="plan-row" :class="{active:Number(plan.planId)===Number(selectedPlan?.planId)}" @click="loadWorkspace(selectedProjectId,plan.planId)" @keyup.enter="loadWorkspace(selectedProjectId,plan.planId)">
              <span><b>v{{ plan.planVersion }} · {{ cycleLabel[plan.cycleType] }}</b><small>{{ $tr("{0} 至 {1}", [plan.cycleStart, plan.cycleEnd]) }}</small></span>
              <span class="plan-row-actions"><el-tag v-if="resultsOnly" :type="settlementTone[plan.settlementStatus]">{{ settlementLabel[plan.settlementStatus] }}</el-tag><el-tag v-else type="info">{{ planStatusLabel[plan.status] || plan.status }}</el-tag><el-button v-if="!resultsOnly && workspace.canVoid && ['DRAFT','RETURNED'].includes(plan.settlementStatus)" v-hasPermi="['business:kpi:manage']" link type="danger" @click.stop="voidPlan(plan)">{{ $tr("作废") }}</el-button></span>
            </div>
          </el-card>
        </aside>
      </div>
    </template>

    <el-dialog v-model="targetDialog" :title="targetForm.kpiId ? $tr(&quot;调整项目KPI目标&quot;) : $tr(&quot;新增项目KPI&quot;)" width="min(680px,94vw)" append-to-body>
      <el-alert :title="$tr(&quot;考核周期和起止时间在发布方案时统一设置；已发布方案继续使用原目标快照。&quot;)" type="info" :closable="false" show-icon />
      <el-form :model="targetForm" label-width="112px" class="dialog-form">
        <el-form-item v-if="!targetForm.kpiId && proposalTargetOptions.length" :label="$tr(&quot;引用立项目标&quot;)">
          <el-select v-model="selectedProposalTarget" filterable :placeholder="$tr(&quot;选择后自动带入，可继续调整&quot;)" style="width:100%" @change="useProposalTarget">
            <el-option v-for="item in proposalTargetOptions" :key="item.key" :label="item.label" :value="item.key" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;系统编码&quot;)"><el-input v-model="targetForm.kpiCode" disabled :placeholder="$tr(&quot;保存后自动生成&quot;)" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;指标名称&quot;)" required><el-input v-model="targetForm.kpiName" /></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;指标类型&quot;)"><el-select v-model="targetForm.metricType" style="width:100%"><el-option v-for="(label,key) in metricTypeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;目标值&quot;)" required><el-input-number v-model="targetForm.targetValue" :min="0.0001" :precision="4" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;单位&quot;)"><el-select :model-value="targetForm.unit" @update:model-value="changeTargetUnit" filterable allow-create default-first-option clearable :placeholder="$tr(&quot;选择或输入单位&quot;)" style="width:100%"><el-option v-for="unit in commonKpiUnits" :key="unit" :label="$tr(unit)" :value="unit" /></el-select><small v-if="targetForm.unit==='万元'" class="field-help">{{ $tr("目标按万元设置；手工结果输入元，系统会换算为万元。") }}</small></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;权重%&quot;)" required><el-input-number v-model="targetForm.weight" :min="0" :max="100" :precision="2" style="width:100%" /><small class="field-help">{{ $tr("其他KPI已占 {0}%，录入后合计 {1}%，本项最多可填 {2}%", [formatWeight(otherKpiWeightTotal), formatWeight(pendingKpiWeightTotal), formatWeight(maxKpiWeight)]) }}</small></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;考核方向&quot;)"><el-select v-model="targetForm.direction" style="width:100%"><el-option :label="$tr(&quot;越高越好&quot;)" value="HIGHER_BETTER"/><el-option :label="$tr(&quot;越低越好&quot;)" value="LOWER_BETTER"/></el-select></el-form-item></el-col></el-row>
        <el-alert v-if="pendingKpiWeightTotal>100" :title="$tr(&quot;当前KPI权重合计为 {0}%，超过100%，请将本项权重调整到 {1}% 以内。&quot;, [formatWeight(pendingKpiWeightTotal), formatWeight(maxKpiWeight)])" type="error" :closable="false" show-icon />
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;数据来源&quot;)" required><el-select v-model="targetForm.sourceType" style="width:100%" @change="changeSourceType"><el-option v-for="(label,key) in sourceTypeLabel" :key="key" :label="label" :value="key" /></el-select></el-form-item></el-col><el-col v-if="sourceNeedsReference" :sm="12" :xs="24"><el-form-item :label="$tr(&quot;绑定数据&quot;)" :required="targetForm.sourceType==='ROUTINE'"><el-select v-model="targetForm.sourceRefId" @change="changeSourceReference" clearable filterable style="width:100%" :placeholder="targetForm.sourceType==='ROUTINE'?$tr(&quot;请选择持续工作&quot;):$tr(&quot;不选则统计全部&quot;)"><el-option v-for="option in sourceReferenceOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item></el-col></el-row>
        <el-alert v-if="targetForm.sourceType!=='MANUAL'" :title="sourceHelpText" type="success" :closable="false" show-icon />
        <el-row :gutter="12"><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;最低值&quot;)"><el-input-number v-model="targetForm.minimumValue" :precision="4" style="width:100%" /></el-form-item></el-col><el-col :sm="12" :xs="24"><el-form-item :label="$tr(&quot;挑战值&quot;)"><el-input-number v-model="targetForm.challengeValue" :precision="4" style="width:100%" /></el-form-item></el-col></el-row>
        <el-form-item :label="$tr(&quot;调整说明&quot;)"><el-input v-model="targetForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="targetDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" :disabled="pendingKpiWeightTotal>100" @click="saveTarget">{{ $tr("保存KPI版本") }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="planDialog" :title="$tr(&quot;发布项目指标方案&quot;)" width="min(900px,96vw)" append-to-body>
      <el-alert :title="$tr(&quot;发布后保存当前指标、目标和权重快照，后续调整仅用于新方案。&quot;)" type="info" :closable="false" show-icon />
      <el-form :model="planForm" label-width="92px" class="dialog-form">
        <el-row :gutter="12">
          <el-col :sm="10" :xs="24"><el-form-item :label="$tr(&quot;考核周期&quot;)" required><el-select v-model="planForm.cycleType" style="width:100%" @change="resetPlanDates"><el-option :label="$tr(&quot;月度&quot;)" value="MONTH"/><el-option :label="$tr(&quot;季度&quot;)" value="QUARTER"/><el-option :label="$tr(&quot;项目周期（需要结束日期）&quot;)" value="PROJECT" :disabled="!projectPeriodReady"/></el-select><small v-if="!workspace.project?.planEndDate" class="field-help">{{ $tr("不限期项目请按月度或季度发布 KPI 方案。") }}</small></el-form-item></el-col>
          <el-col :sm="14" :xs="24">
            <el-form-item v-if="planForm.cycleType==='PROJECT'" :label="$tr(&quot;项目日期&quot;)">
              <span>{{ $tr("{0} 至 {1}", [workspace.project?.planStartDate || $tr("未设置开始日期"), workspace.project?.planEndDate || $tr("未设置结束日期")]) }}</span>
            </el-form-item>
            <el-form-item v-else :label="$tr(&quot;起止日期&quot;)" required><el-date-picker v-model="planDates" type="daterange" value-format="YYYY-MM-DD" :start-placeholder="$tr(&quot;开始日期&quot;)" :end-placeholder="$tr(&quot;结束日期&quot;)" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-alert v-if="planForm.cycleType==='PROJECT'" :title="projectPeriodReady ? $tr(&quot;自动沿用项目计划起止日期。&quot;) : $tr(&quot;请先在项目中完善有效的计划起止日期，再发布项目周期方案。&quot;)" :type="projectPeriodReady ? 'info' : 'warning'" :closable="false" show-icon />
        <el-form-item :label="$tr(&quot;方案说明&quot;)"><el-input v-model="planForm.remark" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="planDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" :disabled="planForm.cycleType==='PROJECT' && !projectPeriodReady" @click="publishPlan">{{ $tr("确认发布") }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="correctionDialog" :title="$tr(&quot;更正已确认 KPI 结果&quot;)" width="min(520px,94vw)" append-to-body>
      <el-alert :title="$tr(&quot;更正会重算本期综合得分并记录原因；已确认的考核结束日期不会自动改变。&quot;)" type="warning" :closable="false" show-icon />
      <el-form label-width="96px" class="dialog-form">
        <el-form-item :label="$tr(&quot;指标&quot;)">{{ correctionForm.item?.kpiName }}</el-form-item>
        <el-form-item :label="$tr(&quot;当前结果&quot;)">{{ correctionForm.original }} {{ $tr(correctionForm.item?.unit) || '' }}</el-form-item>
        <el-form-item :label="manualAmountInYuan(correctionForm.item)?$tr(&quot;正确金额（元）&quot;):$tr(&quot;正确实际值&quot;)" required>
          <el-input-number v-model="correctionForm.actualInput" :min="0" :precision="manualAmountInYuan(correctionForm.item)?2:8" style="width:100%" />
          <small v-if="manualAmountInYuan(correctionForm.item)" class="field-help">{{ $tr("折合 {0} 万元", [correctionForm.actualInput==null?'—':Number((Number(correctionForm.actualInput)/10000).toFixed(8))]) }}</small>
        </el-form-item>
        <el-form-item :label="$tr(&quot;更正原因&quot;)" required><el-input v-model="correctionForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit :placeholder="$tr(&quot;说明原数据为什么有误&quot;)" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="correctionDialog=false">{{ $tr("取消") }}</el-button><el-button type="primary" :loading="saving" @click="submitCorrection">{{ $tr("确认更正") }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="BusinessProjectKpiBonus">
import { translateText } from '@/locales/translate'

import { ElMessage, ElMessageBox } from 'element-plus'
import { retireBusinessProjectKpi, saveBusinessProjectKpi } from '@/api/business/project'
import { correctProjectKpiResult, getProjectKpiOverview, getProjectKpiWorkspace, publishProjectKpiPlan, reviewProjectKpiSettlement, saveProjectKpiResults, submitProjectKpiSettlement, voidProjectKpiPlan } from '@/api/business/kpi'
import { useBusinessRefreshOnReactivated } from '@/utils/businessRefresh'
import { isDeliveryEnded, projectAccountingState } from '@/utils/businessProjectState'

const props=defineProps({resultsOnly:{type:Boolean,default:false}})

const route=useRoute(),router=useRouter()
const loading=ref(false),saving=ref(false),projects=ref([]),projectsLoaded=ref(false),selectedProjectId=ref(null),workspace=reactive({}),targetDialog=ref(false),targetForm=reactive({}),planDialog=ref(false),planForm=reactive({}),planDates=ref([]),resultDraft=reactive({}),correctionDialog=ref(false),correctionForm=reactive({item:null,original:null,actualInput:null,reason:''})
const workspacePaths=props.resultsOnly?['/projects/kpi-results']:['/business/kpi-bonus','/projects/kpi']
let workspaceRequestId=0
const selectedPlan=computed(()=>workspace.selectedPlan||null),settlement=computed(()=>selectedPlan.value?.settlement||null),currentTargets=computed(()=>workspace.currentTargets||[])
function openResults(){if(!selectedProjectId.value||!selectedPlan.value)return;router.push({path:'/projects/kpi-results',query:{projectId:selectedProjectId.value,planId:selectedPlan.value.planId}})}
function openSettings(){router.push({path:'/business/kpi-bonus',query:{projectId:selectedProjectId.value,...(selectedPlan.value?{planId:selectedPlan.value.planId}:{})}})}
const isLegacyPlan=computed(()=>!!selectedPlan.value && selectedPlan.value.rewardPolicyVersion!=='INDEPENDENT_V1')
const costPending=computed(()=>settlement.value?.dataStatus==='PENDING_COST'||(settlement.value?.results||[]).some(r=>r.dataStatus==='PENDING_COST'))
const canConfirmResults=computed(()=>workspace.canConfirm!==false && !costPending.value)
const needsLegacyReview=computed(()=>isLegacyPlan.value && settlement.value?.status==='RETURNED' && !!settlement.value?.reviewedUserId)
const confirmedSummary=computed(()=>isLegacyPlan.value?translateText("综合得分 {0}，历史奖金 ¥{1}。{2}；未记录个人分配或支付。", [settlement.value?.totalScore, money(settlement.value?.bonusAmount), settlement.value?.accountingFactId?translateText("已关联项目成本，可在核算系统查看当前成本状态"):translateText("本周期未生成奖金成本")]):translateText("综合得分 {0}。需要奖励时，请前往人力资源系统的奖金激励模块。", [settlement.value?.totalScore]))
const canEditPlan=computed(()=>!props.resultsOnly&&workspace.canManage&&!isDeliveryEnded(workspace.project)&&projectAccountingState(workspace.project)==='OPEN')
const weightTotal=computed(()=>currentTargets.value.reduce((sum,item)=>sum+Number(item.weight||0),0).toFixed(2).replace(/\.00$/,''))
const otherKpiWeightTotal=computed(()=>currentTargets.value.filter(item=>targetForm.kpiId==null||String(item.kpiId)!==String(targetForm.kpiId)).reduce((sum,item)=>sum+Number(item.weight||0),0))
const maxKpiWeight=computed(()=>Math.max(0,Number((100-otherKpiWeightTotal.value).toFixed(2))))
const pendingKpiWeightTotal=computed(()=>Number((otherKpiWeightTotal.value+Number(targetForm.weight||0)).toFixed(2)))
const periodEnded=computed(()=>!settlement.value?.periodEnd||settlement.value.periodEnd<today())
const automaticSourceTypes=['REVENUE','BUSINESS_COST','PERSONNEL_COST','PROFIT','ROUTINE','TASK','MILESTONE']
const manualItems=computed(()=>(selectedPlan.value?.items||[]).filter(item=>!isAutomatic(item)))
const cycleLabel={MONTH:translateText("月度"),QUARTER:translateText("季度"),PROJECT:translateText("项目周期")}
const directionLabel={HIGHER_BETTER:translateText("越高越好"),LOWER_BETTER:translateText("越低越好")}
const metricTypeLabel={COUNT:translateText("数量"),AMOUNT:translateText("金额"),PERCENT:translateText("百分比"),DURATION:translateText("时长"),SCORE:translateText("评分"),MILESTONE:translateText("里程碑")}
const commonKpiUnits=computed(()=>['个','件','项','次','单','条','人','户',...(workspace.project?.baseCurrency==='CNY'?['元','万元']:[workspace.project?.baseCurrency||'CNY']),'%','分','小时','天'])
const sourceTypeLabel={MANUAL:translateText("负责人手工填报"),REVENUE:translateText("确认收入（自动）"),BUSINESS_COST:translateText("业务成本（自动）"),PERSONNEL_COST:translateText("人员成本（自动）"),PROFIT:translateText("经营结果（自动）"),ROUTINE:translateText("持续工作上报（自动）"),TASK:translateText("完成任务数（自动）"),MILESTONE:translateText("完成里程碑数（自动）")}
const settlementLabel={DRAFT:translateText("填报中"),SUBMITTED:translateText("历史待确认"),RETURNED:translateText("已退回"),CONFIRMED:translateText("已确认")}
const settlementTone={DRAFT:'info',SUBMITTED:'warning',RETURNED:'danger',CONFIRMED:'success'}
const planStatusLabel={PUBLISHED:translateText("已发布"),CLOSED:translateText("已结束"),VOIDED:translateText("已作废")}
const today=()=>localDate(new Date())
const money=value=>value==null?'—':Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
const formatWeight=value=>Number(value||0).toFixed(2).replace(/\.00$/,'').replace(/(\.\d)0$/,'$1')
function resultFor(itemId){return settlement.value?.results?.find(result=>Number(result.planItemId)===Number(itemId))}
function isAutomatic(item){return automaticSourceTypes.includes(item?.sourceType)}
function manualAmountInYuan(item){return !isAutomatic(item)&&item?.unit==='万元'}
function actualFor(item){const value=isAutomatic(item)?resultFor(item.itemId)?.actualValue:resultDraft[item.itemId]?.actualValue;return value==null?null:manualAmountInYuan(item)?Number((Number(value)/10000).toFixed(8)):value}
const allTargetsMet=computed(()=>{const items=selectedPlan.value?.items||[];return !!items.length&&items.every(item=>{const value=actualFor(item);if(value==null)return false;const actual=Number(value),target=Number(item.targetValue);if(!Number.isFinite(actual)||!Number.isFinite(target))return false;return item.direction==='LOWER_BETTER'?actual<=target:actual>=target})})
const canFinishCycle=computed(()=>periodEnded.value||allTargetsMet.value)
const sourceNeedsReference=computed(()=>['ROUTINE','TASK','MILESTONE'].includes(targetForm.sourceType))
const sourceReferenceOptions=computed(()=>{const options=workspace.sourceOptions||{};if(targetForm.sourceType==='ROUTINE')return (options.routines||[]).map(item=>({value:item.routineId,label:`${item.routineName} · ${translateText(item.unit)||''}`}));if(targetForm.sourceType==='TASK')return (options.tasks||[]).map(item=>({value:item.taskId,label:item.taskName}));if(targetForm.sourceType==='MILESTONE')return (options.milestones||[]).map(item=>({value:item.milestoneId,label:item.milestoneName}));return []})
const sourceHelpText=computed(()=>targetForm.sourceType==='ROUTINE'?translateText("考核周期内该持续工作的每日上报实际值将自动求和，例如每天上报视频数量。"):targetForm.sourceType==='TASK'?translateText("按考核周期内完成的任务数量统计；不绑定具体任务时统计项目全部任务。"):targetForm.sourceType==='MILESTONE'?translateText("按考核周期内完成的里程碑数量统计；不绑定时统计项目全部里程碑。"):translateText("从项目已核算的经营结果自动汇总；未确认或尚未核算的数据不会计入。"))
function sourceReferenceLabel(item){const lists=[...(workspace.sourceOptions?.routines||[]),...(workspace.sourceOptions?.tasks||[]),...(workspace.sourceOptions?.milestones||[])],match=lists.find(option=>Number(option.routineId||option.taskId||option.milestoneId)===Number(item.sourceRefId));return match?.routineName||match?.taskName||match?.milestoneName||translateText("记录 #{0}", [item.sourceRefId])}
function changeSourceType(sourceType){targetForm.sourceRefId=null;if(['REVENUE','BUSINESS_COST','PERSONNEL_COST','PROFIT'].includes(sourceType)){targetForm.metricType='AMOUNT';const currency=workspace.project?.baseCurrency||'CNY';const unit=currency==='CNY'?'元':currency;if(targetForm.unit!==unit&&!(currency==='CNY'&&targetForm.unit==='万元'))changeTargetUnit(unit)}else if(['TASK','MILESTONE'].includes(sourceType)){targetForm.metricType='COUNT';changeTargetUnit('项')}}
function changeSourceReference(sourceRefId){if(targetForm.sourceType!=='ROUTINE'||!sourceRefId)return;const routine=(workspace.sourceOptions?.routines||[]).find(row=>Number(row.routineId)===Number(sourceRefId));if(routine?.unit)changeTargetUnit(routine.unit)}
function changeTargetUnit(unit){const cny=workspace.project?.baseCurrency==='CNY';if(cny&&unit==='CNY')unit='元';const previous=targetForm.unit;if(previous===unit)return;const factor=value=>value==='万元'?10000:['元','CNY'].includes(value)?1:null;const oldFactor=cny?factor(previous):null,newFactor=cny?factor(unit):null;if(targetForm.targetValue!=null&&Number(targetForm.targetValue)!==0){const converted=oldFactor&&newFactor?Number(targetForm.targetValue)*oldFactor/newFactor:null;if(converted!=null&&Number.isFinite(converted)&&converted>0&&converted<=99999999999999.99&&Math.abs(converted-Number(converted.toFixed(4)))<1e-9)targetForm.targetValue=Number(converted.toFixed(4));else{targetForm.targetValue=null;ElMessage.info(translateText("单位已变化，请按新单位重新填写目标值"))}}targetForm.unit=unit;if(['元','万元','USD','VND'].includes(unit))targetForm.metricType='AMOUNT'}
function scoreRange(tier){return tier.maxScore===null||tier.maxScore===undefined?translateText("{0}分及以上", [tier.minScore]):translateText("{0} ≤ 得分 < {1}", [tier.minScore, tier.maxScore])}
function hydrateDraft(){Object.keys(resultDraft).forEach(key=>delete resultDraft[key]);for(const item of manualItems.value){const stored=resultFor(item.itemId)||{};resultDraft[item.itemId]={planItemId:item.itemId,actualValue:stored.actualValue==null?null:manualAmountInYuan(item)?Number((Number(stored.actualValue)*10000).toFixed(2)):stored.actualValue,resultNote:stored.resultNote||'',attachmentUrls:stored.attachmentUrls||''}}}
function idKey(value){const scalar=Array.isArray(value)?value[0]:value;return scalar===null||scalar===undefined?'':String(scalar)}
async function loadProjects(){const res=await getProjectKpiOverview();projects.value=res.data||[];const requested=idKey(route.query.projectId),matched=projects.value.find(item=>idKey(item.projectId)===requested);selectedProjectId.value=matched?.projectId??projects.value[0]?.projectId??null;projectsLoaded.value=true;if(!selectedProjectId.value){workspaceRequestId++;Object.keys(workspace).forEach(key=>delete workspace[key])}}
async function loadWorkspace(projectId,planId){if(!idKey(projectId))return;const requestId=++workspaceRequestId;loading.value=true;try{const res=await getProjectKpiWorkspace(projectId,planId);if(requestId!==workspaceRequestId)return;Object.keys(workspace).forEach(key=>delete workspace[key]);Object.assign(workspace,res.data||{});selectedProjectId.value=projectId;hydrateDraft();if(workspacePaths.includes(route.path))await router.replace({query:{...route.query,projectId,planId:workspace.selectedPlan?.planId||undefined}})}catch(error){if(requestId===workspaceRequestId){Object.keys(workspace).forEach(key=>delete workspace[key]);hydrateDraft()}throw error}finally{if(requestId===workspaceRequestId)loading.value=false}}
async function syncWorkspaceFromRoute(){if(!workspacePaths.includes(route.path)||!projectsLoaded.value)return;const requested=idKey(route.query.projectId),matched=projects.value.find(item=>idKey(item.projectId)===requested);if(!matched)return;const requestedPlan=idKey(route.query.planId),currentPlan=idKey(selectedPlan.value?.planId),currentProject=idKey(workspace.project?.projectId);if(currentProject===requested&&(!requestedPlan||requestedPlan===currentPlan))return;await loadWorkspace(matched.projectId,route.query.planId)}
function switchProject(projectId){loadWorkspace(projectId)}
const selectedProposalTarget=ref(null)
const proposalTargetOptions=computed(()=>{
 const items=(workspace.proposalTargets||[]).map((row,index)=>({key:'target-'+index,label:row.targetName,row}))
 if(Number(workspace.proposalEstimatedRevenue)>0)items.unshift({key:'revenue',label:translateText("预计收入"),revenue:true})
 return items
})
function useProposalTarget(key){
 const item=proposalTargetOptions.value.find(item=>item.key===key);if(!item)return
 const row=item.row||{},delivery=row.targetType==='DELIVERY',currency=workspace.project?.baseCurrency||'CNY',rawUnit=item.revenue?currency:row.unit,unit=currency==='CNY'&&rawUnit==='CNY'?'元':rawUnit
 Object.assign(targetForm,{kpiName:item.label,targetValue:item.revenue?Number(workspace.proposalEstimatedRevenue):delivery?1:Number(row.targetValue),unit:unit||'项',metricType:item.revenue?'AMOUNT':delivery?'MILESTONE':unit==='%'?'PERCENT':['元','万元','CNY','USD','VND'].includes(unit)?'AMOUNT':['天','小时','分钟'].includes(unit)?'DURATION':'COUNT',sourceType:item.revenue?'REVENUE':'MANUAL',sourceRefId:null,aggregateType:'SUM',direction:'HIGHER_BETTER',remark:delivery?(translateText("验收通过填1，未通过填0；")+(row.acceptanceEvidence||'')).slice(0,500):row.acceptanceEvidence||''})
}
function openTarget(row={}){selectedProposalTarget.value=null;Object.assign(targetForm,{kpiId:null,projectId:selectedProjectId.value,kpiCode:'',kpiName:'',metricType:'COUNT',targetValue:null,minimumValue:null,warningValue:null,challengeValue:null,unit:'',weight:0,direction:'HIGHER_BETTER',aggregateType:'SUM',sourceType:'MANUAL',sourceRefId:null,remark:'',...row,actualValue:null,ownerUserId:null,ownerName:null});if(workspace.project?.baseCurrency==='CNY'&&targetForm.unit==='CNY')targetForm.unit='元';targetDialog.value=true}
async function saveTarget(){if(!targetForm.kpiName?.trim())return ElMessage.warning(translateText("请填写指标名称"));if(!(Number(targetForm.targetValue)>0))return ElMessage.warning(translateText("KPI目标值必须大于0"));if(!Number.isFinite(Number(targetForm.weight))||Number(targetForm.weight)<0)return ElMessage.warning(translateText("请填写0到100之间的KPI权重"));if(pendingKpiWeightTotal.value>100)return ElMessage.warning(translateText("当前KPI权重合计为 {0}%，不能超过100%；本项最多可填 {1}%", [formatWeight(pendingKpiWeightTotal.value), formatWeight(maxKpiWeight.value)]));if(targetForm.sourceType==='ROUTINE'&&!targetForm.sourceRefId)return ElMessage.warning(translateText("请选择要自动汇总的持续工作"));saving.value=true;try{await saveBusinessProjectKpi(targetForm);targetDialog.value=false;await loadWorkspace(selectedProjectId.value,selectedPlan.value?.planId);ElMessage.success(targetForm.kpiId?translateText("KPI新版本已保存"):translateText("KPI已创建，编码已自动生成"))}finally{saving.value=false}}
async function retireTarget(row){await ElMessageBox.confirm(translateText("确认停用“{0}”吗？已发布方案不会受影响。", [row.kpiName]),translateText("停用KPI"),{type:'warning'});await retireBusinessProjectKpi(selectedProjectId.value,row.kpiId);await loadWorkspace(selectedProjectId.value,selectedPlan.value?.planId);ElMessage.success(translateText("KPI已停用"))}
function monthRange(){const date=new Date(),start=new Date(date.getFullYear(),date.getMonth(),1),end=new Date(date.getFullYear(),date.getMonth()+1,0);return [localDate(start),localDate(end)]}
function quarterRange(){const date=new Date(),month=Math.floor(date.getMonth()/3)*3,start=new Date(date.getFullYear(),month,1),end=new Date(date.getFullYear(),month+3,0);return [localDate(start),localDate(end)]}
function localDate(date){const offset=new Date(date.getTime()-date.getTimezoneOffset()*60000);return offset.toISOString().slice(0,10)}
const projectPlanDates=computed(()=>[workspace.project?.planStartDate,workspace.project?.planEndDate])
const projectPeriodReady=computed(()=>!!projectPlanDates.value[0]&&!!projectPlanDates.value[1]&&projectPlanDates.value[0]<=projectPlanDates.value[1])
function resetPlanDates(){planDates.value=planForm.cycleType==='MONTH'?monthRange():planForm.cycleType==='QUARTER'?quarterRange():[]}
function openPlan(){Object.assign(planForm,{projectId:selectedProjectId.value,cycleType:'MONTH',remark:'',tiers:[]});resetPlanDates();planDialog.value=true}
async function publishPlan(){
  if(planForm.cycleType==='PROJECT'&&!projectPeriodReady.value)return ElMessage.warning(translateText("请先在项目中完善有效的计划起止日期"))
  const dates=planForm.cycleType==='PROJECT'?projectPlanDates.value:planDates.value
  if(!dates?.[0]||!dates?.[1])return ElMessage.warning(translateText("请选择考核起止日期"))
  await ElMessageBox.confirm(translateText("发布后目标和权重将形成不可覆盖的方案快照。确定发布吗？"),translateText("发布指标方案"),{type:'warning'})
  saving.value=true
  try{const res=await publishProjectKpiPlan({...planForm,bonusMode:'NONE',tiers:[],cycleStart:dates[0],cycleEnd:dates[1]});Object.keys(workspace).forEach(key=>delete workspace[key]);Object.assign(workspace,res.data||{});hydrateDraft();planDialog.value=false;ElMessage.success(translateText("项目指标方案已发布"))}finally{saving.value=false}
}
async function voidPlan(row){await ElMessageBox.confirm(translateText("确定作废 v{0} · {1} 吗？作废后该方案不再参与项目考核，已发布的指标快照与历史记录仍会保留。", [row.planVersion, cycleLabel[row.cycleType]]),translateText("作废KPI方案"),{type:'warning',confirmButtonText:translateText("确认作废")});saving.value=true;try{await voidProjectKpiPlan(row.planId);await loadWorkspace(selectedProjectId.value);ElMessage.success(translateText("KPI方案已作废，审计数据已保留"))}finally{saving.value=false}}
function resultPayload(requireAll=false){const rows=manualItems.value.map(item=>({item,...resultDraft[item.itemId]})).filter(row=>row.actualValue!==null&&row.actualValue!==undefined);if(!rows.length){ElMessage.warning(translateText("请至少填写一项手工KPI结果"));return null}for(const row of rows)if(!row.resultNote?.trim()){ElMessage.warning(translateText("每项手工结果都必须填写说明"));return null}if(requireAll&&rows.length!==manualItems.value.length){ElMessage.warning(translateText("请完整填写所有手工KPI结果"));return null}return {results:rows.map(({item,...row})=>({...row,actualValue:manualAmountInYuan(item)?Number((Number(row.actualValue)/10000).toFixed(8)):row.actualValue}))}}
async function saveResults(showMessage=true,requireAll=false){const payload=resultPayload(requireAll);if(!payload)return false;saving.value=true;try{const res=await saveProjectKpiResults(settlement.value.settlementId,payload);selectedPlan.value.settlement=res.data;hydrateDraft();if(showMessage)ElMessage.success(translateText("KPI结果草稿已保存"));return true}finally{saving.value=false}}
async function submitResults(){if(!canFinishCycle.value)return ElMessage.warning(translateText("KPI尚未全部达标，请达标后提前确认，或等待考核周期结束"));if(!canConfirmResults.value)return ElMessage.warning(translateText("人员成本尚未完整计价，请先处理待计价投入"));if(manualItems.value.length&&!await saveResults(false,true))return;if(!canConfirmResults.value)return ElMessage.warning(translateText("人员成本尚未完整计价，请先处理待计价投入"));const confirmText=!periodEnded.value&&allTargetsMet.value?translateText("全部KPI已提前达标。确认后将今天作为本期实际结束日并形成结果快照，确定继续吗？"):needsLegacyReview.value?translateText("按原审核流程重新提交，奖金及成本仍需原审核人确认。确定提交吗？"):isLegacyPlan.value?translateText("历史方案按原规则确认指标、奖金和成本。确定继续吗？"):translateText("按截止日期读取自动指标并形成结果快照。奖金另行核准。确定继续吗？");await ElMessageBox.confirm(confirmText,translateText("确认项目指标"),{type:'warning'});saving.value=true;try{const res=await submitProjectKpiSettlement(settlement.value.settlementId);const status=res.data?.status;selectedPlan.value.settlement=res.data;await loadWorkspace(selectedProjectId.value,selectedPlan.value.planId);ElMessage.success(status==='SUBMITTED'?translateText("已提交原审核流程，尚未核准奖金或入账"):isLegacyPlan.value?translateText("历史项目指标已确认，请查看关联成本状态"):translateText("项目指标已确认"))}finally{saving.value=false}}
async function returnSettlement(){const{value}=await ElMessageBox.prompt(translateText("请填写需要负责人修正的内容"),translateText("退回KPI结算"),{inputValidator:value=>!!value?.trim()||translateText("必须填写退回原因"),type:'warning'});saving.value=true;try{const res=await reviewProjectKpiSettlement(settlement.value.settlementId,{decision:'RETURNED',comment:value});selectedPlan.value.settlement=res.data;hydrateDraft();ElMessage.success(translateText("已退回项目负责人修改"))}finally{saving.value=false}}
async function confirmSettlement(){await ElMessageBox.confirm(translateText("确认综合得分 {0}、项目奖金 ¥{1} 吗？确认后将立即计入 {2} 项目成本。", [settlement.value.totalScore, money(settlement.value.bonusAmount), settlement.value.periodEnd]),translateText("确认项目KPI奖金"),{type:'warning',confirmButtonText:translateText("确认并计入成本")});saving.value=true;try{const res=await reviewProjectKpiSettlement(settlement.value.settlementId,{decision:'CONFIRMED',comment:translateText("确认项目KPI及奖金")});selectedPlan.value.settlement=res.data;await loadWorkspace(selectedProjectId.value,selectedPlan.value.planId);ElMessage.success(translateText("历史项目指标与奖金已确认，请查看关联成本状态"))}finally{saving.value=false}}
function openCorrection(item){Object.assign(correctionForm,{item,original:resultFor(item.itemId)?.actualValue,actualInput:null,reason:''});correctionDialog.value=true}
async function submitCorrection(){const item=correctionForm.item;if(!item||correctionForm.actualInput==null)return ElMessage.warning(translateText("请填写正确实际值"));if(!correctionForm.reason.trim())return ElMessage.warning(translateText("请填写更正原因"));const actualValue=manualAmountInYuan(item)?Number((Number(correctionForm.actualInput)/10000).toFixed(8)):correctionForm.actualInput;await ElMessageBox.confirm(translateText("将“{0}”更正为 {1} {2}，并重算本期综合得分。确定继续吗？", [item.kpiName, actualValue, translateText(item.unit)||'']),translateText("确认更正"),{type:'warning'});saving.value=true;try{await correctProjectKpiResult(settlement.value.settlementId,{planItemId:item.itemId,actualValue,resultNote:correctionForm.reason.trim()});correctionDialog.value=false;await loadWorkspace(selectedProjectId.value,selectedPlan.value.planId);ElMessage.success(translateText("KPI结果与综合得分已更正，原因已记录"))}finally{saving.value=false}}
watch(()=>route.fullPath,syncWorkspaceFromRoute)
onActivated(syncWorkspaceFromRoute)
useBusinessRefreshOnReactivated(async()=>{await loadProjects();if(selectedProjectId.value)await loadWorkspace(selectedProjectId.value,route.query.planId)})
onMounted(async()=>{loading.value=true;try{await loadProjects();if(selectedProjectId.value)await loadWorkspace(selectedProjectId.value,route.query.planId)}finally{loading.value=false}})
</script>

<style scoped>
.kpi-page{min-height:calc(100vh - 84px);padding:24px;background:#f3f5f8;color:#182537}.kpi-hero{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;padding:26px 30px;border-radius:16px;background:linear-gradient(120deg,#173750,#23655d);color:#fff}.kpi-hero>div:first-child>span{font-size:11px;letter-spacing:.17em;color:#7dd7ca}.kpi-hero h1{margin:5px 0;font-size:28px}.kpi-hero p{margin:0;color:#cbdcdf}.hero-tools{display:flex;align-items:center;gap:10px}.hero-tools .el-select{width:330px}.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin:15px 0}.summary-grid article{min-width:0;padding:16px 18px;border:1px solid #dfe5ea;border-radius:12px;background:#fff}.summary-grid span,.summary-grid small,.summary-grid b{display:block}.summary-grid span,.summary-grid small{color:#7d8997}.summary-grid span{font-size:12px}.summary-grid b{overflow:hidden;margin:7px 0;font-size:20px;text-overflow:ellipsis;white-space:nowrap}.summary-grid small{font-size:12px;line-height:1.45}.content-grid{display:grid;grid-template-columns:minmax(0,1.55fr) minmax(320px,.65fr);gap:14px}.content-grid main,.content-grid aside{display:flex;min-width:0;flex-direction:column;gap:14px}.section-card{border-color:#dfe5ea}.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:14px}.section-head h2{margin:0;font-size:18px}.section-head p{margin:5px 0 0;color:#82909f;font-size:12px;line-height:1.5}.section-actions{display:flex;gap:8px}.section-card small{display:block;margin-top:4px;color:#8793a0}.result-list{border-top:1px solid #edf0f3}.result-row{display:grid;grid-template-columns:minmax(170px,.7fr) minmax(150px,.4fr) minmax(230px,1fr) minmax(170px,.65fr);align-items:start;gap:12px;padding:16px 0;border-bottom:1px solid #edf0f3}.result-target,.result-value{display:flex;flex-direction:column;gap:5px}.result-target span,.result-value span{color:#83909e;font-size:12px}.result-row>p{grid-column:2/-1;margin:0;color:#5f6e7e;line-height:1.6}.result-attachments{grid-column:2/-1;min-width:0}.result-attachments>span{display:block;margin-bottom:8px;color:#83909e;font-size:12px}.review-bar{display:flex;align-items:center;justify-content:flex-end;gap:10px;margin-top:16px;padding:15px;border-radius:10px;background:#f6f8fa}.review-bar>div{display:flex;min-width:0;flex:1;flex-direction:column}.review-bar span{margin-top:5px;color:#7e8b99;font-size:12px}.tier-row,.plan-row{display:flex;width:100%;align-items:center;justify-content:space-between;gap:10px;padding:13px 2px;border:0;border-top:1px solid #edf0f3;background:transparent;color:inherit;text-align:left}.tier-row>span,.plan-row>span{display:flex;min-width:0;flex-direction:column}.tier-row strong{color:#217865}.plan-row{cursor:pointer}.plan-row.active{color:#226f67}.plan-row:hover{background:#f7f9fa}.empty-text{padding:20px;text-align:center;color:#929da8}.dialog-form{margin-top:18px}.tier-editor-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin:18px 0 9px}.tier-editor-head>div{display:flex;flex-direction:column;gap:4px}.tier-editor-head small{color:#7d8997;line-height:1.5}.tier-example{margin-bottom:10px}.tier-editor-columns,.tier-editor-row{display:grid;grid-template-columns:minmax(120px,.8fr) minmax(130px,.72fr) minmax(145px,.8fr) minmax(180px,1fr) 54px;gap:8px}.tier-editor-columns{padding:0 0 6px;color:#657384;font-size:12px}.tier-editor-columns span{padding-left:2px}.tier-editor-row{align-items:center;margin-bottom:8px}.tier-field>span{display:none}.tier-editor-row :deep(.el-input-number){width:100%}.tier-action{text-align:center;color:#a0a8b1}@media(max-width:1180px){.summary-grid{grid-template-columns:repeat(3,1fr)}.content-grid{grid-template-columns:1fr}.result-row{grid-template-columns:minmax(170px,.7fr) minmax(150px,.4fr) minmax(230px,1fr)}.result-row>:last-child{grid-column:2/-1}}@media(max-width:720px){.kpi-page{padding:12px}.kpi-hero{align-items:flex-start;flex-direction:column;padding:21px}.hero-tools{width:100%;align-items:stretch;flex-direction:column}.hero-tools .el-select,.hero-tools .el-button{width:100%}.summary-grid{grid-template-columns:repeat(2,1fr);gap:8px}.summary-grid article:last-child{grid-column:1/-1}.section-head,.review-bar,.tier-editor-head{align-items:stretch;flex-direction:column}.section-actions{display:grid;grid-template-columns:1fr 1fr}.result-row{grid-template-columns:1fr}.result-row>*,.result-row>:last-child,.result-row>p{grid-column:1}.tier-editor-columns{display:none}.tier-editor-row{grid-template-columns:1fr 1fr;padding:12px;border:1px solid #e5e9ed;border-radius:9px}.tier-field{display:flex;min-width:0;flex-direction:column;gap:5px}.tier-field>span{display:block;color:#657384;font-size:12px}.tier-editor-row>*:first-child,.tier-editor-row>*:nth-child(4),.tier-action{grid-column:1/-1}.tier-action{text-align:right}}
.settings-summary{grid-template-columns:repeat(3,minmax(0,1fr))}.field-help{display:block;margin-top:6px;color:#8a96a3;font-size:12px;line-height:1.5}.plan-remark{color:#82909f;font-size:13px;line-height:1.5}.plan-row-actions{align-items:flex-end;gap:4px}@media(max-width:720px){.settings-summary{grid-template-columns:repeat(2,minmax(0,1fr))}}
.result-entry{border-color:#a5d8ce;background:#f3fbf8}.result-entry .section-head{margin-bottom:10px}.result-entry-period{color:#526b66;font-size:13px;line-height:1.6}.result-entry .el-button{width:100%;margin-bottom:8px}
.manual-result-input{display:flex;flex-direction:column;gap:4px}.manual-result-input small{color:#6d7b8c;font-size:12px;line-height:1.4}
</style>
