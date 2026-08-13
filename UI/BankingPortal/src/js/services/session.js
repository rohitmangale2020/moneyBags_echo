define(['knockout'], function (ko) {
  'use strict';
  function decode(t) {
    try {
      return JSON.parse(
        decodeURIComponent(
          atob(t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))
            .split('')
            .map((c) => `%${('00' + c.charCodeAt(0).toString(16)).slice(-2)}`)
            .join(''),
        ),
      );
    } catch (_) {
      return null;
    }
  }
  const stored = sessionStorage.getItem('moneybags.token'),
    initial = stored ? decode(stored) : null,
    valid = initial && initial.exp * 1000 > Date.now() ? initial : null;
  if (!valid) sessionStorage.removeItem('moneybags.token');
  const token = ko.observable(valid ? stored : ''),
    claims = ko.observable(valid),
    profile = ko.observable(null);
  return {
    token,
    claims,
    profile,
    isAuthenticated: ko.pureComputed(() => !!token()),
    role: ko.pureComputed(() => (claims() && claims().roles ? claims().roles[0] : null)),
    username: ko.pureComputed(() => (claims() ? claims().sub : '')),
    userId: ko.pureComputed(() => (claims() ? claims().userId : null)),
    establish(v) {
      const p = decode(v);
      if (!p || !p.roles || !p.userId)
        throw new Error('Authentication token is missing identity claims.');
      sessionStorage.setItem('moneybags.token', v);
      claims(p);
      token(v);
    },
    clear() {
      sessionStorage.removeItem('moneybags.token');
      token('');
      claims(null);
      profile(null);
    },
  };
});
