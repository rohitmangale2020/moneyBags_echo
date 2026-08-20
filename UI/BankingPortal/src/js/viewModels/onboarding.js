define(['knockout', 'appController', 'viewModels/indiaAddressOptions', 'ojs/ojinputtext', 'ojs/ojbutton', 'ojs/ojdatetimepicker'], function (ko, app, indiaAddress) {
  'use strict';

  const today = () => new Date().toISOString().slice(0, 10);
  const adultDobMax = () => {
    const date = new Date();
    date.setFullYear(date.getFullYear() - 18);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  };
  const text = (value) => String(value || '').trim();
  const digits = (value, maximumLength = 10) => String(value || '')
    .replace(/\D/g, '').slice(0, maximumLength);
  const inputElement = (event) => {
    if (event.target && String(event.target.tagName).toLowerCase() === 'input') return event.target;
    return event.currentTarget && event.currentTarget.querySelector
      ? event.currentTarget.querySelector('input') : null;
  };
  const digitsBeforeInput = (event, maximumLength = 10) => {
    if (String(event.inputType || '').startsWith('delete')) return true;
    const input = inputElement(event);
    if (!input || event.data === null || event.data === undefined) return true;
    const start = input.selectionStart === null ? input.value.length : input.selectionStart;
    const end = input.selectionEnd === null ? start : input.selectionEnd;
    const proposed = input.value.slice(0, start) + event.data + input.value.slice(end);
    if (/^\d*$/.test(proposed) && proposed.length <= maximumLength) return true;
    event.preventDefault();
    return false;
  };
  const digitsKeydown = (event) => {
    if (event.ctrlKey || event.metaKey || event.altKey
        || ['Backspace', 'Delete', 'Tab', 'Enter', 'Escape', 'ArrowLeft', 'ArrowRight',
          'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)
        || /^\d$/.test(event.key)) return true;
    event.preventDefault();
    return false;
  };
  const digitsPaste = (event, maximumLength = 10) => {
    const input = inputElement(event);
    if (!input || !event.clipboardData) return true;
    event.preventDefault();
    const start = input.selectionStart === null ? input.value.length : input.selectionStart;
    const end = input.selectionEnd === null ? start : input.selectionEnd;
    const next = input.value.slice(0, start)
      + event.clipboardData.getData('text') + input.value.slice(end);
    input.value = digits(next, maximumLength);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    return false;
  };
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
  const documentTypes = ['PAN', 'AADHAAR', 'PASSPORT', 'DRIVING_LICENSE', 'SALARY_SLIP', 'PHOTO', 'SIGNATURE'];

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
    s.permanentAddressRecord = ko.observable(null);
    s.currentAddressRecord = ko.observable(null);
    s.sameAsPermanentAddress = ko.observable(true);
    s.uploadedDocuments = ko.observableArray([]);
    s.uploadedDocumentCount = ko.pureComputed(() => s.uploadedDocuments().length);
    s.availableDocumentTypes = ko.pureComputed(() => {
      const usedTypes = new Set(s.uploadedDocuments().map((document) => document.documentType));
      const currentType = s.document.documentType();
      return documentTypes.filter((type) => type === currentType || !usedTypes.has(type));
    });
    s.kycRecord = ko.observable(null);
    s.kycSaved = ko.pureComputed(() => Boolean(s.kycRecord()));
    s.skippedKyc = ko.observable(false);
    s.resumedOnboarding = ko.observable(false);
    s.file = ko.observable(null);
    // Keep lookup neutral until the employee chooses the identifier type.
    // This also prevents a long default value from making the closed select look wider.
    s.resumeKind = ko.observable('');
    s.resumeValue = ko.observable('');
    s.verifiedBy = ko.pureComputed(() => String(app.session.userId() || ''));

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
      addressType: ko.observable('PERMANENT'),
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
    s.phoneBeforeInput = (_, event) => digitsBeforeInput(event);
    s.phoneKeydown = (_, event) => digitsKeydown(event);
    s.phonePaste = (_, event) => digitsPaste(event);
    s.profile.phone.subscribe((value) => {
      const sanitized = digits(value);
      if (String(value || '') !== sanitized) s.profile.phone(sanitized);
    });
    s.nominee.phone.subscribe((value) => {
      const sanitized = digits(value);
      if (String(value || '') !== sanitized) s.nominee.phone(sanitized);
    });
    s.currentAddress = {
      addressType: ko.observable('CURRENT'),
      line1: ko.observable(''),
      line2: ko.observable(''),
      city: ko.observable(''),
      state: ko.observable(''),
      country: ko.observable('India'),
      pincode: ko.observable(''),
    };
    s.addNominee = ko.observable(false);
    s.addNomineeAddress = ko.observable(false);
    s.indianStates = ko.observableArray(indiaAddress.states());
    s.addressDistricts = ko.pureComputed(() => { s.indianStates(); return indiaAddress.districts(s.address.state()); });
    s.currentAddressDistricts = ko.pureComputed(() => { s.indianStates(); return indiaAddress.districts(s.currentAddress.state()); });
    s.nomineeDistricts = ko.pureComputed(() => { s.indianStates(); return indiaAddress.districts(s.nominee.state()); });
    s.addressAreas = ko.pureComputed(() => indiaAddress.areas(s.address.state(), s.address.city()));
    s.currentAddressAreas = ko.pureComputed(() => indiaAddress.areas(s.currentAddress.state(), s.currentAddress.city()));
    s.nomineeAreas = ko.pureComputed(() => indiaAddress.areas(s.nominee.state(), s.nominee.city()));
    s.addressPincodes = ko.pureComputed(() => indiaAddress.pincodes(s.address.state(), s.address.city(), s.address.line2()));
    s.currentAddressPincodes = ko.pureComputed(() => indiaAddress.pincodes(s.currentAddress.state(), s.currentAddress.city(), s.currentAddress.line2()));
    s.nomineePincodes = ko.pureComputed(() => indiaAddress.pincodes(s.nominee.state(), s.nominee.city(), s.nominee.line2()));
    indiaAddress.load().then(() => s.indianStates(indiaAddress.states()));
    s.address.state.subscribe(() => s.address.city(''));
    s.currentAddress.state.subscribe(() => s.currentAddress.city(''));
    s.nominee.state.subscribe(() => s.nominee.city(''));
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
    s.editSection = (step) => {
      s.clearErrors();
      if (step === 4) s.skippedKyc(false);
      s.step(step);
    };
    // Returning to profile must validate and persist the current form values, not
    // merely move forward with the customer object that was saved earlier.
    s.nextFromProfile = () => s.createProfile();
    s.nextFromAddress = () => { s.clearErrors(); s.step(3); };
    s.skipDocuments = () => {
      s.clearErrors();
      s.kyc.kycStatus('PENDING');
      s.skippedKyc(true);
      s.step(5);
    };
    s.continueFromDocuments = () => {
      if (!s.uploadedDocuments().length) return s.skipDocuments();
      s.clearErrors();
      s.skippedKyc(false);
      s.step(4);
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
      const isUpdate = Boolean(s.customer());
      const result = await s.run(
        () => isUpdate
          ? app.services.customers.update(s.customer().customerId, payload)
          : app.services.customers.create(payload),
        isUpdate ? 'Customer profile updated.' : 'Customer created and CIF assigned.',
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
      const valuesFor = (form, addressType) => Object.fromEntries(
        Object.keys(form).map((key) => [key, key === 'addressType' ? addressType : text(form[key]())]),
      );
      const validate = async (values, prefix, label) => {
        if (!values.line1) e[`${prefix}line1`] = `${label} address line 1 is required.`;
        else if (values.line1.length > 250) e[`${prefix}line1`] = `${label} address line 1 cannot exceed 250 characters.`;
        if (values.line2.length > 250) e[`${prefix}line2`] = `${label} address line 2 cannot exceed 250 characters.`;
        ['city', 'state', 'country'].forEach((field) => {
          if (!values[field]) e[`${prefix}${field}`] = `${label} ${field} is required.`;
          else if (values[field].length > 100) e[`${prefix}${field}`] = `${label} ${field} cannot exceed 100 characters.`;
        });
        try {
          if (!await indiaAddress.validatePincode(values.state, values.city, values.pincode)) e[`${prefix}pincode`] = 'Enter a valid six-digit pincode.';
        } catch (error) { e[`${prefix}pincode`] = 'Enter a valid six-digit pincode.'; }
      };
      const permanent = valuesFor(s.address, 'PERMANENT');
      const current = s.sameAsPermanentAddress() ? Object.assign({}, permanent, { addressType: 'CURRENT' }) : valuesFor(s.currentAddress, 'CURRENT');
      await validate(permanent, 'permanent', 'Permanent');
      if (!s.sameAsPermanentAddress()) await validate(current, 'current', 'Current');
      if (!s.setErrors(e)) return;
      const result = await s.run(() => Promise.all([
        s.permanentAddressRecord()
          ? app.services.customers.updateAddress(s.customer().customerId, s.permanentAddressRecord().addressId, permanent)
          : app.services.customers.address(s.customer().customerId, permanent),
        s.currentAddressRecord()
          ? app.services.customers.updateAddress(s.customer().customerId, s.currentAddressRecord().addressId, current)
          : app.services.customers.address(s.customer().customerId, current),
      ]), 'Addresses saved.',
      );
      if (result) {
        s.permanentAddressRecord(result[0]);
        s.currentAddressRecord(result[1]);
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
    s.deleteDocument = async (record) => {
      if (!record || !record.docId) return;
      if (!window.confirm(`Delete the ${record.documentType} document? This cannot be undone.`)) return;
      await s.run(
        () => app.services.customers.deleteDocument(s.customer().customerId, record.docId),
        'Document deleted.',
      );
      if (s.error()) return;
      s.uploadedDocuments.remove((document) => document.docId === record.docId);
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
        s.document.documentType('');
        s.document.documentNumber('');
        s.document.issueDate('');
        s.document.expiryDate('');
        s.file(null);
        s.clearErrors();
      }
    };

    s.reviewOnboarding = async () => s.saveKyc(true);
    s.confirmOnboarding = async () => s.saveKyc(false);

    s.saveKyc = async (reviewOnly = false) => {
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
      const hasNominee = s.addNominee();
      const nomineeAddressStarted = hasNominee && s.addNomineeAddress();
      if (hasNominee && !text(s.nominee.nomineeName())) e.nomineeName = 'Nominee name is required when nominee details are entered.';
      if (hasNominee && text(s.nominee.nomineeName()).length > 150) e.nomineeName = 'Nominee name cannot exceed 150 characters.';
      if (hasNominee && !text(s.nominee.relationship())) e.relationship = 'Select the nominee relationship.';
      else if (hasNominee && text(s.nominee.relationship()).length > 100) e.relationship = 'Relationship cannot exceed 100 characters.';
      if (hasNominee && s.nominee.dob() && !dateValue(s.nominee.dob())) e.nomineeDob = 'Enter a valid nominee date of birth.';
      else if (hasNominee && s.nominee.dob() && s.nominee.dob() >= today()) e.nomineeDob = 'Nominee date of birth must be in the past.';
      else if (hasNominee && s.nominee.dob() && ageInYears(s.nominee.dob()) < 18) e.nomineeDob = 'Nominee must be at least 18 years old.';
      if (hasNominee && text(s.nominee.phone()) && !/^[6-9][0-9]{9}$/.test(text(s.nominee.phone()))) e.nomineePhone = 'Enter a valid 10-digit mobile number.';
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
        if (text(s.nominee.line2()).length > 250) e.nomineeLine2 = 'Area cannot exceed 250 characters.';
        if (text(s.nominee.pincode())) {
          try {
            if (!await indiaAddress.validatePincode(text(s.nominee.state()), text(s.nominee.city()), text(s.nominee.pincode()))) e.nomineePincode = 'Enter a valid six-digit pincode.';
          } catch (error) { e.nomineePincode = 'Enter a valid six-digit pincode.'; }
        }
      }
      if (!s.setErrors(e)) return;

      // Let the employee inspect every entered value before any nominee or KYC
      // data is sent to the services.
      if (reviewOnly) {
        s.step(5);
        return;
      }

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
      s.skippedKyc(false);
      s.step(5);
    };

    s.finishOnboarding = () => {
      app.notify('Onboarding finished. Review, verify, and activate the customer from Customer Management.');
      app.go('customers');
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
        s.addNominee(true);
        ['nomineeName', 'relationship', 'dob', 'phone', 'sharePercentage'].forEach((key) => {
          if (savedNominee[key] !== undefined && savedNominee[key] !== null) s.nominee[key](savedNominee[key]);
        });
      }
      const populateAddress = (form, savedAddress) => Object.keys(form).forEach((key) => {
        if (savedAddress && savedAddress[key] !== undefined && savedAddress[key] !== null) form[key](savedAddress[key]);
      });
      const permanentAddress = addresses.find((address) => address.addressType === 'PERMANENT') || addresses[0];
      const currentAddress = addresses.find((address) => address.addressType === 'CURRENT');
      if (permanentAddress) {
        s.permanentAddressRecord(permanentAddress);
        populateAddress(s.address, permanentAddress);
      }
      if (currentAddress) {
        s.currentAddressRecord(currentAddress);
        populateAddress(s.currentAddress, currentAddress);
        s.sameAsPermanentAddress(
          ['line1', 'line2', 'city', 'state', 'country', 'pincode'].every((key) =>
            String(permanentAddress?.[key] || '') === String(currentAddress[key] || ''),
          ),
        );
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

  }

  return VM;
});
