import React from 'react';
import { Link, useLocation } from 'react-router-dom';

export const Footer = () => {
  const location = useLocation();

  const handleLabClick = () => {
    if (location.pathname === '/lab') {
      const titleEl = document.getElementById('concurrency-lab-title');
      if (titleEl) {
        titleEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
      } else {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    }
  };

  return (
    <footer className="w-full bg-[#f5f4ee] border-t border-[#e4e2dd] mt-12 text-[#5a413c]">
      <div className="max-w-[1280px] mx-auto px-4 sm:px-8 py-10">
        <div className="grid grid-cols-1 md:grid-cols-12 gap-8 mb-8">
          <div className="md:col-span-5 flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <span className="font-headline text-lg text-[#1b1c19] tracking-tight font-medium">
                SeatFlow
              </span>
              <span className="font-mono text-[10px] text-[#5a413c] uppercase px-1.5 py-0.5 bg-[#e9e8e3] rounded-sm font-semibold">
                v2.4-RC
              </span>
            </div>
            <p className="text-xs text-[#5a413c] max-w-sm leading-relaxed">
              Deterministic ticket reservation system engineered for high-concurrency event drops and guaranteed seat allocation.
            </p>
          </div>

          <div className="md:col-span-7 flex flex-wrap gap-x-12 gap-y-6 items-start justify-start md:justify-end text-xs">
            <div className="flex flex-col gap-1.5">
              <span className="font-mono text-[10px] text-[#1b1c19] uppercase tracking-wider font-semibold">
                Platform
              </span>
              <Link to="/events" className="text-[#5a413c] hover:text-[#1b1c19] transition-colors">
                Events
              </Link>
              <a href="/#how-it-works" className="text-[#5a413c] hover:text-[#1b1c19] transition-colors">
                How it works
              </a>
              <Link
                to="/lab"
                onClick={handleLabClick}
                className="text-[#5a413c] hover:text-[#1b1c19] transition-colors"
              >
                Concurrency Lab
              </Link>
            </div>

            <div className="flex flex-col gap-1.5">
              <span className="font-mono text-[10px] text-[#1b1c19] uppercase tracking-wider font-semibold">
                Operations
              </span>
              <span className="text-[#8e706b]">Documentation</span>
              <span className="text-[#8e706b]">System Status</span>
              <span className="text-[#8e706b]">Privacy &amp; Terms</span>
            </div>
          </div>
        </div>

        <div className="pt-6 border-t border-[#e4e2dd] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 font-mono text-[10px] text-[#5a413c]">
          <div className="flex items-center gap-3">
            <span>CLUSTER: us-east-ticketing-alpha</span>
            <span>•</span>
            <span>STATUS: 99.998% ALLOCATION INTEGRITY [Illustrative]</span>
          </div>
          <div className="flex items-center gap-3">
            <span>© SEATFLOW ARCHITECTURE</span>
          </div>
        </div>
      </div>
    </footer>
  );
};
