<!--
  消费者扫码落地页：只展示公开校验信息，不出现任何内部控制入口。
  二维码里只写 /trace?code=产品编码，追溯明细由本页调用公开接口查询。
-->
<script setup lang="ts">
import {
  CircleCheck,
  CircleAlert,
  Droplets,
  Leaf,
  ScanLine,
  ShieldCheck,
  Sprout,
  Sun,
  Thermometer,
} from 'lucide-vue-next'
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { useRoute } from 'vue-router'
import { queryTrace } from '@/api/modules/trace'
import type { TraceEnvironmentMetric, TraceResult } from '@/types/api'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const result = ref<TraceResult | null>(null)

const code = computed(() => {
  const fromQuery = route.query.code
  if (typeof fromQuery === 'string' && fromQuery.trim()) {
    return fromQuery.trim()
  }
  const fromPath = route.params.code
  return typeof fromPath === 'string' ? fromPath.trim() : ''
})

const verified = computed(() => result.value?.verificationStatus === 'VERIFIED')

const metricOrder = ['temperature', 'humidity', 'light', 'soil_moisture'] as const

const metricMeta: Record<string, { label: string; icon: Component }> = {
  temperature: { label: '空气温度', icon: Thermometer },
  humidity: { label: '空气湿度', icon: Droplets },
  light: { label: '光照强度', icon: Sun },
  soil_moisture: { label: '土壤水分', icon: Sprout },
}

const environmentMetrics = computed(() => {
  const metrics = result.value?.environment?.metrics ?? []
  const byCode = new Map(metrics.map((metric) => [metric.metricCode, metric]))
  const ordered = metricOrder
    .map((code) => byCode.get(code))
    .filter((metric): metric is TraceEnvironmentMetric => Boolean(metric))
  const extras = metrics.filter(
    (metric) => !(metricOrder as readonly string[]).includes(metric.metricCode),
  )
  return [...ordered, ...extras]
})

const environmentComplete = computed(() =>
  metricOrder.every((code) =>
    result.value?.environment?.metrics.some((metric) => metric.metricCode === code),
  ),
)

function metricLabel(metricCode: string) {
  return metricMeta[metricCode]?.label ?? metricCode
}

function metricIcon(metricCode: string) {
  return metricMeta[metricCode]?.icon ?? Leaf
}

function formatMetricValue(metric: TraceEnvironmentMetric) {
  return `${metric.value}${metric.unit ?? ''}`
}

const statusLabel = computed(() => {
  if (!result.value) {
    return '未查询到'
  }
  if (!result.value.found) {
    return '编码不存在'
  }
  return verified.value ? '链上核验通过' : '待核验'
})

function shorten(value: string, head = 10, tail = 8) {
  if (!value) {
    return '—'
  }
  return value.length <= head + tail + 3
    ? value
    : `${value.slice(0, head)}…${value.slice(-tail)}`
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  if (!code.value) {
    errorMessage.value = '缺少溯源码，请重新扫码'
    result.value = null
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    result.value = await queryTrace(code.value)
  } catch {
    result.value = null
    errorMessage.value = '查询失败，请稍后重试或联系基地'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

watch(code, () => {
  void load()
})
</script>

<template>
  <main class="trace-public">
    <header class="trace-public__brand">
      <span><Leaf :size="20" /></span>
      <div>
        <strong>智慧温室</strong>
        <small>农产品全链条溯源查询</small>
      </div>
    </header>

    <section class="trace-public__card">
      <div class="trace-public__code">
        <ScanLine :size="16" />
        <span>{{ code || '未提供溯源码' }}</span>
      </div>

      <p v-if="loading" class="trace-public__hint">正在核验…</p>
      <p v-else-if="errorMessage" class="trace-public__hint is-error">
        {{ errorMessage }}
      </p>

      <template v-else-if="result">
        <div
          class="trace-public__status"
          :class="verified && result.found ? 'is-ok' : 'is-pending'"
        >
          <component :is="verified && result.found ? CircleCheck : CircleAlert" :size="26" />
          <div>
            <strong>{{ statusLabel }}</strong>
            <small>
              {{ result.message || (result.pointerChainValid ? '指针链完整' : '指针链待核验') }}
            </small>
          </div>
        </div>

        <dl class="trace-public__meta">
          <div>
            <dt>产品编码</dt>
            <dd>{{ result.productCode }}</dd>
          </div>
          <div>
            <dt>田块编号</dt>
            <dd>{{ result.fieldNo ?? '—' }}</dd>
          </div>
          <div>
            <dt>作物批次</dt>
            <dd>{{ result.batchId ? `CB-${result.batchId}` : '—' }}</dd>
          </div>
          <div>
            <dt>电子合格证</dt>
            <dd>
              {{ result.certificateNo ?? '未关联' }}
              <em v-if="result.certificateStatus">· {{ result.certificateStatus }}</em>
            </dd>
          </div>
          <div class="is-wide">
            <dt>环境周期根哈希</dt>
            <dd class="is-mono">{{ result.rootHash ?? '—' }}</dd>
          </div>
          <div class="is-wide">
            <dt>签名公钥</dt>
            <dd class="is-mono">{{ result.publicKeyId ?? '—' }}</dd>
          </div>
        </dl>

        <h2 class="trace-public__section">
          <Thermometer :size="16" />
          环境存证
        </h2>
        <template v-if="result.environment">
          <dl class="trace-public__env-grid">
            <div
              v-for="metric in environmentMetrics"
              :key="metric.metricCode"
            >
              <dt>
                <component :is="metricIcon(metric.metricCode)" :size="16" />
                {{ metricLabel(metric.metricCode) }}
              </dt>
              <dd>
                <strong>{{ formatMetricValue(metric) }}</strong>
                <small>{{ formatTime(metric.collectedAt) }}</small>
              </dd>
            </div>
          </dl>
          <p class="trace-public__env-summary">
            环境周期 #{{ result.environment.cycleId }}
            · {{ formatTime(result.environment.cycleStart) }}
            至 {{ formatTime(result.environment.cycleEnd) }}
            <span
              :class="result.environment.chainStatus === 'CONFIRMED'
                ? 'is-ok'
                : 'is-pending'"
            >
              {{ result.environment.chainStatus === 'CONFIRMED'
                ? '链上已确认'
                : '链上待补偿' }}
            </span>
          </p>
          <p class="trace-public__env-note">
            <template v-if="environmentComplete">
              温度、湿度、光照、土壤水分四项读数均已计入该周期的环境根哈希。
            </template>
            <template v-else>
              该环境周期指标不完整，仅供参考。
            </template>
          </p>
        </template>
        <p v-else class="trace-public__hint">暂无可展示的环境周期存证</p>

        <h2 class="trace-public__section">
          <ShieldCheck :size="16" />
          追溯节点
        </h2>
        <ol class="trace-public__timeline">
          <li v-for="stage in result.stages" :key="stage.nodeId">
            <span class="trace-public__dot"></span>
            <div>
              <strong>{{ stage.stageType }}</strong>
              <small>{{ formatTime(stage.createdAt) }}</small>
              <code>{{ shorten(stage.payloadHash) }}</code>
              <em :class="{ 'is-on-chain': Boolean(stage.chainTxHash) }">
                {{ stage.chainTxHash ? `链上回执 ${shorten(stage.chainTxHash)}` : '链上待补偿' }}
              </em>
            </div>
          </li>
          <li v-if="!result.stages.length" class="is-empty">
            <div><strong>暂无追溯节点</strong></div>
          </li>
        </ol>

        <p class="trace-public__note">
          本页只展示可公开的校验信息。标注“链上待补偿”表示该记录已入库但尚未写入联盟链，
          链上回执出现后即为最终存证。
        </p>
      </template>
    </section>

    <footer class="trace-public__footer">
      <a href="/login">内部人员入口</a>
    </footer>
  </main>
</template>

<style scoped>
.trace-public {
  display: grid;
  align-content: start;
  gap: 18px;
  min-height: 100vh;
  padding: 28px 20px 40px;
  color: var(--ink-900, #12241e);
  background:
    radial-gradient(900px 520px at 12% -10%, #e4f5ee 0%, rgb(228 245 238 / 0%) 62%),
    linear-gradient(170deg, #f5faf7 0%, #e9f1ee 100%);
}

.trace-public__brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.trace-public__brand > span {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  color: #ffffff;
  background: linear-gradient(145deg, #4fae99, #0e7c6b);
  border-radius: 13px;
}

.trace-public__brand strong,
.trace-public__brand small {
  display: block;
}

.trace-public__brand strong {
  font-size: 16px;
  font-weight: 650;
}

.trace-public__brand small {
  margin-top: 3px;
  color: var(--ink-500, #6b837a);
  font-size: 12px;
}

.trace-public__card {
  display: grid;
  gap: 14px;
  width: min(680px, 100%);
  margin: 0 auto;
  padding: 18px;
  background: rgb(255 255 255 / 94%);
  border: 1px solid rgb(18 36 30 / 8%);
  border-radius: 20px;
  box-shadow:
    0 1px 2px rgb(9 62 54 / 4%),
    0 14px 34px rgb(9 62 54 / 8%);
}

.trace-public__code {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  color: var(--mint-800, #084f45);
  background: var(--mint-100, #e2f0ec);
  border-radius: 12px;
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.trace-public__hint {
  margin: 0;
  color: var(--ink-500, #6b837a);
  font-size: 13px;
}

.trace-public__hint.is-error {
  color: var(--color-danger-strong, #b23b37);
}

.trace-public__status {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
}

.trace-public__status.is-ok {
  color: var(--color-success-strong, #0a6558);
  background: var(--color-success-soft, #e2f0ec);
}

.trace-public__status.is-pending {
  color: var(--color-warning-strong, #9a6a0d);
  background: var(--color-warning-soft, #fdf3e2);
}

.trace-public__status strong {
  display: block;
  font-size: 16px;
}

.trace-public__status small {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.85;
}

.trace-public__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
  margin: 0;
}

.trace-public__meta > div {
  min-width: 0;
  padding: 10px 12px;
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 6%);
  border-radius: 12px;
}

.trace-public__meta > div.is-wide {
  grid-column: 1 / -1;
}

.trace-public__meta dt {
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
}

.trace-public__meta dd {
  margin: 5px 0 0;
  color: var(--ink-900, #12241e);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.trace-public__meta dd.is-mono {
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
}

.trace-public__meta dd em {
  color: var(--color-success-strong, #0a6558);
  font-style: normal;
}

.trace-public__env-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.trace-public__env-grid > div {
  padding: 12px;
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 6%);
  border-radius: 12px;
}

.trace-public__env-grid dt {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-500, #6b837a);
  font-size: 12px;
}

.trace-public__env-grid dd {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin: 6px 0 0;
}

.trace-public__env-grid strong {
  color: var(--ink-900, #12241e);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.trace-public__env-grid small {
  color: var(--ink-500, #6b837a);
  font-size: 11px;
}

.trace-public__env-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: var(--ink-500, #6b837a);
  font-size: 12px;
}

.trace-public__env-summary span {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
}

.trace-public__env-summary span.is-ok {
  color: var(--color-success-strong, #0a6558);
  background: var(--color-success-soft, #e2f0ec);
}

.trace-public__env-summary span.is-pending {
  color: var(--color-warning-strong, #9a6a0d);
  background: var(--color-warning-soft, #fdf3e2);
}

.trace-public__env-note {
  margin: 0;
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
  line-height: 1.6;
}

.trace-public__section {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 4px 0 0;
  font-size: 14px;
}

.trace-public__timeline {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.trace-public__timeline li {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr);
  gap: 10px;
  padding: 10px 12px;
  background: rgb(255 255 255 / 96%);
  border: 1px solid rgb(18 36 30 / 7%);
  border-radius: 12px;
}

.trace-public__dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  background: var(--mint-500, #1e957d);
  border-radius: 50%;
}

.trace-public__timeline strong {
  display: block;
  font-size: 13.5px;
}

.trace-public__timeline small {
  display: block;
  margin-top: 3px;
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
}

.trace-public__timeline code {
  display: block;
  margin-top: 5px;
  color: var(--ink-700, #3d544b);
  font-family: Consolas, "Courier New", monospace;
  font-size: 11.5px;
}

.trace-public__timeline em {
  display: inline-block;
  margin-top: 6px;
  padding: 2px 8px;
  color: var(--color-warning-strong, #9a6a0d);
  background: var(--color-warning-soft, #fdf3e2);
  border-radius: 999px;
  font-size: 11px;
  font-style: normal;
}

.trace-public__timeline em.is-on-chain {
  color: var(--color-success-strong, #0a6558);
  background: var(--color-success-soft, #e2f0ec);
}

.trace-public__note {
  margin: 0;
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
  line-height: 1.6;
}

.trace-public__footer {
  width: min(680px, 100%);
  margin: 0 auto;
  text-align: center;
}

.trace-public__footer a {
  color: var(--mint-700, #0a6558);
  font-size: 12px;
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 560px) {
  .trace-public__meta {
    grid-template-columns: minmax(0, 1fr);
  }

  .trace-public__env-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
