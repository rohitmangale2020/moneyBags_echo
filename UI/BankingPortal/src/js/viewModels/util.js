define(['knockout'], function (ko) {
  return {
    state(v) {
      return {
        data: ko.observable(v),
        loading: ko.observable(false),
        error: ko.observable(''),
        async run(fn) {
          this.loading(true);
          this.error('');
          try {
            const x = await fn();
            this.data(x);
            return x;
          } catch (e) {
            this.error(e.message);
            throw e;
          } finally {
            this.loading(false);
          }
        },
      };
    },
    list: (p) => (Array.isArray(p) ? p : (p && p.content) || []),
    money: (v, c) => {
      try {
        return new Intl.NumberFormat('en-IN', { style: 'currency', currency: c || 'INR' }).format(
          Number(v || 0),
        );
      } catch (_) {
        return `${c || ''} ${v}`;
      }
    },
    date: (v) =>
      v
        ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(
            new Date(v),
          )
        : '—',
    ref: () =>
      ('MB' + Date.now().toString(36) + crypto.randomUUID().replace(/-/g, ''))
        .toUpperCase()
        .slice(0, 40),
  };
});
