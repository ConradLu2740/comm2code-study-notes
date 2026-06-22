import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'
import './style.css'

// 应用入口
// 教学点：
// 1. createPinia() 创建全局状态管理器
// 2. app.use(ElementPlus) 全局注册 Element Plus 组件
// 3. locale: zhCn 让 Element Plus 组件文案变中文
// 4. 启动时把 localStorage 里的 token 灌进 Pinia，避免刷新后丢失登录态
const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 启动时恢复登录态（在 pinia 挂载之后才能 use store）
const userStore = useUserStore()
userStore.restore()

app.mount('#app')