import api from './index'
import type { PageResult } from './types'
import type { DiffBlock } from './projects'

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
  authorName?: string
  authorEmail?: string
  modelCount?: number
  totalModelCount?: number
  models?: string
  createdAt?: string
}

export interface IssueComment {
  id?: number
  authorName: string
  content: string
  createdAt?: string
}

export interface FixSuggestion {
  id?: number
  issue?: Issue
  fixPatch?: string
  explanation?: string
  status: 'pending' | 'accepted' | 'rejected'
  createdAt?: string
}

export interface FeedbackStats {
  falsePositive: number
  ignored: number
  confirmed: number
  total: number
}

export interface KnowledgeDocument {
  id?: number
  title: string
  content: string
  category: string
  createdAt?: string
}

export interface User {
  id?: number
  username: string
  displayName?: string
  email?: string
  role: string
  enabled?: boolean
}

// ---- API functions ----

export function getComments(issueId: number) {
  return api.get<any, { data: IssueComment[] }>(`/issues/${issueId}/comments`)
}

export function addComment(issueId: number, data: { authorName: string; content: string }) {
  return api.post<any, { code: number; data: IssueComment }>(`/issues/${issueId}/comments`, data)
}

export function assignIssue(issueId: number, data: { assigneeName: string; assigneeEmail: string }) {
  return api.put<any, { data: Issue }>(`/issues/${issueId}/assign`, data)
}

export function getFixSuggestion(issueId: number) {
  return api.get<any, { data: FixSuggestion }>(`/issues/${issueId}/fix`)
}

export function acceptFix(fixId: number, acceptedBy: string) {
  return api.post<any, { data: FixSuggestion }>(`/fixes/${fixId}/accept`, { acceptedBy })
}

export function recordFeedback(issueId: number, data: { action: string; reason: string; reviewedBy: string }) {
  return api.post(`/issues/${issueId}/feedback`, data)
}

export function getFeedbackStats(projectId: number) {
  return api.get<any, { data: FeedbackStats }>(`/projects/${projectId}/feedback/stats`)
}

export function listUsers() {
  return api.get<any, { data: User[] }>(`/users`)
}

export function createUser(data: { username: string; displayName: string; email: string; role: string }) {
  return api.post<any, { data: User }>('/users', data)
}

export function updateUserRole(userId: number, role: string) {
  return api.put<any, { data: User }>(`/users/${userId}/role`, { role })
}

export function getShareLink(reviewId: number) {
  return api.get<any, { data: { link: string } }>(`/reviews/${reviewId}/share`)
}

export function listKnowledgeDocs(projectId: number) {
  return api.get<any, { data: KnowledgeDocument[] }>(`/projects/${projectId}/knowledge`)
}

export function addKnowledgeDoc(projectId: number, data: { title: string; content: string; category: string }) {
  return api.post<any, { data: KnowledgeDocument }>(`/projects/${projectId}/knowledge`, data)
}

export function deleteKnowledgeDoc(id: number) {
  return api.delete(`/knowledge/${id}`)
}

export function listReviews(projectId: number, params?: { page?: number; size?: number }) {
  return api.get<any, { data: PageResult<Review> }>(`/projects/${projectId}/reviews`, { params })
}

export function getReview(id: number) {
  return api.get<any, { data: Review }>(`/reviews/${id}`)
}

export interface ReviewFilter {
  includePaths?: string[]
  excludePaths?: string[]
  includeExtensions?: string[]
  authorEmail?: string
  since?: string
  until?: string
}

export interface CreateReviewRequest {
  title: string
  fromRef?: string
  toRef?: string
  filters?: ReviewFilter
}

export function createReview(projectId: number, data: CreateReviewRequest) {
  return api.post<any, { code: number; data: Review }>(`/projects/${projectId}/reviews`, data)
}

export function cancelReview(id: number) {
  return api.post(`/reviews/${id}/cancel`, null, { timeout: 60000 })
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

export function exportReviewPdf(id: number): Promise<Blob> {
  return api.get(`/reviews/${id}/export/pdf`, { responseType: 'blob' })
}

export function getReviewDiffs(id: number) {
  return api.get<any, { data: DiffBlock[] }>(`/reviews/${id}/diffs`)
}
