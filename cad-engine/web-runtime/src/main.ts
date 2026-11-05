// Strict offline guard: redirect any external cad-data font requests to local origin
if (typeof window !== 'undefined') {
  const originalFetch = window.fetch;
  window.fetch = async function (input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const urlStr = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    if (urlStr.includes('cdn.jsdelivr.net/gh/mlightcad/cad-data')) {
      const localUrl = urlStr.replace(/^https:\/\/cdn\.jsdelivr\.net\/gh\/mlightcad\/cad-data/, window.location.origin + '/cad-data');
      console.log(`[Offline Guard] Redirecting CDN fetch: ${urlStr} -> ${localUrl}`);
      return originalFetch.call(window, localUrl, init);
    }
    return originalFetch.call(window, input, init);
  };

  const originalOpen = XMLHttpRequest.prototype.open;
  (XMLHttpRequest.prototype as any).open = function (method: string, url: string | URL, ...rest: any[]) {
    const urlStr = String(url);
    if (urlStr.includes('cdn.jsdelivr.net/gh/mlightcad/cad-data')) {
      const localUrl = urlStr.replace(/^https:\/\/cdn\.jsdelivr\.net\/gh\/mlightcad\/cad-data/, window.location.origin + '/cad-data');
      console.log(`[Offline Guard] Redirecting CDN XHR: ${urlStr} -> ${localUrl}`);
      return originalOpen.call(this, method, localUrl, ...rest);
    }
    return originalOpen.call(this, method, url, ...rest);
  };
}

import {
  AcApDocManager,
  AcEdOpenMode,
  AcApOpenViewMode,
  type AcApOpenDatabaseOptions
} from '@mlightcad/cad-simple-viewer';
import { AcDbDatabaseConverterManager, AcDbFileType } from '@mlightcad/data-model';
import { LibreDwg } from '@mlightcad/libredwg-web';
import { AcDbLibreDwgMainThreadConverter } from '@mlightcad/libredwg-converter/node';

interface AndroidBridgeInterface {
  onViewerReady?: () => void;
  onDocumentLoaded?: (infoJson: string) => void;
  onDocumentLoadError?: (errorMsg: string) => void;
  onProgress?: (progress: number, message: string) => void;
  postFeasibilityResult?: (reportJson: string) => void;
}

declare global {
  interface Window {
    AndroidBridge?: AndroidBridgeInterface;
    loadCurrentDrawing?: (fileName: string) => Promise<boolean>;
    EurowindowBridge: {
      initViewer: (containerId: string) => Promise<boolean>;
      loadDrawingFromBase64: (fileName: string, base64Data: string) => Promise<boolean>;
      loadDrawingFromArrayBuffer: (fileName: string, buffer: ArrayBuffer) => Promise<boolean>;
      zoomToFit: () => void;
      getLayers: () => Array<{ id: string; name: string; isOff: boolean; color: number }>;
      setLayerVisibility: (layerName: string, visible: boolean) => void;
      closeDrawing: () => Promise<void>;
      getDrawingInfo: () => Record<string, unknown>;
      startMeasureDistance: () => void;
      startMeasureArea: () => void;
      clearMeasurements: () => void;
    };
  }
}

let isInitialized = false;

async function initViewer(containerId: string): Promise<boolean> {
  const container = document.getElementById(containerId) as HTMLDivElement;
  if (!container) {
    console.error('Container not found:', containerId);
    return false;
  }

  try {
    const origin = (typeof window !== 'undefined' ? window.location.origin : 'https://cad.eurowindow.local');
    const localBaseUrl = origin + '/cad-data';
    const wasmDir = `${origin}/assets/web-runtime/workers`;

    // 1. Initialize LibreDwg WebAssembly on the main thread
    console.log('[Eurowindow CAD] Pre-initializing LibreDwg WASM from:', wasmDir);
    try {
      await LibreDwg.create(wasmDir);
      console.log('[Eurowindow CAD] LibreDwg WASM initialized successfully.');
    } catch (e1) {
      console.warn('[Eurowindow CAD] Direct WASM init attempt 1:', e1);
      try {
        await LibreDwg.create('./workers');
        console.log('[Eurowindow CAD] LibreDwg WASM initialized via relative path.');
      } catch (e2) {
        console.warn('[Eurowindow CAD] Direct WASM init attempt 2:', e2);
      }
    }

    // 2. Register main-thread DWG converter (eliminates mobile Web Worker CORS/isolation restrictions)
    const dwgConverter = new AcDbLibreDwgMainThreadConverter();
    AcDbDatabaseConverterManager.instance.register(AcDbFileType.DWG, dwgConverter);

    // 3. Initialize AcApDocManager
    AcApDocManager.createInstance({
      container,
      autoResize: true,
      baseUrl: localBaseUrl,
      useMainThreadDraw: true,
      disableExport: true,
      openDocumentDefaults: () => ({
        mode: AcEdOpenMode.Read,
        drawNoPlotLayers: false,
        openViewMode: AcApOpenViewMode.Extents
      }),
      webworkerFileUrls: {
        mtextRender: './workers/mtext-renderer-worker.js'
      }
    });

    AcApDocManager.instance.events.documentActivated.addEventListener(args => {
      console.log('[Eurowindow CAD] Document activated:', args.doc.docTitle);
      hideLoadingOverlay();
      sendDocumentLoaded();
    });

    isInitialized = true;
    console.log('[Eurowindow CAD] Viewer initialized successfully with Direct DWG + DXF support.');
    
    hideLoadingOverlay();

    if (window.AndroidBridge && typeof window.AndroidBridge.onViewerReady === 'function') {
      window.AndroidBridge.onViewerReady();
    }
    return true;
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    console.error('[Eurowindow CAD] Initialization error:', errorMsg);
    if (window.AndroidBridge && typeof window.AndroidBridge.onDocumentLoadError === 'function') {
      window.AndroidBridge.onDocumentLoadError(errorMsg);
    }
    return false;
  }
}

function showLoadingOverlay(msg: string) {
  const overlay = document.getElementById('loading-overlay');
  const text = document.getElementById('status-text');
  if (overlay) overlay.style.display = 'flex';
  if (text) text.textContent = msg;
}

function hideLoadingOverlay() {
  const overlay = document.getElementById('loading-overlay');
  if (overlay) overlay.style.display = 'none';
}

function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binaryString = window.atob(base64);
  const len = binaryString.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes.buffer;
}

async function loadDrawingFromArrayBuffer(fileName: string, buffer: ArrayBuffer): Promise<boolean> {
  if (!isInitialized) {
    await initViewer('cad-container');
  }

  showLoadingOverlay(`Đang nạp và giải mã bản vẽ: ${fileName}...`);

  const ext = fileName.toLowerCase().split('.').pop();
  if (ext === 'dwg') {
    // Ensure WASM instance is ready before parsing DWG
    try {
      const origin = (typeof window !== 'undefined' ? window.location.origin : 'https://cad.eurowindow.local');
      await LibreDwg.create(`${origin}/assets/web-runtime/workers`);
    } catch (e) {
      console.warn('[Eurowindow CAD] Pre-parse LibreDwg check:', e);
    }
  }

  const options: AcApOpenDatabaseOptions = {
    mode: AcEdOpenMode.Read,
    drawNoPlotLayers: false,
    openViewMode: AcApOpenViewMode.Extents
  };

  try {
    const success = await AcApDocManager.instance.openDocument(fileName, buffer, options);
    if (!success) {
      throw new Error(`Không thể giải mã dữ liệu bản vẽ: ${fileName}`);
    }
    return true;
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    console.error('[Eurowindow CAD] Load drawing error:', msg);
    hideLoadingOverlay();
    if (window.AndroidBridge && typeof window.AndroidBridge.onDocumentLoadError === 'function') {
      window.AndroidBridge.onDocumentLoadError(msg);
    }
    return false;
  }
}

async function loadDrawingFromBase64(fileName: string, base64Data: string): Promise<boolean> {
  const buffer = base64ToArrayBuffer(base64Data);
  return loadDrawingFromArrayBuffer(fileName, buffer);
}

function zoomToFit(): void {
  try {
    const curDoc = AcApDocManager.instance.curDocument;
    if (curDoc && curDoc.view) {
      curDoc.view.zoomToFitDrawing();
    }
  } catch (e) {
    console.warn('[Eurowindow CAD] zoomToFit error:', e);
  }
}

function getLayers(): Array<{ id: string; name: string; isOff: boolean; color: number }> {
  try {
    const doc = AcApDocManager.instance.curDocument;
    if (!doc || !doc.database) return [];
    const layers: Array<{ id: string; name: string; isOff: boolean; color: number }> = [];
    const layerTable = doc.database.tables.layerTable;
    if (layerTable) {
      const records = typeof (layerTable as any).newIterator === 'function'
        ? (layerTable as any).newIterator().toArray()
        : [];
      for (const record of records) {
        layers.push({
          id: record.objectId?.hexHandle || record.name,
          name: record.name,
          isOff: !!record.isOff,
          color: record.color?.colorIndex ?? 7
        });
      }
    }
    return layers;
  } catch (e) {
    console.warn('[Eurowindow CAD] getLayers error:', e);
    return [];
  }
}

function setLayerVisibility(layerName: string, visible: boolean): void {
  try {
    const doc = AcApDocManager.instance.curDocument;
    if (!doc || !doc.database) return;
    const layer = doc.database.tables.layerTable.getAt(layerName);
    if (layer) {
      layer.isOff = !visible;
      doc.view?.requestRender?.();
    }
  } catch (e) {
    console.warn('[Eurowindow CAD] setLayerVisibility error:', e);
  }
}

async function closeDrawing(): Promise<void> {
  try {
    await AcApDocManager.instance.closeDocument();
    showLoadingOverlay('Chưa mở bản vẽ');
  } catch (e) {
    console.warn('[Eurowindow CAD] closeDrawing error:', e);
  }
}

function getDrawingInfo(): Record<string, unknown> {
  const doc = AcApDocManager.instance.curDocument;
  if (!doc) return { hasDocument: false };
  const layers = getLayers();
  return {
    hasDocument: true,
    title: doc.docTitle,
    fileName: doc.fileName,
    layerCount: layers.length,
    layers: layers
  };
}

function sendDocumentLoaded() {
  if (window.AndroidBridge && typeof window.AndroidBridge.onDocumentLoaded === 'function') {
    const info = getDrawingInfo();
    window.AndroidBridge.onDocumentLoaded(JSON.stringify(info));
  }
}

// Global stream loader directly callable from Android
window.loadCurrentDrawing = async function(fileName: string): Promise<boolean> {
  try {
    showLoadingOverlay(`Đang nạp bản vẽ: ${fileName}...`);
    const res = await fetch('https://cad.eurowindow.local/current-drawing-stream');
    if (!res.ok) throw new Error(`HTTP ${res.status} khi tải dữ liệu bản vẽ`);
    const buffer = await res.arrayBuffer();
    return await loadDrawingFromArrayBuffer(fileName, buffer);
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    console.error('[Eurowindow CAD] Stream load error:', msg);
    hideLoadingOverlay();
    if (window.AndroidBridge && typeof window.AndroidBridge.onDocumentLoadError === 'function') {
      window.AndroidBridge.onDocumentLoadError(msg);
    }
    return false;
  }
};

// Expose EurowindowBridge globally to window
window.EurowindowBridge = {
  initViewer,
  loadDrawingFromBase64,
  loadDrawingFromArrayBuffer,
  zoomToFit,
  getLayers,
  setLayerVisibility,
  closeDrawing,
  getDrawingInfo,
  startMeasureDistance: () => {
    try {
      AcApDocManager.instance.sendStringToExecute('measuredistance');
    } catch (e) {
      console.warn('[Eurowindow CAD] startMeasureDistance error:', e);
    }
  },
  startMeasureArea: () => {
    try {
      AcApDocManager.instance.sendStringToExecute('measurearea');
    } catch (e) {
      console.warn('[Eurowindow CAD] startMeasureArea error:', e);
    }
  },
  clearMeasurements: () => {
    try {
      AcApDocManager.instance.sendStringToExecute('clearmeasurements');
    } catch (e) {
      console.warn('[Eurowindow CAD] clearMeasurements error:', e);
    }
  }
};

// Auto-initialize when DOM is ready
window.addEventListener('DOMContentLoaded', () => {
  initViewer('cad-container');
});
