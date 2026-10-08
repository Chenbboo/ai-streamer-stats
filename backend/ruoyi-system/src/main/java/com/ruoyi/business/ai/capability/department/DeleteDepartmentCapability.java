package com.ruoyi.business.ai.capability.department;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessDepartmentService;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.exception.ServiceException;

@Component
public class DeleteDepartmentCapability implements AiConfirmableCapability
{
    private final IBusinessDepartmentService service;
    @Autowired public DeleteDepartmentCapability(IBusinessDepartmentService service) { this.service = service; }
    @Override public String code() { return "department.remove"; }
    @Override public String description()
    { return "删除空部门。必须先读取部门目录取得稳定部门ID；公司根节点、有子部门或人员的部门会被业务服务拒绝。确认后执行。"; }
    @Override public String requiredPermission() { return "business:department:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object(); AiSchemas.property(s, "deptId", "number", "部门目录返回的部门ID");
        AiSchemas.property(s, "deptName", "string", "确认卡展示的部门名称"); return AiSchemas.required(s, "deptId");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return "删除空部门“" + actualName(input) + "”"; }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long deptId = AiCapabilityInputs.number(input.get("deptId")); String name = actualName(input);
        service.deleteDepartment(deptId); Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deptId", deptId); result.put("deptName", name); result.put("status", "REMOVED"); return result;
    }
    private String actualName(Map<String, Object> input)
    {
        Long id = AiCapabilityInputs.number(input.get("deptId"));
        if (id == null) throw new ServiceException("请先确定要删除的部门");
        List<SysDept> rows = service.listDepartments(new SysDept());
        if (rows != null) for (SysDept row : rows) if (id.equals(row.getDeptId())) return row.getDeptName();
        throw new ServiceException("部门不存在或当前账号不可见");
    }
}
