import { api } from "../api.js";
import { ROUTES, goTo } from "../routes.js";
import { requireUnauthenticated } from "../guards.js";
import { byId, emptyToNull, setLoading, setStatus } from "../ui.js";
import { getState, setAuthTransaction, setUserEmail, setOauthRequestId, getIdpAccounts, removeIdpAccount } from "../store.js";

// Kiểm tra xem trang đang chạy bên trong popup window không
const IS_POPUP = Boolean(window.opener && !window.opener.closed);

document.addEventListener("DOMContentLoaded", () => {
  if (!requireUnauthenticated()) return;

  const params = new URLSearchParams(window.location.search);
  const oauthReqId = params.get("oauth_request_id");
  if (oauthReqId) {
    setOauthRequestId(oauthReqId);
    document.querySelectorAll('a[href^="/register/"]').forEach(link => {
      link.href = `/register/?oauth_request_id=${encodeURIComponent(oauthReqId)}`;
    });
  }

  // Gán tên app vào Account Chooser subtitle
  const appNameEl = byId("acAppName");
  if (appNameEl) appNameEl.textContent = oauthReqId ? "ứng dụng của bạn" : "AuthCentralA";

  // Kiểm tra có tài khoản đã lưu không
  const savedAccounts = getIdpAccounts();
  if (savedAccounts.length > 0) {
    _showAccountChooser(savedAccounts);
  } else {
    _showLoginPage();
  }

  // Login page events
  byId("loginSubmit")?.addEventListener("click", openModal);
  byId("loginModal")?.addEventListener("click", onOverlayClick);
  byId("modalLoginForm")?.addEventListener("submit", onModalSubmit);
  document.addEventListener("keydown", e => {
    if (e.key === "Escape") closeModal();
  });

  // Account Chooser events
  byId("acUseOtherBtn")?.addEventListener("click", _switchToLoginPage);
  byId("acCancelBtn")?.addEventListener("click", _handleCancel);
});

// ── Account Chooser ──────────────────────────────────────────────

function _showAccountChooser(accounts) {
  const wrap = byId("accountChooserWrap");
  if (!wrap) return;

  // Render danh sách tài khoản
  const listEl = byId("acAccountList");
  if (listEl) {
    listEl.innerHTML = accounts.map(acc => {
      const initial = (acc.name || acc.email)[0].toUpperCase();
      return `
        <div class="ac-item" data-email="${_escAttr(acc.email)}">
          <div class="ac-avatar">${initial}</div>
          <div class="ac-info">
            <div class="ac-name">${_escHtml(acc.name || acc.email)}</div>
            <div class="ac-email">${_escHtml(acc.email)}</div>
          </div>
          <span class="ac-chevron">&#x203A;</span>
        </div>
      `;
    }).join("");

    // Click vào một tài khoản → pre-fill email và mở modal
    listEl.querySelectorAll(".ac-item").forEach(item => {
      item.addEventListener("click", () => {
        const email = item.dataset.email;
        _selectAccount(email);
      });
    });
  }

  // Hiển thị Account Chooser, ẩn trang login
  wrap.style.display = "flex";
  const page = document.querySelector(".page");
  if (page) page.style.display = "none";
}

function _switchToLoginPage() {
  const wrap = byId("accountChooserWrap");
  if (wrap) wrap.style.display = "none";
  const page = document.querySelector(".page");
  if (page) page.style.display = "";
  _showLoginPage();
}

function _showLoginPage() {
  const page = document.querySelector(".page");
  if (page) page.style.display = "";

  const state = getState();
  const identifierInput = byId("loginIdentifier");
  if (state.userEmail && identifierInput && !identifierInput.value) {
    identifierInput.value = state.userEmail;
  }
}

function _selectAccount(email) {
  // Pre-fill email vào ô login và mở modal ngay
  const identifierInput = byId("loginIdentifier");
  if (identifierInput) identifierInput.value = email;
  setUserEmail(email);

  _switchToLoginPage();

  // Pre-fill modal email và mở ngay
  const modalInput = byId("modalIdentifier");
  if (modalInput) modalInput.value = email;

  openModal();
}

function _handleCancel() {
  if (IS_POPUP && window.opener && !window.opener.closed) {
    // Trong popup: báo cho parent biết người dùng đã hủy
    try {
      window.opener.postMessage({ type: "auth_cancelled" }, window.opener.location.origin);
    } catch (_) {
      // cross-origin fallback: vẫn đóng popup
    }
    window.close();
  } else {
    // Không phải popup: quay về trang trước
    history.back();
  }
}

// ── Modal login ──────────────────────────────────────────────────

function openModal() {
  const modal = byId("loginModal");
  if (!modal) return;

  const mainEmail = byId("loginIdentifier")?.value.trim();
  const modalInput = byId("modalIdentifier");
  if (modalInput && mainEmail && !modalInput.value) {
    modalInput.value = mainEmail;
  }

  modal.classList.add("open");
  byId("modalIdentifier")?.focus();
}

function closeModal() {
  byId("loginModal")?.classList.remove("open");
  const err = byId("modalError");
  if (err) err.textContent = "";
}

function onOverlayClick(e) {
  if (e.target === byId("loginModal")) closeModal();
}

async function onModalSubmit(e) {
  e.preventDefault();

  const submitBtn = byId("modalSubmit");
  const errorEl   = byId("modalError");
  const identifier = byId("modalIdentifier")?.value.trim();
  const preferredMethod = emptyToNull(byId("loginMethod")?.value);
  const clientId = emptyToNull(byId("loginClientId")?.value) || "passwordless-web";

  if (!identifier) {
    if (errorEl) errorEl.textContent = "Vui lòng nhập email hoặc số điện thoại.";
    return;
  }

  if (errorEl) errorEl.textContent = "";
  setLoading(submitBtn, true, "Đang xử lý...");

  const status = byId("loginStatus");

  try {
    const response = await api("/auth/login", {
      method: "POST",
      body: { identifier, preferredMethod, clientId }
    });

    if (!response?.authTxId) throw new Error("Login did not return an authTxId.");

    setAuthTransaction({
      authTxId: response.authTxId,
      selectedMethod: response.selectedMethod,
      challenge: response.challenge,
      userEmail: identifier
    });
    setUserEmail(identifier);

    setStatus(status, `Login started. Method=${response.selectedMethod || "AUTO"}.`, "ok");
    goTo(ROUTES.verifyOtp);
  } catch (error) {
    if (errorEl) errorEl.textContent = error.message;
    setStatus(status, error.message, "error");
  } finally {
    setLoading(submitBtn, false);
  }
}

// ── Helpers ──────────────────────────────────────────────────────

function _escHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;")
    .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function _escAttr(s) {
  return String(s).replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
