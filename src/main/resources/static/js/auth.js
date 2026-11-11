import { get, post } from './api.js';
import { h, mount, field, input, alertBox, describe } from './ui.js';

function authCard(title, subtitle, ...body) {
  mount(h('div', { class: 'auth' }, h('div', { class: 'auth-card' },
    h('div', { class: 'brand', text: 'CV Lievera Stock' }),
    h('h1', { text: title }),
    subtitle ? h('p', { class: 'muted', text: subtitle }) : null,
    ...body)));
}

/** Runs one auth form. `build` returns [fields...]; `submit(values)` returns the next session or throws. */
function authForm({ title, subtitle, fields, button, submit, notice, footer }) {
  return new Promise((resolve) => {
    const errBox = h('div');
    const btn = h('button', { class: 'primary', type: 'submit', text: button });
    const form = h('form', { novalidate: true }, errBox, ...fields, btn);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!form.checkValidity()) { form.reportValidity(); return; }
      btn.disabled = true;
      errBox.replaceChildren();
      try {
        const values = {};
        for (const el of form.elements) if (el.name) values[el.name] = el.value;
        resolve(await submit(values));
      } catch (err) {
        errBox.replaceChildren(alertBox(describe(err)));
        btn.disabled = false;
        const first = form.querySelector('input');
        if (first) { first.value = ''; first.focus(); }
      }
    });
    authCard(title, subtitle, notice || null, form, footer || null);
    const first = form.querySelector('input');
    if (first) first.focus();
  });
}

/** Returns the authenticated session (possibly still restricted). */
export async function loginFlow(message) {
  const result = await authForm({
    title: 'Sign in',
    subtitle: 'Stock, suppliers and purchasing for CV Lievera.',
    notice: message ? alertBox(message, 'info') : null,
    fields: [
      field('Username', input('username', { required: true, autocomplete: 'username', maxlength: 100, autofocus: true })),
      field('Password', input('password', { type: 'password', required: true, autocomplete: 'current-password', maxlength: 128 })),
    ],
    button: 'Sign in',
    submit: (v) => post('/api/auth/login', { username: v.username.trim(), password: v.password }),
  });
  return result.status === 'MFA_REQUIRED' ? mfaFlow(result.mfaToken) : result;
}

async function mfaFlow(mfaToken) {
  return authForm({
    title: 'Two-step check',
    subtitle: 'Enter the 6-digit code from your authenticator app, or one of your recovery codes.',
    fields: [field('Code', input('code', { required: true, inputmode: 'text', maxlength: 20, autocomplete: 'one-time-code', autofocus: true }))],
    button: 'Verify',
    submit: (v) => post('/api/auth/2fa/verify', { mfaToken, code: v.code }),
  });
}

async function passwordFlow() {
  return authForm({
    title: 'Choose a new password',
    subtitle: 'You must replace your temporary password before continuing.',
    notice: h('p', { class: 'hint', text: 'At least 12 characters, mixing 3 of: upper case, lower case, digits, symbols. Not your username.' }),
    fields: [
      field('Current (temporary) password', input('currentPassword', { type: 'password', required: true, autocomplete: 'current-password', maxlength: 128 })),
      field('New password', input('newPassword', { type: 'password', required: true, autocomplete: 'new-password', maxlength: 128 })),
    ],
    button: 'Change password',
    submit: (v) => post('/api/auth/password', v),
  });
}

async function enrollFlow() {
  const setup = await post('/api/auth/2fa/setup');
  const spaced = setup.secret.match(/.{1,4}/g).join(' ');
  const enabled = await authForm({
    title: 'Set up two-step sign-in',
    subtitle: 'Administrators must use an authenticator app (Google Authenticator, Authy, 1Password...).',
    notice: h('div', { class: 'field' },
      h('p', { text: 'Add a new time-based account in your app and enter this key:' }),
      h('div', { class: 'secret', text: spaced }),
      h('p', { class: 'hint' }, 'On a phone you can also ', h('a', { href: setup.otpauthUri, text: 'open it in your authenticator' }), '.')),
    fields: [field('6-digit code from the app', input('code', { required: true, inputmode: 'numeric', maxlength: 6, autocomplete: 'one-time-code' }))],
    button: 'Turn on',
    submit: (v) => post('/api/auth/2fa/enable', { code: v.code.trim() }),
  });
  await showRecoveryCodes(enabled.recoveryCodes);
  return enabled.session;
}

function showRecoveryCodes(codes) {
  return new Promise((resolve) => {
    authCard('Save your recovery codes', 'Each code works once if you lose your phone. They are shown only now.',
      h('div', { class: 'codes' }, codes.map((c) => h('span', { text: c }))),
      h('button', { class: 'primary', text: 'I have saved them', onclick: () => resolve() }));
  });
}

/** Completes any forced steps (password change, 2FA enrolment) and returns the full session. */
export async function completeRequiredActions(session) {
  let s = session;
  while (s.requiredActions && s.requiredActions.length) {
    s = s.requiredActions[0] === 'CHANGE_PASSWORD' ? await passwordFlow() : await enrollFlow();
    if (!s.requiredActions) s = await get('/api/auth/me');
  }
  return s;
}
