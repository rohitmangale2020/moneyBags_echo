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
    s.pageSize = 10;
    s.currentPage = ko.observable(0);
    s.totalTransactions = ko.observable(0);
    s.totalPages = ko.observable(0);
    s.query = ko.observable('');
    s.typeFilter = ko.observable('ALL');
    s.statusFilter = ko.observable('ALL');
    s.currencyFilter = ko.observable('ALL');
    s.dateFrom = ko.observable('');
    s.dateTo = ko.observable('');
    s.sortBy = ko.observable('initiated-desc');
    s.error = ko.observable('');
    s.operation = ko.observable('TRANSFER');
    s.busy = ko.observable(false);
    s.loadingAccounts = ko.observable(false);
    s.accounts = ko.observableArray([]);
    s.activeCustomer = app.activeCustomer;
    s.hasActiveCustomer = app.hasActiveCustomer;
    s.useCustomerAccounts = ko.observable(false);

    s.form = {
      transactionRef: ko.observable(u.ref()),
      debitAccountId: ko.observable(''),
      creditAccountId: ko.observable(''),
      accountId: ko.observable(''),
      customerId: ko.observable(''),
      fromAccountId: ko.observable(''),
      toAccountId: ko.observable(''),
      amount: ko.observable(''),
      currencyCode: ko.observable('INR'),
    };

    s.money = u.money;
    s.date = u.date;
    s.isInternalTransfer = ko.pureComputed(() => s.operation() === 'TRANSFER');
    s.isSelfTransfer = ko.pureComputed(() => s.operation() === 'SELF_TRANSFER');
    s.isSingleAccount = ko.pureComputed(
      () => s.operation() === 'DEPOSIT' || s.operation() === 'WITHDRAWAL',
    );
    s.hasCustomerAccounts = ko.pureComputed(() => s.accounts().length > 0);
    s.isUsingCustomerAccounts = ko.pureComputed(
      () => s.useCustomerAccounts() && s.accounts().length > 0,
    );
    s.operationTitle = ko.pureComputed(() => ({
      TRANSFER: 'Internal transfer',
      DEPOSIT: 'Deposit',
      WITHDRAWAL: 'Withdrawal',
      SELF_TRANSFER: 'Self transfer',
    })[s.operation()]);

    s.accountLabel = (account) =>
      `${account.accountNumber} · ${account.currencyCode} · ${u.money(account.availableBalance, account.currencyCode)}`;

    s.currencies = ko.pureComputed(() =>
      Array.from(new Set(s.state.data().map((transaction) => transaction.currencyCode).filter(Boolean))).sort(),
    );
    s.filteredTransactions = ko.pureComputed(() => {
      const query = s.query().trim().toLowerCase();
      const from = s.dateFrom() ? new Date(`${s.dateFrom()}T00:00:00`).getTime() : null;
      const to = s.dateTo() ? new Date(`${s.dateTo()}T23:59:59.999`).getTime() : null;
      const transactions = s.state.data().filter((transaction) => {
        const searchable = [
          transaction.transactionRef,
          transaction.transactionId,
          transaction.debitAccountId,
          transaction.creditAccountId,
          transaction.initiatedByCustomerId,
          transaction.externalBeneficiary,
          transaction.description,
        ].map((value) => String(value || '').toLowerCase()).join(' ');
        const initiated = timestamp(transaction.initiatedAt);
        return (!query || searchable.includes(query))
          && (s.typeFilter() === 'ALL' || transaction.transactionType === s.typeFilter())
          && (s.statusFilter() === 'ALL' || transaction.transactionStatus === s.statusFilter())
          && (s.currencyFilter() === 'ALL' || transaction.currencyCode === s.currencyFilter())
          && (from === null || initiated >= from)
          && (to === null || initiated <= to);
      });
      const sorters = {
        'initiated-desc': (a, b) => timestamp(b.initiatedAt) - timestamp(a.initiatedAt),
        'initiated-asc': (a, b) => timestamp(a.initiatedAt) - timestamp(b.initiatedAt),
        'amount-desc': (a, b) => Number(b.amount || 0) - Number(a.amount || 0),
        'amount-asc': (a, b) => Number(a.amount || 0) - Number(b.amount || 0),
        'reference-asc': (a, b) => String(a.transactionRef || '').localeCompare(String(b.transactionRef || '')),
        'reference-desc': (a, b) => String(b.transactionRef || '').localeCompare(String(a.transactionRef || '')),
      };
      return transactions.slice().sort(sorters[s.sortBy()] || sorters['initiated-desc']);
    });
    s.resultSummary = ko.pureComputed(() => {
      const shown = s.filteredTransactions().length;
      const total = s.totalTransactions();
      return shown === total
        ? `${total} transaction${total === 1 ? '' : 's'}`
        : `${shown} of ${total} transactions`;
    });
    s.statusClass = (status) => String(status || '').toLowerCase();
    s.load = (requestedPage) => s.state.run(async () => {
      const page = Number.isInteger(requestedPage) ? requestedPage : s.currentPage();
      const response = await app.services.transactions.list(page, s.pageSize);
      s.currentPage(Number(response.number || 0));
      s.totalTransactions(Number(response.totalElements === undefined ? u.list(response).length : response.totalElements));
      s.totalPages(Number(response.totalPages === undefined ? 1 : response.totalPages));
      return u.list(response);
    }).catch(() => null);
    s.previousPage = () => {
      if (s.currentPage() > 0) s.load(s.currentPage() - 1);
    };
    s.nextPage = () => {
      if (s.currentPage() < s.totalPages() - 1) s.load(s.currentPage() + 1);
    };
    s.clearFilters = () => {
      s.query('');
      s.typeFilter('ALL');
      s.statusFilter('ALL');
      s.currencyFilter('ALL');
      s.dateFrom('');
      s.dateTo('');
      s.sortBy('initiated-desc');
      s.load(0);
    };

    s.selectOperation = (operation) => {
      s.operation(operation);
      s.error('');
      if (s.hasActiveCustomer()) s.loadCustomerAccounts();
    };

    s.useDifferentAccount = () => {
      s.useCustomerAccounts(false);
      s.error('');
    };

    s.useActiveCustomerAccounts = () => {
      s.useCustomerAccounts(true);
      if (s.hasActiveCustomer()) s.loadCustomerAccounts();
    };

    s.open = () => {
      s.form.transactionRef(u.ref());
      s.form.debitAccountId('');
      s.form.creditAccountId('');
      s.form.accountId('');
      s.form.customerId(s.hasActiveCustomer()
        ? s.activeCustomer().customerId
        : (app.activeTransactionCustomerId ? app.activeTransactionCustomerId() : ''));
      s.form.fromAccountId('');
      s.form.toAccountId('');
      s.form.amount('');
      s.form.currencyCode('INR');
      s.accounts([]);
      s.useCustomerAccounts(s.hasActiveCustomer());
      s.operation('TRANSFER');
      s.error('');
      document.getElementById('transactionDialog').open();
      if (s.hasActiveCustomer()) s.loadCustomerAccounts();
    };

    s.close = () => document.getElementById('transactionDialog').close();

    s.loadCustomerAccounts = async () => {
      const customerId = s.form.customerId().trim();
      if (!customerId) return s.error('Enter a customer ID first.');
      if (app.setTransactionCustomerId) app.setTransactionCustomerId(customerId);
      s.loadingAccounts(true);
      s.error('');
      try {
        const accounts = (await app.services.accounts.customer(customerId))
          .filter((account) => account.status === 'ACTIVE');
        s.accounts(accounts);
        s.useCustomerAccounts(accounts.length > 0);
        const firstAccountId = accounts[0] ? String(accounts[0].accountId) : '';
        const secondAccountId = accounts[1] ? String(accounts[1].accountId) : '';
        s.form.fromAccountId(firstAccountId);
        s.form.toAccountId(secondAccountId);
        s.form.debitAccountId(firstAccountId);
        s.form.accountId(firstAccountId);
        if (accounts[0]) s.form.currencyCode(accounts[0].currencyCode);
        if (s.isSelfTransfer() && accounts.length < 2) {
          s.error('This customer needs at least two active accounts for a self transfer.');
        }
      } catch (error) {
        s.accounts([]);
        s.useCustomerAccounts(false);
        s.error(error.message);
      } finally {
        s.loadingAccounts(false);
      }
    };

    s.form.fromAccountId.subscribe((accountId) => {
      const account = s.accounts().find((item) => String(item.accountId) === String(accountId));
      if (account) s.form.currencyCode(account.currencyCode);
    });

    function requestPayload() {
      const operation = s.operation();
      const type = operation === 'SELF_TRANSFER' ? 'TRANSFER' : operation;
      let debitAccountId = null;
      let creditAccountId = null;
      let customerId = null;

      if (operation === 'TRANSFER') {
        debitAccountId = String(s.form.debitAccountId() || '').trim();
        creditAccountId = String(s.form.creditAccountId() || '').trim();
      } else if (operation === 'SELF_TRANSFER') {
        debitAccountId = String(s.form.fromAccountId() || '').trim();
        creditAccountId = String(s.form.toAccountId() || '').trim();
        customerId = s.form.customerId().trim();
      } else if (operation === 'DEPOSIT') {
        creditAccountId = String(s.form.accountId() || '').trim();
      } else {
        debitAccountId = String(s.form.accountId() || '').trim();
      }

      return {
        transactionRef: s.form.transactionRef(),
        transactionType: type,
        transactionStatus: null,
        debitAccountId,
        creditAccountId,
        externalBeneficiary: null,
        amount: Number(s.form.amount()),
        currencyCode: s.form.currencyCode().trim().toUpperCase(),
        feeAmount: 0,
        initiatedByCustomerId: customerId,
        initiatedByUserId: null,
        completedAt: null,
        failureCode: null,
        failureReason: null,
      };
    }

    function validate(payload) {
      if (!Number.isFinite(payload.amount) || payload.amount <= 0) return 'Enter a positive amount.';
      if (!/^[A-Z]{3}$/.test(payload.currencyCode)) return 'Currency must contain three letters.';
      if (s.operation() === 'TRANSFER' && (!payload.debitAccountId || !payload.creditAccountId)) {
        return 'Enter both debit and credit account IDs.';
      }
      if (s.isSingleAccount() && !(payload.debitAccountId || payload.creditAccountId)) {
        return 'Enter the account ID.';
      }
      if (s.isSelfTransfer()) {
        if (s.accounts().length < 2) return 'Load at least two active customer accounts.';
        if (!payload.debitAccountId || !payload.creditAccountId) return 'Select both accounts.';
        const from = s.accounts().find((a) => String(a.accountId) === String(payload.debitAccountId));
        const to = s.accounts().find((a) => String(a.accountId) === String(payload.creditAccountId));
        if (!from || !to) return 'Select accounts from the loaded customer account list.';
        if (from.currencyCode !== to.currencyCode) return 'Self-transfer accounts must use the same currency.';
      }
      if (payload.debitAccountId && payload.debitAccountId === payload.creditAccountId) {
        return 'Debit and credit accounts must be different.';
      }
      return null;
    }

    s.submit = async () => {
      const payload = requestPayload();
      const validationError = validate(payload);
      if (validationError) return s.error(validationError);

      s.busy(true);
      s.error('');
      try {
        const transaction = await app.services.transactions.transfer(payload);

        app.setActiveAccount(payload.debitAccountId || payload.creditAccountId);
        s.state.data([transaction].concat(s.state.data().filter((item) => item.transactionId !== transaction.transactionId)));

        s.close();
        app.notify(`${s.operationTitle()} completed.`, 'success');
      } catch (error) {
        if (error.details && error.details.transactionId) {
          await s.load(0);
        }
        s.error(error.message);
      } finally {
        s.busy(false);
      }
    };
    s.load();
  }
  return VM;
});
