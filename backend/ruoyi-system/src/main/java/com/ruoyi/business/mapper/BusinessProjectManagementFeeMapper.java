package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface BusinessProjectManagementFeeMapper
{
    Map<String,Object> selectFee(Long projectId);
    Map<String,Object> selectFeeForUpdate(Long projectId);
    Map<String,Object> selectLifetimeBasis(Long projectId);
    int saveConfiguration(Map<String,Object> input);
    int settle(Map<String,Object> input);
    List<Map<String,Object>> selectPayments(Long feeId);
    Map<String,Object> selectPaymentByRequest(@Param("projectId") Long projectId,@Param("requestKey") String requestKey);
    int countPaymentReference(@Param("feeId") Long feeId,@Param("referenceNo") String referenceNo);
    int insertPayment(Map<String,Object> input);
    int insertEvent(Map<String,Object> input);
}
