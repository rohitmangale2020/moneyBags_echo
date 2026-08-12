define(['knockout', 'appController', 'ojs/ojinputtext', 'ojs/ojbutton'], function (ko, app) {
  'use strict';

  const today = () => new Date().toISOString().slice(0, 10);
  const text = (value) => String(value || '').trim();

  function VM() {
    const s = this;
    s.step = ko.observable(1);
    s.busy = ko.observable(false);
    s.error = ko.observable('');
    s.fieldErrors = ko.observable({});
    s.customer = ko.observable(null);
    s.kycRecord = ko.observable(null);
    s.products = ko.observableArray([]);
    s.account = ko.observable(null);
    s.file = ko.observable(null);
    s.resumeKind = ko.observable('cif');
    s.resumeValue = ko.observable('');
    s.verifiedBy = ko.pureComputed(() => String(app.session.userId() || ''));

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
      kycDate: ko.observable(today()),
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

    s.setErrors = (errors) => {
      s.fieldErrors(errors);
      const first = Object.values(errors)[0];
      s.error(first || '');
      return !first;
    };
    s.clearErrors = () => {
      s.fieldErrors({});
      s.error('');
    };
    s.run = async (fn, message) => {
      s.busy(true);
      s.clearErrors();
      try {
        const result = await fn();
        if (message) app.notify(message);
        return result;
      } catch (error) {
        s.error(error.message);
        return null;
      } finally {
        s.busy(false);
      }
    };
    s.back = () => {
      s.clearErrors();
      s.step(Math.max(1, s.step() - 1));
    };
    s.goCustomers = () => app.go('customers');

    s.validateProfile = () => {
      const e = {};
      const firstName = text(s.profile.firstName());
      const lastName = text(s.profile.lastName());
      const phone = text(s.profile.phone());
      const email = text(s.profile.email());
      const occupation = text(s.profile.occupation());
      if (!firstName) e.firstName = 'First name is required.';
      else if (firstName.length > 100) e.firstName = 'First name cannot exceed 100 characters.';
      if (lastName.length > 100) e.lastName = 'Last name cannot exceed 100 characters.';
      if (!s.profile.dob()) e.dob = 'Date of birth is required.';
      else if (s.profile.dob() >= today()) e.dob = 'Date of birth must be in the past.';
      if (!phone) e.phone = 'Mobile number is required.';
      else if (!/^[6-9][0-9]{9}$/.test(phone)) e.phone = 'Enter a 10-digit Indian mobile number beginning with 6, 7, 8, or 9.';
      if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) e.email = 'Enter a valid email address, for example name@example.com.';
      else if (email.length > 254) e.email = 'Email cannot exceed 254 characters.';
      if (occupation.length > 100) e.occupation = 'Occupation cannot exceed 100 characters.';
      return s.setErrors(e);
    };

    s.createProfile = async () => {
      if (!s.validateProfile()) return;
      const payload = {};
      Object.keys(s.profile).forEach((key) => (payload[key] = text(s.profile[key]()) || null));
      payload.gender = s.profile.gender();
      payload.dob = s.profile.dob();
      const result = await s.run(
        () => app.services.customers.create(payload),
        'Customer created and CIF assigned.',
      );
      if (result) {
        s.customer(result);
        s.step(2);
      }
    };

    s.saveAddress = async () => {
      const e = {};
      const values = {};
      Object.keys(s.address).forEach((key) => (values[key] = text(s.address[key]())));
      if (!values.line1) e.line1 = 'Address line 1 is required.';
      else if (values.line1.length > 250) e.line1 = 'Address line 1 cannot exceed 250 characters.';
      if (values.line2.length > 250) e.line2 = 'Address line 2 cannot exceed 250 characters.';
      ['city', 'state', 'country'].forEach((field) => {
        if (!values[field]) e[field] = `${field[0].toUpperCase()}${field.slice(1)} is required.`;
        else if (values[field].length > 100) e[field] = `${field[0].toUpperCase()}${field.slice(1)} cannot exceed 100 characters.`;
      });
      if (!/^[1-9][0-9]{5}$/.test(values.pincode)) e.pincode = 'Enter a valid six-digit Indian pincode that does not begin with zero.';
      if (!s.setErrors(e)) return;
      const result = await s.run(
        () => app.services.customers.address(s.customer().customerId, values),
        'Address saved.',
      );
      if (result) s.step(3);
    };

    s.choose = (_, event) => {
      s.file(event.target.files && event.target.files[0]);
      if (s.file()) s.fieldErrors(Object.assign({}, s.fieldErrors(), { file: '' }));
    };
    s.upload = async () => {
      const e = {};
      const number = text(s.document.documentNumber());
      if (!number) e.documentNumber = 'Document number is required.';
      else if (number.length > 100) e.documentNumber = 'Document number cannot exceed 100 characters.';
      if (!s.file()) e.file = 'Choose a PDF or image to upload.';
      if (s.document.issueDate() && s.document.issueDate() > today()) e.issueDate = 'Issue date cannot be in the future.';
      if (s.document.issueDate() && s.document.expiryDate() && s.document.expiryDate() < s.document.issueDate()) e.expiryDate = 'Expiry date cannot be earlier than the issue date.';
      if (!s.setErrors(e)) return;
      const payload = {
        documentType: s.document.documentType(),
        documentNumber: number,
        issueDate: s.document.issueDate() || null,
        expiryDate: s.document.expiryDate() || null,
        status: 'UPLOADED',
        verifiedBy: null,
        rejectedReason: null,
        remarks: null,
        updatedBy: s.verifiedBy(),
      };
      const result = await s.run(
        () => app.services.customers.document(s.customer().customerId, s.file(), payload),
        'Document uploaded.',
      );
      if (result) s.step(4);
    };

    s.saveKyc = async () => {
      const e = {};
      const score = Number(s.kyc.riskScore());
      if (s.kyc.kycDate() && s.kyc.kycDate() > today()) e.kycDate = 'KYC date cannot be in the future.';
      if (!Number.isInteger(score) || score < 0 || score > 100) e.riskScore = 'Risk score must be a whole number from 0 to 100.';
      if (text(s.kyc.remarks()).length > 500) e.remarks = 'Remarks cannot exceed 500 characters.';
      const hasNominee = Boolean(text(s.nominee.nomineeName()) || text(s.nominee.relationship()) || text(s.nominee.dob()) || text(s.nominee.phone()));
      if (hasNominee && !text(s.nominee.nomineeName())) e.nomineeName = 'Nominee name is required when nominee details are entered.';
      if (text(s.nominee.nomineeName()).length > 150) e.nomineeName = 'Nominee name cannot exceed 150 characters.';
      if (text(s.nominee.relationship()).length > 100) e.relationship = 'Relationship cannot exceed 100 characters.';
      if (s.nominee.dob() && s.nominee.dob() >= today()) e.nomineeDob = 'Nominee date of birth must be in the past.';
      if (text(s.nominee.phone()) && !/^[6-9][0-9]{9}$/.test(text(s.nominee.phone()))) e.nomineePhone = 'Enter a valid 10-digit Indian mobile number for the nominee.';
      const share = Number(s.nominee.sharePercentage());
      if (hasNominee && (!Number.isFinite(share) || share < 0.01 || share > 100)) e.sharePercentage = 'Nominee share must be between 0.01 and 100.';
      if (!s.setErrors(e)) return;

      const payload = {
        kycStatus: s.kyc.kycStatus(),
        kycDate: s.kyc.kycDate() || null,
        verifiedBy: s.verifiedBy(),
        riskLevel: s.kyc.riskLevel(),
        riskScore: score,
        expiryDate: null,
        remarks: text(s.kyc.remarks()) || null,
        updatedBy: s.verifiedBy(),
      };
      const result = await s.run(
        () => app.services.customers.createKyc(s.customer().customerId, payload),
        'KYC saved.',
      );
      if (!result) return;
      s.kycRecord(result);
      if (hasNominee) {
        const nominee = {
          nomineeName: text(s.nominee.nomineeName()),
          relationship: text(s.nominee.relationship()) || null,
          relationType: 'NOMINEE',
          dob: s.nominee.dob() || null,
          phone: text(s.nominee.phone()),
          address: null,
          sharePercentage: share,
          status: 'ACTIVE',
          updatedBy: s.verifiedBy(),
          startDate: today(),
          endDate: null,
        };
        const nomineeResult = await s.run(
          () => app.services.customers.nominee(s.customer().customerId, nominee),
          'Nominee saved.',
        );
        if (!nomineeResult) return;
      }
      s.step(5);
    };

    s.finishOnboarding = () => {
      app.notify('Onboarding details saved. Activate the verified customer from Customer Management.');
      app.go('customers');
    };

    s.resume = async () => {
      const value = text(s.resumeValue());
      if (!value) return s.setErrors({ resumeValue: 'Enter the customer ID, CIF, email, or phone to continue.' });
      const calls = {
        id: app.services.customers.get,
        cif: app.services.customers.byCif,
        email: app.services.customers.byEmail,
        phone: app.services.customers.byPhone,
      };
      const existing = await s.run(() => calls[s.resumeKind()](value));
      if (!existing) return;
      s.customer(existing);
      const kyc = await s.run(() => app.services.customers.kyc(existing.customerId));
      if (!kyc) return;
      s.kycRecord(kyc);
      if (kyc.kycStatus !== 'VERIFIED') return s.setErrors({ resumeValue: `Customer ${existing.cifNo} cannot continue because KYC is ${kyc.kycStatus}.` });
      if (existing.status !== 'ACTIVE') return s.setErrors({ resumeValue: `Customer ${existing.cifNo} is ${existing.status}. Activate the customer from Customer Management first.` });
      const products = await s.run(() => app.services.products.list());
      if (!products) return;
      s.products(products.filter((product) => product.status === 'ACTIVE'));
      if (!s.products().length) return s.setErrors({ resumeValue: 'No active banking products are available for account opening.' });
      s.account(null);
      s.step(6);
      app.notify(`Resumed account opening for ${existing.cifNo}.`);
    };

    s.openAccount = async () => {
      const e = {};
      const product = s.products().find((item) => String(item.productId) === String(s.accountForm.productId()));
      const accountNumber = text(s.accountForm.accountNumber());
      const openingBalance = Number(s.accountForm.availableBalance());
      if (!product) e.productId = 'Select an active banking product.';
      if (!accountNumber) e.accountNumber = 'Approved account number is required.';
      else if (accountNumber.length > 24) e.accountNumber = 'Account number cannot exceed 24 characters.';
      if (!Number.isFinite(openingBalance) || openingBalance < 0) e.availableBalance = 'Opening balance must be zero or a positive number.';
      if (!s.setErrors(e)) return;
      const payload = {
        accountNumber,
        customerId: String(s.customer().customerId),
        productId: String(product.productId),
        ownershipType: s.accountForm.ownershipType(),
        status: 'ACTIVE',
        currencyCode: product.currency,
        availableBalance: openingBalance,
        closedAt: null,
      };
      const result = await s.run(
        () => app.services.accounts.create(payload),
        'Account opened successfully.',
      );
      if (result) s.account(result);
    };
  }

  return VM;
});
