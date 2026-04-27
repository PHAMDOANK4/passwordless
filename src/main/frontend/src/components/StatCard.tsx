import React from "react";

interface StatCardProps {
  label: string;
  value: string | number;
  helper?: string;
}

export function StatCard({ label, value, helper }: StatCardProps) {
  return (
    <article className="stat-card">
      <p className="stat-label">{label}</p>
      <h3>{value}</h3>
      {helper ? <p className="stat-helper">{helper}</p> : null}
    </article>
  );
}
