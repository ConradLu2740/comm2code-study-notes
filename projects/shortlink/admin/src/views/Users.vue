<template>
  <div class="users-page">
    <el-card v-loading="loading" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">个人信息</span>
          <el-button :icon="Refresh" @click="fetchMe">刷新</el-button>
        </div>
      </template>

      <div v-if="user" class="profile">
        <el-avatar :size="80" class="avatar">
          {{ initial }}
        </el-avatar>
        <el-descriptions :column="1" border style="margin-top: 24px">
          <el-descriptions-item label="用户 ID">
            {{ user.id }}
          </el-descriptions-item>
          <el-descriptions-item label="用户名">
            {{ user.username }}
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            <el-tag v-if="user.email" type="success">{{ user.email }}</el-tag>
            <span v-else class="text-muted">未设置</span>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">
            {{ formatDate(user.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          title="安全提示"
          type="info"
          :closable="false"
          show-icon
          style="margin-top: 20px"
        >
          <template #default>
            你的登录令牌已加密存储在 localStorage，请勿在公共电脑勾选"记住我"。
          </template>
        </el-alert>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import { formatDate } from '../utils/format'

// 用户信息页
// 教学点：调用 /api/v1/users/me 验证 token 是否仍有效，顺便显示最新用户信息。

const userStore = useUserStore()
const loading = ref(false)

const user = computed(() => userStore.user)
const initial = computed(() => (user.value?.username || '?').charAt(0).toUpperCase())

async function fetchMe() {
  loading.value = true
  try {
    await userStore.fetchMe()
  } catch (e) {} finally {
    loading.value = false
  }
}

onMounted(fetchMe)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.profile {
  max-width: 600px;
  margin: 0 auto;
  text-align: center;
}
.avatar {
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  font-size: 32px;
  font-weight: 600;
}
</style>