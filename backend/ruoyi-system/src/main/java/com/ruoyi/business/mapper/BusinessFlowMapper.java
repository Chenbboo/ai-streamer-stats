package com.ruoyi.business.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
public interface BusinessFlowMapper {
 @Select("select task_id from biz_project_task where project_id=#{projectId} and assignee_user_id=#{userId} and active_status='ACTIVE' and status<>'DONE'") List<Long> assignedTasks(@Param("projectId") Long p,@Param("userId") Long u);
 @Select("select routine_id from biz_project_routine where project_id=#{projectId} and assignee_user_id=#{userId} and status='ACTIVE'") List<Long> assignedRoutines(@Param("projectId") Long p,@Param("userId") Long u);
 @Update("update biz_project_member set status='1',left_date=#{date},update_by=#{actor},update_time=now() where user_id=#{userId} and status='0' and member_role<>'OWNER' and project_id in (select project_id from biz_project where del_flag='0' and status not in ('CLOSED','CANCELED'))") int endMemberships(Map<String,Object> row);
 @Update("update biz_project_staff_allocation set status=case when effective_from>#{date} then 'VOID' else status end,effective_to=case when effective_from<=#{date} then #{date} else effective_to end,version=version+1,update_by=#{actor},update_time=now() where user_id=#{userId} and status='ACTIVE' and (effective_to is null or effective_to>#{date}) and project_id in (select project_id from biz_project where del_flag='0' and status not in ('CLOSED','CANCELED'))") int endAllocations(Map<String,Object> row);
 @Update("update biz_project_work_period set end_date=greatest(start_date,#{date}),status='CLOSED',version=version+1,update_by=#{actor},update_time=now() where assignee_user_id=#{userId} and status='ACTIVE' and project_id in (select project_id from biz_project where del_flag='0' and status not in ('CLOSED','CANCELED'))") int endWorkPeriods(Map<String,Object> row);
 @Insert("insert into biz_project_cost_pause(project_id,effective_from,operator_name,reason,created_at) values(#{projectId},date_add(current_date(),interval 1 day),#{actor},#{reason},now())")
 int pause(@Param("projectId") Long p,@Param("actor") String actor,@Param("reason") String reason);
 @Update("update biz_project_cost_pause set effective_to=greatest(effective_from,current_date()),resumed_by=#{actor},resumed_at=now() where project_id=#{projectId} and effective_to is null")
 int resume(@Param("projectId") Long p,@Param("actor") String actor);
 @Select("select m.project_id projectId,p.project_name projectName,p.main_owner_user_id ownerUserId,m.member_role memberRole,(select count(*) from biz_project_task t where t.project_id=m.project_id and t.assignee_user_id=m.user_id and t.active_status='ACTIVE' and t.status<>'DONE') taskCount,(select count(*) from biz_project_routine r where r.project_id=m.project_id and r.assignee_user_id=m.user_id and r.status='ACTIVE') routineCount from biz_project_member m join biz_project p on p.project_id=m.project_id where m.user_id=#{userId} and m.status='0' and p.del_flag='0' and p.status not in ('CLOSED','CANCELED') order by m.project_id")
 List<Map<String,Object>> responsibilities(Long userId);
 @Select("select m.user_id userId,u.nick_name userName from biz_project_member m join sys_user u on u.user_id=m.user_id left join biz_staff_profile s on s.user_id=u.user_id where m.project_id=#{projectId} and m.status='0' and m.member_role<>'OBSERVER' and u.status='0' and u.del_flag='0' and coalesce(s.employment_status,'ACTIVE')<>'LEFT' and u.user_id<>#{userId}")
 List<Map<String,Object>> recipients(@Param("projectId") Long p,@Param("userId") Long u);
 @Select("select user_id from sys_user where user_id=#{userId} for update") Long lockStaff(Long userId);
 @Select("select * from biz_staff_departure where user_id=#{userId} order by id desc limit 1") Map<String,Object> departure(Long userId);
 @Select("select employment_status from biz_staff_profile where user_id=#{id}") String employmentStatus(Long id);
 @Select("select * from biz_staff_departure where id=#{id}") Map<String,Object> selectDeparture(Long id);
 @Select("select * from biz_staff_departure where id=#{id} for update") Map<String,Object> lockDeparture(Long id);
 @Select("select id from biz_staff_departure where status='SCHEDULED' and effective_date<=current_date()") List<Long> dueDepartures();
 @Insert("insert into biz_staff_departure(user_id,effective_date,reason,handover_json,status,requested_by,requested_user_id,created_at) values(#{userId},#{date},#{reason},#{handover},'SCHEDULED',#{actor},#{actorId},now())")
 @Options(useGeneratedKeys=true,keyProperty="id") int insertDeparture(Map<String,Object> row);
 @Update("update biz_staff_departure set status=#{status},error_message=#{error},completed_at=case when #{status}='COMPLETED' then now() else null end where id=#{id}")
 int departureStatus(@Param("id") Long id,@Param("status") String status,@Param("error") String error);
 @Update("update biz_project_task set assignee_user_id=#{recipient},assignee_name=#{name},version=version+1,update_by=#{actor},update_time=now() where project_id=#{projectId} and assignee_user_id=#{userId} and active_status='ACTIVE' and status<>'DONE'") int transferTasks(Map<String,Object> row);
 @Update("update biz_project_routine set assignee_user_id=#{recipient},assignee_name=#{name},version=version+1,update_by=#{actor},update_time=now() where project_id=#{projectId} and assignee_user_id=#{userId} and status='ACTIVE'") int transferRoutines(Map<String,Object> row);
 @Update("update biz_staff_profile set employment_status='LEFT',update_by=#{actor},update_time=now() where user_id=#{userId}") int markDeparted(@Param("userId") Long id,@Param("actor") String actor);
 @Select("select * from biz_closed_project_adjustment where project_id=#{projectId} order by id desc") List<Map<String,Object>> adjustments(Long projectId);
 @Select("select * from biz_closed_project_adjustment where id=#{id}") Map<String,Object> selectAdjustment(Long id);
 @Select("select * from biz_closed_project_adjustment where id=#{id} for update") Map<String,Object> lockAdjustment(Long id);
 @Select("select * from biz_closed_project_adjustment where project_id=#{projectId} and requested_user_id=#{actorId} and request_id=#{requestId}") Map<String,Object> replayAdjustment(Map<String,Object> row);
 @Insert("insert into biz_closed_project_adjustment(project_id,original_fact_id,business_date,profit_delta,currency,reason,status,request_id,requested_user_id,requested_by,created_at) values(#{projectId},#{factId},#{date},#{delta},#{currency},#{reason},'PENDING',#{requestId},#{actorId},#{actor},now())")
 @Options(useGeneratedKeys=true,keyProperty="id") int insertAdjustment(Map<String,Object> row);
 @Update("update biz_closed_project_adjustment set status=#{status},posting_date=case when #{status}='APPROVED' then current_date() else null end,reviewed_by=#{actor},reviewed_user_id=#{actorId},reviewed_at=now(),review_comment=#{comment} where id=#{id} and status='PENDING'") int reviewAdjustment(Map<String,Object> row);
 @Select("<script>select a.currency,sum(a.profit_delta) amount,count(*) itemCount from biz_closed_project_adjustment a join biz_project p on p.project_id=a.project_id where a.status='APPROVED' <if test='!viewAll'>and p.initiator_user_id=#{userId}</if><if test='projectId!=null'>and p.project_id=#{projectId}</if><if test='companyDeptId!=null'>and p.company_dept_id=#{companyDeptId}</if><if test='dateFrom!=null'>and a.posting_date &gt;= #{dateFrom}</if><if test='dateTo!=null'>and a.posting_date &lt;= #{dateTo}</if> group by a.currency</script>") List<Map<String,Object>> adjustmentTotals(Map<String,Object> scope);
}
