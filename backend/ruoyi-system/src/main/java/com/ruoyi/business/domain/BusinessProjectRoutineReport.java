package com.ruoyi.business.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/** 持续工作周期完成填报。 */
public class BusinessProjectRoutineReport extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long reportId;
    private Long routineId;
    private Long projectId;
    @JsonFormat(pattern="yyyy-MM-dd") private Date bizDate;
    private BigDecimal targetSnapshot;
    private BigDecimal actualValue;
    private String unit;
    private String summary;
    private String issueReason;
    private String evidenceUrls;
    private Long submittedUserId;
    private String submittedUserName;
    private String status;
    private Integer version;

    public Long getReportId(){return reportId;} public void setReportId(Long v){reportId=v;}
    public Long getRoutineId(){return routineId;} public void setRoutineId(Long v){routineId=v;}
    public Long getProjectId(){return projectId;} public void setProjectId(Long v){projectId=v;}
    public Date getBizDate(){return bizDate;} public void setBizDate(Date v){bizDate=v;}
    public BigDecimal getTargetSnapshot(){return targetSnapshot;} public void setTargetSnapshot(BigDecimal v){targetSnapshot=v;}
    public BigDecimal getActualValue(){return actualValue;} public void setActualValue(BigDecimal v){actualValue=v;}
    public String getUnit(){return unit;} public void setUnit(String v){unit=v;}
    public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public String getIssueReason(){return issueReason;} public void setIssueReason(String v){issueReason=v;}
    public String getEvidenceUrls(){return evidenceUrls;} public void setEvidenceUrls(String v){evidenceUrls=v;}
    public Long getSubmittedUserId(){return submittedUserId;} public void setSubmittedUserId(Long v){submittedUserId=v;}
    public String getSubmittedUserName(){return submittedUserName;} public void setSubmittedUserName(String v){submittedUserName=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
}
