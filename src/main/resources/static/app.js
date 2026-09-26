/* ====================== 植物园游线规划器 · 前端 ====================== */

const SVG_NS = 'http://www.w3.org/2000/svg';
const TYPE_META = {
  GREENHOUSE:     { label: '温室',     icon: '🏛️', color: '#7b4bb0' },
  FLOWER_BORDER:  { label: '花境',     icon: '🌸', color: '#d6336c' },
  LAKESIDE_WALK:  { label: '湖边栈道', icon: '🌊', color: '#1971c2' },
  REST_AREA:      { label: '休息点',   icon: '🪑', color: '#7a8a66' },
};

const state = {
  nodes: [], edges: [], modes: [],
  nodeById: new Map(),
  startId: null, endId: null,
  mode: 'SHORTEST', maxSlope: 8,
  route: null,
};

/* ---------------- HTTP ---------------- */

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(path, { ...options, headers });
  const text = await res.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch { data = { error: text }; }
  if (!res.ok) throw new Error(data.error || ('HTTP ' + res.status));
  return data;
}

function adminHeaders() {
  const token = localStorage.getItem('adminToken') || '';
  return { 'X-Admin-Token': token };
}

async function adminApi(method, path, body) {
  return api(path, {
    method,
    headers: adminHeaders(),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

function toast(msg, isError = false) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className = 'toast' + (isError ? ' error' : '');
  el.hidden = false;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { el.hidden = true; }, 2600);
}

/* ---------------- 初始化 ---------------- */

let _initialized = false;
async function init() {
  if (_initialized) return;
  _initialized = true;
  bindTabs();
  bindVisitorControls();
  bindAdminControls();
  await loadGarden();
}
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}

async function loadGarden() {
  const data = await api('/api/garden');
  state.nodes = data.nodes;
  state.edges = data.edges;
  state.modes = data.modes;
  state.nodeById = new Map(data.nodes.map(n => [n.id, n]));
  // 选中节点若已被删除则清空
  if (state.startId && !state.nodeById.has(state.startId)) state.startId = null;
  if (state.endId && !state.nodeById.has(state.endId)) state.endId = null;
  renderModeCards();
  renderEndpointSelects();
  renderMap();
  renderLegend();
  renderAdminNodeTable();
  renderAdminEdgeTable();
  populateAdminSelects();
}

/* ---------------- Tab 切换 ---------------- */

function bindTabs() {
  document.querySelectorAll('.tab').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach(b => b.classList.toggle('active', b === btn));
      document.querySelectorAll('.tab-panel').forEach(p =>
        p.classList.toggle('active', p.id === 'tab-' + btn.dataset.tab));
    });
  });
}

/* ---------------- 偏好卡片 + 起终点 ---------------- */

function renderModeCards() {
  const wrap = document.getElementById('mode-cards');
  wrap.innerHTML = '';
  state.modes.forEach(m => {
    const card = document.createElement('div');
    card.className = 'mode-card' + (m.value === state.mode ? ' selected' : '');
    card.innerHTML = `<b>${m.label}</b><span>${m.description}</span>`;
    card.addEventListener('click', () => {
      state.mode = m.value;
      renderModeCards();
      document.getElementById('row-max-slope').hidden = (m.value !== 'EASY');
    });
    wrap.appendChild(card);
  });
}

function renderEndpointSelects() {
  const s = document.getElementById('sel-start');
  const e = document.getElementById('sel-end');
  s.innerHTML = '<option value="">— 请选择起点 —</option>';
  e.innerHTML = '<option value="">— 请选择终点 —</option>';
  state.nodes.forEach(n => {
    const opt = (sel, id) => {
      const o = document.createElement('option');
      o.value = n.id;
      o.textContent = `${TYPE_META[n.type].icon} ${n.name}`;
      if (n.id === id) o.selected = true;
      sel.appendChild(o);
    };
    opt(s, state.startId);
    opt(e, state.endId);
  });
}

function bindVisitorControls() {
  document.getElementById('sel-start').addEventListener('change', ev => {
    state.startId = ev.target.value || null;
    clearRoute();
    renderMap();
  });
  document.getElementById('sel-end').addEventListener('change', ev => {
    state.endId = ev.target.value || null;
    clearRoute();
    renderMap();
  });
  document.getElementById('inp-max-slope').addEventListener('input', ev => {
    state.maxSlope = ev.target.value === '' ? null : Number(ev.target.value);
  });
  document.getElementById('btn-clear').addEventListener('click', () => {
    state.startId = null; state.endId = null;
    clearRoute();
    renderEndpointSelects();
    renderMap();
  });
  document.getElementById('btn-plan').addEventListener('click', planRoute);
}

/* ---------------- SVG 地图 ---------------- */

function svgEl(tag, attrs = {}, text) {
  const el = document.createElementNS(SVG_NS, tag);
  Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, v));
  if (text !== undefined) el.textContent = text;
  return el;
}

function renderMap() {
  const svg = document.getElementById('map');
  svg.innerHTML = '';

  // 装饰：湖泊与山丘
  svg.appendChild(svgEl('ellipse', { cx: 885, cy: 690, rx: 195, ry: 125, fill: '#cfe7f4', opacity: '.85' }));
  svg.appendChild(svgEl('text', { x: 885, y: 700, 'font-size': 17, fill: '#3a7ca5', 'text-anchor': 'middle', opacity: '.8' }, '静心湖'));
  svg.appendChild(svgEl('path', { d: 'M250 210 Q400 60 540 210 Z', fill: '#dce8d0', opacity: '.8' }));
  svg.appendChild(svgEl('text', { x: 395, y: 175, 'font-size': 14, fill: '#7d8f63', 'text-anchor': 'middle', opacity: '.9' }, '小青山'));

  const routeEdgeIds = new Set((state.route?.segments || []).map(s => s.edgeId));
  const routeNodeOrder = new Map((state.route?.nodes || []).map((n, i) => [n.id, i + 1]));

  // 路段
  state.edges.forEach(edge => {
    const a = state.nodeById.get(edge.fromId);
    const b = state.nodeById.get(edge.toId);
    if (!a || !b) return;
    const cls = 'edge' + (edge.strollerFriendly ? '' : ' stroller-no') + (routeEdgeIds.has(edge.id) ? ' route' : '');
    svg.appendChild(svgEl('line', {
      x1: a.x, y1: a.y, x2: b.x, y2: b.y, class: cls,
    }));
  });

  // 节点
  state.nodes.forEach(n => {
    const meta = TYPE_META[n.type];
    const g = svgEl('g', { class: 'node' + (n.id === state.startId || n.id === state.endId ? ' endpoint' : '') });
    g.addEventListener('click', () => onNodeClicked(n.id));

    g.appendChild(svgEl('circle', { cx: n.x, cy: n.y, r: 17, fill: meta.color, class: 'node-circle' }));
    g.appendChild(svgEl('text', { x: n.x, y: n.y - 2, class: 'node-emoji' }, meta.icon));
    g.appendChild(svgEl('text', { x: n.x, y: n.y + 35, class: 'node-label' }, n.name));

    // 规划后的顺序编号
    if (routeNodeOrder.has(n.id)) {
      const ox = n.x + 15, oy = n.y - 15;
      g.appendChild(svgEl('circle', { cx: ox, cy: oy, r: 11, fill: '#e63946', stroke: '#fff', 'stroke-width': 2 }));
      g.appendChild(svgEl('text', { x: ox, y: oy + 1, class: 'node-order' }, String(routeNodeOrder.get(n.id))));
    }
    svg.appendChild(g);
  });

  // 起点/终点徽标（未规划时显示起/终）
  if (!state.route) {
    if (state.startId) endpointBadge(svg, state.nodeById.get(state.startId), '起', '#2d6a4f');
    if (state.endId) endpointBadge(svg, state.nodeById.get(state.endId), '终', '#e63946');
  }
}

function endpointBadge(svg, n, text, color) {
  if (!n) return;
  const x = n.x - 15, y = n.y - 15;
  svg.appendChild(svgEl('circle', { cx: x, cy: y, r: 11, fill: color, stroke: '#fff', 'stroke-width': 2 }));
  svg.appendChild(svgEl('text', { x, y: y + 1, class: 'node-order' }, text));
}

function onNodeClicked(id) {
  // 第一次点击设为起点，第二次（不同节点）设为终点；再点则重新开始
  if (!state.startId || (state.startId && state.endId)) {
    state.startId = id;
    state.endId = null;
  } else if (id === state.startId) {
    state.startId = null;
  } else {
    state.endId = id;
  }
  clearRoute();
  renderEndpointSelects();
  renderMap();
}

function renderLegend() {
  const el = document.getElementById('legend');
  const items = Object.values(TYPE_META).map(m =>
    `<span class="item"><span style="width:12px;height:12px;border-radius:50%;background:${m.color};display:inline-block"></span>${m.icon} ${m.label}</span>`);
  items.push(`<span class="item"><span class="swatch" style="background:#b9cdbf"></span>可推车</span>`);
  items.push(`<span class="item"><span class="swatch" style="background:#e76f51;background-image:repeating-linear-gradient(90deg,#e76f51 0 6px,#f4f6f1 6px 11px)"></span>坡陡/不可推车</span>`);
  items.push(`<span class="item"><span class="swatch" style="background:#e63946"></span>推荐路线</span>`);
  el.innerHTML = items.join('');
}

/* ---------------- 规划路线 ---------------- */

function clearRoute() {
  state.route = null;
  document.getElementById('route-result').hidden = true;
  document.getElementById('route-error').hidden = true;
}

async function planRoute() {
  if (!state.startId) { toast('请先选择起点', true); return; }
  if (!state.endId) { toast('请先选择终点', true); return; }
  if (state.startId === state.endId) { toast('起点和终点不能相同', true); return; }

  const btn = document.getElementById('btn-plan');
  btn.disabled = true; btn.textContent = '规划中…';
  try {
    const params = new URLSearchParams({ start: state.startId, end: state.endId, mode: state.mode });
    if (state.mode === 'EASY' && state.maxSlope !== null && state.maxSlope !== '') {
      params.set('maxSlope', String(state.maxSlope));
    }
    const r = await api('/api/route?' + params.toString());
    state.route = r;
    renderMap();
    renderResult(r);
  } catch (err) {
    toast(err.message, true);
  } finally {
    btn.disabled = false; btn.textContent = '🧭 开始规划';
  }
}

function renderResult(r) {
  const box = document.getElementById('route-result');
  const errBox = document.getElementById('route-error');
  if (!r.found) {
    box.hidden = true;
    errBox.hidden = false;
    errBox.innerHTML = `🚫 <b>暂无可行路线</b><br><span style="font-size:13.5px">${r.reason}</span>`;
    return;
  }
  errBox.hidden = true;
  box.hidden = false;

  document.getElementById('result-mode').textContent = r.modeLabel;
  const stats = [
    `${r.totalDistanceMeters} 米|总距离`,
    `约 ${r.estimatedMinutes} 分钟|预计步行`,
    `${fmtNum(r.maxSlopePercent)}%|最大坡度`,
    `${r.allStrollerFriendly ? '全程适合' : '部分不适合'}|推车通行`,
  ];
  document.getElementById('result-summary').innerHTML = stats.map(s => {
    const [num, lbl] = s.split('|');
    return `<div class="stat"><div class="num">${num}</div><div class="lbl">${lbl}</div></div>`;
  }).join('');

  document.getElementById('result-tips').innerHTML =
    (r.tips || []).map(t => `<div>💡 ${t}</div>`).join('');

  document.getElementById('result-segments').innerHTML = r.segments.map(seg => {
    const fromMeta = TYPE_META[seg.from.type];
    const toMeta = TYPE_META[seg.to.type];
    return `<li class="segment">
      <div class="seg-num">${seg.order}</div>
      <div class="seg-body">
        <div class="seg-text">${seg.instruction}</div>
        <div class="seg-meta">
          <span class="tag dist">🚶 ${seg.distanceMeters} 米</span>
          <span class="tag slope">${seg.slopePercent > 0 ? '⛰️ 坡度 ' + fmtNum(seg.slopePercent) + '%' : '🚳 路面平坦'}</span>
          <span class="tag ${seg.strollerFriendly ? 'stroller-yes' : 'stroller-no'}">
            ${seg.strollerFriendly ? '🍼 可推车' : '⚠️ 不适合推车'}
          </span>
          <span class="tag dist">${fromMeta.icon}→${toMeta.icon}</span>
        </div>
      </div>
    </li>`;
  }).join('');
}

function fmtNum(v) {
  return Number.isInteger(v) ? String(v) : String(Math.round(v * 10) / 10);
}

/* ====================== 管理后台 ====================== */

function bindAdminControls() {
  // 口令
  const saved = localStorage.getItem('adminToken');
  if (saved) {
    document.getElementById('inp-token').value = saved;
    setTokenStatus(true);
  }
  document.getElementById('btn-save-token').addEventListener('click', () => {
    const t = document.getElementById('inp-token').value.trim();
    if (!t) { toast('请输入口令', true); return; }
    localStorage.setItem('adminToken', t);
    setTokenStatus(true);
    toast('口令已保存');
  });

  // 节点表单
  document.getElementById('form-node').addEventListener('submit', async ev => {
    ev.preventDefault();
    const id = document.getElementById('node-id').value;
    const body = {
      name: document.getElementById('node-name').value.trim(),
      type: document.getElementById('node-type').value,
      description: document.getElementById('node-desc').value.trim(),
      x: Number(document.getElementById('node-x').value || 500),
      y: Number(document.getElementById('node-y').value || 400),
    };
    try {
      if (id) { await adminApi('PUT', '/api/admin/nodes/' + encodeURIComponent(id), body); toast('节点已更新'); }
      else { await adminApi('POST', '/api/admin/nodes', body); toast('节点已新增'); }
      resetNodeForm();
      await loadGarden();
    } catch (e) { toast(e.message, true); }
  });
  document.getElementById('btn-node-cancel').addEventListener('click', resetNodeForm);

  // 路段表单
  document.getElementById('form-edge').addEventListener('submit', async ev => {
    ev.preventDefault();
    const id = document.getElementById('edge-id').value;
    const from = document.getElementById('edge-from').value;
    const to = document.getElementById('edge-to').value;
    if (from === to) { toast('请选择两个不同的节点', true); return; }
    const body = {
      distanceMeters: Number(document.getElementById('edge-dist').value),
      slopePercent: Number(document.getElementById('edge-slope').value || 0),
      strollerFriendly: document.getElementById('edge-stroller').checked,
      name: document.getElementById('edge-name').value.trim(),
    };
    try {
      if (id) { await adminApi('PUT', '/api/admin/edges/' + encodeURIComponent(id), body); toast('路段已更新'); }
      else { await adminApi('POST', '/api/admin/edges', { fromId: from, toId: to, ...body }); toast('路段已新增'); }
      resetEdgeForm();
      await loadGarden();
    } catch (e) { toast(e.message, true); }
  });
  document.getElementById('btn-edge-cancel').addEventListener('click', resetEdgeForm);
}

function setTokenStatus(ok) {
  document.getElementById('token-status').textContent = ok ? '✅ 已保存，将随管理操作发送' : '';
}

function populateAdminSelects() {
  const from = document.getElementById('edge-from');
  const to = document.getElementById('edge-to');
  from.innerHTML = to.innerHTML = '<option value="">请选择</option>';
  state.nodes.forEach(n => {
    [from, to].forEach(sel => {
      const o = document.createElement('option');
      o.value = n.id;
      o.textContent = `${TYPE_META[n.type].icon} ${n.name}`;
      sel.appendChild(o);
    });
  });
}

/* ---- 节点表格 ---- */

function renderAdminNodeTable() {
  const tbody = document.querySelector('#table-nodes tbody');
  tbody.innerHTML = state.nodes.map(n => `
    <tr>
      <td style="width:26%"><b>${TYPE_META[n.type].icon} ${esc(n.name)}</b></td>
      <td style="width:18%"><span class="type-chip">${TYPE_META[n.type].label}</span></td>
      <td class="cell-desc" title="${esc(n.description || '')}" style="width:30%">${esc(n.description || '—')}</td>
      <td style="width:26%;text-align:right">
        <button class="btn btn-sm btn-edit" data-act="edit-node" data-id="${n.id}">编辑</button>
        <button class="btn btn-sm btn-danger" data-act="del-node" data-id="${n.id}">删除</button>
      </td>
    </tr>`).join('');

  tbody.querySelectorAll('button').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id = btn.dataset.id;
      const n = state.nodeById.get(id);
      if (btn.dataset.act === 'edit-node') {
        document.getElementById('node-id').value = n.id;
        document.getElementById('node-name').value = n.name;
        document.getElementById('node-type').value = n.type;
        document.getElementById('node-desc').value = n.description || '';
        document.getElementById('node-x').value = Math.round(n.x);
        document.getElementById('node-y').value = Math.round(n.y);
        document.getElementById('btn-node-submit').textContent = '💾 保存修改';
        document.getElementById('btn-node-cancel').hidden = false;
        window.scrollTo({ top: document.getElementById('form-node').offsetTop, behavior: 'smooth' });
      } else if (confirm(`确定删除节点「${n.name}」吗？相连的 ${state.edges.filter(e => e.fromId === id || e.toId === id).length} 段路段将一并删除。`)) {
        try {
          await adminApi('DELETE', '/api/admin/nodes/' + encodeURIComponent(id));
          toast('节点已删除');
          resetNodeForm();
          await loadGarden();
        } catch (e) { toast(e.message, true); }
      }
    });
  });
}

function resetNodeForm() {
  document.getElementById('form-node').reset();
  document.getElementById('node-id').value = '';
  document.getElementById('node-x').value = 500;
  document.getElementById('node-y').value = 400;
  document.getElementById('btn-node-submit').textContent = '＋ 新增节点';
  document.getElementById('btn-node-cancel').hidden = true;
}

/* ---- 路段表格 ---- */

function renderAdminEdgeTable() {
  const tbody = document.querySelector('#table-edges tbody');
  tbody.innerHTML = state.edges.map(e => {
    const a = state.nodeById.get(e.fromId), b = state.nodeById.get(e.toId);
    const title = e.name ? esc(e.name) : `${a.name} ↔ ${b.name}`;
    return `
    <tr>
      <td style="width:42%"><b>${title}</b>
        <div class="cell-desc">${a ? esc(a.name) : '?'} ↔ ${b ? esc(b.name) : '?'}</div>
      </td>
      <td style="width:16%">${e.distanceMeters} 米</td>
      <td style="width:14%">${fmtNum(e.slopePercent)}%</td>
      <td style="width:12%">${e.strollerFriendly ? '✅' : '🚫'}</td>
      <td style="width:16%;text-align:right">
        <button class="btn btn-sm btn-edit" data-act="edit-edge" data-id="${e.id}">编辑</button>
        <button class="btn btn-sm btn-danger" data-act="del-edge" data-id="${e.id}">删除</button>
      </td>
    </tr>`;
  }).join('');

  tbody.querySelectorAll('button').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id = btn.dataset.id;
      const e = state.edges.find(x => x.id === id);
      if (btn.dataset.act === 'edit-edge') {
        document.getElementById('edge-id').value = e.id;
        document.getElementById('edge-from').value = e.fromId;
        document.getElementById('edge-to').value = e.toId;
        document.getElementById('edge-dist').value = e.distanceMeters;
        document.getElementById('edge-slope').value = e.slopePercent;
        document.getElementById('edge-stroller').checked = e.strollerFriendly;
        document.getElementById('edge-name').value = e.name || '';
        document.getElementById('edge-from').disabled = true;
        document.getElementById('edge-to').disabled = true;
        document.getElementById('btn-edge-submit').textContent = '💾 保存修改';
        document.getElementById('btn-edge-cancel').hidden = false;
        window.scrollTo({ top: document.getElementById('form-edge').offsetTop, behavior: 'smooth' });
      } else if (confirm('确定删除该路段吗？')) {
        try {
          await adminApi('DELETE', '/api/admin/edges/' + encodeURIComponent(id));
          toast('路段已删除');
          resetEdgeForm();
          await loadGarden();
        } catch (ex) { toast(ex.message, true); }
      }
    });
  });
}

function resetEdgeForm() {
  document.getElementById('form-edge').reset();
  document.getElementById('edge-id').value = '';
  document.getElementById('edge-slope').value = 0;
  document.getElementById('edge-stroller').checked = true;
  document.getElementById('edge-from').disabled = false;
  document.getElementById('edge-to').disabled = false;
  document.getElementById('btn-edge-submit').textContent = '＋ 新增路段';
  document.getElementById('btn-edge-cancel').hidden = true;
}

function esc(s) {
  return String(s).replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
