<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, ArrowLeft, CircleCheck, CircleClose, Loading, Clock, VideoPlay
} from '@element-plus/icons-vue'
import {
  listReviews,
  createReview,
  cancelReview,
  deleteReview,
  getReview,
  type Review
} from '@/api/reviews'
import type { PageResult } from '@/api/types'
import { getProject, type Project } from '@/api/projects'

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

let pollTimer: ReturnType<typeof setInterval> | null = null

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
        <el-button type="primary" :icon="Plus" @click="dialogVisible = true" round>
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
                <span>{{ review.createdAt?.substring(0, 16)?.replace('T', ' ') }}</span>
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
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="审查标题">
          <el-input v-model="form.title" placeholder="Code Review" />
        </el-form-item>
        <el-form-item label="源引用（fromRef）">
          <el-input v-model="form.fromRef" placeholder="HEAD~1" />
        </el-form-item>
        <el-form-item label="目标引用（toRef）">
          <el-input v-model="form.toRef" placeholder="HEAD" />
        </el-form-item>
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
</style>
