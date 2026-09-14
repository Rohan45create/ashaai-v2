import { useJsApiLoader, GoogleMap, Circle, InfoWindow, Marker } from '@react-google-maps/api';
import { useEffect, useState } from 'react';
import { useAuthStore } from '../../stores/authStore';
import { useTx } from '../../context/TranslationContext';
import { useConfigStore } from '../../utils/configStore';
import { apiFetch } from '../../utils/api';

const CoverageMap = () => {
  const [villages, setVillages]           = useState([]);
  const [activeAshas, setActiveAshas]     = useState([]);
  const [selectedVillage, setSelectedVillage] = useState(null);
  const [loadError, setLoadError]         = useState(null);
  const [mapType, setMapType]             = useState('roadmap');
  const [mapCenter, setMapCenter]         = useState({ lat: 18.99, lng: 75.76 });
  const tx = useTx();

  const { headId: storeHeadId } = useAuthStore();
  const headId = storeHeadId || localStorage.getItem('headId') || 'head_sunita_001';

  const googleMapsApiKey = useConfigStore((state) => state.googleMapsApiKey);

  const { isLoaded } = useJsApiLoader({
    googleMapsApiKey: googleMapsApiKey || '',
    onError: (e) => setLoadError(e.message)
  });

  useEffect(() => {
    if (!headId) return;

    let mounted = true;

    const loadData = async () => {
      try {
        // Replace firestore onSnapshot with API fetch
        // Assuming Spring Boot exposes this later
        const data = await apiFetch(`/api/admin/map-data?headId=${headId}`);
        if (mounted) {
          setVillages(data.villages || []);
          setActiveAshas(data.activeAshas || []);
          if (data.mapCenter) {
            setMapCenter(data.mapCenter);
          }
        }
      } catch (err) {
        console.error('Failed to load map data:', err);
      }
    };

    loadData();

    // Polling every 30s as a replacement for onSnapshot
    const intervalId = setInterval(loadData, 30000);

    return () => {
      mounted = false;
      clearInterval(intervalId);
    };
  }, [headId]);

  if (loadError) return (
    <div className="p-4 md:p-8">
      <h1 className="text-2xl md:text-3xl font-bold mb-6">{tx('Coverage Map', 'coverage_map')}</h1>
      <div className="bg-[#FFF8E1] border border-[#FFCA28] rounded-2xl p-6">
        <p className="text-[#5D4037] font-medium flex items-center gap-1"><span className="material-symbols-outlined text-[20px]">warning</span> {tx('Map failed to load')}: {loadError}</p>
        <p className="text-sm text-gray-600 mt-2">{tx('Check your')} <code>VITE_GOOGLE_MAPS_API_KEY</code> in .env.local</p>
        <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {villages.map(v => <VillageCard key={v.name} v={v} tx={tx} onClick={() => {}} />)}
        </div>
      </div>
    </div>
  );

  if (!isLoaded) return (
    <div className="p-4 md:p-8">
      <h1 className="text-2xl md:text-3xl font-bold mb-6">{tx('Coverage Map', 'coverage_map')}</h1>
      <div className="animate-pulse bg-gray-200 h-96 rounded-2xl" />
    </div>
  );

  return (
    <div className="p-4 md:p-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl md:text-3xl font-bold">{tx('Coverage Map', 'coverage_map')}</h1>
        <span className="text-xs text-[#5F5E5A] bg-gray-100 px-3 py-1 rounded-full">
          {villages.length} {tx('village')}{villages.length !== 1 ? 's' : ''} {tx('tracked')}
        </span>
      </div>

      <div className="bg-white rounded-2xl shadow-sm border border-[#D3D1C7] overflow-hidden mb-6 relative">
        <button
          onClick={() => setMapType(t => t === 'roadmap' ? 'satellite' : 'roadmap')}
          className="absolute top-3 right-3 z-10 bg-white shadow-md border border-gray-200 px-3 py-2 rounded-lg text-xs font-semibold flex items-center gap-1 hover:bg-gray-50 transition-colors"
          title={tx('Toggle satellite view')}
        >
          <span className="material-symbols-outlined text-sm">{mapType === 'satellite' ? 'map' : 'satellite'}</span>
          {mapType === 'satellite' ? tx('Map') : tx('Satellite')}
        </button>
        <GoogleMap
          mapContainerStyle={{ width: '100%', height: '480px' }}
          center={mapCenter}
          zoom={11}
          options={{ mapTypeControl: false, streetViewControl: false, mapTypeId: mapType }}
        >
          {villages.map(v => (
            <Circle
              key={v.name}
              center={{ lat: v.lat, lng: v.lng }}
              radius={Math.min(2500, Math.max(800, v.total * 120))}
              options={{
                fillColor: v.critical > 0 ? '#E24B4A' : v.coveragePercent < 70 ? '#BA7517' : '#1D9E75',
                fillOpacity: 0.35,
                strokeColor: v.critical > 0 ? '#E24B4A' : v.coveragePercent < 70 ? '#BA7517' : '#1D9E75',
                strokeWeight: 2,
                cursor: 'pointer',
              }}
              onClick={() => setSelectedVillage(v)}
            />
          ))}

          {activeAshas.map(asha => (
            <Marker
              key={asha.id}
              position={{ lat: asha.lat, lng: asha.lng }}
              icon={{
                url: 'https://maps.google.com/mapfiles/ms/icons/blue-dot.png',
                scaledSize: new window.google.maps.Size(36, 36),
              }}
              onClick={() => setSelectedVillage({
                name: `ASHA: ${asha.name}`,
                lat: asha.lat,
                lng: asha.lng,
                total: 1,
                critical: 0,
                isWorker: true,
              })}
            />
          ))}

          {selectedVillage && (
            <InfoWindow
              position={{ lat: selectedVillage.lat, lng: selectedVillage.lng }}
              onCloseClick={() => setSelectedVillage(null)}
            >
              <div className="p-1 min-w-[120px]">
                <strong className="block text-sm mb-1">{selectedVillage.name}</strong>
                {!selectedVillage.isWorker ? (
                  <>
                    <p className="text-xs text-gray-600"><strong>ASHA:</strong> {selectedVillage.ashaName}</p>
                    <p className="text-xs text-gray-600"><strong>Households:</strong> {selectedVillage.total}</p>
                    <p className="text-xs text-gray-600"><strong>Coverage:</strong> {selectedVillage.coveragePercent}%</p>
                    <p className={`text-xs font-bold ${selectedVillage.critical > 0 ? 'text-red-600' : 'text-green-600'}`}>
                      <strong>Critical Cases:</strong> {selectedVillage.critical}
                    </p>
                  </>
                ) : (
                  <p className="text-xs text-gray-600">ASHA Worker Location</p>
                )}
              </div>
            </InfoWindow>
          )}
        </GoogleMap>
      </div>

      <div className="flex items-center gap-6 px-4 py-3 bg-gray-50 border-t border-[#D3D1C7] text-xs text-[#5F5E5A]">
        <span className="font-semibold">{tx('Legend')}:</span>
        <span className="flex items-center gap-1.5"><span className="w-4 h-4 rounded-full bg-[#1D9E75] inline-block opacity-70"/> {tx('Good coverage (≥70%)')}</span>
        <span className="flex items-center gap-1.5"><span className="w-4 h-4 rounded-full bg-[#BA7517] inline-block opacity-70"/> {tx('Low coverage (<70%)')}</span>
        <span className="flex items-center gap-1.5"><span className="w-4 h-4 rounded-full bg-[#E24B4A] inline-block opacity-70"/> {tx('Critical cases present')}</span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {villages.length === 0 && (
          <p className="text-[#5F5E5A] p-4 col-span-3">
            {tx('No household data found. Submit some Family Survey records first, or check that ashaIds match Firestore.')}
          </p>
        )}
        {villages.map(v => (
          <VillageCard key={v.name} v={v} tx={tx} onClick={() => setSelectedVillage(v)} />
        ))}
      </div>
    </div>
  );
};

const VillageCard = ({ v, onClick, tx }) => (
  <div
    className="bg-white rounded-2xl p-4 shadow-sm border border-[#D3D1C7] cursor-pointer hover:shadow-md transition-shadow"
    onClick={onClick}
  >
    <div className="flex items-center justify-between mb-3">
      <h3 className="font-bold text-[#1A1A18]">{v.name}</h3>
      <span className={`w-3 h-3 rounded-full ${v.critical > 0 ? 'bg-[#E24B4A]' : v.coveragePercent < 70 ? 'bg-[#BA7517]' : 'bg-[#1D9E75]'}`} />
    </div>
    <div className="grid grid-cols-2 gap-2 text-center">
      <div>
        <p className="text-xl font-bold text-[#085041]">{v.total}</p>
        <p className="text-[10px] text-[#5F5E5A] uppercase tracking-wide">{tx ? tx('Families', 'families') : 'Families'}</p>
      </div>
      <div>
        <p className={`text-xl font-bold ${v.critical > 0 ? 'text-[#E24B4A]' : 'text-[#1D9E75]'}`}>{v.critical}</p>
        <p className="text-[10px] text-[#5F5E5A] uppercase tracking-wide">{tx ? tx('Critical', 'critical') : 'Critical'}</p>
      </div>
    </div>
  </div>
);

export default CoverageMap;
