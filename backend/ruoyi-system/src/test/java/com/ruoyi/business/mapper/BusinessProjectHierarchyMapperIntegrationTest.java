package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.ruoyi.business.domain.BusinessProject;

public class BusinessProjectHierarchyMapperIntegrationTest
{
    private SqlSessionFactory factory;
    public static int field(String value, String... values) { return Arrays.asList(values).indexOf(value) + 1; }

    @BeforeEach void setup() throws Exception
    {
        BusinessProjectWorkMapperIntegrationTest fixture = new BusinessProjectWorkMapperIntegrationTest();
        fixture.setup(); factory = fixture.factory;
        PageInterceptor pagination = new PageInterceptor();
        Properties properties = new Properties(); properties.setProperty("helperDialect", "mysql");
        pagination.setProperties(properties); factory.getConfiguration().addInterceptor(pagination);
        try (Connection connection = fixture.source.getConnection(); Statement sql = connection.createStatement())
        {
            sql.execute("create alias field for \"com.ruoyi.business.mapper.BusinessProjectHierarchyMapperIntegrationTest.field\"");
            sql.execute("alter table sys_dept add del_flag char(1)");
            sql.execute("create table biz_project_task(project_id bigint,active_status varchar(20),status varchar(20))");
            sql.execute("create table biz_project_risk(project_id bigint,status varchar(20))");
            sql.execute("create table biz_project_progress_report(report_id bigint,project_id bigint,progress int,biz_date date,completion_summary varchar(100),evidence_urls varchar(100),submitted_user_name varchar(100),update_time timestamp,create_time timestamp)");
            sql.execute("update biz_project set project_name='Root alpha',main_owner_user_id=9,sponsor_owner_user_id=8 where project_id=1");
            sql.execute("insert into biz_project(project_id,parent_id,project_name,main_owner_user_id,sponsor_owner_user_id,status,management_mode,close_method,del_flag) values"
                + "(2,null,'Root beta',9,8,'ACTIVE','LIGHT','DIRECT','0'),"
                + "(3,null,'Restricted root',11,99,'ACTIVE','LIGHT','DIRECT','0'),"
                + "(4,null,'Deleted root',9,8,'ACTIVE','LIGHT','DIRECT','2'),"
                + "(10,1,'Needle child',10,8,'ACTIVE','LIGHT','DIRECT','0'),"
                + "(11,1,'Secret child',11,99,'ACTIVE','LIGHT','DIRECT','0'),"
                + "(12,3,'Needle second child',10,99,'ACTIVE','LIGHT','DIRECT','0'),"
                + "(13,1,'Deleted child',10,8,'ACTIVE','LIGHT','DIRECT','2')");
        }
    }

    private Map<String, Object> query(Long userId, boolean admin, boolean boss, String keyword)
    {
        Map<String, Object> query = new HashMap<>(); query.put("userId", userId);
        query.put("viewAll", admin); query.put("boss", boss); query.put("keyword", keyword);
        return query;
    }

    @Test void paginationCountsOnlyRootsAndDoesNotReturnChildRecords()
    {
        try (SqlSession session = factory.openSession())
        {
            PageHelper.startPage(1, 1);
            List<BusinessProject> first = session.getMapper(BusinessProjectMapper.class).selectProjectRoots(query(1L, true, false, ""));
            assertEquals(1, first.size()); assertEquals(3, ((Page<?>) first).getTotal());
            assertNull(first.get(0).getParentId()); assertNull(first.get(0).getMatchedChildId());
            PageHelper.startPage(2, 1);
            List<BusinessProject> second = session.getMapper(BusinessProjectMapper.class).selectProjectRoots(query(1L, true, false, ""));
            assertEquals(3, ((Page<?>) second).getTotal()); assertNotEquals(first.get(0).getProjectId(), second.get(0).getProjectId());
        }
        finally { PageHelper.clearPage(); }
    }

    @Test void childSearchReturnsItsRootAndPositionWithoutLeakingOtherBossChildren()
    {
        try (SqlSession session = factory.openSession())
        {
            BusinessProjectMapper mapper = session.getMapper(BusinessProjectMapper.class);
            Map<String, Object> query = query(8L, false, true, "Needle");
            List<BusinessProject> roots = mapper.selectProjectRoots(query);
            assertEquals(1, roots.size()); assertEquals(1L, roots.get(0).getProjectId());
            assertEquals(10L, roots.get(0).getMatchedChildId()); assertFalse(roots.get(0).isContextOnly());
            query.put("keyword", "Secret"); assertTrue(mapper.selectProjectRoots(query).isEmpty());
            query.put("keyword", "Needle"); query.put("status", "CLOSED"); assertTrue(mapper.selectProjectRoots(query).isEmpty());
        }
    }

    @Test void assigningParentOwnerCanSeeCreatedChildButNotOtherChildren() throws Exception {
        try (SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement()) {
            sql.execute("update biz_project set applicant_user_id=9 where project_id=10");
            BusinessProjectMapper mapper=session.getMapper(BusinessProjectMapper.class);
            Map<String,Object> query=query(9L,false,false,"Needle");
            assertEquals(10L,mapper.selectProjectRoots(query).get(0).getMatchedChildId());
            query.remove("keyword");query.put("parentId",1L);
            assertEquals(1,mapper.selectProjectList(query).size());assertEquals(10L,mapper.selectProjectList(query).get(0).getProjectId());
        }
    }

    @Test void deputyCanSeeOnlyChildrenTheyCreated() throws Exception {
        try (SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement()) {
            sql.execute("insert into biz_project_member(member_id,project_id,user_id,member_role,status) values(999,1,7,'DEPUTY','0')");
            sql.execute("update biz_project set applicant_user_id=7 where project_id=10");
            BusinessProjectMapper mapper=session.getMapper(BusinessProjectMapper.class);
            Map<String,Object> query=query(7L,false,false,"Needle");
            assertEquals(10L,mapper.selectProjectRoots(query).get(0).getMatchedChildId());
            query.remove("keyword");query.put("parentId",1L);
            assertEquals(1,mapper.selectProjectList(query).size());
        }
    }

    @Test void childOnlyMembersGetContextRootsAndScopedChildLists()
    {
        try (SqlSession session = factory.openSession())
        {
            BusinessProjectMapper mapper = session.getMapper(BusinessProjectMapper.class);
            Map<String, Object> query = query(10L, false, false, "Needle");
            List<BusinessProject> roots = mapper.selectProjectRoots(query);
            assertEquals(2, roots.size()); assertTrue(roots.stream().allMatch(BusinessProject::isContextOnly));
            query.put("parentId", 1L); query.remove("keyword");
            List<BusinessProject> children = mapper.selectProjectList(query);
            assertEquals(1, children.size()); assertEquals(10L, children.get(0).getProjectId());
            assertEquals(0, children.get(0).getMemberCount());
        }
    }
}
