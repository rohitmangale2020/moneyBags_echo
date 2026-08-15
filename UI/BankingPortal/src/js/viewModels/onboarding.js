define(['knockout', 'appController', 'viewModels/indiaAddressOptions', 'ojs/ojinputtext', 'ojs/ojbutton', 'ojs/ojdatetimepicker'], function (ko, app, indiaAddress) {
  'use strict';

  const today = () => new Date().toISOString().slice(0, 10);
  const adultDobMax = () => {
    const date = new Date();
    date.setFullYear(date.getFullYear() - 18);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  };
  const text = (value) => String(value || '').trim();
  const dateValue = (value) => {
    const raw = text(value);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) return null;
    const date = new Date(`${raw}T00:00:00`);
    const normalized = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    return Number.isNaN(date.getTime()) || normalized !== raw ? null : date;
  };
  const ageInYears = (value) => {
    const dob = dateValue(value);
    if (!dob) return null;
    const now = new Date();
    let age = now.getFullYear() - dob.getFullYear();
    const birthdayNotReached =
      now.getMonth() < dob.getMonth() ||
      (now.getMonth() === dob.getMonth() && now.getDate() < dob.getDate());
    return birthdayNotReached ? age - 1 : age;
  };

  function VM() {
    const s = this;
    s.step = ko.observable(1);
    s.busy = ko.observable(false);
    s.error = ko.observable('');
    s.fieldErrors = ko.observable({});
    s.adultDobMax = adultDobMax();
    s.customer = ko.observable(null);
    s.profileSaved = ko.observable(false);
    s.addressSaved = ko.observable(false);
    s.uploadedDocuments = ko.observableArray([]);
    s.uploadedDocumentCount = ko.pureComputed(() => s.uploadedDocuments().length);
    s.kycRecord = ko.observable(null);
    s.kycSaved = ko.pureComputed(() => Boolean(s.kycRecord()));
    s.products = ko.observableArray([]);
    s.account = ko.observable(null);
    s.resumedOnboarding = ko.observable(false);
    s.file = ko.observable(null);
    // Keep lookup neutral until the employee chooses the identifier type.
    // This also prevents a long default value from making the closed select look wider.
    s.resumeKind = ko.observable('');
    s.resumeValue = ko.observable('');
    s.verifiedBy = ko.pureComputed(() => String(app.session.userId() || ''));
    s.canProceedToAccount = ko.pureComputed(() =>
      s.resumedOnboarding()
      && s.customer()
      && s.customer().status === 'ACTIVE'
      && s.kycRecord()
      && s.kycRecord().kycStatus === 'VERIFIED',
    );

    s.profile = {
      firstName: ko.observable(''),
      lastName: ko.observable(''),
      dob: ko.observable(''),
      gender: ko.observable(''),
      phone: ko.observable(''),
      email: ko.observable(''),
      occupation: ko.observable(''),
    };
    s.address = {
      addressType: ko.observable(''),
      line1: ko.observable(''),
      line2: ko.observable(''),
      city: ko.observable(''),
      state: ko.observable(''),
      country: ko.observable('India'),
      pincode: ko.observable(''),
    };
    s.document = {
      documentType: ko.observable(''),
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
      addressType: ko.observable(''),
      line1: ko.observable(''),
      line2: ko.observable(''),
      city: ko.observable(''),
      state: ko.observable(''),
      country: ko.observable(''),
      pincode: ko.observable(''),
    };
    s.indianStates = ko.observableArray(indiaAddress.states());
    s.addressDistricts = ko.pureComputed(() => { s.indianStates(); return indiaAddress.districts(s.address.state()); });
    s.nomineeDistricts = ko.pureComputed(() => { s.indianStates(); return indiaAddress.districts(s.nominee.state()); });
    indiaAddress.load().then(() => s.indianStates(indiaAddress.states()));
    s.address.state.subscribe(() => s.address.city(''));
    s.nominee.state.subscribe(() => s.nominee.city(''));
    s.accountForm = {
      accountNumber: ko.observable(''),
      productId: ko.observable(''),
      ownershipType: ko.observable('INDIVIDUAL'),
      availableBalance: ko.observable(0),
    };
    s.documentRequiresNumber = ko.pureComputed(() => {
      const type = s.document.documentType();
      return Boolean(type) && !['PHOTO', 'SIGNATURE', 'SALARY_SLIP'].includes(type);
    });
    s.documentShowsIssueDate = ko.pureComputed(() =>
      ['PASSPORT', 'DRIVING_LICENSE'].includes(s.document.documentType()),
    );
    s.documentRequiresExpiry = ko.pureComputed(() =>
      ['PASSPORT', 'DRIVING_LICENSE'].includes(s.document.documentType()),
    );
    s.documentFileAccept = ko.pureComputed(() =>
      ['PHOTO', 'SIGNATURE'].includes(s.document.documentType()) ? '.png,.jpg,.jpeg' : 'application/pdf,.pdf',
    );
    s.document.documentType.subscribe(() => {
      if (!s.documentRequiresNumber()) s.document.documentNumber('');
      if (!s.documentShowsIssueDate()) s.document.issueDate('');
      if (!s.documentRequiresExpiry()) {
        s.document.expiryDate('');
        const errors = Object.assign({}, s.fieldErrors());
        delete errors.expiryDate;
        s.fieldErrors(errors);
      }
    });

    s.setErrors = (errors) => {
      s.fieldErrors(errors);
      const first = Object.values(errors)[0];
      // Validation details belong beside their fields; reserve the top alert for API failures.
      s.error('');
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
    s.nextFromProfile = () => { s.clearErrors(); s.step(2); };
    s.nextFromAddress = () => { s.clearErrors(); s.step(3); };
    s.skipDocuments = () => { s.clearErrors(); s.step(4); };
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
      if (!lastName) e.lastName = 'Last name is required.';
      else if (lastName.length > 100) e.lastName = 'Last name cannot exceed 100 characters.';
      if (!s.profile.dob()) e.dob = 'Date of birth is required.';
      else if (!dateValue(s.profile.dob())) e.dob = 'Enter a valid date of birth.';
      else if (s.profile.dob() >= today()) e.dob = 'Date of birth must be in the past.';
      else if (ageInYears(s.profile.dob()) < 18) e.dob = 'Customer must be at least 18 years old.';
      else if (ageInYears(s.profile.dob()) > 120) e.dob = 'Enter a realistic date of birth.';
      if (!s.profile.gender()) e.gender = 'Select a gender.';


      if (!phone) e.phone = 'Mobile number is required.';
      else if (!/^[6-9][0-9]{9}$/.test(phone)) e.phone = 'Enter a valid 10-digit mobile number.';
      if (!email) e.email = 'Email address is required.';
      else if (!/^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/.test(email))
        e.email = 'Enter a valid email address.';
      else if (email.length > 254) e.email = 'Email cannot exceed 254 characters.';
      if (!occupation) e.occupation = 'Occupation is required.';
      else if (occupation.length > 100) e.occupation = 'Occupation cannot exceed 100 characters.';
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
        app.setActiveCustomer(result);
        s.resumedOnboarding(false);
        s.profileSaved(true);
        s.step(2);
      }
    };

    s.saveAddress = async () => {
      const e = {};
      const values = {};
      Object.keys(s.address).forEach((key) => (values[key] = text(s.address[key]())));
      if (!values.addressType) e.addressType = 'Select an address type.';
      if (!values.line1) e.line1 = 'Address line 1 is required.';
      else if (values.line1.length > 250) e.line1 = 'Address line 1 cannot exceed 250 characters.';
      if (values.line2.length > 250) e.line2 = 'Address line 2 cannot exceed 250 characters.';
      ['city', 'state', 'country'].forEach((field) => {
        if (!values[field]) e[field] = `${field[0].toUpperCase()}${field.slice(1)} is required.`;
        else if (values[field].length > 100) e[field] = `${field[0].toUpperCase()}${field.slice(1)} cannot exceed 100 characters.`;
      });
      try {
        if (!await indiaAddress.validatePincode(values.state, values.city, values.pincode)) e.pincode = 'Enter a pincode valid for the selected state and district.';
      } catch (error) { e.pincode = 'PIN validation is temporarily unavailable. Please try again.'; }
      if (!s.setErrors(e)) return;
      const result = await s.run(
        () => app.services.customers.address(s.customer().customerId, values),
        'Address saved.',
      );
      if (result) {
        s.addressSaved(true);
        s.step(3);
      }
    };

    s.choose = (_, event) => {
      const selected = event.target.files && event.target.files[0];
      const imageDocument = ['PHOTO', 'SIGNATURE'].includes(s.document.documentType());
      const name = String((selected && selected.name) || '').toLowerCase();
      const valid = imageDocument ? /\.(png|jpe?g)$/.test(name) : /\.pdf$/.test(name);
      s.file(valid ? selected : null);
      if (selected && !valid) s.fieldErrors(Object.assign({}, s.fieldErrors(), { file: imageDocument ? 'Upload a PNG or JPEG image.' : 'Upload a PDF file.' }));
      if (s.file()) s.fieldErrors(Object.assign({}, s.fieldErrors(), { file: '' }));
    };
    s.upload = async () => {
      const e = {};
      const type = s.document.documentType();
      const number = text(s.document.documentNumber()).toUpperCase();
      if (!type) e.documentType = 'Select a document type.';
      if (s.documentRequiresNumber() && !number) e.documentNumber = 'Document number is required.';
      else if (number.length > 100) e.documentNumber = 'Document number cannot exceed 100 characters.';
      else if (type === 'PAN' && !/^[A-Z]{5}[0-9]{4}[A-Z]$/.test(number)) e.documentNumber = 'Enter a PAN in the format AAAAA0000A.';
      else if (type === 'AADHAAR' && !/^[2-9][0-9]{3}[0-9]{4}[0-9]{4}$/.test(number)) e.documentNumber = 'Enter a valid 12-digit Aadhaar number.';
      if (!s.file()) e.file = 'Choose a PDF or image to upload.';
      if (s.documentShowsIssueDate() && s.document.issueDate() && !dateValue(s.document.issueDate())) e.issueDate = 'Enter a valid issue date.';
      else if (s.documentShowsIssueDate() && s.document.issueDate() > today()) e.issueDate = 'Issue date cannot be in the future.';
      if (s.documentRequiresExpiry() && !s.document.expiryDate()) e.expiryDate = 'Expiry date is required for this document type.';
      else if (s.documentRequiresExpiry() && !dateValue(s.document.expiryDate())) e.expiryDate = 'Enter a valid expiry date.';
      else if (s.documentRequiresExpiry() && s.document.expiryDate() <= today()) e.expiryDate = 'Expiry date must be in the future.';
      else if (s.documentRequiresExpiry() && s.document.issueDate() && s.document.expiryDate() < s.document.issueDate()) e.expiryDate = 'Expiry date cannot be earlier than the issue date.';
      if (!s.setErrors(e)) return;
      const payload = {
        documentType: type,
        documentNumber: s.documentRequiresNumber() ? number : null,
        issueDate: s.documentShowsIssueDate() ? s.document.issueDate() || null : null,
        expiryDate: s.documentRequiresExpiry() ? s.document.expiryDate() : null,
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
      if (result) {
        s.uploadedDocuments.push(result);
        s.document.documentNumber('');
        s.document.issueDate('');
        s.document.expiryDate('');
        s.file(null);
        s.clearErrors();
      }
    };

    s.saveKyc = async () => {
      // A saved assessment is retained when navigating back from review. Continue
      // forward instead of submitting a second KYC record for the same customer.
      if (s.kycSaved()) {
        s.clearErrors();
        s.step(5);
        return;
      }
      if (!s.addressSaved()) return s.error('Add the customer address before verifying KYC.');
      if (!s.uploadedDocuments().length) return s.error('Upload at least one document before verifying KYC.');
      const e = {};
      const score = 0;
      if (!s.kyc.kycDate()) e.kycDate = 'KYC date is required.';
      else if (!dateValue(s.kyc.kycDate())) e.kycDate = 'Enter a valid KYC date.';
      else if (s.kyc.kycDate() > today()) e.kycDate = 'KYC date cannot be in the future.';
      if (text(s.kyc.remarks()).length > 500) e.remarks = 'Remarks cannot exceed 500 characters.';
      const nomineeAddressStarted = Boolean(text(s.nominee.line1()) || text(s.nominee.line2()) || text(s.nominee.city()) || text(s.nominee.state()) || text(s.nominee.country()) || text(s.nominee.pincode()));
      const hasNominee = Boolean(text(s.nominee.nomineeName()) || text(s.nominee.relationship()) || text(s.nominee.dob()) || text(s.nominee.phone()) || nomineeAddressStarted);
      if (hasNominee && !text(s.nominee.nomineeName())) e.nomineeName = 'Nominee name is required when nominee details are entered.';
      if (text(s.nominee.nomineeName()).length > 150) e.nomineeName = 'Nominee name cannot exceed 150 characters.';
      if (hasNominee && !text(s.nominee.relationship())) e.relationship = 'Select the nominee relationship.';
      else if (text(s.nominee.relationship()).length > 100) e.relationship = 'Relationship cannot exceed 100 characters.';
      if (s.nominee.dob() && !dateValue(s.nominee.dob())) e.nomineeDob = 'Enter a valid nominee date of birth.';
      else if (s.nominee.dob() && s.nominee.dob() >= today()) e.nomineeDob = 'Nominee date of birth must be in the past.';
      if (text(s.nominee.phone()) && !/^[6-9][0-9]{9}$/.test(text(s.nominee.phone()))) e.nomineePhone = 'Enter a valid 10-digit mobile number.';
      const share = Number(s.nominee.sharePercentage());
      if (hasNominee && (!Number.isFinite(share) || share < 0.01 || share > 100)) e.sharePercentage = 'Nominee share must be between 0.01 and 100.';
      if (nomineeAddressStarted) {
        if (!text(s.nominee.addressType())) e.nomineeAddressType = 'Select an address type for the nominee.';
        const nomineeAddress = ['line1', 'city', 'state', 'country', 'pincode'];
        nomineeAddress.forEach((field) => {
          const value = text(s.nominee[field]());
          const label = field === 'line1' ? 'Address line 1' : field[0].toUpperCase() + field.slice(1);
          if (!value) e[`nominee${field[0].toUpperCase()}${field.slice(1)}`] = `${label} is required for the nominee address.`;
          else if (field !== 'pincode' && value.length > (field === 'line1' ? 250 : 100)) e[`nominee${field[0].toUpperCase()}${field.slice(1)}`] = `${label} is too long.`;
        });
        if (text(s.nominee.line2()).length > 250) e.nomineeLine2 = 'Address line 2 cannot exceed 250 characters.';
        if (text(s.nominee.pincode())) {
          try {
            if (!await indiaAddress.validatePincode(text(s.nominee.state()), text(s.nominee.city()), text(s.nominee.pincode()))) e.nomineePincode = 'Enter a pincode valid for the selected state and district.';
          } catch (error) { e.nomineePincode = 'PIN validation is temporarily unavailable. Please try again.'; }
        }
      }
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
      if (hasNominee) {
        const nominee = {
          nomineeName: text(s.nominee.nomineeName()),
          relationship: text(s.nominee.relationship()) || null,
          relationType: 'NOMINEE',
          dob: s.nominee.dob() || null,
          phone: text(s.nominee.phone()),
          address: nomineeAddressStarted ? {
            addressType: s.nominee.addressType(),
            line1: text(s.nominee.line1()),
            line2: text(s.nominee.line2()) || null,
            city: text(s.nominee.city()),
            state: text(s.nominee.state()),
            country: text(s.nominee.country()),
            pincode: text(s.nominee.pincode()),
          } : null,
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
      const result = await s.run(
        () => app.services.customers.createKyc(s.customer().customerId, payload),
        'KYC saved.',
      );
      if (!result) return;
      s.kycRecord(result);
      s.step(5);
    };

    s.finishOnboarding = () => {
      app.notify('Onboarding details saved. Activate the verified customer from Customer Management.');
      app.go('customers');
    };
    s.proceedToAccount = async () => {
      if (!s.canProceedToAccount()) return;
      const products = await s.run(() => app.services.products.list());
      if (!products) return;
      s.products(products.filter((product) => product.status === 'ACTIVE'));
      if (!s.products().length) return s.setErrors({ resumeValue: 'No active banking products are available for account opening.' });
      s.account(null);
      s.step(6);
    };

    s.resume = async () => {
      const value = text(s.resumeValue());
      if (!s.resumeKind()) return s.setErrors({ resumeValue: 'Select how you want to find the customer.' });
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
      app.setActiveCustomer(existing);
      s.resumedOnboarding(true);
      s.profileSaved(true);
      Object.keys(s.profile).forEach((key) => s.profile[key](existing[key] || ''));
      const records = await s.run(() => Promise.allSettled([
        app.services.customers.addresses(existing.customerId),
        app.services.customers.documents(existing.customerId),
        app.services.customers.kyc(existing.customerId),
        app.services.customers.nominees(existing.customerId),
      ]));
      if (!records) return;
      const valueOf = (result, fallback) => result.status === 'fulfilled' ? result.value : fallback;
      const addresses = valueOf(records[0], []);
      const documents = valueOf(records[1], []);
      const kyc = valueOf(records[2], null);
      const nominees = valueOf(records[3], []);
      if (kyc) {
        ['kycStatus', 'kycDate', 'riskLevel', 'riskScore', 'remarks'].forEach((key) => {
          if (kyc[key] !== undefined && kyc[key] !== null && s.kyc[key]) s.kyc[key](kyc[key]);
        });
      }
      if (nominees.length) {
        const savedNominee = nominees[0];
        ['nomineeName', 'relationship', 'dob', 'phone', 'sharePercentage'].forEach((key) => {
          if (savedNominee[key] !== undefined && savedNominee[key] !== null) s.nominee[key](savedNominee[key]);
        });
      }
      s.addressSaved(addresses.length > 0);
      s.uploadedDocuments(documents);
      if (!addresses.length) s.step(2);
      else if (!documents.length) s.step(3);
      else if (!kyc) s.step(4);
      else {
        s.kycRecord(kyc);
        s.step(5);
      }
      app.notify(`Resumed onboarding for ${existing.cifNo}.`);
    };

    s.openAccount = async () => {
      if (!s.kycRecord() || s.kycRecord().kycStatus !== 'VERIFIED' || s.customer().status !== 'ACTIVE') {
        return s.setErrors({ productId: 'An account can be opened only after the customer is active and KYC is verified.' });
      }
      const e = {};
      const product = s.products().find((item) => String(item.productId) === String(s.accountForm.productId()));
      const openingBalance = Number(s.accountForm.availableBalance());
      if (!product) e.productId = 'Select an active banking product.';
      if (!Number.isFinite(openingBalance) || openingBalance < 0) e.availableBalance = 'Opening balance must be zero or a positive number.';
      if (!s.setErrors(e)) return;
      const payload = {
        accountNumber: null,
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
