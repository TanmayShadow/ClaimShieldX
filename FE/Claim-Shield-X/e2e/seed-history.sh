#!/usr/bin/env bash
# Prepares organization "Org H" on the scratch backend with the synthetic claim and garage history from the backend's docs/sample-data,
# so a claim on vehicle token VEH-REPEAT-01 gets a real flagged history check. Run once after e2e/the backend is up. Synthetic data only.
# Needs: psql access to the throwaway database (credentials come from the backend's .env and are never printed), curl, python3.
set -euo pipefail
BACKEND_ENV="${BACKEND_ENV:-../../BE/ClaimShieldX/.env}"
SAMPLES="${SAMPLES:-../../BE/ClaimShieldX/docs/sample-data}"
API="${E2E_API_TARGET:-http://localhost:18080}/api/v1"
DB="${E2E_DB_NAME:-claimshieldx_fe_scratch}"
set -a; source "$BACKEND_ENV"; set +a
export PGPASSWORD="$DB_PASSWORD"
PSQL=(psql -h localhost -U "$DB_USERNAME" -d "$DB" -qAt)

"${PSQL[@]}" -c "insert into organization(id,name,status,created_at,updated_at) select gen_random_uuid(),'Org H','ACTIVE',now(),now() where not exists (select 1 from organization where name='Org H')"
EMAIL="history-admin-$RANDOM@org-h.test"
curl -fsS -o /dev/null -X POST "$API/users/register" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"Passw0rd!x1\",\"organizationName\":\"Org H\"}"
"${PSQL[@]}" -c "update users set role='ORG_ADMIN' where email='$EMAIL'"
TOKEN=$(curl -fsS -X POST "$API/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"Passw0rd!x1\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
for ITEM in "HISTORICAL_CLAIMS:historical-claims-synthetic.csv" "GARAGE_HISTORY:garage-history-synthetic.csv"; do
  curl -fsS -X POST "$API/historical-imports" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: seed-${ITEM%%:*}-v1" \
    -F "file=@$SAMPLES/${ITEM##*:}" -F "importType=${ITEM%%:*}" -F templateVersion=1 -F source=E2E_SEED \
    | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["importType"],d["status"],d["counts"])'
done
