package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 项目持续工作计划。 */
public class BusinessProjectRoutine extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long routineId;
    private Long projectId;
    private String routineName;
    private String frequency;
    /** FIXED/AUTO_TOTAL/DAILY_DYNAMIC/NONE。 */
    private String targetMode;
    private BigDecimal targetValue;
    private String unit;
    private Long assigneeUserId;
    private String assigneeName;
    @JsonFormat(pattern="yyyy-MM-dd") private Date startDate;
    @JsonFormat(pattern="yyyy-MM-dd") private Date endDate;
    private String evidenceRequired;
    private String status;
    private Integer version;
    private BigDecimal todayTarget;
    private Long todayTargetId;
    private Integer todayTargetVersion;
    private String todayRequirement;
    private BigDecimal todayActual;
    private BigDecimal cumulativeActual;
    private String todaySummary;
    private String todayIssueReason;
    private String todayEvidenceUrls;
    private Long todayReportId;
    private Boolean sourceManaged;
    private String sourceDomain;
    private Long sourceRecordId;
    @JsonFormat(pattern="yyyy-MM-dd") private Date sourceBizDate;
    private BigDecimal sourceReportedAmount;
    private String supervisorName;
    private List<BusinessProjectWorkPeriod> executionPeriods;

    public Long getRoutineId(){return routineId;} public void setRoutineId(Long v){routineId=v;}
    public Long getProjectId(){return projectId;} public void setProjectId(Long v){projectId=v;}
    public String getRoutineName(){return routineName;} public void setRoutineName(String v){routineName=v;}
    public String getFrequency(){return frequency;} public void setFrequency(String v){frequency=v;}
    public String getTargetMode(){return targetMode;} public void setTargetMode(String v){targetMode=v;}
    public BigDecimal getTargetValue(){return targetValue;} public void setTargetValue(BigDecimal v){targetValue=v;}
    public String getUnit(){return unit;} public void setUnit(String v){unit=v;}
    public Long getAssigneeUserId(){return assigneeUserId;} public void setAssigneeUserId(Long v){assigneeUserId=v;}
    public String getAssigneeName(){return assigneeName;} public void setAssigneeName(String v){assigneeName=v;}
    public Date getStartDate(){return startDate;} public void setStartDate(Date v){startDate=v;}
    public Date getEndDate(){return endDate;} public void setEndDate(Date v){endDate=v;}
    public String getEvidenceRequired(){return evidenceRequired;} public void setEvidenceRequired(String v){evidenceRequired=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public BigDecimal getTodayTarget(){return todayTarget;} public void setTodayTarget(BigDecimal v){todayTarget=v;}
    public Long getTodayTargetId(){return todayTargetId;} public void setTodayTargetId(Long v){todayTargetId=v;}
    public Integer getTodayTargetVersion(){return todayTargetVersion;} public void setTodayTargetVersion(Integer v){todayTargetVersion=v;}
    public String getTodayRequirement(){return todayRequirement;} public void setTodayRequirement(String v){todayRequirement=v;}
    public BigDecimal getTodayActual(){return todayActual;} public void setTodayActual(BigDecimal v){todayActual=v;}
    public BigDecimal getCumulativeActual(){return cumulativeActual;} public void setCumulativeActual(BigDecimal v){cumulativeActual=v;}
    public String getTodaySummary(){return todaySummary;} public void setTodaySummary(String v){todaySummary=v;}
    public String getTodayIssueReason(){return todayIssueReason;} public void setTodayIssueReason(String v){todayIssueReason=v;}
    public String getTodayEvidenceUrls(){return todayEvidenceUrls;} public void setTodayEvidenceUrls(String v){todayEvidenceUrls=v;}
    public Long getTodayReportId(){return todayReportId;} public void setTodayReportId(Long v){todayReportId=v;}
    public Boolean getSourceManaged(){return sourceManaged;} public void setSourceManaged(Boolean v){sourceManaged=v;}
    public String getSourceDomain(){return sourceDomain;} public void setSourceDomain(String v){sourceDomain=v;}
    public Long getSourceRecordId(){return sourceRecordId;} public void setSourceRecordId(Long v){sourceRecordId=v;}
    public Date getSourceBizDate(){return sourceBizDate;} public void setSourceBizDate(Date v){sourceBizDate=v;}
    public BigDecimal getSourceReportedAmount(){return sourceReportedAmount;} public void setSourceReportedAmount(BigDecimal v){sourceReportedAmount=v;}
    public String getSupervisorName(){return supervisorName;} public void setSupervisorName(String v){supervisorName=v;}
    public List<BusinessProjectWorkPeriod> getExecutionPeriods(){return executionPeriods;} public void setExecutionPeriods(List<BusinessProjectWorkPeriod> v){executionPeriods=v;}
}
