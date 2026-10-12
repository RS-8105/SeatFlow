import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { api, setTokenGetter, setUnauthorizedHandler } from './api-client';

const TOKEN_STORAGE_KEY = 'ticketlock_token';
const USER_STORAGE_KEY = 'ticketlock_user';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [token, setToken] = useState(() => {
    try {
      return localStorage.getItem(TOKEN_STORAGE_KEY) || null;
    } catch {
      return null;
    }
  });

  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem(USER_STORAGE_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });

  const [isLoading, setIsLoading] = useState(false);

  // Provide token getter to the api client
  useEffect(() => {
    setTokenGetter(() => token);
  }, [token]);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    try {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(USER_STORAGE_KEY);
    } catch {
      // ignore storage errors
    }
  }, []);

  // Handle 401 responses gracefully by purging stale auth tokens
  useEffect(() => {
    setUnauthorizedHandler(() => {
      logout();
    });
  }, [logout]);

  const login = useCallback(async (credentials) => {
    setIsLoading(true);
    try {
      const res = await api.login({
        username: credentials.username.trim(),
        password: credentials.password,
      });

      const authToken = res.token;
      const authUser = {
        userId: res.userId,
        username: res.username,
        email: res.email,
      };

      setToken(authToken);
      setUser(authUser);

      try {
        localStorage.setItem(TOKEN_STORAGE_KEY, authToken);
        localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(authUser));
      } catch {
        // ignore storage errors
      }

      return authUser;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const register = useCallback(async (data) => {
    setIsLoading(true);
    try {
      const res = await api.register({
        username: data.username.trim(),
        email: data.email.trim(),
        password: data.password,
      });

      const authToken = res.token;
      const authUser = {
        userId: res.userId,
        username: res.username,
        email: res.email,
      };

      setToken(authToken);
      setUser(authUser);

      try {
        localStorage.setItem(TOKEN_STORAGE_KEY, authToken);
        localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(authUser));
      } catch {
        // ignore storage errors
      }

      return authUser;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const value = {
    token,
    user,
    isAuthenticated: Boolean(token && user),
    isLoading,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
