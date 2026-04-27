import React, { useEffect, useMemo, useState } from "react";
import { PageShell } from "../../components/PageShell";
import { useAppContext } from "../../context/AppContext";
import { toLocalTime } from "../../lib/format";
import { api } from "../../services/api";
import { AdminUser, AuthMethod } from "../../types";

export function AdminUsersPage() {
  const { addToast } = useAppContext();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [search, setSearch] = useState("");
  const [domain, setDomain] = useState("");
  const [status, setStatus] = useState("");

  const [newUser, setNewUser] = useState({
    email: "",
    firstName: "",
    lastName: "",
    domain: "example.com",
    mfaEnabled: true,
    preferredMethod: "OTP" as AuthMethod,
  });

  const load = async () => {
    const data = await api.listUsers({ search, domain, status });
    setUsers(data);
  };

  useEffect(() => {
    void load();
  }, [search, domain, status]);

  const domains = useMemo(() => [...new Set(users.map((u) => u.domain))], [users]);

  const createUser = async (event: React.FormEvent) => {
    event.preventDefault();
    await api.createUser(newUser);
    addToast("success", "Đã tạo user mới.");
    setNewUser({ ...newUser, email: "", firstName: "", lastName: "" });
    await load();
  };

  const lockToggle = async (user: AdminUser) => {
    if (user.status === "ACTIVE") {
      await api.lockUser(user.userId);
      addToast("info", `Đã khóa ${user.email}`);
    } else {
      await api.unlockUser(user.userId);
      addToast("success", `Đã mở khóa ${user.email}`);
    }
    await load();
  };

  const resetMfa = async (user: AdminUser) => {
    await api.resetUserMfa(user.userId);
    addToast("info", `Đã reset MFA của ${user.email}`);
  };

  return (
    <PageShell mode="admin" title="Quản lý người dùng" subtitle="Tạo user, lọc danh sách, khóa/mở khóa, reset MFA.">
      <section className="panel">
        <h2>Bộ lọc</h2>
        <div className="inline-form">
          <input placeholder="Tìm email, tên" value={search} onChange={(e) => setSearch(e.target.value)} />
          <select value={domain} onChange={(e) => setDomain(e.target.value)}>
            <option value="">Tất cả domain</option>
            {domains.map((item) => (
              <option key={item} value={item}>{item}</option>
            ))}
          </select>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Tất cả trạng thái</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="LOCKED">LOCKED</option>
          </select>
        </div>
      </section>

      <section className="panel">
        <h2>Thêm user</h2>
        <form className="inline-form" onSubmit={createUser}>
          <input placeholder="Email" value={newUser.email} onChange={(e) => setNewUser({ ...newUser, email: e.target.value })} />
          <input placeholder="First name" value={newUser.firstName} onChange={(e) => setNewUser({ ...newUser, firstName: e.target.value })} />
          <input placeholder="Last name" value={newUser.lastName} onChange={(e) => setNewUser({ ...newUser, lastName: e.target.value })} />
          <input placeholder="Domain" value={newUser.domain} onChange={(e) => setNewUser({ ...newUser, domain: e.target.value })} />
          <select value={newUser.preferredMethod} onChange={(e) => setNewUser({ ...newUser, preferredMethod: e.target.value as AuthMethod })}>
            <option value="OTP">OTP</option>
            <option value="TOTP">TOTP</option>
            <option value="WEBAUTHN">WEBAUTHN</option>
          </select>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={newUser.mfaEnabled}
              onChange={(e) => setNewUser({ ...newUser, mfaEnabled: e.target.checked })}
            />
            MFA required
          </label>
          <button type="submit">Thêm user</button>
        </form>
      </section>

      <section className="panel">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Email</th>
                <th>Name</th>
                <th>Domain</th>
                <th>MFA</th>
                <th>Status</th>
                <th>Last login</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.userId}>
                  <td>{user.email}</td>
                  <td>{user.firstName} {user.lastName}</td>
                  <td>{user.domain}</td>
                  <td>{user.mfaEnabled ? "Enabled" : "Disabled"}</td>
                  <td>{user.status}</td>
                  <td>{toLocalTime(user.lastLoginAt)}</td>
                  <td>
                    <div className="inline-actions">
                      <button type="button" onClick={() => lockToggle(user)}>
                        {user.status === "ACTIVE" ? "Lock" : "Unlock"}
                      </button>
                      <button type="button" onClick={() => resetMfa(user)}>Reset MFA</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </PageShell>
  );
}
