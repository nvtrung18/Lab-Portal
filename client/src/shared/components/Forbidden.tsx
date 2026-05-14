export function Forbidden() {
  return (
    <section className="rounded-lg border border-red-200 bg-white p-6 text-red-700 shadow-sm">
      <h2 className="text-xl font-semibold">403 Forbidden</h2>
      <p className="mt-2 text-sm">Bạn không có quyền truy cập trang này.</p>
    </section>
  );
}
