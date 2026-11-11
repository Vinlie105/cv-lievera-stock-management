// Thin fetch wrapper. Tokens live in httpOnly cookies the page cannot read; we only send the CSRF header.

export class ApiError extends Error {
  constructor(message, status, fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || null;
  }
}

let onSessionLost = () => {};
export function setSessionLostHandler(fn) { onSessionLost = fn; }

function csrfToken() {
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : '';
}

async function raw(method, path, body) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken();
  return fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    cache: 'no-store',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

async function parse(res) {
  let json = null;
  try { json = await res.json(); } catch { /* empty or non-JSON body */ }
  if (res.ok) return json ? json.data : null;
  const msg = (json && json.error) || (res.status === 403 ? 'You do not have permission to do that' : 'Something went wrong');
  throw new ApiError(msg, res.status, json && json.fieldErrors);
}

let refreshing = null;
function refreshSession() {
  // single-flight: many parallel 401s trigger exactly one rotation
  if (!refreshing) {
    refreshing = raw('POST', '/api/auth/refresh').then((r) => r.ok).catch(() => false)
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

const NO_REFRESH = ['/api/auth/login', '/api/auth/2fa/verify', '/api/auth/refresh', '/api/auth/logout'];

export async function api(method, path, body) {
  let res = await raw(method, path, body);
  if (res.status === 401 && !NO_REFRESH.includes(path)) {
    if (await refreshSession()) {
      res = await raw(method, path, body);
    }
    if (res.status === 401) {
      onSessionLost();
    }
  }
  return parse(res);
}

export const get = (p) => api('GET', p);
export const post = (p, b) => api('POST', p, b === undefined ? {} : b);
export const put = (p, b) => api('PUT', p, b === undefined ? {} : b);
