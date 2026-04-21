<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { http } from '../../api/http'
import type { ApiResponse } from '../../types/api'

type MyAward = {
  recordId: number
  teamId: number
  teamName: string
  teamDeleted: boolean
  competitionName: string
  awardName: string
  awardLevel: string
  awardTime: string
  role: string
}

const loading = ref(false)
const rows = ref<MyAward[]>([])
const activeRole = ref<string>('ALL')

const roleTabs = computed(() => {
  const roleSet = new Set(rows.value.map((row) => row.role).filter(Boolean))
  return ['ALL', ...Array.from(roleSet)]
})

const filteredRows = computed(() => {
  if (activeRole.value === 'ALL') return rows.value
  return rows.value.filter((row) => row.role === activeRole.value)
})

function teamNameText(row: MyAward) {
  if (!row.teamName) return '已解散的团队'
  return row.teamDeleted ? `${row.teamName}（团队已解散）` : row.teamName
}

function formatDateCn(input: string) {
  if (!input) return '-'
  const date = new Date(input)
  if (Number.isNaN(date.getTime())) return input
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}年${month}月${day}日`
}

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<MyAward[]>>('/my-awards')
    if (resp.data.code !== 0) throw new Error(resp.data.message || '加载失败')
    rows.value = resp.data.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="my-awards-page">
    <div class="page-header">
      <h1 class="page-title">我的获奖</h1>
      <p class="page-subtitle">展示您参与并审核通过的获奖记录，支持按身份分类查看</p>
    </div>

    <div class="role-tabs">
      <button
        v-for="role in roleTabs"
        :key="role"
        class="role-tab"
        :class="{ active: activeRole === role }"
        @click="activeRole = role"
      >
        {{ role === 'ALL' ? '全部身份' : role }}
      </button>
    </div>

    <el-table v-loading="loading" :data="filteredRows" stripe>
      <el-table-column prop="competitionName" label="竞赛名称" min-width="180" />
      <el-table-column label="团队名称" min-width="180">
        <template #default="{ row }">
          {{ teamNameText(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="awardName" label="获奖项目" min-width="180" />
      <el-table-column prop="awardLevel" label="获奖等级" min-width="120" />
      <el-table-column prop="awardTime" label="获奖日期" min-width="150">
        <template #default="{ row }">{{ formatDateCn(row.awardTime) }}</template>
      </el-table-column>
      <el-table-column prop="role" label="角色" min-width="110" />

      <template #empty>
        <el-empty description="暂无审核通过的获奖记录" />
      </template>
    </el-table>
  </div>
</template>

<style scoped>
.my-awards-page {
  padding: 8px;
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  color: var(--apple-text);
}

.page-subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--apple-text-secondary);
}

.role-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.role-tab {
  border: 1px solid var(--apple-border);
  background: var(--apple-bg-secondary);
  color: var(--apple-text-secondary);
  padding: 6px 12px;
  border-radius: 14px;
  cursor: pointer;
}

.role-tab.active {
  background: var(--apple-primary);
  color: #fff;
  border-color: var(--apple-primary);
}
</style>
