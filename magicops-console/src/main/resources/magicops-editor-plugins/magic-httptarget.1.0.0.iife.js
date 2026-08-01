/* MagicOps HTTP 目标管理插件 (magic-editor)
 * 在资源右键菜单中加入「HTTP 目标管理」，打开自包含面板对 HTTP 适配目标做增删查。
 * 数据来自 MagicOps Console 的 /api/httptargets（同源 Session 认证）。
 */
(function () {
  'use strict';

  var PANEL_ID = 'magicops-httptarget-panel';
  var API = '/api/httptargets';

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function removePanel() {
    var el = document.getElementById(PANEL_ID);
    if (el) el.parentNode.removeChild(el);
  }

  function renderRows(targets) {
    if (!targets.length) {
      return '<tr><td colspan="5" style="padding:28px;text-align:center;color:#909399">暂无 HTTP 目标</td></tr>';
    }
    return targets.map(function (t) {
      var status = t.enabled
        ? '<span style="color:#67c23a">启用</span>'
        : '<span style="color:#909399">禁用</span>';
      return '<tr>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0">' + esc(t.name) + '</td>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0;max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="' + esc(t.baseUrl) + '">' + esc(t.baseUrl) + '</td>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0">' + esc(t.authType || 'NONE') + '</td>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0">' + status + '</td>' +
        '<td style="padding:8px;border-bottom:1px solid #f0f0f0"><a href="javascript:void(0)" data-del="' + esc(t.id) + '" style="color:#f56c6c;text-decoration:none">删除</a></td>' +
        '</tr>';
    }).join('');
  }

  function loadList(body) {
    body.innerHTML = '<div style="padding:24px;text-align:center;color:#909399">加载中...</div>';
    fetch(API, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (list) {
        body.innerHTML =
          '<table style="width:100%;border-collapse:collapse;font-size:13px">' +
          '<thead><tr style="background:#fafafa;color:#606266;text-align:left">' +
          '<th style="padding:8px">名称</th><th style="padding:8px">基础 URL</th><th style="padding:8px">认证</th>' +
          '<th style="padding:8px">状态</th><th style="padding:8px">操作</th>' +
          '</tr></thead><tbody>' + renderRows(list || []) + '</tbody></table>';
        bindDeletes(body);
      })
      .catch(function (e) {
        body.innerHTML = '<div style="padding:24px;text-align:center;color:#f56c6c">加载失败：' + esc(e.message) + '</div>';
      });
  }

  function bindDeletes(body) {
    var links = body.querySelectorAll('a[data-del]');
    for (var i = 0; i < links.length; i++) {
      links[i].onclick = function () {
        var id = this.getAttribute('data-del');
        if (!window.confirm('确定删除该 HTTP 目标？')) return;
        fetch(API + '/' + encodeURIComponent(id), { method: 'DELETE', credentials: 'same-origin' })
          .then(function () { loadList(body); })
          .catch(function (e) { window.alert('删除失败：' + e.message); });
      };
    }
  }

  function buildForm(body) {
    var field = 'display:block;width:100%;padding:7px 10px;margin-top:4px;border:1px solid #dcdfe6;border-radius:4px;font-size:13px;box-sizing:border-box';
    var label = 'display:block;font-size:12px;color:#606266;margin-top:10px';
    var wrap = document.createElement('div');
    wrap.style.cssText = 'padding:12px 16px;border-top:1px solid #ebeef5;background:#fafbfc';
    wrap.innerHTML =
      '<div style="font-weight:600;font-size:13px;color:#303133;margin-bottom:4px">新建 HTTP 目标</div>' +
      '<label style="' + label + '">名称<input id="ht-name" style="' + field + '" placeholder="order-service"></label>' +
      '<label style="' + label + '">基础 URL<input id="ht-url" style="' + field + '" placeholder="http://10.0.0.5:8080"></label>' +
      '<label style="' + label + '">允许路径（可选）<input id="ht-paths" style="' + field + '" placeholder="/api/**"></label>' +
      '<label style="' + label + '">认证类型<select id="ht-auth" style="' + field + '">' +
        '<option value="NONE">NONE</option><option value="BASIC">BASIC</option><option value="BEARER">BEARER</option><option value="API_KEY">API_KEY</option>' +
      '</select></label>' +
      '<div style="margin-top:12px;text-align:right">' +
        '<button id="ht-cancel" style="padding:7px 14px;border:1px solid #dcdfe6;background:#fff;border-radius:4px;cursor:pointer;font-size:13px">取消</button>' +
        '<button id="ht-save" style="padding:7px 14px;border:none;background:#409eff;color:#fff;border-radius:4px;cursor:pointer;font-size:13px;margin-left:8px">保存</button>' +
      '</div>';
    return wrap;
  }

  function openPanel() {
    var existing = document.getElementById(PANEL_ID);
    if (existing) {
      removePanel();
      return;
    }

    var panel = document.createElement('div');
    panel.id = PANEL_ID;
    panel.style.cssText = 'position:fixed;top:64px;right:24px;width:600px;max-height:76vh;display:flex;flex-direction:column;' +
      'background:#fff;border:1px solid #e4e7ed;border-radius:8px;box-shadow:0 6px 24px rgba(0,0,0,.16);z-index:99999;' +
      'font-family:-apple-system,"Segoe UI","Noto Sans SC",sans-serif;overflow:hidden;';

    panel.innerHTML =
      '<div style="display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid #ebeef5;background:#f5f7fa">' +
        '<span style="font-weight:600;font-size:14px;color:#303133">HTTP 目标管理</span>' +
        '<span style="display:flex;align-items:center;gap:12px">' +
          '<a id="' + PANEL_ID + '-add" href="javascript:void(0)" style="color:#409eff;text-decoration:none;font-size:13px">+ 新建</a>' +
          '<span id="' + PANEL_ID + '-close" style="cursor:pointer;color:#909399;font-size:18px;line-height:1;padding:0 4px">×</span>' +
        '</span>' +
      '</div>' +
      '<div id="' + PANEL_ID + '-body" style="overflow:auto;flex:1"></div>';

    document.body.appendChild(panel);

    var body = document.getElementById(PANEL_ID + '-body');
    document.getElementById(PANEL_ID + '-close').onclick = removePanel;
    document.getElementById(PANEL_ID + '-add').onclick = function () {
      var form = buildForm(body);
      panel.appendChild(form);
      form.querySelector('#ht-cancel').onclick = function () { form.parentNode.removeChild(form); };
      form.querySelector('#ht-save').onclick = function () {
        var name = form.querySelector('#ht-name').value.trim();
        var baseUrl = form.querySelector('#ht-url').value.trim();
        if (!name || !baseUrl) { window.alert('请填写名称和基础 URL'); return; }
        var payload = {
          name: name,
          baseUrl: baseUrl,
          allowedPaths: form.querySelector('#ht-paths').value.trim(),
          authType: form.querySelector('#ht-auth').value,
          enabled: true,
          requireEncryption: false
        };
        fetch(API, {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
          body: JSON.stringify(payload)
        })
          .then(function (r) {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
          })
          .then(function () {
            form.parentNode.removeChild(form);
            loadList(body);
          })
          .catch(function (e) { window.alert('保存失败：' + e.message); });
      };
    };

    loadList(body);
  }

  window.MagicHttpTarget = function (ctx) {
    return {
      contextmenu: function (file) {
        if (!file || file.menuType !== 'resource') return [];
        return [{ label: 'HTTP 目标管理', icon: 'datasource', onClick: openPanel }];
      }
    };
  };
})();
