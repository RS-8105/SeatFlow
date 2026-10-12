import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api-client';
import {
  ArrowLeft,
  ArrowRight,
  RefreshCw,
  AlertCircle,
  Radio,
  Check,
  Lock,
  Hourglass,
  Info,
  X,
  ShieldCheck,
  CheckCircle2
} from 'lucide-react';

export const SeatSelectionPage = () => {
  const { showtimeId } = useParams();
  const id = Number(showtimeId);
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();

  const [selectedSeatIds, setSelectedSeatIds] = useState([]);
  const [isLiveConnected, setIsLiveConnected] = useState(false);
  const [conflictNotice, setConflictNotice] = useState(null);
  const [maxLimitNotice, setMaxLimitNotice] = useState(null);
  const [showReleasedModal, setShowReleasedModal] = useState(Boolean(location.state?.holdReleased));
  const [releasedBanner, setReleasedBanner] = useState(
    location.state?.holdReleased ? 'The seat hold has been released and returned to the public pool.' : null
  );
  const releasedSeatCount = location.state?.releasedSeatCount || 1;

  useEffect(() => {
    if (location.state?.holdReleased) {
      setShowReleasedModal(true);
      setReleasedBanner('The seat hold has been released and returned to the public pool.');
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  // 1. Fetch real seat inventory for this showtime
  const {
    data: seats,
    isLoading,
    error,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['seats', id],
    queryFn: () => api.getSeatsForShowtime(id),
    enabled: !isNaN(id),
  });

  // 2. Connect to Server-Sent Events (SSE) for live seat updates
  useEffect(() => {
    if (isNaN(id)) return;

    const streamUrl = api.getSeatStreamUrl(id);
    const eventSource = new EventSource(streamUrl);

    eventSource.addEventListener('connected', () => {
      setIsLiveConnected(true);
    });

    eventSource.addEventListener('seat-update', (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (!payload || !payload.seatId) return;

        // Invalidate and patch query cache
        queryClient.setQueryData(['seats', id], (oldSeats) => {
          if (!oldSeats) return oldSeats;
          return oldSeats.map((seat) =>
            seat.id === payload.seatId ? { ...seat, status: payload.status } : seat
          );
        });

        // If another user claimed a seat that this user currently selected, deselect and notify
        setSelectedSeatIds((prev) => {
          if (prev.includes(payload.seatId) && payload.status !== 'AVAILABLE') {
            setConflictNotice(`Seat #${payload.seatId} was just held or reserved by another patron.`);
            return prev.filter((sId) => sId !== payload.seatId);
          }
          return prev;
        });
      } catch (err) {
        console.error('Error parsing SSE seat-update payload:', err);
      }
    });

    eventSource.onerror = () => {
      setIsLiveConnected(false);
    };

    return () => {
      eventSource.close();
      setIsLiveConnected(false);
    };
  }, [id, queryClient]);

  // 3. Group real seats by row
  const rows = useMemo(() => {
    if (!seats) return {};
    const grouped = {};
    seats.forEach((seat) => {
      if (!grouped[seat.rowLabel]) {
        grouped[seat.rowLabel] = [];
      }
      grouped[seat.rowLabel].push(seat);
    });

    Object.keys(grouped).forEach((row) => {
      grouped[row].sort((a, b) => a.seatNumber - b.seatNumber);
    });

    return grouped;
  }, [seats]);

  // 4. Selected seat objects and price calculation
  const selectedSeats = useMemo(() => {
    if (!seats) return [];
    return seats.filter((s) => selectedSeatIds.includes(s.id));
  }, [seats, selectedSeatIds]);

  const totalPrice = useMemo(() => {
    return selectedSeats.reduce((sum, s) => sum + Number(s.price || 0), 0);
  }, [selectedSeats]);

  const toggleSeatSelection = (seat) => {
    if (seat.status !== 'AVAILABLE') return;

    setConflictNotice(null);
    setSelectedSeatIds((prev) => {
      if (prev.includes(seat.id)) {
        setMaxLimitNotice(null);
        return prev.filter((sId) => sId !== seat.id);
      }

      if (prev.length >= 6) {
        setMaxLimitNotice('Maximum 6 seats per order permitted by allocation quota.');
        return prev;
      }

      setMaxLimitNotice(null);
      return [...prev, seat.id];
    });
  };

  const handleClearSelection = () => {
    setSelectedSeatIds([]);
    setMaxLimitNotice(null);
    setConflictNotice(null);
  };

  const handleContinueToCheckout = () => {
    if (selectedSeatIds.length === 0) return;

    navigate('/checkout', {
      state: {
        showtimeId: id,
        seatIds: selectedSeatIds,
        seats: selectedSeats,
        totalAmount: totalPrice,
      },
    });
  };

  if (isNaN(id)) {
    return (
      <div className="max-w-md mx-auto my-16 bg-[#efeee8] border border-[#e4e2dd] p-8 rounded-sm text-center space-y-4">
        <AlertCircle className="w-10 h-10 text-[#ba1a1a] mx-auto" />
        <h2 className="text-xl font-headline font-normal text-[#1b1c19]">Invalid Showtime Identifier</h2>
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
    <div className="space-y-6 max-w-[1280px] mx-auto pb-12">
      {/* Top Showtime Summary & Dossier Header */}
      <div className="w-full bg-[#efeee8] border border-[#e4e2dd] rounded-sm p-4 sm:p-6 shadow-sm">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-4 mb-4 border-b border-[#e4e2dd]">
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center gap-2 flex-wrap">
              <Link
                to="/events"
                className="font-mono text-[10px] uppercase text-[#5a413c] hover:text-[#1b1c19] px-2 py-0.5 bg-[#fbf9f4] border border-[#e4e2dd] rounded-sm flex items-center gap-1"
              >
                <ArrowLeft className="w-3 h-3" />
                <span>Events Directory</span>
              </Link>
              <span className="font-mono text-[10px] uppercase px-2 py-0.5 bg-[#dbe7c1] text-[#404a2e] rounded-sm font-semibold">
                SHOWTIME #{id}
              </span>
              <span className="font-mono text-[10px] uppercase px-2 py-0.5 bg-[#ffdea4] text-[#775600] rounded-sm font-semibold">
                CONCURRENCY LOCK ACTIVE
              </span>
            </div>
            <h1 className="font-headline text-2xl sm:text-3xl text-[#1b1c19] font-normal tracking-tight">
              Venue Seating Plan &amp; Allocation Matrix
            </h1>
          </div>

          <div className="flex items-center gap-3 shrink-0">
            <div className="bg-[#fbf9f4] border border-[#d1c9b8] px-3.5 py-2 rounded-sm flex items-center gap-2.5 shadow-[2px_2px_0px_0px_#1b1c19]">
              <div className={`w-2 h-2 rounded-full ${isLiveConnected ? 'bg-[#576244] animate-pulse' : 'bg-[#e8b856]'}`}></div>
              <div className="flex flex-col">
                <span className="font-mono text-[9px] text-[#5a413c] uppercase leading-none">
                  SSE Broadcast
                </span>
                <span className="font-mono text-xs font-semibold text-[#1b1c19]">
                  {isLiveConnected ? 'REAL-TIME ACTIVE' : 'CONNECTING...'}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Monospaced Metadata Ribbon */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-[#fbf9f4] border border-[#e4e2dd] p-3 rounded-sm text-[#1b1c19]">
          <div className="flex flex-col">
            <span className="font-mono text-[10px] text-[#5a413c] uppercase">SESSION ID</span>
            <span className="font-mono text-xs font-medium text-[#1b1c19]">#SHOWTIME-{id}</span>
          </div>
          <div className="flex flex-col">
            <span className="font-mono text-[10px] text-[#5a413c] uppercase">ALLOCATION QUOTA</span>
            <span className="font-mono text-xs font-medium text-[#1b1c19]">MAX 6 SEATS / PATRON</span>
          </div>
          <div className="flex flex-col">
            <span className="font-mono text-[10px] text-[#5a413c] uppercase">HOLD TTL</span>
            <span className="font-mono text-xs font-medium text-[#ae2714]">600s LEASE AT CHECKOUT</span>
          </div>
          <div className="flex flex-col">
            <span className="font-mono text-[10px] text-[#5a413c] uppercase">PIPELINE INTEGRITY</span>
            <span className="font-mono text-xs text-[#576244] font-medium flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#576244] inline-block animate-pulse"></span>
              LOCK DETERMINISTIC
            </span>
          </div>
        </div>
      </div>

      {/* Notifications Bar */}
      {conflictNotice && (
        <div className="bg-[#ffdad6]/40 border border-[#ba1a1a]/30 p-3.5 rounded-sm text-[#ba1a1a] flex items-center justify-between text-xs font-mono">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0 text-[#ba1a1a]" />
            <span>{conflictNotice}</span>
          </div>
          <button
            type="button"
            onClick={() => setConflictNotice(null)}
            className="text-xs uppercase font-semibold underline text-[#ba1a1a]"
          >
            Dismiss
          </button>
        </div>
      )}

      {maxLimitNotice && (
        <div className="bg-[#ffdea4]/50 border border-[#f0bf5c] p-3.5 rounded-sm text-[#775600] flex items-center justify-between text-xs font-mono">
          <div className="flex items-center gap-2">
            <Info className="w-4 h-4 shrink-0 text-[#775600]" />
            <span>{maxLimitNotice}</span>
          </div>
          <button
            type="button"
            onClick={() => setMaxLimitNotice(null)}
            className="text-xs uppercase font-semibold underline text-[#775600]"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Hold Released Notice Bar */}
      {releasedBanner && (
        <div className="bg-[#dbe7c1]/60 border border-[#576244]/40 p-3.5 rounded-sm text-[#404a2e] flex items-center justify-between text-xs font-mono">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0 text-[#576244]" />
            <span>{releasedBanner}</span>
          </div>
          <button
            type="button"
            onClick={() => setReleasedBanner(null)}
            className="text-xs uppercase font-semibold underline text-[#404a2e]"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Seat Released Popup Modal */}
      {showReleasedModal && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 bg-[#1b1c19]/60 backdrop-blur-xs flex items-center justify-center p-4 animate-in fade-in duration-200"
        >
          <div className="bg-[#ffffff] border border-[#1b1c19] max-w-md w-full p-6 rounded-sm shadow-[4px_4px_0px_0px_#1b1c19] space-y-4">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                <span className="w-9 h-9 rounded-full bg-[#dbe7c1] text-[#404a2e] flex items-center justify-center shrink-0">
                  <CheckCircle2 className="w-5 h-5 text-[#576244]" />
                </span>
                <div>
                  <h3 className="font-headline text-xl text-[#1b1c19]">Seat Released</h3>
                  <span className="font-mono text-[10px] uppercase text-[#5a413c]">
                    Inventory Restored to Pool
                  </span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowReleasedModal(false)}
                className="text-[#8e706b] hover:text-[#1b1c19] transition-colors p-1"
                aria-label="Close dialog"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <p className="text-xs text-[#5a413c] leading-relaxed">
              Your temporary hold has been released. The selected seat{releasedSeatCount > 1 ? 's have' : ' has'} been returned to the public pool and {releasedSeatCount > 1 ? 'are' : 'is'} now available for selection.
            </p>

            <div className="flex justify-end pt-2">
              <button
                type="button"
                onClick={() => setShowReleasedModal(false)}
                className="px-4 py-2 bg-[#d1402a] hover:bg-[#30312d] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold transition-all shadow-[2px_2px_0px_0px_#1b1c19] active:translate-x-[1px] active:translate-y-[1px]"
              >
                Continue Selection
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Primary Seating Layout: 12-Column Split */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* LEFT 7 COLUMNS: Interactive Architectural Seating Arena */}
        <div className="lg:col-span-7 flex flex-col gap-6">
          <div className="bg-[#efeee8] border border-[#e4e2dd] rounded-sm p-4 sm:p-6 flex flex-col relative overflow-hidden shadow-sm">
            {/* Legend Strip */}
            <div className="flex flex-wrap items-center justify-between gap-3 bg-[#fbf9f4] border border-[#e4e2dd] p-3 rounded-sm mb-6 text-[#1b1c19]">
              <div className="flex items-center gap-2">
                <span className="w-3.5 h-3.5 rounded-sm bg-[#dbe7c1] border border-[#576244]"></span>
                <span className="font-mono text-xs uppercase text-[#1b1c19]">Available</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-3.5 h-3.5 rounded-sm bg-[#d1402a] text-[#fffafa] text-[10px] flex items-center justify-center font-bold">
                  {selectedSeatIds.length}
                </span>
                <span className="font-mono text-xs uppercase text-[#1b1c19] font-semibold">Your Selection</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-3.5 h-3.5 rounded-sm bg-[#f0bf5c] border border-[#775600]"></span>
                <span className="font-mono text-xs uppercase text-[#5a413c]">Held by Patron</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-3.5 h-3.5 rounded-sm bg-[#30312d] opacity-70"></span>
                <span className="font-mono text-xs uppercase text-[#5a413c]">Booked / Sold</span>
              </div>
            </div>

            {/* Stage Proscenium Indicator */}
            <div className="w-full flex flex-col items-center mb-8">
              <div className="w-4/5 max-w-md h-10 bg-[#fbf9f4] border border-[#1b1c19] flex flex-col items-center justify-center rounded-sm shadow-[2px_2px_0px_0px_#1b1c19]">
                <span className="font-mono text-xs tracking-widest uppercase font-semibold text-[#1b1c19]">
                  STAGE // PROSCENIUM ACOUSTIC CANOPY
                </span>
                <span className="font-mono text-[9px] text-[#5a413c]">MAIN PERFORMANCE GROUND</span>
              </div>
              <div className="w-2/3 h-1 bg-[#e4e2dd] mt-1.5 rounded-full"></div>
            </div>

            {/* Loading / Error States for Seats */}
            {isLoading && (
              <div className="py-20 text-center space-y-3 font-mono text-xs text-[#5a413c]">
                <RefreshCw className="w-6 h-6 mx-auto animate-spin text-[#d1402a]" />
                <p>Loading real-time seat inventory from PostgreSQL...</p>
              </div>
            )}

            {error && (
              <div className="py-12 text-center space-y-3 bg-[#ffdad6]/40 border border-[#ba1a1a]/30 p-6 rounded-sm text-[#ba1a1a]">
                <AlertCircle className="w-8 h-8 mx-auto" />
                <h3 className="font-headline text-lg">Unable to load seat availability</h3>
                <p className="text-xs text-[#5a413c]">
                  {error.message || 'Check database connection and verify showtime exists.'}
                </p>
                <button
                  type="button"
                  onClick={() => refetch()}
                  className="px-4 py-2 bg-[#d1402a] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold"
                >
                  Retry Loading
                </button>
              </div>
            )}

            {/* Seating Matrix Grid */}
            {!isLoading && !error && seats && (
              <div className="w-full overflow-x-auto pb-4 flex flex-col items-center gap-3">
                {Object.keys(rows).length === 0 ? (
                  <p className="text-sm font-mono text-[#5a413c] py-12">
                    No seats configured for this venue.
                  </p>
                ) : (
                  Object.keys(rows).map((rowLabel) => {
                    const rowSeats = rows[rowLabel];
                    return (
                      <div key={rowLabel} className="flex items-center gap-2">
                        {/* Row Header Label */}
                        <span className="w-6 font-mono text-xs font-bold text-[#5a413c] text-center">
                          {rowLabel}
                        </span>

                        {/* Seat Nodes in Row */}
                        <div className="flex gap-1.5 sm:gap-2">
                          {rowSeats.map((seat) => {
                            const isSelected = selectedSeatIds.includes(seat.id);
                            const isAvailable = seat.status === 'AVAILABLE';
                            const isHeld = seat.status === 'HELD';
                            const isBooked = seat.status === 'BOOKED';

                            let buttonStyle = 'bg-[#dbe7c1] text-[#1b1c19] hover:bg-[#d1402a] hover:text-[#fffafa] border border-[#576244]/40 cursor-pointer';

                            if (isSelected) {
                              buttonStyle = 'bg-[#d1402a] text-[#fffafa] shadow-[1px_1px_0px_0px_#1b1c19] cursor-pointer';
                            } else if (isHeld) {
                              buttonStyle = 'bg-[#f0bf5c] text-[#775600] border border-[#775600] cursor-not-allowed opacity-80';
                            } else if (isBooked) {
                              buttonStyle = 'bg-[#30312d] text-[#f2f1eb] opacity-60 cursor-not-allowed';
                            }

                            return (
                              <button
                                key={seat.id}
                                type="button"
                                disabled={!isAvailable && !isSelected}
                                onClick={() => toggleSeatSelection(seat)}
                                title={`Row ${seat.rowLabel}, Seat ${seat.seatNumber} (${seat.category || 'Standard'}) - $${seat.price || '0.00'} [${seat.status}]`}
                                aria-label={`Seat ${seat.rowLabel}${seat.seatNumber}, Price: $${seat.price}, Status: ${seat.status}`}
                                className={`w-7 h-7 sm:w-8 sm:h-8 rounded-sm font-mono text-[10px] sm:text-xs font-semibold transition-all flex items-center justify-center focus:outline-none focus:ring-2 focus:ring-[#1b1c19] ${buttonStyle}`}
                              >
                                {seat.seatNumber}
                              </button>
                            );
                          })}
                        </div>

                        {/* Row Footer Label */}
                        <span className="w-6 font-mono text-xs font-bold text-[#5a413c] text-center">
                          {rowLabel}
                        </span>
                      </div>
                    );
                  })
                )}
              </div>
            )}

            {/* Auditorial Footnote & Pan Note */}
            <div className="mt-6 pt-4 border-t border-[#e4e2dd] flex flex-col sm:flex-row items-center justify-between text-[#5a413c] gap-2">
              <span className="font-mono text-[10px] flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-[#576244]"></span>
                Click available seat to toggle selection • Leased for 10 minutes at checkout
              </span>
              <span className="font-mono text-[10px] uppercase text-[#576244] font-semibold">
                DETERMINISTIC CONFLICT PREVENTION
              </span>
            </div>
          </div>

          {/* Architectural Venue Notes Box */}
          <div className="bg-[#efeee8] border border-[#e4e2dd] p-4 rounded-sm flex items-start gap-3">
            <ShieldCheck className="w-5 h-5 text-[#576244] shrink-0 mt-0.5" />
            <div className="flex flex-col gap-1">
              <span className="font-headline text-base text-[#1b1c19] font-normal">
                Acoustic Sightline &amp; Spatial Alignment
              </span>
              <p className="text-xs text-[#5a413c] leading-relaxed">
                Venue seating is tiered to ensure unimpeded direct-wave acoustics. When seats are held, Redis distributed locks reserve each seat coordinate exclusively to your checkout session before ledger commitment.
              </p>
            </div>
          </div>
        </div>

        {/* RIGHT 5 COLUMNS: Sticky Physical Ticket Dossier & Allocation Summary */}
        <div className="lg:col-span-5 flex flex-col gap-6 lg:sticky lg:top-20">
          <div className="bg-[#ffffff] border border-[#1b1c19] rounded-sm shadow-[4px_4px_0px_0px_#1b1c19] overflow-hidden">
            {/* Ticket Dossier Header */}
            <div className="p-6 bg-[#fbf9f4]">
              <div className="flex items-center justify-between mb-2">
                <span className="font-mono text-[10px] uppercase px-2 py-0.5 bg-[#efeee8] border border-[#e4e2dd] text-[#5a413c] rounded-sm">
                  ALLOCATION VOUCHER
                </span>
                <span className="font-mono text-[10px] uppercase text-[#ae2714] font-bold">
                  SESSION #{id}
                </span>
              </div>
              <h2 className="font-headline text-2xl text-[#1b1c19] font-normal">
                Selected Allocation ({selectedSeatIds.length}/6)
              </h2>
              <p className="text-xs text-[#5a413c] mt-0.5">
                Deterministic seat reservations ready for checkout lock.
              </p>
            </div>

            {/* Perforated Notch Divider */}
            <div className="relative w-full h-5 bg-[#efeee8] border-y border-[#e4e2dd] flex items-center justify-between px-0 overflow-hidden">
              <div className="w-3 h-6 bg-[#fbf9f4] rounded-r-full -ml-1 border-r border-[#e4e2dd]"></div>
              <div className="flex-1 flex justify-center gap-1.5 overflow-hidden">
                {[...Array(14)].map((_, i) => (
                  <span key={i} className="w-2 h-0.5 bg-[#8e706b]/40"></span>
                ))}
              </div>
              <div className="w-3 h-6 bg-[#fbf9f4] rounded-l-full -mr-1 border-l border-[#e4e2dd]"></div>
            </div>

            {/* Ticket Body: Allocated Seats List */}
            <div className="p-6 bg-[#efeee8] flex flex-col gap-4">
              {/* Itemized Seat Reservations */}
              {selectedSeats.length === 0 ? (
                <div className="p-6 border border-dashed border-[#d1c9b8] rounded-sm text-center text-[#5a413c] space-y-1">
                  <p className="text-xs font-mono uppercase">No seats selected yet</p>
                  <p className="text-[11px]">Click up to 6 available seats on the map.</p>
                </div>
              ) : (
                <div className="flex flex-col gap-2.5 max-h-60 overflow-y-auto pr-1">
                  {selectedSeats.map((seat) => (
                    <div
                      key={seat.id}
                      className="p-3 bg-[#ffffff] border border-[#e4e2dd] rounded-sm flex items-center justify-between shadow-sm"
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-7 h-7 bg-[#d1402a] rounded-sm flex items-center justify-center text-[#fffafa] font-mono text-xs font-bold">
                          {seat.seatNumber}
                        </div>
                        <div className="flex flex-col">
                          <span className="font-mono text-xs text-[#1b1c19] font-semibold">
                            Row {seat.rowLabel}, Seat {seat.seatNumber}
                          </span>
                          <span className="font-mono text-[10px] text-[#5a413c]">
                            {seat.category || 'Standard Tier'}
                          </span>
                        </div>
                      </div>
                      <div className="flex items-center gap-3">
                        <span className="font-mono text-xs font-bold text-[#1b1c19]">
                          ${Number(seat.price || 0).toFixed(2)}
                        </span>
                        <button
                          type="button"
                          onClick={() => toggleSeatSelection(seat)}
                          aria-label={`Remove Seat ${seat.rowLabel}${seat.seatNumber}`}
                          className="text-[#5a413c] hover:text-[#ae2714] p-1 transition-colors"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Price Calculation Ledger */}
              <div className="pt-2 border-t border-[#e4e2dd] flex flex-col gap-2 text-[#1b1c19]">
                <div className="flex justify-between text-xs text-[#5a413c]">
                  <span>Subtotal ({selectedSeats.length} Admissions)</span>
                  <span className="font-mono font-medium text-[#1b1c19]">
                    ${totalPrice.toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between font-headline text-xl text-[#1b1c19] pt-1 border-t border-[#e4e2dd]">
                  <span>Total Commitment</span>
                  <span className="font-mono text-[#d1402a] font-bold">
                    ${totalPrice.toFixed(2)}
                  </span>
                </div>
              </div>

              {/* Actions */}
              <div className="flex flex-col gap-2 pt-2">
                <button
                  type="button"
                  disabled={selectedSeats.length === 0}
                  onClick={handleContinueToCheckout}
                  className="w-full bg-[#d1402a] hover:bg-[#30312d] disabled:opacity-40 disabled:hover:bg-[#d1402a] text-[#fffafa] py-3 rounded-sm font-mono text-xs uppercase font-bold tracking-wider transition-all shadow-[2px_2px_0px_0px_#1b1c19] active:translate-x-[1px] active:translate-y-[1px] flex items-center justify-center gap-2"
                >
                  <span>Proceed to Checkout ({selectedSeats.length} Seats)</span>
                  <ArrowRight className="w-4 h-4" />
                </button>

                {selectedSeats.length > 0 && (
                  <div className="flex items-center justify-between pt-1">
                    <button
                      type="button"
                      onClick={handleClearSelection}
                      className="font-mono text-[10px] uppercase text-[#5a413c] hover:text-[#ae2714] underline transition-colors"
                    >
                      Clear Selection
                    </button>
                    <span className="font-mono text-[10px] text-[#576244] font-medium">
                      Guaranteed No-Double-Booking
                    </span>
                  </div>
                )}
              </div>

              {/* Real-time Synchronization Status */}
              <div className="pt-2 bg-[#fbf9f4] border border-[#e4e2dd] p-2 rounded-sm flex items-center justify-between text-[#5a413c] font-mono text-[10px]">
                <div className="flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${isLiveConnected ? 'bg-[#576244]' : 'bg-[#e8b856]'}`}></span>
                  <span>SSE CONNECTION: {isLiveConnected ? 'ACTIVE' : 'CONNECTING'}</span>
                </div>
                <span>CONCURRENCY LOCK v2.4</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
