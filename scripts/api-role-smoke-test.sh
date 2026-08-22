#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
WARD_ID="${WARD_ID:-00000000-0000-0000-0000-000000000001}"
PREFIX="${REGISTER_NID_PREFIX:-smoke-$(date +%s)}"

declare -A EMAIL=(
  [CENTRAL_ADMIN]="${CENTRAL_ADMIN_EMAIL:-}"
  [PROVINCE_ADMIN]="${PROVINCE_ADMIN_EMAIL:-}"
  [LOCAL_BODY_ADMIN]="${LOCAL_BODY_ADMIN_EMAIL:-}"
  [WARD_ADMIN]="${WARD_ADMIN_EMAIL:-}"
)
declare -A PASSWORD=(
  [CENTRAL_ADMIN]="${CENTRAL_ADMIN_PASSWORD:-}"
  [PROVINCE_ADMIN]="${PROVINCE_ADMIN_PASSWORD:-}"
  [LOCAL_BODY_ADMIN]="${LOCAL_BODY_ADMIN_PASSWORD:-}"
  [WARD_ADMIN]="${WARD_ADMIN_PASSWORD:-}"
)

login() {
  local role="$1"
  [[ -n "${EMAIL[$role]}" && -n "${PASSWORD[$role]}" ]] || {
    echo "Missing ${role}_EMAIL or ${role}_PASSWORD" >&2
    exit 2
  }
  curl -fsS "$BASE_URL/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "$(printf '{\"email\":\"%s\",\"password\":\"%s\"}' "${EMAIL[$role]}" "${PASSWORD[$role]}")"
}

for role in CENTRAL_ADMIN PROVINCE_ADMIN LOCAL_BODY_ADMIN WARD_ADMIN; do
  response="$(login "$role")"
  token="$(printf '%s' "$response" | jq -r '.accessToken // .token // empty')"
  [[ -n "$token" ]] || { echo "FAIL $role: login returned no access token" >&2; exit 1; }
  curl -fsS "$BASE_URL/v1/auth/me" -H "Authorization: Bearer $token" >/dev/null
  echo "PASS $role: login and /me"

  status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/v1/citizens/register" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "$(printf '{\"wardId\":\"%s\",\"nid\":\"%s-%s\",\"citizenshipNo\":\"%s-%s\",\"nameNp\":\"Smoke Test\",\"nameEn\":\"Smoke Test\",\"dob\":\"1990-01-01\",\"sex\":\"OTHER\",\"consentChannel\":\"WEB\"}' "$WARD_ID" "$PREFIX" "$role" "$PREFIX" "$role")")"

  if [[ "$role" == WARD_ADMIN || "$role" == LOCAL_BODY_ADMIN ]]; then
    [[ "$status" != 401 && "$status" != 403 ]] || { echo "FAIL $role: registration denied ($status)" >&2; exit 1; }
    echo "PASS $role: registration authorization ($status)"
  else
    [[ "$status" == 403 ]] || { echo "FAIL $role: expected registration 403, got $status" >&2; exit 1; }
    echo "PASS $role: registration denied (403)"
  fi
done

echo "Role smoke tests completed."