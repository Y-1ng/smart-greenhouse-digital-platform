import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import LoginView from '@/views/LoginView.vue'

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
      component: LoginView,
      meta: {
        public: true,
        title: '账号登录',
        subtitle: '生产、质量、合作和治理权限入口',
      },
    },
    {
      path: '/production',
      redirect: '/production/environment',
    },
    {
      path: '/production/environment',
      name: 'production-environment',
      component: () => import('@/views/ProductionView.vue'),
      meta: {
        module: 'production',
        section: 'environment',
        title: '实时环境',
        subtitle: '实时遥测、趋势曲线和阈值状态',
      },
    },
    {
      path: '/production/twin',
      name: 'production-twin',
      component: () => import('@/views/ProductionView.vue'),
      meta: {
        module: 'production',
        section: 'twin',
        title: '三维大棚',
        subtitle: '数字孪生、空间热点与设备控制',
      },
    },
    {
      // 设备控制已并入三维大棚：场景里的设备热点、右侧执行机架和开关指令都在同一页，
      // 旧地址保留重定向，避免书签和外部链接失效。
      path: '/production/devices',
      redirect: '/production/twin',
    },
    {
      path: '/production/water',
      name: 'production-water',
      component: () => import('@/views/ProductionView.vue'),
      meta: {
        module: 'production',
        section: 'water',
        title: '水肥计划',
        subtitle: '配方、排程、执行和作物批次',
      },
    },
    {
      path: '/production/chain',
      name: 'production-chain',
      component: () => import('@/views/ProductionView.vue'),
      meta: {
        module: 'production',
        section: 'chain',
        title: '周期存证',
        subtitle: '环境周期根哈希和联盟链回执',
      },
    },
    {
      // 消费者扫码落地页：公开只读，只展示可公开的核验信息，不进入内部工作台
      path: '/trace',
      name: 'public-trace',
      component: () => import('@/views/PublicTraceView.vue'),
      meta: {
        public: true,
        title: '溯源码查询',
        subtitle: '扫码核验产品来源、检测与链上存证',
      },
    },
    {
      path: '/traceability',
      redirect: '/traceability/query',
    },
    {
      path: '/traceability/query',
      name: 'traceability-query',
      component: () => import('@/views/TraceView.vue'),
      meta: {
        module: 'traceability',
        section: 'query',
        title: '追溯查询',
        subtitle: '产品编码、田块编号和扫码核验',
      },
    },
    {
      path: '/traceability/report',
      name: 'traceability-report',
      component: () => import('@/views/TraceView.vue'),
      meta: {
        module: 'traceability',
        section: 'report',
        title: '质量报告',
        subtitle: '检测报告备案、摘要和原文件',
      },
    },
    {
      path: '/traceability/evidence',
      name: 'traceability-evidence',
      component: () => import('@/views/TraceView.vue'),
      meta: {
        module: 'traceability',
        section: 'evidence',
        title: '链上证据',
        subtitle: '链上回执、哈希指针链和追加节点',
      },
    },
    {
      path: '/traceability/management',
      name: 'traceability-management',
      component: () => import('@/views/TraceView.vue'),
      meta: {
        module: 'traceability',
        section: 'management',
        title: '质量台账',
        subtitle: '合格证备案和一物一码生成',
      },
    },
    {
      path: '/cooperation',
      redirect: '/cooperation/metrics',
    },
    {
      path: '/cooperation/metrics',
      name: 'cooperation-metrics',
      component: () => import('@/views/CooperationView.vue'),
      meta: {
        module: 'cooperation',
        section: 'metrics',
        title: '经营指标',
        subtitle: '合作村庄、土地、收益和确认状态',
      },
    },
    {
      path: '/cooperation/equity',
      name: 'cooperation-equity',
      component: () => import('@/views/CooperationView.vue'),
      meta: {
        module: 'cooperation',
        section: 'equity',
        title: '股权分红',
        subtitle: '股权结构、分红分布和经营洞察',
      },
    },
    {
      path: '/cooperation/orders',
      name: 'cooperation-orders',
      component: () => import('@/views/CooperationView.vue'),
      meta: {
        module: 'cooperation',
        section: 'orders',
        title: '合同订单',
        subtitle: '村庄入股、合作项目和经营底账',
      },
    },
    {
      path: '/governance',
      redirect: '/governance/metrics',
    },
    {
      path: '/governance/metrics',
      name: 'governance-metrics',
      component: () => import('@/views/GovernanceView.vue'),
      meta: {
        module: 'governance',
        section: 'metrics',
        title: '监管指标',
        subtitle: '产业运行指标、风险矩阵和项目进度',
      },
    },
    {
      path: '/governance/chain',
      name: 'governance-chain',
      component: () => import('@/views/GovernanceView.vue'),
      meta: {
        module: 'governance',
        section: 'chain',
        title: '可信治理',
        subtitle: '链上固证、待补偿交易和合约状态',
      },
    },
    {
      path: '/governance/alerts',
      name: 'governance-alerts',
      component: () => import('@/views/GovernanceView.vue'),
      meta: {
        module: 'governance',
        section: 'alerts',
        title: '风险预警',
        subtitle: '风险识别、预警队列和处置闭环',
      },
    },
    {
      path: '/governance/external',
      name: 'governance-external',
      component: () => import('@/views/GovernanceView.vue'),
      meta: {
        module: 'governance',
        section: 'external',
        title: '外部协同',
        subtitle: '外部平台通道、同步记录和调用留痕',
      },
    },
    {
      // AI 总结：与生产指挥、质量溯源同级的一级模块
      path: '/ai',
      redirect: '/ai/summary',
    },
    {
      path: '/ai/summary',
      name: 'ai-summary',
      component: () => import('@/views/AiSummaryView.vue'),
      meta: {
        module: 'ai',
        section: 'summary',
        title: 'AI 总结',
        subtitle: '按业务框架归纳指标、证据与风险',
      },
    },
    {
      path: '/ai/chat',
      name: 'ai-chat',
      component: () => import('@/views/AiSummaryView.vue'),
      meta: {
        module: 'ai',
        section: 'chat',
        title: 'AI 问答',
        subtitle: '基于平台实时数据的多轮问答',
      },
    },
    {
      path: '/ai/frameworks',
      name: 'ai-frameworks',
      component: () => import('@/views/AiSummaryView.vue'),
      meta: {
        module: 'ai',
        section: 'frameworks',
        title: '分析框架',
        subtitle: '四类分析角度与各自的输出维度',
      },
    },
    {
      path: '/ai/history',
      name: 'ai-history',
      component: () => import('@/views/AiSummaryView.vue'),
      meta: {
        module: 'ai',
        section: 'history',
        title: '生成历史',
        subtitle: '当前会话内已生成的结论文本',
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
