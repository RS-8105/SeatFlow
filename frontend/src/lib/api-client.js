const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

export class ApiError extends Error {
  constructor(status, errorResponse) {
    super(errorResponse?.message || `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.errorResponse = errorResponse;
  }
}

let tokenGetter = () => {
  try {
    return localStorage.getItem('ticketlock_token');
  } catch {
    return null;
  }
};

let unauthorizedHandler = null;

export function setTokenGetter(fn) {
  tokenGetter = fn;
}

export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn;
}

async function request(path, options = {}) {
  const url = `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  // Attach JWT Bearer token to requests if available and not explicitly provided
  const token = typeof tokenGetter === 'function' ? tokenGetter() : null;
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorData;
    try {
      errorData = await response.json();
    } catch {
      errorData = {
        timestamp: new Date().toISOString(),
        status: response.status,
        error: response.statusText,
        message: `HTTP error ${response.status}`,
        path,
      };
    }

    // Trigger unauthorized callback on 401 for authenticated endpoints (avoid loop on login failures)
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    if (response.status === 401 && unauthorizedHandler && !normalizedPath.startsWith('/auth/')) {
      unauthorizedHandler();
    }

    throw new ApiError(response.status, errorData);
  }

  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return response.json();
  }
  return response.text();
}

export const api = {
  // Auth
  login: (data) =>
    request('/auth/login', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  register: (data) =>
    request('/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  // Events (Public)
  getEvents: () => request('/events'),
  getEvent: (id) => request(`/events/${id}`),
  getShowtimesForEvent: (eventId) => request(`/events/${eventId}/showtimes`),

  // Seats & Holds
  getSeatsForShowtime: (showtimeId) => request(`/showtimes/${showtimeId}/seats`),
  createHold: (showtimeId, data) =>
    request(`/showtimes/${showtimeId}/holds`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  releaseHold: (data) =>
    request('/holds', {
      method: 'DELETE',
      body: JSON.stringify(data),
    }),

  // Bookings (Protected by JWT)
  confirmBooking: (data, idempotencyKey) =>
    request('/holds/confirm', {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(data),
    }),
  getBooking: (id) => request(`/bookings/${id}`),
  getMyBookings: () => request('/bookings/me'),
  cancelBooking: (id) =>
    request(`/bookings/${id}/cancel`, {
      method: 'POST',
    }),

  // Payments (Protected by JWT)
  payForBooking: (bookingId, data) =>
    request(`/bookings/${bookingId}/payment`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  getBookingPayments: (bookingId) => request(`/bookings/${bookingId}/payments`),

  // Concurrency Lab
  simulateRace: (data) =>
    request('/demo/race', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  resetDemo: (data) =>
    request('/demo/reset', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  // SSE Stream URL generator
  getSeatStreamUrl: (showtimeId) => `${API_BASE_URL}/showtimes/${showtimeId}/stream`,
};
