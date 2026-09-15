package com.ruoyi.business.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessPublicExpenseMapper;
import com.ruoyi.business.service.IBusinessAccountingService;

class BusinessPublicExpenseDailyServiceTest {
    @Test void naturalDaysAndLastDayRemainderConserveTheMonthlyAmount(){
        for(int days:new int[]{1,4,28,29,30,31}) {
            List<Map<String,Object>> rows=BusinessPublicExpenseDailyService.schedule(1L,2L,new BigDecimal("4800.01"),LocalDate.of(2026,1,1),LocalDate.of(2026,1,days),false);
            assertEquals(days,rows.size());
            assertEquals(new BigDecimal("4800.01"),rows.stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add));
            assertTrue(rows.stream().allMatch(r->"ESTIMATED".equals(r.get("status"))));
        }
        List<Map<String,Object>> tiny=BusinessPublicExpenseDailyService.schedule(1L,2L,new BigDecimal("0.01"),LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),true);
        assertEquals(new BigDecimal("0.01"),tiny.get(30).get("amount"));
        assertEquals(new BigDecimal("0.00"),tiny.get(0).get("amount"));
    }
    @Test void submittedAllocationUsesProjectDatesAndRepeatedSyncDoesNotCreateVersions(){
        BusinessPublicExpenseDailyService service=new BusinessPublicExpenseDailyService();
        BusinessPublicExpenseMapper mapper=mock(BusinessPublicExpenseMapper.class);
        BusinessProjectMapper projects=mock(BusinessProjectMapper.class);
        IBusinessAccountingService accounting=mock(IBusinessAccountingService.class);
        ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"projects",projects);ReflectionTestUtils.setField(service,"accounting",accounting);
        Map<String,Object> bill=row("billId",1L,"companyDeptId",10L,"month","2026-01","status","PUBLISHED","recognitionMode","DAILY_V1");
        when(mapper.selectBill(1L)).thenReturn(bill);when(mapper.selectBillForUpdate(1L)).thenReturn(bill);
        when(mapper.selectOwnerAllocations(1L)).thenReturn(Arrays.asList(row("allocationId",3L,"status","SUBMITTED")));
        when(mapper.selectProjectAllocations(3L)).thenReturn(Arrays.asList(row("projectId",2L,"amount",new BigDecimal("4800"))));
        BusinessProject p=new BusinessProject();p.setAccountingState("OPEN");p.setPlanStartDate(java.sql.Date.valueOf("2026-01-15"));p.setPlanEndDate(java.sql.Date.valueOf("2026-01-18"));
        when(projects.selectProjectByIdForUpdate(2L)).thenReturn(p);
        service.synchronize(1L);
        verify(mapper,times(4)).upsertDailyRow(anyMap());verify(accounting,times(4)).recalculatePublicExpenseCost(eq(2L),any(),anyString());
        List<Map<String,Object>> saved=BusinessPublicExpenseDailyService.schedule(1L,2L,new BigDecimal("4800"),LocalDate.of(2026,1,15),LocalDate.of(2026,1,18),false);
        when(mapper.selectDailyRows(1L)).thenReturn(saved);clearInvocations(mapper,accounting);
        service.synchronize(1L);verify(mapper,never()).upsertDailyRow(anyMap());verify(accounting,never()).recalculatePublicExpenseCost(anyLong(),any(),anyString());
        bill.put("status","DRAFT");service.synchronize(1L);
        verify(mapper,times(4)).deleteDailyRow(anyMap());verify(accounting,times(4)).recalculatePublicExpenseCost(eq(2L),any(),anyString());
    }
    private static Map<String,Object> row(Object... pairs){Map<String,Object> out=new HashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
}
