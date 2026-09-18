package com.ruoyi.business.domain;

import java.util.Date;
import java.math.BigDecimal;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

public class BusinessProjectMember extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long memberId;
    private Long projectId;
    private Long userId;
    private String userNameSnapshot;
    private String accountName;
    private String memberRole;
    private String status;
    /** 添加成员时设置的项目投入比例，保存至人员成本分摊记录。 */
    private BigDecimal allocationPercent;
    private Map<String,Object> allocationPlan;
    private String allocationOutcome;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date roleEffectiveDate;
    public Date getRoleEffectiveDate(){return roleEffectiveDate;}
    public void setRoleEffectiveDate(Date value){roleEffectiveDate=value;}
    @JsonFormat(pattern = "yyyy-MM-dd") private Date joinedDate;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date leftDate;

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserNameSnapshot() { return userNameSnapshot; }
    public void setUserNameSnapshot(String userNameSnapshot) { this.userNameSnapshot = userNameSnapshot; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getMemberRole() { return memberRole; }
    public void setMemberRole(String memberRole) { this.memberRole = memberRole; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAllocationPercent() { return allocationPercent; }
    public void setAllocationPercent(BigDecimal allocationPercent) { this.allocationPercent = allocationPercent; }
    public Map<String,Object> getAllocationPlan() { return allocationPlan; }
    public void setAllocationPlan(Map<String,Object> allocationPlan) { this.allocationPlan = allocationPlan; }
    public String getAllocationOutcome() { return allocationOutcome; }
    public void setAllocationOutcome(String allocationOutcome) { this.allocationOutcome = allocationOutcome; }
    public Date getJoinedDate() { return joinedDate; }
    public void setJoinedDate(Date joinedDate) { this.joinedDate = joinedDate; }
    public Date getLeftDate() { return leftDate; }
    public void setLeftDate(Date leftDate) { this.leftDate = leftDate; }
}
