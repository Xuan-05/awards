<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue"
import { http } from "../../api/http"
import { useUserStore } from "../../stores/user"
import type { ApiResponse, PageResult } from "../../types/api"
import type { Team, TeamMember, TeamTeacher } from "../../types/team"
import { labelInviteStatus, labelTeamStatus, labelUserType } from "../../utils/displayLabels"

type Dept = { id: number; deptCode: string; deptName: string; parentId?: number | null; enabled: number; sortNo: number }
type DeptTreeNode = Dept & { children: DeptTreeNode[] }
type TeamAdminRow = {
  id: number
  teamName: string
  captainUserId: number
  captainRealName: string
  ownerDeptId: number
  memberCount: number
  teacherCount: number
  status: string
  createdAt: string
}

const userStore = useUserStore()
const deptsLoading = ref(false)
const depts = ref<Dept[]>([])
const selectedDeptId = ref<number | undefined>(undefined)
const deptTree = computed<DeptTreeNode[]>(() => {
  const byParent: Record<string, Dept[]> = {}
  for (const d of depts.value) {
    const key = String(d.parentId ?? 0)
    if (!byParent[key]) byParent[key] = []
    byParent[key].push(d)
  }
  for (const k of Object.keys(byParent)) {
    byParent[k].sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0) || a.id - b.id)
  }
  const build = (parentId: number | null): DeptTreeNode[] => {
    const key = String(parentId ?? 0)
    return (byParent[key] || []).map((d) => ({ ...d, children: build(d.id) }))
  }
  return build(null)
})
const deptNameById = computed(() => {
  const m: Record<number, string> = {}
  for (const d of depts.value) m[d.id] = d.deptName
  return m
})
const loading = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const query = reactive({ teamName: "", captainRealName: "" })
const rows = ref<TeamAdminRow[]>([])

async function loadDepts() {
  deptsLoading.value = true
  try {
    const resp = await http.get<ApiResponse<Dept[]>>("/depts")
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    depts.value = resp.data.data
  } finally {
    deptsLoading.value = false
  }
}

async function loadTeams() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<TeamAdminRow>>>("/admin/teams", {
      params: {
        pageNo: page.pageNo,
        pageSize: page.pageSize,
        teamName: query.teamName || undefined,
        captainRealName: query.captainRealName || undefined,
        ownerDeptId: selectedDeptId.value,
      },
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    rows.value = resp.data.data.list
    page.total = resp.data.data.total
  } finally {
    loading.value = false
  }
}

function formatDt(s?: string) {
  if (!s) return "-"
  return s.replace("T", " ").slice(0, 19)
}

function onDeptNodeClick(data: { id: number }) {
  selectedDeptId.value = data.id && data.id !== 0 ? Number(data.id) : undefined
  page.pageNo = 1
  loadTeams()
}

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailTeam = ref<Team | null>(null)
const detailMembers = ref<TeamMember[]>([])
const detailTeachers = ref<TeamTeacher[]>([])

async function openDetail(row: TeamAdminRow) {
  detailOpen.value = true
  detailLoading.value = true
  detailTeam.value = null
  detailMembers.value = []
  detailTeachers.value = []
  try {
    const [t, m, te] = await Promise.all([
      http.get<ApiResponse<Team>>(`/teams/${row.id}`),
      http.get<ApiResponse<TeamMember[]>>(`/teams/${row.id}/members`),
      http.get<ApiResponse<TeamTeacher[]>>(`/teams/${row.id}/teachers`),
    ])
    if (t.data.code !== 0) throw new Error(t.data.message)
    if (m.data.code !== 0) throw new Error(m.data.message)
    if (te.data.code !== 0) throw new Error(te.data.message)
    detailTeam.value = t.data.data
    detailMembers.value = m.data.data
    detailTeachers.value = te.data.data
  } finally {
    detailLoading.value = false
  }
}

onMounted(async () => {
  if (!userStore.meLoaded) await userStore.fetchMe()
  await loadDepts()
  await loadTeams()
})
</script>
<template>
  <div class="org-page">
    <div class="page-header">
      <h1 class="page-title">团队管理</h1>
      <p class="page-subtitle">全校团队查询（只读）</p>
    </div>
    <div class="org-layout">
      <div class="dept-panel">
        <div class="panel-header"><h3>院系结构</h3></div>
        <div class="tree-container" v-loading="deptsLoading">
          <el-tree
            node-key="id"
            :data="[{ id: 0, deptName: '全部院系', children: deptTree }]"
            :props="{ label: 'deptName', children: 'children' }"
            default-expand-all
            :expand-on-click-node="false"
            @node-click="(data: any) => onDeptNodeClick(data)"
          >
            <template #default="{ node, data }">
              <span class="tree-node" :class="{ 'is-root': data.id === 0 }">
                <svg v-if="data.children?.length" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="tree-icon">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="tree-icon leaf">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                  <polyline points="14,2 14,8 20,8" />
                </svg>
                <span class="tree-label">{{ node.label }}</span>
                <span v-if="data.id !== 0" class="tree-count">{{ data.id }}</span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>
      <div class="user-panel">
        <div class="search-bar">
          <div class="search-inputs">
            <el-input v-model="query.teamName" placeholder="团队名称" clearable style="width: 140px" />
            <el-input v-model="query.captainRealName" placeholder="队长姓名" clearable style="width: 120px" />
          </div>
          <div class="search-actions">
            <el-button type="primary" @click="page.pageNo = 1; loadTeams()">查询</el-button>
            <el-button @click="loadTeams">刷新</el-button>
          </div>
        </div>
        <div class="tip-banner">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10" /><line x1="12" y1="16" x2="12" y2="12" /><line x1="12" y1="8" x2="12.01" y2="8" />
          </svg>
          <span>仅供校级/系统管理员查看团队信息，不支持在此页新增、编辑或删除团队。</span>
        </div>
        <div class="user-table" v-loading="loading">
          <el-table :data="rows" style="width: 100%">
            <el-table-column prop="id" label="编号" width="70" />
            <el-table-column prop="teamName" label="团队名称" min-width="140" />
            <el-table-column label="队长" width="100"><template #default="{ row }">{{ row.captainRealName || '-' }}</template></el-table-column>
            <el-table-column label="成员数" width="80"><template #default="{ row }">{{ row.memberCount }}</template></el-table-column>
            <el-table-column label="指导教师数" width="100"><template #default="{ row }">{{ row.teacherCount }}</template></el-table-column>
            <el-table-column label="院系" width="140"><template #default="{ row }">{{ deptNameById[row.ownerDeptId] || `ID:${row.ownerDeptId}` }}</template></el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ labelTeamStatus(row.status) }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170"><template #default="{ row }">{{ formatDt(row.createdAt) }}</template></el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }"><el-button size="small" text type="primary" @click="openDetail(row)">查看详情</el-button></template>
            </el-table-column>
          </el-table>
        </div>
        <div class="pagination-bar">
          <el-pagination background layout="total, prev, pager, next, sizes" :total="page.total"
            v-model:current-page="page.pageNo" v-model:page-size="page.pageSize"
            @current-change="loadTeams" @size-change="loadTeams" />
        </div>
      </div>
    </div>
    <el-dialog v-model="detailOpen" title="团队详情" width="720px" class="apple-dialog" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detailTeam">
          <h3 class="detail-section-title">基本信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="团队名称">{{ detailTeam.teamName }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ labelTeamStatus(detailTeam.status) }}</el-descriptions-item>
            <el-descriptions-item label="队长用户ID">{{ detailTeam.captainUserId }}</el-descriptions-item>
            <el-descriptions-item label="归属院系">{{ deptNameById[detailTeam.ownerDeptId] || detailTeam.ownerDeptId }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detailTeam.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
          <h3 class="detail-section-title">成员列表</h3>
          <el-table :data="detailMembers" size="small" max-height="240">
            <el-table-column label="姓名" width="120"><template #default="{ row }">{{ row.user?.realName || '-' }}</template></el-table-column>
            <el-table-column label="账号" width="120"><template #default="{ row }">{{ row.user?.username || '-' }}</template></el-table-column>
            <el-table-column label="类型" width="90"><template #default="{ row }">{{ labelUserType(row.user?.userType) }}</template></el-table-column>
            <el-table-column label="队长" width="70"><template #default="{ row }">{{ row.isCaptain === 1 ? '是' : '否' }}</template></el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ labelInviteStatus(row.joinStatus) }}</template>
            </el-table-column>
          </el-table>
          <h3 class="detail-section-title">指导教师</h3>
          <el-table :data="detailTeachers" size="small" max-height="200">
            <el-table-column label="姓名" width="120"><template #default="{ row }">{{ row.user?.realName || '-' }}</template></el-table-column>
            <el-table-column label="账号" width="120"><template #default="{ row }">{{ row.user?.username || '-' }}</template></el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ labelInviteStatus(row.joinStatus) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>
.org-page { padding: 8px; height: 100%; display: flex; flex-direction: column; }
.page-header { margin-bottom: 20px; }
.page-title { font-size: 28px; font-weight: 700; color: var(--apple-text); margin: 0 0 4px 0; letter-spacing: -0.5px; }
.page-subtitle { font-size: 14px; color: var(--apple-text-secondary); margin: 0; }
.org-layout { display: flex; gap: 16px; flex: 1; min-height: 0; }
.dept-panel { width: 260px; flex-shrink: 0; background: var(--apple-glass); backdrop-filter: blur(20px) saturate(180%); border: 1px solid var(--apple-border); border-radius: var(--apple-radius-lg); display: flex; flex-direction: column; }
.panel-header { padding: 16px 20px; border-bottom: 1px solid var(--apple-border); }
.panel-header h3 { font-size: 14px; font-weight: 600; color: var(--apple-text); margin: 0; }
.tree-container { flex: 1; overflow: auto; padding: 12px; }
.tree-node { display: flex; align-items: center; gap: 8px; padding: 4px 8px; border-radius: 6px; }
.tree-node.is-root { font-weight: 600; }
.tree-icon { width: 16px; height: 16px; color: var(--apple-primary); flex-shrink: 0; }
.tree-icon.leaf { color: var(--apple-text-tertiary); }
.tree-label { font-size: 13px; color: var(--apple-text); flex: 1; }
.tree-count { font-size: 11px; color: var(--apple-text-tertiary); background: var(--apple-bg-secondary); padding: 2px 6px; border-radius: 4px; }
.user-panel { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px; }
.search-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px; background: var(--apple-glass); border: 1px solid var(--apple-border); border-radius: var(--apple-radius-lg); }
.search-inputs { display: flex; align-items: center; gap: 8px; flex: 1; flex-wrap: wrap; }
.search-actions { display: flex; gap: 8px; }
.tip-banner { display: flex; align-items: center; gap: 10px; padding: 12px 16px; background: rgba(0, 122, 255, 0.06); border: 1px solid rgba(0, 122, 255, 0.15); border-radius: var(--apple-radius); font-size: 13px; color: var(--apple-text-secondary); }
.tip-banner svg { width: 16px; height: 16px; color: var(--apple-primary); flex-shrink: 0; }
.user-table { flex: 1; background: var(--apple-glass); border: 1px solid var(--apple-border); border-radius: var(--apple-radius-lg); overflow: hidden; }
.pagination-bar { display: flex; justify-content: flex-end; padding: 12px 16px; background: var(--apple-glass); border: 1px solid var(--apple-border); border-radius: var(--apple-radius-lg); }
.detail-section-title { font-size: 15px; font-weight: 600; margin: 16px 0 8px; color: var(--apple-text); }
.detail-section-title:first-of-type { margin-top: 0; }
</style>