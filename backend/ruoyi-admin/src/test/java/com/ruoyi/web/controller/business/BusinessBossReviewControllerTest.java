package com.ruoyi.web.controller.business;

import static org.mockito.Mockito.*;
import java.util.Collections;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;

class BusinessBossReviewControllerTest {
    BusinessAccountingController controller=new BusinessAccountingController();
    IBusinessAccountingService service=mock(IBusinessAccountingService.class);
    @BeforeEach void setup(){ReflectionTestUtils.setField(controller,"service",service);actor(126L);}
    void actor(Long id){LoginUser user=new LoginUser(id,100L,new SysUser(id),Collections.emptySet());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,Collections.emptyList()));}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void noDateUsesTheDefaultTodayScope(){controller.bossOverview(null,null);verify(service).bossOverview(null,null,126L,false);verifyNoMoreInteractions(service);}
    @Test void reviewDateIsPassedWithAuthenticatedScope(){controller.bossOverview("yesterday",null);verify(service).bossOverview("yesterday",null,126L,false);verifyNoMoreInteractions(service);}
    @Test void administratorScopeAndCompanyFilterComeFromRequest(){actor(1L);controller.bossOverview("2026-09-11",111L);verify(service).bossOverview("2026-09-11",111L,1L,true);}
}
