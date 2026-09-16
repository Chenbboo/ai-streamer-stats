package com.ruoyi.business.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.*;
import com.ruoyi.business.mapper.BusinessProjectMapper;

class BusinessStaffMutationScopeTest {
    private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

    BusinessStaffServiceImpl service=new BusinessStaffServiceImpl();
    ISysUserService users=mock(ISysUserService.class);OnlineUserPermissionService sessions=mock(OnlineUserPermissionService.class);
    @BeforeEach void setup(){ReflectionTestUtils.setField(service,"userService",users);ReflectionTestUtils.setField(service,"onlinePermissions",sessions);ReflectionTestUtils.setField(service,"projectMapper",mock(BusinessProjectMapper.class));SysUser user=new SysUser(52L);user.setDelFlag("0");when(users.selectUserById(52L)).thenReturn(user);
        companyAccess=com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture();
        org.springframework.test.util.ReflectionTestUtils.setField(service,"companyAccess",companyAccess);
}
    @Test void targetOutsideScopeCannotBeDisabledOrPasswordReset(){doThrow(new ServiceException("无权操作")).when(companyAccess).requireStaff(52L);assertThrows(ServiceException.class,()->service.changeStatus(52L,"1","actor"));assertThrows(ServiceException.class,()->service.resetPassword(52L,"Test9876","actor"));verify(users,never()).updateUserStatus(any());verify(users,never()).resetPwd(any());}
    @Test void disablingRevokesEverySessionAfterCommit(){when(users.updateUserStatus(any())).thenReturn(1);service.changeStatus(52L,"1","actor");verify(companyAccess).requireStaff(52L);verify(sessions).forceReloginAfterCommit(52L);}
    @Test void passwordResetRevokesSessions(){when(users.resetPwd(any())).thenReturn(1);service.resetPassword(52L,"Test9876","actor");verify(sessions).forceReloginAfterCommit(52L);}
}
