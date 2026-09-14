// TODO: Add view mode — same pattern as FamilySurvey.jsx
import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import EmptyState from '../../../components/EmptyState';
import BaseModuleForm from '../../../components/BaseModuleForm';
import AadhaarLinkagePopup from '../../../components/AadhaarLinkagePopup';
import { apiFetch } from '../../../utils/api';

export default function DynamicSurvey() {
  const [searchParams] = useSearchParams();
  const surveyId = searchParams.get('id');
  const [template, setTemplate] = useState(null);
  const [loading, setLoading] = useState(true);

  const [showLinkagePopup, setShowLinkagePopup] = useState(false);
  const [linkageData, setLinkageData] = useState(null);
  const [linkageConfirmedData, setLinkageConfirmedData] = useState(null);

  useEffect(() => {
    if (!surveyId) {
      setLoading(false);
      return;
    }
    const fetchTemplate = async () => {
      try {
        const snap = await apiFetch(`/api/surveyTemplates/${surveyId}`);
        if (snap && snap.id) {
          setTemplate(snap);
        }
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchTemplate();
  }, [surveyId]);

  if (loading) {
    return <div className="p-4 text-center">Loading...</div>;
  }

  if (!template) {
    return (
      <div className="space-y-4">
        <div className="bg-white rounded-2xl p-5 shadow-sm border border-[#D3D1C7]">
          <div className="flex items-center space-x-3 mb-2">
            <div className="w-12 h-12 bg-[#F3E5F5] rounded-full flex items-center justify-center text-[#6A1B9A]">
              <span className="material-symbols-outlined text-2xl">assignment</span>
            </div>
            <div>
              <h2 className="text-xl font-bold text-[#1A1A18]">Dynamic Surveys</h2>
              <p className="text-xs text-[#5F5E5A]">Custom surveys published by your supervisor</p>
            </div>
          </div>
        </div>
        <EmptyState module="default" message="No surveys assigned yet. Your supervisor will publish surveys that will appear here automatically." />
      </div>
    );
  }

  const handleAadhaarEntered = async (last4) => {
    if (template.hasLinkage && template.linkMethod === 'aadhaar') {
      try {
        const result = await apiFetch('/api/members/check-linkage', {
          method: 'POST',
          body: JSON.stringify({ aadhaar_last4: last4, module_type: template.connectedSurvey })
        });
        if (result.match_found) {
          setLinkageData(result);
          setShowLinkagePopup(true);
        }
      } catch (err) {
        console.log('Linkage check skipped', err);
      }
    }
  };

  const handleAfterSubmit = async (docId) => {
    if (linkageConfirmedData && template.hasLinkage) {
      const collMap = {
        'family_survey': 'household_members',
        'child_growth': 'children',
        'anc': 'pregnancies',
        'vaccination': 'vaccinations',
        'village_health': 'village_health'
      };
      
      try {
        await apiFetch('/api/members/confirm-linkage', {
          method: 'POST',
          body: JSON.stringify({
            record_collection: collMap[template.connectedSurvey] || template.connectedSurvey,
            record_id: docId,
            household_id: linkageConfirmedData.household_id,
            member_id: linkageConfirmedData.member_id
          })
        });
      } catch (err) {
        console.error('Linkage confirmation failed', err);
      }
    }
  };

  // The backend might return fields as an array (old schema) or an object (new schema)
  let parsedTemplate = template;
  if (typeof template.fields === 'string') {
    try {
      parsedTemplate = { ...template, fields: JSON.parse(template.fields) };
    } catch (e) { console.error('Failed to parse template fields', e); }
  }

  const rawFields = Array.isArray(parsedTemplate.fields) ? parsedTemplate.fields : (parsedTemplate.fields?.fields || []);
  const rawSections = parsedTemplate.fields?.sections;
  const preGate = parsedTemplate.fields?.pre_gate;

  const mapField = f => ({
    id: f.id.toString(),
    label: f.label_en || f.label,
    type: f.type,
    required: f.required,
    placeholder: f.placeholder_en || f.placeholder,
    options: f.type === 'select' ? 
      (Array.isArray(f.options) ? f.options : 
        (f.options_en || f.options || '').split(',').filter(Boolean).map(o => ({ value: o.trim(), label: o.trim() }))
      ) : undefined,
    show_if: f.show_if,
    ai_action: f.ai_action,
    maxLength: f.maxLength,
    checkboxLabel: f.checkboxLabel
  });

  const formFields = rawSections ? null : rawFields.map(mapField);
  const formSections = rawSections ? rawSections.map(sec => ({
    ...sec,
    fields: sec.fields.map(mapField)
  })) : null;

  const hasAadhaarField = rawSections 
    ? rawSections.some(s => s.fields.some(f => f.type === 'aadhaar'))
    : rawFields.some(f => f.type === 'aadhaar');

  return (
    <>
      <BaseModuleForm 
        title={template.title || template.nameEn}
        moduleIcon="assignment" 
        collectionName={template.moduleKey || 'dynamic_submissions'}
        moduleName={template.moduleKey || 'dynamic'}
        fields={formFields}
        sections={formSections}
        preGate={preGate}
        showAadhaar={hasAadhaarField}
        aadhaarPersonLabel="Participant"
        onAadhaarScanned={handleAadhaarEntered}
        afterSubmit={handleAfterSubmit}
        extraData={{ templateId: template.id }}
      />
      <AadhaarLinkagePopup
        isOpen={showLinkagePopup}
        memberName={linkageData?.member_name}
        familyHeadName={linkageData?.family_head}
        moduleType={template.connectedSurvey}
        onConfirm={() => { setShowLinkagePopup(false); setLinkageConfirmedData(linkageData); }}
        onReject={() => { setShowLinkagePopup(false); setLinkageConfirmedData(null); }}
        onClose={() => { setShowLinkagePopup(false); setLinkageConfirmedData(null); }}
      />
    </>
  );
}

