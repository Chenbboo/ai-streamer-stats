package com.ruoyi.framework.security.filter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.common.core.domain.model.LoginUser;

/**
 * /profile 中其他头像等资源保持原有行为，仅对公司经营附件做登录与项目范围校验。
 */
@Component
public class BusinessAttachmentSecurityFilter extends OncePerRequestFilter
{
    @Autowired
    private BusinessFileService businessFileService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException
    {
        if (!HttpMethod.GET.matches(request.getMethod()))
        {
            chain.doFilter(request, response);
            return;
        }
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String uri = businessFileService.normalizeResourceUrl(URLDecoder.decode(
            request.getRequestURI().substring(contextPath.length()), StandardCharsets.UTF_8.name()));
        if (!businessFileService.isProtectedResource(uri))
        {
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser))
        {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "请登录后查看附件");
            return;
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userId = loginUser.getUserId();
        boolean allowed = businessFileService.canAccessResource(uri, userId, false, 1L == userId);
        if (!allowed)
        {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "无权查看该项目附件");
            return;
        }
        response.setHeader("Cache-Control", "private, no-store");
        chain.doFilter(request, response);
    }
}
