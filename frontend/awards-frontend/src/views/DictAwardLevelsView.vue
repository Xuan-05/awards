<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { http } from "../api/http";

type ApiResponse<T> = { code: number; message: string; data: T };
type PageResult<T> = { total: number; list: T[] };
type Scope = { id: number; scopeName: string; enabled: number };
type Row = {
  id: number;
  awardScopeId: number;
  levelName: string;
  enabled: number;
  sortNo: number;
};

const loading = ref(false);
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 });
const query = reactive({
  enabled: undefined as number | undefined,
  awardScopeId: undefined as number | undefined,
});
const rows = ref<Row[]>([]);

const scopes = ref<Scope[]>([]);
const scopeName = computed(() => {
  const m = new Map(scopes.value.map((s) => [s.id, s.scopeName] as const));
  return (id: number) => m.get(id) || String(id);
});

async function loadScopes() {
  const resp = await http.get<ApiResponse<PageResult<Scope>>>(
    "/dicts/award-scopes",
    { params: { pageNo: 1, pageSize: 200, enabled: 1 } },
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  scopes.value = resp.data.data.list;
}

async function load() {
  loading.value = true;
  try {
    const resp = await http.get<ApiResponse<PageResult<Row>>>(
      "/dicts/award-levels",
      {
        params: {
          pageNo: page.pageNo,
          pageSize: page.pageSize,
          enabled: query.enabled,
          awardScopeId: query.awardScopeId === 0 ? undefined : query.awardScopeId,
        },
      },
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    page.total = resp.data.data.total;
    rows.value = resp.data.data.list;
  } finally {
    loading.value = false;
  }
}

// 实质性刷新：重置所有筛选+回到第1页
function refresh() {
  query.awardScopeId = undefined;
  query.enabled = undefined;
  page.pageNo = 1;
  load();
}

const dialogOpen = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({
  awardScopeId: undefined as number | undefined,
  levelName: "",
  sortNo: 0,
});

function openCreate() {
  editingId.value = null;
  form.awardScopeId = query.awardScopeId;
  form.levelName = "";
  form.sortNo = 0;
  dialogOpen.value = true;
}
function openEdit(row: Row) {
  editingId.value = row.id;
  form.awardScopeId = row.awardScopeId;
  form.levelName = row.levelName;
  form.sortNo = row.sortNo ?? 0;
  dialogOpen.value = true;
}

async function save() {
  if (!form.awardScopeId || !form.levelName) {
    ElMessage.error("请把必填项补齐");
    return;
  }
  if (editingId.value) {
    const resp = await http.put<ApiResponse<null>>(
      `/dicts/award-levels/${editingId.value}`,
      form,
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    ElMessage.success("已保存");
  } else {
    const resp = await http.post<ApiResponse<number>>(
      "/dicts/award-levels",
      form,
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    ElMessage.success("已创建");
  }
  dialogOpen.value = false;
  await load();
}

async function toggle(row: Row) {
  const resp = await http.post<ApiResponse<null>>(
    `/dicts/award-levels/${row.id}/toggle`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已切换");
  await load();
}

async function remove(row: Row) {
  try {
    await ElMessageBox.confirm(
      `确认删除获奖等级「${row.levelName}」吗？`,
      "提示",
      { type: "warning" },
    );
  } catch {
    return;
  }
  const resp = await http.delete<ApiResponse<null>>(
    `/dicts/award-levels/${row.id}`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已删除");
  await load();
}

onMounted(async () => {
  await loadScopes();
  await load();
});
</script>

<template>
  <div class="dict-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">获奖等级库</h1>
      <p class="page-subtitle">获奖等级基础数据管理</p>
    </div>

    <!-- 顶部工具栏 -->
    <div class="top-toolbar">
      <div class="toolbar-right">
        <!-- 获奖范围下拉（含全部选项） -->
        <el-select v-model="query.awardScopeId" placeholder="获奖范围" clearable class="status-select"
          @change="page.pageNo = 1; load()">
          <el-option label="全部" :value="0" />
          <el-option v-for="s in scopes" :key="s.id" :label="s.scopeName" :value="s.id" />
        </el-select>

        <!-- 状态下拉 -->
        <el-select v-model="query.enabled" placeholder="状态" clearable class="status-select"
          @change="page.pageNo = 1; load()">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>

        <!-- 刷新按钮（实质性刷新） -->
        <el-button @click="refresh">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            style="width:16px;height:16px;margin-right:6px">
            <path d="M23 4v6h-6M1 20v-6h6" />
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
          </svg>
          刷新
        </el-button>

        <!-- 新增按钮 -->
        <el-button type="primary" @click="openCreate">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            style="width:16px;height:16px;margin-right:6px">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          新增
        </el-button>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="content-area">
      <div class="table-card" v-loading="loading">
        <el-table :data="rows" style="width:100%">
          <!-- 获奖范围 -->
          <el-table-column label="获奖范围" width="180" align="left">
            <template #default="{ row }">
              <span>{{ scopeName(row.awardScopeId) }}</span>
            </template>
          </el-table-column>

          <!-- 等级名称 -->
          <el-table-column prop="levelName" label="等级名称" min-width="220" align="left" />

          <!-- 排序 -->
          <el-table-column prop="sortNo" label="排序" width="100" align="center" />

          <!-- 状态 -->
          <el-table-column prop="enabled" label="状态" width="100" align="center">
            <template #default="{ row }">
              <span class="status-indicator" :class="row.enabled === 1 ? 'active' : 'inactive'">
                {{ row.enabled === 1 ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>

          <!-- 操作 -->
          <el-table-column label="操作" width="240" align="center" fixed="right">
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
    <el-dialog v-model="dialogOpen" :title="editingId ? '编辑获奖等级' : '新增获奖等级'" width="550px" class="apple-dialog">
      <div class="form-grid">
        <div class="form-item full">
          <label>获奖范围 <span class="required">*</span></label>
          <el-select v-model="form.awardScopeId" style="width:100%">
            <el-option v-for="s in scopes" :key="s.id" :label="s.scopeName" :value="s.id" />
          </el-select>
        </div>
        <div class="form-item full">
          <label>等级名称 <span class="required">*</span></label>
          <el-input v-model="form.levelName" placeholder="请输入等级名称" />
        </div>
        <div class="form-item">
          <label>排序</label>
          <el-input-number v-model="form.sortNo" :min="0" style="width:100%" />
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
  --apple-text-secondary: #6e6e73;
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
  width: 140px;
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