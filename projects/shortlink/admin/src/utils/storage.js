// localStorage 封装
// 教学点：
// 1. 直接用 localStorage 会遇到：
//    - SSR 时没有 localStorage（直接报错）
//    - JSON.parse(null) 也是 null，但 try/catch 包裹更稳
//    - 键名散落在各 view 里不好维护
// 2. 封装一层后，所有 view 通过统一的 API 读写，键名集中管理。

const PREFIX = 'shortlink_admin:'

function k(key) {
  return PREFIX + key
}

export function getItem(key, defaultValue = null) {
  try {
    const raw = localStorage.getItem(k(key))
    if (raw === null || raw === undefined) return defaultValue
    return JSON.parse(raw)
  } catch (e) {
    // JSON.parse 失败、SSR 等情况都走这里
    console.warn('[storage] get failed:', key, e)
    return defaultValue
  }
}

export function setItem(key, value) {
  try {
    localStorage.setItem(k(key), JSON.stringify(value))
  } catch (e) {
    console.warn('[storage] set failed:', key, e)
  }
}

export function removeItem(key) {
  try {
    localStorage.removeItem(k(key))
  } catch (e) {
    console.warn('[storage] remove failed:', key, e)
  }
}

// 存储"我创建过的短链"
const MY_LINKS_KEY = 'my_shortlinks'

/** 获取我创建过的短链列表 */
export function getMyLinks() {
  return getItem(MY_LINKS_KEY, [])
}

/** 追加一条创建记录 */
export function pushMyLink(link) {
  const list = getMyLinks()
  // 去重：相同 shortCode 不重复存
  if (!list.find((it) => it.shortCode === link.shortCode)) {
    list.unshift(link)
    setItem(MY_LINKS_KEY, list.slice(0, 500)) // 只保留最近 500 条
  }
}