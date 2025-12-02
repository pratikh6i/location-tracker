import React, { useState, useEffect } from 'react';
import { initializeApp } from 'firebase/app';
import { getAuth, signInAnonymously, onAuthStateChanged } from 'firebase/auth';
import { getFirestore, collection, addDoc } from 'firebase/firestore';
import { MapPin, Activity, Cloud, Trash2, RefreshCw, Play, Square } from 'lucide-react';

// --- CONFIGURATION (HARDCODED FOR RELIABILITY) ---
const firebaseConfig = {
  apiKey: "AIzaSyBTnNBYbXE3k2YXcOI-7Mbf6-eT0K5rSog",
  authDomain: "android-location-tracker-4fe65.firebaseapp.com",
  projectId: "android-location-tracker-4fe65",
  storageBucket: "android-location-tracker-4fe65.firebasestorage.app",
  messagingSenderId: "361729564870",
  appId: "1:361729564870:android:938b93f0519ae31540f7c7"
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
  app = initializeApp(firebaseConfig);
  auth = getAuth(app);
  db = getFirestore(app);
  log("INIT", "Firebase initialized");
} catch (e) {
  console.error("Firebase Init Error:", e);
  log("INIT_ERR", e.message);
}

export default function App() {
  const [step, setStep] = useState('welcome');
  const [user, setUser] = useState(null);
  const [location, setLocation] = useState(null);
  const [queue, setQueue] = useState([]); // Pending upload
  const [history, setHistory] = useState([]); // Local history for table
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [isTracking, setIsTracking] = useState(false);

  // 1. Auth & Network
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

    // Load data
    const savedQueue = localStorage.getItem('offline_queue');
    if (savedQueue) setQueue(JSON.parse(savedQueue));

    const savedHistory = localStorage.getItem('loc_history');
    if (savedHistory) setHistory(JSON.parse(savedHistory));

    return () => { unsub(); window.removeEventListener('online', handleNet); window.removeEventListener('offline', handleNet); };
  }, []);

  // 2. Tracking Logic
  useEffect(() => {
    if (!isTracking) return;

    log('GPS', 'Tracking active');

    const track = () => {
      if (!navigator.geolocation) return log('GPS', 'Not supported');

      navigator.geolocation.getCurrentPosition(
        pos => {
          const now = new Date();
          const point = {
            lat: pos.coords.latitude,
            lng: pos.coords.longitude,
            acc: pos.coords.accuracy,
            time: now.toISOString(),
            displayTime: now.toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' }) // IST
          };

          setLocation(point);
          log('GPS', 'Captured', point);

          // Add to Queue (for Sync)
          setQueue(prev => {
            const next = [...prev, point];
            localStorage.setItem('offline_queue', JSON.stringify(next));
            return next;
          });

          // Add to History (for Table)
          setHistory(prev => {
            const next = [point, ...prev].slice(0, 50); // Keep last 50
            localStorage.setItem('loc_history', JSON.stringify(next));
            return next;
          });
        },
        err => log('GPS_ERR', err.message),
        { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
      );
    };

    track(); // Immediate
    const interval = setInterval(track, 300000); // 5 mins
    return () => clearInterval(interval);
  }, [isTracking]);

  const handleStart = async () => {
    try {
      if (auth) await signInAnonymously(auth);
      setStep('dashboard');
      setIsTracking(true);
    } catch (e) {
      alert('Auth Failed: ' + e.message);
    }
  };

  const handleStop = () => {
    setIsTracking(false);
  };

  const handleSync = async () => {
    if (queue.length === 0) return alert("No data to sync");
    if (!isOnline) return alert("No Internet");
    if (!db) return alert("Database error");

    try {
      const promises = queue.map(item => addDoc(collection(db, 'logs'), item));
      await Promise.all(promises);
      setQueue([]);
      localStorage.removeItem('offline_queue');
      alert("Synced Successfully!");
    } catch (e) {
      alert("Sync Failed: " + e.message);
    }
  };

  const clearHistory = () => {
    if (confirm("Clear local history?")) {
      setHistory([]);
      localStorage.removeItem('loc_history');
    }
  };

  // --- UI ---

  if (step === 'welcome') return (
    <div className="min-h-screen bg-white flex flex-col items-center justify-center p-6 text-center font-sans text-slate-900">
      <div className="mb-6 p-4 bg-blue-100 rounded-full text-blue-600">
        <MapPin size={48} />
      </div>
      <h1 className="text-3xl font-bold mb-2">Location Tracker</h1>
      <p className="text-slate-500 mb-8">Simple. Accurate. Secure.</p>
      <button
        onClick={handleStart}
        className="w-full max-w-xs py-4 bg-blue-600 text-white rounded-lg font-bold shadow-md active:scale-95 transition-transform"
      >
        Start Tracking
      </button>
    </div>
  );

  return (
    <div className="min-h-screen bg-slate-50 font-sans text-slate-900 pb-20">
      {/* Header */}
      <div className="bg-white border-b border-slate-200 p-4 sticky top-0 z-10 flex justify-between items-center shadow-sm">
        <div className="font-bold text-lg flex items-center gap-2">
          <Activity className={isTracking ? "text-green-600 animate-pulse" : "text-slate-400"} />
          Tracker
        </div>
        <div className={`px-3 py-1 rounded-full text-xs font-bold ${isOnline ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
          {isOnline ? 'ONLINE' : 'OFFLINE'}
        </div>
      </div>

      <div className="p-4 max-w-2xl mx-auto space-y-6">

        {/* Controls */}
        <div className="bg-white p-4 rounded-xl shadow-sm border border-slate-200 flex gap-3">
          <button
            onClick={() => setIsTracking(!isTracking)}
            className={`flex-1 py-3 rounded-lg font-bold flex items-center justify-center gap-2 ${isTracking ? 'bg-red-50 text-red-600 border border-red-200' : 'bg-green-600 text-white shadow-md'}`}
          >
            {isTracking ? <><Square size={18} fill="currentColor" /> Stop</> : <><Play size={18} fill="currentColor" /> Start</>}
          </button>

          <button
            onClick={handleSync}
            disabled={queue.length === 0}
            className="flex-1 py-3 bg-blue-50 text-blue-700 border border-blue-200 rounded-lg font-bold flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <Cloud size={18} /> Sync ({queue.length})
          </button>
        </div>

        {/* Current Status */}
        <div className="bg-white p-6 rounded-xl shadow-sm border border-slate-200 text-center">
          <div className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">Last Known Location</div>
          <div className="text-4xl font-light text-slate-800 mb-1">
            {location ? location.lat.toFixed(5) : "--.----"}
          </div>
          <div className="text-4xl font-light text-slate-800 mb-4">
            {location ? location.lng.toFixed(5) : "--.----"}
          </div>
          <div className="inline-block bg-slate-100 px-3 py-1 rounded-full text-xs text-slate-500">
            {location ? location.displayTime : "Waiting for update..."}
          </div>
        </div>

        {/* Data Table */}
        <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <div className="p-4 border-b border-slate-100 flex justify-between items-center bg-slate-50">
            <h3 className="font-bold text-slate-700">History Log</h3>
            <button onClick={clearHistory} className="text-red-500 p-1 hover:bg-red-50 rounded">
              <Trash2 size={16} />
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-100">
                <tr>
                  <th className="px-4 py-3">Time (IST)</th>
                  <th className="px-4 py-3">Lat</th>
                  <th className="px-4 py-3">Lng</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {history.length === 0 ? (
                  <tr>
                    <td colSpan="3" className="px-4 py-8 text-center text-slate-400 italic">
                      No data recorded yet.
                    </td>
                  </tr>
                ) : (
                  history.map((row, i) => (
                    <tr key={i} className="hover:bg-slate-50">
                      <td className="px-4 py-3 font-medium text-slate-700 whitespace-nowrap">
                        {row.displayTime}
                      </td>
                      <td className="px-4 py-3 text-slate-600">{row.lat.toFixed(5)}</td>
                      <td className="px-4 py-3 text-slate-600">{row.lng.toFixed(5)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

      </div>
    </div>
  );
}
