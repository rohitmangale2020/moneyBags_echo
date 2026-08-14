define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojdialog',
], function (ko, app, u) {
  const timestamp = (value) => {
    const parsed = Date.parse(value || '');
    return Number.isNaN(parsed) ? 0 : parsed;
  };

  function VM() {
    const s = this;
    s.state = u.state([]);
    s.query = ko.observable('');
    s.statusFilter = ko.observable('ALL');
    s.ownershipFilter = ko.observable('ALL');
    s.currencyFilter = ko.observable('ALL');
    s.sortBy = ko.observable('opened-desc');
    s.products = ko.observableArray([]);
    s.editingId = ko.observable(null);
    s.error = ko.observable('');
    s.activeCustomer = app.activeCustomer;
    s.hasActiveCustomer = app.hasActiveCustomer;
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
    s.currencies = ko.pureComputed(() =>
      Array.from(new Set(s.state.data().map((account) => account.currencyCode).filter(Boolean))).sort(),
    );
    s.filteredAccounts = ko.pureComputed(() => {
      const query = s.query().trim().toLowerCase();
      const accounts = s.state.data().filter((account) => {
        const inCustomerContext = !s.hasActiveCustomer()
          || String(account.customerId) === String(s.activeCustomer().customerId);
        const searchable = [
          account.accountNumber,
          account.customerName,
          account.productName,
        ].map((value) => String(value || '').toLowerCase()).join(' ');
        return inCustomerContext && (!query || searchable.includes(query))
          && (s.statusFilter() === 'ALL' || account.status === s.statusFilter())
          && (s.ownershipFilter() === 'ALL' || account.ownershipType === s.ownershipFilter())
          && (s.currencyFilter() === 'ALL' || account.currencyCode === s.currencyFilter());
      });
      const sorters = {
        'opened-desc': (a, b) => timestamp(b.openedAt) - timestamp(a.openedAt),
        'opened-asc': (a, b) => timestamp(a.openedAt) - timestamp(b.openedAt),
        'balance-desc': (a, b) => Number(b.availableBalance || 0) - Number(a.availableBalance || 0),
        'balance-asc': (a, b) => Number(a.availableBalance || 0) - Number(b.availableBalance || 0),
        'number-asc': (a, b) => String(a.accountNumber || '').localeCompare(String(b.accountNumber || '')),
        'number-desc': (a, b) => String(b.accountNumber || '').localeCompare(String(a.accountNumber || '')),
      };
      return accounts.slice().sort(sorters[s.sortBy()] || sorters['opened-desc']);
    });
    s.resultSummary = ko.pureComputed(() => {
      const shown = s.filteredAccounts().length;
      const total = s.state.data().length;
      return shown === total
        ? `${total} account${total === 1 ? '' : 's'}`
        : `${shown} of ${total} accounts`;
    });
    s.load = () => s.state.run(async () => {
      const accounts = u.list(await app.services.accounts.list());
      const [customersResult, productsResult] = await Promise.allSettled([
        app.services.customers.list(),
        app.services.products.list(),
      ]);
      const customers = customersResult.status === 'fulfilled' ? u.list(customersResult.value) : [];
      const products = productsResult.status === 'fulfilled' ? u.list(productsResult.value) : [];
      const customerNames = new Map(customers.map((customer) => [
        String(customer.customerId),
        [customer.firstName, customer.lastName].filter(Boolean).join(' '),
      ]));
      const unresolvedCustomerIds = [...new Set(accounts
        .map((account) => account.customerId)
        .filter((customerId) => customerId && !customerNames.has(String(customerId))))];
      const individualCustomers = await Promise.allSettled(
        unresolvedCustomerIds.map((customerId) => app.services.customers.get(customerId)),
      );
      individualCustomers.forEach((result, index) => {
        if (result.status !== 'fulfilled') return;
        const customer = result.value;
        const name = [customer.firstName, customer.lastName].filter(Boolean).join(' ');
        if (name) customerNames.set(String(unresolvedCustomerIds[index]), name);
      });
      const productNames = new Map(products.map((product) => [String(product.productId), product.productName]));
      return accounts.map((account) => Object.assign({}, account, {
        customerName: account.customerName || customerNames.get(String(account.customerId)) || '',
        productName: account.productName || productNames.get(String(account.productId)) || '',
      }));
    }).catch(() => null);
    s.clearFilters = () => {
      s.query('');
      s.statusFilter('ALL');
      s.ownershipFilter('ALL');
      s.currencyFilter('ALL');
      s.sortBy('opened-desc');
    };
    s.open = async () => {
      s.editingId(null);
      s.error('');
      s.form.accountNumber('');
      s.form.customerId(s.hasActiveCustomer() ? s.activeCustomer().customerId : '');
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
      if ((!p && !s.editingId()) || !s.form.customerId())
        return s.error('Complete all required fields.');
      if (Number(s.form.availableBalance()) < 0) return s.error('Available balance cannot be negative.');
      if (!/^[A-Za-z]{3}$/.test(s.editingId() ? s.form.currencyCode() : p.currency)) return s.error('Currency must be a three-letter code.');
      try {
        const payload = {
          accountNumber: s.editingId() ? s.form.accountNumber() : null,
          customerId: String(s.form.customerId()),
          productId: String(p ? p.productId : s.form.productId()),
          ownershipType: s.form.ownershipType(),
          status: s.editingId() ? s.form.status() : 'ACTIVE',
          currencyCode: s.editingId() ? s.form.currencyCode() : p.currency,
          availableBalance: Number(s.form.availableBalance()),
          closedAt: s.form.closedAt() || null,
        };
        const savedAccount = s.editingId()
          ? await app.services.accounts.update(s.editingId(), payload)
          : await app.services.accounts.create(payload);
        if (app.setTransactionCustomerId) app.setTransactionCustomerId(payload.customerId);
        if (savedAccount && savedAccount.accountId && app.setActiveAccount) {
          app.setActiveAccount(savedAccount.accountId);
        }
        document.getElementById('accountDialog').close();
        app.notify(s.editingId() ? 'Account updated.' : 'Account opened successfully.');
        await s.load();
      } catch (e) {
        s.error(e.message);
      }
    };
    s.close = () => document.getElementById('accountDialog').close();
    s.load();
  }
  return VM;
});
