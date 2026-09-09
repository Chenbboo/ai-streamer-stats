package com.ruoyi.business.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.business.service.impl.BusinessProjectWorkServiceTest.row;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.ruoyi.business.domain.*;
import com.ruoyi.business.mapper.*;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.business.support.BusinessPersonnelTotals;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.OnlineUserPermissionService;

class BusinessFlowRegressionTest {
 BusinessAccountingMapper am=mock(BusinessAccountingMapper.class);
 BusinessAccountingServiceImpl as=new BusinessAccountingServiceImpl();
 BusinessFlowMapper fm=mock(BusinessFlowMapper.class);
 BusinessProjectMapper pm=mock(BusinessProjectMapper.class);
 IBusinessProjectService ps=mock(IBusinessProjectService.class);
 ISysUserService users=mock(ISysUserService.class);
 OnlineUserPermissionService sessions=mock(OnlineUserPermissionService.class);
 BusinessFlowService flow=new BusinessFlowService();
 BusinessFlowRegressionTest(){
  ReflectionTestUtils.setField(as,"mapper",am);
  ReflectionTestUtils.setField(flow,"flows",fm);ReflectionTestUtils.setField(flow,"projects",pm);ReflectionTestUtils.setField(flow,"accounting",am);ReflectionTestUtils.setField(flow,"projectService",ps);ReflectionTestUtils.setField(flow,"users",users);ReflectionTestUtils.setField(flow,"sessions",sessions);
  when(am.selectProjectForAccountingForUpdate(1L)).thenReturn(row("projectId",1L,"mainOwnerUserId",7L,"status","ACTIVE","companyDeptId",2L,"accountingState","OPEN"));
 }
 BusinessOperatingFact fact(){BusinessOperatingFact f=new BusinessOperatingFact();f.setProjectId(1L);f.setBizDate(new Date());f.setAmount(new BigDecimal("50"));f.setDescription("Expense");f.setRequestId("request-1234567890123456");return f;}
 @Test void noSpendDoesNotCreateZeroCostFact(){as.confirmNoSpend(1L,new Date(),7L,"owner",false);verify(am).confirmNoSpend(eq(1L),any(),eq(7L),eq("owner"));verify(am,never()).insertFact(any());}
 @Test void noSpendCannotOverrideActualSpending(){when(am.selectProjectDailySpendItems(eq(1L),any())).thenReturn(Collections.singletonList(fact()));assertThrows(ServiceException.class,()->as.confirmNoSpend(1L,new Date(),7L,"owner",false));verify(am,never()).confirmNoSpend(any(),any(),any(),any());}
 @Test void noSpendRejectsWrongOwnerAndPastDay(){assertThrows(ServiceException.class,()->as.confirmNoSpend(1L,new Date(),8L,"other",false));assertThrows(ServiceException.class,()->as.confirmNoSpend(1L,java.sql.Date.valueOf(LocalDate.now().minusDays(1)),7L,"owner",false));}
 @Test void spendRetryReturnsOriginalWithoutReversingOrInserting(){BusinessOperatingFact saved=fact();saved.setFactId(44L);when(am.selectFactByIdempotencyKey("SPEND-1-7-request-1234567890123456")).thenReturn(saved);assertSame(saved,as.saveProjectDailySpend(fact(),7L,"owner",false));verify(am,never()).insertFact(any());verify(am,never()).markFactReversed(any(),any(),any());}
 @Test void changedPayloadCannotReuseSubmissionId(){BusinessOperatingFact saved=fact();saved.setAmount(new BigDecimal("100"));when(am.selectFactByIdempotencyKey(anyString())).thenReturn(saved);assertThrows(ServiceException.class,()->as.saveProjectDailySpend(fact(),7L,"owner",false));}
 @Test void roleHandoverRestoresFutureCostsAndRetainsObserverPast(){BusinessMemberDayCostServiceTest f=new BusinessMemberDayCostServiceTest();f.setup();f.member.put("memberRole","OWNER");when(f.costs.selectRolePeriods(1L)).thenReturn(Arrays.asList(row("userId",7L,"effectiveFrom","2026-08-31","memberRole","OBSERVER"),row("userId",7L,"effectiveFrom","2026-09-02","memberRole","OWNER")));assertEquals(3,f.week().size());}
 @Test void releasedPauseSkipsOnlyUnpricedDatesAndResumeIsInclusive(){BusinessMemberDayCostServiceTest f=new BusinessMemberDayCostServiceTest();f.setup();List<Map<String,Object>> priced=f.week();when(f.costs.selectCosts(1L)).thenReturn(Collections.singletonList(priced.get(1)));when(f.costs.selectCostPauses(1L)).thenReturn(Collections.singletonList(row("effectiveFrom","2026-09-01","effectiveTo","2026-09-03")));List<Map<String,Object>> result=f.week();assertEquals(4,result.size());assertTrue(result.stream().anyMatch(r->r.get("bizDate").equals("2026-09-01")));assertFalse(result.stream().anyMatch(r->r.get("bizDate").equals("2026-09-02")));assertTrue(result.stream().anyMatch(r->r.get("bizDate").equals("2026-09-03")));}
 @Test void routineOnlyCloseStillChecksPendingWork(){BusinessProjectServiceImpl service=new BusinessProjectServiceImpl();BusinessProjectWorkMapper work=mock(BusinessProjectWorkMapper.class);ReflectionTestUtils.setField(service,"mapper",pm);ReflectionTestUtils.setField(service,"workMapper",work);when(pm.selectRoutines(eq(1L),any())).thenReturn(Collections.singletonList(new BusinessProjectRoutine()));assertDoesNotThrow(()->ReflectionTestUtils.invokeMethod(service,"ensureReadyForAcceptance",1L));when(work.countPendingWork(1L)).thenReturn(1);assertThrows(ServiceException.class,()->ReflectionTestUtils.invokeMethod(service,"ensureReadyForAcceptance",1L));}
 @Test void emptyProjectCannotClose(){BusinessProjectServiceImpl service=new BusinessProjectServiceImpl();ReflectionTestUtils.setField(service,"mapper",pm);assertThrows(ServiceException.class,()->ReflectionTestUtils.invokeMethod(service,"ensureReadyForAcceptance",1L));}
 Map<String,Object> cost(String amount){return row("costPolicyVersion","MEMBER_DAYS_V1","userId",7L,"bizDate","2026-09-09","companyDeptId",2L,"currency","CNY","personnelCost",new BigDecimal(amount));}
 @Test void companyCostCountsSamePersonOnceWithoutChangingProjectRows(){List<Map<String,Object>> rows=Arrays.asList(cost("100"),cost("100"));Map<String,Object> total=BusinessPersonnelTotals.summarize(rows).get(0);assertEquals(new BigDecimal("100"),total.get("amount"));assertEquals(1,total.get("duplicateCount"));assertEquals(2,rows.size());}
 @Test void companyCostConflictIsNotSilentlyResolved(){Map<String,Object> total=BusinessPersonnelTotals.summarize(Arrays.asList(cost("100"),cost("120"))).get(0);assertNull(total.get("amount"));assertEquals(1,total.get("issueCount"));}
 @Test void companyCostDoesNotMixCurrencies(){Map<String,Object> usd=cost("20");usd.put("userId",8L);usd.put("currency","USD");assertEquals(2,BusinessPersonnelTotals.summarize(Arrays.asList(cost("100"),usd)).size());}
 @Test void departureNeedsOwnerHandoverFirst(){when(users.selectUserById(7L)).thenReturn(new SysUser(7L));when(fm.responsibilities(7L)).thenReturn(Collections.singletonList(row("projectId",1L,"ownerUserId",7L,"projectName","A","taskCount",0,"routineCount",0)));assertThrows(ServiceException.class,()->flow.requestDeparture(7L,row("effectiveDate",LocalDate.now().toString(),"reason","Leaving"),9L,"hr"));verify(fm,never()).insertDeparture(any());verify(users).checkUserDataScope(7L);}
 @Test void departureNeedsTaskReceiverFirst(){when(users.selectUserById(7L)).thenReturn(new SysUser(7L));when(fm.responsibilities(7L)).thenReturn(Collections.singletonList(row("projectId",1L,"ownerUserId",8L,"projectName","A","taskCount",1,"routineCount",0)));assertThrows(ServiceException.class,()->flow.requestDeparture(7L,row("effectiveDate",LocalDate.now().toString(),"reason","Leaving"),9L,"hr"));}
 @Test void scheduledDepartureDoesNotDisableBeforeEffectiveDate(){when(users.selectUserById(7L)).thenReturn(new SysUser(7L));flow.requestDeparture(7L,row("effectiveDate",LocalDate.now().plusDays(1).toString(),"reason","Leaving"),9L,"hr");verify(fm).insertDeparture(any());verify(users,never()).updateUserStatus(any());verifyNoInteractions(sessions);}
 @Test void effectiveDepartureStopsSessionAndEndsMemberships(){when(fm.selectDeparture(1L)).thenAnswer(i->fm.lockDeparture(1L));when(fm.lockDeparture(1L)).thenReturn(row("id",1L,"user_id",7L,"effective_date",LocalDate.now().toString(),"status","SCHEDULED","requested_by","hr"));flow.completeDeparture(1L);verify(fm).endMemberships(any());verify(fm).markDeparted(7L,"hr");verify(users).updateUserStatus(argThat(u->"1".equals(u.getStatus())));verify(sessions).forceReloginAfterCommit(7L);verify(fm).departureStatus(1L,"COMPLETED",null);}
 BusinessProject closed(){BusinessProject p=new BusinessProject();p.setProjectId(1L);p.setSponsorOwnerUserId(8L);p.setMainOwnerUserId(7L);p.setAccountingState("CLOSED");p.setDeliveryPolicyVersion("SEPARATED_V1");p.setActualEndDate(new Date());p.setBaseCurrency("CNY");return p;}
 @Test void technicalAdminCannotApproveClosedAdjustment(){when(fm.selectAdjustment(3L)).thenAnswer(i->fm.lockAdjustment(3L));when(fm.lockAdjustment(3L)).thenReturn(row("project_id",1L,"status","PENDING"));when(pm.selectProjectByIdForUpdate(1L)).thenReturn(closed());assertThrows(ServiceException.class,()->flow.reviewAdjustment(3L,row("decision","APPROVED","comment","OK"),1L,"admin"));verify(fm,never()).reviewAdjustment(any());}
 @Test void sponsorApprovesInSeparateLedgerWithoutRewritingDailyResults(){when(fm.selectAdjustment(3L)).thenAnswer(i->fm.lockAdjustment(3L));when(fm.lockAdjustment(3L)).thenReturn(row("project_id",1L,"status","PENDING"));when(pm.selectProjectByIdForUpdate(1L)).thenReturn(closed());when(fm.reviewAdjustment(any())).thenReturn(1);flow.reviewAdjustment(3L,row("decision","APPROVED","comment","verified"),8L,"boss");verify(fm).reviewAdjustment(argThat(r->"APPROVED".equals(r.get("status"))));verify(am,never()).insertFact(any());verify(am,never()).retireCurrentResult(any(),any());}
 @Test void adjustmentRejectsForeignFactAndZeroDelta(){when(pm.selectProjectByIdForUpdate(1L)).thenReturn(closed());Map<String,Object> input=row("requestId","request-1234567890123456","businessDate",LocalDate.now().toString(),"profitDelta","0","reason","correction");assertThrows(ServiceException.class,()->flow.requestAdjustment(1L,input,7L,"owner",false));input.put("profitDelta","25");input.put("originalFactId",99L);assertThrows(ServiceException.class,()->flow.requestAdjustment(1L,input,7L,"owner",false));}
}
