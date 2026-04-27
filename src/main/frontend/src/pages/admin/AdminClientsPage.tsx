import React, { useEffect, useState } from "react";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { maskSecret, toLocalTime } from "../../lib/format";
import { api } from "../../services/api";
import { OAuthClient } from "../../types";

export function AdminClientsPage() {
  const { addToast } = useAppContext();
  const [clients, setClients] = useState<OAuthClient[]>([]);
  const [lastGeneratedSecret, setLastGeneratedSecret] = useState<string | null>(null);
  const [form, setForm] = useState({
    clientName: "",
    redirectUris: "https://myapp.example.com/callback",
    scopes: "openid profile email",
    grantTypes: "authorization_code refresh_token",
    requirePkce: true,
    confidential: false,
  });

  const load = async () => {
    setClients(await api.listClients());
  };

  useEffect(() => {
    void load();
  }, []);

  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    const created = await api.createClient({
      clientName: form.clientName,
      redirectUris: form.redirectUris.split("\n").map((item) => item.trim()).filter(Boolean),
      scopes: form.scopes.split(" ").filter(Boolean),
      grantTypes: form.grantTypes.split(" ").filter(Boolean),
      requirePkce: form.requirePkce,
      confidential: form.confidential,
    });
    setLastGeneratedSecret(created.clientSecret ?? null);
    addToast("success", "Đã tạo OAuth2 client.");
    setForm({ ...form, clientName: "" });
    await load();
  };

  const disable = async (clientId: string) => {
    await api.disableClient(clientId);
    addToast("info", "Đã vô hiệu hóa client.");
    await load();
  };

  const resetSecret = async (clientId: string) => {
    const secret = await api.resetClientSecret(clientId);
    setLastGeneratedSecret(secret);
    addToast("success", "Đã reset secret, hãy lưu ngay vì chỉ hiển thị một lần.");
  };

  return (
    <PageShell mode="admin" title="OAuth2 Client Management" subtitle="Tạo/sửa/vô hiệu hóa client, reset secret và kiểm soát PKCE.">
      <section className="panel">
        <h2>Tạo client mới</h2>
        <form className="form-grid" onSubmit={create}>
          <label>
            Client name
            <input value={form.clientName} onChange={(e) => setForm({ ...form, clientName: e.target.value })} required />
          </label>
          <label>
            Redirect URIs (mỗi dòng một URI)
            <textarea value={form.redirectUris} onChange={(e) => setForm({ ...form, redirectUris: e.target.value })} />
          </label>
          <label>
            Scopes (cách nhau khoảng trắng)
            <input value={form.scopes} onChange={(e) => setForm({ ...form, scopes: e.target.value })} />
          </label>
          <label>
            Grant types (cách nhau khoảng trắng)
            <input value={form.grantTypes} onChange={(e) => setForm({ ...form, grantTypes: e.target.value })} />
          </label>
          <label className="checkbox-row">
            <input type="checkbox" checked={form.requirePkce} onChange={(e) => setForm({ ...form, requirePkce: e.target.checked })} />
            Require PKCE
          </label>
          <label className="checkbox-row">
            <input type="checkbox" checked={form.confidential} onChange={(e) => setForm({ ...form, confidential: e.target.checked })} />
            Confidential client (sinh secret)
          </label>
          <button type="submit">Tạo client</button>
        </form>
        {lastGeneratedSecret ? <p className="danger-text">Client Secret (one-time): {maskSecret(lastGeneratedSecret)}</p> : null}
      </section>

      <section className="panel">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Client ID</th>
                <th>Name</th>
                <th>Redirect URIs</th>
                <th>Scopes</th>
                <th>Grant types</th>
                <th>PKCE</th>
                <th>Active</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {clients.map((client) => (
                <tr key={client.clientId}>
                  <td>{client.clientId}</td>
                  <td>{client.clientName}</td>
                  <td>{client.redirectUris.join(", ") || "-"}</td>
                  <td>{client.scopes.join(" ")}</td>
                  <td>{client.grantTypes.join(" ")}</td>
                  <td>{client.requirePkce ? "Yes" : "No"}</td>
                  <td>{client.active ? "Yes" : "No"}</td>
                  <td>{toLocalTime(client.createdAt)}</td>
                  <td>
                    <div className="inline-actions">
                      <button type="button" onClick={() => resetSecret(client.clientId)}>Reset secret</button>
                      <button type="button" onClick={() => disable(client.clientId)}>Disable</button>
                    </div>
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
