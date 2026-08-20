define([
  'knockout',
  'ojs/ojconverter-datetime',
  'appController',
  'viewModels/util',
  'ojs/ojarraydataprovider',
  'ojs/ojinputtext',
  'ojs/ojselectsingle',
  'ojs/ojdatetimepicker',
  'ojs/ojbutton',
  'ojs/ojdialog',
  'ojs/ojtable',
], function (ko, DateTimeConverter, app, u, ArrayDataProvider) {
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
    s.dateOfBirthConverter = new DateTimeConverter.IntlDateTimeConverter({
      pattern: 'dd/MM/yyyy',
    });
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
    s.pageSize = 10;
    s.directoryRequest = 0;
    s.searchTimer = null;
    s.form = ko.observable(blank());
    s.editingId = ko.observable(null);
    s.password = ko.observable('');
    s.error = ko.observable('');
    s.createFieldErrors = ko.observable({});
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
      user.status === 'ACTIVE';
    s.statusRank = (status) =>
      ({ ACTIVE: 0, DEACTIVATED: 1 }[status] ?? 2);
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
    s.validationErrors = (data, needsPassword) => {
      const errors = {};
      const value = (input) => String(input || '').trim();
      const username = value(data.username);
      const email = value(data.email);
      const firstName = value(data.profile.firstName);
      const lastName = value(data.profile.lastName);
      const password = String(data.password || '');
      const phoneNumber = value(data.profile.phoneNumber);
      const countryCode = value(data.profile.countryCode);
      const dateOfBirth = data.profile.dateOfBirth;

      if (!firstName) errors.firstName = 'First name is required.';
      if (!lastName) errors.lastName = 'Last name is required.';
      if (!username) errors.username = 'Username is required.';
      else if (username.length < 3) errors.username = 'Username must contain at least 3 characters.';
      if (!email) errors.email = 'Email address is required.';
      else if (!rfc5322Email.test(email)) errors.email = 'Enter a valid email address.';
      if (needsPassword && !password) errors.password = 'Temporary password is required.';
      else if (needsPassword && (password.length < 8 || password.length > 72)) errors.password = 'Temporary password must contain 8 to 72 characters.';
      if (phoneNumber && !e164Phone.test(phoneNumber)) errors.phoneNumber = 'Enter a valid international phone number, for example +919876543210.';
      if (countryCode && !/^[A-Za-z]{2}$/.test(countryCode)) errors.countryCode = 'Country code must contain exactly two letters.';
      if (dateOfBirth && Number.isNaN(new Date(dateOfBirth).getTime())) errors.dateOfBirth = 'Enter a valid date of birth.';
      else if (dateOfBirth && new Date(dateOfBirth) >= new Date()) errors.dateOfBirth = 'Date of birth must be in the past.';
      return errors;
    };
    s.validate = (data, needsPassword) => Object.values(s.validationErrors(data, needsPassword))[0] || null;
    s.applyCreateServerFieldErrors = (details) => {
      if (!details || !details.errors || typeof details.errors !== 'object' || Array.isArray(details.errors)) return false;
      const supportedFields = new Set(['firstName', 'lastName', 'username', 'email', 'password', 'phoneNumber', 'countryCode', 'dateOfBirth']);
      const errors = {};
      Object.entries(details.errors).forEach(([field, message]) => {
        const name = field.split('.').pop();
        if (supportedFields.has(name)) errors[name] = message;
      });
      if (!Object.keys(errors).length) return false;
      s.createFieldErrors(errors);
      s.error('');
      return true;
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
      s.createFieldErrors({});
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
    s.canManage = (x) => String(x.id) !== String(app.session.userId());
    s.remove = async (x) => {
      if (!window.confirm(`Permanently delete ${x.username}? This cannot be undone.`)) return;
      try {
        await app.services.users.remove(x.id);
        app.notify('User permanently deleted.');
        s.load();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.close = (id) => document.getElementById(id).close();
    s.save = async () => {
      const d = ko.toJS(s.form());
      const validation = s.validationErrors(d, true);
      s.createFieldErrors(validation);
      if (Object.keys(validation).length) {
        s.error('');
        return;
      }
      s.error('');
      try {
        await app.services.users.create(d);
        document.getElementById('userDialog').close();
        app.notify('User created successfully.');
        s.load();
      } catch (e) {
        if (s.applyCreateServerFieldErrors(e.details)) return;
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
