/* FairShare — front end for the Shared Apartment Utility & Dispute Resolver.
 * Plain JavaScript (no frameworks). Talks to the Java back end through /api/... */
(function () {
  'use strict';

  // ------------------------------------------------------------------ helpers
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
  const money = (v) => inr.format(Number(v || 0));
  const moneyShort = (v) => '₹' + Math.round(Number(v || 0)).toLocaleString('en-IN');
  const signed = (v) => (Math.abs(v) < 0.005 ? money(0) : (v > 0 ? '+' : '−') + money(Math.abs(v)));
  const UT_COLORS = { ELECTRICITY: 'var(--elec)', WATER: 'var(--water)', GAS: 'var(--gas)', INTERNET: 'var(--net)', GROCERIES: 'var(--food)', MISCELLANEOUS: 'var(--misc)' };
  const AVATAR = ['#264d3c', '#b8432f', '#2f78b5', '#7354b0', '#9a6a12', '#2f7d55', '#c8553d', '#4a5a8a'];

  function avatar(name) {
    let h = 0;
    for (const ch of String(name)) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    const initials = String(name).trim().split(/\s+/).map((p) => p[0]).join('').slice(0, 2).toUpperCase();
    return `<span class="avatar" style="background:${AVATAR[h % AVATAR.length]}">${esc(initials)}</span>`;
  }

  function monthLabel(ym) {
    const [y, m] = ym.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
  }

  function shiftMonth(ym, delta) {
    const [y, m] = ym.split('-').map(Number);
    const d = new Date(y, m - 1 + delta, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  async function api(path, opts = {}) {
    const init = { method: opts.method || 'GET', headers: {} };
    if (opts.body !== undefined) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(opts.body);
    }
    const res = await fetch('/api/' + path, init);
    const type = res.headers.get('Content-Type') || '';
    const data = type.includes('json') ? await res.json() : await res.text();
    if (!res.ok) {
      const err = new Error((data && data.error) || ('Request failed: ' + res.status));
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  let toastTimer;
  function toast(msg, bad) {
    const t = $('#toast');
    t.textContent = msg;
    t.className = 'toast' + (bad ? ' bad' : '');
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => (t.hidden = true), bad ? 5000 : 2600);
  }

  function openModal(title, html, onMount) {
    $('#modalTitle').textContent = title;
    $('#modalBody').innerHTML = html;
    $('#modal').hidden = false;
    if (onMount) onMount($('#modalBody'));
    const first = $('#modalBody input, #modalBody select, #modalBody textarea');
    if (first) first.focus();
  }
  function closeModal() { $('#modal').hidden = true; $('#modalBody').innerHTML = ''; }

  function showFormError(form, msg) {
    let box = $('.form-error', form);
    if (!box) {
      box = document.createElement('div');
      box.className = 'form-error';
      form.insertBefore(box, $('.modal-actions', form));
    }
    box.innerHTML = msg ? `<div class="alert bad">⚠ ${esc(msg)}</div>` : '';
  }

  function formData(form) {
    const o = {};
    new FormData(form).forEach((v, k) => (o[k] = v));
    return o;
  }

  // ------------------------------------------------------------------ state
  const state = {
    meta: null,
    month: null,
    view: 'dashboard',
    finalized: [],
    roommates: [],
  };

  const VIEWS = {
    dashboard: { title: 'Dashboard', sub: 'Who owes what this month', render: renderDashboard },
    bills: { title: 'Bills', sub: 'Utility bills and how each one is split', render: renderBills },
    roommates: { title: 'Roommates', sub: 'Who lives here and for how long', render: renderRoommates },
    payments: { title: 'Payments', sub: 'Money roommates sent each other', render: renderPayments },
    disputes: { title: 'Disputes', sub: 'Raise, review and resolve disagreements', render: renderDisputes },
    reports: { title: 'Reports', sub: 'Monthly settlement report (TXT / CSV)', render: renderReports },
    activity: { title: 'Activity log', sub: 'Every change, with date and time (audit.log)', render: renderActivity },
    settings: { title: 'Settings', sub: 'Apartment details and data', render: renderSettings },
  };

  const isFinal = () => state.finalized.includes(state.month);

  async function refreshMeta() {
    state.meta = await api('meta');
    const m = await api('months');
    state.finalized = m.finalized;
    state.roommates = await api('roommates?month=' + state.month);
    $('#aptName').textContent = state.meta.apartment.name;
    const chip = $('#monthStatus');
    chip.textContent = isFinal() ? '✓ Finalised' : 'Draft';
    chip.className = 'status-chip ' + (isFinal() ? 'final' : 'draft');
    try {
      const open = await api('disputes?status=UNRESOLVED');
      const c = $('#disputeCount');
      c.textContent = open.length;
      c.hidden = open.length === 0;
    } catch (e) { /* ignore */ }
  }

  async function render() {
    const v = VIEWS[state.view] || VIEWS.dashboard;
    $('#viewTitle').textContent = v.title;
    $('#viewSub').textContent = v.sub + ' · ' + monthLabel(state.month);
    $$('#nav a').forEach((a) => a.classList.toggle('active', a.dataset.view === state.view));
    $('#monthInput').value = state.month;
    try {
      await refreshMeta();
      const view = $('#view');
      if (!state.meta.hasData && !['settings', 'activity'].includes(state.view)) {
        view.innerHTML = welcomeHtml();
        bindWelcome();
        return;
      }
      await v.render(view);
    } catch (e) {
      $('#view').innerHTML = `<div class="alert bad">Could not load: ${esc(e.message)}. Is the Java server still running?</div>`;
    }
  }

  function go(view) {
    state.view = view;
    if (location.hash !== '#' + view) history.replaceState(null, '', '#' + view);
    render();
  }

  // ------------------------------------------------------------------ welcome
  function welcomeHtml() {
    return `<div class="card welcome">
      <h2>Split utility bills fairly.</h2>
      <p>Add your roommates and their move-in dates, enter each month's electricity, water, gas, internet, groceries and other shared bills,
      and the app works out each person's share, who owes whom, and keeps a record of disputes.</p>
      <div class="row" style="justify-content:center;margin-top:20px">
        <button class="btn primary" id="startFresh">Add first roommate</button>
        <button class="btn" id="loadDemo">Load sample data</button>
      </div>
      <p class="muted" style="font-size:13px;margin-top:18px">Sample data: 4 roommates, Aug–Oct 2026, one move-in, one move-out and two disputes.</p>
    </div>`;
  }
  function bindWelcome() {
    $('#startFresh').onclick = () => roommateForm();
    $('#loadDemo').onclick = loadDemo;
  }
  async function loadDemo() {
    await api('demo', { method: 'POST' });
    state.month = '2026-10';
    toast('Sample data loaded');
    go('dashboard');
  }

  // ------------------------------------------------------------------ dashboard
  async function renderDashboard(view) {
    const [s, trends] = await Promise.all([api('summary?month=' + state.month), api('trends')]);
    const present = s.balances.filter((b) => b.days > 0).length;
    const prevTotal = trends.months.includes(shiftMonth(state.month, -1))
      ? Object.values(trends.series).reduce((sum, arr) => sum + arr[trends.months.indexOf(shiftMonth(state.month, -1))], 0) : null;
    const change = prevTotal ? ((s.grandTotal - prevTotal) / prevTotal) * 100 : null;

    let html = '';
    if (s.unresolvedDisputes > 0) {
      html += `<div class="alert warn">⚑ <div><b>${s.unresolvedDisputes} unresolved dispute(s)</b> for ${monthLabel(state.month)}.
        The month can't be finalised until they are resolved or rejected. <a href="#disputes">Review disputes →</a></div></div>`;
    }
    html += `<div class="grid kpis">
      <div class="card kpi"><div class="label">Shared bills this month</div><div class="value">${moneyShort(s.grandTotal)}</div>
        <div class="sub">${change == null ? s.bills.length + ' bill(s)' : (change >= 0 ? '▲ ' : '▼ ') + Math.abs(change).toFixed(1) + '% vs last month'}</div></div>
      <div class="card kpi"><div class="label">Roommates present</div><div class="value">${present}</div>
        <div class="sub">${s.balances.reduce((a, b) => a + b.days, 0)} person-days</div></div>
      <div class="card kpi"><div class="label">Still to settle</div><div class="value">${moneyShort(s.transfers.reduce((a, t) => a + t.amount, 0))}</div>
        <div class="sub">${s.transfers.length} transfer(s) needed</div></div>
      <div class="card kpi"><div class="label">Open disputes</div><div class="value">${s.unresolvedDisputes}</div>
        <div class="sub">${s.finalized ? 'Month finalised' : 'Month is a draft'}</div></div>
    </div>`;

    html += `<div class="grid two">
      <div class="card">
        <div class="card-head"><div><h3>Settle up</h3><p class="hint">Fewest payments that clear every balance.</p></div>
          ${s.finalized ? '' : '<button class="btn small" id="dashAddBill">+ Add bill</button>'}</div>
        ${settleHtml(s)}
      </div>
      <div class="card">
        <h3>Where the money went</h3><p class="hint">${monthLabel(state.month)} by category</p>
        ${utilitySplitHtml(s)}
      </div>
    </div>`;

    html += `<div class="card"><div class="card-head"><div><h3>Roommate balances</h3>
      <p class="hint">Net = paid to provider + sent − received − fair share. Positive means others owe them.</p></div></div>
      ${balancesTable(s)}</div>`;

    html += `<div class="card"><h3>Monthly trend</h3><p class="hint">Total bills per month, stacked by category (from the generic Ledger&lt;T extends Utility&gt;).</p>
      <div class="chart">${trendChart(trends)}</div></div>`;

    view.innerHTML = html;
    const addBtn = $('#dashAddBill');
    if (addBtn) addBtn.onclick = () => billForm();
    $$('[data-settle]').forEach((b) => (b.onclick = () => {
      const t = s.transfers[Number(b.dataset.settle)];
      paymentForm({ fromId: t.fromId, toId: t.toId, amount: t.amount.toFixed(2), note: 'Settle up' });
    }));
  }

  function settleHtml(s) {
    if (!s.bills.length) return `<div class="empty"><h3>No bills yet</h3><p>Add this month's bills to see who owes whom.</p></div>`;
    if (!s.transfers.length) return `<div class="all-good">✓ Everyone is settled for ${monthLabel(s.month)}.</div>`;
    return s.transfers.map((t, i) => `<div class="transfer">
      ${avatar(t.fromName)}<div><b>${esc(t.fromName)}</b> <span class="arrow">pays →</span> <b>${esc(t.toName)}</b></div>
      <span class="amt">${money(t.amount)}</span>
      ${s.finalized ? '' : `<button class="btn small" data-settle="${i}" title="Record this payment">Mark paid</button>`}
    </div>`).join('');
  }

  function utilitySplitHtml(s) {
    const entries = Object.entries(s.totalsByUtility);
    if (!entries.length) return '<p class="muted">No bills for this month.</p>';
    const names = Object.fromEntries(state.meta.utilities.map((u) => [u.type, u.name]));
    let html = '<div class="split-bar">' + entries.map(([k, v]) =>
      `<div style="width:${(v / s.grandTotal) * 100}%;background:${UT_COLORS[k]}" title="${names[k]} ${money(v)}"></div>`).join('') + '</div>';
    html += '<table><tbody>' + entries.map(([k, v]) => `<tr><td><span class="tag ${k}">${names[k]}</span></td>
      <td class="num">${money(v)}</td><td class="num muted">${((v / s.grandTotal) * 100).toFixed(1)}%</td></tr>`).join('') +
      `<tr><td><b>Total</b></td><td class="num"><b>${money(s.grandTotal)}</b></td><td></td></tr></tbody></table>`;
    return html;
  }

  function balancesTable(s) {
    if (!s.balances.length) return '<p class="muted">No roommates in this month.</p>';
    return `<div class="table-wrap"><table><thead><tr><th>Roommate</th><th class="num">Days</th><th class="num">Fair share</th>
      <th class="num">Paid bills</th><th class="num">Sent</th><th class="num">Received</th><th class="num">Net</th></tr></thead><tbody>
      ${s.balances.map((b) => `<tr><td><div class="row">${avatar(b.name)}<b>${esc(b.name)}</b></div></td>
        <td class="num">${b.days}</td><td class="num">${money(b.share)}</td><td class="num">${money(b.paidToProvider)}</td>
        <td class="num">${money(b.sent)}</td><td class="num">${money(b.received)}</td>
        <td class="num ${b.net > 0.004 ? 'pos' : b.net < -0.004 ? 'neg' : ''}">${signed(b.net)}</td></tr>`).join('')}
      </tbody></table></div>`;
  }

  function trendChart(t) {
    const months = t.months.slice(-8);
    if (!months.length) return '<p class="muted">No history yet.</p>';
    const types = Object.keys(t.series);
    const offset = t.months.length - months.length;
    const totals = months.map((_, i) => types.reduce((a, k) => a + t.series[k][i + offset], 0));
    const max = Math.max(...totals) * 1.15 || 1;
    const W = 720, H = 240, padL = 56, padB = 28, padT = 10;
    const bw = Math.min(64, (W - padL) / months.length * 0.55);
    const step = (W - padL) / months.length;
    let svg = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Monthly utility totals">`;
    for (let g = 0; g <= 4; g++) {
      const v = (max / 4) * g;
      const y = H - padB - ((H - padB - padT) * g) / 4;
      svg += `<line x1="${padL}" x2="${W}" y1="${y}" y2="${y}" stroke="var(--line)" />`;
      svg += `<text x="${padL - 8}" y="${y + 4}" text-anchor="end" font-size="11" fill="var(--ink-3)">${moneyShort(v)}</text>`;
    }
    months.forEach((m, i) => {
      const x = padL + step * i + (step - bw) / 2;
      let y = H - padB;
      types.forEach((k) => {
        const v = t.series[k][i + offset];
        const h = ((H - padB - padT) * v) / max;
        if (h > 0) svg += `<rect x="${x}" y="${y - h}" width="${bw}" height="${h}" fill="${UT_COLORS[k]}" rx="2"><title>${k} ${money(v)}</title></rect>`;
        y -= h;
      });
      svg += `<text x="${x + bw / 2}" y="${y - 6}" text-anchor="middle" font-size="11" font-weight="600" fill="var(--ink-2)">${moneyShort(totals[i])}</text>`;
      const label = monthLabel(m).replace(/ (\d{4})$/, " '$1").replace(/'(\d\d)(\d\d)/, "'$2");
      svg += `<text x="${x + bw / 2}" y="${H - 8}" text-anchor="middle" font-size="12" fill="${m === state.month ? 'var(--ink)' : 'var(--ink-3)'}" font-weight="${m === state.month ? 700 : 400}">${label}</text>`;
    });
    svg += '</svg>';
    const names = Object.fromEntries(state.meta.utilities.map((u) => [u.type, u.name]));
    svg += '<div class="legend">' + types.map((k) => `<span><i style="background:${UT_COLORS[k]}"></i>${names[k]} · avg ${moneyShort(t.stats[k].average)}</span>`).join('') + '</div>';
    return svg;
  }

  // ------------------------------------------------------------------ bills
  async function renderBills(view) {
    const s = await api('summary?month=' + state.month);
    const locked = s.finalized;
    let html = `<div class="row">${locked ? '<div class="alert good">✓ This month is finalised. Re-open it from Reports to make changes.</div>' : ''}
      <span class="spacer"></span>${locked ? '' : '<button class="btn primary" id="addBill">+ Add bill</button>'}</div>`;
    if (!s.bills.length) {
      html += `<div class="card empty"><h3>No bills for ${monthLabel(state.month)}</h3><p>Add the electricity, water, gas, internet, groceries or any other shared bill.</p></div>`;
    } else {
      const strat = Object.fromEntries(state.meta.strategies.map((x) => [x.key, x.name]));
      html += '<div class="bill-list">' + s.bills.map((b) => `
        <div class="card bill ${b.utility}">
          <div class="card-head">
            <div>
              <div class="row"><span class="tag ${b.utility}">${esc(b.utilityName)}</span><span class="muted mono">${b.id}</span>
                ${b.openDisputes ? `<span class="badge OPEN">⚑ ${b.openDisputes} open dispute</span>` : ''}</div>
              <div class="bill-meta" style="margin-top:6px">
                <span>Paid by <b>${esc(b.paidByName)}</b></span>
                <span>Rule: <b>${esc(strat[b.strategy] || b.strategy)}</b></span>
                ${b.fixedCharge > 0 ? `<span>Fixed charge ${money(b.fixedCharge)}</span>` : ''}
                ${b.totalUnits > 0 ? `<span>${b.totalUnits} ${esc(b.unit)} total</span>` : ''}
                ${b.dueDate ? `<span>Due ${esc(b.dueDate)}</span>` : ''}
              </div>
              ${b.notes ? `<div class="muted" style="font-size:13px;margin-top:4px">“${esc(b.notes)}”</div>` : ''}
            </div>
            <div style="text-align:right">
              <div class="amount">${money(b.totalAmount)}</div>
              <div class="row" style="justify-content:flex-end;margin-top:6px">
                <button class="btn small" data-dispute="${b.id}" ${locked ? 'disabled' : ''}>⚑ Dispute</button>
                <button class="btn small" data-edit="${b.id}" ${locked ? 'disabled' : ''}>Edit</button>
                <button class="btn small danger" data-del="${b.id}" ${locked ? 'disabled' : ''}>Delete</button>
              </div>
            </div>
          </div>
          ${b.error ? `<div class="alert bad">${esc(b.error)}</div>` : (b.shares || []).map((sh) => `
            <div class="share-row"><div class="row" style="gap:8px">${avatar(sh.name)}<b>${esc(sh.name)}</b></div>
              <div><div class="why">${esc(sh.explanation)}</div><div class="bar"><i style="width:${(sh.amount / b.totalAmount) * 100}%;background:${UT_COLORS[b.utility]}"></i></div></div>
              <div class="amt">${money(sh.amount)}</div></div>`).join('')}
        </div>`).join('') + '</div>';
    }
    view.innerHTML = html;
    const add = $('#addBill');
    if (add) add.onclick = () => billForm();
    $$('[data-edit]').forEach((b) => (b.onclick = async () => billForm(await api('bills/' + b.dataset.edit))));
    $$('[data-del]').forEach((b) => (b.onclick = async () => {
      if (!confirm('Delete bill ' + b.dataset.del + '? Its disputes are deleted too.')) return;
      try { await api('bills/' + b.dataset.del, { method: 'DELETE' }); toast('Bill deleted'); render(); } catch (e) { toast(e.message, true); }
    }));
    $$('[data-dispute]').forEach((b) => (b.onclick = () => disputeForm(b.dataset.dispute)));
  }

  async function billForm(existing) {
    const month = existing ? existing.month : state.month;
    const roommates = await api('roommates');
    if (!roommates.length) { toast('Add a roommate first', true); return; }
    const utilOpts = state.meta.utilities.map((u) => `<option value="${u.type}" ${existing && existing.utility === u.type ? 'selected' : ''}>${u.name}</option>`).join('');
    const payOpts = roommates.map((r) => `<option value="${r.id}" ${existing && existing.paidById === r.id ? 'selected' : ''}>${esc(r.name)}</option>`).join('');
    const stratOpts = state.meta.strategies.map((s) => `<option value="${s.key}">${s.name}</option>`).join('');
    openModal(existing ? 'Edit bill ' + existing.id : 'Add a bill', `
      <form class="form" id="billForm" novalidate>
        <label class="field">Category<select name="utility" ${existing ? 'disabled' : ''}>${utilOpts}</select></label>
        <label class="field">Billing month<input type="month" name="month" value="${month}" required></label>
        <label class="field">Total amount (₹)<input type="number" name="totalAmount" min="0" step="0.01" value="${existing ? existing.totalAmount : ''}" required placeholder="e.g. 2860"></label>
        <label class="field" id="fixedField">Fixed / meter charge (₹) <small>Shared by days present, rest by the rule</small>
          <input type="number" name="fixedCharge" min="0" step="0.01" value="${existing ? existing.fixedCharge : 0}"></label>
        <label class="field">Paid to provider by<select name="paidById">${payOpts}</select></label>
        <label class="field">Split rule<select name="strategy">${stratOpts}</select><small id="stratHint"></small></label>
        <label class="field">Due date<input type="date" name="dueDate" value="${existing && existing.dueDate ? existing.dueDate : ''}"></label>
        <label class="field">Notes<input type="text" name="notes" value="${existing ? esc(existing.notes) : ''}" placeholder="optional"></label>
        <div class="full">
          <div class="row" style="margin-bottom:6px"><b style="font-size:13px;color:var(--ink-2)">Who shares this bill</b>
            <span class="muted" style="font-size:12px">Days are filled in from move-in / move-out dates. Edit them if someone was away.</span></div>
          <div class="table-wrap"><table class="usage-table"><thead><tr><th></th><th>Roommate</th><th class="num">Days present</th><th class="num" id="unitHead">Units used</th></tr></thead>
            <tbody id="usageRows"></tbody></table></div>
        </div>
        <div class="preview-box" id="previewBox"><h4>Live preview</h4><div id="preview" class="muted">Enter an amount to see each share.</div></div>
        <div class="modal-actions"><button type="button" class="btn" data-cancel>Cancel</button><button class="btn primary" type="submit">${existing ? 'Save changes' : 'Add bill'}</button></div>
      </form>`, (root) => {
      const form = $('#billForm', root);
      const util = () => state.meta.utilities.find((u) => u.type === form.utility.value);
      const usageMap = {};
      if (existing) existing.usage.forEach((u) => (usageMap[u.roommateId] = u));

      async function fillRows(keepExisting) {
        const pre = await api('prefill?month=' + form.month.value);
        $('#usageRows', root).innerHTML = pre.map((p) => {
          const ex = keepExisting ? usageMap[p.roommateId] : null;
          const days = ex ? ex.days : p.days;
          const include = ex ? true : (keepExisting && existing ? false : p.days > 0);
          return `<tr data-rid="${p.roommateId}">
            <td><input type="checkbox" class="inc" ${include ? 'checked' : ''}></td>
            <td><div class="row" style="gap:8px;flex-wrap:nowrap">${avatar(p.name)}${esc(p.name)}</div></td>
            <td class="num"><input type="number" class="days" min="0" max="${p.monthDays}" step="1" value="${days}" style="width:90px"></td>
            <td class="num"><input type="number" class="units" min="0" step="0.01" value="${ex ? ex.units : ''}" placeholder="0" style="width:110px"></td></tr>`;
        }).join('');
        syncUtility();
        preview();
      }

      function syncUtility() {
        const u = util();
        $('#unitHead', root).textContent = 'Units used (' + u.unit + ')';
        $$('.units', root).forEach((i) => { i.disabled = !u.metered; if (!u.metered) i.value = ''; });
        $('#fixedField', root).style.display = u.type === 'ELECTRICITY' ? '' : 'none';
        if (u.type !== 'ELECTRICITY') form.fixedCharge.value = 0;
        form.notes.placeholder = u.type === 'MISCELLANEOUS' ? 'Required: what was it for? (e.g. Plumber visit)' : 'optional';
        $$('option', form.strategy).forEach((o) => {
          const s = state.meta.strategies.find((x) => x.key === o.value);
          o.disabled = s.requiresUsage && !u.metered;
        });
        if (form.strategy.selectedOptions[0].disabled) form.strategy.value = u.defaultStrategy;
        $('#stratHint', root).textContent = u.rule;
      }

      function payload() {
        const d = formData(form);
        d.utility = form.utility.value;
        d.usage = $$('#usageRows tr', root).map((tr) => ({
          roommateId: tr.dataset.rid,
          include: $('.inc', tr).checked,
          days: $('.days', tr).value || 0,
          units: $('.units', tr).value || 0,
        }));
        return d;
      }

      let timer;
      function preview() {
        clearTimeout(timer);
        timer = setTimeout(async () => {
          const box = $('#preview', root);
          if (!box) return;
          if (!(Number(form.totalAmount.value) > 0)) { box.innerHTML = 'Enter an amount to see each share.'; return; }
          try {
            const shares = await api('preview', { method: 'POST', body: payload() });
            box.className = '';
            box.innerHTML = shares.map((s) => `<div class="share-row" style="grid-template-columns:110px 1fr 100px"><b>${esc(s.name)}</b><span class="why">${esc(s.explanation)}</span><span class="amt">${money(s.amount)}</span></div>`).join('');
          } catch (e) {
            box.className = '';
            box.innerHTML = `<span class="neg">⚠ ${esc(e.message)}</span>`;
          }
        }, 250);
      }

      form.strategy.value = existing ? existing.strategy : util().defaultStrategy;
      form.utility.onchange = () => { form.strategy.value = util().defaultStrategy; syncUtility(); preview(); };
      form.month.onchange = () => fillRows(false);
      form.addEventListener('input', preview);
      form.addEventListener('change', preview);
      $('[data-cancel]', root).onclick = closeModal;
      fillRows(true);

      form.onsubmit = async (ev) => {
        ev.preventDefault();
        try {
          if (existing) await api('bills/' + existing.id, { method: 'PUT', body: payload() });
          else await api('bills', { method: 'POST', body: payload() });
          closeModal();
          state.month = form.month.value;
          toast(existing ? 'Bill updated' : 'Bill added');
          render();
        } catch (e) { showFormError(form, e.message); }
      };
    });
  }

  // ------------------------------------------------------------------ roommates
  async function renderRoommates(view) {
    const list = state.roommates;
    view.innerHTML = `<div class="row"><span class="spacer"></span><button class="btn primary" id="addRm">+ Add roommate</button></div>
      <div class="card"><div class="table-wrap"><table><thead><tr><th>Roommate</th><th>Contact</th><th>Moved in</th><th>Moved out</th>
        <th class="num">Days in ${esc(monthLabel(state.month).split(' ')[0])}</th><th>Status</th><th></th></tr></thead><tbody>
        ${list.map((r) => `<tr><td><div class="row">${avatar(r.name)}<div><b>${esc(r.name)}</b><div class="muted mono">${r.id}</div></div></div></td>
          <td>${esc(r.email || '')}<div class="muted">${esc(r.phone || '')}</div></td>
          <td>${esc(r.joinDate)}</td><td>${r.leaveDate ? esc(r.leaveDate) : '<span class="muted">—</span>'}</td>
          <td class="num">${r.daysThisMonth}</td>
          <td>${r.active ? '<span class="badge active">Living here</span>' : '<span class="badge left">Moved out</span>'}</td>
          <td class="num"><button class="btn small" data-edit="${r.id}">Edit</button> <button class="btn small danger" data-del="${r.id}">Remove</button></td></tr>`).join('')}
      </tbody></table></div></div>
      <div class="card"><h3>How occupancy is used</h3><p class="hint" style="margin:0">Days present in a month are worked out from move-in and move-out dates,
        and used by the <b>Prorated by days</b> rule and for electricity fixed charges. Someone who moves in on the 12th only pays for the days they lived here.
        Roommates who have bills or payments can't be deleted. Set a move-out date instead so old months stay correct.</p></div>`;
    $('#addRm').onclick = () => roommateForm();
    $$('[data-edit]').forEach((b) => (b.onclick = () => roommateForm(list.find((r) => r.id === b.dataset.edit))));
    $$('[data-del]').forEach((b) => (b.onclick = async () => {
      if (!confirm('Remove this roommate?')) return;
      try { await api('roommates/' + b.dataset.del, { method: 'DELETE' }); toast('Roommate removed'); render(); } catch (e) { toast(e.message, true); }
    }));
  }

  function roommateForm(r) {
    openModal(r ? 'Edit ' + r.name : 'Add a roommate', `
      <form class="form" id="rmForm" novalidate>
        <label class="field">Name<input type="text" name="name" value="${r ? esc(r.name) : ''}" required></label>
        <label class="field">Email<input type="email" name="email" value="${r ? esc(r.email) : ''}" placeholder="optional"></label>
        <label class="field">Phone<input type="text" name="phone" value="${r ? esc(r.phone) : ''}" placeholder="optional"></label>
        <span></span>
        <label class="field">Move-in date<input type="date" name="joinDate" value="${r ? r.joinDate : state.month + '-01'}" required></label>
        <label class="field">Move-out date <small>Leave empty if still living here</small><input type="date" name="leaveDate" value="${r && r.leaveDate ? r.leaveDate : ''}"></label>
        <div class="modal-actions"><button type="button" class="btn" data-cancel>Cancel</button><button class="btn primary">${r ? 'Save' : 'Add roommate'}</button></div>
      </form>`, (root) => {
      const form = $('#rmForm', root);
      $('[data-cancel]', root).onclick = closeModal;
      form.onsubmit = async (ev) => {
        ev.preventDefault();
        try {
          await api(r ? 'roommates/' + r.id : 'roommates', { method: r ? 'PUT' : 'POST', body: formData(form) });
          closeModal(); toast(r ? 'Saved' : 'Roommate added'); render();
        } catch (e) { showFormError(form, e.message); }
      };
    });
  }

  // ------------------------------------------------------------------ payments
  async function renderPayments(view) {
    const list = await api('payments?month=' + state.month);
    const locked = isFinal();
    view.innerHTML = `<div class="row"><span class="spacer"></span>${locked ? '' : '<button class="btn primary" id="addPay">+ Record payment</button>'}</div>
      <div class="card">${list.length ? `<div class="table-wrap"><table><thead><tr><th>Date</th><th>From</th><th>To</th><th class="num">Amount</th><th>Note</th><th></th></tr></thead><tbody>
        ${list.map((p) => `<tr><td>${esc(p.date)}</td><td><div class="row">${avatar(p.fromName)}${esc(p.fromName)}</div></td>
          <td><div class="row">${avatar(p.toName)}${esc(p.toName)}</div></td><td class="num"><b>${money(p.amount)}</b></td>
          <td class="muted">${esc(p.note)}</td><td class="num">${locked ? '' : `<button class="btn small danger" data-del="${p.id}">Delete</button>`}</td></tr>`).join('')}
        </tbody></table></div>` : `<div class="empty"><h3>No payments for ${monthLabel(state.month)}</h3><p>Use <b>Mark paid</b> on the dashboard, or record one here.</p></div>`}</div>`;
    const add = $('#addPay');
    if (add) add.onclick = () => paymentForm();
    $$('[data-del]').forEach((b) => (b.onclick = async () => {
      if (!confirm('Delete this payment?')) return;
      try { await api('payments/' + b.dataset.del, { method: 'DELETE' }); toast('Payment deleted'); render(); } catch (e) { toast(e.message, true); }
    }));
  }

  function paymentForm(pre = {}) {
    const opts = (sel) => state.roommates.map((r) => `<option value="${r.id}" ${sel === r.id ? 'selected' : ''}>${esc(r.name)}</option>`).join('');
    openModal('Record a payment', `
      <form class="form" id="payForm" novalidate>
        <label class="field">From (who paid)<select name="fromId">${opts(pre.fromId)}</select></label>
        <label class="field">To (who received)<select name="toId">${opts(pre.toId || (state.roommates[1] || {}).id)}</select></label>
        <label class="field">Amount (₹)<input type="number" name="amount" min="0" step="0.01" value="${pre.amount || ''}" required></label>
        <label class="field">Date<input type="date" name="date" value="${state.meta.today}"></label>
        <label class="field">For month<input type="month" name="month" value="${state.month}"></label>
        <label class="field">Note<input type="text" name="note" value="${esc(pre.note || '')}" placeholder="e.g. UPI ref"></label>
        <div class="modal-actions"><button type="button" class="btn" data-cancel>Cancel</button><button class="btn primary">Save payment</button></div>
      </form>`, (root) => {
      const form = $('#payForm', root);
      $('[data-cancel]', root).onclick = closeModal;
      form.onsubmit = async (ev) => {
        ev.preventDefault();
        try { await api('payments', { method: 'POST', body: formData(form) }); closeModal(); toast('Payment recorded'); render(); } catch (e) { showFormError(form, e.message); }
      };
    });
  }

  // ------------------------------------------------------------------ disputes
  let disputeFilter = 'UNRESOLVED';
  async function renderDisputes(view) {
    const list = await api('disputes?status=' + disputeFilter);
    const filters = [['UNRESOLVED', 'Unresolved'], ['ALL', 'All'], ['RESOLVED', 'Resolved'], ['REJECTED', 'Rejected']];
    view.innerHTML = `<div class="row">${filters.map(([k, l]) => `<button class="btn small ${disputeFilter === k ? 'primary' : ''}" data-f="${k}">${l}</button>`).join('')}
        <span class="spacer"></span><button class="btn primary" id="newDispute">+ Raise dispute</button></div>
      ${list.length ? list.map((d) => `<div class="card">
        <div class="card-head"><div>
          <div class="row"><span class="badge ${d.status}">${esc(d.statusLabel)}</span><span class="muted mono">${d.id}</span>
            <span class="muted">on <b>${esc(d.billLabel || d.billId)}</b></span></div>
          <p style="margin:10px 0 4px;font-size:16px">“${esc(d.reason)}”</p>
          <div class="muted" style="font-size:13px">Raised by <b>${esc(d.raisedByName)}</b> on ${esc(d.raisedOn)}${d.closedOn ? ' · closed ' + esc(d.closedOn) : ''}</div>
          ${d.resolutionNote ? `<div class="alert ${d.status === 'RESOLVED' ? 'good' : d.status === 'REJECTED' ? '' : 'warn'}" style="margin-top:10px;${d.status === 'REJECTED' ? 'background:var(--surface-2)' : ''}">✎ ${esc(d.resolutionNote)}</div>` : ''}
        </div>
        <div class="row">${d.unresolved ? `
          ${d.status === 'OPEN' ? `<button class="btn small" data-act="UNDER_REVIEW" data-id="${d.id}">Start review</button>` : ''}
          <button class="btn small primary" data-act="RESOLVED" data-id="${d.id}">Resolve</button>
          <button class="btn small" data-act="REJECTED" data-id="${d.id}">Reject</button>` :
          `<button class="btn small" data-act="OPEN" data-id="${d.id}">Re-open</button>`}</div>
        </div></div>`).join('') : `<div class="card empty"><h3>No disputes here</h3><p>If a share looks wrong, raise a dispute on that bill. The month can't be finalised until it's resolved.</p></div>`}`;
    $$('[data-f]').forEach((b) => (b.onclick = () => { disputeFilter = b.dataset.f; render(); }));
    $('#newDispute').onclick = () => disputeForm();
    $$('[data-act]').forEach((b) => (b.onclick = () => disputeAction(b.dataset.id, b.dataset.act)));
  }

  async function disputeForm(billId) {
    const bills = await api('bills?month=' + state.month);
    if (!bills.length) { toast('No bills in ' + monthLabel(state.month) + ' to dispute', true); return; }
    openModal('Raise a dispute', `
      <form class="form" id="dForm" novalidate>
        <label class="field">Bill<select name="billId">${bills.map((b) => `<option value="${b.id}" ${b.id === billId ? 'selected' : ''}>${b.id} · ${esc(b.utilityName)} · ${money(b.totalAmount)}</option>`).join('')}</select></label>
        <label class="field">Raised by<select name="raisedById">${state.roommates.map((r) => `<option value="${r.id}">${esc(r.name)}</option>`).join('')}</select></label>
        <label class="field full">What's wrong?<textarea name="reason" placeholder="e.g. My sub-meter shows 125 kWh, not 145." required></textarea></label>
        <div class="modal-actions"><button type="button" class="btn" data-cancel>Cancel</button><button class="btn primary">Raise dispute</button></div>
      </form>`, (root) => {
      const form = $('#dForm', root);
      $('[data-cancel]', root).onclick = closeModal;
      form.onsubmit = async (ev) => {
        ev.preventDefault();
        try { await api('disputes', { method: 'POST', body: formData(form) }); closeModal(); toast('Dispute raised'); disputeFilter = 'UNRESOLVED'; go('disputes'); } catch (e) { showFormError(form, e.message); }
      };
    });
  }

  function disputeAction(id, status) {
    if (status === 'OPEN') {
      api(`disputes/${id}/status`, { method: 'POST', body: { status } }).then(() => { toast('Dispute re-opened'); render(); }).catch((e) => toast(e.message, true));
      return;
    }
    const titles = { UNDER_REVIEW: 'Start review', RESOLVED: 'Resolve dispute', REJECTED: 'Reject dispute' };
    openModal(titles[status] + ' ' + id, `
      <form class="form" id="sForm" novalidate>
        <label class="field full">${status === 'UNDER_REVIEW' ? 'Note (optional)' : 'Resolution note (required)'}
          <textarea name="note" placeholder="${status === 'RESOLVED' ? 'e.g. Reading corrected to 125 kWh and bill edited.' : status === 'REJECTED' ? 'e.g. Meter photo confirms 145 kWh.' : 'e.g. Checking the meter photo.'}"></textarea></label>
        ${status === 'RESOLVED' ? '<div class="full muted" style="font-size:13px">Tip: if the bill itself was wrong, edit the bill first, then resolve the dispute.</div>' : ''}
        <div class="modal-actions"><button type="button" class="btn" data-cancel>Cancel</button><button class="btn primary">${titles[status]}</button></div>
      </form>`, (root) => {
      const form = $('#sForm', root);
      $('[data-cancel]', root).onclick = closeModal;
      form.onsubmit = async (ev) => {
        ev.preventDefault();
        try { await api(`disputes/${id}/status`, { method: 'POST', body: { status, note: form.note.value } }); closeModal(); toast('Dispute updated'); render(); } catch (e) { showFormError(form, e.message); }
      };
    });
  }

  // ------------------------------------------------------------------ reports
  async function renderReports(view) {
    const text = await api('report?month=' + state.month);
    const locked = isFinal();
    view.innerHTML = `<div class="card"><div class="card-head"><div><h3>Settlement report · ${monthLabel(state.month)}</h3>
        <p class="hint">Saved as <span class="mono">reports/settlement_${state.month}.txt</span> and <span class="mono">.csv</span> next to the app.</p></div>
        <div class="row">
          <button class="btn" id="saveRep">Save to reports folder</button>
          <a class="btn" href="/api/report?month=${state.month}&format=txt&download=1">⬇ TXT</a>
          <a class="btn" href="/api/report?month=${state.month}&format=csv&download=1">⬇ CSV (Excel)</a>
          ${locked ? '<button class="btn" id="reopen">Re-open month</button>' : '<button class="btn accent" id="finalize">Finalise month</button>'}
        </div></div>
        <div id="finMsg"></div>
        <pre class="report">${esc(text)}</pre></div>`;
    $('#saveRep').onclick = async () => {
      try { const r = await api('report?month=' + state.month, { method: 'POST' }); toast('Saved: ' + r.txt.split(/[\\/]/).pop() + ' and .csv'); } catch (e) { toast(e.message, true); }
    };
    const fin = $('#finalize');
    if (fin) fin.onclick = async () => {
      if (!confirm('Finalise ' + monthLabel(state.month) + '? Bills, payments and disputes for this month will be locked.')) return;
      try { await api(`period/${state.month}/finalize`, { method: 'POST' }); toast('Month finalised, report saved'); render(); } catch (e) {
        $('#finMsg').innerHTML = `<div class="alert bad" style="margin-bottom:14px">⚑ ${esc(e.message)} <a href="#disputes">Go to disputes →</a></div>`;
      }
    };
    const re = $('#reopen');
    if (re) re.onclick = async () => { await api(`period/${state.month}/reopen`, { method: 'POST' }); toast('Month re-opened'); render(); };
  }

  // ------------------------------------------------------------------ activity / settings
  async function renderActivity(view) {
    const lines = await api('audit');
    view.innerHTML = `<div class="card">${lines.length ? lines.map((l) => {
      const [t, a, ...rest] = l.split(' | ');
      return `<div class="log-line"><span class="muted">${esc(t)}</span><span class="act">${esc(a || '')}</span><span>${esc(rest.join(' | '))}</span></div>`;
    }).join('') : '<div class="empty">Nothing logged yet.</div>'}</div>`;
  }

  async function renderSettings(view) {
    const a = state.meta.apartment;
    view.innerHTML = `<div class="grid two">
      <div class="card"><h3>Apartment</h3><p class="hint">Shown on reports.</p>
        <form class="form" id="aptForm">
          <label class="field full">Name<input type="text" name="name" value="${esc(a.name)}"></label>
          <label class="field full">Address<input type="text" name="address" value="${esc(a.address)}"></label>
          <div class="modal-actions" style="justify-content:flex-start"><button class="btn primary">Save</button></div>
        </form></div>
      <div class="card"><h3>Data</h3><p class="hint">Everything is stored as plain files on this computer.</p>
        <p class="mono" style="word-break:break-all">${esc(state.meta.dataDir)}</p>
        <p class="muted" style="font-size:13px">roommates.csv · bills.csv · payments.csv · disputes.csv · finalized_months.txt · apartment.properties · audit.log</p>
        <div class="row" style="margin-top:14px"><button class="btn" id="demoBtn">Load sample data</button><button class="btn danger" id="resetBtn">Delete all data</button></div>
      </div></div>
      <div class="card"><h3>Split rules</h3><p class="hint">Implementations of the <span class="mono">SplitStrategy</span> interface.</p>
        <table><tbody>${state.meta.strategies.map((s) => `<tr><td><b>${esc(s.name)}</b></td><td class="muted">${esc(s.description)}</td></tr>`).join('')}</tbody></table>
        <h3 style="margin-top:18px">Bill categories</h3><p class="hint">Subclasses of the abstract <span class="mono">Utility</span> class.</p>
        <table><tbody>${state.meta.utilities.map((u) => `<tr><td><span class="tag ${u.type}">${esc(u.name)}</span></td><td class="muted">${esc(u.rule)}</td><td class="mono">${esc(u.unit)}</td></tr>`).join('')}</tbody></table></div>`;
    $('#aptForm').onsubmit = async (ev) => {
      ev.preventDefault();
      try { await api('apartment', { method: 'PUT', body: formData(ev.target) }); toast('Saved'); render(); } catch (e) { toast(e.message, true); }
    };
    $('#demoBtn').onclick = async () => { if (confirm('This replaces all current data with sample data. Continue?')) loadDemo(); };
    $('#resetBtn').onclick = async () => {
      if (!confirm('Delete ALL roommates, bills, payments and disputes? This cannot be undone.')) return;
      await api('reset', { method: 'POST' }); toast('All data deleted'); render();
    };
  }

  // ------------------------------------------------------------------ boot
  function init() {
    $$('#nav a').forEach((a) => (a.onclick = () => go(a.dataset.view)));
    $('#modalClose').onclick = closeModal;
    $('#modal').addEventListener('mousedown', (e) => { if (e.target.id === 'modal') closeModal(); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeModal(); });
    $('#prevMonth').onclick = () => { state.month = shiftMonth(state.month, -1); render(); };
    $('#nextMonth').onclick = () => { state.month = shiftMonth(state.month, 1); render(); };
    $('#monthInput').onchange = (e) => { if (e.target.value) { state.month = e.target.value; render(); } };
    window.addEventListener('hashchange', () => {
      const v = location.hash.slice(1);
      if (VIEWS[v] && v !== state.view) go(v);
    });
    const now = new Date();
    state.month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    const v = location.hash.slice(1);
    state.view = VIEWS[v] ? v : 'dashboard';
    render();
  }

  init();
})();
