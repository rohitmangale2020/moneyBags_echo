define(['services/apiClient'], function (api) {
  'use strict';
  const e = encodeURIComponent;
  return {
    auth: { login: (u, p) => api.post('/auth/login', { username: u, password: p }) },
    assistant: {
      chat: (message, customerId, transactionId, accountId, module) => api.post('/oda/assistant/chat', {
        message,
        customerId: customerId ? Number(customerId) : null,
        transactionId: transactionId || null,
        accountId: accountId || null,
        module: module || null,
      }),
    },
    users: {
      list: (page = 0, size = 20, query = '') => api.get(`/api/v1/users?page=${e(page)}&size=${e(size)}${query ? `&q=${e(query)}` : ''}`),
      get: (id) => api.get(`/api/v1/users/${e(id)}`),
      create: (d) => api.post('/api/v1/users', d),
      update: (id, d) => api.put(`/api/v1/users/${e(id)}`, d),
      password: (id, password) => api.patch(`/api/v1/users/${e(id)}/password`, { password }),
      status: (id, s) => api.patch(`/api/v1/users/${e(id)}/status`, { status: s }),
      deactivate: (id) => api.delete(`/api/v1/users/${e(id)}`),
    },
    customers: {
      list: (page = 0, size = 10, status = '') => api.get(`/api/customers?page=${e(page)}&size=${e(size)}${status ? `&status=${e(status)}` : ''}`),
      get: (id) => api.get(`/api/customers/${e(id)}`),
      create: (d) => api.post('/api/customers', d),
      update: (id, d) => api.put(`/api/customers/${e(id)}`, d),
      activate: (id) => api.patch(`/api/customers/${e(id)}/activate`),
      deactivate: (id) => api.patch(`/api/customers/${e(id)}/deactivate`),
      remove: (id) => api.delete(`/api/customers/${e(id)}`),
      byCif: (v) => api.get(`/api/customers/search/cif/${e(v)}`),
      byEmail: (v) => api.get(`/api/customers/search/email/${e(v)}`),
      byPhone: (v) => api.get(`/api/customers/search/phone/${e(v)}`),
      byFirstName: (v) => api.get(`/api/customers/search/first-name/${e(v)}`),
      byStatus: (v) => api.get(`/api/customers/status/${e(v)}`),
      addresses: (id) => api.get(`/api/customers/${e(id)}/addresses`),
      address: (id, d) => api.post(`/api/customers/${e(id)}/addresses`, d),
      getAddress: (id, addressId) => api.get(`/api/customers/${e(id)}/addresses/${e(addressId)}`),
      updateAddress: (id, addressId, d) => api.put(`/api/customers/${e(id)}/addresses/${e(addressId)}`, d),
      deleteAddress: (id, addressId) => api.delete(`/api/customers/${e(id)}/addresses/${e(addressId)}`),
      kyc: (id) => api.get(`/api/customers/${e(id)}/kyc`),
      createKyc: (id, d) => api.post(`/api/customers/${e(id)}/kyc`, d),
      updateKyc: (id, d) => api.put(`/api/customers/${e(id)}/kyc`, d),
      document: (id, f, d) => {
        const b = new FormData();
        b.append('file', f);
        b.append('data', new Blob([JSON.stringify(d)], { type: 'application/json' }));
        return api.post(`/api/customers/${e(id)}/documents`, b);
      },
      documents: (id) => api.get(`/api/customers/${e(id)}/documents`),
      getDocument: (id, docId) => api.get(`/api/customers/${e(id)}/documents/${e(docId)}`),
      updateDocument: (id, docId, f, d) => {
        const b = new FormData();
        if (f) b.append('file', f);
        b.append('data', new Blob([JSON.stringify(d)], { type: 'application/json' }));
        return api.putForm(`/api/customers/${e(id)}/documents/${e(docId)}`, b);
      },
      nominee: (id, d) => api.post(`/api/customers/${e(id)}/nominees`, d),
      nominees: (id) => api.get(`/api/customers/${e(id)}/nominees`),
      getNominee: (id, nomineeId) => api.get(`/api/customers/${e(id)}/nominees/${e(nomineeId)}`),
      updateNominee: (id, nomineeId, d) => api.put(`/api/customers/${e(id)}/nominees/${e(nomineeId)}`, d),
      closeNominee: (id, nomineeId) => api.patch(`/api/customers/${e(id)}/nominees/${e(nomineeId)}/close`),
      deleteNominee: (id, nomineeId) => api.delete(`/api/customers/${e(id)}/nominees/${e(nomineeId)}`),
    },
    products: {
      list: () => api.get('/api/v1/products'),
      types: () => api.get('/api/v1/product-types'),
      create: (d) => api.post('/api/v1/products', d),
      createType: (d) => api.post('/api/v1/product-types', d),
      get: (c) => api.get(`/api/v1/products/${e(c)}`),
      history: (c) => api.get(`/api/v1/products/${e(c)}/status-history`),
      retirementImpact: (c) => api.get(`/api/v1/products/${e(c)}/retirement-impact`),
      update: (c, d) => api.put(`/api/v1/products/${e(c)}`, d),
      status: (c, s, r) => api.patch(`/api/v1/products/${e(c)}/status`, { status: s, reason: r }),
      retire: (c, d) => api.post(`/api/v1/products/${e(c)}/retire`, d),
    },
    accounts: {
      list: (page, size, filters = {}) => {
        if (page === undefined) return api.get('/api/accounts');
        const params = new URLSearchParams({ page, size: size || 10 });
        Object.entries(filters).forEach(([key, value]) => {
          if (value !== null && value !== undefined && value !== '' && value !== 'ALL') {
            params.set(key, value);
          }
        });
        return api.get(`/api/accounts?${params.toString()}`);
      },
      customer: (id) => api.get(`/api/accounts?customerId=${e(id)}`),
      number: (v) => api.get(`/api/accounts?accountNumber=${e(v)}`),
      get: (id) => api.get(`/api/accounts/${e(id)}`),
      create: (d) => api.post('/api/accounts', d),
      update: (id, d) => api.put(`/api/accounts/${e(id)}`, d),
    },
    transactions: {
      list: (page, size) => api.get(page === undefined ? '/api/transactions' : `/api/transactions?page=${e(page)}&size=${e(size || 10)}`),
      find: (k, v) => api.get(`/api/transactions?${k}=${e(v)}`),
      get: (id) => api.get(`/api/transactions/${e(id)}`),
      transfer: (d) => api.post('/api/transactions', d),
      update: (id, d) => api.put(`/api/transactions/${e(id)}`, d),
    },
    statements: {
      search: (id, filters = {}) => {
        const params = new URLSearchParams({ accountId: id });
        Object.entries(filters).forEach(([key, value]) => {
          if (value !== null && value !== undefined && value !== '' && value !== 'ALL') {
            params.set(key, value);
          }
        });
        return api.get(`/api/statements?${params.toString()}`);
      },
      monthly: (id, y, m) =>
        api.get(`/api/statements/monthly?accountId=${e(id)}&year=${y}&month=${m}`),
      record: (d) => api.post('/api/statements', d),
    },
    ledger: {
      accounts: () => api.get('/api/ledger/accounts'),
      account: (code) => api.get(`/api/ledger/accounts/${e(code)}`),
      entries: ({ transactionRef, accountCode }) => {
        const params = new URLSearchParams();
        if (transactionRef) params.set('transactionRef', transactionRef);
        if (accountCode) params.set('accountCode', accountCode);
        return api.get(`/api/ledger/entries?${params.toString()}`);
      },
      post: (d) => api.post('/api/ledger/entries', d),
    },
    audits: {
      list: (service, page, size) =>
        api.get(`/api/audit/${e(service)}?page=${e(page || 0)}&size=${e(size || 100)}`),
      get: (service, auditId) => api.get(`/api/audit/${e(service)}/${e(auditId)}`),
    },
  };
});
