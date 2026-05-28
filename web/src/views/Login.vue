<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, register, type UserInfo } from '@/api/auth'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const { login: doLogin } = useAuth()

const isLogin = ref(true)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  displayName: '',
  email: ''
})

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const response = await login({ username: form.username, password: form.password })
    const user = response.data
    onSuccess(user)
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  if (form.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  loading.value = true
  try {
    const response = await register({
      username: form.username,
      password: form.password,
      displayName: form.displayName || undefined,
      email: form.email || undefined
    })
    const user = response.data
    onSuccess(user)
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function onSuccess(user: UserInfo) {
  doLogin(user.token)
  ElMessage.success(`欢迎，${user.displayName || user.username}`)
  router.push('/projects')
}

function toggleMode() {
  isLogin.value = !isLogin.value
  form.password = ''
  form.confirmPassword = ''
}

function handleKeyup(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    isLogin.value ? handleLogin() : handleRegister()
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <div class="login-logo">
          <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
            <rect width="40" height="40" rx="10" fill="#3b82f6" fill-opacity="0.15"/>
            <path d="M12 16l6 6-6 6M24 26l6-10" stroke="#3b82f6" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1>CodeAudit</h1>
        <p>AI 代码审查平台</p>
      </div>

      <div class="login-body" @keyup="handleKeyup">
        <div class="form-item">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
            autocomplete="username"
          />
        </div>
        <div class="form-item">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
          />
        </div>

        <template v-if="!isLogin">
          <div class="form-item">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="确认密码"
              size="large"
              :prefix-icon="Lock"
              show-password
            />
          </div>
          <div class="form-item">
            <el-input
              v-model="form.displayName"
              placeholder="显示名称（可选）"
              size="large"
            />
          </div>
        </template>

        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="login-btn"
          @click="isLogin ? handleLogin() : handleRegister()"
        >
          {{ isLogin ? '登 录' : '注 册' }}
        </el-button>
      </div>

      <div class="login-footer">
        <span>{{ isLogin ? '没有账号？' : '已有账号？' }}</span>
        <el-link type="primary" @click="toggleMode">
          {{ isLogin ? '立即注册' : '去登录' }}
        </el-link>
      </div>

      <div class="login-hint" v-if="isLogin">
        默认账号：admin / admin
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0f172a 100%);
}

.login-card {
  width: 400px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 40px;
  backdrop-filter: blur(12px);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  margin-bottom: 16px;
}

.login-header h1 {
  font-size: 22px;
  font-weight: 700;
  color: #f1f5f9;
  margin: 0;
}

.login-header p {
  font-size: 13px;
  color: #94a3b8;
  margin: 6px 0 0;
}

.login-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-item {
  width: 100%;
}

.form-item :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: none;
}

.form-item :deep(.el-input__wrapper:hover) {
  border-color: rgba(255, 255, 255, 0.2);
}

.form-item :deep(.el-input__inner) {
  color: #e2e8f0;
}

.form-item :deep(.el-input__inner::placeholder) {
  color: #64748b;
}

.login-btn {
  margin-top: 4px;
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
}

.login-footer {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: #94a3b8;
}

.login-hint {
  margin-top: 16px;
  text-align: center;
  font-size: 11px;
  color: #475569;
  background: rgba(255, 255, 255, 0.03);
  padding: 8px;
  border-radius: 6px;
}
</style>
