import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { MapPin, Plus, ShoppingBag, CheckCircle2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { usersApi } from '@/api/users'
import { cartApi } from '@/api/cart'
import { useCartStore } from '@/stores/cartStore'
import { toast } from '@/hooks/use-toast'
import type { AddressResponse, ErrorResponse } from '@/types'
import type { AxiosError } from 'axios'
import { cn } from '@/lib/utils'

const addressSchema = z.object({
  line1: z.string().min(1, 'Street address required'),
  line2: z.string().optional(),
  city: z.string().min(1, 'City required'),
  state: z.string().optional(),
  zip: z.string().min(1, 'ZIP required'),
  country: z.string().min(1, 'Country required'),
})
type AddressForm = z.infer<typeof addressSchema>

export default function CheckoutPage() {
  const navigate = useNavigate()
  const { items, total, clearCartLocal } = useCartStore()
  const [selectedAddressId, setSelectedAddressId] = useState<string | null>(null)
  const [addAddressOpen, setAddAddressOpen] = useState(false)

  const { data: addresses, isLoading: loadingAddresses, refetch: refetchAddresses } = useQuery({
    queryKey: ['addresses'],
    queryFn: (): Promise<import('@/types').AddressResponse[]> => usersApi.getAddresses(),
  })

  // Pre-select default address when addresses load
  useEffect(() => {
    if (!selectedAddressId && addresses && addresses.length > 0) {
      const def = addresses.find((a: AddressResponse) => a.default) ?? addresses[0]
      setSelectedAddressId(def.id)
    }
  }, [addresses, selectedAddressId])

  const { register: registerAddr, handleSubmit: handleAddrSubmit, reset: resetAddr, formState: { errors: addrErrors } } = useForm<AddressForm>({
    resolver: zodResolver(addressSchema),
  })

  const addAddressMutation = useMutation({
    mutationFn: (data: AddressForm) => usersApi.addAddress(data),
    onSuccess: (newAddr) => {
      refetchAddresses()
      setSelectedAddressId(newAddr.id)
      setAddAddressOpen(false)
      resetAddr()
      toast({ title: 'Address added' })
    },
    onError: () => toast({ variant: 'destructive', title: 'Could not save address' }),
  })

  const checkoutMutation = useMutation({
    mutationFn: (shippingAddressId: string) => cartApi.checkout({ shippingAddressId }),
    onSuccess: (order) => {
      clearCartLocal()
      toast({ title: 'Order placed!', description: `Order #${order.id.slice(0, 8)}...` })
      navigate(`/orders/${order.id}`)
    },
    onError: (err: AxiosError<ErrorResponse>) => {
      const msg = err.response?.data?.message || 'Checkout failed — please retry'
      toast({ variant: 'destructive', title: 'Checkout failed', description: msg })
    },
  })

  const handlePlaceOrder = () => {
    if (!selectedAddressId) {
      toast({ variant: 'destructive', title: 'Select a delivery address' })
      return
    }
    checkoutMutation.mutate(selectedAddressId)
  }

  if (items.length === 0) {
    return (
      <div className="container py-8 max-w-2xl text-center">
        <ShoppingBag className="h-16 w-16 mx-auto text-muted-foreground/30 mb-4" />
        <h2 className="text-xl font-semibold mb-2">Your cart is empty</h2>
        <Button asChild><Link to="/products">Browse Products</Link></Button>
      </div>
    )
  }

  return (
    <div className="container py-8 max-w-4xl">
      <h1 className="text-2xl font-bold mb-6">Checkout</h1>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Left — address selection */}
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <MapPin className="h-5 w-5" /> Delivery Address
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {loadingAddresses ? (
                <div className="space-y-3">
                  {[1, 2].map(i => <Skeleton key={i} className="h-20 w-full rounded-lg" />)}
                </div>
              ) : addresses && addresses.length > 0 ? (
                addresses.map(addr => (
                  <div
                    key={addr.id}
                    onClick={() => setSelectedAddressId(addr.id)}
                    className={cn(
                      'border rounded-lg p-4 cursor-pointer transition-colors',
                      selectedAddressId === addr.id
                        ? 'border-primary bg-primary/5'
                        : 'hover:border-primary/50'
                    )}
                  >
                    <div className="flex items-start justify-between">
                      <div className="text-sm space-y-0.5">
                        <p className="font-medium">{addr.line1}{addr.line2 ? `, ${addr.line2}` : ''}</p>
                        <p className="text-muted-foreground">{addr.city}{addr.state ? `, ${addr.state}` : ''} {addr.zip}</p>
                        <p className="text-muted-foreground">{addr.country}</p>
                      </div>
                      {selectedAddressId === addr.id && (
                        <CheckCircle2 className="h-5 w-5 text-primary shrink-0" />
                      )}
                    </div>
                    {addr.default && (
                      <span className="text-xs text-primary font-medium mt-1 block">Default</span>
                    )}
                  </div>
                ))
              ) : (
                <p className="text-sm text-muted-foreground">No addresses saved. Add one below.</p>
              )}

              <Button variant="outline" size="sm" onClick={() => setAddAddressOpen(true)}>
                <Plus className="h-4 w-4 mr-2" /> Add new address
              </Button>
            </CardContent>
          </Card>

          {/* Order items preview */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Items in your order</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {items.map(item => (
                <div key={item.productId} className="flex justify-between text-sm">
                  <span className="text-muted-foreground">{item.name} ×{item.qty}</span>
                  <span className="font-medium">${item.subtotal.toFixed(2)}</span>
                </div>
              ))}
              <Separator />
              <div className="flex justify-between font-semibold">
                <span>Total</span>
                <span>${total.toFixed(2)}</span>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Right — confirm */}
        <div className="lg:col-span-1">
          <div className="border rounded-lg p-6 space-y-4 sticky top-24">
            <h2 className="font-semibold">Order Total</h2>
            <div className="flex justify-between text-2xl font-bold">
              <span>${total.toFixed(2)}</span>
            </div>
            <Separator />
            <p className="text-xs text-muted-foreground">
              By placing your order you agree to our terms. Payment is processed securely.
            </p>
            <Button
              className="w-full"
              size="lg"
              onClick={handlePlaceOrder}
              disabled={checkoutMutation.isPending || !selectedAddressId}
            >
              {checkoutMutation.isPending ? 'Placing order...' : 'Place Order'}
            </Button>
            <Button variant="outline" className="w-full" asChild>
              <Link to="/cart">Back to Cart</Link>
            </Button>
          </div>
        </div>
      </div>

      {/* Add address dialog */}
      <Dialog open={addAddressOpen} onOpenChange={setAddAddressOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add New Address</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleAddrSubmit(d => addAddressMutation.mutate(d))} className="space-y-3">
            <div className="space-y-1">
              <Label>Street Address</Label>
              <Input placeholder="123 Main St" {...registerAddr('line1')} />
              {addrErrors.line1 && <p className="text-xs text-destructive">{addrErrors.line1.message}</p>}
            </div>
            <div className="space-y-1">
              <Label>Apt / Suite (optional)</Label>
              <Input placeholder="Apt 4B" {...registerAddr('line2')} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label>City</Label>
                <Input placeholder="San Francisco" {...registerAddr('city')} />
                {addrErrors.city && <p className="text-xs text-destructive">{addrErrors.city.message}</p>}
              </div>
              <div className="space-y-1">
                <Label>State</Label>
                <Input placeholder="CA" {...registerAddr('state')} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label>ZIP</Label>
                <Input placeholder="94105" {...registerAddr('zip')} />
                {addrErrors.zip && <p className="text-xs text-destructive">{addrErrors.zip.message}</p>}
              </div>
              <div className="space-y-1">
                <Label>Country</Label>
                <Input placeholder="US" {...registerAddr('country')} />
                {addrErrors.country && <p className="text-xs text-destructive">{addrErrors.country.message}</p>}
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setAddAddressOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={addAddressMutation.isPending}>
                {addAddressMutation.isPending ? 'Saving...' : 'Save Address'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
