import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useSessionStore } from './stores/session'
import './styles/base.css'
import './styles/tech-theme.css'
import './styles/art-admin-theme.css'
import './styles/soft-admin-theme.css'
import './styles/glass-mint-theme.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
useSessionStore(pinia)
app.use(router)
app.mount('#app')
