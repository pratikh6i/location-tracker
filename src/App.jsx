import React, { useState, useEffect } from 'react';
import { initializeApp } from 'firebase/app';
import { getAuth, signInAnonymously, onAuthStateChanged } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';
import { MapPin, Shield, Activity, Cloud, Smartphone, FileText } from 'lucide-react';

// --- VERBOSE LOGGING SYSTEM ---
const LOGS = [];
const log = (tag, msg, data = '') => {
  // Fixed: Removed the backslashes that caused the syntax error
  const entry = `[${new Date().toLocaleTimeString()}] [${tag}] ${msg} ${data ? JSON.stringify(data) : ''}`;
  console.log(entry);
  LOGS.push(entry);
  if (LOGS.length > 500) LOGS.shift();
};

// --- REAL KEYS FROM YOUR UPLOAD ---
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID
};

let app, auth, db;
try {
  app = initializeApp(firebaseConfig);
  auth = getAuth(app);
  db = getFirestore(app);
} catch (e) {
  log("INIT_ERR", e.message);
}

export default function App() {
  const [step, setStep] = useState('welcome');
  const [user, setUser] = useState(null);
  const [location, setLocation] = useState(null);
  const [queue, setQueue] = useState([]);
  const [isOnline, setIsOnline] = useState(navigator.onLine);

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

  // 2. Start Tracking (Only when in dashboard)
  useEffect(() => {
    if (step !== 'dashboard') return;

    log('GPS', 'Starting tracking loop (5 min interval)');
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
        err => log('GPS_ERR', err.message)
      );
    };
    track();
    const interval = setInterval(track, 300000);
    return () => clearInterval(interval);
  }, [step]);

  const handleStart = async () => {
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

    log('SYNC', `Attempting to sync ${queue.length} items...`);
    // In a real app, this goes to Firestore.
    // For now, we simulate success to clear queue.
    try {
      // Batch upload or single upload depending on preference. 
      // Here we upload each item. In production, use a batch write.
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

  // --- WELCOME SCREEN ---
  if (step === 'welcome') return (
    <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center p-6 text-center font-sans">
      <div className="w-20 h-20 bg-blue-100 rounded-full flex items-center justify-center mb-6 text-blue-600">
        <MapPin size={40} />
      </div>
      <h1 className="text-2xl font-bold text-slate-800 mb-2">Location Keeper</h1>
      <p className="text-slate-500 mb-8 max-w-xs">
        Securely track your device location.
      </p>
      <button onClick={handleStart} className="w-full py-4 bg-blue-600 text-white rounded-xl font-medium shadow-lg shadow-blue-200 active:scale-95 transition-transform flex items-center justify-center gap-2">
        <Shield size={20} /> Grant Permissions & Start
      </button>
    </div>
  );

  // --- DASHBOARD ---
  return (
    <div className="min-h-screen bg-slate-100 font-sans text-slate-800 pb-20">
      <div className="bg-white p-4 shadow-sm flex justify-between items-center sticky top-0 z-10">
        <div className="font-bold text-lg flex items-center gap-2">
          <Activity className="text-blue-600" /> Dashboard
        </div>
        <div className={`text - xs font - bold px - 2 py - 1 rounded ${isOnline ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
          {isOnline ? 'ONLINE' : 'OFFLINE'}
        </div>
      </div>

      <div className="p-4 space-y-4">
        <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-200 text-center">
          <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-2">Current Coordinates</p>
          <div className="text-3xl font-light text-slate-700 mb-1">
            {location ? location.lat.toFixed(4) : "Waiting..."}
          </div>
          <div className="text-3xl font-light text-slate-700">
            {location ? location.lng.toFixed(4) : "Waiting..."}
          </div>
        </div>

        <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-200">
          <div className="flex justify-between items-center mb-4">
            <div className="text-sm font-bold text-slate-700">Data Queue</div>
            <div className="bg-blue-50 text-blue-700 px-3 py-1 rounded-full text-xs font-bold">
              {queue.length} Items
            </div>
          </div>
          <button onClick={handleSync} disabled={queue.length === 0} className="w-full py-3 bg-slate-800 text-white rounded-xl font-medium flex items-center justify-center gap-2 disabled:opacity-50">
            <Cloud size={18} /> Sync to Cloud
          </button>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <button onClick={exportLogs} className="py-3 bg-white border border-slate-200 rounded-xl text-xs font-medium text-slate-600 flex flex-col items-center justify-center gap-1">
            <FileText size={16} /> Export Logs
          </button>
          <a href={`sms:?body = My Location: ${location?.lat}, ${location?.lng}`} className="py-3 bg-white border border-slate-200 rounded-xl text-xs font-medium text-slate-600 flex flex-col items-center justify-center gap-1">
            <Smartphone size={16} /> SMS Location
          </a>
        </div>
      </div>
    </div>
  );
}
