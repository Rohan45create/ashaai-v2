import React, { useState, useRef } from 'react';
import { apiFetch } from '../../utils/api';
import { saveFeedback } from '../../utils/feedbackDb';

const GRADE_CONFIG = {
  NORMAL: {
    bg: '#EAF3DE', border: '#1D9E75', text: '#085041',
    icon: 'check_circle', label: 'Normal', badgeBg: '#1D9E75',
  },
  YELLOW: {
    bg: '#FFF8E1', border: '#FFCA28', text: '#7A5500',
    icon: 'warning', label: 'Moderate (MAM)', badgeBg: '#F0A500',
  },
  RED: {
    bg: '#FCEBEB', border: '#E24B4A', text: '#791F1F',
    icon: 'crisis_alert', label: 'Severe (SAM)', badgeBg: '#E24B4A',
  },
};

function ConfidenceBar({ value }) {
  const color = value >= 75 ? '#1D9E75' : value >= 50 ? '#F0A500' : '#E24B4A';
  return (
    <div style={{ marginTop: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px', color: '#777', marginBottom: '4px' }}>
        <span>AI Confidence</span>
        <span style={{ fontWeight: '700', color }}>{value}%</span>
      </div>
      <div style={{ background: '#E8E7E0', borderRadius: '100px', height: '6px', overflow: 'hidden' }}>
        <div style={{ width: `${value}%`, background: color, height: '100%', borderRadius: '100px', transition: 'width 0.8s ease' }} />
      </div>
    </div>
  );
}

export default function MalnutritionScannerWidget({ onGradeConfirmed, prefillData }) {
  const [photo, setPhoto] = useState(null);
  const [preview, setPreview] = useState(null);
  const [loading, setLoading] = useState(false);
  const [gradeResult, setGradeResult] = useState(null);
  const [gradeConfirmed, setGradeConfirmed] = useState(false);
  const [referralSent, setReferralSent] = useState(false);
  const [isCorrect, setIsCorrect] = useState(null);
  const [correctedGrade, setCorrectedGrade] = useState('');
  const [consentGiven, setConsentGiven] = useState(null);
  const [feedbackSubmitted, setFeedbackSubmitted] = useState(false);

  // Manual Input fields
  const [age, setAge] = useState(prefillData?.age_months || prefillData?.age || '');
  const [gender, setGender] = useState(prefillData?.gender || '');
  const [height, setHeight] = useState(prefillData?.height_cm || prefillData?.current_height_cm || '');
  const [weight, setWeight] = useState(prefillData?.weight_kg || prefillData?.current_weight_kg || '');
  
  const fileInputRef = useRef();

  const handleCapture = (e) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setPhoto(file);
      setPreview(URL.createObjectURL(file));
      setGradeResult(null);
      setReferralSent(false);
      setIsCorrect(null);
      setCorrectedGrade('');
      setConsentGiven(null);
      setFeedbackSubmitted(false);
    }
  };

  const clearPhoto = () => {
    setPhoto(null);
    setPreview(null);
    setGradeResult(null);
    setGradeConfirmed(false);
    setReferralSent(false);
    setIsCorrect(null);
    setCorrectedGrade('');
    setConsentGiven(null);
    setFeedbackSubmitted(false);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleGrade = async () => {
    if (!photo) return;
    setLoading(true);
    try {
      const formData = new FormData();
      formData.append('photo', photo);
      
      if (age) formData.append('age', age);
      if (gender) formData.append('gender', gender);
      if (height) formData.append('height', height);
      if (weight) formData.append('weight', weight);

      const data = await apiFetch('/api/vision/muac-grade', {
        method: 'POST',
        body: formData,
      });

      setGradeResult(data.grading || data);
      setGradeConfirmed(false);
    } catch (err) {
      console.error('[MalnutritionScan] error:', err);
      setGradeResult({ error: true, grade: 'ERROR', confidence: 0, explanation: err.message });
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateReferral = async () => {
    try {
      const childName = prefillData?.child_name || 'Unknown Child';
      await apiFetch('/api/referrals', {
        method: 'POST',
        body: JSON.stringify({
          reason: `Severe Acute Malnutrition (SAM) — AI Visual Scan (${gradeResult?.confidence ?? '?'}% confidence) for ${childName}`,
          status: 'Pending',
          referredDate: new Date().toISOString().split('T')[0] + 'T00:00:00Z',
        }),
      });

      setReferralSent(true);
    } catch (err) {
      console.error(err);
      alert('Failed to generate referral. Please try again.');
    }
  };

  const displayGrade = correctedGrade || gradeResult?.grade;
  const cfg = displayGrade ? (GRADE_CONFIG[displayGrade] || GRADE_CONFIG.NORMAL) : null;

  return (
    <div className="bg-white rounded-2xl p-5 shadow-sm border border-[#D3D1C7] space-y-4 mb-4">
      <div className="flex items-center space-x-3 mb-2">
        <div className="w-10 h-10 bg-[#FCEBEB] rounded-full flex items-center justify-center text-[#791F1F]">
          <span className="material-symbols-outlined text-xl">vital_signs</span>
        </div>
        <div>
          <h3 className="font-bold text-[#1A1A18]">AI Malnutrition Scan</h3>
          <p className="text-xs text-[#5F5E5A]">Take a photo of the child — Gemini will assess malnutrition risk</p>
        </div>
      </div>

      {gradeResult ? (
        <div>
          {gradeResult.error ? (
            <div style={{ background: '#FCEBEB', border: '1px solid #E24B4A', borderRadius: '14px', padding: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#791F1F', fontWeight: '700' }}>
                <span className="material-symbols-outlined">error</span>
                Analysis Failed
              </div>
              <p style={{ fontSize: '13px', color: '#791F1F', marginTop: '6px' }}>{gradeResult.explanation || 'Could not analyze the photo. Please try again with a clear image.'}</p>
              <button type="button" onClick={clearPhoto} className="mt-3 text-xs underline font-medium text-[#791F1F]">Try Again</button>
            </div>
          ) : (
            <div style={{ background: cfg.bg, border: `1.5px solid ${cfg.border}`, borderRadius: '16px', padding: '18px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
                <div style={{ background: cfg.badgeBg, borderRadius: '50%', width: '38px', height: '38px', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <span className="material-symbols-outlined" style={{ color: '#fff', fontSize: '20px' }}>{cfg.icon}</span>
                </div>
                <div>
                  <p style={{ fontSize: '11px', fontWeight: '600', color: cfg.text, opacity: 0.7, textTransform: 'uppercase', letterSpacing: '0.05em' }}>AI Assessment</p>
                  <h4 style={{ fontSize: '17px', fontWeight: '800', color: cfg.text }}>{gradeResult.severity_label || cfg.label}</h4>
                </div>
              </div>

              <ConfidenceBar value={gradeResult.confidence ?? 0} />

              {gradeResult.explanation && (
                <p style={{ fontSize: '13px', color: cfg.text, marginTop: '12px', lineHeight: '1.55' }}>
                  {gradeResult.explanation}
                </p>
              )}

              {gradeResult.visible_signs && gradeResult.visible_signs.length > 0 && (
                <div style={{ marginTop: '12px' }}>
                  <p style={{ fontSize: '11px', fontWeight: '700', color: cfg.text, textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '6px' }}>Observed Signs</p>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                    {gradeResult.visible_signs.map((sign, i) => (
                      <span key={i} style={{ background: 'rgba(0,0,0,0.08)', borderRadius: '20px', padding: '3px 10px', fontSize: '12px', color: cfg.text, fontWeight: '500' }}>
                        {sign}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {gradeResult.recommendation && (
                <div style={{ marginTop: '12px', background: 'rgba(0,0,0,0.06)', borderRadius: '10px', padding: '10px 12px' }}>
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
                    <span className="material-symbols-outlined" style={{ fontSize: '16px', color: cfg.text, marginTop: '1px', flexShrink: 0 }}>medical_services</span>
                    <p style={{ fontSize: '13px', color: cfg.text, fontWeight: '500', lineHeight: '1.5' }}>{gradeResult.recommendation}</p>
                  </div>
                </div>
              )}

              {!feedbackSubmitted ? (
                <div style={{ marginTop: '14px', borderTop: '1px solid #E8E7E0', paddingTop: '14px' }}>
                  {isCorrect === null && (
                    <div>
                      <p style={{ fontSize: '13px', fontWeight: '600', color: '#1A1A18', marginBottom: '8px' }}>Was this AI prediction correct?</p>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button type="button" onClick={() => setIsCorrect(true)} style={{ flex: 1, padding: '10px', background: '#fff', border: '1px solid #D3D1C7', borderRadius: '8px', fontSize: '13px', fontWeight: '600', color: '#1A1A18', cursor: 'pointer' }}>Yes</button>
                        <button type="button" onClick={() => setIsCorrect(false)} style={{ flex: 1, padding: '10px', background: '#fff', border: '1px solid #D3D1C7', borderRadius: '8px', fontSize: '13px', fontWeight: '600', color: '#1A1A18', cursor: 'pointer' }}>No</button>
                      </div>
                    </div>
                  )}

                  {isCorrect === false && !correctedGrade && (
                    <div style={{ marginTop: '10px' }}>
                      <p style={{ fontSize: '13px', fontWeight: '600', color: '#1A1A18', marginBottom: '8px' }}>Please select the correct grade:</p>
                      <div style={{ display: 'flex', gap: '6px', flexDirection: 'column' }}>
                        <button type="button" onClick={() => setCorrectedGrade('NORMAL')} style={{ padding: '10px', background: '#fff', border: '1px solid #1D9E75', borderRadius: '8px', fontSize: '13px', fontWeight: '600', color: '#085041', cursor: 'pointer' }}>Normal (Green)</button>
                        <button type="button" onClick={() => setCorrectedGrade('YELLOW')} style={{ padding: '10px', background: '#fff', border: '1px solid #F0A500', borderRadius: '8px', fontSize: '13px', fontWeight: '600', color: '#7A5500', cursor: 'pointer' }}>Moderate / MAM (Yellow)</button>
                        <button type="button" onClick={() => setCorrectedGrade('RED')} style={{ padding: '10px', background: '#fff', border: '1px solid #E24B4A', borderRadius: '8px', fontSize: '13px', fontWeight: '600', color: '#791F1F', cursor: 'pointer' }}>Severe / SAM (Red)</button>
                      </div>
                    </div>
                  )}

                  {(isCorrect === true || correctedGrade) && consentGiven === null && (
                    <div style={{ marginTop: '10px', padding: '12px', background: '#F9F9F8', borderRadius: '8px', border: '1px solid #E8E7E0' }}>
                      <p style={{ fontSize: '13px', fontWeight: '600', color: '#1A1A18', marginBottom: '4px' }}>Use this photo to help improve the model?</p>
                      <p style={{ fontSize: '11px', color: '#5F5E5A', marginBottom: '10px' }}>It will be deleted after training, never stored otherwise.</p>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button type="button" onClick={() => setConsentGiven(true)} style={{ flex: 1, padding: '10px', background: '#085041', border: 'none', borderRadius: '8px', fontSize: '13px', fontWeight: '600', color: '#fff', cursor: 'pointer' }}>Yes, Use for Training</button>
                        <button type="button" onClick={() => setConsentGiven(false)} style={{ flex: 1, padding: '10px', background: '#fff', border: '1px solid #D3D1C7', borderRadius: '8px', fontSize: '13px', fontWeight: '600', color: '#1A1A18', cursor: 'pointer' }}>No, Do Not Use</button>
                      </div>
                    </div>
                  )}

                  {consentGiven !== null && (
                    <button
                      type="button"
                      onClick={async () => {
                        const finalGrade = isCorrect ? gradeResult.grade : correctedGrade;
                        await saveFeedback(photo, gradeResult.grade, finalGrade, consentGiven);
                        setFeedbackSubmitted(true);
                        setGradeConfirmed(true);
                        if (gradeResult && onGradeConfirmed) {
                          onGradeConfirmed({
                            malnutritionGrade: finalGrade,
                            muac_color: finalGrade === 'RED' ? 'RED' : finalGrade === 'YELLOW' ? 'YELLOW' : 'GREEN',
                            muac_cm: gradeResult.muac_mm ? (gradeResult.muac_mm / 10).toFixed(1) : undefined
                          });
                        }
                      }}
                      style={{
                        marginTop: '14px', width: '100%', padding: '12px 16px',
                        background: cfg.badgeBg, color: '#fff',
                        border: 'none', borderRadius: '12px', fontWeight: '700',
                        fontSize: '14px', cursor: 'pointer', display: 'flex',
                        alignItems: 'center', justifyContent: 'center', gap: '8px',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.15)'
                      }}
                    >
                      <span className="material-symbols-outlined text-[18px]">how_to_reg</span>
                      Confirm & Apply Grade to Record
                    </button>
                  )}
                </div>
              ) : (
                <div style={{ marginTop: '14px', background: '#EAF3DE', border: '1.5px solid #1D9E75', borderRadius: '12px', padding: '12px 16px', display: 'flex', alignItems: 'center', gap: '8px', color: '#085041', fontWeight: '700', fontSize: '13px' }}>
                  <span className="material-symbols-outlined text-[20px]">verified</span>
                  Grade Confirmed by ASHA — Applied to Record
                </div>
              )}

              <button type="button" onClick={clearPhoto} style={{ marginTop: '14px', fontSize: '12px', fontWeight: '600', color: cfg.text, textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
                Scan Again
              </button>

              {(gradeResult.grade === 'RED' || gradeResult.needs_nrc_referral) && (
                <div style={{ marginTop: '14px' }}>
                  {referralSent ? (
                    <div style={{ background: '#085041', borderRadius: '12px', padding: '12px 16px', display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <span className="material-symbols-outlined" style={{ color: '#fff', fontSize: '20px' }}>check_circle</span>
                      <p style={{ color: '#fff', fontWeight: '700', fontSize: '14px' }}>NRC Referral Sent — Admin Notified</p>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={handleGenerateReferral}
                      style={{
                        width: '100%', padding: '14px', background: '#E24B4A', color: '#fff',
                        borderRadius: '14px', border: 'none', fontWeight: '800', fontSize: '14px',
                        cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
                        gap: '8px', boxShadow: '0 4px 14px rgba(226,75,74,0.35)', transition: 'opacity 0.15s',
                      }}
                    >
                      <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>local_hospital</span>
                      Generate NRC Referral
                    </button>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      ) : preview ? (
        <div>
          <div style={{ position: 'relative' }}>
            <img src={preview} alt="Child" style={{ width: '100%', height: '220px', objectFit: 'cover', borderRadius: '14px', border: '1px solid #D3D1C7' }} />
            <button
              type="button"
              onClick={clearPhoto}
              style={{ position: 'absolute', top: '10px', right: '10px', background: 'white', border: 'none', borderRadius: '50%', width: '32px', height: '32px', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 2px 8px rgba(0,0,0,0.2)', cursor: 'pointer', color: '#E24B4A' }}
            >
              <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>close</span>
            </button>
          </div>
          
          <div style={{ marginTop: '12px', padding: '12px', background: '#F9F9F8', borderRadius: '12px', border: '1px solid #D3D1C7' }}>
            <p style={{ fontSize: '12px', fontWeight: '600', color: '#5F5E5A', marginBottom: '8px' }}>Optional: Verify measurements to improve accuracy</p>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
              <div>
                <label style={{ fontSize: '11px', color: '#5F5E5A', display: 'block', marginBottom: '2px' }}>Age (months)</label>
                <input type="number" value={age} onChange={(e) => setAge(e.target.value)} placeholder="Age" style={{ width: '100%', padding: '8px', border: '1px solid #D3D1C7', borderRadius: '8px', fontSize: '13px' }} />
              </div>
              <div>
                <label style={{ fontSize: '11px', color: '#5F5E5A', display: 'block', marginBottom: '2px' }}>Gender</label>
                <select value={gender} onChange={(e) => setGender(e.target.value)} style={{ width: '100%', padding: '8px', border: '1px solid #D3D1C7', borderRadius: '8px', fontSize: '13px', background: 'white' }}>
                  <option value="">Select...</option>
                  <option value="Male">Male</option>
                  <option value="Female">Female</option>
                </select>
              </div>
              <div>
                <label style={{ fontSize: '11px', color: '#5F5E5A', display: 'block', marginBottom: '2px' }}>Height (cm)</label>
                <input type="number" step="0.1" value={height} onChange={(e) => setHeight(e.target.value)} placeholder="e.g. 72.5" style={{ width: '100%', padding: '8px', border: '1px solid #D3D1C7', borderRadius: '8px', fontSize: '13px' }} />
              </div>
              <div>
                <label style={{ fontSize: '11px', color: '#5F5E5A', display: 'block', marginBottom: '2px' }}>Weight (kg)</label>
                <input type="number" step="0.1" value={weight} onChange={(e) => setWeight(e.target.value)} placeholder="e.g. 8.5" style={{ width: '100%', padding: '8px', border: '1px solid #D3D1C7', borderRadius: '8px', fontSize: '13px' }} />
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={handleGrade}
            disabled={loading}
            style={{
              marginTop: '12px', width: '100%', padding: '14px', background: loading ? '#7FB4AC' : '#085041',
              color: '#fff', borderRadius: '14px', border: 'none', fontWeight: '700', fontSize: '14px',
              cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center',
              justifyContent: 'center', gap: '8px', transition: 'background 0.2s',
            }}
          >
            {loading ? (
              <>
                <span className="material-symbols-outlined" style={{ fontSize: '20px', animation: 'spin 1s linear infinite' }}>refresh</span>
                Analyzing with AI…
              </>
            ) : (
              <>
                <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>biotech</span>
                Analyze for Malnutrition
              </>
            )}
          </button>
          <style>{`@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }`}</style>
        </div>
      ) : (
        <div
          onClick={() => fileInputRef.current.click()}
          style={{
            border: '2px dashed #1D9E75', borderRadius: '14px', padding: '32px 20px',
            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
            textAlign: 'center', cursor: 'pointer', transition: 'background 0.2s',
          }}
          onMouseEnter={e => e.currentTarget.style.background = '#EAF3DE'}
          onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
        >
          <span className="material-symbols-outlined" style={{ fontSize: '40px', color: '#1D9E75', marginBottom: '8px' }}>add_a_photo</span>
          <p style={{ fontWeight: '700', color: '#085041', fontSize: '14px' }}>Tap to photograph the child</p>
          <p style={{ fontSize: '12px', color: '#5F5E5A', marginTop: '4px' }}>Gemini AI will assess malnutrition risk visually</p>
        </div>
      )}
      <input type="file" accept="image/*" capture="environment" ref={fileInputRef} onChange={handleCapture} className="hidden" />
    </div>
  );
}
