<script setup lang="ts">
/**
 * 学生/教师端 - 消息中心（/app/messages）。
 *
 * 核心能力：
 * - 分页列表：未读/已读切换，按类型/关键字筛选
 * - 读/删：单条标记已读、全部已读、删除
 * - 邀请闭环：当消息 bizType=TEAM_INVITATION 时，可在详情中直接“接受/拒绝”
 */
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { http } from "../../api/http";
import type { ApiResponse, PageResult } from "../../types/api";

type MessageRow = {
  /** 消息ID（sys_message.id） */
  id: number;
  /** 消息类型（INVITATION/AUDIT/NOTICE） */
  msgType: string;
  /** 标题 */
  title: string;
  /** 内容 */
  content: string;
  /** 业务类型（用于前端联动） */
  bizType?: string;
  /** 业务ID（用于前端联动） */
  bizId?: number;
  /** 已读标记：0 未读 / 1 已读 */
  readFlag: number;
  /** 创建时间 */
  createdAt: string;
};

type Invitation = {
  id: number;
  teamId: number;
  inviteeUserId: number;
  inviteeType: string;
  status: string;
  inviterUserId: number;
  createdAt: string;
};

// 当前 tab：未读/已读
const active = ref<"unread" | "read">("unread");
// 列表 loading
const loading = ref(false);
// 列表数据
const rows = ref<MessageRow[]>([]);
// 分页信息（Element Plus v-model 绑定）
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 });
// 查询条件
const query = reactive({ keyword: "", msgType: "" });

// 未读数量（用于 tab 徽标显示）
const unreadCount = ref(0);

/**
 * 拉取未读数量。
 * 接口：GET /api/messages/unread-count
 */
async function loadUnreadCount() {
  const resp = await http.get<ApiResponse<{ unreadCount: number }>>(
    "/messages/unread-count",
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  unreadCount.value = resp.data.data.unreadCount;
}

/**
 * 拉取消息分页列表。
 * 接口：GET /api/messages
 */
async function load() {
  loading.value = true;
  try {
    const resp = await http.get<ApiResponse<PageResult<MessageRow>>>(
      "/messages",
      {
        params: {
          pageNo: page.pageNo,
          pageSize: page.pageSize,
          readFlag: active.value === "unread" ? 0 : 1,
          msgType: query.msgType || undefined,
          keyword: query.keyword || undefined,
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

/**
 * 标记某条消息已读。
 * 接口：POST /api/messages/{id}/read
 */
async function markRead(id: number) {
  const resp = await http.post<ApiResponse<null>>(`/messages/${id}/read`);
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  // 标记已读会影响列表与未读数，因此同时刷新
  await Promise.all([load(), loadUnreadCount()]);
}

/**
 * 全部标记为已读（仅当前用户）。
 * 接口：POST /api/messages/read-all
 */
async function readAll() {
  const resp = await http.post<ApiResponse<null>>("/messages/read-all", null);
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已全部标记为已读");
  await Promise.all([load(), loadUnreadCount()]);
}

/**
 * 删除消息（物理删除，仅删除当前用户自己的消息）。
 * 接口：DELETE /api/messages/{id}
 */
async function removeMsg(id: number) {
  await ElMessageBox.confirm("确认删除这条消息吗？", "提示", {
    type: "warning",
  });
  const resp = await http.delete<ApiResponse<null>>(`/messages/${id}`);
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已删除");
  await Promise.all([load(), loadUnreadCount()]);
}

// 详情抽屉开关
const detailOpen = ref(false);
// 当前选中的消息
const current = ref<MessageRow | null>(null);

/**
 * 打开详情抽屉。
 * - 双击行或点击“详情”按钮都会触发
 * - 若当前消息未读，会自动标记为已读
 */
function openDetail(row: MessageRow) {
  current.value = row;
  detailOpen.value = true;
  if (row.readFlag === 0) markRead(row.id);
}

// 是否是“团队邀请类消息”：用于决定是否显示“邀请处理”卡片
const isInvitationMsg = computed(
  () => current.value?.bizType === "TEAM_INVITATION" && !!current.value?.bizId,
);

// 邀请卡片 loading
const invitationLoading = ref(false);
// 当前消息对应的邀请对象（从“我的邀请列表”中找）
const invitation = ref<Invitation | null>(null);

/**
 * 加载邀请详情（通过 /team-invitations/my 拉取列表后按 id 查找）。
 *
 * 说明：这里复用“我的邀请”接口，避免额外新增“按 id 获取邀请”的接口。
 */
async function loadInvitation(invId: number) {
  invitationLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<Invitation[]>>(
      "/team-invitations/my",
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    invitation.value =
      (resp.data.data || []).find((x) => x.id === invId) || null;
  } finally {
    invitationLoading.value = false;
  }
}

/**
 * 邀请处理（接受/拒绝）。
 * 接口：
 * - 接受：POST /api/team-invitations/{id}/accept
 * - 拒绝：POST /api/team-invitations/{id}/reject
 */
async function handleInvitation(invId: number, action: "accept" | "reject") {
  const resp = await http.post<ApiResponse<null>>(
    `/team-invitations/${invId}/${action}`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success(action === "accept" ? "已接受邀请" : "已拒绝邀请");
  // 处理邀请后：消息列表/未读数/邀请状态都可能变化，因此都刷新
  await Promise.all([load(), loadUnreadCount()]);
  await loadInvitation(invId);
}

// 页面挂载：并发加载未读数与列表
onMounted(async () => {
  await Promise.all([loadUnreadCount(), load()]);
});

// 切换 tab
function switchTab(tab: "unread" | "read") {
  active.value = tab;
  page.pageNo = 1;
  load();
}

// 获取消息类型标签
function getMsgTypeLabel(type: string): string {
  const map: Record<string, string> = {
    INVITATION: "团队邀请",
    AUDIT: "审核通知",
    NOTICE: "通知",
  };
  return map[type] || type;
}

// 获取消息类型样式类
function getMsgTypeClass(type: string): string {
  const map: Record<string, string> = {
    INVITATION: "invitation",
    AUDIT: "audit",
    NOTICE: "notice",
  };
  return map[type] || "notice";
}

// 格式化时间
function formatTime(time: string): string {
  if (!time) return "-";
  const date = new Date(time);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 7) return `${days}天前`;
  return time.slice(0, 10);
}

// 格式化完整日期时间：2026-03-25T19:49:29 -> 2026年3月25日 19:49
function formatDateTime(dateStr?: string): string {
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

// 邀请状态文本映射
function invitationStatusText(status: string): string {
  const map: Record<string, string> = {
    PENDING: '等待处理',
    ACCEPTED: '已接受',
    REJECTED: '已拒绝',
  };
  return map[status] || status;
}
</script>

<template>
  <div class="messages-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">消息中心</h1>
        <p class="page-subtitle">查看系统通知和团队消息</p>
      </div>
      <div class="header-actions">
        <button
          v-if="active === 'unread'"
          class="action-btn success"
          @click="readAll"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <polyline points="20,6 9,17 4,12" />
          </svg>
          全部已读
        </button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-tabs">
        <button
          class="filter-tab"
          :class="{ active: active === 'unread' }"
          @click="switchTab('unread')"
        >
          未读
          <span class="badge" v-if="unreadCount > 0">{{ unreadCount }}</span>
        </button>
        <button
          class="filter-tab"
          :class="{ active: active === 'read' }"
          @click="switchTab('read')"
        >
          已读
        </button>
      </div>
      <div class="filter-actions">
        <select
          v-model="query.msgType"
          @change="
            page.pageNo = 1;
            load();
          "
          class="filter-select"
        >
          <option value="">全部类型</option>
          <option value="INVITATION">团队邀请</option>
          <option value="AUDIT">审核通知</option>
          <option value="NOTICE">通知</option>
        </select>
        <input
          type="text"
          v-model="query.keyword"
          placeholder="搜索消息..."
          class="filter-input"
          @keyup.enter="
            page.pageNo = 1;
            load();
          "
        />
        <button
          class="filter-btn"
          @click="
            page.pageNo = 1;
            load();
          "
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
        </button>
        <button class="filter-btn refresh" @click="load">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <polyline points="23,4 23,10 17,10" />
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 消息列表 -->
    <div class="messages-list" v-loading="loading">
      <div
        v-for="msg in rows"
        :key="msg.id"
        class="message-item"
        :class="{ unread: msg.readFlag === 0 }"
        @click="openDetail(msg)"
      >
        <div class="message-avatar" :class="getMsgTypeClass(msg.msgType)">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <template v-if="msg.msgType === 'INVITATION'">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="8.5" cy="7" r="4" />
              <line x1="20" y1="8" x2="20" y2="14" />
              <line x1="23" y1="11" x2="17" y2="11" />
            </template>
            <template v-else-if="msg.msgType === 'AUDIT'">
              <path d="M9 11l3 3L22 4" />
              <path
                d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"
              />
            </template>
            <template v-else>
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
              <path d="M13.73 21a2 2 0 0 1-3.46 0" />
            </template>
          </svg>
        </div>
        <div class="message-content">
          <div class="message-header">
            <span class="message-type">{{ getMsgTypeLabel(msg.msgType) }}</span>
            <span class="message-time">{{ formatTime(msg.createdAt) }}</span>
          </div>
          <h4 class="message-title">{{ msg.title }}</h4>
          <p class="message-preview">{{ msg.content }}</p>
        </div>
        <div class="message-status">
          <span class="status-dot" v-if="msg.readFlag === 0"></span>
        </div>
        <div class="message-actions" @click.stop>
          <button
            v-if="msg.readFlag === 0"
            class="msg-btn read"
            @click="markRead(msg.id)"
            title="标记已读"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <polyline points="20,6 9,17 4,12" />
            </svg>
          </button>
          <button
            class="msg-btn delete"
            @click="removeMsg(msg.id)"
            title="删除"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <polyline points="3,6 5,6 21,6" />
              <path
                d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
              />
            </svg>
          </button>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="rows.length === 0 && !loading" class="empty-state">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path
            d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"
          />
        </svg>
        <p>{{ active === "unread" ? "暂无未读消息" : "暂无已读消息" }}</p>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination-bar" v-if="rows.length > 0">
      <span class="pagination-info">共 {{ page.total }} 条消息</span>
      <div class="pagination-controls">
        <button
          class="page-btn"
          :disabled="page.pageNo <= 1"
          @click="
            page.pageNo--;
            load();
          "
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <polyline points="15,18 9,12 15,6" />
          </svg>
        </button>
        <span class="page-num"
          >{{ page.pageNo }} / {{ Math.ceil(page.total / page.pageSize) }}</span
        >
        <button
          class="page-btn"
          :disabled="page.pageNo >= Math.ceil(page.total / page.pageSize)"
          @click="
            page.pageNo++;
            load();
          "
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <polyline points="9,18 15,12 9,6" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 详情抽屉 -->
    <div
      class="detail-drawer"
      :class="{ open: detailOpen }"
      @click.self="detailOpen = false"
    >
      <div class="drawer-content">
        <div class="drawer-header">
          <h3>消息详情</h3>
          <button class="close-btn" @click="detailOpen = false">
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

        <div class="drawer-body" v-if="current">
          <div class="detail-section">
            <div class="detail-row">
              <span class="detail-label">类型</span>
              <span class="detail-value">{{
                getMsgTypeLabel(current.msgType)
              }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">时间</span>
              <span class="detail-value">{{ formatDateTime(current.createdAt) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">标题</span>
              <span class="detail-value">{{ current.title }}</span>
            </div>
            <div class="detail-row full">
              <span class="detail-label">内容</span>
              <p class="detail-content">{{ current.content }}</p>
            </div>
          </div>

          <!-- 邀请处理卡片 -->
          <div
            class="invitation-card"
            v-if="isInvitationMsg"
            v-loading="invitationLoading"
          >
            <h4 class="card-title">邀请处理</h4>
            <div v-if="current?.bizId && !invitation">
              <button class="load-btn" @click="loadInvitation(current.bizId)">
                加载邀请状态
              </button>
            </div>
            <div v-else-if="invitation">
              <div class="invitation-info">
                <div class="info-row">
                  <span>团队ID</span>
                  <span>{{ invitation.teamId }}</span>
                </div>
                <div class="info-row">
                  <span>邀请类型</span>
                  <span class="type-badge" :class="invitation.inviteeType.toLowerCase()">
                    {{ invitation.inviteeType === 'MEMBER' ? '队员' : '指导教师' }}
                  </span>
                </div>
                <div class="info-row">
                  <span>状态</span>
                  <span
                    class="status-badge"
                    :class="invitation.status.toLowerCase()"
                  >
                    {{ invitationStatusText(invitation.status) }}
                  </span>
                </div>
                <div class="info-row">
                  <span>邀请时间</span>
                  <span>{{ formatDateTime(invitation.createdAt) }}</span>
                </div>
              </div>
              <div
                class="invitation-actions"
                v-if="invitation.status === 'PENDING' && current?.bizId"
              >
                <button
                  class="accept-btn"
                  @click="handleInvitation(current.bizId, 'accept')"
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <polyline points="20,6 9,17 4,12" />
                  </svg>
                  接受邀请
                </button>
                <button
                  class="reject-btn"
                  @click="handleInvitation(current.bizId, 'reject')"
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
                  拒绝
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.messages-page {
  padding: 8px;
}

/* Page Header */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  font-size: 24px;
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

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
}

.action-btn svg {
  width: 18px;
  height: 18px;
}

.action-btn.success {
  background: var(--apple-success);
  color: white;
}

.action-btn.success:hover {
  background: #34c759;
  box-shadow: 0 4px 12px rgba(52, 199, 89, 0.3);
}

/* Filter Bar */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
}

.filter-tabs {
  display: flex;
  gap: 4px;
}

.filter-tab {
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: var(--apple-text-secondary);
  font-size: 14px;
  font-weight: 500;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-tab:hover {
  background: var(--apple-bg-secondary);
  color: var(--apple-text);
}

.filter-tab.active {
  background: var(--apple-primary);
  color: white;
}

.filter-tab .badge {
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}

.filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.filter-select {
  padding: 8px 12px;
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
  font-size: 13px;
  color: var(--apple-text);
  background: var(--apple-bg-secondary);
  cursor: pointer;
  outline: none;
  min-width: 120px;
}

.filter-input {
  padding: 8px 12px;
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
  font-size: 13px;
  color: var(--apple-text);
  background: var(--apple-bg-secondary);
  outline: none;
  width: 180px;
}

.filter-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--apple-border);
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.filter-btn svg {
  width: 18px;
  height: 18px;
  color: var(--apple-text-secondary);
}

.filter-btn:hover {
  background: var(--apple-primary);
  border-color: var(--apple-primary);
}

.filter-btn:hover svg {
  color: white;
}

/* Messages List */
.messages-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  cursor: pointer;
  transition: all 0.2s;
}

.message-item:hover {
  border-color: var(--apple-primary);
  transform: translateX(4px);
}

.message-item.unread {
  background: rgba(0, 122, 255, 0.02);
  border-left: 3px solid var(--apple-primary);
}

.message-avatar {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-avatar svg {
  width: 24px;
  height: 24px;
}

.message-avatar.invitation {
  background: rgba(0, 122, 255, 0.12);
  color: var(--apple-primary);
}

.message-avatar.audit {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.message-avatar.notice {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}

.message-type {
  font-size: 12px;
  font-weight: 600;
  color: var(--apple-primary);
}

.message-time {
  font-size: 12px;
  color: var(--apple-text-tertiary);
}

.message-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 4px 0;
}

.message-preview {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-status {
  display: flex;
  align-items: center;
  padding: 0 8px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--apple-primary);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.message-actions {
  display: flex;
  gap: 4px;
}

.msg-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.msg-btn svg {
  width: 16px;
  height: 16px;
}

.msg-btn.read svg {
  color: var(--apple-success);
}

.msg-btn.delete svg {
  color: var(--apple-danger);
}

.msg-btn:hover {
  background: var(--apple-bg-secondary);
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
}

.empty-state svg {
  width: 48px;
  height: 48px;
  color: var(--apple-text-tertiary);
  margin-bottom: 16px;
}

.empty-state p {
  font-size: 15px;
  color: var(--apple-text-secondary);
  margin: 0;
}

/* Pagination */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding: 12px 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
}

.pagination-info {
  font-size: 13px;
  color: var(--apple-text-secondary);
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--apple-border);
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.page-btn svg {
  width: 16px;
  height: 16px;
  color: var(--apple-text-secondary);
}

.page-btn:hover:not(:disabled) {
  background: var(--apple-bg-secondary);
  border-color: var(--apple-primary);
}

.page-btn:hover:not(:disabled) svg {
  color: var(--apple-primary);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-num {
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text);
}

/* Detail Drawer */
.detail-drawer {
  position: fixed;
  top: 0;
  right: 0;
  width: 100%;
  height: 100%;
  z-index: 1000;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.3s;
}

.detail-drawer.open {
  pointer-events: auto;
  opacity: 1;
}

.drawer-content {
  position: absolute;
  right: 0;
  top: 0;
  width: 480px;
  max-width: 100%;
  height: 100%;
  background: var(--apple-bg);
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.15);
  transform: translateX(100%);
  transition: transform 0.3s;
}

.detail-drawer.open .drawer-content {
  transform: translateX(0);
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--apple-border);
}

.drawer-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0;
}

.close-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.close-btn svg {
  width: 20px;
  height: 20px;
  color: var(--apple-text-secondary);
}

.close-btn:hover {
  background: var(--apple-bg-secondary);
}

.drawer-body {
  padding: 24px;
  overflow-y: auto;
  height: calc(100% - 72px);
}

.detail-section {
  margin-bottom: 24px;
}

.detail-row {
  display: flex;
  margin-bottom: 16px;
}

.detail-row.full {
  flex-direction: column;
}

.detail-label {
  font-size: 13px;
  color: var(--apple-text-secondary);
  width: 60px;
  flex-shrink: 0;
}

.detail-value {
  font-size: 14px;
  color: var(--apple-text);
  font-weight: 500;
}

.detail-content {
  font-size: 14px;
  color: var(--apple-text);
  line-height: 1.6;
  margin: 8px 0 0 0;
}

/* Invitation Card */
.invitation-card {
  padding: 20px;
  background: rgba(0, 122, 255, 0.05);
  border: 1px solid rgba(0, 122, 255, 0.1);
  border-radius: var(--apple-radius-lg);
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--apple-primary);
  margin: 0 0 16px 0;
}

.load-btn {
  width: 100%;
  padding: 12px;
  background: var(--apple-primary);
  color: white;
  border: none;
  border-radius: var(--apple-radius);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.load-btn:hover {
  background: #0066d6;
}

.invitation-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.info-row span:first-child {
  color: var(--apple-text-secondary);
}

.info-row span:last-child {
  color: var(--apple-text);
  font-weight: 500;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}

.status-badge.pending {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.status-badge.accepted {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.status-badge.rejected {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.type-badge {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}

.type-badge.member {
  background: rgba(0, 122, 255, 0.12);
  color: var(--apple-primary);
}

.type-badge.teacher {
  background: rgba(88, 86, 214, 0.12);
  color: #5856D6;
}

.invitation-actions {
  display: flex;
  gap: 12px;
}

.accept-btn,
.reject-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border: none;
  border-radius: var(--apple-radius);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.accept-btn svg,
.reject-btn svg {
  width: 18px;
  height: 18px;
}

.accept-btn {
  background: var(--apple-success);
  color: white;
}

.accept-btn:hover {
  background: #34c759;
}

.reject-btn {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.reject-btn:hover {
  background: rgba(255, 59, 48, 0.2);
}

@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
    gap: 12px;
  }

  .filter-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .drawer-content {
    width: 100%;
  }

  .message-item {
    flex-wrap: wrap;
  }

  .message-actions {
    width: 100%;
    justify-content: flex-end;
    margin-top: 8px;
  }
}
</style>
