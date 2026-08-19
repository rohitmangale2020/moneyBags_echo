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
    s.contextCustomerId = ko.observable('');
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
    const isEligibleTransactionAccount = (account) => account
      && account.status === 'ACTIVE'
      && ['SAVINGS', 'SALARY', 'CURRENT'].includes(
        String(account.productTypeCode || '').toUpperCase(),
      );
    s.eligibleAccounts = ko.pureComputed(() =>
      s.accounts().filter(isEligibleTransactionAccount),
    );
    s.hasCustomerAccounts = ko.pureComputed(() => s.eligibleAccounts().length > 0);
    s.eligibleRecipientAccounts = ko.pureComputed(() => {
      const debitAccountId = String(s.form.debitAccountId() || '');
      const currencyCode = String(s.form.currencyCode() || '').toUpperCase();
      return s.recipientAccounts().filter((account) =>
        isEligibleTransactionAccount(account)
        && String(account.accountId) !== debitAccountId
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
      `${account.accountNumber} · ${account.productTypeCode || 'ACCOUNT'} · ${account.currencyCode} · ${u.money(account.availableBalance, account.currencyCode)}`;

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
    s.pagedTransactions = ko.pureComputed(() => {
      const size = Number(s.pageSize());
      const start = s.currentPage() * size;
      return s.filteredTransactions().slice(start, start + size);
    });
    s.filteredTransactionCount = ko.pureComputed(() => s.filteredTransactions().length);
    s.pageCount = ko.pureComputed(() => {
      const count = s.filteredTransactionCount();
      return count ? Math.ceil(count / Number(s.pageSize())) : 0;
    });
    s.updatePagination = () => {
      const pages = s.pageCount();
      const safePage = pages ? Math.min(s.currentPage(), pages - 1) : 0;
      if (s.currentPage() !== safePage) s.currentPage(safePage);
    };
    s.resultSummary = ko.pureComputed(() => {
      const shown = s.filteredTransactionCount();
      const filtersApplied = Boolean(
        String(s.query() || '').trim()
        || s.typeFilter() !== 'ALL'
        || s.statusFilter() !== 'ALL'
        || s.currencyFilter() !== 'ALL'
        || s.dateFrom()
        || s.dateTo(),
      );
      if (filtersApplied) return `Showing ${shown} matching transaction${shown === 1 ? '' : 's'}`;
      return `${shown} transaction${shown === 1 ? '' : 's'}`;
    });
    s.statusClass = (status) => String(status || '').toLowerCase();
    s.loadContextAccounts = async () => {
      if (!s.hasActiveCustomer()) {
        s.contextAccounts([]);
        s.contextCustomerId('');
        return [];
      }
      const customerId = String(s.activeCustomer().customerId);
      try {
        const accounts = u.list(await app.services.accounts.customer(customerId));
        if (!s.hasActiveCustomer() || String(s.activeCustomer().customerId) !== customerId) return [];
        s.contextAccounts(accounts);
        s.contextCustomerId(customerId);
        return accounts;
      } catch (_) {
        if (!s.hasActiveCustomer() || String(s.activeCustomer().customerId) === customerId) {
          s.contextAccounts([]);
          s.contextCustomerId('');
        }
        return [];
      }
    };
    s.load = (requestedPage) => s.state.run(async () => {
      const page = Number.isInteger(requestedPage) ? requestedPage : s.currentPage();
      const accountNumber = String(s.query() || '').trim();
      if (/^\d+$/.test(accountNumber)) {
        const accounts = u.list(await app.services.accounts.number(accountNumber));
        if (!accounts.length) {
          s.currentPage(0);
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
        s.currentPage(Math.max(0, page));
        return uniqueTransactions;
      }
      if (accountNumber) {
        const transactions = u.list(await app.services.transactions.list()).filter((transaction) =>
          String(transaction.description || '').toLowerCase().includes(accountNumber.toLowerCase()),
        );
        s.currentPage(Math.max(0, page));
        return transactions;
      }
      if (s.hasActiveCustomer()) {
        const customerId = String(s.activeCustomer().customerId);
        const accounts = s.contextCustomerId() === customerId
          ? s.contextAccounts()
          : await s.loadContextAccounts();
        const accountIds = accounts.map((account) => String(account.accountId));
        const responses = await Promise.all(accountIds.flatMap((accountId) => [
          app.services.transactions.find('debitAccountId', accountId),
          app.services.transactions.find('creditAccountId', accountId),
        ]));
        const transactions = Array.from(new Map(responses.flatMap(u.list)
          .map((transaction) => [transaction.transactionId, transaction])).values());
        s.currentPage(Math.max(0, page));
        return transactions;
      }
      const transactions = u.list(await app.services.transactions.list());
      s.currentPage(Math.max(0, page));
      return transactions;
    }).catch(() => null);
    s.previousPage = () => {
      if (s.currentPage() > 0) s.currentPage(s.currentPage() - 1);
    };
    s.nextPage = () => {
      if (s.currentPage() < s.pageCount() - 1) s.currentPage(s.currentPage() + 1);
    };
    s.state.data.subscribe(() => s.updatePagination());
    [s.pageSize, s.typeFilter, s.statusFilter, s.currencyFilter, s.dateFrom, s.dateTo, s.sortBy]
      .forEach((filter) => filter.subscribe(() => s.updatePagination()));
    s.pageSize.subscribe(() => s.currentPage(0));
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
    s.activeCustomer.subscribe(async () => {
      s.query('');
      s.currentPage(0);
      s.contextAccounts([]);
      s.contextCustomerId('');
      await s.loadContextAccounts();
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
          .filter(isEligibleTransactionAccount);
        s.customerId(customerId);
        s.accounts(accounts);
        const eligibleAccounts = s.eligibleAccounts();
        const firstAccountId = eligibleAccounts[0] ? String(eligibleAccounts[0].accountId) : '';
        const secondAccountId = eligibleAccounts[1] ? String(eligibleAccounts[1].accountId) : '';
        s.form.fromAccountId(firstAccountId);
        s.form.toAccountId(secondAccountId);
        s.form.debitAccountId(firstAccountId);
        s.form.accountId(firstAccountId);
        if (eligibleAccounts[0]) s.form.currencyCode(eligibleAccounts[0].currencyCode);
        if (!eligibleAccounts.length) {
          s.error('This customer has no eligible Savings, Salary, or Current account for a normal transaction.');
        } else if (s.isSelfTransfer() && eligibleAccounts.length < 2) {
          s.error('This customer needs at least two eligible Savings, Salary, or Current accounts for a self transfer.');
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

    async function validateTransactionCustomersAreActive(payload) {
      const accountIds = [...new Set([payload.debitAccountId, payload.creditAccountId]
        .filter(Boolean)
        .map(String))];
      if (!accountIds.length) return null;
      try {
        const accounts = await Promise.all(accountIds.map((accountId) => app.services.accounts.get(accountId)));
        const customerIds = [...new Set(accounts.map((account) => String(account.customerId || '')).filter(Boolean))];
        const customers = await Promise.all(customerIds.map((customerId) => app.services.customers.get(customerId)));
        return customers.some((customer) => String(customer.status || '').toUpperCase() !== 'ACTIVE')
          ? 'Only an active customer can make a transaction.'
          : null;
      } catch (_) {
        return 'Unable to verify the customer status for this transaction.';
      }
    }

    s.submit = async () => {
      let payload = requestPayload();
      const validationError = validate(payload);
      if (validationError) return s.error(validationError);
      const customerStatusError = await validateTransactionCustomersAreActive(payload);
      if (customerStatusError) return s.error(customerStatusError);

      s.busy(true);
      s.error('');
      s.submissionState('submitting');
      s.submissionMessage(`Posting ${s.operationTitle().toLowerCase()}...`);
      try {
        const transaction = await app.services.transactions.transfer(payload);

        app.setActiveAccount(payload.debitAccountId || payload.creditAccountId);
        s.state.data([transaction].concat(s.state.data().filter((item) => item.transactionId !== transaction.transactionId)));

        const held = transaction.transactionStatus === 'PENDING_APPROVAL';
        s.submissionState(held ? 'pending' : 'success');
        s.submissionMessage(held
          ? `${s.operationTitle()} is awaiting administrator approval (${transaction.riskLevel || 'RISK_UNAVAILABLE'}).`
          : `${s.operationTitle()} completed successfully.`);
        app.notify(held ? `${s.operationTitle()} is awaiting administrator approval.` : `${s.operationTitle()} completed.`, held ? 'warning' : 'success');
        if (held) app.refreshRiskApprovals();
        closeAfter(held ? 2600 : 1400);
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
      s.loadContextAccounts().then(() => s.load());
    }
  }
  return VM;
});
