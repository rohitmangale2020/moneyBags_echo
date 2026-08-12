define(['appController', 'viewModels/util'], function (app, u) {
  function VM() {
    this.initials = app.initials;
    this.profile = app.session.profile;
    this.date = u.date;
  }
  return VM;
});
