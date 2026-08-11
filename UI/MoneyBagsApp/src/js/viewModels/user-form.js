define(['knockout', 'accUtils', 'services/userStore', 'appController'], function (ko, accUtils, userStore, app) {
  function UserFormViewModel() {
    const existing = userStore.selectedUser();
    this.isEdit = ko.observable(!!existing);
    this.title = ko.pureComputed(() => this.isEdit() ? 'User profile & access' : 'Create user');
    this.username = ko.observable(existing ? existing.username : ''); this.email = ko.observable(existing ? existing.email : '');
    this.role = ko.observable(existing ? existing.role : 'EMPLOYEE'); this.firstName = ko.observable(existing ? existing.firstName : '');
    this.lastName = ko.observable(existing ? existing.lastName : ''); this.phoneNumber = ko.observable(existing ? existing.phoneNumber : '');
    this.city = ko.observable(existing ? existing.city : ''); this.countryCode = ko.observable(existing ? existing.countryCode : 'IN');
    this.status = ko.observable(existing ? existing.status : 'PENDING_VERIFICATION'); this.message = ko.observable('');
    this.save = () => {
      if (!this.username().trim() || !this.email().trim() || !this.firstName().trim() || !this.lastName().trim()) { this.message('Complete all required fields before saving.'); return; }
      userStore.save({ id: existing && existing.id, username: this.username().trim(), email: this.email().trim(), role: this.role(), status: this.status(), firstName: this.firstName().trim(), lastName: this.lastName().trim(), phoneNumber: this.phoneNumber().trim(), city: this.city().trim(), countryCode: this.countryCode().trim().toUpperCase() });
      userStore.selectedUser(null); app.goToUsers();
    };
    this.deactivate = () => { this.status('DEACTIVATED'); this.message('The user will be marked DEACTIVATED when you save.'); };
    this.cancel = () => { userStore.selectedUser(null); app.goToUsers(); };
    this.connected = () => { accUtils.announce('User form page loaded.'); document.title = 'MoneyBags | User profile'; };
  }
  return UserFormViewModel;
});
