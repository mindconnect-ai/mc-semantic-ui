#!/usr/bin/env bash
#
# Sets the four repository secrets the release workflow needs, using the
# GitHub CLI. Run it once; after that a release is just
# "Actions -> release -> Run workflow".
#
#     ./scripts/setup-release-secrets.sh [owner/repo]
#
# Secrets written (see .github/workflows/release.yml and RELEASING.md):
#     MAVEN_GPG_PRIVATE_KEY   armored private key, piped straight from gpg
#     MAVEN_GPG_PASSPHRASE    its passphrase
#     CENTRAL_TOKEN_USERNAME  Central Portal user token, username half
#     CENTRAL_TOKEN_PASSWORD  Central Portal user token, password half
#
# On secrets hygiene, since this handles all four:
#   * Values are read with `read -rs` (no echo) or piped directly from gpg —
#     none of them is ever written to a file, not even a temporary one.
#   * Values go to `gh` through a pipe, never as a command-line argument, so
#     they never show up in `ps` output.
#   * Nothing is printed back. If you need to check a value, look it up at its
#     source; GitHub cannot show you a secret once it is set either.
#   * This script itself contains no secrets and is safe to commit.

set -euo pipefail

REPO="${1:-mindconnect-ai/mc-semantic-ui}"

# ── preflight ─────────────────────────────────────────────────────────────

command -v gh  >/dev/null || { echo "error: GitHub CLI (gh) not found — https://cli.github.com"; exit 1; }
command -v gpg >/dev/null || { echo "error: gpg not found"; exit 1; }

gh auth status >/dev/null 2>&1 || { echo "error: not logged in — run: gh auth login"; exit 1; }

# Fail early with a clear message rather than four confusing 404s.
gh repo view "$REPO" >/dev/null 2>&1 \
  || { echo "error: cannot access repo '$REPO' (wrong name, or no permission)"; exit 1; }

echo "Repository: $REPO"
echo

# ── 1. the signing key ────────────────────────────────────────────────────

# Auto-detect the fingerprint, but let the user confirm or override it: a
# keyring with several keys would otherwise silently pick the wrong one.
DETECTED=$(gpg --list-secret-keys --with-colons 2>/dev/null | awk -F: '/^fpr/ {print $10; exit}' || true)

if [ -n "$DETECTED" ]; then
    echo "Found signing key: $DETECTED"
    read -rp "Use this key? [Y/n] " REPLY
    case "$REPLY" in
        [nN]*) KEY_ID="" ;;
        *)     KEY_ID="$DETECTED" ;;
    esac
else
    echo "No secret key found in your keyring."
    KEY_ID=""
fi

if [ -z "$KEY_ID" ]; then
    gpg --list-secret-keys --keyid-format=long
    read -rp "Fingerprint / key id to use: " KEY_ID
    [ -n "$KEY_ID" ] || { echo "error: no key id given"; exit 1; }
fi

# Verify the key can actually be exported BEFORE touching any secrets, so a
# wrong passphrase does not leave the repo half-configured.
echo
echo "Exporting the private key (gpg will ask for its passphrase)..."
gpg --armor --export-secret-keys "$KEY_ID" > /dev/null \
  || { echo "error: could not export key '$KEY_ID'"; exit 1; }

# ── 2. collect the rest ───────────────────────────────────────────────────

echo
echo "Now the three remaining values. Nothing you type is echoed or stored."
echo

read -rsp "GPG passphrase: " GPG_PASSPHRASE; echo
[ -n "$GPG_PASSPHRASE" ] || { echo "error: passphrase is empty"; exit 1; }

echo
echo "Central Portal token — https://central.sonatype.com -> View Account"
echo "-> Generate User Token. This is NOT your Sonatype login."
read -rp  "  token username: " CENTRAL_USERNAME
read -rsp "  token password: " CENTRAL_PASSWORD; echo
[ -n "$CENTRAL_USERNAME" ] && [ -n "$CENTRAL_PASSWORD" ] \
  || { echo "error: central token incomplete"; exit 1; }

# ── 3. write them ─────────────────────────────────────────────────────────

echo
echo "Setting secrets..."

# Piped, never passed as an argument — arguments are visible in `ps`.
gpg --armor --export-secret-keys "$KEY_ID" \
  | gh secret set MAVEN_GPG_PRIVATE_KEY --repo "$REPO"
echo "  MAVEN_GPG_PRIVATE_KEY   set"

printf '%s' "$GPG_PASSPHRASE"   | gh secret set MAVEN_GPG_PASSPHRASE   --repo "$REPO"
echo "  MAVEN_GPG_PASSPHRASE    set"

printf '%s' "$CENTRAL_USERNAME" | gh secret set CENTRAL_TOKEN_USERNAME --repo "$REPO"
echo "  CENTRAL_TOKEN_USERNAME  set"

printf '%s' "$CENTRAL_PASSWORD" | gh secret set CENTRAL_TOKEN_PASSWORD --repo "$REPO"
echo "  CENTRAL_TOKEN_PASSWORD  set"

unset GPG_PASSPHRASE CENTRAL_PASSWORD

# ── 4. show what the repo has now ─────────────────────────────────────────

echo
echo "Secrets on $REPO (names and timestamps only — values are write-only):"
gh secret list --repo "$REPO"

cat <<'EOF'

Done. Remaining manual step, once:

  Publish the PUBLIC half of your key, or Central cannot verify the signature:

      gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
      gpg --keyserver keyserver.ubuntu.com --recv-keys <YOUR_KEY_ID>   # verify

  Then release with: Actions -> release -> Run workflow
EOF
