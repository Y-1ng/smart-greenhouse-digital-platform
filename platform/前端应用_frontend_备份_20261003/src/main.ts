import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useSessionStore } from './stores/session'
import './styles/base.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
useSessionStore(pinia)
app.use(router)
app.mount('#app')
