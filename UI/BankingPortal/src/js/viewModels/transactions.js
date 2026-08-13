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
    s.operation = ko.observable('TRANSFER');
    s.busy = ko.observable(false);
    s.loadingAccounts = ko.observable(false);
    s.accounts = ko.observableArray([]);

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
    s.operationTitle = ko.pureComputed(() => ({
      TRANSFER: 'Internal transfer',
      DEPOSIT: 'Deposit',
      WITHDRAWAL: 'Withdrawal',
      SELF_TRANSFER: 'Self transfer',
    })[s.operation()]);

    s.accountLabel = (account) =>
      `${account.accountNumber} · ${account.currencyCode} · ${u.money(account.availableBalance, account.currencyCode)}`;

    s.search = () => {
      if (!s.query().trim()) {
        s.state.error('Enter an account ID to search.');
        return Promise.resolve();
      }
      return s.state.run(() => app.services.transactions.find(s.kind(), s.query().trim())).catch(() => null);
    };

    s.selectOperation = (operation) => {
      s.operation(operation);
      s.error('');
    };

    s.open = () => {
      s.form.transactionRef(u.ref());
      s.form.debitAccountId('');
      s.form.creditAccountId('');
      s.form.accountId('');
      s.form.customerId('');
      s.form.fromAccountId('');
      s.form.toAccountId('');
      s.form.amount('');
      s.form.currencyCode('INR');
      s.accounts([]);
      s.operation('TRANSFER');
      s.error('');
      document.getElementById('transactionDialog').open();
    };

    s.close = () => document.getElementById('transactionDialog').close();

    s.loadCustomerAccounts = async () => {
      const customerId = s.form.customerId().trim();
      if (!customerId) return s.error('Enter a customer ID first.');
      s.loadingAccounts(true);
      s.error('');
      try {
        const accounts = (await app.services.accounts.customer(customerId))
          .filter((account) => account.status === 'ACTIVE');
        s.accounts(accounts);
        s.form.fromAccountId(accounts[0] ? accounts[0].accountId : '');
        s.form.toAccountId(accounts[1] ? accounts[1].accountId : '');
        if (accounts[0]) s.form.currencyCode(accounts[0].currencyCode);
        if (accounts.length < 2) s.error('This customer needs at least two active accounts for a self transfer.');
      } catch (error) {
        s.accounts([]);
        s.error(error.message);
      } finally {
        s.loadingAccounts(false);
      }
    };

    s.form.fromAccountId.subscribe((accountId) => {
      const account = s.accounts().find((item) => item.accountId === accountId);
      if (account) s.form.currencyCode(account.currencyCode);
    });

    function requestPayload() {
      const operation = s.operation();
      const type = operation === 'SELF_TRANSFER' ? 'TRANSFER' : operation;
      let debitAccountId = null;
      let creditAccountId = null;
      let customerId = null;

      if (operation === 'TRANSFER') {
        debitAccountId = s.form.debitAccountId().trim();
        creditAccountId = s.form.creditAccountId().trim();
      } else if (operation === 'SELF_TRANSFER') {
        debitAccountId = s.form.fromAccountId();
        creditAccountId = s.form.toAccountId();
        customerId = s.form.customerId().trim();
      } else if (operation === 'DEPOSIT') {
        creditAccountId = s.form.accountId().trim();
      } else {
        debitAccountId = s.form.accountId().trim();
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
        const from = s.accounts().find((a) => a.accountId === payload.debitAccountId);
        const to = s.accounts().find((a) => a.accountId === payload.creditAccountId);
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
        s.state.data([transaction].concat(s.state.data().filter((item) => item.transactionId !== transaction.transactionId)));
        s.close();
        app.notify(`${s.operationTitle()} completed.`, 'success');
      } catch (error) {
        if (error.details && error.details.transactionId) {
          s.state.data([error.details].concat(s.state.data()));
        }
        s.error(error.message);
      } finally {
        s.busy(false);
      }
    };
  }
  return VM;
});
