package com.ruoyi.business.service.impl;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class BusinessAccountingPrivacyTest
{
    @Mock BusinessAccountingMapper mapper;
    @InjectMocks BusinessAccountingServiceImpl service;
    private Map<String,Object> detail()
    {
        Map<String,Object> result=new HashMap<>();result.put("resultId",7L);
        Map<String,Object> item=new HashMap<>();item.put("componentCode","PERSONNEL_COST_PERSON");item.put("componentName","Example member");
        item.put("amount",new BigDecimal("100"));item.put("monthlyCost",new BigDecimal("99999"));item.put("basisJson","private-rate-snapshot");item.put("calculationDetail","private-rate-snapshot");
        when(mapper.selectDailyResults(anyMap())).thenReturn(Collections.singletonList(result));
        when(mapper.selectDailyResultItems(7L)).thenReturn(Collections.singletonList(item));return item;
    }
    @Test void financialVisibilityDoesNotConveyRawRateVisibility()
    {
        Map<String,Object> original=detail();Map<String,Object> result=service.resultDetail(7L,9L,false);
        Map<?,?> item=((List<Map<?,?>>)result.get("personnelItems")).get(0);
        assertFalse((Boolean)result.get("rawCostVisible"));assertNull(item.get("monthlyCost"));assertNull(item.get("basisJson"));
        assertFalse(String.valueOf(item.get("calculationDetail")).contains("private-rate"));assertEquals(new BigDecimal("100"),item.get("amount"));
        assertTrue(original.containsKey("monthlyCost"));
    }
    @Test void explicitAdministratorMayReadSavedRateBasis()
    {
        detail();Map<String,Object> result=service.resultDetail(7L,1L,true);
        Map<?,?> item=((List<Map<?,?>>)result.get("personnelItems")).get(0);
        assertTrue((Boolean)result.get("rawCostVisible"));assertEquals("private-rate-snapshot",item.get("basisJson"));
    }
}
