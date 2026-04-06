<script setup lang="ts">
/**
 * 管理端 - 日志审计（/admin/logs）
 *
 * - 操作日志：来自 /api/admin/logs/operate（记录写操作：POST/PUT/DELETE）
 * - 登录日志：来自 /api/admin/logs/login（记录登录成功/失败）
 *
 * 页面交互：
 * - 顶部 segmented 切换 tab，并重置各自分页到第一页
 * - 各 tab 内的“查询/刷新”只影响当前 tab
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
import type { ApiResponse, PageResult } from '../../types/api'
import { labelHttpMethod, labelOperateBizType } from '../../utils/displayLabels'

type OperateLogRow = {
  id: number
  userId?: number
  httpMethod: string
  requestUri: string
  bizType?: string
  action?: string
  responseCode?: number
  successFlag: number
  errorMessage?: string
  ip?: string
  userAgent?: string
  costMs?: number
  createdAt: string
}

type LoginLogRow = {
  id: number
  username: string
  userId?: number
  successFlag: number
  errorMessage?: string
  ip?: string
  userAgent?: string
  createdAt: string
}

// 当前 tab：operate=操作日志，login=登录日志
const active = ref<'operate' | 'login'>('operate')
// 表格 loading（两张表复用一个 loading，避免切换时闪烁）
const loading = ref(false)

// 操作日志查询条件 + 分页 + 数据
const op = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
  list: [] as OperateLogRow[],
  userId: undefined as number | undefined,
  bizType: '',
  action: '',
})

// 登录日志查询条件 + 分页 + 数据
const lg = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
  list: [] as LoginLogRow[],
  username: '',
  successFlag: undefined as number | undefined,
})

// “结果”下拉选项（clearable 表示可不选 -> 查全部）
const successOptions = [
  { label: '成功', value: 1 },
  { label: '失败', value: 0 },
]

/**
 * 通用分页加载方法：
 * - 统一处理 loading、code 校验与错误抛出
 */
async function fetchPage<T>(url: string, params: Record<string, any>): Promise<PageResult<T>> {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<T>>>(url, { params })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    return resp.data.data
  } finally {
    loading.value = false
  }
}

/**
 * 拉取操作日志。
 * 接口：GET /api/admin/logs/operate
 */
async function loadOperate() {
  const data = await fetchPage<OperateLogRow>('/admin/logs/operate', {
    pageNo: op.pageNo,
    pageSize: op.pageSize,
    userId: op.userId,
    bizType: op.bizType || undefined,
    action: op.action || undefined,
  })
  op.total = data.total
  op.list = data.list
}

/**
 * 拉取登录日志。
 * 接口：GET /api/admin/logs/login
 */
async function loadLogin() {
  const data = await fetchPage<LoginLogRow>('/admin/logs/login', {
    pageNo: lg.pageNo,
    pageSize: lg.pageSize,
    username: lg.username || undefined,
    successFlag: lg.successFlag,
  })
  lg.total = data.total
  lg.list = data.list
}

/**
 * 统一入口：根据 active 加载当前 tab 数据。
 * 说明：切换 tab 或页面初始化时使用。
 */
async function load() {
  try {
    if (active.value === 'operate') await loadOperate()
    else await loadLogin()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  }
}

// 顶部卡片标题
const title = computed(() => (active.value === 'operate' ? '操作日志' : '登录日志'))

// 切换 tab
function switchTab(tab: 'operate' | 'login') {
  active.value = tab
  op.pageNo = 1
  lg.pageNo = 1
  load()
}

// 格式化时间
function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// 页面挂载：加载默认 tab
onMounted(load)
</script>

<template>
  <div class="logs-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">{{ title }}</h1>
      <p class="page-subtitle">系统操作与登录记录追踪</p>
    </div>

    <!-- Tab 切换 -->
    <div class="tab-bar">
      <button 
        class="tab-btn" 
        :class="{ active: active === 'operate' }"
        @click="switchTab('operate')"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14,2 14,8 20,8"/>
          <line x1="16" y1="13" x2="8" y2="13"/>
          <line x1="16" y1="17" x2="8" y2="17"/>
        </svg>
        操作日志
      </button>
      <button 
        class="tab-btn" 
        :class="{ active: active === 'login' }"
        @click="switchTab('login')"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/>
          <polyline points="10,17 15,12 10,7"/>
          <line x1="15" y1="12" x2="3" y2="12"/>
        </svg>
        登录日志
      </button>
    </div>

    <!-- 操作日志 -->
    <div v-if="active === 'operate'" class="log-section">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <div class="filter-inputs">
          <div class="filter-item">
            <label>用户ID</label>
            <el-input-number v-model="op.userId" :min="1" placeholder="用户编号" controls-position="right" />
          </div>
          <div class="filter-item">
            <label>业务类型</label>
            <el-input v-model="op.bizType" placeholder="业务类型编码，可留空查全部" />
          </div>
          <div class="filter-item">
            <label>动作</label>
            <el-input v-model="op.action" placeholder="请求方法，可留空查全部" />
          </div>
        </div>
        <div class="filter-actions">
          <button class="query-btn" @click="op.pageNo = 1; loadOperate()">查询</button>
          <button class="refresh-btn" @click="loadOperate">刷新</button>
        </div>
      </div>

      <!-- 时间线 -->
      <div class="timeline-container" v-loading="loading">
        <div v-if="op.list.length === 0 && !loading" class="empty-state">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12,6 12,12 16,14"/>
          </svg>
          <p>暂无操作日志</p>
        </div>

        <div class="timeline">
          <div v-for="log in op.list" :key="log.id" class="timeline-item">
            <div class="timeline-marker" :class="log.successFlag === 1 ? 'success' : 'failed'">
              <svg v-if="log.successFlag === 1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="20,6 9,17 4,12"/>
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </div>
            <div class="timeline-content">
              <div class="timeline-header">
                <span class="log-method" :class="log.httpMethod.toLowerCase()">{{ labelHttpMethod(log.httpMethod) }}</span>
                <span class="log-uri">{{ log.requestUri }}</span>
                <span class="log-time">{{ formatTime(log.createdAt) }}</span>
              </div>
              <div class="timeline-body">
                <div class="log-detail">
                  <div class="detail-item">
                    <span class="detail-label">业务</span>
                    <span class="detail-value">{{ log.bizType ? labelOperateBizType(log.bizType) : '-' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">动作</span>
                    <span class="detail-value">{{ log.action ? labelHttpMethod(log.action) : '-' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">用户</span>
                    <span class="detail-value">{{ log.userId || '-' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">响应码</span>
                    <span class="detail-value">{{ log.responseCode || '-' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">耗时</span>
                    <span class="detail-value">{{ log.costMs ? `${log.costMs}ms` : '-' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">IP 地址</span>
                    <span class="detail-value">{{ log.ip || '-' }}</span>
                  </div>
                </div>
                <div v-if="log.errorMessage" class="log-error">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="12" y1="8" x2="12" y2="12"/>
                    <line x1="12" y1="16" x2="12.01" y2="16"/>
                  </svg>
                  {{ log.errorMessage }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination-bar" v-if="op.list.length > 0">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="op.total"
          :current-page="op.pageNo"
          :page-size="op.pageSize"
          @update:current-page="(v) => (op.pageNo = v)"
          @change="loadOperate"
        />
      </div>
    </div>

    <!-- 登录日志 -->
    <div v-else class="log-section">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <div class="filter-inputs">
          <div class="filter-item">
            <label>账号</label>
            <el-input v-model="lg.username" placeholder="用户名" />
          </div>
          <div class="filter-item">
            <label>结果</label>
            <el-select v-model="lg.successFlag" placeholder="全部" clearable>
              <el-option
                v-for="o in successOptions"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </div>
        </div>
        <div class="filter-actions">
          <button class="query-btn" @click="lg.pageNo = 1; loadLogin()">查询</button>
          <button class="refresh-btn" @click="loadLogin">刷新</button>
        </div>
      </div>

      <!-- 时间线 -->
      <div class="timeline-container" v-loading="loading">
        <div v-if="lg.list.length === 0 && !loading" class="empty-state">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/>
            <polyline points="10,17 15,12 10,7"/>
            <line x1="15" y1="12" x2="3" y2="12"/>
          </svg>
          <p>暂无登录日志</p>
        </div>

        <div class="timeline">
          <div v-for="log in lg.list" :key="log.id" class="timeline-item">
            <div class="timeline-marker" :class="log.successFlag === 1 ? 'success' : 'failed'">
              <svg v-if="log.successFlag === 1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="20,6 9,17 4,12"/>
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </div>
            <div class="timeline-content">
              <div class="timeline-header">
                <span class="log-username">{{ log.username }}</span>
                <span class="log-status" :class="log.successFlag === 1 ? 'success' : 'failed'">
                  {{ log.successFlag === 1 ? '登录成功' : '登录失败' }}
                </span>
                <span class="log-time">{{ formatTime(log.createdAt) }}</span>
              </div>
              <div class="timeline-body">
                <div class="log-detail">
                  <div class="detail-item">
                    <span class="detail-label">用户ID</span>
                    <span class="detail-value">{{ log.userId || '-' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">IP地址</span>
                    <span class="detail-value">{{ log.ip || '-' }}</span>
                  </div>
                  <div class="detail-item full">
                    <span class="detail-label">User-Agent</span>
                    <span class="detail-value ua">{{ log.userAgent || '-' }}</span>
                  </div>
                </div>
                <div v-if="log.errorMessage" class="log-error">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="12" y1="8" x2="12" y2="12"/>
                    <line x1="12" y1="16" x2="12.01" y2="16"/>
                  </svg>
                  {{ log.errorMessage }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination-bar" v-if="lg.list.length > 0">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="lg.total"
          :current-page="lg.pageNo"
          :page-size="lg.pageSize"
          @update:current-page="(v) => (lg.pageNo = v)"
          @change="loadLogin"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.logs-page {
  padding: 8px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--apple-text);
  margin: 0 0 4px 0;
  letter-spacing: -0.5px;
}

.page-subtitle {
  font-size: 14px;
  color: var(--apple-text-secondary);
  margin: 0;
}

/* Tab Bar */
.tab-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: var(--apple-radius);
  border: 1px solid var(--apple-border);
  background: var(--apple-glass);
  color: var(--apple-text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.tab-btn svg {
  width: 16px;
  height: 16px;
}

.tab-btn:hover {
  background: var(--apple-bg-secondary);
}

.tab-btn.active {
  background: var(--apple-primary);
  color: white;
  border-color: var(--apple-primary);
}

/* Filter Bar */
.filter-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 16px 20px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  margin-bottom: 16px;
}

.filter-inputs {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-item label {
  font-size: 12px;
  font-weight: 500;
  color: var(--apple-text-secondary);
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.query-btn {
  padding: 8px 20px;
  border-radius: var(--apple-radius);
  border: none;
  background: var(--apple-primary);
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.query-btn:hover {
  background: #0066d6;
}

.refresh-btn {
  padding: 8px 16px;
  border-radius: var(--apple-radius);
  border: 1px solid var(--apple-border);
  background: transparent;
  color: var(--apple-text);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.refresh-btn:hover {
  background: var(--apple-bg-secondary);
}

/* Timeline Container */
.timeline-container {
  min-height: 400px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
}

.empty-state svg {
  width: 48px;
  height: 48px;
  color: var(--apple-text-tertiary);
  margin-bottom: 12px;
}

.empty-state p {
  font-size: 14px;
  color: var(--apple-text-secondary);
  margin: 0;
}

/* Timeline */
.timeline {
  position: relative;
  padding-left: 32px;
}

.timeline::before {
  content: '';
  position: absolute;
  left: 15px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--apple-border);
}

.timeline-item {
  position: relative;
  padding-bottom: 20px;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-marker {
  position: absolute;
  left: -32px;
  top: 4px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.timeline-marker svg {
  width: 14px;
  height: 14px;
}

.timeline-marker.success {
  background: rgba(52, 199, 89, 0.15);
  color: var(--apple-success);
}

.timeline-marker.failed {
  background: rgba(255, 59, 48, 0.15);
  color: var(--apple-danger);
}

.timeline-content {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  overflow: hidden;
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--apple-bg-secondary);
  border-bottom: 1px solid var(--apple-border);
}

.log-method {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 4px;
  text-transform: uppercase;
}

.log-method.get {
  background: rgba(88, 86, 214, 0.12);
  color: #5856D6;
}

.log-method.post {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.log-method.put {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.log-method.delete {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.log-uri {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text);
  font-family: 'SF Mono', Monaco, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-time {
  font-size: 12px;
  color: var(--apple-text-tertiary);
}

.log-username {
  font-size: 14px;
  font-weight: 600;
  color: var(--apple-text);
}

.log-status {
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 6px;
}

.log-status.success {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.log-status.failed {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.timeline-body {
  padding: 14px 16px;
}

.log-detail {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-item.full {
  grid-column: span 2;
}

.detail-label {
  font-size: 10px;
  color: var(--apple-text-tertiary);
  text-transform: uppercase;
}

.detail-value {
  font-size: 13px;
  color: var(--apple-text);
}

.detail-value.ua {
  font-size: 11px;
  word-break: break-all;
  color: var(--apple-text-secondary);
}

.log-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 12px;
  background: rgba(255, 59, 48, 0.08);
  border-radius: var(--apple-radius);
  font-size: 12px;
  color: var(--apple-danger);
}

.log-error svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  margin-top: 1px;
}

/* Pagination */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

