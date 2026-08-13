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
    s.mode = ko.observable('customer');
    s.query = ko.observable('');
    s.products = ko.observableArray([]);
    s.editingId = ko.observable(null);
    s.error = ko.observable('');
    s.form = {
      accountNumber: ko.observable(''),
      customerId: ko.observable(''),
      productId: ko.observable(''),
      ownershipType: ko.observable('INDIVIDUAL'),
      availableBalance: ko.observable(0),
      status: ko.observable('ACTIVE'),
      currencyCode: ko.observable('INR'),
      closedAt: ko.observable(null),
    };
    s.money = u.money;
    s.date = u.date;
    s.search = () => {
      if (!s.query()) return;
      s.state
        .run(() =>
          s.mode() === 'customer'
            ? app.services.accounts.customer(s.query())
            : s.mode() === 'number'
              ? app.services.accounts.number(s.query())
              : app.services.accounts.get(s.query()).then((x) => [x]),
        )
        .catch(() => null);
    };
    s.open = async () => {
      s.editingId(null);
      s.error('');
      s.form.accountNumber('');
      s.form.customerId('');
      s.form.productId('');
      s.form.ownershipType('INDIVIDUAL');
      s.form.availableBalance(0);
      s.form.status('ACTIVE');
      s.form.currencyCode('INR');
      s.form.closedAt(null);
      try {
        s.products((await app.services.products.list()).filter((p) => p.status === 'ACTIVE'));
        document.getElementById('accountDialog').open();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.edit = async (x) => {
      s.editingId(x.accountId);
      s.error('');
      s.form.accountNumber(x.accountNumber);
      s.form.customerId(x.customerId);
      s.form.productId(x.productId);
      s.form.ownershipType(x.ownershipType);
      s.form.availableBalance(x.availableBalance);
      s.form.status(x.status);
      s.form.currencyCode(x.currencyCode);
      s.form.closedAt(x.closedAt);
      try {
        s.products(await app.services.products.list());
        document.getElementById('accountDialog').open();
      } catch (e) { app.notify(e.message, 'error'); }
    };
    s.create = async () => {
      const p = s.products().find((x) => String(x.productId) === String(s.form.productId()));
      if ((!p && !s.editingId()) || !s.form.customerId() || !s.form.accountNumber())
        return s.error('Complete all required fields.');
      if (s.form.accountNumber().length > 24) return s.error('Account number cannot exceed 24 characters.');
      if (Number(s.form.availableBalance()) < 0) return s.error('Available balance cannot be negative.');
      if (!/^[A-Za-z]{3}$/.test(s.editingId() ? s.form.currencyCode() : p.currency)) return s.error('Currency must be a three-letter code.');
      try {
        const payload = {
          accountNumber: s.form.accountNumber(),
          customerId: String(s.form.customerId()),
          productId: String(p ? p.productId : s.form.productId()),
          ownershipType: s.form.ownershipType(),
          status: s.editingId() ? s.form.status() : 'ACTIVE',
          currencyCode: s.editingId() ? s.form.currencyCode() : p.currency,
          availableBalance: Number(s.form.availableBalance()),
          closedAt: s.form.closedAt() || null,
        };
        const r = s.editingId()
          ? await app.services.accounts.update(s.editingId(), payload)
          : await app.services.accounts.create(payload);
        document.getElementById('accountDialog').close();
        s.state.data([r]);
        app.notify(s.editingId() ? 'Account updated.' : 'Account opened successfully.');
      } catch (e) {
        s.error(e.message);
      }
    };
    s.close = () => document.getElementById('accountDialog').close();
  }
  return VM;
});
