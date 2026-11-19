// Renders scene.html frame by frame and encodes an MP4 with ffmpeg.
//   node render.js                 -> ../froyer-ad.mp4 (30 fps)
//   node render.js --stills 1,5,10 -> stills/<t>.jpg only
const { chromium } = require('playwright');
const { spawn, execFileSync } = require('child_process');
const http = require('http'), fs = require('fs'), path = require('path');

const root = __dirname, FPS = 30;

// Pull in the current app screens, logo and fonts so the ad always matches the app.
const repo = path.join(root, '..', '..', '..');
fs.mkdirSync(path.join(root, 'assets'), { recursive: true });
for (const f of ['home', 'movies', 'apps', 'settings', 'library', 'mark', 'lockup'])
  fs.copyFileSync(path.join(repo, 'docs/showcase/screens', f + '.png'), path.join(root, 'assets', f + '.png'));
for (const w of ['regular', 'semibold', 'bold'])
  fs.copyFileSync(path.join(repo, 'app/src/main/res/font', `inter_${w}.ttf`), path.join(root, 'assets', `inter_${w}.ttf`));
const types = { '.html': 'text/html', '.js': 'text/javascript', '.png': 'image/png', '.ttf': 'font/ttf' };
const server = http.createServer((req, res) => {
  const f = path.join(root, decodeURIComponent(req.url.split('?')[0]));
  if (!f.startsWith(root) || !fs.existsSync(f)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': types[path.extname(f)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});

(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  page.on('pageerror', e => console.error('page error:', e.message));
  await page.goto(`http://127.0.0.1:${port}/scene.html`);
  await page.waitForFunction('window.ready === true', null, { timeout: 120000 });

  const stillsArg = process.argv.indexOf('--stills');
  if (stillsArg > 0) {
    fs.mkdirSync(path.join(root, 'stills'), { recursive: true });
    for (const t of process.argv[stillsArg + 1].split(',').map(Number)) {
      await page.evaluate(t => window.renderAt(t), t);
      await page.screenshot({ path: path.join(root, 'stills', `${t}.jpg`), type: 'jpeg', quality: 85 });
      console.log('still', t);
    }
  } else {
    const duration = await page.evaluate('window.DURATION');
    const out = path.join(root, '..', 'froyer-ad.mp4');
    const ff = spawn('ffmpeg', ['-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-',
      '-c:v', 'libx264', '-preset', 'slow', '-crf', '17', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out], { stdio: ['pipe', 'ignore', 'inherit'] });
    const frames = Math.round(duration * FPS);
    const start = Date.now();
    for (let f = 0; f < frames; f++) {
      await page.evaluate(t => window.renderAt(t), f / FPS);
      const buf = await page.screenshot({ type: 'jpeg', quality: 95 });
      if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
      if (f % 60 === 0) console.log(`frame ${f}/${frames}  ${((Date.now() - start) / 1000).toFixed(0)}s`);
    }
    ff.stdin.end();
    await new Promise(r => ff.on('close', r));
    console.log('wrote', out);
  }
  await browser.close(); server.close();
})();
