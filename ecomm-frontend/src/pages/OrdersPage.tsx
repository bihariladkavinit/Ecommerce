import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Package, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import { ordersApi } from '@/api/orders'
import { format } from 'date-fns'

function StatusBadge({ status }: { status: string }) {
  if (status === 'CONFIRMED') return <Badge variant="success">Confirmed</Badge>
  if (status === 'CANCELLED') return <Badge variant="destructive">Cancelled</Badge>
  return <Badge variant="warning">Pending</Badge>
}

export default function OrdersPage() {
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['orders', page],
    queryFn: (): Promise<import('@/types').PageOrderResponse> => ordersApi.getOrders({ page, size: 10 }),
  })

  return (
    <div className="container py-8 max-w-3xl">
      <h1 className="text-2xl font-bold mb-6">My Orders</h1>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <div key={i} className="border rounded-lg p-5 space-y-2">
              <Skeleton className="h-5 w-1/3" />
              <Skeleton className="h-4 w-1/4" />
            </div>
          ))}
        </div>
      ) : data?.empty ? (
        <div className="flex flex-col items-center justify-center py-20 gap-4 text-center">
          <Package className="h-16 w-16 text-muted-foreground/30" />
          <h2 className="text-xl font-semibold">No orders yet</h2>
          <p className="text-muted-foreground">Start shopping to see your orders here</p>
          <Button asChild><Link to="/products">Browse Products</Link></Button>
        </div>
      ) : (
        <>
          <div className="space-y-3">
            {data?.content.map(order => (
              <Link
                key={order.id}
                to={`/orders/${order.id}`}
                className="block border rounded-lg p-5 hover:shadow-md transition-shadow bg-card group"
              >
                <div className="flex items-start justify-between">
                  <div className="space-y-1">
                    <div className="flex items-center gap-3">
                      <span className="font-mono text-sm text-muted-foreground">
                        #{order.id.slice(0, 8)}...
                      </span>
                      <StatusBadge status={order.status} />
                    </div>
                    <p className="text-sm text-muted-foreground">
                      {format(new Date(order.createdAt), 'PPP')}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {order.items?.length ?? 0} item{(order.items?.length ?? 0) !== 1 ? 's' : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-bold text-lg">${order.totalAmount.toFixed(2)}</span>
                    <ChevronRight className="h-5 w-5 text-muted-foreground group-hover:text-foreground transition-colors" />
                  </div>
                </div>
                {order.items && order.items.length > 0 && (
                  <>
                    <Separator className="my-3" />
                    <p className="text-xs text-muted-foreground line-clamp-1">
                      {order.items.map(i => i.productName).join(', ')}
                    </p>
                  </>
                )}
              </Link>
            ))}
          </div>

          {data && data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 mt-8">
              <Button variant="outline" size="sm" disabled={data.first} onClick={() => setPage(p => p - 1)}>
                Previous
              </Button>
              <span className="text-sm text-muted-foreground">
                Page {page + 1} of {data.totalPages}
              </span>
              <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage(p => p + 1)}>
                Next
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
