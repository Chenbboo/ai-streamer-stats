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
            com.ruoyi.business.CompanyAccessTestSupport.grant(connection,101L,8L);
            com.ruoyi.business.CompanyAccessTestSupport.grant(connection,102L,8L);
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
            sql.execute("update biz_project set company_dept_id=case when sponsor_owner_user_id=8 then 101 else 103 end");
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

    @Test void projectTypeFilterMatchesRootsAndChildren() throws Exception
    {
        try (SqlSession session = factory.openSession(); Statement sql = session.getConnection().createStatement())
        {
            sql.execute("update biz_project set project_type='GENERAL'");
            sql.execute("update biz_project set project_type='ECOMMERCE' where project_id=10");
            BusinessProjectMapper mapper = session.getMapper(BusinessProjectMapper.class);
            Map<String, Object> query = query(1L, true, false, "");
            query.put("projectType", "ECOMMERCE");
            List<BusinessProject> roots = mapper.selectProjectRoots(query);
            assertEquals(1, roots.size()); assertEquals(1L, roots.get(0).getProjectId());
            assertEquals(10L, roots.get(0).getMatchedChildId());
            query.put("parentId", 1L);
            List<BusinessProject> children = mapper.selectProjectList(query);
            assertEquals(1, children.size()); assertEquals(10L, children.get(0).getProjectId());
            query.put("projectType", "JEWELRY");
            assertTrue(mapper.selectProjectRoots(query).isEmpty());
        }
    }

    @Test void companyFilterPreservesPaginationChildMatchingAndAccessScope() throws Exception
    {
        try (SqlSession session = factory.openSession(); Statement sql = session.getConnection().createStatement())
        {
            sql.execute("update biz_project set company_dept_id=101 where project_id in (1,10,13)");
            sql.execute("update biz_project set company_dept_id=102 where project_id in (2)");
            BusinessProjectMapper mapper = session.getMapper(BusinessProjectMapper.class);
            Map<String, Object> query = query(8L, false, true, "");
            query.put("companyDeptId", "102");
            PageHelper.startPage(1, 1);
            List<BusinessProject> roots = mapper.selectProjectRoots(query);
            assertEquals(1, ((Page<?>) roots).getTotal());
            assertEquals(2L, roots.get(0).getProjectId());
            query.put("keyword", "Needle");
            assertTrue(mapper.selectProjectRoots(query).isEmpty());
            query.put("companyDeptId", "101");
            roots = mapper.selectProjectRoots(query);
            assertEquals(1, roots.size());
            assertEquals(1L, roots.get(0).getProjectId());
            assertEquals(10L, roots.get(0).getMatchedChildId());
            query.put("parentId", 1L);
            List<BusinessProject> children = mapper.selectProjectList(query);
            assertEquals(1, children.size());
            assertEquals(10L, children.get(0).getProjectId());
            query.put("companyDeptId", "");
            query.put("keyword", "");
            assertEquals(2, mapper.selectProjectRoots(query).size());
            query.put("companyDeptId", "999");
            assertTrue(mapper.selectProjectRoots(query).isEmpty());
        }
        finally { PageHelper.clearPage(); }
    }

    @Test void ownerDepartmentFilterIncludesDescendantsAndCombinesWithCompanyWithoutLeakingProjects() throws Exception
    {
        try (SqlSession session = factory.openSession(); Statement sql = session.getConnection().createStatement())
        {
            sql.execute("alter table sys_user add dept_id bigint");
            sql.execute("alter table sys_dept add ancestors varchar(100)");
            sql.execute("insert into sys_dept(dept_id,dept_name,ancestors) values(201,'Operations','0,100,101'),(202,'Team','0,100,101,201'),(1201,'Other','0,100,102')");
            sql.execute("insert into sys_user(user_id,dept_id) values(9,1201),(10,202),(11,201)");
            sql.execute("update biz_project set company_dept_id=101 where project_id in (1,10,13)");
            sql.execute("update biz_project set company_dept_id=102 where project_id in (2)");
            BusinessProjectMapper mapper = session.getMapper(BusinessProjectMapper.class);
            Map<String, Object> query = query(8L, false, true, "");
            query.put("mainOwnerDeptId", "201");
            List<BusinessProject> roots = mapper.selectProjectRoots(query);
            assertEquals(1, roots.size());
            assertEquals(1L, roots.get(0).getProjectId());
            assertEquals(10L, roots.get(0).getMatchedChildId());
            query.put("companyDeptId", "102");
            assertTrue(mapper.selectProjectRoots(query).isEmpty());
            query.put("companyDeptId", "101");
            query.put("parentId", 1L);
            List<BusinessProject> children = mapper.selectProjectList(query);
            assertEquals(1, children.size());
            assertEquals(10L, children.get(0).getProjectId());
            query.put("mainOwnerDeptId", "202");
            assertEquals(1, mapper.selectProjectRoots(query).size());
            query.put("mainOwnerDeptId", "999");
            assertTrue(mapper.selectProjectRoots(query).isEmpty());
            query.put("mainOwnerDeptId", "");
            query.put("companyDeptId", "");
            assertEquals(2, mapper.selectProjectRoots(query).size());
        }
    }

    @Test void departmentOptionsIncludeNestedDepartmentsUnderActiveCompanies() throws Exception
    {
        try (SqlSession session = factory.openSession(); Statement sql = session.getConnection().createStatement())
        {
            sql.execute("alter table sys_dept add ancestors varchar(100)");
            sql.execute("alter table sys_dept add parent_id bigint");
            sql.execute("alter table sys_dept add if not exists status char(1)");
            sql.execute("alter table sys_dept add order_num int default 0");
            sql.execute("delete from sys_dept where dept_id in(101,102)");
            sql.execute("insert into sys_dept(dept_id,dept_name,parent_id,ancestors,status,del_flag) values"
                + "(101,'Company A',100,'0,100','0','0'),(102,'Company B',100,'0,100','1','0'),"
                + "(201,'Operations',101,'0,100,101','0','0'),(202,'Team',201,'0,100,101,201','0','0'),"
                + "(203,'Inactive',101,'0,100,101','1','0'),(204,'Deleted',101,'0,100,101','0','2'),"
                + "(205,'Inactive company team',102,'0,100,102','0','0')");
            List<Map<String, Object>> options = session.getMapper(BusinessProjectMapper.class).selectProjectDepartmentOptions();
            assertEquals(2, options.size());
            // The H2 fixture normalizes unquoted SQL aliases to lower case.
            assertEquals(201L, ((Number) options.get(0).get("deptid")).longValue());
            assertEquals(202L, ((Number) options.get(1).get("deptid")).longValue());
            assertTrue(options.stream().allMatch(option -> "Company A".equals(option.get("companyname"))));
        }
    }

    @Test void parentOwnerCanSeeAllDirectChildrenButNotUnrelatedOrDeletedChildren() throws Exception {
        try (SqlSession session=factory.openSession();Statement sql=session.getConnection().createStatement()) {
            sql.execute("update biz_project set applicant_user_id=9 where project_id=10");
            BusinessProjectMapper mapper=session.getMapper(BusinessProjectMapper.class);
            for (boolean boss : new boolean[] {false, true}) {
                Map<String,Object> query=query(9L,false,boss,"Needle");
                List<BusinessProject> roots=mapper.selectProjectRoots(query);
                assertEquals(1,roots.size());
                assertEquals(1L,roots.get(0).getProjectId());
                assertEquals(10L,roots.get(0).getMatchedChildId());

                // The current parent owner can also find a child they did not create.
                query.put("keyword","Secret");
                roots=mapper.selectProjectRoots(query);
                assertEquals(1,roots.size());
                assertEquals(11L,roots.get(0).getMatchedChildId());

                query.remove("keyword");query.put("parentId",1L);
                List<BusinessProject> children=mapper.selectProjectList(query);
                Set<Long> childIds=new HashSet<>();
                children.forEach(child -> childIds.add(child.getProjectId()));
                assertEquals(2,children.size());
                assertEquals(new HashSet<>(Arrays.asList(10L,11L)),childIds);

                // Parent ownership does not grant access under another parent.
                query.put("parentId",3L);
                assertTrue(mapper.selectProjectList(query).isEmpty());
            }
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
