package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.domain.BusinessIncentiveAward;
import com.ruoyi.business.domain.BusinessIncentiveRule;
import com.ruoyi.business.domain.BusinessIncentiveTier;
import com.ruoyi.business.domain.BusinessProjectKpiPlan;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.domain.BusinessProjectKpiSettlement;
import com.ruoyi.business.mapper.BusinessAccountingMapper;
import com.ruoyi.business.mapper.BusinessIncentiveMapper;
import com.ruoyi.business.mapper.BusinessProjectKpiMapper;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class BusinessIncentiveServiceImplTest
{
    private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

    @Mock BusinessIncentiveMapper mapper;
    @Mock BusinessProjectMapper projectMapper;
    @Mock BusinessProjectKpiMapper kpiMapper;
    @Mock BusinessAccountingMapper accountingMapper;
    @Mock BusinessProfitTaxService profitTax;
    @Mock BusinessBonusDistributionService distributionService;
    @Mock com.ruoyi.business.service.BusinessFileService businessFileService;
    @InjectMocks BusinessIncentiveServiceImpl service;
    BusinessProject project;

    @BeforeEach void setup()
    {
        project=new BusinessProject();project.setProjectId(1L);project.setCompanyDeptId(110L);
        project.setMainOwnerUserId(9L);project.setSponsorOwnerUserId(8L);project.setInitiatorUserId(8L);
        project.setStatus("ACTIVE");project.setAccountingState("OPEN");project.setDeliveryPolicyVersion("SEPARATED_V1");
        project.setBaseCurrency("CNY");project.setPlanStartDate(Date.valueOf("2026-01-01"));
        lenient().when(projectMapper.selectProjectById(1L)).thenReturn(project);
        lenient().when(projectMapper.selectProjectByIdForUpdate(1L)).thenReturn(project);
        lenient().when(profitTax.projectResult(1L)).thenReturn(currentProfit("10000.00"));
        lenient().when(profitTax.previousMonthResult(1L)).thenAnswer(call->{Map<String,Object> value=profitTax.projectResult(1L);value.put("month","2026-09");return value;});

        companyAccess=com.ruoyi.business.CompanyAccessTestSupport.sponsorFixture();
        org.springframework.test.util.ReflectionTestUtils.setField(service,"companyAccess",companyAccess);
}

    @Test void profitRuleUsesServerProfitAndHasNoKpiDependency()
    {
        BusinessIncentiveRule input=profitRule();
        input.setAfterTaxProfit(new BigDecimal("999999"));input.setAmount(new BigDecimal("999999"));
        input.setMinScore(new BigDecimal("100"));input.setKpiPlanId(77L);
        Map<String,Object> result=new LinkedHashMap<>();result.put("available",true);result.put("afterTaxProfit",new BigDecimal("1234.56"));
        when(profitTax.projectResult(1L)).thenReturn(result);
        service.publishRule(input,9L,"owner",false);
        ArgumentCaptor<BusinessIncentiveRule> captured=ArgumentCaptor.forClass(BusinessIncentiveRule.class);
        verify(mapper).insertRule(captured.capture());BusinessIncentiveRule saved=captured.getValue();
        assertEquals("PROFIT_SHARE_V1",saved.getPolicyVersion());assertEquals(new BigDecimal("1234.56"),saved.getAfterTaxProfit());
        assertEquals(new BigDecimal("308.64"),saved.getAmount());assertNull(saved.getKpiPlanId());assertNull(saved.getMinScore());
        assertEquals(new BigDecimal("10"),saved.getMainOwnerBonusRate());assertEquals(new BigDecimal("15"),saved.getSponsorOwnerBonusRate());
        assertEquals("2026-09",saved.getSettlementMonth());
        verify(kpiMapper,never()).selectPlanById(any());verify(mapper).retireProfitRules(1L,"owner");
    }

    @Test void zeroLossOrMissingCurrentProfitCannotPublishOrSaveBonusShares()
    {
        Map<String,Object> setting=new LinkedHashMap<>();setting.put("projectId",1L);
        setting.put("mainOwnerBonusRate",40);setting.put("sponsorOwnerBonusRate",60);setting.put("version",0);
        for(String amount:Arrays.asList("0.00","-2130.68",null))
        {
            when(profitTax.projectResult(1L)).thenReturn(currentProfit(amount));
            assertThrows(ServiceException.class,()->service.publishRule(profitRule(),9L,"owner",false));
            assertThrows(ServiceException.class,()->service.publishRule(rule(),9L,"owner",false));
            assertThrows(ServiceException.class,()->service.saveBonusSetting(setting,9L,"owner",false));
        }
        verify(mapper,never()).insertRule(any());verify(mapper,never()).retireProfitRules(any(),any());
        verify(mapper,never()).saveBonusSetting(anyMap());
    }

    @Test void oldPositiveSnapshotCannotCreateEditSubmitOrApproveWhenCurrentProfitIsNonPositive()
    {
        BusinessIncentiveAward source=award("DRAFT");source.setPolicyVersion("PROFIT_SHARE_V1");
        source.setRuleAfterTaxProfit(new BigDecimal("10000.00"));mockAward(source);
        for(String amount:Arrays.asList("0.00","-0.01"))
        {
            when(profitTax.projectResult(1L)).thenReturn(currentProfit(amount));source.setStatus("DRAFT");
            assertTrue(assertThrows(ServiceException.class,()->service.updateAward(21L,award("DRAFT"),9L,"owner")).getMessage().contains("税后盈利"));
            assertTrue(assertThrows(ServiceException.class,()->service.submit(21L,0,"提交",9L,"owner")).getMessage().contains("税后盈利"));
            source.setStatus("SUBMITTED");
            assertTrue(assertThrows(ServiceException.class,()->service.review(21L,0,"APPROVED","同意",8L,"boss")).getMessage().contains("税后盈利"));
        }
        verify(mapper,never()).insertAward(any());verify(accountingMapper,never()).insertFact(any());
        verify(mapper,never()).transitionAward(any(),any(),any(),any(),any(),any(),any(),any());
    }

    @Test void nonPositiveProfitStillAllowsReturningAnApplication()
    {
        BusinessIncentiveAward source=award("SUBMITTED");mockAward(source);
        lenient().when(profitTax.projectResult(1L)).thenReturn(currentProfit("-100.00"));
        when(mapper.transitionAward(21L,"SUBMITTED","RETURNED",0,8L,"boss","退回",null)).thenReturn(1);
        service.review(21L,0,"RETURNED","退回",8L,"boss");
        verify(mapper).transitionAward(21L,"SUBMITTED","RETURNED",0,8L,"boss","退回",null);
    }

    private Map<String,Object> currentProfit(String amount)
    {
        Map<String,Object> result=new LinkedHashMap<>();result.put("available",amount!=null);
        result.put("afterTaxProfit",amount==null?null:new BigDecimal(amount));return result;
    }

    @Test void profitRuleRejectsMissingOverPreciseAndExcessiveShares()
    {
        BusinessIncentiveRule input=profitRule();input.setMainOwnerBonusRate(null);
        assertThrows(ServiceException.class,()->service.publishRule(input,9L,"owner",false));
        input.setMainOwnerBonusRate(new BigDecimal("0.12345"));
        assertThrows(ServiceException.class,()->service.publishRule(input,9L,"owner",false));
        input.setMainOwnerBonusRate(new BigDecimal("90"));
        assertThrows(ServiceException.class,()->service.publishRule(input,9L,"owner",false));
        verify(mapper,never()).insertRule(any());
    }

    @Test void profitRuleEstimateUsesPublishedSnapshotWithoutKpi()
    {
        BusinessIncentiveRule source=profitRule();source.setRuleId(11L);source.setStatus("ACTIVE");source.setAmount(new BigDecimal("308.64"));
        when(mapper.selectRule(11L)).thenReturn(source);
        assertEquals(new BigDecimal("308.64"),service.estimate(1L,11L,null,9L,false).get("amount"));
        assertThrows(ServiceException.class,()->service.estimate(1L,11L,77L,9L,false));
        verify(profitTax,never()).projectResult(any());verify(kpiMapper,never()).selectSettlementById(any());
    }

    private BusinessIncentiveRule profitRule()
    {
        BusinessIncentiveRule input=rule();input.setPolicyVersion("PROFIT_SHARE_V1");
        input.setMainOwnerBonusRate(new BigDecimal("10"));input.setSponsorOwnerBonusRate(new BigDecimal("15"));return input;
    }

    @Test void monthlyPublicationUsesLastMonthNotCumulativeAndRejectsStaleClientMonth()
    {
        Map<String,Object> monthly=currentProfit("2000.00");monthly.put("month","2026-09");
        when(profitTax.previousMonthResult(1L)).thenReturn(monthly);
        BusinessIncentiveRule input=profitRule();input.setSettlementMonth("2026-08");
        assertThrows(ServiceException.class,()->service.publishRule(input,9L,"owner",false));
        input.setSettlementMonth("2026-09");service.publishRule(input,9L,"owner",false);
        ArgumentCaptor<BusinessIncentiveRule> saved=ArgumentCaptor.forClass(BusinessIncentiveRule.class);verify(mapper).insertRule(saved.capture());
        assertEquals(new BigDecimal("500.00"),saved.getValue().getAmount());assertEquals(new BigDecimal("2000.00"),saved.getValue().getAfterTaxProfit());
    }

    @Test void monthlyApplicationFreezesMonthAndOwnerPoolAndBlocksDuplicateAcrossRuleVersions()
    {
        BusinessIncentiveRule source=profitRule();source.setSettlementMonth("2026-09");source.setAfterTaxProfit(new BigDecimal("10000.00"));source.setAmount(new BigDecimal("2500.00"));
        when(mapper.selectRule(11L)).thenReturn(source);when(profitTax.monthlyBonusResult(1L,"2026-09")).thenReturn(currentProfit("10000.00"));
        BusinessIncentiveAward input=award("DRAFT");input.setBizDate(Date.valueOf("2026-10-01"));
        doAnswer(call->{BusinessIncentiveAward saved=call.getArgument(0);saved.setAwardId(21L);when(mapper.selectAward(21L)).thenReturn(saved);return 1;}).when(mapper).insertAward(any());
        BusinessIncentiveAward result=service.createAward(input,9L,"owner");
        assertEquals("2026-09",result.getSettlementMonth());assertEquals(new BigDecimal("2500.00"),result.getAmount());
        assertEquals(new BigDecimal("1000.00"),result.getRuleMainOwnerBonusAmount());assertEquals(new BigDecimal("1500.00"),result.getRuleSponsorOwnerBonusAmount());
        when(mapper.countExistingMonthlyAward(1L,"2026-09")).thenReturn(1);source.setRuleVersion(2);
        assertThrows(ServiceException.class,()->service.createAward(input,9L,"owner"));
        verify(mapper,org.mockito.Mockito.times(1)).insertAward(any());
    }

    @Test void previousMonthlyRuleAndLegacyCumulativeRuleCannotCreateNewMonthlyApplication()
    {
        BusinessIncentiveRule source=profitRule();when(mapper.selectRule(11L)).thenReturn(source);
        BusinessIncentiveAward input=award("DRAFT");input.setBizDate(Date.valueOf("2026-10-01"));
        assertThrows(ServiceException.class,()->service.createAward(input,9L,"owner"));
        source.setSettlementMonth("2026-08");assertThrows(ServiceException.class,()->service.createAward(input,9L,"owner"));
        verify(mapper,never()).insertAward(any());
    }

    @Test void monthlySubmissionKeepsItsOwnMonthAcrossRolloverAndRejectsChangedProfitOrBackdatedCost()
    {
        BusinessIncentiveAward source=award("DRAFT");source.setSettlementMonth("2026-09");source.setRuleAfterTaxProfit(new BigDecimal("10000.00"));source.setBizDate(Date.valueOf("2026-10-01"));mockAward(source);
        when(profitTax.monthlyBonusResult(1L,"2026-09")).thenReturn(currentProfit("5000.00"));
        assertThrows(ServiceException.class,()->service.submit(21L,0,"提交",9L,"owner"));
        when(profitTax.monthlyBonusResult(1L,"2026-09")).thenReturn(currentProfit("10000.00"));source.setBizDate(Date.valueOf("2026-09-30"));
        assertThrows(ServiceException.class,()->service.submit(21L,0,"提交",9L,"owner"));
        source.setBizDate(Date.valueOf("2026-10-01"));
        when(mapper.transitionAward(21L,"DRAFT","SUBMITTED",0,9L,"owner","提交",null)).thenReturn(1);
        assertEquals("2026-09",service.submit(21L,0,"提交",9L,"owner").getSettlementMonth());
        verify(profitTax,never()).previousMonthResult(any());
    }

    @Test void sponsorSeesOriginalPlanDetailsAfterOwnerSubmitsAward()
    {
        BusinessIncentiveAward award=award("DRAFT");award.setPolicyVersion("PROFIT_SHARE_V1");
        award.setRuleAfterTaxProfit(new BigDecimal("10000.00"));award.setRuleMainOwnerBonusRate(new BigDecimal("40"));
        award.setRuleSponsorOwnerBonusRate(new BigDecimal("60"));award.setRuleReason("原方案依据");mockAward(award);
        when(mapper.transitionAward(21L,"DRAFT","SUBMITTED",0,9L,"owner","提交",null)).thenAnswer(call->{award.setStatus("SUBMITTED");award.setVersion(1);return 1;});
        service.submit(21L,0,"提交",9L,"owner");
        when(mapper.selectAwards(1L)).thenReturn(Collections.singletonList(award));
        Map<String,Object> currentProfit=new LinkedHashMap<>();currentProfit.put("available",true);currentProfit.put("afterTaxProfit",new BigDecimal("99000.00"));
        when(profitTax.projectResult(1L)).thenReturn(currentProfit);
        Map<String,Object> workspace=service.workspace(1L,8L,false);
        assertEquals(true,workspace.get("canApprove"));assertEquals(true,award.getCanReview());
        assertEquals(new BigDecimal("10000.00"),award.getRuleAfterTaxProfit());
        assertEquals(new BigDecimal("4000.00"),award.getRuleMainOwnerBonusAmount());
        assertEquals(new BigDecimal("6000.00"),award.getRuleSponsorOwnerBonusAmount());assertEquals("原方案依据",award.getRuleReason());
        verify(accountingMapper,never()).insertFact(any());
    }

    @Test void ordinaryMemberAndOtherCompanyCannotReadRewardAmounts()
    {
        assertThrows(ServiceException.class,()->service.workspace(1L,10L,false));
        assertThrows(ServiceException.class,()->service.workspace(1L,88L,false));
        verify(mapper,never()).selectAwards(any());
    }

    @Test void parentOwnerCannotReadChildAwards()
    {
        project.setParentId(2L);BusinessProject parent=new BusinessProject();parent.setProjectId(2L);parent.setMainOwnerUserId(99L);
        assertThrows(com.ruoyi.common.exception.ServiceException.class,()->service.workspace(1L,99L,false));
        assertThrows(ServiceException.class,()->service.workspace(1L,88L,false));
    }

    @Test void technicalAdministratorCanReadButCannotApproveForBusinessSponsor()
    {
        BusinessIncentiveAward award=award("SUBMITTED");mockAward(award);
        when(mapper.selectAwards(1L)).thenReturn(Collections.singletonList(award));
        Map<String,Object> workspace=service.workspace(1L,1L,true);
        assertEquals(false,workspace.get("canApprove"));
        assertEquals(false,award.getCanReview());
        assertThrows(ServiceException.class,()->service.review(21L,0,"APPROVED","核准",1L,"admin"));
        verify(accountingMapper,never()).insertFact(any());
    }

    @Test void ownerAndCreatorCannotApproveTheirOwnReward()
    {
        BusinessIncentiveAward award=award("SUBMITTED");mockAward(award);
        assertThrows(ServiceException.class,()->service.review(21L,0,"APPROVED","核准",9L,"owner"));
        project.setSponsorOwnerUserId(9L);
        ServiceException error=assertThrows(ServiceException.class,()->service.review(21L,0,"APPROVED","核准",9L,"owner"));
        assertTrue(error.getMessage().contains("本人"));
    }

    @Test void projectSponsorCreatesImmutableRuleVersion()
    {
        BusinessIncentiveRule input=rule();input.setRuleId(999L);input.setPolicyVersion("OVERRIDE");input.setRuleVersion(999);
        when(mapper.nextRuleVersion(1L)).thenReturn(3);
        doAnswer(call->{((BusinessIncentiveRule)call.getArgument(0)).setRuleId(11L);return 1;}).when(mapper).insertRule(any());
        when(mapper.selectRule(11L)).thenReturn(rule());
        service.publishRule(input,8L,"boss",false);
        ArgumentCaptor<BusinessIncentiveRule> captured=ArgumentCaptor.forClass(BusinessIncentiveRule.class);
        verify(mapper).insertRule(captured.capture());
        assertEquals("FIXED_V1",captured.getValue().getPolicyVersion());
        assertEquals(Integer.valueOf(3),captured.getValue().getRuleVersion());
    }

    @Test void ownerSponsorAndAdministratorCanConfigureRulesButOrdinaryCreatorCannot()
    {
        for (Long userId : Arrays.asList(9L,8L,1L))
        {
            Map<String,Object> workspace=service.workspace(1L,userId,userId==1L);
            assertEquals(true,workspace.get("canManageRules"));
            assertEquals(true,workspace.get("canRetireRules"));
        }
        project.setInitiatorUserId(10L);
        for (Long userId : Arrays.asList(10L,88L))
            assertThrows(ServiceException.class,()->service.publishRule(scoreRule(),userId,"other",false));
        verify(mapper,never()).insertRule(any());
    }

    @Test void workspaceShowsAuthoritativeAfterTaxProfitAndSavedBonusShares()
    {
        Map<String,Object> profit=new LinkedHashMap<>();profit.put("available",true);profit.put("afterTaxProfit",new BigDecimal("9000.00"));profit.put("currency","CNY");
        Map<String,Object> setting=new LinkedHashMap<>();setting.put("mainOwnerBonusRate",new BigDecimal("10.0000"));setting.put("sponsorOwnerBonusRate",new BigDecimal("5.0000"));setting.put("version",2);
        when(profitTax.projectResult(1L)).thenReturn(profit);when(mapper.selectBonusSetting(1L)).thenReturn(setting);
        Map<String,Object> result=service.workspace(1L,9L,false);
        assertEquals(new BigDecimal("9000.00"),((Map<?,?>)result.get("profitResult")).get("afterTaxProfit"));
        assertEquals(new BigDecimal("10.0000"),((Map<?,?>)result.get("bonusSetting")).get("mainOwnerBonusRate"));
    }

    @Test void bonusSharesAreVersionedAuditedAndCannotExceedFullProfit()
    {
        Map<String,Object> input=new LinkedHashMap<>();input.put("projectId",1L);input.put("mainOwnerBonusRate","60.5");input.put("sponsorOwnerBonusRate","39.5");input.put("version",0);
        Map<String,Object> saved=new LinkedHashMap<>(input);saved.put("version",1);
        when(mapper.selectBonusSetting(1L)).thenReturn(null,saved);
        assertEquals(1,service.saveBonusSetting(input,9L,"owner",false).get("version"));
        verify(mapper).saveBonusSetting(anyMap());verify(mapper).insertBonusSettingEvent(anyMap());

        input.put("mainOwnerBonusRate","60.5001");
        assertThrows(ServiceException.class,()->service.saveBonusSetting(input,9L,"owner",false));
        input.put("mainOwnerBonusRate","10");input.put("sponsorOwnerBonusRate","5");input.put("version",0);
        assertThrows(ServiceException.class,()->service.saveBonusSetting(input,9L,"owner",false));
    }

    @Test void ownerCanPublishAndRetireScoreRuleForOwnProjectOnly()
    {
        mockPlan();BusinessIncentiveRule input=scoreRule();
        when(mapper.nextRuleVersion(1L)).thenReturn(1);
        doAnswer(call->{((BusinessIncentiveRule)call.getArgument(0)).setRuleId(11L);return 1;}).when(mapper).insertRule(any());
        when(mapper.selectRule(11L)).thenReturn(input);
        service.publishRule(input,9L,"owner",false);
        when(mapper.retireRule(11L,"owner")).thenReturn(1);
        service.retireRule(11L,"调整目标",9L,"owner",false);
        verify(mapper).insertRule(any());verify(mapper).retireRule(11L,"owner");
        assertThrows(ServiceException.class,()->service.retireRule(11L,"other project",19L,"other",false));
    }

    @Test void administratorCanPublishScoreRulesWithoutBecomingRewardApprover()
    {
        mockPlan();when(mapper.nextRuleVersion(1L)).thenReturn(1);
        doAnswer(call->{((BusinessIncentiveRule)call.getArgument(0)).setRuleId(11L);return 1;}).when(mapper).insertRule(any());
        when(mapper.selectRule(11L)).thenReturn(scoreRule());
        service.publishRule(scoreRule(),1L,"admin",true);
        verify(mapper).insertRule(any());
        mockAward(award("SUBMITTED"));
        assertThrows(ServiceException.class,()->service.review(21L,0,"APPROVED","确认",1L,"admin"));
        verify(accountingMapper,never()).insertFact(any());
    }

    @Test void ownerRuleMaintenanceStillRequiresActiveProjectAndOpenAccounting()
    {
        project.setStatus("CLOSED");
        assertEquals(false,service.workspace(1L,9L,false).get("canManageRules"));
        assertThrows(ServiceException.class,()->service.publishRule(scoreRule(),9L,"owner",false));
        project.setStatus("ACTIVE");project.setAccountingState("CLOSED");
        Map<String,Object> workspace=service.workspace(1L,9L,false);
        assertEquals(false,workspace.get("canManageRules"));assertEquals(false,workspace.get("canRetireRules"));
        assertThrows(ServiceException.class,()->service.publishRule(scoreRule(),9L,"owner",false));
        verify(mapper,never()).insertRule(any());
    }

    @Test void estimateHasNoAccountingSideEffectsAndKpiIsOptional()
    {
        when(mapper.selectRule(11L)).thenReturn(rule());
        Map<String,Object> estimate=service.estimate(1L,11L,null,9L,false);
        assertEquals("ESTIMATE_ONLY",estimate.get("status"));
        assertEquals(new BigDecimal("800.00"),estimate.get("amount"));
        verify(accountingMapper,never()).insertFact(any());
        verify(mapper,never()).insertAward(any());
    }

    @Test void legacyLinkedKpiCannotGenerateIndependentBonusAgain()
    {
        when(mapper.selectRule(11L)).thenReturn(rule());
        when(kpiMapper.selectSettlementById(20L)).thenReturn(evidence("LEGACY_LINKED"));
        ServiceException error=assertThrows(ServiceException.class,()->service.estimate(1L,11L,20L,9L,false));
        assertTrue(error.getMessage().contains("历史联动"));
    }

    @Test void unconfirmedOrCrossProjectKpiCannotBeRewardEvidence()
    {
        when(mapper.selectRule(11L)).thenReturn(rule());
        BusinessProjectKpiSettlement evidence=evidence("INDEPENDENT_V1");evidence.setProjectId(2L);
        when(kpiMapper.selectSettlementById(20L)).thenReturn(evidence);
        assertThrows(ServiceException.class,()->service.estimate(1L,11L,20L,9L,false));
        evidence.setProjectId(1L);evidence.setStatus("DRAFT");
        assertThrows(ServiceException.class,()->service.estimate(1L,11L,20L,9L,false));
    }

    @Test void minimumScoreRequiresConfirmedEvidenceMeetingThreshold()
    {
        BusinessIncentiveRule rule=rule();rule.setMinScore(new BigDecimal("110"));
        when(mapper.selectRule(11L)).thenReturn(rule);
        when(kpiMapper.selectSettlementById(20L)).thenReturn(evidence("INDEPENDENT_V1"));
        assertThrows(ServiceException.class,()->service.estimate(1L,11L,null,9L,false));
        assertThrows(ServiceException.class,()->service.estimate(1L,11L,20L,9L,false));
    }

    @Test void createFreezesServerAmountAndIgnoresClientApprovalAndFactFields()
    {
        BusinessIncentiveAward input=award("APPROVED");input.setAmount(new BigDecimal("999999"));input.setAccountingFactId(999L);
        when(mapper.selectRule(11L)).thenReturn(rule());
        doAnswer(call->{((BusinessIncentiveAward)call.getArgument(0)).setAwardId(21L);return 1;}).when(mapper).insertAward(any());
        when(mapper.selectAward(21L)).thenReturn(award("DRAFT"));
        service.createAward(input,9L,"owner");
        ArgumentCaptor<BusinessIncentiveAward> captured=ArgumentCaptor.forClass(BusinessIncentiveAward.class);
        verify(mapper).insertAward(captured.capture());
        assertEquals("DRAFT",captured.getValue().getStatus());
        assertEquals(new BigDecimal("800.00"),captured.getValue().getAmount());
        assertNull(captured.getValue().getAccountingFactId());
        assertEquals("NOT_RECORDED",captured.getValue().getPaymentStatus());
    }

    @Test void combinedApplicationSavesValidatedProposalWithoutCreatingAnAllocationYet()
    {
        BusinessIncentiveAward input=award("DRAFT");com.ruoyi.business.domain.BusinessBonusAllocation proposal=new com.ruoyi.business.domain.BusinessBonusAllocation();input.setApplicationAllocation(proposal);
        when(mapper.selectRule(11L)).thenReturn(rule());when(distributionService.prepareApplicationAllocation(eq(proposal),any())).thenReturn(proposal);
        doAnswer(call->{BusinessIncentiveAward saved=call.getArgument(0);saved.setAwardId(21L);when(mapper.selectAward(21L)).thenReturn(saved);return 1;}).when(mapper).insertAward(any());
        BusinessIncentiveAward saved=service.createAward(input,9L,"owner");assertEquals(proposal,saved.getApplicationAllocation());assertEquals("DRAFT",saved.getStatus());
        verify(distributionService,never()).save(any(),any(),any());
    }

    @Test void applicantCanEditDraftOrReturnedButNotSubmittedAward()
    {
        BusinessIncentiveAward existing=award("DRAFT");mockAward(existing);
        BusinessIncentiveAward input=award("DRAFT");input.setBizDate(Date.valueOf("2026-07-01"));input.setReason("更新成员分配依据");
        when(mapper.updateAwardDraft(21L,0,existing.getBizDate(),input.getReason(),null,null,"owner")).thenReturn(1);
        service.updateAward(21L,input,9L,"owner");
        verify(mapper).updateAwardDraft(21L,0,input.getBizDate(),input.getReason(),null,null,"owner");
        existing.setStatus("RETURNED");service.updateAward(21L,input,9L,"owner");
        existing.setStatus("SUBMITTED");assertThrows(ServiceException.class,()->service.updateAward(21L,input,9L,"owner"));
        existing.setStatus("DRAFT");assertThrows(ServiceException.class,()->service.updateAward(21L,input,8L,"boss"));
        input.setRuleId(12L);assertThrows(ServiceException.class,()->service.updateAward(21L,input,9L,"owner"));
    }

    @Test void invalidCombinedProposalDoesNotInsertAnAward()
    {
        BusinessIncentiveAward input=award("DRAFT");com.ruoyi.business.domain.BusinessBonusAllocation proposal=new com.ruoyi.business.domain.BusinessBonusAllocation();input.setApplicationAllocation(proposal);
        when(mapper.selectRule(11L)).thenReturn(rule());when(distributionService.prepareApplicationAllocation(eq(proposal),any())).thenThrow(new ServiceException("超额"));
        assertThrows(ServiceException.class,()->service.createAward(input,9L,"owner"));verify(mapper,never()).insertAward(any());
    }

    @Test void combinedRetryRejectsChangedAllocationWithoutCreatingAnotherAward()
    {
        BusinessIncentiveAward existing=award("DRAFT");com.ruoyi.business.domain.BusinessBonusAllocation proposal=new com.ruoyi.business.domain.BusinessBonusAllocation();existing.setApplicationAllocation(proposal);
        when(mapper.selectAwardByRequest(1L,"request-12345678")).thenReturn(existing);
        BusinessIncentiveAward input=award("DRAFT");input.setApplicationAllocation(proposal);when(distributionService.sameApplicationAllocation(proposal,existing)).thenReturn(true);
        service.createAward(input,9L,"owner");assertThrows(ServiceException.class,()->service.createAward(award("DRAFT"),9L,"owner"));verify(mapper,never()).insertAward(any());
    }

    @Test void reviewingCombinedAwardCreatesOnlyAnOwnerDraftOnceAndReturnCreatesNothing()
    {
        BusinessIncentiveAward application=award("SUBMITTED");com.ruoyi.business.domain.BusinessBonusAllocation proposal=new com.ruoyi.business.domain.BusinessBonusAllocation();application.setApplicationAllocation(proposal);mockAward(application);
        Map<String,Object> category=new LinkedHashMap<>();category.put("categoryId",3L);category.put("categoryName","奖金成本");when(accountingMapper.selectCategoryByCode("PROJECT_BONUS_COST")).thenReturn(category);
        when(mapper.transitionAward(eq(21L),eq("SUBMITTED"),eq("APPROVED"),eq(0),eq(8L),eq("boss"),eq("核准"),any())).thenAnswer(call->{application.setStatus("APPROVED");return 1;});
        service.review(21L,0,"APPROVED","核准",8L,"boss");service.review(21L,0,"APPROVED","再次核准",8L,"boss");
        verify(distributionService).save(proposal,9L,"owner");assertEquals(Long.valueOf(21L),proposal.getAwardId());assertEquals("AWARD-APPLICATION-21",proposal.getRequestKey());
        application.setStatus("SUBMITTED");when(mapper.transitionAward(eq(21L),eq("SUBMITTED"),eq("RETURNED"),eq(0),eq(8L),eq("boss"),eq("退回"),any())).thenReturn(1);
        service.review(21L,0,"RETURNED","退回",8L,"boss");verify(distributionService,org.mockito.Mockito.times(1)).save(any(),any(),any());
    }

    @Test void duplicateRequestReturnsOriginalButDifferentPayloadIsRejected()
    {
        BusinessIncentiveAward input=award("DRAFT");
        when(mapper.selectAwardByRequest(1L,"request-12345678")).thenReturn(input);
        assertEquals(Long.valueOf(21),service.createAward(award("DRAFT"),9L,"owner").getAwardId());
        BusinessIncentiveAward other=award("DRAFT");other.setReason("另一份奖励");
        assertThrows(ServiceException.class,()->service.createAward(other,9L,"owner"));
        verify(mapper,never()).insertAward(any());
    }

    @Test void sameKpiAndRuleCannotBeAwardedAgainWithDifferentRequestKey()
    {
        BusinessIncentiveAward input=award("DRAFT");input.setSettlementId(20L);
        when(mapper.selectRule(11L)).thenReturn(rule());
        when(kpiMapper.selectSettlementById(20L)).thenReturn(evidence("INDEPENDENT_V1"));
        when(mapper.countExistingEvidenceAward(1L,11L,20L)).thenReturn(1);
        assertThrows(ServiceException.class,()->service.createAward(input,9L,"owner"));
        verify(mapper,never()).insertAward(any());
    }

    @Test void approvalCreatesOneDraftProtectedSourceAndDoesNotConfirmOrPay()
    {
        BusinessIncentiveAward award=award("SUBMITTED");mockAward(award);
        Map<String,Object> category=new LinkedHashMap<String,Object>();category.put("categoryId",17L);category.put("categoryName","项目奖金");
        when(accountingMapper.selectCategoryByCode("PROJECT_BONUS_COST")).thenReturn(category);
        doAnswer(call->{((BusinessOperatingFact)call.getArgument(0)).setFactId(71L);return 1;}).when(accountingMapper).insertFact(any());
        when(mapper.transitionAward(21L,"SUBMITTED","APPROVED",0,8L,"boss","同意",71L)).thenReturn(1);

        service.review(21L,0,"APPROVED","同意",8L,"boss");

        ArgumentCaptor<BusinessOperatingFact> captured=ArgumentCaptor.forClass(BusinessOperatingFact.class);
        verify(accountingMapper).insertFact(captured.capture());
        BusinessOperatingFact fact=captured.getValue();
        assertEquals("DRAFT",fact.getStatus());assertEquals("HR_INCENTIVE",fact.getSourceDomain());
        assertEquals("BONUS",fact.getSourceType());assertEquals("21",fact.getSourceId());
        assertEquals("HR-INCENTIVE-AWARD-21",fact.getIdempotencyKey());
        assertEquals(new BigDecimal("800.00"),fact.getAmount());assertNull(fact.getConfirmedUserId());
        verify(accountingMapper,never()).confirmFact(any(),any(),any(),any());
    }

    @Test void repeatedApprovedRequestDoesNotInsertAnotherCostOrEvent()
    {
        BusinessIncentiveAward award=award("APPROVED");award.setAccountingFactId(71L);mockAward(award);
        assertEquals("APPROVED",service.review(21L,0,"APPROVED","同意",8L,"boss").getStatus());
        verify(accountingMapper,never()).insertFact(any());verify(mapper,never()).insertEvent(any());
    }

    @Test void staleReviewVersionAndClosedLedgerCannotApprove()
    {
        mockAward(award("SUBMITTED"));
        assertThrows(ServiceException.class,()->service.review(21L,9,"APPROVED","同意",8L,"boss"));
        project.setAccountingState("CLOSED");
        assertThrows(ServiceException.class,()->service.review(21L,0,"APPROVED","同意",8L,"boss"));
        verify(accountingMapper,never()).insertFact(any());
    }

    @Test void lateRewardMustBelongToExecutionDateRange()
    {
        project.setStatus("CLOSED");project.setActualEndDate(Date.valueOf("2026-07-01"));
        BusinessIncentiveAward input=award("DRAFT");input.setBizDate(Date.valueOf("2026-07-02"));
        assertThrows(ServiceException.class,()->service.createAward(input,9L,"owner"));
        input.setBizDate(Date.valueOf("2025-12-31"));
        assertThrows(ServiceException.class,()->service.createAward(input,9L,"owner"));
        verify(mapper,never()).insertAward(any());
    }

    @Test void cancelApprovedUnconfirmedCostVoidsSourceWithoutDeletingHistory()
    {
        BusinessIncentiveAward award=award("APPROVED");award.setAccountingFactId(71L);mockAward(award);
        when(accountingMapper.selectFactByIdForUpdate(71L)).thenReturn(fact("DRAFT"));
        when(mapper.voidSourceFact(71L,0,"boss")).thenReturn(1);
        when(mapper.transitionAward(21L,"APPROVED","CANCELED",0,8L,"boss","重复申请",71L)).thenReturn(1);
        service.cancel(21L,0,"重复申请",8L,"boss");
        verify(mapper).voidSourceFact(71L,0,"boss");verify(mapper).insertEvent(any());
        verify(accountingMapper,never()).markFactReversed(any(),any(),any());
    }

    @Test void approvedCostCannotBeCanceledAfterConfirmationOrReversal()
    {
        BusinessIncentiveAward award=award("APPROVED");award.setAccountingFactId(71L);mockAward(award);
        when(accountingMapper.selectFactByIdForUpdate(71L)).thenReturn(fact("CONFIRMED"),fact("REVERSED"));
        assertThrows(ServiceException.class,()->service.cancel(21L,0,"撤销",8L,"boss"));
        assertThrows(ServiceException.class,()->service.cancel(21L,0,"撤销",8L,"boss"));
        verify(mapper,never()).voidSourceFact(any(),any(),any());
    }

    @Test void returnedCostIsResubmittedWithoutChangingAmountOrApprovalIdentity()
    {
        BusinessIncentiveAward award=award("APPROVED");award.setAccountingFactId(71L);mockAward(award);
        when(accountingMapper.selectFactByIdForUpdate(71L)).thenReturn(fact("RETURNED"));
        when(mapper.resubmitSourceFact(71L,0,"boss")).thenReturn(1);
        when(mapper.transitionAward(21L,"APPROVED","APPROVED",0,8L,"boss","已补核准依据",71L)).thenReturn(1);
        service.resubmitCost(21L,0,"已补核准依据",8L,"boss");
        verify(mapper).resubmitSourceFact(71L,0,"boss");
        verify(accountingMapper,never()).insertFact(any());verify(accountingMapper,never()).updateDraftFact(any());
    }

    @Test void returnedRewardRequiresExplanationAndResubmissionKeepsSameSnapshot()
    {
        BusinessIncentiveAward award=award("RETURNED");mockAward(award);
        assertThrows(ServiceException.class,()->service.submit(21L,0,"",9L,"owner"));
        when(mapper.transitionAward(eq(21L),eq("RETURNED"),eq("SUBMITTED"),eq(0),eq(9L),eq("owner"),eq("已补说明"),isNull())).thenReturn(1);
        service.submit(21L,0,"已补说明",9L,"owner");
        verify(mapper,never()).insertAward(any());verify(accountingMapper,never()).insertFact(any());
    }

    @Test void scoreRuleBindsPublishedKpiAndStoresServerControlledTierVersion()
    {
        BusinessIncentiveRule input=scoreRule();mockPlan();
        input.getTiers().get(0).setRuleId(999L);input.getTiers().get(0).setSortOrder(999);
        when(mapper.nextRuleVersion(1L)).thenReturn(2);
        doAnswer(call->{((BusinessIncentiveRule)call.getArgument(0)).setRuleId(12L);return 1;}).when(mapper).insertRule(any());
        when(mapper.selectRule(12L)).thenReturn(input);
        service.publishRule(input,8L,"boss",false);
        ArgumentCaptor<BusinessIncentiveRule> rule=ArgumentCaptor.forClass(BusinessIncentiveRule.class);
        verify(mapper).insertRule(rule.capture());
        assertEquals("SCORE_TIERS_V1",rule.getValue().getPolicyVersion());
        assertEquals(Long.valueOf(10),rule.getValue().getKpiPlanId());
        assertEquals(new BigDecimal("2000.00"),rule.getValue().getAmount());
        verify(mapper).retirePlanRules(1L,10L,"boss");
        ArgumentCaptor<BusinessIncentiveTier> tiers=ArgumentCaptor.forClass(BusinessIncentiveTier.class);
        verify(mapper,org.mockito.Mockito.times(3)).insertTier(tiers.capture());
        assertEquals(Long.valueOf(12),tiers.getAllValues().get(0).getRuleId());
        assertEquals(Integer.valueOf(1),tiers.getAllValues().get(0).getSortOrder());
    }

    @Test void cannotBindMissingCrossProjectLegacyOrVoidedKpiPlan()
    {
        BusinessIncentiveRule input=scoreRule();
        assertThrows(ServiceException.class,()->service.publishRule(input,8L,"boss",false));
        BusinessProjectKpiPlan plan=mockPlan();plan.setProjectId(2L);
        assertThrows(ServiceException.class,()->service.publishRule(input,8L,"boss",false));
        plan.setProjectId(1L);plan.setRewardPolicyVersion("LEGACY_LINKED");
        assertThrows(ServiceException.class,()->service.publishRule(input,8L,"boss",false));
        plan.setRewardPolicyVersion("INDEPENDENT_V1");plan.setStatus("VOIDED");
        assertThrows(ServiceException.class,()->service.publishRule(input,8L,"boss",false));
        verify(mapper,never()).insertRule(any());verify(mapper,never()).retirePlanRules(any(),any(),any());
    }

    @Test void scoreRulesRejectGapsOverlapMissingFinalBoundNegativeAndOverprecisionAmounts()
    {
        mockPlan();
        for (int scenario=0;scenario<7;scenario++)
        {
            BusinessIncentiveRule input=scoreRule();
            switch(scenario)
            {
                case 0: input.getTiers().get(0).setMinScore(BigDecimal.ONE);break;
                case 1: input.getTiers().get(1).setMinScore(new BigDecimal("81"));break;
                case 2: input.getTiers().get(1).setMinScore(new BigDecimal("79"));break;
                case 3: input.getTiers().get(2).setMaxScore(new BigDecimal("120"));break;
                case 4: input.getTiers().get(1).setAmount(new BigDecimal("-1"));break;
                case 5: input.getTiers().get(1).setAmount(new BigDecimal("1.001"));break;
                default: input.getTiers().forEach(tier->tier.setAmount(BigDecimal.ZERO));break;
            }
            assertThrows(ServiceException.class,()->service.publishRule(input,8L,"boss",false));
        }
        verify(mapper,never()).insertRule(any());verify(mapper,never()).retirePlanRules(any(),any(),any());
    }

    @Test void scoreEstimatesSelectExactlyOneTierIncludingBoundariesAndHaveNoWrites()
    {
        mockPlan();when(mapper.selectRule(11L)).thenReturn(scoreRule());
        BusinessProjectKpiSettlement evidence=evidence("INDEPENDENT_V1");evidence.setPlanId(10L);
        when(kpiMapper.selectSettlementById(20L)).thenReturn(evidence);
        String[][] cases={{"0","0.00"},{"79.99","0.00"},{"80","1000.00"},{"99.99","1000.00"},{"100","2000.00"},{"120","2000.00"}};
        for(String[] row:cases){evidence.setTotalScore(new BigDecimal(row[0]));assertEquals(new BigDecimal(row[1]),service.estimate(1L,11L,20L,9L,false).get("amount"));}
        verify(mapper,never()).insertAward(any());verify(accountingMapper,never()).insertFact(any());
    }

    @Test void scoreRuleRequiresItsOwnConfirmedPlanResult()
    {
        mockPlan();when(mapper.selectRule(11L)).thenReturn(scoreRule());
        assertThrows(ServiceException.class,()->service.estimate(1L,11L,null,9L,false));
        BusinessProjectKpiSettlement evidence=evidence("INDEPENDENT_V1");evidence.setPlanId(12L);
        when(kpiMapper.selectSettlementById(20L)).thenReturn(evidence);
        assertThrows(ServiceException.class,()->service.estimate(1L,11L,20L,9L,false));
        evidence.setPlanId(10L);evidence.setStatus("DRAFT");
        assertThrows(ServiceException.class,()->service.estimate(1L,11L,20L,9L,false));
        evidence.setStatus("CONFIRMED");evidence.setTotalScore(null);
        assertThrows(ServiceException.class,()->service.estimate(1L,11L,20L,9L,false));
    }

    @Test void scoreAwardFreezesMatchedAmountAndRejectsZeroOrRepeatAcrossVersions()
    {
        mockPlan();when(mapper.selectRule(11L)).thenReturn(scoreRule());
        BusinessProjectKpiSettlement evidence=evidence("INDEPENDENT_V1");evidence.setPlanId(10L);evidence.setTotalScore(new BigDecimal("79"));
        when(kpiMapper.selectSettlementById(20L)).thenReturn(evidence);
        BusinessIncentiveAward input=award("DRAFT");input.setSettlementId(20L);input.setAmount(new BigDecimal("9999"));
        assertThrows(ServiceException.class,()->service.createAward(input,9L,"owner"));
        evidence.setTotalScore(new BigDecimal("100"));when(mapper.countExistingScoreAward(1L,20L)).thenReturn(1);
        assertThrows(ServiceException.class,()->service.createAward(input,9L,"owner"));
        when(mapper.countExistingScoreAward(1L,20L)).thenReturn(0);
        doAnswer(call->{((BusinessIncentiveAward)call.getArgument(0)).setAwardId(21L);return 1;}).when(mapper).insertAward(any());
        when(mapper.selectAward(21L)).thenReturn(input);
        service.createAward(input,9L,"owner");
        ArgumentCaptor<BusinessIncentiveAward> saved=ArgumentCaptor.forClass(BusinessIncentiveAward.class);
        verify(mapper).insertAward(saved.capture());assertEquals(new BigDecimal("2000.00"),saved.getValue().getAmount());
        assertEquals(new BigDecimal("100"),saved.getValue().getScoreSnapshot());
    }

    @Test void tierAwardApprovalKeepsRetiredRuleSnapshotAndRejectsWrongAmount()
    {
        mockPlan();BusinessIncentiveRule rule=scoreRule();rule.setStatus("RETIRED");when(mapper.selectRule(11L)).thenReturn(rule);
        BusinessProjectKpiSettlement evidence=evidence("INDEPENDENT_V1");evidence.setPlanId(10L);
        when(kpiMapper.selectSettlementById(20L)).thenReturn(evidence);
        BusinessIncentiveAward award=award("SUBMITTED");award.setPolicyVersion("SCORE_TIERS_V1");award.setSettlementId(20L);award.setScoreSnapshot(new BigDecimal("100"));mockAward(award);
        assertThrows(ServiceException.class,()->service.review(21L,0,"APPROVED","同意",8L,"boss"));
        award.setAmount(new BigDecimal("2000.00"));
        Map<String,Object> category=new LinkedHashMap<String,Object>();category.put("categoryId",17L);category.put("categoryName","项目奖金");
        when(accountingMapper.selectCategoryByCode("PROJECT_BONUS_COST")).thenReturn(category);
        doAnswer(call->{((BusinessOperatingFact)call.getArgument(0)).setFactId(71L);return 1;}).when(accountingMapper).insertFact(any());
        when(mapper.transitionAward(21L,"SUBMITTED","APPROVED",0,8L,"boss","同意",71L)).thenReturn(1);
        service.review(21L,0,"APPROVED","同意",8L,"boss");
        ArgumentCaptor<BusinessOperatingFact> cost=ArgumentCaptor.forClass(BusinessOperatingFact.class);
        verify(accountingMapper).insertFact(cost.capture());assertEquals(new BigDecimal("2000.00"),cost.getValue().getAmount());
        assertEquals("DRAFT",cost.getValue().getStatus());
    }

    private BusinessProjectKpiPlan mockPlan()
    {
        BusinessProjectKpiPlan plan=new BusinessProjectKpiPlan();plan.setPlanId(10L);plan.setProjectId(1L);plan.setStatus("PUBLISHED");plan.setRewardPolicyVersion("INDEPENDENT_V1");
        when(kpiMapper.selectPlanById(10L)).thenReturn(plan);return plan;
    }
    private BusinessIncentiveRule scoreRule()
    {
        BusinessIncentiveRule r=rule();r.setPolicyVersion("SCORE_TIERS_V1");r.setKpiPlanId(10L);
        r.setTiers(Arrays.asList(tier("0","80","0.00"),tier("80","100","1000.00"),tier("100",null,"2000.00")));return r;
    }
    private BusinessIncentiveTier tier(String min,String max,String amount)
    {
        BusinessIncentiveTier t=new BusinessIncentiveTier();t.setMinScore(new BigDecimal(min));t.setMaxScore(max==null?null:new BigDecimal(max));t.setAmount(new BigDecimal(amount));return t;
    }

    private void mockAward(BusinessIncentiveAward award)
    {
        lenient().when(mapper.selectAward(21L)).thenReturn(award);
        lenient().when(mapper.selectAwardForUpdate(21L)).thenReturn(award);
    }
    private BusinessIncentiveRule rule()
    {
        BusinessIncentiveRule rule=new BusinessIncentiveRule();rule.setRuleId(11L);rule.setProjectId(1L);
        rule.setRuleVersion(1);rule.setRuleName("交付奖励");rule.setPolicyVersion("FIXED_V1");rule.setStatus("ACTIVE");
        rule.setAmount(new BigDecimal("800.00"));rule.setCurrency("CNY");rule.setReason("交付成果奖励规则");return rule;
    }
    private BusinessIncentiveAward award(String status)
    {
        BusinessIncentiveAward a=new BusinessIncentiveAward();a.setAwardId(21L);a.setProjectId(1L);a.setCompanyDeptId(110L);
        a.setRuleId(11L);a.setRuleName("交付奖励");a.setRuleVersion(1);a.setPolicyVersion("FIXED_V1");
        a.setAmount(new BigDecimal("800.00"));a.setCurrency("CNY");a.setStatus(status);a.setVersion(0);
        a.setApplicantUserId(9L);a.setApplicantUserName("owner");a.setBizDate(Date.valueOf("2026-06-30"));
        a.setReason("交付成果已核对");a.setRequestKey("request-12345678");return a;
    }

    @Test void launchedProjectCanCreateDirectAwardWithoutProfitOrPublishedRule()
    {
        BusinessIncentiveAward input=award("DRAFT");input.setRuleId(null);input.setApplicationMonth("2026-09");input.setAmount(new BigDecimal("300.00"));input.setAttachmentUrls("/profile/upload/business/9/1/evidence.pdf");
        when(mapper.nextRuleVersion(1L)).thenReturn(1);
        doAnswer(call->{((BusinessIncentiveRule)call.getArgument(0)).setRuleId(31L);return 1;}).when(mapper).insertRule(any());
        final BusinessIncentiveAward[] stored=new BusinessIncentiveAward[1];
        doAnswer(call->{stored[0]=call.getArgument(0);stored[0].setAwardId(21L);stored[0].setVersion(0);return 1;}).when(mapper).insertAward(any());
        when(mapper.selectAward(21L)).thenAnswer(call->stored[0]);
        BusinessIncentiveAward created=service.createAward(input,9L,"owner");
        assertEquals(new BigDecimal("300.00"),created.getAmount());assertEquals("FIXED_V1",created.getPolicyVersion());
        assertEquals(Date.valueOf(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))),created.getBizDate());
        assertEquals(input.getAttachmentUrls(),created.getAttachmentUrls());
        verify(businessFileService).validateReferences(input.getAttachmentUrls(),1L,9L,false,false);
        assertEquals("2026-09",created.getSettlementMonth());verify(profitTax,never()).previousMonthResult(any());
        assertThrows(ServiceException.class,()->service.createAward(input,88L,"other"));
    }
    private BusinessProjectKpiSettlement evidence(String policy)
    {
        BusinessProjectKpiSettlement s=new BusinessProjectKpiSettlement();s.setSettlementId(20L);s.setProjectId(1L);
        s.setStatus("CONFIRMED");s.setRewardPolicyVersion(policy);s.setTotalScore(new BigDecimal("100"));return s;
    }
    private BusinessOperatingFact fact(String status)
    {
        BusinessOperatingFact fact=new BusinessOperatingFact();fact.setFactId(71L);fact.setProjectId(1L);
        fact.setSourceDomain("HR_INCENTIVE");fact.setSourceType("BONUS");fact.setSourceId("21");
        fact.setAmount(new BigDecimal("800.00"));fact.setCurrency("CNY");fact.setBizDate(Date.valueOf("2026-06-30"));
        fact.setStatus(status);fact.setVersion(0);return fact;
    }
}
