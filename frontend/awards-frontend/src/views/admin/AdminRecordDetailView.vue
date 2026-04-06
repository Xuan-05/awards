<script setup lang="ts">
/**
 * 校管理员端 - 获奖记录只读详情（/admin/record/detail/:id）
 */
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { http } from "../../api/http";
import type { ApiResponse, PageResult } from "../../types/api";
import type { Team, TeamMember, TeamTeacher } from "../../types/team";
import {
  labelAuditActionType,
  labelAuditNodeType,
  labelInviteStatus,
  labelRecordStatus,
  labelTeamStatus,
} from "../../utils/displayLabels";

type AwardRecord = {
  id: number;
  teamId: number;
  competitionId: number;
  awardScopeId: number;
  awardLevelId: number;
  awardDate: string;
  semester: string;
  projectName: string;
  teamAwardCount?: number;
  ownerDeptId?: number;
  status: string;
  submitTime?: string;
  finalAuditTime?: string;
  remark?: string;
};

type Competition = { id: number; competitionName: string; enabled: number };
type AwardScope = { id: number; scopeName: string; enabled: number };
type AwardLevel = {
  id: number;
  awardScopeId: number;
  levelName: string;
  enabled: number;
};
type Dept = { id: number; deptCode: string; deptName: string };
type AuditLog = {
  id: number;
  nodeType: string;
  actionType: string;
  fromStatus: string;
  toStatus: string;
  commentText?: string;
  createdAt?: string;
};
type RecordFileRel = {
  recordId: number;
  fileId: number;
  createdAt?: string;
  fileName?: string;
};

const route = useRoute();
const router = useRouter();

const recordId = computed(() => {
  const n = Number(route.params.id);
  return Number.isFinite(n) ? n : null;
});

const loading = ref(true);
const record = ref<AwardRecord | null>(null);
const depts = ref<Dept[]>([]);
const competitions = ref<Competition[]>([]);
const scopes = ref<AwardScope[]>([]);
const levels = ref<AwardLevel[]>([]);
const teamLoading = ref(false);
const team = ref<Team | null>(null);
const members = ref<TeamMember[]>([]);
const teachers = ref<TeamTeacher[]>([]);
const filesLoading = ref(false);
const files = ref<RecordFileRel[]>([]);
const logsLoading = ref(false);
const logs = ref<AuditLog[]>([]);

const deptNameById = computed(() => {
  const m: Record<number, string> = {};
  for (const d of depts.value) m[d.id] = d.deptName;
  return m;
});

function competitionName(id: number) {
  return competitions.value.find((x) => x.id === id)?.competitionName ?? `竞赛 #${id}`;
}
function scopeName(id: number) {
  return scopes.value.find((x) => x.id === id)?.scopeName ?? `范围 #${id}`;
}
function levelName(id: number) {
  return levels.value.find((x) => x.id === id)?.levelName ?? `等级 #${id}`;
}
function attachmentLabel(f: RecordFileRel) {
  return f.fileName?.trim() ? f.fileName : `文件 #${f.fileId}`;
}
function preview(fileId: number) {
  const token = localStorage.getItem("token");
  if (!token) {
    ElMessage.warning("请先登录");
    return;
  }
  window.open(`/api/files/${fileId}/preview?Authorization=${encodeURIComponent(token)}`);
}
function download(fileId: number) {
  const token = localStorage.getItem("token");
  if (!token) {
    ElMessage.warning("请先登录");
    return;
  }
  window.open(`/api/files/${fileId}/download?Authorization=${encodeURIComponent(token)}`);
}
function backToList() {
  router.push("/admin/audit/tasks");
}

async function loadDicts() {
  const [d, c, s, l] = await Promise.all([
    http.get<ApiResponse<Dept[]>>("/depts"),
    http.get<ApiResponse<PageResult<Competition>>>("/dicts/competitions", {
      params: { pageNo: 1, pageSize: 200, enabled: 1 },
    }),
    http.get<ApiResponse<PageResult<AwardScope>>>("/dicts/award-scopes", {
      params: { pageNo: 1, pageSize: 200, enabled: 1 },
    }),
    http.get<ApiResponse<PageResult<AwardLevel>>>("/dicts/award-levels", {
      params: { pageNo: 1, pageSize: 500, enabled: 1 },
    }),
  ]);
  if (d.data.code !== 0) throw new Error(d.data.message);
  if (c.data.code !== 0) throw new Error(c.data.message);
  if (s.data.code !== 0) throw new Error(s.data.message);
  if (l.data.code !== 0) throw new Error(l.data.message);
  depts.value = d.data.data;
  competitions.value = c.data.data.list;
  scopes.value = s.data.data.list;
  levels.value = l.data.data.list;
}

async function loadRecord() {
  const id = recordId.value!;
  const resp = await http.get<ApiResponse<AwardRecord>>(`/award-records/${id}`);
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  record.value = resp.data.data;
}

async function loadTeamBlock(teamId: number) {
  teamLoading.value = true;
  try {
    const [t, m, th] = await Promise.all([
      http.get<ApiResponse<Team>>(`/teams/${teamId}`),
      http.get<ApiResponse<TeamMember[]>>(`/teams/${teamId}/members`),
      http.get<ApiResponse<TeamTeacher[]>>(`/teams/${teamId}/teachers`),
    ]);
    team.value = t.data.code === 0 ? t.data.data : null;
    members.value = m.data.code === 0 ? m.data.data : [];
    teachers.value = th.data.code === 0 ? th.data.data : [];
  } catch {
    team.value = null;
    members.value = [];
    teachers.value = [];
  } finally {
    teamLoading.value = false;
  }
}

async function loadFiles(id: number) {
  filesLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<RecordFileRel[]>>(`/award-records/${id}/files`);
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    files.value = resp.data.data;
  } finally {
    filesLoading.value = false;
  }
}

async function loadLogs(id: number) {
  logsLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<AuditLog[]>>(`/audit/${id}/logs`);
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    logs.value = resp.data.data;
  } finally {
    logsLoading.value = false;
  }
}



const captainMember = computed(() => members.value.find((x) => x.isCaptain === 1));

onMounted(async () => {
  if (recordId.value == null) {
    ElMessage.error("记录不存在");
    backToList();
    return;
  }
  loading.value = true;
  try {
    await loadDicts();
    await loadRecord();
    const r = record.value!;
    await Promise.all([loadFiles(r.id), loadLogs(r.id), loadTeamBlock(r.teamId)]);
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : "加载失败");
    backToList();
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="admin-record-detail" v-loading="loading">
    <div class="page-toolbar">
      <el-button @click="backToList">返回审核列表</el-button>
    </div>

    <template v-if="record">
      <div class="detail-header">
        <h1 class="page-title">{{ record.projectName || "未命名项目" }}</h1>
        <span
          :class="[
            'status-pill',
            record.status === 'APPROVED' ? 'ok' : record.status === 'SCHOOL_REJECTED' ? 'rej' : record.status === 'PENDING_SCHOOL' ? 'pending' : '',
          ]"
        >
          {{ labelRecordStatus(record.status) }}
        </span>
      </div>

      <section class="block">
        <h2 class="block-title">填报信息</h2>
        <div class="info-grid">
          <div class="info-cell"><span class="label">记录 ID</span><span class="value">{{ record.id }}</span></div>
          <div class="info-cell"><span class="label">竞赛名称</span><span class="value">{{ competitionName(record.competitionId) }}</span></div>
          <div class="info-cell"><span class="label">获奖范围</span><span class="value">{{ scopeName(record.awardScopeId) }}</span></div>
          <div class="info-cell"><span class="label">获奖等级</span><span class="value">{{ levelName(record.awardLevelId) }}</span></div>
          <div class="info-cell"><span class="label">获奖日期</span><span class="value">{{ record.awardDate ? String(record.awardDate).slice(0, 10) : "-" }}</span></div>
          <div class="info-cell"><span class="label">学期</span><span class="value">{{ record.semester || "-" }}</span></div>
          <div class="info-cell">
            <span class="label">归属院系</span>
            <span class="value">{{ record.ownerDeptId != null ? (deptNameById[record.ownerDeptId] ?? `#${record.ownerDeptId}`) : "-" }}</span>
          </div>
          <div class="info-cell"><span class="label">团队获奖人数</span><span class="value">{{ record.teamAwardCount ?? "-" }}</span></div>
          <div class="info-cell full"><span class="label">备注</span><span class="value">{{ record.remark || "-" }}</span></div>
          <div class="info-cell"><span class="label">提交时间</span><span class="value">{{ record.submitTime || "-" }}</span></div>
          <div class="info-cell"><span class="label">终审时间</span><span class="value">{{ record.finalAuditTime || "-" }}</span></div>
        </div>
      </section>

      <section class="block" v-loading="teamLoading">
        <h2 class="block-title">团队信息</h2>
        <template v-if="team">
          <div class="info-grid">
            <div class="info-cell"><span class="label">团队名称</span><span class="value">{{ team.teamName }}</span></div>
            <div class="info-cell"><span class="label">团队 ID</span><span class="value">{{ team.id }}</span></div>
            <div class="info-cell">
              <span class="label">队长</span>
              <span class="value">{{ captainMember?.user?.realName ?? (team.captainUserId != null ? `用户 #${team.captainUserId}` : "-") }}</span>
            </div>
            <div class="info-cell">
              <span class="label">归属院系</span>
              <span class="value">{{ deptNameById[team.ownerDeptId] ?? `#${team.ownerDeptId}` }}</span>
            </div>
            <div class="info-cell"><span class="label">状态</span><span class="value">{{ labelTeamStatus(team.status) }}</span></div>
          </div>
          <h3 class="sub-title">成员列表</h3>
          <el-table :data="members" size="small" border empty-text="暂无成员">
            <el-table-column label="姓名" min-width="120">
              <template #default="{ row }">{{ row.user?.realName ?? `用户 #${row.userId}` }}</template>
            </el-table-column>
            <el-table-column label="学号/工号" min-width="120">
              <template #default="{ row }">{{ row.user?.username ?? "-" }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ labelInviteStatus(row.joinStatus) }}</template>
            </el-table-column>
            <el-table-column label="队长" width="80">
              <template #default="{ row }">{{ row.isCaptain === 1 ? "是" : "否" }}</template>
            </el-table-column>
          </el-table>
          <h3 class="sub-title">指导教师</h3>
          <el-table :data="teachers" size="small" border empty-text="暂无指导教师">
            <el-table-column label="姓名" min-width="120">
              <template #default="{ row }">{{ row.user?.realName ?? `用户 #${row.teacherUserId}` }}</template>
            </el-table-column>
            <el-table-column label="工号" min-width="120">
              <template #default="{ row }">{{ row.user?.username ?? "-" }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ labelInviteStatus(row.joinStatus) }}</template>
            </el-table-column>
          </el-table>
        </template>
        <p v-else class="muted">无法加载团队信息（无权限或团队不存在）</p>
      </section>

      <section class="block">
        <h2 class="block-title">附件材料</h2>
        <div class="file-list" v-loading="filesLoading">
          <div v-for="f in files" :key="f.fileId" class="file-row">
            <button type="button" class="file-link" @click="preview(f.fileId)">{{ attachmentLabel(f) }}</button>
            <el-button size="small" text type="primary" @click="download(f.fileId)">下载</el-button>
          </div>
          <p v-if="files.length === 0 && !filesLoading" class="muted">暂无附件</p>
        </div>
      </section>

      <section class="block">
        <h2 class="block-title">审核历史</h2>
        <div class="timeline" v-loading="logsLoading">
          <div v-for="l in logs" :key="l.id" class="timeline-item">
            <div class="timeline-dot" />
            <div class="timeline-card">
              <div class="timeline-head">
                <span>{{ labelAuditNodeType(l.nodeType) }} / {{ labelAuditActionType(l.actionType) }}</span>
                <span class="time">{{ l.createdAt }}</span>
              </div>
              <div class="timeline-body">{{ labelRecordStatus(l.fromStatus) }} → {{ labelRecordStatus(l.toStatus) }}</div>
              <div v-if="l.commentText" class="timeline-comment">意见：{{ l.commentText }}</div>
            </div>
          </div>
          <p v-if="logs.length === 0 && !logsLoading" class="muted">暂无审核记录</p>
        </div>
      </section>
    </template>

  </div>
</template>

<style scoped>
.admin-record-detail { max-width: 960px; }
.page-toolbar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 20px; }
.detail-header {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px;
  margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid var(--apple-border);
}
.page-title { font-size: 22px; font-weight: 700; color: var(--apple-text); margin: 0; letter-spacing: -0.3px; }
.status-pill { flex-shrink: 0; font-size: 12px; font-weight: 600; padding: 4px 12px; border-radius: 6px; }
.status-pill.pending { background: rgba(255, 149, 0, 0.12); color: var(--apple-warning); }
.status-pill.ok { background: rgba(52, 199, 89, 0.12); color: var(--apple-success); }
.status-pill.rej { background: rgba(255, 59, 48, 0.12); color: var(--apple-danger); }
.block { margin-bottom: 28px; }
.block-title { font-size: 15px; font-weight: 600; color: var(--apple-text); margin: 0 0 12px 0; }
.sub-title { font-size: 13px; font-weight: 600; color: var(--apple-text-secondary); margin: 16px 0 8px 0; }
.info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 24px; }
.info-cell { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.info-cell.full { grid-column: 1 / -1; }
.info-cell .label { font-size: 12px; color: var(--apple-text-secondary); }
.info-cell .value { font-size: 14px; font-weight: 500; color: var(--apple-text); word-break: break-word; }
.file-list { background: var(--apple-bg-secondary); border-radius: var(--apple-radius); padding: 12px; }
.file-row { display: flex; align-items: center; justify-content: space-between; padding: 8px 10px; border-radius: 6px; }
.file-row:hover { background: var(--apple-bg-tertiary); }
.file-link {
  background: none; border: none; padding: 0; font-size: 13px; font-weight: 500; color: var(--apple-primary);
  cursor: pointer; text-decoration: underline; text-underline-offset: 2px; text-align: left;
}
.muted { font-size: 13px; color: var(--apple-text-tertiary); margin: 0; }
.timeline { position: relative; padding-left: 20px; }
.timeline-item { position: relative; padding-bottom: 16px; }
.timeline-item:last-child { padding-bottom: 0; }
.timeline-dot {
  position: absolute; left: -20px; top: 6px; width: 8px; height: 8px; border-radius: 50%; background: var(--apple-primary);
}
.timeline-item::before {
  content: ""; position: absolute; left: -16px; top: 14px; bottom: 0; width: 2px; background: var(--apple-border);
}
.timeline-item:last-child::before { display: none; }
.timeline-card { background: var(--apple-bg-secondary); border-radius: var(--apple-radius); padding: 10px 14px; }
.timeline-head {
  display: flex; justify-content: space-between; gap: 12px; font-size: 13px; font-weight: 600; color: var(--apple-text);
}
.timeline-head .time { font-weight: 400; font-size: 12px; color: var(--apple-text-tertiary); }
.timeline-body { font-size: 12px; color: var(--apple-text-secondary); margin-top: 4px; }
.timeline-comment {
  font-size: 12px; color: var(--apple-text-secondary); margin-top: 8px;
  padding: 8px 10px; background: var(--apple-bg-tertiary); border-radius: 6px;
}
</style>
