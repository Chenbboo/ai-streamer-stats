package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.attendance.BusinessFeishuService;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.mapper.BusinessIncentiveMapper;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessIncentiveProjectCloseTest
{
    @Mock BusinessProjectMapper mapper;
    @Mock BusinessAccountingMapper accountingMapper;
    @Mock BusinessIncentiveMapper incentiveMapper;
    @Mock BusinessProjectWorkMapper workMapper;
    @Mock BusinessFeishuService feishuService;
    @Mock IBusinessAccountingService accountingService;
    @InjectMocks BusinessProjectServiceImpl service;
    private BusinessProject project;

    @BeforeEach void projectReadyForSettlement()
    {
        project = new BusinessProject(); project.setProjectId(31L); project.setVersion(7);
        project.setSponsorOwnerUserId(8L); project.setInitiatorUserId(8L); project.setMainOwnerUserId(9L);
        project.setStatus("CLOSED"); project.setDeliveryPolicyVersion("SEPARATED_V1");
        project.setAccountingState("OPEN"); project.setCostPolicyVersion("ACTUAL_WORK_V1");
        project.setActualEndDate(java.sql.Date.valueOf("2026-08-20"));
        lenient().when(mapper.selectProjectById(31L)).thenReturn(project);
        lenient().when(mapper.selectProjectByIdForUpdate(31L)).thenReturn(project);
    }

    @Test void workspaceListsPendingAwardsAsIndependentCloseBlocker()
    {
        when(incentiveMapper.countPendingAwards(31L)).thenReturn(2);
        Map<String,Object> status = service.settlementStatus(31L, 8L, false, true);
        assertEquals(2, status.get("pendingAwardCount")); assertEquals(false, status.get("canClose"));
        assertTrue(blockers(status).stream().anyMatch(row -> "PENDING_AWARD".equals(row.get("code")) && Integer.valueOf(2).equals(row.get("count"))));
        verifyNoInteractions(accountingService);
    }

    @Test void closeRechecksAwardsAfterAcquiringProjectLock()
    {
        when(incentiveMapper.countPendingAwards(31L)).thenReturn(0, 1);
        assertEquals(true, service.settlementStatus(31L, 8L, false, true).get("canClose"));
        assertThrows(ServiceException.class, () -> service.closeAccounting(31L, 7, "准备关账", 8L, "sponsor", true));
        InOrder order = inOrder(mapper, incentiveMapper);
        order.verify(incentiveMapper).countPendingAwards(31L);
        order.verify(mapper).selectProjectByIdForUpdate(31L);
        order.verify(incentiveMapper).countPendingAwards(31L);
        verifyNoInteractions(accountingService); verify(mapper, never()).closeAccounting(anyLong(), anyInt(), anyString());
    }

    @Test void closedOrCanceledAwardsStopBlockingButPendingCostsStillBlock()
    {
        when(incentiveMapper.countPendingAwards(31L)).thenReturn(0);
        when(accountingMapper.countProjectUnsettledFacts(31L)).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.closeAccounting(31L, 7, "准备关账", 8L, "sponsor", true));
        Map<String,Object> status = service.settlementStatus(31L, 8L, false, true);
        assertEquals(0, status.get("pendingAwardCount")); assertEquals(1, status.get("pendingFactCount"));
        assertTrue(blockers(status).stream().anyMatch(row -> "PENDING_FACT".equals(row.get("code"))));
        verifyNoInteractions(accountingService);
    }

    @Test void sponsorCanCloseAfterRewardAndCostQueuesAreSettled()
    {
        when(mapper.closeAccounting(31L, 7, "sponsor")).thenReturn(1);
        Map<String,Object> closed = service.closeAccounting(31L, 7, "奖励和成本均已结清", 8L, "sponsor", true);
        assertEquals("CLOSED", closed.get("accountingState")); assertEquals(8, closed.get("version"));
        InOrder order = inOrder(mapper, incentiveMapper, accountingService);
        order.verify(mapper).selectProjectByIdForUpdate(31L);
        order.verify(incentiveMapper).countPendingAwards(31L);
        order.verify(accountingService).closeProjectAccounting(31L, project.getActualEndDate(), "sponsor");
        order.verify(mapper).closeAccounting(31L, 7, "sponsor");
    }

    @Test void projectOwnerOrTechnicalAdministratorCannotSignSponsorClosure()
    {
        assertThrows(ServiceException.class, () -> service.closeAccounting(31L, 7, "管理员准备关账", 1L, "admin", true));
        assertThrows(ServiceException.class, () -> service.closeAccounting(31L, 7, "负责人准备关账", 9L, "owner", false));
        assertThrows(ServiceException.class, () -> service.closeAccounting(31L, 7, "其他老板准备关账", 12L, "foreign-boss", true));
        verifyNoInteractions(accountingService); verifyNoInteractions(incentiveMapper);
    }

    @Test void staleProjectVersionCannotCloseEvenWhenQueuesLookSettled()
    {
        assertThrows(ServiceException.class, () -> service.closeAccounting(31L, 6, "旧页面准备关账", 8L, "sponsor", true));
        verifyNoInteractions(accountingService); verifyNoInteractions(incentiveMapper);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> blockers(Map<String,Object> status)
    {
        return (List<Map<String,Object>>) status.get("blockers");
    }
}
