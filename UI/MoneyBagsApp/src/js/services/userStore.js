define(['knockout'], function (ko) {
  const users = ko.observableArray([
    { id: 101, username: 'anita.sharma', email: 'anita.sharma@moneybags.bank', role: 'ADMIN', status: 'ACTIVE', firstName: 'Anita', lastName: 'Sharma', phoneNumber: '+919812345678', city: 'Mumbai', countryCode: 'IN' },
    { id: 102, username: 'rohit.mehta', email: 'rohit.mehta@moneybags.bank', role: 'EMPLOYEE', status: 'ACTIVE', firstName: 'Rohit', lastName: 'Mehta', phoneNumber: '+919876543210', city: 'Pune', countryCode: 'IN' },
    { id: 103, username: 'neha.kapoor', email: 'neha.kapoor@moneybags.bank', role: 'EMPLOYEE', status: 'PENDING_VERIFICATION', firstName: 'Neha', lastName: 'Kapoor', phoneNumber: '+919111222333', city: 'Bengaluru', countryCode: 'IN' }
  ]);
  const selectedUser = ko.observable(null);
  const save = (user) => {
    const copy = Object.assign({}, user);
    const existing = users().findIndex((item) => item.id === copy.id);
    if (existing >= 0) users.splice(existing, 1, copy);
    else { copy.id = Date.now(); copy.status = 'PENDING_VERIFICATION'; users.unshift(copy); }
  };
  return { users: users, selectedUser: selectedUser, save: save };
});
