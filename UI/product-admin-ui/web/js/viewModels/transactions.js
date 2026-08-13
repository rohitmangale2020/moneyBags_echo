define(['../accUtils'], function (accUtils) {
  function TransactionsViewModel() {
    this.connected = () => { accUtils.announce('Transactions page loaded.', 'assertive'); document.title = 'Transaction Activity'; };
  }
  return TransactionsViewModel;
});
