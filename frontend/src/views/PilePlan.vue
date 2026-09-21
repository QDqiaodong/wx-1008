<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ElCard, ElSelect, ElOption, ElButton, ElTable, ElTableColumn, ElTag,
  ElAlert, ElCheckbox, ElMessage, ElEmpty, ElStatistic, ElDivider
} from 'element-plus'
import type { Anchor, FlightRoute, PilePlanResult } from '../api'
import type { TableInstance } from 'element-plus'
import { anchorApi, routeApi, pilePlanApi } from '../api'

const routes = ref<FlightRoute[]>([])
const anchors = ref<Anchor[]>([])
const selectedRouteId = ref<number>(0)
const selectedAnchorIds = ref<number[]>([])
const operator = ref('operator')
const simulateDbFailure = ref(false)
const previewResult = ref<PilePlanResult | null>(null)
const submitResult = ref<PilePlanResult | null>(null)
const previewLoading = ref(false)
const submitLoading = ref(false)
const anchorTableRef = ref<TableInstance | null>(null)

const enabledAnchors = computed(() => anchors.value.filter(a => a.status === 1))

const LEVEL_TABLE = [
  { level: '微风', range: '0 - 3', weight: 500 },
  { level: '轻风', range: '3 - 6', weight: 800 },
  { level: '和风', range: '6 - 10', weight: 1200 },
  { level: '强风', range: '10 - 15', weight: 1800 },
  { level: '疾风', range: '15 - 20', weight: 2500 }
]

const selectedRoute = computed(() => routes.value.find(r => r.id === selectedRouteId.value))
const selectedLevelMinWeight = computed(() =>
  LEVEL_TABLE.find(x => x.level === selectedRoute.value?.windLevel)?.weight)

const levelTagType = (level?: string) => {
  const map: Record<string, string> = {
    微风: 'success', 轻风: 'info', 和风: 'warning', 强风: 'danger', 疾风: 'danger'
  }
  return (level && map[level]) || 'info'
}

const resetResults = () => {
  previewResult.value = null
  submitResult.value = null
}

const onRouteChange = () => {
  anchorTableRef.value?.clearSelection()
  selectedAnchorIds.value = []
  resetResults()
}

const onSelectionChange = (rows: Anchor[]) => {
  selectedAnchorIds.value = rows.map(r => r.id)
  // 勾选变化后，旧的预演结果已失效，清掉，强制重新体检
  previewResult.value = null
  submitResult.value = null
}

const canPreview = computed(() => selectedRouteId.value && selectedAnchorIds.value.length > 0)

const handlePreview = async () => {
  if (!canPreview.value) return
  previewLoading.value = true
  submitResult.value = null
  try {
    previewResult.value = await pilePlanApi.preview({
      routeId: selectedRouteId.value,
      anchorIds: selectedAnchorIds.value,
      operator: operator.value
    })
  } catch (e: any) {
    ElMessage.error(e?.message || '预演失败')
  } finally {
    previewLoading.value = false
  }
}

const handleSubmit = async () => {
  if (!canPreview.value) return
  if (!previewResult.value) {
    ElMessage.warning('请先进行预演体检，确认无误后再提交')
    return
  }
  if (!previewResult.value.valid) {
    ElMessage.error('当前预演未通过（存在不合格/冲突锚点或总承重预算不足），按整组回退策略无法提交')
    return
  }
  submitLoading.value = true
  try {
    submitResult.value = await pilePlanApi.submit({
      routeId: selectedRouteId.value,
      anchorIds: selectedAnchorIds.value,
      operator: operator.value,
      simulateDbFailure: simulateDbFailure.value
    })
    if (submitResult.value.valid) {
      ElMessage.success('整组配桩提交成功，已落库并同步缓存')
      anchorTableRef.value?.clearSelection()
      selectedAnchorIds.value = []
      previewResult.value = null
    } else {
      ElMessage.error('提交被整组回退，一条都未落库，详见下方结果与适配流水')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitLoading.value = false
  }
}

onMounted(async () => {
  routes.value = await routeApi.list()
  anchors.value = await anchorApi.list()
})
</script>

<template>
  <div class="space-y-5">
    <!-- 策略与规则说明 -->
    <ElCard shadow="never">
      <template #header>
        <span class="font-semibold text-base">航线成组配桩 · 预演与提交</span>
      </template>
      <ElAlert type="warning" :closable="false" show-icon class="mb-3">
        <template #title>
          <span class="font-medium">提交策略（整组回退 · 全有或全无）：</span>
          一次性勾选的这一套锚点，只要有 <b>任意一个</b> 体检不合格 / 被别的启用航线占用，或整套总承重预算不足，
          提交时 <b>整套一起回退、一条都不落库</b>，系统不会先落合格的、再让你二次确认。
          预演与提交用的是后端<b>同一套判定</b>，预演说不行的，提交同样会被拦下。
        </template>
      </ElAlert>
      <div class="text-sm text-gray-600 leading-relaxed">
        体检同时守住三件<b>彼此独立</b>的事，缺一件都不算配上：
        <ElTag size="small" class="mx-1">① 气流区间真正包住航线气流（下限≤航线≤上限，下限上限都查）</ElTag>
        <ElTag size="small" type="warning" class="mx-1">② 锚点最大承重 ≥ 航线气流等级查表的最低承重</ElTag>
        <ElTag size="small" type="danger" class="mx-1">③ 单锚点唯一占用 + 整套总承重预算</ElTag>
      </div>
      <ElDivider class="my-3" />
      <div class="text-sm text-gray-600">
        <b>承重等级表：</b>
        <ElTag v-for="row in LEVEL_TABLE" :key="row.level" size="small" class="mr-2 mb-1"
               :type="(levelTagType(row.level) as any)">
          {{ row.level }}（{{ row.range }} m/s）最低 {{ row.weight }}kg
        </ElTag>
      </div>
    </ElCard>

    <!-- 第一步：选航线 + 勾选锚点 -->
    <ElCard shadow="never">
      <template #header><span class="font-semibold">第一步：选择航线并一次性勾选锚点</span></template>
      <div class="flex flex-wrap items-center gap-4 mb-4">
        <span class="text-gray-600">航线：</span>
        <ElSelect v-model="selectedRouteId" placeholder="请先选择一条航线" style="width: 320px"
                  @change="onRouteChange">
          <ElOption v-for="r in routes" :key="r.id"
                    :label="`${r.routeCode} - ${r.routeName}（${r.windSpeed}m/s ${r.windLevel}）`" :value="r.id" />
        </ElSelect>
        <ElTag v-if="selectedRoute" :type="(levelTagType(selectedRoute.windLevel) as any)" size="large">
          当前气流 {{ selectedRoute.windSpeed }} m/s（{{ selectedRoute.windLevel }}）
        </ElTag>
        <span class="text-gray-600 ml-2">操作人：</span>
        <ElSelect v-model="operator" style="width: 140px">
          <ElOption label="运营A" value="运营A" />
          <ElOption label="运营B" value="运营B" />
          <ElOption label="operator" value="operator" />
        </ElSelect>
      </div>

      <ElAlert v-if="selectedRoute" type="info" :closable="false" class="mb-3">
        <template #title>
          该航线为<b>{{ selectedRoute.windLevel }}</b>，单锚点最低承重 <b>
          {{ selectedLevelMinWeight }}</b>kg；
          锚点适配区间必须满足 下限 ≤ {{ selectedRoute.windSpeed }} ≤ 上限。
        </template>
      </ElAlert>
      <ElTable :data="enabledAnchors" border size="small" max-height="360"
               @selection-change="onSelectionChange" ref="anchorTableRef" row-key="id">
        <ElTableColumn type="selection" width="48" />
        <ElTableColumn prop="anchorCode" label="锚点编号" width="150" />
        <ElTableColumn prop="maxWeight" label="最大承重(kg)" width="120" />
        <ElTableColumn label="适配气流区间(m/s)" width="160">
          <template #default="scope">
            {{ scope.row.minWindSpeed }} ~ {{ scope.row.maxWindSpeed }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="locationDesc" label="位置描述" min-width="180" />
      </ElTable>

      <div class="mt-4 flex flex-wrap items-center gap-3">
        <ElButton type="primary" :loading="previewLoading" :disabled="!canPreview" @click="handlePreview">
          预演体检（不落库）
        </ElButton>
        <ElButton type="success" :loading="submitLoading" :disabled="!canPreview" @click="handleSubmit">
          确认无误 · 整组提交
        </ElButton>
        <ElCheckbox v-model="simulateDbFailure">
          验收用：提交时人为制造一次落库失败（验证缓存/库一致回滚）
        </ElCheckbox>
        <span class="text-gray-500 text-sm">已勾选 {{ selectedAnchorIds.length }} 个锚点</span>
      </div>
    </ElCard>

    <!-- 第二步：预演/提交结果 -->
    <ElCard v-if="previewResult || submitResult" shadow="never">
      <template #header>
        <span class="font-semibold">第二步：体检 / 提交结果（预演与提交同一套判定）</span>
      </template>

      <ElAlert
        :type="(submitResult ?? previewResult)!.valid ? 'success' : 'error'"
        :closable="false" show-icon class="mb-4">
        <template #title>
          <div class="whitespace-pre-line leading-6">{{ (submitResult ?? previewResult)!.summary }}</div>
        </template>
      </ElAlert>

      <div class="grid grid-cols-5 gap-4 mb-4">
        <ElStatistic title="勾选锚点数" :value="(submitResult ?? previewResult)!.totalCount" />
        <ElStatistic title="合格" :value="(submitResult ?? previewResult)!.passedCount" />
        <ElStatistic title="不合格/冲突" :value="(submitResult ?? previewResult)!.failedCount" />
        <ElStatistic title="合格总承重(kg)" :value="(submitResult ?? previewResult)!.actualTotalWeight" />
        <ElStatistic title="总承重预算(kg)" :value="(submitResult ?? previewResult)!.requiredTotalWeight" />
      </div>

      <ElAlert v-if="!(submitResult ?? previewResult)!.budgetEnough" type="error" :closable="false" class="mb-3">
        <template #title>{{ (submitResult ?? previewResult)!.budgetReason }}</template>
      </ElAlert>

      <ElTable :data="(submitResult ?? previewResult)!.items" border size="small">
        <ElTableColumn prop="anchorCode" label="锚点编号" width="140" />
        <ElTableColumn prop="maxWeight" label="承重(kg)" width="90" />
        <ElTableColumn label="适配区间" width="120">
          <template #default="scope">
            {{ scope.row.minWindSpeed }}~{{ scope.row.maxWindSpeed }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="结论" width="110">
          <template #default="scope">
            <ElTag v-if="scope.row.passed" type="success">合格</ElTag>
            <ElTag v-else type="danger">不通过</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="未通过的具体判定原因（逐条）" min-width="360">
          <template #default="scope">
            <div v-if="scope.row.passed" class="text-green-600">
              气流区间包住、承重达标、未被占用
            </div>
            <div v-else class="space-y-1">
              <div v-for="(r, i) in scope.row.failReasons" :key="i" class="text-red-600 text-sm">
                · {{ r }}
              </div>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElCard v-else shadow="never">
      <ElEmpty description="先选航线、勾选多个锚点，然后点“预演体检”" />
    </ElCard>
  </div>
</template>
