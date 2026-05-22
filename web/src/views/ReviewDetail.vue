<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, Download, WarningFilled, CircleCheckFilled, CircleCloseFilled,
  InfoFilled, Document, Promotion
} from '@element-plus/icons-vue'
import {
  getReview,
  listIssues,
  updateIssueStatus,
  type Review,
  type Issue
} from '@/api/reviews'

const route = useRoute()
const router = useRouter()

const reviewId = computed(() => Number(route.params.id))
const review = ref<Review | null>(null)
const issues = ref<Issue[]>([])
const loading = ref(false)

const statusFilter = ref('')
const severityFilter = ref('')

const filteredIssues = computed(() => {
  return issues.value.filter(i => {
    if (statusFilter.value && i.status !== statusFilter.value) return false
    if (severityFilter.value && i.severity !== severityFilter.value) return false
    return true
  })
})

const severityIcon = (severity: string) => {
  switch (severity) {
    case 'HIGH': return WarningFilled
    case 'MEDIUM': return Promotion
    case 'LOW': return InfoFilled
    default: return InfoFilled
  }
}

const severityColor = (severity: string) => {
  switch (severity) {
    case 'HIGH': return 'var(--color-danger)'
    case 'MEDIUM': return 'var(--color-warning)'
    case 'LOW': return 'var(--color-info)'
    default: return 'var(--color-text-muted)'
  }
}

const severityBg = (severity: string) => {
  switch (severity) {
    case 'HIGH': return 'var(--color-danger-light)'
    case 'MEDIUM': return 'var(--color-warning-light)'
    case 'LOW': return 'var(--color-info-light)'
    default: return 'var(--color-border-light)'
  }
}

const categoryLabel = (category: string) => {
  switch (category) {
    case 'SECURITY': return '安全漏洞'
    case 'PERFORMANCE': return '性能问题'
    case 'STYLE': return '代码规范'
    case 'BUG': return '潜在缺陷'
    default: return category
  }
}

const statusIconMap: Record<string, any> = {
  open: CircleCloseFilled,
  resolved: CircleCheckFilled,
  ignored: InfoFilled
}

const statusColorMap: Record<string, string> = {
  open: 'var(--color-danger)',
  resolved: 'var(--color-success)',
  ignored: 'var(--color-text-muted)'
}

const statusLabelMap: Record<string, string> = {
  open: '未处理',
  resolved: '已修复',
  ignored: '已忽略'
}

const formatDuration = (ms?: number) => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}min`
}

async function fetchData() {
  loading.value = true
  try {
    const [reviewRes, issuesRes] = await Promise.all([
      getReview(reviewId.value),
      listIssues(reviewId.value, { page: 0, size: 200 })
    ])
    review.value = reviewRes.data
    issues.value = issuesRes.data.content
  } catch (e) {
    console.error('Failed to fetch review data:', e)
  } finally {
    loading.value = false
  }
}

async function handleStatusChange(issue: Issue, newStatus: Issue['status']) {
  try {
    await updateIssueStatus(issue.id!, newStatus)
    issue.status = newStatus
    ElMessage.success('状态已更新')
  } catch (e) {
    console.error('Failed to update issue status:', e)
  }
}

function exportJSON() {
  const data = {
    review: review.value,
    issues: issues.value
  }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `code-review-${reviewId.value}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 100)
  ElMessage.success('JSON 报告已下载')
}

function goBack() {
  if (review.value?.project?.id) {
    router.push(`/projects/${review.value.project.id}/reviews`)
  } else {
    router.push('/projects')
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <el-button text :icon="ArrowLeft" @click="goBack" style="margin-bottom: 12px;">
        返回审查列表
      </el-button>
      <div style="display: flex; align-items: flex-start; justify-content: space-between;">
        <div>
          <h1 class="page-title">{{ review?.title || '审查报告' }}</h1>
          <p class="page-subtitle">
            {{ review?.project?.name }} &middot;
            {{ review?.createdAt?.substring(0, 16)?.replace('T', ' ') }}
          </p>
        </div>
        <div class="export-btns">
          <el-button :icon="Download" round @click="exportJSON">导出 JSON</el-button>
        </div>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--color-danger-light); color: var(--color-danger);">
          <el-icon :size="18"><WarningFilled /></el-icon>
        </div>
        <div class="stat-value">{{ review?.highCount || 0 }}</div>
        <div class="stat-label">高危</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--color-warning-light); color: var(--color-warning);">
          <el-icon :size="18"><Promotion /></el-icon>
        </div>
        <div class="stat-value">{{ review?.mediumCount || 0 }}</div>
        <div class="stat-label">中危</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--color-info-light); color: var(--color-info);">
          <el-icon :size="18"><InfoFilled /></el-icon>
        </div>
        <div class="stat-value">{{ review?.lowCount || 0 }}</div>
        <div class="stat-label">低危</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--color-primary-light); color: var(--color-primary);">
          <el-icon :size="18"><Document /></el-icon>
        </div>
        <div class="stat-value">{{ review?.totalIssues || 0 }}</div>
        <div class="stat-label">问题总数</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #f0fdf4; color: var(--color-success);">
          <el-icon :size="18"><CircleCheckFilled /></el-icon>
        </div>
        <div class="stat-value">{{ issues.filter(i => i.status === 'resolved').length }}</div>
        <div class="stat-label">已修复</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="severityFilter" placeholder="严重程度" clearable size="small" style="width: 130px;">
        <el-option label="🔴 高危" value="HIGH" />
        <el-option label="🟡 中危" value="MEDIUM" />
        <el-option label="🔵 低危" value="LOW" />
      </el-select>
      <el-select v-model="statusFilter" placeholder="处理状态" clearable size="small" style="width: 130px;">
        <el-option label="未处理" value="open" />
        <el-option label="已修复" value="resolved" />
        <el-option label="已忽略" value="ignored" />
      </el-select>
    </div>

    <div v-if="filteredIssues.length > 0" class="issues-list">
      <div
        v-for="issue in filteredIssues"
        :key="issue.id"
        class="issue-item"
      >
        <div class="issue-left">
          <span class="issue-severity" :style="{ color: severityColor(issue.severity), background: severityBg(issue.severity) }">
            <el-icon :size="14"><component :is="severityIcon(issue.severity)" /></el-icon>
            {{ issue.severity === 'HIGH' ? '高危' : issue.severity === 'MEDIUM' ? '中危' : '低危' }}
          </span>
          <span class="issue-category">{{ categoryLabel(issue.category) }}</span>
        </div>
        <div class="issue-body">
          <div class="issue-location">
            <code>{{ issue.filePath }}</code>
            <span v-if="issue.lineNumber" class="issue-line">:{{ issue.lineNumber }}</span>
          </div>
          <p class="issue-message">{{ issue.message }}</p>
          <div v-if="issue.suggestion" class="issue-suggestion">
            <el-icon :size="14"><InfoFilled /></el-icon>
            <span>{{ issue.suggestion }}</span>
          </div>
        </div>
        <div class="issue-actions">
          <el-dropdown trigger="click" @command="(cmd: Issue['status']) => handleStatusChange(issue, cmd)">
            <span class="issue-status-badge" :style="{ color: statusColorMap[issue.status] }">
              <el-icon :size="14"><component :is="statusIconMap[issue.status]" /></el-icon>
              {{ statusLabelMap[issue.status] }}
              <el-icon :size="12" style="margin-left: 2px;"><ArrowLeft style="transform: rotate(-90deg);" /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="open" v-if="issue.status !== 'open'">标记未处理</el-dropdown-item>
                <el-dropdown-item command="resolved" v-if="issue.status !== 'resolved'">标记已修复</el-dropdown-item>
                <el-dropdown-item command="ignored" v-if="issue.status !== 'ignored'">标记已忽略</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>
    <div v-else class="empty-state" style="margin-top: 40px;">
      <el-icon><Document /></el-icon>
      <p>{{ issues.length === 0 ? '无问题记录' : '无匹配的问题' }}</p>
    </div>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.issues-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.issue-item {
  display: flex;
  align-items: flex-start;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: 18px 20px;
  transition: box-shadow var(--transition);
}

.issue-item:hover {
  box-shadow: var(--shadow-sm);
}

.issue-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-right: 16px;
  flex-shrink: 0;
  min-width: 80px;
}

.issue-severity {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 100px;
  white-space: nowrap;
}

.issue-category {
  font-size: 11px;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding-left: 8px;
}

.issue-body {
  flex: 1;
  min-width: 0;
}

.issue-location {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 6px;
}

.issue-location code {
  font-size: 12px;
  color: var(--color-text-secondary);
  background: var(--color-border-light);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
}

.issue-line {
  font-size: 12px;
  color: var(--color-text-muted);
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
}

.issue-message {
  font-size: 14px;
  color: var(--color-text-primary);
  line-height: 1.5;
  margin: 0;
}

.issue-suggestion {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  padding: 10px 14px;
  background: var(--color-primary-light);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--color-primary-dark);
  line-height: 1.6;
}

.issue-actions {
  flex-shrink: 0;
  margin-left: 12px;
}

.issue-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: background var(--transition);
  white-space: nowrap;
}

.issue-status-badge:hover {
  background: var(--color-border-light);
}

.export-btns {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
