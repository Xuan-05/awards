<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '../api/http'
import { ElMessage } from 'element-plus'
import {
  labelAuditActionType,
  labelAuditNodeType,
  labelRecordStatus,
} from '../utils/displayLabels'

type ApiResponse<T> = { code: number; message: string; data: T }
type PageResult<T> = { total: number; list: T[] }

type Dept = { id: number; deptCode: string; deptName: string }
type Competition = { id: number; competitionName: string; enabled: number }
type AwardLevel = { id: number; awardScopeId: number; levelName: string; enabled: number }

/** 与后端 BizAwardRecord 对齐（审核列表返回完整实体） */
type TaskRow = {
  id: number
  teamId: number
  competitionId: number
  ownerDeptId: number
  status: string
  awardLevelId?: number
  awardDate?: string
  semester?: string
  projectName?: string
  submitTime?: string
}
type AuditLog = { id: number; nodeType: string; actionType: string; fromStatus: string; toStatus: string; commentText?: string; auditorUserId?: number; createdAt?: string }
type RecordFileRel = { recordId: number; fileId: number; createdAt?: string; fileName?: string }

const router = useRouter()
const loading = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const rows = ref<TaskRow[]>([])

const query = reactive({
  status: 'PENDING_SCHOOL',
  deptId: undefined as number | undefined,
  competitionId: undefined as number | undefined,
  semester: '',
  keyword: '',
})

const depts = ref<Dept[]>([])
const competitions = ref<Competition[]>([])
const awardLevels = ref<AwardLevel[]>([])

/** 当前列表页每条记录的附件（供卡片展示） */
const cardFilesByRecordId = ref<Record<number, RecordFileRel[]>>({})
const cardFilesHydrating = ref(false)

async function loadDepts() {
  const resp = await http.get<ApiResponse<Dept[]>>('/depts')
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  depts.value = resp.data.data
}

async function loadCompetitions() {
  const resp = await http.get<ApiResponse<PageResult<Competition>>>('/dicts/competitions', {
    params: { pageNo: 1, pageSize: 200, enabled: 1 },
  })
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  competitions.value = resp.data.data.list
}

async function loadAwardLevels() {
  const resp = await http.get<ApiResponse<PageResult<AwardLevel>>>('/dicts/award-levels', {
    params: { pageNo: 1, pageSize: 500, enabled: 1 },
  })
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  awardLevels.value = resp.data.data.list
}

function competitionLabel(competitionId: number) {
  const c = competitions.value.find((x) => x.id === competitionId)
  return c?.competitionName ?? `竞赛 #${competitionId}`
}

function awardLevelLabel(awardLevelId?: number) {
  if (awardLevelId == null) return '-'
  const lv = awardLevels.value.find((x) => x.id === awardLevelId)
  return lv?.levelName ?? `等级 #${awardLevelId}`
}

function formatAwardDate(raw?: string) {
  if (raw == null || raw === '') return '-'
  return String(raw).slice(0, 10)
}

function attachmentDisplayName(f: RecordFileRel) {
  if (f.fileName != null && f.fileName !== '') return f.fileName
  return `文件 #${f.fileId}`
}

async function hydrateCardFiles(list: TaskRow[]) {
  cardFilesHydrating.value = true
  const next: Record<number, RecordFileRel[]> = {}
  try {
    await Promise.all(
      list.map(async (r) => {
        try {
          const resp = await http.get<ApiResponse<RecordFileRel[]>>(`/award-records/${r.id}/files`)
          next[r.id] = resp.data.code === 0 ? resp.data.data : []
        } catch {
          next[r.id] = []
        }
      }),
    )
  } finally {
    cardFilesByRecordId.value = next
    cardFilesHydrating.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<TaskRow>>>('/audit/tasks', {
      params: {
        nodeType: 'SCHOOL',
        status: query.status,
        deptId: query.deptId,
        competitionId: query.competitionId,
        semester: query.semester || undefined,
        keyword: query.keyword || undefined,
        pageNo: page.pageNo,
        pageSize: page.pageSize,
      },
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    page.total = resp.data.data.total
    rows.value = resp.data.data.list
    await hydrateCardFiles(resp.data.data.list)
  } finally {
    loading.value = false
  }
}

function goDetail(row: TaskRow) {
  router.push(`/admin/record/detail/${row.id}`)
}

const detailOpen = ref(false)
const current = ref<TaskRow | null>(null)
const logsLoading = ref(false)
const logs = ref<AuditLog[]>([])
const filesLoading = ref(false)
const files = ref<RecordFileRel[]>([])

async function openDetail(row: TaskRow) {
  current.value = row
  detailOpen.value = true
  await Promise.all([loadLogs(row.id), loadFiles(row.id)])
}

async function loadLogs(recordId: number) {
  logsLoading.value = true
  try {
    const resp = await http.get<ApiResponse<AuditLog[]>>(`/audit/${recordId}/logs`)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    logs.value = resp.data.data
  } finally {
    logsLoading.value = false
  }
}

async function loadFiles(recordId: number) {
  filesLoading.value = true
  try {
    const resp = await http.get<ApiResponse<RecordFileRel[]>>(`/award-records/${recordId}/files`)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    files.value = resp.data.data
  } finally {
    filesLoading.value = false
  }
}

async function approve(id: number) {
  const resp = await http.post<ApiResponse<null>>(`/audit/${id}/school/approve`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  ElMessage.success('审核通过')
  await load()
  if (current.value?.id === id) await openDetail({ ...current.value, status: 'APPROVED' })
}

const rejectOpen = ref(false)
const rejectForm = reactive({ comment: '' })
let rejectRecordId: number | null = null

function openReject(id: number) {
  rejectRecordId = id
  rejectForm.comment = ''
  rejectOpen.value = true
}

async function doReject() {
  if (!rejectRecordId) return
  const resp = await http.post<ApiResponse<null>>(`/audit/${rejectRecordId}/school/reject`, { comment: rejectForm.comment || '材料不完整' })
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  ElMessage.success('已驳回')
  rejectOpen.value = false
  await load()
  if (current.value?.id === rejectRecordId) await openDetail({ ...current.value, status: 'SCHOOL_REJECTED' })
}

function preview(fileId: number) {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录')
    return
  }
  window.open(`/api/files/${fileId}/preview?Authorization=${encodeURIComponent(token)}`)
}
function download(fileId: number) {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录')
    return
  }
  window.open(`/api/files/${fileId}/download?Authorization=${encodeURIComponent(token)}`)
}

onMounted(async () => {
  await Promise.all([loadDepts(), loadCompetitions(), loadAwardLevels()])
  await load()
})
</script>

<template>
  <div class="audit-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">审核管理</h1>
      <p class="page-subtitle">校级审核工作台</p>
    </div>

    <!-- 状态切换 -->
    <div class="status-tabs">
      <div 
        class="status-tab" 
        :class="{ active: query.status === 'PENDING_SCHOOL' }"
        @click="query.status = 'PENDING_SCHOOL'; load()"
      >
        <div class="status-tab-icon pending">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12,6 12,12 16,14"/>
          </svg>
        </div>
        <span class="status-tab-label">待校级审核</span>
      </div>
      <div 
        class="status-tab" 
        :class="{ active: query.status === 'APPROVED' }"
        @click="query.status = 'APPROVED'; load()"
      >
        <div class="status-tab-icon approved">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22,4 12,14.01 9,11.01"/>
          </svg>
        </div>
        <span class="status-tab-label">审核通过</span>
      </div>
      <div 
        class="status-tab" 
        :class="{ active: query.status === 'SCHOOL_REJECTED' }"
        @click="query.status = 'SCHOOL_REJECTED'; load()"
      >
        <div class="status-tab-icon rejected">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="15" y1="9" x2="9" y2="15"/>
            <line x1="9" y1="9" x2="15" y2="15"/>
          </svg>
        </div>
        <span class="status-tab-label">校级驳回</span>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <div class="filter-group">
        <el-select v-model="query.deptId" clearable filterable placeholder="院系" class="filter-select">
          <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-select v-model="query.competitionId" clearable filterable placeholder="竞赛" class="filter-select-wide">
          <el-option v-for="c in competitions" :key="c.id" :label="c.competitionName" :value="c.id" />
        </el-select>
        <el-input v-model="query.semester" placeholder="学期 如 2025-2026-1" class="filter-input" />
        <el-input v-model="query.keyword" placeholder="项目名称关键字" class="filter-input">
          <template #prefix>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 14px; height: 14px;">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
          </template>
        </el-input>
      </div>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <!-- 任务列表 -->
    <div class="task-list" v-loading="loading">
      <div v-for="row in rows" :key="row.id" class="task-card">
        <div class="task-main">
          <div class="task-header">
            <span class="task-id">#{{ row.id }}</span>
            <span class="task-semester">{{ row.semester || '-' }}</span>
          </div>
          <h3 class="task-project">{{ row.projectName || '未命名项目' }}</h3>
          <div class="task-detail-grid">
            <div class="task-detail-item">
              <span class="task-detail-label">竞赛名称</span>
              <span class="task-detail-value">{{ competitionLabel(row.competitionId) }}</span>
            </div>
            <div class="task-detail-item">
              <span class="task-detail-label">获奖等级</span>
              <span class="task-detail-value">{{ awardLevelLabel(row.awardLevelId) }}</span>
            </div>
            <div class="task-detail-item">
              <span class="task-detail-label">获奖日期</span>
              <span class="task-detail-value">{{ formatAwardDate(row.awardDate) }}</span>
            </div>
            <div class="task-detail-item">
              <span class="task-detail-label">学期</span>
              <span class="task-detail-value">{{ row.semester || '-' }}</span>
            </div>
          </div>
          <div class="task-meta">
            <span class="task-meta-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
              </svg>
              团队 {{ row.teamId }}
            </span>
            <span class="task-meta-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                <line x1="16" y1="2" x2="16" y2="6"/>
                <line x1="8" y1="2" x2="8" y2="6"/>
                <line x1="3" y1="10" x2="21" y2="10"/>
              </svg>
              提交 {{ row.submitTime?.slice(0, 10) || '-' }}
            </span>
          </div>
          <div class="task-attachments">
            <span class="task-attachments-label">附件</span>
            <div class="task-attachments-inner">
              <span v-if="cardFilesHydrating" class="task-attachments-hint">加载中…</span>
              <template v-else>
                <template v-if="(cardFilesByRecordId[row.id] || []).length === 0">
                  <span class="task-attachments-empty">暂无附件</span>
                </template>
                <template v-else>
                  <button
                    v-for="f in cardFilesByRecordId[row.id]"
                    :key="f.fileId"
                    type="button"
                    class="attachment-name-link"
                    @click.stop="preview(f.fileId)"
                  >
                    {{ attachmentDisplayName(f) }}
                  </button>
                </template>
              </template>
            </div>
          </div>
        </div>
        <div class="task-side">
          <div class="task-actions" @click.stop>
            <template v-if="row.status === 'PENDING_SCHOOL'">
              <el-button size="small" @click="goDetail(row)">详情</el-button>
              <el-button type="success" size="small" @click="approve(row.id)">通过</el-button>
              <el-button type="danger" size="small" @click="openReject(row.id)">驳回</el-button>
            </template>
            <template v-else>
              <el-button size="small" @click="goDetail(row)">详情</el-button>
              <el-button size="small" @click="openDetail(row)">审核记录</el-button>
              <span :class="['status-badge', row.status === 'APPROVED' ? 'approved' : 'rejected']">
                {{ labelRecordStatus(row.status) }}
              </span>
            </template>
          </div>
        </div>
      </div>

      <div v-if="rows.length === 0 && !loading" class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
        </svg>
        <p>暂无审核任务</p>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination-bar">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="page.total"
        v-model:current-page="page.pageNo"
        v-model:page-size="page.pageSize"
        @change="load"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailOpen" title="审核详情" size="50%" class="detail-drawer">
      <div class="detail-section" v-if="current">
        <div class="detail-header">
          <h3>{{ current.projectName || '未命名项目' }}</h3>
          <span :class="['detail-status', current.status === 'APPROVED' ? 'approved' : current.status === 'SCHOOL_REJECTED' ? 'rejected' : 'pending']">
            {{ labelRecordStatus(current.status) }}
          </span>
        </div>

        <div class="detail-info">
          <div class="info-item">
            <span class="info-label">记录ID</span>
            <span class="info-value">{{ current.id }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">团队ID</span>
            <span class="info-value">{{ current.teamId }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">竞赛ID</span>
            <span class="info-value">{{ current.competitionId }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">学期</span>
            <span class="info-value">{{ current.semester || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">提交时间</span>
            <span class="info-value">{{ current.submitTime || '-' }}</span>
          </div>
        </div>

        <!-- 附件 -->
        <div class="detail-block">
          <h4>附件材料</h4>
          <div class="file-list" v-loading="filesLoading">
            <div v-for="f in files" :key="f.fileId" class="file-item">
              <span class="file-id">{{ attachmentDisplayName(f) }}</span>
              <div class="file-actions">
                <el-button size="small" text @click="preview(f.fileId)">预览</el-button>
                <el-button size="small" text @click="download(f.fileId)">下载</el-button>
              </div>
            </div>
            <p v-if="files.length === 0" class="empty-text">暂无附件</p>
          </div>
        </div>

        <!-- 审核轨迹 -->
        <div class="detail-block">
          <h4>审核轨迹</h4>
          <div class="timeline" v-loading="logsLoading">
            <div v-for="l in logs" :key="l.id" class="timeline-item">
              <div class="timeline-dot"></div>
              <div class="timeline-content">
                <div class="timeline-header">
                  <span class="timeline-action">{{ labelAuditNodeType(l.nodeType) }} / {{ labelAuditActionType(l.actionType) }}</span>
                  <span class="timeline-time">{{ l.createdAt }}</span>
                </div>
                <div class="timeline-status">
                  {{ labelRecordStatus(l.fromStatus) }} → {{ labelRecordStatus(l.toStatus) }}
                </div>
                <div v-if="l.commentText" class="timeline-comment">
                  意见：{{ l.commentText }}
                </div>
              </div>
            </div>
            <p v-if="logs.length === 0" class="empty-text">暂无审核记录</p>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="detail-actions" v-if="current.status === 'PENDING_SCHOOL'">
          <el-button type="success" @click="approve(current.id)">通过</el-button>
          <el-button type="danger" @click="openReject(current.id)">驳回</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectOpen" title="驳回申请" width="480px" class="reject-dialog">
      <div class="reject-form">
        <label>驳回原因</label>
        <el-input 
          v-model="rejectForm.comment" 
          type="textarea" 
          :rows="4" 
          placeholder="请输入驳回原因，将返回给学生修改"
        />
      </div>
      <template #footer>
        <el-button @click="rejectOpen = false">取消</el-button>
        <el-button type="danger" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.audit-page {
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

/* Status Tabs */
.status-tabs {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.status-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border-radius: var(--apple-radius);
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.status-tab:hover {
  background: var(--apple-bg-secondary);
}

.status-tab.active {
  background: var(--apple-bg-secondary);
  border-color: var(--apple-primary);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.15);
}

.status-tab-icon {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-tab-icon svg {
  width: 14px;
  height: 14px;
}

.status-tab-icon.pending {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.status-tab-icon.approved {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.status-tab-icon.rejected {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.status-tab-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text);
}

/* Filter Bar */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  margin-bottom: 16px;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.filter-select {
  width: 160px;
}

.filter-select-wide {
  width: 220px;
}

.filter-input {
  width: 180px;
}

/* Task List */
.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 400px;
}

.task-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  transition: background 0.2s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.task-card:hover {
  background: var(--apple-glass-hover);
  box-shadow: var(--apple-shadow-md);
}

.task-main {
  flex: 1;
  min-width: 0;
}

.task-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.task-id {
  font-size: 12px;
  font-weight: 600;
  color: var(--apple-text-secondary);
  background: var(--apple-bg-secondary);
  padding: 2px 8px;
  border-radius: 4px;
}

.task-semester {
  font-size: 12px;
  color: var(--apple-text-tertiary);
}

.task-project {
  font-size: 16px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 10px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 20px;
  margin-bottom: 10px;
}

.task-detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.task-detail-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--apple-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.task-detail-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-attachments {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--apple-border);
}

.task-attachments-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--apple-text-secondary);
}

.task-attachments-inner {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  min-width: 0;
}

.task-attachments-hint,
.task-attachments-empty {
  font-size: 13px;
  color: var(--apple-text-tertiary);
}

.attachment-name-link {
  background: none;
  border: none;
  padding: 0;
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-primary);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
  text-align: left;
}

.attachment-name-link:hover {
  opacity: 0.85;
}

.task-side {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 16px;
}

.task-meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--apple-text-secondary);
}

.task-meta-item svg {
  width: 14px;
  height: 14px;
  opacity: 0.6;
}

.task-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.status-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 6px;
}

.status-badge.approved {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.status-badge.rejected {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--apple-text-tertiary);
}

.empty-state svg {
  width: 48px;
  height: 48px;
  margin-bottom: 16px;
  opacity: 0.4;
}

.empty-state p {
  font-size: 14px;
  margin: 0;
}

/* Pagination */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* Detail Drawer */
.detail-section {
  padding: 0 8px;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--apple-border);
}

.detail-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0;
}

.detail-status {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 6px;
}

.detail-status.pending {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.detail-status.approved {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.detail-status.rejected {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.detail-info {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: var(--apple-text-secondary);
}

.info-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text);
}

.detail-block {
  margin-bottom: 24px;
}

.detail-block h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 12px 0;
}

.file-list {
  background: var(--apple-bg-secondary);
  border-radius: var(--apple-radius);
  padding: 12px;
}

.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 6px;
  transition: background 0.15s;
}

.file-item:hover {
  background: var(--apple-bg-tertiary);
}

.file-id {
  font-size: 13px;
  color: var(--apple-text);
}

.file-actions {
  display: flex;
  gap: 4px;
}

.empty-text {
  font-size: 13px;
  color: var(--apple-text-tertiary);
  text-align: center;
  margin: 0;
  padding: 16px;
}

/* Timeline */
.timeline {
  position: relative;
  padding-left: 24px;
}

.timeline-item {
  position: relative;
  padding-bottom: 20px;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-dot {
  position: absolute;
  left: -24px;
  top: 4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--apple-primary);
  border: 2px solid var(--apple-bg);
  box-shadow: 0 0 0 2px var(--apple-primary);
}

.timeline-item::before {
  content: '';
  position: absolute;
  left: -19px;
  top: 14px;
  bottom: 0;
  width: 2px;
  background: var(--apple-border);
}

.timeline-item:last-child::before {
  display: none;
}

.timeline-content {
  background: var(--apple-bg-secondary);
  border-radius: var(--apple-radius);
  padding: 12px 16px;
}

.timeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.timeline-action {
  font-size: 13px;
  font-weight: 600;
  color: var(--apple-text);
}

.timeline-time {
  font-size: 11px;
  color: var(--apple-text-tertiary);
}

.timeline-status {
  font-size: 12px;
  color: var(--apple-text-secondary);
  margin-bottom: 4px;
}

.timeline-comment {
  font-size: 12px;
  color: var(--apple-text-secondary);
  background: var(--apple-bg-tertiary);
  padding: 8px 12px;
  border-radius: 6px;
  margin-top: 8px;
}

.detail-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--apple-border);
}

/* Reject Dialog */
.reject-form label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text);
  margin-bottom: 8px;
}
</style>


