package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.business.service.impl.BusinessProjectWorkServiceTest.row;
import java.sql.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessProjectPlanServiceTest
{
    private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

    @Mock BusinessProjectMapper projectMapper;
    @Mock BusinessProjectWorkMapper mapper;
    @Mock BusinessProjectBudgetService budgets;
    @Mock com.ruoyi.business.mapper.BusinessMemberDayCostMapper memberCosts;
    @Spy ObjectMapper json=new ObjectMapper();
    @InjectMocks BusinessProjectPlanService service;
    BusinessProject project;
    @BeforeEach void setup(){project=new BusinessProject();project.setProjectId(1L);project.setProjectName("示例项目");project.setPriority("MEDIUM");project.setMainOwnerUserId(10L);project.setSponsorOwnerUserId(20L);project.setStatus("ACTIVE");project.setDeliveryPolicyVersion("SEPARATED_V1");project.setCostPolicyVersion("ACTUAL_WORK_V1");project.setAccountingState("OPEN");project.setTemplateVersion("LIGHT_V1");project.setBaselineVersion(1);project.setVersion(3);project.setPlanStartDate(Date.valueOf("2026-01-01"));lenient().when(projectMapper.selectProjectByIdForUpdate(1L)).thenReturn(project);
        companyAccess=com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture();
        org.springframework.test.util.ReflectionTestUtils.setField(service,"companyAccess",companyAccess);
}
    @Test void lightweightAuthorizedChangeCreatesNewBaseline(){when(mapper.applyPlanChange(anyMap())).thenReturn(1);service.request(1L,change(),10L,"owner");ArgumentCaptor<Map<String,Object>> b=ArgumentCaptor.forClass(Map.class);verify(mapper).insertBaseline(b.capture());assertEquals(2,b.getValue().get("baselineVersion"));assertEquals("OWNER_AUTHORIZED_CHANGE",b.getValue().get("authorizationSource"));verify(mapper,never()).reviewPlanChange(any());}
    @Test void controlledChangeDoesNotMutateBaselineUntilReview(){project.setTemplateVersion("CONTROLLED_V1");service.request(1L,change(),10L,"owner");ArgumentCaptor<Map<String,Object>> c=ArgumentCaptor.forClass(Map.class);verify(mapper).insertPlanChange(c.capture());assertEquals("SUBMITTED",c.getValue().get("status"));verify(mapper,never()).applyPlanChange(any());}
    @Test void staleBaseCannotBeApproved(){project.setTemplateVersion("CONTROLLED_V1");Map<String,Object> c=row("changeId",8L,"projectId",1L,"baseVersion",0,"status","SUBMITTED","requestUserId",10L,"version",0);when(mapper.selectPlanChange(8L)).thenReturn(c);assertThrows(ServiceException.class,()->service.review(8L,row("version",0,"decision","APPROVED","reason","同意"),20L,"sponsor"));verify(mapper,never()).applyPlanChange(any());}
    @Test void technicalAdministratorCannotApproveInsteadOfSponsor(){Map<String,Object> c=row("changeId",8L,"projectId",1L,"baseVersion",1,"status","SUBMITTED","requestUserId",10L,"version",0);when(mapper.selectPlanChange(8L)).thenReturn(c);assertThrows(ServiceException.class,()->service.review(8L,row("version",0,"decision","APPROVED","reason","同意"),1L,"admin"));}
    @Test void closedProjectCannotCreateNewPlan(){project.setStatus("CLOSED");assertThrows(ServiceException.class,()->service.request(1L,change(),10L,"owner"));verify(mapper,never()).insertPlanChange(any());}
    @Test void unlimitedPlanChangeKeepsNullEndDate() throws Exception
    {
        Map<String,Object> input=change();input.put("planEndDate",null);
        when(mapper.applyPlanChange(anyMap())).thenReturn(1);
        service.request(1L,input,10L,"owner");
        ArgumentCaptor<Map<String,Object>> change=ArgumentCaptor.forClass(Map.class);verify(mapper).applyPlanChange(change.capture());
        assertNull(change.getValue().get("planEndDate"));
        ArgumentCaptor<Map<String,Object>> baseline=ArgumentCaptor.forClass(Map.class);verify(mapper).insertBaseline(baseline.capture());
        assertNull(json.readValue((String)baseline.getValue().get("snapshotJson"),Map.class).get("planEndDate"));
    }
    @Test void renewedBudgetUsesActiveStaffAndPreservesGovernanceSnapshot() throws Exception
    {
        project.setTemplateSnapshotJson("{\"managementMode\":\"LIGHT\",\"budget\":{\"cycle\":\"MONTH\",\"businessAmount\":100}}");
        Map<String,Object> input=change();input.put("planEndDate",null);input.put("budgetLimit",1);input.put("budget",row("cycle","QUARTER","anchorDate","2026-10-01","businessAmount",200));
        when(mapper.selectAssignments(1L)).thenReturn(java.util.Arrays.asList(row("userId",30L,"status","ACTIVE","effectiveFrom","2026-10-01","effectiveTo","2026-12-31"),row("userId",40L,"status","INACTIVE")));
        when(budgets.estimate(any())).thenReturn(row("status","READY","cycle","QUARTER","startDate","2026-10-01","endDate","2026-12-31","businessAmount",200,"personnelAmount",800,"totalAmount",1000));
        when(mapper.applyPlanChange(anyMap())).thenReturn(1);
        service.request(1L,input,10L,"owner");
        ArgumentCaptor<com.ruoyi.business.domain.BusinessProjectProposal> proposal=ArgumentCaptor.forClass(com.ruoyi.business.domain.BusinessProjectProposal.class);verify(budgets).estimate(proposal.capture());
        assertEquals(1,proposal.getValue().getStaffingLines().size());assertEquals("2026-10-01",proposal.getValue().getStaffingLines().get(0).get("planStartDate"));
        ArgumentCaptor<Map<String,Object>> applied=ArgumentCaptor.forClass(Map.class);verify(mapper).applyPlanChange(applied.capture());assertEquals(1000,applied.getValue().get("budgetLimit"));
        Map<String,Object> snapshot=json.readValue((String)applied.getValue().get("templateSnapshotJson"),Map.class);assertEquals("LIGHT",snapshot.get("managementMode"));assertEquals("QUARTER",((Map<?,?>)snapshot.get("budget")).get("cycle"));
    }
    @Test void malformedEndDateCannotSilentlyBecomeUnlimited()
    {
        Map<String,Object> input=change();input.put("planEndDate","not-a-date");
        assertThrows(ServiceException.class,()->service.request(1L,input,10L,"owner"));verify(mapper,never()).applyPlanChange(anyMap());
    }
    @Test void planChangeMatchesProposalReasonAndOptionalAcceptanceCriteria()
    {
        Map<String,Object> input=change();input.put("acceptanceCriteria","");
        when(mapper.applyPlanChange(anyMap())).thenReturn(1);
        service.request(1L,input,10L,"owner");
        ArgumentCaptor<Map<String,Object>> applied=ArgumentCaptor.forClass(Map.class);
        verify(mapper).applyPlanChange(applied.capture());assertEquals("调整立项计划",applied.getValue().get("applicationReason"));
        assertEquals("",applied.getValue().get("acceptanceCriteria"));
        input.put("applicationReason","");
        assertThrows(ServiceException.class,()->service.request(1L,input,10L,"owner"));
    }
    @Test void financialPlanIsValidatedCalculatedAndStoredInNewBaseline() throws Exception
    {
        project.setTemplateSnapshotJson("{\"budget\":{\"mode\":\"TOTAL\",\"scope\":\"FULL_COST\",\"businessAmount\":500}}");
        Map<String,Object> input=change();input.put("projectName","调整后的项目");input.put("priority","HIGH");
        input.put("revenueLines",java.util.Arrays.asList(row("scenario","BASE","revenueType","SERVICE","itemName","服务收入","expectedAmount",1000,"occurrenceType","ONE_TIME","expectedDate","2026-02-01")));
        input.put("expenseLines",java.util.Arrays.asList(row("expenseCategory","OUTSOURCING","itemName","设计外包","purpose","交付设计","amount",200,"occurrenceType","ONE_TIME","occurDate","2026-02-01")));
        input.put("budget",row("mode","TOTAL","scope","FULL_COST","businessAmount",500));
        when(budgets.estimate(any())).thenReturn(row("status","READY","revenueAmount",1000,"plannedBusinessAmount",200,"personnelAmount",300,"plannedTotalCost",500,"profit",500,"totalAmount",800,"businessAmount",500,"monthlyForecasts",java.util.Arrays.asList(row("month","2026-02","profit",500))));
        when(mapper.applyPlanChange(anyMap())).thenReturn(1);
        service.request(1L,input,10L,"owner");
        ArgumentCaptor<Map<String,Object>> applied=ArgumentCaptor.forClass(Map.class);verify(mapper).applyPlanChange(applied.capture());
        assertEquals("调整后的项目",applied.getValue().get("projectName"));assertEquals("HIGH",applied.getValue().get("priority"));
        assertEquals("服务收入",((java.util.List<Map<String,Object>>)applied.getValue().get("revenueLines")).get(0).get("itemName"));
        ArgumentCaptor<Map<String,Object>> baseline=ArgumentCaptor.forClass(Map.class);verify(mapper).insertBaseline(baseline.capture());
        Map<String,Object> snapshot=json.readValue((String)baseline.getValue().get("snapshotJson"),Map.class);
        assertEquals(1,((java.util.List<?>)snapshot.get("expenseLines")).size());assertEquals(500,((Number)((Map<?,?>)snapshot.get("budget")).get("profit")).intValue());
    }
    @Test void planLoadsFinancialLinesFromLatestBaselineThatContainsThem()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(project);when(mapper.selectPlanChanges(1L)).thenReturn(java.util.Collections.emptyList());
        when(mapper.selectBaselines(1L)).thenReturn(java.util.Arrays.asList(
            row("baselineVersion",2,"snapshotJson","{\"objective\":\"only scalar fields\"}"),
            row("baselineVersion",1,"snapshotJson","{\"revenueModel\":\"按服务收费\",\"revenueLines\":[{\"itemName\":\"服务收入\"}],\"expenseLines\":[{\"itemName\":\"外包\"}]}")
        ));
        Map<String,Object> current=(Map<String,Object>)service.plan(1L,10L,false).get("currentPlan");
        assertEquals("按服务收费",current.get("revenueModel"));assertEquals("服务收入",((java.util.List<Map<String,Object>>)current.get("revenueLines")).get(0).get("itemName"));
    }
    @Test void planChangeLoadsAndReplacesSavedAcceptanceTargets() throws Exception
    {
        project.setTemplateSnapshotJson("{\"budget\":{\"mode\":\"TOTAL\"}}");
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(mapper.selectBaselines(1L)).thenReturn(java.util.Collections.singletonList(
            row("baselineVersion",1,"snapshotJson","{\"targetLines\":[{\"targetType\":\"QUANTITY\",\"targetName\":\"旧目标\",\"targetValue\":1,\"unit\":\"个\",\"acceptanceEvidence\":\"验收文件\"}]}")));
        Map<String,Object> current=(Map<String,Object>)service.plan(1L,10L,false).get("currentPlan");
        assertEquals("旧目标",((java.util.List<Map<String,Object>>)current.get("targetLines")).get(0).get("targetName"));
        Map<String,Object> input=change();
        input.put("targetLines",java.util.Collections.singletonList(row("targetType","QUANTITY","targetName","新目标","targetValue",2.1234,"unit","个","acceptanceEvidence","新验收文件")));
        when(budgets.estimate(any())).thenReturn(row("status","READY","mode","TOTAL","totalAmount",100));
        when(mapper.applyPlanChange(anyMap())).thenReturn(1);
        service.request(1L,input,10L,"owner");
        ArgumentCaptor<Map<String,Object>> applied=ArgumentCaptor.forClass(Map.class);verify(mapper).applyPlanChange(applied.capture());
        assertEquals("新目标",((java.util.List<Map<String,Object>>)applied.getValue().get("targetLines")).get(0).get("targetName"));
        Map<String,Object> snapshot=json.readValue((String)applied.getValue().get("templateSnapshotJson"),Map.class);
        assertEquals("新目标",((java.util.List<Map<String,Object>>)snapshot.get("targetLines")).get(0).get("targetName"));
    }
    @Test void planChangeKeepsTenThousandYuanTargetAndRejectsItForOtherCurrencies()
    {
        project.setBaseCurrency("CNY");
        Map<String,Object> input=change();
        input.put("targetLines",java.util.Collections.singletonList(row("targetType","QUANTITY",
            "targetName","合同额","targetValue",10,"unit","万元","acceptanceEvidence","已签合同")));
        when(mapper.applyPlanChange(anyMap())).thenReturn(1);

        service.request(1L,input,10L,"owner");

        ArgumentCaptor<Map<String,Object>> applied=ArgumentCaptor.forClass(Map.class);
        verify(mapper).applyPlanChange(applied.capture());
        Map<String,Object> target=((java.util.List<Map<String,Object>>)applied.getValue().get("targetLines")).get(0);
        assertEquals("FINANCIAL",target.get("targetType"));
        assertEquals(0,((java.math.BigDecimal)target.get("targetValue")).compareTo(new java.math.BigDecimal("10")));
        assertEquals("万元",target.get("unit"));

        project.setBaseCurrency("VND");
        assertThrows(ServiceException.class,()->service.request(1L,input,10L,"owner"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs={10L,20L,1L})
    void monthlyReadProjectionPreservesApprovedBudgetsAndArchivedJson(Long actor) throws Exception
    {
        project.setBaselineVersion(2);project.setBaseCurrency("CNY");
        Map<String,Object> approved=row("cycle","PROJECT","startDate","2026-09-12","endDate","2026-12-11",
            "personnelAmount",27958.25,"totalAmount",27958.25,"revenueAmount",71966.67,
            "monthlyForecasts",java.util.Collections.singletonList(row("month","2026-09","revenueAmount",71966.67)),
            "revenueLines",java.util.Collections.singletonList(row("expectedAmount",47700,"expectedDate",Date.valueOf("2026-09-12").getTime())),
            "expenseLines",java.util.Collections.singletonList(row("amount",100,"occurDate",Date.valueOf("2026-09-12").getTime())));
        String template=json.writeValueAsString(row("budget",approved));project.setTemplateSnapshotJson(template);
        String currentJson=json.writeValueAsString(row("budget",approved,"planStartDate","2026-09-11","planEndDate","2026-12-10",
            "staffingLines",java.util.Collections.singletonList(row("userId",30,"planStartDate",Date.valueOf("2026-09-12").getTime(),"planEndDate",Date.valueOf("2026-12-11").getTime(),"participationMode","CUSTOM"))));
        String olderJson=json.writeValueAsString(row("budget",row("cycle","PROJECT","startDate","2026-01-01","endDate","2026-02-01","totalAmount",100),
            "assignments",java.util.Arrays.asList(row("userId",40,"status","ACTIVE","effectiveFrom","2026-01-10","effectiveTo","2026-01-20"),row("userId",50,"status","INACTIVE"))));
        String changeJson=json.writeValueAsString(row("budget",row("cycle","PROJECT","startDate","2027-01-01","endDate","2027-03-31","totalAmount",200)));
        Map<String,Object> baseline=row("baselineVersion",2,"snapshotJson",currentJson);
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(mapper.selectBaselines(1L)).thenReturn(java.util.Arrays.asList(baseline,row("baselineVersion",1,"snapshotJson",olderJson)));
        when(mapper.selectPlanChanges(1L)).thenReturn(java.util.Collections.singletonList(row("snapshotJson",changeJson,"status","RETURNED")));
        doAnswer(invocation->{
            com.ruoyi.business.domain.BusinessProjectProposal proposal=invocation.getArgument(0);
            Map<String,Object> projected=new java.util.LinkedHashMap<>(proposal.getBudget());
            projected.put("monthlyForecasts",java.util.Collections.singletonList(row("startDate",com.ruoyi.common.utils.DateUtils.parseDateToStr("yyyy-MM-dd",proposal.getPlanStartDate()),"revenueAmount",52766.67)));
            proposal.setBudget(projected);return null;
        }).when(budgets).refreshMonthlyForecast(any());

        Map<String,Object> result=service.plan(1L,actor,actor==1L);

        ArgumentCaptor<com.ruoyi.business.domain.BusinessProjectProposal> proposals=ArgumentCaptor.forClass(com.ruoyi.business.domain.BusinessProjectProposal.class);
        verify(budgets,times(4)).refreshMonthlyForecast(proposals.capture());
        java.util.List<com.ruoyi.business.domain.BusinessProjectProposal> inputs=proposals.getAllValues();
        assertEquals("2026-09-12",com.ruoyi.common.utils.DateUtils.parseDateToStr("yyyy-MM-dd",inputs.get(0).getPlanStartDate()));
        assertEquals("2026-12-11",com.ruoyi.common.utils.DateUtils.parseDateToStr("yyyy-MM-dd",inputs.get(0).getPlanEndDate()));
        assertTrue(inputs.get(0).getStaffingLines().get(0).get("planStartDate") instanceof java.util.Date);
        assertEquals("2026-09-12",inputs.get(0).getRevenueLines().get(0).get("expectedDate"));
        assertEquals("2026-09-12",inputs.get(0).getExpenseLines().get(0).get("occurDate"));
        assertEquals(1,inputs.get(1).getStaffingLines().size());assertEquals("CUSTOM",inputs.get(1).getStaffingLines().get(0).get("participationMode"));
        assertEquals("2027-01-01",com.ruoyi.common.utils.DateUtils.parseDateToStr("yyyy-MM-dd",inputs.get(2).getPlanStartDate()));
        assertEquals(1,inputs.get(3).getStaffingLines().size());
        Map<String,Object> current=(Map<String,Object>)result.get("currentPlan");
        Map<String,Object> displayed=(Map<String,Object>)current.get("budget");
        assertEquals(27958.25,displayed.get("totalAmount"));assertEquals(71966.67,displayed.get("revenueAmount"));
        Map<String,Object> returned=((java.util.List<Map<String,Object>>)result.get("baselines")).get(0);
        assertEquals(currentJson,returned.get("snapshotJson"));assertTrue(returned.containsKey("displaySnapshot"));
        assertFalse(baseline.containsKey("displaySnapshot"));assertEquals(template,project.getTemplateSnapshotJson());
        assertEquals(71966.67,((java.util.List<Map<String,Object>>)project.getBudget().get("monthlyForecasts")).get(0).get("revenueAmount"));
        verify(mapper,never()).selectAssignments(anyLong());verify(mapper,never()).applyPlanChange(anyMap());verify(mapper,never()).insertBaseline(anyMap());verify(budgets,never()).estimate(any());
    }
    @Test void missingHistoricalStaffDoesNotLoadTodaysAssignments() throws Exception
    {
        project.setBaselineVersion(2);
        project.setTemplateSnapshotJson("{\"budget\":{\"cycle\":\"PROJECT\",\"startDate\":\"2026-09-12\",\"endDate\":\"2026-12-11\",\"personnelAmount\":100}}");
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(mapper.selectBaselines(1L)).thenReturn(java.util.Collections.singletonList(row("baselineVersion",1,"snapshotJson","{\"staffingLines\":[{\"userId\":30}]}")));
        service.plan(1L,10L,false);
        ArgumentCaptor<com.ruoyi.business.domain.BusinessProjectProposal> proposal=ArgumentCaptor.forClass(com.ruoyi.business.domain.BusinessProjectProposal.class);
        verify(budgets).refreshMonthlyForecast(proposal.capture());assertTrue(proposal.getValue().getStaffingLines().isEmpty());
        verify(mapper,never()).selectAssignments(anyLong());
    }
    @Test void malformedArchiveRemainsReadableWithoutProjection()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(mapper.selectBaselines(1L)).thenReturn(java.util.Arrays.asList(row("baselineVersion",1,"snapshotJson","bad-json"),row("snapshotJson","null")));
        Map<String,Object> result=service.plan(1L,10L,false);
        for(Map<String,Object> baseline:(java.util.List<Map<String,Object>>)result.get("baselines"))assertFalse(baseline.containsKey("displaySnapshot"));
        verifyNoInteractions(budgets);
    }
    @Test void monthlyProjectionDoesNotBypassProjectReadPermission()
    {
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        assertThrows(ServiceException.class,()->service.plan(1L,99L,false));verifyNoInteractions(budgets);
    }
    @Test void epochCashDatesPassRealBudgetValidationAndAppearOnlyInTheirMonth() throws Exception
    {
        project.setBaseCurrency("CNY");
        long occurrence=Date.valueOf("2026-09-12").getTime();
        Map<String,Object> approved=row("cycle","PROJECT","startDate","2026-09-12","endDate","2026-10-11","personnelAmount",0,"businessAmount",10,
            "revenueLines",java.util.Collections.singletonList(row("expectedAmount",100,"expectedDate",occurrence,"occurrenceType","ONE_TIME")),
            "expenseLines",java.util.Collections.singletonList(row("amount",10,"occurDate",occurrence,"occurrenceType","ONE_TIME")));
        project.setTemplateSnapshotJson(json.writeValueAsString(row("budget",approved)));
        String snapshot=json.writeValueAsString(row("budget",approved));
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(mapper.selectBaselines(1L)).thenReturn(java.util.Collections.singletonList(row("baselineVersion",1,"snapshotJson",snapshot)));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"budgets",new BusinessProjectBudgetService());

        Map<String,Object> result=service.plan(1L,10L,false);

        Map<String,Object> current=(Map<String,Object>)result.get("currentPlan");
        java.util.List<Map<String,Object>> months=(java.util.List<Map<String,Object>>)((Map<String,Object>)current.get("budget")).get("monthlyForecasts");
        assertEquals(2,months.size());
        for(Map<String,Object> month:months){assertEquals("READY",month.get("status"));assertTrue(((java.util.List<?>)month.get("issues")).isEmpty());}
        assertEquals(new java.math.BigDecimal("100.00"),months.get(0).get("revenueAmount"));
        assertEquals(new java.math.BigDecimal("10.00"),months.get(0).get("plannedBusinessAmount"));
        assertEquals(new java.math.BigDecimal("0.00"),months.get(1).get("revenueAmount"));
        assertEquals(new java.math.BigDecimal("0.00"),months.get(1).get("plannedBusinessAmount"));
        assertEquals(snapshot,((java.util.List<Map<String,Object>>)result.get("baselines")).get(0).get("snapshotJson"));
    }
    @Test void memberPreviewLoadsDatedAllocationsMembershipsAndCalendarsAndFreezesThemWithoutWrites() {
        project.setCostPolicyVersion("MEMBER_DAYS_V1");project.setBaseCurrency("CNY");
        project.setTemplateSnapshotJson("{\"budget\":{\"mode\":\"TOTAL\",\"businessAmount\":100}}");
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(mapper.selectMembers(1L)).thenReturn(java.util.Arrays.asList(
            row("userId",30L,"userName","成员甲","memberRole","MEMBER","status","0","joinedDate",Date.valueOf("2026-02-10")),
            row("userId",40L,"userName","已退出成员","memberRole","MEMBER","status","1","joinedDate",Date.valueOf("2026-01-01"),"leftDate",Date.valueOf("2026-02-09"))));
        when(memberCosts.selectPastMemberships(1L)).thenReturn(java.util.Collections.singletonList(
            row("userId",30L,"memberRole","MEMBER","status","1","joinedDate",Date.valueOf("2026-01-01"),"leftDate",Date.valueOf("2026-01-31"))));
        when(mapper.selectAssignments(1L)).thenReturn(java.util.Collections.singletonList(
            row("userId",30L,"inputQuantity",0,"calendarId",2L,"status","ACTIVE","participationMode","FOLLOW_PROJECT","effectiveFrom",Date.valueOf("2026-02-10"))));
        when(mapper.selectCalendars()).thenReturn(java.util.Collections.singletonList(row("calendarId",2L,"workingWeekdays","1,2,3,4,5","dailyMinutes",480)));
        when(projectMapper.selectUserAllocationTimeline(30L)).thenReturn(java.util.Collections.singletonList(
            row("projectId",1L,"allocationId",11L,"allocationValue",30,"confirmationStatus","CONFIRMED","effectiveFrom",Date.valueOf("2026-02-10"))));
        when(projectMapper.selectUserAllocationTimeline(40L)).thenReturn(java.util.Collections.singletonList(
            row("projectId",1L,"allocationId",12L,"allocationValue",50,"effectiveFrom",Date.valueOf("2026-01-01"),"effectiveTo",Date.valueOf("2026-02-09"))));
        when(budgets.estimate(any())).thenReturn(row("status","READY","mode","TOTAL","totalAmount",100));

        Map<String,Object> result=service.preview(1L,change(),10L);

        ArgumentCaptor<com.ruoyi.business.domain.BusinessProjectProposal> captured=ArgumentCaptor.forClass(com.ruoyi.business.domain.BusinessProjectProposal.class);
        verify(budgets).estimate(captured.capture());
        java.util.List<Map<String,Object>> staff=captured.getValue().getStaffingLines();assertEquals(2,staff.size());
        Map<String,Object> person=staff.get(0);assertFalse(person.containsKey("inputQuantity"));
        java.util.List<Map<String,Object>> membership=(java.util.List<Map<String,Object>>)person.get("membershipPeriods");
        assertEquals(2,membership.size());assertEquals("2026-02-10",membership.get(0).get("joinedDate"));
        assertEquals("2026-01-31",membership.get(1).get("leftDate"));
        Map<String,Object> allocation=((java.util.List<Map<String,Object>>)person.get("allocationTimeline")).get(0);
        assertEquals(30,allocation.get("allocationValue"));assertEquals("2026-02-10",allocation.get("effectiveFrom"));
        assertEquals("2026-06-01",allocation.get("projectEndDate"));
        assertEquals(staff,result.get("staffingLines"));
        verify(mapper,never()).applyPlanChange(anyMap());verify(mapper,never()).insertPlanChange(anyMap());
    }
    @Test void nameAndObjectiveChangesRetainApprovedBudgetDespiteHistoricalGaps() throws Exception {
        project.setPlanEndDate(Date.valueOf("2026-06-01"));project.setBudgetLimit(new java.math.BigDecimal("1100"));
        project.setTemplateSnapshotJson("{\"budget\":{\"mode\":\"TOTAL\",\"scope\":\"FULL_COST\",\"cycle\":\"PROJECT\",\"businessAmount\":100,\"personnelAmount\":1000,\"totalAmount\":1100}}");
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(mapper.selectBaselines(1L)).thenReturn(java.util.Collections.singletonList(row("snapshotJson","{\"staffingLines\":[{\"userId\":30,\"inputQuantity\":50,\"planStartDate\":\"2026-01-01\"}]}")));
        Map<String,Object> input=change();input.put("projectName","调整名称");input.put("budget",row("mode","TOTAL","scope","FULL_COST","cycle","MONTH","businessAmount",100.00));
        Map<String,Object> preview=service.preview(1L,input,10L);
        assertEquals(true,preview.get("budgetRetained"));assertEquals(1100,((Map<?,?>)preview.get("budget")).get("totalAmount"));
        assertEquals(30,((Map<?,?>)((java.util.List<?>)preview.get("staffingLines")).get(0)).get("userId"));
        verify(budgets,never()).estimate(any());
        when(mapper.applyPlanChange(anyMap())).thenReturn(1);service.request(1L,input,10L,"owner");
        ArgumentCaptor<Map<String,Object>> applied=ArgumentCaptor.forClass(Map.class);verify(mapper).applyPlanChange(applied.capture());assertEquals(new java.math.BigDecimal("1100"),applied.getValue().get("budgetLimit"));
    }
    @Test void financialChangesStillRequireCompleteDatedCostEstimate(){
        project.setPlanEndDate(Date.valueOf("2026-06-01"));project.setTemplateSnapshotJson("{\"budget\":{\"mode\":\"TOTAL\",\"businessAmount\":100}}");
        when(projectMapper.selectProjectById(1L)).thenReturn(project);when(budgets.estimate(any())).thenReturn(row("status","INCOMPLETE","issues","成员甲缺少历史投入"));
        Map<String,Object> input=change();input.put("planEndDate","2026-07-01");assertTrue(assertThrows(ServiceException.class,()->service.preview(1L,input,10L)).getMessage().contains("缺少历史投入"));
        input.put("planEndDate","2026-06-01");input.put("budget",row("mode","TOTAL","businessAmount",200));assertThrows(ServiceException.class,()->service.preview(1L,input,10L));verify(budgets,times(2)).estimate(any());verify(mapper,never()).applyPlanChange(anyMap());
    }
    @Test void unchangedFinancialLinesIgnoreDatabaseMetadataButChangedAmountRecalculates() throws Exception {
        project.setPlanEndDate(Date.valueOf("2026-06-01"));project.setTemplateSnapshotJson("{\"budget\":{\"mode\":\"TOTAL\",\"businessAmount\":100}}");when(projectMapper.selectProjectById(1L)).thenReturn(project);
        Map<String,Object> line=row("lineId",9,"itemName","服务","revenueType","SERVICE","expectedAmount",1000,"expectedDate","2026-03-01","occurrenceType","MONTHLY","scenario","BASE","amountUnit","CNY","assumptionText","");
        String savedLines=json.writeValueAsString(row("revenueLines",java.util.Collections.singletonList(line)));
        when(mapper.selectBaselines(1L)).thenReturn(java.util.Collections.singletonList(row("snapshotJson",savedLines)));
        Map<String,Object> input=change();input.put("revenueLines",java.util.Collections.singletonList(line));assertEquals(true,service.preview(1L,input,10L).get("budgetRetained"));verify(budgets,never()).estimate(any());
        line.put("expectedAmount",2000);when(budgets.estimate(any())).thenReturn(row("status","READY","totalAmount",100));service.preview(1L,input,10L);verify(budgets).estimate(any());
    }
    @Test void retainedPersonnelSummariesGainNamesParticipationAndExactIssuesWithoutChangingAmountsOrRecords() throws Exception {
        project.setCostPolicyVersion("MEMBER_DAYS_V1");project.setPlanStartDate(Date.valueOf("2026-09-01"));
        String issue="2026-09-01 至 2026-09-17（期间共 13 个工作日） 缺少有效的项目投入比例";
        Map<String,Object> september=row("startDate","2026-09-01","endDate","2026-09-30","status","PENDING","personnelAmount",null,"issues",java.util.Collections.singletonList("蔡新武："+issue),"staffingStatus",java.util.Arrays.asList(
            row("userId",132,"status","PENDING","issues",java.util.Collections.singletonList(issue)),
            row("userId",138,"status","READY","amount",2545.45,"issues",java.util.Collections.emptyList()),
            row("userId",135,"status","READY","amount",0,"issues",java.util.Collections.emptyList())));
        Map<String,Object> original=row("mode","TOTAL","scope","FULL_COST","cycle","MONTH","businessAmount",100,"startDate","2026-10-01","endDate","2026-10-31","personnelAmount",11745.13,"totalAmount",11845.13,"monthlyForecasts",java.util.Collections.singletonList(september));
        project.setTemplateSnapshotJson(json.writeValueAsString(row("budget",original)));String before=project.getTemplateSnapshotJson();
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(mapper.selectMembers(1L)).thenReturn(java.util.Arrays.asList(
            row("userId",132L,"userName","蔡新武","joinedDate","2026-09-01","memberRole","OWNER","status","0"),
            row("userId",138L,"userName","刘鑫","joinedDate","2026-09-20","memberRole","MEMBER","status","0"),
            row("userId",135L,"userName","蒋豪","joinedDate","2026-09-20","leftDate","2026-09-19","memberRole","MEMBER","status","1")));
        when(mapper.selectCalendars()).thenReturn(java.util.Collections.singletonList(row("calendarId",1,"workingWeekdays","1,2,3,4,5","dailyMinutes",480,"effectiveFrom","2020-01-01")));
        Map<String,Object> input=change();input.put("planStartDate","2026-09-01");input.put("planEndDate",null);
        Map<String,Object> result=service.preview(1L,input,10L),budget=(Map<String,Object>)result.get("budget");
        java.util.List<Map<String,Object>> people=(java.util.List<Map<String,Object>>)((Map<?,?>)((java.util.List<?>)budget.get("monthlyForecasts")).get(0)).get("staffingStatus");
        assertEquals(true,result.get("budgetRetained"));assertEquals(11745.13,budget.get("personnelAmount"));assertEquals(before,project.getTemplateSnapshotJson());
        assertEquals("蔡新武",people.get(0).get("userName"));assertEquals(22,people.get(0).get("participationWorkingDays"));assertEquals(java.util.Collections.singletonList(issue),people.get(0).get("issues"));assertNull(people.get(0).get("amount"));
        assertEquals("2026-09-20",((Map<?,?>)((java.util.List<?>)people.get(1).get("participationPeriods")).get(0)).get("startDate"));assertEquals(8,people.get(1).get("participationWorkingDays"));assertEquals(2545.45,people.get(1).get("amount"));
        assertEquals(0,people.get(2).get("participationWorkingDays"));assertEquals(true,people.get(2).get("noParticipation"));
        verify(budgets,never()).estimate(any());verify(projectMapper,never()).selectUserAllocationTimeline(anyLong());verify(mapper,never()).applyPlanChange(anyMap());verify(mapper,never()).insertBaseline(anyMap());
    }
    @Test void missingCalendarInOldCostDetailsIsExplainedWithoutGuessingDaysOrDiscardingAmount() throws Exception {
        project.setCostPolicyVersion("MEMBER_DAYS_V1");project.setPlanStartDate(Date.valueOf("2026-09-01"));
        project.setTemplateSnapshotJson("{\"budget\":{\"mode\":\"TOTAL\",\"cycle\":\"MONTH\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-30\",\"staffingStatus\":[{\"userId\":132,\"status\":\"READY\",\"amount\":3300}]}}");
        when(projectMapper.selectProjectById(1L)).thenReturn(project);when(mapper.selectMembers(1L)).thenReturn(java.util.Collections.singletonList(row("userId",132L,"userName","蔡新武","joinedDate","2026-09-01","memberRole","OWNER","status","0")));
        Map<String,Object> input=change();input.put("planStartDate","2026-09-01");input.put("planEndDate",null);
        Map<String,Object> budget=(Map<String,Object>)service.preview(1L,input,10L).get("budget"),person=(Map<String,Object>)((java.util.List<?>)budget.get("staffingStatus")).get(0);
        assertEquals(3300,person.get("amount"));assertEquals("READY",person.get("status"));assertFalse(person.containsKey("participationWorkingDays"));assertTrue(person.get("displayIssues").toString().contains("2026-09-01 缺少有效工作日历"));
        verify(budgets,never()).estimate(any());verify(mapper,never()).applyPlanChange(anyMap());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"TIMESTAMP","DATE","STRING"})
    void archivedMonthDatesAreEditableAndPreviewAndSaveRetainUnchangedBudget(String representation) throws Exception {
        project.setBaseCurrency("CNY");project.setPlanEndDate(Date.valueOf("2026-06-01"));
        project.setTemplateSnapshotJson("{\"budget\":{\"mode\":\"TOTAL\",\"businessAmount\":100,\"totalAmount\":1100}}");
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        Object date="TIMESTAMP".equals(representation)?Date.valueOf("2026-03-01").getTime():"DATE".equals(representation)?Date.valueOf("2026-03-01"):"2026-03-01";
        Map<String,Object> revenue=row("itemName","服务","revenueType","SERVICE","expectedAmount",1000,"expectedDate",date,"occurrenceType","ONE_TIME");
        Map<String,Object> expense=row("itemName","外包","expenseCategory","OTHER","purpose","交付","amount",100,"occurDate",date,"occurrenceType","ONE_TIME");
        Map<String,Object> target=row("targetType","FINANCIAL","targetName","合同额","targetValue",1000,"unit","元","acceptanceEvidence","合同","dueDate",date);
        String archived=json.writeValueAsString(row("revenueLines",java.util.Collections.singletonList(revenue),"expenseLines",java.util.Collections.singletonList(expense),"targetLines",java.util.Collections.singletonList(target)));
        when(mapper.selectBaselines(1L)).thenReturn(java.util.Collections.singletonList(row("baselineVersion",1,"snapshotJson",archived)));

        Map<String,Object> result=service.plan(1L,10L,false),current=(Map<String,Object>)result.get("currentPlan");
        for(String[] fields:new String[][]{{"revenueLines","expectedDate"},{"expenseLines","occurDate"},{"targetLines","dueDate"}})
            assertEquals("2026-03-01",((Map<?,?>)((java.util.List<?>)current.get(fields[0])).get(0)).get(fields[1]));
        assertEquals(archived,((Map<?,?>)((java.util.List<?>)result.get("baselines")).get(0)).get("snapshotJson"));
        Map<String,Object> input=change();input.put("revenueLines",java.util.Collections.singletonList(revenue));input.put("expenseLines",java.util.Collections.singletonList(expense));input.put("targetLines",java.util.Collections.singletonList(target));
        Map<String,Object> preview=service.preview(1L,input,10L);assertEquals(true,preview.get("budgetRetained"));
        assertEquals("2026-03-01",((Map<?,?>)((java.util.List<?>)preview.get("targetLines")).get(0)).get("dueDate"));
        verify(mapper,never()).insertPlanChange(anyMap());
        when(mapper.applyPlanChange(anyMap())).thenReturn(1);service.request(1L,input,10L,"owner");
        ArgumentCaptor<Map<String,Object>> applied=ArgumentCaptor.forClass(Map.class);verify(mapper).applyPlanChange(applied.capture());
        assertEquals("2026-03-01",((Map<?,?>)((java.util.List<?>)applied.getValue().get("revenueLines")).get(0)).get("expectedDate"));
        assertEquals("2026-03-01",((Map<?,?>)((java.util.List<?>)applied.getValue().get("expenseLines")).get(0)).get("occurDate"));
        verify(budgets,never()).estimate(any());
    }
    @Test void archivedMonthDatesStillRejectMissingAndOutOfRangeInputsAndRecalculateChangedAmounts() throws Exception {
        project.setBaseCurrency("CNY");project.setPlanEndDate(Date.valueOf("2026-06-01"));
        project.setTemplateSnapshotJson("{\"budget\":{\"mode\":\"TOTAL\",\"businessAmount\":100}}");
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
        Map<String,Object> line=row("itemName","服务","revenueType","SERVICE","expectedAmount",1000,"expectedDate",Date.valueOf("2026-03-01").getTime(),"occurrenceType","ONE_TIME");
        String archived=json.writeValueAsString(row("revenueLines",java.util.Collections.singletonList(line)));
        when(mapper.selectBaselines(1L)).thenReturn(java.util.Collections.singletonList(row("snapshotJson",archived)));
        Map<String,Object> input=change();input.put("revenueLines",java.util.Collections.singletonList(line));
        line.put("expectedDate",null);assertTrue(assertThrows(ServiceException.class,()->service.preview(1L,input,10L)).getMessage().contains("月份不能为空"));
        line.put("expectedDate",Date.valueOf("2027-01-01").getTime());assertTrue(assertThrows(ServiceException.class,()->service.preview(1L,input,10L)).getMessage().contains("结束月后6个月"));
        line.put("expectedDate","2026-03-01");line.put("expectedAmount",2000);
        when(budgets.estimate(any())).thenReturn(row("status","READY","totalAmount",100));
        service.preview(1L,input,10L);verify(budgets).estimate(any());
        verify(mapper,never()).insertPlanChange(anyMap());verify(mapper,never()).applyPlanChange(anyMap());
    }
    @Test void oldRegionalBudgetPreviewUsesNewMonthRuleWithoutRewritingApprovedSnapshot(){
        project.setPlanEndDate(Date.valueOf("2026-06-01"));
        String approved="{\"budget\":{\"mode\":\"TOTAL\",\"businessAmount\":100,\"personnelCostRule\":\"REGION_STANDARD_PROJECT_DAY_V4\",\"personnelAmount\":3337.84}}";
        project.setTemplateSnapshotJson(approved);when(projectMapper.selectProjectById(1L)).thenReturn(project);
        when(budgets.estimate(any())).thenReturn(row("status","READY","personnelCostRule","NATURAL_MONTH_AND_REGIONAL_DAYS_V5","personnelAmount",3300,"totalAmount",3400));
        Map<String,Object> preview=service.preview(1L,change(),10L);
        assertEquals(3300,((Map<?,?>)preview.get("budget")).get("personnelAmount"));assertNotEquals(true,preview.get("budgetRetained"));
        assertEquals(approved,project.getTemplateSnapshotJson());verify(mapper,never()).applyPlanChange(anyMap());verify(mapper,never()).insertBaseline(anyMap());
    }
    private Map<String,Object> change(){return row("version",3,"reason","增加交付验证","objective","完成成果交付","applicationReason","调整立项计划","planStartDate","2026-01-01","planEndDate","2026-06-01","acceptanceCriteria","检查成果清单");}
}
