package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessPublicExpenseMapper;
import com.ruoyi.business.service.IBusinessAccountingService;

class BusinessPublicExpenseServiceTest
{
    private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

    BusinessPublicExpenseService service=new BusinessPublicExpenseService();
    BusinessPublicExpenseMapper mapper=mock(BusinessPublicExpenseMapper.class);
    BusinessProjectMapper projects=mock(BusinessProjectMapper.class);
    BusinessAccountingMapper accounting=mock(BusinessAccountingMapper.class);
    IBusinessAccountingService calculation=mock(IBusinessAccountingService.class);
    Map<String,Object> bill,owner,allocation;BusinessProject project;
    @BeforeEach void setup()
    {
        ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"projects",projects);
        ReflectionTestUtils.setField(service,"accounting",accounting);ReflectionTestUtils.setField(service,"accountingService",calculation);
        bill=map("billId",1L,"companyDeptId",100L,"companyName","公司","month","2025-02","currency","CNY","totalAmount",bd("100.00"),"status","PUBLISHED","version",0);
        owner=map("allocationId",2L,"billId",1L,"ownerUserId",20L,"ownerName","负责人","percentage",bd("100"),"amount",bd("100.00"),"status","SUBMITTED","version",0);
        allocation=map("projectAllocationId",3L,"allocationId",2L,"projectId",4L,"projectName","项目","percentage",bd("100"),"amount",bd("100.00"));
        project=new BusinessProject();project.setProjectId(4L);project.setProjectName("项目");project.setCompanyDeptId(100L);project.setMainOwnerUserId(20L);project.setBaseCurrency("CNY");project.setAccountingState("OPEN");project.setDeliveryPolicyVersion("SEPARATED_V1");project.setStatus("ACTIVE");project.setDelFlag("0");
        when(mapper.selectCompanyForUpdate(100L)).thenReturn(map("companyDeptId",100L,"leaderUserId",10L));
        when(mapper.selectBill(1L)).thenReturn(bill);when(mapper.selectBillForUpdate(1L)).thenReturn(bill);when(mapper.selectOwner(2L)).thenReturn(owner);
        when(mapper.selectOwnerAllocations(1L)).thenReturn(Arrays.asList(owner));when(mapper.selectProjectAllocations(2L)).thenReturn(Arrays.asList(allocation));
        when(mapper.selectEntries(1L)).thenReturn(Arrays.asList(map("entryId",7L,"amount",bd("100.00"),"estimated",false)));
        when(mapper.updateBill(anyMap())).thenReturn(1);when(mapper.updateOwner(anyMap())).thenReturn(1);
        when(projects.selectProjectByIdForUpdate(4L)).thenReturn(project);when(projects.selectProjectById(4L)).thenReturn(project);
        when(accounting.selectCategoryByCode("COMPANY_PUBLIC_COST")).thenReturn(map("categoryId",99L));
        doAnswer(call->{((BusinessOperatingFact)call.getArgument(0)).setFactId(55L);return 1;}).when(accounting).insertFact(any());

        companyAccess=com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture();
        lenient().when(companyAccess.allowed(10L,100L,"BUSINESS")).thenReturn(true);
        lenient().when(companyAccess.allowed(10L,101L,"BUSINESS")).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"companyAccess",companyAccess);
}

    @Test void parentOwnerCanReadChildPublicExpensesWithoutBecomingCompanyLeader()
    {
        project.setParentId(2L);BusinessProject parent=new BusinessProject();parent.setProjectId(2L);parent.setMainOwnerUserId(99L);
        when(projects.selectProjectById(2L)).thenReturn(parent);
        assertDoesNotThrow(()->service.projectWorkspace(4L,"2025-02",99L,false));
        assertThrows(RuntimeException.class,()->service.projectWorkspace(4L,"2025-02",88L,false));
    }

    @Test void personnelSaveRejectsClientAmountsAndUsesAutomaticSources() {
        BusinessPublicPersonnelService personnel=mock(BusinessPublicPersonnelService.class);
        ReflectionTestUtils.setField(service,"personnel",personnel);
        bill.put("status","DRAFT");when(mapper.selectMonth(100L,"2025-02","CNY")).thenReturn(bill);
        Map<String,Object> input=map("companyDeptId",100L,"month","2025-02","currency","CNY","version",0,"rows",Arrays.asList(map("totalAmount",1)));
        assertThrows(RuntimeException.class,()->service.savePersonnel(input,10L,"老板"));verifyNoInteractions(personnel);verify(mapper,never()).updateBill(anyMap());
        input.remove("rows");when(personnel.allocationSnapshot(100L,"2025-02","CNY",null)).thenReturn(map("publicAmount",bd("60.00"),"sourceMode","DEPARTMENT_NET_V1","rows",Collections.emptyList()));
        when(mapper.selectOwners(100L)).thenReturn(Arrays.asList(map("userId",20L,"userName","负责人","deptId",201L)));
        service.savePersonnel(input,10L,"老板");assertEquals(bd("160.00"),bill.get("totalAmount"));assertEquals(bd("60.00"),bill.get("personnelAmount"));
        verify(personnel).allocationSnapshot(100L,"2025-02","CNY",null);
    }
    @Test void itProjectsCannotReceiveEitherCostPoolEvenWithAValidOwner() {
        when(mapper.countItSourceProject(4L)).thenReturn(1);
        for(String pool:Arrays.asList("EXPENSE","PERSONNEL","COMBINED")) {
            owner.put("costPool",pool);
            assertThrows(RuntimeException.class,()->service.saveProjects(2L,map("version",0,"allocations",Arrays.asList(allocation)),20L,"负责人"));
            assertThrows(RuntimeException.class,()->service.submit(2L,map("version",0),20L,"负责人"));
        }
        verify(mapper,never()).deleteOwnerProjects(anyLong());verify(mapper,never()).updateOwner(anyMap());
    }
    @Test void publicSupportSourceCannotBeChangedByAnUnbalancedGenericAdjustment() {
        bill.put("status","SETTLED");bill.put("personnelSnapshot","{\"sourceMode\":\"DEPARTMENT_NET_V1\"}");
        assertThrows(RuntimeException.class,()->service.adjust(1L,map("projectId",4L,"amount",1,"reason","核对","requestKey","test","costPool","PERSONNEL"),10L,"老板"));
        owner.put("costPool","PERSONNEL");
        assertThrows(RuntimeException.class,()->service.adjust(1L,map("projectId",4L,"amount",1,"reason","核对","requestKey","test","version",0),10L,"老板"));
        verify(mapper,never()).insertAdjustment(anyMap());verify(accounting,never()).insertFact(any());
    }
    @Test void separatePoolsAllowSameOwnerWithoutMixingPercentages() {
        bill.put("status","DRAFT");bill.put("personnelAmount",bd("60.00"));owner.put("amount",bd("40.00"));
        when(mapper.selectOwners(100L)).thenReturn(Arrays.asList(map("userId",20L,"userName","负责人","deptId",201L)));
        service.saveOwners(1L,map("version",0,"costPool","PERSONNEL","allocations",Arrays.asList(map("ownerUserId",20L,"deptId",201L,"percentage",100))),10L,"老板");
        verify(mapper).insertOwner(argThat(r->"PERSONNEL".equals(r.get("costPool"))&&bd("60.00").equals(r.get("amount"))));
        verify(mapper).insertOwner(argThat(r->"EXPENSE".equals(r.get("costPool"))&&bd("40.00").equals(r.get("amount"))));
    }
    @Test void combinedOwnersReplaceBothOldPoolsAndAllocateTheFullTotalExactlyOnce() {
        bill.put("status","DRAFT");bill.put("totalAmount",bd("72209.83"));bill.put("personnelAmount",bd("26143.17"));
        when(mapper.selectOwners(100L)).thenReturn(Arrays.asList(map("userId",20L,"userName","A","deptId",201L),map("userId",21L,"userName","B","deptId",202L)));
        when(mapper.selectOwnerAllocations(1L)).thenReturn(Arrays.asList(owner,map("ownerUserId",20L,"costPool","PERSONNEL","percentage",100,"amount",bd("26143.17"))));
        service.saveOwners(1L,map("version",0,"costPool","COMBINED","allocations",Arrays.asList(map("ownerUserId",20L,"percentage",60),map("ownerUserId",21L,"percentage",40))),10L,"老板");
        verify(mapper,times(2)).insertOwner(argThat(r->"COMBINED".equals(r.get("costPool"))));
        verify(mapper).insertOwner(argThat(r->Long.valueOf(20L).equals(r.get("ownerUserId"))&&bd("43325.90").equals(r.get("amount"))));
        verify(mapper).insertOwner(argThat(r->Long.valueOf(21L).equals(r.get("ownerUserId"))&&bd("28883.93").equals(r.get("amount"))));
    }
    @Test void combinedPublishNeedsOneCompleteSetAndRejectsMixedOrDuplicateOwners() {
        bill.put("status","DRAFT");bill.put("personnelAmount",bd("60.00"));owner.put("costPool","COMBINED");
        when(mapper.selectOwners(100L)).thenReturn(Arrays.asList(map("userId",20L,"userName","负责人","deptId",201L)));
        owner.put("percentage",bd("99"));
        assertThrows(RuntimeException.class,()->service.publish(1L,map("version",0),10L,"老板"));
        assertThrows(RuntimeException.class,()->service.saveOwners(1L,map("version",0,"costPool","COMBINED","allocations",Arrays.asList(map("ownerUserId",20L,"percentage",50),map("ownerUserId",20L,"percentage",50))),10L,"老板"));
        assertThrows(RuntimeException.class,()->service.saveOwners(1L,map("version",0,"costPool","PERSONNEL","allocations",Arrays.asList(map("ownerUserId",20L,"percentage",100))),10L,"老板"));
        verify(mapper,never()).deleteOwners(anyLong());
        owner.put("percentage",bd("100"));
        assertDoesNotThrow(()->service.publish(1L,map("version",0),10L,"老板"));
        assertEquals("PUBLISHED",bill.get("status"));
    }
    @Test void combinedOwnerTaskHasOneTotalAndAllSourceEntriesWithoutPayrollDetails() {
        bill.put("totalAmount",bd("160.00"));bill.put("personnelAmount",bd("60.00"));
        bill.put("personnelSnapshot","{\"sourceMode\":\"DEPARTMENT_NET_V1\",\"personnelDetails\":[{\"userName\":\"private\"}]}");
        owner.put("costPool","COMBINED");owner.put("amount",bd("160.00"));
        when(mapper.selectOwnerBills(20L,"2025-02")).thenReturn(Arrays.asList(owner));
        Map<String,Object> view=(Map<String,Object>)((List<?>)service.ownerWorkspace("2025-02",20L).get("bills")).get(0);
        assertEquals(bd("160.00"),view.get("totalAmount"));assertEquals("公共费用合计",view.get("costPoolLabel"));
        assertFalse(view.containsKey("personnel"));assertFalse(view.containsKey("personnelSnapshot"));
        List<Map<String,Object>> entries=(List<Map<String,Object>>)view.get("entries");assertEquals(2,entries.size());
        assertEquals(bd("160.00"),entries.stream().map(r->(BigDecimal)r.get("ownerAmount")).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void oldOwnerPoolsMergeByAmountsAndIncompleteCoverageStaysIncomplete() {
        bill.put("personnelAmount",bd("60.00"));
        List<Map<String,Object>> old=Arrays.asList(map("ownerUserId",20L,"costPool","EXPENSE","amount",bd("40.00")),map("ownerUserId",20L,"costPool","PERSONNEL","amount",bd("30.00")),map("ownerUserId",21L,"costPool","PERSONNEL","amount",bd("30.00")));
        List<Map<String,Object>> merged=BusinessPublicExpenseService.combinedOwners(bill,old);
        assertEquals(2,merged.size());assertEquals(bd("70.00"),merged.get(0).get("percentage"));assertEquals(bd("30.00"),merged.get(1).get("percentage"));
        merged=BusinessPublicExpenseService.combinedOwners(bill,Collections.singletonList(old.get(0)));
        assertEquals(bd("100.00"),merged.get(0).get("percentage"));
        assertEquals(bd("100.00"),merged.get(0).get("amount"));
        merged=BusinessPublicExpenseService.combinedOwners(bill,Collections.singletonList(map("ownerUserId",20L,"costPool","EXPENSE","amount",bd("20.00"))));
        assertEquals(bd("50.00"),merged.get(0).get("percentage"));
        assertEquals(bd("50.00"),merged.get(0).get("amount"));
    }
    @Test void copyingPreviousSeparatePoolsProducesOneCombinedSharePerOwner() {
        bill.put("status","DRAFT");bill.put("totalAmount",bd("200.00"));
        Map<String,Object> prior=map("billId",9L,"totalAmount",bd("100.00"));
        when(mapper.selectMonth(100L,"2025-01","CNY")).thenReturn(prior);
        when(mapper.selectOwnerAllocations(9L)).thenReturn(Arrays.asList(map("ownerUserId",20L,"costPool","EXPENSE","amount",bd("40.00")),map("ownerUserId",20L,"costPool","PERSONNEL","amount",bd("60.00"))));
        when(mapper.selectOwners(100L)).thenReturn(Arrays.asList(map("userId",20L,"userName","负责人","deptId",201L)));
        service.copyOwners(1L,map("version",0,"costPool","COMBINED"),10L,"老板");
        verify(mapper,times(1)).insertOwner(argThat(r->"COMBINED".equals(r.get("costPool"))&&bd("100.00").equals(r.get("percentage"))&&bd("200.00").equals(r.get("amount"))));
    }
    @Test void settledCombinedAdjustmentCannotReduceTheSealedSupportSources() {
        when(mapper.selectAdjustmentByRequest(eq(1L),anyString())).thenReturn(null);
        bill.put("status","SETTLED");bill.put("personnelAmount",bd("60.00"));bill.put("personnelSnapshot","{\"sourceMode\":\"DEPARTMENT_NET_V1\"}");
        owner.put("costPool","COMBINED");
        assertEquals("调整后项目公共费用不能为负数",assertThrows(RuntimeException.class,()->service.adjust(1L,map("version",0,"projectId",4L,"amount",-41,"reason","核对","requestKey","blocked"),10L,"老板")).getMessage());
        assertDoesNotThrow(()->service.adjust(1L,map("version",0,"projectId",4L,"amount",-40,"reason","核对","requestKey","ordinary"),10L,"老板"));
        verify(mapper,times(1)).insertAdjustment(anyMap());
    }
    @Test void zeroCombinedBillCanReceiveAnOrdinaryAdjustmentWithoutDividingByZero() {
        when(mapper.selectAdjustmentByRequest(eq(1L),anyString())).thenReturn(null);
        bill.put("status","SETTLED");bill.put("totalAmount",bd("0.00"));bill.put("personnelAmount",bd("0.00"));bill.put("personnelSnapshot","{\"sourceMode\":\"DEPARTMENT_NET_V1\"}");
        owner.put("costPool","COMBINED");owner.put("amount",bd("0.00"));allocation.put("amount",bd("0.00"));
        assertDoesNotThrow(()->service.adjust(1L,map("version",0,"projectId",4L,"amount",1,"reason","补录日常费用","requestKey","zero"),10L,"老板"));
        verify(mapper).insertAdjustment(anyMap());
    }
    @Test void cannotPublishUntilBothPoolsAreCompletelyAllocated() {
        bill.put("status","DRAFT");bill.put("personnelAmount",bd("60.00"));owner.put("amount",bd("40.00"));
        assertThrows(RuntimeException.class,()->service.publish(1L,map("version",0),10L,"老板"));verify(mapper,never()).updateBill(anyMap());
    }
    @Test void ownerReceivesPoolTotalWithoutPayrollNamesOrSnapshots() {
        bill.put("personnelAmount",bd("60.00"));bill.put("personnelSnapshot","sensitive payroll");owner.put("costPool","PERSONNEL");owner.put("amount",bd("60.00"));
        when(mapper.selectOwnerBills(20L,"2025-02")).thenReturn(Arrays.asList(owner));
        Map<String,Object> view=(Map<String,Object>)((List<?>)service.ownerWorkspace("2025-02",20L).get("bills")).get(0);
        assertEquals(bd("60.00"),view.get("totalAmount"));assertFalse(view.containsKey("personnelSnapshot"));assertFalse(view.containsKey("personnel"));
        assertEquals("公共人员成本",((Map<?,?>)((List<?>)view.get("entries")).get(0)).get("name"));
    }
    @Test void bossCanSwitchCompaniesAndSaveOnlyTheSelectedCompanyOwners()
    {
        when(companyAccess.allowed(11L,100L,"BUSINESS")).thenReturn(true);
        when(mapper.selectCompanies(11L)).thenReturn(Arrays.asList(map("companyDeptId",100L),map("companyDeptId",101L)));
        when(mapper.selectOwners(100L)).thenReturn(Arrays.asList(map("userId",20L,"userName","A负责人","deptId",201L)));
        when(mapper.selectOwners(101L)).thenReturn(Arrays.asList(map("userId",21L,"userName","B负责人","deptId",202L)));
        assertEquals(2,((List<?>)service.workspace(100L,"2025-02","CNY",11L).get("companies")).size());
        assertEquals(21L,((List<Map<String,Object>>)service.workspace(101L,"2025-02","CNY",11L).get("owners")).get(0).get("userId"));
        bill.put("status","DRAFT");
        assertDoesNotThrow(()->service.saveOwners(1L,map("version",0,"allocations",Arrays.asList(map("ownerUserId",20L,"percentage",100))),11L,"老板"));
        assertThrows(RuntimeException.class,()->service.saveOwners(1L,map("version",1,"allocations",Arrays.asList(map("ownerUserId",21L,"percentage",100))),11L,"老板"));
        verify(mapper,times(1)).insertOwner(anyMap());
    }
    @Test void departmentsWithoutOwnersRemainSelectableFromCompanyOrganization()
    {
        when(mapper.selectCompanies(10L)).thenReturn(Arrays.asList(map("companyDeptId",100L)));
        List<Map<String,Object>> departments=Arrays.asList(map("deptId",201L,"deptName","运营部"),map("deptId",202L,"deptName","IT部"));
        when(mapper.selectDepartments(100L)).thenReturn(departments);
        when(mapper.selectOwners(100L)).thenReturn(Collections.emptyList());
        Map<String,Object> result=service.workspace(100L,"2025-02","CNY",10L);
        assertEquals(departments,result.get("departments"));
        assertTrue(((List<?>)result.get("owners")).isEmpty());
    }
    @Test void ordinaryOwnerCannotManageAnotherCompanyCosts()
    {
        assertThrows(RuntimeException.class,()->service.workspace(100L,"2025-02","CNY",20L));
        assertThrows(RuntimeException.class,()->service.saveOwners(1L,map("version",0,"allocations",Collections.emptyList()),20L,"负责人"));
        verify(mapper,never()).deleteOwners(anyLong());
    }

    @Test void annualScheduleConservesEveryCentAcrossYearBoundary()
    {
        Map<String,Object> policy=map("amount",bd("100.01"),"periodType","ANNUAL","startMonth","2025-09");BigDecimal total=BigDecimal.ZERO;
        for(int i=0;i<12;i++)total=total.add(BusinessPublicExpenseService.monthlyAmount(policy,YearMonth.of(2025,9).plusMonths(i)));
        assertEquals(bd("100.01"),total);assertEquals(bd("8.38"),BusinessPublicExpenseService.monthlyAmount(policy,YearMonth.of(2026,8)));
    }
    @Test void largestRemainderDoesNotMakeTinyLastShareNegative()
    {
        List<BigDecimal> values=BusinessPublicExpenseService.allocate(bd("0.02"),Arrays.asList(bd("33.3333"),bd("33.3333"),bd("33.3333"),bd("0.0001")));
        assertEquals(Arrays.asList(bd("0.01"),bd("0.01"),bd("0.00"),bd("0.00")),values);
    }
    @Test void incompleteDraftNeverAllocatesMoreThanTheCostPool()
    {
        List<BigDecimal> values=BusinessPublicExpenseService.allocate(bd("1.00"),Collections.nCopies(166,bd("0.6")));
        assertEquals(bd("1.00"),values.stream().reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void overOneHundredPercentIsRejected(){assertThrows(RuntimeException.class,()->BusinessPublicExpenseService.allocate(bd("1.00"),Arrays.asList(bd("60"),bd("41"))));}
    @Test void anotherCompanyBossCannotPublish(){bill.put("status","DRAFT");assertThrows(RuntimeException.class,()->service.publish(1L,map("version",0),99L,"别人"));verify(mapper,never()).updateBill(anyMap());}
    @Test void staleVersionCannotPublish(){bill.put("status","DRAFT");assertThrows(RuntimeException.class,()->service.publish(1L,map("version",2),10L,"老板"));verify(mapper,never()).updateBill(anyMap());}
    @Test void incompleteOwnerPercentCannotPublish(){bill.put("status","DRAFT");owner.put("percentage",bd("90"));assertThrows(RuntimeException.class,()->service.publish(1L,map("version",0),10L,"老板"));}
    @Test void ownerCannotAllocateSomeoneElsesShare(){assertThrows(RuntimeException.class,()->service.saveProjects(2L,map("version",0,"allocations",Arrays.asList(map("projectId",4L,"percentage",100))),21L,"别人"));verify(mapper,never()).deleteOwnerProjects(anyLong());}
    @Test void crossCompanyAndCrossCurrencyProjectsAreRejected()
    {
        project.setCompanyDeptId(101L);assertThrows(RuntimeException.class,()->service.saveProjects(2L,map("version",0,"allocations",Arrays.asList(map("projectId",4L,"percentage",100))),20L,"负责人"));
        project.setCompanyDeptId(100L);project.setBaseCurrency("VND");assertThrows(RuntimeException.class,()->service.saveProjects(2L,map("version",0,"allocations",Arrays.asList(map("projectId",4L,"percentage",100))),20L,"负责人"));verify(mapper,never()).deleteOwnerProjects(anyLong());
    }
    @Test void nonPrimaryProjectOwnerCannotAllocate(){project.setMainOwnerUserId(21L);assertThrows(RuntimeException.class,()->service.saveProjects(2L,map("version",0,"allocations",Arrays.asList(map("projectId",4L,"percentage",100))),20L,"负责人"));}
    @Test void currentMonthCannotBeSettled(){bill.put("month",YearMonth.now().toString());assertThrows(RuntimeException.class,()->service.settle(1L,map("version",0),10L,"老板"));verify(accounting,never()).insertFact(any());}
    @Test void estimatedOrUnsubmittedCostsCannotBeSettled()
    {
        when(mapper.selectEntries(1L)).thenReturn(Arrays.asList(map("amount",bd("100.00"),"estimated",1)));assertThrows(RuntimeException.class,()->service.settle(1L,map("version",0),10L,"老板"));
        when(mapper.selectEntries(1L)).thenReturn(Collections.emptyList());owner.put("status","DRAFT");assertThrows(RuntimeException.class,()->service.settle(1L,map("version",0),10L,"老板"));verify(accounting,never()).insertFact(any());
    }
    @Test void monthlySettlementWritesOnlyMonthlyAmountAndPreservesSubmittedOwnerAfterHandover()
    {
        project.setMainOwnerUserId(21L);project.setActualEndDate(java.sql.Date.valueOf("2025-02-18"));
        service.settle(1L,map("version",0),10L,"老板");
        verify(accounting).insertFact(argThat(f->bd("100.00").equals(f.getAmount())&&"COMPANY_PUBLIC_COST".equals(f.getCategoryCode())&&"COMPANY".equals(f.getSourceDomain())&&"PUBLIC_EXPENSE".equals(f.getSourceType())&&"CONFIRMED".equals(f.getStatus())&&java.sql.Date.valueOf("2025-02-18").equals(f.getBizDate())));
        verify(calculation).recalculatePersonnelCost(eq(4L),eq(java.sql.Date.valueOf("2025-02-18")),eq("老板"));assertEquals("SETTLED",bill.get("status"));
        assertThrows(RuntimeException.class,()->service.settle(1L,map("version",1),10L,"老板"));verify(accounting,times(1)).insertFact(any());
    }
    @Test void recallWorksAfterOldOwnerAccountIsDisabled()
    {
        when(mapper.selectOwners(100L)).thenReturn(Collections.emptyList());service.recall(1L,map("version",0,"reason","负责人离职重新分配"),10L,"老板");
        assertEquals("DRAFT",bill.get("status"));verify(mapper).deleteBillProjects(1L);verify(mapper).updateOwner(argThat(row->"DRAFT".equals(row.get("status"))));verify(mapper,never()).deleteOwners(anyLong());
    }
    @Test void adjustmentCannotDriveProjectCostsNegative()
    {
        bill.put("status","SETTLED");assertThrows(RuntimeException.class,()->service.adjust(1L,map("version",0,"projectId",4L,"amount","-100.01","reason","错误冲减","requestKey","abc"),10L,"老板"));verify(mapper,never()).insertAdjustment(anyMap());
    }
    @Test void adjustmentRequestIsIdempotentAndClosedProjectIsProtected()
    {
        bill.put("status","SETTLED");Map<String,Object> input=map("version",0,"projectId",4L,"amount","5.00","reason","补差","requestKey","abc");
        when(mapper.selectAdjustmentByRequest(1L,"abc")).thenReturn(map("projectId",4L,"amount",bd("5.00"),"reason","补差"));service.adjust(1L,input,10L,"老板");verify(mapper,never()).insertAdjustment(anyMap());
        when(mapper.selectAdjustmentByRequest(1L,"abc")).thenReturn(null);project.setAccountingState("CLOSED");assertThrows(RuntimeException.class,()->service.adjust(1L,input,10L,"老板"));
    }
    @Test void ownerWithNoProjectsStillReceivesVisibleExpenseCard()
    {
        when(mapper.selectOwnerBills(20L,"2025-02")).thenReturn(Arrays.asList(owner));when(mapper.selectProjectAllocations(2L)).thenReturn(Collections.emptyList());
        Map<String,Object> result=service.ownerWorkspace("2025-02",20L);List<?> bills=(List<?>)result.get("bills");assertEquals(1,bills.size());assertEquals(bd("100.00"),((Map<?,?>)bills.get(0)).get("remainingAmount"));
    }
    @Test void projectViewShowsOnlySelectedMonthAllocations()
    {
        when(mapper.readProjectCosts(4L,"2025-02")).thenReturn(map("monthAmount",bd("100.00"),"hasPublishedBill",1));when(mapper.selectProjectHistory(4L)).thenReturn(Arrays.asList(map("month","2025-01"),map("month","2025-02")));
        Map<String,Object> result=service.projectWorkspace(4L,"2025-02",20L,false);assertEquals(1,((List<?>)result.get("allocations")).size());assertEquals(2,((List<?>)result.get("history")).size());assertEquals(bd("4.60"),result.get("dailyReference"));
    }
    @Test void syncNewPoliciesAppendsOnlyMissingRulesAndPreservesVerifiedAmounts()
    {
        bill.put("status","DRAFT");when(mapper.selectMonth(100L,"2025-02","CNY")).thenReturn(bill);
        when(mapper.selectEntries(1L)).thenReturn(Arrays.asList(map("entryId",7L,"policyId",8L,"amount",bd("100.00"),"estimated",false)));
        when(mapper.selectOwners(100L)).thenReturn(Arrays.asList(map("userId",20L,"userName","负责人")));
        Map<String,Object> first=map("policyId",8L,"version",1,"amount",bd("300.00"),"periodType","MONTHLY","startMonth","2025-01","endMonth","2025-12","currency","CNY","status","ACTIVE");
        Map<String,Object> added=map("policyId",9L,"version",0,"amount",bd("20.00"),"periodType","MONTHLY","startMonth","2025-01","endMonth","2025-12","currency","CNY","status","ACTIVE");
        when(mapper.selectPolicies(100L)).thenReturn(Arrays.asList(first,added));
        service.generateMonth(map("companyDeptId",100L,"month","2025-02","currency","CNY","syncNewPolicies",true,"version",0),10L,"老板");
        assertEquals(bd("120.00"),bill.get("totalAmount"));verify(mapper).insertEntry(argThat(row->Long.valueOf(9L).equals(row.get("policyId"))&&bd("20.00").equals(row.get("amount"))));
        verify(mapper,never()).updateEntry(anyMap());verify(mapper).insertOwner(argThat(row->bd("120.00").equals(row.get("amount"))));
    }
    @Test void ownerExpenseCompositionAlsoConservesEveryCent()
    {
        bill.put("totalAmount",bd("0.02"));owner.put("amount",bd("0.01"));owner.put("percentage",bd("50"));
        when(mapper.selectOwnerBills(20L,"2025-02")).thenReturn(Arrays.asList(owner));when(mapper.selectEntries(1L)).thenReturn(Arrays.asList(map("amount",bd("0.01")),map("amount",bd("0.01"))));
        Map<String,Object> result=service.ownerWorkspace("2025-02",20L);Map<?,?> view=(Map<?,?>)((List<?>)result.get("bills")).get(0);
        BigDecimal total=BigDecimal.ZERO;for(Object entry:(List<?>)view.get("entries"))total=total.add((BigDecimal)((Map<?,?>)entry).get("ownerAmount"));assertEquals(bd("0.01"),total);
    }
    @Test void retainedHistoryReducesTheCombinedBudgetAndStaleAmountsCannotPublish() {
        bill.put("status","DRAFT");bill.put("totalAmount",bd("72209.83"));bill.put("personnelAmount",bd("26143.17"));
        when(mapper.selectRetainedDailyCosts(1L)).thenReturn(Arrays.asList(map("projectId",11L,"amount",bd("230.33"))));
        when(mapper.selectOwners(100L)).thenReturn(Arrays.asList(map("userId",20L,"deptId",201L),map("userId",21L,"deptId",202L)));
        service.saveOwners(1L,map("version",0,"costPool","COMBINED","allocations",Arrays.asList(map("ownerUserId",20L,"percentage",90),map("ownerUserId",21L,"percentage",10))),10L,"老板");
        assertEquals(bd("71979.50"),bill.get("allocatableAmount"));
        verify(mapper).insertOwner(argThat(r->bd("64781.55").equals(r.get("amount"))));
        verify(mapper).insertOwner(argThat(r->bd("7197.95").equals(r.get("amount"))));
        owner.put("costPool","COMBINED");owner.put("amount",bd("72209.83"));
        clearInvocations(mapper);
        assertThrows(RuntimeException.class,()->service.publish(1L,map("version",1),10L,"老板"));
        verify(mapper,never()).updateBill(anyMap());
        owner.put("amount",bd("71979.50"));
        assertDoesNotThrow(()->service.publish(1L,map("version",1),10L,"老板"));
    }
    @Test void combinedPreviewRepricesExistingOwnersWithoutMutatingStoredRows() {
        bill.put("totalAmount",bd("100.00"));bill.put("retainedAmount",bd("20.00"));owner.put("costPool","COMBINED");
        assertEquals(bd("80.00"),BusinessPublicExpenseService.combinedOwners(bill,Arrays.asList(owner)).get(0).get("amount"));
        assertEquals(bd("100.00"),owner.get("amount"));
    }
    @Test void combinedOwnerSourcesUseOnlyTheAvailableBudgetAfterHistoricalDeduction() {
        when(mapper.selectRetainedDailyCosts(1L)).thenReturn(Arrays.asList(map("amount",bd("20.00"))));
        owner.put("costPool","COMBINED");owner.put("amount",bd("80.00"));
        when(mapper.selectOwnerBills(20L,"2025-02")).thenReturn(Arrays.asList(owner));
        Map<?,?> view=(Map<?,?>)((List<?>)service.ownerWorkspace("2025-02",20L).get("bills")).get(0);
        assertEquals(bd("80.00"),view.get("totalAmount"));
        assertEquals(bd("80.00"),((Map<?,?>)((List<?>)view.get("entries")).get(0)).get("ownerAmount"));
        assertFalse(view.containsKey("personnelSnapshot"));
    }
    @Test void retainedAmountsAboveTheBudgetBlockWrites() {
        when(mapper.selectRetainedDailyCosts(1L)).thenReturn(Arrays.asList(map("amount",bd("100.01"))));
        assertThrows(RuntimeException.class,()->service.recall(1L,map("version",0,"reason","核对"),10L,"老板"));
        verify(mapper,never()).deleteBillProjects(anyLong());
    }
    @Test void previousSeparateProjectPoolsMergeByMoneyAndInvalidProjectsStayUnallocated() {
        owner.put("costPool","COMBINED");
        when(mapper.selectMonth(100L,"2025-01","CNY")).thenReturn(map("billId",9L));
        when(mapper.selectOwnerAllocations(9L)).thenReturn(Arrays.asList(
            map("allocationId",8L,"ownerUserId",20L,"costPool","EXPENSE","amount",bd("40.00")),
            map("allocationId",9L,"ownerUserId",20L,"costPool","PERSONNEL","amount",bd("60.00")),
            map("allocationId",10L,"ownerUserId",99L,"costPool","EXPENSE","amount",bd("100.00"))));
        when(mapper.selectProjectAllocations(8L)).thenReturn(Arrays.asList(map("projectId",4L,"percentage",100,"amount",bd("40.00"))));
        when(mapper.selectProjectAllocations(9L)).thenReturn(Arrays.asList(map("projectId",4L,"percentage",50,"amount",bd("30.00")),map("projectId",5L,"percentage",50,"amount",bd("30.00"))));
        when(mapper.selectProjects(100L,20L,"2025-02","CNY")).thenReturn(Arrays.asList(map("projectId",4L)));
        service.copyProjects(2L,map("version",0),20L,"负责人");
        verify(mapper).insertProject(argThat(r->Long.valueOf(4L).equals(r.get("projectId"))&&bd("70.00").equals(r.get("percentage"))&&bd("70.00").equals(r.get("amount"))));
        verify(mapper,times(1)).insertProject(anyMap());verify(mapper,never()).selectProjectAllocations(10L);
    }
    @Test void recallCannotOrphanItCreditsOnAClosedRecipientOrSource() {
        bill.put("personnelSnapshot","{\"sourceMode\":\"DEPARTMENT_NET_V1\",\"itLoss\":{\"netLoss\":10}}");
        owner.put("costPool","COMBINED");project.setAccountingState("CLOSED");
        assertThrows(RuntimeException.class,()->service.recall(1L,map("version",0,"reason","核对"),10L,"老板"));
        project.setAccountingState("OPEN");
        when(mapper.selectDailyRows(1L)).thenReturn(Arrays.asList(map("projectId",5L,"itTransferAmount",bd("-10.00"))));
        assertThrows(RuntimeException.class,()->service.recall(1L,map("version",0,"reason","核对"),10L,"老板"));
        verify(mapper,never()).deleteBillProjects(anyLong());verify(mapper,never()).insertEvent(anyMap());
    }
    private static BigDecimal bd(String value){return new BigDecimal(value);}
    private static Map<String,Object> map(Object... values){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)out.put(String.valueOf(values[i]),values[i+1]);return out;}
}
