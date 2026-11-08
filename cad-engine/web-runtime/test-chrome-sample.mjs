import http from 'http';
import fs from 'fs';
import path from 'path';
import { spawn } from 'child_process';

const PORT = 9988;
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

  console.log(`[HTTP Req]: ${req.method} ${req.url}`);
  const relPath = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '');
  const filePath = path.join(webDir, relPath);
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
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
    '--remote-debugging-port=9222',
    '--disable-gpu',
    '--no-sandbox',
    `http://127.0.0.1:${PORT}/index.html`
  ]);

  await new Promise(r => setTimeout(r, 2000));

  try {
    const listResp = await fetch('http://127.0.0.1:9222/json');
    const pages = await listResp.json();
    console.log('Pages:', pages);
    const targetPage = pages.find(p => p.url && p.url.includes('9988')) || pages[0];
    const wsUrl = targetPage.webSocketDebuggerUrl;
    console.log('Connecting to Chrome CDP WebSocket for target page:', targetPage.title, wsUrl);

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
      } else if (data.method === 'Network.responseReceived') {
        // console.log(`[Network ${data.params.response.status}]: ${data.params.response.url}`);
      } else if (data.method === 'Network.loadingFailed') {
        console.error(`[Network FAILED]: ${data.params.errorText} - ${data.params.url}`);
      }
    });

    await send('Runtime.enable');
    await send('Log.enable');
    await send('Network.enable');
    await send('Page.enable');

    console.log('Waiting for viewer initialization...');
    await new Promise(r => setTimeout(r, 4000));

    console.log('Waiting for viewer initialization...');
    await new Promise(r => setTimeout(r, 2000));

    console.log('Triggering loadDrawingFromArrayBuffer("HoSo.dwg")...');
    const evalRes = await send('Runtime.evaluate', {
      expression: `(async () => {
        const res = await fetch('/current-drawing-stream');
        const buf = await res.arrayBuffer();
        return await window.EurowindowBridge.loadDrawingFromArrayBuffer("REV12-25305285-489641.dwg", buf);
      })()`,
      awaitPromise: true
    });
    console.log('Evaluate result:', evalRes);

    await new Promise(r => setTimeout(r, 2000));

    console.log('Evaluating drawing details...');
    const diagRes = await send('Runtime.evaluate', {
      expression: `(() => {
        // Query window
        const doc = (window as any)._doc || (window as any).EurowindowBridge?.getDrawingInfo?.();
        return doc;
      })()`,
      returnByValue: true
    });
    console.log('Drawing info from EurowindowBridge:', diagRes);

    await new Promise(r => setTimeout(r, 2000));

    console.log('Taking screenshot after smart zoom...');
    const shot = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync('F:\\EW DWG View\\cad-engine\\web-runtime\\test-render-smart.png', Buffer.from(shot.data, 'base64'));
    console.log('Screenshot saved to test-render-smart.png');
  } catch (err) {
    console.error('Test error:', err);
  } finally {
    chrome.kill();
    server.close();
    process.exit(0);
  }
});
