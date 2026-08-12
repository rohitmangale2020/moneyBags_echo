define(['knockout', 'appController', 'viewModels/util'], function (ko, app, u) {
  function VM() {
    const self = this;
    self.displayName = app.displayName;
    self.isEmployee = ko.pureComputed(() => app.session.role() === 'EMPLOYEE');
    self.isCustomer = ko.pureComputed(() => app.session.role() === 'CUSTOMER');
    self.openOnboarding = () => app.go('onboarding');
    self.openCustomers = () => app.go('customers');
    self.openAccounts = () => app.go('accounts');
    self.openTransactions = () => app.go('transactions');
    self.state = u.state({ users: [], customers: [], products: [] });
    self.cards = ko.pureComputed(() => {
      const d = self.state.data(),
        r = app.session.role();
      if (r === 'CUSTOMER') return [{ label: 'Secure identity', value: 'Protected', tone: 'blue' }];
      return [
        {
          label: r === 'ADMIN' ? 'Platform users' : 'Customers',
          value: r === 'ADMIN' ? d.users.length : d.customers.length,
          tone: 'blue',
        },
        {
          label: 'KYC attention',
          value: d.customers.filter((c) => ['NEW', 'KYC_PENDING'].includes(c.status)).length,
          tone: 'amber',
        },
        {
          label: 'Active customers',
          value: d.customers.filter((c) => c.status === 'ACTIVE').length,
          tone: 'soft',
        },
        {
          label: 'Active products',
          value: d.products.filter((p) => p.status === 'ACTIVE').length,
          tone: 'indigo',
        },
      ];
    });
    self.load = () =>
      self.state
        .run(async () => {
          if (app.session.role() === 'CUSTOMER') return { users: [], customers: [], products: [] };
          const values = await Promise.all([
            app.session.role() === 'ADMIN' ? app.services.users.list() : Promise.resolve([]),
            app.services.customers.list(),
            app.services.products.list(),
          ]);
          return { users: u.list(values[0]), customers: values[1], products: values[2] };
        })
        .catch(() => null);
    self.load();
  }
  return VM;
});
