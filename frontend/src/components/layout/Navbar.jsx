import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Ticket, User, LogOut, LogIn, Calendar, BookmarkCheck, Zap } from 'lucide-react';
import { useAuth } from '../../lib/auth';

export const Navbar = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();

  const handleLogout = () => {
    logout();
    navigate('/', { replace: true });
  };

  const navLinks = [
    { to: '/events', label: 'Events' },
    { to: '/bookings', label: 'My Bookings', authOnly: true },
    { to: '/lab', label: 'Concurrency Lab' },
  ];

  return (
    <header className="sticky top-0 z-50 bg-[#fbf9f4]/95 backdrop-blur-sm border-b border-[#e4e2dd]">
      <div className="max-w-[1280px] mx-auto px-4 sm:px-8 h-16 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <Link to="/" className="flex items-center gap-2 group">
            <div className="w-7 h-7 bg-[#1b1c19] flex items-center justify-center rounded-sm text-[#fbf9f4] group-hover:scale-105 transition-transform">
              <Ticket className="w-4 h-4" />
            </div>
            <div className="flex items-baseline gap-1">
              <span className="font-headline text-xl text-[#1b1c19] tracking-tight font-medium">
                SeatFlow
              </span>
              <span className="font-mono text-[10px] text-[#5a413c] uppercase ml-1 px-1.5 py-0.5 bg-[#e9e8e3] rounded-sm font-semibold tracking-wider">
                RESERVATION ENGINE
              </span>
            </div>
          </Link>

          <nav className="hidden md:flex items-center gap-1 ml-4">
            {navLinks.map((link) => {
              if (link.authOnly && !isAuthenticated) return null;
              const isActive = location.pathname.startsWith(link.to);
              return (
                <Link
                  key={link.to}
                  to={link.to}
                  className={`px-3 py-1 text-sm rounded-sm transition-colors ${
                    isActive
                      ? 'bg-[#efeee8] text-[#1b1c19] font-medium'
                      : 'text-[#5a413c] hover:text-[#1b1c19] hover:bg-[#f5f4ee]'
                  }`}
                >
                  {link.label}
                </Link>
              );
            })}
            <a
              href="/#how-it-works"
              className="px-3 py-1 text-sm text-[#5a413c] hover:text-[#1b1c19] hover:bg-[#f5f4ee] rounded-sm transition-colors"
            >
              How it works
            </a>
          </nav>
        </div>

        <div className="flex items-center gap-3 sm:gap-4">
          {isAuthenticated ? (
            <div className="flex items-center gap-2">
              <div className="flex items-center gap-2 px-2.5 py-1 bg-[#efeee8] border border-[#e4e2dd] rounded-sm text-xs text-[#1b1c19]">
                <User className="w-3.5 h-3.5 text-[#576244]" />
                <span className="font-medium max-w-[120px] truncate">{user?.username}</span>
              </div>
              <button
                type="button"
                onClick={handleLogout}
                className="px-2 py-1 text-xs text-[#5a413c] hover:text-[#ae2714] transition-colors font-mono uppercase"
              >
                Sign Out
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link
                to="/login"
                state={location.pathname !== '/login' && location.pathname !== '/register' ? { from: location } : undefined}
                className="text-sm font-medium text-[#5a413c] hover:text-[#1b1c19] transition-colors px-2 py-1"
              >
                Sign in
              </Link>
              <Link
                to="/register"
                state={location.pathname !== '/login' && location.pathname !== '/register' ? { from: location } : undefined}
                className="hidden sm:inline-block text-sm font-medium text-[#5a413c] hover:text-[#1b1c19] transition-colors px-2 py-1"
              >
                Register
              </Link>
            </div>
          )}

          <Link
            to="/events"
            className="bg-[#d1402a] text-[#fffafa] hover:bg-[#30312d] hover:text-[#f2f1eb] px-3.5 py-1.5 rounded-sm font-mono text-xs uppercase font-semibold transition-all shadow-[2px_2px_0px_0px_#1b1c19] whitespace-nowrap"
          >
            Browse Events
          </Link>
        </div>
      </div>
    </header>
  );
};
