package com.ruoyi.business.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import com.luciad.imageio.webp.WebPWriteParam;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessProjectMapper;

/**
 * 公司经营模块统一附件上传：保留原件，并为图片生成 WebP 预览与缩略图。
 */
@Service
public class BusinessFileService
{
    private static final Logger log = LoggerFactory.getLogger(BusinessFileService.class);

    public static final int MAX_FILE_SIZE_MB = 20;
    public static final int MAX_FILE_COUNT = 10;
    public static final int MAX_TOTAL_SIZE_MB = 100;
    private static final long MAX_FILE_BYTES = MAX_FILE_SIZE_MB * 1024L * 1024L;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;
    private static final int PREVIEW_LONG_EDGE = 1920;
    private static final int THUMB_LONG_EDGE = 480;
    private static final long SMALL_WEBP_BYTES = 500L * 1024L;
    private static final Semaphore IMAGE_PROCESSORS = new Semaphore(2, true);

    @Autowired
    private BusinessProjectMapper projectMapper;

    private static final String[] ALLOWED_EXTENSIONS = {
        "jpg", "jpeg", "png", "webp", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "txt", "zip", "mp4", "mov"
    };

    public Map<String, Object> upload(MultipartFile file, Long projectId, Long userId, boolean boss, boolean admin) throws Exception
    {
        requireProjectAccess(projectId, userId, boss, admin);
        if (file == null || file.isEmpty()) throw new ServiceException("请选择要上传的文件");
        if (file.getSize() > MAX_FILE_BYTES)
            throw new ServiceException("单个附件不能超过" + MAX_FILE_SIZE_MB + "MB");
        if (StringUtils.defaultString(file.getOriginalFilename()).contains(","))
            throw new ServiceException("文件名不能包含英文逗号");

        String extension = StringUtils.defaultString(FileUploadUtils.getExtension(file)).toLowerCase(Locale.ROOT);
        if (!FileUploadUtils.isAllowedExtension(extension, ALLOWED_EXTENSIONS))
            throw new ServiceException("不支持该文件格式");
        validateFileSignature(file, extension);

        String uploadPath = RuoYiConfig.getUploadPath() + File.separator + "business" + File.separator
            + userId + File.separator + projectId;
        // 文件名保留可读的原始名称并追加全局序号，便于只读列表追溯。
        String originalUrl = normalizeResourceUrl(
            FileUploadUtils.upload(uploadPath, file, ALLOWED_EXTENSIONS, false));
        File originalFile = resourceFile(originalUrl);
        String previewUrl = null;
        String thumbnailUrl = null;
        String processingWarning = null;

        if (isImage(extension))
        {
            BufferedImage source;
            try
            {
                source = readCheckedImage(originalFile);
            }
            catch (Exception e)
            {
                Files.deleteIfExists(originalFile.toPath());
                throw new ServiceException("图片内容无法识别或像素过大");
            }

            boolean acquired = false;
            try
            {
                acquired = IMAGE_PROCESSORS.tryAcquire(30, TimeUnit.SECONDS);
                if (!acquired) throw new ServiceException("图片处理繁忙，请稍后重试");
                previewUrl = optimizedUrl(originalUrl, "preview");
                thumbnailUrl = optimizedUrl(originalUrl, "thumb");
                File previewFile = resourceFile(previewUrl);
                File thumbnailFile = resourceFile(thumbnailUrl);
                if ("webp".equals(extension) && originalFile.length() <= SMALL_WEBP_BYTES)
                    Files.copy(originalFile.toPath(), previewFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                else
                    writeWebp(resize(source, PREVIEW_LONG_EDGE), previewFile, 0.82F);
                writeWebp(resize(source, THUMB_LONG_EDGE), thumbnailFile, 0.78F);
            }
            catch (Exception | LinkageError e)
            {
                previewUrl = null;
                thumbnailUrl = null;
                processingWarning = "原件已保存，WebP预览生成失败";
                log.warn("Generate business attachment preview failed for {}", originalUrl, e);
            }
            finally
            {
                if (acquired) IMAGE_PROCESSORS.release();
                source.flush();
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileName", originalUrl);
        result.put("originalFilename", file.getOriginalFilename());
        result.put("newFileName", FilenameUtils.getName(originalUrl));
        result.put("previewFileName", previewUrl);
        result.put("thumbnailFileName", thumbnailUrl);
        result.put("size", file.getSize());
        result.put("extension", extension);
        result.put("sha256", sha256(originalFile));
        if (processingWarning != null) result.put("warning", processingWarning);
        return result;
    }

    /** 在业务数据落库前再次核验附件数量、总大小及路径，防止绕过浏览器限制。 */
    public void validateReferences(String attachmentUrls)
    {
        if (StringUtils.isBlank(attachmentUrls)) return;
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (String value : attachmentUrls.split(","))
        {
            String url = normalizeResourceUrl(value == null ? "" : value.trim());
            if (!url.isEmpty()) urls.add(url);
        }
        if (urls.size() > MAX_FILE_COUNT)
            throw new ServiceException("附件数量不能超过" + MAX_FILE_COUNT + "个");
        long totalBytes = 0L;
        for (String url : urls)
        {
            if (url.contains(".preview.webp") || url.contains(".thumb.webp"))
                throw new ServiceException("业务记录只能绑定附件原件");
            File file = resourceFile(url);
            if (!file.isFile()) throw new ServiceException("附件不存在或已失效");
            String extension = FilenameUtils.getExtension(file.getName()).toLowerCase(Locale.ROOT);
            if (!FileUploadUtils.isAllowedExtension(extension, ALLOWED_EXTENSIONS))
                throw new ServiceException("附件格式不受支持");
            totalBytes += file.length();
            if (totalBytes > MAX_TOTAL_SIZE_MB * 1024L * 1024L)
                throw new ServiceException("全部附件总大小不能超过" + MAX_TOTAL_SIZE_MB + "MB");
        }
    }

    /** 绑定到业务记录时同时校验项目归属，防止跨项目复用他人附件。 */
    public void validateReferences(String attachmentUrls, Long projectId, Long userId, boolean boss, boolean admin)
    {
        validateReferences(attachmentUrls);
        if (StringUtils.isBlank(attachmentUrls)) return;
        requireProjectAccess(projectId, userId, boss, admin);
        for (String value : attachmentUrls.split(","))
        {
            String url = normalizeResourceUrl(value == null ? "" : value.trim());
            if (url.isEmpty()) continue;
            Long embeddedProjectId = namespacedProjectId(url);
            if (embeddedProjectId != null && !embeddedProjectId.equals(projectId))
                throw new ServiceException("附件不属于当前项目");
            List<Long> boundProjects = projectMapper.selectAttachmentProjectIds(url);
            if (embeddedProjectId == null && boundProjects != null && !boundProjects.isEmpty()
                && !boundProjects.contains(projectId))
                throw new ServiceException("附件已绑定其他项目，不能跨项目复用");
        }
    }

    /** 判断该静态资源是否属于需要登录与项目授权的公司经营附件。 */
    public boolean isProtectedResource(String url)
    {
        url = normalizeResourceUrl(url);
        if (StringUtils.isBlank(url) || !url.startsWith(Constants.RESOURCE_PREFIX + "/")) return false;
        if (namespacedProjectId(url) != null) return true;
        String original = originalResourceUrl(url);
        List<Long> projectIds = projectMapper.selectAttachmentProjectIds(original);
        if (projectIds != null && !projectIds.isEmpty()) return true;
        File file = resourceFile(original);
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0)
        {
            File preview = new File(file.getParentFile(), name.substring(0, dot) + ".preview.webp");
            File thumb = new File(file.getParentFile(), name.substring(0, dot) + ".thumb.webp");
            return preview.isFile() || thumb.isFile();
        }
        return false;
    }

    public boolean canAccessResource(String url, Long userId, boolean boss, boolean admin)
    {
        url = normalizeResourceUrl(url);
        String original = originalResourceUrl(url);
        Long embeddedProjectId = namespacedProjectId(original);
        if (embeddedProjectId != null) return hasProjectAccess(embeddedProjectId, userId, boss, admin);
        List<Long> projectIds = projectMapper.selectAttachmentProjectIds(original);
        if (projectIds == null || projectIds.isEmpty()) return false;
        for (Long projectId : projectIds)
            if (hasProjectAccess(projectId, userId, boss, admin)) return true;
        return false;
    }

    private String originalResourceUrl(String url)
    {
        url = normalizeResourceUrl(url);
        if (!url.endsWith(".preview.webp") && !url.endsWith(".thumb.webp")) return url;
        File optimized = resourceFile(url);
        String suffix = url.endsWith(".preview.webp") ? ".preview.webp" : ".thumb.webp";
        String baseUrl = url.substring(0, url.length() - suffix.length());
        File parent = optimized.getParentFile();
        if (parent != null)
        {
            File[] candidates = parent.listFiles((dir, name) -> name.startsWith(new File(baseUrl).getName() + ".")
                && !name.endsWith(".preview.webp") && !name.endsWith(".thumb.webp"));
            if (candidates != null && candidates.length > 0)
            {
                String prefix = url.substring(0, url.lastIndexOf('/') + 1);
                return prefix + candidates[0].getName();
            }
        }
        return baseUrl;
    }

    private Long namespacedProjectId(String url)
    {
        url = normalizeResourceUrl(url);
        String marker = Constants.RESOURCE_PREFIX + "/upload/business/";
        int start = url.indexOf(marker);
        if (start < 0) return null;
        String[] parts = url.substring(start + marker.length()).split("/");
        if (parts.length < 3) return null;
        try { return Long.valueOf(parts[1]); }
        catch (NumberFormatException e) { return null; }
    }

    private void requireProjectAccess(Long projectId, Long userId, boolean boss, boolean admin)
    {
        if (!hasProjectAccess(projectId, userId, boss, admin))
            throw new ServiceException("项目不存在或无权上传和查看附件");
    }

    private boolean hasProjectAccess(Long projectId, Long userId, boolean boss, boolean admin)
    {
        if (projectId == null || userId == null) return false;
        BusinessProject project = projectMapper.selectProjectById(projectId);
        if (project == null) return false;
        if (admin) return true;
        if (userId.equals(project.getMainOwnerUserId())) return true;
        String role = projectMapper.selectMemberRole(projectId, userId);
        if (role != null) return true;
        return userId.equals(project.getSponsorOwnerUserId()) || userId.equals(project.getInitiatorUserId());
    }

    private void validateFileSignature(MultipartFile file, String extension) throws Exception
    {
        byte[] header = new byte[16];
        int length;
        try (InputStream input = file.getInputStream())
        {
            length = input.read(header);
        }
        boolean valid;
        switch (extension)
        {
            case "jpg": case "jpeg":
                valid = length >= 3 && byteAt(header, 0) == 0xFF && byteAt(header, 1) == 0xD8 && byteAt(header, 2) == 0xFF;
                break;
            case "png":
                valid = length >= 8 && byteAt(header, 0) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
                break;
            case "webp":
                valid = length >= 12 && ascii(header, 0, 4).equals("RIFF") && ascii(header, 8, 4).equals("WEBP");
                break;
            case "pdf":
                valid = length >= 5 && ascii(header, 0, 5).equals("%PDF-");
                break;
            case "doc": case "xls": case "ppt":
                valid = length >= 8 && byteAt(header, 0) == 0xD0 && byteAt(header, 1) == 0xCF
                    && byteAt(header, 2) == 0x11 && byteAt(header, 3) == 0xE0;
                break;
            case "docx": case "xlsx": case "pptx": case "zip":
                valid = isZip(header, length) && validateZipKind(file, extension);
                break;
            case "mp4": case "mov":
                valid = length >= 12 && ascii(header, 4, 4).equals("ftyp");
                break;
            case "txt":
                valid = length >= 0 && !containsNull(header, Math.max(length, 0));
                break;
            default:
                valid = false;
        }
        if (!valid) throw new ServiceException("文件内容与扩展名不一致");
    }

    private boolean validateZipKind(MultipartFile file, String extension) throws Exception
    {
        if ("zip".equals(extension)) return true;
        String requiredPrefix = "docx".equals(extension) ? "word/" : "xlsx".equals(extension) ? "xl/" : "ppt/";
        int inspected = 0;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream()))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && inspected++ < 2000)
            {
                String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (name.startsWith(requiredPrefix)) return true;
            }
        }
        return false;
    }

    private BufferedImage readCheckedImage(File file) throws Exception
    {
        try (ImageInputStream input = ImageIO.createImageInputStream(file))
        {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ServiceException("图片格式无法识别");
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS)
                    throw new ServiceException("图片总像素不能超过2500万");
                BufferedImage image = reader.read(0);
                if (image == null) throw new ServiceException("图片格式无法识别");
                return image;
            }
            finally
            {
                reader.dispose();
            }
        }
    }

    private BufferedImage resize(BufferedImage source, int maxLongEdge)
    {
        double scale = Math.min(1D, (double) maxLongEdge / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        boolean alpha = source.getColorModel().hasAlpha();
        BufferedImage target = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try
        {
            if (!alpha)
            {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        }
        finally
        {
            graphics.dispose();
        }
        return target;
    }

    private void writeWebp(BufferedImage image, File target, float quality) throws Exception
    {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) throw new ServiceException("系统缺少WebP编码器");
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target))
        {
            writer.setOutput(output);
            WebPWriteParam parameter = new WebPWriteParam(writer.getLocale());
            parameter.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameter.setCompressionType("Lossy");
            parameter.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), parameter);
        }
        finally
        {
            writer.dispose();
            image.flush();
        }
    }

    private File resourceFile(String resourceUrl)
    {
        resourceUrl = normalizeResourceUrl(resourceUrl);
        String prefix = Constants.RESOURCE_PREFIX + "/";
        if (!resourceUrl.startsWith(prefix)) throw new ServiceException("附件路径非法");
        String relative = resourceUrl.substring(prefix.length()).replace('/', File.separatorChar);
        File root = new File(RuoYiConfig.getProfile()).getAbsoluteFile();
        File target = new File(root, relative).getAbsoluteFile();
        try
        {
            if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator))
                throw new ServiceException("附件路径非法");
        }
        catch (java.io.IOException e)
        {
            throw new ServiceException("附件路径无法解析");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        return target;
    }

    private String optimizedUrl(String originalUrl, String kind)
    {
        originalUrl = normalizeResourceUrl(originalUrl);
        int slash = originalUrl.lastIndexOf('/');
        int dot = originalUrl.lastIndexOf('.');
        if (dot <= slash) return originalUrl + "." + kind + ".webp";
        return originalUrl.substring(0, dot) + "." + kind + ".webp";
    }

    private String sha256(File file) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream input = new FileInputStream(file))
        {
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return Hex.encodeHexString(digest.digest());
    }

    private boolean isImage(String extension)
    {
        return "jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension) || "webp".equals(extension);
    }

    /** URLs are persisted and compared as web paths, independent of the host operating system. */
    public String normalizeResourceUrl(String url)
    {
        return StringUtils.defaultString(url).replace('\\', '/');
    }

    private boolean isZip(byte[] bytes, int length)
    {
        return length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
            && ((bytes[2] == 3 && bytes[3] == 4) || (bytes[2] == 5 && bytes[3] == 6) || (bytes[2] == 7 && bytes[3] == 8));
    }

    private boolean containsNull(byte[] bytes, int length)
    {
        for (int i = 0; i < length; i++) if (bytes[i] == 0) return true;
        return false;
    }

    private int byteAt(byte[] bytes, int index)
    {
        return bytes[index] & 0xFF;
    }

    private String ascii(byte[] bytes, int offset, int length)
    {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
