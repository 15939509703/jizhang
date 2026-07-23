#!/usr/bin/env bash

set -euo pipefail

readonly APP_USER="jizhang"
readonly APP_GROUP="jizhang"
readonly APP_DIR="/opt/jizhang-server"
readonly APP_STATE_DIR="/var/lib/jizhang"
readonly APP_CONFIG_DIR="/etc/jizhang"
readonly APP_ENV_FILE="${APP_CONFIG_DIR}/jizhang-server.env"
readonly LOCAL_SECRETS_FILE="/tmp/.env.local"
readonly MYSQL_CONTAINER="mysql8"
readonly REDIS_CONTAINER="agentos-infra-redis-1"

if [[ "${EUID}" -ne 0 ]]; then
    echo "This script must run as root." >&2
    exit 1
fi

for required_file in \
    /tmp/jizhang-bootstrap-1.0.0-SNAPSHOT.jar \
    /tmp/jizhang-server.service \
    "${LOCAL_SECRETS_FILE}"; do
    if [[ ! -f "${required_file}" ]]; then
        echo "Missing deployment file: ${required_file}" >&2
        exit 1
    fi
done

set -a
source "${LOCAL_SECRETS_FILE}"
set +a

: "${WX_APP_ID:?WX_APP_ID is required}"
: "${WX_APP_SECRET:?WX_APP_SECRET is required}"
: "${JWT_SECRET_BASE64:?JWT_SECRET_BASE64 is required}"

existing_db_password=""
if [[ -f "${APP_ENV_FILE}" ]]; then
    existing_db_password="$(sed -n 's/^DB_PASSWORD="\(.*\)"$/\1/p' "${APP_ENV_FILE}")"
fi
db_password="${existing_db_password:-$(openssl rand -hex 24)}"
redis_password="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "${REDIS_CONTAINER}" \
    | sed -n 's/^AGENTOS_REDIS_PASSWORD=//p')"

if [[ -z "${redis_password}" ]]; then
    echo "Unable to read the existing Redis password." >&2
    exit 1
fi

mysql_sql="$(printf \
    "CREATE DATABASE IF NOT EXISTS jizhang_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'jizhang_app'@'%%' IDENTIFIED BY '%s'; ALTER USER 'jizhang_app'@'%%' IDENTIFIED BY '%s'; GRANT ALL PRIVILEGES ON jizhang_db.* TO 'jizhang_app'@'%%'; FLUSH PRIVILEGES;" \
    "${db_password}" "${db_password}")"
printf '%s\n' "${mysql_sql}" | docker exec -i "${MYSQL_CONTAINER}" sh -c \
    'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" mysql -uroot'

docker exec -e REDISCLI_AUTH="${redis_password}" "${REDIS_CONTAINER}" redis-cli ping \
    | grep -qx PONG

escape_environment_value() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    printf '%s' "${value}"
}

write_environment_value() {
    local key="$1"
    local value="$2"
    printf '%s="%s"\n' "${key}" "$(escape_environment_value "${value}")"
}

umask 077
{
    write_environment_value SPRING_PROFILES_ACTIVE prod
    write_environment_value SERVER_ADDRESS 172.17.0.1
    write_environment_value SERVER_PORT 8081
    write_environment_value DB_URL 'jdbc:mysql://127.0.0.1:3306/jizhang_db?createDatabaseIfNotExist=false&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false'
    write_environment_value DB_USERNAME jizhang_app
    write_environment_value DB_PASSWORD "${db_password}"
    write_environment_value DB_MAX_POOL_SIZE 10
    write_environment_value DB_MIN_IDLE 2
    write_environment_value DB_CONNECTION_TIMEOUT_MS 5000
    write_environment_value SPRING_LIQUIBASE_ENABLED true
    write_environment_value REDIS_HOST 127.0.0.1
    write_environment_value REDIS_PORT 6379
    write_environment_value REDIS_PASSWORD "${redis_password}"
    write_environment_value REDIS_TIMEOUT 2s
    write_environment_value WX_APP_ID "${WX_APP_ID}"
    write_environment_value WX_APP_SECRET "${WX_APP_SECRET}"
    write_environment_value JWT_SECRET_BASE64 "${JWT_SECRET_BASE64}"
    write_environment_value UPLOAD_DIR "${APP_STATE_DIR}/uploads"
    write_environment_value HTTP_LOG_INCLUDE_BODY false
    write_environment_value HTTP_LOG_MAX_PAYLOAD_LENGTH 4096
    write_environment_value SWAGGER_ENABLED false
} > "${APP_ENV_FILE}"
chmod 600 "${APP_ENV_FILE}"
chown root:root "${APP_ENV_FILE}"

install -m 0640 -o "${APP_USER}" -g "${APP_GROUP}" \
    /tmp/jizhang-bootstrap-1.0.0-SNAPSHOT.jar "${APP_DIR}/jizhang-server.jar"
install -m 0644 -o root -g root \
    /tmp/jizhang-server.service /etc/systemd/system/jizhang-server.service

rm -f "${LOCAL_SECRETS_FILE}" \
    /tmp/jizhang-bootstrap-1.0.0-SNAPSHOT.jar \
    /tmp/jizhang-server.service

systemctl daemon-reload
systemctl enable jizhang-server.service
systemctl restart jizhang-server.service
