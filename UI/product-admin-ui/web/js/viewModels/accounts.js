define(['../accUtils'], function (accUtils) {
  function AccountsViewModel() {
    this.connected = () => { accUtils.announce('Accounts page loaded.', 'assertive'); document.title = 'Account Portfolio'; };
  }
  return AccountsViewModel;
});
