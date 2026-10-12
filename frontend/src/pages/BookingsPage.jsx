import React from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api-client';
import {
  BookmarkCheck,
  Calendar,
  ArrowRight,
  AlertCircle,
  RefreshCw,
  Ticket,
  Clock,
  ShieldCheck,
  Building2
} from 'lucide-react';

export const BookingsPage = () => {
  const { data: bookings, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['my-bookings'],
    queryFn: () => api.getMyBookings(),
  });

  return (
    <div className="space-y-8 max-w-[1280px] mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 border-b border-[#e4e2dd] pb-6">
        <div className="space-y-1">
          <div className="inline-flex items-center gap-2">
            <span className="font-mono text-[10px] uppercase text-[#5a413c] tracking-widest px-2 py-0.5 bg-[#efeee8] border border-[#e4e2dd] rounded-sm">
              PATRON ARCHIVE // SETTLED RESERVATIONS
            </span>
          </div>
          <h1 className="font-headline text-3xl sm:text-4xl text-[#1b1c19] font-normal tracking-tight">
            My Reservation History
          </h1>
          <p className="text-sm text-[#5a413c] max-w-xl leading-relaxed">
            Review confirmed ticket ledger entries, access barcode dispatch passes, or process payment simulation settlement.
          </p>
        </div>

        <button
          type="button"
          onClick={() => refetch()}
          disabled={isFetching}
          className="px-3.5 py-2 bg-[#efeee8] hover:bg-[#e4e2dd] text-[#1b1c19] border border-[#d1c9b8] rounded-sm font-mono text-xs uppercase font-semibold transition-colors flex items-center gap-2 self-start sm:self-auto"
        >
          <RefreshCw className={`w-3.5 h-3.5 text-[#5a413c] ${isFetching ? 'animate-spin' : ''}`} />
          <span>Refresh History</span>
        </button>
      </div>

      {/* Loading Skeletons */}
      {isLoading && (
        <div className="space-y-4">
          {[1, 2, 3].map((i) => (
            <div
              key={i}
              className="bg-[#efeee8] border border-[#e4e2dd] p-6 rounded-sm animate-pulse space-y-3"
            >
              <div className="h-4 w-32 bg-[#e4e2dd] rounded" />
              <div className="h-6 w-1/2 bg-[#e4e2dd] rounded" />
              <div className="h-4 w-1/4 bg-[#e4e2dd] rounded" />
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
              <h2 className="font-semibold text-sm">Failed to retrieve booking ledger</h2>
              <p className="text-xs text-[#5a413c] mt-0.5">
                {error.message || 'Unable to fetch your booking history from the server.'}
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
      {!isLoading && !error && bookings && bookings.length === 0 && (
        <div className="text-center py-16 bg-[#efeee8] border border-[#e4e2dd] rounded-sm text-[#5a413c] space-y-3">
          <Ticket className="w-10 h-10 mx-auto text-[#8e706b]" />
          <h2 className="text-xl font-headline font-normal text-[#1b1c19]">No Bookings on Record</h2>
          <p className="text-xs max-w-sm mx-auto leading-relaxed">
            You do not currently have any confirmed or past ticket reservations tied to this patron account.
          </p>
          <div className="pt-2">
            <Link
              to="/events"
              className="inline-flex items-center gap-2 px-4 py-2 bg-[#d1402a] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold transition-all shadow-[2px_2px_0px_0px_#1b1c19]"
            >
              <span>Browse Performances</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </div>
      )}

      {/* Bookings List */}
      {!isLoading && !error && bookings && bookings.length > 0 && (
        <div className="space-y-4">
          {bookings.map((booking) => {
            const isConfirmed = booking.status === 'CONFIRMED';
            const isCancelled = booking.status === 'CANCELLED';

            return (
              <div
                key={booking.id}
                className="bg-[#ffffff] border border-[#1b1c19] rounded-sm p-6 shadow-[3px_3px_0px_0px_#1b1c19] hover:border-[#ae2714] transition-all flex flex-col md:flex-row md:items-center justify-between gap-6"
              >
                <div className="space-y-2">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-mono text-xs font-bold text-[#1b1c19]">
                      BOOKING #{booking.id}
                    </span>
                    <span
                      className={`font-mono text-[10px] uppercase px-2 py-0.5 rounded-sm font-semibold ${
                        isConfirmed
                          ? 'bg-[#dbe7c1] text-[#404a2e]'
                          : isCancelled
                          ? 'bg-[#ffdad6] text-[#93000a]'
                          : 'bg-[#ffdea4] text-[#775600]'
                      }`}
                    >
                      {booking.status}
                    </span>
                    <span className="font-mono text-[10px] text-[#5a413c]">
                      SETTLED: {new Date(booking.createdAt).toLocaleString()}
                    </span>
                  </div>

                  <h3 className="font-headline text-2xl text-[#1b1c19] font-normal">
                    {booking.eventName || booking.showtime?.event?.name || `Showtime #${booking.showtimeId || booking.showtime?.id || 'Session'}`}
                  </h3>

                  {(booking.showtimeStartsAt || booking.venueName) && (
                    <div className="flex items-center gap-3 text-xs font-mono text-[#5a413c] flex-wrap">
                      {booking.showtimeStartsAt && (
                        <span className="flex items-center gap-1.5">
                          <Calendar className="w-3.5 h-3.5 text-[#8e706b]" />
                          <span>
                            {new Date(booking.showtimeStartsAt).toLocaleString(undefined, {
                              weekday: 'short',
                              month: 'short',
                              day: 'numeric',
                              year: 'numeric',
                              hour: '2-digit',
                              minute: '2-digit',
                            })}
                          </span>
                        </span>
                      )}
                      {booking.venueName && (
                        <span className="flex items-center gap-1.5">
                          <Building2 className="w-3.5 h-3.5 text-[#8e706b]" />
                          <span>
                            {booking.venueName}{booking.venueCity ? ` (${booking.venueCity})` : ''}
                          </span>
                        </span>
                      )}
                    </div>
                  )}

                  <div className="font-mono text-xs text-[#5a413c] flex items-center gap-3 flex-wrap pt-1">
                    <span className="text-[#1b1c19] font-medium">
                      SEATS ({(booking.seats?.length || booking.seatIds?.length || 1)}):
                    </span>
                    {booking.seats && booking.seats.length > 0 ? (
                      <div className="flex flex-wrap gap-1.5">
                        {booking.seats.map((seat) => {
                          const label = (seat.rowLabel && seat.seatNumber)
                            ? `${seat.rowLabel}${seat.seatNumber}`
                            : (seat.seatLabel || (seat.seatId ? `Seat #${seat.seatId}` : 'Seat'));
                          return (
                            <span
                              key={seat.seatId || label}
                              className="px-2 py-0.5 bg-[#efeee8] border border-[#e4e2dd] rounded-sm text-[11px] font-bold text-[#1b1c19]"
                            >
                              {label}
                            </span>
                          );
                        })}
                      </div>
                    ) : (
                      <span>
                        {booking.seatIds?.length || 1} ALLOCATED
                      </span>
                    )}
                    <span>•</span>
                    <span className="text-[#1b1c19] font-semibold">
                      TOTAL: ${Number(booking.totalAmount || 0).toFixed(2)}
                    </span>
                  </div>
                </div>

                <div className="shrink-0 flex items-center gap-3">
                  <Link
                    to={`/bookings/${booking.id}`}
                    state={{ booking }}
                    className="inline-flex items-center gap-2 px-4 py-2.5 bg-[#30312d] text-[#f2f1eb] hover:bg-[#d1402a] hover:text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold transition-all shadow-sm"
                  >
                    <span>View Dossier &amp; Settle</span>
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
