import React, { useState } from "react";
import { PageShell } from "../../components/PageShell";
import { decodeJwtPayload } from "../../lib/format";
import { api, fakeJwt } from "../../services/api";
import { TokenInspection } from "../../types";

export function TokenInspectorPage() {
  const [token, setToken] = useState(fakeJwt());
  const [result, setResult] = useState<TokenInspection | null>(null);
  const [jwtPayload, setJwtPayload] = useState<Record<string, unknown> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const inspect = async () => {
    try {
      setError(null);
      setJwtPayload(decodeJwtPayload(token));
      setResult(await api.inspectToken(token));
    } catch (e) {
      setError((e as Error).message);
      setResult(null);
      setJwtPayload(null);
    }
  };

  return (
    <PageShell mode="developer" title="Token Inspector" subtitle="Decode JWT claims và kiểm tra active qua introspection.">
      <section className="panel form-grid">
        <label>
          Access token
          <textarea value={token} onChange={(e) => setToken(e.target.value)} rows={6} />
        </label>
        <button type="button" onClick={inspect}>Kiểm tra token</button>
        {error ? <p className="danger-text">{error}</p> : null}
      </section>

      {result ? (
        <section className="panel-grid two-col">
          <article className="panel">
            <h2>Introspection</h2>
            <p>Active: {result.active ? "true" : "false"}</p>
            <pre>{JSON.stringify(result.claims, null, 2)}</pre>
          </article>
          <article className="panel">
            <h2>Decoded JWT payload</h2>
            <pre>{JSON.stringify(jwtPayload, null, 2)}</pre>
          </article>
        </section>
      ) : null}
    </PageShell>
  );
}
