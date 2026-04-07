<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
import { useUserStore } from '../../stores/user'

type ApiResponse<T> = { code: number; message: string; data: T }

const user = useUserStore()

const contactFormRef = ref<FormInstance>()
const contact = reactive({ phone: '', email: '' })
const savingContact = ref(false)

const contactRules: FormRules = {
  phone: [
    {
      validator: (_r, v, cb) => {
        const s = String(v ?? '').trim()
        if (!s) {
          cb()
          return
        }
        if (!/^1[3-9]\d{9}$/.test(s)) {
          cb(new Error('请输入正确的手机号'))
          return
        }
        cb()
      },
      trigger: 'blur',
    },
  ],
  email: [
    {
      validator: (_r, v, cb) => {
        const s = String(v ?? '').trim()
        if (!s) {
          cb()
          return
        }
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s)) {
          cb(new Error('请输入正确的邮箱'))
          return
        }
        cb()
      },
      trigger: 'blur',
    },
  ],
}

async function saveContact() {
  const f = contactFormRef.value
  if (!f) return
  try {
    await f.validate()
  } catch {
    return
  }
  savingContact.value = true
  try {
    const resp = await http.put<ApiResponse<null>>('/auth/profile', {
      phone: contact.phone.trim(),
      email: contact.email.trim(),
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('联系方式已保存')
    await user.fetchMe()
    contact.phone = user.me?.phone ?? ''
    contact.email = user.me?.email ?? ''
  } finally {
    savingContact.value = false
  }
}

const loading = ref(false)
async function load() {
  loading.value = true
  try {
    await user.fetchMe()
    contact.phone = user.me?.phone ?? ''
    contact.email = user.me?.email ?? ''
  } finally {
    loading.value = false
  }
}

const pwd = reactive({ oldPassword: '', newPassword: '' })
const changing = ref(false)
async function changePassword() {
  changing.value = true
  try {
    const resp = await http.post<ApiResponse<null>>('/auth/change-password', pwd)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    pwd.oldPassword = ''
    pwd.newPassword = ''
    ElMessage.success('修改成功')
  } finally {
    changing.value = false
  }
}

onMounted(load)

// 密码显示切换
const showOldPwd = ref(false)
const showNewPwd = ref(false)

// 获取用户类型标签
function getUserTypeLabel(type?: string): string {
  const map: Record<string, string> = {
    STUDENT: '学生',
    TEACHER: '教师',
    ADMIN: '管理员',
  }
  return map[type || ''] || type || '-'
}

// 获取角色标签
function getRoleLabel(role: string): string {
  const map: Record<string, string> = {
    STUDENT: '学生',
    TEACHER: '教师',
    TEAM_LEADER: '队长',
    SCHOOL_ADMIN: '校级管理员',
    SUPER_ADMIN: '超级管理员',
  }
  return map[role] || role
}
</script>

<template>
  <div class="profile-page" v-loading="loading">
    <!-- 顶部：蓝色通栏 头像+姓名 -->
    <div class="profile-header">
      <div class="avatar">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
          <circle cx="12" cy="7" r="4"/>
        </svg>
      </div>
      <h1 class="profile-name">{{ user.me?.realName || '用户' }}</h1>
    </div>

    <!-- 蓝色栏下面：左右两栏布局 -->
    <div class="profile-body">
      <!-- 左侧：基本信息 -->
      <div class="left-section">
        <div class="profile-card">
          <div class="detail-section">
            <h3 class="section-title">基本信息</h3>
            <div class="detail-grid">
              <div class="detail-item">
                <div class="detail-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                    <circle cx="12" cy="7" r="4"/>
                  </svg>
                </div>
                <div class="detail-content">
                  <span class="detail-label">姓名</span>
                  <span class="detail-value">{{ user.me?.realName || '-' }}</span>
                </div>
              </div>

              <div class="detail-item">
                <div class="detail-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/>
                    <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>
                  </svg>
                </div>
                <div class="detail-content">
                  <span class="detail-label">用户类型</span>
                  <span class="detail-value">{{ getUserTypeLabel(user.me?.userType) }}</span>
                </div>
              </div>

              <div class="detail-item">
                <div class="detail-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                  </svg>
                </div>
                <div class="detail-content">
                  <span class="detail-label">手机号</span>
                  <span class="detail-value">{{ user.me?.phone || '-' }}</span>
                </div>
              </div>

              <div class="detail-item">
                <div class="detail-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                    <polyline points="22,6 12,13 2,6"/>
                  </svg>
                </div>
                <div class="detail-content">
                  <span class="detail-label">邮箱</span>
                  <span class="detail-value">{{ user.me?.email || '-' }}</span>
                </div>
              </div>

              <div class="detail-item">
                <div class="detail-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                    <polyline points="9,22 9,12 15,12 15,22"/>
                  </svg>
                </div>
                <div class="detail-content">
                  <span class="detail-label">院系</span>
                  <span class="detail-value">{{ user.me?.deptId ? `院系 #${user.me.deptId}` : '-' }}</span>
                </div>
              </div>

              <div class="detail-item" v-if="user.me?.userType === 'STUDENT'">
                <div class="detail-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                    <circle cx="9" cy="7" r="4"/>
                    <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                    <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                  </svg>
                </div>
                <div class="detail-content">
                  <span class="detail-label">班级</span>
                  <span class="detail-value">{{ user.me?.className || (user.me?.classId ? `#${user.me.classId}` : '-') }}</span>
                </div>
              </div>

              <div class="detail-item" v-if="user.me?.userType === 'STUDENT'">
                <div class="detail-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                    <line x1="16" y1="2" x2="16" y2="6"/>
                    <line x1="8" y1="2" x2="8" y2="6"/>
                    <line x1="3" y1="10" x2="21" y2="10"/>
                  </svg>
                </div>
                <div class="detail-content">
                  <span class="detail-label">学号</span>
                  <span class="detail-value">{{ user.me?.studentNo || '-' }}</span>
                </div>
              </div>

              <div class="detail-item" v-if="user.me?.userType === 'TEACHER'">
                <div class="detail-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                    <line x1="16" y1="2" x2="16" y2="6"/>
                    <line x1="8" y1="2" x2="8" y2="6"/>
                    <line x1="3" y1="10" x2="21" y2="10"/>
                  </svg>
                </div>
                <div class="detail-content">
                  <span class="detail-label">工号</span>
                  <span class="detail-value">{{ user.me?.teacherNo || '-' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：设置 -->
      <div class="right-section">
        <!-- 联系方式 -->
        <div class="setting-card">
          <div class="card-header">
            <h3 class="card-title">联系方式</h3>
            <p class="card-desc">手机号与邮箱用于通知与联系，所有身份均可维护</p>
          </div>
          <el-form
            ref="contactFormRef"
            :model="contact"
            :rules="contactRules"
            label-position="top"
            class="setting-form"
          >
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="contact.phone" placeholder="选填，11位中国大陆手机号" maxlength="11" clearable />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="contact.email" placeholder="选填" maxlength="128" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingContact" @click="saveContact">保存联系方式</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 修改密码 -->
        <div class="setting-card">
          <div class="card-header">
            <h3 class="card-title">修改密码</h3>
            <p class="card-desc">定期更换密码可以提高账户安全性</p>
          </div>
          <div class="setting-form">
            <div class="form-group">
              <label class="form-label">旧密码</label>
              <div class="input-wrapper">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
                <input
                  :type="showOldPwd ? 'text' : 'password'"
                  v-model="pwd.oldPassword"
                  placeholder="请输入旧密码"
                />
                <button class="toggle-btn" @click="showOldPwd = !showOldPwd" type="button">
                  <svg v-if="showOldPwd" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                    <line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                </button>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">新密码</label>
              <div class="input-wrapper">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
                <input
                  :type="showNewPwd ? 'text' : 'password'"
                  v-model="pwd.newPassword"
                  placeholder="请输入新密码"
                />
                <button class="toggle-btn" @click="showNewPwd = !showNewPwd" type="button">
                  <svg v-if="showNewPwd" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                    <line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                </button>
              </div>
            </div>

            <button class="submit-btn" :loading="changing" @click="changePassword">
              保存新密码
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  padding: 0;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* 顶部蓝色通栏：头像+姓名 */
.profile-header {
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856D6 100%);
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  gap: 16px;
}

.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar svg {
  width: 40px;
  height: 40px;
}

.profile-name {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
}

/* 主体：左右布局 */
.profile-body {
  display: flex;
  gap: 37px;        
  padding: 32px;
  max-width: 1200px;
  margin: 20px auto 0; 
  width: 100%;
  box-sizing: border-box;
}
/* 左侧：基本信息 */
.left-section {
  flex: 5;
  min-width: 300px;
}

/* 右侧：设置 */
.right-section {
  flex: 5;
  width: auto;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  gap: 0px;
}

/* 卡片通用 */
.profile-card,
.setting-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 24px;
}

/* 基本信息网格 */
.detail-section {
  margin-bottom: 0;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 16px 0;
}
.detail-grid {
  display: grid;
  grid-template-columns: 1fr; 
  gap: 16px;
}
.detail-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--apple-bg-secondary);
  border-radius: var(--apple-radius);
}
.detail-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(0, 122, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.detail-icon svg {
  width: 20px;
  height: 20px;
  color: var(--apple-primary);
}
.detail-content {
  display: flex;
  flex-direction: column;
}
.detail-label {
  font-size: 12px;
  color: var(--apple-text-secondary);
}
.detail-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text);
}

/* 设置卡片 */
.card-header {
  margin-bottom: 20px;
}
.card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 4px;
}
.card-desc {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin: 0;
}

.setting-form {
  width: 100%;
}

.form-group {
  margin-bottom: 20px;
}
.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text);
  margin-bottom: 8px;
}

.input-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--apple-bg-secondary);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
}
.input-wrapper input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  color: var(--apple-text);
}
.input-wrapper svg {
  width: 20px;
  height: 20px;
  color: var(--apple-text-secondary);
}

.toggle-btn {
  background: none;
  border: none;
  cursor: pointer;
}
.toggle-btn svg {
  width: 18px;
  height: 18px;
  color: var(--apple-text-secondary);
}

.submit-btn {
  width: 100%;
  padding: 14px;
  background: var(--apple-primary);
  color: white;
  border: none;
  border-radius: var(--apple-radius);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}

/* 响应式 */
@media (max-width: 1024px) {
  .profile-body {
    flex-direction: column;
  }
  .right-section {
    width: 100%;
  }
}
</style>