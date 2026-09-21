<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElTable, ElTableColumn, ElButton, ElSelect, ElOption, ElMessage, ElCard, ElTag,
  ElAlert, ElEmpty, ElDialog, ElDescriptions, ElDescriptionsItem
} from 'element-plus'
import type { Anchor, FlightRoute, GroupRehearseResult, GroupSubmitResult } from '../api'
import { anchorApi, routeApi, groupBindingApi, adaptApi } from '../api'

const routes = ref<FlightRoute[]>([])
const anchors = ref<Anchor[]>([])
const selectedRouteId = ref<number>(0)
const checkedAnchorIds = ref<number[]>([])
const rules = ref<Record<string, number>>({})

const rehearsal = ref<GroupRehearseResult | null>(null)
const submitting = ref(false)
const rehearsing = ref(false)
const submitDialogVisible = ref(false)
const lastSubmit = ref<GroupSubmitResult | null>(null)

const selectedRoute = computed(() => routes.value.find(r => r.id === selectedRouteId.value))

// 已绑定到当前航线的锚点不重复出现在候选中
const boundIds = ref<Set<number>>(new Set())
const candidateAnchors = computed(() =>
  anchors.value.filter(a => a.status === 1 && !boundIds.value.has(a.id))
)

const selectedAnchors = computed(() =>
  candidateAnchors.value.filter(a => checkedAnchorIds.value.includes(a.id))
)

const levelTagType = (level?: string) =>
  level === '微风' ? 'success'
    : level === '轻风' ? 'info'
      : level === '和风' ? 'warning'
        : 'danger'

const CHECK_LABEL: Record<string, string> = {
  ANCHOR_DISABLED: '锚点停用',
  ANCHOR_NOT_FOUND: '锚点不存在',
  WIND_MIN: '气流下限不匹配',
  WIND_MAX: '气流上限不匹配',
  WEIGHT: '承重低于航线风级要求',
  WEIGHT_LEVEL: '承重达不到所适配风级的等级要求',
  OCCUPIED: '唯一占用冲突',
  ALREADY_BOUND: '已在本航线'
}

const loadBound = async () => {
  boundIds.value = new Set()
  if (!selectedRouteId.value) return
  try {
    const bound = await adaptApi.bound(selectedRouteId.value)
    boundIds.value = new Set(bound.filter(b => b.status === 1).map(b => b.anchorId))
    checkedAnchorIds.value = checkedAnchorIds.value.filter(id => !boundIds.value.has(id))
  } catch { /* ignore */ }
}

const onRouteChange = async () => {
  rehearsal.value = null
  lastSubmit.value = null
  checkedAnchorIds.value = []
  await loadBound()
}

const onSelectionChange = (rows: Anchor[]) => {
  checkedAnchorIds.value = rows.map(r => r.id)
  rehearsal.value = null // 选择变了，旧预演作废，必须重新体检，避免拿旧结论提交
}

const doRehearse = async () => {
  if (!selectedRouteId.value) {
    ElMessage.warning('请先选择一条航线')
    return
  }
  if (checkedAnchorIds.value.length === 0) {
    ElMessage.warning('请至少勾选一个地面锚点')
    return
  }
  rehearsing.value = true
  try {
    rehearsal.value = await groupBindingApi.rehearse(selectedRouteId.value, [...checkedAnchorIds.value])
    if (rehearsal.value.groupValid) {
      ElMessage.success('预演体检通过：全部锚点合格，可提交整套方案')
    } else {
      ElMessage.warning(`预演发现 ${rehearsal.value.rejectedCount} 个锚点不合格，按整套策略将一条不落`)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '预演失败')
  } finally {
    rehearsing.value = false
  }
}

const canSubmit = computed(() =>
  !!rehearsal.value &&
  rehearsal.value.groupValid &&
  rehearsal.value.routeId === selectedRouteId.value &&
  checkedAnchorIds.value.length === rehearsal.value.totalCount &&
  !submitting.value
)

const openSubmitDialog = () => {
  if (!canSubmit.value) return
  submitDialogVisible.value = true
}

const confirmSubmit = async () => {
  submitting.value = true
  try {
    const res = await groupBindingApi.submit(selectedRouteId.value, [...checkedAnchorIds.value])
    lastSubmit.value = res
    submitDialogVisible.value = false
    if (res.committed) {
      ElMessage.success(res.message)
      checkedAnchorIds.value = []
      rehearsal.value = null
      await loadBound()
    } else {
      ElMessage.error(res.message)
      if (res.rehearsal) rehearsal.value = res.rehearsal // 提交环节若因并发变化而拒绝，回填最新逐条结论
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const [rs, ans, rl] = await Promise.all([routeApi.list(), anchorApi.list(), groupBindingApi.rules()])
    routes.value = rs
    anchors.value = ans
    rules.value = rl
  } catch (e: any) {
    ElMessage.error(e?.message || '数据加载失败')
  }
})
</script>

<template>
  <div class="space-y-5">
    <!-- 顶部：航线选择 + 规则表 -->
    <ElCard>
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-lg font-semibold">航线成组配桩 · 预演与提交</span>
          <ElTag type="info">先预演体检，确认无误后才允许提交落库</ElTag>
        </div>
      </template>

      <div class="flex flex-wrap items-center gap-4">
        <span class="text-gray-600">选择航线：</span>
        <ElSelect v-model="selectedRouteId" placeholder="请选择航线" style="width: 320px" @change="onRouteChange">
          <ElOption
            v-for="r in routes.filter(x => x.status === 1)"
            :key="r.id"
            :label="`${r.routeCode} - ${r.routeName}`"
            :value="r.id"
          />
        </ElSelect>
        <ElTag v-if="selectedRoute" :type="levelTagType(selectedRoute.windLevel)">
          当前气流 {{ selectedRoute.windSpeed }} m/s（{{ selectedRoute.windLevel }}）
        </ElTag>
        <div class="text-sm text-gray-500">
          风级最低承重要求：
          <span v-for="(w, lv) in rules" :key="lv" class="mr-3">
            <ElTag size="small" :type="levelTagType(lv as string)" class="mr-1">{{ lv }}</ElTag>{{ w }}kg
          </span>
        </div>
      </div>
    </ElCard>

    <!-- 策略后果，讲清楚 -->
    <ElAlert type="warning" :closable="false" show-icon>
      <template #title>
        提交策略（预演与提交完全一致）：<b>整套方案 · 全成或全回退</b>
      </template>
      <div class="text-sm leading-6">
        一次性勾选多个锚点组成一套配桩方案。系统对每个锚点同时独立校验三件事——
        <b>① 气流区间真正包住</b>（下限≤航线气流、上限≥航线气流，下限也会查）、
        <b>② 承重达标</b>（既要满足航线当前风级门槛，也要达到锚点自身所适配风级的等级承重）、
        <b>③ 单锚点唯一占用</b>（同一锚点同一时间只能服役一条启用航线）。
        <br />
        后果：勾选的锚点中<b class="text-red-600">只要有一个不合格，提交时整套一起回退，一条绑定都不落库</b>，
        并在下方列出每个不合格锚点的具体原因；只有全部合格且整套承重预算达标，才会一次性整体落库。
        并发下若锚点刚被其他运营占用，提交会给出明确"占用冲突"，同样整套不落。
      </div>
    </ElAlert>

    <template v-if="selectedRoute">
      <!-- 候选锚点多选 -->
      <ElCard>
        <template #header>
          <div class="flex items-center justify-between">
            <span>勾选地面锚点组成方案（已选 {{ checkedAnchorIds.length }} 个）</span>
            <div class="space-x-2">
              <ElButton type="primary" plain :loading="rehearsing" @click="doRehearse">
                预演体检
              </ElButton>
              <ElButton type="success" :disabled="!canSubmit" @click="openSubmitDialog">
                提交整套方案
              </ElButton>
            </div>
          </div>
        </template>

        <ElTable
          :data="candidateAnchors"
          size="small"
          border
          row-key="id"
          @selection-change="onSelectionChange"
        >
          <ElTableColumn type="selection" width="46" reserve-selection />
          <ElTableColumn prop="anchorCode" label="锚点编号" width="150" />
          <ElTableColumn prop="locationDesc" label="说明" min-width="180" />
          <ElTableColumn prop="maxWeight" label="最大承重(kg)" width="110" />
          <ElTableColumn label="适配气流区间(m/s)" width="170">
            <template #default="{ row }">
              {{ row.minWindSpeed }} ~ {{ row.maxWindSpeed }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="对当前航线" min-width="220">
            <template #default>
              <span class="text-xs text-gray-500">
                需承重≥{{ rehearsal?.requiredMinWeight ?? '—' }}
                ，区间需包住 {{ selectedRoute.windSpeed }}
              </span>
            </template>
          </ElTableColumn>
        </ElTable>
        <ElEmpty v-if="candidateAnchors.length === 0" description="暂无可勾选锚点（可能全部已绑定或停用）" />
      </ElCard>

      <!-- 预演结果 -->
      <ElCard v-if="rehearsal">
        <template #header>
          <div class="flex items-center justify-between">
            <span>预演体检结果</span>
            <ElTag :type="rehearsal.groupValid ? 'success' : 'danger'" size="large">
              {{ rehearsal.groupValid ? '整套合格，可提交' : `不合格 ${rehearsal.rejectedCount}/${rehearsal.totalCount}，提交将整套回退` }}
            </ElTag>
          </div>
        </template>

        <ElDescriptions :column="3" border size="small" class="mb-4">
          <ElDescriptionsItem label="航线">
            {{ rehearsal.routeCode }} · {{ rehearsal.windLevel }}（{{ rehearsal.routeWindSpeed }}m/s）
          </ElDescriptionsItem>
          <ElDescriptionsItem label="单锚点承重门槛">{{ rehearsal.requiredMinWeight }} kg</ElDescriptionsItem>
          <ElDescriptionsItem label="合格/总数">
            {{ rehearsal.eligibleCount }} / {{ rehearsal.totalCount }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="整套承重预算">
            合格锚点合计 {{ rehearsal.eligibleTotalWeight }}kg / 预算门槛
            {{ rehearsal.requiredMinWeight }}×{{ rehearsal.totalCount }}={{ rehearsal.requiredTotalWeight }}kg
          </ElDescriptionsItem>
          <ElDescriptionsItem label="预算是否达标">
            <ElTag :type="rehearsal.totalWeightBudgetOk ? 'success' : 'danger'">
              {{ rehearsal.totalWeightBudgetOk ? '达标' : '不达标' }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="一致策略">{{ rehearsal.policy === 'ALL_OR_NOTHING' ? '全成或全回退' : rehearsal.policy }}</ElDescriptionsItem>
        </ElDescriptions>

        <ElTable :data="rehearsal.anchorResults" size="small" border>
          <ElTableColumn prop="anchorCode" label="锚点编号" width="150" />
          <ElTableColumn prop="maxWeight" label="承重(kg)" width="90" />
          <ElTableColumn label="区间(m/s)" width="130">
            <template #default="{ row }">{{ row.minWindSpeed }}~{{ row.maxWindSpeed }}</template>
          </ElTableColumn>
          <ElTableColumn label="结论" width="110">
            <template #default="{ row }">
              <ElTag :type="row.eligible ? 'success' : 'danger'">
                {{ row.eligible ? '合格' : '不合格' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="未过判定" width="200">
            <template #default="{ row }">
              <ElTag
                v-for="c in row.failedChecks"
                :key="c"
                type="danger"
                size="small"
                class="mr-1 mb-1"
              >
                {{ CHECK_LABEL[c] || c }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="具体原因（逐条）" min-width="360">
            <template #default="{ row }">
              <div
                v-for="(msg, i) in row.reasons"
                :key="i"
                :class="row.eligible ? 'text-green-700' : 'text-red-600'"
                class="text-xs leading-5"
              >· {{ msg }}</div>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElCard>

      <!-- 最近一次提交回执 -->
      <ElCard v-if="lastSubmit">
        <template #header>
          <div class="flex items-center justify-between">
            <span>最近一次提交回执</span>
            <ElTag :type="lastSubmit.committed ? 'success' : 'danger'">
              {{ lastSubmit.committed ? `已整体落库 ${lastSubmit.submittedCount} 个` : '整套回退，0 条落库' }}
            </ElTag>
          </div>
        </template>
        <p :class="lastSubmit.committed ? 'text-green-700' : 'text-red-600'">{{ lastSubmit.message }}</p>
      </ElCard>
    </template>

    <ElCard v-else>
      <ElEmpty description="请先选择一条航线" />
    </ElCard>

    <!-- 提交确认 -->
    <ElDialog v-model="submitDialogVisible" title="确认提交整套配桩方案" width="560px">
      <div v-if="rehearsal" class="space-y-3 text-sm leading-6">
        <p>航线：<b>{{ rehearsal.routeCode }}</b>（{{ rehearsal.windLevel }} {{ rehearsal.routeWindSpeed }}m/s）</p>
        <p>将一次性整体绑定 <b>{{ rehearsal.totalCount }}</b> 个锚点：
          <ElTag v-for="a in selectedAnchors" :key="a.id" size="small" class="mr-1">{{ a.anchorCode }}</ElTag>
        </p>
        <ElAlert type="warning" :closable="false" :title="rehearsal.policyNotice" />
        <p class="text-gray-500">提交会再次用同一套判定在锁内校验；若此刻锚点被他人占用，将整套拒绝并提示冲突。</p>
      </div>
      <template #footer>
        <ElButton @click="submitDialogVisible = false">取消</ElButton>
        <ElButton type="success" :loading="submitting" @click="confirmSubmit">确认整体提交</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
