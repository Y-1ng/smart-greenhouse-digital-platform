<!-- 治理决策页面：展示监管、风险和环境质量指标。 -->
<script setup lang="ts">
import {
  AlertTriangle,
  Building2,
  CloudSun,
  Link2,
  RefreshCw,
  ScanLine,
  Wifi,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import DataFreshnessTag from '@/components/DataFreshnessTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import { acknowledgeAlert, listAlerts } from '@/api/modules/alerts'
import {
  getChainStatus,
  triggerChainCompensation,
} from '@/api/modules/chain'
import {
  getExternalStatus,
  listExternalLogs,
  syncExternalPlatform,
} from '@/api/modules/external'
import { getGovernmentDashboard } from '@/api/modules/dashboard'
import { useSessionStore } from '@/stores/session'
import type {
  AlertRecord,
  ChainStatus,
  ExternalLog,
  ExternalPlatformStatus,
  GovernmentDashboard,
} from '@/types/api'

const session = useSessionStore()
const dashboard = ref<GovernmentDashboard | null>(null)
const errorMessage = ref('')
const alerts = ref<AlertRecord[]>([])
const alertMessage = ref('')
const ackPendingId = ref<number | null>(null)
const chainStatus = ref<ChainStatus | null>(null)
const chainMessage = ref('')
const compensating = ref(false)
const externalStatus = ref<ExternalPlatformStatus[]>([])
const externalLogs = ref<ExternalLog[]>([])
const externalSyncing = ref(false)
const externalMessage = ref('')
const freshnessNow = ref(Date.now())
let freshnessTimer: number | undefined

function formatLocalTime(value: string, withDate = false) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    hour12: false,
    month: withDate ? '2-digit' : undefined,
    day: withDate ? '2-digit' : undefined,
    hour: '2-digit',
    minute: '2-digit',
  })
}
const canAcknowledge = computed(
  () => session.roleCode === 'ADMIN' || session.roleCode === 'OPERATOR',
)
const canCompensate = computed(
  () => session.roleCode === 'ADMIN' || session.roleCode === 'OPERATOR',
)
const canSyncExternal = computed(
  () => session.roleCode === 'ADMIN' || session.roleCode === 'OPERATOR',
)

const indicators = computed(() => [
  {
    label: '接入温室',
    value: String(dashboard.value?.greenhouseCount ?? 0),
    unit: '座',
    icon: Building2,
  },
  {
    label: '设备在线率',
    value: ((dashboard.value?.deviceOnlineRate ?? 0) * 100).toFixed(1),
    unit: '%',
    icon: Wifi,
  },
  {
    label: '待处理预警',
    value: String(dashboard.value?.activeAlerts ?? 0),
    unit: '条',
    icon: AlertTriangle,
  },
  {
    label: '溯源覆盖率',
    value: ((dashboard.value?.traceabilityCoverage ?? 0) * 100).toFixed(1),
    unit: '%',
    icon: ScanLine,
  },
])

const riskItems = computed(() => [
  {
    label: '设备离线',
    value: dashboard.value?.offlineDeviceCount ?? 0,
    unit: '台',
  },
  {
    label: '链上待确认存证',
    value: dashboard.value?.pendingChainProofCount ?? 0,
    unit: '条',
  },
  {
    label: '重复扫码异常',
    value: dashboard.value?.repeatScanCount ?? 0,
    unit: '条',
  },
])

const pendingAlertCount = computed(
  () => alerts.value.filter((alert) => alert.status === 'PENDING').length,
)

const governanceFreshness = computed(() => {
  if (errorMessage.value) {
    return {
      mode: 'stale' as const,
      label: '治理数据暂不可用',
    }
  }
  if (!dashboard.value) {
    return {
      mode: 'loading' as const,
      label: '正在同步治理数据',
    }
  }
  const generatedAt = new Date(dashboard.value.generatedAt).getTime()
  const age = Number.isFinite(generatedAt)
    ? Math.max(0, freshnessNow.value - generatedAt)
    : 0
  if (age > 5 * 60_000) {
    return {
      mode: 'stale' as const,
      label: `数据延迟 ${Math.max(1, Math.floor(age / 60_000))} 分钟`,
    }
  }
  return {
    mode: 'real' as const,
    label: `治理数据 · ${formatLocalTime(dashboard.value.generatedAt)}`,
  }
})

const hasPendingProof = computed(
  () => (chainStatus.value?.pending.total ?? 0) > 0,
)

const chainTone = computed(() => {
  if (!chainStatus.value) {
    return 'offline'
  }
  if (chainStatus.value.adapter.reachable) {
    return 'online'
  }
  return chainStatus.value.adapter.mode === 'rest' ? 'offline' : 'warning'
})

const chainTagText = computed(() => {
  if (!chainStatus.value) {
    return '状态未知'
  }
  if (chainStatus.value.adapter.reachable) {
    return '适配服务已连接'
  }
  return chainStatus.value.adapter.mode === 'rest'
    ? '适配服务不可用'
    : '本地模拟模式'
})

const contractAddress = computed(
  () => chainStatus.value?.adapter.contractAddress || '暂未配置',
)

const chainModeLabel = computed(() => {
  if (!chainStatus.value) {
    return '状态未知'
  }
  return chainStatus.value.adapter.mode === 'rest'
    ? '真实链（REST 适配器）'
    : '本地模拟（未上链）'
})

const chainModeTone = computed(() => {
  if (!chainStatus.value) {
    return 'is-unknown'
  }
  return chainStatus.value.adapter.mode === 'rest'
    ? 'is-real'
    : 'is-simulated'
})

const chainContractHint = computed(() => {
  const adapter = chainStatus.value?.adapter
  if (!adapter?.contractAddress) {
    return adapter?.mode === 'rest'
      ? '真实链模式尚未配置合约地址'
      : '模拟模式不使用链上合约'
  }
  return adapter.reachable
    ? '合约地址已加载，可发起真实交易'
    : '地址已配置，但适配服务当前不可达'
})

const pendingProofItems = computed(() => [
  {
    label: '环境周期存证',
    value: chainStatus.value?.pending.environmentCycle ?? 0,
  },
  {
    label: '合格证存证',
    value: chainStatus.value?.pending.certificate ?? 0,
  },
  {
    label: '一物一码存证',
    value: chainStatus.value?.pending.traceCode ?? 0,
  },
  {
    label: '扫码记录存证',
    value: chainStatus.value?.pending.scanRecord ?? 0,
  },
])

async function loadChainStatus() {
  try {
    chainStatus.value = await getChainStatus()
    chainMessage.value = chainStatus.value.adapter.message
  } catch {
    chainStatus.value = null
    chainMessage.value = '链上状态暂不可用'
  }
}

async function runCompensation() {
  compensating.value = true
  chainMessage.value = ''
  try {
    const report = await triggerChainCompensation()
    if (report.attempted === 0) {
      chainMessage.value = '当前没有待补偿存证'
    } else {
      const latest = report.entries[0]
      chainMessage.value = latest
        ? `本次补偿成功 ${report.confirmed} 条，失败 ${report.failed} 条，最新交易 ${latest.transactionHash.slice(0, 18)}…（区块 ${latest.blockNumber}）`
        : `本次补偿成功 ${report.confirmed} 条，失败 ${report.failed} 条`
    }
    await loadChainStatus()
  } catch {
    chainMessage.value = '补偿失败，请检查链上适配服务'
  } finally {
    compensating.value = false
  }
}

function externalStatusText(status: ExternalPlatformStatus) {
  if (status.lastStatus === 'SUCCESS') {
    return `最近调用成功 · 累计成功 ${status.successCount} 次`
  }
  if (status.lastStatus === 'FAILED') {
    return `最近调用失败 · 累计失败 ${status.failureCount} 次`
  }
  return '尚未调用，可手动同步验证'
}

async function loadExternalStatus() {
  try {
    const [status, logs] = await Promise.all([
      getExternalStatus(),
      listExternalLogs(5),
    ])
    externalStatus.value = status
    externalLogs.value = logs
  } catch {
    externalStatus.value = []
    externalLogs.value = []
  }
}

async function runExternalSync() {
  externalSyncing.value = true
  externalMessage.value = ''
  try {
    const reports = await syncExternalPlatform('ALL')
    const succeeded = reports.reduce((sum, item) => sum + item.succeeded, 0)
    const failed = reports.reduce((sum, item) => sum + item.failed, 0)
    externalMessage.value = `同步完成：成功 ${succeeded} 次，失败 ${failed} 次`
    await loadExternalStatus()
  } catch {
    externalMessage.value = '同步失败，请检查后端或适配器配置'
  } finally {
    externalSyncing.value = false
  }
}

async function loadAlerts() {
  try {
    alerts.value = await listAlerts({ limit: 10 })
  } catch {
    alerts.value = []
  }
}

async function confirmAlert(alert: AlertRecord) {
  ackPendingId.value = alert.alertId
  alertMessage.value = ''
  try {
    const updated = await acknowledgeAlert(
      alert.alertId,
      session.displayName || '基地管理员',
    )
    alerts.value = alerts.value.map((item) =>
      item.alertId === updated.alertId ? updated : item,
    )
    alertMessage.value = `预警 #${updated.alertId} 已确认`
    await loadAlerts()
  } catch {
    alertMessage.value = '确认失败，请检查权限或服务状态'
  } finally {
    ackPendingId.value = null
  }
}

onMounted(async () => {
  try {
    dashboard.value = await getGovernmentDashboard()
  } catch {
    errorMessage.value = '治理驾驶舱数据暂不可用'
  }
  await loadAlerts()
  await loadChainStatus()
  await loadExternalStatus()
  freshnessTimer = window.setInterval(() => {
    freshnessNow.value = Date.now()
  }, 10_000)
})

onBeforeUnmount(() => {
  window.clearInterval(freshnessTimer)
})
</script>

<template>
  <div class="governance-page">
    <div class="page-heading">
      <div>
        <h2>产业治理驾驶舱</h2>
        <p>示范项目、质量风险和产业带动指标</p>
      </div>
      <div class="governance-heading__actions">
        <DataFreshnessTag
          :mode="governanceFreshness.mode"
          :label="governanceFreshness.label"
        />
      </div>
    </div>

    <p v-if="errorMessage" class="empty-state">{{ errorMessage }}</p>

    <div class="metric-grid">
      <article v-for="item in indicators" :key="item.label" class="metric">
        <span class="metric__label">
          <component :is="item.icon" :size="17" />
          {{ item.label }}
        </span>
        <span class="metric__value">
          {{ item.value }}
          <span class="metric__unit">{{ item.unit }}</span>
        </span>
      </article>
    </div>

    <div class="two-column">
      <section class="panel">
        <div class="panel__header">
          <h3>示范项目建设进度</h3>
        </div>
        <div class="panel__body">
          <dl class="data-list">
            <div class="data-list__row">
              <dt>一期温室建设</dt>
              <dd>已完成</dd>
            </div>
            <div class="data-list__row">
              <dt>设备接入和标定</dt>
              <dd>进行中</dd>
            </div>
            <div class="data-list__row">
              <dt>质量追溯上线</dt>
              <dd>试运行</dd>
            </div>
          </dl>
        </div>
      </section>

      <section class="panel">
        <div class="panel__header">
          <h3>风险预警</h3>
        </div>
        <div class="panel__body">
          <dl class="data-list">
            <div v-for="item in riskItems" :key="item.label" class="data-list__row">
              <dt>{{ item.label }}</dt>
              <dd :class="{ 'governance-risk': item.value > 0 }">
                {{ item.value }} {{ item.unit }}
              </dd>
            </div>
          </dl>
          <p v-if="dashboard" class="governance-source">
            统计自设备在线状态、环境周期链上状态和扫码风控记录，更新时间
            {{ formatLocalTime(dashboard.generatedAt, true) }}
          </p>
        </div>
      </section>
    </div>

    <section class="panel governance-chain-panel">
      <div class="panel__header">
        <div>
          <h3>
            <Link2 :size="17" />
            链上存证
          </h3>
          <p>存证联通状态与待补偿交易，失败记录可手工重试上链</p>
        </div>
        <StatusTag :tone="chainTone">{{ chainTagText }}</StatusTag>
      </div>
      <div class="panel__body">
        <div class="governance-chain-overview">
          <div class="governance-chain-overview__item">
            <span>存证模式</span>
            <strong :class="chainModeTone">{{ chainModeLabel }}</strong>
          </div>
          <div class="governance-chain-overview__item">
            <span>合约地址</span>
            <code
              class="governance-chain-address"
              :class="{ 'is-missing': !chainStatus?.adapter.contractAddress }"
            >
              {{ contractAddress }}
            </code>
            <small>{{ chainContractHint }}</small>
          </div>
        </div>
        <dl class="data-list governance-chain-pending">
          <div
            v-for="item in pendingProofItems"
            :key="item.label"
            class="data-list__row"
          >
            <dt>{{ item.label }}</dt>
            <dd :class="{ 'governance-risk': item.value > 0 }">
              {{ item.value }} 条
            </dd>
          </div>
        </dl>
        <p class="governance-source">{{ chainMessage }}</p>
        <button
          v-if="canCompensate"
          class="secondary-button"
          type="button"
          :disabled="compensating || !hasPendingProof"
          @click="runCompensation"
        >
          {{
            compensating
              ? '补偿中'
              : hasPendingProof
                ? '立即补偿'
                : '无待补偿存证'
          }}
        </button>
      </div>
    </section>

    <section class="panel governance-external-panel">
      <div class="panel__header">
        <div>
          <h3>
            <CloudSun :size="17" />
            外部平台对接
          </h3>
          <p>京东农场、气象与短信通道的状态、重试与调用留痕</p>
        </div>
        <button
          v-if="canSyncExternal"
          class="secondary-button"
          type="button"
          :disabled="externalSyncing"
          @click="runExternalSync"
        >
          <RefreshCw :size="14" />
          {{ externalSyncing ? '同步中' : '立即同步' }}
        </button>
      </div>
      <div class="panel__body">
        <ul v-if="externalStatus.length > 0" class="governance-external-list">
          <li v-for="item in externalStatus" :key="item.platform">
            <div class="governance-external-body">
              <strong>{{ item.label }}</strong>
              <small>{{ externalStatusText(item) }}</small>
              <small v-if="item.lastMessage" class="is-message">
                {{ item.lastMessage }}
              </small>
            </div>
            <StatusTag :tone="item.mode === 'rest' ? 'warning' : 'online'">
              {{ item.mode === 'rest' ? '真实接口' : '模拟适配' }}
            </StatusTag>
            <StatusTag
              :tone="item.lastStatus === 'FAILED' ? 'offline' : 'online'"
            >
              {{
                item.lastStatus === 'SUCCESS'
                  ? '正常'
                  : item.lastStatus === 'FAILED'
                    ? '异常'
                    : '未调用'
              }}
            </StatusTag>
          </li>
        </ul>
        <p v-else class="empty-state">外部平台状态暂不可用</p>

        <ul v-if="externalLogs.length > 0" class="governance-external-logs">
          <li v-for="log in externalLogs" :key="log.logId">
            <span>{{ log.createdAt.replace('T', ' ').slice(11, 19) }}</span>
            <strong>{{ log.label }}</strong>
            <span>{{ log.requestNo }}</span>
            <em :class="{ 'is-failed': log.status !== 'SUCCESS' }">
              {{ log.status === 'SUCCESS' ? '成功' : '失败' }} · {{ log.attempts }} 次尝试
              · {{ log.durationMs }} ms
            </em>
          </li>
        </ul>
        <p v-if="externalMessage" class="governance-source">
          {{ externalMessage }}
        </p>
      </div>
    </section>

    <section class="panel governance-alert-panel">
      <div class="panel__header">
        <div>
          <h3>最近预警</h3>
          <p>遥测超出告警范围时自动记录，可在此确认处理</p>
        </div>
        <StatusTag :tone="pendingAlertCount > 0 ? 'warning' : 'online'">
          {{
            pendingAlertCount > 0
              ? `${pendingAlertCount} 条待处理`
              : '暂无待处理'
          }}
        </StatusTag>
      </div>
      <div class="panel__body">
        <ul v-if="alerts.length > 0" class="governance-alert-list">
          <li
            v-for="alert in alerts"
            :key="alert.alertId"
            :class="{ 'is-acknowledged': alert.status !== 'PENDING' }"
          >
            <span
              class="governance-alert-level"
              :class="alert.alertLevel === 'HIGH' ? 'is-high' : 'is-low'"
            >
              {{ alert.alertLevel === 'HIGH' ? '高于上限' : '低于下限' }}
            </span>
            <div class="governance-alert-body">
              <strong>{{ alert.message }}</strong>
              <small>
                {{ alert.greenhouseCode }} ·
                {{ alert.createdAt.replace('T', ' ').slice(0, 16) }}
                {{
                  alert.acknowledgedBy
                    ? `· 已由 ${alert.acknowledgedBy} 确认`
                    : ''
                }}
              </small>
            </div>
            <button
              v-if="canAcknowledge && alert.status === 'PENDING'"
              class="secondary-button"
              type="button"
              :disabled="ackPendingId === alert.alertId"
              @click="confirmAlert(alert)"
            >
              {{ ackPendingId === alert.alertId ? '处理中' : '确认处理' }}
            </button>
            <em v-else>
              {{ alert.status === 'PENDING' ? '待处理' : '已确认' }}
            </em>
          </li>
        </ul>
        <p v-else class="empty-state">暂无预警记录</p>
        <p v-if="alertMessage" class="governance-source">
          {{ alertMessage }}
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.governance-page {
  display: grid;
  gap: 16px;
}

.governance-heading__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.governance-page :deep(.panel__body) {
  padding: 14px;
}

.governance-page :deep(.data-list) {
  gap: 0;
}

.governance-page :deep(.data-list__row) {
  min-height: 40px;
  padding: 0;
}

.governance-page :deep(.data-list__row:last-child) {
  padding-bottom: 0;
}

.governance-chain-panel {
  margin-top: 0;
}

.governance-chain-panel .panel__header {
  align-items: flex-start;
}

.governance-chain-panel .panel__header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.governance-chain-panel .panel__header p {
  margin: 5px 0 0;
  color: #65736c;
  font-size: 12px;
  font-weight: 400;
}

.governance-chain-address {
  overflow: hidden;
  color: #267a58;
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.governance-chain-address.is-missing {
  color: #8a6b2f;
  word-break: break-all;
}

.governance-chain-overview {
  display: grid;
  grid-template-columns: minmax(170px, 0.72fr) minmax(0, 1.28fr);
  gap: 10px;
  margin-bottom: 12px;
}

.governance-chain-overview__item {
  min-width: 0;
  padding: 10px 11px;
  background: #f6faf7;
  border: 1px solid #e0e9e3;
  border-radius: 7px;
}

.governance-chain-overview__item span,
.governance-chain-overview__item strong,
.governance-chain-overview__item code,
.governance-chain-overview__item small {
  display: block;
}

.governance-chain-overview__item > span {
  color: #708078;
  font-size: 10px;
}

.governance-chain-overview__item strong {
  margin-top: 5px;
  font-size: 12px;
}

.governance-chain-overview__item strong.is-real {
  color: #267a58;
}

.governance-chain-overview__item strong.is-simulated {
  color: #9d6918;
}

.governance-chain-overview__item strong.is-unknown {
  color: #75827a;
}

.governance-chain-overview__item code {
  margin-top: 5px;
}

.governance-chain-overview__item small {
  margin-top: 5px;
  color: #63736a;
  font-size: 10px;
  line-height: 1.45;
}

.governance-chain-pending {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 22px;
}

.governance-chain-panel .secondary-button {
  margin-top: 12px;
}

.governance-external-panel {
  margin-top: 0;
}

.governance-external-panel .panel__header {
  align-items: flex-start;
}

.governance-external-panel .panel__header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.governance-external-panel .panel__header p {
  margin: 5px 0 0;
  color: #65736c;
  font-size: 12px;
  font-weight: 400;
}

.governance-external-panel .panel__header button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.governance-external-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.governance-external-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #f8fbf9;
  border: 1px solid #e1e9e3;
  border-radius: 8px;
}

.governance-external-body strong {
  display: block;
  color: #26382e;
  font-size: 13px;
}

.governance-external-body small {
  display: block;
  margin-top: 4px;
  color: #75827a;
  font-size: 11px;
}

.governance-external-body small.is-message {
  color: #4f6b5a;
}

.governance-external-logs {
  display: grid;
  gap: 6px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.governance-external-logs li {
  display: grid;
  grid-template-columns: auto 88px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  color: #62736a;
  font-size: 11px;
}

.governance-external-logs em {
  color: #35704f;
  font-style: normal;
}

.governance-external-logs em.is-failed {
  color: #a14e48;
}

.governance-alert-panel .panel__header {
  align-items: flex-start;
}

.governance-alert-panel {
  margin-top: 0;
}

.governance-alert-panel .panel__header p {
  margin: 5px 0 0;
  color: #65736c;
  font-size: 12px;
  font-weight: 400;
}

.governance-alert-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.governance-alert-list li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: #fdf7f6;
  border: 1px solid #ecd6d3;
  border-radius: 8px;
}

.governance-alert-list li.is-acknowledged {
  background: #f7faf8;
  border-color: #e1e9e3;
}

.governance-alert-level {
  padding: 3px 8px;
  color: #a14e48;
  background: #faeceb;
  border: 1px solid #e5c6c3;
  border-radius: 999px;
  font-size: 10px;
  white-space: nowrap;
}

.governance-alert-level.is-low {
  color: #9d6918;
  background: #fbf2df;
  border-color: #ead19a;
}

.governance-alert-body strong,
.governance-alert-body small {
  display: block;
}

.governance-alert-body strong {
  color: #26382e;
  font-size: 13px;
}

.governance-alert-body small {
  margin-top: 4px;
  color: #75827a;
  font-size: 11px;
}

.governance-alert-list li > em {
  color: #75827a;
  font-size: 11px;
  font-style: normal;
  white-space: nowrap;
}

.governance-risk {
  color: #a14e48;
  font-weight: 700;
}

.governance-source {
  margin: 10px 0 0;
  color: #5e6f66;
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .governance-chain-overview,
  .governance-chain-pending {
    grid-template-columns: 1fr;
  }
}
</style>
