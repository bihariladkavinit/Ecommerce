import { Link } from 'react-router-dom'
import { ShoppingCart, ImageOff } from 'lucide-react'
import { Card, CardContent, CardFooter } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import type { ProductResponse } from '@/types'
import { useCartStore } from '@/stores/cartStore'
import { useAuthStore } from '@/stores/authStore'
import { toast } from '@/hooks/use-toast'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

interface ProductCardProps {
  product: ProductResponse
}

export function ProductCard({ product }: ProductCardProps) {
  const [adding, setAdding] = useState(false)
  const { addItem } = useCartStore()
  const { isAuthenticated } = useAuthStore()
  const navigate = useNavigate()

  const handleAddToCart = async (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (!isAuthenticated) {
      navigate('/login')
      return
    }
    setAdding(true)
    try {
      await addItem(product.id, 1)
      toast({ title: 'Added to cart', description: product.name })
    } catch {
      toast({ variant: 'destructive', title: 'Could not add item', description: 'Please try again' })
    } finally {
      setAdding(false)
    }
  }

  return (
    <Card className="group overflow-hidden hover:shadow-md transition-shadow">
      <Link to={`/products/${product.id}`}>
        <div className="relative aspect-square overflow-hidden bg-muted">
          {product.imageUrl ? (
            <img
              src={product.imageUrl}
              alt={product.name}
              className="object-cover w-full h-full group-hover:scale-105 transition-transform duration-300"
            />
          ) : (
            <div className="flex items-center justify-center h-full text-muted-foreground">
              <ImageOff className="h-12 w-12 opacity-30" />
            </div>
          )}
          {!product.active && (
            <div className="absolute inset-0 bg-background/60 flex items-center justify-center">
              <Badge variant="secondary">Unavailable</Badge>
            </div>
          )}
        </div>

        <CardContent className="p-4 pb-2">
          <h3 className="font-medium text-sm line-clamp-2 group-hover:text-primary transition-colors">
            {product.name}
          </h3>
          <p className="text-lg font-bold mt-1">${product.price.toFixed(2)}</p>
        </CardContent>
      </Link>

      <CardFooter className="p-4 pt-0">
        <Button
          size="sm"
          className="w-full"
          onClick={handleAddToCart}
          disabled={adding || !product.active}
        >
          <ShoppingCart className="h-4 w-4 mr-2" />
          {adding ? 'Adding...' : 'Add to Cart'}
        </Button>
      </CardFooter>
    </Card>
  )
}
