import api from './index'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  displayName?: string
  email?: string
}

export interface UserInfo {
  token: string
  userId: number
  username: string
  displayName: string
  role: string
}

export function login(data: LoginRequest) {
  return api.post<unknown, UserInfo>('/auth/login', data)
}

export function register(data: RegisterRequest) {
  return api.post<unknown, UserInfo>('/auth/register', data)
}

export function getCurrentUser() {
  return api.get<unknown, UserInfo>('/auth/me')
}
