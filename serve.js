// serve.js — 极简静态文件服务器，托管 Flutter Web 构建产物。
// 修复（2026-10-08 代码审查 C4）：
//  - 路径包含检查（原实现 `path.join(root, '/../x')` 可逃逸根目录 → 任意文件读取）
//  - decodeURIComponent 异常捕获（原实现一个畸形 URI 即杀死进程 → 远程 DoS）
//  - 仅绑定 127.0.0.1（原实现监听 0.0.0.0，局域网可直达）
const http = require('http');
const fs = require('fs');
const path = require('path');

const root = process.argv[2] || '.';
const port = parseInt(process.argv[3] || '8100', 10);
const rootDir = path.resolve(root);
const mime = {
  '.html': 'text/html', '.js': 'application/javascript',
  '.json': 'application/json', '.css': 'text/css',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml',
  '.wasm': 'application/wasm', '.ttf': 'font/ttf', '.otf': 'font/otf',
  '.ico': 'image/x-icon'
};

http.createServer((req, res) => {
  let p;
  try {
    p = decodeURIComponent(req.url.split('?')[0]);
  } catch (e) {
    res.writeHead(400);
    res.end('Bad request');
    return;
  }
  if (p === '/') p = '/index.html';
  const file = path.resolve(rootDir, '.' + p);
  // 路径必须落在根目录内（拒绝 ../ 逃逸）
  if (file !== rootDir && !file.startsWith(rootDir + path.sep)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); res.end('Not found'); return; }
    res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' });
    res.end(data);
  });
}).listen(port, '127.0.0.1', () => console.log('Serving ' + root + ' on http://localhost:' + port));
