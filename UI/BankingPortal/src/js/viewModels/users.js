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
    s.load = () =>
      s.state.run(async () => u.list(await app.services.users.list())).catch(() => null);
    s.open = () => {
      s.form(blank());
      s.error('');
      document.getElementById('userDialog').open();
    };
    s.save = async () => {
      const d = ko.toJS(s.form());
      if (!d.username || !d.email || !d.password || !d.profile.firstName || !d.profile.lastName)
        return s.error('Complete all required fields.');
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
