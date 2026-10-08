package com.ruoyi.web.controller.business;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.security.filter.JwtAuthenticationTokenFilter;
import com.ruoyi.framework.web.service.TokenService;
class BusinessDisabledSessionTest {
    @Test void cachedDisabledIdentityCannotAuthenticate()throws Exception{
        TokenService tokens=mock(TokenService.class);SysUser user=new SysUser(52L);user.setStatus("1");user.setDelFlag("0");LoginUser cached=new LoginUser();cached.setUser(user);cached.setToken("test-session");when(tokens.getLoginUser(any())).thenReturn(cached);
        JwtAuthenticationTokenFilter filter=new JwtAuthenticationTokenFilter();ReflectionTestUtils.setField(filter,"tokenService",tokens);SecurityContextHolder.clearContext();
        try{filter.doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),new MockFilterChain());assertNull(SecurityContextHolder.getContext().getAuthentication());verify(tokens).delLoginUser("test-session");}finally{SecurityContextHolder.clearContext();}
    }
}
