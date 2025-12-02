import React, { useState, useEffect } from 'react';
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
  MapPin, Wifi, WifiOff, Settings, LogOut, Smartphone, Activity, AlertCircle, CheckCircle, Shield
} from 'lucide-react';

// ==========================================
// ⚠️ ACTION REQUIRED: REPLACE THESE KEYS!
// ==========================================
const firebaseConfig = {
  apiKey: "REPLACE_WITH_YOUR_API_KEY",
  authDomain: "REPLACE_WITH_YOUR_PROJECT_ID.firebaseapp.com",
  projectId: "REPLACE_WITH_YOUR_PROJECT_ID",
  storageBucket: "REPLACE_WITH_YOUR_PROJECT_ID.firebasestorage.app",
  messagingSenderId: "123456789",
  appId: "1:123456789:web:abcdef"
};

// Initialize Firebase safely
let app, auth, db;
try {
  app = initializeApp(firebaseConfig);
  auth = getAuth(app);
  db = getFirestore(app);
} catch (e) {
  console.error("Firebase Init Error", e);
}

// --- BEAUTIFUL UI COMPONENTS ---

const Card = ({ children, className = "" }) => (
  <div className={`bg-white rounded-[24px] shadow-sm border border-gray-100 p-6 ${className}`}>
    {children}
  </div>
);

const Button = ({ onClick, children, variant = "primary", icon: Icon, className = "" }) => {
  const baseStyle = "w-full py-4 rounded-[16px] font-medium flex items-center justify-center gap-3 transition-all active:scale-95";
  const variants = {
    primary: "bg-[#1a73e8] text-white shadow-md shadow-blue-200",
    secondary: "bg-[#e8f0fe] text-[#1a73e8]",
    outline: "border border-gray-200 text-gray-600 hover:bg-gray-50",
    danger: "bg-red-50 text-red-600"
  };
  return (
    <button onClick={onClick} className={`${baseStyle} ${variants[variant]} ${className}`}>
      {Icon && <Icon size={20} />}
      {children}
    </button>
  );
};

const StatusBadge = ({ isOnline }) => (
  <div className={`inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium ${
    isOnline ? 'bg-green-50 text-green-700' : 'bg-orange-50 text-orange-700'
  }`}>
    <div className={`w-2 h-2 rounded-full ${isOnline ? 'bg-green-500' : 'bg-orange-500 animate-pulse'}`} />
    {isOnline ? 'Online' : 'Offline Mode'}
  </div>
);

// --- MAIN LOGIC ---

export default function App() {
  const [user, setUser] = useState(null);
  const [activeTab, setActiveTab] = useState('dashboard');
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [lastLocation, setLastLocation] = useState(null);
  const [locationQueue, setLocationQueue] = useState([]);
  const [settings, setSettings] = useState({ smsNumber: '', offlineThresholdHours: 2 });
  const [errorMsg, setErrorMsg] = useState(null);
  const [loading, setLoading] = useState(true);

  // Auth Listener
  useEffect(() => {
    if (!auth) return;
    const unsub = onAuthStateChanged(auth, (u) => {
      setUser(u);
      setLoading(false);
    });
    return () => unsub();
  }, []);

  // Sync Logic
  useEffect(() => {
    const handleStatus = () => setIsOnline(navigator.onLine);
    window.addEventListener('online', handleStatus);
    window.addEventListener('offline', handleStatus);
    return () => { window.removeEventListener('online', handleStatus); window.removeEventListener('offline', handleStatus); };
  }, []);

  // Tracking Logic
  useEffect(() => {
    if (!user) return;
    
    // Load local queue
    const savedQueue = localStorage.getItem(`queue_${user.uid}`);
    if (savedQueue) setLocationQueue(JSON.parse(savedQueue));

    const track = () => {
      if (!navigator.geolocation) return;
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const log = {
            lat: pos.coords.latitude,
            lng: pos.coords.longitude,
            acc: pos.coords.accuracy,
            time: new Date().toISOString()
          };
          setLastLocation(log);
          
          if (navigator.onLine) {
             // Here we would sync to Firestore
             console.log("Syncing", log);
          } else {
             setLocationQueue(prev => {
               const newQ = [...prev, log];
               localStorage.setItem(`queue_${user.uid}`, JSON.stringify(newQ));
               return newQ;
             });
          }
        },
        (err) => console.error(err),
        { enableHighAccuracy: true }
      );
    };

    track(); // Immediate
    const interval = setInterval(track, 300000); // 5 mins
    return () => clearInterval(interval);
  }, [user]);

  const handleLogin = async () => {
    setErrorMsg(null);
    try {
      const provider = new GoogleAuthProvider();
      await signInWithPopup(auth, provider);
    } catch (err) {
      console.error(err);
      setErrorMsg(err.message); // Show exact error
    }
  };

  const handleGuestLogin = async () => {
    try {
      await signInAnonymously(auth);
    } catch (err) {
      setErrorMsg(err.message);
    }
  };

  const generateSMSLink = () => {
    if (!lastLocation) return "#";
    const body = `HELP: Last Known Loc: ${lastLocation.lat},${lastLocation.lng} at ${new Date(lastLocation.time).toLocaleTimeString()}`;
    return `sms:${settings.smsNumber}?body=${encodeURIComponent(body)}`;
  };

  if (loading) return <div className="h-screen flex items-center justify-center text-[#1a73e8]">Loading...</div>;

  // --- LOGIN SCREEN ---
  if (!user) return (
    <div className="min-h-screen bg-white flex flex-col p-6 items-center justify-center text-center">
      <div className="w-20 h-20 bg-blue-50 rounded-[28px] flex items-center justify-center mb-8">
        <MapPin className="text-[#1a73e8] w-10 h-10" />
      </div>
      <h1 className="text-3xl font-bold text-gray-900 mb-2 font-sans tracking-tight">Location Sync</h1>
      <p className="text-gray-500 mb-10 text-lg">Secure, offline-first tracking.</p>
      
      {errorMsg && (
        <div className="w-full bg-red-50 text-red-600 p-4 rounded-xl text-sm mb-6 text-left border border-red-100 flex gap-2">
           <AlertCircle className="shrink-0 w-5 h-5"/>
           {errorMsg}
        </div>
      )}

      <div className="w-full space-y-4 max-w-sm">
        <Button onClick={handleLogin} icon={Shield}>
          Sign in with Google
        </Button>
        <Button onClick={handleGuestLogin} variant="secondary">
          Continue as Guest
        </Button>
      </div>
    </div>
  );

  // --- DASHBOARD ---
  return (
    <div className="min-h-screen bg-[#F0F4F8] pb-24 font-sans text-gray-800">
      
      {/* Header */}
      <header className="bg-white px-6 py-5 shadow-sm flex justify-between items-center sticky top-0 z-10">
        <span className="font-bold text-xl text-gray-700 tracking-tight">Dashboard</span>
        <button onClick={() => signOut(auth)} className="bg-gray-100 p-2 rounded-full text-gray-600 hover:bg-red-50 hover:text-red-500 transition-colors">
          <LogOut size={20} />
        </button>
      </header>

      <main className="p-4 space-y-5 max-w-lg mx-auto mt-2">
        
        {activeTab === 'dashboard' ? (
          <>
            {/* Status Card */}
            <Card className="flex flex-col items-center text-center py-10 relative overflow-hidden">
               <div className={`absolute top-0 left-0 w-full h-1 ${isOnline ? 'bg-green-500' : 'bg-orange-500'}`} />
               
               <StatusBadge isOnline={isOnline} />
               
               <div className="mt-8 mb-2 text-5xl font-mono font-light text-gray-800 tracking-tighter">
                 {lastLocation ? (
                   <>
                     {lastLocation.lat.toFixed(4)}
                     <span className="text-gray-300 text-3xl mx-2">/</span>
                     {lastLocation.lng.toFixed(4)}
                   </>
                 ) : (
                   <span className="text-gray-300 text-2xl">Locating...</span>
                 )}
               </div>
               <p className="text-gray-400 text-sm">Latitude / Longitude</p>

               {!isOnline && (
                 <div className="mt-6 flex items-center gap-2 text-orange-600 bg-orange-50 px-4 py-2 rounded-lg text-sm font-medium">
                   <Activity size={16} />
                   {locationQueue.length} Logs Queued
                 </div>
               )}
            </Card>

            {/* Actions */}
            <div className="grid grid-cols-2 gap-4">
               <a href={generateSMSLink()} className="col-span-2">
                 <Button variant="outline" icon={Smartphone} className="bg-white border-none shadow-sm text-gray-700 justify-between px-6">
                   Send SMS Alert
                 </Button>
               </a>
            </div>
          </>
        ) : (
          /* Settings Tab */
          <div className="space-y-4">
             <Card>
               <h2 className="text-lg font-bold mb-4 flex items-center gap-2">
                 <Settings size={20} className="text-gray-400"/> Configuration
               </h2>
               <div className="space-y-4">
                 <div>
                   <label className="text-xs font-bold text-gray-400 uppercase tracking-wider">Emergency Number</label>
                   <input 
                     type="tel" 
                     placeholder="+1 234..."
                     className="w-full mt-2 p-4 bg-gray-50 rounded-xl outline-none focus:ring-2 focus:ring-blue-100 transition-all"
                     value={settings.smsNumber}
                     onChange={e => setSettings({...settings, smsNumber: e.target.value})}
                   />
                 </div>
                 <Button onClick={() => setActiveTab('dashboard')}>Save & Close</Button>
               </div>
             </Card>
          </div>
        )}

      </main>

      {/* Bottom Nav */}
      <nav className="fixed bottom-0 w-full bg-white border-t border-gray-100 flex justify-around p-4 z-20 pb-safe">
        <button 
          onClick={() => setActiveTab('dashboard')} 
          className={`p-3 rounded-2xl transition-colors ${activeTab === 'dashboard' ? 'bg-blue-50 text-blue-600' : 'text-gray-400'}`}
        >
          <Activity size={24} />
        </button>
        <button 
          onClick={() => setActiveTab('settings')} 
          className={`p-3 rounded-2xl transition-colors ${activeTab === 'settings' ? 'bg-blue-50 text-blue-600' : 'text-gray-400'}`}
        >
          <Settings size={24} />
        </button>
      </nav>

    </div>
  );
}
