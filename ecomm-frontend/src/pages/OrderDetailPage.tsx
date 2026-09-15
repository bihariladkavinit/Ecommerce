import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { ArrowLeft, Package, Truck, CheckCircle2, XCircle, Clock, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from '@/components/ui/alert-dialog'
import { ordersApi } from '@/api/orders'
import { shipmentsApi } from '@/api/shipments'
import { toast } from '@/hooks/use-toast'
import { format } from 'date-fns'

const SAGA_STEPS = [
  'CREATED',
  'INVENTORY_RESERVED',
  'PAYMENT_COMPLETED',
  'INVENTORY_CONFIRMED',
  'SHIPMENT_CREATED',
  'CONFIRMED',
]

function SagaTimeline({ sagaState }: { sagaState: string }) {
  const isCancelled = sagaState === 'CANCELLED' || sagaState === 'COMPENSATING'
  const currentIdx = SAGA_STEPS.indexOf(sagaState)

  return (
    <div className="space-y-2">
      {isCancelled ? (
        <div className="flex items-center gap-2 text-destructive">
          <XCircle className="h-5 w-5" />
          <span className="font-medium">Order Cancelled</span>
          {sagaState === 'COMPENSATING' && (
            <span className="text-xs text-muted-foreground">(compensation in progress)</span>
          )}
        </div>
      ) : (
        <ol className="flex flex-col gap-0">
          {SAGA_STEPS.map((step, idx) => {
            const done = currentIdx >= idx
            const active = currentIdx === idx
            return (
              <li key={step} className="flex items-start gap-3">
                <div className="flex flex-col items-center">
                  <div className={`h-6 w-6 rounded-full flex items-center justify-center text-xs font-bold border-2 ${
                    done ? 'bg-primary border-primary text-primary-foreground' :
                    active ? 'border-primary text-primary animate-pulse' :
                    'border-muted-foreground/30 text-muted-foreground/30'
                  }`}>
                    {done ? '✓' : idx + 1}
                  </div>
                  {idx < SAGA_STEPS.length - 1 && (
                    <div className={`w-0.5 h-6 ${done ? 'bg-primary' : 'bg-muted-foreground/20'}`} />
                  )}
                </div>
                <span className={`text-sm pt-0.5 ${done ? 'font-medium' : 'text-muted-foreground'}`}>
                  {step.replace(/_/g, ' ')}
                </span>
              </li>
            )
          })}
        </ol>
      )}
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  if (status === 'CONFIRMED') return <Badge variant="success">Confirmed</Badge>
  if (status === 'CANCELLED') return <Badge variant="destructive">Cancelled</Badge>
  return <Badge variant="warning">Pending</Badge>
}

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()

  const { data: order, isLoading } = useQuery({
    queryKey: ['order', id],
    queryFn: () => ordersApi.getOrder(id!),
    enabled: !!id,
  })

  // Poll saga status while PENDING
  const { data: statusData } = useQuery({
    queryKey: ['order-status', id],
    queryFn: () => ordersApi.getOrderStatus(id!),
    enabled: !!id && order?.status === 'PENDING',
    refetchInterval: 3000,
  })

  // Stop polling and refetch full order when status becomes terminal
  useEffect(() => {
    if (statusData && (statusData as { status?: string }).status !== 'PENDING') {
      qc.invalidateQueries({ queryKey: ['order', id] })
    }
  }, [statusData, id, qc])

  const typedStatusData = statusData as import('@/types').OrderStatusResponse | undefined
  const sagaState = typedStatusData?.sagaState ?? order?.sagaState ?? ''
  const currentStatus = typedStatusData?.status ?? order?.status ?? 'PENDING'
  const isCancellable = sagaState === 'CREATED' || sagaState === 'INVENTORY_RESERVED'

  // Fetch shipment only when CONFIRMED
  const { data: shipment } = useQuery({
    queryKey: ['shipment', id],
    queryFn: () => shipmentsApi.getShipmentByOrderId(id!),
    enabled: !!id && (order?.status === 'CONFIRMED' || typedStatusData?.status === 'CONFIRMED'),
    retry: false,
  })

  const cancelMutation = useMutation({
    mutationFn: () => ordersApi.cancelOrder(id!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['order', id] })
      toast({ title: 'Order cancelled' })
    },
    onError: () => toast({ variant: 'destructive', title: 'Cannot cancel this order' }),
  })

  if (isLoading) {
    return (
      <div className="container py-8 max-w-3xl space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full rounded-lg" />
        <Skeleton className="h-40 w-full rounded-lg" />
      </div>
    )
  }

  if (!order) {
    return (
      <div className="container py-8 text-center">
        <p className="text-muted-foreground">Order not found.</p>
        <Button variant="outline" className="mt-4" asChild><Link to="/orders">My Orders</Link></Button>
      </div>
    )
  }

  return (
    <div className="container py-8 max-w-3xl space-y-6">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={() => navigate('/orders')}>
          <ArrowLeft className="h-4 w-4 mr-1" /> Orders
        </Button>
        <h1 className="text-xl font-bold">Order #{order.id.slice(0, 8)}...</h1>
        <StatusBadge status={currentStatus} />
        {order.status === 'PENDING' && (
          <RefreshCw className="h-4 w-4 text-muted-foreground animate-spin ml-auto" />
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Saga timeline */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Clock className="h-4 w-4" /> Order Progress
            </CardTitle>
          </CardHeader>
          <CardContent>
            <SagaTimeline sagaState={sagaState || order.sagaState} />
          </CardContent>
        </Card>

        {/* Shipment */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Truck className="h-4 w-4" /> Shipment
            </CardTitle>
          </CardHeader>
          <CardContent>
            {shipment ? (
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Status</span>
                  <Badge variant={shipment.status === 'DELIVERED' ? 'success' : 'secondary'}>
                    {shipment.status}
                  </Badge>
                </div>
                {shipment.carrier && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Carrier</span>
                    <span>{shipment.carrier}</span>
                  </div>
                )}
                {shipment.trackingNumber && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Tracking</span>
                    <Link
                      to={`/track/${shipment.trackingNumber}`}
                      className="text-primary hover:underline font-mono text-xs"
                    >
                      {shipment.trackingNumber}
                    </Link>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Created</span>
                  <span>{format(new Date(shipment.createdAt), 'MMM d, yyyy')}</span>
                </div>
              </div>
            ) : currentStatus === 'CONFIRMED' ? (
              <div className="flex items-center gap-2 text-muted-foreground text-sm">
                <RefreshCw className="h-4 w-4 animate-spin" /> Loading shipment...
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                Shipment details will appear once your order is confirmed.
              </p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Order items */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Package className="h-4 w-4" /> Items
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {order.items?.map(item => (
            <div key={item.productId} className="flex justify-between text-sm">
              <div>
                <p className="font-medium">{item.productName}</p>
                <p className="text-muted-foreground">Qty: {item.qty} × ${item.unitPrice.toFixed(2)}</p>
              </div>
              <span className="font-medium">${(item.qty * item.unitPrice).toFixed(2)}</span>
            </div>
          ))}
          <Separator />
          <div className="flex justify-between font-bold">
            <span>Total</span>
            <span>${order.totalAmount.toFixed(2)}</span>
          </div>
        </CardContent>
      </Card>

      {/* Meta */}
      <div className="flex items-center justify-between text-sm text-muted-foreground">
        <span>Placed {format(new Date(order.createdAt), 'PPP')}</span>
        {isCancellable && (
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button variant="outline" size="sm" className="text-destructive border-destructive hover:bg-destructive/10">
                <XCircle className="h-4 w-4 mr-2" /> Cancel Order
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Cancel this order?</AlertDialogTitle>
                <AlertDialogDescription>
                  This will cancel your order. If inventory was reserved, it will be released.
                  This action cannot be undone.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Keep Order</AlertDialogCancel>
                <AlertDialogAction
                  className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                  onClick={() => cancelMutation.mutate()}
                >
                  Yes, Cancel Order
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        )}
      </div>
    </div>
  )
}
