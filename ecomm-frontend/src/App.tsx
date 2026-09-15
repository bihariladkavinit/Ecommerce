import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { AppLayout } from '@/components/shared/AppLayout'
import { ProtectedRoute } from '@/components/shared/ProtectedRoute'
import { Toaster } from '@/components/ui/toaster'

// Public pages
const HomePage       = lazy(() => import('@/pages/HomePage'))
const LoginPage      = lazy(() => import('@/pages/LoginPage'))
const RegisterPage   = lazy(() => import('@/pages/RegisterPage'))
const ProductsPage   = lazy(() => import('@/pages/ProductsPage'))
const ProductDetailPage = lazy(() => import('@/pages/ProductDetailPage'))
const TrackingPage   = lazy(() => import('@/pages/TrackingPage'))
const NotFoundPage   = lazy(() => import('@/pages/NotFoundPage'))

// Authenticated pages
const CartPage       = lazy(() => import('@/pages/CartPage'))
const CheckoutPage   = lazy(() => import('@/pages/CheckoutPage'))
const OrdersPage     = lazy(() => import('@/pages/OrdersPage'))
const OrderDetailPage = lazy(() => import('@/pages/OrderDetailPage'))
const ProfilePage    = lazy(() => import('@/pages/ProfilePage'))

// Admin pages
const AdminLayout        = lazy(() => import('@/pages/admin/AdminLayout'))
const AdminDashboardPage = lazy(() => import('@/pages/admin/AdminDashboardPage'))
const AdminProductsPage  = lazy(() => import('@/pages/admin/AdminProductsPage'))
const AdminCategoriesPage = lazy(() => import('@/pages/admin/AdminCategoriesPage'))
const AdminInventoryPage = lazy(() => import('@/pages/admin/AdminInventoryPage'))

function PageLoader() {
  return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
    </div>
  )
}

export default function App() {
  return (
    <>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          {/* Auth pages (no Navbar) */}
          <Route path="/login"    element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Main layout */}
          <Route element={<AppLayout />}>
            <Route path="/"                element={<HomePage />} />
            <Route path="/products"        element={<ProductsPage />} />
            <Route path="/products/:id"    element={<ProductDetailPage />} />
            <Route path="/track"           element={<TrackingPage />} />
            <Route path="/track/:tracking" element={<TrackingPage />} />

            {/* Authenticated */}
            <Route path="/cart" element={
              <ProtectedRoute><CartPage /></ProtectedRoute>
            } />
            <Route path="/checkout" element={
              <ProtectedRoute><CheckoutPage /></ProtectedRoute>
            } />
            <Route path="/orders" element={
              <ProtectedRoute><OrdersPage /></ProtectedRoute>
            } />
            <Route path="/orders/:id" element={
              <ProtectedRoute><OrderDetailPage /></ProtectedRoute>
            } />
            <Route path="/profile" element={
              <ProtectedRoute><ProfilePage /></ProtectedRoute>
            } />

            {/* Admin */}
            <Route path="/admin" element={
              <ProtectedRoute adminOnly><AdminLayout /></ProtectedRoute>
            }>
              <Route index element={<AdminDashboardPage />} />
              <Route path="products"   element={<AdminProductsPage />} />
              <Route path="categories" element={<AdminCategoriesPage />} />
              <Route path="inventory"  element={<AdminInventoryPage />} />
            </Route>

            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </Suspense>
      <Toaster />
    </>
  )
}
