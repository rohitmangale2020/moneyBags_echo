define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojdialog',
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
      status: 'INACTIVE',
      rate: { interestRate: 0 },
      term: {
        tenureMonths: null,
        installmentAmount: null,
        installmentFrequency: null,
        lockInPeriod: null,
        maturityInstruction: null,
        prematureWithdrawalAllowed: null,
      },
      fee: { monthlyMaintenanceFee: 0 },
    };
  }
  function VM() {
    const s = this;
    s.isAdmin = ko.pureComputed(() => app.session.role() === 'ADMIN');
    s.isEmployee = ko.pureComputed(() => app.session.role() === 'EMPLOYEE');
    s.state = u.state([]);
    s.types = ko.observableArray([]);
    s.form = ko.observable(blank());
    s.editingCode = ko.observable(null);
    s.selected = ko.observable(null);
    s.history = ko.observableArray([]);
    s.statusTarget = ko.observable(null);
    s.statusReason = ko.observable('');
    s.typeForm = {
      productTypeCode: ko.observable(''),
      productTypeName: ko.observable(''),
      description: ko.observable(''),
      status: ko.observable('ACTIVE'),
    };
    s.error = ko.observable('');
    s.money = u.money;
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
      const d = ko.toJS(s.selected());
      s.editingCode(d.productCode);
      s.form(Object.assign(blank(), d, {
        rate: Object.assign({ interestRate: 0 }, d.rate || {}),
        term: Object.assign(blank().term, d.term || {}),
        fee: Object.assign({ monthlyMaintenanceFee: 0 }, d.fee || {}),
      }));
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
        productCode: raw.productCode, productName: raw.productName, productTypeCode: raw.productTypeCode,
        description: raw.description, minimumBalance: raw.minimumBalance, maximumBalance: raw.maximumBalance,
        currency: raw.currency, status: raw.status,
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
        document.getElementById('productDialog').close();
        if (s.editingCode()) s.selected(value);
        app.notify(s.editingCode() ? 'Product updated.' : 'Product created.');
        s.load();
      } catch (e) {
        s.error(e.message);
      }
    };
    s.openStatus = (p) => {
      s.statusTarget(p);
      s.statusReason('');
      s.error('');
      document.getElementById('productStatusDialog').open();
    };
    s.status = async () => {
      const p = s.statusTarget();
      const v = p.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      if (!s.statusReason().trim()) return s.error('A status-change reason is required.');
      try {
        const value = await app.services.products.status(p.productCode, v, s.statusReason().trim());
        document.getElementById('productStatusDialog').close();
        if (s.selected() && s.selected().productCode === p.productCode) {
          s.selected(value);
          s.history(await app.services.products.history(p.productCode));
        }
        app.notify(`Product marked ${v.toLowerCase()}.`);
        s.load();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.retire = async (p) => {
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
