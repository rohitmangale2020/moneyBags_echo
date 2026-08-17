define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojdialog',
], function (ko, app, u) {
  function VM() {
    const s = this;
    s.state = u.state(null);
    s.form = ko.observable(null);
    s.currentPassword = ko.observable('');
    s.password = ko.observable('');
    s.confirmPassword = ko.observable('');
    s.error = ko.observable('');
    s.passwordChangeRequired = app.session.passwordChangeRequired;
    s.display = (value) => value === null || value === undefined || value === '' ? 'Not provided' : value;
    s.load = () => s.state.run(async () => {
      const user = await app.services.users.get(app.session.userId());
      s.form({ username: user.username, email: user.email, profile: Object.assign({}, user.profile || {}) });
      return user;
    }).catch(() => null);
    s.save = async () => {
      const current = s.state.data();
      const data = ko.toJS(s.form());
      try {
        await app.services.users.update(current.id, {
          username: data.username,
          email: data.email,
          role: current.role,
          profile: data.profile,
        });
        app.notify('Profile updated.');
        s.load();
      } catch (e) { s.error(e.message); }
    };
    s.openPassword = () => {
      s.currentPassword('');
      s.password('');
      s.confirmPassword('');
      s.error('');
      document.getElementById('myPasswordDialog').open();
    };
    s.savePassword = async () => {
      if (!s.currentPassword()) return s.error('Enter your current password.');
      if (s.password().length < 8) return s.error('Password must contain at least 8 characters.');
      if (s.password() !== s.confirmPassword()) return s.error('New passwords do not match.');
      try {
        await app.services.users.changeOwnPassword(s.currentPassword(), s.password());
        const login = await app.services.auth.login(app.session.username(), s.password());
        app.session.establish(login.accessToken);
        document.getElementById('myPasswordDialog').close();
        app.notify('Password updated.');
        if (!s.passwordChangeRequired()) await app.completeLogin();
      } catch (e) { s.error(e.message); }
    };
    s.closePassword = () => document.getElementById('myPasswordDialog').close();
    s.load();
    if (app.profilePasswordRequest() || s.passwordChangeRequired()) {
      app.profilePasswordRequest(false);
      setTimeout(s.openPassword, 0);
    }
  }
  return VM;
});
