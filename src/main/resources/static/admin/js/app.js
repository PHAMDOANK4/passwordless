/* =====================================================
   Passwordless Admin - Main App JS
   ===================================================== */

const API = {
  BASE: window.location.origin,
  dashboard: { stats: () => '/admin/api/dashboard/stats' },
  users: {
    list: (p, s, q, st) => `/admin/api/users?page=${p}&size=${s}${q ? '&search=' + encodeURIComponent(q) : ''}${st ? '&status=' + st : ''}`,
    detail: id => `/admin/api/users/${id}`,
    update: id => `/admin/api/users/${id}`,
    delete: id => `/admin/api/users/${id}`,
    suspend: id => `/admin/api/users/${id}/suspend`,
    activate: id => `/admin/api/users/${id}/activate`,
    totpKeys: id => `/admin/api/users/${id}/totp-keys`,
    deleteTotpKey: (uid, kid) => `/admin/api/users/${uid}/totp-keys/${kid}`,
    passkeys: id => `/admin/api/users/${id}/passkeys`,
    deletePasskey: (uid, kid) => `/admin/api/users/${uid}/passkeys/${kid}`,
    otpSessions: id => `/admin/api/users/${id}/otp-sessions`,
    sessions: id => `/admin/api/users/${id}/sessions`,
    revokeSession: (uid, sid) => `/admin/api/users/${uid}/sessions/${sid}/revoke`,
    revokeAllSessions: id => `/admin/api/users/${id}/sessions/revoke-all`,
  },
  apps: {
    register: () => '/apps/v1/register',
    list: () => '/apps/v1/list',
    deactivate: id => `/apps/v1/${id}/deactivate`,
    activate: id => `/apps/v1/${id}/activate`,
    delete: id => `/apps/v1/${id}`,
    regenerateKey: id => `/apps/v1/${id}/regenerate-key`,
  },
  oauthClients: {
    list: () => '/admin/api/oauth2/clients',
    detail: id => `/admin/api/oauth2/clients/${id}`,
    create: () => '/admin/api/oauth2/clients',
    update: id => `/admin/api/oauth2/clients/${id}`,
    activate: id => `/admin/api/oauth2/clients/${id}/activate`,
    deactivate: id => `/admin/api/oauth2/clients/${id}/deactivate`,
    rotateSecret: id => `/admin/api/oauth2/clients/${id}/rotate-secret`,
    delete: id => `/admin/api/oauth2/clients/${id}`,
  },
  domains: {
    list: () => '/admin/api/domains',
    detail: id => `/admin/api/domains/${id}`,
    create: () => '/admin/api/domains',
    update: id => `/admin/api/domains/${id}`,
    delete: id => `/admin/api/domains/${id}`,
  },
  audit: {
    logs: (p, s) => `/apps/v1/audit/logs?page=${p}&size=${s}`,
  }
};

let adminToken = localStorage.getItem('admin_jwt');
let authTxId = null;

async function apiFetch(url, options = {}) {
  try {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (adminToken) {
      headers['Authorization'] = `Bearer ${adminToken}`;
    }
    const res = await fetch(url, { ...options, headers });

    if (res.status === 401 || res.status === 403) {
      showLoginOverlay('Phiên đăng nhập hết hạn hoặc bạn không có quyền truy cập.');
      throw new Error('Unauthorized');
    }

    const ct = res.headers.get('content-type') || '';
    let payload = null;
    if (ct.includes('application/json')) {
      payload = await res.json();
    } else if (res.status !== 204) {
      payload = await res.text();
    }

    if (!res.ok) {
      const message = typeof payload === 'string'
        ? payload
        : (payload && (payload.message || payload.error))
          ? (payload.message || payload.error)
          : `HTTP ${res.status}`;
      throw new Error(message);
    }

    if (ct.includes('application/json')) return payload;
    return payload;
  } catch (e) {
    throw e;
  }
}

// ---- ADMIN LOGIN LOGIC ----
function showLoginOverlay(errorMsg = '') {
  document.getElementById('loginOverlay').style.display = 'flex';
  document.getElementById('loginStep1').style.display = 'block';
  document.getElementById('loginStep2').style.display = 'none';
  const errEl = document.getElementById('loginError');
  if (errorMsg) {
    errEl.textContent = errorMsg;
    errEl.style.display = 'block';
  } else {
    errEl.style.display = 'none';
  }
  if (dashboardInterval) clearInterval(dashboardInterval);
}

function hideLoginOverlay() {
  document.getElementById('loginOverlay').style.display = 'none';
  document.getElementById('loginError').style.display = 'none';
  document.getElementById('logoutBtn').style.display = 'block';
}

document.getElementById('logoutBtn').addEventListener('click', () => {
  localStorage.removeItem('admin_jwt');
  adminToken = null;
  window.location.reload();
});

document.getElementById('loginNextBtn').addEventListener('click', async () => {
  const email = document.getElementById('loginEmail').value.trim();
  if (!email) return;
  const btn = document.getElementById('loginNextBtn');
  btn.disabled = true;
  btn.textContent = 'Đang xử lý...';

  try {
    const res = await fetch('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identifier: email, clientId: 'passwordless-web' })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || data.error || 'Lỗi đăng nhập');

    authTxId = data.authTxId;
    document.getElementById('loginStep1').style.display = 'none';
    document.getElementById('loginStep2').style.display = 'block';
    document.getElementById('loginError').style.display = 'none';
  } catch (e) {
    document.getElementById('loginError').textContent = e.message;
    document.getElementById('loginError').style.display = 'block';
  } finally {
    btn.disabled = false;
    btn.textContent = 'Tiếp tục →';
  }
});

document.getElementById('loginBackBtn').addEventListener('click', () => {
  document.getElementById('loginStep1').style.display = 'block';
  document.getElementById('loginStep2').style.display = 'none';
});

document.getElementById('loginVerifyBtn').addEventListener('click', async () => {
  const code = document.getElementById('loginCode').value.trim();
  if (!code || !authTxId) return;
  const btn = document.getElementById('loginVerifyBtn');
  btn.disabled = true;
  btn.textContent = 'Đang xác thực...';

  try {
    const isTotp = code.length === 6 && !isNaN(code);
    const res = await fetch('/auth/mfa/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        authTxId: authTxId,
        method: 'OTP',
        otp: code
      })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || data.error || 'Mã xác thực không hợp lệ');

    adminToken = data.accessToken;
    localStorage.setItem('admin_jwt', adminToken);
    hideLoginOverlay();

    // Load admin profile and start dashboard
    checkAdminProfile();
  } catch (e) {
    document.getElementById('loginError').textContent = e.message;
    document.getElementById('loginError').style.display = 'block';
  } finally {
    btn.disabled = false;
    btn.textContent = 'Xác thực';
  }
});

async function checkAdminProfile() {
  if (!adminToken) {
    showLoginOverlay();
    return;
  }
  try {
    const me = await apiFetch('/admin/api/me');
    document.getElementById('topbarInitials').textContent = me.email ? me.email.charAt(0).toUpperCase() : 'A';
    document.getElementById('topbarAvatar').title = `${me.email} (${me.role})`;
    hideLoginOverlay();

    // Auto load current page
    navigate(currentPage || 'dashboard');
  } catch (e) {
    console.error('Failed to load profile', e);
  }
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
  if (adminToken) {
    checkAdminProfile();
  } else {
    showLoginOverlay();
  }
});

// ---- TOAST ----
function showToast(msg, type = 'info') {
  const icons = {
    success: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>`,
    error: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/></svg>`,
    info: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd"/></svg>`
  };
  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.innerHTML = `<span class="toast-icon">${icons[type] || icons.info}</span><span>${msg}</span>`;
  document.getElementById('toastContainer').appendChild(el);
  setTimeout(() => el.remove(), 3500);
}

// ---- MODAL CONFIRM ----
let _modalResolve = null;
function showConfirm(title, body) {
  return new Promise(resolve => {
    _modalResolve = resolve;
    document.getElementById('modalTitle').textContent = title;
    document.getElementById('modalBody').innerHTML = body;
    document.getElementById('modalOverlay').style.display = 'flex';
    _modalResolve = resolve;
  });
}
document.getElementById('modalConfirm').onclick = () => { document.getElementById('modalOverlay').style.display = 'none'; _modalResolve && _modalResolve(true); };
document.getElementById('modalCancel').onclick = () => { document.getElementById('modalOverlay').style.display = 'none'; _modalResolve && _modalResolve(false); };
document.getElementById('modalClose').onclick = () => { document.getElementById('modalOverlay').style.display = 'none'; _modalResolve && _modalResolve(false); };
document.getElementById('modalOverlay').onclick = e => { if (e.target === document.getElementById('modalOverlay')) { document.getElementById('modalOverlay').style.display = 'none'; _modalResolve && _modalResolve(false); } };

// ---- HELPERS ----
function fmtDate(ts) {
  if (!ts) return '—';
  const d = new Date(typeof ts === 'number' ? ts : ts);
  return d.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
}
function fmtDateLong(ts) {
  if (!ts) return '—';
  const d = new Date(ts);
  return d.toLocaleString('vi-VN', { dateStyle: 'medium', timeStyle: 'medium' });
}
function initials(user) {
  if (user.displayName) return user.displayName.charAt(0).toUpperCase();
  if (user.email) return user.email.charAt(0).toUpperCase();
  return '?';
}
function truncate(str, n = 28) { return str && str.length > n ? str.slice(0, n) + '…' : (str || '—'); }
function statusBadge(s) {
  const map = { ACTIVE: 'badge-active', SUSPENDED: 'badge-suspended', DELETED: 'badge-deleted', PENDING_VERIFICATION: 'badge-pending' };
  const label = { ACTIVE: 'Active', SUSPENDED: 'Suspended', DELETED: 'Deleted', PENDING_VERIFICATION: 'Pending' };
  return `<span class="badge ${map[s] || ''}"><span class="badge-dot"></span>${label[s] || s}</span>`;
}
function roleBadge(r) {
  const colors = { SUPER_ADMIN: 'badge-totp', DOMAIN_ADMIN: 'badge-passkey', USER: 'badge-otp', GUEST: '' };
  return `<span class="badge ${colors[r] || ''}">${r || '—'}</span>`;
}
function mfaBadge(method) {
  if (!method) return '<span class="badge">—</span>';
  const colors = { TOTP: 'badge-totp', WEBAUTHN: 'badge-passkey', SMS: 'badge-otp', EMAIL: 'badge-otp' };
  return `<span class="badge ${colors[method] || ''}">${method}</span>`;
}

// ---- SIDEBAR TOGGLE ----
document.getElementById('sidebarToggle').onclick = () => {
  document.getElementById('sidebar').classList.toggle('open');
};

// ---- ROUTER ----
const pages = {};
let currentPage = '';
let dashboardInterval = null;

function navigate(page, params = {}) {
  if (dashboardInterval) {
    clearInterval(dashboardInterval);
    dashboardInterval = null;
  }

  // Update nav active state
  document.querySelectorAll('.nav-item').forEach(el => {
    el.classList.toggle('active', el.dataset.page === page);
  });

  // Update breadcrumb
  const labels = {
    dashboard: 'Dashboard',
    users: 'Người dùng',
    'user-detail': 'Chi tiết người dùng',
    apps: 'Ứng dụng',
    'oauth-clients': 'OAuth2 Clients',
    domains: 'Domain SSO',
    audit: 'Audit Logs'
  };
  const bc = document.getElementById('breadcrumb');
  if (page === 'user-detail') {
    bc.innerHTML = `<span onclick="navigate('users')" style="cursor:pointer;color:var(--text-muted)">Người dùng</span> <span style="color:var(--text-muted);margin:0 6px">/</span> <span>Chi tiết</span>`;
  } else {
    bc.innerHTML = `<span>${labels[page] || page}</span>`;
  }

  // Show/hide search
  document.getElementById('topbarSearchWrapper').style.display = ['users'].includes(page) ? 'flex' : 'none';

  currentPage = page;
  const content = document.getElementById('mainContent');
  content.innerHTML = `<div class="page-loading"><div class="spinner"></div><p>Đang tải...</p></div>`;

  if (pages[page]) {
    pages[page](params);
  }
}

document.querySelectorAll('.nav-item[data-page]').forEach(el => {
  el.addEventListener('click', e => { e.preventDefault(); navigate(el.dataset.page); });
});

// =========================================================
// PAGE: DASHBOARD
// =========================================================
let myUserChart = null;
let myAuthChart = null;

pages.dashboard = async function () {
  const content = document.getElementById('mainContent');
  content.innerHTML = `
    <div class="page-header">
      <div style="display:flex; justify-content:space-between; align-items:flex-end; flex-wrap:wrap; gap:10px;">
        <div>
          <h1>Dashboard</h1>
          <p>Tổng quan hệ thống xác thực không mật khẩu</p>
        </div>
        <div style="display:flex; align-items:center; gap:8px; font-size:12px; color:var(--text-muted)">
          <div class="footer-dot" style="width:10px;height:10px;box-shadow:0 0 8px var(--emerald)"></div> Live Update: <span id="lastUpdateTime" style="font-family:monospace">Đang tải...</span>
        </div>
      </div>
    </div>

    <div class="stats-grid">
      ${statCard('Tổng người dùng', 'statTotalUsers', 'var(--indigo)', 'var(--indigo-alpha)', userIcon(), '<span id="statActiveUsers">...</span> Active')}
      ${statCard('TOTP Keys', 'statTotpKeys', 'var(--emerald)', 'var(--emerald-alpha)', totpIcon(), 'Google Authenticator')}
      ${statCard('Passkeys', 'statPasskeys', '#a78bfa', 'rgba(139,92,246,0.12)', passkeyIcon(), 'WebAuthn / FIDO2')}
      ${statCard('OTP Sessions', 'statOtpSessions', 'var(--sky)', 'var(--sky-alpha)', otpIcon(), 'SMS & Email')}
      ${statCard('Ứng dụng', 'statTotalApps', 'var(--amber)', 'var(--amber-alpha)', appIcon(), 'Đã đăng ký')}
      ${statCard('Audit Logs', 'statAuditLogs', 'var(--rose)', 'var(--rose-alpha)', auditIcon(), 'Lịch sử hoạt động')}
    </div>

    <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(300px, 1fr));gap:20px">
      <div class="card" style="display:flex;flex-direction:column;">
        <div class="card-header">
          <div class="card-title">${userIcon()} Phân bổ tài khoản</div>
        </div>
        <div class="card-body" style="flex:1; display:flex; align-items:center; justify-content:center;">
           <div style="width:240px; height:240px; position:relative;">
             <canvas id="userDistChart"></canvas>
           </div>
        </div>
      </div>
      <div class="card" style="display:flex;flex-direction:column;">
        <div class="card-header">
          <div class="card-title">${passkeyIcon()} Phương thức đang dùng</div>
        </div>
        <div class="card-body" style="flex:1; display:flex; align-items:center; justify-content:center;">
           <div style="width:240px; height:240px; position:relative;">
             <canvas id="authMethodsChart"></canvas>
           </div>
        </div>
      </div>
    </div>

    <div style="margin-top:20px" class="card">
      <div class="card-header">
        <div class="card-title">🔒 Bảo mật tài khoản (KPIs)</div>
      </div>
      <div class="card-body">
        <div class="info-grid">
          <div class="info-item"><div class="info-label">MFA Activated</div><div class="info-value" id="statMfaUsers" style="color:var(--emerald);font-size:22px;font-weight:800">...</div></div>
          <div class="info-item"><div class="info-label">Tài khoản Suspended</div><div class="info-value" id="statSuspendedUsers" style="color:var(--amber);font-size:22px;font-weight:800">...</div></div>
          <div class="info-item"><div class="info-label">Tỉ lệ kích hoạt MFA</div><div class="info-value" id="statMfaRatio" style="color:var(--indigo-light);font-size:22px;font-weight:800">...</div></div>
          <div class="info-item"><div class="info-label">Tổng số Auth Keys</div><div class="info-value" id="statTotalAuthKeys" style="font-size:22px;font-weight:800">...</div></div>
        </div>
      </div>
    </div>
  `;

  if (myUserChart) myUserChart.destroy();
  if (myAuthChart) myAuthChart.destroy();

  const chartOpts = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom', labels: { color: '#8b91a8', font: { family: "'Inter', sans-serif", size: 12 } } } },
    cutout: '72%',
    borderWidth: 0,
    animation: { animateScale: true }
  };

  const ctxUser = document.getElementById('userDistChart').getContext('2d');
  myUserChart = new Chart(ctxUser, {
    type: 'doughnut',
    data: {
      labels: ['Active', 'Suspended', 'Unverified/Other'],
      datasets: [{ data: [0, 0, 0], backgroundColor: ['#10b981', '#f59e0b', '#252a3a'], borderColor: 'transparent', hoverOffset: 4 }]
    },
    options: chartOpts
  });

  const ctxAuth = document.getElementById('authMethodsChart').getContext('2d');
  myAuthChart = new Chart(ctxAuth, {
    type: 'doughnut',
    data: {
      labels: ['TOTP', 'Passkeys', 'OTP Sessions'],
      datasets: [{ data: [0, 0, 0], backgroundColor: ['#6366f1', '#a78bfa', '#0ea5e9'], borderColor: 'transparent', hoverOffset: 4 }]
    },
    options: chartOpts
  });

  await fetchDashboardStats();
  dashboardInterval = setInterval(fetchDashboardStats, 5000);
};

async function fetchDashboardStats() {
  try {
    const stats = await apiFetch(API.dashboard.stats());

    document.getElementById('statTotalUsers').textContent = stats.totalUsers || 0;
    document.getElementById('statActiveUsers').textContent = stats.activeUsers || 0;
    document.getElementById('statTotpKeys').textContent = stats.totalTotpKeys || 0;
    document.getElementById('statPasskeys').textContent = stats.totalPasskeys || 0;
    document.getElementById('statOtpSessions').textContent = stats.totalOtpSessions || 0;
    document.getElementById('statTotalApps').textContent = stats.totalApps || 0;
    document.getElementById('statAuditLogs').textContent = stats.totalAuditLogs || 0;

    document.getElementById('statMfaUsers').textContent = stats.mfaEnabledUsers || 0;
    document.getElementById('statSuspendedUsers').textContent = stats.suspendedUsers || 0;
    const ratio = stats.totalUsers > 0 ? Math.round((stats.mfaEnabledUsers / stats.totalUsers) * 100) : 0;
    document.getElementById('statMfaRatio').textContent = ratio + '%';
    document.getElementById('statTotalAuthKeys').textContent = ((stats.totalTotpKeys || 0) + (stats.totalPasskeys || 0));

    // update charts dynamically without re-rendering everything
    const active = stats.activeUsers || 0;
    const suspended = stats.suspendedUsers || 0;
    const other = Math.max(0, (stats.totalUsers || 0) - active - suspended);

    if (myUserChart && (myUserChart.data.datasets[0].data[0] !== active || myUserChart.data.datasets[0].data[1] !== suspended)) {
      myUserChart.data.datasets[0].data = [active, suspended, other];
      myUserChart.update();
    }

    if (myAuthChart && (myAuthChart.data.datasets[0].data[0] !== stats.totalTotpKeys || myAuthChart.data.datasets[0].data[1] !== stats.totalPasskeys)) {
      myAuthChart.data.datasets[0].data = [stats.totalTotpKeys || 0, stats.totalPasskeys || 0, stats.totalOtpSessions || 0];
      myAuthChart.update();
    }

    const now = new Date();
    document.getElementById('lastUpdateTime').textContent = now.toLocaleTimeString('vi-VN');
    document.getElementById('badge-users').textContent = stats.totalUsers || 0;
  } catch (e) {
    console.error('Lỗi load live stats:', e);
  }
}

function statCard(label, valueId, color, bg, icon, sub) {
  return `
    <div class="stat-card" style="--accent:${color}">
      <div class="stat-card-top">
        <div class="stat-icon" style="background:${bg}">${icon.replace('currentColor', color)}</div>
      </div>
      <div class="stat-value" id="${valueId}">...</div>
      <div class="stat-label">${label}</div>
      <div class="stat-sub">${sub}</div>
    </div>`;
}

// =========================================================
// PAGE: USERS LIST
// =========================================================
let usersState = { page: 0, size: 10, search: '', status: '' };

pages.users = async function (params = {}) {
  if (params.reset) usersState = { page: 0, size: 10, search: '', status: '' };
  const content = document.getElementById('mainContent');

  content.innerHTML = `
    <div class="page-header">
      <h1>Người dùng</h1>
      <p>Quản lý tài khoản và các khóa xác thực</p>
    </div>
    <div class="card">
      <div class="filter-bar">
        <div class="filter-input-wrap">
          <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd"/></svg>
          <input class="filter-input" id="searchInput" placeholder="Tìm email hoặc tên..." value="${usersState.search}" />
        </div>
        <select class="filter-select" id="statusFilter">
          <option value="">Tất cả trạng thái</option>
          <option value="ACTIVE" ${usersState.status === 'ACTIVE' ? 'selected' : ''}>Active</option>
          <option value="SUSPENDED" ${usersState.status === 'SUSPENDED' ? 'selected' : ''}>Suspended</option>
          <option value="PENDING_VERIFICATION" ${usersState.status === 'PENDING_VERIFICATION' ? 'selected' : ''}>Pending</option>
          <option value="DELETED" ${usersState.status === 'DELETED' ? 'selected' : ''}>Deleted</option>
        </select>
      </div>
      <div id="usersTableArea"><div class="page-loading"><div class="spinner"></div></div></div>
    </div>`;

  // Bind filters
  let searchTimer;
  document.getElementById('searchInput').addEventListener('input', e => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { usersState.search = e.target.value; usersState.page = 0; loadUsersTable(); }, 400);
  });
  document.getElementById('globalSearch').addEventListener('input', e => {
    document.getElementById('searchInput').value = e.target.value;
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { usersState.search = e.target.value; usersState.page = 0; loadUsersTable(); }, 400);
  });
  document.getElementById('statusFilter').addEventListener('change', e => {
    usersState.status = e.target.value; usersState.page = 0; loadUsersTable();
  });

  loadUsersTable();
};

async function loadUsersTable() {
  const area = document.getElementById('usersTableArea');
  if (!area) return;
  area.innerHTML = '<div class="page-loading" style="padding:30px"><div class="spinner"></div></div>';
  try {
    const data = await apiFetch(API.users.list(usersState.page, usersState.size, usersState.search, usersState.status));
    const users = data.users || [];
    if (!users.length) {
      area.innerHTML = `<div class='empty-state' style='padding:60px'>${userIcon()}<p>Không tìm thấy người dùng nào</p></div>`;
      return;
    }
    area.innerHTML = `
      <div class="table-wrapper">
        <table class="data-table">
          <thead><tr>
            <th>Người dùng</th>
            <th>Trạng thái</th>
            <th>Vai trò</th>
            <th>MFA</th>
            <th>Đăng nhập cuối</th>
            <th>Thao tác</th>
          </tr></thead>
          <tbody>${users.map(u => userRow(u)).join('')}</tbody>
        </table>
      </div>
      <div class="pagination">
        <span>Hiển thị ${users.length} / ${data.totalElements} người dùng</span>
        <div class="pagination-pages">${renderPageBtns(data.currentPage, data.totalPages)}</div>
      </div>`;

    // Bind row actions
    area.querySelectorAll('[data-action="view"]').forEach(btn => {
      btn.onclick = () => navigate('user-detail', { id: btn.dataset.id });
    });
    area.querySelectorAll('[data-action="suspend"]').forEach(btn => {
      btn.onclick = () => suspendUser(btn.dataset.id, btn.dataset.email);
    });
    area.querySelectorAll('[data-action="activate"]').forEach(btn => {
      btn.onclick = () => activateUser(btn.dataset.id, btn.dataset.email);
    });
    area.querySelectorAll('[data-action="delete"]').forEach(btn => {
      btn.onclick = () => deleteUser(btn.dataset.id, btn.dataset.email);
    });
    area.querySelectorAll('.page-btn[data-p]').forEach(btn => {
      btn.onclick = () => { usersState.page = parseInt(btn.dataset.p); loadUsersTable(); };
    });
  } catch (e) {
    area.innerHTML = errorState('Không tải được danh sách người dùng.');
  }
}

function userRow(u) {
  const isActive = u.status === 'ACTIVE';
  return `<tr>
    <td>
      <div style="display:flex;align-items:center;gap:10px">
        <div class="topbar-avatar" style="width:32px;height:32px;font-size:12px;flex-shrink:0">${initials(u)}</div>
        <div>
          <div class="primary" style="font-size:13.5px">${truncate(u.displayName || u.email, 30)}</div>
          <div style="font-size:12px;color:var(--text-muted)">${truncate(u.email, 32)}</div>
        </div>
      </div>
    </td>
    <td>${statusBadge(u.status)}${u.locked ? ` <span class="badge badge-deleted">🔒 Locked</span>` : ''}</td>
    <td>${roleBadge(u.role)}</td>
    <td>${u.authMethods && u.authMethods.length > 0 ? u.authMethods.map(m => mfaBadge(m)).join(' ') : '<span class="badge" style="color:var(--text-muted)">Chưa có</span>'}</td>
    <td style="font-size:12px;white-space:nowrap">${u.lastLoginAt ? fmtDate(u.lastLoginAt) : '<span style="color:var(--text-muted)">Chưa Login Bao Giờ</span>'}</td>
    <td>
      <div class="actions">
        <button class="btn-icon" data-action="view" data-id="${u.id}" title="Xem chi tiết">
          <svg viewBox="0 0 20 20" fill="currentColor"><path d="M10 12a2 2 0 100-4 2 2 0 000 4z"/><path fill-rule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clip-rule="evenodd"/></svg>
        </button>
        ${isActive
      ? `<button class="btn-icon" data-action="suspend" data-id="${u.id}" data-email="${u.email}" title="Đình chỉ" style="color:var(--amber)">
              <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M13.477 14.89A6 6 0 015.11 6.524L13.477 14.89zm1.414-1.414L6.524 5.11a6 6 0 018.367 8.367zM18 10a8 8 0 11-16 0 8 8 0 0116 0z" clip-rule="evenodd"/></svg>
             </button>`
      : `<button class="btn-icon" data-action="activate" data-id="${u.id}" data-email="${u.email}" title="Kích hoạt" style="color:var(--emerald)">
              <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
             </button>`}
        <button class="btn-icon" data-action="delete" data-id="${u.id}" data-email="${u.email}" title="Xóa" style="color:var(--rose)">
          <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
        </button>
      </div>
    </td>
  </tr>`;
}

function renderPageBtns(current, total) {
  if (total <= 1) return '';
  let btns = `<button class="page-btn" data-p="${current - 1}" ${current === 0 ? 'disabled' : ''}>‹</button>`;
  for (let i = 0; i < total; i++) {
    if (total > 7 && Math.abs(i - current) > 2 && i !== 0 && i !== total - 1) {
      if (i === 1 || i === total - 2) btns += `<span style="padding:0 4px;color:var(--text-muted)">…</span>`;
      continue;
    }
    btns += `<button class="page-btn ${i === current ? 'active' : ''}" data-p="${i}">${i + 1}</button>`;
  }
  btns += `<button class="page-btn" data-p="${current + 1}" ${current >= total - 1 ? 'disabled' : ''}>›</button>`;
  return btns;
}

async function suspendUser(id, email) {
  const ok = await showConfirm('Đình chỉ tài khoản', `Bạn có chắc muốn đình chỉ tài khoản <strong>${email}</strong>?`);
  if (!ok) return;
  try {
    await apiFetch(API.users.suspend(id), { method: 'POST' });
    showToast('Đã đình chỉ tài khoản', 'success');
    loadUsersTable();
  } catch (e) { showToast('Có lỗi xảy ra', 'error'); }
}

async function activateUser(id, email) {
  const ok = await showConfirm('Kích hoạt tài khoản', `Kích hoạt lại tài khoản <strong>${email}</strong>?`);
  if (!ok) return;
  try {
    await apiFetch(API.users.activate(id), { method: 'POST' });
    showToast('Đã kích hoạt tài khoản', 'success');
    loadUsersTable();
  } catch (e) { showToast('Có lỗi xảy ra', 'error'); }
}

async function deleteUser(id, email) {
  const ok = await showConfirm('Xóa tài khoản', `<div style="color:var(--rose);margin-bottom:8px">⚠️ Hành động này không thể hoàn tác!</div>Bạn có chắc muốn xóa tài khoản <strong>${email}</strong>?`);
  if (!ok) return false;
  try {
    await apiFetch(API.users.delete(id), { method: 'DELETE' });
    showToast('Đã xóa tài khoản', 'success');
    loadUsersTable();
    return true;
  } catch (e) {
    showToast(`Xóa thất bại: ${e.message}`, 'error');
    return false;
  }
}

// =========================================================
// PAGE: USER DETAIL
// =========================================================
pages['user-detail'] = async function ({ id }) {
  const content = document.getElementById('mainContent');
  try {
    const user = await apiFetch(API.users.detail(id));

    content.innerHTML = `
      <button class="back-btn" id="backBtn">
        <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9.707 16.707a1 1 0 01-1.414 0l-6-6a1 1 0 010-1.414l6-6a1 1 0 011.414 1.414L5.414 9H17a1 1 0 110 2H5.414l4.293 4.293a1 1 0 010 1.414z" clip-rule="evenodd"/></svg>
        Quay lại danh sách
      </button>
      <div class="user-detail-grid">
        <!-- LEFT PROFILE CARD -->
        <div class="user-profile-card">
          <div class="user-avatar-lg">${initials(user)}</div>
          <div class="user-profile-name">${user.displayName || (user.firstName || '') + ' ' + (user.lastName || '') || '—'}</div>
          <div class="user-profile-email">${user.email}</div>
          <div class="user-profile-badges">
            ${statusBadge(user.status)}
            ${roleBadge(user.role)}
            ${user.authMethods && user.authMethods.length > 0 ? user.authMethods.map(m => mfaBadge(m)).join(' ') : ''}
            ${user.locked ? '<span class="badge badge-deleted">🔒 Locked</span>' : ''}
          </div>
          <div class="user-meta-list">
            <div class="user-meta-item"><span class="meta-key">ID</span><span class="meta-value" style="font-size:11px;font-family:monospace">${user.id}</span></div>
            <div class="user-meta-item"><span class="meta-key">Phone</span><span class="meta-value">${user.phoneNumber || '—'}</span></div>
            <div class="user-meta-item"><span class="meta-key">Locale</span><span class="meta-value">${user.locale || '—'}</span></div>
            <div class="user-meta-item"><span class="meta-key">Timezone</span><span class="meta-value">${user.timezone || '—'}</span></div>
            <div class="user-meta-item"><span class="meta-key">Tạo lúc</span><span class="meta-value">${fmtDate(user.createdAt)}</span></div>
            <div class="user-meta-item"><span class="meta-key">Login cuối</span><span class="meta-value">${user.lastLoginAt ? fmtDate(user.lastLoginAt) : '<span style="color:var(--text-muted)">Chưa Login Lần Nào</span>'}</span></div>
            <div class="user-meta-item"><span class="meta-key">IP cuối</span><span class="meta-value">${user.lastLoginIp || '—'}</span></div>
            <div class="user-meta-item"><span class="meta-key">Sai mật khẩu</span><span class="meta-value" style="color:${user.failedLoginAttempts > 0 ? 'var(--amber)' : 'inherit'}">${user.failedLoginAttempts}</span></div>
            ${user.lockedUntil ? `<div class="user-meta-item"><span class="meta-key">Khóa đến</span><span class="meta-value" style="color:var(--rose)">${fmtDate(user.lockedUntil)}</span></div>` : ''}
          </div>
          <div class="user-actions-profile">
            ${user.status === 'ACTIVE'
        ? `<button class="btn btn-warning" onclick="suspendUser('${user.id}','${user.email}').then(()=>navigate('user-detail',{id:'${user.id}'}))">
                  <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M13.477 14.89A6 6 0 015.11 6.524L13.477 14.89zm1.414-1.414L6.524 5.11a6 6 0 018.367 8.367zM18 10a8 8 0 11-16 0 8 8 0 0116 0z" clip-rule="evenodd"/></svg>
                  Đình chỉ tài khoản
                </button>`
        : `<button class="btn btn-success" onclick="activateUser('${user.id}','${user.email}').then(()=>navigate('user-detail',{id:'${user.id}'}))">
                  <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
                  Kích hoạt
                </button>`}
            <button class="btn btn-danger" onclick="deleteUser('${user.id}','${user.email}').then(ok=>{if(ok)navigate('users',{reset:true})})">
              <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
              Xóa tài khoản
            </button>
          </div>
        </div>

        <!-- RIGHT DETAIL TABS -->
        <div class="user-detail-main">
          <div class="card">
            <div class="tabs" id="detailTabs">
              <button class="tab-btn active" data-tab="totp">🔑 TOTP Keys <span class="tab-count" id="tc-totp">${user.totpKeyCount || 0}</span></button>
              <button class="tab-btn" data-tab="passkeys">🔐 Passkeys <span class="tab-count" id="tc-pass">${user.passkeyCount || 0}</span></button>
              <button class="tab-btn" data-tab="otp">📱 OTP Sessions</button>
              <button class="tab-btn" data-tab="sessions">🧠 IdP Sessions</button>
            </div>
            <div id="tab-totp" class="tab-panel active"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
            <div id="tab-passkeys" class="tab-panel"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
            <div id="tab-otp" class="tab-panel"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
            <div id="tab-sessions" class="tab-panel"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
          </div>
        </div>
      </div>`;

    document.getElementById('backBtn').onclick = () => navigate('users');

    // Tab switching
    document.querySelectorAll('#detailTabs .tab-btn').forEach(btn => {
      btn.onclick = () => {
        document.querySelectorAll('#detailTabs .tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById('tab-' + btn.dataset.tab).classList.add('active');
      };
    });

    // Load all tabs in parallel
    loadTotpTab(id);
    loadPasskeysTab(id);
    loadOtpTab(id);
    loadIdpSessionsTab(id);

  } catch (e) {
    content.innerHTML = errorState('Không tải được chi tiết người dùng.');
  }
};

async function loadTotpTab(userId) {
  const panel = document.getElementById('tab-totp');
  try {
    const keys = await apiFetch(API.users.totpKeys(userId));
    if (!keys.length) { panel.innerHTML = emptyKeys('Chưa có TOTP key nào được đăng ký'); return; }
    panel.innerHTML = `<div class="keys-list">${keys.map(k => `
      <div class="key-item">
        <div style="display:flex;align-items:center;gap:12px">
          <div class="stat-icon" style="width:36px;height:36px;background:var(--indigo-alpha)">${totpIcon().replace('20px', '16px')}</div>
          <div class="key-info">
            <div class="key-name">${k.username}</div>
            <div class="key-sub">ID: <code style="font-size:11px">${k.id}</code> · Secret: ${k.hasSecret ? '●●●●●●●●' : 'Không có'}</div>
          </div>
        </div>
        <button class="btn btn-sm btn-danger" data-kid="${k.id}" onclick="deleteTotpKey('${userId}','${k.id}','${k.username}')">
          <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
          Xóa
        </button>
      </div>`).join('')}</div>`;
  } catch (e) { panel.innerHTML = errorState('Không tải được TOTP keys'); }
}

async function loadPasskeysTab(userId) {
  const panel = document.getElementById('tab-passkeys');
  try {
    const keys = await apiFetch(API.users.passkeys(userId));
    if (!keys.length) { panel.innerHTML = emptyKeys('Chưa có Passkey nào được đăng ký'); return; }
    panel.innerHTML = `<div class="keys-list">${keys.map(k => `
      <div class="key-item" style="flex-direction:column;align-items:stretch;gap:10px">
        <div style="display:flex;align-items:center;justify-content:space-between;gap:12px">
          <div style="display:flex;align-items:center;gap:12px">
            <div class="stat-icon" style="width:36px;height:36px;background:rgba(139,92,246,0.12)">${passkeyIcon().replace('currentColor', '#a78bfa').replace('20px', '16px')}</div>
            <div class="key-info">
              <div class="key-name">${k.deviceName || 'Thiết bị không xác định'}</div>
              <div class="key-sub">${k.username} · ${k.attestationType || 'unknown'} · Dùng lần cuối: ${fmtDate(k.lastUsedAt)}</div>
            </div>
          </div>
          <button class="btn btn-sm btn-danger" onclick="deletePasskey('${userId}',${k.id},'${k.deviceName || 'thiết bị này'}')">
            <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
            Xóa
          </button>
        </div>
        <div class="info-grid" style="border-radius:var(--radius-sm);overflow:hidden">
          <div class="info-item"><div class="info-label">Credential ID</div><div class="info-value" style="font-size:11px;font-family:monospace">${truncate(k.credentialId, 36)}</div></div>
          <div class="info-item"><div class="info-label">Counter</div><div class="info-value">${k.counter ?? '—'}</div></div>
          <div class="info-item"><div class="info-label">Backup</div><div class="info-value">${k.backedUp ? '✅ Có' : '❌ Không'}</div></div>
          <div class="info-item"><div class="info-label">Đăng ký lúc</div><div class="info-value">${fmtDate(k.createdAt)}</div></div>
        </div>
      </div>`).join('')}</div>`;
  } catch (e) { panel.innerHTML = errorState('Không tải được Passkeys'); }
}

async function loadOtpTab(userId) {
  const panel = document.getElementById('tab-otp');
  try {
    const sessions = await apiFetch(API.users.otpSessions(userId));
    if (!sessions.length) { panel.innerHTML = emptyKeys('Không có OTP session nào gần đây'); return; }
    panel.innerHTML = `
      <div class="table-wrapper">
        <table class="data-table">
          <thead><tr><th>Session ID</th><th>Destination</th><th>Attempts</th><th>Gửi lúc</th><th>Hết hạn</th></tr></thead>
          <tbody>${sessions.map(s => `
            <tr>
              <td><code style="font-size:11px">${String(s.sessionId).substring(0, 14)}…</code></td>
              <td class="primary">${s.destination || '—'}</td>
              <td>${s.attempts ?? 0}</td>
              <td style="font-size:12px">${fmtDate(s.lastSentAt)}</td>
              <td>${s.expired ? '<span class="badge badge-deleted">Hết hạn</span>' : '<span class="badge badge-active">Còn hạn</span>'}</td>
            </tr>`).join('')}
          </tbody>
        </table>
      </div>`;
  } catch (e) { panel.innerHTML = errorState('Không tải được OTP sessions'); }
}

async function loadIdpSessionsTab(userId) {
  const panel = document.getElementById('tab-sessions');
  try {
    const sessions = await apiFetch(API.users.sessions(userId));
    if (!sessions.length) {
      panel.innerHTML = emptyKeys('Không có IdP session đang hoạt động');
      return;
    }

    panel.innerHTML = `
      <div style="display:flex;justify-content:flex-end;margin-bottom:10px">
        <button class="btn btn-sm btn-danger" id="revokeAllSessionsBtn">Revoke All Sessions</button>
      </div>
      <div class="table-wrapper">
        <table class="data-table">
          <thead><tr><th>Session ID</th><th>Auth</th><th>IP</th><th>Device</th><th>Last Activity</th><th>Expires</th><th>Action</th></tr></thead>
          <tbody>${sessions.map(s => `
            <tr>
              <td><code style="font-size:11px">${truncate(s.sessionId, 22)}</code></td>
              <td>${mfaBadge(s.authMethod || 'OTP')} <span style="font-size:11px;color:var(--text-muted)">L${s.authLevel ?? 1}</span></td>
              <td style="font-size:12px;font-family:monospace">${s.ipAddress || '—'}</td>
              <td style="font-size:12px;max-width:260px">${truncate(s.deviceInfo || 'Unknown', 58)}</td>
              <td style="font-size:12px">${fmtDate(s.lastActivityAt)}</td>
              <td style="font-size:12px">${fmtDate(s.expiresAt)}</td>
              <td><button class="btn btn-sm btn-warning" data-revoke-session="${s.sessionId}">Revoke</button></td>
            </tr>`).join('')}</tbody>
        </table>
      </div>`;

    document.getElementById('revokeAllSessionsBtn').onclick = () => revokeAllUserSessions(userId);
    panel.querySelectorAll('[data-revoke-session]').forEach(btn => {
      btn.onclick = () => revokeUserSession(userId, btn.dataset.revokeSession);
    });
  } catch (e) {
    panel.innerHTML = errorState('Không tải được IdP sessions');
  }
}

async function revokeUserSession(userId, sessionId) {
  const ok = await showConfirm('Revoke Session', `Thu hồi session <strong>${sessionId}</strong>?`);
  if (!ok) return;
  try {
    await apiFetch(API.users.revokeSession(userId, sessionId), { method: 'POST' });
    showToast('Đã thu hồi session', 'success');
    loadIdpSessionsTab(userId);
  } catch (e) {
    showToast(`Thu hồi thất bại: ${e.message}`, 'error');
  }
}

async function revokeAllUserSessions(userId) {
  const ok = await showConfirm('Revoke All Sessions', 'Thu hồi tất cả session đang hoạt động của user này?');
  if (!ok) return;
  try {
    const res = await apiFetch(API.users.revokeAllSessions(userId), { method: 'POST' });
    showToast(`Đã thu hồi ${res.revokedCount ?? 0} session`, 'success');
    loadIdpSessionsTab(userId);
  } catch (e) {
    showToast(`Thu hồi thất bại: ${e.message}`, 'error');
  }
}

async function deleteTotpKey(userId, keyId, username) {
  const ok = await showConfirm('Xóa TOTP Key', `Xóa TOTP key của <strong>${username}</strong>? Người dùng sẽ cần đăng ký lại Google Authenticator.`);
  if (!ok) return;
  try {
    await apiFetch(API.users.deleteTotpKey(userId, keyId), { method: 'DELETE' });
    showToast('Đã xóa TOTP key', 'success');
    loadTotpTab(userId);
  } catch (e) { showToast('Lỗi khi xóa TOTP key', 'error'); }
}

async function deletePasskey(userId, keyId, deviceName) {
  const ok = await showConfirm('Xóa Passkey', `Xóa Passkey <strong>${deviceName}</strong>? Người dùng sẽ không thể dùng thiết bị này để đăng nhập.`);
  if (!ok) return;
  try {
    await apiFetch(API.users.deletePasskey(userId, keyId), { method: 'DELETE' });
    showToast('Đã xóa Passkey', 'success');
    loadPasskeysTab(userId);
  } catch (e) { showToast('Lỗi khi xóa Passkey', 'error'); }
}

// =========================================================
// PAGE: APPS
// =========================================================
pages.apps = async function () {
  const content = document.getElementById('mainContent');
  content.innerHTML = `<div class="page-header"><h1>Ứng dụng</h1><p>Quản lý các ứng dụng đã đăng ký và API keys</p></div>
    <div class="card">
      <div class="tabs" id="appsTabs">
        <button class="tab-btn active" data-app-tab="list">📦 Danh sách ứng dụng</button>
        <button class="tab-btn" data-app-tab="generate">🔑 Generate API Key</button>
      </div>
      <div id="appsListTab" class="tab-panel active">
        <div class="card-header"><div class="card-title">${appIcon()} Danh sách ứng dụng</div></div>
        <div id="appsArea"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
      </div>
      <div id="appsGenerateTab" class="tab-panel">
        <div class="card-header"><div class="card-title">🔐 Cấp API Key cho khách hàng</div></div>
        <div class="card-body" id="apiKeyGenerateBody"></div>
      </div>
    </div>`;

  renderApiKeyGenerator();

  document.querySelectorAll('#appsTabs .tab-btn').forEach(btn => {
    btn.onclick = () => {
      document.querySelectorAll('#appsTabs .tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('#mainContent .tab-panel').forEach(p => p.classList.remove('active'));
      btn.classList.add('active');
      if (btn.dataset.appTab === 'generate') {
        document.getElementById('appsGenerateTab').classList.add('active');
      } else {
        document.getElementById('appsListTab').classList.add('active');
      }
    };
  });

  try {
    const apps = await apiFetch(API.apps.list());
    if (!apps.length) { document.getElementById('appsArea').innerHTML = emptyKeys('Chưa có ứng dụng nào đăng ký'); return; }
    document.getElementById('appsArea').innerHTML = `<div class="apps-grid">${apps.map(a => appCard(a)).join('')}</div>`;
    document.querySelectorAll('[data-app-action]').forEach(btn => {
      btn.onclick = () => handleAppAction(btn.dataset.appAction, btn.dataset.id, btn.dataset.name);
    });
  } catch (e) { document.getElementById('appsArea').innerHTML = errorState('Không tải được danh sách ứng dụng'); }
};

function appCard(a) {
  return `<div class="app-card">
    <div>
      <div class="app-card-name">${a.name}</div>
      <div class="app-card-desc">${a.description || 'Không có mô tả'}</div>
      <div class="app-card-meta">
        <span class="app-meta-item"><strong>Rate:</strong> ${a.rateLimitPerMinute}/min · ${a.rateLimitPerHour}/hr</span>
        <span class="app-meta-item"><strong>Tạo:</strong> ${fmtDate(a.createdAt)}</span>
        <span class="app-meta-item"><strong>Dùng cuối:</strong> ${fmtDate(a.lastUsedAt)}</span>
      </div>
    </div>
    <div style="display:flex;flex-direction:column;align-items:flex-end;gap:8px">
      ${a.active ? '<span class="badge badge-active"><span class="badge-dot"></span>Active</span>' : '<span class="badge badge-suspended">Inactive</span>'}
      <div class="actions">
        ${a.active
      ? `<button class="btn btn-sm btn-warning" data-app-action="deactivate" data-id="${a.id}" data-name="${a.name}">Deactivate</button>`
      : `<button class="btn btn-sm btn-success" data-app-action="activate" data-id="${a.id}" data-name="${a.name}">Activate</button>`}
        <button class="btn btn-sm" data-app-action="regenerate" data-id="${a.id}" data-name="${a.name}">Regenerate Key</button>
        <button class="btn btn-sm btn-danger" data-app-action="delete" data-id="${a.id}" data-name="${a.name}">Xóa</button>
      </div>
    </div>
  </div>`;
}

async function handleAppAction(action, id, name) {
  if (action === 'deactivate') {
    const ok = await showConfirm('Deactivate App', `Vô hiệu hóa ứng dụng <strong>${name}</strong>?`);
    if (!ok) return;
    await apiFetch(API.apps.deactivate(id), { method: 'POST' });
    showToast('Đã vô hiệu hóa ứng dụng', 'success');
    pages.apps();
  } else if (action === 'activate') {
    await apiFetch(API.apps.activate(id), { method: 'POST' });
    showToast('Đã kích hoạt ứng dụng', 'success');
    pages.apps();
  } else if (action === 'regenerate') {
    const ok = await showConfirm('Regenerate API Key', `Tạo API key mới cho <strong>${name}</strong>? API key cũ sẽ hết hiệu lực ngay.`);
    if (!ok) return;
    const newKey = await apiFetch(API.apps.regenerateKey(id), { method: 'POST' });
    showGeneratedApiKey({ name, apiKey: newKey });
    showToast('Đã tạo API key mới', 'success');
  } else if (action === 'delete') {
    const ok = await showConfirm('Xóa App', `<span style="color:var(--rose)">⚠️ Không thể hoàn tác!</span><br>Xóa ứng dụng <strong>${name}</strong>?`);
    if (!ok) return;
    await apiFetch(API.apps.delete(id), { method: 'DELETE' });
    showToast('Đã xóa ứng dụng', 'success');
    pages.apps();
  }
}

function renderApiKeyGenerator() {
  const body = document.getElementById('apiKeyGenerateBody');
  if (!body) return;
  body.innerHTML = `
    <form id="apiKeyForm" style="display:grid;gap:14px;max-width:760px">
      <div>
        <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Tên khách hàng / ứng dụng</label>
        <input id="appNameInput" class="filter-input" placeholder="Ví dụ: customer-acme-prod" minlength="3" maxlength="100" required />
      </div>
      <div>
        <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Mô tả</label>
        <textarea id="appDescInput" class="filter-input" placeholder="Mục đích sử dụng API key" style="min-height:88px;resize:vertical"></textarea>
      </div>
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px">
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Rate limit / phút</label>
          <input id="rateMinuteInput" class="filter-input" type="number" min="1" value="60" required />
        </div>
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Rate limit / giờ</label>
          <input id="rateHourInput" class="filter-input" type="number" min="1" value="1000" required />
        </div>
      </div>
      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <button type="submit" class="btn btn-success">Generate API Key</button>
        <span style="font-size:12px;color:var(--text-muted)">API key chỉ hiển thị một lần. Hãy lưu ngay sau khi tạo.</span>
      </div>
    </form>
    <div id="apiKeyResult" style="margin-top:16px"></div>`;

  const form = document.getElementById('apiKeyForm');
  form.addEventListener('submit', handleGenerateApiKey);
}

async function handleGenerateApiKey(event) {
  event.preventDefault();
  const submitBtn = event.target.querySelector('button[type="submit"]');
  const payload = {
    name: document.getElementById('appNameInput').value.trim(),
    description: document.getElementById('appDescInput').value.trim(),
    rateLimitPerMinute: Number(document.getElementById('rateMinuteInput').value),
    rateLimitPerHour: Number(document.getElementById('rateHourInput').value)
  };

  if (!payload.name || payload.name.length < 3) {
    showToast('Tên ứng dụng tối thiểu 3 ký tự', 'error');
    return;
  }
  if (!Number.isFinite(payload.rateLimitPerMinute) || payload.rateLimitPerMinute < 1) {
    showToast('Rate limit/phút phải lớn hơn 0', 'error');
    return;
  }
  if (!Number.isFinite(payload.rateLimitPerHour) || payload.rateLimitPerHour < 1) {
    showToast('Rate limit/giờ phải lớn hơn 0', 'error');
    return;
  }

  try {
    submitBtn.disabled = true;
    submitBtn.textContent = 'Đang tạo...';
    const res = await apiFetch(API.apps.register(), {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    showGeneratedApiKey(res);
    showToast('Tạo API key thành công', 'success');
  } catch (e) {
    showToast(`Không thể tạo API key: ${e.message}`, 'error');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = 'Generate API Key';
  }
}

function showGeneratedApiKey(app) {
  const result = document.getElementById('apiKeyResult');
  if (!result || !app || !app.apiKey) return;
  result.innerHTML = `
    <div class="info-grid">
      <div class="info-item" style="grid-column:1/-1">
        <div class="info-label">Ứng dụng</div>
        <div class="info-value">${app.name || 'N/A'}</div>
      </div>
      <div class="info-item" style="grid-column:1/-1">
        <div class="info-label">API Key</div>
        <div class="info-value" style="font-family:monospace;word-break:break-all">${app.apiKey}</div>
      </div>
      <div class="info-item">
        <div class="info-label">Rate limit / phút</div>
        <div class="info-value">${app.rateLimitPerMinute ?? 'N/A'}</div>
      </div>
      <div class="info-item">
        <div class="info-label">Rate limit / giờ</div>
        <div class="info-value">${app.rateLimitPerHour ?? 'N/A'}</div>
      </div>
    </div>
    <div style="margin-top:10px;display:flex;gap:10px;flex-wrap:wrap">
      <button class="btn btn-sm" id="copyApiKeyBtn">Copy API Key</button>
      <span style="font-size:12px;color:var(--text-muted)">Chia sẻ key này cho khách hàng để gọi OTP/TOTP APIs.</span>
    </div>`;

  const copyBtn = document.getElementById('copyApiKeyBtn');
  copyBtn.onclick = async () => {
    try {
      await navigator.clipboard.writeText(app.apiKey);
      showToast('Đã copy API key', 'success');
    } catch (_) {
      showToast('Không thể copy tự động, vui lòng copy thủ công', 'error');
    }
  };
}

// =========================================================
// PAGE: OAUTH2 CLIENTS
// =========================================================
pages['oauth-clients'] = async function () {
  const content = document.getElementById('mainContent');
  content.innerHTML = `<div class="page-header"><h1>OAuth2 Clients</h1><p>Quản lý OAuth clients cho Authorization Code + OIDC flow</p></div>
    <div class="card">
      <div class="tabs" id="oauthTabs">
        <button class="tab-btn active" data-oauth-tab="list">🧩 Danh sách Clients</button>
        <button class="tab-btn" data-oauth-tab="create">🆕 Tạo Client</button>
      </div>
      <div id="oauthListTab" class="tab-panel active">
        <div class="card-header"><div class="card-title">🔐 OAuth2 Client Registry</div></div>
        <div id="oauthClientsArea"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
      </div>
      <div id="oauthCreateTab" class="tab-panel">
        <div class="card-header"><div class="card-title">➕ Provision OAuth2 Client</div></div>
        <div class="card-body" id="oauthClientCreateBody"></div>
      </div>
    </div>`;

  renderOAuthClientCreator();
  await loadOAuthClientsCards();

  document.querySelectorAll('#oauthTabs .tab-btn').forEach(btn => {
    btn.onclick = () => {
      document.querySelectorAll('#oauthTabs .tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('#mainContent .tab-panel').forEach(p => p.classList.remove('active'));
      btn.classList.add('active');
      if (btn.dataset.oauthTab === 'create') {
        document.getElementById('oauthCreateTab').classList.add('active');
      } else {
        document.getElementById('oauthListTab').classList.add('active');
      }
    };
  });
};

async function loadOAuthClientsCards() {
  const area = document.getElementById('oauthClientsArea');
  if (!area) return;
  area.innerHTML = '<div class="page-loading" style="padding:30px"><div class="spinner"></div></div>';

  try {
    const clients = await apiFetch(API.oauthClients.list());
    if (!clients.length) {
      area.innerHTML = emptyKeys('Chưa có OAuth client nào');
      return;
    }

    area.innerHTML = `<div class="apps-grid">${clients.map(c => oauthClientCard(c)).join('')}</div>`;
    area.querySelectorAll('[data-oauth-action]').forEach(btn => {
      btn.onclick = () => handleOAuthClientAction(btn.dataset.oauthAction, btn.dataset.id, btn.dataset.name);
    });
  } catch (e) {
    area.innerHTML = errorState('Không tải được OAuth clients');
  }
}

function oauthClientCard(client) {
  const redirects = (client.redirectUris || []).slice(0, 2).map(v => `<div class="app-meta-item" style="font-family:monospace">↳ ${truncate(v, 56)}</div>`).join('');
  const scopes = (client.allowedScopes || []).map(s => `<span class="badge badge-otp">${s}</span>`).join(' ');
  const activeBadge = client.active
    ? '<span class="badge badge-active"><span class="badge-dot"></span>Active</span>'
    : '<span class="badge badge-suspended">Inactive</span>';

  return `<div class="app-card">
    <div>
      <div class="app-card-name">${client.clientName}</div>
      <div class="app-card-desc" style="font-family:monospace">client_id: ${client.clientId}</div>
      <div class="app-card-meta">
        <span class="app-meta-item"><strong>Domain:</strong> ${client.domainName || 'default.com'}</span>
        <span class="app-meta-item"><strong>PKCE:</strong> ${client.requirePkce ? 'Required' : 'Optional'}</span>
        <span class="app-meta-item"><strong>Grant:</strong> ${(client.grantTypes || []).join(', ') || 'authorization_code'}</span>
        <span class="app-meta-item"><strong>Created:</strong> ${fmtDate(client.createdAt)}</span>
      </div>
      <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:8px">${scopes || '<span class="badge">openid</span>'}</div>
      <div style="display:grid;gap:2px;margin-top:8px">${redirects || '<div class="app-meta-item">↳ No redirect URI</div>'}</div>
    </div>
    <div style="display:flex;flex-direction:column;align-items:flex-end;gap:8px">
      ${activeBadge}
      <div class="actions">
        <button class="btn btn-sm" data-oauth-action="edit" data-id="${client.id}" data-name="${client.clientName}">Edit</button>
        ${client.active
      ? `<button class="btn btn-sm btn-warning" data-oauth-action="deactivate" data-id="${client.id}" data-name="${client.clientName}">Deactivate</button>`
      : `<button class="btn btn-sm btn-success" data-oauth-action="activate" data-id="${client.id}" data-name="${client.clientName}">Activate</button>`}
        <button class="btn btn-sm" data-oauth-action="rotate" data-id="${client.id}" data-name="${client.clientName}">Rotate Secret</button>
        <button class="btn btn-sm btn-danger" data-oauth-action="delete" data-id="${client.id}" data-name="${client.clientName}">Delete</button>
      </div>
    </div>
  </div>`;
}

async function handleOAuthClientAction(action, id, name) {
  if (action === 'edit') {
    const client = await apiFetch(API.oauthClients.detail(id));
    populateOAuthClientForm(client);
    showToast(`Editing ${name}`, 'info');

    const createTabButton = document.querySelector('#oauthTabs .tab-btn[data-oauth-tab="create"]');
    if (createTabButton) {
      createTabButton.click();
    }
    return;
  }

  if (action === 'activate') {
    await apiFetch(API.oauthClients.activate(id), { method: 'POST' });
    showToast('Đã kích hoạt OAuth client', 'success');
    await loadOAuthClientsCards();
    return;
  }

  if (action === 'deactivate') {
    const ok = await showConfirm('Deactivate OAuth Client', `Ngừng hoạt động client <strong>${name}</strong>?`);
    if (!ok) return;
    await apiFetch(API.oauthClients.deactivate(id), { method: 'POST' });
    showToast('Đã deactivate OAuth client', 'success');
    await loadOAuthClientsCards();
    return;
  }

  if (action === 'rotate') {
    const ok = await showConfirm('Rotate Client Secret', `Tạo client secret mới cho <strong>${name}</strong>? Secret cũ sẽ vô hiệu ngay.`);
    if (!ok) return;
    const secret = await apiFetch(API.oauthClients.rotateSecret(id), { method: 'POST' });
    showOAuthClientSecret(secret, 'Client secret đã được xoay vòng thành công');
    showToast('Đã rotate secret', 'success');
    return;
  }

  if (action === 'delete') {
    const ok = await showConfirm('Delete OAuth Client', `<span style="color:var(--rose)">⚠️ Không thể hoàn tác!</span><br>Xóa client <strong>${name}</strong>?`);
    if (!ok) return;
    await apiFetch(API.oauthClients.delete(id), { method: 'DELETE' });
    showToast('Đã xóa OAuth client', 'success');
    await loadOAuthClientsCards();
  }
}

function renderOAuthClientCreator() {
  const body = document.getElementById('oauthClientCreateBody');
  if (!body) return;

  body.innerHTML = `
    <form id="oauthClientForm" style="display:grid;gap:14px;max-width:860px">
      <input type="hidden" id="oauthEditId" value="" />
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px">
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Client Name *</label>
          <input id="oauthClientName" class="filter-input" placeholder="My Web App" required />
        </div>
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Client ID (optional)</label>
          <input id="oauthClientId" class="filter-input" placeholder="web-app-prod" />
        </div>
      </div>

      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px">
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Domain</label>
          <input id="oauthDomain" class="filter-input" value="default.com" />
        </div>
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Created By</label>
          <input id="oauthCreatedBy" class="filter-input" value="admin-ui" />
        </div>
      </div>

      <div>
        <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Redirect URIs * (mỗi dòng một URI)</label>
        <textarea id="oauthRedirectUris" class="filter-input" style="min-height:110px;resize:vertical">https://localhost/callback</textarea>
      </div>

      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px">
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Allowed Scopes (space/comma separated)</label>
          <input id="oauthScopes" class="filter-input" value="openid profile email" />
        </div>
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Grant Types (comma separated)</label>
          <input id="oauthGrantTypes" class="filter-input" value="authorization_code,refresh_token" />
        </div>
      </div>

      <div style="display:flex;gap:18px;flex-wrap:wrap">
        <label style="display:flex;align-items:center;gap:8px"><input type="checkbox" id="oauthRequirePkce" checked /> Require PKCE</label>
        <label style="display:flex;align-items:center;gap:8px"><input type="checkbox" id="oauthActive" checked /> Active after create</label>
      </div>

      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <button type="submit" class="btn btn-success" id="oauthSubmitBtn">Create OAuth Client</button>
        <button type="button" class="btn btn-ghost" id="oauthCancelEditBtn" style="display:none">Cancel Edit</button>
        <span style="font-size:12px;color:var(--text-muted)">Client secret chỉ hiển thị một lần.</span>
      </div>
    </form>
    <div id="oauthClientSecretResult" style="margin-top:16px"></div>`;

  document.getElementById('oauthClientForm').addEventListener('submit', handleCreateOAuthClient);
  document.getElementById('oauthCancelEditBtn').onclick = () => clearOAuthClientEditState(true);
}

async function handleCreateOAuthClient(event) {
  event.preventDefault();
  const submitBtn = document.getElementById('oauthSubmitBtn');
  const editId = document.getElementById('oauthEditId').value.trim();

  const redirectUris = document.getElementById('oauthRedirectUris').value
    .split('\n')
    .map(v => v.trim())
    .filter(Boolean);

  const allowedScopes = document.getElementById('oauthScopes').value
    .split(/[ ,]+/)
    .map(v => v.trim())
    .filter(Boolean);

  const grantTypes = document.getElementById('oauthGrantTypes').value
    .split(',')
    .map(v => v.trim())
    .filter(Boolean);

  if (!redirectUris.length) {
    showToast('Cần ít nhất 1 redirect URI', 'error');
    return;
  }

  const payload = {
    clientName: document.getElementById('oauthClientName').value.trim(),
    clientId: document.getElementById('oauthClientId').value.trim() || null,
    domainName: document.getElementById('oauthDomain').value.trim() || 'default.com',
    createdBy: document.getElementById('oauthCreatedBy').value.trim() || 'admin-ui',
    redirectUris,
    allowedScopes,
    grantTypes,
    requirePkce: document.getElementById('oauthRequirePkce').checked,
    active: document.getElementById('oauthActive').checked
  };

  if (!payload.clientName) {
    showToast('Client name không được để trống', 'error');
    return;
  }

  try {
    submitBtn.disabled = true;
    submitBtn.textContent = editId ? 'Đang cập nhật...' : 'Đang tạo...';

    if (editId) {
      await apiFetch(API.oauthClients.update(editId), {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
      showToast('Đã cập nhật OAuth client', 'success');
      clearOAuthClientEditState(false);
    } else {
      const secret = await apiFetch(API.oauthClients.create(), {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      showOAuthClientSecret(secret, 'Tạo OAuth client thành công');
      showToast('Đã tạo OAuth client', 'success');
    }

    await loadOAuthClientsCards();
  } catch (e) {
    const actionLabel = editId ? 'cập nhật' : 'tạo';
    showToast(`Không thể ${actionLabel} client: ${e.message}`, 'error');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = document.getElementById('oauthEditId').value.trim()
      ? 'Update OAuth Client'
      : 'Create OAuth Client';
  }
}

function populateOAuthClientForm(client) {
  document.getElementById('oauthEditId').value = client.id || '';
  document.getElementById('oauthClientName').value = client.clientName || '';
  document.getElementById('oauthClientId').value = client.clientId || '';
  document.getElementById('oauthDomain').value = client.domainName || 'default.com';
  document.getElementById('oauthRedirectUris').value = (client.redirectUris || []).join('\n');
  document.getElementById('oauthScopes').value = (client.allowedScopes || []).join(' ');
  document.getElementById('oauthGrantTypes').value = (client.grantTypes || []).join(',');
  document.getElementById('oauthRequirePkce').checked = !!client.requirePkce;
  document.getElementById('oauthActive').checked = !!client.active;

  document.getElementById('oauthSubmitBtn').textContent = 'Update OAuth Client';
  document.getElementById('oauthCancelEditBtn').style.display = 'inline-flex';
}

function clearOAuthClientEditState(clearSecretResult) {
  const form = document.getElementById('oauthClientForm');
  if (form) {
    form.reset();
  }

  document.getElementById('oauthEditId').value = '';
  document.getElementById('oauthDomain').value = 'default.com';
  document.getElementById('oauthCreatedBy').value = 'admin-ui';
  document.getElementById('oauthRedirectUris').value = 'https://localhost/callback';
  document.getElementById('oauthScopes').value = 'openid profile email';
  document.getElementById('oauthGrantTypes').value = 'authorization_code,refresh_token';
  document.getElementById('oauthRequirePkce').checked = true;
  document.getElementById('oauthActive').checked = true;

  document.getElementById('oauthSubmitBtn').textContent = 'Create OAuth Client';
  document.getElementById('oauthCancelEditBtn').style.display = 'none';

  if (clearSecretResult) {
    const secretArea = document.getElementById('oauthClientSecretResult');
    if (secretArea) {
      secretArea.innerHTML = '';
    }
  }
}

function showOAuthClientSecret(secret, heading) {
  const result = document.getElementById('oauthClientSecretResult');
  if (!result || !secret || !secret.clientSecret) return;

  result.innerHTML = `
    <div class="info-grid">
      <div class="info-item"><div class="info-label">Client ID</div><div class="info-value" style="font-family:monospace">${secret.clientId}</div></div>
      <div class="info-item"><div class="info-label">Issued At</div><div class="info-value">${fmtDate(secret.issuedAt)}</div></div>
      <div class="info-item" style="grid-column:1/-1">
        <div class="info-label">Client Secret</div>
        <div class="info-value" style="font-family:monospace;word-break:break-all">${secret.clientSecret}</div>
      </div>
    </div>
    <div style="margin-top:10px;display:flex;gap:10px;align-items:center;flex-wrap:wrap">
      <button class="btn btn-sm" id="copyOAuthSecretBtn">Copy Client Secret</button>
      <span style="font-size:12px;color:var(--text-muted)">${heading || 'Client secret chỉ hiển thị một lần.'}</span>
    </div>`;

  const copyBtn = document.getElementById('copyOAuthSecretBtn');
  copyBtn.onclick = async () => {
    try {
      await navigator.clipboard.writeText(secret.clientSecret);
      showToast('Đã copy client secret', 'success');
    } catch (_) {
      showToast('Không thể copy tự động', 'error');
    }
  };
}

// =========================================================
// PAGE: DOMAIN SSO
// =========================================================
pages.domains = async function () {
  const content = document.getElementById('mainContent');
  content.innerHTML = `<div class="page-header"><h1>Domain SSO</h1><p>Quản lý cài đặt SSO theo domain và bật/tắt local auth policy</p></div>
    <div class="card">
      <div class="tabs" id="domainTabs">
        <button class="tab-btn active" data-domain-tab="list">🏷️ Danh sách Domain</button>
        <button class="tab-btn" data-domain-tab="edit">⚙️ Cấu hình SSO</button>
      </div>
      <div id="domainListTab" class="tab-panel active">
        <div class="card-header"><div class="card-title">🌐 Domain Registry</div></div>
        <div id="domainsArea"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
      </div>
      <div id="domainEditTab" class="tab-panel">
        <div class="card-header"><div class="card-title">🔐 Domain SSO Settings</div></div>
        <div class="card-body" id="domainFormBody"></div>
      </div>
    </div>`;

  renderDomainEditor();
  await loadDomainCards();

  document.querySelectorAll('#domainTabs .tab-btn').forEach(btn => {
    btn.onclick = () => {
      document.querySelectorAll('#domainTabs .tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('#mainContent .tab-panel').forEach(p => p.classList.remove('active'));
      btn.classList.add('active');
      if (btn.dataset.domainTab === 'edit') {
        document.getElementById('domainEditTab').classList.add('active');
      } else {
        document.getElementById('domainListTab').classList.add('active');
      }
    };
  });
};

async function loadDomainCards() {
  const area = document.getElementById('domainsArea');
  if (!area) return;
  area.innerHTML = '<div class="page-loading" style="padding:30px"><div class="spinner"></div></div>';

  try {
    const domains = await apiFetch(API.domains.list());
    if (!domains.length) {
      area.innerHTML = emptyKeys('Chưa có domain nào');
      return;
    }

    area.innerHTML = `<div class="domain-grid">${domains.map(domain => domainCard(domain)).join('')}</div>`;
    area.querySelectorAll('[data-domain-action]').forEach(btn => {
      btn.onclick = () => handleDomainAction(btn.dataset.domainAction, btn.dataset.id, btn.dataset.name);
    });
  } catch (e) {
    area.innerHTML = errorState('Không tải được danh sách domain');
  }
}

function domainCard(domain) {
  const ssoBadge = domain.ssoEnabled
    ? '<span class="badge badge-passkey"><span class="badge-dot"></span>SSO Enabled</span>'
    : '<span class="badge badge-suspended">Local Auth</span>';
  const mfaBadgeHtml = domain.requireMfa
    ? '<span class="badge badge-totp">MFA Required</span>'
    : '<span class="badge">MFA Optional</span>';

  return `<div class="domain-card">
    <div>
      <div class="domain-card-title">${domain.displayName}</div>
      <div class="domain-card-sub" style="font-family:monospace">${domain.domainName}</div>
      <div class="domain-card-meta">
        <span class="app-meta-item"><strong>Owner:</strong> ${domain.ownerEmail || '—'}</span>
        <span class="app-meta-item"><strong>Users:</strong> ${domain.currentUsers ?? 0}${domain.maxUsers ? ` / ${domain.maxUsers}` : ''}</span>
        <span class="app-meta-item"><strong>Login URL:</strong> ${domain.customLoginUrl || '—'}</span>
        <span class="app-meta-item"><strong>Created:</strong> ${fmtDate(domain.createdAt)}</span>
      </div>
      <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:8px">${ssoBadge}${mfaBadgeHtml}</div>
      ${domain.ssoConfig ? `<div class="domain-config-preview">${truncate(domain.ssoConfig, 120)}</div>` : ''}
    </div>
    <div style="display:flex;flex-direction:column;align-items:flex-end;gap:8px">
      ${domain.active ? '<span class="badge badge-active"><span class="badge-dot"></span>Active</span>' : '<span class="badge badge-deleted">Inactive</span>'}
      <div class="actions">
        <button class="btn btn-sm" data-domain-action="edit" data-id="${domain.id}" data-name="${domain.displayName}">Edit</button>
        <button class="btn btn-sm btn-danger" data-domain-action="delete" data-id="${domain.id}" data-name="${domain.displayName}">Delete</button>
      </div>
    </div>
  </div>`;
}

async function handleDomainAction(action, id, name) {
  if (action === 'edit') {
    const domain = await apiFetch(API.domains.detail(id));
    populateDomainForm(domain);
    showToast(`Editing ${name}`, 'info');
    const editTabButton = document.querySelector('#domainTabs .tab-btn[data-domain-tab="edit"]');
    if (editTabButton) editTabButton.click();
    return;
  }

  if (action === 'delete') {
    const ok = await showConfirm('Delete Domain', `<span style="color:var(--rose)">⚠️ Không thể hoàn tác!</span><br>Xóa domain <strong>${name}</strong>?`);
    if (!ok) return;
    await apiFetch(API.domains.delete(id), { method: 'DELETE' });
    showToast('Đã xóa domain', 'success');
    await loadDomainCards();
    return;
  }
}

function renderDomainEditor() {
  const body = document.getElementById('domainFormBody');
  if (!body) return;

  body.innerHTML = `
    <form id="domainForm" style="display:grid;gap:14px;max-width:900px">
      <input type="hidden" id="domainEditId" value="" />
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px">
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Domain Name *</label>
          <input id="domainName" class="filter-input" placeholder="acme.com" required />
        </div>
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Display Name *</label>
          <input id="domainDisplayName" class="filter-input" placeholder="Acme Corp" required />
        </div>
      </div>

      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px">
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Owner Email *</label>
          <input id="domainOwnerEmail" class="filter-input" type="email" placeholder="owner@acme.com" required />
        </div>
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Custom Login URL</label>
          <input id="domainCustomLoginUrl" class="filter-input" placeholder="https://login.acme.com" />
        </div>
      </div>

      <div>
        <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Description</label>
        <textarea id="domainDescription" class="filter-input" style="min-height:88px;resize:vertical" placeholder="Domain / organization description"></textarea>
      </div>

      <div>
        <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">SSO Config JSON</label>
        <textarea id="domainSsoConfig" class="filter-input" style="min-height:140px;resize:vertical;font-family:monospace" placeholder='{"issuer":"https://sso.acme.com","clientId":"..."}'></textarea>
      </div>

      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px">
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Logo URL</label>
          <input id="domainLogoUrl" class="filter-input" placeholder="https://acme.com/logo.png" />
        </div>
        <div>
          <label style="display:block;font-size:12px;color:var(--text-muted);margin-bottom:6px">Max Users</label>
          <input id="domainMaxUsers" class="filter-input" type="number" min="1" placeholder="100" />
        </div>
      </div>

      <div style="display:flex;gap:18px;flex-wrap:wrap">
        <label style="display:flex;align-items:center;gap:8px"><input type="checkbox" id="domainActive" checked /> Active</label>
        <label style="display:flex;align-items:center;gap:8px"><input type="checkbox" id="domainRequireMfa" /> Require MFA</label>
        <label style="display:flex;align-items:center;gap:8px"><input type="checkbox" id="domainSsoEnabled" /> SSO Enabled</label>
      </div>

      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <button type="submit" class="btn btn-success" id="domainSubmitBtn">Create Domain</button>
        <button type="button" class="btn btn-ghost" id="domainCancelEditBtn" style="display:none">Cancel Edit</button>
        <span style="font-size:12px;color:var(--text-muted)">Bật SSO sẽ chặn local register/login cho domain này.</span>
      </div>
    </form>
    <div id="domainFormResult" style="margin-top:16px"></div>`;

  document.getElementById('domainForm').addEventListener('submit', handleDomainSubmit);
  document.getElementById('domainCancelEditBtn').onclick = () => clearDomainEditState(true);
}

async function handleDomainSubmit(event) {
  event.preventDefault();
  const submitBtn = document.getElementById('domainSubmitBtn');
  const editId = document.getElementById('domainEditId').value.trim();
  const maxUsersRaw = document.getElementById('domainMaxUsers').value.trim();
  const ssoConfigRaw = document.getElementById('domainSsoConfig').value.trim();

  let parsedSsoConfig = null;
  if (ssoConfigRaw) {
    try {
      JSON.parse(ssoConfigRaw);
      parsedSsoConfig = ssoConfigRaw;
    } catch (error) {
      showToast('SSO Config phải là JSON hợp lệ', 'error');
      return;
    }
  }

  const payload = {
    domainName: document.getElementById('domainName').value.trim(),
    displayName: document.getElementById('domainDisplayName').value.trim(),
    ownerEmail: document.getElementById('domainOwnerEmail').value.trim(),
    description: document.getElementById('domainDescription').value.trim(),
    customLoginUrl: document.getElementById('domainCustomLoginUrl').value.trim(),
    logoUrl: document.getElementById('domainLogoUrl').value.trim(),
    requireMfa: document.getElementById('domainRequireMfa').checked,
    ssoEnabled: document.getElementById('domainSsoEnabled').checked,
    ssoConfig: parsedSsoConfig,
    maxUsers: maxUsersRaw ? Number(maxUsersRaw) : null
  };

  if (!payload.domainName || !payload.displayName || !payload.ownerEmail) {
    showToast('Domain name, display name và owner email là bắt buộc', 'error');
    return;
  }

  try {
    submitBtn.disabled = true;
    submitBtn.textContent = editId ? 'Đang cập nhật...' : 'Đang tạo...';

    if (editId) {
      await apiFetch(API.domains.update(editId), {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
      showToast('Đã cập nhật domain', 'success');
      clearDomainEditState(false);
    } else {
      await apiFetch(API.domains.create(), {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      showToast('Đã tạo domain', 'success');
    }

    await loadDomainCards();
  } catch (e) {
    const actionLabel = editId ? 'cập nhật' : 'tạo';
    showToast(`Không thể ${actionLabel} domain: ${e.message}`, 'error');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = document.getElementById('domainEditId').value.trim()
      ? 'Update Domain'
      : 'Create Domain';
  }
}

function populateDomainForm(domain) {
  document.getElementById('domainEditId').value = domain.id || '';
  document.getElementById('domainName').value = domain.domainName || '';
  document.getElementById('domainDisplayName').value = domain.displayName || '';
  document.getElementById('domainOwnerEmail').value = domain.ownerEmail || '';
  document.getElementById('domainDescription').value = domain.description || '';
  document.getElementById('domainCustomLoginUrl').value = domain.customLoginUrl || '';
  document.getElementById('domainLogoUrl').value = domain.logoUrl || '';
  document.getElementById('domainMaxUsers').value = domain.maxUsers ?? '';
  document.getElementById('domainActive').checked = !!domain.active;
  document.getElementById('domainRequireMfa').checked = !!domain.requireMfa;
  document.getElementById('domainSsoEnabled').checked = !!domain.ssoEnabled;
  document.getElementById('domainSsoConfig').value = domain.ssoConfig || '';

  document.getElementById('domainSubmitBtn').textContent = 'Update Domain';
  document.getElementById('domainCancelEditBtn').style.display = 'inline-flex';
}

function clearDomainEditState(clearResult) {
  const form = document.getElementById('domainForm');
  if (form) form.reset();

  document.getElementById('domainEditId').value = '';
  document.getElementById('domainActive').checked = true;
  document.getElementById('domainRequireMfa').checked = false;
  document.getElementById('domainSsoEnabled').checked = false;
  document.getElementById('domainSubmitBtn').textContent = 'Create Domain';
  document.getElementById('domainCancelEditBtn').style.display = 'none';

  if (clearResult) {
    const result = document.getElementById('domainFormResult');
    if (result) result.innerHTML = '';
  }
}

// =========================================================
// PAGE: AUDIT LOGS
// =========================================================
let auditState = { page: 0, size: 20 };

pages.audit = async function () {
  const content = document.getElementById('mainContent');
  content.innerHTML = `<div class="page-header"><h1>Audit Logs</h1><p>Lịch sử hoạt động xác thực và API requests</p></div>
    <div class="card">
      <div class="card-header"><div class="card-title">${auditIcon()} Nhật ký hoạt động</div></div>
      <div id="auditArea"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
    </div>`;
  loadAuditTable();
};

async function loadAuditTable() {
  const area = document.getElementById('auditArea');
  if (!area) return;
  try {
    const data = await apiFetch(API.audit.logs(auditState.page, auditState.size));
    const logs = data.content || [];
    if (!logs.length) { area.innerHTML = emptyKeys('Chưa có log nào'); return; }
    area.innerHTML = `
      <div class="table-wrapper">
        <table class="data-table">
          <thead><tr><th>Thời gian</th><th>Event</th><th>App</th><th>User</th><th>IP</th><th>Status</th></tr></thead>
          <tbody>${logs.map(l => `<tr>
            <td style="font-size:12px;white-space:nowrap">${fmtDateLong(l.createdAt)}</td>
            <td><span class="event-badge">${l.eventType || '—'}</span></td>
            <td style="font-size:12px">${l.appName || l.appId || '—'}</td>
            <td style="font-size:12px">${l.userEmail || '—'}</td>
            <td style="font-size:12px;font-family:monospace">${l.ipAddress || '—'}</td>
            <td>${l.success ? '<span class="event-badge success">✓ OK</span>' : '<span class="event-badge error">✗ Fail</span>'}</td>
          </tr>`).join('')}</tbody>
        </table>
      </div>
      <div class="pagination">
        <span>Trang ${data.number + 1} / ${data.totalPages} · ${data.totalElements} logs</span>
        <div class="pagination-pages">
          <button class="page-btn" onclick="auditState.page=Math.max(0,auditState.page-1);loadAuditTable()" ${data.first ? 'disabled' : ''}>‹</button>
          <button class="page-btn" onclick="auditState.page=Math.min(data.totalPages-1,auditState.page+1);loadAuditTable()" ${data.last ? 'disabled' : ''}>›</button>
        </div>
      </div>`;
  } catch (e) { area.innerHTML = errorState('Không tải được audit logs'); }
}

// =========================================================
// ICON HELPERS
// =========================================================
function userIcon() { return `<svg viewBox="0 0 20 20" fill="currentColor" style="width:20px;height:20px"><path d="M9 6a3 3 0 11-6 0 3 3 0 016 0zM17 6a3 3 0 11-6 0 3 3 0 016 0zM12.93 17c.046-.327.07-.66.07-1a6.97 6.97 0 00-1.5-4.33A5 5 0 0119 16v1h-6.07zM6 11a5 5 0 015 5v1H1v-1a5 5 0 015-5z"/></svg>`; }
function totpIcon() { return `<svg viewBox="0 0 20 20" fill="currentColor" style="width:20px;height:20px"><path fill-rule="evenodd" d="M10 1a4.5 4.5 0 00-4.5 4.5V9H5a2 2 0 00-2 2v6a2 2 0 002 2h10a2 2 0 002-2v-6a2 2 0 00-2-2h-.5V5.5A4.5 4.5 0 0010 1zm3 8V5.5a3 3 0 10-6 0V9h6z" clip-rule="evenodd"/></svg>`; }
function passkeyIcon() { return `<svg viewBox="0 0 20 20" fill="currentColor" style="width:20px;height:20px"><path fill-rule="evenodd" d="M6.625 2.655A9 9 0 0119 11a1 1 0 11-2 0 7 7 0 00-9.625-6.492 1 1 0 11-.75-1.853zM4.662 4.959A1 1 0 014.75 6.37 6.97 6.97 0 003 11a1 1 0 11-2 0 8.97 8.97 0 012.25-5.953 1 1 0 011.412-.088z" clip-rule="evenodd"/><path fill-rule="evenodd" d="M5 11a5 5 0 1110 0 1 1 0 11-2 0 3 3 0 10-6 0c0 1.677-.345 3.276-.968 4.729a1 1 0 11-1.838-.789A9.964 9.964 0 005 11zm8.921 2.012a1 1 0 01.831 1.145 19.86 19.86 0 01-.545 2.436 1 1 0 11-1.92-.558c.207-.713.371-1.445.49-2.192a1 1 0 011.144-.831z" clip-rule="evenodd"/></svg>`; }
function otpIcon() { return `<svg viewBox="0 0 20 20" fill="currentColor" style="width:20px;height:20px"><path d="M2 3a1 1 0 011-1h2.153a1 1 0 01.986.836l.74 4.435a1 1 0 01-.54 1.06l-1.548.773a11.037 11.037 0 006.105 6.105l.774-1.548a1 1 0 011.059-.54l4.435.74a1 1 0 01.836.986V17a1 1 0 01-1 1h-2C7.82 18 2 12.18 2 5V3z"/></svg>`; }
function appIcon() { return `<svg viewBox="0 0 20 20" fill="currentColor" style="width:20px;height:20px"><path fill-rule="evenodd" d="M6 2a2 2 0 00-2 2v12a2 2 0 002 2h8a2 2 0 002-2V7.414A2 2 0 0015.414 6L12 2.586A2 2 0 0010.586 2H6zm2 10a1 1 0 10-2 0v3a1 1 0 102 0v-3zm2-3a1 1 0 011 1v5a1 1 0 11-2 0v-5a1 1 0 011-1zm4-1a1 1 0 10-2 0v7a1 1 0 102 0V8z" clip-rule="evenodd"/></svg>`; }
function auditIcon() { return `<svg viewBox="0 0 20 20" fill="currentColor" style="width:20px;height:20px"><path fill-rule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" clip-rule="evenodd"/></svg>`; }

function emptyKeys(msg) {
  return `<div class="empty-state">${totpIcon()}<p>${msg}</p></div>`;
}
function errorState(msg) {
  return `<div class="empty-state"><svg viewBox="0 0 20 20" fill="currentColor" style="width:40px;height:40px;color:var(--rose);opacity:0.5"><path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/></svg><p style="color:var(--rose)">${msg}</p></div>`;
}

// =========================================================
// INIT
// =========================================================
navigate('dashboard');
