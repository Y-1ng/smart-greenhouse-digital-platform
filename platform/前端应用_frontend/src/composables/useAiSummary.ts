import { computed, ref, watch } from 'vue'
import { describeApiError } from '@/api/errors'
import { fetchAiStatus, generateAiSummary } from '@/api/modules/ai'
import type { AiSource, AiStatus } from '@/types/api'

export type AiFrameworkId = 'production' | 'quality' | 'operation' | 'governance'

export interface AiFramework {
  id: AiFrameworkId
  label: string
  /** 一句话说明这个框架看什么 */
  description: string
  /** 分析时的侧重点，用于"分析框架"页展示 */
  focus: string
  /** 该框架输出的结论维度 */
  outputs: string[]
}

export interface AiSummaryRecord {
  title: string
  summary: string
  time: string
  /** 这条结论来自大模型还是本地兜底 */
  source: AiSource
}

/**
 * AI 总结的框架定义。真实的结论文案由后端调用 DeepSeek 生成，
 * 这里的 outputs 只在后端不可用时作为本地兜底。
 */
export const aiFrameworks: AiFramework[] = [
  {
    id: 'production',
    label: '农业生产模型',
    description: '按环境、作物、设备和水肥四个层级归纳生产偏差与处置建议。',
    focus: '环境偏离、设备回执、水肥执行',
    outputs: [
      '当前生产链路的结论文本需要后端 AI 接口返回，本地兜底只给出框架要点。',
      '建议优先检查偏离目标区间最大的环境指标，并核对对应执行设备回执。',
      '下一阶段可将高频指标置于首屏，把低频档案放入详情抽屉，降低重复切换成本。',
    ],
  },
  {
    id: 'quality',
    label: '质量链审计',
    description: '按采样、周期、证书、扫码和链上回执检查证据完整性。',
    focus: '证据完整性、证书有效性、断链与重复扫码',
    outputs: [
      '质量证据链的完整结论需要后端 AI 接口返回，本地兜底只给出框架要点。',
      '建议重点核查待补偿或待确认记录，确保每个产品编码都能回连到有效证书。',
      '对断链、重复编码和证书过期三类异常应设置独立筛选入口，便于答辩时快速展示闭环。',
    ],
  },
  {
    id: 'operation',
    label: '经营运营模型',
    description: '聚合产量、成本、合同、股权和分红，输出经营关注点。',
    focus: '履约率、收益波动、分红兑现',
    outputs: [
      '经营结论需要后端 AI 接口返回，本地兜底只给出框架要点。',
      '建议将收益波动最大的村庄和主体置于高优先级，联动查看履约率与分红兑现。',
      '可继续沉淀“投入、产出、分配”三段式指标，为产业分析提供统一口径。',
    ],
  },
  {
    id: 'governance',
    label: '风险治理模型',
    description: '围绕监管指标、预警闭环、产业带动和外部同步形成治理结论。',
    focus: '预警闭环、指标口径、外部同步',
    outputs: [
      '治理结论需要后端 AI 接口返回，本地兜底只给出框架要点。',
      '建议优先处理未闭环预警和同步失败记录，并对跨村指标采用同一时间窗口比较。',
      '治理结论应同时保留原始明细和摘要依据，确保每项判断都能追溯到业务记录。',
    ],
  },
]

/** 保留的历史条数 */
const HISTORY_LIMIT = 6
/** 会话级存储：刷新页面不丢本次会话结论 */
const STORAGE_KEY = 'smart-greenhouse-ai-summary'

const FRAMEWORK_IDS: AiFrameworkId[] = [
  'production',
  'quality',
  'operation',
  'governance',
]

interface PersistedSummaryState {
  framework: AiFrameworkId
  instruction: string
  output: string
  history: AiSummaryRecord[]
  source: AiSource
  notice: string
  model: string
  latencyMs: number
  contextSections: string[]
  highlights: string[]
}

function readPersisted(): Partial<PersistedSummaryState> {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as Partial<PersistedSummaryState>
    return FRAMEWORK_IDS.includes(parsed.framework as AiFrameworkId)
      ? parsed
      : { ...parsed, framework: undefined }
  } catch {
    window.sessionStorage.removeItem(STORAGE_KEY)
    return {}
  }
}

const persisted = readPersisted()

/**
 * 状态定义在模块作用域，抽屉和独立页面共用同一份会话状态：
 * 在任意模块的抽屉里生成过总结，切到 AI 总结页仍然看得到同一份结果和历史。
 */
const selectedFramework = ref<AiFrameworkId>(persisted.framework ?? 'production')
const instruction = ref(persisted.instruction ?? '')
const generating = ref(false)
const output = ref(persisted.output ?? '')
const history = ref<AiSummaryRecord[]>(persisted.history ?? [])
const source = ref<AiSource>(persisted.source ?? 'local')
const notice = ref(persisted.notice ?? '')
const modelLabel = ref(persisted.model ?? '')
const latencyMs = ref(persisted.latencyMs ?? 0)
const contextSections = ref<string[]>(persisted.contextSections ?? [])
const highlights = ref<string[]>(persisted.highlights ?? [])
const aiStatus = ref<AiStatus | null>(null)

watch(
  [
    selectedFramework,
    instruction,
    output,
    history,
    source,
    notice,
    modelLabel,
    latencyMs,
    contextSections,
    highlights,
  ],
  () => {
    const snapshot: PersistedSummaryState = {
      framework: selectedFramework.value,
      instruction: instruction.value,
      output: output.value,
      history: history.value,
      source: source.value,
      notice: notice.value,
      model: modelLabel.value,
      latencyMs: latencyMs.value,
      contextSections: contextSections.value,
      highlights: highlights.value,
    }
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot))
  },
  { deep: true },
)

const activeFramework = computed(
  () =>
    aiFrameworks.find((item) => item.id === selectedFramework.value)
    ?? aiFrameworks[0],
)

const historyCount = computed(() => history.value.length)

const sourceLabel = computed(() => {
  if (source.value === 'deepseek') {
    const provider = aiStatus.value?.provider ?? 'DeepSeek'
    const model = modelLabel.value || aiStatus.value?.model || ''
    return model ? `${provider} · ${model}` : provider
  }
  return '本地兜底结论'
})

/** 模型返回的分点行去掉序号和符号，交给界面自己编号 */
function normalizeLines(content: string) {
  return content
    .split('\n')
    .map((line) => line.trim().replace(/^([-*•]|\d+\s*[.、)]|#+)\s*/, '').trim())
    .filter((line) => line.length > 0)
    .join('\n')
}

/** 后端 AI 接口也挂了时的最后兜底：只给框架要点，不编造数据 */
function buildLocalSummary(scopeTitle: string) {
  const framework = activeFramework.value
  const focus = instruction.value.trim()
  const lines = [
    `分析对象：${scopeTitle}`,
    `分析框架：${framework.label}。${framework.description}`,
    ...framework.outputs,
    focus
      ? `本次额外遵循指令：“${focus}”。`
      : '本次默认聚焦异常项、趋势变化和可执行动作。',
  ]
  return lines
    .map((line, index) => `${index + 1}. ${line}`)
    .join('\n')
}

function clockNow() {
  return new Date().toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

/** 读取后端 AI 状态：是否配置了真实密钥、当前是不是真实大模型 */
async function refreshStatus() {
  try {
    aiStatus.value = await fetchAiStatus()
  } catch {
    aiStatus.value = null
  }
}

async function generate(scopeTitle = '') {
  if (generating.value) {
    return
  }
  generating.value = true
  output.value = ''
  notice.value = ''

  const framework = activeFramework.value
  const title = scopeTitle.trim() || framework.label
  const startedAt = Date.now()

  try {
    const result = await generateAiSummary({
      scopeTitle: title,
      framework: framework.id,
      instruction: instruction.value.trim() || undefined,
    })
    output.value = normalizeLines(result.content)
    source.value = result.source
    notice.value = result.notice ?? ''
    modelLabel.value = result.model ?? ''
    latencyMs.value = result.latencyMs || Date.now() - startedAt
    contextSections.value = result.contextSections ?? []
    highlights.value = result.highlights ?? []
  } catch (error) {
    output.value = buildLocalSummary(title)
    source.value = 'local'
    modelLabel.value = ''
    latencyMs.value = Date.now() - startedAt
    notice.value = `AI 接口调用失败：${describeApiError(error)}；已改为本地框架结论。`
  }

  const lines = output.value.split('\n').filter(Boolean)
  history.value = [
    {
      title: `${title} · ${framework.label}`,
      summary: lines[2] ?? lines[1] ?? lines[0] ?? '',
      time: clockNow(),
      source: source.value,
    },
    ...history.value,
  ].slice(0, HISTORY_LIMIT)
  generating.value = false
}

function runQuickPrompt(text: string, scopeTitle = '') {
  instruction.value = text
  void generate(scopeTitle)
}

function selectFramework(id: AiFrameworkId) {
  selectedFramework.value = id
}

export function useAiSummary() {
  return {
    frameworks: aiFrameworks,
    selectedFramework,
    activeFramework,
    instruction,
    generating,
    output,
    history,
    historyCount,
    source,
    sourceLabel,
    notice,
    modelLabel,
    latencyMs,
    contextSections,
    highlights,
    aiStatus,
    refreshStatus,
    generate,
    runQuickPrompt,
    selectFramework,
  }
}
