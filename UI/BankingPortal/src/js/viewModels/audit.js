define([
  'knockout',
  'appController',
  'viewModels/util',
  'ojs/ojbutton',
  'ojs/ojdialog',
], function (ko, app, u) {
  'use strict';

  const PAGE_SIZE = 100;
  const MAX_PAGES = 20;
  const EMPTY = '—';
  const OWN = Object.prototype.hasOwnProperty;

  const CATEGORIES = [
    { id: 'security', label: 'Security', eyebrow: 'AUTH', description: 'Sign-ins and access decisions' },
    { id: 'users', label: 'Users', eyebrow: 'IDENTITY', description: 'User, role and status changes' },
    { id: 'customers', label: 'Customers', eyebrow: 'CUSTOMER', description: 'Profiles, KYC and related records' },
    { id: 'products', label: 'Products', eyebrow: 'CATALOGUE', description: 'Products, rates, terms and fees' },
    { id: 'accounts', label: 'Accounts', eyebrow: 'ACCOUNTS', description: 'Account, holder and balance changes' },
    { id: 'transactions', label: 'Transactions', eyebrow: 'PAYMENTS', description: 'Transactions, approvals and statements' },
    { id: 'api-access', label: 'API access', eyebrow: 'GATEWAY', description: 'Request results and service response times' },
  ];

  const isPresent = (value) => value !== null && value !== undefined && value !== '';
  const isUuid = (value) => /^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(String(value || ''));
  const timeValue = (value) => {
    const parsed = Date.parse(value || '');
    return Number.isNaN(parsed) ? 0 : parsed;
  };

  function humanize(value) {
    if (!isPresent(value)) return EMPTY;
    const words = String(value)
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .replace(/[_-]+/g, ' ')
      .trim()
      .toLowerCase();
    const text = words.charAt(0).toUpperCase() + words.slice(1);
    return text
      .replace(/\bApi\b/g, 'API')
      .replace(/\bKyc\b/g, 'KYC')
      .replace(/\bIp\b/g, 'IP')
      .replace(/\bId\b/g, 'ID');
  }

  function parseJson(value) {
    if (!isPresent(value)) return {};
    if (typeof value === 'object') return value;
    try {
      const parsed = JSON.parse(value);
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch (_) {
      return {};
    }
  }

  function formatValue(value) {
    if (value === null || value === undefined || value === '') return 'Not set';
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (Array.isArray(value)) return value.length ? value.map(formatValue).join(', ') : 'None';
    if (typeof value === 'object') {
      return Object.keys(value)
        .map((key) => `${humanize(key)}: ${formatValue(value[key])}`)
        .join(' · ') || 'None';
    }
    if (typeof value === 'number') return new Intl.NumberFormat('en-IN').format(value);
    const text = String(value);
    if (/^\d{4}-\d{2}-\d{2}T/.test(text) && timeValue(text)) return u.date(text);
    if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
      return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(new Date(`${text}T00:00:00`));
    }
    if (/^[A-Z][A-Z0-9_]*$/.test(text)) return humanize(text);
    return text;
  }

  function money(value, currency) {
    return isPresent(value) ? u.money(value, currency || 'INR') : EMPTY;
  }

  function transition(before, after) {
    if (!isPresent(before) && !isPresent(after)) return '';
    if (isPresent(before) && isPresent(after) && String(before) === String(after)) return '';
    return `${formatValue(before)} → ${formatValue(after)}`;
  }

  function formatChangeValue(field, value, log) {
    if (!isPresent(value)) return 'Not set';
    if (/rate|percentage|percent/i.test(field) && !Number.isNaN(Number(value))) return `${Number(value)}%`;
    if (/amount|balance|fee/i.test(field) && !Number.isNaN(Number(value))) {
      return money(value, log.currencyCode);
    }
    return formatValue(value);
  }

  function pushField(fields, label, value) {
    if (isPresent(value)) fields.push({ label, value: formatValue(value) });
  }

  function failureSummary(log) {
    const raw = log.failureReason || log.errorMessage;
    if (!isPresent(raw)) return isPresent(log.errorCode) ? String(log.errorCode) : '';
    const parsed = parseJson(raw);
    const hasPayload = Object.keys(parsed).length > 0;
    const status = hasPayload
      ? (parsed.status || parsed.statusCode || parsed.code || log.errorCode)
      : log.errorCode;
    const message = hasPayload
      ? (parsed.message || parsed.error || parsed.detail || formatValue(parsed))
      : String(raw);
    return isPresent(status) ? `${status} : ${message}` : message;
  }

  function friendlyActor(log) {
    if (isPresent(log.username)) return log.username;
    if (isPresent(log.actorId) && !isUuid(log.actorId)) return log.actorId;
    return humanize(log.actorType || 'SYSTEM');
  }

  function activityTitle(log) {
    return humanize(log.action || 'Audit event');
  }

  function activityCaption(log) {
    const description = String(log.description || '').trim();
    return description && description.toLowerCase() !== activityTitle(log).toLowerCase()
      ? description
      : '';
  }

  function subjectFor(category, log) {
    switch (category) {
      case 'security':
        return log.username || (isPresent(log.userId) ? `User ${log.userId}` : 'Authentication');
      case 'users':
        return isPresent(log.targetUserId) ? `User ${log.targetUserId}` : 'Platform user';
      case 'customers':
        return isPresent(log.customerId) ? `Customer ${log.customerId}` : humanize(log.relatedEntityType || 'Customer record');
      case 'products':
        return isPresent(log.productId) ? `Product ${log.productId}` : humanize(log.componentType || 'Product configuration');
      case 'accounts':
        return log.transactionRef || (isPresent(log.customerId) && !isUuid(log.customerId)
          ? `Customer ${log.customerId}` : 'Account activity');
      case 'transactions':
        return log.transactionRef || humanize(log.relatedEntityType || 'Transaction activity');
      case 'api-access':
        return humanize(log.targetService || 'API gateway');
      default:
        return 'Audit activity';
    }
  }

  function contextFor(category, log) {
    const status = transition(log.previousStatus, log.newStatus);
    switch (category) {
      case 'security':
        return log.clientIp ? `From ${log.clientIp}` : humanize(log.actorType);
      case 'users':
        return status || transition(log.previousRole, log.newRole) || 'User record changed';
      case 'customers':
        return status || humanize(log.relatedEntityType || 'Customer record');
      case 'products':
        return log.changeSummary || status || humanize(log.componentType || 'Product settings');
      case 'accounts':
        if (isPresent(log.balanceBefore) || isPresent(log.balanceAfter)) {
          return `${money(log.balanceBefore, log.currencyCode)} → ${money(log.balanceAfter, log.currencyCode)}`;
        }
        return isPresent(log.amount) ? money(log.amount, log.currencyCode) : (status || 'Account record changed');
      case 'transactions':
        return isPresent(log.amount) ? money(log.amount, log.currencyCode) : (status || humanize(log.relatedEntityType || 'Transaction'));
      case 'api-access':
        return `${log.httpMethod || 'HTTP'} ${log.requestPath || ''}`.trim();
      default:
        return log.description || EMPTY;
    }
  }

  function contextNote(category, log) {
    if (category === 'api-access') {
      const duration = isPresent(log.durationMs) ? ` · ${log.durationMs} ms` : '';
      return `HTTP ${log.httpStatus || EMPTY}${duration}`;
    }
    if (category === 'transactions' && transition(log.previousStatus, log.newStatus)) {
      return transition(log.previousStatus, log.newStatus);
    }
    if (category === 'products' && log.componentType) return humanize(log.componentType);
    if (category === 'customers' && log.relatedEntityType) return humanize(log.relatedEntityType);
    if (category === 'accounts' && log.transactionRef) return `Transaction ${log.transactionRef}`;
    return '';
  }

  function detailFields(category, log) {
    const fields = [];
    switch (category) {
      case 'security':
        pushField(fields, 'Username', log.username);
        pushField(fields, 'Client IP', log.clientIp);
        break;
      case 'users':
        pushField(fields, 'Target user', isPresent(log.targetUserId) ? `User ${log.targetUserId}` : null);
        pushField(fields, 'Status change', transition(log.previousStatus, log.newStatus));
        pushField(fields, 'Role change', transition(log.previousRole, log.newRole));
        break;
      case 'customers':
        pushField(fields, 'Customer', isPresent(log.customerId) ? `Customer ${log.customerId}` : null);
        pushField(fields, 'Related record', isPresent(log.relatedEntityType) ? humanize(log.relatedEntityType) : null);
        pushField(fields, 'Status change', transition(log.previousStatus, log.newStatus));
        break;
      case 'products':
        pushField(fields, 'Product', isPresent(log.productId) ? `Product ${log.productId}` : null);
        pushField(fields, 'Configuration area', isPresent(log.componentType) ? humanize(log.componentType) : null);
        pushField(fields, 'Status change', transition(log.previousStatus, log.newStatus));
        pushField(fields, 'Change summary', log.changeSummary);
        break;
      case 'accounts':
        pushField(fields, 'Transaction reference', log.transactionRef);
        pushField(fields, 'Customer', isPresent(log.customerId) && !isUuid(log.customerId) ? `Customer ${log.customerId}` : null);
        pushField(fields, 'Amount', isPresent(log.amount) ? money(log.amount, log.currencyCode) : null);
        pushField(fields, 'Balance change', (isPresent(log.balanceBefore) || isPresent(log.balanceAfter))
          ? `${money(log.balanceBefore, log.currencyCode)} → ${money(log.balanceAfter, log.currencyCode)}` : null);
        pushField(fields, 'Status change', transition(log.previousStatus, log.newStatus));
        pushField(fields, 'Reason', log.reason);
        break;
      case 'transactions':
        pushField(fields, 'Amount', isPresent(log.amount) ? money(log.amount, log.currencyCode) : null);
        pushField(fields, 'Status change', transition(log.previousStatus, log.newStatus));
        pushField(fields, 'Related record', isPresent(log.relatedEntityType) ? humanize(log.relatedEntityType) : null);
        pushField(fields, 'Failure reason', failureSummary(log));
        break;
      case 'api-access':
        pushField(fields, 'Service', isPresent(log.targetService) ? humanize(log.targetService) : null);
        pushField(fields, 'Request', `${log.httpMethod || 'HTTP'} ${log.requestPath || ''}`.trim());
        pushField(fields, 'HTTP status', log.httpStatus);
        pushField(fields, 'Response time', isPresent(log.durationMs) ? `${log.durationMs} ms` : null);
        pushField(fields, 'Username', log.username);
        pushField(fields, 'Client IP', log.clientIp);
        break;
      default:
        break;
    }
    return fields;
  }

  function technicalFields(category, log) {
    const fields = [];
    [
      ['Audit ID', log.auditId],
      ['Correlation ID', log.correlationId],
      ['Actor ID', log.actorId],
      ['User ID', log.userId],
      ['Account ID', log.accountId],
      ['Transaction ID', log.transactionId],
      ['Transaction reference', category === 'transactions' ? log.transactionRef : null],
      ['Debit account ID', log.debitAccountId],
      ['Credit account ID', log.creditAccountId],
      ['Operation ID', log.operationId],
      ['Component ID', log.componentId],
      ['Related record ID', log.relatedEntityId],
    ].forEach((entry) => pushField(fields, entry[0], entry[1]));
    return fields;
  }

  function changeRows(log) {
    const before = parseJson(log.oldValuesJson);
    const after = parseJson(log.newValuesJson);
    const keys = Array.from(new Set(Object.keys(before).concat(Object.keys(after))));
    const rows = keys.map((key) => ({
      field: humanize(key),
      previous: OWN.call(before, key) ? formatChangeValue(key, before[key], log) : 'Not set',
      current: OWN.call(after, key) ? formatChangeValue(key, after[key], log) : 'Not set',
      changed: JSON.stringify(before[key]) !== JSON.stringify(after[key]),
    }));

    if (!keys.some((key) => /status/i.test(key)) && String(log.previousStatus || '') !== String(log.newStatus || '')) {
      rows.push({ field: 'Status', previous: formatValue(log.previousStatus), current: formatValue(log.newStatus), changed: true });
    }
    if (!keys.some((key) => /role/i.test(key)) && String(log.previousRole || '') !== String(log.newRole || '')) {
      rows.push({ field: 'Role', previous: formatValue(log.previousRole), current: formatValue(log.newRole), changed: true });
    }
    if (!keys.some((key) => /balance/i.test(key))
        && JSON.stringify(log.balanceBefore) !== JSON.stringify(log.balanceAfter)) {
      rows.push({
        field: 'Available balance',
        previous: money(log.balanceBefore, log.currencyCode),
        current: money(log.balanceAfter, log.currencyCode),
        changed: true,
      });
    }
    return rows.filter((row) => row.changed);
  }

  function VM() {
    const s = this;
    const cache = {};
    let loadSequence = 0;

    s.app = app;
    s.categories = CATEGORIES;
    s.activeCategory = ko.observable(CATEGORIES[0].id);
    s.state = u.state([]);
    s.totalAvailable = ko.observable(0);
    s.truncated = ko.observable(false);
    s.query = ko.observable('');
    s.outcomeFilter = ko.observable('ALL');
    s.actionFilter = ko.observable('ALL');
    s.dateFrom = ko.observable('');
    s.dateTo = ko.observable('');
    s.sortBy = ko.observable('created-desc');
    s.currentPage = ko.observable(0);
    s.pageSize = ko.observable(20);
    s.selectedLog = ko.observable(null);
    s.selectedFields = ko.observableArray([]);
    s.selectedChanges = ko.observableArray([]);
    s.selectedTechnical = ko.observableArray([]);

    s.activeConfig = ko.pureComputed(() =>
      CATEGORIES.find((category) => category.id === s.activeCategory()) || CATEGORIES[0]);
    s.actions = ko.pureComputed(() => Array.from(new Set(
      s.state.data().map((log) => log.action).filter(Boolean),
    )).sort());

    s.activityTitle = activityTitle;
    s.activityCaption = activityCaption;
    s.subject = (log) => subjectFor(s.activeCategory(), log);
    s.context = (log) => contextFor(s.activeCategory(), log);
    s.contextNote = (log) => contextNote(s.activeCategory(), log);
    s.actor = friendlyActor;
    s.date = u.date;
    s.humanize = humanize;
    s.failureSummary = failureSummary;
    s.outcomeClass = (outcome) => `audit-${String(outcome || '').toLowerCase()}`;

    s.filteredLogs = ko.pureComputed(() => {
      const query = s.query().trim().toLowerCase();
      const from = s.dateFrom() ? new Date(`${s.dateFrom()}T00:00:00`).getTime() : null;
      const to = s.dateTo() ? new Date(`${s.dateTo()}T23:59:59.999`).getTime() : null;
      const category = s.activeCategory();
      const logs = s.state.data().filter((log) => {
        const created = timeValue(log.createdAt);
        const searchable = [
          activityTitle(log), activityCaption(log), subjectFor(category, log), contextFor(category, log),
          contextNote(category, log), friendlyActor(log), log.action, log.outcome,
        ].join(' ').toLowerCase();
        return (!query || searchable.includes(query))
          && (s.outcomeFilter() === 'ALL' || log.outcome === s.outcomeFilter())
          && (s.actionFilter() === 'ALL' || log.action === s.actionFilter())
          && (from === null || created >= from)
          && (to === null || created <= to);
      });
      const sorters = {
        'created-desc': (a, b) => timeValue(b.createdAt) - timeValue(a.createdAt),
        'created-asc': (a, b) => timeValue(a.createdAt) - timeValue(b.createdAt),
        'activity-asc': (a, b) => activityTitle(a).localeCompare(activityTitle(b)),
        'actor-asc': (a, b) => friendlyActor(a).localeCompare(friendlyActor(b)),
        'outcome-asc': (a, b) => String(a.outcome).localeCompare(String(b.outcome)),
      };
      return logs.slice().sort(sorters[s.sortBy()] || sorters['created-desc']);
    });
    s.totalViewPages = ko.pureComputed(() => Math.max(1, Math.ceil(s.filteredLogs().length / Number(s.pageSize()))));
    s.pagedLogs = ko.pureComputed(() => {
      const start = s.currentPage() * Number(s.pageSize());
      return s.filteredLogs().slice(start, start + Number(s.pageSize()));
    });
    s.resultSummary = ko.pureComputed(() => {
      const matching = s.filteredLogs().length;
      const loaded = s.state.data().length;
      const total = s.totalAvailable();
      const base = matching === loaded
        ? `${loaded} audit ${loaded === 1 ? 'event' : 'events'}`
        : `${matching} of ${loaded} loaded events match`;
      return s.truncated() ? `${base} · newest ${loaded} of ${total} loaded` : base;
    });
    s.pageSummary = ko.pureComputed(() => `Page ${s.currentPage() + 1} of ${s.totalViewPages()}`);
    s.detailTitle = ko.pureComputed(() => s.selectedLog() ? activityTitle(s.selectedLog()) : 'Audit details');
    s.detailDescription = ko.pureComputed(() => {
      const log = s.selectedLog();
      return log ? (log.description || activityTitle(log)) : '';
    });

    async function fetchCategory(category) {
      const first = await app.services.audits.list(category, 0, PAGE_SIZE);
      const firstRows = u.list(first);
      const total = Number(first.totalElements === undefined ? firstRows.length : first.totalElements);
      const totalPages = Number(first.totalPages === undefined ? 1 : first.totalPages);
      const pagesToLoad = Math.min(totalPages, MAX_PAGES);
      const requests = [];
      for (let page = 1; page < pagesToLoad; page += 1) {
        requests.push(app.services.audits.list(category, page, PAGE_SIZE));
      }
      const remaining = await Promise.all(requests);
      const rows = firstRows.concat(...remaining.map(u.list));
      return { rows, total, truncated: totalPages > MAX_PAGES };
    }

    s.load = (force) => {
      const category = s.activeCategory();
      const sequence = ++loadSequence;
      if (!force && cache[category]) {
        s.state.data(cache[category].rows);
        s.totalAvailable(cache[category].total);
        s.truncated(cache[category].truncated);
        return Promise.resolve(cache[category].rows);
      }
      return s.state.run(async () => {
        const result = await fetchCategory(category);
        if (sequence !== loadSequence) return s.state.data();
        cache[category] = result;
        s.totalAvailable(result.total);
        s.truncated(result.truncated);
        return result.rows;
      }).catch(() => null);
    };

    s.selectCategory = (category) => {
      if (s.activeCategory() === category.id) return;
      s.activeCategory(category.id);
      s.actionFilter('ALL');
      s.currentPage(0);
      s.load(false);
    };
    s.refresh = () => {
      delete cache[s.activeCategory()];
      s.currentPage(0);
      s.load(true);
    };
    s.clearFilters = () => {
      s.query('');
      s.outcomeFilter('ALL');
      s.actionFilter('ALL');
      s.dateFrom('');
      s.dateTo('');
      s.sortBy('created-desc');
      s.currentPage(0);
    };
    s.previousPage = () => {
      if (s.currentPage() > 0) s.currentPage(s.currentPage() - 1);
    };
    s.nextPage = () => {
      if (s.currentPage() < s.totalViewPages() - 1) s.currentPage(s.currentPage() + 1);
    };

    s.openDetails = (log) => {
      s.selectedLog(log);
      s.selectedFields(detailFields(s.activeCategory(), log));
      s.selectedChanges(changeRows(log));
      s.selectedTechnical(technicalFields(s.activeCategory(), log));
      document.getElementById('auditDetailDialog').open();
    };
    s.closeDetails = () => document.getElementById('auditDetailDialog').close();
    s.handleRowKey = (log, event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        s.openDetails(log);
      }
      return true;
    };

    [s.query, s.outcomeFilter, s.actionFilter, s.dateFrom, s.dateTo, s.sortBy, s.pageSize]
      .forEach((observable) => observable.subscribe(() => s.currentPage(0)));
    s.filteredLogs.subscribe(() => {
      if (s.currentPage() >= s.totalViewPages()) s.currentPage(Math.max(0, s.totalViewPages() - 1));
    });

    s.load(false);
  }

  return VM;
});
