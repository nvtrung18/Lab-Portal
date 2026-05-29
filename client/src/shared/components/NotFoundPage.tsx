import { useNavigate } from 'react-router-dom';

import { Button } from './Button';
import { getHomePath } from './errorNavigation';

export function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-10 text-slate-900">
      <section className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-6 text-center shadow-sm">
        <h1 className="text-xl font-semibold text-slate-950">404 - Không tìm thấy trang</h1>
        <p className="mt-2 text-sm text-slate-600">
          Trang bạn đang truy cập không tồn tại hoặc đã bị thay đổi.
        </p>
        <div className="mt-6">
          <Button onClick={() => navigate(getHomePath(), { replace: true })}>Quay về trang chủ</Button>
        </div>
      </section>
    </main>
  );
}
