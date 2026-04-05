package com.university.awards.file.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.file.entity.SysFile;
import com.university.awards.file.service.FileStorageService;
import com.university.awards.file.service.SysFileService;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.rbac.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 文件上传/下载/预览接口。
 *
 * <p>
 * 文件存储：
 * </p>
 * <ul>
 * <li>本地文件系统存储（非 OSS/云存储）。</li>
 * <li>文件元数据存于 {@code sys_file}，包括原始文件名、存储路径、预览类型等。</li>
 * </ul>
 *
 * <p>
 * 权限与数据范围：
 * </p>
 * <ul>
 * <li>SCHOOL_ADMIN/SYS_ADMIN：可访问任意文件。</li>
 * <li>普通用户：仅可访问自己上传的文件（{@code uploader_user_id}）。</li>
 * </ul>
 *
 * <p>
 * 预览规则：
 * </p>
 * <ul>
 * <li>{@code preview_type=image}：允许 inline 预览（以图片媒体类型返回）。</li>
 * <li>{@code preview_type=pdf}：允许 inline 预览（application/pdf）。</li>
 * <li>{@code preview_type=download_only}：仅允许下载（例如 ZIP）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final SysFileService sysFileService;
    private final AuthzService authz;
    private final RbacService rbacService;

    /**
     * 上传文件，返回文件元数据。
     *
     * <p>
     * <b>权限</b>：需要登录（在 {@link AuthzService#currentUserId()} 或文件服务内校验）。
     * </p>
     *
     * @param file 上传文件
     * @return 文件元数据（含 fileId、previewType、storagePath 等）
     */
    @PostMapping("/upload")
    public ApiResponse<SysFile> upload(@RequestParam("file") MultipartFile file) {
        SysFile meta = fileStorageService.save(file);
        return ApiResponse.ok(meta);
    }

    /**
     * 下载文件（以附件形式返回）。
     *
     * <p>
     * <b>权限</b>：需要登录；数据范围见类注释。
     * </p>
     *
     * @param fileId 文件 ID（{@code sys_file.id}）
     * @param token  可选，URL参数传递的token
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<FileSystemResource> download(
            @PathVariable Long fileId,
            @RequestParam(value = "Authorization", required = false) String token) {
        Long uid = getCurrentUserId(token);
        SysFile meta = sysFileService.getById(fileId);
        if (meta == null || meta.getDeleted() != null && meta.getDeleted() == 1)
            throw new BizException(404, "文件不存在");
        if (!hasAnyRole(uid, "SCHOOL_ADMIN", "SYS_ADMIN")) {
            if (meta.getUploaderUserId() == null || !uid.equals(meta.getUploaderUserId()))
                throw new BizException(403, "无权限");
        }

        String fileName = meta.getFileName() == null ? ("file-" + meta.getId()) : meta.getFileName();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(meta.getStoragePath()));
    }

    /**
     * 预览文件（inline）。
     *
     * <p>
     * <b>权限</b>：需要登录；数据范围见类注释。
     * </p>
     * <p>
     * <b>失败场景</b>：
     * </p>
     * <ul>
     * <li>文件不存在：404。</li>
     * <li>无权限：403。</li>
     * <li>仅支持下载：400（previewType=download_only）。</li>
     * </ul>
     *
     * @param fileId 文件 ID
     * @param token  可选，URL参数传递的token
     */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<FileSystemResource> preview(
            @PathVariable Long fileId,
            @RequestParam(value = "Authorization", required = false) String token) {
        Long uid = getCurrentUserId(token);
        SysFile meta = sysFileService.getById(fileId);
        if (meta == null || meta.getDeleted() != null && meta.getDeleted() == 1)
            throw new BizException(404, "文件不存在");
        if (!hasAnyRole(uid, "SCHOOL_ADMIN", "SYS_ADMIN")) {
            if (meta.getUploaderUserId() == null || !uid.equals(meta.getUploaderUserId()))
                throw new BizException(403, "无权限");
        }
        if ("download_only".equals(meta.getPreviewType()))
            throw new BizException(400, "仅支持下载");

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if ("image".equals(meta.getPreviewType()))
            type = MediaType.IMAGE_JPEG;
        if ("pdf".equals(meta.getPreviewType()))
            type = MediaType.APPLICATION_PDF;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(type)
                .body(new FileSystemResource(meta.getStoragePath()));
    }

    /**
     * 获取当前用户ID，支持从URL参数或header读取token
     */
    private Long getCurrentUserId(String tokenFromQuery) {
        // 尝试从URL参数读取
        if (tokenFromQuery != null && !tokenFromQuery.isBlank()) {
            // 去掉可能的Bearer前缀
            String token = tokenFromQuery;
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            // 通过token获取loginId
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId != null) {
                return Long.parseLong(loginId.toString());
            }
        }
        // 从header读取（Sa-Token自动处理）
        return authz.currentUserId();
    }

    /**
     * 检查用户是否拥有任意一个角色
     */
    private boolean hasAnyRole(Long userId, String... roleCodes) {
        Set<String> roles = Set.copyOf(rbacService.getRoleCodes(userId));
        for (String r : roleCodes) {
            if (roles.contains(r))
                return true;
        }
        return false;
    }
}
