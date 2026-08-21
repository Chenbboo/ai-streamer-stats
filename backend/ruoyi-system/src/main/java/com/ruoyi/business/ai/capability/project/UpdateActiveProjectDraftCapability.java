package com.ruoyi.business.ai.capability.project;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.common.exception.ServiceException;

public class UpdateActiveProjectDraftCapability extends ProjectDraftCapabilitySupport implements AiCapability
{
    private static final Set<String> ALLOWED_FIELDS = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        "projectName", "ownerName", "companyName", "objective", "planStartDate", "planEndDate",
        "projectType", "accountingMode", "managementMode", "priority", "baseCurrency", "budgetLimit",
        "noBudget", "executionSource")));

    @Autowired
    public UpdateActiveProjectDraftCapability(BusinessAiMapper mapper, ObjectMapper objectMapper)
    {
        super(mapper, objectMapper);
    }

    @Override public String code() { return "project.draft.update"; }
    @Override public String description()
    {
        return "按老板当前表达修改正在编辑的立项草稿。传入完整的新字段值；不要依赖固定说法，也不要修改未提及的字段。";
    }
    @Override public String requiredPermission() { return "business:project:add"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.DRAFT_WRITE; }

    @Override
    public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        Map<String, Object> changes = AiSchemas.property(schema, "changes", "object",
            "仅包含老板明确要求新增或修改的草稿字段");
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        changes.put("properties", properties);
        changes.put("additionalProperties", false);
        stringProperty(properties, "projectName", "项目名称");
        stringProperty(properties, "ownerName", "系统中的负责人姓名或账号");
        Map<String, Object> company = new LinkedHashMap<String, Object>();
        company.put("type", "string");
        company.put("description", "归属公司。老板说上海公司时填上海美丸文化公司；说越南公司时填越南meimaru公司");
        company.put("enum", Arrays.asList("上海美丸文化公司", "越南meimaru公司"));
        properties.put("companyName", company);
        stringProperty(properties, "objective", "完整且可验收的新项目目标");
        stringProperty(properties, "planStartDate", "开始日期，YYYY-MM-DD");
        stringProperty(properties, "planEndDate", "结束日期，YYYY-MM-DD");
        stringProperty(properties, "projectType", "项目类型");
        stringProperty(properties, "accountingMode", "PROFIT/COST/VALUE/HYBRID");
        stringProperty(properties, "managementMode", "管理模式");
        stringProperty(properties, "priority", "优先级");
        stringProperty(properties, "baseCurrency", "币种");
        numberProperty(properties, "budgetLimit", "预算上限");
        booleanProperty(properties, "noBudget", "是否明确不设预算");
        stringProperty(properties, "executionSource", "执行来源");
        return AiSchemas.required(schema, "changes");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Object changeValue = input.get("changes");
        if (!(changeValue instanceof Map) || ((Map<?, ?>) changeValue).isEmpty())
            throw new ServiceException("没有提供需要修改的立项字段");
        Map<String, Object> changes = (Map<String, Object>) changeValue;
        for (String key : changes.keySet())
            if (!ALLOWED_FIELDS.contains(key)) throw new ServiceException("不允许修改立项字段：" + key);

        Map<String, Object> workflow = activeWorkflow(invocation.getConversationId(),
            invocation.getActor().getUserId());
        Map<String, Object> before = draft(workflow);
        Map<String, Object> after = new LinkedHashMap<String, Object>(before);
        after.putAll(changes);
        if (before.equals(after)) return view(workflow, after);

        Map<String, Object> row = new LinkedHashMap<String, Object>(workflow);
        row.put("draftJson", json(after));
        row.put("workflowStatus", "COLLECTING");
        row.put("currentStep", "REVALIDATE");
        row.put("missingFieldsJson", json(Collections.singletonList("修改后的立项资料需要重新校验")));
        row.put("actionRequestId", null);
        int updated = mapper.updateWorkflow(row);
        if (updated != 1) throw new ServiceException("立项草稿刚刚被其他操作更新，请重新读取后再修改");
        Number version = (Number) row.get("versionNo");
        if (version != null) row.put("versionNo", version.longValue() + 1L);
        Number oldActionRequestId = workflow.get("actionRequestId") instanceof Number
            ? (Number) workflow.get("actionRequestId") : null;
        if (oldActionRequestId != null)
            mapper.supersedeActionRequest(oldActionRequestId.longValue(), invocation.getActor().getUserId());

        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("workflowId", workflow.get("workflowId"));
        event.put("conversationId", invocation.getConversationId());
        event.put("userId", invocation.getActor().getUserId());
        event.put("eventType", "FIELDS_UPDATED");
        event.put("beforeJson", json(before));
        event.put("afterJson", json(after));
        event.put("messageId", invocation.getRequestMessageId());
        mapper.insertWorkflowEvent(event);
        return view(row, after);
    }

    private void stringProperty(Map<String, Object> properties, String name, String description)
    {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string"); property.put("description", description); properties.put(name, property);
    }

    private void numberProperty(Map<String, Object> properties, String name, String description)
    {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "number"); property.put("description", description); properties.put(name, property);
    }

    private void booleanProperty(Map<String, Object> properties, String name, String description)
    {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "boolean"); property.put("description", description); properties.put(name, property);
    }
}
