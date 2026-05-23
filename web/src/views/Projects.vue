<script setup lang="ts">
import { ref, onMounted } from 'vue'

function debounce<T extends (...args: any[]) => any>(fn: T, delay: number) {
  let timer: ReturnType<typeof setTimeout> | null = null
  return (...args: Parameters<T>) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), delay)
  }
}
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, FolderOpened, Delete, View } from '@element-plus/icons-vue'
import {
  listProjects,
  createProject,
  deleteProject,
  type Project
} from '@/api/projects'
import type { PageResult } from '@/api/types'

const router = useRouter()

const projects = ref<Project[]>([])
const loading = ref(false)
const searchName = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)

const form = ref<Project>({
  name: '',
  repoPath: '',
  language: 'Java'
})

async function fetchProjects() {
  loading.value = true
  try {
    const res = await listProjects({
      name: searchName.value || undefined,
      page: 0,
      size: 50
    })
    projects.value = res.data.content
  } catch (e) {
    console.error('Failed to fetch projects:', e)
  } finally {
    loading.value = false
  }
}

const debouncedFetchProjects = debounce(fetchProjects, 300)

async function handleCreate() {
  submitting.value = true
  try {
    const res = await createProject(form.value)
    if (res.code === 201) {
      ElMessage.success('项目创建成功')
      dialogVisible.value = false
      form.value = { name: '', repoPath: '', language: 'Java' }
      await fetchProjects()
    }
  } catch (e) {
    console.error('Failed to create project:', e)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(project: Project) {
  try {
    await ElMessageBox.confirm(
      `确定要删除项目「${project.name}」吗？相关的审查记录和问题也将被删除。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteProject(project.id!)
    ElMessage.success('项目已删除')
    await fetchProjects()
  } catch (e) {
    if (e !== 'cancel') console.error('Failed to delete project:', e)
  }
}

function viewReviews(project: Project) {
  router.push(`/projects/${project.id}/reviews`)
}

onMounted(fetchProjects)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div style="display: flex; align-items: center; justify-content: space-between;">
        <div>
          <h1 class="page-title">项目列表</h1>
          <p class="page-subtitle">管理已接入的 Git 仓库，触发代码审查</p>
        </div>
        <el-button type="primary" :icon="Plus" @click="dialogVisible = true" round>
          新建项目
        </el-button>
      </div>
    </div>

    <div class="toolbar">
      <el-input
        v-model="searchName"
        placeholder="搜索项目名称..."
        :prefix-icon="Search"
        clearable
        style="width: 320px;"
        @input="debouncedFetchProjects"
      />
    </div>

    <div v-loading="loading" style="min-height: 200px;">
      <div v-if="projects.length > 0" class="card-grid">
        <el-card
          v-for="project in projects"
          :key="project.id"
          shadow="never"
          class="project-card"
        >
          <div class="project-card-header">
            <div class="project-avatar">
              <el-icon :size="22"><FolderOpened /></el-icon>
            </div>
            <div class="project-info">
              <h3 class="project-name">{{ project.name }}</h3>
              <span class="project-language">{{ project.language || 'Java' }}</span>
            </div>
          </div>
          <div class="project-meta">
            <div class="meta-item">
              <span class="meta-label">仓库路径</span>
              <span class="meta-value" :title="project.repoPath">{{ project.repoPath }}</span>
            </div>
            <div class="meta-item" v-if="project.currentBranch">
              <span class="meta-label">当前分支</span>
              <span class="meta-value">
                <el-tag size="small" type="info" effect="plain">{{ project.currentBranch }}</el-tag>
              </span>
            </div>
          </div>
          <div class="project-actions">
            <span class="project-time">{{ project.createdAt?.substring(0, 10) }}</span>
            <div class="action-btns">
              <el-button size="small" text type="primary" @click="viewReviews(project)">
                <el-icon :size="15"><View /></el-icon>
                审查记录
              </el-button>
              <el-button size="small" text type="danger" @click="handleDelete(project)">
                <el-icon :size="15"><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
      <div v-else class="empty-state">
        <el-icon><FolderOpened /></el-icon>
        <p>暂无项目</p>
        <p style="font-size: 12px;">点击"新建项目"接入 Git 仓库</p>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      title="新建项目"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="项目名称" required>
          <el-input v-model="form.name" placeholder="输入项目名称，如 my-app" />
        </el-form-item>
        <el-form-item label="Git 仓库路径" required>
          <el-input
            v-model="form.repoPath"
            placeholder="输入本地 Git 仓库绝对路径，如 E:/projects/my-app"
          />
        </el-form-item>
        <el-form-item label="编程语言">
          <el-select v-model="form.language" style="width: 100%;">
            <el-option label="Java" value="Java" />
            <el-option label="Python" value="Python" />
            <el-option label="Go" value="Go" />
            <el-option label="JavaScript" value="JavaScript" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建项目</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 20px;
}

.project-card {
  cursor: default;
}

.project-card :deep(.el-card__body) {
  padding: 20px;
}

.project-card-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 16px;
}

.project-avatar {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  background: var(--color-primary-light);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.project-info {
  min-width: 0;
}

.project-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-language {
  display: inline-block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
  background: var(--color-border-light);
  padding: 1px 8px;
  border-radius: 100px;
}

.project-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-label {
  font-size: 11px;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.meta-value {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-light);
}

.project-time {
  font-size: 11px;
  color: var(--color-text-muted);
}

.action-btns {
  display: flex;
  gap: 4px;
}
</style>
