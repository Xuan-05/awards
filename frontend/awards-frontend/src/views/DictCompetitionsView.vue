<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../api/http'

type ApiResponse<T> = { code: number; message: string; data: T }
type PageResult<T> = { total: number; list: T[] }
type Category = { id: number; categoryName: string; enabled: number }
type Organizer = { id: number; organizerName: string; enabled: number }
type Row = {
  id: number
  competitionName: string
  competitionShortName?: string
  categoryId: number
  organizerId?: number
  organizerName?: string
  defaultLevel?: string
  enabled: number
  sortNo: number
  remark?: string
}

const loading = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const query = reactive({
  enabled: undefined as number | undefined,
  categoryId: undefined as number | undefined,
})
const rows = ref<Row[]>([])

const categories = ref<Category[]>([])
const organizers = ref<Organizer[]>([])

// 分类名称映射
const catName = computed(() => {
  const m = new Map(categories.value.map((c) => [c.id, c.categoryName] as const))
  return (id: number) => m.get(id) || String(id)
})
// 主办方名称映射
const orgName = computed(() => {
  const m = new Map(organizers.value.map((o) => [o.id, o.organizerName] as const))
  return (row: Row) => {
    if (row.organizerName) return row.organizerName
    const id = row.organizerId
    return id ? m.get(id) || String(id) : '-'
  }
})

// 加载分类、主办方基础数据
async function loadBase() {
  const [c, o] = await Promise.all([
    http.get<ApiResponse<PageResult<Category>>>('/dicts/categories', { params: { pageNo: 1, pageSize: 200, enabled: 1 } }),
    http.get<ApiResponse<PageResult<Organizer>>>('/dicts/organizers', { params: { pageNo: 1, pageSize: 200, enabled: 1 } }),
  ])
  if (c.data.code !== 0) throw new Error(c.data.message)
  if (o.data.code !== 0) throw new Error(o.data.message)
  categories.value = c.data.data.list
  organizers.value = o.data.data.list
}

// 加载竞赛列表
async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<Row>>>('/dicts/competitions', {
      params: { pageNo: page.pageNo, pageSize: page.pageSize, enabled: query.enabled, categoryId: query.categoryId },
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
  competitionName: '',
  competitionShortName: '',
  categoryId: undefined as number | undefined,
  organizerId: undefined as number | undefined,
  defaultLevel: '',
  sortNo: 0,
  remark: '',
})

// 打开新增弹窗
function openCreate() {
  editingId.value = null
  form.competitionName = ''
  form.competitionShortName = ''
  form.categoryId = query.categoryId
  form.organizerId = undefined
  form.defaultLevel = ''
  form.sortNo = 0
  form.remark = ''
  dialogOpen.value = true
}
// 打开编辑弹窗
function openEdit(row: Row) {
  editingId.value = row.id
  form.competitionName = row.competitionName
  form.competitionShortName = row.competitionShortName || ''
  form.categoryId = row.categoryId
  form.organizerId = row.organizerId
  form.defaultLevel = row.defaultLevel || ''
  form.sortNo = row.sortNo ?? 0
  form.remark = row.remark || ''
  dialogOpen.value = true
}

// 保存/新增竞赛
async function save() {
  if (!form.competitionName || !form.categoryId) {
    ElMessage.error('请把必填项补齐')
    return
  }
  const payload = {
    competitionName: form.competitionName,
    competitionShortName: form.competitionShortName || undefined,
    categoryId: form.categoryId,
    organizerId: form.organizerId || undefined,
    defaultLevel: form.defaultLevel || undefined,
    sortNo: form.sortNo ?? 0,
    remark: form.remark || undefined,
  }
  if (editingId.value) {
    const resp = await http.put<ApiResponse<null>>(`/dicts/competitions/${editingId.value}`, payload)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已保存')
  } else {
    const resp = await http.post<ApiResponse<number>>('/dicts/competitions', payload)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已创建')
  }
  dialogOpen.value = false
  await load()
}

// 切换启用/停用
async function toggle(row: Row) {
  const resp = await http.post<ApiResponse<null>>(`/dicts/competitions/${row.id}/toggle`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  ElMessage.success('已切换')
  await load()
}

// 切换分类
function switchCategory(id: number | undefined) {
  query.categoryId = id
  page.pageNo = 1 // 切换分类重置页码
  load()
}

onMounted(async () => {
  await loadBase()
  await load()
})
</script>

<template>
  <div class="dict-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">竞赛字典</h1>
      <p class="page-subtitle">竞赛项目基础数据管理</p>
    </div>

    <!-- 顶部横向分类导航（替代原左侧栏） -->
    <div class="category-tabs">
      <div 
        class="tab-item"
        :class="{ active: query.categoryId === undefined }"
        @click="switchCategory(undefined)"
      >
        全部
      </div>
      <div 
        v-for="cat in categories" 
        :key="cat.id" 
        class="tab-item"
        :class="{ active: query.categoryId === cat.id }"
        @click="switchCategory(cat.id)"
      >
        {{ cat.categoryName }}
      </div>
    </div>

    <!-- 内容区 -->
    <div class="content-area">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="query.enabled" placeholder="状态" clearable class="status-select" @change="load">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button @click="load">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 16px; height: 16px; margin-right: 6px;">
              <path d="M23 4v6h-6M1 20v-6h6"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            刷新
          </el-button>
          <el-button type="primary" @click="openCreate">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 16px; height: 16px; margin-right: 6px;">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            新增竞赛
          </el-button>
        </div>
      </div>

      <!-- 数据表格 -->
      <div class="table-card" v-loading="loading">
        <el-table :data="rows" style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="competitionName" label="竞赛名称" min-width="220">
            <template #default="{ row }">
              <div class="comp-name">
                <span class="comp-full">{{ row.competitionName }}</span>
                <span v-if="row.competitionShortName" class="comp-short">{{ row.competitionShortName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="categoryId" label="类别" width="140">
            <template #default="{ row }">
              <span class="cat-badge">{{ catName(row.categoryId) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="organizerId" label="主办方" width="180">
            <template #default="{ row }">
              <span class="org-text">{{ orgName(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="defaultLevel" label="默认级别" width="100">
            <template #default="{ row }">
              <span v-if="row.defaultLevel" class="level-tag">{{ row.defaultLevel }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="enabled" label="状态" width="90">
            <template #default="{ row }">
              <span class="status-indicator" :class="row.enabled === 1 ? 'active' : 'inactive'">
                {{ row.enabled === 1 ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序" width="70" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="openEdit(row)">编辑</el-button>
              <el-button size="small" text :type="row.enabled === 1 ? 'warning' : 'success'" @click="toggle(row)">
                {{ row.enabled === 1 ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :total="page.total"
            :current-page="page.pageNo"
            :page-size="page.pageSize"
            @update:current-page="(v) => (page.pageNo = v)"
            @change="load"
          />
        </div>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogOpen" :title="editingId ? '编辑竞赛' : '新增竞赛'" width="640px" class="apple-dialog">
      <div class="form-grid">
        <div class="form-item full">
          <label>竞赛名称 <span class="required">*</span></label>
          <el-input v-model="form.competitionName" placeholder="竞赛全称" />
        </div>
        <div class="form-item">
          <label>简称</label>
          <el-input v-model="form.competitionShortName" placeholder="可选简称" />
        </div>
        <div class="form-item">
          <label>类别 <span class="required">*</span></label>
          <el-select v-model="form.categoryId" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </div>
        <div class="form-item">
          <label>主办方</label>
          <el-select v-model="form.organizerId" clearable style="width: 100%">
            <el-option v-for="o in organizers" :key="o.id" :label="o.organizerName" :value="o.id" />
          </el-select>
        </div>
        <div class="form-item">
          <label>默认级别</label>
          <el-input v-model="form.defaultLevel" placeholder="如：国家级、省级" />
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

/* 顶部横向分类标签栏（核心修改） */
.category-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  overflow-x: auto;
  scrollbar-width: none;
}
.category-tabs::-webkit-scrollbar {
  display: none;
}

.tab-item {
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text-secondary);
  cursor: pointer;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;
}
.tab-item:hover {
  background: var(--apple-bg-secondary);
  color: var(--apple-text);
}
.tab-item.active {
  background: var(--apple-primary);
  color: #fff;
}

/* 内容区 */
.content-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-select {
  width: 100px;
}

.table-card {
  flex: 1;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
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

.comp-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.comp-full {
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text);
}

.comp-short {
  font-size: 11px;
  color: var(--apple-text-tertiary);
}

.cat-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  padding: 3px 8px;
  border-radius: 4px;
  background: rgba(88, 86, 214, 0.1);
  color: #5856D6;
}

.org-text {
  font-size: 13px;
  color: var(--apple-text-secondary);
}

.level-tag {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(255, 149, 0, 0.1);
  color: var(--apple-warning);
}

.text-muted {
  color: var(--apple-text-tertiary);
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

/* Dialog Form */
.apple-dialog .form-grid {
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
  background: var(--apple-glass) !important;
  backdrop-filter: blur(40px) saturate(180%) !important;
  -webkit-backdrop-filter: blur(40px) saturate(180%) !important;
  border: 1px solid var(--apple-border) !important;
}

.apple-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid var(--apple-border);
}

.apple-dialog :deep(.el-dialog__title) {
  font-weight: 600;
  color: var(--apple-text);
}
</style>