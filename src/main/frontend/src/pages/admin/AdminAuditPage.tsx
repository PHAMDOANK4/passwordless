import React, { useEffect, useState } from "react";
import { PageShell } from "../../components/PageShell";
import { toLocalTime } from "../../lib/format";
import { api } from "../../services/api";
import { AuditLog } from "../../types";

export function AdminAuditPage() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [action, setAction] = useState("");
  const [user, setUser] = useState("");

  const load = async () => {
    const data = await api.listAuditLogs({ action, user });
    setLogs(data);
  };

  useEffect(() => {
    void load();
  }, [action, user]);

  const exportAs = (format: "csv" | "json") => {
    if (format === "json") {
      const blob = new Blob([JSON.stringify(logs, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "audit-logs.json";
      link.click();
      URL.revokeObjectURL(url);
      return;
    }

    const header = "time,user,clientId,action,ip,result";
    const rows = logs.map((log) => [log.time, log.user ?? "", log.clientId ?? "", log.action, log.ip, log.result].join(","));
    const blob = new Blob([[header, ...rows].join("\n")], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "audit-logs.csv";
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <PageShell mode="admin" title="Audit Logs" subtitle="Lọc theo user/action và export CSV hoặc JSON.">
      <section className="panel">
        <div className="inline-form">
          <input placeholder="Filter action" value={action} onChange={(e) => setAction(e.target.value)} />
          <input placeholder="Filter user" value={user} onChange={(e) => setUser(e.target.value)} />
          <button type="button" onClick={() => exportAs("csv")}>Export CSV</button>
          <button type="button" onClick={() => exportAs("json")}>Export JSON</button>
        </div>
      </section>

      <section className="panel">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Thời gian</th>
                <th>User</th>
                <th>Client ID</th>
                <th>Action</th>
                <th>IP</th>
                <th>Result</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id}>
                  <td>{toLocalTime(log.time)}</td>
                  <td>{log.user ?? "-"}</td>
                  <td>{log.clientId ?? "-"}</td>
                  <td>{log.action}</td>
                  <td>{log.ip}</td>
                  <td>{log.result}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </PageShell>
  );
}
