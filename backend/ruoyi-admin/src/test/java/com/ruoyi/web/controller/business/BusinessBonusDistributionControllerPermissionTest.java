package com.ruoyi.web.controller.business;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.business.domain.BusinessBonusPayment;
import com.ruoyi.business.domain.BusinessBonusAllocation;
import java.util.Map;

class BusinessBonusDistributionControllerPermissionTest {
 @Test void allocationApprovalAndPaymentUseSeparatePermissions() throws Exception {
  Class<?> type=BusinessBonusDistributionController.class;
  assertEquals("@ss.hasPermi('business:incentive:apply')",type.getMethod("save",BusinessBonusAllocation.class).getAnnotation(PreAuthorize.class).value());
  assertEquals("@ss.hasPermi('business:incentive:apply')",type.getMethod("submit",Long.class,Map.class).getAnnotation(PreAuthorize.class).value());
  assertEquals("@ss.hasPermi('business:incentive:approve')",type.getMethod("review",Long.class,Map.class).getAnnotation(PreAuthorize.class).value());
  assertEquals("@ss.hasPermi('business:incentive:pay')",type.getMethod("payment",BusinessBonusPayment.class).getAnnotation(PreAuthorize.class).value());
  assertEquals("@ss.hasPermi('business:incentive:list')",type.getMethod("workspace",Long.class).getAnnotation(PreAuthorize.class).value());
 }
}
