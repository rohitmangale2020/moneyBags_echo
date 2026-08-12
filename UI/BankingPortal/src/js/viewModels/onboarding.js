define(['knockout', 'appController', 'ojs/ojinputtext', 'ojs/ojbutton'], function (ko, app) {
  function VM() {
    const s = this;
    s.step = ko.observable(1);
    s.busy = ko.observable(false);
    s.error = ko.observable('');
    s.customer = ko.observable(null);
    s.kycRecord = ko.observable(null);
    s.products = ko.observableArray([]);
    s.account = ko.observable(null);
    s.file = ko.observable(null);
    s.profile = {
      firstName: ko.observable(''),
      lastName: ko.observable(''),
      dob: ko.observable(''),
      gender: ko.observable('MALE'),
      phone: ko.observable(''),
      email: ko.observable(''),
      occupation: ko.observable(''),
    };
    s.address = {
      addressType: ko.observable('PERMANENT'),
      line1: ko.observable(''),
      line2: ko.observable(''),
      city: ko.observable(''),
      state: ko.observable(''),
      country: ko.observable('India'),
      pincode: ko.observable(''),
    };
    s.document = {
      documentType: ko.observable('PAN'),
      documentNumber: ko.observable(''),
      issueDate: ko.observable(''),
      expiryDate: ko.observable(''),
    };
    s.kyc = {
      kycStatus: ko.observable('PENDING'),
      kycDate: ko.observable(new Date().toISOString().slice(0, 10)),
      riskLevel: ko.observable('LOW'),
      riskScore: ko.observable(0),
      remarks: ko.observable(''),
    };
    s.nominee = {
      nomineeName: ko.observable(''),
      relationship: ko.observable(''),
      dob: ko.observable(''),
      phone: ko.observable(''),
      sharePercentage: ko.observable(100),
    };
    s.accountForm = {
      accountNumber: ko.observable(''),
      productId: ko.observable(''),
      ownershipType: ko.observable('INDIVIDUAL'),
      availableBalance: ko.observable(0),
    };
    s.run = async (fn, msg) => {
      s.busy(true);
      s.error('');
      try {
        const r = await fn();
        if (msg) app.notify(msg);
        return r;
      } catch (e) {
        s.error(e.message);
        return null;
      } finally {
        s.busy(false);
      }
    };
    s.back = () => s.step(Math.max(1, s.step() - 1));
    s.createProfile = async () => {
      const p = {};
      Object.keys(s.profile).forEach((k) => (p[k] = s.profile[k]()));
      if (!p.firstName || !p.dob || !p.phone)
        return s.error('Complete all required profile fields.');
      const r = await s.run(
        () => app.services.customers.create(p),
        'Customer created and CIF assigned.',
      );
      if (r) {
        s.customer(r);
        s.step(2);
      }
    };
    s.saveAddress = async () => {
      const p = {};
      Object.keys(s.address).forEach((k) => (p[k] = s.address[k]()));
      const r = await s.run(
        () => app.services.customers.address(s.customer().customerId, p),
        'Address saved.',
      );
      if (r) s.step(3);
    };
    s.choose = (_, e) => s.file(e.target.files[0]);
    s.upload = async () => {
      if (!s.file() || !s.document.documentNumber())
        return s.error('Choose a file and enter its document number.');
      const d = {
        documentType: s.document.documentType(),
        documentNumber: s.document.documentNumber(),
        issueDate: s.document.issueDate() || null,
        expiryDate: s.document.expiryDate() || null,
        status: 'UPLOADED',
        verifiedBy: null,
        rejectedReason: null,
        remarks: null,
        updatedBy: null,
      };
      const r = await s.run(
        () => app.services.customers.document(s.customer().customerId, s.file(), d),
        'Document uploaded.',
      );
      if (r) s.step(4);
    };
    s.saveKyc = async () => {
      const d = {
        kycStatus: s.kyc.kycStatus(),
        kycDate: s.kyc.kycDate() || null,
        verifiedBy: null,
        riskLevel: s.kyc.riskLevel(),
        riskScore: Number(s.kyc.riskScore()),
        expiryDate: null,
        remarks: s.kyc.remarks(),
        updatedBy: null,
      };
      const r = await s.run(
        () => app.services.customers.createKyc(s.customer().customerId, d),
        'KYC saved.',
      );
      if (!r) return;
      s.kycRecord(r);
      if (s.nominee.nomineeName()) {
        const n = {
          nomineeName: s.nominee.nomineeName(),
          relationship: s.nominee.relationship(),
          relationType: 'NOMINEE',
          dob: s.nominee.dob() || null,
          phone: s.nominee.phone(),
          address: null,
          sharePercentage: Number(s.nominee.sharePercentage()),
          status: 'ACTIVE',
          updatedBy: null,
          startDate: new Date().toISOString().slice(0, 10),
          endDate: null,
        };
        if (
          !(await s.run(
            () => app.services.customers.nominee(s.customer().customerId, n),
            'Nominee saved.',
          ))
        )
          return;
      }
      s.step(5);
    };
    s.activate = async () => {
      if (s.kycRecord().kycStatus !== 'VERIFIED')
        return s.error('KYC must be VERIFIED before activation.');
      const r = await s.run(
        () => app.services.customers.activate(s.customer().customerId),
        'Customer activated.',
      );
      if (r) {
        s.customer(r);
        const p = await s.run(() => app.services.products.list());
        if (p) {
          s.products(p.filter((x) => x.status === 'ACTIVE'));
          s.step(6);
        }
      }
    };
    s.openAccount = async () => {
      const p = s.products().find((x) => String(x.productId) === String(s.accountForm.productId()));
      if (!p || !s.accountForm.accountNumber())
        return s.error('Select a product and enter the approved account number.');
      const d = {
        accountNumber: s.accountForm.accountNumber(),
        customerId: String(s.customer().customerId),
        productId: String(p.productId),
        ownershipType: s.accountForm.ownershipType(),
        status: 'ACTIVE',
        currencyCode: p.currency,
        availableBalance: Number(s.accountForm.availableBalance()),
        closedAt: null,
      };
      const r = await s.run(() => app.services.accounts.create(d), 'Account opened successfully.');
      if (r) s.account(r);
    };
  }
  return VM;
});
