package com.university.awards.message.service;

/**
 * 消息写入服务（内部复用）。
 *
 * <p>为什么需要它：</p>
 * <ul>
 *   <li>团队邀请、审核结果等多个业务点都会“写一条 sys_message”。</li>
 *   <li>若每个业务类都手写一遍 saveMsg，会出现重复字段赋值、默认值不一致等问题。</li>
 *   <li>抽成一个服务后：统一默认 readFlag=0、createdAt=now 的行为，便于后续扩展（比如批量写入、模板化内容等）。</li>
 * </ul>
 */
public interface MessageWriteService {

    /**
     * 写入一条消息（sys_message）。
     *
     * @param receiverUserId 接收人用户ID
     * @param msgType 消息类型（如 INVITATION/AUDIT/NOTICE）
     * @param title 标题
     * @param content 内容（允许较长文本）
     * @param bizType 业务类型（用于前端联动，比如 TEAM_INVITATION / AWARD_RECORD）
     * @param bizId 业务ID（用于前端跳转或补充查询）
     */
    void write(Long receiverUserId, String msgType, String title, String content, String bizType, Long bizId);
}

