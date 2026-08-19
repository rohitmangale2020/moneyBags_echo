define(['knockout', 'appController', 'viewModels/util', 'ojs/ojbutton'], function (ko, app, u) {
  function VM() {
    const self = this;
    self.state = u.state([]); self.notes = new Map(); self.busy = ko.observable(false); self.error = ko.observable('');
    self.money = u.money; self.date = u.date;
    self.noteFor = (transaction) => {
      const id = transaction.transactionId;
      if (!self.notes.has(id)) self.notes.set(id, ko.observable(''));
      return self.notes.get(id);
    };
    self.load = () => self.state.run(async () => { const items = u.list(await app.services.transactions.pendingApprovals()); app.pendingRiskApprovals(items); return items; });
    self.decide = async (transaction, decision) => {
      self.busy(true); self.error('');
      try {
        const note = self.noteFor(transaction)().trim() || null;
        const updated = await app.services.transactions.decideApproval(transaction.transactionId, { decision, note });
        if (updated.transactionStatus !== 'PENDING_APPROVAL') self.state.data(self.state.data().filter((item) => item.transactionId !== updated.transactionId));
        self.notes.delete(transaction.transactionId);
        await self.load();
        const message = decision === 'REJECT' ? 'Transaction rejected.'
          : updated.transactionStatus === 'COMPLETED' ? 'Transaction approved and posted.'
            : updated.transactionStatus === 'FAILED' ? `Posting failed: ${updated.failureReason || 'accounts-service rejected the transaction.'}`
              : 'Risk service is unavailable; transaction remains held.';
        app.notify(message, updated.transactionStatus === 'COMPLETED' ? 'success' : updated.transactionStatus === 'FAILED' ? 'error' : 'warning');
      } catch (error) { self.error(error.message || 'The decision could not be saved.'); } finally { self.busy(false); }
    };
    self.approve = (transaction) => self.decide(transaction, 'APPROVE'); self.reject = (transaction) => self.decide(transaction, 'REJECT'); self.load();
  }
  return VM;
});
