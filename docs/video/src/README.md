# Froyer 3D ad

A 27-second 1080p ad rendered from code: a modeled TV in a studio scene showing the
app's real screens (`docs/showcase/screens`), with the logo intro and end card.

```bash
cd docs/video/src
npm install
node render.js                       # writes ../froyer-ad.mp4 (needs Playwright and ffmpeg)
node render.js --stills 5,11,19.5    # single frames into stills/ for checking framing
```

`scene.html` holds the scene, camera shots and captions; edit the `shots` and `caps`
tables to change timing or framing. The video is silent so music can be added in an editor.
