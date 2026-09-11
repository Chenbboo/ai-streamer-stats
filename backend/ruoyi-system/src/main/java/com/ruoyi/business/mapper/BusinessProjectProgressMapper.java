package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.ruoyi.business.domain.BusinessProjectProgressReport;

/** No update/delete operation exists for a submitted report. */
public interface BusinessProjectProgressMapper {
    @Update("update biz_project set progress_weight=#{weight},version=version+1,update_by=#{userName},update_time=current_timestamp "
        + "where project_id=#{projectId} and parent_id=#{parentId} and del_flag='0'")
    int setWeight(@Param("projectId") Long projectId,@Param("parentId") Long parentId,
        @Param("weight") java.math.BigDecimal weight,@Param("userName") String userName);
    @Select("select project_id projectId,parent_id parentId,project_name projectName,main_owner_user_id mainOwnerUserId,"
        + "sponsor_owner_user_id sponsorOwnerUserId,initiator_user_id initiatorUserId,status,del_flag delFlag "
        + "from biz_project where project_id=#{id}")
    com.ruoyi.business.domain.BusinessProject archiveProject(Long id);

    @Select("select task_id taskId,report_id reportId,biz_date bizDate,progress,completion_summary completionSummary,"
        + "evidence_urls evidenceUrls,submitted_user_id submittedUserId,submitted_user_name submittedUserName,version "
        + "from biz_project_task_report where project_id=#{id} order by report_id")
    List<Map<String,Object>> taskReports(Long id);

    String COLUMNS = "report_id reportId,project_id projectId,biz_date bizDate,progress,completion_summary completionSummary,"
        + "evidence_urls evidenceUrls,submitted_user_id submittedUserId,submitted_user_name submittedUserName,version,"
        + "create_time createTime,issues_risks issuesRisks,next_plan nextPlan,sync_tasks syncTasks,sync_routines syncRoutines,"
        + "snapshot_json snapshotJson,parent_project_id parentProjectId,project_name_snapshot projectNameSnapshot";

    @Select("select " + COLUMNS + " from biz_project_progress_report where project_id=#{id} order by report_id desc")
    List<BusinessProjectProgressReport> history(Long id);

    @Select("select " + COLUMNS + " from biz_project_progress_report r where r.project_id=#{projectId} "
        + "and exists(select 1 from biz_project_progress_notification n where n.report_id=r.report_id and n.recipient_user_id=#{userId}) order by r.report_id desc")
    List<BusinessProjectProgressReport> recipientHistory(@Param("projectId") Long projectId,@Param("userId") Long userId);

    @Select("select " + COLUMNS + " from biz_project_progress_report where parent_project_id=#{id} order by report_id desc")
    List<BusinessProjectProgressReport> childHistory(Long id);

    @Insert("insert into biz_project_progress_notification(report_id,recipient_user_id) values(#{reportId},#{userId})")
    int notifyOwner(@Param("reportId") Long reportId, @Param("userId") Long userId);

    @Select("select n.notification_id notificationId,n.read_time readTime,n.report_id reportId,r.project_id projectId,"
        + "r.project_name_snapshot projectName,r.submitted_user_name reporterName,r.progress,r.create_time createTime "
        + "from biz_project_progress_notification n join biz_project_progress_report r on r.report_id=n.report_id "
        + "where n.recipient_user_id=#{userId} order by (n.read_time is null) desc,n.notification_id desc limit 100")
    @Results({
        @Result(column="notificationId",property="notificationId"),
        @Result(column="readTime",property="readTime"),
        @Result(column="reportId",property="reportId"),
        @Result(column="projectId",property="projectId"),
        @Result(column="projectName",property="projectName"),
        @Result(column="reporterName",property="reporterName"),
        @Result(column="progress",property="progress"),
        @Result(column="createTime",property="createTime")
    })
    List<Map<String,Object>> notifications(Long userId);

    @Update("update biz_project_progress_notification set read_time=coalesce(read_time,current_timestamp) "
        + "where notification_id=#{id} and recipient_user_id=#{userId}")
    int readNotification(@Param("id") Long id,@Param("userId") Long userId);

}
