import api from './index'
import type { PageResult } from './types'

export interface Rule {
  id?: number
  name: string
  category: string
  language: string
  description?: string
  prompt: string
  isBuiltin?: boolean
  isEnabled?: boolean
  createdAt?: string
  updatedAt?: string
}

export function listRules(params?: { category?: string; page?: number; size?: number }) {
  return api.get<any, { data: PageResult<Rule> }>('/rules', { params })
}

export function getEnabledRules() {
  return api.get<any, { data: Rule[] }>('/rules/enabled')
}

export function getRule(id: number) {
  return api.get<any, { data: Rule }>(`/rules/${id}`)
}

export function createRule(data: Rule) {
  return api.post<any, { code: number; data: Rule }>('/rules', data)
}

export function updateRule(id: number, data: Partial<Rule>) {
  return api.put<any, { data: Rule }>(`/rules/${id}`, data)
}

export function toggleRule(id: number, isEnabled: boolean) {
  return api.put<any, { data: Rule }>(`/rules/${id}/toggle`, { isEnabled })
}

export function deleteRule(id: number) {
  return api.delete(`/rules/${id}`)
}

export interface RuleTemplate {
  key: string
  name: string
  description: string
  ruleCount: number
  language: string
}

export function listRuleTemplates() {
  return api.get<any, { data: RuleTemplate[] }>('/rules/templates')
}

export function importRuleTemplate(templateKey: string) {
  return api.post<any, { code: number; data: Rule[] }>('/rules/import-template', { templateKey })
}
