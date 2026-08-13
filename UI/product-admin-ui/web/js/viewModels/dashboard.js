define(['knockout', '../accUtils'], function (ko, accUtils) {
  function DashboardViewModel() {
    this.products = ko.observableArray([]); this.error = ko.observable(''); this.apiBaseUrl = window.PRODUCT_API_BASE_URL || 'http://localhost:8081/api/v1';
    const hour = new Date().getHours(); this.timeGreeting = ko.observable(hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening');
    this.totalCount = ko.pureComputed(() => this.products().length); this.activeCount = ko.pureComputed(() => this.products().filter(p => p.status === 'ACTIVE').length); this.inactiveCount = ko.pureComputed(() => this.products().filter(p => p.status === 'INACTIVE').length); this.retiredCount = ko.pureComputed(() => this.products().filter(p => p.status === 'RETIRED').length); this.activePercentage = ko.pureComputed(() => this.totalCount() ? Math.round((this.activeCount() / this.totalCount()) * 100) : 0);
    this.productsByType = ko.pureComputed(() => { const total = this.totalCount() || 1; const counts = this.products().reduce((r, p) => { r[p.productTypeCode] = (r[p.productTypeCode] || 0) + 1; return r; }, {}); return Object.keys(counts).map(code => ({ label: code.replace('_', ' '), count: counts[code], percent: Math.round(counts[code] * 100 / total) })).sort((a, b) => b.count - a.count); });
    this.recentlyUpdated = ko.pureComputed(() => this.products().slice().sort((a, b) => new Date(b.updatedDate || 0) - new Date(a.updatedDate || 0)).slice(0, 5));
    this.load = async () => { try { const token = window.sessionStorage.getItem('productAdminBasicToken'); const response = await fetch(this.apiBaseUrl + '/products', { headers: token ? { Authorization: 'Basic ' + token } : {} }); if (!response.ok) throw new Error('Unable to load dashboard data.'); this.products(await response.json()); } catch (error) { this.error(error.message); } };
    this.connected = () => { accUtils.announce('Admin dashboard loaded.', 'assertive'); document.title = 'Admin dashboard'; this.load(); };
  }
  return DashboardViewModel;
});
