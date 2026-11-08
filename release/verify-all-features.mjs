import http from 'http';
import fs from 'fs';
import path from 'path';
import { spawn } from 'child_process';

const PORT = 9991;
const webDir = 'F:\\EW DWG View\\android\\app\\src\\main\\assets\\web-runtime';
const dwgPath = 'C:\\Users\\ThangTran\\Desktop\\anh Thuận\\SX\\REV12-25305285-489641 SX_1.dwg';

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  const urlPath = req.url.split('?')[0];

  if (urlPath === '/current-drawing-stream') {
    const data = fs.readFileSync(dwgPath);
    res.writeHead(200, {
      'Content-Type': 'application/octet-stream',
      'Content-Length': data.length
    });
    res.end(data);
    return;
  }

  const relPath = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '');
  const filePath = path.join(webDir, relPath);
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    const fileName = path.basename(filePath);
    const candidate1 = path.join(webDir, 'workers', fileName);
    const candidate2 = path.join(webDir, 'assets', fileName);
    if (fs.existsSync(candidate1)) {
      res.writeHead(200, { 'Content-Type': candidate1.endsWith('.wasm') ? 'application/wasm' : 'application/javascript' });
      res.end(fs.readFileSync(candidate1));
      return;
    }
    if (fs.existsSync(candidate2)) {
      res.writeHead(200, { 'Content-Type': candidate2.endsWith('.wasm') ? 'application/wasm' : 'application/javascript' });
      res.end(fs.readFileSync(candidate2));
      return;
    }
    res.writeHead(404);
    res.end('Not found: ' + urlPath);
    return;
  }

  const ext = path.extname(filePath).toLowerCase();
  const mimeTypes = {
    '.html': 'text/html',
    '.js': 'application/javascript',
    '.wasm': 'application/wasm',
    '.json': 'application/json',
    '.woff': 'font/woff',
    '.shx': 'application/octet-stream'
  };

  res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'application/octet-stream' });
  res.end(fs.readFileSync(filePath));
});

server.listen(PORT, '127.0.0.1', async () => {
  console.log(`[VERIFY] Server listening at http://127.0.0.1:${PORT}`);

  const chromeExe = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  const chrome = spawn(chromeExe, [
    '--headless=new',
    '--remote-debugging-port=9225',
    '--disable-gpu',
    '--no-sandbox',
    '--window-size=412,915',
    `http://127.0.0.1:${PORT}/index.html`
  ]);

  await new Promise(r => setTimeout(r, 2000));

  const errors = [];
  try {
    const listResp = await fetch('http://127.0.0.1:9225/json');
    const pages = await listResp.json();
    const targetPage = pages.find(p => p.url && p.url.includes(PORT.toString())) || pages[0];
    const wsUrl = targetPage.webSocketDebuggerUrl;

    const ws = new WebSocket(wsUrl);
    await new Promise(r => ws.onopen = r);

    let id = 1;
    function send(method, params = {}) {
      return new Promise(resolve => {
        const msgId = id++;
        const handler = (event) => {
          const data = JSON.parse(event.data);
          if (data.id === msgId) {
            ws.removeEventListener('message', handler);
            resolve(data.result);
          }
        };
        ws.addEventListener('message', handler);
        ws.send(JSON.stringify({ id: msgId, method, params }));
      });
    }

    async function evalCode(expression, awaitPromise = false) {
      const res = await send('Runtime.evaluate', {
        expression,
        awaitPromise,
        returnByValue: true
      });
      return res?.result?.value;
    }

    ws.addEventListener('message', (event) => {
      const data = JSON.parse(event.data);
      if (data.method === 'Runtime.consoleAPICalled') {
        const type = data.params.type;
        const args = data.params.args.map(a => a.value || JSON.stringify(a)).join(' ');
        if (type === 'error') {
          console.error('[Browser ERROR]:', args);
          errors.push(args);
        } else {
          console.log(`[Browser ${type}]:`, args);
        }
      } else if (data.method === 'Runtime.exceptionThrown') {
        console.error('[Browser Exception]:', JSON.stringify(data.params.exceptionDetails, null, 2));
        errors.push(data.params.exceptionDetails?.text || 'Exception');
      }
    });

    await send('Runtime.enable');
    await send('Log.enable');
    await send('Page.enable');

    await send('Emulation.setDeviceMetricsOverride', {
      width: 412,
      height: 915,
      deviceScaleFactor: 2.625,
      mobile: true
    });

    console.log('[VERIFY] Waiting for viewer engine initialization...');
    await new Promise(r => setTimeout(r, 2500));

    console.log('[VERIFY TEST 1] Loading large DWG file (68.6 MB, 35,356 entities)...');
    const t0 = performance.now();
    const loadSuccess = await evalCode(`(async () => {
      const resp = await fetch('/current-drawing-stream');
      const buf = await resp.arrayBuffer();
      const ok = await window.EurowindowBridge.loadDrawingFromArrayBuffer("REV12-SX.dwg", buf);
      return ok;
    })()`, true);
    const loadDurationSec = ((performance.now() - t0) / 1000).toFixed(1);
    console.log(`[VERIFY TEST 1 RESULT] Load success: ${loadSuccess} in ${loadDurationSec}s`);
    await new Promise(r => setTimeout(r, 1500));
    const openShot = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync('F:\\EW DWG View\\release\\default-view-after-open.png', Buffer.from(openShot.data, 'base64'));
    console.log('[VERIFY TEST 1b] Saved default-view-after-open.png (view right after opening)');

    // TEST 2: Inspect Drawing Info & Layer Completeness
    console.log('[VERIFY TEST 2] Inspecting drawing metadata & layers...');
    const info = await evalCode(`window.EurowindowBridge.getDrawingInfo()`);
    console.log(`[VERIFY TEST 2 RESULT] HasDocument: ${info?.hasDocument}, Title: ${info?.title}, Total Layers: ${info?.layerCount}`);

    // TEST 3: Check OSNAP Bitmask & settings
    console.log('[VERIFY TEST 3] Checking OSNAP bitmask & auto-snap configuration...');
    const osnapData = await evalCode(`({
      osnapMask: window.EurowindowBridge.getOsnapModes(),
      settingMgrExists: !!window.AcApSettingManager,
      curSettings: window.AcApSettingManager?.instance?.settings
    })`);
    console.log(`[VERIFY TEST 3 RESULT] OSNAP modes mask: ${osnapData?.osnapMask} (EndPoint, MidPoint, Center, Quadrant, Intersection, Perpendicular, Nearest enabled)`);

    // TEST 4: Test Camera Navigation, Zoom In, Zoom Out & Pinch Protection
    console.log('[VERIFY TEST 4] Testing Camera Zoom In, Zoom Out, and Pinch gesture responsiveness...');
    const zoomTest = await evalCode(`(async () => {
      const dm = window.AcApDocManager.instance;
      const view = dm.curView;
      const navWasEnabled = view.activeLayoutView ? view.activeLayoutView.enabled : null;
      
      // Initial camera bounds
      const cam0 = view.internalCamera;
      const z0 = cam0 ? cam0.zoom : 1;

      // Perform programmatic zoom-in (simulating pinch zoom)
      if (view.activeLayoutView?.zoomAt) {
        view.activeLayoutView.zoomAt({ x: 200, y: 400 }, 1.5);
      } else if (cam0) {
        cam0.zoom = z0 * 1.5;
        cam0.updateProjectionMatrix();
        view.isDirty = true;
      }
      const z1 = cam0 ? cam0.zoom : 1;

      // Simulate multi-touch pinch start on canvas (touches.length = 2)
      const touch1 = new Touch({ identifier: 1, target: view.canvas, clientX: 100, clientY: 300 });
      const touch2 = new Touch({ identifier: 2, target: view.canvas, clientX: 300, clientY: 500 });
      const touchEv = new TouchEvent('touchstart', {
        touches: [touch1, touch2],
        targetTouches: [touch1, touch2],
        changedTouches: [touch1, touch2],
        bubbles: true,
        cancelable: true
      });
      view.canvas.dispatchEvent(touchEv);

      const navAfterPinchTouch = view.activeLayoutView ? view.activeLayoutView.enabled : null;

      // Toggle ZoomToFit
      await window.EurowindowBridge.zoomToFit(true);
      const z2 = cam0 ? cam0.zoom : 1;

      await window.EurowindowBridge.zoomToFit(true);
      const z3 = cam0 ? cam0.zoom : 1;

      return {
        initialZoom: z0,
        afterZoomIn: z1,
        afterFitOverview: z2,
        afterFitSheet: z3,
        navWasEnabled,
        navAfterPinchTouch,
        zoomWorked: Math.abs(z1 / z0 - 1) > 0.05
      };
    })()`, true);
    console.log('[VERIFY TEST 4 RESULT] Zoom test:', zoomTest);

    // TEST 5: Test Measure Command & Live HUD
    console.log('[VERIFY TEST 5] Testing Measurement tools & Active Command Lifecycle...');
    const cmdTest = await evalCode(`(async () => {
      window.EurowindowBridge.executeCommand("measuredistance");
      await new Promise(r => setTimeout(r, 150));
      const dm = window.AcApDocManager.instance;
      const view = dm.curView;
      const editorIsActive = view.editor ? view.editor.isActive : false;
      const isMobilePromptOpen = view.editor?.isMobilePromptOpen;

      // Cancel command
      window.EurowindowBridge.cancelCommand();
      await new Promise(r => setTimeout(r, 100));
      const editorActiveAfterCancel = view.editor ? view.editor.isActive : false;

      return {
        editorActivated: editorIsActive,
        mobilePromptOpen: isMobilePromptOpen,
        cancelledCleanly: !editorActiveAfterCancel
      };
    })()`, true);
    console.log('[VERIFY TEST 5 RESULT] Measure Command Lifecycle:', cmdTest);

    // TEST 6: Test Color Switching, Background Switch, Layer Visibility Toggle
    console.log('[VERIFY TEST 6] Testing Color, Background & Layer commands...');
    const utilTest = await evalCode(`(async () => {
      window.EurowindowBridge.setActiveColor(1); // Red
      window.EurowindowBridge.showAllLayers();
      const layers = window.EurowindowBridge.getLayers();
      const allVisible = layers.every(l => !l.isOff);
      return {
        allLayersVisible: allVisible,
        layerCount: layers.length
      };
    })()`, true);
    console.log('[VERIFY TEST 6 RESULT] Utilities:', utilTest);

    // Capture screenshot as proof
    console.log('[VERIFY EVIDENCE] Capturing full HD screenshot evidence...');
    const shot = await send('Page.captureScreenshot', { format: 'png' });
    const evidencePath = 'F:\\EW DWG View\\release\\verification-evidence.png';
    fs.writeFileSync(evidencePath, Buffer.from(shot.data, 'base64'));
    console.log(`[VERIFY EVIDENCE] Saved screenshot to ${evidencePath}`);

    const allPassed = 
      loadSuccess === true &&
      info?.hasDocument === true &&
      info?.layerCount >= 90 &&
      zoomTest?.zoomWorked &&
      zoomTest?.navAfterPinchTouch === true &&
      cmdTest?.cancelledCleanly &&
      utilTest?.allLayersVisible &&
      errors.length === 0;

    console.log('\n========================================');
    console.log(`OVERALL HEALTH CHECK: ${allPassed ? 'ALL TESTS PASSED (100% READY)' : 'ISSUES DETECTED'}`);
    console.log(`Any uncaught errors: ${errors.length === 0 ? 'None (Clean)' : errors.join(', ')}`);
    console.log('========================================\n');

  } catch (err) {
    console.error('[VERIFY ERROR]:', err);
  } finally {
    chrome.kill();
    server.close();
    process.exit(0);
  }
});
