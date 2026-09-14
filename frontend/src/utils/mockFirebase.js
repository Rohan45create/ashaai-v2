import { apiFetch } from './api';

export const db = {};
export const auth = {};

// Simulate Firestore query structure
export const collection = (db, path) => ({ path });
export const query = (col, ...constraints) => ({ ...col, constraints });
export const where = (field, op, value) => ({ field, op, value });
export const orderBy = (field, dir) => ({ orderBy: field, dir });
export const limit = (num) => ({ limit: num });
export const doc = (db, path, id) => ({ path, id });
export const serverTimestamp = () => new Date();
export const Timestamp = { now: () => ({ toDate: () => new Date() }) };

const mapCollectionToEndpoint = (path) => {
    switch (path) {
        case 'pregnancies': return '/api/pregnancies';
        case 'children': return '/api/children';
        case 'households': return '/api/households';
        case 'vaccinations': return '/api/vaccinations';
        case 'survey_templates': return '/api/surveyTemplates';
        default: return `/api/${path}`;
    }
};

export const getDocs = async (q) => {
    const endpoint = mapCollectionToEndpoint(q.path);
    try {
        const data = await apiFetch(endpoint);
        const docs = Array.isArray(data) ? data.map(item => ({
            id: item.id || item.uuid,
            data: () => item
        })) : [];
        return {
            empty: docs.length === 0,
            docs,
            forEach: (cb) => docs.forEach(cb)
        };
    } catch (e) {
        console.error("Mock Firebase getDocs failed", e);
        return { empty: true, docs: [], forEach: () => {} };
    }
};

export const getDoc = async (d) => {
    const endpoint = `${mapCollectionToEndpoint(d.path)}/${d.id}`;
    try {
        const data = await apiFetch(endpoint);
        return {
            exists: () => !!data,
            data: () => data,
            id: data.id || data.uuid
        };
    } catch (e) {
        return { exists: () => false, data: () => ({}) };
    }
};

export const onSnapshot = (q, callback) => {
    // Instead of real WebSockets, we just do a one-time fetch to populate UI
    // In a production PWA, this should poll or use SSE.
    getDocs(q).then(callback);
    return () => {}; // Unsubscribe function
};

export const addDoc = async (col, data) => {
    const endpoint = mapCollectionToEndpoint(col.path);
    const result = await apiFetch(endpoint, {
        method: 'POST',
        body: JSON.stringify(data)
    });
    return { id: result.id || result.uuid };
};

export const updateDoc = async (d, data) => {
    const endpoint = `${mapCollectionToEndpoint(d.path)}/${d.id}`;
    await apiFetch(endpoint, {
        method: 'PUT',
        body: JSON.stringify(data)
    });
};

// Auth stubs (already migrated to Supabase in authStore, but left here for safe imports)
export const getAuth = () => ({});
export const signOut = async () => {};
export const signInWithEmailAndPassword = async () => {};
export const sendPasswordResetEmail = async () => {};
export const RecaptchaVerifier = class {};
export const signInWithPhoneNumber = async () => {};
export const onAuthStateChanged = () => () => {};

export const subscribeToSurveyTemplates = (cb) => {
    apiFetch('/api/surveyTemplates').then(data => cb(data || []));
    return () => {};
};

export const addHousehold = async () => {};
export const addMember = async () => {};
export const updateMember = async () => {};
export const deleteMember = async () => {};
export const markSyncComplete = async () => {};
