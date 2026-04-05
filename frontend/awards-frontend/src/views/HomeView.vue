<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = useRouter()
const user = useUserStore()

async function logout() {
  await user.logout()
  await router.replace('/login')
}
</script>

<template>
  <div style="padding: 16px">
    <el-alert
      v-if="!user.authed"
      title="未登录：请先登录"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-space direction="vertical" alignment="start" size="large">
      <el-card style="width: 640px">
        <template #header>基础连通性</template>
        <div>token：{{ user.token ? '已存在' : '无' }}</div>
        <div v-if="user.me">当前用户：{{ user.me.realName }}（{{ user.me.userType }} / dept={{ user.me.deptId }}）</div>
        <div v-if="user.me">角色：{{ user.me.roles.join(', ') || '-' }}</div>
      </el-card>

      <el-space>
        <el-button type="primary" @click="router.push('/login')">去登录页</el-button>
        <el-button type="danger" @click="logout">清除 token</el-button>
      </el-space>
    </el-space>
  </div>
</template>

