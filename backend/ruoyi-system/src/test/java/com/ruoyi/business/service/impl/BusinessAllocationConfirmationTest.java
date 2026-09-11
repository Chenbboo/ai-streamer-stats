package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.*;
import com.ruoyi.business.mapper.*;
import com.ruoyi.common.exception.ServiceException;

class BusinessAllocationConfirmationTest {
    BusinessProjectServiceImpl service;
    BusinessProjectMapper projects;
    BusinessAllocationRequestMapper requests;
    BusinessMemberDayCostService costs;
    List<Map<String,Object>> rows, reviews;
    Map<String,Object> stored;
    Map<String,Object> body;
    static Map<String,Object> row(Object... pairs) { Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r; }

    @BeforeEach void setup() {
        service=new BusinessProjectServiceImpl();projects=mock(BusinessProjectMapper.class);requests=mock(BusinessAllocationRequestMapper.class);costs=mock(BusinessMemberDayCostService.class);
        ReflectionTestUtils.setField(service,"mapper",projects);ReflectionTestUtils.setField(service,"allocationRequests",requests);ReflectionTestUtils.setField(service,"memberDays",costs);
        reviews=new ArrayList<>();stored=null;
        rows=new ArrayList<>(Arrays.asList(
            row("projectId",91L,"projectName","项目甲","ownerUserId",9L,"ownerName","张三","allocationId",1L,"allocationVersion",0,"allocationValue",new BigDecimal("100"),"confirmationStatus","CONFIRMED"),
            row("projectId",92L,"projectName","项目乙","ownerUserId",12L,"ownerName","李四","allocationId",2L,"allocationVersion",0,"allocationValue",BigDecimal.ZERO,"confirmationStatus","PENDING")));
        when(projects.selectActiveUserById(anyLong())).thenAnswer(call->row("nickName","人员"+call.getArgument(0),"status","0"));
        when(projects.selectUserAllocationWorkspace(eq(11L),any())).thenAnswer(call->rows);
        when(projects.selectProjectById(anyLong())).thenAnswer(call->{Long id=call.getArgument(0);Map<String,Object> r=rows.stream().filter(x->id.equals(x.get("projectId"))).findFirst().orElse(null);if(r==null)return null;BusinessProject p=new BusinessProject();p.setProjectId(id);p.setMainOwnerUserId((Long)r.get("ownerUserId"));p.setStatus("ACTIVE");p.setAccountingState("OPEN");p.setCostPolicyVersion(BusinessMemberDayCostService.POLICY);return p;});
        when(projects.selectProjectByIdForUpdate(anyLong())).thenAnswer(call->projects.selectProjectById(call.getArgument(0)));
        when(requests.selectPending(11L)).thenAnswer(call->stored!=null&&"PENDING".equals(stored.get("status"))?stored:null);
        when(requests.selectRequest(anyLong())).thenAnswer(call->stored);
        when(requests.selectReviews(anyLong())).thenAnswer(call->reviews);
        doAnswer(call->{stored=new LinkedHashMap<>((Map<String,Object>)call.getArgument(0));stored.put("requestId",1L);stored.put("status","PENDING");((Map<String,Object>)call.getArgument(0)).put("requestId",1L);return 1;}).when(requests).insertRequest(anyMap());
        doAnswer(call->{reviews.add(new LinkedHashMap<>((Map<String,Object>)call.getArgument(0)));return 1;}).when(requests).insertReview(anyMap());
        doAnswer(call->{reviews.stream().filter(r->r.get("ownerUserId").equals(call.getArgument(1))).forEach(r->r.put("status",call.getArgument(2)));return 1;}).when(requests).review(anyLong(),anyLong(),anyString(),any());
        doAnswer(call->{stored.put("status",call.getArgument(1));return 1;}).when(requests).finish(anyLong(),anyString(),any());
        body=row("userId",11L,"effectiveDate","2026-09-11","reason","跨项目协商调配","versionToken",service.staffAllocationWorkspace(11L,java.sql.Date.valueOf("2026-09-11"),9L,false).get("versionToken"),"allocations",Arrays.asList(row("projectId",91L,"allocationValue",60),row("projectId",92L,"allocationValue",40)));
    }
    void submit() { service.saveStaffAllocationWorkspace(body,9L,"owner9",false); }
    @Test void crossOwnerProposalDoesNotChangeAllocationOrCost() {
        assertEquals("PENDING",service.saveStaffAllocationWorkspace(body,9L,"owner9",false).get("outcome"));
        assertEquals(2,reviews.size());assertEquals("APPROVED",reviews.get(0).get("status"));assertEquals("PENDING",reviews.get(1).get("status"));
        verify(projects,never()).insertProjectStaffAllocation(any());verify(costs,never()).synchronizeAllocationChange(anyLong(),any(),any());
    }
    @Test void lastOwnerConfirmationAppliesWholeDistribution() {
        submit();assertEquals("APPLIED",service.reviewStaffAllocationRequest(1L,"APPROVED","同意",12L,"owner12").get("status"));
        verify(projects,times(2)).insertProjectStaffAllocation(any());verify(costs).synchronizeAllocationChange(91L,java.sql.Date.valueOf("2026-09-11"),"owner12");verify(costs).synchronizeAllocationChange(92L,java.sql.Date.valueOf("2026-09-11"),"owner12");
        assertThrows(ServiceException.class,()->service.reviewStaffAllocationRequest(1L,"APPROVED","",12L,"owner12"));
    }
    @Test void unrelatedPersonCannotApproveEvenAsAdministrator() {
        submit();assertThrows(ServiceException.class,()->service.reviewStaffAllocationRequest(1L,"APPROVED","",1L,"admin"));verify(projects,never()).insertProjectStaffAllocation(any());
    }
    @Test void secondPendingRequestIsBlocked() {
        submit();assertThrows(ServiceException.class,()->service.saveStaffAllocationWorkspace(body,12L,"owner12",false));verify(requests,times(1)).insertRequest(anyMap());
    }
    @Test void rejectionRequiresReasonAndPreservesOldDistribution() {
        submit();assertThrows(ServiceException.class,()->service.reviewStaffAllocationRequest(1L,"REJECTED"," ",12L,"owner12"));
        assertEquals("REJECTED",service.reviewStaffAllocationRequest(1L,"REJECTED","目前不能减少投入",12L,"owner12").get("status"));verify(projects,never()).insertProjectStaffAllocation(any());
    }
    @Test void onlyApplicantCanWithdrawThenResubmit() {
        submit();assertThrows(ServiceException.class,()->service.reviewStaffAllocationRequest(1L,"WITHDRAWN","",12L,"owner12"));
        service.reviewStaffAllocationRequest(1L,"WITHDRAWN","重新协商",9L,"owner9");submit();verify(requests,times(2)).insertRequest(anyMap());verify(projects,never()).insertProjectStaffAllocation(any());
    }
    @Test void ownerChangeInvalidatesSnapshotWithoutApplying() {
        submit();rows.get(1).put("ownerUserId",13L);
        assertEquals("INVALIDATED",service.reviewStaffAllocationRequest(1L,"APPROVED","同意",12L,"owner12").get("status"));verify(projects,never()).insertProjectStaffAllocation(any());
    }
    @Test void waitsForEveryAffectedOwner() {
        rows.add(row("projectId",93L,"projectName","项目丙","ownerUserId",13L,"ownerName","王五","allocationId",3L,"allocationVersion",0,"allocationValue",BigDecimal.ZERO,"confirmationStatus","PENDING"));
        body.put("versionToken",service.staffAllocationWorkspace(11L,java.sql.Date.valueOf("2026-09-11"),9L,false).get("versionToken"));
        body.put("allocations",Arrays.asList(row("projectId",91L,"allocationValue",60),row("projectId",92L,"allocationValue",20),row("projectId",93L,"allocationValue",20)));
        submit();assertEquals("PENDING",service.reviewStaffAllocationRequest(1L,"APPROVED","同意",12L,"owner12").get("status"));verify(projects,never()).insertProjectStaffAllocation(any());
        assertEquals("APPLIED",service.reviewStaffAllocationRequest(1L,"APPROVED","同意",13L,"owner13").get("status"));verify(projects,times(3)).insertProjectStaffAllocation(any());
    }
}
