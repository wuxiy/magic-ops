/* MagicOps 审批动态插件 (magic-editor)
 * 在资源右键菜单中加入「审批动态」，打开自包含面板展示最近审批记录。
 * 数据来自 MagicOps Console 的 /api/approvals（同源 Session 认证）。
 */
(function () {
  'use strict';

  var PANEL_ID = 'magicops-approval-panel';

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function fmtTime(t) {
    if (!t) return '-';
    try {
      return new Date(t).toLocaleString('zh-CN', { hour12: false });
    } catch (e) {
      return String(t);
    }
  }

  function decisionTag(d) {
    var map = {
      APPROVED: ['已批准', '#67c23a'],
      REJECTED: ['已拒绝', '#f56c6c'],
      PENDING: ['待审批', '#e6a23c']
    };
    var m = map[d] || [d || '-', '#909399'];
    return '<span style="display:inline-block;padding:1px 8px;border-radius:10px;font-size:12px;color:#fff;background:' + m[1] + '">' + m[0] + '</span>';
  }

  function renderRows(records) {
    if (!records.length) {
      return '<div style="padding:32px;text-align:center;color:#909399">暂无审批记录</div>';
    }
    var rows = records.map(function (r) {
      return '<tr>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0">' + esc(r.id) + '</td>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0">' + esc(r.submittedBy) + '</td>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0">' + decisionTag(r.decision) + '</td>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0">' + esc(r.decidedBy || '-') + '</td>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0;white-space:nowrap">' + fmtTime(r.submittedAt) + '</td>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0;max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="' + esc(r.comment) + '">' + esc(r.comment || '-') + '</td>' +
        '</tr>';
    }).join('');
    return '<table style="width:100%;border-collapse:collapse;font-size:13px">' +
      '<thead><tr style="background:#fafafa;color:#606266;text-align:left">' +
      '<th style="padding:8px">ID</th><th style="padding:8px">提交人</th><th style="padding:8px">决定</th>' +
      '<th style="padding:8px">审批人</th><th style="padding:8px">提交时间</th><th style="padding:8px">意见</th>' +
      '</tr></thead><tbody>' + rows + '</tbody></table>';
  }

  function openPanel() {
    var existing = document.getElementById(PANEL_ID);
    if (existing) {
      existing.parentNode.removeChild(existing);
      return;
    }

    var panel = document.createElement('div');
    panel.id = PANEL_ID;
    panel.style.cssText = 'position:fixed;top:64px;right:24px;width:640px;max-height:72vh;display:flex;flex-direction:column;' +
      'background:#fff;border:1px solid #e4e7ed;border-radius:8px;box-shadow:0 6px 24px rgba(0,0,0,.16);z-index:99999;' +
      'font-family:-apple-system,"Segoe UI","Noto Sans SC",sans-serif;overflow:hidden;';

    panel.innerHTML =
      '<div style="display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid #ebeef5;background:#f5f7fa">' +
        '<span style="font-weight:600;font-size:14px;color:#303133">审批动态</span>' +
        '<span id="' + PANEL_ID + '-close" style="cursor:pointer;color:#909399;font-size:18px;line-height:1;padding:0 4px">×</span>' +
      '</div>' +
      '<div id="' + PANEL_ID + '-body" style="overflow:auto;padding:8px 12px">加载中...</div>';

    document.body.appendChild(panel);

    document.getElementById(PANEL_ID + '-close').onclick = function () {
      panel.parentNode.removeChild(panel);
    };

    var body = document.getElementById(PANEL_ID + '-body');
    fetch('/api/approvals?page=0&size=20', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (d) {
        body.innerHTML = renderRows(d.content || []);
      })
      .catch(function (e) {
        body.innerHTML = '<div style="padding:32px;text-align:center;color:#f56c6c">加载失败：' + esc(e.message) + '</div>';
      });
  }

  window.MagicApproval = function (ctx) {
    return {
      contextmenu: function (file) {
        if (!file || file.menuType !== 'resource') return [];
        return [{ label: '审批动态', icon: 'log', onClick: openPanel }];
      }
    };
  };
})();
