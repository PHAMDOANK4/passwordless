import React, { useEffect, useState } from "react";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { toLocalTime } from "../../lib/format";
import { api } from "../../services/api";
import { AuthorizedApp, MfaState, UserSession } from "../../types";

export function ProfilePage() {
  const { profile, setProfile, setRole, addToast, refreshToken } = useAppContext();
  const [sessions, setSessions] = useState<UserSession[]>([]);
  const [mfaState, setMfaState] = useState<MfaState | null>(null);
  const [authorizedApps, setAuthorizedApps] = useState<AuthorizedApp[]>([]);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [totpQr, setTotpQr] = useState<string | null>(null);
  const [totpCode, setTotpCode] = useState("123456");

  const load = async () => {
    const [profileResult, sessionResult, mfaResult, appsResult] = await Promise.all([
      api.getProfile(),
      api.getSessions(),
      api.getMfaState(),
      api.getAuthorizedApps(),
    ]);
    setProfile(profileResult);
    if (profileResult.role === "ADMIN") {
      setRole("ADMIN");
    } else if (profileResult.role === "DEVELOPER") {
      setRole("DEVELOPER");
    } else {
      setRole("USER");
    }
    setFirstName(profileResult.firstName ?? "");
    setLastName(profileResult.lastName ?? "");
    setSessions(sessionResult);
    setMfaState(mfaResult);
    setAuthorizedApps(appsResult);
  };

  useEffect(() => {
    void load();
  }, []);

  const saveProfile = async (event: React.FormEvent) => {
    event.preventDefault();
    const updated = await api.updateProfile(firstName, lastName);
    setProfile(updated);
    addToast("success", "Đã cập nhật thông tin người dùng.");
  };

  const revokeSession = async (sessionId: string) => {
    await api.revokeSession(sessionId);
    addToast("info", `Đã thu hồi session ${sessionId}.`);
    await load();
  };

  const revokeAll = async () => {
    await api.revokeAllSessions();
    if (refreshToken) {
      await api.revokeRefreshToken(refreshToken);
    }
    addToast("success", "Đã đăng xuất toàn bộ thiết bị và thu hồi refresh token.");
    await load();
  };

  const setupTotp = async () => {
    const setup = await api.setupTotp();
    setTotpQr(setup.qrCodeDataUrl);
  };

  const activateTotp = async () => {
    await api.activateTotp(totpCode);
    addToast("success", "Đã kích hoạt TOTP.");
    await load();
  };

  const registerWebauthn = async () => {
    await api.registerWebauthn();
    addToast("success", "Đã thêm WebAuthn credential.");
    await load();
  };

  const disableMethod = async (method: "TOTP" | "WEBAUTHN") => {
    await api.disableMfaMethod(method);
    addToast("info", `Đã vô hiệu hóa ${method}.`);
    await load();
  };

  const revokeGrant = async (grantId: string) => {
    await api.revokeAppGrant(grantId);
    addToast("info", "Đã thu hồi quyền ứng dụng.");
    await load();
  };

  return (
    <PageShell mode="user" title="User Profile" subtitle="Quản lý tài khoản, session, MFA và ứng dụng đã cấp quyền.">
      <div className="panel-grid two-col">
        <section className="panel">
          <h2>Thông tin tài khoản</h2>
          <p>Email: {profile?.email ?? "-"}</p>
          <p>Domain: {profile?.domain ?? "-"}</p>
          <p>Role: {profile?.role ?? "USER"}</p>
          <p>Trạng thái: {profile?.status ?? "-"}</p>
          <form className="form-grid" onSubmit={saveProfile}>
            <label>
              Họ
              <input value={lastName} onChange={(e) => setLastName(e.target.value)} />
            </label>
            <label>
              Tên
              <input value={firstName} onChange={(e) => setFirstName(e.target.value)} />
            </label>
            <button type="submit">Cập nhật</button>
          </form>
        </section>

        <section className="panel">
          <h2>Session đã đăng nhập</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>User Agent</th>
                  <th>IP</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {sessions.map((session) => (
                  <tr key={session.sessionId}>
                    <td>{toLocalTime(session.createdAt)}</td>
                    <td>{session.userAgent}</td>
                    <td>{session.ipAddress}</td>
                    <td>{session.status}</td>
                    <td>
                      <button type="button" onClick={() => revokeSession(session.sessionId)}>
                        Thu hồi
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button type="button" className="danger" onClick={revokeAll}>
            Đăng xuất toàn bộ
          </button>
        </section>

        <section className="panel">
          <h2>Quản lý MFA</h2>
          <p>OTP: {mfaState?.otpEnabled ? "Đang bật" : "Tắt"}</p>
          <p>TOTP: {mfaState?.totpEnabled ? "Đang bật" : "Tắt"}</p>
          <p>WebAuthn: {mfaState?.webauthnEnabled ? "Đang bật" : "Tắt"}</p>

          <div className="inline-actions">
            <button type="button" onClick={setupTotp}>Thêm TOTP</button>
            <button type="button" onClick={registerWebauthn}>Thêm WebAuthn</button>
            <button type="button" onClick={() => disableMethod("TOTP")}>Tắt TOTP</button>
            <button type="button" onClick={() => disableMethod("WEBAUTHN")}>Tắt WebAuthn</button>
          </div>

          {totpQr ? (
            <div className="totp-box">
              <img src={totpQr} alt="TOTP QR" />
              <label>
                Nhập mã để kích hoạt
                <input value={totpCode} onChange={(e) => setTotpCode(e.target.value)} />
              </label>
              <button type="button" onClick={activateTotp}>Xác nhận TOTP</button>
            </div>
          ) : null}
        </section>

        <section className="panel">
          <h2>Ứng dụng đã cấp quyền</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Ứng dụng</th>
                  <th>Scope</th>
                  <th>Thời gian</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {authorizedApps.map((app) => (
                  <tr key={app.grantId}>
                    <td>{app.clientName}</td>
                    <td>{app.scopes.join(", ")}</td>
                    <td>{toLocalTime(app.grantedAt)}</td>
                    <td>
                      <button type="button" onClick={() => revokeGrant(app.grantId)}>
                        Thu hồi quyền
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </PageShell>
  );
}
