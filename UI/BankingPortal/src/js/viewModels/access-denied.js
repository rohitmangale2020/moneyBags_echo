define(['appController'], function (app) {
  function VM() {
    this.returnToDashboard = () => app.go('dashboard');
  }
  return VM;
});
