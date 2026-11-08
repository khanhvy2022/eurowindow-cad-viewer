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
import {
  AcDbDatabaseConverterManager,
  AcDbFileType,
  acdbHostApplicationServices
} from '@mlightcad/data-model';
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
      zoomToFit: (toggleMode?: boolean) => Promise<void>;
      onHostResize: (widthCssPx?: number, heightCssPx?: number) => void;
      getLayers: () => Array<{ id: string; name: string; isOff: boolean; color: number }>;
      setLayerVisibility: (layerName: string, visible: boolean) => void;
      closeDrawing: () => Promise<void>;
      getDrawingInfo: () => Record<string, unknown>;
      startMeasureDistance: () => void;
      startMeasureArea: () => void;
      clearMeasurements: () => void;
      switchToModelSpace: () => void;
    };
  }
}

let isInitialized = false;
let preferOverviewStrip = false;

function getActiveView(): any {
  try {
    const dm = AcApDocManager.instance as any;
    return dm?.curView ?? dm?.context?.view ?? dm?.mainView ?? null;
  } catch {
    return null;
  }
}

function ensureNonZeroContainerSize(container: HTMLElement, explicitW?: number, explicitH?: number) {
  const fallbackW = Math.max(window.innerWidth || 0, document.documentElement.clientWidth || 0, window.screen?.width || 360, 320);
  const fallbackH = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0, (window.screen?.height || 760) - 120, 480);
  const targetW = explicitW && explicitW > 10 ? Math.floor(explicitW) : fallbackW;
  const targetH = explicitH && explicitH > 10 ? Math.floor(explicitH) : fallbackH;

  if (explicitW && explicitH && explicitW > 10 && explicitH > 10) {
    container.style.width = `${targetW}px`;
    container.style.height = `${targetH}px`;
  } else if (container.clientWidth <= 10 || container.clientHeight <= 10) {
    container.style.width = `${targetW}px`;
    container.style.height = `${targetH}px`;
  }
}

function onHostResize(widthCssPx?: number, heightCssPx?: number) {
  try {
    const container = document.getElementById('cad-container');
    if (container) {
      ensureNonZeroContainerSize(container, widthCssPx, heightCssPx);
    }
    const view = getActiveView();
    if (view) {
      view.onWindowResize?.();
      view.isDirty = true;
      view.requestRender?.();
    }
  } catch (e) {
    console.warn('[Eurowindow CAD] onHostResize error:', e);
  }
}

function ensureBestSpace(doc: any) {
  try {
    const db = doc?.database;
    const modelSpace = db?.tables?.blockTable?.modelSpace;
    if (!db || !modelSpace) return;
    if (db.currentSpaceId !== modelSpace.objectId) {
      const modelCount = typeof modelSpace.newIterator === 'function' ? modelSpace.newIterator().count : 0;
      const curBtr = db.tables.blockTable.getIdAt(db.currentSpaceId);
      const curCount = curBtr && typeof curBtr.newIterator === 'function' ? curBtr.newIterator().count : 0;
      if (modelCount > 0 && curCount <= 2) {
        console.log(`[Eurowindow CAD] Auto-switching from empty Paper Space (${curCount}) to Model Space (${modelCount} entities)`);
        acdbHostApplicationServices().layoutManager.setCurrentLayoutBtrId(modelSpace.objectId, db);
        const view = getActiveView();
        if (view) {
          view.activeLayoutBtrId = modelSpace.objectId;
        }
      }
    }
  } catch (errLayout) {
    console.warn('[Eurowindow CAD] Layout check:', errLayout);
  }
}

async function initViewer(containerId: string): Promise<boolean> {
  if (isInitialized) {
    onHostResize();
    return true;
  }

  const container = document.getElementById(containerId) as HTMLDivElement;
  if (!container) {
    console.error('Container not found:', containerId);
    return false;
  }

  try {
    ensureNonZeroContainerSize(container);

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

    // 2. Register main-thread DWG converter
    const dwgConverter = new AcDbLibreDwgMainThreadConverter();
    AcDbDatabaseConverterManager.instance.register(AcDbFileType.DWG, dwgConverter);

    // 3. Initialize AcApDocManager
    AcApDocManager.createInstance({
      container,
      autoResize: true,
      baseUrl: localBaseUrl,
      useMainThreadDraw: true,
      disableExport: true,
      builtinOpenFileDialog: false,
      notificationCenter: false,
      openDocumentDefaults: () => ({
        mode: AcEdOpenMode.Read,
        drawNoPlotLayers: false,
        openViewMode: AcApOpenViewMode.Extents
      }),
      webworkerFileUrls: {
        mtextRender: './workers/mtext-renderer-worker.js'
      }
    } as any);

    AcApDocManager.instance.events.documentActivated.addEventListener(args => {
      console.log('[Eurowindow CAD] Document activated:', args.doc.docTitle);
      preferOverviewStrip = false;
      hideLoadingOverlay();
      ensureBestSpace(args.doc);
      sendDocumentLoaded();

      // Progressive framing passes (immediate + after geometry/glyph batches flush)
      setTimeout(() => { zoomToFit(false); }, 100);
      setTimeout(() => { zoomToFit(false); }, 700);
    });

    // Attach ResizeObserver to keep canvas sized to Android WebView container
    if (typeof ResizeObserver !== 'undefined') {
      const ro = new ResizeObserver(() => {
        onHostResize();
      });
      ro.observe(container);
    }
    window.addEventListener('resize', () => {
      onHostResize();
    });

    (window as any).AcApDocManager = AcApDocManager;
    isInitialized = true;
    console.log(`[Eurowindow CAD] Viewer initialized (${container.clientWidth}x${container.clientHeight}).`);

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

  onHostResize();
  showLoadingOverlay(`Đang nạp và giải mã bản vẽ: ${fileName}...`);

  const ext = fileName.toLowerCase().split('.').pop();
  if (ext === 'dwg') {
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
    hideLoadingOverlay();
    await zoomToFit(false);
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

function applyReadableMobileBox(view: any, box: any, useOverview: boolean) {
  if (!box || !box.min || !box.max) return false;
  const w = box.max.x - box.min.x;
  const h = box.max.y - box.min.y;
  if (!Number.isFinite(w) || !Number.isFinite(h) || w <= 0 || h <= 0) return false;

  // When a shop drawing consists of many sheets arranged in a wide horizontal strip (w > h * 2.5),
  // fitting all sheets at once on a narrow portrait phone makes the strip tiny.
  // In default sheet focus mode, frame the leftmost 1-2 sheets so details are immediately large and readable;
  // tapping "Vừa khung" toggles between Sheet Focus and Full Strip Overview.
  if (!useOverview && w > h * 2.5) {
    const focusW = Math.min(w, Math.max(h * 1.75, w / 3.5));
    const focusedBox = typeof box.clone === 'function' ? box.clone() : new box.constructor();
    if (focusedBox && focusedBox.min && focusedBox.max) {
      focusedBox.min.x = box.min.x;
      focusedBox.min.y = box.min.y;
      focusedBox.max.x = box.min.x + focusW;
      focusedBox.max.y = box.max.y;
      view.zoomTo(focusedBox, 1.06);
      view.isDirty = true;
      view.requestRender?.();
      return true;
    }
  }

  view.zoomTo(box, 1.06);
  view.isDirty = true;
  view.requestRender?.();
  return true;
}

async function zoomToFit(toggleMode = true): Promise<void> {
  try {
    const container = document.getElementById('cad-container');
    if (container) {
      ensureNonZeroContainerSize(container);
    }
    const view = getActiveView();
    if (!view) return;

    if (toggleMode) {
      preferOverviewStrip = !preferOverviewStrip;
    }

    try {
      view.onWindowResize?.();
    } catch (_) {}

    // 1. Immediate raw extents framing
    try {
      const rawBox = view.getDrawingExtents?.();
      if (!applyReadableMobileBox(view, rawBox, preferOverviewStrip)) {
        view.zoomToFitDrawing?.(0);
      }
    } catch (_) {}

    // 2. Smart extents framing (waits for entity conversion and filters out (0,0) outliers)
    if (typeof view.zoomToSmartExtents === 'function') {
      try {
        await view.zoomToSmartExtents(0);
        const smartBox = view._scene?.activeLayout?.cachedSmartExtents ?? (await view.resolveSmartFitBox?.());
        if (smartBox) {
          applyReadableMobileBox(view, smartBox, preferOverviewStrip);
        }
      } catch (_) {}
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
      const view = getActiveView();
      if (view) {
        view.isDirty = true;
        view.requestRender?.();
      }
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
  onHostResize,
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
  },
  switchToModelSpace: () => {
    try {
      const doc = AcApDocManager.instance.curDocument;
      const view = getActiveView();
      if (!doc || !doc.database || !view) return;
      const db = doc.database;
      const modelSpace = db.tables.blockTable.modelSpace;
      if (!modelSpace) return;

      if (view.activeLayoutBtrId !== modelSpace.objectId) {
        acdbHostApplicationServices().layoutManager.setCurrentLayoutBtrId(modelSpace.objectId, db);
        view.activeLayoutBtrId = modelSpace.objectId;
      } else {
        const layoutDict = (db.objects as any)?.layout;
        let switched = false;
        if (layoutDict && typeof layoutDict.newIterator === 'function') {
          for (const layout of layoutDict.newIterator()) {
            const btrId = layout.blockTableRecordId;
            if (btrId && btrId !== modelSpace.objectId) {
              const btr = db.tables.blockTable.getIdAt(btrId);
              const count = btr && typeof btr.newIterator === 'function' ? btr.newIterator().count : 0;
              if (count > 1) {
                acdbHostApplicationServices().layoutManager.setCurrentLayoutBtrId(btrId, db);
                view.activeLayoutBtrId = btrId;
                switched = true;
                break;
              }
            }
          }
        }
        if (!switched) {
          view.activeLayoutBtrId = modelSpace.objectId;
        }
      }
      view.isDirty = true;
      setTimeout(() => { zoomToFit(false); }, 150);
    } catch (e) {
      console.warn('[Eurowindow CAD] switchToModelSpace error:', e);
    }
  }
};

// Auto-initialize when DOM is ready
window.addEventListener('DOMContentLoaded', () => {
  initViewer('cad-container');
});
