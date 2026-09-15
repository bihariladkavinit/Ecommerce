import { useQuery } from '@tanstack/react-query'
import { Package, Tag, Warehouse, ShoppingCart } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { productsApi } from '@/api/products'

export default function AdminDashboardPage() {
  const { data: products, isLoading: loadingProducts } = useQuery({
    queryKey: ['admin-products-count'],
    queryFn: () => productsApi.getProducts({ page: 0, size: 1 }),
  })

  const { data: categories, isLoading: loadingCats } = useQuery({
    queryKey: ['categories'],
    queryFn: productsApi.getCategories,
    staleTime: 5 * 60_000,
  })

  const stats = [
    { label: 'Total Products',   value: products?.totalElements, icon: Package,      loading: loadingProducts },
    { label: 'Categories',       value: categories?.length,      icon: Tag,          loading: loadingCats },
    { label: 'Infra',            value: 'Healthy',               icon: Warehouse,    loading: false },
    { label: 'Services Online',  value: '8 / 8',                 icon: ShoppingCart, loading: false },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground text-sm mt-1">Overview of your store</p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map(({ label, value, icon: Icon, loading }) => (
          <Card key={label}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
              <Icon className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              {loading ? (
                <Skeleton className="h-7 w-16" />
              ) : (
                <div className="text-2xl font-bold">{value ?? '—'}</div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Quick links</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground space-y-1">
          <p>→ Use <strong>Products</strong> to create, edit, or soft-delete catalog items.</p>
          <p>→ Use <strong>Categories</strong> to organise the product hierarchy.</p>
          <p>→ Use <strong>Inventory</strong> to restock available quantities per product.</p>
        </CardContent>
      </Card>
    </div>
  )
}
