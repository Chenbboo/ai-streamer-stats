package com.ruoyi.business.ai.capability.project;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.mapper.BusinessAiMapper;

@Component
public class GetActiveProjectDraftCapability extends ProjectDraftCapabilitySupport implements AiCapability
{
    @Autowired
    public GetActiveProjectDraftCapability(BusinessAiMapper mapper, ObjectMapper objectMapper)
    {
        super(mapper, objectMapper);
    }

    @Override public String code() { return "project.draft.get"; }
    @Override public String description() { return "读取当前对话中正在编辑的立项草稿及其稳定工作流ID。"; }
    @Override public String requiredPermission() { return "business:project:add"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema() { return AiSchemas.object(); }

    @Override
    public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> workflow = activeWorkflow(invocation.getConversationId(),
            invocation.getActor().getUserId());
        return view(workflow, draft(workflow));
    }
}
