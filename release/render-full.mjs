import http from 'http';
import fs from 'fs';
import path from 'path';
import { spawn } from 'child_process';

const runtimeDir = 'F:\\EW DWG View\\android\\app\\src\\main\\assets\\web-runtime';
const dwgPath = 'C:\\Users\\ThangTran\\Desktop\\anh Thuận\\SX\\REV12-25305285-489641 SX_1.dwg';
const PORT = 9995;

const server = http.createServer((req, res) => {
  const urlPath = req.url.split('?')[0];
  if (urlPath === '/current-drawing-stream') {
    const stat = fs.statSync(dwgPath);
    res.writeHead(200, {
      'Content-Type': 'application/octet-stream',
      'Content-Length': stat.size,
      'Access-Control-Allow-Origin': '*'
    });
    fs.createReadStream(dwgPath).pipe(res);
    return;
  }

  let filePath = path.join(runtimeDir, urlPath === '/' ? 'index.html' : urlPath);
  if (!fs.existsSync(filePath)) {
    res.writeHead(404);
    res.end('Not found');
    return;
  }

  const ext = path.extname(filePath);
  const types = {
    '.html': 'text/html',
    '.js': 'application/javascript',
    '.wasm': 'application/wasm',
    '.json': 'application/json',
    '.png': 'image/png'
  };
  res.writeHead(200, {
    'Content-Type': types[ext] || 'application/octet-stream',
    'Access-Control-Allow-Origin': '*'
  });
  fs.createReadStream(filePath).pipe(res);
});

server.listen(PORT, '127.0.0.1', async () => {
  const chromeExe = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  const chrome = spawn(chromeExe, [
    '--headless=new',
    '--remote-debugging-port=9228',
    '--disable-gpu',
    '--no-sandbox',
    '--window-size=2400,1600',
    `http://127.0.0.1:${PORT}/index.html`
  ]);

  await new Promise(r => setTimeout(r, 2000));

  try {
    const listResp = await fetch('http://127.0.0.1:9228/json');
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

    await send('Runtime.enable');
    await send('Page.enable');
    await new Promise(r => setTimeout(r, 2500));

    console.log('Loading drawing...');
    await evalCode(`(async () => {
      const resp = await fetch('/current-drawing-stream');
      const buf = await resp.arrayBuffer();
      await window.EurowindowBridge.loadDrawingFromArrayBuffer("REV12-SX.dwg", buf);
    })()`, true);

    console.log('Full fit...');
    await evalCode(`(() => {
      const dm = window.AcApDocManager.instance;
      const view = dm.curView;
      view.zoomToFitDrawing?.(0);
      view.isDirty = true;
    })()`);
    await new Promise(r => setTimeout(r, 1500));

    let shot = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync('F:\\EW DWG View\\release\\full-fit-2400.png', Buffer.from(shot.data, 'base64'));
    console.log('Saved full-fit-2400.png');

    // Also let's inspect what spatial bounding boxes exist for all 35,356 entities
    const boxes = await evalCode(`(() => {
      const dm = window.AcApDocManager.instance;
      const doc = dm.curDocument;
      const db = doc.database;
      const ms = db.tables?.blockTable?.modelSpace;
      
      const clusters = [];
      // Let's divide the space or find bounding boxes of entities
      let minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
      let count = 0;
      if (ms && typeof ms.newIterator === 'function') {
        for (const ent of ms.newIterator()) {
          count++;
          // check if ent has bounding box
          let b = ent.geometricExtents || ent.bounds || ent._box;
          if (b && b.min && b.max) {
            minX = Math.min(minX, b.min.x);
            minY = Math.min(minY, b.min.y);
            maxX = Math.max(maxX, b.max.x);
            maxY = Math.max(maxY, b.max.y);
          }
        }
      }
      return { count, minX, minY, maxX, maxY };
    })()`);
    console.log('Bounding boxes info:', boxes);

  } catch (err) {
    console.error('Error:', err);
  } finally {
    chrome.kill();
    server.close();
    process.exit(0);
  }
});
