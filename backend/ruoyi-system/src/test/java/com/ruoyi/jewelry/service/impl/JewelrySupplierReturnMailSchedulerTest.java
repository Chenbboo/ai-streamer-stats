package com.ruoyi.jewelry.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;

import static org.mockito.Mockito.mock;

class JewelrySupplierReturnMailSchedulerTest
{
    private final JewelryErpMapper mapper = mock(JewelryErpMapper.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);

    @Test
    void disabledOrIncompleteConfigurationNeverQueriesOrSends()
    {
        scheduler(false, "recipient@example.com", "auth-code").sendDaily();
        scheduler(true, "", "auth-code").sendDaily();
        scheduler(true, "recipient@example.com", "").sendDaily();
        verify(mapper, never()).selectStockList(any());
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void emptyWarningListDoesNotSendOrClaim()
    {
        when(provider.getIfAvailable()).thenReturn(sender);
        when(mapper.selectStockList(any())).thenReturn(Arrays.asList());

        scheduler(true, "recipient@example.com", "auth-code").sendDaily();

        verify(mapper, never()).claimSupplierReturnMail(any(), anyString(), anyInt());
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendsOnlyClaimedWarningDigestToConfiguredRecipients()
    {
        when(provider.getIfAvailable()).thenReturn(sender);
        when(mapper.selectStockList(any())).thenReturn(Arrays.asList(warning()));
        when(mapper.claimSupplierReturnMail(any(), anyString(), anyInt())).thenReturn(1);

        scheduler(true, "first@example.com, second@example.com", "auth-code").sendDaily();

        ArgumentCaptor<Map<String, Object>> query = ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectStockList(query.capture());
        assertTrue(Boolean.TRUE.equals(query.getValue().get("warningOnly")));
        assertTrue("supplierReturn".equals(query.getValue().get("warningType")));
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        assertTrue(mail.getValue().getText().contains("SKU-1"));
        assertTrue(mail.getValue().getText().contains("采购单-1"));
        assertTrue(mail.getValue().getTo().length == 2);
        verify(mapper).markSupplierReturnMailSent(any(LocalDate.class));
    }

    @Test
    void duplicateClaimDoesNotSend()
    {
        when(provider.getIfAvailable()).thenReturn(sender);
        when(mapper.selectStockList(any())).thenReturn(Arrays.asList(warning()));
        when(mapper.claimSupplierReturnMail(any(), anyString(), anyInt())).thenReturn(0);

        scheduler(true, "recipient@example.com", "auth-code").sendDaily();

        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendFailureIsRecordedWithoutRetrying()
    {
        when(provider.getIfAvailable()).thenReturn(sender);
        when(mapper.selectStockList(any())).thenReturn(Arrays.asList(warning()));
        when(mapper.claimSupplierReturnMail(any(), anyString(), anyInt())).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("secret"))
            .when(sender).send(any(SimpleMailMessage.class));

        scheduler(true, "recipient@example.com", "auth-code").sendDaily();

        verify(mapper).markSupplierReturnMailFailed(any(LocalDate.class),
            org.mockito.ArgumentMatchers.eq("IllegalStateException"));
    }

    private JewelrySupplierReturnMailScheduler scheduler(boolean enabled, String recipients, String authCode)
    {
        return new JewelrySupplierReturnMailScheduler(mapper, provider, enabled, "sender@163.com", recipients,
            authCode);
    }

    private Map<String, Object> warning()
    {
        Map<String, Object> row = new HashMap<>();
        row.put("sku", "SKU-1");
        row.put("productName", "测试成品");
        row.put("onHandQty", 12);
        row.put("supplierReturnDate", "2026-09-25");
        row.put("supplierReturnDays", 5);
        row.put("supplierReturnDocNo", "采购单-1");
        return row;
    }
}
