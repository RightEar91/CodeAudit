<script setup lang="ts">
import { useRouter } from 'vue-router'
import { HomeFilled } from '@element-plus/icons-vue'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="not-found-content">
      <h1>404</h1>
      <p>页面不存在</p>
      <el-button type="primary" :icon="HomeFilled" @click="router.push('/')">
        返回首页
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.not-found-content {
  text-align: center;
}

.not-found-content h1 {
  font-size: 96px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: -2px;
  margin: 0;
}

.not-found-content p {
  font-size: 16px;
  color: var(--color-text-muted);
  margin: 8px 0 24px;
}
</style>
