import apiClient from './axios'
import type { ProductResponse, ProductRequest, PageProductResponse, CategoryResponse, CategoryRequest } from '@/types'

export interface GetProductsParams {
  category?: string
  search?: string
  page?: number
  size?: number
  sort?: string
}

export const productsApi = {
  getProducts: async (params: GetProductsParams = {}): Promise<PageProductResponse> => {
    const res = await apiClient.get<PageProductResponse>('/products', { params: { size: 12, page: 0, ...params } })
    return res.data
  },

  getProduct: async (id: string): Promise<ProductResponse> => {
    const res = await apiClient.get<ProductResponse>(`/products/${id}`)
    return res.data
  },

  createProduct: async (data: ProductRequest): Promise<ProductResponse> => {
    const res = await apiClient.post<ProductResponse>('/products', data)
    return res.data
  },

  updateProduct: async (id: string, data: ProductRequest): Promise<ProductResponse> => {
    const res = await apiClient.put<ProductResponse>(`/products/${id}`, data)
    return res.data
  },

  deleteProduct: async (id: string): Promise<void> => {
    await apiClient.delete(`/products/${id}`)
  },

  getCategories: async (): Promise<CategoryResponse[]> => {
    const res = await apiClient.get<CategoryResponse[]>('/categories')
    return res.data
  },

  createCategory: async (data: CategoryRequest): Promise<CategoryResponse> => {
    const res = await apiClient.post<CategoryResponse>('/categories', data)
    return res.data
  },
}
