package com.ruoyi.business.ai.capability.project;

import java.util.Map;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.support.BusinessProjectLifecycle;
import com.ruoyi.common.exception.ServiceException;

/** Re-read the authorized project both before a card is prepared and when an old card is confirmed. */
final class ProjectLegacyPlanningGuard
{
    private ProjectLegacyPlanningGuard() { }

    static BusinessProject requireLegacy(IBusinessProjectService service, AiCapabilityInvocation invocation,
        Map<String,Object> input, boolean budget)
    {
        Long projectId = input == null ? null : AiCapabilityInputs.number(input.get("projectId"));
        if (projectId == null || projectId <= 0) throw new ServiceException("请先确定有效的项目ID");
        BusinessProject project = service.getProject(projectId, invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), true);
        if (project == null) throw new ServiceException("项目不存在或无权查看");
        if ("ACTUAL_WORK_V1".equals(project.getCostPolicyVersion()))
            throw new ServiceException(budget
                ? "本项目使用独立计划基线，请先查询项目计划，再通过计划变更流程申请调整预算。"
                : "本项目按实际小时或人天管理，请先查询资源计划和工作记录，再通过资源与实际工作流程办理。");
        if (project.getCostPolicyVersion() != null && !"PERCENTAGE_V1".equals(project.getCostPolicyVersion()))
            throw new ServiceException("项目成本策略不受旧版投入或预算能力支持，请刷新项目后使用对应流程");
        BusinessProjectLifecycle.requireAccountingOpen(project);
        return project;
    }
}
