<script setup lang="ts">
import { RouterView } from 'vue-router'
import { ElContainer, ElAside, ElMenu, ElMenuItem, ElHeader } from 'element-plus'

const menuItems = [
  { path: '/anchor', label: '锚点管理' },
  { path: '/route', label: '航线管理' },
  { path: '/adapt', label: '适配校验' },
  { path: '/group-binding', label: '成组配桩预演' },
  { path: '/logs', label: '流水记录' }
]
</script>

<template>
  <ElContainer class="h-screen">
    <ElAside width="200px" class="bg-slate-800 text-white">
      <div class="p-4 border-b border-slate-700">
        <h1 class="text-lg font-semibold text-white">动力伞基地系统</h1>
      </div>
      <ElMenu router class="h-full bg-slate-800 text-white">
        <ElMenuItem v-for="item in menuItems" :key="item.path" :index="item.path">
          {{ item.label }}
        </ElMenuItem>
      </ElMenu>
    </ElAside>
    <ElContainer>
      <ElHeader class="bg-white border-b border-gray-200 flex items-center">
        <h2 class="text-lg font-semibold text-gray-800">{{ $route.name }}</h2>
      </ElHeader>
      <div class="p-6">
        <RouterView />
      </div>
    </ElContainer>
  </ElContainer>
</template>

<style scoped>
.el-menu {
  border-right: none;
}
</style>
