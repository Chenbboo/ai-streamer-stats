package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.*;
import com.ruoyi.business.mapper.*;
import com.ruoyi.common.exception.ServiceException;

class BusinessKpiPauseTest
{
    private BusinessProjectKpi target(String code,String status)
    {
        BusinessProjectKpi k=new BusinessProjectKpi();k.setKpiCode(code);k.setStatus(status);return k;
    }
    @Test void mixedPlanCanCompleteWithoutPausedResultButRequiresRunningResult()
    {
        BusinessProjectKpiServiceImpl service=new BusinessProjectKpiServiceImpl();
        ReflectionTestUtils.setField(service,"businessFileService",mock(com.ruoyi.business.service.BusinessFileService.class));
        BusinessProjectKpiPlanItem paused=new BusinessProjectKpiPlanItem();paused.setItemId(1L);paused.setPaused(true);
        BusinessProjectKpiPlanItem active=new BusinessProjectKpiPlanItem();active.setItemId(2L);
        active.setTargetValue(BigDecimal.TEN);active.setWeight(new BigDecimal("100"));active.setDirection("HIGHER_BETTER");
        BusinessProjectKpiResult result=new BusinessProjectKpiResult();result.setPlanItemId(2L);result.setActualValue(BigDecimal.TEN);result.setResultNote("完成");
        List<BusinessProjectKpiPlanItem> items=Arrays.asList(paused,active);
        assertDoesNotThrow(()->ReflectionTestUtils.invokeMethod(service,"requireComplete",items,Collections.singletonList(result)));
        assertEquals(true,ReflectionTestUtils.invokeMethod(service,"allTargetsMet",items,Collections.singletonList(result)));
        assertThrows(ServiceException.class,()->ReflectionTestUtils.invokeMethod(service,"requireComplete",items,Collections.emptyList()));
        paused.setPaused(false);
        assertThrows(ServiceException.class,()->ReflectionTestUtils.invokeMethod(service,"requireComplete",items,Collections.singletonList(result)));
    }

    @Test void onlyOwnerCanPauseAndStartCurrentTarget()
    {
        BusinessProjectServiceImpl service=new BusinessProjectServiceImpl();BusinessProjectMapper projects=mock(BusinessProjectMapper.class);
        ReflectionTestUtils.setField(service,"mapper",projects);
        BusinessProject project=new BusinessProject();project.setProjectId(1L);project.setMainOwnerUserId(9L);project.setStatus("ACTIVE");
        BusinessProjectKpi target=target("A","CURRENT");target.setKpiId(2L);target.setProjectId(1L);
        when(projects.selectProjectByIdForUpdate(1L)).thenReturn(project);
        when(projects.selectProjectKpiById(2L)).thenReturn(target);
        when(projects.changeProjectKpiStatus(1L,2L,"CURRENT","PAUSED","owner")).thenReturn(1);
        assertThrows(ServiceException.class,()->service.changeKpiStatus(1L,2L,"PAUSE",10L,"other",false));
        assertDoesNotThrow(()->service.changeKpiStatus(1L,2L,"PAUSE",9L,"owner",false));
        verify(projects).changeProjectKpiStatus(1L,2L,"CURRENT","PAUSED","owner");
        target.setStatus("PAUSED");
        when(projects.changeProjectKpiStatus(1L,2L,"PAUSED","CURRENT","owner")).thenReturn(1);
        assertDoesNotThrow(()->service.changeKpiStatus(1L,2L,"START",9L,"owner",false));
        verify(projects).changeProjectKpiStatus(1L,2L,"PAUSED","CURRENT","owner");
        project.setStatus("CLOSED");
        assertThrows(ServiceException.class,()->service.changeKpiStatus(1L,2L,"PAUSE",9L,"owner",false));
    }
}
