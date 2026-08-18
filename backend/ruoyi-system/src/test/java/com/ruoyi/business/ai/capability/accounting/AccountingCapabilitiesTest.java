package com.ruoyi.business.ai.capability.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiExecutionContext;
import com.ruoyi.business.ai.capability.read.AccountingDirectoryCapability;
import com.ruoyi.business.ai.capability.read.AccountingFactsCapability;
import com.ruoyi.business.ai.capability.read.AccountingResultDetailCapability;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.service.IBusinessAccountingService;

@ExtendWith(MockitoExtension.class)
class AccountingCapabilitiesTest
{
    @Mock private IBusinessAccountingService service;

    @Test void createsDraftUsingExistingProjectAndCategoryContract()
    {
        BusinessOperatingFact saved = new BusinessOperatingFact();
        saved.setFactId(90L); saved.setStatus("DRAFT"); saved.setAmount(new BigDecimal("88.50"));
        saved.setCurrency("CNY"); saved.setDescription("样品采购");
        when(service.saveFact(any(BusinessOperatingFact.class), eq(1L), eq("boss"), eq(true))).thenReturn(saved);
        Map<String,Object> input = new LinkedHashMap<String,Object>();
        input.put("projectId", 12L); input.put("categoryId", 5L); input.put("categoryName", "业务成本");
        input.put("bizDate", "2026-08-13"); input.put("amount", "88.50");
        input.put("currency", "cny"); input.put("description", "样品采购");

        CreateAccountingDraftCapability capability = new CreateAccountingDraftCapability(service);
        Map<String,Object> result = capability.executeConfirmed(invocation(), input);

        ArgumentCaptor<BusinessOperatingFact> fact = ArgumentCaptor.forClass(BusinessOperatingFact.class);
        verify(service).saveFact(fact.capture(), eq(1L), eq("boss"), eq(true));
        assertEquals(12L, fact.getValue().getProjectId());
        assertEquals(5L, fact.getValue().getCategoryId());
        assertNull(fact.getValue().getCompanyDeptId());
        assertEquals("CNY", fact.getValue().getCurrency());
        assertEquals(90L, result.get("factId"));
    }

    @Test void directoryReturnsOnlyLookupCollections()
    {
        Map<String,Object> dashboard = new LinkedHashMap<String,Object>();
        dashboard.put("companies", Collections.singletonList(Collections.singletonMap("companyDeptId", 100L)));
        dashboard.put("projects", Collections.singletonList(Collections.singletonMap("projectId", 12L)));
        dashboard.put("categories", Collections.singletonList(Collections.singletonMap("categoryId", 5L)));
        dashboard.put("facts", Collections.singletonList(Collections.singletonMap("amount", 9999)));
        when(service.dashboard(any(), eq(1L), eq(true))).thenReturn(dashboard);

        Map<String,Object> result = new AccountingDirectoryCapability(service).execute(invocation(), Collections.emptyMap());

        assertEquals(dashboard.get("projects"), result.get("projects"));
        assertEquals(dashboard.get("categories"), result.get("categories"));
        assertNull(result.get("facts"));
    }

    @Test void confirmationSummaryUsesTheExactDraftFactAndExecutionConfirmsIt()
    {
        Map<String,Object> row = factRow("DRAFT");
        when(service.facts(any(), eq(1L), eq(true))).thenReturn(Collections.singletonList(row));
        BusinessOperatingFact saved = new BusinessOperatingFact(); saved.setFactId(90L); saved.setStatus("CONFIRMED");
        saved.setProjectId(12L); saved.setAmount(new BigDecimal("88.50")); saved.setCurrency("CNY");
        when(service.confirmFact(90L, 1L, "boss", true)).thenReturn(saved);
        Map<String,Object> input = Collections.<String,Object>singletonMap("factId", 90L);
        ConfirmAccountingFactCapability capability = new ConfirmAccountingFactCapability(service);

        assertTrue(capability.confirmationSummary(invocation(), input).contains("样品采购"));
        Map<String,Object> result = capability.executeConfirmed(invocation(), input);

        verify(service).confirmFact(90L, 1L, "boss", true);
        assertEquals("CONFIRMED", result.get("status"));
    }

    @Test void reversalRequiresReasonAndRecalculatesOnlyAfterConfirmation()
    {
        Map<String,Object> row = factRow("CONFIRMED");
        when(service.facts(any(), eq(1L), eq(true))).thenReturn(Collections.singletonList(row));
        BusinessOperatingFact reversed = new BusinessOperatingFact(); reversed.setFactId(91L);
        reversed.setReversalFactId(90L); reversed.setStatus("REVERSED");
        when(service.reverseFact(90L, "录入错误", 1L, "boss", true)).thenReturn(reversed);
        Map<String,Object> input = new LinkedHashMap<String,Object>(); input.put("factId", 90L); input.put("reason", "录入错误");

        ReverseAccountingFactCapability capability = new ReverseAccountingFactCapability(service);
        assertTrue(capability.confirmationSummary(invocation(), input).contains("录入错误"));
        assertEquals("REVERSED", capability.executeConfirmed(invocation(), input).get("status"));

        Map<String,Object> recalculated = Collections.<String,Object>singletonMap("resultId", 300L);
        when(service.recalculate(eq(12L), any(), eq(1L), eq("boss"), eq(true))).thenReturn(recalculated);
        Map<String,Object> recalcInput = new LinkedHashMap<String,Object>(); recalcInput.put("projectId", 12L);
        recalcInput.put("bizDate", "2026-08-14");
        assertEquals(300L, new RecalculateProjectDayCapability(service).executeConfirmed(invocation(), recalcInput).get("resultId"));
    }

    @Test void readCapabilitiesKeepStableIdentifiersInTheQueryContract()
    {
        when(service.facts(any(), eq(1L), eq(true))).thenReturn(Collections.singletonList(factRow("DRAFT")));
        Map<String,Object> input = new LinkedHashMap<String,Object>(); input.put("factId", 90L); input.put("status", "DRAFT");
        new AccountingFactsCapability(service).execute(invocation(), input);
        ArgumentCaptor<Map<String,Object>> query = mapCaptor();
        verify(service).facts(query.capture(), eq(1L), eq(true));
        assertEquals(90L, query.getValue().get("factId"));

        when(service.resultDetail(300L, 1L, true)).thenReturn(Collections.<String,Object>singletonMap("resultId", 300L));
        Map<String,Object> detail = new AccountingResultDetailCapability(service).execute(invocation(),
            Collections.<String,Object>singletonMap("resultId", 300L));
        assertTrue(detail.containsKey("result"));
    }

    private Map<String,Object> factRow(String status)
    {
        Map<String,Object> row = new LinkedHashMap<String,Object>(); row.put("factId", 90L); row.put("projectId", 12L);
        row.put("projectName", "新谷酵素视频剪辑"); row.put("bizDate", "2026-08-14");
        row.put("categoryName", "样品采购"); row.put("amount", new BigDecimal("88.50"));
        row.put("currency", "CNY"); row.put("status", status); return row;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Map<String,Object>> mapCaptor() { return ArgumentCaptor.forClass((Class) Map.class); }

    private AiCapabilityInvocation invocation()
    { return new AiCapabilityInvocation(AiExecutionContext.legacy(1L, "boss", true), 2L, 3L, 4L); }
}
