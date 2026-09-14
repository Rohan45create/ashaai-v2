import React, { useState, useEffect, useRef, useCallback } from 'react';
import { apiFetch } from '../utils/api';

/**
 * MemberLookupField — search-as-you-type against household_members.
 *
 * Stores the selected household_members.id UUID (never a free-text name)
 * in formData via onSelect(fieldId, { memberId, displayName }).
 *
 * If no match found, offers an inline "Register new person" mini-form
 * that POSTs to /api/households/{householdId}/members (temporary_id path).
 *
 * Per RULES.md: identity-resolution failure returns a visible error;
 * we never silently fall back to a different identity.
 */
export default function MemberLookupField({
  field,
  value,            // current value: { memberId, displayName } or null
  onSelect,         // (fieldId, { memberId, displayName } | null) => void
  disabled = false,
  error,
}) {
  const [query, setQuery] = useState(value?.displayName || '');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [showNewForm, setShowNewForm] = useState(false);
  const [newPerson, setNewPerson] = useState({ name: '', dob: '', gender: '' });
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState('');
  const debounceRef = useRef(null);
  const dropdownRef = useRef(null);

  // Keep display text in sync if value is set externally (e.g. voice fill)
  useEffect(() => {
    if (value?.displayName && value.displayName !== query) {
      setQuery(value.displayName);
    }
  }, [value?.displayName]);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handler = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setShowDropdown(false);
        setShowNewForm(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleQueryChange = useCallback((e) => {
    const q = e.target.value;
    setQuery(q);
    // Clear current selection when user retypes
    if (value?.memberId) onSelect(field.id, null);
    setShowNewForm(false);

    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!q.trim() || q.trim().length < 2) {
      setResults([]);
      setShowDropdown(false);
      return;
    }

    debounceRef.current = setTimeout(async () => {
      setSearching(true);
      try {
        const data = await apiFetch(
          `/api/members/search?q=${encodeURIComponent(q.trim())}`
        );
        setResults(Array.isArray(data) ? data : []);
        setShowDropdown(true);
      } catch {
        setResults([]);
        setShowDropdown(true);
      } finally {
        setSearching(false);
      }
    }, 300);
  }, [field.id, value?.memberId, onSelect]);

  const handleSelect = (member) => {
    const displayName =
      member.name +
      (member.dateOfBirth ? ` (DOB: ${member.dateOfBirth})` : '');
    setQuery(displayName);
    setShowDropdown(false);
    setShowNewForm(false);
    onSelect(field.id, { memberId: member.id, displayName });
  };

  const handleCreateNew = async () => {
    if (!newPerson.name.trim()) {
      setCreateError('Name is required.');
      return;
    }
    setCreateError('');
    setCreating(true);
    try {
      // We need a householdId to POST under MemberController's nested path.
      // The field schema may supply a householdId hint; otherwise we'll use
      // the global household context passed through field.householdId.
      const householdId = field.householdId || 'unknown';
      if (householdId === 'unknown') {
        setCreateError('Cannot create member: household not selected yet.');
        setCreating(false);
        return;
      }
      const body = {
        name: newPerson.name.trim(),
        dateOfBirth: newPerson.dob || null,
        gender: newPerson.gender || null,
        // No Aadhaar → backend generates temporary_id automatically
      };
      const created = await apiFetch(`/api/households/${householdId}/members`, {
        method: 'POST',
        body: JSON.stringify(body),
      });
      const displayName =
        created.name + (created.dateOfBirth ? ` (DOB: ${created.dateOfBirth})` : '');
      setQuery(displayName);
      setShowNewForm(false);
      setShowDropdown(false);
      onSelect(field.id, { memberId: created.id, displayName });
    } catch (err) {
      // Per RULES.md: show a real error, never silently proceed
      setCreateError(`Failed to register: ${err.message || 'Unknown error'}`);
    } finally {
      setCreating(false);
    }
  };

  const isSelected = !!value?.memberId;

  return (
    <div className="mb-4" ref={dropdownRef}>
      <label className="block text-sm font-medium mb-1 text-[#5F5E5A]">
        {field.label}
        {field.required && <span className="text-[#E24B4A] ml-1">*</span>}
      </label>

      {/* Search input */}
      <div className="relative">
        <input
          type="text"
          value={query}
          onChange={handleQueryChange}
          disabled={disabled}
          placeholder={field.placeholder || `Search by name, DOB, or address…`}
          className={`w-full p-3 border rounded-xl outline-none focus:border-[#1D9E75] transition-colors pr-10 ${
            error ? 'border-[#E24B4A]' : isSelected ? 'border-[#1D9E75] bg-[#F0FFF8]' : 'border-[#D3D1C7]'
          }`}
        />
        {searching && (
          <span className="material-symbols-outlined absolute right-3 top-3 text-gray-400 animate-spin text-lg">
            refresh
          </span>
        )}
        {isSelected && !searching && (
          <span className="material-symbols-outlined absolute right-3 top-3 text-[#1D9E75] text-lg">
            verified_user
          </span>
        )}
      </div>

      {/* Confirmed badge */}
      {isSelected && (
        <div className="mt-1.5 flex items-center gap-1.5 text-xs font-semibold text-[#085041]">
          <span className="material-symbols-outlined text-sm">link</span>
          Linked to household_members ID: {value.memberId.slice(0, 8)}…
        </div>
      )}

      {/* Dropdown results */}
      {showDropdown && !disabled && (
        <div className="absolute z-20 mt-1 bg-white border border-[#D3D1C7] rounded-xl shadow-xl overflow-hidden w-full max-h-64 overflow-y-auto">
          {results.length > 0 ? (
            <>
              {results.map((m) => (
                <button
                  key={m.id}
                  type="button"
                  onClick={() => handleSelect(m)}
                  className="w-full text-left px-4 py-3 hover:bg-[#EAF3DE] border-b border-[#F0EFE8] last:border-0 transition-colors"
                >
                  <p className="font-bold text-sm text-[#1A1A18]">{m.name}</p>
                  <p className="text-xs text-[#5F5E5A]">
                    {[m.gender, m.dateOfBirth, m.relationshipToHead]
                      .filter(Boolean)
                      .join(' · ')}
                  </p>
                  {m.temporaryId && (
                    <span className="text-[10px] text-[#BA7517] font-semibold">
                      ID: {m.temporaryId}
                    </span>
                  )}
                </button>
              ))}
              <button
                type="button"
                onClick={() => { setShowNewForm(true); setShowDropdown(false); setNewPerson({ name: query, dob: '', gender: '' }); }}
                className="w-full text-left px-4 py-3 hover:bg-[#FFF3E0] text-[#BA7517] font-semibold text-sm flex items-center gap-2"
              >
                <span className="material-symbols-outlined text-sm">person_add</span>
                Register as new person instead
              </button>
            </>
          ) : (
            <div className="px-4 py-3">
              <p className="text-sm text-[#5F5E5A] mb-2">No match found for "{query}"</p>
              <button
                type="button"
                onClick={() => { setShowNewForm(true); setShowDropdown(false); setNewPerson({ name: query, dob: '', gender: '' }); }}
                className="text-sm font-bold text-[#1D9E75] flex items-center gap-1.5"
              >
                <span className="material-symbols-outlined text-sm">person_add</span>
                Register as new person
              </button>
            </div>
          )}
        </div>
      )}

      {/* Inline new-person mini-form */}
      {showNewForm && !disabled && (
        <div className="mt-3 p-4 border border-[#FFCA28] rounded-xl bg-[#FFFBEA] space-y-3">
          <p className="text-sm font-bold text-[#7A5500] flex items-center gap-2">
            <span className="material-symbols-outlined text-sm">person_add</span>
            Register new household member
          </p>
          <div>
            <label className="block text-xs font-medium text-[#5F5E5A] mb-1">Full Name *</label>
            <input
              type="text"
              value={newPerson.name}
              onChange={(e) => setNewPerson((p) => ({ ...p, name: e.target.value }))}
              className="w-full p-2.5 border border-[#D3D1C7] rounded-xl text-sm outline-none focus:border-[#1D9E75]"
              placeholder="Full name"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-[#5F5E5A] mb-1">Date of Birth</label>
              <input
                type="date"
                value={newPerson.dob}
                onChange={(e) => setNewPerson((p) => ({ ...p, dob: e.target.value }))}
                className="w-full p-2.5 border border-[#D3D1C7] rounded-xl text-sm outline-none focus:border-[#1D9E75]"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-[#5F5E5A] mb-1">Gender</label>
              <select
                value={newPerson.gender}
                onChange={(e) => setNewPerson((p) => ({ ...p, gender: e.target.value }))}
                className="w-full p-2.5 border border-[#D3D1C7] rounded-xl text-sm outline-none focus:border-[#1D9E75] bg-white"
              >
                <option value="">Select…</option>
                <option value="Male">Male</option>
                <option value="Female">Female</option>
                <option value="Other">Other</option>
              </select>
            </div>
          </div>
          <p className="text-xs text-[#7A5500]">
            No Aadhaar needed — a temporary ID will be assigned automatically.
          </p>
          {createError && (
            <p className="text-xs text-[#E24B4A] font-medium">{createError}</p>
          )}
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handleCreateNew}
              disabled={creating}
              className="flex-1 py-2.5 bg-[#1D9E75] text-white rounded-xl font-bold text-sm flex items-center justify-center gap-2 disabled:opacity-50"
            >
              {creating ? (
                <span className="material-symbols-outlined animate-spin text-sm">refresh</span>
              ) : (
                <span className="material-symbols-outlined text-sm">save</span>
              )}
              {creating ? 'Registering…' : 'Register & Link'}
            </button>
            <button
              type="button"
              onClick={() => { setShowNewForm(false); }}
              className="px-4 py-2.5 border border-[#D3D1C7] text-[#5F5E5A] rounded-xl font-medium text-sm"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {error && <p className="text-xs text-[#E24B4A] mt-1">{error}</p>}
    </div>
  );
}
