define([], function () {
  'use strict';
  const base = window.MONEYBAGS_API_BASE_URL || '';

  function errorMessage(payload, status) {
    if (typeof payload === 'string' && payload.trim()) return payload.trim();
    if (payload) {
      if (payload.message || payload.detail || payload.error) {
        return payload.message || payload.detail || payload.error;
      }
      if (payload.errors && typeof payload.errors === 'object') {
        const messages = Array.isArray(payload.errors)
          ? payload.errors.map((item) => item.defaultMessage || item.message || String(item))
          : Object.entries(payload.errors).map(([field, message]) => `${field}: ${message}`);
        if (messages.length) return messages.join(' ');
      }
      if (Array.isArray(payload.violations)) {
        return payload.violations
          .map((item) => `${item.field || item.propertyPath || 'Value'}: ${item.message}`)
          .join(' ');
      }
    }
    return status === 401
      ? 'Your session has expired. Please sign in again.'
      : `Request failed (${status}).`;
  }

  async function request(path, o) {
    const c = Object.assign({}, o || {});
    c.headers = new Headers(c.headers || {});
    const t = sessionStorage.getItem('moneybags.token');
    if (t) c.headers.set('Authorization', `Bearer ${t}`);
    if (c.body && !(c.body instanceof FormData)) c.headers.set('Content-Type', 'application/json');
    c.headers.set('Accept', 'application/json');
    let r;
    try {
      r = await fetch(base + path, c);
    } catch (_) {
      throw new Error('The banking gateway is unavailable. Start the platform services and retry.');
    }
    const type = r.headers.get('content-type') || '';
    const p =
      r.status === 204
        ? null
        : type.includes('json')
          ? await r.json().catch(() => null)
          : await r.text();
    if (!r.ok) {
      if (r.status === 401 && t) window.dispatchEvent(new CustomEvent('moneybags-session-expired'));
      const e = new Error(errorMessage(p, r.status));
      e.status = r.status;
      e.details = p;
      throw e;
    }
    return p;
  }
  return {
    get: (p) => request(p),
    post: (p, b) =>
      request(p, { method: 'POST', body: b instanceof FormData ? b : JSON.stringify(b) }),
    put: (p, b) => request(p, { method: 'PUT', body: JSON.stringify(b) }),
    putForm: (p, b) => request(p, { method: 'PUT', body: b }),
    patch: (p, b) =>
      request(p, { method: 'PATCH', body: b === undefined ? undefined : JSON.stringify(b) }),
    delete: (p) => request(p, { method: 'DELETE' }),
  };
});
