import apiClient from './axios'
import type { StockResponse, StockUpdateRequest, AvailabilityResponse } from '@/types'

export const inventoryApi = {
  getStock: async (productId: string): Promise<StockResponse> => {
    const res = await apiClient.get<StockResponse>(`/inventory/${productId}`)
    return res.data
  },

  checkAvailability: async (productId: string, qty: number): Promise<AvailabilityResponse> => {
    const res = await apiClient.get<AvailabilityResponse>(`/inventory/${productId}/availability`, { params: { qty } })
    return res.data
  },

  restock: async (productId: string, data: StockUpdateRequest): Promise<StockResponse> => {
    const res = await apiClient.put<StockResponse>(`/inventory/${productId}`, data)
    return res.data
  },
}
