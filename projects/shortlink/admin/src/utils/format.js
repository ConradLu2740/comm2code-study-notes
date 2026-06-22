// 格式化工具
// 教学点：纯函数集合，零依赖。复用率高的逻辑抽出来，避免每个 view 重复写。

/**
 * 格式化日期时间
 * @param {string|Date|number} value - ISO 字符串 / Date / 时间戳
 * @param {string} fmt - 'YYYY-MM-DD HH:mm:ss' / 'YYYY-MM-DD' / 'HH:mm:ss'
 * @returns {string}
 */
export function formatDate(value, fmt = 'YYYY-MM-DD HH:mm:ss') {
  if (!value) return '-'
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return '-'

  const pad = (n) => String(n).padStart(2, '0')
  return fmt
    .replace('YYYY', d.getFullYear())
    .replace('MM', pad(d.getMonth() + 1))
    .replace('DD', pad(d.getDate()))
    .replace('HH', pad(d.getHours()))
    .replace('mm', pad(d.getMinutes()))
    .replace('ss', pad(d.getSeconds()))
}

/**
 * 数字千分位：1234567 -> 1,234,567
 */
export function formatNumber(n) {
  if (n === null || n === undefined) return 0
  return Number(n).toLocaleString('en-US')
}

/**
 * 截断长 URL，中间用省略号
 */
export function truncate(str, max = 60) {
  if (!str) return ''
  if (str.length <= max) return str
  return str.slice(0, max / 2) + '...' + str.slice(-max / 2)
}

/**
 * 相对时间：3 分钟前 / 2 小时前 / 5 天前
 */
export function fromNow(value) {
  if (!value) return '-'
  const d = value instanceof Date ? value : new Date(value)
  const diff = Date.now() - d.getTime()
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return `${sec} 秒前`
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr} 小时前`
  const day = Math.floor(hr / 24)
  if (day < 30) return `${day} 天前`
  return formatDate(value, 'YYYY-MM-DD')
}