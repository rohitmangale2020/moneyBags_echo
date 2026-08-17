define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojarraydataprovider',
  'ojs/ojinputtext',
  'ojs/ojselectsingle',
  'ojs/ojdatetimepicker',
  'ojs/ojbutton',
  'ojs/ojdialog',
  'ojs/ojtable',
], function (ko, app, u, ArrayDataProvider) {
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
  const rfc5322Email = /^(?=.{1,254}$)(?=.{1,64}@)(?:[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[^"\\\r\n]|\\.)+")@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$/;
  const e164Phone = /^\+?[1-9]\d{7,14}$/;
  function VM() {
    const s = this;
    s.state = u.state([]);
    s.roleOptions = new ArrayDataProvider(
      [
        { value: 'ADMIN', label: 'Admin' },
        { value: 'EMPLOYEE', label: 'Employee' },
      ],
      { keyAttributes: 'value' },
    );
    s.query = ko.observable('');
    s.currentPage = ko.observable(0);
    s.totalPages = ko.observable(1);
    s.totalUsers = ko.observable(0);
    s.pageSize = 20;
    s.directoryRequest = 0;
    s.searchTimer = null;
    s.form = ko.observable(blank());
    s.editingId = ko.observable(null);
    s.password = ko.observable('');
    s.error = ko.observable('');
    s.emailValidators = [{
      validate: (value) => {
        if (value && !rfc5322Email.test(value.trim())) {
          throw new Error('Enter a valid RFC 5322 email address.');
        }
      },
    }];
    s.phoneValidators = [{
      validate: (value) => {
        if (value && !e164Phone.test(value.trim())) {
          throw new Error('Enter a valid international phone number, for example +919876543210.');
        }
      },
    }];
    s.detailState = u.state(null);
    s.detailColumns = [
      { headerText: 'Field', field: 'field' },
      { headerText: 'Value', field: 'value' },
    ];
    s.detailDataProvider = ko.pureComputed(() =>
      new ArrayDataProvider(s.detailState.data() || [], {
        keyAttributes: 'field',
      }),
    );
    s.canViewDetails = (user) =>
      ['ACTIVE', 'PENDING_VERIFICATION'].includes(user.status);
    s.statusRank = (status) =>
      ({ ACTIVE: 0, PENDING_VERIFICATION: 1, DEACTIVATED: 2 }[status] ?? 3);
    s.toDetailRow = (user) => {
      const profile = user.profile || {};
      const display = (value) => (value === null || value === undefined || value === '' ? 'Not provided' : value);
      return [
        ['User ID', user.id], ['Username', user.username], ['Email', user.email], ['Role', user.role],
        ['Status', user.status && user.status.replaceAll('_', ' ')], ['First name', profile.firstName],
        ['Middle name', profile.middleName], ['Last name', profile.lastName], ['Phone', profile.phoneNumber],
        ['Date of birth', profile.dateOfBirth], ['Country', profile.countryCode], ['Address line 1', profile.addressLine1],
        ['Address line 2', profile.addressLine2], ['City', profile.city], ['State', profile.state], ['Postal code', profile.postalCode],
      ].map(([field, value]) => ({ field, value: display(value) }));
    };
    s.users = ko.pureComputed(() =>
      s.state.data().map((x) =>
          Object.assign({}, x, {
            detailsAvailable: s.canViewDetails(x),
            openDetails: () => s.openDetails(x),
          }),
        ),
    );
    s.validate = (data, needsPassword) => {
      if (!data.username || data.username.length < 3 || !data.email || !data.profile.firstName || !data.profile.lastName)
        return 'Username (at least 3 characters), email, first name, and last name are required.';
      if (!rfc5322Email.test(data.email.trim())) return 'Enter a valid RFC 5322 email address.';
      if (needsPassword && (!data.password || data.password.length < 8 || data.password.length > 72)) return 'Password must contain 8 to 72 characters.';
      if (data.profile.phoneNumber && !e164Phone.test(data.profile.phoneNumber.trim())) return 'Enter a valid international phone number, for example +919876543210.';
      if (data.profile.countryCode && !/^[A-Za-z]{2}$/.test(data.profile.countryCode)) return 'Country code must contain two letters.';
      if (data.profile.dateOfBirth && new Date(data.profile.dateOfBirth) >= new Date()) return 'Date of birth must be in the past.';
      return null;
    };
    s.load = async (requestedPage = s.currentPage()) => {
      const requestId = ++s.directoryRequest;
      const page = Number.isInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0;
      s.state.loading(true);
      s.state.error('');
      try {
        const response = await app.services.users.list(page, s.pageSize, s.query().trim());
        if (requestId !== s.directoryRequest) return s.state.data();
        const users = u.list(response);
        const totalPages = Number(response.totalPages === undefined ? 1 : response.totalPages);
        if (!users.length && page > 0 && totalPages > 0 && page >= totalPages) {
          return s.load(totalPages - 1);
        }
        s.currentPage(Number(response.number || 0));
        s.totalPages(totalPages);
        s.totalUsers(Number(response.totalElements === undefined ? users.length : response.totalElements));
        s.state.data(users);
        return users;
      } catch (error) {
        if (requestId === s.directoryRequest) s.state.error(error.message);
        return null;
      } finally {
        if (requestId === s.directoryRequest) s.state.loading(false);
      }
    };
    s.previousPage = () => {
      if (!s.state.loading() && s.currentPage() > 0) s.load(s.currentPage() - 1);
    };
    s.nextPage = () => {
      if (!s.state.loading() && s.currentPage() < s.totalPages() - 1) s.load(s.currentPage() + 1);
    };
    s.search = () => {
      if (s.searchTimer) window.clearTimeout(s.searchTimer);
      s.searchTimer = null;
      return s.load(0);
    };
    s.query.subscribe(() => {
      if (s.searchTimer) window.clearTimeout(s.searchTimer);
      s.searchTimer = window.setTimeout(() => {
        s.searchTimer = null;
        s.load(0);
      }, 300);
    });
    s.open = () => {
      s.editingId(null);
      s.form(blank());
      s.error('');
      document.getElementById('userDialog').open();
    };
    s.openDetails = async (user) => {
      if (!s.canViewDetails(user)) return;
      s.detailState.data(null);
      document.getElementById('userDetailsDialog').open();
      await s.detailState
        .run(async () => s.toDetailRow(await app.services.users.get(user.id)))
        .catch(() => null);
    };
    s.closeDetails = () => document.getElementById('userDetailsDialog').close();
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
