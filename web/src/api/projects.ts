import api from './index'
import type { PageResult } from './types'

export interface Project {
  id?: number
  name: string
  repoType?: string
  repoPath?: string
  repoUrl?: string
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

export interface BranchInfo {
  name: string
  isHead: boolean
}

export interface CommitInfo {
  hash: string
  shortHash: string
  message: string
}

export interface DiffBlock {
  filePath: string
  changeType: string
  addedLines: number
  removedLines: number
  diffContent: string
}

export function listBranches(projectId: number) {
  return api.get<any, { data: BranchInfo[] }>(`/projects/${projectId}/branches`)
}

export function listCommits(projectId: number, count?: number) {
  return api.get<any, { data: CommitInfo[] }>(`/projects/${projectId}/commits`, { params: { count } })
}

export function previewDiff(projectId: number, fromRef: string, toRef: string) {
  return api.get<any, { data: DiffBlock[] }>(`/projects/${projectId}/diff-preview`, { params: { fromRef, toRef } })
}
