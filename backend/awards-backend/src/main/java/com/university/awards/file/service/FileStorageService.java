package com.university.awards.file.service;

import cn.dev33.satoken.stp.StpUtil;
import com.university.awards.common.BizException;
import com.university.awards.file.entity.SysFile;
import com.university.awards.systemconfig.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件存储服务（本地文件系统）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>校验上传文件的扩展名白名单（png/jpg/jpeg/gif/webp/pdf/zip）。</li>
 *   <li>生成存储路径与文件名，保存到 {@code storage.base-dir} 下的按日期目录。</li>
 *   <li>根据 MIME/扩展名判定 {@code previewType}：image/pdf/download_only。</li>
 *   <li>写入 {@code sys_file} 元数据（含 uploaderUserId）。</li>
 * </ul>
 *
 * <p>注意：ZIP 文件在本项目中仅支持下载，不支持预览。</p>
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final SysFileService sysFileService;
    private final SysConfigService configService;

    @Value("${storage.base-dir:./data/files}")
    private String baseDir;

    /**
     * 保存上传文件并生成元数据。
     *
     * @param file MultipartFile
     * @return SysFile 元数据（已入库）
     * @throws BizException 400 文件为空/类型不支持；500 文件保存失败
     */
    public SysFile save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "文件为空");
        }

        String originalName = file.getOriginalFilename();
        String ext = StringUtils.getFilenameExtension(originalName);
        ext = (ext == null ? "" : ext.toLowerCase(Locale.ROOT));
        validateExt(ext);

        String mime = file.getContentType();
        long size = file.getSize();

        // 系统参数：单文件大小上限（MB），默认 50
        // - 仅限制“单文件”，不限制“总附件数量/总大小”
        // - 配置错误（非数字）会由 SysConfigService.getInt 回落默认值，避免影响上传
        Integer maxMb = configService.getInt("upload_max_single_file_mb", 50);
        if (maxMb != null && maxMb > 0) {
            long maxBytes = maxMb.longValue() * 1024 * 1024;
            if (size > maxBytes) throw new BizException(400, "文件过大，单文件最大 " + maxMb + "MB");
        }

        String previewType = resolvePreviewType(mime, ext);
        String dateDir = LocalDate.now().toString();
        String saveName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        Path dir = Path.of(baseDir, dateDir);
        Path target = dir.resolve(saveName);
        try {
            Files.createDirectories(dir);
            file.transferTo(target);
        } catch (IOException e) {
            throw new BizException(500, "文件保存失败");
        }

        SysFile meta = new SysFile();
        meta.setFileName(originalName == null ? saveName : originalName);
        meta.setFileExt(ext);
        meta.setMimeType(mime);
        meta.setStoragePath(target.toString());
        meta.setPreviewType(previewType);
        meta.setFileSize(size);
        // uploaderUserId：用于下载鉴权、审计、问题追踪（未登录则为空）
        meta.setUploaderUserId(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null);
        meta.setCreatedAt(LocalDateTime.now());
        meta.setDeleted(0);

        sysFileService.save(meta);
        return meta;
    }

    private String resolvePreviewType(String mime, String ext) {
        if ("zip".equals(ext)) return "download_only";
        if (mime != null) {
            String m = mime.toLowerCase(Locale.ROOT);
            if (m.startsWith("image/")) return "image";
            if (m.contains("pdf")) return "pdf";
        }
        if ("pdf".equals(ext)) return "pdf";
        if ("png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext) || "gif".equals(ext) || "webp".equals(ext)) {
            return "image";
        }
        return "download_only";
    }

    private void validateExt(String ext) {
        if (ext == null || ext.isBlank()) {
            throw new BizException(400, "文件类型不支持");
        }
        if (!switch (ext) {
            case "png", "jpg", "jpeg", "gif", "webp", "pdf", "zip" -> true;
            default -> false;
        }) {
            throw new BizException(400, "文件类型不支持");
        }
    }
}

