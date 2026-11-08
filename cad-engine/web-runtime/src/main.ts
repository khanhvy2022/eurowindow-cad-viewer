// Strict offline guard: redirect any external cad-data font requests to local origin
if (typeof window !== 'undefined') {
  const originalFetch = window.fetch;
  window.fetch = async function (input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const urlStr = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    if (urlStr.includes('cdn.jsdelivr.net/gh/mlightcad/cad-data')) {
      const localUrl = urlStr.replace(/^https:\/\/cdn\.jsdelivr\.net\/gh\/mlightcad\/cad-data/, window.location.origin + '/cad-data');
      return originalFetch.call(window, localUrl, init);
    }
    return originalFetch.call(window, input, init);
  };

  const originalOpen = XMLHttpRequest.prototype.open;
  (XMLHttpRequest.prototype as any).open = function (method: string, url: string | URL, ...rest: any[]) {
    const urlStr = String(url);
    if (urlStr.includes('cdn.jsdelivr.net/gh/mlightcad/cad-data')) {
      const localUrl = urlStr.replace(/^https:\/\/cdn\.jsdelivr\.net\/gh\/mlightcad\/cad-data/, window.location.origin + '/cad-data');
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
let currentLoadingFileName = '';

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
      const dpr = Math.min(Math.max(window.devicePixelRatio || 2, 2.5), 3);
      view._renderer?.internalRenderer?.setPixelRatio?.(dpr);
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

function attachDatabaseProgress(doc: any) {
  try {
    const db = doc?.database;
    if (!db?.events?.openProgress || (db as any).__ewProgressAttached) return;
    (db as any).__ewProgressAttached = true;
    db.events.openProgress.addEventListener((ev: any) => {
      const rawPct = Math.max(0, Math.min(100, Math.round(ev.percentage ?? 0)));
      // Map database parse/conversion (0..100%) to 0..75% of overall load progress;
      // the remaining 75..100% tracks scene geometry & glyph building.
      const scaledPct = Math.min(75, Math.round(rawPct * 0.75));
      const stageLabel = ev.stage === 'PARSE'
        ? 'Đang giải mã cấu trúc DWG'
        : ev.subStage === 'FONT'
        ? 'Đang nạp phông chữ CAD'
        : ev.subStage === 'ENTITY'
        ? 'Đang đọc thực thể CAD'
        : 'Đang xử lý dữ liệu bản vẽ';
      const msg = `${stageLabel}: ${scaledPct}% (${currentLoadingFileName || 'Bản vẽ'})`;
      updateLoadingText(msg);
      window.AndroidBridge?.onProgress?.(scaledPct, msg);
    });
  } catch (_) {}
}

/**
 * Waits until 100% of entities and text/block geometries in the active layout are converted
 * into the WebGL scene, updating the progress overlay from 75% -> 100%.
 */
async function waitForFullSceneReady(view: any, fileName: string): Promise<void> {
  if (!view) return;
  const initialTotal = Math.max(1, (view._numOfEntitiesToProcess || 0) + (view._pendingGeometryJobs || 0));
  const startTime = performance.now();
  const maxWaitMs = 90000;

  while (performance.now() - startTime < maxWaitMs) {
    const remainingEntities = view._numOfEntitiesToProcess || 0;
    const remainingJobs = view._pendingGeometryJobs || 0;
    const remaining = remainingEntities + remainingJobs;
    if (remaining <= 0 && !view.isProcessingEntities) {
      break;
    }
    const doneRatio = Math.max(0, Math.min(1, 1 - remaining / Math.max(initialTotal, remaining + 1)));
    const pct = Math.min(99, 75 + Math.round(doneRatio * 24));
    const msg = `Đang dựng đầy đủ 100% chi tiết & chữ CAD: ${pct}% (${fileName})`;
    updateLoadingText(msg);
    window.AndroidBridge?.onProgress?.(pct, msg);
    await new Promise(r => setTimeout(r, 40));
  }

  // Flush all instanced text/MTEXT glyphs into the scene immediately so no text is missing
  try {
    view.flushMeshGlyphWorldBatch?.();
  } catch (_) {}
  try {
    view.cancelOpenLineworkFrame?.();
    if (view._progressiveOpenFit) {
      view._progressiveOpenFit.userAdjusted = true;
    }
    if (view._externallyFramedLayouts && view.activeLayoutBtrId != null) {
      view._externallyFramedLayouts.add(view.activeLayoutBtrId);
    }
    view.endProgressiveOpenFit?.();
  } catch (_) {}
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

    // 1. Pre-initialize LibreDwg WebAssembly singleton on the main thread
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

    // 3. Initialize AcApDocManager with 100% layer & entity fidelity (drawNoPlotLayers: true, convertInvisibleLayers: true)
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
        drawNoPlotLayers: true,
        convertInvisibleLayers: true,
        minimumChunkSize: 10000,
        progressiveRendering: false,
        cooperativeYield: true,
        openViewMode: AcApOpenViewMode.Extents
      }),
      webworkerFileUrls: {
        mtextRender: './workers/mtext-renderer-worker.js'
      }
    } as any);

    const dm = AcApDocManager.instance as any;
    if (dm?.curDocument) {
      attachDatabaseProgress(dm.curDocument);
    }
    dm?.events?.documentCreated?.addEventListener?.((args: any) => {
      if (args?.doc) attachDatabaseProgress(args.doc);
    });

    AcApDocManager.instance.events.documentActivated.addEventListener(args => {
      console.log('[Eurowindow CAD] Document activated:', args.doc.docTitle);
      preferOverviewStrip = false;
      ensureBestSpace(args.doc);
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

function updateLoadingText(msg: string) {
  const text = document.getElementById('status-text');
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

  currentLoadingFileName = fileName;
  onHostResize();
  showLoadingOverlay(`Đang giải mã bản vẽ: ${fileName}...`);

  const dm = AcApDocManager.instance as any;
  if (dm?.curDocument) {
    attachDatabaseProgress(dm.curDocument);
  }

  const options: any = {
    mode: AcEdOpenMode.Read,
    drawNoPlotLayers: true,
    convertInvisibleLayers: true,
    minimumChunkSize: 10000,
    progressiveRendering: false,
    cooperativeYield: true,
    openViewMode: AcApOpenViewMode.Extents
  };

  try {
    const success = await AcApDocManager.instance.openDocument(fileName, buffer, options);
    if (!success) {
      throw new Error(`Không thể giải mã dữ liệu bản vẽ: ${fileName}`);
    }

    const view = getActiveView();
    if (view) {
      await waitForFullSceneReady(view, fileName);
      if (view._scene?.activeLayout) {
        view._scene.activeLayout.cachedSmartExtents = undefined;
      }
    }

    hideLoadingOverlay();
    await zoomToFit(false);
    sendDocumentLoaded();
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

/**
 * Fast synchronous (< 5ms) outlier-filtered bounding box calculation using the top-level
 * spatial index items of the active layout after all 100% of entities are loaded.
 */
function computeFastSmartExtents(view: any): any {
  try {
    try {
      view?.endProgressiveOpenFit?.();
    } catch (_) {}
    const activeLayout = view?._scene?.activeLayout;
    if (activeLayout?.cachedSmartExtents) {
      return activeLayout.cachedSmartExtents;
    }
    const items = activeLayout?._spatialIndex?.rootIndex?.all?.();
    if (Array.isArray(items) && items.length >= 4) {
      let boxes: Array<{ minX: number; minY: number; maxX: number; maxY: number }> = [];
      for (let i = 0; i < items.length; i++) {
        const it = items[i];
        if (!it || !Number.isFinite(it.minX) || !Number.isFinite(it.minY) || !Number.isFinite(it.maxX) || !Number.isFinite(it.maxY)) continue;
        boxes.push(it);
      }

      const pruneGap = (arr: typeof boxes, axis: 'x' | 'y') => {
        if (arr.length < 8) return arr;
        const pts = arr.map(p => ({
          val: axis === 'x' ? 0.5 * (p.minX + p.maxX) : 0.5 * (p.minY + p.maxY),
          box: p
        })).sort((a, b) => a.val - b.val);
        const span = pts[pts.length - 1].val - pts[0].val;
        if (!(span > 0)) return arr;

        let maxGap = 0, splitIdx = 0;
        for (let i = 1; i < pts.length; i++) {
          const g = pts[i].val - pts[i - 1].val;
          if (g > maxGap) {
            maxGap = g;
            splitIdx = i;
          }
        }
        const left = pts.slice(0, splitIdx);
        const right = pts.slice(splitIdx);
        const major = left.length >= right.length ? left : right;
        const minor = left.length < right.length ? left : right;
        const minSpanRatio = minor.length <= Math.max(10, arr.length * 0.01) ? 0.02 : 0.2;
        if (maxGap < span * minSpanRatio) return arr;

        const sizes = arr.map(p => Math.max(p.maxX - p.minX, p.maxY - p.minY, 1e-9)).sort((a, b) => a - b);
        const medSize = sizes[Math.floor(sizes.length / 2)] || 1;
        const gaps: number[] = [];
        for (let i = 1; i < major.length; i++) gaps.push(major[i].val - major[i - 1].val);
        gaps.sort((a, b) => a - b);
        const medGap = (gaps.length > 0 && gaps[Math.floor(gaps.length / 2)]) || medSize;
        const thresh = Math.max(medGap * 10, medSize * 20);
        return maxGap < thresh ? arr : major.map(p => p.box);
      };

      let prevLen = -1;
      let iter = 0;
      while (boxes.length !== prevLen && iter++ < 6) {
        prevLen = boxes.length;
        boxes = pruneGap(boxes, 'x');
        boxes = pruneGap(boxes, 'y');
      }

      if (boxes.length > 0) {
        let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        for (let i = 0; i < boxes.length; i++) {
          const b = boxes[i];
          if (b.minX < minX) minX = b.minX;
          if (b.minY < minY) minY = b.minY;
          if (b.maxX > maxX) maxX = b.maxX;
          if (b.maxY > maxY) maxY = b.maxY;
        }
        if (Number.isFinite(minX) && maxX > minX && maxY > minY) {
          const rawBox = view.getDrawingExtents?.();
          const box = rawBox && typeof rawBox.clone === 'function' ? rawBox.clone() : { min: { x: minX, y: minY }, max: { x: maxX, y: maxY } };
          box.min.x = minX;
          box.min.y = minY;
          box.max.x = maxX;
          box.max.y = maxY;
          if (activeLayout) {
            activeLayout.cachedSmartExtents = box;
          }
          return box;
        }
      }
    }
  } catch (_) {}
  return view?.getDrawingExtents?.() ?? null;
}

function applyReadableMobileBox(view: any, box: any, useOverview: boolean) {
  if (!box || !box.min || !box.max) return false;
  const w = box.max.x - box.min.x;
  const h = box.max.y - box.min.y;
  if (!Number.isFinite(w) || !Number.isFinite(h) || w <= 0 || h <= 0) return false;

  if (!useOverview && w > h * 2.5) {
    const focusW = Math.min(w, h * 2.0);
    const focusedBox = typeof box.clone === 'function' ? box.clone() : { min: { x: 0, y: 0 }, max: { x: 0, y: 0 } };
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

    try {
      view.cancelOpenLineworkFrame?.();
      if (view._progressiveOpenFit) {
        view._progressiveOpenFit.userAdjusted = true;
      }
      if (view._externallyFramedLayouts && view.activeLayoutBtrId != null) {
        view._externallyFramedLayouts.add(view.activeLayoutBtrId);
      }
      view.endProgressiveOpenFit?.();
    } catch (_) {}

    if (toggleMode) {
      preferOverviewStrip = !preferOverviewStrip;
    }

    try {
      const dpr = Math.min(Math.max(window.devicePixelRatio || 2, 2.5), 3);
      view._renderer?.internalRenderer?.setPixelRatio?.(dpr);
      view.onWindowResize?.();
    } catch (_) {}

    const smartBox = computeFastSmartExtents(view);
    if (!applyReadableMobileBox(view, smartBox, preferOverviewStrip)) {
      view.zoomToFitDrawing?.(0);
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
    currentLoadingFileName = fileName;
    showLoadingOverlay(`Đang đọc tệp bản vẽ: ${fileName}...`);
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
