define([
  "knockout",
  "ojs/ojcontext",
  "ojs/ojcorerouter",
  "ojs/ojmodulerouter-adapter",
  "ojs/ojknockoutrouteradapter",
  "ojs/ojurlparamadapter",
  "ojs/ojarraydataprovider",
  "ojs/ojresponsiveutils",
  "ojs/ojresponsiveknockoututils",
  "ojs/ojoffcanvas",
  "ojs/ojknockouttemplateutils",
  "services/session",
  "services/bankingServices",
  "ojs/ojmodule-element",
  "ojs/ojknockout",
], function (
  ko,
  Context,
  CoreRouter,
  ModuleRouterAdapter,
  KnockoutRouterAdapter,
  UrlParamAdapter,
  ArrayDataProvider,
  ResponsiveUtils,
  ResponsiveKnockoutUtils,
  OffcanvasUtils,
  KnockoutTemplateUtils,
  session,
  services,
) {
  "use strict";
  function App() {
    const self = this;
    self.session = session;
    self.services = services;
    self.KnockoutTemplateUtils = KnockoutTemplateUtils;
    self.toasts = ko.observableArray([]);
    self.manner = ko.observable("polite");
    self.message = ko.observable("");
    const routes = [
      { path: "", redirect: "login" },
      { path: "login", detail: { public: true, hidden: true } },
      {
        path: "dashboard",
        detail: {
          label: "Overview",
          iconClass: "oj-ux-ico-bar-chart",
          roles: ["ADMIN", "EMPLOYEE", "CUSTOMER"],
        },
      },
      {
        path: "users",
        detail: {
          label: "Users",
          iconClass: "oj-ux-ico-contact-group",
          roles: ["ADMIN"],
        },
      },
      {
        path: "customers",
        detail: {
          label: "Customers",
          iconClass: "oj-ux-ico-contacts",
          roles: ["ADMIN", "EMPLOYEE"],
        },
      },
      {
        path: "onboarding",
        detail: {
          label: "Onboard customer",
          iconClass: "oj-ux-ico-add-user",
          roles: ["EMPLOYEE"],
        },
      },
      {
        path: "products",
        detail: {
          label: "Products",
          iconClass: "oj-ux-ico-bank",
          roles: ["ADMIN", "EMPLOYEE"],
        },
      },
      {
        path: "accounts",
        detail: {
          label: "Accounts",
          iconClass: "oj-ux-ico-wallet",
          roles: ["ADMIN", "EMPLOYEE"],
        },
      },
      {
        path: "transactions",
        detail: {
          label: "Transactions",
          iconClass: "oj-ux-ico-transfer",
          roles: ["ADMIN", "EMPLOYEE"],
        },
      },
      {
        path: "statements",
        detail: {
          label: "Statements",
          iconClass: "oj-ux-ico-document",
          roles: ["ADMIN", "EMPLOYEE"],
        },
      },
      {
        path: "audit",
        detail: {
          label: "Audit logs",
          iconClass: "oj-ux-ico-history",
          roles: ["ADMIN"],
        },
      },
      {
        path: "self-service",
        detail: {
          label: "My banking",
          iconClass: "oj-ux-ico-user",
          roles: ["CUSTOMER"],
        },
      },
      {
        path: "access-denied",
        detail: { hidden: true, roles: ["ADMIN", "EMPLOYEE", "CUSTOMER"] },
      },
    ];
    const router = new CoreRouter(routes, {
      urlAdapter: new UrlParamAdapter(),
    });
    self.isAllowed = (path) => {
      const r = routes.find((x) => x.path === path);
      return (
        !!r &&
        (r.detail.public ||
          (session.isAuthenticated() &&
            r.detail.roles.includes(session.role())))
      );
    };
    router.beforeStateChange.subscribe((a) => {
      if (a && a.state && !self.isAllowed(a.state.path)) {
        a.accept(Promise.reject("Not allowed"));
        setTimeout(
          () =>
            router.go({
              path: session.isAuthenticated() ? "access-denied" : "login",
            }),
          0,
        );
      }
    });
    self.moduleAdapter = new ModuleRouterAdapter(router);
    self.selection = new KnockoutRouterAdapter(router);
    self.navItems = ko.pureComputed(() =>
      routes.filter(
        (r) =>
          r.detail &&
          !r.detail.hidden &&
          !r.detail.public &&
          session.isAuthenticated() &&
          r.detail.roles.includes(session.role()),
      ),
    );
    self.navDataProvider = ko.pureComputed(
      () => new ArrayDataProvider(self.navItems(), { keyAttributes: "path" }),
    );
    self.go = (p) => router.go({ path: p });
    const savedCustomerContext = (() => {
      try { return JSON.parse(sessionStorage.getItem('moneybags.activeCustomer') || 'null'); } catch (_) { return null; }
    })();
    self.activeCustomer = ko.observable(savedCustomerContext);
    self.activeAccountId = ko.observable(
      sessionStorage.getItem('moneybags.activeAccountId')
      || (savedCustomerContext && savedCustomerContext.activeAccountId ? String(savedCustomerContext.activeAccountId) : ''),
    );
    self.hasActiveCustomer = ko.pureComputed(() => !!self.activeCustomer());
    self.activeCustomerLabel = ko.pureComputed(() => {
      const customer = self.activeCustomer();
      return customer ? `${customer.name} · ${customer.cifNo}` : '';
    });
    self.setActiveCustomer = (customer) => {
      if (!customer || !customer.customerId) return;
      const context = {
        customerId: String(customer.customerId),
        cifNo: customer.cifNo || 'CIF pending',
        name: [customer.firstName, customer.lastName].filter(Boolean).join(' ') || 'Customer',
        status: customer.status || '',
      };
      self.activeCustomer(context);
      self.activeAccountId('');
      sessionStorage.removeItem('moneybags.activeAccountId');
      sessionStorage.setItem('moneybags.activeCustomer', JSON.stringify(context));
    };
    self.setActiveAccount = (accountId) => {
      const customer = self.activeCustomer();
      if (!accountId) return;
      const activeAccountId = String(accountId);
      self.activeAccountId(activeAccountId);
      sessionStorage.setItem('moneybags.activeAccountId', activeAccountId);
      if (customer) {
        const context = { ...customer, activeAccountId };
        self.activeCustomer(context);
        sessionStorage.setItem('moneybags.activeCustomer', JSON.stringify(context));
      }
    };
    self.clearActiveCustomer = () => {
      self.activeCustomer(null);
      self.activeAccountId('');
      sessionStorage.removeItem('moneybags.activeCustomer');
      sessionStorage.removeItem('moneybags.activeAccountId');
    };
    self.customerToManage = ko.observable(null);
    self.openCustomer = (customerId) => {
      self.customerToManage(String(customerId));
      return router.go({ path: "customers" });
    };
    self.completeLogin = async () => {
      try {
        session.profile(await services.users.get(session.userId()));
      } catch (_) {
        session.profile(null);
      }
      return router.go({ path: "dashboard" });
    };
    self.signOut = () => {
      session.clear();
      router.go({ path: "login" });
    };
    self.displayName = ko.pureComputed(() => {
      const p = session.profile() && session.profile().profile;
      return p ? `${p.firstName} ${p.lastName}` : session.username();
    });
    self.initials = ko.pureComputed(() =>
      self
        .displayName()
        .split(/\s+/)
        .slice(0, 2)
        .map((v) => v[0])
        .join("")
        .toUpperCase(),
    );
    self.notify = (text, kind) => {
      const item = {
        id: Date.now() + Math.random(),
        text,
        kind: kind || "success",
      };
      self.toasts.push(item);
      setTimeout(() => self.toasts.remove(item), 4200);
    };
    self.dismiss = (item) => self.toasts.remove(item);
    const md = ResponsiveUtils.getFrameworkQuery(
      ResponsiveUtils.FRAMEWORK_QUERY_KEY.MD_UP,
    );
    self.mdScreen = ResponsiveKnockoutUtils.createMediaQueryObservable(md);
    self.drawerParams = {
      displayMode: "overlay",
      selector: "#navDrawer",
      content: "#pageContent",
    };
    self.toggleDrawer = () => OffcanvasUtils.toggle(self.drawerParams);
    self.mdScreen.subscribe(() => {
      if (document.querySelector(self.drawerParams.selector)) {
        OffcanvasUtils.close(self.drawerParams);
      }
    });
    window.addEventListener("moneybags-session-expired", () => {
      session.clear();
      self.notify("Your session expired. Please sign in again.", "warning");
      router.go({ path: "login" });
    });
    router
      .sync()
      .then(() => {
        if (session.isAuthenticated()) self.completeLogin();
      })
      .catch(() => router.go({ path: "login" }));
  }
  Context.getPageContext().getBusyContext().applicationBootstrapComplete();
  return new App();
});
