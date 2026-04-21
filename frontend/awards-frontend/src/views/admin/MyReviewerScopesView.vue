<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'

type ApiResponse<T> = { code: number; message: string; data: T }
type ReviewerScopeRow = {
  id: number
  competitionId: number
  competitionName: string
  enabled: number
  validFrom?: string | null
  validTo?: string | null
}

const loading = ref(false)
const rows = ref<ReviewerScopeRow[]>([])

const now = computed(() => new Date())

function formatDateTimeCn(input?: string | null) {
  if (!input) return '长期'
  const d = new Date(input)
  if (Number.isNaN(d.getTime())) return input
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${d.getFullYear()}年${month}月${day}日 ${hh}:${mm}`
}

function activeStatus(row: ReviewerScopeRow) {
  if (row.enabled !== 1) return { label: '已停用', type: 'danger' as const }
  const nowTime = now.value.getTime()
  const fromOk = row.validFrom ? new Date(row.validFrom).getTime() <= nowTime : true
  const toOk = row.validTo ? new Date(row.validTo).getTime() >= nowTime : true
  if (fromOk && toOk) return { label: '生效中', type: 'success' as const }
  return { label: '未生效/已过期', type: 'warning' as const }
}

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<ReviewerScopeRow[]>>('/admin/reviewers/my-scopes')
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    rows.value = resp.data.data
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="scope-page">
    <div class="page-header">
      <h1 class="page-title">我的竞赛权限</h1>
      <p class="page-subtitle">查看当前账号可审核的竞赛及授权时间期限</p>
    </div>

    <el-alert
      title="说明：当授权状态为“生效中”时，一级审核台才能看到对应竞赛的待审记录。"
      type="info"
      show-icon
      :closable="false"
      class="tips"
    />

    <el-table :data="rows" border stripe v-loading="loading" empty-text="当前账号尚未配置竞赛授权">
      <el-table-column type="index" label="序号" width="70" />
      <el-table-column prop="competitionName" label="竞赛名称" min-width="260" />
      <el-table-column prop="competitionId" label="竞赛ID" width="110" />
      <el-table-column label="授权状态" width="120">
        <template #default="{ row }">
          <el-tag :type="activeStatus(row).type" effect="light">{{ activeStatus(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用开关" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" effect="plain">
            {{ row.enabled === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生效开始时间" min-width="190">
        <template #default="{ row }">{{ formatDateTimeCn(row.validFrom) }}</template>
      </el-table-column>
      <el-table-column label="生效结束时间" min-width="190">
        <template #default="{ row }">{{ formatDateTimeCn(row.validTo) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.scope-page {
  padding: 8px;
}
.page-header {
  margin-bottom: 12px;
}
.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}
.page-subtitle {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}
.tips {
  margin-bottom: 12px;
}
</style>
