<script setup lang="ts">
/**
 * 学生/教师端 - 我的团队（/app/teams）。
 *
 * 页面能力：
 * - 展示我参与的团队列表
 * - 任何学生都可以创建团队（创建者自动成为该团队的队长）
 * - “我的邀请”：查看收到的团队邀请，并可直接接受/拒绝（邀请处理闭环）
 */
import { computed, onMounted, reactive, ref, watch } from "vue";
import { http } from "../api/http";
import { useUserStore } from "../stores/user";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { ApiResponse } from "../types/api";
import {
  labelInviteStatus,
  labelInviteeType,
  labelTeamStatus,
} from "../utils/displayLabels";

type Team = {
  id: number;
  teamName: string;
  captainUserId: number;
  ownerDeptId: number;
  status: string;
  remark?: string;
};
type Invitation = {
  id: number;
  teamId: number;
  inviteeType: string;
  status: string;
  inviterUserId: number;
  createdAt?: string;
};

// 团队列表 loading
const loading = ref(false);
// 团队列表数据
const teams = ref<Team[]>([]);
// 我创建的团队（我是队长）
const myCreatedTeams = computed(() =>
  teams.value.filter((t) => t.captainUserId === user.me?.id),
);
// 我加入的团队（我不是队长）
const myJoinedTeams = computed(() =>
  teams.value.filter((t) => t.captainUserId !== user.me?.id),
);
// 邀请列表 loading
const invLoading = ref(false);
// 邀请列表数据
const invitations = ref<Invitation[]>([]);
// 邀请弹窗开关
const invOpen = ref(false);

// 创建团队弹窗开关
const createDialog = ref(false);
const user = useUserStore();
// 是否允许创建团队：任何学生都可以创建团队（创建者自动成为该团队的队长）
const canCreate = computed(() => user.me?.userType === "STUDENT");
const router = useRouter();

// 创建团队表单（ownerDeptId 默认取当前用户院系）
const form = reactive({ teamName: "", ownerDeptId: 1, remark: "" });

// 监听当前用户院系：自动回填创建团队的 ownerDeptId
watch(
  () => user.me?.deptId,
  (deptId) => {
    if (deptId != null) form.ownerDeptId = deptId;
  },
  { immediate: true },
);

/**
 * 拉取我参与的团队列表。
 * 接口：GET /api/teams/my
 */
async function load() {
  loading.value = true;
  try {
    const resp = await http.get<ApiResponse<Team[]>>("/teams/my");
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    teams.value = resp.data.data;
  } finally {
    loading.value = false;
  }
}

/**
 * 创建团队。
 * 接口：POST /api/teams
 */
async function createTeam() {
  const resp = await http.post<ApiResponse<number>>("/teams", form);
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  createDialog.value = false;
  form.teamName = "";
  form.remark = "";
  await load();
}

/**
 * 拉取“我收到的邀请”列表。
 * 接口：GET /api/team-invitations/my
 */
async function loadInvitations() {
  invLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<Invitation[]>>(
      "/team-invitations/my",
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    invitations.value = resp.data.data;
  } finally {
    invLoading.value = false;
  }
}

/**
 * 接受邀请。
 * 接口：POST /api/team-invitations/{id}/accept
 */
async function accept(invId: number) {
  const resp = await http.post<ApiResponse<null>>(
    `/team-invitations/${invId}/accept`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已加入团队");
  // 接受后：邀请状态变更 + 团队列表可能变化，因此两边都刷新
  await Promise.all([loadInvitations(), load()]);
}

/**
 * 拒绝邀请。
 * 接口：POST /api/team-invitations/{id}/reject
 */
async function reject(invId: number) {
  const resp = await http.post<ApiResponse<null>>(
    `/team-invitations/${invId}/reject`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已拒绝");
  // 拒绝后：只需要刷新邀请列表
  await loadInvitations();
}

// 页面挂载：加载团队列表
onMounted(load);

// 格式化日期：2026-03-25T19:49:29 -> 2026年3月25日 19:49
function formatDate(dateStr?: string): string {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return dateStr;
  const year = date.getFullYear();
  const month = date.getMonth() + 1;
  const day = date.getDate();
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${year}年${month}月${day}日 ${hour}:${minute}`;
}

// 状态标签类型（邀请状态）
function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'warning',
    ACCEPTED: 'success',
    REJECTED: 'danger',
    INVITED: 'info',
  };
  return map[status] ?? 'info';
}
</script>

<template>
  <div class="teams-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">我的团队</h1>
        <p class="page-subtitle">管理您创建和加入的团队</p>
      </div>
      <div class="header-actions">
        <el-button @click="invOpen = true; loadInvitations()">我的邀请</el-button>
        <el-button v-if="canCreate" type="primary" @click="createDialog = true">创建团队</el-button>
      </div>
    </div>

    <!-- 非学生提示 -->
    <el-alert
      v-if="user.me?.userType !== 'STUDENT'"
      title="教师和管理员不可创建团队，但可以被邀请为指导教师。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 20px"
    />

    <!-- 我创建的团队 -->
    <div class="teams-section" v-if="myCreatedTeams.length > 0">
      <h2 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
        我创建的团队
        <span class="count">{{ myCreatedTeams.length }}</span>
      </h2>
      <div class="teams-grid" v-loading="loading">
        <div v-for="team in myCreatedTeams" :key="team.id" class="team-card captain">
          <div class="team-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <div class="team-info">
            <h3 class="team-name">{{ team.teamName }}</h3>
            <div class="team-meta">
              <span class="meta-item">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                </svg>
                院系 #{{ team.ownerDeptId }}
              </span>
              <span class="meta-item status" :class="team.status?.toLowerCase()">
                {{ labelTeamStatus(team.status) }}
              </span>
            </div>
            <p class="team-remark" v-if="team.remark">{{ team.remark }}</p>
          </div>
          <div class="team-badge captain">队长</div>
          <button class="team-action" @click="router.push(`/app/teams/${team.id}`)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9,18 15,12 9,6"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 我加入的团队 -->
    <div class="teams-section" v-if="myJoinedTeams.length > 0">
      <h2 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
          <circle cx="9" cy="7" r="4"/>
          <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
          <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
        </svg>
        我加入的团队
        <span class="count">{{ myJoinedTeams.length }}</span>
      </h2>
      <div class="teams-grid" v-loading="loading">
        <div v-for="team in myJoinedTeams" :key="team.id" class="team-card member">
          <div class="team-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <div class="team-info">
            <h3 class="team-name">{{ team.teamName }}</h3>
            <div class="team-meta">
              <span class="meta-item">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                </svg>
                院系 #{{ team.ownerDeptId }}
              </span>
              <span class="meta-item status" :class="team.status?.toLowerCase()">
                {{ labelTeamStatus(team.status) }}
              </span>
            </div>
            <p class="team-remark" v-if="team.remark">{{ team.remark }}</p>
          </div>
          <div class="team-badge member">成员</div>
          <button class="team-action" @click="router.push(`/app/teams/${team.id}`)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9,18 15,12 9,6"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div class="empty-state" v-if="!loading && teams.length === 0">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
        <circle cx="9" cy="7" r="4"/>
        <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
        <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
      </svg>
      <h3>暂无团队</h3>
      <p v-if="canCreate">创建一个团队开始您的竞赛之旅</p>
      <p v-else>等待被邀请加入团队</p>
    </div>

    <!-- 创建团队弹窗 -->
    <el-dialog v-model="createDialog" title="创建团队" width="520px">
      <el-form label-width="90px">
        <el-form-item label="团队名称">
          <el-input v-model="form.teamName" placeholder="请输入团队名称" />
        </el-form-item>
        <el-form-item label="归属院系ID">
          <el-input-number v-model="form.ownerDeptId" :min="1" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="可选备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="createTeam">确定</el-button>
      </template>
    </el-dialog>

    <!-- 邀请弹窗 -->
    <el-dialog v-model="invOpen" title="我的团队邀请" width="720px">
      <el-table :data="invitations" v-loading="invLoading" style="width: 100%">
        <el-table-column prop="teamId" label="团队ID" width="90" />
        <el-table-column prop="inviteeType" label="邀请类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.inviteeType === 'MEMBER' ? 'primary' : 'success'">
              {{ labelInviteeType(row.inviteeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">
              {{ labelInviteStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="邀请时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" size="small" type="success" @click="accept(row.id)">接受</el-button>
            <el-button v-if="row.status === 'PENDING'" size="small" type="danger" @click="reject(row.id)">拒绝</el-button>
            <el-button size="small" @click="router.push(`/app/teams/${row.teamId}`)">查看团队</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.teams-page {
  padding: 8px;
}

/* Page Header */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.header-content {
  flex: 1;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--apple-text);
  margin: 0 0 4px 0;
}

.page-subtitle {
  font-size: 14px;
  color: var(--apple-text-secondary);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 8px;
}

/* Section Title */
.teams-section {
  margin-bottom: 32px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 16px 0;
}

.section-title svg {
  width: 20px;
  height: 20px;
  color: var(--apple-primary);
}

.count {
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text-secondary);
  background: var(--apple-bg-secondary);
  padding: 2px 8px;
  border-radius: 10px;
}

/* Teams Grid */
.teams-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

/* Team Card */
.team-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  position: relative;
  transition: all 0.2s;
}

.team-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.team-card.captain {
  border-left: 3px solid var(--apple-primary);
}

.team-card.member {
  border-left: 3px solid #5856D6;
}

.team-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856D6 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.team-icon svg {
  width: 24px;
  height: 24px;
}

.team-info {
  flex: 1;
  min-width: 0;
}

.team-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 8px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.team-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--apple-text-secondary);
}

.meta-item svg {
  width: 14px;
  height: 14px;
}

.meta-item.status.active {
  color: var(--apple-success);
}

.team-remark {
  font-size: 12px;
  color: var(--apple-text-tertiary);
  margin: 8px 0 0 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Team Badge */
.team-badge {
  position: absolute;
  top: 12px;
  right: 48px;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
}

.team-badge.captain {
  background: rgba(0, 122, 255, 0.12);
  color: var(--apple-primary);
}

.team-badge.member {
  background: rgba(88, 86, 214, 0.12);
  color: #5856D6;
}

/* Team Action */
.team-action {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.team-action svg {
  width: 18px;
  height: 18px;
  color: var(--apple-text-tertiary);
}

.team-action:hover {
  background: var(--apple-bg-secondary);
}

.team-action:hover svg {
  color: var(--apple-primary);
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--apple-text-secondary);
}

.empty-state svg {
  width: 64px;
  height: 64px;
  color: var(--apple-text-tertiary);
  margin-bottom: 16px;
}

.empty-state h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 8px 0;
}

.empty-state p {
  font-size: 14px;
  margin: 0;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 16px;
  }

  .teams-grid {
    grid-template-columns: 1fr;
  }
}
</style>
