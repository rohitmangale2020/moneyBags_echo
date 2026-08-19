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
    s.isTransactionPage = app.selection.path() === 'new-transaction';
    s.state = u.state([]);
    s.pageSize = ko.observable(10);
    s.pageSizeOptions = [5, 10, 20];
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
    s.submissionState = ko.observable('idle');
    s.submissionMessage = ko.observable('');
    s.loadingAccounts = ko.observable(false);
    s.loadingRecipientAccounts = ko.observable(false);
    s.accounts = ko.observableArray([]);
    s.recipientAccounts = ko.observableArray([]);
    s.customerMatches = ko.observableArray([]);
    s.recipientMatches = ko.observableArray([]);
    s.activeCustomer = app.activeCustomer;
    s.hasActiveCustomer = app.hasActiveCustomer;
    s.contextAccounts = ko.observableArray([]);
    s.customerId = ko.observable('');

    s.form = {
      transactionRef: ko.observable(u.ref()),
      debitAccountId: ko.observable(''),
      creditAccountId: ko.observable(''),
      accountId: ko.observable(''),
      customerCif: ko.observable(''),
      recipientCustomerCif: ko.observable(''),
      fromAccountId: ko.observable(''),
      toAccountId: ko.observable(''),
      amount: ko.observable(''),
      currencyCode: ko.observable('INR'),
    };

    s.money = u.money;
    s.date = u.date;
    s.customerLookupLabel = (customer) => `${[customer.firstName, customer.lastName].filter(Boolean).join(' ')} · ${customer.phone || 'No phone'} · ${customer.cifNo}`;
    s.isInternalTransfer = ko.pureComputed(() => s.operation() === 'TRANSFER');
    s.isSelfTransfer = ko.pureComputed(() => s.operation() === 'SELF_TRANSFER');
    s.isSingleAccount = ko.pureComputed(
      () => s.operation() === 'DEPOSIT' || s.operation() === 'WITHDRAWAL',
    );
    s.hasCustomerAccounts = ko.pureComputed(() => s.accounts().length > 0);
    s.eligibleRecipientAccounts = ko.pureComputed(() => {
      const debitAccountId = String(s.form.debitAccountId() || '');
      const currencyCode = String(s.form.currencyCode() || '').toUpperCase();
      return s.recipientAccounts().filter((account) =>
        String(account.accountId) !== debitAccountId
        && (!currencyCode || String(account.currencyCode || '').toUpperCase() === currencyCode),
      );
    });
    s.hasRecipientAccounts = ko.pureComputed(() => s.eligibleRecipientAccounts().length > 0);
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
      const descriptionQuery = String(s.query() || '').trim().toLowerCase();
      const isAccountNumberSearch = /^\d+$/.test(descriptionQuery);
      const from = s.dateFrom() ? new Date(`${s.dateFrom()}T00:00:00`).getTime() : null;
      const to = s.dateTo() ? new Date(`${s.dateTo()}T23:59:59.999`).getTime() : null;
      const transactions = s.state.data().filter((transaction) => {
        const initiated = timestamp(transaction.initiatedAt);
        return (s.typeFilter() === 'ALL' || transaction.transactionType === s.typeFilter())
          && (s.statusFilter() === 'ALL' || transaction.transactionStatus === s.statusFilter())
          && (s.currencyFilter() === 'ALL' || transaction.currencyCode === s.currencyFilter())
          && (!descriptionQuery || isAccountNumberSearch
            || String(transaction.description || '').toLowerCase().includes(descriptionQuery))
          && (from === null || initiated >= from)
          && (to === null || initiated <= to);
      });
      const sorters = {
        'initiated-desc': (a, b) => timestamp(b.initiatedAt) - timestamp(a.initiatedAt),
        'initiated-asc': (a, b) => timestamp(a.initiatedAt) - timestamp(b.initiatedAt),
        'amount-desc': (a, b) => Number(b.amount || 0) - Number(a.amount || 0),
        'amount-asc': (a, b) => Number(a.amount || 0) - Number(b.amount || 0),
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
    s.loadContextAccounts = async () => {
      if (!s.hasActiveCustomer()) {
        s.contextAccounts([]);
        return;
      }
      try {
        const accounts = u.list(await app.services.accounts.customer(s.activeCustomer().customerId));
        s.contextAccounts(accounts);
      } catch (_) {
        s.contextAccounts([]);
      }
    };
    s.load = (requestedPage) => s.state.run(async () => {
      const page = Number.isInteger(requestedPage) ? requestedPage : s.currentPage();
      const accountNumber = String(s.query() || '').trim();
      if (/^\d+$/.test(accountNumber)) {
        const accounts = u.list(await app.services.accounts.number(accountNumber));
        if (!accounts.length) {
          s.currentPage(0);
          s.totalTransactions(0);
          s.totalPages(0);
          return [];
        }
        const accountId = String(accounts[0].accountId);
        const [debits, credits] = await Promise.all([
          app.services.transactions.find('debitAccountId', accountId),
          app.services.transactions.find('creditAccountId', accountId),
        ]);
        const transactions = [...u.list(debits), ...u.list(credits)];
        const uniqueTransactions = Array.from(
          new Map(transactions.map((transaction) => [transaction.transactionId, transaction])).values(),
        );
        const totalPages = Math.max(1, Math.ceil(uniqueTransactions.length / Number(s.pageSize())));
        const safePage = Math.min(Math.max(0, page), totalPages - 1);
        s.currentPage(safePage);
        s.totalTransactions(uniqueTransactions.length);
        s.totalPages(totalPages);
        return uniqueTransactions.slice(safePage * Number(s.pageSize()), (safePage + 1) * Number(s.pageSize()));
      }
      if (accountNumber) {
        const transactions = u.list(await app.services.transactions.list()).filter((transaction) =>
          String(transaction.description || '').toLowerCase().includes(accountNumber.toLowerCase()),
        );
        const totalPages = Math.max(1, Math.ceil(transactions.length / Number(s.pageSize())));
        const safePage = Math.min(Math.max(0, page), totalPages - 1);
        s.currentPage(safePage);
        s.totalTransactions(transactions.length);
        s.totalPages(totalPages);
        return transactions.slice(safePage * Number(s.pageSize()), (safePage + 1) * Number(s.pageSize()));
      }
      if (s.hasActiveCustomer()) {
        const customerId = String(s.activeCustomer().customerId);
        const accounts = s.contextAccounts().length
          ? s.contextAccounts()
          : u.list(await app.services.accounts.customer(customerId));
        s.contextAccounts(accounts);
        const accountIds = accounts.map((account) => String(account.accountId));
        const responses = await Promise.all(accountIds.flatMap((accountId) => [
          app.services.transactions.find('debitAccountId', accountId),
          app.services.transactions.find('creditAccountId', accountId),
        ]));
        const transactions = Array.from(new Map(responses.flatMap(u.list)
          .map((transaction) => [transaction.transactionId, transaction])).values());
        const totalPages = Math.max(1, Math.ceil(transactions.length / Number(s.pageSize())));
        const safePage = Math.min(Math.max(0, page), totalPages - 1);
        s.currentPage(safePage);
        s.totalTransactions(transactions.length);
        s.totalPages(totalPages);
        return transactions.slice(safePage * Number(s.pageSize()), (safePage + 1) * Number(s.pageSize()));
      }
      const response = await app.services.transactions.list(page, Number(s.pageSize()));
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
    s.pageSize.subscribe(() => s.load(0));
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
    let transactionSearchTimer = null;
    s.query.subscribe(() => {
      if (transactionSearchTimer) window.clearTimeout(transactionSearchTimer);
      transactionSearchTimer = window.setTimeout(() => s.load(0), 300);
    });
    s.activeCustomer.subscribe(() => {
      s.query('');
      s.loadContextAccounts();
      s.load(0);
    });
    if (app.activeAccountId) app.activeAccountId.subscribe(() => s.loadContextAccounts());

    s.selectOperation = (operation) => {
      s.operation(operation);
      s.error('');
      if (s.hasActiveCustomer()) s.loadCustomerAccounts();
    };

    let closeTimer = null;
    const clearCloseTimer = () => {
      if (closeTimer) window.clearTimeout(closeTimer);
      closeTimer = null;
    };
    const closeAfter = (milliseconds) => {
      clearCloseTimer();
      closeTimer = window.setTimeout(() => s.close(), milliseconds);
    };

    s.openPage = () => app.go('new-transaction');
    s.open = () => {
      clearCloseTimer();
      s.form.transactionRef(u.ref());
      s.form.debitAccountId('');
      s.form.creditAccountId('');
      s.form.accountId('');
      s.form.customerCif(s.hasActiveCustomer() ? s.activeCustomer().cifNo : '');
      s.form.recipientCustomerCif('');
      s.customerId('');
      s.form.fromAccountId('');
      s.form.toAccountId('');
      s.form.amount('');
      s.form.currencyCode('INR');
      s.accounts([]);
      s.recipientAccounts([]);
      s.customerMatches([]);
      s.recipientMatches([]);
      s.operation('TRANSFER');
      s.error('');
      s.submissionState('idle');
      s.submissionMessage('');
      if (!s.isTransactionPage) document.getElementById('transactionDialog').open();
      if (s.hasActiveCustomer()) s.loadCustomerAccounts();
    };

    s.close = () => {
      clearCloseTimer();
      if (s.isTransactionPage) app.go('transactions');
      else document.getElementById('transactionDialog').close();
      s.submissionState('idle');
      s.submissionMessage('');
    };

    s.resolveCustomer = async (value, matchList) => {
      const searchValue = String(value || '').trim();
      if (!searchValue) throw new Error('Enter a customer CIF or name first.');
      if (/^CIF/i.test(searchValue)) {
        matchList([]);
        return app.services.customers.byCif(searchValue);
      }
      const firstName = searchValue.split(/\s+/)[0];
      const matches = u.list(await app.services.customers.byFirstName(firstName)).filter((customer) =>
        [customer.firstName, customer.lastName].filter(Boolean).join(' ')
          .toLowerCase().includes(searchValue.toLowerCase()),
      );
      if (!matches.length) throw new Error('No customer matches that name.');
      if (matches.length > 1) {
        matchList(matches);
        return null;
      }
      matchList([]);
      return matches[0];
    };
    s.loadCustomerAccounts = async () => {
      const customerLookup = String(s.form.customerCif() || '').trim();
      if (!customerLookup) return s.error('Enter a customer CIF or name first.');
      s.loadingAccounts(true);
      s.error('');
      try {
        const customer = await s.resolveCustomer(customerLookup, s.customerMatches);
        if (!customer) return s.error('Choose a matching customer below.');
        const customerId = String(customer.customerId);
        if (app.setTransactionCustomerId) app.setTransactionCustomerId(customerId);
        const accounts = (await app.services.accounts.customer(customerId))
          .filter((account) => account.status === 'ACTIVE'
            && String(account.productTypeCode || '').toUpperCase() !== 'FD');
        s.customerId(customerId);
        s.accounts(accounts);
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
        s.customerId('');
        s.accounts([]);
        s.error(error.message);
      } finally {
        s.loadingAccounts(false);
      }
    };

    s.loadRecipientAccounts = async () => {
      const recipientLookup = String(s.form.recipientCustomerCif() || '').trim();
      if (!recipientLookup) return s.error('Enter the recipient CIF or name first.');
      s.loadingRecipientAccounts(true);
      s.error('');
      try {
        const customer = await s.resolveCustomer(recipientLookup, s.recipientMatches);
        if (!customer) return s.error('Choose a matching recipient below.');
        const accounts = (await app.services.accounts.customer(String(customer.customerId)))
          .filter((account) => account.status === 'ACTIVE'
            && String(account.productTypeCode || '').toUpperCase() !== 'FD');
        s.recipientAccounts(accounts);
        const eligibleAccounts = s.eligibleRecipientAccounts();
        s.form.creditAccountId(eligibleAccounts[0] ? String(eligibleAccounts[0].accountId) : '');
        if (!eligibleAccounts.length) {
          s.error('The recipient has no eligible active account in the selected currency.');
        }
      } catch (error) {
        s.recipientAccounts([]);
        s.form.creditAccountId('');
        s.error(error.message);
      } finally {
        s.loadingRecipientAccounts(false);
      }
    };

    s.form.fromAccountId.subscribe((accountId) => {
      const account = s.accounts().find((item) => String(item.accountId) === String(accountId));
      if (account) s.form.currencyCode(account.currencyCode);
    });
    s.chooseCustomerMatch = (customer) => {
      if (!customer) return;
      s.form.customerCif(customer.cifNo);
      s.customerMatches([]);
      s.loadCustomerAccounts();
    };
    s.chooseRecipientMatch = (customer) => {
      if (!customer) return;
      s.form.recipientCustomerCif(customer.cifNo);
      s.recipientMatches([]);
      s.loadRecipientAccounts();
    };
    const findCustomerMatches = async (value, matchList) => {
      const searchValue = String(value || '').trim();
      if (searchValue.length < 2 || /^CIF/i.test(searchValue)) {
        matchList([]);
        return;
      }
      try {
        const firstName = searchValue.split(/\s+/)[0];
        const matches = u.list(await app.services.customers.byFirstName(firstName)).filter((customer) =>
          [customer.firstName, customer.lastName].filter(Boolean).join(' ')
            .toLowerCase().includes(searchValue.toLowerCase()),
        );
        matchList(matches);
      } catch (_) {
        matchList([]);
      }
    };
    let customerMatchTimer = null;
    let recipientMatchTimer = null;
    const scheduleCustomerMatches = (value, matchList, timerName) => {
      if (timerName === 'customer' && customerMatchTimer) window.clearTimeout(customerMatchTimer);
      if (timerName === 'recipient' && recipientMatchTimer) window.clearTimeout(recipientMatchTimer);
      const timer = window.setTimeout(() => findCustomerMatches(value, matchList), 250);
      if (timerName === 'customer') customerMatchTimer = timer;
      else recipientMatchTimer = timer;
    };
    s.form.customerCif.subscribe((value) => scheduleCustomerMatches(value, s.customerMatches, 'customer'));
    s.form.recipientCustomerCif.subscribe((value) => scheduleCustomerMatches(value, s.recipientMatches, 'recipient'));
    s.form.debitAccountId.subscribe((accountId) => {
      const account = s.accounts().find((item) => String(item.accountId) === String(accountId));
      if (account) s.form.currencyCode(account.currencyCode);
      const eligibleAccounts = s.eligibleRecipientAccounts();
      if (!eligibleAccounts.some((item) => String(item.accountId) === String(s.form.creditAccountId()))) {
        s.form.creditAccountId(eligibleAccounts[0] ? String(eligibleAccounts[0].accountId) : '');
      }
    });
    s.form.accountId.subscribe((accountId) => {
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
        customerId = s.customerId();
      } else if (operation === 'DEPOSIT') {
        creditAccountId = String(s.form.accountId() || '').trim();
      } else {
        debitAccountId = String(s.form.accountId() || '').trim();
      }

      return {
        transactionRef: s.form.transactionRef(),
        transactionType: type,
        transactionStatus: 'INITIATED',
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
      if (s.operation() === 'TRANSFER' && !payload.debitAccountId) {
        return 'Load a customer CIF and select a debit account.';
      }
      if (s.operation() === 'TRANSFER' && !payload.creditAccountId) {
        return 'Load the recipient CIF and select a recipient account.';
      }
      if (s.operation() === 'TRANSFER'
        && !s.eligibleRecipientAccounts().some((account) =>
          String(account.accountId) === String(payload.creditAccountId))) {
        return 'Select an eligible account belonging to the loaded recipient.';
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
      let payload = requestPayload();
      const validationError = validate(payload);
      if (validationError) return s.error(validationError);

      s.busy(true);
      s.error('');
      s.submissionState('submitting');
      s.submissionMessage(`Posting ${s.operationTitle().toLowerCase()}...`);
      try {
        const transaction = await app.services.transactions.transfer(payload);

        app.setActiveAccount(payload.debitAccountId || payload.creditAccountId);
        s.state.data([transaction].concat(s.state.data().filter((item) => item.transactionId !== transaction.transactionId)));

        s.submissionState('success');
        s.submissionMessage(`${s.operationTitle()} completed successfully.`);
        app.notify(`${s.operationTitle()} completed.`, 'success');
        closeAfter(1400);
      } catch (error) {
        if (error.details && error.details.transactionId) {
          await s.load(0);
        }
        s.error(error.message || 'The transaction could not be completed.');
        s.submissionState('idle');
        s.submissionMessage('');
        app.notify(`${s.operationTitle()} failed.`, 'error');
      } finally {
        s.busy(false);
      }
    };
    if (s.isTransactionPage) s.open();
    else {
      s.loadContextAccounts();
      s.load();
    }
  }
  return VM;
});
