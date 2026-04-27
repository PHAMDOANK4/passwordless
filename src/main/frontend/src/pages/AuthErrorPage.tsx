import React from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { PageShell } from "../components/PageShell";

interface AuthErrorPageProps {
  code: 401 | 403;
}

export function AuthErrorPage({ code }: AuthErrorPageProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const from = (location.state as { from?: string } | null)?.from;

  const title = code === 401 ? "401 - Chưa xác thực" : "403 - Không đủ quyền";
  const description =
    code === 401
      ? "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại để tiếp tục."
      : "Tài khoản hiện tại không có quyền truy cập khu vực này.";

  return (
    <PageShell mode="user" title={title} subtitle={description}>
      <section className="panel form-grid">
        {from ? <p>Bạn vừa truy cập: <strong>{from}</strong></p> : null}
        <div className="inline-actions">
          {code === 401 ? (
            <Link className="button-link" to="/idp">
              Đến trang đăng nhập
            </Link>
          ) : (
            <button type="button" onClick={() => navigate(-1)}>
              Quay lại trang trước
            </button>
          )}
          <Link className="button-link" to="/profile">
            Về trang hồ sơ
          </Link>
        </div>
      </section>
    </PageShell>
  );
}
