#!/usr/bin/env bash
# MagicOps E2E 闭环测试脚本
# 验证：创建脚本 → 调试 → 提交 → 自审自批拒绝（切片 31）→ 审批 → 签名 → 推送（共享密钥认证，切片 32）→ Runtime 验签加载 → 执行 → 审计
#
# 前置条件：
#   1. mvn install -DskipTests 已完成
#   2. Java 21 可用
#
# 用法：
#   bash docs/testing/e2e/e2e-closed-loop-test.sh

set -euo pipefail

CONSOLE_PORT=8080
RUNTIME_PORT=8081
CONSOLE_URL="http://localhost:$CONSOLE_PORT"
RUNTIME_URL="http://localhost:$RUNTIME_PORT"
AUTH="admin:magicops-admin"
APPR_AUTH="approver:appr123"
PASS_COUNT=0
FAIL_COUNT=0

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✅ PASS${NC}: $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { echo -e "${RED}❌ FAIL${NC}: $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
step() { echo -e "\n${YELLOW}====== $1 ======${NC}"; }

cleanup() {
    echo ""
    echo "Cleaning up..."
    kill $CONSOLE_PID $RUNTIME_PID 2>/dev/null || true
    wait $CONSOLE_PID $RUNTIME_PID 2>/dev/null || true
    echo "Apps stopped."
}
trap cleanup EXIT

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# 1. 生成共享密钥对
step "生成共享 RSA 密钥对"
eval "$(java -cp "$PROJECT_ROOT/magicops-sign/target/classes" top.cywu.magicops.sign.key.KeyPairGeneratorUtil)"
export MAGICOPS_ENVIRONMENT="development"
export MAGICOPS_RUNTIME_SHARED_SECRET="e2e-push-secret-$RANDOM$RANDOM"
echo "Key ID: $MAGICOPS_KEY_ID"
echo "Push secret configured (Console 与 Runtime 共用)"

# 2. 启动 Console 和 Runtime
step "启动 Console ($CONSOLE_PORT) 和 Runtime ($RUNTIME_PORT)"
java -jar "$PROJECT_ROOT/magicops-console/target/magicops-console-0.1.0-SNAPSHOT.jar" > /tmp/console.log 2>&1 &
CONSOLE_PID=$!
java -jar "$PROJECT_ROOT/magicops-runtime/target/magicops-runtime-0.1.0-SNAPSHOT.jar" > /tmp/runtime.log 2>&1 &
RUNTIME_PID=$!
echo "Console PID: $CONSOLE_PID, Runtime PID: $RUNTIME_PID"

# 等待启动
echo "Waiting for apps to start..."
for i in $(seq 1 30); do
    if curl -s -o /dev/null -u $AUTH "$CONSOLE_URL/api/scripts" 2>/dev/null && \
       curl -s -o /dev/null "$RUNTIME_URL/api/packages/active" 2>/dev/null; then
        echo "Both apps started successfully."
        sleep 2  # Extra time for full initialization
        break
    fi
    sleep 1
done

# 3. E2E 闭环

step "Step 1: 创建脚本 (DRAFT)"
CREATE_RESP=$(curl -s -u $AUTH -X POST "$CONSOLE_URL/api/scripts" \
  -H "Content-Type: application/json" \
  -d '{"name":"e2e-patient-query","projectCode":"hospital-a","scriptType":"DYNAMIC_QUERY"}')
SCRIPT_ID=$(echo "$CREATE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
SCRIPT_STATUS=$(echo "$CREATE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
if [ "$SCRIPT_STATUS" = "DRAFT" ]; then pass "Script created: id=$SCRIPT_ID, status=DRAFT"; else fail "Expected DRAFT, got $SCRIPT_STATUS"; fi

step "Step 2: 更新草稿 (调试)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u $AUTH -X PUT "$CONSOLE_URL/api/scripts/$SCRIPT_ID/draft" \
  -H "Content-Type: application/json" \
  -d '{"content":"SELECT 1 AS test_value","routePath":"/api/patients/query","routeMethod":"GET"}')
if [ "$HTTP_CODE" = "200" ]; then pass "Draft updated"; else fail "Draft update failed: HTTP $HTTP_CODE"; fi

step "Step 3: 创建版本"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u $AUTH -X POST "$CONSOLE_URL/api/scripts/$SCRIPT_ID/versions" \
  -H "Content-Type: application/json" \
  -d '{"version":"1.0.0","riskLevel":"LOW"}')
if [ "$HTTP_CODE" = "201" ]; then pass "Version created: v1.0.0"; else fail "Version creation failed: HTTP $HTTP_CODE"; fi

step "Step 4: 提交审批 (admin)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u $AUTH -X POST "$CONSOLE_URL/api/scripts/$SCRIPT_ID/submit")
if [ "$HTTP_CODE" = "200" ]; then pass "Submitted for approval by admin"; else fail "Submit failed: HTTP $HTTP_CODE"; fi

step "Step 5a: 自审自批被拒绝 (切片 31 分离规则)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u $AUTH -X POST "$CONSOLE_URL/api/scripts/$SCRIPT_ID/approve" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVED","comment":"self approve attempt"}')
if [ "$HTTP_CODE" = "403" ]; then pass "Self-approval rejected: HTTP 403"; else fail "Self-approval should be rejected, got HTTP $HTTP_CODE"; fi

step "Step 5b: 审批通过 (approver, APPROVED)"
APPROVE_RESP=$(curl -s -u $APPR_AUTH -X POST "$CONSOLE_URL/api/scripts/$SCRIPT_ID/approve" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVED","comment":"E2E test approved"}')
DECISION=$(echo "$APPROVE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['decision'])")
if [ "$DECISION" = "APPROVED" ]; then pass "Approved by approver"; else fail "Approval failed: $DECISION"; fi

# 验证脚本状态
STATUS=$(curl -s -u $AUTH "$CONSOLE_URL/api/scripts/$SCRIPT_ID" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
if [ "$STATUS" = "APPROVED" ]; then pass "Script status: APPROVED"; else fail "Expected APPROVED, got $STATUS"; fi

step "Step 5c: 未携带共享密钥的推送被拒绝 (切片 32, 401)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$RUNTIME_URL/api/packages" \
  -H "Content-Type: application/json" \
  -d '{"manifest":{}}')
if [ "$HTTP_CODE" = "401" ]; then pass "Unauthenticated push rejected: HTTP 401"; else fail "Expected 401 for push without secret, got HTTP $HTTP_CODE"; fi

step "Step 6+7: 构建签名 + 推送发布包到 Runtime (携带共享密钥)"
PUBLISH_RESP=$(curl -s -u $AUTH -X POST "$CONSOLE_URL/api/scripts/publish" \
  -H "Content-Type: application/json" \
  -d "{\"scriptIds\":[$SCRIPT_ID],\"environment\":\"development\"}")
PUB_STATUS=$(echo "$PUBLISH_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
if [ "$PUB_STATUS" = "published" ]; then pass "Package built, signed, and pushed"; else fail "Publish failed: $PUB_STATUS"; fi

step "Step 8: Runtime 验签加载"
ACTIVE_RESP=$(curl -s "$RUNTIME_URL/api/packages/active")
ACTIVE_STATUS=$(echo "$ACTIVE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
ACTIVE_VERSION=$(echo "$ACTIVE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['version'])")
if [ "$ACTIVE_STATUS" = "active" ]; then pass "Runtime loaded package: v$ACTIVE_VERSION"; else fail "Package not active: $ACTIVE_STATUS"; fi

step "Step 9: Runtime 脚本引用执行查询 (切片 34)"
QUERY_RESP=$(curl -s -u runtime:runtime -X POST "$RUNTIME_URL/api/query" \
  -H "Content-Type: application/json" \
  -d "{\"scriptId\":\"$SCRIPT_ID\",\"traceId\":\"e2e-trace-001\"}")
QUERY_SUCCESS=$(echo "$QUERY_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['success'])")
QUERY_ROWS=$(echo "$QUERY_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['resultSize'])")
QUERY_TRACE=$(echo "$QUERY_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['traceId'])")
if [ "$QUERY_SUCCESS" = "True" ]; then pass "Query executed via script reference: rows=$QUERY_ROWS, traceId=$QUERY_TRACE"; else fail "Query failed: $(echo $QUERY_RESP | python3 -c "import sys,json; print(json.load(sys.stdin).get('errorMessage','unknown'))")"; fi

step "Step 10: 裸 SQL 请求被拒绝 (切片 34)"
REJECT_RESP=$(curl -s -u runtime:runtime -X POST "$RUNTIME_URL/api/query" \
  -H "Content-Type: application/json" \
  -d '{"sql":"DELETE FROM patients WHERE id = 1","traceId":"e2e-trace-002"}')
REJECT_HTTP=$(echo "$REJECT_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('error',''))")
if echo "$REJECT_HTTP" | grep -q "scriptId"; then pass "Bare SQL request rejected (scriptId required)"; else fail "Bare SQL should be rejected, got: $REJECT_HTTP"; fi

# 总结
echo ""
echo "============================================"
echo " E2E 闭环测试结果"
echo "============================================"
echo -e " 通过: ${GREEN}$PASS_COUNT${NC}"
echo -e " 失败: ${RED}$FAIL_COUNT${NC}"
echo "============================================"

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo -e "${GREEN}🎉 E2E 闭环测试全部通过！${NC}"
    exit 0
else
    echo -e "${RED}E2E 测试存在失败项。${NC}"
    echo "Console log: /tmp/console.log"
    echo "Runtime log: /tmp/runtime.log"
    exit 1
fi
