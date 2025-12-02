import React, { useState, useEffect, useRef, useCallback } from 'react';
import { initializeApp } from 'firebase/app';
import { 
  getAuth, 
  signInWithPopup, 
  GoogleAuthProvider, 
  onAuthStateChanged, 
  signOut,
  signInAnonymously
} from 'firebase/auth';
import { 
  getFirestore, 
  doc, 
  setDoc, 
  onSnapshot
} from 'firebase/firestore';
import { 
  MapPin, Settings, LogOut, Smartphone, Activity, AlertCircle, Shield, FileText, Share2, Wifi, WifiOff
} from 'lucide-react';

// --- VERBOSE LOGGING SYSTEM ---
// This system captures every action in memory to help debug errors
const LOG_BUFFER = [];
const addLog = (type, message, data = null) => {
  const timestamp = new Date().toISOString();
  const logEntry = `[${timestamp}] [${type}] ${message} ${data ? JSON.stringify(data) : ''}`;
  console.log(logEntry); // Also print to console
  LOG_BUFFER.push(logEntry);
  // Keep buffer size manageable (last 1000 lines)
  if (LOG_BUFFER.length > 1000) LOG_BUFFER.shift();
};

// --- FIREBASE CONFIGURATION (Auto-Filled from your JSON) ---
const firebaseConfig = {
  apiKey: "AIzaSyBTnNBYbXE3k2YXcOI-7Mbf6-eT0K5rSog",
  authDomain: "android-location-tracker-4fe65.firebaseapp.com",
  projectId: "android-location-tracker-4fe65",
  storageBucket: "android-location-tracker-4fe65.firebasestorage.app",
  messagingSenderId: "361729564870",
  appId: "1:361729564870:android:938b93f0519ae31540f7c7"
};

// Initialize Firebase safely
let app, auth, db;
try {
  addLog("INIT", "Initializing Firebase...");
  app = initializeApp(firebaseConfig);
  auth = getAuth(app);
  db = getFirestore(app);
  addLog("INIT", "Firebase Initialized Successfully");
} catch (e) {
  addLog("FATAL", "Firebase Init Failed", e.message);
}

// --- BEAUTIFUL UI COMPONENTS ---

const Card = ({ children, className = "" }) => (
  <div className={`bg-white rounded-[20px] shadow-sm border border-slate-100 p-6 ${className}`}>
    {children}
  </div>
);

const Button = ({ onClick, children, variant = "primary", icon: Icon, className = "" }) => {
  const baseStyle = "w-full py-4 rounded-[14px] font-medium flex items-center justify-center gap-2.5 transition-all active:scale-95 text-[15px]";
  const variants = {
    primary: "bg-[#1a73e8] text-white shadow-md shadow-blue-600/20 hover:bg-[#1557b0]",
    secondary: "bg-[#e8f0fe] text-[#1a73e8] hover:bg-[#d2e3fc]",
    outline: "border border-slate-200 text-slate-600 hover:bg-slate-50",
    danger: "bg-red-50 text-red-600 border border-red-100"
  };
  return (
    <button onClick={onClick} className={`${baseStyle} ${variants[variant]} ${className}`}>
      {Icon && <Icon size={18} strokeWidth={2.5} />}
      {children}
    </button>
  );
};

// --- MAIN APPLICATION ---

export default function App() {
  const [user, setUser] = useState(null);
  const [activeTab, setActiveTab] = useState('dashboard');
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [lastLocation, setLastLocation] = useState(null);
  const [locationQueue, setLocationQueue] = useState([]);
  const [settings, setSettings] = useState({ smsNumber: '' });
  const [errorMsg, setErrorMsg] = useState(null);
  const [loading, setLoading] = useState(true);
  
  // Debug State
  const [showDebug, setShowDebug] = useState(false);

  // Auth Listener
  useEffect(() => {
    if (!auth) return;
    addLog("AUTH", "Setting up auth listener");
    const unsub = onAuthStateChanged(auth, (u) => {
      if (u) {
        addLog("AUTH", "User Authenticated", { uid: u.uid, isAnonymous: u.isAnonymous });
        setUser(u);
      } else {
        addLog("AUTH", "User Signed Out");
        setUser(null);
      }
      setLoading(false);
    });
    return () => unsub();
  }, []);

  // Network Monitor
  useEffect(() => {
    const handleStatus = () => {
      const status = navigator.onLine;
      setIsOnline(status);
      addLog("NETWORK", "Connection Status Changed", { online: status });
    };
    window.addEventListener('online', handleStatus);
    window.addEventListener('offline', handleStatus);
    return () => { window.removeEventListener('online', handleStatus); window.removeEventListener('offline', handleStatus); };
  }, []);

  // Tracking Logic
  useEffect(() => {
    if (!user) return;
    
    // Load local queue
    try {
      const savedQueue = localStorage.getItem(`queue_${user.uid}`);
      if (savedQueue) {
        const parsed = JSON.parse(savedQueue);
        setLocationQueue(parsed);
        addLog("STORAGE", "Loaded offline queue", { count: parsed.length });
      }
    } catch (e) {
      addLog("ERROR", "Failed to load local queue", e.message);
    }

    const track = () => {
      if (!navigator.geolocation) {
        addLog("GPS", "Geolocation not supported");
        return;
      }
      
      addLog("GPS", "Requesting current position...");
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const log = {
            lat: pos.coords.latitude,
            lng: pos.coords.longitude,
            acc: pos.coords.accuracy,
            time: new Date().toISOString()
          };
          setLastLocation(log);
          addLog("GPS", "Location captured", { lat: log.lat, lng: log.lng });
          
          if (navigator.onLine) {
             // Simulate Sync
             addLog("SYNC", "Uploading location to cloud...");
          } else {
             setLocationQueue(prev => {
               const newQ = [...prev, log];
               localStorage.setItem(`queue_${user.uid}`, JSON.stringify(newQ));
               addLog("OFFLINE", "Queued location locally", { queueSize: newQ.length });
               return newQ;
             });
          }
        },
        (err) => {
          addLog("GPS_ERROR", "Failed to get location", { code: err.code, msg: err.message });
          setErrorMsg(`GPS Error: ${err.message}`);
        },
        { enableHighAccuracy: true, timeout: 15000, maximumAge: 10000 }
      );
    };

    track(); // Immediate
    const interval = setInterval(track, 300000); // 5 mins
    return () => clearInterval(interval);
  }, [user]);

  const handleLogin = async () => {
    setErrorMsg(null);
    addLog("AUTH", "Attempting Google Sign In...");
    try {
      const provider = new GoogleAuthProvider();
      await signInWithPopup(auth, provider);
      addLog("AUTH", "Google Sign In Success");
    } catch (err) {
      addLog("AUTH_ERROR", "Google Sign In Failed", { code: err.code, msg: err.message });
      setErrorMsg(err.message);
    }
  };

  const handleGuestLogin = async () => {
    setErrorMsg(null);
    addLog("AUTH", "Attempting Guest Login...");
    try {
      await signInAnonymously(auth);
      addLog("AUTH", "Guest Login Success");
    } catch (err) {
      addLog("AUTH_ERROR", "Guest Login Failed", err.message);
      setErrorMsg(err.message);
    }
  };

  const exportLogs = () => {
    const blob = new Blob([LOG_BUFFER.join('\n')], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `debug_logs_${new Date().getTime()}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    addLog("SYSTEM", "Logs exported by user");
  };

  const generateSMSLink = () => {
    if (!lastLocation) return "#";
    const body = `HELP: Last Known Loc: ${lastLocation.lat},${lastLocation.lng} at ${new Date(lastLocation.time).toLocaleTimeString()}`;
    return `sms:${settings.smsNumber}?body=${encodeURIComponent(body)}`;
  };

  if (loading) return (
    <div className="h-screen flex flex-col items-center justify-center bg-slate-50 text-[#1a73e8] gap-4">
      <div className="w-8 h-8 border-4 border-current border-t-transparent rounded-full animate-spin" />
      <span className="font-medium">Initializing Secure Environment...</span>
    </div>
  );

  // --- LOGIN SCREEN ---
  if (!user) return (
    <div className="min-h-screen bg-slate-50 flex flex-col px-6 items-center justify-center font-sans">
      <div className="w-full max-w-sm">
        <div className="text-center mb-10">
          <div className="w-20 h-20 bg-white rounded-[24px] shadow-sm flex items-center justify-center mx-auto mb-6 border border-slate-100">
            <MapPin className="text-[#1a73e8] w-10 h-10" />
          </div>
          <h1 className="text-2xl font-bold text-slate-800 mb-2">Location Sync</h1>
          <p className="text-slate-500">Secure, offline-first tracking.</p>
        </div>
        
        {errorMsg && (
          <div className="bg-red-50 text-red-700 p-4 rounded-[16px] text-sm mb-6 border border-red-100 flex gap-3 items-start">
             <AlertCircle className="shrink-0 w-5 h-5 mt-0.5"/>
             <div className="flex-1 break-words">{errorMsg}</div>
          </div>
        )}

        <div className="space-y-3">
          <Button onClick={handleLogin} icon={Shield}>
            Sign in with Google
          </Button>
          <Button onClick={handleGuestLogin} variant="secondary">
            Continue as Guest
          </Button>
        </div>

        <div className="mt-8 text-center">
          <button onClick={exportLogs} className="text-xs text-slate-400 underline flex items-center justify-center gap-1 mx-auto">
            <FileText size={12}/> Download Debug Logs
          </button>
        </div>
      </div>
    </div>
  );

  // --- DASHBOARD ---
  return (
    <div className="min-h-screen bg-[#F8FAFC] pb-32 font-sans text-slate-800">
      
      {/* Header */}
      <header className="bg-white px-6 py-4 shadow-sm border-b border-slate-100 flex justify-between items-center sticky top-0 z-10">
        <div className="flex items-center gap-2">
          <MapPin className="text-[#1a73e8]" size={20} />
          <span className="font-bold text-lg text-slate-700">Location Sync</span>
        </div>
        <button onClick={() => signOut(auth)} className="bg-slate-50 p-2 rounded-full text-slate-500 hover:text-red-600 transition-colors">
          <LogOut size={20} />
        </button>
      </header>

      <main className="p-5 max-w-lg mx-auto space-y-5">
        
        {activeTab === 'dashboard' ? (
          <>
            {/* Status Card */}
            <Card className="flex flex-col items-center text-center py-8 relative overflow-hidden">
               <div className={`absolute top-4 right-4 flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold ${
                 isOnline ? 'bg-green-100 text-green-700' : 'bg-orange-100 text-orange-700'
               }`}>
                 {isOnline ? <Wifi size={12} /> : <WifiOff size={12} />}
                 {isOnline ? 'ONLINE' : 'OFFLINE'}
               </div>
               
               <div className="mt-4 mb-2">
                 <span className="text-4xl font-light tracking-tight text-slate-700">
                    {lastLocation ? lastLocation.lat.toFixed(4) : "---"}
                 </span>
                 <span className="mx-2 text-slate-300 text-2xl">|</span>
                 <span className="text-4xl font-light tracking-tight text-slate-700">
                    {lastLocation ? lastLocation.lng.toFixed(4) : "---"}
                 </span>
               </div>
               <p className="text-slate-400 text-sm font-medium tracking-wide uppercase">Current Coordinates</p>

               <div className="mt-8 grid grid-cols-2 gap-4 w-full">
                 <div className="bg-slate-50 rounded-2xl p-3">
                   <div className="text-xs text-slate-400 font-bold uppercase mb-1">Queue</div>
                   <div className="text-xl font-semibold text-slate-700">{locationQueue.length}</div>
                 </div>
                 <div className="bg-slate-50 rounded-2xl p-3">
                    <div className="text-xs text-slate-400 font-bold uppercase mb-1">Accuracy</div>
                    <div className="text-xl font-semibold text-slate-700">{lastLocation ? Math.round(lastLocation.acc) + 'm' : '-'}</div>
                 </div>
               </div>
            </Card>

            {/* Actions */}
            <div className="space-y-3">
               <a href={generateSMSLink()} className="block">
                 <Button variant="outline" icon={Smartphone} className="bg-white justify-between px-6 text-slate-700">
                   Send SMS Alert
                 </Button>
               </a>
               
               <Button 
                 variant="secondary" 
                 icon={FileText} 
                 onClick={exportLogs}
                 className="bg-slate-100 text-slate-600 hover:bg-slate-200"
               >
                 Export Debug Logs
               </Button>
            </div>
          </>
        ) : (
          /* Settings Tab */
          <div className="space-y-4">
             <Card>
               <h2 className="text-lg font-bold mb-6 flex items-center gap-2 text-slate-700">
                 <Settings size={20} className="text-slate-400"/> Configuration
               </h2>
               
               <div className="space-y-4">
                 <div>
                   <label className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2 block">Emergency SMS Number</label>
                   <input 
                     type="tel" 
                     placeholder="+1 234 567 8900"
                     className="w-full p-4 bg-slate-50 border border-slate-200 rounded-xl outline-none focus:border-[#1a73e8] focus:ring-1 focus:ring-[#1a73e8] transition-all font-medium text-slate-700 placeholder:text-slate-400"
                     value={settings.smsNumber}
                     onChange={e => setSettings({...settings, smsNumber: e.target.value})}
                   />
                 </div>
                 <div className="pt-4">
                   <Button onClick={() => setActiveTab('dashboard')}>Save Configuration</Button>
                 </div>
               </div>
             </Card>

             <Card className="bg-slate-50 border-none">
                <div className="text-xs text-slate-400 font-mono break-all">
                  APP_ID: {firebaseConfig.appId}<br/>
                  PKG: com.pratikh6i.locationkeeper
                </div>
             </Card>
          </div>
        )}

      </main>

      {/* Bottom Nav */}
      <nav className="fixed bottom-0 w-full bg-white border-t border-slate-100 flex justify-around p-3 z-20 pb-safe shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.02)]">
        <button 
          onClick={() => setActiveTab('dashboard')} 
          className={`flex flex-col items-center gap-1 p-2 rounded-xl transition-all w-20 ${activeTab === 'dashboard' ? 'text-[#1a73e8]' : 'text-slate-400 hover:text-slate-600'}`}
        >
          <Activity size={24} strokeWidth={activeTab === 'dashboard' ? 2.5 : 2} />
          <span className="text-[10px] font-medium">Track</span>
        </button>
        <button 
          onClick={() => setActiveTab('settings')} 
          className={`flex flex-col items-center gap-1 p-2 rounded-xl transition-all w-20 ${activeTab === 'settings' ? 'text-[#1a73e8]' : 'text-slate-400 hover:text-slate-600'}`}
        >
          <Settings size={24} strokeWidth={activeTab === 'settings' ? 2.5 : 2} />
          <span className="text-[10px] font-medium">Config</span>
        </button>
      </nav>

    </div>
  );
}
