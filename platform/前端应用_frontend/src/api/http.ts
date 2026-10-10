import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use((config) => {
  const token = window.localStorage.getItem('smart-greenhouse-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 这里曾经在无 JWT 时回退到 .env 里的 Basic 凭据。那样会把 admin 账号的
  // 用户名密码带给所有请求，包括公开接口（消费者扫码页查 /api/trace/{code}）：
  // Spring Security 只要收到无效 Basic 就直接 401，连 permitAll 的接口也不例外，
  // 结果匿名访客被弹到登录页。应用本身走 JWT，登录接口用请求体传账号，无需这个回退。
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const requestUrl = String(error.config?.url ?? '')
    // 401 之前是否处于登录态；匿名访问公开接口被拒时不应把页面跳走
    const hadToken = Boolean(
      window.localStorage.getItem('smart-greenhouse-token'),
    )
    if (
      error.response?.status === 401
      && !requestUrl.includes('/auth/login')
    ) {
      window.localStorage.removeItem('smart-greenhouse-token')
      window.localStorage.removeItem('smart-greenhouse-user')
      if (hadToken && window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(
          `${window.location.pathname}${window.location.search}`,
        )
        window.location.assign(`/login?redirect=${redirect}`)
      }
    }
    return Promise.reject(error)
  },
)

export default http
