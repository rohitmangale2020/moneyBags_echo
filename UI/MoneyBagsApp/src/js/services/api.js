define([], function () {
  const baseUrl = window.MONEYBAGS_API_BASE_URL || 'http://localhost:8080';

  async function request(path, options) {
    const settings = options || {};
    const headers = Object.assign({ Accept: 'application/json' }, settings.headers || {});
    const token = sessionStorage.getItem('moneybags.accessToken');
    if (token) headers.Authorization = `Bearer ${token}`;
    if (settings.body) headers['Content-Type'] = 'application/json';
    let response;
    try {
      response = await fetch(`${baseUrl}${path}`, Object.assign({}, settings, { headers: headers }));
    } catch (error) {
      throw new Error('Cannot reach the banking API. Confirm that the API gateway is running on port 8080.');
    }
    if (!response.ok) {
      let problem = {};
      try { problem = await response.json(); } catch (ignore) { /* no JSON problem body */ }
      if (response.status === 401) throw new Error('Your session is invalid or has expired. Sign in again.');
      if (response.status === 403) throw new Error('Your role is not permitted to perform this action.');
      throw new Error(problem.detail || problem.title || `Request failed (${response.status}).`);
    }
    if (response.status === 204) return null;
    return response.json();
  }
  return { request: request };
});
