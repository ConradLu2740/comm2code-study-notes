<template>
  <div class="dashboard">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="20" class="stat-row">
      <el-col :xs="24" :sm="12" :md="6" v-for="card in statCards" :key="card.label">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">
            {{ card.value }}
          </div>
          <div class="stat-icon" :style="{ background: card.color + '20', color: card.color }">
            <el-icon :size="24"><component :is="card.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Top 5 饼图 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">Top 5 短链（按点击数）</span>
          </template>
          <div ref="pieRef" class="chart"></div>
          <el-empty v-if="topLinks.length === 0" description="暂无数据" :image-size="80" />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">最近创建</span>
          </template>
          <el-table :data="recentLinks" stripe size="small" max-height="320">
            <el-table-column prop="shortCode" label="短码" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ row.shortCode }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="longUrl" label="原链接" show-overflow-tooltip />
            <el-table-column prop="clickCount" label="点击" width="80" align="right" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { Link, Pointer, Calendar, TrendCharts } from '@element-plus/icons-vue'
import { getMyLinks } from '../utils/storage'
import { formatNumber, fromNow } from '../utils/format'

// Dashboard 仪表盘
// 教学点：
// 1. 数据来源：本页只展示"我创建过的短链"（localStorage 缓存）+ 简单统计
// 2. ECharts 用 ref 拿 DOM 节点，手动 init/dispose，路由切换时记得 dispose
// 3. 用 computed 派生聚合数据，模板只负责展示

const allLinks = ref(getMyLinks())

const statCards = computed(() => {
  const totalLinks = allLinks.value.length
  const totalClicks = allLinks.value.reduce((s, l) => s + (l.clickCount || 0), 0)
  const today = new Date().toDateString()
  const todayNew = allLinks.value.filter(
    (l) => new Date(l.createdAt).toDateString() === today,
  ).length
  // 活跃 = 7 天内创建
  const weekAgo = Date.now() - 7 * 24 * 3600 * 1000
  const active = allLinks.value.filter(
    (l) => new Date(l.createdAt).getTime() >= weekAgo,
  ).length

  return [
    {
      label: '总链接数',
      value: formatNumber(totalLinks),
      color: '#409eff',
      icon: Link,
    },
    {
      label: '总点击数',
      value: formatNumber(totalClicks),
      color: '#67c23a',
      icon: Pointer,
    },
    {
      label: '今日新增',
      value: formatNumber(todayNew),
      color: '#e6a23c',
      icon: Calendar,
    },
    {
      label: '7日活跃',
      value: formatNumber(active),
      color: '#f56c6c',
      icon: TrendCharts,
    },
  ]
})

const topLinks = computed(() =>
  [...allLinks.value].sort((a, b) => (b.clickCount || 0) - (a.clickCount || 0)).slice(0, 5),
)

const recentLinks = computed(() =>
  [...allLinks.value]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 8),
)

// ===== ECharts 饼图 =====
const pieRef = ref(null)
let chart = null

function renderPie() {
  if (!pieRef.value) return
  if (!chart) chart = echarts.init(pieRef.value)
  if (topLinks.value.length === 0) {
    chart.clear()
    return
  }
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c}' },
        data: topLinks.value.map((l) => ({
          name: l.shortCode,
          value: l.clickCount || 0,
        })),
      },
    ],
  })
}

function handleResize() {
  chart?.resize()
}

watch(topLinks, () => nextTick(renderPie))

onMounted(() => {
  nextTick(renderPie)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
  margin: 0 auto;
}
.stat-row {
  margin-bottom: 0;
}
.stat-card {
  position: relative;
  overflow: hidden;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
}
.stat-icon {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.chart {
  width: 100%;
  height: 320px;
}
</style>