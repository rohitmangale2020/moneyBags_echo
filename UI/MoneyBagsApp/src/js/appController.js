/**
 * @license
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
/*
 * Your application specific code will go here
 */
define(['knockout', 'ojs/ojcontext', 'ojs/ojresponsiveutils', 'ojs/ojresponsiveknockoututils', 'ojs/ojcorerouter', 'ojs/ojmodulerouter-adapter', 'ojs/ojknockoutrouteradapter', 'ojs/ojurlparamadapter', 'ojs/ojarraydataprovider', 'ojs/ojknockouttemplateutils', 'ojs/ojmodule-element', 'ojs/ojknockout', 'services/authService'],
  function(ko, Context, ResponsiveUtils, ResponsiveKnockoutUtils, CoreRouter, ModuleRouterAdapter, KnockoutRouterAdapter, UrlParamAdapter, ArrayDataProvider, KnockoutTemplateUtils, moduleElement, ojKnockout, authService) {

     function ControllerViewModel() {

        this.KnockoutTemplateUtils = KnockoutTemplateUtils;

        // Handle announcements sent when pages change, for Accessibility.
        this.manner = ko.observable('polite');
        this.message = ko.observable();

        announcementHandler = (event) => {
          this.message(event.detail.message);
          this.manner(event.detail.manner);
      };

      document.getElementById('globalBody').addEventListener('announce', announcementHandler, false);

      // Media queries for repsonsive layouts
      const smQuery = ResponsiveUtils.getFrameworkQuery(ResponsiveUtils.FRAMEWORK_QUERY_KEY.SM_ONLY);
      this.smScreen = ResponsiveKnockoutUtils.createMediaQueryObservable(smQuery);

      const routes = [
        { path: '', redirect: 'login' },
        { path: 'login', detail: { public: true } },
        { path: 'dashboard', detail: { label: 'Overview', iconClass: 'oj-ux-ico-bar-chart', roles: ['ADMIN', 'EMPLOYEE'] } },
        { path: 'users', detail: { label: 'User Directory', iconClass: 'oj-ux-ico-contact-group', roles: ['ADMIN'] } },
        { path: 'user-form', detail: { label: 'Create User', iconClass: 'oj-ux-ico-plus', roles: ['ADMIN'], hidden: true } },
        { path: 'access-denied', detail: { hidden: true, roles: ['ADMIN', 'EMPLOYEE', 'CUSTOMER'] } }
      ];
      const protectedRoutes = routes.filter((route) => route.detail && !route.detail.public);
      const restoredSession = authService.session();
      this.isAuthenticated = ko.observable(!!restoredSession);
      this.currentRole = ko.observable(restoredSession ? restoredSession.role : null);
      // Router setup
      const router = new CoreRouter(routes, {
        urlAdapter: new UrlParamAdapter()
      });
      this.routeAllowed = (path) => {
        const route = routes.find((item) => item.path === path);
        return !!route && (route.detail.public || (this.isAuthenticated() && route.detail.roles.indexOf(this.currentRole()) >= 0));
      };
      router.beforeStateChange.subscribe((args) => {
        if (!args || !args.state) return;
        if (!this.routeAllowed(args.state.path)) {
          args.accept(Promise.reject('Route is not available for this session.'));
          window.setTimeout(() => router.go({ path: this.isAuthenticated() ? 'access-denied' : 'login' }), 0);
        }
      });
      router.sync().then((state) => {
        if (restoredSession && state.path === 'login') router.go({ path: restoredSession.role === 'CUSTOMER' ? 'access-denied' : 'dashboard' });
      }).catch(() => router.go({ path: 'login' }));

      this.moduleAdapter = new ModuleRouterAdapter(router);

      this.selection = new KnockoutRouterAdapter(router);

      this.visibleNavItems = ko.pureComputed(() => protectedRoutes.filter((route) =>
        !route.detail.hidden && this.isAuthenticated() && route.detail.roles.indexOf(this.currentRole()) >= 0));
      this.navDataProvider = ko.pureComputed(() => new ArrayDataProvider(this.visibleNavItems(), { keyAttributes: 'path' }));

      // Header
      // Application Name used in Branding Area
      this.appName = ko.observable('MoneyBags Admin');
      // User Info used in Global Navigation area
      this.userLogin = ko.observable(restoredSession ? restoredSession.username : '');
      this.completeLogin = (username, role) => {
        this.userLogin(username);
        this.currentRole(role);
        this.isAuthenticated(true);
        return router.go({ path: role === 'CUSTOMER' ? 'access-denied' : 'dashboard' });
      };
      this.signOut = () => {
        authService.logout();
        this.isAuthenticated(false);
        this.currentRole(null);
        this.userLogin('');
        return router.go({ path: 'login' });
      };
      this.goToUsers = () => router.go({ path: 'users' });
      this.goToUserForm = () => router.go({ path: 'user-form' });

      // Footer
      this.footerLinks = [
        {name: 'MoneyBags Banking System', linkId: 'moneyBags', linkTarget:'#'},
        {name: 'Internal use only', linkId: 'internalOnly', linkTarget:'#'}
      ];
     }

     // release the application bootstrap busy state
     Context.getPageContext().getBusyContext().applicationBootstrapComplete();

     return new ControllerViewModel();
  }
);
