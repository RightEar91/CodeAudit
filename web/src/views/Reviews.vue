<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, ArrowLeft, ArrowRight, ArrowDown, CircleCheck, CircleClose, Loading, Clock, VideoPlay,
  Search, DocumentAdd, DocumentDelete, Edit
} from '@element-plus/icons-vue'
import {
  listReviews,
  createReview,
  cancelReview,
  deleteReview,
  type Review
} from '@/api/reviews'
import type { PageResult } from '@/api/types'
import {
  getProject,
  listBranches,
  listCommits,
  previewDiff,
  type Project,
  type BranchInfo,
  type CommitInfo,
  type DiffBlock
} from '@/api/projects'
import { highlightDiffContent } from '@/utils/syntaxHighlight'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => Number(route.params.id))
const project = ref<Project | null>(null)
const reviews = ref<Review[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)

const form = ref({
  title: 'Code Review',
  fromRef: 'HEAD~1',
  toRef: 'HEAD'
})

const branches = ref<BranchInfo[]>([])
const commits = ref<CommitInfo[]>([])
const diffBlocks = ref<DiffBlock[]>([])
const previewLoading = ref(false)
const refsLoading = ref(false)
const expandedFile = ref<string | null>(null)

let pollTimer: ReturnType<typeof setInterval> | null = null

const refOptions = computed(() => {
  const options: { label: string; options: { value: string; label: string }[] }[] = []

  if (branches.value.length > 0) {
    options.push({
      label: '分支',
      options: branches.value.map(b => ({
        value: b.name,
        label: b.isHead ? `${b.name} (当前)` : b.name
      }))
    })
  }

  if (commits.value.length > 0) {
    options.push({
      label: '最近提交',
      options: commits.value.map(c => ({
        value: c.hash,
        label: `${c.shortHash} - ${c.message}`
      }))
    })
  }

  return options
})

const changeTypeIcon = (type: string) => {
  switch (type) {
    case 'ADD': return DocumentAdd
    case 'DELETE': return DocumentDelete
    case 'MODIFY': return Edit
    default: return Edit
  }
}

const changeTypeLabel = (type: string) => {
  switch (type) {
    case 'ADD': return '新增'
    case 'DELETE': return '删除'
    case 'MODIFY': return '修改'
    case 'RENAME': return '重命名'
    case 'COPY': return '复制'
    default: return type
  }
}

const changeTypeColor = (type: string) => {
  switch (type) {
    case 'ADD': return '#22c55e'
    case 'DELETE': return '#ef4444'
    case 'MODIFY': return '#3b82f6'
    default: return 'var(--color-text-muted)'
  }
}

const totalAddedLines = computed(() => diffBlocks.value.reduce((sum, b) => sum + b.addedLines, 0))
const totalRemovedLines = computed(() => diffBlocks.value.reduce((sum, b) => sum + b.removedLines, 0))

interface HighlightedDiffLine {
  type: string
  html: string
}

function getHighlightedDiff(block: DiffBlock): HighlightedDiffLine[] {
  try {
    return highlightDiffContent(block.diffContent, block.filePath)
  } catch {
    return []
  }
}

function toggleFile(filePath: string) {
  expandedFile.value = expandedFile.value === filePath ? null : filePath
}

function openDialog() {
  dialogVisible.value = true
  diffBlocks.value = []
  expandedFile.value = null
  fetchRefs()
}

const statusIcon = (status: string) => {
  switch (status) {
    case 'completed': return CircleCheck
    case 'processing': return Loading
    case 'failed': return CircleClose
    default: return Clock
  }
}

const statusColor = (status: string) => {
  switch (status) {
    case 'completed': return 'success'
    case 'processing': return 'primary'
    case 'failed': return 'danger'
    default: return 'warning'
  }
}

const statusLabel = (status: string) => {
  switch (status) {
    case 'pending': return '等待中'
    case 'processing': return '审查中'
    case 'completed': return '已完成'
    case 'failed': return '失败'
    default: return status
  }
}

const severitySummary = (review: Review) => {
  return [
    { label: '高', value: review.highCount || 0, color: 'var(--color-danger)' },
    { label: '中', value: review.mediumCount || 0, color: 'var(--color-warning)' },
    { label: '低', value: review.lowCount || 0, color: 'var(--color-info)' }
  ].filter(s => s.value > 0)
}

const formatDuration = (ms?: number) => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}min`
}

const formatRelativeTime = (dateStr?: string) => {
  if (!dateStr) return ''
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  const diff = now - then
  if (diff < 0) return ''
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return '刚刚'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  const months = Math.floor(days / 30)
  return `${months} 个月前`
}

const formatDateTime = (dateStr?: string) => {
  if (!dateStr) return '-'
  return dateStr.substring(0, 16).replace('T', ' ')
}

const progressPercent = (review: Review) => {
  if (!review.totalFiles || review.totalFiles === 0) return 0
  return Math.round((review.reviewedFiles || 0) / review.totalFiles * 100)
}

async function fetchProject() {
  try {
    const res = await getProject(projectId.value)
    project.value = res.data
  } catch (e) {
    console.error('Failed to fetch project:', e)
  }
}

async function fetchReviews() {
  loading.value = true
  try {
    const res = await listReviews(projectId.value, { page: 0, size: 50 })
    reviews.value = res.data.content
  } finally {
    loading.value = false
  }
}

async function fetchRefs() {
  refsLoading.value = true
  try {
    const [branchesRes, commitsRes] = await Promise.all([
      listBranches(projectId.value),
      listCommits(projectId.value, 30)
    ])
    branches.value = branchesRes.data
    commits.value = commitsRes.data
  } catch (e) {
    console.error('Failed to fetch refs:', e)
  } finally {
    refsLoading.value = false
  }
}

async function handlePreviewDiff() {
  if (!form.value.fromRef || !form.value.toRef) return
  previewLoading.value = true
  try {
    const res = await previewDiff(projectId.value, form.value.fromRef, form.value.toRef)
    diffBlocks.value = res.data
  } catch (e) {
    console.error('Failed to preview diff:', e)
    diffBlocks.value = []
  } finally {
    previewLoading.value = false
  }
}

watch(dialogVisible, (val) => {
  if (!val) {
    diffBlocks.value = []
    expandedFile.value = null
  }
})

async function handleCreate() {
  submitting.value = true
  try {
    const res = await createReview(projectId.value, {
      title: form.value.title,
      fromRef: form.value.fromRef,
      toRef: form.value.toRef
    })
    if (res.code === 200 || res.code === 201) {
      ElMessage.success('审查任务已创建')
      dialogVisible.value = false
      form.value = { title: 'Code Review', fromRef: 'HEAD~1', toRef: 'HEAD' }
      await fetchReviews()
      startPolling()
    }
  } catch (e) {
    console.error('Failed to create review:', e)
  } finally {
    submitting.value = false
  }
}

async function handleCancel(review: Review) {
  try {
    await ElMessageBox.confirm('确定要取消该审查吗？', '取消审查', {
      confirmButtonText: '确定取消',
      cancelButtonText: '返回',
      type: 'warning'
    })
    await cancelReview(review.id!)
    ElMessage.success('审查已取消')
    await fetchReviews()
  } catch (e) {
    if (e !== 'cancel') console.error('Failed to cancel review:', e)
  }
}

async function handleDelete(review: Review) {
  try {
    await ElMessageBox.confirm('确定要删除该审查记录及所有关联问题吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteReview(review.id!)
    ElMessage.success('审查已删除')
    await fetchReviews()
  } catch (e) {
    if (e !== 'cancel') console.error('Failed to delete review:', e)
  }
}

function viewDetail(review: Review) {
  router.push(`/reviews/${review.id}`)
}

function goBack() {
  router.push('/projects')
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    const hasProcessing = reviews.value.some(r => r.status === 'processing' || r.status === 'pending')
    if (!hasProcessing) {
      if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
      }
      return
    }
    await fetchReviews()
  }, 3000)
}

onMounted(async () => {
  await Promise.all([fetchProject(), fetchReviews()])
  startPolling()
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button text :icon="ArrowLeft" @click="goBack" style="margin-bottom: 12px;">
        返回项目列表
      </el-button>
      <div style="display: flex; align-items: center; justify-content: space-between;">
        <div>
          <h1 class="page-title">{{ project?.name || '审查记录' }}</h1>
          <p class="page-subtitle">{{ project?.repoPath }}</p>
        </div>
        <el-button type="primary" :icon="Plus" @click="openDialog" round>
          新建审查
        </el-button>
      </div>
    </div>

    <div v-loading="loading" style="min-height: 200px;">
      <div v-if="reviews.length > 0" class="reviews-list">
        <div
          v-for="review in reviews"
          :key="review.id"
          class="review-item"
          :class="{ clickable: review.status === 'completed' }"
          @click="review.status === 'completed' ? viewDetail(review) : null"
        >
          <div class="review-main">
            <div class="review-status-icon">
              <el-icon :size="20" :color="`var(--el-color-${statusColor(review.status)})`">
                <component :is="statusIcon(review.status)" :class="{ 'is-loading': review.status === 'processing' }" />
              </el-icon>
            </div>
            <div class="review-body">
              <div class="review-title-row">
                <h3 class="review-title">{{ review.title }}</h3>
                <el-tag
                  :type="statusColor(review.status)"
                  size="small"
                  effect="light"
                >
                  {{ statusLabel(review.status) }}
                </el-tag>
              </div>
              <div class="review-meta">
                <span>{{ formatDateTime(review.createdAt) }}</span>
                <span class="meta-rel-time">（{{ formatRelativeTime(review.createdAt) }}）</span>
                <span class="meta-sep">|</span>
                <span>耗时 {{ formatDuration(review.durationMs) }}</span>
                <span class="meta-sep">|</span>
                <span>问题 {{ review.totalIssues || 0 }} 个</span>
              </div>
              <div class="severity-tags" v-if="severitySummary(review).length > 0">
                <span v-for="s in severitySummary(review)" :key="s.label" class="severity-dot" :style="{ color: s.color }">
                  <span class="dot" :style="{ background: s.color }"></span>
                  {{ s.label }}危 {{ s.value }}
                </span>
              </div>
              <div v-if="review.status === 'processing' && review.totalFiles" class="progress-row">
                <el-progress
                  :percentage="progressPercent(review)"
                  :stroke-width="6"
                  :show-text="true"
                  :color="'#3b82f6'"
                >
                  <span class="progress-text">{{ review.reviewedFiles || 0 }}/{{ review.totalFiles }} 文件</span>
                </el-progress>
              </div>
              <div v-if="review.errorMessage" class="error-msg">
                {{ review.errorMessage }}
              </div>
            </div>
          </div>
          <div class="review-actions" @click.stop>
            <template v-if="review.status === 'processing' || review.status === 'pending'">
              <el-button size="small" text type="warning" @click="handleCancel(review)">取消</el-button>
            </template>
            <el-button size="small" text type="danger" @click="handleDelete(review)">删除</el-button>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-icon><VideoPlay /></el-icon>
        <p>暂无审查记录</p>
        <p style="font-size: 12px;">点击"新建审查"开始分析代码变更</p>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      title="新建审查"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="审查标题">
          <el-input v-model="form.title" placeholder="Code Review" />
        </el-form-item>
        <div class="refs-row">
          <el-form-item label="源引用（fromRef）" class="ref-item">
            <el-select
              v-model="form.fromRef"
              filterable
              allow-create
              default-first-option
              placeholder="选择分支或 commit，或手动输入如 HEAD~1"
              style="width: 100%"
              :loading="refsLoading"
              @change="diffBlocks = []"
            >
              <el-option-group
                v-for="group in refOptions"
                :key="group.label"
                :label="group.label"
              >
                <el-option
                  v-for="item in group.options"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item label="目标引用（toRef）" class="ref-item">
            <el-select
              v-model="form.toRef"
              filterable
              allow-create
              default-first-option
              placeholder="选择分支或 commit，或手动输入如 HEAD"
              style="width: 100%"
              :loading="refsLoading"
              @change="diffBlocks = []"
            >
              <el-option-group
                v-for="group in refOptions"
                :key="group.label"
                :label="group.label"
              >
                <el-option
                  v-for="item in group.options"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-option-group>
            </el-select>
          </el-form-item>
        </div>
        <div class="preview-btn-row">
          <el-button
            type="default"
            :icon="Search"
            size="small"
            :loading="previewLoading"
            :disabled="!form.fromRef || !form.toRef"
            @click="handlePreviewDiff"
          >
            预览变更
          </el-button>
        </div>
        <div v-if="diffBlocks.length > 0" class="diff-preview">
          <div class="preview-header">
            <span class="preview-title">变更文件列表</span>
            <span class="preview-stats">
              <span class="stat added">+{{ totalAddedLines }}</span>
              <span class="stat removed">-{{ totalRemovedLines }}</span>
              <span class="stat">{{ diffBlocks.length }} 个文件</span>
            </span>
          </div>
          <div class="preview-files">
            <div v-for="block in diffBlocks" :key="block.filePath" class="preview-file-wrapper">
              <div
                class="preview-file-item"
                :class="{ expanded: expandedFile === block.filePath }"
                @click="toggleFile(block.filePath)"
              >
                <span class="file-expand-icon">
                  <el-icon :size="12">
                    <component :is="expandedFile === block.filePath ? ArrowDown : ArrowRight" />
                  </el-icon>
                </span>
                <span class="file-icon" :style="{ color: changeTypeColor(block.changeType) }">
                  <el-icon :size="14">
                    <component :is="changeTypeIcon(block.changeType)" />
                  </el-icon>
                </span>
                <span class="file-path">{{ block.filePath }}</span>
                <span class="file-change-type" :style="{ color: changeTypeColor(block.changeType) }">
                  {{ changeTypeLabel(block.changeType) }}
                </span>
                <span class="file-lines">
                  <span class="added-lines">+{{ block.addedLines }}</span>
                  <span class="removed-lines">-{{ block.removedLines }}</span>
                </span>
              </div>
              <div v-if="expandedFile === block.filePath" class="diff-content">
                <div
                  v-for="(line, idx) in getHighlightedDiff(block)"
                  :key="idx"
                  class="diff-line"
                  :class="line.type"
                  v-html="line.html"
                />
              </div>
            </div>
          </div>
        </div>
        <div v-else-if="!previewLoading && diffBlocks.length === 0 && !form.fromRef && !form.toRef" class="preview-hint">
          选择源引用和目标引用后，点击"预览变更"查看将要审查的文件
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">开始审查</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.reviews-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-item {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: 18px 20px;
  transition: all var(--transition);
}

.review-item.clickable {
  cursor: pointer;
}

.review-item.clickable:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-border);
}

.review-main {
  display: flex;
  gap: 14px;
  flex: 1;
  min-width: 0;
}

.review-status-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.review-body {
  flex: 1;
  min-width: 0;
}

.review-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.review-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.review-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.meta-sep {
  opacity: 0.4;
}

.meta-rel-time {
  opacity: 0.75;
}

.severity-tags {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.severity-dot {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.progress-row {
  margin-top: 12px;
  max-width: 320px;
}

.progress-text {
  font-size: 11px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.error-msg {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-danger);
  background: var(--color-danger-light);
  padding: 6px 10px;
  border-radius: var(--radius-sm);
}

.review-actions {
  flex-shrink: 0;
  display: flex;
  gap: 4px;
  margin-left: 12px;
}

.refs-row {
  display: flex;
  gap: 16px;
}

.ref-item {
  flex: 1;
  min-width: 0;
}

.preview-btn-row {
  margin-bottom: 12px;
}

.diff-preview {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--el-color-info-light-9);
  border-bottom: 1px solid var(--color-border-light);
}

.preview-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.preview-stats {
  display: flex;
  gap: 10px;
  font-size: 12px;
  font-weight: 500;
}

.stat {
  color: var(--color-text-muted);
}

.stat.added {
  color: #22c55e;
}

.stat.removed {
  color: #ef4444;
}

.preview-files {
  max-height: 360px;
  overflow-y: auto;
}

.preview-file-wrapper {
  border-bottom: 1px solid var(--color-border-light);
}

.preview-file-wrapper:last-child {
  border-bottom: none;
}

.preview-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}

.preview-file-item:hover {
  background: var(--el-color-info-light-9);
}

.preview-file-item.expanded {
  background: var(--el-color-primary-light-9);
}

.file-expand-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  color: var(--color-text-muted);
}

.file-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.file-path {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-primary);
  font-family: monospace;
  font-size: 12px;
}

.file-change-type {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 500;
  min-width: 36px;
  text-align: right;
}

.file-lines {
  flex-shrink: 0;
  display: flex;
  gap: 6px;
  font-family: monospace;
  font-size: 11px;
  min-width: 54px;
  justify-content: flex-end;
}

.added-lines {
  color: #22c55e;
}

.removed-lines {
  color: #ef4444;
}

.preview-hint {
  padding: 24px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
}

.diff-content {
  background: #1e1e1e;
  border-top: 1px solid var(--color-border-light);
  padding: 4px 0;
  max-height: 320px;
  overflow: auto;
}

.diff-line {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 20px;
  min-height: 20px;
  white-space: pre;
  padding: 0 14px;
}

.diff-line.header {
  background: #2a2a2a;
  color: #569cd6;
  font-weight: 600;
}

.diff-line.add {
  background: #1b3a1b;
}

.diff-line.remove {
  background: #3a1b1b;
}

.diff-line.context {
  background: transparent;
  color: #d4d4d4;
}

.diff-line.add :deep(.hljs-keyword),
.diff-line.add :deep(.hljs-type),
.diff-line.add :deep(.hljs-built_in) {
  color: #6ab8ff;
}

.diff-line.add :deep(.hljs-string) {
  color: #c39178;
}

.diff-line.add :deep(.hljs-number) {
  color: #b8d7a3;
}

.diff-line.add :deep(.hljs-comment) {
  color: #6a9955;
  font-style: italic;
}

.diff-line.add :deep(.hljs-annotation),
.diff-line.add :deep(.hljs-meta) {
  color: #d7ba7d;
}

.diff-line.add :deep(.hljs-title) {
  color: #b8e6b8;
}

.diff-line.add :deep(.hljs-params) {
  color: #b8e6b8;
}

.diff-line.add :deep(.hljs-attr) {
  color: #9cdcfe;
}

.diff-line.add :deep(.hljs-literal) {
  color: #569cd6;
}

.diff-line.add :deep(.hljs-function) {
  color: #dcdcaa;
}

.diff-line.add :deep(.hljs-variable) {
  color: #9cdcfe;
}

.diff-line.add :deep(.hljs-property) {
  color: #9cdcfe;
}

.diff-line.remove :deep(.hljs-keyword),
.diff-line.remove :deep(.hljs-type),
.diff-line.remove :deep(.hljs-built_in) {
  color: #6ab8ff;
}

.diff-line.remove :deep(.hljs-string) {
  color: #c39178;
}

.diff-line.remove :deep(.hljs-number) {
  color: #b8d7a3;
}

.diff-line.remove :deep(.hljs-comment) {
  color: #6a9955;
  font-style: italic;
}

.diff-line.remove :deep(.hljs-annotation),
.diff-line.remove :deep(.hljs-meta) {
  color: #d7ba7d;
}

.diff-line.remove :deep(.hljs-title) {
  color: #e6b8b8;
}

.diff-line.remove :deep(.hljs-params) {
  color: #e6b8b8;
}

.diff-line.remove :deep(.hljs-attr) {
  color: #9cdcfe;
}

.diff-line.remove :deep(.hljs-literal) {
  color: #569cd6;
}

.diff-line.remove :deep(.hljs-function) {
  color: #dcdcaa;
}

.diff-line.remove :deep(.hljs-variable) {
  color: #9cdcfe;
}

.diff-line.remove :deep(.hljs-property) {
  color: #9cdcfe;
}

.diff-line.context :deep(.hljs-keyword),
.diff-line.context :deep(.hljs-type),
.diff-line.context :deep(.hljs-built_in) {
  color: #569cd6;
}

.diff-line.context :deep(.hljs-string) {
  color: #ce9178;
}

.diff-line.context :deep(.hljs-number) {
  color: #b5cea8;
}

.diff-line.context :deep(.hljs-comment) {
  color: #6a9955;
  font-style: italic;
}

.diff-line.context :deep(.hljs-annotation),
.diff-line.context :deep(.hljs-meta) {
  color: #d7ba7d;
}

.diff-line.context :deep(.hljs-title) {
  color: #dcdcaa;
}

.diff-line.context :deep(.hljs-params) {
  color: #d4d4d4;
}

.diff-line.context :deep(.hljs-attr) {
  color: #9cdcfe;
}

.diff-line.context :deep(.hljs-literal) {
  color: #569cd6;
}

.diff-line.context :deep(.hljs-function) {
  color: #dcdcaa;
}

.diff-line.context :deep(.hljs-variable) {
  color: #9cdcfe;
}

.diff-line.context :deep(.hljs-property) {
  color: #9cdcfe;
}

.diff-line.context :deep(.hljs-symbol) {
  color: #79c0ff;
}

.diff-line.context :deep(.hljs-link) {
  color: #569cd6;
  text-decoration: underline;
}

.diff-line.context :deep(.hljs-operator) {
  color: #d4d4d4;
}

.diff-line.context :deep(.hljs-punctuation),
.diff-line.add :deep(.hljs-punctuation),
.diff-line.remove :deep(.hljs-punctuation) {
  color: #d4d4d4;
}
</style>
