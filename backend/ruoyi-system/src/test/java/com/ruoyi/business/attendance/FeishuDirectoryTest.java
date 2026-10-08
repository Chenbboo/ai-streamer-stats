package com.ruoyi.business.attendance;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.ruoyi.common.exception.ServiceException;

class FeishuDirectoryTest {
 private FeishuAttendanceClient client = new FeishuAttendanceClient();
 private MockRestServiceServer server;
 private static final String BASE="https://open.feishu.cn/open-apis";
 private static final String SCOPE="/contact/v3/scopes?user_id_type=user_id&department_id_type=open_department_id&page_size=100";
 private void setup(){
  ReflectionTestUtils.setField(client,"enabled",true);ReflectionTestUtils.setField(client,"appId","app");ReflectionTestUtils.setField(client,"appSecret","secret");ReflectionTestUtils.setField(client,"tenantKey","tenant");
  ReflectionTestUtils.setField(client,"token","cached");ReflectionTestUtils.setField(client,"expiresAt",Long.MAX_VALUE);ReflectionTestUtils.setField(client,"verifiedTenantToken","cached");
  server=MockRestServiceServer.createServer((RestTemplate)ReflectionTestUtils.getField(client,"http"));
 }
 private void expect(String path,String data){server.expect(requestTo(BASE+path)).andRespond(withSuccess("{\"code\":0,\"data\":"+data+"}",MediaType.APPLICATION_JSON));}
 @Test void followsScopeAndDepartmentPaginationDeduplicatesAndMinimizesFields(){
  setup();expect(SCOPE,"{\"has_more\":true,\"page_token\":\"next\",\"user_ids\":[\"u1\"]}");
  expect(SCOPE+"&page_token=next","{\"has_more\":false,\"department_ids\":[\"d1\"]}");
  expect("/contact/v3/departments/d1/children?user_id_type=user_id&department_id_type=open_department_id&fetch_child=true&page_size=50","{\"has_more\":false,\"items\":[{\"open_department_id\":\"d2\"}]}");
  expect("/contact/v3/users/find_by_department?user_id_type=user_id&department_id_type=open_department_id&department_id=d1&page_size=50","{\"has_more\":true,\"page_token\":\"p2\",\"items\":[{\"user_id\":\"u1\",\"name\":\"One\",\"mobile\":\"private\"}]}");
  expect("/contact/v3/users/find_by_department?user_id_type=user_id&department_id_type=open_department_id&department_id=d1&page_size=50&page_token=p2","{\"has_more\":false}");
  expect("/contact/v3/users/find_by_department?user_id_type=user_id&department_id_type=open_department_id&department_id=d2&page_size=50","{\"has_more\":false,\"items\":[{\"user_id\":\"u1\",\"name\":\"One\"},{\"user_id\":\"u2\",\"name\":\"Two\"}]}");
  List<Map<String,Object>> rows=client.directory("tenant");assertEquals(2,rows.size());assertFalse(rows.toString().contains("private"));assertEquals("u1",rows.get(0).get("externalUserId"));server.verify();
 }
 @Test void repeatedCursorFailsRatherThanReturningPartialPeople(){setup();expect(SCOPE,"{\"has_more\":true,\"page_token\":\"same\"}");expect(SCOPE+"&page_token=same","{\"has_more\":true,\"page_token\":\"same\"}");assertThrows(ServiceException.class,()->client.directory("tenant"));server.verify();}
 @Test void cursorIsEncodedOnceEvenWhenItContainsReservedCharacters(){setup();expect(SCOPE,"{\"has_more\":true,\"page_token\":\"x/y+z=\"}");expect(SCOPE+"&page_token=x%2Fy%2Bz%3D","{\"has_more\":false}");assertTrue(client.directory("tenant").isEmpty());server.verify();}
 @Test void scopeFailureDoesNotReturnEmptySuccess(){setup();expect(SCOPE,"{}");assertThrows(ServiceException.class,()->client.directory("tenant"));server.verify();}
 @Test void individualLookupMustReturnRequestedIdentity(){setup();expect(SCOPE,"{\"has_more\":false,\"user_ids\":[\"u1\"]}");expect("/contact/v3/users/u1?user_id_type=user_id","{\"user\":{\"user_id\":\"other\",\"name\":\"One\"}}");assertThrows(ServiceException.class,()->client.directory("tenant"));server.verify();}
}
