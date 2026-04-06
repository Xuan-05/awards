<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
import { useUserStore } from '../../stores/user'
import { labelRoleCode, labelUserType } from '../../utils/displayLabels'

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

</script>

<template>
  <div class="profile-page" v-loading="loading">
    <!-- 个人信息卡片 -->
    <div class="profile-card">
      <div class="profile-header">
        <div class="avatar">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
        </div>
        <div class="profile-info">
          <h1 class="profile-name">{{ user.me?.realName || '用户' }}</h1>
          <p class="profile-type">{{ labelUserType(user.me?.userType) }}</p>
          <div class="profile-badges">
            <span class="badge" v-for="role in user.me?.roles" :key="role">
              {{ labelRoleCode(role) }}
            </span>
          </div>
        </div>
      </div>

      <div class="profile-details">
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
                <span class="detail-value">{{ labelUserType(user.me?.userType) }}</span>
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

    <!-- 联系方式 -->
    <div class="password-card">
      <div class="card-header">
        <h3 class="card-title">联系方式</h3>
        <p class="card-desc">手机号与邮箱用于通知与联系，所有身份均可维护</p>
      </div>
      <el-form
        ref="contactFormRef"
        :model="contact"
        :rules="contactRules"
        label-position="top"
        class="password-form"
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

    <!-- 修改密码卡片 -->
    <div class="password-card">
      <div class="card-header">
        <h3 class="card-title">修改密码</h3>
        <p class="card-desc">定期更换密码可以提高账户安全性</p>
      </div>
      <div class="password-form">
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
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
            <polyline points="17,21 17,13 7,13 7,21"/>
            <polyline points="7,3 7,8 15,8"/>
          </svg>
          保存新密码
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Profile Card */
.profile-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  overflow: hidden;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 32px;
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856D6 100%);
  color: white;
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

.profile-info {
  flex: 1;
}

.profile-name {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 4px 0;
  letter-spacing: -0.5px;
}

.profile-type {
  font-size: 15px;
  opacity: 0.9;
  margin: 0 0 12px 0;
}

.profile-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.badge {
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.profile-details {
  padding: 24px;
}

.detail-section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 16px 0;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
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
  margin-bottom: 2px;
}

.detail-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text);
}

/* Password Card */
.password-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 24px;
}

.card-header {
  margin-bottom: 24px;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 4px 0;
}

.card-desc {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin: 0;
}

.password-form {
  max-width: 400px;
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
  transition: all 0.15s;
}

.input-wrapper:focus-within {
  border-color: var(--apple-primary);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.1);
}

.input-wrapper svg {
  width: 20px;
  height: 20px;
  color: var(--apple-text-secondary);
  flex-shrink: 0;
}

.input-wrapper input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 14px;
  color: var(--apple-text);
  outline: none;
}

.input-wrapper input::placeholder {
  color: var(--apple-text-tertiary);
}

.toggle-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: all 0.15s;
}

.toggle-btn svg {
  width: 18px;
  height: 18px;
  color: var(--apple-text-secondary);
}

.toggle-btn:hover {
  background: rgba(0, 122, 255, 0.1);
}

.toggle-btn:hover svg {
  color: var(--apple-primary);
}

.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 14px 24px;
  background: var(--apple-primary);
  color: white;
  border: none;
  border-radius: var(--apple-radius);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.submit-btn svg {
  width: 18px;
  height: 18px;
}

.submit-btn:hover {
  background: #0066d6;
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);
}

@media (max-width: 768px) {
  .profile-header {
    flex-direction: column;
    text-align: center;
    padding: 24px;
  }

  .profile-badges {
    justify-content: center;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .password-form {
    max-width: 100%;
  }
}
</style>

