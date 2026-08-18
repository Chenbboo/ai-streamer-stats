package com.ruoyi.business.ai.capability.project;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessProjectEffort;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class ReviewProjectMemberEffortCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public ReviewProjectMemberEffortCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.effort.member.review"; }
    @Override public String description()
    { return "确认或退回项目成员某天的实际投入。先读取项目及成员数据取得稳定ID；退回必须填写意见，确认后执行。"; }
    @Override public String requiredPermission() { return "business:project:allocation"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object(); Map<String, Object> decision = AiSchemas.property(s,
            "decision", "string", "CONFIRM确认，RETURN退回"); decision.put("enum", Arrays.asList("CONFIRM", "RETURN"));
        AiSchemas.property(s, "projectId", "number", "项目ID"); AiSchemas.property(s, "memberUserId", "number", "成员用户ID");
        AiSchemas.property(s, "memberName", "string", "确认卡展示的成员姓名");
        AiSchemas.property(s, "bizDate", "string", "投入日期 YYYY-MM-DD");
        AiSchemas.property(s, "reviewComment", "string", "退回原因");
        return AiSchemas.required(s, "decision", "projectId", "memberUserId", "bizDate");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        validate(input); return ("RETURN".equals(AiCapabilityInputs.upper(input.get("decision"))) ? "退回" : "确认")
            + "“" + AiCapabilityInputs.text(input.get("memberName")) + "”在 "
            + AiCapabilityInputs.text(input.get("bizDate")) + " 的项目投入";
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        validate(input); String decision = AiCapabilityInputs.upper(input.get("decision"));
        BusinessProjectEffort saved = "RETURN".equals(decision)
            ? service.returnMemberEffort(AiCapabilityInputs.number(input.get("projectId")),
                AiCapabilityInputs.number(input.get("memberUserId")), AiCapabilityInputs.date(input.get("bizDate")),
                AiCapabilityInputs.text(input.get("reviewComment")), invocation.getActor().getUserId(),
                invocation.getActor().getUserName(), true)
            : service.confirmMemberEffort(AiCapabilityInputs.number(input.get("projectId")),
                AiCapabilityInputs.number(input.get("memberUserId")), AiCapabilityInputs.date(input.get("bizDate")),
                invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("projectId", saved.getProjectId());
        result.put("memberUserId", saved.getUserId()); result.put("bizDate", input.get("bizDate"));
        result.put("status", saved.getReportStatus()); return result;
    }
    private void validate(Map<String, Object> input)
    {
        String decision = AiCapabilityInputs.upper(input.get("decision"));
        if (!"CONFIRM".equals(decision) && !"RETURN".equals(decision)) throw new ServiceException("投入审核决定不正确");
        if (AiCapabilityInputs.date(input.get("bizDate")) == null) throw new ServiceException("投入日期不正确");
        if ("RETURN".equals(decision) && AiCapabilityInputs.text(input.get("reviewComment")).isEmpty())
            throw new ServiceException("退回投入必须填写意见");
    }
}
