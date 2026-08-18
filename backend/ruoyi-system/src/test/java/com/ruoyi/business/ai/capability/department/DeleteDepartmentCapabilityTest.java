package com.ruoyi.business.ai.capability.department;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.service.IBusinessDepartmentService;
import com.ruoyi.common.core.domain.entity.SysDept;

@ExtendWith(MockitoExtension.class)
class DeleteDepartmentCapabilityTest
{
    @Mock private IBusinessDepartmentService service;

    @Test
    void resolvesRealDepartmentBeforePreparingAndExecutingRemoval()
    {
        SysDept dept = new SysDept(); dept.setDeptId(108L); dept.setDeptName("测试部门");
        when(service.listDepartments(any())).thenReturn(Collections.singletonList(dept));
        DeleteDepartmentCapability capability = new DeleteDepartmentCapability(service);
        Map<String, Object> input = new LinkedHashMap<String, Object>(); input.put("deptId", 108L);
        AiCapabilityInvocation invocation = new AiCapabilityInvocation(
            AiExecutionContext.legacy(23L, "jianglan", true), 1L, 2L, 3L);

        assertEquals("删除空部门“测试部门”", capability.confirmationSummary(invocation, input));
        Map<String, Object> result = capability.executeConfirmed(invocation, input);

        assertEquals("REMOVED", result.get("status"));
        verify(service).deleteDepartment(108L);
    }

    @Test
    void departmentSortUsesStructuredIdsAndOrderNumbers()
    {
        SortDepartmentsCapability capability = new SortDepartmentsCapability(service);
        Map<String, Object> first = new LinkedHashMap<String, Object>(); first.put("deptId", 108L); first.put("orderNum", 1);
        Map<String, Object> second = new LinkedHashMap<String, Object>(); second.put("deptId", 109L); second.put("orderNum", 2);
        Map<String, Object> input = new LinkedHashMap<String, Object>(); input.put("departments", Arrays.asList(first, second));
        AiCapabilityInvocation invocation = new AiCapabilityInvocation(
            AiExecutionContext.legacy(23L, "jianglan", true), 1L, 2L, 3L);

        Map<String, Object> result = capability.executeConfirmed(invocation, input);

        assertEquals(2, result.get("updatedCount"));
        verify(service).updateSort(aryEq(new String[] { "108", "109" }), aryEq(new String[] { "1", "2" }));
    }
}
