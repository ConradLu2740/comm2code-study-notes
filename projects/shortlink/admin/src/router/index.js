import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

// 路由表
// 教学点：
// 1. 懒加载：() => import('../views/xxx.vue') 让每个 view 拆成独立 chunk，
//    首次访问某页才下载该 JS，首屏更快。
// 2. meta.requiresAuth：标记哪些路由需要登录。
// 3. meta.title：浏览器标签页标题。

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { requiresAuth: false, title: '登录' },
  },
  {
    path: '/',
    component: () => import('../components/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'shortlinks',
        name: 'ShortLinks',
        component: () => import('../views/ShortLinks.vue'),
        meta: { title: '短链管理' },
      },
      {
        path: 'domains',
        name: 'Domains',
        component: () => import('../views/Domains.vue'),
        meta: { title: '域名管理' },
      },
      {
        path: 'stats',
        name: 'Stats',
        component: () => import('../views/Stats.vue'),
        meta: { title: '访问统计' },
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/Users.vue'),
        meta: { title: '个人信息' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// ============ 导航守卫 ============
// 教学点：
// - to.meta.requiresAuth === true 且未登录 → 跳 /login，并把原路径记在 query.from
// - 已登录但访问 /login → 跳首页
// - 守卫里的 store 必须在 pinia 挂载之后调用，所以从 useUserStore() 拿实例
router.beforeEach((to, from, next) => {
  // 标题
  if (to.meta.title) {
    document.title = `${to.meta.title} · 短链接后台`
  } else {
    document.title = '短链接后台'
  }

  const userStore = useUserStore()

  if (to.meta.requiresAuth === false) {
    // 白名单页面（登录）
    if (to.path === '/login' && userStore.isLoggedIn) {
      next('/')
      return
    }
    next()
    return
  }

  // 需要登录的页面
  if (!userStore.isLoggedIn) {
    next({ path: '/login', query: { from: to.fullPath } })
    return
  }

  next()
})

export default router