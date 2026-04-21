<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'
import type { ApiResponse, PageResult } from '../../types/api'
import { labelRoleCode } from '../../utils/displayLabels'

type ReviewerRow = {
  id: number
  username: string
  realName: string
  teacherNo: string
  deptId?: number
  enabled: number
  roleCodes: string[]
  activeScopeCount: number
  createdAt?: string
  updatedAt?: string
}
type ReviewerScopeRow = {
  id: number
  competitionId: number
  competitionName: string
  enabled: number
  validFrom?: string
  validTo?: string
}
type Competition = { id: number; competitionName: string; enabled: number }
type ScopeDraft = {
  competitionId: number
  enabled: number
  validRange?: [string, string] | []
}

const loading = ref(false)
const rows = ref<ReviewerRow[]>([])
const page = reactive({ pageNo: 1, pageSize: 10, total: 0 })
const query = reactive({
  keyword: '',
  teacherNo: '',
  enabled: undefined as number | undefined,
})

const competitions = ref<Competition[]>([])

const roleOptions = [
  { value: 'COMP_REVIEWER_L1', label: '一级审核员' },
  { value: 'COMP_REVIEWER_L2', label: '二级复审员' },
]

const formOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  username: '',
  realName: '',
  teacherNo: '',
  deptId: undefined as number | undefined,
  password: '',
})

const rolesOpen = ref(false)
const roleEditing = ref<ReviewerRow | null>(null)
const roleCodes = ref<string[]>([])

const scopesOpen = ref(false)
const scopeEditing = ref<ReviewerRow | null>(null)
const scopeList = ref<ScopeDraft[]>([])
const selectedCompetitionIds = ref<number[]>([])

const saving = ref(false)

const competitionNameById = computed(() => {
  const map: Record<number, string> = {}
  for (const c of competitions.value) map[c.id] = c.competitionName
  return map
})

function formatDateCn(input?: string) {
  if (!input) return '-'
  const d = new Date(input)
  if (Number.isNaN(d.getTime())) return input
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}年${month}月${day}日`
}

async function loadCompetitions() {
  const resp = await http.get<ApiResponse<PageResult<Competition>>>('/dicts/competitions', {
    params: { pageNo: 1, pageSize: 1000, enabled: 1 },
  })
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  competitions.value = resp.data.data.list
}

async function loadReviewers() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<ReviewerRow>>>('/admin/reviewers', {
      params: {
        pageNo: page.pageNo,
        pageSize: page.pageSize,
        keyword: query.keyword || undefined,
        teacherNo: query.teacherNo || undefined,
        enabled: query.enabled,
      },
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

function resetQuery() {
  query.keyword = ''
  query.teacherNo = ''
  query.enabled = undefined
  page.pageNo = 1
  loadReviewers()
}

function openCreate() {
  editingId.value = null
  form.username = ''
  form.realName = ''
  form.teacherNo = ''
  form.deptId = undefined
  form.password = '123456'
  formOpen.value = true
}

function openEdit(row: ReviewerRow) {
  editingId.value = row.id
  form.username = row.username
  form.realName = row.realName
  form.teacherNo = row.teacherNo
  form.deptId = row.deptId
  form.password = ''
  formOpen.value = true
}

async function saveForm() {
  if (!form.realName.trim() || !form.teacherNo.trim()) {
    ElMessage.warning('姓名和工号必填')
    return
  }
  saving.value = true
  try {
    if (editingId.value == null) {
      if (!form.username.trim()) {
        ElMessage.warning('账号必填')
        return
      }
      const payload = {
        username: form.username.trim(),
        realName: form.realName.trim(),
        teacherNo: form.teacherNo.trim(),
        deptId: form.deptId,
        password: form.password || '123456',
        roleCodes: ['COMP_REVIEWER_L1'],
        scopes: [],
      }
      const resp = await http.post<ApiResponse<number>>('/admin/reviewers', payload)
      if (resp.data.code !== 0) throw new Error(resp.data.message)
      ElMessage.success('审核账号已创建，默认角色为一级审核员')
    } else {
      const resp = await http.put<ApiResponse<null>>(`/admin/reviewers/${editingId.value}`, {
        realName: form.realName.trim(),
        teacherNo: form.teacherNo.trim(),
        deptId: form.deptId,
      })
      if (resp.data.code !== 0) throw new Error(resp.data.message)
      ElMessage.success('基本信息已更新')
    }
    formOpen.value = false
    await loadReviewers()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function openRoleDialog(row: ReviewerRow) {
  roleEditing.value = row
  roleCodes.value = [...(row.roleCodes || [])]
  rolesOpen.value = true
}

async function saveRoles() {
  if (!roleEditing.value) return
  if (!roleCodes.value.length) {
    ElMessage.warning('至少选择一个角色')
    return
  }
  saving.value = true
  try {
    const resp = await http.put<ApiResponse<null>>(`/admin/reviewers/${roleEditing.value.id}/roles`, {
      roleCodes: roleCodes.value,
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('角色已保存')
    rolesOpen.value = false
    await loadReviewers()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存角色失败')
  } finally {
    saving.value = false
  }
}

function syncScopeListBySelected() {
  const selectedSet = new Set(selectedCompetitionIds.value)
  const next = scopeList.value.filter((x) => selectedSet.has(x.competitionId))
  const existingSet = new Set(next.map((x) => x.competitionId))
  for (const id of selectedCompetitionIds.value) {
    if (!existingSet.has(id)) next.push({ competitionId: id, enabled: 1, validRange: [] })
  }
  scopeList.value = next.sort((a, b) => a.competitionId - b.competitionId)
}

async function openScopeDialog(row: ReviewerRow) {
  scopeEditing.value = row
  scopeList.value = []
  selectedCompetitionIds.value = []
  try {
    const resp = await http.get<ApiResponse<ReviewerScopeRow[]>>(`/admin/reviewers/${row.id}/scopes`)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    scopeList.value = resp.data.data.map((s) => ({
      competitionId: s.competitionId,
      enabled: s.enabled ?? 1,
      validRange: s.validFrom && s.validTo ? [s.validFrom, s.validTo] : [],
    }))
    selectedCompetitionIds.value = scopeList.value.map((x) => x.competitionId)
    scopesOpen.value = true
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载竞赛授权失败')
  }
}

async function saveScopes() {
  if (!scopeEditing.value) return
  saving.value = true
  try {
    const payload = {
      scopes: scopeList.value.map((x) => ({
        competitionId: x.competitionId,
        enabled: x.enabled,
        validFrom: x.validRange && x.validRange.length === 2 ? x.validRange[0] : null,
        validTo: x.validRange && x.validRange.length === 2 ? x.validRange[1] : null,
      })),
    }
    const resp = await http.put<ApiResponse<null>>(`/admin/reviewers/${scopeEditing.value.id}/scopes`, payload)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('竞赛授权已保存')
    scopesOpen.value = false
    await loadReviewers()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存竞赛授权失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: ReviewerRow) {
  try {
    await ElMessageBox.confirm(
      `确认${row.enabled === 1 ? '停用' : '启用'}审核账号「${row.realName}」吗？`,
      '确认操作',
      { type: 'warning' },
    )
    const resp = await http.post<ApiResponse<null>>(`/admin/reviewers/${row.id}/toggle`)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('状态已更新')
    await loadReviewers()
  } catch (e: unknown) {
    if (e !== 'cancel') ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function resetPassword(row: ReviewerRow) {
  try {
    await ElMessageBox.confirm(
      `确认重置「${row.realName}」密码为 123456 吗？`,
      '确认操作',
      { type: 'warning' },
    )
    const resp = await http.post<ApiResponse<null>>(`/admin/reviewers/${row.id}/reset-password`)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('密码已重置为 123456')
  } catch (e: unknown) {
    if (e !== 'cancel') ElMessage.error(e instanceof Error ? e.message : '重置失败')
  }
}

onMounted(async () => {
  await Promise.all([loadCompetitions(), loadReviewers()])
})
</script>

<template>
  <div class="reviewer-page">
    <div class="page-header">
      <h1 class="page-title">审核账号管理</h1>
      <p class="page-subtitle">校级管理员可配置审核账号、审核角色和竞赛授权生效时间</p>
    </div>

    <el-alert
      title="使用提示：先创建审核账号，再勾选角色（L1/L2），最后配置竞赛授权范围与生效时间。"
      type="info"
      :closable="false"
      show-icon
      class="tips"
    />

    <div class="toolbar">
      <div class="filters">
        <el-input v-model="query.keyword" placeholder="姓名/账号关键字" class="w-180" clearable />
        <el-input v-model="query.teacherNo" placeholder="工号" class="w-140" clearable />
        <el-select v-model="query.enabled" placeholder="状态" class="w-120" clearable>
          <el-option :value="1" label="启用" />
          <el-option :value="0" label="停用" />
        </el-select>
        <el-button type="primary" @click="loadReviewers">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新建审核账号</el-button>
    </div>

    <el-table :data="rows" border stripe v-loading="loading">
      <el-table-column type="index" label="序号" width="70" />
      <el-table-column prop="username" label="账号" min-width="130" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="teacherNo" label="工号" min-width="120" />
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <el-tag v-for="r in row.roleCodes" :key="r" class="mr6" effect="light">{{ labelRoleCode(r) }}</el-tag>
          <span v-if="!row.roleCodes?.length">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="activeScopeCount" label="授权竞赛数" width="110" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" effect="light">
            {{ row.enabled === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="140">
        <template #default="{ row }">{{ formatDateCn(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" text type="primary" @click="openRoleDialog(row)">角色</el-button>
          <el-button size="small" text type="primary" @click="openScopeDialog(row)">竞赛授权</el-button>
          <el-button size="small" text type="warning" @click="toggleEnabled(row)">
            {{ row.enabled === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button size="small" text type="danger" @click="resetPassword(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="page.total"
        v-model:current-page="page.pageNo"
        v-model:page-size="page.pageSize"
        @change="loadReviewers"
      />
    </div>

    <el-dialog v-model="formOpen" :title="editingId == null ? '新建审核账号' : '编辑审核账号'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="账号">
          <el-input v-model="form.username" :disabled="editingId != null" placeholder="仅新建时可填写" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="工号">
          <el-input v-model="form.teacherNo" />
        </el-form-item>
        <el-form-item label="院系ID">
          <el-input-number v-model="form.deptId" :min="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="editingId == null" label="初始密码">
          <el-input v-model="form.password" />
          <div class="small-tip">不填则默认 123456</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rolesOpen" title="角色勾选" width="480px">
      <el-checkbox-group v-model="roleCodes">
        <el-checkbox v-for="r in roleOptions" :key="r.value" :value="r.value">{{ r.label }}</el-checkbox>
      </el-checkbox-group>
      <div class="small-tip">至少选择一个角色，决定可访问一级审核台或二级复审台。</div>
      <template #footer>
        <el-button @click="rolesOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoles">保存角色</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="scopesOpen" title="竞赛授权多选与生效时间" width="860px">
      <el-form label-width="90px">
        <el-form-item label="竞赛多选">
          <el-select
            v-model="selectedCompetitionIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择允许审核的竞赛"
            @change="syncScopeListBySelected"
            style="width: 100%"
          >
            <el-option v-for="c in competitions" :key="c.id" :label="c.competitionName" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table :data="scopeList" border size="small" empty-text="请先选择竞赛">
        <el-table-column label="竞赛" min-width="240">
          <template #default="{ row }">{{ competitionNameById[row.competitionId] || `竞赛#${row.competitionId}` }}</template>
        </el-table-column>
        <el-table-column label="是否启用" width="120">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="生效时间范围" min-width="320">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.validRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
            />
          </template>
        </el-table-column>
      </el-table>
      <el-alert
        v-if="scopeList.length === 0"
        title="当前账号尚未配置竞赛授权"
        type="warning"
        :closable="false"
        show-icon
        class="empty-scope-tip"
      />
      <div class="small-tip">不填时间范围表示长期有效；保存为覆盖式更新。</div>
      <template #footer>
        <el-button @click="scopesOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveScopes">保存授权</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.reviewer-page {
  padding: 8px;
}
.page-header {
  margin-bottom: 12px;
}
.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}
.page-subtitle {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}
.tips {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.filters {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.w-180 {
  width: 180px;
}
.w-140 {
  width: 140px;
}
.w-120 {
  width: 120px;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.small-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
}
.empty-scope-tip {
  margin-top: 10px;
}
.mr6 {
  margin-right: 6px;
}
</style>
