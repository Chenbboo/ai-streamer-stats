package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.common.exception.ServiceException;

class BusinessInternalProjectTransferTest
{
    BusinessAccountingMapper mapper=mock(BusinessAccountingMapper.class);
    BusinessAccountingServiceImpl service=new BusinessAccountingServiceImpl();
    Map<Long,Map<String,Object>> projects=new HashMap<>();
    Map<Long,BusinessOperatingFact> facts=new LinkedHashMap<>();
    AtomicLong sequence=new AtomicLong(100);

    @BeforeEach void setup()
    {
        ReflectionTestUtils.setField(service,"mapper",mapper);
        ReflectionTestUtils.setField(service,"businessFileService",mock(BusinessFileService.class));
        projects.put(1L,project(1L));projects.put(2L,project(2L));projects.put(3L,project(3L));
        when(mapper.selectProjectForAccountingForUpdate(anyLong())).thenAnswer(c->projects.get(c.getArgument(0)));
        when(mapper.selectProjectForAccounting(anyLong())).thenAnswer(c->projects.get(c.getArgument(0)));
        when(mapper.selectCategoryById(6L)).thenReturn(category(6L,"INTERNAL_PROJECT_COST","COST"));
        when(mapper.selectCategoryById(8L)).thenReturn(category(8L,"OTHER_EXPENSE","COST"));
        when(mapper.selectCategoryByCode("INTERNAL_PROJECT_REVENUE")).thenReturn(category(7L,"INTERNAL_PROJECT_REVENUE","REVENUE"));
        when(mapper.selectCategoryById(7L)).thenReturn(category(7L,"INTERNAL_PROJECT_REVENUE","REVENUE"));
        when(mapper.selectFactById(anyLong())).thenAnswer(c->facts.get(c.getArgument(0)));
        when(mapper.selectFactByIdForUpdate(anyLong())).thenAnswer(c->facts.get(c.getArgument(0)));
        when(mapper.selectFactByIdempotencyKey(anyString())).thenAnswer(c->facts.values().stream()
            .filter(f->c.getArgument(0).equals(f.getIdempotencyKey())).findFirst().orElse(null));
        doAnswer(c->{BusinessOperatingFact f=c.getArgument(0);f.setFactId(sequence.incrementAndGet());f.setVersion(0);facts.put(f.getFactId(),f);return 1;})
            .when(mapper).insertFact(any());
        when(mapper.confirmFact(anyLong(),anyLong(),anyString(),anyInt())).thenAnswer(c->{facts.get(c.getArgument(0)).setStatus("CONFIRMED");return 1;});
        when(mapper.markFactReversed(anyLong(),anyString(),anyInt())).thenAnswer(c->{facts.get(c.getArgument(0)).setStatus("REVERSED");return 1;});
        when(mapper.sumProjectFacts(anyLong(),any())).thenReturn(Collections.emptyMap());
    }

    @Test void sameDaySameCurrencyIncomeIsGeneratedAndRetryDoesNotDuplicate()
    {
        BusinessOperatingFact expense=service.saveProjectDailySpend(input(2L),9L,"owner",false);
        BusinessOperatingFact income=incomeFor(expense.getFactId());
        assertEquals(2L,income.getProjectId());assertEquals(expense.getAmount(),income.getAmount());
        assertEquals(expense.getBizDate(),income.getBizDate());assertEquals("CNY",income.getCurrency());
        assertEquals("CONFIRMED",income.getStatus());assertEquals("REVENUE",income.getFactKind());
        assertTrue(income.getDescription().contains("项目1"));
        assertSame(expense,service.saveProjectDailySpend(input(2L),9L,"owner",false));assertEquals(2,facts.size());
        verify(mapper).retireCurrentResult(eq(2L),any());verify(mapper).retireCurrentResult(eq(1L),any());
    }

    @Test void retryCannotChangeRecipientOrCategory()
    {
        service.saveProjectDailySpend(input(2L),9L,"owner",false);
        assertThrows(ServiceException.class,()->service.saveProjectDailySpend(input(3L),9L,"owner",false));
        BusinessOperatingFact changed=input(2L);changed.setCategoryId(8L);
        assertThrows(ServiceException.class,()->service.saveProjectDailySpend(changed,9L,"owner",false));
        assertEquals(2,facts.size());
    }

    @Test void changingRecipientAndAmountReversesOldIncomeAndGeneratesNewIncome()
    {
        BusinessOperatingFact old=service.saveProjectDailySpend(input(2L),9L,"owner",false);
        BusinessOperatingFact oldIncome=incomeFor(old.getFactId());
        BusinessOperatingFact edit=input(3L);edit.setFactId(old.getFactId());edit.setRequestId("edit-request-123456789");edit.setAmount(new BigDecimal("80.00"));
        BusinessOperatingFact replacement=service.saveProjectDailySpend(edit,9L,"owner",false);
        assertEquals("REVERSED",old.getStatus());assertEquals("REVERSED",oldIncome.getStatus());
        assertEquals(3L,incomeFor(replacement.getFactId()).getProjectId());
        assertEquals(new BigDecimal("80.00"),incomeFor(replacement.getFactId()).getAmount());
        assertEquals(0,net(2L,"REVENUE").signum());assertEquals(new BigDecimal("80.00"),net(3L,"REVENUE"));
        assertEquals(new BigDecimal("80.00"),net(1L,"COST"));
    }

    @Test void changingCategoryRemovesLinkedIncomeAndClearsRecipient()
    {
        BusinessOperatingFact old=service.saveProjectDailySpend(input(2L),9L,"owner",false);
        BusinessOperatingFact edit=input(2L);edit.setFactId(old.getFactId());edit.setCategoryId(8L);edit.setRequestId("edit-request-123456789");
        BusinessOperatingFact replacement=service.saveProjectDailySpend(edit,9L,"owner",false);
        assertNull(replacement.getTargetProjectId());assertEquals(0,net(2L,"REVENUE").signum());
        assertEquals(new BigDecimal("50.00"),net(1L,"COST"));
    }

    @Test void reversalCancelsBothSidesAndCannotBeRepeated()
    {
        BusinessOperatingFact old=service.saveProjectDailySpend(input(2L),9L,"owner",false);
        service.reverseProjectDailySpend(old.getFactId(),"错误支出",9L,"owner",false);
        assertEquals(0,net(1L,"COST").signum());assertEquals(0,net(2L,"REVENUE").signum());
        assertThrows(ServiceException.class,()->service.reverseProjectDailySpend(old.getFactId(),"重复",9L,"owner",false));
        assertEquals(4,facts.size());
    }

    @Test void closedRecipientPreventsReversalBeforeEitherSideIsChanged()
    {
        BusinessOperatingFact old=service.saveProjectDailySpend(input(2L),9L,"owner",false);
        projects.get(2L).put("accountingState","CLOSED");
        assertThrows(ServiceException.class,()->service.reverseProjectDailySpend(old.getFactId(),"修改",9L,"owner",false));
        assertEquals("CONFIRMED",old.getStatus());assertEquals(2,facts.size());
    }

    @Test void legacyInternalExpenseWithoutRecipientDoesNotInventRevenueWhenReversed()
    {
        BusinessOperatingFact old=input(null);old.setFactId(99L);old.setVersion(0);old.setStatus("CONFIRMED");
        old.setCategoryCode("INTERNAL_PROJECT_COST");old.setFactKind("COST");old.setSourceDomain("PROJECT_DAILY");old.setSourceType("DAILY_ITEM");facts.put(99L,old);
        service.reverseProjectDailySpend(99L,"历史费用更正",9L,"owner",false);
        assertEquals(2,facts.size());assertEquals(0,net(1L,"COST").signum());
        assertTrue(facts.values().stream().noneMatch(f->"REVENUE".equals(f.getFactKind())));
    }

    @Test void missingLinkedIncomeBlocksReversalBeforeExpenseIsChanged()
    {
        BusinessOperatingFact old=service.saveProjectDailySpend(input(2L),9L,"owner",false);
        facts.remove(incomeFor(old.getFactId()).getFactId());
        assertThrows(ServiceException.class,()->service.reverseProjectDailySpend(old.getFactId(),"错误关联",9L,"owner",false));
        assertEquals("CONFIRMED",old.getStatus());assertEquals(1,facts.size());
    }

    @ParameterizedTest @ValueSource(strings={"missing","self","nonexistent","currency","closed","draft","date","company","owner"})
    void invalidRecipientsAndUnauthorizedPayersCannotCreateFacts(String scenario)
    {
        BusinessOperatingFact f=input(2L);long actor=9L;
        switch(scenario)
        {
            case "missing":f.setTargetProjectId(null);break;
            case "self":f.setTargetProjectId(1L);break;
            case "nonexistent":f.setTargetProjectId(999L);break;
            case "currency":projects.get(2L).put("currency","VND");break;
            case "closed":projects.get(2L).put("accountingState","CLOSED");break;
            case "draft":projects.get(2L).put("status","DRAFT");break;
            case "date":projects.get(2L).put("actualStartDate",java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(1)));break;
            case "company":projects.get(2L).remove("companyDeptId");break;
            case "owner":actor=77L;break;
        }
        long finalActor=actor;
        assertThrows(ServiceException.class,()->service.saveProjectDailySpend(f,finalActor,"user",false));
        assertTrue(facts.isEmpty());
    }

    @Test void vndTransfersAlsoProduceEqualRevenueWithoutConversion()
    {
        projects.get(1L).put("currency","VND");projects.get(2L).put("currency","VND");
        BusinessOperatingFact f=input(2L);f.setCurrency("VND");
        BusinessOperatingFact saved=service.saveProjectDailySpend(f,9L,"owner",false);
        assertEquals("VND",incomeFor(saved.getFactId()).getCurrency());assertEquals(saved.getAmount(),incomeFor(saved.getFactId()).getAmount());
    }

    @Test void automaticIncomeAndLinkedExpensesCannotBeWrittenThroughGenericAccounting()
    {
        assertThrows(ServiceException.class,()->service.saveFact(input(2L),9L,"owner",true));
        BusinessOperatingFact revenue=input(2L);revenue.setCategoryId(7L);
        assertThrows(ServiceException.class,()->service.saveFact(revenue,9L,"owner",true));
        BusinessOperatingFact expense=service.saveProjectDailySpend(input(2L),9L,"owner",false);
        assertThrows(ServiceException.class,()->service.reverseFact(incomeFor(expense.getFactId()).getFactId(),"冲销",9L,"owner",true));
        assertThrows(ServiceException.class,()->service.reverseFact(expense.getFactId(),"冲销",9L,"owner",true));
        assertEquals(2,facts.size());
    }

    @Test void directoryRequiresSourceOwnershipAndOnlyReturnsMinimalProjectOptions()
    {
        List<Map<String,Object>> options=Arrays.asList(projects.get(1L),projects.get(2L));
        when(mapper.selectInternalTransferProjects()).thenReturn(options);
        assertSame(options,service.internalTransferProjects(1L,9L,false));
        assertThrows(ServiceException.class,()->service.internalTransferProjects(1L,77L,false));
        verify(mapper,times(1)).selectInternalTransferProjects();
    }

    private BusinessOperatingFact incomeFor(Long id){return facts.values().stream().filter(f->("INTERNAL-PROJECT-REVENUE-"+id).equals(f.getIdempotencyKey())).findFirst().orElseThrow(java.util.NoSuchElementException::new);}
    private BigDecimal net(Long id,String kind){return facts.values().stream().filter(f->id.equals(f.getProjectId())&&kind.equals(f.getFactKind())&&Arrays.asList("CONFIRMED","REVERSED").contains(f.getStatus())).map(BusinessOperatingFact::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2);}
    private BusinessOperatingFact input(Long target){BusinessOperatingFact f=new BusinessOperatingFact();f.setProjectId(1L);f.setTargetProjectId(target);f.setCategoryId(6L);f.setBizDate(java.sql.Date.valueOf(java.time.LocalDate.now()));f.setCurrency("CNY");f.setAmount(new BigDecimal("50.00"));f.setDescription("内部协作费用");f.setRequestId("request-1234567890123456");return f;}
    private Map<String,Object> project(Long id){Map<String,Object> p=new HashMap<>();p.put("projectId",id);p.put("projectName","项目"+id);p.put("mainOwnerUserId",9L);p.put("companyDeptId",110L);p.put("currency","CNY");p.put("status","ACTIVE");p.put("accountingState","OPEN");p.put("accountingMode","PROFIT");return p;}
    private Map<String,Object> category(Long id,String code,String kind){Map<String,Object> c=new HashMap<>();c.put("categoryId",id);c.put("categoryCode",code);c.put("categoryName",code);c.put("factKind",kind);return c;}
}
