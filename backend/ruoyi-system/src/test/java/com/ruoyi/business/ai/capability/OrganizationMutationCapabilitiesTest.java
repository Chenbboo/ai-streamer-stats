package com.ruoyi.business.ai.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.ai.capability.department.ManageDepartmentCapability;
import com.ruoyi.business.ai.capability.staff.ChangeStaffStatusCapability;
import com.ruoyi.business.ai.capability.staff.SaveStaffCostPolicyCapability;
import com.ruoyi.business.domain.BusinessStaffCostPolicy;
import com.ruoyi.business.service.IBusinessDepartmentService;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class OrganizationMutationCapabilitiesTest
{
    @Mock private IBusinessStaffService staffService;
    @Mock private IBusinessProjectService projectService;
    @Mock private IBusinessDepartmentService departmentService;
    private AiCapabilityInvocation invocation;

    @BeforeEach
    void setUp()
    {
        SysUser user = new SysUser(); user.setUserId(23L); user.setUserName("jianglan");
        AiExecutionContext actor = AiExecutionContext.from(
            new LoginUser(23L, 100L, user, Collections.singleton("*:*:*")));
        invocation = new AiCapabilityInvocation(actor, 7L, 8L, 9L);
    }

    @Test
    void staffStatusConfirmationResolvesStableIdAndDoesNotMutateAccount()
    {
        ChangeStaffStatusCapability capability = new ChangeStaffStatusCapability(staffService);
        when(staffService.listOptions()).thenReturn(Collections.singletonList(
            map("userId", 66L, "nickName", "石头")));
        Map<String, Object> input = map("staffUserId", 66L, "status", "1");

        assertEquals("停用员工账号“石头”", capability.confirmationSummary(invocation, input));
        verify(staffService, never()).changeStatus(any(), any(), any());

        capability.executeConfirmed(invocation, input);
        verify(staffService).changeStatus(66L, "1", "jianglan");
    }

    @Test
    void staffStatusRejectsUnknownPersonAndInvalidStatus()
    {
        ChangeStaffStatusCapability capability = new ChangeStaffStatusCapability(staffService);
        when(staffService.listOptions()).thenReturn(Collections.singletonList(map("userId", 66L, "nickName", "石头")));
        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation,
            map("staffUserId", 99L, "status", "1")));
        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation,
            map("staffUserId", 66L, "status", "disabled")));
        verify(staffService, never()).changeStatus(any(), any(), any());
    }

    @Test
    void costPolicyCarriesExactAmountModeCurrencyAndEffectiveDate()
    {
        SaveStaffCostPolicyCapability capability = new SaveStaffCostPolicyCapability(projectService);
        Map<String, Object> input = map("staffUserId", 66L, "costMode", "daily", "unitCost", "368.50",
            "currency", "cny", "effectiveFrom", "2026-08-18", "remark", "新核算标准");
        BusinessStaffCostPolicy saved = new BusinessStaffCostPolicy(); saved.setPolicyId(77L); saved.setUserId(66L);
        saved.setCostMode("DAILY"); saved.setUnitCost(new BigDecimal("368.50")); saved.setCurrency("CNY");
        saved.setPolicyVersion(2);
        when(projectService.saveStaffCostPolicy(any(BusinessStaffCostPolicy.class), eq(23L), eq("jianglan"), eq(true)))
            .thenReturn(saved);

        assertEquals(true, capability.confirmationSummary(invocation, input).contains("368.50 CNY"));
        Map<String, Object> result = capability.executeConfirmed(invocation, input);
        assertEquals(2, result.get("policyVersion"));
        ArgumentCaptor<BusinessStaffCostPolicy> captor = ArgumentCaptor.forClass(BusinessStaffCostPolicy.class);
        verify(projectService).saveStaffCostPolicy(captor.capture(), eq(23L), eq("jianglan"), eq(true));
        assertEquals(66L, captor.getValue().getUserId());
        assertEquals("DAILY", captor.getValue().getCostMode());
        assertEquals(new BigDecimal("368.50"), captor.getValue().getUnitCost());
        assertEquals("CNY", captor.getValue().getCurrency());
    }

    @Test
    void departmentConfirmationDoesNotWriteAndExecutionUsesExactParentAndLeader()
    {
        ManageDepartmentCapability capability = new ManageDepartmentCapability(departmentService);
        Map<String, Object> input = map("operation", "CREATE", "parentId", 100L, "deptName", "内容部",
            "orderNum", 3, "leaderUserId", 66L, "status", "0");
        assertEquals(true, capability.confirmationSummary(invocation, input).contains("内容部"));
        verify(departmentService, never()).createDepartment(any(), any());

        SysDept saved = new SysDept(); saved.setDeptId(120L); saved.setParentId(100L); saved.setDeptName("内容部"); saved.setStatus("0");
        when(departmentService.createDepartment(any(SysDept.class), eq("jianglan"))).thenReturn(saved);
        capability.executeConfirmed(invocation, input);
        ArgumentCaptor<SysDept> captor = ArgumentCaptor.forClass(SysDept.class);
        verify(departmentService).createDepartment(captor.capture(), eq("jianglan"));
        assertEquals(100L, captor.getValue().getParentId());
        assertEquals(66L, captor.getValue().getLeaderUserId());
        assertEquals(Integer.valueOf(3), captor.getValue().getOrderNum());
    }

    @Test
    void departmentCapabilityRejectsInvalidStatusNegativeOrderAndMissingUpdateId()
    {
        ManageDepartmentCapability capability = new ManageDepartmentCapability(departmentService);
        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation,
            map("operation", "CREATE", "parentId", 100L, "deptName", "内容部", "orderNum", 1, "status", "9")));
        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation,
            map("operation", "CREATE", "parentId", 100L, "deptName", "内容部", "orderNum", -1, "status", "0")));
        assertThrows(ServiceException.class, () -> capability.confirmationSummary(invocation,
            map("operation", "UPDATE", "parentId", 100L, "deptName", "内容部", "orderNum", 1, "status", "0")));
        verify(departmentService, never()).createDepartment(any(), any());
        verify(departmentService, never()).updateDepartment(any(), any());
    }

    private Map<String, Object> map(Object... values)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2)
            result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }
}
