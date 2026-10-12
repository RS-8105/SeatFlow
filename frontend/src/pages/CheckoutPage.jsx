import React, { useState, useEffect, useRef } from 'react';
import { useLocation, Link, useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api-client';
import {
  Clock,
  ShieldCheck,
  AlertCircle,
  ArrowLeft,
  RotateCcw,
  CheckCircle2,
  Lock,
  Hourglass,
  RefreshCw,
  BadgeCheck,
  Building2,
  Ticket
} from 'lucide-react';

export const CheckoutPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { showtimeId, seatIds, seats, totalAmount } = location.state || {};

  const holdRequestedRef = useRef(false);
  const idempotencyKeyRef = useRef(null);
  if (!idempotencyKeyRef.current) {
    idempotencyKeyRef.current =
      typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `idem-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  }

  const [secondsRemaining, setSecondsRemaining] = useState(null);
  const [isExpired, setIsExpired] = useState(false);
  const [isReleased, setIsReleased] = useState(false);
  const [isConfirmed, setIsConfirmed] = useState(false);
  const [releaseError, setReleaseError] = useState(null);
  const [confirmError, setConfirmError] = useState(null);

  // Form attendee fields
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');

  // 1. Mutation to acquire Redis temporary hold
  const holdMutation = useMutation({
    mutationFn: (payload) =>
      api.createHold(payload.showtimeId, {
        seatIds: payload.seatIds,
      }),
  });

  // 2. Mutation to safely release hold via Lua compare-and-delete
  const releaseMutation = useMutation({
    mutationFn: (payload) =>
      api.releaseHold({
        showtimeId: payload.showtimeId,
        seatIds: payload.seatIds,
        holdToken: payload.holdToken,
      }),
    onSuccess: () => {
      setIsReleased(true);
      setReleaseError(null);
      queryClient.invalidateQueries({ queryKey: ['seats', showtimeId] });
      queryClient.invalidateQueries({ queryKey: ['showtime-seats', showtimeId] });
      navigate(`/showtimes/${showtimeId}/seats`, {
        replace: true,
        state: { holdReleased: true, releasedSeatCount: seatIds?.length || 1 },
      });
    },
    onError: (err) => {
      setReleaseError(err.message || 'Failed to release hold on server.');
      queryClient.invalidateQueries({ queryKey: ['seats', showtimeId] });
      queryClient.invalidateQueries({ queryKey: ['showtime-seats', showtimeId] });
      navigate(`/showtimes/${showtimeId}/seats`, {
        replace: true,
        state: { holdReleased: true, releasedSeatCount: seatIds?.length || 1 },
      });
    },
  });

  // 3. Mutation to confirm booking into PostgreSQL with idempotency protection
  const confirmMutation = useMutation({
    mutationFn: (payload) =>
      api.confirmBooking(
        {
          showtimeId: payload.showtimeId,
          seatIds: payload.seatIds,
          holdToken: payload.holdToken,
        },
        idempotencyKeyRef.current
      ),
    onSuccess: (data) => {
      setIsConfirmed(true);
      setConfirmError(null);
      queryClient.invalidateQueries({ queryKey: ['my-bookings'] });
      queryClient.invalidateQueries({ queryKey: ['booking', data.id] });
      navigate(`/bookings/${data.id}`, { state: { booking: data } });
    },
    onError: (err) => {
      const isUncertain = !err.status || err.status >= 500;
      setConfirmError({
        isUncertain,
        status: err.status,
        message: err.message || 'Failed to confirm reservation.',
      });

      if (err.status === 404) {
        setIsExpired(true);
      }
    },
  });

  // Request hold on mount with valid state
  useEffect(() => {
    if (showtimeId && seatIds && seatIds.length > 0 && !holdRequestedRef.current) {
      holdRequestedRef.current = true;
      holdMutation.mutate({ showtimeId, seatIds });
    }
  }, [showtimeId, seatIds]);

  // Countdown timer derived strictly from backend-provided expiresAt
  useEffect(() => {
    if (!holdMutation.data?.expiresAt || isReleased || isConfirmed) return;

    const expiryTime = new Date(holdMutation.data.expiresAt).getTime();

    const updateCountdown = () => {
      const now = Date.now();
      const diff = Math.max(0, Math.floor((expiryTime - now) / 1000));
      setSecondsRemaining(diff);

      if (diff <= 0) {
        setIsExpired(true);
      }
    };

    updateCountdown();
    const timer = setInterval(updateCountdown, 1000);

    return () => clearInterval(timer);
  }, [holdMutation.data?.expiresAt, isReleased, isConfirmed]);

  const formatCountdown = (totalSecs) => {
    if (totalSecs == null || totalSecs < 0) return '00:00';
    const minutes = Math.floor(totalSecs / 60);
    const seconds = totalSecs % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  const isHoldActive = Boolean(
    holdMutation.isSuccess &&
    holdMutation.data?.holdToken &&
    !isExpired &&
    !isReleased &&
    !isConfirmed &&
    (secondsRemaining === null || secondsRemaining > 0)
  );

  const canConfirm = isHoldActive && !confirmMutation.isPending;

  const handleConfirm = (e) => {
    e.preventDefault();
    if (!canConfirm || confirmMutation.isPending) return;

    confirmMutation.mutate({
      showtimeId,
      seatIds,
      holdToken: holdMutation.data.holdToken,
    });
  };

  const handleRelease = () => {
    if (releaseMutation.isPending || isReleased || isConfirmed) {
      return;
    }

    if (!holdMutation.data?.holdToken) {
      navigate(`/showtimes/${showtimeId}/seats`, {
        replace: true,
        state: { holdReleased: true, releasedSeatCount: seatIds?.length || 1 },
      });
      return;
    }

    releaseMutation.mutate({
      showtimeId,
      seatIds,
      holdToken: holdMutation.data.holdToken,
    });
  };

  // Missing Navigation State Fallback
  if (!showtimeId || !seatIds || seatIds.length === 0) {
    return (
      <div className="max-w-md mx-auto my-16 bg-[#efeee8] border border-[#e4e2dd] p-8 rounded-sm text-center space-y-4">
        <AlertCircle className="w-10 h-10 text-[#775600] mx-auto" />
        <h2 className="text-xl font-headline font-normal text-[#1b1c19]">No Active Hold Selection</h2>
        <p className="text-xs text-[#5a413c] leading-relaxed">
          You navigated to checkout without selecting seats. Please choose seats from a showtime to initiate an exclusive lock.
        </p>
        <Link
          to="/events"
          className="inline-flex items-center gap-2 px-4 py-2 bg-[#d1402a] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Browse Available Events</span>
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-[1280px] mx-auto pb-16">
      {/* Progress Breadcrumbs Tracker */}
      <div className="w-full bg-[#efeee8] border border-[#e4e2dd] rounded-sm p-3 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2 font-mono text-[11px] uppercase text-[#5a413c]">
          <div className="flex items-center gap-2">
            <span className="w-4 h-4 rounded-full bg-[#dbe7c1] text-[#404a2e] flex items-center justify-center text-[10px] font-bold">✓</span>
            <span className="line-through opacity-70">1. Showtime</span>
          </div>
          <span className="text-[#8e706b]/40">/</span>
          <div className="flex items-center gap-2">
            <span className="w-4 h-4 rounded-full bg-[#dbe7c1] text-[#404a2e] flex items-center justify-center text-[10px] font-bold">✓</span>
            <span className="line-through opacity-70">2. Seat Selection</span>
          </div>
          <span className="text-[#8e706b]/40">/</span>
          <div className="flex items-center gap-2 bg-[#ffffff] border border-[#d1c9b8] px-2.5 py-1 rounded-sm text-[#d1402a] font-semibold shadow-sm">
            <span className="w-2 h-2 rounded-full bg-[#d1402a] animate-pulse"></span>
            <span className="tracking-wider">3. Checkout &amp; Review (Current)</span>
          </div>
          <span className="text-[#8e706b]/40">/</span>
          <div className="flex items-center gap-2 opacity-50">
            <span className="w-4 h-4 rounded-full bg-[#e4e2dd] flex items-center justify-center text-[10px]">4</span>
            <span>4. Confirmation &amp; Settlement</span>
          </div>
        </div>
      </div>

      {/* Hold Loading Banner */}
      {holdMutation.isPending && (
        <div className="bg-[#efeee8] border border-[#e4e2dd] p-4 rounded-sm flex items-center justify-between font-mono text-xs text-[#5a413c]">
          <div className="flex items-center gap-2">
            <RefreshCw className="w-4 h-4 animate-spin text-[#d1402a]" />
            <span>Acquiring atomic Redis hold lease for seats: [{seatIds.join(', ')}]...</span>
          </div>
          <span>LOCK IN PROGRESS</span>
        </div>
      )}

      {/* Hold Error Banner */}
      {holdMutation.isError && (
        <div className="bg-[#ffdad6]/50 border border-[#ba1a1a]/40 p-4 rounded-sm text-[#ba1a1a] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs font-mono">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-5 h-5 shrink-0" />
            <div>
              <span className="font-bold block">Failed to acquire seat hold</span>
              <span>{holdMutation.error?.message || 'One or more seats may already be held by another user.'}</span>
            </div>
          </div>
          <Link
            to={`/showtimes/${showtimeId}/seats`}
            className="px-3 py-1.5 bg-[#ba1a1a] text-white rounded-sm uppercase font-semibold"
          >
            Reselect Seats
          </Link>
        </div>
      )}


      {/* Expired Hold Banner */}
      {isExpired && !isConfirmed && (
        <div className="bg-[#ffdad6]/50 border border-[#ba1a1a]/40 p-4 rounded-sm text-[#ba1a1a] flex flex-col sm:flex-row items-center justify-between gap-3 text-xs font-mono">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-5 h-5 shrink-0" />
            <div>
              <span className="font-bold block">Hold Reservation Expired</span>
              <span>Your 10-minute hold window elapsed. Please re-select available seats.</span>
            </div>
          </div>
          <Link
            to={`/showtimes/${showtimeId}/seats`}
            className="px-3 py-1.5 bg-[#ba1a1a] text-white rounded-sm uppercase font-semibold"
          >
            Return to Seat Map
          </Link>
        </div>
      )}

      {/* Released Hold Banner */}
      {isReleased && (
        <div className="bg-[#efeee8] border border-[#e4e2dd] p-4 rounded-sm text-[#5a413c] flex flex-col sm:flex-row items-center justify-between gap-3 text-xs font-mono">
          <div className="flex items-center gap-2">
            <RotateCcw className="w-5 h-5 shrink-0 text-[#8e706b]" />
            <span>Hold released successfully. Seat locks have been revoked in Redis.</span>
          </div>
          <Link
            to={`/showtimes/${showtimeId}/seats`}
            className="px-3 py-1.5 bg-[#30312d] text-white rounded-sm uppercase font-semibold"
          >
            Select New Seats
          </Link>
        </div>
      )}

      {/* Main Split Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Column (7 cols): Event Dossier & Attendee Specs */}
        <div className="lg:col-span-7 flex flex-col gap-6">
          {/* Event Dossier Card */}
          <div className="bg-[#efeee8] border border-[#e4e2dd] rounded-sm p-6 shadow-sm">
            <div className="flex flex-col sm:flex-row justify-between items-start gap-4 pb-4 border-b border-[#e4e2dd]">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-mono text-[10px] uppercase text-[#5a413c] tracking-widest">
                    RESERVATION SPECIMEN
                  </span>
                  <span className="text-[#8e706b]">•</span>
                  <span className="font-mono text-[10px] uppercase bg-[#fbf9f4] border border-[#e4e2dd] px-1.5 py-0.5 rounded-sm text-[#1b1c19]">
                    SHOWTIME #{showtimeId}
                  </span>
                </div>
                <h1 className="font-headline text-3xl text-[#1b1c19] font-normal">
                  Order Review &amp; Allocation Settlement
                </h1>
              </div>
              <div className="shrink-0 text-right font-mono text-xs text-[#5a413c]">
                <span className="block text-[10px] uppercase">STATUS</span>
                <span className="font-semibold text-[#576244] block">
                  {isHoldActive ? 'HOLD ACTIVE' : isExpired ? 'EXPIRED' : 'PROCESSING'}
                </span>
              </div>
            </div>

            {/* Seat Vector */}
            <div className="mt-4">
              <span className="font-mono text-[10px] uppercase text-[#5a413c] block mb-2">
                Allocated Seat Vector ({seatIds.length} Units)
              </span>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {seats && seats.map((seat) => (
                  <div
                    key={seat.id}
                    className="bg-[#ffffff] border border-[#e4e2dd] p-3 rounded-sm shadow-sm flex items-center justify-between"
                  >
                    <div className="flex items-center gap-2.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-[#d1402a] inline-block"></span>
                      <div>
                        <span className="font-mono text-xs font-semibold text-[#1b1c19] block">
                          ROW {seat.rowLabel} • SEAT {seat.seatNumber}
                        </span>
                        <span className="font-mono text-[10px] text-[#5a413c]">
                          {seat.category || 'Standard Tier'}
                        </span>
                      </div>
                    </div>
                    <span className="font-mono text-xs font-semibold text-[#1b1c19]">
                      ${Number(seat.price || 0).toFixed(2)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Attendee Form */}
          <div className="bg-[#efeee8] border border-[#e4e2dd] rounded-sm p-6 shadow-sm">
            <div className="flex items-center justify-between pb-3 mb-4 border-b border-[#e4e2dd]">
              <span className="font-headline text-xl text-[#1b1c19]">
                Attendee Record (Optional Notation)
              </span>
              <span className="font-mono text-[10px] text-[#5a413c] uppercase">
                JWT Identity Verified
              </span>
            </div>

            <form onSubmit={handleConfirm} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="firstName" className="block font-mono text-[10px] text-[#5a413c] uppercase mb-1">
                    First Name
                  </label>
                  <input
                    id="firstName"
                    type="text"
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    placeholder="Clara"
                    className="w-full bg-[#ffffff] text-[#1b1c19] border border-[#d1c9b8] px-3 py-2 rounded-sm text-sm focus:outline-none focus:border-[#1b1c19]"
                  />
                </div>
                <div>
                  <label htmlFor="lastName" className="block font-mono text-[10px] text-[#5a413c] uppercase mb-1">
                    Last Name
                  </label>
                  <input
                    id="lastName"
                    type="text"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    placeholder="Wiesner"
                    className="w-full bg-[#ffffff] text-[#1b1c19] border border-[#d1c9b8] px-3 py-2 rounded-sm text-sm focus:outline-none focus:border-[#1b1c19]"
                  />
                </div>
              </div>

              <div>
                <label htmlFor="checkout-email" className="block font-mono text-[10px] text-[#5a413c] uppercase mb-1">
                  Dispatch Email
                </label>
                <input
                  id="checkout-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="patron@orchestra.org"
                  className="w-full bg-[#ffffff] text-[#1b1c19] border border-[#d1c9b8] px-3 py-2 rounded-sm text-sm focus:outline-none focus:border-[#1b1c19]"
                />
              </div>

              {/* Simulated Mode Callout */}
              <div className="bg-[#fbf9f4] border border-[#e4e2dd] p-3.5 rounded-sm flex items-start gap-2.5 mt-4">
                <ShieldCheck className="w-4 h-4 text-[#576244] shrink-0 mt-0.5" />
                <div className="text-xs text-[#5a413c] leading-relaxed">
                  <span className="font-mono uppercase font-bold text-[#1b1c19] block mb-0.5">
                    Deterministic Reservation Sandbox Mode
                  </span>
                  Simulated payment environment active. No real credit card or fiat currency will be debited. Transactions evaluate true distributed locking and idempotency validation.
                </div>
              </div>
            </form>
          </div>
        </div>

        {/* Right Column (5 cols): Order Summary & Confirm Actions */}
        <div className="lg:col-span-5 flex flex-col gap-6 lg:sticky lg:top-20">
          <div className="bg-[#ffffff] border border-[#1b1c19] rounded-sm shadow-[4px_4px_0px_0px_#1b1c19] overflow-hidden">
            {/* Header */}
            <div className="p-6 bg-[#fbf9f4]">
              <div className="flex items-center justify-between mb-2">
                <span className="font-mono text-[10px] uppercase px-1.5 py-0.5 bg-[#efeee8] border border-[#e4e2dd] rounded-sm text-[#1b1c19] font-semibold">
                  OFFICIAL BILLING LEDGER
                </span>
                <span className="font-mono text-[10px] text-[#5a413c]">
                  IDEMPOTENCY ARMED
                </span>
              </div>
              <h2 className="font-headline text-2xl text-[#1b1c19] font-normal">
                Seat Allocation Summary
              </h2>
              <span className="font-mono text-xs text-[#5a413c] block mt-0.5">
                Showtime #{showtimeId} • {seatIds.length} Admissions
              </span>
            </div>

            {/* Perforated Notch */}
            <div className="relative w-full h-5 bg-[#efeee8] border-y border-[#e4e2dd] flex items-center justify-between px-0 overflow-hidden">
              <div className="w-3 h-6 bg-[#fbf9f4] rounded-r-full -ml-1 border-r border-[#e4e2dd]"></div>
              <div className="flex-1 flex justify-center gap-1.5 overflow-hidden">
                {[...Array(14)].map((_, i) => (
                  <span key={i} className="w-2 h-0.5 bg-[#8e706b]/40"></span>
                ))}
              </div>
              <div className="w-3 h-6 bg-[#fbf9f4] rounded-l-full -mr-1 border-l border-[#e4e2dd]"></div>
            </div>

            {/* Body */}
            <div className="p-6 bg-[#efeee8] flex flex-col gap-4">
              <div className="flex flex-col gap-2 border-b border-[#e4e2dd] pb-3 text-xs">
                <div className="flex justify-between text-[#5a413c]">
                  <span>Subtotal ({seatIds.length} Seats)</span>
                  <span className="font-mono font-medium text-[#1b1c19]">
                    ${Number(totalAmount || 0).toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between text-[#5a413c]">
                  <span>Simulated Booking Fee</span>
                  <span className="font-mono font-medium text-[#576244]">$0.00</span>
                </div>
                <div className="flex justify-between font-headline text-xl text-[#1b1c19] pt-2 border-t border-[#e4e2dd]">
                  <span>Total Commitment</span>
                  <span className="font-mono text-[#d1402a] font-bold">
                    ${Number(totalAmount || 0).toFixed(2)}
                  </span>
                </div>
              </div>

              {/* Error on Confirm */}
              {confirmError && (
                <div className="bg-[#ffdad6]/60 border border-[#ba1a1a]/40 p-3 rounded-sm text-[#ba1a1a] text-xs font-mono">
                  <div className="flex items-center gap-1.5 font-bold mb-1">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>Confirmation Failed</span>
                  </div>
                  <p>{confirmError.message}</p>
                </div>
              )}

              {/* Action Buttons */}
              <div className="flex flex-col gap-2.5 pt-1">
                <button
                  type="button"
                  disabled={!canConfirm}
                  onClick={handleConfirm}
                  className="w-full bg-[#d1402a] hover:bg-[#30312d] disabled:opacity-40 disabled:hover:bg-[#d1402a] text-[#fffafa] py-3.5 rounded-sm font-mono text-xs uppercase font-bold tracking-wider transition-all shadow-[2px_2px_0px_0px_#1b1c19] active:translate-x-[1px] active:translate-y-[1px] flex items-center justify-center gap-2"
                >
                  {confirmMutation.isPending ? (
                    <>
                      <RefreshCw className="w-4 h-4 animate-spin" />
                      <span>Settling Ledger in PostgreSQL...</span>
                    </>
                  ) : (
                    <>
                      <span>Confirm &amp; Settle Reservation</span>
                      <ArrowLeft className="w-4 h-4 rotate-180" />
                    </>
                  )}
                </button>

                {isHoldActive && (
                  <button
                    type="button"
                    disabled={releaseMutation.isPending}
                    onClick={handleRelease}
                    className="w-full bg-[#efeee8] hover:bg-[#e4e2dd] border border-[#d1c9b8] text-[#5a413c] hover:text-[#ae2714] py-2 rounded-sm font-mono text-xs uppercase transition-colors"
                  >
                    {releaseMutation.isPending ? 'Releasing Lock...' : 'Release Hold Back to Pool'}
                  </button>
                )}
              </div>

              <div className="pt-2 bg-[#fbf9f4] border border-[#e4e2dd] p-2.5 rounded-sm text-[#5a413c] font-mono text-[10px] space-y-1">
                <div className="flex justify-between">
                  <span>IDEMPOTENCY KEY:</span>
                  <span className="text-[#1b1c19] truncate max-w-[140px]">{idempotencyKeyRef.current}</span>
                </div>
                <div className="flex justify-between">
                  <span>TRANSACTION PROTOCOL:</span>
                  <span className="text-[#576244] font-semibold">WAL SERIALIZABLE</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
