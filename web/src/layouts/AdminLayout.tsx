import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { clearSession, getStaff } from '@/api/client'

export default function AdminLayout() {
  const navigate = useNavigate()
  const staff = getStaff()

  function logout() {
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="brand">
          <span className="brand__name">夏晨衣舍</span>
          <span className="brand__sub">管理后台</span>
        </div>
        <nav className="nav">
          <NavLink to="/stores" className={({ isActive }) => (isActive ? 'nav__item nav__item--active' : 'nav__item')}>
            门店
          </NavLink>
          <NavLink to="/home-themes" className={({ isActive }) => (isActive ? 'nav__item nav__item--active' : 'nav__item')}>
            首页主题
          </NavLink>
          <NavLink to="/products" className={({ isActive }) => (isActive ? 'nav__item nav__item--active' : 'nav__item')}>
            商品维护
          </NavLink>
          <NavLink to="/inventory" className={({ isActive }) => (isActive ? 'nav__item nav__item--active' : 'nav__item')}>
            库存维护
          </NavLink>
          <NavLink to="/collections" className={({ isActive }) => (isActive ? 'nav__item nav__item--active' : 'nav__item')}>
            系列维护
          </NavLink>
          <NavLink to="/looks" className={({ isActive }) => (isActive ? 'nav__item nav__item--active' : 'nav__item')}>
            造型维护
          </NavLink>
          <NavLink to="/appointments" className={({ isActive }) => (isActive ? 'nav__item nav__item--active' : 'nav__item')}>
            预约看板
          </NavLink>
        </nav>
        <div className="sidebar-foot">
          <span>{staff ? `${staff.displayName}（${staff.username}）` : '未登录'}</span>
          <button type="button" className="link-button" onClick={logout}>
            退出
          </button>
        </div>
      </aside>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
