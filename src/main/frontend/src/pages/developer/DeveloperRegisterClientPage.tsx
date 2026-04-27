import React, { useState } from "react";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { maskSecret } from "../../lib/format";
import { api } from "../../services/api";

export function DeveloperRegisterClientPage() {
  const { addToast } = useAppContext();
  const [result, setResult] = useState<{ clientId: string; clientSecret?: string } | null>(null);
  const [form, setForm] = useState({
    appName: "",
    redirectUris: "https://myapp.example.com/callback",
    scopes: "openid profile email",
    grantTypes: "authorization_code refresh_token",
    confidential: false,
  });

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const response = await api.selfRegisterClient({
      appName: form.appName,
      redirectUris: form.redirectUris.split("\n").map((item) => item.trim()).filter(Boolean),
      scopes: form.scopes.split(" ").filter(Boolean),
      grantTypes: form.grantTypes.split(" ").filter(Boolean),
      confidential: form.confidential,
    });
    setResult(response);
    addToast("success", "Đăng ký OAuth2 client thành công.");
  };

  return (
    <PageShell mode="developer" title="Developer Client Registration" subtitle="Tự phục vụ đăng ký OAuth2 client + cảnh báo bảo mật.">
      <section className="panel">
        <form className="form-grid" onSubmit={submit}>
          <label>
            App name
            <input value={form.appName} onChange={(e) => setForm({ ...form, appName: e.target.value })} required />
          </label>
          <label>
            Redirect URIs (mỗi dòng)
            <textarea value={form.redirectUris} onChange={(e) => setForm({ ...form, redirectUris: e.target.value })} />
          </label>
          <label>
            Scopes
            <input value={form.scopes} onChange={(e) => setForm({ ...form, scopes: e.target.value })} />
          </label>
          <label>
            Grant types
            <input value={form.grantTypes} onChange={(e) => setForm({ ...form, grantTypes: e.target.value })} />
          </label>
          <label className="checkbox-row">
            <input type="checkbox" checked={form.confidential} onChange={(e) => setForm({ ...form, confidential: e.target.checked })} />
            Confidential client
          </label>
          <button type="submit">Đăng ký client</button>
        </form>
      </section>

      {result ? (
        <section className="panel">
          <h2>Kết quả</h2>
          <p>Client ID: <strong>{result.clientId}</strong></p>
          {result.clientSecret ? <p>Client Secret (one-time): <strong>{maskSecret(result.clientSecret)}</strong></p> : null}
          <p className="danger-text">Không đặt client_secret trong SPA/mobile. Public client bắt buộc dùng PKCE.</p>
        </section>
      ) : null}

      <section className="panel">
        <h2>PKCE JavaScript mẫu</h2>
        <pre>{`const verifier = crypto.randomUUID().replace(/-/g, '')
const encoder = new TextEncoder()
const data = encoder.encode(verifier)
const digest = await crypto.subtle.digest('SHA-256', data)
const challenge = btoa(String.fromCharCode(...new Uint8Array(digest)))
  .replace(/\+/g, '-')
  .replace(/\//g, '_')
  .replace(/=+$/, '')`}</pre>
      </section>
    </PageShell>
  );
}
