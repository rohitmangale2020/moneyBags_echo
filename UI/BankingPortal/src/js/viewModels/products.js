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
      fee: { annualMaintenanceFee: 0 },
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
    }).sort((left, right) => {
      const typeOrder = String(left.productTypeName || left.productTypeCode || '')
        .localeCompare(String(right.productTypeName || right.productTypeCode || ''));
      return typeOrder || String(left.productCode || '').localeCompare(String(right.productCode || ''));
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
    s.retirementImpact = ko.observable(null);
    s.retirementProduct = ko.observable(null);
    s.retirementMigrationProduct = ko.observable('');
    s.retirementInProgress = ko.observable(false);
    s.impactLabel = (riskLevel) => riskLevel === 'NO_IMPACT' ? 'No impact' : `${riskLevel} impact`;
    s.riskLabel = (riskLevel) => riskLevel === 'NO_IMPACT' ? 'No impact' : `${riskLevel} risk`;
    s.canProceedRetirement = ko.pureComputed(() => {
      const impact = s.retirementImpact();
      return !!impact && (impact.affectedAccountCount === 0 || !!s.retirementMigrationProduct());
    });
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
    s.refreshProducts = () => s.load();
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
    s.isInterestBearing = ko.pureComputed(() => ['SAVINGS', 'SALARY', 'FD', 'RD', 'CREDIT_CARD'].includes(s.selectedProductType()));
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
      if (!['SAVINGS', 'CURRENT'].includes(typeCode)) product.fee.annualMaintenanceFee = 0;
      if (typeCode === 'CREDIT_CARD') {
        product.minimumBalance = null;
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
        fee: Object.assign({ annualMaintenanceFee: 0 }, d.fee || {}),
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
        description: raw.description, minimumBalance: raw.minimumBalance, maximumBalance: null,
        currency: 'INR', status: raw.status,
        rate: Object.assign({ interestRate: 0 }, raw.rate), term: Object.assign({}, raw.term),
        fee: Object.assign({ annualMaintenanceFee: 0 }, raw.fee),
      };
      d.minimumBalance = Number(d.minimumBalance || 0);
      d.rate.interestRate = Number(d.rate.interestRate || 0);
      d.fee.annualMaintenanceFee = Number(d.fee.annualMaintenanceFee || 0);
      d.term.tenureMonths = d.term.tenureMonths ? Number(d.term.tenureMonths) : null;
      d.term.installmentAmount = d.term.installmentAmount ? Number(d.term.installmentAmount) : null;
      d.term.lockInPeriod = d.term.lockInPeriod ? Number(d.term.lockInPeriod) : null;
      if (!d.productCode || !d.productName || !d.productTypeCode)
        return s.error('Complete all required fields.');
      if (!/^[A-Za-z0-9_-]+$/.test(d.productCode)) return s.error('Product code can contain only letters, numbers, hyphens, and underscores.');
      if (d.currency !== 'INR') return s.error('Products currently support INR only.');
      if ([d.minimumBalance, d.rate.interestRate, d.fee.annualMaintenanceFee, d.term.installmentAmount].some((v) => v !== null && (v < 0 || v > 999999999.99))) return s.error('Balances, fees, and installments must be between 0 and 999,999,999.99.');
      if (d.rate.interestRate > 100) return s.error('Interest rate cannot exceed 100%.');
      const isTermDeposit = ['FD', 'RD'].includes(d.productTypeCode);
      if (isTermDeposit && (!Number.isInteger(d.term.tenureMonths) || d.term.tenureMonths < 1 || d.term.tenureMonths > 1200)) return s.error('FD and RD tenure must be a whole number between 1 and 1200 months.');
      if (isTermDeposit && d.term.lockInPeriod !== null && (!Number.isInteger(d.term.lockInPeriod) || d.term.lockInPeriod < 0 || d.term.lockInPeriod > d.term.tenureMonths)) return s.error('Lock-in period must be a whole number from zero to the product tenure.');
      if (d.productTypeCode === 'RD' && (d.term.installmentAmount === null || d.term.installmentAmount <= 0 || !['MONTHLY', 'QUARTERLY'].includes(d.term.installmentFrequency))) return s.error('RD products require a positive installment amount and a frequency.');
      if (d.productTypeCode === 'CREDIT_CARD' && d.minimumBalance > 0) return s.error('Credit card products cannot have a minimum balance.');
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
      try {
        const impact = await app.services.products.retirementImpact(p.productCode);
        s.retirementProduct(p);
        s.retirementImpact(impact);
        s.retirementMigrationProduct(impact.recommendedProducts && impact.recommendedProducts.length
          ? impact.recommendedProducts[0].productCode : '');
        document.getElementById('retirementImpactDialog').open();
      } catch (e) { app.notify(e.message, 'error'); }
    };
    s.cancelRetirement = () => {
      s.retirementImpact(null);
      s.retirementProduct(null);
      s.retirementMigrationProduct('');
      document.getElementById('retirementImpactDialog').close();
    };
    s.proceedRetirement = async () => {
      const product = s.retirementProduct();
      const impact = s.retirementImpact();
      if (!product || !impact) return;
      s.retirementInProgress(true);
      try {
        await app.services.products.retire(product.productCode, {
          migrationProductCode: s.retirementMigrationProduct() || null,
        });
        document.getElementById('retirementImpactDialog').close();
        document.getElementById('productDetailDialog').close();
        app.notify(`Product retired. ${impact.riskLevel} impact was recorded in lifecycle history.`);
        s.load();
      } catch (e) { app.notify(e.message, 'error');
      } finally { s.retirementInProgress(false); }
    };
    s.load();
  }
  return VM;
});
