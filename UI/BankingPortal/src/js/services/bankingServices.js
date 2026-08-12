define(['services/apiClient'], function (api) {
  'use strict';
  const e = encodeURIComponent;
  return {
    auth: { login: (u, p) => api.post('/auth/login', { username: u, password: p }) },
    users: {
      list: () => api.get('/api/v1/users?page=0&size=100&sort=id,desc'),
      get: (id) => api.get(`/api/v1/users/${e(id)}`),
      create: (d) => api.post('/api/v1/users', d),
      update: (id, d) => api.put(`/api/v1/users/${e(id)}`, d),
      status: (id, s) => api.patch(`/api/v1/users/${e(id)}/status`, { status: s }),
      deactivate: (id) => api.delete(`/api/v1/users/${e(id)}`),
    },
    customers: {
      list: () => api.get('/api/customers'),
      get: (id) => api.get(`/api/customers/${e(id)}`),
      create: (d) => api.post('/api/customers', d),
      update: (id, d) => api.put(`/api/customers/${e(id)}`, d),
      activate: (id) => api.patch(`/api/customers/${e(id)}/activate`),
      deactivate: (id) => api.patch(`/api/customers/${e(id)}/deactivate`),
      address: (id, d) => api.post(`/api/customers/${e(id)}/addresses`, d),
      kyc: (id) => api.get(`/api/customers/${e(id)}/kyc`),
      createKyc: (id, d) => api.post(`/api/customers/${e(id)}/kyc`, d),
      updateKyc: (id, d) => api.put(`/api/customers/${e(id)}/kyc`, d),
      document: (id, f, d) => {
        const b = new FormData();
        b.append('file', f);
        b.append('data', new Blob([JSON.stringify(d)], { type: 'application/json' }));
        return api.post(`/api/customers/${e(id)}/documents`, b);
      },
      nominee: (id, d) => api.post(`/api/customers/${e(id)}/nominees`, d),
    },
    products: {
      list: () => api.get('/api/v1/products'),
      types: () => api.get('/api/v1/product-types'),
      create: (d) => api.post('/api/v1/products', d),
      createType: (d) => api.post('/api/v1/product-types', d),
      update: (c, d) => api.put(`/api/v1/products/${e(c)}`, d),
      status: (c, s, r) => api.patch(`/api/v1/products/${e(c)}/status`, { status: s, reason: r }),
      retire: (c) => api.delete(`/api/v1/products/${e(c)}`),
    },
    accounts: {
      customer: (id) => api.get(`/api/accounts?customerId=${e(id)}`),
      number: (v) => api.get(`/api/accounts?accountNumber=${e(v)}`),
      create: (d) => api.post('/api/accounts', d),
      update: (id, d) => api.put(`/api/accounts/${e(id)}`, d),
    },
    transactions: {
      find: (k, v) => api.get(`/api/transactions?${k}=${e(v)}`),
      transfer: (d) => api.post('/api/transactions', d),
    },
    statements: {
      account: (id) => api.get(`/api/statements?accountId=${e(id)}`),
      monthly: (id, y, m) =>
        api.get(`/api/statements/monthly?accountId=${e(id)}&year=${y}&month=${m}`),
    },
  };
});
