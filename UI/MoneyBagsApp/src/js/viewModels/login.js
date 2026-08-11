define(['knockout', 'accUtils', 'appController', 'services/authService'], function (ko, accUtils, app, authService) {
  function LoginViewModel() {
    this.username = ko.observable('');
    this.password = ko.observable('');
    this.errorMessage = ko.observable('');
    this.signIn = async () => {
      if (!this.username().trim() || this.password().length < 8) {
        this.errorMessage('Enter a username and a password of at least 8 characters.');
        return;
      }
      try {
        const session = await authService.login(this.username().trim(), this.password());
        this.errorMessage('');
        app.completeLogin(session.username, session.role);
      } catch (error) {
        this.errorMessage(error.message);
      }
    };
    this.connected = () => { accUtils.announce('Sign in page loaded.'); document.title = 'MoneyBags | Sign in'; };
  }
  return LoginViewModel;
});
