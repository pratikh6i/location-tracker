import React, { useState, useEffect } from 'react';
import { initializeApp } from 'firebase/app';
import { getAuth, signInAnonymously, onAuthStateChanged } from 'firebase/auth';
import { getFirestore, collection, addDoc } from 'firebase/firestore';
import { MapPin, Shield, Activity, Cloud, Smartphone, FileText, Zap, Navigation, Radio } from 'lucide-react';

// --- CONFIGURATION ---
// Fallback to hardcoded values if env vars fail (Critical for build reliability)
const getEnv = (key, fallback) => import.meta.env[key] || fallback;

const firebaseConfig = {
  apiKey: getEnv('VITE_FIREBASE_API_KEY', "AIzaSyBTnNBYbXE3k2YXcOI-7Mbf6-eT0K5rSog"),
  authDomain: getEnv('VITE_FIREBASE_AUTH_DOMAIN', "android-location-tracker-4fe65.firebaseapp.com"),
  projectId: getEnv('VITE_FIREBASE_PROJECT_ID', "android-location-tracker-4fe65"),
  storageBucket: getEnv('VITE_FIREBASE_STORAGE_BUCKET', "android-location-tracker-4fe65.firebasestorage.app"),
  messagingSenderId: getEnv('VITE_FIREBASE_MESSAGING_SENDER_ID', "361729564870"),
  appId: getEnv('VITE_FIREBASE_APP_ID', "1:361729564870:android:938b93f0519ae31540f7c7")
};

// --- LOGGING ---
const LOGS = [];
const log = (tag, msg, data = '') => {
  const entry = `[${new Date().toLocaleTimeString()}] [${tag}] ${msg} ${data ? JSON.stringify(data) : ''}`;
  console.log(entry);
  LOGS.push(entry);
  if (LOGS.length > 500) LOGS.shift();
};

// --- FIREBASE INIT ---
let app, auth, db;
try {
  if (!firebaseConfig.apiKey) throw new Error("Missing API Key");
  app = initializeApp(firebaseConfig);
  auth = getAuth(app);
  db = getFirestore(app);
  log("INIT", "Firebase initialized successfully");
} catch (e) {
  log("INIT_ERR", e.message);
  console.error("Firebase Init Failed:", e);
}

export default function App() {
  const [step, setStep] = useState('welcome');
  const [user, setUser] = useState(null);
  const [location, setLocation] = useState(null);
  const [queue, setQueue] = useState([]);
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [isTracking, setIsTracking] = useState(false);

  // 1. Auth & Network Listener
  useEffect(() => {
    if (!auth) return;
    const unsub = onAuthStateChanged(auth, u => {
      if (u) {
        setUser(u);
        log('AUTH', 'User signed in', u.uid);
      }
    });

    const handleNet = () => {
      setIsOnline(navigator.onLine);
      log('NET', 'Status changed', navigator.onLine ? 'Online' : 'Offline');
    };
    window.addEventListener('online', handleNet);
    window.addEventListener('offline', handleNet);

    const saved = localStorage.getItem('offline_queue');
    if (saved) setQueue(JSON.parse(saved));

    return () => { unsub(); window.removeEventListener('online', handleNet); window.removeEventListener('offline', handleNet); };
  }, []);

  // 2. Start Tracking
  useEffect(() => {
    if (step !== 'dashboard') return;

    log('GPS', 'Starting tracking loop');
    setIsTracking(true);

    const track = () => {
      if (!navigator.geolocation) return log('GPS', 'Not supported');

      navigator.geolocation.getCurrentPosition(
        pos => {
          const point = {
            lat: pos.coords.latitude,
            lng: pos.coords.longitude,
            acc: pos.coords.accuracy,
            time: new Date().toISOString()
          };
          setLocation(point);
          log('GPS', 'Location captured', point);

          setQueue(prev => {
            const next = [...prev, point];
            localStorage.setItem('offline_queue', JSON.stringify(next));
            return next;
          });
        },
        err => log('GPS_ERR', err.message),
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
      );
    };

    // Initial track
    track();
    // Interval track (5 mins)
    const interval = setInterval(track, 300000);
    return () => clearInterval(interval);
  }, [step]);

  const handleStart = async () => {
    if (!auth) return alert("Firebase not initialized. Check logs.");
    log('USER', 'Clicked Start Setup');
    try {
      await signInAnonymously(auth);
      setStep('dashboard');
    } catch (e) {
      log('AUTH_ERR', e.message);
      alert('Setup Failed: ' + e.message);
    }
  };

  const handleSync = async () => {
    if (queue.length === 0) return alert("Nothing to sync!");
    if (!isOnline) return alert("No Internet Connection!");
    if (!db) return alert("Database not initialized");

    log('SYNC', `Attempting to sync ${queue.length} items...`);
    try {
      const promises = queue.map(item => addDoc(collection(db, 'logs'), item));
      await Promise.all(promises);

      log('SYNC', 'Upload successful');
      setQueue([]);
      localStorage.removeItem('offline_queue');
      alert("Sync Complete!");
    } catch (e) {
      log('SYNC_ERR', e.message);
      alert("Sync Failed: " + e.message);
    }
  };

  const exportLogs = () => {
    const text = LOGS.join('\n');
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'debug_logs.txt';
    a.click();
  };

  // --- UI COMPONENTS ---

  const GlassCard = ({ children, className = "" }) => (
    <div className={`backdrop-blur-xl bg-white/10 border border-white/20 shadow-xl rounded-3xl p-6 ${className}`}>
      {children}
    </div>
  );

  // --- WELCOME SCREEN ---
  if (step === 'welcome') return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-900 to-slate-900 text-white flex flex-col items-center justify-center p-6 font-sans relative overflow-hidden">
      {/* Background Orbs */}
      <div className="absolute top-0 left-0 w-96 h-96 bg-blue-500/20 rounded-full blur-3xl -translate-x-1/2 -translate-y-1/2 animate-pulse" />
      <div className="absolute bottom-0 right-0 w-96 h-96 bg-purple-500/20 rounded-full blur-3xl translate-x-1/2 translate-y-1/2 animate-pulse delay-1000" />

      <GlassCard className="w-full max-w-sm flex flex-col items-center text-center z-10">
        <div className="w-24 h-24 bg-gradient-to-tr from-blue-500 to-cyan-400 rounded-full flex items-center justify-center mb-6 shadow-lg shadow-blue-500/30 animate-bounce-slow">
          <Navigation size={48} className="text-white" />
        </div>

        <h1 className="text-3xl font-bold mb-2 bg-gradient-to-r from-white to-slate-300 bg-clip-text text-transparent">
          Location Keeper
        </h1>
        <p className="text-slate-300 mb-8 text-sm leading-relaxed">
          Advanced secure tracking system. <br />
          Offline-first architecture.
        </p>

        <button
          onClick={handleStart}
          className="w-full py-4 bg-gradient-to-r from-blue-600 to-blue-500 hover:from-blue-500 hover:to-blue-400 text-white rounded-xl font-bold shadow-lg shadow-blue-500/30 active:scale-95 transition-all flex items-center justify-center gap-3 group"
        >
          <Zap className="fill-current group-hover:scale-110 transition-transform" size={20} />
          Initialize System
        </button>

        <div className="mt-6 text-[10px] text-slate-500 uppercase tracking-widest">
          v2.2 • Secure Environment
        </div>
      </GlassCard>
    </div>
  );

  // --- DASHBOARD ---
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 font-sans pb-24 relative">
      {/* App Bar */}
      <div className="bg-slate-900/80 backdrop-blur-md p-4 flex justify-between items-center sticky top-0 z-50 border-b border-white/5">
        <div className="font-bold text-lg flex items-center gap-2 text-blue-400">
          <Activity size={20} />
          <span className="bg-gradient-to-r from-blue-400 to-cyan-400 bg-clip-text text-transparent">Dashboard</span>
        </div>
        <div className={`text-[10px] font-bold px-3 py-1 rounded-full flex items-center gap-1.5 ${isOnline ? 'bg-green-500/10 text-green-400 border border-green-500/20' : 'bg-red-500/10 text-red-400 border border-red-500/20'}`}>
          <div className={`w-1.5 h-1.5 rounded-full ${isOnline ? 'bg-green-400 animate-pulse' : 'bg-red-400'}`} />
          {isOnline ? 'ONLINE' : 'OFFLINE'}
        </div>
      </div>

      <div className="p-4 space-y-4 max-w-md mx-auto">

        {/* Main Location Card */}
        <GlassCard className="relative overflow-hidden">
          <div className="absolute top-0 right-0 p-4 opacity-10">
            <MapPin size={120} />
          </div>

          <div className="relative z-10">
            <div className="flex items-center gap-2 mb-4">
              <Radio size={16} className={`text-blue-400 ${isTracking ? 'animate-ping' : ''}`} />
              <p className="text-blue-400 text-xs font-bold uppercase tracking-wider">Live Coordinates</p>
            </div>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <div className="text-xs text-slate-500 mb-1">Latitude</div>
                <div className="text-2xl font-mono text-white">{location ? location.lat.toFixed(5) : "---"}</div>
              </div>
              <div>
                <div className="text-xs text-slate-500 mb-1">Longitude</div>
                <div className="text-2xl font-mono text-white">{location ? location.lng.toFixed(5) : "---"}</div>
              </div>
            </div>

            <div className="flex items-center gap-2 text-xs text-slate-400 bg-slate-900/50 p-2 rounded-lg border border-white/5">
              <Activity size={12} />
              Accuracy: {location ? Math.round(location.acc) + 'm' : 'N/A'}
              <span className="mx-1">•</span>
              {location ? new Date(location.time).toLocaleTimeString() : "Waiting for signal..."}
            </div>
          </div>
        </GlassCard>

        {/* Sync Section */}
        <GlassCard>
          <div className="flex justify-between items-center mb-6">
            <div className="text-sm font-bold text-slate-200 flex items-center gap-2">
              <Cloud size={18} className="text-blue-400" /> Data Queue
            </div>
            <div className="bg-blue-500/20 text-blue-300 px-3 py-1 rounded-full text-xs font-bold border border-blue-500/30">
              {queue.length} Points
            </div>
          </div>

          <button
            onClick={handleSync}
            disabled={queue.length === 0}
            className="w-full py-4 bg-slate-800 hover:bg-slate-700 text-white rounded-xl font-medium flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors border border-white/10"
          >
            <Cloud size={18} /> Sync to Cloud
          </button>
        </GlassCard>

        {/* Debug Tools */}
        <div className="grid grid-cols-2 gap-3">
          <button onClick={exportLogs} className="py-4 bg-slate-900/50 border border-white/10 rounded-2xl text-xs font-medium text-slate-400 hover:bg-slate-800 hover:text-white transition-colors flex flex-col items-center justify-center gap-2">
            <FileText size={20} /> Export Logs
          </button>
          <a href={`sms:?body=My Location: ${location?.lat},${location?.lng}`} className="py-4 bg-slate-900/50 border border-white/10 rounded-2xl text-xs font-medium text-slate-400 hover:bg-slate-800 hover:text-white transition-colors flex flex-col items-center justify-center gap-2">
            <Smartphone size={20} /> SMS Location
          </a>
        </div>

      </div>
    </div>
  );
}
