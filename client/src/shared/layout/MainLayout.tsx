import { NavLink, Outlet } from 'react-router-dom';

import { Header } from './Header';

const navigationItems = [
  { label: 'Labs', path: '/labs' },
  { label: 'Applications', path: '/applications' },
  { label: 'Booking', path: '/booking' },
  { label: 'Research', path: '/research' },
  { label: 'Profile', path: '/profile' },
];

export function MainLayout() {
  return (
    <div className="min-h-screen bg-slate-100 text-slate-900">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-slate-200 bg-white px-4 py-6 shadow-sm lg:block">
        <div className="px-3 text-lg font-semibold tracking-tight text-slate-950">
          Lab Portal
        </div>

        <nav className="mt-8 space-y-1">
          {navigationItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                [
                  'block rounded-md px-3 py-2 text-sm font-medium transition',
                  isActive
                    ? 'bg-slate-900 text-white'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950',
                ].join(' ')
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="lg:pl-64">
        <Header />

        <main className="px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
