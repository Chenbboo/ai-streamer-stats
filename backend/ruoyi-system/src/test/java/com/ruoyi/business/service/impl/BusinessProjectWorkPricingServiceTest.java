package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.business.service.impl.BusinessProjectWorkServiceTest.row;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessProjectWorkPricingServiceTest
{
    @Mock BusinessProjectWorkMapper mapper;
    @Mock BusinessProjectMapper projectMapper;
    @Mock IBusinessAccountingService accountingService;
    @Mock PlatformTransactionManager transactionManager;
    @Spy ObjectMapper json=new ObjectMapper();
    @InjectMocks BusinessProjectWorkPricingService service;
    Map<String,Object> entry,event;
    @BeforeEach void setup(){BusinessProject p=new BusinessProject();p.setProjectId(1L);p.setStatus("CLOSED");p.setAccountingState("OPEN");p.setDeliveryPolicyVersion("SEPARATED_V1");p.setBaseCurrency("CNY");event=row("eventId",5L,"entryId",7L,"projectId",1L,"status","PENDING");entry=row("entryId",7L,"logicalEntryId",7L,"projectId",1L,"userId",30L,"bizDate","2026-03-02","workMinutes",240,"minutesPerDay",480,"revisionNo",1,"status","CONFIRMED","isCurrent","1","inputUnit","DAY","inputQuantity",new BigDecimal("0.5"));lenient().when(transactionManager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());lenient().when(mapper.selectEvent(5L)).thenReturn(event);lenient().when(mapper.selectEventForUpdate(5L)).thenReturn(event);lenient().when(projectMapper.selectProjectByIdForUpdate(1L)).thenReturn(p);lenient().when(mapper.selectEntryForUpdate(7L)).thenReturn(entry);}
    @Test void monthlyRateUsesConfirmedMinutesAndSingleFinalRounding(){assertEquals(new BigDecimal("137.93"),BusinessProjectWorkPricingService.price(240,480,row("costMode","MONTHLY","unitCost",new BigDecimal("6000"),"standardWorkDays",new BigDecimal("21.75"))));}
    @Test void hourlyRateDoesNotDependOnCalendarOrDayDisplayUnit(){assertEquals(new BigDecimal("120.00"),BusinessProjectWorkPricingService.price(240,360,row("costMode","HOURLY","unitCost",new BigDecimal("30"))));}
    @Test void changingWorkDisplayPolicyCannotChangeDailyRatePrice(){Map<String,Object> rate=row("costMode","DAILY","unitCost",new BigDecimal("200"),"rateMinutesPerDay",480);assertEquals(new BigDecimal("100.00"),BusinessProjectWorkPricingService.price(240,480,rate));assertEquals(new BigDecimal("100.00"),BusinessProjectWorkPricingService.price(240,360,rate));}
    @Test void invalidAndMissingRateNeverBecomeZero(){assertThrows(ServiceException.class,()->BusinessProjectWorkPricingService.price(240,480,row("costMode","MONTHLY","unitCost",6000,"standardWorkDays",0)));assertThrows(ServiceException.class,()->BusinessProjectWorkPricingService.price(240,480,row("costMode","FIXED_TASK","unitCost",50)));}
    @Test void missingRateIsPersistedAsPendingWithNullAmount(){service.process(5L,"pricing");ArgumentCaptor<Map<String,Object>> cost=ArgumentCaptor.forClass(Map.class);verify(mapper).upsertWorkCost(cost.capture());assertEquals("PENDING",cost.getValue().get("pricingStatus"));assertNull(cost.getValue().get("amount"));assertTrue(String.valueOf(cost.getValue().get("basisJson")).contains("MISSING_RATE"));verify(accountingService,never()).recalculatePersonnelCost(any(),any(),any());}
    @Test void overlappingRatesRequireResolution(){when(mapper.selectApplicableRates(30L,"2026-03-02")).thenReturn(Arrays.asList(row("policyId",1L),row("policyId",2L)));service.process(5L,"pricing");ArgumentCaptor<Map<String,Object>> cost=ArgumentCaptor.forClass(Map.class);verify(mapper).upsertWorkCost(cost.capture());assertTrue(String.valueOf(cost.getValue().get("basisJson")).contains("AMBIGUOUS_RATE"));assertNull(cost.getValue().get("amount"));}
    @Test void successfulPricingStoresSourceRevisionRateAndRounding(){when(mapper.selectApplicableRates(30L,"2026-03-02")).thenReturn(Collections.singletonList(row("policyId",88L,"version",3,"costMode","DAILY","unitCost",new BigDecimal("200"),"currency","CNY")));service.process(5L,"pricing");ArgumentCaptor<Map<String,Object>> cost=ArgumentCaptor.forClass(Map.class);verify(mapper).upsertWorkCost(cost.capture());assertEquals(new BigDecimal("100.00"),cost.getValue().get("amount"));assertEquals(88L,cost.getValue().get("ratePolicyId"));assertTrue(String.valueOf(cost.getValue().get("basisJson")).contains("HALF_UP"));verify(accountingService).recalculatePersonnelCost(eq(1L),any(),eq("pricing"));assertEquals("DONE",event.get("status"));}
    @Test void repeatedDoneEventDoesNotCountCostAgain(){event.put("status","DONE");service.process(5L,"pricing");verify(mapper,never()).upsertWorkCost(any());verify(accountingService,never()).recalculatePersonnelCost(any(),any(),any());}
    @Test void supersededVersionIsNotCounted(){entry.put("status","SUPERSEDED");entry.put("isCurrent","0");service.process(5L,"pricing");verify(mapper,never()).upsertWorkCost(any());assertEquals("DONE",event.get("status"));}
    @Test void correctionToZeroIsPricedWithoutInventingRate(){entry.put("workMinutes",0);service.process(5L,"pricing");ArgumentCaptor<Map<String,Object>> cost=ArgumentCaptor.forClass(Map.class);verify(mapper).upsertWorkCost(cost.capture());assertEquals(BigDecimal.ZERO.setScale(2),cost.getValue().get("amount"));assertEquals("PRICED",cost.getValue().get("pricingStatus"));}
    @Test void failedAccountingLeavesOnlyRetryableEventFailure(){when(mapper.selectApplicableRates(30L,"2026-03-02")).thenReturn(Collections.singletonList(row("policyId",88L,"version",3,"costMode","DAILY","unitCost",200,"currency","CNY")));doThrow(new ServiceException("sensitive value")).when(accountingService).recalculatePersonnelCost(any(),any(),any());service.process(5L,"pricing");ArgumentCaptor<Map<String,Object>> failed=ArgumentCaptor.forClass(Map.class);verify(mapper).recordEventFailure(failed.capture());assertEquals("核算处理失败，等待重试",failed.getValue().get("lastError"));verify(mapper,never()).finishEvent(any());}
    @Test void explicitProjectRetryIsNotLimitedByOtherProjectsPendingQueue(){BusinessProject p=new BusinessProject();p.setProjectId(1L);p.setSponsorOwnerUserId(20L);p.setAccountingState("OPEN");when(projectMapper.selectProjectById(1L)).thenReturn(p);when(mapper.selectPendingProjectEvents(1L)).thenReturn(Collections.singletonList(event));service.retryProject(1L,20L,"boss",false);verify(mapper).selectPendingProjectEvents(1L);verify(mapper,never()).selectPendingEvents();verify(mapper).upsertWorkCost(any());}
}
