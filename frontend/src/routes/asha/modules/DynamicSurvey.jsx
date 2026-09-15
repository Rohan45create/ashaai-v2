/**
 * DynamicSurvey.jsx — schema-driven survey renderer for ALL 12 built-in modules
 * and custom supervisor-published surveys.
 *
 * Fetches the survey_template by ?id= param, maps the JSON schema into
 * BaseModuleForm props, and handles:
 *   - sections (repeatable groups)
 *   - pre_gate
 *   - photo fields with ai_action
 *   - computed/derives fields (thresholds read from schema — NEVER hardcoded)
 *   - show_if (string truthy AND { field, equals } object form)
 *   - member_lookup fields (resolve to household_members.id UUID)
 *   - template icon (lucide-react, from DB column)
 *   - Aadhaar linkage popup
 *
 * Child Growth module:
 *   - Renders the AI Malnutrition Scanner widget (via ai_action: malnutrition_grade)
 *   - MUAC color zone auto-derives from muac_mm using schema thresholds
 *   - Orphan/no-parents toggle (pre_gate in schema)
 *
 * Per ARCHITECTURE.md: one write path only — React → Spring Boot REST → Postgres.
 * Per RULES.md: member_lookup failure → visible error, never silent fallback.
 */
import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import EmptyState from '../../../components/EmptyState';
import BaseModuleForm from '../../../components/BaseModuleForm';
import AadhaarLinkagePopup from '../../../components/AadhaarLinkagePopup';
import { apiFetch } from '../../../utils/api';
import {
  Home as HouseIcon, User, Baby, Syringe, Heart, Stethoscope, ClipboardList,
  Microscope, Eye, Hand, Pill, Bandage, Users, MapPin, Droplet, Shield, LineChart, Leaf,
  Activity, Thermometer, Clipboard, BookOpen, Brain, Star, Globe
} from 'lucide-react';

// Maps icon names (from DB) to lucide-react components
const ICON_MAP = {
  house: <HouseIcon className="w-6 h-6" />,
  person: <User className="w-6 h-6" />,
  baby: <Baby className="w-6 h-6" />,
  syringe: <Syringe className="w-6 h-6" />,
  heart: <Heart className="w-6 h-6" />,
  stethoscope: <Stethoscope className="w-6 h-6" />,
  clipboard: <ClipboardList className="w-6 h-6" />,
  microscope: <Microscope className="w-6 h-6" />,
  eye: <Eye className="w-6 h-6" />,
  hand: <Hand className="w-6 h-6" />,
  pill: <Pill className="w-6 h-6" />,
  bandage: <Bandage className="w-6 h-6" />,
  pregnant: <User className="w-6 h-6" />,
  elderly: <User className="w-6 h-6" />,
  family: <Users className="w-6 h-6" />,
  village: <MapPin className="w-6 h-6" />,
  water: <Droplet className="w-6 h-6" />,
  shield: <Shield className="w-6 h-6" />,
  chart: <LineChart className="w-6 h-6" />,
  leaf: <Leaf className="w-6 h-6" />,
  activity: <Activity className="w-6 h-6" />,
  thermometer: <Thermometer className="w-6 h-6" />,
  book: <BookOpen className="w-6 h-6" />,
  brain: <Brain className="w-6 h-6" />,
  star: <Star className="w-6 h-6" />,
  globe: <Globe className="w-6 h-6" />,
};

/**
 * Maps a raw schema field object to BaseModuleForm's field shape.
 * Passes through derives, thresholds, ai_action, show_if, member_lookup.
 */
function mapField(f) {
  return {
    id: f.id.toString(),
    label: f.label_en || f.label || f.id,
    type: f.type,
    required: !!f.required,
    placeholder: f.placeholder_en || f.placeholder,
    options: f.type === 'select'
      ? (Array.isArray(f.options)
          ? f.options
          : (f.options_en || f.options || '').split(',').filter(Boolean).map(o => ({
              value: o.trim(), label: o.trim()
            })))
      : undefined,
    show_if: f.show_if,          // string or { field, equals } — evaluated by BaseModuleForm
    ai_action: f.ai_action,       // 'malnutrition_grade' triggers MalnutritionScannerWidget
    derives: f.derives,           // { from, thresholds } — evaluated by BaseModuleForm
    maxLength: f.maxLength,
    checkboxLabel: f.checkboxLabel,
    householdId: f.householdId,   // optional hint for MemberLookupField create-new path
  };
}

export default function DynamicSurvey() {
  const [searchParams] = useSearchParams();
  const surveyId = searchParams.get('id');
  const [template, setTemplate] = useState(null);
  const [loading, setLoading] = useState(true);

  const [showLinkagePopup, setShowLinkagePopup] = useState(false);
  const [linkageData, setLinkageData] = useState(null);
  const [linkageConfirmedData, setLinkageConfirmedData] = useState(null);

  useEffect(() => {
    if (!surveyId) { setLoading(false); return; }
    const fetchTemplate = async () => {
      try {
        // Try by UUID first, then fall back to searching by module_key
        let snap = null;
        // If surveyId looks like a UUID, fetch directly
        const uuidRe = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
        if (uuidRe.test(surveyId)) {
          snap = await apiFetch(`/api/surveyTemplates/${surveyId}`);
        } else {
          // module_key path: fetch all and find by moduleKey
          const all = await apiFetch('/api/surveyTemplates');
          snap = Array.isArray(all)
            ? all.find(t => t.moduleKey === surveyId)
            : null;
        }
        if (snap && (snap.id || snap.moduleKey)) {
          setTemplate(snap);
        }
      } catch (err) {
        console.error('[DynamicSurvey] failed to load template:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchTemplate();
  }, [surveyId]);

  if (loading) {
    return (
      <div className="flex justify-center items-center py-16">
        <span className="material-symbols-outlined animate-spin text-3xl text-[#1D9E75]">refresh</span>
      </div>
    );
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
        <EmptyState module="default" message="Survey not found. It may have been deactivated or the link is incorrect." />
      </div>
    );
  }

  // ── Parse template fields ──────────────────────────────────────────────────
  let parsedFields = template.fields;
  if (typeof parsedFields === 'string') {
    try { parsedFields = JSON.parse(parsedFields); } catch { parsedFields = {}; }
  }

  // Schema shape: { fields: [...], sections: [...], pre_gate: {...} }
  const rawFields    = Array.isArray(parsedFields) ? parsedFields : (parsedFields?.fields || []);
  const rawSections  = parsedFields?.sections;
  const preGate      = parsedFields?.pre_gate;

  const formFields   = rawSections ? null : rawFields.map(mapField);
  const formSections = rawSections
    ? rawSections.map(sec => ({ ...sec, fields: sec.fields.map(mapField) }))
    : null;

  // ── Aadhaar field detection ────────────────────────────────────────────────
  const hasAadhaarField = rawSections
    ? rawSections.some(s => s.fields.some(f => f.type === 'aadhaar'))
    : rawFields.some(f => f.type === 'aadhaar');

  // ── Lucide icon from DB ─────────────────────────────────────────────────
  // template.icon is the DB column (e.g. "baby", "heart")
  const lucideIcon = template.icon ? (ICON_MAP[template.icon] || ICON_MAP['clipboard']) : null;

  // ── collection / routing ────────────────────────────────────────────────
  // Built-in modules have a moduleKey that maps to a real Postgres table
  const MODULE_COLLECTION_MAP = {
    child_growth: 'children',
    anc: 'pregnancies',
    vaccination: 'vaccinations',
    family_survey: 'household_members',
    village_health: 'village_health',
    disease_surveillance: 'disease_cases',
    birth_record: 'birth_records',
    death_record: 'death_records',
    ncd_tracking: 'ncd_records',
    family_planning: 'family_planning',
    sanitation: 'sanitation',
    elderly_care: 'elderly_care',
  };
  const collection = MODULE_COLLECTION_MAP[template.moduleKey] || 'dynamic_submissions';

  // ── Linkage handlers ────────────────────────────────────────────────────
  const handleAadhaarEntered = async (last4) => {
    if (!template.hasLinkage && !template.moduleKey) return;
    try {
      const result = await apiFetch('/api/members/check-linkage', {
        method: 'POST',
        body: JSON.stringify({
          aadhaar_last4: last4,
          module_type: template.moduleKey || template.connectedSurvey,
        }),
      });
      if (result.match_found) {
        setLinkageData(result);
        setShowLinkagePopup(true);
      }
    } catch (err) {
      console.log('[DynamicSurvey] Linkage check skipped:', err);
    }
  };

  const handleAfterSubmit = async (docId) => {
    if (!linkageConfirmedData) return;
    try {
      await apiFetch('/api/members/confirm-linkage', {
        method: 'POST',
        body: JSON.stringify({
          record_collection: collection,
          record_id: docId,
          household_id: linkageConfirmedData.household_id,
          member_id: linkageConfirmedData.member_id,
        }),
      });
    } catch (err) {
      console.error('[DynamicSurvey] Linkage confirmation failed:', err);
    }
  };

  const title = template.nameEn || template.title || 'Survey';

  return (
    <>
      <BaseModuleForm
        title={title}
        moduleIcon="assignment"
        templateIcon={lucideIcon}
        collectionName={collection}
        moduleName={template.moduleKey || 'dynamic'}
        fields={formFields}
        sections={formSections}
        preGate={preGate}
        showAadhaar={hasAadhaarField}
        aadhaarPersonLabel="Participant"
        onAadhaarScanned={handleAadhaarEntered}
        afterSubmit={handleAfterSubmit}
        extraData={{ templateId: template.id, moduleKey: template.moduleKey }}
      />
      <AadhaarLinkagePopup
        isOpen={showLinkagePopup}
        memberName={linkageData?.member_name}
        familyHeadName={linkageData?.family_head}
        moduleType={title}
        onConfirm={() => {
          setShowLinkagePopup(false);
          setLinkageConfirmedData(linkageData);
        }}
        onReject={() => { setShowLinkagePopup(false); setLinkageConfirmedData(null); }}
        onClose={() => { setShowLinkagePopup(false); setLinkageConfirmedData(null); }}
      />
    </>
  );
}
