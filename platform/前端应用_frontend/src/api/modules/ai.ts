import http from '@/api/http'
import type {
  AiChatMessage,
  AiChatReply,
  AiStatus,
  AiSummaryResult,
  ApiResponse,
} from '@/types/api'

/**
 * 大模型生成比普通查询慢，单独放宽超时（后端读取超时是 60 秒）。
 */
const AI_TIMEOUT_MS = 90_000

export async function fetchAiStatus() {
  const response = await http.get<ApiResponse<AiStatus>>('/ai/status', {
    timeout: 10_000,
  })
  return response.data.data
}

export interface AiSummaryPayload {
  scopeTitle?: string
  framework: string
  instruction?: string
}

/** AI 总结：后端组装真实数据上下文后调用 DeepSeek */
export async function generateAiSummary(payload: AiSummaryPayload) {
  const response = await http.post<ApiResponse<AiSummaryResult>>(
    '/ai/summary',
    payload,
    { timeout: AI_TIMEOUT_MS },
  )
  return response.data.data
}

/** AI 问答：按会话读取历史 */
export async function loadAiChatHistory(sessionId: string) {
  const response = await http.get<ApiResponse<AiChatMessage[]>>(
    '/ai/chat/history',
    {
      params: { sessionId },
      timeout: 15_000,
    },
  )
  return response.data.data
}

export interface AiChatPayload {
  sessionId: string
  question: string
  operator?: string
}

/** AI 问答：提问并返回这一问一答 */
export async function sendAiChatMessage(payload: AiChatPayload) {
  const response = await http.post<ApiResponse<AiChatReply>>(
    '/ai/chat',
    payload,
    { timeout: AI_TIMEOUT_MS },
  )
  return response.data.data
}

/** AI 问答：清空当前会话 */
export async function clearAiChatHistory(sessionId: string) {
  const response = await http.delete<ApiResponse<number>>('/ai/chat/history', {
    params: { sessionId },
    timeout: 15_000,
  })
  return response.data.data
}
