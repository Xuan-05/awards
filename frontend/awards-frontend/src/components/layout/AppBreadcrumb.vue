<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

type Crumb = { title: string; path?: string }

const crumbs = computed<Crumb[]>(() => {
  const meta = route.meta || {}
  const fromMeta = (meta.breadcrumb as Crumb[] | undefined) || []
  if (fromMeta.length > 0) return fromMeta
  const title = (meta.title as string | undefined) || String(route.name || route.path)
  return [{ title }]
})

function to(crumb: Crumb) {
  if (!crumb.path) return
  router.push(crumb.path)
}
</script>

<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item v-for="(c, idx) in crumbs" :key="idx">
      <span v-if="!c.path">{{ c.title }}</span>
      <a v-else href="javascript:void(0)" @click.prevent="to(c)">{{ c.title }}</a>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

