package com.ruoyi.business.support;
import java.util.*;
import java.math.BigDecimal;
/** Distinguishes project allocations from unique personnel days within the authorized selection. */
public final class BusinessPersonnelTotals {
 private BusinessPersonnelTotals(){}
 public static List<Map<String,Object>> summarize(List<Map<String,Object>> rows){
  Map<String,Map<String,Object>> people=new LinkedHashMap<>();
  for(Map<String,Object> row:rows){
   if(!"MEMBER_DAYS_V1".equals(row.get("costPolicyVersion")))continue;
   String key=row.get("userId")+":"+row.get("bizDate");
   Map<String,Object> person=people.get(key);
   if(person==null){person=new LinkedHashMap<>(row);person.put("occurrences",1);person.put("conflict",false);people.put(key,person);}
   else{
    person.put("occurrences",((Integer)person.get("occurrences"))+1);
    Object a=person.get("personnelCost"),b=row.get("personnelCost");
    if(a==null||b==null||new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))!=0||!Objects.equals(person.get("currency"),row.get("currency"))||!Objects.equals(person.get("companyDeptId"),row.get("companyDeptId")))person.put("conflict",true);
   }
  }
  Map<String,Map<String,Object>> groups=new LinkedHashMap<>();
  for(Map<String,Object> person:people.values()){
   String key=person.get("companyDeptId")+":"+person.get("currency");
   Map<String,Object> total=groups.computeIfAbsent(key,k->{Map<String,Object> t=new LinkedHashMap<>();t.put("companyName",person.get("companyName"));t.put("currency",person.get("currency"));t.put("amount",BigDecimal.ZERO);t.put("peopleCount",0);t.put("duplicateCount",0);t.put("issueCount",0);return t;});
   total.put("peopleCount",((Integer)total.get("peopleCount"))+1);total.put("duplicateCount",((Integer)total.get("duplicateCount"))+((Integer)person.get("occurrences"))-1);
   if(Boolean.TRUE.equals(person.get("conflict"))||person.get("personnelCost")==null){total.put("issueCount",((Integer)total.get("issueCount"))+1);}
   else total.put("amount",((BigDecimal)total.get("amount")).add(new BigDecimal(person.get("personnelCost").toString())));
  }
  for(Map<String,Object> group:groups.values())if(((Integer)group.get("issueCount"))>0)group.put("amount",null);
  return new ArrayList<>(groups.values());
 }
}
