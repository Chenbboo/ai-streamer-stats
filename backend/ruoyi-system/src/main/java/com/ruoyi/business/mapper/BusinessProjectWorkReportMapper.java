package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.business.domain.BusinessProjectWorkReport;

public interface BusinessProjectWorkReportMapper
{
    int insert(BusinessProjectWorkReport report);
    List<BusinessProjectWorkReport> selectByProject(@Param("projectId") Long projectId);
    List<BusinessProjectWorkReport> selectLatestBySubmitter(@Param("userId") Long userId);
    BusinessProjectWorkReport selectById(@Param("reportId") Long reportId);
    int review(@Param("reportId") Long reportId, @Param("decision") String decision,
        @Param("comment") String comment, @Param("reviewedUserId") Long reviewedUserId,
        @Param("reviewedUserName") String reviewedUserName);
    int insertReturnNotification(@Param("reportId") Long reportId, @Param("recipientUserId") Long recipientUserId);
    List<Map<String, Object>> selectReturnNotifications(@Param("userId") Long userId);
    int readReturnNotification(@Param("notificationId") Long notificationId, @Param("userId") Long userId);
    int readAllReturnNotifications(@Param("userId") Long userId);
}
