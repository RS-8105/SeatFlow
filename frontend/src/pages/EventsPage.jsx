import React, { useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api-client';
import {
  Calendar,
  ArrowRight,
  AlertCircle,
  RefreshCw,
  Search,
  Sparkles,
  Ticket
} from 'lucide-react';

const CURATED_EVENT_IMAGES = [
  'https://lh3.googleusercontent.com/aida-public/AB6AXuDi4cfzSic1zZtgxylquH0O3UjFCu3-jjMNp_UiH54JQ2r0OgGKYaSyn7Tz95Ohzp9mRTk25hRlqssNfHso-fpQUQmdti9TrOrWU_OaGfcRejnJsfQt7uvncv5QamBraLxWcJJ30D-LEQpD7QS88PjOqIG6XY4yRyrUnKbp_bnSfzOp19YA1z601EZ9IXCNLtEUMB_rXYiSsWwnU1J_tKhd4JhRbdPKbcNhqQj7LC5nXwPlIKWUvbpvOQ',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuCdH5fa1PjBPgRfRsLZiG3lcwGY9jlVjbvWx9bdoj4a6J03-HNUnVTUSFTJz68KsEpAPcsmlIBKuPA12gUG3gcmQHWHufjPUlj7p6kDr57sjj0yNvklBeu5593pR1gtSBzVfa1klaqocj2fSrgs0mqI1BtB7au_-YASJonqZX9VHqKmcml1SyHwPAw29UULq1iSahg4AVNAy5vnT0b--bDjuuCCRXWbJ97vWQBfzXunaXL_a1lHaz8ZgQ',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuCiEaefcEBBhPFmaZw6yrUXf2HwABvcxXV8jlUjTy4Ro95oq0Nu1O6u-BptnhVZ4DPMmGu3b9Y8u-B6LGiJSvVhU7hpfUn1RLuhzkniGoi1FKLv2osAWuc02Wv4qZm77EEPmc4oMyDwYu4sB3OLZxOxW6FsfrTe1gGq130SH73biC3A9b4n0WSeDvASYp2JJMB574KcFK7Sjc3L2cUOSLtC99dENzuVCLMNYp61izKG0tCTsOEJQeYrfA',
];

const getEventImage = (event) => {
  if (event?.imageUrl) return event.imageUrl;
  if (event?.image) return event.image;
  const numId = Number(event?.id);
  const imageIndex = !isNaN(numId) && numId > 0
    ? (numId - 1) % CURATED_EVENT_IMAGES.length
    : 0;
  return CURATED_EVENT_IMAGES[imageIndex];
};

export const EventsPage = () => {
  const [searchTerm, setSearchTerm] = useState('');

  const {
    data: events,
    isLoading,
    error,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['events'],
    queryFn: api.getEvents,
  });

  const filteredEvents = useMemo(() => {
    if (!events) return [];
    if (!searchTerm.trim()) return events;
    const term = searchTerm.toLowerCase();
    return events.filter(
      (e) =>
        e.name?.toLowerCase().includes(term) ||
        e.description?.toLowerCase().includes(term)
    );
  }, [events, searchTerm]);

  return (
    <div className="space-y-8 max-w-[1280px] mx-auto">
      {/* Header Strip */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 border-b border-[#e4e2dd] pb-6">
        <div className="space-y-2">
          <div className="inline-flex items-center gap-2">
            <span className="font-mono text-[10px] uppercase text-[#5a413c] tracking-widest px-2 py-0.5 bg-[#efeee8] border border-[#e4e2dd] rounded-sm">
              CATALOG SPECIMEN // ACTIVE REPERTOIRE
            </span>
          </div>
          <h1 className="font-headline text-3xl sm:text-4xl text-[#1b1c19] tracking-tight font-normal">
            Scheduled Performances
          </h1>
          <p className="text-sm sm:text-base text-[#5a413c] max-w-2xl leading-relaxed">
            Browse current productions registered on the SeatFlow ticketing engine. Select any performance to inspect available venue showtimes and real-time seat matrices.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => refetch()}
            disabled={isFetching}
            className="px-3.5 py-2 bg-[#efeee8] hover:bg-[#e4e2dd] text-[#1b1c19] border border-[#d1c9b8] rounded-sm font-mono text-xs uppercase font-semibold transition-colors flex items-center gap-2"
          >
            <RefreshCw className={`w-3.5 h-3.5 text-[#5a413c] ${isFetching ? 'animate-spin' : ''}`} />
            <span>Refresh Repertoire</span>
          </button>
        </div>
      </div>

      {/* Filter & Search Bar */}
      <div className="bg-[#efeee8] border border-[#e4e2dd] p-3 rounded-sm flex flex-col sm:flex-row items-center justify-between gap-3">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[#5a413c]" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search by title or descriptor..."
            className="w-full bg-[#ffffff] text-[#1b1c19] border border-[#d1c9b8] pl-9 pr-3 py-1.5 rounded-sm text-sm focus:outline-none focus:border-[#1b1c19]"
          />
        </div>

        <div className="font-mono text-xs text-[#5a413c] self-end sm:self-center">
          <span>SHOWING {filteredEvents.length} OF {events?.length || 0} PERFORMANCES</span>
        </div>
      </div>

      {/* Loading Skeletons */}
      {isLoading && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <div
              key={i}
              className="bg-[#efeee8] border border-[#e4e2dd] rounded-sm overflow-hidden flex flex-col justify-between animate-pulse"
            >
              <div className="h-52 bg-[#e4e2dd]" />
              <div className="p-6 space-y-3">
                <div className="h-3 w-28 bg-[#e4e2dd] rounded" />
                <div className="h-6 w-3/4 bg-[#e4e2dd] rounded" />
                <div className="h-4 w-full bg-[#e4e2dd]/80 rounded" />
              </div>
              <div className="p-6 pt-0 flex justify-between items-center">
                <div className="h-5 w-20 bg-[#e4e2dd] rounded" />
                <div className="h-8 w-28 bg-[#e4e2dd] rounded" />
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Error State */}
      {error && (
        <div className="bg-[#ffdad6]/40 border border-[#ba1a1a]/30 p-6 rounded-sm text-[#ba1a1a] flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <AlertCircle className="w-5 h-5 flex-shrink-0 text-[#ba1a1a]" />
            <div>
              <h2 className="font-semibold text-sm">Failed to retrieve event directory</h2>
              <p className="text-xs text-[#5a413c] mt-0.5">
                {error.message || 'Ensure the backend server is running on port 8080.'}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => refetch()}
            className="px-4 py-2 bg-[#d1402a] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold transition-colors"
          >
            Retry Request
          </button>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && !error && filteredEvents.length === 0 && (
        <div className="text-center py-16 bg-[#efeee8] border border-[#e4e2dd] rounded-sm text-[#5a413c] space-y-3">
          <Calendar className="w-10 h-10 mx-auto text-[#8e706b]" />
          <h2 className="text-lg font-headline font-normal text-[#1b1c19]">
            {searchTerm ? 'No Matching Performances' : 'No Events Registered'}
          </h2>
          <p className="text-xs max-w-sm mx-auto">
            {searchTerm
              ? `No catalog entities match "${searchTerm}". Clear your search query to see all performances.`
              : 'There are currently no events registered in the database.'}
          </p>
          {searchTerm && (
            <button
              type="button"
              onClick={() => setSearchTerm('')}
              className="mt-2 text-xs font-mono uppercase text-[#ae2714] underline"
            >
              Clear Search Query
            </button>
          )}
        </div>
      )}

      {/* Events Grid */}
      {!isLoading && !error && filteredEvents.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {filteredEvents.map((event) => {
            const imageSrc = getEventImage(event);
            const createdDate = event.createdAt
              ? new Date(event.createdAt).toLocaleDateString('en-US', {
                  month: 'short',
                  day: 'numeric',
                  year: 'numeric',
                })
              : 'CURRENT REPERTOIRE';

            return (
              <div
                key={event.id}
                className="bg-[#efeee8] border border-[#e4e2dd] rounded-sm overflow-hidden flex flex-col justify-between group hover:bg-[#e9e8e3] transition-colors"
              >
                <div>
                  {/* Event Image */}
                  <div className="relative w-full h-52 bg-[#e4e2dd] overflow-hidden border-b border-[#e4e2dd]">
                    <img
                      src={imageSrc}
                      alt={event.name}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                    />
                    <div className="absolute top-2.5 right-2.5 bg-[#fbf9f4]/95 border border-[#e4e2dd] px-2 py-0.5 rounded-sm font-mono text-[10px] text-[#1b1c19] font-semibold uppercase">
                      Event #{event.id}
                    </div>
                    <div className="absolute bottom-2.5 left-2.5 bg-[#30312d]/90 text-[#f2f1eb] px-2 py-0.5 rounded-sm font-mono text-[10px] uppercase">
                      CATALOG SPECIMEN
                    </div>
                  </div>

                  {/* Body Content */}
                  <div className="p-6 flex flex-col gap-2">
                    <span className="font-mono text-xs text-[#5a413c]">
                      ADDED: {createdDate.toUpperCase()}
                    </span>
                    <h2 className="font-headline text-2xl text-[#1b1c19] font-normal group-hover:text-[#ae2714] transition-colors">
                      {event.name}
                    </h2>
                    <p className="text-xs text-[#5a413c] leading-relaxed line-clamp-3">
                      {event.description || 'Full performance description, casting details, and seating allocation available.'}
                    </p>
                  </div>
                </div>

                {/* Footer Link */}
                <div className="px-6 pb-6 pt-2 flex items-center justify-between mt-auto border-t border-[#e4e2dd]/60">
                  <div className="flex flex-col">
                    <span className="font-mono text-[10px] text-[#5a413c] uppercase">STATUS</span>
                    <span className="font-mono text-xs font-semibold text-[#576244]">SHOWTIMES ACTIVE</span>
                  </div>
                  <Link
                    to={`/events/${event.id}`}
                    className="bg-[#30312d] text-[#f2f1eb] hover:bg-[#d1402a] hover:text-[#fffafa] px-4 py-2 rounded-sm font-mono text-xs uppercase tracking-wider transition-colors shadow-sm flex items-center gap-1.5"
                  >
                    <span>Inspect Showtimes</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
