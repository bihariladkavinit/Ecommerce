// ── Auth ──────────────────────────────────────────────────────────────
export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

// ── Users ─────────────────────────────────────────────────────────────
export interface UserResponse {
  id: string
  email: string
  firstName: string
  lastName: string
  phone?: string
  active: boolean
  roles: string[]
  createdAt: string
}

export interface UpdateUserRequest {
  firstName?: string
  lastName?: string
  phone?: string
}

export interface AddressRequest {
  line1: string
  line2?: string
  city: string
  state?: string
  zip: string
  country: string
  default?: boolean
}

export interface AddressResponse {
  id: string
  line1: string
  line2?: string
  city: string
  state?: string
  zip: string
  country: string
  default: boolean
}

// ── Products ──────────────────────────────────────────────────────────
export interface ProductRequest {
  name: string
  description?: string
  price: number
  categoryId?: string
  imageUrl?: string
}

export interface ProductResponse {
  id: string
  name: string
  description?: string
  price: number
  categoryId?: string
  imageUrl?: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CategoryRequest {
  name: string
  parentId?: string
}

export interface CategoryResponse {
  id: string
  name: string
  parentId?: string
}

export interface PageProductResponse {
  content: ProductResponse[]
  totalPages: number
  totalElements: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

// ── Inventory ─────────────────────────────────────────────────────────
export interface StockResponse {
  productId: string
  availableQty: number
  reservedQty: number
}

export interface StockUpdateRequest {
  quantity: number
}

export interface AvailabilityResponse {
  productId: string
  available: boolean
}

// ── Cart ──────────────────────────────────────────────────────────────
export interface CartItemResponse {
  productId: string
  name: string
  price: number
  qty: number
  subtotal: number
}

export interface CartResponse {
  userId: string
  items: CartItemResponse[]
  total: number
}

export interface AddItemRequest {
  productId: string
  qty: number
}

export interface UpdateItemRequest {
  qty: number
}

export interface CheckoutRequest {
  shippingAddressId: string
}

// ── Orders ────────────────────────────────────────────────────────────
export interface OrderItemResponse {
  productId: string
  productName: string
  qty: number
  unitPrice: number
}

export interface OrderResponse {
  id: string
  userId: string
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED'
  sagaState: string
  totalAmount: number
  items: OrderItemResponse[]
  createdAt: string
}

export interface OrderStatusResponse {
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED'
  sagaState: string
}

export interface PageOrderResponse {
  content: OrderResponse[]
  totalPages: number
  totalElements: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

// ── Shipments ─────────────────────────────────────────────────────────
export type ShipmentStatus = 'CREATED' | 'DISPATCHED' | 'DELIVERED' | 'CANCELLED'

export interface ShipmentResponse {
  id: string
  orderId: string
  status: ShipmentStatus
  carrier?: string
  trackingNumber?: string
  createdAt: string
  updatedAt: string
}

export interface ShipmentStatusUpdateRequest {
  status: ShipmentStatus
}

// ── Shared ────────────────────────────────────────────────────────────
export interface ErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  traceId?: string
  fieldErrors?: Record<string, string>
}

export interface ApiError {
  message: string
  status: number
  fieldErrors?: Record<string, string>
}
