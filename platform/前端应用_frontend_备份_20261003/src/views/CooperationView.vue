<script setup lang="ts">
import { Coins, Plus, Sprout, Trash2, X } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  createDividend,
  createShare,
  deleteCooperationShare,
  listVillages,
} from '@/api/modules/cooperation'
import { useSessionStore } from '@/stores/session'
import type { VillageSummary } from '@/types/api'

const session = useSessionStore()
const villages = ref<VillageSummary[]>([])
const loading = ref(false)
const errorMessage = ref('')
const panelOpen = ref(false)
const formMode = ref<'share' | 'dividend'>('share')
const submitting = ref(false)
const formMessage = ref('')
const formError = ref('')
const tableMessage = ref('')
const tableError = ref('')
const deletingShareId = ref<number | null>(null)
const activeVillageId = ref<number | null>(null)
const shareForm = ref({
  villageName: '示范村一',
  projectName: '智慧温室示范项目',
  shareRatioPercent: 15,
  effectiveDate: toLocalDate(new Date()),
  status: 'ACTIVE',
})
const dividendForm = ref({
  shareId: 0,
  periodName: '2026 Q4',
  amount: 10000,
  status: 'PENDING',
})
const canWrite = computed(
  () => session.roleCode === 'ADMIN' || session.roleCode === 'COOPERATIVE',
)

const confirmedCount = computed(
  () => villages.value.filter((item) => item.status === 'CONFIRMED').length,
)

const pendingCount = computed(
  () => villages.value.filter((item) => item.status !== 'CONFIRMED').length,
)

const cooperationStats = computed(() => {
  const totalLand = villages.value.reduce(
    (sum, item) => sum + (item.landArea ?? 0),
    0,
  )
  const totalDividend = villages.value.reduce(
    (sum, item) => sum + (item.dividendAmount ?? 0),
    0,
  )
  const averageRatio =
    villages.value.length === 0
      ? 0
      : villages.value.reduce((sum, item) => sum + item.shareRatio, 0)
        / villages.value.length
  return [
    {
      label: '合作村庄',
      value: String(villages.value.length),
      unit: '个',
    },
    {
      label: '入股土地合计',
      value: totalLand.toLocaleString('zh-CN'),
      unit: '亩',
    },
    {
      label: '本期分红合计',
      value: totalDividend.toLocaleString('zh-CN'),
      unit: '元',
    },
    {
      label: '平均股权比例',
      value: (averageRatio * 100).toFixed(2),
      unit: '%',
    },
  ]
})

const maxDividend = computed(() =>
  villages.value.reduce(
    (max, item) => Math.max(max, item.dividendAmount ?? 0),
    0,
  ),
)

const dividendTotal = computed(() =>
  villages.value.reduce(
    (sum, item) => sum + Math.max(0, item.dividendAmount ?? 0),
    0,
  ),
)

const equityTotal = computed(() =>
  villages.value.reduce(
    (sum, item) => sum + Math.max(0, item.shareRatio ?? 0),
    0,
  ),
)

const donutCircumference = 2 * Math.PI * 66
const donutColors = [
  '#267a58',
  '#c08a2e',
  '#3b78a8',
  '#8a6fae',
  '#c8655d',
  '#4d8f86',
]

const villageColorMap = computed<Record<number, string>>(() =>
  Object.fromEntries(
    villages.value.map((village, index) => [
      village.shareId,
      donutColors[index % donutColors.length],
    ]),
  ),
)

function buildDonutSlices(
  valueOf: (village: VillageSummary) => number,
  total: number,
) {
  let offset = 0
  return villages.value
    .filter((village) => valueOf(village) > 0)
    .map((village, index) => {
      const value = valueOf(village)
      const ratio = total > 0
        ? value / total
        : 0
      const slice = {
        shareId: village.shareId,
        villageName: village.villageName,
        value,
        ratio,
        color:
          villageColorMap.value[village.shareId]
          ?? donutColors[index % donutColors.length],
        dashArray: `${ratio * donutCircumference} ${donutCircumference}`,
        dashOffset: -offset,
      }
      offset += ratio * donutCircumference
      return slice
    })
}

const dividendSlices = computed(() =>
  buildDonutSlices(
    (village) => Math.max(0, village.dividendAmount ?? 0),
    dividendTotal.value,
  ),
)

const equitySlices = computed(() =>
  buildDonutSlices(
    (village) => Math.max(0, village.shareRatio ?? 0),
    equityTotal.value,
  ),
)

const focusedVillage = computed(
  () =>
    villages.value.find((item) => item.shareId === activeVillageId.value)
    ?? villages.value[0]
    ?? null,
)

const focusedSlices = computed(() =>
  dividendSlices.value.find(
    (slice) => slice.shareId === focusedVillage.value?.shareId,
  ) ?? null,
)

const focusedEquitySlice = computed(() =>
  equitySlices.value.find(
    (slice) => slice.shareId === focusedVillage.value?.shareId,
  ) ?? null,
)

function activateVillage(shareId: number) {
  activeVillageId.value = shareId
}

function dividendBarWidth(amount: number | null | undefined) {
  if (!amount || maxDividend.value <= 0) {
    return 0
  }
  return Math.max(6, (amount / maxDividend.value) * 100)
}

function toLocalDate(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

function formatCurrency(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—'
  }
  return `${value.toLocaleString('zh-CN')} 元`
}

function formatLandArea(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—'
  }
  return `${value.toLocaleString('zh-CN')} 亩`
}

function dividendStatusLabel(value: string | null | undefined) {
  if (!value) {
    return '暂无分红'
  }
  return value === 'CONFIRMED' ? '已确认' : '待确认'
}

async function loadVillages(preferredShareId?: number) {
  loading.value = true
  errorMessage.value = ''
  tableMessage.value = ''
  tableError.value = ''
  try {
    villages.value = await listVillages()
    const targetShareId = preferredShareId ?? activeVillageId.value
    activeVillageId.value =
      villages.value.some((item) => item.shareId === targetShareId)
        ? targetShareId ?? null
        : villages.value[0]?.shareId ?? null
    if (
      dividendForm.value.shareId === 0
      && villages.value.length > 0
    ) {
      dividendForm.value.shareId = villages.value[0].shareId
    }
  } catch {
    errorMessage.value = '合作运营数据暂不可用'
  } finally {
    loading.value = false
  }
}

async function removeVillageShare(village: VillageSummary) {
  const confirmed = window.confirm(
    `确定删除“${village.villageName} · ${village.projectName}”吗？关联分红记录也会一并删除。`,
  )
  if (!confirmed || deletingShareId.value !== null) {
    return
  }

  deletingShareId.value = village.shareId
  tableMessage.value = ''
  tableError.value = ''
  try {
    await deleteCooperationShare(village.shareId)
    villages.value = villages.value.filter(
      (item) => item.shareId !== village.shareId,
    )
    if (dividendForm.value.shareId === village.shareId) {
      dividendForm.value.shareId = villages.value[0]?.shareId ?? 0
    }
    if (activeVillageId.value === village.shareId) {
      activeVillageId.value = villages.value[0]?.shareId ?? null
    }
    tableMessage.value = `${village.villageName}入股记录已删除`
  } catch (error) {
    const responseMessage = (
      error as { response?: { data?: { message?: string } } }
    ).response?.data?.message
    tableError.value = responseMessage ?? '删除失败，请检查权限或服务状态'
  } finally {
    deletingShareId.value = null
  }
}

function openPanel(mode: 'share' | 'dividend') {
  formMode.value = mode
  panelOpen.value = true
  formMessage.value = ''
  formError.value = ''
}

async function submitShare() {
  if (!shareForm.value.villageName.trim()) {
    formError.value = '请输入所属村庄'
    return
  }
  submitting.value = true
  formMessage.value = ''
  formError.value = ''
  try {
    const result = await createShare({
      villageName: shareForm.value.villageName.trim(),
      projectName: shareForm.value.projectName,
      shareRatio: shareForm.value.shareRatioPercent / 100,
      effectiveDate: shareForm.value.effectiveDate,
      status: shareForm.value.status,
    })
    formMessage.value = `入股记录 #${result.shareId} 已保存`
    await loadVillages(result.shareId)
  } catch {
    formError.value = '入股记录保存失败'
  } finally {
    submitting.value = false
  }
}

async function submitDividend() {
  submitting.value = true
  formMessage.value = ''
  formError.value = ''
  try {
    const result = await createDividend({
      shareId: dividendForm.value.shareId,
      periodName: dividendForm.value.periodName,
      amount: dividendForm.value.amount,
      status: dividendForm.value.status,
    })
    formMessage.value = `分红方案 #${result.dividendId} 已保存`
    await loadVillages(result.shareId)
  } catch {
    formError.value = '分红方案保存失败'
  } finally {
    submitting.value = false
  }
}

onMounted(loadVillages)
</script>

<template>
  <div class="cooperation-page">
    <div class="page-heading">
      <div>
        <h2>多村合作台账</h2>
        <p>土地、股权、合同和分红规则集中管理</p>
      </div>
      <button
        v-if="canWrite"
        class="primary-button"
        type="button"
        @click="openPanel('dividend')"
      >
        <Plus :size="16" />
        新增分红方案
      </button>
    </div>

    <div class="metric-grid">
      <article v-for="item in cooperationStats" :key="item.label" class="metric">
        <span class="metric__label">{{ item.label }}</span>
        <span class="metric__value">
          {{ item.value }}
          <span class="metric__unit">{{ item.unit }}</span>
        </span>
      </article>
    </div>

    <section class="panel cooperation-chart-panel">
      <div class="panel__header">
        <div>
          <h3>分红与股权分布</h3>
          <p>按村庄对比本期分红金额，条宽按最高分红等比换算</p>
        </div>
        <StatusTag :tone="pendingCount > 0 ? 'warning' : 'online'">
          {{
            pendingCount > 0
              ? `${pendingCount} 条待确认`
              : `${confirmedCount} 条已确认`
          }}
        </StatusTag>
      </div>
      <div
        v-if="villages.length > 0"
        class="panel__body cooperation-chart-layout"
      >
        <div class="cooperation-donut-panel">
          <div class="cooperation-donut-grid">
            <section class="cooperation-donut-block is-dividend">
              <div class="cooperation-donut-block__head">
                <strong>分红金额柱状图</strong>
                <span>按村庄对比本期分红金额</span>
              </div>
              <div class="cooperation-dividend-columns" aria-label="本期分红金额柱状图">
                <div class="cooperation-dividend-columns__plot">
                  <span class="cooperation-dividend-columns__grid" aria-hidden="true"></span>
                  <button
                    v-for="slice in dividendSlices"
                    :key="`column-${slice.shareId}`"
                    type="button"
                    class="cooperation-dividend-column"
                    :class="{ 'is-active': focusedVillage?.shareId === slice.shareId }"
                    :aria-pressed="focusedVillage?.shareId === slice.shareId"
                    :aria-label="`${slice.villageName}，分红${formatCurrency(slice.value)}，占比${(slice.ratio * 100).toFixed(2)}%`"
                    @mouseenter="activateVillage(slice.shareId)"
                    @focus="activateVillage(slice.shareId)"
                    @click="activateVillage(slice.shareId)"
                    @keydown.enter.prevent="activateVillage(slice.shareId)"
                    @keydown.space.prevent="activateVillage(slice.shareId)"
                  >
                    <em>{{ formatCurrency(slice.value).replace(' 元', '') }}</em>
                    <span
                      class="cooperation-dividend-column__bar"
                      :style="{
                        height: `${Math.max(6, dividendBarWidth(slice.value))}%`,
                        background: slice.color,
                      }"
                    ></span>
                    <small>{{ slice.villageName }}</small>
                  </button>
                </div>
                <div class="cooperation-dividend-columns__focus">
                  <span>{{ focusedVillage?.villageName ?? '暂无数据' }}</span>
                  <strong>{{ formatCurrency(focusedVillage?.dividendAmount) }}</strong>
                  <em>
                    占总分红
                    {{ focusedSlices ? (focusedSlices.ratio * 100).toFixed(2) : '0.00' }}%
                  </em>
                </div>
              </div>
            </section>

            <section class="cooperation-donut-block is-equity">
              <div class="cooperation-donut-block__head">
                <strong>股权比例分布</strong>
                <span>按登记入股比例</span>
              </div>
              <div class="cooperation-donut" aria-label="股权比例扇形统计图">
                <svg viewBox="0 0 220 220" role="group">
                  <circle
                    class="cooperation-donut__track"
                    cx="110"
                    cy="110"
                    r="66"
                    transform="rotate(-90 110 110)"
                  />
                  <circle
                    v-for="slice in equitySlices"
                    :key="`equity-slice-${slice.shareId}`"
                    class="cooperation-donut__slice"
                    :class="{ 'is-active': focusedVillage?.shareId === slice.shareId }"
                    cx="110"
                    cy="110"
                    r="66"
                    transform="rotate(-90 110 110)"
                    :stroke="slice.color"
                    :stroke-dasharray="slice.dashArray"
                    :stroke-dashoffset="slice.dashOffset"
                    tabindex="0"
                    role="button"
                    :aria-pressed="focusedVillage?.shareId === slice.shareId"
                    :aria-label="`${slice.villageName}，股权比例${(slice.value * 100).toFixed(2)}%，占总股权${(slice.ratio * 100).toFixed(2)}%`"
                    @mouseenter="activateVillage(slice.shareId)"
                    @focus="activateVillage(slice.shareId)"
                    @click="activateVillage(slice.shareId)"
                    @keydown.enter.prevent="activateVillage(slice.shareId)"
                    @keydown.space.prevent="activateVillage(slice.shareId)"
                  >
                    <title>
                      {{ slice.villageName }} · 股权
                      {{ (slice.value * 100).toFixed(2) }}% · 占总股权
                      {{ (slice.ratio * 100).toFixed(2) }}%
                    </title>
                  </circle>
                </svg>
                <div class="cooperation-donut__center">
                  <span>{{ focusedVillage?.villageName ?? '暂无数据' }}</span>
                  <strong>
                    {{ focusedVillage
                      ? (focusedVillage.shareRatio * 100).toFixed(2)
                      : '0.00' }}%
                  </strong>
                  <em>
                    占总股权
                    {{
                      focusedEquitySlice
                        ? (focusedEquitySlice.ratio * 100).toFixed(2)
                        : '0.00'
                    }}%
                  </em>
                </div>
              </div>

              <div class="cooperation-donut-legend">
                <button
                  v-for="slice in equitySlices"
                  :key="`equity-legend-${slice.shareId}`"
                  type="button"
                  :class="{ 'is-active': focusedVillage?.shareId === slice.shareId }"
                  :aria-pressed="focusedVillage?.shareId === slice.shareId"
                  @mouseenter="activateVillage(slice.shareId)"
                  @focus="activateVillage(slice.shareId)"
                  @click="activateVillage(slice.shareId)"
                >
                  <i :style="{ background: slice.color }"></i>
                  <span>
                    <strong>{{ slice.villageName }}</strong>
                    <small>
                      股权 {{ (slice.value * 100).toFixed(2) }}% · 占比
                      {{ (slice.ratio * 100).toFixed(2) }}%
                    </small>
                  </span>
                </button>
              </div>
            </section>
          </div>
        </div>

        <div class="cooperation-bars-panel">
          <div class="cooperation-bars-panel__head">
            <strong>分红金额对比</strong>
            <span>条宽按最高分红等比换算</span>
          </div>
          <ul class="cooperation-bars">
            <li
              v-for="village in villages"
              :key="`bar-${village.shareId}`"
              :class="{ 'is-active': focusedVillage?.shareId === village.shareId }"
              tabindex="0"
              @mouseenter="activateVillage(village.shareId)"
              @focus="activateVillage(village.shareId)"
              @click="activateVillage(village.shareId)"
              @keydown.enter.prevent="activateVillage(village.shareId)"
              @keydown.space.prevent="activateVillage(village.shareId)"
            >
              <div class="cooperation-bars__head">
                <strong>{{ village.villageName }}</strong>
                <span>
                  {{ (village.shareRatio * 100).toFixed(2) }}% ·
                  {{ village.projectName }}
                </span>
              </div>
              <div class="cooperation-bars__track">
                <span
                  class="cooperation-bars__fill"
                  :class="{ 'is-pending': village.status !== 'CONFIRMED' }"
                  :style="{ width: `${dividendBarWidth(village.dividendAmount)}%` }"
                ></span>
              </div>
              <em>{{ formatCurrency(village.dividendAmount) }}</em>
            </li>
          </ul>
        </div>
      </div>
      <div v-else class="panel__body">
        <p class="empty-state">暂无合作运营数据</p>
      </div>
    </section>

    <section v-if="panelOpen" class="cooperation-action-panel">
      <div class="cooperation-action-panel__heading">
        <div class="cooperation-mode-tabs" role="tablist">
          <button
            type="button"
            :class="{ 'is-active': formMode === 'share' }"
            @click="formMode = 'share'"
          >
            <Sprout :size="15" />
            新增入股
          </button>
          <button
            type="button"
            :class="{ 'is-active': formMode === 'dividend' }"
            @click="formMode = 'dividend'"
          >
            <Coins :size="15" />
            新增分红
          </button>
        </div>
        <button
          class="cooperation-close"
          type="button"
          aria-label="关闭操作面板"
          @click="panelOpen = false"
        >
          <X :size="17" />
        </button>
      </div>

      <form
        v-if="formMode === 'share'"
        class="cooperation-form"
        @submit.prevent="submitShare"
      >
        <label>
          <span>所属村庄</span>
          <input
            v-model.trim="shareForm.villageName"
            type="text"
            list="cooperation-village-options"
            placeholder="输入村庄名称，不存在时会自动创建"
          />
          <datalist id="cooperation-village-options">
            <option
              v-for="village in villages"
              :key="village.villageId"
              :value="village.villageName"
            ></option>
          </datalist>
        </label>
        <label>
          <span>合作项目</span>
          <input v-model.trim="shareForm.projectName" type="text" />
        </label>
        <label>
          <span>股权比例（%）</span>
          <input
            v-model.number="shareForm.shareRatioPercent"
            type="number"
            min="0.01"
            max="100"
            step="0.01"
          />
        </label>
        <label>
          <span>生效日期</span>
          <input v-model="shareForm.effectiveDate" type="date" />
        </label>
        <label>
          <span>状态</span>
          <select v-model="shareForm.status">
            <option value="ACTIVE">生效</option>
            <option value="PENDING">待确认</option>
          </select>
        </label>
        <div class="cooperation-form__actions">
          <button class="primary-button" type="submit" :disabled="submitting">
            {{ submitting ? '正在保存' : '保存入股记录' }}
          </button>
        </div>
      </form>

      <form
        v-else
        class="cooperation-form"
        @submit.prevent="submitDividend"
      >
        <label>
          <span>入股记录</span>
          <select v-model.number="dividendForm.shareId">
            <option
              v-for="village in villages"
              :key="village.shareId"
              :value="village.shareId"
            >
              {{ village.villageName }} · {{ village.projectName }}
            </option>
          </select>
        </label>
        <label>
          <span>分红周期</span>
          <input v-model.trim="dividendForm.periodName" type="text" />
        </label>
        <label>
          <span>分红金额（元）</span>
          <input
            v-model.number="dividendForm.amount"
            type="number"
            min="0.01"
            step="0.01"
          />
        </label>
        <label>
          <span>确认状态</span>
          <select v-model="dividendForm.status">
            <option value="PENDING">待确认</option>
            <option value="CONFIRMED">已确认</option>
          </select>
        </label>
        <div class="cooperation-form__actions">
          <button class="primary-button" type="submit" :disabled="submitting">
            {{ submitting ? '正在保存' : '提交分红方案' }}
          </button>
        </div>
      </form>

      <p v-if="formMessage" class="cooperation-feedback is-success">
        {{ formMessage }}
      </p>
      <p v-if="formError" class="cooperation-feedback is-error">
        {{ formError }}
      </p>
    </section>

    <section class="panel">
      <div class="panel__header">
        <h3>村庄经营概览</h3>
        <StatusTag>{{ villages.length }} 条入股记录</StatusTag>
      </div>
      <div class="panel__body table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>村庄</th>
              <th>合作项目</th>
              <th>入股土地</th>
              <th>股权比例</th>
              <th>本期分红</th>
              <th>确认状态</th>
              <th v-if="canWrite">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="village in villages" :key="village.shareId">
              <td>{{ village.villageName }}</td>
              <td>{{ village.projectName }}</td>
              <td>{{ formatLandArea(village.landArea) }}</td>
              <td>{{ (village.shareRatio * 100).toFixed(2) }}%</td>
              <td>{{ formatCurrency(village.dividendAmount) }}</td>
              <td v-if="canWrite">
                <StatusTag
                  :tone="!village.status
                    ? 'offline'
                    : village.status === 'CONFIRMED' ? 'online' : 'warning'"
                >
                  {{ dividendStatusLabel(village.status) }}
                </StatusTag>
              </td>
              <td>
                <button
                  class="cooperation-delete"
                  type="button"
                  :disabled="deletingShareId === village.shareId"
                  :aria-label="`删除${village.villageName}入股记录`"
                  @click="removeVillageShare(village)"
                >
                  <Trash2 :size="14" />
                  {{ deletingShareId === village.shareId ? '删除中' : '删除' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="loading" class="empty-state">正在加载合作运营数据...</p>
        <p v-else-if="errorMessage" class="empty-state">{{ errorMessage }}</p>
        <p v-else-if="villages.length === 0" class="empty-state">
          暂无合作运营数据
        </p>
        <p v-if="tableMessage" class="cooperation-table-feedback is-success">
          {{ tableMessage }}
        </p>
        <p v-if="tableError" class="cooperation-table-feedback is-error">
          {{ tableError }}
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.cooperation-page {
  display: grid;
  gap: 18px;
}

.cooperation-chart-panel .panel__header {
  align-items: flex-start;
}

.cooperation-chart-panel .panel__header p {
  margin: 5px 0 0;
  color: #65736c;
  font-size: 12px;
  font-weight: 400;
}

.cooperation-chart-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr);
  gap: 24px;
  align-items: start;
}

.cooperation-donut-panel {
  display: grid;
  gap: 14px;
  align-content: start;
  padding-right: 24px;
  border-right: 1px solid #e3ebe6;
}

.cooperation-donut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.cooperation-donut-block {
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
}

.cooperation-donut-block.is-equity {
  order: 1;
}

.cooperation-donut-block.is-dividend {
  order: 2;
}

.cooperation-donut-block__head strong,
.cooperation-donut-block__head span {
  display: block;
}

.cooperation-donut-block__head strong {
  color: #26382e;
  font-size: 12px;
}

.cooperation-donut-block__head span {
  margin-top: 3px;
  color: #7a8780;
  font-size: 9px;
}

.cooperation-dividend-columns {
  display: grid;
  gap: 10px;
  min-height: 348px;
  padding: 10px;
  background: #f8faf9;
  border: 1px solid #e0e8e3;
  border-radius: 7px;
}

.cooperation-dividend-columns__plot {
  position: relative;
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(0, 1fr);
  align-items: stretch;
  gap: 8px;
  height: 264px;
  padding: 8px 4px 0;
  border-bottom: 1px solid #dce5df;
}

.cooperation-dividend-columns__grid {
  position: absolute;
  inset: 8px 4px 0;
  background:
    repeating-linear-gradient(
      to bottom,
      transparent 0,
      transparent calc(25% - 1px),
      #e3ebe6 calc(25% - 1px),
      #e3ebe6 25%
    );
  pointer-events: none;
}

.cooperation-dividend-column {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-rows: 20px minmax(0, 1fr) 28px;
  gap: 5px;
  align-items: end;
  min-width: 0;
  height: 100%;
  padding: 0;
  color: #52645a;
  background: transparent;
  border: 0;
  border-radius: 6px;
  outline: none;
}

.cooperation-dividend-column em {
  align-self: start;
  overflow: hidden;
  font-size: 8px;
  font-style: normal;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cooperation-dividend-column__bar {
  display: block;
  align-self: end;
  justify-self: center;
  width: min(32px, 72%);
  min-height: 5px;
  border-radius: 4px 4px 0 0;
  transform-origin: bottom center;
  transition:
    transform 150ms ease,
    filter 150ms ease,
    opacity 150ms ease;
}

.cooperation-dividend-column small {
  overflow: hidden;
  color: #66766d;
  font-size: 8px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cooperation-dividend-column:hover .cooperation-dividend-column__bar,
.cooperation-dividend-column:focus-visible .cooperation-dividend-column__bar,
.cooperation-dividend-column.is-active .cooperation-dividend-column__bar {
  filter: drop-shadow(0 4px 7px rgb(31 69 51 / 18%));
  transform: scaleX(1.16);
}

.cooperation-dividend-column:focus-visible {
  box-shadow: 0 0 0 3px rgb(33 115 84 / 12%);
}

.cooperation-dividend-columns__focus {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    "name amount"
    "ratio amount";
  align-items: center;
  gap: 2px 10px;
  min-height: 48px;
  padding: 7px 9px;
  background: #ffffff;
  border: 1px solid #e0e8e3;
  border-radius: 6px;
}

.cooperation-dividend-columns__focus span {
  grid-area: name;
  overflow: hidden;
  color: #66766d;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cooperation-dividend-columns__focus strong {
  grid-area: amount;
  color: #26382e;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.cooperation-dividend-columns__focus em {
  grid-area: ratio;
  color: #267a58;
  font-size: 9px;
  font-style: normal;
}

.cooperation-donut {
  position: relative;
  width: 252px;
  height: 252px;
  margin: 0 auto;
}

.cooperation-donut svg {
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}

.cooperation-donut__track,
.cooperation-donut__slice {
  fill: none;
  stroke-linecap: butt;
}

.cooperation-donut__track {
  stroke: #edf3ef;
  stroke-width: 26;
  pointer-events: none;
}

.cooperation-donut__slice {
  cursor: pointer;
  outline: none;
  pointer-events: stroke;
  stroke-width: 26;
  transition:
    stroke-width 160ms ease,
    opacity 160ms ease,
    filter 160ms ease;
}

.cooperation-donut__slice:hover,
.cooperation-donut__slice:focus,
.cooperation-donut__slice.is-active {
  stroke-width: 34;
  filter: drop-shadow(0 4px 7px rgb(31 69 51 / 18%));
}

.cooperation-donut__slice:focus-visible {
  filter:
    drop-shadow(0 0 0 #ffffff)
    drop-shadow(0 0 5px rgb(33 115 84 / 55%));
}

.cooperation-donut__center {
  position: absolute;
  inset: 56px;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 4px;
  padding: 16px;
  text-align: center;
  background: #f8fbf9;
  border: 1px solid #e4ece7;
  border-radius: 50%;
  box-shadow: inset 0 0 0 6px #ffffff;
  pointer-events: none;
}

.cooperation-donut__center span,
.cooperation-donut__center strong,
.cooperation-donut__center em {
  display: block;
  max-width: 100%;
}

.cooperation-donut__center span {
  overflow: hidden;
  color: #69776f;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cooperation-donut__center strong {
  color: #23392d;
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.cooperation-donut__center em {
  color: #267a58;
  font-size: 10px;
  font-style: normal;
}

.cooperation-donut-legend {
  display: grid;
  gap: 6px;
}

.cooperation-donut-legend button {
  display: grid;
  grid-template-columns: 9px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  min-height: 42px;
  padding: 7px 9px;
  color: #33483c;
  text-align: left;
  background: #f8faf9;
  border: 1px solid #e0e8e3;
  border-radius: 7px;
  transition:
    background 150ms ease,
    border-color 150ms ease,
    transform 150ms ease;
}

.cooperation-donut-legend button:hover,
.cooperation-donut-legend button:focus-visible,
.cooperation-donut-legend button.is-active {
  background: #eef7f1;
  border-color: #9fc8b1;
  outline: none;
  transform: translateX(2px);
}

.cooperation-donut-legend i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.cooperation-donut-legend strong,
.cooperation-donut-legend small {
  display: block;
}

.cooperation-donut-legend strong {
  font-size: 11px;
}

.cooperation-donut-legend small {
  margin-top: 3px;
  color: #748179;
  font-size: 9px;
}

.cooperation-bars-panel {
  min-width: 0;
}

.cooperation-bars-panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.cooperation-bars-panel__head strong {
  color: #26382e;
  font-size: 13px;
}

.cooperation-bars-panel__head span {
  color: #7a8780;
  font-size: 10px;
}

.cooperation-bars {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.cooperation-bars li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    "head amount"
    "track track";
  align-items: center;
  gap: 8px 12px;
  padding: 8px 10px;
  border-radius: 7px;
  outline: none;
  transition:
    background 150ms ease,
    box-shadow 150ms ease;
}

.cooperation-bars li:hover,
.cooperation-bars li:focus-visible,
.cooperation-bars li.is-active {
  background: #f2f8f4;
  box-shadow: inset 3px 0 #267a58;
}

.cooperation-bars__head {
  grid-area: head;
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.cooperation-bars__head strong {
  color: #26382e;
  font-size: 13px;
  white-space: nowrap;
}

.cooperation-bars__head span {
  overflow: hidden;
  color: #75827a;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cooperation-bars__track {
  grid-area: track;
  height: 10px;
  background: #eef3ef;
  border: 1px solid #e0e8e3;
  border-radius: 5px;
}

.cooperation-bars__fill {
  display: block;
  height: 100%;
  background: #267a58;
  border-radius: 4px;
}

.cooperation-bars__fill.is-pending {
  background: #c08a2e;
}

.cooperation-bars li > em {
  grid-area: amount;
  color: #26382e;
  font-size: 12px;
  font-style: normal;
  font-weight: 600;
  text-align: right;
  white-space: nowrap;
}

.cooperation-action-panel {
  padding: 18px;
  background: #ffffff;
  border: 1px solid #dce5df;
  border-radius: 8px;
}

.cooperation-action-panel__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e5ebe7;
}

.cooperation-mode-tabs {
  display: inline-flex;
  gap: 3px;
  padding: 3px;
  background: #eef3ef;
  border: 1px solid #dce5df;
  border-radius: 7px;
}

.cooperation-mode-tabs button,
.cooperation-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 34px;
  color: #56685e;
  background: transparent;
  border: 0;
  border-radius: 6px;
  font-size: 12px;
}

.cooperation-mode-tabs button {
  padding: 0 12px;
}

.cooperation-mode-tabs button.is-active {
  color: #ffffff;
  background: #217354;
}

.cooperation-close {
  width: 34px;
  color: #68776f;
}

.cooperation-close:hover {
  color: #a14e48;
  background: #fff0ef;
}

.cooperation-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  padding-top: 16px;
}

.cooperation-form label {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.cooperation-form label > span {
  color: #53635b;
  font-size: 12px;
  font-weight: 700;
}

.cooperation-form input,
.cooperation-form select {
  width: 100%;
  min-height: 40px;
  padding: 0 11px;
  color: #17201b;
  background: #ffffff;
  border: 1px solid #cbd7cf;
  border-radius: 7px;
  outline: none;
}

.cooperation-form input:focus,
.cooperation-form select:focus {
  border-color: #217354;
  box-shadow: 0 0 0 3px rgb(33 115 84 / 11%);
}

.cooperation-form__actions {
  display: flex;
  align-items: flex-end;
}

.cooperation-feedback {
  margin: 14px 0 0;
  font-size: 12px;
}

.cooperation-feedback.is-success {
  color: #217354;
}

.cooperation-feedback.is-error {
  color: #a14e48;
}

.cooperation-delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 9px;
  color: #a14e48;
  background: #fff7f6;
  border: 1px solid #e8cecb;
  border-radius: 6px;
  font-size: 11px;
}

.cooperation-delete:hover {
  color: #ffffff;
  background: #a14e48;
  border-color: #a14e48;
}

.cooperation-delete:disabled {
  cursor: wait;
  opacity: 0.6;
}

.cooperation-table-feedback {
  margin: 12px 0 0;
  font-size: 11px;
}

.cooperation-table-feedback.is-success {
  color: #217354;
}

.cooperation-table-feedback.is-error {
  color: #a14e48;
}

@media (max-width: 1100px) {
  .cooperation-chart-layout {
    grid-template-columns: 1fr;
  }

  .cooperation-donut-panel {
    padding-right: 0;
    padding-bottom: 22px;
    border-right: 0;
    border-bottom: 1px solid #e3ebe6;
  }

  .cooperation-donut-legend {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .cooperation-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .cooperation-form {
    grid-template-columns: 1fr;
  }

  .cooperation-action-panel__heading {
    align-items: flex-start;
  }

  .cooperation-donut-grid {
    grid-template-columns: 1fr;
  }

  .cooperation-donut {
    width: 190px;
    height: 190px;
  }

  .cooperation-donut__center {
    inset: 43px;
  }

  .cooperation-donut-legend {
    grid-template-columns: 1fr;
  }

  .cooperation-bars-panel__head {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
