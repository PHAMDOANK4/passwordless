import React, { useEffect, useState } from "react";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { api } from "../../services/api";
import { JwkInfo, SystemConfig } from "../../types";

export function AdminSystemPage() {
  const { addToast } = useAppContext();
  const [config, setConfig] = useState<SystemConfig | null>(null);
  const [jwks, setJwks] = useState<JwkInfo[]>([]);

  const load = async () => {
    const [configResult, jwksResult] = await Promise.all([api.getSystemConfig(), api.listJwks()]);
    setConfig(configResult);
    setJwks(jwksResult);
  };

  useEffect(() => {
    void load();
  }, []);

  const saveConfig = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!config) {
      return;
    }

    await api.saveSystemConfig(config);
    addToast("success", "Đã cập nhật cấu hình hệ thống.");
  };

  const rotateKey = async () => {
    const key = await api.rotateJwk();
    addToast("info", `Đã tạo key mới ${key.kid}.`);
    await load();
  };

  if (!config) {
    return <PageShell mode="admin" title="System Configuration">Loading...</PageShell>;
  }

  return (
    <PageShell mode="admin" title="System Configuration" subtitle="TTL token, grant type, rate limit và JWKS rotation.">
      <section className="panel">
        <form className="form-grid" onSubmit={saveConfig}>
          <label>
            Access token TTL (sec)
            <input type="number" value={config.accessTokenTtlSec} onChange={(e) => setConfig({ ...config, accessTokenTtlSec: Number(e.target.value) })} />
          </label>
          <label>
            Refresh token TTL (sec)
            <input type="number" value={config.refreshTokenTtlSec} onChange={(e) => setConfig({ ...config, refreshTokenTtlSec: Number(e.target.value) })} />
          </label>
          <label>
            Authorization code TTL (sec)
            <input
              type="number"
              value={config.authorizationCodeTtlSec}
              onChange={(e) => setConfig({ ...config, authorizationCodeTtlSec: Number(e.target.value) })}
            />
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={config.refreshRotationEnabled}
              onChange={(e) => setConfig({ ...config, refreshRotationEnabled: e.target.checked })}
            />
            Enable refresh token rotation
          </label>
          <button type="submit">Lưu cấu hình</button>
        </form>
      </section>

      <section className="panel">
        <h2>Rate limiting</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Endpoint</th>
                <th>Limit / minute</th>
                <th>By</th>
              </tr>
            </thead>
            <tbody>
              {config.rateLimits.map((rate) => (
                <tr key={`${rate.endpoint}-${rate.by}`}>
                  <td>{rate.endpoint}</td>
                  <td>{rate.perMinute}</td>
                  <td>{rate.by}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel">
        <h2>JWKS</h2>
        <button type="button" onClick={rotateKey}>Tạo key mới (rotation)</button>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>KID</th>
                <th>Algorithm</th>
                <th>Active</th>
              </tr>
            </thead>
            <tbody>
              {jwks.map((key) => (
                <tr key={key.kid}>
                  <td>{key.kid}</td>
                  <td>{key.algorithm}</td>
                  <td>{key.active ? "Yes" : "No"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </PageShell>
  );
}
