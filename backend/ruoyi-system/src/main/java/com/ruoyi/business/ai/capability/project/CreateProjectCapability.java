package com.ruoyi.business.ai.capability.project;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/** Creates a project from stable directory IDs after explicit user confirmation. */
public class CreateProjectCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService projectService;
    private final IBusinessStaffService staffService;

    @Autowired
    public CreateProjectCapability(IBusinessProjectService projectService, IBusinessStaffService staffService)
    {
        this.projectService = projectService;
        this.staffService = staffService;
    }

    @Override public String code() { return "project.create"; }

    @Override
    public String description()
    {
        return "创建正式项目。先通过人员目录和部门目录取得负责人 userId 与归属公司 deptId；"
            + "资料缺失时应向用户追问，不得猜测 ID。调用后生成完整立项确认单，用户确认后才创建。";
    }

    @Override public String requiredPermission() { return "business:project:add"; }

    @Override
    public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectName", "string", "项目名称");
        AiSchemas.property(schema, "mainOwnerUserId", "number", "人员目录返回的负责人稳定 userId");
        AiSchemas.property(schema, "companyDeptId", "number", "部门目录返回的归属公司稳定 deptId");
        AiSchemas.property(schema, "objective", "string", "完整、可验收的项目目标");
        AiSchemas.property(schema, "planStartDate", "string", "计划开始日期 YYYY-MM-DD");
        AiSchemas.property(schema, "planEndDate", "string", "计划结束日期 YYYY-MM-DD");
        enumProperty(schema, "projectType", "项目类型", "LIVE", "JEWELRY", "ECOMMERCE", "OPERATIONS",
            "INTERNAL", "GENERAL", "OTHER");
        enumProperty(schema, "accountingMode", "核算方式", "PROFIT", "COST", "VALUE", "HYBRID");
        enumProperty(schema, "managementMode", "管理模式", "SIMPLE", "STANDARD", "DELIVERY");
        enumProperty(schema, "priority", "优先级", "LOW", "MEDIUM", "HIGH");
        AiSchemas.property(schema, "baseCurrency", "string", "三位币种代码，例如 CNY 或 VND");
        AiSchemas.property(schema, "budgetLimit", "number", "预算上限；明确不设预算时省略");
        AiSchemas.property(schema, "noBudget", "boolean", "是否明确不设置预算");
        enumProperty(schema, "executionSource", "执行来源", "MANUAL", "LIVE");
        return AiSchemas.required(schema, "projectName", "mainOwnerUserId", "companyDeptId", "objective",
            "planStartDate", "planEndDate", "accountingMode", "noBudget");
    }

    @Override
    public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        ProjectDraft draft = validate(input);
        return "创建项目“" + draft.project.getProjectName() + "”，归属" + draft.companyName + "，负责人"
            + draft.ownerName + "，周期 " + date(draft.project.getPlanStartDate()) + " 至 "
            + date(draft.project.getPlanEndDate());
    }

    @Override
    public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        ProjectDraft draft = validate(input);
        Map<String, Object> result = view(draft.project);
        result.put("companyName", draft.companyName);
        result.put("mainOwnerName", draft.ownerName);
        return result;
    }

    @Override
    public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        ProjectDraft draft = validate(input);
        BusinessProject created = projectService.createProject(draft.project, invocation.getActor().getUserId(),
            invocation.getActor().getUserName());
        Map<String, Object> result = view(created);
        result.put("companyName", StringUtils.isBlank(created.getCompanyName()) ? draft.companyName : created.getCompanyName());
        result.put("mainOwnerName", StringUtils.isBlank(created.getMainOwnerName()) ? draft.ownerName : created.getMainOwnerName());
        result.put("status", created.getStatus());
        return result;
    }

    private ProjectDraft validate(Map<String, Object> input)
    {
        String name = AiCapabilityInputs.text(input.get("projectName"));
        String objective = AiCapabilityInputs.text(input.get("objective"));
        Long ownerId = AiCapabilityInputs.number(input.get("mainOwnerUserId"));
        Long companyId = AiCapabilityInputs.number(input.get("companyDeptId"));
        Date start = AiCapabilityInputs.date(input.get("planStartDate"));
        Date end = AiCapabilityInputs.date(input.get("planEndDate"));
        String accountingMode = AiCapabilityInputs.upper(input.get("accountingMode"));
        boolean noBudget = Boolean.TRUE.equals(input.get("noBudget"));
        BigDecimal budget = AiCapabilityInputs.decimal(input.get("budgetLimit"));

        if (StringUtils.isBlank(name) || StringUtils.isBlank(objective))
            throw new ServiceException("项目名称和目标不能为空");
        if (ownerId == null || companyId == null)
            throw new ServiceException("负责人和归属公司必须使用目录返回的稳定 ID");
        if (start == null || end == null || start.after(end))
            throw new ServiceException("项目计划周期不正确");
        if (!Arrays.asList("PROFIT", "COST", "VALUE", "HYBRID").contains(accountingMode))
            throw new ServiceException("项目核算方式不正确");
        if (!noBudget && budget == null)
            throw new ServiceException("请填写预算上限，或明确不设置预算");
        if (budget != null && budget.signum() < 0)
            throw new ServiceException("预算不能小于 0");

        String ownerName = null;
        for (Map<String, Object> item : safe(projectService.userOptions(null)))
            if (ownerId.equals(AiCapabilityInputs.number(item.get("userId"))))
                ownerName = first(item.get("nickName"), item.get("userName"));
        String companyName = null;
        for (Map<String, Object> item : safe(staffService.listOptions()))
            if (companyId.equals(AiCapabilityInputs.number(item.get("companyDeptId"))))
                companyName = AiCapabilityInputs.text(item.get("companyName"));
        if (StringUtils.isBlank(ownerName)) throw new ServiceException("负责人不存在或当前不可选");
        if (StringUtils.isBlank(companyName)) throw new ServiceException("归属公司不存在或当前不可选");

        BusinessProject project = new BusinessProject();
        project.setProjectName(name);
        project.setMainOwnerUserId(ownerId);
        project.setCompanyDeptId(companyId);
        project.setObjective(objective);
        project.setPlanStartDate(start);
        project.setPlanEndDate(end);
        project.setAccountingMode(accountingMode);
        project.setProjectType(defaultValue(AiCapabilityInputs.upper(input.get("projectType")), "GENERAL"));
        project.setManagementMode(allowed(AiCapabilityInputs.upper(input.get("managementMode")),
            "SIMPLE", "STANDARD", "DELIVERY"));
        project.setPriority(allowed(AiCapabilityInputs.upper(input.get("priority")), "LOW", "MEDIUM", "HIGH"));
        project.setBaseCurrency(defaultValue(AiCapabilityInputs.upper(input.get("baseCurrency")), "CNY"));
        if (project.getBaseCurrency().length() != 3) throw new ServiceException("币种必须使用三位代码");
        project.setBudgetLimit(noBudget ? null : budget);
        if ("LIVE".equals(AiCapabilityInputs.upper(input.get("executionSource")))) project.setExecutionSource("LIVE");
        return new ProjectDraft(project, ownerName, companyName);
    }

    private Map<String, Object> view(BusinessProject project)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", project.getProjectId());
        result.put("projectNo", project.getProjectNo());
        result.put("projectName", project.getProjectName());
        result.put("objective", project.getObjective());
        result.put("mainOwnerUserId", project.getMainOwnerUserId());
        result.put("companyDeptId", project.getCompanyDeptId());
        result.put("planStartDate", date(project.getPlanStartDate()));
        result.put("planEndDate", date(project.getPlanEndDate()));
        result.put("projectType", project.getProjectType());
        result.put("accountingMode", project.getAccountingMode());
        result.put("managementMode", project.getManagementMode());
        result.put("priority", project.getPriority());
        result.put("budgetLimit", project.getBudgetLimit());
        result.put("baseCurrency", project.getBaseCurrency());
        return result;
    }

    private void enumProperty(Map<String, Object> schema, String name, String description, String... values)
    {
        AiSchemas.property(schema, name, "string", description).put("enum", Arrays.asList(values));
    }

    private String allowed(String value, String... values)
    {
        return Arrays.asList(values).contains(value) ? value : values[0];
    }

    private String defaultValue(String value, String fallback)
    {
        return StringUtils.isBlank(value) ? fallback : value;
    }

    private String first(Object first, Object second)
    {
        String value = AiCapabilityInputs.text(first);
        return StringUtils.isBlank(value) ? AiCapabilityInputs.text(second) : value;
    }

    private String date(Date value)
    {
        return value == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(value);
    }

    private List<Map<String, Object>> safe(List<Map<String, Object>> values)
    {
        return values == null ? Collections.<Map<String, Object>>emptyList() : values;
    }

    private static final class ProjectDraft
    {
        private final BusinessProject project;
        private final String ownerName;
        private final String companyName;

        private ProjectDraft(BusinessProject project, String ownerName, String companyName)
        {
            this.project = project;
            this.ownerName = ownerName;
            this.companyName = companyName;
        }
    }
}
