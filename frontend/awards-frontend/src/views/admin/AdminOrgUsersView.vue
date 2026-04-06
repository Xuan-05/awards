<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { http } from "../../api/http";
import { useUserStore } from "../../stores/user";
import type { ApiResponse, PageResult } from "../../types/api";
import { labelRoleCode, labelUserType } from "../../utils/displayLabels";

type Dept = {
  id: number;
  deptCode: string;
  deptName: string;
  parentId?: number | null;
  enabled: number;
  sortNo: number;
};
type Role = { id: number; roleCode: string; roleName: string };
type ClassRow = {
  id: number;
  deptId: number;
  className: string;
  enabled: number;
  sortNo: number;
};
type DeptTreeNode = Dept & { children: DeptTreeNode[] };
type UserRow = {
  id: number;
  username: string;
  realName: string;
  userType: string;
  deptId: number;
  studentNo?: string;
  teacherNo?: string;
  classId?: number;
  enabled: number;
  roles: string[];
};

const userStore = useUserStore();
const isSysAdmin = computed(() => userStore.hasAnyRole("SYS_ADMIN"));

const deptsLoading = ref(false);
const depts = ref<Dept[]>([]);
const selectedDeptId = ref<number | undefined>(undefined);
const deptTree = computed<DeptTreeNode[]>(() => {
  const byParent: Record<string, Dept[]> = {};
  for (const d of depts.value) {
    const key = String(d.parentId ?? 0);
    if (!byParent[key]) byParent[key] = [];
    byParent[key].push(d);
  }
  for (const k of Object.keys(byParent)) {
    byParent[k].sort(
      (a, b) => (a.sortNo || 0) - (b.sortNo || 0) || a.id - b.id,
    );
  }
  const build = (parentId: number | null): DeptTreeNode[] => {
    const key = String(parentId ?? 0);
    return (byParent[key] || []).map((d) => ({ ...d, children: build(d.id) }));
  };
  return build(null);
});

const loading = ref(false);
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 });
const query = reactive({
  username: "",
  realName: "",
  userType: "",
  enabled: undefined as number | undefined,
  roleCode: "",
  studentNo: "",
  teacherNo: "",
});
const rows = ref<UserRow[]>([]);

const rolesLoading = ref(false);
const roles = ref<Role[]>([]);

const classesLoading = ref(false);
const classes = ref<ClassRow[]>([]);

const deptNameById = computed(() => {
  const m: Record<number, string> = {};
  for (const d of depts.value) m[d.id] = d.deptName;
  return m;
});

const classNameById = computed(() => {
  const m: Record<number, string> = {};
  for (const c of classes.value) m[c.id] = c.className;
  return m;
});

async function loadDepts() {
  deptsLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<Dept[]>>("/depts");
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    depts.value = resp.data.data;
  } finally {
    deptsLoading.value = false;
  }
}

async function loadRoles() {
  rolesLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<Role[]>>("/admin/roles");
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    roles.value = resp.data.data;
  } finally {
    rolesLoading.value = false;
  }
}

async function loadClasses(deptId?: number) {
  classesLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<ClassRow[]>>("/classes", {
      params: { deptId },
    });
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    classes.value = resp.data.data;
  } finally {
    classesLoading.value = false;
  }
}

async function loadUsers() {
  loading.value = true;
  try {
    const resp = await http.get<ApiResponse<PageResult<UserRow>>>(
      "/admin/users",
      {
        params: {
          pageNo: page.pageNo,
          pageSize: page.pageSize,
          username: query.username || undefined,
          realName: query.realName || undefined,
          userType: query.userType || undefined,
          studentNo: query.studentNo || undefined,
          teacherNo: query.teacherNo || undefined,
          enabled: query.enabled,
          deptId: selectedDeptId.value,
          roleCode: query.roleCode || undefined,
        },
      },
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    rows.value = resp.data.data.list;
    page.total = resp.data.data.total;
  } finally {
    loading.value = false;
  }
}

function canOperate(row: UserRow) {
  // 非 SYS_ADMIN 不允许对 SYS_ADMIN 用户做危险操作
  if (!isSysAdmin.value && row.roles?.includes("SYS_ADMIN")) return false;
  return true;
}

async function toggleUser(row: UserRow) {
  if (!canOperate(row)) {
    ElMessage.warning("无权限操作系统管理员用户");
    return;
  }
  await ElMessageBox.confirm(
    `确认要${row.enabled === 1 ? "停用" : "启用"}用户「${row.realName}」吗？`,
    "提示",
    { type: "warning" },
  );
  const resp = await http.post<ApiResponse<null>>(
    `/admin/users/${row.id}/toggle`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已更新");
  await loadUsers();
}

async function resetPassword(row: UserRow) {
  if (!canOperate(row)) {
    ElMessage.warning("无权限操作系统管理员用户");
    return;
  }
  await ElMessageBox.confirm(
    `确认重置「${row.realName}」密码为 Admin123! 吗？`,
    "提示",
    { type: "warning" },
  );
  const resp = await http.post<ApiResponse<null>>(
    `/admin/users/${row.id}/reset-password`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已重置");
}

const roleDialogOpen = ref(false);
const roleEditing = ref<UserRow | null>(null);
const roleForm = reactive({ roleCodes: [] as string[] });

function openRoleDialog(row: UserRow) {
  if (!canOperate(row)) {
    ElMessage.warning("无权限操作系统管理员用户");
    return;
  }
  roleEditing.value = row;
  roleForm.roleCodes = [...(row.roles || [])];
  roleDialogOpen.value = true;
}

async function saveRoles() {
  if (!roleEditing.value) return;
  const resp = await http.put<ApiResponse<null>>(
    `/admin/users/${roleEditing.value.id}/roles`,
    { roleCodes: roleForm.roleCodes },
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已保存");
  roleDialogOpen.value = false;
  await loadUsers();
}

const profileDialogOpen = ref(false);
const profileEditing = ref<UserRow | null>(null);
const profileForm = reactive({
  deptId: undefined as number | undefined,
  classId: undefined as number | undefined,
  studentNo: "",
  teacherNo: "",
});

async function openProfileDialog(row: UserRow) {
  if (!canOperate(row)) {
    ElMessage.warning("无权限操作系统管理员用户");
    return;
  }
  profileEditing.value = row;
  profileForm.deptId = row.deptId;
  profileForm.classId = row.classId;
  profileForm.studentNo = row.studentNo || "";
  profileForm.teacherNo = row.teacherNo || "";
  if (row.userType === "STUDENT") {
    await loadClasses(row.deptId);
  } else {
    classes.value = [];
  }
  profileDialogOpen.value = true;
}

async function onProfileDeptChanged() {
  if (profileEditing.value?.userType !== "STUDENT") return;
  profileForm.classId = undefined;
  if (profileForm.deptId) await loadClasses(profileForm.deptId);
}

async function saveProfile() {
  if (!profileEditing.value) return;
  const payload: any = { deptId: profileForm.deptId };
  if (profileEditing.value.userType === "STUDENT") {
    payload.classId = profileForm.classId;
    payload.studentNo = profileForm.studentNo || undefined;
  }
  if (profileEditing.value.userType === "TEACHER") {
    payload.teacherNo = profileForm.teacherNo || undefined;
  }
  const resp = await http.put<ApiResponse<null>>(
    `/admin/users/${profileEditing.value.id}/profile`,
    payload,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已保存");
  profileDialogOpen.value = false;
  await loadUsers();
}

onMounted(async () => {
  if (!userStore.meLoaded) await userStore.fetchMe();
  await Promise.all([loadDepts(), loadRoles()]);
  await loadUsers();
});
</script>

<template>
  <div class="org-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">用户组织</h1>
      <p class="page-subtitle">院系与用户管理</p>
    </div>

    <div class="org-layout">
      <!-- 左侧院系树 -->
      <div class="dept-panel">
        <div class="panel-header">
          <h3>院系结构</h3>
        </div>
        <div class="tree-container" v-loading="deptsLoading">
          <el-tree
            node-key="id"
            :data="[{ id: 0, deptName: '全部院系', children: deptTree }]"
            :props="{ label: 'deptName', children: 'children' }"
            default-expand-all
            :expand-on-click-node="false"
            @node-click="
              (node) => {
                selectedDeptId =
                  node?.id && node.id !== 0 ? Number(node.id) : undefined;
                page.pageNo = 1;
                loadUsers();
              }
            "
          >
            <template #default="{ node, data }">
              <span class="tree-node" :class="{ 'is-root': data.id === 0 }">
                <svg
                  v-if="data.children?.length"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  class="tree-icon"
                >
                  <path
                    d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"
                  />
                </svg>
                <svg
                  v-else
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  class="tree-icon leaf"
                >
                  <path
                    d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"
                  />
                  <polyline points="14,2 14,8 20,8" />
                </svg>
                <span class="tree-label">{{ node.label }}</span>
                <span v-if="data.id !== 0" class="tree-count">{{
                  data.id
                }}</span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>

      <!-- 右侧用户列表 -->
      <div class="user-panel">
        <!-- 搜索栏 -->
        <div class="search-bar">
          <div class="search-inputs">
            <el-input
              v-model="query.username"
              placeholder="账号"
              class="search-input-sm"
              clearable
            />
            <el-input
              v-model="query.realName"
              placeholder="姓名"
              class="search-input-sm"
              clearable
            />
            <el-select
              v-model="query.userType"
              clearable
              placeholder="类型"
              class="search-select"
            >
              <el-option label="学生" value="STUDENT" />
              <el-option label="教师" value="TEACHER" />
              <el-option label="管理员" value="ADMIN" />
            </el-select>
            <el-select
              v-model="query.enabled"
              clearable
              placeholder="状态"
              class="search-select-sm"
            >
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
            <el-select
              v-model="query.roleCode"
              clearable
              placeholder="角色"
              class="search-select"
              :loading="rolesLoading"
            >
              <el-option
                v-for="r in roles"
                :key="r.roleCode"
                :label="r.roleName"
                :value="r.roleCode"
              />
            </el-select>
          </div>
          <div class="search-actions">
            <el-button
              type="primary"
              @click="
                page.pageNo = 1;
                loadUsers();
              "
              >查询</el-button
            >
            <el-button @click="loadUsers">刷新</el-button>
          </div>
        </div>

        <!-- 提示 -->
        <div class="tip-banner">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="16" x2="12" y2="12" />
            <line x1="12" y1="8" x2="12.01" y2="8" />
          </svg>
          <span
            >校级管理员可分配除「系统管理员」外的角色；若目标用户含「系统管理员」角色，则只有系统管理员可操作。</span
          >
        </div>

        <!-- 用户表格 -->
        <div class="user-table" v-loading="loading">
          <el-table :data="rows" style="width: 100%">
            <el-table-column prop="id" label="编号" width="70" />
            <el-table-column prop="username" label="账号" width="140" />
            <el-table-column prop="realName" label="姓名" width="100" />
            <el-table-column prop="userType" label="类型" width="80">
              <template #default="{ row }">
                <span class="type-tag" :class="row.userType.toLowerCase()">{{
                  labelUserType(row.userType)
                }}</span>
              </template>
            </el-table-column>
            <el-table-column label="院系" width="140">
              <template #default="{ row }">
                {{ deptNameById[row.deptId] || `ID:${row.deptId}` }}
              </template>
            </el-table-column>
            <el-table-column label="班级" width="120">
              <template #default="{ row }">
                <span v-if="row.userType === 'STUDENT'">{{
                  row.classId
                    ? classNameById[row.classId] || `ID:${row.classId}`
                    : "-"
                }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="学号/工号" width="120">
              <template #default="{ row }">
                <span v-if="row.userType === 'STUDENT'">{{
                  row.studentNo || "-"
                }}</span>
                <span v-else-if="row.userType === 'TEACHER'">{{
                  row.teacherNo || "-"
                }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <span
                  class="status-dot"
                  :class="row.enabled === 1 ? 'active' : 'inactive'"
                ></span>
                {{ row.enabled === 1 ? "启用" : "停用" }}
              </template>
            </el-table-column>
            <el-table-column prop="roles" label="角色" min-width="180">
              <template #default="{ row }">
                <span v-for="r in row.roles" :key="r" class="role-tag">{{
                  labelRoleCode(r)
                }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button
                    size="small"
                    text
                    :disabled="!canOperate(row)"
                    @click="openProfileDialog(row)"
                    >资料</el-button
                  >
                  <el-button
                    size="small"
                    text
                    :disabled="!canOperate(row)"
                    @click="openRoleDialog(row)"
                    >角色</el-button
                  >
                  <el-button
                    size="small"
                    text
                    :disabled="!canOperate(row)"
                    @click="resetPassword(row)"
                    >重置密码</el-button
                  >
                  <el-button
                    size="small"
                    text
                    type="warning"
                    :disabled="!canOperate(row)"
                    @click="toggleUser(row)"
                  >
                    {{ row.enabled === 1 ? "停用" : "启用" }}
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 分页 -->
        <div class="pagination-bar">
          <el-pagination
            background
            layout="total, prev, pager, next, sizes"
            :total="page.total"
            v-model:current-page="page.pageNo"
            v-model:page-size="page.pageSize"
            @change="loadUsers"
          />
        </div>
      </div>
    </div>

    <!-- 角色编辑弹窗 -->
    <el-dialog
      v-model="roleDialogOpen"
      title="编辑角色"
      width="480px"
      class="apple-dialog"
    >
      <div class="dialog-user-info">
        <span class="dialog-user-name">{{ roleEditing?.realName }}</span>
        <span class="dialog-user-account">{{ roleEditing?.username }}</span>
      </div>
      <div class="dialog-field">
        <label>角色</label>
        <el-select
          v-model="roleForm.roleCodes"
          multiple
          filterable
          style="width: 100%"
          :loading="rolesLoading"
        >
          <el-option
            v-for="r in roles"
            :key="r.roleCode"
            :label="r.roleName || labelRoleCode(r.roleCode)"
            :value="r.roleCode"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="roleDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>

    <!-- 资料编辑弹窗 -->
    <el-dialog
      v-model="profileDialogOpen"
      title="编辑资料"
      width="520px"
      class="apple-dialog"
    >
      <div class="dialog-user-info">
        <span class="dialog-user-name">{{ profileEditing?.realName }}</span>
        <span class="dialog-user-account">{{ profileEditing?.username }}</span>
        <span class="dialog-user-type">{{ labelUserType(profileEditing?.userType) }}</span>
      </div>
      <div class="dialog-field">
        <label>院系</label>
        <el-select
          v-model="profileForm.deptId"
          filterable
          style="width: 100%"
          @change="onProfileDeptChanged"
        >
          <el-option
            v-for="d in depts"
            :key="d.id"
            :label="d.deptName"
            :value="d.id"
          />
        </el-select>
      </div>
      <div v-if="profileEditing?.userType === 'STUDENT'" class="dialog-field">
        <label>班级</label>
        <el-select
          v-model="profileForm.classId"
          clearable
          filterable
          style="width: 100%"
          :loading="classesLoading"
          @visible-change="
            (v) => {
              if (v && profileForm.deptId) loadClasses(profileForm.deptId);
            }
          "
        >
          <el-option
            v-for="c in classes"
            :key="c.id"
            :label="c.className"
            :value="c.id"
          />
        </el-select>
      </div>
      <div v-if="profileEditing?.userType === 'STUDENT'" class="dialog-field">
        <label>学号</label>
        <el-input
          v-model="profileForm.studentNo"
          placeholder="可为空；非空唯一"
        />
      </div>
      <div v-if="profileEditing?.userType === 'TEACHER'" class="dialog-field">
        <label>工号</label>
        <el-input
          v-model="profileForm.teacherNo"
          placeholder="可为空；非空唯一"
        />
      </div>
      <template #footer>
        <el-button @click="profileDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="saveProfile">保存</el-button>
      </template>
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
  color: var(--apple-text);
  margin: 0 0 4px 0;
  letter-spacing: -0.5px;
}

.page-subtitle {
  font-size: 14px;
  color: var(--apple-text-secondary);
  margin: 0;
}

.org-layout {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

/* Department Panel */
.dept-panel {
  width: 260px;
  flex-shrink: 0;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--apple-border);
}

.panel-header h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0;
}

.tree-container {
  flex: 1;
  overflow: auto;
  padding: 12px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.15s;
}

.tree-node:hover {
  background: var(--apple-bg-secondary);
}

.tree-node.is-root {
  font-weight: 600;
}

.tree-icon {
  width: 16px;
  height: 16px;
  color: var(--apple-primary);
  flex-shrink: 0;
}

.tree-icon.leaf {
  color: var(--apple-text-tertiary);
}

.tree-label {
  font-size: 13px;
  color: var(--apple-text);
  flex: 1;
}

.tree-count {
  font-size: 11px;
  color: var(--apple-text-tertiary);
  background: var(--apple-bg-secondary);
  padding: 2px 6px;
  border-radius: 4px;
}

/* User Panel */
.user-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
}

.search-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  flex-wrap: wrap;
}

.search-input-sm {
  width: 100px;
}

.search-select {
  width: 100px;
}

.search-select-sm {
  width: 80px;
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
  border-radius: var(--apple-radius);
  font-size: 13px;
  color: var(--apple-text-secondary);
}

.tip-banner svg {
  width: 16px;
  height: 16px;
  color: var(--apple-primary);
  flex-shrink: 0;
}

.user-table {
  flex: 1;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  overflow: hidden;
}

.type-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
}

.type-tag.student {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.type-tag.teacher {
  background: rgba(88, 86, 214, 0.12);
  color: #5856d6;
}

.type-tag.admin {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.text-muted {
  color: var(--apple-text-tertiary);
}

.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 6px;
}

.status-dot.active {
  background: var(--apple-success);
}

.status-dot.inactive {
  background: var(--apple-text-tertiary);
}

.role-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--apple-bg-secondary);
  color: var(--apple-text-secondary);
  margin-right: 4px;
  margin-bottom: 2px;
}

.action-btns {
  display: flex;
  gap: 4px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
}

/* Dialog Styles */
.apple-dialog .dialog-user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--apple-border);
}

.dialog-user-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--apple-text);
}

.dialog-user-account {
  font-size: 13px;
  color: var(--apple-text-secondary);
}

.dialog-user-type {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.dialog-field {
  margin-bottom: 16px;
}

.dialog-field label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text-secondary);
  margin-bottom: 8px;
}
</style>
