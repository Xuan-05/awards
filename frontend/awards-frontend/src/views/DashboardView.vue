<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { http } from '../api/http'
import * as echarts from 'echarts'
import { useUserStore } from '../stores/user'

type ApiResponse<T> = { code: number; message: string; data: T }
type Summary = { teamCount: number; pendingAuditCount: number; approvedCount: number }
type DeptPending = { deptId: number; pendingCount: number }
type AdminMetrics = { days: string[]; submitCounts: number[]; pendingByDept: DeptPending[] }
type Todos = { unreadMessageCount: number; pendingInvitationCount: number; draftRecordCount: number; rejectedRecordCount: number }

const userStore = useUserStore()
const isAdmin = computed(() => userStore.hasAnyRole('DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN'))
const showSchoolLevel = computed(() => userStore.hasAnyRole('SCHOOL_ADMIN', 'SYS_ADMIN'))

const loading = ref(false)
const summary = ref<Summary | null>(null)
const metrics = ref<AdminMetrics | null>(null)
const todos = ref<Todos | null>(null)
const l2PendingCount = ref<number | null>(null)
const schoolStats = ref<{
  categorySeries: { name: string; value: number }[]
  awardBins: { label: string; value: number }[]
  l2Series: { name: string; value: number }[]
} | null>(null)

const lastUpdatedAt = ref<number | null>(null)
const autoRefresh = ref(true)
const refreshSecondsLeft = ref(60)
let refreshTimer: number | null = null
let countdownTimer: number | null = null

const trendEl = ref<HTMLDivElement | null>(null)
const deptEl = ref<HTMLDivElement | null>(null)
const categoryEl = ref<HTMLDivElement | null>(null)
const awardEl = ref<HTMLDivElement | null>(null)
const l2El = ref<HTMLDivElement | null>(null)

let trendChart: echarts.ECharts | null = null
let deptChart: echarts.ECharts | null = null
let categoryChart: echarts.ECharts | null = null
let awardChart: echarts.ECharts | null = null
let l2Chart: echarts.ECharts | null = null

type ApprovedRow = {
  competitionCategoryId?: number
  competitionId?: number
  awardScopeId?: number
  awardLevelId?: number
  l2ReviewFlag?: number
  l2ReviewResult?: string
}
type DictCategory = { id: number; categoryName: string }
type DictScope = { id: number; scopeName: string }
type DictLevel = { id: number; levelName: string; awardScopeId?: number }

function nowText() {
  if (!lastUpdatedAt.value) return '—'
  const d = new Date(lastUpdatedAt.value)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

async function loadTodos() {
  const resp = await http.get<ApiResponse<Todos>>('/dashboard/todos')
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  todos.value = resp.data.data
}

async function loadSchoolLevelStats() {
  // 这里取一大页即可：用于图表聚合统计（数据量不大时够用）。
  const [approvedResp, catResp, scopeResp, levelResp] = await Promise.all([
    http.get<ApiResponse<{ total: number; list: ApprovedRow[] }>>('/admin/records/approved', {
      params: { pageNo: 1, pageSize: 5000, sortBy: 'finalAuditTime', sortOrder: 'desc' },
    }),
    http.get<ApiResponse<{ total: number; list: DictCategory[] }>>('/dicts/categories', { params: { pageNo: 1, pageSize: 500, enabled: 1 } }),
    http.get<ApiResponse<{ total: number; list: DictScope[] }>>('/dicts/award-scopes', { params: { pageNo: 1, pageSize: 500, enabled: 1 } }),
    http.get<ApiResponse<{ total: number; list: DictLevel[] }>>('/dicts/award-levels', { params: { pageNo: 1, pageSize: 500, enabled: 1 } }),
  ])

  if (approvedResp.data.code !== 0) throw new Error(approvedResp.data.message)
  if (catResp.data.code !== 0) throw new Error(catResp.data.message)
  if (scopeResp.data.code !== 0) throw new Error(scopeResp.data.message)
  if (levelResp.data.code !== 0) throw new Error(levelResp.data.message)

  const approved = approvedResp.data.data.list ?? []
  const categories = catResp.data.data.list ?? []
  const scopes = scopeResp.data.data.list ?? []
  const levels = levelResp.data.data.list ?? []

  // 1) 二级复审待处理数量：APPROVED 下 l2ReviewFlag != 1
  const reviewed = approved.filter((r) => r.l2ReviewFlag === 1).length
  l2PendingCount.value = approved.length - reviewed

  // 2) 竞赛类别占比（按 competition_category_id）
  const catCount = new Map<number, number>()
  for (const r of approved) {
    const id = r.competitionCategoryId
    if (!id && id !== 0) continue
    catCount.set(id, (catCount.get(id) ?? 0) + 1)
  }
  const catNameById = new Map<number, string>()
  for (const c of categories) catNameById.set(c.id, c.categoryName)
  const sortedCats = [...catCount.entries()].sort((a, b) => b[1] - a[1])
  const catTop = sortedCats.slice(0, 6)
  const catOthers = sortedCats.slice(6).reduce((sum, [, v]) => sum + v, 0)
  const categorySeries = catTop.map(([id, v]) => ({ name: catNameById.get(id) ?? String(id), value: v }))
  if (catOthers > 0) categorySeries.push({ name: '其他', value: catOthers })

  // 3) 奖项维度（范围+等级）Top
  const scopeNameById = new Map<number, string>()
  for (const s of scopes) scopeNameById.set(s.id, s.scopeName)
  const levelNameById = new Map<number, string>()
  for (const l of levels) levelNameById.set(l.id, l.levelName)

  const awardBinsCount = new Map<string, number>()
  const awardBinsLabelByKey = new Map<string, string>()
  for (const r of approved) {
    const scopeId = r.awardScopeId
    const levelId = r.awardLevelId
    if (scopeId == null || levelId == null) continue
    const key = `${scopeId}|${levelId}`
    awardBinsCount.set(key, (awardBinsCount.get(key) ?? 0) + 1)
    if (!awardBinsLabelByKey.has(key)) {
      const scopeName = scopeNameById.get(scopeId!) ?? String(scopeId)
      const levelName = levelNameById.get(levelId!) ?? String(levelId)
      awardBinsLabelByKey.set(key, `${scopeName} ${levelName}`)
    }
  }
  const sortedBins = [...awardBinsCount.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8)
  const awardBins = sortedBins.map(([key, v]) => ({ label: awardBinsLabelByKey.get(key) ?? key, value: v }))

  // 4) 复审状态占比（l2ReviewFlag=1 时按 l2ReviewResult）
  const l2ReviewFlag = approved.filter((r) => r.l2ReviewFlag === 1)
  const l2Pass = l2ReviewFlag.filter((r) => r.l2ReviewResult === 'PASS').length
  const l2Reject = l2ReviewFlag.filter((r) => r.l2ReviewResult === 'REJECT').length
  const l2Pending = l2ReviewFlag.filter((r) => r.l2ReviewResult === 'PENDING').length
  const notReviewed = approved.length - l2ReviewFlag.length

  const l2Series = [
    { name: '未复审', value: notReviewed },
    { name: '复审通过', value: l2Pass },
    { name: '复审驳回', value: l2Reject },
  ]
  if (l2Pending > 0) l2Series.splice(2, 0, { name: '待复审', value: l2Pending })

  schoolStats.value = { categorySeries, awardBins, l2Series }
}

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<Summary>>('/dashboard/summary')
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    summary.value = resp.data.data

    if (isAdmin.value) {
      const m = await http.get<ApiResponse<AdminMetrics>>('/dashboard/admin/metrics')
      if (m.data.code === 0) metrics.value = m.data.data
    }

    await loadTodos()
    if (showSchoolLevel.value) await loadSchoolLevelStats()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  if (!metrics.value) return
  if (trendEl.value) {
    trendChart ||= echarts.init(trendEl.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: metrics.value.days },
      yAxis: { type: 'value' },
      series: [{ name: '提交量', type: 'line', data: metrics.value.submitCounts, smooth: true }],
    })
  }
  if (deptEl.value) {
    deptChart ||= echarts.init(deptEl.value)
    deptChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: metrics.value.pendingByDept.map((d) => String(d.deptId)),
        axisLabel: { interval: 0, rotate: 30 },
      },
      yAxis: { type: 'value' },
      series: [{ name: '待校级审核', type: 'bar', data: metrics.value.pendingByDept.map((d) => d.pendingCount) }],
    })
  }
}

function renderSchoolCharts() {
  if (!schoolStats.value) return

  if (categoryEl.value) {
    categoryChart ||= echarts.init(categoryEl.value)
    const data = schoolStats.value.categorySeries.map((x) => ({ value: x.value, name: x.name }))
    categoryChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, left: 'center', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
      series: [
        {
          name: '竞赛分类',
          type: 'pie',
          radius: ['45%', '70%'],
          center: ['50%', '55%'],
          data,
          label: { show: false },
          emphasis: {
            label: { show: true, fontSize: 12, formatter: '{b}: {c}' },
          },
        },
      ],
    })
  }

  if (awardEl.value) {
    awardChart ||= echarts.init(awardEl.value)
    awardChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: schoolStats.value.awardBins.map((x) => x.label),
        axisLabel: { interval: 0, rotate: 25 },
      },
      yAxis: { type: 'value' },
      series: [
        {
          name: '获奖记录数',
          type: 'bar',
          data: schoolStats.value.awardBins.map((x) => x.value),
        },
      ],
    })
  }

  if (l2El.value) {
    l2Chart ||= echarts.init(l2El.value)
    const data = schoolStats.value.l2Series.map((x) => ({ value: x.value, name: x.name }))
    l2Chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, left: 'center', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
      series: [
        {
          name: '复审状态',
          type: 'pie',
          radius: ['45%', '70%'],
          center: ['50%', '55%'],
          data,
          label: { show: false },
        },
      ],
    })
  }
}

async function refreshNow() {
  await load()
  lastUpdatedAt.value = Date.now()
  renderCharts()
  renderSchoolCharts()
}

async function startAutoRefresh() {
  if (!autoRefresh.value) return
  refreshTimer && clearInterval(refreshTimer)
  refreshTimer = null
  countdownTimer && clearInterval(countdownTimer)
  refreshSecondsLeft.value = 60

  countdownTimer = window.setInterval(async () => {
    refreshSecondsLeft.value -= 1
    if (refreshSecondsLeft.value <= 0) {
      try {
        await refreshNow()
      } catch {
        // ignore refresh errors to avoid timer crash
      } finally {
        refreshSecondsLeft.value = 60
      }
    }
  }, 1000)
}

onMounted(async () => {
  if (!userStore.meLoaded) await userStore.fetchMe()
  await load()
  lastUpdatedAt.value = Date.now()
  renderCharts()
  renderSchoolCharts()
  window.addEventListener('resize', () => {
    trendChart?.resize()
    deptChart?.resize()
    categoryChart?.resize()
    awardChart?.resize()
    l2Chart?.resize()
  })

  await startAutoRefresh()
})

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (countdownTimer) clearInterval(countdownTimer)
})

watch(autoRefresh, async (v) => {
  if (!v) {
    if (refreshTimer) clearInterval(refreshTimer)
    if (countdownTimer) clearInterval(countdownTimer)
    return
  }
  try {
    await refreshNow()
    await startAutoRefresh()
  } catch {
    // ignore
  }
})
</script>

<template>
  <div class="dashboard">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-header-left">
        <h1 class="page-title">控制台</h1>
        <p class="page-subtitle">数据概览与快捷操作</p>
      </div>
      <div class="page-header-right">
        <div class="header-meta">
          <span class="meta-label">最后更新：</span>
          <span class="meta-value">{{ nowText() }}</span>
        </div>
        <div class="refresh-meta">
          <span class="meta-label">自动刷新</span>
          <el-switch v-model="autoRefresh" size="small" />
          <span class="countdown" v-if="autoRefresh">({{ refreshSecondsLeft }}s)</span>
        </div>
        <el-button size="small" type="primary" @click="refreshNow">立即刷新</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row" :class="{ 'stats-row-4': showSchoolLevel }" v-loading="loading">
      <div class="stat-card stat-card-blue">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ summary?.teamCount ?? '-' }}</div>
          <div class="stat-label">团队总数</div>
        </div>
      </div>

      <div class="stat-card stat-card-orange">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12,6 12,12 16,14"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ summary?.pendingAuditCount ?? '-' }}</div>
          <div class="stat-label">待校级审核</div>
        </div>
      </div>

      <div class="stat-card stat-card-green">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22,4 12,14.01 9,11.01"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ summary?.approvedCount ?? '-' }}</div>
          <div class="stat-label">审核通过</div>
        </div>
      </div>

      <div v-if="showSchoolLevel" class="stat-card stat-card-purple">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2l9 4.5-9 4.5-9-4.5L12 2z"/>
            <path d="M3 6.5V17.5L12 22l9-4.5V6.5"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ l2PendingCount ?? '-' }}</div>
          <div class="stat-label">待二级复审</div>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <div v-if="metrics" class="charts-row">
      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">近 7 天提交趋势</h3>
        </div>
        <div ref="trendEl" class="chart-container" />
      </div>

      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">各院系待校级审核分布</h3>
          <span class="chart-badge">Top 20</span>
        </div>
        <div ref="deptEl" class="chart-container" />
      </div>
    </div>

    <!-- 待办与动态 -->
    <div v-if="todos" class="todos-card">
      <div class="chart-header">
        <h3 class="chart-title">我的待办</h3>
        <span class="chart-badge">实时</span>
      </div>
      <div class="todos-grid">
        <div class="todo-item" @click="$router.push('/admin/logs')">
          <div class="todo-top">
            <span class="todo-dot todo-dot-blue"></span>
            <span class="todo-label">未读消息</span>
          </div>
          <div class="todo-value">{{ todos.unreadMessageCount }}</div>
        </div>
        <div class="todo-item" @click="$router.push('/admin/teams')">
          <div class="todo-top">
            <span class="todo-dot todo-dot-orange"></span>
            <span class="todo-label">待接受邀请</span>
          </div>
          <div class="todo-value">{{ todos.pendingInvitationCount }}</div>
        </div>
        <div class="todo-item" @click="$router.push('/admin/audit/l1')">
          <div class="todo-top">
            <span class="todo-dot todo-dot-green"></span>
            <span class="todo-label">我的草稿</span>
          </div>
          <div class="todo-value">{{ todos.draftRecordCount }}</div>
        </div>
        <div class="todo-item" @click="$router.push('/admin/audit/l1')">
          <div class="todo-top">
            <span class="todo-dot todo-dot-red"></span>
            <span class="todo-label">我的驳回</span>
          </div>
          <div class="todo-value">{{ todos.rejectedRecordCount }}</div>
        </div>
      </div>
    </div>

    <!-- 校级管理员扩展报表 -->
    <div v-if="showSchoolLevel && schoolStats" class="charts-row-extended">
      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">竞赛类别占比（C/D更明显）</h3>
          <span class="chart-badge">Approved</span>
        </div>
        <div ref="categoryEl" class="chart-container chart-container-donut" />
      </div>

      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">奖项维度 Top（范围+等级）</h3>
          <span class="chart-badge">Top 8</span>
        </div>
        <div ref="awardEl" class="chart-container" />
      </div>

      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">复审状态占比</h3>
          <span class="chart-badge">L2</span>
        </div>
        <div ref="l2El" class="chart-container chart-container-donut" />
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="shortcuts-card">
      <h3 class="shortcuts-title">快捷入口</h3>
      <div class="shortcuts-grid">
        <div class="shortcut-item" @click="$router.push('/admin/audit/l1')">
          <div class="shortcut-icon shortcut-icon-primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 11l3 3L22 4"/>
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
            </svg>
          </div>
          <span class="shortcut-label">一级审核台</span>
        </div>

        <div class="shortcut-item" @click="$router.push('/admin/org/depts')">
          <div class="shortcut-icon shortcut-icon-blue">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <span class="shortcut-label">用户组织</span>
        </div>

        <div class="shortcut-item" @click="$router.push('/admin/dicts/competitions')">
          <div class="shortcut-icon shortcut-icon-purple">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
          </div>
          <span class="shortcut-label">竞赛字典</span>
        </div>

        <div class="shortcut-item" @click="$router.push('/admin/dicts/classes')">
          <div class="shortcut-icon shortcut-icon-cyan">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
              <line x1="16" y1="2" x2="16" y2="6"/>
              <line x1="8" y1="2" x2="8" y2="6"/>
              <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
          </div>
          <span class="shortcut-label">班级字典</span>
        </div>

        <div class="shortcut-item" @click="$router.push('/admin/exports')">
          <div class="shortcut-icon shortcut-icon-green">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7,10 12,15 17,10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
          </div>
          <span class="shortcut-label">数据导出</span>
        </div>

        <div class="shortcut-item" @click="$router.push('/admin/logs')">
          <div class="shortcut-icon shortcut-icon-orange">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14,2 14,8 20,8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10,9 9,9 8,9"/>
            </svg>
          </div>
          <span class="shortcut-label">日志审计</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 8px;
}

.page-header {
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
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

.page-header-left {
  min-width: 0;
}

.page-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.header-meta {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.meta-label {
  font-size: 12px;
  color: var(--apple-text-secondary);
}

.meta-value {
  font-size: 12px;
  font-weight: 700;
  color: var(--apple-text);
}

.refresh-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.countdown {
  font-size: 12px;
  color: var(--apple-text-secondary);
}

/* Stats Row */
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stats-row-4 {
  grid-template-columns: repeat(4, 1fr);
}
.stat-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--apple-shadow-md);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon svg {
  width: 24px;
  height: 24px;
}

.stat-card-blue .stat-icon {
  background: rgba(0, 122, 255, 0.12);
  color: var(--apple-primary);
}

.stat-card-orange .stat-icon {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.stat-card-green .stat-icon {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.stat-card-purple .stat-icon {
  background: rgba(175, 82, 222, 0.12);
  color: #AF52DE;
}

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--apple-text);
  line-height: 1.2;
  letter-spacing: -1px;
}

.stat-label {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin-top: 2px;
}

/* Charts Row */
.charts-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.chart-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 20px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0;
}

.chart-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--apple-text-secondary);
  background: var(--apple-bg-secondary);
  padding: 4px 8px;
  border-radius: 6px;
}

.chart-container {
  height: 280px;
}

.chart-container-donut {
  height: 280px;
}

.charts-row-extended {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

/* Todos Card */
.todos-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 20px;
  margin-bottom: 24px;
}

.todos-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.todo-item {
  border-radius: var(--apple-radius);
  padding: 14px 12px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: transparent;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.todo-item:hover {
  background: var(--apple-bg-secondary);
  transform: translateY(-1px);
}

.todo-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.todo-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  display: inline-block;
}

.todo-dot-blue {
  background: var(--apple-primary);
}

.todo-dot-orange {
  background: var(--apple-warning);
}

.todo-dot-green {
  background: var(--apple-success);
}

.todo-dot-red {
  background: #FF3B30;
}

.todo-label {
  font-size: 12px;
  color: var(--apple-text-secondary);
}

.todo-value {
  font-size: 20px;
  font-weight: 800;
  color: var(--apple-text);
  letter-spacing: -0.4px;
}

@media (max-width: 1200px) {
  .charts-row-extended {
    grid-template-columns: repeat(2, 1fr);
  }
  .todos-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 680px) {
  .charts-row-extended {
    grid-template-columns: 1fr;
  }
  .todos-grid {
    grid-template-columns: 1fr;
  }
}

/* Shortcuts Card */
.shortcuts-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 20px;

  /* 占满上层模块：移除最大宽度限制 */
  width: 100%;
  max-width: none;
  margin: 0;
  box-sizing: border-box;

  /* 内容横向并排布局 */
  display: flex;
  flex-direction: row;
  flex-wrap: wrap; 
  gap: 16px; 
  align-items: center; 
}
.shortcuts-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--apple-text);
  /* 定位到卡片左上角 */
  position: absolute;
  top: 20px;    /* 距离顶部内边距 */
  left: 20px;   /* 距离左侧内边距 */
  margin: 0;    /* 清除原来的负边距 */
}

.shortcuts-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr); 
  gap: 50px;
  padding-left: 24px;
    padding-top: 25px;
}

.shortcut-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px 12px;
  border-radius: var(--apple-radius);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  background: transparent;
}

.shortcut-item:hover {
  background: var(--apple-bg-secondary);
}

.shortcut-item:active {
  transform: scale(0.96);
}

.shortcut-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shortcut-icon svg {
  width: 22px;
  height: 22px;
}

.shortcut-icon-primary {
  background: var(--apple-primary);
  color: white;
}

.shortcut-icon-blue {
  background: #5856D6;
  color: white;
}

.shortcut-icon-purple {
  background: #AF52DE;
  color: white;
}

.shortcut-icon-cyan {
  background: #32ADEA;
  color: white;
}

.shortcut-icon-green {
  background: var(--apple-success);
  color: white;
}

.shortcut-icon-orange {
  background: var(--apple-warning);
  color: white;
}

.shortcut-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--apple-text);
  text-align: center;
}

@media (max-width: 1200px) {
  .shortcuts-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .stats-row {
    grid-template-columns: 1fr;
  }
  .charts-row {
    grid-template-columns: 1fr;
  }
}
</style>

