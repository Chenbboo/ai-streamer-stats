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
    @Test void forecastDoesNotReplaceApprovedBaseline(){when(mapper.touchProject(anyMap())).thenReturn(1);service.forecast(1L,row("version",3,"forecastEndDate","2026-06-10","reason","更新剩余预测"),10L,"owner");verify(mapper).insertForecast(anyMap());verify(mapper,never()).insertBaseline(anyMap());verify(mapper,never()).applyPlanChange(anyMap());}
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
    private Map<String,Object> change(){return row("version",3,"reason","增加交付验证","objective","完成成果交付","applicationReason","调整立项计划","planStartDate","2026-01-01","planEndDate","2026-06-01","acceptanceCriteria","检查成果清单");}
}
