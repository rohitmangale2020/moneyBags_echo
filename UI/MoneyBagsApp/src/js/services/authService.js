define(['services/api'], function (api) {
  function decodeToken(token) {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(decodeURIComponent(atob(payload).split('').map((character) => `%${(`00${character.charCodeAt(0).toString(16)}`).slice(-2)}`).join('')));
  }
  async function login(username, password) {
    const result = await api.request('/auth/login', { method: 'POST', body: JSON.stringify({ username: username, password: password }) });
    const claims = decodeToken(result.accessToken);
    const role = (claims.roles || [])[0];
    if (!role) throw new Error('The authentication token does not include an application role.');
    sessionStorage.setItem('moneybags.accessToken', result.accessToken);
    sessionStorage.setItem('moneybags.username', claims.sub);
    sessionStorage.setItem('moneybags.role', role);
    return { username: claims.sub, role: role };
  }
  function session() {
    const token = sessionStorage.getItem('moneybags.accessToken');
    const username = sessionStorage.getItem('moneybags.username');
    const role = sessionStorage.getItem('moneybags.role');
    return token && username && role ? { username: username, role: role } : null;
  }
  function logout() { ['moneybags.accessToken', 'moneybags.username', 'moneybags.role'].forEach((key) => sessionStorage.removeItem(key)); }
  return { login: login, session: session, logout: logout };
});
