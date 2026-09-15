import apiClient from './axios'
import type { ShipmentResponse, ShipmentStatusUpdateRequest } from '@/types'

export const shipmentsApi = {
  getShipmentByOrderId: async (orderId: string): Promise<ShipmentResponse> => {
    const res = await apiClient.get<ShipmentResponse>(`/shipments/${orderId}`)
    return res.data
  },

  trackShipment: async (trackingNumber: string): Promise<ShipmentResponse> => {
    const res = await apiClient.get<ShipmentResponse>(`/shipments/track/${trackingNumber}`)
    return res.data
  },

  updateStatus: async (orderId: string, data: ShipmentStatusUpdateRequest): Promise<ShipmentResponse> => {
    const res = await apiClient.put<ShipmentResponse>(`/shipments/${orderId}/status`, data)
    return res.data
  },
}
