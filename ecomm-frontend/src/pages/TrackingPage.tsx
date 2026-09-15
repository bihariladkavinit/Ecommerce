import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Search, Truck, Package, CheckCircle, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { shipmentsApi } from '@/api/shipments'
import { format } from 'date-fns'

function ShipmentStatusBadge({ status }: { status: string }) {
  if (status === 'DELIVERED') return <Badge variant="success">Delivered</Badge>
  if (status === 'DISPATCHED') return <Badge variant="warning">Dispatched</Badge>
  if (status === 'CANCELLED') return <Badge variant="destructive">Cancelled</Badge>
  return <Badge variant="secondary">Created</Badge>
}

function ShipmentTimeline({ status }: { status: string }) {
  const steps = ['CREATED', 'DISPATCHED', 'DELIVERED']
  const currentIdx = steps.indexOf(status)
  const isCancelled = status === 'CANCELLED'

  if (isCancelled) {
    return (
      <div className="flex items-center gap-2 text-destructive">
        <XCircle className="h-5 w-5" />
        <span className="font-medium">Shipment Cancelled</span>
      </div>
    )
  }

  return (
    <ol className="flex items-center w-full">
      {steps.map((step, idx) => (
        <li key={step} className={`flex items-center ${idx < steps.length - 1 ? 'flex-1' : ''}`}>
          <div className="flex flex-col items-center">
            <div className={`h-8 w-8 rounded-full flex items-center justify-center text-xs font-bold border-2 ${
              currentIdx >= idx
                ? 'bg-primary border-primary text-primary-foreground'
                : 'border-muted-foreground/30 text-muted-foreground/30'
            }`}>
              {currentIdx >= idx ? <CheckCircle className="h-4 w-4" /> : idx + 1}
            </div>
            <span className={`text-xs mt-1 ${currentIdx >= idx ? 'font-medium' : 'text-muted-foreground'}`}>
              {step}
            </span>
          </div>
          {idx < steps.length - 1 && (
            <div className={`flex-1 h-0.5 mx-2 mb-5 ${currentIdx > idx ? 'bg-primary' : 'bg-muted-foreground/20'}`} />
          )}
        </li>
      ))}
    </ol>
  )
}

export default function TrackingPage() {
  const { tracking: urlTracking } = useParams<{ tracking?: string }>()
  const navigate = useNavigate()
  const [inputValue, setInputValue] = useState(urlTracking ?? '')
  const [searchTracking, setSearchTracking] = useState(urlTracking ?? '')

  const { data: shipment, isLoading, isError, error } = useQuery({
    queryKey: ['track', searchTracking],
    queryFn: () => shipmentsApi.trackShipment(searchTracking),
    enabled: searchTracking.length > 0,
    retry: false,
  })

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    if (!inputValue.trim()) return
    setSearchTracking(inputValue.trim())
    navigate(`/track/${inputValue.trim()}`, { replace: true })
  }

  return (
    <div className="container py-12 max-w-2xl">
      <div className="text-center mb-8">
        <div className="flex justify-center mb-4">
          <div className="h-16 w-16 rounded-full bg-primary/10 flex items-center justify-center">
            <Truck className="h-8 w-8 text-primary" />
          </div>
        </div>
        <h1 className="text-2xl font-bold">Track Your Order</h1>
        <p className="text-muted-foreground mt-2">Enter your tracking number to see the current status</p>
      </div>

      <form onSubmit={handleSearch} className="flex gap-2 mb-8">
        <Input
          placeholder="e.g. TRK-A1B2C3D4"
          value={inputValue}
          onChange={e => setInputValue(e.target.value)}
          className="flex-1"
        />
        <Button type="submit" disabled={isLoading || !inputValue.trim()}>
          <Search className="h-4 w-4 mr-2" />
          {isLoading ? 'Tracking...' : 'Track'}
        </Button>
      </form>

      {isError && (
        <Card className="border-destructive/50">
          <CardContent className="pt-6 text-center text-destructive">
            <XCircle className="h-8 w-8 mx-auto mb-2" />
            <p className="font-medium">Shipment not found</p>
            <p className="text-sm text-muted-foreground mt-1">
              Check the tracking number and try again.
            </p>
          </CardContent>
        </Card>
      )}

      {shipment && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Package className="h-5 w-5" />
                <span className="font-mono text-sm">{shipment.trackingNumber}</span>
              </div>
              <ShipmentStatusBadge status={shipment.status} />
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            <ShipmentTimeline status={shipment.status} />
            <Separator />
            <div className="grid grid-cols-2 gap-4 text-sm">
              {shipment.carrier && (
                <>
                  <span className="text-muted-foreground">Carrier</span>
                  <span className="font-medium">{shipment.carrier}</span>
                </>
              )}
              <span className="text-muted-foreground">Order ID</span>
              <span className="font-mono text-xs">{shipment.orderId.slice(0, 16)}...</span>
              <span className="text-muted-foreground">Created</span>
              <span>{format(new Date(shipment.createdAt), 'PPP')}</span>
              <span className="text-muted-foreground">Last updated</span>
              <span>{format(new Date(shipment.updatedAt), 'PPp')}</span>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
