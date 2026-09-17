import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../../utils/api';
import { saveFeedback } from '../../utils/feedbackDb';
import NRC_FIELD_MAP from '../../config/nrc_referral_field_map.json';

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

export default function MalnutritionScannerWidget({ onGradeConfirmed, prefillData, householdMemberId }) {
  const navigate = useNavigate();
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
  const [appliedToSurvey, setAppliedToSurvey] = useState(false);
  const [appliedWithNrc, setAppliedWithNrc] = useState(false);

  // Language toggle state & in-memory translation cache (per scan report)
  const [activeLang, setActiveLang] = useState('en');          // 'en' | 'hi' | 'mr'
  const [translationCache, setTranslationCache] = useState({}); // { hi: translatedObj, mr: translatedObj }
  const [translateLoading, setTranslateLoading] = useState(false);
  const [translateError, setTranslateError] = useState(null);

  // Manual Input fields
  const [age, setAge] = useState(prefillData?.age_months || prefillData?.age || '');
  const [gender, setGender] = useState(prefillData?.gender || '');
  const [height, setHeight] = useState(prefillData?.height_cm || prefillData?.current_height_cm || '');
  const [weight, setWeight] = useState(prefillData?.weight_kg || prefillData?.current_weight_kg || '');
  
  const fileInputRef = useRef();

  // The displayed report: translated variant when a non-English lang is active and cached, otherwise original
  const displayResult = (activeLang !== 'en' && translationCache[activeLang]) ? translationCache[activeLang] : gradeResult;

  // ── Language toggle handler with client-side caching ─────────────────────
  const handleLangToggle = async (lang) => {
    if (lang === 'en') {
      setActiveLang('en');
      setTranslateError(null);
      return;
    }
    // Instant cache hit — switch without repeated network requests to Groq
    if (translationCache[lang]) {
      setActiveLang(lang);
      setTranslateError(null);
      return;
    }
    setActiveLang(lang);
    setTranslateLoading(true);
    setTranslateError(null);
    try {
      const translated = await apiFetch(`/api/malnutrition-reports/translate?lang=${lang}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(gradeResult),
      });
      setTranslationCache((prev) => ({ ...prev, [lang]: translated }));
    } catch (err) {
      setTranslateError('Translation unavailable. Showing English report.');
      setActiveLang('en');
    } finally {
      setTranslateLoading(false);
    }
  };

  // ── Apply to Survey / Send NRC Referral handler ──────────────────────────
  // Fills generated results and malnutrition report into respective survey fields.
  // When referToNrc is true, sets referred_to_nrc = true.
  const handleApplyToSurvey = (referToNrc = true) => {
    const finalGrade = (isCorrect === false && correctedGrade) ? correctedGrade : (gradeResult?.grade || 'YELLOW');
    const finalMuacMm = gradeResult?.muac_mm;
    const computedMuacCm = finalMuacMm ? parseFloat((finalMuacMm / 10).toFixed(1)) : undefined;

    const illnessSignsText = (gradeResult?.visible_signs && gradeResult.visible_signs.length > 0)
      ? gradeResult.visible_signs.join(', ')
      : (gradeResult?.explanation || '');

    const payload = {
      weight_kg: weight || prefillData?.weight_kg || prefillData?.current_weight_kg || gradeResult?.weight_kg || '',
      height_cm: height || prefillData?.height_cm || prefillData?.current_height_cm || gradeResult?.height_cm || '',
      muac_cm: computedMuacCm || prefillData?.muac_cm || '',
      muac_color: finalGrade === 'RED' ? 'RED' : finalGrade === 'YELLOW' ? 'YELLOW' : 'GREEN',
      malnutritionGrade: finalGrade,
      age_months: age || prefillData?.age_months || prefillData?.age || '',
      gender: gender || prefillData?.gender || '',
      child_name: prefillData?.child_name || prefillData?.name || '',
      mother_name: prefillData?.mother_name || prefillData?.mother_lookup || '',
      mother_lookup: prefillData?.mother_lookup || prefillData?.mother_name || '',
      referred_to_nrc: referToNrc,
      illness_signs: illnessSignsText,
      malnutrition_report: gradeResult,
      _prefill_member_id: householdMemberId || prefillData?.household_member_id || prefillData?.householdMemberId || '',
      _prefill_household_id: prefillData?.household_id || prefillData?.householdId || '',
    };

    (NRC_FIELD_MAP.mappings || []).forEach((mapping) => {
      if (mapping.value !== undefined && mapping.field_id !== 'referred_to_nrc') {
        payload[mapping.field_id] = mapping.value;
      }
    });

    if (onGradeConfirmed) {
      onGradeConfirmed(payload);
      setAppliedToSurvey(true);
      setAppliedWithNrc(referToNrc);
      setGradeConfirmed(true);
      setTimeout(() => {
        const target = document.getElementById('child_name') || document.querySelector('form');
        if (target) {
          target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      }, 150);
    } else {
      const encoded = btoa(JSON.stringify(payload));
      navigate(`/asha/dynamic-survey?id=child_growth&prefill=${encoded}`);
    }
  };

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
      setAppliedToSurvey(false);
      setAppliedWithNrc(false);
      setActiveLang('en');
      setTranslationCache({});
      setTranslateError(null);
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
    setAppliedToSurvey(false);
    setAppliedWithNrc(false);
    setActiveLang('en');
    setTranslationCache({});
    setTranslateError(null);
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

  // Lang toggle labels
  const LANG_LABELS = [
    { code: 'en', label: 'English' },
    { code: 'hi', label: 'हिंदी' },
    { code: 'mr', label: 'मराठी' },
  ];

  return (
    <div className="bg-white rounded-xl p-3.5 sm:p-4 shadow-sm border border-[#D3D1C7] space-y-3 mb-4 w-full max-w-full min-w-0 box-border overflow-hidden">
      <div className="flex items-center space-x-3 mb-1">
        <div className="w-9 h-9 bg-[#FCEBEB] rounded-full flex items-center justify-center text-[#791F1F] flex-shrink-0">
          <span className="material-symbols-outlined text-lg">vital_signs</span>
        </div>
        <div className="min-w-0">
          <h3 className="font-bold text-[#1A1A18] text-sm sm:text-base">AI Malnutrition Scan</h3>
          <p className="text-xs text-[#5F5E5A] truncate">Take a photo of the child — Gemini will assess malnutrition risk</p>
        </div>
      </div>

      {gradeResult ? (
        <div className="w-full max-w-full min-w-0 box-border">
          {gradeResult.error ? (
            <div style={{ background: '#FCEBEB', border: '1px solid #E24B4A', borderRadius: '12px', padding: '14px', width: '100%', boxSizing: 'border-box' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#791F1F', fontWeight: '700' }}>
                <span className="material-symbols-outlined">error</span>
                Analysis Failed
              </div>
              <p style={{ fontSize: '13px', color: '#791F1F', marginTop: '6px' }}>{gradeResult.explanation || 'Could not analyze the photo. Please try again with a clear image.'}</p>
              <button type="button" onClick={clearPhoto} className="mt-3 text-xs underline font-medium text-[#791F1F]">Try Again</button>
            </div>
          ) : (
            <div style={{ background: cfg.bg, border: `1.5px solid ${cfg.border}`, borderRadius: '14px', padding: '14px', width: '100%', maxWidth: '100%', minWidth: 0, boxSizing: 'border-box', overflow: 'hidden' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '6px', marginBottom: '10px', flexWrap: 'wrap', width: '100%', minWidth: 0, boxSizing: 'border-box' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0 }}>
                  <div style={{ background: cfg.badgeBg, borderRadius: '50%', width: '32px', height: '32px', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <span className="material-symbols-outlined" style={{ color: '#fff', fontSize: '18px' }}>{cfg.icon}</span>
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <p style={{ fontSize: '10px', fontWeight: '600', color: cfg.text, opacity: 0.7, textTransform: 'uppercase', letterSpacing: '0.05em', margin: 0 }}>AI Assessment</p>
                    <h4 style={{ fontSize: '15px', fontWeight: '800', color: cfg.text, margin: 0, whiteSpace: 'nowrap' }}>{displayResult?.severity_label || cfg.label}</h4>
                  </div>
                </div>

                {/* ── Language Toggle (Feature 1) ── always visible at top of assessment card */}
                <div style={{ display: 'flex', gap: '3px', background: 'rgba(0,0,0,0.06)', padding: '2px', borderRadius: '16px', flexShrink: 0 }}>
                  {LANG_LABELS.map(({ code, label }) => (
                    <button
                      key={code}
                      type="button"
                      disabled={translateLoading}
                      onClick={() => handleLangToggle(code)}
                      style={{
                        padding: '3px 8px',
                        borderRadius: '12px',
                        border: 'none',
                        background: activeLang === code ? '#fff' : 'transparent',
                        color: activeLang === code ? '#1A1A18' : '#5F5E5A',
                        fontWeight: activeLang === code ? '700' : '500',
                        fontSize: '11px',
                        cursor: translateLoading ? 'not-allowed' : 'pointer',
                        boxShadow: activeLang === code ? '0 1px 2px rgba(0,0,0,0.1)' : 'none',
                        transition: 'all 0.15s',
                      }}
                    >
                      {translateLoading && activeLang === code ? '…' : label}
                    </button>
                  ))}
                </div>
              </div>

              <ConfidenceBar value={gradeResult.confidence ?? 0} />

              {/* Machine-translation disclaimer */}
              {activeLang !== 'en' && !translateLoading && translationCache[activeLang] && (
                <div style={{ marginTop: '10px', padding: '8px 12px', background: '#FFF8E1', border: '1px solid #FFCA28', borderRadius: '8px', display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
                  <span className="material-symbols-outlined" style={{ fontSize: '16px', color: '#7A5500', marginTop: '1px', flexShrink: 0 }}>info</span>
                  <p style={{ fontSize: '11px', color: '#7A5500', lineHeight: '1.5', margin: 0 }}>
                    <strong>Machine-translated — English is the record of authority.</strong><br />
                    {activeLang === 'hi' ? 'यह स्वचालित अनुवाद है — अभिलेख अंग्रेजी में है।' : 'हे स्वयंचलित भाषांतर आहे — अधिकृत नोंद इंग्रजीत आहे.'}
                  </p>
                </div>
              )}

              {translateError && (
                <p style={{ fontSize: '11px', color: '#E24B4A', marginTop: '6px' }}>{translateError}</p>
              )}

              {displayResult?.explanation && (
                <p style={{ fontSize: '13px', color: cfg.text, marginTop: '12px', lineHeight: '1.55' }}>
                  {displayResult.explanation}
                </p>
              )}

              {displayResult?.visible_signs && displayResult.visible_signs.length > 0 && (
                <div style={{ marginTop: '12px' }}>
                  <p style={{ fontSize: '11px', fontWeight: '700', color: cfg.text, textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '6px' }}>Observed Signs</p>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                    {displayResult.visible_signs.map((sign, i) => (
                      <span key={i} style={{ background: 'rgba(0,0,0,0.08)', borderRadius: '20px', padding: '3px 10px', fontSize: '12px', color: cfg.text, fontWeight: '500' }}>
                        {sign}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {displayResult?.recommendation && (
                <div style={{ marginTop: '12px', background: 'rgba(0,0,0,0.06)', borderRadius: '10px', padding: '10px 12px' }}>
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
                    <span className="material-symbols-outlined" style={{ fontSize: '16px', color: cfg.text, marginTop: '1px', flexShrink: 0 }}>medical_services</span>
                    <p style={{ fontSize: '13px', color: cfg.text, fontWeight: '500', lineHeight: '1.5' }}>{displayResult.recommendation}</p>
                  </div>
                </div>
              )}

              {/* ── Action Buttons: Send NRC Referral & Apply to Survey Fields ── */}
              <div style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {appliedToSurvey && (
                  <div style={{ background: '#EAF3DE', border: '1.5px solid #1D9E75', borderRadius: '12px', padding: '10px 14px', display: 'flex', alignItems: 'center', gap: '8px', color: '#085041', fontWeight: '700', fontSize: '13px' }}>
                    <span className="material-symbols-outlined text-[20px]">verified</span>
                    <span>
                      {appliedWithNrc ? 'Report applied to survey — Referred to NRC pre-checked' : 'Report applied to survey fields below'}
                    </span>
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => handleApplyToSurvey(true)}
                  style={{
                    width: '100%', padding: '13px', background: '#085041', color: '#fff',
                    borderRadius: '12px', border: 'none', fontWeight: '800', fontSize: '14px',
                    cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
                    gap: '8px', boxShadow: '0 3px 12px rgba(8,80,65,0.25)', transition: 'opacity 0.15s',
                  }}
                >
                  <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>assignment_add</span>
                  Send NRC Referral
                </button>

                <button
                  type="button"
                  onClick={() => handleApplyToSurvey(false)}
                  style={{
                    width: '100%', padding: '11px', background: '#fff', color: '#085041',
                    borderRadius: '12px', border: '1.5px solid #1D9E75', fontWeight: '700', fontSize: '13px',
                    cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
                    gap: '8px', transition: 'background 0.15s',
                  }}
                >
                  <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>playlist_add_check</span>
                  Apply to Survey Fields (No Referral)
                </button>

                <p style={{ fontSize: '11px', color: '#5F5E5A', textAlign: 'center', margin: 0 }}>
                  Fills MUAC, grade, clinical signs &amp; report into survey fields — worker reviews &amp; submits.
                </p>
              </div>

              {/* ── AI Model Verification & Feedback ── */}
              <div style={{ marginTop: '16px', borderTop: '1px solid #E8E7E0', paddingTop: '14px' }}>
                {!feedbackSubmitted ? (
                  <div>
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
                          const shouldRefer = finalGrade === 'RED' || finalGrade === 'YELLOW' || gradeResult.needs_nrc_referral;
                          handleApplyToSurvey(shouldRefer);
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
                        Confirm Feedback &amp; Apply to Record
                      </button>
                    )}
                  </div>
                ) : (
                  <div style={{ background: '#EAF3DE', border: '1.5px solid #1D9E75', borderRadius: '12px', padding: '12px 16px', display: 'flex', alignItems: 'center', gap: '8px', color: '#085041', fontWeight: '700', fontSize: '13px' }}>
                    <span className="material-symbols-outlined text-[20px]">verified</span>
                    Grade Confirmed by ASHA — Applied to Record
                  </div>
                )}

                <button type="button" onClick={clearPhoto} style={{ marginTop: '14px', fontSize: '12px', fontWeight: '600', color: cfg.text, textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
                  Scan Again
                </button>
              </div>
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
