#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

JAR_NAME="${JAR_NAME:-loipl-0.0.1-SNAPSHOT.jar}"
LOCAL_JAR="${1:-${PROJECT_ROOT}/target/${JAR_NAME}}"

JUMP_USER="${JUMP_USER:-jagnya}"
JUMP_HOST="${JUMP_HOST:-10.126.1.132}"
JUMP_HOME="${JUMP_HOME:-/home/${JUMP_USER}}"

TARGET_USER="${TARGET_USER:-lmsone}"
TARGET_HOST="${TARGET_HOST:-10.126.32.10}"
TARGET_JAR_DIR="${TARGET_JAR_DIR:-/home/${TARGET_USER}/chatbot}"
TARGET_SCRIPT_DIR="${TARGET_SCRIPT_DIR:-${TARGET_JAR_DIR}/scripts}"

REMOTE_JAR_ON_JUMP="${JUMP_HOME}/${JAR_NAME}"

if [[ ! -f "${LOCAL_JAR}" ]]; then
    echo "Jar not found: ${LOCAL_JAR}" >&2
    echo "Build it first with: ./mvnw clean package" >&2
    exit 1
fi

echo "Copying ${LOCAL_JAR} to ${JUMP_USER}@${JUMP_HOST}:${JUMP_HOME}/"
scp "${LOCAL_JAR}" "${JUMP_USER}@${JUMP_HOST}:${JUMP_HOME}/"

echo "Copying ${JAR_NAME} from jump server to ${TARGET_USER}@${TARGET_HOST}:${TARGET_JAR_DIR}/"
ssh "${JUMP_USER}@${JUMP_HOST}" \
    "scp '${REMOTE_JAR_ON_JUMP}' '${TARGET_USER}@${TARGET_HOST}:${TARGET_JAR_DIR}/'"

echo "Restarting chatbot on ${TARGET_HOST}"
ssh "${JUMP_USER}@${JUMP_HOST}" \
    "ssh '${TARGET_USER}@${TARGET_HOST}' 'cd \"${TARGET_SCRIPT_DIR}\" && ./lmg_stop.sh && ./lmg_start.sh'"

echo "Deployment completed."
