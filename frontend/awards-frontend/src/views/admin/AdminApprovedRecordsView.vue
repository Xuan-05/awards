<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
import type { ApiResponse, PageResult } from '../../types/api'
import { labelRecordStatus } from '../../utils/displayLabels'

type Dept = { id: number; deptName: string }
type Competition = { id: number; competitionName: string; categoryId: number; enabled: number }
type Category = { id: number; categoryName: string; enabled: number }
type AwardLevel = { id: number; awardScopeId: number; levelName: string; enabled: number }
type AwardScope = { id: number; scopeName: string; enabled: number }
type Row = {
  id: number
  projectName?: string
  ownerDeptId?: number
  competitionId?: number
  competitionCategoryId?: number
  awardScopeId?: number
  awardLevelId?: number
  semester?: string
  awardDate?: string
  submitTime?: string
  finalAuditTime?: string
  status: string
  l2ReviewFlag?: number
  l2ReviewResult?: string
}

const router = useRouter()
const loading = ref(false)
const rows = ref<Row[]>([])
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })

const depts = ref<Dept[]>([])
const competitions = ref<Competition[]>([])
const categories = ref<Category[]>([])
const awardLevels = ref<AwardLevel[]>([])
const awardScopes = ref<AwardScope[]>([])

const query = reactive({
  recordId: undefined as number | undefined,
  keyword: '',
  deptId: undefined as number | undefined,
  competitionId: undefined as number | undefined,
  competitionCategoryId: undefined as number | undefined,
  awardScopeId: undefined as number | undefined,
  awardLevelId: undefined as number | undefined,
  semester: '',
  awardDateRange: [] as string[],
  submitTimeRange: [] as string[],
  approvedTimeRange: [] as string[],
  l2ReviewFlag: undefined as number | undefined,
  l2ReviewResult: '' as '' | 'PENDING' | 'PASS' | 'REJECT',
})

const compNameById = computed(() => {
  const m = new Map<number, string>()
  for (const x of competitions.value) m.set(x.id, x.competitionName)
  return m
})
const deptNameById = computed(() => {
  const m = new Map<number, string>()
  for (const x of depts.value) m.set(x.id, x.deptName)
  return m
})
const catNameById = computed(() => {
  const m = new Map<number, string>()
  for (const x of categories.value) m.set(x.id, x.categoryName)
  return m
})
const scopeNameById = computed(() => {
  const m = new Map<number, string>()
  for (const x of awardScopes.value) m.set(x.id, x.scopeName)
  return m
})
const levelNameById = computed(() => {
  const m = new Map<number, string>()
  for (const x of awardLevels.value) m.set(x.id, x.levelName)
  return m
})

function fmtDateTime(raw?: string) {
  if (!raw) return '-'
  return raw.replace('T', ' ').slice(0, 19)
}
function l2ResultLabel(v?: string) {
  if (!v) return '-'
  if (v === 'PENDING') return '待复审'
  if (v === 'PASS') return '复审通过'
  if (v === 'REJECT') return '复审驳回'
  return v
}
function l2FlagLabel(v?: number) {
  if (v === 1) return '已复审'
  return '未复审'
}

function buildParams() {
  return {
    recordId: query.recordId,
    keyword: query.keyword || undefined,
    deptId: query.deptId,
    competitionId: query.competitionId,
    competitionCategoryId: query.competitionCategoryId,
    awardScopeId: query.awardScopeId,
    awardLevelId: query.awardLevelId,
    semester: query.semester || undefined,
    awardDateStart: query.awardDateRange?.[0] || undefined,
    awardDateEnd: query.awardDateRange?.[1] || undefined,
    submitTimeStart: query.submitTimeRange?.[0] || undefined,
    submitTimeEnd: query.submitTimeRange?.[1] || undefined,
    approvedTimeStart: query.approvedTimeRange?.[0] || undefined,
    approvedTimeEnd: query.approvedTimeRange?.[1] || undefined,
    l2ReviewFlag: query.l2ReviewFlag,
    l2ReviewResult: query.l2ReviewResult || undefined,
    pageNo: page.pageNo,
    pageSize: page.pageSize,
    sortBy: 'finalAuditTime',
    sortOrder: 'desc',
  }
}

async function loadRows() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<Row>>>('/admin/records/approved', {
      params: buildParams(),
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    rows.value = resp.data.data.list
    page.total = resp.data.data.total
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadDicts() {
  const [deptResp, compResp, catResp, scopeResp, levelResp] = await Promise.all([
    http.get<ApiResponse<Dept[]>>('/depts'),
    http.get<ApiResponse<PageResult<Competition>>>('/dicts/competitions', { params: { pageNo: 1, pageSize: 500, enabled: 1 } }),
    http.get<ApiResponse<PageResult<Category>>>('/dicts/categories', { params: { pageNo: 1, pageSize: 500, enabled: 1 } }),
    http.get<ApiResponse<PageResult<AwardScope>>>('/dicts/award-scopes', { params: { pageNo: 1, pageSize: 500, enabled: 1 } }),
    http.get<ApiResponse<PageResult<AwardLevel>>>('/dicts/award-levels', { params: { pageNo: 1, pageSize: 500, enabled: 1 } }),
  ])
  if (deptResp.data.code !== 0) throw new Error(deptResp.data.message)
  if (compResp.data.code !== 0) throw new Error(compResp.data.message)
  if (catResp.data.code !== 0) throw new Error(catResp.data.message)
  if (scopeResp.data.code !== 0) throw new Error(scopeResp.data.message)
  if (levelResp.data.code !== 0) throw new Error(levelResp.data.message)
  depts.value = deptResp.data.data
  competitions.value = compResp.data.data.list
  categories.value = catResp.data.data.list
  awardScopes.value = scopeResp.data.data.list
  awardLevels.value = levelResp.data.data.list
}

async function search() {
  page.pageNo = 1
  await loadRows()
}

async function resetQuery() {
  query.recordId = undefined
  query.keyword = ''
  query.deptId = undefined
  query.competitionId = undefined
  query.competitionCategoryId = undefined
  query.awardScopeId = undefined
  query.awardLevelId = undefined
  query.semester = ''
  query.awardDateRange = []
  query.submitTimeRange = []
  query.approvedTimeRange = []
  query.l2ReviewFlag = undefined
  query.l2ReviewResult = ''
  page.pageNo = 1
  await loadRows()
}

function goDetail(row: Row) {
  router.push({ path: `/admin/record/detail/${row.id}`, query: { from: 'approved' } })
}

onMounted(async () => {
  try {
    await loadDicts()
    await loadRows()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '初始化失败')
  }
})
</script>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <h1 class="page-title">竞赛填报信息</h1>
      <p class="page-subtitle">仅展示已通过（APPROVED）的竞赛填报记录，支持多维筛选</p>
    </div>

    <el-alert
      title="可按记录、竞赛、分类、等级、时间范围与复审状态进行组合检索。"
      type="info"
      show-icon
      :closable="false"
      class="tip"
    />

    <div class="query-panel">
      <div class="query-section-title">基础筛选</div>
      <div class="query-grid grid-basic">
        <div class="query-item">
          <span class="item-label">记录ID</span>
          <el-input v-model.number="query.recordId" clearable placeholder="输入记录ID" />
        </div>
        <div class="query-item">
          <span class="item-label">项目关键字</span>
          <el-input v-model="query.keyword" clearable placeholder="输入项目关键字" />
        </div>
        <div class="query-item">
          <span class="item-label">院系</span>
          <el-select v-model="query.deptId" clearable filterable placeholder="全部院系">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </div>
        <div class="query-item">
          <span class="item-label">竞赛</span>
          <el-select v-model="query.competitionId" clearable filterable placeholder="全部竞赛">
            <el-option v-for="c in competitions" :key="c.id" :label="c.competitionName" :value="c.id" />
          </el-select>
        </div>
        <div class="query-item">
          <span class="item-label">竞赛分类</span>
          <el-select v-model="query.competitionCategoryId" clearable filterable placeholder="全部分类">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </div>
        <div class="query-item">
          <span class="item-label">获奖范围</span>
          <el-select v-model="query.awardScopeId" clearable filterable placeholder="全部范围">
            <el-option v-for="s in awardScopes" :key="s.id" :label="s.scopeName" :value="s.id" />
          </el-select>
        </div>
        <div class="query-item">
          <span class="item-label">获奖等级</span>
          <el-select v-model="query.awardLevelId" clearable filterable placeholder="全部等级">
            <el-option v-for="l in awardLevels" :key="l.id" :label="l.levelName" :value="l.id" />
          </el-select>
        </div>
        <div class="query-item">
          <span class="item-label">学期</span>
          <el-input v-model="query.semester" clearable placeholder="如 2025-2026-1" />
        </div>
      </div>

      <div class="query-section-title">时间筛选</div>
      <div class="query-grid grid-time">
        <div class="query-item">
          <span class="item-label">获奖日期范围</span>
          <el-date-picker
            v-model="query.awardDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </div>
        <div class="query-item">
          <span class="item-label">提交时间范围</span>
          <el-date-picker
            v-model="query.submitTimeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </div>
        <div class="query-item">
          <span class="item-label">通过时间范围</span>
          <el-date-picker
            v-model="query.approvedTimeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </div>
      </div>

      <div class="query-bottom">
        <div class="query-grid grid-review">
          <div class="query-item">
            <span class="item-label">复审状态</span>
            <el-select v-model="query.l2ReviewFlag" clearable placeholder="全部状态">
              <el-option label="未复审" :value="0" />
              <el-option label="已复审" :value="1" />
            </el-select>
          </div>
          <div class="query-item">
            <span class="item-label">复审结论</span>
            <el-select v-model="query.l2ReviewResult" clearable placeholder="全部结论">
              <el-option label="待复审" value="PENDING" />
              <el-option label="复审通过" value="PASS" />
              <el-option label="复审驳回" value="REJECT" />
            </el-select>
          </div>
        </div>

        <div class="actions">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </div>
      </div>
    </div>

    <el-table :data="rows" border stripe v-loading="loading">
      <el-table-column prop="id" label="记录ID" width="90" />
      <el-table-column prop="projectName" label="项目名称" min-width="220" show-overflow-tooltip />
      <el-table-column label="院系" min-width="130">
        <template #default="{ row }">{{ deptNameById.get(row.ownerDeptId) || '-' }}</template>
      </el-table-column>
      <el-table-column label="竞赛" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ compNameById.get(row.competitionId) || '-' }}</template>
      </el-table-column>
      <el-table-column label="竞赛分类" min-width="120">
        <template #default="{ row }">{{ catNameById.get(row.competitionCategoryId) || '-' }}</template>
      </el-table-column>
      <el-table-column label="获奖范围" min-width="110">
        <template #default="{ row }">{{ scopeNameById.get(row.awardScopeId) || '-' }}</template>
      </el-table-column>
      <el-table-column label="获奖等级" min-width="120">
        <template #default="{ row }">{{ levelNameById.get(row.awardLevelId) || '-' }}</template>
      </el-table-column>
      <el-table-column prop="semester" label="学期" width="130" />
      <el-table-column prop="awardDate" label="获奖日期" width="120" />
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ fmtDateTime(row.submitTime) }}</template>
      </el-table-column>
      <el-table-column label="通过时间" width="170">
        <template #default="{ row }">{{ fmtDateTime(row.finalAuditTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ labelRecordStatus(row.status) }}</template>
      </el-table-column>
      <el-table-column label="复审状态" width="100">
        <template #default="{ row }">{{ l2FlagLabel(row.l2ReviewFlag) }}</template>
      </el-table-column>
      <el-table-column label="复审结论" width="100">
        <template #default="{ row }">{{ l2ResultLabel(row.l2ReviewResult) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="goDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="total, prev, pager, next, sizes"
        :total="page.total"
        v-model:current-page="page.pageNo"
        v-model:page-size="page.pageSize"
        @current-change="loadRows"
        @size-change="loadRows"
      />
    </div>
  </div>
</template>

<style scoped>
.page-wrap { padding: 8px; }
.page-header { margin-bottom: 12px; }
.page-title { margin: 0; font-size: 24px; font-weight: 700; }
.page-subtitle { margin: 6px 0 0; color: var(--el-text-color-secondary); }
.tip { margin-bottom: 12px; }
.query-panel {
  padding: 14px;
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: #fff;
}
.query-section-title {
  margin: 2px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}
.query-grid {
  display: grid;
  gap: 10px;
}
.grid-basic {
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  margin-bottom: 8px;
}
.grid-time {
  grid-template-columns: repeat(3, minmax(260px, 1fr));
}
.grid-review {
  grid-template-columns: repeat(2, minmax(180px, 1fr));
}
.query-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.item-label {
  font-size: 12px;
  line-height: 1;
  color: var(--el-text-color-secondary);
}
.query-bottom {
  margin-top: 10px;
  display: flex;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
}
.actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
:deep(.query-item .el-select),
:deep(.query-item .el-date-editor.el-input__wrapper),
:deep(.query-item .el-date-editor.el-range-editor),
:deep(.query-item .el-input) {
  width: 100%;
}

@media (max-width: 1500px) {
  .grid-basic {
    grid-template-columns: repeat(3, minmax(180px, 1fr));
  }
  .grid-time {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1100px) {
  .grid-basic,
  .grid-review {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
  .query-bottom {
    flex-direction: column;
    align-items: stretch;
  }
  .actions {
    justify-content: flex-end;
  }
}
</style>
