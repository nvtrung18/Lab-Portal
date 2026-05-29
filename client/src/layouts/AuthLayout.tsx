import { Outlet } from 'react-router-dom';

export function AuthLayout() {
  return (
    <main className="flex min-h-screen items-center justify-center overflow-x-hidden bg-slate-100 px-4 py-8 sm:px-6 sm:py-10">
      <div className="min-w-0 w-[calc(100vw-2rem)] max-w-full sm:w-full sm:max-w-[420px]">
        <Outlet />
      </div>
    </main>
  );
}
