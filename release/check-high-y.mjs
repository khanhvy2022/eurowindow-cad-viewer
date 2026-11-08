import http from 'http';
import fs from 'fs';
import path from 'path';
import { spawn } from 'child_process';

const runtimeDir = 'F:\\EW DWG View\\android\\app\\src\\main\\assets\\web-runtime';
const dwgPath = 'C:\\Users\\ThangTran\\Desktop\\anh Thuận\\SX\\REV12-25305285-489641 SX_1.dwg';
const PORT = 10005;

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
    '--remote-debugging-port=9237',
    '--disable-gpu',
    '--no-sandbox',
    '--window-size=1200,800',
    `http://127.0.0.1:${PORT}/index.html`
  ]);

  await new Promise(r => setTimeout(r, 2000));

  try {
    const listResp = await fetch('http://127.0.0.1:9237/json');
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

    const highYEntities = await evalCode(`(() => {
      const dm = window.AcApDocManager.instance;
      const doc = dm.curDocument;
      const db = doc.database;
      const ms = db.tables?.blockTable?.modelSpace;

      const items = [];
      if (ms && typeof ms.newIterator === 'function') {
        for (const ent of ms.newIterator()) {
          const b = ent.geometricExtents || ent.bounds;
          if (b && b.max && b.max.y > 35000) {
            items.push({
              layer: ent.layer,
              type: ent.dxfName || ent.constructor?.name,
              minY: Math.round(b.min.y),
              maxY: Math.round(b.max.y),
              minX: Math.round(b.min.x),
              maxX: Math.round(b.max.x),
              text: ent.textString || ent.contents || null
            });
            if (items.length > 50) break;
          }
        }
      }
      return { count: items.length, sample: items.slice(0, 20) };
    })()`);

    console.log('HIGH Y ENTITIES:', JSON.stringify(highYEntities, null, 2));

  } catch (err) {
    console.error('Error:', err);
  } finally {
    chrome.kill();
    server.close();
    process.exit(0);
  }
});
