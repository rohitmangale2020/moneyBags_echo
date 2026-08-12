define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojinputtext',
  'ojs/ojbutton',
], function (ko, app, u) {
  function VM() {
    const s = this;
    s.isEmployee = ko.pureComputed(() => app.session.role() === 'EMPLOYEE');
    s.openOnboarding = () => app.go('onboarding');
    s.state = u.state([]);
    s.query = ko.observable('');
    s.filtered = ko.pureComputed(() => {
      const q = s.query().toLowerCase();
      return s.state
        .data()
        .filter(
          (x) =>
            !q ||
            `${x.cifNo} ${x.firstName} ${x.lastName} ${x.email || ''} ${x.phone}`
              .toLowerCase()
              .includes(q),
        );
    });
    s.load = () => s.state.run(() => app.services.customers.list()).catch(() => null);
    s.status = async (x) => {
      try {
        x.status === 'ACTIVE'
          ? await app.services.customers.deactivate(x.customerId)
          : await app.services.customers.activate(x.customerId);
        app.notify(`Customer ${x.status === 'ACTIVE' ? 'deactivated' : 'activated'}.`);
        s.load();
      } catch (e) {
        app.notify(e.message, 'error');
      }
    };
    s.load();
  }
  return VM;
});
