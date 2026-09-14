import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { apiFetch } from '../utils/api';
import { useAuthStore } from '../stores/authStore';
import { useNavigate, useLocation } from 'react-router-dom';
import VoiceOverlay from './VoiceOverlay';
import AmbientToggle from './AmbientToggle';
import AadhaarAutofill from './AadhaarAutofill';
import { useTx } from '../context/TranslationContext';
import MalnutritionScannerWidget from './widgets/MalnutritionScannerWidget';
import MemberLookupField from './MemberLookupField';

/**
 * Evaluates a show_if spec against the current formData.
 * Supports:
 *   - string: shows if formData[string] is truthy
 *   - { field, equals } object: shows if formData[field] === equals
 *   - { field, not_equals } object: shows if formData[field] !== not_equals
 */
function evaluateShowIf(showIf, formData) {
  if (!showIf) return true;
  if (typeof showIf === 'string') return !!formData[showIf];
  if (typeof showIf === 'object') {
    const val = formData[showIf.field];
    if ('equals' in showIf) return val === showIf.equals;
    if ('not_equals' in showIf) return val !== showIf.not_equals;
    if ('truthy' in showIf) return !!val;
  }
  return true;
}

/**
 * Evaluates a `derives` spec against the current formData.
 * Schema shape: { from: "field_id", thresholds: [ { gte, value }, { lt, value } ] }
 * Thresholds are evaluated in order; first match wins.
 */
function evaluateDerives(derives, formData) {
  if (!derives || !derives.from) return '';
  const raw = parseFloat(formData[derives.from]);
  if (isNaN(raw)) return '';
  const thresholds = derives.thresholds || [];
  for (const t of thresholds) {
    if (t.gte !== undefined && raw >= t.gte) return t.value;
    if (t.gt !== undefined && raw > t.gt) return t.value;
    if (t.lte !== undefined && raw <= t.lte) return t.value;
    if (t.lt !== undefined && raw < t.lt) return t.value;
    if (t.eq !== undefined && raw === t.eq) return t.value;
  }
  return '';
}

export default function BaseModuleForm({ title, moduleIcon, templateIcon, collectionName, fields, sections, preGate, moduleName, onSubmit, onFormChange, showAadhaar = true, aadhaarPersonLabel = '', extraData = {}, onAadhaarScanned, afterSubmit, renderCustomTop }) {
  const { user, ashaId: storeAshaId } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();
  const viewState = location.state;
  const isViewMode = viewState?.mode === 'view';
  const tx = useTx();
  
  const [formData, setFormData] = useState(() => {
    if (isViewMode && viewState?.submissionData) {
      return viewState.submissionData;
    }
    return {};
  });
  
  const [gateValue, setGateValue] = useState(true); // pre_gate toggle

  const [isLoading, setIsLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const [toast, setToast] = useState(null);

  useEffect(() => {
    if (isViewMode && viewState?.submissionId) {
      const fetchFullRecord = async () => {
        setIsLoading(true);
        try {
          // We don't have standard GET /{id} on all endpoints, so we fetch all and filter
          let recordData = null;
            // Fallback 1: Try to find the record by ASHA ID and proximity in time without using orderBy (which requires an index)
            const ashaIdToUse = viewState.submissionData?.ashaId || storeAshaId || localStorage.getItem('ashaId') || user?.uid;
            if (ashaIdToUse) {
              const allRecords = await apiFetch('/api/' + collectionName);
              const fallbackSnaps = Array.isArray(allRecords) ? allRecords : [];
              
              if (fallbackSnaps.length > 0) {
                let closestDoc = null;
                
                // Try to find the original submittedAt. It might be in submissionData or we might not have it.
                const submittedAt = viewState.submissionData?.submittedAt || viewState.rawSubmission?.submittedAt;
                if (submittedAt) {
                  const targetTime = submittedAt?.toMillis ? submittedAt.toMillis() : Date.parse(submittedAt);
                  let minDiff = Infinity;
                  
                  fallbackSnaps.forEach(dData => {
                    if (dData.createdAt) {
                      const docTime = dData.createdAt?.toMillis ? dData.createdAt.toMillis() : Date.parse(dData.createdAt);
                      const diff = Math.abs(docTime - targetTime);
                      if (diff < minDiff) {
                        minDiff = diff;
                        closestDoc = dData;
                      }
                    }
                  });
                  
                  if (closestDoc && minDiff < 1000 * 60 * 5) { // within 5 minutes
                    setFormData(closestDoc);
                    return;
                  }
                }
                
                // If we couldn't match time exactly or it fell outside 5 minutes, find the newest one manually
                let newestDoc = null;
                let maxTime = -1;
                fallbackSnaps.forEach(dData => {
                  const docTime = dData.createdAt?.toMillis ? dData.createdAt.toMillis() : (Date.parse(dData.createdAt) || 0);
                  if (docTime > maxTime) {
                    maxTime = docTime;
                    newestDoc = dData;
                  }
                });
                
                setFormData(newestDoc || fallbackSnaps[0]);
                return;
              }
            }
            
            // Fallback 2: The payload might be right there in the state
            if (viewState.submissionData?.formData) {
              setFormData(viewState.submissionData.formData);
            } else {
              setFormData(viewState.submissionData || {});
            }
        } catch (err) {
          console.error("Error fetching full record:", err);
          setFormData(viewState.submissionData?.formData || viewState.submissionData || {});
        } finally {
          setIsLoading(false);
        }
      };
      fetchFullRecord();
    }
  }, [isViewMode, viewState, collectionName]);

  // Voice integration
  const voiceModule = moduleName || collectionName || 'family_survey';
  const [showVoice, setShowVoice] = useState(false);
  const [voiceFilledFields, setVoiceFilledFields] = useState([]);
  const [aadhaarFilledFields, setAadhaarFilledFields] = useState([]);

  const showToast = useCallback((message, type = 'info') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  }, []);

  const handleVoiceFilled = (structuredData) => {
    if (structuredData && typeof structuredData === 'object') {
      const newFilledFields = [];
      setFormData(prev => {
        const merged = { ...prev };
        Object.entries(structuredData).forEach(([key, value]) => {
          if (value !== null && value !== undefined && value !== '') {
            merged[key] = value;
            newFilledFields.push(key);
          }
        });
        return merged;
      });
      setVoiceFilledFields(prev => [...new Set([...prev, ...newFilledFields])]);
      showToast(tx('Voice data applied to form'), 'success');
    }
  };

  // Aadhaar autofill handler
  const handleAadhaarAutofill = (payload, rawAadhaar) => {
    if (onAadhaarScanned && rawAadhaar) {
      onAadhaarScanned(rawAadhaar.slice(-4));
    }
    if (!payload || typeof payload !== 'object') return;
    const filledKeys = [];
    setFormData(prev => {
      const merged = { ...prev };
      Object.entries(payload).forEach(([key, value]) => {
        if (value !== null && value !== undefined && value !== '') {
          merged[key] = String(value);
          filledKeys.push(key);
        }
      });
      if (onFormChange) onFormChange(merged);
      return merged;
    });
    setAadhaarFilledFields(prev => [...new Set([...prev, ...filledKeys])]);
    const count = Object.keys(payload).length;
    showToast(`${tx('Aadhaar scanned')} — ${count} ${tx('field(s) autofilled')}`, 'success');
  };

  const validate = () => {
    const newErrors = {};
    const checkFields = (fs, parentPath = null, index = null) => {
      if (!fs) return;
      fs.forEach(f => {
        let val;
        if (parentPath && index !== null) {
          val = formData[parentPath]?.[index]?.[f.id];
        } else {
          val = formData[f.id];
        }
        
        if (f.required && (val === undefined || val === null || String(val).trim() === '')) {
          const key = parentPath ? `${parentPath}[${index}].${f.id}` : f.id;
          newErrors[key] = `${f.label} is required`;
        }
      });
    };

    if (sections) {
      sections.forEach(sec => {
        if (sec.repeatable) {
          const arr = formData[sec.id] || [];
          arr.forEach((_, idx) => checkFields(sec.fields, sec.id, idx));
        } else {
          checkFields(sec.fields);
        }
      });
    } else {
      checkFields(fields);
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!user) return;
    if (!validate()) {
      showToast(tx('Please fill all required fields'), 'error');
      return;
    }
    const resolvedAshaId = storeAshaId || localStorage.getItem('ashaId') || user?.uid;
    setIsLoading(true);
    try {
      if (onSubmit) {
        await onSubmit(formData);
      } else {
        const result = await apiFetch(`/api/${collectionName}`, {
          method: 'POST',
          body: JSON.stringify({
            ...formData,
            ...extraData,
            ashaId: resolvedAshaId,
            source: 'manual',
          })
        });

        // Backend EditHistoryController doesn't exist yet, so we skip audit trail here 
        // as the server should handle auditing natively.
        // Also skip module_submissions as backend computes activity dynamically.

        if (afterSubmit && result) {
          await afterSubmit(result.id || result.uuid, formData);
        }
      }
      showToast(tx('Record saved successfully!', 'record_saved'), 'success');
      setTimeout(() => navigate(-1), 800);
    } catch (err) {
      console.error(err);
      showToast(tx('Error saving data. Will sync when online.'), 'error');
    } finally {
      setIsLoading(false);
    }
  };

  const handleChange = (e, fieldId, sectionId = null, index = null) => {
    const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
    setFormData(prev => {
      let next;
      if (sectionId && index !== null) {
        const arr = [...(prev[sectionId] || [])];
        if (!arr[index]) arr[index] = {};
        arr[index] = { ...arr[index], [fieldId]: value };
        next = { ...prev, [sectionId]: arr };
      } else {
        next = { ...prev, [fieldId]: value };
      }
      
      if (onFormChange) onFormChange(next);
      return next;
    });

    const errorKey = sectionId && index !== null ? `${sectionId}[${index}].${fieldId}` : fieldId;
    if (errors[errorKey]) {
      setErrors(prev => { const n = { ...prev }; delete n[errorKey]; return n; });
    }
  };

  const handleRepeatCountChange = (e, sectionId, countField) => {
    const count = parseInt(e.target.value, 10) || 0;
    handleChange(e, countField);
    
    setFormData(prev => {
      let arr = [...(prev[sectionId] || [])];
      if (count > arr.length) {
        // pad with empty objects
        arr = arr.concat(Array(count - arr.length).fill({}));
      } else if (count < arr.length) {
        arr = arr.slice(0, count);
      }
      const next = { ...prev, [sectionId]: arr };
      if (onFormChange) onFormChange(next);
      return next;
    });
  };

  const handleAmbientSuggestion = (suggestion) => {
    if (suggestion?.field && suggestion?.value !== undefined) {
      setFormData(prev => ({ ...prev, [suggestion.field]: suggestion.value }));
      showToast(`Applied: ${suggestion.chip_label || suggestion.field}`, 'success');
    }
  };

  return (
    <div className="bg-white rounded-2xl p-5 shadow-sm border border-[#D3D1C7] relative">
      {/* Toast */}
      {toast && (
        <div className={`fixed top-4 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-xl shadow-lg text-sm font-medium flex items-center space-x-2 animate-slide-down ${
          toast.type === 'success' ? 'bg-[#EAF3DE] text-[#085041] border border-[#1D9E75]' :
          toast.type === 'error' ? 'bg-[#FCEBEB] text-[#791F1F] border border-[#E24B4A]' :
          'bg-white text-[#1A1A18] border border-[#D3D1C7]'
        }`}>
          <span className="material-symbols-outlined text-[18px]">
            {toast.type === 'success' ? 'check_circle' : toast.type === 'error' ? 'error' : 'info'}
          </span>
          <span>{toast.message}</span>
        </div>
      )}

      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center space-x-3">
          <div className="w-12 h-12 bg-[#EAF3DE] rounded-full flex items-center justify-center text-[#1D9E75]">
            {/* templateIcon is a lucide-react component; moduleIcon is a material-symbols string */}
            {templateIcon ? (
              <span className="text-[#1D9E75]">{templateIcon}</span>
            ) : (
              <span className="material-symbols-outlined text-2xl">{moduleIcon}</span>
            )}
          </div>
          <h2 className="text-xl font-bold text-[#1A1A18]">{title}</h2>
        </div>
        {/* Ambient AI toggle */}
        <AmbientToggle module={voiceModule} onAcceptSuggestion={handleAmbientSuggestion} />
      </div>

      {/* Custom top section (e.g. ANC genetic prediction) */}
      {renderCustomTop && <renderCustomTop />}

      <form onSubmit={handleSubmit} className="space-y-4">
        {isViewMode && (
          <div style={{background:'#EAF3DE', padding:'10px 16px', borderRadius:8, marginBottom:12, display:'flex', justifyContent:'space-between', alignItems:'center'}}>
            <span style={{fontSize:13, color:'#27500A', fontWeight:500}}>{tx('Viewing submitted record')}</span>
          </div>
        )}
        <fieldset disabled={isViewMode} className="space-y-4 border-none p-0 m-0">
        
        {preGate && !isViewMode && (
          <div className="bg-white rounded-2xl p-5 shadow-sm border border-[#D3D1C7] mb-4">
            <label className="block text-sm font-medium mb-3 text-[#5F5E5A]">
              {tx(preGate.label)}
            </label>
            <div className="flex gap-4">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  name={preGate.id}
                  checked={gateValue === true}
                  onChange={() => setGateValue(true)}
                  className="w-5 h-5 accent-[#1D9E75]"
                />
                <span className="font-medium">Yes</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  name={preGate.id}
                  checked={gateValue === false}
                  onChange={() => setGateValue(false)}
                  className="w-5 h-5 accent-[#1D9E75]"
                />
                <span className="font-medium">No</span>
              </label>
            </div>
            {!gateValue && (
              <div className="mt-4 p-3 bg-[#FFF8E1] border border-[#FFCA28] rounded-xl text-sm text-[#7A5500] font-medium flex items-start gap-2">
                <span className="material-symbols-outlined text-[18px]">info</span>
                <span>You can skip this form as the child does not have parents/guardians present. Record will still be saved.</span>
              </div>
            )}
          </div>
        )}

        {gateValue && (
          <>
            {/* Aadhaar Autofill — shown at top of every form */}
            {showAadhaar && !isViewMode && (
              <AadhaarAutofill
                moduleName={moduleName || 'default'}
                personLabel={aadhaarPersonLabel}
                onAutofill={handleAadhaarAutofill}
              />
            )}

            {/* Render Sections or Fields */}
            {(() => {
              const renderField = (field, sectionId = null, index = null) => {
                // Extended show_if: supports string (truthy) and {field, equals/not_equals} object
                if (field.show_if && !evaluateShowIf(field.show_if, formData)) return null;

                const val = (sectionId && index !== null) ? (formData[sectionId]?.[index]?.[field.id] || '') : (formData[field.id] || '');
                const errorKey = sectionId && index !== null ? `${sectionId}[${index}].${field.id}` : field.id;

                // member_lookup: search-as-you-type against household_members, stores UUID
                if (field.type === 'member_lookup') {
                  return (
                    <MemberLookupField
                      key={errorKey}
                      field={field}
                      value={val || null}
                      onSelect={(fId, selection) => {
                        setFormData(prev => {
                          const next = { ...prev, [fId]: selection };
                          if (onFormChange) onFormChange(next);
                          return next;
                        });
                        if (errors[fId]) setErrors(prev => { const n = { ...prev }; delete n[fId]; return n; });
                      }}
                      disabled={false}
                      error={errors[errorKey]}
                    />
                  );
                }

                // computed: derives its value from another field via schema thresholds (read-only)
                if (field.type === 'computed' || field.derives) {
                  const derived = evaluateDerives(field.derives, formData);
                  // Auto-apply derived value into formData when it changes
                  if (derived && formData[field.id] !== derived) {
                    // Use a ref-safe approach: mutate formData in render is bad;
                    // we schedule a state update only if value actually changed.
                    // Using useEffect is not possible inside renderField, so we
                    // trigger it via a 0-timeout to avoid React batching issues.
                    setTimeout(() => {
                      setFormData(prev => {
                        if (prev[field.id] === derived) return prev;
                        const next = { ...prev, [field.id]: derived };
                        if (onFormChange) onFormChange(next);
                        return next;
                      });
                    }, 0);
                  }
                  const derivedConfig = field.derivedDisplay || {};
                  const colorMap = { GREEN: '#1D9E75', YELLOW: '#F0A500', RED: '#E24B4A',
                    NORMAL: '#1D9E75', MAM: '#F0A500', SAM: '#E24B4A' };
                  const color = colorMap[derived] || '#5F5E5A';
                  return (
                    <div key={errorKey} className="mb-4">
                      <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">
                        {tx(field.label)}
                      </label>
                      <div className="flex items-center gap-3 p-3 border border-[#D3D1C7] rounded-xl bg-gray-50">
                        <span className="material-symbols-outlined" style={{ color }}>
                          {derived === 'GREEN' || derived === 'NORMAL' ? 'check_circle' :
                           derived === 'YELLOW' || derived === 'MAM' ? 'warning' :
                           derived === 'RED' || derived === 'SAM' ? 'crisis_alert' : 'calculate'}
                        </span>
                        <span className="font-bold" style={{ color }}>
                          {derived || '—'}
                        </span>
                        <span className="text-xs text-gray-400 ml-1">(auto-computed)</span>
                      </div>
                    </div>
                  );
                }

                // photo field with ai_action
                if (field.ai_action === 'malnutrition_grade') {
                  return (
                    <MalnutritionScannerWidget 
                      key={errorKey}
                      prefillData={formData} 
                      onGradeConfirmed={(payload) => {
                        setFormData(prev => {
                          const next = { ...prev, ...payload };
                          if (onFormChange) onFormChange(next);
                          return next;
                        });
                      }} 
                    />
                  );
                }

                return (
                  <div key={errorKey} className="mb-4">
                    <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">
                      {tx(field.label)}
                      {field.required && <span className="text-[#E24B4A] ml-1">*</span>}
                    </label>
                    {field.type === 'select' ? (
                      <select
                        required={field.required}
                        value={val}
                        onChange={(e) => handleChange(e, field.id, sectionId, index)}
                        className={`w-full p-3 border rounded-xl outline-none focus:border-[#1D9E75] bg-white transition-colors ${
                          errors[errorKey] ? 'border-[#E24B4A]' : 'border-[#D3D1C7]'
                        }`}
                      >
                        <option value="">{tx('Select...')}</option>
                        {field.options?.map(o => <option key={o.value} value={o.value}>{tx(o.label)}</option>)}
                      </select>
                    ) : field.type === 'checkbox' ? (
                      <div className="flex items-center space-x-2">
                        <input
                          type="checkbox"
                          checked={val || false}
                          onChange={(e) => handleChange(e, field.id, sectionId, index)}
                          className="w-5 h-5 accent-[#1D9E75]"
                        />
                        <span className="text-sm">{tx(field.checkboxLabel || field.label)}</span>
                      </div>
                    ) : field.type === 'textarea' ? (
                      <textarea
                        required={field.required}
                        value={val}
                        onChange={(e) => handleChange(e, field.id, sectionId, index)}
                        placeholder={field.placeholder}
                        rows={3}
                        className={`w-full p-3 border rounded-xl outline-none focus:border-[#1D9E75] transition-colors resize-none ${
                          errors[errorKey] ? 'border-[#E24B4A]' : 'border-[#D3D1C7]'
                        }`}
                      />
                    ) : (
                      <input
                        type={field.type === 'number' ? 'number' : 'text'}
                        required={field.required}
                        value={val}
                        onChange={(e) => {
                          if (field.id === 'memberCount' && sectionId) {
                            handleRepeatCountChange(e, sectionId, field.id);
                          } else {
                            handleChange(e, field.id, sectionId, index);
                          }
                        }}
                        placeholder={field.placeholder}
                        maxLength={field.maxLength}
                        className={`w-full p-3 border rounded-xl outline-none focus:border-[#1D9E75] transition-colors ${
                          errors[errorKey] ? 'border-[#E24B4A]' : 'border-[#D3D1C7]'
                        }`}
                      />
                    )}
                    {errors[errorKey] && (
                      <p className="text-xs text-[#E24B4A] mt-1">{errors[errorKey]}</p>
                    )}
                  </div>
                );
              };

              if (sections) {
                return sections.map(sec => (
                  <div key={sec.id} className="mb-6 p-4 border border-[#D3D1C7] rounded-xl bg-gray-50">
                    <h3 className="font-bold text-lg mb-4 text-[#1A1A18]">{tx(sec.title)}</h3>
                    {sec.repeatable ? (
                      <>
                        <div className="mb-4">
                           {sec.fields.find(f => f.id === sec.repeatCountField) ? null : 
                             <p className="text-sm text-red-500">Repeat count field missing.</p>
                           }
                        </div>
                        {(formData[sec.id] || []).map((_, idx) => (
                          <div key={idx} className="mb-4 p-4 border border-dashed border-[#D3D1C7] rounded-xl bg-white relative">
                            <div className="absolute -top-3 left-3 bg-white px-2 text-xs font-bold text-[#5F5E5A]">Entry {idx + 1}</div>
                            {sec.fields.map(f => renderField(f, sec.id, idx))}
                          </div>
                        ))}
                      </>
                    ) : (
                      sec.fields.map(f => {
                        // if repeatCountField is present, render it here
                        return renderField(f);
                      })
                    )}
                  </div>
                ));
              }

              // Flat fields
              return fields?.map(f => renderField(f));
            })()}
          </>
        )}

        {/* Action buttons */}
        {!isViewMode && (
          <div className="pt-4 space-y-3">
            {/* Submit + Cancel */}
            <div className="flex space-x-3">
              <button type="button" onClick={() => navigate(-1)} className="flex-1 py-3 border border-[#D3D1C7] text-[#5F5E5A] rounded-xl font-medium text-center hover:bg-gray-50 flex justify-center items-center">
                {tx('Cancel', 'cancel')}
              </button>
              <button type="submit" disabled={isLoading} className="flex-1 py-3 bg-[#1D9E75] text-white rounded-xl font-medium text-center shadow-md active:scale-[0.98] flex justify-center items-center">
                {isLoading ? <span className="material-symbols-outlined animate-spin">refresh</span> : tx('Save Record', 'save_record')}
              </button>
            </div>
          </div>
        )}
        </fieldset>
      </form>

      {/* Floating Voice Mic Button */}
      {!isViewMode && (
        <button
          type="button"
          onClick={() => setShowVoice(true)}
          style={{ position: 'fixed', bottom: '80px', right: '20px' }}
          className="w-[72px] h-[72px] bg-[#1D9E75] text-white rounded-full flex items-center justify-center shadow-lg z-40 hover:scale-105 transition-transform"
        >
          <span className="material-symbols-outlined text-4xl">mic</span>
        </button>
      )}

      {showVoice && !isViewMode && (
        <VoiceOverlay 
          moduleType={voiceModule} 
          formFields={fields}
          onFieldsFilled={handleVoiceFilled} 
          onClose={() => setShowVoice(false)} 
        />
      )}

    </div>
  );
}
