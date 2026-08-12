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
    s.editingId = ko.observable(null);
    s.form = {
      transactionRef: ko.observable(u.ref()),
      debitAccountId: ko.observable(''),
      creditAccountId: ko.observable(''),
      amount: ko.observable(''),
      currencyCode: ko.observable('INR'),
      transactionType: ko.observable('TRANSFER'),
      transactionStatus: ko.observable(null),
      feeAmount: ko.observable(0),
      initiatedByCustomerId: ko.observable(null),
      initiatedByUserId: ko.observable(null),
      externalBeneficiary: ko.observable(null),
      completedAt: ko.observable(null),
      failureCode: ko.observable(null),
      failureReason: ko.observable(null),
    };
    s.money = u.money;
    s.date = u.date;
    s.search = () =>
      s.state.run(() => s.kind() === 'transactionId'
        ? app.services.transactions.get(s.query()).then((x) => [x])
        : app.services.transactions.find(s.kind(), s.query())).catch(() => null);
    s.open = () => {
      s.editingId(null);
      s.form.transactionRef(u.ref());
      s.result(null);
      s.error('');
      document.getElementById('transferDialog').open();
    };
    s.edit = (x) => {
      s.editingId(x.transactionId);
      Object.keys(s.form).forEach((key) => {
        if (key === 'transactionRef') s.form[key](x[key] || '');
        else if (Object.prototype.hasOwnProperty.call(x, key)) s.form[key](x[key]);
      });
      s.error(''); s.result(null);
      document.getElementById('transactionEditDialog').open();
    };
    s.update = async () => {
      const payload = {
        transactionRef: s.form.transactionRef(), transactionType: s.form.transactionType(),
        transactionStatus: s.form.transactionStatus(), debitAccountId: s.form.debitAccountId(),
        creditAccountId: s.form.creditAccountId(), externalBeneficiary: s.form.externalBeneficiary() || null,
        amount: Number(s.form.amount()), currencyCode: s.form.currencyCode(), feeAmount: Number(s.form.feeAmount() || 0),
        initiatedByCustomerId: s.form.initiatedByCustomerId() || null, initiatedByUserId: s.form.initiatedByUserId() || null,
        completedAt: s.form.completedAt() || null, failureCode: s.form.failureCode() || null,
        failureReason: s.form.failureReason() || null,
      };
      if (!payload.transactionStatus || !payload.transactionRef || payload.amount <= 0) return s.error('Reference, status, and a positive amount are required.');
      if (payload.transactionRef.length > 40 || !/^[A-Za-z]{3}$/.test(payload.currencyCode)) return s.error('Reference is limited to 40 characters and currency must contain three letters.');
      if (payload.feeAmount < 0) return s.error('Fee cannot be negative.');
      try { const value = await app.services.transactions.update(s.editingId(), payload); document.getElementById('transactionEditDialog').close(); s.state.data([value]); app.notify('Transaction record updated.'); }
      catch (e) { s.error(e.message); }
    };
    s.close = (id) => document.getElementById(id).close();
    s.transfer = async () => {
      if (!s.form.debitAccountId() || !s.form.creditAccountId() || Number(s.form.amount()) <= 0)
        return s.error('Complete all required transfer fields.');
      if (s.form.debitAccountId() === s.form.creditAccountId()) return s.error('Debit and credit accounts must be different.');
      if (!/^[A-Za-z]{3}$/.test(s.form.currencyCode())) return s.error('Currency must contain three letters.');
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
