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
    s.error = ko.observable('');
    s.form = {
      accountNumber: ko.observable(''),
      customerId: ko.observable(''),
      productId: ko.observable(''),
      ownershipType: ko.observable('INDIVIDUAL'),
      availableBalance: ko.observable(0),
    };
    s.money = u.money;
    s.date = u.date;
    s.search = () => {
      if (!s.query()) return;
      s.state
        .run(() =>
          s.mode() === 'customer'
            ? app.services.accounts.customer(s.query())
            : app.services.accounts.number(s.query()),
        )
        .catch(() => null);
    };
    s.open = async () => {
      s.error('');
      try {
        s.products((await app.services.products.list()).filter((p) => p.status === 'ACTIVE'));
        document.getElementById('accountDialog').open();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.create = async () => {
      const p = s.products().find((x) => String(x.productId) === String(s.form.productId()));
      if (!p || !s.form.customerId() || !s.form.accountNumber())
        return s.error('Complete all required fields.');
      try {
        const r = await app.services.accounts.create({
          accountNumber: s.form.accountNumber(),
          customerId: String(s.form.customerId()),
          productId: String(p.productId),
          ownershipType: s.form.ownershipType(),
          status: 'ACTIVE',
          currencyCode: p.currency,
          availableBalance: Number(s.form.availableBalance()),
          closedAt: null,
        });
        document.getElementById('accountDialog').close();
        s.state.data([r]);
        app.notify('Account opened successfully.');
      } catch (e) {
        s.error(e.message);
      }
    };
  }
  return VM;
});
