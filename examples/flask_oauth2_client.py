import os
import base64
import hashlib
import secrets
import requests
from urllib.parse import urlencode
from flask import Flask, redirect, request, session, url_for, jsonify

app = Flask(__name__)
app.secret_key = "super-secret-key-change-this"

# ================= CONFIG =================
IDP_BASE_URL = "https://passwordless.actvn"   # URL IdP của bạn
CLIENT_ID = "my-app"
REDIRECT_URI = "http://192.168.119.146:5000/callback"

AUTHORIZE_URL = f"{IDP_BASE_URL}/oauth2/authorize"
TOKEN_URL = f"{IDP_BASE_URL}/oauth2/token"
USERINFO_URL = f"{IDP_BASE_URL}/oauth2/userinfo"
VERIFY_CERT = "/home/pvd/passwordless-final/passwordless/nginx/certs/nginx-selfsigned.crt"


# ================= PKCE =================

def generate_pkce():
    """
    Sinh PKCE code_verifier (32 random bytes, base64url no-padding)
    và code_challenge = BASE64URL(SHA256(verifier)).
    """
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(32)).rstrip(b"=").decode()
    digest    = hashlib.sha256(verifier.encode("ascii")).digest()
    challenge = base64.urlsafe_b64encode(digest).rstrip(b"=").decode()
    return verifier, challenge


def generate_state():
    """CSRF-protection token — URL-safe, không có padding '='."""
    return secrets.token_urlsafe(16)


def generate_nonce():
    """
    OIDC nonce — bắt buộc khi scope chứa 'openid'.
    IdP sẽ trả lỗi 'nonce is required' nếu thiếu.
    """
    return secrets.token_urlsafe(16)


# ================= ROUTES =================

@app.route("/")
def home():
    if "access_token" in session:
        return """
        <h2>✅ Logged in</h2>
        <a href='/profile'>View Profile</a><br>
        <a href='/logout'>Logout</a>
        """
    return "<a href='/login'>🔐 Login with IdP</a>"


# ===== LOGIN =====

@app.route("/login")
def login():
    verifier, challenge = generate_pkce()
    state = generate_state()
    nonce = generate_nonce()          # ← IdP bắt buộc nếu scope có "openid"

    # Lưu vào session để validate ở /callback
    session["verifier"]    = verifier
    session["state"]       = state
    session["nonce"]       = nonce

    # Dùng urlencode để đảm bảo mọi ký tự đặc biệt được encode đúng
    params = urlencode({
        "response_type":         "code",
        "client_id":             CLIENT_ID,
        "redirect_uri":          REDIRECT_URI,
        "scope":                 "openid profile email",
        "code_challenge":        challenge,
        "code_challenge_method": "S256",
        "state":                 state,
        "nonce":                 nonce,
    })
    return redirect(f"{AUTHORIZE_URL}?{params}")


# ===== CALLBACK =====

@app.route("/callback")
def callback():
    # 1. IdP báo lỗi (VD: user bấm "Deny" trên consent screen)
    error = request.args.get("error")
    if error:
        desc = request.args.get("error_description", "no description")
        return f"<h3>❌ Authorization error</h3><p>{error}: {desc}</p><a href='/'>Home</a>", 400

    code  = request.args.get("code")
    state = request.args.get("state")

    if not code:
        return "Missing authorization code", 400

    # 2. CSRF: validate state
    saved_state = session.pop("state", None)
    if not state or state != saved_state:
        return "Invalid state (possible CSRF)", 400

    # 3. Lấy verifier, pop khỏi session để tránh reuse
    verifier = session.pop("verifier", None)
    if not verifier:
        return "Session expired, please login again", 400

    # 4. Exchange code → tokens
    try:
        token_res = requests.post(TOKEN_URL, data={
            "grant_type":    "authorization_code",
            "client_id":     CLIENT_ID,
            "code":          code,
            "redirect_uri":  REDIRECT_URI,
            "code_verifier": verifier,
        }, verify=VERIFY_CERT, timeout=10)
    except requests.exceptions.SSLError as exc:
        return f"SSL error connecting to IdP: {exc}", 502
    except requests.exceptions.ConnectionError as exc:
        return f"Cannot reach IdP: {exc}", 502

    if token_res.status_code != 200:
        return f"Token error ({token_res.status_code}): {token_res.text}", 400

    tokens = token_res.json()
    if "access_token" not in tokens:
        return f"No access_token in response: {tokens}", 400

    session["access_token"]  = tokens["access_token"]
    session["id_token"]      = tokens.get("id_token", "")
    session["refresh_token"] = tokens.get("refresh_token", "")

    return redirect(url_for("profile"))


# ===== PROFILE =====

@app.route("/profile")
def profile():
    access_token = session.get("access_token")
    if not access_token:
        return redirect(url_for("login"))

    try:
        res = requests.get(USERINFO_URL, headers={
            "Authorization": f"Bearer {access_token}"
        }, verify=VERIFY_CERT, timeout=10)
    except requests.exceptions.ConnectionError as exc:
        return f"Cannot reach IdP: {exc}", 502

    # Token hết hạn → redirect login
    if res.status_code == 401:
        session.clear()
        return redirect(url_for("login"))

    if res.status_code != 200:
        return f"UserInfo error ({res.status_code}): {res.text}", 400

    return jsonify(res.json())


# ===== LOGOUT =====

@app.route("/logout")
def logout():
    session.clear()
    return redirect(url_for("home"))


# ================= RUN =================
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
