import React, { useEffect, useState } from "react";
import { Line } from "react-chartjs-2";
import { Chart as ChartJS, CategoryScale, LinearScale, LineElement, PointElement, Tooltip, Legend } from "chart.js";
import { PageShell } from "../../components/PageShell";
import { StatCard } from "../../components/StatCard";
import { api } from "../../services/api";
import { AdminStats } from "../../types";

ChartJS.register(CategoryScale, LinearScale, LineElement, PointElement, Tooltip, Legend);

export function AdminDashboardPage() {
  const [stats, setStats] = useState<AdminStats | null>(null);

  useEffect(() => {
    void api.getAdminStats().then(setStats);
  }, []);

  const lineData = {
    labels: stats?.trend.map((point) => point.hour) ?? [],
    datasets: [
      {
        label: "Success",
        data: stats?.trend.map((point) => point.success) ?? [],
        borderColor: "#0ea5e9",
        backgroundColor: "rgba(14, 165, 233, 0.3)",
      },
      {
        label: "Failure",
        data: stats?.trend.map((point) => point.failure) ?? [],
        borderColor: "#ef4444",
        backgroundColor: "rgba(239, 68, 68, 0.3)",
      },
    ],
  };

  return (
    <PageShell mode="admin" title="Admin Dashboard" subtitle="Số liệu hoạt động IdP trong 24 giờ gần nhất.">
      <div className="card-grid four-col">
        <StatCard label="Tổng user" value={stats?.totalUsers ?? "--"} />
        <StatCard label="OAuth clients" value={stats?.totalClients ?? "--"} />
        <StatCard label="Session active" value={stats?.activeSessions ?? "--"} />
        <StatCard label="Login success / fail" value={`${stats?.loginSuccess24h ?? 0} / ${stats?.loginFailure24h ?? 0}`} />
      </div>
      <section className="panel">
        <h2>Biểu đồ đăng nhập</h2>
        <Line data={lineData} />
      </section>
    </PageShell>
  );
}
