import http from 'http';
import fs from 'fs';
import path from 'path';
import { spawn } from 'child_process';

const runtimeDir = 'F:\\EW DWG View\\android\\app\\src\\main\\assets\\web-runtime';
const dwgPath = 'C:\\Users\\ThangTran\\Desktop\\anh Thuận\\SX\\REV12-25305285-489641 SX_1.dwg';

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

server.listen(9992, '127.0.0.1', async () => {
  console.log('[DIAGNOSTIC] Server running at http://127.0.0.1:9992');

  const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  const chrome = spawn(chromePath, [
    '--headless=new',
    '--remote-debugging-port=9223',
    '--disable-gpu',
    '--no-sandbox',
    'http://127.0.0.1:9992'
  ]);

  try {
    await new Promise(r => setTimeout(r, 2000));
    const versionRes = await fetch('http://127.0.0.1:9223/json');
    const pages = await versionRes.json();
    const targetPage = pages.find(p => p.url && p.url.includes('9992')) || pages[0];
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
      if (res?.exceptionDetails) {
        console.error('[EVAL EXCEPTION]:', res.exceptionDetails);
      }
      return res?.result?.value;
    }

    ws.addEventListener('message', (event) => {
      const data = JSON.parse(event.data);
      if (data.method === 'Runtime.consoleAPICalled') {
        const type = data.params.type;
        const args = data.params.args.map(a => a.value || JSON.stringify(a)).join(' ');
        console.log(`[Browser ${type}]:`, args);
      } else if (data.method === 'Runtime.exceptionThrown') {
        console.error('[Browser Exception]:', JSON.stringify(data.params.exceptionDetails, null, 2));
      }
    });

    await send('Runtime.enable');
    await send('Page.enable');

    console.log('[DIAGNOSTIC] Waiting for viewer engine initialization...');
    await new Promise(r => setTimeout(r, 2500));

    console.log('[DIAGNOSTIC] Loading drawing...');
    await evalCode(`(async () => {
      const resp = await fetch('/current-drawing-stream');
      const buf = await resp.arrayBuffer();
      await window.EurowindowBridge.loadDrawingFromArrayBuffer("REV12-SX.dwg", buf);
    })()`, true);

    console.log('[DIAGNOSTIC] Inspecting document structure...');
    const diag = await evalCode(`(() => {
      const dm = window.AcApDocManager.instance;
      const doc = dm.curDocument;
      const db = doc.database;
      
      // Check block table records (Model Space, Paper Space, and other blocks)
      const btrList = [];
      if (db.tables?.blockTable && typeof db.tables.blockTable.newIterator === 'function') {
        for (const btr of db.tables.blockTable.newIterator()) {
          const count = typeof btr.newIterator === 'function' ? btr.newIterator().count : 0;
          btrList.push({
            name: btr.name,
            id: btr.objectId,
            count: count,
            hasLayout: !!btr.layoutId
          });
        }
      }

      // Check layouts dictionary
      const layouts = [];
      const layoutDict = db.objects?.layout;
      if (layoutDict && typeof layoutDict.newIterator === 'function') {
        for (const l of layoutDict.newIterator()) {
          layouts.push({
            name: l.layoutName,
            tabOrder: l.tabOrder,
            btrId: l.blockTableRecordId
          });
        }
      }

      // Check current view active layout
      const view = dm.curView;
      const activeLayoutBtrId = view?.activeLayoutBtrId;

      // Entity types distribution in current active space
      const entityCounts = {};
      const activeBtr = db.tables?.blockTable?.getIdAt(activeLayoutBtrId);
      if (activeBtr && typeof activeBtr.newIterator === 'function') {
        for (const ent of activeBtr.newIterator()) {
          const typeName = ent.constructor?.name || ent.dxfName || typeof ent;
          entityCounts[typeName] = (entityCounts[typeName] || 0) + 1;
        }
      }

      return {
        currentSpaceId: db.currentSpaceId,
        activeLayoutBtrId,
        modelSpaceId: db.tables?.blockTable?.modelSpace?.objectId,
        btrList: btrList.filter(b => b.count > 0 || b.name.startsWith('*')),
        layouts,
        entityCounts
      };
    })()`);

    console.log('[DIAGNOSTIC RESULT]:', JSON.stringify(diag, null, 2));

  } catch (err) {
    console.error('[DIAGNOSTIC ERROR]:', err);
  } finally {
    chrome.kill();
    server.close();
    process.exit(0);
  }
});
