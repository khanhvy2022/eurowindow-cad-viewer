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
    EurowindowBridge: {
      initViewer: (containerId: string) => Promise<boolean>;
      loadDrawingFromBase64: (fileName: string, base64Data: string) => Promise<boolean>;
      loadDrawingFromArrayBuffer: (fileName: string, buffer: ArrayBuffer) => Promise<boolean>;
      zoomToFit: () => void;
      getLayers: () => Array<{ id: string; name: string; isOff: boolean; color: number }>;
      setLayerVisibility: (layerName: string, visible: boolean) => void;
      closeDrawing: () => Promise<void>;
      getDrawingInfo: () => Record<string, unknown>;
    };
  }
}

// Ensure DWG support is strictly disabled by default (Phase P0.1 Policy)
const ENABLE_COMMERCIAL_DWG_PARSER = false;
if (ENABLE_COMMERCIAL_DWG_PARSER) {
  console.log('[Eurowindow CAD] Commercial DWG Parser registration point (disabled)');
}

let isInitialized = false;

async function initViewer(containerId: string): Promise<boolean> {
  const container = document.getElementById(containerId) as HTMLDivElement;
  if (!container) {
    console.error('Container not found:', containerId);
    return false;
  }

  try {
    const localBaseUrl = (typeof window !== 'undefined' ? window.location.origin : '') + '/cad-data';
    AcApDocManager.createInstance({
      container,
      autoResize: true,
      baseUrl: localBaseUrl,
      useMainThreadDraw: true, // Main-thread drawing uses minimal memory on mobile devices
      disableExport: true,
      openDocumentDefaults: () => ({
        mode: AcEdOpenMode.Read,
        drawNoPlotLayers: false,
        openViewMode: AcApOpenViewMode.Extents
      })
    });

    AcApDocManager.instance.events.documentActivated.addEventListener(args => {
      console.log('[Eurowindow CAD] Document activated:', args.doc.docTitle);
      hideLoadingOverlay();
      sendDocumentLoaded();
    });

    isInitialized = true;
    console.log('[Eurowindow CAD] Viewer initialized successfully.');
    
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

  showLoadingOverlay(`Đang tải bản vẽ: ${fileName}...`);

  const ext = fileName.toLowerCase().split('.').pop();
  if (ext === 'dwg') {
    const err = 'Định dạng DWG hiện đang bị khóa trong phiên bản P0.1 (Yêu cầu cấp phép bản quyền parser thương mại). Vui lòng dùng định dạng DXF.';
    console.warn(err);
    hideLoadingOverlay();
    if (window.AndroidBridge && typeof window.AndroidBridge.onDocumentLoadError === 'function') {
      window.AndroidBridge.onDocumentLoadError(err);
    }
    return false;
  }

  const options: AcApOpenDatabaseOptions = {
    mode: AcEdOpenMode.Read,
    drawNoPlotLayers: false,
    openViewMode: AcApOpenViewMode.Extents
  };

  try {
    const success = await AcApDocManager.instance.openDocument(fileName, buffer, options);
    if (!success) {
      throw new Error(`Failed to parse drawing: ${fileName}`);
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
  return {
    hasDocument: true,
    title: doc.docTitle,
    fileName: doc.fileName,
    layerCount: getLayers().length
  };
}

function sendDocumentLoaded() {
  if (window.AndroidBridge && typeof window.AndroidBridge.onDocumentLoaded === 'function') {
    const info = getDrawingInfo();
    window.AndroidBridge.onDocumentLoaded(JSON.stringify(info));
  }
}

// Expose EurowindowBridge globally to window
window.EurowindowBridge = {
  initViewer,
  loadDrawingFromBase64,
  loadDrawingFromArrayBuffer,
  zoomToFit,
  getLayers,
  setLayerVisibility,
  closeDrawing,
  getDrawingInfo
};

// Auto-initialize when DOM is ready
window.addEventListener('DOMContentLoaded', () => {
  initViewer('cad-container');
});
