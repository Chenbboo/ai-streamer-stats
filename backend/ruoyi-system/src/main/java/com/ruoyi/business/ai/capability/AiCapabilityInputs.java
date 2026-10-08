package com.ruoyi.business.ai.capability;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/** Strict, locale-independent conversion helpers for model tool arguments. */
public final class AiCapabilityInputs
{
    private AiCapabilityInputs() { }
    public static boolean has(Map<String,Object> input,String key){return input!=null&&input.containsKey(key);}
    public static String text(Object value){return value==null?"":String.valueOf(value).trim();}
    public static Long number(Object value)
    {try{return value instanceof Number?((Number)value).longValue():value==null?null:Long.valueOf(String.valueOf(value));}catch(Exception ex){return null;}}
    public static Integer integer(Object value)
    {try{return value instanceof Number?((Number)value).intValue():value==null?null:Integer.valueOf(String.valueOf(value));}catch(Exception ex){return null;}}
    public static BigDecimal decimal(Object value)
    {try{return value==null?null:new BigDecimal(String.valueOf(value));}catch(Exception ex){return null;}}
    public static Date date(Object value)
    {try{if(value==null||text(value).isEmpty())return null;SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd");f.setLenient(false);return f.parse(text(value));}catch(Exception ex){return null;}}
    public static String upper(Object value){return text(value).toUpperCase();}
}
