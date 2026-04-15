<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

function teamNameText(row: MyAward) {
  if (!row.teamName) return '已解散的团队'
  return row.teamDeleted ? `${row.teamName}（团队已解散）` : row.teamName
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
      <p class="page-subtitle">展示您作为队员、队长或指导教师参与并审核通过的获奖记录</p>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="competitionName" label="竞赛名称" min-width="180" />
      <el-table-column label="团队名称" min-width="180">
        <template #default="{ row }">
          {{ teamNameText(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="awardName" label="获奖项目" min-width="180" />
      <el-table-column prop="awardLevel" label="获奖等级" min-width="120" />
      <el-table-column prop="awardTime" label="获奖日期" min-width="130" />
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
</style>
