import { useNavigate } from 'react-router-dom';

import { Button } from './Button';
import { getHomePath } from './errorNavigation';

export function ForbiddenPage() {
  const navigate = useNavigate();

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-10 text-slate-900">
      <section className="w-full max-w-lg rounded-lg border border-red-200 bg-white p-6 text-center shadow-sm">
        <h1 className="text-xl font-semibold text-red-700">403 - Không có quyền truy cập</h1>
        <p className="mt-2 text-sm text-slate-600">Bạn không có quyền truy cập trang này.</p>
        <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">
          <Button variant="outline" onClick={() => navigate(-1)}>
            Quay lại
          </Button>
          <Button onClick={() => navigate(getHomePath(), { replace: true })}>Về trang chính</Button>
        </div>
      </section>
    </main>
  );
}
