import React from 'react';
import { Outlet, useLocation, ScrollRestoration } from 'react-router-dom';
import { Navbar } from './Navbar';
import { Footer } from './Footer';

export const Layout = () => {
  const location = useLocation();
  const isLanding = location.pathname === '/';

  return (
    <div className="flex flex-col min-h-screen bg-[#fbf9f4] text-[#1b1c19] font-sans antialiased selection:bg-[#d1402a] selection:text-[#fffafa]">
      <ScrollRestoration />
      <Navbar />
      <main className={`flex-1 w-full ${isLanding ? '' : 'max-w-[1280px] mx-auto px-4 sm:px-8 py-8'}`}>
        <Outlet />
      </main>
      <Footer />
    </div>
  );
};
