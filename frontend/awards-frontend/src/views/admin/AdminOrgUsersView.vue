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

const renderKey = ref(0)
const userStore = useUserStore();
const isSysAdmin = computed(() => userStore.hasAnyRole("SYS_ADMIN"));
const deptsLoading = ref(false);
const depts = ref<Dept[]>([]);
const selectedDeptId = ref<number | undefined | 0>(undefined);
const loading = ref(false);
const page = reactive({
  pageNo: 1,
  pageSize: 12,
  total: 0
});
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
          deptId: selectedDeptId.value === 0 || selectedDeptId.value === undefined ? undefined : selectedDeptId.value,
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

function refresh() {
  selectedDeptId.value = undefined;
  query.username = "";
  query.realName = "";
  query.userType = "";
  query.enabled = undefined;
  query.roleCode = "";
  query.studentNo = "";
  query.teacherNo = "";
  page.pageNo = 1;
  loadUsers();
}

function canOperate(row: UserRow) {
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
const selectedRoleCode = ref<string>("");

function openRoleDialog(row: UserRow) {
  if (!canOperate(row)) {
    ElMessage.warning("无权限操作系统管理员用户");
    return;
  }
  roleEditing.value = row;
  selectedRoleCode.value = row.roles?.[0] ?? "";
  roleDialogOpen.value = true;
}

async function saveRoleImmediately() {
  if (!roleEditing.value) return;
  const roleCodes = selectedRoleCode.value ? [selectedRoleCode.value] : [];
  const resp = await http.put<ApiResponse<null>>(
    `/admin/users/${roleEditing.value.id}/roles`,
    { roleCodes },
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("角色已保存");
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
  renderKey.value++;
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
    <div class="page-header">
      <h1 class="page-title">用户组织</h1>
      <p class="page-subtitle">院系与用户管理</p>
    </div>

    <div class="search-bar">
      <div class="search-inputs">
        <el-input v-model="query.username" placeholder="账号" class="search-input-sm" clearable />
        <el-input v-model="query.realName" placeholder="姓名" class="search-input-sm" clearable />
        <el-select v-model="selectedDeptId" placeholder="院系" clearable filterable style="width:150px"
          @change="page.pageNo = 1; loadUsers()">
          <el-option label="全部" :value="0" />
          <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-select v-model="query.userType" clearable placeholder="类型" class="search-select">
          <el-option label="学生" value="STUDENT" />
          <el-option label="教师" value="TEACHER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
        <el-select v-model="query.enabled" clearable placeholder="状态" class="search-select-sm">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-select v-model="query.roleCode" clearable placeholder="角色" class="search-select" :loading="rolesLoading">
          <el-option v-for="r in roles" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
        </el-select>
      </div>
      <div class="search-actions">
        <el-button type="primary" @click="
          page.pageNo = 1;
        loadUsers();
        ">查询</el-button>
        <el-button @click="refresh">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            style="width:16px;height:16px;margin-right:4px">
            <path d="M23 4v6h-6M1 20v-6h6" />
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
          </svg>
          刷新
        </el-button>
      </div>
    </div>

    <div class="tip-banner">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="16" x2="12" y2="12" />
        <line x1="12" y1="8" x2="12.01" y2="8" />
      </svg>
      <span>校级管理员可分配除「系统管理员」外的角色；若目标用户含「系统管理员」角色，则只有系统管理员可操作。</span>
    </div>

    <div class="user-table" v-loading="loading">
      <el-table 
        :data="rows" 
        style="width: 100%" 
        :key="renderKey"
        :fit="false"
      >
        <el-table-column prop="id" label="编号" width="70" align="center" show-overflow-tooltip />
        <el-table-column prop="username" label="账号" width="124" align="center" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" width="93" align="center" show-overflow-tooltip />
        <el-table-column prop="userType" label="类型" width="93" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="type-tag" :class="row.userType.toLowerCase()">{{
              labelUserType(row.userType)
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="院系" width="130" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ deptNameById[row.deptId] || `ID:${row.deptId}` }}
          </template>
        </el-table-column>
        <el-table-column label="班级" width="100" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.userType === 'STUDENT'">
              {{ row.classId ? (classNameById[row.classId] || `ID:${row.classId}`) : '-' }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="学号/工号" width="120" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.userType === 'STUDENT'">{{ row.studentNo || '-' }}</span>
            <span v-else-if="row.userType === 'TEACHER'">{{ row.teacherNo || '-' }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="enabled" label="状态" width="86" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="row.enabled === 1 ? 'color:#00C48B;font-weight:500' : 'color:#999'">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="130" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.roles && row.roles.length > 0">
              <span v-for="r in row.roles" :key="r" class="role-tag">
                {{ labelRoleCode(r) }}
              </span>
            </template>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="资料" width="74" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button size="small" text @click="openProfileDialog(row)">详情</el-button>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="320" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button size="small" text :disabled="!canOperate(row)" @click="openRoleDialog(row)">
              选择角色
            </el-button>
            <el-button size="small" text :disabled="!canOperate(row)" @click="resetPassword(row)">
              重置密码
            </el-button>
            <el-button size="small" text type="warning" :disabled="!canOperate(row)" @click="toggleUser(row)">
              {{ row.enabled === 1 ? "停用" : "启用" }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination-bar">
      <el-pagination background layout="total, prev, pager, next, sizes" :total="page.total"
        v-model:current-page="page.pageNo" v-model:page-size="page.pageSize" @current-change="loadUsers"
        @size-change="loadUsers" />
    </div>

    <el-dialog v-model="roleDialogOpen" title="选择角色" width="400px" :append-to-body="true" @closed="renderKey++">
      <el-select v-model="selectedRoleCode" placeholder="请选择" style="width:100%">
        <el-option label="无" :value="''" />
        <el-option v-for="r in roles" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
      </el-select>
      <template #footer>
        <el-button @click="roleDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="saveRoleImmediately">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog 
      v-model="profileDialogOpen" 
      title="用户详情" 
      width="520px"
      :append-to-body="true"
      @closed="renderKey++"
    >
      <div class="dialog-user-info">
        <span class="dialog-user-name">{{ profileEditing?.realName }}</span>
        <span class="dialog-user-account">{{ profileEditing?.username }}</span>
      </div>
      <el-form label-width="80px">
        <el-form-item label="院系">
          <el-select v-model="profileForm.deptId" filterable style="width: 100%" @change="onProfileDeptChanged">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级" v-if="profileEditing?.userType === 'STUDENT'">
          <el-select v-model="profileForm.classId" clearable filterable style="width: 100%" :loading="classesLoading">
            <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学号" v-if="profileEditing?.userType === 'STUDENT'">
          <el-input v-model="profileForm.studentNo" />
        </el-form-item>
        <el-form-item label="工号" v-if="profileEditing?.userType === 'TEACHER'">
          <el-input v-model="profileForm.teacherNo" />
        </el-form-item>
      </el-form>
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
  width: 120px;
}

.search-select {
  width: 120px;
}

.search-select-sm {
  width: 100px;
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

.type-tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
}

.type-tag.student {
  background: rgba(52, 199, 89, 0.1);
  color: #34c759;
}

.type-tag.teacher {
  background: rgba(88, 86, 214, 0.1);
  color: #5856d6;
}

.type-tag.admin {
  background: rgba(255, 149, 0, 0.1);
  color: #bca638;
}

.text-muted {
  color: #999;
}

.role-tag {
  font-size: 11px;
  padding: 2px 6px;
  background: #f5f5f7;
  border-radius: 4px;
  margin-right: 4px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  margin-top: 12px;
}

.dialog-user-info {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.dialog-user-name {
  font-weight: 600;
}

.dialog-user-account {
  color: #6e6e73;
  font-size: 13px;
}
</style>