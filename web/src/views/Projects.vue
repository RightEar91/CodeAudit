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
import { Plus, Search, FolderOpened, Delete, View, Folder } from '@element-plus/icons-vue'
import {
  listProjects,
  createProject,
  deleteProject,
  type Project
} from '@/api/projects'
import type { PageResult } from '@/api/types'
import { browseDirectory, type DirEntry } from '@/api/filesystem'

const router = useRouter()

const projects = ref<Project[]>([])
const loading = ref(false)
const searchName = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)

const form = ref<Project>({
  name: '',
  repoType: 'LOCAL',
  repoPath: '',
  repoUrl: '',
  language: 'Java'
})

const dirChooserVisible = ref(false)
const dirEntries = ref<DirEntry[]>([])
const currentPath = ref('')
const breadcrumbs = ref<{ name: string; path: string }[]>([])
const dirLoading = ref(false)

async function openDirectoryChooser() {
  dirChooserVisible.value = true
  await loadDirectory('')
}

async function loadDirectory(path: string) {
  dirLoading.value = true
  try {
    const res = await browseDirectory(path || undefined)
    dirEntries.value = res.data || []
    currentPath.value = path || ''
    buildBreadcrumbs(path)
  } catch (e) {
    console.error('Failed to browse directory:', e)
  } finally {
    dirLoading.value = false
  }
}

function buildBreadcrumbs(path: string) {
  if (!path) {
    breadcrumbs.value = []
    return
  }
  const parts = path.replace(/\\/g, '/').split('/').filter(Boolean)
  const crumbs: { name: string; path: string }[] = []
  let accumulated = ''
  if (path.match(/^[A-Za-z]:/)) {
    accumulated = parts[0]
    crumbs.push({ name: parts[0], path: accumulated })
    parts.shift()
  }
  for (const part of parts) {
    accumulated = accumulated ? accumulated + '/' + part : part
    crumbs.push({ name: part, path: accumulated })
  }
  breadcrumbs.value = crumbs
}

function navigateToPath(path: string) {
  loadDirectory(path)
}

function selectDirectory(entry: DirEntry) {
  form.value.repoPath = entry.path
  dirChooserVisible.value = false
}

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
      form.value = { name: '', repoType: 'LOCAL', repoPath: '', repoUrl: '', language: 'Java' }
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
              <div class="project-tags">
                <el-tag size="small" type="info" effect="light">{{ project.language || 'Java' }}</el-tag>
                <el-tag v-if="project.repoType === 'GITHUB'" size="small" type="success" effect="light">GitHub</el-tag>
                <el-tag v-else size="small" type="info" effect="light">本地</el-tag>
              </div>
            </div>
          </div>
          <div class="project-meta">
            <div class="meta-item" v-if="project.repoType === 'GITHUB' && project.repoUrl">
              <span class="meta-label">仓库地址</span>
              <span class="meta-value" :title="project.repoUrl">{{ project.repoUrl }}</span>
            </div>
            <div class="meta-item" v-else-if="project.repoPath">
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
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="项目名称" required>
          <el-input v-model="form.name" placeholder="输入项目名称，如 my-app" />
        </el-form-item>
        <el-form-item label="仓库类型">
          <el-radio-group v-model="form.repoType">
            <el-radio value="LOCAL">本地仓库</el-radio>
            <el-radio value="GITHUB">GitHub</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.repoType === 'LOCAL'">
          <el-form-item label="Git 仓库路径" required>
            <el-input
              v-model="form.repoPath"
              class="path-input"
              placeholder="输入路径或点击右侧按钮选择目录"
            >
              <template #append>
                <div class="folder-trigger" @click="openDirectoryChooser">
                  <el-icon><Folder /></el-icon>
                </div>
              </template>
            </el-input>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="GitHub 仓库地址" required>
            <el-input
              v-model="form.repoUrl"
              placeholder="https://github.com/user/repo.git"
            />
          </el-form-item>
        </template>
        <el-form-item label="编程语言">
          <el-select v-model="form.language" style="width: 100%;">
              <el-option label="Java" value="Java" />
              <el-option label="Python" value="Python" />
              <el-option label="Go" value="Go" />
              <el-option label="JavaScript" value="JavaScript" />
              <el-option label="TypeScript" value="TypeScript" />
              <el-option label="Kotlin" value="Kotlin" />
              <el-option label="Rust" value="Rust" />
              <el-option label="C / C++" value="C" />
            </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建项目</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="dirChooserVisible"
      title="选择 Git 仓库目录"
      width="600px"
      :close-on-click-modal="false"
    >
      <div v-loading="dirLoading" class="dir-chooser">
        <div class="dir-breadcrumb">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="(crumb, index) in breadcrumbs"
              :key="crumb.path"
            >
              <a href="javascript:void(0)" @click="navigateToPath(crumb.path)">{{ crumb.name }}</a>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="dir-list">
          <div
            v-if="currentPath"
            class="dir-item parent-dir"
            @click="navigateToPath(currentPath.split(/[\\/]/).slice(0, -1).join('/'))"
          >
            <el-icon :size="20" color="var(--el-color-primary)"><Folder /></el-icon>
            <span class="dir-name">..</span>
          </div>
          <div
            v-for="entry in dirEntries"
            :key="entry.path"
            class="dir-item"
            @dblclick="navigateToPath(entry.path)"
          >
            <el-icon :size="20" color="var(--el-color-warning)"><Folder /></el-icon>
            <span class="dir-name">{{ entry.name }}</span>
            <el-button
              size="small"
              type="primary"
              link
              class="select-btn"
              @click.stop="selectDirectory(entry)"
            >
              选择此目录
            </el-button>
          </div>
          <el-empty v-if="!dirLoading && dirEntries.length === 0" description="此目录下没有子目录" />
        </div>
      </div>
      <template #footer>
        <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
          <span class="current-path-hint" v-if="currentPath">{{ currentPath }}</span>
          <el-button @click="dirChooserVisible = false">取消</el-button>
        </div>
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

.project-tags {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}

.folder-trigger {
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
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

.dir-chooser {
  min-height: 300px;
}

.dir-breadcrumb {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 6px;
}

.dir-breadcrumb :deep(.el-breadcrumb__item) {
  font-size: 13px;
}

.dir-list {
  max-height: 360px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 6px;
}

.dir-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  transition: background 0.15s;
}

.dir-item:last-child {
  border-bottom: none;
}

.dir-item:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.dir-item.parent-dir {
  color: var(--el-color-primary);
}

.dir-name {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.select-btn {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}

.dir-item:hover .select-btn {
  opacity: 1;
}

.current-path-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 400px;
}
</style>
