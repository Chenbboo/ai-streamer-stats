package com.ruoyi.business.ai.capability.project;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class UpdateProjectBudgetCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public UpdateProjectBudgetCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.budget.update"; }
    @Override public String description()
    { return "把指定项目的预算上限调整为新金额。必须先用项目目录取得稳定项目ID；只准备确认单，老板确认后才执行。"; }
    @Override public String requiredPermission() { return "business:project:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "项目目录返回的稳定项目ID");
        AiSchemas.property(schema, "budgetLimit", "number", "新的预算上限，必须大于等于0");
        AiSchemas.property(schema, "currency", "string", "三位币种代码，如CNY或VND");
        AiSchemas.property(schema, "reason", "string", "老板说明的预算调整原因");
        return AiSchemas.required(schema, "projectId", "budgetLimit", "currency", "reason");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProject project = project(input, invocation);
        BigDecimal amount = amount(input.get("budgetLimit"));
        String currency = text(input.get("currency")).toUpperCase();
        String reason = text(input.get("reason"));
        if (amount == null || amount.signum() < 0) throw new ServiceException("预算金额必须大于等于0");
        if (currency.length() != 3) throw new ServiceException("币种代码必须是三位字符");
        if (reason.isEmpty()) throw new ServiceException("请说明调整预算的原因");
        return "将项目“" + project.getProjectName() + "”的预算调整为 " + amount.toPlainString()
            + " " + currency + "，原因：" + reason;
    }
    @Override public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProject project = project(input, invocation);
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("projectId", project.getProjectId()); details.put("projectName", project.getProjectName());
        details.put("oldBudgetLimit", project.getBudgetLimit()); details.put("budgetLimit", input.get("budgetLimit"));
        details.put("currency", text(input.get("currency")).toUpperCase()); details.put("reason", text(input.get("reason")));
        return details;
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long projectId = number(input.get("projectId")); BigDecimal amount = amount(input.get("budgetLimit"));
        BusinessProject project = service.updateBudget(projectId, amount, text(input.get("currency")).toUpperCase(),
            text(input.get("reason")), invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", project.getProjectId()); result.put("projectNo", project.getProjectNo());
        result.put("projectName", project.getProjectName()); result.put("budgetLimit", amount);
        result.put("currency", text(input.get("currency")).toUpperCase()); result.put("reason", text(input.get("reason")));
        return result;
    }
    private BusinessProject project(Map<String, Object> input, AiCapabilityInvocation invocation)
    {
        Long projectId = number(input.get("projectId"));
        if (projectId == null) throw new ServiceException("请先确定要调整预算的项目");
        return service.getProject(projectId, invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), true);
    }
    private Long number(Object value)
    { try { return value instanceof Number ? ((Number)value).longValue() : Long.valueOf(String.valueOf(value)); }
      catch (Exception ex) { return null; } }
    private BigDecimal amount(Object value)
    { try { return value == null ? null : new BigDecimal(String.valueOf(value)); }
      catch (Exception ex) { return null; } }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
