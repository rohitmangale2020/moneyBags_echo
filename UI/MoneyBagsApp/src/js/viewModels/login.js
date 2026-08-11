define(['knockout', 'accUtils', 'appController'], function (ko, accUtils, app) {
  function LoginViewModel() {
    this.username = ko.observable('');
    this.password = ko.observable('');
    this.errorMessage = ko.observable('');
    this.signIn = () => {
      if (!this.username().trim() || this.password().length < 8) {
        this.errorMessage('Enter a username and a password of at least 8 characters.');
        return;
      }
      // Local mock only. Replace this role assignment with POST /auth/login later.
      const role = this.username().toLowerCase().indexOf('employee') >= 0 ? 'EMPLOYEE' : 'ADMIN';
      this.errorMessage('');
      app.completeLogin(this.username().trim(), role);
    };
    this.connected = () => { accUtils.announce('Sign in page loaded.'); document.title = 'MoneyBags | Sign in'; };
  }
  return LoginViewModel;
});
