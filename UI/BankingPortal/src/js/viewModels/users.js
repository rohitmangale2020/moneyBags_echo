define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojselectsingle',
  'ojs/ojbutton',
  'ojs/ojdialog',
], function (ko, app, u) {
  function blank() {
    return {
      username: '',
      email: '',
      password: '',
      role: 'EMPLOYEE',
      profile: {
        firstName: '',
        middleName: '',
        lastName: '',
        phoneNumber: '',
        dateOfBirth: null,
        addressLine1: '',
        addressLine2: '',
        city: '',
        state: '',
        postalCode: '',
        countryCode: 'IN',
      },
    };
  }
  function VM() {
    const s = this;
    s.state = u.state([]);
    s.query = ko.observable('');
    s.form = ko.observable(blank());
    s.editingId = ko.observable(null);
    s.password = ko.observable('');
    s.error = ko.observable('');
    s.filtered = ko.pureComputed(() => {
      const q = s.query().toLowerCase();
      return s.state
        .data()
        .filter(
          (x) =>
            !q ||
            `${x.username} ${x.email} ${x.profile && x.profile.firstName}`
              .toLowerCase()
              .includes(q),
        );
    });
    s.validate = (data, needsPassword) => {
      if (!data.username || data.username.length < 3 || !data.email || !data.profile.firstName || !data.profile.lastName)
        return 'Username (at least 3 characters), email, first name, and last name are required.';
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) return 'Enter a valid email address.';
      if (needsPassword && (!data.password || data.password.length < 8 || data.password.length > 72)) return 'Password must contain 8 to 72 characters.';
      if (data.profile.phoneNumber && !/^\+?[1-9]\d{7,14}$/.test(data.profile.phoneNumber)) return 'Enter a valid international phone number.';
      if (data.profile.countryCode && !/^[A-Za-z]{2}$/.test(data.profile.countryCode)) return 'Country code must contain two letters.';
      if (data.profile.dateOfBirth && new Date(data.profile.dateOfBirth) >= new Date()) return 'Date of birth must be in the past.';
      return null;
    };
    s.load = () =>
      s.state.run(async () => u.list(await app.services.users.list())).catch(() => null);
    s.open = () => {
      s.editingId(null);
      s.form(blank());
      s.error('');
      document.getElementById('userDialog').open();
    };
    s.edit = async (x) => {
      s.error('');
      try { x = await app.services.users.get(x.id); } catch (e) { return app.notify(e.message, 'error'); }
      s.editingId(x.id);
      s.form({
        username: x.username,
        email: x.email,
        password: '',
        role: x.role,
        profile: Object.assign(blank().profile, x.profile || {}),
      });
      document.getElementById('userEditDialog').open();
    };
    s.saveEdit = async () => {
      const d = ko.toJS(s.form());
      delete d.password;
      const validation = s.validate(d, false);
      if (validation) return s.error(validation);
      try {
        await app.services.users.update(s.editingId(), d);
        document.getElementById('userEditDialog').close();
        app.notify('User details updated.');
        s.load();
      } catch (e) {
        s.error(e.message);
      }
    };
    s.openPassword = (x) => {
      s.editingId(x.id);
      s.password('');
      s.error('');
      document.getElementById('passwordDialog').open();
    };
    s.savePassword = async () => {
      if (s.password().length < 8) return s.error('Password must contain at least 8 characters.');
      try {
        await app.services.users.password(s.editingId(), s.password());
        document.getElementById('passwordDialog').close();
        app.notify('Temporary password updated.');
      } catch (e) {
        s.error(e.message);
      }
    };
    s.remove = async (x) => {
      if (!window.confirm(`Deactivate ${x.username}?`)) return;
      try {
        await app.services.users.deactivate(x.id);
        app.notify('User deactivated.');
        s.load();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.close = (id) => document.getElementById(id).close();
    s.save = async () => {
      const d = ko.toJS(s.form());
      const validation = s.validate(d, true);
      if (validation) return s.error(validation);
      try {
        await app.services.users.create(d);
        document.getElementById('userDialog').close();
        app.notify('User created successfully.');
        s.load();
      } catch (e) {
        s.error(e.message);
      }
    };
    s.status = async (x, v) => {
      try {
        await app.services.users.status(x.id, v);
        app.notify(`User marked ${v.toLowerCase()}.`);
        s.load();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.load();
  }
  return VM;
});
