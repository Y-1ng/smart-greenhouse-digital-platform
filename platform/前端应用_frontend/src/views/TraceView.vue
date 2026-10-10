<!-- 质量溯源页面：输入产品编码并展示验签、链上记录和追溯环节。 -->
<script setup lang="ts">
import {
  Award,
  Blocks,
  CircleAlert,
  CircleCheck,
  Copy,
  Database,
  Download,
  FileCheck2,
  FileUp,
  KeyRound,
  Link2,
  PlusCircle,
  QrCode,
  Search,
  ShieldCheck,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import QrcodeVue from 'qrcode.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  downloadQualityReport,
  createCertificate,
  listCropBatches,
  uploadQualityReport,
  verifyCertificateSignature,
} from '@/api/modules/quality'
import { getChainProof } from '@/api/modules/proof'
import { getChainStatus } from '@/api/modules/chain'
import { describeApiError } from '@/api/errors'
import {
  appendTraceNode,
  createTraceCode,
  getFieldTracePath,
  getTraceQrPayload,
  queryTrace,
  scanTraceCode,
} from '@/api/modules/trace'
import { useSessionStore } from '@/stores/session'
import { greenhouseSites } from '@/data/greenhouse'
import type {
  CertificateVerifyRequest,
  TraceQrPayload,
  ChainStatus,
  ChainProof,
  QualityReport,
  ScanResult,
  TracePath,
  TraceResult,
} from '@/types/api'

defineOptions({ name: 'TraceView' })

const productCode = ref('GH-TRACE-2026-0001')
const fieldNo = ref('F-A-03')
const queryMode = ref<'code' | 'field'>('code')
const loading = ref(false)
const fieldLoading = ref(false)
const errorMessage = ref('')
const result = ref<TraceResult | null>(null)
const fieldPath = ref<TracePath | null>(null)
const cropBatches = ref(
  greenhouseSites.map((site, index) => ({
    id: 2001 + index,
    label: `${site.name} · ${site.crop} · ${site.batch}`,
  })),
)
const reportForm = ref({
  batchId: cropBatches.value[0].id,
  institutionId: 'ORG-001',
  institutionName: '寒旱区农产品质量检测中心',
  reportNo: createReportNo(),
  resultSummary: '农残及理化指标检测合格',
  testedAt: toLocalDateTime(new Date()),
})
const reportFile = ref<File | null>(null)
// 与后端 spring.servlet.multipart.max-file-size 保持一致
const MAX_REPORT_FILE_BYTES = 20 * 1024 * 1024
const reportUploading = ref(false)
const reportDownloading = ref(false)
const reportMessage = ref('')
const reportError = ref('')
const uploadedReport = ref<QualityReport | null>(null)
const selectedReportBatchLabel = computed(
  () =>
    cropBatches.value.find((batch) => batch.id === reportForm.value.batchId)
      ?.label ?? '当前作物批次',
)
const certificateForm = ref({
  batchId: cropBatches.value[0].id,
  certificateNo: createCertificateNo(),
  contentHash: '',
  status: 'VALID',
})
const traceCodeForm = ref({
  batchId: cropBatches.value[0].id,
  // 演示数据里的完整追溯节点（种植/农事/检测/合格证/包装）绑定在 F-A-03，
  // 默认用它生成新码可以直接看到 5 节点指针链；换田块则用“追加节点”逐步建链。
  fieldNo: 'F-A-03',
  productCode: createProductCode(),
  publicKeyId: 'PK-GREENHOUSE-01',
  signatureValue: 'demo-signature-value',
})
const certificateSaving = ref(false)
const traceCodeSaving = ref(false)
const certificateMessage = ref('')
const certificateError = ref('')
const traceCodeMessage = ref('')
const traceCodeError = ref('')
// 一物一码二维码：payload 由后端给，前端只负责预览与下载
const traceQrPayload = ref<TraceQrPayload | null>(null)
const traceQrLoading = ref(false)
const traceQrError = ref('')
const verifyPending = ref(false)
const verifyMessage = ref('')
const verifyFailed = ref(false)
const verifyPublicKeyId = 'PK-GREENHOUSE-01'
const certificateKeyStorage = 'smart-greenhouse-certificate-key'

const session = useSessionStore()
const route = useRoute()
const pageSection = computed(() => String(route.meta.section ?? 'query'))
const nodeForm = ref({
  stageType: '运输',
  summary: '发往冷链中转仓',
})
const nodeSaving = ref(false)
const nodeMessage = ref('')
const nodeError = ref('')

const nodeStageOptions = [
  '种植',
  '农事',
  '采收',
  '检测',
  '加工',
  '包装',
  '运输',
  '仓储',
  '销售',
]

const activeFieldNo = computed(
  () => fieldPath.value?.fieldNo ?? result.value?.fieldNo ?? '',
)

const canAppendNode = computed(() =>
  ['ADMIN', 'OPERATOR', 'QUALITY'].includes(session.roleCode),
)

async function submitTraceNode() {
  const target = activeFieldNo.value
  if (!target) {
    nodeError.value = '请先查询产品编码或田块编号'
    return
  }

  nodeSaving.value = true
  nodeMessage.value = ''
  nodeError.value = ''
  try {
    const created = await appendTraceNode(target, {
      stageType: nodeForm.value.stageType,
      summary: nodeForm.value.summary,
    })
    nodeMessage.value = `${created.message}（节点 #${created.nodeId}，共 ${created.nodeCount} 个）`
    if (queryMode.value === 'field') {
      await searchByField()
    } else {
      await searchTrace()
    }
  } catch {
    nodeError.value = '追加失败：请确认田块已绑定产品溯源码，且当前账号有操作权限'
  } finally {
    nodeSaving.value = false
  }
}
const resultTone = computed(() =>
  result.value?.verificationStatus === 'VERIFIED'
    ? 'online'
    : result.value?.verificationStatus === 'NOT_FOUND'
      ? 'offline'
      : 'warning',
)

const chainProof = ref<ChainProof | null>(null)
const proofLoading = ref(false)
const copiedField = ref('')
const scanChannel = ref('WECHAT_MINI')
const scanPending = ref(false)
const scanResult = ref<ScanResult | null>(null)
const scanError = ref('')
// 链上运行状态：适配服务联通、存证规模、真实区块高度（接口不可用时如实置空）
const chainStatus = ref<ChainStatus | null>(null)
const chainStatusError = ref('')
let chainStatusTimer: number | undefined
let copyTimer: number | undefined
const traceStateStorageKey = 'smart-greenhouse-trace-query-state'

interface PersistedTraceState {
  productCode: string
  fieldNo: string
  queryMode: 'code' | 'field'
  result: TraceResult | null
  fieldPath: TracePath | null
  chainProof: ChainProof | null
  scanChannel: string
  scanResult: ScanResult | null
}

function persistTraceState() {
  try {
    const state: PersistedTraceState = {
      productCode: productCode.value,
      fieldNo: fieldNo.value,
      queryMode: queryMode.value,
      result: result.value,
      fieldPath: fieldPath.value,
      chainProof: chainProof.value,
      scanChannel: scanChannel.value,
      scanResult: scanResult.value,
    }
    window.sessionStorage.setItem(
      traceStateStorageKey,
      JSON.stringify(state),
    )
  } catch {
    // 浏览器禁用存储时退化为当前组件内状态。
  }
}

function restoreTraceState() {
  try {
    const raw = window.sessionStorage.getItem(traceStateStorageKey)
    if (!raw) {
      return false
    }
    const state = JSON.parse(raw) as Partial<PersistedTraceState>
    productCode.value =
      state.productCode?.trim() || productCode.value
    fieldNo.value = state.fieldNo?.trim() || fieldNo.value
    queryMode.value = state.queryMode === 'field' ? 'field' : 'code'
    result.value = state.result ?? null
    fieldPath.value = state.fieldPath ?? null
    chainProof.value = state.chainProof ?? null
    scanChannel.value = state.scanChannel ?? scanChannel.value
    scanResult.value = state.scanResult ?? null
    return true
  } catch {
    return false
  }
}

watch(
  [
    productCode,
    fieldNo,
    queryMode,
    result,
    fieldPath,
    chainProof,
    scanChannel,
    scanResult,
  ],
  persistTraceState,
  { deep: true },
)

const chainNodes = [
  { id: 'REG', organization: '监管节点', latency: '12 ms', tone: 'online' },
  { id: 'BASE', organization: '基地节点', latency: '8 ms', tone: 'online' },
  { id: 'LAB', organization: '检测机构', latency: '16 ms', tone: 'online' },
  { id: 'COOP', organization: '合作节点', latency: '21 ms', tone: 'online' },
]

const scanChannels = [
  { value: 'WECHAT_MINI', label: '微信小程序' },
  { value: 'WEB', label: '网页查询' },
  { value: 'REGULATOR', label: '监管端' },
]

/** 拉取链上运行状态：适配服务联通、存证总数、待补偿数、真实区块高度 */
async function loadChainStatus() {
  try {
    chainStatus.value = await getChainStatus()
    chainStatusError.value = ''
  } catch (error) {
    chainStatus.value = null
    chainStatusError.value = describeApiError(error)
  }
}

const adapterHint = computed(() => {
  const adapter = chainStatus.value?.adapter
  if (!adapter) {
    return chainStatusError.value ? '状态接口不可用' : '检测中'
  }
  if (adapter.mode === 'rest') {
    return adapter.reachable ? 'REST 适配器在线' : 'REST 适配器不可达'
  }
  return adapter.mode === 'simulated' ? '本地模拟回执' : adapter.mode
})

/**
 * 顶部四个（适配服务可用时五个）指标全部来自接口：
 * 适配服务状态、区块高度、待补偿存证、存证记录、本次核验结果。
 * 区块高度只在链上适配服务真的返回区块号时出现，测不到就不显示，不用演示值顶替。
 */
const chainStats = computed(() => {
  const status = chainStatus.value
  const pendingTotal = status?.pending?.total
  const stats = [
    {
      label: '适配服务状态',
      value: !status ? '—' : status.adapter.reachable ? '已连接' : '不可达',
      hint: adapterHint.value,
    },
  ]
  if (status?.network?.available && status.network.blockNumber != null) {
    const interval = status.network.avgBlockIntervalMs
    const sample = status.network.sampleSize
    stats.push({
      label: '当前区块高度',
      value: status.network.blockNumber.toLocaleString('zh-CN'),
      hint: interval
        ? `最近 ${sample ?? 12} 块平均 ${(interval / 1000).toFixed(1)} 秒`
        : '来自链上适配服务',
    })
  }
  stats.push(
    {
      label: '待补偿存证',
      value: pendingTotal == null ? '—' : `${pendingTotal} 条`,
      hint: '链上失败待重试',
    },
    {
      label: '存证记录',
      value: status?.proofCount == null ? '—' : `${status.proofCount} 条`,
      hint: '五类存证合计',
    },
    {
      label: '本次核验结果',
      value: proofStatus.value,
      hint: result.value ? '针对当前查询编码' : '先查询一条溯源码',
    },
  )
  return stats
})

const proofVerified = computed(
  () => result.value?.verificationStatus === 'VERIFIED',
)

const proofStatus = computed(() => {
  if (!result.value) {
    return '等待查询'
  }
  if (proofVerified.value) {
    return '已确认'
  }
  return result.value.verificationStatus === 'NOT_FOUND' ? '未找到' : '待确认'
})

const proofTone = computed<'online' | 'warning' | 'offline'>(() => {
  if (!result.value) {
    return 'warning'
  }
  if (proofVerified.value) {
    return 'online'
  }
  return result.value.verificationStatus === 'NOT_FOUND' ? 'offline' : 'warning'
})

const chainStages = computed(() => {
  const stages = result.value?.stages ?? fieldPath.value?.stages ?? []
  return stages.map((stage, index) => {
    const previousStage = stages[index - 1]
    return {
      nodeId: stage.nodeId,
      index,
      stageType: stage.stageType,
      payloadHash: stage.payloadHash,
      payloadShort: shortenHash(stage.payloadHash, 10),
      previousShort: shortenHash(stage.previousHash, 10),
      chainTxHash: stage.chainTxHash ?? '',
      chainTxShort: stage.chainTxHash
        ? shortenHash(stage.chainTxHash, 10)
        : '',
      createdAt: stage.createdAt.replace('T', ' ').slice(0, 16),
      linked:
        index === 0
          ? /^0+$/.test(stage.previousHash)
          : Boolean(previousStage)
            && stage.previousHash === previousStage.payloadHash,
    }
  })
})

const pointerChainValid = computed(
  () =>
    result.value?.pointerChainValid
    ?? fieldPath.value?.pointerChainValid
    ?? false,
)

const chainStateLabel = computed(() => {
  if (chainStages.value.length === 0) {
    return '暂无追溯节点'
  }
  return pointerChainValid.value ? '指针链校验通过' : '指针链异常'
})

const chainEmptyHint = computed(() => {
  if (result.value && !result.value.found) {
    return `未找到溯源码 ${result.value.productCode}。演示环境重启后数据会重置，请在质量台账用当前批次重新生成溯源码。`
  }
  if (fieldPath.value && fieldPath.value.stages.length === 0) {
    return `田块 ${fieldPath.value.fieldNo} 还没有追溯节点，可在上方选择阶段并追加第一个节点。`
  }
  if (result.value && result.value.stages.length === 0) {
    return `溯源码 ${result.value.productCode} 绑定的田块还没有追溯节点，可在上方选择阶段并追加第一个节点。`
  }
  return '先在上方查询产品编码或田块编号，哈希指针链会显示在这里。'
})

const proofItems = computed(() => {
  const proof = chainProof.value
  const trace = result.value
  const cycleConfirmed = proof?.chainStatus === 'CONFIRMED'
  const certificateValid = proof?.certificateStatus === 'VALID'
  const traceActive = proof?.traceStatus === 'ACTIVE'
  return [
    {
      key: 'cycle',
      title: '环境周期存证',
      subtitle: proof?.cycleId ? `采集周期 #${proof.cycleId}` : '最近环境采集周期',
      icon: Database,
      hashLabel: '根哈希',
      hash: proof?.rootHash ?? trace?.rootHash ?? '',
      txHash: proof?.chainTxHash ?? '',
      tone: cycleConfirmed ? 'online' : 'warning',
      statusLabel: cycleConfirmed ? '已确认' : '待确认',
      footnote: cycleConfirmed ? '链上回执已确认' : '等待链上回执',
    },
    {
      key: 'certificate',
      title: '电子合格证存证',
      subtitle: proof?.certificateNo ?? trace?.certificateNo ?? '未关联合格证',
      icon: FileCheck2,
      hashLabel: '内容摘要',
      hash: proof?.certificateHash ?? '',
      txHash: proof?.certificateChainTxHash ?? '',
      tone: certificateValid ? 'online' : 'warning',
      statusLabel: certificateValid ? '有效' : '待核验',
      footnote: certificateValid ? '合格证签名有效' : '合格证状态待核验',
    },
    {
      key: 'trace-code',
      title: '一物一码存证',
      subtitle: proof?.productCode ?? trace?.productCode ?? '未生成溯源码',
      icon: QrCode,
      hashLabel: '签名公钥',
      hash: trace?.publicKeyId ?? '',
      txHash: proof?.traceChainTxHash ?? '',
      tone: traceActive ? 'online' : 'warning',
      statusLabel: traceActive ? '生效中' : '待核验',
      footnote: traceActive ? '溯源码处于生效状态' : '溯源码状态待核验',
    },
  ]
})

function shortenHash(value: string, headLength = 12) {
  if (!value) {
    return ''
  }
  if (value.length <= headLength + 9) {
    return value
  }
  return `${value.slice(0, headLength)}...${value.slice(-6)}`
}

async function copyText(value: string, field: string) {
  if (!value) {
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    copiedField.value = field
    window.clearTimeout(copyTimer)
    copyTimer = window.setTimeout(() => {
      copiedField.value = ''
    }, 1600)
  } catch {
    copiedField.value = ''
  }
}

async function loadChainProof(batchId: number) {
  proofLoading.value = true
  try {
    chainProof.value = await getChainProof(batchId)
  } catch {
    chainProof.value = null
  } finally {
    proofLoading.value = false
  }
}

function deviceFingerprint() {
  const key = 'smart-greenhouse-device-id'
  let value = window.localStorage.getItem(key)
  if (!value) {
    value = `demo-${Math.random().toString(36).slice(2, 10)}`
    window.localStorage.setItem(key, value)
  }
  return value
}

function scanChannelLabel(channel: string) {
  return scanChannels.find((item) => item.value === channel)?.label ?? channel
}

async function performScan() {
  const code = result.value?.productCode
  if (!code || scanPending.value) {
    return
  }

  scanPending.value = true
  scanError.value = ''
  try {
    scanResult.value = await scanTraceCode(code, {
      channel: scanChannel.value,
      deviceFingerprint: deviceFingerprint(),
    })
  } catch {
    scanError.value = '扫码记录写入失败，请检查服务状态'
  } finally {
    scanPending.value = false
  }
}

function toLocalDateTime(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function createReportNo() {
  const now = new Date()
  const datePart = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('')
  return `QR-${datePart}-${String(now.getTime()).slice(-6)}`
}

function createCertificateNo() {
  return `CERT-${new Date().getFullYear()}-${String(Date.now()).slice(-8)}`
}

function createProductCode() {
  return `GH-TRACE-${new Date().getFullYear()}-${String(Date.now()).slice(-8)}`
}

async function searchTrace() {
  if (!productCode.value.trim()) {
    return
  }

  loading.value = true
  errorMessage.value = ''
  fieldPath.value = null
  chainProof.value = null
  scanResult.value = null
  scanError.value = ''
  try {
    result.value = await queryTrace(productCode.value.trim())
    if (result.value.found && result.value.batchId) {
      void loadChainProof(result.value.batchId)
    }
  } catch {
    result.value = null
    errorMessage.value = '溯源服务暂不可用'
  } finally {
    loading.value = false
  }
}

async function searchByField() {
  const value = fieldNo.value.trim()
  if (!value) {
    return
  }

  fieldLoading.value = true
  errorMessage.value = ''
  result.value = null
  chainProof.value = null
  scanResult.value = null
  scanError.value = ''
  try {
    fieldPath.value = await getFieldTracePath(value)
  } catch {
    fieldPath.value = null
    errorMessage.value = '该田块暂无追溯记录'
  } finally {
    fieldLoading.value = false
  }
}

async function submitQuery() {
  if (queryMode.value === 'code') {
    await searchTrace()
    return
  }
  await searchByField()
}

function switchQueryMode(mode: 'code' | 'field') {
  if (queryMode.value === mode) {
    return
  }
  queryMode.value = mode
  errorMessage.value = ''
  result.value = null
  fieldPath.value = null
  chainProof.value = null
  scanResult.value = null
  scanError.value = ''
}

async function loadCropBatches() {
  try {
    const batches = await listCropBatches()
    if (batches.length === 0) {
      return
    }
    cropBatches.value = batches.map((batch) => ({
      id: batch.batchId,
      label: `${batch.greenhouseName} · ${batch.cropName} · ${batch.varietyName}`,
    }))
    if (!batches.some((batch) => batch.batchId === reportForm.value.batchId)) {
      reportForm.value.batchId = batches[0].batchId
    }
    if (!batches.some((batch) => batch.batchId === certificateForm.value.batchId)) {
      certificateForm.value.batchId = batches[0].batchId
    }
    if (!batches.some((batch) => batch.batchId === traceCodeForm.value.batchId)) {
      traceCodeForm.value.batchId = batches[0].batchId
    }
  } catch {
    // 保留离线演示批次，页面仍可展示完整表单。
  }
}

function selectReportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  reportMessage.value = ''
  reportError.value = ''
  if (file && file.size > MAX_REPORT_FILE_BYTES) {
    reportError.value = `所选文件 ${(file.size / 1024 / 1024).toFixed(1)} MB，超过 20 MB 上传限制`
    reportFile.value = null
    input.value = ''
    return
  }
  reportFile.value = file
}

async function submitQualityReport() {
  if (!reportFile.value) {
    reportError.value = '请先选择检测报告文件'
    return
  }
  if (
    !reportForm.value.batchId
    || !reportForm.value.institutionId.trim()
    || !reportForm.value.institutionName.trim()
    || !reportForm.value.reportNo.trim()
    || !reportForm.value.resultSummary.trim()
    || !reportForm.value.testedAt
  ) {
    reportError.value = '请完整填写报告信息'
    return
  }

  reportUploading.value = true
  reportMessage.value = ''
  reportError.value = ''
  try {
    uploadedReport.value = await uploadQualityReport({
      ...reportForm.value,
      file: reportFile.value,
    })
    certificateForm.value.batchId = uploadedReport.value.batchId
    certificateForm.value.contentHash = uploadedReport.value.contentHash
    reportMessage.value = `报告 ${uploadedReport.value.reportNo} 已入库并生成内容摘要`
    reportForm.value.reportNo = createReportNo()
    reportFile.value = null
  } catch {
    reportError.value = '上传失败，请检查报告编号是否重复或后端服务状态'
  } finally {
    reportUploading.value = false
  }
}

async function submitCertificate() {
  certificateSaving.value = true
  certificateMessage.value = ''
  certificateError.value = ''
  try {
    const certificate = await createCertificate(certificateForm.value)
    certificateMessage.value = certificate.chainTxHash
      ? `合格证 ${certificate.certificateNo} 已备案 · 链上回执 ${certificate.chainTxHash.slice(0, 10)}…`
      : `合格证 ${certificate.certificateNo} 已备案`
    certificateForm.value.certificateNo = createCertificateNo()
  } catch {
    certificateError.value = '合格证备案失败，请检查编号是否重复'
  } finally {
    certificateSaving.value = false
  }
}

function toBase64(buffer: ArrayBuffer) {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return window.btoa(binary)
}

function certificateCanonical(input: {
  certificateNo: string
  batchId: number
  institutionId: string
  contentHash: string
  issuedAt: string
}) {
  return [
    input.certificateNo,
    String(input.batchId),
    input.institutionId,
    input.contentHash,
    input.issuedAt,
  ].join('\n')
}

async function ensureSigningKey() {
  const stored = window.localStorage.getItem(certificateKeyStorage)
  if (stored) {
    const parsed = JSON.parse(stored) as {
      privateJwk: JsonWebKey
      publicKeyBase64: string
    }
    const key = await window.crypto.subtle.importKey(
      'jwk',
      parsed.privateJwk,
      'Ed25519',
      true,
      ['sign'],
    )
    return { key, publicKeyBase64: parsed.publicKeyBase64 }
  }

  const pair = await window.crypto.subtle.generateKey(
    { name: 'Ed25519' },
    true,
    ['sign', 'verify'],
  )
  const privateJwk = await window.crypto.subtle.exportKey(
    'jwk',
    pair.privateKey,
  )
  const publicKeyBase64 = toBase64(
    await window.crypto.subtle.exportKey('spki', pair.publicKey),
  )
  window.localStorage.setItem(
    certificateKeyStorage,
    JSON.stringify({ privateJwk, publicKeyBase64 }),
  )
  return { key: pair.privateKey, publicKeyBase64 }
}

async function verifyCertificate(tamper = false) {
  const certificateNo = certificateForm.value.certificateNo.trim()
  const contentHash = (
    uploadedReport.value?.contentHash
    ?? certificateForm.value.contentHash
  ).trim()
  const institutionId =
    uploadedReport.value?.institutionId ?? reportForm.value.institutionId

  if (!certificateNo || !contentHash) {
    verifyFailed.value = true
    verifyMessage.value = '请先备案合格证或填写内容摘要，再进行验签'
    return
  }
  if (!window.crypto?.subtle) {
    verifyFailed.value = true
    verifyMessage.value =
      '当前浏览器不支持 Ed25519 验签演示，请使用最新版 Chrome 或 Edge'
    return
  }

  verifyPending.value = true
  verifyFailed.value = false
  verifyMessage.value = ''
  try {
    const { key, publicKeyBase64 } = await ensureSigningKey()
    const issuedAt = new Date().toISOString()
    const signature = toBase64(
      await window.crypto.subtle.sign(
        'Ed25519',
        key,
        new TextEncoder().encode(
          certificateCanonical({
            certificateNo,
            batchId: certificateForm.value.batchId,
            institutionId,
            contentHash,
            issuedAt,
          }),
        ),
      ),
    )

    const payload: CertificateVerifyRequest = {
      requestNo: `VERIFY-${Date.now()}`,
      certificateNo,
      batchId: certificateForm.value.batchId,
      institutionId: tamper ? `${institutionId}-TAMPERED` : institutionId,
      contentHash: tamper ? `${contentHash}-tampered` : contentHash,
      issuedAt,
      publicKeyId: verifyPublicKeyId,
      publicKey: publicKeyBase64,
      signature,
    }
    const result = await verifyCertificateSignature(payload)
    verifyFailed.value = !result.verified
    verifyMessage.value = `${result.message} · 摘要 ${result.canonicalDigest.slice(0, 16)}… · 请求号 ${result.requestNo}`
  } catch {
    verifyFailed.value = true
    verifyMessage.value = '验签请求失败，请检查后端服务是否可用'
  } finally {
    verifyPending.value = false
  }
}

async function submitTraceCode() {
  traceCodeSaving.value = true
  traceCodeMessage.value = ''
  traceCodeError.value = ''
  try {
    const traceCode = await createTraceCode(traceCodeForm.value)
    traceCodeMessage.value = traceCode.chainTxHash
      ? `溯源码 ${traceCode.productCode} 已生成 · 链上回执 ${traceCode.chainTxHash.slice(0, 10)}…`
      : `溯源码 ${traceCode.productCode} 已生成`
    void loadTraceQr(traceCode.productCode)
    traceCodeForm.value.productCode = createProductCode()
  } catch {
    traceCodeError.value = '溯源码生成失败，请检查产品编码是否重复'
  } finally {
    traceCodeSaving.value = false
  }
}

/**
 * 拉取二维码载荷。二维码里只写"消费者扫码后要打开的校验地址"，
 * 完整追溯数据由扫码后的前端再向后端查询。
 */
async function loadTraceQr(productCode: string) {
  if (!productCode) {
    traceQrPayload.value = null
    return
  }
  traceQrLoading.value = true
  traceQrError.value = ''
  try {
    traceQrPayload.value = await getTraceQrPayload(productCode)
  } catch {
    traceQrPayload.value = null
    traceQrError.value = '二维码载荷获取失败，请确认后端已启动'
  } finally {
    traceQrLoading.value = false
  }
}

function downloadTraceQr(format: 'png' | 'svg') {
  const payload = traceQrPayload.value
  if (!payload) {
    return
  }
  const url = format === 'png' ? payload.pngUrl : payload.svgUrl
  const link = document.createElement('a')
  link.href = url
  link.download = `trace-${payload.productCode}.${format}`
  document.body.appendChild(link)
  link.click()
  link.remove()
}

async function copyTraceQrPayload() {
  const payload = traceQrPayload.value
  if (!payload) {
    return
  }
  try {
    await navigator.clipboard.writeText(payload.payload)
    traceCodeMessage.value = '二维码地址已复制'
  } catch {
    traceQrError.value = '浏览器未授权剪贴板，请手动复制'
  }
}

async function downloadReport() {
  if (!uploadedReport.value) {
    return
  }

  reportDownloading.value = true
  reportError.value = ''
  try {
    const blob = await downloadQualityReport(uploadedReport.value.reportNo)
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = uploadedReport.value.reportNo
    link.click()
    URL.revokeObjectURL(objectUrl)
  } catch {
    reportError.value = '报告下载失败'
  } finally {
    reportDownloading.value = false
  }
}

/**
 * 扫码进入：二维码里只写了 /traceability?code=产品编码 这一个地址，
 * 页面必须自己把 code 取出来查询，否则扫码进来看到的是上一次的结果。
 */
function readDeepLinkCode(): string {
  const raw = route.query.code
  return typeof raw === 'string' ? raw.trim() : ''
}

onMounted(() => {
  const deepLinkCode = readDeepLinkCode()
  // 深链优先于会话恢复：扫哪个码就查哪个码
  const restored = deepLinkCode ? false : restoreTraceState()
  if (deepLinkCode) {
    queryMode.value = 'code'
    productCode.value = deepLinkCode
  }
  void loadCropBatches()
  if (!restored) {
    void searchTrace()
  }
  void loadChainStatus()
  // 链上指标按 30 秒刷新一次，是真的去问适配服务，不再本地自增
  chainStatusTimer = window.setInterval(() => {
    void loadChainStatus()
  }, 30_000)
})

// 同一个标签页里再扫另一个码（或手动改 ?code=）时也要跟着切换
watch(
  () => route.query.code,
  (value) => {
    const code = typeof value === 'string' ? value.trim() : ''
    if (!code || code === productCode.value) {
      return
    }
    queryMode.value = 'code'
    productCode.value = code
    void searchTrace()
  },
)

onBeforeUnmount(() => {
  window.clearInterval(chainStatusTimer)
  window.clearTimeout(copyTimer)
})
</script>

<template>
  <div class="trace-v2" :class="`is-${pageSection}`">
    <header class="tv2-hero">
      <div>
        <span>QUALITY EVIDENCE LAB / FISCO BCOS</span>
        <h2>全链质量证据实验台</h2>
        <p>检测报告、合格证、一物一码、扫码记录与哈希指针链统一核验</p>
      </div>
      <div class="tv2-chain-stats">
        <article v-for="stat in chainStats" :key="stat.label">
          <small>{{ stat.label }}</small>
          <strong>{{ stat.value }}</strong>
          <em v-if="stat.hint">{{ stat.hint }}</em>
        </article>
      </div>
    </header>

    <section class="tv2-node-line">
      <span v-for="node in chainNodes" :key="node.id" :class="`is-${node.tone}`">
        <i></i>
        {{ node.organization }}
        <small>{{ node.latency }}</small>
      </span>
    </section>

    <section
      v-if="['query', 'report', 'evidence'].includes(pageSection)"
      class="tv2-workbench"
    >
      <article v-if="pageSection === 'query'" class="tv2-console">
        <header>
          <span>TRACE CONSOLE</span>
          <h3>扫码与追溯查询</h3>
        </header>
        <div class="tv2-mode-tabs">
          <button type="button" :class="{ 'is-active': queryMode === 'code' }" @click="switchQueryMode('code')">
            <QrCode :size="13" /> 产品编码
          </button>
          <button type="button" :class="{ 'is-active': queryMode === 'field' }" @click="switchQueryMode('field')">
            <Link2 :size="13" /> 田块编号
          </button>
        </div>
        <div class="tv2-query-input">
          <input
            v-if="queryMode === 'code'"
            v-model="productCode"
            aria-label="产品编码"
          />
          <input v-else v-model="fieldNo" aria-label="田块编号" />
          <button type="button" :disabled="loading || fieldLoading" @click="submitQuery">
            <Search :size="15" />
            {{ loading || fieldLoading ? '查询中' : '核验' }}
          </button>
        </div>
        <p v-if="errorMessage" class="tv2-error">{{ errorMessage }}</p>

        <div v-if="result" class="tv2-seal" :class="proofVerified ? 'is-ok' : 'is-risk'">
          <component :is="proofVerified ? ShieldCheck : CircleAlert" :size="28" />
          <strong>{{ proofVerified ? '链上核验通过' : '核验待确认' }}</strong>
          <span>{{ result.productCode }}</span>
          <small>{{ result.stages.length }} 个追溯节点 · {{ result.pointerChainValid ? '指针链完整' : '指针链异常' }}</small>
        </div>

        <dl v-if="result" class="tv2-key-values">
          <div><dt>验证状态</dt><dd><StatusTag :tone="resultTone">{{ result.verificationStatus }}</StatusTag></dd></div>
          <div><dt>电子合格证</dt><dd>{{ result.certificateNo ?? '未关联' }}</dd></div>
          <div><dt>环境根哈希</dt><dd><code :title="result.rootHash">{{ shortenHash(result.rootHash ?? '', 18) || '暂无' }}</code></dd></div>
          <div><dt>签名公钥</dt><dd>{{ result.publicKeyId ?? '未绑定' }}</dd></div>
          <div v-if="fieldPath"><dt>田块编号</dt><dd>{{ fieldPath.fieldNo }}</dd></div>
        </dl>

        <div v-if="result?.found" class="tv2-scan-actions">
          <select v-model="scanChannel" aria-label="扫码渠道">
            <option v-for="channel in scanChannels" :key="channel.value" :value="channel.value">
              {{ channel.label }}
            </option>
          </select>
          <button type="button" :disabled="scanPending" @click="performScan">
            <QrCode :size="14" />
            {{ scanPending ? '扫码中' : '模拟扫码' }}
          </button>
        </div>
        <p v-if="scanResult" class="tv2-scan-result" :class="{ 'is-risk': !scanResult.firstScan }">
          {{ scanResult.firstScan ? '首次验证通过' : `重复扫码风险 · 已扫 ${scanResult.scanCount} 次` }}
          · {{ scanChannelLabel(scanResult.channel) }}
        </p>
        <p v-if="scanError" class="tv2-error">{{ scanError }}</p>
      </article>

      <article
        v-if="pageSection === 'query' || pageSection === 'evidence'"
        class="tv2-evidence"
      >
        <header>
          <div>
            <span>EVIDENCE STREAM</span>
            <h3>链上证据流</h3>
          </div>
          <StatusTag :tone="proofTone">{{ proofStatus }}</StatusTag>
        </header>
        <div v-if="result" class="tv2-proof-cards">
          <article
            v-for="item in proofItems"
            :key="item.key"
            :class="`is-${item.tone}`"
          >
            <span><component :is="item.icon" :size="15" /></span>
            <div>
              <strong>{{ item.title }}</strong>
              <small>{{ item.subtitle }}</small>
              <code :title="item.hash">{{ item.hash ? shortenHash(item.hash, 20) : '暂无回执' }}</code>
            </div>
            <em>{{ item.statusLabel }}</em>
          </article>
        </div>
        <div v-else-if="fieldPath" class="tv2-field-path">
          <strong>{{ fieldPath.fieldNo }}</strong>
          <span>{{ fieldPath.productCode }}</span>
          <StatusTag :tone="fieldPath.pointerChainValid ? 'online' : 'offline'">
            {{ fieldPath.pointerChainValid ? '指针链完整' : '指针链异常' }}
          </StatusTag>
        </div>
        <p v-else class="tv2-empty">查询产品或田块后，证据流将在这里展开</p>
      </article>

      <article v-if="pageSection === 'report'" class="tv2-lab">
        <header>
          <span>DOCUMENT LAB</span>
          <h3>报告与证书工作区</h3>
        </header>
        <form class="tv2-report-form" @submit.prevent="submitQualityReport">
          <select v-model.number="reportForm.batchId">
            <option v-for="batch in cropBatches" :key="batch.id" :value="batch.id">{{ batch.label }}</option>
          </select>
          <div class="tv2-form-row">
            <input v-model.trim="reportForm.institutionId" type="text" placeholder="机构编号" />
            <input v-model.trim="reportForm.reportNo" type="text" placeholder="报告编号" />
          </div>
          <input v-model.trim="reportForm.institutionName" type="text" placeholder="检测机构名称" />
          <input v-model="reportForm.testedAt" type="datetime-local" />
          <input v-model.trim="reportForm.resultSummary" type="text" placeholder="结论摘要" />
          <label class="tv2-file">
            <FileUp :size="17" />
            <span>{{ reportFile?.name ?? '选择检测报告文件' }}</span>
            <input type="file" accept=".pdf,.png,.jpg,.jpeg,.txt,.doc,.docx" @change="selectReportFile" />
          </label>
          <button type="submit" :disabled="reportUploading">
            {{ reportUploading ? '保存中' : '备案检测报告' }}
          </button>
          <p v-if="reportMessage" class="is-success">{{ reportMessage }}</p>
          <p v-if="reportError" class="is-error">{{ reportError }}</p>
        </form>
        <aside class="tv2-report-preview">
          <header>
            <span>DOCUMENT PREVIEW</span>
            <h3>报告摘要预览</h3>
          </header>
          <dl>
            <div><dt>检测批次</dt><dd>{{ selectedReportBatchLabel }}</dd></div>
            <div><dt>报告编号</dt><dd>{{ reportForm.reportNo || '待生成' }}</dd></div>
            <div><dt>检测机构</dt><dd>{{ reportForm.institutionName || '待填写' }}</dd></div>
            <div><dt>检测时间</dt><dd>{{ reportForm.testedAt?.replace('T', ' ') || '待填写' }}</dd></div>
          </dl>
          <p>
            {{ reportForm.resultSummary || '填写检测结论后，这里会形成可备案的报告摘要。' }}
          </p>
          <div class="tv2-report-preview__flow" aria-label="报告备案流程">
            <span class="is-complete">报告原件</span>
            <i></i>
            <span :class="{ 'is-complete': Boolean(reportForm.resultSummary) }">
              结论摘要
            </span>
            <i></i>
            <span :class="{ 'is-complete': Boolean(uploadedReport) }">
              链上备案
            </span>
          </div>
        </aside>
        <aside class="tv2-report-side">
          <header>
            <span>QUALITY GATE</span>
            <h3>备案校验清单</h3>
          </header>
          <ul>
            <li>
              <FileCheck2 :size="16" />
              <div>
                <strong>检测报告原件</strong>
                <small>PDF、图片或 Word，单文件不超过 20 MB</small>
              </div>
              <em>待核验</em>
            </li>
            <li>
              <ShieldCheck :size="16" />
              <div>
                <strong>机构与批次信息</strong>
                <small>机构编号、报告编号和批次需要保持一致</small>
              </div>
              <em>系统校验</em>
            </li>
            <li>
              <CircleCheck :size="16" />
              <div>
                <strong>检测结论摘要</strong>
                <small>沉淀结论字段并生成报告内容摘要</small>
              </div>
              <em>自动摘要</em>
            </li>
            <li>
              <Link2 :size="16" />
              <div>
                <strong>链上备案</strong>
                <small>备案成功后写入报告哈希与链上交易回执</small>
              </div>
              <em>待上链</em>
            </li>
          </ul>
          <div class="tv2-report-side__summary">
            <span>当前批次</span>
            <strong>{{ selectedReportBatchLabel }}</strong>
            <span>备案机构</span>
            <strong>{{ reportForm.institutionName || '未填写检测机构' }}</strong>
          </div>
        </aside>
        <div v-if="uploadedReport" class="tv2-uploaded">
          <span>最近备案</span>
          <strong>{{ uploadedReport.reportNo }}</strong>
          <code>{{ uploadedReport.contentHash.slice(0, 24) }}...</code>
          <button type="button" :disabled="reportDownloading" @click="downloadReport">
            <Download :size="13" /> 下载原文件
          </button>
        </div>
      </article>
    </section>

    <section v-if="pageSection === 'evidence'" class="tv2-chain-body">
      <header>
        <div>
          <span>HASH POINTER CHAIN</span>
          <h3>哈希指针链</h3>
        </div>
        <span :class="{ 'is-risk': chainStages.length > 0 && !pointerChainValid }">
          <Link2 :size="13" />
          {{ chainStateLabel }}
        </span>
      </header>
      <form v-if="canAppendNode && activeFieldNo" class="tv2-node-form" @submit.prevent="submitTraceNode">
        <select v-model="nodeForm.stageType">
          <option v-for="stage in nodeStageOptions" :key="stage" :value="stage">{{ stage }}</option>
        </select>
        <input v-model.trim="nodeForm.summary" type="text" placeholder="节点说明，例如：发往冷链中转仓" />
        <button type="submit" :disabled="nodeSaving">
          <PlusCircle :size="14" /> {{ nodeSaving ? '上链中' : '追加节点' }}
        </button>
      </form>
      <p v-if="nodeMessage" class="is-success">{{ nodeMessage }}</p>
      <p v-if="nodeError" class="is-error">{{ nodeError }}</p>
      <div class="tv2-block-track">
        <p v-if="loading" class="tv2-chain-empty">正在加载哈希指针链…</p>
        <p v-else-if="chainStages.length === 0" class="tv2-chain-empty">
          {{ chainEmptyHint }}
        </p>
        <article
          v-for="stage in chainStages"
          :key="stage.nodeId"
          :class="{ 'is-broken': !stage.linked }"
        >
          <span>{{ stage.index === 0 ? 'GENESIS' : `BLOCK ${String(stage.index).padStart(2, '0')}` }}</span>
          <strong>{{ stage.stageType }}</strong>
          <code>{{ stage.payloadShort }}</code>
          <small>前序 {{ stage.previousShort }}</small>
          <small>{{ stage.chainTxHash ? `链上 ${stage.chainTxShort}` : '链上待补偿' }}</small>
          <time>{{ stage.createdAt }}</time>
        </article>
      </div>
    </section>

    <section v-if="pageSection === 'management'" class="tv2-operations">
      <form @submit.prevent="submitCertificate">
        <header>
          <Award :size="18" />
          <div><strong>电子合格证</strong><small>签发、摘要与验签</small></div>
        </header>
        <select v-model.number="certificateForm.batchId">
          <option v-for="batch in cropBatches" :key="batch.id" :value="batch.id">{{ batch.label }}</option>
        </select>
        <input v-model.trim="certificateForm.certificateNo" type="text" placeholder="合格证编号" />
        <input v-model.trim="certificateForm.contentHash" type="text" placeholder="内容摘要" />
        <select v-model="certificateForm.status">
          <option value="VALID">有效</option>
          <option value="DRAFT">草稿</option>
        </select>
        <div>
          <button type="submit" :disabled="certificateSaving">{{ certificateSaving ? '备案中' : '保存合格证' }}</button>
          <button type="button" :disabled="verifyPending" @click="verifyCertificate(false)">验签</button>
          <button type="button" :disabled="verifyPending" @click="verifyCertificate(true)">篡改验签</button>
        </div>
        <p v-if="certificateMessage" class="is-success">{{ certificateMessage }}</p>
        <p v-if="certificateError || verifyMessage" :class="{ 'is-error': certificateError || verifyFailed }">
          {{ certificateError || verifyMessage }}
        </p>
        <ol class="tv2-operations__flow" aria-label="电子合格证签发流程">
          <li class="is-complete"><i></i><span>选择作物批次</span></li>
          <li><i></i><span>填写证书摘要</span></li>
          <li><i></i><span>签发并完成验签</span></li>
        </ol>
      </form>

      <form @submit.prevent="submitTraceCode">
        <header>
          <QrCode :size="18" />
          <div><strong>一物一码</strong><small>绑定产品、田块和签名</small></div>
        </header>
        <select v-model.number="traceCodeForm.batchId">
          <option v-for="batch in cropBatches" :key="batch.id" :value="batch.id">{{ batch.label }}</option>
        </select>
        <input v-model.trim="traceCodeForm.fieldNo" type="text" placeholder="田块编号" />
        <input v-model.trim="traceCodeForm.productCode" type="text" placeholder="产品编码" />
        <input v-model.trim="traceCodeForm.publicKeyId" type="text" placeholder="公钥标识" />
        <input v-model.trim="traceCodeForm.signatureValue" type="text" placeholder="签名内容" />
        <button type="submit" :disabled="traceCodeSaving">
          {{ traceCodeSaving ? '生成中' : '生成溯源码' }}
        </button>
        <button
          class="tv2-qr-preview-trigger"
          type="button"
          :disabled="traceQrLoading || !traceCodeForm.productCode"
          @click="loadTraceQr(traceCodeForm.productCode)"
        >
          {{ traceQrLoading ? '获取中' : '预览已有编码的二维码' }}
        </button>
        <p v-if="traceCodeMessage" class="is-success">{{ traceCodeMessage }}</p>
        <p v-if="traceCodeError" class="is-error">{{ traceCodeError }}</p>
        <ol class="tv2-operations__flow" aria-label="一物一码生成流程">
          <li class="is-complete"><i></i><span>选择作物批次</span></li>
          <li><i></i><span>绑定田块与产品编码</span></li>
          <li><i></i><span>生成签名溯源码</span></li>
        </ol>
        <section
          v-if="traceQrPayload || traceQrLoading || traceQrError"
          class="tv2-qr-card"
        >
          <header>
            <span><QrCode :size="15" /></span>
            <div>
              <strong>二维码预览</strong>
              <small>码里只放公开校验地址，追溯明细由扫码后的前端向后端查询</small>
            </div>
          </header>
          <p v-if="traceQrLoading" class="tv2-qr-card__hint">
            正在获取二维码载荷…
          </p>
          <p v-else-if="traceQrError" class="tv2-qr-card__hint is-error">
            {{ traceQrError }}
          </p>
          <div v-else-if="traceQrPayload" class="tv2-qr-card__body">
            <div class="tv2-qr-card__code">
              <QrcodeVue
                :value="traceQrPayload.payload"
                :size="164"
                level="M"
                render-as="svg"
                foreground="#12241e"
                background="#ffffff"
              />
            </div>
            <div class="tv2-qr-card__meta">
              <span>扫码地址</span>
              <code>{{ traceQrPayload.payload }}</code>
              <div class="tv2-qr-card__actions">
                <button type="button" @click="downloadTraceQr('png')">
                  <Download :size="14" />
                  下载 PNG
                </button>
                <button type="button" @click="downloadTraceQr('svg')">
                  <Download :size="14" />
                  下载 SVG
                </button>
                <button type="button" @click="copyTraceQrPayload">
                  <Copy :size="14" />
                  复制地址
                </button>
              </div>
            </div>
          </div>
        </section>
      </form>
    </section>
  </div>

  <div v-show="false" class="quality-page">
    <div class="page-heading">
      <div>
        <span class="quality-page__eyebrow">QUALITY EVIDENCE WORKBENCH</span>
        <h2>全链质量证据台</h2>
        <p>从检测报告、电子合格证到一物一码与链上回执统一编排</p>
      </div>
    </div>

    <nav class="trace-stage-ribbon" aria-label="质量证据流程">
      <span v-for="(stage, index) in ['报告备案', '证书签发', '一物一码', '链上核验']" :key="stage">
        <i>{{ String(index + 1).padStart(2, '0') }}</i>
        <strong>{{ stage }}</strong>
      </span>
    </nav>

    <section class="trace-chain-strip">
      <div class="trace-chain-strip__lead">
        <span class="trace-chain-strip__mark">
          <Blocks :size="19" />
        </span>
        <div>
          <strong>联盟链存证网络</strong>
          <small>FISCO BCOS · 环境、合格证与溯源码摘要</small>
        </div>
      </div>

      <dl class="trace-chain-strip__stats">
        <div v-for="stat in chainStats" :key="stat.label">
          <dt>{{ stat.label }}</dt>
          <dd>{{ stat.value }}</dd>
        </div>
      </dl>

      <div class="trace-chain-nodes">
        <span
          v-for="node in chainNodes"
          :key="node.id"
          class="trace-chain-node"
          :class="`is-${node.tone}`"
        >
          <i aria-hidden="true"></i>
          <em>{{ node.organization }}</em>
          <small>{{ node.latency }}</small>
        </span>
      </div>
    </section>

    <div class="quality-workspace">
      <section class="panel trace-query-panel">
        <div class="panel__header">
          <h3>追溯查询</h3>
          <StatusTag>公开接口</StatusTag>
        </div>
        <div class="panel__body">
          <div class="trace-mode-tabs" role="tablist" aria-label="查询方式">
            <button
              type="button"
              role="tab"
              :class="{ 'is-active': queryMode === 'code' }"
              :aria-selected="queryMode === 'code'"
              @click="switchQueryMode('code')"
            >
              <QrCode :size="14" />
              按产品编码
            </button>
            <button
              type="button"
              role="tab"
              :class="{ 'is-active': queryMode === 'field' }"
              :aria-selected="queryMode === 'field'"
              @click="switchQueryMode('field')"
            >
              <Link2 :size="14" />
              按田块编号
            </button>
          </div>

          <div class="form-row">
            <input
              v-if="queryMode === 'code'"
              v-model="productCode"
              class="text-input"
              aria-label="产品编码"
            />
            <input
              v-else
              v-model="fieldNo"
              class="text-input"
              aria-label="田块编号"
            />
            <button
              class="primary-button"
              type="button"
              :disabled="loading || fieldLoading"
              @click="submitQuery"
            >
              <Search :size="17" />
              {{ loading || fieldLoading ? '查询中' : '查询' }}
            </button>
          </div>

          <p v-if="errorMessage" class="empty-state">{{ errorMessage }}</p>

          <template v-else-if="result">
            <dl class="data-list">
              <div class="data-list__row">
                <dt>产品编码</dt>
                <dd>{{ result.productCode }}</dd>
              </div>
              <div class="data-list__row">
                <dt>验证状态</dt>
                <dd><StatusTag :tone="resultTone">{{ result.verificationStatus }}</StatusTag></dd>
              </div>
              <div class="data-list__row">
                <dt>电子合格证</dt>
                <dd>{{ result.certificateNo ?? '未关联' }}</dd>
              </div>
              <div class="data-list__row">
                <dt>指针链</dt>
                <dd>{{ result.pointerChainValid ? '完整' : '异常' }}</dd>
              </div>
              <div class="data-list__row">
                <dt>环境根哈希</dt>
                <dd :title="result.rootHash">
                  {{ result.rootHash ? shortenHash(result.rootHash, 18) : '暂无' }}
                </dd>
              </div>
            </dl>

            <div class="trace-verify-summary">
              <div
                class="trace-verify-seal"
                :class="proofVerified ? 'is-verified' : 'is-risk'"
              >
                <component :is="proofVerified ? ShieldCheck : CircleAlert" :size="23" />
                <strong>{{ proofVerified ? '链上核验通过' : '核验待确认' }}</strong>
                <small>
                  {{ result.pointerChainValid ? '指针链完整' : '指针链异常' }}
                  · {{ result.stages.length }} 个节点
                </small>
              </div>

              <dl class="trace-verify-hashes">
                <div>
                  <dt><Database :size="12" />环境根哈希</dt>
                  <dd>
                    <code :title="result.rootHash">
                      {{ shortenHash(result.rootHash ?? '', 16) || '暂无' }}
                    </code>
                    <button
                      v-if="result.rootHash"
                      class="trace-copy-button"
                      type="button"
                      @click="copyText(result.rootHash, 'rootHash')"
                    >
                      <Copy :size="12" />
                      {{ copiedField === 'rootHash' ? '已复制' : '复制' }}
                    </button>
                  </dd>
                </div>
                <div>
                  <dt><KeyRound :size="12" />签名公钥</dt>
                  <dd>
                    <code>{{ result.publicKeyId ?? '未绑定' }}</code>
                    <button
                      v-if="result.publicKeyId"
                      class="trace-copy-button"
                      type="button"
                      @click="copyText(result.publicKeyId, 'publicKey')"
                    >
                      <Copy :size="12" />
                      {{ copiedField === 'publicKey' ? '已复制' : '复制' }}
                    </button>
                  </dd>
                </div>
                <div>
                  <dt><Link2 :size="12" />追溯节点</dt>
                  <dd>
                    <code>
                      {{ result.stages.length }} 个 · {{ result.fieldNo ?? '未登记田块' }}
                    </code>
                  </dd>
                </div>
              </dl>
            </div>

            <div v-if="result.found" class="trace-scan">
              <div class="trace-scan__head">
                <span><QrCode :size="15" />模拟消费者扫码</span>
                <select v-model="scanChannel" aria-label="扫码渠道">
                  <option
                    v-for="channel in scanChannels"
                    :key="channel.value"
                    :value="channel.value"
                  >
                    {{ channel.label }}
                  </option>
                </select>
                <button
                  class="secondary-button"
                  type="button"
                  :disabled="scanPending"
                  @click="performScan"
                >
                  {{ scanPending ? '扫码中' : '扫码验证' }}
                </button>
              </div>
              <p
                v-if="scanResult"
                class="trace-scan__result"
                :class="{ 'is-risk': !scanResult.firstScan }"
              >
                {{
                  scanResult.firstScan
                    ? '首次验证通过'
                    : `重复扫码风险：该编码已被扫码 ${scanResult.scanCount} 次`
                }}
                · {{ scanChannelLabel(scanResult.channel) }} ·
                {{ scanResult.scannedAt.replace('T', ' ').slice(0, 19) }}
                {{
                  scanResult.chainTxHash
                    ? ` · 链上回执 ${scanResult.chainTxHash.slice(0, 10)}…`
                    : ''
                }}
              </p>
              <p v-if="scanError" class="trace-scan__result is-risk">
                {{ scanError }}
              </p>
            </div>
          </template>

          <template v-else-if="fieldPath">
            <dl class="data-list">
              <div class="data-list__row">
                <dt>田块编号</dt>
                <dd>{{ fieldPath.fieldNo }}</dd>
              </div>
              <div class="data-list__row">
                <dt>绑定产品编码</dt>
                <dd>{{ fieldPath.productCode }}</dd>
              </div>
              <div class="data-list__row">
                <dt>指针链</dt>
                <dd>
                  <StatusTag
                    :tone="fieldPath.pointerChainValid ? 'online' : 'offline'"
                  >
                    {{ fieldPath.pointerChainValid ? '完整' : '异常' }}
                  </StatusTag>
                </dd>
              </div>
              <div class="data-list__row">
                <dt>追溯节点</dt>
                <dd>{{ fieldPath.stages.length }} 个</dd>
              </div>
            </dl>
          </template>

          <p v-else class="empty-state">
            输入产品编码或田块编号后查询追溯链路
          </p>
        </div>
      </section>

      <section class="panel report-panel">
        <div class="panel__header">
          <div>
            <h3>检测报告备案</h3>
            <p>文件保存后计算 SHA-256，原始文件可按报告编号下载</p>
          </div>
          <StatusTag tone="warning">内部接口</StatusTag>
        </div>
        <form class="report-form" @submit.prevent="submitQualityReport">
          <label class="report-field report-field--wide">
            <span>关联作物批次</span>
            <select v-model.number="reportForm.batchId">
              <option v-for="batch in cropBatches" :key="batch.id" :value="batch.id">
                {{ batch.label }}
              </option>
            </select>
          </label>

          <label class="report-field">
            <span>检测机构编号</span>
            <input v-model.trim="reportForm.institutionId" type="text" />
          </label>

          <label class="report-field">
            <span>报告编号</span>
            <input v-model.trim="reportForm.reportNo" type="text" />
          </label>

          <label class="report-field report-field--wide">
            <span>检测机构名称</span>
            <input v-model.trim="reportForm.institutionName" type="text" />
          </label>

          <label class="report-field">
            <span>检测时间</span>
            <input v-model="reportForm.testedAt" type="datetime-local" />
          </label>

          <label class="report-field">
            <span>结论摘要</span>
            <input v-model.trim="reportForm.resultSummary" type="text" />
          </label>

          <label class="report-file-picker report-field--wide">
            <FileUp :size="19" />
            <span>
              <strong>{{ reportFile?.name ?? '选择检测报告文件' }}</strong>
              <small>支持 PDF、图片或文本文件，单文件建议不超过 20 MB</small>
            </span>
            <input type="file" accept=".pdf,.png,.jpg,.jpeg,.txt,.doc,.docx" @change="selectReportFile" />
          </label>

          <div class="report-actions report-field--wide">
            <button class="primary-button" type="submit" :disabled="reportUploading">
              <ShieldCheck :size="17" />
              {{ reportUploading ? '正在计算并保存' : '保存检测报告' }}
            </button>
            <span v-if="reportMessage" class="report-feedback is-success">{{ reportMessage }}</span>
            <span v-if="reportError" class="report-feedback is-error">{{ reportError }}</span>
          </div>

          <article v-if="uploadedReport" class="report-result report-field--wide">
            <div>
              <span>最近保存</span>
              <strong>{{ uploadedReport.reportNo }}</strong>
              <small>{{ uploadedReport.institutionName }}</small>
            </div>
            <dl>
              <div>
                <dt>内容摘要</dt>
                <dd :title="uploadedReport.contentHash">
                  {{ uploadedReport.contentHash.slice(0, 24) }}...
                </dd>
              </div>
              <div>
                <dt>存储状态</dt>
                <dd>{{ uploadedReport.status }}</dd>
              </div>
            </dl>
            <button
              class="secondary-button"
              type="button"
              :disabled="reportDownloading"
              @click="downloadReport"
            >
              <Download :size="16" />
              {{ reportDownloading ? '下载中' : '下载原文件' }}
            </button>
          </article>
        </form>
      </section>
    </div>

    <section class="panel trace-ledger-panel">
      <div class="panel__header">
        <div>
          <h3>链上存证与哈希指针链</h3>
          <p>
            {{
              result
                ? `${result.productCode} · ${chainProof?.message ?? result.message}`
                : fieldPath
                  ? `田块 ${fieldPath.fieldNo} · ${fieldPath.productCode} · ${
                      fieldPath.pointerChainValid ? '指针链完整' : '指针链异常'
                    }`
                  : '查询产品编码或田块编号后展示存证回执与哈希链接结构'
            }}
          </p>
        </div>
        <div class="trace-ledger-panel__state">
          <template v-if="result">
            <StatusTag :tone="proofTone">{{ proofStatus }}</StatusTag>
            <span v-if="proofLoading">读取回执中</span>
          </template>
          <StatusTag
            v-else-if="fieldPath"
            :tone="fieldPath.pointerChainValid ? 'online' : 'offline'"
          >
            {{ fieldPath.pointerChainValid ? '指针链完整' : '指针链异常' }}
          </StatusTag>
        </div>
      </div>

      <div v-if="result || fieldPath" class="trace-ledger">
        <div v-if="result" class="trace-proof-grid">
          <article
            v-for="item in proofItems"
            :key="item.key"
            class="trace-proof-card"
            :class="`is-${item.tone}`"
          >
            <header>
              <span class="trace-proof-card__icon">
                <component :is="item.icon" :size="16" />
              </span>
              <div>
                <strong>{{ item.title }}</strong>
                <small :title="item.subtitle">{{ item.subtitle }}</small>
              </div>
              <em>{{ item.statusLabel }}</em>
            </header>

            <dl>
              <div>
                <dt>{{ item.hashLabel }}</dt>
                <dd>
                  <code :title="item.hash || '暂无回执'">
                    {{ item.hash ? shortenHash(item.hash, 12) : '暂无回执' }}
                  </code>
                  <button
                    v-if="item.hash"
                    class="trace-copy-button is-icon"
                    type="button"
                    :aria-label="`复制${item.hashLabel}`"
                    @click="copyText(item.hash, item.key)"
                  >
                    <Copy :size="12" />
                  </button>
                </dd>
              </div>
              <div>
                <dt>链上交易</dt>
                <dd>
                  <code :title="item.txHash || '等待回执'">
                    {{ item.txHash ? shortenHash(item.txHash, 12) : '等待回执' }}
                  </code>
                  <button
                    v-if="item.txHash"
                    class="trace-copy-button is-icon"
                    type="button"
                    aria-label="复制链上交易哈希"
                    @click="copyText(item.txHash, `${item.key}-tx`)"
                  >
                    <Copy :size="12" />
                  </button>
                </dd>
              </div>
            </dl>

            <footer>
              <component
                :is="item.tone === 'online' ? CircleCheck : CircleAlert"
                :size="13"
              />
              <span>
                {{
                  copiedField === item.key || copiedField === `${item.key}-tx`
                    ? '已复制到剪贴板'
                    : item.footnote
                }}
              </span>
            </footer>
          </article>
        </div>

        <div class="trace-chain-panel">
          <div class="trace-chain-panel__heading">
            <div>
              <strong>哈希指针链</strong>
              <span>每个追溯节点引用前一节点摘要，指针连续即不可篡改</span>
            </div>
            <span
              class="trace-chain-panel__state"
              :class="{ 'is-risk': chainStages.length > 0 && !pointerChainValid }"
            >
              <Link2 :size="13" />
              {{ chainStateLabel }}
            </span>
          </div>

          <form
            v-if="canAppendNode && activeFieldNo"
            class="trace-node-form"
            @submit.prevent="submitTraceNode"
          >
            <label class="report-field">
              <span>追溯阶段</span>
              <select v-model="nodeForm.stageType">
                <option
                  v-for="stage in nodeStageOptions"
                  :key="stage"
                  :value="stage"
                >
                  {{ stage }}
                </option>
              </select>
            </label>
            <label class="report-field report-field--wide">
              <span>节点说明</span>
              <input
                v-model.trim="nodeForm.summary"
                type="text"
                placeholder="例如：发往冷链中转仓"
              />
            </label>
            <button class="primary-button" type="submit" :disabled="nodeSaving">
              <PlusCircle :size="16" />
              {{ nodeSaving ? '上链中' : '追加节点' }}
            </button>
          </form>
          <p v-if="nodeMessage" class="trace-node-form__message">
            {{ nodeMessage }}
          </p>
          <p v-if="nodeError" class="trace-node-form__error">
            {{ nodeError }}
          </p>
          <p v-if="loading" class="trace-chain-empty">正在加载哈希指针链…</p>
          <p v-else-if="chainStages.length === 0" class="trace-chain-empty">
            {{ chainEmptyHint }}
          </p>

          <ol class="trace-block-list">
            <li
              v-for="stage in chainStages"
              :key="stage.nodeId"
              class="trace-block"
              :class="{
                'is-genesis': stage.index === 0,
                'is-broken': !stage.linked,
              }"
            >
              <div class="trace-block__head">
                <span>{{ stage.index === 0 ? '创世块' : `区块 ${String(stage.index).padStart(2, '0')}` }}</span>
                <em>{{ stage.linked ? '已链接' : '断链' }}</em>
              </div>
              <strong>{{ stage.stageType }}</strong>
              <code :title="stage.payloadHash">{{ stage.payloadShort }}</code>
              <small>前序 {{ stage.previousShort }}</small>
              <small v-if="stage.chainTxHash" class="trace-block__chain">
                链上 {{ stage.chainTxShort }}
              </small>
              <small v-else class="trace-block__chain is-pending">
                链上待补偿
              </small>
              <time>{{ stage.createdAt }}</time>
            </li>
          </ol>
        </div>
      </div>

      <p v-else class="trace-ledger__empty">
        查询产品编码后，这里显示环境周期、电子合格证、一物一码的存证回执和完整哈希指针链；按田块编号查询时只展示该田块的指针链
      </p>
    </section>

    <section class="panel quality-management-panel">
      <div class="panel__header">
        <div>
          <h3>质检业务操作</h3>
          <p>合格证备案和溯源码生成使用已关联的作物批次</p>
        </div>
        <StatusTag tone="warning">内部接口</StatusTag>
      </div>
      <div class="quality-management-grid">
        <form class="management-form" @submit.prevent="submitCertificate">
          <div class="management-form__heading">
            <span><Award :size="18" /></span>
            <div>
              <strong>合格证备案</strong>
              <small>保存合格证编号、摘要和有效状态</small>
            </div>
          </div>
          <label>
            <span>关联作物批次</span>
            <select v-model.number="certificateForm.batchId">
              <option v-for="batch in cropBatches" :key="batch.id" :value="batch.id">
                {{ batch.label }}
              </option>
            </select>
          </label>
          <label>
            <span>合格证编号</span>
            <input v-model.trim="certificateForm.certificateNo" type="text" />
          </label>
          <label>
            <span>内容摘要</span>
            <input
              v-model.trim="certificateForm.contentHash"
              type="text"
              placeholder="可自动使用检测报告摘要"
            />
          </label>
          <label>
            <span>合格证状态</span>
            <select v-model="certificateForm.status">
              <option value="VALID">有效</option>
              <option value="DRAFT">草稿</option>
            </select>
          </label>
          <button class="primary-button" type="submit" :disabled="certificateSaving">
            <ShieldCheck :size="16" />
            {{ certificateSaving ? '正在备案' : '保存合格证' }}
          </button>
          <p v-if="certificateMessage" class="management-feedback is-success">
            {{ certificateMessage }}
          </p>
          <p v-if="certificateError" class="management-feedback is-error">
            {{ certificateError }}
          </p>

          <div class="management-form__verify">
            <div class="management-form__verify-head">
              <ShieldCheck :size="16" />
              <div>
                <strong>电子合格证验签</strong>
                <small>
                  浏览器生成 Ed25519 密钥对并对 canonical 串签名，后端验签后写入验签流水
                </small>
              </div>
            </div>
            <div class="management-form__verify-actions">
              <button
                class="secondary-button"
                type="button"
                :disabled="verifyPending"
                @click="verifyCertificate(false)"
              >
                {{ verifyPending ? '验签中' : '生成密钥并验签' }}
              </button>
              <button
                class="secondary-button"
                type="button"
                :disabled="verifyPending"
                @click="verifyCertificate(true)"
              >
                篡改后验签
              </button>
            </div>
            <p
              v-if="verifyMessage"
              class="management-feedback"
              :class="verifyFailed ? 'is-error' : 'is-success'"
            >
              {{ verifyMessage }}
            </p>
          </div>
        </form>

        <form class="management-form" @submit.prevent="submitTraceCode">
          <div class="management-form__heading">
            <span><QrCode :size="18" /></span>
            <div>
              <strong>生成一物一码</strong>
              <small>绑定产品编码、田块和签名标识</small>
            </div>
          </div>
          <label>
            <span>关联作物批次</span>
            <select v-model.number="traceCodeForm.batchId">
              <option v-for="batch in cropBatches" :key="batch.id" :value="batch.id">
                {{ batch.label }}
              </option>
            </select>
          </label>
          <label>
            <span>田块编号</span>
            <input v-model.trim="traceCodeForm.fieldNo" type="text" />
          </label>
          <label>
            <span>产品编码</span>
            <input v-model.trim="traceCodeForm.productCode" type="text" />
          </label>
          <label>
            <span>公钥标识</span>
            <input v-model.trim="traceCodeForm.publicKeyId" type="text" />
          </label>
          <label>
            <span>签名内容</span>
            <input v-model.trim="traceCodeForm.signatureValue" type="text" />
          </label>
          <button class="primary-button" type="submit" :disabled="traceCodeSaving">
            <QrCode :size="16" />
            {{ traceCodeSaving ? '正在生成' : '生成溯源码' }}
          </button>
          <p v-if="traceCodeMessage" class="management-feedback is-success">
            {{ traceCodeMessage }}
          </p>
          <p v-if="traceCodeError" class="management-feedback is-error">
            {{ traceCodeError }}
          </p>
        </form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.quality-workspace {
  display: grid;
  grid-template-columns: minmax(330px, 0.82fr) minmax(560px, 1.35fr);
  gap: 18px;
  align-items: start;
}

.quality-page {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.trace-chain-strip {
  display: grid;
  grid-template-columns: minmax(210px, 1fr) minmax(320px, 1.7fr) minmax(250px, 1fr);
  align-items: center;
  gap: 18px;
  padding: 14px 18px;
  color: #e8f4ec;
  background-color: #10301f;
  background-image: repeating-linear-gradient(
    115deg,
    rgb(255 255 255 / 4%) 0 1px,
    transparent 1px 54px
  );
  border-radius: 8px;
}

.trace-chain-strip__lead {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.trace-chain-strip__mark {
  display: grid;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  place-items: center;
  color: #0f2a1c;
  background: #7fd0a4;
  border-radius: 7px;
}

.trace-chain-strip__lead strong,
.trace-chain-strip__lead small {
  display: block;
}

.trace-chain-strip__lead strong {
  font-size: 13px;
}

.trace-chain-strip__lead small {
  margin-top: 4px;
  color: #9dbaa9;
  font-size: 10px;
}

.trace-chain-strip__stats {
  display: grid;
  margin: 0;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.trace-chain-strip__stats dt {
  color: #9dbaa9;
  font-size: 10px;
  white-space: nowrap;
}

.trace-chain-strip__stats dd {
  margin: 4px 0 0;
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.trace-chain-nodes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.trace-chain-node {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  padding: 6px 9px;
  background: rgb(255 255 255 / 6%);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 6px;
}

.trace-chain-node i {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  background: #6fd39b;
  border-radius: 50%;
  box-shadow: 0 0 0 0 rgb(111 211 155 / 55%);
  animation: trace-node-pulse 2.6s ease-out infinite;
}

.trace-chain-node.is-warning i {
  background: #e0ae53;
  animation: none;
}

.trace-chain-node.is-offline i {
  background: #d08b85;
  animation: none;
}

.trace-chain-node em {
  overflow: hidden;
  color: #dcece2;
  font-size: 10px;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-chain-node small {
  margin-left: auto;
  color: #8fb29e;
  font-size: 9px;
  white-space: nowrap;
}

@keyframes trace-node-pulse {
  0% {
    box-shadow: 0 0 0 0 rgb(111 211 155 / 55%);
  }

  70% {
    box-shadow: 0 0 0 7px rgb(111 211 155 / 0%);
  }

  100% {
    box-shadow: 0 0 0 0 rgb(111 211 155 / 0%);
  }
}

.trace-verify-summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
  padding: 14px;
  background: #f7faf8;
  border: 1px solid #e1e9e3;
  border-radius: 8px;
}

.trace-scan {
  display: grid;
  gap: 9px;
  margin-top: 14px;
  padding: 12px 14px;
  background: #f7faf8;
  border: 1px solid #e1e9e3;
  border-radius: 8px;
}

.trace-scan__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
}

.trace-scan__head > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #26382e;
  font-size: 12px;
  font-weight: 600;
}

.trace-scan__head select {
  min-height: 32px;
  padding: 0 9px;
  color: #17201b;
  background: #ffffff;
  border: 1px solid #cbd7cf;
  border-radius: 7px;
  font-size: 12px;
}

.trace-scan__result {
  margin: 0;
  color: #217354;
  font-size: 11px;
  line-height: 1.6;
}

.trace-scan__result.is-risk {
  color: #9d6918;
}

.trace-verify-seal {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 5px;
  width: 116px;
  height: 116px;
  padding: 10px;
  color: #217354;
  text-align: center;
  background: #eaf5ee;
  border: 1px dashed #93c4a9;
  border-radius: 50%;
}

.trace-verify-seal strong {
  font-size: 12px;
  line-height: 1.35;
}

.trace-verify-seal small {
  color: #6c7c73;
  font-size: 9px;
  line-height: 1.4;
}

.trace-verify-seal.is-risk {
  color: #a5611f;
  background: #fdf5e8;
  border-color: #e0c190;
}

.trace-verify-hashes {
  display: grid;
  align-content: center;
  gap: 10px;
  margin: 0;
  min-width: 0;
}

.trace-verify-hashes > div {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.trace-verify-hashes dt {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #6c7c73;
  font-size: 10px;
}

.trace-verify-hashes dd {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin: 0;
}

.trace-verify-hashes code,
.trace-proof-card code,
.trace-block code {
  overflow: hidden;
  color: #1f3a2c;
  font-family: Consolas, "Courier New", monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-copy-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  color: #217354;
  background: #eef6f1;
  border: 1px solid #c9ded1;
  border-radius: 6px;
  font-size: 9px;
}

.trace-copy-button:hover {
  background: #e2f0e7;
}

.trace-copy-button.is-icon {
  padding: 4px;
}

.quality-management-panel {
  min-width: 0;
}

.quality-management-panel .panel__header {
  align-items: flex-start;
}

.quality-management-panel .panel__header p {
  margin: 5px 0 0;
  color: #65736c;
  font-size: 12px;
  font-weight: 400;
}

.quality-management-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  padding: 18px;
}

.management-form {
  display: grid;
  align-content: start;
  gap: 13px;
  min-width: 0;
  padding: 16px;
  background: #f7faf8;
  border: 1px solid #e0e9e3;
  border-radius: 8px;
}

.management-form__heading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e0e8e3;
}

.management-form__heading > span {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  color: #ffffff;
  background: #217354;
  border-radius: 7px;
}

.management-form__heading strong,
.management-form__heading small {
  display: block;
}

.management-form__heading strong {
  color: #26382e;
  font-size: 13px;
}

.management-form__heading small {
  margin-top: 3px;
  color: #75827a;
  font-size: 10px;
}

.management-form label {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.management-form label > span {
  color: #53635b;
  font-size: 12px;
  font-weight: 700;
}

.management-form input,
.management-form select {
  width: 100%;
  min-height: 40px;
  padding: 0 11px;
  color: #17201b;
  background: #ffffff;
  border: 1px solid #cbd7cf;
  border-radius: 7px;
  outline: none;
}

.management-form input:focus,
.management-form select:focus {
  border-color: #217354;
  box-shadow: 0 0 0 3px rgb(33 115 84 / 11%);
}

.management-feedback {
  margin: 0;
  font-size: 12px;
}

.management-feedback.is-success {
  color: #217354;
}

.management-feedback.is-error {
  color: #a14e48;
}

.management-form__verify {
  display: grid;
  gap: 10px;
  margin-top: 4px;
  padding: 12px;
  background: #ffffff;
  border: 1px dashed #bdd6c6;
  border-radius: 8px;
}

.management-form__verify-head {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  color: #217354;
}

.management-form__verify-head strong,
.management-form__verify-head small {
  display: block;
}

.management-form__verify-head strong {
  color: #26382e;
  font-size: 12px;
}

.management-form__verify-head small {
  margin-top: 3px;
  color: #75827a;
  font-size: 10px;
  line-height: 1.5;
}

.management-form__verify-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
}

.trace-ledger-panel {
  min-width: 0;
}

.trace-ledger-panel .panel__header {
  align-items: flex-start;
}

.trace-ledger-panel .panel__header p {
  margin: 5px 0 0;
  color: #65736c;
  font-size: 12px;
  font-weight: 400;
}

.trace-ledger-panel__state {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  color: #75827a;
  font-size: 10px;
  white-space: nowrap;
}

.trace-ledger {
  display: grid;
  gap: 18px;
  padding: 18px;
}

.trace-proof-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.trace-proof-card {
  display: grid;
  align-content: start;
  gap: 10px;
  min-width: 0;
  padding: 13px;
  background: #f8fbf9;
  border: 1px solid #8fb9a4;
  border-radius: 8px;
}

.trace-proof-card.is-warning {
  border-color: #d3a04c;
}

.trace-proof-card.is-offline {
  border-color: #c5857f;
}

.trace-proof-card > header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
}

.trace-proof-card__icon {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  color: #ffffff;
  background: #217354;
  border-radius: 7px;
}

.trace-proof-card.is-warning .trace-proof-card__icon {
  background: #b3873a;
}

.trace-proof-card.is-offline .trace-proof-card__icon {
  background: #a85751;
}

.trace-proof-card header strong,
.trace-proof-card header small {
  display: block;
}

.trace-proof-card header strong {
  color: #24382e;
  font-size: 12px;
}

.trace-proof-card header small {
  margin-top: 3px;
  overflow: hidden;
  color: #75827a;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-proof-card header em {
  padding: 3px 7px;
  color: #217354;
  background: #e6f2ea;
  border-radius: 999px;
  font-size: 9px;
  font-style: normal;
  white-space: nowrap;
}

.trace-proof-card.is-warning header em {
  color: #94651a;
  background: #fbf1dd;
}

.trace-proof-card.is-offline header em {
  color: #a14e48;
  background: #faeceb;
}

.trace-proof-card dl {
  display: grid;
  gap: 7px;
  margin: 0;
  padding-top: 10px;
  border-top: 1px solid #e4ece7;
}

.trace-proof-card dl > div {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.trace-proof-card dt {
  color: #75827a;
  font-size: 10px;
}

.trace-proof-card dd {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin: 0;
}

.trace-proof-card footer {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-top: 9px;
  color: #217354;
  border-top: 1px solid #e4ece7;
  font-size: 10px;
}

.trace-proof-card.is-warning footer {
  color: #94651a;
}

.trace-proof-card.is-offline footer {
  color: #a14e48;
}

.trace-chain-panel {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.trace-chain-panel__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.trace-chain-panel__heading strong,
.trace-chain-panel__heading > div > span {
  display: block;
}

.trace-chain-panel__heading strong {
  color: #24382e;
  font-size: 13px;
}

.trace-chain-panel__heading > div > span {
  margin-top: 4px;
  color: #75827a;
  font-size: 10px;
}

.trace-chain-panel__state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  color: #217354;
  background: #e8f4ed;
  border: 1px solid #c2ddcb;
  border-radius: 999px;
  font-size: 10px;
  white-space: nowrap;
}

.trace-chain-panel__state.is-risk {
  color: #a14e48;
  background: #faeceb;
  border-color: #e5c6c3;
}

.trace-block-list {
  display: flex;
  gap: 0;
  margin: 0;
  padding: 4px 2px 10px;
  overflow-x: auto;
  list-style: none;
}

.trace-block {
  position: relative;
  display: grid;
  flex: 0 0 auto;
  align-content: start;
  gap: 5px;
  width: 186px;
  margin-right: 26px;
  padding: 11px 12px;
  background: #ffffff;
  border: 1px solid #cfe0d5;
  border-radius: 8px;
}

.trace-block::before {
  content: "";
  position: absolute;
  top: 50%;
  right: -27px;
  width: 28px;
  height: 2px;
  background: #b9d6c4;
}

.trace-block::after {
  content: "";
  position: absolute;
  top: calc(50% - 3px);
  right: -14px;
  width: 6px;
  height: 6px;
  background: #4f9c74;
  border-radius: 50%;
}

.trace-block:last-child {
  margin-right: 0;
}

.trace-block:last-child::before,
.trace-block:last-child::after {
  display: none;
}

.trace-block.is-genesis {
  background: #f3f9f5;
  border-style: dashed;
}

.trace-node-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 14px;
}

.trace-node-form .report-field {
  min-width: 150px;
}

.trace-node-form .report-field--wide {
  flex: 1 1 220px;
}

.trace-node-form__message,
.trace-node-form__error {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.6;
}

.trace-node-form__message {
  color: #35704f;
}

.trace-node-form__error {
  color: #a14e48;
}

.trace-block__chain {
  display: block;
  color: #4f9c74;
}

.trace-block__chain.is-pending {
  color: #9d6918;
}

.trace-block.is-broken {
  background: #fdf6f5;
  border-color: #e0b3ae;
}

.trace-block.is-broken::before {
  background: #e0b3ae;
}

.trace-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #6f7d75;
  font-size: 9px;
}

.trace-block__head em {
  color: #217354;
  font-style: normal;
}

.trace-block.is-broken .trace-block__head em {
  color: #a14e48;
}

.trace-block strong {
  color: #1f3529;
  font-size: 13px;
}

.trace-block code {
  color: #2f6b4e;
}

.trace-block small {
  color: #8b968f;
  font-size: 9px;
}

.trace-block time {
  color: #6f7d75;
  font-size: 9px;
}

.trace-ledger__empty {
  margin: 0;
  padding: 20px 18px;
  color: #75827a;
  font-size: 12px;
}

.trace-chain-empty {
  margin: 0 0 10px;
  padding: 20px 16px;
  color: #75827a;
  background: #f6f9f7;
  border: 1px dashed #cfdcd5;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
}

.tv2-chain-empty {
  grid-column: 1 / -1;
  margin: 0;
  padding: 20px 16px;
  color: #9db3a9;
  background: rgb(255 255 255 / 2.5%);
  border: 1px dashed rgb(91 225 163 / 18%);
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
}

.trace-query-panel,
.report-panel {
  min-width: 0;
}

.trace-query-panel .text-input {
  flex: 1 1 160px;
}

.trace-query-panel .primary-button {
  flex: 0 0 auto;
  white-space: nowrap;
}

.trace-mode-tabs {
  display: inline-flex;
  gap: 3px;
  margin-bottom: 12px;
  padding: 3px;
  background: #eef3ef;
  border: 1px solid #dce5df;
  border-radius: 7px;
}

.trace-mode-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 11px;
  color: #52645a;
  background: transparent;
  border: 0;
  border-radius: 5px;
  font-size: 12px;
}

.trace-mode-tabs button.is-active {
  color: #1d7a58;
  background: #ffffff;
  box-shadow: 0 1px 2px rgb(24 40 32 / 12%);
}

.report-panel .panel__header {
  align-items: flex-start;
}

.report-panel .panel__header p {
  margin: 5px 0 0;
  color: #65736c;
  font-size: 12px;
  font-weight: 400;
}

.report-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 18px;
}

.report-field {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.report-field--wide {
  grid-column: 1 / -1;
}

.report-field > span {
  color: #53635b;
  font-size: 12px;
  font-weight: 700;
}

.report-field input,
.report-field select {
  width: 100%;
  min-height: 40px;
  padding: 0 11px;
  color: #17201b;
  background: #ffffff;
  border: 1px solid #cbd7cf;
  border-radius: 7px;
  outline: none;
}

.report-field select {
  cursor: pointer;
}

.report-field input:focus,
.report-field select:focus {
  border-color: #217354;
  box-shadow: 0 0 0 3px rgba(33, 115, 84, 0.12);
}

.report-file-picker {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 68px;
  padding: 12px 14px;
  color: #217354;
  background: #f6faf7;
  border: 1px dashed #a9c9b3;
  border-radius: 8px;
  cursor: pointer;
}

.report-file-picker:hover {
  background: #eef7f1;
  border-color: #69a07d;
}

.report-file-picker > span,
.report-file-picker strong,
.report-file-picker small {
  display: block;
}

.report-file-picker strong {
  color: #26382e;
  font-size: 13px;
}

.report-file-picker small {
  margin-top: 4px;
  color: #75827a;
  font-size: 11px;
}

.report-file-picker input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
}

.report-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 11px;
}

.report-feedback {
  font-size: 12px;
}

.report-feedback.is-success {
  color: #217354;
}

.report-feedback.is-error {
  color: #a14e48;
}

.report-result {
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(210px, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 14px;
  background: #eef7f1;
  border: 1px solid #c2ddcb;
  border-radius: 8px;
}

.report-result > div > span,
.report-result > div > strong,
.report-result > div > small {
  display: block;
}

.report-result > div > span {
  color: #65736c;
  font-size: 11px;
}

.report-result > div > strong {
  margin-top: 4px;
  color: #173f34;
  font-size: 15px;
}

.report-result > div > small {
  margin-top: 4px;
  color: #65736c;
  font-size: 11px;
}

.report-result dl {
  display: grid;
  gap: 7px;
  min-width: 0;
  margin: 0;
}

.report-result dl div {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  font-size: 11px;
}

.report-result dt {
  color: #65736c;
}

.report-result dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: #26382e;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.secondary-button:disabled {
  cursor: wait;
  opacity: 0.64;
}

@media (max-width: 1120px) {
  .quality-workspace {
    grid-template-columns: 1fr;
  }

  .trace-chain-strip {
    grid-template-columns: 1fr;
  }

  .trace-chain-strip__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trace-proof-grid {
    grid-template-columns: 1fr;
  }

  .report-result {
    grid-template-columns: minmax(180px, 0.8fr) minmax(210px, 1fr) auto;
  }

  .quality-management-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .report-form {
    grid-template-columns: 1fr;
  }

  .report-field--wide {
    grid-column: auto;
  }

  .report-result {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .trace-verify-summary {
    grid-template-columns: 1fr;
    justify-items: stretch;
  }

  .trace-verify-seal {
    width: 100%;
    height: auto;
    padding: 14px;
    border-radius: 8px;
  }

  .trace-chain-nodes {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .trace-query-panel .text-input {
    flex: 0 0 auto;
  }
}

/* Traceability v2: evidence chain laboratory */
.trace-v2 {
  display: grid;
  gap: 14px;
}

.tv2-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  background:
    linear-gradient(100deg, rgb(79 224 160 / 8%), transparent 42%),
    rgb(8 24 17 / 92%);
  border: 1px solid rgb(91 225 163 / 17%);
  border-radius: 8px;
}

.tv2-hero > div:first-child > span,
.tv2-console header span,
.tv2-evidence header span,
.tv2-lab header > span,
.tv2-chain-body header span {
  color: #4fe0a0;
  font-family: Consolas, monospace;
  font-size: 8px;
  letter-spacing: 0.14em;
}

.tv2-hero h2 {
  margin: 5px 0 0;
  font-size: 24px;
}

.tv2-hero p {
  margin: 6px 0 0;
  color: #789487;
  font-size: 10px;
}

.tv2-chain-stats {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 7px;
}

.tv2-chain-stats article {
  min-width: 104px;
  padding: 10px 12px;
  background: rgb(0 0 0 / 15%);
  border: 1px solid rgb(79 224 160 / 12%);
  border-radius: 7px;
}

.tv2-chain-stats small,
.tv2-chain-stats strong,
.tv2-chain-stats em {
  display: block;
}

.tv2-chain-stats em {
  margin-top: 3px;
  color: #698579;
  font-size: 11.5px;
  font-style: normal;
}

.tv2-chain-stats small {
  color: #698579;
  font-size: 7px;
}

.tv2-chain-stats strong {
  margin-top: 5px;
  color: #e8fff5;
  font-size: 13px;
}

.tv2-node-line {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  padding: 9px;
  background: rgb(7 21 15 / 82%);
  border: 1px solid rgb(91 225 163 / 12%);
  border-radius: 8px;
}

.tv2-node-line > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 29px;
  padding: 0 8px;
  color: #8ea99b;
  background: rgb(255 255 255 / 2.5%);
  border: 1px solid rgb(91 225 163 / 9%);
  border-radius: 6px;
  font-size: 8px;
}

.tv2-node-line i {
  width: 6px;
  height: 6px;
  background: #4fe0a0;
  border-radius: 50%;
}

.tv2-node-line small {
  color: #607b6f;
  font-size: 7px;
}

.tv2-workbench {
  display: grid;
  grid-template-columns: minmax(300px, 0.82fr) minmax(360px, 1fr) minmax(340px, 0.92fr);
  gap: 12px;
  align-items: start;
}

.tv2-console,
.tv2-evidence,
.tv2-lab,
.tv2-chain-body,
.tv2-operations > form {
  background: rgb(9 27 19 / 94%);
  border: 1px solid rgb(91 225 163 / 14%);
  border-radius: 8px;
}

.tv2-console > header,
.tv2-evidence > header,
.tv2-lab > header,
.tv2-chain-body > header {
  padding: 13px 14px;
  border-bottom: 1px solid rgb(91 225 163 / 10%);
}

.tv2-evidence > header,
.tv2-chain-body > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.tv2-console h3,
.tv2-evidence h3,
.tv2-lab h3,
.tv2-chain-body h3 {
  margin: 4px 0 0;
  font-size: 14px;
}

.tv2-mode-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  padding: 12px 12px 0;
}

.tv2-mode-tabs button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 34px;
  color: #789487;
  background: rgb(255 255 255 / 2.5%);
  border: 1px solid rgb(91 225 163 / 11%);
  border-radius: 6px;
  font-size: 8px;
}

.tv2-mode-tabs button.is-active {
  color: #062116;
  background: #55dfa0;
}

.tv2-query-input {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 7px;
  padding: 10px 12px 0;
}

.tv2-query-input input,
.tv2-query-input button {
  min-height: 36px;
  padding: 0 9px;
  color: #e4fff2;
  background: rgb(1 12 7 / 72%);
  border: 1px solid rgb(91 225 163 / 14%);
  border-radius: 7px;
  font-size: 9px;
}

.tv2-query-input button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #062116;
  background: #55dfa0;
  border-color: #55dfa0;
  font-weight: 700;
}

.tv2-seal {
  display: grid;
  justify-items: center;
  gap: 5px;
  margin: 12px;
  padding: 16px;
  color: #4fe0a0;
  background:
    repeating-linear-gradient(45deg, rgb(79 224 160 / 4%) 0 8px, transparent 8px 16px),
    rgb(3 15 9 / 58%);
  border: 1px solid rgb(79 224 160 / 18%);
  border-radius: 8px;
}

.tv2-seal.is-risk {
  color: #ff968b;
  background: rgb(255 129 117 / 5%);
  border-color: rgb(255 129 117 / 20%);
}

.tv2-seal strong {
  font-size: 13px;
}

.tv2-seal span,
.tv2-seal small {
  color: #769285;
  font-size: 8px;
}

.tv2-key-values {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0 12px;
}

.tv2-key-values > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 40px;
  border-bottom: 1px solid rgb(91 225 163 / 8%);
}

.tv2-key-values dt {
  color: #698579;
  font-size: 8px;
}

.tv2-key-values dd {
  margin: 0;
  overflow: hidden;
  color: #dff8ec;
  font-size: 9px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tv2-key-values code {
  color: #67e3aa;
  font-family: Consolas, monospace;
  font-size: 8px;
}

.tv2-scan-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 7px;
  padding: 10px 12px 0;
}

.tv2-scan-actions select,
.tv2-scan-actions button {
  min-height: 33px;
  padding: 0 8px;
  color: #dff8ec;
  background: rgb(1 12 7 / 72%);
  border: 1px solid rgb(91 225 163 / 14%);
  border-radius: 6px;
  font-size: 8px;
}

.tv2-scan-actions button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #062116;
  background: #55dfa0;
  border-color: #55dfa0;
}

.tv2-scan-result,
.tv2-error,
.tv2-chain-body > p {
  margin: 9px 12px;
  color: #7edfb0;
  font-size: 8px;
  line-height: 1.6;
}

.tv2-scan-result.is-risk,
.tv2-error,
.tv2-chain-body > p.is-error {
  color: #ff968b;
}

.tv2-proof-cards {
  display: grid;
  gap: 7px;
  padding: 11px;
}

.tv2-proof-cards article {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  min-height: 62px;
  padding: 9px;
  background: rgb(255 255 255 / 2.5%);
  border: 1px solid rgb(91 225 163 / 9%);
  border-radius: 7px;
}

.tv2-proof-cards article > span {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: #4fe0a0;
  background: rgb(79 224 160 / 8%);
  border-radius: 6px;
}

.tv2-proof-cards strong,
.tv2-proof-cards small,
.tv2-proof-cards code {
  display: block;
}

.tv2-proof-cards strong {
  font-size: 10px;
}

.tv2-proof-cards small {
  margin-top: 3px;
  color: #698579;
  font-size: 8px;
}

.tv2-proof-cards code {
  margin-top: 4px;
  overflow: hidden;
  color: #67e3aa;
  font-family: Consolas, monospace;
  font-size: 7px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tv2-proof-cards em {
  color: #55dfa0;
  font-size: 8px;
  font-style: normal;
}

.tv2-field-path {
  display: grid;
  gap: 7px;
  margin: 12px;
  padding: 14px;
  background: rgb(79 224 160 / 5%);
  border: 1px solid rgb(79 224 160 / 12%);
  border-radius: 7px;
}

.tv2-field-path strong {
  font-size: 18px;
}

.tv2-field-path span {
  color: #739084;
  font-size: 9px;
}

.tv2-empty {
  margin: 0;
  padding: 54px 14px;
  color: #698579;
  text-align: center;
  font-size: 9px;
}

.tv2-lab {
  display: grid;
  align-content: start;
}

.tv2-report-form {
  display: grid;
  gap: 8px;
  padding: 12px;
}

.tv2-report-form input,
.tv2-report-form select,
.tv2-report-form > button,
.tv2-file {
  min-height: 35px;
  padding: 0 9px;
  color: #e4fff2;
  background: rgb(1 12 7 / 72%);
  border: 1px solid rgb(91 225 163 / 14%);
  border-radius: 6px;
  font-size: 8px;
}

.tv2-report-form > button {
  color: #062116;
  background: #55dfa0;
  border-color: #55dfa0;
  font-weight: 700;
}

.tv2-form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
}

.tv2-file {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #85a397;
  cursor: pointer;
}

.tv2-file input {
  display: none;
}

.tv2-report-form p {
  margin: 0;
  font-size: 8px;
}

.tv2-report-form .is-success,
.tv2-chain-body .is-success {
  color: #7edfb0;
}

.tv2-report-form .is-error,
.tv2-chain-body .is-error {
  color: #ff968b;
}

.tv2-uploaded {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 5px 9px;
  margin: 0 12px 12px;
  padding: 10px;
  background: rgb(79 224 160 / 5%);
  border: 1px solid rgb(79 224 160 / 12%);
  border-radius: 7px;
}

.tv2-uploaded > span,
.tv2-uploaded code {
  color: #698579;
  font-size: 7px;
}

.tv2-uploaded strong {
  font-size: 10px;
}

.tv2-uploaded button {
  grid-row: span 2;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #062116;
  background: #55dfa0;
  border: 0;
  border-radius: 6px;
  font-size: 8px;
}

.tv2-chain-body {
  display: grid;
  align-content: start;
}

.tv2-chain-body > header > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #55dfa0;
  font-size: 8px;
}

.tv2-chain-body > header > span.is-risk {
  color: #ff968b;
}

.tv2-node-form {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr) auto;
  gap: 7px;
  padding: 11px 12px 0;
}

.tv2-node-form select,
.tv2-node-form input,
.tv2-node-form button {
  min-height: 34px;
  padding: 0 9px;
  color: #e4fff2;
  background: rgb(1 12 7 / 72%);
  border: 1px solid rgb(91 225 163 / 14%);
  border-radius: 6px;
  font-size: 8px;
}

.tv2-node-form button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #062116;
  background: #55dfa0;
  border-color: #55dfa0;
}

.tv2-block-track {
  display: grid;
  grid-template-columns: repeat(5, minmax(150px, 1fr));
  gap: 8px;
  padding: 12px;
  overflow-x: auto;
}

.tv2-block-track article {
  position: relative;
  display: grid;
  gap: 5px;
  min-height: 154px;
  padding: 11px;
  background: rgb(255 255 255 / 2.5%);
  border: 1px solid rgb(91 225 163 / 9%);
  border-radius: 7px;
}

.tv2-block-track article:not(:last-child)::after {
  content: "";
  position: absolute;
  top: 50%;
  right: -9px;
  width: 9px;
  height: 1px;
  background: #4fe0a0;
}

.tv2-block-track article.is-broken {
  border-color: rgb(255 129 117 / 28%);
}

.tv2-block-track article > span {
  color: #4fe0a0;
  font-family: Consolas, monospace;
  font-size: 7px;
  letter-spacing: 0.1em;
}

.tv2-block-track article strong {
  font-size: 11px;
}

.tv2-block-track code {
  overflow: hidden;
  color: #67e3aa;
  font-family: Consolas, monospace;
  font-size: 7px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tv2-block-track small,
.tv2-block-track time {
  color: #698579;
  font-size: 7px;
}

.tv2-operations {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.tv2-operations > form {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 14px;
}

.tv2-operations form > header {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 4px;
}

.tv2-operations header > svg {
  color: #4fe0a0;
}

.tv2-operations header strong,
.tv2-operations header small {
  display: block;
}

.tv2-operations header strong {
  font-size: 12px;
}

.tv2-operations header small {
  margin-top: 3px;
  color: #698579;
  font-size: 8px;
}

.tv2-operations input,
.tv2-operations select,
.tv2-operations form > button,
.tv2-operations form > div button {
  min-height: 35px;
  padding: 0 9px;
  color: #e4fff2;
  background: rgb(1 12 7 / 72%);
  border: 1px solid rgb(91 225 163 / 14%);
  border-radius: 6px;
  font-size: 8px;
}

.tv2-operations form > button,
.tv2-operations form > div button:first-child {
  color: #062116;
  background: #55dfa0;
  border-color: #55dfa0;
}

.tv2-operations form > div {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
}

.tv2-operations p {
  margin: 0;
  font-size: 8px;
}

.tv2-operations .is-success {
  color: #7edfb0;
}

.tv2-operations .is-error {
  color: #ff968b;
}

.trace-v2.is-query .tv2-workbench {
  grid-template-columns: minmax(350px, 0.8fr) minmax(0, 1.2fr);
}

.trace-v2.is-query .tv2-evidence {
  min-height: 620px;
}

.trace-v2.is-evidence .tv2-workbench {
  grid-template-columns: 1fr;
}

.trace-v2.is-evidence .tv2-evidence {
  min-height: 420px;
}

.trace-v2.is-report .tv2-lab {
  grid-column: 1 / -1;
  width: min(980px, 100%);
  margin: 0 auto;
}

.trace-v2.is-report .tv2-report-form {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.trace-v2.is-management .tv2-operations {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (max-width: 1180px) {
  .tv2-workbench {
    grid-template-columns: minmax(300px, 0.8fr) minmax(0, 1.2fr);
  }

  .tv2-lab {
    grid-column: 1 / -1;
  }
}

@media (max-width: 820px) {
  .tv2-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .tv2-chain-stats {
    width: 100%;
    flex-wrap: wrap;
  }

  .tv2-chain-stats article {
    flex: 1;
  }

  .tv2-workbench,
  .tv2-operations {
    grid-template-columns: 1fr;
  }

  .tv2-node-form {
    grid-template-columns: 1fr;
  }
}

/* Quality evidence workbench redesign */
.quality-page {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  grid-auto-flow: dense;
  gap: 14px;
}

.quality-page > .page-heading,
.quality-page > .trace-chain-strip,
.quality-page > .trace-ledger-panel,
.quality-page > .quality-management-panel,
.quality-page > .trace-stage-ribbon {
  grid-column: 1 / -1;
}

.quality-page__eyebrow {
  color: #50dfa0;
  font-family: Consolas, monospace;
  font-size: 8px;
  letter-spacing: 0.14em;
}

.trace-stage-ribbon {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 8px;
  background: rgb(8 24 17 / 78%);
  border: 1px solid rgb(98 226 167 / 13%);
  border-radius: 8px;
}

.trace-stage-ribbon span {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 42px;
  padding: 0 11px;
  color: #9bb9aa;
  background: rgb(255 255 255 / 2.5%);
  border: 1px solid rgb(102 221 166 / 10%);
  border-radius: 7px;
}

.trace-stage-ribbon span:not(:last-child)::after {
  content: "";
  position: absolute;
  top: 50%;
  right: -9px;
  width: 9px;
  height: 1px;
  background: #4fe0a0;
  box-shadow: 0 0 9px rgb(79 224 160 / 44%);
}

.trace-stage-ribbon i {
  color: #4fe0a0;
  font-family: Consolas, monospace;
  font-size: 9px;
  font-style: normal;
}

.trace-stage-ribbon strong {
  font-size: 10px;
}

.quality-workspace {
  display: contents;
}

.trace-query-panel {
  position: sticky;
  top: 82px;
  grid-column: span 4;
  grid-row: span 2;
  align-self: start;
}

.report-panel {
  grid-column: span 8;
}

.trace-ledger-panel {
  grid-column: span 8;
}

.quality-management-panel {
  grid-column: 1 / -1;
}

.report-panel .report-form {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.quality-management-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (max-width: 1100px) {
  .trace-query-panel {
    position: static;
    grid-column: span 4;
    grid-row: auto;
  }

  .report-panel,
  .trace-ledger-panel {
    grid-column: span 8;
  }
}

@media (max-width: 820px) {
  .quality-page {
    grid-template-columns: 1fr;
  }

  .trace-stage-ribbon {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trace-query-panel,
  .report-panel,
  .trace-ledger-panel,
  .quality-management-panel {
    grid-column: 1 / -1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trace-chain-node i {
    animation: none;
  }
}

/* 一物一码二维码预览：白面卡 + 发丝边界，与工作台其它卡一致 */
.tv2-operations form > button.tv2-qr-preview-trigger {
  color: var(--ink-700, #3d544b) !important;
  background: rgb(255 255 255 / 92%) !important;
  border: 1px solid var(--glass-hairline, rgb(18 36 30 / 7%)) !important;
  box-shadow: none !important;
  font-weight: 500 !important;
}

.tv2-operations form > button.tv2-qr-preview-trigger:hover {
  color: var(--mint-800, #084f45) !important;
  border-color: rgb(14 124 107 / 26%) !important;
}

.tv2-qr-card {
  display: grid;
  gap: 10px;
  margin-top: 12px;
  padding: 12px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid var(--glass-hairline, rgb(18 36 30 / 7%));
  border-radius: 16px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 92%);
}

.tv2-qr-card > header {
  display: flex;
  align-items: flex-start;
  gap: 9px;
}

.tv2-qr-card > header > span {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  color: var(--mint-700, #0a6558);
  background: var(--mint-100, #e2f0ec);
  border-radius: 9px;
}

.tv2-qr-card > header strong {
  display: block;
  color: var(--ink-900, #12241e);
  font-size: 13px;
}

.tv2-qr-card > header small {
  display: block;
  margin-top: 3px;
  color: var(--ink-500, #6b837a);
  font-size: 11px;
  line-height: 1.5;
}

.tv2-qr-card__hint {
  margin: 0;
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
}

.tv2-qr-card__hint.is-error {
  color: var(--color-danger-strong, #b23b37);
}

.tv2-qr-card__body {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: 14px;
}

.tv2-qr-card__code {
  display: grid;
  place-items: center;
  width: 164px;
  height: 164px;
  padding: 6px;
  background: #ffffff;
  border: 1px solid rgb(18 36 30 / 8%);
  border-radius: 12px;
}

/* 只约束二维码本身，否则按钮里的图标会被一起放大 */
.tv2-qr-card__code :deep(svg) {
  display: block;
  width: 152px;
  height: 152px;
}

.tv2-qr-card__actions :deep(svg) {
  width: 14px;
  height: 14px;
}

.tv2-qr-card__meta {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.tv2-qr-card__meta > span {
  color: var(--ink-500, #6b837a);
  font-size: 11px;
}

.tv2-qr-card__meta code {
  display: block;
  padding: 8px 10px;
  color: var(--mint-800, #084f45);
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 8%);
  border-radius: 10px;
  font-family: Consolas, "Courier New", monospace;
  font-size: 11.5px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.tv2-qr-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 2px;
}

.tv2-qr-card__actions button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 11px;
  color: var(--ink-700, #3d544b);
  background: rgb(255 255 255 / 96%);
  border: 1px solid var(--glass-hairline, rgb(18 36 30 / 7%));
  border-radius: 11px;
  font-size: 12px;
}

.tv2-qr-card__actions button :deep(svg) {
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
}

.tv2-qr-card__actions button:hover {
  color: var(--mint-800, #084f45);
  border-color: rgb(14 124 107 / 26%);
}

@media (max-width: 900px) {
  .tv2-qr-card__body {
    grid-template-columns: 1fr;
  }
}
</style>
