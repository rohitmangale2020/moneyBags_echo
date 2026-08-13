define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojdialog',
], function (ko, app, u) {
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
    docId: null, documentType: 'PAN', documentNumber: '', issueDate: '', expiryDate: '', status: 'UPLOADED', verifiedBy: '', rejectedReason: '', remarks: '', updatedBy: '',
  });
  const nomineeBlank = () => ({
    nomineeId: null, nomineeName: '', relationship: '', relationType: 'NOMINEE', dob: '', phone: '', sharePercentage: 100, status: 'ACTIVE', updatedBy: '', startDate: '', endDate: '', includeAddress: false, address: addressBlank(),
  });
  const isMinor = (dob) => {
    if (!dob) return false;
    const birthDate = new Date(`${dob}T00:00:00`);
    const today = new Date();
    let age = today.getFullYear() - birthDate.getFullYear();
    if (today.getMonth() < birthDate.getMonth() || (today.getMonth() === birthDate.getMonth() && today.getDate() < birthDate.getDate())) age -= 1;
    return age < 18;
  };

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
    s.detailState = u.state({ addresses: [], documents: [], nominees: [], kyc: null });
    s.query = ko.observable('');
    s.searchMode = ko.observable('local');
    s.selected = ko.observable(null);
    s.form = ko.observable(customerBlank());
    s.addressForm = ko.observable(addressBlank());
    s.kycForm = ko.observable(kycBlank());
    s.documentForm = ko.observable(documentBlank());
    s.nomineeForm = ko.observable(nomineeBlank());
    s.documentFile = ko.observable(null);
    s.error = ko.observable('');
    s.date = u.date;
    s.filtered = ko.pureComputed(() => {
      const q = s.query().toLowerCase();
      return s.state.data().filter((x) => !q || `${x.cifNo} ${x.firstName} ${x.lastName || ''} ${x.email || ''} ${x.phone}`.toLowerCase().includes(q));
    });
    s.load = () => s.state.run(() => app.services.customers.list()).catch(() => null);
    s.backendSearch = () => {
      const q = s.query().trim();
      if (!q || s.searchMode() === 'local') return s.load();
      const calls = {
        cif: app.services.customers.byCif,
        email: app.services.customers.byEmail,
        phone: app.services.customers.byPhone,
        status: app.services.customers.byStatus,
      };
      s.state.run(async () => {
        const value = await calls[s.searchMode()](q);
        return Array.isArray(value) ? value : [value];
      }).catch(() => null);
    };
    s.loadDetail = async (customer) => {
      const id = customer.customerId;
      s.selected(await app.services.customers.get(id));
      return s.detailState.run(async () => {
        const results = await Promise.allSettled([
          app.services.customers.addresses(id),
          app.services.customers.documents(id),
          app.services.customers.nominees(id),
          app.services.customers.kyc(id),
        ]);
        return {
          addresses: results[0].status === 'fulfilled' ? results[0].value : [],
          documents: results[1].status === 'fulfilled' ? results[1].value : [],
          nominees: results[2].status === 'fulfilled' ? results[2].value : [],
          kyc: results[3].status === 'fulfilled' ? results[3].value : null,
        };
      });
    };
    s.openDetail = async (customer) => {
      s.error('');
      document.getElementById('customerDetailDialog').open();
      try { await s.loadDetail(customer); } catch (e) { s.error(e.message); }
    };
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
        const value = x.status === 'ACTIVE' ? await app.services.customers.deactivate(x.customerId) : await app.services.customers.activate(x.customerId);
        if (s.selected() && s.selected().customerId === x.customerId) s.selected(value);
        app.notify(`Customer ${x.status === 'ACTIVE' ? 'deactivated' : 'activated'}.`); s.load();
      } catch (e) { app.notify(e.message, 'error'); }
    };
    s.remove = async (x) => {
      if (!window.confirm(`Delete customer ${x.cifNo}? This calls the backend DELETE operation.`)) return;
      try { await app.services.customers.remove(x.customerId); document.getElementById('customerDetailDialog').close(); app.notify('Customer deleted.'); s.load(); }
      catch (e) { app.notify(e.message, 'error'); }
    };
    s.openAddress = async (x) => { s.error(''); try { const value = x ? await app.services.customers.getAddress(s.selected().customerId, x.addressId) : null; s.addressForm(Object.assign(addressBlank(), value || {})); document.getElementById('addressDialog').open(); } catch (e) { app.notify(e.message, 'error'); } };
    s.saveAddress = async () => {
      const raw = ko.toJS(s.addressForm()), addressId = raw.addressId, d = clean({ addressType: raw.addressType, line1: raw.line1, line2: raw.line2, city: raw.city, state: raw.state, country: raw.country, pincode: raw.pincode }), id = s.selected().customerId;
      if (!d.line1 || !d.city || !d.state || !d.country || !/^[1-9][0-9]{5}$/.test(d.pincode || '')) return s.error('Complete the address and enter a valid six-digit pincode.');
      try { addressId ? await app.services.customers.updateAddress(id, addressId, d) : await app.services.customers.address(id, d); document.getElementById('addressDialog').close(); app.notify('Address saved.'); await s.loadDetail(s.selected()); }
      catch (e) { s.error(e.message); }
    };
    s.deleteAddress = async (x) => { if (!window.confirm('Delete this address?')) return; try { await app.services.customers.deleteAddress(s.selected().customerId, x.addressId); app.notify('Address deleted.'); await s.loadDetail(s.selected()); } catch (e) { app.notify(e.message, 'error'); } };
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
      const nominees = s.detailState.data().nominees || [];
      if (isMinor(s.selected().dob) && !nominees.some((nominee) => String(nominee.status || '').toUpperCase() === 'ACTIVE')) {
        return s.error('A minor customer must have an active nominee before KYC can be saved. Add the nominee first.');
      }
      if (d.kycDate && new Date(d.kycDate) > new Date()) return s.error('KYC date cannot be in the future.');
      d.riskScore = 0;
      try { s.detailState.data().kyc ? await app.services.customers.updateKyc(id, d) : await app.services.customers.createKyc(id, d); document.getElementById('kycDialog').close(); app.notify('KYC record saved.'); await s.loadDetail(s.selected()); }
      catch (e) { s.error(e.message); }
    };
    s.openDocument = async (x) => { s.error(''); try { const value = x ? await app.services.customers.getDocument(s.selected().customerId, x.docId) : null; s.documentForm(Object.assign(documentBlank(), value || {})); s.documentFile(null); document.getElementById('documentDialog').open(); } catch (e) { app.notify(e.message, 'error'); } };
    s.pickFile = (_, event) => { s.documentFile(event.target.files && event.target.files[0]); };
    s.saveDocument = async () => {
      const raw = ko.toJS(s.documentForm()), docId = raw.docId, d = clean({ documentType: raw.documentType, documentNumber: raw.documentNumber, issueDate: raw.issueDate, expiryDate: raw.expiryDate, status: raw.status, verifiedBy: raw.verifiedBy, rejectedReason: raw.rejectedReason, remarks: raw.remarks, updatedBy: raw.updatedBy }), id = s.selected().customerId, file = s.documentFile();
      if (!d.documentNumber || (!docId && !file)) return s.error('Document number and file are required.');
      if (d.issueDate && new Date(d.issueDate) > new Date()) return s.error('Document issue date cannot be in the future.');
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
        form.includeAddress = Boolean(value && value.address);
        form.address = Object.assign(addressBlank(), (value && value.address) || {});
        s.nomineeForm(form);
        document.getElementById('nomineeDialog').open();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.saveNominee = async () => {
      const raw = ko.toJS(s.nomineeForm()), nomineeId = raw.nomineeId, d = clean({ nomineeName: raw.nomineeName, relationship: raw.relationship, relationType: raw.relationType, dob: raw.dob, phone: raw.phone, address: raw.includeAddress ? clean({ addressType: raw.address.addressType, line1: raw.address.line1, line2: raw.address.line2, city: raw.address.city, state: raw.address.state, country: raw.address.country, pincode: raw.address.pincode }) : null, sharePercentage: raw.sharePercentage, status: raw.status, updatedBy: raw.updatedBy, startDate: raw.startDate, endDate: raw.endDate }), id = s.selected().customerId;
      d.sharePercentage = Number(d.sharePercentage);
      if (!d.nomineeName || d.sharePercentage <= 0 || d.sharePercentage > 100) return s.error('Enter a nominee name and share between 0.01 and 100.');
      if (d.phone && !/^[6-9][0-9]{9}$/.test(d.phone)) return s.error('Nominee phone must be a valid 10-digit Indian mobile number.');
      if (d.dob && new Date(d.dob) >= new Date()) return s.error('Nominee date of birth must be in the past.');
      if (d.address && (!d.address.line1 || !d.address.city || !d.address.state || !d.address.country || !/^[1-9][0-9]{5}$/.test(d.address.pincode || ''))) return s.error('Complete the nominee address and enter a valid six-digit pincode.');
      try { nomineeId ? await app.services.customers.updateNominee(id, nomineeId, d) : await app.services.customers.nominee(id, d); document.getElementById('nomineeDialog').close(); app.notify('Nominee saved.'); await s.loadDetail(s.selected()); }
      catch (e) { s.error(e.message); }
    };
    s.closeNominee = async (x) => { try { await app.services.customers.closeNominee(s.selected().customerId, x.nomineeId); app.notify('Nominee closed.'); await s.loadDetail(s.selected()); } catch (e) { app.notify(e.message, 'error'); } };
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
