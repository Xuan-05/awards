package com.university.awards.message.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.common.PageResult;
import com.university.awards.message.entity.SysMessage;
import com.university.awards.message.service.SysMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 消息中心接口（学生/教师端）。
 *
 * <p>权限：需要登录；只能操作自己的消息（按 receiver_user_id 做权限校验）。</p>
 *
 * <p>消息联动约定（前端用）：</p>
 * <ul>
 *   <li>{@code msgType}：消息大类（INVITATION/AUDIT/NOTICE）。</li>
 *   <li>{@code bizType}/{@code bizId}：业务联动字段；例如 TEAM_INVITATION + invitationId，用于消息详情里直接处理邀请。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final SysMessageService messageService;

    /**
     * 消息分页列表。
     *
     * <p>只返回当前登录用户的消息。</p>
     *
     * @param readFlag 0 未读 / 1 已读（可选）
     * @param msgType 消息类型（可选）
     * @param keyword 关键字（title/content 模糊）（可选）
     */
    @GetMapping
    public ApiResponse<PageResult<SysMessage>> page(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Integer readFlag,
            @RequestParam(required = false) String msgType,
            @RequestParam(required = false) String keyword
    ) {
        StpUtil.checkLogin();
        long uid = StpUtil.getLoginIdAsLong();
        Page<SysMessage> page = messageService.page(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<SysMessage>()
                        .eq(SysMessage::getReceiverUserId, uid)
                        .eq(readFlag != null, SysMessage::getReadFlag, readFlag)
                        .eq(msgType != null && !msgType.isBlank(), SysMessage::getMsgType, msgType)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(SysMessage::getTitle, keyword)
                                .or()
                                .like(SysMessage::getContent, keyword)
                        )
                        .orderByDesc(SysMessage::getCreatedAt)
                        .orderByDesc(SysMessage::getId)
        );
        return ApiResponse.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /**
     * 未读数量。
     *
     * <p>用于工作台“待办”或消息中心 tab 徽标。</p>
     */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount() {
        StpUtil.checkLogin();
        long uid = StpUtil.getLoginIdAsLong();
        long cnt = messageService.count(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getReceiverUserId, uid)
                .eq(SysMessage::getReadFlag, 0));
        return ApiResponse.ok(Map.of("unreadCount", cnt));
    }

    /**
     * 标记已读。
     *
     * <p>幂等：重复标记已读会直接返回 ok。</p>
     */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        StpUtil.checkLogin();
        long uid = StpUtil.getLoginIdAsLong();
        SysMessage m = messageService.getById(id);
        // 消息不存在：视为成功（前端可直接刷新）
        if (m == null) return ApiResponse.ok(null);
        // 只能操作自己的消息
        if (m.getReceiverUserId() == null || m.getReceiverUserId() != uid) throw new BizException(403, "无权限");
        // 已读：无需重复更新
        if (m.getReadFlag() != null && m.getReadFlag() == 1) return ApiResponse.ok(null);
        SysMessage upd = new SysMessage();
        upd.setId(id);
        upd.setReadFlag(1);
        messageService.updateById(upd);
        return ApiResponse.ok(null);
    }

    /**
     * 全部已读。
     *
     * <p>仅影响当前用户的未读消息。</p>
     */
    @PostMapping("/read-all")
    public ApiResponse<Void> readAll() {
        StpUtil.checkLogin();
        long uid = StpUtil.getLoginIdAsLong();
        // 仅更新“当前用户 + 未读”的消息为已读（不影响其他用户）
        SysMessage upd = new SysMessage();
        upd.setReadFlag(1);
        messageService.update(
                upd,
                new LambdaQueryWrapper<SysMessage>()
                        .eq(SysMessage::getReceiverUserId, uid)
                        .eq(SysMessage::getReadFlag, 0)
        );
        return ApiResponse.ok(null);
    }

    /**
     * 删除消息（物理删除）。
     *
     * <p>说明：仅删除 sys_message 记录，不影响业务表；消息属于通知类数据，允许物理删除。</p>
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        StpUtil.checkLogin();
        long uid = StpUtil.getLoginIdAsLong();
        SysMessage m = messageService.getById(id);
        // 不存在：视为成功
        if (m == null) return ApiResponse.ok(null);
        if (m.getReceiverUserId() == null || m.getReceiverUserId() != uid) throw new BizException(403, "无权限");
        messageService.removeById(id);
        return ApiResponse.ok(null);
    }
}

