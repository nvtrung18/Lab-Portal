export function CleaningPage() {
  const statuses = ['PENDING', 'ASSIGNED', 'DONE'];

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
          Lab Manager
        </p>
        <h2 className="mt-1 text-xl font-semibold text-slate-950">Cleaning</h2>
        <p className="mt-2 text-sm text-slate-600">
          Placeholder cho man hinh quan ly va phan cong ve sinh lab.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {statuses.map((status) => (
          <div
            key={status}
            className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
          >
            <p className="text-sm font-semibold text-slate-950">{status}</p>
            <p className="mt-2 text-sm text-slate-600">
              Danh sach task trang thai {status.toLowerCase()} se duoc hien thi tai
              day.
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}
