import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api-client';
import {
  ArrowRight,
  Terminal,
  Lock,
  CheckCircle2,
  Calendar,
  Zap,
  ShieldCheck,
  AlertCircle,
  RefreshCw,
  Check,
  Sparkles,
} from 'lucide-react';

const CURATED_PERFORMANCE_IMAGES = [
  'https://lh3.googleusercontent.com/aida-public/AB6AXuDi4cfzSic1zZtgxylquH0O3UjFCu3-jjMNp_UiH54JQ2r0OgGKYaSyn7Tz95Ohzp9mRTk25hRlqssNfHso-fpQUQmdti9TrOrWU_OaGfcRejnJsfQt7uvncv5QamBraLxWcJJ30D-LEQpD7QS88PjOqIG6XY4yRyrUnKbp_bnSfzOp19YA1z601EZ9IXCNLtEUMB_rXYiSsWwnU1J_tKhd4JhRbdPKbcNhqQj7LC5nXwPlIKWUvbpvOQ',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuCdH5fa1PjBPgRfRsLZiG3lcwGY9jlVjbvWx9bdoj4a6J03-HNUnVTUSFTJz68KsEpAPcsmlIBKuPA12gUG3gcmQHWHufjPUlj7p6kDr57sjj0yNvklBeu5593pR1gtSBzVfa1klaqocj2fSrgs0mqI1BtB7au_-YASJonqZX9VHqKmcml1SyHwPAw29UULq1iSahg4AVNAy5vnT0b--bDjuuCCRXWbJ97vWQBfzXunaXL_a1lHaz8ZgQ',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuCiEaefcEBBhPFmaZw6yrUXf2HwABvcxXV8jlUjTy4Ro95oq0Nu1O6u-BptnhVZ4DPMmGu3b9Y8u-B6LGiJSvVhU7hpfUn1RLuhzkniGoi1FKLv2osAWuc02Wv4qZm77EEPmc4oMyDwYu4sB3OLZxOxW6FsfrTe1gGq130SH73biC3A9b4n0WSeDvASYp2JJMB574KcFK7Sjc3L2cUOSLtC99dENzuVCLMNYp61izKG0tCTsOEJQeYrfA',
];

const getPerformanceImage = (event) => {
  if (event?.imageUrl) return event.imageUrl;
  if (event?.image) return event.image;
  const numId = Number(event?.id);
  const imageIndex = !isNaN(numId) && numId > 0
    ? (numId - 1) % CURATED_PERFORMANCE_IMAGES.length
    : 0;
  return CURATED_PERFORMANCE_IMAGES[imageIndex];
};

const FILTER_DISCIPLINES = [
  'ALL DISCIPLINES',
  'ORCHESTRAL',
  'SYNTHESIS & STRINGS',
  'PIANO ACOUSTICS',
];

export const LandingPage = () => {
  // 1. Real Event Data from API
  const {
    data: events,
    isLoading: isLoadingEvents,
    error: eventsError,
    refetch: refetchEvents,
    isFetching: isFetchingEvents,
  } = useQuery({
    queryKey: ['events'],
    queryFn: api.getEvents,
  });

  // 2. Discipline filter state
  const [selectedDiscipline, setSelectedDiscipline] = useState('ALL DISCIPLINES');

  // 3. Interactive Seat Matrix Sandbox State
  const [seatStates, setSeatStates] = useState({
    A1: 'available',
    A2: 'available',
    A3: 'held',
    A4: 'held',
    A5: 'available',
    A6: 'available',
    A7: 'reserved',
    A8: 'reserved',
    B1: 'available',
    B2: 'available',
    B3: 'available',
    B4: 'available',
    B5: 'selected',
    B6: 'selected',
    B7: 'available',
    B8: 'available',
    C1: 'reserved',
    C2: 'reserved',
    C3: 'available',
    C4: 'available',
    C5: 'available',
    C6: 'available',
    C7: 'available',
    C8: 'available',
  });
  const [matrixStatus, setMatrixStatus] = useState('CLICK A SEAT NODE TO INITIATE LOCK');

  const handleSeatClick = (seatId) => {
    const currentState = seatStates[seatId];
    if (currentState === 'reserved') return;

    if (currentState === 'selected') {
      setSeatStates((prev) => ({ ...prev, [seatId]: 'available' }));
      setMatrixStatus(`RELEASED SEAT [${seatId}] // LOCK REVOKED`);
    } else if (currentState === 'available' || currentState === 'held') {
      setSeatStates((prev) => ({ ...prev, [seatId]: 'selected' }));
      setMatrixStatus(`ACQUIRED REDIS LOCK [${seatId}] // 600S TTL ACTIVE`);
    }
  };

  // 4. Hero Countdown Ticker Simulation
  const [countdownSeconds, setCountdownSeconds] = useState(582);
  useEffect(() => {
    const timer = setInterval(() => {
      setCountdownSeconds((prev) => (prev > 0 ? prev - 1 : 600));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const formatCountdown = (totalSec) => {
    const m = String(Math.floor(totalSec / 60)).padStart(2, '0');
    const s = String(totalSec % 60).padStart(2, '0');
    return `${m}:${s}`;
  };

  // 5. Programming Dispatch Form Submission
  const [dispatchEmail, setDispatchEmail] = useState('');
  const [dispatchSubmitted, setDispatchSubmitted] = useState(false);

  const handleDispatchSubmit = (e) => {
    e.preventDefault();
    if (dispatchEmail.trim()) {
      setDispatchSubmitted(true);
    }
  };

  return (
    <div className="w-full bg-[#fbf9f4] text-[#1b1c19] font-sans antialiased selection:bg-[#d1402a] selection:text-[#fffafa]">
      {/* Top Archival Strip */}
      <div className="w-full bg-[#efeee8] py-2 px-4 sm:px-8 border-b border-[#e4e2dd] text-[#5a413c] font-mono text-xs tracking-wider">
        <div className="max-w-[1280px] mx-auto flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-3">
            <span className="inline-flex items-center gap-1.5 font-semibold text-[#1b1c19]">
              <span className="w-2 h-2 rounded-full bg-[#576244]"></span>
              CORE ENGINE ACTIVE
            </span>
            <span>•</span>
            <span>LATENCY: 14MS P99 [Illustrative Target]</span>
            <span className="hidden sm:inline">•</span>
            <span className="hidden sm:inline">ZERO CONCURRENCY COLLISIONS LOGGED</span>
          </div>
          <div className="hidden md:flex items-center gap-2">
            <span>DISTRIBUTED TICKETING PROTOCOL v2.4</span>
            <span className="px-1.5 py-0.5 bg-[#fbf9f4] text-[#1b1c19] font-semibold rounded-sm">
              STANDARDS COMPLIANT
            </span>
          </div>
        </div>
      </div>

      <div className="max-w-[1280px] mx-auto px-4 sm:px-8 py-8 sm:py-12">
        {/* Section 1: Hero Section */}
        <section className="w-full pt-4 pb-12 sm:pb-16 border-b border-[#e4e2dd]">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-12 items-start">
            {/* Left Editorial Dossier (7 Cols) */}
            <div className="lg:col-span-7 flex flex-col gap-6">
              <div className="flex flex-col gap-2">
                <div className="inline-flex items-center">
                  <span className="font-mono text-[11px] uppercase text-[#5a413c] tracking-widest px-2.5 py-1 bg-[#efeee8] border border-[#e4e2dd] rounded-sm">
                    DISTRIBUTED TICKET ALLOCATION // POSTGRESQL &amp; REDIS ENGINE
                  </span>
                </div>
                <h1 className="font-headline text-4xl sm:text-5xl lg:text-6xl text-[#1b1c19] mt-3 leading-[1.15] tracking-tight font-normal">
                  Where Every Seat is Guaranteed.<br />
                  <span className="italic font-normal text-[#ae2714]">Never Double-Booked.</span>
                </h1>
              </div>

              <p className="text-base sm:text-lg text-[#5a413c] max-w-xl leading-relaxed">
                Engineered for high-volume ticket drops. Experience world-class classical, avant-garde, and contemporary performances with deterministic zero-collision seat reservation.
              </p>

              {/* CTA Cluster */}
              <div className="flex flex-wrap items-center gap-4 pt-2">
                <Link
                  to="/events"
                  className="inline-flex items-center gap-2 bg-[#d1402a] text-[#fffafa] hover:bg-[#30312d] hover:text-[#f2f1eb] px-6 py-3 rounded-sm font-mono text-xs uppercase tracking-wider font-semibold transition-all shadow-[2px_2px_0px_0px_#1b1c19]"
                >
                  <span>Browse Events</span>
                  <ArrowRight className="w-4 h-4" />
                </Link>
                <a
                  href="#how-it-works"
                  className="inline-flex items-center gap-2 bg-[#f5f4ee] text-[#1b1c19] hover:bg-[#e9e8e3] border border-[#e4e2dd] px-6 py-3 rounded-sm font-mono text-xs uppercase tracking-wider font-semibold transition-colors"
                >
                  <span>How It Works</span>
                  <Terminal className="w-4 h-4 text-[#5a413c]" />
                </a>
                <Link
                  to="/lab"
                  className="inline-flex items-center gap-1.5 text-xs font-mono uppercase text-[#5a413c] hover:text-[#ae2714] transition-colors ml-1"
                >
                  <Zap className="w-3.5 h-3.5 text-[#775600]" />
                  <span>Lab Sandbox</span>
                </Link>
              </div>

              {/* Metric Anchors */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-4 mt-2 bg-[#f5f4ee] border border-[#e4e2dd] p-4 sm:p-5 rounded-sm">
                <div className="flex flex-col gap-1 border-b sm:border-b-0 sm:border-r border-[#e4e2dd] pb-3 sm:pb-0 sm:pr-3">
                  <span className="font-mono text-[10px] text-[#5a413c] uppercase tracking-wider">
                    Lock Guarantee
                  </span>
                  <span className="font-mono text-sm sm:text-base font-semibold text-[#1b1c19]">
                    600 SEC EXCLUSIVE
                  </span>
                  <span className="text-xs text-[#5a413c]">
                    Redis Redlock consensus
                  </span>
                </div>
                <div className="flex flex-col gap-1 border-b sm:border-b-0 sm:border-r border-[#e4e2dd] pb-3 sm:pb-0 sm:pr-3">
                  <span className="font-mono text-[10px] text-[#5a413c] uppercase tracking-wider">
                    Throughput [Target]
                  </span>
                  <span className="font-mono text-sm sm:text-base font-semibold text-[#1b1c19]">
                    42,000 REQ/SEC
                  </span>
                  <span className="text-xs text-[#5a413c]">
                    Burst seat assignment
                  </span>
                </div>
                <div className="flex flex-col gap-1">
                  <span className="font-mono text-[10px] text-[#5a413c] uppercase tracking-wider">
                    Integrity
                  </span>
                  <span className="font-mono text-sm sm:text-base font-semibold text-[#ae2714]">
                    0.000% OVERSELL
                  </span>
                  <span className="text-xs text-[#5a413c]">
                    PostgreSQL WAL verification
                  </span>
                </div>
              </div>
            </div>

            {/* Right Physical Woodblock Ticket Card (5 Cols) */}
            <div className="lg:col-span-5 flex flex-col">
              <div className="bg-[#ffffff] border border-[#1b1c19] rounded-sm shadow-[4px_4px_0px_0px_#1b1c19] overflow-hidden flex flex-col">
                {/* Ticket Header Band */}
                <div className="p-3 sm:p-4 bg-[#30312d] text-[#f2f1eb] flex items-center justify-between font-mono text-xs">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 bg-[#ae2714] rounded-full animate-pulse"></span>
                    <span className="uppercase font-semibold tracking-wider text-[11px]">
                      LIVE DROP SIMULATION [Illustrative]
                    </span>
                  </div>
                  <span className="uppercase px-2 py-0.5 bg-[#d1402a] text-[#fffafa] rounded-sm font-bold text-[10px]">
                    RESERVED HOLD: 10 MIN
                  </span>
                </div>

                {/* Ticket Image Frame */}
                <div className="relative w-full h-52 sm:h-56 bg-[#efeee8] overflow-hidden border-b border-[#e4e2dd]">
                  <img
                    className="w-full h-full object-cover grayscale contrast-125"
                    alt="Acoustic orchestra hall woodblock illustrative ticket visual"
                    src="https://lh3.googleusercontent.com/aida-public/AB6AXuBYG2_r3hQq2yotbIrm3Mi0KLDNKlheNaMy-CQNkxbYbHtOWiYlGrltyIZOxaaXoZbecQYqKD1VXpwUgC6xTYFuOu3sw-YTPOJRisRHh3bLb3nL2G5ghheJLWIJFFjdimSB4NjfPBPVaoJAcvGlnaDOdAkSqB0_hkUNIjEZk17RS-nwyBe8SN2jmXrZ33gmmx-q7Iz4MyG6mhuLCzxO2uFhK2LK8NGrzikePTM34itmSCxtrMl6YLNe5g"
                  />
                  <div className="absolute inset-0 bg-[#1b1c19]/15 mix-blend-multiply"></div>
                  <div className="absolute bottom-2.5 left-2.5 px-2.5 py-1 bg-[#fbf9f4]/95 text-[#1b1c19] font-mono text-[10px] uppercase font-semibold rounded-sm border border-[#e4e2dd]">
                    ELBPHILHARMONIE // GROSSER SAAL [Illustrative Demo]
                  </div>
                </div>

                {/* Performance Content */}
                <div className="p-5 sm:p-6 flex flex-col gap-3 bg-[#fbf9f4]">
                  <div className="flex justify-between items-baseline font-mono text-xs text-[#5a413c]">
                    <span>SERIES XII • SYMPHONIC DROP</span>
                    <span>TOKEN: #ELB-9482-TX</span>
                  </div>
                  <h3 className="font-headline text-2xl sm:text-3xl text-[#1b1c19] font-normal">
                    Gustav Mahler: Symphony No. 2 [Demo Performance]
                  </h3>
                  <p className="text-sm text-[#5a413c] leading-relaxed">
                    NDR Elbphilharmonie Orchester conducted by Kirill Petrenko with 120-member festival choir.
                  </p>
                  <div className="grid grid-cols-2 gap-3 pt-2 font-mono text-xs">
                    <div className="p-2.5 bg-[#efeee8] border border-[#e4e2dd] rounded-sm">
                      <span className="text-[#5a413c] block text-[10px]">DATE &amp; TIME</span>
                      <span className="font-bold text-[#1b1c19]">OCT 24 // 20:00 CET</span>
                    </div>
                    <div className="p-2.5 bg-[#efeee8] border border-[#e4e2dd] rounded-sm">
                      <span className="text-[#5a413c] block text-[10px]">SECTION</span>
                      <span className="font-bold text-[#1b1c19]">PARKETT MITTE • ROW G</span>
                    </div>
                  </div>
                </div>

                {/* Perforated Notched Separator */}
                <div className="relative bg-[#e9e8e3] py-2 flex items-center justify-between px-4 border-y border-[#e4e2dd]">
                  <div className="w-4 h-8 bg-[#fbf9f4] rounded-r-full -ml-4 border-r border-[#e4e2dd]"></div>
                  <div className="flex-1 flex justify-center gap-1.5 overflow-hidden">
                    {[...Array(16)].map((_, i) => (
                      <span key={i} className="w-2 h-0.5 bg-[#8e706b]/40"></span>
                    ))}
                  </div>
                  <div className="w-4 h-8 bg-[#fbf9f4] rounded-l-full -mr-4 border-l border-[#e4e2dd]"></div>
                </div>

                {/* Ticket Footer / Live Status */}
                <div className="p-4 sm:p-5 bg-[#efeee8] flex items-center justify-between font-mono text-xs">
                  <div className="flex items-center gap-2 text-[#1b1c19] font-semibold">
                    <Lock className="w-4 h-4 text-[#576244]" />
                    <span>SEATS HELD: {formatCountdown(countdownSeconds)} REMAINING</span>
                  </div>
                  <div className="text-right">
                    <span className="font-headline text-xl text-[#1b1c19] font-semibold block leading-none">
                      $145.00
                    </span>
                    <span className="text-[9px] text-[#5a413c] uppercase block font-mono">
                      [Sample Tier]
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Section 2: Core Reliability Architecture */}
        <section className="w-full py-12 sm:py-16 border-b border-[#e4e2dd]" id="how-it-works">
          <div className="flex flex-col gap-2 mb-8">
            <div className="flex items-center justify-between flex-wrap gap-2">
              <span className="font-mono text-xs uppercase text-[#5a413c] tracking-wider">
                SYSTEM ARCHITECTURE &amp; GUARANTEES
              </span>
              <span className="font-mono text-xs text-[#576244] font-semibold uppercase">
                ACID COMPLIANT PIPELINE
              </span>
            </div>
            <h2 className="font-headline text-3xl sm:text-4xl text-[#1b1c19] font-normal">
              Engineered for Zero Failure at Scale
            </h2>
            <p className="text-sm sm:text-base text-[#5a413c] max-w-2xl leading-relaxed">
              Ticketing collapses during high demand because traditional databases lock entire tables. SeatFlow decouples transient contention locks from permanent ledger writes.
            </p>
          </div>

          {/* 3 Disciplined Paper-Surfaced Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Card 1 */}
            <div className="bg-[#efeee8] border border-[#e4e2dd] p-6 rounded-sm flex flex-col justify-between hover:bg-[#e9e8e3] transition-colors">
              <div className="flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[10px] uppercase text-[#ae2714] font-bold px-2 py-0.5 bg-[#fbf9f4] border border-[#e4e2dd] rounded-sm">
                    01 // PROTOCOL
                  </span>
                  <RefreshCw className="w-4 h-4 text-[#5a413c]" />
                </div>
                <h3 className="font-headline text-2xl text-[#1b1c19] font-normal">
                  Real-Time Availability
                </h3>
                <p className="text-sm text-[#5a413c] leading-relaxed">
                  Persistent Server-Sent Events (SSE) stream venue state transitions to all connected clients under 18ms. No destructive polling, no stale browser caches.
                </p>
              </div>
              <div className="mt-6 pt-3 bg-[#fbf9f4] border border-[#e4e2dd] p-3 rounded-sm font-mono text-xs text-[#5a413c] flex items-center justify-between">
                <span>TRANSPORT</span>
                <span className="font-semibold text-[#1b1c19]">SSE HTTP/2 STREAMS</span>
              </div>
            </div>

            {/* Card 2 */}
            <div className="bg-[#efeee8] border border-[#e4e2dd] p-6 rounded-sm flex flex-col justify-between hover:bg-[#e9e8e3] transition-colors">
              <div className="flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[10px] uppercase text-[#ae2714] font-bold px-2 py-0.5 bg-[#fbf9f4] border border-[#e4e2dd] rounded-sm">
                    02 // LOCKING
                  </span>
                  <Lock className="w-4 h-4 text-[#5a413c]" />
                </div>
                <h3 className="font-headline text-2xl text-[#1b1c19] font-normal">
                  Atomic Redis Holds
                </h3>
                <p className="text-sm text-[#5a413c] leading-relaxed">
                  Lua scripts orchestrate an instant 10-minute deterministic lock holding selected seats strictly to your customer session UUID before database commitment.
                </p>
              </div>
              <div className="mt-6 pt-3 bg-[#fbf9f4] border border-[#e4e2dd] p-3 rounded-sm font-mono text-xs text-[#5a413c] flex items-center justify-between">
                <span>CONCURRENCY</span>
                <span className="font-semibold text-[#1b1c19]">REDIS CONSENSUS</span>
              </div>
            </div>

            {/* Card 3 */}
            <div className="bg-[#efeee8] border border-[#e4e2dd] p-6 rounded-sm flex flex-col justify-between hover:bg-[#e9e8e3] transition-colors">
              <div className="flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[10px] uppercase text-[#ae2714] font-bold px-2 py-0.5 bg-[#fbf9f4] border border-[#e4e2dd] rounded-sm">
                    03 // SETTLEMENT
                  </span>
                  <ShieldCheck className="w-4 h-4 text-[#5a413c]" />
                </div>
                <h3 className="font-headline text-2xl text-[#1b1c19] font-normal">
                  Idempotent Settlement
                </h3>
                <p className="text-sm text-[#5a413c] leading-relaxed">
                  Transactional integrity enforced via serialized PostgreSQL WAL commits. Even in catastrophic payment dropouts, no seat is ever allocated twice.
                </p>
              </div>
              <div className="mt-6 pt-3 bg-[#fbf9f4] border border-[#e4e2dd] p-3 rounded-sm font-mono text-xs text-[#5a413c] flex items-center justify-between">
                <span>ISOLATION</span>
                <span className="font-semibold text-[#1b1c19]">POSTGRESQL SERIALIZABLE</span>
              </div>
            </div>
          </div>
        </section>

        {/* Section 3: Interactive Tactile Seat Allocation Matrix Preview */}
        <section className="w-full bg-[#f5f4ee] border border-[#e4e2dd] p-6 sm:p-8 rounded-sm my-12">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
            <div className="lg:col-span-5 flex flex-col gap-3">
              <span className="font-mono text-[11px] uppercase text-[#5a413c] tracking-wider">
                TACTILE SEAT ALLOCATION CANVAS [Interactive Demonstration Sandbox — No Live Hold Created]
              </span>
              <h3 className="font-headline text-2xl sm:text-3xl text-[#1b1c19] font-normal">
                Deterministic Contention Testing
              </h3>
              <p className="text-sm text-[#5a413c] leading-relaxed">
                Click any available seat node to simulate client lock acquisition. Observe how individual seating coordinates transition between states without full database locking.
              </p>

              {/* Legend */}
              <div className="flex flex-wrap gap-4 pt-2 font-mono text-xs text-[#1b1c19]">
                <div className="flex items-center gap-2">
                  <span className="w-3.5 h-3.5 rounded-sm bg-[#dbe7c1] border border-[#576244]"></span>
                  <span>Available</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="w-3.5 h-3.5 rounded-sm bg-[#d1402a] text-[#fffafa] flex items-center justify-center text-[9px]"></span>
                  <span>Selected</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="w-3.5 h-3.5 rounded-sm bg-[#f0bf5c] border border-[#775600]"></span>
                  <span>Held (Other)</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="w-3.5 h-3.5 rounded-sm bg-[#e4e2dd] border border-[#8e706b] opacity-60"></span>
                  <span>Reserved</span>
                </div>
              </div>
            </div>

            {/* Seat Grid Canvas Box */}
            <div className="lg:col-span-7 bg-[#ffffff] border border-[#e4e2dd] p-6 sm:p-8 rounded-sm flex flex-col items-center justify-center shadow-sm">
              {/* Stage Curved Indicator */}
              <div className="w-3/4 max-w-sm h-2 bg-[#30312d] rounded-full mb-3"></div>
              <span className="font-mono text-[10px] text-[#5a413c] uppercase tracking-widest mb-6">
                STAGE // PROSCENIUM
              </span>

              {/* Matrix Rows */}
              <div className="flex flex-col gap-2.5 items-center">
                {/* Row A */}
                <div className="flex items-center gap-2">
                  <span className="font-mono text-[10px] text-[#5a413c] w-3 text-right">A</span>
                  <div className="flex gap-1.5">
                    {['A1', 'A2', 'A3', 'A4'].map((seatId) => {
                      const state = seatStates[seatId];
                      const isSelected = state === 'selected';
                      const isHeld = state === 'held';
                      const isReserved = state === 'reserved';
                      return (
                        <button
                          key={seatId}
                          type="button"
                          onClick={() => handleSeatClick(seatId)}
                          disabled={isReserved}
                          title={`Seat ${seatId} (${state})`}
                          className={`w-7 h-7 rounded-sm font-mono text-[10px] font-semibold transition-all ${
                            isSelected
                              ? 'bg-[#d1402a] text-[#fffafa] shadow-sm'
                              : isHeld
                              ? 'bg-[#f0bf5c] text-[#775600]'
                              : isReserved
                              ? 'bg-[#e4e2dd] text-[#8e706b] opacity-50 cursor-not-allowed'
                              : 'bg-[#dbe7c1] text-[#1b1c19] hover:bg-[#d1402a] hover:text-[#fffafa]'
                          }`}
                        >
                          {seatId.slice(1)}
                        </button>
                      );
                    })}
                    <div className="w-3"></div>
                    {['A5', 'A6', 'A7', 'A8'].map((seatId) => {
                      const state = seatStates[seatId];
                      const isSelected = state === 'selected';
                      const isHeld = state === 'held';
                      const isReserved = state === 'reserved';
                      return (
                        <button
                          key={seatId}
                          type="button"
                          onClick={() => handleSeatClick(seatId)}
                          disabled={isReserved}
                          title={`Seat ${seatId} (${state})`}
                          className={`w-7 h-7 rounded-sm font-mono text-[10px] font-semibold transition-all ${
                            isSelected
                              ? 'bg-[#d1402a] text-[#fffafa] shadow-sm'
                              : isHeld
                              ? 'bg-[#f0bf5c] text-[#775600]'
                              : isReserved
                              ? 'bg-[#e4e2dd] text-[#8e706b] opacity-50 cursor-not-allowed'
                              : 'bg-[#dbe7c1] text-[#1b1c19] hover:bg-[#d1402a] hover:text-[#fffafa]'
                          }`}
                        >
                          {seatId.slice(1)}
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Row B */}
                <div className="flex items-center gap-2">
                  <span className="font-mono text-[10px] text-[#5a413c] w-3 text-right">B</span>
                  <div className="flex gap-1.5">
                    {['B1', 'B2', 'B3', 'B4'].map((seatId) => {
                      const state = seatStates[seatId];
                      const isSelected = state === 'selected';
                      const isHeld = state === 'held';
                      const isReserved = state === 'reserved';
                      return (
                        <button
                          key={seatId}
                          type="button"
                          onClick={() => handleSeatClick(seatId)}
                          disabled={isReserved}
                          title={`Seat ${seatId} (${state})`}
                          className={`w-7 h-7 rounded-sm font-mono text-[10px] font-semibold transition-all ${
                            isSelected
                              ? 'bg-[#d1402a] text-[#fffafa] shadow-sm'
                              : isHeld
                              ? 'bg-[#f0bf5c] text-[#775600]'
                              : isReserved
                              ? 'bg-[#e4e2dd] text-[#8e706b] opacity-50 cursor-not-allowed'
                              : 'bg-[#dbe7c1] text-[#1b1c19] hover:bg-[#d1402a] hover:text-[#fffafa]'
                          }`}
                        >
                          {seatId.slice(1)}
                        </button>
                      );
                    })}
                    <div className="w-3"></div>
                    {['B5', 'B6', 'B7', 'B8'].map((seatId) => {
                      const state = seatStates[seatId];
                      const isSelected = state === 'selected';
                      const isHeld = state === 'held';
                      const isReserved = state === 'reserved';
                      return (
                        <button
                          key={seatId}
                          type="button"
                          onClick={() => handleSeatClick(seatId)}
                          disabled={isReserved}
                          title={`Seat ${seatId} (${state})`}
                          className={`w-7 h-7 rounded-sm font-mono text-[10px] font-semibold transition-all ${
                            isSelected
                              ? 'bg-[#d1402a] text-[#fffafa] shadow-sm'
                              : isHeld
                              ? 'bg-[#f0bf5c] text-[#775600]'
                              : isReserved
                              ? 'bg-[#e4e2dd] text-[#8e706b] opacity-50 cursor-not-allowed'
                              : 'bg-[#dbe7c1] text-[#1b1c19] hover:bg-[#d1402a] hover:text-[#fffafa]'
                          }`}
                        >
                          {seatId.slice(1)}
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Row C */}
                <div className="flex items-center gap-2">
                  <span className="font-mono text-[10px] text-[#5a413c] w-3 text-right">C</span>
                  <div className="flex gap-1.5">
                    {['C1', 'C2', 'C3', 'C4'].map((seatId) => {
                      const state = seatStates[seatId];
                      const isSelected = state === 'selected';
                      const isHeld = state === 'held';
                      const isReserved = state === 'reserved';
                      return (
                        <button
                          key={seatId}
                          type="button"
                          onClick={() => handleSeatClick(seatId)}
                          disabled={isReserved}
                          title={`Seat ${seatId} (${state})`}
                          className={`w-7 h-7 rounded-sm font-mono text-[10px] font-semibold transition-all ${
                            isSelected
                              ? 'bg-[#d1402a] text-[#fffafa] shadow-sm'
                              : isHeld
                              ? 'bg-[#f0bf5c] text-[#775600]'
                              : isReserved
                              ? 'bg-[#e4e2dd] text-[#8e706b] opacity-50 cursor-not-allowed'
                              : 'bg-[#dbe7c1] text-[#1b1c19] hover:bg-[#d1402a] hover:text-[#fffafa]'
                          }`}
                        >
                          {seatId.slice(1)}
                        </button>
                      );
                    })}
                    <div className="w-3"></div>
                    {['C5', 'C6', 'C7', 'C8'].map((seatId) => {
                      const state = seatStates[seatId];
                      const isSelected = state === 'selected';
                      const isHeld = state === 'held';
                      const isReserved = state === 'reserved';
                      return (
                        <button
                          key={seatId}
                          type="button"
                          onClick={() => handleSeatClick(seatId)}
                          disabled={isReserved}
                          title={`Seat ${seatId} (${state})`}
                          className={`w-7 h-7 rounded-sm font-mono text-[10px] font-semibold transition-all ${
                            isSelected
                              ? 'bg-[#d1402a] text-[#fffafa] shadow-sm'
                              : isHeld
                              ? 'bg-[#f0bf5c] text-[#775600]'
                              : isReserved
                              ? 'bg-[#e4e2dd] text-[#8e706b] opacity-50 cursor-not-allowed'
                              : 'bg-[#dbe7c1] text-[#1b1c19] hover:bg-[#d1402a] hover:text-[#fffafa]'
                          }`}
                        >
                          {seatId.slice(1)}
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>

              {/* Status Banner */}
              <div className="mt-6 w-full flex flex-col sm:flex-row items-center justify-between px-4 py-2 bg-[#efeee8] border border-[#e4e2dd] rounded-sm font-mono text-xs gap-2">
                <span className="text-[#1b1c19] font-medium text-center sm:text-left">
                  {matrixStatus}
                </span>
                <span className="text-[#5a413c] text-[10px]">
                  SANDBOX SESSION: #SF-DEMO-SIM
                </span>
              </div>
            </div>
          </div>
        </section>

        {/* Section 4: Curated Upcoming Events (Real API Data) */}
        <section className="w-full py-12 sm:py-16 border-b border-[#e4e2dd]">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-8">
            <div className="flex flex-col gap-1">
              <span className="font-mono text-xs uppercase text-[#5a413c] tracking-wider">
                ARCHIVAL REPERTOIRE
              </span>
              <h2 className="font-headline text-3xl sm:text-4xl text-[#1b1c19] font-normal">
                Featured Performances
              </h2>
            </div>

            {/* Clean Filter Chips */}
            <div className="flex flex-wrap gap-1.5 p-1 bg-[#efeee8] border border-[#e4e2dd] rounded-sm">
              {FILTER_DISCIPLINES.map((discipline) => {
                const isActive = selectedDiscipline === discipline;
                return (
                  <button
                    key={discipline}
                    type="button"
                    onClick={() => setSelectedDiscipline(discipline)}
                    className={`px-3 py-1 font-mono text-xs uppercase transition-colors rounded-sm ${
                      isActive
                        ? 'bg-[#ffffff] text-[#1b1c19] font-semibold shadow-sm border border-[#e4e2dd]'
                        : 'text-[#5a413c] hover:text-[#1b1c19]'
                    }`}
                  >
                    {discipline}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Loading Skeletons */}
          {isLoadingEvents && (
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
                    <div className="h-5 w-16 bg-[#e4e2dd] rounded" />
                    <div className="h-8 w-28 bg-[#e4e2dd] rounded" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Error State with Retry Button */}
          {eventsError && (
            <div className="bg-[#ffdad6]/40 border border-[#ba1a1a]/30 p-6 rounded-sm text-[#ba1a1a] flex flex-col sm:flex-row items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <AlertCircle className="w-5 h-5 flex-shrink-0 text-[#ba1a1a]" />
                <div>
                  <h4 className="font-semibold text-sm">Failed to retrieve real event listings</h4>
                  <p className="text-xs text-[#5a413c]">
                    {eventsError.message || 'Ensure the backend server is running on port 8080.'}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => refetchEvents()}
                disabled={isFetchingEvents}
                className="px-4 py-2 bg-[#d1402a] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-semibold transition-colors flex items-center gap-2"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isFetchingEvents ? 'animate-spin' : ''}`} />
                <span>Retry Request</span>
              </button>
            </div>
          )}

          {/* Empty State */}
          {!isLoadingEvents && !eventsError && events && events.length === 0 && (
            <div className="text-center py-16 bg-[#efeee8] border border-[#e4e2dd] rounded-sm text-[#5a413c] space-y-3">
              <Calendar className="w-10 h-10 mx-auto text-[#8e706b]" />
              <h3 className="text-lg font-headline font-normal text-[#1b1c19]">No Events Scheduled</h3>
              <p className="text-xs max-w-sm mx-auto">
                No active performances are currently registered in the database.
              </p>
            </div>
          )}

          {/* Real Events Grid */}
          {!isLoadingEvents && !eventsError && events && events.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {events.map((event) => {
                const imageSrc = getPerformanceImage(event);
                const createdDate = event.createdAt
                  ? new Date(event.createdAt).toLocaleDateString('en-US', {
                      month: 'short',
                      day: 'numeric',
                    })
                  : 'CURRENT SEASON';

                return (
                  <div
                    key={event.id}
                    className="bg-[#efeee8] border border-[#e4e2dd] rounded-sm overflow-hidden flex flex-col justify-between group hover:bg-[#e9e8e3] transition-colors"
                  >
                    <div>
                      {/* Event Image Frame */}
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
                          CONFIRMED REPERTOIRE
                        </div>
                      </div>

                      {/* Event Info */}
                      <div className="p-6 flex flex-col gap-2">
                        <span className="font-mono text-xs text-[#5a413c]">
                          RELEASED: {createdDate.toUpperCase()} // SHOWTIMES ACTIVE
                        </span>
                        <h3 className="font-headline text-2xl text-[#1b1c19] font-normal group-hover:text-[#ae2714] transition-colors">
                          {event.name}
                        </h3>
                        <p className="text-xs text-[#5a413c] leading-relaxed line-clamp-2">
                          {event.description || 'Full performance details and seating availability listed on event page.'}
                        </p>
                      </div>
                    </div>

                    {/* Action Bar */}
                    <div className="px-6 pb-6 pt-2 flex items-center justify-between mt-auto border-t border-[#e4e2dd]/60">
                      <div className="flex flex-col">
                        <span className="font-mono text-[10px] text-[#5a413c] uppercase">
                          RESERVATION STATUS
                        </span>
                        <span className="font-mono text-xs font-semibold text-[#576244]">
                          AVAILABLE SEATS
                        </span>
                      </div>
                      <Link
                        to={`/events/${event.id}`}
                        className="bg-[#30312d] text-[#f2f1eb] hover:bg-[#d1402a] hover:text-[#fffafa] px-4 py-2 rounded-sm font-mono text-xs uppercase tracking-wider transition-colors shadow-sm"
                      >
                        Select Seats
                      </Link>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>

        {/* Section 5: Architecture & Concurrency Section */}
        <section className="w-full my-12 bg-[#30312d] text-[#f2f1eb] p-6 sm:p-10 rounded-sm">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
            <div className="lg:col-span-8 flex flex-col gap-4">
              <div className="inline-flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-[#d8e5be]"></span>
                <span className="font-mono text-xs uppercase text-[#d8e5be] font-semibold tracking-wider">
                  STRESS BENCHMARK ENVIRONMENT
                </span>
              </div>
              <h2 className="font-headline text-3xl sm:text-4xl text-[#f2f1eb] font-normal leading-tight">
                Inspect Concurrency Guarantees Under Load
              </h2>
              <p className="text-sm sm:text-base text-[#dbdad5] max-w-2xl leading-relaxed">
                Verify our allocation guarantees under peak concurrency. Observe how Redis locks and PostgreSQL transactions isolate thousands of simultaneous requests without a single double-booking.
              </p>

              {/* Inline Telemetry Snippet */}
              <div className="bg-[#1b1c19] border border-[#5a413c]/40 p-4 rounded-sm font-mono text-xs text-[#fbf9f4] flex flex-col gap-1 max-w-xl">
                <div className="text-[#8e706b]">
                  // BENCHMARK EMULATION TRACE: 5,000 CONCURRENT USERS / ROW A
                </div>
                <div className="text-[#dbe7c1]">
                  2025-05-18T14:32:01.104Z [REDIS] ACQUIRE_LOCK seat:elb-A4 uuid:usr-8192 -&gt; OK (TTL: 600s)
                </div>
                <div className="text-[#ffdad6]">
                  2025-05-18T14:32:01.109Z [REDIS] REJECT_LOCK seat:elb-A4 uuid:usr-3041 -&gt; 409 CONFLICT
                </div>
                <div className="text-[#fbf9f4]">
                  2025-05-18T14:32:01.115Z [PG-WAL] COMMIT TX #882941 SEAT_STATUS=HELD
                </div>
              </div>
            </div>

            <div className="lg:col-span-4 flex flex-col items-start lg:items-end justify-center">
              <Link
                to="/lab"
                className="inline-flex items-center gap-2 bg-[#d1402a] text-[#fffafa] hover:bg-[#fbf9f4] hover:text-[#1b1c19] px-6 py-3.5 rounded-sm font-mono text-xs uppercase tracking-wider font-semibold transition-all shadow-[2px_2px_0px_0px_#ffffff]"
              >
                <span>Explore Concurrency Lab</span>
                <Zap className="w-4 h-4 text-[#ffdea4]" />
              </Link>
              <span className="font-mono text-[10px] text-[#dbdad5] mt-2.5">
                Interactive chaos-engineering sandbox
              </span>
            </div>
          </div>
        </section>

        {/* Section 6: Final CTA (Notification & Drop Alert) */}
        <section className="w-full py-12">
          <div className="bg-[#efeee8] border border-[#e4e2dd] p-6 sm:p-10 rounded-sm flex flex-col md:flex-row md:items-center justify-between gap-8">
            <div className="flex flex-col gap-2 max-w-xl">
              <span className="font-mono text-xs uppercase text-[#5a413c] tracking-wider">
                PROGRAMMING DISPATCH
              </span>
              <h2 className="font-headline text-3xl sm:text-4xl text-[#1b1c19] font-normal">
                Never Miss a Seat Release
              </h2>
              <p className="text-sm text-[#5a413c] leading-relaxed">
                Receive cryptographic event tokens 15 minutes prior to general sales. No promotional fluff; strictly performance schedules and priority drop timestamps.
              </p>
            </div>

            {/* Clean Input & Vermilion Action */}
            <form onSubmit={handleDispatchSubmit} className="flex flex-col gap-2 w-full md:w-auto md:min-w-[360px]">
              <div className="flex flex-col sm:flex-row gap-2">
                <input
                  type="email"
                  value={dispatchEmail}
                  onChange={(e) => setDispatchEmail(e.target.value)}
                  placeholder="patron@orchestra.org"
                  required
                  className="bg-[#ffffff] text-[#1b1c19] border border-[#e4e2dd] font-sans text-sm px-4 py-2.5 rounded-sm focus:outline-none focus:border-[#1b1c19] flex-1"
                />
                <button
                  type="submit"
                  className="bg-[#d1402a] text-[#fffafa] hover:bg-[#30312d] hover:text-[#f2f1eb] px-6 py-2.5 rounded-sm font-mono text-xs uppercase font-semibold transition-colors shadow-[2px_2px_0px_0px_#1b1c19] whitespace-nowrap"
                >
                  Request Alerts
                </button>
              </div>

              {dispatchSubmitted && (
                <div className="text-[#576244] font-mono text-xs mt-1 flex items-center gap-1.5">
                  <Check className="w-4 h-4 text-[#576244]" />
                  <span>[Preview Feature] Cryptographic dispatch simulation verified. No email backend connected.</span>
                </div>
              )}

              <span className="font-mono text-[10px] text-[#5a413c]">
                Illustrative visual element. No subscription endpoint connected.
              </span>
            </form>
          </div>
        </section>
      </div>
    </div>
  );
};
