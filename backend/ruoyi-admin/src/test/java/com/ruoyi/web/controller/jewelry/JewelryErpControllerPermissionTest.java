package com.ruoyi.web.controller.jewelry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.jewelry.service.JewelryDocumentExcelService;
import com.ruoyi.jewelry.service.JewelryInfluencerBindingExcelService;
import com.ruoyi.jewelry.service.IJewelryErpService;
import com.ruoyi.jewelry.domain.JewelryDocument;
import com.ruoyi.jewelry.domain.JewelryProductBatchUpdate;

class JewelryErpControllerPermissionTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyErpEndpointDeclaresAuthorization()
    {
        long endpointCount = 0;
        for (Method method : JewelryErpController.class.getDeclaredMethods())
        {
            if (!isEndpoint(method))
            {
                continue;
            }
            endpointCount++;
            assertNotNull(method.getAnnotation(PreAuthorize.class),
                () -> method.getName() + " 缺少 @PreAuthorize 权限保护");
        }
        assertTrue(endpointCount >= 18, "ERP 控制器端点数量异常，请检查测试是否遗漏新接口");
    }

    @Test
    void criticalWorkflowEndpointsKeepTheirDedicatedPermissions()
    {
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("dashboard", "@ss.hasPermi('jewelry:overview:list')");
        expected.put("staffList", "@ss.hasPermi('jewelry:staff:list')");
        expected.put("addStaff", "@ss.hasPermi('jewelry:staff:add')");
        expected.put("editStaff", "@ss.hasPermi('jewelry:staff:edit')");
        expected.put("productList", "@ss.hasPermi('jewelry:product:list')");
        expected.put("exportProducts", "@ss.hasPermi('jewelry:product:list')");
        expected.put("supplierList", "@ss.hasPermi('jewelry:supplier:list')");
        expected.put("influencerList", "@ss.hasPermi('jewelry:influencer:list')");
        expected.put("influencerOptions", "@ss.hasPermi('jewelry:influencer:list')");
        expected.put("influencerProductPrices", "@ss.hasPermi('jewelry:influencer:list')");
        expected.put("influencerPriceHistory", "@ss.hasPermi('jewelry:influencer:list')");
        expected.put("influencerBundleItems", "@ss.hasPermi('jewelry:influencer:list')");
        expected.put("changeInfluencerPrice", "@ss.hasPermi('jewelry:influencer:price')");
        expected.put("confirmInfluencerBindings", "@ss.hasPermi('jewelry:influencer:price')");
        expected.put("stockList", "@ss.hasPermi('jewelry:stock:list')");
        expected.put("stockSupplierOptions", "@ss.hasPermi('jewelry:stock:list')");
        expected.put("supplierReturnDays", "@ss.hasPermi('jewelry:stock:list')");
        expected.put("updateSupplierReturnDays", "@ss.hasPermi('jewelry:stock:config')");
        expected.put("updateSupplierReturnDate", "@ss.hasPermi('jewelry:stock:config')");
        expected.put("transactions", "@ss.hasPermi('jewelry:stock:list')");
        expected.put("directAdjustCosts", "@ss.hasPermi('jewelry:stock:config')");
        expected.put("documentList", "@ss.hasPermi('jewelry:document:list')");
        expected.put("document", "@ss.hasPermi('jewelry:document:list')");
        expected.put("customerReturnSource", "@ss.hasPermi('jewelry:document:list')");
        expected.put("assessDocumentRisk", "@ss.hasAnyPermi('jewelry:document:add,jewelry:document:edit')");
        expected.put("deleteDraft", "@ss.hasPermi('jewelry:document:edit')");
        expected.put("submit", "@ss.hasPermi('jewelry:document:submit')");
        expected.put("withdraw", "@ss.hasPermi('jewelry:document:withdraw')");
        expected.put("reverse", "@ss.hasPermi('jewelry:document:reverse')");
        expected.put("approve", "@ss.hasPermi('jewelry:approval:approve')");
        expected.put("reject", "@ss.hasPermi('jewelry:approval:reject')");
        expected.put("calculate", "@ss.hasPermi('jewelry:calculator:list')");

        for (Map.Entry<String, String> entry : expected.entrySet())
        {
            Method method = findMethod(entry.getKey());
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertNotNull(authorization, entry.getKey() + " 缺少权限保护");
            assertEquals(entry.getValue(), authorization.value(),
                entry.getKey() + " 的权限标识发生了非预期变化");
        }
    }

    @Test
    void invalidInfluencerImportNeverWritesAnyBinding()
    {
        IJewelryErpService service = mock(IJewelryErpService.class);
        JewelryInfluencerBindingExcelService excel = mock(JewelryInfluencerBindingExcelService.class);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "influencerExcelService", excel);
        loginAs("jewelry_admin", Collections.singleton("jewelry:influencer:price"));
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("excelRow", 2);
        row.put("errors", Collections.singletonList("商品类型不正确"));
        java.util.List<Map<String, Object>> rows = Collections.singletonList(row);
        when(excel.validateRows(9L, rows)).thenReturn(rows);

        assertTrue(controller.confirmInfluencerBindings(9L, rows).isSuccess());
        verify(service, never()).saveInfluencerBindings(any(), any(), any(), any());
    }

    @Test
    void makerAndAdministratorCanMaintainInfluencerBindingsButReviewerCannot()
    {
        IJewelryErpService service = mock(IJewelryErpService.class);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        java.util.List<Map<String, Object>> bindings = Collections.singletonList(
            Collections.<String, Object>singletonMap("productId", 88L));

        loginAs("jewelry_maker", Collections.singleton("jewelry:influencer:price"));
        assertTrue(controller.saveInfluencerBindings(9L, bindings).isSuccess());
        verify(service).saveInfluencerBindings(9L, bindings, 20L, "jewelry_maker");

        loginAs("jewelry_reviewer", Collections.<String>emptySet());
        assertFalse(controller.saveInfluencerBindings(9L, bindings).isSuccess());

        loginAs("jewelry_admin", Collections.singleton("jewelry:influencer:price"));
        assertTrue(controller.saveInfluencerBindings(9L, bindings).isSuccess());
        verify(service).saveInfluencerBindings(9L, bindings, 20L, "jewelry_admin");
    }

    @Test
    void creatingProductThroughBindingRequiresProductAddPermission()
    {
        IJewelryErpService service = mock(IJewelryErpService.class);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        loginAs("jewelry_admin", Collections.singleton("jewelry:influencer:price"));
        Map<String, Object> newProduct = new HashMap<String, Object>();
        newProduct.put("sku", "NEW-1");
        newProduct.put("productName", "新成品");

        assertFalse(controller.saveInfluencerBindings(9L, Collections.singletonList(newProduct)).isSuccess());
        newProduct.put("productId", "");
        assertFalse(controller.saveInfluencerBindings(9L, Collections.singletonList(newProduct)).isSuccess());
        verify(service, never()).saveInfluencerBindings(any(), any(), any(), any());
    }

    @Test
    void creatingSupplierThroughBindingRequiresSupplierAddPermission()
    {
        IJewelryErpService service = mock(IJewelryErpService.class);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        loginAs("jewelry_admin", Collections.singleton("jewelry:influencer:price"));
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("productId", 1L);
        Map<String, Object> supplier = new HashMap<>();
        supplier.put("supplierCode", "NEW-SUP");
        supplier.put("supplierName", "新供应商");
        row.put("newSupplier", supplier);

        assertFalse(controller.saveInfluencerBindings(9L, Collections.singletonList(row)).isSuccess());
        verify(service, never()).saveInfluencerBindings(any(), any(), any(), any());
    }

    @Test
    void reviewerDocumentListHidesDraftsButMakerAndAdministratorKeepTheirViews()
    {
        IJewelryErpService service = mock(IJewelryErpService.class);
        when(service.listDocuments(any())).thenReturn(Collections.emptyList());
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try
        {
            loginAs("jewelry_reviewer", Collections.singleton("jewelry:document:list"));
            JewelryDocument reviewerQuery = new JewelryDocument();
            controller.documentList(reviewerQuery);
            assertTrue(reviewerQuery.isHideDrafts());
            assertTrue(reviewerQuery.getCreatorUserId() == null);

            loginAs("jewelry_maker", Collections.singleton("jewelry:document:list"));
            JewelryDocument makerQuery = new JewelryDocument();
            controller.documentList(makerQuery);
            assertFalse(makerQuery.isHideDrafts());
            assertEquals(20L, makerQuery.getCreatorUserId());

            loginAs("jewelry_admin", Collections.singleton("jewelry:document:list"));
            JewelryDocument administratorQuery = new JewelryDocument();
            controller.documentList(administratorQuery);
            assertFalse(administratorQuery.isHideDrafts());
            assertTrue(administratorQuery.getCreatorUserId() == null);
        }
        finally
        {
            PageUtils.clearPage();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void writeEndpointsUseSeparateCreateEditOrApprovalPermissions()
    {
        assertEquals("@ss.hasAnyPermi('jewelry:product:add,jewelry:product:edit,jewelry:product:basic-edit')",
            authorization("saveProduct"));
        assertEquals("@ss.hasAnyPermi('jewelry:supplier:add,jewelry:supplier:edit')",
            authorization("saveSupplier"));
        assertEquals("@ss.hasAnyPermi('jewelry:influencer:add,jewelry:influencer:edit')",
            authorization("saveInfluencer"));
        assertEquals("@ss.hasAnyPermi('jewelry:document:add,jewelry:document:edit')",
            authorization("saveDocument"));
    }

    @Test
    void makerWithProductAddPermissionCanCreateEverySupportedProductType()
    {
        IJewelryErpService service = mock(IJewelryErpService.class);
        when(service.saveProduct(any())).thenReturn(1);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        loginAsMakerWithProductAdd();

        for (String productType : Arrays.asList("FINISHED", "PART", "ACCESSORY", "WELFARE", "SAMPLE", "GIFT"))
        {
            Map<String, Object> product = new HashMap<String, Object>();
            product.put("sku", productType + "-001");
            product.put("productName", productType);
            product.put("productType", productType);
            assertTrue(controller.saveProduct(product).isSuccess());
        }
        verify(service, times(6)).saveProduct(any());
    }

    @Test
    void makerProductAddPermissionEnablesExcelNewSkuPreview() throws Exception
    {
        JewelryDocumentExcelService excelService = mock(JewelryDocumentExcelService.class);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        when(file.getOriginalFilename()).thenReturn("purchase.xlsx");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
        when(excelService.preview(eq("PURCHASE_IN"), any(InputStream.class), eq(true)))
            .thenReturn(Collections.<String, Object>emptyMap());
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "documentExcelService", excelService);
        loginAsMakerWithProductAdd();

        assertTrue(controller.documentImportPreview("PURCHASE_IN", file).isSuccess());
        verify(excelService).preview(eq("PURCHASE_IN"), any(InputStream.class), eq(true));
    }

    @Test
    void makerBasicEditCanOnlySendNameAndImageFieldsToService()
    {
        IJewelryErpService service = mock(IJewelryErpService.class);
        when(service.updateProductBasic(any())).thenReturn(1);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        loginAs("jewelry_maker", new HashSet<String>(Arrays.asList(
            "jewelry:product:add", "jewelry:product:basic-edit")));

        Map<String, Object> product = new HashMap<String, Object>();
        product.put("productId", 88L);
        product.put("productName", "新名称");
        product.put("imageUrl", "/profile/new.jpg");
        product.put("imageUrls", "/profile/new.jpg");
        product.put("productType", "WELFARE");
        product.put("status", "1");
        product.put("defaultPackFee", "999");

        assertTrue(controller.saveProduct(product).isSuccess());
        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(service).updateProductBasic(fields.capture());
        assertEquals(88L, fields.getValue().get("productId"));
        assertEquals("新名称", fields.getValue().get("productName"));
        assertFalse(fields.getValue().containsKey("productType"));
        assertFalse(fields.getValue().containsKey("status"));
        assertFalse(fields.getValue().containsKey("defaultPackFee"));
        verify(service, never()).saveProduct(any());
    }

    @Test
    void makerWithoutBasicEditAndReviewerCannotModifyExistingProduct()
    {
        IJewelryErpService service = mock(IJewelryErpService.class);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        Map<String, Object> product = new HashMap<String, Object>();
        product.put("productId", 88L);
        product.put("productName", "新名称");

        loginAsMakerWithProductAdd();
        assertFalse(controller.saveProduct(product).isSuccess());

        loginAs("jewelry_reviewer", Collections.<String>emptySet());
        assertFalse(controller.saveProduct(product).isSuccess());
        verify(service, never()).updateProductBasic(any());
        verify(service, never()).saveProduct(any());
    }

    @Test
    void administratorKeepsFullProductEditPath()
    {
        IJewelryErpService service = mock(IJewelryErpService.class);
        when(service.saveProduct(any())).thenReturn(1);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        loginAs("jewelry_admin", Collections.singleton("jewelry:product:edit"));

        Map<String, Object> product = new HashMap<String, Object>();
        product.put("productId", 88L);
        product.put("sku", "SKU-88");
        product.put("productName", "管理员修改");
        product.put("productType", "ACCESSORY");

        assertTrue(controller.saveProduct(product).isSuccess());
        verify(service).saveProduct(product);
        verify(service, never()).updateProductBasic(any());
    }

    @Test
    void onlyAdministratorCanUseDirectCostAdjustment()
    {
        IJewelryErpService service = mock(IJewelryErpService.class);
        JewelryDocument document = new JewelryDocument();
        document.setDocType("COST_ADJUST");
        when(service.directAdjustCosts(any(), eq(20L), eq("jewelry_admin"), eq("jewelry_admin")))
            .thenReturn(document);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);

        loginAs("jewelry_maker", Collections.singleton("jewelry:stock:config"));
        assertFalse(controller.directAdjustCosts(document).isSuccess());
        verify(service, never()).directAdjustCosts(any(), any(), any(), any());

        loginAs("jewelry_admin", Collections.singleton("jewelry:stock:config"));
        assertTrue(controller.directAdjustCosts(document).isSuccess());
        verify(service).directAdjustCosts(document, 20L, "jewelry_admin", "jewelry_admin");
    }

    @Test
    void batchProductUpdatePreservesMakerReviewerAndAdminPermissions()
    {
        assertEquals("@ss.hasAnyPermi('jewelry:product:edit,jewelry:product:basic-edit')", authorization("batchUpdateProducts"));
        IJewelryErpService service = mock(IJewelryErpService.class);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        JewelryProductBatchUpdate request = new JewelryProductBatchUpdate();
        request.setProductIds(Arrays.asList(1L, 2L));
        request.setChanges(Collections.<String, Object>singletonMap("productName", "新名称"));
        loginAs("jewelry_reviewer", Collections.singleton("jewelry:product:list"));
        assertFalse(controller.batchUpdateProducts(request).isSuccess());
        loginAsMakerWithProductAdd();
        assertFalse(controller.batchUpdateProducts(request).isSuccess());
        verify(service, never()).batchUpdateProducts(any(), any(Boolean.class), any());
        loginAs("jewelry_maker", Collections.singleton("jewelry:product:basic-edit"));
        assertTrue(controller.batchUpdateProducts(request).isSuccess());
        verify(service).batchUpdateProducts(request, false, "jewelry_maker");
        loginAs("jewelry_admin", Collections.singleton("jewelry:product:edit"));
        assertTrue(controller.batchUpdateProducts(request).isSuccess());
        verify(service).batchUpdateProducts(request, true, "jewelry_admin");
    }

    private void loginAsMakerWithProductAdd()
    {
        loginAs("jewelry_maker", Collections.singleton("jewelry:product:add"));
    }

    @Test
    void productFullEditAndDeletePermitMakerAndAdminButNotReviewer()
    {
        assertEquals("@ss.hasPermi('jewelry:product:remove')", authorization("deleteProducts"));
        IJewelryErpService service = mock(IJewelryErpService.class);
        when(service.saveProduct(any())).thenReturn(1);
        JewelryErpController controller = new JewelryErpController();
        ReflectionTestUtils.setField(controller, "service", service);
        JewelryProductBatchUpdate request = new JewelryProductBatchUpdate();
        request.setProductIds(Arrays.asList(1L, 2L));
        request.setChanges(Collections.<String, Object>singletonMap("productType", "SAMPLE"));
        loginAs("jewelry_reviewer", Collections.singleton("jewelry:product:list"));
        assertFalse(controller.deleteProducts(Arrays.asList(1L, 2L)).isSuccess());
        assertFalse(controller.batchUpdateProducts(request).isSuccess());
        verify(service, never()).deleteProducts(any());
        for (String role : Arrays.asList("jewelry_maker", "jewelry_admin"))
        {
            loginAs(role, new HashSet<String>(Arrays.asList("jewelry:product:edit", "jewelry:product:remove")));
            assertTrue(controller.batchUpdateProducts(request).isSuccess());
            verify(service).batchUpdateProducts(request, true, role);
            assertTrue(controller.deleteProducts(Arrays.asList(1L, 2L)).isSuccess());
            Map<String, Object> product = new HashMap<String, Object>();
            product.put("productId", 1L);
            product.put("sku", "SKU-1");
            product.put("productName", "商品");
            assertTrue(controller.saveProduct(product).isSuccess());
        }
        verify(service, org.mockito.Mockito.times(2)).deleteProducts(Arrays.asList(1L, 2L));
        verify(service, org.mockito.Mockito.times(2)).saveProduct(any());
        verify(service, never()).updateProductBasic(any());
    }

    private void loginAs(String roleKey, java.util.Set<String> permissions)
    {
        SysRole role = new SysRole(30L);
        role.setRoleKey(roleKey);
        SysUser user = new SysUser();
        user.setUserId(20L);
        user.setUserName(roleKey);
        user.setRoles(Collections.singletonList(role));
        LoginUser loginUser = new LoginUser(user, new HashSet<String>(permissions));
        loginUser.setUserId(20L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(loginUser, null));
    }

    private boolean isEndpoint(Method method)
    {
        return Arrays.stream(method.getAnnotations()).anyMatch(annotation ->
            annotation.annotationType() == GetMapping.class
                || annotation.annotationType() == PostMapping.class
                || annotation.annotationType() == PutMapping.class
                || annotation.annotationType() == DeleteMapping.class
                || annotation.annotationType() == PatchMapping.class
                || annotation.annotationType() == RequestMapping.class);
    }

    private String authorization(String methodName)
    {
        PreAuthorize annotation = findMethod(methodName).getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " 缺少权限保护");
        return annotation.value();
    }

    private Method findMethod(String name)
    {
        return Arrays.stream(JewelryErpController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("找不到控制器方法：" + name));
    }
}
