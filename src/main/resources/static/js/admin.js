import { get, post, put } from './api.js';
import { h, toast, dt, pill, field, input, select, formDialog, confirmDialog, table, alertBox, describe } from './ui.js';

const header = (title, sub, ...actions) =>
  h('div', { class: 'page-head' }, h('div', {}, h('h1', { text: title }), sub ? h('p', { class: 'muted', text: sub }) : null), h('div', { class: 'row-actions' }, actions));

// ---------------------------------------------------------------------- users
export async function users(ctx) {
  const page = await get('/api/users?size=100&sort=username,asc');
  const act = (label, fn, cls = '') => h('button', { class: 'small ' + cls, text: label, onclick: fn });
  const run = async (promise, msg) => {
    try { await promise; toast(msg); ctx.reload(); } catch (e) { toast(describe(e), true); }
  };
  const resetPw = (u) => formDialog({
    title: 'Set temporary password for ' + u.username,
    fields: [field('Temporary password', input('newPassword', { type: 'password', required: true, autocomplete: 'new-password', maxlength: 128 }),
      'They will be forced to choose their own at next sign-in. All their sessions are ended.')],
    onSubmit: async (v) => { await post(`/api/users/${u.id}/reset-password`, { newPassword: v.newPassword }); toast('Password reset'); ctx.reload(); },
  });

  const status = (u) => {
    if (!u.active) return pill('Deactivated', 'off');
    if (u.lockedUntil && new Date(u.lockedUntil) > new Date()) return pill('Locked', 'bad');
    return pill('Active');
  };

  return h('div', {},
    header('Users', 'Accounts and access. Admin only.', h('button', { class: 'primary', text: 'Add user', onclick: () => userForm(ctx) })),
    h('div', { class: 'card flush' }, table([
      { label: 'User', render: (u) => h('div', {}, h('strong', { text: u.fullName }), h('div', { class: 'muted mono', text: u.username })) },
      { label: 'Role', render: (u) => pill(u.role, u.role === 'ADMIN' ? 'warn' : 'off') },
      { label: '2FA', render: (u) => (u.totpEnabled ? pill('On') : pill('Off', 'off')) },
      { label: 'Status', render: (u) => status(u) },
      { label: 'Last sign-in', render: (u) => dt(u.lastLoginAt) },
      { label: '', actions: true, render: (u) => {
        if (u.id === ctx.user.id) return h('span', { class: 'muted', text: 'You' });
        return [
          u.lockedUntil && new Date(u.lockedUntil) > new Date() ? act('Unlock', () => run(put(`/api/users/${u.id}/unlock`), 'Unlocked')) : null,
          act('Reset password', () => resetPw(u)),
          u.totpEnabled ? act('Reset 2FA', async () => {
            if (await confirmDialog('Reset 2FA?', `${u.fullName} will have to enrol an authenticator again.`, 'Reset')) {
              run(post(`/api/users/${u.id}/reset-2fa`), '2FA reset');
            }
          }) : null,
          u.active ? act('Deactivate', () => run(put(`/api/users/${u.id}/deactivate`), 'Deactivated'), 'danger')
            : act('Activate', () => run(put(`/api/users/${u.id}/activate`), 'Activated')),
        ];
      } },
    ], page.content)));
}

function userForm(ctx) {
  formDialog({
    title: 'New user',
    fields: [
      field('Full name', input('fullName', { required: true, maxlength: 150 })),
      h('div', { class: 'grid2' },
        field('Username', input('username', { required: true, maxlength: 100, autocomplete: 'off' }), 'Letters, digits, . _ -'),
        field('Role', select('role', [['STAFF', 'Staff - operations only'], ['ADMIN', 'Admin - full control']], 'STAFF'))),
      field('Email (optional)', input('email', { type: 'email', maxlength: 150 })),
      field('Temporary password', input('password', { type: 'password', required: true, autocomplete: 'new-password', maxlength: 128 }),
        '12+ characters, 3 of 4 character types. The user must change it at first sign-in.'),
    ],
    onSubmit: async (v) => {
      await post('/api/users', { fullName: v.fullName.trim(), username: v.username.trim(), role: v.role, email: v.email.trim() || null, password: v.password });
      toast('User created');
      ctx.reload();
    },
  });
}

// ---------------------------------------------------------------------- audit
export async function audit() {
  const [page, chain] = await Promise.all([get('/api/audit-logs?size=100&sort=id,desc'), get('/api/audit-logs/verify')]);
  return h('div', {},
    header('Audit log', 'Every action, with who, when and from where.'),
    chain.intact
      ? alertBox('Integrity check passed: no entries have been altered or removed.', 'info')
      : alertBox(`Integrity check FAILED at entry #${chain.brokenAtId}. The log has been tampered with.`, 'error'),
    h('div', { class: 'card flush' }, table([
      { label: 'When', render: (l) => dt(l.createdAt) },
      { label: 'User', render: (l) => l.username || '-' },
      { label: 'Action', render: (l) => h('span', { class: 'mono', text: l.action }) },
      { label: 'Detail', render: (l) => [l.entityType && `${l.entityType}${l.entityId ? ' #' + l.entityId : ''}`, l.changeSummary].filter(Boolean).join(' - ') },
      { label: 'IP', render: (l) => h('span', { class: 'muted mono', text: l.ipAddress || '' }) },
    ], page.content, 'No activity recorded.')));
}
