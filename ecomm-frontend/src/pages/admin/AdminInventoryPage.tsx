import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { productsApi } from '@/api/products'
import { inventoryApi } from '@/api/inventory'
import { toast } from '@/hooks/use-toast'
import type { ProductResponse } from '@/types'

const restockSchema = z.object({
  quantity: z.coerce.number().int().min(1, 'Must be at least 1'),
})
type RestockForm = z.infer<typeof restockSchema>

function StockCell({ productId }: { productId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ['stock', productId],
    queryFn: () => inventoryApi.getStock(productId),
    retry: false,
    staleTime: 30_000,
  })

  if (isLoading) return <Skeleton className="h-5 w-20" />
  if (!data) return <span className="text-muted-foreground text-xs">No record</span>

  return (
    <div className="flex items-center gap-2 text-sm">
      <span className="font-medium">{data.availableQty}</span>
      <span className="text-muted-foreground">avail</span>
      {data.reservedQty > 0 && (
        <Badge variant="warning" className="text-xs">{data.reservedQty} reserved</Badge>
      )}
    </div>
  )
}

export default function AdminInventoryPage() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [restockTarget, setRestockTarget] = useState<ProductResponse | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-products', page],
    queryFn: (): Promise<import('@/types').PageProductResponse> => productsApi.getProducts({ page, size: 20 }),
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<RestockForm>({
    resolver: zodResolver(restockSchema),
    defaultValues: { quantity: 10 },
  })

  const restockMutation = useMutation({
    mutationFn: ({ productId, quantity }: { productId: string; quantity: number }) =>
      inventoryApi.restock(productId, { quantity }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['stock', vars.productId] })
      setRestockTarget(null)
      reset({ quantity: 10 })
      toast({ title: 'Stock updated', description: `Added ${vars.quantity} units` })
    },
    onError: () => toast({ variant: 'destructive', title: 'Could not restock' }),
  })

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold">Inventory</h1>
        <p className="text-sm text-muted-foreground mt-1">View stock levels and restock products</p>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {[1,2,3,4].map(i => <Skeleton key={i} className="h-14 w-full rounded-lg" />)}
        </div>
      ) : (
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted/50">
              <tr>
                <th className="text-left px-4 py-3 font-medium">Product</th>
                <th className="text-left px-4 py-3 font-medium">Stock</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y">
              {data?.content.map(product => (
                <tr key={product.id} className="hover:bg-muted/30 transition-colors">
                  <td className="px-4 py-3">
                    <div>
                      <p className="font-medium line-clamp-1">{product.name}</p>
                      <p className="text-xs text-muted-foreground">${product.price.toFixed(2)}</p>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <StockCell productId={product.id} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => { reset({ quantity: 10 }); setRestockTarget(product) }}
                    >
                      <RefreshCw className="h-3 w-3 mr-1" /> Restock
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" disabled={data.first} onClick={() => setPage(p => p - 1)}>Previous</Button>
          <span className="text-sm text-muted-foreground">Page {page + 1} of {data.totalPages}</span>
          <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage(p => p + 1)}>Next</Button>
        </div>
      )}

      <Dialog open={!!restockTarget} onOpenChange={open => { if (!open) { setRestockTarget(null); reset({ quantity: 10 }) } }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Restock: {restockTarget?.name}</DialogTitle>
          </DialogHeader>
          <form
            onSubmit={handleSubmit(({ quantity }) =>
              restockTarget && restockMutation.mutate({ productId: restockTarget.id, quantity })
            )}
            className="space-y-4"
          >
            <div className="space-y-1">
              <Label>Quantity to add *</Label>
              <Input type="number" min="1" {...register('quantity')} />
              {errors.quantity && <p className="text-xs text-destructive">{errors.quantity.message}</p>}
              <p className="text-xs text-muted-foreground">
                This amount will be <strong>added</strong> to the current available stock.
              </p>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setRestockTarget(null); reset({ quantity: 10 }) }}>
                Cancel
              </Button>
              <Button type="submit" disabled={restockMutation.isPending}>
                {restockMutation.isPending ? 'Updating...' : 'Add Stock'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
