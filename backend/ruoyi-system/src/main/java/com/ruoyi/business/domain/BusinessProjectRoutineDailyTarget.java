package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 负责人按客户要求下达的持续工作每日目标版本。 */
public class BusinessProjectRoutineDailyTarget extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long dailyTargetId;
    private Long projectId;
    private Long routineId;
    @JsonFormat(pattern="yyyy-MM-dd") private Date bizDate;
    private BigDecimal targetValue;
    private String unit;
    private String customerRequirement;
    private String changeReason;
    private Long assigneeUserId;
    private String assigneeName;
    private Integer targetVersion;
    private String status;

    public Long getDailyTargetId(){return dailyTargetId;} public void setDailyTargetId(Long v){dailyTargetId=v;}
    public Long getProjectId(){return projectId;} public void setProjectId(Long v){projectId=v;}
    public Long getRoutineId(){return routineId;} public void setRoutineId(Long v){routineId=v;}
    public Date getBizDate(){return bizDate;} public void setBizDate(Date v){bizDate=v;}
    public BigDecimal getTargetValue(){return targetValue;} public void setTargetValue(BigDecimal v){targetValue=v;}
    public String getUnit(){return unit;} public void setUnit(String v){unit=v;}
    public String getCustomerRequirement(){return customerRequirement;} public void setCustomerRequirement(String v){customerRequirement=v;}
    public String getChangeReason(){return changeReason;} public void setChangeReason(String v){changeReason=v;}
    public Long getAssigneeUserId(){return assigneeUserId;} public void setAssigneeUserId(Long v){assigneeUserId=v;}
    public String getAssigneeName(){return assigneeName;} public void setAssigneeName(String v){assigneeName=v;}
    public Integer getTargetVersion(){return targetVersion;} public void setTargetVersion(Integer v){targetVersion=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
}
