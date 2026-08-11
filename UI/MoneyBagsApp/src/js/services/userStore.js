define(['knockout', 'services/api'], function (ko, api) {
  const users = ko.observableArray([]);
  const selectedUser = ko.observable(null);
  const load = async () => {
    const page = await api.request('/api/v1/users?page=0&size=100&sort=id');
    users(page.content || []);
    return users();
  };
  const create = (payload) => api.request('/api/v1/users', { method: 'POST', body: JSON.stringify(payload) });
  const update = (id, payload) => api.request(`/api/v1/users/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
  const updateStatus = (id, status) => api.request(`/api/v1/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status: status }) });
  const updatePassword = (id, password) => api.request(`/api/v1/users/${id}/password`, { method: 'PATCH', body: JSON.stringify({ password: password }) });
  const deactivate = (id) => api.request(`/api/v1/users/${id}`, { method: 'DELETE' });
  return { users: users, selectedUser: selectedUser, load: load, create: create, update: update, updateStatus: updateStatus, updatePassword: updatePassword, deactivate: deactivate };
});
