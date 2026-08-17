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
    let balanceAnimationFrame;
    let balanceAnimationId = 0;
    s.state = u.state([]);
    s.pageSize = 10;
    s.currentPage = ko.observable(0);
    s.totalAccounts = ko.observable(0);
    s.totalPages = ko.observable(0);
    s.lastLoadedAccounts = [];
    s.draggedAccount = ko.observable(null);
    s.movingAccountId = ko.observable(null);
    s.selectedAccount = ko.observable(null);
    s.query = ko.observable('');
    s.searchCustomerId = ko.observable(null);
    s.statusFilter = ko.observable('ALL');
    s.ownershipFilter = ko.observable('ALL');
    s.currencyFilter = ko.observable('ALL');
    s.sortBy = ko.observable('opened-desc');
    s.products = ko.observableArray([]);
    s.selectedProduct = ko.pureComputed(() => s.products().find((product) => String(product.productId) === String(s.form.productId())) || null);
    s.productLabel = ko.pureComputed(() => {
      const product = s.selectedProduct();
      return product ? `${product.productName} · ${product.productCode}` : s.form.productId();
    });
    s.selectedProductMinimum = ko.pureComputed(() => Number(s.selectedProduct()?.minimumBalance || 0));
    s.selectedProductRule = ko.pureComputed(() => {
      const product = s.selectedProduct();
      if (!product) return '';
      const minimum = s.selectedProductMinimum();
      return minimum > 0
        ? `Minimum opening balance for ${product.productName}: ${u.money(minimum, product.currency || 'INR')}`
        : 'No minimum opening balance for this product.';
    });
    s.editingId = ko.observable(null);
    s.closingAccount = ko.observable(false);
    s.closeDateMin = new Date().toISOString().slice(0, 10);
    s.error = ko.observable('');
    s.activeCustomer = app.activeCustomer;
    s.hasActiveCustomer = app.hasActiveCustomer;
    s.form = {
      accountNumber: ko.observable(''),
      customerId: ko.observable(''),
      customerCif: ko.observable(''),
      productId: ko.observable(''),
      ownershipType: ko.observable('INDIVIDUAL'),
      availableBalance: ko.observable(0),
      status: ko.observable('ACTIVE'),
      currencyCode: ko.observable('INR'),
      closedAt: ko.observable(null),
    };
    s.money = u.money;
    s.date = u.date;
    s.currencies = ko.pureComputed(() =>
      Array.from(new Set(['INR', 'USD', ...s.state.data()
        .map((account) => account.currencyCode)
        .filter(Boolean)])).sort(),
    );
    s.filteredAccounts = ko.pureComputed(() => {
      const accounts = s.state.data().filter((account) => {
        const customerId = s.searchCustomerId()
          || (s.hasActiveCustomer() ? s.activeCustomer().customerId : null);
        const inCustomerContext = !customerId || String(account.customerId) === String(customerId);
        return inCustomerContext
          && (s.statusFilter() === 'ALL' || account.status === s.statusFilter())
          && (s.ownershipFilter() === 'ALL' || account.ownershipType === s.ownershipFilter())
          && (s.currencyFilter() === 'ALL' || account.currencyCode === s.currencyFilter());
      });
      const sorters = {
        'opened-desc': (a, b) => timestamp(b.openedAt) - timestamp(a.openedAt),
        'opened-asc': (a, b) => timestamp(a.openedAt) - timestamp(b.openedAt),
        'balance-desc': (a, b) => Number(b.availableBalance || 0) - Number(a.availableBalance || 0),
        'balance-asc': (a, b) => Number(a.availableBalance || 0) - Number(b.availableBalance || 0),
        'number-asc': (a, b) => String(a.accountNumber || '').localeCompare(String(b.accountNumber || '')),
        'number-desc': (a, b) => String(b.accountNumber || '').localeCompare(String(a.accountNumber || '')),
      };
      return accounts.slice().sort(sorters[s.sortBy()] || sorters['opened-desc']);
    });
    s.resultSummary = ko.pureComputed(() => {
      const shown = s.filteredAccounts().length;
      const total = s.totalAccounts();
      return shown === total
        ? `${total} account${total === 1 ? '' : 's'}`
        : `${shown} of ${total} accounts`;
    });
    s.statusLanes = ko.pureComputed(() => {
      const statuses = ['ACTIVE', 'INACTIVE', 'DORMANT', 'FROZEN', 'CLOSED'];
      return statuses
        .filter((status) => s.statusFilter() === 'ALL' || s.statusFilter() === status)
        .map((status) => ({ status, accounts: s.filteredAccounts().filter((account) => account.status === status) }));
    });
    s.animateBalances = (accounts) => {
      cancelAnimationFrame(balanceAnimationFrame);
      const animationId = ++balanceAnimationId;
      const reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      if (reduceMotion) {
        accounts.forEach((account) => account.balanceDisplay(Number(account.availableBalance || 0)));
        return;
      }
      const startedAt = performance.now();
      const duration = 1200;
      const stagger = 80;
      const update = (now) => {
        if (animationId !== balanceAnimationId) return;
        let complete = true;
        accounts.forEach((account, index) => {
          const elapsed = Math.max(0, now - startedAt - (index * stagger));
          const progress = Math.min(elapsed / duration, 1);
          const easedProgress = 1 - Math.pow(1 - progress, 3);
          account.balanceDisplay(Number(account.availableBalance || 0) * easedProgress);
          if (progress < 1) complete = false;
        });
        if (!complete) balanceAnimationFrame = requestAnimationFrame(update);
      };
      balanceAnimationFrame = requestAnimationFrame(update);
    };
    s.load = (requestedPage) => s.state.run(async () => {
      const page = Number.isInteger(requestedPage) ? requestedPage : s.currentPage();
      const customerId = s.searchCustomerId()
        || (s.hasActiveCustomer() ? s.activeCustomer().customerId : null);
      const response = await app.services.accounts.list(page, s.pageSize, {
        customerId,
        status: s.statusFilter(),
        ownershipType: s.ownershipFilter(),
        currencyCode: s.currencyFilter(),
      });
      const accounts = u.list(response);
      s.currentPage(Number(response.number || 0));
      s.totalAccounts(Number(response.totalElements === undefined ? accounts.length : response.totalElements));
      s.totalPages(Number(response.totalPages === undefined ? 1 : response.totalPages));
      const [customersResult, productsResult] = await Promise.allSettled([
        app.services.customers.list(),
        app.services.products.list(),
      ]);
      const customers = customersResult.status === 'fulfilled' ? u.list(customersResult.value) : [];
      const products = productsResult.status === 'fulfilled' ? u.list(productsResult.value) : [];
      const customerNames = new Map(customers.map((customer) => [
        String(customer.customerId),
        [customer.firstName, customer.lastName].filter(Boolean).join(' '),
      ]));
      const unresolvedCustomerIds = [...new Set(accounts
        .map((account) => account.customerId)
        .filter((customerId) => customerId && !customerNames.has(String(customerId))))];
      const individualCustomers = await Promise.allSettled(
        unresolvedCustomerIds.map((customerId) => app.services.customers.get(customerId)),
      );
      individualCustomers.forEach((result, index) => {
        if (result.status !== 'fulfilled') return;
        const customer = result.value;
        const name = [customer.firstName, customer.lastName].filter(Boolean).join(' ');
        if (name) customerNames.set(String(unresolvedCustomerIds[index]), name);
      });
      const productNames = new Map(products.map((product) => [String(product.productId), product.productName]));
      const productDetails = new Map(products.map((product) => [String(product.productId), product]));
      const loadedAccounts = accounts.map((account) => Object.assign({}, account, {
        customerName: account.customerName || customerNames.get(String(account.customerId)) || '',
        productName: account.productName || productNames.get(String(account.productId)) || '',
        productTypeCode: account.productTypeCode || productDetails.get(String(account.productId))?.productTypeCode || '',
        productStatus: account.productStatus || productDetails.get(String(account.productId))?.status || '',
        balanceDisplay: ko.observable(0),
      }));
      s.lastLoadedAccounts = loadedAccounts;
      return loadedAccounts;
    }).then((accounts) => {
      if (accounts) s.animateBalances(s.lastLoadedAccounts);
      return accounts;
    }).catch(() => null);
    s.previousPage = () => {
      if (s.currentPage() > 0) s.load(s.currentPage() - 1);
    };
    s.nextPage = () => {
      if (s.currentPage() < s.totalPages() - 1) s.load(s.currentPage() + 1);
    };
    let resettingFilters = false;
    [s.statusFilter, s.ownershipFilter, s.currencyFilter].forEach((filter) => {
      filter.subscribe(() => {
        if (!resettingFilters) s.load(0);
      });
    });
    s.search = () => {
      const searchValue = s.query().trim();
      if (!searchValue) {
        s.searchCustomerId(null);
        return s.load(0);
      }
      return /^CIF/i.test(searchValue)
        ? s.searchByCustomerCif(searchValue)
        : s.searchByAccountNumber(searchValue);
    };
    s.searchByCustomerCif = (cifNo) => {
      return s.state.run(async () => {
        const customer = await app.services.customers.byCif(cifNo);
        s.searchCustomerId(String(customer.customerId));
        return s.load(0);
      }).catch(() => null);
    };
    s.searchByAccountNumber = (accountNumber) => s.state.run(async () => {
      const accounts = u.list(await app.services.accounts.number(accountNumber));
      if (!accounts.length) throw new Error('Account number was not found.');
      const account = accounts[0];
      s.searchCustomerId(account.customerId ? String(account.customerId) : null);
      const [customerResult, productsResult] = await Promise.allSettled([
        account.customerId ? app.services.customers.get(account.customerId) : Promise.resolve(null),
        app.services.products.list(),
      ]);
      const customer = customerResult.status === 'fulfilled' ? customerResult.value : null;
      const products = productsResult.status === 'fulfilled' ? u.list(productsResult.value) : [];
      const product = products.find((item) => String(item.productId) === String(account.productId));
      const customerName = customer
        ? [customer.firstName, customer.lastName].filter(Boolean).join(' ')
        : '';
      const loadedAccount = Object.assign({}, account, {
        customerName: account.customerName || customerName,
        productName: account.productName || product?.productName || '',
        productTypeCode: account.productTypeCode || product?.productTypeCode || '',
        productStatus: account.productStatus || product?.status || '',
        balanceDisplay: ko.observable(0),
      });
      s.currentPage(0);
      s.totalAccounts(1);
      s.totalPages(1);
      s.lastLoadedAccounts = [loadedAccount];
      return [loadedAccount];
    }).then((accounts) => {
      if (accounts) s.animateBalances(s.lastLoadedAccounts);
      return accounts;
    }).catch(() => null);
    s.startCardDrag = (account, event) => {
      s.draggedAccount(account);
      s.selectedAccount(account);
      const transfer = event.dataTransfer;
      if (transfer) {
        transfer.effectAllowed = 'move';
        transfer.setData('text/plain', account.accountId);
      }
      const card = event.currentTarget.closest('.mb-account-kanban-card');
      if (card) card.classList.add('mb-card-dragging');
    };
    s.beginCardDrag = (account, event) => {
      if (event.button !== undefined && event.button !== 0) return;
      if (event.target.closest('button,a,input,select,textarea')) return;
      event.preventDefault();
      s.draggedAccount(account);
      s.selectedAccount(account);
      const card = event.currentTarget.closest('.mb-account-kanban-card');
      if (card) card.classList.add('mb-card-dragging');
      const finishDrag = (finishEvent) => {
        document.removeEventListener('pointerup', finishDrag);
        document.removeEventListener('pointercancel', finishDrag);
        if (card) card.classList.remove('mb-card-dragging');
        const target = document.elementFromPoint(finishEvent.clientX, finishEvent.clientY);
        const lane = target && target.closest('.mb-account-lane');
        const status = lane && lane.dataset.status;
        s.draggedAccount(null);
        if (status) s.moveAccountToStatus(account, status);
      };
      document.addEventListener('pointerup', finishDrag);
      document.addEventListener('pointercancel', finishDrag);
    };
    s.endCardDrag = (_, event) => {
      const card = event.currentTarget.closest('.mb-account-kanban-card');
      if (card) card.classList.remove('mb-card-dragging');
      s.draggedAccount(null);
    };
    s.allowLaneDrop = (_, event) => {
      event.preventDefault();
      if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
    };
    s.moveAccountToStatus = async (account, status) => {
      if (!account || account.status === status || s.movingAccountId()) return;
      const rule = s.statusChangeRule(account, status);
      if (rule) return app.notify(rule, 'warning');
      s.movingAccountId(account.accountId);
      try {
        const updated = await app.services.accounts.update(account.accountId, {
          accountNumber: account.accountNumber,
          customerId: account.customerId,
          productId: account.productId,
          ownershipType: account.ownershipType,
          status,
          currencyCode: account.currencyCode,
          availableBalance: Number(account.availableBalance),
          closedAt: status === 'CLOSED'
            ? account.closedAt || new Date().toISOString().slice(0, 16)
            : null,
        });
        s.state.data(s.state.data().map((item) => item.accountId === account.accountId
          ? Object.assign({}, item, updated, { balanceDisplay: item.balanceDisplay })
          : item));
        app.notify(`${account.accountNumber} moved to ${status}.`);
      } catch (error) {
        app.notify(error.message, 'error');
      } finally {
        s.movingAccountId(null);
        s.selectedAccount(null);
      }
    };
    s.dropInLane = (lane, event) => {
      event.preventDefault();
      const account = s.draggedAccount() || s.selectedAccount();
      s.draggedAccount(null);
      return s.moveAccountToStatus(account, lane.status);
    };
    s.selectCard = (account) => {
      if (!s.movingAccountId()) s.selectedAccount(account);
    };
    s.moveSelectedToLane = (lane) => {
      const account = s.selectedAccount();
      if (account) return s.moveAccountToStatus(account, lane.status);
    };
    s.statusChangeRule = (account, nextStatus) => {
      if (account.status === 'CLOSED') return 'Closed accounts cannot have their status changed.';
      if (account.productStatus && account.productStatus !== 'ACTIVE') {
        return 'This account cannot be changed because its product is retired.';
      }
      const productType = String(account.productTypeCode || '').toUpperCase();
      const transitions = productType === 'FIXED_DEPOSIT'
        ? { ACTIVE: ['CLOSED'], FROZEN: ['ACTIVE', 'CLOSED'], INACTIVE: ['ACTIVE', 'CLOSED'] }
        : productType === 'CREDIT_CARD'
          ? { ACTIVE: ['INACTIVE', 'FROZEN', 'CLOSED'], INACTIVE: ['ACTIVE', 'CLOSED'], FROZEN: ['ACTIVE', 'CLOSED'] }
          : { ACTIVE: ['INACTIVE', 'DORMANT', 'FROZEN', 'CLOSED'], INACTIVE: ['ACTIVE', 'FROZEN', 'CLOSED'], DORMANT: ['ACTIVE', 'FROZEN', 'CLOSED'], FROZEN: ['ACTIVE', 'CLOSED'] };
      if (!transitions[account.status] || !transitions[account.status].includes(nextStatus)) {
        const typeName = productType ? productType.replace(/_/g, ' ').toLowerCase() : 'this product';
        return `This ${typeName} account cannot be moved from ${account.status} to ${nextStatus}.`;
      }
      return '';
    };
    s.clearFilters = () => {
      resettingFilters = true;
      s.query('');
      s.searchCustomerId(null);
      s.statusFilter('ALL');
      s.ownershipFilter('ALL');
      s.currencyFilter('ALL');
      s.sortBy('opened-desc');
      resettingFilters = false;
      s.load(0);
    };
    s.open = async () => {
      s.editingId(null);
      s.error('');
      s.form.accountNumber('');
      s.form.customerId(s.hasActiveCustomer() ? s.activeCustomer().customerId : '');
      s.form.customerCif(s.hasActiveCustomer() ? s.activeCustomer().cifNo : '');
      s.form.productId('');
      s.form.ownershipType('INDIVIDUAL');
      s.form.availableBalance(0);
      s.form.status('ACTIVE');
      s.form.currencyCode('INR');
      s.form.closedAt(null);
      s.closingAccount(false);
      try {
        const products = (await app.services.products.list()).filter((p) => p.status === 'ACTIVE');
        s.products(products);
        s.form.productId(products.length ? String(products[0].productId) : '');
        document.getElementById('accountDialog').open();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.edit = async (x) => {
      s.editingId(x.accountId);
      s.error('');
      s.form.accountNumber(x.accountNumber);
      s.form.customerId(x.customerId);
      s.form.customerCif('');
      s.form.productId(x.productId);
      s.form.ownershipType(x.ownershipType);
      s.form.availableBalance(x.availableBalance);
      s.form.status(x.status);
      s.form.currencyCode(x.currencyCode);
      s.form.closedAt(x.closedAt ? String(x.closedAt).slice(0, 10) : '');
      s.closingAccount(x.status === 'CLOSED');
      try {
        const [productsResult, customerResult] = await Promise.allSettled([
          app.services.products.list(),
          app.services.customers.get(x.customerId),
        ]);
        if (productsResult.status !== 'fulfilled') throw productsResult.reason;
        const products = u.list(productsResult.value);
        s.products(products);
        const selectedProduct = products.find((product) =>
          String(product.productId) === String(x.productId)
          || String(product.productCode) === String(x.productId));
        s.form.productId(String(selectedProduct ? selectedProduct.productId : x.productId));
        if (customerResult.status === 'fulfilled') {
          s.form.customerCif(customerResult.value.cifNo || '');
        }
        document.getElementById('accountDialog').open();
      } catch (e) { app.notify(e.message, 'error'); }
    };
    s.closeAccount = () => {
      s.closingAccount(true);
      s.form.status('CLOSED');
      if (!s.form.closedAt()) s.form.closedAt(s.closeDateMin);
    };
    s.create = async () => {
      const p = s.products().find((x) => String(x.productId) === String(s.form.productId()));
      if ((!p && !s.editingId()) || (s.editingId() && !s.form.customerId()))
        return s.error('Complete all required fields.');
      if (!s.editingId() && !s.form.customerCif().trim()) return s.error('Customer CIF is required.');
      if (Number(s.form.availableBalance()) < 0) return s.error('Available balance cannot be negative.');
      if (!s.editingId() && Number(s.form.availableBalance()) < Number(p.minimumBalance || 0)) {
        return s.error(`Opening balance must be at least ${u.money(p.minimumBalance, p.currency || 'INR')} for ${p.productName}.`);
      }
      if (!/^[A-Za-z]{3}$/.test(s.editingId() ? s.form.currencyCode() : p.currency)) return s.error('Currency must be a three-letter code.');
      try {
        const customerId = s.editingId()
          ? String(s.form.customerId())
          : String((await app.services.customers.byCif(s.form.customerCif().trim())).customerId);
        s.form.customerId(customerId);
        const payload = {
          accountNumber: s.editingId() ? s.form.accountNumber() : null,
          customerId,
          productId: String(p ? p.productId : s.form.productId()),
          ownershipType: s.form.ownershipType(),
          status: s.editingId() ? s.form.status() : 'ACTIVE',
          currencyCode: s.editingId() ? s.form.currencyCode() : p.currency,
          availableBalance: Number(s.form.availableBalance()),
          closedAt: s.closingAccount() && s.form.closedAt()
            ? `${s.form.closedAt()}T00:00:00` : null,
        };
        const savedAccount = s.editingId()
          ? await app.services.accounts.update(s.editingId(), payload)
          : await app.services.accounts.create(payload);
        if (app.setTransactionCustomerId) app.setTransactionCustomerId(payload.customerId);
        if (savedAccount && savedAccount.accountId && app.setActiveAccount) {
          app.setActiveAccount(savedAccount.accountId);
        }
        document.getElementById('accountDialog').close();
        app.notify(s.editingId() ? 'Account updated.' : 'Account opened successfully.');
        await s.load();
      } catch (e) {
        s.error(e.message);
      }
    };
    s.close = () => document.getElementById('accountDialog').close();
    s.load();
  }
  return VM;
});
