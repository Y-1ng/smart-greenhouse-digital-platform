<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import { useRoute } from 'vue-router'
import {
  AlertTriangle,
  ArrowRight,
  Blocks,
  CircleCheck,
  Cpu,
  Droplets,
  Fan,
  Layers3,
  LayoutGrid,
  Leaf,
  Lightbulb,
  LocateFixed,
  Network,
  Pause,
  Play,
  Plus,
  RefreshCw,
  Rotate3d,
  ShieldCheck,
  SlidersHorizontal,
  Sprout,
  Thermometer,
  Zap,
  X,
} from 'lucide-vue-next'
import DataFreshnessTag from '@/components/DataFreshnessTag.vue'
import GreenhouseManagerDialog from '@/components/greenhouse/GreenhouseManagerDialog.vue'
import GreenhouseScene from '@/components/greenhouse/GreenhouseScene.vue'
import {
  createControlCommand,
  getControlCommand,
} from '@/api/modules/control'
import {
  createWaterFertilizerPlan,
  executeWaterFertilizerPlan,
  listWaterFertilizerPlans,
} from '@/api/modules/waterFertilizer'
import {
  attestEnvironmentCycle,
  getEnvironmentTrend,
  getTelemetryHistory,
  listEnvironmentThresholds,
  updateEnvironmentThreshold,
} from '@/api/modules/environment'
import {
  getGreenhouseOverview,
  listGreenhouses,
} from '@/api/modules/greenhouses'
import {
  greenhouseDevices,
  greenhouseHotspots,
  greenhouseSites,
} from '@/data/greenhouse'
import type {
  GreenhouseHotspot,
  GreenhouseSite,
  SceneMode,
} from '@/types/greenhouse'
import type {
  ControlCommandResult,
  EnvironmentThreshold,
  EnvironmentTrend,
  GreenhouseOverview,
  GreenhouseSummary,
  WaterFertilizerPlan,
} from '@/types/api'
import { useSessionStore } from '@/stores/session'

interface GreenhouseSceneExpose {
  resetView: () => void
  focusHotspot: (hotspotId: string) => void
}

const sceneRef = ref<GreenhouseSceneExpose | null>(null)
const route = useRoute()
const pageSection = computed(() => String(route.meta.section ?? 'environment'))
const session = useSessionStore()
const availableSites = ref<GreenhouseSite[]>([...greenhouseSites])
// 路由切换会重建视图（App.vue 用 currentRoute.name 做 key），
// 选中的温室必须落在会话里，否则换个页面再回来就退回 1 号温室。
const SELECTED_SITE_STORAGE_KEY = 'smart-greenhouse-selected-site'

function readStoredSiteCode(): string {
  try {
    return window.sessionStorage.getItem(SELECTED_SITE_STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

function resolveInitialSite(): GreenhouseSite {
  const storedCode = readStoredSiteCode()
  return (
    greenhouseSites.find((site) => site.code === storedCode)
    ?? greenhouseSites[0]
  )
}

const selectedSite = ref<GreenhouseSite>(resolveInitialSite())

watch(
  () => selectedSite.value.code,
  (code) => {
    if (!code) {
      return
    }
    try {
      window.sessionStorage.setItem(SELECTED_SITE_STORAGE_KEY, code)
    } catch {
      // 无痕模式等禁用存储的场景忽略即可，不影响功能
    }
  },
  { immediate: true },
)
const selectedOverview = ref<GreenhouseOverview | null>(null)
// 记录当前 selectedOverview 属于哪个温室，用于判断失败时能否沿用旧数据
const overviewSiteCode = ref('')
const greenhouseManagerOpen = ref(false)
const sceneMode = ref<SceneMode>('overview')
const autoRotate = ref(false)
const linkedMetric = ref('')
const selectedHotspot = ref<GreenhouseHotspot>(greenhouseHotspots[0])
const apiConnected = ref(false)
const syncError = ref('')
const initialLoading = ref(true)
const commandMessage = ref('')
const waterPlanRecords = ref<WaterFertilizerPlan[]>([])
const waterPlanMessage = ref('')
const waterPlanPendingNo = ref('')
const selectedWaterPlanNo = ref('')
const waterPlanFormOpen = ref(false)
const waterPlanCreating = ref(false)
const waterPlanFormError = ref('')
const waterPlanForm = ref({
  time: '15:30',
  zoneName: 'A区',
  waterVolume: 2.4,
  recipeVersion: 'V3',
  ecTarget: 1.8,
  phTarget: 6.2,
})
const environmentTrend = ref<EnvironmentTrend | null>(null)
const trendLimit = ref(24)
const trendLoading = ref(false)
const attestingCycle = ref(false)
const cycleAttestMessage = ref('')
const cycleAttestError = ref(false)
// 后端历史遥测负责铺满时间窗，本地缓冲负责接口不可用时的连续采样。
const liveRefreshSeconds = 20
// 展示窗口是显示偏好：切到别的页面再回来、刷新页面都不该跳回 30 分钟
const LIVE_WINDOW_STORAGE_KEY = 'smart-greenhouse-live-window'
const LIVE_WINDOW_OPTIONS = [5, 15, 30]

function readStoredLiveWindow(): number {
  try {
    const parsed = Number(window.sessionStorage.getItem(LIVE_WINDOW_STORAGE_KEY))
    return LIVE_WINDOW_OPTIONS.includes(parsed) ? parsed : 30
  } catch {
    return 30
  }
}

const liveWindowMinutes = ref(readStoredLiveWindow())
const telemetryHistory = ref<LiveSample[]>([])
const telemetryHistoryLoading = ref(false)
const telemetryHistoryError = ref(false)
const liveSamples = ref<LiveSample[]>([])
const liveRefreshing = ref(false)
const liveSamplingPaused = ref(false)
const samplingNow = ref(Date.now())
const nextLiveSampleAt = ref(Date.now() + liveRefreshSeconds * 1000)
const LIVE_SAMPLE_LIMIT = 180

interface LiveSample {
  at: string
  metrics: Record<string, number>
}

function liveStorageKey() {
  return `gh-live-samples-${selectedSite.value.code}`
}

function loadLiveSamples() {
  try {
    const raw = window.sessionStorage.getItem(liveStorageKey())
    liveSamples.value = raw ? (JSON.parse(raw) as LiveSample[]) : []
  } catch {
    liveSamples.value = []
  }
}

function saveLiveSamples() {
  try {
    window.sessionStorage.setItem(
      liveStorageKey(),
      JSON.stringify(liveSamples.value),
    )
  } catch {
    // 浏览器禁用存储时退化为内存缓存，不影响图表
  }
}

function recordLiveSample() {
  const metrics = selectedOverview.value?.metrics
  if (!metrics) {
    return
  }
  const at = new Date().toISOString()
  // 同一秒内多次刷新（例如手动刷新叠加定时刷新）只记一个点
  if (liveSamples.value.at(-1)?.at.slice(0, 19) === at.slice(0, 19)) {
    return
  }
  const cleaned: Record<string, number> = {}
  Object.entries(metrics).forEach(([key, value]) => {
    const numeric = Number(value)
    if (Number.isFinite(numeric)) {
      cleaned[key] = numeric
    }
  })
  liveSamples.value = [...liveSamples.value, { at, metrics: cleaned }]
    .slice(-LIVE_SAMPLE_LIMIT)
  saveLiveSamples()
}

function mergeLiveSamples(
  serverSamples: LiveSample[],
  localSamples: LiveSample[],
) {
  const latestServerTime = serverSamples.at(-1)?.at
    ? new Date(serverSamples.at(-1)!.at).getTime()
    : Number.NEGATIVE_INFINITY
  const samples = new Map<number, LiveSample>()
  serverSamples.forEach((sample) => {
    const timestamp = new Date(sample.at).getTime()
    if (Number.isFinite(timestamp)) {
      samples.set(timestamp, sample)
    }
  })
  localSamples.forEach((sample) => {
    const timestamp = new Date(sample.at).getTime()
    if (Number.isFinite(timestamp) && timestamp >= latestServerTime - 1_000) {
      samples.set(timestamp, sample)
    }
  })
  return [...samples.values()].sort(
    (left, right) =>
      new Date(left.at).getTime() - new Date(right.at).getTime(),
  )
}

function formatClock(timestamp: number, withSeconds = false) {
  const date = new Date(timestamp)
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  if (!withSeconds) {
    return `${hh}:${mm}`
  }
  return `${hh}:${mm}:${String(date.getSeconds()).padStart(2, '0')}`
}
const devicePending = ref<Record<string, boolean>>({})
const recipeOpen = ref(false)
const ventilationPending = ref(false)
const thresholds = ref<EnvironmentThreshold[]>([])
const thresholdDialogOpen = ref(false)
const thresholdDialogRef = ref<HTMLElement | null>(null)
const activeMetricCode = ref('temperature')
const thresholdSaving = ref(false)
const thresholdMessage = ref('')
const thresholdMessageIsError = ref(false)
const thresholdForm = ref({
  targetMin: 18,
  targetMax: 28,
  alertMin: 12,
  alertMax: 32,
  controlAction: 'VENTILATION',
  enabled: true,
})
let thresholdTrigger: HTMLElement | null = null
const deviceSwitches = ref<Record<string, boolean>>(
  Object.fromEntries(
    greenhouseDevices.map((device) => [device.id, device.active]),
  ),
)

const canManageGreenhouses = computed(() =>
  ['ADMIN', 'OPERATOR'].includes(session.roleCode),
)

const thresholdFallbacks: Record<
  string,
  {
    targetMin: number
    targetMax: number
    alertMin: number
    alertMax: number
    controlAction: string
  }
> = {
  temperature: {
    targetMin: 18,
    targetMax: 28,
    alertMin: 12,
    alertMax: 32,
    controlAction: 'VENTILATION',
  },
  humidity: {
    targetMin: 55,
    targetMax: 80,
    alertMin: 40,
    alertMax: 90,
    controlAction: 'MIST',
  },
  light: {
    targetMin: 12000,
    targetMax: 30000,
    alertMin: 5000,
    alertMax: 45000,
    controlAction: 'SUPPLEMENT_LIGHT',
  },
  soil_moisture: {
    targetMin: 35,
    targetMax: 55,
    alertMin: 25,
    alertMax: 65,
    controlAction: 'IRRIGATION',
  },
}

const metricUnits: Record<string, string> = {
  temperature: '℃',
  humidity: '%',
  light: 'lx',
  soil_moisture: '%',
}

const controlActions = [
  { value: 'VENTILATION', label: '通风降温' },
  { value: 'MIST', label: '微雾增湿' },
  { value: 'SUPPLEMENT_LIGHT', label: '补光调节' },
  { value: 'IRRIGATION', label: '水肥灌溉' },
  { value: 'CURTAIN', label: '卷帘调节' },
  { value: 'HEATING', label: '加热保温' },
  { value: 'DEHUMIDIFY', label: '除湿通风' },
]

const chainFeature = {
  index: '01',
  title: '周期根哈希',
  description: '环境数据按周期生成哈希链，仅将根哈希写入联盟链。',
}

const blockHeight = ref(1_284_602)
let blockHeightTimer: number | undefined
let liveRefreshTimer: number | undefined
let liveCountdownTimer: number | undefined

const lastRefreshedAt = ref<Date | null>(null)
const freshnessNow = ref(Date.now())
let freshnessTimer: number | undefined

const liveRefreshLabel = computed(() =>
  lastRefreshedAt.value
    ? `数据更新 ${lastRefreshedAt.value.toLocaleTimeString('zh-CN', {
        hour12: false,
      })}`
    : `每 ${liveRefreshSeconds} 秒自动刷新`,
)

const dataFreshness = computed(() => {
  if (initialLoading.value) {
    return {
      mode: 'loading' as const,
      label: '正在同步实时数据',
    }
  }
  if (!apiConnected.value) {
    return {
      mode: 'demo' as const,
      label: '本地演示数据',
    }
  }
  if (!lastRefreshedAt.value) {
    return {
      mode: 'loading' as const,
      label: '正在同步实时数据',
    }
  }
  const age = Math.max(0, freshnessNow.value - lastRefreshedAt.value.getTime())
  if (age > liveRefreshSeconds * 3_000) {
    return {
      mode: 'stale' as const,
      label: `数据延迟 ${Math.max(1, Math.floor(age / 60_000))} 分钟`,
    }
  }
  return {
    mode: 'real' as const,
    label: `实时数据 · ${lastRefreshedAt.value.toLocaleTimeString('zh-CN', {
      hour12: false,
    })}`,
  }
})

const liveSamplingCountdown = computed(() => {
  if (liveSamplingPaused.value) {
    return '已暂停'
  }
  if (liveRefreshing.value) {
    return '采样中'
  }
  const remaining = Math.max(
    0,
    Math.ceil((nextLiveSampleAt.value - samplingNow.value) / 1000),
  )
  return remaining > 0 ? `${remaining} 秒` : '即将采样'
})

const liveSamplingStatus = computed(() => {
  if (liveSamplingPaused.value) {
    return {
      tone: 'is-paused',
      label: '自动采样已暂停',
    }
  }
  if (telemetryHistoryLoading.value && telemetryHistory.value.length === 0) {
    return {
      tone: 'is-loading',
      label: '读取历史采样',
    }
  }
  if (liveRefreshing.value) {
    return {
      tone: 'is-loading',
      label: '正在同步实时值',
    }
  }
  if (telemetryHistoryError.value) {
    return {
      tone: 'is-warning',
      label: '使用本地采样',
    }
  }
  return {
    tone: 'is-live',
    label: '实时采样中',
  }
})

function buildDemoCycles(baseTemperature: number, baseTime: Date) {
  return Array.from({ length: 5 }, (_, index) => {
    const offset = 5 - index
    const time = new Date(baseTime.getTime() - offset * 5 * 60_000)
    return {
      cycleId: -1 - index,
      time: time.toTimeString().slice(0, 5),
      temperature: Number(
        (baseTemperature + Math.sin(index * 1.15) * 1.4 - 0.6).toFixed(1),
      ),
      hash: '',
      hashShort: '本地演示周期',
      confirmed: false,
      demo: true,
    }
  })
}

const chainCycles = computed(() => {
  const points = environmentTrend.value?.points ?? []
  const latestPoint = points[0]
  const realCycles = points.slice(0, 6).map((point) => {
    const hash = point.rootHash ?? ''
    return {
      cycleId: point.cycleId,
      time: point.cycleEnd.slice(11, 16),
      temperature: Number(point.metrics.temperature ?? 0),
      hash,
      hashShort: hash ? `${hash.slice(0, 10)}…${hash.slice(-6)}` : '待生成',
      confirmed: point.chainStatus === 'CONFIRMED',
      demo: false,
    }
  })
  if (realCycles.length > 0) {
    return realCycles
  }
  return buildDemoCycles(
      Number(latestPoint?.metrics.temperature ?? 22.6),
      latestPoint?.cycleEnd ? new Date(latestPoint.cycleEnd) : new Date(),
    )
    .reverse()
})

const chainChartCycles = computed(() => [...chainCycles.value].reverse())

const chainHasDemo = computed(() =>
  chainCycles.value.some((cycle) => cycle.demo),
)

const chainChart = computed(() => {
  const cycles = chainChartCycles.value
  if (cycles.length === 0) {
    return {
      linePath: '',
      areaPath: '',
      points: [] as Array<{ x: number; y: number; cycleId: number }>,
    }
  }

  const values = cycles.map((cycle) => cycle.temperature)
  const minimum = Math.min(...values) - 1
  const maximum = Math.max(...values) + 1
  const left = 28
  const right = 532
  const top = 20
  const bottom = 124
  const span = Math.max(maximum - minimum, 1)
  const points = values.map((value, index) => {
    const x = left + (index / Math.max(values.length - 1, 1)) * (right - left)
    const ratio = Math.min(1, Math.max(0, (value - minimum) / span))
    return {
      x: Number(x.toFixed(1)),
      y: Number((bottom - ratio * (bottom - top)).toFixed(1)),
      cycleId: cycles[index].cycleId,
    }
  })
  const linePath = points
    .map((point, index) => {
      if (index === 0) {
        return `M${point.x} ${point.y}`
      }
      const previous = points[index - 1]
      const controlOffset = (point.x - previous.x) / 2
      return `C${previous.x + controlOffset} ${previous.y} ${point.x - controlOffset} ${point.y} ${point.x} ${point.y}`
    })
    .join(' ')
  const areaPath =
    points.length > 1
      ? `${linePath} L${points.at(-1)?.x} ${bottom} L${points[0].x} ${bottom} Z`
      : ''
  return { linePath, areaPath, points }
})

const chainRootHash = computed(() => {
  const withHash = chainCycles.value.find((cycle) => cycle.hash)
  return withHash?.hash ?? ''
})

const chainConfirmed = computed(
  () => {
    const chainCyclesOnly = chainCycles.value.filter((cycle) => !cycle.demo)
    return (
      chainCyclesOnly.length > 0
      && chainCyclesOnly.every((cycle) => cycle.confirmed)
    )
  },
)

function shortenChainHash(value: string, headLength = 22) {
  if (!value) {
    return ''
  }
  if (value.length <= headLength + 9) {
    return value
  }
  return `${value.slice(0, headLength)}...${value.slice(-6)}`
}

const sceneModes: Array<{
  id: SceneMode
  label: string
  icon: typeof Layers3
}> = [
  { id: 'overview', label: '全景', icon: Layers3 },
  { id: 'environment', label: '环境感知', icon: Leaf },
  { id: 'control', label: '执行设备', icon: Fan },
]

const siteMetricValues: Record<
  string,
  { humidity: string; light: string; soil: string }
> = {
  'gh-01': { humidity: '68.4', light: '18,500', soil: '43.2' },
  'gh-02': { humidity: '69.1', light: '17,800', soil: '45.0' },
  'gh-03': { humidity: '65.8', light: '16,900', soil: '41.6' },
  'gh-04': { humidity: '70.2', light: '19,200', soil: '46.3' },
  'gh-05': { humidity: '67.5', light: '18,100', soil: '42.7' },
  'gh-06': { humidity: '72.1', light: '15,700', soil: '48.5' },
}

function numberOr(value: unknown, fallback: number) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

const metricReadings = computed<Record<string, number>>(() => {
  const apiMetrics = selectedOverview.value?.metrics
  const siteValues =
    siteMetricValues[selectedSite.value.id] ?? siteMetricValues['gh-01']
  return {
    temperature: numberOr(
      apiMetrics?.temperature ?? selectedSite.value.metric.replace('℃', ''),
      22.6,
    ),
    humidity: numberOr(apiMetrics?.humidity ?? siteValues.humidity, 68.4),
    light: numberOr(
      apiMetrics?.light ?? siteValues.light.replace(/,/g, ''),
      18500,
    ),
    soil_moisture: numberOr(apiMetrics?.soil_moisture ?? siteValues.soil, 43.2),
  }
})

const metrics = computed(() => {
  const apiMetrics = selectedOverview.value?.metrics
  const siteValues =
    siteMetricValues[selectedSite.value.id] ?? siteMetricValues['gh-01']
  return [
    {
      key: 'temperature',
      label: '空气温度',
      value:
        apiMetrics?.temperature?.toFixed(1)
        ?? selectedSite.value.metric.replace('℃', ''),
      unit: '℃',
      note: thresholdNote('temperature', '适宜范围 18-28℃'),
      icon: Thermometer,
      tone: 'temperature',
    },
    {
      key: 'humidity',
      label: '空气湿度',
      value: apiMetrics?.humidity?.toFixed(1) ?? siteValues.humidity,
      unit: '%',
      note: thresholdNote('humidity', '较昨日 +3.2%'),
      icon: Droplets,
      tone: 'water',
    },
    {
      key: 'light',
      label: '光照强度',
      value:
        apiMetrics?.light?.toLocaleString('zh-CN', {
          maximumFractionDigits: 0,
        })
        ?? siteValues.light,
      unit: 'lx',
      note: thresholdNote('light', '补光阈值 12,000'),
      icon: Lightbulb,
      tone: 'light',
    },
    {
      key: 'soil_moisture',
      label: '土壤水分',
      value: apiMetrics?.soil_moisture?.toFixed(1) ?? siteValues.soil,
      unit: '%',
      note: thresholdNote('soil_moisture', '下次灌溉 15:30'),
      icon: Sprout,
      tone: 'soil',
    },
  ]
})

function thresholdFor(metricCode: string) {
  return thresholds.value.find((item) => item.metricCode === metricCode)
}

function formatThresholdValue(value: number) {
  return value.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function thresholdNote(metricCode: string, fallback: string) {
  const threshold = thresholdFor(metricCode)
  if (!threshold) {
    return fallback
  }
  const unit = metricUnits[metricCode] ?? ''
  return `目标 ${formatThresholdValue(threshold.targetMin)}-${formatThresholdValue(threshold.targetMax)}${unit}`
}

type MetricStateTone = 'ok' | 'warning' | 'alert' | 'muted'

interface MetricState {
  label: string
  tone: MetricStateTone
}

const metricStates = computed<Record<string, MetricState>>(() => {
  const readings = metricReadings.value
  const states: Record<string, MetricState> = {}
  for (const key of ['temperature', 'humidity', 'light', 'soil_moisture']) {
    const threshold = thresholdFor(key)
    const value = readings[key]
    if (!threshold || !threshold.enabled) {
      states[key] = { label: '未启用', tone: 'muted' }
      continue
    }
    if (value < threshold.alertMin || value > threshold.alertMax) {
      states[key] = { label: '告警', tone: 'alert' }
      continue
    }
    if (value < threshold.targetMin) {
      states[key] = { label: '偏低', tone: 'warning' }
      continue
    }
    if (value > threshold.targetMax) {
      states[key] = { label: '偏高', tone: 'warning' }
      continue
    }
    states[key] = { label: '正常', tone: 'ok' }
  }
  return states
})

const environmentSummary = computed(() => {
  const states = Object.values(metricStates.value)
  const alerts = states.filter((state) => state.tone === 'alert').length
  const warnings = states.filter((state) => state.tone === 'warning').length
  const inactive = states.filter((state) => state.tone === 'muted').length
  if (alerts > 0) {
    return { tone: 'is-offline', label: `${alerts} 项告警` }
  }
  if (warnings > 0) {
    return { tone: 'is-warning', label: `${warnings} 项偏离目标` }
  }
  if (inactive === states.length) {
    return { tone: '', label: '阈值未启用' }
  }
  if (inactive > 0) {
    return { tone: '', label: '部分阈值未启用' }
  }
  return { tone: 'is-ok', label: '全部正常' }
})

const activeMetric = computed(
  () =>
    metrics.value.find((item) => item.key === activeMetricCode.value)
    ?? metrics.value[0],
)

const activeThreshold = computed(() => thresholdFor(activeMetricCode.value))

const metricThresholdCards = computed(() =>
  metrics.value.map((metric) => ({
    ...metric,
    threshold: thresholdFor(metric.key) ?? null,
  })),
)

function controlActionLabel(action: string) {
  return (
    controlActions.find((item) => item.value === action)?.label ?? action
  )
}

function selectThresholdMetric(metricCode: string) {
  activeMetricCode.value = metricCode
  const threshold = thresholdFor(metricCode)
  const fallback =
    thresholdFallbacks[metricCode] ?? thresholdFallbacks.temperature
  thresholdMessage.value = ''
  thresholdMessageIsError.value = false
  thresholdForm.value = threshold
    ? {
        targetMin: threshold.targetMin,
        targetMax: threshold.targetMax,
        alertMin: threshold.alertMin,
        alertMax: threshold.alertMax,
        controlAction: threshold.controlAction,
        enabled: threshold.enabled,
      }
    : { ...fallback, enabled: true }
}

function openThresholdDialog(metricCode: string, event?: MouseEvent) {
  thresholdTrigger =
    event?.currentTarget instanceof HTMLElement ? event.currentTarget : null
  selectThresholdMetric(metricCode)
  thresholdDialogOpen.value = true
  nextTick(() => {
    thresholdDialogRef.value
      ?.querySelector<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), select:not([disabled])',
      )
      ?.focus()
  })
}

function closeThresholdDialog() {
  thresholdDialogOpen.value = false
  thresholdMessage.value = ''
  thresholdMessageIsError.value = false
  nextTick(() => thresholdTrigger?.focus())
}

function trapThresholdFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab' || !thresholdDialogRef.value) {
    return
  }
  const focusable = Array.from(
    thresholdDialogRef.value.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ),
  ).filter((element) => element.offsetParent !== null)
  if (focusable.length === 0) {
    return
  }
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function validateThresholdForm() {
  const form = thresholdForm.value
  const rawValues: Array<number | string> = [
    form.targetMin,
    form.targetMax,
    form.alertMin,
    form.alertMax,
  ]
  if (
    rawValues.some(
      (value) => value === '' || value === null || !Number.isFinite(Number(value)),
    )
  ) {
    return '请完整填写目标范围和告警范围'
  }
  const [targetMin, targetMax, alertMin, alertMax] = rawValues.map(Number)
  if (targetMin > targetMax) {
    return '目标下限不能大于目标上限'
  }
  if (alertMin > targetMin || alertMax < targetMax) {
    return '告警范围必须覆盖目标范围'
  }
  if (alertMin > alertMax) {
    return '告警下限不能大于告警上限'
  }
  if (!form.controlAction) {
    return '请选择控制动作'
  }
  return ''
}

async function saveThreshold() {
  const validationMessage = validateThresholdForm()
  if (validationMessage) {
    thresholdMessage.value = validationMessage
    thresholdMessageIsError.value = true
    return
  }

  const form = thresholdForm.value
  thresholdSaving.value = true
  thresholdMessage.value = ''
  thresholdMessageIsError.value = false
  try {
    const updated = await updateEnvironmentThreshold(activeMetricCode.value, {
      greenhouseCode: selectedSite.value.code,
      targetMin: Number(form.targetMin),
      targetMax: Number(form.targetMax),
      alertMin: Number(form.alertMin),
      alertMax: Number(form.alertMax),
      controlAction: form.controlAction,
      enabled: form.enabled,
    })
    thresholds.value = thresholdFor(updated.metricCode)
      ? thresholds.value.map((item) =>
          item.metricCode === updated.metricCode ? updated : item,
        )
      : [...thresholds.value, updated]
    thresholdForm.value = {
      targetMin: updated.targetMin,
      targetMax: updated.targetMax,
      alertMin: updated.alertMin,
      alertMax: updated.alertMax,
      controlAction: updated.controlAction,
      enabled: updated.enabled,
    }
    thresholdMessage.value = `${activeMetric.value.label}阈值已保存，自动化控制按新范围执行`
    commandMessage.value = `${selectedSite.value.name}${activeMetric.value.label}阈值已更新`
  } catch (error) {
    const responseMessage = (
      error as { response?: { data?: { message?: string } } }
    ).response?.data?.message
    thresholdMessage.value = responseMessage ?? '阈值保存失败，请稍后重试'
    thresholdMessageIsError.value = true
  } finally {
    thresholdSaving.value = false
  }
}

// 本地演示设备：接口未连接、或该温室还没有设备上报时用来兜底，
// 页面上会明确标注为演示设备，不是真实接入。
const demoDevices = () =>
  greenhouseDevices.map((device) => ({
    ...device,
    deviceType: inferDeviceType(device.name),
  }))

// 后端已经连上、但这个温室一台设备都没上报（例如刚新建的大棚、
// 或物联网服务尚未上报）。这种情况不能再让右侧机架整个空掉。
const deviceFallbackActive = computed(
  () => !selectedOverview.value?.devices.length,
)

// 兜底设备在后端并不存在，禁止下发指令，避免弹出"设备不存在"的报错
const deviceCommandLocked = computed(
  () => apiConnected.value && deviceFallbackActive.value,
)

const deviceRackNote = computed(() => {
  if (!apiConnected.value) {
    return '实时接口未连接，当前展示本地演示设备'
  }
  if (deviceFallbackActive.value) {
    return '该温室暂无设备上报，当前展示本地演示设备，不可下发指令'
  }
  return ''
})

const displayDevices = computed(() => {
  if (!apiConnected.value) {
    return demoDevices()
  }
  const overview = selectedOverview.value
  if (!overview?.devices.length) {
    return demoDevices()
  }

  const typeNames: Record<string, string> = {
    FAN: '循环风机',
    VALVE: '水肥阀组',
    GROW_LIGHT: '补光灯组',
    MIST: '微雾喷灌',
    CURTAIN: '卷帘机',
    HEATER: '加热设备',
    DEHUMIDIFIER: '除湿设备',
  }

  return overview.devices.map((device) => {
    const online = device.onlineStatus === 'ONLINE'
    const operating = online && device.operatingState === 'ON'
    return {
      id: device.deviceCode,
      name: typeNames[device.deviceType] ?? device.deviceType,
      location: selectedSite.value.name,
      value: operating ? '运行中' : online ? '待机' : '离线',
      status: operating ? '运行中' : online ? '待机' : '离线',
      tone: operating
        ? ('running' as const)
        : online
          ? ('warning' as const)
          : ('offline' as const),
      active: operating,
      deviceType: device.deviceType,
    }
  })
})

// 后端设备码与本地演示设备码不同，数据首次返回时为开关补齐对应键。
watch(
  displayDevices,
  (devices) => {
    const next = { ...deviceSwitches.value }
    let changed = false
    devices.forEach((device) => {
      if (!(device.id in next)) {
        next[device.id] = device.active
        changed = true
      }
    })
    if (changed) {
      deviceSwitches.value = next
    }
  },
  { immediate: true },
)

const linkageDeviceTypes: Record<string, string> = {
  VENTILATION: 'FAN',
  DEHUMIDIFY: 'FAN',
  MIST: 'MIST',
  SUPPLEMENT_LIGHT: 'GROW_LIGHT',
  IRRIGATION: 'VALVE',
  CURTAIN: 'CURTAIN',
  HEATING: 'HEATER',
}

const linkagePending = ref(false)

const pendingLinkages = computed(() =>
  metrics.value
    .filter((metric) => {
      const tone = metricStates.value[metric.key]?.tone
      return tone === 'warning' || tone === 'alert'
    })
    .map((metric) => {
      const action = thresholdFor(metric.key)?.controlAction ?? ''
      const deviceType = linkageDeviceTypes[action] ?? ''
      const device = displayDevices.value.find(
        (item) => item.deviceType === deviceType && item.tone !== 'offline',
      )
      return {
        key: metric.key,
        metricLabel: metric.label,
        stateLabel: metricStates.value[metric.key]?.label ?? '',
        action,
        actionLabel: controlActionLabel(action),
        deviceType,
        deviceCode: device?.id ?? '',
        deviceLabel: device?.name ?? '',
        available: Boolean(device),
      }
    }),
)

const linkageButtonLabel = computed(() => {
  const first = pendingLinkages.value[0]
  if (!first) {
    return '阈值联动'
  }
  return `阈值联动 · ${first.actionLabel}`
})

const linkageButtonHint = computed(() => {
  if (pendingLinkages.value.length === 0) {
    return '当前指标都在目标范围内，无需联动'
  }
  return pendingLinkages.value
    .map(
      (item) =>
        `${item.metricLabel}${item.stateLabel} → ${item.actionLabel}${item.available ? '' : '（缺少可用设备）'}`,
    )
    .join('；')
})

async function executeThresholdLinkage() {
  const targets = pendingLinkages.value
  if (targets.length === 0 || linkagePending.value) {
    return
  }

  const executable = targets.filter((item) => item.available)
  if (executable.length === 0) {
    commandMessage.value = `缺少可用设备：${targets
      .map((item) => item.deviceType || item.action)
      .join('、')}`
    return
  }

  linkagePending.value = true
  commandMessage.value = `正在按阈值执行 ${executable.length} 项联动...`
  try {
    const results = await Promise.allSettled(
      executable.map((item) =>
        issueCommand(item.deviceCode, commandTypeFor(item.deviceType, true), {
          source: 'THRESHOLD_LINKAGE',
          metricCode: item.key,
          action: item.action,
        }),
      ),
    )
    const succeeded = results.filter(
      (result) =>
        result.status === 'fulfilled'
        && result.value.status === 'SUCCEEDED',
    ).length
    await loadOverview(selectedSite.value)
    const missing = targets.length - executable.length
    if (succeeded === results.length && missing === 0) {
      commandMessage.value = `阈值联动已执行：${executable
        .map((item) => `${item.metricLabel}→${item.actionLabel}`)
        .join('，')}`
    } else {
      commandMessage.value = `阈值联动部分执行：成功 ${succeeded}/${results.length}${
        missing > 0 ? `，${missing} 项缺少设备` : ''
      }`
    }
  } finally {
    linkagePending.value = false
  }
}

const cropDisplay = computed(() => {
  const crop = selectedOverview.value?.crop
  if (!crop) {
    return {
      name: selectedSite.value.crop,
      batch: selectedSite.value.batch,
      plantingDate: '2026-06-12',
      harvestDate: '2026-09-28',
    }
  }
  return {
    name: `${crop.cropName} · ${crop.varietyName}`,
    batch: `CB-${crop.batchId}`,
    plantingDate: crop.plantingDate,
    harvestDate: crop.expectedHarvestDate,
  }
})

const fallbackWaterPlans = [
  {
    planNo: '',
    time: '11:30',
    zone: 'A区 / B区',
    amount: '2.1 m³',
    status: '已完成',
    rawStatus: 'SUCCEEDED',
    real: false,
  },
  {
    planNo: '',
    time: '15:30',
    zone: 'A区',
    amount: '2.4 m³',
    status: '待执行',
    rawStatus: 'PLANNED',
    real: false,
  },
  {
    planNo: '',
    time: '19:00',
    zone: 'B区',
    amount: '1.6 m³',
    status: '待执行',
    rawStatus: 'PLANNED',
    real: false,
  },
]

const waterPlans = computed(() => {
  if (waterPlanRecords.value.length === 0) {
    return fallbackWaterPlans
  }
  return waterPlanRecords.value.map((plan) => ({
    planNo: plan.planNo,
    time: plan.plannedAt.slice(11, 16),
    zone: plan.zoneName,
    amount: `${plan.waterVolume.toFixed(1)} m³`,
    status:
      plan.status === 'SUCCEEDED'
        ? '已完成'
        : plan.status === 'FAILED'
          ? '执行失败'
          : '待执行',
    rawStatus: plan.status,
    real: true,
  }))
})

const selectedWaterPlan = computed(() => {
  const selected = waterPlanRecords.value.find(
    (plan) => plan.planNo === selectedWaterPlanNo.value,
  )
  if (selected) {
    return selected
  }
  return (
    waterPlanRecords.value.find((plan) => plan.status === 'PLANNED')
    ?? waterPlanRecords.value[0]
    ?? null
  )
})

const hasPendingWaterPlan = computed(() =>
  waterPlanRecords.value.some((plan) => plan.status === 'PLANNED'),
)

const waterRecipeSummary = computed(() => {
  const plan = selectedWaterPlan.value
  if (!plan) {
    return '配方 V3 · EC 1.8 · pH 6.2'
  }
  return `配方 ${plan.recipeVersion} · EC ${plan.ecTarget} · pH ${plan.phTarget}`
})

const recipeDetails = computed(() => {
  const plan = selectedWaterPlan.value
  return {
    recipeCode: plan?.recipeCode ?? 'TOMATO-FRUIT',
    recipeVersion: plan?.recipeVersion ?? 'V3',
    ecTarget: plan?.ecTarget ?? 1.8,
    phTarget: plan?.phTarget ?? 6.2,
    zoneName: plan?.zoneName ?? 'A区 / B区',
    waterVolume: plan?.waterVolume ?? 2.1,
    operatorName: plan?.operatorName ?? '基地管理员',
  }
})

const waterCompletion = computed(() => {
  const plans = waterPlans.value
  if (plans.length === 0) {
    return { done: 0, total: 0, percent: 0 }
  }
  const done = plans.filter((plan) => plan.status === '已完成').length
  return {
    done,
    total: plans.length,
    percent: Math.round(done / plans.length * 100),
  }
})

/**
 * 图表时间轴：优先使用每 20 秒采样的实时数据；采样不足 2 个点时
 * 回退到环境周期数据，保证刚打开页面也有基线可看。
 */
const chartTimeline = computed(() => {
  const windowStart = Date.now() - liveWindowMinutes.value * 60_000
  const live = mergeLiveSamples(
    telemetryHistory.value,
    liveSamples.value,
  )
    .filter((sample) => new Date(sample.at).getTime() >= windowStart)
    .map((sample) => ({ at: sample.at, metrics: sample.metrics }))
  if (live.length >= 2) {
    return { source: 'live' as const, points: live }
  }
  const cycles = (environmentTrend.value?.points ?? []).map((point) => ({
    at: point.cycleEnd,
    metrics: point.metrics,
  }))
  return {
    source: cycles.length > 0 ? ('cycle' as const) : ('none' as const),
    points: cycles,
  }
})

const liveSampleCount = computed(() => chartTimeline.value.points.length)

const hotspotMetricSeries: Record<
  string,
  Array<{ key: string; label: string; unit: string; digits: number }>
> = {
  'air-sensor': [
    { key: 'temperature', label: '空气温度', unit: '℃', digits: 1 },
    { key: 'humidity', label: '空气湿度', unit: '%', digits: 1 },
  ],
  'light-sensor': [
    { key: 'light', label: '光照强度', unit: 'lx', digits: 0 },
  ],
  'soil-sensor': [
    { key: 'soil_moisture', label: '土壤水分', unit: '%', digits: 1 },
  ],
}

const hotspotRecentEvents = computed(() => {
  const hotspot = selectedHotspot.value
  const definitions = hotspotMetricSeries[hotspot.id] ?? []
  if (definitions.length > 0) {
    const events = chartTimeline.value.points
      .slice(-3)
      .reverse()
      .flatMap((point, pointIndex) =>
        definitions.flatMap((definition) => {
          const rawValue = Number(point.metrics[definition.key])
          if (!Number.isFinite(rawValue)) {
            return []
          }
          return [
            {
              id: `${definition.key}-${point.at}-${pointIndex}`,
              time: formatClock(new Date(point.at).getTime()),
              text: `${definition.label} ${rawValue.toFixed(definition.digits)}${definition.unit}`,
            },
          ]
        }),
      )
    if (events.length > 0) {
      return events.slice(0, 4)
    }
  }

  const latestCycle = environmentTrend.value?.points.at(-1)
  const latestCycleTime = latestCycle?.cycleEnd
    ? latestCycle.cycleEnd.replace('T', ' ').slice(11, 16)
    : '等待周期'
  const events = [
    {
      id: 'current',
      time: '当前',
      text: `${hotspot.label} ${hotspot.value} ${hotspot.unit}`,
    },
    {
      id: 'status',
      time: '状态',
      text: hotspot.status,
    },
    {
      id: 'source',
      time: latestCycleTime,
      text: hotspot.category === 'sensor' ? '最近环境周期已同步' : '设备状态已同步',
    },
  ]
  if (hotspot.category === 'actuator' && commandMessage.value) {
    events.push({
      id: 'command',
      time: '指令',
      text: commandMessage.value,
    })
  }
  return events
})

const trendMetricCards = computed(() => {
  const timeline = chartTimeline.value.points
  const definitions = [
    {
      key: 'temperature',
      label: '空气温度',
      unit: '℃',
      icon: Thermometer,
      tone: 'temperature',
      fallback: selectedOverview.value?.metrics?.temperature ?? 22.6,
      padding: 1.5,
    },
    {
      key: 'humidity',
      label: '空气湿度',
      unit: '%',
      icon: Droplets,
      tone: 'water',
      fallback: selectedOverview.value?.metrics?.humidity ?? 68.4,
      padding: 3,
    },
    {
      key: 'light',
      label: '光照强度',
      unit: 'lx',
      icon: Lightbulb,
      tone: 'light',
      fallback: selectedOverview.value?.metrics?.light ?? 18500,
      padding: 1500,
    },
    {
      key: 'soil_moisture',
      label: '土壤水分',
      unit: '%',
      icon: Sprout,
      tone: 'soil',
      fallback: selectedOverview.value?.metrics?.soil_moisture ?? 43.2,
      padding: 2.5,
    },
  ]

  return definitions.map((definition) => {
    const values = timeline.map((item) =>
      Number(item.metrics[definition.key] ?? definition.fallback),
    )
    const chartValues = values.length > 0 ? values : [definition.fallback]
    const times = timeline.map((item) => new Date(item.at).getTime())
    const coordinates = buildTrendCoordinates(
      chartValues,
      times,
      definition.padding,
    )
    const path = buildSmoothTrendPath(coordinates)
    return {
      ...definition,
      current: chartValues.at(-1) ?? definition.fallback,
      minimumValue: Math.min(...chartValues),
      maximumValue: Math.max(...chartValues),
      path,
      areaPath: path && coordinates.length > 1
        ? `${path} L${coordinates.at(-1)!.x} 88 L${coordinates[0].x} 88 Z`
        : '',
      dot: coordinates.at(-1),
      hasLine: chartValues.length > 1,
      axisLabels: buildTimeAxisLabels(times),
      updatedAt: times.length > 0
        ? formatClock(times[times.length - 1])
        : '等待采样',
    }
  })
})

const metricSparklineMap = computed(() =>
  Object.fromEntries(
    trendMetricCards.value.map((card) => [card.key, card.path]),
  ),
)

interface TrendChartSummaryInput {
  key: string
  label: string
  unit: string
  current: number
  minimumValue: number
  maximumValue: number
  axisLabels: string[]
}

function formatTrendValue(card: TrendChartSummaryInput, value: number) {
  const digits = card.key === 'light' ? 0 : 1
  return `${value.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })} ${card.unit}`
}

function trendChartSummary(card: TrendChartSummaryInput) {
  const start = card.axisLabels[0] || '暂无'
  const end = card.axisLabels[2] || '暂无'
  return `${card.label}趋势。当前 ${formatTrendValue(card, card.current)}，最低 ${formatTrendValue(card, card.minimumValue)}，最高 ${formatTrendValue(card, card.maximumValue)}，时间范围 ${start} 至 ${end}。`
}

/**
 * 横坐标按真实时间戳分布，纵坐标按窗口内实际波动自动缩放（留最小跨度）。
 */
function buildTrendCoordinates(
  values: number[],
  times: number[],
  padding: number,
) {
  const width = 280
  const left = 20
  const top = 12
  const bottom = 88

  const minValue = Math.min(...values)
  const maxValue = Math.max(...values)
  const span = Math.max(maxValue - minValue, padding)
  const center = (minValue + maxValue) / 2
  const low = center - span / 2
  const high = center + span / 2

  const validTimes = times.map((value) =>
    Number.isFinite(value) ? value : Date.now(),
  )
  const startTime = validTimes.length > 0
    ? Math.min(...validTimes)
    : Date.now()
  const endTime = validTimes.length > 0
    ? Math.max(...validTimes)
    : startTime
  const timeSpan = endTime - startTime

  return values.map((value, index) => {
    const timeRatio = timeSpan > 0
      ? (validTimes[index] - startTime) / timeSpan
      : 0.5
    const x = left + timeRatio * width
    const ratio = Math.min(
      1,
      Math.max(0, (value - low) / (high - low)),
    )
    const y = bottom - ratio * (bottom - top)
    return {
      x: Number(x.toFixed(1)),
      y: Number(y.toFixed(1)),
    }
  })
}

function buildSmoothTrendPath(
  points: Array<{ x: number; y: number }>,
) {
  if (points.length === 0) {
    return ''
  }
  if (points.length === 1) {
    return `M${points[0].x} ${points[0].y}`
  }
  let path = `M${points[0].x} ${points[0].y}`
  for (let index = 1; index < points.length; index += 1) {
    const previous = points[index - 1]
    const current = points[index]
    const controlOffset = (current.x - previous.x) * 0.42
    path +=
      ` C${(previous.x + controlOffset).toFixed(1)} ${previous.y},`
      + ` ${(current.x - controlOffset).toFixed(1)} ${current.y},`
      + ` ${current.x} ${current.y}`
  }
  return path
}

function buildTimeAxisLabels(times: number[]) {
  if (times.length === 0) {
    return ['', '', '']
  }
  const start = Math.min(...times)
  const end = Math.max(...times)
  const withSeconds = end - start < 10 * 60_000
  if (start === end) {
    // 只有一个采样点时圆点居中，时间标签也放中间
    return ['', formatClock(start, withSeconds), '']
  }
  return [
    formatClock(start, withSeconds),
    formatClock(Math.round((start + end) / 2), withSeconds),
    formatClock(end, withSeconds),
  ]
}

const activeLayerLabel = computed(
  () =>
    sceneModes.find((mode) => mode.id === sceneMode.value)?.label ?? '全景',
)

function changeMode(mode: SceneMode) {
  sceneMode.value = mode
  const nextHotspot = greenhouseHotspots.find((hotspot) => {
    if (mode === 'overview') return true
    if (mode === 'environment') return hotspot.category === 'sensor'
    return hotspot.category === 'actuator'
  })
  if (nextHotspot) selectHotspot(nextHotspot)
}

function selectHotspot(hotspot: GreenhouseHotspot) {
  selectedHotspot.value = hotspot
  nextTick(() => sceneRef.value?.focusHotspot(hotspot.id))
}

function focusDeviceInScene(device: { name: string }) {
  const hotspotMap: Record<string, string> = {
    循环风机: 'fan-actuator',
    水肥阀组: 'drip-actuator',
    补光灯组: 'growlight-actuator',
    微雾喷灌: 'mist-actuator',
    卷帘机: 'curtain-actuator',
  }
  const hotspot = greenhouseHotspots.find(
    (item) => item.id === hotspotMap[device.name],
  )
  if (hotspot) selectHotspot(hotspot)
}

function resetScene() {
  sceneRef.value?.resetView()
}

function inferDeviceType(deviceName: string) {
  const types: Record<string, string> = {
    '循环风机': 'FAN',
    '水肥阀组': 'VALVE',
    '补光灯组': 'GROW_LIGHT',
    '微雾喷灌': 'MIST',
    '卷帘机': 'CURTAIN',
  }
  return types[deviceName] ?? 'DEVICE'
}

function commandTypeFor(deviceType: string, enabled: boolean) {
  const commands: Record<string, [string, string]> = {
    FAN: ['FAN_ON', 'FAN_OFF'],
    VALVE: ['VALVE_OPEN', 'VALVE_CLOSE'],
    GROW_LIGHT: ['LIGHT_ON', 'LIGHT_OFF'],
    MIST: ['MIST_ON', 'MIST_OFF'],
    CURTAIN: ['CURTAIN_OPEN', 'CURTAIN_CLOSE'],
  }
  const pair = commands[deviceType] ?? ['DEVICE_ON', 'DEVICE_OFF']
  return enabled ? pair[0] : pair[1]
}

const COMMAND_POLL_INTERVAL_MS = 1200
const COMMAND_POLL_ATTEMPTS = 10
const COMMAND_DISPATCH_WAIT_ATTEMPTS = 5

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function commandStatusText(status: string) {
  if (status === 'DISPATCHED') {
    return '指令已下发，等待设备回执'
  }
  if (status === 'CREATED') {
    return '指令已生成，等待物联网服务下发'
  }
  return '指令处理中'
}

/**
 * 轮询指令真实状态：显示由物联网服务下发、设备回执驱动的状态迁移，
 * 而不是在前端直接回执。
 */
async function waitForCommandResult(
  commandNo: string,
): Promise<ControlCommandResult | null> {
  for (let attempt = 0; attempt < COMMAND_POLL_ATTEMPTS; attempt += 1) {
    await delay(COMMAND_POLL_INTERVAL_MS)
    try {
      const latest = await getControlCommand(commandNo)
      if (latest.status === 'SUCCEEDED' || latest.status === 'FAILED') {
        return latest
      }
      // 多次仍停在 CREATED，说明物联网服务没有把指令下发出去，提前结束等待。
      if (latest.status === 'CREATED'
        && attempt + 1 >= COMMAND_DISPATCH_WAIT_ATTEMPTS) {
        return latest
      }
      commandMessage.value = commandStatusText(latest.status)
    } catch {
      // 单次查询失败不影响继续等待
    }
  }
  return null
}

async function toggleDevice(device: {
  id: string
  name: string
  deviceType: string
  tone: string
}) {
  if (device.tone === 'offline' || devicePending.value[device.id]) {
    return
  }

  const enabled = !deviceSwitches.value[device.id]
  const previousState = deviceSwitches.value[device.id]
  deviceSwitches.value[device.id] = enabled
  devicePending.value[device.id] = true
  commandMessage.value = `正在下发${device.name}控制指令...`
  try {
    const command = await createControlCommand({
      greenhouseCode: selectedSite.value.code,
      deviceCode: device.id,
      commandType: commandTypeFor(device.deviceType, enabled),
      parameters: {
        enabled,
        source: 'PRODUCTION_DASHBOARD',
      },
      operator: '基地管理员',
      timeoutSeconds: 30,
    })
    commandMessage.value = `${device.name}指令已生成，等待物联网服务下发`
    const latest = await waitForCommandResult(command.commandNo)
    if (latest === null) {
      commandMessage.value = `${device.name}指令已下发，设备回执较慢，可稍后查看设备状态`
    } else if (latest.status === 'SUCCEEDED') {
      devicePending.value[device.id] = false
      await loadOverview(selectedSite.value)
      commandMessage.value = `${device.name}${enabled ? '已开启' : '已关闭'}（设备已回执）`
    } else if (latest.status === 'CREATED') {
      deviceSwitches.value[device.id] = previousState
      commandMessage.value = `${device.name}指令已生成但未被下发，请确认物联网服务已启动（超时后会自动重试）`
    } else {
      deviceSwitches.value[device.id] = previousState
      commandMessage.value = `${device.name}执行失败：${
        latest.failureReason ?? '设备未在规定时间内回执'
      }`
    }
  } catch (error) {
    deviceSwitches.value[device.id] = previousState
    const responseMessage = (
      error as { response?: { data?: { message?: string } } }
    ).response?.data?.message
    commandMessage.value =
      responseMessage ?? '控制指令下发失败，请检查设备状态'
  } finally {
    devicePending.value[device.id] = false
  }
}

async function issueCommand(
  deviceCode: string,
  commandType: string,
  parameters: Record<string, unknown>,
) {
  const command = await createControlCommand({
    greenhouseCode: selectedSite.value.code,
    deviceCode,
    commandType,
    parameters,
    operator: '基地管理员',
    timeoutSeconds: 30,
  })
  return (await waitForCommandResult(command.commandNo)) ?? command
}

function toSite(summary: GreenhouseSummary): GreenhouseSite {
  const layout = greenhouseSites.find(
    (site) => site.code === summary.greenhouseCode,
  )
  const statusMap: Record<string, string> = {
    ACTIVE: '运行正常',
    WARNING: '待巡检',
    STANDBY: '设备待机',
  }
  const toneMap: Record<string, GreenhouseSite['tone']> = {
    ACTIVE: 'running',
    WARNING: 'warning',
    STANDBY: 'warning',
  }
  return {
    ...(layout ?? greenhouseSites[0]),
    id: layout?.id ?? `gh-${summary.greenhouseId}`,
    code: summary.greenhouseCode,
    name: summary.greenhouseName,
    area: `${summary.area.toLocaleString('zh-CN')} ㎡`,
    location: summary.location,
    greenhouseId: summary.greenhouseId,
    batchId: summary.batchId ?? undefined,
    crop: summary.cropName
      ? `${summary.cropName}${summary.varietyName ? ` · ${summary.varietyName}` : ''}`
      : '未选择作物',
    batch: summary.batchId ? `CB-${summary.batchId}` : '',
    plantingDate: summary.plantingDate ?? undefined,
    expectedHarvestDate: summary.expectedHarvestDate ?? undefined,
    status: statusMap[summary.status] ?? summary.status,
    tone: toneMap[summary.status] ?? 'warning',
  }
}

async function loadOverview(site: GreenhouseSite) {
  try {
    selectedOverview.value = await getGreenhouseOverview(site.code)
    overviewSiteCode.value = site.code
    selectedHotspot.value = greenhouseHotspots[0]
    const pendingSwitches = { ...deviceSwitches.value }
    const pendingIds = new Set(
      Object.entries(devicePending.value)
        .filter(([, pending]) => pending)
        .map(([deviceId]) => deviceId),
    )
    deviceSwitches.value = Object.fromEntries(
      displayDevices.value.map((device) => [
        device.id,
        pendingIds.has(device.id)
          ? pendingSwitches[device.id] ?? device.active
          : device.active,
      ]),
    )
    recordLiveSample()
  } catch (error) {
    console.warn('[production] 温室概览加载失败', error)
    // 同一个温室已经拿到过真实数据时保留它：一次请求抖动不该把设备机架清空，
    // 新鲜度标签会按 lastRefreshedAt 自己降级成"数据延迟"。
    if (overviewSiteCode.value === site.code && selectedOverview.value) {
      return
    }
    selectedOverview.value = null
    overviewSiteCode.value = ''
    apiConnected.value = false
  }
}

async function loadWaterPlanData(site: GreenhouseSite) {
  try {
    waterPlanMessage.value = ''
    waterPlanRecords.value = await listWaterFertilizerPlans(
      site.code,
      formatLocalDate(new Date()),
    )
    const selectedExists = waterPlanRecords.value.some(
      (plan) => plan.planNo === selectedWaterPlanNo.value,
    )
    if (!selectedExists) {
      selectedWaterPlanNo.value =
        waterPlanRecords.value.find((plan) => plan.status === 'PLANNED')
          ?.planNo
        ?? waterPlanRecords.value[0]?.planNo
        ?? ''
    }
  } catch {
    waterPlanRecords.value = []
    selectedWaterPlanNo.value = ''
  }
}

async function loadEnvironmentTrendData(site: GreenhouseSite) {
  trendLoading.value = true
  try {
    environmentTrend.value = await getEnvironmentTrend(
      site.code,
      trendLimit.value,
    )
  } catch {
    environmentTrend.value = null
  } finally {
    trendLoading.value = false
  }
}

async function loadTelemetryHistoryData(site: GreenhouseSite) {
  telemetryHistoryLoading.value = true
  telemetryHistoryError.value = false
  try {
    const history = await getTelemetryHistory(
      site.code,
      liveWindowMinutes.value,
      liveRefreshSeconds,
    )
    if (history.greenhouseCode === selectedSite.value.code) {
      telemetryHistory.value = history.samples.map((sample) => ({
        at: sample.at,
        metrics: sample.metrics,
      }))
    }
  } catch {
    telemetryHistoryError.value = true
  } finally {
    telemetryHistoryLoading.value = false
  }
}

async function loadThresholdData(site: GreenhouseSite) {
  try {
    thresholds.value = await listEnvironmentThresholds(site.code)
  } catch {
    thresholds.value = []
  }
}

function selectLiveWindow(minutes: number) {
  if (liveWindowMinutes.value === minutes) {
    return
  }
  liveWindowMinutes.value = minutes
  try {
    window.sessionStorage.setItem(LIVE_WINDOW_STORAGE_KEY, String(minutes))
  } catch {
    // 浏览器禁用存储时只影响本次切换的持久化，不影响当前展示
  }
  void loadTelemetryHistoryData(selectedSite.value)
}

function toggleLiveSampling() {
  liveSamplingPaused.value = !liveSamplingPaused.value
  if (!liveSamplingPaused.value) {
    samplingNow.value = Date.now()
    nextLiveSampleAt.value = Date.now() + liveRefreshSeconds * 1000
    void refreshLiveData()
  }
}

function formatLocalDate(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

async function applyGreenhouseSummaries(
  summaries: GreenhouseSummary[],
  preferredCode = '',
) {
  availableSites.value = summaries.map(toSite)
  const targetCode = preferredCode || selectedSite.value.code
  selectedSite.value =
    availableSites.value.find((site) => site.code === targetCode)
    ?? availableSites.value[0]
  loadLiveSamples()
  await Promise.all([
    loadOverview(selectedSite.value),
    loadTelemetryHistoryData(selectedSite.value),
    loadWaterPlanData(selectedSite.value),
    loadEnvironmentTrendData(selectedSite.value),
    loadThresholdData(selectedSite.value),
  ])
}

async function loadBackendData() {
  initialLoading.value = true
  syncError.value = ''
  try {
    const summaries = await listGreenhouses()
    if (summaries.length > 0) {
      apiConnected.value = true
      await applyGreenhouseSummaries(summaries)
      lastRefreshedAt.value = new Date()
    } else {
      apiConnected.value = false
      syncError.value = '未获取到温室列表，当前展示本地演示数据'
    }
  } catch {
    apiConnected.value = false
    syncError.value = '实时接口暂不可用，当前展示本地演示数据'
  } finally {
    initialLoading.value = false
  }
}

async function refreshGreenhouseList(preferredCode = '') {
  const summaries = await listGreenhouses()
  if (summaries.length === 0) {
    availableSites.value = []
    apiConnected.value = false
    syncError.value = '园区暂无温室，请先新增大棚'
    return
  }
  apiConnected.value = true
  syncError.value = ''
  await applyGreenhouseSummaries(summaries, preferredCode)
  lastRefreshedAt.value = new Date()
}

async function handleGreenhouseChanged(preferredCode: string) {
  try {
    await refreshGreenhouseList(preferredCode)
  } catch {
    syncError.value = '温室列表已变更，但重新加载失败，请点击重新连接'
  }
}

async function retryBackendConnection() {
  if (initialLoading.value) {
    return
  }
  await loadBackendData()
}

async function refreshLiveData() {
  if (liveRefreshing.value) {
    return
  }
  if (document.visibilityState !== 'visible') {
    return
  }
  liveRefreshing.value = true
  try {
    await Promise.all([
      loadOverview(selectedSite.value),
      loadTelemetryHistoryData(selectedSite.value),
      loadWaterPlanData(selectedSite.value),
      loadEnvironmentTrendData(selectedSite.value),
      loadThresholdData(selectedSite.value),
    ])
    lastRefreshedAt.value = new Date()
  } finally {
    liveRefreshing.value = false
    samplingNow.value = Date.now()
    nextLiveSampleAt.value = Date.now() + liveRefreshSeconds * 1000
  }
}

async function chooseSite(site: GreenhouseSite) {
  selectedSite.value = site
  loadLiveSamples()
  telemetryHistory.value = []
  greenhouseManagerOpen.value = false
  closeThresholdDialog()
  selectedOverview.value = null
  overviewSiteCode.value = ''
  commandMessage.value = ''
  nextTick(() => sceneRef.value?.resetView())
  await Promise.all([
    loadOverview(site),
    loadTelemetryHistoryData(site),
    loadWaterPlanData(site),
    loadEnvironmentTrendData(site),
    loadThresholdData(site),
  ])
}

function selectWaterPlan(planNo: string) {
  if (!planNo) {
    waterPlanMessage.value =
      '当前显示的是本地演示计划，连接实时接口后才能执行'
    return
  }
  selectedWaterPlanNo.value = planNo
  waterPlanMessage.value = ''
}

async function executeWaterPlan(planNo = '') {
  const targetPlanNo =
    planNo
    || waterPlanRecords.value.find((plan) => plan.status === 'PLANNED')?.planNo
    || ''
  if (!targetPlanNo) {
    waterPlanMessage.value = '当前没有可执行的实时计划'
    return
  }
  if (waterPlanPendingNo.value) {
    return
  }

  waterPlanPendingNo.value = targetPlanNo
  selectedWaterPlanNo.value = targetPlanNo
  waterPlanMessage.value = ''
  try {
    const updated = await executeWaterFertilizerPlan(
      targetPlanNo,
      session.displayName || '基地管理员',
    )
    waterPlanRecords.value = waterPlanRecords.value.map((plan) =>
      plan.planNo === updated.planNo ? updated : plan,
    )
    waterPlanMessage.value =
      `${updated.zoneName} ${updated.plannedAt.slice(11, 16)} 计划已执行`
  } catch (error) {
    const responseMessage = (
      error as { response?: { data?: { message?: string } } }
    ).response?.data?.message
    waterPlanMessage.value =
      responseMessage ?? '计划执行失败，请检查设备与服务状态'
  } finally {
    waterPlanPendingNo.value = ''
  }
}

function defaultPlanTime() {
  const date = new Date(Date.now() + 60 * 60_000)
  return `${String(date.getHours()).padStart(2, '0')}:${String(
    date.getMinutes(),
  ).padStart(2, '0')}`
}

function openWaterPlanForm() {
  waterPlanFormError.value = ''
  waterPlanForm.value = {
    time: defaultPlanTime(),
    zoneName: 'A区',
    waterVolume: 2.4,
    recipeVersion: selectedWaterPlan.value?.recipeVersion ?? 'V3',
    ecTarget: selectedWaterPlan.value?.ecTarget ?? 1.8,
    phTarget: selectedWaterPlan.value?.phTarget ?? 6.2,
  }
  waterPlanFormOpen.value = true
}

async function createWaterPlan() {
  const batchId = selectedOverview.value?.crop?.batchId
  if (!batchId) {
    waterPlanFormError.value = '当前温室没有可用的作物批次'
    return
  }
  if (!waterPlanForm.value.time || !waterPlanForm.value.zoneName.trim()) {
    waterPlanFormError.value = '请填写计划时间和灌溉分区'
    return
  }
  if (
    !Number.isFinite(Number(waterPlanForm.value.waterVolume))
    || Number(waterPlanForm.value.waterVolume) <= 0
  ) {
    waterPlanFormError.value = '计划水量必须大于 0'
    return
  }

  waterPlanCreating.value = true
  waterPlanFormError.value = ''
  try {
    const created = await createWaterFertilizerPlan({
      greenhouseCode: selectedSite.value.code,
      batchId,
      recipeCode: selectedWaterPlan.value?.recipeCode ?? 'TOMATO-FRUIT',
      recipeVersion: waterPlanForm.value.recipeVersion,
      ecTarget: Number(waterPlanForm.value.ecTarget),
      phTarget: Number(waterPlanForm.value.phTarget),
      zoneName: waterPlanForm.value.zoneName.trim(),
      plannedAt: `${formatLocalDate(new Date())}T${waterPlanForm.value.time}:00`,
      waterVolume: Number(waterPlanForm.value.waterVolume),
      operatorName: session.displayName || '基地管理员',
    })
    waterPlanRecords.value = [...waterPlanRecords.value, created].sort(
      (left, right) => left.plannedAt.localeCompare(right.plannedAt),
    )
    selectedWaterPlanNo.value = created.planNo
    waterPlanFormOpen.value = false
    waterPlanMessage.value = `${created.zoneName} ${created.plannedAt.slice(11, 16)} 计划已新增`
  } catch (error) {
    const responseMessage = (
      error as { response?: { data?: { message?: string } } }
    ).response?.data?.message
    waterPlanFormError.value = responseMessage ?? '新增计划失败，请检查表单数据'
  } finally {
    waterPlanCreating.value = false
  }
}

async function refreshTodayData() {
  commandMessage.value = '正在刷新今日数据...'
  await Promise.all([
    loadOverview(selectedSite.value),
    loadTelemetryHistoryData(selectedSite.value),
    loadEnvironmentTrendData(selectedSite.value),
    loadWaterPlanData(selectedSite.value),
    loadThresholdData(selectedSite.value),
  ])
  lastRefreshedAt.value = new Date()
  samplingNow.value = Date.now()
  nextLiveSampleAt.value = Date.now() + liveRefreshSeconds * 1000
  commandMessage.value = '今日环境和灌溉数据已刷新'
}

async function attestCycleNow() {
  if (attestingCycle.value) {
    return
  }
  attestingCycle.value = true
  cycleAttestMessage.value =
    '正在汇总温度、湿度、光照、土壤水分四项遥测并提交链上存证...'
  cycleAttestError.value = false
  commandMessage.value =
    '正在汇总温度、湿度、光照、土壤水分四项遥测并提交环境周期存证...'
  try {
    const cycle = await attestEnvironmentCycle(selectedSite.value.code)
    await Promise.all([
      loadEnvironmentTrendData(selectedSite.value),
      loadOverview(selectedSite.value),
    ])
    commandMessage.value =
      `环境周期 #${cycle.cycleId} 已生成（温度/湿度/光照/土壤水分），` +
      `根哈希 ${shortenChainHash(cycle.rootHash, 12)}，` +
      `链上状态 ${cycle.chainStatus === 'CONFIRMED' ? '已确认' : '待补偿'}`
    cycleAttestMessage.value = commandMessage.value
    cycleAttestError.value = cycle.chainStatus !== 'CONFIRMED'
  } catch (error) {
    const responseMessage = (
      error as { response?: { data?: { message?: string } } }
    ).response?.data?.message
    commandMessage.value =
      responseMessage ?? '环境周期存证失败，请检查遥测数据和链上适配服务'
    cycleAttestMessage.value = commandMessage.value
    cycleAttestError.value = true
  } finally {
    attestingCycle.value = false
  }
}

function exportCycleData() {
  const points = environmentTrend.value?.points ?? []
  if (points.length === 0) {
    commandMessage.value = '暂无可导出的周期数据'
    return
  }

  const rows = [
    [
      '采集周期',
      '开始时间',
      '结束时间',
      '温度',
      '湿度',
      '光照',
      '土壤水分',
      '根哈希',
      '链上状态',
    ],
    ...points.map((point) => [
      point.cycleId,
      point.cycleStart,
      point.cycleEnd,
      point.metrics.temperature ?? '',
      point.metrics.humidity ?? '',
      point.metrics.light ?? '',
      point.metrics.soil_moisture ?? '',
      point.rootHash ?? '',
      point.chainStatus ?? '',
    ]),
  ]
  const csv = `\uFEFF${rows
    .map((row) =>
      row
        .map((value) => `"${String(value).replaceAll('"', '""')}"`)
        .join(','),
    )
    .join('\n')}`
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = `${selectedSite.value.code}-环境周期.csv`
  link.click()
  URL.revokeObjectURL(objectUrl)
  commandMessage.value = '环境周期数据已导出'
}

async function sendVentCommand() {
  ventilationPending.value = true
  commandMessage.value = '正在启动通风联动...'
  try {
    const results = await Promise.allSettled([
      issueCommand('FAN-A-02', 'FAN_ON', {
        speed: 68,
        source: 'IMMEDIATE_VENTILATION',
      }),
      issueCommand('CURTAIN-A-01', 'CURTAIN_OPEN', {
        opening: 42,
        source: 'IMMEDIATE_VENTILATION',
      }),
    ])
    const succeeded = results.filter(
      (result) =>
        result.status === 'fulfilled'
        && result.value.status === 'SUCCEEDED',
    ).length
    await loadOverview(selectedSite.value)
    if (succeeded === results.length) {
      commandMessage.value = '通风联动已执行：循环风机已开启，卷帘机已打开（设备已回执）'
    } else if (succeeded > 0) {
      commandMessage.value = `通风联动部分执行，成功 ${succeeded}/${results.length} 条指令`
    } else {
      commandMessage.value = '通风联动执行失败，请检查设备状态'
    }
  } finally {
    ventilationPending.value = false
  }
}

function handleDocumentKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') {
    return
  }
  if (thresholdDialogOpen.value) {
    closeThresholdDialog()
    return
  }
  greenhouseManagerOpen.value = false
}

function handleDocumentVisibilityChange() {
  if (document.visibilityState !== 'visible' || liveSamplingPaused.value) {
    return
  }
  samplingNow.value = Date.now()
  const refreshedAt = lastRefreshedAt.value?.getTime() ?? 0
  if (Date.now() - refreshedAt >= liveRefreshSeconds * 1000) {
    void refreshLiveData()
  } else {
    nextLiveSampleAt.value = Date.now() + liveRefreshSeconds * 1000
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleDocumentKeydown)
  document.addEventListener('visibilitychange', handleDocumentVisibilityChange)
  void loadBackendData()
  blockHeightTimer = window.setInterval(() => {
    blockHeight.value += 1
  }, 3200)
  liveRefreshTimer = window.setInterval(() => {
    if (!liveSamplingPaused.value) {
      void refreshLiveData()
    }
  }, liveRefreshSeconds * 1000)
  liveCountdownTimer = window.setInterval(() => {
    samplingNow.value = Date.now()
  }, 1000)
  freshnessTimer = window.setInterval(() => {
    freshnessNow.value = Date.now()
  }, 10_000)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleDocumentKeydown)
  document.removeEventListener(
    'visibilitychange',
    handleDocumentVisibilityChange,
  )
  window.clearInterval(blockHeightTimer)
  window.clearInterval(liveRefreshTimer)
  window.clearInterval(liveCountdownTimer)
  window.clearInterval(freshnessTimer)
})
</script>

<template>
  <div class="production-v2" :class="`is-${pageSection}`">
    <header class="pv2-hero">
      <div class="pv2-hero__identity">
        <span>PRODUCTION DIGITAL TWIN / {{ selectedSite.code }}</span>
        <h2>{{ selectedSite.name }}数字生产中枢</h2>
        <p>
          {{ selectedSite.crop }} · {{ selectedSite.area }} ·
          当前状态 {{ environmentSummary.label }}
        </p>
      </div>
      <div class="pv2-hero__actions">
        <button type="button" @click="greenhouseManagerOpen = true">
          <LayoutGrid :size="15" />
          {{ selectedSite.name }}
        </button>
        <DataFreshnessTag
          :mode="dataFreshness.mode"
          :label="dataFreshness.label"
        />
        <button type="button" @click="exportCycleData">
          <ArrowRight :size="15" />
          导出生产档案
        </button>
      </div>
    </header>

    <section class="pv2-statusbar">
      <div>
        <span
          class="pv2-statusbar__dot"
          :class="{
            'is-warning': environmentSummary.tone === 'is-warning',
            'is-alert': environmentSummary.tone === 'is-offline',
          }"
        ></span>
        <div>
          <strong>{{ environmentSummary.label }}</strong>
          <small>{{ selectedSite.crop }} · {{ liveRefreshLabel }}</small>
        </div>
      </div>
      <div class="pv2-statusbar__actions">
        <button type="button" :disabled="initialLoading" @click="refreshTodayData">
          <RefreshCw :size="14" />
          刷新今日
        </button>
        <button
          type="button"
          :disabled="linkagePending || pendingLinkages.length === 0"
          :title="linkageButtonHint"
          @click="executeThresholdLinkage"
        >
          <Zap :size="14" />
          {{ linkagePending ? '联动中' : linkageButtonLabel }}
        </button>
        <button
          class="is-primary"
          type="button"
          :disabled="ventilationPending"
          @click="sendVentCommand"
        >
          <Fan :size="14" />
          {{ ventilationPending ? '通风中' : '立即通风' }}
        </button>
      </div>
      <p v-if="commandMessage">{{ commandMessage }}</p>
    </section>

    <section v-if="syncError" class="pv2-sync-warning">
      <AlertTriangle :size="15" />
      <span>{{ syncError }}</span>
      <button type="button" :disabled="initialLoading" @click="retryBackendConnection">
        重试
      </button>
    </section>

    <section
      v-if="['environment', 'twin', 'water'].includes(pageSection)"
      class="pv2-metric-ribbon"
    >
      <button
        v-for="metric in metrics"
        :key="metric.key"
        type="button"
        :class="[
          `is-${metric.tone}`,
          `state-${metricStates[metric.key]?.tone ?? 'muted'}`,
        ]"
        @click="openThresholdDialog(metric.key, $event)"
      >
        <span><component :is="metric.icon" :size="18" /></span>
        <div>
          <small>{{ metric.label }}</small>
          <strong>{{ metric.value }} <em>{{ metric.unit }}</em></strong>
          <p>{{ metric.note }}</p>
        </div>
        <svg
          v-if="metricSparklineMap[metric.key]"
          class="pv2-metric-sparkline"
          viewBox="20 6 280 90"
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          <path :d="metricSparklineMap[metric.key]" />
        </svg>
        <i>{{ metricStates[metric.key]?.label ?? '未启用' }}</i>
      </button>
      <article class="pv2-crop-card">
        <span><Sprout :size="18" /></span>
        <div>
          <small>当前作物批次</small>
          <strong>{{ selectedOverview?.crop?.cropName ?? selectedSite.crop }}</strong>
          <p>
            {{
              selectedOverview?.crop?.batchId
                ? `CB-${selectedOverview.crop.batchId}`
                : selectedSite.batch
            }}
            · {{ selectedSite.plantingDate ?? '未登记' }}
          </p>
        </div>
      </article>
    </section>

    <section v-if="pageSection === 'twin'" class="pv2-stage">
      <div class="pv2-scene">
        <div class="pv2-scene__top">
          <div class="pv2-scene__title">
            <span><Layers3 :size="15" /> LIVE DIGITAL TWIN</span>
            <strong>{{ selectedSite.name }} · {{ selectedSite.crop }}</strong>
          </div>
          <div class="pv2-scene__controls">
            <div class="pv2-scene__mode-group">
              <button
                v-for="mode in sceneModes"
                :key="mode.id"
                type="button"
                :class="{ 'is-active': sceneMode === mode.id }"
                :aria-pressed="sceneMode === mode.id"
                @click="changeMode(mode.id)"
              >
                <component :is="mode.icon" :size="13" />
                {{ mode.label }}
              </button>
            </div>
            <div class="pv2-scene__view-actions">
              <button
                type="button"
                :class="{ 'is-active': autoRotate }"
                :aria-pressed="autoRotate"
                aria-label="切换自动环视"
                title="自动环视"
                @click="autoRotate = !autoRotate"
              >
                <Rotate3d :size="14" />
              </button>
              <button
                type="button"
                aria-label="重置三维视角"
                title="重置视角"
                @click="resetScene"
              >
                <LocateFixed :size="14" />
              </button>
            </div>
          </div>
        </div>

        <GreenhouseScene
          ref="sceneRef"
          class="pv2-scene__canvas"
          :mode="sceneMode"
          :selected-id="selectedHotspot.id"
          :auto-rotate="autoRotate"
          :crop="selectedSite.crop"
          :active-zone="selectedWaterPlan?.zoneName ?? ''"
          :highlight-metric="linkedMetric"
          theme="light"
          immersive
          @select="selectHotspot"
        />

        <div class="pv2-scene__hotspot">
          <span><component :is="selectedHotspot.icon" :size="14" /></span>
          <div>
            <strong>{{ selectedHotspot.label }}</strong>
            <small>{{ selectedHotspot.value }} {{ selectedHotspot.unit }}</small>
          </div>
          <em>{{ selectedHotspot.status }}</em>
        </div>

        <div class="pv2-scene__footer">
          <span>{{ selectedSite.area }} · {{ selectedSite.status }}</span>
          <span>
            <Network :size="13" />
            {{ displayDevices.length }} 台设备在线
          </span>
        </div>
      </div>

      <aside class="pv2-device-rack">
        <header>
          <div>
            <span>EXECUTION RACK</span>
            <h3>设备执行机架</h3>
          </div>
          <strong>{{ displayDevices.length }}</strong>
        </header>
        <p v-if="deviceRackNote" class="pv2-device-rack__note">
          {{ deviceRackNote }}
        </p>
        <div
          v-for="device in displayDevices"
          :key="device.id"
          class="pv2-device"
          :class="`is-${device.tone}`"
          tabindex="0"
          @click="focusDeviceInScene(device)"
          @keydown.enter="focusDeviceInScene(device)"
        >
          <span class="pv2-device__pulse"></span>
          <div>
            <strong>{{ device.name }}</strong>
            <small>
              {{ devicePending[device.id] ? '指令处理中' : device.status }}
            </small>
          </div>
          <button
            class="pv2-device__switch"
            :class="{ 'is-on': deviceSwitches[device.id] }"
            type="button"
            :disabled="
              device.tone === 'offline'
                || devicePending[device.id]
                || deviceCommandLocked
            "
            :title="deviceCommandLocked ? '演示设备，未接入实时接口' : `切换${device.name}`"
            :aria-label="`切换${device.name}`"
            @click.stop="toggleDevice(device)"
          >
            <i></i>
          </button>
        </div>
        <p v-if="displayDevices.length === 0" class="pv2-device-rack__note">
          当前没有可用执行设备
        </p>
      </aside>
    </section>

    <section v-if="pageSection === 'environment'" class="pv2-trend-wall">
      <header>
        <div>
          <span>REAL-TIME TELEMETRY WALL</span>
          <h3>实时环境采样墙</h3>
          <p>{{ liveSampleCount }} 个采样点 · {{ liveWindowMinutes }} 分钟窗口</p>
        </div>
        <div class="pv2-trend-wall__tools">
          <button
            type="button"
            :class="liveSamplingStatus.tone"
            title="采样状态"
          >
            <i></i>
            {{ liveSamplingStatus.label }} · {{ liveSamplingCountdown }}
          </button>
          <button
            type="button"
            :disabled="liveRefreshing"
            @click="refreshLiveData"
          >
            <RefreshCw :size="13" />
            {{ liveRefreshing ? '采样中' : '立即采样' }}
          </button>
          <button
            type="button"
            :aria-pressed="liveSamplingPaused"
            :aria-label="liveSamplingPaused ? '继续实时采样' : '暂停实时采样'"
            :title="liveSamplingPaused ? '继续实时采样' : '暂停实时采样'"
            @click="toggleLiveSampling"
          >
            <Pause v-if="!liveSamplingPaused" :size="13" />
            <Play v-else :size="13" />
          </button>
          <div class="pv2-trend-wall__periods">
            <button
              v-for="minutes in [5, 15, 30]"
              :key="minutes"
              type="button"
              :class="{ 'is-active': liveWindowMinutes === minutes }"
              @click="selectLiveWindow(minutes)"
            >
              {{ minutes }}m
            </button>
          </div>
        </div>
      </header>
      <div class="pv2-trend-wall__grid">
        <article
          v-for="card in trendMetricCards"
          :key="card.key"
          class="pv2-trend-cell"
          :class="`is-${card.tone}`"
          @mouseenter="linkedMetric = card.key"
          @mouseleave="linkedMetric = ''"
        >
          <div class="pv2-trend-cell__head">
            <span><component :is="card.icon" :size="15" /></span>
            <div>
              <small>{{ card.label }}</small>
              <strong>
                {{ card.key === 'light'
                  ? card.current.toLocaleString('zh-CN')
                  : card.current.toFixed(1)
                }}
                <em>{{ card.unit }}</em>
              </strong>
            </div>
            <time>{{ card.updatedAt }}</time>
          </div>
          <svg
            class="pv2-trend-chart"
            viewBox="0 0 320 118"
            role="img"
            :aria-label="trendChartSummary(card)"
          >
            <title>{{ trendChartSummary(card) }}</title>
            <path class="pv2-trend-grid" d="M20 12H300M20 50H300M20 88H300" />
            <path v-if="card.areaPath" class="pv2-trend-area" :d="card.areaPath" />
            <path
              v-if="card.hasLine"
              class="pv2-trend-line"
              :d="card.path"
              pathLength="1"
            />
            <circle v-if="card.dot" class="pv2-trend-dot" :cx="card.dot.x" :cy="card.dot.y" r="3.4" />
            <text class="pv2-trend-axis" x="20" y="110">{{ card.axisLabels[0] }}</text>
            <text class="pv2-trend-axis" x="160" y="110" text-anchor="middle">{{ card.axisLabels[1] }}</text>
            <text class="pv2-trend-axis" x="300" y="110" text-anchor="end">{{ card.axisLabels[2] }}</text>
          </svg>
          <footer>
            <span>低 {{ card.minimumValue.toFixed(card.key === 'light' ? 0 : 1) }}</span>
            <span>高 {{ card.maximumValue.toFixed(card.key === 'light' ? 0 : 1) }}</span>
          </footer>
          <table class="sr-only">
            <caption>{{ card.label }}数据摘要</caption>
            <tbody>
              <tr>
                <th>当前值</th>
                <td>{{ formatTrendValue(card, card.current) }}</td>
              </tr>
              <tr>
                <th>最低值</th>
                <td>{{ formatTrendValue(card, card.minimumValue) }}</td>
              </tr>
              <tr>
                <th>最高值</th>
                <td>{{ formatTrendValue(card, card.maximumValue) }}</td>
              </tr>
              <tr>
                <th>时间范围</th>
                <td>{{ card.axisLabels[0] }} 至 {{ card.axisLabels[2] }}</td>
              </tr>
            </tbody>
          </table>
        </article>
      </div>
    </section>

    <section v-if="pageSection === 'water'" class="pv2-water">
      <header>
        <div>
          <span>WATER & FERTILIZER QUEUE</span>
          <h3>水肥执行队列</h3>
          <p>{{ waterRecipeSummary }}</p>
        </div>
        <div>
          <button type="button" @click="openWaterPlanForm">
            <Plus :size="13" /> 新增计划
          </button>
          <button type="button" @click="recipeOpen = !recipeOpen">
            {{ recipeOpen ? '收起配方' : '查看配方' }}
          </button>
        </div>
      </header>
      <div class="pv2-water-layout">
        <div class="pv2-water-main">
          <div v-if="recipeOpen" class="pv2-recipe">
            <span>配方 {{ recipeDetails.recipeVersion }}</span>
            <span>EC {{ recipeDetails.ecTarget }}</span>
            <span>pH {{ recipeDetails.phTarget }}</span>
            <span>{{ recipeDetails.zoneName }}</span>
            <span>{{ recipeDetails.waterVolume }} m³</span>
          </div>
          <form v-if="waterPlanFormOpen" class="pv2-water-form" @submit.prevent="createWaterPlan">
            <input v-model="waterPlanForm.time" type="time" />
            <input v-model="waterPlanForm.zoneName" type="text" placeholder="分区" />
            <input v-model.number="waterPlanForm.waterVolume" type="number" min="0.1" step="0.1" placeholder="水量" />
            <input v-model="waterPlanForm.recipeVersion" type="text" placeholder="配方" />
            <button type="submit" :disabled="waterPlanCreating">
              {{ waterPlanCreating ? '保存中' : '保存' }}
            </button>
          </form>
          <p v-if="waterPlanFormError" class="pv2-water-error">{{ waterPlanFormError }}</p>
          <div class="pv2-water-list">
            <button
              v-for="plan in waterPlans"
              :key="plan.planNo || `${plan.time}-${plan.zone}`"
              type="button"
              :class="{
                'is-selected': plan.planNo && plan.planNo === selectedWaterPlanNo,
                'is-pending': plan.planNo === waterPlanPendingNo,
              }"
              @click="selectWaterPlan(plan.planNo)"
            >
              <time>{{ plan.time }}</time>
              <span>
                <strong>{{ plan.zone }}</strong>
                <small>{{ plan.amount }}</small>
              </span>
              <em>{{ plan.status }}</em>
              <i
                v-if="plan.real && plan.rawStatus === 'PLANNED'"
                role="button"
                tabindex="0"
                @click.stop="executeWaterPlan(plan.planNo)"
                @keydown.enter.stop="executeWaterPlan(plan.planNo)"
              >
                执行
              </i>
            </button>
          </div>
          <button
            class="pv2-water-execute"
            type="button"
            :disabled="!hasPendingWaterPlan || Boolean(waterPlanPendingNo)"
            @click="executeWaterPlan()"
          >
            <Play :size="14" />
            {{ waterPlanPendingNo ? '正在执行' : '执行下一灌溉计划' }}
          </button>
          <p v-if="waterPlanMessage" class="pv2-water-message">{{ waterPlanMessage }}</p>
        </div>

        <aside class="pv2-water-summary">
          <header>
            <span>IRRIGATION SNAPSHOT</span>
            <strong>本轮执行概览</strong>
          </header>
          <dl>
            <div><dt>当前配方</dt><dd>{{ recipeDetails.recipeVersion }}</dd></div>
            <div><dt>目标 EC</dt><dd>{{ recipeDetails.ecTarget }}</dd></div>
            <div><dt>目标 pH</dt><dd>{{ recipeDetails.phTarget }}</dd></div>
            <div><dt>控制分区</dt><dd>{{ recipeDetails.zoneName }}</dd></div>
            <div><dt>计划水量</dt><dd>{{ recipeDetails.waterVolume.toFixed(1) }} m³</dd></div>
            <div><dt>责任人</dt><dd>{{ recipeDetails.operatorName }}</dd></div>
          </dl>
          <div class="pv2-water-progress">
            <span>计划完成度</span>
            <strong>{{ waterCompletion.percent }}%</strong>
            <i><em :style="{ width: `${waterCompletion.percent}%` }"></em></i>
            <small>{{ waterCompletion.done }} / {{ waterCompletion.total }} 项已完成</small>
          </div>
        </aside>
      </div>
    </section>

    <section v-if="pageSection === 'chain'" class="pv2-proof">
      <header>
        <div>
          <span>CYCLE ROOT HASH</span>
          <h3>周期根哈希存证带</h3>
          <p>环境数据按周期形成哈希链，仅根哈希写入联盟链</p>
        </div>
        <div>
          <span :class="{ 'is-confirmed': chainConfirmed }">
            <ShieldCheck :size="14" />
            {{ chainConfirmed ? '链上已确认' : '等待链上回执' }}
          </span>
          <button type="button" :disabled="attestingCycle" @click="attestCycleNow">
            <RefreshCw :size="14" />
            {{ attestingCycle ? '存证中' : '立即生成并存证' }}
          </button>
        </div>
      </header>
      <p v-if="cycleAttestMessage" class="pv2-proof-message" :class="{ 'is-error': cycleAttestError }">
        {{ cycleAttestMessage }}
      </p>
      <div class="pv2-proof-body">
        <div class="pv2-proof-chart">
          <svg viewBox="0 0 560 150" role="img" aria-label="近期环境周期温度曲线">
            <path class="pv2-chain-grid" d="M28 20H532M28 54H532M28 88H532M28 124H532" />
            <path v-if="chainChart.areaPath" class="pv2-chain-area" :d="chainChart.areaPath" />
            <path
              v-if="chainChart.linePath"
              class="pv2-chain-line"
              :d="chainChart.linePath"
              pathLength="1"
            />
            <circle
              v-for="(point, index) in chainChart.points"
              :key="point.cycleId"
              class="pv2-chain-point"
              :class="{ 'is-latest': index === chainChart.points.length - 1 }"
              :cx="point.x"
              :cy="point.y"
              r="4.2"
            />
          </svg>
          <div class="pv2-proof-axis">
            <span v-for="cycle in chainChartCycles" :key="cycle.cycleId">{{ cycle.time }}</span>
          </div>
        </div>
        <div class="pv2-hash-stack">
          <div v-for="cycle in chainCycles.slice(0, 5)" :key="cycle.cycleId">
            <time>{{ cycle.time }}</time>
            <code :title="cycle.hash">{{ cycle.hashShort }}</code>
            <em>{{ cycle.demo ? '演示' : cycle.confirmed ? '已确认' : '待确认' }}</em>
          </div>
        </div>
      </div>
      <footer>
        <span>最新根哈希</span>
        <code :title="chainRootHash">{{ chainRootHash ? shortenChainHash(chainRootHash) : '暂无' }}</code>
        <small>区块高度 {{ blockHeight.toLocaleString('zh-CN') }}</small>
      </footer>
    </section>
  </div>

  <div v-show="false" class="production-page">
    <section class="production-command-head">
      <div class="production-command-head__title">
        <span>PRODUCTION COMMAND CENTER / {{ selectedSite.code }}</span>
        <h2>{{ selectedSite.name }}生产指挥中心</h2>
        <p>
          {{ selectedSite.crop }} · {{ selectedSite.area }} ·
          环境、设备、水肥与链上证据统一编排
        </p>
      </div>
      <div class="production-heading__actions">
        <button
          class="production-site-button"
          type="button"
          @click="greenhouseManagerOpen = true"
        >
          <LayoutGrid :size="16" />
          {{ selectedSite.name }}
          <span>管理</span>
        </button>
        <DataFreshnessTag
          :mode="dataFreshness.mode"
          :label="dataFreshness.label"
        />
        <button class="secondary-button" type="button" @click="exportCycleData">
          <ArrowRight :size="16" />
          导出周期数据
        </button>
      </div>
    </section>

    <section class="production-command-bar">
      <div>
        <span
          class="production-status-dot"
          :class="{
            'is-warning': environmentSummary.tone === 'is-warning',
            'is-alert': environmentSummary.tone === 'is-offline',
          }"
        ></span>
        <strong>
          {{
            environmentSummary.tone === 'is-offline'
              ? '环境指标告警'
              : environmentSummary.tone === 'is-warning'
                ? '环境指标偏离目标'
                : '温室运行正常'
          }}
        </strong>
        <span>
          {{ selectedSite.name }} · {{ selectedSite.metric }} ·
          {{ liveRefreshLabel }}
        </span>
      </div>
      <div>
        <button class="secondary-button" type="button" @click="refreshTodayData">
          刷新今日
        </button>
        <button
          class="secondary-button"
          type="button"
          :disabled="linkagePending || pendingLinkages.length === 0"
          :title="linkageButtonHint"
          @click="executeThresholdLinkage"
        >
          <Zap :size="16" />
          {{ linkagePending ? '正在联动' : linkageButtonLabel }}
        </button>
        <button
          class="primary-button"
          type="button"
          :disabled="ventilationPending"
          @click="sendVentCommand"
        >
          <Fan :size="16" />
          {{ ventilationPending ? '正在通风' : '立即通风' }}
        </button>
      </div>
      <p v-if="commandMessage" class="production-command-message">
        {{ commandMessage }}
      </p>
    </section>

    <div v-if="syncError" class="production-sync-banner" role="alert">
      <AlertTriangle :size="16" />
      <span>{{ syncError }}</span>
      <button
        type="button"
        :disabled="initialLoading"
        @click="retryBackendConnection"
      >
        <RefreshCw :size="13" />
        {{ initialLoading ? '重试中' : '重新连接' }}
      </button>
    </div>

    <section class="production-kpi-ribbon">
      <article
        v-for="metric in metrics"
        :key="metric.key"
        class="production-kpi"
        :class="`is-${metric.tone}`"
      >
        <span><component :is="metric.icon" :size="17" /></span>
        <div>
          <small>{{ metric.label }}</small>
          <strong>{{ metric.value }} <em>{{ metric.unit }}</em></strong>
          <p>{{ metric.note }}</p>
        </div>
      </article>
      <article class="production-kpi is-system">
        <span><Cpu :size="17" /></span>
        <div>
          <small>生产编排状态</small>
          <strong>{{ environmentSummary.label }}</strong>
          <p>{{ liveRefreshLabel }} · {{ displayDevices.length }} 台设备接入</p>
        </div>
      </article>
    </section>

    <div class="production-workspace">
      <aside class="production-summary">
        <div class="production-panel-heading">
          <div>
            <h3>环境摘要</h3>
            <p>实时遥测与阈值判断</p>
          </div>
          <span class="production-badge" :class="environmentSummary.tone">
            {{ environmentSummary.label }}
          </span>
        </div>

        <div class="production-metric-list">
          <button
            v-for="metric in metrics"
            :key="metric.key"
            class="production-metric-card"
            type="button"
            :aria-label="`调节${metric.label}阈值`"
            @click="openThresholdDialog(metric.key, $event)"
          >
            <span class="production-metric-icon" :class="`is-${metric.tone}`">
              <component :is="metric.icon" :size="17" />
            </span>
            <div>
              <span class="production-metric-label">
                {{ metric.label }}
                <i
                  class="production-metric-state"
                  :class="`is-${metricStates[metric.key].tone}`"
                >
                  {{ metricStates[metric.key].label }}
                </i>
              </span>
              <strong
                :class="{
                  'is-alert': metricStates[metric.key].tone === 'alert',
                  'is-warning': metricStates[metric.key].tone === 'warning',
                }"
              >
                {{ metric.value }} <small>{{ metric.unit }}</small>
              </strong>
              <em>{{ metric.note }}</em>
            </div>
            <span class="production-metric-edit">
              <SlidersHorizontal :size="13" />
              调节
            </span>
          </button>
        </div>

        <div class="production-crop">
          <div>
            <span><Sprout :size="18" /></span>
            <div>
              <strong>{{ cropDisplay.name }}</strong>
              <small>批次 {{ cropDisplay.batch }}</small>
            </div>
          </div>
          <dl>
            <div><dt>定植日期</dt><dd>{{ cropDisplay.plantingDate }}</dd></div>
            <div><dt>预计采收</dt><dd>{{ cropDisplay.harvestDate }}</dd></div>
            <div><dt>温室面积</dt><dd>{{ selectedSite.area }}</dd></div>
          </dl>
        </div>
      </aside>

      <section class="production-scene-panel">
        <div class="production-scene-heading">
          <div>
            <h3>{{ selectedSite.name }}三维数字孪生</h3>
            <p>{{ selectedSite.crop }} · 当前图层：{{ activeLayerLabel }}</p>
          </div>
          <div class="production-scene-actions">
            <div class="production-layer-tabs" role="tablist" aria-label="三维图层">
              <button
                v-for="mode in sceneModes"
                :key="mode.id"
                type="button"
                :class="{ 'is-active': sceneMode === mode.id }"
                :aria-selected="sceneMode === mode.id"
                role="tab"
                @click="changeMode(mode.id)"
              >
                <component :is="mode.icon" :size="14" />
                {{ mode.label }}
              </button>
            </div>
            <button
              class="production-icon-button"
              :class="{ 'is-active': autoRotate }"
              type="button"
              aria-label="自动环视"
              @click="autoRotate = !autoRotate"
            >
              <Rotate3d :size="16" />
            </button>
            <button class="production-icon-button" type="button" aria-label="重置视角" @click="resetScene">
              <LocateFixed :size="16" />
            </button>
          </div>
        </div>

        <GreenhouseScene
          v-if="false"
          ref="sceneRef"
          class="production-scene"
          :mode="sceneMode"
          :selected-id="selectedHotspot.id"
          :auto-rotate="autoRotate"
          :crop="selectedSite.crop"
          :active-zone="selectedWaterPlan?.zoneName ?? ''"
          :highlight-metric="linkedMetric"
          theme="dark"
          @select="selectHotspot"
        />
        <div class="production-scene-site">
          <span>当前温室</span>
          <strong>{{ selectedSite.name }}</strong>
          <small>{{ selectedSite.area }} · {{ selectedSite.status }}</small>
        </div>
      </section>

      <aside class="production-inspector">
        <section class="production-hotspot-card">
          <div class="production-panel-heading">
            <div>
              <h3>热点详情</h3>
              <p>{{ selectedHotspot.category === 'sensor' ? '环境感知设备' : '执行设备' }}</p>
            </div>
            <span
              class="production-badge"
              :class="{
                'is-ok': selectedHotspot.tone === 'ok' || selectedHotspot.tone === 'running',
                'is-warning': selectedHotspot.tone === 'warning',
                'is-offline': selectedHotspot.tone === 'offline',
              }"
            >
              {{ selectedHotspot.status }}
            </span>
          </div>
          <div class="production-hotspot-detail">
            <span>
              <component :is="selectedHotspot.icon" :size="21" />
            </span>
            <div>
              <small>{{ selectedHotspot.label }}</small>
              <strong>{{ selectedHotspot.value }} <em>{{ selectedHotspot.unit }}</em></strong>
            </div>
          </div>
          <p>{{ selectedHotspot.description }}</p>
          <div class="production-hotspot-events">
            <header>
              <strong>最近事件</strong>
              <span>{{ hotspotRecentEvents.length }} 条</span>
            </header>
            <ul>
              <li v-for="event in hotspotRecentEvents" :key="event.id">
                <time>{{ event.time }}</time>
                <span>{{ event.text }}</span>
              </li>
            </ul>
          </div>
          <dl>
            <div><dt>点位编号</dt><dd>{{ selectedHotspot.id.toUpperCase() }}</dd></div>
            <div><dt>空间坐标</dt><dd>{{ selectedHotspot.position.join(', ') }}</dd></div>
          </dl>
        </section>

        <section class="production-device-card">
          <div class="production-panel-heading">
            <div>
              <h3>执行设备</h3>
              <p>控制回执与在线状态</p>
            </div>
            <span class="production-badge is-ok">
              {{ displayDevices.length }} 台接入
            </span>
          </div>
          <p v-if="deviceRackNote" class="production-device-note">
            {{ deviceRackNote }}
          </p>
          <div class="production-device-list">
            <article
              v-for="device in displayDevices"
              :key="device.id"
              tabindex="0"
              @click="focusDeviceInScene(device)"
              @keydown.enter="focusDeviceInScene(device)"
            >
              <span class="production-device-state" :class="`is-${device.tone}`"></span>
              <div>
                <strong>{{ device.name }}</strong>
                <small>
                  {{ device.location }} ·
                  {{ devicePending[device.id] ? '指令处理中' : device.status }}
                </small>
              </div>
              <button
                class="production-switch"
                :class="{
                  'is-on': deviceSwitches[device.id],
                  'is-pending': devicePending[device.id],
                }"
                type="button"
                :disabled="
                  device.tone === 'offline'
                    || devicePending[device.id]
                    || deviceCommandLocked
                "
                :aria-pressed="deviceSwitches[device.id]"
                :aria-busy="devicePending[device.id]"
                :title="
                  deviceCommandLocked
                    ? '演示设备，未接入实时接口'
                    : device.tone === 'offline'
                      ? '设备离线，无法控制'
                      : `切换${device.name}`
                "
                :aria-label="`切换${device.name}`"
                @click.stop="toggleDevice(device)"
              ></button>
            </article>
            <p v-if="displayDevices.length === 0" class="production-device-empty">
              当前大棚尚未接入执行设备
            </p>
          </div>
        </section>
      </aside>
    </div>

    <div class="production-bottom-grid">
      <section class="production-panel">
        <div class="production-panel-heading">
          <div>
            <h3>实时环境采样</h3>
            <p>
              {{
                chartTimeline.source === 'live'
                  ? `近 ${liveWindowMinutes} 分钟实时采样 · ${liveSampleCount} 个点 · ${selectedSite.name}`
                  : chartTimeline.source === 'cycle'
                    ? `历史采样加载中 · 当前显示 ${liveSampleCount} 个环境周期基线`
                    : environmentTrend
                      ? `等待本轮遥测生成环境周期 · ${selectedSite.name}`
                      : `本地演示周期数据 · ${selectedSite.name}`
              }}
            </p>
          </div>
          <div class="production-trend-controls">
            <span
              class="production-trend-sampling"
              :class="liveSamplingStatus.tone"
            >
              <i aria-hidden="true"></i>
              <span>{{ liveSamplingStatus.label }}</span>
              <small>{{ liveSamplingCountdown }}</small>
            </span>
            <button
              class="production-trend-action"
              type="button"
              :disabled="liveRefreshing"
              :aria-busy="liveRefreshing"
              title="立即读取最新遥测并记录采样点"
              @click="refreshLiveData"
            >
              <RefreshCw :size="13" :class="{ 'is-spinning': liveRefreshing }" />
              {{ liveRefreshing ? '采样中' : '立即采样' }}
            </button>
            <button
              class="production-trend-pause"
              type="button"
              :aria-pressed="liveSamplingPaused"
              :title="liveSamplingPaused ? '继续自动采样' : '暂停自动采样'"
              :aria-label="liveSamplingPaused ? '继续自动采样' : '暂停自动采样'"
              @click="toggleLiveSampling"
            >
              <Play v-if="liveSamplingPaused" :size="14" />
              <Pause v-else :size="14" />
            </button>
            <div class="production-trend-periods" role="tablist">
              <button
                v-for="minutes in [5, 15, 30]"
                :key="minutes"
                type="button"
                :class="{ 'is-active': liveWindowMinutes === minutes }"
                @click="selectLiveWindow(minutes)"
              >
                {{ minutes }} 分钟
              </button>
            </div>
          </div>
        </div>
        <div
          class="production-trend-grid"
          :class="{ 'is-loading': trendLoading || telemetryHistoryLoading }"
        >
          <article
            v-for="card in trendMetricCards"
            :key="card.key"
            class="production-trend-card"
            :class="`is-${card.tone}`"
            @mouseenter="linkedMetric = card.key"
            @mouseleave="linkedMetric = ''"
            @focusin="linkedMetric = card.key"
            @focusout="linkedMetric = ''"
          >
            <header>
              <span class="production-trend-card__icon">
                <component :is="card.icon" :size="16" />
              </span>
              <div>
                <small>{{ card.label }}</small>
                <strong>
                  {{
                    card.key === 'light'
                      ? card.current.toLocaleString('zh-CN', {
                          maximumFractionDigits: 0,
                        })
                      : card.current.toFixed(1)
                  }}
                  <em>{{ card.unit }}</em>
                </strong>
              </div>
              <time>{{ card.updatedAt }}</time>
            </header>
            <svg
              class="production-trend-card__chart"
              viewBox="0 0 320 118"
              role="img"
              :aria-label="`${card.label}随时间变化`"
            >
              <path class="trend-grid" d="M20 12H300M20 50H300M20 88H300" />
              <path
                v-if="card.areaPath"
                class="trend-area"
                :d="card.areaPath"
              />
              <path v-if="card.hasLine" class="trend-line" :d="card.path" />
              <circle
                v-if="card.dot"
                class="trend-dot"
                :cx="card.dot.x"
                :cy="card.dot.y"
                r="3.5"
              />
              <text class="trend-axis" x="20" y="110" text-anchor="start">
                {{ card.axisLabels[0] }}
              </text>
              <text class="trend-axis" x="160" y="110" text-anchor="middle">
                {{ card.axisLabels[1] }}
              </text>
              <text class="trend-axis" x="300" y="110" text-anchor="end">
                {{ card.axisLabels[2] }}
              </text>
            </svg>
            <footer>
              <span>低 {{ card.minimumValue.toFixed(card.key === 'light' ? 0 : 1) }}</span>
              <span>高 {{ card.maximumValue.toFixed(card.key === 'light' ? 0 : 1) }}</span>
            </footer>
          </article>
          <p
            v-if="(trendLoading || telemetryHistoryLoading) && liveSampleCount === 0"
            class="production-trend-loading"
          >
            正在读取实时采样历史...
          </p>
          <p v-else-if="liveSampleCount === 0" class="production-trend-empty">
            正在建立实时采样窗口，新数据会自动进入曲线
          </p>
          <div v-else class="production-trend-footnote">
            <span>每 {{ liveRefreshSeconds }} 秒同步一次实时遥测，横坐标为采样时间</span>
            <span v-if="chartTimeline.source === 'live'">
              {{ liveSampleCount }} 个采样点 · 展示窗口 {{ liveWindowMinutes }} 分钟
            </span>
            <span v-else>
              {{ telemetryHistoryLoading ? '正在补充历史采样' : '当前显示环境周期基线' }}
            </span>
          </div>
        </div>
      </section>

      <section class="production-panel">
        <div class="production-panel-heading">
          <div>
            <h3>今日水肥计划</h3>
            <p>{{ waterRecipeSummary }}</p>
          </div>
          <div class="production-water-actions">
            <button
              class="text-button"
              type="button"
              @click="openWaterPlanForm"
            >
              <Plus :size="14" />
              新增计划
            </button>
            <button
              class="text-button"
              type="button"
              @click="recipeOpen = !recipeOpen"
            >
              {{ recipeOpen ? '收起配方' : '查看配方' }}
            </button>
          </div>
        </div>
        <div v-if="recipeOpen" class="production-recipe-detail">
          <div>
            <span>配方编号</span>
            <strong>{{ recipeDetails.recipeCode }}</strong>
          </div>
          <div>
            <span>版本</span>
            <strong>{{ recipeDetails.recipeVersion }}</strong>
          </div>
          <div>
            <span>EC 目标</span>
            <strong>{{ recipeDetails.ecTarget }}</strong>
          </div>
          <div>
            <span>pH 目标</span>
            <strong>{{ recipeDetails.phTarget }}</strong>
          </div>
          <div>
            <span>适用分区</span>
            <strong>{{ recipeDetails.zoneName }}</strong>
          </div>
          <div>
            <span>计划水量</span>
            <strong>{{ recipeDetails.waterVolume }} m³</strong>
          </div>
          <p>执行人：{{ recipeDetails.operatorName }}</p>
        </div>
        <div v-if="waterPlanFormOpen" class="production-water-form">
          <header>
            <strong>新增灌溉计划</strong>
            <button
              type="button"
              aria-label="关闭新增计划"
              @click="waterPlanFormOpen = false"
            >
              <X :size="15" />
            </button>
          </header>
          <div class="production-water-form__grid">
            <label>
              <span>计划时间</span>
              <input v-model="waterPlanForm.time" type="time" />
            </label>
            <label>
              <span>灌溉分区</span>
              <input v-model="waterPlanForm.zoneName" type="text" placeholder="例如 A区" />
            </label>
            <label>
              <span>计划水量</span>
              <input v-model.number="waterPlanForm.waterVolume" type="number" min="0.1" step="0.1" />
            </label>
            <label>
              <span>配方版本</span>
              <input v-model="waterPlanForm.recipeVersion" type="text" placeholder="例如 V3" />
            </label>
            <label>
              <span>EC 目标</span>
              <input v-model.number="waterPlanForm.ecTarget" type="number" min="0.1" step="0.1" />
            </label>
            <label>
              <span>pH 目标</span>
              <input v-model.number="waterPlanForm.phTarget" type="number" min="0.1" max="10" step="0.1" />
            </label>
          </div>
          <p v-if="waterPlanFormError" class="production-water-form__error">
            {{ waterPlanFormError }}
          </p>
          <footer>
            <button
              class="secondary-button"
              type="button"
              @click="waterPlanFormOpen = false"
            >
              取消
            </button>
            <button
              class="primary-button"
              type="button"
              :disabled="waterPlanCreating"
              @click="createWaterPlan"
            >
              {{ waterPlanCreating ? '保存中' : '保存计划' }}
            </button>
          </footer>
        </div>
        <div class="production-water-list">
          <article
            v-for="plan in waterPlans"
            :key="plan.planNo || `${plan.time}-${plan.zone}`"
            :class="{
              'is-selected': plan.planNo && plan.planNo === selectedWaterPlanNo,
              'is-pending': plan.planNo === waterPlanPendingNo,
            }"
            @click="selectWaterPlan(plan.planNo)"
          >
            <time>{{ plan.time }}</time>
            <div>
              <strong>{{ plan.zone }}</strong>
              <span>{{ plan.amount }}</span>
            </div>
            <div class="production-water-status">
              <em
                :class="{
                  'is-done': plan.status === '已完成',
                  'is-failed': plan.status === '执行失败',
                }"
              >
                {{ plan.status }}
              </em>
              <button
                v-if="plan.real && plan.rawStatus === 'PLANNED'"
                type="button"
                :disabled="plan.planNo === waterPlanPendingNo"
                @click.stop="executeWaterPlan(plan.planNo)"
              >
                <Play :size="13" />
                {{ plan.planNo === waterPlanPendingNo ? '执行中' : '执行' }}
              </button>
            </div>
          </article>
        </div>
        <button
          class="secondary-button is-full"
          type="button"
          :disabled="!hasPendingWaterPlan || Boolean(waterPlanPendingNo)"
          @click="executeWaterPlan()"
        >
          <Play :size="15" />
          {{ waterPlanPendingNo ? '正在执行' : '执行下一灌溉计划' }}
        </button>
        <p v-if="waterPlanMessage" class="production-water-message">
          {{ waterPlanMessage }}
        </p>
      </section>
    </div>

    <section class="production-chain-band">
      <header class="production-chain-head">
        <div class="production-chain-head__title">
          <span>{{ chainFeature.index }}</span>
          <div>
            <h3>{{ chainFeature.title }}</h3>
            <p>{{ chainFeature.description }}</p>
          </div>
        </div>
        <div class="production-chain-head__actions">
          <button
            class="secondary-button"
            type="button"
            :disabled="attestingCycle"
            @click="attestCycleNow"
          >
            <RefreshCw :size="14" />
            {{ attestingCycle ? '存证中' : '立即生成并存证' }}
          </button>
          <div
            class="production-chain-head__state"
            :class="{ 'is-pending': !chainConfirmed }"
          >
            <ShieldCheck :size="17" />
            <span>{{ chainConfirmed ? '联盟链已确认' : '等待链上回执' }}</span>
          </div>
        </div>
      </header>
      <p
        v-if="cycleAttestMessage"
        class="production-chain-attest-message"
        :class="{ 'is-error': cycleAttestError }"
        role="status"
      >
        <RefreshCw :size="13" :class="{ 'is-spinning': attestingCycle }" />
        {{ cycleAttestMessage }}
      </p>

      <div class="production-chain-body">
        <div class="production-chain-chart">
          <div class="production-chain-chart__head">
            <div>
              <strong>环境周期数据</strong>
              <span>
                近 {{ chainCycles.length }} 个采集周期 · 空气温度 ℃
                {{ chainHasDemo ? '· 含本地演示周期' : '' }}
              </span>
            </div>
            <span class="production-chain-chart__legend">
              <i></i>
              周期温度
            </span>
          </div>

          <svg viewBox="0 0 560 150" role="img" aria-label="近期环境周期温度曲线">
            <defs>
              <linearGradient id="productionChainArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#267a58" stop-opacity="0.22" />
                <stop offset="100%" stop-color="#267a58" stop-opacity="0" />
              </linearGradient>
            </defs>
            <path class="chain-grid" d="M30 26H540M30 74H540M30 122H540" />
            <path v-if="chainChart.areaPath" class="chain-area" :d="chainChart.areaPath" />
            <path v-if="chainChart.linePath" class="chain-line" :d="chainChart.linePath" />
            <circle
              v-for="(point, index) in chainChart.points"
              :key="point.cycleId"
              class="chain-point"
              :class="{ 'is-latest': index === chainChart.points.length - 1 }"
              :cx="point.x"
              :cy="point.y"
              r="3.5"
            />
          </svg>

          <div class="production-chain-chart__axis">
            <span v-for="cycle in chainChartCycles" :key="cycle.cycleId">
              {{ cycle.time }}
            </span>
          </div>
        </div>

        <div class="production-chain-hash">
          <div class="production-chain-hash__head">
            <strong>周期哈希链</strong>
            <span>每个周期只把根哈希写入联盟链</span>
          </div>
          <ol v-if="chainCycles.length > 0" class="production-chain-hash__list">
            <li
              v-for="(cycle, index) in chainCycles"
              :key="cycle.cycleId"
              :class="{ 'is-latest': index === 0 }"
            >
              <time>{{ cycle.time }}</time>
              <code :title="cycle.hash">{{ cycle.hashShort }}</code>
              <em
                :class="{
                  'is-pending': !cycle.confirmed && !cycle.demo,
                  'is-demo': cycle.demo,
                }"
              >
                {{ cycle.demo ? '演示' : cycle.confirmed ? '已确认' : '待确认' }}
              </em>
            </li>
          </ol>
          <p v-else class="production-chain-hash__empty">暂无环境周期数据</p>
        </div>
      </div>

      <footer class="production-chain-foot">
        <div class="production-chain-foot__root">
          <span>最新根哈希</span>
          <code :title="chainRootHash">
            {{ chainRootHash ? shortenChainHash(chainRootHash) : '暂无' }}
          </code>
        </div>
        <div class="production-chain-foot__meta">
          <span>
            <Blocks :size="14" />
            区块高度 {{ blockHeight.toLocaleString('zh-CN') }}
          </span>
          <span>
            <Network :size="14" />
            联盟链节点 4 / 4
          </span>
          <span>
            <CircleCheck :size="14" />
            {{ chainConfirmed ? '链上回执确认' : '等待回执' }}
          </span>
        </div>
      </footer>
    </section>

    <Teleport to="body">
      <Transition name="threshold">
        <div
          v-if="thresholdDialogOpen"
          class="threshold-overlay"
          @click.self="closeThresholdDialog"
        >
          <section
            ref="thresholdDialogRef"
            class="threshold-dialog"
            role="dialog"
            aria-modal="true"
            :aria-label="`${activeMetric.label}阈值调节`"
            tabindex="-1"
            @keydown="trapThresholdFocus"
          >
            <header class="threshold-dialog__header">
              <div>
                <span class="threshold-dialog__eyebrow">
                  <SlidersHorizontal :size="14" />
                  环境阈值调节
                </span>
                <h3>{{ activeMetric.label }}阈值</h3>
                <p>
                  {{ selectedSite.name }} · 实时值 {{ activeMetric.value }} {{ activeMetric.unit }} ·
                  {{ activeThreshold && !activeThreshold.enabled ? '自动联动已停用' : '自动联动已启用' }}
                </p>
              </div>
              <button
                class="threshold-dialog__close"
                type="button"
                aria-label="关闭阈值调节"
                @click="closeThresholdDialog"
              >
                <X :size="18" />
              </button>
            </header>

            <div class="threshold-dialog__body">
              <nav class="threshold-metric-list" aria-label="环境指标">
                <button
                  v-for="card in metricThresholdCards"
                  :key="card.key"
                  type="button"
                  :class="{ 'is-active': card.key === activeMetricCode }"
                  @click="selectThresholdMetric(card.key)"
                >
                  <span
                    class="threshold-metric-list__icon"
                    :class="`is-${card.tone}`"
                  >
                    <component :is="card.icon" :size="16" />
                  </span>
                  <span class="threshold-metric-list__text">
                    <strong>{{ card.label }}</strong>
                    <small v-if="card.threshold">
                      目标 {{ formatThresholdValue(card.threshold.targetMin) }}-{{ formatThresholdValue(card.threshold.targetMax) }} {{ card.unit }}
                    </small>
                    <small v-else>未配置阈值</small>
                  </span>
                  <em
                    :class="{
                      'is-off': card.threshold && !card.threshold.enabled,
                    }"
                  >
                    {{
                      card.threshold
                        ? card.threshold.enabled
                          ? '已启用'
                          : '已停用'
                        : '默认'
                    }}
                  </em>
                </button>
              </nav>

              <form class="threshold-form" @submit.prevent="saveThreshold">
                <div class="threshold-form__live">
                  <div>
                    <span>实时值</span>
                    <strong>
                      {{ activeMetric.value }}
                      <small>{{ activeMetric.unit }}</small>
                    </strong>
                  </div>
                  <div>
                    <span>最近更新</span>
                    <strong class="is-small">
                      {{
                        activeThreshold
                          ? activeThreshold.updatedAt.replace('T', ' ').slice(0, 16)
                          : '未配置'
                      }}
                    </strong>
                  </div>
                </div>

                <fieldset class="threshold-form__group">
                  <legend>目标范围</legend>
                  <div class="threshold-form__grid">
                    <label>
                      <span>目标下限</span>
                      <input
                        v-model.number="thresholdForm.targetMin"
                        type="number"
                        step="0.1"
                        inputmode="decimal"
                      />
                    </label>
                    <label>
                      <span>目标上限</span>
                      <input
                        v-model.number="thresholdForm.targetMax"
                        type="number"
                        step="0.1"
                        inputmode="decimal"
                      />
                    </label>
                  </div>
                </fieldset>

                <fieldset class="threshold-form__group">
                  <legend>告警范围</legend>
                  <div class="threshold-form__grid">
                    <label>
                      <span>告警下限</span>
                      <input
                        v-model.number="thresholdForm.alertMin"
                        type="number"
                        step="0.1"
                        inputmode="decimal"
                      />
                    </label>
                    <label>
                      <span>告警上限</span>
                      <input
                        v-model.number="thresholdForm.alertMax"
                        type="number"
                        step="0.1"
                        inputmode="decimal"
                      />
                    </label>
                  </div>
                </fieldset>

                <div class="threshold-form__grid">
                  <label>
                    <span>控制动作</span>
                    <select v-model="thresholdForm.controlAction">
                      <option
                        v-for="action in controlActions"
                        :key="action.value"
                        :value="action.value"
                      >
                        {{ action.label }}
                      </option>
                    </select>
                  </label>
                  <label class="threshold-form__switch">
                    <span>自动联动</span>
                    <span class="threshold-switch">
                      <input v-model="thresholdForm.enabled" type="checkbox" />
                      <span class="threshold-switch__track" aria-hidden="true"></span>
                      <em>{{ thresholdForm.enabled ? '启用' : '停用' }}</em>
                    </span>
                  </label>
                </div>

                <p class="threshold-form__hint">
                  超出告警范围时触发「{{ controlActionLabel(thresholdForm.controlAction) }}」，保存后立即生效。
                </p>

                <p
                  v-if="thresholdMessage"
                  class="threshold-form__message"
                  :class="{ 'is-error': thresholdMessageIsError }"
                >
                  {{ thresholdMessage }}
                </p>

                <footer class="threshold-dialog__footer">
                  <button
                    class="secondary-button"
                    type="button"
                    @click="closeThresholdDialog"
                  >
                    取消
                  </button>
                  <button
                    class="primary-button"
                    type="submit"
                    :disabled="thresholdSaving"
                  >
                    {{ thresholdSaving ? '保存中...' : '保存阈值' }}
                  </button>
                </footer>
              </form>
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>

    <GreenhouseManagerDialog
      :open="greenhouseManagerOpen"
      :sites="availableSites"
      :selected-id="selectedSite.id"
      :can-manage="canManageGreenhouses"
      @close="greenhouseManagerOpen = false"
      @select="chooseSite"
      @changed="handleGreenhouseChanged"
    />
  </div>
</template>

<style scoped>
.production-page {
  display: grid;
  gap: 14px;
}

.production-heading {
  align-items: center;
  margin-bottom: 0;
}

.production-heading__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.production-site-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 36px;
  padding: 0 11px;
  color: #294437;
  background: #ffffff;
  border: 1px solid #cddbd2;
  border-radius: 7px;
  font-size: 11px;
}

.production-site-button:hover,
.production-site-button:focus-visible {
  color: #1d7a58;
  border-color: #9fc8b1;
  background: #f1f8f3;
}

.production-site-button > span {
  padding: 2px 5px;
  color: #2d7a58;
  background: #e8f4ec;
  border-radius: 4px;
  font-size: 9px;
}

.production-command-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14px;
  min-height: 58px;
  padding: 10px 13px;
  background: #ffffff;
  border: 1px solid #dce5df;
  border-radius: 8px;
}

.production-sync-banner {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 42px;
  padding: 8px 11px;
  color: #8a621b;
  background: #fff9ec;
  border: 1px solid #ecd8a7;
  border-radius: 8px;
  font-size: 11px;
}

.production-sync-banner span {
  min-width: 0;
  flex: 1;
}

.production-sync-banner button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 29px;
  padding: 0 9px;
  color: #ffffff;
  background: #9d6918;
  border: 0;
  border-radius: 6px;
  font-size: 10px;
}

.production-sync-banner button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.production-command-message {
  flex-basis: 100%;
  margin: 0;
  color: #267a58;
  font-size: 10px;
}

.production-command-bar > div {
  display: flex;
  align-items: center;
  gap: 9px;
}

.production-command-bar .secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.production-command-bar span:last-child {
  color: #76837b;
  font-size: 10px;
}

.production-status-dot {
  width: 8px;
  height: 8px;
  background: #2e9b68;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgb(46 155 104 / 11%);
}

.production-status-dot.is-warning {
  background: #c08a2e;
  box-shadow: 0 0 0 4px rgb(192 138 46 / 13%);
}

.production-status-dot.is-alert {
  background: #b5554e;
  box-shadow: 0 0 0 4px rgb(181 85 78 / 13%);
}

.production-workspace {
  display: grid;
  grid-template-columns: 218px minmax(0, 1fr) 252px;
  gap: 13px;
  align-items: stretch;
}

.production-summary,
.production-scene-panel,
.production-inspector > section,
.production-panel {
  min-width: 0;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #dce5df;
  border-radius: 8px;
}

.production-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 58px;
  padding: 12px 14px;
  border-bottom: 1px solid #e5ebe7;
}

.production-panel-heading h3 {
  margin: 0;
  font-size: 14px;
}

.production-panel-heading p {
  margin: 3px 0 0;
  color: #77837c;
  font-size: 11px;
}

.production-badge {
  display: inline-flex;
  align-items: center;
  min-height: 23px;
  padding: 0 7px;
  color: #52645a;
  background: #eef3ef;
  border: 1px solid #d9e3dc;
  border-radius: 5px;
  font-size: 9px;
  white-space: nowrap;
}

.production-badge.is-ok {
  color: #267a58;
  background: #e6f2ea;
  border-color: #bedcca;
}

.production-badge.is-warning {
  color: #9d6918;
  background: #fbf2df;
  border-color: #ead19a;
}

.production-badge.is-offline {
  color: #a14e48;
  background: #faeceb;
  border-color: #e5c6c3;
}

.production-metric-list {
  display: grid;
}

.production-metric-card {
  display: grid;
  width: 100%;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 73px;
  padding: 10px 13px;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #e8ede9;
}

.production-metric-card:hover,
.production-metric-card:focus-visible {
  background: #f4f9f5;
  outline: none;
}

.production-metric-card:focus-visible {
  box-shadow: inset 0 0 0 2px #9fc8b1;
}

.production-metric-icon {
  display: grid;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 7px;
}

.production-metric-icon.is-temperature {
  color: #a7544d;
  background: #f8eaea;
}

.production-metric-icon.is-water {
  color: #286f9f;
  background: #e7f1f7;
}

.production-metric-icon.is-light {
  color: #a86e17;
  background: #fbf2df;
}

.production-metric-icon.is-soil {
  color: #267a58;
  background: #e5f2ea;
}

.production-metric-list span,
.production-metric-list strong,
.production-metric-list em {
  display: block;
}

.production-metric-card > div > span {
  color: #77837c;
  font-size: 11px;
}

.production-metric-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.production-metric-state {
  padding: 1px 6px;
  color: #267a58;
  background: #e8f4ee;
  border: 1px solid #c9e2d3;
  border-radius: 999px;
  font-size: 9px;
  font-style: normal;
  white-space: nowrap;
}

.production-metric-state.is-warning {
  color: #9d6918;
  background: #fbf2df;
  border-color: #ead19a;
}

.production-metric-state.is-alert {
  color: #a14e48;
  background: #faeceb;
  border-color: #e5c6c3;
}

.production-metric-state.is-muted {
  color: #7d8a82;
  background: #f1f4f2;
  border-color: #dde5e0;
}

.production-metric-list strong.is-warning {
  color: #9d6918;
}

.production-metric-list strong.is-alert {
  color: #a14e48;
}

.production-metric-list strong {
  margin-top: 2px;
  color: #1e2d25;
  font-size: 19px;
  font-weight: 600;
}

.production-metric-list small {
  color: #77837c;
  font-size: 10px;
  font-weight: 400;
}

.production-metric-list em {
  margin-top: 3px;
  color: #87928b;
  font-size: 10px;
  font-style: normal;
}

.production-metric-edit {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  color: #267a58;
  background: #eaf4ee;
  border: 1px solid #cbe1d3;
  border-radius: 999px;
  font-size: 9px;
  white-space: nowrap;
  opacity: 0.62;
  transition: opacity 0.15s ease;
}

.production-metric-card:hover .production-metric-edit,
.production-metric-card:focus-visible .production-metric-edit {
  opacity: 1;
}

.production-crop {
  margin: 13px;
  padding: 12px;
  background: #f5f8f6;
  border: 1px solid #e1e8e3;
  border-radius: 7px;
}

.production-crop > div {
  display: flex;
  align-items: center;
  gap: 9px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e2e9e4;
}

.production-crop > div > span {
  display: grid;
  width: 33px;
  height: 33px;
  place-items: center;
  color: #267a58;
  background: #e4f1e9;
  border-radius: 7px;
}

.production-crop strong,
.production-crop small {
  display: block;
}

.production-crop strong {
  font-size: 11px;
}

.production-crop small {
  margin-top: 2px;
  color: #77837c;
  font-size: 9px;
}

.production-crop dl {
  display: grid;
  gap: 8px;
  margin: 11px 0 0;
}

.production-crop dl div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 9px;
}

.production-crop dt {
  color: #77837c;
}

.production-crop dd {
  margin: 0;
  font-weight: 500;
}

.production-scene-panel {
  position: relative;
  display: grid;
  min-height: 560px;
  grid-template-rows: auto minmax(0, 1fr);
}

.production-scene-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 13px;
  border-bottom: 1px solid #e5ebe7;
}

.production-scene-heading h3 {
  margin: 0;
  font-size: 14px;
}

.production-scene-heading p {
  margin: 3px 0 0;
  color: #77837c;
  font-size: 10px;
}

.production-scene-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.production-layer-tabs {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: #eef3ef;
  border: 1px solid #dce5df;
  border-radius: 7px;
}

.production-layer-tabs button,
.production-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 31px;
  color: #56685e;
  background: transparent;
  border: 0;
  border-radius: 6px;
  font-size: 10px;
}

.production-layer-tabs button {
  padding: 0 9px;
}

.production-layer-tabs button.is-active {
  color: #ffffff;
  background: #1d7a58;
}

.production-icon-button {
  width: 32px;
  background: #ffffff;
  border: 1px solid #d6e0d9;
}

.production-icon-button.is-active {
  color: #1d7a58;
  background: #e8f3ed;
  border-color: #b9d8c7;
}

.production-scene {
  min-height: 0;
  height: 100%;
}

.production-scene-site {
  position: absolute;
  bottom: 14px;
  left: 14px;
  z-index: 5;
  display: grid;
  gap: 2px;
  padding: 9px 11px;
  color: #21352a;
  background: rgb(255 255 255 / 88%);
  border: 1px solid #d3e0d7;
  border-radius: 7px;
  backdrop-filter: blur(10px);
}

.production-scene-site span,
.production-scene-site small {
  color: #77837c;
  font-size: 9px;
}

.production-scene-site strong {
  font-size: 12px;
}

.production-inspector {
  display: grid;
  gap: 13px;
  min-height: 100%;
  grid-template-rows: auto minmax(0, 1fr);
  align-content: stretch;
}

.production-hotspot-detail {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 14px 5px;
}

.production-hotspot-detail > span {
  display: grid;
  width: 41px;
  height: 41px;
  place-items: center;
  color: #ffffff;
  background: #267a58;
  border-radius: 8px;
}

.production-hotspot-detail small,
.production-hotspot-detail strong {
  display: block;
}

.production-hotspot-detail small {
  color: #77837c;
  font-size: 9px;
}

.production-hotspot-detail strong {
  margin-top: 3px;
  font-size: 17px;
  font-weight: 600;
}

.production-hotspot-detail em {
  color: #77837c;
  font-size: 9px;
  font-style: normal;
  font-weight: 400;
}

.production-hotspot-card > p {
  min-height: 47px;
  margin: 9px 14px 10px;
  color: #66736b;
  font-size: 10px;
  line-height: 1.6;
}

.production-hotspot-events {
  margin: 0 14px 4px;
  padding: 10px 11px;
  background: #f5f9f6;
  border: 1px solid #e0e9e3;
  border-radius: 7px;
}

.production-hotspot-events header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 7px;
  border-bottom: 1px solid #e1e9e3;
}

.production-hotspot-events header strong {
  color: #2b3b32;
  font-size: 10px;
}

.production-hotspot-events header span {
  color: #829087;
  font-size: 9px;
}

.production-hotspot-events ul {
  display: grid;
  gap: 6px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.production-hotspot-events li {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.production-hotspot-events time {
  color: #267a58;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.production-hotspot-events li span {
  overflow: hidden;
  color: #5f6d65;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-hotspot-card dl {
  display: grid;
  margin: 0;
  padding: 0 14px 13px;
}

.production-hotspot-card dl div {
  display: flex;
  justify-content: space-between;
  gap: 9px;
  padding: 9px 0;
  border-top: 1px solid #e8ede9;
}

.production-hotspot-card dt {
  color: #77837c;
  font-size: 9px;
}

.production-hotspot-card dd {
  max-width: 142px;
  margin: 0;
  overflow: hidden;
  font-size: 9px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-device-list {
  display: grid;
  min-height: 0;
  grid-auto-rows: minmax(57px, 1fr);
  align-content: stretch;
}

.production-device-empty {
  align-self: center;
  margin: 0;
  padding: 18px 13px;
  color: #7b8880;
  font-size: 10px;
  text-align: center;
}

.production-device-card {
  display: grid;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr);
}

.production-device-list article {
  display: grid;
  grid-template-columns: 7px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  min-height: 57px;
  padding: 9px 13px;
  border-bottom: 1px solid #e8ede9;
}

.production-device-list article:last-child {
  border-bottom: 0;
}

.production-device-state {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.production-device-state.is-running,
.production-device-state.is-ok {
  background: #2e9b68;
}

.production-device-state.is-warning {
  background: #d4952f;
}

.production-device-state.is-offline {
  background: #99a59e;
}

.production-device-list strong,
.production-device-list small {
  display: block;
}

.production-device-list strong {
  font-size: 11px;
}

.production-device-list small {
  margin-top: 2px;
  color: #7b8880;
  font-size: 10px;
}

.production-switch {
  position: relative;
  width: 34px;
  height: 20px;
  padding: 0;
  background: #c8d3cc;
  border: 0;
  border-radius: 10px;
}

.production-switch::after {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 14px;
  height: 14px;
  content: "";
  background: #ffffff;
  border-radius: 50%;
  transition: transform 140ms ease;
}

.production-switch.is-on {
  background: #2e9b68;
}

.production-switch.is-on::after {
  transform: translateX(14px);
}

.production-switch:disabled {
  cursor: not-allowed;
  opacity: 0.46;
}

.production-switch.is-pending {
  cursor: wait;
  opacity: 0.72;
}

.production-recipe-detail {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 13px 14px 10px;
  background: #f5f9f6;
  border-bottom: 1px solid #e1e9e3;
}

.production-recipe-detail > div {
  min-width: 0;
}

.production-recipe-detail span,
.production-recipe-detail strong {
  display: block;
}

.production-recipe-detail span {
  color: #77837c;
  font-size: 9px;
}

.production-recipe-detail strong {
  margin-top: 3px;
  overflow: hidden;
  color: #26382e;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-recipe-detail p {
  grid-column: 1 / -1;
  margin: 0;
  color: #65736c;
  font-size: 9px;
}

.production-water-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.production-water-actions .text-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.production-water-form {
  margin: 0 14px 10px;
  padding: 12px;
  background: #f7faf8;
  border: 1px solid #dce6df;
  border-radius: 8px;
}

.production-water-form header,
.production-water-form footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.production-water-form header strong {
  color: #26382e;
  font-size: 12px;
}

.production-water-form header button {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  color: #68776f;
  background: #ffffff;
  border: 1px solid #dce5df;
  border-radius: 6px;
}

.production-water-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  margin: 12px 0;
}

.production-water-form label > span {
  display: block;
  margin-bottom: 5px;
  color: #6f7d75;
  font-size: 9px;
}

.production-water-form input {
  width: 100%;
  min-height: 34px;
  padding: 0 9px;
  color: #26382e;
  background: #ffffff;
  border: 1px solid #cddbd2;
  border-radius: 6px;
  font-size: 11px;
}

.production-water-form input:focus {
  border-color: #5aa27c;
  outline: none;
  box-shadow: 0 0 0 3px rgb(90 162 124 / 14%);
}

.production-water-form__error {
  margin: 0 0 10px;
  color: #a14e48;
  font-size: 10px;
}

.production-water-form footer {
  justify-content: flex-end;
}

.production-bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 0.75fr);
  gap: 13px;
}

.production-chain-band {
  min-width: 0;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #dce5df;
  border-radius: 8px;
}

.production-chain-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 58px;
  padding: 12px 14px;
  border-bottom: 1px solid #e5ebe7;
}

.production-chain-head__title {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  min-width: 0;
}

.production-chain-head__title > span {
  color: #267a58;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
}

.production-chain-head__title h3 {
  margin: 0;
  color: #1e2d25;
  font-size: 14px;
}

.production-chain-head__title p {
  margin: 3px 0 0;
  color: #77837c;
  font-size: 10px;
  line-height: 1.6;
}

.production-chain-head__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.production-chain-head__state {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  min-height: 23px;
  padding: 0 8px;
  color: #267a58;
  background: #e6f2ea;
  border: 1px solid #bedcca;
  border-radius: 5px;
  font-size: 9px;
  white-space: nowrap;
}

.production-chain-head__state.is-pending {
  color: #9d6918;
  background: #fbf2df;
  border-color: #ead19a;
}

.production-chain-attest-message {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 8px 14px;
  color: #267a58;
  background: #f1f8f3;
  border-bottom: 1px solid #dce9e0;
  font-size: 10px;
}

.production-chain-attest-message.is-error {
  color: #a14e48;
  background: #fff6f5;
  border-bottom-color: #efd3d0;
}

.production-chain-body {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(255px, 0.85fr);
}

.production-chain-chart {
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
  padding: 14px 16px 16px;
  border-right: 1px solid #e5ebe7;
}

.production-chain-chart__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.production-chain-chart__head strong,
.production-chain-chart__head span {
  display: block;
}

.production-chain-chart__head strong {
  color: #2b3b32;
  font-size: 12px;
}

.production-chain-chart__head span {
  margin-top: 3px;
  color: #77837c;
  font-size: 10px;
}

.production-chain-chart__legend {
  display: inline-flex !important;
  align-items: center;
  gap: 6px;
  margin-top: 0 !important;
  white-space: nowrap;
}

.production-chain-chart__legend i {
  width: 16px;
  height: 2px;
  background: #267a58;
  border-radius: 999px;
}

.production-chain-chart svg {
  width: 100%;
  height: 150px;
}

.chain-grid {
  fill: none;
  stroke: #e4ece7;
  stroke-width: 1;
  stroke-dasharray: 4 5;
}

.chain-area {
  fill: url(#productionChainArea);
}

.chain-line {
  fill: none;
  stroke: #267a58;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.2;
}

.chain-point {
  fill: #ffffff;
  stroke: #267a58;
  stroke-width: 2;
}

.chain-point.is-latest {
  fill: #267a58;
  stroke: #ffffff;
  stroke-width: 2.4;
}

.production-chain-chart__axis {
  display: flex;
  justify-content: space-between;
  gap: 6px;
  padding: 0 12px;
  color: #88948c;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.production-chain-hash {
  display: grid;
  align-content: start;
  gap: 10px;
  min-width: 0;
  padding: 14px 16px 16px;
}

.production-chain-hash__head strong,
.production-chain-hash__head span {
  display: block;
}

.production-chain-hash__head strong {
  color: #2b3b32;
  font-size: 12px;
}

.production-chain-hash__head span {
  margin-top: 3px;
  color: #77837c;
  font-size: 10px;
}

.production-chain-hash__list {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.production-chain-hash__list li {
  position: relative;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 6px 0 6px 17px;
}

.production-chain-hash__list li::before {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 4px;
  width: 1px;
  background: #dde6e0;
}

.production-chain-hash__list li:first-child::before {
  top: 50%;
}

.production-chain-hash__list li:last-child::before {
  bottom: 50%;
}

.production-chain-hash__list li::after {
  content: "";
  position: absolute;
  top: calc(50% - 3px);
  left: 1px;
  width: 7px;
  height: 7px;
  background: #ffffff;
  border: 1px solid #a9c9b3;
  border-radius: 50%;
}

.production-chain-hash__list li.is-latest::after {
  background: #267a58;
  border-color: #267a58;
  box-shadow: 0 0 0 3px rgb(38 122 88 / 12%);
}

.production-chain-hash__list time {
  color: #77837c;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.production-chain-hash__list code {
  overflow: hidden;
  color: #2f6b4e;
  font-family: Consolas, "Courier New", monospace;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-chain-hash__list em {
  color: #267a58;
  font-size: 9px;
  font-style: normal;
  white-space: nowrap;
}

.production-chain-hash__list em.is-pending {
  color: #9d6918;
}

.production-chain-hash__list em.is-demo {
  color: #88948c;
}

.production-chain-hash__empty {
  margin: 0;
  color: #77837c;
  font-size: 11px;
}

.production-chain-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14px;
  padding: 11px 14px;
  background: #f7faf8;
  border-top: 1px solid #e5ebe7;
}

.production-chain-foot__root {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.production-chain-foot__root span {
  color: #77837c;
  font-size: 10px;
  white-space: nowrap;
}

.production-chain-foot__root code {
  overflow: hidden;
  color: #2f6b4e;
  font-family: Consolas, "Courier New", monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-chain-foot__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.production-chain-foot__meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #52645a;
  font-size: 10px;
}

.production-trend-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
}

.production-trend-sampling {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 28px;
  padding: 0 8px;
  color: #53665c;
  background: #f6faf7;
  border: 1px solid #dce7df;
  border-radius: 7px;
  font-size: 9px;
  white-space: nowrap;
}

.production-trend-sampling i {
  width: 7px;
  height: 7px;
  background: #2e9b68;
  border-radius: 50%;
}

.production-trend-sampling.is-live i {
  box-shadow: 0 0 0 4px rgb(46 155 104 / 12%);
  animation: production-sampling-pulse 1.8s ease-out infinite;
}

.production-trend-sampling.is-loading i {
  background: #d4952f;
  animation: production-sampling-pulse 1.1s ease-out infinite;
}

.production-trend-sampling.is-warning i {
  background: #b5554e;
  box-shadow: 0 0 0 4px rgb(181 85 78 / 11%);
}

.production-trend-sampling.is-paused i {
  background: #8b978f;
}

.production-trend-sampling small {
  color: #7d8a82;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.production-trend-action,
.production-trend-pause {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 28px;
  color: #ffffff;
  background: #217354;
  border: 1px solid #217354;
  border-radius: 7px;
  font-size: 9px;
}

.production-trend-action {
  padding: 0 9px;
}

.production-trend-pause {
  width: 28px;
  padding: 0;
  color: #53665c;
  background: #ffffff;
  border-color: #d5e1d8;
}

.production-trend-action:hover,
.production-trend-action:focus-visible {
  background: #185f45;
  border-color: #185f45;
}

.production-trend-pause:hover,
.production-trend-pause:focus-visible {
  color: #1d7a58;
  background: #eff7f2;
  border-color: #9fc8b1;
}

.production-trend-action:disabled {
  cursor: wait;
  opacity: 0.72;
}

.production-trend-action .is-spinning,
.production-chain-attest-message .is-spinning {
  animation: production-spin 900ms linear infinite;
}

@keyframes production-sampling-pulse {
  0% {
    box-shadow: 0 0 0 0 rgb(46 155 104 / 28%);
  }
  75%,
  100% {
    box-shadow: 0 0 0 7px rgb(46 155 104 / 0%);
  }
}

@keyframes production-spin {
  to {
    transform: rotate(360deg);
  }
}

.production-trend-periods {
  display: inline-flex;
  gap: 3px;
  padding: 3px;
  background: #eef3ef;
  border: 1px solid #dce5df;
  border-radius: 7px;
}

.production-trend-periods button {
  min-height: 28px;
  padding: 0 8px;
  color: #62736a;
  background: transparent;
  border: 0;
  border-radius: 5px;
  font-size: 9px;
}

.production-trend-periods button.is-active {
  color: #ffffff;
  background: #217354;
}

.production-trend-periods button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.production-trend-grid {
  position: relative;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 14px;
}

.production-trend-grid.is-loading {
  opacity: 0.72;
}

.production-trend-card {
  min-width: 0;
  padding: 12px;
  color: #b45f58;
  background: #fffafa;
  border: 1px solid #efd9d7;
  border-radius: 8px;
}

.production-trend-card.is-water {
  color: #286f9f;
  background: #f8fbfd;
  border-color: #d5e6f0;
}

.production-trend-card.is-light {
  color: #a86e17;
  background: #fffcf5;
  border-color: #efdfb7;
}

.production-trend-card.is-soil {
  color: #267a58;
  background: #f8fcf9;
  border-color: #d3e8dc;
}

.production-trend-card header {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.production-trend-card__icon {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  background: rgb(255 255 255 / 76%);
  border: 1px solid currentColor;
  border-radius: 7px;
}

.production-trend-card small,
.production-trend-card strong {
  display: block;
}

.production-trend-card small {
  color: #718078;
  font-size: 9px;
}

.production-trend-card strong {
  margin-top: 2px;
  color: #26382e;
  font-size: 16px;
}

.production-trend-card strong em {
  color: #718078;
  font-size: 9px;
  font-style: normal;
  font-weight: 400;
}

.production-trend-card time {
  color: #7d8a82;
  font-size: 9px;
}

.production-trend-card__icon svg {
  width: 16px;
  height: 16px;
  margin: 0;
}

.production-trend-card__chart {
  display: block;
  width: 100%;
  height: 96px;
  margin-top: 6px;
}

.trend-axis {
  fill: #8a978f;
  font-size: 9px;
  font-weight: 500;
}

.trend-grid {
  fill: none;
  stroke: #dfe8e1;
  stroke-width: 1;
  stroke-dasharray: 4 5;
}

.trend-area {
  fill: currentColor;
  opacity: 0.08;
}

.trend-line {
  fill: none;
  stroke: currentColor;
  stroke-width: 2.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.trend-dot {
  fill: currentColor;
  transform-box: fill-box;
  transform-origin: center;
  animation: production-trend-dot 2s ease-in-out infinite;
}

@keyframes production-trend-dot {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.72;
    transform: scale(1.45);
  }
}

.production-trend-card footer {
  display: flex;
  justify-content: space-between;
  color: #809087;
  font-size: 9px;
}

.production-trend-loading,
.production-trend-empty,
.production-trend-footnote {
  grid-column: 1 / -1;
}

.production-trend-loading,
.production-trend-empty {
  margin: 0;
  padding: 8px 0;
  color: #65736c;
  text-align: center;
  font-size: 10px;
}

.production-trend-footnote {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 2px;
  color: #7d8a82;
  font-size: 9px;
}

.production-water-list {
  display: grid;
  padding: 4px 14px 10px;
}

.production-water-list article {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 51px;
  padding: 0 5px;
  border-radius: 7px;
  border-bottom: 1px solid #e8ede9;
  transition: background-color 140ms ease, box-shadow 140ms ease;
}

.production-water-list article:hover,
.production-water-list article.is-selected {
  background: #f1f8f3;
  box-shadow: inset 3px 0 #5aa27c;
}

.production-water-list article.is-pending {
  background: #fffbf1;
  box-shadow: inset 0 0 0 1px #d4952f;
}

.production-water-list time {
  color: #267a58;
  font-size: 11px;
  font-weight: 600;
}

.production-water-list strong,
.production-water-list span {
  display: block;
}

.production-water-list strong {
  font-size: 10px;
}

.production-water-list span {
  margin-top: 2px;
  color: #7b8880;
  font-size: 9px;
}

.production-water-list em {
  color: #9d6918;
  font-size: 9px;
  font-style: normal;
}

.production-water-list em.is-done {
  color: #267a58;
}

.production-water-list em.is-failed {
  color: #a14e48;
}

.production-water-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.production-water-status button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 27px;
  padding: 0 8px;
  color: #ffffff;
  background: #267a58;
  border: 0;
  border-radius: 6px;
  font-size: 9px;
}

.production-water-status button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.is-full {
  width: calc(100% - 28px);
  margin: 0 14px 14px;
}

.is-full:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.production-water-message {
  margin: -4px 14px 12px;
  color: #267a58;
  font-size: 10px;
}

.threshold-overlay {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: grid;
  padding: 24px;
  place-items: center;
  background: rgb(24 40 32 / 46%);
  backdrop-filter: blur(2px);
}

.threshold-dialog {
  display: grid;
  width: min(68vw, 1180px);
  height: min(70vh, 760px);
  min-height: 520px;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #cddbd2;
  border-radius: 10px;
  box-shadow: 0 26px 72px rgb(24 40 32 / 32%);
}

.threshold-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 20px;
  border-bottom: 1px solid #e5ebe7;
}

.threshold-dialog__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #267a58;
  font-size: 10px;
}

.threshold-dialog__header h3 {
  margin: 6px 0 0;
  color: #1e2d25;
  font-size: 18px;
  font-weight: 600;
}

.threshold-dialog__header p {
  margin: 5px 0 0;
  color: #7b8880;
  font-size: 10px;
}

.threshold-dialog__close {
  display: grid;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  place-items: center;
  color: #68776f;
  background: #f4f7f5;
  border: 1px solid #dde6e0;
  border-radius: 7px;
}

.threshold-dialog__close:hover {
  color: #1d7a58;
  background: #eaf4ee;
}

.threshold-dialog__body {
  display: grid;
  min-height: 0;
  grid-template-columns: minmax(210px, 268px) minmax(0, 1fr);
}

.threshold-metric-list {
  display: grid;
  align-content: start;
  gap: 7px;
  padding: 14px;
  overflow-y: auto;
  background: #f7faf8;
  border-right: 1px solid #e5ebe7;
}

.threshold-metric-list > button {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 10px;
  color: #2b3b32;
  text-align: left;
  background: #ffffff;
  border: 1px solid #e1e9e3;
  border-radius: 7px;
}

.threshold-metric-list > button:hover,
.threshold-metric-list > button.is-active {
  background: #eef7f1;
  border-color: #9fc8b1;
}

.threshold-metric-list__icon {
  display: grid;
  width: 31px;
  height: 31px;
  place-items: center;
  border-radius: 7px;
}

.threshold-metric-list__icon.is-temperature {
  color: #a7544d;
  background: #f8eaea;
}

.threshold-metric-list__icon.is-water {
  color: #286f9f;
  background: #e7f1f7;
}

.threshold-metric-list__icon.is-light {
  color: #a86e17;
  background: #fbf2df;
}

.threshold-metric-list__icon.is-soil {
  color: #267a58;
  background: #e5f2ea;
}

.threshold-metric-list__text strong,
.threshold-metric-list__text small {
  display: block;
}

.threshold-metric-list__text strong {
  font-size: 12px;
  font-weight: 600;
}

.threshold-metric-list__text small {
  margin-top: 3px;
  color: #7b8880;
  font-size: 9px;
}

.threshold-metric-list em {
  padding: 3px 7px;
  color: #267a58;
  background: #eaf4ee;
  border-radius: 999px;
  font-size: 9px;
  font-style: normal;
  white-space: nowrap;
}

.threshold-metric-list em.is-off {
  color: #9d6918;
  background: #fbf2df;
}

.threshold-form {
  display: grid;
  align-content: start;
  gap: 13px;
  padding: 16px 20px 18px;
  overflow-y: auto;
}

.threshold-form__live {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 12px 14px;
  background: #f5f8f6;
  border: 1px solid #e1e8e3;
  border-radius: 7px;
}

.threshold-form__live span,
.threshold-form__live strong {
  display: block;
}

.threshold-form__live span {
  color: #77837c;
  font-size: 10px;
}

.threshold-form__live strong {
  margin-top: 3px;
  color: #1e2d25;
  font-size: 20px;
  font-weight: 600;
}

.threshold-form__live strong.is-small {
  font-size: 12px;
  font-weight: 500;
}

.threshold-form__live small {
  color: #77837c;
  font-size: 10px;
  font-weight: 400;
}

.threshold-form__group {
  margin: 0;
  padding: 0;
  border: 0;
}

.threshold-form__group legend {
  padding: 0 0 7px;
  color: #77837c;
  font-size: 10px;
}

.threshold-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.threshold-form label > span {
  display: block;
  margin-bottom: 6px;
  color: #77837c;
  font-size: 10px;
}

.threshold-form input[type="number"],
.threshold-form select {
  width: 100%;
  min-height: 38px;
  padding: 0 10px;
  color: #1e2d25;
  background: #ffffff;
  border: 1px solid #cddbd2;
  border-radius: 7px;
  font-size: 12px;
}

.threshold-form input[type="number"]:focus,
.threshold-form select:focus {
  border-color: #5aa27c;
  outline: none;
  box-shadow: 0 0 0 3px rgb(90 162 124 / 18%);
}

.threshold-form__switch > span:first-child {
  margin-bottom: 6px;
}

.threshold-switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.threshold-switch input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.threshold-switch__track {
  position: relative;
  display: inline-block;
  width: 42px;
  height: 23px;
  background: #cbd7d0;
  border-radius: 999px;
  transition: background 0.16s ease;
}

.threshold-switch__track::after {
  content: "";
  position: absolute;
  top: 3px;
  left: 3px;
  width: 17px;
  height: 17px;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: 0 1px 3px rgb(24 40 32 / 24%);
  transition: transform 0.16s ease;
}

.threshold-switch input:checked + .threshold-switch__track {
  background: #2f8b64;
}

.threshold-switch input:checked + .threshold-switch__track::after {
  transform: translateX(19px);
}

.threshold-switch input:focus-visible + .threshold-switch__track {
  box-shadow: 0 0 0 3px rgb(47 139 100 / 22%);
}

.threshold-switch em {
  color: #4a5850;
  font-size: 10px;
  font-style: normal;
}

.threshold-form__hint {
  margin: 0;
  color: #7b8880;
  font-size: 10px;
  line-height: 1.6;
}

.threshold-form__message {
  margin: 0;
  padding: 9px 11px;
  color: #267a58;
  background: #eaf4ee;
  border: 1px solid #cbe1d3;
  border-radius: 7px;
  font-size: 10px;
}

.threshold-form__message.is-error {
  color: #a14e48;
  background: #faeceb;
  border-color: #e5c6c3;
}

.threshold-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 3px;
}

.threshold-enter-active,
.threshold-leave-active {
  transition: opacity 0.18s ease;
}

.threshold-enter-active .threshold-dialog,
.threshold-leave-active .threshold-dialog {
  transition: transform 0.18s ease;
}

.threshold-enter-from,
.threshold-leave-to {
  opacity: 0;
}

.threshold-enter-from .threshold-dialog,
.threshold-leave-to .threshold-dialog {
  transform: translateY(10px) scale(0.99);
}

@media (max-width: 1240px) {
  .production-workspace {
    grid-template-columns: 210px minmax(0, 1fr);
  }

  .production-inspector {
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .production-heading,
  .production-command-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .production-heading__actions,
  .production-command-bar > div {
    width: 100%;
    flex-wrap: wrap;
  }

  .production-workspace {
    grid-template-columns: 1fr;
  }

  .production-summary {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .production-summary > .production-panel-heading {
    grid-column: 1 / -1;
  }

  .production-metric-list {
    display: contents;
  }

  .production-metric-card {
    border-right: 1px solid #e8ede9;
  }

  .production-metric-edit {
    opacity: 1;
  }

  .production-crop {
    display: none;
  }

  .production-inspector,
  .production-bottom-grid {
    grid-template-columns: 1fr;
  }

  .production-trend-controls {
    width: 100%;
    justify-content: flex-start;
  }

  .threshold-overlay {
    padding: 14px;
  }

  .threshold-dialog {
    width: min(92vw, 720px);
    height: auto;
    max-height: 88vh;
    min-height: 0;
  }

  .threshold-dialog__body {
    grid-template-columns: 1fr;
    overflow-y: auto;
  }

  .threshold-metric-list {
    grid-auto-flow: column;
    grid-auto-columns: minmax(168px, 1fr);
    overflow-x: auto;
    overflow-y: hidden;
    border-right: 0;
    border-bottom: 1px solid #e5ebe7;
  }

  .production-chain-body {
    grid-template-columns: 1fr;
  }

  .production-chain-chart {
    border-right: 0;
    border-bottom: 1px solid #e5ebe7;
  }
}

@media (max-width: 640px) {
  .production-summary {
    grid-template-columns: 1fr;
  }

  .production-metric-list {
    display: grid;
  }

  .production-metric-card {
    border-right: 0;
  }

  .production-scene-heading,
  .production-heading__actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .production-scene-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .production-site-button {
    width: 100%;
  }

  .production-layer-tabs {
    width: 100%;
  }

  .production-layer-tabs button {
    flex: 1;
  }

  .production-trend-grid {
    grid-template-columns: 1fr;
  }

  .production-bottom-grid .production-panel-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .production-water-form__grid {
    grid-template-columns: 1fr;
  }

  .production-trend-footnote {
    align-items: flex-start;
    flex-direction: column;
  }

  .threshold-dialog__header {
    padding: 14px 14px;
  }

  .threshold-form {
    padding: 14px 14px 16px;
  }

  .threshold-form__grid,
  .threshold-form__live {
    grid-template-columns: 1fr;
  }

  .threshold-dialog__footer {
    flex-direction: column-reverse;
  }

  .threshold-dialog__footer button {
    width: 100%;
  }

  .production-chain-head,
  .production-chain-foot {
    align-items: flex-start;
    flex-direction: column;
  }

  .production-chain-head__actions {
    width: 100%;
    justify-content: flex-start;
  }

  .production-chain-chart,
  .production-chain-hash {
    padding: 14px 16px 16px;
  }

  .production-chain-chart svg {
    height: 132px;
  }
}

/* Production v2: full-width digital twin command surface */
.production-v2 {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 14px;
}

.pv2-hero,
.pv2-statusbar,
.pv2-sync-warning,
.pv2-metric-ribbon,
.pv2-stage,
.pv2-proof {
  grid-column: 1 / -1;
}

.pv2-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  background:
    linear-gradient(100deg, rgb(79 224 160 / 8%), transparent 42%),
    rgb(8 24 17 / 92%);
  border: 1px solid rgb(94 226 165 / 17%);
  border-radius: 8px;
}

.pv2-hero__identity > span,
.pv2-trend-wall header > div > span,
.pv2-water header > div > span,
.pv2-proof header > div > span {
  color: #4fe0a0;
  font-family: Consolas, monospace;
  font-size: 8px;
  letter-spacing: 0.14em;
}

.pv2-hero h2 {
  margin: 5px 0 0;
  font-size: 24px;
}

.pv2-hero p {
  margin: 6px 0 0;
  color: #789487;
  font-size: 10px;
}

.pv2-hero__actions,
.pv2-statusbar__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.pv2-hero__actions > button,
.pv2-statusbar__actions button,
.pv2-trend-wall__tools > button,
.pv2-water header button,
.pv2-proof header button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 33px;
  padding: 0 10px;
  color: #9bb9aa;
  background: rgb(255 255 255 / 3%);
  border: 1px solid rgb(100 225 166 / 15%);
  border-radius: 7px;
  font-size: 9px;
}

.pv2-statusbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 11px 14px;
  background: rgb(7 21 15 / 88%);
  border: 1px solid rgb(98 226 167 / 13%);
  border-radius: 8px;
}

.pv2-statusbar > div:first-child {
  display: flex;
  align-items: center;
  gap: 9px;
}

.pv2-statusbar__dot {
  width: 9px;
  height: 9px;
  background: #4fe0a0;
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgb(79 224 160 / 9%);
}

.pv2-statusbar__dot.is-warning {
  background: #f1b650;
}

.pv2-statusbar__dot.is-alert {
  background: #ff8175;
}

.pv2-statusbar strong,
.pv2-statusbar small {
  display: block;
}

.pv2-statusbar strong {
  color: #e8fff5;
  font-size: 11px;
}

.pv2-statusbar small {
  margin-top: 3px;
  color: #688477;
  font-size: 8px;
}

.pv2-statusbar > p {
  grid-column: 1 / -1;
  width: 100%;
  margin: 0;
  color: #8fc6aa;
  font-size: 9px;
}

.pv2-statusbar__actions .is-primary {
  color: #062116;
  background: #58e3a1;
  border-color: #58e3a1;
}

.pv2-sync-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 11px;
  color: #f5c76d;
  background: rgb(241 182 80 / 7%);
  border: 1px solid rgb(241 182 80 / 18%);
  border-radius: 8px;
  font-size: 10px;
}

.pv2-sync-warning span {
  flex: 1;
}

.pv2-metric-ribbon {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.pv2-metric-ribbon > button,
.pv2-crop-card {
  position: relative;
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 96px;
  padding: 12px;
  color: #b45f58;
  text-align: left;
  background: rgb(10 29 22 / 94%);
  border: 1px solid rgb(101 224 166 / 14%);
  border-radius: 8px;
}

.pv2-metric-ribbon > button:hover,
.pv2-metric-ribbon > button:focus-visible {
  background: rgb(14 40 29 / 96%);
  border-color: rgb(101 228 168 / 34%);
}

.pv2-metric-ribbon > button > span,
.pv2-crop-card > span {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  background: rgb(255 255 255 / 4%);
  border: 1px solid currentColor;
  border-radius: 8px;
}

.pv2-metric-ribbon small,
.pv2-metric-ribbon strong,
.pv2-metric-ribbon p,
.pv2-crop-card small,
.pv2-crop-card strong,
.pv2-crop-card p {
  display: block;
}

.pv2-metric-ribbon small,
.pv2-crop-card small {
  color: #789487;
  font-size: 9px;
}

.pv2-metric-ribbon strong,
.pv2-crop-card strong {
  margin-top: 4px;
  color: #ecfff6;
  font-size: 19px;
}

.pv2-metric-ribbon strong em {
  color: #789487;
  font-size: 9px;
  font-style: normal;
  font-weight: 400;
}

.pv2-metric-ribbon p,
.pv2-crop-card p {
  margin: 4px 0 0;
  overflow: hidden;
  color: #6c887b;
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pv2-metric-ribbon > button > i {
  position: absolute;
  top: 8px;
  right: 8px;
  color: currentColor;
  font-size: 7px;
  font-style: normal;
}

.pv2-metric-ribbon > button.is-water {
  color: #56aaff;
}

.pv2-metric-ribbon > button.is-light {
  color: #f1b650;
}

.pv2-metric-ribbon > button.is-soil,
.pv2-metric-ribbon > button.is-system {
  color: #4fe0a0;
}

.pv2-crop-card {
  color: #4fe0a0;
}

.pv2-stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 270px;
  gap: 12px;
  min-height: 560px;
}

.pv2-scene {
  position: relative;
  min-width: 0;
  overflow: hidden;
  background: #06130e;
  border: 1px solid rgb(93 225 164 / 16%);
  border-radius: 8px;
}

.pv2-scene::after {
  content: "";
  position: absolute;
  inset: 14px;
  z-index: 4;
  border: 1px solid rgb(79 224 160 / 7%);
  pointer-events: none;
}

.pv2-scene__canvas {
  position: absolute;
  inset: 0;
}

.pv2-scene__top {
  position: absolute;
  top: 16px;
  right: 18px;
  left: 18px;
  z-index: 6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pv2-scene__title {
  display: grid;
  gap: 4px;
}

.pv2-scene__title span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #4fe0a0;
  font-family: Consolas, monospace;
  font-size: 8px;
  letter-spacing: 0.13em;
}

.pv2-scene__title strong {
  color: #e7fff3;
  font-size: 11px;
}

.pv2-scene__controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 5px;
}

.pv2-scene__controls button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 31px;
  padding: 0 9px;
  color: #9ab7a9;
  background: rgb(3 15 9 / 74%);
  border: 1px solid rgb(99 226 166 / 15%);
  border-radius: 7px;
  font-size: 8px;
  backdrop-filter: blur(10px);
}

.pv2-scene__controls button.is-active {
  color: #062116;
  background: #55dfa0;
  border-color: #55dfa0;
}

.pv2-scene__hotspot {
  position: absolute;
  bottom: 58px;
  left: 18px;
  z-index: 6;
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-width: 260px;
  padding: 7px 9px;
  background: rgb(3 15 9 / 80%);
  border: 1px solid rgb(99 226 166 / 17%);
  border-radius: 7px;
  backdrop-filter: blur(10px);
}

.pv2-scene__hotspot > span {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  color: #4fe0a0;
  background: rgb(79 224 160 / 10%);
  border-radius: 6px;
}

.pv2-scene__hotspot strong,
.pv2-scene__hotspot small {
  display: block;
}

.pv2-scene__hotspot strong {
  color: #e7fff3;
  font-size: 10px;
}

.pv2-scene__hotspot small {
  margin-top: 3px;
  color: #779487;
  font-size: 8px;
}

.pv2-scene__hotspot em {
  color: #54dca0;
  font-size: 8px;
  font-style: normal;
}

.pv2-scene__footer {
  position: absolute;
  right: 18px;
  bottom: 17px;
  left: 18px;
  z-index: 6;
  display: flex;
  justify-content: space-between;
  color: #6f8d7f;
  font-size: 8px;
}

.pv2-scene__footer span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.pv2-device-rack {
  display: grid;
  align-content: start;
  overflow: hidden;
  background: rgb(9 27 19 / 94%);
  border: 1px solid rgb(96 223 164 / 15%);
  border-radius: 8px;
}

.pv2-device-rack > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px;
  border-bottom: 1px solid rgb(96 223 164 / 12%);
}

.pv2-device-rack header span {
  color: #4fe0a0;
  font-family: Consolas, monospace;
  font-size: 7px;
  letter-spacing: 0.12em;
}

.pv2-device-rack h3 {
  margin: 4px 0 0;
  font-size: 13px;
}

.pv2-device-rack header > strong {
  color: #4fe0a0;
  font-size: 28px;
  line-height: 1;
}

.pv2-device {
  display: grid;
  grid-template-columns: 7px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  min-height: 62px;
  padding: 9px 13px;
  border-bottom: 1px solid rgb(96 223 164 / 9%);
  cursor: pointer;
}

.pv2-device:hover,
.pv2-device:focus-visible {
  background: rgb(74 215 145 / 7%);
  outline: none;
}

.pv2-device__pulse {
  width: 7px;
  height: 7px;
  background: #8b978f;
  border-radius: 50%;
}

.pv2-device.is-running .pv2-device__pulse,
.pv2-device.is-ok .pv2-device__pulse {
  background: #4fe0a0;
  box-shadow: 0 0 0 4px rgb(79 224 160 / 9%);
}

.pv2-device.is-warning .pv2-device__pulse {
  background: #f1b650;
}

.pv2-device strong,
.pv2-device small {
  display: block;
}

.pv2-device strong {
  font-size: 10px;
}

.pv2-device small {
  margin-top: 3px;
  color: #708c7f;
  font-size: 8px;
}

.pv2-device__switch {
  position: relative;
  width: 38px;
  height: 20px;
  padding: 0;
  background: #263d32;
  border: 0;
  border-radius: 12px;
}

.pv2-device__switch i {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 14px;
  height: 14px;
  background: #84978c;
  border-radius: 50%;
  transition: transform 160ms ease, background-color 160ms ease;
}

.pv2-device__switch.is-on {
  background: #22694d;
}

.pv2-device__switch.is-on i {
  background: #67e5ab;
  transform: translateX(18px);
}

.pv2-device-rack > p {
  margin: 18px;
  color: #739084;
  font-size: 9px;
}

.pv2-trend-wall {
  grid-column: span 8;
  background: rgb(9 27 19 / 94%);
  border: 1px solid rgb(96 223 164 / 15%);
  border-radius: 8px;
}

.pv2-trend-wall > header,
.pv2-water > header,
.pv2-proof > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border-bottom: 1px solid rgb(96 223 164 / 11%);
}

.pv2-trend-wall h3,
.pv2-water h3,
.pv2-proof h3 {
  margin: 4px 0 0;
  font-size: 14px;
}

.pv2-trend-wall header p,
.pv2-water header p,
.pv2-proof header p {
  margin: 4px 0 0;
  color: #6f8b7e;
  font-size: 8px;
}

.pv2-trend-wall__tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 5px;
}

.pv2-trend-wall__tools > button:first-child {
  color: #75e8b1;
}

.pv2-trend-wall__tools > button:first-child i {
  width: 6px;
  height: 6px;
  background: #4fe0a0;
  border-radius: 50%;
}

.pv2-trend-wall__periods {
  display: inline-flex;
  padding: 3px;
  background: rgb(0 0 0 / 17%);
  border-radius: 7px;
}

.pv2-trend-wall__periods button {
  min-height: 27px;
  padding: 0 7px;
  color: #698579;
  background: transparent;
  border: 0;
  border-radius: 5px;
  font-size: 8px;
}

.pv2-trend-wall__periods button.is-active {
  color: #062116;
  background: #55dfa0;
}

.pv2-trend-wall__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  padding: 12px;
}

.pv2-trend-cell {
  min-width: 0;
  padding: 10px;
  color: #ff8a80;
  background: rgb(255 255 255 / 2%);
  border: 1px solid rgb(101 224 166 / 10%);
  border-radius: 7px;
}

.pv2-trend-cell.is-water {
  color: #56aaff;
}

.pv2-trend-cell.is-light {
  color: #f1b650;
}

.pv2-trend-cell.is-soil {
  color: #4fe0a0;
}

.pv2-trend-cell__head {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
}

.pv2-trend-cell__head > span {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  background: rgb(0 0 0 / 16%);
  border: 1px solid currentColor;
  border-radius: 6px;
}

.pv2-trend-cell__head small,
.pv2-trend-cell__head strong {
  display: block;
}

.pv2-trend-cell__head small {
  color: #789487;
  font-size: 8px;
}

.pv2-trend-cell__head strong {
  margin-top: 2px;
  color: #ecfff6;
  font-size: 15px;
}

.pv2-trend-cell__head strong em {
  color: #789487;
  font-size: 8px;
  font-style: normal;
  font-weight: 400;
}

.pv2-trend-cell__head time {
  color: #657f73;
  font-size: 8px;
}

.pv2-trend-cell .pv2-trend-chart {
  display: block;
  width: 100%;
  height: 86px;
  margin-top: 5px;
}

.pv2-trend-cell footer {
  display: flex;
  justify-content: space-between;
  color: #627d70;
  font-size: 8px;
}

.pv2-trend-grid {
  fill: none;
  stroke: rgb(128 183 156 / 14%);
  stroke-width: 1;
  stroke-dasharray: 4 5;
}

.pv2-trend-area {
  fill: currentColor;
  opacity: 0.08;
}

.pv2-trend-line {
  fill: none;
  stroke: currentColor;
  stroke-width: 2.2;
  stroke-linecap: round;
}

.pv2-trend-dot {
  fill: currentColor;
}

.pv2-trend-axis {
  fill: #617b70;
  font-size: 8px;
}

.pv2-water {
  grid-column: span 4;
  background: rgb(9 27 19 / 94%);
  border: 1px solid rgb(96 223 164 / 15%);
  border-radius: 8px;
}

.pv2-water header > div:last-child {
  display: flex;
  gap: 5px;
}

.pv2-recipe {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 12px 0;
}

.pv2-recipe span {
  padding: 4px 7px;
  color: #74dcaa;
  background: rgb(74 216 146 / 8%);
  border: 1px solid rgb(88 223 158 / 14%);
  border-radius: 5px;
  font-size: 8px;
}

.pv2-water-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
  padding: 11px 12px 0;
}

.pv2-water-form input,
.pv2-water-form button {
  min-height: 32px;
  padding: 0 8px;
  color: #def7eb;
  background: rgb(1 12 7 / 68%);
  border: 1px solid rgb(96 224 165 / 16%);
  border-radius: 6px;
  font-size: 9px;
}

.pv2-water-form button {
  color: #062116;
  background: #55dfa0;
  border-color: #55dfa0;
}

.pv2-water-error,
.pv2-water-message {
  margin: 8px 12px 0;
  color: #ff968b;
  font-size: 8px;
}

.pv2-water-list {
  display: grid;
  gap: 5px;
  padding: 10px 12px;
}

.pv2-water-list > button {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 7px;
  min-height: 48px;
  padding: 6px 8px;
  color: #a1b9ac;
  text-align: left;
  background: rgb(255 255 255 / 2.5%);
  border: 1px solid rgb(96 222 163 / 9%);
  border-radius: 7px;
}

.pv2-water-list > button.is-selected {
  background: rgb(73 215 144 / 7%);
  border-color: rgb(73 215 144 / 27%);
}

.pv2-water-list time {
  color: #62dda5;
  font-family: Consolas, monospace;
  font-size: 10px;
}

.pv2-water-list strong,
.pv2-water-list small {
  display: block;
}

.pv2-water-list strong {
  font-size: 10px;
}

.pv2-water-list small {
  margin-top: 2px;
  color: #6c877b;
  font-size: 8px;
}

.pv2-water-list em {
  color: #f1b650;
  font-size: 8px;
  font-style: normal;
}

.pv2-water-list i {
  padding: 5px 7px;
  color: #062116;
  background: #55dfa0;
  border-radius: 5px;
  font-size: 8px;
  font-style: normal;
}

.pv2-water-execute {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: calc(100% - 24px);
  min-height: 35px;
  margin: 0 12px 12px;
  color: #062116;
  background: #55dfa0;
  border: 0;
  border-radius: 7px;
  font-size: 9px;
  font-weight: 700;
}

.pv2-water-execute:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.pv2-proof {
  background: rgb(9 27 19 / 94%);
  border: 1px solid rgb(96 223 164 / 15%);
  border-radius: 8px;
}

.pv2-proof header > div:last-child {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pv2-proof header span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #f1b650;
  font-size: 9px;
}

.pv2-proof header span.is-confirmed {
  color: #4fe0a0;
}

.pv2-proof-message {
  margin: 10px 14px 0;
  color: #7be7b4;
  font-size: 9px;
}

.pv2-proof-message.is-error {
  color: #ff968b;
}

.pv2-proof-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 14px;
  padding: 12px 14px;
}

.pv2-proof-chart svg {
  display: block;
  width: 100%;
  height: 160px;
}

.pv2-chain-grid {
  fill: none;
  stroke: rgb(128 183 156 / 13%);
  stroke-dasharray: 4 5;
}

.pv2-chain-area {
  fill: url("#pv2ChainArea");
}

.pv2-chain-line {
  fill: none;
  stroke: #4fe0a0;
  stroke-width: 2;
}

.pv2-chain-point {
  fill: #4fe0a0;
}

.pv2-chain-point.is-latest {
  r: 5;
  filter: drop-shadow(0 0 6px rgb(79 224 160 / 60%));
}

.pv2-proof-axis {
  display: flex;
  justify-content: space-between;
  color: #627d70;
  font-size: 8px;
}

.pv2-hash-stack {
  display: grid;
  gap: 5px;
}

.pv2-hash-stack > div {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
  min-height: 29px;
  padding: 0 8px;
  background: rgb(255 255 255 / 2.5%);
  border: 1px solid rgb(96 222 163 / 8%);
  border-radius: 6px;
}

.pv2-hash-stack time {
  color: #688477;
  font-size: 8px;
}

.pv2-hash-stack code {
  overflow: hidden;
  color: #67e3aa;
  font-family: Consolas, monospace;
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pv2-hash-stack em {
  color: #55dda1;
  font-size: 8px;
  font-style: normal;
}

.pv2-proof > footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 14px;
  border-top: 1px solid rgb(96 223 164 / 10%);
}

.pv2-proof > footer span,
.pv2-proof > footer small {
  color: #688477;
  font-size: 8px;
}

.pv2-proof > footer code {
  flex: 1;
  overflow: hidden;
  color: #67e3aa;
  font-family: Consolas, monospace;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-v2.is-environment .pv2-trend-wall {
  grid-column: 1 / -1;
}

.production-v2.is-environment .pv2-trend-wall__grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.production-v2.is-water .pv2-water {
  grid-column: 1 / -1;
}

.production-v2.is-water .pv2-water-list {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.production-v2.is-twin .pv2-stage {
  min-height: 680px;
}

@media (max-width: 1180px) {
  .pv2-metric-ribbon {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .pv2-stage {
    grid-template-columns: minmax(0, 1fr) 240px;
  }

  .pv2-trend-wall {
    grid-column: 1 / -1;
  }

  .pv2-water {
    grid-column: 1 / -1;
  }
}

@media (max-width: 820px) {
  .production-v2 {
    grid-template-columns: 1fr;
  }

  .pv2-hero,
  .pv2-statusbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .pv2-metric-ribbon {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pv2-stage {
    grid-template-columns: 1fr;
  }

  .pv2-scene {
    min-height: 510px;
  }

  .pv2-device-rack,
  .pv2-trend-wall,
  .pv2-water,
  .pv2-proof {
    grid-column: 1 / -1;
  }

  .pv2-proof-body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 540px) {
  .pv2-metric-ribbon,
  .pv2-trend-wall__grid {
    grid-template-columns: 1fr;
  }

  .pv2-trend-wall > header,
  .pv2-water > header,
  .pv2-proof > header {
    align-items: flex-start;
    flex-direction: column;
  }
}

/* Production command-center bento redesign */
.production-command-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
  background:
    linear-gradient(110deg, rgb(63 211 143 / 8%), transparent 46%),
    rgb(8 24 17 / 92%);
  border: 1px solid rgb(98 226 167 / 16%);
  border-radius: 8px;
}

.production-command-head__title > span {
  color: #52dfa0;
  font-family: Consolas, monospace;
  font-size: 8px;
  letter-spacing: 0.13em;
}

.production-command-head__title h2 {
  margin: 5px 0 0;
  font-size: 22px;
}

.production-command-head__title p {
  margin: 6px 0 0;
  color: #759184;
  font-size: 10px;
}

.production-page {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  grid-auto-flow: dense;
  gap: 14px;
}

.production-command-head,
.production-command-bar,
.production-sync-banner,
.production-kpi-ribbon,
.production-chain-band {
  grid-column: 1 / -1;
}

.production-command-bar {
  margin: 0;
}

.production-kpi-ribbon {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.production-kpi {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 92px;
  padding: 12px;
  color: #b45f58;
  background:
    linear-gradient(140deg, rgb(255 255 255 / 2.5%), transparent 58%),
    rgb(10 29 22 / 94%);
  border: 1px solid rgb(103 223 166 / 14%);
  border-radius: 8px;
}

.production-kpi > span {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  background: rgb(255 255 255 / 4%);
  border: 1px solid currentColor;
  border-radius: 8px;
}

.production-kpi small,
.production-kpi strong,
.production-kpi p {
  display: block;
}

.production-kpi small {
  color: #789487;
  font-size: 9px;
}

.production-kpi strong {
  margin-top: 3px;
  color: #ecfff6;
  font-size: 20px;
  line-height: 1.1;
}

.production-kpi strong em {
  color: #789487;
  font-size: 9px;
  font-style: normal;
  font-weight: 400;
}

.production-kpi p {
  margin: 4px 0 0;
  overflow: hidden;
  color: #668175;
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-kpi.is-water {
  color: #56aaff;
}

.production-kpi.is-light {
  color: #f1b650;
}

.production-kpi.is-soil,
.production-kpi.is-system {
  color: #4fe0a0;
}

.production-workspace,
.production-inspector,
.production-bottom-grid {
  display: contents;
}

.production-summary {
  grid-column: span 3;
  grid-row: span 2;
}

.production-scene-panel {
  grid-column: span 6;
  grid-row: span 2;
  min-height: 540px;
}

.production-hotspot-card {
  grid-column: span 3;
}

.production-device-card {
  grid-column: span 3;
}

.production-bottom-grid > .production-panel:nth-child(1) {
  grid-column: span 8;
}

.production-bottom-grid > .production-panel:nth-child(2) {
  grid-column: span 4;
}

.production-chain-band {
  margin-top: 0;
}

@media (max-width: 1240px) {
  .production-page {
    grid-template-columns: repeat(8, minmax(0, 1fr));
  }

  .production-kpi-ribbon {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .production-summary {
    grid-column: span 3;
  }

  .production-scene-panel {
    grid-column: span 5;
  }

  .production-hotspot-card,
  .production-device-card {
    grid-column: span 4;
  }

  .production-bottom-grid > .production-panel:nth-child(1) {
    grid-column: span 8;
  }

  .production-bottom-grid > .production-panel:nth-child(2) {
    grid-column: span 4;
  }
}

@media (max-width: 900px) {
  .production-command-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .production-page {
    grid-template-columns: 1fr;
  }

  .production-kpi-ribbon {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .production-summary,
  .production-scene-panel,
  .production-hotspot-card,
  .production-device-card,
  .production-bottom-grid > .production-panel:nth-child(1),
  .production-bottom-grid > .production-panel:nth-child(2) {
    grid-column: 1 / -1;
    grid-row: auto;
  }
}

@media (max-width: 560px) {
  .production-kpi-ribbon {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .production-trend-sampling i,
  .trend-dot,
  .production-trend-action .is-spinning,
  .production-chain-attest-message .is-spinning {
    animation: none;
  }
}
</style>
