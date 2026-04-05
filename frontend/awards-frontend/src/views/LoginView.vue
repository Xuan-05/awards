<script setup lang="ts">
/**
 * 统一登录页。
 *
 * - 同一个账号入口：学生/教师端与管理员端共用一个登录页。
 * - “身份选择”仅表示用户的【期望跳转端】；真正能去哪个端取决于后端返回 roles。
 * - 登录成功后：根据 roles 自动落地到 /app 或 /admin；若与期望不一致，给出提示。
 */
import { reactive, ref } from 'vue'
import { http } from '../api/http'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import type { ApiResponse } from '../types/api'

const router = useRouter()
const user = useUserStore()

const loading = ref(false)

// 密码显示切换
const showPassword = ref(false)

// 当前聚焦的输入框
const focusedField = ref('')

// 登录表单（账号/密码）
const form = reactive({
  username: '',
  password: '',
})

// 用户在登录前选择的“期望端”（仅用于提示；最终落地端由 roles 决定）
const expectedPortal = ref<'app' | 'admin'>('app')

/**
 * 点击“登录”按钮后的提交逻辑：
 * 1) 校验表单
 * 2) 调用后端 /api/auth/login 获取 token + userInfo
 * 3) 保存 token、缓存用户信息
 * 4) 按角色自动跳转到可访问的端
 */
async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    // 调用登录接口，成功后返回 token + userInfo（含 roles）
    const resp = await http.post<
      ApiResponse<{
        token: string
        userInfo: { id: number; realName: string; userType: string; deptId: number; roles: string[] }
      }>
    >('/auth/login', form)
    if (resp.data.code !== 0) throw new Error(resp.data.message || '登录失败')
    // 保存 token（前端后续请求会自动带上 Authorization: Bearer <token>）
    user.setToken(resp.data.data.token)
    // 直接把登录返回的用户信息写入 store，避免再额外请求 /auth/me
    user.setMe(resp.data.data.userInfo)

    const roles = resp.data.data.userInfo.roles || []
    const isAdmin = roles.includes('DEPT_ADMIN') || roles.includes('SCHOOL_ADMIN') || roles.includes('SYS_ADMIN')
    const actualPortal: 'app' | 'admin' = isAdmin ? 'admin' : 'app'
    const target = actualPortal === 'admin' ? '/admin/dashboard' : '/app/workbench'

    // 若用户“期望端”和账号实际可访问端不一致，给出提示（但仍以权限为准跳转）
    if (expectedPortal.value !== actualPortal) {
      ElMessage.warning('已按账号权限自动跳转到可访问的端')
    }
    // 使用 replace：避免登录页出现在浏览器返回栈里
    await router.replace(target)
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 左侧品牌区域（大屏幕显示） -->
    <div class="brand-section">
      <div class="brand-content">
        <div class="brand-logo">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
        <h1 class="brand-title">大学生竞赛获奖管理系统</h1>
        <p class="brand-subtitle">记录每一次成长，见证每一份荣誉</p>
        
        <div class="features">
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 12l2 2 4-4"/>
                <circle cx="12" cy="12" r="10"/>
              </svg>
            </div>
            <div class="feature-text">
              <h4>便捷填报</h4>
              <p>一站式竞赛获奖记录填报</p>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
            <div class="feature-text">
              <h4>团队协作</h4>
              <p>灵活组建与管理参赛团队</p>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14,2 14,8 20,8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
                <polyline points="10,9 9,9 8,9"/>
              </svg>
            </div>
            <div class="feature-text">
              <h4>高效审核</h4>
              <p>多级审核流程清晰透明</p>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 装饰元素 -->
      <div class="decoration">
        <div class="circle circle-1"></div>
        <div class="circle circle-2"></div>
        <div class="circle circle-3"></div>
      </div>
    </div>

    <!-- 右侧登录表单区域 -->
    <div class="form-section">
      <div class="form-container">
        <!-- 移动端标题 -->
        <div class="mobile-header">
          <div class="mobile-logo">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M12 2L2 7l10 5 10-5-10-5z"/>
              <path d="M2 17l10 5 10-5"/>
              <path d="M2 12l10 5 10-5"/>
            </svg>
          </div>
          <h1>大学生竞赛获奖管理系统</h1>
        </div>

        <div class="form-header">
          <h2>欢迎登录</h2>
          <p>请选择您的身份并输入账号信息</p>
        </div>

        <!-- 端口选择 -->
        <div class="portal-selector">
          <button 
            class="portal-btn"
            :class="{ active: expectedPortal === 'app' }"
            @click="expectedPortal = 'app'"
            type="button"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
            <span>学生/教师端</span>
          </button>
          <button 
            class="portal-btn"
            :class="{ active: expectedPortal === 'admin' }"
            @click="expectedPortal = 'admin'"
            type="button"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7l10 5 10-5-10-5z"/>
              <path d="M2 17l10 5 10-5"/>
              <path d="M2 12l10 5 10-5"/>
            </svg>
            <span>管理员端</span>
          </button>
        </div>

        <!-- 登录表单 -->
        <form class="login-form" @submit.prevent="onSubmit">
          <div class="input-group" :class="{ focused: focusedField === 'username', filled: form.username }">
            <label>用户名</label>
            <div class="input-wrapper">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <input 
                type="text" 
                v-model="form.username" 
                @focus="focusedField = 'username'"
                @blur="focusedField = ''"
                autocomplete="username"
                required
              />
            </div>
          </div>

          <div class="input-group" :class="{ focused: focusedField === 'password', filled: form.password }">
            <label>密码</label>
            <div class="input-wrapper">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              <input 
                :type="showPassword ? 'text' : 'password'" 
                v-model="form.password" 
                @focus="focusedField = 'password'"
                @blur="focusedField = ''"
                autocomplete="current-password"
                required
              />
              <button type="button" class="toggle-password" @click="showPassword = !showPassword">
                <svg v-if="showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
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

          <button type="submit" class="submit-btn" :disabled="loading">
            <span v-if="loading" class="loading-spinner"></span>
            <span v-else>登录</span>
          </button>
        </form>

        <div class="form-footer">
          <p>遇到问题？请联系系统管理员</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  background: var(--apple-bg);
}

/* Brand Section - Left Side */
.brand-section {
  flex: 1;
  display: none;
  position: relative;
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856D6 50%, #AF52DE 100%);
  overflow: hidden;
  padding: 60px;
}

@media (min-width: 1024px) {
  .brand-section {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.brand-content {
  position: relative;
  z-index: 1;
  color: white;
  max-width: 480px;
}

.brand-logo {
  width: 80px;
  height: 80px;
  margin-bottom: 24px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(10px);
}

.brand-logo svg {
  width: 48px;
  height: 48px;
}

.brand-title {
  font-size: 36px;
  font-weight: 700;
  margin: 0 0 12px 0;
  letter-spacing: -0.5px;
  line-height: 1.2;
}

.brand-subtitle {
  font-size: 18px;
  opacity: 0.9;
  margin: 0 0 48px 0;
  font-weight: 400;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  backdrop-filter: blur(10px);
  transition: all 0.3s;
}

.feature-item:hover {
  background: rgba(255, 255, 255, 0.15);
  transform: translateX(8px);
}

.feature-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feature-icon svg {
  width: 24px;
  height: 24px;
}

.feature-text h4 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px 0;
}

.feature-text p {
  font-size: 14px;
  opacity: 0.8;
  margin: 0;
}

/* Decoration Circles */
.decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
}

.circle-1 {
  width: 400px;
  height: 400px;
  top: -100px;
  right: -100px;
}

.circle-2 {
  width: 300px;
  height: 300px;
  bottom: -50px;
  left: -50px;
}

.circle-3 {
  width: 200px;
  height: 200px;
  bottom: 20%;
  right: 10%;
}

/* Form Section - Right Side */
.form-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background: var(--apple-bg);
}

@media (min-width: 1024px) {
  .form-section {
    max-width: 520px;
    padding: 60px;
  }
}

.form-container {
  width: 100%;
  max-width: 400px;
}

/* Mobile Header */
.mobile-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32px;
}

@media (min-width: 1024px) {
  .mobile-header {
    display: none;
  }
}

.mobile-logo {
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856D6 100%);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mobile-logo svg {
  width: 36px;
  height: 36px;
  color: white;
}

.mobile-header h1 {
  font-size: 20px;
  font-weight: 700;
  color: var(--apple-text);
  margin: 0;
  text-align: center;
}

/* Form Header */
.form-header {
  margin-bottom: 32px;
}

@media (max-width: 1023px) {
  .form-header {
    display: none;
  }
}

.form-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: var(--apple-text);
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.form-header p {
  font-size: 15px;
  color: var(--apple-text-secondary);
  margin: 0;
}

/* Portal Selector */
.portal-selector {
  display: flex;
  gap: 12px;
  margin-bottom: 32px;
}

.portal-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 16px;
  border: 2px solid var(--apple-border);
  border-radius: 16px;
  background: transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.portal-btn svg {
  width: 28px;
  height: 28px;
  color: var(--apple-text-secondary);
  transition: color 0.2s;
}

.portal-btn span {
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text-secondary);
  transition: color 0.2s;
}

.portal-btn:hover {
  border-color: var(--apple-primary);
  background: rgba(0, 122, 255, 0.02);
}

.portal-btn.active {
  border-color: var(--apple-primary);
  background: rgba(0, 122, 255, 0.05);
}

.portal-btn.active svg {
  color: var(--apple-primary);
}

.portal-btn.active span {
  color: var(--apple-primary);
}

/* Login Form */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.input-group {
  position: relative;
}

.input-group label {
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
  padding: 14px 16px;
  background: var(--apple-bg-secondary);
  border: 1px solid var(--apple-border);
  border-radius: 12px;
  transition: all 0.2s;
}

.input-group.focused .input-wrapper {
  border-color: var(--apple-primary);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.1);
}

.input-wrapper svg {
  width: 20px;
  height: 20px;
  color: var(--apple-text-secondary);
  flex-shrink: 0;
  transition: color 0.2s;
}

.input-group.focused .input-wrapper svg {
  color: var(--apple-primary);
}

.input-wrapper input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 15px;
  color: var(--apple-text);
  outline: none;
}

.input-wrapper input::placeholder {
  color: var(--apple-text-tertiary);
}

.toggle-password {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: all 0.15s;
}

.toggle-password svg {
  width: 18px;
  height: 18px;
  color: var(--apple-text-secondary);
}

.toggle-password:hover {
  background: rgba(0, 122, 255, 0.1);
}

.toggle-password:hover svg {
  color: var(--apple-primary);
}

/* Submit Button */
.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 16px 24px;
  background: var(--apple-primary);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 8px;
}

.submit-btn:hover:not(:disabled) {
  background: #0066d6;
  box-shadow: 0 4px 16px rgba(0, 122, 255, 0.3);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Form Footer */
.form-footer {
  margin-top: 32px;
  text-align: center;
}

.form-footer p {
  font-size: 13px;
  color: var(--apple-text-tertiary);
  margin: 0;
}
</style>

