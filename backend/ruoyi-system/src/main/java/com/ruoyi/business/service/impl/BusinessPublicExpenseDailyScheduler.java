package com.ruoyi.business.service.impl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class BusinessPublicExpenseDailyScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(BusinessPublicExpenseDailyScheduler.class);
    @Autowired private BusinessPublicExpenseDailyService service;
    @Scheduled(fixedDelay=300000,initialDelay=25000)
    public void refresh(){for(Long id:service.bills())try{service.synchronize(id);}catch(Exception ex){LOG.warn("Public expense daily cost refresh failed for bill {}",id,ex);}}
}
