define(['knockout', 'appController'], function (ko, app) {
  'use strict';

  function VM() {
    const self = this;
    self.message = ko.observable('Prepare a customer 360 briefing');
    self.customerId = ko.observable((app.activeCustomer() || {}).customerId || '');
    self.transactionId = ko.observable('');
    self.accountId = ko.observable(app.activeAccountId() || '');
    self.module = ko.pureComputed(() => app.assistantContext() || 'platform');
    self.history = ko.observableArray([]);
    self.loading = ko.observable(false);
    self.error = ko.observable('');
    self.response = ko.observable(null);
    self.isStaff = ko.pureComputed(() => ['ADMIN', 'EMPLOYEE'].includes(app.session.role()));
    self.hasResponse = ko.pureComputed(() => !!self.response());

    self.useCustomerBriefing = () => {
      self.message('Prepare a customer 360 briefing');
      self.transactionId('');
    };
    self.useTransactionReview = () => {
      self.message('Review this transaction and explain the result');
      self.customerId('');
    };
    self.useProductRecommendation = () => {
      self.message('Recommend suitable active banking products');
      self.transactionId('');
      self.accountId('');
    };
    self.useAccountOverview = () => {
      self.message('Show the account overview and balance');
      self.customerId('');
      self.transactionId('');
    };
    self.usePolicyHelp = () => {
      self.message('What policy controls apply to this operation?');
      self.customerId('');
      self.transactionId('');
      self.accountId('');
    };
    self.send = async () => {
      const message = self.message().trim();
      if (!message) {
        self.error('Enter a question for the assistant.');
        return;
      }
      self.loading(true);
      self.error('');
      self.response(null);
      try {
        const response = await app.services.assistant.chat(message, self.customerId(), self.transactionId().trim(), self.accountId().trim(), self.module());
        self.response(response);
        self.history.unshift({ message, intent: response.intent, answer: response.answer });
      } catch (error) {
        self.error(error.message || 'The assistant could not complete the request.');
      } finally {
        self.loading(false);
      }
    };
  }
  return VM;
});
