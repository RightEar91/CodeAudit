<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Download } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getProject,
  type Project
} from '@/api/projects'
import api from '@/api/index'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => Number(route.params.id))
const project = ref<Project | null>(null)
const loading = ref(false)
const days = ref(0)

const trendChart = ref<HTMLDivElement>()
const severityChart = ref<HTMLDivElement>()
const categoryChart = ref<HTMLDivElement>()
const authorChart = ref<HTMLDivElement>()

const stats = ref({
  issueDensityTrend: [] as any[],
  fixRate: { resolved: 0, open: 0, ignored: 0, rate: 0 },
  severityDistribution: [] as any[],
  categoryDistribution: [] as any[],
  topAuthors: [] as any[]
})

async function fetchProject() {
  try {
    const res = await getProject(projectId.value)
    project.value = res.data
  } catch (e) {
    console.error('Failed to fetch project:', e)
  }
}

async function fetchStats() {
  loading.value = true
  try {
    const params: any = {}
    if (days.value > 0) params.days = days.value
    const res = await api.get(`/projects/${projectId.value}/statistics`, { params })
    stats.value = res.data || {
      issueDensityTrend: [],
      fixRate: { resolved: 0, open: 0, ignored: 0, rate: 0 },
      severityDistribution: [],
      categoryDistribution: [],
      topAuthors: []
    }
  } catch (e) {
    console.error('Failed to fetch statistics:', e)
  } finally {
    loading.value = false
  }
}

function renderTrendChart() {
  if (!trendChart.value || stats.value.issueDensityTrend.length === 0) return
  const chart = echarts.init(trendChart.value)
  const data = stats.value.issueDensityTrend
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['问题数', '文件数'], top: 0 },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: data.map((d: any) => d.date) },
    yAxis: { type: 'value' },
    series: [
      { name: '问题数', type: 'line', data: data.map((d: any) => d.issueCount), smooth: true, itemStyle: { color: '#ef4444' } },
      { name: '文件数', type: 'line', data: data.map((d: any) => d.fileCount), smooth: true, itemStyle: { color: '#3b82f6' } }
    ]
  })
  window.addEventListener('resize', () => chart.resize())
}

function renderSeverityChart() {
  if (!severityChart.value || stats.value.severityDistribution.length === 0) return
  const chart = echarts.init(severityChart.value)
  const data = stats.value.severityDistribution.map((d: any) => ({ name: d.severity, value: d.count }))
  const colorMap: Record<string, string> = { HIGH: '#ef4444', MEDIUM: '#f59e0b', LOW: '#22c55e' }
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['50%', '55%'],
      label: { formatter: '{b}\n{c}' },
      data,
      itemStyle: { color: (p: any) => colorMap[p.data.name] || '#94a3b8' }
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

function renderCategoryChart() {
  if (!categoryChart.value || stats.value.categoryDistribution.length === 0) return
  const chart = echarts.init(categoryChart.value)
  const data = stats.value.categoryDistribution.map((d: any) => ({ name: d.category, value: d.count }))
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['50%', '55%'],
      label: { formatter: '{b}\n{c}' },
      data
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

function renderAuthorChart() {
  if (!authorChart.value || stats.value.topAuthors.length === 0) return
  const chart = echarts.init(authorChart.value)
  const authors = stats.value.topAuthors
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 100, right: 20, top: 10, bottom: 20 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: authors.map((a: any) => a.authorName).reverse() },
    series: [{
      type: 'bar',
      data: authors.map((a: any) => a.issueCount).reverse(),
      itemStyle: { color: '#6366f1' }
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

function renderAllCharts() {
  setTimeout(() => {
    renderTrendChart()
    renderSeverityChart()
    renderCategoryChart()
    renderAuthorChart()
  }, 100)
}

async function handleExportExcel() {
  try {
    const config: any = { responseType: 'blob' }
    if (days.value > 0) config.params = { days: days.value }
    const res = await api.get(`/projects/${projectId.value}/statistics/excel`, config)
    const url = URL.createObjectURL(res as unknown as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `codeaudit-statistics-${projectId.value}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('Excel 导出成功')
  } catch (e) {
    console.error('Excel export failed:', e)
    ElMessage.error('Excel 导出失败')
  }
}

function goBack() {
  router.push(`/projects/${projectId.value}/reviews`)
}

function statusText(status: string) {
  if (status === 'SECURITY') return '安全漏洞'
  if (status === 'PERFORMANCE') return '性能问题'
  if (status === 'STYLE') return '代码规范'
  if (status === 'BUG') return '潜在缺陷'
  return status
}

onMounted(async () => {
  await fetchProject()
  await fetchStats()
  renderAllCharts()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button text :icon="ArrowLeft" @click="goBack">
        返回审查列表
      </el-button>
      <div style="display: flex; align-items: center; justify-content: space-between;">
        <h1 class="page-title">{{ project?.name || '趋势报表' }}</h1>
        <div style="display: flex; gap: 12px; align-items: center;">
          <el-select v-model="days" @change="fetchStats().then(renderAllCharts)" style="width: 140px;">
            <el-option :value="0" label="全部时间" />
            <el-option :value="7" label="最近 7 天" />
            <el-option :value="30" label="最近 30 天" />
            <el-option :value="90" label="最近 90 天" />
          </el-select>
          <el-button :icon="Download" @click="handleExportExcel">导出 Excel</el-button>
        </div>
      </div>
    </div>

    <div v-loading="loading" class="stats-grid">
      <!-- Fix Rate -->
      <div class="stat-card fix-rate-card">
        <div class="card-title">修复率</div>
        <div class="fix-rate-value">{{ stats.fixRate.rate }}%</div>
        <div class="fix-rate-detail">
          <span class="detail-item resolved">已修复 {{ stats.fixRate.resolved }}</span>
          <span class="detail-item open">未处理 {{ stats.fixRate.open }}</span>
          <span class="detail-item ignored">已忽略 {{ stats.fixRate.ignored }}</span>
        </div>
      </div>

      <!-- Issue Density Trend -->
      <div class="stat-card chart-card">
        <div class="card-title">问题密度趋势</div>
        <div ref="trendChart" class="chart-box"></div>
      </div>

      <!-- Severity Distribution -->
      <div class="stat-card chart-card">
        <div class="card-title">严重程度分布</div>
        <div ref="severityChart" class="chart-box"></div>
      </div>

      <!-- Category Distribution -->
      <div class="stat-card chart-card">
        <div class="card-title">问题分类分布</div>
        <div ref="categoryChart" class="chart-box"></div>
      </div>

      <!-- Top Authors -->
      <div class="stat-card chart-card">
        <div class="card-title">问题作者 Top 10</div>
        <div ref="authorChart" class="chart-box"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.stat-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: 20px;
}

.fix-rate-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.fix-rate-value {
  font-size: 48px;
  font-weight: 700;
  color: #22c55e;
  margin: 8px 0;
}

.fix-rate-detail {
  display: flex;
  gap: 16px;
  font-size: 13px;
  margin-top: 8px;
}

.detail-item { padding: 4px 10px; border-radius: 4px; }
.detail-item.resolved { color: #22c55e; background: rgba(34, 197, 94, 0.1); }
.detail-item.open { color: #f59e0b; background: rgba(245, 158, 11, 0.1); }
.detail-item.ignored { color: #94a3b8; background: rgba(148, 163, 184, 0.1); }

.chart-card {
  min-height: 320px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

.chart-box {
  width: 100%;
  height: 280px;
}
</style>
