import axios from 'axios'
import { ElMessage } from 'element-plus'

// Axios 实例
// 教学点：
// 1. baseURL 留空：所有请求都写 /api/v1/xxx，相对路径，
//    开发时通过 vite proxy 转发到 8080，部署时 nginx 改一下即可。
// 2. timeout 不要设太短（10s 起步），Spring Boot 启动慢时偶尔超过 5s。
// 3. 拦截器是 axios 最强大的特性：
//    - 请求拦截器：自动加 Authorization 头
//    - 响应拦截器：401 跳登录、统一错误提示

const http = axios.create({
  baseURL: '',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// ============ 请求拦截器 ============
http.interceptors.request.use(
  (config) => {
    // 从 localStorage 取 token（注意：不要从 Pinia store 直接读，
    // 因为拦截器里的 this 指向 axios 而不是 Vue 组件）
    const token = localStorage.getItem('shortlink_admin:token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ============ 响应拦截器 ============
http.interceptors.response.use(
  (response) => {
    // 后端直接返回 JSON，没有统一的 { code, data } 包装
    // 所以直接 return response.data，由调用方解构
    return response.data
  },
  (error) => {
    const status = error.response?.status
    const msg = error.response?.data?.message || error.response?.data?.error || error.message

    if (status === 401) {
      // token 过期或无效
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('shortlink_admin:token')
      localStorage.removeItem('shortlink_admin:user')
      // 用 location 强制跳，避免在 Pinia 里循环依赖
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    } else if (status === 429) {
      ElMessage.warning('请求过于频繁，请稍后再试')
    } else if (status >= 500) {
      ElMessage.error(`服务器开小差：${msg || status}`)
    } else {
      ElMessage.error(msg || '请求失败')
    }
    return Promise.reject(error)
  },
)

// ============ API 封装 ============

/** POST /api/v1/auth/login */
export function login(data) {
  return http.post('/api/v1/auth/login', data)
}

/** POST /api/v1/auth/register */
export function register(data) {
  return http.post('/api/v1/auth/register', data)
}

/** GET /api/v1/users/me */
export function getMe() {
  return http.get('/api/v1/users/me')
}

/** POST /api/v1/shortlinks */
export function createShortLink(data) {
  return http.post('/api/v1/shortlinks', data)
}

/** GET /api/v1/shortlinks/{code} */
export function getShortLinkInfo(code) {
  return http.get(`/api/v1/shortlinks/${encodeURIComponent(code)}`)
}

/** GET /api/v1/shortlinks/{code}/stats */
export function getShortLinkStats(code) {
  return http.get(`/api/v1/shortlinks/${encodeURIComponent(code)}/stats`)
}

/** GET /api/v1/shortlinks/{code}/visits */
export function getShortLinkVisits(code) {
  return http.get(`/api/v1/shortlinks/${encodeURIComponent(code)}/visits`)
}

/** GET /api/v1/domains?userId=xxx */
export function listDomains(userId) {
  return http.get('/api/v1/domains', { params: { userId } })
}

/** POST /api/v1/domains?userId=xxx */
export function bindDomain(userId, data) {
  return http.post('/api/v1/domains', data, { params: { userId } })
}

/** DELETE /api/v1/domains/{id}?userId=xxx */
export function unbindDomain(id, userId) {
  return http.delete(`/api/v1/domains/${id}`, { params: { userId } })
}

/** POST /api/v1/domains/{domain}/verify */
export function verifyDomain(domain) {
  return http.post(`/api/v1/domains/${encodeURIComponent(domain)}/verify`)
}

export default http