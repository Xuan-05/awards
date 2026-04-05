import { defineStore } from 'pinia'
import { http } from '../api/http'
import type { ApiResponse } from '../types/api'

export type UserInfo = {
  /** 用户ID（sys_user.id） */
  id: number
  /** 姓名 */
  realName: string
  /** 用户类型：STUDENT/TEACHER/ADMIN */
  userType: string
  /** 所属院系ID */
  deptId: number
  /** 学生学号（可空） */
  studentNo?: string | null
  /** 教师工号（可空） */
  teacherNo?: string | null
  /** 班级ID（仅学生；可空） */
  classId?: number | null
  /** 班级名称（仅学生；可空；由后端 /auth/me 计算返回） */
  className?: string | null
  /** 手机号（可空） */
  phone?: string | null
  /** 邮箱（可空） */
  email?: string | null
  /** 角色编码列表（CAPTAIN/TEACHER/DEPT_ADMIN/SCHOOL_ADMIN/SYS_ADMIN） */
  roles: string[]
}

export const useUserStore = defineStore('user', {
  state: () => ({
    /** 当前 token（localStorage: token） */
    token: localStorage.getItem('token') || '',
    /** 当前登录用户信息（来自 /auth/me 或 /auth/login 响应） */
    me: null as UserInfo | null,
    /** 是否已加载过 me（避免每次路由跳转都请求 /auth/me） */
    meLoaded: false,
  }),
  getters: {
    /** 是否已登录：只要本地有 token 就视为 authed（后端仍可能 401） */
    authed: (s) => Boolean(s.token),
    /** 当前角色列表（若 me 未加载则返回空数组） */
    roles: (s) => s.me?.roles || [],
    /** 是否拥有任意一个角色（用于菜单/按钮显示与路由守卫） */
    hasAnyRole:
      (s) =>
      (...roleCodes: string[]) => {
        const set = new Set(s.me?.roles || [])
        return roleCodes.some((r) => set.has(r))
      },
    /** 是否管理员端角色（用于决定默认落地端） */
    isAdmin: (s) => {
      const set = new Set(s.me?.roles || [])
      return set.has('DEPT_ADMIN') || set.has('SCHOOL_ADMIN') || set.has('SYS_ADMIN')
    },
  },
  actions: {
    /** 写入 token，并同步到 localStorage */
    setToken(token: string) {
      this.token = token
      if (token) localStorage.setItem('token', token)
      else localStorage.removeItem('token')
    },
    /** 写入当前用户信息（me），并标记已加载 */
    setMe(me: UserInfo | null) {
      this.me = me
      this.meLoaded = true
    },
    /** 请求后端 /auth/me 获取当前用户信息（用于路由守卫与顶部用户信息展示） */
    async fetchMe() {
      if (!this.token) {
        this.setMe(null)
        return
      }
      const resp = await http.get<ApiResponse<UserInfo>>('/auth/me')
      if (resp.data.code !== 0) throw new Error(resp.data.message || '获取用户信息失败')
      this.setMe(resp.data.data)
    },
    /** 退出登录：清 token + 清 me（后端 logout 为幂等，这里前端先本地清理） */
    async logout() {
      this.setToken('')
      this.setMe(null)
    },
  },
})

