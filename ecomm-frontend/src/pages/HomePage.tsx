import { Link } from 'react-router-dom'
import { ArrowRight, ShoppingBag, Truck, Shield } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { useQuery } from '@tanstack/react-query'
import { productsApi } from '@/api/products'
import { ProductCard } from '@/components/shared/ProductCard'
import { Skeleton } from '@/components/ui/skeleton'

export default function HomePage() {
  const { data, isLoading } = useQuery({
    queryKey: ['products', { page: 0, size: 8 }],
    queryFn: () => productsApi.getProducts({ page: 0, size: 8 }),
  })

  return (
    <div>
      {/* Hero */}
      <section className="bg-gradient-to-br from-primary/5 via-background to-primary/10 py-20 px-4">
        <div className="container text-center space-y-6">
          <h1 className="text-4xl md:text-5xl font-bold tracking-tight">
            Shop Everything,<br />
            <span className="text-primary">Delivered Fast</span>
          </h1>
          <p className="text-muted-foreground text-lg max-w-xl mx-auto">
            Explore our curated catalog. Every order is tracked end-to-end with real-time saga orchestration.
          </p>
          <div className="flex items-center justify-center gap-4">
            <Button size="lg" asChild>
              <Link to="/products">
                Browse Products <ArrowRight className="ml-2 h-4 w-4" />
              </Link>
            </Button>
            <Button size="lg" variant="outline" asChild>
              <Link to="/track">Track Order</Link>
            </Button>
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="py-16 px-4 bg-muted/30">
        <div className="container grid grid-cols-1 md:grid-cols-3 gap-6">
          {[
            { icon: ShoppingBag, title: 'Wide Selection', desc: 'Hundreds of products across all categories' },
            { icon: Truck, title: 'Fast Delivery', desc: 'Real-time shipment tracking from warehouse to door' },
            { icon: Shield, title: 'Secure Payments', desc: 'End-to-end saga ensures no double charges' },
          ].map(({ icon: Icon, title, desc }) => (
            <Card key={title}>
              <CardContent className="pt-6 text-center space-y-3">
                <div className="flex justify-center">
                  <div className="h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center">
                    <Icon className="h-6 w-6 text-primary" />
                  </div>
                </div>
                <h3 className="font-semibold">{title}</h3>
                <p className="text-sm text-muted-foreground">{desc}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      </section>

      {/* Featured products */}
      <section className="py-16 px-4">
        <div className="container">
          <div className="flex items-center justify-between mb-8">
            <h2 className="text-2xl font-bold">Featured Products</h2>
            <Button variant="outline" asChild>
              <Link to="/products">View all <ArrowRight className="ml-2 h-4 w-4" /></Link>
            </Button>
          </div>

          {isLoading ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
              {Array.from({ length: 8 }).map((_, i) => (
                <div key={i} className="space-y-3">
                  <Skeleton className="h-48 w-full rounded-lg" />
                  <Skeleton className="h-4 w-3/4" />
                  <Skeleton className="h-4 w-1/2" />
                </div>
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
              {data?.content.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
