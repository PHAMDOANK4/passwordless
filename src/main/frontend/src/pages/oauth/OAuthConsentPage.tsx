import React, { useEffect, useState } from "react";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { api } from "../../services/api";
import { ConsentRequest } from "../../types";

const scopeDescription: Record<string, string> = {
  openid: "Xác thực danh tính người dùng",
  profile: "Truy cập thông tin hồ sơ",
  email: "Truy cập địa chỉ email",
};

export function OAuthConsentPage() {
  const { addToast } = useAppContext();
  const [request, setRequest] = useState<ConsentRequest | null>(null);
  const [remember, setRemember] = useState(false);

  useEffect(() => {
    void api.getConsentRequest().then((data) => {
      setRequest(data);
      setRemember(data.rememberDecision);
    });
  }, []);

  const allow = async () => {
    await api.allowConsent(remember);
    addToast("success", "Bạn đã cấp quyền cho ứng dụng.");
  };

  const deny = async () => {
    await api.denyConsent();
    addToast("info", "Bạn đã từ chối yêu cầu cấp quyền.");
  };

  return (
    <PageShell mode="developer" title="OAuth2 Consent" subtitle="Trang xác nhận cấp quyền trước khi phát authorization code.">
      <section className="panel">
        <h2>Ứng dụng yêu cầu quyền: {request?.clientName ?? "..."}</h2>
        <ul>
          {(request?.scopes ?? []).map((scope) => (
            <li key={scope}>
              <strong>{scope}</strong>: {scopeDescription[scope] ?? "Quyền truy cập bổ sung"}
            </li>
          ))}
        </ul>
        <label className="checkbox-row">
          <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
          Ghi nhớ quyết định cho lần sau
        </label>
        <div className="inline-actions">
          <button type="button" onClick={allow}>Cho phép</button>
          <button type="button" className="danger" onClick={deny}>Từ chối</button>
        </div>
      </section>
    </PageShell>
  );
}
