package com.ruoyi.business.ai.capability.read;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessProjectService;

/** Permission-scoped decisions and due work shown on the boss workbench. */
@Component
public class PendingDecisionsCapability implements AiCapability
{
    private final IBusinessProjectService service;
    private final ObjectMapper objectMapper;

    @Autowired
    public PendingDecisionsCapability(IBusinessProjectService service, ObjectMapper objectMapper)
    {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override public String code() { return "business.pending-decisions.get"; }
    @Override public String description()
    {
        return "读取当前登录老板需要处理的项目决策和到期工作，包括立项推进、计划审批、暂停恢复、项目验收。"
            + "返回稳定项目ID；需要查看或执行时再调用对应项目读取或确认能力。";
    }
    @Override public String requiredPermission() { return "business:boss:view"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema() { return AiSchemas.object(); }

    @Override
    public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> dashboard = service.dashboard(invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), true);
        List<Map<String, Object>> decisions = views(dashboard.get("decisions"), true);
        List<Map<String, Object>> tasks = views(dashboard.get("tasks"), false);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("decisionCount", decisions.size());
        result.put("taskCount", tasks.size());
        result.put("decisions", decisions);
        result.put("tasks", tasks);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> views(Object value, boolean decision)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (!(value instanceof List)) return result;
        for (Object item : (List<Object>) value)
        {
            Map<String, Object> source;
            if (item instanceof Map) source = (Map<String, Object>) item;
            else source = objectMapper.convertValue(item, Map.class);
            Map<String, Object> target = new LinkedHashMap<String, Object>();
            String[] fields = decision
                ? new String[] { "projectId", "projectNo", "projectName", "companyName", "mainOwnerName",
                    "objective", "status", "baselineStatus", "planStartDate", "planEndDate" }
                : new String[] { "taskId", "taskName", "projectId", "projectNo", "projectName",
                    "assigneeUserId", "assigneeName", "status", "priority", "progress", "dueDate" };
            for (String field : fields) if (source.containsKey(field)) target.put(field, source.get(field));
            if (decision) addDecisionMeaning(target);
            result.add(target);
        }
        return result;
    }

    private void addDecisionMeaning(Map<String, Object> decision)
    {
        String status = text(decision.get("status"));
        String baselineStatus = text(decision.get("baselineStatus"));
        if ("PLANNING".equals(status) && "SUBMITTED".equals(baselineStatus))
        { decision.put("decisionType", "PLAN_APPROVAL"); decision.put("nextAction", "审核项目计划"); }
        else if ("DRAFT".equals(status))
        { decision.put("decisionType", "START_PLANNING"); decision.put("nextAction", "确认进入规划"); }
        else if ("ACCEPTANCE".equals(status))
        { decision.put("decisionType", "PROJECT_ACCEPTANCE"); decision.put("nextAction", "审核项目验收"); }
        else if ("PAUSED".equals(status))
        { decision.put("decisionType", "RESUME_PROJECT"); decision.put("nextAction", "确认是否恢复执行"); }
        else
        { decision.put("decisionType", "PROJECT_DECISION"); decision.put("nextAction", "查看项目后作出决定"); }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
