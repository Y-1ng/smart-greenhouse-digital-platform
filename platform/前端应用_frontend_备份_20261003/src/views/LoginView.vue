<script setup lang="ts">
import { ArrowRight, Leaf, LockKeyhole, ShieldCheck, UserRound } from 'lucide-vue-next'
import { computed, ref } from 'vue'
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
const redirectTarget = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/')
    ? redirect
    : '/production'
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
    <section class="login-context">
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
</style>
