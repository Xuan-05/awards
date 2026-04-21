<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'
import type { ApiResponse, PageResult } from '../../types/api'

type Dept = { id: number; deptCode: string; deptName: string; parentId?: number | null; enabled: number; sortNo: number }
type ClassRow = { id: number; deptId: number; className: string; enabled: number }

const deptsLoading = ref(false)
const depts = ref<Dept[]>([])
const deptNameById = computed(() => {
  const m: Record<number, string> = {}
  for (const d of depts.value) m[d.id] = d.deptName
  return m
})

const selectedDeptId = ref<number | undefined>(undefined)

const loading = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const query = reactive({
  enabled: undefined as number | undefined,
  deptId: undefined as number | undefined,
  className: '',
})
const rows = ref<ClassRow[]>([])

async function loadDepts() {
  deptsLoading.value = true
  try {
    const resp = await http.get<ApiResponse<Dept[]>>('/depts')
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    depts.value = resp.data.data
  } finally {
    deptsLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<ClassRow>>>('/admin/classes', {
      params: {
        pageNo: page.pageNo,
        pageSize: page.pageSize,
        deptId: query.deptId,
        enabled: query.enabled,
        className: query.className?.trim() || undefined,
      }
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    rows.value = resp.data.data.list
    page.total = resp.data.data.total
  } finally {
    loading.value = false
  }
}
// 强制刷新表格
function refreshTable() {
  query.deptId = undefined
  query.enabled = undefined
  query.className = ''
  page.pageNo = 1
  load()
}
const dlgOpen = ref(false)
const dlgMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const form = reactive({
  deptId: undefined as number | undefined,
  className: '',
})

function openCreate() {
  dlgMode.value = 'create'
  editingId.value = null
  form.deptId = selectedDeptId.value
  form.className = ''
  dlgOpen.value = true
}

function openEdit(row: ClassRow) {
  dlgMode.value = 'edit'
  editingId.value = row.id
  form.deptId = row.deptId
  form.className = row.className
  dlgOpen.value = true
}

async function save() {
  if (!form.deptId || !form.className.trim()) {
    ElMessage.error('请填写院系与班级名称')
    return
  }
  const payload = { deptId: form.deptId, className: form.className.trim(), sortNo: 0 }
  if (dlgMode.value === 'create') {
    const resp = await http.post<ApiResponse<number>>('/admin/classes', payload)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已新增')
  } else {
    const resp = await http.put<ApiResponse<null>>(`/admin/classes/${editingId.value}`, payload)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已保存')
  }
  dlgOpen.value = false
  await load()
}

async function toggle(row: ClassRow) {
  await ElMessageBox.confirm(`确认要${row.enabled === 1 ? '停用' : '启用'}班级「${row.className}」吗？`, '提示', { type: 'warning' })
  const resp = await http.post<ApiResponse<null>>(`/admin/classes/${row.id}/toggle`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  ElMessage.success('已更新')
  await load()
}

async function remove(row: ClassRow) {
  try {
    await ElMessageBox.confirm(
      `确认删除班级「${row.className}」吗？`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const resp = await http.delete<ApiResponse<null>>(`/admin/classes/${row.id}`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await loadDepts()
  await load()
})
</script>

<template>
  <div class="dict-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">班级字典</h1>
      <p class="page-subtitle">班级基础数据管理</p>
    </div>
    <!-- 顶部工具栏 -->
    <div class="top-toolbar">
      <div class="toolbar-right">
        <!-- 院系下拉 + 内置“全部”选项 -->
        <el-select v-model="query.deptId" placeholder="院系" clearable class="status-select" @change="load">
          <el-option label="全部" :value="0" />
          <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>

        <el-select v-model="query.enabled" placeholder="状态" clearable class="status-select" @change="load">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>

        <el-input v-model="query.className" placeholder="班级名称关键字" style="width: 220px" suffix-icon="search"
          @keyup.enter="page.pageNo = 1; load()" />

        <el-button @click="refreshTable">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            style="width:16px;height:16px;margin-right:6px">
            <path d="M23 4v6h-6M1 20v-6h6" />
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
          </svg>
          刷新
        </el-button>

        <el-button type="primary" @click="openCreate">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            style="width:16px;height:16px;margin-right:6px">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          新增班级
        </el-button>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="content-area">
      <div class="table-card" v-loading="loading">
        <el-table :data="rows" style="width:100%">
          <!-- 院系 -->
          <el-table-column label="院系" width="200" align="left">
            <template #default="{ row }">
              <span>{{ deptNameById[row.deptId] || '-' }}</span>
            </template>
          </el-table-column>

          <!-- 班级名称 -->
          <el-table-column prop="className" label="班级名称" min-width="260" align="left" />

          <!-- 状态 -->
          <el-table-column prop="enabled" label="状态" width="100" align="center">
            <template #default="{ row }">
              <span class="status-indicator" :class="row.enabled === 1 ? 'active' : 'inactive'">
                {{ row.enabled === 1 ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>

          <!-- 操作 -->
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="openEdit(row)">编辑</el-button>
              <el-button size="small" text :type="row.enabled === 1 ? 'warning' : 'success'" @click="toggle(row)">
                {{ row.enabled === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button v-if="row.enabled === 0" size="small" text type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <el-pagination background layout="total, prev, pager, next, sizes" :total="page.total"
            v-model:current-page="page.pageNo" v-model:page-size="page.pageSize" @change="load" />
        </div>
      </div>
    </div>

    <!-- 弹窗 -->
    <el-dialog v-model="dlgOpen" :title="dlgMode === 'create' ? '新增班级' : '编辑班级'" width="550px" class="apple-dialog">
      <div class="form-grid">
        <div class="form-item full">
          <label>院系 <span class="required">*</span></label>
          <el-select v-model="form.deptId" filterable style="width:100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </div>
        <div class="form-item full">
          <label>班级名称 <span class="required">*</span></label>
          <el-input v-model="form.className" placeholder="请输入班级名称" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dlgOpen = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
:root {
  --apple-text: #1d1d1f;
  --apple-text-secondary: #424245;
  --apple-text-tertiary: #86868b;
  --apple-bg-secondary: #f5f5f7;
  --apple-border: rgba(0, 0, 0, 0.1);
  --apple-border-light: rgba(0, 0, 0, 0.05);
  --apple-primary: #007aff;
  --apple-success: #34c759;
  --apple-warning: #ff9500;
  --apple-danger: #ff3b30;
  --apple-radius-lg: 12px;
  --apple-radius-xl: 16px;
}

.dict-page {
  padding: 8px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  margin-bottom: 16px;
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

.top-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-select {
  width: 110px;
}

.content-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

.table-card {
  flex: 1;
  background: #fff;
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-card :deep(.el-table) {
  flex: 1;
  --el-table-border-color: var(--apple-border);
  --el-table-header-bg-color: var(--apple-bg-secondary);
  --el-table-row-hover-bg-color: rgba(0, 122, 255, 0.04);
  background: transparent;
  font-size: 13px;
}

.table-card :deep(.el-table th.el-table__cell) {
  background: var(--apple-bg-secondary);
  font-weight: 600;
  color: var(--apple-text);
  font-size: 12px;
}

.table-card :deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid var(--apple-border-light);
  padding: 10px 0;
}

.status-indicator {
  font-size: 12px;
  font-weight: 500;
}

.status-indicator.active {
  color: var(--apple-success);
}

.status-indicator.inactive {
  color: var(--apple-text-tertiary);
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--apple-border);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item.full {
  grid-column: span 2;
}

.form-item label {
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text-secondary);
}

.required {
  color: var(--apple-danger);
}

.apple-dialog :deep(.el-dialog) {
  border-radius: var(--apple-radius-xl) !important;
  background: #fff !important;
  border: 1px solid var(--apple-border) !important;
}
</style>