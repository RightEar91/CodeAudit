import api from './index'
import type { PageResult } from './types'

export interface Project {
  id?: number
  name: string
  repoPath: string
  currentBranch?: string
  language?: string
  createdAt?: string
  updatedAt?: string
}

export function listProjects(params?: { name?: string; page?: number; size?: number }) {
  return api.get<any, { data: PageResult<Project> }>('/projects', { params })
}

export function getProject(id: number) {
  return api.get<any, { data: Project }>(`/projects/${id}`)
}

export function createProject(data: Project) {
  return api.post<any, { code: number; message: string; data: Project }>('/projects', data)
}

export function updateProject(id: number, data: Partial<Project>) {
  return api.put<any, { data: Project }>(`/projects/${id}`, data)
}

export function deleteProject(id: number) {
  return api.delete(`/projects/${id}`)
}
