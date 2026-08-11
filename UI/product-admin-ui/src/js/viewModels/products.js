define(['knockout', '../accUtils'], function (ko, accUtils) {
  function ProductsViewModel() {
    this.products = ko.observableArray([]);
    this.loading = ko.observable(false);
    this.error = ko.observable('');
    this.searchText = ko.observable('');
    this.selectedType = ko.observable('ALL');
    this.selectedStatus = ko.observable('ALL');
    this.typeOptions = [
      { value: 'ALL', label: 'All product types' }, { value: 'FD', label: 'Fixed Deposit' }, { value: 'RD', label: 'Recurring Deposit' },
      { value: 'SAVINGS', label: 'Savings Account' }, { value: 'CURRENT', label: 'Current Account' }, { value: 'SALARY', label: 'Salary Account' }, { value: 'CREDIT_CARD', label: 'Credit Card' }
    ];
    this.statusOptions = [{ value: 'ALL', label: 'All statuses' }, { value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }, { value: 'RETIRED', label: 'Retired' }];
    this.filteredProducts = ko.pureComputed(() => {
      const search = this.searchText().trim().toLowerCase();
      return this.products().filter(product => {
        const matchesText = !search || product.productCode.toLowerCase().includes(search) || product.productName.toLowerCase().includes(search);
        return matchesText && (this.selectedType() === 'ALL' || product.productTypeCode === this.selectedType()) && (this.selectedStatus() === 'ALL' || product.status === this.selectedStatus());
      });
    });
    this.totalCount = ko.pureComputed(() => this.products().length);
    this.activeCount = ko.pureComputed(() => this.products().filter(product => product.status === 'ACTIVE').length);
    this.inactiveCount = ko.pureComputed(() => this.products().filter(product => product.status === 'INACTIVE').length);
    this.retiredCount = ko.pureComputed(() => this.products().filter(product => product.status === 'RETIRED').length);
    this.apiBaseUrl = window.PRODUCT_API_BASE_URL || 'http://localhost:8081/api/v1';

    this.loadProducts = async () => {
      this.loading(true);
      this.error('');
      try {
        const response = await fetch(this.apiBaseUrl + '/products', { headers: this.authHeaders() });
        if (!response.ok) throw new Error('Unable to load products (' + response.status + '). Check that Product Service is running and your Admin credentials are configured.');
        this.products(await response.json());
      } catch (error) {
        this.error(error.message);
      } finally {
        this.loading(false);
      }
    };

    this.authHeaders = () => {
      const token = window.sessionStorage.getItem('productAdminBasicToken');
      return token ? { Authorization: 'Basic ' + token } : {};
    };
    this.retireProduct = async (product) => {
      if (!window.confirm('Retire product ' + product.productCode + '? This will keep the record but make it unavailable.')) return;
      this.error('');
      try {
        const response = await fetch(this.apiBaseUrl + '/products/' + encodeURIComponent(product.productCode), { method: 'DELETE', headers: this.authHeaders() });
        if (!response.ok) throw new Error('Unable to retire product (' + response.status + ').');
        await this.loadProducts();
      } catch (error) { this.error(error.message); }
    };
    this.editProduct = (product) => { window.location.href = '?ojr=create-product&code=' + encodeURIComponent(product.productCode); };
    this.clearFilters = () => { this.searchText(''); this.selectedType('ALL'); this.selectedStatus('ALL'); };

    this.connected = () => {
      accUtils.announce('Product catalogue page loaded.', 'assertive');
      document.title = 'Product Catalogue';
      this.loadProducts();
    };
  }
  return ProductsViewModel;
});
