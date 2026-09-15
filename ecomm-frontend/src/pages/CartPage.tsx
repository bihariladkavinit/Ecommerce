import { Link, useNavigate } from 'react-router-dom'
import { Trash2, ShoppingBag, Plus, Minus, ImageOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { useCartStore } from '@/stores/cartStore'
import { toast } from '@/hooks/use-toast'
import { useState } from 'react'

export default function CartPage() {
  const { items, total, isLoading, updateItem, removeItem } = useCartStore()
  const navigate = useNavigate()
  const [loadingItem, setLoadingItem] = useState<string | null>(null)

  const handleQtyChange = async (productId: string, newQty: number) => {
    if (newQty < 1) return
    setLoadingItem(productId)
    try {
      await updateItem(productId, newQty)
    } catch {
      toast({ variant: 'destructive', title: 'Could not update quantity' })
    } finally {
      setLoadingItem(null)
    }
  }

  const handleRemove = async (productId: string, name: string) => {
    setLoadingItem(productId)
    try {
      await removeItem(productId)
      toast({ title: 'Item removed', description: name })
    } catch {
      toast({ variant: 'destructive', title: 'Could not remove item' })
    } finally {
      setLoadingItem(null)
    }
  }

  if (isLoading) {
    return (
      <div className="container py-8 max-w-4xl">
        <h1 className="text-2xl font-bold mb-6">Shopping Cart</h1>
        <div className="space-y-4">
          {[1, 2, 3].map(i => (
            <div key={i} className="flex gap-4 p-4 border rounded-lg">
              <Skeleton className="h-20 w-20 rounded" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-4 w-1/4" />
              </div>
            </div>
          ))}
        </div>
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className="container py-8 max-w-4xl">
        <h1 className="text-2xl font-bold mb-6">Shopping Cart</h1>
        <div className="flex flex-col items-center justify-center py-20 gap-4 text-center">
          <ShoppingBag className="h-16 w-16 text-muted-foreground/30" />
          <h2 className="text-xl font-semibold">Your cart is empty</h2>
          <p className="text-muted-foreground">Add some products to get started</p>
          <Button asChild><Link to="/products">Browse Products</Link></Button>
        </div>
      </div>
    )
  }

  return (
    <div className="container py-8 max-w-4xl">
      <h1 className="text-2xl font-bold mb-6">Shopping Cart</h1>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Items list */}
        <div className="lg:col-span-2 space-y-4">
          {items.map(item => (
            <div
              key={item.productId}
              className="flex gap-4 p-4 border rounded-lg bg-card hover:shadow-sm transition-shadow"
            >
              {/* Image */}
              <Link to={`/products/${item.productId}`} className="shrink-0">
                <div className="h-20 w-20 rounded bg-muted flex items-center justify-center overflow-hidden">
                  <ImageOff className="h-8 w-8 text-muted-foreground/30" />
                </div>
              </Link>

              {/* Info */}
              <div className="flex-1 min-w-0">
                <Link
                  to={`/products/${item.productId}`}
                  className="font-medium text-sm hover:text-primary transition-colors line-clamp-2"
                >
                  {item.name}
                </Link>
                <p className="text-sm text-muted-foreground mt-1">
                  ${item.price.toFixed(2)} each
                </p>
                <p className="text-sm font-semibold mt-1">
                  Subtotal: ${item.subtotal.toFixed(2)}
                </p>
              </div>

              {/* Qty + Remove */}
              <div className="flex flex-col items-end gap-2 shrink-0">
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7 text-muted-foreground hover:text-destructive"
                  disabled={loadingItem === item.productId}
                  onClick={() => handleRemove(item.productId, item.name)}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>

                <div className="flex items-center border rounded">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    disabled={loadingItem === item.productId || item.qty <= 1}
                    onClick={() => handleQtyChange(item.productId, item.qty - 1)}
                  >
                    <Minus className="h-3 w-3" />
                  </Button>
                  <span className="w-8 text-center text-sm font-medium">{item.qty}</span>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    disabled={loadingItem === item.productId}
                    onClick={() => handleQtyChange(item.productId, item.qty + 1)}
                  >
                    <Plus className="h-3 w-3" />
                  </Button>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Order summary */}
        <div className="lg:col-span-1">
          <div className="border rounded-lg p-6 space-y-4 sticky top-24">
            <h2 className="font-semibold text-lg">Order Summary</h2>
            <Separator />

            <div className="space-y-2 text-sm">
              {items.map(item => (
                <div key={item.productId} className="flex justify-between">
                  <span className="text-muted-foreground truncate max-w-[60%]">
                    {item.name} ×{item.qty}
                  </span>
                  <span>${item.subtotal.toFixed(2)}</span>
                </div>
              ))}
            </div>

            <Separator />

            <div className="flex justify-between font-semibold text-base">
              <span>Total</span>
              <span>${total.toFixed(2)}</span>
            </div>

            <Button
              className="w-full"
              size="lg"
              onClick={() => navigate('/checkout')}
            >
              Proceed to Checkout
            </Button>

            <Button variant="outline" className="w-full" asChild>
              <Link to="/products">Continue Shopping</Link>
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
