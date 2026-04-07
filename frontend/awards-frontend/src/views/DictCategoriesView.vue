<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../api/http'

type ApiResponse<T> = { code: number; message: string; data: T }
type PageResult<T> = { total: number; list: T[] }
type Category = {
  id: number
  categoryName: string
  enabled: number
  sortNo: number
  remark?: string
}

const loading = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const query = reactive({
  enabled: undefined as number | undefined,
})
const rows = ref<Category[]>([])

// 加载列表
async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<Category>>>('/dicts/categories', {
      params: {
        pageNo: page.pageNo,
        pageSize: page.pageSize,
        enabled: query.enabled,
      },
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    page.total = resp.data.data.total
    rows.value = resp.data.data.list
  } finally {
    loading.value = false
  }
}

const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  categoryName: '',
  sortNo: 0,
  remark: '',
})

// 打开新增
function openCreate() {
  editingId.value = null
  form.categoryName = ''
  form.sortNo = 0
  form.remark = ''
  dialogOpen.value = true
}

// 打开编辑
function openEdit(row: Category) {
  editingId.value = row.id
  form.categoryName = row.categoryName
  form.sortNo = row.sortNo ?? 0
  form.remark = row.remark || ''
  dialogOpen.value = true
}

// 保存
async function save() {
  if (!form.categoryName) {
    ElMessage.error('请把必填项补齐')
    return
  }
  const payload = {
    categoryName: form.categoryName,
    sortNo: form.sortNo ?? 0,
    remark: form.remark || undefined,
  }
  if (editingId.value) {
    const resp = await http.put<ApiResponse<null>>(`/dicts/categories/${editingId.value}`, payload)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已保存')
  } else {
    const resp = await http.post<ApiResponse<number>>('/dicts/categories', payload)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已创建')
  }
  dialogOpen.value = false
  await load()
}

// 切换启用/停用
async function toggle(row: Category) {
  const resp = await http.post<ApiResponse<null>>(`/dicts/categories/${row.id}/toggle`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  ElMessage.success('已切换')
  await load()
}

// 删除
async function remove(row: Category) {
  try {
    await ElMessageBox.confirm(
      `确认删除竞赛类别「${row.categoryName}」吗？`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const resp = await http.delete<ApiResponse<null>>(`/dicts/categories/${row.id}`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  ElMessage.success('已删除')
  await load()
}

onMounted(() => {
  load()
})
</script>

<template>
  <div class="dict-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">竞赛类别库</h1>
      <p class="page-subtitle">竞赛类别基础数据管理</p>
    </div>

    <!-- 顶部工具栏 -->
    <div class="top-toolbar">
      <!-- 左侧无分类标签，保持结构占位 -->
      <div class="category-tabs" />

      <!-- 右侧筛选+按钮 -->
      <div class="toolbar-right">
        <el-select v-model="query.enabled" placeholder="状态" clearable class="status-select" @change="load">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button @click="page.pageNo = 1; query.enabled = undefined; load()"> <svg viewBox="0 0 24 24" fill="none"
            stroke="currentColor" stroke-width="2" style="width: 16px; height: 16px; margin-right: 6px;">
            <path d="M23 4v6h-6M1 20v-6h6" />
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
          </svg>
          刷新
        </el-button>
        <el-button type="primary" @click="openCreate">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            style="width: 16px; height: 16px; margin-right: 6px;">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          新增类别
        </el-button>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="content-area">
      <div class="table-card" v-loading="loading">
        <el-table :data="rows" style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="categoryName" label="类别名称" min-width="180" align="left" />
          <el-table-column prop="enabled" label="状态" width="100" align="center">
            <template #default="{ row }">
              <span class="status-indicator" :class="row.enabled === 1 ? 'active' : 'inactive'">
                {{ row.enabled === 1 ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序" width="100" align="center" />
          <el-table-column prop="remark" label="备注" min-width="180" align="center">
            <template #default="{ row }">
              {{ row.remark || '--' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right" align="center">
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
          <el-pagination background layout="total, prev, pager, next" :total="page.total"
            v-model:current-page="page.pageNo" v-model:page-size="page.pageSize" @change="load" />
        </div>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogOpen" :title="editingId ? '编辑类别' : '新增类别'" width="550px" class="apple-dialog">
      <div class="form-grid">
        <div class="form-item full">
          <label>类别名称 <span class="required">*</span></label>
          <el-input v-model="form.categoryName" placeholder="类别全称" />
        </div>
        <div class="form-item">
          <label>排序</label>
          <el-input-number v-model="form.sortNo" :min="0" style="width: 100%" />
        </div>
        <div class="form-item full">
          <label>备注</label>
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="可选备注信息" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
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
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: white;
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  overflow: hidden;
}

.category-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  flex: 1;
  min-width: 0;
}

.category-tabs::-webkit-scrollbar {
  display: none;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.status-select {
  width: 100px;
}

.content-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
}

.table-card :deep(.el-table th.el-table__cell) {
  background: var(--apple-bg-secondary);
  font-weight: 600;
  color: var(--apple-text);
  font-size: 12px;
}

.table-card :deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid var(--apple-border-light);
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
  grid-template-columns: repeat(2, 1fr);
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

.form-item .required {
  color: var(--apple-danger);
}

.apple-dialog :deep(.el-dialog) {
  border-radius: var(--apple-radius-xl) !important;
  background: #fff !important;
  border: 1px solid var(--apple-border) !important;
}

.apple-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid var(--apple-border);
}

.apple-dialog :deep(.el-dialog__title) {
  font-weight: 600;
  color: var(--apple-text);
}

:deep(.el-table__cell) {
  padding: 10px 0;
  line-height: 1.5;
}

:deep(.el-table) {
  font-size: 13px;
}
</style>