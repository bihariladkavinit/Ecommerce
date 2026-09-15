import apiClient from './axios'
import type { CartResponse, AddItemRequest, UpdateItemRequest, CheckoutRequest, OrderResponse } from '@/types'

export const cartApi = {
  getCart: async (): Promise<CartResponse> => {
    const res = await apiClient.get<CartResponse>('/cart')
    return res.data
  },

  addItem: async (data: AddItemRequest): Promise<CartResponse> => {
    const res = await apiClient.post<CartResponse>('/cart/items', data)
    return res.data
  },

  updateItem: async (productId: string, data: UpdateItemRequest): Promise<CartResponse> => {
    const res = await apiClient.put<CartResponse>(`/cart/items/${productId}`, data)
    return res.data
  },

  removeItem: async (productId: string): Promise<CartResponse> => {
    const res = await apiClient.delete<CartResponse>(`/cart/items/${productId}`)
    return res.data
  },

  clearCart: async (): Promise<void> => {
    await apiClient.delete('/cart')
  },

  checkout: async (data: CheckoutRequest): Promise<OrderResponse> => {
    const idempotencyKey = crypto.randomUUID()
    const res = await apiClient.post<OrderResponse>('/cart/checkout', data, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
    return res.data
  },
}
