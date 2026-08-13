define(['knockout', 'ojs/ojarraydataprovider', '../accUtils'], function (ko, ArrayDataProvider, accUtils) {
  function CreateProductViewModel() {
    this.productCode = ko.observable(''); this.productName = ko.observable(''); this.productTypeCode = ko.observable('FD');
    this.description = ko.observable(''); this.minimumBalance = ko.observable(); this.maximumBalance = ko.observable();
    this.currency = ko.observable('INR'); this.status = ko.observable('ACTIVE'); this.message = ko.observable(''); this.error = ko.observable('');
    this.editingProductCode = ko.observable(null); this.editMode = ko.observable(false); this.pageTitle = ko.observable('Create Product'); this.submitLabel = ko.observable('Create product');
    this.interestRate = ko.observable(0); this.tenureMonths = ko.observable(); this.installmentAmount = ko.observable(); this.installmentFrequency = ko.observable('MONTHLY');
    this.lockInPeriod = ko.observable(0); this.maturityInstruction = ko.observable('CREDIT_TO_ACCOUNT'); this.prematureWithdrawalAllowed = ko.observable(true); this.monthlyMaintenanceFee = ko.observable(0);
    this.newTypeCode = ko.observable(''); this.newTypeName = ko.observable(''); this.newTypeDescription = ko.observable(''); this.typeMessage = ko.observable('');
    this.apiBaseUrl = window.PRODUCT_API_BASE_URL || 'http://localhost:8081/api/v1';
    this.productTypes = new ArrayDataProvider([
      { value: 'FD', label: 'Fixed Deposit (FD)' }, { value: 'RD', label: 'Recurring Deposit (RD)' },
      { value: 'SAVINGS', label: 'Savings Account' }, { value: 'CURRENT', label: 'Current Account' },
      { value: 'SALARY', label: 'Salary Account' }, { value: 'CREDIT_CARD', label: 'Credit Card' }
    ], { keyAttributes: 'value' });
    this.statuses = new ArrayDataProvider([{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }], { keyAttributes: 'value' });
    this.selectedType = ko.pureComputed(() => String(this.productTypeCode() || '').toUpperCase());
    this.showTermFields = ko.pureComputed(() => ['FD', 'RD'].includes(this.selectedType()));
    this.showInstallmentFields = ko.pureComputed(() => this.selectedType() === 'RD');
    this.showBalanceFields = ko.pureComputed(() => this.selectedType() !== 'CREDIT_CARD');
    this.productTypeCode.subscribe(() => {
      if (!this.showBalanceFields()) { this.minimumBalance(null); this.maximumBalance(null); }
      if (!this.showTermFields()) { this.tenureMonths(null); this.lockInPeriod(null); this.maturityInstruction(null); this.prematureWithdrawalAllowed(null); }
      if (!this.showInstallmentFields()) { this.installmentAmount(null); this.installmentFrequency(null); }
    });

    this.loadProductTypes = async () => {
      try {
        const token = window.sessionStorage.getItem('productAdminBasicToken');
        const response = await fetch(this.apiBaseUrl + '/product-types', { headers: token ? { Authorization: 'Basic ' + token } : {} });
        if (!response.ok) return;
        const types = (await response.json()).filter(type => type.status === 'ACTIVE').map(type => ({ value: type.productTypeCode, label: type.productTypeName + ' (' + type.productTypeCode + ')' }));
        this.productTypes = new ArrayDataProvider(types, { keyAttributes: 'value' });
      } catch (_) { /* The seeded type list remains available when the service is offline. */ }
    };

    this.saveProduct = async () => {
      this.message(''); this.error('');
      const hasTerm = this.showTermFields(); const isRd = this.showInstallmentFields();
      const payload = { productCode: this.productCode(), productName: this.productName(), productTypeCode: this.productTypeCode(), description: this.description(), minimumBalance: this.showBalanceFields() ? this.minimumBalance() : null, maximumBalance: this.showBalanceFields() ? this.maximumBalance() : null, currency: this.currency(), status: this.status(), rate: { interestRate: this.interestRate() }, term: { tenureMonths: hasTerm ? this.tenureMonths() : null, installmentAmount: isRd ? this.installmentAmount() : null, installmentFrequency: isRd ? this.installmentFrequency() : null, lockInPeriod: hasTerm ? this.lockInPeriod() : null, maturityInstruction: hasTerm ? this.maturityInstruction() : null, prematureWithdrawalAllowed: hasTerm ? this.prematureWithdrawalAllowed() : null }, fee: { monthlyMaintenanceFee: this.monthlyMaintenanceFee() } };
      try {
        const token = window.sessionStorage.getItem('productAdminBasicToken');
        const method = this.editMode() ? 'PUT' : 'POST'; const url = this.editMode() ? this.apiBaseUrl + '/products/' + encodeURIComponent(this.editingProductCode()) : this.apiBaseUrl + '/products';
        const response = await fetch(url, { method: method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Basic ' + token } : {}) }, body: JSON.stringify(payload) });
        if (!response.ok) { const body = await response.json().catch(() => ({})); throw new Error(body.message || 'Unable to save product (' + response.status + ').'); }
        const product = await response.json();
        if (this.editMode()) {
          this.message('Product ' + product.productCode + ' was updated successfully. Returning to the catalogue…');
          window.setTimeout(() => { window.location.href = '?ojr=products'; }, 800);
        } else {
          this.message('Product ' + product.productCode + ' was created successfully.');
        }
      } catch (error) { this.error(error.message); }
    };
    this.loadProductForEdit = async (productCode) => {
      try {
        const token = window.sessionStorage.getItem('productAdminBasicToken');
        const response = await fetch(this.apiBaseUrl + '/products/' + encodeURIComponent(productCode), { headers: token ? { Authorization: 'Basic ' + token } : {} });
        if (!response.ok) throw new Error('Unable to load the selected product.');
        const product = await response.json();
        this.productCode(product.productCode); this.productName(product.productName); this.productTypeCode(product.productTypeCode); this.description(product.description); this.minimumBalance(product.minimumBalance); this.maximumBalance(product.maximumBalance); this.currency(product.currency); this.status(product.status);
        this.interestRate(product.rate ? product.rate.interestRate : 0); this.tenureMonths(product.term ? product.term.tenureMonths : null); this.installmentAmount(product.term ? product.term.installmentAmount : null); this.installmentFrequency(product.term ? product.term.installmentFrequency : null); this.lockInPeriod(product.term ? product.term.lockInPeriod : null); this.maturityInstruction(product.term ? product.term.maturityInstruction : null); this.prematureWithdrawalAllowed(product.term ? product.term.prematureWithdrawalAllowed : null); this.monthlyMaintenanceFee(product.fee ? product.fee.monthlyMaintenanceFee : 0);
      } catch (error) { this.error(error.message); }
    };
    this.createProductType = async () => {
      this.typeMessage(''); this.error('');
      try {
        const token = window.sessionStorage.getItem('productAdminBasicToken');
        const response = await fetch(this.apiBaseUrl + '/product-types', { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Basic ' + token } : {}) }, body: JSON.stringify({ productTypeCode: this.newTypeCode(), productTypeName: this.newTypeName(), description: this.newTypeDescription(), status: 'ACTIVE' }) });
        if (!response.ok) { const body = await response.json().catch(() => ({})); throw new Error(body.message || 'Unable to create product type.'); }
        this.typeMessage('Product type was added.'); this.productTypeCode(this.newTypeCode()); await this.loadProductTypes();
      } catch (error) { this.error(error.message); }
    };
    this.connected = () => {
      const productCode = new URLSearchParams(window.location.search).get('code');
      if (productCode) { this.editingProductCode(productCode); this.editMode(true); this.pageTitle('Edit Product'); this.submitLabel('Update product'); }
      accUtils.announce(productCode ? 'Edit product page loaded.' : 'Create product page loaded.', 'assertive'); document.title = productCode ? 'Edit Product' : 'Create Product'; this.loadProductTypes(); if (productCode) this.loadProductForEdit(productCode);
    };
  }
  return CreateProductViewModel;
});
