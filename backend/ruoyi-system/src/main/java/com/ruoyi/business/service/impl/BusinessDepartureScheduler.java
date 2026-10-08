package com.ruoyi.business.service.impl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class BusinessDepartureScheduler {
 @Autowired private BusinessFlowService service;
 @Scheduled(fixedDelay=60000,initialDelay=20000)
 public void process(){for(Long id:service.dueDepartures())try{service.completeDeparture(id);}catch(Exception ex){service.recordDepartureFailure(id,ex.getMessage());}}
}
