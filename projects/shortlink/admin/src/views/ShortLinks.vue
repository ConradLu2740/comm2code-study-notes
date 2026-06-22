<template>
  <div class="shortlinks-page">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">我的短链</span>
          <el-button type="primary" :icon="Plus" @click="showCreate = true">
            创建短链
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="toolbar">
        <el-input
          v-model="search"
          placeholder="按短码或长 URL 搜索"
          clearable
          style="width: 320px"
          :prefix-icon="Search"
        />
        <el-button @click="refresh">刷新</el-button>
      </div>

      <!-- 列表 -->
      <el-table
        :data="filtered"
        v-loading="loading"
        stripe
        empty-text="还没有短链，点右上角创建第一个吧"
        style="width: 100%"
      >
        <el-table-column prop="shortCode" label="短码" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.shortCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="shortUrl" label="短链接" min-width="240">
          <template #default="{ row }">
            <a :href="row.shortUrl" target="_blank" class="short-url">
              {{ row.shortUrl }}
            </a>
          </template>
        </el-table-column>
        <el-table-column prop="longUrl" label="原 URL" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="long-url">{{ row.longUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="clickCount" label="点击数" width="100" align="right">
          <template #default="{ row }">
            <el-tag :type="row.clickCount > 0 ? 'success' : 'info'" size="small">
              {{ row.clickCount || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fromNow(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="copy(row)">
              复制
            </el-button>
            <el-button size="small" type="primary" link @click="viewDetail(row)">
              详情
            </el-button>
            <el-button size="small" type="primary" link @click="refreshOne(row)">
              刷新
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建短链 -->
    <el-dialog v-model="showCreate" title="创建短链" width="520px">
      <el-form ref="formRef" :model="createForm" :rules="rules" label-width="80px">
        <el-form-item label="长链接" prop="longUrl">
          <el-input
            v-model="createForm.longUrl"
            placeholder="https://example.com/very/long/url"
            clearable
          />
        </el-form-item>
        <el-form-item label="自定义短码" prop="alias">
          <el-input
            v-model="createForm.alias"
            placeholder="可选，留空自动生成"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="showDetail" title="短链详情" width="520px">
      <el-descriptions :column="1" border v-if="detail">
        <el-descriptions-item label="短码">
          <el-tag>{{ detail.shortCode }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="短链接">
          <a :href="detail.shortUrl" target="_blank">{{ detail.shortUrl }}</a>
        </el-descriptions-item>
        <el-descriptions-item label="原 URL">{{ detail.longUrl }}</el-descriptions-item>
        <el-descriptions-item label="点击数">
          {{ detail.clickCount || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatDate(detail.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="过期时间">
          {{ detail.expireAt ? formatDate(detail.expireAt) : '永不过期' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { createShortLink, getShortLinkInfo } from '../api'
import { getMyLinks, pushMyLink } from '../utils/storage'
import { fromNow, formatDate } from '../utils/format'

// 短链管理页
// 教学点：
// 1. 后端没有"列出我的所有短链"接口，所以前端用 localStorage 缓存已创建的短码。
// 2. 创建后立即同步缓存 + 后端响应，避免再发一次 list 请求。
// 3. 详情用 GET /api/v1/shortlinks/{code} 重新拉一次，保证 clickCount 是最新的。

const search = ref('')
const loading = ref(false)
const links = ref(getMyLinks())

const showCreate = ref(false)
const showDetail = ref(false)
const createLoading = ref(false)
const detail = ref(null)

const formRef = ref(null)
const createForm = reactive({
  longUrl: '',
  alias: '',
})

const rules = {
  longUrl: [
    { required: true, message: '请输入长链接', trigger: 'blur' },
    {
      validator: (_, v, cb) => {
        if (!/^https?:\/\//i.test(v || '')) {
          return cb(new Error('必须以 http:// 或 https:// 开头'))
        }
        cb()
      },
      trigger: 'blur',
    },
  ],
  alias: [
    {
      validator: (_, v, cb) => {
        if (!v) return cb()
        if (!/^[a-zA-Z0-9_-]{3,32}$/.test(v)) {
          return cb(new Error('3-32 位字母数字下划线'))
        }
        cb()
      },
      trigger: 'blur',
    },
  ],
}

const filtered = computed(() => {
  const kw = search.value.trim().toLowerCase()
  if (!kw) return links.value
  return links.value.filter(
    (l) =>
      (l.shortCode || '').toLowerCase().includes(kw) ||
      (l.longUrl || '').toLowerCase().includes(kw),
  )
})

function refresh() {
  loading.value = true
  links.value = getMyLinks()
  loading.value = false
}

async function submitCreate() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  createLoading.value = true
  try {
    const payload = { longUrl: createForm.longUrl }
    if (createForm.alias) payload.alias = createForm.alias

    const resp = await createShortLink(payload)
    // 后端 ShortLinkResponse: { shortCode, shortUrl, longUrl, createdAt, expireAt, clickCount }
    pushMyLink(resp)
    links.value = getMyLinks()
    ElMessage.success(`创建成功：${resp.shortCode}`)
    showCreate.value = false
    createForm.longUrl = ''
    createForm.alias = ''
  } catch (e) {
    // 错误已统一处理
  } finally {
    createLoading.value = false
  }
}

function copy(row) {
  // 教学点：Clipboard API 在 HTTP（非 localhost 域名）下不可用，
  // 用老式 document.execCommand('copy') 作为 fallback
  const text = row.shortUrl
  if (navigator.clipboard) {
    navigator.clipboard
      .writeText(text)
      .then(() => ElMessage.success('已复制到剪贴板'))
      .catch(() => fallbackCopy(text))
  } else {
    fallbackCopy(text)
  }
}

function fallbackCopy(text) {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.opacity = '0'
  document.body.appendChild(ta)
  ta.select()
  try {
    document.execCommand('copy')
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
  document.body.removeChild(ta)
}

async function viewDetail(row) {
  try {
    detail.value = await getShortLinkInfo(row.shortCode)
    showDetail.value = true
  } catch (e) {}
}

async function refreshOne(row) {
  loading.value = true
  try {
    const fresh = await getShortLinkInfo(row.shortCode)
    const idx = links.value.findIndex((l) => l.shortCode === row.shortCode)
    if (idx >= 0) {
      links.value[idx] = fresh
      // 同步到 localStorage
      const all = getMyLinks()
      const i = all.findIndex((l) => l.shortCode === row.shortCode)
      if (i >= 0) all[i] = fresh
      // 简单做法：直接重写整个数组
      localStorage.setItem(
        'shortlink_admin:my_shortlinks',
        JSON.stringify(all),
      )
    }
    ElMessage.success('已刷新')
  } catch (e) {} finally {
    loading.value = false
  }
}

onMounted(() => {
  refresh()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.short-url {
  color: #409eff;
  text-decoration: none;
}
.short-url:hover {
  text-decoration: underline;
}
.long-url {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
}
</style>