package com.ruoyi.business.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.*;

public interface BusinessBonusDistributionMapper
{
    List<Map<String,Object>> projects(@Param("userId") Long userId,@Param("admin") boolean admin,@Param("finance") boolean finance);
    int companyAccess(@Param("projectId") Long projectId,@Param("userId") Long userId);
    List<Map<String,Object>> recipients(Long projectId);
    List<BusinessBonusAllocation> allocations(Long projectId);
    BusinessBonusAllocation allocation(Long allocationId);
    BusinessBonusAllocation byRequest(@Param("projectId") Long projectId,@Param("requestKey") String requestKey);
    List<BusinessBonusAllocationLine> lines(Long allocationId);
    BusinessBonusAllocationLine line(Long lineId);
    BigDecimal reserved(@Param("awardId") Long awardId,@Param("excludeId") Long excludeId);
    int countAllocations(Long awardId);
    int insertAllocation(BusinessBonusAllocation value);
    int updateAllocation(BusinessBonusAllocation value);
    int deleteLines(Long allocationId);
    int insertLine(BusinessBonusAllocationLine value);
    int transition(@Param("id") Long id,@Param("version") Integer version,@Param("status") String status,@Param("userId") Long userId,@Param("userName") String userName);
    List<BusinessBonusPayment> payments(Long allocationId);
    BusinessBonusPayment paymentRequest(@Param("projectId") Long projectId,@Param("requestKey") String requestKey);
    BusinessBonusPayment paymentReference(@Param("lineId") Long lineId,@Param("referenceNo") String referenceNo);
    int insertPayment(BusinessBonusPayment value);
    int event(Map<String,Object> value);
    List<Map<String,Object>> events(Long allocationId);
}
