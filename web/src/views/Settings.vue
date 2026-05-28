<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting, Link, Promotion, CircleCheckFilled, Connection, SwitchFilled } from '@element-plus/icons-vue'
import api from '@/api/index'

const provider = ref('ollama')
const ollamaUrl = ref('http://localhost:11434')
const ollamaModel = ref('qwen3:8b')
const openaiApiKey = ref('')
const openaiBaseUrl = ref('https://api.openai.com')
const openaiModel = ref('gpt-4o')
const openaiTemperature = ref(0.1)
const thinkingEnabled = ref(true)
const parallelism = ref(2)
const githubToken = ref('')

const webhookUrl = computed(() => {
  return window.location.origin + '/api/webhook/github'
})

const isLocalhost = computed(() => {
  return webhookUrl.value.startsWith('http://localhost') || webhookUrl.value.startsWith('http://127.0.0.1')
})

function copyWebhookUrl() {
  navigator.clipboard.writeText(webhookUrl.value)
  ElMessage.success('已复制 Webhook URL')
}

const checking = ref(false)
const ollamaStatus = ref<'unknown' | 'connected' | 'disconnected'>('unknown')
const saving = ref(false)
const ollamaModels = ref<string[]>([])
const modelsLoading = ref(false)

async function fetchOllamaModels() {
  modelsLoading.value = true
  try {
    const res = await api.get('/ollama/models', { timeout: 5000 })
    ollamaModels.value = res.data || []
    if (ollamaModels.value.length > 0 && !ollamaModels.value.includes(ollamaModel.value)) {
      ollamaModel.value = ollamaModels.value[0]
    }
  } catch {
    ollamaModels.value = []
  } finally {
    modelsLoading.value = false
  }
}

async function checkOllama() {
  checking.value = true
  ollamaStatus.value = 'unknown'
  try {
    await api.get('/ollama/check', { timeout: 5000 })
    ollamaStatus.value = 'connected'
    ElMessage.success('Ollama 连接正常')
    await fetchOllamaModels()
  } catch (e) {
    ollamaStatus.value = 'disconnected'
    ElMessage.error('无法连接 Ollama，请检查服务和地址')
  } finally {
    checking.value = false
  }
}

async function saveSettings() {
  saving.value = true
  try {
    await api.put('/settings', {
      provider: provider.value,
      ollamaUrl: ollamaUrl.value,
      ollamaModel: ollamaModel.value,
      openaiApiKey: openaiApiKey.value,
      openaiBaseUrl: openaiBaseUrl.value,
      openaiModel: openaiModel.value,
      openaiTemperature: openaiTemperature.value,
      thinkingEnabled: thinkingEnabled.value,
      parallelism: parallelism.value,
      githubToken: githubToken.value
    })
    ElMessage.success('设置已保存（重启后还原为配置文件值）')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function fetchSettings() {
  try {
    const res = await api.get('/settings')
    if (res.data) {
      provider.value = res.data.provider || 'ollama'
      ollamaUrl.value = res.data.ollamaUrl || ollamaUrl.value
      ollamaModel.value = res.data.ollamaModel || ollamaModel.value
      openaiApiKey.value = res.data.openaiApiKey || ''
      openaiBaseUrl.value = res.data.openaiBaseUrl || openaiBaseUrl.value
      openaiModel.value = res.data.openaiModel || openaiModel.value
      openaiTemperature.value = res.data.openaiTemperature ?? 0.1
      thinkingEnabled.value = res.data.thinkingEnabled ?? true
      parallelism.value = res.data.parallelism ?? 2
      githubToken.value = typeof res.data.githubToken === 'string' && res.data.githubToken ? res.data.githubToken : ''
    }
  } catch (e) {
    console.error('Failed to fetch settings:', e)
  }
}

watch(provider, () => {
  if (provider.value === 'ollama') {
    ollamaStatus.value = 'unknown'
  }
})

onMounted(async () => {
  await fetchSettings()
  if (provider.value === 'ollama') {
    fetchOllamaModels()
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">系统设置</h1>
      <p class="page-subtitle">配置 AI 服务和审查参数</p>
    </div>

    <div class="settings-sections">
      <!-- Provider 选择 -->
      <div class="settings-card">
        <div class="settings-card-header">
          <div class="settings-icon" style="background: var(--color-info-light); color: var(--color-info);">
            <el-icon :size="18"><Connection /></el-icon>
          </div>
          <div>
            <h3>AI 模型提供商</h3>
            <p>选择本地 Ollama 或云端 OpenAI 兼容 API</p>
          </div>
        </div>
        <div class="settings-card-body">
          <el-radio-group v-model="provider" size="small">
            <el-radio-button value="ollama">🖥️ 本地 Ollama</el-radio-button>
            <el-radio-button value="openai">☁️ 云端 OpenAI</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- Ollama 配置 -->
      <div class="settings-card" v-if="provider === 'ollama'">
        <div class="settings-card-header">
          <div class="settings-icon" style="background: var(--color-primary-light); color: var(--color-primary);">
            <el-icon :size="18"><Link /></el-icon>
          </div>
          <div>
            <h3>Ollama 服务</h3>
            <p>本地 AI 模型服务配置</p>
          </div>
        </div>
        <div class="settings-card-body">
          <div class="form-item">
            <label>服务地址</label>
            <div class="input-with-status">
              <el-input v-model="ollamaUrl" placeholder="http://localhost:11434" style="flex: 1;" />
              <el-tag
                v-if="ollamaStatus === 'connected'"
                type="success"
                size="small"
                effect="light"
              >
                <el-icon :size="12"><CircleCheckFilled /></el-icon>
                已连接
              </el-tag>
              <el-tag
                v-else-if="ollamaStatus === 'disconnected'"
                type="danger"
                size="small"
                effect="light"
              >
                未连接
              </el-tag>
            </div>
          </div>
          <div class="form-item">
            <label>模型名称</label>
            <el-select
              v-model="ollamaModel"
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入模型名，如 qwen3:8b"
              style="width: 100%"
              :loading="modelsLoading"
              @focus="ollamaModels.length === 0 ? fetchOllamaModels() : null"
            >
              <el-option
                v-for="m in ollamaModels"
                :key="m"
                :label="m"
                :value="m"
              />
            </el-select>
          </div>
          <div class="form-actions">
            <el-button :loading="checking" @click="checkOllama">检测连接</el-button>
          </div>
        </div>
      </div>

      <!-- OpenAI 配置 -->
      <div class="settings-card" v-if="provider === 'openai'">
        <div class="settings-card-header">
          <div class="settings-icon" style="background: var(--color-success-light); color: var(--color-success);">
            <el-icon :size="18"><Promotion /></el-icon>
          </div>
          <div>
            <h3>OpenAI 兼容 API</h3>
            <p>支持 OpenAI / DeepSeek / 阿里百炼 / Moonshot 等</p>
          </div>
        </div>
        <div class="settings-card-body">
          <div class="form-item">
            <label>API Key</label>
            <el-input v-model="openaiApiKey" type="password" show-password placeholder="sk-..." />
          </div>
          <div class="form-item">
            <label>Base URL</label>
            <el-input v-model="openaiBaseUrl" placeholder="https://api.openai.com" />
            <span class="form-hint">不同服务商的端点地址，如 https://api.deepseek.com</span>
          </div>
          <div class="form-row">
            <div class="form-item" style="flex: 1;">
              <label>模型名称</label>
              <el-input v-model="openaiModel" placeholder="gpt-4o" />
            </div>
            <div class="form-item" style="width: 140px;">
              <label>Temperature</label>
              <el-input-number
                v-model="openaiTemperature"
                :min="0"
                :max="2"
                :step="0.1"
                :precision="1"
                style="width: 100%;"
              />
            </div>
          </div>
          <div class="form-item" style="margin-bottom: 0;">
            <label>推理模式（Thinking）</label>
            <div style="display: flex; align-items: center; gap: 10px; margin-top: 4px;">
              <el-switch v-model="thinkingEnabled" size="small" />
              <span class="form-hint" style="margin: 0;">{{ thinkingEnabled ? '模型输出推理过程（Token 消耗更多）' : '关闭推理，仅输出结果（节省 Token）' }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 审查性能 -->
      <div class="settings-card">
        <div class="settings-card-header">
          <div class="settings-icon" style="background: var(--color-primary-light); color: var(--color-primary);">
            <el-icon :size="18"><Setting /></el-icon>
          </div>
          <div>
            <h3>审查性能</h3>
            <p>控制文件级并行审查的并发度</p>
          </div>
        </div>
        <div class="settings-card-body">
          <div class="form-item">
            <label>并行度：{{ parallelism }} 个文件同时审查</label>
            <el-slider
              v-model="parallelism"
              :min="1"
              :max="8"
              :step="1"
              :marks="{ 1: '1', 2: '2', 4: '4', 6: '6', 8: '8' }"
              show-stops
              style="padding: 0 12px; margin-top: 8px;"
            />
          </div>
          <div class="config-hint">
            <el-icon :size="14"><SwitchFilled /></el-icon>
            <span>推荐 2~4，云端 API 建议不超过 4 以免触发限流</span>
          </div>
        </div>
    </div>

    <!-- GitHub 配置 -->
    <div class="settings-card">
      <div class="settings-card-header">
        <div class="settings-icon" style="background: var(--color-success-light); color: var(--color-success);">
          <svg style="width: 18px; height: 18px;" viewBox="0 0 16 16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/></svg>
        </div>
        <div>
          <h3>GitHub 集成</h3>
          <p>配置 Token 以连接 GitHub 仓库和 Webhook</p>
        </div>
      </div>
      <div class="settings-card-body">
        <div class="form-item">
          <label>Personal Access Token</label>
          <el-input v-model="githubToken" type="password" show-password placeholder="ghp_..." />
          <span class="form-hint">需要 repo 权限，用于 clone 仓库和发布 PR 评论</span>
        </div>
        <div class="form-item">
          <label>Webhook URL（复制到 GitHub 仓库 Settings → Webhooks）</label>
          <el-input :model-value="webhookUrl" readonly>
            <template #append>
              <el-button @click="copyWebhookUrl">复制</el-button>
            </template>
          </el-input>
          <span v-if="isLocalhost" class="form-hint form-warn">
            ⚠️ GitHub Webhook 要求 HTTPS 公网地址，localhost 无法使用
          </span>
          <span class="form-hint">Payload URL 填入此地址，Content type 选 application/json</span>
        </div>
        <div class="form-item" v-if="isLocalhost">
          <label>本地开发如何使用 Webhook？</label>
          <div class="ngrok-guide">
            <p>使用 <strong>ngrok</strong> 将本地服务暴露到公网 HTTPS：</p>
            <ol>
              <li>下载 <el-link href="https://ngrok.com/download" target="_blank" type="primary">ngrok</el-link> 并安装</li>
              <li>终端运行：<code>ngrok http 9090</code></li>
              <li>ngrok 会生成一个 HTTPS 地址，如 <code>https://xxxx.ngrok-free.app</code></li>
              <li>将 <code>https://xxxx.ngrok-free.app/api/webhook/github</code> 填入 GitHub Webhook</li>
            </ol>
          </div>
        </div>
      </div>
    </div>

    <div class="save-bar">
        <el-button type="primary" :loading="saving" @click="saveSettings" round>
          保存设置
        </el-button>
        <span class="save-hint">⚠️ 运行时修改仅在当前会话生效，重启后还原</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-sections {
  max-width: 680px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.settings-card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 24px 0;
}

.settings-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.settings-card-header h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.settings-card-header p {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 2px 0 0;
}

.settings-card-body {
  padding: 20px 24px 24px;
}

.form-item {
  margin-bottom: 18px;
}

.form-item label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.form-hint {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-text-muted);
}

.form-warn {
  color: #e6a23c;
  font-weight: 500;
}

.ngrok-guide {
  background: var(--color-border-light);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.ngrok-guide p {
  margin: 0 0 8px;
}

.ngrok-guide ol {
  margin: 0;
  padding-left: 18px;
}

.ngrok-guide code {
  background: rgba(0, 0, 0, 0.06);
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
}

.input-with-status {
  display: flex;
  align-items: center;
  gap: 10px;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-actions {
  padding-top: 4px;
}

.config-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: var(--color-border-light);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--color-text-muted);
}

.save-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-top: 8px;
}

.save-hint {
  font-size: 11px;
  color: var(--color-text-muted);
}
</style>
