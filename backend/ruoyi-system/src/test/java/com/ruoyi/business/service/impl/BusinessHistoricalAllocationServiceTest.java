package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ruoyi.business.service.impl.BusinessHistoricalAllocationService.map;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.*;
import com.ruoyi.business.mapper.*;
import com.ruoyi.business.service.BusinessCompanyAccessService;
import com.ruoyi.business.support.BusinessAllocationWeights;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class BusinessHistoricalAllocationServiceTest {
    BusinessHistoricalAllocationService service;
    BusinessHistoricalAllocationMapper history;
    BusinessProjectMapper projects;
    BusinessAllocationRequestMapper requests;
    BusinessMemberDayCostMapper costs;
    BusinessMemberDayCostService memberDays;
    BusinessProjectWorkMapper work;
    ObjectMapper json=new ObjectMapper();
    BusinessProject target,other;
    List<Map<String,Object>> memberships,timeline;
    LocalDate from=LocalDate.parse("2026-09-01"),to=LocalDate.parse("2026-09-08");
    @BeforeEach void setup(){
        service=new BusinessHistoricalAllocationService();history=mock(BusinessHistoricalAllocationMapper.class);
        projects=mock(BusinessProjectMapper.class);requests=mock(BusinessAllocationRequestMapper.class);
        costs=mock(BusinessMemberDayCostMapper.class);memberDays=mock(BusinessMemberDayCostService.class);work=mock(BusinessProjectWorkMapper.class);
        ReflectionTestUtils.setField(service,"history",history);ReflectionTestUtils.setField(service,"projects",projects);
        ReflectionTestUtils.setField(service,"requests",requests);ReflectionTestUtils.setField(service,"costs",costs);
        ReflectionTestUtils.setField(service,"memberDays",memberDays);ReflectionTestUtils.setField(service,"work",work);
        ReflectionTestUtils.setField(service,"access",mock(BusinessCompanyAccessService.class));ReflectionTestUtils.setField(service,"json",json);
        target=project(1L,10L);other=project(2L,10L);
        when(projects.selectProjectById(1L)).thenReturn(target);when(projects.selectProjectById(2L)).thenReturn(other);
        memberships=new ArrayList<>(Arrays.asList(membership(1L,10L),membership(2L,10L)));
        timeline=new ArrayList<>(Arrays.asList(period(1L,11L,50,"2026-09-09",null),period(2L,22L,100,"2026-08-01",null)));
        when(history.selectMemberships(eq(7L),isNull(),anyString(),anyString())).thenAnswer(c->memberships);
        when(projects.selectUserAllocationTimeline(7L)).thenAnswer(c->timeline);
        when(requests.selectPending(7L)).thenReturn(null);
        when(memberDays.previewHistoricalAllocation(any(),any(),any(),eq(7L),anyList())).thenAnswer(c->
            Collections.singletonList(map("userId",7L,"userName","成员甲","bizDate","2026-09-01","amount",new BigDecimal("100"))));
        when(history.voidVersion(anyLong(),anyInt(),anyString())).thenReturn(1);
        when(history.restoreStart(anyLong(),anyInt(),anyString(),anyString())).thenReturn(1);
        BusinessProjectStaffAllocation existing=new BusinessProjectStaffAllocation();existing.setAllocationId(22L);existing.setProjectId(2L);existing.setUserId(7L);
        existing.setAllocationValue(new BigDecimal("100"));existing.setEffectiveFrom(Date.valueOf("2026-08-01"));existing.setConfirmationStatus("CONFIRMED");existing.setVersion(2);
        when(projects.selectProjectStaffAllocationById(22L)).thenReturn(existing);
    }
    BusinessProject project(Long id,Long owner){BusinessProject p=new BusinessProject();p.setProjectId(id);p.setMainOwnerUserId(owner);p.setDelFlag("0");p.setStatus("ACTIVE");p.setAccountingState("OPEN");p.setDeliveryPolicyVersion("SEPARATED_V1");p.setCostPolicyVersion("MEMBER_DAYS_V1");p.setPlanStartDate(Date.valueOf("2026-09-01"));p.setActualStartDate(Date.valueOf("2026-09-09"));p.setVersion(3);p.setBaseCurrency("CNY");return p;}
    Map<String,Object> membership(Long pid,Long owner){return map("projectId",pid,"projectName","项目"+pid,"ownerUserId",owner,"ownerName","负责人"+owner,"currency","CNY","projectStatus","ACTIVE","accountingState","OPEN","deliveryPolicyVersion","SEPARATED_V1","projectVersion",3,"projectDelFlag","0","planStartDate","2026-09-01","actualStartDate","2026-09-09","recordedDate","2026-09-09","userId",7L,"userName","成员甲","memberRole","MEMBER","joinedDate","2026-09-01");}
    Map<String,Object> period(Long pid,Long aid,int value,String start,String end){return map("projectId",pid,"allocationId",aid,"allocationVersion",2,"allocationValue",value,"effectiveFrom",start,"effectiveTo",end,"confirmationStatus","CONFIRMED","projectStartDate",pid==2L?"2026-08-01":"2026-09-09");}
    Map<String,Object> input(){return map("userId",7L,"dateFrom",from.toString(),"dateTo",to.toString());}
    Map<String,Object> submission(){Map<String,Object> body=input();body.put("versionToken",service.workspace(1L,body,10L).get("versionToken"));body.put("allocations",Arrays.asList(map("projectId",1L,"allocationValue",50),map("projectId",2L,"allocationValue",50)));body.put("reason","按历史工作记录核对");body.put("impactConfirmed",true);return body;}
    @Test void historicalGapUsesParticipationAndDetectsRecordingStartCorrection(){
        Map<String,Object> ws=service.workspace(1L,input(),10L);
        List<Map<String,Object>> rows=(List<Map<String,Object>>)ws.get("projects");
        assertEquals(8,ws.get("gapDays"));assertEquals("2026-09-01",rows.get(0).get("startCorrectionDate"));assertEquals(new BigDecimal("100"),rows.get(1).get("allocationValue"));
        verify(projects,never()).insertProjectStaffAllocation(any());verifyNoInteractions(memberDays);
    }
    @Test void actualParticipationBoundaryAndExistingWeightsCannotBeOverwritten(){
        memberships.get(0).put("joinedDate","2026-09-03");assertTrue(assertThrows(ServiceException.class,()->service.workspace(1L,input(),10L)).getMessage().contains("未参与"));
        memberships.get(0).put("joinedDate","2026-09-01");to=LocalDate.parse("2026-09-09");
        assertTrue(assertThrows(ServiceException.class,()->service.workspace(1L,input(),10L)).getMessage().contains("2026-09-09"));
    }
    @Test void changingOtherParticipationOrWeightsRequiresExplicitSegments(){
        memberships.get(1).put("leftDate","2026-09-04");assertTrue(assertThrows(ServiceException.class,()->service.workspace(1L,input(),10L)).getMessage().contains("2026-09-05"));
        memberships.get(1).remove("leftDate");timeline.get(1).put("effectiveTo","2026-09-04");timeline.add(period(2L,23L,100,"2026-09-05",null));
        assertTrue(assertThrows(ServiceException.class,()->service.workspace(1L,input(),10L)).getMessage().contains("分段补录"));
    }
    @Test void previewChecksGlobalTotalAndDatedCostWithoutWriting(){
        Map<String,Object> body=submission();Map<String,Object> result=service.preview(1L,body,10L);
        assertEquals(false,result.get("needsConfirmation"));assertEquals(2,((List<?>)result.get("impacts")).size());
        verify(projects,never()).insertProjectStaffAllocation(any());verify(requests,never()).insertRequest(any());
        ((Map<String,Object>)((List<?>)body.get("allocations")).get(0)).put("allocationValue",40);
        assertTrue(assertThrows(ServiceException.class,()->service.preview(1L,body,10L)).getMessage().contains("合计必须"));
    }
    @Test void missingHistoricalCostHasPersonAndDateAndNeverWrites(){
        when(memberDays.previewHistoricalAllocation(any(),any(),any(),eq(7L),anyList())).thenReturn(Collections.singletonList(map("userId",7L,"userName","成员甲","bizDate","2026-09-01","issue","缺少有效用人成本")));
        assertTrue(assertThrows(ServiceException.class,()->service.preview(1L,submission(),10L)).getMessage().contains("成员甲 / 2026-09-01"));verify(projects,never()).insertProjectStaffAllocation(any());
    }
    @Test void frozenPeriodAndStaleInputsCannotBeSaved(){
        Map<String,Object> body=submission();memberships.get(1).put("projectVersion",4);
        assertTrue(assertThrows(ServiceException.class,()->service.preview(1L,body,10L)).getMessage().contains("已变化"));
        when(history.countFrozenResults(eq(1L),anyString(),anyString())).thenReturn(1);
        assertTrue(assertThrows(ServiceException.class,()->service.preview(1L,submission(),10L)).getMessage().contains("已冻结"));
    }
    @Test void splitRetainsPriorAndFutureVersionsAndOnlyAddsBoundedRecords(){
        Map<Long,BigDecimal> weights=new HashMap<>();
        weights.put(1L,new BigDecimal("50"));weights.put(2L,new BigDecimal("50"));
        List<Map<String,Object>> simulated=BusinessHistoricalAllocationService.replaceTimeline(timeline,new HashSet<>(Arrays.asList(1L,2L)),weights,from,to);
        assertEquals(new BigDecimal("100"),new BigDecimal(BusinessAllocationWeights.at(simulated,LocalDate.parse("2026-08-31")).get(2L).get("allocationValue").toString()));
        assertEquals(50,BusinessAllocationWeights.at(simulated,LocalDate.parse("2026-09-09")).get(1L).get("allocationValue"));
        assertEquals(100,BusinessAllocationWeights.at(simulated,LocalDate.parse("2026-09-09")).get(2L).get("allocationValue"));
        assertEquals(100,timeline.get(1).get("allocationValue"));assertNull(timeline.get(1).get("effectiveTo"));
    }
    void insertedRequest(){doAnswer(c->{Map<String,Object> req=c.getArgument(0);req.put("requestId",5L);req.put("status","PENDING");when(requests.selectRequest(5L)).thenReturn(req);return 1;}).when(requests).insertRequest(anyMap());}
    @Test void selfConfirmedRepairPreservesBothSidesAndRepricesBoundedDays(){
        insertedRequest();service.save(1L,submission(),10L,"负责人甲");
        ArgumentCaptor<BusinessProjectStaffAllocation> captured=ArgumentCaptor.forClass(BusinessProjectStaffAllocation.class);verify(projects,times(4)).insertProjectStaffAllocation(captured.capture());
        assertTrue(captured.getAllValues().stream().anyMatch(a->a.getProjectId()==2L&&Date.valueOf("2026-08-01").equals(a.getEffectiveFrom())&&Date.valueOf("2026-08-31").equals(a.getEffectiveTo())));
        assertTrue(captured.getAllValues().stream().anyMatch(a->a.getProjectId()==2L&&Date.valueOf("2026-09-09").equals(a.getEffectiveFrom())&&a.getEffectiveTo()==null));
        verify(history).voidVersion(22L,2,"负责人甲");verify(history).restoreStart(1L,3,"2026-09-01","负责人甲");
        verify(memberDays).synchronizeHistoricalAllocationChange(1L,Date.valueOf(from),Date.valueOf(to),"负责人甲");
        verify(memberDays).synchronizeHistoricalAllocationChange(2L,Date.valueOf(from),Date.valueOf(to),"负责人甲");
        verify(memberDays,never()).synchronizeAllocationChange(anyLong(),any(),anyString());verify(requests).finish(5L,"APPLIED","历史投入已补齐并重新核算");
    }
    @Test void otherOwnerMustConfirmBeforeAnyAllocationOrCostWrite() throws Exception {
        memberships.get(1).put("ownerUserId",20L);insertedRequest();Map<String,Object> result=service.save(1L,submission(),10L,"负责人甲");
        assertEquals("PENDING",result.get("outcome"));verify(projects,never()).insertProjectStaffAllocation(any());verify(memberDays,never()).synchronizeHistoricalAllocationChange(anyLong(),any(),any(),anyString());
    }
    @Test void finalConfirmationRevalidatesThenAppliesAndAuditsReviewer() throws Exception {
        memberships.get(1).put("ownerUserId",20L);Map<String,Object> snapshot=service.preview(1L,submission(),10L);snapshot.put("reason","核对历史");
        Map<String,Object> req=map("requestId",5L,"userId",7L,"applicantId",10L,"status","PENDING","snapshotJson",json.writeValueAsString(snapshot));when(requests.selectRequest(5L)).thenReturn(req);
        when(requests.selectReviews(5L)).thenReturn(Collections.singletonList(map("ownerUserId",20L,"status","PENDING"))).thenReturn(Collections.singletonList(map("ownerUserId",20L,"status","APPROVED")));
        service.review(5L,"APPROVED","核对通过",20L,"负责人乙");
        verify(requests).finish(5L,"APPLIED","相关负责人已确认历史补录");
        ArgumentCaptor<Map<String,Object>> events=ArgumentCaptor.forClass(Map.class);verify(projects,times(2)).insertEvent(events.capture());assertEquals(20L,events.getValue().get("operatorUserId"));
    }
    @Test void confirmationInvalidatesRequestWhenMembershipChanged() throws Exception {
        Map<String,Object> snapshot=service.preview(1L,submission(),10L);Map<String,Object> req=map("requestId",5L,"userId",7L,"applicantId",10L,"status","PENDING","snapshotJson",json.writeValueAsString(snapshot));when(requests.selectRequest(5L)).thenReturn(req);when(requests.selectReviews(5L)).thenReturn(Collections.singletonList(map("ownerUserId",20L,"status","PENDING")));
        memberships.get(0).put("joinedDate","2026-09-03");service.review(5L,"APPROVED","",20L,"负责人乙");
        verify(requests).finish(eq(5L),eq("INVALIDATED"),contains("未参与"));verify(projects,never()).insertProjectStaffAllocation(any());
    }
    @Test void initialHistoryCannotVoidLaterRecordsOrFrozenBooks(){
        assertTrue(assertThrows(ServiceException.class,()->service.validateInitialChange(1L,7L,Date.valueOf(from))).getMessage().contains("后续投入"));
        when(history.countFrozenResults(eq(2L),anyString(),anyString())).thenReturn(1);assertTrue(assertThrows(ServiceException.class,()->service.validateInitialChange(2L,7L,Date.valueOf(from))).getMessage().contains("冻结"));
    }
    @Test void memberPickerDefaultsToFirstActualGapRatherThanAlreadyConfiguredToday(){
        when(history.selectMemberships(isNull(),eq(1L),anyString(),anyString())).thenReturn(Collections.singletonList(memberships.get(0)));
        when(history.selectMemberships(eq(7L),eq(1L),anyString(),anyString())).thenReturn(Collections.singletonList(memberships.get(0)));
        Map<String,Object> m=(Map<String,Object>)((List<?>)service.members(1L,10L).get("members")).get(0);assertEquals("2026-09-01",m.get("gapFrom"));assertEquals("2026-09-08",m.get("gapTo"));
    }
    @Test void permissionAndImpactConfirmationAreRequired(){
        assertThrows(ServiceException.class,()->service.workspace(1L,input(),99L));Map<String,Object> body=submission();body.remove("impactConfirmed");assertThrows(ServiceException.class,()->service.save(1L,body,10L,"负责人甲"));verify(projects,never()).insertProjectStaffAllocation(any());
    }

    Map<String,Object> segment(String begin,String end,int a,int b){
        Map<String,Object> s=map("userId",7L,"dateFrom",begin,"dateTo",end);
        s.put("versionToken",service.scheduleWorkspace(1L,s,10L).get("versionToken"));
        s.put("allocations",Arrays.asList(map("projectId",1L,"allocationValue",a),map("projectId",2L,"allocationValue",b)));return s;
    }
    Map<String,Object> schedule(Map<String,Object>... segments){return map("userId",7L,"segments",Arrays.asList(segments),"reason","逐段核对实际工作记录","impactConfirmed",true);}
    void scheduleMembership(){when(history.selectMemberships(eq(7L),eq(1L),anyString(),anyString())).thenAnswer(c->Collections.singletonList(memberships.get(0)));}
    void validExistingTimeline(){timeline.clear();timeline.add(period(1L,11L,50,"2026-08-01",null));timeline.add(period(2L,22L,50,"2026-08-01",null));scheduleMembership();}

    @Test void proposalConfirmationChecksInputsWhileOtherMembersAndLedgersAreBeingPopulated(){
        validExistingTimeline();Map<String,Object> body=schedule(segment("2026-09-01","2026-09-08",30,70));body.put("initialAllocation",true);
        Object token=service.previewSchedule(1L,body,10L).get("previewToken");
        when(costs.selectCosts(1L)).thenReturn(Arrays.asList(map("userId",7L,"bizDate","2026-09-01","amount",100),map("userId",99L,"bizDate","2026-09-01","amount",999)));
        assertEquals(token,service.previewSchedule(1L,body,10L).get("previewToken"));
        timeline.get(0).put("allocationVersion",3);
        assertThrows(ServiceException.class,()->service.previewSchedule(1L,body,10L));
    }
    @Test void scheduleCanEditExistingHistoricalWeightsAndPreviewWithoutWriting(){
        validExistingTimeline();Map<String,Object> body=schedule(segment("2026-09-01","2026-09-08",30,70));
        Map<String,Object> preview=service.previewSchedule(1L,body,10L);assertEquals("SEGMENTS",preview.get("kind"));assertNotNull(preview.get("previewToken"));
        assertEquals(1,((List<?>)preview.get("segments")).size());verify(projects,never()).insertProjectStaffAllocation(any());verify(requests,never()).insertRequest(any());
    }
    @Test void scheduleFindsVersionAndParticipationBoundariesForAutomaticSplitting(){
        validExistingTimeline();timeline.get(0).put("effectiveTo","2026-09-04");timeline.add(period(1L,12L,40,"2026-09-05",null));
        assertEquals("2026-09-05",service.scheduleWorkspace(1L,input(),10L).get("splitDate"));
        timeline.get(0).put("effectiveTo",null);timeline.remove(2);memberships.get(1).put("leftDate","2026-09-03");
        assertEquals("2026-09-04",service.scheduleWorkspace(1L,input(),10L).get("splitDate"));
    }
    @Test void openSegmentStopsBeforeLaterValidRecordAndFutureDatesAreSupported(){
        validExistingTimeline();timeline.get(0).put("effectiveTo","2026-09-08");timeline.add(period(1L,12L,40,"2026-09-09",null));
        Map<String,Object> ws=service.scheduleWorkspace(1L,map("userId",7L,"dateFrom","2026-09-01"),10L);
        assertEquals("2026-09-08",ws.get("dateTo"));assertEquals("2026-09-09",ws.get("preservedFrom"));
        timeline.get(0).put("effectiveTo",null);timeline.remove(2);
        String future=LocalDate.now().plusDays(10).toString();Map<String,Object> futureWs=service.scheduleWorkspace(1L,map("userId",7L,"dateFrom",future),10L);
        assertNull(futureWs.get("dateTo"));assertNull(futureWs.get("preservedFrom"));
        assertNotNull(service.previewSchedule(1L,schedule(segment(future,null,30,70)),10L).get("previewToken"));
    }
    @Test void overlappingSegmentsAndMissingRetainedDaysCannotBeSaved(){
        scheduleMembership();Map<String,Object> overlap=schedule(segment("2026-09-01","2026-09-04",50,50),segment("2026-09-04","2026-09-08",50,50));
        assertTrue(assertThrows(ServiceException.class,()->service.previewSchedule(1L,overlap,10L)).getMessage().contains("不能重叠"));
        Map<String,Object> gap=schedule(segment("2026-09-01","2026-09-02",50,50),segment("2026-09-07","2026-09-08",50,50));
        assertTrue(assertThrows(ServiceException.class,()->service.previewSchedule(1L,gap,10L)).getMessage().contains("2026-09-03"));
        verify(projects,never()).insertProjectStaffAllocation(any());
    }
    @Test void anyFrozenSegmentRejectsTheWholeScheduleBeforeWrites(){
        validExistingTimeline();Map<String,Object> body=schedule(segment("2026-09-01","2026-09-04",30,70),segment("2026-09-05","2026-09-08",40,60));
        when(history.countFrozenResults(1L,"2026-09-05","2026-09-08")).thenReturn(1);
        assertTrue(assertThrows(ServiceException.class,()->service.saveSchedule(1L,body,10L,"负责人甲")).getMessage().contains("已变化"));
        verify(projects,never()).insertProjectStaffAllocation(any());verify(requests,never()).insertRequest(any());
    }
    @Test void changedCostPreviewCannotBeSavedEvenWhenAllocationVersionMatches(){
        validExistingTimeline();Map<String,Object> body=schedule(segment("2026-09-01","2026-09-08",30,70));body.put("previewToken",service.previewSchedule(1L,body,10L).get("previewToken"));
        when(memberDays.previewHistoricalAllocation(any(),any(),any(),eq(7L),anyList())).thenReturn(Collections.singletonList(map("userId",7L,"userName","成员甲","bizDate","2026-09-01","amount",new BigDecimal("999"))));
        assertTrue(assertThrows(ServiceException.class,()->service.saveSchedule(1L,body,10L,"负责人甲")).getMessage().contains("重新预览"));verify(requests,never()).insertRequest(any());
    }
    void mutableAllocationStore(){
        Map<Long,BusinessProjectStaffAllocation> stored=new HashMap<>();long[] next={100};
        for(Map<String,Object> row:timeline){BusinessProjectStaffAllocation a=new BusinessProjectStaffAllocation();a.setAllocationId(((Number)row.get("allocationId")).longValue());a.setProjectId(((Number)row.get("projectId")).longValue());a.setUserId(7L);a.setVersion(2);a.setEffectiveFrom(Date.valueOf(row.get("effectiveFrom").toString()));a.setEffectiveTo(row.get("effectiveTo")==null?null:Date.valueOf(row.get("effectiveTo").toString()));a.setAllocationValue(new BigDecimal(row.get("allocationValue").toString()));a.setConfirmationStatus("CONFIRMED");stored.put(a.getAllocationId(),a);}
        when(projects.selectProjectStaffAllocationById(anyLong())).thenAnswer(c->stored.get(c.getArgument(0)));
        when(history.voidVersion(anyLong(),anyInt(),anyString())).thenAnswer(c->{Long id=c.getArgument(0);if(timeline.removeIf(r->id.equals(r.get("allocationId"))))return 1;return 0;});
        doAnswer(c->{BusinessProjectStaffAllocation a=c.getArgument(0);a.setAllocationId(next[0]++);if(a.getVersion()==null)a.setVersion(0);stored.put(a.getAllocationId(),a);Map<String,Object> row=period(a.getProjectId(),a.getAllocationId(),a.getAllocationValue().intValueExact(),a.getEffectiveFrom().toString(),a.getEffectiveTo()==null?null:a.getEffectiveTo().toString());row.put("projectStartDate","2026-09-01");timeline.add(row);return 1;}).when(projects).insertProjectStaffAllocation(any());
    }
    @Test void twoSegmentsApplyAtomicallyAndRetainEveryOutsideDateAndOneStartCorrection(){
        validExistingTimeline();mutableAllocationStore();insertedRequest();Map<String,Object> body=schedule(segment("2026-09-01","2026-09-04",30,70),segment("2026-09-05","2026-09-08",40,60));
        body.put("previewToken",service.previewSchedule(1L,body,10L).get("previewToken"));service.saveSchedule(1L,body,10L,"负责人甲");
        assertTrue(timeline.stream().anyMatch(r->Long.valueOf(1).equals(r.get("projectId"))&&"2026-08-01".equals(r.get("effectiveFrom"))&&"2026-08-31".equals(r.get("effectiveTo"))&&Integer.valueOf(50).equals(r.get("allocationValue"))));
        assertEquals(30,BusinessAllocationWeights.at(timeline,LocalDate.parse("2026-09-01")).get(1L).get("allocationValue"));
        assertEquals(40,BusinessAllocationWeights.at(timeline,LocalDate.parse("2026-09-05")).get(1L).get("allocationValue"));
        assertEquals(50,BusinessAllocationWeights.at(timeline,LocalDate.parse("2026-09-09")).get(1L).get("allocationValue"));
        verify(history,times(1)).restoreStart(1L,3,"2026-09-01","负责人甲");verify(history,times(1)).restoreStart(2L,3,"2026-09-01","负责人甲");
        verify(memberDays).synchronizeHistoricalAllocationChange(1L,Date.valueOf("2026-09-01"),Date.valueOf("2026-09-04"),"负责人甲");
        verify(memberDays).synchronizeHistoricalAllocationChange(1L,Date.valueOf("2026-09-05"),Date.valueOf("2026-09-08"),"负责人甲");verify(requests).finish(5L,"APPLIED","多段投入已保存并重新核算");
    }
    @Test void multiSegmentOtherOwnerConfirmationDoesNotWriteUntilFinalReview(){
        validExistingTimeline();memberships.get(1).put("ownerUserId",20L);insertedRequest();Map<String,Object> body=schedule(segment("2026-09-01","2026-09-04",30,70),segment("2026-09-05","2026-09-08",40,60));
        body.put("previewToken",service.previewSchedule(1L,body,10L).get("previewToken"));assertEquals("PENDING",service.saveSchedule(1L,body,10L,"负责人甲").get("outcome"));
        verify(projects,never()).insertProjectStaffAllocation(any());verifyNoInteractionsOnCosts();
        when(requests.selectReviews(5L)).thenReturn(Collections.singletonList(map("ownerUserId",20L,"status","PENDING"))).thenReturn(Collections.singletonList(map("ownerUserId",20L,"status","APPROVED")));
        mutableAllocationStore();service.review(5L,"APPROVED","核对通过",20L,"负责人乙");verify(requests).finish(5L,"APPLIED","相关负责人已确认多段投入");
    }
    void verifyNoInteractionsOnCosts(){verify(memberDays,never()).synchronizeHistoricalAllocationChange(anyLong(),any(),any(),anyString());}
    @Test void openScheduleConfirmationPreservesAlreadyPlannedFutureVersions(){
        validExistingTimeline();timeline.get(0).put("effectiveTo","2026-09-08");timeline.get(1).put("effectiveTo","2026-09-08");
        timeline.add(period(1L,12L,40,"2026-09-09",null));timeline.add(period(2L,23L,60,"2026-09-09",null));
        memberships.get(1).put("ownerUserId",20L);insertedRequest();Map<String,Object> body=schedule(segment("2026-09-01",null,30,70));
        body.put("previewToken",service.previewSchedule(1L,body,10L).get("previewToken"));assertEquals("PENDING",service.saveSchedule(1L,body,10L,"负责人甲").get("outcome"));
        when(requests.selectReviews(5L)).thenReturn(Collections.singletonList(map("ownerUserId",20L,"status","PENDING"))).thenReturn(Collections.singletonList(map("ownerUserId",20L,"status","APPROVED")));
        mutableAllocationStore();service.review(5L,"APPROVED","核对通过",20L,"负责人乙");
        verify(requests).finish(5L,"APPLIED","相关负责人已确认多段投入");assertEquals(40,BusinessAllocationWeights.at(timeline,LocalDate.parse("2026-09-09")).get(1L).get("allocationValue"));
        assertEquals(60,BusinessAllocationWeights.at(timeline,LocalDate.parse("2026-09-09")).get(2L).get("allocationValue"));
        verify(memberDays).synchronizeHistoricalAllocationChange(1L,Date.valueOf("2026-09-01"),Date.valueOf("2026-09-08"),"负责人乙");
    }
    @Test void readingPendingRequestDoesNotRequireAnEditableDateAndSupportsOlderRequests() throws Exception {
        scheduleMembership();Map<String,Object> legacy=map("projects",Collections.singletonList(map("projectId",1L,"requestedValue",100)));
        when(requests.selectPending(7L)).thenReturn(map("requestId",5L,"userId",7L,"applicantId",10L,"status","PENDING","snapshotJson",json.writeValueAsString(legacy)));
        Map<String,Object> state=service.scheduleState(1L,7L,10L);Map<String,Object> pending=(Map<String,Object>)state.get("pendingRequest");assertEquals(true,pending.get("canWithdraw"));assertEquals(1,((List<?>)pending.get("projects")).size());verifyNoInteractions(memberDays);
    }
    @Test void periodSummaryMergesEqualVersionsAndKeepsMissingRecordsUnknown(){
        for(Map<String,Object> m:memberships)m.put("actualStartDate","2026-09-01");
        timeline.clear();timeline.add(period(1L,11L,30,"2026-09-01","2026-09-04"));
        timeline.add(period(1L,12L,30,"2026-09-05","2026-09-08"));
        timeline.add(period(2L,22L,70,"2026-09-01","2026-09-08"));
        List<Map<String,Object>> periods=service.allocationPeriods(7L,timeline);
        assertEquals(2,periods.size());assertEquals("2026-09-08",periods.get(0).get("dateTo"));
        assertEquals("FULL",periods.get(0).get("status"));assertEquals(new BigDecimal("100"),periods.get(0).get("totalPercent"));
        assertEquals("INCOMPLETE",periods.get(1).get("status"));
        assertNull(((Map<?,?>)((List<?>)periods.get(1).get("projects")).get(0)).get("allocationValue"));
        assertEquals(3,timeline.size());verify(projects,never()).insertProjectStaffAllocation(any());verifyNoInteractions(memberDays);
    }
    @Test void periodSummaryUsesAutomaticRedistributionAndActualProjectEndings(){
        for(Map<String,Object> m:memberships)m.put("actualStartDate","2026-09-01");
        memberships.get(0).put("projectEndDate","2026-09-04");
        timeline.clear();timeline.add(period(1L,11L,50,"2026-09-01",null));timeline.get(0).put("projectEndDate","2026-09-04");
        timeline.add(period(2L,22L,50,"2026-09-01",null));
        List<Map<String,Object>> periods=service.allocationPeriods(7L,timeline);
        assertEquals(2,periods.size());assertEquals("2026-09-05",periods.get(1).get("dateFrom"));
        List<?> rows=(List<?>)periods.get(1).get("projects");assertEquals(1,rows.size());
        assertEquals(new BigDecimal("100.00"),((Map<?,?>)rows.get(0)).get("allocationValue"));assertEquals(true,((Map<?,?>)rows.get(0)).get("autoRedistributed"));
        assertEquals(50,timeline.get(1).get("allocationValue"));
    }
    @Test void periodSummaryDistinguishesExplicitZeroPendingAndOverAllocation(){
        for(Map<String,Object> m:memberships)m.put("actualStartDate","2026-09-01");
        timeline.clear();timeline.add(period(1L,11L,0,"2026-09-01",null));timeline.add(period(2L,22L,100,"2026-09-01",null));
        timeline.get(1).put("confirmationStatus","PENDING");
        Map<String,Object> period=service.allocationPeriods(7L,timeline).get(0);List<?> rows=(List<?>)period.get("projects");
        assertEquals(BigDecimal.ZERO,((Map<?,?>)rows.get(0)).get("allocationValue"));assertEquals(false,((Map<?,?>)rows.get(0)).get("allocationMissing"));
        assertNull(((Map<?,?>)rows.get(1)).get("allocationValue"));assertEquals("INCOMPLETE",period.get("status"));
        timeline.get(0).put("allocationValue",70);timeline.get(1).put("allocationValue",60);timeline.get(1).put("confirmationStatus","CONFIRMED");
        period=service.allocationPeriods(7L,timeline).get(0);assertEquals("OVER",period.get("status"));assertEquals(new BigDecimal("130"),period.get("totalPercent"));
    }
    @Test void deletedClosedAndFrozenPeriodsHaveDifferentLockReasons(){
        scheduleMembership();
        memberships.get(1).put("projectDelFlag","2");memberships.get(1).put("actualStartDate","2026-09-01");
        List<?> rows=(List<?>)service.scheduleWorkspace(1L,input(),10L).get("projects");assertEquals("DELETED",((Map<?,?>)rows.get(1)).get("freezeReason"));
        assertTrue(assertThrows(ServiceException.class,()->service.preview(1L,submission(),10L)).getMessage().contains("已删除"));
        memberships.get(1).put("projectDelFlag","0");memberships.get(1).put("accountingState","CLOSED");
        rows=(List<?>)service.scheduleWorkspace(1L,input(),10L).get("projects");assertEquals("ACCOUNTING_CLOSED",((Map<?,?>)rows.get(1)).get("freezeReason"));
        assertTrue(assertThrows(ServiceException.class,()->service.preview(1L,submission(),10L)).getMessage().contains("已关账"));
        memberships.get(1).put("accountingState","OPEN");when(history.countFrozenResults(eq(2L),anyString(),anyString())).thenReturn(1);
        rows=(List<?>)service.scheduleWorkspace(1L,input(),10L).get("projects");assertEquals("PERIOD_CLOSED",((Map<?,?>)rows.get(1)).get("freezeReason"));
    }
    @Test void displayLockReasonsDoNotChangeExistingConfirmationTokens(){
        Map<String,Object> original=map("projects",Collections.singletonList(map("projectId",1L,"frozen",true)));
        Object before=ReflectionTestUtils.invokeMethod(service,"digest",original);
        ((Map<String,Object>)((List<?>)original.get("projects")).get(0)).put("freezeReason","ACCOUNTING_CLOSED");
        assertEquals(before,ReflectionTestUtils.invokeMethod(service,"digest",original));
    }
    @Test void unchangedScheduleRetainsVersionsAndDoesNotRepriceCosts(){
        validExistingTimeline();for(Map<String,Object> m:memberships)m.put("actualStartDate","2026-09-01");insertedRequest();
        Map<String,Object> body=schedule(segment("2026-09-01","2026-09-08",50,50));body.put("previewToken",service.previewSchedule(1L,body,10L).get("previewToken"));
        service.saveSchedule(1L,body,10L,"负责人甲");verify(projects,never()).insertProjectStaffAllocation(any());verifyNoInteractionsOnCosts();
    }
}
