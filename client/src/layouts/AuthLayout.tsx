import { Outlet } from 'react-router-dom';

export function AuthLayout() {
  return (
    <main className="flex min-h-screen items-center justify-center overflow-x-hidden bg-slate-100 px-4 py-8 sm:px-6 sm:py-10">
      <div className="w-full max-w-[420px]">
        <Outlet />
      </div>
    </main>
  );
}
