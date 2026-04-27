import React, { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { api } from "../../services/api";
import { AuthMethod } from "../../types";

interface MfaLocationState {
  method?: AuthMethod;
  identifier?: string;
  challenge?: {
    remainingAttempts: number;
    resendAllowedAt: number;
    destination?: string;
  };
}

export function MfaChallengePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { pendingAuthTxId, setTokens, setProfile, setRole, addToast } = useAppContext();
  const state = (location.state || {}) as MfaLocationState;
  const method = state.method ?? "OTP";

  const [otp, setOtp] = useState("123456");
  const [remainingAttempts, setRemainingAttempts] = useState(state.challenge?.remainingAttempts ?? 5);
  const [resendIn, setResendIn] = useState(30);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const timer = setInterval(() => {
      setResendIn((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const challengeHint = useMemo(() => {
    if (method === "OTP") {
      return `Mã OTP đã gửi tới ${state.challenge?.destination ?? "email đã đăng ký"}.`;
    }

    if (method === "WEBAUTHN") {
      return "Nhấn xác nhận bằng thiết bị bảo mật hoặc trình duyệt hỗ trợ passkey.";
    }

    return "Nhập mã TOTP từ ứng dụng Authenticator.";
  }, [method, state.challenge?.destination]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!pendingAuthTxId) {
      addToast("error", "Không tìm thấy auth transaction, vui lòng login lại.");
      navigate("/idp");
      return;
    }

    setLoading(true);
    try {
      const result = await api.verifyMfa(pendingAuthTxId, method, method === "WEBAUTHN" ? undefined : otp);
      setTokens(result.accessToken, result.refreshToken);
      const me = await api.getProfile();
      setProfile(me);
      if (me.role === "ADMIN") {
        setRole("ADMIN");
      } else if (me.role === "DEVELOPER") {
        setRole("DEVELOPER");
      } else {
        setRole("USER");
      }
      addToast("success", "Xác thực MFA thành công.");
      navigate("/profile");
    } catch (error) {
      setRemainingAttempts((prev) => Math.max(0, prev - 1));
      addToast("error", (error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const resend = async () => {
    if (!pendingAuthTxId) {
      return;
    }

    if (!state.identifier) {
      addToast("error", "Không đủ dữ liệu để gửi lại OTP. Vui lòng đăng nhập lại.");
      return;
    }

    await api.resendOtp(state.identifier);
    setResendIn(30);
    addToast("info", "Đã gửi lại OTP.");
  };

  return (
    <PageShell mode="user" title="MFA Challenge" subtitle={challengeHint}>
      <section className="panel">
        <form className="form-grid" onSubmit={submit}>
          {method !== "WEBAUTHN" ? (
            <label>
              Mã xác thực
              <input value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="123456" />
            </label>
          ) : (
            <div className="hint-card">
              <p>WebAuthn sẽ xác thực bằng credential đã đăng ký. Ở môi trường mock, nhấn nút xác thực để tiếp tục.</p>
            </div>
          )}

          <p className="small-muted">Số lần thử còn lại: {remainingAttempts}</p>

          <div className="inline-actions">
            <button type="submit" disabled={loading}>
              {loading ? "Đang xác thực..." : "Xác thực"}
            </button>
            <button type="button" onClick={resend} disabled={resendIn > 0 || method !== "OTP"}>
              Gửi lại OTP {method === "OTP" ? `(${resendIn}s)` : ""}
            </button>
          </div>
        </form>
      </section>
    </PageShell>
  );
}
