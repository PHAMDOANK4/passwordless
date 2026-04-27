import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { api } from "../../services/api";

export function LogoutPage() {
  const navigate = useNavigate();
  const { addToast, setTokens, setProfile } = useAppContext();
  const [includeRpLogout, setIncludeRpLogout] = useState(true);

  const logout = async () => {
    await api.revokeAllSessions();
    await api.logout(includeRpLogout ? "id_token_hint_sample" : undefined);
    setTokens(null, null);
    setProfile(null);
    addToast("success", "Đã đăng xuất khỏi IdP. Có thể đã đăng xuất khỏi các ứng dụng SSO liên quan.");
    navigate("/idp");
  };

  return (
    <PageShell mode="user" title="Đăng xuất" subtitle="Kết thúc session IdP và thu hồi phiên hiện tại.">
      <section className="panel form-grid">
        <p>Bạn muốn đăng xuất khỏi hệ thống IdP?</p>
        <label className="checkbox-row">
          <input type="checkbox" checked={includeRpLogout} onChange={(e) => setIncludeRpLogout(e.target.checked)} />
          Gửi RP-initiated logout (`/oauth2/logout?id_token_hint=...`)
        </label>
        <div className="inline-actions">
          <button className="danger" type="button" onClick={logout}>
            Xác nhận đăng xuất
          </button>
          <button type="button" onClick={() => navigate(-1)}>
            Hủy
          </button>
        </div>
      </section>
    </PageShell>
  );
}
