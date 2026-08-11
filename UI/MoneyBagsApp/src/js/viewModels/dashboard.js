/**
 * @license
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
/*
 * Your dashboard ViewModel code goes here
 */
define(['knockout', 'accUtils', 'services/userStore', 'appController'],
 function(ko, accUtils, userStore, app) {
    function DashboardViewModel() {
      this.users = userStore.users;
      this.activeCount = ko.pureComputed(() => this.users().filter((user) => user.status === 'ACTIVE').length);
      this.pendingCount = ko.pureComputed(() => this.users().filter((user) => user.status === 'PENDING_VERIFICATION').length);
      this.openDirectory = () => app.goToUsers();
      this.createUser = () => { userStore.selectedUser(null); app.goToUserForm(); };

      /**
       * Optional ViewModel method invoked after the View is inserted into the
       * document DOM.  The application can put logic that requires the DOM being
       * attached here.
       * This method might be called multiple times - after the View is created
       * and inserted into the DOM and after the View is reconnected
       * after being disconnected.
       */
      this.connected = async () => {
        accUtils.announce('Dashboard page loaded.');
        document.title = 'MoneyBags | Overview';
        try { await userStore.load(); } catch (error) { /* Directory displays the detailed API error. */ }
      };

      /**
       * Optional ViewModel method invoked after the View is disconnected from the DOM.
       */
      this.disconnected = () => {
        // Implement if needed
      };

      /**
       * Optional ViewModel method invoked after transition to the new View is complete.
       * That includes any possible animation between the old and the new View.
       */
      this.transitionCompleted = () => {
        // Implement if needed
      };
    }

    /*
     * Returns an instance of the ViewModel providing one instance of the ViewModel. If needed,
     * return a constructor for the ViewModel so that the ViewModel is constructed
     * each time the view is displayed.
     */
    return DashboardViewModel;
  }
);
