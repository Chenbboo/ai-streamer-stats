package com.ruoyi.business.ai.capability.department;

import java.util.ArrayList;
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
import com.ruoyi.common.exception.ServiceException;

@Component
public class SortDepartmentsCapability implements AiConfirmableCapability
{
    private final IBusinessDepartmentService service;
    @Autowired public SortDepartmentsCapability(IBusinessDepartmentService service) { this.service = service; }
    @Override public String code() { return "department.sort.update"; }
    @Override public String description()
    { return "调整多个部门的显示顺序。先读取部门目录取得真实部门ID；只提交用户明确要求调整的部门及非负顺序，确认后执行。"; }
    @Override public String requiredPermission() { return "business:department:manage"; }
    @Override @SuppressWarnings("unchecked") public Map<String, Object> inputSchema()
    {
        Map<String, Object> item = AiSchemas.object(); AiSchemas.property(item, "deptId", "number", "部门ID");
        AiSchemas.property(item, "deptName", "string", "确认卡展示的部门名称");
        AiSchemas.property(item, "orderNum", "number", "非负显示顺序"); AiSchemas.required(item, "deptId", "orderNum");
        Map<String, Object> s = AiSchemas.object(); Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("type", "array"); values.put("description", "要调整顺序的部门列表"); values.put("items", item);
        ((Map<String, Object>) s.get("properties")).put("departments", values); return AiSchemas.required(s, "departments");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return "调整 " + rows(input).size() + " 个部门的显示顺序"; }
    @Override public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    { Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("departments", rows(input)); return result; }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        List<Map<String, Object>> rows = rows(input); String[] ids = new String[rows.size()]; String[] orders = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) { ids[i] = String.valueOf(AiCapabilityInputs.number(rows.get(i).get("deptId")));
            orders[i] = String.valueOf(AiCapabilityInputs.integer(rows.get(i).get("orderNum"))); }
        service.updateSort(ids, orders); Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("updatedCount", rows.size()); result.put("status", "UPDATED"); return result;
    }
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> input)
    {
        Object value = input == null ? null : input.get("departments");
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) throw new ServiceException("请提供要调整顺序的部门");
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Object item : (List<Object>) value)
        {
            if (!(item instanceof Map)) throw new ServiceException("部门顺序数据格式不正确");
            Map<String, Object> row = new LinkedHashMap<String, Object>((Map<String, Object>) item);
            Long id = AiCapabilityInputs.number(row.get("deptId")); Integer order = AiCapabilityInputs.integer(row.get("orderNum"));
            if (id == null || order == null || order < 0) throw new ServiceException("部门ID或显示顺序不正确"); result.add(row);
        }
        return result;
    }
}
