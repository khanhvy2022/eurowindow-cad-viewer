import http from 'http';
import fs from 'fs';
import path from 'path';
import { spawn } from 'child_process';

const PORT = 9989;
const webDir = 'F:\\EW DWG View\\android\\app\\src\\main\\assets\\web-runtime';

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  const urlPath = req.url.split('?')[0];

  if (urlPath === '/current-drawing-stream') {
    const dwgPath = 'F:\\EW DWG View\\android\\app\\src\\main\\assets\\sample-drawings\\sample-door.dwg';
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
  console.log(`Server listening at http://127.0.0.1:${PORT}`);

  const chromeExe = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  const chrome = spawn(chromeExe, [
    '--headless=new',
    '--remote-debugging-port=9223',
    '--disable-gpu',
    '--no-sandbox',
    '--window-size=412,915',
    `http://127.0.0.1:${PORT}/index.html`
  ]);

  await new Promise(r => setTimeout(r, 2000));

  try {
    const listResp = await fetch('http://127.0.0.1:9223/json');
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

    ws.addEventListener('message', (event) => {
      const data = JSON.parse(event.data);
      if (data.method === 'Runtime.consoleAPICalled') {
        const type = data.params.type;
        const args = data.params.args.map(a => a.value || JSON.stringify(a)).join(' ');
        console.log(`[Chrome Console ${type}]:`, args);
      } else if (data.method === 'Runtime.exceptionThrown') {
        console.error('[Chrome Exception]:', JSON.stringify(data.params.exceptionDetails, null, 2));
      }
    });

    await send('Runtime.enable');
    await send('Log.enable');
    await send('Network.enable');
    await send('Page.enable');

    await send('Emulation.setDeviceMetricsOverride', {
      width: 412,
      height: 915,
      deviceScaleFactor: 2.625,
      mobile: true
    });

    console.log('Waiting for viewer initialization...');
    await new Promise(r => setTimeout(r, 3000));

    console.log('Loading DWG via ArrayBuffer...');
    const loadRes = await send('Runtime.evaluate', {
      expression: `(async () => {
        const resp = await fetch("/current-drawing-stream");
        const buf = await resp.arrayBuffer();
        return await window.EurowindowBridge.loadDrawingFromArrayBuffer("sample-door.dwg", buf);
      })()`,
      awaitPromise: true
    });
    console.log('loadDrawingFromArrayBuffer result:', loadRes);

    await new Promise(r => setTimeout(r, 2000));

    const diag = await send('Runtime.evaluate', {
      expression: `(() => {
        const container = document.getElementById("cad-container");
        const overlay = document.getElementById("loading-overlay");
        const canvas = container ? container.querySelector("canvas") : null;
        const curDoc = window.AcApDocManager ? window.AcApDocManager.instance.curDocument : null;
        const view = curDoc ? curDoc.view : null;
        const modelSpace = curDoc && curDoc.database && curDoc.database.tables.blockTable ? curDoc.database.tables.blockTable.modelSpace : null;
        return {
          containerWidth: container ? container.clientWidth : -1,
          containerHeight: container ? container.clientHeight : -1,
          canvasWidth: canvas ? canvas.width : -1,
          canvasHeight: canvas ? canvas.height : -1,
          overlayDisplay: overlay ? overlay.style.display : "unknown",
          docTitle: curDoc ? curDoc.docTitle : "none",
          hasView: !!view,
          entitiesCount: modelSpace ? modelSpace.entities.length : -1,
          currentSpaceId: curDoc && curDoc.database ? curDoc.database.currentSpaceId : -1,
          modelSpaceId: modelSpace ? modelSpace.objectId : -1
        };
      })()`,
      returnByValue: true
    });
    console.log('Diagnostics:', diag.result.value);

    console.log('Taking screenshot of mobile view...');
    const shot = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync('cad-engine/web-runtime/test-mobile-render.png', Buffer.from(shot.data, 'base64'));
    console.log('Saved mobile render screenshot.');

  } catch (err) {
    console.error('Test error:', err);
  } finally {
    chrome.kill();
    server.close();
    process.exit(0);
  }
});
