import React from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api-client';
import {
  MapPin,
  Clock,
  ArrowRight,
  ArrowLeft,
  Calendar,
  AlertCircle,
  Ticket,
  RefreshCw,
  Building2,
  ShieldCheck
} from 'lucide-react';

const CONCERT_HERO_IMAGE = 'https://lh3.googleusercontent.com/aida-public/AB6AXuBYG2_r3hQq2yotbIrm3Mi0KLDNKlheNaMy-CQNkxbYbHtOWiYlGrltyIZOxaaXoZbecQYqKD1VXpwUgC6xTYFuOu3sw-YTPOJRisRHh3bLb3nL2G5ghheJLWIJFFjdimSB4NjfPBPVaoJAcvGlnaDOdAkSqB0_hkUNIjEZk17RS-nwyBe8SN2jmXrZ33gmmx-q7Iz4MyG6mhuLCzxO2uFhK2LK8NGrzikePTM34itmSCxtrMl6YLNe5g';

export const EventDetailsPage = () => {
  const { eventId } = useParams();
  const id = Number(eventId);

  const {
    data: event,
    isLoading: isEventLoading,
    error: eventError,
    refetch: refetchEvent,
  } = useQuery({
    queryKey: ['event', id],
    queryFn: () => api.getEvent(id),
    enabled: !isNaN(id),
  });

  const {
    data: showtimes,
    isLoading: isShowtimesLoading,
    error: showtimesError,
    refetch: refetchShowtimes,
  } = useQuery({
    queryKey: ['showtimes', id],
    queryFn: () => api.getShowtimesForEvent(id),
    enabled: !isNaN(id),
  });

  const formatShowtimeDate = (dateString) => {
    if (!dateString) return 'Date TBD';
    const d = new Date(dateString);
    return d.toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  };

  const formatShowtimeTime = (dateString) => {
    if (!dateString) return 'Time TBD';
    const d = new Date(dateString);
    return d.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      timeZoneName: 'short',
    });
  };

  if (isNaN(id)) {
    return (
      <div className="max-w-md mx-auto my-16 bg-[#efeee8] border border-[#e4e2dd] p-8 rounded-sm text-center space-y-4">
        <AlertCircle className="w-10 h-10 text-[#ba1a1a] mx-auto" />
        <h2 className="text-xl font-headline font-normal text-[#1b1c19]">Invalid Event Identifier</h2>
        <p className="text-xs text-[#5a413c]">
          The performance identifier specified in the URL is not valid.
        </p>
        <Link
          to="/events"
          className="inline-flex items-center gap-2 px-4 py-2 bg-[#d1402a] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Back to Events</span>
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-8 max-w-[1280px] mx-auto">
      {/* Breadcrumb Navigation */}
      <div className="flex items-center justify-between border-b border-[#e4e2dd] pb-4">
        <Link
          to="/events"
          className="inline-flex items-center gap-2 text-xs font-mono uppercase text-[#5a413c] hover:text-[#1b1c19] transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Return to Repertoire</span>
        </Link>
        <span className="font-mono text-[10px] text-[#5a413c] uppercase">
          EVENT RECORD #{id}
        </span>
      </div>

      {/* Loading Skeleton */}
      {isEventLoading && (
        <div className="bg-[#efeee8] border border-[#e4e2dd] p-8 rounded-sm space-y-4 animate-pulse">
          <div className="h-4 w-32 bg-[#e4e2dd] rounded" />
          <div className="h-8 w-2/3 bg-[#e4e2dd] rounded" />
          <div className="h-4 w-full bg-[#e4e2dd]/70 rounded" />
        </div>
      )}

      {/* Error State */}
      {eventError && (
        <div className="bg-[#ffdad6]/40 border border-[#ba1a1a]/30 p-6 rounded-sm text-[#ba1a1a] flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <AlertCircle className="w-5 h-5 flex-shrink-0 text-[#ba1a1a]" />
            <div>
              <h2 className="font-semibold text-sm">Failed to retrieve performance record</h2>
              <p className="text-xs text-[#5a413c] mt-0.5">
                {eventError.message || 'The requested performance could not be loaded.'}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => refetchEvent()}
            className="px-4 py-2 bg-[#d1402a] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold transition-colors"
          >
            Retry Request
          </button>
        </div>
      )}

      {/* Main Event Showcase Card */}
      {!isEventLoading && !eventError && event && (
        <div className="bg-[#efeee8] border border-[#e4e2dd] rounded-sm overflow-hidden shadow-sm">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-0">
            {/* Left Content Column */}
            <div className="lg:col-span-7 p-6 sm:p-10 flex flex-col justify-between">
              <div className="space-y-4">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-mono text-[10px] uppercase text-[#5a413c] tracking-widest px-2 py-0.5 bg-[#fbf9f4] border border-[#e4e2dd] rounded-sm">
                    CATALOG SPECIMEN #{event.id}
                  </span>
                  <span className="font-mono text-[10px] uppercase text-[#576244] tracking-widest px-2 py-0.5 bg-[#dbe7c1] rounded-sm font-semibold">
                    ALLOCATION PIPELINE ACTIVE
                  </span>
                </div>

                <h1 className="font-headline text-3xl sm:text-5xl text-[#1b1c19] tracking-tight font-normal leading-tight">
                  {event.name}
                </h1>

                <p className="text-sm sm:text-base text-[#5a413c] leading-relaxed">
                  {event.description || 'Full performance details and seating availability listed below.'}
                </p>

                <div className="pt-2 font-mono text-xs text-[#5a413c] flex items-center gap-4">
                  <span>RECORD CREATED: {new Date(event.createdAt).toLocaleDateString()}</span>
                  <span>•</span>
                  <span>GUARANTEE: ZERO OVERSELL</span>
                </div>
              </div>

              <div className="pt-8 border-t border-[#e4e2dd] mt-8 flex items-center gap-3">
                <span className="font-mono text-xs text-[#5a413c]">
                  SELECT AN ACTIVE SHOWTIME BELOW TO ACCESS THE REAL-TIME SEATING MATRIX.
                </span>
              </div>
            </div>

            {/* Right Image Column */}
            <div className="lg:col-span-5 relative bg-[#e4e2dd] min-h-[260px] lg:min-h-full border-t lg:border-t-0 lg:border-l border-[#e4e2dd] overflow-hidden">
              <img
                src={event.imageUrl || event.image || CONCERT_HERO_IMAGE}
                alt={event.name || "Concert hall acoustic architecture visual"}
                className="w-full h-full object-cover grayscale contrast-125"
              />
              <div className="absolute inset-0 bg-[#1b1c19]/15 mix-blend-multiply"></div>
              <div className="absolute bottom-3 left-3 bg-[#fbf9f4]/95 border border-[#e4e2dd] px-2.5 py-1 rounded-sm font-mono text-[10px] text-[#1b1c19] uppercase font-semibold">
                ARCHITECTURAL STAGING // ACOUSTIC CANOPY
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Showtimes Section */}
      <div className="space-y-6 pt-4">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 border-b border-[#e4e2dd] pb-4">
          <div className="space-y-1">
            <span className="font-mono text-xs uppercase text-[#5a413c] tracking-wider">
              ALLOCATION SESSIONS
            </span>
            <h2 className="font-headline text-2xl sm:text-3xl text-[#1b1c19] font-normal">
              Available Showtimes
            </h2>
          </div>

          <button
            type="button"
            onClick={() => refetchShowtimes()}
            className="text-xs font-mono uppercase text-[#5a413c] hover:text-[#1b1c19] transition-colors flex items-center gap-1.5"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Refresh Showtimes</span>
          </button>
        </div>

        {/* Loading Showtimes */}
        {isShowtimesLoading && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {[1, 2].map((i) => (
              <div
                key={i}
                className="bg-[#efeee8] border border-[#e4e2dd] p-6 rounded-sm animate-pulse space-y-3"
              >
                <div className="h-4 w-32 bg-[#e4e2dd] rounded" />
                <div className="h-6 w-2/3 bg-[#e4e2dd] rounded" />
                <div className="h-8 w-28 bg-[#e4e2dd] rounded mt-4" />
              </div>
            ))}
          </div>
        )}

        {/* Error Showtimes */}
        {showtimesError && (
          <div className="bg-[#ffdad6]/40 border border-[#ba1a1a]/30 p-4 rounded-sm text-[#ba1a1a] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span className="text-xs font-mono">Unable to retrieve showtime availability.</span>
            </div>
            <button
              type="button"
              onClick={() => refetchShowtimes()}
              className="text-xs font-mono underline uppercase text-[#ba1a1a]"
            >
              Retry
            </button>
          </div>
        )}

        {/* Empty Showtimes */}
        {!isShowtimesLoading && !showtimesError && Array.isArray(showtimes) && showtimes.length === 0 && (
          <div className="text-center py-12 bg-[#efeee8] border border-[#e4e2dd] rounded-sm text-[#5a413c] space-y-2">
            <Clock className="w-8 h-8 mx-auto text-[#8e706b]" />
            <h3 className="font-headline text-lg text-[#1b1c19]">No Showtimes Scheduled</h3>
            <p className="text-xs max-w-sm mx-auto">
              There are currently no showtimes registered for this performance in the database.
            </p>
          </div>
        )}

        {/* Showtimes Grid */}
        {!isShowtimesLoading && !showtimesError && Array.isArray(showtimes) && showtimes.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {showtimes.map((st) => {
              const dateStr = formatShowtimeDate(st.startsAt);
              const timeStr = formatShowtimeTime(st.startsAt);
              const venueTitle = st.venueName || 'Main Performance Hall';
              const venueLocation = st.venueCity ? `${st.venueCity}` : 'Metropolitan Venue';

              return (
                <div
                  key={st.id}
                  className="bg-[#efeee8] border border-[#e4e2dd] p-6 rounded-sm flex flex-col justify-between hover:bg-[#e9e8e3] transition-colors"
                >
                  <div className="space-y-4">
                    <div className="flex items-center justify-between font-mono text-xs text-[#5a413c]">
                      <span>SESSION #{st.id}</span>
                      <span className="text-[#576244] font-semibold">● SEATS ALLOCATABLE</span>
                    </div>

                    <div>
                      <h3 className="font-headline text-2xl text-[#1b1c19] font-normal">
                        {dateStr}
                      </h3>
                      <div className="font-mono text-sm text-[#ae2714] font-semibold mt-0.5">
                        {timeStr}
                      </div>
                    </div>

                    <div className="pt-2 border-t border-[#e4e2dd] flex items-center justify-between text-xs text-[#5a413c]">
                      <div className="flex items-center gap-1.5">
                        <Building2 className="w-3.5 h-3.5 text-[#5a413c]" />
                        <span className="font-medium text-[#1b1c19]">{venueTitle}</span>
                        <span>({venueLocation})</span>
                      </div>
                    </div>
                  </div>

                  <div className="pt-6 mt-4 border-t border-[#e4e2dd] flex items-center justify-between">
                    <span className="font-mono text-[10px] uppercase text-[#5a413c]">
                      REDIS LOCK LEASE: 10 MIN
                    </span>
                    <Link
                      to={`/showtimes/${st.id}/seats`}
                      className="inline-flex items-center gap-2 bg-[#d1402a] text-[#fffafa] hover:bg-[#30312d] hover:text-[#f2f1eb] px-4 py-2 rounded-sm font-mono text-xs uppercase font-semibold transition-all shadow-[2px_2px_0px_0px_#1b1c19]"
                    >
                      <span>Select Seats</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </Link>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
