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
    private Map<String,Object> change(){return row("version",3,"reason","增加交付验证","objective","完成成果交付","applicationReason","调整立项计划","planStartDate","2026-01-01","planEndDate","2026-06-01","acceptanceCriteria","检查成果清单");}
}
