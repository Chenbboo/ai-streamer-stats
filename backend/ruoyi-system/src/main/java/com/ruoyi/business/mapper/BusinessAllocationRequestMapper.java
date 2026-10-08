package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface BusinessAllocationRequestMapper {
    Long lockEmployee(Long userId);
    Map<String,Object> selectRequest(Long requestId);
    Map<String,Object> selectPending(Long userId);
    List<Map<String,Object>> selectHistory(Long userId);
    List<Map<String,Object>> selectReviews(Long requestId);
    List<Map<String,Object>> selectOwnerPending(Long ownerUserId);
    int insertRequest(Map<String,Object> request);
    int insertReview(Map<String,Object> review);
    int review(@Param("requestId") Long requestId, @Param("ownerId") Long ownerId,
        @Param("status") String status, @Param("comment") String comment);
    int finish(@Param("requestId") Long requestId, @Param("status") String status, @Param("reason") String reason);
}
