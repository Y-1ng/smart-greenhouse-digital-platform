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
  } else if (import.meta.env.VITE_API_USERNAME && import.meta.env.VITE_API_PASSWORD) {
    config.auth = {
      username: import.meta.env.VITE_API_USERNAME,
      password: import.meta.env.VITE_API_PASSWORD,
    }
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (
      error.response?.status === 401
      && !String(error.config?.url ?? '').includes('/auth/login')
    ) {
      window.localStorage.removeItem('smart-greenhouse-token')
      window.localStorage.removeItem('smart-greenhouse-user')
      if (window.location.pathname !== '/login') {
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
