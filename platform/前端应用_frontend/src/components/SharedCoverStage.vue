<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import '@/cover/styles.scoped.css'

const rootRef = ref<HTMLElement | null>(null)

onMounted(async () => {
  await nextTick()
  if (rootRef.value) {
    await import('@/cover/main.js')
  }
})
</script>

<template>
  <div ref="rootRef" class="cover-app" data-app-cover>
    <div id="cover" class="cover" tabindex="0">
      <canvas id="scene" class="cover__canvas"></canvas>
      <div class="cover__grade" aria-hidden="true"></div>
      <div class="cover__grid" aria-hidden="true"></div>
      <div class="cover__scan" aria-hidden="true"></div>
      <div id="sweep" class="cover__sweep" aria-hidden="true"></div>

      <header class="topbar">
        <div class="brand">
          <span class="brand__mark" aria-hidden="true">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"
              />
              <path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12" />
            </svg>
          </span>
          <span class="brand__text">
            <strong>智慧温室</strong>
            <em>FULL-CHAIN DIGITAL PLATFORM</em>
          </span>
        </div>

        <nav id="topnav" class="topnav" aria-label="章节导航"></nav>

        <div class="topbar__aside">
          <span class="topbar__status"><i></i>园区在线 · 6 座温室</span>
          <a class="topbar__cta" href="/production/environment">进入平台</a>
        </div>
      </header>

      <ol id="rail" class="rail" aria-hidden="true"></ol>
      <main id="deck" class="deck"></main>
      <div id="hotspots" class="hotspots"></div>
      <aside id="concept" class="concept" hidden></aside>

      <footer class="dock">
        <div class="dock__meta">
          <span id="dockIndex" class="dock__index">01</span>
          <span class="dock__divider"></span>
          <span class="dock__total">05</span>
          <span id="dockTag" class="dock__tag"></span>
        </div>

        <div id="dots" class="dots" role="tablist" aria-label="场景切换"></div>

        <div class="dock__cue">
          <span class="dock__wheel" aria-hidden="true"><i></i></span>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.cover-app {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background: #071d17;
}

.cover-app.is-home-layer {
  z-index: 10;
  pointer-events: auto;
}

.cover-app.is-login-layer {
  z-index: 0;
}
</style>
