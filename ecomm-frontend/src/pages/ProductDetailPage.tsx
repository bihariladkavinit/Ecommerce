import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ShoppingCart, ArrowLeft, ImageOff, CheckCircle, XCircle } from 'lucide-react'
import { useState } from 'react'
import { productsApi } from '@/api/products'
import { inventoryApi } from '@/api/inventory'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import { useCartStore } from '@/stores/cartStore'
import { useAuthStore } from '@/stores/authStore'
import { toast } from '@/hooks/use-toast'

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [qty, setQty] = useState(1)
  const [adding, setAdding] = useState(false)
  const { addItem } = useCartStore()
  const { isAuthenticated } = useAuthStore()

  const { data: product, isLoading, isError } = useQuery({
    queryKey: ['product', id],
    queryFn: () => productsApi.getProduct(id!),
    enabled: !!id,
  })

  const { data: availability } = useQuery({
    queryKey: ['availability', id, qty],
    queryFn: () => inventoryApi.checkAvailability(id!, qty),
    enabled: !!id && !!product,
    retry: false,
  })

  const handleAddToCart = async () => {
    if (!isAuthenticated) { navigate('/login'); return }
    setAdding(true)
    try {
      await addItem(id!, qty)
      toast({ title: 'Added to cart', description: `${qty}x ${product?.name}` })
    } catch {
      toast({ variant: 'destructive', title: 'Could not add item', description: 'Please try again' })
    } finally {
      setAdding(false)
    }
  }

  if (isLoading) {
    return (
      <div className="container py-8">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
          <Skeleton className="aspect-square w-full rounded-lg" />
          <div className="space-y-4">
            <Skeleton className="h-8 w-3/4" />
            <Skeleton className="h-6 w-1/4" />
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-10 w-1/3" />
          </div>
        </div>
      </div>
    )
  }

  if (isError || !product) {
    return (
      <div className="container py-8 text-center">
        <p className="text-muted-foreground">Product not found.</p>
        <Button variant="outline" className="mt-4" asChild>
          <Link to="/products">Back to Products</Link>
        </Button>
      </div>
    )
  }

  const inStock = availability?.available !== false

  return (
    <div className="container py-8">
      <Button variant="ghost" size="sm" className="mb-6" onClick={() => navigate(-1)}>
        <ArrowLeft className="h-4 w-4 mr-2" /> Back
      </Button>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
        {/* Image */}
        <div className="aspect-square rounded-lg overflow-hidden bg-muted flex items-center justify-center">
          {product.imageUrl ? (
            <img src={product.imageUrl} alt={product.name} className="object-cover w-full h-full" />
          ) : (
            <ImageOff className="h-24 w-24 text-muted-foreground/30" />
          )}
        </div>

        {/* Info */}
        <div className="space-y-5">
          <div>
            <h1 className="text-3xl font-bold">{product.name}</h1>
            <p className="text-3xl font-bold text-primary mt-2">${product.price.toFixed(2)}</p>
          </div>

          {/* Stock status */}
          <div className="flex items-center gap-2">
            {inStock ? (
              <>
                <CheckCircle className="h-4 w-4 text-green-600" />
                <span className="text-sm text-green-700 font-medium">In Stock</span>
              </>
            ) : (
              <>
                <XCircle className="h-4 w-4 text-destructive" />
                <span className="text-sm text-destructive font-medium">Out of Stock</span>
              </>
            )}
            {!product.active && <Badge variant="secondary">Unavailable</Badge>}
          </div>

          <Separator />

          {product.description && (
            <p className="text-muted-foreground leading-relaxed">{product.description}</p>
          )}

          {/* Quantity + Add to Cart */}
          <div className="flex items-center gap-4">
            <div className="flex items-center border rounded-md">
              <Button
                variant="ghost"
                size="icon"
                className="h-10 w-10 rounded-r-none"
                onClick={() => setQty(q => Math.max(1, q - 1))}
                disabled={qty <= 1}
              >−</Button>
              <span className="w-12 text-center text-sm font-medium">{qty}</span>
              <Button
                variant="ghost"
                size="icon"
                className="h-10 w-10 rounded-l-none"
                onClick={() => setQty(q => q + 1)}
              >+</Button>
            </div>

            <Button
              size="lg"
              className="flex-1"
              onClick={handleAddToCart}
              disabled={adding || !product.active || !inStock}
            >
              <ShoppingCart className="h-5 w-5 mr-2" />
              {adding ? 'Adding...' : 'Add to Cart'}
            </Button>
          </div>

          <Separator />

          <div className="text-xs text-muted-foreground space-y-1">
            <p>Product ID: <span className="font-mono">{product.id}</span></p>
          </div>
        </div>
      </div>
    </div>
  )
}
