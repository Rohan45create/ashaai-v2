import { openDB } from 'idb';

const DB_NAME = 'asha-ai-feedback-db';
const DB_VERSION = 1;
const STORE_NAME = 'feedback_store';

export async function initFeedbackDb() {
  return openDB(DB_NAME, DB_VERSION, {
    upgrade(db) {
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'id', autoIncrement: true });
      }
    },
  });
}

export async function saveFeedback(photoBlob, aiGrade, correctedGrade, consented) {
  if (!consented) return;
  const db = await initFeedbackDb();
  await db.add(STORE_NAME, {
    photoBlob,
    aiGrade,
    correctedGrade,
    timestamp: new Date().toISOString(),
    consented,
  });
}

export async function getAllFeedback() {
  const db = await initFeedbackDb();
  return db.getAll(STORE_NAME);
}

export async function clearFeedback() {
  const db = await initFeedbackDb();
  await db.clear(STORE_NAME);
}
