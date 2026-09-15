import apiClient from './axios'
import type { OrderResponse, OrderStatusResponse, PageOrderResponse } from '@/types'

export interface GetOrdersParams {
  page?: number
  size?: number
}

export const ordersApi = {
  getOrders: async (params: GetOrdersParams = {}): Promise<PageOrderResponse> => {
    const res = await apiClient.get<PageOrderResponse>('/orders', { params: { size: 10, page: 0, ...params } })
    return res.data
  },

  getOrder: async (id: string): Promise<OrderResponse> => {
    const res = await apiClient.get<OrderResponse>(`/orders/${id}`)
    return res.data
  },

  getOrderStatus: async (id: string): Promise<OrderStatusResponse> => {
    const res = await apiClient.get<OrderStatusResponse>(`/orders/${id}/status`)
    return res.data
  },

  cancelOrder: async (id: string): Promise<OrderResponse> => {
    const res = await apiClient.post<OrderResponse>(`/orders/${id}/cancel`)
    return res.data
  },
}
