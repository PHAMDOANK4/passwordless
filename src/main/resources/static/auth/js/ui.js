export function byId(id) {
  return document.getElementById(id);
}

export function setStatus(element, message, kind = "") {
  if (!element) {
    return;
  }
  element.textContent = message;
  element.className = "status" + (kind ? ` ${kind}` : "");
}

export function setText(element, value) {
  if (!element) {
    return;
  }
  element.textContent = value;
}

export function setLoading(button, isLoading, label) {
  if (!button) {
    return;
  }
  if (!button.dataset.defaultLabel) {
    button.dataset.defaultLabel = button.textContent;
  }
  button.disabled = isLoading;
  button.textContent = isLoading
    ? label || "Working..."
    : button.dataset.defaultLabel;
}

export function emptyToNull(value) {
  const normalized = String(value || "").trim();
  return normalized ? normalized : null;
}

export function show(element) {
  if (element) {
    element.hidden = false;
  }
}

export function hide(element) {
  if (element) {
    element.hidden = true;
  }
}
