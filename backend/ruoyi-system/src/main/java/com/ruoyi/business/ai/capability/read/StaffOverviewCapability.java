package com.ruoyi.business.ai.capability.read;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessStaffService;

/** Deterministic people summary; details remain available through the staff directory/profile tools. */
@Component
public class StaffOverviewCapability implements AiCapability
{
    private final IBusinessStaffService service;

    @Autowired public StaffOverviewCapability(IBusinessStaffService service) { this.service = service; }
    @Override public String code() { return "staff.overview.get"; }
    @Override public String description()
    {
        return "统计当前账号有权管理的在用人员总数，并按公司和部门汇总。"
            + "如需姓名列表、个人档案或项目职责，再调用人员目录、档案或职责能力。";
    }
    @Override public String requiredPermission() { return "business:staff:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema() { return AiSchemas.object(); }

    @Override
    public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        List<Map<String, Object>> staff = service.listOptions();
        Map<String, Integer> companyCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> departmentCounts = new LinkedHashMap<String, Integer>();
        for (Map<String, Object> row : staff)
        {
            increment(companyCounts, label(row.get("companyName"), "未归属公司"));
            increment(departmentCounts, label(row.get("deptName"), "未归属部门"));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("staffCount", staff.size());
        result.put("companyCounts", companyCounts);
        result.put("departmentCounts", departmentCounts);
        return result;
    }

    private void increment(Map<String, Integer> counts, String key)
    { counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1); }
    private String label(Object value, String fallback)
    { return value == null || String.valueOf(value).trim().isEmpty() ? fallback : String.valueOf(value); }
}
