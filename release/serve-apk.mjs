import http from 'http';
import fs from 'fs';
import path from 'path';

const PORT = 8888;
const apkPath = 'F:\\EW DWG View\\release\\com.eurowindow.cadviewer-v1.0.14-release.apk';

const server = http.createServer((req, res) => {
  if (req.url === '/' || req.url.startsWith('/download') || req.url.endsWith('.apk')) {
    if (!fs.existsSync(apkPath)) {
      res.writeHead(404);
      res.end('File not found');
      return;
    }
    const stat = fs.statSync(apkPath);
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': stat.size,
      'Content-Disposition': 'attachment; filename="com.eurowindow.cadviewer-v1.0.14-release.apk"',
      'Access-Control-Allow-Origin': '*'
    });
    fs.createReadStream(apkPath).pipe(res);
    return;
  }
  res.writeHead(404);
  res.end('Not found');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Local APK server running on http://192.168.1.10:${PORT}`);
});
