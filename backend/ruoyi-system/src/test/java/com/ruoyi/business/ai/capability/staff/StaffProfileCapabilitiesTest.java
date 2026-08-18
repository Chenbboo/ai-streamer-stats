package com.ruoyi.business.ai.capability.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.ai.capability.read.StaffProfileCapability;
import com.ruoyi.business.domain.BusinessStaffProfile;
import com.ruoyi.business.service.IBusinessStaffService;

@ExtendWith(MockitoExtension.class)
class StaffProfileCapabilitiesTest
{
    @Mock private IBusinessStaffService service;

    @Test void createsDisabledAccountWithoutAcceptingOrReturningPassword()
    {
        when(service.createStaff(any(BusinessStaffProfile.class), eq("boss"))).thenAnswer(call -> {
            BusinessStaffProfile profile = call.getArgument(0); profile.setUserId(55L); return Collections.singletonMap("userId", 55L);
        });
        Map<String,Object> input = new LinkedHashMap<String,Object>(); input.put("userName", "newstaff");
        input.put("nickName", "新员工"); input.put("deptId", 101L); input.put("countryRegion", "CN");

        Map<String,Object> result = new CreateStaffCapability(service).executeConfirmed(invocation(), input);

        ArgumentCaptor<BusinessStaffProfile> profile = ArgumentCaptor.forClass(BusinessStaffProfile.class);
        verify(service).createStaff(profile.capture(), eq("boss")); verify(service).changeStatus(55L, "1", "boss");
        assertTrue(profile.getValue().getPassword().length() >= 16);
        assertEquals(false, result.containsKey("password")); assertEquals(true, result.get("requiresSecurePasswordSetup"));
    }

    @Test void updateOverlaysOnlyExplicitFieldsOnCurrentProfile()
    {
        BusinessStaffProfile current = new BusinessStaffProfile(); current.setUserId(55L); current.setNickName("原姓名");
        current.setDeptId(101L); current.setEmail("old@example.com"); current.setCountryRegion("CN");
        current.setEmploymentType("FULL_TIME"); current.setEmploymentStatus("ACTIVE");
        when(service.getStaffProfile(55L)).thenReturn(current); when(service.updateStaff(any(), eq("boss"))).thenReturn(Collections.emptyMap());
        Map<String,Object> input = new LinkedHashMap<String,Object>(); input.put("staffUserId", 55L); input.put("nickName", "新姓名");

        new UpdateStaffProfileCapability(service).executeConfirmed(invocation(), input);

        ArgumentCaptor<BusinessStaffProfile> profile = ArgumentCaptor.forClass(BusinessStaffProfile.class);
        verify(service).updateStaff(profile.capture(), eq("boss")); assertEquals("新姓名", profile.getValue().getNickName());
        assertEquals("old@example.com", profile.getValue().getEmail()); assertEquals(101L, profile.getValue().getDeptId());
    }

    @Test void readProfileReturnsStableIdentityAndOrganizationFields()
    {
        BusinessStaffProfile profile = new BusinessStaffProfile(); profile.setUserId(55L); profile.setUserName("newstaff");
        profile.setNickName("新员工"); profile.setDeptId(101L); when(service.getStaffProfile(55L)).thenReturn(profile);
        Map<String,Object> result = new StaffProfileCapability(service).execute(invocation(),
            Collections.<String,Object>singletonMap("staffUserId", 55L));
        assertEquals(55L, result.get("userId")); assertEquals(101L, result.get("deptId"));
    }

    private AiCapabilityInvocation invocation()
    { return new AiCapabilityInvocation(AiExecutionContext.legacy(1L, "boss", true), 2L, 3L, 4L); }
}
