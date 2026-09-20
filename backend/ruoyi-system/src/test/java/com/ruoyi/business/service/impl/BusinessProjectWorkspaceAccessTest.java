package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.Date;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessProjectWorkMapper;

class BusinessProjectWorkspaceAccessTest
{
    @Test void resourceOptionsUseActorScopeCombineSponsorProjectsAndExposeOnlyActualProjectIdentity()
    {
        BusinessProjectMapper projects=mock(BusinessProjectMapper.class);
        BusinessProjectWorkService service=new BusinessProjectWorkService();{org.springframework.test.util.ReflectionTestUtils.setField(service,"companyAccess",com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture());}ReflectionTestUtils.setField(service,"projectMapper",projects);
        BusinessProject actual=project(1L,"MEMBER_DAYS_V1"),legacy=project(2L,"LEGACY_V1"),sponsored=project(3L,"MEMBER_DAYS_V1");
        when(projects.selectProjectList(anyMap())).thenAnswer(call->{Map<String,Object> query=call.getArgument(0);assertEquals(7L,query.get("userId"));assertEquals(false,query.get("viewAll"));return Boolean.TRUE.equals(query.get("boss"))?Arrays.asList(actual,sponsored):Arrays.asList(actual,legacy);});
        List<Map<String,Object>> options=service.options(7L,false);
        assertEquals(2,options.size());assertEquals(1L,options.get(0).get("projectId"));assertEquals(3L,options.get(1).get("projectId"));
        for(Map<String,Object> option:options)assertEquals(new HashSet<>(Arrays.asList("projectId","projectName","costPolicyVersion")),option.keySet());
    }
    @Test void sponsorCanReadPlanButOnlyProjectOwnerCanRequestChange()
    {
        BusinessProjectMapper projects=mock(BusinessProjectMapper.class);BusinessProjectWorkMapper work=mock(BusinessProjectWorkMapper.class);
        BusinessProjectPlanService service=new BusinessProjectPlanService();{org.springframework.test.util.ReflectionTestUtils.setField(service,"companyAccess",com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture());}ReflectionTestUtils.setField(service,"projectMapper",projects);ReflectionTestUtils.setField(service,"mapper",work);
        when(projects.selectProjectById(1L)).thenReturn(project(1L,"ACTUAL_WORK_V1"));
        assertEquals(false,service.plan(1L,20L,false).get("canRequestChange"));
        assertEquals(true,service.plan(1L,10L,false).get("canRequestChange"));
    }
    @Test void workWorkspaceReturnsDateOnlyBoundsForTheReportForm()
    {
        BusinessProjectMapper projects=mock(BusinessProjectMapper.class);BusinessProjectWorkMapper work=mock(BusinessProjectWorkMapper.class);
        BusinessProjectWorkService service=new BusinessProjectWorkService();{org.springframework.test.util.ReflectionTestUtils.setField(service,"companyAccess",com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture());}ReflectionTestUtils.setField(service,"projectMapper",projects);ReflectionTestUtils.setField(service,"mapper",work);
        BusinessProject project=project(1L,"ACTUAL_WORK_V1");project.setPlanStartDate(Date.valueOf("2026-09-01"));project.setPlanEndDate(Date.valueOf("2026-10-01"));project.setActualStartDate(Date.valueOf("2026-09-03"));when(projects.selectProjectById(1L)).thenReturn(project);
        Map<?,?> summary=(Map<?,?>)service.workspace(1L,Collections.emptyMap(),10L,false).get("project");
        assertEquals("2026-09-01",summary.get("planStartDate"));assertEquals("2026-10-01",summary.get("planEndDate"));assertEquals("2026-09-03",summary.get("actualStartDate"));
    }
    @Test void parentOwnerCanReadChildPlanAndAllWorkWithoutManagingIt()
    {
        BusinessProjectMapper projects=mock(BusinessProjectMapper.class);BusinessProjectWorkMapper work=mock(BusinessProjectWorkMapper.class);
        BusinessProject child=project(2L,"ACTUAL_WORK_V1");child.setParentId(1L);child.setMainOwnerUserId(30L);
        when(projects.selectProjectById(2L)).thenReturn(child);when(projects.selectProjectById(1L)).thenReturn(project(1L,"ACTUAL_WORK_V1"));
        BusinessProjectPlanService plans=new BusinessProjectPlanService();ReflectionTestUtils.setField(plans,"companyAccess",com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture());
        ReflectionTestUtils.setField(plans,"projectMapper",projects);ReflectionTestUtils.setField(plans,"mapper",work);
        assertEquals(false,plans.plan(2L,10L,false).get("canRequestChange"));
        assertEquals(false,plans.plan(2L,10L,false).get("canForecast"));

        BusinessProjectWorkService records=new BusinessProjectWorkService();ReflectionTestUtils.setField(records,"companyAccess",com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture());
        ReflectionTestUtils.setField(records,"projectMapper",projects);ReflectionTestUtils.setField(records,"mapper",work);
        when(work.selectEntries(anyMap())).thenReturn(Collections.singletonList(BusinessProjectWorkServiceTest.row(
            "entryId",7L,"userId",30L,"status","SUBMITTED","createUserId",30L)));
        Map<String,Object> result=records.workspace(2L,Collections.emptyMap(),10L,false);
        assertEquals(1,((List<?>)result.get("entries")).size());assertEquals(false,result.get("canManage"));assertEquals(false,result.get("canReport"));
        org.mockito.ArgumentCaptor<Map<String,Object>> query=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(work).selectEntries(query.capture());assertFalse(query.getValue().containsKey("userId"));
        when(work.selectEntry(7L)).thenReturn(BusinessProjectWorkServiceTest.row(
            "entryId",7L,"projectId",2L,"userId",30L));
        when(work.selectAudit(7L)).thenReturn(Collections.singletonList(BusinessProjectWorkServiceTest.row("action","SUBMIT")));
        assertEquals(1,records.history(7L,10L,false).size());
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->records.history(7L,99L,false));
    }
    private BusinessProject project(Long id,String policy)
    {
        BusinessProject p=new BusinessProject();p.setProjectId(id);p.setProjectName("project-"+id);p.setCostPolicyVersion(policy);p.setMainOwnerUserId(10L);p.setSponsorOwnerUserId(20L);p.setStatus("ACTIVE");p.setAccountingState("OPEN");p.setDeliveryPolicyVersion("SEPARATED_V1");return p;
    }
}
