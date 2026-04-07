/**
 * 界面展示用中文标签（仅展示，不改变接口枚举值）。
 */
export function labelTeamStatus(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    ACTIVE: '正常',
    INACTIVE: '禁用',
    ARCHIVED: '已归档',
  }
  return m[s] ?? s
}

export function labelInviteStatus(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    PENDING: '待处理',
    ACCEPTED: '已加入',
    REJECTED: '已拒绝',
    INVITED: '已邀请',
  }
  return m[s] ?? s
}

export function labelRecordStatus(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    DRAFT: '草稿',
    PENDING_SCHOOL: '待校级审核',
    SCHOOL_REJECTED: '校级驳回',
    APPROVED: '审核通过',
    ARCHIVED: '已归档',
  }
  return m[s] ?? s
}

export function labelUserType(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    STUDENT: '学生',
    TEACHER: '教师',
    ADMIN: '管理员',
  }
  return m[s] ?? s
}

export function labelInviteeType(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    MEMBER: '成员',
    TEACHER: '指导教师',
  }
  return m[s] ?? s
}

export function labelRoleCode(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    SYS_ADMIN: '系统管理员',
    SCHOOL_ADMIN: '校级管理员',
    DEPT_ADMIN: '院系管理员',
    TEACHER: '教师',
    STUDENT: '学生',
    CAPTAIN: '队长',
  }
  return m[s] ?? s
}

export function labelAuditNodeType(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = { SCHOOL: '校级' }
  return m[s] ?? s
}

export function labelAuditActionType(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    SUBMIT: '提交',
    APPROVE: '通过',
    REJECT: '驳回',
    RESUBMIT: '重新提交',
  }
  return m[s] ?? s
}

/** 导出任务类型：DETAIL / SUMMARY */
export function labelExportType(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    DETAIL: '明细导出',
    SUMMARY: '汇总导出',
  }
  return m[s] ?? s
}

/** 系统参数 valueType */
export function labelConfigValueType(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    STRING: '字符串',
    INT: '整数',
    BOOL: '布尔',
    JSON: '结构化数据',
  }
  return m[s] ?? s
}

/** 操作日志业务类型（与后端 OperateLogAspect 归类一致） */
export function labelOperateBizType(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    AWARD_RECORD: '获奖记录',
    TEAM: '团队',
    AUDIT: '审核',
    EXPORT: '导出',
    DICT: '字典',
    ADMIN: '管理',
    SYSTEM: '系统',
    MESSAGE: '消息',
    FILE: '文件',
    API: '接口',
  }
  return m[s] ?? s
}

/** HTTP 方法展示（仅中文，便于日志阅读） */
export function labelHttpMethod(s: string | undefined | null): string {
  if (s == null || s === '') return '-'
  const m: Record<string, string> = {
    GET: '查询',
    POST: '提交',
    PUT: '更新',
    DELETE: '删除',
    PATCH: '部分更新',
    HEAD: '头请求',
    OPTIONS: '预检请求',
  }
  return m[s] ?? s
}
