<template>
  <div class="stats-page">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">访问统计</span>
          <div class="picker">
            <el-select
              v-model="selectedCode"
              placeholder="选择短码查看统计"
              filterable
              clearable
              style="width: 280px"
              @change="loadStats"
            >
              <el-option
                v-for="it in myLinks"
                :key="it.shortCode"
                :label="`${it.shortCode} (${it.clickCount || 0} 点击)`"
                :value="it.shortCode"
              />
            </el-select>
            <el-button
              :icon="Refresh"
              :disabled="!selectedCode"
              @click="loadStats"
            >
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-empty
        v-if="!selectedCode"
        description="请先选择短码"
        :image-size="120"
      />

      <template v-else>
        <!-- 顶部数字 -->
        <el-row :gutter="20" v-loading="loading">
          <el-col :xs="24" :sm="8">
            <el-card class="mini-card" shadow="never">
              <div class="mini-label">总访问量</div>
              <div class="mini-value">{{ formatNumber(stats?.totalClicks || 0) }}</div>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-card class="mini-card" shadow="never">
              <div class="mini-label">覆盖国家/地区</div>
              <div class="mini-value">{{ stats?.uniqueCountries || 0 }}</div>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-card class="mini-card" shadow="never">
              <div class="mini-label">设备类型数</div>
              <div class="mini-value">{{ stats?.topDevices?.length || 0 }}</div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 折线图 + 饼图 -->
        <el-row :gutter="20" style="margin-top: 20px" v-loading="loading">
          <el-col :xs="24" :md="14">
            <el-card shadow="never">
              <template #header>
                <span class="card-title">每日访问趋势</span>
              </template>
              <div ref="lineRef" class="chart"></div>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="10">
            <el-card shadow="never">
              <template #header>
                <span class="card-title">设备分布</span>
              </template>
              <div ref="devicePieRef" class="chart"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="20" style="margin-top: 20px" v-loading="loading">
          <el-col :span="24">
            <el-card shadow="never">
              <template #header>
                <span class="card-title">国家/地区分布</span>
              </template>
              <div ref="countryPieRef" class="chart"></div>
            </el-card>
          </el-col>
        </el-row>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import { getShortLinkStats } from '../api'
import { getMyLinks } from '../utils/storage'
import { formatNumber } from '../utils/format'

// 访问统计页
// 教学点：
// 1. 三张图都用 ECharts：折线图（按天）+ 设备饼图 + 国家饼图
// 2. 切换短码时 dispose 旧实例，避免内存泄漏
// 3. 后端 StatsResponse 是 record 类型，前端用 ?. 处理可能的字段缺失

const myLinks = ref(getMyLinks())
const selectedCode = ref('')
const stats = ref(null)
const loading = ref(false)

const lineRef = ref(null)
const devicePieRef = ref(null)
const countryPieRef = ref(null)
let lineChart = null
let deviceChart = null
let countryChart = null

async function loadStats() {
  if (!selectedCode.value) return
  loading.value = true
  try {
    stats.value = await getShortLinkStats(selectedCode.value)
    await nextTick()
    renderAll()
  } catch (e) {} finally {
    loading.value = false
  }
}

function renderAll() {
  renderLine()
  renderDevice()
  renderCountry()
}

function renderLine() {
  if (!lineRef.value) return
  if (!lineChart) lineChart = echarts.init(lineRef.value)
  const daily = stats.value?.dailyTrend || []
  // 后端按日期倒序，这里反转让 X 轴从左到右是时间正向
  const sorted = [...daily].reverse()
  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: sorted.map((d) => d.date),
      axisLabel: { rotate: 30 },
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '访问量',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.2 },
        data: sorted.map((d) => d.count),
        itemStyle: { color: '#409eff' },
      },
    ],
  })
}

function renderDevice() {
  if (!devicePieRef.value) return
  if (!deviceChart) deviceChart = echarts.init(devicePieRef.value)
  const devices = stats.value?.topDevices || []
  deviceChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        data: devices.map((d) => ({ name: d.deviceType, value: d.count })),
      },
    ],
  })
}

function renderCountry() {
  if (!countryPieRef.value) return
  if (!countryChart) countryChart = echarts.init(countryPieRef.value)
  const countries = stats.value?.topCountries || []
  countryChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { type: 'scroll', orient: 'vertical', right: 10, top: 20, bottom: 20 },
    series: [
      {
        type: 'pie',
        radius: '60%',
        center: ['38%', '50%'],
        data: countries.map((c) => ({ name: c.country, value: c.count })),
        label: { formatter: '{b}' },
      },
    ],
  })
}

function handleResize() {
  lineChart?.resize()
  deviceChart?.resize()
  countryChart?.resize()
}

onMounted(() => {
  // 默认选第一个
  if (myLinks.value.length > 0) {
    selectedCode.value = myLinks.value[0].shortCode
    loadStats()
  }
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  lineChart?.dispose()
  deviceChart?.dispose()
  countryChart?.dispose()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.picker {
  display: flex;
  gap: 12px;
}
.mini-card {
  background: #fafbfc;
}
.mini-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}
.mini-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
.chart {
  width: 100%;
  height: 320px;
}
</style>