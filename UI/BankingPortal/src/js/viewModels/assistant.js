define(['knockout', 'appController'], function (ko, app) {
  'use strict';

  function VM() {
    const self = this;
    self.message = ko.observable('Prepare a customer 360 briefing');
    self.customerId = ko.observable((app.activeCustomer() || {}).customerId || '');
    self.transactionId = ko.observable('');
    self.accountId = ko.observable(app.activeAccountId() || '');
    self.module = ko.pureComputed(() => app.assistantContext() || 'platform');
    self.history = ko.observableArray([]);
    self.loading = ko.observable(false);
    self.error = ko.observable('');
    self.response = ko.observable(null);
    self.isStaff = ko.pureComputed(() => ['ADMIN', 'EMPLOYEE'].includes(app.session.role()));
    self.isAdmin = ko.pureComputed(() => app.session.role() === 'ADMIN');
    self.hasResponse = ko.pureComputed(() => !!self.response());
    self.employeeId = ko.observable('');
    self.employeeStatus = ko.observable('ACTIVE');
    self.employeeAction = ko.observable('');
    self.riskFinding = ko.observable(null);
    self.fraudFinding = ko.observable(null);
    self.alerts = ko.observableArray([]);

    const bankingTerms = [
      'account', 'balance', 'bank', 'banking', 'beneficiary', 'card', 'cash', 'credit', 'customer',
      'deposit', 'dispute', 'emi', 'fixed deposit', 'fraud', 'fund', 'interest', 'kyc', 'ledger',
      'loan', 'money', 'nominee', 'overdraft', 'password', 'payment', 'pin', 'product', 'rate',
      'statement', 'transfer', 'transaction', 'upi', 'withdraw', 'withdrawal'
    ];

    const isBankingQuestion = (message) => bankingTerms.some((term) => message.toLowerCase().includes(term));
    const requestError = (error, fallback) => error && error.status === 403
      ? 'Your current sign-in is not authorized for this feature. Sign out, sign in again, and retry as an employee or admin.'
      : (error.message || fallback);

    self.useCustomerBriefing = () => {
      self.message('Prepare a customer 360 briefing');
      self.transactionId('');
    };
    self.useCustomerLookup = () => {
      self.message('What is the status for customer Rishabh Singh?');
      self.customerId('');
      self.transactionId('');
      self.accountId('');
    };
    self.useTransactionReview = () => {
      self.message('Review this transaction and explain the result');
      self.customerId('');
    };
    self.useProductRecommendation = () => {
      if (!self.customerId()) {
        self.error('Enter a Customer ID to receive recommendations based on that customer’s balance and transaction history.');
        return;
      }
      self.message('Recommend suitable active banking products');
      self.transactionId('');
      self.accountId('');
      self.send();
    };
    self.useAccountOverview = () => {
      self.message('Show the account overview and balance');
      self.customerId('');
      self.transactionId('');
    };
    self.usePolicyHelp = () => {
      self.message('What policy controls apply to this operation?');
      self.customerId('');
      self.transactionId('');
      self.accountId('');
    };
    self.changeEmployeeStatus = async () => {
      const employeeId = self.employeeId().trim();
      if (!employeeId) { self.error('Enter the employee User ID before changing a status.'); return; }
      self.employeeAction('Updating employee status…');
      self.error('');
      try {
        const employee = await app.services.users.status(employeeId, self.employeeStatus());
        self.employeeAction(`${employee.username} is now ${employee.status}.`);
        self.alerts.unshift({ level: 'success', title: 'Employee status updated', message: `${employee.username} is now ${employee.status}.` });
      } catch (error) { self.error(requestError(error, 'Unable to update the employee status.')); }
    };
    self.reviewAccountRisk = async () => {
      const accountReference = self.accountId().trim();
      if (!accountReference) { self.error('Enter an Account ID or 12-digit account number to run a risk review.'); return; }
      self.loading(true); self.error(''); self.riskFinding(null);
      try {
        const accountRequest = /^\d{12}$/.test(accountReference)
          ? app.services.accounts.number(accountReference)
          : app.services.accounts.get(accountReference);
        const account = await accountRequest;
        const resolvedAccount = Array.isArray(account) ? account[0] : account;
        if (!resolvedAccount) throw new Error('No account matched the supplied account number.');
        const accountId = resolvedAccount.accountId;
        const [accountDebits, accountCredits] = await Promise.all([
          app.services.transactions.find('debitAccountId', accountId),
          app.services.transactions.find('creditAccountId', accountId)
        ]);
        const transactions = [...accountDebits, ...accountCredits];
        const failed = transactions.filter((item) => item.transactionStatus === 'FAILED').length;
        const highValue = transactions.filter((item) => Number(item.amount) >= 100000).length;
        const issues = [];
        if (resolvedAccount.status !== 'ACTIVE') issues.push(`Account status is ${resolvedAccount.status}`);
        if (Number(resolvedAccount.availableBalance) < 0) issues.push('Available balance is negative');
        if (failed) issues.push(`${failed} failed transaction(s)`);
        if (highValue) issues.push(`${highValue} high-value transaction(s)`);
        const level = issues.some((issue) => issue.includes('negative') || issue.includes('FROZEN')) ? 'HIGH' : issues.length ? 'MEDIUM' : 'LOW';
        const finding = { level, accountNumber: resolvedAccount.accountNumber, balance: `${resolvedAccount.availableBalance} ${resolvedAccount.currencyCode}`, transactionCount: transactions.length, issues: issues.length ? issues : ['No risk indicators detected from account status and transaction history.'] };
        self.riskFinding(finding);
        if (level !== 'LOW') self.alerts.unshift({ level: level.toLowerCase(), title: `Account risk: ${level}`, message: `Account ${resolvedAccount.accountNumber} requires review.` });
      } catch (error) { self.error(requestError(error, 'Unable to complete the account risk review.')); }
      finally { self.loading(false); }
    };
    self.inspectFraud = async () => {
      const transactionId = self.transactionId().trim();
      if (!transactionId) { self.error('Enter a Transaction ID to inspect for fraud indicators.'); return; }
      self.loading(true); self.error(''); self.fraudFinding(null);
      try {
        const transaction = await app.services.transactions.get(transactionId);
        const indicators = [];
        if (transaction.transactionStatus === 'FAILED') indicators.push(`Transaction failed: ${transaction.failureReason || 'reason not recorded'}`);
        if (Number(transaction.amount) >= 100000) indicators.push('High-value transaction');
        if (transaction.externalBeneficiary) indicators.push('External beneficiary involved');
        if (transaction.failureCode || /fraud|suspicious|urgent/i.test(transaction.description || '')) indicators.push('Transaction metadata requires manual review');
        const level = indicators.length >= 3 ? 'HIGH' : indicators.length ? 'MEDIUM' : 'LOW';
        self.fraudFinding({ level, reference: transaction.transactionRef, amount: `${transaction.amount} ${transaction.currencyCode}`, indicators: indicators.length ? indicators : ['No fraud indicators detected by the current rules.'] });
        if (level !== 'LOW') self.alerts.unshift({ level: level.toLowerCase(), title: `Fraud review: ${level}`, message: `Transaction ${transaction.transactionRef} needs employee review.` });
      } catch (error) { self.error(requestError(error, 'Unable to inspect the transaction.')); }
      finally { self.loading(false); }
    };
    self.send = async () => {
      const message = self.message().trim();
      if (!message) {
        self.error('Enter a question for the assistant.');
        return;
      }
      if (!isBankingQuestion(message) && !self.customerId()) {
        const response = {
          intent: 'OUT_OF_SCOPE',
          answer: 'I can help only with banking questions, or customer-specific questions when a Customer ID is provided.',
          evidence: [],
          nextSteps: ['Please ask a banking-related question or provide a Customer ID for a customer question.'],
          recommendations: [],
          policy: { decision: 'RESTRICTED', rationale: 'This assistant is limited to banking support.' }
        };
        self.error('');
        self.response(response);
        self.history.unshift({ message, intent: response.intent, answer: response.answer, customerProfile: response.customerProfile });
        return;
      }
      self.loading(true);
      self.error('');
      self.response(null);
      try {
        const response = await app.services.assistant.chat(message, self.customerId(), self.transactionId().trim(), self.accountId().trim(), self.module());
        self.response(response);
        self.history.unshift({ message, intent: response.intent, answer: response.answer });
      } catch (error) {
        self.error(requestError(error, 'The assistant could not complete the request.'));
      } finally {
        self.loading(false);
      }
    };
  }
  return VM;
});
