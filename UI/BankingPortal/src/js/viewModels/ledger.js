define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojbutton',
  'ojs/ojdialog',
], function (ko, app, u) {
  'use strict';

  const dateValue = (value) => {
    const parsed = Date.parse(value || '');
    return Number.isNaN(parsed) ? 0 : parsed;
  };
  const today = () => new Date().toISOString().slice(0, 10);

  function VM() {
    const s = this;
    s.app = app;
    s.accountState = u.state([]);
    s.entryState = u.state([]);
    s.transactionRef = ko.observable('');
    s.accountCode = ko.observable('');
    s.entryType = ko.observable('ALL');
    s.currency = ko.observable('ALL');
    s.dateFrom = ko.observable('');
    s.dateTo = ko.observable('');
    s.query = ko.observable('');
    s.sortBy = ko.observable('date-desc');
    s.postingError = ko.observable('');
    s.postingBusy = ko.observable(false);

    const entryLine = () => ({
      ledgerAccountCode: ko.observable(''),
      customerAccountId: ko.observable(''),
      entryType: ko.observable('DEBIT'),
      amount: ko.observable(''),
      description: ko.observable(''),
    });
    s.posting = {
      transactionRef: ko.observable(u.ref()),
      postingDate: ko.observable(today()),
      currencyCode: ko.observable('INR'),
      description: ko.observable(''),
      items: ko.observableArray([entryLine(), entryLine()]),
    };

    s.money = u.money;
    s.date = (value) => value
      ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(new Date(`${value}T00:00:00`))
      : '—';
    s.accountType = (type) => String(type || '').replace(/_/g, ' ').toLowerCase()
      .replace(/\b\w/g, (letter) => letter.toUpperCase());
    s.entryClass = (type) => String(type || '').toLowerCase();
    s.currencies = ko.pureComputed(() => Array.from(new Set(
      s.entryState.data().map((entry) => entry.currencyCode).filter(Boolean),
    )).sort());
    s.selectedScope = ko.pureComputed(() => {
      if (s.transactionRef().trim()) return `Transaction ${s.transactionRef().trim()}`;
      if (s.accountCode().trim()) return `Account ${s.accountCode().trim().toUpperCase()}`;
      return 'Choose an account or transaction reference';
    });
    s.filteredEntries = ko.pureComputed(() => {
      const text = s.query().trim().toLowerCase();
      const from = s.dateFrom() ? dateValue(s.dateFrom()) : null;
      const to = s.dateTo() ? dateValue(s.dateTo()) : null;
      const sorters = {
        'date-desc': (a, b) => dateValue(b.postingDate) - dateValue(a.postingDate) || b.lineNumber - a.lineNumber,
        'date-asc': (a, b) => dateValue(a.postingDate) - dateValue(b.postingDate) || a.lineNumber - b.lineNumber,
        'amount-desc': (a, b) => Number(b.amount || 0) - Number(a.amount || 0),
        'account-asc': (a, b) => String(a.ledgerAccountCode || '').localeCompare(String(b.ledgerAccountCode || '')),
      };
      return s.entryState.data().filter((entry) => {
        const searchable = [entry.transactionRef, entry.ledgerAccountCode, entry.customerAccountId, entry.description]
          .filter(Boolean).join(' ').toLowerCase();
        const postingDate = dateValue(entry.postingDate);
        return (!text || searchable.includes(text))
          && (s.entryType() === 'ALL' || entry.entryType === s.entryType())
          && (s.currency() === 'ALL' || entry.currencyCode === s.currency())
          && (from === null || postingDate >= from)
          && (to === null || postingDate <= to);
      }).slice().sort(sorters[s.sortBy()] || sorters['date-desc']);
    });
    s.resultSummary = ko.pureComputed(() => {
      const loaded = s.entryState.data().length;
      const shown = s.filteredEntries().length;
      return shown === loaded ? `${loaded} ledger ${loaded === 1 ? 'entry' : 'entries'}` : `${shown} of ${loaded} entries`;
    });
    s.totalDebits = ko.pureComputed(() => s.posting.items().filter((item) => item.entryType() === 'DEBIT')
      .reduce((total, item) => total + (Number(item.amount()) || 0), 0));
    s.totalCredits = ko.pureComputed(() => s.posting.items().filter((item) => item.entryType() === 'CREDIT')
      .reduce((total, item) => total + (Number(item.amount()) || 0), 0));
    s.isBalanced = ko.pureComputed(() => s.totalDebits() > 0
      && Math.abs(s.totalDebits() - s.totalCredits()) < 0.00001);

    s.loadAccounts = () => s.accountState.run(() => app.services.ledger.accounts()).catch(() => null);
    s.loadEntries = () => {
      const transactionRef = s.transactionRef().trim();
      const accountCode = s.accountCode().trim().toUpperCase();
      if (!transactionRef && !accountCode) {
        s.entryState.error('Enter an account code or transaction reference to load ledger entries.');
        return Promise.resolve();
      }
      if (transactionRef && accountCode) {
        s.entryState.error('Use either an account code or a transaction reference, not both.');
        return Promise.resolve();
      }
      return s.entryState.run(() => app.services.ledger.entries({ transactionRef, accountCode })).catch(() => null);
    };
    s.selectAccount = (account) => {
      s.transactionRef('');
      s.accountCode(account.code);
      s.loadEntries();
    };
    s.clearFilters = () => {
      s.entryType('ALL');
      s.currency('ALL');
      s.dateFrom('');
      s.dateTo('');
      s.query('');
      s.sortBy('date-desc');
    };
    s.openPost = () => {
      s.posting.transactionRef(u.ref());
      s.posting.postingDate(today());
      s.posting.currencyCode('INR');
      s.posting.description('');
      s.posting.items([entryLine(), entryLine()]);
      s.postingError('');
      document.getElementById('ledgerPostingDialog').open();
    };
    s.closePost = () => document.getElementById('ledgerPostingDialog').close();
    s.addLine = () => s.posting.items.push(entryLine());
    s.removeLine = (item) => {
      if (s.posting.items().length > 2) s.posting.items.remove(item);
    };
    s.submitPosting = async () => {
      const payload = {
        transactionRef: s.posting.transactionRef().trim(),
        postingDate: s.posting.postingDate() || null,
        currencyCode: s.posting.currencyCode().trim().toUpperCase(),
        description: s.posting.description().trim() || null,
        items: s.posting.items().map((item) => ({
          ledgerAccountCode: item.ledgerAccountCode().trim().toUpperCase(),
          customerAccountId: item.customerAccountId().trim() || null,
          entryType: item.entryType(),
          amount: Number(item.amount()),
          description: item.description().trim() || null,
        })),
      };
      if (!payload.transactionRef) return s.postingError('Transaction reference is required.');
      if (!/^[A-Z]{3}$/.test(payload.currencyCode)) return s.postingError('Currency must contain three letters.');
      if (!s.isBalanced()) return s.postingError('Debits and credits must be equal and greater than zero.');
      if (payload.items.some((item) => !item.ledgerAccountCode || !Number.isFinite(item.amount) || item.amount <= 0)) {
        return s.postingError('Every line needs a ledger account and a positive amount.');
      }
      s.postingBusy(true);
      s.postingError('');
      try {
        const entries = await app.services.ledger.post(payload);
        s.transactionRef(payload.transactionRef);
        s.accountCode('');
        s.entryState.data(entries);
        await s.loadAccounts();
        s.closePost();
        app.notify('Balanced ledger entry posted.', 'success');
      } catch (error) {
        s.postingError(error.message);
      } finally {
        s.postingBusy(false);
      }
    };

    s.loadAccounts();
  }
  return VM;
});
