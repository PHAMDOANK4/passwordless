import React from "react";
import { useAppContext } from "../context/AppContext";

export function ToastHost() {
  const { toasts, dismissToast } = useAppContext();

  return (
    <div className="toast-host" aria-live="polite">
      {toasts.map((toast) => (
        <button
          key={toast.id}
          className={`toast toast-${toast.type}`}
          onClick={() => dismissToast(toast.id)}
          type="button"
        >
          {toast.message}
        </button>
      ))}
    </div>
  );
}
