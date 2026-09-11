package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.mapper.BusinessProjectManagementFeeMapper;
import com.ruoyi.business.mapper.BusinessProjectMapper;

class BusinessProjectManagementFeeServiceTest
{
    BusinessProjectManagementFeeService service=new BusinessProjectManagementFeeService();
    BusinessProjectManagementFeeMapper mapper=mock(BusinessProjectManagementFeeMapper.class);
    BusinessProjectMapper projects=mock(BusinessProjectMapper.class);
    BusinessAccountingMapper accounting=mock(BusinessAccountingMapper.class);
    BusinessProject project;Map<String,Object> configured;

    @BeforeEach void setup()
    {
        ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"projects",projects);
        ReflectionTestUtils.setField(service,"accounting",accounting);
        project=new BusinessProject();project.setProjectId(1L);project.setProjectName("项目A");project.setCompanyDeptId(100L);
        project.setMainOwnerUserId(10L);project.setMainOwnerName("负责人");project.setSponsorOwnerUserId(20L);
        project.setBaseCurrency("CNY");project.setStatus("ACTIVE");project.setAccountingState("OPEN");project.setDelFlag("0");
        when(projects.selectProjectById(1L)).thenReturn(project);when(projects.selectProjectByIdForUpdate(1L)).thenReturn(project);
        when(projects.selectBossOwnerActiveProjects(20L,false,10L)).thenReturn(activeProjects(3));
        when(mapper.selectLifetimeBasis(1L)).thenReturn(map("basisRevenue",new BigDecimal("1000"),"basisBusinessCost",new BigDecimal("100"),
            "basisPersonnelCost",new BigDecimal("200"),"basisBonusCost",new BigDecimal("50"),"basisAdjustment",new BigDecimal("10")));
        configured=map("feeId",2L,"projectId",1L,"recipientUserId",10L,"recipientUserName","负责人","calculationMode","PROFIT_RATE",
            "profitRate",new BigDecimal("10"),"minimumProfit",new BigDecimal("700"),"capAmount",new BigDecimal("60"),"currency","CNY","status","CONFIGURED","version",0);
        when(mapper.selectFee(1L)).thenReturn(configured);when(mapper.selectFeeForUpdate(1L)).thenReturn(configured);
        when(mapper.selectPayments(2L)).thenReturn(Collections.emptyList());when(mapper.insertEvent(anyMap())).thenReturn(1);
        when(mapper.selectPaymentByRequest(anyLong(),anyString())).thenReturn(null);
    }

    @Test void estimateUsesFinalProfitBasisAndIgnoresRemovedLegacyLimits()
    {
        Map<String,Object> result=service.workspace(1L,20L,false,true);
        assertEquals(new BigDecimal("660"),result.get("preFeeProfit"));
        assertEquals(new BigDecimal("66.00"),result.get("estimatedAmount"));
        assertEquals("ESTIMATED",result.get("processStatus"));
    }

    @Test void ownerWithFewerThanThreeProjectsDoesNotNeedManagementFeeConfiguration()
    {
        when(mapper.selectFee(1L)).thenReturn(null);
        when(projects.selectBossOwnerActiveProjects(20L,false,10L)).thenReturn(activeProjects(2));

        Map<String,Object> result=service.workspace(1L,20L,false,true);

        assertEquals(2,result.get("projectCount"));
        assertEquals(false,result.get("eligible"));
        assertEquals(false,result.get("configurationRequired"));
        assertEquals(false,result.get("canConfigure"));
        assertEquals("INELIGIBLE",result.get("processStatus"));
    }

    @Test void configurationCapturesThreeProjectEligibilitySnapshot()
    {
        Map<String,Object> input=map("version",0,"calculationMode","FIXED","fixedAmount",new BigDecimal("500"),
            "configReason","负责三个项目");
        when(mapper.saveConfiguration(anyMap())).thenReturn(1);

        service.configure(1L,input,20L,"老板");

        verify(mapper).saveConfiguration(argThat(row->!row.containsKey("minimumProfit")&&!row.containsKey("capAmount")
            &&Integer.valueOf(3).equals(row.get("eligibilityProjectCount"))
            &&"1,2,3".equals(row.get("eligibilityProjectIds"))
            &&"项目1、项目2、项目3".equals(row.get("eligibilityProjectNames"))));
    }

    @Test void configurationIsRejectedUntilOwnerHasThreeProjects()
    {
        when(mapper.selectFeeForUpdate(1L)).thenReturn(null);
        when(projects.selectBossOwnerActiveProjects(20L,false,10L)).thenReturn(activeProjects(2));
        Map<String,Object> input=map("calculationMode","FIXED","fixedAmount",new BigDecimal("500"),
            "configReason","尝试设置");

        RuntimeException error=assertThrows(RuntimeException.class,()->service.configure(1L,input,20L,"老板"));

        assertTrue(error.getMessage().contains("达到3个后"));
        verify(mapper,never()).saveConfiguration(anyMap());
    }

    @Test void ineligibleProjectCanCloseWithoutCreatingFeeCost()
    {
        when(mapper.selectFeeForUpdate(1L)).thenReturn(null);
        when(projects.selectBossOwnerActiveProjects(20L,false,10L)).thenReturn(activeProjects(2));

        Map<String,Object> result=service.settle(1L,new Date(),20L,"老板");

        assertEquals("INELIGIBLE",result.get("status"));
        assertEquals(new BigDecimal("0.00"),result.get("settledAmount"));
        verify(accounting,never()).insertFact(any());
        verify(mapper,never()).settle(anyMap());
    }

    @Test void settlementCreatesOneConfirmedCostFact()
    {
        when(accounting.selectCategoryByCode("PROJECT_MANAGEMENT_FEE")).thenReturn(map("categoryId",9L,"categoryName","项目管理费"));
        when(accounting.insertFact(any())).thenAnswer(call->{((BusinessOperatingFact)call.getArgument(0)).setFactId(88L);return 1;});
        when(mapper.settle(anyMap())).thenReturn(1);
        service.settle(1L,new Date(),20L,"老板");
        verify(accounting).insertFact(argThat(fact->"PROJECT_MANAGEMENT_FEE".equals(fact.getCategoryCode())
            &&"CONFIRMED".equals(fact.getStatus())&&new BigDecimal("66.00").compareTo(fact.getAmount())==0));
        verify(mapper).settle(argThat(row->Long.valueOf(88L).equals(row.get("accountingFactId"))));
    }

    @Test void paymentOnlyWritesEvidenceAndNeverPostsCostAgain() throws Exception
    {
        project.setStatus("CLOSED");project.setAccountingState("CLOSED");
        Map<String,Object> settled=new HashMap<String,Object>(configured);settled.put("status","SETTLED");settled.put("settledAmount",new BigDecimal("60"));settled.put("settledTime",new Date());
        when(mapper.selectFeeForUpdate(1L)).thenReturn(settled);when(mapper.insertPayment(anyMap())).thenReturn(1);
        Map<String,Object> input=map("amount",new BigDecimal("20"),"paidDate",new SimpleDateFormat("yyyy-MM-dd").format(new Date()),
            "method","BANK","referenceNo","bank-1","voucher","/profile/upload/proof.png","reason","首笔付款","requestKey","pay-1");
        service.pay(1L,input,20L,"老板");
        verify(mapper).insertPayment(argThat(row->new BigDecimal("20").compareTo((BigDecimal)row.get("amount"))==0));
        verify(accounting,never()).insertFact(any());
    }

    private static Map<String,Object> map(Object... values)
    {Map<String,Object> result=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}
    private static List<Map<String,Object>> activeProjects(int count)
    {List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();for(int i=1;i<=count;i++)rows.add(map("projectId",Long.valueOf(i),"projectName","项目"+i,"status","ACTIVE"));return rows;}
}
