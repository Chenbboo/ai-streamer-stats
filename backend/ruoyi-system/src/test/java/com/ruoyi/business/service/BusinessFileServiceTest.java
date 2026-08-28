package com.ruoyi.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;

class BusinessFileServiceTest
{
    @TempDir Path profile;
    private BusinessFileService service;
    private BusinessProjectMapper projectMapper;

    @BeforeEach
    void setUp()
    {
        new RuoYiConfig().setProfile(profile.toString());
        service = new BusinessFileService();
        projectMapper = mock(BusinessProjectMapper.class);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        BusinessProject project = new BusinessProject();
        project.setProjectId(1L); project.setMainOwnerUserId(2L);
        when(projectMapper.selectProjectById(1L)).thenReturn(project);
    }

    @Test
    void imageUploadKeepsOriginalAndCreatesWebpDerivatives() throws Exception
    {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);

        Map<String, Object> uploaded = service.upload(new MockMultipartFile(
            "file", "proof.png", "image/png", bytes.toByteArray()), 1L, 2L, false, false);

        assertTrue(String.valueOf(uploaded.get("fileName")).endsWith(".png"));
        assertFalse(String.valueOf(uploaded.get("fileName")).contains("\\"));
        assertTrue(String.valueOf(uploaded.get("previewFileName")).endsWith(".preview.webp"));
        assertTrue(String.valueOf(uploaded.get("thumbnailFileName")).endsWith(".thumb.webp"));
        assertTrue(Files.isRegularFile(resource(uploaded.get("fileName"))));
        assertTrue(Files.isRegularFile(resource(uploaded.get("previewFileName"))));
        assertTrue(Files.isRegularFile(resource(uploaded.get("thumbnailFileName"))));
        service.validateReferences(String.valueOf(uploaded.get("fileName")));
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchExtension()
    {
        ServiceException error = assertThrows(ServiceException.class, () -> service.upload(
            new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes()),
            1L, 2L, false, false));
        assertEquals("文件内容与扩展名不一致", error.getMessage());
    }

    @Test
    void normalizesLegacyWindowsAttachmentUrl()
    {
        assertEquals("/profile/upload/business/130/20/proof.jpg",
            service.normalizeResourceUrl("/profile/upload\\business\\130\\20/proof.jpg"));
    }

    private Path resource(Object url)
    {
        String relative = String.valueOf(url).replaceFirst("^/profile/", "");
        return profile.resolve(relative.replace('/', java.io.File.separatorChar));
    }
}
