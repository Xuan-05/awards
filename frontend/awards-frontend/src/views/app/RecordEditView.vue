<script setup lang="ts">
/**
 * 学生/教师端 - 填报详情/编辑（/app/records/new 或 /app/records/:id）
 *
 * 页面能力：
 * - 新建：第 1 步保存草稿生成记录 ID，再允许上传附件
 * - 编辑：加载记录详情、附件列表
 * - 附件：上传/删除关联、预览/下载（zip 仅下载，预览可能返回“download_only”）
 *
 * 重要约束（后端生效）：
 * - record_attachment_required=true 时，提交前必须至少上传一个附件（在 submit 时校验）
 * - upload_max_single_file_mb 限制单文件大小（在上传时校验）
 */
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { http } from "../../api/http";
import type { ApiResponse, PageResult } from "../../types/api";

type Team = { id: number; teamName: string };
type DictCompetition = {
  id: number;
  competitionName: string;
  categoryId: number;
  organizerId?: number;
  enabled: number;
};
type DictAwardScope = { id: number; scopeName: string; enabled: number };
type DictAwardLevel = {
  id: number;
  awardScopeId: number;
  levelName: string;
  enabled: number;
};

type AwardRecord = {
  id: number;
  status: string;
  teamId: number;
  competitionId: number;
  awardScopeId: number;
  awardLevelId: number;
  awardDate: string;
  semester: string;
  projectName: string;
  remark?: string;
};

type RecordFileRel = { recordId: number; fileId: number; createdAt?: string };

type UpsertPayload = {
  teamId: number;
  competitionId: number;
  awardScopeId: number;
  awardLevelId: number;
  awardDate: string;
  semester: string;
  projectName: string;
  teamAwardCount: number;
  remark?: string;
};

const route = useRoute();
const router = useRouter();

const recordId = computed(() => {
  const p = route.params.id;
  if (!p) return null;
  const n = Number(p);
  return Number.isFinite(n) ? n : null;
});

const isNew = computed(
  () => route.path.endsWith("/new") || recordId.value == null,
);

/** 进入页面时的模式：用于 replace 路由后仍区分「创建填报」与「保存修改」 */
const entryMode = ref<"create" | "edit">("edit");

const saving = ref(false);
const stepSaving = ref(false);
const loading = ref(false);

const form = reactive({
  teamId: undefined as number | undefined,
  competitionId: undefined as number | undefined,
  awardScopeId: undefined as number | undefined,
  awardLevelId: undefined as number | undefined,
  awardDate: "" as string,
  semester: "" as string,
  projectName: "" as string,
  remark: "" as string,
});

const teams = ref<Team[]>([]);
const competitions = ref<DictCompetition[]>([]);
const scopes = ref<DictAwardScope[]>([]);
const levelsAll = ref<DictAwardLevel[]>([]);

const levels = computed(() => {
  if (!form.awardScopeId) return [];
  return levelsAll.value.filter(
    (x) => x.awardScopeId == form.awardScopeId && x.enabled === 1,
  );
});

/** 连续 3 个学年，每学期 -1 / -2 */
function buildSemesterOptions(): string[] {
  const y = new Date().getFullYear();
  const out: string[] = [];
  for (let i = 0; i < 3; i++) {
    const start = y - 2 + i;
    const end = start + 1;
    out.push(`${start}-${end}-1`);
    out.push(`${start}-${end}-2`);
  }
  return out;
}

const semesterOptions = ref<string[]>(buildSemesterOptions());

function todayYmd(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/**
 * 占位用范围+等级：优先匹配表单已有范围；否则依次尝试「启用的范围」，
 * 再尝试其余范围，直到找到第一个「该范围下存在启用等级」的组合，避免因字典排序导致首项无等级而失败。
 */
function pickPlaceholderScopeLevel(): { scopeId: number; levelId: number } {
  const levelFor = (scopeId: number) =>
    levelsAll.value.find(
      (l) => l.awardScopeId == scopeId && l.enabled === 1,
    );

  if (form.awardScopeId != null) {
    const lv = levelFor(form.awardScopeId);
    if (lv) return { scopeId: form.awardScopeId, levelId: lv.id };
  }

  const seen = new Set<number>();
  const tryScopes = (list: DictAwardScope[]) => {
    for (const s of list) {
      if (seen.has(s.id)) continue;
      seen.add(s.id);
      const lv = levelFor(s.id);
      if (lv) return { scopeId: s.id, levelId: lv.id };
    }
    return null;
  };

  const hit =
    tryScopes(scopes.value.filter((s) => s.enabled === 1)) ??
    tryScopes(scopes.value);

  if (hit) return hit;

  if (!scopes.value.length) throw new Error("无可用获奖范围字典");
  throw new Error(
    "未找到可用的获奖等级：请确认至少一个获奖范围下配置了启用的等级字典",
  );
}

/** 第 1 步草稿：补齐后端非空字段（占位用字典首项、今日日期、学期首项） */
function buildCompletePayload(): UpsertPayload {
  if (!form.teamId || !form.competitionId) {
    throw new Error("请选择团队与竞赛");
  }
  const name = form.projectName.trim();
  if (!name) throw new Error("请填写项目名称");
  const { scopeId, levelId } = pickPlaceholderScopeLevel();
  const firstSem = semesterOptions.value[0];
  if (!firstSem) throw new Error("学期选项未初始化");
  const payload: UpsertPayload = {
    teamId: form.teamId,
    competitionId: form.competitionId,
    awardScopeId: form.awardScopeId ?? scopeId,
    awardLevelId: form.awardLevelId ?? levelId,
    awardDate: (form.awardDate && form.awardDate.trim()) || todayYmd(),
    semester: (form.semester && form.semester.trim()) || firstSem,
    projectName: name,
    teamAwardCount: 1,
  };
  if (form.remark?.trim()) payload.remark = form.remark.trim();
  return payload;
}

function buildStrictPayload(): UpsertPayload {
  if (
    !form.teamId ||
    !form.competitionId ||
    !form.awardScopeId ||
    !form.awardLevelId ||
    !form.awardDate?.trim() ||
    !form.semester?.trim() ||
    !form.projectName.trim()
  ) {
    throw new Error("请把必填项补齐");
  }
  const payload: UpsertPayload = {
    teamId: form.teamId,
    competitionId: form.competitionId,
    awardScopeId: form.awardScopeId,
    awardLevelId: form.awardLevelId,
    awardDate: form.awardDate.trim(),
    semester: form.semester.trim(),
    projectName: form.projectName.trim(),
    teamAwardCount: 1,
  };
  if (form.remark?.trim()) payload.remark = form.remark.trim();
  return payload;
}

async function loadDicts() {
  const [t, c, s, l] = await Promise.all([
    http.get<ApiResponse<Team[]>>("/teams/my"),
    http.get<ApiResponse<PageResult<DictCompetition>>>("/dicts/competitions", {
      params: { pageNo: 1, pageSize: 200, enabled: 1 },
    }),
    http.get<ApiResponse<PageResult<DictAwardScope>>>("/dicts/award-scopes", {
      params: { pageNo: 1, pageSize: 200, enabled: 1 },
    }),
    http.get<ApiResponse<PageResult<DictAwardLevel>>>("/dicts/award-levels", {
      params: { pageNo: 1, pageSize: 500, enabled: 1 },
    }),
  ]);
  if (t.data.code !== 0) throw new Error(t.data.message);
  if (c.data.code !== 0) throw new Error(c.data.message);
  if (s.data.code !== 0) throw new Error(s.data.message);
  if (l.data.code !== 0) throw new Error(l.data.message);
  teams.value = t.data.data;
  competitions.value = c.data.data.list;
  scopes.value = s.data.data.list;
  levelsAll.value = l.data.data.list;
}

async function loadRecord() {
  if (!recordId.value) return;
  const resp = await http.get<ApiResponse<AwardRecord>>(
    `/award-records/${recordId.value}`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  const r = resp.data.data;
  form.teamId = r.teamId;
  form.competitionId = r.competitionId;
  form.awardScopeId = r.awardScopeId;
  form.awardLevelId = r.awardLevelId;
  form.awardDate = String(r.awardDate || "");
  form.semester = r.semester || "";
  form.projectName = r.projectName || "";
  form.remark = r.remark || "";
  if (form.semester && !semesterOptions.value.includes(form.semester)) {
    semesterOptions.value = [form.semester, ...semesterOptions.value];
  }
}

const filesLoading = ref(false);
const rels = ref<RecordFileRel[]>([]);

async function loadFiles() {
  if (!recordId.value) return;
  filesLoading.value = true;
  try {
    const resp = await http.get<ApiResponse<RecordFileRel[]>>(
      `/award-records/${recordId.value}/files`,
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    rels.value = resp.data.data;
  } finally {
    filesLoading.value = false;
  }
}

watch(
  recordId,
  (id) => {
    if (id != null) void loadFiles();
  },
  { immediate: true },
);

async function uploadFile(raw: File) {
  if (!recordId.value) {
    ElMessage.warning(
      "记录尚未保存：请返回第 1 步填写基本信息并点击「下一步」生成记录后再上传。",
    );
    return;
  }
  try {
    const fd = new FormData();
    fd.append("file", raw);
    const resp = await http.post<ApiResponse<RecordFileRel>>(
      `/award-records/${recordId.value}/files`,
      fd,
      {
        headers: { "Content-Type": "multipart/form-data" },
      },
    );
    if (resp.data.code !== 0) {
      ElMessage.error(resp.data.message || "上传失败");
      return;
    }
    ElMessage.success("上传成功");
    await loadFiles();
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string };
    ElMessage.error(err.response?.data?.message || err.message || "上传失败");
  }
}

async function removeRel(fileId: number) {
  if (!recordId.value) return;
  const resp = await http.delete<ApiResponse<null>>(
    `/award-records/${recordId.value}/files/${fileId}`,
  );
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  await loadFiles();
}

function preview(fileId: number) {
  const token = localStorage.getItem("token");
  if (!token) {
    ElMessage.warning("请先登录");
    return;
  }
  window.open(
    `/api/files/${fileId}/preview?Authorization=${encodeURIComponent(token)}`,
  );
}

function download(fileId: number) {
  const token = localStorage.getItem("token");
  if (!token) {
    ElMessage.warning("请先登录");
    return;
  }
  window.open(
    `/api/files/${fileId}/download?Authorization=${encodeURIComponent(token)}`,
  );
}

const currentStep = ref(1);

const canGoNext = computed(() => {
  if (currentStep.value === 1) {
    return Boolean(
      form.teamId &&
        form.competitionId &&
        form.projectName.trim().length > 0,
    );
  }
  if (currentStep.value === 2) {
    return Boolean(
      form.awardScopeId &&
        form.awardLevelId &&
        form.awardDate?.trim() &&
        form.semester?.trim(),
    );
  }
  return false;
});

async function persistStep1Draft() {
  const payload = buildCompletePayload();
  if (!recordId.value) {
    const resp = await http.post<ApiResponse<number>>(
      "/award-records",
      payload,
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    const id = resp.data.data;
    await router.replace(`/app/records/${id}`);
    await nextTick();
    await loadRecord();
    ElMessage.success("草稿已保存");
  } else {
    const resp = await http.put<ApiResponse<null>>(
      `/award-records/${recordId.value}`,
      payload,
    );
    if (resp.data.code !== 0) throw new Error(resp.data.message);
    await loadRecord();
    ElMessage.success("已保存");
  }
}

async function persistStep2Strict() {
  const id = recordId.value;
  if (id == null) throw new Error("记录不存在");
  const payload = buildStrictPayload();
  const resp = await http.put<ApiResponse<null>>(`/award-records/${id}`, payload);
  if (resp.data.code !== 0) throw new Error(resp.data.message);
  ElMessage.success("已保存");
  await loadFiles();
}

async function goNext() {
  if (!canGoNext.value || stepSaving.value) return;
  stepSaving.value = true;
  try {
    if (currentStep.value === 1) {
      try {
        await persistStep1Draft();
      } catch (e) {
        const msg = e instanceof Error ? e.message : "保存失败";
        ElMessage.error(msg);
        return;
      }
      currentStep.value = 2;
    } else if (currentStep.value === 2) {
      try {
        await persistStep2Strict();
      } catch (e) {
        const msg = e instanceof Error ? e.message : "保存失败";
        ElMessage.error(msg);
        return;
      }
      currentStep.value = 3;
    }
  } finally {
    stepSaving.value = false;
  }
}

async function handleFinalAction() {
  if (!recordId.value) {
    ElMessage.error("记录不存在，请返回第 1 步保存");
    return;
  }
  saving.value = true;
  try {
    const payload = buildStrictPayload();
    const putResp = await http.put<ApiResponse<null>>(
      `/award-records/${recordId.value}`,
      payload,
    );
    if (putResp.data.code !== 0) throw new Error(putResp.data.message);
    if (entryMode.value === "create") {
      const sub = await http.post<ApiResponse<null>>(
        `/award-records/${recordId.value}/submit`,
      );
      if (sub.data.code !== 0) throw new Error(sub.data.message);
      ElMessage.success("已提交审核");
      await router.push("/app/records");
    } else {
      ElMessage.success("已保存");
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : "操作失败";
    ElMessage.error(msg);
  } finally {
    saving.value = false;
  }
}

function onAwardScopeChange() {
  form.awardLevelId = undefined;
}

onMounted(async () => {
  entryMode.value =
    route.path.endsWith("/new") || recordId.value == null ? "create" : "edit";
  semesterOptions.value = buildSemesterOptions();
  loading.value = true;
  try {
    await loadDicts();
    await loadRecord();
  } finally {
    loading.value = false;
  }
});

function getTeamName(teamId: number): string {
  const team = teams.value.find((t) => t.id === teamId);
  return team?.teamName || `#${teamId}`;
}

function getCompetitionName(competitionId: number): string {
  const comp = competitions.value.find((c) => c.id === competitionId);
  return comp?.competitionName || `#${competitionId}`;
}

function handleFileSelect(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (file) {
    const maxSize = 50 * 1024 * 1024;
    if (file.size > maxSize) {
      ElMessage.error(
        `文件过大，最大支持50MB。当前文件大小：${(file.size / 1024 / 1024).toFixed(2)}MB`,
      );
      target.value = "";
      return;
    }
    uploadFile(file);
  }
  target.value = "";
}

const fileInput = ref<HTMLInputElement | null>(null);

function triggerUpload() {
  if (!recordId.value) return;
  fileInput.value?.click();
}
</script>

<template>
  <div class="record-edit-page" v-loading="loading">
    <!-- 页面头部 -->
    <div class="page-header">
      <button class="back-btn" @click="router.push('/app/records')">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <polyline points="15,18 9,12 15,6" />
        </svg>
      </button>
      <div class="header-content">
        <h1 class="page-title">{{ isNew ? "新建填报" : "编辑填报" }}</h1>
        <p class="page-subtitle" v-if="recordId">编号：{{ recordId }}</p>
      </div>
    </div>

    <!-- 步骤指示器 -->
    <div class="steps-indicator">
      <div
        class="step"
        :class="{ active: currentStep >= 1, completed: currentStep > 1 }"
      >
        <div class="step-number">1</div>
        <span class="step-label">基本信息</span>
      </div>
      <div class="step-line" :class="{ active: currentStep > 1 }"></div>
      <div
        class="step"
        :class="{ active: currentStep >= 2, completed: currentStep > 2 }"
      >
        <div class="step-number">2</div>
        <span class="step-label">获奖信息</span>
      </div>
      <div class="step-line" :class="{ active: currentStep > 2 }"></div>
      <div class="step" :class="{ active: currentStep >= 3 }">
        <div class="step-number">3</div>
        <span class="step-label">附件材料</span>
      </div>
    </div>

    <!-- 表单内容 -->
    <div class="form-container">
      <!-- 步骤1：基本信息 -->
      <div class="form-section" v-show="currentStep === 1">
        <h2 class="section-title">基本信息</h2>
        <p class="section-desc">选择团队和参赛竞赛</p>

        <div class="form-group">
          <label class="form-label required">参赛团队</label>
          <select v-model="form.teamId" class="form-select">
            <option :value="undefined" disabled>请选择团队</option>
            <option v-for="t in teams" :key="t.id" :value="t.id">
              {{ t.teamName }}
            </option>
          </select>
          <p class="form-hint">选择代表参赛的团队</p>
        </div>

        <div class="form-group">
          <label class="form-label required">参赛竞赛</label>
          <select v-model="form.competitionId" class="form-select">
            <option :value="undefined" disabled>请选择竞赛</option>
            <option v-for="c in competitions" :key="c.id" :value="c.id">
              {{ c.competitionName }}
            </option>
          </select>
          <p class="form-hint">从竞赛字典中选择</p>
        </div>

        <div class="form-group">
          <label class="form-label required">项目名称</label>
          <input
            type="text"
            v-model="form.projectName"
            placeholder="请输入参赛项目/选题名称"
            class="form-input"
          />
        </div>

        <div class="form-group">
          <label class="form-label">备注说明</label>
          <textarea
            v-model="form.remark"
            placeholder="可选，填写补充说明"
            class="form-textarea"
            rows="3"
          ></textarea>
        </div>
      </div>

      <!-- 步骤2：获奖信息 -->
      <div class="form-section" v-show="currentStep === 2">
        <h2 class="section-title">获奖信息</h2>
        <p class="section-desc">填写获奖详情</p>

        <div class="form-row">
          <div class="form-group half">
            <label class="form-label required">获奖范围</label>
            <select
              v-model="form.awardScopeId"
              class="form-select"
              @change="onAwardScopeChange"
            >
              <option :value="undefined" disabled>请选择范围</option>
              <option v-for="s in scopes" :key="s.id" :value="s.id">
                {{ s.scopeName }}
              </option>
            </select>
          </div>

          <div class="form-group half">
            <label class="form-label required">获奖等级</label>
            <select
              v-model="form.awardLevelId"
              class="form-select"
              :disabled="!form.awardScopeId"
            >
              <option :value="undefined" disabled>
                {{ form.awardScopeId ? "请选择等级" : "请先选择获奖范围" }}
              </option>
              <option v-for="l in levels" :key="l.id" :value="l.id">
                {{ l.levelName }}
              </option>
            </select>
            <p class="form-hint warning" v-if="!form.awardScopeId">
              需先选择获奖范围
            </p>
            <p class="form-hint error" v-else-if="levels.length === 0">
              该范围暂无可用等级
            </p>
          </div>
        </div>

        <div class="form-row">
          <div class="form-group half ep-field">
            <label class="form-label required">获奖日期</label>
            <el-date-picker
              v-model="form.awardDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              :editable="false"
              class="record-edit-date"
            />
          </div>

          <div class="form-group half ep-field">
            <label class="form-label required">学期</label>
            <el-select
              v-model="form.semester"
              placeholder="请选择学期"
              :filterable="false"
              :allow-create="false"
              class="record-edit-semester"
            >
              <el-option
                v-for="opt in semesterOptions"
                :key="opt"
                :label="opt"
                :value="opt"
              />
            </el-select>
          </div>
        </div>

        <!-- 信息预览卡片 -->
        <div class="preview-card" v-if="form.teamId && form.competitionId">
          <h4 class="preview-title">填报预览</h4>
          <div class="preview-content">
            <div class="preview-item">
              <span class="preview-label">团队</span>
              <span class="preview-value">{{ getTeamName(form.teamId) }}</span>
            </div>
            <div class="preview-item">
              <span class="preview-label">竞赛</span>
              <span class="preview-value">{{
                getCompetitionName(form.competitionId)
              }}</span>
            </div>
            <div class="preview-item">
              <span class="preview-label">项目</span>
              <span class="preview-value">{{ form.projectName || "-" }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 步骤3：附件材料 -->
      <div class="form-section" v-show="currentStep === 3">
        <h2 class="section-title">附件材料</h2>
        <p class="section-desc">上传获奖证明材料</p>

        <!-- 上传区域（无 recordId 时禁用，需先完成第 1 步） -->
        <div class="upload-area">
          <input
            type="file"
            ref="fileInput"
            @change="handleFileSelect"
            style="display: none"
          />
          <div
            class="upload-box"
            :class="{ 'upload-box--disabled': !recordId }"
            @click="triggerUpload"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="17,8 12,3 7,8" />
              <line x1="12" y1="3" x2="12" y2="15" />
            </svg>
            <p class="upload-text">点击上传文件</p>
            <p class="upload-hint">
              支持图片、PDF、ZIP；单文件最大 50MB。未完成第 1 步保存前无法上传。
            </p>
          </div>
          <p v-if="!recordId" class="upload-disabled-hint">
            请返回第 1 步填写团队、竞赛与项目名称，点击「下一步」生成记录后再上传附件。
          </p>
        </div>

        <!-- 附件列表 -->
        <div class="files-list" v-loading="filesLoading">
          <div v-for="rel in rels" :key="rel.fileId" class="file-item">
            <div class="file-icon">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"
                />
                <polyline points="14,2 14,8 20,8" />
              </svg>
            </div>
            <div class="file-info">
              <span class="file-id">文件 #{{ rel.fileId }}</span>
              <span class="file-time">{{ rel.createdAt || "-" }}</span>
            </div>
            <div class="file-actions">
              <button class="file-btn preview" @click="preview(rel.fileId)">
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              </button>
              <button class="file-btn download" @click="download(rel.fileId)">
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="7,10 12,15 17,10" />
                  <line x1="12" y1="15" x2="12" y2="3" />
                </svg>
              </button>
              <button class="file-btn delete" @click="removeRel(rel.fileId)">
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
          <div
            v-if="rels.length === 0 && !filesLoading && recordId"
            class="empty-files"
          >
            暂无附件
          </div>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="form-actions">
        <button
          v-if="currentStep > 1"
          class="action-btn secondary"
          :disabled="stepSaving || saving"
          @click="currentStep--"
        >
          上一步
        </button>
        <button
          v-if="currentStep < 3"
          class="action-btn primary"
          type="button"
          :disabled="!canGoNext || stepSaving"
          @click="goNext"
        >
          {{ stepSaving ? "保存中…" : "下一步" }}
        </button>
        <button
          v-if="currentStep === 3"
          class="action-btn primary"
          type="button"
          :disabled="!recordId || saving"
          @click="handleFinalAction"
        >
          {{ entryMode === "create" ? "创建填报" : "保存修改" }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.record-edit-page {
  padding: 8px;
}

/* Page Header */
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.back-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid var(--apple-border);
  background: var(--apple-glass);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.back-btn svg {
  width: 20px;
  height: 20px;
  color: var(--apple-text-secondary);
}

.back-btn:hover {
  background: var(--apple-bg-secondary);
  border-color: var(--apple-primary);
}

.back-btn:hover svg {
  color: var(--apple-primary);
}

.header-content {
  flex: 1;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--apple-text);
  margin: 0 0 4px 0;
  letter-spacing: -0.5px;
}

.page-subtitle {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin: 0;
}

/* Steps Indicator */
.steps-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  margin-bottom: 32px;
  padding: 20px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.step-number {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--apple-bg-secondary);
  border: 2px solid var(--apple-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--apple-text-secondary);
  transition: all 0.2s;
}

.step.active .step-number {
  background: var(--apple-primary);
  border-color: var(--apple-primary);
  color: white;
}

.step.completed .step-number {
  background: var(--apple-success);
  border-color: var(--apple-success);
  color: white;
}

.step-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text-secondary);
  transition: color 0.2s;
}

.step.active .step-label {
  color: var(--apple-primary);
}

.step-line {
  width: 60px;
  height: 2px;
  background: var(--apple-border);
  margin: 0 8px 20px 8px;
  transition: background 0.2s;
}

.step-line.active {
  background: var(--apple-success);
}

/* Form Container */
.form-container {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 24px;
}

.form-section {
  min-height: 300px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 4px 0;
}

.section-desc {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin: 0 0 24px 0;
}

/* Form Groups */
.form-group {
  margin-bottom: 20px;
}

.form-group.half {
  flex: 1;
}

.form-row {
  display: flex;
  gap: 20px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text);
  margin-bottom: 8px;
}

.form-label.required::after {
  content: " *";
  color: var(--apple-danger);
}

.form-input,
.form-select,
.form-textarea {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
  font-size: 14px;
  color: var(--apple-text);
  background: var(--apple-bg-secondary);
  outline: none;
  transition: all 0.15s;
  box-sizing: border-box;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  border-color: var(--apple-primary);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.1);
}

.form-select {
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='%238e8e93' stroke-width='2'%3E%3Cpolyline points='6,9 12,15 18,9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  background-size: 16px;
  padding-right: 40px;
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.form-hint {
  font-size: 12px;
  color: var(--apple-text-tertiary);
  margin: 6px 0 0 0;
}

.form-hint.warning {
  color: var(--apple-warning);
}

.form-hint.error {
  color: var(--apple-danger);
}

/* Preview Card */
.preview-card {
  margin-top: 24px;
  padding: 16px;
  background: rgba(0, 122, 255, 0.05);
  border: 1px solid rgba(0, 122, 255, 0.1);
  border-radius: var(--apple-radius);
}

.preview-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--apple-primary);
  margin: 0 0 12px 0;
}

.preview-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-item {
  display: flex;
  gap: 12px;
}

.preview-label {
  font-size: 13px;
  color: var(--apple-text-secondary);
  width: 60px;
  flex-shrink: 0;
}

.preview-value {
  font-size: 13px;
  color: var(--apple-text);
  font-weight: 500;
}

/* Upload Area */
.upload-area {
  margin-bottom: 20px;
}

.upload-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  border: 2px dashed var(--apple-border);
  border-radius: var(--apple-radius-lg);
  cursor: pointer;
  transition: all 0.2s;
}

.upload-box:hover:not(.upload-box--disabled) {
  border-color: var(--apple-primary);
  background: rgba(0, 122, 255, 0.02);
}

.upload-box--disabled {
  cursor: not-allowed;
  opacity: 0.5;
  pointer-events: none;
}

.upload-disabled-hint {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin: 10px 0 0 0;
  line-height: 1.5;
}

.ep-field :deep(.el-date-editor.el-input),
.ep-field :deep(.record-edit-date.el-date-editor),
.ep-field :deep(.record-edit-semester) {
  width: 100%;
}

.ep-field :deep(.el-select .el-input__wrapper) {
  width: 100%;
}

.upload-box svg {
  width: 48px;
  height: 48px;
  color: var(--apple-text-tertiary);
  margin-bottom: 12px;
}

.upload-text {
  font-size: 15px;
  font-weight: 500;
  color: var(--apple-text);
  margin: 0 0 4px 0;
}

.upload-hint {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin: 0;
}

/* Files List */
.files-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--apple-bg-secondary);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
}

.file-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(0, 122, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
}

.file-icon svg {
  width: 18px;
  height: 18px;
  color: var(--apple-primary);
}

.file-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.file-id {
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text);
}

.file-time {
  font-size: 12px;
  color: var(--apple-text-tertiary);
}

.file-actions {
  display: flex;
  gap: 4px;
}

.file-btn {
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

.file-btn svg {
  width: 16px;
  height: 16px;
}

.file-btn.preview svg {
  color: var(--apple-primary);
}

.file-btn.download svg {
  color: var(--apple-success);
}

.file-btn.delete svg {
  color: var(--apple-danger);
}

.file-btn:hover {
  background: var(--apple-bg-secondary);
}

.empty-files {
  text-align: center;
  padding: 24px;
  color: var(--apple-text-secondary);
  font-size: 14px;
}

/* Form Actions */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--apple-border);
}

.action-btn {
  padding: 12px 24px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
}

.action-btn.primary {
  background: var(--apple-primary);
  color: white;
}

.action-btn.primary:hover:not(:disabled) {
  background: #0066d6;
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);
}

.action-btn.primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  box-shadow: none;
}

.action-btn.primary:disabled:hover {
  background: var(--apple-primary);
  box-shadow: none;
}

.action-btn.secondary {
  background: var(--apple-bg-secondary);
  color: var(--apple-text);
  border: 1px solid var(--apple-border);
}

.action-btn.secondary:hover {
  background: var(--apple-border);
}

@media (max-width: 768px) {
  .form-row {
    flex-direction: column;
    gap: 0;
  }

  .steps-indicator {
    padding: 16px;
  }

  .step-line {
    width: 40px;
  }
}
</style>
