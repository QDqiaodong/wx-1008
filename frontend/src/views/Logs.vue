<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElSelect, ElOption, ElButton, ElMessage, ElTag } from 'element-plus'
import type { AdaptLog, FlightRoute } from '../api'
import { adaptApi, routeApi } from '../api'

const logs = ref<AdaptLog[]>([])
const routes = ref<FlightRoute[]>([])
const selectedRouteId = ref<number>(0)

const loadLogs = async () => {
  logs.value = await adaptApi.logs(selectedRouteId.value || undefined)
}

const loadRoutes = async () => {
  routes.value = await routeApi.list()
}

const handleFilter = () => {
  loadLogs()
}

const handleExport = () => {
  const dataStr = JSON.stringify(logs.value, null, 2)
  const blob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `adapt-logs-${new Date().toISOString().slice(0, 10)}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}

const getOperationTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    BIND: '绑定',
    UNBIND: '解绑',
    REBIND: '重新绑定'
  }
  return map[type] || type
}

const getOperationTypeColor = (type: string): 'success' | 'danger' | 'warning' | 'info' => {
  const map: Record<string, 'success' | 'danger' | 'warning'> = {
    BIND: 'success',
    UNBIND: 'danger',
    REBIND: 'warning'
  }
  return map[type] || 'info'
}

onMounted(() => {
  loadLogs()
  loadRoutes()
})
</script>

<template>
  <div class="bg-white rounded-lg shadow p-6">
    <div class="flex justify-between items-center mb-6">
      <h2 class="text-xl font-semibold">适配调整流水记录</h2>
      <ElButton type="primary" @click="handleExport">导出记录</ElButton>
    </div>

    <div class="bg-gray-50 rounded-lg p-4 mb-6">
      <div class="flex items-center gap-4">
        <span class="text-gray-600">筛选航线:</span>
        <ElSelect v-model="selectedRouteId" placeholder="全部航线" style="width: 300px;" @change="handleFilter">
          <ElOption label="全部航线" value="0" />
          <ElOption v-for="route in routes" :key="route.id" :label="`${route.routeCode} - ${route.routeName}`" :value="route.id" />
        </ElSelect>
      </div>
    </div>

    <ElTable :data="logs" stripe>
      <ElTableColumn prop="id" label="ID" width="80" />
      <ElTableColumn prop="routeCode" label="航线编号" />
      <ElTableColumn prop="anchorCode" label="锚点编号" />
      <ElTableColumn prop="operationType" label="操作类型">
        <template #default="scope">
          <ElTag :type="getOperationTypeColor(scope.row.operationType)">
            {{ getOperationTypeLabel(scope.row.operationType) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="气流强度变化(m/s)">
        <template #default="scope">
          <span v-if="scope.row.beforeWindSpeed !== null">
            {{ scope.row.beforeWindSpeed }} → {{ scope.row.afterWindSpeed }}
          </span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="锚点承重(kg)">
        <template #default="scope">
          <span v-if="scope.row.afterWeight !== null">{{ scope.row.afterWeight }}</span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="reason" label="操作原因" min-width="200" />
      <ElTableColumn prop="operator" label="操作人" />
      <ElTableColumn prop="createTime" label="操作时间" />
    </ElTable>
  </div>
</template>
