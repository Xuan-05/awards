package com.university.awards.message.service.impl;

import com.university.awards.message.entity.SysMessage;
import com.university.awards.message.service.MessageWriteService;
import com.university.awards.message.service.SysMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消息写入服务实现。
 */
@Service
@RequiredArgsConstructor
public class MessageWriteServiceImpl implements MessageWriteService {

    private final SysMessageService messageService;

    @Override
    public void write(Long receiverUserId, String msgType, String title, String content, String bizType, Long bizId) {
        // 接收人为空：忽略（调用方无需额外判空）
        if (receiverUserId == null) return;

        SysMessage m = new SysMessage();
        m.setReceiverUserId(receiverUserId);
        m.setMsgType(msgType);
        m.setTitle(title);
        m.setContent(content);
        m.setBizType(bizType);
        m.setBizId(bizId);
        // 新消息默认未读
        m.setReadFlag(0);
        // 统一由服务端写入创建时间
        m.setCreatedAt(LocalDateTime.now());
        messageService.save(m);
    }
}

