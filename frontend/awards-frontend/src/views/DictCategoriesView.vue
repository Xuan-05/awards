<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { http } from '../api/http'
import { ElMessage } from 'element-plus'

type ApiResponse<T> = { code: number; message: string; data: T }
type PageResult<T> = { total: number; list: T[] }

type Category = {
  id: number
  categoryName: string
  enabled: number
  sortNo: number
  remark?: string
}

const loading = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const rows = ref<Category[]>([])
const query = reactive({ enabled: undefined as number | undefined })

async function load() {
  loading.value = true
  try {
    const resp = await http.get<ApiResponse<PageResult<Category>>>('/dicts/categories', {
      params: { pageNo: page.pageNo, pageSize: page.pageSize, enabled: query.enabled },
    })
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    page.total = resp.data.data.total
    rows.value = resp.data.data.list
  } finally {
    loading.value = false
  }
}

const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ categoryName: '', sortNo: 0, remark: '' })

function openCreate() {
  editingId.value = null
  form.categoryName = ''
  form.sortNo = 0
  form.remark = ''
  dialogOpen.value = true
}

function openEdit(row: Category) {
  editingId.value = row.id
  form.categoryName = row.categoryName
  form.sortNo = row.sortNo ?? 0
  form.remark = row.remark || ''
  dialogOpen.value = true
}

async function save() {
  if (!form.categoryName) {
    ElMessage.error('请填写名称')
    return
  }
  if (editingId.value) {
    const resp = await http.put<ApiResponse<null>>(`/dicts/categories/${editingId.value}`, form)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已保存')
  } else {
    const resp = await http.post<ApiResponse<number>>('/dicts/categories', form)
    if (resp.data.code !== 0) throw new Error(resp.data.message)
    ElMessage.success('已创建')
  }
  dialogOpen.value = false
  await load()
}

async function toggle(row: Category) {
  const resp = await http.post<ApiResponse<null>>(`/dicts/categories/${row.id}/toggle`)
  if (resp.data.code !== 0) throw new Error(resp.data.message)
  ElMessage.success('已切换')
  await load()
}

onMounted(load)
</script>

<template>
  <el-space direction="vertical" alignment="start" size="large" style="width: 100%">
    <el-card style="width: 100%">
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="query.enabled" style="width: 180px" @change="load" clearable placeholder="全部">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="openCreate">新增</el-button>
          <el-button @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="width: 100%">
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="编号" width="80" />
        <el-table-column prop="categoryName" label="名称" />
        <el-table-column prop="enabled" label="启用" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序" width="100" />
        <el-table-column label="操作" width="200">
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

    <el-dialog v-model="dialogOpen" :title="editingId ? '编辑类别' : '新增类别'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="form.categoryName" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-space>
</template>

