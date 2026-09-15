import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Plus, Pencil, Trash2, ImageOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { productsApi } from '@/api/products'
import { toast } from '@/hooks/use-toast'
import type { ProductResponse } from '@/types'

const productSchema = z.object({
  name: z.string().min(1, 'Required').max(255),
  description: z.string().optional(),
  price: z.coerce.number().min(0, 'Must be ≥ 0'),
  categoryId: z.string().optional(),
  imageUrl: z.string().url('Must be a valid URL').optional().or(z.literal('')),
})
type ProductForm = z.infer<typeof productSchema>

export default function AdminProductsPage() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [dialog, setDialog] = useState<{ open: boolean; editing: ProductResponse | null }>({ open: false, editing: null })
  const [deleteTarget, setDeleteTarget] = useState<ProductResponse | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-products', page],
    queryFn: (): Promise<import('@/types').PageProductResponse> => productsApi.getProducts({ page, size: 20 }),
  })

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: productsApi.getCategories,
    staleTime: 5 * 60_000,
  })

  const { register, handleSubmit, reset, setValue, formState: { errors } } = useForm<ProductForm>({
    resolver: zodResolver(productSchema),
  })

  const openCreate = () => {
    reset({ name: '', description: '', price: 0, categoryId: '', imageUrl: '' })
    setDialog({ open: true, editing: null })
  }

  const openEdit = (p: ProductResponse) => {
    reset({ name: p.name, description: p.description ?? '', price: p.price, categoryId: p.categoryId ?? '', imageUrl: p.imageUrl ?? '' })
    setDialog({ open: true, editing: p })
  }

  const saveMutation = useMutation({
    mutationFn: (data: ProductForm) => {
      const payload = { ...data, categoryId: data.categoryId || undefined, imageUrl: data.imageUrl || undefined }
      return dialog.editing
        ? productsApi.updateProduct(dialog.editing.id, payload)
        : productsApi.createProduct(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-products'] })
      qc.invalidateQueries({ queryKey: ['products'] })
      setDialog({ open: false, editing: null })
      toast({ title: dialog.editing ? 'Product updated' : 'Product created' })
    },
    onError: () => toast({ variant: 'destructive', title: 'Could not save product' }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => productsApi.deleteProduct(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-products'] })
      qc.invalidateQueries({ queryKey: ['products'] })
      setDeleteTarget(null)
      toast({ title: 'Product deleted' })
    },
    onError: () => toast({ variant: 'destructive', title: 'Could not delete product' }),
  })

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Products</h1>
          {data && <p className="text-sm text-muted-foreground">{data.totalElements} total</p>}
        </div>
        <Button onClick={openCreate}><Plus className="h-4 w-4 mr-2" />New Product</Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {[1,2,3,4].map(i => <Skeleton key={i} className="h-16 w-full rounded-lg" />)}
        </div>
      ) : (
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted/50">
              <tr>
                <th className="text-left px-4 py-3 font-medium">Product</th>
                <th className="text-left px-4 py-3 font-medium hidden md:table-cell">Category</th>
                <th className="text-left px-4 py-3 font-medium">Price</th>
                <th className="text-left px-4 py-3 font-medium hidden sm:table-cell">Status</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y">
              {data?.content.map(product => (
                <tr key={product.id} className="hover:bg-muted/30 transition-colors">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="h-10 w-10 rounded bg-muted shrink-0 flex items-center justify-center overflow-hidden">
                        {product.imageUrl
                          ? <img src={product.imageUrl} alt="" className="object-cover h-full w-full" />
                          : <ImageOff className="h-4 w-4 text-muted-foreground/40" />
                        }
                      </div>
                      <span className="font-medium line-clamp-1">{product.name}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground hidden md:table-cell">
                    {categories?.find(c => c.id === product.categoryId)?.name ?? '—'}
                  </td>
                  <td className="px-4 py-3 font-medium">${product.price.toFixed(2)}</td>
                  <td className="px-4 py-3 hidden sm:table-cell">
                    {product.active
                      ? <Badge variant="success">Active</Badge>
                      : <Badge variant="secondary">Inactive</Badge>}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1">
                      <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => openEdit(product)}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive" onClick={() => setDeleteTarget(product)}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
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

      {/* Create / Edit dialog */}
      <Dialog open={dialog.open} onOpenChange={open => { if (!open) setDialog({ open: false, editing: null }) }}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{dialog.editing ? 'Edit Product' : 'New Product'}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit(d => saveMutation.mutate(d))} className="space-y-3">
            <div className="space-y-1">
              <Label>Name *</Label>
              <Input {...register('name')} />
              {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
            </div>
            <div className="space-y-1">
              <Label>Description</Label>
              <Input {...register('description')} placeholder="Optional description" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label>Price *</Label>
                <Input type="number" step="0.01" min="0" {...register('price')} />
                {errors.price && <p className="text-xs text-destructive">{errors.price.message}</p>}
              </div>
              <div className="space-y-1">
                <Label>Category</Label>
                <Select onValueChange={v => setValue('categoryId', v)} defaultValue={dialog.editing?.categoryId ?? ''}>
                  <SelectTrigger><SelectValue placeholder="Select..." /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="">No category</SelectItem>
                    {categories?.map(c => (
                      <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="space-y-1">
              <Label>Image URL</Label>
              <Input {...register('imageUrl')} placeholder="https://..." />
              {errors.imageUrl && <p className="text-xs text-destructive">{errors.imageUrl.message}</p>}
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialog({ open: false, editing: null })}>Cancel</Button>
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending ? 'Saving...' : dialog.editing ? 'Update' : 'Create'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete confirm */}
      <AlertDialog open={!!deleteTarget} onOpenChange={open => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete "{deleteTarget?.name}"?</AlertDialogTitle>
            <AlertDialogDescription>
              This soft-deletes the product — it will no longer appear in the catalog.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
