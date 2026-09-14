<script setup lang="ts">import { ref, computed, onMounted } from 'vue';
import { ElTable, ElTableColumn, ElButton, ElSelect, ElOption, ElMessage, ElCard, ElTag } from 'element-plus';
import type { Anchor, FlightRoute, RouteAnchor } from '../api';
import { anchorApi, routeApi, adaptApi } from '../api';
const routes = ref<FlightRoute[]>([]);
const anchors = ref<Anchor[]>([]);
const selectedRouteId = ref<number>(0);
const boundAnchors = ref<RouteAnchor[]>([]);
const selectedRoute = computed(() => routes.value.find(r => r.id === selectedRouteId.value));
const availableAnchors = computed(() => {
 if (!selectedRoute.value)
 return [];
 const boundAnchorIds = boundAnchors.value.map(ba => ba.anchorId);
 return anchors.value.filter(a => a.status === 1 && !boundAnchorIds.includes(a.id));
});
const adaptableAnchors = computed(() => {
 if (!selectedRoute.value)
 return [];
 return availableAnchors.value.filter(a => a.maxWindSpeed >= selectedRoute.value!.windSpeed);
});
const nonAdaptableAnchors = computed(() => {
 if (!selectedRoute.value)
 return [];
 return availableAnchors.value.filter(a => a.maxWindSpeed < selectedRoute.value!.windSpeed);
});
const loadRoutes = async () => {
 routes.value = await routeApi.list();
};
const loadAnchors = async () => {
 anchors.value = await anchorApi.list();
};
const loadBoundAnchors = async () => {
 if (selectedRouteId.value) {
 boundAnchors.value = await adaptApi.bound(selectedRouteId.value);
 }
};
const handleRouteChange = () => {
 loadBoundAnchors();
};
const getAnchorById = (id: number) => anchors.value.find(a => a.id === id);
const handleBind = async (anchorId: number) => {
 try {
 const result = await adaptApi.bind(selectedRouteId.value, anchorId);
 if (result.valid) {
 ElMessage.success(`绑定成功: ${result.reason}`);
 loadBoundAnchors();
 }
 else {
 ElMessage.error(`绑定失败: ${result.reason}`);
 }
 }
 catch (error: any) {
 ElMessage.error(error.message);
 }
};
const handleUnbind = async (anchorId: number) => {
 try {
 const result = await adaptApi.unbind(selectedRouteId.value, anchorId);
 if (result.valid) {
 ElMessage.success(`解绑成功`);
 loadBoundAnchors();
 }
 else {
 ElMessage.error(`解绑失败: ${result.reason}`);
 }
 }
 catch (error: any) {
 ElMessage.error(error.message);
 }
};
const handleRecheck = async () => {
 try {
 const result = await adaptApi.recheck(selectedRouteId.value);
 ElMessage.success(result.reason);
 loadBoundAnchors();
 }
 catch (error: any) {
 ElMessage.error(error.message);
 }
};
onMounted(() => {
 loadRoutes();
 loadAnchors();
});
</script>

<template>
  <div class="space-y-6">
    <div class="bg-white rounded-lg shadow p-6">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-xl font-semibold">适配校验</h2>
        <ElButton type="warning" @click="handleRecheck" :disabled="!selectedRouteId">重新校验</ElButton>
      </div>
      
      <div class="flex items-center gap-4">
        <span class="text-gray-600">选择航线:</span>
        <ElSelect v-model="selectedRouteId" placeholder="请选择航线" style="width: 300px;" @change="handleRouteChange">
          <ElOption v-for="route in routes" :key="route.id" :label="`${route.routeCode} - ${route.routeName}`" :value="route.id" />
        </ElSelect>
        
        <div v-if="selectedRoute" class="ml-4">
          <ElTag :type="selectedRoute.windLevel === '微风' ? 'success' : selectedRoute.windLevel === '轻风' ? 'info' : selectedRoute.windLevel === '和风' ? 'warning' : 'danger'">
            当前气流: {{ selectedRoute.windSpeed }} m/s ({{ selectedRoute.windLevel }})
          </ElTag>
        </div>
      </div>
    </div>

    <div v-if="selectedRoute" class="grid grid-cols-3 gap-6">
      <ElCard title="已绑定锚点" :body-style="{ padding: '12px' }">
        <div v-if="boundAnchors.length === 0" class="text-gray-400 text-center py-8">暂无绑定锚点</div>
        <ElTable v-else :data="boundAnchors" size="small" border>
          <ElTableColumn label="锚点编号">
            <template #default="scope">
              {{ getAnchorById(scope.row.anchorId)?.anchorCode }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="最大承重(kg)">
            <template #default="scope">
              {{ getAnchorById(scope.row.anchorId)?.maxWeight }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="适配上限(m/s)">
            <template #default="scope">
              {{ getAnchorById(scope.row.anchorId)?.maxWindSpeed }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作">
            <template #default="scope">
              <ElButton type="danger" size="small" @click="handleUnbind(scope.row.anchorId)">解绑</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElCard>

      <ElCard title="可适配锚点" :body-style="{ padding: '12px' }">
        <div v-if="adaptableAnchors.length === 0" class="text-gray-400 text-center py-8">无适配锚点</div>
        <ElTable v-else :data="adaptableAnchors" size="small" border>
          <ElTableColumn prop="anchorCode" label="锚点编号" />
          <ElTableColumn prop="maxWeight" label="最大承重(kg)" />
          <ElTableColumn prop="maxWindSpeed" label="适配上限(m/s)" />
          <ElTableColumn label="操作">
            <template #default="scope">
              <ElButton type="success" size="small" @click="handleBind(scope.row.id)">绑定</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElCard>

      <ElCard title="不可适配锚点" :body-style="{ padding: '12px' }">
        <div v-if="nonAdaptableAnchors.length === 0" class="text-gray-400 text-center py-8">无不适配锚点</div>
        <ElTable v-else :data="nonAdaptableAnchors" size="small" border>
          <ElTableColumn prop="anchorCode" label="锚点编号" />
          <ElTableColumn prop="maxWeight" label="最大承重(kg)" />
          <ElTableColumn prop="maxWindSpeed" label="适配上限(m/s)" />
          <ElTableColumn label="原因">
            <template #default>
              <span class="text-red-500 text-xs">上限不足</span>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElCard>
    </div>

    <div v-else class="bg-white rounded-lg shadow p-6">
      <div class="text-gray-400 text-center py-12">请先选择航线</div>
    </div>
  </div>
</template>
