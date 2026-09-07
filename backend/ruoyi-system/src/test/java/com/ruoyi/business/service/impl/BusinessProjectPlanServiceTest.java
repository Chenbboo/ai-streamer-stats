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
    @Mock BusinessProjectMapper projectMapper;
    @Mock BusinessProjectWorkMapper mapper;
    @Spy ObjectMapper json=new ObjectMapper();
    @InjectMocks BusinessProjectPlanService service;
    BusinessProject project;
    @BeforeEach void setup(){project=new BusinessProject();project.setProjectId(1L);project.setMainOwnerUserId(10L);project.setSponsorOwnerUserId(20L);project.setStatus("ACTIVE");project.setDeliveryPolicyVersion("SEPARATED_V1");project.setCostPolicyVersion("ACTUAL_WORK_V1");project.setAccountingState("OPEN");project.setTemplateVersion("LIGHT_V1");project.setBaselineVersion(1);project.setVersion(3);project.setPlanStartDate(Date.valueOf("2026-01-01"));lenient().when(projectMapper.selectProjectByIdForUpdate(1L)).thenReturn(project);}
    @Test void lightweightAuthorizedChangeCreatesNewBaseline(){when(mapper.applyPlanChange(anyMap())).thenReturn(1);service.request(1L,change(),10L,"owner");ArgumentCaptor<Map<String,Object>> b=ArgumentCaptor.forClass(Map.class);verify(mapper).insertBaseline(b.capture());assertEquals(2,b.getValue().get("baselineVersion"));assertEquals("OWNER_AUTHORIZED_CHANGE",b.getValue().get("authorizationSource"));verify(mapper,never()).reviewPlanChange(any());}
    @Test void controlledChangeDoesNotMutateBaselineUntilReview(){project.setTemplateVersion("CONTROLLED_V1");service.request(1L,change(),10L,"owner");ArgumentCaptor<Map<String,Object>> c=ArgumentCaptor.forClass(Map.class);verify(mapper).insertPlanChange(c.capture());assertEquals("SUBMITTED",c.getValue().get("status"));verify(mapper,never()).applyPlanChange(any());}
    @Test void staleBaseCannotBeApproved(){project.setTemplateVersion("CONTROLLED_V1");Map<String,Object> c=row("changeId",8L,"projectId",1L,"baseVersion",0,"status","SUBMITTED","requestUserId",10L,"version",0);when(mapper.selectPlanChange(8L)).thenReturn(c);assertThrows(ServiceException.class,()->service.review(8L,row("version",0,"decision","APPROVED","reason","同意"),20L,"sponsor"));verify(mapper,never()).applyPlanChange(any());}
    @Test void technicalAdministratorCannotApproveInsteadOfSponsor(){Map<String,Object> c=row("changeId",8L,"projectId",1L,"baseVersion",1,"status","SUBMITTED","requestUserId",10L,"version",0);when(mapper.selectPlanChange(8L)).thenReturn(c);assertThrows(ServiceException.class,()->service.review(8L,row("version",0,"decision","APPROVED","reason","同意"),1L,"admin"));}
    @Test void forecastDoesNotReplaceApprovedBaseline(){when(mapper.touchProject(anyMap())).thenReturn(1);service.forecast(1L,row("version",3,"forecastEndDate","2026-06-10","reason","更新剩余预测"),10L,"owner");verify(mapper).insertForecast(anyMap());verify(mapper,never()).insertBaseline(anyMap());verify(mapper,never()).applyPlanChange(anyMap());}
    @Test void closedProjectCannotCreateNewPlan(){project.setStatus("CLOSED");assertThrows(ServiceException.class,()->service.request(1L,change(),10L,"owner"));verify(mapper,never()).insertPlanChange(any());}
    private Map<String,Object> change(){return row("version",3,"reason","增加交付验证","objective","完成成果交付","planStartDate","2026-01-01","planEndDate","2026-06-01","acceptanceCriteria","检查成果清单");}
}
