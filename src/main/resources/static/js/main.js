import { get, post, setSessionLostHandler } from './api.js';
import { h, mount, toast, describe, alertBox } from './ui.js';
import { loginFlow, completeRequiredActions } from './auth.js';
import { dashboard, products, stock, suppliers, categories } from './views.js';
import { users, audit } from './admin.js';

const IDLE_MS = 30 * 60 * 1000; // matches the server-side idle timeout (UC-03)

const ROUTES = {
  '/': { label: 'Today', icon: '◧', view: dashboard },
  '/products': { label: 'Products', icon: '▤', view: products },
  '/stock': { label: 'Stock', icon: '⇅', view: stock },
  '/suppliers': { label: 'Suppliers', icon: '☖', view: suppliers },
  '/categories': { label: 'Categories', icon: '#', view: categories, hidden: true },
  '/users': { label: 'Users', icon: '☺', view: users, admin: true },
  '/audit': { label: 'Audit', icon: '☰', view: audit, admin: true },
};

let user = null;
let idleTimer = null;
let navToken = 0;

const currentPath = () => (location.hash.slice(1) || '/');

async function signOut(message) {
  clearTimeout(idleTimer);
  try { await post('/api/auth/logout'); } catch { /* cookies may already be gone */ }
  user = null;
  start(message);
}

function armIdleTimer() {
  clearTimeout(idleTimer);
  idleTimer = setTimeout(() => signOut('You were signed out after 30 minutes of inactivity.'), IDLE_MS);
}
['pointerdown', 'keydown', 'touchstart'].forEach((ev) =>
  window.addEventListener(ev, () => { if (user) armIdleTimer(); }, { passive: true }));

async function render() {
  if (!user) return;
  const token = ++navToken;
  const path = ROUTES[currentPath()] ? currentPath() : '/';
  const route = ROUTES[path];
  const main = document.getElementById('view');
  if (!main) return;

  document.querySelectorAll('.tabs a').forEach((a) => {
    if (a.dataset.path === path) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
  });
  if (route.admin && user.role !== 'ADMIN') {
    main.replaceChildren(alertBox('Administrators only.'));
    return;
  }
  main.replaceChildren(h('p', { class: 'muted', text: 'Loading...' }));
  try {
    const node = await route.view({ user, reload: render });
    if (token === navToken) main.replaceChildren(node);
  } catch (e) {
    if (token === navToken) main.replaceChildren(alertBox(describe(e)));
  }
  if (path === '/products' && token === navToken) addCategoriesLink(main);
}

function addCategoriesLink(main) {
  const actions = main.querySelector('.page-head .row-actions');
  if (actions && !actions.querySelector('[data-cat]')) {
    actions.prepend(h('button', { 'data-cat': '1', text: 'Categories', onclick: () => { location.hash = '#/categories'; } }));
  }
}

function shell() {
  const tabs = Object.entries(ROUTES)
    .filter(([, r]) => !r.hidden && (!r.admin || user.role === 'ADMIN'))
    .map(([path, r]) => h('a', { href: '#' + path, 'data-path': path },
      h('span', { class: 'ico', 'aria-hidden': 'true', text: r.icon }), r.label));
  mount(h('div', { class: 'shell' },
    h('header', { class: 'topbar' },
      h('div', { class: 'brand', text: 'CV Lievera Stock' }),
      h('div', { class: 'who' }, h('span', {}, h('b', { text: user.fullName }), ' · ', user.role === 'ADMIN' ? 'Admin' : 'Staff'),
        h('button', { class: 'small', text: 'Sign out', onclick: () => signOut() }))),
    h('nav', { class: 'tabs', 'aria-label': 'Main' }, tabs),
    h('main', { id: 'view', tabindex: '-1' })));
}

async function start(message) {
  try {
    let session = null;
    try {
      // an existing cookie session (page reload) resumes without asking for credentials again
      if (!message) session = await get('/api/auth/me');
    } catch { /* not signed in */ }
    if (!session) session = await loginFlow(message);
    session = await completeRequiredActions(session);
    user = session.user;
    armIdleTimer();
    shell();
    render();
  } catch (e) {
    toast(describe(e), true);
    start(describe(e));
  }
}

setSessionLostHandler(() => { if (user) signOut('Your session ended. Please sign in again.'); });
window.addEventListener('hashchange', render);

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js').catch(() => {}));
}

start();
