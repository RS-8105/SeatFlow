import React, { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { Lock, User, Mail, UserPlus, AlertCircle, ArrowRight, Ticket } from 'lucide-react';

const resolveTargetDestination = (fromLoc) => {
  if (!fromLoc) return { path: '/events', state: undefined };

  let path = '/events';
  let state = undefined;

  if (typeof fromLoc === 'string') {
    path = fromLoc;
  } else if (typeof fromLoc === 'object') {
    path = fromLoc.pathname
      ? fromLoc.pathname + (fromLoc.search || '')
      : '/events';
    state = fromLoc.state;
  }

  // Prevent redirect loops or landing on auth routes after registration
  const normalizedPath = path.split('?')[0].replace(/\/+$/, '');
  if (normalizedPath === '/login' || normalizedPath === '/register' || normalizedPath === '') {
    path = '/events';
    state = undefined;
  }

  return { path, state };
};

export const RegisterPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { register, isAuthenticated } = useAuth();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  // Preserve intended destination and state (such as selected seats)
  const fromLocation = location.state?.from;

  // If already authenticated, redirect to target destination or events
  useEffect(() => {
    if (isAuthenticated) {
      const { path, state } = resolveTargetDestination(fromLocation);
      navigate(path, { replace: true, state });
    }
  }, [isAuthenticated, fromLocation, navigate]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage(null);

    const trimmedUsername = username.trim();
    const trimmedEmail = email.trim();

    if (!trimmedUsername || !trimmedEmail || !password) {
      setErrorMessage('Please fill in all required fields.');
      return;
    }

    if (trimmedUsername.length < 3 || trimmedUsername.length > 50) {
      setErrorMessage('Username must be between 3 and 50 characters.');
      return;
    }

    if (!trimmedEmail.includes('@') || !trimmedEmail.includes('.')) {
      setErrorMessage('Please provide a valid email address.');
      return;
    }

    if (password.length < 6) {
      setErrorMessage('Password must be at least 6 characters long.');
      return;
    }

    setIsSubmitting(true);
    try {
      await register({
        username: trimmedUsername,
        email: trimmedEmail,
        password,
      });

      const { path, state } = resolveTargetDestination(fromLocation);
      navigate(path, {
        replace: true,
        state,
      });
    } catch (err) {
      setErrorMessage(
        err.status === 409
          ? 'An account with this username or email already exists.'
          : err.message || 'Registration failed. Please check your information.'
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-md mx-auto my-12 sm:my-16">
      <div className="bg-[#ffffff] border border-[#1b1c19] rounded-sm shadow-[4px_4px_0px_0px_#1b1c19] overflow-hidden">
        {/* Header Band */}
        <div className="p-6 bg-[#fbf9f4] border-b border-[#e4e2dd]">
          <div className="flex items-center justify-between mb-2">
            <span className="font-mono text-[10px] uppercase px-1.5 py-0.5 bg-[#efeee8] border border-[#e4e2dd] rounded-sm text-[#5a413c]">
              ACCOUNT ISSUANCE
            </span>
            <span className="font-mono text-[10px] text-[#576244] font-semibold">
              PATRON ONBOARDING
            </span>
          </div>
          <h1 className="font-headline text-3xl text-[#1b1c19] font-normal">
            Register Patron Account
          </h1>
          <p className="text-xs text-[#5a413c] mt-0.5">
            Create your authenticated identity to unlock reservation holds.
          </p>
        </div>

        {/* Form Body */}
        <div className="p-6 bg-[#efeee8]">
          {errorMessage && (
            <div className="mb-4 bg-[#ffdad6]/60 border border-[#ba1a1a]/40 p-3 rounded-sm text-[#ba1a1a] text-xs font-mono flex items-start gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{errorMessage}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block font-mono text-[10px] uppercase text-[#5a413c] mb-1">
                Username <span className="text-[#ae2714]">*</span>
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="clara_wiesner"
                required
                autoComplete="username"
                className="w-full bg-[#ffffff] text-[#1b1c19] border border-[#d1c9b8] px-3.5 py-2.5 rounded-sm text-sm focus:outline-none focus:border-[#1b1c19]"
              />
              <span className="font-mono text-[9px] text-[#5a413c] mt-0.5 block">
                3 to 50 characters, unique identifier.
              </span>
            </div>

            <div>
              <label className="block font-mono text-[10px] uppercase text-[#5a413c] mb-1">
                Email Address <span className="text-[#ae2714]">*</span>
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="patron@orchestra.org"
                required
                autoComplete="email"
                className="w-full bg-[#ffffff] text-[#1b1c19] border border-[#d1c9b8] px-3.5 py-2.5 rounded-sm text-sm focus:outline-none focus:border-[#1b1c19]"
              />
            </div>

            <div>
              <label className="block font-mono text-[10px] uppercase text-[#5a413c] mb-1">
                Password <span className="text-[#ae2714]">*</span>
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                autoComplete="new-password"
                className="w-full bg-[#ffffff] text-[#1b1c19] border border-[#d1c9b8] px-3.5 py-2.5 rounded-sm text-sm focus:outline-none focus:border-[#1b1c19]"
              />
              <span className="font-mono text-[9px] text-[#5a413c] mt-0.5 block">
                Minimum 6 characters.
              </span>
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full bg-[#d1402a] hover:bg-[#30312d] disabled:opacity-50 text-[#fffafa] py-3 rounded-sm font-mono text-xs uppercase font-bold tracking-wider transition-all shadow-[2px_2px_0px_0px_#1b1c19] active:translate-x-[1px] active:translate-y-[1px] flex items-center justify-center gap-2 mt-2"
            >
              <span>{isSubmitting ? 'Registering Account...' : 'Create Patron Account'}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </form>

          {/* Switch to Login */}
          <div className="mt-6 pt-4 border-t border-[#e4e2dd] text-center font-mono text-xs text-[#5a413c]">
            <span>Already registered? </span>
            <Link
              to="/login"
              state={fromLocation && fromLocation !== '/register' && fromLocation.pathname !== '/register' ? { from: fromLocation } : undefined}
              className="text-[#ae2714] font-semibold hover:underline ml-1"
            >
              Sign In Instead
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
