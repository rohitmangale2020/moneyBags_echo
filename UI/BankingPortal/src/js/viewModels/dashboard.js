define(['knockout', 'appController', 'viewModels/util'], function (ko, app, u) {
  const statusOrder = ['ACTIVE', 'KYC_PENDING', 'INACTIVE'];
  const statusStyles = {
    ACTIVE: { background: 'rgba(73, 173, 123, .16)', color: '#4b9b70' },
    KYC_PENDING: { background: 'rgba(232, 166, 58, .16)', color: '#b9852d' },
    INACTIVE: { background: 'rgba(132, 148, 170, .16)', color: '#7a879a' }
  };
  const accountColors = ['#1b486d', '#2a6fab', '#358fdc', '#6bb1e4', '#a2cdee'];
  const accountTypeColors = { CURRENT: '#1b486d', SAVINGS: '#2a6fab', FD: '#358fdc', FIXED_DEPOSIT: '#358fdc', SALARY: '#6bb1e4', OTHER: '#a2cdee' };
  const count = (page) => Number(page && page.totalElements !== undefined ? page.totalElements : u.list(page).length);
  const dayKey = (value) => {
    const date = value ? new Date(value) : null;
    if (!date || Number.isNaN(date.getTime())) return null;
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${date.getFullYear()}-${month}-${day}`;
  };
  const activityDay = (item) => dayKey(item.initiatedAt || item.openedAt || item.completedAt || item.createdAt || item.updatedAt) || dayKey(new Date());
  const lastSevenDays = () => Array.from({ length: 7 }, (_, index) => { const date = new Date(); date.setHours(0, 0, 0, 0); date.setDate(date.getDate() - (6 - index)); return date; });
  const periodDays = (period) => {
    if (period === 'TODAY') return 1;
    if (period === '30D') return 30;
    if (period === 'MTD') return new Date().getDate();
    if (period === 'YTD') return Math.min(365, Math.ceil((new Date() - new Date(new Date().getFullYear(), 0, 1)) / 86400000) + 1);
    return 7;
  };
  const daysForPeriod = (period) => {
    const length = periodDays(period);
    return Array.from({ length }, (_, index) => { const date = new Date(); date.setHours(0, 0, 0, 0); date.setDate(date.getDate() - (length - 1 - index)); return date; });
  };
  const daysForRange = (start, end) => {
    const first = start ? new Date(`${start}T00:00:00`) : null;
    const last = end ? new Date(`${end}T00:00:00`) : null;
    if (!first || !last || Number.isNaN(first.getTime()) || Number.isNaN(last.getTime()) || first > last) return daysForPeriod('7D');
    const length = Math.min(366, Math.floor((last - first) / 86400000) + 1);
    return Array.from({ length }, (_, index) => { const date = new Date(first); date.setDate(first.getDate() + index); return date; });
  };
  const shortDate = (date) => new Intl.DateTimeFormat('en-IN', { month: 'short', day: 'numeric' }).format(date);
  const label = (value) => String(value || 'Other').replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
  const compactInr = (value) => {
    const amount = Number(value || 0);
    if (Math.abs(amount) >= 10000000) return `₹${(amount / 10000000).toFixed(1)}Cr`;
    if (Math.abs(amount) >= 100000) return `₹${(amount / 100000).toFixed(1)}L`;
    if (Math.abs(amount) >= 1000) return `₹${(amount / 1000).toFixed(1)}K`;
    return `₹${amount.toFixed(0)}`;
  };
  const percent = (value, total) => total ? Math.round(value / total * 1000) / 10 : 0;
  const grouped = (items, field, value = (item) => 1) => items.reduce((result, item) => { const key = item[field] || 'OTHER'; result[key] = (result[key] || 0) + value(item); return result; }, {});
  const gradient = (segments) => {
    let start = 0;
    const stops = segments.map((segment) => { const end = start + segment.percent; const stop = `${segment.color} ${start}% ${end}%`; start = end; return stop; });
    return `conic-gradient(${stops.join(', ') || '#eef1f5 0 100%'})`;
  };
  const chart = (days, primary, secondary) => {
    const left = 36, top = 18, width = 604, height = 154, max = Math.max(1, ...primary, ...secondary);
    const points = (series) => series.map((value, index) => ({ x: left + (index * width / Math.max(1, days.length - 1)), y: top + height - (value / max * height) }));
    const joined = (series) => points(series).map((point) => `${point.x},${point.y}`).join(' ');
    const labelStride = days.length <= 7 ? 1 : days.length <= 31 ? 5 : Math.ceil(days.length / 6);
    const visibleDot = (series, index) => days.length <= 31 || series[index] !== 0 || index % labelStride === 0 || index === days.length - 1;
    const primaryDots = points(primary).map((point, index) => ({ ...point, date: shortDate(days[index]), primary: primary[index], secondary: secondary[index] })).filter((point, index) => visibleDot(primary, index));
    const secondaryDots = points(secondary).map((point, index) => ({ ...point, date: shortDate(days[index]), primary: primary[index], secondary: secondary[index] })).filter((point, index) => visibleDot(secondary, index));
    const peak = primaryDots.reduce((largest, point) => point.primary > largest.primary ? point : largest, primaryDots[0] || { x: left, y: top + height, primary: 0 });
    return { labels: days.map((date, index) => ({ text: shortDate(date), x: left + (index * width / Math.max(1, days.length - 1)), visible: index % labelStride === 0 || index === days.length - 1 })).filter((entry) => entry.visible), grid: [0, .25, .5, .75, 1].map((step) => ({ y: top + height - step * height, label: Math.round(max * step) })), primaryDots, secondaryDots, primaryPoints: joined(primary), secondaryPoints: joined(secondary), primaryAreaPoints: `${left},${top + height} ${joined(primary)} ${left + width},${top + height}`, peak: { x: peak.x, y: Math.max(14, peak.y - 12), value: peak.primary } };
  };

  function VM() {
    const self = this;
    document.body.classList.add('mb-operations-layout');
    self.disconnected = () => document.body.classList.remove('mb-operations-layout');
    self.displayName = app.displayName;
    self.isCustomer = ko.pureComputed(() => app.session.role() === 'CUSTOMER');
    self.state = u.state({ usersTotal: 0, customerTotal: 0, customerTotals: {}, products: [], customers: [], accounts: [], transactions: [], transactionsTotal: 0 });
    self.money = u.money;
    self.date = u.date;
    self.openCustomers = () => app.go('customers');
    self.openAccounts = () => app.go('accounts');
    self.openTransactions = () => app.go('transactions');
    self.openProducts = () => app.go('products');
    self.openOnboarding = () => app.go('onboarding');
    self.openProfile = () => app.go('profile');
    self.toggleSidebar = app.toggleSidebar;
    self.period = ko.observable('ALL');
    const initialRange = daysForPeriod('7D');
    self.startDate = ko.observable(dayKey(initialRange[0]));
    self.endDate = ko.observable(dayKey(initialRange[initialRange.length - 1]));
    self.lastUpdated = ko.observable(null);
    self.lastUpdatedLabel = ko.pureComputed(() => self.lastUpdated() ? `Last updated ${new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit' }).format(self.lastUpdated())}` : 'Updating…');
    self.activityMetric = ko.observable('TRANSACTIONS');
    self.showComparison = ko.observable(true);
    self.periodLabel = ko.pureComputed(() => ({ ALL: 'All activity', TODAY: 'Today', '7D': 'Last 7 days', '30D': 'Last 30 days', MTD: 'Month to date', YTD: 'Year to date', CUSTOM: 'Custom range' })[self.period()]);
    self.allActivityDays = ko.pureComputed(() => {
      const dates = [...self.state.data().transactions, ...self.state.data().accounts].map(activityDay).sort();
      if (!dates.length) return daysForPeriod('7D');
      const earliest = new Date(`${dates[0]}T00:00:00`);
      const today = dayKey(new Date());
      const latestKey = dates[dates.length - 1] > today ? dates[dates.length - 1] : today;
      const latest = new Date(`${latestKey}T00:00:00`);
      if ((latest - earliest) / 86400000 <= 366) return daysForRange(dates[0], latestKey);
      return [...new Set(dates)].map((date) => new Date(`${date}T00:00:00`));
    });
    self.setPeriod = (period) => {
      self.period(period);
      const dates = period === 'ALL' ? self.allActivityDays() : daysForPeriod(period);
      self.startDate(dayKey(dates[0]));
      self.endDate(dayKey(dates[dates.length - 1]));
    };
    self.applyDateRange = () => self.period('CUSTOM');
    self.selectedDays = ko.pureComputed(() => self.period() === 'ALL' ? self.allActivityDays() : daysForRange(self.startDate(), self.endDate()));
    self.filteredTransactions = ko.pureComputed(() => {
      if (self.period() === 'ALL') return self.state.data().transactions;
      const earliest = self.startDate(); const latest = self.endDate();
      return self.state.data().transactions.filter((transaction) => dayKey(transaction.initiatedAt) >= earliest && dayKey(transaction.initiatedAt) <= latest);
    });
    self.filteredAccounts = ko.pureComputed(() => {
      if (self.period() === 'ALL') return self.state.data().accounts;
      const earliest = self.startDate(); const latest = self.endDate();
      return self.state.data().accounts.filter((account) => dayKey(account.openedAt || account.createdAt) >= earliest && dayKey(account.openedAt || account.createdAt) <= latest);
    });
    self.toggleComparison = () => self.showComparison(!self.showComparison());
    self.refreshDashboard = async () => { await self.load(); app.notify('Dashboard data refreshed.'); };
    self.refreshActivity = self.refreshDashboard;
    self.customerStatus = ko.pureComputed(() => {
      const data = self.state.data(); const total = data.customerTotal;
      const segments = statusOrder.map((status) => ({ key: status, label: label(status), value: data.customerTotals[status] || 0, background: statusStyles[status].background, color: statusStyles[status].color, percent: percent(data.customerTotals[status] || 0, total) }));
      return { total, segments, gradient: gradient(segments) };
    });
    self.accountComposition = ko.pureComputed(() => {
      const accounts = self.state.data().accounts;
      const makeSegments = (values) => {
        const total = Object.values(values).reduce((sum, value) => sum + value, 0);
        return Object.entries(values).sort((left, right) => right[1] - left[1]).slice(0, 4).map(([type, value], index) => ({ label: label(type), value, color: accountTypeColors[type] || accountColors[index], percent: percent(value, total) }));
      };
      const number = makeSegments(grouped(accounts, 'productTypeCode'));
      const balanceValues = grouped(accounts, 'productTypeCode', (account) => Math.max(0, Number(account.availableBalance || 0)));
      const balance = makeSegments(balanceValues);
      const balanceTotal = Object.values(balanceValues).reduce((sum, value) => sum + value, 0);
      return { number: { total: accounts.length, segments: number, gradient: gradient(number) }, balance: { total: balanceTotal, formattedTotal: compactInr(balanceTotal), segments: balance, gradient: gradient(balance) } };
    });
    self.operationalPulse = ko.pureComputed(() => {
      const days = self.selectedDays();
      const transactionCounts = days.map((day) => self.filteredTransactions().filter((transaction) => activityDay(transaction) === dayKey(day)).length);
      const accountCounts = days.map((day) => self.filteredAccounts().filter((account) => activityDay(account) === dayKey(day)).length);
      const transactionsSelected = self.activityMetric() === 'TRANSACTIONS';
      const primary = transactionsSelected ? transactionCounts : accountCounts;
      const secondary = self.showComparison() ? (transactionsSelected ? accountCounts : transactionCounts) : primary.map(() => 0);
      const result = chart(days, primary, secondary);
      result.primaryLabel = transactionsSelected ? 'Transactions' : 'Accounts opened';
      result.secondaryLabel = transactionsSelected ? 'Accounts opened' : 'Transactions';
      result.showComparison = self.showComparison();
      result.primaryDots.forEach((point) => { point.tooltip = `${point.date}: ${point.primary} ${result.primaryLabel} · ${point.secondary} ${result.secondaryLabel}`; });
      result.secondaryDots.forEach((point) => { point.tooltip = `${point.date}: ${point.secondary} ${result.secondaryLabel} · ${point.primary} ${result.primaryLabel}`; });
      result.primaryTotal = primary.reduce((total, value) => total + value, 0);
      result.secondaryTotal = secondary.reduce((total, value) => total + value, 0);
      return result;
    });
    self.metrics = ko.pureComputed(() => {
      const data = self.state.data();
      const failed = data.transactions.filter((transaction) => transaction.transactionStatus === 'FAILED').length;
      const weekDays = lastSevenDays();
      const createdThisWeek = data.customers.filter((customer) => weekDays.some((day) => dayKey(customer.createdAt) === dayKey(day))).length;
      const openedThisWeek = data.accounts.filter((account) => weekDays.some((day) => dayKey(account.openedAt || account.createdAt) === dayKey(day))).length;
      const kycCompletion = percent(data.customerTotals.ACTIVE || 0, data.customerTotal);
      return [
        { label: 'Total customers', value: data.customerTotal, note: 'Live portfolio', tone: 'blue' },
        { label: 'Total accounts', value: data.accounts.length, note: 'Live portfolio', tone: 'teal' },
        { label: 'Active products', value: data.products.filter((product) => product.status === 'ACTIVE').length, note: 'Available products', tone: 'blue' },
        { label: 'Transactions', value: data.transactionsTotal, note: 'Recorded activity', tone: 'blue' },
        { label: 'New customers', value: createdThisWeek, note: 'Joined this week', tone: 'green' },
        { label: 'Accounts opened', value: openedThisWeek, note: 'Opened this week', tone: 'teal' },
        { label: 'KYC completion', value: `${kycCompletion}%`, note: 'Verified and active', tone: 'green' },
        { label: 'Failed transactions', value: failed, note: 'In loaded activity', tone: failed ? 'red' : 'green' },
      ];
    });
    self.recentTransactions = ko.pureComputed(() => self.filteredTransactions().slice().sort((left, right) => Date.parse(right.initiatedAt || 0) - Date.parse(left.initiatedAt || 0)).slice(0, 4));
    self.transactionKind = (transaction) => transaction.transactionType === 'WITHDRAWAL' ? 'Withdrawal' : label(transaction.transactionType);
    self.transactionAccount = (transaction) => transaction.debitAccountId || transaction.creditAccountId || '—';
    self.queues = ko.pureComputed(() => {
      const data = self.state.data();
      return [
        { label: 'KYC verification', count: data.customerTotals.KYC_PENDING || 0, action: self.openCustomers },
        { label: 'Account opening', count: data.accounts.filter((account) => account.status === 'PENDING').length, action: self.openAccounts },
        { label: 'Transaction reviews', count: data.transactions.filter((transaction) => transaction.transactionStatus === 'PENDING').length, action: self.openTransactions },
        { label: 'Product catalogue', count: data.products.filter((product) => product.status === 'ACTIVE').length, action: self.openProducts },
      ];
    });
    self.load = () => self.state.run(async () => {
      if (self.isCustomer()) return { usersTotal: 0, customerTotal: 0, customerTotals: {}, products: [], customers: [], accounts: [], transactions: [], transactionsTotal: 0 };
      const values = await Promise.all([app.services.customers.list(0, 1000), app.services.customers.list(0, 1, 'KYC_PENDING'), app.services.customers.list(0, 1, 'ACTIVE'), app.services.customers.list(0, 1, 'INACTIVE'), app.services.products.list(), app.services.accounts.list(), app.services.transactions.list()]);
      return { customerTotal: count(values[0]), customers: u.list(values[0]), customerTotals: { KYC_PENDING: count(values[1]), ACTIVE: count(values[2]), INACTIVE: count(values[3]) }, products: values[4], accounts: u.list(values[5]), transactions: u.list(values[6]), transactionsTotal: count(values[6]) };
    }).then((result) => {
      if (self.period() === 'ALL') {
        const dates = self.allActivityDays();
        self.startDate(dayKey(dates[0]));
        self.endDate(dayKey(dates[dates.length - 1]));
      }
      self.lastUpdated(new Date());
      return result;
    }).catch(() => null);
    self.load();
  }
  return VM;
});
