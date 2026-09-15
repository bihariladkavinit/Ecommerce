import { NavLink, Outlet } from 'react-router-dom'
import { LayoutDashboard, Package, Tag, Warehouse } from 'lucide-react'
import { cn } from '@/lib/utils'

const navItems = [
  { to: '/admin',           label: 'Dashboard',  icon: LayoutDashboard, end: true },
  { to: '/admin/products',  label: 'Products',   icon: Package,         end: false },
  { to: '/admin/categories',label: 'Categories', icon: Tag,             end: false },
  { to: '/admin/inventory', label: 'Inventory',  icon: Warehouse,       end: false },
]

export default function AdminLayout() {
  return (
    <div className="container py-8">
      <div className="flex flex-col md:flex-row gap-8">
        {/* Sidebar */}
        <aside className="w-full md:w-52 shrink-0">
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3 px-3">
            Admin Panel
          </p>
          <nav className="flex md:flex-col gap-1">
            {navItems.map(({ to, label, icon: Icon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-primary text-primary-foreground'
                      : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                  )
                }
              >
                <Icon className="h-4 w-4 shrink-0" />
                {label}
              </NavLink>
            ))}
          </nav>
        </aside>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
