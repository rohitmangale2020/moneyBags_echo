define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojdialog',
], function (ko, app, u) {
  function VM() {
    const s = this;
    s.state = u.state([]);
    s.kind = ko.observable('debitAccountId');
    s.query = ko.observable('');
    s.error = ko.observable('');
    s.result = ko.observable(null);
    s.form = {
      transactionRef: ko.observable(u.ref()),
      debitAccountId: ko.observable(''),
      creditAccountId: ko.observable(''),
      amount: ko.observable(''),
      currencyCode: ko.observable('INR'),
    };
    s.money = u.money;
    s.date = u.date;
    s.search = () =>
      s.state.run(() => app.services.transactions.find(s.kind(), s.query())).catch(() => null);
    s.open = () => {
      s.form.transactionRef(u.ref());
      s.result(null);
      s.error('');
      document.getElementById('transferDialog').open();
    };
    s.transfer = async () => {
      if (!s.form.debitAccountId() || !s.form.creditAccountId() || Number(s.form.amount()) <= 0)
        return s.error('Complete all required transfer fields.');
      try {
        const r = await app.services.transactions.transfer({
          transactionRef: s.form.transactionRef(),
          transactionType: 'TRANSFER',
          transactionStatus: null,
          debitAccountId: s.form.debitAccountId(),
          creditAccountId: s.form.creditAccountId(),
          externalBeneficiary: null,
          amount: Number(s.form.amount()),
          currencyCode: s.form.currencyCode(),
          feeAmount: 0,
          initiatedByCustomerId: null,
          initiatedByUserId: null,
          completedAt: null,
          failureCode: null,
          failureReason: null,
        });
        s.result(r);
        s.state.data([r].concat(s.state.data()));
        app.notify(
          `Transfer ${r.transactionStatus.toLowerCase()}.`,
          r.transactionStatus === 'COMPLETED' ? 'success' : 'warning',
        );
      } catch (e) {
        s.error(e.message);
      }
    };
  }
  return VM;
});
