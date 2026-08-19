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
    self.chatMessages = ko.observableArray([]);
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
    self.selectedTool = ko.observable(null);
    self.toolSelectionId = ko.observable('');
    self.toolInput = ko.observable('');
    self.toolOptions = ko.observableArray([
      { id: 'customer360', label: 'Customer 360', input: 'cif', prompt: 'Enter the customer CIF', placeholder: 'For example: CIF1234567890', action: 'useCustomerBriefing', staff: true },
      { id: 'transactionReview', label: 'Review transaction', input: 'transaction', prompt: 'Enter the transaction ID', placeholder: 'Transaction ID', action: 'useTransactionReview', staff: true },
      { id: 'products', label: 'Recommend products', input: 'cif', prompt: 'Enter the customer CIF', placeholder: 'For example: CIF1234567890', action: 'useProductRecommendation' },
      { id: 'accountOverview', label: 'Account overview', input: 'account', prompt: 'Enter the account ID or 12-digit account number', placeholder: 'Account ID or 12-digit number', action: 'useAccountOverview', staff: true },
      { id: 'accountRisk', label: 'Account risk review', input: 'account', prompt: 'Enter the account ID or 12-digit account number', placeholder: 'Account ID or 12-digit number', action: 'reviewAccountRisk', staff: true },
      { id: 'fraud', label: 'Inspect fraud', input: 'transaction', prompt: 'Enter the transaction ID', placeholder: 'Transaction ID', action: 'inspectFraud', staff: true },
      { id: 'onboarding', label: 'Onboard new customer', input: null, action: 'startOnboarding', staff: true }
    ]);
    self.availableToolOptions = ko.pureComputed(() => self.toolOptions().filter((tool) => !tool.staff || self.isStaff()));
    self.toolNeedsInput = ko.pureComputed(() => !!(self.selectedTool() && self.selectedTool().input));
    self.composerPlaceholder = ko.pureComputed(() => {
      const tool = self.selectedTool();
      return tool && tool.input ? tool.placeholder : 'Type your message…';
    });
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
      { key: 'dob', prompt: 'What is the date of birth? Use YYYY-MM-DD.', valid: (value) => isAdultDateOfBirth(value) || 'The customer must be at least 18 years old. Enter a valid date in YYYY-MM-DD format.' },
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
      addChatMessage(role === 'employee' ? 'employee' : 'assistant', text);
      window.setTimeout(() => {
        const transcript = document.querySelector('.mb-chat-onboarding .mb-chat-transcript');
        if (transcript) transcript.scrollTop = transcript.scrollHeight;
      }, 0);
    };
    const askOnboardingQuestion = () => {
      const question = onboardingQuestions[self.onboardingStep()];
      if (question) addOnboardingMessage('assistant', question.prompt);
      else addOnboardingMessage('assistant', 'All required details are collected. Type “Create customer” to save the record and start KYC.');
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
    function isAdultDateOfBirth(value) {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
      const [year, month, day] = value.split('-').map(Number);
      const dob = new Date(year, month - 1, day);
      if (Number.isNaN(dob.getTime()) || dob.getFullYear() !== year || dob.getMonth() !== month - 1 || dob.getDate() !== day) return false;
      const today = new Date();
      const adultDate = new Date(dob.getFullYear() + 18, dob.getMonth(), dob.getDate());
      return adultDate <= new Date(today.getFullYear(), today.getMonth(), today.getDate());
    }
    const isNotFound = (error) => error && error.status === 404;
    const ensureContactIsAvailable = async (field, value) => {
      try {
        await (field === 'phone' ? app.services.customers.byPhone(value) : app.services.customers.byEmail(value));
        return `${field === 'phone' ? 'Phone number' : 'Email address'} already exists. Enter a different ${field === 'phone' ? 'mobile number' : 'email address'}.`;
      } catch (error) {
        if (isNotFound(error)) return true;
        throw error;
      }
    };
    const lookupPincode = async (pincode) => {
      let response;
      try {
        response = await fetch(`https://api.postalpincode.in/pincode/${encodeURIComponent(pincode)}`);
      } catch (_) {
        throw new Error('Unable to validate the PIN code right now. Check the connection and try again.');
      }
      if (!response.ok) throw new Error('Unable to validate the PIN code right now. Please try again.');
      const result = await response.json();
      const offices = result && result[0] && result[0].PostOffice;
      if (!Array.isArray(offices) || !offices.length) return null;
      const primary = offices[0];
      const names = offices.flatMap((office) => [office.Name, office.District]).filter(Boolean);
      return { city: primary.District || primary.Name, state: primary.State, names };
    };
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
    const scrollAssistantChat = () => {
      const moveToLatest = () => {
        const thread = document.querySelector('.mb-assistant-message-list');
        if (!thread) return;
        thread.scrollTop = thread.scrollHeight;
      };
      window.requestAnimationFrame(() => window.requestAnimationFrame(moveToLatest));
      window.setTimeout(moveToLatest, 120);
    };
    const addChatMessage = (role, text, customer360, transactionReview, productOptions, accountOverview, riskFinding, fraudFinding) => {
      self.chatMessages.push({ role, text, customer360: customer360 || null, transactionReview: transactionReview || null, productOptions: productOptions || null, accountOverview: accountOverview || null, riskFinding: riskFinding || null, fraudFinding: fraudFinding || null });
      scrollAssistantChat();
    };
    self.response.subscribe((response) => {
      if (!response) return;
      if (response.customer360) {
        addChatMessage('assistant', 'Here is the customer profile and account summary.', response.customer360);
      } else if (response.transactionReview) {
        addChatMessage('assistant', 'Here is the transaction review.', null, response.transactionReview);
      } else if (response.productOptions) {
        addChatMessage('assistant', 'Here are the available product options.', null, null, response.productOptions);
      } else if (response.accountOverview) {
        addChatMessage('assistant', 'Here is the account overview.', null, null, null, response.accountOverview);
      } else if (response.answer) addChatMessage('assistant', response.answer);
    });
    self.loading.subscribe(() => scrollAssistantChat());

    const retainContext = (kind) => {
      if (kind !== 'cif') self.customerCif('');
      if (kind !== 'transaction') self.transactionId('');
      if (kind !== 'account') self.accountId('');
    };
    self.selectAssistantTool = (tool) => {
      self.selectedTool(tool);
      self.toolSelectionId(tool.id);
      self.toolInput('');
      self.message('');
      self.error('');
      if (tool.input) {
        addChatMessage('assistant', `${tool.label}: ${tool.prompt}`);
      } else {
        self[tool.action]();
      }
    };
    self.toolSelectionChanged = () => {
      const tool = self.toolOptions().find((item) => item.id === self.toolSelectionId());
      if (tool) self.selectAssistantTool(tool);
    };
    self.runSelectedTool = (providedInput) => {
      const tool = self.selectedTool();
      const input = String(providedInput === undefined ? self.toolInput() : providedInput).trim();
      if (!tool) return;
      if (!input) { self.error(tool.prompt); return; }
      if (tool.input === 'cif' && !/^CIF[A-Z0-9]+$/i.test(input)) {
        self.error('Enter a valid Customer CIF, beginning with CIF.');
        addChatMessage('assistant', 'Please enter a valid Customer CIF (for example, CIF1234567890).');
        return;
      }
      if (tool.input === 'cif') self.customerCif(input);
      if (tool.input === 'transaction') self.transactionId(input);
      if (tool.input === 'account') self.accountId(input);
      addChatMessage('employee', `${tool.label}: ${input}`);
      self.selectedTool(null);
      self.toolSelectionId('');
      self.toolInput('');
      self[tool.action]();
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
    self.discardOnboarding = () => {
      self.cancelOnboarding();
      self.onboardingStep(0);
      self.onboardingReply('');
      self.onboardingMessages([]);
      self.onboardedCustomer(null);
      Object.keys(self.onboarding).forEach((key) => self.onboarding[key](''));
    };
    self.captureOnboardingReply = async (reply) => {
      const question = onboardingQuestions[self.onboardingStep()];
      if (!question) return;
      const value = reply.trim();
      addOnboardingMessage('employee', value);
      if (!value) { addOnboardingMessage('assistant', `Please provide a value. ${question.prompt}`); return; }
      const result = question.valid(value);
      if (result !== true) { addOnboardingMessage('assistant', `${result} ${question.prompt}`); return; }
      self.onboardingBusy(true);
      try {
        if (question.key === 'phone' || question.key === 'email') {
          const availability = await ensureContactIsAvailable(question.key, value);
          if (availability !== true) { addOnboardingMessage('assistant', `${availability} ${question.prompt}`); return; }
        }

        if (question.key === 'pincode') {
          const location = await lookupPincode(value);
          if (!location) {
            addOnboardingMessage('assistant', `This PIN code could not be mapped to an Indian city. ${question.prompt}`);
            return;
          }
          const enteredCity = String(self.onboarding.city()).trim().toLocaleLowerCase('en-IN');
          const validCities = location.names.map((name) => String(name).trim().toLocaleLowerCase('en-IN'));
          if (!validCities.includes(enteredCity)) {
            addOnboardingMessage('assistant', `PIN code ${value} belongs to ${location.city}, ${location.state}, not ${self.onboarding.city()}. Enter a PIN code for the stated city.`);
            return;
          }
          self.onboarding.city(location.city);
          self.onboarding.state(location.state);
          addOnboardingMessage('assistant', `PIN code verified. City and state are mapped to ${location.city}, ${location.state}.`);
        }

        self.onboarding[question.key](question.transform ? question.transform(value) : value);
        self.onboardingStep(self.onboardingStep() + 1);
        askOnboardingQuestion();
      } catch (error) {
        addOnboardingMessage('assistant', `${requestError(error, 'Unable to validate this detail. Please try again.')} ${question.prompt}`);
      } finally {
        self.onboardingBusy(false);
      }
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
      if (!isAdultDateOfBirth(value('dob'))) { self.onboardingError('Customer must be at least 18 years old.'); return; }
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
        addChatMessage('assistant', `Customer ${customer.firstName} ${customer.lastName} was created with CIF ${customer.cifNo}. KYC is pending document upload and verification.`);
        self.onboardingActive(false);
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
        addChatMessage('assistant', 'Here is the account risk assessment.', null, null, null, null, finding);
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
        const finding = { level, reference: transaction.transactionRef, amount: `${transaction.amount} ${transaction.currencyCode}`, indicators: indicators.length ? indicators : ['No fraud indicators detected by the current rules.'] };
        self.fraudFinding(finding);
        addChatMessage('assistant', 'Here is the fraud inspection result.', null, null, null, null, null, finding);
        self.response(null);
        if (level !== 'LOW') self.alerts.unshift({ level: level.toLowerCase(), title: `Fraud review: ${level}`, message: `Transaction ${transaction.transactionRef} needs employee review.` });
      } catch (error) { self.error(requestError(error, 'Unable to inspect the transaction.')); }
      finally { self.loading(false); }
    };
    self.messageKeypress = (_, event) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        self.send();
        return false;
      }
      return true;
    };
    self.send = async () => {
      const message = self.message().trim();
      if (!message) {
        self.error('Enter a question for the assistant.');
        return;
      }
      if (self.onboardingActive()) {
        self.message('');
        if (self.onboardingReady()) {
          addOnboardingMessage('employee', message);
          if (/^cancel$/i.test(message)) {
            self.discardOnboarding();
            addChatMessage('assistant', 'Customer onboarding was discarded.');
          } else if (/^(create|create customer|confirm)$/i.test(message)) {
            await self.submitOnboarding();
          } else {
            addOnboardingMessage('assistant', 'All details are collected. Type “Create customer” to create the record and start KYC, or type “Cancel” to discard it.');
          }
          return;
        }
        self.sendOnboardingReply(message);
        return;
      }
      if (self.selectedTool() && self.selectedTool().input) {
        self.runSelectedTool(message);
        self.message('');
        return;
      }
      addChatMessage('employee', message);
      self.message('');
      if (self.isStaff() && /\b(onboard|onboarding|create)\b.*\b(customer|client)\b|\b(customer|client)\b.*\b(onboard|onboarding)\b/i.test(message)) {
        self.startOnboarding();
        return;
      }
      if (!isBankingQuestion(message)) {
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
      scrollAssistantChat();
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
