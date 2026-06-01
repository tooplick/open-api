import { post } from './http'
import type { LoginRequest, LoginResponse, RegisterRequest, User } from '@/types'

export function login(body: LoginRequest) {
  return post<LoginResponse>('/api/auth/login', body)
}

export function register(body: RegisterRequest) {
  return post<User>('/api/auth/register', body)
}
