import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { productsApi } from '@/api/products'
import { toast } from '@/hooks/use-toast'

const schema = z.object({
  name: z.string().min(1, 'Name required').max(150),
  parentId: z.string().optional(),
})
type CategoryForm = z.infer<typeof schema>

export default function AdminCategoriesPage() {
  const qc = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)

  const { data: categories, isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: productsApi.getCategories,
    staleTime: 5 * 60_000,
  })

  const { register, handleSubmit, reset, setValue, formState: { errors } } = useForm<CategoryForm>({
    resolver: zodResolver(schema),
  })

  const createMutation = useMutation({
    mutationFn: (data: CategoryForm) =>
      productsApi.createCategory({ ...data, parentId: data.parentId || undefined }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] })
      setDialogOpen(false)
      reset()
      toast({ title: 'Category created' })
    },
    onError: () => toast({ variant: 'destructive', title: 'Could not create category' }),
  })

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Categories</h1>
          {categories && <p className="text-sm text-muted-foreground">{categories.length} total</p>}
        </div>
        <Button onClick={() => { reset(); setDialogOpen(true) }}>
          <Plus className="h-4 w-4 mr-2" />New Category
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {[1,2,3].map(i => <Skeleton key={i} className="h-12 w-full rounded-lg" />)}
        </div>
      ) : !categories?.length ? (
        <p className="text-sm text-muted-foreground py-8 text-center">
          No categories yet. Create the first one.
        </p>
      ) : (
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted/50">
              <tr>
                <th className="text-left px-4 py-3 font-medium">Name</th>
                <th className="text-left px-4 py-3 font-medium">Parent</th>
                <th className="text-left px-4 py-3 font-medium hidden sm:table-cell">ID</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {categories.map(cat => (
                <tr key={cat.id} className="hover:bg-muted/30 transition-colors">
                  <td className="px-4 py-3 font-medium">{cat.name}</td>
                  <td className="px-4 py-3 text-muted-foreground">
                    {cat.parentId
                      ? categories.find(c => c.id === cat.parentId)?.name ?? cat.parentId.slice(0, 8) + '...'
                      : '—'}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-muted-foreground hidden sm:table-cell">
                    {cat.id.slice(0, 8)}...
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Dialog open={dialogOpen} onOpenChange={open => { if (!open) { setDialogOpen(false); reset() } }}>
        <DialogContent>
          <DialogHeader><DialogTitle>New Category</DialogTitle></DialogHeader>
          <form onSubmit={handleSubmit(d => createMutation.mutate(d))} className="space-y-4">
            <div className="space-y-1">
              <Label>Name *</Label>
              <Input placeholder="e.g. Electronics" {...register('name')} />
              {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
            </div>
            <div className="space-y-1">
              <Label>Parent Category (optional)</Label>
              <Select onValueChange={v => setValue('parentId', v === 'none' ? '' : v)}>
                <SelectTrigger><SelectValue placeholder="Top-level (no parent)" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">No parent</SelectItem>
                  {categories?.map(c => (
                    <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setDialogOpen(false); reset() }}>Cancel</Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending ? 'Creating...' : 'Create'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
