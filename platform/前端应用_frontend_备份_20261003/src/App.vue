<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  Factory,
  Home,
  Landmark,
  Leaf,
  LogOut,
  Menu,
  ScanLine,
  UserRound,
  Users,
  X,
} from 'lucide-vue-next'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const sidebarOpen = ref(false)

const navigation = [
  { to: '/', label: '首页', icon: Home },
  { to: '/production', label: '生产指挥', icon: Factory },
  { to: '/traceability', label: '质量溯源', icon: ScanLine },
  { to: '/cooperation', label: '合作运营', icon: Users },
  { to: '/governance', label: '治理决策', icon: Landmark },
]

const pageTitle = computed(() => String(route.meta.title ?? '智慧温室'))
const pageSubtitle = computed(() => String(route.meta.subtitle ?? '数字化管理平台'))
const isStandalonePage = computed(
  () => route.name === 'home' || route.name === 'login',
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
  void router.push({ name: 'login' })
}
</script>

<template>
  <div v-if="isStandalonePage" class="immersive-shell">
    <RouterView />
  </div>

  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ 'sidebar--open': sidebarOpen }">
      <div class="brand">
        <div class="brand__mark">
          <Leaf :size="22" />
        </div>
        <div>
          <strong>智慧温室</strong>
          <span>全链条数字化平台</span>
        </div>
      </div>

      <nav class="navigation" aria-label="主导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.to"
          class="navigation__item"
          :to="item.to"
          @click="sidebarOpen = false"
        >
          <component :is="item.icon" :size="19" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar__status">
        <span class="status-dot status-dot--online"></span>
        <div>
          <strong>{{ session.displayName }}</strong>
          <span>{{ roleLabel || '已登录' }}</span>
        </div>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <button class="icon-button topbar__menu" type="button" aria-label="打开导航" @click="sidebarOpen = !sidebarOpen">
          <X v-if="sidebarOpen" :size="20" />
          <Menu v-else :size="20" />
        </button>
        <div>
          <p class="eyebrow">寒旱区设施农业示范基地</p>
          <h1>{{ pageTitle }}</h1>
          <span class="topbar__subtitle">{{ pageSubtitle }}</span>
        </div>
        <div class="topbar__meta">
          <span class="topbar__user">
            <UserRound :size="16" />
            {{ session.displayName }} · {{ roleLabel }}
          </span>
          <button class="topbar__logout" type="button" @click="signOut">
            <LogOut :size="16" />
            退出
          </button>
        </div>
      </header>

      <section class="page-content">
        <RouterView v-slot="{ Component, route }">
          <KeepAlive :include="['TraceView']">
            <component :is="Component" :key="route.name" />
          </KeepAlive>
        </RouterView>
      </section>
    </main>
  </div>
</template>
