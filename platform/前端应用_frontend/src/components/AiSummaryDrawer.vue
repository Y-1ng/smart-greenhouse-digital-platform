<script setup lang="ts">
import { computed, watch } from 'vue'
import {
  Bot,
  ChevronRight,
  FileChartColumn,
  History,
  Maximize2,
  Send,
  Sparkles,
  Target,
  X,
} from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useAiSummary } from '@/composables/useAiSummary'
import type { AiFrameworkId } from '@/composables/useAiSummary'

const props = defineProps<{
  open: boolean
  moduleTitle: string
  moduleSubtitle: string
}>()

const emit = defineEmits<{
  close: []
}>()

const router = useRouter()
const {
  frameworks,
  selectedFramework,
  activeFramework,
  instruction,
  generating,
  output,
  history,
  generate,
  runQuickPrompt,
  selectFramework,
  source,
  sourceLabel,
  notice,
  refreshStatus,
} = useAiSummary()

/** 抽屉是就地速览，分析对象取当前模块标题 */
const scopeTitle = computed(() => props.moduleTitle || '当前模块')

const moduleKey = computed<AiFrameworkId>(() => {
  const title = props.moduleTitle
  if (title.includes('质量')) return 'quality'
  if (title.includes('合作')) return 'operation'
  if (title.includes('治理')) return 'governance'
  return 'production'
})

watch(
  () => props.open,
  (open) => {
    if (!open) return
    selectFramework(moduleKey.value)
    void refreshStatus()
    if (!output.value) void generate(scopeTitle.value)
  },
)

/** 抽屉用于速览，完整的框架说明和历史在独立页面里 */
function openFullPage() {
  emit('close')
  void router.push({ name: 'ai-summary' })
}
</script>

<template>
  <Teleport to="body">
    <Transition name="ai-drawer">
      <div
        v-if="open"
        class="ai-summary-overlay"
        @click.self="emit('close')"
      >
        <aside class="ai-summary-drawer" aria-label="AI 分析与总结">
          <header class="ai-summary-drawer__header">
            <div class="ai-summary-drawer__mark">
              <Sparkles :size="19" />
            </div>
            <div>
              <span>统一 AI 总结</span>
              <strong>{{ moduleTitle }}</strong>
              <small>{{ moduleSubtitle }}</small>
            </div>
            <button type="button" aria-label="关闭 AI 总结" @click="emit('close')">
              <X :size="18" />
            </button>
          </header>

          <div class="ai-summary-drawer__body">
            <section class="ai-summary-framework">
              <div class="ai-summary-section-title">
                <Target :size="15" />
                <span>分析框架</span>
              </div>
              <div class="ai-summary-framework__list">
                <button
                  v-for="item in frameworks"
                  :key="item.id"
                  type="button"
                  :class="{ 'is-active': selectedFramework === item.id }"
                  @click="selectFramework(item.id)"
                >
                  <span>{{ item.label }}</span>
                  <ChevronRight :size="14" />
                </button>
              </div>
              <p>{{ activeFramework.description }}</p>
            </section>

            <section class="ai-summary-instruction">
              <div class="ai-summary-section-title">
                <Bot :size="15" />
                <span>自定义指令</span>
              </div>
              <textarea
                v-model="instruction"
                rows="3"
                placeholder="例如：重点列出三项风险，并给出今天可执行的处置步骤。"
              ></textarea>
              <div class="ai-summary-quick-prompts">
                <button
                  type="button"
                  @click="runQuickPrompt('只保留高优先级风险', scopeTitle)"
                >
                  只看风险
                </button>
                <button
                  type="button"
                  @click="runQuickPrompt('生成对比表格', scopeTitle)"
                >
                  生成对比表格
                </button>
                <button
                  type="button"
                  @click="runQuickPrompt('给出今日行动清单', scopeTitle)"
                >
                  今日行动
                </button>
              </div>
              <button
                class="ai-summary-generate"
                type="button"
                :disabled="generating"
                @click="generate(scopeTitle)"
              >
                <Sparkles :size="15" />
                {{ generating ? '正在分析模块数据' : '生成总结' }}
              </button>
            </section>

            <section class="ai-summary-result">
              <div class="ai-summary-section-title">
                <FileChartColumn :size="15" />
                <span>分析结果</span>
                <span
                  v-if="output"
                  class="ai-summary-source"
                  :class="`is-${source}`"
                >
                  {{ sourceLabel }}
                </span>
              </div>
              <p v-if="notice" class="ai-summary-notice">{{ notice }}</p>
              <div v-if="generating" class="ai-summary-thinking">
                <i></i>
                <i></i>
                <i></i>
                <span>正在按子分类归纳证据与结论</span>
              </div>
              <pre v-else-if="output">{{ output }}</pre>
              <p v-else class="ai-summary-empty">
                选择分析框架并生成当前模块的结构化总结。
              </p>
            </section>

            <section v-if="history.length" class="ai-summary-history">
              <div class="ai-summary-section-title">
                <History :size="15" />
                <span>最近分析</span>
              </div>
              <button
                v-for="item in history"
                :key="`${item.time}-${item.title}`"
                type="button"
                @click="instruction = item.summary"
              >
                <div>
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.summary }}</span>
                </div>
                <time>{{ item.time }}</time>
              </button>
            </section>
          </div>

          <footer class="ai-summary-drawer__footer">
            <span>
              {{ source === 'deepseek'
                ? `由 ${sourceLabel} 生成`
                : '当前为本地框架结论（未接入大模型）' }}
            </span>
            <div class="ai-summary-drawer__footer-actions">
              <button type="button" @click="openFullPage">
                <Maximize2 :size="14" />
                打开完整页面
              </button>
              <button type="button" @click="generate(scopeTitle)">
                <Send :size="14" />
                重新生成
              </button>
            </div>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.ai-summary-overlay {
  position: fixed;
  inset: 0;
  z-index: 220;
  display: flex;
  justify-content: flex-end;
  background: rgb(2 10 7 / 58%);
  backdrop-filter: blur(8px);
}

.ai-summary-drawer {
  display: grid;
  width: min(520px, 94vw);
  height: 100vh;
  grid-template-rows: auto minmax(0, 1fr) auto;
  color: #e9fff5;
  background:
    linear-gradient(180deg, rgb(16 42 31 / 96%), rgb(5 17 12 / 98%)),
    #07110d;
  border-left: 1px solid rgb(91 231 168 / 24%);
  box-shadow: -24px 0 70px rgb(0 0 0 / 42%);
}

.ai-summary-drawer__header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 18px;
  border-bottom: 1px solid rgb(120 235 180 / 14%);
}

.ai-summary-drawer__mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  color: #062116;
  background: linear-gradient(135deg, #7bf3bb, #36c88a);
  border-radius: 8px;
  box-shadow: 0 0 28px rgb(59 214 145 / 28%);
}

.ai-summary-drawer__header span,
.ai-summary-drawer__header strong,
.ai-summary-drawer__header small {
  display: block;
}

.ai-summary-drawer__header span {
  color: #64e7a9;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.ai-summary-drawer__header strong {
  margin-top: 4px;
  font-size: 17px;
}

.ai-summary-drawer__header small {
  margin-top: 3px;
  color: #7f9e90;
  font-size: 11px;
}

.ai-summary-drawer__header > button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: #9bb7a9;
  background: rgb(255 255 255 / 4%);
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 7px;
}

.ai-summary-drawer__body {
  display: grid;
  align-content: start;
  gap: 18px;
  padding: 18px;
  overflow-y: auto;
}

.ai-summary-section-title {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #b9d8c9;
  font-size: 11px;
  font-weight: 700;
}

.ai-summary-source {
  margin-left: auto;
  padding: 2px 8px;
  color: var(--ink-700, #3d544b);
  background: rgb(244 249 248 / 92%);
  border: 1px solid rgb(18 36 30 / 6%);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.ai-summary-source.is-deepseek {
  color: var(--mint-800, #084f45);
  background: var(--mint-100, #e2f0ec);
  border-color: rgb(14 124 107 / 22%);
}

.ai-summary-notice {
  margin: 10px 0 0;
  padding: 8px 10px;
  color: #8a4a20;
  background: rgb(212 112 62 / 8%);
  border: 1px solid rgb(194 90 43 / 18%);
  border-radius: 12px;
  font-size: 11px;
  line-height: 1.6;
}

.ai-summary-framework__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
  margin-top: 10px;
}

.ai-summary-framework__list button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  padding: 0 10px;
  color: #9ab5a7;
  background: rgb(255 255 255 / 3%);
  border: 1px solid rgb(157 225 188 / 12%);
  border-radius: 7px;
  font-size: 12px;
  text-align: left;
}

.ai-summary-framework__list button.is-active {
  color: #eafff3;
  background: rgb(63 211 143 / 12%);
  border-color: rgb(88 231 163 / 42%);
  box-shadow: inset 3px 0 #48da99;
}

.ai-summary-framework > p {
  margin: 9px 0 0;
  color: #789487;
  font-size: 11.5px;
  line-height: 1.7;
}

.ai-summary-instruction textarea {
  width: 100%;
  min-height: 84px;
  margin-top: 10px;
  padding: 10px;
  resize: vertical;
  color: #ddf7eb;
  background: rgb(0 0 0 / 18%);
  border: 1px solid rgb(128 213 170 / 18%);
  border-radius: 7px;
  outline: none;
  font-size: 11px;
  line-height: 1.6;
}

.ai-summary-instruction textarea:focus {
  border-color: rgb(83 225 158 / 54%);
  box-shadow: 0 0 0 3px rgb(67 218 147 / 8%);
}

.ai-summary-quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.ai-summary-quick-prompts button {
  min-height: 27px;
  padding: 0 8px;
  color: #8fd6b4;
  background: rgb(61 204 137 / 7%);
  border: 1px solid rgb(93 216 157 / 16%);
  border-radius: 6px;
  font-size: 9px;
}

.ai-summary-generate {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  min-height: 38px;
  margin-top: 10px;
  color: #062116;
  background: linear-gradient(135deg, #78eeb7, #39c98d);
  border: 0;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 700;
}

.ai-summary-generate:disabled {
  cursor: wait;
  opacity: 0.7;
}

.ai-summary-result pre {
  margin: 10px 0 0;
  padding: 12px;
  overflow-x: auto;
  color: #cce8da;
  background: rgb(0 0 0 / 18%);
  border: 1px solid rgb(124 222 173 / 13%);
  border-radius: 7px;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.ai-summary-thinking {
  display: flex;
  align-items: center;
  gap: 5px;
  min-height: 90px;
  margin-top: 10px;
  padding: 14px;
  color: #82a294;
  background: rgb(0 0 0 / 15%);
  border: 1px solid rgb(124 222 173 / 11%);
  border-radius: 7px;
  font-size: 12px;
}

.ai-summary-thinking i {
  width: 5px;
  height: 5px;
  background: #4ce0a4;
  border-radius: 50%;
  animation: ai-thinking 900ms ease-in-out infinite alternate;
}

.ai-summary-thinking i:nth-child(2) {
  animation-delay: 180ms;
}

.ai-summary-thinking i:nth-child(3) {
  margin-right: 6px;
  animation-delay: 360ms;
}

.ai-summary-empty {
  margin: 10px 0 0;
  color: #718d80;
  font-size: 12px;
}

.ai-summary-history {
  display: grid;
  gap: 7px;
}

.ai-summary-history > button {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  width: 100%;
  padding: 9px 10px;
  color: #cae9da;
  text-align: left;
  background: rgb(255 255 255 / 3%);
  border: 1px solid rgb(146 221 182 / 10%);
  border-radius: 7px;
}

.ai-summary-history strong,
.ai-summary-history span {
  display: block;
}

.ai-summary-history strong {
  font-size: 12px;
}

.ai-summary-history span {
  margin-top: 3px;
  overflow: hidden;
  color: #789487;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-summary-history time {
  color: #668175;
  font-size: 11px;
}

.ai-summary-drawer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 18px;
  color: #688477;
  border-top: 1px solid rgb(120 235 180 / 13%);
  font-size: 11.5px;
}

.ai-summary-drawer__footer-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.ai-summary-drawer__footer button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 9px;
  color: #93d9b7;
  background: rgb(73 213 145 / 7%);
  border: 1px solid rgb(80 220 151 / 18%);
  border-radius: 6px;
  font-size: 12px;
}

.ai-drawer-enter-active,
.ai-drawer-leave-active {
  transition: opacity 220ms ease;
}

.ai-drawer-enter-active .ai-summary-drawer,
.ai-drawer-leave-active .ai-summary-drawer {
  transition: transform 260ms cubic-bezier(0.22, 1, 0.36, 1);
}

.ai-drawer-enter-from,
.ai-drawer-leave-to {
  opacity: 0;
}

.ai-drawer-enter-from .ai-summary-drawer,
.ai-drawer-leave-to .ai-summary-drawer {
  transform: translateX(100%);
}

@keyframes ai-thinking {
  to {
    opacity: 0.25;
    transform: translateY(-3px);
  }
}

@media (max-width: 640px) {
  .ai-summary-framework__list {
    grid-template-columns: 1fr;
  }

  .ai-summary-drawer__footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
