<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, Download, WarningFilled, CircleCheckFilled, CircleCloseFilled,
  InfoFilled, Document, Promotion, ArrowDown, ArrowRight, Folder, Loading
} from '@element-plus/icons-vue'
import {
  getReview,
  listIssues,
  updateIssueStatus,
  exportReviewPdf,
  getReviewDiffs,
  type Review,
  type Issue
} from '@/api/reviews'
import type { DiffBlock } from '@/api/projects'
import { highlightDiffContent } from '@/utils/syntaxHighlight'

const route = useRoute()
const router = useRouter()

const reviewId = computed(() => Number(route.params.id))
const review = ref<Review | null>(null)
const issues = ref<Issue[]>([])
const diffBlocks = ref<DiffBlock[]>([])
const expandedIssueIds = ref<Set<number>>(new Set())
const expandedFilePaths = ref<Set<string>>(new Set())
const loading = ref(false)
let sseConnection: EventSource | null = null

interface HighlightedDiffLine {
  type: string
  html: string
}

const statusFilter = ref('')
const severityFilter = ref('')

const filteredIssues = computed(() => {
  return issues.value.filter(i => {
    if (statusFilter.value && i.status !== statusFilter.value) return false
    if (severityFilter.value && i.severity !== severityFilter.value) return false
    return true
  })
})

interface FileGroup {
  filePath: string
  issues: Issue[]
  highCount: number
  mediumCount: number
  lowCount: number
}

const fileGroups = computed<FileGroup[]>(() => {
  const map = new Map<string, Issue[]>()
  for (const issue of filteredIssues.value) {
    const arr = map.get(issue.filePath) || []
    arr.push(issue)
    map.set(issue.filePath, arr)
  }
  const groups: FileGroup[] = []
  for (const [filePath, fileIssues] of map) {
    fileIssues.sort((a, b) => {
      const sevOrder: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }
      return (sevOrder[a.severity] ?? 9) - (sevOrder[b.severity] ?? 9)
    })
    groups.push({
      filePath,
      issues: fileIssues,
      highCount: fileIssues.filter(i => i.severity === 'HIGH').length,
      mediumCount: fileIssues.filter(i => i.severity === 'MEDIUM').length,
      lowCount: fileIssues.filter(i => i.severity === 'LOW').length
    })
  }
  groups.sort((a, b) => b.highCount - a.highCount || b.mediumCount - a.mediumCount)
  return groups
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

function getDiffForFile(filePath: string): DiffBlock | undefined {
  return diffBlocks.value.find(d =>
    d.filePath === filePath ||
    d.filePath.endsWith('/' + filePath) ||
    filePath.endsWith('/' + d.filePath) ||
    d.filePath.replace(/\\/g, '/') === filePath.replace(/\\/g, '/')
  )
}

function getHighlightedDiff(filePath: string): HighlightedDiffLine[] {
  const block = getDiffForFile(filePath)
  if (!block) return []
  try {
    return highlightDiffContent(block.diffContent, block.filePath)
  } catch {
    return []
  }
}

function getDiffLineNumbers(filePath: string): number[] {
  const block = getDiffForFile(filePath)
  if (!block) return []
  const lines: number[] = []
  let newLineNum = 0
  const diffLines = block.diffContent.split('\n')
  for (const line of diffLines) {
    if (line.startsWith('@@')) {
      const match = line.match(/\+(\d+)/)
      if (match) newLineNum = parseInt(match[1]) - 1
      continue
    }
    if (!line.startsWith('-')) {
      newLineNum++
    }
    if (line.startsWith('+')) {
      lines.push(newLineNum)
    }
  }
  return lines
}

function toggleIssueExpand(issueId: number) {
  const newSet = new Set(expandedIssueIds.value)
  if (newSet.has(issueId)) {
    newSet.delete(issueId)
  } else {
    newSet.add(issueId)
    const issue = issues.value.find(i => i.id === issueId)
    if (issue && issue.lineNumber) {
      nextTick(() => scrollToIssueLine(issueId, issue.filePath, issue.lineNumber!))
    }
  }
  expandedIssueIds.value = newSet
}

function scrollToIssueLine(issueId: number, filePath: string, lineNumber: number) {
  const selector = `.diff-line-highlighted-${issueId}`
  const el = document.querySelector(selector) as HTMLElement | null
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

function toggleFileGroup(filePath: string) {
  const newSet = new Set(expandedFilePaths.value)
  if (newSet.has(filePath)) {
    newSet.delete(filePath)
  } else {
    newSet.add(filePath)
  }
  expandedFilePaths.value = newSet
}

function changeTypeLabel(type: string): string {
  switch (type) {
    case 'ADD': return '新增'
    case 'DELETE': return '删除'
    case 'MODIFY': return '修改'
    case 'RENAME': return '重命名'
    default: return type
  }
}

function changeTypeColor(type: string): string {
  switch (type) {
    case 'ADD': return '#22c55e'
    case 'DELETE': return '#ef4444'
    case 'MODIFY': return '#3b82f6'
    default: return 'var(--color-text-muted)'
  }
}

function isIssueLine(filePath: string, diffLineIdx: number, issueLine: number): boolean {
  const block = getDiffForFile(filePath)
  if (!block) return false
  let newLineNum = 0
  let currentDiffIdx = 0
  const diffLines = block.diffContent.split('\n')
  for (const line of diffLines) {
    if (currentDiffIdx === diffLineIdx) {
      if (line.startsWith('+') && !line.startsWith('+++') && newLineNum === issueLine) {
        return true
      }
      if (!line.startsWith('-') && !line.startsWith('@@') && newLineNum === issueLine) {
        return true
      }
      return false
    }
    if (line.startsWith('@@')) {
      const match = line.match(/\+(\d+)/)
      if (match) newLineNum = parseInt(match[1]) - 1
      currentDiffIdx++
      continue
    }
    if (!line.startsWith('-')) {
      newLineNum++
    }
    currentDiffIdx++
  }
  return false
}

async function fetchDiffData() {
  try {
    const res = await getReviewDiffs(reviewId.value)
    diffBlocks.value = res.data
  } catch (e) {
    console.error('Failed to fetch diff data:', e)
  }
}

async function fetchReviewAndIssues() {
  const [reviewRes, issuesRes] = await Promise.all([
    getReview(reviewId.value),
    listIssues(reviewId.value, { page: 0, size: 200 })
  ])
  review.value = reviewRes.data
  issues.value = issuesRes.data.content
}

function subscribeToSse() {
  if (sseConnection) return
  if (review.value?.status !== 'processing') return

  const es = new EventSource(`/api/reviews/${reviewId.value}/stream`)
  let retryCount = 0
  const MAX_RETRIES = 5

  es.addEventListener('progress', (event: MessageEvent) => {
    try {
      const data = JSON.parse(event.data)
      if (review.value) {
        review.value.reviewedFiles = data.reviewedFiles
        review.value.totalFiles = data.totalFiles
      }
    } catch (e) {
      console.error('[SSE] 解析进度事件失败:', e)
    }
  })

  es.addEventListener('completed', async () => {
    stopSse()
    await fetchReviewAndIssues()
  })

  es.addEventListener('failed', async () => {
    stopSse()
    await fetchReviewAndIssues()
  })

  es.onerror = () => {
    retryCount++
    if (retryCount > MAX_RETRIES) {
      console.log(`[SSE] 重试次数超限，断开连接: reviewId=${reviewId.value}`)
      stopSse()
    }
  }

  sseConnection = es
}

function stopSse() {
  if (sseConnection) {
    sseConnection.close()
    sseConnection = null
  }
}

async function fetchData() {
  loading.value = true
  try {
    await fetchReviewAndIssues()
    fetchDiffData()
    subscribeToSse()
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

async function exportPDF() {
  try {
    const blob = await exportReviewPdf(reviewId.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `code-review-${reviewId.value}.pdf`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 100)
    ElMessage.success('PDF 报告已下载')
  } catch (e) {
    console.error('Failed to export PDF:', e)
  }
}

function goBack() {
  if (review.value?.project?.id) {
    router.push(`/projects/${review.value.project.id}/reviews`)
  } else {
    router.push('/projects')
  }
}

onMounted(fetchData)
onUnmounted(stopSse)
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
          <el-button :icon="Download" round @click="exportPDF">导出 PDF</el-button>
          <el-button :icon="Download" round @click="exportJSON">导出 JSON</el-button>
        </div>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--color-danger-light); color: var(--color-danger);">
          <el-icon :size="20"><WarningFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ review?.highCount || 0 }}</div>
          <div class="stat-label">高危</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--color-warning-light); color: var(--color-warning);">
          <el-icon :size="20"><Promotion /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ review?.mediumCount || 0 }}</div>
          <div class="stat-label">中危</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--color-info-light); color: var(--color-info);">
          <el-icon :size="20"><InfoFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ review?.lowCount || 0 }}</div>
          <div class="stat-label">低危</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--color-primary-light); color: var(--color-primary);">
          <el-icon :size="20"><Document /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ review?.totalIssues || 0 }}</div>
          <div class="stat-label">问题总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #f0fdf4; color: var(--color-success);">
          <el-icon :size="20"><CircleCheckFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ issues.filter(i => i.status === 'resolved').length }}</div>
          <div class="stat-label">已修复</div>
        </div>
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

    <div v-if="review?.status === 'processing'" class="processing-banner">
      <div class="processing-icon">
        <el-icon :size="20" class="is-loading"><Loading /></el-icon>
      </div>
      <div class="processing-info">
        <span class="processing-title">AI 审查进行中</span>
        <span class="processing-detail">
          已完成 {{ review?.reviewedFiles || 0 }}/{{ review?.totalFiles || 0 }} 个文件
          &middot; 当前已发现 {{ review?.totalIssues || 0 }} 个问题
        </span>
      </div>
      <div class="processing-progress">
        <el-progress
          :percentage="review?.totalFiles ? Math.round((review.reviewedFiles || 0) / review.totalFiles * 100) : 0"
          :stroke-width="6"
          :show-text="false"
        />
      </div>
    </div>

    <div v-if="fileGroups.length > 0" class="file-groups">
      <div
        v-for="group in fileGroups"
        :key="group.filePath"
        class="file-group"
      >
        <div class="file-group-header" @click="toggleFileGroup(group.filePath)">
          <div class="file-group-left">
            <el-icon :size="14" style="color: var(--color-text-muted);">
              <component :is="expandedFilePaths.has(group.filePath) ? ArrowDown : ArrowRight" />
            </el-icon>
            <el-icon :size="16" style="color: var(--color-text-secondary);"><Folder /></el-icon>
            <span class="file-group-path">{{ group.filePath }}</span>
            <span v-if="getDiffForFile(group.filePath)?.changeType" class="file-group-change-type" :style="{ color: changeTypeColor(getDiffForFile(group.filePath)!.changeType) }">
              {{ changeTypeLabel(getDiffForFile(group.filePath)!.changeType) }}
            </span>
          </div>
          <div class="file-group-right">
            <div class="file-group-stats">
              <span v-if="group.highCount" class="file-stat-badge" style="color: var(--color-danger);">🔴 {{ group.highCount }}</span>
              <span v-if="group.mediumCount" class="file-stat-badge" style="color: var(--color-warning);">🟡 {{ group.mediumCount }}</span>
              <span v-if="group.lowCount" class="file-stat-badge" style="color: var(--color-info);">🔵 {{ group.lowCount }}</span>
              <span class="file-stat-badge file-stat-total">{{ group.issues.length }} 个问题</span>
            </div>
          </div>
        </div>

        <div v-if="expandedFilePaths.has(group.filePath)" class="file-group-body">
          <div
            v-for="issue in group.issues"
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
              <div class="issue-location" @click="toggleIssueExpand(issue.id!)" style="cursor: pointer;">
                <el-icon :size="12" style="margin-right: 2px; color: var(--color-text-muted);">
                  <component :is="expandedIssueIds.has(issue.id!) ? ArrowDown : ArrowRight" />
                </el-icon>
                <span v-if="issue.lineNumber" class="issue-line-inline">:{{ issue.lineNumber }}</span>
              </div>
              <p class="issue-message">{{ issue.message }}</p>
              <div v-if="issue.suggestion" class="issue-suggestion">
                <el-icon :size="14"><InfoFilled /></el-icon>
                <span>{{ issue.suggestion }}</span>
              </div>
              <div v-if="expandedIssueIds.has(issue.id!)" class="diff-panel">
                <div v-if="getDiffForFile(issue.filePath)" class="diff-content-wrapper">
                  <div class="diff-lines">
                    <div
                      v-for="(line, idx) in getHighlightedDiff(issue.filePath)"
                      :key="idx"
                      class="diff-line"
                      :class="[
                        'diff-line-' + line.type,
                        {
                          'diff-line-highlighted': issue.lineNumber && isIssueLine(issue.filePath, idx, issue.lineNumber!),
                          ['diff-line-highlighted-' + issue.id]: issue.lineNumber && isIssueLine(issue.filePath, idx, issue.lineNumber!)
                        }
                      ]"
                      v-html="line.html"
                    />
                  </div>
                </div>
                <div v-else class="diff-empty">
                  <el-icon :size="14"><InfoFilled /></el-icon>
                  <span>该文件不在本次 diff 范围内或已无法获取变更内容</span>
                </div>
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

.processing-banner {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.08), rgba(59, 130, 246, 0.03));
  border: 1px solid rgba(59, 130, 246, 0.25);
  border-radius: var(--radius-lg);
}

.processing-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}

.processing-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.processing-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
}

.processing-detail {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.processing-progress {
  width: 180px;
  flex-shrink: 0;
}

.file-groups {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.file-group {
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: box-shadow var(--transition);
}

.file-group:hover {
  box-shadow: var(--shadow-sm);
}

.file-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  cursor: pointer;
  user-select: none;
  background: var(--color-surface);
  transition: background var(--transition);
}

.file-group-header:hover {
  background: var(--color-border-light);
}

.file-group-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.file-group-path {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-group-change-type {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  background: var(--color-border-light);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.file-group-right {
  flex-shrink: 0;
  margin-left: 12px;
}

.file-group-stats {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-stat-badge {
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.file-stat-total {
  color: var(--color-text-muted);
  font-weight: 500;
  font-size: 11px;
}

.file-group-body {
  border-top: 1px solid var(--color-border-light);
  padding: 10px 18px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.issue-item {
  display: flex;
  align-items: flex-start;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  padding: 14px 16px;
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

.issue-line-inline {
  font-size: 12px;
  color: var(--color-text-muted);
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

.diff-panel {
  margin-top: 12px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.diff-lines {
  max-height: 320px;
  overflow-y: auto;
}

.diff-content-wrapper {
  background: #1e1e2e;
}

.diff-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: #2a2a3e;
  border-bottom: 1px solid #3a3a4e;
}

.diff-file-name {
  font-size: 12px;
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
  color: #cdd6f4;
}

.diff-change-type {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  background: #313244;
  border-radius: var(--radius-sm);
}

.diff-lines {
  max-height: 360px;
  overflow-y: auto;
  font-size: 12px;
  line-height: 1.7;
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
  tab-size: 4;
}

.diff-line {
  padding: 1px 14px;
  white-space: pre;
  overflow-x: auto;
}

.diff-line-header {
  color: #89b4fa;
  background: #313244;
}

.diff-line-add {
  color: #a6e3a1;
  background: rgba(166, 227, 161, 0.06);
}

.diff-line-remove {
  color: #f38ba8;
  background: rgba(243, 139, 168, 0.06);
}

.diff-line-context {
  color: #bac2de;
}

.diff-line-highlighted {
  background: rgba(250, 179, 135, 0.18);
  border-left: 3px solid #fab387;
  padding-left: 11px;
}

.diff-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 14px;
  font-size: 13px;
  color: var(--color-text-muted);
  background: var(--color-surface);
}
</style>
