package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.*;
import com.ruoyi.business.mapper.*;
import com.ruoyi.common.exception.ServiceException;

class BusinessBonusDistributionServiceTest
{
 BusinessBonusDistributionMapper mapper=mock(BusinessBonusDistributionMapper.class);
 BusinessIncentiveMapper awards=mock(BusinessIncentiveMapper.class);
 BusinessProjectMapper projects=mock(BusinessProjectMapper.class);
 BusinessBonusDistributionService service=new BusinessBonusDistributionService();
 BusinessProject project;BusinessIncentiveAward award;BusinessBonusAllocation batch;BusinessBonusAllocationLine line;
 @BeforeEach void setup(){
  ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"awards",awards);ReflectionTestUtils.setField(service,"projects",projects);
  project=new BusinessProject();project.setProjectId(1L);project.setMainOwnerUserId(10L);project.setSponsorOwnerUserId(20L);project.setCompanyDeptId(100L);project.setBaseCurrency("CNY");project.setDelFlag("0");
  when(projects.selectProjectById(1L)).thenReturn(project);when(projects.selectProjectByIdForUpdate(1L)).thenReturn(project);
  award=new BusinessIncentiveAward();award.setAwardId(2L);award.setProjectId(1L);award.setAmount(new BigDecimal("100.00"));award.setStatus("APPROVED");award.setCostStatus("CONFIRMED");award.setCurrency("CNY");award.setRuleName("KPI bonus");
  when(awards.selectAward(2L)).thenReturn(award);when(awards.selectAwardForUpdate(2L)).thenReturn(award);when(awards.selectAwards(1L)).thenReturn(Arrays.asList(award));
  when(mapper.recipients(1L)).thenReturn(Arrays.asList(person(30L,"Alice"),person(40L,"Bob")));
  when(mapper.reserved(eq(2L),any())).thenReturn(BigDecimal.ZERO);
  batch=new BusinessBonusAllocation();batch.setAllocationId(3L);batch.setProjectId(1L);batch.setAwardId(2L);batch.setCreatedUserId(10L);batch.setStatus("DRAFT");batch.setMode("AMOUNT");batch.setReason("allocation");batch.setVersion(0);batch.setAmount(new BigDecimal("60.00"));batch.setApprovedTime(new Date());
  when(mapper.allocation(3L)).thenReturn(batch);when(mapper.transition(anyLong(),anyInt(),anyString(),anyLong(),anyString())).thenReturn(1);
  line=line(30L,"60.00");line.setLineId(4L);line.setAllocationId(3L);line.setPaidAmount(new BigDecimal("20.00"));
  when(mapper.line(4L)).thenReturn(line);when(mapper.lines(3L)).thenReturn(Arrays.asList(line));
  when(mapper.companyAccess(1L,50L)).thenReturn(1);
 }
 Map<String,Object> person(Long id,String name){Map<String,Object> m=new HashMap<>();m.put("userId",id);m.put("userName",name);return m;}
 BusinessBonusAllocationLine line(Long user,String amount){BusinessBonusAllocationLine l=new BusinessBonusAllocationLine();l.setUserId(user);l.setUserName("client spoof");l.setAmount(new BigDecimal(amount));l.setReason("contribution");return l;}
 BusinessBonusAllocation draft(){BusinessBonusAllocation b=new BusinessBonusAllocation();b.setAwardId(2L);b.setMode("AMOUNT");b.setReason("allocation");b.setRequestKey("request1");b.setLines(Arrays.asList(line(30L,"60.00")));return b;}
 BusinessBonusPayment payment(){BusinessBonusPayment p=new BusinessBonusPayment();p.setLineId(4L);p.setAmount(new BigDecimal("30.00"));p.setPaidDate(new Date());p.setMethod("BANK");p.setReferenceNo("bank1");p.setVoucher("/profile/upload/2026/09/proof.pdf");p.setReason("paid");p.setRequestKey("pay1");return p;}
 @Test void onlyActualOwnerCanAllocateIncludingAdministrator(){
  for(Long actor:Arrays.asList(20L,30L,50L,1L,999L))assertThrows(ServiceException.class,()->service.save(draft(),actor,"user"));
  verify(mapper,never()).insertAllocation(any());
 }
 @Test void validatesRecipientsAndSnapshotsNames(){
  BusinessBonusAllocation b=draft();service.validateLines(b,award,mapper.recipients(1L));assertEquals("Alice",b.getLines().get(0).getUserName());
  b.getLines().get(0).setUserId(999L);assertThrows(ServiceException.class,()->service.validateLines(b,award,mapper.recipients(1L)));
  b.setLines(Arrays.asList(line(30L,"10"),line(30L,"10")));assertThrows(ServiceException.class,()->service.validateLines(b,award,mapper.recipients(1L)));
 }
 @Test void percentagesUseApprovedTotalAndRoundToCents(){
  BusinessBonusAllocation b=draft();b.setMode("PERCENT");b.getLines().get(0).setPercentage(new BigDecimal("33.33"));b.getLines().get(0).setAmount(new BigDecimal("99999"));
  service.validateLines(b,award,mapper.recipients(1L));assertEquals(new BigDecimal("33.33"),b.getAmount());
  b.getLines().get(0).setPercentage(new BigDecimal("100.01"));assertThrows(ServiceException.class,()->service.validateLines(b,award,mapper.recipients(1L)));
 }
 @Test void rejectsZeroNegativeAndFractionalCents(){
  for(String value:Arrays.asList("0","-1","1.001")){BusinessBonusAllocation b=draft();b.getLines().get(0).setAmount(new BigDecimal(value));assertThrows(ServiceException.class,()->service.validateLines(b,award,mapper.recipients(1L)));}
 }
 @Test void reservesDraftAndOtherBatchAmounts(){
  when(mapper.reserved(2L,null)).thenReturn(new BigDecimal("50.00"));
  assertThrows(ServiceException.class,()->service.save(draft(),10L,"owner"));verify(mapper,never()).insertAllocation(any());
 }
 @Test void cannotEditSubmittedApprovedOrStaleAllocation(){
  BusinessBonusAllocation b=draft();b.setAllocationId(3L);b.setVersion(0);
  for(String status:Arrays.asList("SUBMITTED","APPROVED","CANCELED")){batch.setStatus(status);assertThrows(ServiceException.class,()->service.save(b,10L,"owner"));}
  batch.setStatus("DRAFT");b.setVersion(99);assertThrows(ServiceException.class,()->service.save(b,10L,"owner"));
 }
 @Test void draftSaveStoresRowsAndAuditWithoutTouchingCost(){
  when(mapper.insertAllocation(any())).thenAnswer(i->{((BusinessBonusAllocation)i.getArgument(0)).setAllocationId(3L);return 1;});
  assertEquals(batch,service.save(draft(),10L,"owner"));verify(mapper).insertLine(any());verify(mapper).event(argThat(e->"SAVE".equals(e.get("eventType"))));
  verify(awards,never()).transitionAward(anyLong(),anyString(),anyString(),anyInt(),anyLong(),anyString(),anyString(),any());
 }
 @Test void onlyIndependentSponsorCanReview(){
  batch.setStatus("SUBMITTED");
  for(Long actor:Arrays.asList(10L,1L,30L,50L,999L))assertThrows(ServiceException.class,()->service.transition(3L,0,"APPROVED","review",actor,"user"));
  service.transition(3L,0,"APPROVED","review",20L,"sponsor");verify(mapper).transition(3L,0,"APPROVED",20L,"sponsor");
  project.setSponsorOwnerUserId(10L);assertThrows(ServiceException.class,()->service.transition(3L,0,"APPROVED","review",10L,"owner"));
 }
 @Test void returnedBatchCanBeResubmittedOrCanceledByOriginalOwner(){
  batch.setStatus("RETURNED");service.transition(3L,0,"SUBMITTED","fixed",10L,"owner");service.transition(3L,0,"CANCELED","withdraw",10L,"owner");
  assertThrows(ServiceException.class,()->service.transition(3L,0,"CANCELED","withdraw",20L,"sponsor"));
 }
 @Test void paymentRequiresApprovedAllocationConfirmedCostAndCompany(){
  assertThrows(ServiceException.class,()->service.pay(payment(),50L,"finance",true));batch.setStatus("APPROVED");
  assertThrows(ServiceException.class,()->service.pay(payment(),999L,"outsider",false));
  award.setCostStatus("DRAFT");assertThrows(ServiceException.class,()->service.pay(payment(),50L,"finance",true));
  award.setCostStatus("REVERSED");assertThrows(ServiceException.class,()->service.pay(payment(),50L,"finance",true));
  verify(mapper,never()).insertPayment(any());
 }
 @Test void projectSponsorCanRecordPaymentWithoutCompanyFinanceRole(){
  batch.setStatus("APPROVED");service.pay(payment(),20L,"sponsor",false);verify(mapper).insertPayment(any());
  assertThrows(ServiceException.class,()->service.pay(payment(),10L,"owner",false));
 }
 @Test void partialPaymentsCannotExceedPersonalBalance(){
  batch.setStatus("APPROVED");BusinessBonusPayment p=payment();p.setAmount(new BigDecimal("40.01"));assertThrows(ServiceException.class,()->service.pay(p,50L,"finance",true));
  p.setAmount(new BigDecimal("40.00"));service.pay(p,50L,"finance",true);verify(mapper).insertPayment(p);verify(mapper).event(argThat(e->"PAYMENT".equals(e.get("eventType"))));
 }
 @Test void paymentRetryIsIdempotentAndPayloadChangesRejected(){
  batch.setStatus("APPROVED");BusinessBonusPayment existing=payment();when(mapper.paymentRequest(1L,"pay1")).thenReturn(existing);
  assertSame(existing,service.pay(payment(),50L,"finance",true));verify(mapper,never()).insertPayment(any());
  BusinessBonusPayment changed=payment();changed.setAmount(new BigDecimal("20.00"));assertThrows(ServiceException.class,()->service.pay(changed,50L,"finance",true));
 }
 @Test void rejectsDuplicateReceiptAndUnsafeEvidenceAndFutureDate(){
  batch.setStatus("APPROVED");when(mapper.paymentReference(4L,"bank1")).thenReturn(payment());assertThrows(ServiceException.class,()->service.pay(payment(),50L,"finance",true));
  when(mapper.paymentReference(4L,"bank1")).thenReturn(null);
  BusinessBonusPayment p=payment();p.setVoucher("javascript:alert(1)");assertThrows(ServiceException.class,()->service.pay(p,50L,"finance",true));
  BusinessBonusPayment future=payment();future.setPaidDate(new Date(System.currentTimeMillis()+86400000L));assertThrows(ServiceException.class,()->service.pay(future,50L,"finance",true));
 }
 @Test void existingAllocationBlocksCancelingSourceAward(){
  BusinessIncentiveServiceImpl original=new BusinessIncentiveServiceImpl();
  ReflectionTestUtils.setField(original,"mapper",awards);ReflectionTestUtils.setField(original,"projectMapper",projects);
  project.setStatus("ACTIVE");award.setVersion(0);when(awards.countDistributionReservations(2L)).thenReturn(1);
  assertThrows(ServiceException.class,()->original.cancel(2L,0,"cancel",20L,"sponsor"));
  verify(awards,never()).transitionAward(anyLong(),anyString(),anyString(),anyInt(),anyLong(),anyString(),anyString(),any());
 }
 @Test void memberReadModelContainsOnlyOwnApprovedMoney(){
  Map<String,Object> directory=new HashMap<>();directory.put("projectId",1L);
  when(mapper.projects(30L,false,false)).thenReturn(Arrays.asList(directory));batch.setStatus("APPROVED");
  BusinessBonusAllocationLine other=line(40L,"40.00");other.setLineId(5L);
  when(mapper.lines(3L)).thenReturn(Arrays.asList(line,other));when(mapper.allocations(1L)).thenReturn(Arrays.asList(batch));
  Map<String,Object> out=service.workspace(1L,30L,false,false);
  assertEquals(true,out.get("personal"));assertTrue(((List<?>)out.get("awards")).isEmpty());assertTrue(((List<?>)out.get("recipients")).isEmpty());
  Map<?,?> view=(Map<?,?>)((List<?>)out.get("allocations")).get(0);assertEquals(new BigDecimal("60.00"),view.get("amount"));assertEquals(1,((List<?>)view.get("lines")).size());assertNull(view.get("reason"));assertTrue(((List<?>)view.get("events")).isEmpty());
  batch.setStatus("DRAFT");assertTrue(((List<?>)service.workspace(1L,30L,false,false).get("allocations")).isEmpty());
  assertThrows(ServiceException.class,()->service.workspace(2L,30L,false,false));
 }
}
