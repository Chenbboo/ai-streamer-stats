package com.ruoyi.business.service.impl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class BusinessMemberDayCostScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(BusinessMemberDayCostScheduler.class);
    @Autowired private BusinessMemberDayCostService service;
    @Scheduled(fixedDelay=300000,initialDelay=15000)
    public void refresh(){for(Long id:service.openProjects())try{service.synchronize(id);}catch(Exception ex){LOG.warn("Member-day cost refresh failed for project {}",id);}}
}
