import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { User, MapPin, Plus, Pencil, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { usersApi } from '@/api/users'
import { useAuthStore } from '@/stores/authStore'
import { toast } from '@/hooks/use-toast'
import type { AddressResponse } from '@/types'

const profileSchema = z.object({
  firstName: z.string().min(1, 'Required').max(100),
  lastName: z.string().min(1, 'Required').max(100),
  phone: z.string().max(30).optional(),
})
type ProfileForm = z.infer<typeof profileSchema>

const addressSchema = z.object({
  line1: z.string().min(1, 'Required'),
  line2: z.string().optional(),
  city: z.string().min(1, 'Required'),
  state: z.string().optional(),
  zip: z.string().min(1, 'Required'),
  country: z.string().min(1, 'Required'),
  default: z.boolean().optional(),
})
type AddressForm = z.infer<typeof addressSchema>

export default function ProfilePage() {
  const qc = useQueryClient()
  const { setUser } = useAuthStore()
  const [addrDialog, setAddrDialog] = useState<{ open: boolean; editing: AddressResponse | null }>({ open: false, editing: null })
  const [deleteAddr, setDeleteAddr] = useState<AddressResponse | null>(null)

  const { data: user, isLoading: loadingUser } = useQuery({ queryKey: ['me'], queryFn: usersApi.getMe })
  const { data: addresses, isLoading: loadingAddrs } = useQuery({ queryKey: ['addresses'], queryFn: usersApi.getAddresses })

  const { register: regProfile, handleSubmit: handleProfile, formState: { errors: profileErrors } } = useForm<ProfileForm>({
    resolver: zodResolver(profileSchema),
    values: user ? { firstName: user.firstName, lastName: user.lastName, phone: user.phone ?? '' } : undefined,
  })

  const { register: regAddr, handleSubmit: handleAddr, reset: resetAddr, formState: { errors: addrErrors } } = useForm<AddressForm>({
    resolver: zodResolver(addressSchema),
  })

  const updateProfileMutation = useMutation({
    mutationFn: usersApi.updateMe,
    onSuccess: (updated) => {
      setUser(updated)
      qc.setQueryData(['me'], updated)
      toast({ title: 'Profile updated' })
    },
    onError: () => toast({ variant: 'destructive', title: 'Could not update profile' }),
  })

  const saveAddressMutation = useMutation({
    mutationFn: (data: AddressForm) =>
      addrDialog.editing
        ? usersApi.updateAddress(addrDialog.editing.id, data)
        : usersApi.addAddress(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['addresses'] })
      setAddrDialog({ open: false, editing: null })
      resetAddr()
      toast({ title: addrDialog.editing ? 'Address updated' : 'Address added' })
    },
    onError: () => toast({ variant: 'destructive', title: 'Could not save address' }),
  })

  const deleteAddressMutation = useMutation({
    mutationFn: (id: string) => usersApi.deleteAddress(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['addresses'] })
      setDeleteAddr(null)
      toast({ title: 'Address deleted' })
    },
    onError: () => toast({ variant: 'destructive', title: 'Could not delete address' }),
  })

  const openEditAddr = (addr: AddressResponse) => {
    resetAddr({ line1: addr.line1, line2: addr.line2 ?? '', city: addr.city, state: addr.state ?? '', zip: addr.zip, country: addr.country, default: addr.default })
    setAddrDialog({ open: true, editing: addr })
  }

  const openNewAddr = () => {
    resetAddr({})
    setAddrDialog({ open: true, editing: null })
  }

  return (
    <div className="container py-8 max-w-3xl space-y-8">
      <h1 className="text-2xl font-bold">Profile & Addresses</h1>

      {/* Profile */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <User className="h-5 w-5" /> Personal Information
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loadingUser ? (
            <div className="space-y-3"><Skeleton className="h-10 w-full" /><Skeleton className="h-10 w-full" /></div>
          ) : (
            <form onSubmit={handleProfile(d => updateProfileMutation.mutate(d))} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1">
                  <Label>First name</Label>
                  <Input {...regProfile('firstName')} />
                  {profileErrors.firstName && <p className="text-xs text-destructive">{profileErrors.firstName.message}</p>}
                </div>
                <div className="space-y-1">
                  <Label>Last name</Label>
                  <Input {...regProfile('lastName')} />
                  {profileErrors.lastName && <p className="text-xs text-destructive">{profileErrors.lastName.message}</p>}
                </div>
              </div>
              <div className="space-y-1">
                <Label>Phone (optional)</Label>
                <Input placeholder="+1 555 000 0000" {...regProfile('phone')} />
              </div>
              <div className="space-y-1">
                <Label>Email</Label>
                <Input value={user?.email ?? ''} disabled className="bg-muted" />
                <p className="text-xs text-muted-foreground">Email cannot be changed</p>
              </div>
              <Button type="submit" disabled={updateProfileMutation.isPending}>
                {updateProfileMutation.isPending ? 'Saving...' : 'Save Changes'}
              </Button>
            </form>
          )}
        </CardContent>
      </Card>

      {/* Addresses */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-base">
            <MapPin className="h-5 w-5" /> Saved Addresses
          </CardTitle>
          <Button size="sm" variant="outline" onClick={openNewAddr}>
            <Plus className="h-4 w-4 mr-1" /> Add Address
          </Button>
        </CardHeader>
        <CardContent>
          {loadingAddrs ? (
            <div className="space-y-3">{[1,2].map(i => <Skeleton key={i} className="h-20 w-full rounded-lg" />)}</div>
          ) : !addresses?.length ? (
            <p className="text-sm text-muted-foreground">No addresses saved yet.</p>
          ) : (
            <div className="space-y-3">
              {addresses.map((addr, idx) => (
                <div key={addr.id}>
                  {idx > 0 && <Separator />}
                  <div className="flex items-start justify-between pt-3 first:pt-0">
                    <div className="text-sm space-y-0.5">
                      <div className="flex items-center gap-2">
                        <p className="font-medium">{addr.line1}{addr.line2 ? `, ${addr.line2}` : ''}</p>
                        {addr.default && <Badge variant="secondary" className="text-xs">Default</Badge>}
                      </div>
                      <p className="text-muted-foreground">{addr.city}{addr.state ? `, ${addr.state}` : ''} {addr.zip}</p>
                      <p className="text-muted-foreground">{addr.country}</p>
                    </div>
                    <div className="flex items-center gap-1 shrink-0">
                      <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => openEditAddr(addr)}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8 text-destructive hover:text-destructive"
                        onClick={() => setDeleteAddr(addr)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Address dialog */}
      <Dialog open={addrDialog.open} onOpenChange={open => { if (!open) setAddrDialog({ open: false, editing: null }) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{addrDialog.editing ? 'Edit Address' : 'New Address'}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleAddr(d => saveAddressMutation.mutate(d))} className="space-y-3">
            <div className="space-y-1"><Label>Street Address</Label><Input {...regAddr('line1')} />{addrErrors.line1 && <p className="text-xs text-destructive">{addrErrors.line1.message}</p>}</div>
            <div className="space-y-1"><Label>Apt / Suite (optional)</Label><Input {...regAddr('line2')} /></div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1"><Label>City</Label><Input {...regAddr('city')} />{addrErrors.city && <p className="text-xs text-destructive">{addrErrors.city.message}</p>}</div>
              <div className="space-y-1"><Label>State</Label><Input {...regAddr('state')} /></div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1"><Label>ZIP</Label><Input {...regAddr('zip')} />{addrErrors.zip && <p className="text-xs text-destructive">{addrErrors.zip.message}</p>}</div>
              <div className="space-y-1"><Label>Country</Label><Input {...regAddr('country')} />{addrErrors.country && <p className="text-xs text-destructive">{addrErrors.country.message}</p>}</div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setAddrDialog({ open: false, editing: null })}>Cancel</Button>
              <Button type="submit" disabled={saveAddressMutation.isPending}>
                {saveAddressMutation.isPending ? 'Saving...' : 'Save'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete confirm */}
      <AlertDialog open={!!deleteAddr} onOpenChange={open => { if (!open) setDeleteAddr(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete address?</AlertDialogTitle>
            <AlertDialogDescription>
              {deleteAddr?.line1}, {deleteAddr?.city} will be permanently removed.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => deleteAddr && deleteAddressMutation.mutate(deleteAddr.id)}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
