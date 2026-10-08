package com.ruoyi.live.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.live.domain.LiveUpload;
import com.ruoyi.live.mapper.LiveUploadMapper;

@ExtendWith(MockitoExtension.class)
class LiveUploadServiceImplTest
{
    private static final String RESULT = "{\"type\":\"gift\",\"items\":[{\"nickname\":\"same-customer\",\"badge\":\"VIP\",\"xu\":100}]}";

    @Mock
    private LiveUploadMapper mapper;

    @InjectMocks
    private LiveUploadServiceImpl service;

    @BeforeEach
    void authenticateReviewer()
    {
        SysUser user = new SysUser();
        user.setUserName("reviewer");
        LoginUser loginUser = new LoginUser(user, Collections.<String>emptySet());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
    }

    @AfterEach
    void clearAuthentication()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void batchConfirmLoadsOnceAndReusesCustomerLookupWithinTheGroup()
    {
        Date bizDate = new Date(1785859200000L);
        LiveUpload first = giftUpload(1L, bizDate);
        LiveUpload second = giftUpload(2L, bizDate);
        when(mapper.selectLiveUploadByIds(any(Long[].class))).thenReturn(Arrays.asList(first, second));
        when(mapper.selectCustomerIdByNickname("same-customer", 10L)).thenReturn(99L);
        when(mapper.updateAiResult(any(Long.class), any(String.class), any(String.class))).thenReturn(1);

        int confirmed = service.confirmRecognizeBatch(new Long[] { 1L, 2L });

        assertEquals(2, confirmed);
        verify(mapper, times(1)).selectLiveUploadByIds(any(Long[].class));
        verify(mapper, times(1)).insertCustomerIfAbsent("same-customer", "VIP", first);
        verify(mapper, times(1)).selectCustomerIdByNickname("same-customer", 10L);
        verify(mapper, times(2)).upsertGiftRecord(any(LiveUpload.class), any(Long.class), any(Integer.class), any(Integer.class));
        verify(mapper).updateAiResult(1L, "2", RESULT);
        verify(mapper).updateAiResult(2L, "2", RESULT);
    }

    @Test
    void batchConfirmRejectsMissingUploadBeforeWritingAnyRecords()
    {
        when(mapper.selectLiveUploadByIds(any(Long[].class)))
                .thenReturn(Collections.singletonList(giftUpload(1L, new Date())));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.confirmRecognizeBatch(new Long[] { 1L, 2L }));

        assertEquals("上传记录不存在:2", error.getMessage());
    }

    private LiveUpload giftUpload(Long uploadId, Date bizDate)
    {
        LiveUpload upload = new LiveUpload();
        upload.setUploadId(uploadId);
        upload.setStreamerId(10L);
        upload.setBizDate(bizDate);
        upload.setUploadType(LiveUpload.TYPE_GIFT);
        upload.setAiStatus("1");
        upload.setAiResult(RESULT);
        return upload;
    }
}
