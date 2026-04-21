<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'
import {
  labelAuditActionType,
  labelAuditNodeType,
  labelRecordStatus,
} from '../../utils/displayLabels'

type AuditMode = 'L1' | 'L2'
const props = defineProps<{ mode: AuditMode }>()

type ApiResponse<T> = { code: number; message: string; data: T }
type PageResult<T> = { total: number; list: T[] }
type Dept = { id: number; deptCode: string; deptName: string }
type Competition = { id: number; competitionName: string; enabled: number }
type AwardLevel = { id: number; awardScopeId: number; levelName: string; enabled: number }
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
  l2ReviewResult?: string
}
type AuditLog = {
  id: number
  nodeType: string
  actionType: string
  fromStatus: string
  toStatus: string
  commentText?: string
  createdAt?: string
  auditStage?: string
  auditorWorkNo?: string
  rejectTarget?: string
}

const router = useRouter()
const isL2 = computed(() => props.mode === 'L2')
const pageTitle = computed(() => (isL2.value ? '二级复审台' : '一级审核台'))
const pageSubtitle = computed(() =>
  isL2.value ? '仅复审一级已通过记录' : '按竞赛授权处理待审记录',
)
const operationTips = computed(() =>
  isL2.value
    ? '操作提示：二级复审只处理一级已通过记录；驳回会直接回到学生并通知一级审核人。'
    : '操作提示：一级审核只处理待审记录；请优先处理资料完整且时间较早的提交。',
)

const loading = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const rows = ref<TaskRow[]>([])
const query = reactive({
  status: isL2.value ? 'APPROVED' : 'PENDING_SCHOOL',
  deptId: undefined as number | undefined,
  competitionId: undefined as number | undefined,
  semester: '',
  keyword: '',
})

const depts = ref<Dept[]>([])
const competitions = ref<Competition[]>([])
const awardLevels = ref<AwardLevel[]>([])

const statusOptions = computed(() =>
  isL2.value
    ? [
        { value: 'APPROVED', label: '一级已通过' },
        { value: 'SCHOOL_REJECTED', label: '复审已驳回' },
      ]
    : [
        { value: 'PENDING_SCHOOL', label: '待一级审核' },
        { value: 'APPROVED', label: '一级已通过' },
        { value: 'SCHOOL_REJECTED', label: '一级已驳回' },
      ],
)

const rejectOpen = ref(false)
const rejectForm = reactive({ comment: '' })
const rejectId = ref<number | null>(null)

const detailOpen = ref(false)
const current = ref<TaskRow | null>(null)
const logsLoading = ref(false)
const logs = ref<AuditLog[]>([])
const l2RejectReasonByRecordId = ref<Record<number, string>>({})
const updatedAt = ref('')

const rejectQuickReasons = computed(() =>
  isL2.value
    ? ['证书信息与填报不一致', '获奖等级佐证不足', '团队成员信息有误', '请补充清晰附件后重新提交']
    : ['材料不完整', '获奖信息填写错误', '团队信息待核验', '请补充附件后重新提交'],
)

const stats = computed(() => {
  const pending = rows.value.filter((r) => r.status === 'PENDING_SCHOOL').length
  const approved = rows.value.filter((r) => r.status === 'APPROVED').length
  const rejected = rows.value.filter((r) => r.status === 'SCHOOL_REJECTED').length
  const l2Rejected = rows.value.filter(
    (r) => r.l2ReviewResult === 'REJECT' || l2RejectReasonByRecordId.value[r.id],
  ).length
  return { pending, approved, rejected, l2Rejected }
})

function resetStatusByMode() {
  query.status = isL2.value ? 'APPROVED' : 'PENDING_SCHOOL'
}

async function loadDepts() {
  const resp = await http.get<ApiResponse<Dept[]>>('/depts')
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  depts.value = resp.data.data
}
async function loadCompetitions() {
  const resp = await http.get<ApiResponse<PageResult<Competition>>>('/dicts/competitions', {
    params: { pageNo: 1, pageSize: 500, enabled: 1 },
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

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<TaskRow>>>('/audit/tasks', {
      params: {
        nodeType: props.mode,
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
    if (isL2.value) {
      await hydrateL2RejectReasons(resp.data.data.list)
    } else {
      l2RejectReasonByRecordId.value = {}
    }
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function quickSetStatus(status: string) {
  query.status = status
  page.pageNo = 1
  load()
}

async function hydrateL2RejectReasons(list: TaskRow[]) {
  const next: Record<number, string> = {}
  await Promise.all(
    list.map(async (row) => {
      try {
        const resp = await http.get<ApiResponse<AuditLog[]>>(`/audit/${row.id}/logs`)
        if (resp.data.code !== 0) {
          next[row.id] = '-'
          return
        }
        let reason = '-'
        for (let i = resp.data.data.length - 1; i >= 0; i--) {
          const item = resp.data.data[i]
          if (item.nodeType === 'L2' && item.actionType === 'REJECT' && item.commentText) {
            reason = item.commentText
            break
          }
        }
        next[row.id] = reason
      } catch {
        next[row.id] = '-'
      }
    }),
  )
  l2RejectReasonByRecordId.value = next
}

function competitionLabel(id: number) {
  return competitions.value.find((x) => x.id === id)?.competitionName ?? `竞赛 #${id}`
}
function awardLevelLabel(id?: number) {
  if (id == null) return '-'
  return awardLevels.value.find((x) => x.id === id)?.levelName ?? `等级 #${id}`
}
function formatDateCn(raw?: string) {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return String(raw).slice(0, 10)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}年${m}月${day}日`
}
function rejectTargetLabel(t?: string) {
  if (!t) return '-'
  if (t === 'STUDENT') return '学生'
  if (t === 'L1') return '一级审核人'
  return t
}

function hasL2RejectReason(id: number) {
  const v = l2RejectReasonByRecordId.value[id]
  return v != null && v !== '' && v !== '-'
}

function l2ResultLabel(v?: string) {
  if (!v) return '-'
  if (v === 'PENDING') return '待复审'
  if (v === 'PASS') return '复审通过'
  if (v === 'REJECT') return '复审驳回'
  return v
}

function l2ResultClass(v?: string) {
  if (v === 'PASS') return 'l2-result pass'
  if (v === 'REJECT') return 'l2-result reject'
  if (v === 'PENDING') return 'l2-result pending'
  return 'l2-result'
}

function goDetail(row: TaskRow) {
  router.push({
    path: `/admin/record/detail/${row.id}`,
    query: { from: isL2.value ? 'l2' : 'l1' },
  })
}

async function copyRecordId(id: number) {
  try {
    await navigator.clipboard.writeText(String(id))
    ElMessage.success(`记录ID ${id} 已复制`)
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
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

async function openDetail(row: TaskRow) {
  current.value = row
  detailOpen.value = true
  await loadLogs(row.id)
}

async function doL1Approve(id: number) {
  const resp = await http.post<ApiResponse<null>>(`/audit/${id}/school/approve`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
}
async function doL1Reject(id: number, comment: string) {
  const resp = await http.post<ApiResponse<null>>(`/audit/${id}/school/reject`, { comment })
  if (resp.data.code !== 0) throw new Error(resp.data.message)
}
async function doL2Pass(id: number) {
  const resp = await http.post<ApiResponse<null>>(`/audit/${id}/l2/pass`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
}
async function doL2Reject(id: number, comment: string) {
  const resp = await http.post<ApiResponse<null>>(`/audit/${id}/l2/reject-to-student`, { comment })
  if (resp.data.code !== 0) throw new Error(resp.data.message)
}

async function approve(id: number) {
  await ElMessageBox.confirm(
    isL2.value ? '确认复审通过该记录？' : '确认一级审核通过该记录？',
    '确认操作',
    { type: 'success' },
  )
  try {
    if (isL2.value) {
      await doL2Pass(id)
      ElMessage.success('复审通过成功')
    } else {
      await doL1Approve(id)
      ElMessage.success('审核通过成功')
    }
    await load()
    if (current.value?.id === id) await openDetail({ ...current.value })
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

function openReject(id: number) {
  rejectId.value = id
  rejectForm.comment = ''
  rejectOpen.value = true
}

function appendQuickReason(reason: string) {
  if (!reason) return
  if (!rejectForm.comment) {
    rejectForm.comment = reason
    return
  }
  if (!rejectForm.comment.includes(reason)) {
    rejectForm.comment = `${rejectForm.comment}；${reason}`
  }
}

async function submitReject() {
  if (rejectId.value == null) return
  try {
    if (isL2.value) {
      await doL2Reject(rejectId.value, rejectForm.comment || '复审未通过，请修改后重提')
      ElMessage.success('已驳回至学生')
    } else {
      await doL1Reject(rejectId.value, rejectForm.comment || '材料不完整')
      ElMessage.success('已驳回')
    }
    rejectOpen.value = false
    await load()
    if (current.value?.id === rejectId.value) await openDetail({ ...current.value })
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '驳回失败')
  }
}

function canApprove(row: TaskRow) {
  return row.status === 'PENDING_SCHOOL' || (isL2.value && row.status === 'APPROVED')
}
function canReject(row: TaskRow) {
  return row.status === 'PENDING_SCHOOL' || (isL2.value && row.status === 'APPROVED')
}

function tableRowClassName({ row }: { row: TaskRow }) {
  if (isL2.value && hasL2RejectReason(row.id)) {
    return 'row-has-l2-reject'
  }
  return ''
}

onMounted(async () => {
  resetStatusByMode()
  await Promise.all([loadDepts(), loadCompetitions(), loadAwardLevels()])
  await load()
})
</script>

<template>
  <div class="audit-page">
    <div class="page-header">
      <h1 class="page-title">{{ pageTitle }}</h1>
      <p class="page-subtitle">{{ pageSubtitle }}</p>
    </div>
    <el-alert :title="operationTips" type="info" :closable="false" show-icon class="tips-alert" />

    <div class="stats-bar">
      <el-tag type="warning" effect="light" class="stats-tag clickable" @click="quickSetStatus('PENDING_SCHOOL')">
        待处理 {{ stats.pending }}
      </el-tag>
      <el-tag type="success" effect="light" class="stats-tag clickable" @click="quickSetStatus('APPROVED')">
        已通过 {{ stats.approved }}
      </el-tag>
      <el-tag type="danger" effect="light" class="stats-tag clickable" @click="quickSetStatus('SCHOOL_REJECTED')">
        已驳回 {{ stats.rejected }}
      </el-tag>
      <el-tag v-if="isL2" type="danger" effect="dark" class="stats-tag">
        L2驳回记录 {{ stats.l2Rejected }}
      </el-tag>
      <span class="last-update">最近刷新：{{ updatedAt || '-' }}</span>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="query.status" @change="load">
        <el-radio-button v-for="s in statusOptions" :key="s.value" :label="s.value">
          {{ s.label }}
        </el-radio-button>
      </el-radio-group>
      <div class="filter-inline">
        <el-select v-model="query.deptId" clearable filterable placeholder="院系" class="w-140">
          <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-select v-model="query.competitionId" clearable filterable placeholder="竞赛" class="w-220">
          <el-option v-for="c in competitions" :key="c.id" :label="c.competitionName" :value="c.id" />
        </el-select>
        <el-input v-model="query.semester" placeholder="学期" class="w-120" />
        <el-input v-model="query.keyword" placeholder="项目关键字" class="w-180" />
        <el-button type="primary" @click="load">查询</el-button>
      </div>
    </div>

    <el-table :data="rows" border stripe v-loading="loading" :row-class-name="tableRowClassName">
      <el-table-column type="index" label="序号" width="70" />
      <el-table-column prop="projectName" label="项目名称" min-width="220" />
      <el-table-column label="比赛名称" min-width="180">
        <template #default="{ row }">{{ competitionLabel(row.competitionId) }}</template>
      </el-table-column>
      <el-table-column label="获奖等级" min-width="120">
        <template #default="{ row }">{{ awardLevelLabel(row.awardLevelId) }}</template>
      </el-table-column>
      <el-table-column label="获奖日期" min-width="120">
        <template #default="{ row }">{{ formatDateCn(row.awardDate) }}</template>
      </el-table-column>
      <el-table-column label="状态" min-width="120">
        <template #default="{ row }">{{ labelRecordStatus(row.status) }}</template>
      </el-table-column>
      <el-table-column v-if="isL2" label="复审结论" min-width="110">
        <template #default="{ row }">
          <span :class="l2ResultClass(row.l2ReviewResult)">{{ l2ResultLabel(row.l2ReviewResult) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isL2" label="L2驳回原因" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="reason-cell">
            <span v-if="hasL2RejectReason(row.id)" class="reason-dot"></span>
            {{ l2RejectReasonByRecordId[row.id] || '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="详情" width="90">
        <template #default="{ row }">
          <el-tooltip content="进入详情页查看团队和附件" placement="top">
            <el-button text @click="goDetail(row)">查看</el-button>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="审核记录" width="90">
        <template #default="{ row }"><el-button text @click="openDetail(row)">查看</el-button></template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <div class="op-actions">
            <el-tooltip content="复制记录ID" placement="top">
              <el-button size="small" text @click="copyRecordId(row.id)">复制ID</el-button>
            </el-tooltip>
            <el-tooltip :content="isL2 ? '确认复审通过，记录保持已通过' : '一级审核通过'" placement="top">
              <el-button v-if="canApprove(row)" size="small" type="success" @click="approve(row.id)">
                {{ isL2 ? '复审通过' : '通过' }}
              </el-button>
            </el-tooltip>
            <el-tooltip :content="isL2 ? '驳回会直接退回学生并通知一级审核人' : '驳回后由学生修改重提'" placement="top">
              <el-button v-if="canReject(row)" size="small" type="danger" @click="openReject(row.id)">
                {{ isL2 ? '驳回学生' : '驳回' }}
              </el-button>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
    </el-table>

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

    <el-drawer v-model="detailOpen" title="审核轨迹" size="45%">
      <div class="timeline" v-loading="logsLoading">
        <div v-for="l in logs" :key="l.id" class="timeline-item">
          <div class="title">{{ labelAuditNodeType(l.nodeType) }} / {{ labelAuditActionType(l.actionType) }}</div>
          <div class="desc">{{ labelRecordStatus(l.fromStatus) }} → {{ labelRecordStatus(l.toStatus) }}</div>
          <div class="meta">阶段：{{ l.auditStage || '-' }} ｜ 审核人工号：{{ l.auditorWorkNo || '-' }}</div>
          <div class="meta">驳回对象：{{ rejectTargetLabel(l.rejectTarget) }}</div>
          <div v-if="l.commentText" class="comment">意见：{{ l.commentText }}</div>
          <div class="time">{{ l.createdAt || '-' }}</div>
        </div>
        <p v-if="logs.length === 0 && !logsLoading" class="empty-text">暂无审核记录</p>
      </div>
    </el-drawer>

    <el-dialog v-model="rejectOpen" :title="isL2 ? '复审驳回至学生' : '一级审核驳回'" width="460px">
      <el-input
        v-model="rejectForm.comment"
        type="textarea"
        :rows="4"
        :placeholder="isL2 ? '请输入复审驳回原因（将通知学生和一级审核人）' : '请输入一级驳回原因'"
      />
      <div class="quick-reasons">
        <span class="quick-reason-label">常用原因：</span>
        <el-tag
          v-for="reason in rejectQuickReasons"
          :key="reason"
          class="quick-reason-item"
          effect="plain"
          @click="appendQuickReason(reason)"
        >
          {{ reason }}
        </el-tag>
      </div>
      <template #footer>
        <el-button @click="rejectOpen = false">取消</el-button>
        <el-button type="danger" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.audit-page { padding: 8px; }
.page-header { margin-bottom: 16px; }
.page-title { margin: 0; font-size: 24px; font-weight: 700; }
.page-subtitle { margin: 6px 0 0; color: var(--el-text-color-secondary); }
.tips-alert { margin-bottom: 10px; }
.stats-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.stats-tag { user-select: none; }
.clickable { cursor: pointer; }
.last-update {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-tertiary);
}
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-inline { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.w-140 { width: 140px; }
.w-220 { width: 220px; }
.w-120 { width: 120px; }
.w-180 { width: 180px; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 12px; }
:deep(.el-table .cell) {
  line-height: 1.2;
}
.op-actions {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  flex-wrap: nowrap;
}
:deep(.op-actions .el-button) {
  margin-left: 0 !important;
}
:deep(.el-table .row-has-l2-reject td) {
  background: rgba(245, 108, 108, 0.06);
}
.timeline-item {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.title { font-weight: 600; margin-bottom: 4px; }
.desc, .meta, .time, .comment { font-size: 13px; color: var(--el-text-color-secondary); margin-top: 3px; }
.comment { color: var(--el-text-color-primary); }
.empty-text { color: var(--el-text-color-tertiary); }
.reason-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.reason-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-color-danger);
  flex-shrink: 0;
}
.l2-result {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  border: 1px solid var(--el-border-color);
  color: var(--el-text-color-secondary);
}
.l2-result.pass {
  color: var(--el-color-success);
  border-color: var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
}
.l2-result.reject {
  color: var(--el-color-danger);
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
}
.l2-result.pending {
  color: var(--el-color-warning);
  border-color: var(--el-color-warning-light-5);
  background: var(--el-color-warning-light-9);
}
.quick-reasons {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.quick-reason-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.quick-reason-item {
  cursor: pointer;
}
</style>
