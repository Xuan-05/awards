<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { http } from '../api/http'
import type { FormInstance, FormRules } from 'element-plus'
import { labelRecordStatus } from '../utils/displayLabels'

type ApiResponse<T> = { code: number; message: string; data: T }

type AwardRecord = {
  id: number
  status: string
  teamId: number
  competitionId: number
  projectName?: string
  semester?: string
  awardDate?: string
  updatedAt?: string
}

type PageResult<T> = { total: number; list: T[] }

const loading = ref(false)
const list = ref<AwardRecord[]>([])
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<AwardRecord>>>('/award-records/my', {
      params: { pageNo: 1, pageSize: 20 },
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message || '加载失败')
    list.value = resp.data.data.list
    total.value = resp.data.data.total
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  teamId: undefined as number | undefined,
  competitionId: undefined as number | undefined,
  projectName: '',
  semester: '',
})
const rules: FormRules = {
  teamId: [{ required: true, message: '请输入团队编号', trigger: 'blur' }],
  competitionId: [{ required: true, message: '请输入竞赛编号', trigger: 'blur' }],
}

async function create() {
  await formRef.value?.validate()
  const resp = await http.post<ApiResponse<number>>('/award-records', form)
  if (resp.data.code !== 0) throw new Error(resp.data.message || '创建失败')
  dialogVisible.value = false
  await load()
}

async function submit(id: number) {
  const resp = await http.post<ApiResponse<void>>(`/award-records/${id}/submit`)
  if (resp.data.code !== 0) throw new Error(resp.data.message || '提交失败')
  await load()
}

onMounted(load)
</script>

<template>
  <div style="padding: 16px">
    <el-space style="margin-bottom: 12px">
      <el-button type="primary" @click="dialogVisible = true">新建获奖记录</el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
      <span>共 {{ total }} 条</span>
    </el-space>

    <el-table :data="list" border :loading="loading">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column label="状态" width="140">
        <template #default="{ row }">{{ labelRecordStatus(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="teamId" label="团队编号" width="100" />
      <el-table-column prop="competitionId" label="竞赛编号" width="120" />
      <el-table-column prop="projectName" label="项目名称" min-width="180" />
      <el-table-column prop="semester" label="学期" width="120" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'DRAFT' || row.status === 'SCHOOL_REJECTED'"
            size="small"
            type="primary"
            @click="submit(row.id)"
          >
            提交
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新建获奖记录" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="团队编号" prop="teamId">
          <el-input v-model.number="form.teamId" />
        </el-form-item>
        <el-form-item label="竞赛编号" prop="competitionId">
          <el-input v-model.number="form.competitionId" />
        </el-form-item>
        <el-form-item label="项目名称">
          <el-input v-model="form.projectName" />
        </el-form-item>
        <el-form-item label="学期">
          <el-input v-model="form.semester" placeholder="如 2025-2026-1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

