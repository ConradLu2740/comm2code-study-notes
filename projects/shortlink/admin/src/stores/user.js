import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '../api'
import { setItem, getItem, removeItem } from '../utils/storage'

// 用户 Store
// 教学点：
// 1. Pinia 的 setup 风格：defineStore('user', () => { ... })，内部用 ref/computed
//    比 Vuex 的 options 风格更灵活，TS 推断也好。
// 2. state 在 store 里，持久化在 storage.js 里手动同步（Pinia 也有插件但这里简单起见手写）
// 3. computed 派生 isLoggedIn：避免每个 view 都要写 !!token.value

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const user = ref(null)
  const expiresAt = ref(0) // 毫秒时间戳

  const isLoggedIn = computed(() => !!token.value && Date.now() < expiresAt.value)

  /** 恢复登录态（main.js 启动时调用） */
  function restore() {
    const t = localStorage.getItem('shortlink_admin:token')
    const u = getItem('user', null)
    const exp = Number(localStorage.getItem('shortlink_admin:expiresAt') || 0)
    if (t && u && Date.now() < exp) {
      token.value = t
      user.value = u
      expiresAt.value = exp
    } else {
      // 过期或不存在则清掉
      clear()
    }
  }

  /** 登录 */
  async function login(username, password) {
    const resp = await api.login({ username, password })
    // 后端 LoginResponse: { token, expiresIn, user }
    token.value = resp.token
    user.value = resp.user
    expiresAt.value = Date.now() + resp.expiresIn * 1000
    // 持久化（注意：localStorage 不能存 ref 对象，要 .value 序列化）
    localStorage.setItem('shortlink_admin:token', resp.token)
    localStorage.setItem('shortlink_admin:expiresAt', String(expiresAt.value))
    setItem('user', resp.user)
    return resp
  }

  /** 登出 */
  function logout() {
    clear()
  }

  function clear() {
    token.value = ''
    user.value = null
    expiresAt.value = 0
    localStorage.removeItem('shortlink_admin:token')
    localStorage.removeItem('shortlink_admin:expiresAt')
    removeItem('user')
  }

  /** 重新拉取用户信息 */
  async function fetchMe() {
    const me = await api.getMe()
    user.value = me
    setItem('user', me)
    return me
  }

  return {
    token,
    user,
    expiresAt,
    isLoggedIn,
    restore,
    login,
    logout,
    fetchMe,
  }
})