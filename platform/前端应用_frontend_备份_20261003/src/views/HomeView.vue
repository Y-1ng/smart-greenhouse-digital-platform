<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowRight,
  ChevronDown,
  CircleDot,
  Droplets,
  Fan,
  Info,
  Layers3,
  LayoutGrid,
  Leaf,
  Lightbulb,
  LocateFixed,
  Maximize2,
  Rotate3d,
  ShieldCheck,
  Shrink,
  Sprout,
  Thermometer,
  X,
} from 'lucide-vue-next'
import GreenhouseScene from '@/components/greenhouse/GreenhouseScene.vue'
import GreenhouseParkScene from '@/components/greenhouse/GreenhouseParkScene.vue'
import { greenhouseHotspots } from '@/data/greenhouse'
import type {
  GreenhouseHotspot,
  GreenhouseSite,
  SceneMode,
} from '@/types/greenhouse'

interface GreenhouseSceneExpose {
  resetView: () => void
  focusHotspot: (hotspotId: string) => void
}

const heroRef = ref<HTMLElement | null>(null)
const sceneRef = ref<GreenhouseSceneExpose | null>(null)
const parkSceneRef = ref<{ resetView: () => void } | null>(null)
const sceneMode = ref<SceneMode>('overview')
const autoRotate = ref(false)
const selectedSite = ref<GreenhouseSite | null>(null)
const selectedHotspot = ref<GreenhouseHotspot>(greenhouseHotspots[0])
const detailOpen = ref(false)
const adVisible = ref(true)
const adClosing = ref(false)
const adDismissed = ref(false)
const navScrolled = ref(false)
const navOnLight = ref(false)
const isFullscreen = ref(false)
const scrollProgress = ref(0)
let adDismissTimer = 0

const sceneModes: Array<{
  id: SceneMode
  label: string
  icon: typeof Layers3
}> = [
  { id: 'overview', label: '全景', icon: Layers3 },
  { id: 'environment', label: '环境感知', icon: Leaf },
  { id: 'control', label: '执行设备', icon: Fan },
]

const environmentMetrics = [
  {
    id: 'air-sensor',
    label: '空气温度',
    value: '22.6',
    unit: '℃',
    note: '适宜范围 18-28℃',
    icon: Thermometer,
    position: 'one',
  },
  {
    id: 'air-sensor',
    label: '空气湿度',
    value: '68.4',
    unit: '%',
    note: '较昨日 +3.2%',
    icon: Droplets,
    position: 'two',
  },
  {
    id: 'light-sensor',
    label: '光照强度',
    value: '18,500',
    unit: 'lx',
    note: '补光阈值 12,000',
    icon: Lightbulb,
    position: 'three',
  },
  {
    id: 'soil-sensor',
    label: '土壤水分',
    value: '43.2',
    unit: '%',
    note: '下次灌溉 15:30',
    icon: Sprout,
    position: 'four',
  },
]

const controlPoints = greenhouseHotspots.filter(
  (hotspot) => hotspot.category === 'actuator',
)

const traceSteps = [
  {
    index: '01',
    title: '周期根哈希',
    description: '环境数据按周期生成哈希链，仅将根哈希写入联盟链。',
  },
  {
    index: '02',
    title: '电子合格证',
    description: '开证 API 验签通过后保存摘要，链下保留完整证书与附件。',
  },
  {
    index: '03',
    title: '一物一码',
    description: '产品编码与签名绑定，扫码时同步完成签名和链上记录核验。',
  },
  {
    index: '04',
    title: '田块指针链',
    description: '种植至销售节点通过前一摘要串联，断链时可以立即识别。',
  },
]

function changeMode(mode: SceneMode) {
  sceneMode.value = mode
  const nextHotspot = greenhouseHotspots.find((hotspot) => {
    if (mode === 'overview') return true
    if (mode === 'environment') return hotspot.category === 'sensor'
    return hotspot.category === 'actuator'
  })
  if (nextHotspot) selectedHotspot.value = nextHotspot
}

function selectHotspot(hotspot: GreenhouseHotspot) {
  selectedHotspot.value = hotspot
  detailOpen.value = true
  nextTick(() => sceneRef.value?.focusHotspot(hotspot.id))
}

function selectSite(site: GreenhouseSite) {
  selectedSite.value = site
  detailOpen.value = false
  if (adVisible.value) dismissAd()
  nextTick(() => {
    window.setTimeout(() => sceneRef.value?.resetView(), 320)
  })
}

function returnToPark() {
  selectedSite.value = null
  detailOpen.value = false
  nextTick(() => parkSceneRef.value?.resetView())
}

function selectMetric(hotspotId: string) {
  const hotspot = greenhouseHotspots.find((item) => item.id === hotspotId)
  if (hotspot) selectHotspot(hotspot)
}

function resetScene() {
  detailOpen.value = false
  sceneRef.value?.resetView()
}

function dismissAd() {
  if (!adVisible.value || adClosing.value) return
  adClosing.value = true
  window.clearTimeout(adDismissTimer)
  adDismissTimer = window.setTimeout(() => {
    adVisible.value = false
    adClosing.value = false
    adDismissed.value = true
    nextTick(() => sceneRef.value?.resetView())
  }, 280)
}

function reopenAd() {
  adDismissed.value = false
  adClosing.value = false
  adVisible.value = true
}

function scrollToSection(sectionId: string) {
  document.getElementById(sectionId)?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

async function toggleFullscreen() {
  if (!heroRef.value) return
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else {
      await heroRef.value.requestFullscreen()
    }
  } catch {
    isFullscreen.value = false
  }
}

function handleScroll() {
  const heroHeight = heroRef.value?.clientHeight ?? window.innerHeight
  navScrolled.value = window.scrollY > 28
  navOnLight.value = window.scrollY > heroHeight - 86
  scrollProgress.value = Math.min(window.scrollY / heroHeight, 1)
}

function handleFullscreenChange() {
  isFullscreen.value = Boolean(document.fullscreenElement)
}

let revealObserver: IntersectionObserver | null = null

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  handleScroll()

  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) entry.target.classList.add('is-visible')
      })
    },
    {
      threshold: 0.16,
      rootMargin: '0px 0px -8% 0px',
    },
  )

  document
    .querySelectorAll('[data-reveal]')
    .forEach((element) => revealObserver?.observe(element))
})

onBeforeUnmount(() => {
  window.clearTimeout(adDismissTimer)
  window.removeEventListener('scroll', handleScroll)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  revealObserver?.disconnect()
})
</script>

<template>
  <div class="home-immersive-page">
    <header
      class="home-nav"
      :class="{
        'is-scrolled': navScrolled,
        'is-light': navOnLight,
      }"
    >
      <RouterLink class="home-nav__brand" to="/">
        <span class="home-nav__mark"><Leaf :size="19" /></span>
        <span>
          <strong>智慧温室</strong>
          <small>Smart Greenhouse</small>
        </span>
      </RouterLink>

      <nav class="home-nav__links" aria-label="首页导航">
        <button type="button" @click="scrollToSection('environment')">环境感知</button>
        <button type="button" @click="scrollToSection('control')">执行控制</button>
        <button type="button" @click="scrollToSection('trust')">可信存证</button>
        <RouterLink to="/production">管理后台</RouterLink>
      </nav>

      <div class="home-nav__state">
        <span class="home-online-dot"></span>
        <span>系统在线</span>
      </div>
    </header>

    <section
      id="home-top"
      ref="heroRef"
      class="home-hero"
      :class="{
        'is-ad-closing': adClosing,
        'is-ad-dismissed': adDismissed,
      }"
    >
      <Transition name="scene-swap" mode="out-in">
        <GreenhouseParkScene
          v-if="!selectedSite"
          key="park"
          ref="parkSceneRef"
          class="home-hero__scene"
          :selected-id="''"
          :auto-rotate="autoRotate"
          @select="selectSite"
        />
        <GreenhouseScene
          v-else
          :key="selectedSite.id"
          ref="sceneRef"
          class="home-hero__scene"
          :mode="sceneMode"
          :selected-id="selectedHotspot.id"
          :auto-rotate="autoRotate"
          :crop="selectedSite.crop"
          theme="dark"
          immersive
          @select="selectHotspot"
        />
      </Transition>

      <div v-if="adVisible" class="home-hero__copy">
        <button class="home-ad-close" type="button" aria-label="关闭广告" @click="dismissAd">
          <X :size="17" />
        </button>
        <span class="home-product-label">
          {{ selectedSite ? `${selectedSite.name}数字孪生` : '园区三维总览' }}
        </span>
        <h1 v-if="selectedSite">让每一株作物<br />生长在数据之中</h1>
        <h1 v-else>选择一座温室<br />开始数字孪生</h1>
        <p v-if="selectedSite">
          从空气、土壤到设备执行，再到质量溯源与链上核验，一座温室的完整生长过程都在这里发生。
        </p>
        <p v-else>
          园区中的 6 座温室均已完成三维建模。点击任意温室，进入它的环境、设备和可信数据空间。
        </p>
        <div class="home-hero__actions">
          <button
            class="home-action home-action--primary"
            type="button"
            @click="selectedSite ? scrollToSection('environment') : dismissAd()"
          >
            {{ selectedSite ? '探索数字孪生' : '开始选择温室' }}
            <ArrowRight :size="17" />
          </button>
          <RouterLink class="home-action home-action--secondary" to="/production">
            进入生产指挥
          </RouterLink>
        </div>
      </div>

      <div v-if="selectedSite" class="home-hero__metrics">
        <button
          v-for="metric in environmentMetrics"
          :key="`${metric.label}-${metric.position}`"
          class="home-hero-metric"
          :class="`is-${metric.position}`"
          type="button"
          @click="selectMetric(metric.id)"
        >
          <span><component :is="metric.icon" :size="15" />{{ metric.label }}</span>
          <strong>{{ metric.value }} <small>{{ metric.unit }}</small></strong>
          <em>{{ metric.note }}</em>
        </button>
      </div>

      <div v-if="selectedSite" class="home-active-site">
        <span>当前温室</span>
        <strong>{{ selectedSite.name }}</strong>
        <small>{{ selectedSite.crop }} · {{ selectedSite.area }}</small>
      </div>

      <div class="home-hero__controls">
        <button
          v-if="selectedSite"
          class="home-park-return"
          type="button"
          @click="returnToPark"
        >
          <LayoutGrid :size="15" />
          切换温室
        </button>
        <div v-if="selectedSite" class="home-layer-switch" role="tablist" aria-label="三维图层">
          <button
            v-for="mode in sceneModes"
            :key="mode.id"
            type="button"
            :class="{ 'is-active': sceneMode === mode.id }"
            :aria-selected="sceneMode === mode.id"
            role="tab"
            @click="changeMode(mode.id)"
          >
            <component :is="mode.icon" :size="15" />
            {{ mode.label }}
          </button>
        </div>
        <button
          class="home-round-control"
          :class="{ 'is-active': autoRotate }"
          type="button"
          aria-label="自动环视"
          @click="autoRotate = !autoRotate"
        >
          <Rotate3d :size="17" />
          <span class="home-round-control__label">自动环视</span>
        </button>
        <button class="home-round-control" type="button" aria-label="重置视角" @click="resetScene">
          <LocateFixed :size="17" />
          <span class="home-round-control__label">重置视角</span>
        </button>
        <button class="home-round-control" type="button" aria-label="全屏查看" @click="toggleFullscreen">
          <Shrink v-if="isFullscreen" :size="17" />
          <Maximize2 v-else :size="17" />
          <span class="home-round-control__label">
            {{ isFullscreen ? '退出全屏' : '全屏查看' }}
          </span>
        </button>
        <button
          v-if="adDismissed"
          class="home-round-control"
          type="button"
          aria-label="重新打开简介"
          @click="reopenAd"
        >
          <Info :size="17" />
          <span class="home-round-control__label">项目简介</span>
        </button>
      </div>

      <article v-if="selectedSite && detailOpen" class="home-hotspot-popover">
        <button class="home-hotspot-popover__close" type="button" aria-label="关闭热点详情" @click="detailOpen = false">
          <X :size="16" />
        </button>
        <span class="home-hotspot-popover__icon">
          <component :is="selectedHotspot.icon" :size="20" />
        </span>
        <div>
          <span>{{ selectedHotspot.category === 'sensor' ? '环境感知' : '执行设备' }}</span>
          <strong>{{ selectedHotspot.label }}</strong>
          <b>{{ selectedHotspot.value }} {{ selectedHotspot.unit }}</b>
          <p>{{ selectedHotspot.description }}</p>
        </div>
      </article>

      <button class="home-scroll-cue" type="button" @click="selectedSite ? scrollToSection('environment') : dismissAd()">
        <span>{{ selectedSite ? '向下探索' : '选择温室' }}</span>
        <ChevronDown :size="17" />
      </button>

      <div class="home-scroll-progress" :style="{ transform: `scaleX(${scrollProgress})` }"></div>
    </section>

    <main class="home-story">
      <section id="environment" class="home-story-section home-story-section--light" data-reveal>
        <div class="home-story-heading">
          <span>环境感知</span>
          <h2>每一个变化，<br />都在被看见。</h2>
          <p>温湿度、光照、CO₂ 和根区数据持续进入周期模型，异常变化在形成风险之前就被识别。</p>
        </div>

        <div class="home-environment-grid">
          <article v-for="metric in environmentMetrics" :key="metric.label">
            <span><component :is="metric.icon" :size="18" />{{ metric.label }}</span>
            <strong>{{ metric.value }} <small>{{ metric.unit }}</small></strong>
            <em>{{ metric.note }}</em>
          </article>
        </div>

        <div class="home-story-note">
          <span class="home-story-note__icon"><CircleDot :size="18" /></span>
          <div>
            <strong>8 个采样点持续在线</strong>
            <p>实时数据延迟 1.8 秒，周期数据按 SHA-256 形成哈希链。</p>
          </div>
        </div>
      </section>

      <section id="control" class="home-story-section home-story-section--paper" data-reveal>
        <div class="home-story-heading">
          <span>执行控制</span>
          <h2>环境变化，<br />设备立即响应。</h2>
          <p>策略自动匹配风机、卷帘、补光和滴灌设备。每一条指令都有编号、超时和回执状态。</p>
        </div>

        <div class="home-control-list">
          <button
            v-for="point in controlPoints"
            :key="point.id"
            type="button"
            :class="{ 'is-selected': selectedHotspot.id === point.id }"
            @click="selectHotspot(point); scrollToSection('home-top')"
          >
            <span class="home-control-list__icon">
              <component :is="point.icon" :size="19" />
            </span>
            <span class="home-control-list__name">
              <strong>{{ point.label }}</strong>
              <small>{{ point.description }}</small>
            </span>
            <span class="home-control-list__value">
              <strong>{{ point.value }}</strong>
              <small>{{ point.unit }}</small>
            </span>
            <ArrowRight :size="17" />
          </button>
        </div>
      </section>

      <section id="trust" class="home-story-section home-story-section--dark" data-reveal>
        <div class="home-story-heading">
          <span>可信存证</span>
          <h2>上链的不是文件，<br />是可信的关键摘要。</h2>
          <p>环境周期、电子合格证、一物一码和追溯节点共同形成可验证业务链。</p>
        </div>

        <div class="home-trust-grid">
          <article v-for="step in traceSteps" :key="step.index">
            <span>{{ step.index }}</span>
            <h3>{{ step.title }}</h3>
            <p>{{ step.description }}</p>
          </article>
        </div>

        <div class="home-trust-status">
          <ShieldCheck :size="20" />
          <div>
            <strong>联盟链节点 4 / 4 正常</strong>
            <span>最近区块高度 1,284,602 · 链上回执确认</span>
          </div>
        </div>
      </section>
    </main>

    <footer class="home-footer">
      <div>
        <span class="home-footer__mark"><Leaf :size="16" /></span>
        <span>智慧温室大棚农产品全链条数字化管理平台</span>
      </div>
      <RouterLink to="/production">
        进入业务工作台
        <ArrowRight :size="15" />
      </RouterLink>
    </footer>
  </div>
</template>

<style scoped>
.home-immersive-page {
  min-width: 320px;
  overflow-x: clip;
  color: #171b18;
  background: #f5f7f5;
}

.home-nav {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 24px;
  min-height: 54px;
  padding: 0 clamp(20px, 4vw, 68px);
  color: #eef7f1;
  background: rgb(6 19 14 / 42%);
  border-bottom: 1px solid rgb(222 244 231 / 8%);
  backdrop-filter: blur(16px);
  transition: background-color 180ms ease, border-color 180ms ease;
}

.home-nav.is-scrolled {
  background: rgb(6 19 14 / 88%);
  border-color: rgb(222 244 231 / 14%);
}

.home-nav.is-light {
  color: #1b201d;
  background: rgb(255 255 255 / 88%);
  border-color: rgb(23 27 24 / 9%);
}

.home-nav__brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  width: max-content;
  color: #ffffff;
}

.home-nav.is-light .home-nav__brand {
  color: #1b201d;
}

.home-nav__mark,
.home-footer__mark {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  color: #123225;
  background: #bfe3ca;
  border-radius: 7px;
}

.home-nav__brand strong,
.home-nav__brand small {
  display: block;
}

.home-nav.is-light .home-nav__brand small {
  color: #748078;
}

.home-nav__brand strong {
  font-size: 13px;
  font-weight: 600;
}

.home-nav__brand small {
  margin-top: 1px;
  color: #91aa9d;
  font-size: 8px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.home-nav__links {
  display: flex;
  align-items: center;
  gap: 28px;
}

.home-nav__links button,
.home-nav__links a {
  padding: 0;
  color: #c9d8d0;
  background: transparent;
  border: 0;
  font-size: 11px;
}

.home-nav__links button:hover,
.home-nav__links a:hover {
  color: #ffffff;
}

.home-nav.is-light .home-nav__links button,
.home-nav.is-light .home-nav__links a {
  color: #4f5a53;
}

.home-nav.is-light .home-nav__links button:hover,
.home-nav.is-light .home-nav__links a:hover {
  color: #111512;
}

.home-nav__state {
  display: inline-flex;
  align-items: center;
  justify-self: end;
  gap: 7px;
  color: #b8cbbf;
  font-size: 10px;
}

.home-nav.is-light .home-nav__state {
  color: #5f6b64;
}

.home-online-dot {
  width: 6px;
  height: 6px;
  background: #56c989;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgb(86 201 137 / 10%);
}

.home-hero {
  position: relative;
  min-height: 760px;
  height: 100svh;
  overflow: hidden;
  color: #ffffff;
  background: #06130e;
}

.home-hero__scene {
  position: absolute;
  top: clamp(350px, 37vh, 460px);
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1;
  height: auto;
  transition: top 720ms cubic-bezier(0.2, 0.72, 0.2, 1);
}

.home-hero.is-ad-dismissed .home-hero__scene {
  top: 0;
}

.home-hero__copy {
  position: absolute;
  top: clamp(76px, 8.8vh, 106px);
  left: 50%;
  z-index: 3;
  display: grid;
  justify-items: center;
  width: min(860px, calc(100% - 40px));
  padding: 28px 60px 30px;
  text-align: center;
  pointer-events: none;
  background: rgb(10 27 20 / 76%);
  border: 1px solid rgb(185 226 201 / 18%);
  border-radius: 14px;
  box-shadow: 0 24px 80px rgb(0 0 0 / 26%);
  transform: translateX(-50%);
  backdrop-filter: blur(14px);
  animation: home-ad-enter 420ms cubic-bezier(0.2, 0.72, 0.2, 1) both;
  transition: opacity 280ms ease, transform 280ms ease;
}

.home-hero.is-ad-closing .home-hero__copy {
  opacity: 0;
  transform: translateX(-50%) translateY(-18px) scale(0.98);
}

@keyframes home-ad-enter {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-18px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0) scale(1);
  }
}

.home-ad-close {
  position: absolute;
  top: 12px;
  right: 12px;
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  color: #a9c0b4;
  pointer-events: auto;
  background: transparent;
  border: 0;
  border-radius: 50%;
}

.home-ad-close:hover {
  color: #ffffff;
  background: rgb(255 255 255 / 8%);
}

.home-product-label {
  color: #9bd4ae;
  font-size: 12px;
  font-weight: 600;
}

.home-hero__copy h1 {
  margin: 12px 0 0;
  font-size: clamp(40px, 5vw, 72px);
  font-weight: 600;
  line-height: 1.05;
  letter-spacing: -0.045em;
}

.home-hero__copy p {
  max-width: 620px;
  margin: 20px 0 0;
  color: #b7cbc0;
  font-size: clamp(14px, 1.3vw, 18px);
  line-height: 1.7;
}

.home-hero__actions {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-top: 28px;
  pointer-events: auto;
}

.home-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  font-size: 12px;
}

.home-action--primary {
  color: #0d2a1e;
  background: #b9e2c7;
  border: 1px solid #b9e2c7;
}

.home-action--primary:hover {
  background: #c8ebd3;
}

.home-action--secondary {
  color: #e0ede5;
  background: transparent;
  border: 1px solid rgb(218 239 226 / 48%);
}

.home-action--secondary:hover {
  border-color: #d9ecdf;
  background: rgb(255 255 255 / 5%);
}

.home-hero__metrics {
  position: absolute;
  inset: 0;
  z-index: 4;
  pointer-events: none;
}

.home-hero-metric {
  position: absolute;
  display: grid;
  gap: 3px;
  width: 172px;
  padding: 11px 13px;
  color: #eef7f1;
  text-align: left;
  pointer-events: auto;
  background: rgb(10 29 21 / 72%);
  border: 1px solid rgb(185 226 201 / 18%);
  border-radius: 8px;
  backdrop-filter: blur(12px);
}

.home-hero-metric:hover {
  background: rgb(15 43 30 / 88%);
  border-color: rgb(185 226 201 / 38%);
}

.home-hero-metric.is-one {
  top: 44%;
  left: clamp(22px, 4vw, 70px);
}

.home-hero-metric.is-two {
  top: 59%;
  left: clamp(22px, 6vw, 100px);
}

.home-hero-metric.is-three {
  top: 45%;
  right: clamp(22px, 4vw, 70px);
}

.home-hero-metric.is-four {
  top: 60%;
  right: clamp(22px, 6vw, 100px);
}

.home-hero-metric > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #a9c0b4;
  font-size: 10px;
}

.home-hero-metric strong {
  font-size: 22px;
  font-weight: 600;
}

.home-hero-metric small {
  color: #a9c0b4;
  font-size: 10px;
  font-weight: 400;
}

.home-hero-metric em {
  color: #89a196;
  font-size: 9px;
  font-style: normal;
}

.home-hero__controls {
  position: absolute;
  bottom: 23px;
  left: 50%;
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 8px;
  transform: translateX(-50%);
}

.home-park-return {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 13px;
  color: #d9e8df;
  background: rgb(9 25 18 / 78%);
  border: 1px solid rgb(185 226 201 / 20%);
  border-radius: 999px;
  font-size: 11px;
  backdrop-filter: blur(14px);
}

.home-park-return:hover {
  color: #0e2c1f;
  background: #b9e2c7;
}

.home-active-site {
  position: absolute;
  top: 82px;
  left: 50%;
  z-index: 5;
  display: grid;
  justify-items: center;
  padding: 8px 14px;
  color: #e8f2ec;
  background: rgb(9 25 18 / 62%);
  border: 1px solid rgb(185 226 201 / 14%);
  border-radius: 8px;
  transform: translateX(-50%);
  backdrop-filter: blur(10px);
}

.home-active-site span,
.home-active-site small {
  color: #8fa79a;
  font-size: 9px;
}

.home-active-site strong {
  margin: 2px 0;
  font-size: 13px;
  font-weight: 600;
}

.scene-swap-enter-active,
.scene-swap-leave-active {
  transition: opacity 420ms ease, transform 420ms ease;
}

.scene-swap-enter-from {
  opacity: 0;
  transform: scale(1.025);
}

.scene-swap-leave-to {
  opacity: 0;
  transform: scale(0.985);
}

.home-layer-switch {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  background: rgb(9 25 18 / 78%);
  border: 1px solid rgb(185 226 201 / 20%);
  border-radius: 999px;
  backdrop-filter: blur(14px);
}

.home-layer-switch button,
.home-round-control {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  color: #b7cbc0;
  background: transparent;
  border: 0;
  border-radius: 999px;
  font-size: 11px;
}

.home-layer-switch button {
  padding: 0 12px;
}

.home-layer-switch button.is-active {
  color: #0e2c1f;
  background: #b9e2c7;
}

.home-round-control {
  width: auto;
  min-width: 42px;
  padding: 0 11px;
  background: rgb(9 25 18 / 78%);
  border: 1px solid rgb(185 226 201 / 20%);
  backdrop-filter: blur(14px);
}

.home-round-control__label {
  white-space: nowrap;
}

.home-round-control.is-active {
  color: #0e2c1f;
  background: #b9e2c7;
}

.home-hotspot-popover {
  position: absolute;
  right: clamp(22px, 4vw, 70px);
  bottom: 88px;
  z-index: 7;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 11px;
  width: min(360px, calc(100% - 44px));
  padding: 14px;
  color: #eaf4ee;
  background: rgb(9 25 18 / 94%);
  border: 1px solid rgb(185 226 201 / 25%);
  border-radius: 8px;
  box-shadow: 0 18px 60px rgb(0 0 0 / 32%);
  backdrop-filter: blur(18px);
}

.home-hotspot-popover__close {
  position: absolute;
  top: 8px;
  right: 8px;
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  color: #abc0b4;
  background: transparent;
  border: 0;
  border-radius: 7px;
}

.home-hotspot-popover__icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  color: #0d2a1e;
  background: #b9e2c7;
  border-radius: 8px;
}

.home-hotspot-popover span,
.home-hotspot-popover strong,
.home-hotspot-popover b {
  display: block;
}

.home-hotspot-popover div > span {
  color: #91a99c;
  font-size: 9px;
}

.home-hotspot-popover div > strong {
  margin-top: 2px;
  font-size: 13px;
}

.home-hotspot-popover b {
  margin-top: 7px;
  color: #bfe3ca;
  font-size: 18px;
  font-weight: 600;
}

.home-hotspot-popover p {
  margin: 6px 24px 0 0;
  color: #9eb4a8;
  font-size: 10px;
  line-height: 1.55;
}

.home-scroll-cue {
  position: absolute;
  bottom: 23px;
  left: clamp(22px, 4vw, 70px);
  z-index: 6;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #8ea79a;
  background: transparent;
  border: 0;
  font-size: 10px;
}

.home-scroll-progress {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 8;
  height: 2px;
  background: #8fd4a8;
  transform-origin: left center;
}

.home-story-section {
  display: grid;
  grid-template-columns: minmax(280px, 0.8fr) minmax(0, 1.25fr);
  gap: clamp(36px, 7vw, 110px);
  align-items: center;
  min-height: 680px;
  padding: clamp(70px, 9vw, 130px) clamp(24px, 7vw, 118px);
  opacity: 1;
  transform: translateY(0);
}

.home-story-section.is-visible {
  animation: home-section-reveal 720ms cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

@keyframes home-section-reveal {
  from {
    opacity: 0;
    transform: translateY(30px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.home-story-section--light {
  color: #171b18;
  background: #ffffff;
}

.home-story-section--paper {
  color: #171b18;
  background: #f1f4f1;
}

.home-story-section--dark {
  color: #edf6f0;
  background: #07140f;
}

.home-story-heading > span {
  color: #1d7a58;
  font-size: 12px;
  font-weight: 600;
}

.home-story-section--dark .home-story-heading > span {
  color: #8fd4a8;
}

.home-story-heading h2 {
  margin: 12px 0 0;
  font-size: clamp(34px, 4.2vw, 64px);
  font-weight: 600;
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.home-story-heading p {
  max-width: 500px;
  margin: 20px 0 0;
  color: #6b746e;
  font-size: 14px;
  line-height: 1.75;
}

.home-story-section--dark .home-story-heading p {
  color: #9bafa4;
}

.home-environment-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid #dfe5e1;
  border-left: 1px solid #dfe5e1;
}

.home-environment-grid article {
  min-height: 150px;
  padding: 24px;
  border-right: 1px solid #dfe5e1;
  border-bottom: 1px solid #dfe5e1;
}

.home-environment-grid span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #69736d;
  font-size: 12px;
}

.home-environment-grid strong {
  display: block;
  margin-top: 17px;
  font-size: clamp(30px, 3vw, 46px);
  font-weight: 600;
}

.home-environment-grid small {
  color: #69736d;
  font-size: 12px;
  font-weight: 400;
}

.home-environment-grid em {
  display: block;
  margin-top: 6px;
  color: #88918b;
  font-size: 10px;
  font-style: normal;
}

.home-story-note {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: -68px;
  padding-top: 22px;
  border-top: 1px solid #dfe5e1;
}

.home-story-note__icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  color: #1d7a58;
  background: #e7f3ec;
  border-radius: 8px;
}

.home-story-note strong {
  font-size: 12px;
}

.home-story-note p {
  margin: 4px 0 0;
  color: #78827c;
  font-size: 10px;
}

.home-control-list {
  display: grid;
  border-top: 1px solid #d8dfda;
}

.home-control-list button {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto 20px;
  align-items: center;
  gap: 14px;
  min-height: 76px;
  padding: 12px 4px;
  color: #202622;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #d8dfda;
}

.home-control-list button:hover,
.home-control-list button.is-selected {
  padding-right: 12px;
  padding-left: 12px;
  background: #ffffff;
}

.home-control-list__icon {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  color: #1d7a58;
  background: #e5f1e9;
  border-radius: 8px;
}

.home-control-list__name strong,
.home-control-list__name small,
.home-control-list__value strong,
.home-control-list__value small {
  display: block;
}

.home-control-list__name strong {
  font-size: 13px;
}

.home-control-list__name small {
  max-width: 360px;
  margin-top: 4px;
  overflow: hidden;
  color: #78827c;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-control-list__value {
  text-align: right;
}

.home-control-list__value strong {
  font-size: 13px;
}

.home-control-list__value small {
  margin-top: 3px;
  color: #78827c;
  font-size: 9px;
}

.home-trust-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  border-top: 1px solid rgb(191 226 204 / 22%);
  border-left: 1px solid rgb(191 226 204 / 22%);
}

.home-trust-grid article {
  min-height: 210px;
  padding: 24px;
  border-right: 1px solid rgb(191 226 204 / 22%);
  border-bottom: 1px solid rgb(191 226 204 / 22%);
}

.home-trust-grid article > span {
  color: #79c794;
  font-size: 12px;
}

.home-trust-grid h3 {
  margin: 38px 0 0;
  font-size: 16px;
  font-weight: 500;
}

.home-trust-grid p {
  margin: 12px 0 0;
  color: #93a99d;
  font-size: 11px;
  line-height: 1.7;
}

.home-trust-status {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: -68px;
  padding-top: 22px;
  color: #8fd4a8;
  border-top: 1px solid rgb(191 226 204 / 22%);
}

.home-trust-status strong,
.home-trust-status span {
  display: block;
}

.home-trust-status strong {
  font-size: 12px;
}

.home-trust-status span {
  margin-top: 4px;
  color: #82988c;
  font-size: 10px;
}

.home-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 84px;
  padding: 20px clamp(24px, 7vw, 118px);
  color: #8fa499;
  background: #07140f;
  border-top: 1px solid rgb(191 226 204 / 16%);
  font-size: 10px;
}

.home-footer > div,
.home-footer > a {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.home-footer > a {
  color: #dcebe2;
}

.home-footer__mark {
  width: 28px;
  height: 28px;
}

@media (max-width: 1040px) {
  .home-nav {
    grid-template-columns: 1fr auto;
  }

  .home-nav__links {
    display: none;
  }

  .home-hero-metric {
    width: 150px;
  }

  .home-hero-metric.is-two {
    left: 32px;
  }

  .home-hero-metric.is-four {
    right: 32px;
  }

  .home-story-section {
    grid-template-columns: 1fr;
    gap: 48px;
  }

  .home-story-note,
  .home-trust-status {
    margin-top: 0;
  }

  .home-trust-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .home-nav {
    min-height: 58px;
    padding: 0 18px;
  }

  .home-nav__state span:last-child {
    display: none;
  }

  .home-hero {
    min-height: 860px;
  }

  .home-hero__copy {
    top: 76px;
    width: calc(100% - 28px);
    padding: 24px 44px 24px 18px;
  }

  .home-hero__scene {
    top: 46%;
  }

  .home-hero__copy h1 {
    font-size: clamp(40px, 12vw, 56px);
  }

  .home-hero__copy p {
    max-width: 520px;
    font-size: 14px;
  }

  .home-hero__actions {
    width: min(420px, 100%);
    flex-direction: column;
  }

  .home-action {
    width: 100%;
  }

  .home-hero-metric {
    width: 132px;
    padding: 9px 10px;
  }

  .home-hero-metric.is-one {
    top: 63%;
    left: 16px;
  }

  .home-hero-metric.is-two {
    top: 73%;
    left: 16px;
  }

  .home-hero-metric.is-three {
    top: 63%;
    right: 16px;
  }

  .home-hero-metric.is-four {
    top: 73%;
    right: 16px;
  }

  .home-hero-metric strong {
    font-size: 17px;
  }

  .home-hero__controls {
    bottom: 18px;
    width: calc(100% - 30px);
    justify-content: center;
    flex-wrap: wrap;
  }

  .home-layer-switch {
    order: 2;
    width: 100%;
    justify-content: center;
  }

  .home-layer-switch button {
    flex: 1;
  }

  .home-round-control {
    width: 42px;
    padding: 0;
  }

  .home-round-control__label {
    display: none;
  }

  .home-hotspot-popover {
    right: 16px;
    bottom: 112px;
  }

  .home-scroll-cue {
    display: none;
  }

  .home-story-section {
    min-height: auto;
    padding: 74px 20px;
  }

  .home-story-heading h2 {
    font-size: clamp(34px, 10vw, 48px);
  }

  .home-environment-grid {
    grid-template-columns: 1fr;
  }

  .home-control-list button {
    grid-template-columns: 40px minmax(0, 1fr) auto;
  }

  .home-control-list button > svg {
    display: none;
  }

  .home-control-list__name small {
    white-space: normal;
  }

  .home-trust-grid {
    grid-template-columns: 1fr;
  }

  .home-trust-grid article {
    min-height: 160px;
  }

  .home-footer {
    align-items: flex-start;
    flex-direction: column;
    padding: 24px 20px;
  }
}

@media (max-height: 720px) and (min-width: 721px) {
  .home-hero__copy {
    top: 70px;
  }

  .home-hero__copy h1 {
    font-size: 54px;
  }

  .home-hero__scene {
    top: 330px;
  }

  .home-hero__copy p {
    margin-top: 14px;
  }

  .home-hero__actions {
    margin-top: 18px;
  }
}
</style>
