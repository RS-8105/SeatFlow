import React, { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api-client';
import { useAuth } from '../lib/auth';
import {
  Zap,
  RotateCcw,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Clock,
  Shield,
  Layers,
  BarChart3,
  Users,
  Database,
  Filter,
  Check,
  Info,
  ShieldAlert,
  LogIn,
  Play,
  Terminal,
  RefreshCw
} from 'lucide-react';

export const ConcurrencyLabPage = () => {
  const queryClient = useQueryClient();
  const location = useLocation();
  const { isAuthenticated } = useAuth();
  const titleRef = React.useRef(null);

  // Scroll to title/top whenever navigated to /lab
  React.useEffect(() => {
    if (titleRef.current) {
      titleRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } else {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [location.pathname, location.key]);

  // Configuration state
  const [showtimeId, setShowtimeId] = useState(1);
  const [seatId, setSeatId] = useState(1);
  const [userCount, setUserCount] = useState(50);
  const [attemptFilter, setAttemptFilter] = useState('ALL'); // 'ALL' | 'ACQUIRED' | 'REJECTED' | 'ERROR'

  // Reset confirmation modal state
  const [isResetConfirmOpen, setIsResetConfirmOpen] = useState(false);
  const [resetMessage, setResetMessage] = useState(null);
  const [resetError, setResetError] = useState(null);

  // Fetch real seats for selected demo showtime
  const { data: seats, isLoading: isLoadingSeats } = useQuery({
    queryKey: ['showtime-seats', showtimeId],
    queryFn: () => api.getSeatsForShowtime(showtimeId),
  });

  // Race execution mutation
  const raceMutation = useMutation({
    mutationFn: (payload) => api.simulateRace(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['showtime-seats', showtimeId] });
      queryClient.invalidateQueries({ queryKey: ['seats', showtimeId] });
    },
  });

  // Demo reset mutation
  const resetMutation = useMutation({
    mutationFn: (payload) => api.resetDemo(payload),
    onSuccess: (data) => {
      setResetMessage(data.message || 'Demo showtime reset successfully.');
      setResetError(null);
      setIsResetConfirmOpen(false);
      queryClient.invalidateQueries({ queryKey: ['showtime-seats', showtimeId] });
      queryClient.invalidateQueries({ queryKey: ['seats', showtimeId] });
    },
    onError: (err) => {
      setResetError(err.message || 'Failed to reset demo state.');
      setResetMessage(null);
    },
  });

  const handleRunSimulation = () => {
    setResetMessage(null);
    setResetError(null);
    raceMutation.mutate({
      showtimeId,
      seatId,
      userCount,
    });
  };

  const handleConfirmReset = () => {
    resetMutation.mutate({ showtimeId });
  };

  const applyPreset = (presetUsers, targetSeatId) => {
    setUserCount(presetUsers);
    if (targetSeatId) setSeatId(targetSeatId);
  };

  // Filter attempt records
  const attempts = raceMutation.data?.attempts || [];
  const filteredAttempts = attempts.filter((att) => {
    const isAcq = att.status === 'ACQUIRED' || att.acquired === true;
    const isErr = att.status === 'ERROR' || Boolean(att.error);
    const isRej = att.status === 'REJECTED' || (!isAcq && !isErr);

    if (attemptFilter === 'ALL') return true;
    if (attemptFilter === 'ACQUIRED') return isAcq;
    if (attemptFilter === 'REJECTED') return isRej;
    if (attemptFilter === 'ERROR') return isErr;
    return true;
  });

  const successCount = raceMutation.data?.successfulHolds ?? (attempts.filter((a) => a.acquired || a.status === 'ACQUIRED').length);
  const rejectCount = raceMutation.data?.rejectedHolds ?? raceMutation.data?.failedAttempts ?? (attempts.filter((a) => !a.acquired && !a.error).length);
  const totalExecuted = raceMutation.data?.requestedUsers ?? raceMutation.data?.totalAttempts ?? attempts.length;
  const oversellPct = raceMutation.data?.oversellPercentage !== undefined
    ? `${Number(raceMutation.data.oversellPercentage).toFixed(3)}%`
    : '0.000%';

  return (
    <div className="space-y-8 max-w-[1280px] mx-auto pb-16">
      {/* Top Meta / Dossier Classification Strip */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-2 pb-4 border-b border-[#e4e2dd]">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="px-2 py-0.5 bg-[#1b1c19] text-[#fbf9f4] font-mono text-[10px] uppercase tracking-wider rounded-sm font-semibold">
            LAB_FACILITY // BENCHMARK
          </span>
          <span className="px-2 py-0.5 bg-[#efeee8] text-[#5a413c] font-mono text-[10px] uppercase rounded-sm border border-[#e4e2dd]">
            REDIS v7.2 [REDLOCK]
          </span>
          <span className="px-2 py-0.5 bg-[#efeee8] text-[#5a413c] font-mono text-[10px] uppercase rounded-sm border border-[#e4e2dd]">
            PGSQL 16 [SERIALIZABLE]
          </span>
          <span className="text-[#576244] font-mono text-[10px] flex items-center gap-1 font-semibold ml-1">
            <span className="inline-block w-2 h-2 rounded-full bg-[#576244] animate-pulse"></span>
            ISOLATION ENGINE ACTIVE
          </span>
        </div>
        <div className="flex items-center gap-3 font-mono text-[11px] text-[#5a413c]">
          <span>INTEGRITY TARGET: 100.000%</span>
          <span>•</span>
          <span>MAX OVERSELL TOLERANCE: 0.000%</span>
        </div>
      </div>

      {/* Section 1: Laboratory Masthead & Preset Scenarios */}
      <section className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        <div ref={titleRef} id="concurrency-lab-title" className="lg:col-span-8 flex flex-col gap-2">
          <div className="flex items-center gap-1.5 text-[#ae2714] font-mono text-xs uppercase tracking-widest font-semibold">
            <span>Section 01</span>
            <span>/</span>
            <span>Contention Verification Bench</span>
          </div>
          <h1 className="font-headline text-3xl sm:text-5xl text-[#1b1c19] tracking-tight font-normal">
            Concurrency Laboratory <span className="text-[#5a413c] font-headline text-2xl italic font-normal">// Deterministic Collision Engine</span>
          </h1>
          <p className="text-sm sm:text-base text-[#5a413c] max-w-2xl mt-1 leading-relaxed">
            Empirically demonstrate high-throughput ticket reservation contention. Simulate simultaneous user requests targeting an identical seat block and inspect atomic Redis locks and PostgreSQL transactional integrity under high-demand drops.
          </p>
        </div>

        {/* Preset Selection Card */}
        <div className="lg:col-span-4 bg-[#efeee8] border border-[#e4e2dd] p-4 rounded-sm shadow-sm">
          <div className="flex items-center justify-between pb-2 mb-3 border-b border-[#e4e2dd]">
            <span className="font-mono text-xs text-[#5a413c] uppercase tracking-wider font-semibold">
              Benchmark Presets
            </span>
            <span className="font-mono text-[10px] text-[#ae2714] font-bold">
              PRESET-CONFIG
            </span>
          </div>
          <div className="flex flex-col gap-2">
            <button
              type="button"
              onClick={() => applyPreset(100, 1)}
              className="text-left p-2.5 bg-[#ffffff] hover:bg-[#fbf9f4] border border-[#d1c9b8] rounded-sm transition-all flex items-center justify-between shadow-sm"
            >
              <div className="flex flex-col">
                <span className="font-mono text-xs text-[#1b1c19] font-semibold">High-Demand Drop</span>
                <span className="text-[11px] text-[#5a413c]">100 Users → Single Seat Lock</span>
              </div>
              <span className="font-mono text-xs text-[#ae2714] uppercase font-bold">P-01</span>
            </button>
            <button
              type="button"
              onClick={() => applyPreset(250, 2)}
              className="text-left p-2.5 bg-[#ffffff] hover:bg-[#fbf9f4] border border-[#d1c9b8] rounded-sm transition-all flex items-center justify-between shadow-sm"
            >
              <div className="flex flex-col">
                <span className="font-mono text-xs text-[#1b1c19] font-semibold">Tier Surge Stress</span>
                <span className="text-[11px] text-[#5a413c]">250 Users → High Contention</span>
              </div>
              <span className="font-mono text-xs text-[#ae2714] uppercase font-bold">P-02</span>
            </button>
            <button
              type="button"
              onClick={() => applyPreset(50, 1)}
              className="text-left p-2.5 bg-[#ffffff] hover:bg-[#fbf9f4] border border-[#d1c9b8] rounded-sm transition-all flex items-center justify-between shadow-sm"
            >
              <div className="flex flex-col">
                <span className="font-mono text-xs text-[#1b1c19] font-semibold">Standard Burst</span>
                <span className="text-[11px] text-[#5a413c]">50 Users → Baseline Lease</span>
              </div>
              <span className="font-mono text-xs text-[#ae2714] uppercase font-bold">P-03</span>
            </button>
          </div>
        </div>
      </section>

      {/* Section 2: Architectural Pipeline Visualizer & Control Panel */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Simulation Control Panel */}
        <div className="lg:col-span-7 bg-[#efeee8] border border-[#e4e2dd] p-6 rounded-sm shadow-sm flex flex-col justify-between">
          <div className="space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-[#e4e2dd]">
              <div className="flex items-center gap-2">
                <Zap className="w-4 h-4 text-[#ae2714]" />
                <span className="font-mono text-xs text-[#1b1c19] uppercase font-semibold tracking-wider">
                  Simulation Control Matrix
                </span>
              </div>
              <span className="px-2 py-0.5 bg-[#fbf9f4] border border-[#e4e2dd] text-[#5a413c] font-mono text-[10px] uppercase rounded-sm">
                SYS::RUN_MODE = TEST
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {/* Showtime Selector */}
              <div className="sm:col-span-1 flex flex-col gap-1">
                <label className="font-mono text-[10px] text-[#5a413c] uppercase">
                  Target Showtime Catalog Entity
                </label>
                <select
                  value={showtimeId}
                  onChange={(e) => setShowtimeId(Number(e.target.value))}
                  className="bg-[#ffffff] text-[#1b1c19] border border-[#d1c9b8] p-2 rounded-sm font-mono text-xs focus:outline-none"
                >
                  <option value={1}>Showtime #1 (Demo Showcase)</option>
                  <option value={2}>Showtime #2 (Stress Simulation)</option>
                </select>
              </div>

              {/* Target Seat Selector */}
              <div className="sm:col-span-1 flex flex-col gap-1">
                <label className="font-mono text-[10px] text-[#5a413c] uppercase">
                  Target Contended Seat
                </label>
                <select
                  value={seatId}
                  onChange={(e) => setSeatId(Number(e.target.value))}
                  className="bg-[#ffffff] text-[#1b1c19] border border-[#d1c9b8] p-2 rounded-sm font-mono text-xs focus:outline-none"
                >
                  {isLoadingSeats ? (
                    <option value={1}>Loading seats...</option>
                  ) : seats && seats.length > 0 ? (
                    seats.slice(0, 15).map((s) => (
                      <option key={s.id} value={s.id}>
                        Seat #{s.id} (Row {s.rowLabel}, Seat {s.seatNumber} — {s.status})
                      </option>
                    ))
                  ) : (
                    <option value={1}>Seat #1 (Default)</option>
                  )}
                </select>
              </div>

              {/* Threads Slider */}
              <div className="sm:col-span-2 bg-[#ffffff] border border-[#d1c9b8] p-4 rounded-sm space-y-2">
                <div className="flex justify-between items-center">
                  <label className="font-mono text-xs text-[#5a413c] uppercase tracking-wider">
                    Simulated Concurrent Threads (Workers)
                  </label>
                  <div className="flex items-center gap-1 font-mono text-xs font-bold text-[#1b1c19]">
                    <span>{userCount}</span>
                    <span className="text-[#5a413c] font-normal text-[10px]">USERS</span>
                  </div>
                </div>
                <input
                  type="range"
                  min="10"
                  max="500"
                  step="10"
                  value={userCount}
                  onChange={(e) => setUserCount(Number(e.target.value))}
                  className="w-full accent-[#d1402a] cursor-pointer"
                />
                <div className="flex justify-between font-mono text-[10px] text-[#5a413c]">
                  <span>10 workers</span>
                  <span>250 workers (nominal)</span>
                  <span>500 workers (stress limit)</span>
                </div>
              </div>
            </div>
          </div>

            {/* Action Buttons */}
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 pt-6 border-t border-[#e4e2dd] mt-4">
              <button
                type="button"
                disabled={raceMutation.isPending}
                onClick={handleRunSimulation}
                className="flex-1 bg-[#d1402a] hover:bg-[#30312d] disabled:opacity-50 text-[#fffafa] py-3 px-6 rounded-sm font-mono text-xs uppercase font-bold tracking-wider transition-all shadow-[2px_2px_0px_0px_#1b1c19] active:translate-x-[1px] active:translate-y-[1px] flex items-center justify-center gap-2"
              >
                {raceMutation.isPending ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    <span>Executing Contention Burst...</span>
                  </>
                ) : (
                  <>
                    <Play className="w-4 h-4 fill-current" />
                    <span>Run Concurrency Simulation</span>
                  </>
                )}
              </button>

              <button
                type="button"
                disabled={resetMutation.isPending}
                onClick={() => setIsResetConfirmOpen(true)}
                className="bg-[#ffffff] hover:bg-[#e4e2dd] border border-[#d1c9b8] text-[#1b1c19] py-3 px-4 rounded-sm font-mono text-xs uppercase font-semibold transition-colors flex items-center justify-center gap-1.5 shadow-sm"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>Reset Demo Showtime</span>
              </button>
            </div>
          </div>

        {/* Live Execution Telemetry Dossier */}
        <div className="lg:col-span-5 bg-[#efeee8] border border-[#e4e2dd] p-6 rounded-sm shadow-sm flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between pb-2 mb-2 border-b border-[#e4e2dd]">
              <span className="font-mono text-[10px] text-[#5a413c] uppercase tracking-wider">
                Atomicity Mechanics
              </span>
              <span className="font-mono text-[10px] text-[#576244] font-bold">
                VALIDATED REDLOCK
              </span>
            </div>
            <h3 className="font-headline text-xl text-[#1b1c19] mb-1">
              Hardware-Level Lock-Stepping
            </h3>
            <p className="text-xs text-[#5a413c] leading-relaxed mb-4">
              Requests enter the simulation thread pool and simultaneously execute an atomic Redis lease token acquisition. Exactly 1 execution thread wins ownership; all trailing threads instantaneously bounce with an HTTP 409 Conflict.
            </p>

            {/* SVG Contention Resolution Ring Pipeline */}
            <div className="bg-[#ffffff] border border-[#d1c9b8] p-3 rounded-sm mb-4 shadow-sm">
              <div className="flex items-center justify-between mb-2 font-mono text-[10px]">
                <span className="text-[#1b1c19] uppercase font-medium">Contention Resolution Ring</span>
                <span className="text-[#ae2714] font-bold">Deterministic 1-to-N Shed</span>
              </div>
              <svg className="w-full h-20" fill="none" viewBox="0 0 320 80">
                <line stroke="#dbdad5" strokeDasharray="3 3" strokeWidth="2" x1="20" x2="100" y1="40" y2="40"></line>
                <line stroke="#576244" strokeWidth="2.5" x1="100" x2="180" y1="40" y2="20"></line>
                <line stroke="#ae2714" strokeDasharray="2 2" strokeWidth="1.5" x1="100" x2="180" y1="40" y2="60"></line>
                <line stroke="#576244" strokeWidth="2.5" x1="180" x2="290" y1="20" y2="20"></line>
                <line stroke="#dbdad5" strokeWidth="1" x1="180" x2="290" y1="60" y2="60"></line>

                {/* Node 1: Ingress Burst */}
                <rect fill="#efeee8" height="24" rx="2" stroke="#1b1c19" strokeWidth="1.5" width="28" x="10" y="28"></rect>
                <text fill="#1b1c19" fontFamily="JetBrains Mono" fontSize="8" fontWeight="bold" textAnchor="middle" x="24" y="44">{userCount}N</text>

                {/* Node 2: Mutex */}
                <circle cx="100" cy="40" fill="#1b1c19" r="14"></circle>
                <text fill="#fbf9f4" fontFamily="JetBrains Mono" fontSize="7" textAnchor="middle" x="100" y="43">MUTEX</text>

                {/* Node 3: Acquired */}
                <rect fill="#d8e5be" height="20" rx="2" width="115" x="180" y="10"></rect>
                <text fill="#404a2e" fontFamily="JetBrains Mono" fontSize="7.5" fontWeight="bold" x="186" y="24">ACQUIRED: 1 THREAD (200)</text>

                {/* Node 4: Collision Drop */}
                <rect fill="#ffdad6" height="20" rx="2" width="115" x="180" y="50"></rect>
                <text fill="#93000a" fontFamily="JetBrains Mono" fontSize="7.5" fontWeight="bold" x="186" y="64">COLLISION: REJECTED (409)</text>
              </svg>
            </div>
          </div>

          <div className="bg-[#fbf9f4] border border-[#e4e2dd] p-3 text-[#5a413c] font-mono text-[10px] flex flex-col gap-1">
            <div className="flex justify-between">
              <span>CONSISTENCY LEVEL:</span>
              <span className="text-[#1b1c19] font-semibold">STRICT SERIALIZABILITY</span>
            </div>
            <div className="flex justify-between">
              <span>RETRY PROTOCOL:</span>
              <span className="text-[#1b1c19] font-semibold">FAST-FAIL WITHOUT BACKOFF</span>
            </div>
            <div className="flex justify-between">
              <span>LEASE TTL:</span>
              <span className="text-[#1b1c19] font-semibold">600,000 MS (10.00 MIN)</span>
            </div>
          </div>
        </div>
      </div>

      {/* Section 3: Telemetry Output & Quantitative Metrics Grid */}
      <section className="space-y-4">
        <div className="flex items-center justify-between pb-2 border-b border-[#e4e2dd]">
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 bg-[#ae2714] text-[#fffafa] font-mono text-[10px] uppercase font-bold rounded-sm">
              Telemetry
            </span>
            <h2 className="font-headline text-2xl text-[#1b1c19] font-normal">
              Simulation Verification Output
            </h2>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#576244] animate-pulse"></span>
            <span className="font-mono text-xs text-[#1b1c19] font-semibold">
              CLUSTER SYNCED: 0 DOUBLE-BOOKINGS
            </span>
          </div>
        </div>

        {/* Quantitative Metrics Tiles */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="bg-[#efeee8] border border-[#e4e2dd] p-4 rounded-sm shadow-sm flex flex-col justify-between">
            <span className="font-mono text-[10px] text-[#5a413c] uppercase">Total Concurrent Ingress</span>
            <div className="my-2">
              <span className="font-headline text-3xl sm:text-4xl text-[#1b1c19] font-normal">
                {raceMutation.data ? totalExecuted : userCount}
              </span>
            </div>
            <span className="font-mono text-[10px] text-[#5a413c]">
              BURST DURATION: {raceMutation.data?.executionTimeMs ? `${raceMutation.data.executionTimeMs}ms` : '—'}
            </span>
          </div>

          <div className="bg-[#efeee8] border border-[#e4e2dd] p-4 rounded-sm shadow-sm flex flex-col justify-between">
            <span className="font-mono text-[10px] text-[#576244] uppercase font-semibold">Successful Holds (200 OK)</span>
            <div className="my-2 flex items-baseline gap-2">
              <span className="font-headline text-3xl sm:text-4xl text-[#576244] font-normal">
                {raceMutation.data ? successCount : '—'}
              </span>
              <span className="font-mono text-[10px] text-[#576244] font-bold">SEAT CLAIMED</span>
            </div>
            <span className="font-mono text-[10px] text-[#576244]">REDIS LEASE ACQUIRED</span>
          </div>

          <div className="bg-[#efeee8] border border-[#e4e2dd] p-4 rounded-sm shadow-sm flex flex-col justify-between">
            <span className="font-mono text-[10px] text-[#ae2714] uppercase font-semibold">Collision Drops (409)</span>
            <div className="my-2 flex items-baseline gap-2">
              <span className="font-headline text-3xl sm:text-4xl text-[#ae2714] font-normal">
                {raceMutation.data ? rejectCount : '—'}
              </span>
              <span className="font-mono text-[10px] text-[#ae2714] font-bold">BOUNCED</span>
            </div>
            <span className="font-mono text-[10px] text-[#5a413c]">FAST-FAIL REJECTION</span>
          </div>

          <div className="bg-[#efeee8] border border-[#e4e2dd] p-4 rounded-sm shadow-sm flex flex-col justify-between">
            <span className="font-mono text-[10px] text-[#5a413c] uppercase">PostgreSQL Oversell</span>
            <div className="my-2">
              <span className={`font-headline text-3xl sm:text-4xl font-normal ${
                raceMutation.data && Number(raceMutation.data.oversellPercentage || 0) > 0 ? 'text-[#ae2714]' : 'text-[#1b1c19]'
              }`}>
                {raceMutation.data ? oversellPct : '0.000%'}
              </span>
            </div>
            <span className={`font-mono text-[10px] font-semibold ${
              raceMutation.data && Number(raceMutation.data.oversellPercentage || 0) > 0 ? 'text-[#ae2714]' : 'text-[#576244]'
            }`}>
              {raceMutation.data && Number(raceMutation.data.oversellPercentage || 0) > 0 ? 'INTEGRITY VIOLATION' : 'WAL ZERO DEFECTS'}
            </span>
          </div>
        </div>

        {/* Feedback Notices */}
        {resetMessage && (
          <div className="p-3 bg-[#dbe7c1] border border-[#576244] rounded-sm text-[#404a2e] font-mono text-xs flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0 text-[#576244]" />
            <span>{resetMessage}</span>
          </div>
        )}

        {resetError && (
          <div className="p-3 bg-[#ffdad6] border border-[#ba1a1a] rounded-sm text-[#ba1a1a] font-mono text-xs flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{resetError}</span>
          </div>
        )}

        {/* Trace Log Terminal */}
        <div className="bg-[#1b1c19] border border-[#30312d] rounded-sm overflow-hidden text-[#f2f1eb]">
          <div className="p-3 bg-[#30312d] flex items-center justify-between border-b border-[#1b1c19] font-mono text-xs">
            <div className="flex items-center gap-2">
              <Terminal className="w-4 h-4 text-[#d8e5be]" />
              <span className="font-bold text-[#fbf9f4]">TRACE LOG TERMINAL ({filteredAttempts.length} RECORDS)</span>
            </div>
            <div className="flex gap-1.5">
              {['ALL', 'ACQUIRED', 'REJECTED'].map((filter) => (
                <button
                  key={filter}
                  type="button"
                  onClick={() => setAttemptFilter(filter)}
                  className={`px-2 py-0.5 rounded text-[10px] font-semibold uppercase ${
                    attemptFilter === filter
                      ? 'bg-[#d1402a] text-white'
                      : 'bg-[#1b1c19] text-[#dbdad5] hover:text-white'
                  }`}
                >
                  {filter}
                </button>
              ))}
            </div>
          </div>

          <div className="p-4 max-h-72 overflow-y-auto font-mono text-xs space-y-1">
            {attempts.length === 0 ? (
              <p className="text-[#8e706b] italic py-8 text-center">
                Simulation idle. Configure parameters above and click &quot;Run Concurrency Simulation&quot; to execute real distributed contention against Redis and PostgreSQL.
              </p>
            ) : filteredAttempts.length === 0 ? (
              <p className="text-[#8e706b] italic py-4 text-center">
                No attempt records match current filter &quot;{attemptFilter}&quot;.
              </p>
            ) : (
              filteredAttempts.map((att, i) => {
                const isAcq = att.status === 'ACQUIRED' || att.acquired === true;
                const isErr = att.status === 'ERROR' || Boolean(att.error);
                const isRej = att.status === 'REJECTED' || (!isAcq && !isErr);
                const statusLabel = isAcq ? 'ACQUIRED' : isErr ? 'ERROR' : 'REJECTED';
                return (
                  <div key={i} className="flex items-center justify-between py-1 border-b border-[#30312d]/40">
                    <div className="flex items-center gap-2">
                      <span className="text-[#8e706b]">#{String(att.attemptNumber || i + 1).padStart(3, '0')}</span>
                      <span className="text-[#dbdad5]">{att.userId || `USER-${i + 1}`}</span>
                      <span className="text-[#8e706b]">→</span>
                      <span className={isAcq ? 'text-[#d8e5be] font-bold' : isRej ? 'text-[#ffdad6]' : 'text-white'}>
                        {statusLabel}
                      </span>
                    </div>
                    <span className="text-[10px] text-[#8e706b]">
                      {att.error || (isAcq ? 'HTTP 200 OK — LEASE TOKEN ISSUED' : 'HTTP 409 CONFLICT — LEASE DENIED')}
                    </span>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </section>

      {/* Reset Confirmation Modal */}
      {isResetConfirmOpen && (
        <div className="fixed inset-0 z-50 bg-[#1b1c19]/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-[#ffffff] border border-[#1b1c19] max-w-md w-full p-6 rounded-sm shadow-[4px_4px_0px_0px_#1b1c19] space-y-4">
            <div className="flex items-center gap-2 text-[#ae2714]">
              <AlertTriangle className="w-5 h-5 shrink-0" />
              <h3 className="font-headline text-xl text-[#1b1c19]">Confirm Demo Reset</h3>
            </div>
            <p className="text-xs text-[#5a413c] leading-relaxed">
              This will release all simulated locks and reset confirmed reservations for Demo Showtime #{showtimeId}. Restricted to demo showtimes only.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setIsResetConfirmOpen(false)}
                className="px-3.5 py-1.5 bg-[#efeee8] hover:bg-[#e4e2dd] text-[#1b1c19] border border-[#d1c9b8] rounded-sm font-mono text-xs uppercase"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={resetMutation.isPending}
                onClick={handleConfirmReset}
                className="px-3.5 py-1.5 bg-[#d1402a] hover:bg-[#ae2714] text-[#fffafa] rounded-sm font-mono text-xs uppercase font-bold"
              >
                {resetMutation.isPending ? 'Resetting...' : 'Confirm Reset'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
