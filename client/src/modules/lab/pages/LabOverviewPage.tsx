export function LabOverviewPage() {
  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
          Lab Manager
        </p>
        <h2 className="mt-1 text-xl font-semibold text-slate-950">Lab Overview</h2>
        <p className="mt-2 text-sm text-slate-600">
          Quan ly thong tin tong quan cua lab. API tao va cap nhat lab se duoc ket
          noi trong task rieng.
        </p>
      </div>

      <form className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="grid gap-5 md:grid-cols-2">
          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700">Ten lab</span>
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              placeholder="VD: AI Research Lab"
              type="text"
            />
          </label>

          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700">Dia diem</span>
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              placeholder="VD: Toa A, phong 501"
              type="text"
            />
          </label>

          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700">Khoa / don vi</span>
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              placeholder="VD: Computer Science"
              type="text"
            />
          </label>

          <label className="space-y-2">
            <span className="text-sm font-medium text-slate-700">Suc chua</span>
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              min={1}
              placeholder="VD: 30"
              type="number"
            />
          </label>
        </div>

        <label className="mt-5 block space-y-2">
          <span className="text-sm font-medium text-slate-700">Mo ta lab</span>
          <textarea
            className="min-h-32 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
            placeholder="Mo ta linh vuc nghien cuu, thiet bi, dieu kien tham gia..."
          />
        </label>

        <div className="mt-6 flex justify-end">
          <button
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-700"
            type="button"
          >
            Luu thong tin lab
          </button>
        </div>
      </form>
    </section>
  );
}
