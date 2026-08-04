package com.ruoyi.live.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.live.domain.LiveCustomer;
import com.ruoyi.live.mapper.LiveCustomerMapper;

@ExtendWith(MockitoExtension.class)
class LiveCustomerServiceImplTest
{
    @Mock
    private LiveCustomerMapper mapper;

    @InjectMocks
    private LiveCustomerServiceImpl service;

    @Test
    void cannotMergeCustomersFromDifferentStreamers()
    {
        when(mapper.selectCustomerById(1L)).thenReturn(customer(1L, 10L, "primary"));
        when(mapper.selectCustomerById(2L)).thenReturn(customer(2L, 20L, "secondary"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.mergeCustomers(1L, 2L));

        assertTrue(error.getMessage().contains("同一主播"));
        verify(mapper, never()).mergeGiftRecords(anyLong(), anyLong());
    }

    @Test
    void mergedCustomerCannotBeMergedAgain()
    {
        LiveCustomer primary = customer(1L, 10L, "primary");
        LiveCustomer secondary = customer(2L, 10L, "secondary");
        secondary.setMergedIntoId(3L);
        when(mapper.selectCustomerById(1L)).thenReturn(primary);
        when(mapper.selectCustomerById(2L)).thenReturn(secondary);

        ServiceException error = assertThrows(ServiceException.class, () -> service.mergeCustomers(1L, 2L));

        assertTrue(error.getMessage().contains("不能再次参与合并"));
        verify(mapper, never()).mergeGiftRecords(anyLong(), anyLong());
    }

    @Test
    void mergeMovesEveryCustomerDataSourceAndKeepsAliasMetadata()
    {
        LiveCustomer primary = customer(1L, 10L, "primary");
        LiveCustomer secondary = customer(2L, 10L, "old-name");
        Date firstSeen = new Date(1000L);
        Date lastSeen = new Date(2000L);
        secondary.setFirstSeenDate(firstSeen);
        secondary.setLastSeenDate(lastSeen);
        when(mapper.selectCustomerById(1L)).thenReturn(primary);
        when(mapper.selectCustomerById(2L)).thenReturn(secondary);

        service.mergeCustomers(1L, 2L);

        verify(mapper).mergeGiftRecords(2L, 1L);
        verify(mapper).deleteGiftRecords(2L);
        verify(mapper).mergeChatContacts(2L, 1L);
        verify(mapper).deleteChatContacts(2L);
        verify(mapper).mergeFollowRecords(2L, 1L);
        verify(mapper).deleteFollowRecords(2L);
        verify(mapper).updateChatMessageCustomerId(2L, 1L);
        verify(mapper).moveAliases(2L, 1L, 10L);
        verify(mapper).insertAlias(1L, 10L, "old-name", "merge", firstSeen, lastSeen);
        verify(mapper).mergeCustomerMetadata(1L, 2L);
        verify(mapper).markCustomerMerged(2L, 1L);
    }

    private LiveCustomer customer(Long id, Long streamerId, String nickname)
    {
        LiveCustomer customer = new LiveCustomer();
        customer.setCustomerId(id);
        customer.setStreamerId(streamerId);
        customer.setNickname(nickname);
        return customer;
    }
}
