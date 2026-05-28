<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Switch } from '@element-plus/icons-vue'
import {
  listRules,
  createRule,
  updateRule,
  toggleRule,
  deleteRule,
  listRuleTemplates,
  importRuleTemplate,
  type Rule,
  type RuleTemplate
} from '@/api/rules'
import type { PageResult } from '@/api/types'

const rules = ref<Rule[]>([])
const loading = ref(false)
const categoryFilter = ref('')
const templateDialogVisible = ref(false)
const templates = ref<RuleTemplate[]>([])
const templateLoading = ref(false)
const importingKey = ref<string | null>(null)
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)

const form = ref<Rule>({
  name: '',
  category: 'SECURITY',
  language: 'Java',
  description: '',
  prompt: '',
  isEnabled: true
})

const categoryOptions = [
  { label: '安全漏洞', value: 'SECURITY' },
  { label: '性能问题', value: 'PERFORMANCE' },
  { label: '代码规范', value: 'STYLE' },
  { label: '潜在缺陷', value: 'BUG' }
]

const languageOptions = ['Java', 'Python', 'Go', 'JavaScript', 'TypeScript', 'Kotlin', 'Rust', 'C']

const categoryLabel = (category: string) => {
  const opt = categoryOptions.find(o => o.value === category)
  return opt?.label || category
}

const categoryColor = (category: string) => {
  switch (category) {
    case 'SECURITY': return 'danger'
    case 'PERFORMANCE': return 'warning'
    case 'STYLE': return 'info'
    case 'BUG': return 'primary'
    default: return 'info'
  }
}

async function fetchRules() {
  loading.value = true
  try {
    const res = await listRules({
      category: categoryFilter.value || undefined,
      page: 0,
      size: 100
    })
    rules.value = res.data.content
  } catch (e) {
    console.error('Failed to fetch rules:', e)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEditing.value = false
  editingId.value = null
  form.value = {
    name: '',
    category: 'SECURITY',
    language: 'Java',
    description: '',
    prompt: '',
    isEnabled: true
  }
  dialogVisible.value = true
}

function openEdit(rule: Rule) {
  isEditing.value = true
  editingId.value = rule.id!
  form.value = {
    name: rule.name,
    category: rule.category,
    language: rule.language,
    description: rule.description || '',
    prompt: rule.prompt,
    isEnabled: rule.isEnabled
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  submitting.value = true
  try {
    if (isEditing.value && editingId.value) {
      await updateRule(editingId.value, form.value)
      ElMessage.success('规则已更新')
    } else {
      await createRule(form.value)
      ElMessage.success('规则已创建')
    }
    dialogVisible.value = false
    await fetchRules()
  } catch (e) {
    console.error('Failed to submit rule:', e)
  } finally {
    submitting.value = false
  }
}

async function handleToggle(rule: Rule) {
  const next = !rule.isEnabled
  try {
    await toggleRule(rule.id!, next)
    rule.isEnabled = next
    ElMessage.success(next ? '规则已启用' : '规则已禁用')
  } catch (e) {
    console.error('Failed to toggle rule:', e)
  }
}

async function handleDelete(rule: Rule) {
  if (rule.isBuiltin) {
    ElMessage.warning('内置规则不可删除')
    return
  }
  try {
    await ElMessageBox.confirm('确定要删除该规则吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteRule(rule.id!)
    ElMessage.success('规则已删除')
    await fetchRules()
  } catch (e) {
    if (e !== 'cancel') console.error('Failed to delete rule:', e)
  }
}

async function openTemplateDialog() {
  templateDialogVisible.value = true
  if (templates.value.length === 0) {
    templateLoading.value = true
    try {
      const res = await listRuleTemplates()
      templates.value = res.data
    } catch (e) {
      console.error('Failed to fetch templates:', e)
    } finally {
      templateLoading.value = false
    }
  }
}

async function handleImportTemplate(templateKey: string) {
  importingKey.value = templateKey
  try {
    await importRuleTemplate(templateKey)
    const template = templates.value.find(t => t.key === templateKey)
    ElMessage.success(`模板「${template?.name || templateKey}」导入成功`)
    importingKey.value = null
    templateDialogVisible.value = false
    await fetchRules()
  } catch (e) {
    console.error('Failed to import template:', e)
    importingKey.value = null
  }
}

onMounted(fetchRules)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div style="display: flex; align-items: center; justify-content: space-between;">
        <div>
          <h1 class="page-title">审查规则</h1>
          <p class="page-subtitle">管理 AI 审查规则，自定义团队编码规范</p>
        </div>
        <div style="display: flex; gap: 8px;">
          <el-button type="primary" :icon="Plus" @click="openCreate" round>
            新建规则
          </el-button>
          <el-button :icon="Plus" @click="openTemplateDialog" round>
            导入模板
          </el-button>
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="categoryFilter" placeholder="规则分类" clearable size="small" style="width: 150px;" @change="fetchRules">
        <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
      </el-select>
    </div>

    <div v-loading="loading" style="min-height: 200px;">
      <div v-if="rules.length > 0" class="rules-list">
        <div v-for="rule in rules" :key="rule.id" class="rule-item">
          <div class="rule-main">
            <div class="rule-header">
              <h3 class="rule-name">{{ rule.name }}</h3>
              <div class="rule-tags">
                <el-tag :type="categoryColor(rule.category)" size="small" effect="light">
                  {{ categoryLabel(rule.category) }}
                </el-tag>
                <el-tag size="small" type="info" effect="plain">{{ rule.language }}</el-tag>
                <el-tag v-if="rule.isBuiltin" size="small" type="info" effect="plain">内置</el-tag>
              </div>
            </div>
            <p v-if="rule.description" class="rule-desc">{{ rule.description }}</p>
            <div class="rule-prompt">
              <code>{{ rule.prompt }}</code>
            </div>
          </div>
          <div class="rule-actions">
            <el-switch
              :model-value="rule.isEnabled"
              size="small"
              @change="handleToggle(rule)"
            />
            <el-button size="small" text @click="openEdit(rule)">
              <el-icon :size="15"><Edit /></el-icon>
            </el-button>
            <el-button
              size="small"
              text
              type="danger"
              :disabled="rule.isBuiltin"
              @click="handleDelete(rule)"
            >
              <el-icon :size="15"><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-icon><Switch /></el-icon>
        <p>暂无规则</p>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑规则' : '新建规则'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.name" placeholder="如 SQL 注入检查" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类" required>
              <el-select v-model="form.category" style="width: 100%;">
                <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标语言" required>
              <el-select v-model="form.language" style="width: 100%;">
                <el-option v-for="l in languageOptions" :key="l" :label="l" :value="l" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="规则说明">
          <el-input v-model="form.description" placeholder="简要说明规则用途" />
        </el-form-item>
        <el-form-item label="Prompt 指令" required>
          <el-input
            v-model="form.prompt"
            type="textarea"
            :rows="4"
            placeholder="描述 AI 应遵循的审查标准，如：检查所有 SQL 语句是否使用了参数化查询"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEditing ? '保存修改' : '创建规则' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="templateDialogVisible"
      title="导入规则模板"
      width="600px"
      :close-on-click-modal="false"
    >
      <div v-loading="templateLoading" style="min-height: 120px;">
        <p style="margin: 0 0 16px; color: var(--color-text-secondary); font-size: 13px;">
          选择一个规则模板包，一键导入到当前规则库中。导入的规则可自由启用/禁用和删除。
        </p>
        <div v-if="templates.length > 0" class="template-list">
          <div
            v-for="tmpl in templates"
            :key="tmpl.key"
            class="template-item"
          >
            <div class="template-info">
              <h4>{{ tmpl.name }}</h4>
              <p>{{ tmpl.description }}</p>
              <div class="template-meta">
                <el-tag size="small" type="info" effect="plain">{{ tmpl.language }}</el-tag>
                <span class="template-count">{{ tmpl.ruleCount }} 条规则</span>
              </div>
            </div>
            <el-button
              type="primary"
              size="small"
              :loading="importingKey === tmpl.key"
              @click="handleImportTemplate(tmpl.key)"
            >
              导入
            </el-button>
          </div>
        </div>
        <div v-else-if="!templateLoading" class="empty-state" style="padding: 20px 0;">
          <p>暂无可用模板</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 16px;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.template-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
}

.template-info h4 {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.template-info p {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.template-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.template-count {
  font-size: 12px;
  color: var(--color-text-muted);
}

.rules-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rule-item {
  display: flex;
  align-items: flex-start;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: 18px 20px;
  transition: box-shadow var(--transition);
}

.rule-item:hover {
  box-shadow: var(--shadow-sm);
}

.rule-main {
  flex: 1;
  min-width: 0;
}

.rule-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.rule-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.rule-tags {
  display: flex;
  gap: 6px;
}

.rule-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 8px 0;
}

.rule-prompt {
  margin-top: 8px;
}

.rule-prompt code {
  font-size: 12px;
  color: var(--color-text-muted);
  background: var(--color-border-light);
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  display: block;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.rule-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 16px;
  padding-top: 2px;
}
</style>
