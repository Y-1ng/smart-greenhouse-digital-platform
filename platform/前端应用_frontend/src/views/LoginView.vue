<script setup lang="ts">
import { ArrowRight, Leaf, LockKeyhole, ShieldCheck, UserRound } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '@/api/modules/auth'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const username = ref('admin')
const password = ref('123')
const loading = ref(false)
const errorMessage = ref('')
const visualReady = ref(false)
let readyFallback: number | undefined

function handleCoverReady() {
  visualReady.value = true
  if (readyFallback) {
    window.clearTimeout(readyFallback)
    readyFallback = undefined
  }
}

onMounted(() => {
  window.addEventListener('greenhouse-cover-ready', handleCoverReady)
  readyFallback = window.setTimeout(() => {
    visualReady.value = true
  }, 2600)
})

onBeforeUnmount(() => {
  window.removeEventListener('greenhouse-cover-ready', handleCoverReady)
  if (readyFallback) {
    window.clearTimeout(readyFallback)
  }
})

watch(
  () => route.name,
  (name) => {
    if (name !== 'login') return
    loading.value = false
    errorMessage.value = ''
  },
)

const redirectTarget = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/')
    ? redirect
    : '/'
})

async function submit() {
  if (!username.value.trim() || !password.value) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const result = await login({
      username: username.value.trim(),
      password: password.value,
    })
    session.setSession(result)
    window.sessionStorage.setItem('smart-greenhouse-cover-arrival', '1')
    await router.replace(redirectTarget.value)
  } catch (error) {
    const responseMessage = (
      error as { response?: { data?: { message?: string } } }
    ).response?.data?.message
    errorMessage.value = responseMessage ?? '登录失败，请检查账号或后端服务'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <div
      class="login-visual"
      :class="{ 'is-ready': visualReady }"
      aria-hidden="true"
    >
      <div class="login-visual__veil"></div>
      <div class="login-visual__loading" :class="{ 'is-hidden': visualReady }">
        <i></i>
        正在加载数字孪生模型
      </div>
    </div>

    <section class="login-context">
      <div class="login-context__content">
        <div class="login-brand">
          <span class="login-brand__mark"><Leaf :size="24" /></span>
          <div>
            <strong>智慧温室</strong>
            <small>全链条数字化管理平台</small>
          </div>
        </div>

        <div class="login-context__body">
          <span class="login-context__eyebrow">寒旱区设施农业示范基地</span>
          <h1>从环境感知到质量溯源的统一工作台</h1>
          <p>生产、质量、合作和治理权限在这里进入各自的工作区域。</p>
        </div>

        <div class="login-demo">
          <div class="login-demo__heading">
            <ShieldCheck :size="17" />
            <span>演示账号</span>
          </div>
          <dl>
            <div><dt>管理员</dt><dd>admin / 123</dd></div>
            <div><dt>操作员</dt><dd>operator / 123</dd></div>
            <div><dt>质检员</dt><dd>quality / 123</dd></div>
          </dl>
        </div>
      </div>
    </section>

    <section class="login-form-panel">
      <form class="login-form" @submit.prevent="submit">
        <div class="login-form__heading">
          <span>账号登录</span>
          <h2>进入管理平台</h2>
          <p>使用已分配的平台账号继续操作。</p>
        </div>

        <label>
          <span>用户名</span>
          <span class="login-input">
            <UserRound :size="17" />
            <input
              v-model="username"
              autocomplete="username"
              placeholder="请输入用户名"
            />
          </span>
        </label>

        <label>
          <span>密码</span>
          <span class="login-input">
            <LockKeyhole :size="17" />
            <input
              v-model="password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </span>
        </label>

        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

        <button class="login-submit" type="submit" :disabled="loading">
          <span>{{ loading ? '正在验证' : '登录平台' }}</span>
          <ArrowRight :size="17" />
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(420px, 1.12fr) minmax(420px, 0.88fr);
  min-height: 100vh;
  color: #17201b;
  background: #f2f5f2;
}

.login-context {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 42px 52px;
  color: #ffffff;
  background: #173f34;
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.login-brand__mark {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  color: #173f34;
  background: #b8dfc5;
  border-radius: 8px;
}

.login-brand strong,
.login-brand small {
  display: block;
}

.login-brand strong {
  font-size: 18px;
}

.login-brand small {
  margin-top: 4px;
  color: #bed0c6;
  font-size: 12px;
}

.login-context__body {
  max-width: 590px;
  margin: auto 0;
  padding: 70px 0;
}

.login-context__eyebrow {
  color: #9dd0af;
  font-size: 13px;
  font-weight: 700;
}

.login-context h1 {
  max-width: 560px;
  margin: 18px 0 0;
  font-size: 46px;
  line-height: 1.16;
  letter-spacing: 0;
}

.login-context__body p {
  max-width: 500px;
  margin: 20px 0 0;
  color: #c9d9d0;
  font-size: 15px;
  line-height: 1.8;
}

.login-demo {
  max-width: 620px;
  padding: 18px 0 0;
  border-top: 1px solid rgb(255 255 255 / 16%);
}

.login-demo__heading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #b8dfc5;
  font-size: 13px;
  font-weight: 700;
}

.login-demo dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0 0;
}

.login-demo dl div {
  min-width: 0;
}

.login-demo dt {
  color: #8eaa9b;
  font-size: 11px;
}

.login-demo dd {
  margin: 5px 0 0;
  overflow: hidden;
  color: #eef5f0;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.login-form-panel {
  display: grid;
  place-items: center;
  padding: 42px;
}

.login-form {
  display: grid;
  gap: 20px;
  width: min(410px, 100%);
  padding: 34px;
  background: #ffffff;
  border: 1px solid #d8e2db;
  border-radius: 8px;
  box-shadow: 0 24px 60px rgb(31 56 43 / 10%);
}

.login-form__heading > span {
  color: #217354;
  font-size: 12px;
  font-weight: 700;
}

.login-form__heading h2 {
  margin: 8px 0 0;
  font-size: 28px;
}

.login-form__heading p {
  margin: 8px 0 0;
  color: #65736c;
  font-size: 13px;
}

.login-form label {
  display: grid;
  gap: 8px;
}

.login-form label > span:first-child {
  color: #43534a;
  font-size: 13px;
  font-weight: 700;
}

.login-input {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 44px;
  padding: 0 12px;
  color: #708078;
  background: #ffffff;
  border: 1px solid #c8d5cc;
  border-radius: 7px;
}

.login-input:focus-within {
  color: #217354;
  border-color: #217354;
  box-shadow: 0 0 0 3px rgb(33 115 84 / 11%);
}

.login-input input {
  width: 100%;
  min-width: 0;
  height: 42px;
  padding: 0;
  color: #17201b;
  background: transparent;
  border: 0;
  outline: 0;
}

.login-error {
  margin: -4px 0 0;
  color: #a14e48;
  font-size: 12px;
}

.login-submit {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 46px;
  padding: 0 16px;
  color: #ffffff;
  background: #217354;
  border: 1px solid #217354;
  border-radius: 7px;
  font-weight: 700;
}

.login-submit:hover {
  background: #195c43;
}

.login-submit:disabled {
  cursor: wait;
  opacity: 0.7;
}

.login-page {
  color: #12241e;
  background:
    radial-gradient(1100px 620px at 6% -10%, #eef6f4 0%, rgb(225 247 238 / 0%) 62%),
    radial-gradient(880px 540px at 98% 4%, #e2f0f1 0%, rgb(220 239 243 / 0%) 58%),
    linear-gradient(158deg, #e7f1ef 0%, #dce9e7 52%, #d5e6e2 100%);
}

.login-context {
  color: #12241e;
  background: rgb(255 255 255 / 42%);
  border-right: 1px solid rgb(255 255 255 / 70%);
  backdrop-filter: blur(18px);
}

.login-brand__mark {
  color: #ffffff;
  background: linear-gradient(145deg, #4fae99, #0e7c6b);
  border-radius: 14px;
  box-shadow: 0 10px 24px rgb(14 124 107 / 24%);
}

.login-brand small,
.login-context__body p {
  color: #4f6b60;
}

.login-context__eyebrow {
  color: #0a6558;
}

.login-form-panel {
  background: transparent;
}

.login-form {
  background: rgb(255 255 255 / 88%);
  border: 1px solid rgb(255 255 255 / 76%);
  border-radius: 24px;
  box-shadow:
    0 24px 60px rgb(9 62 54 / 14%),
    inset 0 1px rgb(255 255 255 / 88%);
  backdrop-filter: blur(22px);
}

.login-form__heading > span {
  color: #0a6558;
}

.login-form__heading h2 {
  color: #12241e;
}

.login-form__heading p {
  color: #5c7a6e;
}

.login-form label > span:first-child {
  color: #3d544b;
}

.login-input {
  color: #6b837a;
  background: rgb(255 255 255 / 86%);
  border: 1px solid rgb(18 36 30 / 8%);
  border-radius: 12px;
}

.login-input:focus-within {
  color: #0a6558;
  border-color: #1e957d;
  box-shadow: 0 0 0 3px rgb(30 149 125 / 18%);
}

.login-input input {
  color: #12241e;
}

.login-error {
  color: #b23b37;
}

.login-submit {
  color: #ffffff;
  background: linear-gradient(140deg, #1e957d, #0e7c6b);
  border-color: transparent;
  border-radius: 14px;
  box-shadow: 0 12px 26px rgb(14 124 107 / 24%);
}

.login-submit:hover {
  background: linear-gradient(140deg, #1e957d, #0a6558);
}

.login-demo {
  border-top-color: rgb(18 36 30 / 10%);
}

.login-demo__heading {
  color: #0a6558;
}

.login-demo dt {
  color: #6b837a;
}

.login-demo dd {
  color: #3d544b;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-context {
    min-height: 44vh;
    padding: 30px;
  }

  .login-context__body {
    padding: 54px 0;
  }

  .login-context h1 {
    font-size: 34px;
  }

  .login-demo {
    display: none;
  }

  .login-form-panel {
    padding: 28px 18px 40px;
  }
}

@media (max-width: 560px) {
  .login-context,
  .login-form {
    padding: 24px;
  }

  .login-context h1 {
    font-size: 29px;
  }

  .login-form__heading h2 {
    font-size: 24px;
  }
}

/* Shared cover scene: the model stays visible behind the sign-in copy. */
.login-page {
  position: relative;
  isolation: isolate;
  background: transparent;
}

.login-context {
  position: relative;
  z-index: 1;
  overflow: hidden;
  color: #f4fff9;
  background: linear-gradient(
    90deg,
    rgb(7 29 23 / 52%),
    rgb(7 29 23 / 8%)
  ) !important;
  border-right: 0 !important;
  backdrop-filter: none !important;
}

.login-visual {
  position: fixed;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
  background: transparent;
  transition:
    filter 900ms cubic-bezier(0.16, 1, 0.3, 1),
    opacity 500ms ease;
}

.login-visual.is-ready {
  opacity: 1;
}

.login-visual__veil {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(
      90deg,
      rgb(7 29 23 / 46%) 0%,
      rgb(7 29 23 / 14%) 48%,
      rgb(7 29 23 / 26%) 100%
    ),
    linear-gradient(180deg, rgb(7 29 23 / 6%), rgb(7 29 23 / 36%));
  transition: opacity 500ms ease;
}

.login-visual__loading {
  position: absolute;
  bottom: 28px;
  left: 32px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #b7d5c7;
  font-size: 11px;
  transition: opacity 260ms ease, transform 260ms ease;
}

.login-visual__loading i {
  width: 7px;
  height: 7px;
  background: #72dfa7;
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgb(114 223 167 / 12%);
  animation: login-model-pulse 1.4s ease-in-out infinite;
}

.login-visual__loading.is-hidden {
  opacity: 0;
  transform: translateY(4px);
}

@keyframes login-model-pulse {
  50% {
    opacity: 0.5;
    transform: scale(0.82);
  }
}

.login-context__content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 100%;
  transition:
    opacity 260ms ease,
    transform 360ms cubic-bezier(0.16, 1, 0.3, 1);
}

.login-brand strong {
  color: #ffffff;
}

.login-brand small,
.login-context__body p {
  color: #bed4c9;
}

.login-context__eyebrow,
.login-demo__heading {
  color: #8fe0b6;
}

.login-context h1 {
  color: #ffffff;
}

.login-demo {
  border-top-color: rgb(255 255 255 / 16%);
}

.login-demo dt {
  color: #8eaa9b;
}

.login-demo dd {
  color: #eef8f3;
}

.login-form-panel {
  position: relative;
  z-index: 1;
  background: linear-gradient(
    135deg,
    rgb(7 29 23 / 8%),
    rgb(7 29 23 / 34%)
  ) !important;
}

.login-form {
  background: linear-gradient(
    180deg,
    rgb(255 255 255 / 90%),
    rgb(240 248 245 / 82%)
  ) !important;
  border-color: rgb(255 255 255 / 50%) !important;
  box-shadow:
    0 30px 70px rgb(0 0 0 / 28%),
    inset 0 1px 0 rgb(255 255 255 / 82%) !important;
  backdrop-filter: none !important;
}

@media (max-width: 900px) {
  .login-context {
    min-height: 50vh;
    padding: 30px;
  }

  .login-visual__veil {
    background:
      linear-gradient(180deg, rgb(7 29 23 / 8%), rgb(7 29 23 / 46%)),
      linear-gradient(90deg, rgb(7 29 23 / 26%), rgb(7 29 23 / 14%));
  }

  .login-form-panel {
    background: linear-gradient(
      180deg,
      rgb(7 29 23 / 14%),
      rgb(7 29 23 / 38%)
    ) !important;
  }
}

@media (max-width: 560px) {
  .login-context {
    min-height: 52vh;
    padding: 24px;
  }

  .login-context__body {
    padding: 38px 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-visual,
  .login-visual__veil,
  .login-context__content,
  .login-form-panel {
    transition: none !important;
  }
}
</style>
