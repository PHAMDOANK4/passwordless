import React from "react";
import { Link, useSearchParams } from "react-router-dom";
import { PageShell } from "../../components/PageShell";

const descriptions: Record<string, string> = {
  invalid_request: "Thiếu tham số bắt buộc hoặc request không đúng chuẩn.",
  access_denied: "Người dùng đã từ chối cấp quyền.",
  redirect_uri_mismatch: "redirect_uri không khớp với cấu hình client.",
  invalid_client: "Client không hợp lệ hoặc chưa được kích hoạt.",
};

export function OAuthErrorPage() {
  const [params] = useSearchParams();
  const error = params.get("error") ?? "invalid_request";

  return (
    <PageShell mode="developer" title="OAuth2 Error" subtitle="Thông báo lỗi chuẩn cho authorization/token flows.">
      <section className="panel">
        <h2>{error}</h2>
        <p>{descriptions[error] ?? "Đã xảy ra lỗi OAuth2 không xác định."}</p>
        <div className="inline-actions">
          <Link className="button-link" to="/developer/register-client">Quay lại ứng dụng</Link>
          <Link className="button-link" to="/idp">Về trang chủ IdP</Link>
        </div>
      </section>
    </PageShell>
  );
}
