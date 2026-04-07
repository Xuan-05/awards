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
const selectedDeptId = ref<number | undefined | 0>(undefined)
const loading = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const query = reactive({ teamName: "", captainRealName: "" })
const rows = ref<TeamAdminRow[]>([])

const deptNameById = computed(() => {
  const m: Record<number, string> = {}
  for (const d of depts.value) m[d.id] = d.deptName
  return m
})

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
        ownerDeptId: selectedDeptId.value === 0 || selectedDeptId.value === undefined ? undefined : selectedDeptId.value,
      },
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    rows.value = resp.data.data.list
    page.total = resp.data.data.total
  } finally {
    loading.value = false
  }
}

// ✅ 修复：刷新按钮真正清空所有条件
function refresh() {
  query.teamName = ""
  query.captainRealName = ""
  selectedDeptId.value = undefined
  page.pageNo = 1
  loadTeams()
}

function formatDt(s?: string) {
  if (!s) return "-"
  return s.replace("T", " ").slice(0, 19)
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

    <div class="search-bar">
      <div class="search-inputs">
        <el-input v-model="query.teamName" placeholder="团队名称" clearable class="search-input-sm" />
        <el-input v-model="query.captainRealName" placeholder="队长姓名" clearable class="search-input-sm" />
        
        <el-select 
          v-model="selectedDeptId" 
          placeholder="院系" 
          clearable 
          filterable 
          style="width:150px"
          @change="page.pageNo = 1; loadTeams()"
        >
          <el-option label="全部" :value="0" />
          <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
      </div>
      <div class="search-actions">
        <el-button type="primary" @click="page.pageNo = 1; loadTeams()">查询</el-button>
        
        <!-- ✅ 刷新按钮绑定正确方法 -->
        <el-button @click="refresh">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px;margin-right:4px">
            <path d="M23 4v6h-6M1 20v-6h6" />
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
          </svg>
          刷新
        </el-button>
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
        <el-table-column prop="id" label="编号" width="70" align="center" />
        <el-table-column prop="teamName" label="团队名称" min-width="140" align="center" />
        <el-table-column label="队长" width="105" align="center">
          <template #default="{ row }">{{ row.captainRealName || '-' }}</template>
        </el-table-column>
        <el-table-column label="成员数" width="85" align="center">
          <template #default="{ row }">{{ row.memberCount }}</template>
        </el-table-column>
        <el-table-column label="指导教师数" width="105" align="center">
          <template #default="{ row }">{{ row.teacherCount }}</template>
        </el-table-column>
        <el-table-column label="院系" width="145" align="center">
          <template #default="{ row }">{{ deptNameById[row.ownerDeptId] || `ID:${row.ownerDeptId}` }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">{{ labelTeamStatus(row.status) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="180" align="center">
          <template #default="{ row }">{{ formatDt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页：和用户页面完全一样，不动 -->
    <div class="pagination-bar">
      <el-pagination 
        background 
        layout="total, prev, pager, next, sizes" 
        :total="page.total"
        v-model:current-page="page.pageNo" 
        v-model:page-size="page.pageSize"
        @current-change="loadTeams" 
        @size-change="loadTeams" 
      />
    </div>

    <el-dialog v-model="detailOpen" title="团队详情" width="720px" destroy-on-close>
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
.org-page {
  padding: 8px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #1d1d1f;
  margin: 0 0 4px 0;
}

.page-subtitle {
  font-size: 14px;
  color: #6e6e73;
  margin: 0;
}

.search-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  margin-bottom: 12px;
}

.search-inputs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.search-input-sm {
  width: 140px;
}

.search-actions {
  display: flex;
  gap: 8px;
}

.tip-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(0, 122, 255, 0.06);
  border: 1px solid rgba(0, 122, 255, 0.15);
  border-radius: 8px;
  font-size: 13px;
  color: #6e6e73;
  margin-bottom: 12px;
}

.tip-banner svg {
  width: 16px;
  height: 16px;
}

.user-table {
  flex: 1;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 12px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.detail-section-title {
  font-size: 15px;
  font-weight: 600;
  margin: 16px 0 8px;
}

.detail-section-title:first-of-type {
  margin-top: 0;
}
</style>