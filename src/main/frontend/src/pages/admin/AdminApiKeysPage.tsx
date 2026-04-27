import React, { useEffect, useState } from "react";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { maskSecret, toLocalTime } from "../../lib/format";
import { api } from "../../services/api";
import { ApiKeyItem } from "../../types";

export function AdminApiKeysPage() {
  const { addToast } = useAppContext();
  const [items, setItems] = useState<ApiKeyItem[]>([]);
  const [visibleKey, setVisibleKey] = useState<string | null>(null);
  const [form, setForm] = useState({ appName: "", description: "", rateLimitPerMinute: 60 });

  const load = async () => {
    setItems(await api.listApiKeys());
  };

  useEffect(() => {
    void load();
  }, []);

  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    const result = await api.createApiKey(form);
    setVisibleKey(result.apiKey);
    addToast("success", "Đã tạo API key mới. Hãy lưu key ngay.");
    setForm({ appName: "", description: "", rateLimitPerMinute: 60 });
    await load();
  };

  const revoke = async (id: string) => {
    await api.revokeApiKey(id);
    addToast("info", "Đã thu hồi API key.");
    await load();
  };

  return (
    <PageShell mode="admin" title="API Key Management" subtitle="Tạo và thu hồi API key cho server-to-server.">
      <section className="panel">
        <h2>Tạo API key</h2>
        <form className="inline-form" onSubmit={create}>
          <input placeholder="Tên app" value={form.appName} onChange={(e) => setForm({ ...form, appName: e.target.value })} required />
          <input placeholder="Mô tả" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          <input
            type="number"
            value={form.rateLimitPerMinute}
            onChange={(e) => setForm({ ...form, rateLimitPerMinute: Number(e.target.value) })}
          />
          <button type="submit">Tạo key</button>
        </form>
        {visibleKey ? <p className="danger-text">API Key (one-time): {maskSecret(visibleKey)}</p> : null}
      </section>

      <section className="panel">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>App</th>
                <th>Description</th>
                <th>Rate limit</th>
                <th>Active</th>
                <th>Created</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  <td>{item.appName}</td>
                  <td>{item.description}</td>
                  <td>{item.rateLimitPerMinute}</td>
                  <td>{item.active ? "Yes" : "No"}</td>
                  <td>{toLocalTime(item.createdAt)}</td>
                  <td>
                    <button type="button" onClick={() => revoke(item.id)}>Thu hồi</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </PageShell>
  );
}
