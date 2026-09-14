<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElSelect, ElOption } from 'element-plus'
import type { FlightRoute, RouteDTO } from '../api'
import { routeApi } from '../api'

const routes = ref<FlightRoute[]>([])
const groups = ref<string[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number>(0)
const selectedGroup = ref('')

const form = ref<RouteDTO>({
  routeCode: '',
  routeName: '',
  routeGroup: '',
  windSpeed: 0,
  description: ''
})

const getWindLevel = (speed: number): string => {
  if (speed < 3) return '微风'
  if (speed < 6) return '轻风'
  if (speed < 10) return '和风'
  if (speed < 15) return '强风'
  return '疾风'
}

const loadRoutes = async () => {
  routes.value = await routeApi.list()
}

const loadGroups = async () => {
  groups.value = await routeApi.groups()
}

const handleAdd = () => {
  isEdit.value = false
  editId.value = 0
  form.value = {
    routeCode: '',
    routeName: '',
    routeGroup: '',
    windSpeed: 0,
    description: ''
  }
  dialogVisible.value = true
}

const handleEdit = (route: any) => {
  isEdit.value = true
  editId.value = route.id
  form.value = {
    routeCode: route.routeCode,
    routeName: route.routeName,
    routeGroup: route.routeGroup,
    windSpeed: route.windSpeed,
    description: route.description
  }
  dialogVisible.value = true
}

const handleDelete = async (id: number) => {
  await routeApi.delete(id)
  ElMessage.success('删除成功')
  loadRoutes()
}

const handleSubmit = async () => {
  try {
    if (isEdit.value) {
      await routeApi.update(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await routeApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadRoutes()
    loadGroups()
  } catch (error: any) {
    ElMessage.error(error.message)
  }
}

const handleGroupFilter = () => {
  if (selectedGroup.value) {
    routes.value = routes.value.filter(r => r.routeGroup === selectedGroup.value)
  } else {
    loadRoutes()
  }
}

onMounted(() => {
  loadRoutes()
  loadGroups()
})
</script>

<template>
  <div class="bg-white rounded-lg shadow p-6">
    <div class="flex justify-between items-center mb-6">
      <h2 class="text-xl font-semibold">动力伞航线管理</h2>
      <ElButton type="primary" @click="handleAdd">新增航线</ElButton>
    </div>

    <div class="bg-gray-50 rounded-lg p-4 mb-6">
      <div class="flex items-center gap-4">
        <span class="text-gray-600">航线分组:</span>
        <ElSelect v-model="selectedGroup" placeholder="选择分组" style="width: 200px;" @change="handleGroupFilter">
          <ElOption label="全部" value="" />
          <ElOption v-for="group in groups" :key="group" :label="group" :value="group" />
        </ElSelect>
      </div>
    </div>

    <ElTable :data="routes" stripe>
      <ElTableColumn prop="routeCode" label="航线编号" />
      <ElTableColumn prop="routeName" label="航线名称" />
      <ElTableColumn prop="routeGroup" label="航线分组" />
      <ElTableColumn prop="windSpeed" label="气流强度(m/s)" />
      <ElTableColumn prop="windLevel" label="气流等级">
        <template #default="scope">
          <span :class="{
            'text-green-600': scope.row.windLevel === '微风',
            'text-blue-600': scope.row.windLevel === '轻风',
            'text-yellow-600': scope.row.windLevel === '和风',
            'text-orange-600': scope.row.windLevel === '强风',
            'text-red-600': scope.row.windLevel === '疾风'
          }">
            {{ scope.row.windLevel }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="description" label="航线描述" />
      <ElTableColumn prop="status" label="状态">
        <template #default="scope">
          <span :class="scope.row.status === 1 ? 'text-green-600' : 'text-gray-400'">
            {{ scope.row.status === 1 ? '启用' : '停用' }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="createTime" label="创建时间" />
      <ElTableColumn label="操作">
        <template #default="scope">
          <ElButton type="primary" size="small" @click="handleEdit(scope.row)">编辑</ElButton>
          <ElButton type="danger" size="small" @click="handleDelete(scope.row.id)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog :title="isEdit ? '编辑航线' : '新增航线'" v-model="dialogVisible" width="500px">
      <ElForm :model="form" label-width="120px">
        <ElFormItem label="航线编号" required>
          <ElInput v-model="form.routeCode" />
        </ElFormItem>
        <ElFormItem label="航线名称" required>
          <ElInput v-model="form.routeName" />
        </ElFormItem>
        <ElFormItem label="航线分组" required>
          <ElSelect v-model="form.routeGroup" style="width: 100%;">
            <ElOption v-for="group in groups" :key="group" :label="group" :value="group" />
            <ElOption label="新建分组" value="" />
          </ElSelect>
          <ElInput v-model="form.routeGroup" v-if="!form.routeGroup || !groups.includes(form.routeGroup)" placeholder="输入新分组名称" />
        </ElFormItem>
        <ElFormItem label="气流强度(m/s)" required>
          <ElInputNumber v-model="form.windSpeed" :min="0" :max="20" :step="0.5" />
          <span class="ml-2 text-gray-500">{{ getWindLevel(form.windSpeed) }}</span>
        </ElFormItem>
        <ElFormItem label="航线描述">
          <ElInput v-model="form.description" type="textarea" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSubmit">确定</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
