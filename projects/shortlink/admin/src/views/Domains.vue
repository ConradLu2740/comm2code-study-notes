<template>
  <div class="domains-page">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">我的域名</span>
          <el-button type="primary" :icon="Plus" @click="showBind = true">
            绑定域名
          </el-button>
        </div>
      </template>

      <el-table
        :data="domains"
        v-loading="loading"
        stripe
        empty-text="还没有绑定任何域名"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="domain" label="域名" min-width="200">
          <template #default="{ row }">
            <el-icon><Globe /></el-icon>
            <span style="margin-left: 6px">{{ row.domain }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="shortCode" label="关联短码" width="160">
          <template #default="{ row }">
            <el-tag size="small" v-if="row.shortCode">{{ row.shortCode }}</el-tag>
            <span v-else class="text-muted">未指定</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag
              :type="statusType(row.status)"
              size="small"
              effect="dark"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'VERIFIED'"
              size="small"
              type="primary"
              link
              @click="verify(row)"
            >
              验证
            </el-button>
            <el-button size="small" type="danger" link @click="unbind(row)">
              解绑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 绑定对话框 -->
    <el-dialog v-model="showBind" title="绑定域名" width="480px">
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="100px">
        <el-form-item label="域名" prop="domain">
          <el-input
            v-model="bindForm.domain"
            placeholder="例如 s.example.com"
            clearable
          />
        </el-form-item>
        <el-form-item label="关联短码" prop="shortCode">
          <el-input
            v-model="bindForm.shortCode"
            placeholder="已存在的短码"
            clearable
          />
        </el-form-item>
        <el-alert
          title="提示"
          type="info"
          :closable="false"
          show-icon
        >
          <template #default>
            绑定后需通过 DNS 解析验证才能生效。
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showBind = false">取消</el-button>
        <el-button type="primary" :loading="bindLoading" @click="submitBind">
          绑定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Globe } from '@element-plus/icons-vue'
import { listDomains, bindDomain, verifyDomain, unbindDomain } from '../api'
import { useUserStore } from '../stores/user'
import { formatDate } from '../utils/format'

// 域名管理页
// 教学点：
// 1. 域名接口要求 userId 参数，这里从 Pinia user store 取
// 2. 状态字段是枚举字符串（PENDING / VERIFIED / FAILED），前端做映射

const userStore = useUserStore()
const domains = ref([])
const loading = ref(false)
const showBind = ref(false)
const bindLoading = ref(false)
const bindFormRef = ref(null)

const bindForm = reactive({
  domain: '',
  shortCode: '',
})

const bindRules = {
  domain: [
    { required: true, message: '请输入域名', trigger: 'blur' },
    {
      validator: (_, v, cb) => {
        // 简单域名校验
        if (!/^([a-z0-9-]+\.)+[a-z]{2,}$/i.test(v || '')) {
          return cb(new Error('域名格式不正确'))
        }
        cb()
      },
      trigger: 'blur',
    },
  ],
  shortCode: [{ required: true, message: '请输入关联短码', trigger: 'blur' }],
}

function statusLabel(s) {
  return { PENDING: '待验证', VERIFIED: '已验证', FAILED: '验证失败' }[s] || s
}
function statusType(s) {
  return { PENDING: 'warning', VERIFIED: 'success', FAILED: 'danger' }[s] || ''
}

async function fetchList() {
  if (!userStore.user?.id) {
    ElMessage.warning('请先登录')
    return
  }
  loading.value = true
  try {
    domains.value = await listDomains(userStore.user.id)
  } catch (e) {} finally {
    loading.value = false
  }
}

async function submitBind() {
  const valid = await bindFormRef.value.validate().catch(() => false)
  if (!valid) return
  bindLoading.value = true
  try {
    await bindDomain(userStore.user.id, bindForm)
    ElMessage.success('绑定成功')
    showBind.value = false
    bindForm.domain = ''
    bindForm.shortCode = ''
    await fetchList()
  } catch (e) {} finally {
    bindLoading.value = false
  }
}

async function verify(row) {
  try {
    const r = await verifyDomain(row.domain)
    ElMessage.success(r?.message || '验证已触发')
    await fetchList()
  } catch (e) {}
}

async function unbind(row) {
  try {
    await ElMessageBox.confirm(
      `确定解绑域名 ${row.domain} 吗？`,
      '提示',
      { type: 'warning' },
    )
    await unbindDomain(row.id, userStore.user.id)
    ElMessage.success('已解绑')
    await fetchList()
  } catch (e) {
    // 用户取消或接口报错已处理
  }
}

onMounted(fetchList)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>