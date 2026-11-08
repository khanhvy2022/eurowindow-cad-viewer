import http from 'http';
import fs from 'fs';
import path from 'path';
import { spawn } from 'child_process';

const runtimeDir = 'F:\\EW DWG View\\android\\app\\src\\main\\assets\\web-runtime';
const dwgPath = 'C:\\Users\\ThangTran\\Desktop\\anh Thuận\\SX\\REV12-25305285-489641 SX_1.dwg';
const PORT = 9993;

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
    '--remote-debugging-port=9226',
    '--disable-gpu',
    '--no-sandbox',
    '--window-size=1200,800',
    `http://127.0.0.1:${PORT}/index.html`
  ]);

  await new Promise(r => setTimeout(r, 2000));

  try {
    const listResp = await fetch('http://127.0.0.1:9226/json');
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

    console.log('Drawing loaded. Inspecting entities and positions...');
    const details = await evalCode(`(() => {
      const dm = window.AcApDocManager.instance;
      const doc = dm.curDocument;
      const db = doc.database;
      const view = dm.curView;

      // Check all layouts in the database
      const layouts = [];
      if (db.objects?.layout && typeof db.objects.layout.newIterator === 'function') {
        for (const l of db.objects.layout.newIterator()) {
          const btr = db.tables.blockTable.getIdAt(l.blockTableRecordId);
          const count = btr && typeof btr.newIterator === 'function' ? btr.newIterator().count : 0;
          layouts.push({
            name: l.layoutName,
            tabOrder: l.tabOrder,
            btrId: l.blockTableRecordId,
            entityCount: count
          });
        }
      }

      // Check extents of model space
      const ms = db.tables?.blockTable?.modelSpace;
      const sampleEntities = [];
      let i = 0;
      if (ms && typeof ms.newIterator === 'function') {
        for (const ent of ms.newIterator()) {
          if (i++ < 30) {
            sampleEntities.push({
              type: ent.dxfName || ent.constructor?.name,
              layer: ent.layer,
              text: ent.textString || ent.contents || (ent.attributeStrings ? Array.from(ent.attributeStrings.values()) : null)
            });
          }
        }
      }

      // Check if there are multiple layouts or what activeLayout is
      return {
        layouts,
        activeLayoutBtrId: view?.activeLayoutBtrId,
        currentSpaceId: db.currentSpaceId,
        totalEntitiesInModelSpace: ms ? ms.newIterator().count : 0,
        sampleEntities
      };
    })()`);

    console.log('DETAILS:', JSON.stringify(details, null, 2));

    // Now zoom to the main plan area and take a detailed screenshot
    console.log('Taking zoomed screenshot of the first plan...');
    await evalCode(`(() => {
      const dm = window.AcApDocManager.instance;
      const view = dm.curView;
      // Let's find extents of entities on layer or main drawing
      const ext = view.getDrawingExtents();
      console.log('Drawing extents:', JSON.stringify(ext));
    })()`);

    // Let's zoom into the central content
    await evalCode(`(() => {
      const dm = window.AcApDocManager.instance;
      const view = dm.curView;
      const ext = view.getDrawingExtents();
      if (ext && ext.min && ext.max) {
        // Zoom into center half
        const midX = (ext.min.x + ext.max.x) / 2;
        const midY = (ext.min.y + ext.max.y) / 2;
        const box = {
          min: { x: ext.min.x + (ext.max.x - ext.min.x) * 0.2, y: ext.min.y + (ext.max.y - ext.min.y) * 0.2 },
          max: { x: ext.min.x + (ext.max.x - ext.min.x) * 0.6, y: ext.min.y + (ext.max.y - ext.min.y) * 0.6 }
        };
        view.zoomTo(box, 1.0);
        view.isDirty = true;
      }
    })()`);

    await new Promise(r => setTimeout(r, 1000));

    const shot = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync('F:\\EW DWG View\\release\\zoom-detail.png', Buffer.from(shot.data, 'base64'));
    console.log('Saved F:\\EW DWG View\\release\\zoom-detail.png');

  } catch (err) {
    console.error('Error:', err);
  } finally {
    chrome.kill();
    server.close();
    process.exit(0);
  }
});
