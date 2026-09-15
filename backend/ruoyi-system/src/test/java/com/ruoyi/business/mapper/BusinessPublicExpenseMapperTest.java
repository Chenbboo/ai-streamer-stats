package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class BusinessPublicExpenseMapperTest
{
    @Test void mapperParsesAndMissingMonthlyBillsCannotBypassClosing() throws Exception
    {
        Configuration config=new Configuration();try(InputStream stream=getClass().getResourceAsStream("/mapper/business/BusinessPublicExpenseMapper.xml")){assertNotNull(stream);new XMLMapperBuilder(stream,config,"public-expenses",config.getSqlFragments()).parse();}
        Map<String,Object> args=new HashMap<>();args.put("projectId",1L);args.put("month","2025-02");
        String pending=config.getMappedStatement(BusinessPublicExpenseMapper.class.getName()+".countProjectPending").getBoundSql(args).getSql();
        assertTrue(pending.contains("biz_public_expense_policy"));assertTrue(pending.contains("not exists(select 1 from biz_public_expense_month"));assertTrue(pending.contains("p.base_currency=policy.currency"));
        String read=config.getMappedStatement(BusinessPublicExpenseMapper.class.getName()+".readProjectCosts").getBoundSql(args).getSql();assertTrue(read.contains("biz_public_expense_adjustment"));assertTrue(read.contains("hasPublishedBill"));
        String transfer=config.getMappedStatement(BusinessPublicExpenseMapper.class.getName()+".countProjectUnsubmitted").getBoundSql(args).getSql();assertTrue(transfer.contains("o.status!='SUBMITTED'"));assertTrue(transfer.contains("o.owner_user_id=p.main_owner_user_id"));
        for(java.lang.reflect.Method method:BusinessPublicExpenseMapper.class.getMethods())assertTrue(config.hasStatement(BusinessPublicExpenseMapper.class.getName()+"."+method.getName()),method.getName());
    }
}
