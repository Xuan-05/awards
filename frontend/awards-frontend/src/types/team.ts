/**
 * 团队相关契约类型（对齐后端 TeamController/TeamService VO）。
 *
 * <p>说明：</p>
 * - 本文件的类型用于前端展示与接口对接，不包含任何前端状态字段。\n
 * - “学号/工号”当前使用后端 {@code sys_user.username} 字段（不额外建 studentNo/teacherNo）。\n
 */

/**
 * 用户简要信息（用于团队成员/邀请记录展示）。
 * 对齐后端：TeamService.UserBrief。
 */
export type UserBrief = {
  /** 用户 ID（sys_user.id） */
  id: number
  /** 登录名（sys_user.username），当前也用作学号/工号展示 */
  username: string
  /** 姓名（sys_user.real_name） */
  realName: string
  /** 用户类型：STUDENT/TEACHER/ADMIN */
  userType: string
  /** 院系 ID（sys_user.dept_id） */
  deptId: number
}

/**
 * 团队基础信息。
 * 对齐后端：BizTeam（字段略有裁剪，仅保留前端使用项）。
 */
export type Team = {
  id: number
  /** 团队名称 */
  teamName: string
  /** 队长用户 ID */
  captainUserId: number
  /** 归属院系 ID */
  ownerDeptId: number
  /** 状态：ACTIVE 等 */
  status: string
  /** 备注（可选） */
  remark?: string
}

/**
 * 团队成员（展示 VO）。
 * 对齐后端：TeamService.TeamMemberVO。
 */
export type TeamMember = {
  id: number
  teamId: number
  /** 成员用户 ID */
  userId: number
  /** 成员排序号 */
  memberOrderNo: number
  /** 是否队长：1 是 / 0 否 */
  isCaptain: number
  /** 加入状态：ACCEPTED/PENDING */
  joinStatus: string
  /** 用户简要信息（可能为空：若用户不存在或被清理） */
  user?: UserBrief
}

/**
 * 指导教师（展示 VO）。
 * 对齐后端：TeamService.TeamTeacherVO。
 */
export type TeamTeacher = {
  id: number
  teamId: number
  /** 教师用户 ID */
  teacherUserId: number
  /** 教师排序号 */
  teacherOrderNo: number
  /** 加入状态：ACCEPTED/PENDING */
  joinStatus: string
  user?: UserBrief
}

/**
 * 团队邀请记录（展示 VO）。
 * 对齐后端：TeamService.TeamInvitationVO。
 */
export type TeamInvitation = {
  id: number
  teamId: number
  /** 被邀请用户 ID */
  inviteeUserId: number
  /** 被邀请类型：MEMBER/TEACHER */
  inviteeType: string
  /** 邀请状态：PENDING/ACCEPTED/REJECTED */
  status: string
  /** 邀请人用户 ID（通常为队长） */
  inviterUserId: number
  createdAt?: string
  updatedAt?: string
  /** 被邀请人用户信息（可能为空） */
  invitee?: UserBrief
  /** 邀请人用户信息（可能为空） */
  inviter?: UserBrief
}

