#!/usr/bin/env bash
# Keep this file LF-only so release bundles remain executable on Linux.
set -Eeuo pipefail

release="${1:?usage: deploy-release.sh <git-release-id>}"
if [[ ! "$release" =~ ^[0-9a-f]{7,40}$ ]]; then
  echo "invalid release id: $release" >&2
  exit 1
fi

app_root=/opt/ai-streamer
stage_dir="$app_root/releases/$release"
backup_dir="$app_root/backups/$(date +%Y%m%d%H%M%S)-$release"
frontend_new="$app_root/frontend.$release.new"
frontend_old="$app_root/frontend.$release.old"
config="$app_root/application-prod.yml"
ai_env="$app_root/business-ai.env"
upload_dir="$app_root/uploads"
dropin_dir=/etc/systemd/system/ai-streamer.service.d
dropin_file="$dropin_dir/10-business-ai.conf"
service_stopped=0
frontend_switched=0
backup_complete=0
deploy_complete=0

for target in "$stage_dir" "$backup_dir" "$frontend_new" "$frontend_old"; do
  resolved="$(readlink -m "$target")"
  case "$resolved" in
    /opt/ai-streamer/*) ;;
    *) echo "unsafe deployment path: $resolved" >&2; exit 1 ;;
  esac
done

required=(
  SHA256SUMS ruoyi-admin.jar frontend.tar.gz deploy-release.sh
  ai-streamer-business-ai.conf migrations/preflight_business_upgrade.sql
  migrations/verify_business_schema.sql migrations/release_gate.sql
)
for file in "${required[@]}"; do test -s "$stage_dir/$file"; done
for version in $(seq -w 10 48); do
  matches=("$stage_dir/migrations/V0${version}"__*.sql)
  test "${#matches[@]}" = 1
  test -s "${matches[0]}"
done
test -s "$app_root/ruoyi-admin.jar"
test -s "$app_root/frontend/index.html"
test -s "$config"
test ! -e "$frontend_new"
test ! -e "$frontend_old"

(
  cd "$stage_dir"
  sha256sum --check SHA256SUMS
)

test -s "$ai_env"
test "$(stat -c '%a' "$ai_env")" = 600
grep -Eq '^BUSINESS_AI_DEEPSEEK_ENABLED=true$' "$ai_env"
grep -Eq '^BUSINESS_AI_DEEPSEEK_API_KEY=.+$' "$ai_env"
token_secret="$(sed -n 's/^RUOYI_TOKEN_SECRET=//p' "$ai_env" | head -1)"
api_key="$(sed -n 's/^BUSINESS_AI_DEEPSEEK_API_KEY=//p' "$ai_env" | head -1)"
test "${#token_secret}" -ge 32
test "$token_secret" != abcdefghijklmnopqrstuvwxyz
test "$token_secret" != replace-with-at-least-32-random-characters
test "$api_key" != replace-with-production-key
grep -Eq '^RUOYI_LOG_LEVEL=info$' "$ai_env"
grep -Eq '^RUOYI_PROFILE=/opt/ai-streamer/uploads$' "$ai_env"
grep -Eq '^SPRING_DEVTOOLS_RESTART_ENABLED=false$' "$ai_env"
grep -Eq '^SWAGGER_ENABLED=false$' "$ai_env"
grep -Eq '^DRUID_STAT_VIEW_ENABLED=false$' "$ai_env"

db_url="$(awk '/^[[:space:]]+master:/{in_master=1; next} in_master && /^[[:space:]]+url:/{sub(/^[^:]+:[[:space:]]*/, ""); print; exit}' "$config")"
db_user="$(awk '/^[[:space:]]+master:/{in_master=1; next} in_master && /^[[:space:]]+username:/{sub(/^[^:]+:[[:space:]]*/, ""); print; exit}' "$config")"
db_pass="$(awk '/^[[:space:]]+master:/{in_master=1; next} in_master && /^[[:space:]]+password:/{sub(/^[^:]+:[[:space:]]*/, ""); print; exit}' "$config")"
db_url="${db_url%\"}"; db_url="${db_url#\"}"
db_user="${db_user%\"}"; db_user="${db_user#\"}"
db_pass="${db_pass%\"}"; db_pass="${db_pass#\"}"
if [[ ! "$db_url" =~ ^jdbc:mysql://([^:/]+):([0-9]+)/([^?]+) ]]; then
  echo "cannot parse database URL" >&2
  exit 1
fi
db_host="${BASH_REMATCH[1]}"
db_port="${BASH_REMATCH[2]}"
db_name="${BASH_REMATCH[3]}"

mysql_query() {
  MYSQL_PWD="$db_pass" mysql --batch --skip-column-names --default-character-set=utf8mb4 \
    -h "$db_host" -P "$db_port" -u "$db_user" "$db_name" "$@"
}

wang_count="$(mysql_query --execute="select count(*) from sys_user where user_name='wangfuzhang' and del_flag='0'")"
test "$wang_count" = 1

mkdir -p "$backup_dir"
cp -a "$app_root/ruoyi-admin.jar" "$backup_dir/ruoyi-admin.jar"
cp -a "$app_root/frontend" "$backup_dir/frontend"
cp -a "$config" "$backup_dir/application-prod.yml"
if [[ -f "$dropin_file" ]]; then cp -a "$dropin_file" "$backup_dir/10-business-ai.conf"; fi
MYSQL_PWD="$db_pass" mysqldump --single-transaction --quick --skip-lock-tables \
  --routines --triggers --events --hex-blob --set-gtid-purged=OFF \
  -h "$db_host" -P "$db_port" -u "$db_user" "$db_name" > "$backup_dir/database.sql"
test -s "$backup_dir/database.sql"
backup_complete=1

mysql_query < "$stage_dir/migrations/preflight_business_upgrade.sql" | tee "$backup_dir/preflight-before.tsv"
identity_before="$(sed -n '1p' "$backup_dir/preflight-before.tsv" | cut -f3)"
role_before="$(sed -n '2p' "$backup_dir/preflight-before.tsv" | cut -f2)"
test -n "$identity_before"
test -n "$role_before"

mkdir -p "$frontend_new"
tar -xzf "$stage_dir/frontend.tar.gz" -C "$frontend_new"
test -s "$frontend_new/index.html"
test -n "$(find "$frontend_new/static/js" -type f -name '*.js' -print -quit)"
chown -R root:root "$frontend_new"
find "$frontend_new" -type d -exec chmod 755 {} +
find "$frontend_new" -type f -exec chmod 644 {} +

rollback() {
  status=$?
  if [[ "$deploy_complete" = 1 ]]; then return; fi
  echo "deployment failed; starting rollback" >&2
  set +e
  if [[ "$backup_complete" = 1 ]]; then
    MYSQL_PWD="$db_pass" mysql --default-character-set=utf8mb4 \
      -h "$db_host" -P "$db_port" -u "$db_user" "$db_name" < "$backup_dir/database.sql"
    install -m 0644 "$backup_dir/ruoyi-admin.jar" "$app_root/ruoyi-admin.jar"
    install -m 0600 "$backup_dir/application-prod.yml" "$config"
    if [[ -f "$backup_dir/10-business-ai.conf" ]]; then
      mkdir -p "$dropin_dir"
      install -m 0644 "$backup_dir/10-business-ai.conf" "$dropin_file"
    else
      rm -f "$dropin_file"
    fi
  fi
  if [[ "$frontend_switched" = 1 && -d "$frontend_old" ]]; then
    mv "$app_root/frontend" "$app_root/frontend.$release.failed.$(date +%s)"
    mv "$frontend_old" "$app_root/frontend"
  fi
  rm -rf "$frontend_new"
  systemctl daemon-reload
  systemctl start ai-streamer.service
  exit "$status"
}
trap rollback ERR INT TERM HUP

systemctl stop ai-streamer.service
service_stopped=1

for migration in "$stage_dir"/migrations/V*.sql; do
  echo "applying $(basename "$migration")"
  mysql_query < "$migration"
done

mysql_query < "$stage_dir/migrations/verify_business_schema.sql" | tee "$backup_dir/verify-after.tsv"
gate="$(mysql_query < "$stage_dir/migrations/release_gate.sql")"
test "$gate" = 0

mysql_query < "$stage_dir/migrations/preflight_business_upgrade.sql" | tee "$backup_dir/preflight-after.tsv"
identity_after="$(sed -n '1p' "$backup_dir/preflight-after.tsv" | cut -f3)"
role_after="$(sed -n '2p' "$backup_dir/preflight-after.tsv" | cut -f2)"
test "$identity_before" = "$identity_after"
test "$role_before" = "$role_after"

install -m 0644 "$stage_dir/ruoyi-admin.jar" "$app_root/ruoyi-admin.jar"
chmod 0600 "$config"
install -d -m 0755 "$upload_dir"
mkdir -p "$dropin_dir"
install -m 0644 "$stage_dir/ai-streamer-business-ai.conf" "$dropin_file"
systemctl daemon-reload

mv "$app_root/frontend" "$frontend_old"
mv "$frontend_new" "$app_root/frontend"
frontend_switched=1

systemctl start ai-streamer.service
api_code=000
for _ in $(seq 1 60); do
  if systemctl is-active --quiet ai-streamer.service; then
    api_code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8080/captchaImage || true)"
    if [[ "$api_code" = 200 ]]; then break; fi
  fi
  sleep 1
done
test "$api_code" = 200

nginx -t
frontend_code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 http://127.0.0.1:8090/)"
proxy_api_code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 http://127.0.0.1:8090/prod-api/captchaImage)"
druid_code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 http://127.0.0.1:8080/druid/index.html)"
unauthorized_body="$(curl -sS --max-time 10 http://127.0.0.1:8080/business/project/list)"
test "$frontend_code" = 200
test "$proxy_api_code" = 200
test "$druid_code" = 404
grep -Eq '"code"[[:space:]]*:[[:space:]]*401' <<< "$unauthorized_body"

mv "$frontend_old" "$backup_dir/frontend-live-before-switch"
frontend_switched=0
deploy_complete=1
trap - ERR INT TERM HUP

echo "release=$release"
echo "backup_dir=$backup_dir"
echo "service=$(systemctl is-active ai-streamer.service)"
echo "api_code=$api_code"
echo "frontend_code=$frontend_code"
echo "proxy_api_code=$proxy_api_code"
echo "druid_code=$druid_code"
echo "release_gate=$gate"
