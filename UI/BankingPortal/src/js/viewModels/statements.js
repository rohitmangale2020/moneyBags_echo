define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojbutton',
], function (ko, app, u) {
  function VM() {
    const s = this,
      n = new Date();
    s.state = u.state([]);
    s.accountId = ko.observable('');
    s.monthly = ko.observable(false);
    s.year = ko.observable(n.getFullYear());
    s.month = ko.observable(n.getMonth() + 1);
    s.money = u.money;
    s.date = u.date;
    s.search = () =>
      s.state
        .run(() =>
          s.monthly()
            ? app.services.statements.monthly(s.accountId(), s.year(), s.month())
            : app.services.statements.account(s.accountId()),
        )
        .catch(() => null);
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
