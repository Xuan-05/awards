<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { http } from '../api/http'
import { ElMessage } from 'element-plus'
import { labelExportType } from '../utils/displayLabels'

type ApiResponse<T> = { code: number; message: string; data: T }
type PageResult<T> = { total: number; list: T[] }

type ExportTask = {
  id: number
  exportType: string
  taskStatus: string
  fileName?: string
  errorMessage?: string
  createdAt?: string
  finishedAt?: string
}

const creating = ref(false)
const loading = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const rows = ref<ExportTask[]>([])

function getStatusClass(status: string) {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'failed'
    default: return 'pending'
  }
}

function getStatusText(status: string) {
  switch (status) {
    case 'SUCCESS': return '已完成'
    case 'FAILED': return '失败'
    case 'PROCESSING': return '处理中'
    default: return '待处理'
  }
}

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<ExportTask>>>('/exports/tasks', {
      params: { pageNo: page.pageNo, pageSize: page.pageSize },
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    page.total = resp.data.data.total
    rows.value = resp.data.data.list
  } finally {
    loading.value = false
  }
}

// 刷新：回到第一页并重新加载
function refresh() {
  page.pageNo = 1
  load()
}

async function createDetail() {
  creating.value = true
  try {
    const resp = await http.post<ApiResponse<number>>('/exports/detail', null)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('任务已创建')
    refresh()
  } finally {
    creating.value = false
  }
}

async function createSummary() {
  creating.value = true
  try {
    const resp = await http.post<ApiResponse<number>>('/exports/summary', null)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('任务已创建')
    refresh()
  } finally {
    creating.value = false
  }
}

function download(id: number) {
  window.open(`/api/exports/tasks/${id}/download`)
}

onMounted(load)
</script>

<template>
  <div class="export-page">
    <div class="page-header">
      <h1 class="page-title">数据导出</h1>
      <p class="page-subtitle">导出任务管理与下载</p>
    </div>

    <div class="tip-banner">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
        <line x1="12" y1="9" x2="12" y2="13"/>
        <line x1="12" y1="17" x2="12.01" y2="17"/>
      </svg>
      <span>导出严格走模板填充，请将学校模板放到后端配置的模板目录中</span>
    </div>

    <!-- 顶部栏：左侧总数 + 右侧按钮组 -->
    <div class="action-bar">
      <div class="total-text">
        共 {{ page.total }} 条
      </div>
      <div class="action-buttons">
        <button class="action-btn primary" :disabled="creating" @click="createDetail">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14,2 14,8 20,8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
          <span>生成明细导出</span>
        </button>
        <button class="action-btn primary" :disabled="creating" @click="createSummary">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17,8 12,3 7,8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span>生成汇总导出</span>
        </button>
        <button class="refresh-btn" @click="refresh">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M23 4v6h-6M1 20v-6h6"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
          刷新
        </button>
      </div>
    </div>

    <div class="task-list" v-loading="loading">
      <div v-if="rows.length === 0 && !loading" class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/>
          <polyline points="13,2 13,9 20,9"/>
        </svg>
        <p>暂无导出任务</p>
        <span>点击上方按钮创建新任务</span>
      </div>

      <div v-for="task in rows" :key="task.id" class="task-item">
        <div class="task-icon" :class="getStatusClass(task.taskStatus)">
          <svg v-if="task.taskStatus === 'SUCCESS'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22,4 12,14.01 9,11.01"/>
          </svg>
          <svg v-else-if="task.taskStatus === 'FAILED'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="15" y1="9" x2="9" y2="15"/>
            <line x1="9" y1="9" x2="15" y2="15"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12,6 12,12 16,14"/>
          </svg>
        </div>

        <div class="task-content">
          <div class="task-header">
            <span class="task-id">#{{ task.id }}</span>
            <span class="task-type">{{ labelExportType(task.exportType) }}</span>
            <span class="task-status" :class="getStatusClass(task.taskStatus)">
              {{ getStatusText(task.taskStatus) }}
            </span>
          </div>
          <div class="task-info">
            <div class="info-row" v-if="task.fileName">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14,2 14,8 20,8"/>
              </svg>
              <span>{{ task.fileName }}</span>
            </div>
            <div class="info-row" v-if="task.errorMessage">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
              <span class="error-text">{{ task.errorMessage }}</span>
            </div>
            <div class="info-row">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                <line x1="16" y1="2" x2="16" y2="6"/>
                <line x1="8" y1="2" x2="8" y2="6"/>
                <line x1="3" y1="10" x2="21" y2="10"/>
              </svg>
              <span>创建: {{ task.createdAt || '-' }}</span>
              <span v-if="task.finishedAt" style="margin-left: 16px;">完成: {{ task.finishedAt }}</span>
            </div>
          </div>
        </div>

        <div class="task-action">
          <button v-if="task.taskStatus === 'SUCCESS'" class="download-btn" @click="download(task.id)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7,10 12,15 17,10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            下载
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.export-page {
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

.tip-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(255, 149, 0, 0.08);
  border: 1px solid rgba(255, 149, 0, 0.2);
  border-radius: 12px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--apple-text-secondary);
}

.tip-banner svg {
  width: 18px;
  height: 18px;
  color: #ff9500;
  flex-shrink: 0;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.total-text {
  font-size: 14px;
  color: var(--apple-text-secondary);
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 12px;
  border: none;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn svg {
  width: 18px;
  height: 18px;
}

.action-btn.primary {
  background: #007bff;
  color: white;
}

.action-btn.primary:hover:not(:disabled) {
  background: #0066d6;
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 12px;
  border: 1px solid var(--apple-border);
  background: #fff;
  color: #333;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.refresh-btn svg {
  width: 14px;
  height: 14px;
}

.refresh-btn:hover {
  background: #f5f5f7;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 400px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  background: rgba(255,255,255,0.8);
  border: 1px solid var(--apple-border);
  border-radius: 16px;
  color: #8a8a8a;
}

.empty-state svg {
  width: 48px;
  height: 48px;
  margin-bottom: 16px;
  opacity: 0.4;
}

.empty-state p {
  font-size: 15px;
  font-weight: 500;
  color: #666;
  margin: 0 0 4px 0;
}

.empty-state span {
  font-size: 13px;
}

.task-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 20px;
  background: rgba(255,255,255,0.8);
  border: 1px solid var(--apple-border);
  border-radius: 16px;
  transition: all 0.2s;
}

.task-item:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.task-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.task-icon svg {
  width: 20px;
  height: 20px;
}

.task-icon.success {
  background: rgba(52, 199, 89, 0.12);
  color: #34c759;
}

.task-icon.failed {
  background: rgba(255, 59, 48, 0.12);
  color: #ff3b30;
}

.task-icon.pending {
  background: rgba(255, 149, 0, 0.12);
  color: #ff9500;
}

.task-content {
  flex: 1;
  min-width: 0;
}

.task-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.task-id {
  font-size: 12px;
  font-weight: 600;
  color: #666;
  background: #f2f2f7;
  padding: 2px 8px;
  border-radius: 4px;
}

.task-type {
  font-size: 14px;
  font-weight: 600;
  color: #111;
}

.task-status {
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 6px;
}

.task-status.success {
  background: rgba(52, 199, 89, 0.12);
  color: #34c759;
}

.task-status.failed {
  background: rgba(255, 59, 48, 0.12);
  color: #ff3b30;
}

.task-status.pending {
  background: rgba(255, 149, 0, 0.12);
  color: #ff9500;
}

.task-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #666;
}

.info-row svg {
  width: 14px;
  height: 14px;
  opacity: 0.5;
}

.error-text {
  color: #ff3b30;
}

.task-action {
  flex-shrink: 0;
}

.download-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 12px;
  border: none;
  background: #007bff;
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.download-btn svg {
  width: 16px;
  height: 16px;
}

.download-btn:hover {
  background: #0066d6;
}

:root {
  --apple-text: #111827;
  --apple-text-secondary: #6b7280;
  --apple-text-tertiary: #9ca3af;
  --apple-border: #e5e7eb;
  --apple-bg-secondary: #f9fafb;
}
</style>