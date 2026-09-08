package com.ruoyi.web.controller.business;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.business.service.IBusinessProjectService;

class LocalLeaveRemovalTest
{
    @Test
    void retiredAiCapabilityIsNotOnTheRuntimeClasspath()
    {
        org.junit.jupiter.api.Assertions.assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.ruoyi.business.ai.capability.project.SetProjectMemberLeaveCapability"));
    }

    @Test
    void retiredLeaveEndpointsCannotCreateApproveOrCancelRequests() throws Exception
    {
        BusinessProjectController controller = new BusinessProjectController();
        IBusinessProjectService service = mock(IBusinessProjectService.class);
        ReflectionTestUtils.setField(controller,"projectService",service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/business/owner/1/member/2/leave").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/business/owner/1/member/2/leave").param("leaveDate","2026-09-08"))
            .andExpect(status().isNotFound());
        mvc.perform(put("/business/boss/leave-request/3/review").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/business/leave-request/3/cancel").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
