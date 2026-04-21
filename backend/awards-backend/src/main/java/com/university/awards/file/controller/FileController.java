package com.university.awards.file.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.file.entity.SysFile;
import com.university.awards.file.service.FileStorageService;
import com.university.awards.file.service.SysFileService;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.rbac.service.RbacService;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.entity.BizAwardRecordFile;
import com.university.awards.record.mapper.BizAwardRecordFileMapper;
import com.university.awards.record.mapper.BizAwardRecordMapper;
import com.university.awards.team.entity.BizTeam;
import com.university.awards.team.mapper.BizTeamMapper;
import com.university.awards.dict.entity.DictAwardLevel;
import com.university.awards.dict.entity.DictCompetition;
import com.university.awards.dict.mapper.DictAwardLevelMapper;
import com.university.awards.dict.mapper.DictCompetitionMapper;
import com.university.awards.reviewer.entity.BizReviewerCompScope;
import com.university.awards.reviewer.mapper.BizReviewerCompScopeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.util.List;
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
@Slf4j
public class FileController {

    private final FileStorageService fileStorageService;
    private final SysFileService sysFileService;
    private final AuthzService authz;
    private final RbacService rbacService;
    private final BizAwardRecordFileMapper awardRecordFileMapper;
    private final BizAwardRecordMapper awardRecordMapper;
    private final BizTeamMapper teamMapper;
    private final DictCompetitionMapper competitionMapper;
    private final DictAwardLevelMapper awardLevelMapper;
    private final BizReviewerCompScopeMapper reviewerCompScopeMapper;

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
        
        // 使用新的权限检查逻辑
        if (!canAccessFile(uid, fileId)) {
            log.warn("File download denied: userId={}, fileId={}", uid, fileId);
            throw new BizException(403, "无权限");
        }

        String fileName = buildDownloadName(meta);
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
        
        // 使用新的权限检查逻辑
        if (!canAccessFile(uid, fileId)) {
            log.warn("File preview denied: userId={}, fileId={}", uid, fileId);
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

    private String buildDownloadName(SysFile meta) {
        String fallback = meta.getFileName() == null ? ("file-" + meta.getId()) : meta.getFileName();
        String ext = "";
        int dot = fallback.lastIndexOf('.');
        if (dot > -1 && dot < fallback.length() - 1) {
            ext = fallback.substring(dot);
        }
        BizAwardRecordFile rel = awardRecordFileMapper.selectOne(
                new LambdaQueryWrapper<BizAwardRecordFile>()
                        .eq(BizAwardRecordFile::getFileId, meta.getId())
                        .eq(BizAwardRecordFile::getDeleted, 0)
                        .orderByDesc(BizAwardRecordFile::getId)
                        .last("limit 1"));
        if (rel == null) return fallback;
        BizAwardRecord record = awardRecordMapper.selectById(rel.getRecordId());
        if (record == null) return fallback;
        DictCompetition competition = competitionMapper.selectById(record.getCompetitionId());
        DictAwardLevel awardLevel = awardLevelMapper.selectById(record.getAwardLevelId());
        BizTeam team = teamMapper.selectById(record.getTeamId());
        if (competition == null || awardLevel == null || team == null) return fallback;
        String baseName = sanitizeFileName(competition.getCompetitionName()) + "_"
                + sanitizeFileName(awardLevel.getLevelName()) + "_"
                + sanitizeFileName(team.getTeamName());
        if (baseName.isBlank()) return fallback;
        return baseName + ext;
    }

    private String sanitizeFileName(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "").trim();
    }

    /**
     * 检查用户是否可以访问文件（包含审核员权限检查）
     */
    private boolean canAccessFile(Long userId, Long fileId) {
        // 1. 管理员可以访问任意文件
        if (hasAnyRole(userId, "SCHOOL_ADMIN", "SYS_ADMIN")) {
            log.info("User {} granted access to file {} as admin", userId, fileId);
            return true;
        }

        // 2. 获取文件元数据
        SysFile file = sysFileService.getById(fileId);
        if (file == null || (file.getDeleted() != null && file.getDeleted() == 1)) {
            log.info("File {} not found or deleted", fileId);
            return false;
        }

        // 3. 上传者可以访问自己的文件
        if (file.getUploaderUserId() != null && file.getUploaderUserId().equals(userId)) {
            log.info("User {} granted access to file {} as uploader", userId, fileId);
            return true;
        }

        // 4. 检查审核员权限
        Set<String> roles = Set.copyOf(rbacService.getRoleCodes(userId));
        log.info("User {} roles: {}", userId, roles);
        
        if (roles.contains("COMP_REVIEWER_L1") || roles.contains("COMP_REVIEWER_L2")) {
            boolean canAccess = canReviewerAccessFile(userId, fileId, roles);
            log.info("Reviewer {} access to file {}: {}", userId, fileId, canAccess);
            return canAccess;
        }

        log.info("User {} has no valid permission for file {}", userId, fileId);
        return false;
    }

    /**
     * 检查审核员是否可以访问文件
     */
    private boolean canReviewerAccessFile(Long reviewerUserId, Long fileId, Set<String> roles) {
        // 查找与该文件关联的获奖记录
        List<BizAwardRecordFile> recordFiles = awardRecordFileMapper.selectList(
                new LambdaQueryWrapper<BizAwardRecordFile>()
                        .eq(BizAwardRecordFile::getFileId, fileId)
                        .eq(BizAwardRecordFile::getDeleted, 0));

        log.info("File {} is linked to {} award records", fileId, recordFiles.size());
        
        if (recordFiles.isEmpty()) {
            log.info("File {} has no linked award records", fileId);
            return false;
        }

        // 检查是否有任何一个关联的记录允许该审核员访问
        for (BizAwardRecordFile recordFile : recordFiles) {
            log.info("Checking reviewer {} access to record {}", reviewerUserId, recordFile.getRecordId());
            if (canReviewerAccessRecord(reviewerUserId, recordFile.getRecordId(), roles)) {
                log.info("Reviewer {} granted access via record {}", reviewerUserId, recordFile.getRecordId());
                return true;
            }
        }

        log.info("Reviewer {} denied access to all linked records for file {}", reviewerUserId, fileId);
        return false;
    }

    /**
     * 检查审核员是否可以访问指定的获奖记录
     */
    private boolean canReviewerAccessRecord(Long reviewerUserId, Long recordId, Set<String> roles) {
        BizAwardRecord record = awardRecordMapper.selectById(recordId);
        if (record == null || (record.getDeleted() != null && record.getDeleted() == 1)) {
            log.info("Record {} not found or deleted", recordId);
            return false;
        }

        log.info("Checking record {}: status={}, l1Auditor={}, l2Reviewer={}, l2Flag={}, competition={}", 
                  recordId, record.getStatus(), record.getL1AuditorUserId(), 
                  record.getL2ReviewerUserId(), record.getL2ReviewFlag(), record.getCompetitionId());

        // L1审核员权限检查
        if (roles.contains("COMP_REVIEWER_L1")) {
            // 直接分配：该审核员是该记录的L1审核员
            if (record.getL1AuditorUserId() != null && record.getL1AuditorUserId().equals(reviewerUserId)) {
                log.info("L1 reviewer {} granted access via direct assignment to record {}", reviewerUserId, recordId);
                return true;
            }

            // 基于范围的访问：记录状态为PENDING_SCHOOL或之后的状态
            if (isRecordInReviewableStatus(record.getStatus())) {
                boolean hasScope = hasValidScope(reviewerUserId, record.getCompetitionId());
                log.info("L1 reviewer {} scope check for competition {}: {}", reviewerUserId, record.getCompetitionId(), hasScope);
                if (hasScope) {
                    return true;
                }
            } else {
                log.info("Record {} status {} is not reviewable for L1", recordId, record.getStatus());
            }
        }

        // L2审核员权限检查
        if (roles.contains("COMP_REVIEWER_L2")) {
            // 直接分配：该审核员是该记录的L2审核员
            if (record.getL2ReviewerUserId() != null && record.getL2ReviewerUserId().equals(reviewerUserId)) {
                log.info("L2 reviewer {} granted access via direct assignment to record {}", reviewerUserId, recordId);
                return true;
            }

            // 基于范围的访问：L2审核员可以访问所有在其竞赛范围内的记录（不管状态和l2_review_flag）
            boolean hasScope = hasValidScope(reviewerUserId, record.getCompetitionId());
            log.info("L2 reviewer {} scope check for competition {}: {}", reviewerUserId, record.getCompetitionId(), hasScope);
            if (hasScope) {
                log.info("L2 reviewer {} granted access via scope to record {}", reviewerUserId, recordId);
                return true;
            }
        }

        log.info("Reviewer {} denied access to record {}", reviewerUserId, recordId);
        return false;
    }

    /**
     * 检查记录状态是否处于可审核状态
     */
    private boolean isRecordInReviewableStatus(String status) {
        if (status == null) return false;
        return status.equals("PENDING_SCHOOL") || 
               status.equals("SCHOOL_REJECTED") || 
               status.equals("APPROVED") || 
               status.equals("ARCHIVED");
    }

    /**
     * 检查审核员是否有有效的竞赛范围
     */
    private boolean hasValidScope(Long reviewerUserId, Long competitionId) {
        BizReviewerCompScope scope = reviewerCompScopeMapper.selectOne(
                new LambdaQueryWrapper<BizReviewerCompScope>()
                        .eq(BizReviewerCompScope::getReviewerUserId, reviewerUserId)
                        .eq(BizReviewerCompScope::getCompetitionId, competitionId)
                        .eq(BizReviewerCompScope::getEnabled, 1)
                        .last("limit 1"));

        if (scope == null) {
            log.info("No enabled scope found for reviewer {} and competition {}", reviewerUserId, competitionId);
            return false;
        }

        // 检查日期有效性
        LocalDateTime now = LocalDateTime.now();
        if (scope.getValidFrom() != null && now.isBefore(scope.getValidFrom())) {
            log.info("Scope for reviewer {} and competition {} not yet valid (validFrom={})", 
                      reviewerUserId, competitionId, scope.getValidFrom());
            return false;
        }
        if (scope.getValidTo() != null && now.isAfter(scope.getValidTo())) {
            log.info("Scope for reviewer {} and competition {} expired (validTo={})", 
                      reviewerUserId, competitionId, scope.getValidTo());
            return false;
        }

        log.info("Valid scope found for reviewer {} and competition {}", reviewerUserId, competitionId);
        return true;
    }
}
