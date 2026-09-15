import apiClient from './axios'
import type { UserResponse, UpdateUserRequest, AddressRequest, AddressResponse } from '@/types'

export const usersApi = {
  getMe: async (): Promise<UserResponse> => {
    const res = await apiClient.get<UserResponse>('/users/me')
    return res.data
  },

  updateMe: async (data: UpdateUserRequest): Promise<UserResponse> => {
    const res = await apiClient.put<UserResponse>('/users/me', data)
    return res.data
  },

  getAddresses: async (): Promise<AddressResponse[]> => {
    const res = await apiClient.get<AddressResponse[]>('/users/me/addresses')
    return res.data
  },

  addAddress: async (data: AddressRequest): Promise<AddressResponse> => {
    const res = await apiClient.post<AddressResponse>('/users/me/addresses', data)
    return res.data
  },

  updateAddress: async (id: string, data: AddressRequest): Promise<AddressResponse> => {
    const res = await apiClient.put<AddressResponse>(`/users/me/addresses/${id}`, data)
    return res.data
  },

  deleteAddress: async (id: string): Promise<void> => {
    await apiClient.delete(`/users/me/addresses/${id}`)
  },
}
