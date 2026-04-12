/* =====================================================
   Passwordless Admin - Main App JS
   ===================================================== */

const API = {
  BASE: window.location.origin,
  dashboard: { stats: () => '/admin/api/dashboard/stats' },
  users: {
    list: (p, s, q, st) => `/admin/api/users?page=${p}&size=${s}${q?'&search='+encodeURIComponent(q):''}${st?'&status='+st:''}`,
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
  },
  apps: {
    list: () => '/apps/v1/list',
    deactivate: id => `/apps/v1/${id}/deactivate`,
    activate: id => `/apps/v1/${id}/activate`,
    delete: id => `/apps/v1/${id}`,
  },
  audit: {
    logs: (p, s) => `/apps/v1/audit/logs?page=${p}&size=${s}`,
  }
};

async function apiFetch(url, options = {}) {
  try {
    const res = await fetch(url, { headers: { 'Content-Type': 'application/json' }, ...options });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('application/json')) return res.json();
    return null;
  } catch (e) {
    throw e;
  }
}

// ---- TOAST ----
function showToast(msg, type = 'info') {
  const icons = {
    success: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>`,
    error: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/></svg>`,
    info: `<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd"/></svg>`
  };
  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.innerHTML = `<span class="toast-icon">${icons[type]||icons.info}</span><span>${msg}</span>`;
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
document.getElementById('modalConfirm').onclick = () => { document.getElementById('modalOverlay').style.display='none'; _modalResolve && _modalResolve(true); };
document.getElementById('modalCancel').onclick = () => { document.getElementById('modalOverlay').style.display='none'; _modalResolve && _modalResolve(false); };
document.getElementById('modalClose').onclick = () => { document.getElementById('modalOverlay').style.display='none'; _modalResolve && _modalResolve(false); };
document.getElementById('modalOverlay').onclick = e => { if(e.target===document.getElementById('modalOverlay')){ document.getElementById('modalOverlay').style.display='none'; _modalResolve&&_modalResolve(false); } };

// ---- HELPERS ----
function fmtDate(ts) {
  if (!ts) return '—';
  const d = new Date(typeof ts === 'number' ? ts : ts);
  return d.toLocaleString('vi-VN', { dateStyle:'short', timeStyle:'short' });
}
function fmtDateLong(ts) {
  if (!ts) return '—';
  const d = new Date(ts);
  return d.toLocaleString('vi-VN', { dateStyle:'medium', timeStyle:'medium' });
}
function initials(user) {
  if (user.displayName) return user.displayName.charAt(0).toUpperCase();
  if (user.email) return user.email.charAt(0).toUpperCase();
  return '?';
}
function truncate(str, n=28) { return str && str.length > n ? str.slice(0,n)+'…' : (str||'—'); }
function statusBadge(s) {
  const map = { ACTIVE:'badge-active', SUSPENDED:'badge-suspended', DELETED:'badge-deleted', PENDING_VERIFICATION:'badge-pending' };
  const label = { ACTIVE:'Active', SUSPENDED:'Suspended', DELETED:'Deleted', PENDING_VERIFICATION:'Pending' };
  return `<span class="badge ${map[s]||''}"><span class="badge-dot"></span>${label[s]||s}</span>`;
}
function roleBadge(r) {
  const colors = { SUPER_ADMIN:'badge-totp', DOMAIN_ADMIN:'badge-passkey', USER:'badge-otp', GUEST:'' };
  return `<span class="badge ${colors[r]||''}">${r||'—'}</span>`;
}
function mfaBadge(method) {
  if (!method) return '<span class="badge">—</span>';
  const colors = { TOTP:'badge-totp', WEBAUTHN:'badge-passkey', SMS:'badge-otp', EMAIL:'badge-otp' };
  return `<span class="badge ${colors[method]||''}">${method}</span>`;
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
  const labels = { dashboard:'Dashboard', users:'Người dùng', 'user-detail':'Chi tiết người dùng', apps:'Ứng dụng', audit:'Audit Logs' };
  const bc = document.getElementById('breadcrumb');
  if (page === 'user-detail') {
    bc.innerHTML = `<span onclick="navigate('users')" style="cursor:pointer;color:var(--text-muted)">Người dùng</span> <span style="color:var(--text-muted);margin:0 6px">/</span> <span>Chi tiết</span>`;
  } else {
    bc.innerHTML = `<span>${labels[page]||page}</span>`;
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
    plugins: { legend: { position: 'bottom', labels: { color: '#8b91a8', font: {family: "'Inter', sans-serif", size: 12} } } },
    cutout: '72%',
    borderWidth: 0,
    animation: { animateScale: true }
  };

  const ctxUser = document.getElementById('userDistChart').getContext('2d');
  myUserChart = new Chart(ctxUser, {
    type: 'doughnut',
    data: {
      labels: ['Active', 'Suspended', 'Unverified/Other'],
      datasets: [{ data: [0,0,0], backgroundColor: ['#10b981', '#f59e0b', '#252a3a'], borderColor: 'transparent', hoverOffset: 4 }]
    },
    options: chartOpts
  });

  const ctxAuth = document.getElementById('authMethodsChart').getContext('2d');
  myAuthChart = new Chart(ctxAuth, {
    type: 'doughnut',
    data: {
      labels: ['TOTP', 'Passkeys', 'OTP Sessions'],
      datasets: [{ data: [0,0,0], backgroundColor: ['#6366f1', '#a78bfa', '#0ea5e9'], borderColor: 'transparent', hoverOffset: 4 }]
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
    const ratio = stats.totalUsers > 0 ? Math.round((stats.mfaEnabledUsers/stats.totalUsers)*100) : 0;
    document.getElementById('statMfaRatio').textContent = ratio + '%';
    document.getElementById('statTotalAuthKeys').textContent = ((stats.totalTotpKeys||0) + (stats.totalPasskeys||0));
    
    // update charts dynamically without re-rendering everything
    const active = stats.activeUsers || 0;
    const suspended = stats.suspendedUsers || 0;
    const other = Math.max(0, (stats.totalUsers||0) - active - suspended);
    
    if(myUserChart && (myUserChart.data.datasets[0].data[0] !== active || myUserChart.data.datasets[0].data[1] !== suspended)) {
      myUserChart.data.datasets[0].data = [active, suspended, other];
      myUserChart.update();
    }
    
    if(myAuthChart && (myAuthChart.data.datasets[0].data[0] !== stats.totalTotpKeys || myAuthChart.data.datasets[0].data[1] !== stats.totalPasskeys)) {
      myAuthChart.data.datasets[0].data = [stats.totalTotpKeys||0, stats.totalPasskeys||0, stats.totalOtpSessions||0];
      myAuthChart.update();
    }
    
    const now = new Date();
    document.getElementById('lastUpdateTime').textContent = now.toLocaleTimeString('vi-VN');
    document.getElementById('badge-users').textContent = stats.totalUsers || 0;
  } catch(e) {
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
let usersState = { page: 0, size:10, search:'', status:'' };

pages.users = async function (params={}) {
  if (params.reset) usersState = { page:0, size:10, search:'', status:'' };
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
          <option value="ACTIVE" ${usersState.status==='ACTIVE'?'selected':''}>Active</option>
          <option value="SUSPENDED" ${usersState.status==='SUSPENDED'?'selected':''}>Suspended</option>
          <option value="PENDING_VERIFICATION" ${usersState.status==='PENDING_VERIFICATION'?'selected':''}>Pending</option>
          <option value="DELETED" ${usersState.status==='DELETED'?'selected':''}>Deleted</option>
        </select>
      </div>
      <div id="usersTableArea"><div class="page-loading"><div class="spinner"></div></div></div>
    </div>`;

  // Bind filters
  let searchTimer;
  document.getElementById('searchInput').addEventListener('input', e => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { usersState.search = e.target.value; usersState.page=0; loadUsersTable(); }, 400);
  });
  document.getElementById('globalSearch').addEventListener('input', e => {
    document.getElementById('searchInput').value = e.target.value;
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { usersState.search = e.target.value; usersState.page=0; loadUsersTable(); }, 400);
  });
  document.getElementById('statusFilter').addEventListener('change', e => {
    usersState.status = e.target.value; usersState.page=0; loadUsersTable();
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
  } catch(e) {
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
          <div class="primary" style="font-size:13.5px">${truncate(u.displayName||u.email, 30)}</div>
          <div style="font-size:12px;color:var(--text-muted)">${truncate(u.email, 32)}</div>
        </div>
      </div>
    </td>
    <td>${statusBadge(u.status)}${u.locked?` <span class="badge badge-deleted">🔒 Locked</span>`:''}</td>
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
  let btns = `<button class="page-btn" data-p="${current-1}" ${current===0?'disabled':''}>‹</button>`;
  for (let i = 0; i < total; i++) {
    if (total > 7 && Math.abs(i - current) > 2 && i !== 0 && i !== total-1) {
      if (i === 1 || i === total-2) btns += `<span style="padding:0 4px;color:var(--text-muted)">…</span>`;
      continue;
    }
    btns += `<button class="page-btn ${i===current?'active':''}" data-p="${i}">${i+1}</button>`;
  }
  btns += `<button class="page-btn" data-p="${current+1}" ${current>=total-1?'disabled':''}>›</button>`;
  return btns;
}

async function suspendUser(id, email) {
  const ok = await showConfirm('Đình chỉ tài khoản', `Bạn có chắc muốn đình chỉ tài khoản <strong>${email}</strong>?`);
  if (!ok) return;
  try {
    await apiFetch(API.users.suspend(id), { method:'POST' });
    showToast('Đã đình chỉ tài khoản', 'success');
    loadUsersTable();
  } catch(e) { showToast('Có lỗi xảy ra', 'error'); }
}

async function activateUser(id, email) {
  const ok = await showConfirm('Kích hoạt tài khoản', `Kích hoạt lại tài khoản <strong>${email}</strong>?`);
  if (!ok) return;
  try {
    await apiFetch(API.users.activate(id), { method:'POST' });
    showToast('Đã kích hoạt tài khoản', 'success');
    loadUsersTable();
  } catch(e) { showToast('Có lỗi xảy ra', 'error'); }
}

async function deleteUser(id, email) {
  const ok = await showConfirm('Xóa tài khoản', `<div style="color:var(--rose);margin-bottom:8px">⚠️ Hành động này không thể hoàn tác!</div>Bạn có chắc muốn xóa tài khoản <strong>${email}</strong>?`);
  if (!ok) return;
  try {
    await apiFetch(API.users.delete(id), { method:'DELETE' });
    showToast('Đã xóa tài khoản', 'success');
    loadUsersTable();
  } catch(e) { showToast('Có lỗi xảy ra', 'error'); }
}

// =========================================================
// PAGE: USER DETAIL
// =========================================================
pages['user-detail'] = async function({ id }) {
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
          <div class="user-profile-name">${user.displayName || (user.firstName||'') + ' ' + (user.lastName||'') || '—'}</div>
          <div class="user-profile-email">${user.email}</div>
          <div class="user-profile-badges">
            ${statusBadge(user.status)}
            ${roleBadge(user.role)}
            ${user.authMethods && user.authMethods.length > 0 ? user.authMethods.map(m => mfaBadge(m)).join(' ') : ''}
            ${user.locked ? '<span class="badge badge-deleted">🔒 Locked</span>' : ''}
          </div>
          <div class="user-meta-list">
            <div class="user-meta-item"><span class="meta-key">ID</span><span class="meta-value" style="font-size:11px;font-family:monospace">${user.id}</span></div>
            <div class="user-meta-item"><span class="meta-key">Phone</span><span class="meta-value">${user.phoneNumber||'—'}</span></div>
            <div class="user-meta-item"><span class="meta-key">Locale</span><span class="meta-value">${user.locale||'—'}</span></div>
            <div class="user-meta-item"><span class="meta-key">Timezone</span><span class="meta-value">${user.timezone||'—'}</span></div>
            <div class="user-meta-item"><span class="meta-key">Tạo lúc</span><span class="meta-value">${fmtDate(user.createdAt)}</span></div>
            <div class="user-meta-item"><span class="meta-key">Login cuối</span><span class="meta-value">${user.lastLoginAt ? fmtDate(user.lastLoginAt) : '<span style="color:var(--text-muted)">Chưa Login Lần Nào</span>'}</span></div>
            <div class="user-meta-item"><span class="meta-key">IP cuối</span><span class="meta-value">${user.lastLoginIp||'—'}</span></div>
            <div class="user-meta-item"><span class="meta-key">Sai mật khẩu</span><span class="meta-value" style="color:${user.failedLoginAttempts>0?'var(--amber)':'inherit'}">${user.failedLoginAttempts}</span></div>
            ${user.lockedUntil?`<div class="user-meta-item"><span class="meta-key">Khóa đến</span><span class="meta-value" style="color:var(--rose)">${fmtDate(user.lockedUntil)}</span></div>`:''}
          </div>
          <div class="user-actions-profile">
            ${user.status==='ACTIVE'
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
              <button class="tab-btn active" data-tab="totp">🔑 TOTP Keys <span class="tab-count" id="tc-totp">${user.totpKeyCount||0}</span></button>
              <button class="tab-btn" data-tab="passkeys">🔐 Passkeys <span class="tab-count" id="tc-pass">${user.passkeyCount||0}</span></button>
              <button class="tab-btn" data-tab="otp">📱 OTP Sessions</button>
            </div>
            <div id="tab-totp" class="tab-panel active"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
            <div id="tab-passkeys" class="tab-panel"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
            <div id="tab-otp" class="tab-panel"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div>
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

  } catch(e) {
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
          <div class="stat-icon" style="width:36px;height:36px;background:var(--indigo-alpha)">${totpIcon().replace('20px','16px')}</div>
          <div class="key-info">
            <div class="key-name">${k.username}</div>
            <div class="key-sub">ID: <code style="font-size:11px">${k.id}</code> · Secret: ${k.hasSecret ? '●●●●●●●●':'Không có'}</div>
          </div>
        </div>
        <button class="btn btn-sm btn-danger" data-kid="${k.id}" onclick="deleteTotpKey('${userId}','${k.id}','${k.username}')">
          <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
          Xóa
        </button>
      </div>`).join('')}</div>`;
  } catch(e) { panel.innerHTML = errorState('Không tải được TOTP keys'); }
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
            <div class="stat-icon" style="width:36px;height:36px;background:rgba(139,92,246,0.12)">${passkeyIcon().replace('currentColor','#a78bfa').replace('20px','16px')}</div>
            <div class="key-info">
              <div class="key-name">${k.deviceName || 'Thiết bị không xác định'}</div>
              <div class="key-sub">${k.username} · ${k.attestationType||'unknown'} · Dùng lần cuối: ${fmtDate(k.lastUsedAt)}</div>
            </div>
          </div>
          <button class="btn btn-sm btn-danger" onclick="deletePasskey('${userId}',${k.id},'${k.deviceName||'thiết bị này'}')">
            <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
            Xóa
          </button>
        </div>
        <div class="info-grid" style="border-radius:var(--radius-sm);overflow:hidden">
          <div class="info-item"><div class="info-label">Credential ID</div><div class="info-value" style="font-size:11px;font-family:monospace">${truncate(k.credentialId,36)}</div></div>
          <div class="info-item"><div class="info-label">Counter</div><div class="info-value">${k.counter??'—'}</div></div>
          <div class="info-item"><div class="info-label">Backup</div><div class="info-value">${k.backedUp?'✅ Có':'❌ Không'}</div></div>
          <div class="info-item"><div class="info-label">Đăng ký lúc</div><div class="info-value">${fmtDate(k.createdAt)}</div></div>
        </div>
      </div>`).join('')}</div>`;
  } catch(e) { panel.innerHTML = errorState('Không tải được Passkeys'); }
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
              <td><code style="font-size:11px">${String(s.sessionId).substring(0,14)}…</code></td>
              <td class="primary">${s.destination||'—'}</td>
              <td>${s.attempts??0}</td>
              <td style="font-size:12px">${fmtDate(s.lastSentAt)}</td>
              <td>${s.expired ? '<span class="badge badge-deleted">Hết hạn</span>' : '<span class="badge badge-active">Còn hạn</span>'}</td>
            </tr>`).join('')}
          </tbody>
        </table>
      </div>`;
  } catch(e) { panel.innerHTML = errorState('Không tải được OTP sessions'); }
}

async function deleteTotpKey(userId, keyId, username) {
  const ok = await showConfirm('Xóa TOTP Key', `Xóa TOTP key của <strong>${username}</strong>? Người dùng sẽ cần đăng ký lại Google Authenticator.`);
  if (!ok) return;
  try {
    await apiFetch(API.users.deleteTotpKey(userId, keyId), { method:'DELETE' });
    showToast('Đã xóa TOTP key', 'success');
    loadTotpTab(userId);
  } catch(e) { showToast('Lỗi khi xóa TOTP key', 'error'); }
}

async function deletePasskey(userId, keyId, deviceName) {
  const ok = await showConfirm('Xóa Passkey', `Xóa Passkey <strong>${deviceName}</strong>? Người dùng sẽ không thể dùng thiết bị này để đăng nhập.`);
  if (!ok) return;
  try {
    await apiFetch(API.users.deletePasskey(userId, keyId), { method:'DELETE' });
    showToast('Đã xóa Passkey', 'success');
    loadPasskeysTab(userId);
  } catch(e) { showToast('Lỗi khi xóa Passkey', 'error'); }
}

// =========================================================
// PAGE: APPS
// =========================================================
pages.apps = async function () {
  const content = document.getElementById('mainContent');
  content.innerHTML = `<div class="page-header"><h1>Ứng dụng</h1><p>Quản lý các ứng dụng đã đăng ký và API keys</p></div>
    <div class="card"><div class="card-header"><div class="card-title">${appIcon()} Danh sách ứng dụng</div></div><div id="appsArea"><div class="page-loading" style="padding:30px"><div class="spinner"></div></div></div></div>`;
  try {
    const apps = await apiFetch(API.apps.list());
    if (!apps.length) { document.getElementById('appsArea').innerHTML = emptyKeys('Chưa có ứng dụng nào đăng ký'); return; }
    document.getElementById('appsArea').innerHTML = `<div class="apps-grid">${apps.map(a => appCard(a)).join('')}</div>`;
    document.querySelectorAll('[data-app-action]').forEach(btn => {
      btn.onclick = () => handleAppAction(btn.dataset.appAction, btn.dataset.id, btn.dataset.name);
    });
  } catch(e) { document.getElementById('appsArea').innerHTML = errorState('Không tải được danh sách ứng dụng'); }
};

function appCard(a) {
  return `<div class="app-card">
    <div>
      <div class="app-card-name">${a.name}</div>
      <div class="app-card-desc">${a.description||'Không có mô tả'}</div>
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
        <button class="btn btn-sm btn-danger" data-app-action="delete" data-id="${a.id}" data-name="${a.name}">Xóa</button>
      </div>
    </div>
  </div>`;
}

async function handleAppAction(action, id, name) {
  if (action === 'deactivate') {
    const ok = await showConfirm('Deactivate App', `Vô hiệu hóa ứng dụng <strong>${name}</strong>?`);
    if (!ok) return;
    await apiFetch(API.apps.deactivate(id), { method:'POST' });
    showToast('Đã vô hiệu hóa ứng dụng', 'success');
    pages.apps();
  } else if (action === 'activate') {
    await apiFetch(API.apps.activate(id), { method:'POST' });
    showToast('Đã kích hoạt ứng dụng', 'success');
    pages.apps();
  } else if (action === 'delete') {
    const ok = await showConfirm('Xóa App', `<span style="color:var(--rose)">⚠️ Không thể hoàn tác!</span><br>Xóa ứng dụng <strong>${name}</strong>?`);
    if (!ok) return;
    await apiFetch(API.apps.delete(id), { method:'DELETE' });
    showToast('Đã xóa ứng dụng', 'success');
    pages.apps();
  }
}

// =========================================================
// PAGE: AUDIT LOGS
// =========================================================
let auditState = { page:0, size:20 };

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
            <td><span class="event-badge">${l.eventType||'—'}</span></td>
            <td style="font-size:12px">${l.appName||l.appId||'—'}</td>
            <td style="font-size:12px">${l.userEmail||'—'}</td>
            <td style="font-size:12px;font-family:monospace">${l.ipAddress||'—'}</td>
            <td>${l.success ? '<span class="event-badge success">✓ OK</span>' : '<span class="event-badge error">✗ Fail</span>'}</td>
          </tr>`).join('')}</tbody>
        </table>
      </div>
      <div class="pagination">
        <span>Trang ${data.number+1} / ${data.totalPages} · ${data.totalElements} logs</span>
        <div class="pagination-pages">
          <button class="page-btn" onclick="auditState.page=Math.max(0,auditState.page-1);loadAuditTable()" ${data.first?'disabled':''}>‹</button>
          <button class="page-btn" onclick="auditState.page=Math.min(data.totalPages-1,auditState.page+1);loadAuditTable()" ${data.last?'disabled':''}>›</button>
        </div>
      </div>`;
  } catch(e) { area.innerHTML = errorState('Không tải được audit logs'); }
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
