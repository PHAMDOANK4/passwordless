import React from "react";
import { Navbar } from "./Navbar";
import { Sidebar } from "./Sidebar";

interface PageShellProps {
  mode: "user" | "admin" | "developer";
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

export function PageShell({ mode, title, subtitle, children }: PageShellProps) {
  return (
    <div className="app-frame">
      <Navbar />
      <div className="body-grid">
        <Sidebar mode={mode} />
        <main className="content-panel">
          <section className="hero-card">
            <h1>{title}</h1>
            {subtitle ? <p>{subtitle}</p> : null}
          </section>
          {children}
        </main>
      </div>
    </div>
  );
}
