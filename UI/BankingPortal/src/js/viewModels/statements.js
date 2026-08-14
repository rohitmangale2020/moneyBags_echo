define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojbutton',
], function (ko, app, u) {
  const dateValue = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };
  const timestamp = (value) => {
    const parsed = Date.parse(value || '');
    return Number.isNaN(parsed) ? 0 : parsed;
  };
  const currentMonthRange = () => {
    const now = new Date();
    return { from: dateValue(new Date(now.getFullYear(), now.getMonth(), 1)), to: dateValue(now) };
  };

  function VM() {
    const s = this;
    const initialRange = currentMonthRange();
    s.state = u.state([]);
    s.accountNumber = ko.observable('');
    s.entryType = ko.observable('ALL');
    s.channel = ko.observable('ALL');
    s.fromDate = ko.observable(initialRange.from);
    s.toDate = ko.observable(initialRange.to);
    s.sortBy = ko.observable('posted-desc');
    s.money = u.money;
    s.date = u.date;
    s.activeCustomer = app.activeCustomer;
    s.hasActiveCustomer = app.hasActiveCustomer;
    s.activeAccountId = app.activeAccountId;
    s.customerAccounts = ko.observableArray([]);

    s.loadActiveCustomerAccounts = async () => {
      if (!s.hasActiveCustomer()) return;
      try {
        const accounts = (await app.services.accounts.customer(s.activeCustomer().customerId))
          .filter((account) => account.status === 'ACTIVE');
        s.customerAccounts(accounts);
        const preferredAccount = accounts.find(
          (account) => String(account.accountId) === String(s.activeAccountId()),
        ) || accounts[0];
        if (preferredAccount) s.accountNumber(String(preferredAccount.accountNumber));
      } catch (_) {
        s.customerAccounts([]);
      }
    };

    s.filteredStatements = ko.pureComputed(() => {
      const sorters = {
        'posted-desc': (a, b) => timestamp(b.postedAt) - timestamp(a.postedAt),
        'posted-asc': (a, b) => timestamp(a.postedAt) - timestamp(b.postedAt),
        'withdrawal-desc': (a, b) => Number(b.withdrawalAmount || 0) - Number(a.withdrawalAmount || 0),
        'deposit-desc': (a, b) => Number(b.depositAmount || 0) - Number(a.depositAmount || 0),
      };
      return s.state.data().slice().sort(sorters[s.sortBy()] || sorters['posted-desc']);
    });

    s.resultSummary = ko.pureComputed(() => {
      const shown = s.filteredStatements().length;
      const total = s.state.data().length;
      return shown === total ? `${total} entr${total === 1 ? 'y' : 'ies'}` : `${shown} of ${total} entries`;
    });

    s.search = () => {
      const accountNumber = String(s.accountNumber() || '').trim();
      if (!accountNumber) return Promise.resolve(s.state.error('Account number is required.'));
      if (!s.fromDate() || !s.toDate()) {
        return Promise.resolve(s.state.error('Select both from and to dates.'));
      }
      if (s.fromDate() > s.toDate()) {
        return Promise.resolve(s.state.error('From date cannot be after to date.'));
      }
      return s.state.run(async () => {
        const accounts = u.list(await app.services.accounts.number(accountNumber));
        if (!accounts.length) throw new Error('Account number was not found.');
        const accountId = String(accounts[0].accountId);
        if (app.setActiveAccount) app.setActiveAccount(accountId);
        return app.services.statements.search(accountId, {
          fromDate: s.fromDate(),
          toDate: s.toDate(),
          entryType: s.entryType(),
          channel: s.channel(),
        });
      }).catch(() => null);
    };

    s.currentMonth = () => {
      const range = currentMonthRange();
      s.fromDate(range.from);
      s.toDate(range.to);
    };

    s.clearFilters = () => {
      s.entryType('ALL');
      s.channel('ALL');
      s.sortBy('posted-desc');
      s.currentMonth();
    };

    s.displayMoney = (value, currencyCode) =>
      value === null || value === undefined ? '' : u.money(value, currencyCode);

    s.download = () => {
      const rows = [
          ['Posted', 'Description', 'Reference', 'Channel', 'Withdrawal', 'Deposit', 'Currency', 'Closing balance'],
          ...s.filteredStatements().map((x) => [
            x.postedAt,
            x.description,
            x.transactionRef,
            x.channel,
            x.withdrawalAmount,
            x.depositAmount,
            x.currencyCode,
            x.closingBalance,
          ]),
        ];
      const csv = rows
        .map((row) => row.map((value) => `"${String(value === null || value === undefined ? '' : value).replaceAll('"', '""')}"`).join(','))
        .join('\n');
      const link = document.createElement('a');
      link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
      link.download = `statement-${s.accountNumber()}-${s.fromDate()}-${s.toDate()}.csv`;
      link.click();
      URL.revokeObjectURL(link.href);
    };

    s.loadActiveCustomerAccounts();
  }
  return VM;
});
