define([
  'knockout',
  'appController',
  'viewModels/util',
  'jspdf',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojdialog',
], function (ko, app, u, jspdf) {
  const dateValue = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };
  const timestamp = (value) => {
    const parsed = Date.parse(value || '');
    return Number.isNaN(parsed) ? 0 : parsed;
  };
  const currentMonthRange = () => {
    const now = new Date();
    return { from: dateValue(new Date(now.getFullYear(), now.getMonth(), 1)), to: dateValue(now) };
  };

  function VM() {
    const s = this;
    const initialRange = currentMonthRange();
    s.state = u.state([]);
    s.accountNumber = ko.observable('');
    s.channel = ko.observable('ALL');
    s.fromDate = ko.observable(initialRange.from);
    s.toDate = ko.observable(initialRange.to);
    s.sortBy = ko.observable('posted-desc');
    s.pageSize = ko.observable(10);
    s.pageSizeOptions = [5, 10, 20];
    s.currentPage = ko.observable(0);
    s.money = u.money;
    s.date = u.date;
    s.activeCustomer = app.activeCustomer;
    s.hasActiveCustomer = app.hasActiveCustomer;
    s.activeAccountId = app.activeAccountId;
    s.customerAccounts = ko.observableArray([]);

    s.loadActiveCustomerAccounts = async () => {
      if (!s.hasActiveCustomer()) {
        s.customerAccounts([]);
        return;
      }
      try {
        const accounts = (await app.services.accounts.customer(s.activeCustomer().customerId))
          .filter((account) => account.status === 'ACTIVE');
        s.customerAccounts(accounts);
        const preferredAccount = accounts.find(
          (account) => String(account.accountId) === String(s.activeAccountId()),
        ) || accounts[0];
        if (preferredAccount) s.accountNumber(String(preferredAccount.accountNumber));
      } catch (_) {
        s.customerAccounts([]);
      }
    };

    s.filteredStatements = ko.pureComputed(() => {
      const sorters = {
        'posted-desc': (a, b) => timestamp(b.postedAt) - timestamp(a.postedAt),
        'posted-asc': (a, b) => timestamp(a.postedAt) - timestamp(b.postedAt),
        'withdrawal-desc': (a, b) => Number(b.withdrawalAmount || 0) - Number(a.withdrawalAmount || 0),
        'deposit-desc': (a, b) => Number(b.depositAmount || 0) - Number(a.depositAmount || 0),
      };
      return s.state.data().slice().sort(sorters[s.sortBy()] || sorters['posted-desc']);
    });

    s.totalPages = ko.pureComputed(() => Math.max(1, Math.ceil(s.filteredStatements().length / Number(s.pageSize()))));
    s.pagedStatements = ko.pureComputed(() => {
      const safePage = Math.min(s.currentPage(), s.totalPages() - 1);
      const start = safePage * Number(s.pageSize());
      return s.filteredStatements().slice(start, start + Number(s.pageSize()));
    });

    s.resultSummary = ko.pureComputed(() => {
      const shown = s.pagedStatements().length;
      const total = s.filteredStatements().length;
      return shown === total ? `${total} entr${total === 1 ? 'y' : 'ies'}` : `${shown} of ${total} entries`;
    });

    s.search = () => {
      const accountNumber = String(s.accountNumber() || '').trim();
      if (!accountNumber) return Promise.resolve(s.state.error('Account number is required.'));
      if (!s.fromDate() || !s.toDate()) {
        return Promise.resolve(s.state.error('Select both from and to dates.'));
      }
      if (s.fromDate() > s.toDate()) {
        return Promise.resolve(s.state.error('From date cannot be after to date.'));
      }
      s.currentPage(0);
      return s.state.run(async () => {
        const accounts = u.list(await app.services.accounts.number(accountNumber));
        if (!accounts.length) throw new Error('Account number was not found.');
        const accountId = String(accounts[0].accountId);
        if (app.setActiveAccount) app.setActiveAccount(accountId);
        return app.services.statements.search(accountId, {
          fromDate: s.fromDate(),
          toDate: s.toDate(),
          channel: s.channel(),
        });
      }).catch(() => null);
    };

    s.currentMonth = () => {
      const range = currentMonthRange();
      s.fromDate(range.from);
      s.toDate(range.to);
    };

    s.clearFilters = () => {
      s.channel('ALL');
      s.sortBy('posted-desc');
      s.currentMonth();
    };

    s.previousPage = () => {
      if (s.currentPage() > 0) s.currentPage(s.currentPage() - 1);
    };
    s.nextPage = () => {
      if (s.currentPage() < s.totalPages() - 1) s.currentPage(s.currentPage() + 1);
    };
    [s.accountNumber, s.channel, s.fromDate, s.toDate, s.sortBy, s.pageSize]
      .forEach((observable) => observable.subscribe(() => s.currentPage(0)));
    s.activeCustomer.subscribe(() => {
      s.accountNumber('');
      s.loadActiveCustomerAccounts();
    });
    s.activeAccountId.subscribe(() => s.loadActiveCustomerAccounts());

    s.displayMoney = (value, currencyCode) =>
      value === null || value === undefined ? '' : u.money(value, currencyCode);

    const statementPassword = (customer) => {
      const letters = String(customer.firstName || '').replace(/[^a-z]/gi, '');
      const dob = String(customer.dob || '').match(/^(\d{4})-(\d{2})-(\d{2})$/);
      if (letters.length < 4 || !dob) {
        throw new Error('A four-letter first name and date of birth are required to protect this statement.');
      }
      return `${letters.slice(0, 4).toUpperCase()}${dob[3]}${dob[2]}`;
    };

    const pdfDate = (value) => value ? new Date(value).toLocaleDateString('en-IN') : '';
    const shortText = (value, maxLength) => {
      const text = String(value || '');
      return text.length > maxLength ? `${text.slice(0, maxLength - 3)}...` : text;
    };

    s.download = () => {
      if (!s.filteredStatements().length) return;
      document.getElementById('statementPasswordDialog').open();
    };

    s.cancelDownload = () => {
      document.getElementById('statementPasswordDialog').close();
    };

    s.confirmDownload = async () => {
      document.getElementById('statementPasswordDialog').close();
      try {
        const accountNumber = String(s.accountNumber() || '').trim();
        const accounts = u.list(await app.services.accounts.number(accountNumber));
        const account = accounts[0];
        if (!account || !account.customerId) throw new Error('The statement account could not be resolved.');
        const customer = await app.services.customers.get(account.customerId);
        const password = statementPassword(customer);
        const { jsPDF } = jspdf;
        const document = new jsPDF({
          orientation: 'landscape',
          unit: 'mm',
          format: 'a4',
          encryption: {
            userPassword: password,
            ownerPassword: password,
            userPermissions: ['print'],
          },
        });
        const margin = 12;
        const pageWidth = document.internal.pageSize.getWidth();
        const pageHeight = document.internal.pageSize.getHeight();
        const columns = [
          { label: 'Posted', width: 26 }, { label: 'Description', width: 72 },
          { label: 'Reference', width: 35 }, { label: 'Channel', width: 31 },
          { label: 'Withdrawal', width: 31, align: 'right' }, { label: 'Deposit', width: 31, align: 'right' },
          { label: 'Closing balance', width: 38, align: 'right' },
        ];
        const header = () => {
          document.setFont('helvetica', 'bold');
          document.setFontSize(16);
          document.text('Account Statement', margin, 15);
          document.setFont('helvetica', 'normal');
          document.setFontSize(9);
          document.text(`Account: ${accountNumber}   |   ${s.fromDate()} to ${s.toDate()}`, margin, 22);
          document.text(`Customer: ${customer.firstName} ${customer.lastName || ''}`.trim(), margin, 27);
          let x = margin;
          document.setFillColor(31, 78, 121);
          document.rect(margin, 32, pageWidth - (margin * 2), 7, 'F');
          document.setTextColor(255, 255, 255);
          document.setFont('helvetica', 'bold');
          document.setFontSize(7);
          columns.forEach((column) => {
            const textX = column.align === 'right' ? x + column.width - 1 : x + 1;
            document.text(column.label, textX, 36.5,
              column.align === 'right' ? { align: 'right' } : undefined);
            x += column.width;
          });
          document.setTextColor(0, 0, 0);
          return 39;
        };
        let y = header();
        document.setFont('helvetica', 'normal');
        document.setFontSize(7);
        s.filteredStatements().forEach((entry, index) => {
          if (y > pageHeight - 14) {
            document.addPage();
            y = header();
            document.setFont('helvetica', 'normal');
            document.setFontSize(7);
          }
          if (index % 2 === 0) {
            document.setFillColor(245, 248, 252);
            document.rect(margin, y, pageWidth - (margin * 2), 6, 'F');
          }
          const values = [
            pdfDate(entry.postedAt), shortText(entry.description, 48), shortText(entry.transactionRef, 23),
            shortText(entry.channel, 18), s.displayMoney(entry.withdrawalAmount, entry.currencyCode),
            s.displayMoney(entry.depositAmount, entry.currencyCode), s.displayMoney(entry.closingBalance, entry.currencyCode),
          ];
          let x = margin;
          values.forEach((value, valueIndex) => {
            const column = columns[valueIndex];
            const textX = column.align === 'right' ? x + column.width - 1 : x + 1;
            document.text(String(value || ''), textX, y + 4,
              column.align === 'right' ? { align: 'right' } : undefined);
            x += columns[valueIndex].width;
          });
          y += 6;
        });
        document.setFontSize(7);
        document.setTextColor(90, 90, 90);
        document.text('Password: first four letters of the account holder first name (uppercase) + date of birth (DDMM).', margin, pageHeight - 7);
        document.save(`statement-${accountNumber}-${s.fromDate()}-${s.toDate()}.pdf`);
        app.notify('Password-protected statement PDF downloaded.', 'success');
      } catch (error) {
        s.state.error(error.message || 'The statement PDF could not be created.');
      }
    };

    s.loadActiveCustomerAccounts();
  }
  return VM;
});
