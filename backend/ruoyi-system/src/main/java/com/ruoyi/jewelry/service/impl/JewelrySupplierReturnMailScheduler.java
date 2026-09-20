package com.ruoyi.jewelry.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ruoyi.jewelry.mapper.JewelryErpMapper;

/** Sends the same finished-product supplier-return warnings shown in ERP overview. */
@Component
@EnableScheduling
public class JewelrySupplierReturnMailScheduler
{
    private static final Logger LOG = LoggerFactory.getLogger(JewelrySupplierReturnMailScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final JewelryErpMapper mapper;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;
    private final String recipients;
    private final String authCode;

    public JewelrySupplierReturnMailScheduler(JewelryErpMapper mapper,
        ObjectProvider<JavaMailSender> mailSender,
        @Value("${jewelry.alert.mail.enabled:false}") boolean enabled,
        @Value("${spring.mail.username:}") String from,
        @Value("${jewelry.alert.mail.to:}") String recipients,
        @Value("${spring.mail.password:}") String authCode)
    {
        this.mapper = mapper;
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.recipients = recipients;
        this.authCode = authCode;
    }

    @Scheduled(cron = "${jewelry.alert.mail.cron:0 0 9 * * *}", zone = "Asia/Shanghai")
    public void sendDaily()
    {
        if (!enabled)
        {
            return;
        }
        if (isBlank(from) || isBlank(recipients) || isBlank(authCode))
        {
            LOG.warn("珠宝退供预警邮件未发送：发件地址、收件地址或 SMTP 授权码未配置");
            return;
        }

        final String[] to;
        try
        {
            InternetAddress sender = new InternetAddress(from, true);
            sender.validate();
            InternetAddress[] parsed = InternetAddress.parse(recipients.replace(';', ','), true);
            if (parsed.length == 0)
            {
                throw new AddressException("empty recipient list");
            }
            to = new String[parsed.length];
            for (int i = 0; i < parsed.length; i++)
            {
                parsed[i].validate();
                to[i] = parsed[i].getAddress();
            }
        }
        catch (AddressException ex)
        {
            LOG.warn("珠宝退供预警邮件未发送：邮箱地址格式无效");
            return;
        }

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null)
        {
            LOG.warn("珠宝退供预警邮件未发送：SMTP 发件服务未启用");
            return;
        }

        if (recipients.length() > 2000)
        {
            LOG.warn("珠宝退供预警邮件未发送：收件地址列表过长");
            return;
        }

        LocalDate alertDate = LocalDate.now(BUSINESS_ZONE);
        Map<String, Object> query = new HashMap<>();
        query.put("warningOnly", true);
        query.put("warningType", "supplierReturn");
        List<Map<String, Object>> warnings;
        try
        {
            warnings = mapper.selectStockList(query);
            if (warnings == null || warnings.isEmpty())
            {
                return;
            }
            // The primary key is the calendar day: concurrent app instances and restarts cannot resend.
            if (mapper.claimSupplierReturnMail(alertDate, String.join(",", to), warnings.size()) != 1)
            {
                return;
            }
        }
        catch (Exception ex)
        {
            LOG.error("珠宝退供预警邮件查询或登记失败", ex);
            return;
        }

        try
        {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("[珠宝ERP] 成品商品供应商退货期限预警（" + alertDate + "）");
            message.setText(buildBody(alertDate, warnings));
            sender.send(message);
        }
        catch (Exception ex)
        {
            String error = ex.getClass().getSimpleName();
            try
            {
                mapper.markSupplierReturnMailFailed(alertDate, error);
            }
            catch (Exception recordEx)
            {
                LOG.error("珠宝退供预警邮件失败状态记录失败：日期={}", alertDate, recordEx);
            }
            // Do not log the mail exception: provider messages may contain account details.
            LOG.error("珠宝退供预警邮件发送失败：日期={}，错误类型={}", alertDate, error);
            return;
        }
        try
        {
            mapper.markSupplierReturnMailSent(alertDate);
            LOG.info("珠宝退供预警邮件已发送：日期={}，商品数={}", alertDate, warnings.size());
        }
        catch (Exception ex)
        {
            // SMTP already accepted the message. Keep the daily claim to prevent a duplicate.
            LOG.error("珠宝退供预警邮件已发送，但状态更新失败：日期={}", alertDate, ex);
        }
    }

    static String buildBody(LocalDate alertDate, List<Map<String, Object>> warnings)
    {
        StringBuilder body = new StringBuilder();
        body.append(alertDate).append(" 珠宝ERP成品商品退供预警\n")
            .append("范围：仍有在库数量、距约定退供日期不足7天（含到期和超期）的成品商品。\n")
            .append("共 ").append(warnings.size()).append(" 种。请登录库存台账核对批次与退货安排。\n\n");
        for (Map<String, Object> row : warnings)
        {
            body.append("SKU: ").append(value(row, "sku"))
                .append("；商品: ").append(value(row, "productName"))
                .append("；在库: ").append(value(row, "onHandQty"))
                .append("；截止: ").append(value(row, "supplierReturnDate"))
                .append("；剩余天数: ").append(value(row, "supplierReturnDays"))
                .append("；供应商: ").append(value(row, "supplierReturnSupplierName"))
                .append("；原采购单: ").append(value(row, "supplierReturnDocNo"))
                .append('\n');
        }
        body.append("\n本邮件由系统自动发送，请勿直接回复。");
        return body.toString();
    }

    private static String value(Map<String, Object> row, String key)
    {
        Object value = row.get(key);
        return value == null ? "—" : String.valueOf(value).replace('\n', ' ').replace('\r', ' ');
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
