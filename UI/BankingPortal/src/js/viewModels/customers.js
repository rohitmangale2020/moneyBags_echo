define([
  'knockout',
  'appController',
  'viewModels/util',
  'viewModels/indiaAddressOptions',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojdialog',
], function (ko, app, u, indiaAddress) {
  'use strict';

  const customerBlank = () => ({
    firstName: '', lastName: '', dob: '', gender: 'MALE', phone: '', email: '', occupation: '',
  });
  const addressBlank = () => ({
    addressId: null, addressType: 'CURRENT', line1: '', line2: '', city: '', state: '', country: 'India', pincode: '',
  });
  const kycBlank = () => ({
    kycStatus: 'PENDING', kycDate: '', verifiedBy: '', riskLevel: 'LOW', riskScore: 0, expiryDate: '', remarks: '', updatedBy: '',
  });
  const documentBlank = () => ({
    docId: null, documentType: '', documentNumber: '', issueDate: '', expiryDate: '', status: 'UPLOADED', verifiedBy: '', rejectedReason: '', remarks: '', updatedBy: '',
  });
  const nomineeBlank = () => ({
    nomineeId: null, nomineeName: '', relationship: '', relationType: 'NOMINEE', dob: '', phone: '', sharePercentage: 100, status: 'ACTIVE', updatedBy: '', startDate: '', endDate: '', includeAddress: false, address: addressBlank(),
  });
  function clean(value) {
    const result = {};
    Object.keys(value).forEach((key) => {
      const item = value[key];
      result[key] = item === '' ? null : item;
    });
    return result;
  }

  function VM() {
    const s = this;
    s.isEmployee = ko.pureComputed(() => app.session.role() === 'EMPLOYEE');
    s.openOnboarding = () => app.go('onboarding');
    s.state = u.state([]);
    s.currentPage = ko.observable(0);
    s.totalPages = ko.observable(1);
    s.totalCustomers = ko.observable(0);
    s.pageSize = 10;
    s.directoryRequest = 0;
    s.detailState = u.state({ addresses: [], documents: [], nominees: [], accounts: [], kyc: null });
    s.showDetailPage = ko.observable(false);
    s.query = ko.observable('');
    s.searchMode = ko.observable('local');
    s.statusFilter = ko.observable('');
    s.selected = ko.observable(null);
    s.form = ko.observable(customerBlank());
    s.addressForm = ko.observable(addressBlank());
    s.documentRequiresExpiry = ko.observable(false);
    s.documentRequiresNumber = ko.observable(false);
    s.documentShowsIssueDate = ko.observable(false);
    s.documentFileAccept = ko.observable('application/pdf,.pdf');
    s.setDocumentExpiryRequirement = (type) => {
      const documentType = String(type || '');
      const needsExpiry = ['PASSPORT', 'DRIVING_LICENSE'].includes(documentType);
      const needsNumber = Boolean(documentType) && !['PHOTO', 'SIGNATURE', 'SALARY_SLIP'].includes(documentType);
      const showsIssueDate = !['PHOTO', 'SIGNATURE'].includes(documentType);
      s.documentRequiresExpiry(needsExpiry);
      s.documentRequiresNumber(needsNumber);
      s.documentShowsIssueDate(showsIssueDate);
      s.documentFileAccept(['PHOTO', 'SIGNATURE'].includes(documentType) ? '.png,.jpg,.jpeg' : 'application/pdf,.pdf');
      if (!needsNumber && s.documentForm()) s.documentForm().documentNumber = '';
      if (!showsIssueDate && s.documentForm()) s.documentForm().issueDate = '';
      if (!needsExpiry && s.documentForm()) s.documentForm().expiryDate = '';
    };
    s.onDocumentTypeChange = (_, event) => s.setDocumentExpiryRequirement(event.target.value);
    s.kycForm = ko.observable(kycBlank());
    s.documentForm = ko.observable(documentBlank());
    s.nomineeForm = ko.observable(nomineeBlank());
    s.includeNomineeAddress = ko.observable(false);
    s.indianStates = ko.observableArray(indiaAddress.states());
    s.nomineeAddressState = ko.observable('');
    s.settingNomineeAddress = false;
    s.nomineeAddressDistricts = ko.pureComputed(() => {
      s.indianStates();
      return indiaAddress.districts(s.nomineeAddressState());
    });
    indiaAddress.load().then(() => s.indianStates(indiaAddress.states()));
    s.nomineeAddressState.subscribe((state) => {
      const form = s.nomineeForm();
      if (!form || !form.address) return;
      form.address.state = state;
      if (!s.settingNomineeAddress) form.address.city = '';
      s.nomineeForm.valueHasMutated();
    });
    s.documentFile = ko.observable(null);
    s.activationNotice = ko.observable('');
    s.accountProducts = ko.observableArray([]);
    s.accountForm = {
      productId: ko.observable(''),
      ownershipType: ko.observable('INDIVIDUAL'),
      availableBalance: ko.observable(0),
    };
    s.error = ko.observable('');
    s.date = u.date;
    s.filtered = ko.pureComputed(() => {
      const q = s.query().toLowerCase();
      return s.state.data().filter((x) => !q || `${x.cifNo} ${x.firstName} ${x.lastName || ''} ${x.email || ''} ${x.phone}`.toLowerCase().includes(q));
    });
    s.orderedCustomers = ko.pureComputed(() => s.filtered().slice().sort((a, b) => {
      if (a.status === 'ACTIVE' && b.status !== 'ACTIVE') return -1;
      if (b.status === 'ACTIVE' && a.status !== 'ACTIVE') return 1;
      return 0;
    }));
    s.kycVerified = ko.pureComputed(() =>
      String((s.detailState.data().kyc || {}).kycStatus || '').toUpperCase() === 'VERIFIED',
    );
    s.accountActionLabel = ko.pureComputed(() => s.kycVerified() ? 'Add account' : 'Add account (KYC required)');
    s.load = async (requestedPage = 0) => {
      const requestId = ++s.directoryRequest;
      const page = Number.isInteger(requestedPage) ? requestedPage : 0;
      const status = s.statusFilter();
      s.state.loading(true);
      s.state.error('');
      try {
        const response = await app.services.customers.list(page, s.pageSize, status);
        if (requestId !== s.directoryRequest) return s.state.data();
        const customers = u.list(response);
        s.currentPage(Number(response.number || 0));
        s.totalPages(Number(response.totalPages === undefined ? 1 : response.totalPages));
        s.totalCustomers(Number(response.totalElements === undefined ? customers.length : response.totalElements));
        s.state.data(customers);
        return customers;
      } catch (error) {
        if (requestId === s.directoryRequest) s.state.error(error.message);
        return null;
      } finally {
        if (requestId === s.directoryRequest) s.state.loading(false);
      }
    };
    s.previousPage = () => { if (!s.state.loading() && s.currentPage() > 0) s.load(s.currentPage() - 1); };
    s.nextPage = () => { if (!s.state.loading() && s.currentPage() < s.totalPages() - 1) s.load(s.currentPage() + 1); };
    s.statusFilter.subscribe(() => {
      s.query('');
      s.searchMode('local');
      s.load(0);
    });
    s.backendSearch = () => {
      const q = s.query().trim();
      if (!q || s.searchMode() === 'local') return s.load(0);
      const calls = {
        cif: app.services.customers.byCif,
        email: app.services.customers.byEmail,
        phone: app.services.customers.byPhone,
        firstName: app.services.customers.byFirstName,
      };
      s.state.run(async () => {
        const value = await calls[s.searchMode()](q);
        return Array.isArray(value) ? value : [value];
      }).catch(() => null);
    };
    s.loadDetail = async (customer) => {
      const id = customer.customerId;
      s.selected(await app.services.customers.get(id));
      app.setActiveCustomer(s.selected());
      return s.detailState.run(async () => {
        const results = await Promise.allSettled([
          app.services.customers.addresses(id),
          app.services.customers.documents(id),
          app.services.customers.nominees(id),
          app.services.customers.kyc(id),
          app.services.accounts.customer(id),
          app.services.products.list(),
        ]);
        const products = results[5].status === 'fulfilled' ? results[5].value : [];
        const productNames = new Map(products.map((product) => [String(product.productId), product.productName]));
        return {
          addresses: results[0].status === 'fulfilled' ? results[0].value : [],
          documents: results[1].status === 'fulfilled' ? results[1].value : [],
          nominees: results[2].status === 'fulfilled' ? results[2].value : [],
          kyc: results[3].status === 'fulfilled' ? results[3].value : null,
          accounts: results[4].status === 'fulfilled'
            ? results[4].value.map((account) => Object.assign({}, account, {
              productName: account.productName || productNames.get(String(account.productId)) || 'Product unavailable',
            }))
            : [],
        };
      });
    };
    s.openDetail = async (customer) => {
      s.error('');
      try { await s.loadDetail(customer); s.showDetailPage(true); } catch (e) { s.error(e.message); }
    };
    s.backToDirectory = () => { s.error(''); s.showDetailPage(false); };
    s.editCustomer = () => {
      s.form(Object.assign(customerBlank(), ko.toJS(s.selected())));
      s.error('');
      document.getElementById('customerEditDialog').open();
    };
    s.saveCustomer = async () => {
      const raw = ko.toJS(s.form()), d = clean({ firstName: raw.firstName, lastName: raw.lastName, dob: raw.dob, gender: raw.gender, phone: raw.phone, email: raw.email, occupation: raw.occupation });
      if (!d.firstName || !d.dob || !d.gender || !d.phone) return s.error('First name, birth date, gender, and phone are required.');
      if (!/^[6-9][0-9]{9}$/.test(d.phone))
        return s.error('Phone must be a valid 10-digit mobile number.');

      if (new Date(d.dob) >= new Date())
        return s.error('Date of birth must be in the past.');

      if (d.email && !/^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/.test(d.email))
        return s.error('Enter a valid email address.');

      try {
        const value = await app.services.customers.update(s.selected().customerId, d);
        s.selected(value);
        document.getElementById('customerEditDialog').close();
        app.notify('Customer profile updated.'); s.load();
      } catch (e) { s.error(e.message); }
    };
    s.status = async (x) => {
      try {
        if (x.status !== 'ACTIVE') {
          let kyc = null;
          try { kyc = await app.services.customers.kyc(x.customerId); } catch (e) { /* KYC has not been created yet. */ }
          if (String((kyc || {}).kycStatus || '').toUpperCase() !== 'VERIFIED') {
            s.activationNotice(`Customer ${x.cifNo} cannot be activated yet. Complete and verify KYC first, then activate the customer.`);
            document.getElementById('activationRequirementsDialog').open();
            return;
          }
        }
        const value = x.status === 'ACTIVE' ? await app.services.customers.deactivate(x.customerId) : await app.services.customers.activate(x.customerId);
        if (s.selected() && s.selected().customerId === x.customerId) s.selected(value);
        app.notify(`Customer ${x.status === 'ACTIVE' ? 'deactivated' : 'activated'}.`); s.load(s.currentPage());
      } catch (e) { app.notify(e.message, 'error'); }
    };
    s.openAccountForCustomer = async () => {
      if (!s.kycVerified()) {
        return app.notify('Verify KYC first. An account or product can only be added after KYC is verified.', 'warning');
      }
      s.error('');
      s.accountForm.productId('');
      s.accountForm.ownershipType('INDIVIDUAL');
      s.accountForm.availableBalance(0);
      try {
        s.accountProducts((await app.services.products.list()).filter((product) => product.status === 'ACTIVE'));
        if (!s.accountProducts().length) return s.error('No active banking products are available for account opening.');
        document.getElementById('customerAccountDialog').open();
      } catch (e) { s.error(e.message); }
    };
    s.createAccount = async () => {
      const product = s.accountProducts().find((item) => String(item.productId) === String(s.accountForm.productId()));
      const openingBalance = Number(s.accountForm.availableBalance());
      if (!s.kycVerified()) return s.error('Verify KYC first. An account cannot be opened before KYC is verified.');
      if (!product) return s.error('Select an active banking product.');
      if (!Number.isFinite(openingBalance) || openingBalance < 0) return s.error('Opening balance must be zero or a positive number.');
      try {
        await app.services.accounts.create({
          customerId: String(s.selected().customerId),
          productId: String(product.productId),
          ownershipType: s.accountForm.ownershipType(),
          status: 'ACTIVE',
          currencyCode: product.currency,
          availableBalance: openingBalance,
          closedAt: null,
        });
        document.getElementById('customerAccountDialog').close();
        app.notify('Account opened successfully.');
        await s.loadDetail(s.selected());
      } catch (e) { s.error(e.message); }
    };
    s.remove = async (x) => {
      if (!window.confirm(`Delete customer ${x.cifNo}? This calls the backend DELETE operation.`)) return;
      try { await app.services.customers.remove(x.customerId); s.showDetailPage(false); app.notify('Customer deleted.'); s.load(); }
      catch (e) { app.notify(e.message, 'error'); }
    };
    s.openAddress = async (x) => { s.error(''); try { const value = x ? await app.services.customers.getAddress(s.selected().customerId, x.addressId) : null; s.addressForm(Object.assign(addressBlank(), value || {})); document.getElementById('addressDialog').open(); } catch (e) { app.notify(e.message, 'error'); } };
    s.saveAddress = async () => {
      const raw = ko.toJS(s.addressForm()), addressId = raw.addressId, d = clean({ addressType: raw.addressType, line1: raw.line1, line2: raw.line2, city: raw.city, state: raw.state, country: raw.country, pincode: raw.pincode }), id = s.selected().customerId;
      if (!d.line1 || !d.city || !d.state || !d.country || !/^[1-9][0-9]{5}$/.test(d.pincode || '')) return s.error('Complete the address and enter a valid six-digit pincode.');
      try { addressId ? await app.services.customers.updateAddress(id, addressId, d) : await app.services.customers.address(id, d); document.getElementById('addressDialog').close(); app.notify('Address saved.'); await s.loadDetail(s.selected()); }
      catch (e) { s.error(e.message); }
    };
    s.deleteAddress = async (x) => {
      if ((s.detailState.data().addresses || []).length <= 1) {
        return app.notify('A customer must have at least one address.', 'warning');
      }
      if (!window.confirm('Delete this address?')) return;
      try { await app.services.customers.deleteAddress(s.selected().customerId, x.addressId); app.notify('Address deleted.'); await s.loadDetail(s.selected()); } catch (e) { app.notify(e.message, 'error'); }
    };
    s.openKyc = () => {
      const form = Object.assign(kycBlank(), s.detailState.data().kyc || {});
      form.verifiedBy = String(app.session.userId() || '');
      form.updatedBy = String(app.session.userId() || '');
      s.kycForm(form);
      s.error('');
      document.getElementById('kycDialog').open();
    };
    s.saveKyc = async () => {
      const raw = ko.toJS(s.kycForm()), d = clean({ kycStatus: raw.kycStatus, kycDate: raw.kycDate, verifiedBy: raw.verifiedBy, riskLevel: 'LOW', riskScore: 0, expiryDate: raw.expiryDate, remarks: raw.remarks, updatedBy: raw.updatedBy }), id = s.selected().customerId;
      if (String(d.kycStatus).toUpperCase() === 'VERIFIED') {
        if (!(s.detailState.data().addresses || []).length) return s.error('Add at least one customer address before verifying KYC.');
        if (!(s.detailState.data().documents || []).length) return s.error('Upload at least one customer document before verifying KYC.');
      }
      if (d.kycDate && new Date(d.kycDate) > new Date()) return s.error('KYC date cannot be in the future.');
      d.riskScore = 0;
      try { s.detailState.data().kyc ? await app.services.customers.updateKyc(id, d) : await app.services.customers.createKyc(id, d); document.getElementById('kycDialog').close(); app.notify('KYC record saved.'); await s.loadDetail(s.selected()); }
      catch (e) { s.error(e.message); }
    };
    s.openDocument = async (x) => { s.error(''); try { const value = x ? await app.services.customers.getDocument(s.selected().customerId, x.docId) : null; const form = Object.assign(documentBlank(), value || {}); s.documentForm(form); s.setDocumentExpiryRequirement(form.documentType); s.documentFile(null); document.getElementById('documentDialog').open(); } catch (e) { app.notify(e.message, 'error'); } };
    s.pickFile = (_, event) => {
      const file = event.target.files && event.target.files[0];
      const imageDocument = ['PHOTO', 'SIGNATURE'].includes(s.documentForm().documentType);
      const valid = !file || (imageDocument ? /\.(png|jpe?g)$/i : /\.pdf$/i).test(file.name);
      s.documentFile(valid ? file : null);
      if (file && !valid) s.error(imageDocument ? 'Upload a PNG or JPEG image.' : 'Upload a PDF file.');
    };
    s.saveDocument = async () => {
      const raw = ko.toJS(s.documentForm()), docId = raw.docId, d = clean({ documentType: raw.documentType, documentNumber: s.documentRequiresNumber() ? raw.documentNumber : null, issueDate: s.documentShowsIssueDate() ? raw.issueDate : null, expiryDate: s.documentRequiresExpiry() ? raw.expiryDate : null, status: raw.status, verifiedBy: null, rejectedReason: raw.rejectedReason, remarks: raw.remarks, updatedBy: raw.updatedBy }), id = s.selected().customerId, file = s.documentFile();
      if (!d.documentType) return s.error('Select a document type.');
      if ((s.documentRequiresNumber() && !d.documentNumber) || (!docId && !file)) return s.error(s.documentRequiresNumber() ? 'Document number and file are required.' : 'A document file is required.');
      if (d.issueDate && new Date(d.issueDate) > new Date()) return s.error('Document issue date cannot be in the future.');
      if (s.documentRequiresExpiry() && !d.expiryDate) return s.error('Expiry date is required for this document type.');
      if (d.expiryDate && new Date(d.expiryDate) <= new Date()) return s.error('Expiry date must be in the future.');
      try { docId ? await app.services.customers.updateDocument(id, docId, file, d) : await app.services.customers.document(id, file, d); document.getElementById('documentDialog').close(); app.notify('Document saved.'); await s.loadDetail(s.selected()); }
      catch (e) { s.error(e.message); }
    };
    s.openNominee = async (x) => {
      s.error('');
      try {
        const value = x
          ? await app.services.customers.getNominee(s.selected().customerId, x.nomineeId)
          : null;
        const form = Object.assign(nomineeBlank(), value || {});
        s.includeNomineeAddress(Boolean(value && value.address));
        form.address = Object.assign(addressBlank(), (value && value.address) || {});
        s.nomineeForm(form);
        s.settingNomineeAddress = true;
        s.nomineeAddressState(form.address.state || '');
        s.settingNomineeAddress = false;
        document.getElementById('nomineeDialog').open();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.saveNominee = async () => {
      const raw = ko.toJS(s.nomineeForm()), nomineeId = raw.nomineeId, d = clean({ nomineeName: raw.nomineeName, relationship: raw.relationship, relationType: raw.relationType, dob: raw.dob, phone: raw.phone, address: s.includeNomineeAddress() ? clean({ addressType: raw.address.addressType, line1: raw.address.line1, line2: raw.address.line2, city: raw.address.city, state: raw.address.state, country: raw.address.country, pincode: raw.address.pincode }) : null, sharePercentage: raw.sharePercentage, status: raw.status, updatedBy: raw.updatedBy, startDate: raw.startDate, endDate: raw.endDate }), id = s.selected().customerId;
      d.sharePercentage = Number(d.sharePercentage);
      if (!d.nomineeName || d.sharePercentage <= 0 || d.sharePercentage > 100) return s.error('Enter a nominee name and share between 0.01 and 100.');
      const allocatedShare = (s.detailState.data().nominees || [])
        .filter((nominee) => nominee.nomineeId !== nomineeId && String(nominee.status || '').toUpperCase() === 'ACTIVE')
        .reduce((total, nominee) => total + Number(nominee.sharePercentage || 0), 0);
      if (String(d.status || '').toUpperCase() === 'ACTIVE' && allocatedShare + d.sharePercentage > 100.000001) {
        return s.error(`Nominee share cannot exceed 100%. ${allocatedShare.toFixed(2)}% is already allocated.`);
      }
      if (d.phone && !/^[6-9][0-9]{9}$/.test(d.phone)) return s.error('Nominee phone must be a valid 10-digit Indian mobile number.');
      if (d.dob && new Date(d.dob) >= new Date()) return s.error('Nominee date of birth must be in the past.');
      if (d.address && (!d.address.line1 || !d.address.city || !d.address.state || !d.address.country || !/^[1-9][0-9]{5}$/.test(d.address.pincode || ''))) return s.error('Complete the nominee address and enter a valid six-digit pincode.');
      if (d.address) {
        try {
          if (!await indiaAddress.validatePincode(d.address.state, d.address.city, d.address.pincode)) return s.error('Enter a pincode valid for the selected state and city / district.');
        } catch (e) {
          return s.error('PIN validation is temporarily unavailable. Please try again.');
        }
      }
      try { nomineeId ? await app.services.customers.updateNominee(id, nomineeId, d) : await app.services.customers.nominee(id, d); document.getElementById('nomineeDialog').close(); app.notify('Nominee saved.'); await s.loadDetail(s.selected()); }
      catch (e) { s.error(e.message); }
    };
    s.closeNominee = async (x) => { try { await app.services.customers.closeNominee(s.selected().customerId, x.nomineeId); app.notify('Nominee closed.'); await s.loadDetail(s.selected()); } catch (e) { app.notify(e.message, 'error'); } };
    s.deleteNominee = async (x) => {
      try {
        await app.services.customers.deleteNominee(s.selected().customerId, x.nomineeId);
        app.notify('Nominee deleted.');
        await s.loadDetail(s.selected());
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.close = (id) => document.getElementById(id).close();
    s.load().then(async () => {
      const customerId = app.customerToManage && app.customerToManage();
      if (!customerId) return;
      app.customerToManage(null);
      setTimeout(async () => {
        try {
          await s.openDetail({ customerId });
        } catch (e) {
          app.notify(e.message || 'Unable to open the selected customer.', 'error');
        }
      }, 0);
    });
  }
  return VM;
});
