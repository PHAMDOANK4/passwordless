import React, { useEffect, useState } from "react";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { api } from "../../services/api";
import { DomainConfig } from "../../types";

export function AdminDomainsPage() {
  const { addToast } = useAppContext();
  const [domains, setDomains] = useState<DomainConfig[]>([]);
  const [form, setForm] = useState<DomainConfig>({
    domainName: "",
    displayName: "",
    ownerEmail: "",
    active: true,
    ssoEnabled: true,
    mfaRequired: true,
    accessTokenTtlSec: 900,
  });

  const load = async () => {
    setDomains(await api.listDomains());
  };

  useEffect(() => {
    void load();
  }, []);

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    await api.saveDomain(form);
    addToast("success", "Đã lưu domain config.");
    setForm({
      domainName: "",
      displayName: "",
      ownerEmail: "",
      active: true,
      ssoEnabled: true,
      mfaRequired: true,
      accessTokenTtlSec: 900,
    });
    await load();
  };

  return (
    <PageShell mode="admin" title="Domain / Tenant Management" subtitle="Cấu hình SSO, MFA bắt buộc và token TTL theo domain.">
      <section className="panel">
        <h2>Thêm hoặc cập nhật domain</h2>
        <form className="inline-form" onSubmit={save}>
          <input placeholder="example.com" value={form.domainName} onChange={(e) => setForm({ ...form, domainName: e.target.value })} required />
          <input placeholder="Display name" value={form.displayName ?? ""} onChange={(e) => setForm({ ...form, displayName: e.target.value })} required />
          <input placeholder="Owner email" value={form.ownerEmail ?? ""} onChange={(e) => setForm({ ...form, ownerEmail: e.target.value })} required />
          <label className="checkbox-row">
            <input type="checkbox" checked={form.ssoEnabled} onChange={(e) => setForm({ ...form, ssoEnabled: e.target.checked })} />
            SSO enabled
          </label>
          <label className="checkbox-row">
            <input type="checkbox" checked={form.mfaRequired} onChange={(e) => setForm({ ...form, mfaRequired: e.target.checked })} />
            MFA required
          </label>
          <label className="checkbox-row">
            <input type="checkbox" checked={form.active ?? true} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
            Active
          </label>
          <input
            type="number"
            value={form.accessTokenTtlSec}
            onChange={(e) => setForm({ ...form, accessTokenTtlSec: Number(e.target.value) })}
            placeholder="Access token TTL"
          />
          <button type="submit">Lưu</button>
        </form>
      </section>

      <section className="panel">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Domain</th>
                <th>Display name</th>
                <th>Owner</th>
                <th>SSO</th>
                <th>MFA Required</th>
                <th>Active</th>
                <th>Access TTL (sec)</th>
              </tr>
            </thead>
            <tbody>
              {domains.map((domain) => (
                <tr key={domain.domainName}>
                  <td>{domain.domainName}</td>
                  <td>{domain.displayName ?? "-"}</td>
                  <td>{domain.ownerEmail ?? "-"}</td>
                  <td>{domain.ssoEnabled ? "Yes" : "No"}</td>
                  <td>{domain.mfaRequired ? "Yes" : "No"}</td>
                  <td>{domain.active ? "Yes" : "No"}</td>
                  <td>{domain.accessTokenTtlSec}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </PageShell>
  );
}
