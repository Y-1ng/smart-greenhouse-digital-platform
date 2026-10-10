import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: {
        public: true,
        title: '数字孪生首页',
        subtitle: '温室三维场景、环境感知与执行设备总览',
      },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: {
        public: true,
        title: '账号登录',
        subtitle: '生产、质量、合作和治理权限入口',
      },
    },
    {
      path: '/production',
      name: 'production',
      component: () => import('@/views/ProductionView.vue'),
      meta: {
        title: '生产指挥',
        subtitle: '环境、控制、水肥和作物档案',
      },
    },
    {
      path: '/traceability',
      name: 'traceability',
      component: () => import('@/views/TraceView.vue'),
      meta: {
        title: '质量溯源',
        subtitle: '合格证、一物一码和链上核验',
      },
    },
    {
      path: '/cooperation',
      name: 'cooperation',
      component: () => import('@/views/CooperationView.vue'),
      meta: {
        title: '合作运营',
        subtitle: '一村一图、股权、合同和分红',
      },
    },
    {
      path: '/governance',
      name: 'governance',
      component: () => import('@/views/GovernanceView.vue'),
      meta: {
        title: '治理决策',
        subtitle: '监管指标、风险预警和产业带动',
      },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: {
        title: '页面不存在',
      },
    },
  ],
})

router.beforeEach((to) => {
  const session = useSessionStore()
  if (to.meta.public === true || session.authenticated) {
    return true
  }
  return {
    name: 'login',
    query: {
      redirect: to.fullPath,
    },
  }
})

export default router
