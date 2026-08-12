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
      s.form(blank());
      s.error('');
      document.getElementById('productDialog').open();
    };
    s.openType = () => {
      s.error('');
      document.getElementById('productTypeDialog').open();
    };
    s.close = () => document.getElementById('productDialog').close();
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
      const d = ko.toJS(s.form());
      d.minimumBalance = Number(d.minimumBalance || 0);
      d.maximumBalance = d.maximumBalance ? Number(d.maximumBalance) : null;
      d.rate.interestRate = Number(d.rate.interestRate || 0);
      d.fee.monthlyMaintenanceFee = Number(d.fee.monthlyMaintenanceFee || 0);
      if (!d.productCode || !d.productName || !d.productTypeCode)
        return s.error('Complete all required fields.');
      try {
        await app.services.products.create(d);
        document.getElementById('productDialog').close();
        app.notify('Product created.');
        s.load();
      } catch (e) {
        s.error(e.message);
      }
    };
    s.status = async (p) => {
      const v = p.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      try {
        await app.services.products.status(
          p.productCode,
          v,
          `Status changed to ${v} through portal`,
        );
        app.notify(`Product marked ${v.toLowerCase()}.`);
        s.load();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.load();
  }
  return VM;
});
