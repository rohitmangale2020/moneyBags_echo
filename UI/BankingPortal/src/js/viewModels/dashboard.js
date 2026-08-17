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
    self.state = u.state({ users: [], usersTotal: 0, customerTotal: 0, customerTotals: {}, products: [] });
    self.cards = ko.pureComputed(() => {
      const d = self.state.data(),
        r = app.session.role();
      if (r === 'CUSTOMER')
        return [
          {
            label: 'Secure identity',
            value: 'Protected',
            tone: 'blue',
            iconClass: 'oj-ux-ico-shield',
          },
        ];
      return [
        {
          label: r === 'ADMIN' ? 'Platform users' : 'Customers',
          value: r === 'ADMIN' ? d.usersTotal : d.customerTotal,
          tone: 'blue',
          iconClass: r === 'ADMIN' ? 'oj-ux-ico-contact-group' : 'oj-ux-ico-contacts',
        },
        {
          label: 'KYC attention',
          value: d.customerTotals.KYC_PENDING || 0,
          tone: 'amber',
          iconClass: 'oj-ux-ico-shield',
        },
        {
          label: 'Active customers',
          value: d.customerTotals.ACTIVE || 0,
          tone: 'soft',
          iconClass: 'oj-ux-ico-contacts',
        },
        {
          label: 'Active products',
          value: d.products.filter((p) => p.status === 'ACTIVE').length,
          tone: 'indigo',
          iconClass: 'oj-ux-ico-bank',
        },
      ];
    });
    self.load = () =>
      self.state
        .run(async () => {
          if (app.session.role() === 'CUSTOMER') return { users: [], usersTotal: 0, customerTotal: 0, customerTotals: {}, products: [] };
          const values = await Promise.all([
            app.session.role() === 'ADMIN' ? app.services.users.list() : Promise.resolve([]),
            app.services.customers.list(0, 1),
            app.services.customers.list(0, 1, 'KYC_PENDING'),
            app.services.customers.list(0, 1, 'ACTIVE'),
            app.services.customers.list(0, 1, 'INACTIVE'),
            app.services.products.list(),
          ]);
          const users = u.list(values[0]);
          const usersTotal = values[0] && values[0].totalElements !== undefined
            ? Number(values[0].totalElements)
            : users.length;
          return {
            users,
            usersTotal,
            customerTotal: Number(values[1].totalElements === undefined ? u.list(values[1]).length : values[1].totalElements),
            customerTotals: {
              KYC_PENDING: Number(values[2].totalElements === undefined ? u.list(values[2]).length : values[2].totalElements),
              ACTIVE: Number(values[3].totalElements === undefined ? u.list(values[3]).length : values[3].totalElements),
              INACTIVE: Number(values[4].totalElements === undefined ? u.list(values[4]).length : values[4].totalElements),
            },
            products: values[5],
          };
        })
        .catch(() => null);
    self.load();
  }
  return VM;
});
