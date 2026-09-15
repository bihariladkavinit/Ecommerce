# Ecomm POC — React Frontend

Full-featured e-commerce frontend built with **Vite + React + TypeScript + shadcn/ui + Tailwind CSS**.

## Prerequisites

All backend services must be running and accessible via the API Gateway at `http://localhost:8080`.

Start the backend:
```bash
# From the project root
docker compose up -d
```

## Setup & Run

```bash
cd ecomm-frontend
npm install
npm run dev
```

The app starts at **http://localhost:5173**.

All API calls are proxied through Vite's dev server to `http://localhost:8080` — no CORS configuration needed.

## Build

```bash
npm run build        # outputs to dist/
npm run preview      # preview the production build locally
```

## Features

### Customer
| Page | Route |
|---|---|
| Home | `/` |
| Product listing (search + filter) | `/products` |
| Product detail + add to cart | `/products/:id` |
| Shopping cart | `/cart` |
| Checkout (address select) | `/checkout` |
| Order history | `/orders` |
| Order detail + live saga tracking | `/orders/:id` |
| Profile + address book | `/profile` |
| Public shipment tracking | `/track/:trackingNumber` |

### Admin (requires ROLE_ADMIN)
| Page | Route |
|---|---|
| Dashboard | `/admin` |
| Product CRUD | `/admin/products` |
| Category management | `/admin/categories` |
| Inventory restock | `/admin/inventory` |

## Tech stack

- **Vite** — build tool
- **React 18** + **TypeScript**
- **React Router v6** — client-side routing
- **TanStack Query** — server state, caching, background refetch
- **Zustand** — client state (auth tokens, cart)
- **Axios** — HTTP client with JWT interceptors and auto-refresh
- **shadcn/ui** + **Tailwind CSS** — UI components
- **react-hook-form** + **zod** — form validation
- **date-fns** — date formatting
- **lucide-react** — icons
