define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojdialog',
  'ojs/ojswitch',
], function (ko, app, u) {
  function blank() {
    return {
      productCode: '',
      productName: '',
      productTypeCode: '',
      description: '',
      minimumBalance: 0,
      maximumBalance: null,
      currency: 'INR',
      status: 'ACTIVE',
      rate: { interestRate: 0 },
      term: {
        tenureMonths: null,
        installmentAmount: null,
        installmentFrequency: null,
        lockInPeriod: null,
        maturityInstruction: null,
        prematureWithdrawalAllowed: false,
      },
      fee: { monthlyMaintenanceFee: 0 },
    };
  }
  function filterProducts(products, search, typeCode) {
    const query = (search || '').trim().toLowerCase();
    return products.filter((product) => {
      if (typeCode && product.productTypeCode !== typeCode) return false;
      if (!query) return true;
      return [
        product.productCode,
        product.productName,
        product.productTypeCode,
        product.productTypeName,
        product.description,
      ].some((value) => String(value || '').toLowerCase().includes(query));
    });
  }
  function productTypeOptions(products) {
    const options = new Map();
    products.forEach((product) => {
      options.set(product.productTypeCode, product.productTypeName || product.productTypeCode);
    });
    return Array.from(options, ([code, name]) => ({ code, name }))
      .sort((left, right) => left.name.localeCompare(right.name));
  }
  function VM() {
    const s = this;
    s.isAdmin = ko.pureComputed(() => app.session.role() === 'ADMIN');
    s.isEmployee = ko.pureComputed(() => app.session.role() === 'EMPLOYEE');
    s.state = u.state([]);
    s.types = ko.observableArray([]);
    s.form = ko.observable(blank());
    s.formType = ko.observable('');
    s.editingCode = ko.observable(null);
    s.selected = ko.observable(null);
    s.history = ko.observableArray([]);
    s.activeProducts = ko.pureComputed(() => s.state.data().filter((product) => product.status === 'ACTIVE'));
    s.retiredProducts = ko.pureComputed(() => s.state.data().filter((product) => product.status === 'RETIRED'));
    s.activeSearch = ko.observable('');
    s.activeType = ko.observable('');
    s.activeCompact = ko.observable(false);
    s.retiredSearch = ko.observable('');
    s.retiredType = ko.observable('');
    s.retiredCompact = ko.observable(false);
    s.activeTypeOptions = ko.pureComputed(() => productTypeOptions(s.activeProducts()));
    s.retiredTypeOptions = ko.pureComputed(() => productTypeOptions(s.retiredProducts()));
    s.filteredActiveProducts = ko.pureComputed(() =>
      filterProducts(s.activeProducts(), s.activeSearch(), s.activeType()));
    s.filteredRetiredProducts = ko.pureComputed(() =>
      filterProducts(s.retiredProducts(), s.retiredSearch(), s.retiredType()));
    s.activeFiltersApplied = ko.pureComputed(() => !!s.activeSearch().trim() || !!s.activeType());
    s.retiredFiltersApplied = ko.pureComputed(() => !!s.retiredSearch().trim() || !!s.retiredType());
    s.activeCountLabel = ko.pureComputed(() => s.activeFiltersApplied()
      ? `${s.filteredActiveProducts().length} of ${s.activeProducts().length} active`
      : `${s.activeProducts().length} active`);
    s.retiredCountLabel = ko.pureComputed(() => s.retiredFiltersApplied()
      ? `${s.filteredRetiredProducts().length} of ${s.retiredProducts().length} retired`
      : `${s.retiredProducts().length} retired`);
    s.clearActiveFilters = () => { s.activeSearch(''); s.activeType(''); };
    s.clearRetiredFilters = () => { s.retiredSearch(''); s.retiredType(''); };
    s.toggleActiveCompact = () => s.activeCompact(!s.activeCompact());
    s.toggleRetiredCompact = () => s.retiredCompact(!s.retiredCompact());
    s.typeForm = {
      productTypeCode: ko.observable(''),
      productTypeName: ko.observable(''),
      description: ko.observable(''),
      status: ko.observable('ACTIVE'),
    };
    s.error = ko.observable('');
    s.money = u.money;
    s.productHeading = (product) => product.productCode;
    s.productSubheading = (product) => product.productName;
    s.selectedProductType = ko.pureComputed(() => s.formType());
    s.isFixedDeposit = ko.pureComputed(() => s.selectedProductType() === 'FD');
    s.isRecurringDeposit = ko.pureComputed(() => s.selectedProductType() === 'RD');
    s.isTermDeposit = ko.pureComputed(() => s.isFixedDeposit() || s.isRecurringDeposit());
    s.isCreditCard = ko.pureComputed(() => s.selectedProductType() === 'CREDIT_CARD');
    s.hasProductType = ko.pureComputed(() => !!s.selectedProductType());
    s.isInterestBearing = ko.pureComputed(() => ['SAVINGS', 'SALARY', 'FD', 'RD'].includes(s.selectedProductType()));
    s.formType.subscribe((typeCode) => {
      const product = s.form();
      product.productTypeCode = typeCode;
      if (typeCode !== 'FD' && typeCode !== 'RD') {
        product.term.tenureMonths = null; product.term.lockInPeriod = null;
        product.term.maturityInstruction = null; product.term.prematureWithdrawalAllowed = false;
      }
      if (typeCode !== 'RD') {
        product.term.installmentAmount = null; product.term.installmentFrequency = null;
      }
      if (typeCode === 'CREDIT_CARD') {
        product.minimumBalance = null; product.maximumBalance = null;
      }
      s.form.valueHasMutated();
    });
    s.productIcon = (product) => {
      const icons = {
        SAVINGS: 'mb-product-icon-savings', CURRENT: 'mb-product-icon-current', SALARY: 'mb-product-icon-salary',
        FD: 'mb-product-icon-fixed-deposit', RD: 'mb-product-icon-recurring-deposit', CREDIT_CARD: 'mb-product-icon-credit-card',
      };
      return icons[product.productTypeCode] || 'mb-product-icon-generic';
    };
    s.load = () =>
      s.state
        .run(async () => {
          const r = await Promise.all([
            app.services.products.list(),
            app.services.products.types(),
          ]);
          s.types(r[1]);
          return r[0];
        })
        .catch(() => null);
    s.open = () => {
      s.editingCode(null);
      s.form(blank());
      s.formType('');
      s.error('');
      document.getElementById('productDialog').open();
    };
    s.openType = () => {
      s.error('');
      document.getElementById('productTypeDialog').open();
    };
    s.manage = async (p) => {
      s.error('');
      document.getElementById('productDetailDialog').open();
      try {
        const values = await Promise.all([
          app.services.products.get(p.productCode),
          app.services.products.history(p.productCode),
        ]);
        s.selected(values[0]);
        s.history(values[1]);
      } catch (e) {
        s.error(e.message);
      }
    };
    s.edit = () => {
      if (s.selected().status === 'RETIRED') return app.notify('Retired products cannot be edited.', 'warning');
      const d = ko.toJS(s.selected());
      s.editingCode(d.productCode);
      s.form(Object.assign(blank(), d, {
        rate: Object.assign({ interestRate: 0 }, d.rate || {}),
        term: Object.assign(blank().term, d.term || {}, {
          prematureWithdrawalAllowed: !!(d.term && d.term.prematureWithdrawalAllowed),
        }),
        fee: Object.assign({ monthlyMaintenanceFee: 0 }, d.fee || {}),
      }));
      s.formType(d.productTypeCode);
      document.getElementById('productDialog').open();
    };
    s.close = (id) => document.getElementById(typeof id === 'string' ? id : 'productDialog').close();
    s.closeType = () => document.getElementById('productTypeDialog').close();
    s.saveType = async () => {
      const data = {
        productTypeCode: s.typeForm.productTypeCode(),
        productTypeName: s.typeForm.productTypeName(),
        description: s.typeForm.description(),
        status: s.typeForm.status(),
      };
      if (!data.productTypeCode || !data.productTypeName)
        return s.error('Enter the product type code and name.');
      try {
        await app.services.products.createType(data);
        document.getElementById('productTypeDialog').close();
        app.notify('Product type created.');
        s.load();
      } catch (e) {
        s.error(e.message);
      }
    };
    s.save = async () => {
      const raw = ko.toJS(s.form());
      const d = {
        productCode: raw.productCode, productName: raw.productName, productTypeCode: s.formType(),
        description: raw.description, minimumBalance: raw.minimumBalance, maximumBalance: raw.maximumBalance,
        currency: 'INR', status: raw.status,
        rate: Object.assign({ interestRate: 0 }, raw.rate), term: Object.assign({}, raw.term),
        fee: Object.assign({ monthlyMaintenanceFee: 0 }, raw.fee),
      };
      d.minimumBalance = Number(d.minimumBalance || 0);
      d.maximumBalance = d.maximumBalance ? Number(d.maximumBalance) : null;
      d.rate.interestRate = Number(d.rate.interestRate || 0);
      d.fee.monthlyMaintenanceFee = Number(d.fee.monthlyMaintenanceFee || 0);
      d.term.tenureMonths = d.term.tenureMonths ? Number(d.term.tenureMonths) : null;
      d.term.installmentAmount = d.term.installmentAmount ? Number(d.term.installmentAmount) : null;
      d.term.lockInPeriod = d.term.lockInPeriod ? Number(d.term.lockInPeriod) : null;
      if (!d.productCode || !d.productName || !d.productTypeCode)
        return s.error('Complete all required fields.');
      if (!/^[A-Z]{3}$/.test(d.currency || '')) return s.error('Currency must be a three-letter uppercase code.');
      if ([d.minimumBalance, d.maximumBalance, d.rate.interestRate, d.fee.monthlyMaintenanceFee, d.term.installmentAmount].some((v) => v !== null && v < 0)) return s.error('Balances, rates, fees, and installments cannot be negative.');
      if (d.maximumBalance !== null && d.maximumBalance < d.minimumBalance) return s.error('Maximum balance cannot be lower than minimum balance.');
      try {
        const value = s.editingCode()
          ? await app.services.products.update(s.editingCode(), d)
          : await app.services.products.create(d);
        const wasEditing = !!s.editingCode();
        document.getElementById('productDialog').close();
        if (wasEditing) {
          s.selected(value);
          s.history(await app.services.products.history(value.productCode));
        }
        app.notify(wasEditing ? 'Product updated.' : 'Product created.');
        s.load();
      } catch (e) {
        s.error(e.message);
      }
    };
    s.retire = async (p) => {
      if (!s.isAdmin()) {
        app.notify('Only administrators can retire products.', 'warning');
        return;
      }
      if (!window.confirm(`Retire product ${p.productCode}?`)) return;
      try {
        await app.services.products.retire(p.productCode);
        document.getElementById('productDetailDialog').close();
        app.notify('Product retired.');
        s.load();
      } catch (e) { app.notify(e.message, 'error'); }
    };
    s.load();
  }
  return VM;
});
