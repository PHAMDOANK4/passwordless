function unwrapByteField(value) {
  if (value && typeof value === "object" && "value" in value) {
    return value.value;
  }
  return value;
}

export function base64ToBytes(value) {
  const encoded = String(value || "")
    .replace(/-/g, "+")
    .replace(/_/g, "/");
  const padded = encoded + "=".repeat((4 - (encoded.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export function bytesToBase64Url(bytes) {
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

export function normalizeCreationOptions(input) {
  const options = structuredClone(input);
  options.challenge = base64ToBytes(unwrapByteField(options.challenge));

  if (!options.user || !options.user.id) {
    throw new Error("Invalid registration challenge: user.id is missing.");
  }
  options.user.id = base64ToBytes(unwrapByteField(options.user.id));

  if (Array.isArray(options.excludeCredentials)) {
    options.excludeCredentials = options.excludeCredentials.map((credential) => ({
      ...credential,
      id: base64ToBytes(unwrapByteField(credential.id))
    }));
  }

  return options;
}

export function normalizeRequestOptions(input) {
  const options = structuredClone(input);
  options.challenge = base64ToBytes(unwrapByteField(options.challenge));

  if (Array.isArray(options.allowCredentials)) {
    options.allowCredentials = options.allowCredentials.map((credential) => ({
      ...credential,
      id: base64ToBytes(unwrapByteField(credential.id))
    }));
  }

  return options;
}

export function serializeCredential(credential) {
  return {
    id: credential.id,
    rawId: bytesToBase64Url(new Uint8Array(credential.rawId)),
    type: credential.type,
    response: {
      attestationObject: bytesToBase64Url(new Uint8Array(credential.response.attestationObject)),
      clientDataJSON: bytesToBase64Url(new Uint8Array(credential.response.clientDataJSON))
    }
  };
}

export function serializeAssertion(assertion) {
  return {
    id: assertion.id,
    rawId: bytesToBase64Url(new Uint8Array(assertion.rawId)),
    type: assertion.type,
    response: {
      authenticatorData: bytesToBase64Url(new Uint8Array(assertion.response.authenticatorData)),
      clientDataJSON: bytesToBase64Url(new Uint8Array(assertion.response.clientDataJSON)),
      signature: bytesToBase64Url(new Uint8Array(assertion.response.signature)),
      userHandle: assertion.response.userHandle
        ? bytesToBase64Url(new Uint8Array(assertion.response.userHandle))
        : ""
    }
  };
}
