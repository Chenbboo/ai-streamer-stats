package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.BusinessIncentiveAward;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.mapper.BusinessIncentiveMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.common.exception.ServiceException;

/** Cross-domain tests: a reward approval and an accounting confirmation are separate sign-offs. */
@ExtendWith(MockitoExtension.class)
class BusinessIncentiveAccountingTest
{
    private static final Long PROJECT = 31L, SPONSOR = 8L, OWNER = 9L, FACT = 501L, AWARD = 701L;
    private static final Date DAY = java.sql.Date.valueOf("2026-08-20");
    @Mock BusinessAccountingMapper mapper;
    @Mock BusinessIncentiveMapper incentiveMapper;
    @Mock BusinessProjectWorkMapper workMapper;
    @Mock BusinessFileService businessFileService;
    @InjectMocks BusinessAccountingServiceImpl service;
    private Map<String,Object> project;
    private BusinessOperatingFact fact;
    private BusinessIncentiveAward award;

    @BeforeEach void fixture()
    {
        project = new HashMap<String,Object>();
        project.put("projectId", PROJECT); project.put("initiatorUserId", SPONSOR);
        project.put("mainOwnerUserId", OWNER); project.put("companyDeptId", 100L);
        project.put("status", "CLOSED"); project.put("deliveryPolicyVersion", "SEPARATED_V1");
        project.put("accountingState", "OPEN"); project.put("costPolicyVersion", "ACTUAL_WORK_V1");
        project.put("actualEndDate", DAY); project.put("currency", "CNY");
        fact = fact(); award = award();
        lenient().when(mapper.selectProjectForAccountingForUpdate(PROJECT)).thenReturn(project);
        lenient().when(mapper.selectFactById(FACT)).thenAnswer(call -> fact);
        lenient().when(mapper.selectFactByIdForUpdate(FACT)).thenAnswer(call -> fact);
        lenient().when(incentiveMapper.selectAwardForUpdate(AWARD)).thenAnswer(call -> award);
    }

    @Test void applicantCannotConfirmRewardCostEvenWithGlobalVisibility()
    {
        rejectAllCostActions(OWNER, true);
    }

    @Test void factCreatorCannotConfirmRewardCostEvenWithGlobalVisibility()
    {
        fact.setCreateUserId(10L);
        rejectAllCostActions(10L, true);
    }

    @Test void technicalAdministratorCannotReplaceSponsorSignOff()
    {
        rejectAllCostActions(1L, true);
    }

    @Test void unrelatedCompanyBossCannotConfirmRewardCost()
    {
        rejectAllCostActions(12L, false);
    }

    @Test void sponsorConfirmsMatchingApprovedRewardAndRetriesWithoutDuplicatingCost()
    {
        when(mapper.sumProjectFacts(PROJECT, DAY)).thenReturn(Collections.emptyMap());
        when(mapper.confirmFact(FACT, SPONSOR, "sponsor", 2)).thenAnswer(call -> {
            fact.setStatus("CONFIRMED"); fact.setVersion(3); return 1;
        });

        assertSame(fact, service.confirmFact(FACT, SPONSOR, "sponsor", false));
        assertSame(fact, service.confirmFact(FACT, SPONSOR, "sponsor", false));

        verify(mapper, times(1)).confirmFact(FACT, SPONSOR, "sponsor", 2);
        verify(mapper, times(1)).insertDailyResult(anyMap());
        verify(mapper, never()).insertFact(any());
        InOrder locks = inOrder(mapper, incentiveMapper);
        locks.verify(mapper).selectProjectForAccountingForUpdate(PROJECT);
        locks.verify(mapper).selectFactByIdForUpdate(FACT);
        locks.verify(incentiveMapper).selectAwardForUpdate(AWARD);
        locks.verify(mapper).confirmFact(FACT, SPONSOR, "sponsor", 2);
    }

    @Test void confirmedCostRetryStillValidatesItsCurrentApproval()
    {
        fact.setStatus("CONFIRMED"); award.setStatus("CANCELED");
        assertThrows(ServiceException.class, () -> service.confirmFact(FACT, SPONSOR, "sponsor", false));
        verifyNoCostWrites();
    }

    @Test void missingOrUnapprovedRewardCannotBecomeCost()
    {
        for (String state : Arrays.asList("DRAFT", "SUBMITTED", "RETURNED", "CANCELED"))
        {
            award.setStatus(state);
            assertThrows(ServiceException.class, () -> service.confirmFact(FACT, SPONSOR, "sponsor", false), state);
        }
        award = null;
        assertThrows(ServiceException.class, () -> service.confirmFact(FACT, SPONSOR, "sponsor", false));
        verifyNoCostWrites();
    }

    @Test void malformedRewardSourceDoesNotReachApprovalLookup()
    {
        for (String source : Arrays.asList(null, "", "not-an-award", "1.5"))
        {
            fact.setSourceId(source);
            assertThrows(ServiceException.class, () -> service.confirmFact(FACT, SPONSOR, "sponsor", false));
        }
        verifyNoInteractions(incentiveMapper); verifyNoCostWrites();
    }

    @Test void wrongProjectOrCostLinkCannotBorrowAnotherApproval()
    {
        award.setProjectId(32L);
        assertThrows(ServiceException.class, () -> service.confirmFact(FACT, SPONSOR, "sponsor", false));
        award.setProjectId(PROJECT); award.setAccountingFactId(502L);
        assertThrows(ServiceException.class, () -> service.confirmFact(FACT, SPONSOR, "sponsor", false));
        verifyNoCostWrites();
    }

    @Test void wrongAmountCurrencyOrSourceTypeCannotBeConfirmed()
    {
        rejectChangedFact(value -> value.setAmount(new BigDecimal("501.00")));
        rejectChangedFact(value -> value.setAmount(null));
        rejectChangedFact(value -> value.setCurrency("VND"));
        rejectChangedFact(value -> value.setSourceType("MANUAL"));
    }

    @Test void changedDateCompanyOrCategoryCannotBeConfirmed()
    {
        rejectChangedFact(value -> value.setBizDate(java.sql.Date.valueOf("2026-08-19")));
        rejectChangedFact(value -> value.setCompanyDeptId(101L));
        rejectChangedFact(value -> value.setFactKind("REVENUE"));
        rejectChangedFact(value -> value.setCategoryCode("OTHER_COST"));
    }

    @Test void changedSourceLineOrIdempotencyKeyCannotBeConfirmed()
    {
        rejectChangedFact(value -> value.setSourceLineKey("OTHER"));
        rejectChangedFact(value -> value.setIdempotencyKey("HR-INCENTIVE-AWARD-702"));
    }

    @Test void ordinaryDraftEditorCannotChangeProtectedRewardCost()
    {
        Map<String,Object> category = new HashMap<String,Object>();
        category.put("factKind", "COST"); category.put("categoryCode", "PROJECT_BONUS_COST");
        category.put("categoryName", "项目奖励");
        when(mapper.selectCategoryById(17L)).thenReturn(category);
        for (String state : Arrays.asList("DRAFT", "RETURNED"))
        {
            fact.setStatus(state);
            BusinessOperatingFact edited = fact(); edited.setAmount(new BigDecimal("1000"));
            assertThrows(ServiceException.class, () -> service.saveFact(edited, SPONSOR, "sponsor", false));
        }
        verify(mapper, never()).updateDraftFact(any()); verifyNoCostWrites();
    }

    @Test void ordinaryCreateCannotForgeRewardSourceOrItsUniqueKey()
    {
        Map<String,Object> category = new HashMap<String,Object>();
        category.put("factKind", "COST"); category.put("categoryCode", "OTHER_COST");
        category.put("categoryName", "其他成本");
        when(mapper.selectCategoryById(17L)).thenReturn(category);
        BusinessOperatingFact forged = fact(); forged.setFactId(null);
        service.saveFact(forged, SPONSOR, "sponsor", false);
        assertEquals("MANUAL", forged.getSourceDomain()); assertEquals("MANUAL", forged.getSourceType());
        assertTrue(forged.getIdempotencyKey().startsWith("MANUAL-"));
        assertEquals("DRAFT", forged.getStatus());
        verify(mapper).insertFact(forged); verifyNoInteractions(incentiveMapper);
    }

    @Test void sponsorCanReturnCostWithoutChangingApprovedReward()
    {
        when(mapper.returnFact(FACT, "补充依据", SPONSOR, "sponsor", 2)).thenReturn(1);
        service.returnFact(FACT, "补充依据", SPONSOR, "sponsor", false);
        verify(mapper).returnFact(FACT, "补充依据", SPONSOR, "sponsor", 2);
        assertEquals("APPROVED", award.getStatus());
        verify(incentiveMapper).selectAwardForUpdate(AWARD);
        verifyNoMoreInteractions(incentiveMapper);
    }

    @Test void returnedCostMustUseRewardResubmissionBeforeConfirmation()
    {
        fact.setStatus("RETURNED");
        assertThrows(ServiceException.class, () -> service.confirmFact(FACT, SPONSOR, "sponsor", false));
        verifyNoCostWrites();
    }

    @Test void sponsorCanReverseConfirmedCostWithExplicitAuditRecord()
    {
        fact.setStatus("CONFIRMED");
        when(mapper.markFactReversed(FACT, "sponsor", 2)).thenReturn(1);
        when(mapper.sumProjectFacts(PROJECT, DAY)).thenReturn(Collections.emptyMap());
        BusinessOperatingFact reversal = service.reverseFact(FACT, "核准记录更正，保留原始核准单", SPONSOR, "sponsor", false);
        assertEquals("REVERSAL", reversal.getSourceDomain());
        assertEquals(FACT, reversal.getReversalFactId());
        assertEquals(new BigDecimal("-500.00"), reversal.getAmount());
        assertEquals("APPROVED", award.getStatus());
        verify(mapper).insertFact(reversal);
    }

    @Test void accountingClosureBlocksAllRewardCostChanges()
    {
        project.put("accountingState", "CLOSED");
        rejectAllCostActions(SPONSOR, false);
    }

    private void rejectChangedFact(Consumer<BusinessOperatingFact> change)
    {
        fact = fact(); change.accept(fact);
        assertThrows(ServiceException.class, () -> service.confirmFact(FACT, SPONSOR, "sponsor", false));
        verifyNoCostWrites();
    }

    private void rejectAllCostActions(Long actor, boolean viewAll)
    {
        assertThrows(ServiceException.class, () -> service.confirmFact(FACT, actor, "actor", viewAll));
        assertThrows(ServiceException.class, () -> service.returnFact(FACT, "退回核对", actor, "actor", viewAll));
        fact.setStatus("CONFIRMED");
        assertThrows(ServiceException.class, () -> service.reverseFact(FACT, "更正冲销", actor, "actor", viewAll));
        verifyNoCostWrites();
    }

    private void verifyNoCostWrites()
    {
        verify(mapper, never()).confirmFact(anyLong(), anyLong(), anyString(), anyInt());
        verify(mapper, never()).returnFact(anyLong(), anyString(), anyLong(), anyString(), anyInt());
        verify(mapper, never()).markFactReversed(anyLong(), anyString(), anyInt());
        verify(mapper, never()).insertFact(any()); verify(mapper, never()).insertDailyResult(anyMap());
    }

    private BusinessOperatingFact fact()
    {
        BusinessOperatingFact value = new BusinessOperatingFact();
        value.setFactId(FACT); value.setProjectId(PROJECT); value.setCompanyDeptId(100L);
        value.setCategoryId(17L); value.setCategoryCode("PROJECT_BONUS_COST"); value.setFactKind("COST");
        value.setBizDate(DAY); value.setAmount(new BigDecimal("500.00")); value.setCurrency("CNY");
        value.setSourceDomain("HR_INCENTIVE"); value.setSourceType("BONUS");
        value.setSourceId(String.valueOf(AWARD)); value.setSourceLineKey("AWARD");
        value.setIdempotencyKey("HR-INCENTIVE-AWARD-" + AWARD);
        value.setStatus("DRAFT"); value.setVersion(2); value.setCreateUserId(OWNER);
        value.setDescription("奖励核准生成待确认成本"); return value;
    }

    private BusinessIncentiveAward award()
    {
        BusinessIncentiveAward value = new BusinessIncentiveAward();
        value.setAwardId(AWARD); value.setProjectId(PROJECT); value.setCompanyDeptId(100L);
        value.setAccountingFactId(FACT); value.setStatus("APPROVED"); value.setBizDate(DAY);
        value.setAmount(new BigDecimal("500.00")); value.setCurrency("CNY");
        value.setApplicantUserId(OWNER); value.setApprovedUserId(SPONSOR); return value;
    }
}
