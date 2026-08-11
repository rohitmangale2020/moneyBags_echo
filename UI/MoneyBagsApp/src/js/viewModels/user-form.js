define(['knockout', 'accUtils', 'ojs/ojarraydataprovider', 'services/userStore', 'appController', 'ojs/ojselectsingle'], function (ko, accUtils, ArrayDataProvider, userStore, app) {
  function UserFormViewModel() {
    const existing = userStore.selectedUser();
    const profile = existing && existing.profile ? existing.profile : {};
    this.isEdit = ko.observable(!!existing);
    this.title = ko.pureComputed(() => this.isEdit() ? 'User profile & access' : 'Create user');
    this.username = ko.observable(existing ? existing.username : ''); this.email = ko.observable(existing ? existing.email : '');
    this.role = ko.observable(existing ? existing.role : 'EMPLOYEE'); this.status = ko.observable(existing ? existing.status : 'PENDING_VERIFICATION');
    this.roleOptions = new ArrayDataProvider([
      { value: 'ADMIN', label: 'Administrator' },
      { value: 'EMPLOYEE', label: 'Employee' },
      { value: 'CUSTOMER', label: 'Customer' }
    ], { keyAttributes: 'value' });
    this.statusOptions = new ArrayDataProvider([
      { value: 'PENDING_VERIFICATION', label: 'Pending verification' },
      { value: 'ACTIVE', label: 'Active' },
      { value: 'LOCKED', label: 'Locked' },
      { value: 'DEACTIVATED', label: 'Deactivated' }
    ], { keyAttributes: 'value' });
    this.password = ko.observable(''); this.newPassword = ko.observable(''); this.message = ko.observable(''); this.busy = ko.observable(false);
    ['firstName', 'middleName', 'lastName', 'phoneNumber', 'dateOfBirth', 'addressLine1', 'addressLine2', 'city', 'state', 'postalCode', 'countryCode'].forEach((field) => { this[field] = ko.observable(profile[field] || (field === 'countryCode' ? 'IN' : '')); });
    this.payload = () => ({ username: this.username().trim(), email: this.email().trim(), role: this.role(), profile: {
      firstName: this.firstName().trim(), middleName: this.middleName().trim() || null, lastName: this.lastName().trim(), phoneNumber: this.phoneNumber().trim() || null,
      dateOfBirth: this.dateOfBirth() || null, addressLine1: this.addressLine1().trim() || null, addressLine2: this.addressLine2().trim() || null,
      city: this.city().trim() || null, state: this.state().trim() || null, postalCode: this.postalCode().trim() || null, countryCode: this.countryCode().trim() || null
    } });
    this.save = async () => {
      if (!this.username().trim() || !this.email().trim() || !this.firstName().trim() || !this.lastName().trim() || (!existing && this.password().length < 8)) { this.message('Username, email, first name, last name, and an 8+ character initial password are required.'); return; }
      this.busy(true); this.message('');
      try {
        if (existing) {
          await userStore.update(existing.id, this.payload());
          if (existing.status !== this.status()) await userStore.updateStatus(existing.id, this.status());
          if (this.newPassword()) { if (this.newPassword().length < 8) throw new Error('The replacement password must be at least 8 characters.'); await userStore.updatePassword(existing.id, this.newPassword()); }
        } else { const payload = this.payload(); payload.password = this.password(); await userStore.create(payload); }
        await userStore.load(); userStore.selectedUser(null); app.goToUsers();
      } catch (error) { this.message(error.message); } finally { this.busy(false); }
    };
    this.deactivate = async () => { if (!existing) return; this.busy(true); try { await userStore.deactivate(existing.id); await userStore.load(); userStore.selectedUser(null); app.goToUsers(); } catch (error) { this.message(error.message); } finally { this.busy(false); } };
    this.cancel = () => { userStore.selectedUser(null); app.goToUsers(); };
    this.connected = () => { accUtils.announce('User form page loaded.'); document.title = 'MoneyBags | User profile'; };
  }
  return UserFormViewModel;
});
