import { useState, useEffect } from 'react';
import { useAuthStore } from '../../stores/authStore';
import { apiFetch } from '../../utils/api';
import AdminReportModal from '../../components/AdminReportModal';

const FALLBACK_ASHA_IDS = [
  'asha_lata_001', 'asha_priya_002', 'asha_kavita_003',
  'asha_meena_004', 'asha_anita_005'
];

function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1, 0, 0, 0, 0);
}

function calcChange(current, previous) {
  if (previous === 0) return current > 0 ? { str: 'New', up: true } : { str: '—', up: true };
  const pct = ((current - previous) / previous * 100).toFixed(1);
  return { str: `${pct > 0 ? '+' : ''}${pct}%`, up: Number(pct) >= 0 };
}

export default function Reports() {
  const [metrics, setMetrics] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedMetric, setSelectedMetric] = useState(null);
  const [adminAshaIds, setAdminAshaIds] = useState(FALLBACK_ASHA_IDS);
  const { headId: storeHeadId } = useAuthStore();
  const headId = storeHeadId || localStorage.getItem('headId') || 'head_sunita_001';

  const now = new Date();
  const thisMonthStart = startOfMonth(now);
  const lastMonthStart = startOfMonth(new Date(now.getFullYear(), now.getMonth() - 1, 1));
  const monthLabel = now.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

  useEffect(() => {
    const load = async () => {
      try {
        const backendMetrics = await apiFetch('/api/admin/reports');
        
        const rows = backendMetrics.map(r => ({ ...r, ...calcChange(r.current, r.previous) }));

        setMetrics(rows);
      } catch (err) {
        console.error('[Reports] Load error:', err);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [headId]);

  const handleExportCSV = () => {
    const headers = ['Metric', 'This Month', 'Last Month', 'Change'];
    const rows = metrics.map(m => [m.name, m.current, m.previous, m.str]);
    const csv = [headers, ...rows].map(r => r.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ashaai_report_${new Date().toISOString().slice(0,7)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="p-4 md:p-8 relative">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl md:text-3xl font-bold">Reports</h1>
          <p className="text-[#5F5E5A] text-sm">{monthLabel} - Monthly Summary</p>
        </div>
        <button onClick={handleExportCSV} className="bg-[#085041] text-white px-4 py-2 rounded-xl font-medium flex items-center gap-2 self-start hover:bg-[#1D9E75] transition-colors">
          <span className="material-symbols-outlined text-lg">download</span> Export CSV
        </button>
      </div>

      {loading ? (
        <div className="bg-white rounded-2xl p-8 text-center animate-pulse text-[#5F5E5A]">Loading report data…</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full bg-white rounded-2xl shadow-sm border border-[#D3D1C7] overflow-hidden">
            <thead>
              <tr className="bg-[#085041] text-white text-sm">
                <th className="text-left px-4 py-3">Metric</th>
                <th className="text-center px-4 py-3">This Month</th>
                <th className="text-center px-4 py-3 hidden sm:table-cell">Last Month</th>
                <th className="text-center px-4 py-3">Change</th>
              </tr>
            </thead>
            <tbody>
              {metrics.map(m => (
                <tr 
                  key={m.name} 
                  className="border-t border-[#D3D1C7] hover:bg-[#EAF3DE] cursor-pointer transition-colors"
                  onClick={() => setSelectedMetric(m.name)}
                >
                  <td className="px-4 py-3 font-medium text-sm text-[#085041] underline decoration-1 underline-offset-2">{m.name}</td>
                  <td className="px-4 py-3 text-center font-bold">{m.current}</td>
                  <td className="px-4 py-3 text-center text-[#5F5E5A] hidden sm:table-cell">{m.previous}</td>
                  <td className="px-4 py-3 text-center">
                    <span className={`text-sm font-bold ${m.up ? 'text-[#1D9E75]' : 'text-[#E24B4A]'}`}>
                      {m.str}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="text-xs text-[#5F5E5A] mt-3">Click on any metric row to download a detailed PDF report across all workers.</p>
        </div>
      )}

      {selectedMetric && (
        <AdminReportModal
          isOpen={!!selectedMetric}
          onClose={() => setSelectedMetric(null)}
          metricName={selectedMetric}
          ashaIds={adminAshaIds}
        />
      )}
    </div>
  );
}
