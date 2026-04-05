<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'
import type { ApiResponse, PageResult } from '../../types/api'

type Dept = { id: number; deptCode: string; deptName: string; parentId?: number | null; enabled: number; sortNo: number }
type ClassRow = { id: number; deptId: number; className: string; enabled: number; sortNo: number }

const deptsLoading = ref(false)
const depts = ref<Dept[]>([])
const deptNameById = computed(() => {
  const m: Record<number, string> = {}
  for (const d of depts.value) m[d.id] = d.deptName
  return m
})

const selectedDeptId = ref<number | undefined>(undefined)

const loading = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const query = reactive({
  enabled: undefined as number | undefined,
  className: '',
})
const rows = ref<ClassRow[]>([])

async function loadDepts() {
  deptsLoading.value = true
  try {
    const resp = await http.get<ApiResponse<Dept[]>>('/depts')
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    depts.value = resp.data.data
  } finally {
    deptsLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<ClassRow>>>('/admin/classes', {
      params: {
        pageNo: page.pageNo,
        pageSize: page.pageSize,
        deptId: selectedDeptId.value,
        enabled: query.enabled,
        className: query.className || undefined,
      },
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    rows.value = resp.data.data.list
    page.total = resp.data.data.total
  } finally {
    loading.value = false
  }
}

const dlgOpen = ref(false)
const dlgMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const form = reactive({
  deptId: undefined as number | undefined,
  className: '',
  sortNo: 0,
})

function openCreate() {
  dlgMode.value = 'create'
  editingId.value = null
  form.deptId = selectedDeptId.value
  form.className = ''
  form.sortNo = 0
  dlgOpen.value = true
}

function openEdit(row: ClassRow) {
  dlgMode.value = 'edit'
  editingId.value = row.id
  form.deptId = row.deptId
  form.className = row.className
  form.sortNo = row.sortNo || 0
  dlgOpen.value = true
}

async function save() {
  if (!form.deptId || !form.className.trim()) {
    ElMessage.error('请填写院系与班级名称')
    return
  }
  const payload = { deptId: form.deptId, className: form.className.trim(), sortNo: form.sortNo || 0 }
  if (dlgMode.value === 'create') {
    const resp = await http.post<ApiResponse<number>>('/admin/classes', payload)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已新增')
  } else {
    const resp = await http.put<ApiResponse<null>>(`/admin/classes/${editingId.value}`, payload)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已保存')
  }
  dlgOpen.value = false
  await load()
}

async function toggle(row: ClassRow) {
  await ElMessageBox.confirm(`确认要${row.enabled === 1 ? '停用' : '启用'}班级「${row.className}」吗？`, '提示', { type: 'warning' })
  const resp = await http.post<ApiResponse<null>>(`/admin/classes/${row.id}/toggle`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  ElMessage.success('已更新')
  await load()
}

onMounted(async () => {
  await loadDepts()
  await load()
})
</script>

<template>
  <el-row :gutter="12" style="height: 100%">
    <el-col :span="6" style="height: 100%">
      <el-card style="height: 100%" v-loading="deptsLoading">
        <template #header>院系</template>
        <el-menu
          :default-active="selectedDeptId ? String(selectedDeptId) : ''"
          style="border-right: none"
          @select="
            (k: string) => {
              selectedDeptId = k ? Number(k) : undefined
              page.pageNo = 1
              load()
            }
          "
        >
          <el-menu-item index="">全部院系</el-menu-item>
          <el-menu-item v-for="d in depts" :key="d.id" :index="String(d.id)">{{ d.deptName }}</el-menu-item>
        </el-menu>
      </el-card>
    </el-col>

    <el-col :span="18" style="height: 100%">
      <el-space direction="vertical" alignment="start" size="large" style="width: 100%">
        <el-card style="width: 100%">
          <el-space wrap>
            <el-form inline>
              <el-form-item label="状态">
                <el-select v-model="query.enabled" clearable style="width: 140px" placeholder="全部">
                  <el-option label="启用" :value="1" />
                  <el-option label="停用" :value="0" />
                </el-select>
              </el-form-item>
              <el-form-item label="班级名称">
                <el-input v-model="query.className" placeholder="关键字" style="width: 220px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="page.pageNo = 1; load()">查询</el-button>
                <el-button @click="load">刷新</el-button>
              </el-form-item>
            </el-form>
            <el-button type="primary" @click="openCreate">新增班级</el-button>
          </el-space>
        </el-card>

        <el-card style="width: 100%">
          <el-table :data="rows" v-loading="loading" style="width: 100%">
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="deptId" label="院系" width="200">
              <template #default="{ row }">
                <span>{{ deptNameById[row.deptId] || `ID:${row.deptId}` }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="className" label="班级名称" min-width="220" />
            <el-table-column prop="sortNo" label="排序" width="100" />
            <el-table-column prop="enabled" label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button size="small" @click="openEdit(row)">编辑</el-button>
                <el-button size="small" type="warning" @click="toggle(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div style="display: flex; justify-content: flex-end; margin-top: 12px">
            <el-pagination
              background
              layout="total, prev, pager, next, sizes"
              :total="page.total"
              v-model:current-page="page.pageNo"
              v-model:page-size="page.pageSize"
              @change="load"
            />
          </div>
        </el-card>
      </el-space>
    </el-col>
  </el-row>

  <el-dialog v-model="dlgOpen" :title="dlgMode === 'create' ? '新增班级' : '编辑班级'" width="520px">
    <el-form label-width="90px">
      <el-form-item label="院系">
        <el-select v-model="form.deptId" filterable style="width: 100%">
          <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="班级名称">
        <el-input v-model="form.className" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortNo" :min="0" :max="9999" style="width: 180px" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dlgOpen = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

