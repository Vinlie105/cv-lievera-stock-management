// DOM helpers. Everything is built with createElement/textContent, never innerHTML, so API data cannot inject markup.

export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === false || v == null) continue;
    if (k === 'class') el.className = v;
    else if (k === 'text') el.textContent = v;
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
    else if (v === true) el.setAttribute(k, '');
    else el.setAttribute(k, String(v));
  }
  for (const c of children.flat()) {
    if (c == null || c === false) continue;
    el.append(c.nodeType ? c : document.createTextNode(String(c)));
  }
  return el;
}

export function mount(node) {
  const app = document.getElementById('app');
  app.replaceChildren(node);
}

let toastTimer;
export function toast(message, isError = false) {
  const t = document.getElementById('toast');
  t.textContent = message;
  t.className = 'toast' + (isError ? ' error' : '');
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, isError ? 6000 : 3200);
}

export const money = (n) => (n == null ? '-' : 'Rp ' + Number(n).toLocaleString('id-ID', { maximumFractionDigits: 2 }));
export const dt = (s) => (s ? new Date(s).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' }) : '-');

export function pill(text, kind = '') { return h('span', { class: 'pill ' + kind, text }); }
export function alertBox(text, kind = 'error') { return h('div', { class: 'alert ' + kind, role: kind === 'error' ? 'alert' : 'status', text }); }

/** Labelled form field. `input` is a prebuilt element. */
export function field(label, input, hint) {
  const id = 'f' + Math.random().toString(36).slice(2, 9);
  input.id = id;
  return h('div', { class: 'field' }, h('label', { for: id, text: label }), input, hint ? h('div', { class: 'hint', text: hint }) : null);
}

export function input(name, opts = {}) {
  return h('input', { name, type: opts.type || 'text', value: opts.value ?? '', required: !!opts.required, autocomplete: opts.autocomplete || 'off',
    inputmode: opts.inputmode, maxlength: opts.maxlength, min: opts.min, step: opts.step, placeholder: opts.placeholder, autofocus: !!opts.autofocus });
}

export function select(name, options, value) {
  const s = h('select', { name }, options.map(([v, label]) => h('option', { value: v, text: label })));
  s.value = value ?? '';
  return s;
}

/** Modal form. onSubmit(values) may throw; the message is shown inline and the dialog stays open. */
export function formDialog({ title, fields, submitLabel = 'Save', onSubmit, extra }) {
  const dlg = h('dialog', { 'aria-label': title });
  const errBox = h('div');
  const form = h('form', { method: 'dialog', novalidate: true });
  const submit = h('button', { class: 'primary', type: 'submit', text: submitLabel });
  form.append(h('h2', { text: title }), errBox, ...fields, extra || null,
    h('div', { class: 'dialog-actions' }, h('button', { type: 'button', text: 'Cancel', onclick: () => dlg.close() }), submit));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!form.checkValidity()) { form.reportValidity(); return; }
    submit.disabled = true;
    errBox.replaceChildren();
    try {
      await onSubmit(formValues(form));
      dlg.close();
    } catch (err) {
      errBox.replaceChildren(alertBox(describe(err)));
    } finally {
      submit.disabled = false;
    }
  });
  dlg.append(form);
  dlg.addEventListener('close', () => dlg.remove());
  document.body.append(dlg);
  dlg.showModal();
  return dlg;
}

export function describe(err) {
  if (err && err.fieldErrors) return Object.values(err.fieldErrors).join(' · ');
  return (err && err.message) || 'Something went wrong';
}

export function formValues(form) {
  const out = {};
  for (const el of form.elements) {
    if (!el.name) continue;
    out[el.name] = el.type === 'checkbox' ? el.checked : el.value;
  }
  return out;
}

export function confirmDialog(title, message, confirmLabel = 'Confirm') {
  return new Promise((resolve) => {
    const dlg = h('dialog', { 'aria-label': title });
    let result = false;
    dlg.append(h('h2', { text: title }), h('p', { text: message }),
      h('div', { class: 'dialog-actions' },
        h('button', { text: 'Cancel', onclick: () => dlg.close() }),
        h('button', { class: 'danger', text: confirmLabel, onclick: () => { result = true; dlg.close(); } })));
    dlg.addEventListener('close', () => { dlg.remove(); resolve(result); });
    document.body.append(dlg);
    dlg.showModal();
  });
}

/** Responsive table: columns = [{label, num?, render(row)->node|string}] */
export function table(columns, rows, emptyText = 'Nothing here yet.') {
  if (!rows.length) return h('div', { class: 'empty', text: emptyText });
  return h('table', {},
    h('thead', {}, h('tr', {}, columns.map((c) => h('th', { class: c.num ? 'num' : '', text: c.label })))),
    h('tbody', {}, rows.map((r) => h('tr', {}, columns.map((c) => {
      const content = c.render(r);
      return h('td', { class: (c.num ? 'num ' : '') + (c.actions ? 'actions' : ''), 'data-label': c.actions ? null : c.label },
        c.actions ? h('div', { class: 'row-actions' }, content) : content);
    })))));
}
