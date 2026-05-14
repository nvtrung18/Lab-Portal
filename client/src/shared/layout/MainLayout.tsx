import { NavLink, Outlet } from 'react-router-dom';

const navigationItems = [
  { label: 'Labs', path: '/labs' },
  { label: 'Booking', path: '/booking' },
  { label: 'Research', path: '/research' },
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
        <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/95 px-4 py-4 shadow-sm backdrop-blur lg:px-8">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-xs font-medium uppercase text-slate-500">Dashboard</p>
              <h1 className="text-xl font-semibold text-slate-950">Lab Management</h1>
            </div>
            <div className="rounded-md border border-slate-200 px-3 py-2 text-sm text-slate-600">
              User
            </div>
          </div>
        </header>

        <main className="px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
