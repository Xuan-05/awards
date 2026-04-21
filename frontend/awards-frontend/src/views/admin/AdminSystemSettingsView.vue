<script setup lang="ts">
/**
 * 管理端 - 系统设置页面（/admin/system）
 *
 * 页面分两块：
 * 1) 系统参数配置：从后端 sys_config 读取，可编辑并保存（/api/system/configs）。
 * 2) 高级配置只读展示：展示当前运行时配置（/api/system/runtime），用于运维排查与避免前端硬编码。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
import type { ApiResponse } from '../../types/api'
import { labelConfigValueType } from '../../utils/displayLabels'

/**
 * 系统参数（后端 SysConfig 对应字段）。
 * - configKey：参数 key（如 max_teacher_count）
 * - configValue：参数值（统一用字符串存储）
 * - valueType：类型提示（STRING/INT/BOOL/JSON）
 */
type SysConfig = {
  id: number
  configKey: string
  configValue: string
  valueType: string
  remark?: string
  updatedBy?: number
  updatedAt?: string
}

/**
 * 运行时配置（只读展示）。
 * 这些值来自后端 application.yml 当前生效配置，不保证修改后立即生效。
 */
type RuntimeInfo = {
  storageBaseDir: string
  exportOutDir: string
  exportTemplateDetail: string
  exportTemplateSummary: string
}

// 页面加载状态（表格与描述信息共用）
const loading = ref(false)
// 关键字搜索：按 key/备注过滤
const keyword = ref('')
// sys_config 全量列表
const list = ref<SysConfig[]>([])
// 运行时配置（只读展示）
const runtime = ref<RuntimeInfo | null>(null)
const configKeyLabelMap: Record<string, string> = {
  max_teacher_count: '团队最大指导教师数',
  upload_max_single_file_mb: '单文件上传大小上限(MB)',
  record_attachment_required: '提交填报前必须上传附件',
  allow_admin_supplement_after_deadline: '截止后允许管理员补录',
}

function labelConfigKey(configKey: string) {
  return configKeyLabelMap[configKey] || '自定义参数'
}

/**
 * 根据 keyword 对 sys_config 做前端过滤（key/remark 里包含关键字）。
 * 说明：这里不走后端过滤，列表数据量通常很小。
 */
const filtered = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return list.value
  return list.value.filter((c) => c.configKey.includes(kw) || (c.remark || '').includes(kw))
})

/**
 * 拉取系统参数列表（sys_config）。
 * 接口：GET /api/system/configs
 */
async function loadConfigs() {
  const resp = await http.get<ApiResponse<SysConfig[]>>('/system/configs')
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  list.value = resp.data.data
}

/**
 * 拉取运行时配置（只读展示）。
 * 接口：GET /api/system/runtime
 */
async function loadRuntime() {
  const resp = await http.get<ApiResponse<RuntimeInfo>>('/system/runtime')
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  runtime.value = resp.data.data
}

/**
 * 页面初始化/刷新：
 * - 并发加载 sys_config 与 runtime 信息
 */
async function load() {
  loading.value = true
  try {
    await Promise.all([loadConfigs(), loadRuntime()])
  } finally {
    loading.value = false
  }
}

// 编辑弹窗：是否打开
const editOpen = ref(false)
// 当前正在编辑的配置项（点击表格“编辑”时赋值）
const editing = ref<SysConfig | null>(null)
// 编辑表单（注意：sys_config 的 value 在后端统一存字符串）
const form = reactive({ configValue: '', valueType: 'STRING', remark: '' })
// 保存按钮 loading
const saving = ref(false)

/**
 * 打开编辑弹窗，并把当前行数据填入表单。
 */
function openEdit(row: SysConfig) {
  editing.value = row
  form.configValue = row.configValue
  form.valueType = row.valueType || 'STRING'
  form.remark = row.remark || ''
  editOpen.value = true
}

// 重点参数：需要在编辑弹窗中提供“更友好”的控件（开关/数字框）
const FRIENDLY_INT_KEYS = ['max_teacher_count', 'upload_max_single_file_mb'] as const
const FRIENDLY_BOOL_KEYS = ['record_attachment_required', 'allow_admin_supplement_after_deadline'] as const
const friendlyKeys = new Set<string>([...FRIENDLY_INT_KEYS, ...FRIENDLY_BOOL_KEYS])

// 当前编辑项是否属于重点参数
const isFriendly = computed(() => (editing.value ? friendlyKeys.has(editing.value.configKey) : false))

/**
 * Bool 控件与 form.configValue 的互转：
 * - true => '1'
 * - false => '0'
 */
const asBool = computed({
  get() {
    const v = (form.configValue || '').trim().toLowerCase()
    return v === '1' || v === 'true' || v === 'yes' || v === 'y'
  },
  set(val: boolean) {
    form.configValue = val ? '1' : '0'
    form.valueType = 'BOOL'
  },
})

/**
 * Int 控件与 form.configValue 的互转。
 */
const asInt = computed({
  get() {
    const n = Number.parseInt(form.configValue || '0', 10)
    return Number.isFinite(n) ? n : 0
  },
  set(val: number) {
    form.configValue = String(val)
    form.valueType = 'INT'
  },
})

// 当前编辑项是否是“布尔开关”类重点参数
const isBoolKey = computed(() => (editing.value ? FRIENDLY_BOOL_KEYS.includes(editing.value.configKey as any) : false))
// 当前编辑项是否是“整数输入”类重点参数
const isIntKey = computed(() => (editing.value ? FRIENDLY_INT_KEYS.includes(editing.value.configKey as any) : false))

/**
 * 保存系统参数（覆盖式更新）。
 * 接口：PUT /api/system/configs/{key}
 */
async function save() {
  if (!editing.value) return
  saving.value = true
  try {
    const resp = await http.put<ApiResponse<null>>(`/system/configs/${editing.value.configKey}`, {
      configValue: form.configValue,
      valueType: form.valueType,
      remark: form.remark || undefined,
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已保存')
    editOpen.value = false
    // 保存成功后刷新列表：确保表格展示的是最新值
    await loadConfigs()
  } finally {
    saving.value = false
  }
}

/**
 * 页面挂载后加载数据。
 */
onMounted(async () => {
  try {
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  }
})

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}年${String(d.getMonth() + 1).padStart(2, '0')}月${String(d.getDate()).padStart(2, '0')}日`
}
</script>

<template>
  <div class="settings-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">系统设置</h1>
      <p class="page-subtitle">系统参数与运行时配置管理</p>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <div class="search-input">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/>
          <line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <input v-model="keyword" placeholder="搜索参数..." />
      </div>
      <button class="refresh-btn" @click="load">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M23 4v6h-6M1 20v-6h6"/>
          <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
        </svg>
        刷新
      </button>
    </div>

    <div class="settings-layout" v-loading="loading">
      <!-- 系统参数卡片组 -->
      <div class="settings-section">
        <div class="section-header">
          <h2>系统参数</h2>
          <span class="section-count">{{ filtered.length }} 项</span>
        </div>
        
        <div class="settings-grid">
          <div 
            v-for="config in filtered" 
            :key="config.id" 
            class="setting-card"
            @click="openEdit(config)"
          >
            <div class="setting-header">
              <div class="setting-icon">
                <svg v-if="config.valueType === 'BOOL'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="1" y="5" width="22" height="14" rx="7" ry="7"/>
                  <circle cx="16" cy="12" r="3"/>
                </svg>
                <svg v-else-if="config.valueType === 'INT'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                  <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                </svg>
                <svg v-else-if="config.valueType === 'JSON'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="16,18 22,12 16,6"/>
                  <polyline points="8,6 2,12 8,18"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14,2 14,8 20,8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                </svg>
              </div>
              <span class="setting-type" :class="config.valueType.toLowerCase()">{{ labelConfigValueType(config.valueType) }}</span>
            </div>
            
            <div class="setting-body">
              <h4 class="setting-key">{{ labelConfigKey(config.configKey) }}</h4>
              <p class="setting-key-sub">{{ config.configKey }}</p>
              <p class="setting-remark">{{ config.remark || '暂无说明' }}</p>
            </div>
            
            <div class="setting-footer">
              <div class="setting-value">
                <span class="value-label">当前值</span>
                <span class="value-text">{{ config.configValue }}</span>
              </div>
              <div class="setting-meta">
                <span v-if="config.updatedAt">{{ formatDate(config.updatedAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 高级配置卡片 -->
      <div class="settings-section advanced">
        <div class="section-header">
          <h2>高级配置</h2>
          <span class="section-badge">只读</span>
        </div>

        <div class="tip-banner">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <span>这些配置来自当前运行时配置，修改可能需要重启才生效</span>
        </div>

        <div class="runtime-grid">
          <div class="runtime-item">
            <div class="runtime-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <div class="runtime-content">
              <span class="runtime-label">存储根目录</span>
              <span class="runtime-value">{{ runtime?.storageBaseDir || '-' }}</span>
            </div>
          </div>

          <div class="runtime-item">
            <div class="runtime-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17,8 12,3 7,8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
            </div>
            <div class="runtime-content">
              <span class="runtime-label">导出输出目录</span>
              <span class="runtime-value">{{ runtime?.exportOutDir || '-' }}</span>
            </div>
          </div>

          <div class="runtime-item">
            <div class="runtime-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14,2 14,8 20,8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
            </div>
            <div class="runtime-content">
              <span class="runtime-label">明细导出模板路径</span>
              <span class="runtime-value">{{ runtime?.exportTemplateDetail || '-' }}</span>
            </div>
          </div>

          <div class="runtime-item">
            <div class="runtime-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14,2 14,8 20,8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
            </div>
            <div class="runtime-content">
              <span class="runtime-label">汇总导出模板路径</span>
              <span class="runtime-value">{{ runtime?.exportTemplateSummary || '-' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editOpen" title="编辑参数" width="480px" class="apple-dialog">
      <div class="edit-header" v-if="editing">
        <div class="edit-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
        </div>
        <div class="edit-info">
          <h4>{{ editing.configKey }}</h4>
          <p>{{ editing.remark || '暂无说明' }}</p>
        </div>
      </div>

      <div class="edit-form">
        <div class="form-group">
          <label>参数值</label>
          <template v-if="isFriendly && isBoolKey">
            <div class="switch-control">
              <el-switch v-model="asBool" />
              <span class="switch-label">{{ asBool ? '开启' : '关闭' }}</span>
            </div>
          </template>
          <template v-else-if="isFriendly && isIntKey">
            <el-input-number v-model="asInt" :min="0" style="width: 100%" />
          </template>
          <template v-else>
            <el-input v-model="form.configValue" placeholder="输入参数值" />
          </template>
        </div>

        <div class="form-group">
          <label>类型</label>
          <el-select v-model="form.valueType" style="width: 100%">
            <el-option label="字符串" value="STRING" />
            <el-option label="整数" value="INT" />
            <el-option label="布尔" value="BOOL" />
            <el-option label="结构化数据" value="JSON" />
          </el-select>
        </div>

        <div class="form-group">
          <label>说明</label>
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="参数说明" />
        </div>
      </div>

      <template #footer>
        <el-button @click="editOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.settings-page {
  padding: 8px;
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

/* Search Bar */
.search-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.search-input {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius);
  width: 320px;
}

.search-input svg {
  width: 16px;
  height: 16px;
  color: var(--apple-text-tertiary);
}

.search-input input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 14px;
  color: var(--apple-text);
  outline: none;
}

.search-input input::placeholder {
  color: var(--apple-text-tertiary);
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-radius: var(--apple-radius);
  border: 1px solid var(--apple-border);
  background: var(--apple-glass);
  color: var(--apple-text);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.refresh-btn svg {
  width: 14px;
  height: 14px;
}

.refresh-btn:hover {
  background: var(--apple-bg-secondary);
}

/* Settings Layout */
.settings-layout {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.settings-section {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-lg);
  padding: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--apple-border);
}

.section-header h2 {
  font-size: 16px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0;
}

.section-count {
  font-size: 12px;
  color: var(--apple-text-tertiary);
  background: var(--apple-bg-secondary);
  padding: 4px 10px;
  border-radius: 10px;
}

.section-badge {
  font-size: 11px;
  font-weight: 500;
  color: var(--apple-warning);
  background: rgba(255, 149, 0, 0.12);
  padding: 4px 10px;
  border-radius: 10px;
}

/* Settings Grid */
.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.setting-card {
  padding: 16px;
  background: var(--apple-bg-secondary);
  border-radius: var(--apple-radius);
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.setting-card:hover {
  background: var(--apple-bg-tertiary);
  border-color: var(--apple-primary);
  transform: translateY(-2px);
}

.setting-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.setting-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--apple-bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.setting-icon svg {
  width: 16px;
  height: 16px;
  color: var(--apple-primary);
}

.setting-type {
  font-size: 10px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 4px;
  text-transform: uppercase;
}

.setting-type.string {
  background: rgba(88, 86, 214, 0.12);
  color: #5856D6;
}

.setting-type.int {
  background: rgba(52, 199, 89, 0.12);
  color: var(--apple-success);
}

.setting-type.bool {
  background: rgba(255, 149, 0, 0.12);
  color: var(--apple-warning);
}

.setting-type.json {
  background: rgba(255, 59, 48, 0.12);
  color: var(--apple-danger);
}

.setting-body {
  margin-bottom: 12px;
}

.setting-key {
  font-size: 13px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 4px 0;
}

.setting-key-sub {
  font-size: 11px;
  color: var(--apple-text-tertiary);
  margin: 0 0 4px 0;
  font-family: 'SF Mono', Monaco, monospace;
}

.setting-remark {
  font-size: 12px;
  color: var(--apple-text-secondary);
  margin: 0;
  line-height: 1.4;
}

.setting-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid var(--apple-border);
}

.setting-value {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.value-label {
  font-size: 10px;
  color: var(--apple-text-tertiary);
}

.value-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--apple-text);
}

.setting-meta {
  font-size: 11px;
  color: var(--apple-text-tertiary);
}

/* Advanced Section */
.settings-section.advanced {
  background: rgba(255, 149, 0, 0.02);
}

.tip-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: rgba(255, 149, 0, 0.08);
  border: 1px solid rgba(255, 149, 0, 0.15);
  border-radius: var(--apple-radius);
  margin-bottom: 16px;
  font-size: 12px;
  color: var(--apple-text-secondary);
}

.tip-banner svg {
  width: 16px;
  height: 16px;
  color: var(--apple-warning);
  flex-shrink: 0;
}

.runtime-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.runtime-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  background: var(--apple-bg-secondary);
  border-radius: var(--apple-radius);
}

.runtime-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--apple-bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.runtime-icon svg {
  width: 18px;
  height: 18px;
  color: var(--apple-text-secondary);
}

.runtime-content {
  flex: 1;
  min-width: 0;
}

.runtime-label {
  display: block;
  font-size: 11px;
  font-weight: 500;
  color: var(--apple-text-tertiary);
  margin-bottom: 4px;
  font-family: 'SF Mono', Monaco, monospace;
}

.runtime-value {
  display: block;
  font-size: 13px;
  color: var(--apple-text);
  word-break: break-all;
}

/* Edit Dialog */
.apple-dialog .edit-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--apple-border);
}

.edit-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(0, 122, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.edit-icon svg {
  width: 20px;
  height: 20px;
  color: var(--apple-primary);
}

.edit-info h4 {
  font-size: 15px;
  font-weight: 600;
  color: var(--apple-text);
  margin: 0 0 4px 0;
  font-family: 'SF Mono', Monaco, monospace;
}

.edit-info p {
  font-size: 12px;
  color: var(--apple-text-secondary);
  margin: 0;
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--apple-text-secondary);
  margin-bottom: 8px;
}

.switch-control {
  display: flex;
  align-items: center;
  gap: 12px;
}

.switch-label {
  font-size: 13px;
  color: var(--apple-text);
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

