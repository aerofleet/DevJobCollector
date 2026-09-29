const API_BASE_URL = (import.meta.env.VITE_ADMIN_API_BASE_URL || '/api/v1/admin').replace(/\/$/, '');

export class AdminApiError extends Error {
  constructor(message, status, code, requestId) {
    super(message);
    this.name = 'AdminApiError';
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }
}

const readCookie = (name) => document.cookie
  .split('; ')
  .find((part) => part.startsWith(`${name}=`))
  ?.split('=')
  .slice(1)
  .join('=');

export const adminRequest = async (path, options = {}) => {
  const method = options.method || 'GET';
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) {
    const csrfToken = readCookie('DJC_ADMIN_CSRF');
    if (csrfToken) headers.set('X-CSRF-TOKEN', decodeURIComponent(csrfToken));
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    method,
    headers,
    credentials: 'include',
  });
  const requestId = response.headers.get('X-Request-Id') || '';
  const payload = response.status === 204
    ? null
    : await response.json().catch(() => null);

  if (!response.ok) {
    throw new AdminApiError(
      payload?.message || '관리자 API 요청을 처리하지 못했습니다.',
      response.status,
      payload?.code || 'ADMIN_API_ERROR',
      payload?.requestId || requestId,
    );
  }
  return payload;
};

export const adminApi = {
  login: (credentials) => adminRequest('/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  }),
  logout: () => adminRequest('/auth/logout', { method: 'POST' }),
  me: () => adminRequest('/me'),
  dashboardSummary: () => adminRequest('/dashboard/summary'),
  users: (params) => adminRequest(`/users?${new URLSearchParams(params)}`),
  user: (id) => adminRequest(`/users/${id}`),
  moderateUser: (id, body) => adminRequest(`/users/${id}/status`, {
    method: 'PATCH', body: JSON.stringify(body),
  }),
  jobs: (params) => adminRequest(`/jobs?${new URLSearchParams(params)}`),
  job: (id) => adminRequest(`/jobs/${id}`),
  moderateJob: (id, body) => adminRequest(`/jobs/${id}/status`, {
    method: 'PATCH', body: JSON.stringify(body),
  }),
};
