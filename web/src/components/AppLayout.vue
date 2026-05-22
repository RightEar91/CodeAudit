<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  FolderOpened,
  List,
  Setting,
  DataAnalysis
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const isCollapsed = ref(false)

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/projects')) return '/projects'
  if (path.startsWith('/reviews')) return '/reviews'
  if (path.startsWith('/rules')) return '/rules'
  if (path.startsWith('/settings')) return '/settings'
  return '/projects'
})

function navigate(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="app-layout">
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-brand" @click="navigate('/projects')">
        <div class="brand-icon">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect width="28" height="28" rx="6" fill="#3b82f6" fill-opacity="0.15"/>
            <path d="M8 10l4 4-4 4M16 18l4-8" stroke="#3b82f6" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <span class="brand-text" v-show="!isCollapsed">CodeAudit</span>
      </div>

      <nav class="sidebar-nav">
        <div
          class="nav-item"
          :class="{ active: activeMenu === '/projects' }"
          @click="navigate('/projects')"
        >
          <el-icon :size="18"><FolderOpened /></el-icon>
          <span v-show="!isCollapsed">项目列表</span>
        </div>
        <div
          class="nav-item"
          :class="{ active: activeMenu === '/rules' }"
          @click="navigate('/rules')"
        >
          <el-icon :size="18"><List /></el-icon>
          <span v-show="!isCollapsed">审查规则</span>
        </div>
        <div
          class="nav-item"
          :class="{ active: activeMenu === '/settings' }"
          @click="navigate('/settings')"
        >
          <el-icon :size="18"><Setting /></el-icon>
          <span v-show="!isCollapsed">系统设置</span>
        </div>
      </nav>

      <div class="sidebar-footer" @click="isCollapsed = !isCollapsed" v-show="!isCollapsed">
        <el-icon :size="16"><DataAnalysis /></el-icon>
        <span>收起菜单</span>
      </div>
    </aside>

    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.sidebar {
  width: 220px;
  min-width: 220px;
  background: var(--color-sidebar);
  display: flex;
  flex-direction: column;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
  z-index: 10;
}

.sidebar.collapsed {
  width: 64px;
  min-width: 64px;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px;
  cursor: pointer;
  transition: opacity var(--transition);
}

.sidebar-brand:hover {
  opacity: 0.8;
}

.brand-icon {
  flex-shrink: 0;
}

.brand-text {
  font-size: 17px;
  font-weight: 700;
  color: #f1f5f9;
  letter-spacing: -0.3px;
}

.sidebar-nav {
  flex: 1;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  color: var(--color-sidebar-text);
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition);
  white-space: nowrap;
}

.nav-item:hover {
  background: var(--color-sidebar-hover);
  color: #e2e8f0;
}

.nav-item.active {
  background: rgba(59, 130, 246, 0.12);
  color: var(--color-sidebar-active);
}

.nav-item.active:hover {
  background: rgba(59, 130, 246, 0.18);
}

.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  color: var(--color-sidebar-text);
  font-size: 12.5px;
  cursor: pointer;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  transition: color var(--transition);
}

.sidebar-footer:hover {
  color: #e2e8f0;
}

.main-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--color-bg);
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
