import { useRef, useState } from 'react';
import jsQR from 'jsqr';
import { extractAadhaarFromCardImage, validateVerhoeff } from '../utils/aadhaarOcr';

const AadhaarInput = ({ memberIndex, memberName, onAadhaarScanned }) => {
  const [mode, setMode] = useState('choose'); // choose | scan_qr | photo_ocr | review_ocr | manual | confirmed
  const [masked, setMasked] = useState('');
  const [ocrStatus, setOcrStatus] = useState('');
  const [ocrProgress, setOcrProgress] = useState(0);
  const [isProcessingOcr, setIsProcessingOcr] = useState(false);
  const [extractedData, setExtractedData] = useState({
    aadhaar_raw: '',
    name: '',
    dob: '',
    gender: ''
  });

  const canvasRef = useRef(null);
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const fileInputRef = useRef(null);

  // ── QR Scanner Camera ──
  const startCamera = async () => {
    setMode('scan_qr');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ 
        video: { facingMode: 'environment' } 
      });
      streamRef.current = stream;
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play();
          scanFrame(stream);
        }
      }, 100);
    } catch (err) {
      console.warn('Camera error:', err);
      alert('Could not access camera. Try uploading an image file instead.');
      setMode('choose');
    }
  };

  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(t => t.stop());
      streamRef.current = null;
    }
    if (videoRef.current && videoRef.current.srcObject) {
      videoRef.current.srcObject.getTracks().forEach(t => t.stop());
      videoRef.current.srcObject = null;
    }
  };

  const scanFrame = (stream) => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx || !videoRef.current) return;
    
    const tick = () => {
      if (mode !== 'scan_qr' || !streamRef.current) {
        stopCamera();
        return;
      }
      
      if (videoRef.current.readyState === videoRef.current.HAVE_ENOUGH_DATA) {
        canvas.width = videoRef.current.videoWidth;
        canvas.height = videoRef.current.videoHeight;
        ctx.drawImage(videoRef.current, 0, 0);
        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const code = jsQR(imageData.data, imageData.width, imageData.height);
        
        if (code) {
          stopCamera();
          parseAadhaarQR(code.data);
          return;
        }
      }
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  };

  const parseAadhaarQR = (qrData) => {
    try {
      const parser = new DOMParser();
      const xml = parser.parseFromString(qrData, 'text/xml');
      const root = xml.documentElement;
      
      const aadhaar = root.getAttribute('uid') || '';
      const name = root.getAttribute('name') || '';
      const dob = root.getAttribute('dob') || '';
      const gender = root.getAttribute('gender') === 'M' ? 'Male' : 
                     root.getAttribute('gender') === 'F' ? 'Female' : 'Other';
      
      const [d, m, y] = dob.split('-');
      const formattedDob = y && m && d ? `${y}-${m.padStart(2,'0')}-${d.padStart(2,'0')}` : '';
      
      const last4 = aadhaar.slice(-4);
      setMasked(`XXXX-XXXX-XXXX-${last4}`);
      setMode('confirmed');
      
      onAadhaarScanned({ aadhaar_raw: aadhaar, name, dob: formattedDob, gender });
    } catch {
      const params = new URLSearchParams(qrData.split('?')[1]);
      const uid = params.get('uid') || qrData;
      setMasked(`XXXX-XXXX-XXXX-${uid.slice(-4)}`);
      setMode('confirmed');
      onAadhaarScanned({ aadhaar_raw: uid });
    }
  };

  // ── On-Device Card Photo OCR ──
  const startCardCapture = async () => {
    setMode('photo_ocr');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ 
        video: { facingMode: 'environment', width: { ideal: 1920 }, height: { ideal: 1080 } } 
      });
      streamRef.current = stream;
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play();
        }
      }, 100);
    } catch (err) {
      console.warn('Camera error for card capture:', err);
    }
  };

  const capturePhotoAndRunOcr = async () => {
    if (!videoRef.current) return;
    const canvas = document.createElement('canvas');
    canvas.width = videoRef.current.videoWidth || 1280;
    canvas.height = videoRef.current.videoHeight || 720;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(videoRef.current, 0, 0, canvas.width, canvas.height);
    stopCamera();

    await processImageForOcr(canvas);
  };

  const handleFileUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    stopCamera();
    await processImageForOcr(file);
  };

  const processImageForOcr = async (imageSource) => {
    setIsProcessingOcr(true);
    setOcrStatus('Initializing on-device OCR engine...');
    setOcrProgress(10);

    try {
      const result = await extractAadhaarFromCardImage(imageSource, ({ status, progress }) => {
        if (status === 'checking_qr') {
          setOcrStatus('Checking for Aadhaar Secure QR on card...');
          setOcrProgress(25);
        } else if (status === 'running_browser_ocr') {
          setOcrStatus('Reading printed text locally with browser OCR...');
          setOcrProgress(50);
        } else if (status === 'recognizing text') {
          setOcrStatus('Recognizing name, DOB, and 12-digit number...');
          setOcrProgress(Math.round(progress * 100));
        }
      });

      setExtractedData({
        aadhaar_raw: result.aadhaar_raw || '',
        name: result.name || '',
        dob: result.dob || '',
        gender: result.gender || ''
      });

      setMode('review_ocr');
    } catch (err) {
      console.error('[AadhaarInput] OCR failed:', err);
      alert('Could not extract text from this image. Please try again with clear lighting or enter manually.');
      setMode('choose');
    } finally {
      setIsProcessingOcr(false);
    }
  };

  const handleConfirmOcr = () => {
    const last4 = extractedData.aadhaar_raw?.slice(-4) || '????';
    setMasked(`XXXX-XXXX-XXXX-${last4}`);
    setMode('confirmed');
    onAadhaarScanned({
      aadhaar_raw: extractedData.aadhaar_raw,
      name: extractedData.name,
      dob: extractedData.dob,
      gender: extractedData.gender
    });
  };

  const handleManualEntry = (value) => {
    const digits = value.replace(/\D/g, '').slice(0, 12);
    if (digits.length === 12) {
      setMasked(`XXXX-XXXX-XXXX-${digits.slice(-4)}`);
      setMode('confirmed');
      onAadhaarScanned({ aadhaar_raw: digits });
    }
  };

  // ── Render Views ──
  if (mode === 'choose') return (
    <div className="p-4 bg-gray-50 rounded-xl border border-gray-200">
      <div className="flex items-center justify-between mb-3">
        <p className="text-sm font-medium text-gray-700">Aadhaar for {memberName || `Member ${memberIndex + 1}`}</p>
        <span className="text-[11px] bg-emerald-100 text-emerald-800 font-semibold px-2 py-0.5 rounded-full flex items-center gap-1">
          <span className="material-symbols-outlined text-[14px]">lock</span> DPDP Compliant
        </span>
      </div>
      
      <div className="flex flex-col gap-2">
        <button 
          type="button" 
          onClick={startCardCapture} 
          className="w-full py-2.5 bg-[#1D9E75] text-white rounded-lg flex items-center justify-center gap-2 font-medium hover:bg-[#168361] transition-colors shadow-sm"
        >
          <span className="material-symbols-outlined text-[20px]">document_scanner</span> Photograph Aadhaar Card (On-Device OCR)
        </button>

        <button 
          type="button" 
          onClick={startCamera} 
          className="w-full py-2 bg-blue-50 text-blue-700 rounded-lg flex items-center justify-center gap-2 font-medium hover:bg-blue-100 transition-colors"
        >
          <span className="material-symbols-outlined text-[20px]">qr_code_scanner</span> Scan Aadhaar QR
        </button>

        <button 
          type="button" 
          onClick={() => setMode('manual')} 
          className="w-full py-2 bg-gray-100 text-gray-700 rounded-lg flex items-center justify-center gap-2 font-medium hover:bg-gray-200 transition-colors"
        >
          <span className="material-symbols-outlined text-[20px]">edit</span> Enter Manually
        </button>

        <button 
          type="button" 
          onClick={() => onAadhaarScanned(null)} 
          className="w-full py-2 text-gray-500 font-medium mt-1 hover:text-gray-700"
        >
          Skip for now
        </button>
      </div>

      <p className="text-[11px] text-gray-500 mt-2 text-center flex items-center justify-center gap-1">
        <span className="material-symbols-outlined text-[13px] text-emerald-600">verified_user</span>
        Raw card image never leaves device; only verified fields cross the network.
      </p>
    </div>
  );
  
  if (mode === 'scan_qr') return (
    <div className="p-4 bg-gray-50 rounded-xl border border-gray-200 flex flex-col gap-3">
      <div className="relative rounded-lg overflow-hidden bg-black aspect-video flex items-center justify-center">
        <video ref={videoRef} playsInline className="w-full h-full object-cover" />
        <div className="absolute inset-0 border-2 border-dashed border-white/50 m-4 rounded"></div>
      </div>
      <canvas ref={canvasRef} className="hidden" />
      <p className="text-sm text-center text-gray-600 font-medium">Point camera at Aadhaar QR code</p>
      <button type="button" onClick={() => { setMode('choose'); stopCamera(); }} className="w-full py-2 bg-gray-200 text-gray-800 rounded-lg font-medium hover:bg-gray-300">
        Cancel Scan
      </button>
    </div>
  );

  if (mode === 'photo_ocr') return (
    <div className="p-4 bg-gray-50 rounded-xl border border-gray-200 flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <p className="text-sm font-bold text-gray-800">Photograph Aadhaar Card</p>
        <span className="text-[10px] bg-emerald-100 text-emerald-800 font-bold px-2 py-0.5 rounded">100% Local OCR</span>
      </div>

      {isProcessingOcr ? (
        <div className="p-6 bg-white rounded-xl border border-gray-200 text-center flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-4 border-[#1D9E75] border-t-transparent rounded-full animate-spin"></div>
          <p className="text-sm font-medium text-gray-800">{ocrStatus}</p>
          <div className="w-full bg-gray-100 h-2 rounded-full overflow-hidden">
            <div className="bg-[#1D9E75] h-full transition-all duration-300" style={{ width: `${ocrProgress}%` }}></div>
          </div>
          <p className="text-xs text-gray-500">Processing locally inside your browser...</p>
        </div>
      ) : (
        <>
          <div className="relative rounded-lg overflow-hidden bg-black aspect-video flex items-center justify-center border-2 border-[#1D9E75]">
            <video ref={videoRef} playsInline className="w-full h-full object-cover" />
            <div className="absolute inset-x-8 inset-y-4 border-2 border-dashed border-emerald-400 rounded-lg pointer-events-none flex items-center justify-center">
              <span className="text-white/80 text-xs bg-black/60 px-2 py-1 rounded">Align Aadhaar Card Front</span>
            </div>
          </div>

          <div className="flex gap-2">
            <button 
              type="button" 
              onClick={capturePhotoAndRunOcr} 
              className="flex-1 py-3 bg-[#1D9E75] text-white rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-[#168361]"
            >
              <span className="material-symbols-outlined">camera</span> Capture & Read Card
            </button>
            <button 
              type="button" 
              onClick={() => fileInputRef.current?.click()} 
              className="px-4 py-3 bg-white border border-gray-300 text-gray-700 rounded-xl font-medium flex items-center justify-center gap-1 hover:bg-gray-100"
              title="Upload photo from files"
            >
              <span className="material-symbols-outlined">upload_file</span>
            </button>
            <input 
              ref={fileInputRef} 
              type="file" 
              accept="image/*" 
              className="hidden" 
              onChange={handleFileUpload} 
            />
          </div>

          <button 
            type="button" 
            onClick={() => { setMode('choose'); stopCamera(); }} 
            className="w-full py-2 bg-gray-200 text-gray-700 rounded-lg text-sm font-medium hover:bg-gray-300"
          >
            Cancel
          </button>
        </>
      )}
    </div>
  );

  if (mode === 'review_ocr') return (
    <div className="p-4 bg-emerald-50 rounded-xl border-2 border-[#1D9E75] space-y-3">
      <div className="flex items-center justify-between border-b border-emerald-200 pb-2">
        <div className="flex items-center gap-1.5 text-emerald-900 font-bold text-sm">
          <span className="material-symbols-outlined text-[18px]">verified</span> Review Extracted Aadhaar
        </div>
        <span className="text-[10px] bg-emerald-200 text-emerald-900 font-bold px-2 py-0.5 rounded">Tier 2 Review Gate</span>
      </div>

      <p className="text-xs text-emerald-800">
        Extracted locally on your device. Please verify or correct fields before applying:
      </p>

      <div className="space-y-2 bg-white p-3 rounded-lg border border-emerald-100">
        <div>
          <label className="block text-[11px] font-bold text-gray-600 mb-0.5">Aadhaar Number (12 Digits)</label>
          <div className="flex items-center gap-2">
            <input 
              type="text" 
              maxLength={12}
              value={extractedData.aadhaar_raw} 
              onChange={e => setExtractedData(prev => ({ ...prev, aadhaar_raw: e.target.value.replace(/\D/g, '') }))}
              className="w-full p-2 border rounded font-mono text-sm tracking-wider" 
              placeholder="12 digit number" 
            />
            {extractedData.aadhaar_raw.length === 12 && (
              <span className={`text-[10px] font-bold px-2 py-1 rounded whitespace-nowrap ${
                validateVerhoeff(extractedData.aadhaar_raw) ? 'bg-green-100 text-green-800' : 'bg-amber-100 text-amber-800'
              }`}>
                {validateVerhoeff(extractedData.aadhaar_raw) ? '✓ Checksum OK' : '⚠ Check Digits'}
              </span>
            )}
          </div>
        </div>

        <div>
          <label className="block text-[11px] font-bold text-gray-600 mb-0.5">Full Name</label>
          <input 
            type="text" 
            value={extractedData.name} 
            onChange={e => setExtractedData(prev => ({ ...prev, name: e.target.value }))}
            className="w-full p-2 border rounded text-sm" 
            placeholder="Name on card" 
          />
        </div>

        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className="block text-[11px] font-bold text-gray-600 mb-0.5">Date of Birth</label>
            <input 
              type="date" 
              value={extractedData.dob} 
              onChange={e => setExtractedData(prev => ({ ...prev, dob: e.target.value }))}
              className="w-full p-2 border rounded text-sm" 
            />
          </div>
          <div>
            <label className="block text-[11px] font-bold text-gray-600 mb-0.5">Gender</label>
            <select 
              value={extractedData.gender} 
              onChange={e => setExtractedData(prev => ({ ...prev, gender: e.target.value }))}
              className="w-full p-2 border rounded text-sm bg-white"
            >
              <option value="">Select</option>
              <option value="Male">Male</option>
              <option value="Female">Female</option>
              <option value="Other">Other</option>
            </select>
          </div>
        </div>
      </div>

      <div className="flex gap-2 pt-1">
        <button 
          type="button" 
          onClick={handleConfirmOcr}
          className="flex-1 py-2.5 bg-[#1D9E75] text-white rounded-lg font-bold text-sm hover:bg-[#168361] shadow-sm flex items-center justify-center gap-1.5"
        >
          <span className="material-symbols-outlined text-[18px]">check_circle</span> Confirm & Apply
        </button>
        <button 
          type="button" 
          onClick={() => setMode('choose')}
          className="px-3 py-2.5 bg-white border border-gray-300 text-gray-600 rounded-lg text-sm font-medium hover:bg-gray-100"
        >
          Retake
        </button>
      </div>
    </div>
  );
  
  if (mode === 'manual') return (
    <div className="p-4 bg-gray-50 rounded-xl border border-gray-200">
      <p className="text-sm font-medium text-gray-700 mb-2">Enter Manual Aadhaar</p>
      <input 
        type="tel" 
        maxLength={12}
        className="w-full p-3 border rounded-lg mb-3 font-mono text-center tracking-widest text-lg"
        placeholder="12-digit number"
        onChange={e => handleManualEntry(e.target.value)}
      />
      <button type="button" onClick={() => setMode('choose')} className="w-full py-2 text-gray-500 font-medium text-sm hover:text-gray-700">
        Cancel
      </button>
    </div>
  );
  
  if (mode === 'confirmed') return (
    <div className="p-4 bg-green-50 rounded-xl border border-green-200 flex justify-between items-center">
      <div className="flex items-center gap-2 text-green-700 font-medium">
        <span className="material-symbols-outlined text-[20px]">check_circle</span>
        <span className="font-mono tracking-wider">{masked}</span>
      </div>
      <button type="button" onClick={() => setMode('choose')} className="text-sm text-blue-600 font-medium hover:underline">
        Replace
      </button>
    </div>
  );
};

export default AadhaarInput;

