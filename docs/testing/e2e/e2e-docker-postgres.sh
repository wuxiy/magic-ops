#!/usr/bin/env bash
# MagicOps Docker Compose E2E 闭环测试（PostgreSQL）
# 验证切片 33/34 在真实 PostgreSQL 下的行为：
#   - Console+Runtime+PostgreSQL 三容器起停
#   - Flyway V1-V10 在真实 PG 迁移，Runtime ddl-auto=validate 通过
#   - 12 步治理闭环（含脚本引用执行、裸 SQL 拒绝）
#   - Runtime 重启后从 active_packages 重载激活包（切片 33-b）
#   - 执行审计落 audit_records 表（切片 33-a）
#   - HTTP 目标从 http_targets 同步（切片 33-c）
#
# 前置条件：
#   1. mvn install -DskipTests 已完成（jar 存在）
#   2. Docker daemon 可用
#
# 用法：
#   bash docs/testing/e2e/e2e-docker-postgres.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$PROJECT_ROOT"

CONSOLE_URL="http://localhost:8080"
RUNTIME_URL="http://localhost:8081"
AUTH="admin:docker-admin-pw"
APPR_AUTH="approver:docker-appr-pw"
PASS_COUNT=0
FAIL_COUNT=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'
pass() { echo -e "${GREEN}✅ PASS${NC}: $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { echo -e "${RED}❌ FAIL${NC}: $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
step() { echo -e "\n${YELLOW}====== $1 ======${NC}"; }

cleanup() {
    echo ""
    echo "Cleaning up Docker stack..."
    docker compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

# 1. 生成密钥与凭据写入 .env
step "生成签名密钥对与凭据，写入 .env"
eval "$(java -cp "$PROJECT_ROOT/magicops-sign/target/classes" top.cywu.magicops.sign.key.KeyPairGeneratorUtil)"
cat > "$PROJECT_ROOT/.env" <<EOF
DB_PASSWORD=docker-db-pw
MAGICOPS_ADMIN_PASSWORD=docker-admin-pw
MAGICOPS_PRIVATE_KEY=$MAGICOPS_PRIVATE_KEY
MAGICOPS_PUBLIC_KEY=$MAGICOPS_PUBLIC_KEY
MAGICOPS_KEY_ID=$MAGICOPS_KEY_ID
MAGICOPS_RUNTIME_SHARED_SECRET=docker-push-secret
RUNTIME_USERNAME=runtime
RUNTIME_PASSWORD=docker-runtime-pw
EOF
echo "Key ID: $MAGICOPS_KEY_ID"

# 2. 构建并启动
step "构建镜像并启动 PostgreSQL+Console+Runtime"
docker compose down -v --remove-orphans >/dev/null 2>&1 || true
docker compose build >/dev/null 2>&1 || { echo "build failed"; exit 1; }
docker compose up -d >/dev/null 2>&1 || { echo "up failed"; docker compose logs; exit 1; }

echo "等待三容器健康..."
for i in $(seq 1 60); do
    healthy=$(docker compose ps --format json 2>/dev/null | grep -c '"Health":"healthy"' || true)
    if [ "$healthy" -ge 3 ]; then echo "三容器健康"; break; fi
    sleep 2
done
# 兜底等待 Console/Runtime 可达
for i in $(seq 1 30); do
    if curl -s -o /dev/null -u "$AUTH" "$CONSOLE_URL/api/scripts" 2>/dev/null \
       && curl -s -o /dev/null "$RUNTIME_URL/api/packages/active" 2>/dev/null; then
        break
    fi
    sleep 1
done
sleep 3

# 3. 治理闭环
step "Step 1: 创建脚本 (DRAFT, DYNAMIC_QUERY)"
CREATE_RESP=$(curl -s -u $AUTH -X POST "$CONSOLE_URL/api/scripts" \
  -H "Content-Type: application/json" \
  -d '{"name":"e2e-docker-query","projectCode":"hospital-a","scriptType":"DYNAMIC_QUERY"}')
SCRIPT_ID=$(echo "$CREATE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
if [ -n "$SCRIPT_ID" ]; then pass "Script created: id=$SCRIPT_ID"; else fail "Script creation failed"; exit 1; fi

step "Step 2: 更新草稿 (可执行 SQL)"
curl -s -o /dev/null -u $AUTH -X PUT "$CONSOLE_URL/api/scripts/$SCRIPT_ID/draft" \
  -H "Content-Type: application/json" \
  -d '{"content":"SELECT 1 AS test_value","routePath":"/api/test","routeMethod":"GET"}' && pass "Draft updated" || fail "Draft update failed"

step "Step 3: 创建版本"
curl -s -o /dev/null -X POST -u $AUTH "$CONSOLE_URL/api/scripts/$SCRIPT_ID/versions" \
  -H "Content-Type: application/json" -d '{"version":"1.0.0","riskLevel":"LOW"}' && pass "Version created" || fail "Version creation failed"

step "Step 4: 提交审批 (admin)"
curl -s -o /dev/null -X POST -u $AUTH "$CONSOLE_URL/api/scripts/$SCRIPT_ID/submit" && pass "Submitted" || fail "Submit failed"

step "Step 5: 自审自批被拒 (切片 31, 403)"
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u $AUTH "$CONSOLE_URL/api/scripts/$SCRIPT_ID/approve" \
  -H "Content-Type: application/json" -d '{"decision":"APPROVED","comment":"self"}')
if [ "$HTTP" = "403" ]; then pass "Self-approval rejected"; else fail "Expected 403, got $HTTP"; fi

step "Step 6: 审批通过 (approver)"
# prod profile 下不预置 approver 测试账号；admin（PLATFORM_ADMIN）创建审批人并赋予 APPROVER 角色
APPR_ID=$(curl -s -u $AUTH -X POST "$CONSOLE_URL/api/users" \
  -H "Content-Type: application/json" \
  -d '{"username":"approver","password":"docker-appr-pw","displayName":"E2E Approver","email":"appr@e2e.local"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
curl -s -o /dev/null -u $AUTH -X POST "$CONSOLE_URL/api/users/$APPR_ID/roles" \
  -H "Content-Type: application/json" -d '{"roleName":"APPROVER"}' || true
DEC=$(curl -s -u $APPR_AUTH -X POST "$CONSOLE_URL/api/scripts/$SCRIPT_ID/approve" \
  -H "Content-Type: application/json" -d '{"decision":"APPROVED","comment":"e2e"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('decision',''))" 2>/dev/null || echo "")
if [ "$DEC" = "APPROVED" ]; then pass "Approved by approver"; else fail "Approval failed: $DEC"; fi

step "Step 7: 未携带共享密钥推送被拒 (切片 32, 401)"
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$RUNTIME_URL/api/packages" -H "Content-Type: application/json" -d '{"manifest":{}}')
if [ "$HTTP" = "401" ]; then pass "Unauthenticated push rejected"; else fail "Expected 401, got $HTTP"; fi

step "Step 8: 签名+推送发布包 (携带共享密钥)"
PUB=$(curl -s -u $AUTH -X POST "$CONSOLE_URL/api/scripts/publish" \
  -H "Content-Type: application/json" -d "{\"scriptIds\":[$SCRIPT_ID],\"environment\":\"development\"}")
PST=$(echo "$PUB" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")
if [ "$PST" = "published" ]; then pass "Package built, signed, pushed"; else fail "Publish failed: $PUB"; fi

step "Step 9: Runtime 验签加载"
ACT=$(curl -s "$RUNTIME_URL/api/packages/active" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")
if [ "$ACT" = "active" ]; then pass "Runtime loaded package"; else fail "Package not active: $ACT"; fi

step "Step 10: 脚本引用执行查询 (切片 34)"
QR=$(curl -s -u runtime:docker-runtime-pw -X POST "$RUNTIME_URL/api/query" \
  -H "Content-Type: application/json" -d "{\"scriptId\":\"$SCRIPT_ID\",\"traceId\":\"docker-001\"}")
QS=$(echo "$QR" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success'))" 2>/dev/null || echo "False")
if [ "$QS" = "True" ]; then pass "Query executed via script reference"; else fail "Query failed: $QR"; fi

step "Step 11: 裸 SQL 请求被拒 (切片 34)"
RR=$(curl -s -u runtime:docker-runtime-pw -X POST "$RUNTIME_URL/api/query" \
  -H "Content-Type: application/json" -d '{"sql":"SELECT 1"}')
ERR=$(echo "$RR" | python3 -c "import sys,json; print(json.load(sys.stdin).get('error',''))" 2>/dev/null || echo "")
if echo "$ERR" | grep -q "scriptId"; then pass "Bare SQL rejected"; else fail "Bare SQL should be rejected: $RR"; fi

step "Step 12: Runtime 重启后从 PostgreSQL 重载激活包 (切片 33-b)"
docker compose restart runtime >/dev/null 2>&1
for i in $(seq 1 30); do
    if curl -s -o /dev/null "$RUNTIME_URL/api/packages/active" 2>/dev/null; then sleep 3; break; fi
    sleep 1
done
ACT2=$(curl -s "$RUNTIME_URL/api/packages/active" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")
if [ "$ACT2" = "active" ]; then pass "Runtime reloaded active package from PostgreSQL after restart"; else fail "Package not reloaded: $ACT2"; fi

step "Step 13: 重载后脚本引用执行仍成功 (切片 33-b + 34)"
QR2=$(curl -s -u runtime:docker-runtime-pw -X POST "$RUNTIME_URL/api/query" \
  -H "Content-Type: application/json" -d "{\"scriptId\":\"$SCRIPT_ID\",\"traceId\":\"docker-002\"}")
QS2=$(echo "$QR2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success'))" 2>/dev/null || echo "False")
if [ "$QS2" = "True" ]; then pass "Query via script reference after restart"; else fail "Query after restart failed: $QR2"; fi

step "Step 14: Console 推送下线指令，Runtime 停用激活包 (切片 38)"
DEACT=$(curl -s -u $AUTH -X POST "$CONSOLE_URL/api/scripts/deactivate" \
  -H "Content-Type: application/json" -d '{"reason":"E2E 下线验证"}')
DEACT_ST=$(echo "$DEACT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")
if [ "$DEACT_ST" = "deactivated" ]; then pass "Console deactivate pushed, Runtime accepted"; else fail "Deactivate failed: $DEACT"; fi

step "Step 15: 下线后脚本引用执行被拒绝 (切片 38)"
QR3=$(curl -s -u runtime:docker-runtime-pw -X POST "$RUNTIME_URL/api/query" \
  -H "Content-Type: application/json" -d "{\"scriptId\":\"$SCRIPT_ID\",\"traceId\":\"docker-003\"}")
QR3_ERR=$(echo "$QR3" | python3 -c "import sys,json; print(json.load(sys.stdin).get('error',''))" 2>/dev/null || echo "")
if echo "$QR3_ERR" | grep -q "没有已激活"; then pass "Query rejected after deactivate"; else fail "Query should be rejected after deactivate: $QR3"; fi


step "Step 16: 执行审计落 PostgreSQL audit_records (切片 33-a)"
AUDIT_CNT=$(docker compose exec -T postgres psql -U magicops -d magicops -t \
  -c "SELECT count(*) FROM audit_records WHERE event_type IN ('PACKAGE_LOADED','SCRIPT_EXECUTED')" 2>/dev/null | tr -d '[:space:]' || echo "0")
if [ "$AUDIT_CNT" -ge 2 ] 2>/dev/null; then pass "Audit records persisted to PostgreSQL: $AUDIT_CNT"; else fail "Expected >=2 audit records, got: $AUDIT_CNT"; fi

step "Step 17: 激活包落 PostgreSQL active_packages（下线后应为 INACTIVE，切片 33-b + 38）"
PKG_INACTIVE=$(docker compose exec -T postgres psql -U magicops -d magicops -t \
  -c "SELECT count(*) FROM active_packages WHERE status='INACTIVE'" 2>/dev/null | tr -d '[:space:]' || echo "0")
PKG_ACTIVE=$(docker compose exec -T postgres psql -U magicops -d magicops -t \
  -c "SELECT count(*) FROM active_packages WHERE status='ACTIVE'" 2>/dev/null | tr -d '[:space:]' || echo "0")
if [ "$PKG_INACTIVE" -ge 1 ] && [ "$PKG_ACTIVE" = "0" ] 2>/dev/null; then pass "Package persisted (INACTIVE=$PKG_INACTIVE, ACTIVE=$PKG_ACTIVE after deactivate)"; else fail "Expected INACTIVE>=1 & ACTIVE=0, got INACTIVE=$PKG_INACTIVE ACTIVE=$PKG_ACTIVE"; fi

step "Step 18: actuator/health 真实端点可匿名访问 (切片 35)"
CONSOLE_H=$(curl -s -o /dev/null -w "%{http_code}" "$CONSOLE_URL/actuator/health")
RUNTIME_H=$(curl -s -o /dev/null -w "%{http_code}" "$RUNTIME_URL/actuator/health")
if [ "$CONSOLE_H" = "200" ] && [ "$RUNTIME_H" = "200" ]; then pass "actuator/health up on Console($CONSOLE_H) and Runtime($RUNTIME_H)"; else fail "actuator/health: console=$CONSOLE_H runtime=$RUNTIME_H"; fi


# 总结
echo ""
echo "============================================"
echo " Docker Compose E2E (PostgreSQL) 结果"
echo "============================================"
echo -e " 通过: ${GREEN}$PASS_COUNT${NC}"
echo -e " 失败: ${RED}$FAIL_COUNT${NC}"
echo "============================================"

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo -e "${GREEN}🎉 Docker E2E (PostgreSQL) 全部通过！${NC}"
    exit 0
else
    echo -e "${RED}存在失败，查看容器日志：docker compose logs${NC}"
    exit 1
fi
