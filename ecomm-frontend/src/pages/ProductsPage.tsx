import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Search, SlidersHorizontal, X } from 'lucide-react'
import { useState, useEffect } from 'react'
import { productsApi } from '@/api/products'
import { ProductCard } from '@/components/shared/ProductCard'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'

function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(t)
  }, [value, delay])
  return debounced
}

export default function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [searchInput, setSearchInput] = useState(searchParams.get('search') || '')
  const debouncedSearch = useDebounce(searchInput, 300)

  const currentCategory = searchParams.get('category') || ''
  const currentPage = parseInt(searchParams.get('page') || '0', 10)

  // Sync debounced search into URL
  useEffect(() => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      if (debouncedSearch) { next.set('search', debouncedSearch) } else { next.delete('search') }
      next.delete('page')
      return next
    }, { replace: true })
  }, [debouncedSearch, setSearchParams])

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: productsApi.getCategories,
    staleTime: 5 * 60_000,
  })

  const { data, isLoading } = useQuery({
    queryKey: ['products', { category: currentCategory, search: debouncedSearch, page: currentPage }],
    queryFn: (): Promise<import('@/types').PageProductResponse> => productsApi.getProducts({
      category: currentCategory || undefined,
      search: debouncedSearch || undefined,
      page: currentPage,
      size: 12,
    }),
  })

  const setCategory = (id: string) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      if (id) { next.set('category', id) } else { next.delete('category') }
      next.delete('page')
      return next
    })
  }

  const setPage = (page: number) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      if (page > 0) { next.set('page', String(page)) } else { next.delete('page') }
      return next
    })
  }

  const clearFilters = () => {
    setSearchInput('')
    setSearchParams({})
  }

  const hasFilters = !!(currentCategory || debouncedSearch)

  return (
    <div className="container py-8">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold">Products</h1>
          {data && (
            <p className="text-sm text-muted-foreground mt-1">
              {data.totalElements} product{data.totalElements !== 1 ? 's' : ''} found
            </p>
          )}
        </div>

        {/* Search */}
        <div className="relative w-full md:w-80">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search products..."
            className="pl-9 pr-9"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
          {searchInput && (
            <button
              className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              onClick={() => setSearchInput('')}
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>

      {/* Category filters */}
      <div className="flex flex-wrap items-center gap-2 mb-6">
        <SlidersHorizontal className="h-4 w-4 text-muted-foreground" />
        <Button
          variant={!currentCategory ? 'default' : 'outline'}
          size="sm"
          onClick={() => setCategory('')}
        >
          All
        </Button>
        {categories?.map(cat => (
          <Button
            key={cat.id}
            variant={currentCategory === cat.id ? 'default' : 'outline'}
            size="sm"
            onClick={() => setCategory(cat.id)}
          >
            {cat.name}
          </Button>
        ))}
        {hasFilters && (
          <Button variant="ghost" size="sm" onClick={clearFilters} className="text-destructive hover:text-destructive">
            <X className="h-3 w-3 mr-1" /> Clear filters
          </Button>
        )}
      </div>

      {/* Active filter badges */}
      {hasFilters && (
        <div className="flex flex-wrap gap-2 mb-4">
          {debouncedSearch && <Badge variant="secondary">Search: "{debouncedSearch}"</Badge>}
          {currentCategory && (
            <Badge variant="secondary">
              Category: {categories?.find(c => c.id === currentCategory)?.name ?? currentCategory}
            </Badge>
          )}
        </div>
      )}

      {/* Grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
          {Array.from({ length: 12 }).map((_, i) => (
            <div key={i} className="space-y-3">
              <Skeleton className="aspect-square w-full rounded-lg" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-9 w-full" />
            </div>
          ))}
        </div>
      ) : data?.empty ? (
        <div className="text-center py-20 text-muted-foreground">
          <p className="text-lg">No products found</p>
          <p className="text-sm mt-2">Try adjusting your search or category filter</p>
          <Button variant="outline" className="mt-4" onClick={clearFilters}>Clear filters</Button>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
            {data?.content.map(product => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 mt-10">
              <Button
                variant="outline"
                size="sm"
                disabled={data.first}
                onClick={() => setPage(currentPage - 1)}
              >
                Previous
              </Button>
              {Array.from({ length: data.totalPages }, (_, i) => i).map(page => (
                <Button
                  key={page}
                  variant={page === currentPage ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => setPage(page)}
                >
                  {page + 1}
                </Button>
              ))}
              <Button
                variant="outline"
                size="sm"
                disabled={data.last}
                onClick={() => setPage(currentPage + 1)}
              >
                Next
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
