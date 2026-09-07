package com.ruoyi.business.ai.capability.project;

import static com.ruoyi.business.ai.capability.AiCapabilityInputs.number;
import static com.ruoyi.business.ai.capability.AiCapabilityInputs.text;
import static com.ruoyi.business.ai.capability.AiCapabilityInputs.upper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.common.exception.ServiceException;

@Component
public class TransitionProjectCapability implements AiConfirmableCapability
{
    private static final Set<String> ACTIONS = new LinkedHashSet<String>(
        Arrays.asList("START_PLANNING", "PAUSE", "RESUME", "CLOSE", "CANCEL"));
    private final IBusinessProjectService service;

    @Autowired public TransitionProjectCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.transition"; }
    @Override public String description()
    {
        return "让项目进入规划、暂停、恢复、直接关闭交付或取消。分离策略的交付关闭不会关闭核算；"
            + "旧项目沿用原结项规则，核算关闭需使用页面专用操作。计划审批和验收结项使用专用审核工具。"
            + "必须提供稳定项目ID，确认后执行。";
    }
    @Override public String requiredPermission() { return "business:project:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "项目ID");
        Map<String, Object> action = AiSchemas.property(schema, "action", "string", "START_PLANNING/PAUSE/RESUME/CLOSE/CANCEL");
        action.put("enum", new ArrayList<String>(ACTIONS));
        AiSchemas.property(schema, "comment", "string", "暂停、关闭、取消时必须填写原因或结论");
        return AiSchemas.required(schema, "projectId", "action");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProject project = currentProject(invocation, input);
        String action = upper(input.get("action"));
        String summary = label(action) + "项目“" + project.getProjectName() + "”"
            + (text(input.get("comment")).isEmpty() ? "" : "，说明：" + text(input.get("comment")));
        if ("CLOSE".equals(action)) summary += "；" + closeEffect(project);
        return summary;
    }
    @Override public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProject project = currentProject(invocation, input);
        Map<String, Object> details = new LinkedHashMap<String, Object>(input);
        details.put("projectName", project.getProjectName()); details.put("status", project.getStatus());
        details.put("deliveryPolicyVersion", policy(project)); details.put("accountingState", accountingState(project));
        if ("CLOSE".equals(upper(input.get("action")))) details.put("closureEffect", closeEffect(project));
        return details;
    }
    @Override public Map<String, Object> persistedInput(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProject project = currentProject(invocation, input);
        Map<String, Object> persisted = new LinkedHashMap<String, Object>(input);
        persisted.put("expectedDeliveryPolicyVersion", policy(project));
        persisted.put("expectedAccountingState", accountingState(project));
        persisted.put("expectedProjectStatus", project.getStatus());
        return persisted;
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProject current = currentProject(invocation, input);
        boolean hasSnapshot = input.containsKey("expectedDeliveryPolicyVersion");
        if ((!hasSnapshot && BusinessProjectLifecycle.isSeparated(current))
            || (hasSnapshot && (!Objects.equals(input.get("expectedDeliveryPolicyVersion"), policy(current))
                || !Objects.equals(input.get("expectedAccountingState"), accountingState(current))
                || !Objects.equals(input.get("expectedProjectStatus"), current.getStatus()))))
            throw new ServiceException("项目状态、交付规则或核算状态已变化，请重新生成操作确认单");

        String action = upper(input.get("action"));
        BusinessProject saved = service.transition(number(input.get("projectId")), action, text(input.get("comment")),
            invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", saved.getProjectId()); result.put("projectName", saved.getProjectName());
        result.put("action", action); result.put("status", saved.getStatus());
        result.put("deliveryPolicyVersion", policy(saved)); result.put("accountingState", accountingState(saved));
        return result;
    }
    private BusinessProject currentProject(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        String action = upper(input.get("action"));
        if (!ACTIONS.contains(action)) throw new ServiceException("不支持的项目状态操作");
        BusinessProject project = service.getProject(number(input.get("projectId")), invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), true);
        if (("PAUSE".equals(action) || "CLOSE".equals(action) || "CANCEL".equals(action))
            && text(input.get("comment")).isEmpty()) throw new ServiceException("请说明操作原因或完成结论");
        return project;
    }
    private String policy(BusinessProject project)
    { return BusinessProjectLifecycle.isSeparated(project) ? "SEPARATED_V1" : "LEGACY_V1"; }
    private String accountingState(BusinessProject project)
    { return BusinessProjectLifecycle.isAccountingClosed(project) ? "CLOSED" : "OPEN"; }
    private String closeEffect(BusinessProject project)
    {
        return BusinessProjectLifecycle.isSeparated(project)
            ? "仅关闭项目交付，核算状态保持不变，后续结算按原有权限继续办理"
            : "沿用旧流程，项目结项同时关闭核算";
    }
    private String label(String action)
    {
        if ("START_PLANNING".equals(action)) return "推进到规划";
        if ("PAUSE".equals(action)) return "暂停";
        if ("RESUME".equals(action)) return "恢复";
        if ("CLOSE".equals(action)) return "直接关闭";
        return "取消";
    }
}
