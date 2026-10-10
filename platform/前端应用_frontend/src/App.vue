<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  Blocks,
  Boxes,
  ChevronRight,
  CircleCheck,
  ClipboardList,
  Database,
  Droplets,
  Factory,
  FileText,
  FlaskConical,
  Gauge,
  Home,
  History,
  Landmark,
  Leaf,
  Link2,
  LogOut,
  Menu,
  MessageSquare,
  Network,
  PieChart,
  ScanLine,
  ShieldCheck,
  Sparkles,
  Target,
  ThermometerSun,
  TrendingUp,
  TriangleAlert,
  UserRound,
  Users,
  X,
} from 'lucide-vue-next'
import AiSummaryDrawer from '@/components/AiSummaryDrawer.vue'
import SharedCoverStage from '@/components/SharedCoverStage.vue'
import LoginPage from '@/views/LoginView.vue'
import { useSessionStore } from '@/stores/session'

interface SubNavigationItem {
  to: string
  label: string
  description: string
  icon: typeof Home
  selector: string
}

interface NavigationItem {
  name: string
  to: string
  label: string
  eyebrow: string
  icon: typeof Home
  children: SubNavigationItem[]
}

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const navOpen = ref(false)
const aiOpen = ref(false)
const activeSubItem = ref('')
const pageContentRef = ref<HTMLElement | null>(null)

/**
 * 父页面记住上次浏览的子页面：
 * 第一次进入平台时点父页面落到导航第一项，之后切回该父页面回到上次看的子页面。
 * 存在 sessionStorage，刷新和路由切换都不会丢，换一个标签页则是全新的。
 */
const NAV_MEMORY_KEY = 'smart-greenhouse-nav-memory'

function readModuleMemory(): Record<string, string> {
  try {
    const raw = window.sessionStorage.getItem(NAV_MEMORY_KEY)
    if (!raw) {
      return {}
    }
    const parsed = JSON.parse(raw) as Record<string, string>
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

const moduleMemory = ref<Record<string, string>>(readModuleMemory())

const navigation: NavigationItem[] = [
  {
    name: 'home',
    to: '/',
    label: '首页',
    eyebrow: '概念总览',
    icon: Home,
    children: [],
  },
  {
    name: 'production',
    to: '/production/environment',
    label: '生产指挥',
    eyebrow: '环境、控制与水肥',
    icon: Factory,
    children: [
      {
        to: '/production/twin',
        label: '三维大棚',
        description: '数字孪生与设备控制',
        icon: Boxes,
        selector: '.pv2-scene',
      },
      {
        to: '/production/environment',
        label: '实时环境',
        description: '温湿度、光照和土壤水分',
        icon: ThermometerSun,
        selector: '.pv2-trend-wall',
      },
      {
        to: '/production/water',
        label: '水肥计划',
        description: '配方、排程和执行',
        icon: Droplets,
        selector: '.pv2-water',
      },
      {
        to: '/production/chain',
        label: '周期存证',
        description: '环境周期根哈希',
        icon: Link2,
        selector: '.pv2-proof',
      },
    ],
  },
  {
    name: 'traceability',
    to: '/traceability/query',
    label: '质量溯源',
    eyebrow: '合格证与一物一码',
    icon: ScanLine,
    children: [
      {
        to: '/traceability/query',
        label: '追溯查询',
        description: '产品编码和溯源结果',
        icon: ScanLine,
        selector: '.tv2-console',
      },
      {
        to: '/traceability/report',
        label: '质量报告',
        description: '检测指标和报告摘要',
        icon: FlaskConical,
        selector: '.tv2-lab',
      },
      {
        to: '/traceability/evidence',
        label: '链上证据',
        description: '存证、回执和校验',
        icon: Blocks,
        selector: '.tv2-chain-body',
      },
      {
        to: '/traceability/management',
        label: '质量台账',
        description: '证书与检验记录',
        icon: ClipboardList,
        selector: '.tv2-operations',
      },
    ],
  },
  {
    name: 'cooperation',
    to: '/cooperation/metrics',
    label: '合作运营',
    eyebrow: '村庄、股权与分红',
    icon: Users,
    children: [
      {
        to: '/cooperation/metrics',
        label: '经营指标',
        description: '订单、收益和覆盖率',
        icon: TrendingUp,
        selector: '.cv2-stat-strip',
      },
      {
        to: '/cooperation/equity',
        label: '股权分红',
        description: '收益构成与分配结构',
        icon: PieChart,
        selector: '.cv2-board',
      },
      {
        to: '/cooperation/orders',
        label: '合同订单',
        description: '订单履约和业务台账',
        icon: FileText,
        selector: '.cv2-ledger',
      },
    ],
  },
  {
    name: 'governance',
    to: '/governance/metrics',
    label: '治理决策',
    eyebrow: '监管、预警与带动',
    icon: Landmark,
    children: [
      {
        to: '/governance/metrics',
        label: '监管指标',
        description: '产业运行核心数据',
        icon: Gauge,
        selector: '.gv2-metric-deck',
      },
      {
        to: '/governance/chain',
        label: '可信治理',
        description: '链上存证和治理留痕',
        icon: ShieldCheck,
        selector: '.gv2-chain-card',
      },
      {
        to: '/governance/alerts',
        label: '风险预警',
        description: '风险等级和闭环状态',
        icon: TriangleAlert,
        selector: '.gv2-alert-queue',
      },
      {
        to: '/governance/external',
        label: '外部协同',
        description: '三类外部平台同步',
        icon: Network,
        selector: '.gv2-sync-band',
      },
    ],
  },
  {
    name: 'ai',
    to: '/ai/summary',
    label: 'AI 总结',
    eyebrow: '归纳、框架与历史',
    icon: Sparkles,
    children: [
      {
        to: '/ai/chat',
        label: 'AI 问答',
        description: '基于实时数据的多轮问答',
        icon: MessageSquare,
        selector: '.ai-page__chat',
      },
      {
        to: '/ai/summary',
        label: '生成总结',
        description: '按框架输出结构化结论',
        icon: Sparkles,
        selector: '.ai-page__board',
      },
      {
        to: '/ai/frameworks',
        label: '分析框架',
        description: '四类分析角度与维度',
        icon: Target,
        selector: '.ai-page__cards',
      },
      {
        to: '/ai/history',
        label: '历史记录',
        description: '本次会话已生成结论',
        icon: History,
        selector: '.ai-page__panel.is-wide',
      },
    ],
  },
]

const activeNavigation = computed(
  () =>
    navigation.find((item) => item.name === route.meta.module)
    ?? navigation[0],
)
const pageTitle = computed(() => String(route.meta.title ?? '智慧温室'))
const pageSubtitle = computed(() =>
  String(route.meta.subtitle ?? '全链条数字化管理平台'),
)
const shellModuleClass = computed(
  () => `module-${String(route.meta.module ?? 'home')}`,
)
const isStandalonePage = computed(
  () =>
    route.name === 'home'
    || route.name === 'login'
    || route.name === 'public-trace',
)
const isCoverRoute = computed(
  () => route.name === 'home' || route.name === 'login',
)
const sharedCoverClass = computed(
  () => route.name === 'login' ? 'is-login-layer' : 'is-home-layer',
)
const roleLabel = computed(() => {
  const labels: Record<string, string> = {
    ADMIN: '管理员',
    OPERATOR: '操作员',
    QUALITY: '质检员',
    GOVERNMENT: '监管员',
    AGRONOMIST: '农技员',
    COOPERATIVE: '合作社管理员',
  }
  return labels[session.roleCode] ?? session.roleCode
})

function signOut() {
  session.logout()
  window.sessionStorage.removeItem('smart-greenhouse-trace-query-state')
  window.sessionStorage.removeItem(NAV_MEMORY_KEY)
  moduleMemory.value = {}
  void router.push({ name: 'login' })
}

/**
 * 一级导航的落点：有记忆就回到上次浏览的子页面，没有就落到导航第一项。
 * 记忆值必须仍是该父页面的子路由，否则退回默认项。
 */
function moduleTarget(item: NavigationItem) {
  const remembered = moduleMemory.value[item.name]
  if (!remembered) {
    return item.to
  }
  const isChild = item.children.some((child) => child.to === remembered)
  return isChild ? remembered : item.to
}

async function goToSubItem(item: SubNavigationItem) {
  activeSubItem.value = item.label
  navOpen.value = false
  if (route.path !== item.to) {
    await router.push(item.to)
    return
  }
  await nextTick()
  window.setTimeout(() => {
    const target = pageContentRef.value?.querySelector(item.selector)
    target?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    })
  }, 80)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (aiOpen.value) {
    aiOpen.value = false
    return
  }
  navOpen.value = false
}

function postSharedCoverMode() {
  if (!isCoverRoute.value) return
  const mode = route.name === 'login' ? 'login' : 'home'
  const arrival =
    mode === 'home'
    && window.sessionStorage.getItem('smart-greenhouse-cover-arrival') === '1'
  window.dispatchEvent(
    new CustomEvent('greenhouse-cover-mode', {
      detail: { type: 'greenhouse-cover-mode', mode, arrival },
    }),
  )
  if (arrival) {
    window.sessionStorage.removeItem('smart-greenhouse-cover-arrival')
  }
}

function handleSharedCoverReady() {
  if (isCoverRoute.value) {
    postSharedCoverMode()
  }
}

watch(
  () => route.fullPath,
  async () => {
    activeSubItem.value =
      activeNavigation.value.children.find((item) => item.to === route.path)
        ?.label ?? ''
    // 记录当前父页面正在浏览的子页面，供一级导航回跳
    const moduleName = String(route.meta.module ?? '')
    const module = navigation.find((item) => item.name === moduleName)
    if (module?.children.some((child) => child.to === route.path)) {
      moduleMemory.value = { ...moduleMemory.value, [moduleName]: route.path }
      try {
        window.sessionStorage.setItem(
          NAV_MEMORY_KEY,
          JSON.stringify(moduleMemory.value),
        )
      } catch {
        // 浏览器禁用存储时退化为本次会话的内存记忆
      }
    }
    navOpen.value = false
    await nextTick()
    postSharedCoverMode()
  },
  { immediate: true },
)

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  window.addEventListener('greenhouse-cover-ready', handleSharedCoverReady)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('greenhouse-cover-ready', handleSharedCoverReady)
})
</script>

<template>
  <SharedCoverStage
    v-show="isCoverRoute"
    :class="sharedCoverClass"
  />

  <div v-if="isCoverRoute" class="immersive-shell cover-route-shell">
    <LoginPage :class="{ 'is-route-hidden': route.name !== 'login' }" />
  </div>

  <div v-else-if="isStandalonePage" class="immersive-shell">
    <RouterView />
  </div>

  <div
    v-else
    class="app-shell tech-app-shell"
    :class="shellModuleClass"
  >
    <aside class="icon-rail" aria-label="一级导航">
      <RouterLink class="icon-rail__brand" to="/" aria-label="返回首页">
        <span><Leaf :size="21" /></span>
      </RouterLink>

      <nav class="icon-rail__nav">
        <RouterLink
          v-for="item in navigation"
          :key="item.name"
          class="icon-rail__item"
          :class="{ 'is-active': activeNavigation.name === item.name }"
          :to="moduleTarget(item)"
          :title="item.label"
          :aria-label="item.label"
        >
          <component :is="item.icon" :size="20" />
          <small>{{ item.label }}</small>
        </RouterLink>
      </nav>

      <div class="icon-rail__foot">
        <span class="rail-online" title="服务在线"></span>
        <button type="button" title="退出登录" aria-label="退出登录" @click="signOut">
          <LogOut :size="18" />
        </button>
      </div>
    </aside>

    <aside class="module-panel" :class="{ 'module-panel--open': navOpen }">
      <header class="module-panel__header">
        <div>
          <span>{{ activeNavigation.eyebrow }}</span>
          <h2>{{ activeNavigation.label }}</h2>
        </div>
        <button
          type="button"
          aria-label="关闭二级导航"
          @click="navOpen = false"
        >
          <X :size="18" />
        </button>
      </header>

      <div class="module-panel__system">
        <span><Database :size="14" /> 数字孪生底座</span>
        <strong>4 / 4 节点在线</strong>
      </div>

      <nav class="module-navigation" aria-label="二级导航">
        <button
          v-for="item in activeNavigation.children"
          :key="item.label"
          type="button"
          :class="{ 'is-active': activeSubItem === item.label }"
          @click="goToSubItem(item)"
        >
          <span class="module-navigation__icon">
            <component :is="item.icon" :size="23" :stroke-width="1.6" />
          </span>
          <span class="module-navigation__text">
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
          <ChevronRight :size="18" :stroke-width="1.6" />
        </button>
        <p v-if="activeNavigation.children.length === 0">
          当前模块没有二级分类
        </p>
      </nav>

      <div class="module-panel__meter">
        <span>系统协同指数</span>
        <strong>96.8</strong>
        <i></i>
        <small>数据、设备、链上服务运行稳定</small>
      </div>
    </aside>

    <button
      v-if="navOpen"
      class="module-panel__backdrop"
      type="button"
      aria-label="关闭导航"
      @click="navOpen = false"
    ></button>

    <main class="shell-main">
      <header class="shell-topbar">
        <button
          class="shell-topbar__menu"
          type="button"
          aria-label="打开二级导航"
          @click="navOpen = !navOpen"
        >
          <Menu :size="19" />
        </button>
        <div class="shell-topbar__title">
          <span>寒旱区设施农业示范基地</span>
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageSubtitle }}</p>
        </div>

        <div class="shell-topbar__telemetry">
          <span><CircleCheck :size="14" /> 数据链路正常</span>
          <span><Blocks :size="14" /> FISCO 4 / 4</span>
        </div>

        <div class="shell-topbar__actions">
          <button
            v-if="route.meta.module !== 'ai'"
            class="ai-entry"
            type="button"
            aria-label="AI 总结"
            @click="aiOpen = true"
          >
            <Sparkles :size="15" />
            AI 总结
          </button>
          <div class="shell-user">
            <UserRound :size="16" />
            <span>
              <strong>{{ session.displayName }}</strong>
              <small>{{ roleLabel }}</small>
            </span>
          </div>
        </div>
      </header>

      <section
        ref="pageContentRef"
        class="page-content shell-content"
      >
        <RouterView v-slot="{ Component, route: currentRoute }">
          <KeepAlive :include="['TraceView']">
            <component :is="Component" :key="currentRoute.name" />
          </KeepAlive>
        </RouterView>
      </section>
    </main>

    <AiSummaryDrawer
      :open="aiOpen"
      :module-title="pageTitle"
      :module-subtitle="pageSubtitle"
      @close="aiOpen = false"
    />
  </div>
</template>

<style scoped>
.tech-app-shell {
  display: grid;
  min-height: 100vh;
  grid-template-columns: 72px 244px minmax(0, 1fr);
  color: #e9fff5;
  background: #06110d;
}

.immersive-shell {
  background: transparent !important;
}

.is-route-hidden {
  visibility: hidden !important;
  pointer-events: none !important;
}

.icon-rail {
  position: sticky;
  top: 0;
  z-index: 42;
  display: grid;
  height: 100vh;
  grid-template-rows: auto minmax(0, 1fr) auto;
  padding: 12px 8px;
  background: #04100b;
  border-right: 1px solid rgb(102 229 166 / 13%);
}

.icon-rail__brand {
  display: grid;
  width: 46px;
  height: 46px;
  margin: 0 auto;
  place-items: center;
  color: #061b12;
  background: linear-gradient(135deg, #76efb5, #32c988);
  border-radius: 8px;
  box-shadow: 0 0 28px rgb(65 219 148 / 23%);
}

.icon-rail__brand span {
  display: grid;
  place-items: center;
}

.icon-rail__nav {
  display: grid;
  align-content: start;
  gap: 7px;
  margin-top: 18px;
}

.icon-rail__item {
  position: relative;
  display: grid;
  min-height: 54px;
  place-items: center;
  gap: 3px;
  color: #617c6f;
  border: 1px solid transparent;
  border-radius: 8px;
  transition:
    color 160ms ease,
    background-color 160ms ease,
    border-color 160ms ease;
}

.icon-rail__item small {
  font-size: 8px;
  white-space: nowrap;
}

.icon-rail__item:hover,
.icon-rail__item.is-active,
.icon-rail__item.router-link-exact-active {
  color: #dcfff0;
  background: rgb(64 215 143 / 9%);
  border-color: rgb(89 230 163 / 17%);
}

.icon-rail__item.is-active::before,
.icon-rail__item.router-link-exact-active::before {
  content: "";
  position: absolute;
  top: 10px;
  bottom: 10px;
  left: -9px;
  width: 2px;
  background: #52e3a1;
  border-radius: 2px;
  box-shadow: 0 0 12px rgb(82 227 161 / 70%);
}

.icon-rail__foot {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding-top: 10px;
  border-top: 1px solid rgb(102 229 166 / 12%);
}

.rail-online {
  width: 8px;
  height: 8px;
  background: #50df9d;
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgb(80 223 157 / 9%);
}

.icon-rail__foot button {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  color: #789487;
  background: transparent;
  border: 0;
  border-radius: 7px;
}

.icon-rail__foot button:hover {
  color: #ffb5ad;
  background: rgb(255 112 99 / 8%);
}

.module-panel {
  position: sticky;
  top: 0;
  z-index: 38;
  display: grid;
  height: 100vh;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  color: #dff7eb;
  background: rgb(8 25 18 / 96%);
  border-right: 1px solid rgb(102 229 166 / 12%);
  backdrop-filter: blur(16px);
}

.module-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 18px 17px 14px;
}

.module-panel__header span {
  color: #5ddd9f;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.module-panel__header h2 {
  margin: 5px 0 0;
  font-size: 18px;
  font-weight: 650;
}

.module-panel__header > button {
  display: none;
  width: 32px;
  height: 32px;
  place-items: center;
  color: #9cb9aa;
  background: rgb(255 255 255 / 4%);
  border: 1px solid rgb(255 255 255 / 8%);
  border-radius: 7px;
}

.module-panel__system {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 14px 14px;
  padding: 9px 10px;
  background: rgb(70 213 144 / 6%);
  border: 1px solid rgb(91 225 161 / 12%);
  border-radius: 7px;
}

.module-panel__system span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #8ba99a;
  font-size: 9px;
}

.module-panel__system strong {
  color: #58e3a1;
  font-size: 9px;
}

.module-navigation {
  display: grid;
  align-content: start;
  gap: 5px;
  padding: 0 11px;
  overflow-y: auto;
}

.module-navigation button {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 54px;
  padding: 8px 9px;
  color: #789487;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 7px;
}

.module-navigation button:hover,
.module-navigation button.is-active {
  color: #e2fff1;
  background: rgb(66 212 142 / 8%);
  border-color: rgb(86 225 158 / 15%);
}

.module-navigation button.is-active {
  box-shadow: inset 2px 0 #50e09d;
}

.module-navigation__index {
  color: #4fcf93;
  font-family: Consolas, monospace;
  font-size: 9px;
}

.module-navigation__text strong,
.module-navigation__text small {
  display: block;
}

.module-navigation__text strong {
  font-size: 11px;
}

.module-navigation__text small {
  margin-top: 3px;
  overflow: hidden;
  color: #617c70;
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-navigation > p {
  margin: 14px;
  color: #5f796d;
  font-size: 10px;
}

.module-panel__meter {
  display: grid;
  gap: 6px;
  margin: 12px 14px 15px;
  padding: 12px;
  background: rgb(0 0 0 / 15%);
  border: 1px solid rgb(103 215 160 / 10%);
  border-radius: 7px;
}

.module-panel__meter span,
.module-panel__meter small {
  color: #648073;
  font-size: 8px;
}

.module-panel__meter strong {
  color: #dffff0;
  font-size: 22px;
  line-height: 1;
}

.module-panel__meter i {
  width: 96.8%;
  height: 3px;
  background: linear-gradient(90deg, #3ed08e, #7df0b8);
  border-radius: 3px;
  box-shadow: 0 0 12px rgb(74 220 149 / 35%);
}

.module-panel__backdrop {
  display: none;
}

.shell-main {
  min-width: 0;
}

.shell-topbar {
  position: sticky;
  top: 0;
  z-index: 35;
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 70px;
  padding: 10px 20px;
  background: rgb(7 20 15 / 88%);
  border-bottom: 1px solid rgb(102 229 166 / 12%);
  backdrop-filter: blur(18px);
}

.shell-topbar__menu {
  display: none;
  width: 36px;
  height: 36px;
  place-items: center;
  color: #b4d2c3;
  background: rgb(255 255 255 / 4%);
  border: 1px solid rgb(255 255 255 / 8%);
  border-radius: 7px;
}

.shell-topbar__title span {
  color: #51dc99;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.shell-topbar__title h1 {
  display: inline-block;
  margin: 2px 0 0;
  font-size: 17px;
  line-height: 1.15;
}

.shell-topbar__title p {
  display: inline-block;
  margin: 0 0 0 10px;
  color: #668175;
  font-size: 9px;
}

.shell-topbar__telemetry {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-left: auto;
}

.shell-topbar__telemetry span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 28px;
  padding: 0 8px;
  color: #83a294;
  background: rgb(255 255 255 / 3%);
  border: 1px solid rgb(99 218 166 / 10%);
  border-radius: 6px;
  font-size: 8px;
}

.shell-topbar__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-entry {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 11px;
  color: #062116;
  background: linear-gradient(135deg, #7cf0b9, #3dcf91);
  border: 0;
  border-radius: 7px;
  font-size: 10px;
  font-weight: 700;
  box-shadow: 0 0 24px rgb(64 220 148 / 18%);
}

.shell-user {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 36px;
  padding: 0 9px;
  color: #9cb8aa;
  background: rgb(255 255 255 / 3%);
  border: 1px solid rgb(104 218 166 / 10%);
  border-radius: 7px;
}

.shell-user strong,
.shell-user small {
  display: block;
}

.shell-user strong {
  color: #ddfff0;
  font-size: 9px;
}

.shell-user small {
  margin-top: 2px;
  color: #668175;
  font-size: 8px;
}

.shell-content {
  padding: 18px 20px 32px;
}

@media (max-width: 1180px) {
  .tech-app-shell {
    grid-template-columns: 72px minmax(0, 1fr);
  }

  .module-panel {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 72px;
    z-index: 80;
    width: 244px;
    transform: translateX(-110%);
    transition: transform 220ms cubic-bezier(0.22, 1, 0.36, 1);
  }

  .module-panel--open {
    transform: translateX(0);
  }

  .module-panel__header > button {
    display: grid;
  }

  .module-panel__backdrop {
    position: fixed;
    inset: 0 0 0 72px;
    z-index: 70;
    display: block;
    background: rgb(0 0 0 / 48%);
    border: 0;
    backdrop-filter: blur(3px);
  }

  .shell-topbar__menu {
    display: grid;
  }
}

@media (max-width: 760px) {
  .tech-app-shell {
    grid-template-columns: 58px minmax(0, 1fr);
  }

  .icon-rail {
    padding: 8px 5px;
  }

  .icon-rail__brand {
    width: 40px;
    height: 40px;
  }

  .icon-rail__item {
    min-height: 48px;
  }

  .icon-rail__item small {
    display: none;
  }

  .module-panel {
    left: 58px;
    width: min(244px, calc(100vw - 58px));
  }

  .module-panel__backdrop {
    left: 58px;
  }

  .shell-topbar {
    min-height: 64px;
    padding: 9px 12px;
  }

  .shell-topbar__title p,
  .shell-topbar__telemetry,
  .shell-user span {
    display: none;
  }

  .shell-topbar__title h1 {
    font-size: 15px;
  }

  .shell-content {
    padding: 14px 12px 28px;
  }

}
</style>
