import { create } from 'zustand'
import type { CartItemResponse } from '@/types'
import { cartApi } from '@/api/cart'

interface CartState {
  items: CartItemResponse[]
  total: number
  isLoading: boolean

  itemCount: () => number
  fetchCart: () => Promise<void>
  addItem: (productId: string, qty: number) => Promise<void>
  updateItem: (productId: string, qty: number) => Promise<void>
  removeItem: (productId: string) => Promise<void>
  clearCartLocal: () => void
}

export const useCartStore = create<CartState>((set, get) => ({
  items: [],
  total: 0,
  isLoading: false,

  itemCount: () => get().items.reduce((sum, item) => sum + item.qty, 0),

  fetchCart: async () => {
    set({ isLoading: true })
    try {
      const cart = await cartApi.getCart()
      set({ items: cart.items, total: cart.total })
    } catch {
      // Not authenticated yet or cart empty — fine
    } finally {
      set({ isLoading: false })
    }
  },

  addItem: async (productId, qty) => {
    const cart = await cartApi.addItem({ productId, qty })
    set({ items: cart.items, total: cart.total })
  },

  updateItem: async (productId, qty) => {
    const cart = await cartApi.updateItem(productId, { qty })
    set({ items: cart.items, total: cart.total })
  },

  removeItem: async (productId) => {
    const cart = await cartApi.removeItem(productId)
    set({ items: cart.items, total: cart.total })
  },

  clearCartLocal: () => set({ items: [], total: 0 }),
}))
