# RMS Frontend

React 19 + Vite + Tailwind CSS frontend for the Restaurant Management System.

## Setup

1. `npm install`
2. Copy `.env.example` to `.env.local` and adjust the API/WebSocket URLs if needed.
3. `npm run dev` - serves on http://localhost:5173

## Structure

- `src/api/` - Axios client (JWT auto-injected) + per-module API calls
- `src/context/AuthContext.jsx` - authenticated session state
- `src/hooks/useStompClient.js` - STOMP-over-WebSocket connection with auto-reconnect
- `src/hooks/useCart.js` - POS cart state (useReducer)
- `src/pages/` - LoginPage, PosPage, KitchenPage, ManagerPage
- `src/components/` - grouped by domain (pos/, kitchen/, tables/, common/)

## Default seed login

username: `admin` / password: `admin123` (see backend `schema.sql` - change immediately
in any non-development environment).
