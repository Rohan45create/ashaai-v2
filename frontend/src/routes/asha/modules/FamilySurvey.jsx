import React, { useState, useEffect } from 'react';
import AadhaarInput from '../../../components/AadhaarInput';
import DuplicateWarningModal from '../../../components/DuplicateWarningModal';
import { useAuthStore } from '../../../stores/authStore';
import { apiFetch } from '../../../utils/api';
import useOfflineQueue from '../../../hooks/useOfflineQueue';
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom';
import AmbientToggle from '../../../components/AmbientToggle';
import VoiceOverlay from '../../../components/VoiceOverlay';
import { parseToIsoDate, calculateAgeFromDob, extractDobFromText, normalizeGender, normalizeRelationship, normalizeMaritalStatus } from '../../../utils/formDateUtils';

const FAMILY_MEMBER_FIELDS = [
  { id: 'member_name', label: 'Full Name / पूर्ण नाव / Name', type: 'text' },
  { id: 'gender', label: 'Gender / लिंग (Male / Female / Other)', type: 'select' },
  { id: 'date_of_birth', label: 'Date of Birth / जन्मतारीख (YYYY-MM-DD)', type: 'date' },
  { id: 'age', label: 'Age (years) / वय', type: 'number' },
  { id: 'relationship_to_head', label: 'Relationship / नाते (Self / Spouse / Son / Daughter / Father / Mother / Other)', type: 'select' },
  { id: 'marital_status', label: 'Marital Status / वैवाहिक स्थिती (Married / Unmarried / Widow / Separated)', type: 'select' },
  { id: 'mobile_number', label: 'Mobile Number / मोबाईल नंबर (10 digits)', type: 'tel' },
  { id: 'abha_id', label: 'ABHA ID', type: 'text' },
];

async function hashAadhaar(aadhaarString) {
  if (!aadhaarString) return null;
  const msgUint8 = new TextEncoder().encode(aadhaarString);
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgUint8);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
}

export default React.memo(function FamilySurvey() {
  const { ashaId, docId, user } = useAuthStore();
  const { addToQueue } = useOfflineQueue();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const viewState = location.state;
  const isViewMode = viewState?.mode === 'view' || searchParams.get('mode') === 'view';
  const [isEditMode, setIsEditMode] = useState(false);
  const submissionId = viewState?.submissionId || searchParams.get('submissionId');
  const [isDataLoaded, setIsDataLoaded] = useState(false);

  const [household, setHousehold] = useState({
    house_number: '',
    bplStatus: 'No',
  });

  const [initialCount, setInitialCount] = useState(1);
  const [memberCount, setMemberCount] = useState(null);
  const [members, setMembers] = useState([]);
  const [expandedMember, setExpandedMember] = useState(0);

  const [isLoading, setIsLoading] = useState(false);
  const [toast, setToast] = useState(null);
  const [duplicateModal, setDuplicateModal] = useState({ show: false, existingRecord: null, memberIndex: null });
  const [voiceFilledFields, setVoiceFilledFields] = useState({});
  const [showVoice, setShowVoice] = useState(false);

  const showToast = (message, type = 'info') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  const initMembers = (count) => {
    setMemberCount(count);
    setMembers(Array.from({ length: count }, (_, i) => ({
      id: Date.now() + i,
      serial_number: i + 1,
      house_number: household.house_number, 
      member_name: '', gender: '',
      date_of_birth: '', age: '', relationship_to_head: i === 0 ? 'Self' : '',
      marital_status: '', aadhaar_raw: '', aadhaarHash: '', mobile_number: '',
      abha_id: '', birth_register_serial: '',
      reason_removed_from_register: '',
      noAadhaar: false, temporaryId: '',
      is_pregnant: false,
      has_genetic_condition: false, genetic_conditions: '', genetic_condition_notes: '',
      voiceFilled: [], isDuplicate: false, existingId: null
    })));
  };

  useEffect(() => {
    if (viewState?.submissionData) {
      const data = viewState.submissionData;
      const formData = data.data || data.formData || data;
      if (formData) {
        if (formData.household) setHousehold(formData.household);
        if (formData.members) {
          setMembers(formData.members);
          setMemberCount(formData.members.length);
        }
      }
      setIsDataLoaded(true);
    } else if (submissionId && !isDataLoaded) {
      const loadSubmission = async () => {
        setIsLoading(true);
        try {
          const docSnap = await apiFetch(`/api/surveySubmissions/${submissionId}`);
          if (docSnap) {
            const formData = docSnap.data || docSnap.formData || docSnap;
            if (formData) {
              if (formData.household) setHousehold(formData.household);
              if (formData.members) {
                setMembers(formData.members);
                setMemberCount(formData.members.length);
              }
            }
          }
          setIsDataLoaded(true);
        } catch (err) {
          console.error(err);
          showToast('Failed to load submission data', 'error');
        } finally {
          setIsLoading(false);
        }
      };
      loadSubmission();
    }
  }, [submissionId, isDataLoaded, viewState]);

  const updateMember = (index, field, value) => {
    setMembers(prev => prev.map((m, i) => i === index ? { ...m, [field]: value } : m));
  };

  const checkDuplicate = async (index, aadhaarRaw) => {
    if (!aadhaarRaw || aadhaarRaw.length < 12) return;
    try {
      const hash = await hashAadhaar(aadhaarRaw);
      updateMember(index, 'aadhaarHash', hash);
      const last4 = aadhaarRaw.slice(-4);
      const data = await apiFetch(`/api/members/check-duplicate?aadhaar_last4=${last4}&aadhaar_hash=${hash}&aadhaar_raw=${aadhaarRaw}`);
      if (data && data.found) {
        setDuplicateModal({ show: true, existingRecord: data.record, memberIndex: index });
      }
    } catch (e) {
      console.error("Duplicate check failed:", e);
    }
  };

  const handleAadhaarScanned = (index, extracted) => {
    if (!extracted) return;
    if (extracted.name) updateMember(index, 'member_name', extracted.name);
    if (extracted.gender) updateMember(index, 'gender', extracted.gender);
    if (extracted.dob) updateMember(index, 'date_of_birth', extracted.dob);
    if (extracted.aadhaar_raw) {
      updateMember(index, 'aadhaar_raw', extracted.aadhaar_raw);
      checkDuplicate(index, extracted.aadhaar_raw);
    }
  };

  const handleNoAadhaarChange = (index, isChecked) => {
    updateMember(index, 'noAadhaar', isChecked);
    if (isChecked) {
      const tempId = `TMP-${user?.uid?.slice(0, 4).toUpperCase() || 'ASHA'}-${Date.now().toString(36).toUpperCase()}`;
      updateMember(index, 'temporaryId', tempId);
      updateMember(index, 'aadhaar_raw', null);
      updateMember(index, 'aadhaarHash', null);
    } else {
      updateMember(index, 'temporaryId', '');
    }
  };

  const applyDuplicateUpdate = () => {
    const { existingRecord, memberIndex } = duplicateModal;
    setMembers(prev => prev.map((m, i) => {
      if (i === memberIndex) {
        return {
          ...m,
          member_name: existingRecord.member_name || '',
          gender: existingRecord.gender || '',
          date_of_birth: existingRecord.date_of_birth || '',
          mobile_number: existingRecord.mobile_number || '',
          isDuplicate: true,
          existingId: existingRecord.id
        };
      }
      return m;
    }));
    setDuplicateModal({ show: false, existingRecord: null, memberIndex: null });
  };

  const skipDuplicateUpdate = () => {
    setDuplicateModal({ show: false, existingRecord: null, memberIndex: null });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!household.house_number) {
      return showToast('House number is required', 'error');
    }

    setIsLoading(true);
    const familyHeadName = members[0]?.member_name || 'Unknown';
    const resolvedAshaId = docId || ashaId || localStorage.getItem('ashaId') || user?.id || user?.uid;

    let payload = null;
    try {
      const formattedMembers = [];
      for (let idx = 0; idx < members.length; idx++) {
        const m = members[idx];
        let aadhaarHash = m.aadhaarHash;
        if (m.aadhaar_raw && !aadhaarHash) {
          aadhaarHash = await hashAadhaar(m.aadhaar_raw);
        }

        formattedMembers.push({
          serial_number: idx + 1,
          member_name: m.member_name,
          name: m.member_name,
          gender: m.gender,
          date_of_birth: m.date_of_birth || null,
          age: m.age ? parseInt(m.age) : null,
          relationship_to_head: m.relationship_to_head || (idx === 0 ? 'Self' : 'Other'),
          marital_status: m.marital_status || null,
          aadhaar_raw: m.aadhaar_raw || null,
          aadhaar_last4: m.aadhaar_raw && m.aadhaar_raw.length >= 4 ? m.aadhaar_raw.slice(-4) : (m.aadhaarLast4 || null),
          aadhaar_hash: aadhaarHash || null,
          temporary_id: m.temporaryId || null,
          temporaryId: m.temporaryId || null,
          noAadhaar: m.noAadhaar || false,
          mobile_number: m.mobile_number || null,
          abha_id: m.abha_id || null,
          is_pregnant: Boolean(m.is_pregnant),
          pregnant: Boolean(m.is_pregnant),
          has_genetic_condition: Boolean(m.has_genetic_condition),
          genetic_conditions: m.genetic_conditions ? (typeof m.genetic_conditions === 'string' ? m.genetic_conditions.split(',').map(s=>s.trim()) : m.genetic_conditions) : [],
          genetic_condition_notes: m.genetic_condition_notes || '',
          existingId: m.existingId || null,
        });
      }

      payload = {
        moduleKey: 'family_survey',
        ashaId: resolvedAshaId,
        data: {
          moduleKey: 'family_survey',
          household: {
            house_number: household.house_number,
            bplStatus: household.bplStatus,
            familyHeadName,
          },
          members: formattedMembers,
        }
      };

      if (!navigator.onLine) {
        await addToQueue('/api/surveySubmissions', {
          method: 'POST',
          body: payload
        });
        showToast('Offline: Family survey queued for sync', 'info');
      } else {
        await apiFetch('/api/surveySubmissions', {
          method: 'POST',
          body: JSON.stringify(payload)
        });
        showToast('Family Survey saved successfully!', 'success');
      }
      setTimeout(() => navigate(-1), 1000);
    } catch (err) {
      console.error('Error saving family survey:', err);
      if (payload) {
        try {
          await addToQueue('/api/surveySubmissions', {
            method: 'POST',
            body: payload
          });
          showToast('Network issue: Saved to offline sync queue', 'info');
          setTimeout(() => navigate(-1), 1000);
          return;
        } catch (queueErr) {
          console.error('Queue save failed:', queueErr);
        }
      }
      showToast('Error saving survey. Please try again.', 'error');
    } finally {
      setIsLoading(false);
    }
  };
  
  const handleVoiceData = (structuredData) => {
    if (!structuredData || typeof structuredData !== 'object') return;
    
    // Auto-expand target card so user can immediately see populated fields
    const targetIdx = expandedMember === -1 ? 0 : expandedMember;
    setExpandedMember(targetIdx);

    setMembers((prev) => {
      const copy = [...prev];
      const targetIndex = targetIdx < copy.length ? targetIdx : 0;
      const m = { ...copy[targetIndex] };
      
      const newFilled = [];

      // Name — support multiple possible keys from voice extraction
      const rawName = structuredData.member_name || structuredData.name || structuredData.full_name || structuredData.person_name;
      if (rawName) {
        m.member_name = String(rawName).trim();
        newFilled.push('member_name');
      }

      // Gender — normalize English/Hindi/Marathi
      const rawGender = structuredData.gender || structuredData.sex;
      if (rawGender) {
        const normGender = normalizeGender(rawGender);
        if (normGender) {
          m.gender = normGender;
          newFilled.push('gender');
        }
      }

      // Date of Birth — HTML5 date inputs strictly require YYYY-MM-DD
      let rawDob = structuredData.date_of_birth || structuredData.dob || structuredData.birth_date ||
                   structuredData.birthdate || structuredData.dateOfBirth || structuredData.date;
      
      // Also inspect structuredData for any key variation
      if (!rawDob) {
        for (const [k, v] of Object.entries(structuredData)) {
          if (/^(date_?of_?birth|dob|birth_?date|birthdate|जन्मतारीख|जन्म\s*तारीख)/i.test(k) && v) {
            rawDob = v;
            break;
          }
        }
      }

      // If still missing, attempt regex extraction from transcript
      if (!rawDob) {
        const transcriptText = structuredData._transcript || structuredData.transcript || '';
        rawDob = extractDobFromText(transcriptText);
      }

      if (rawDob) {
        const parsedDob = parseToIsoDate(rawDob);
        if (parsedDob && /^\d{4}-\d{2}-\d{2}$/.test(parsedDob)) {
          m.date_of_birth = parsedDob;
          newFilled.push('date_of_birth');
          // Auto-compute age if age was not explicitly dictated
          if (!m.age && !structuredData.age) {
            const calcAge = calculateAgeFromDob(parsedDob);
            if (calcAge !== null && calcAge >= 0) {
              m.age = String(calcAge);
              newFilled.push('age');
            }
          }
        }
      }

      // Age (years)
      const rawAge = structuredData.age || structuredData.age_years;
      if (rawAge !== undefined && rawAge !== null && rawAge !== '') {
        const numAge = parseInt(String(rawAge).replace(/\D+/g, ''), 10);
        if (!isNaN(numAge)) {
          m.age = String(numAge);
          if (!newFilled.includes('age')) newFilled.push('age');
        }
      }

      // Relationship to head
      const rawRel = structuredData.relationship_to_head || structuredData.relationship || structuredData.relation;
      if (rawRel) {
        const normRel = normalizeRelationship(rawRel);
        if (normRel) {
          m.relationship_to_head = normRel;
          newFilled.push('relationship_to_head');
        }
      }

      // Marital status
      const rawMarital = structuredData.marital_status || structuredData.marital;
      if (rawMarital) {
        const normMarital = normalizeMaritalStatus(rawMarital);
        if (normMarital) {
          m.marital_status = normMarital;
          newFilled.push('marital_status');
        }
      }

      // Mobile number
      const rawMobile = structuredData.mobile_number || structuredData.mobile || structuredData.phone;
      if (rawMobile) {
        const digits = String(rawMobile).replace(/\D+/g, '');
        if (digits.length >= 10) {
          m.mobile_number = digits.slice(-10);
          newFilled.push('mobile_number');
        }
      }

      // ABHA ID
      const rawAbha = structuredData.abha_id || structuredData.abha;
      if (rawAbha) {
        m.abha_id = String(rawAbha).trim();
      }

      // Pregnant flag
      if (structuredData.is_pregnant !== undefined) {
        m.is_pregnant = Boolean(structuredData.is_pregnant);
      }

      copy[targetIndex] = m;
      
      setVoiceFilledFields((prevV) => ({
        ...prevV, [m.id]: [...new Set([...(prevV[m.id]||[]), ...newFilled])]
      }));
      return copy;
    });
    setShowVoice(false);
    showToast('Voice data applied successfully', 'success');
  };

  const getVoiceTag = (memberId, field) => {
    if (voiceFilledFields[memberId]?.includes(field)) {
      return <span className="text-xs text-[#1D9E75] font-bold px-2 py-0.5 bg-[#EAF3DE] rounded border border-[#1D9E75] mt-1 inline-flex items-center"><span className="material-symbols-outlined text-[14px] mr-1">mic</span> Voice filled</span>;
    }
    return null;
  };
  
  const getInputClass = (memberId, field) => {
    const isFilled = voiceFilledFields[memberId]?.includes(field);
    return `w-full p-3 border rounded-xl outline-none transition-colors ${
      isFilled ? 'border-[#1D9E75] bg-[#EAF3DE]/30' : 'border-[#D3D1C7] focus:border-[#1D9E75] bg-white'
    }`;
  };

  const removeMember = (indexToRemove) => {
    if (window.confirm("Are you sure you want to remove this family member?")) {
      setMembers(prev => prev.filter((_, i) => i !== indexToRemove));
    }
  };

  const addAnotherMember = () => {
    setMembers(prev => [...prev, {
      id: Date.now(),
      serial_number: prev.length + 1,
      house_number: household.house_number, 
      member_name: '', gender: '',
      date_of_birth: '', age: '', relationship_to_head: '',
      marital_status: '', aadhaar_raw: '', aadhaarHash: '', mobile_number: '',
      abha_id: '', birth_register_serial: '',
      reason_removed_from_register: '',
      noAadhaar: false, temporaryId: '',
      is_pregnant: false,
      has_genetic_condition: false, genetic_conditions: '', genetic_condition_notes: '',
      voiceFilled: [], isDuplicate: false, existingId: null
    }]);
    setExpandedMember(members.length);
  };

  if (memberCount === null) {
    return (
      <div className="p-4 flex flex-col justify-center items-center h-[80vh]">
        <div className="w-16 h-16 bg-[#EAF3DE] rounded-full flex items-center justify-center text-[#1D9E75] mb-6">
          <span className="material-symbols-outlined text-4xl">family_restroom</span>
        </div>
        <h1 className="text-3xl font-bold text-[#1A1A18] mb-2 text-center">New Family Survey</h1>
        <p className="text-[#5F5E5A] mb-8 text-center text-lg">How many family members are in this household?</p>
        
        <div className="flex items-center justify-center gap-6 mb-10 w-full max-w-sm">
           <button 
             type="button" 
             onClick={() => setInitialCount(Math.max(1, initialCount - 1))}
             className="w-16 h-16 rounded-2xl bg-white border-2 border-[#D3D1C7] text-3xl font-bold text-[#1A1A18] flex items-center justify-center shadow-sm active:scale-95 transition-all"
           >
             -
           </button>
           <input 
             type="number" 
             value={initialCount} 
             readOnly
             className="w-24 h-24 text-center text-5xl font-black bg-transparent outline-none border-b-4 border-[#1D9E75]" 
           />
           <button 
             type="button" 
             onClick={() => setInitialCount(initialCount + 1)}
             className="w-16 h-16 rounded-2xl bg-white border-2 border-[#D3D1C7] text-3xl font-bold text-[#1A1A18] flex items-center justify-center shadow-sm active:scale-95 transition-all"
           >
             +
           </button>
        </div>
        
        <div className="w-full max-w-sm">
          <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">House Number</label>
          <input 
            type="text" 
            autoFocus
            value={household.house_number} 
            onChange={e => setHousehold(prev => ({ ...prev, house_number: e.target.value }))} 
            className="w-full p-4 border-2 border-[#D3D1C7] rounded-xl outline-none focus:border-[#1D9E75] mb-8 text-lg" 
            placeholder="E.g., 102/B" 
          />
        </div>

        <button 
          onClick={() => {
            if (!household.house_number) {
              showToast('House number is required to start', 'error');
              return;
            }
            initMembers(initialCount);
          }}
          className="w-full max-w-sm py-4 bg-[#1D9E75] text-white rounded-2xl font-bold text-xl shadow-lg active:scale-95 transition-all"
        >
          Start Survey
        </button>

        {toast && (
          <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 px-6 py-3 rounded-xl shadow-lg font-medium animate-slide-down bg-[#FCEBEB] text-[#791F1F] border border-[#E24B4A]">
            {toast.message}
          </div>
        )}
      </div>
    );
  }

  const isMemberComplete = (m) => m.member_name && m.gender && (m.date_of_birth || m.age);
  const completeCount = members.filter(isMemberComplete).length;
  const isAllComplete = completeCount === members.length && household.house_number;

  return (
    <div className="pb-28">
      {duplicateModal.show && (
        <DuplicateWarningModal 
          existingRecord={duplicateModal.existingRecord} 
          onUpdate={applyDuplicateUpdate} 
          onSkip={skipDuplicateUpdate} 
          onClose={skipDuplicateUpdate}
        />
      )}

      {toast && (
        <div className={`fixed top-4 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-xl shadow-lg text-sm font-medium animate-slide-down ${
          toast.type === 'success' ? 'bg-[#EAF3DE] text-[#085041] border border-[#1D9E75]' : 'bg-[#FCEBEB] text-[#791F1F] border border-[#E24B4A]'
        }`}>
          {toast.message}
        </div>
      )}

      <div className="flex items-center justify-between p-4 bg-white border-b sticky top-0 z-30 shadow-sm">
        <div className="flex items-center gap-2">
          <h2 className="text-xl font-bold text-[#1A1A18]">Family Survey</h2>
        </div>
        <div className="flex items-center gap-2">
          <AmbientToggle module="family_survey" onAcceptSuggestion={() => {}} />
        </div>
      </div>

      <form onSubmit={handleSubmit} className="px-4 mt-6 space-y-4">
        {isViewMode && !isEditMode && (
          <div style={{background:'#EAF3DE', padding:'10px 16px', borderRadius:8, marginBottom:12, display:'flex', justifyContent:'space-between', alignItems:'center'}}>
            <span style={{fontSize:13, color:'#27500A'}}>Viewing submitted record</span>
            <button
              type="button"
              onClick={() => setIsEditMode(true)}
              style={{fontSize:13, color:'#1D9E75', background:'none', border:'none', cursor:'pointer', fontWeight:500}}
            >
              Edit ✏️
            </button>
          </div>
        )}

        <fieldset disabled={isViewMode && !isEditMode} className="space-y-4 border-none p-0 m-0">
        {members.map((member, index) => {
          const isExpanded = expandedMember === index;
          const complete = isMemberComplete(member);
          return (
            <div key={member.id} className={`bg-white rounded-2xl shadow-sm border overflow-hidden ${complete ? 'border-[#1D9E75]' : 'border-[#D3D1C7]'}`}>
              <div 
                className={`w-full p-4 flex items-center justify-between ${isExpanded ? 'bg-[#EAF3DE]/30' : 'hover:bg-gray-50'}`}
              >
                <div 
                   className="flex items-center gap-3 flex-1 cursor-pointer"
                   onClick={() => setExpandedMember(isExpanded ? -1 : index)}
                >
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center font-bold text-sm text-white ${complete ? 'bg-[#1D9E75]' : 'bg-gray-400'}`}>
                    {complete ? <span className="material-symbols-outlined text-[16px]">check</span> : (index + 1)}
                  </div>
                  <span className="font-bold text-[#1A1A18] text-lg">
                    {member.member_name || 'Not yet filled'}
                  </span>
                </div>
                <div className="flex items-center gap-3">
                   <button type="button" onClick={() => removeMember(index)} className="w-8 h-8 rounded-full flex items-center justify-center text-red-500 hover:bg-red-50 transition-colors">
                     <span className="text-2xl leading-none">&minus;</span>
                   </button>
                   <div role="button" onClick={() => setExpandedMember(isExpanded ? -1 : index)} className="text-[#5F5E5A] cursor-pointer">
                     <span className="material-symbols-outlined">{isExpanded ? 'expand_less' : 'expand_more'}</span>
                   </div>
                </div>
              </div>

              {isExpanded && (
                <div className="p-4 space-y-5 border-t border-[#D3D1C7]">
                  <div>
                    {!member.noAadhaar && (
                      <AadhaarInput 
                        memberIndex={index}
                        memberName={member.member_name}
                        onAadhaarScanned={extracted => handleAadhaarScanned(index, extracted)} 
                      />
                    )}
                    
                    <div className="mt-3 flex items-center justify-between bg-gray-50 p-3 rounded-lg border">
                      <label className="flex items-center gap-2 cursor-pointer">
                        <input type="checkbox" className="w-5 h-5 text-[#1D9E75] rounded focus:ring-[#1D9E75]" checked={member.noAadhaar} onChange={e => handleNoAadhaarChange(index, e.target.checked)} />
                        <span className="font-medium text-gray-700">No Aadhaar available?</span>
                      </label>
                    </div>

                    {member.temporaryId && (
                      <div className="mt-3 bg-blue-50 border border-blue-200 p-4 rounded-xl flex items-center justify-between">
                        <div>
                          <p className="text-xs text-blue-600 font-bold uppercase tracking-wider mb-1">Temporary ID Generated</p>
                          <p className="font-mono text-lg tracking-wider text-blue-900">{member.temporaryId}</p>
                        </div>
                        <button type="button" onClick={() => navigator.clipboard.writeText(member.temporaryId)} className="p-2 text-blue-600 hover:bg-blue-100 rounded-lg">
                           <span className="material-symbols-outlined">content_copy</span>
                        </button>
                      </div>
                    )}
                  </div>

                  <div>
                    <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">Full Name <span className="text-[#E24B4A]">*</span></label>
                    <input type="text" required value={member.member_name} onChange={e => updateMember(index, 'member_name', e.target.value)} className={getInputClass(member.id, 'member_name')} placeholder="First & Last Name"/>
                    {getVoiceTag(member.id, 'member_name')}
                  </div>
                  
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">Gender <span className="text-[#E24B4A]">*</span></label>
                      <select required value={member.gender} onChange={e => updateMember(index, 'gender', e.target.value)} className={getInputClass(member.id, 'gender')}>
                        <option value="">Select...</option>
                        <option value="Male">Male</option>
                        <option value="Female">Female</option>
                        <option value="Other">Other</option>
                      </select>
                      {getVoiceTag(member.id, 'gender')}
                    </div>
                    <div>
                      <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">Age / DOB <span className="text-[#E24B4A]">*</span></label>
                      <input type="date" required={!member.age} value={member.date_of_birth || ''} onChange={e => updateMember(index, 'date_of_birth', e.target.value)} className={getInputClass(member.id, 'date_of_birth')} />
                      {getVoiceTag(member.id, 'date_of_birth')}
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                     <div>
                        <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">Relationship</label>
                        <select value={member.relationship_to_head} onChange={e => updateMember(index, 'relationship_to_head', e.target.value)} className={getInputClass(member.id, 'relationship_to_head')}>
                          <option value="Self">Self</option>
                          <option value="Spouse">Spouse</option>
                          <option value="Son">Son</option>
                          <option value="Daughter">Daughter</option>
                          <option value="Father">Father</option>
                          <option value="Mother">Mother</option>
                          <option value="Other">Other</option>
                        </select>
                        {getVoiceTag(member.id, 'relationship_to_head')}
                     </div>
                     <div>
                        <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">Marital Status</label>
                        <select value={member.marital_status || ''} onChange={e => updateMember(index, 'marital_status', e.target.value)} className={getInputClass(member.id, 'marital_status')}>
                          <option value="">Select...</option>
                          <option value="Married">Married</option>
                          <option value="Unmarried">Unmarried</option>
                          <option value="Widow">Widow</option>
                          <option value="Separated">Separated</option>
                        </select>
                        {getVoiceTag(member.id, 'marital_status')}
                     </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                     <div>
                        <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">Mobile</label>
                        <input type="tel" maxLength={10} value={member.mobile_number} onChange={e => updateMember(index, 'mobile_number', e.target.value)} className={getInputClass(member.id, 'mobile_number')} placeholder="10 digits" />
                        {getVoiceTag(member.id, 'mobile_number')}
                     </div>
                     <div>
                        <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">ABHA ID (Optional)</label>
                        <input type="text" value={member.abha_id || ''} onChange={e => updateMember(index, 'abha_id', e.target.value)} className={getInputClass(member.id, 'abha_id')} placeholder="14 digits" />
                     </div>
                  </div>

                  {member.gender === 'Female' && (
                    <div className="bg-pink-50 border border-pink-200 rounded-xl p-3 flex items-center justify-between">
                      <label className="flex items-center gap-2 cursor-pointer font-medium text-pink-900">
                        <input 
                          type="checkbox" 
                          className="w-5 h-5 text-pink-600 rounded focus:ring-pink-500" 
                          checked={member.is_pregnant || false} 
                          onChange={e => updateMember(index, 'is_pregnant', e.target.checked)} 
                        />
                        <span>Currently Pregnant (Auto-link to ANC)</span>
                      </label>
                      {member.is_pregnant && (
                        <span className="text-xs bg-pink-200 text-pink-800 font-bold px-2 py-1 rounded">Draft ANC</span>
                      )}
                    </div>
                  )}

                  <div className="border-t pt-4 mt-2">
                    <label className="flex items-center gap-2 cursor-pointer mb-3">
                      <input type="checkbox" className="w-5 h-5 text-[#1D9E75] rounded focus:ring-[#1D9E75]" checked={member.has_genetic_condition} onChange={e => updateMember(index, 'has_genetic_condition', e.target.checked)} />
                      <span className="font-bold text-[#1A1A18]">Has Genetic / Hereditary Condition?</span>
                    </label>
                    {member.has_genetic_condition && (
                      <div className="space-y-3 bg-[#FCEBEB] p-4 rounded-xl border border-[#E24B4A]">
                        <div>
                          <label className="block text-xs font-bold mb-1 text-[#791F1F]">Conditions (comma separated)</label>
                          <input type="text" value={member.genetic_conditions} onChange={e => updateMember(index, 'genetic_conditions', e.target.value)} className="w-full p-2 border border-[#E24B4A] rounded-lg focus:outline-none" placeholder="e.g. Sickle Cell, Thalassemia, Diabetes" />
                        </div>
                        <div>
                          <label className="block text-xs font-bold mb-1 text-[#791F1F]">Additional Notes</label>
                          <textarea value={member.genetic_condition_notes} onChange={e => updateMember(index, 'genetic_condition_notes', e.target.value)} className="w-full p-2 border border-[#E24B4A] rounded-lg focus:outline-none" rows="2" placeholder="Any specific details..."></textarea>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          )
        })}

        <button 
          type="button" 
          onClick={addAnotherMember} 
          className="w-full py-4 border-2 border-dashed border-[#D3D1C7] text-[#5F5E5A] rounded-2xl font-bold flex items-center justify-center gap-2 hover:bg-gray-50 transition-colors"
        >
          <span className="text-2xl leading-none">+</span> Add Another Member
        </button>

        <div className="h-8"></div>
        </fieldset>

        {(!isViewMode || isEditMode) && (
          <div className="fixed bottom-0 left-0 right-0 p-4 bg-white border-t border-[#D3D1C7] shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)] z-30">
             <div className="flex flex-col space-y-2 max-w-lg mx-auto">
               <div className="flex justify-between text-sm font-medium">
                  <span className="text-gray-600">Progress</span>
                  <span className={isAllComplete ? "text-[#1D9E75]" : "text-amber-500"}>{completeCount} of {members.length} members complete</span>
               </div>
               <div className="w-full h-2 bg-gray-200 rounded-full overflow-hidden">
                  <div 
                     className={`h-full transition-all duration-500 ${isAllComplete ? 'bg-[#1D9E75]' : 'bg-amber-500'}`} 
                     style={{ width: `${Math.round((completeCount / members.length) * 100)}%` }}
                  ></div>
               </div>
               
               <button 
                  type="submit" 
                  disabled={isLoading || !isAllComplete} 
                  className={`w-full mt-3 py-4 rounded-xl font-bold shadow-md transition-all flex justify-center items-center gap-2 ${
                    isAllComplete && !isLoading ? 'bg-[#1D9E75] text-white hover:bg-[#16815e] active:scale-95' : 'bg-gray-200 text-gray-400 cursor-not-allowed'
                  }`}
               >
                  {isLoading ? <span className="material-symbols-outlined animate-spin">refresh</span> : 'Save Survey & Confirm'}
               </button>
             </div>
          </div>
        )}
      </form>
      
      <button
        type="button"
        onClick={() => setShowVoice(true)}
        style={{ position: 'fixed', bottom: '100px', right: '20px' }}
        className="w-[72px] h-[72px] bg-[#1D9E75] text-white rounded-full flex items-center justify-center shadow-[0_8px_30px_rgba(29,158,117,0.3)] hover:scale-105 active:scale-95 transition-transform z-40"
      >
        <span className="material-symbols-outlined text-4xl">mic</span>
      </button>

      {showVoice && (
        <VoiceOverlay 
          moduleType="family_survey" 
          formFields={FAMILY_MEMBER_FIELDS}
          onFieldsFilled={handleVoiceData} 
          onClose={() => setShowVoice(false)}
        />
      )}
    </div>
  );
});
