<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
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
      parallelism: parallelism.value
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
