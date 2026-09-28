package com.ruoyi.web.controller.business;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Collections;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;

class BusinessOwnerSpendHistoryControllerTest
{
    IBusinessProjectService service;
    MockMvc mvc;

    @BeforeEach void setup()
    {
        service=mock(IBusinessProjectService.class);
        BusinessProjectController controller=new BusinessProjectController();
        ReflectionTestUtils.setField(controller,"projectService",service);
        mvc=MockMvcBuilders.standaloneSetup(controller).build();
        LoginUser user=new LoginUser(23L,100L,new SysUser(23L),Collections.singleton("business:project:owner:view"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,Collections.emptyList()));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}

    @Test void completeMonthUrlMatchesControllerPrefixAndPassesAuthenticatedOwner() throws Exception
    {
        when(service.ownerSpendHistory(13L,"2026-09",null,23L,false)).thenReturn(Collections.singletonMap("rows",Collections.emptyList()));
        mvc.perform(get("/business/owner/spend-history/13").param("month","2026-09"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.rows").isArray());
        verify(service).ownerSpendHistory(13L,"2026-09",null,23L,false);
    }

    @Test void completeDateUrlPassesSingleDateWithoutMonth() throws Exception
    {
        when(service.ownerSpendHistory(13L,null,"2026-09-28",23L,false)).thenReturn(Collections.emptyMap());
        mvc.perform(get("/business/owner/spend-history/13").param("bizDate","2026-09-28"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).ownerSpendHistory(13L,null,"2026-09-28",23L,false);
    }

    @Test void extraProjectPrefixDoesNotAccidentallyMatchAnotherEndpoint() throws Exception
    {
        mvc.perform(get("/business/project/owner/spend-history/13").param("month","2026-09"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
