import type { ReactElement } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { getToken } from './api/client'
import AdminLayout from './layouts/AdminLayout'
import LoginPage from './pages/login/LoginPage'
import ProductListPage from './pages/products/ProductListPage'
import ProductEditorPage from './pages/products/ProductEditorPage'
import InventoryPage from './pages/inventory/InventoryPage'
import StoresPage from './pages/stores/StoresPage'
import AppointmentsPage from './pages/appointments/AppointmentsPage'
import LooksPage from './pages/looks/LooksPage'
import LookEditorPage from './pages/looks/LookEditorPage'
import HomeThemesPage from './pages/home-themes/HomeThemesPage'
import HomeThemeEditorPage from './pages/home-themes/HomeThemeEditorPage'

function RequireAuth({ children }: { children: ReactElement }) {
  return getToken() ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <RequireAuth>
              <AdminLayout />
            </RequireAuth>
          }
        >
          <Route path="/" element={<Navigate to="/products" replace />} />
          <Route path="/products" element={<ProductListPage />} />
          <Route path="/products/new" element={<ProductEditorPage />} />
          <Route path="/products/:id" element={<ProductEditorPage />} />
          <Route path="/inventory" element={<InventoryPage />} />
          <Route path="/stores" element={<StoresPage />} />
          <Route path="/home-themes" element={<HomeThemesPage />} />
          <Route path="/home-themes/new" element={<HomeThemeEditorPage />} />
          <Route path="/home-themes/:id" element={<HomeThemeEditorPage />} />
          <Route path="/appointments" element={<AppointmentsPage />} />
          <Route path="/looks" element={<LooksPage />} />
          <Route path="/looks/new" element={<LookEditorPage />} />
          <Route path="/looks/:id" element={<LookEditorPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/products" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
