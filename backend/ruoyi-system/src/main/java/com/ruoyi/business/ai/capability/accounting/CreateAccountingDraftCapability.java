package com.ruoyi.business.ai.capability.accounting;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessOperatingFact;
import com.ruoyi.business.service.IBusinessAccountingService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class CreateAccountingDraftCapability implements AiConfirmableCapability
{
    private final IBusinessAccountingService service;
    @Autowired public CreateAccountingDraftCapability(IBusinessAccountingService service) { this.service = service; }
    @Override public String code() { return "accounting.fact.draft.create"; }
    @Override public String description()
    { return "为一个项目录入每日收支草稿。先查询经营收支目录取得项目ID和分类ID，再提供日期、金额、币种和说明；老板确认后才写入草稿。"; }
    @Override public String requiredPermission() { return "business:accounting:add"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "projectId", "number", "经营收支目录返回的项目ID");
        AiSchemas.property(schema, "categoryId", "number", "经营收支目录返回的收支分类ID，决定收入或支出口径");
        AiSchemas.property(schema, "categoryName", "string", "可选，经营收支目录返回的分类名称，仅用于确认单展示");
        AiSchemas.property(schema, "bizDate", "string", "业务日期 YYYY-MM-DD");
        AiSchemas.property(schema, "amount", "number", "金额，必须大于等于0");
        AiSchemas.property(schema, "currency", "string", "三位币种代码");
        AiSchemas.property(schema, "description", "string", "收支事项说明");
        return AiSchemas.required(schema, "projectId", "categoryId", "bizDate", "amount", "currency", "description");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        validate(input); return "为项目 " + number(input.get("projectId")) + " 录入 " + text(input.get("bizDate"))
            + " 的“" + categoryLabel(input) + "”草稿 " + amount(input).toPlainString()
            + " " + text(input.get("currency")).toUpperCase() + "，说明：" + text(input.get("description"));
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        validate(input); BusinessOperatingFact fact = new BusinessOperatingFact();
        fact.setProjectId(number(input.get("projectId"))); fact.setCategoryId(number(input.get("categoryId")));
        fact.setBizDate(date(input.get("bizDate"))); fact.setAmount(amount(input));
        fact.setCurrency(text(input.get("currency")).toUpperCase()); fact.setDescription(text(input.get("description")));
        BusinessOperatingFact saved = service.saveFact(fact, invocation.getActor().getUserId(),
            invocation.getActor().getUserName(), invocation.getActor().isAdministrator());
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("factId", saved.getFactId());
        result.put("status", saved.getStatus()); result.put("amount", saved.getAmount());
        result.put("currency", saved.getCurrency()); result.put("description", saved.getDescription()); return result;
    }
    private void validate(Map<String, Object> input)
    {
        if (number(input.get("projectId")) == null || number(input.get("categoryId")) == null || date(input.get("bizDate")) == null
            || amount(input) == null || amount(input).signum() < 0 || text(input.get("description")).isEmpty())
            throw new ServiceException("收支资料不完整或金额不正确");
        if (text(input.get("currency")).length() != 3) throw new ServiceException("币种代码必须是三位字符");
    }
    private String categoryLabel(Map<String, Object> input)
    { String name = text(input.get("categoryName")); return name.isEmpty() ? "分类ID " + number(input.get("categoryId")) : name; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private Long number(Object value)
    { try { return value instanceof Number ? ((Number)value).longValue() : value == null ? null : Long.valueOf(String.valueOf(value)); }
      catch (Exception ex) { return null; } }
    private BigDecimal amount(Map<String, Object> input)
    { try { return input.get("amount") == null ? null : new BigDecimal(String.valueOf(input.get("amount"))); }
      catch (Exception ex) { return null; } }
    private Date date(Object value)
    { try { if (value == null) return null; SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            format.setLenient(false); return format.parse(String.valueOf(value)); }
      catch (Exception ex) { return null; } }
}
