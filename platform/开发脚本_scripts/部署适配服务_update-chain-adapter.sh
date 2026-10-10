#!/usr/bin/env bash
# 在链上适配服务虚拟机上重新编译并重启 ChainAdapter（新增 GET /chain/block）。
# 用法（在虚拟机里执行）：
#   bash update-chain-adapter.sh [工作目录] [服务端口] [新源码路径] [sudo 密码]
#
# 执行顺序刻意设计为：先编译成功，再停服务。编译失败时旧服务继续跑，不会出现"停了起不来"。
# 虚拟机上服务由 systemd 单元 chain-adapter.service 管理时优先走 systemctl restart；
# 没有 systemd 单元才退化成 setsid nohup 手动拉起（那种情况下要自己保证没有重复实例）。
set -euo pipefail

DIR="${1:-$HOME/smart-greenhouse-chain}"
PORT="${2:-8600}"
SOURCE="${3:-/tmp/ChainAdapter.java}"
SUDO_PASS="${4:-}"
SERVICE="chain-adapter.service"
# 与 docs 里的实训环境部署记录保持一致
CONTRACT="0xf763648341076819904e80d855127272f2d4e27b"

cd "$DIR"
echo "[1/5] 备份现有源码与编译产物"
cp -f ChainAdapter.java "ChainAdapter.java.bak.$(date +%Y%m%d%H%M%S)" 2>/dev/null || true
cp -f "$SOURCE" ./ChainAdapter.java

echo "[2/5] 编译（javac -encoding UTF-8 -cp lib/* -d out）"
javac -encoding UTF-8 -cp "lib/*" -d out ChainAdapter.java
echo "      编译完成"

if systemctl cat "${SERVICE}" >/dev/null 2>&1; then
  echo "[3/5] 重启 systemd 服务 ${SERVICE}"
  if [ -n "${SUDO_PASS}" ]; then
    printf '%s\n' "${SUDO_PASS}" | sudo -S systemctl restart "${SERVICE}"
  else
    sudo systemctl restart "${SERVICE}"
  fi
  echo "      已提交重启"
else
  echo "[3/5] 未发现 ${SERVICE}，改用手动方式重启"
  PID="$(ss -lptn "sport = :${PORT}" 2>/dev/null | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2 || true)"
  if [ -z "${PID}" ]; then
    PID="$(pgrep -f 'ChainAdapter --action serve' | head -1 || true)"
  fi
  if [ -n "${PID}" ]; then
    kill "${PID}" 2>/dev/null || true
    for _ in 1 2 3 4 5; do
      sleep 1
      kill -0 "${PID}" 2>/dev/null || break
    done
    kill -9 "${PID}" 2>/dev/null || true
    echo "      已停止 pid ${PID}"
  fi
  echo "[4/5] 手动拉起适配服务"
  setsid nohup java -Djdk.tls.namedGroups=SM2,secp256k1,x25519,secp256r1,secp384r1,secp521r1 \
    -cp "out:conf/:lib/*" ChainAdapter --action serve --config conf/config.toml \
    --abi contract.abi.json --bin contract.bin \
    --address "${CONTRACT}" --port "${PORT}" \
    > adapter.log 2>&1 < /dev/null &
fi

echo "[5/5] 等待服务监听 ${PORT} 并自检（最多 40 秒）"
for _ in $(seq 1 40); do
  if curl -sS -m 2 "http://127.0.0.1:${PORT}/chain/block" -o /tmp/chain-block.json 2>/dev/null; then
    cat /tmp/chain-block.json
    echo
    echo "完成：GET /chain/block 已可用。"
    exit 0
  fi
  sleep 1
done

echo "启动 40 秒后仍访问不到 /chain/block，最近日志：" >&2
tail -20 adapter.log >&2
exit 1
