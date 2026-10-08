package com.ruoyi.business.ai.capability.accounting;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessAccountingService;

@Component
public class RecalculateProjectDayCapability implements AiConfirmableCapability
{
    private final IBusinessAccountingService service;
    @Autowired public RecalculateProjectDayCapability(IBusinessAccountingService service) { this.service = service; }
    @Override public String code() { return "accounting.project-day.recalculate"; }
    @Override public String description() { return "按项目成本政策版本、已确认收支和投入依据重新核算指定项目某日经营结果并生成新版本。旧版保留历史假勤规则；飞书只读同步不会直接修改投入或成本。分离策略项目交付后可在核算开放时重算执行期间日期，已关账项目不能直接重算。"; }
    @Override public String requiredPermission() { return "business:accounting:recalculate"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object(); AiSchemas.property(schema, "projectId", "number", "稳定项目ID");
        AiSchemas.property(schema, "bizDate", "string", "核算日期 YYYY-MM-DD"); return AiSchemas.required(schema, "projectId", "bizDate");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return "重新核算项目 " + AiCapabilityInputs.number(input.get("projectId")) + " 在 " + AiCapabilityInputs.text(input.get("bizDate")) + " 的经营结果并生成新版本"; }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> calculated = service.recalculate(AiCapabilityInputs.number(input.get("projectId")),
            AiCapabilityInputs.date(input.get("bizDate")), invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), invocation.getActor().isAdministrator());
        return new LinkedHashMap<String, Object>(calculated);
    }
}
