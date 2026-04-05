<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { http } from "../../api/http";
import { useUserStore } from "../../stores/user";
import type { ApiResponse } from "../../types/api";
import type {
  Team,
  TeamInvitation,
  TeamMember,
  TeamTeacher,
} from "../../types/team";

const route = useRoute();
const user = useUserStore();
const teamId = Number(route.params.id);

const loading = ref(false);
const team = ref<Team | null>(null);

const inviteLoading = ref(false);
// 邀请表单：学号/工号输入，查询后自动填充用户信息
const inviteForm = reactive({
  studentNo: "",
  teacherNo: "",
  memberUserId: undefined as number | undefined,
  memberRealName: "",
  teacherUserId: undefined as number | undefined,
  teacherRealName: "",
});
const memberSearching = ref(false);
const teacherSearching = ref(false);

const membersLoading = ref(false);
const members = ref<TeamMember[]>([]);

const teachersLoading = ref(false);
const teachers = ref<TeamTeacher[]>([]);

const invLoading = ref(false);
const invitations = ref<TeamInvitation[]>([]);


/** 仅用于界面展示，不改变接口入参或业务状态值 */
function formatTeamStatus(status: string | undefined | null) {
  if (!status) return "-";
  const map: Record<string, string> = { ACTIVE: "正常" };
  return map[status] ?? status;
}

function formatTeamRemark(remark: string) {
  return remark === "selftest" ? "自测团队" : remark;
}

function formatJoinStatus(status: string | undefined | null) {
  if (!status) return "-";
  const map: Record<string, string> = {
    ACCEPTED: "已加入",
    PENDING: "待处理",
  };
  return map[status] ?? status;
}

function formatInvitationStatus(status: string | undefined | null) {
  if (!status) return "-";
  const map: Record<string, string> = {
    PENDING: "待处理",
    ACCEPTED: "已接受",
    REJECTED: "已拒绝",
  };
  return map[status] ?? status;
}

async function load() {
  loading.value = true;
  try {
    const resp = await http.get<ApiResponse<Team>>(`/teams/${teamId}`);
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    team.value = resp.data.data;
  } finally {
    loading.value = false;
  }
}

async function loadMembers() {
  membersLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<TeamMember[]>>(
      `/teams/${teamId}/members`,
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    members.value = resp.data.data;
  } finally {
    membersLoading.value = false;
  }
}

async function loadTeachers() {
  teachersLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<TeamTeacher[]>>(
      `/teams/${teamId}/teachers`,
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    teachers.value = resp.data.data;
  } finally {
    teachersLoading.value = false;
  }
}

async function loadInvitations() {
  invLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<TeamInvitation[]>>(
      `/teams/${teamId}/invitations`,
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    invitations.value = resp.data.data;
  } finally {
    invLoading.value = false;
  }
}

// 根据学号查询学生信息
async function searchMember() {
  if (!inviteForm.studentNo.trim()) {
    inviteForm.memberUserId = undefined;
    inviteForm.memberRealName = "";
    return;
  }
  memberSearching.value = true;
  try {
    const resp = await http.get<
      ApiResponse<{ id: number; realName: string } | null>
    >(`/users/by-student-no/${inviteForm.studentNo.trim()}`);
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    if (resp.data.data) {
      inviteForm.memberUserId = resp.data.data.id;
      inviteForm.memberRealName = resp.data.data.realName;
    } else {
      inviteForm.memberUserId = undefined;
      inviteForm.memberRealName = "未找到该学号对应的学生";
      ElMessage.warning("未找到该学号对应的学生");
    }
  } finally {
    memberSearching.value = false;
  }
}

// 根据工号查询教师信息
async function searchTeacher() {
  if (!inviteForm.teacherNo.trim()) {
    inviteForm.teacherUserId = undefined;
    inviteForm.teacherRealName = "";
    return;
  }
  teacherSearching.value = true;
  try {
    const resp = await http.get<
      ApiResponse<{ id: number; realName: string } | null>
    >(`/users/by-teacher-no/${inviteForm.teacherNo.trim()}`);
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    if (resp.data.data) {
      inviteForm.teacherUserId = resp.data.data.id;
      inviteForm.teacherRealName = resp.data.data.realName;
    } else {
      inviteForm.teacherUserId = undefined;
      inviteForm.teacherRealName = "未找到该工号对应的教师";
      ElMessage.warning("未找到该工号对应的教师");
    }
  } finally {
    teacherSearching.value = false;
  }
}

async function inviteMember() {
  if (!inviteForm.memberUserId) {
    ElMessage.warning("请先输入学号查询学生");
    return;
  }
  inviteLoading.value = true;
  try {
    const resp = await http.post<ApiResponse<number>>(
      `/teams/${teamId}/members/invite`,
      { userId: inviteForm.memberUserId },
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    ElMessage.success(`已向 ${inviteForm.memberRealName} 发送邀请`);
    inviteForm.studentNo = "";
    inviteForm.memberUserId = undefined;
    inviteForm.memberRealName = "";
    await loadInvitations();
  } finally {
    inviteLoading.value = false;
  }
}

async function inviteTeacher() {
  if (!inviteForm.teacherUserId) {
    ElMessage.warning("请先输入工号查询教师");
    return;
  }
  inviteLoading.value = true;
  try {
    const resp = await http.post<ApiResponse<number>>(
      `/teams/${teamId}/teachers/invite`,
      { userId: inviteForm.teacherUserId },
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    ElMessage.success(`已向 ${inviteForm.teacherRealName} 发送邀请`);
    inviteForm.teacherNo = "";
    inviteForm.teacherUserId = undefined;
    inviteForm.teacherRealName = "";
    await loadInvitations();
  } finally {
    inviteLoading.value = false;
  }
}

// 是否有管理权限：当前用户是该团队的队长
const canManage = computed(() => team.value?.captainUserId === user.me?.id);

async function removeMember(row: TeamMember) {
  const resp = await http.delete<ApiResponse<null>>(
    `/teams/${teamId}/members/${row.userId}`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已移除");
  await loadMembers();
}

async function removeTeacher(row: TeamTeacher) {
  const resp = await http.delete<ApiResponse<null>>(
    `/teams/${teamId}/teachers/${row.teacherUserId}`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已移除");
  await loadTeachers();
}

onMounted(async () => {
  await user.fetchMe();
  await Promise.all([load(), loadMembers(), loadTeachers(), loadInvitations()]);
});
</script>

<template>
  <div class="team-detail-page" v-loading="loading">
    <!-- 团队信息卡片 -->
    <div class="team-header-card">
      <div class="team-avatar">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
      </div>
      <div class="team-info">
        <h1 class="team-name">{{ team?.teamName || "团队详情" }}</h1>
        <div class="team-meta">
          <span class="meta-item">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
            队长: {{ team?.captainUserId || "-" }}
          </span>
          <span class="meta-item">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <polyline points="9,22 9,12 15,12 15,22" />
            </svg>
            院系: {{ team?.ownerDeptId || "-" }}
          </span>
          <span class="meta-item status" :class="team?.status?.toLowerCase()">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <circle cx="12" cy="12" r="10" />
              <polyline points="12,6 12,12 16,14" />
            </svg>
            {{ formatTeamStatus(team?.status) }}
          </span>
        </div>
        <p class="team-remark" v-if="team?.remark">
          {{ formatTeamRemark(team.remark) }}
        </p>
      </div>
    </div>

    <!-- 邀请面板 (仅队长可见) -->
    <div class="invite-section" v-if="canManage">
      <h2 class="section-title">邀请成员</h2>
      <div class="invite-cards">
        <div class="invite-card">
          <div class="invite-icon member">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="8.5" cy="7" r="4" />
              <line x1="20" y1="8" x2="20" y2="14" />
              <line x1="23" y1="11" x2="17" y2="11" />
            </svg>
          </div>
          <div class="invite-content">
            <h4>邀请队员</h4>
            <p>输入学号查询学生信息，确认后发送邀请</p>
            <div class="invite-form">
              <div class="input-with-search">
                <input
                  type="text"
                  v-model="inviteForm.studentNo"
                  placeholder="输入学号"
                  @keyup.enter="searchMember"
                />
                <button
                  class="search-btn"
                  @click="searchMember"
                  :disabled="memberSearching"
                >
                  {{ memberSearching ? "查询中..." : "查询" }}
                </button>
              </div>
              <div
                v-if="inviteForm.memberRealName"
                class="search-result"
                :class="{ 'not-found': !inviteForm.memberUserId }"
              >
                <span class="result-label">学生姓名：</span>
                <span class="result-value">{{
                  inviteForm.memberRealName
                }}</span>
              </div>
              <button
                class="invite-btn"
                @click="inviteMember"
                :disabled="inviteLoading || !inviteForm.memberUserId"
              >
                {{ inviteLoading ? "发送中..." : "发送邀请" }}
              </button>
            </div>
          </div>
        </div>

        <div class="invite-card">
          <div class="invite-icon teacher">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          </div>
          <div class="invite-content">
            <h4>邀请指导教师</h4>
            <p>输入工号查询教师信息，确认后发送邀请</p>
            <div class="invite-form">
              <div class="input-with-search">
                <input
                  type="text"
                  v-model="inviteForm.teacherNo"
                  placeholder="输入工号"
                  @keyup.enter="searchTeacher"
                />
                <button
                  class="search-btn"
                  @click="searchTeacher"
                  :disabled="teacherSearching"
                >
                  {{ teacherSearching ? "查询中..." : "查询" }}
                </button>
              </div>
              <div
                v-if="inviteForm.teacherRealName"
                class="search-result"
                :class="{ 'not-found': !inviteForm.teacherUserId }"
              >
                <span class="result-label">教师姓名：</span>
                <span class="result-value">{{
                  inviteForm.teacherRealName
                }}</span>
              </div>
              <button
                class="invite-btn"
                @click="inviteTeacher"
                :disabled="inviteLoading || !inviteForm.teacherUserId"
              >
                {{ inviteLoading ? "发送中..." : "发送邀请" }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 成员列表 -->
    <div class="members-section">
      <h2 class="section-title">
        团队成员
        <span class="count">{{ members.length }}人</span>
      </h2>
      <div class="members-grid" v-loading="membersLoading">
        <div v-for="member in members" :key="member.userId" class="member-card">
          <div
            class="member-avatar"
            :class="{ captain: member.isCaptain === 1 }"
          >
            {{ member.user?.realName?.charAt(0) || member.userId }}
          </div>
          <div class="member-info">
            <span class="member-name">{{ member.user?.realName || "-" }}</span>
            <span class="member-username">{{
              member.user?.username || "-"
            }}</span>
          </div>
          <div class="member-badge" :class="member.joinStatus?.toLowerCase()">
            {{ formatJoinStatus(member.joinStatus) }}
          </div>
          <div class="member-captain-badge" v-if="member.isCaptain === 1">
            队长
          </div>
          <button
            v-if="canManage && member.isCaptain !== 1"
            class="member-remove"
            @click="removeMember(member)"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
        <div v-if="members.length === 0 && !membersLoading" class="empty-hint">
          暂无成员
        </div>
      </div>
    </div>

    <!-- 指导教师列表 -->
    <div class="teachers-section">
      <h2 class="section-title">
        指导教师
        <span class="count">{{ teachers.length }}人</span>
      </h2>
      <div class="teachers-grid" v-loading="teachersLoading">
        <div
          v-for="teacher in teachers"
          :key="teacher.teacherUserId"
          class="teacher-card"
        >
          <div class="teacher-avatar">
            {{ teacher.user?.realName?.charAt(0) || teacher.teacherUserId }}
          </div>
          <div class="teacher-info">
            <span class="teacher-name">{{
              teacher.user?.realName || "-"
            }}</span>
            <span class="teacher-username">{{
              teacher.user?.username || "-"
            }}</span>
          </div>
          <div class="teacher-badge" :class="teacher.joinStatus?.toLowerCase()">
            {{ formatJoinStatus(teacher.joinStatus) }}
          </div>
          <button
            v-if="canManage"
            class="teacher-remove"
            @click="removeTeacher(teacher)"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
        <div
          v-if="teachers.length === 0 && !teachersLoading"
          class="empty-hint"
        >
          暂无指导教师
        </div>
      </div>
    </div>

    <!-- 邀请记录 -->
    <div class="invitations-section">
      <h2 class="section-title">邀请记录</h2>
      <div class="invitations-list" v-loading="invLoading">
        <div v-for="inv in invitations" :key="inv.id" class="invitation-item">
          <div class="invitation-type" :class="inv.inviteeType?.toLowerCase()">
            {{ inv.inviteeType === "MEMBER" ? "队员" : "教师" }}
          </div>
          <div class="invitation-info">
            <span class="invitee-name">{{ inv.invitee?.realName || "-" }}</span>
            <span class="invitee-username">{{
              inv.invitee?.username || inv.inviteeUserId
            }}</span>
          </div>
          <div class="invitation-status" :class="inv.status?.toLowerCase()">
            {{ formatInvitationStatus(inv.status) }}
          </div>
          <div class="invitation-meta">
            <span class="inviter"
              >邀请人: {{ inv.inviter?.realName || "-" }}</span
            >
            <span class="time">{{ inv.createdAt }}</span>
          </div>
        </div>
        <div v-if="invitations.length === 0 && !invLoading" class="empty-hint">
          暂无邀请记录
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.team-detail-page {
  padding: 8px;
}

/* Team Header Card */
.team-header-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856d6 100%);
  border-radius: var(--apple-radius-lg);
  margin-bottom: 24px;
  color: white;
}

.team-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.team-avatar svg {
  width: 36px;
  height: 36px;
}

.team-info {
  flex: 1;
}

.team-name {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.team-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  opacity: 0.9;
}

.meta-item svg {
  width: 16px;
  height: 16px;
}

.meta-item.status.active {
  color: #4ade80;
}

.team-remark {
  font-size: 13px;
  opacity: 0.8;
  margin: 12px 0 0 0;
}

/* Section Title */
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 16px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.count {
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text-secondary);
  background: var(--apple-bg-secondary);
  padding: 2px 8px;
  border-radius: 10px;
}

/* Invite Section */
.invite-section {
  margin-bottom: 24px;
}

.invite-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.invite-card {
  display: flex;
  gap: 16px;
  padding: 20px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
}

.invite-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.invite-icon svg {
  width: 24px;
  height: 24px;
}

.invite-icon.member {
  background: rgba(0, 122, 255, 0.12);
  color: var(--apple-primary);
}

.invite-icon.teacher {
  background: rgba(88, 86, 214, 0.12);
  color: #5856d6;
}

.invite-content {
  flex: 1;
}

.invite-content h4 {
  font-size: 15px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 4px 0;
}

.invite-content p {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin: 0 0 12px 0;
}

.invite-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.input-with-search {
  display: flex;
  gap: 8px;
}

.input-with-search input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
  font-size: 13px;
  color: var(--apple-text);
  background: var(--apple-bg-secondary);
  outline: none;
  transition: border-color 0.15s;
}

.input-with-search input:focus {
  border-color: var(--apple-primary);
}

.search-btn {
  padding: 8px 14px;
  background: var(--apple-bg-secondary);
  color: var(--apple-text);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.search-btn:hover:not(:disabled) {
  background: var(--apple-border);
}

.search-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.search-result {
  padding: 8px 12px;
  background: rgba(52, 199, 89, 0.08);
  border: 1px solid rgba(52, 199, 89, 0.2);
  border-radius: var(--apple-radius);
  font-size: 13px;
}

.search-result.not-found {
  background: rgba(255, 59, 48, 0.08);
  border-color: rgba(255, 59, 48, 0.2);
}

.result-label {
  color: var(--apple-text-secondary);
}

.result-value {
  color: var(--apple-text);
  font-weight: 500;
}

.invite-btn {
  padding: 8px 16px;
  background: var(--apple-primary);
  color: white;
  border: none;
  border-radius: var(--apple-radius);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.invite-btn:hover:not(:disabled) {
  background: #0066d6;
}

.invite-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Members Section */
.members-section,
.teachers-section,
.invitations-section {
  margin-bottom: 24px;
}

.members-grid,
.teachers-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.member-card,
.teacher-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
  position: relative;
}

.member-avatar,
.teacher-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--apple-primary) 0%, #5856d6 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.member-avatar.captain {
  background: linear-gradient(135deg, #ff9500 0%, #ff6b00 100%);
}

.member-info,
.teacher-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.member-name,
.teacher-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--apple-text);
}

.member-username,
.teacher-username {
  font-size: 12px;
  color: var(--apple-text-secondary);
}

.member-badge,
.teacher-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 3px 8px;
  border-radius: 6px;
}

.member-badge.active,
.teacher-badge.active {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.member-badge.pending,
.teacher-badge.pending {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.member-captain-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(255, 149, 0, 0.15);
  color: #ff9500;
}

.member-remove,
.teacher-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.member-remove svg,
.teacher-remove svg {
  width: 14px;
  height: 14px;
  color: var(--apple-text-tertiary);
}

.member-remove:hover,
.teacher-remove:hover {
  background: rgba(255, 59, 48, 0.12);
}

.member-remove:hover svg,
.teacher-remove:hover svg {
  color: var(--apple-danger);
}

/* Invitations List */
.invitations-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.invitation-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
}

.invitation-type {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
}

.invitation-type.member {
  background: rgba(0, 122, 255, 0.12);
  color: var(--apple-primary);
}

.invitation-type.teacher {
  background: rgba(88, 86, 214, 0.12);
  color: #5856d6;
}

.invitation-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.invitee-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text);
}

.invitee-username {
  font-size: 12px;
  color: var(--apple-text-secondary);
}

.invitation-status {
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 6px;
}

.invitation-status.pending {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.invitation-status.accepted {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.invitation-status.rejected {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.invitation-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.inviter {
  font-size: 12px;
  color: var(--apple-text-secondary);
}

.time {
  font-size: 11px;
  color: var(--apple-text-tertiary);
}

.empty-hint {
  text-align: center;
  padding: 24px;
  color: var(--apple-text-secondary);
  font-size: 14px;
}

@media (max-width: 768px) {
  .invite-cards {
    grid-template-columns: 1fr;
  }

  .team-header-card {
    flex-direction: column;
    text-align: center;
  }

  .team-meta {
    justify-content: center;
  }
}
</style>
