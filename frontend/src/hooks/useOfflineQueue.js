import { useEffect } from 'react';
import useSyncStore from '../stores/syncStore';
import { apiFetch } from '../utils/api';

// Native indexedDB implementation for an isolated offline queue for non-firestore APIs
const DB_NAME = 'AshaAIOfflineQueue';
const STORE_NAME = 'requests';

const initDB = () => {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = (event) => {
      const db = event.target.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'id', autoIncrement: true });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
};

export const getOfflineQueueCount = async () => {
  try {
    const db = await initDB();
    return new Promise((resolve) => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const store = tx.objectStore(STORE_NAME);
      const countReq = store.count();
      countReq.onsuccess = () => resolve(countReq.result || 0);
      countReq.onerror = () => resolve(0);
    });
  } catch (e) {
    return 0;
  }
};

const useOfflineQueue = () => {
  const { isOnline } = useSyncStore();

  const syncPendingCount = async () => {
    const count = await getOfflineQueueCount();
    useSyncStore.getState().setPendingCount(count);
  };

  const addToQueue = async (url, options) => {
    const db = await initDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      
      // Serialize headers and mostly non-DOM objects
      const safeOptions = { ...options };
      if (safeOptions.body instanceof Object && !(safeOptions.body instanceof FormData)) {
        safeOptions.bodyStr = JSON.stringify(safeOptions.body);
        delete safeOptions.body;
      }
      
      store.add({
        url,
        options: safeOptions,
        timestamp: Date.now()
      });
      tx.oncomplete = () => {
        syncPendingCount();
        resolve(true);
      };
      tx.onerror = () => reject(tx.error);
    });
  };

  const processQueue = async () => {
    if (!isOnline) return;
    
    const db = await initDB();
    const tx = db.transaction(STORE_NAME, 'readonly');
    const store = tx.objectStore(STORE_NAME);
    const request = store.getAll();

    request.onsuccess = async () => {
      const items = request.result;
      if (items.length === 0) {
        syncPendingCount();
        return;
      }

      for (const item of items) {
        try {
          const fetchOptions = { ...item.options };
          if (fetchOptions.bodyStr) {
             fetchOptions.body = JSON.parse(fetchOptions.bodyStr);
             delete fetchOptions.bodyStr;
          }
          
          await apiFetch(item.url, fetchOptions);
          
          // If successful, delete from queue
          const deleteTx = db.transaction(STORE_NAME, 'readwrite');
          deleteTx.objectStore(STORE_NAME).delete(item.id);
          deleteTx.oncomplete = () => syncPendingCount();
        } catch (e) {
          console.error("Failed to process queued item", item, e);
        }
      }
    };
  };

  // Sync count from IndexedDB on mount and whenever connection changes
  useEffect(() => {
    syncPendingCount();
    if (isOnline) {
      processQueue();
    }
  }, [isOnline]);

  return { addToQueue, processQueue, syncPendingCount };
};

export default useOfflineQueue;
