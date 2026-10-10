<!--
  AI 总结独立页面：与生产指挥/质量溯源同级的一级模块。
  生成逻辑来自 composables/useAiSummary.ts，与旧抽屉共用同一份实现。
-->
<script setup lang="ts">
import {
  ArrowUp,
  Bot,
  Blocks,
  CircleAlert,
  History,
  Send,
  Sparkles,
  Target,
  Trash2,
  UserRound,
} from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAiSummary } from '@/composables/useAiSummary'
import { useAiChat } from '@/composables/useAiChat'

const route = useRoute()
const pageSection = computed(() => String(route.meta.section ?? 'summary'))

const {
  frameworks,
  selectedFramework,
  activeFramework,
  instruction,
  generating,
  output,
  history,
  historyCount,
  generate,
  runQuickPrompt,
  selectFramework,
  source,
  sourceLabel,
  notice,
  latencyMs,
  contextSections,
  aiStatus,
  refreshStatus,
} = useAiSummary()

const {
  sessionId: chatSessionId,
  messages: chatMessages,
  sending: chatSending,
  loading: chatLoading,
  notice: chatNotice,
  loadHistory: loadChatHistory,
  send: sendChatMessage,
  clear: clearChat,
} = useAiChat()

const chatDraft = ref('')

const chatSuggestions = [
  '现在哪座温室的温度偏离最大？',
  '质量证据链里还有哪些待确认记录？',
  '今天的通风和水肥执行情况怎么样？',
  '给我三条今天可执行的处置动作',
]

const quickPrompts = [
  '只保留异常项和处置建议',
  '按风险等级从高到低排序',
  '补充可追溯的原始记录来源',
]

const outputLines = computed(() =>
  output.value ? output.value.split('\n').filter(Boolean) : [],
)

/** 独立页面面向全平台数据做归纳，抽屉里才按当前模块收口 */
const PAGE_SCOPE_TITLE = '全平台业务数据'

/** 当前 AI 通道状态，直接告诉使用者是真实大模型还是本地兜底 */
const aiModeLabel = computed(() => {
  const status = aiStatus.value
  if (!status) return 'AI 服务未连接'
  return status.mode === 'deepseek'
    ? `${status.provider} ${status.model} 已接入`
    : '未配置密钥，展示本地结论'
})

/** 顶栏已经给出分节标题，页内主标题跟着分节走，避免两处文案互相打架 */
const heroCopy = computed(() => {
  if (pageSection.value === 'frameworks') {
    return {
      eyebrow: 'ANALYSIS FRAMEWORKS',
      title: '分析框架',
      description: '四类分析角度与各自的输出维度，生成结论时按所选框架归纳。',
    }
  }
  if (pageSection.value === 'history') {
    return {
      eyebrow: 'GENERATION HISTORY',
      title: '生成历史',
      description: '本次会话内已生成的结论文本，刷新页面后依然保留。',
    }
  }
  if (pageSection.value === 'chat') {
    return {
      eyebrow: 'AI QUESTION & ANSWER',
      title: 'AI 问答',
      description: '基于平台实时数据提问，回答会标注由谁生成、依据哪几块数据。',
    }
  }
  return {
    eyebrow: 'AI SUMMARY WORKBENCH',
    title: 'AI 总结工作台',
    description: '按业务框架归纳指标、证据与风险，输出可追溯的结论文本。',
  }
})

function generateForActiveFramework() {
  void generate(PAGE_SCOPE_TITLE)
}

async function submitChat(text?: string) {
  const question = (text ?? chatDraft.value).trim()
  if (!question || chatSending.value) {
    return
  }
  chatDraft.value = ''
  await sendChatMessage(question)
}

function askSuggestion(text: string) {
  void submitChat(text)
}

function formatClock(value: string) {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return parsed.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

onMounted(() => {
  void refreshStatus()
  if (pageSection.value === 'chat') {
    void loadChatHistory()
  }
})
</script>

<template>
  <div class="production-v2 is-ai ai-page">
    <header class="pv2-hero">
      <div class="pv2-hero__identity">
        <span>{{ heroCopy.eyebrow }}</span>
        <h2>{{ heroCopy.title }}</h2>
        <p>{{ heroCopy.description }}</p>
      </div>
      <div class="pv2-hero__actions">
        <span class="ai-page__stat">
          <Blocks :size="14" />
          可用框架 {{ frameworks.length }}
        </span>
        <span class="ai-page__stat">
          <History :size="14" />
          本次已生成 {{ historyCount }}
        </span>
        <span class="ai-page__stat" :class="{ 'is-live': aiStatus?.mode === 'deepseek' }">
          <Bot :size="14" />
          {{ aiModeLabel }}
        </span>
      </div>
    </header>

    <!-- 生成总结 -->
    <section v-if="pageSection === 'summary'" class="ai-page__board">
      <div class="ai-page__panel">
        <header>
          <Target :size="15" />
          <div>
            <h3>分析框架</h3>
            <p>框架决定归纳的角度与输出维度</p>
          </div>
        </header>
        <div class="ai-page__frameworks">
          <button
            v-for="item in frameworks"
            :key="item.id"
            type="button"
            :class="{ 'is-active': selectedFramework === item.id }"
            @click="selectFramework(item.id)"
          >
            <Sparkles :size="14" />
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </button>
        </div>

        <label class="ai-page__field">
          <span>补充指令（可选）</span>
          <textarea
            v-model="instruction"
            rows="3"
            placeholder="例如：只保留异常项和处置建议"
          ></textarea>
        </label>

        <div class="ai-page__quick">
          <button
            v-for="text in quickPrompts"
            :key="text"
            type="button"
            @click="runQuickPrompt(text, PAGE_SCOPE_TITLE)"
          >
            {{ text }}
          </button>
        </div>

        <button
          class="ai-page__generate"
          type="button"
          :disabled="generating"
          @click="generateForActiveFramework"
        >
          <Send :size="15" />
          {{ generating ? '生成中…' : `按「${activeFramework.label}」生成总结` }}
        </button>
      </div>

      <div class="ai-page__panel">
        <header>
          <Sparkles :size="15" />
          <div>
            <h3>生成结果</h3>
            <p>{{ activeFramework.label }} · {{ activeFramework.focus }}</p>
          </div>
          <span
            v-if="outputLines.length"
            class="ai-page__source"
            :class="`is-${source}`"
          >
            {{ sourceLabel }}
            <template v-if="latencyMs > 0">
              · {{ (latencyMs / 1000).toFixed(1) }}s
            </template>
          </span>
        </header>

        <p v-if="notice" class="ai-page__notice">
          <CircleAlert :size="14" />
          {{ notice }}
        </p>

        <div v-if="contextSections.length" class="ai-page__context">
          <span>本轮数据范围</span>
          <i v-for="section in contextSections" :key="section">{{ section }}</i>
        </div>

        <div v-if="generating" class="ai-page__thinking">
          <i></i><i></i><i></i>
          <span>正在按框架归纳指标与证据…</span>
        </div>
        <ol v-else-if="outputLines.length" class="ai-page__result">
          <li v-for="(line, index) in outputLines" :key="index">
            {{ line.replace(/^\d+\.\s*/, '') }}
          </li>
        </ol>
        <div v-else class="ai-page__empty">
          <CircleAlert :size="18" />
          <strong>还没有生成结果</strong>
          <small>选择左侧框架后点击生成，结果会保留在历史记录里。</small>
        </div>

        <section v-if="history.length" class="ai-page__history">
          <h4>最近生成</h4>
          <ul>
            <li v-for="record in history" :key="`${record.title}-${record.time}`">
              <div>
                <strong>{{ record.title }}</strong>
                <small>{{ record.summary }}</small>
              </div>
              <time>{{ record.time }}</time>
            </li>
          </ul>
        </section>
      </div>
    </section>

    <!-- AI 问答 -->
    <section v-else-if="pageSection === 'chat'" class="ai-page__chat">
      <aside class="ai-page__chat-side">
        <header>
          <Bot :size="15" />
          <div>
            <h3>问答设置</h3>
            <p>每次回答都会带上平台实时数据</p>
          </div>
        </header>

        <dl class="ai-page__chat-meta">
          <div>
            <dt>会话号</dt>
            <dd>{{ chatSessionId }}</dd>
          </div>
          <div>
            <dt>当前模型</dt>
            <dd>{{ aiModeLabel }}</dd>
          </div>
          <div>
            <dt>已问条数</dt>
            <dd>{{ Math.ceil(chatMessages.length / 2) }}</dd>
          </div>
        </dl>

        <div class="ai-page__chat-suggest">
          <span>可以这样问</span>
          <button
            v-for="text in chatSuggestions"
            :key="text"
            type="button"
            :disabled="chatSending"
            @click="askSuggestion(text)"
          >
            {{ text }}
          </button>
        </div>

        <button
          class="ai-page__chat-clear"
          type="button"
          :disabled="chatSending || chatMessages.length === 0"
          @click="clearChat()"
        >
          <Trash2 :size="14" />
          清空对话
        </button>
      </aside>

      <div class="ai-page__chat-main">
        <header class="ai-page__chat-head">
          <div class="ai-page__chat-identity">
            <span><Bot :size="17" /></span>
            <div>
              <strong>温室 AI 助手</strong>
              <small>基于平台实时数据回答</small>
            </div>
          </div>
          <span
            class="ai-page__chat-model"
            :class="{ 'is-live': aiStatus?.mode === 'deepseek' }"
          >
            <i></i>
            {{ aiModeLabel }}
          </span>
        </header>

        <div class="ai-page__chat-thread">
          <div v-if="chatLoading" class="ai-page__thinking">
            <i></i><i></i><i></i>
            <span>正在读取历史对话…</span>
          </div>
          <div v-else-if="chatMessages.length === 0" class="ai-page__empty is-chat">
            <span class="ai-page__empty-mark"><Sparkles :size="23" /></span>
            <strong>有什么可以帮你？</strong>
            <small>
              可以结合生产、质量、合作和治理数据回答；结论只依据平台真实记录，并标明生成来源。
            </small>
            <div class="ai-page__empty-hints">
              <span>实时环境</span>
              <span>设备控制</span>
              <span>质量溯源</span>
            </div>
          </div>

          <article
            v-for="(message, index) in chatMessages"
            :key="`${message.role}-${index}-${message.createdAt}`"
            class="ai-page__bubble"
            :class="`is-${message.role}`"
          >
            <header>
              <component
                :is="message.role === 'user' ? UserRound : Bot"
                :size="14"
              />
              <strong>{{ message.role === 'user' ? '我' : 'AI 助手' }}</strong>
              <span
                v-if="message.role === 'assistant'"
                class="ai-page__source"
                :class="`is-${message.source}`"
              >
                {{ message.source === 'deepseek'
                  ? (message.model || 'DeepSeek')
                  : '本地兜底' }}
              </span>
              <time>{{ formatClock(message.createdAt) }}</time>
            </header>
            <p>{{ message.content }}</p>
          </article>

          <div v-if="chatSending" class="ai-page__thinking">
            <i></i><i></i><i></i>
            <span>正在结合平台数据回答…</span>
          </div>
        </div>

        <p v-if="chatNotice" class="ai-page__notice">
          <CircleAlert :size="14" />
          {{ chatNotice }}
        </p>

        <form class="ai-page__composer" @submit.prevent="submitChat()">
          <textarea
            v-model="chatDraft"
            rows="2"
            placeholder="输入问题，和温室 AI 助手聊聊…"
            @keydown.enter.exact.prevent="submitChat()"
          ></textarea>
          <button
            type="submit"
            :disabled="chatSending || !chatDraft.trim()"
            aria-label="发送问题"
            title="发送问题"
          >
            <ArrowUp :size="18" :stroke-width="2.2" />
          </button>
        </form>
      </div>
    </section>

    <!-- 分析框架 -->
    <section v-else-if="pageSection === 'frameworks'" class="ai-page__cards">
      <article v-for="item in frameworks" :key="item.id">
        <header>
          <span><Sparkles :size="15" /></span>
          <div>
            <h3>{{ item.label }}</h3>
            <p>{{ item.description }}</p>
          </div>
        </header>
        <dl>
          <div>
            <dt>关注重点</dt>
            <dd>{{ item.focus }}</dd>
          </div>
        </dl>
        <ul>
          <li v-for="line in item.outputs" :key="line">{{ line }}</li>
        </ul>
      </article>
    </section>

    <!-- 历史记录 -->
    <section v-else-if="pageSection === 'history'" class="ai-page__panel is-wide">
      <header>
        <History :size="15" />
        <div>
          <h3>生成历史</h3>
          <p>当前会话内最近 {{ historyCount }} 条总结</p>
        </div>
      </header>
      <ul v-if="history.length" class="ai-page__history-list">
        <li v-for="record in history" :key="`${record.title}-${record.time}`">
          <strong>{{ record.title }}</strong>
          <small>{{ record.summary }}</small>
          <time>{{ record.time }}</time>
        </li>
      </ul>
      <div v-else class="ai-page__empty">
        <CircleAlert :size="18" />
        <strong>还没有历史记录</strong>
        <small>到「生成总结」页选择框架并生成，结果会自动记录在这里。</small>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-page__stat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 12px;
  color: var(--ink-700, #3d544b);
  background: rgb(255 255 255 / 92%);
  border: 1px solid var(--glass-hairline, rgb(18 36 30 / 7%));
  border-radius: 999px;
  font-size: 12px;
}

.ai-page__board {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 14px;
  align-items: start;
}

.ai-page__panel {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid var(--glass-hairline, rgb(18 36 30 / 7%));
  border-radius: 18px;
  box-shadow:
    0 1px 2px rgb(9 62 54 / 4%),
    0 10px 28px rgb(9 62 54 / 7%);
}

.ai-page__panel > header {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  color: var(--mint-700, #0a6558);
}

.ai-page__panel > header h3 {
  margin: 0;
  color: var(--ink-900, #12241e);
  font-size: 15px;
}

.ai-page__panel > header p {
  margin: 3px 0 0;
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
}

.ai-page__frameworks {
  display: grid;
  gap: 8px;
}

.ai-page__frameworks button {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 4px 8px;
  padding: 11px 12px;
  color: var(--ink-700, #3d544b);
  text-align: left;
  background: rgb(255 255 255 / 96%);
  border: 1px solid rgb(18 36 30 / 7%);
  border-radius: 14px;
}

.ai-page__frameworks button strong {
  font-size: 13.5px;
}

.ai-page__frameworks button small {
  grid-column: 2;
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
  line-height: 1.5;
}

.ai-page__frameworks button:hover {
  border-color: rgb(14 124 107 / 26%);
}

.ai-page__frameworks button.is-active {
  color: var(--mint-800, #084f45);
  background: var(--mint-100, #e2f0ec);
  border-color: rgb(14 124 107 / 34%);
}

.ai-page__field {
  display: grid;
  gap: 6px;
}

.ai-page__field > span {
  color: var(--ink-700, #3d544b);
  font-size: 12px;
}

.ai-page__field textarea {
  padding: 10px 12px;
  color: var(--ink-900, #12241e);
  background: rgb(255 255 255 / 96%);
  border: 1px solid rgb(18 36 30 / 7%);
  border-radius: 12px;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
}

.ai-page__field textarea:focus {
  border-color: var(--mint-500, #1e957d);
  box-shadow: 0 0 0 3px rgb(30 149 125 / 18%);
  outline: none;
}

.ai-page__quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ai-page__quick button {
  padding: 5px 10px;
  color: var(--ink-500, #6b837a);
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 6%);
  border-radius: 999px;
  font-size: 11.5px;
}

.ai-page__quick button:hover {
  color: var(--mint-800, #084f45);
  border-color: rgb(14 124 107 / 24%);
}

.ai-page__generate {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 42px;
  color: #ffffff;
  background: linear-gradient(140deg, #1e957d, #0e7c6b);
  border: 0;
  border-radius: 14px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 10px 22px rgb(14 124 107 / 20%);
}

.ai-page__generate:disabled {
  color: var(--ink-500, #6b837a);
  background: rgb(18 36 30 / 8%);
  box-shadow: none;
}

.ai-page__thinking {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 16px;
  color: var(--ink-500, #6b837a);
  background: rgb(244 249 248 / 92%);
  border-radius: 14px;
  font-size: 12.5px;
}

.ai-page__thinking i {
  width: 6px;
  height: 6px;
  background: var(--mint-500, #1e957d);
  border-radius: 50%;
  animation: ai-dot 1.1s ease-in-out infinite;
}

.ai-page__thinking i:nth-child(2) {
  animation-delay: 0.16s;
}

.ai-page__thinking i:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes ai-dot {
  0%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }

  50% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.ai-page__result {
  display: grid;
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: ai-line;
}

.ai-page__result li {
  position: relative;
  padding: 11px 13px 11px 34px;
  color: var(--ink-700, #3d544b);
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 6%);
  border-radius: 12px;
  font-size: 12.5px;
  line-height: 1.65;
}

.ai-page__result li::before {
  counter-increment: ai-line;
  content: counter(ai-line);
  position: absolute;
  top: 11px;
  left: 12px;
  display: grid;
  width: 16px;
  height: 16px;
  place-items: center;
  color: #ffffff;
  background: var(--mint-600, #0e7c6b);
  border-radius: 50%;
  font-size: 10px;
}

.ai-page__empty {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 26px 16px;
  color: var(--ink-500, #6b837a);
  background: rgb(244 249 248 / 76%);
  border: 1px dashed rgb(18 36 30 / 12%);
  border-radius: 14px;
  text-align: center;
}

.ai-page__empty strong {
  color: var(--ink-900, #12241e);
  font-size: 13px;
}

.ai-page__empty small {
  max-width: 34ch;
  font-size: 11.5px;
  line-height: 1.6;
}

.ai-page__history h4 {
  margin: 0 0 8px;
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
  font-weight: 600;
}

.ai-page__history ul,
.ai-page__history-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-page__history li,
.ai-page__history-list li {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 6%);
  border-radius: 12px;
}

.ai-page__history strong,
.ai-page__history-list strong {
  display: block;
  color: var(--ink-900, #12241e);
  font-size: 12.5px;
}

.ai-page__history small,
.ai-page__history-list small {
  display: block;
  margin-top: 4px;
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
  line-height: 1.55;
}

.ai-page__history time,
.ai-page__history-list time {
  flex: 0 0 auto;
  color: var(--ink-300, #9bafa7);
  font-family: Consolas, "Courier New", monospace;
  font-size: 11.5px;
}

/* 历史页的整行记录：标题与摘要在左列堆叠，时间单独靠右，避免摘要被甩到远端 */
.ai-page__history-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 16px;
}

.ai-page__history-list small {
  margin-top: 0;
}

.ai-page__history-list time {
  grid-row: 1;
  grid-column: 2;
}

.ai-page__cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.ai-page__cards article {
  display: grid;
  gap: 10px;
  padding: 16px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid var(--glass-hairline, rgb(18 36 30 / 7%));
  border-radius: 18px;
  box-shadow:
    0 1px 2px rgb(9 62 54 / 4%),
    0 10px 28px rgb(9 62 54 / 7%);
}

.ai-page__cards article > header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.ai-page__cards article > header > span {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  color: var(--mint-700, #0a6558);
  background: var(--mint-100, #e2f0ec);
  border-radius: 10px;
}

.ai-page__cards h3 {
  margin: 0;
  color: var(--ink-900, #12241e);
  font-size: 15px;
}

.ai-page__cards article > header p {
  margin: 4px 0 0;
  color: var(--ink-500, #6b837a);
  font-size: 12px;
  line-height: 1.6;
}

.ai-page__cards dl {
  margin: 0;
}

.ai-page__cards dt {
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
}

.ai-page__cards dd {
  margin: 4px 0 0;
  color: var(--mint-800, #084f45);
  font-size: 12.5px;
}

.ai-page__cards ul {
  display: grid;
  gap: 7px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-page__cards li {
  position: relative;
  padding-left: 16px;
  color: var(--ink-700, #3d544b);
  font-size: 12.5px;
  line-height: 1.65;
}

.ai-page__cards li::before {
  content: '';
  position: absolute;
  top: 7px;
  left: 2px;
  width: 6px;
  height: 6px;
  background: var(--mint-500, #1e957d);
  border-radius: 2px;
}

.ai-page__panel.is-wide {
  max-width: 1080px;
}

.ai-page__stat.is-live {
  color: var(--mint-800, #084f45);
  background: var(--mint-100, #e2f0ec);
  border-color: rgb(14 124 107 / 22%);
}

.ai-page__source {
  margin-left: auto;
  padding: 3px 9px;
  color: var(--ink-700, #3d544b);
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 6%);
  border-radius: 999px;
  font-size: 11.5px;
  white-space: nowrap;
}

.ai-page__source.is-deepseek {
  color: var(--mint-800, #084f45);
  background: var(--mint-100, #e2f0ec);
  border-color: rgb(14 124 107 / 22%);
}

.ai-page__notice {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin: 0;
  padding: 9px 12px;
  color: #8a4a20;
  background: rgb(212 112 62 / 8%);
  border: 1px solid rgb(194 90 43 / 18%);
  border-radius: 12px;
  font-size: 11.5px;
  line-height: 1.6;
}

.ai-page__context {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.ai-page__context > span {
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
}

.ai-page__context i {
  padding: 3px 9px;
  color: var(--ink-700, #3d544b);
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 6%);
  border-radius: 999px;
  font-size: 11.5px;
  font-style: normal;
}

/* ---------- AI 问答 ---------- */

.ai-page__chat {
  display: grid;
  grid-template-columns: minmax(0, 320px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.ai-page__chat-side,
.ai-page__chat-main {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid var(--glass-hairline, rgb(18 36 30 / 7%));
  border-radius: 18px;
  box-shadow:
    0 1px 2px rgb(9 62 54 / 4%),
    0 10px 28px rgb(9 62 54 / 7%);
}

.ai-page__chat-side > header {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  color: var(--mint-700, #0a6558);
}

.ai-page__chat-side > header h3 {
  margin: 0;
  color: var(--ink-900, #12241e);
  font-size: 15px;
}

.ai-page__chat-side > header p {
  margin: 3px 0 0;
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
}

.ai-page__chat-meta {
  display: grid;
  gap: 8px;
  margin: 0;
}

.ai-page__chat-meta > div {
  display: grid;
  gap: 3px;
  padding: 9px 11px;
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 6%);
  border-radius: 12px;
}

.ai-page__chat-meta dt {
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
}

.ai-page__chat-meta dd {
  margin: 0;
  overflow: hidden;
  color: var(--ink-900, #12241e);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-page__chat-suggest {
  display: grid;
  gap: 6px;
}

.ai-page__chat-suggest > span {
  color: var(--ink-500, #6b837a);
  font-size: 11.5px;
}

.ai-page__chat-suggest button {
  padding: 8px 11px;
  color: var(--ink-700, #3d544b);
  text-align: left;
  background: rgb(255 255 255 / 96%);
  border: 1px solid rgb(18 36 30 / 7%);
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.5;
}

.ai-page__chat-suggest button:hover:not(:disabled) {
  color: var(--mint-800, #084f45);
  border-color: rgb(14 124 107 / 26%);
}

.ai-page__chat-suggest button:disabled {
  color: var(--ink-300, #9bafa7);
}

.ai-page__chat-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  color: var(--ink-700, #3d544b);
  background: rgb(255 255 255 / 96%);
  border: 1px solid rgb(18 36 30 / 8%);
  border-radius: 12px;
  font-size: 12.5px;
}

.ai-page__chat-clear:disabled {
  color: var(--ink-300, #9bafa7);
  background: rgb(18 36 30 / 5%);
}

.ai-page__chat-main {
  align-content: start;
}

.ai-page__chat-thread {
  display: grid;
  gap: 10px;
  min-height: 180px;
  align-content: start;
}

.ai-page__bubble {
  display: grid;
  max-width: min(680px, 92%);
  gap: 6px;
  padding: 11px 13px;
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 6%);
  border-radius: 14px;
}

.ai-page__bubble.is-user {
  justify-self: end;
  background: var(--mint-100, #e2f0ec);
  border-color: rgb(14 124 107 / 20%);
}

.ai-page__bubble > header {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--mint-700, #0a6558);
}

.ai-page__bubble > header strong {
  color: var(--ink-900, #12241e);
  font-size: 12px;
}

.ai-page__bubble > header .ai-page__source {
  margin-left: 0;
}

.ai-page__bubble > header time {
  margin-left: auto;
  color: var(--ink-300, #9bafa7);
  font-family: Consolas, "Courier New", monospace;
  font-size: 11px;
}

.ai-page__bubble > p {
  margin: 0;
  color: var(--ink-700, #3d544b);
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.ai-page__composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: end;
}

.ai-page__composer textarea {
  padding: 10px 12px;
  color: var(--ink-900, #12241e);
  background: rgb(255 255 255 / 96%);
  border: 1px solid rgb(18 36 30 / 8%);
  border-radius: 12px;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
}

.ai-page__composer textarea:focus {
  border-color: var(--mint-500, #1e957d);
  box-shadow: 0 0 0 3px rgb(30 149 125 / 18%);
  outline: none;
}

.ai-page__composer button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 42px;
  padding: 0 16px;
  color: #ffffff;
  background: linear-gradient(140deg, #1e957d, #0e7c6b);
  border: 0;
  border-radius: 14px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 10px 22px rgb(14 124 107 / 20%);
}

.ai-page__composer button:disabled {
  color: var(--ink-500, #6b837a);
  background: rgb(18 36 30 / 8%);
  box-shadow: none;
}

/* Doubao-like chat workspace: keep the setup rail, calm the conversation canvas. */
.ai-page__chat {
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
}

.ai-page__chat-side {
  align-content: start;
  height: min(760px, calc(100vh - 240px));
  min-height: 620px;
  padding: 18px;
  border-radius: 24px;
}

.ai-page__chat-main {
  display: grid;
  height: min(760px, calc(100vh - 240px));
  min-height: 620px;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
  gap: 0;
  padding: 0;
  overflow: hidden;
  background: #ffffff;
  border-radius: 24px;
}

.ai-page__chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 20px;
  border-bottom: 1px solid #edf0ef;
}

.ai-page__chat-identity {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.ai-page__chat-identity > span {
  display: grid;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  place-items: center;
  color: #ffffff;
  background: #1f2322;
  border-radius: 11px;
}

.ai-page__chat-identity strong,
.ai-page__chat-identity small {
  display: block;
}

.ai-page__chat-identity strong {
  color: #1f2322;
  font-size: 13px;
  font-weight: 650;
}

.ai-page__chat-identity small {
  margin-top: 2px;
  color: #8b9490;
  font-size: 11px;
}

.ai-page__chat-model {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 280px;
  padding: 6px 10px;
  overflow: hidden;
  color: #66706c;
  background: #f5f7f6;
  border: 1px solid #e8ecea;
  border-radius: 999px;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-page__chat-model i {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  background: #a5afab;
  border-radius: 50%;
}

.ai-page__chat-model.is-live {
  color: #0d715f;
  background: #eff9f5;
  border-color: #cfebe0;
}

.ai-page__chat-model.is-live i {
  background: #17a77f;
  box-shadow: 0 0 0 3px rgb(23 167 127 / 12%);
}

.ai-page__chat-thread {
  min-height: 0;
  padding: 28px clamp(20px, 5vw, 72px) 22px;
  overflow-y: auto;
  align-content: start;
  gap: 22px;
  scrollbar-gutter: stable;
}

.ai-page__empty.is-chat {
  min-height: 100%;
  align-content: center;
  gap: 10px;
  padding: 48px 16px;
  color: #7f8985;
  background: transparent;
  border: 0;
  border-radius: 0;
}

.ai-page__empty-mark {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  color: #ffffff;
  background: #1f2322;
  border-radius: 17px;
  box-shadow: 0 12px 26px rgb(31 35 34 / 16%);
}

.ai-page__empty.is-chat strong {
  color: #1f2322;
  font-size: 22px;
  font-weight: 620;
}

.ai-page__empty.is-chat small {
  max-width: 52ch;
  color: #8b9490;
  font-size: 13px;
  line-height: 1.7;
}

.ai-page__empty-hints {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 7px;
  margin-top: 8px;
}

.ai-page__empty-hints span {
  padding: 6px 10px;
  color: #6e7874;
  background: #f5f7f6;
  border: 1px solid #e8ecea;
  border-radius: 999px;
  font-size: 11px;
}

.ai-page__bubble {
  max-width: min(780px, 100%);
  gap: 8px;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
}

.ai-page__bubble.is-assistant {
  justify-self: start;
}

.ai-page__bubble.is-user {
  max-width: min(620px, 88%);
  justify-self: end;
  padding: 11px 15px;
  background: #f1f3f3;
  border: 1px solid #e9eceb;
  border-radius: 18px 18px 6px 18px;
}

.ai-page__bubble.is-user > header {
  display: none;
}

.ai-page__bubble.is-assistant > header {
  align-items: center;
  color: #0d715f;
}

.ai-page__bubble.is-assistant > header > svg {
  box-sizing: content-box;
  width: 15px;
  height: 15px;
  padding: 7px;
  color: #0d715f;
  background: #eef8f4;
  border-radius: 50%;
}

.ai-page__bubble > header strong {
  color: #303634;
  font-size: 12.5px;
}

.ai-page__bubble > header time {
  color: #a3aca8;
  font-size: 10.5px;
}

.ai-page__bubble > p {
  color: #343b38;
  font-size: 14px;
  line-height: 1.8;
}

.ai-page__chat-main > .ai-page__notice {
  margin: 0 20px 10px;
}

.ai-page__chat-thread .ai-page__thinking {
  padding: 8px 0;
  color: #7f8985;
  background: transparent;
}

.ai-page__composer {
  grid-template-columns: minmax(0, 1fr) 44px;
  align-items: end;
  gap: 8px;
  margin: 0 20px 18px;
  padding: 9px 9px 9px 16px;
  background: #ffffff;
  border: 1px solid #e2e7e5;
  border-radius: 24px;
  box-shadow:
    0 12px 34px rgb(31 35 34 / 8%),
    0 2px 6px rgb(31 35 34 / 4%);
}

.ai-page__composer textarea {
  min-height: 46px;
  max-height: 150px;
  padding: 10px 0;
  color: #1f2322;
  background: transparent;
  border: 0;
  border-radius: 0;
  font-size: 14px;
  line-height: 1.65;
  resize: none;
}

.ai-page__composer textarea:focus {
  border: 0;
  box-shadow: none;
  outline: none;
}

.ai-page__composer button {
  width: 44px;
  min-width: 44px;
  height: 44px;
  min-height: 44px;
  padding: 0;
  color: #ffffff;
  background: #1f2322;
  border-radius: 50%;
  box-shadow: none;
}

.ai-page__composer button:hover:not(:disabled) {
  background: #343a38;
}

.ai-page__composer button:disabled {
  color: #aeb7b3;
  background: #eef1f0;
  box-shadow: none;
}

@media (max-width: 1180px) {
  .ai-page__board,
  .ai-page__cards,
  .ai-page__chat {
    grid-template-columns: minmax(0, 1fr);
  }

  .ai-page__chat-side {
    height: auto;
    min-height: 0;
  }

  .ai-page__chat-main {
    height: min(720px, 72vh);
    min-height: 540px;
  }
}

@media (max-width: 760px) {
  .ai-page__chat {
    gap: 12px;
  }

  .ai-page__chat-head {
    padding: 12px 14px;
  }

  .ai-page__chat-model {
    max-width: 150px;
  }

  .ai-page__chat-thread {
    padding: 22px 14px 16px;
  }

  .ai-page__empty.is-chat {
    padding: 32px 8px;
  }

  .ai-page__empty.is-chat strong {
    font-size: 19px;
  }

  .ai-page__composer {
    margin: 0 12px 12px;
    padding-left: 13px;
    border-radius: 20px;
  }

  .ai-page__chat-main > .ai-page__notice {
    margin-right: 12px;
    margin-left: 12px;
  }
}
</style>
