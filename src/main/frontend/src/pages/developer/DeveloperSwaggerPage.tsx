import React from "react";
import { PageShell } from "../../components/PageShell";

export function DeveloperSwaggerPage() {
  return (
    <PageShell mode="developer" title="API Documentation & Swagger" subtitle="Nhúng OpenAPI và cho phép thử API bằng token hoặc API key.">
      <section className="panel">
        <h2>OpenAPI endpoints</h2>
        <ul>
          <li>/auth/*</li>
          <li>/oauth2/*</li>
          <li>/otp/v1/*</li>
          <li>/totp/v1/*</li>
        </ul>
        <p>
          Ở môi trường triển khai thực, bạn có thể nhúng Swagger UI bằng iframe hoặc mount bundle Swagger tại route
          <code> /developer/swagger</code>.
        </p>
        <iframe
          title="Swagger placeholder"
          srcDoc="<html><body style='font-family: sans-serif; padding: 20px;'><h3>Swagger UI Placeholder</h3><p>Bind this page to /v3/api-docs or docs/openapi.yaml in deployment.</p></body></html>"
          className="swagger-frame"
        />
      </section>
    </PageShell>
  );
}
