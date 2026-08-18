package com.ruoyi.business.ai.capability.project;

import java.util.LinkedHashMap;
import java.util.List;
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
public class ChangeProjectOwnerCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public ChangeProjectOwnerCapability(IBusinessProjectService service) { this.service = service; }
    @Override public String code() { return "project.owner.change"; }
    @Override public String description()
    { return "更换已有项目的主负责人。先用项目和人员目录取得稳定ID，必须说明原因；老板确认后才执行。"; }
    @Override public String requiredPermission() { return "business:project:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "项目目录返回的稳定项目ID");
        AiSchemas.property(schema, "newOwnerUserId", "number", "人员目录返回的新负责人用户ID");
        AiSchemas.property(schema, "reason", "string", "更换负责人的真实原因");
        return AiSchemas.required(schema, "projectId", "newOwnerUserId", "reason");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProject project = project(input, invocation); String owner = ownerName(input);
        String reason = text(input.get("reason")); if (reason.isEmpty()) throw new ServiceException("请说明更换负责人的原因");
        return "将项目“" + project.getProjectName() + "”的主负责人由“" + project.getMainOwnerName()
            + "”更换为“" + owner + "”，原因：" + reason;
    }
    @Override public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProject project = project(input, invocation); Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projectId", project.getProjectId()); result.put("projectName", project.getProjectName());
        result.put("oldOwnerName", project.getMainOwnerName()); result.put("newOwnerName", ownerName(input));
        result.put("reason", text(input.get("reason"))); return result;
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessProject saved = service.changeOwner(number(input.get("projectId")), number(input.get("newOwnerUserId")),
            text(input.get("reason")), invocation.getActor().getUserId(), invocation.getActor().getUserName(), true);
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("projectId", saved.getProjectId());
        result.put("projectName", saved.getProjectName()); result.put("mainOwnerName", saved.getMainOwnerName()); return result;
    }
    private BusinessProject project(Map<String, Object> input, AiCapabilityInvocation invocation)
    {
        Long id = number(input.get("projectId")); if (id == null) throw new ServiceException("请先确定项目");
        return service.getProject(id, invocation.getActor().getUserId(), invocation.getActor().isAdministrator(), true);
    }
    private String ownerName(Map<String, Object> input)
    {
        Long id = number(input.get("newOwnerUserId")); if (id == null) throw new ServiceException("请先确定新负责人");
        List<Map<String, Object>> users = service.userOptions(null);
        if (users != null) for (Map<String, Object> row : users) if (id.equals(number(row.get("userId"))))
            return text(row.get("nickName") == null ? row.get("userName") : row.get("nickName"));
        throw new ServiceException("新负责人不存在或当前账号不可见");
    }
    private Long number(Object value)
    { try { return value instanceof Number ? ((Number)value).longValue() : Long.valueOf(String.valueOf(value)); }
      catch (Exception ex) { return null; } }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
