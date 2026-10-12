import React from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { Layout } from '../components/layout/Layout';
import { ProtectedRoute } from '../components/layout/ProtectedRoute';
import { LandingPage } from '../pages/LandingPage';
import { EventsPage } from '../pages/EventsPage';
import { EventDetailsPage } from '../pages/EventDetailsPage';
import { SeatSelectionPage } from '../pages/SeatSelectionPage';
import { CheckoutPage } from '../pages/CheckoutPage';
import { BookingsPage } from '../pages/BookingsPage';
import { BookingDetailsPage } from '../pages/BookingDetailsPage';
import { ConcurrencyLabPage } from '../pages/ConcurrencyLabPage';
import { LoginPage } from '../pages/LoginPage';
import { RegisterPage } from '../pages/RegisterPage';
import { NotFoundPage } from '../pages/NotFoundPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <LandingPage /> },
      { path: 'events', element: <EventsPage /> },
      { path: 'events/:eventId', element: <EventDetailsPage /> },
      { path: 'showtimes/:showtimeId/seats', element: <SeatSelectionPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      {
        path: 'checkout',
        element: (
          <ProtectedRoute>
            <CheckoutPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'bookings',
        element: (
          <ProtectedRoute>
            <BookingsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'bookings/:bookingId',
        element: (
          <ProtectedRoute>
            <BookingDetailsPage />
          </ProtectedRoute>
        ),
      },
      { path: 'lab', element: <ConcurrencyLabPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
