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
import com.ruoyi.jewelry.service.JewelryDocumentExcelService;
import com.ruoyi.jewelry.service.IJewelryErpService;

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
        expected.put("stockList", "@ss.hasPermi('jewelry:stock:list')");
        expected.put("transactions", "@ss.hasPermi('jewelry:stock:list')");
        expected.put("documentList", "@ss.hasPermi('jewelry:document:list')");
        expected.put("document", "@ss.hasPermi('jewelry:document:list')");
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
    void writeEndpointsUseSeparateCreateEditOrApprovalPermissions()
    {
        assertEquals("@ss.hasAnyPermi('jewelry:product:add,jewelry:product:edit,jewelry:product:basic-edit')",
            authorization("saveProduct"));
        assertEquals("@ss.hasAnyPermi('jewelry:supplier:add,jewelry:supplier:edit')",
            authorization("saveSupplier"));
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

        for (String productType : Arrays.asList("FINISHED", "PART", "ACCESSORY", "WELFARE"))
        {
            Map<String, Object> product = new HashMap<String, Object>();
            product.put("sku", productType + "-001");
            product.put("productName", productType);
            product.put("productType", productType);
            assertTrue(controller.saveProduct(product).isSuccess());
        }
        verify(service, times(4)).saveProduct(any());
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

    private void loginAsMakerWithProductAdd()
    {
        loginAs("jewelry_maker", Collections.singleton("jewelry:product:add"));
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
