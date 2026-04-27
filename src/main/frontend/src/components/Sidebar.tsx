import React from "react";
import { NavLink } from "react-router-dom";

interface SidebarProps {
  mode: "user" | "admin" | "developer";
}

const menuMap = {
  user: [
    { to: "/idp", label: "Đăng nhập IdP" },
    { to: "/mfa", label: "MFA Challenge" },
    { to: "/profile", label: "User Profile" },
    { to: "/logout", label: "Logout" },
  ],
  admin: [
    { to: "/admin/dashboard", label: "Dashboard" },
    { to: "/admin/users", label: "Users" },
    { to: "/admin/clients", label: "OAuth Clients" },
    { to: "/admin/domains", label: "Domains" },
    { to: "/admin/system", label: "System Config" },
    { to: "/admin/audit", label: "Audit Logs" },
    { to: "/admin/api-keys", label: "API Keys" },
  ],
  developer: [
    { to: "/developer/register-client", label: "Register Client" },
    { to: "/developer/swagger", label: "Swagger" },
    { to: "/developer/token-inspector", label: "Token Inspector" },
    { to: "/oauth/consent", label: "OAuth Consent" },
    { to: "/oauth/error", label: "OAuth Error" },
  ],
};

export function Sidebar({ mode }: SidebarProps) {
  return (
    <aside className="sidebar">
      <h2>{mode === "admin" ? "Admin Console" : mode === "developer" ? "Dev Portal" : "User Portal"}</h2>
      <ul>
        {menuMap[mode].map((item) => (
          <li key={item.to}>
            <NavLink to={item.to}>{item.label}</NavLink>
          </li>
        ))}
      </ul>
    </aside>
  );
}
