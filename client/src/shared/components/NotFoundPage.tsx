import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <h1 className="text-xl font-semibold text-slate-950">404 Not Found</h1>
      <p className="mt-2 text-sm text-slate-600">Trang bạn tìm không tồn tại.</p>
      <Link className="mt-4 inline-flex text-sm font-medium text-slate-950 hover:underline" to="/app/profile">
        Quay lại ứng dụng
      </Link>
    </section>
  );
}
