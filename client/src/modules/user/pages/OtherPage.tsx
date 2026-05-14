const otherItems = ['My Cleaning Tasks', 'My Lab Info', 'My Membership'];

export function OtherPage() {
  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
          Student Lab Member
        </p>
        <h2 className="mt-1 text-xl font-semibold text-slate-950">Other</h2>
        <p className="mt-2 text-sm text-slate-600">
          Khu vuc chi hien thi khi student da co membership ACTIVE trong lab.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {otherItems.map((item) => (
          <div
            key={item}
            className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
          >
            <h3 className="text-sm font-semibold text-slate-950">{item}</h3>
            <p className="mt-2 text-sm text-slate-600">Placeholder.</p>
          </div>
        ))}
      </div>
    </section>
  );
}
