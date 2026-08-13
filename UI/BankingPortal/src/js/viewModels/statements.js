define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojdialog',
], function (ko, app, u) {
  function VM() {
    const s = this,
      n = new Date();
    s.state = u.state([]);
    s.accountId = ko.observable('');
    s.monthly = ko.observable(false);
    s.year = ko.observable(n.getFullYear());
    s.month = ko.observable(n.getMonth() + 1);
    s.statementId = ko.observable('');
    s.error = ko.observable('');
    s.form = {
      transactionId: ko.observable(''), accountId: ko.observable(''), entryType: ko.observable('DEBIT'),
      amount: ko.observable(''), currencyCode: ko.observable('INR'), balanceAfter: ko.observable(''),
    };
    s.money = u.money;
    s.date = u.date;
    s.search = () =>
      !s.accountId() ? Promise.resolve(s.state.error('Account ID is required.')) : s.monthly() && (Number(s.month()) < 1 || Number(s.month()) > 12)
        ? Promise.resolve(s.state.error('Month must be between 1 and 12.')) : s.state
        .run(() =>
          s.monthly()
            ? app.services.statements.monthly(s.accountId(), s.year(), s.month())
            : app.services.statements.account(s.accountId()),
        )
        .catch(() => null);
    s.getById = () => {
      if (!s.statementId()) return;
      s.state.run(() => app.services.statements.get(s.statementId()).then((x) => [x])).catch(() => null);
    };
    s.openRecord = () => { s.error(''); document.getElementById('statementDialog').open(); };
    s.record = async () => {
      const d = { transactionId: s.form.transactionId(), accountId: s.form.accountId(), entryType: s.form.entryType(), amount: Number(s.form.amount()), currencyCode: s.form.currencyCode(), balanceAfter: Number(s.form.balanceAfter()) };
      if (!d.transactionId || !d.accountId || d.amount <= 0 || !/^[A-Za-z]{3}$/.test(d.currencyCode)) return s.error('Complete the required fields with a positive amount and three-letter currency.');
      try { const value = await app.services.statements.record(d); document.getElementById('statementDialog').close(); s.state.data([value].concat(s.state.data())); app.notify('Statement entry recorded.'); }
      catch (e) { s.error(e.message); }
    };
    s.close = () => document.getElementById('statementDialog').close();
    s.download = () => {
      const rows = [
          ['Posted', 'Type', 'Amount', 'Currency', 'Balance after', 'Transaction ID'],
          ...s.state
            .data()
            .map((x) => [
              x.postedAt,
              x.entryType,
              x.amount,
              x.currencyCode,
              x.balanceAfter,
              x.transactionId,
            ]),
        ],
        csv = rows
          .map((r) => r.map((v) => `"${String(v || '').replaceAll('"', '""')}"`).join(','))
          .join('\n'),
        a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
      a.download = `statement-${s.accountId()}.csv`;
      a.click();
      URL.revokeObjectURL(a.href);
    };
  }
  return VM;
});
