define(['knockout', 'appController', 'ojs/ojinputtext', 'ojs/ojbutton'], function (ko, app) {
  function VM() {
    this.username = ko.observable('');
    this.password = ko.observable('');
    this.loading = ko.observable(false);
    this.error = ko.observable('');
    this.submit = async () => {
      if (!this.username().trim() || !this.password())
        return this.error('Enter your username and password.');
      this.loading(true);
      this.error('');
      try {
        const r = await app.services.auth.login(this.username().trim(), this.password());
        app.session.establish(r.accessToken);
        await app.completeLogin();
        app.notify('Welcome back. Your secure session is ready.');
      } catch (e) {
        this.error(e.status === 401 ? 'The username or password is incorrect.' : e.message);
      } finally {
        this.loading(false);
      }
    };
  }
  return VM;
});
