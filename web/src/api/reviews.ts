import api from './index'
import type { PageResult } from './types'

export interface Review {
  id?: number
  project?: { id: number; name: string }
  title: string
  fromRef?: string
  toRef?: string
  status: 'pending' | 'processing' | 'completed' | 'failed'
  totalIssues?: number
  highCount?: number
  mediumCount?: number
  lowCount?: number
  totalFiles?: number
  reviewedFiles?: number
  durationMs?: number
  errorMessage?: string
  createdAt?: string
  updatedAt?: string
}

export interface Issue {
  id?: number
  filePath: string
  lineNumber?: number
  severity: 'HIGH' | 'MEDIUM' | 'LOW'
  category: 'SECURITY' | 'PERFORMANCE' | 'STYLE' | 'BUG'
  message: string
  suggestion?: string
  status: 'open' | 'resolved' | 'ignored'
  createdAt?: string
}

export function listReviews(projectId: number, params?: { page?: number; size?: number }) {
  return api.get<any, { data: PageResult<Review> }>(`/projects/${projectId}/reviews`, { params })
}

export function getReview(id: number) {
  return api.get<any, { data: Review }>(`/reviews/${id}`)
}

export function createReview(projectId: number, data: { title: string; fromRef?: string; toRef?: string }) {
  return api.post<any, { code: number; data: Review }>(`/projects/${projectId}/reviews`, data)
}

export function cancelReview(id: number) {
  return api.post(`/reviews/${id}/cancel`)
}

export function deleteReview(id: number) {
  return api.delete(`/reviews/${id}`)
}

export function listIssues(reviewId: number, params?: { page?: number; size?: number }) {
  return api.get<any, { data: PageResult<Issue> }>(`/reviews/${reviewId}/issues`, { params })
}

export function updateIssueStatus(id: number, status: string) {
  return api.put<any, { data: Issue }>(`/issues/${id}/status`, { status })
}
