#!/bin/sh
# Aspen Nacos 配置种子脚本 (POSIX sh, 在 curl 容器内执行)
# 行为: 初始化管理员密码(仅首次) -> 登录取 accessToken -> 按「只补缺失」发布 /seed/config 下全部 dataId
# FORCE_SEED=1 时跳过存在性检查, 以 Git 种子强制覆盖; 控制台手工修改在默认模式下不会被覆盖

set -eu

: "${NACOS_SERVER_ADDR:?缺少 NACOS_SERVER_ADDR}"
: "${NACOS_USER:?缺少 NACOS_USER}"
: "${NACOS_PASSWORD:?缺少 NACOS_PASSWORD}"

BASE="http://${NACOS_SERVER_ADDR}/nacos"
GROUP="DEFAULT_GROUP"
NAMESPACE="public"

# Nacos 3.x 起无内置管理员密码, 首启必须初始化; 已初始化时本接口报错, 直接忽略
if curl -sf -X POST "${BASE}/v3/auth/user/admin" \
  --data-urlencode "password=${NACOS_PASSWORD}" >/dev/null; then
  echo "管理员密码已初始化: ${NACOS_USER}"
else
  echo "管理员密码已存在, 跳过初始化"
fi

# 登录取 JWT; accessToken 为 JWT 字符中不含引号, sed 提取安全
TOKEN=$(curl -sf -X POST "${BASE}/v3/auth/user/login" \
  --data-urlencode "username=${NACOS_USER}" \
  --data-urlencode "password=${NACOS_PASSWORD}" \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -n "${TOKEN}" ] || { echo "登录失败, 无法获取 accessToken" >&2; exit 1; }
AUTH="Authorization: Bearer ${TOKEN}"

for file in /seed/config/*.yaml; do
  [ -f "${file}" ] || continue
  dataId=$(basename "${file}")

  if [ "${FORCE_SEED:-0}" != "1" ]; then
    if curl -sf -H "${AUTH}" \
      "${BASE}/v3/admin/cs/config?dataId=${dataId}&groupName=${GROUP}&namespaceId=${NAMESPACE}" \
      >/dev/null; then
      echo "已存在, 跳过: ${dataId}"
      continue
    fi
  fi

  curl -sf -H "${AUTH}" -X POST "${BASE}/v3/admin/cs/config" \
    --data-urlencode "dataId=${dataId}" \
    --data-urlencode "groupName=${GROUP}" \
    --data-urlencode "namespaceId=${NAMESPACE}" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@${file}" \
    >/dev/null
  echo "已发布: ${dataId}"
done

echo "配置种子完成"
