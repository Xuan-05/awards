<script setup lang="ts">
/**
 * 学生/教师端 - 竞赛填报列表（/app/records）。
 *
 * 核心能力：
 * - 查询我提交的填报记录（按状态/团队/竞赛/学期过滤）
 * - 草稿/驳回可提交；待审可撤回；草稿可删除
 * - 下拉项来自“我的团队”与“竞赛字典”
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '../../api/http'
import { useUserStore } from '../../stores/user'
import type { ApiResponse, PageResult } from '../../types/api'
import { labelRecordStatus } from '../../utils/displayLabels'

type Team = { id: number; teamName: string; captainUserId: number }
type Competition = { id: number; competitionName: string; enabled: number }

type AwardRecord = {
  id: number
  status: string
  teamId: number
  competitionId: number
  semester?: string
  projectName?: string
  awardDate?: string
  updatedAt?: string
}

const router = useRouter()
const user = useUserStore()

// 状态 tab：用于筛选列表（'' 表示全部）
const tabs = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '待校级审核', value: 'PENDING_SCHOOL' },
  { label: '校级驳回', value: 'SCHOOL_REJECTED' },
  { label: '审核通过', value: 'APPROVED' },
]

// 列表 loading
const loading = ref(false)
// 列表数据
const rows = ref<AwardRecord[]>([])
// 分页数据
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
// 查询条件
const query = reactive({
  status: '',
  teamId: undefined as number | undefined,
  competitionId: undefined as number | undefined,
  semester: '',
})

// 是否有“填报”权限：
// 1. 是某个团队的队长（team.captainUserId === user.me.id）
// 2. 是指导教师（TEACHER 角色）
// 3. 是管理员（DEPT_ADMIN, SCHOOL_ADMIN, SYS_ADMIN 角色）
const isCaptainOfAnyTeam = computed(() => teamOptions.value.some(t => t.captainUserId === user.me?.id))
const canCreate = computed(() =>
  isCaptainOfAnyTeam.value ||
  user.hasAnyRole('TEACHER', 'DEPT_ADMIN', 'SCHOOL_ADMIN', 'SYS_ADMIN')
)

// “我的团队”下拉选项
const teamOptions = ref<Team[]>([])
// “竞赛字典”下拉选项
const competitionOptions = ref<Competition[]>([])

// 通过 options 生成 id->name 的 map，便于表格展示（避免每行都 find）
const teamNameMap = computed(() => new Map(teamOptions.value.map((t) => [t.id, t.teamName])))
const competitionNameMap = computed(() => new Map(competitionOptions.value.map((c) => [c.id, c.competitionName])))

function formatDateCn(input?: string) {
  if (!input) return '-'
  const d = new Date(input)
  if (Number.isNaN(d.getTime())) return input
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}年${month}月${day}日`
}

/**
 * 加载下拉选项：
 * - 团队：GET /api/teams/my
 * - 竞赛：GET /api/dicts/competitions?enabled=1
 */
async function loadOptions() {
  const [teamsResp, compsResp] = await Promise.all([
    http.get<ApiResponse<Team[]>>('/teams/my'),
    http.get<ApiResponse<PageResult<Competition>>>('/dicts/competitions', { params: { enabled: 1, pageSize: 200 } }),
  ])
  if (teamsResp.data.code !== 0) throw new Error(teamsResp.data.message)
  if (compsResp.data.code !== 0) throw new Error(compsResp.data.message)
  teamOptions.value = teamsResp.data.data
  competitionOptions.value = compsResp.data.data.list
}

/**
 * 加载列表数据。
 * 接口：GET /api/award-records/my
 */
async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<AwardRecord>>>('/award-records/my', {
      params: {
        status: query.status || undefined,
        teamId: query.teamId,
        competitionId: query.competitionId,
        semester: query.semester || undefined,
        pageNo: page.pageNo,
        pageSize: page.pageSize,
      },
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    rows.value = resp.data.data.list
    page.total = resp.data.data.total
  } finally {
    loading.value = false
  }
}

/**
 * 提交：DRAFT / SCHOOL_REJECTED -> PENDING_SCHOOL
 * 接口：POST /api/award-records/{id}/submit
 */
async function submit(id: number) {
  const resp = await http.post<ApiResponse<null>>(`/award-records/${id}/submit`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  await load()
}

/**
 * 撤回：PENDING_SCHOOL -> DRAFT
 * 接口：POST /api/award-records/{id}/withdraw
 */
async function withdraw(id: number) {
  const resp = await http.post<ApiResponse<null>>(`/award-records/${id}/withdraw`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  await load()
}

/**
 * 删除草稿：仅 DRAFT 可删除
 * 接口：DELETE /api/award-records/{id}
 */
async function delDraft(id: number) {
  const resp = await http.delete<ApiResponse<null>>(`/award-records/${id}`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  await load()
}

onMounted(async () => {
  // 首次进入：确保 user.me 已加载（用于 canCreate 计算与顶部展示）
  if (!user.meLoaded) await user.fetchMe()
  await loadOptions()
  await load()
})

</script>

<template>
  <div class="records-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">我的填报</h1>
        <p class="page-subtitle">管理您的竞赛获奖填报记录</p>
      </div>
      <button class="create-btn" @click="router.push('/app/records/new')" :disabled="!canCreate">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        新建填报
      </button>
    </div>

    <!-- 无权限提示 -->
    <div class="permission-notice" v-if="!canCreate">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <line x1="12" y1="8" x2="12" y2="12"/>
        <line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <span>您当前角色无填报权限（仅队长/指导教师/管理员可填报）</span>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-tabs">
        <button 
          v-for="tab in tabs" 
          :key="tab.value"
          class="filter-tab"
          :class="{ active: query.status === tab.value }"
          @click="query.status = tab.value; load()"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="filter-actions">
        <select v-model="query.teamId" @change="load" class="filter-select">
          <option :value="undefined">全部团队</option>
          <option v-for="t in teamOptions" :key="t.id" :value="t.id">{{ t.teamName }}</option>
        </select>
        <select v-model="query.competitionId" @change="load" class="filter-select">
          <option :value="undefined">全部竞赛</option>
          <option v-for="c in competitionOptions" :key="c.id" :value="c.id">{{ c.competitionName }}</option>
        </select>
        <input 
          type="text" 
          v-model="query.semester" 
          placeholder="学期筛选"
          class="filter-input"
          @keyup.enter="load"
        />
        <button class="filter-btn" @click="load">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          查询
        </button>
      </div>
    </div>

    <!-- 记录列表 -->
    <div class="records-list" v-loading="loading">
      <div v-for="row in rows" :key="row.id" class="record-card" @click="router.push(`/app/records/${row.id}`)">
        <div class="record-status" :class="row.status.toLowerCase()">
          {{ labelRecordStatus(row.status) }}
        </div>
        <div class="record-main">
          <div class="record-header">
            <h3 class="record-project">{{ row.projectName || '未命名项目' }}</h3>
            <span class="record-id">#{{ row.id }}</span>
          </div>
          <div class="record-meta">
            <span class="meta-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="8" r="7"/>
                <polyline points="8.21,13.89 7,23 12,20 17,23 15.79,13.88"/>
              </svg>
              {{ competitionNameMap.get(row.competitionId) || `#${row.competitionId}` }}
            </span>
            <span class="meta-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
              </svg>
              {{ teamNameMap.get(row.teamId) || `#${row.teamId}` }}
            </span>
            <span class="meta-item" v-if="row.semester">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                <line x1="16" y1="2" x2="16" y2="6"/>
                <line x1="8" y1="2" x2="8" y2="6"/>
                <line x1="3" y1="10" x2="21" y2="10"/>
              </svg>
              {{ row.semester }}
            </span>
          </div>
          <div class="record-footer">
            <span class="record-time">更新于 {{ formatDateCn(row.updatedAt) }}</span>
            <div class="record-actions" @click.stop>
              <button 
                v-if="row.status === 'DRAFT' || row.status === 'SCHOOL_REJECTED'"
                class="action-btn submit"
                @click="submit(row.id)"
              >
                提交
              </button>
              <button 
                v-if="row.status === 'PENDING_SCHOOL'"
                class="action-btn withdraw"
                @click="withdraw(row.id)"
              >
                撤回
              </button>
              <button 
                v-if="row.status === 'DRAFT'"
                class="action-btn delete"
                @click="delDraft(row.id)"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="rows.length === 0 && !loading" class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14,2 14,8 20,8"/>
        </svg>
        <p>暂无填报记录</p>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination-bar" v-if="rows.length > 0">
      <span class="pagination-info">共 {{ page.total }} 条记录</span>
      <div class="pagination-controls">
        <button 
          class="page-btn" 
          :disabled="page.pageNo <= 1"
          @click="page.pageNo--; load()"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="15,18 9,12 15,6"/>
          </svg>
        </button>
        <span class="page-num">{{ page.pageNo }} / {{ Math.ceil(page.total / page.pageSize) }}</span>
        <button 
          class="page-btn"
          :disabled="page.pageNo >= Math.ceil(page.total / page.pageSize)"
          @click="page.pageNo++; load()"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9,18 15,12 9,6"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.records-page {
  padding: 8px;
}

/* Page Header */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  font-size: 24px;
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

.create-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--apple-primary);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.create-btn svg {
  width: 18px;
  height: 18px;
}

.create-btn:hover:not(:disabled) {
  background: #0066d6;
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);
}

.create-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Permission Notice */
.permission-notice {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(255, 149, 0, 0.1);
  border: 1px solid rgba(255, 149, 0, 0.2);
  border-radius: var(--apple-radius);
  margin-bottom: 16px;
  color: var(--apple-warning);
  font-size: 14px;
}

.permission-notice svg {
  width: 20px;
  height: 20px;
}

/* Filter Bar */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
}

.filter-tabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.filter-tab {
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: var(--apple-text-secondary);
  font-size: 13px;
  font-weight: 500;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.15s;
}

.filter-tab:hover {
  background: var(--apple-bg-secondary);
  color: var(--apple-text);
}

.filter-tab.active {
  background: var(--apple-primary);
  color: white;
}

.filter-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  margin-left: auto;
}

.filter-select {
  padding: 8px 12px;
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
  font-size: 13px;
  color: var(--apple-text);
  background: var(--apple-bg-secondary);
  cursor: pointer;
  outline: none;
  min-width: 120px;
}

.filter-select:focus {
  border-color: var(--apple-primary);
}

.filter-input {
  padding: 8px 12px;
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
  font-size: 13px;
  color: var(--apple-text);
  background: var(--apple-bg-secondary);
  outline: none;
  width: 120px;
}

.filter-input:focus {
  border-color: var(--apple-primary);
}

.filter-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--apple-primary);
  color: white;
  border: none;
  border-radius: var(--apple-radius);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.filter-btn svg {
  width: 16px;
  height: 16px;
}

.filter-btn:hover {
  background: #0066d6;
}

/* Records List */
.records-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  cursor: pointer;
  transition: all 0.2s;
}

.record-card:hover {
  border-color: var(--apple-primary);
  transform: translateY(-2px);
  box-shadow: var(--apple-shadow);
}

.record-status {
  width: 80px;
  padding: 6px 0;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  border-radius: 6px;
  flex-shrink: 0;
}

.record-status.draft {
  background: rgba(142, 142, 147, 0.12);
  color: var(--apple-text-secondary);
}

.record-status.pending_school {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.record-status.school_rejected {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.record-status.approved {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.record-main {
  flex: 1;
  min-width: 0;
}

.record-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.record-project {
  font-size: 16px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-id {
  font-size: 12px;
  color: var(--apple-text-tertiary);
  flex-shrink: 0;
}

.record-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--apple-text-secondary);
}

.meta-item svg {
  width: 14px;
  height: 14px;
}

.record-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.record-time {
  font-size: 12px;
  color: var(--apple-text-tertiary);
}

.record-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 6px 14px;
  border: none;
  border-radius: 14px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn.submit {
  background: var(--apple-primary);
  color: white;
}

.action-btn.submit:hover {
  background: #0066d6;
}

.action-btn.withdraw {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.action-btn.withdraw:hover {
  background: rgba(255, 149, 0, 0.2);
}

.action-btn.delete {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.action-btn.delete:hover {
  background: rgba(255, 59, 48, 0.2);
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
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
  margin-bottom: 16px;
}

.empty-state p {
  font-size: 15px;
  color: var(--apple-text-secondary);
  margin: 0 0 16px 0;
}

.empty-btn {
  padding: 10px 20px;
  background: var(--apple-primary);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.empty-btn:hover {
  background: #0066d6;
}

/* Pagination */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding: 12px 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
}

.pagination-info {
  font-size: 13px;
  color: var(--apple-text-secondary);
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--apple-border);
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.page-btn svg {
  width: 16px;
  height: 16px;
  color: var(--apple-text-secondary);
}

.page-btn:hover:not(:disabled) {
  background: var(--apple-bg-secondary);
  border-color: var(--apple-primary);
}

.page-btn:hover:not(:disabled) svg {
  color: var(--apple-primary);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-num {
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text);
}

@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
  }

  .filter-tabs {
    margin-bottom: 8px;
  }

  .filter-actions {
    margin-left: 0;
    width: 100%;
  }

  .record-card {
    flex-direction: column;
    gap: 12px;
  }

  .record-status {
    width: auto;
    align-self: flex-start;
  }
}
</style>

