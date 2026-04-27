import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { api } from "../../services/api";
import { AuthMethod } from "../../types";

export function LoginPage() {
  const navigate = useNavigate();
  const { addToast, setPendingAuthTxId } = useAppContext();
  const [identifier, setIdentifier] = useState("alice@example.com");
  const [method, setMethod] = useState<AuthMethod>("OTP");
  const [loading, setLoading] = useState(false);

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setLoading(true);
    try {
      const response = await api.login(identifier, method);
      setPendingAuthTxId(response.authTxId);
      addToast("success", `Đã tạo challenge ${response.selectedMethod}.`);
      navigate("/mfa", { state: { method, challenge: response.challenge, identifier } });
    } catch (error) {
      addToast("error", (error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageShell mode="user" title="Đăng nhập IdP" subtitle="Passwordless login với OTP, TOTP và WebAuthn.">
      <section className="panel">
        <form className="form-grid" onSubmit={onSubmit}>
          <label>
            Email / Username
            <input value={identifier} onChange={(e) => setIdentifier(e.target.value)} placeholder="alice@example.com" />
          </label>

          <label>
            Phương thức xác thực
            <select value={method} onChange={(e) => setMethod(e.target.value as AuthMethod)}>
              <option value="OTP">OTP</option>
              <option value="TOTP">TOTP</option>
              <option value="WEBAUTHN">WebAuthn</option>
            </select>
          </label>

          {method === "TOTP" ? (
            <div className="hint-card">
              <p>Nếu đây là lần đầu dùng TOTP, user sẽ được hiển thị QR code tại trang Profile để kích hoạt.</p>
            </div>
          ) : null}

          {method === "WEBAUTHN" ? (
            <div className="hint-card">
              <p>WebAuthn flow sẽ gọi challenge API và dùng navigator.credentials.get ở bước MFA challenge.</p>
            </div>
          ) : null}

          <button type="submit" disabled={loading}>
            {loading ? "Đang khởi tạo..." : "Tiếp tục"}
          </button>
        </form>
      </section>
    </PageShell>
  );
}
