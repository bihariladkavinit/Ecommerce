import apiClient from './axios'
import type { LoginRequest, RegisterRequest, RefreshRequest, TokenResponse } from '@/types'

export const authApi = {
  login: async (data: LoginRequest): Promise<TokenResponse> => {
    const res = await apiClient.post<TokenResponse>('/auth/login', data)
    return res.data
  },

  register: async (data: RegisterRequest): Promise<TokenResponse> => {
    const res = await apiClient.post<TokenResponse>('/auth/register', data)
    return res.data
  },

  refresh: async (data: RefreshRequest): Promise<TokenResponse> => {
    const res = await apiClient.post<TokenResponse>('/auth/refresh', data)
    return res.data
  },

  logout: async (): Promise<void> => {
    await apiClient.post('/auth/logout')
  },
}
