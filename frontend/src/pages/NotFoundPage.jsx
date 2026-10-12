import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Ticket, AlertCircle } from 'lucide-react';

export const NotFoundPage = () => {
  return (
    <div className="max-w-md mx-auto my-16 text-center space-y-4">
      <div className="bg-[#ffffff] border border-[#1b1c19] rounded-sm p-8 shadow-[4px_4px_0px_0px_#1b1c19] space-y-4">
        <div className="inline-flex items-center gap-1.5 px-2 py-0.5 bg-[#efeee8] border border-[#e4e2dd] rounded-sm font-mono text-[10px] uppercase text-[#5a413c]">
          <span>ERROR 404</span>
          <span>•</span>
          <span>ROUTE UNREACHABLE</span>
        </div>

        <h1 className="text-4xl font-headline font-normal text-[#1b1c19]">
          Page Not Located
        </h1>

        <p className="text-xs text-[#5a413c] leading-relaxed max-w-sm mx-auto">
          The requested catalogue entity, seat map, or administrative route does not exist or has been relocated within the SeatFlow ledger.
        </p>

        <div className="pt-4 flex flex-col sm:flex-row items-center justify-center gap-2">
          <Link
            to="/events"
            className="w-full sm:w-auto px-4 py-2 bg-[#d1402a] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold transition-all shadow-[2px_2px_0px_0px_#1b1c19] flex items-center justify-center gap-2"
          >
            <Ticket className="w-3.5 h-3.5" />
            <span>Browse Events</span>
          </Link>
          <Link
            to="/"
            className="w-full sm:w-auto px-4 py-2 bg-[#efeee8] hover:bg-[#e4e2dd] border border-[#d1c9b8] text-[#1b1c19] rounded-sm font-mono text-xs uppercase font-semibold transition-colors flex items-center justify-center gap-2"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Return Home</span>
          </Link>
        </div>
      </div>
    </div>
  );
};
