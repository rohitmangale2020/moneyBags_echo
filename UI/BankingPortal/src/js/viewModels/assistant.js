define(['knockout', 'appController'], function (ko, app) {
  'use strict';

  function VM() {
    const self = this;
    self.message = ko.observable('Prepare a customer 360 briefing');
    self.customerId = ko.observable((app.activeCustomer() || {}).customerId || '');
    self.customerCif = ko.observable((app.activeCustomer() || {}).cifNo || '');
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
    self.statusChangeConfirmationOpen = ko.observable(false);
    self.riskFinding = ko.observable(null);
    self.fraudFinding = ko.observable(null);
    self.alerts = ko.observableArray([]);
    self.onboardingActive = ko.observable(false);
    self.onboardingBusy = ko.observable(false);
    self.onboardingReply = ko.observable('');
    self.onboardingError = ko.observable('');
    self.onboardedCustomer = ko.observable(null);
    self.onboardingPrompt = ko.observable('I’ll create the customer profile and residential address, then start a pending KYC assessment. Complete document upload and verification in the onboarding workspace.');
    self.onboardingMessages = ko.observableArray([]);
    self.onboardingStep = ko.observable(0);
    self.onboarding = {
      firstName: ko.observable(''), lastName: ko.observable(''), dob: ko.observable(''), gender: ko.observable(''),
      phone: ko.observable(''), email: ko.observable(''), occupation: ko.observable(''), addressType: ko.observable(''),
      line1: ko.observable(''), line2: ko.observable(''), city: ko.observable(''), state: ko.observable(''), pincode: ko.observable('')
    };
    const onboardingQuestions = [
      { key: 'firstName', prompt: 'What is the customer’s first name?', valid: (value) => value.length <= 100 || 'First name must be 100 characters or fewer.' },
      { key: 'lastName', prompt: 'What is the customer’s last name?', valid: (value) => value.length <= 100 || 'Last name must be 100 characters or fewer.' },
      { key: 'dob', prompt: 'What is the date of birth? Use YYYY-MM-DD.', valid: (value) => /^\d{4}-\d{2}-\d{2}$/.test(value) && new Date(`${value}T00:00:00`) < new Date() ? true : 'Enter a date of birth in YYYY-MM-DD format that is in the past.' },
      { key: 'gender', prompt: 'What is the gender? Reply Male, Female, or Other.', valid: (value) => ['MALE', 'FEMALE', 'OTHER'].includes(value.toUpperCase()) || 'Reply Male, Female, or Other.', transform: (value) => value.toUpperCase() },
      { key: 'phone', prompt: 'What is the 10-digit Indian mobile number?', valid: (value) => /^[6-9]\d{9}$/.test(value) || 'Enter a valid 10-digit Indian mobile number.' },
      { key: 'email', prompt: 'What is the email address?', valid: (value) => /^\S+@\S+\.\S+$/.test(value) || 'Enter a valid email address.' },
      { key: 'occupation', prompt: 'What is the customer’s occupation?', valid: (value) => value.length <= 100 || 'Occupation must be 100 characters or fewer.' },
      { key: 'addressType', prompt: 'Is this the Current, Permanent, or Office address?', valid: (value) => ['CURRENT', 'PERMANENT', 'OFFICE'].includes(value.toUpperCase()) || 'Reply Current, Permanent, or Office.', transform: (value) => value.toUpperCase() },
      { key: 'line1', prompt: 'What is address line 1?', valid: (value) => value.length <= 250 || 'Address line 1 must be 250 characters or fewer.' },
      { key: 'city', prompt: 'What is the city or district?', valid: (value) => value.length <= 100 || 'City must be 100 characters or fewer.' },
      { key: 'state', prompt: 'What is the state?', valid: (value) => value.length <= 100 || 'State must be 100 characters or fewer.' },
      { key: 'pincode', prompt: 'What is the six-digit PIN code?', valid: (value) => /^[1-9]\d{5}$/.test(value) || 'Enter a valid six-digit PIN code.' }
    ];
    self.onboardingReady = ko.pureComputed(() => self.onboardingActive() && self.onboardingStep() >= onboardingQuestions.length);
    self.onboardingSummary = ko.pureComputed(() => {
      const value = (key) => String(self.onboarding[key]()).trim();
      return `${value('firstName')} ${value('lastName')} · ${value('phone')} · ${value('city')}, ${value('state')} ${value('pincode')}`;
    });
    const addOnboardingMessage = (role, text) => {
      self.onboardingMessages.push({ role, text });
      window.setTimeout(() => {
        const transcript = document.querySelector('.mb-chat-onboarding .mb-chat-transcript');
        if (transcript) transcript.scrollTop = transcript.scrollHeight;
      }, 0);
    };
    const askOnboardingQuestion = () => {
      const question = onboardingQuestions[self.onboardingStep()];
      if (question) addOnboardingMessage('assistant', question.prompt);
      else addOnboardingMessage('assistant', 'All required details are collected. Review the summary, then select “Create customer and start KYC”.');
    };

    const bankingTerms = [
      'account', 'balance', 'bank', 'banking', 'beneficiary', 'card', 'cash', 'credit', 'customer',
      'deposit', 'dispute', 'emi', 'fixed deposit', 'fraud', 'fund', 'interest', 'kyc', 'ledger',
      'loan', 'money', 'nominee', 'overdraft', 'password', 'payment', 'pin', 'product', 'rate',
      'statement', 'transfer', 'transaction', 'upi', 'withdraw', 'withdrawal'
    ];

    const isBankingQuestion = (message) => bankingTerms.some((term) => message.toLowerCase().includes(term));
    const rows = (value) => Array.isArray(value) ? value : (value && (value.content || value.items || value.data)) || [];
    const currency = (amount, code) => `${new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(Number(amount || 0))} ${code || 'INR'}`;
    const requestError = (error, fallback) => {
      if (error && error.status === 403) {
        const detail = error.message && error.message !== 'Request failed (403).' ? ` ${error.message}` : '';
        return `Access was denied by ${error.path || 'the server'} (HTTP 403).${detail}`;
      }
      if (error && error.status === 401 && error.path === '/auth/gpt-oss/chat') {
        return 'The GPT-OSS assistant endpoint rejected the request. Your banking session is still active; check the assistant service configuration.';
      }
      if (error && error.status === 503 && error.path === '/auth/gpt-oss/chat') {
        return 'GPT-OSS is unavailable. Start the configured OpenAI-compatible runtime and verify GPT_OSS_BASE_URL and GPT_OSS_MODEL.';
      }
      return (error && error.message) || fallback;
    };

    const retainContext = (kind) => {
      if (kind !== 'cif') self.customerCif('');
      if (kind !== 'transaction') self.transactionId('');
      if (kind !== 'account') self.accountId('');
    };
    self.useCustomerBriefing = () => {
      retainContext('cif');
      self.message('Prepare a customer 360 briefing');
      if (self.customerCif().trim()) self.prepareCustomer360();
    };
    self.prepareCustomer360 = async () => {
      const cif = self.customerCif().trim();
      if (!cif) {
        self.error('Enter a Customer CIF to prepare a factual Customer 360 summary.');
        return;
      }
      self.loading(true); self.error(''); self.response(null);
      try {
        const customer = await app.services.customers.byCif(cif);
        const [accountsResult, kycResult] = await Promise.allSettled([
          app.services.accounts.customer(customer.customerId),
          app.services.customers.kyc(customer.customerId),
        ]);
        const accounts = accountsResult.status === 'fulfilled' ? rows(accountsResult.value) : [];
        const kyc = kycResult.status === 'fulfilled' ? kycResult.value : null;
        const total = accounts.reduce((sum, account) => sum + Number(account.availableBalance ?? account.balance ?? 0), 0);
        const accountLines = accounts.length
          ? accounts.slice(0, 4).map((account) => `- ${account.accountType || 'Account'} ending ${String(account.accountNumber || '').slice(-4)}: ${currency(account.availableBalance ?? account.balance, account.currencyCode)}`).join('\n')
          : '- No accounts are available for this customer.';
        const answer = [
          `Customer 360 summary for ${customer.firstName} ${customer.lastName || ''} (CIF ${customer.cifNo})`, '',
          'Profile',
          `- Status: ${customer.status || 'Not recorded'}`,
          `- Contact: ${customer.phone || 'Not recorded'}${customer.email ? ` · ${customer.email}` : ''}`,
          `- Occupation: ${customer.occupation || 'Not recorded'}`, '',
          'Accounts',
          `- ${accounts.length} account${accounts.length === 1 ? '' : 's'}; total available balance: ${currency(total, accounts[0] && accounts[0].currencyCode)}`,
          accountLines, '', 'Compliance',
          `- KYC status: ${(kyc && kyc.kycStatus) || 'Not available'}`,
          `- Risk level: ${(kyc && kyc.riskLevel) || 'Not available'}`,
        ].join('\n');
        const response = {
          intent: 'CUSTOMER_360', answer, evidence: [], recommendations: [],
          customer360: {
            fullName: `${customer.firstName} ${customer.lastName || ''}`.trim(), cifNo: customer.cifNo,
            status: customer.status || 'NOT_RECORDED', phone: customer.phone || 'Not recorded',
            email: customer.email || 'Not recorded', occupation: customer.occupation || 'Not recorded',
            accounts: accounts.map((account) => Object.assign({}, account, {
              displayType: account.accountType || account.type || 'Account',
              displayNumber: String(account.accountNumber || account.number || account.accountId || 'Not recorded'),
              displayBalance: currency(account.availableBalance ?? account.balance, account.currencyCode)
            })),
            accountCount: accounts.length, totalBalance: currency(total, accounts[0] && accounts[0].currencyCode),
            kycStatus: (kyc && kyc.kycStatus) || 'NOT_AVAILABLE', riskLevel: (kyc && kyc.riskLevel) || 'NOT_AVAILABLE'
          },
          nextSteps: [kyc && String(kyc.kycStatus).toUpperCase() !== 'VERIFIED'
            ? 'Complete or verify KYC before enabling restricted services.'
            : 'Review recent account activity and discuss relevant approved products.'],
          policy: { decision: 'FACTUAL_SUMMARY', rationale: 'Built from approved customer, account, and KYC records for the entered CIF.' },
        };
        self.response(response);
        self.history.unshift({ message: `Customer 360 for CIF ${customer.cifNo}`, intent: response.intent, answer: response.answer });
      } catch (error) {
        self.error(requestError(error, 'Unable to find the customer or prepare the Customer 360 summary for this CIF.'));
      } finally { self.loading(false); }
    };
    const publish = (intent, message, answer, nextSteps = [], details = {}) => {
      const response = { intent, answer, evidence: [], recommendations: [], nextSteps,
        policy: { decision: 'FACTUAL_SUMMARY', rationale: 'Built from approved MoneyBags records.' }, ...details };
      self.response(response);
      self.history.unshift({ message, intent, answer });
    };
    const resolveAccount = async (reference) => {
      const result = /^\d{12}$/.test(reference)
        ? await app.services.accounts.number(reference)
        : await app.services.accounts.get(reference);
      return rows(result)[0] || result;
    };
    self.useCustomerLookup = () => { retainContext('cif'); self.prepareCustomer360(); };
    self.useTransactionReview = async () => {
      const transactionId = self.transactionId().trim();
      retainContext('transaction');
      if (!transactionId) { self.error('Enter a Transaction ID to review it.'); return; }
      self.loading(true); self.error(''); self.response(null);
      try {
        const tx = await app.services.transactions.get(transactionId);
        publish('TRANSACTION_REVIEW', `Transaction review for ${tx.transactionRef || tx.transactionId}`,
          [`Transaction ${tx.transactionRef || tx.transactionId}`, '', 'Details',
            `- Status: ${tx.transactionStatus || 'Not recorded'}`,
            `- Type: ${tx.transactionType || 'Not recorded'}`,
            `- Amount: ${currency(tx.amount, tx.currencyCode)}`,
            `- Initiated: ${tx.initiatedAt || 'Not recorded'}`,
            '', 'Review',
            `- Description: ${tx.description || 'Not recorded'}`,
            `- Failure reason: ${tx.failureReason || 'None recorded'}`].join('\n'),
          [tx.transactionStatus === 'FAILED' ? 'Review the failure reason and retry only through the approved workflow.' : 'No additional action is required unless the customer disputes this transaction.'],
          { transactionReview: {
            reference: tx.transactionRef || tx.transactionId || 'Not recorded', status: tx.transactionStatus || 'NOT_RECORDED',
            type: tx.transactionType || 'Transaction', amount: currency(tx.amount, tx.currencyCode),
            initiatedAt: tx.initiatedAt ? String(tx.initiatedAt).replace('T', ' ').slice(0, 16) : 'Not recorded',
            description: tx.description || 'No description recorded', failureReason: tx.failureReason || 'No failure reason recorded'
          }});
      } catch (error) { self.error(requestError(error, 'Unable to retrieve this transaction.')); }
      finally { self.loading(false); }
    };
    self.useProductRecommendation = async () => {
      const cif = self.customerCif().trim();
      retainContext('cif');
      if (!cif) { self.error('Enter a Customer CIF to receive product options.'); return; }
      self.loading(true); self.error(''); self.response(null);
      try {
        const customer = await app.services.customers.byCif(cif);
        const [accountsResult, productsResult] = await Promise.allSettled([app.services.accounts.customer(customer.customerId), app.services.products.list()]);
        const accounts = accountsResult.status === 'fulfilled' ? rows(accountsResult.value) : [];
        const products = productsResult.status === 'fulfilled' ? rows(productsResult.value).filter((product) => String(product.status || 'ACTIVE').toUpperCase() === 'ACTIVE').slice(0, 3) : [];
        publish('PRODUCT_OPTIONS', `Product options for CIF ${customer.cifNo}`,
          [`Product options for ${customer.firstName} ${customer.lastName || ''}`, '', 'Current relationship',
            `- Active accounts found: ${accounts.filter((account) => String(account.status) === 'ACTIVE').length}`,
            `- Available balance across records: ${currency(accounts.reduce((sum, account) => sum + Number(account.availableBalance || 0), 0), accounts[0] && accounts[0].currencyCode)}`,
            '', 'Available products',
            ...(products.length ? products.map((product) => `- ${product.productName || product.name || product.productCode}: review eligibility and fees.`) : ['- No active product catalogue entries are available.'])].join('\n'),
          ['Discuss only products for which the customer is eligible; obtain consent before starting an application.'],
          { productOptions: (() => {
            const totalBalance = accounts.reduce((sum, account) => sum + Number(account.availableBalance || 0), 0);
            const productOptions = {
            fullName: `${customer.firstName} ${customer.lastName || ''}`.trim(), cifNo: customer.cifNo,
            activeAccountCount: accounts.filter((account) => String(account.status).toUpperCase() === 'ACTIVE').length,
            totalBalance: currency(totalBalance, accounts[0] && accounts[0].currencyCode), eligibility: ko.observable(null),
            products: products.map((product) => ({
              name: product.productName || product.name || product.productCode || 'Banking product',
              code: product.productCode || 'PRODUCT', type: product.productType || 'Product',
              minimumBalanceValue: Number(product.minimumBalance || 0),
              minimumBalance: product.minimumBalance === null || product.minimumBalance === undefined ? 'Not specified' : currency(product.minimumBalance, product.currency),
              interestRate: product.interestRate === null || product.interestRate === undefined ? 'Rate on request' : `${product.interestRate}% p.a.`
            }))
            };
            productOptions.reviewEligibility = (product) => {
              const hasRequiredBalance = totalBalance >= product.minimumBalanceValue;
              const hasRelationship = productOptions.activeAccountCount > 0;
              productOptions.eligibility({
                productName: product.name,
                level: hasRequiredBalance && hasRelationship ? 'PRELIMINARY MATCH' : 'REVIEW REQUIRED',
                message: hasRequiredBalance && hasRelationship
                  ? `The available balance meets the displayed minimum balance for ${product.name}. Confirm product-specific rules, KYC, and customer consent before applying.`
                  : `The current relationship does not meet all displayed criteria for ${product.name}. Review the minimum balance, KYC, and product-specific rules with an employee.`,
                checks: [
                  `Available balance: ${productOptions.totalBalance}`,
                  `Required minimum balance: ${product.minimumBalance}`,
                  hasRelationship ? `${productOptions.activeAccountCount} active account record(s) found.` : 'No active account record was found.'
                ]
              });
            };
            return productOptions;
          })() });
      } catch (error) { self.error(requestError(error, 'Unable to prepare product options for this CIF.')); }
      finally { self.loading(false); }
    };
    self.useAccountOverview = async () => {
      const reference = self.accountId().trim();
      retainContext('account');
      if (!reference) { self.error('Enter an Account ID or 12-digit account number.'); return; }
      self.loading(true); self.error(''); self.response(null);
      try {
        const account = await resolveAccount(reference);
        if (!account) throw new Error('No account matched the supplied reference.');
        publish('ACCOUNT_OVERVIEW', `Account overview for ${account.accountNumber || account.accountId}`,
          [`Account overview`, '', 'Account details', `- Account number: ${account.accountNumber || 'Not recorded'}`,
            `- Status: ${account.status || 'Not recorded'}`, `- Ownership: ${account.ownershipType || 'Not recorded'}`,
            `- Available balance: ${currency(account.availableBalance, account.currencyCode)}`, `- Opened: ${account.openedAt || 'Not recorded'}`].join('\n'),
          [String(account.status).toUpperCase() === 'ACTIVE' ? 'Account is active; continue with the requested approved service.' : 'Review account status before performing any service request.'],
          { accountOverview: {
            accountNumber: account.accountNumber || account.accountId || 'Not recorded', status: account.status || 'NOT_RECORDED',
            ownershipType: account.ownershipType || 'Not recorded', balance: currency(account.availableBalance, account.currencyCode),
            currencyCode: account.currencyCode || 'INR', openedAt: account.openedAt ? String(account.openedAt).replace('T', ' ').slice(0, 16) : 'Not recorded',
            accountType: account.accountType || 'Bank account'
          }});
      } catch (error) { self.error(requestError(error, 'Unable to retrieve this account.')); }
      finally { self.loading(false); }
    };
    self.usePolicyHelp = () => {
      const reference = self.transactionId().trim() || self.accountId().trim() || self.customerCif().trim();
      publish('POLICY_HELP', 'Policy help', ['Policy guidance', '', '- Verify the employee role and customer consent before viewing records.', '- Mask sensitive information outside approved screens.', '- Route KYC, account changes, and product applications through their approved workflows.', `- Reference supplied: ${reference || 'None'}`].join('\n'), ['Use the relevant Customer 360, Transaction review, or Account overview action to see factual details.']);
    };
    self.startOnboarding = () => {
      retainContext('none');
      self.onboardingActive(true);
      self.onboardingError('');
      self.onboardedCustomer(null);
      self.onboardingStep(0);
      self.onboardingReply('');
      self.onboardingMessages([]);
      Object.keys(self.onboarding).forEach((key) => self.onboarding[key](''));
      addOnboardingMessage('assistant', 'Let’s onboard a new customer. I’ll collect one detail at a time.');
      askOnboardingQuestion();
      self.history.unshift({ message: 'Start customer onboarding', intent: 'CUSTOMER_ONBOARDING', answer: 'The onboarding chatbot is collecting the customer profile and address.' });
    };
    self.cancelOnboarding = () => {
      self.onboardingActive(false);
      self.onboardingError('');
    };
    self.captureOnboardingReply = (reply) => {
      const question = onboardingQuestions[self.onboardingStep()];
      if (!question) return;
      const value = reply.trim();
      addOnboardingMessage('employee', value);
      if (!value) { addOnboardingMessage('assistant', `Please provide a value. ${question.prompt}`); return; }
      const result = question.valid(value);
      if (result !== true) { addOnboardingMessage('assistant', `${result} ${question.prompt}`); return; }
      self.onboarding[question.key](question.transform ? question.transform(value) : value);
      self.onboardingStep(self.onboardingStep() + 1);
      askOnboardingQuestion();
    };
    self.sendOnboardingReply = (reply) => {
      if (self.onboardingBusy()) return;
      self.captureOnboardingReply(reply === undefined ? self.onboardingReply() : String(reply));
      self.onboardingReply('');
    };
    self.onboardingReplyKeypress = (_, event) => {
      if (event.key === 'Enter' || event.keyCode === 13) {
        event.preventDefault();
        self.sendOnboardingReply(event.target.value);
        event.target.value = '';
        return false;
      }
      return true;
    };
    self.continueOnboarding = () => {
      const customer = self.onboardedCustomer();
      if (!customer) return;
      app.setActiveCustomer(customer);
      sessionStorage.setItem('moneybags.resumeOnboardingCustomerId', String(customer.customerId));
      app.go('onboarding');
    };
    self.submitOnboarding = async () => {
      const data = self.onboarding;
      const value = (key) => String(data[key]()).trim();
      const required = ['firstName', 'lastName', 'dob', 'gender', 'phone', 'email', 'occupation', 'addressType', 'line1', 'city', 'state', 'pincode'];
      const missing = required.find((key) => !value(key));
      if (missing) { self.onboardingError(`Please provide ${missing.replace(/([A-Z])/g, ' $1').toLowerCase()}.`); return; }
      if (!/^[6-9]\d{9}$/.test(value('phone'))) { self.onboardingError('Enter a valid 10-digit Indian mobile number.'); return; }
      if (!/^\S+@\S+\.\S+$/.test(value('email'))) { self.onboardingError('Enter a valid email address.'); return; }
      if (!/^\d{4}-\d{2}-\d{2}$/.test(value('dob')) || new Date(`${value('dob')}T00:00:00`) >= new Date()) { self.onboardingError('Enter a date of birth in the past.'); return; }
      if (!/^[1-9]\d{5}$/.test(value('pincode'))) { self.onboardingError('Enter a valid six-digit PIN code.'); return; }
      self.onboardingBusy(true); self.onboardingError('');
      try {
        const customer = await app.services.customers.create({
          firstName: value('firstName'), lastName: value('lastName'), dob: value('dob'), gender: value('gender'),
          phone: value('phone'), email: value('email'), occupation: value('occupation')
        });
        await app.services.customers.address(customer.customerId, {
          addressType: value('addressType'), line1: value('line1'), line2: value('line2') || null,
          city: value('city'), state: value('state'), country: 'India', pincode: value('pincode')
        });
        await app.services.customers.createKyc(customer.customerId, {
          kycStatus: 'PENDING', kycDate: new Date().toISOString().slice(0, 10), verifiedBy: String(app.session.userId() || ''),
          riskLevel: 'LOW', riskScore: 0, expiryDate: null, remarks: 'Started through Banking Assistant.', updatedBy: String(app.session.userId() || '')
        });
        self.onboardedCustomer(customer);
        app.setActiveCustomer(customer);
        self.history.unshift({ message: 'Create customer onboarding', intent: 'ONBOARDING_STARTED', answer: `Created ${customer.firstName} ${customer.lastName} with CIF ${customer.cifNo}. KYC is pending document upload and verification.` });
        self.onboardingPrompt('Customer profile and address were saved. KYC is pending; continue to upload identity documents and complete verification.');
      } catch (error) {
        self.onboardingError(requestError(error, 'Unable to start customer onboarding. Check the details and try again.'));
      } finally { self.onboardingBusy(false); }
    };
    self.requestEmployeeStatusChange = () => {
      const employeeId = self.employeeId().trim();
      if (!employeeId) { self.error('Enter the employee User ID before changing a status.'); return; }
      self.error('');
      self.statusChangeConfirmationOpen(true);
    };
    self.cancelEmployeeStatusChange = () => self.statusChangeConfirmationOpen(false);
    self.changeEmployeeStatus = async () => {
      const employeeId = self.employeeId().trim();
      if (!employeeId) { self.statusChangeConfirmationOpen(false); self.error('Enter the employee User ID before changing a status.'); return; }
      self.statusChangeConfirmationOpen(false);
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
      retainContext('account');
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
        self.response(null);
        if (level !== 'LOW') self.alerts.unshift({ level: level.toLowerCase(), title: `Account risk: ${level}`, message: `Account ${resolvedAccount.accountNumber} requires review.` });
      } catch (error) { self.error(requestError(error, 'Unable to complete the account risk review.')); }
      finally { self.loading(false); }
    };
    self.inspectFraud = async () => {
      const transactionId = self.transactionId().trim();
      retainContext('transaction');
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
        self.response(null);
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
      if (self.isStaff() && /\b(onboard|onboarding|create)\b.*\b(customer|client)\b|\b(customer|client)\b.*\b(onboard|onboarding)\b/i.test(message)) {
        self.startOnboarding();
        return;
      }
      if (!isBankingQuestion(message) && !self.customerCif()) {
        const response = {
          intent: 'OUT_OF_SCOPE',
          answer: 'I can help only with banking questions, or customer-specific questions when a Customer CIF is provided.',
          evidence: [],
          nextSteps: ['Please ask a banking-related question or provide a Customer CIF for a customer question.'],
          recommendations: [],
          policy: { decision: 'RESTRICTED', rationale: 'This assistant is limited to banking support.' }
        };
        self.error('');
        self.response(response);
        self.history.unshift({ message, intent: response.intent, answer: response.answer, customerProfile: response.customerProfile });
        return;
      }
      if (/customer\s*360|360\s*(briefing|summary)|prepare.*customer/i.test(message)) {
        await self.prepareCustomer360();
        return;
      }
      self.loading(true);
      self.error('');
      self.response(null);
      try {
        const response = await app.services.assistant.chat(message, null, self.transactionId().trim(), self.accountId().trim(), self.module());
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
