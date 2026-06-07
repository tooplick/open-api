import { get, post, put } from './http'
import type { EmailRegisterRequest, GithubAuthorizeResponse, GithubTicketExchangeRequest, LoginRequest, LoginResponse, RegisterRequest, SendEmailCodeRequest, User } from '@/types'

export function login(body: LoginRequest) {
  return post<LoginResponse>('/api/auth/login', body)
}

export function getGithubAuthorizeUrl(redirect?: string) {
  return get<GithubAuthorizeResponse>('/api/auth/github/authorize-url', { redirect })
}

export function exchangeGithubTicket(body: GithubTicketExchangeRequest) {
  return post<LoginResponse>('/api/auth/github/exchange', body)
}

export function register(body: RegisterRequest) {
  return post<User>('/api/auth/register', body)
}

export function sendEmailCode(body: SendEmailCodeRequest) {
  return post<void>('/api/auth/email-code', body)
}

export function emailRegister(body: EmailRegisterRequest) {
  return post<User>('/api/auth/email-register', body)
}

/** 首次登录强制修改账号与密码,返回新的 token + user */
export function changeInitialCredentials(body: { username: string, newPassword: string }) {
  return put<LoginResponse>('/api/user/initial-credentials', body)
}
