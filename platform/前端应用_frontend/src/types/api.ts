export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  requestId?: string
}

export interface AuthUser {
  userId: number
  username: string
  displayName: string
  roleCode: string
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

export interface EnvironmentSnapshot {
  greenhouseId: string
  metrics: Record<string, number>
  collectedAt: string
  source: string
  rootHash?: string
  chainStatus?: string
}

export interface EnvironmentTrend {
  greenhouseCode: string
  points: Array<{
    cycleId: number
    cycleStart: string
    cycleEnd: string
    metrics: Record<string, number>
    rootHash?: string
    chainStatus?: string
  }>
}

export interface TelemetryHistory {
  greenhouseCode: string
  minutes: number
  bucketSeconds: number
  samples: Array<{
    at: string
    metrics: Record<string, number>
  }>
}

export interface EnvironmentCycleResult {
  cycleId: number
  greenhouseCode: string
  cycleStart: string
  cycleEnd: string
  dataDigest: string
  rootHash: string
  chainStatus: string
  chainTxHash?: string | null
  readingCount: number
}

export interface EnvironmentThreshold {
  thresholdId: number
  metricCode: string
  targetMin: number
  targetMax: number
  alertMin: number
  alertMax: number
  controlAction: string
  enabled: boolean
  updatedAt: string
}

export interface UpdateEnvironmentThresholdRequest {
  greenhouseCode: string
  targetMin: number
  targetMax: number
  alertMin: number
  alertMax: number
  controlAction: string
  enabled: boolean
}

export interface ControlCommandRequest {
  greenhouseCode: string
  deviceCode: string
  commandType: string
  parameters: Record<string, unknown>
  operator: string
  timeoutSeconds: number
}

export interface ControlCommandResult {
  commandNo: string
  greenhouseCode: string
  deviceCode: string
  commandType: string
  parameters: Record<string, unknown>
  operator: string
  timeoutSeconds: number
  status: string
  failureReason?: string
  createdAt: string
  dispatchedAt?: string
  acknowledgedAt?: string
  completedAt?: string
  retryCount?: number
}

export interface ControlCommandAckRequest {
  success: boolean
  message?: string
  executedAt?: string
}

export interface TraceResult {
  productCode: string
  found: boolean
  verificationStatus: string
  batchId?: number
  certificateNo?: string
  certificateStatus?: string
  fieldNo?: string
  publicKeyId?: string
  rootHash?: string
  environment?: TraceEnvironment | null
  pointerChainValid: boolean
  stages: TraceStage[]
  message: string
}

export interface TraceEnvironment {
  cycleId: number
  cycleStart: string
  cycleEnd: string
  chainStatus: string
  dataDigest?: string | null
  collectedAt?: string | null
  metrics: TraceEnvironmentMetric[]
}

export interface TraceEnvironmentMetric {
  metricCode: string
  value: number
  unit?: string | null
  collectedAt: string
}

export interface TraceStage {
  nodeId: number
  stageType: string
  previousHash: string
  payloadHash: string
  chainTxHash?: string | null
  createdAt: string
}

export interface TraceNodeResult {
  fieldNo: string
  nodeId: number
  stageType: string
  previousHash: string
  payloadHash: string
  chainTxHash?: string | null
  pointerChainValid: boolean
  nodeCount: number
  message: string
}

export interface ScanResult {
  productCode: string
  result: string
  firstScan: boolean
  scanCount: number
  channel: string
  scannedAt: string
  chainTxHash?: string
}

export interface TracePath {
  fieldNo: string
  productCode: string
  pointerChainValid: boolean
  stages: TraceStage[]
}

export interface ChainProof {
  batchId: number
  cropName: string
  greenhouseCode: string
  cycleId?: number
  rootHash?: string
  chainTxHash?: string
  chainStatus: string
  certificateNo?: string
  certificateHash?: string
  certificateStatus?: string
  certificateChainTxHash?: string
  productCode?: string
  traceStatus?: string
  traceChainTxHash?: string
  verificationStatus: string
  message: string
}

export interface QualityReport {
  reportId: number
  batchId: number
  institutionId: string
  institutionName: string
  reportNo: string
  resultSummary: string
  fileUrl: string
  contentHash: string
  testedAt: string
  status: string
}

export interface QualityReportUploadRequest {
  batchId: number
  institutionId: string
  institutionName: string
  reportNo: string
  resultSummary: string
  testedAt: string
  file: File
}

export interface CertificateRecord {
  certificateId: number
  batchId: number
  certificateNo: string
  contentHash: string
  chainTxHash?: string
  status: string
  issuedAt?: string
  revokedAt?: string
}

export interface CertificateVerifyRequest {
  requestNo: string
  certificateNo: string
  batchId: number
  institutionId: string
  contentHash: string
  issuedAt: string
  publicKeyId: string
  publicKey: string
  signature: string
}

export interface CertificateVerifyResult {
  requestNo: string
  certificateNo: string
  verified: boolean
  canonicalDigest: string
  message: string
  verifiedAt: string
}

export interface CreateCertificateRequest {
  batchId: number
  certificateNo: string
  contentHash: string
  status: string
}

export interface TraceCodeRecord {
  productCode: string
  fieldNo: string
  batchId: number
  publicKeyId: string
  status: string
  chainTxHash?: string
}

export interface CreateTraceCodeRequest {
  batchId: number
  fieldNo: string
  productCode: string
  publicKeyId: string
  signatureValue: string
}

/**
 * 一物一码二维码载荷：payload 是写进二维码的公开校验地址，
 * pngUrl / svgUrl 指向后端生成的下载地址。
 */
export interface TraceQrPayload {
  productCode: string
  payload: string
  mode: string
  size: number
  pngUrl: string
  svgUrl: string
}

export interface CropBatch {
  batchId: number
  cropName: string
  varietyName: string
  plantingDate: string
  expectedHarvestDate: string
  status: string
  greenhouseCode: string
  greenhouseName: string
}

export interface WaterFertilizerPlan {
  planId: number
  planNo: string
  greenhouseCode: string
  batchId: number
  cropName: string
  recipeCode: string
  recipeVersion: string
  ecTarget: number
  phTarget: number
  zoneName: string
  plannedAt: string
  waterVolume: number
  status: string
  operatorName?: string
  executedAt?: string
  failureReason?: string
}

export interface CreateWaterFertilizerPlanRequest {
  greenhouseCode: string
  batchId: number
  recipeCode: string
  recipeVersion: string
  ecTarget: number
  phTarget: number
  zoneName: string
  plannedAt: string
  waterVolume: number
  operatorName: string
}

export interface GovernmentDashboard {
  greenhouseCount: number
  deviceOnlineRate: number
  activeAlerts: number
  traceabilityCoverage: number
  offlineDeviceCount: number
  pendingChainProofCount: number
  repeatScanCount: number
  generatedAt: string
}

export interface AlertRecord {
  alertId: number
  greenhouseCode: string
  metricCode: string
  metricValue: number
  alertLevel: string
  alertMin: number
  alertMax: number
  message: string
  status: string
  createdAt: string
  acknowledgedAt?: string
  acknowledgedBy?: string
}

export interface VillageSummary {
  villageId: number
  villageName: string
  // 后端 LEFT JOIN 分红记录：刚新增的入股记录还没有分红，字段可能为空
  landArea: number | null
  shareId: number
  projectName: string
  shareRatio: number
  effectiveDate: string
  dividendAmount: number | null
  status: string | null
}

export interface CreateShareRequest {
  villageId?: number
  villageName?: string
  projectName: string
  shareRatio: number
  effectiveDate: string
  status: string
}

export interface CooperationShare {
  shareId: number
  villageId: number
  projectName: string
  shareRatio: number
  effectiveDate: string
  status: string
}

export interface CreateDividendRequest {
  shareId: number
  periodName: string
  amount: number
  status: string
}

export interface Dividend {
  dividendId: number
  shareId: number
  periodName: string
  amount: number
  status: string
  confirmedAt?: string
}

export interface GreenhouseSummary {
  greenhouseId: number
  greenhouseCode: string
  greenhouseName: string
  area: number
  location: string
  status: string
  batchId?: number | null
  cropName?: string | null
  varietyName?: string | null
  plantingDate?: string | null
  expectedHarvestDate?: string | null
}

export interface CreateGreenhouseRequest {
  code: string
  name: string
  area: number
  location: string
  cropName: string
  varietyName: string
  plantingDate: string
  expectedHarvestDate: string
}

export interface UpdateGreenhouseCropRequest {
  cropName: string
  varietyName: string
  plantingDate: string
  expectedHarvestDate: string
}

export interface ChainAdapterStatus {
  mode: string
  reachable: boolean
  status: string
  contractAddress: string | null
  message: string
}

export interface ChainPendingCounts {
  environmentCycle: number
  certificate: number
  traceCode: number
  traceNode: number
  scanRecord: number
  total: number
}

/** 链上运行指标：适配服务未提供区块查询接口时 available=false */
export interface ChainNetworkStatus {
  available: boolean
  blockNumber: number | null
  blockTimestamp: string | null
  avgBlockIntervalMs: number | null
  sampleSize: number | null
  message: string
}

export interface ChainStatus {
  adapter: ChainAdapterStatus
  pending: ChainPendingCounts
  /** 全平台存证记录总数（五类存证表行数之和） */
  proofCount: number
  network: ChainNetworkStatus
}

export interface ChainCompensationEntry {
  type: string
  businessId: string
  transactionHash: string
  blockNumber: number
}

export interface ChainCompensationReport {
  attempted: number
  confirmed: number
  failed: number
  entries: ChainCompensationEntry[]
  failures: string[]
}

export interface ExternalPlatformStatus {
  platform: string
  label: string
  mode: string
  lastStatus: string
  lastMessage: string
  lastAt?: string | null
  successCount: number
  failureCount: number
}

export interface ExternalSyncReport {
  platform: string
  label: string
  succeeded: number
  failed: number
  entries: Array<{
    requestNo: string
    status: string
    message: string
    attempts: number
  }>
}

export interface ExternalLog {
  logId: number
  platform: string
  label: string
  requestNo: string
  status: string
  attempts: number
  durationMs: number
  mode: string
  message: string
  createdAt: string
}

export interface GreenhouseOverview {
  greenhouseCode: string
  greenhouseName: string
  area: number
  greenhouseStatus: string
  cycleId?: number
  rootHash?: string
  chainStatus?: string
  metrics: Record<string, number>
  crop?: {
    batchId: number
    cropName: string
    varietyName: string
    plantingDate: string
    expectedHarvestDate: string
    status: string
  }
  devices: Array<{
    deviceId: number
    deviceCode: string
    deviceType: string
    onlineStatus: string
    operatingState: string
    lastSeenAt?: string
  }>
}

/** AI 结果来源：deepseek=真实大模型输出，local=未配置密钥或调用失败时的本地兜底 */
export type AiSource = 'deepseek' | 'local'

export interface AiStatus {
  provider: string
  model: string
  configured: boolean
  modelAvailable: boolean
  mode: AiSource
}

export interface AiSummaryResult {
  content: string
  source: AiSource
  provider: string
  model: string
  frameworkId: string
  framework: string
  scopeTitle: string
  latencyMs: number
  notice: string | null
  contextSections: string[]
  highlights: string[]
  generatedAt: string
}

export interface AiChatMessage {
  messageId: number | null
  role: 'user' | 'assistant'
  content: string
  source: AiSource
  model: string | null
  latencyMs: number | null
  createdAt: string
  /** 仅前端使用：这条消息还在等后端回答，拿到结果后会被替换掉 */
  pending?: boolean
}

export interface AiChatReply {
  userMessage: AiChatMessage
  assistantMessage: AiChatMessage
  source: AiSource
  notice: string | null
}

