import { computed, ref } from 'vue'
import {
  clearAiChatHistory,
  loadAiChatHistory,
  sendAiChatMessage,
} from '@/api/modules/ai'
import { describeApiError } from '@/api/errors'
import { useSessionStore } from '@/stores/session'
import type { AiChatMessage, AiSource } from '@/types/api'

/**
 * 会话号存在 sessionStorage：同一个标签页刷新后还是同一段对话，
 * 关掉标签页就是新会话，不会把演示环境的历史越堆越多。
 */
const STORAGE_KEY = 'smart-greenhouse-ai-chat-session'

function resolveSessionId() {
  const existing = window.sessionStorage.getItem(STORAGE_KEY)
  if (existing) {
    return existing
  }
  const created = `web-${Date.now().toString(36)}-${Math.random()
    .toString(36)
    .slice(2, 10)}`
  window.sessionStorage.setItem(STORAGE_KEY, created)
  return created
}

/** 模块级单例：抽屉与 AI 问答页共用同一段对话 */
const sessionId = resolveSessionId()
const messages = ref<AiChatMessage[]>([])
const sending = ref(false)
const loading = ref(false)
const notice = ref('')
const lastSource = ref<AiSource>('local')
const lastLatencyMs = ref(0)
const loaded = ref(false)

const hasConversation = computed(() => messages.value.length > 0)
const messageCount = computed(() => messages.value.length)

async function loadHistory() {
  loading.value = true
  notice.value = ''
  try {
    messages.value = await loadAiChatHistory(sessionId)
  } catch (error) {
    notice.value = `加载问答历史失败：${describeApiError(error)}`
  } finally {
    loading.value = false
    loaded.value = true
  }
}

/**
 * 提问。返回 true 表示这一轮拿到了回答（含本地兜底回答）。
 * 失败时把乐观插入的用户消息撤回，避免界面留下一条没有回答的提问。
 */
async function send(question: string) {
  const text = question.trim()
  if (!text || sending.value) {
    return false
  }
  const session = useSessionStore()
  sending.value = true
  notice.value = ''

  const optimistic: AiChatMessage = {
    messageId: null,
    role: 'user',
    content: text,
    source: 'local',
    model: null,
    latencyMs: null,
    createdAt: new Date().toISOString(),
    pending: true,
  }
  messages.value = [...messages.value, optimistic]

  try {
    const reply = await sendAiChatMessage({
      sessionId,
      question: text,
      operator: session.displayName,
    })
    messages.value = [
      ...messages.value.filter((item) => !item.pending),
      reply.userMessage,
      reply.assistantMessage,
    ]
    lastSource.value = reply.source
    lastLatencyMs.value = reply.assistantMessage.latencyMs ?? 0
    notice.value = reply.notice ?? ''
    return true
  } catch (error) {
    // 失败时撤回这条待回答的提问，避免界面留下没有回答的提问
    messages.value = messages.value.filter((item) => !item.pending)
    notice.value = `提问失败：${describeApiError(error)}`
    return false
  } finally {
    sending.value = false
  }
}

async function clear() {
  if (sending.value) {
    return false
  }
  try {
    await clearAiChatHistory(sessionId)
    messages.value = []
    notice.value = ''
    lastSource.value = 'local'
    lastLatencyMs.value = 0
    return true
  } catch (error) {
    notice.value = `清空失败：${describeApiError(error)}`
    return false
  }
}

export function useAiChat() {
  return {
    sessionId,
    messages,
    messageCount,
    hasConversation,
    sending,
    loading,
    loaded,
    notice,
    lastSource,
    lastLatencyMs,
    loadHistory,
    send,
    clear,
  }
}
