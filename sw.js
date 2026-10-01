const CACHE='remote-shlakbaum-v1';
const APP=['./','./index.html','./manifest.webmanifest'];
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(APP))));
self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));
self.addEventListener('fetch',e=>{if(e.request.method!=='GET')return;e.respondWith(fetch(e.request).then(r=>{const copy=r.clone();caches.open(CACHE).then(c=>c.put(e.request,copy));return r;}).catch(()=>caches.match(e.request).then(r=>r||caches.match('./index.html'))));});
