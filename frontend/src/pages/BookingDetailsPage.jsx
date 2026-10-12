import React, { useState } from 'react';
import { useParams, useLocation, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api-client';
import {
  CheckCircle2,
  XCircle,
  Calendar,
  Ticket,
  ArrowRight,
  ArrowLeft,
  BookmarkCheck,
  ShieldCheck,
  AlertCircle,
  CreditCard,
  RotateCcw,
  RefreshCw,
  Clock,
  Building2
} from 'lucide-react';

export const BookingDetailsPage = () => {
  const { bookingId } = useParams();
  const location = useLocation();
  const queryClient = useQueryClient();
  const id = Number(bookingId);

  const initialBooking = location.state?.booking;

  // Simulation controls
  const [simulateSuccess, setSimulateSuccess] = useState(true);
  const [lastPaymentResult, setLastPaymentResult] = useState(null);
  const [paymentError, setPaymentError] = useState(null);

  // 1. Fetch authoritative booking record
  const {
    data: booking,
    isLoading: isLoadingBooking,
    error: bookingError,
    refetch: refetchBooking,
  } = useQuery({
    queryKey: ['booking', id],
    queryFn: () => api.getBooking(id),
    initialData: initialBooking && initialBooking.id === id ? initialBooking : undefined,
    enabled: !isNaN(id),
  });

  // 2. Fetch payment history for this booking
  const {
    data: payments,
    isLoading: isLoadingPayments,
    error: paymentsError,
    refetch: refetchPayments,
  } = useQuery({
    queryKey: ['booking-payments', id],
    queryFn: () => api.getBookingPayments(id),
    enabled: !isNaN(id),
  });

  // 3. Simulated payment mutation
  const paymentMutation = useMutation({
    mutationFn: (payload) => api.payForBooking(id, payload),
    onSuccess: (data) => {
      setLastPaymentResult(data);
      setPaymentError(null);
      queryClient.invalidateQueries({ queryKey: ['booking', id] });
      queryClient.invalidateQueries({ queryKey: ['booking-payments', id] });
      queryClient.invalidateQueries({ queryKey: ['my-bookings'] });
    },
    onError: (err) => {
      setPaymentError(err.message || 'Payment simulation failed.');
    },
  });

  // 4. Booking cancellation mutation
  const cancelMutation = useMutation({
    mutationFn: () => api.cancelBooking(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['booking', id] });
      queryClient.invalidateQueries({ queryKey: ['my-bookings'] });
    },
  });

  const handleProcessPayment = () => {
    paymentMutation.mutate({ simulateSuccess });
  };

  const handleCancelBooking = () => {
    if (window.confirm('Are you sure you wish to cancel this reservation and release the allocated seats?')) {
      cancelMutation.mutate();
    }
  };

  if (isNaN(id)) {
    return (
      <div className="max-w-md mx-auto my-16 bg-[#efeee8] border border-[#e4e2dd] p-8 rounded-sm text-center space-y-4">
        <AlertCircle className="w-10 h-10 text-[#ba1a1a] mx-auto" />
        <h2 className="text-xl font-headline font-normal text-[#1b1c19]">Invalid Booking Identifier</h2>
        <Link
          to="/bookings"
          className="inline-flex items-center gap-2 px-4 py-2 bg-[#d1402a] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Back to Bookings</span>
        </Link>
      </div>
    );
  }

  const hasSuccessfulPayment = payments?.some((p) => p.status === 'SUCCESS');
  const isCancelled = booking?.status === 'CANCELLED';

  return (
    <div className="space-y-8 max-w-[1280px] mx-auto pb-16">
      {/* Header Breadcrumb */}
      <div className="flex items-center justify-between border-b border-[#e4e2dd] pb-4">
        <Link
          to="/bookings"
          className="inline-flex items-center gap-2 text-xs font-mono uppercase text-[#5a413c] hover:text-[#1b1c19] transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Return to Reservation History</span>
        </Link>
        <span className="font-mono text-[10px] text-[#5a413c] uppercase">
          LEDGER ENTRY #{id}
        </span>
      </div>

      {/* Loading Skeleton */}
      {isLoadingBooking && (
        <div className="bg-[#efeee8] border border-[#e4e2dd] p-8 rounded-sm space-y-4 animate-pulse">
          <div className="h-4 w-32 bg-[#e4e2dd] rounded" />
          <div className="h-8 w-1/2 bg-[#e4e2dd] rounded" />
          <div className="h-4 w-1/3 bg-[#e4e2dd] rounded" />
        </div>
      )}

      {/* Error State */}
      {bookingError && (
        <div className="bg-[#ffdad6]/40 border border-[#ba1a1a]/30 p-6 rounded-sm text-[#ba1a1a] flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <AlertCircle className="w-5 h-5 flex-shrink-0 text-[#ba1a1a]" />
            <div>
              <h2 className="font-semibold text-sm">Booking record inaccessible</h2>
              <p className="text-xs text-[#5a413c] mt-0.5">
                {bookingError.message || 'You may not have ownership permissions to view this booking.'}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => refetchBooking()}
            className="px-4 py-2 bg-[#d1402a] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold"
          >
            Retry Request
          </button>
        </div>
      )}

      {/* Main Booking Dossier */}
      {!isLoadingBooking && !bookingError && booking && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Left Column (7 cols): Ticket Voucher Dossier */}
          <div className="lg:col-span-7 flex flex-col gap-6">
            <div className="bg-[#ffffff] border border-[#1b1c19] rounded-sm shadow-[4px_4px_0px_0px_#1b1c19] overflow-hidden">
              {/* Header */}
              <div className="p-6 bg-[#fbf9f4] border-b border-[#e4e2dd] space-y-4">
                <div className="flex items-center justify-between flex-wrap gap-2">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[10px] uppercase px-1.5 py-0.5 bg-[#efeee8] border border-[#e4e2dd] rounded-sm text-[#5a413c]">
                      OFFICIAL DISPATCH VOUCHER
                    </span>
                    <span className="font-mono text-xs font-bold text-[#1b1c19] px-2 py-0.5 bg-[#efeee8] border border-[#d1c9b8] rounded-sm">
                      BOOKING #{booking.id}
                    </span>
                  </div>
                  <span
                    className={`font-mono text-[10px] uppercase px-2.5 py-0.5 rounded-sm font-bold ${
                      booking.status === 'CONFIRMED'
                        ? 'bg-[#dbe7c1] text-[#404a2e]'
                        : 'bg-[#ffdad6] text-[#93000a]'
                    }`}
                  >
                    STATUS: {booking.status}
                  </span>
                </div>

                {/* Event Name - Displayed Prominently */}
                <div>
                  <h1 className="font-headline text-3xl sm:text-4xl text-[#1b1c19] font-normal tracking-tight">
                    {booking.eventName || `Reservation #${booking.id}`}
                  </h1>
                </div>

                {/* Showtime and Venue Metadata */}
                {(booking.showtimeStartsAt || booking.venueName) && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-3 border-t border-[#e4e2dd] text-xs font-mono text-[#5a413c]">
                    {booking.showtimeStartsAt && (
                      <div className="flex items-center gap-2">
                        <Calendar className="w-4 h-4 text-[#8e706b] flex-shrink-0" />
                        <div>
                          <span className="text-[10px] uppercase text-[#8e706b] block">Showtime Schedule</span>
                          <span className="text-[#1b1c19] font-semibold">
                            {new Date(booking.showtimeStartsAt).toLocaleString(undefined, {
                              weekday: 'short',
                              year: 'numeric',
                              month: 'short',
                              day: 'numeric',
                              hour: '2-digit',
                              minute: '2-digit',
                            })}
                          </span>
                        </div>
                      </div>
                    )}
                    {booking.venueName && (
                      <div className="flex items-center gap-2">
                        <Building2 className="w-4 h-4 text-[#8e706b] flex-shrink-0" />
                        <div>
                          <span className="text-[10px] uppercase text-[#8e706b] block">Venue &amp; Location</span>
                          <span className="text-[#1b1c19] font-semibold">
                            {booking.venueName}{booking.venueCity ? ` • ${booking.venueCity}` : ''}
                          </span>
                        </div>
                      </div>
                    )}
                  </div>
                )}

                <p className="text-[11px] font-mono text-[#5a413c] pt-1">
                  SETTLED: {new Date(booking.createdAt).toLocaleString()} // IDEMPOTENCY KEY VERIFIED
                </p>
              </div>

              {/* Perforated Notch */}
              <div className="relative w-full h-5 bg-[#efeee8] border-y border-[#e4e2dd] flex items-center justify-between px-0 overflow-hidden">
                <div className="w-3 h-6 bg-[#ffffff] rounded-r-full -ml-1 border-r border-[#e4e2dd]"></div>
                <div className="flex-1 flex justify-center gap-1.5 overflow-hidden">
                  {[...Array(16)].map((_, i) => (
                    <span key={i} className="w-2 h-0.5 bg-[#8e706b]/40"></span>
                  ))}
                </div>
                <div className="w-3 h-6 bg-[#ffffff] rounded-l-full -mr-1 border-l border-[#e4e2dd]"></div>
              </div>

              {/* Body: Allocated Seats */}
              <div className="p-6 bg-[#efeee8] space-y-4">
                <span className="font-mono text-[10px] uppercase text-[#5a413c] block">
                  Allocated Seats Vector
                </span>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {booking.seats && booking.seats.length > 0 ? (
                    booking.seats.map((seat) => {
                      const displayLabel = (seat.rowLabel && seat.seatNumber)
                        ? `${seat.rowLabel}${seat.seatNumber}`
                        : (seat.seatLabel || (seat.seatId ? `Seat #${seat.seatId}` : 'Allocated Seat'));

                      return (
                        <div
                          key={seat.seatId || displayLabel}
                          className="p-3 bg-[#ffffff] border border-[#e4e2dd] rounded-sm shadow-sm flex items-center justify-between"
                        >
                          <div className="flex items-center gap-2.5">
                            <span className="w-2.5 h-2.5 rounded-full bg-[#576244]"></span>
                            <div>
                              <span className="font-mono text-sm font-bold text-[#1b1c19] block tracking-wide">
                                {displayLabel}
                              </span>
                              <span className="font-mono text-[10px] text-[#5a413c] block">
                                {seat.rowLabel ? `ROW ${seat.rowLabel} • SEAT ${seat.seatNumber}` : (seat.category || 'Standard Category')}
                              </span>
                              {seat.category && seat.rowLabel && (
                                <span className="font-mono text-[9px] uppercase px-1.5 py-0.5 bg-[#efeee8] text-[#5a413c] rounded-xs inline-block mt-0.5">
                                  {seat.category}
                                </span>
                              )}
                            </div>
                          </div>
                          <span className="font-mono text-xs font-bold text-[#1b1c19]">
                            {seat.price !== undefined && seat.price !== null
                              ? `$${Number(seat.price).toFixed(2)}`
                              : 'CONFIRMED'}
                          </span>
                        </div>
                      );
                    })
                  ) : booking.bookingSeats && booking.bookingSeats.length > 0 ? (
                    booking.bookingSeats.map((bs) => {
                      const seatObj = bs.seat || bs;
                      const displayLabel = (seatObj.rowLabel && seatObj.seatNumber)
                        ? `${seatObj.rowLabel}${seatObj.seatNumber}`
                        : (bs.id ? `Seat #${bs.id}` : 'Allocated Seat');

                      return (
                        <div
                          key={bs.id || seatObj.id || displayLabel}
                          className="p-3 bg-[#ffffff] border border-[#e4e2dd] rounded-sm shadow-sm flex items-center justify-between"
                        >
                          <div className="flex items-center gap-2.5">
                            <span className="w-2.5 h-2.5 rounded-full bg-[#576244]"></span>
                            <div>
                              <span className="font-mono text-sm font-bold text-[#1b1c19] block tracking-wide">
                                {displayLabel}
                              </span>
                              <span className="font-mono text-[10px] text-[#5a413c] block">
                                {seatObj.rowLabel ? `ROW ${seatObj.rowLabel} • SEAT ${seatObj.seatNumber}` : (seatObj.category || 'Standard Category')}
                              </span>
                              {seatObj.category && seatObj.rowLabel && (
                                <span className="font-mono text-[9px] uppercase px-1.5 py-0.5 bg-[#efeee8] text-[#5a413c] rounded-xs inline-block mt-0.5">
                                  {seatObj.category}
                                </span>
                              )}
                            </div>
                          </div>
                          <span className="font-mono text-xs font-bold text-[#1b1c19]">
                            {seatObj.price !== undefined && seatObj.price !== null
                              ? `$${Number(seatObj.price).toFixed(2)}`
                              : 'CONFIRMED'}
                          </span>
                        </div>
                      );
                    })
                  ) : booking.seatIds && booking.seatIds.length > 0 ? (
                    booking.seatIds.map((seatId) => (
                      <div
                        key={seatId}
                        className="p-3 bg-[#ffffff] border border-[#e4e2dd] rounded-sm shadow-sm flex items-center justify-between"
                      >
                        <div className="flex items-center gap-2.5">
                          <span className="w-2.5 h-2.5 rounded-full bg-[#576244]"></span>
                          <div>
                            <span className="font-mono text-xs font-semibold text-[#1b1c19] block">
                              Seat #{seatId}
                            </span>
                            <span className="font-mono text-[10px] text-[#5a413c]">
                              Allocated Seat
                            </span>
                          </div>
                        </div>
                        <span className="font-mono text-xs font-bold text-[#576244]">
                          CONFIRMED
                        </span>
                      </div>
                    ))
                  ) : (
                    <div className="p-3 bg-[#ffffff] border border-[#e4e2dd] rounded-sm text-xs font-mono text-[#5a413c]">
                      Seat allocation verified in ledger
                    </div>
                  )}
                </div>

                <div className="pt-4 border-t border-[#e4e2dd] flex justify-between items-center text-xs">
                  <span className="font-mono text-[#5a413c] uppercase">TOTAL ORDER COMMITMENT</span>
                  <span className="font-headline text-2xl text-[#d1402a] font-normal">
                    ${Number(booking.totalAmount || 0).toFixed(2)}
                  </span>
                </div>
              </div>
            </div>

            {/* Cancel Action if Confirmed */}
            {booking.status === 'CONFIRMED' && (
              <div className="bg-[#efeee8] border border-[#e4e2dd] p-4 rounded-sm flex items-center justify-between">
                <div>
                  <span className="font-mono text-xs text-[#1b1c19] font-bold block">
                    Cancellation &amp; Seat Release
                  </span>
                  <span className="text-[11px] text-[#5a413c]">
                    Releases seating vector back to the public booking pool.
                  </span>
                </div>
                <button
                  type="button"
                  disabled={cancelMutation.isPending}
                  onClick={handleCancelBooking}
                  className="px-3.5 py-2 bg-[#efeee8] hover:bg-[#ffdad6] text-[#ba1a1a] border border-[#ba1a1a]/40 rounded-sm font-mono text-xs uppercase font-semibold transition-colors"
                >
                  {cancelMutation.isPending ? 'Cancelling...' : 'Cancel Reservation'}
                </button>
              </div>
            )}
          </div>

          {/* Right Column (5 cols): Payment Simulation Console */}
          <div className="lg:col-span-5 flex flex-col gap-6">
            <div className="bg-[#efeee8] border border-[#e4e2dd] rounded-sm p-6 shadow-sm space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-[#e4e2dd]">
                <div className="flex items-center gap-2">
                  <CreditCard className="w-4 h-4 text-[#576244]" />
                  <span className="font-headline text-xl text-[#1b1c19]">
                    Simulated Payment Terminal
                  </span>
                </div>
                <span className="font-mono text-[10px] uppercase text-[#5a413c]">
                  SANDBOX PROTOCOL
                </span>
              </div>

              <p className="text-xs text-[#5a413c] leading-relaxed">
                Test the transaction settlement flow. This calls the backend simulated payment service to record payment records and verify state transitions.
              </p>

              {/* Simulation Mode Toggle */}
              <div className="bg-[#ffffff] border border-[#d1c9b8] p-3 rounded-sm space-y-2">
                <span className="font-mono text-[10px] uppercase text-[#5a413c] block">
                  Simulation Outcome Toggle
                </span>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setSimulateSuccess(true)}
                    className={`py-2 px-3 rounded-sm font-mono text-xs uppercase font-semibold transition-colors ${
                      simulateSuccess
                        ? 'bg-[#dbe7c1] text-[#404a2e] border border-[#576244]'
                        : 'bg-[#efeee8] text-[#5a413c]'
                    }`}
                  >
                    Simulate Success
                  </button>
                  <button
                    type="button"
                    onClick={() => setSimulateSuccess(false)}
                    className={`py-2 px-3 rounded-sm font-mono text-xs uppercase font-semibold transition-colors ${
                      !simulateSuccess
                        ? 'bg-[#ffdad6] text-[#93000a] border border-[#ba1a1a]'
                        : 'bg-[#efeee8] text-[#5a413c]'
                    }`}
                  >
                    Simulate Failure
                  </button>
                </div>
              </div>

              {/* Payment Feedback */}
              {lastPaymentResult && (
                <div
                  className={`p-3 rounded-sm font-mono text-xs border ${
                    lastPaymentResult.status === 'SUCCESS'
                      ? 'bg-[#dbe7c1]/50 border-[#576244] text-[#404a2e]'
                      : 'bg-[#ffdad6]/60 border-[#ba1a1a] text-[#93000a]'
                  }`}
                >
                  <div className="font-bold">
                    Payment Result: {lastPaymentResult.status}
                  </div>
                  <div className="text-[11px] mt-0.5">
                    Tx ID: #{lastPaymentResult.id} • Processed at {new Date(lastPaymentResult.createdAt).toLocaleTimeString()}
                  </div>
                </div>
              )}

              {paymentError && (
                <div className="p-3 bg-[#ffdad6]/60 border border-[#ba1a1a] rounded-sm font-mono text-xs text-[#93000a]">
                  {paymentError}
                </div>
              )}

              {/* Execute Payment Button */}
              <button
                type="button"
                disabled={paymentMutation.isPending || isCancelled}
                onClick={handleProcessPayment}
                className="w-full bg-[#d1402a] hover:bg-[#30312d] disabled:opacity-50 text-[#fffafa] py-3 rounded-sm font-mono text-xs uppercase font-bold tracking-wider transition-all shadow-[2px_2px_0px_0px_#1b1c19] active:translate-x-[1px] active:translate-y-[1px] flex items-center justify-center gap-2"
              >
                {paymentMutation.isPending ? (
                  <span>Processing Payment Simulation...</span>
                ) : (
                  <span>Submit Simulated Payment (${Number(booking.totalAmount || 0).toFixed(2)})</span>
                )}
              </button>

              {/* Payment Records Ledger */}
              <div className="pt-4 border-t border-[#e4e2dd] space-y-2">
                <span className="font-mono text-[10px] uppercase text-[#5a413c] block">
                  Payment History on Ledger ({payments?.length || 0} Transactions)
                </span>

                {isLoadingPayments && (
                  <p className="text-xs font-mono text-[#5a413c]">Loading transactions...</p>
                )}

                {payments && payments.length === 0 && (
                  <p className="text-xs text-[#5a413c] italic">
                    No payment attempts have been recorded for this booking yet.
                  </p>
                )}

                {payments && payments.length > 0 && (
                  <div className="space-y-2">
                    {payments.map((p) => (
                      <div
                        key={p.id}
                        className="bg-[#ffffff] border border-[#e4e2dd] p-2.5 rounded-sm flex items-center justify-between text-xs font-mono"
                      >
                        <div className="flex items-center gap-2">
                          <span
                            className={`w-2 h-2 rounded-full ${
                              p.status === 'SUCCESS' ? 'bg-[#576244]' : 'bg-[#ae2714]'
                            }`}
                          ></span>
                          <span>TX #{p.id}</span>
                          <span className="text-[#5a413c]">({p.status})</span>
                        </div>
                        <span className="font-bold text-[#1b1c19]">
                          ${Number(p.amount || 0).toFixed(2)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
