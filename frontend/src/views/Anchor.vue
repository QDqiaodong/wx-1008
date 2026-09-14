<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElSlider, ElMessage, ElSelect, ElOption } from 'element-plus'
import type { Anchor, AnchorDTO } from '../api'
import { anchorApi } from '../api'

const anchors = ref<Anchor[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number>(0)

const form = ref<AnchorDTO>({
  anchorCode: '',
  maxWeight: 0,
  minWindSpeed: 0,
  maxWindSpeed: 0,
  locationDesc: ''
})

const windRange = ref([0, 20])

const windLevelOptions = [
  { label: '微风 (0-3)', value: [0, 3] },
  { label: '轻风 (3-6)', value: [3, 6] },
  { label: '和风 (6-10)', value: [6, 10] },
  { label: '强风 (10-15)', value: [10, 15] },
  { label: '疾风 (15-20)', value: [15, 20] }
]

const loadAnchors = async () => {
  anchors.value = await anchorApi.list()
}

const handleAdd = () => {
  isEdit.value = false
  editId.value = 0
  form.value = {
    anchorCode: '',
    maxWeight: 0,
    minWindSpeed: 0,
    maxWindSpeed: 0,
    locationDesc: ''
  }
  dialogVisible.value = true
}

const handleEdit = (anchor: any) => {
  isEdit.value = true
  editId.value = anchor.id
  form.value = {
    anchorCode: anchor.anchorCode,
    maxWeight: anchor.maxWeight,
    minWindSpeed: anchor.minWindSpeed,
    maxWindSpeed: anchor.maxWindSpeed,
    locationDesc: anchor.locationDesc
  }
  dialogVisible.value = true
}

const handleDelete = async (id: number) => {
  await anchorApi.delete(id)
  ElMessage.success('删除成功')
  loadAnchors()
}

const handleSubmit = async () => {
  try {
    if (isEdit.value) {
      await anchorApi.update(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await anchorApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadAnchors()
  } catch (error: any) {
    ElMessage.error(error.message)
  }
}

const handleFilter = async () => {
  anchors.value = await anchorApi.filter(windRange.value[0], windRange.value[1])
}

const handleReset = () => {
  windRange.value = [0, 20]
  loadAnchors()
}

onMounted(loadAnchors)
</script>

<template>
  <div class="bg-white rounded-lg shadow p-6">
    <div class="flex justify-between items-center mb-6">
      <h2 class="text-xl font-semibold">地面固定锚点管理</h2>
      <ElButton type="primary" @click="handleAdd">新增锚点</ElButton>
    </div>

    <div class="bg-gray-50 rounded-lg p-4 mb-6">
      <div class="flex items-center gap-4">
        <span class="text-gray-600">气流区间筛选:</span>
        <ElSelect placeholder="选择气流等级" @change="(val: [number, number]) => { windRange = val; handleFilter() }" style="width: 160px;">
          <ElOption v-for="opt in windLevelOptions" :key="opt.label" :label="opt.label" :value="opt.value" />
        </ElSelect>
        <span class="text-gray-600">自定义区间:</span>
        <ElSlider v-model="windRange" :min="0" :max="20" :step="0.5" style="flex: 1;" />
        <span class="text-gray-800 font-medium">{{ windRange[0] }} - {{ windRange[1] }} m/s</span>
        <ElButton @click="handleFilter">筛选</ElButton>
        <ElButton @click="handleReset">重置</ElButton>
      </div>
    </div>

    <ElTable :data="anchors" stripe>
      <ElTableColumn prop="anchorCode" label="锚点编号" />
      <ElTableColumn prop="maxWeight" label="最大承重(kg)" />
      <ElTableColumn label="适配气流区间(m/s)">
        <template #default="scope">
          {{ scope.row.minWindSpeed }} - {{ scope.row.maxWindSpeed }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="locationDesc" label="位置描述" />
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

    <ElDialog :title="isEdit ? '编辑锚点' : '新增锚点'" v-model="dialogVisible" width="500px">
      <ElForm :model="form" label-width="120px">
        <ElFormItem label="锚点编号" required>
          <ElInput v-model="form.anchorCode" />
        </ElFormItem>
        <ElFormItem label="最大承重(kg)" required>
          <ElInputNumber v-model="form.maxWeight" :min="0" :step="100" />
        </ElFormItem>
        <ElFormItem label="适配气流下限(m/s)" required>
          <ElInputNumber v-model="form.minWindSpeed" :min="0" :max="20" :step="0.5" />
        </ElFormItem>
        <ElFormItem label="适配气流上限(m/s)" required>
          <ElInputNumber v-model="form.maxWindSpeed" :min="0" :max="20" :step="0.5" />
        </ElFormItem>
        <ElFormItem label="位置描述">
          <ElInput v-model="form.locationDesc" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSubmit">确定</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
