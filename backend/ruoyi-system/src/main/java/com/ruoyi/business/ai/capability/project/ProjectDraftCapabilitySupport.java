package com.ruoyi.business.ai.capability.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.mapper.BusinessAiMapper;
import com.ruoyi.common.exception.ServiceException;

abstract class ProjectDraftCapabilitySupport
{
    protected final BusinessAiMapper mapper;
    protected final ObjectMapper objectMapper;

    ProjectDraftCapabilitySupport(BusinessAiMapper mapper, ObjectMapper objectMapper)
    {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    protected Map<String, Object> activeWorkflow(Long conversationId, Long userId)
    {
        if (conversationId == null) throw new ServiceException("请先开始一次老板 AI 对话");
        Map<String, Object> workflow = mapper.selectActiveWorkflow(conversationId, userId);
        if (workflow == null || !"CREATE_PROJECT".equals(string(workflow.get("workflowCode"))))
            throw new ServiceException("当前对话没有正在进行的立项草稿");
        return workflow;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> draft(Map<String, Object> workflow)
    {
        try
        {
            Object parsed = objectMapper.readValue(string(workflow.get("draftJson")), Object.class);
            return parsed instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>) parsed)
                : new LinkedHashMap<String, Object>();
        }
        catch (Exception ex) { throw new ServiceException("立项草稿数据格式异常"); }
    }

    protected Map<String, Object> view(Map<String, Object> workflow, Map<String, Object> draft)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("workflowId", workflow.get("workflowId"));
        result.put("status", workflow.get("workflowStatus"));
        result.put("currentStep", workflow.get("currentStep"));
        result.put("version", workflow.get("versionNo"));
        result.put("actionRequestId", workflow.get("actionRequestId"));
        result.put("draft", Collections.unmodifiableMap(new LinkedHashMap<String, Object>(draft)));
        return result;
    }

    protected String json(Object value)
    {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new ServiceException("立项草稿序列化失败"); }
    }

    protected String string(Object value) { return value == null ? "" : String.valueOf(value); }
}
