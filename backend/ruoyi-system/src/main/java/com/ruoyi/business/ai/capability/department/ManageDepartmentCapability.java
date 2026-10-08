package com.ruoyi.business.ai.capability.department;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessDepartmentService;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.exception.ServiceException;

@Component
public class ManageDepartmentCapability implements AiConfirmableCapability
{
    private final IBusinessDepartmentService service;
    @Autowired public ManageDepartmentCapability(IBusinessDepartmentService service) { this.service = service; }
    @Override public String code() { return "department.save"; }
    @Override public String description()
    { return "新建部门或修改已有部门的名称、上级、排序、负责人和状态。先读取部门与人员目录取得稳定ID；老板确认后才执行。"; }
    @Override public String requiredPermission() { return "business:department:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        Map<String, Object> operation = AiSchemas.property(schema, "operation", "string", "CREATE或UPDATE");
        operation.put("enum", Arrays.asList("CREATE", "UPDATE"));
        AiSchemas.property(schema, "deptId", "number", "修改时的部门ID");
        AiSchemas.property(schema, "parentId", "number", "上级公司或部门ID");
        AiSchemas.property(schema, "deptName", "string", "部门名称");
        AiSchemas.property(schema, "orderNum", "number", "显示顺序，非负整数");
        AiSchemas.property(schema, "leaderUserId", "number", "人员目录返回的负责人用户ID，可不填");
        Map<String, Object> status = AiSchemas.property(schema, "status", "string", "0正常，1停用");
        status.put("enum", Arrays.asList("0", "1"));
        return AiSchemas.required(schema, "operation", "parentId", "deptName", "orderNum", "status");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        validate(input); return "CREATE".equals(operation(input))
            ? "在上级组织 #" + number(input.get("parentId")) + " 下新建部门“" + text(input.get("deptName")) + "”"
            : "将部门 #" + number(input.get("deptId")) + " 更新为“" + text(input.get("deptName")) + "”";
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        validate(input); SysDept dept = new SysDept();
        dept.setDeptId(number(input.get("deptId"))); dept.setParentId(number(input.get("parentId")));
        dept.setDeptName(text(input.get("deptName"))); dept.setOrderNum(integer(input.get("orderNum")));
        dept.setLeaderUserId(number(input.get("leaderUserId"))); dept.setStatus(text(input.get("status")));
        SysDept saved = "CREATE".equals(operation(input))
            ? service.createDepartment(dept, invocation.getActor().getUserName())
            : service.updateDepartment(dept, invocation.getActor().getUserName());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deptId", saved.getDeptId()); result.put("deptName", saved.getDeptName());
        result.put("parentId", saved.getParentId()); result.put("status", saved.getStatus()); return result;
    }
    private void validate(Map<String, Object> input)
    {
        String operation = operation(input);
        if (!"CREATE".equals(operation) && !"UPDATE".equals(operation)) throw new ServiceException("部门操作类型不正确");
        if ("UPDATE".equals(operation) && number(input.get("deptId")) == null) throw new ServiceException("修改部门需要部门ID");
        Integer orderNum = integer(input.get("orderNum"));
        String status = text(input.get("status"));
        if (number(input.get("parentId")) == null || text(input.get("deptName")).isEmpty() || orderNum == null)
            throw new ServiceException("部门资料不完整");
        if (orderNum < 0) throw new ServiceException("部门显示顺序不能小于0");
        if (!"0".equals(status) && !"1".equals(status)) throw new ServiceException("部门状态只能是正常或停用");
    }
    private String operation(Map<String, Object> input) { return text(input.get("operation")).toUpperCase(); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private Long number(Object value)
    { try { return value instanceof Number ? ((Number)value).longValue() : Long.valueOf(String.valueOf(value)); }
      catch (Exception ex) { return null; } }
    private Integer integer(Object value)
    { try { return value instanceof Number ? ((Number)value).intValue() : Integer.valueOf(String.valueOf(value)); }
      catch (Exception ex) { return null; } }
}
