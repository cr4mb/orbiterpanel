/* Orbiter Panel — event animations.
   Every event theme is a small canvas scene: an intro burst for the first INTRO seconds after a tap,
   then a calmer ambient loop. Themes are matched to live events by tag or name, so recurring events
   pick up their animation automatically when they come back. */
(() => {
"use strict";
const TAU = Math.PI * 2;
const INTRO = 2.6;

/* ---------- helpers ---------- */
function seeded(seed){ let s = seed >>> 0 || 1; return () => (s = (s * 1664525 + 1013904223) >>> 0) / 4294967296; }
const lerp = (a, b, t) => a + (b - a) * t;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const ease = t => 1 - Math.pow(1 - clamp(t, 0, 1), 3);
function bg(g, w, h, top, bottom, glow){
  const lg = g.createLinearGradient(0, 0, 0, h); lg.addColorStop(0, top); lg.addColorStop(1, bottom);
  g.fillStyle = lg; g.fillRect(0, 0, w, h);
  if (glow) { const r = g.createRadialGradient(glow[0] * w, glow[1] * h, 0, glow[0] * w, glow[1] * h, Math.max(w, h) * glow[2]);
    r.addColorStop(0, glow[3]); r.addColorStop(1, "rgba(0,0,0,0)"); g.fillStyle = r; g.fillRect(0, 0, w, h); }
}
function glowDot(g, x, y, r, color, alpha){
  if (r <= 0 || alpha <= 0) return;
  const gr = g.createRadialGradient(x, y, 0, x, y, r);
  gr.addColorStop(0, color); gr.addColorStop(1, "rgba(0,0,0,0)");
  g.globalAlpha = alpha; g.fillStyle = gr; g.beginPath(); g.arc(x, y, r, 0, TAU); g.fill(); g.globalAlpha = 1;
}
function diamond(g, x, y, r, color, alpha, rot){
  g.save(); g.translate(x, y); g.rotate(rot || 0); g.globalAlpha = alpha; g.fillStyle = color;
  g.beginPath(); g.moveTo(0, -r); g.lineTo(r * .7, 0); g.lineTo(0, r); g.lineTo(-r * .7, 0); g.closePath(); g.fill(); g.restore(); g.globalAlpha = 1;
}
function heart(g, x, y, s, color, alpha, rot){
  g.save(); g.translate(x, y); g.rotate(rot || 0); g.scale(s / 16, s / 16); g.globalAlpha = alpha; g.fillStyle = color;
  g.beginPath(); g.moveTo(0, 6); g.bezierCurveTo(-14, -4, -6, -14, 0, -6); g.bezierCurveTo(6, -14, 14, -4, 0, 6); g.fill(); g.restore(); g.globalAlpha = 1;
}
function star(g, x, y, r, color, alpha, rot){
  g.save(); g.translate(x, y); g.rotate(rot || 0); g.globalAlpha = alpha; g.fillStyle = color; g.beginPath();
  for (let i = 0; i < 10; i++) { const a = i / 10 * TAU - Math.PI / 2, rr = i % 2 ? r * .45 : r; g.lineTo(Math.cos(a) * rr, Math.sin(a) * rr); }
  g.closePath(); g.fill(); g.restore(); g.globalAlpha = 1;
}
/* Particles that loop on their own clock: each has a phase so the scene never "runs out". */
function field(n, seed, make){ const r = seeded(seed), out = []; for (let i = 0; i < n; i++) out.push(make(r, i)); return out; }
const loop = (t, speed, phase) => ((t * speed + phase) % 1 + 1) % 1;

/* ---------- themes ---------- */
// colors: card gradient [top, bottom, accent]. draw(g, w, h, t, k, s): t = seconds since tap, k = intensity 0..1, s = per-scene state.
const THEMES = {
  thermia: {
    name:"Thermia Fractures", where:"Orb Vallis", blurb:"Seal heat fractures across the Orb Vallis with Diluted Thermia.",
    match:/thermia|heatfissure/i, colors:["#3b1206", "#120503", "#ff8a3a"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#2a0b05", "#080302", [.5, .65, .7, "rgba(255,110,40,.35)"]);
      if (!s.cracks) {
        const r = seeded(11); s.cracks = [];
        for (let c = 0; c < 7; c++) { let x = r() * w, y = h * (.35 + r() * .6), pts = [[x, y]], a = r() * TAU;
          for (let i = 0; i < 9; i++) { a += (r() - .5) * 1.4; x += Math.cos(a) * w * .06; y += Math.sin(a) * h * .06; pts.push([x, y]); }
          s.cracks.push({ pts, delay:r() * .9 }); }
        s.embers = field(70, 12, r => ({ x:r(), ph:r(), sp:.12 + r() * .2, sz:1 + r() * 2.4, dr:(r() - .5) * .06 }));
      }
      const seal = clamp((t - 1.7) / .8, 0, 1); // the fractures cool to cyan as they "seal"
      for (const c of s.cracks) {
        const grow = ease((t - c.delay) / 1.1), n = Math.max(1, Math.floor(c.pts.length * grow));
        const hot = `rgba(${Math.round(lerp(255, 120, seal))},${Math.round(lerp(140, 230, seal))},${Math.round(lerp(50, 255, seal))},`;
        g.lineCap = "round"; g.lineJoin = "round";
        for (const [lw, a] of [[9, .18], [4, .45], [1.6, 1]]) {
          g.strokeStyle = hot + (a * (.55 + .45 * k)) + ")"; g.lineWidth = lw * (w / 400);
          g.beginPath(); c.pts.slice(0, n).forEach(([x, y], i) => i ? g.lineTo(x, y) : g.moveTo(x, y)); g.stroke();
        }
      }
      for (const e of s.embers) {
        const p = loop(t, e.sp, e.ph), x = (e.x + Math.sin((t + e.ph * 9) * 1.7) * e.dr) * w, y = h * (1.05 - p * 1.15);
        glowDot(g, x, y, e.sz * 3 * (w / 400), seal > .5 ? "#bdf3ff" : "#ffb070", (1 - p) * (.4 + .6 * k));
      }
      if (t < .35) { g.fillStyle = `rgba(255,170,90,${(.35 - t) * 1.4})`; g.fillRect(0, 0, w, h); }
    },
  },
  naberus: {
    name:"Nights of Naberus", where:"Necralisk, Deimos", blurb:"Daughter's seasonal treats and Naberus customizations for Mother Tokens.",
    match:/naberus|deimoshalloween/i, colors:["#2b1240", "#0b0512", "#ff9d3c"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#1d0a2e", "#07030c", [.5, .2, .6, "rgba(160,70,220,.28)"]);
      if (!s.l) { s.l = field(16, 21, r => ({ x:r(), ph:r(), sp:.05 + r() * .06, sz:.6 + r() * .7, sw:r() * TAU }));
        s.bats = field(9, 22, r => ({ y:.1 + r() * .45, ph:r(), sp:.12 + r() * .12, sz:.6 + r() * .6, dir:r() < .5 ? 1 : -1 })); }
      for (let i = 0; i < 4; i++) { // drifting fog bands
        const y = h * (.55 + i * .13), off = Math.sin(t * .4 + i) * w * .08;
        const gr = g.createLinearGradient(0, y - h * .1, 0, y + h * .1);
        gr.addColorStop(0, "rgba(120,60,170,0)"); gr.addColorStop(.5, `rgba(150,90,200,${.12 + .05 * k})`); gr.addColorStop(1, "rgba(120,60,170,0)");
        g.fillStyle = gr; g.fillRect(off - w * .1, y - h * .1, w * 1.2, h * .2);
      }
      const intro = ease(t / 1.6);
      for (const L of s.l) {
        const p = loop(t, L.sp, L.ph), x = (L.x + Math.sin(t * .8 + L.sw) * .02) * w, y = h * (1.1 - p * 1.25) + (1 - intro) * h * .4;
        const sz = L.sz * w * .035, fl = .75 + .25 * Math.sin(t * 9 + L.sw * 5);
        glowDot(g, x, y, sz * 2.6, "#ff9d3c", .35 * fl * (.6 + .4 * k));
        g.fillStyle = `rgba(255,170,80,${.9 * fl})`; g.beginPath(); g.ellipse(x, y, sz * .55, sz * .75, 0, 0, TAU); g.fill();
        g.fillStyle = "rgba(60,20,10,.8)"; g.fillRect(x - sz * .3, y - sz * .95, sz * .6, sz * .22);
      }
      g.fillStyle = "rgba(10,4,16,.92)";
      for (const b of s.bats) {
        const p = loop(t, b.sp, b.ph), x = b.dir > 0 ? (p * 1.3 - .15) * w : (1.15 - p * 1.3) * w, y = b.y * h + Math.sin(t * 3 + b.ph * 7) * h * .03;
        const sz = b.sz * w * .03, flap = Math.sin(t * 16 + b.ph * 20) * .6;
        g.beginPath(); g.moveTo(x, y);
        g.quadraticCurveTo(x - sz, y - sz * (1 + flap), x - sz * 2, y - sz * flap * .3);
        g.quadraticCurveTo(x - sz, y + sz * .2, x, y + sz * .35);
        g.quadraticCurveTo(x + sz, y + sz * .2, x + sz * 2, y - sz * flap * .3);
        g.quadraticCurveTo(x + sz, y - sz * (1 + flap), x, y); g.fill();
      }
    },
  },
  hallowed: {
    name:"Hallowed Nightmares", where:"Earth", blurb:"A Halloween tactical alert against the Jack O'Naut.",
    match:/hallowed|halloween|jack ?o|pumpkin/i, colors:["#1e2a10", "#070a04", "#ff8a1f"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#131b0a", "#040602", [.5, .62, .55, "rgba(255,120,20,.22)"]);
      if (!s.e) s.e = field(50, 31, r => ({ x:r(), ph:r(), sp:.1 + r() * .15, sz:.8 + r() * 1.8 }));
      // lightning: a guaranteed strike in the intro, then occasional ones
      const strike = (t > .5 && t < .7) || (t > INTRO && (Math.floor(t * 1.3) % 7 === 0) && (t * 1.3 % 1) < .12);
      if (strike) {
        g.fillStyle = "rgba(220,240,255,.28)"; g.fillRect(0, 0, w, h);
        const r = seeded(Math.floor(t * 10)); let x = w * (.25 + r() * .5), y = 0;
        g.strokeStyle = "rgba(230,245,255,.95)"; g.lineWidth = 2 * (w / 400); g.beginPath(); g.moveTo(x, y);
        while (y < h * .55) { x += (r() - .5) * w * .08; y += h * .06; g.lineTo(x, y); } g.stroke();
      }
      // the jack-o'-lantern face: grows in, then flickers
      const grow = ease((t - .2) / 1.2), cx = w * .5, cy = h * .6, R = Math.min(w, h) * .26 * grow;
      const fl = .78 + .22 * Math.sin(t * 13) * Math.sin(t * 7.3);
      if (R > 1) {
        glowDot(g, cx, cy, R * 2.4, "#ff7a14", .5 * fl * (.6 + .4 * k));
        g.fillStyle = "#c85a0c"; g.beginPath(); g.ellipse(cx, cy, R * 1.15, R, 0, 0, TAU); g.fill();
        g.strokeStyle = "rgba(90,30,0,.6)"; g.lineWidth = R * .05;
        for (const dx of [-.55, 0, .55]) { g.beginPath(); g.ellipse(cx + dx * R, cy, R * .35, R * .98, 0, 0, TAU); g.stroke(); }
        g.fillStyle = "#3a6a1c"; g.fillRect(cx - R * .08, cy - R * 1.25, R * .16, R * .3);
        g.fillStyle = `rgba(255,220,90,${fl})`;
        const tri = (x, y, s2, up) => { g.beginPath(); g.moveTo(x - s2, y + (up ? s2 : -s2) * .6); g.lineTo(x + s2, y + (up ? s2 : -s2) * .6); g.lineTo(x, y - (up ? s2 : -s2) * .6); g.fill(); };
        tri(cx - R * .42, cy - R * .2, R * .2, true); tri(cx + R * .42, cy - R * .2, R * .2, true);
        g.beginPath(); g.moveTo(cx - R * .6, cy + R * .25);
        for (let i = 0; i <= 6; i++) g.lineTo(cx - R * .6 + i * R * .2, cy + R * (.25 + (i % 2 ? .22 : .05)));
        g.lineTo(cx + R * .6, cy + R * .45); g.quadraticCurveTo(cx, cy + R * .75, cx - R * .6, cy + R * .45); g.fill();
      }
      for (const e of s.e) { const p = loop(t, e.sp, e.ph);
        glowDot(g, e.x * w, h * (1 - p), e.sz * 3 * (w / 400), "#ffae4a", (1 - p) * .7); }
    },
  },
  tau: {
    name:"Epoch Operation: Taubound", where:"Road to Tau", blurb:"Help open the path to the Tau System.",
    match:/tau\b|taubound|roadtotau|road to tau|epoch/i, colors:["#1a1d3a", "#04050c", "#fff1c2"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#0b0d1f", "#020308", [.5, .5, .6, "rgba(255,230,170,.18)"]);
      if (!s.st) s.st = field(220, 41, r => ({ a:r() * TAU, ph:r(), sp:.18 + r() * .5, hue:r() }));
      const cx = w / 2, cy = h / 2, warp = t < INTRO ? 1 + 4 * Math.sin(Math.PI * clamp(t / INTRO, 0, 1)) : 1, D = Math.hypot(w, h) * .6;
      g.lineCap = "round";
      for (const p of s.st) { // streaks flying outward from the far star
        const q = loop(t, p.sp * (.6 + .4 * k) * (t < INTRO ? 1.8 : 1), p.ph), d0 = q * q * D, d1 = d0 + (6 + q * 30) * warp * (w / 400);
        g.strokeStyle = p.hue < .2 ? `rgba(255,214,140,${q})` : `rgba(200,220,255,${q * .9})`;
        g.lineWidth = (.6 + q * 1.8) * (w / 400);
        g.beginPath(); g.moveTo(cx + Math.cos(p.a) * d0, cy + Math.sin(p.a) * d0); g.lineTo(cx + Math.cos(p.a) * d1, cy + Math.sin(p.a) * d1); g.stroke();
      }
      const grow = ease(t / 2.2), R = Math.min(w, h) * (.05 + .1 * grow) * (1 + .06 * Math.sin(t * 2.4));
      glowDot(g, cx, cy, R * 6, "#ffe9b0", .35 + .25 * k); glowDot(g, cx, cy, R * 2.2, "#fff6dc", .9);
      g.strokeStyle = `rgba(255,236,190,${.35 * k + .15})`; g.lineWidth = 1.2 * (w / 400);
      for (let i = 0; i < 3; i++) { const rr = R * (2.5 + i * 1.6 + ((t * .6 + i / 3) % 1) * 1.6); g.globalAlpha = 1 - ((t * .6 + i / 3) % 1);
        g.beginPath(); g.arc(cx, cy, rr, 0, TAU); g.stroke(); }
      g.globalAlpha = 1;
      if (t < .3) { g.fillStyle = `rgba(255,248,225,${(.3 - t) * 2.4})`; g.fillRect(0, 0, w, h); }
    },
  },
  plague: {
    name:"Plague Star", where:"Plains of Eidolon", blurb:"Fight the Infested Hydrolyst with Konzu's bounties.",
    match:/plague ?star|infestedplains|hydrolyst/i, colors:["#16301a", "#040a05", "#9cff6a"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#0c1d0f", "#030703", [.5, .9, .7, "rgba(120,220,60,.3)"]);
      if (!s.sp) { s.sp = field(90, 51, r => ({ x:r(), ph:r(), sp:.05 + r() * .1, sz:1 + r() * 3, sw:r() * TAU }));
        const r = seeded(52); s.v = []; for (let i = 0; i < 6; i++) { let x = r() * w, y = h, pts = [[x, y]]; for (let j = 0; j < 8; j++) { x += (r() - .5) * w * .1; y -= h * .09; pts.push([x, y]); } s.v.push(pts); } }
      const pulse = .5 + .5 * Math.sin(t * 3), grow = ease(t / 1.4);
      g.lineCap = "round";
      for (const v of s.v) { g.strokeStyle = `rgba(150,255,90,${(.25 + .35 * pulse) * (.5 + .5 * k)})`; g.lineWidth = 3 * (w / 400);
        g.beginPath(); v.slice(0, Math.max(1, Math.ceil(v.length * grow))).forEach(([x, y], i) => i ? g.lineTo(x, y) : g.moveTo(x, y)); g.stroke(); }
      for (const p of s.sp) { const q = loop(t, p.sp * (t < INTRO ? 2 : 1), p.ph);
        glowDot(g, (p.x + Math.sin(t + p.sw) * .02) * w, h * (1 - q), p.sz * 3 * (w / 400), "#b6ff7a", Math.sin(q * Math.PI) * (.4 + .5 * k)); }
    },
  },
  ghoul: {
    name:"Ghoul Purge", where:"Plains of Eidolon", blurb:"Grineer Ghouls burrow up across the Plains. Konzu needs you.",
    match:/ghoul/i, colors:["#3a0f0a", "#0c0303", "#ff5a3a"],
    draw(g, w, h, t, k, s){
      const shake = t < 1.2 ? Math.sin(t * 60) * (1.2 - t) * w * .012 : 0;
      g.save(); g.translate(shake, 0);
      bg(g, w, h, "#240806", "#070202", [.5, 1, .7, "rgba(255,70,40,.3)"]);
      if (!s.a) { s.a = field(80, 61, r => ({ x:r(), ph:r(), sp:.08 + r() * .12, sz:1 + r() * 2.5 }));
        const r = seeded(62); s.c = [[0, h * .82]]; let x = 0; while (x < w) { x += w * .05; s.c.push([x, h * (.8 + (r() - .5) * .08)]); } }
      const open = ease((t - .3) / 1.2);
      g.fillStyle = "#120403"; g.beginPath(); g.moveTo(0, h); s.c.forEach(([x, y]) => g.lineTo(x, y + 8 * open)); g.lineTo(w, h); g.fill();
      g.strokeStyle = `rgba(255,90,40,${.4 + .5 * open})`; g.lineWidth = 3 * (w / 400) * open + .5; g.beginPath(); s.c.forEach(([x, y], i) => i ? g.lineTo(x, y) : g.moveTo(x, y)); g.stroke();
      glowDot(g, w * .5, h * .85, w * .7, "#ff3a1a", .35 * open);
      if (!s.r) s.r = field(40, 63, r => ({ x:r(), vx:(r() - .5) * .5, vy:.6 + r() * .9, sz:2 + r() * 5, rot:r() * TAU }));
      const et = t - .5; // rubble thrown up as the Ghouls break through
      if (et > 0) for (const r of s.r) { const tt = et % 3.2, x = (r.x + r.vx * tt * .3) * w, y = h * .82 - (r.vy * tt - 1.1 * tt * tt) * h * .5;
        if (y < h * .9) { g.save(); g.translate(x, y); g.rotate(r.rot + tt * 4); g.fillStyle = "#3a1a10"; g.strokeStyle = "rgba(255,110,60,.7)"; const z = r.sz * (w / 400);
          g.fillRect(-z, -z * .7, z * 2, z * 1.4); g.strokeRect(-z, -z * .7, z * 2, z * 1.4); g.restore(); } }
      for (const a of s.a) { const q = loop(t, a.sp, a.ph); glowDot(g, a.x * w, h * (q * 1.1 - .05), a.sz * 3 * (w / 400), q > .6 ? "#ff6a3a" : "#e0c0a8", .6 * (.5 + .5 * k)); }
      g.restore();
    },
  },
  sentient: {
    name:"Sentient Anomaly", where:"Veil Proxima", blurb:"Sentient incursions such as Scarlet Spear and the Murmur.",
    match:/scarlet|sentient|murmur|anomaly|belly of the beast|orphix/i, colors:["#3a0d14", "#0a0306", "#ff5a6e"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#1f060b", "#060104", [.5, .5, .6, "rgba(255,70,100,.25)"]);
      if (!s.sh) s.sh = field(26, 71, r => ({ a:r() * TAU, d:.15 + r() * .45, sz:.5 + r(), sp:(r() - .5) * .6, rot:r() * TAU }));
      const cx = w / 2, cy = h / 2, gather = t < INTRO ? 1 - ease(t / 1.8) : 0, M = Math.min(w, h);
      for (let i = 0; i < 3; i++) { const q = (t * .45 + i / 3) % 1; g.strokeStyle = `rgba(255,110,130,${(1 - q) * (.4 + .4 * k)})`; g.lineWidth = 2 * (w / 400);
        g.beginPath(); g.arc(cx, cy, M * (.08 + q * .5), 0, TAU); g.stroke(); }
      for (const p of s.sh) { const a = p.a + t * p.sp, d = (p.d + gather * 1.2) * M;
        const x = cx + Math.cos(a) * d, y = cy + Math.sin(a) * d * .7, r = p.sz * M * .045;
        g.save(); g.translate(x, y); g.rotate(p.rot + t * p.sp * 2); g.fillStyle = "rgba(255,120,140,.85)"; g.strokeStyle = "rgba(255,210,220,.9)"; g.lineWidth = 1;
        g.beginPath(); g.moveTo(0, -r); g.lineTo(r * .9, r * .7); g.lineTo(-r * .9, r * .7); g.closePath(); g.fill(); g.stroke(); g.restore(); }
      glowDot(g, cx, cy, M * .18, "#ff8aa0", .6);
    },
  },
  stardays: {
    name:"Star Days", where:"Tenno Relays", blurb:"Ticker's seasonal shop, with gifts traded for Rose petals.",
    match:/star ?days|valentine|rose/i, colors:["#4a1534", "#12040c", "#ff8ac4"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#2c0a1f", "#0a0207", [.5, .4, .6, "rgba(255,120,190,.3)"]);
      if (!s.p) s.p = field(46, 81, r => ({ x:r(), ph:r(), sp:.06 + r() * .1, sz:.6 + r(), rot:(r() - .5), kind:r() < .55 }));
      const burst = t < INTRO ? ease(t / .9) : 1;
      for (const p of s.p) { const q = loop(t, p.sp * (t < INTRO ? 1.6 : 1), p.ph), x = (p.x + Math.sin(t + p.ph * 9) * .03) * w, y = h * (1.08 - q * 1.2);
        const sz = p.sz * Math.min(w, h) * .06 * burst, a = Math.sin(q * Math.PI) * (.5 + .5 * k);
        if (p.kind) heart(g, x, y, sz, "#ff7ab8", a, p.rot * Math.sin(t)); else star(g, x, y, sz * .55, "#ffe6a8", a, t * p.rot); }
    },
  },
  dogdays: {
    name:"Dog Days", where:"Earth", blurb:"Summer water fights against Kavat-and-Kubrow-themed Grineer.",
    match:/dog ?days|summer|water fight|soaktron/i, colors:["#0e4a6a", "#04131c", "#7ad8ff"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#0d3b57", "#03111a", [.82, .1, .5, "rgba(255,240,170,.35)"]);
      if (!s.d) s.d = field(60, 91, r => ({ x:r(), ph:r(), sp:.3 + r() * .4, sz:.6 + r() }));
      for (let i = 0; i < 4; i++) { const q = (t * .5 + i / 4) % 1, x = w * (.2 + .2 * i), y = h * .85;
        g.strokeStyle = `rgba(160,230,255,${(1 - q) * .6})`; g.lineWidth = 2 * (w / 400); g.beginPath(); g.ellipse(x, y, w * .14 * q, h * .04 * q, 0, 0, TAU); g.stroke(); }
      const splash = t < INTRO ? 1 : .5;
      for (const d of s.d) { const q = loop(t, d.sp * splash, d.ph), x = d.x * w, y = h * (q * q * 1.1 - .1), sz = d.sz * (w / 400) * 4;
        g.fillStyle = `rgba(170,235,255,${.75 * (.5 + .5 * k)})`; g.beginPath(); g.moveTo(x, y - sz * 1.6); g.quadraticCurveTo(x + sz, y, x, y + sz); g.quadraticCurveTo(x - sz, y, x, y - sz * 1.6); g.fill(); }
    },
  },
  winter: {
    name:"Tennobaum", where:"Origin System", blurb:"Winter festivities, gifts and holiday customizations.",
    match:/tennobaum|winter|christmas|holiday|snow|yule/i, colors:["#16324a", "#050c14", "#ffe6a8"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#0f2236", "#03070c");
      for (let i = 0; i < 3; i++) { const y = h * (.15 + i * .1), gr = g.createLinearGradient(0, y - h * .1, 0, y + h * .1), a = (.12 + .08 * Math.sin(t * .7 + i)) * (.6 + .4 * k);
        gr.addColorStop(0, "rgba(90,255,200,0)"); gr.addColorStop(.5, `rgba(${i === 1 ? "160,120,255" : "90,255,200"},${a})`); gr.addColorStop(1, "rgba(90,255,200,0)");
        g.fillStyle = gr; g.fillRect(0, y - h * .1 + Math.sin(t * .5 + i) * h * .03, w, h * .2); }
      if (!s.f) s.f = field(120, 101, r => ({ x:r(), ph:r(), sp:.05 + r() * .09, sz:.5 + r() * 1.8, sw:r() * TAU, gold:r() < .12 }));
      for (const f of s.f) { const q = loop(t, f.sp * (t < INTRO ? 1.8 : 1), f.ph), x = (f.x + Math.sin(t * .9 + f.sw) * .025) * w, y = h * (q * 1.1 - .05);
        if (f.gold) star(g, x, y, f.sz * 2.2 * (w / 400), "#ffe08a", .8 * (.5 + .5 * Math.sin(t * 5 + f.sw)), t);
        else { g.globalAlpha = .85; g.fillStyle = "#eef7ff"; g.beginPath(); g.arc(x, y, f.sz * (w / 400), 0, TAU); g.fill(); g.globalAlpha = 1; } }
    },
  },
  lunar: {
    name:"Lunar New Year", where:"Origin System", blurb:"Fireworks, lanterns and seasonal glyphs and decorations.",
    match:/lunar|new year/i, colors:["#4a0e0e", "#120303", "#ffd27a"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#2a0706", "#080101", [.5, .8, .6, "rgba(255,60,40,.25)"]);
      if (!s.b) s.b = field(7, 111, r => ({ x:.12 + r() * .76, y:.15 + r() * .4, ph:r(), col:r() < .5 ? "#ffd27a" : "#ff6a5a" }));
      for (const b of s.b) { const q = loop(t, .45, b.ph), cx = b.x * w, cy = b.y * h, R = ease(q) * Math.min(w, h) * .22, a = (1 - q) * (.5 + .5 * k);
        if (q < .15) { glowDot(g, cx, cy + (1 - q / .15) * h * .4, 6 * (w / 400), b.col, .9); continue; }
        glowDot(g, cx, cy, R * .9, b.col, a * .25);
        g.lineCap = "round";
        for (let i = 0; i < 26; i++) { const ang = i / 26 * TAU, x = cx + Math.cos(ang) * R, y = cy + Math.sin(ang) * R + q * q * h * .06;
          g.strokeStyle = b.col; g.globalAlpha = a * .8; g.lineWidth = 2 * (w / 400);
          g.beginPath(); g.moveTo(cx + Math.cos(ang) * R * .55, cy + Math.sin(ang) * R * .55 + q * q * h * .03); g.lineTo(x, y); g.stroke(); g.globalAlpha = 1;
          glowDot(g, x, y, 7 * (w / 400), "#fff4d0", a); } }
      for (let i = 0; i < 5; i++) { const x = w * (.1 + i * .2), y = h * (.82 + Math.sin(t * 1.4 + i) * .02);
        glowDot(g, x, y, w * .06, "#ff5040", .5); g.fillStyle = "#e0402e"; g.beginPath(); g.ellipse(x, y, w * .028, h * .06, 0, 0, TAU); g.fill();
        g.fillStyle = "#ffd27a"; g.fillRect(x - w * .015, y - h * .07, w * .03, h * .012); }
    },
  },
  armada: {
    name:"Fomorian & Razorback", where:"Origin System", blurb:"Balor Fomorians and Razorback Armadas threaten the Tenno. Sabotage them.",
    match:/fomorian|razorback|balor|armada|sabotage|disruption alert/i, colors:["#3a1010", "#0a0404", "#ff4a3a"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#1d0808", "#060202");
      const sweep = (t * .6) % 1; // alarm sweep
      const gr = g.createLinearGradient(sweep * w * 1.6 - w * .4, 0, sweep * w * 1.6 - w * .1, 0);
      gr.addColorStop(0, "rgba(255,40,30,0)"); gr.addColorStop(.5, `rgba(255,60,40,${.18 + .12 * k})`); gr.addColorStop(1, "rgba(255,40,30,0)"); g.fillStyle = gr; g.fillRect(0, 0, w, h);
      const arrive = ease(t / 1.8), sx = lerp(w * 1.3, w * .55, arrive), sy = h * .38, L = w * .55;
      glowDot(g, sx + L * .5, sy, L * .35, "#ff4a2a", .35);
      const hull = g.createLinearGradient(0, sy - h * .1, 0, sy + h * .1); hull.addColorStop(0, "#5a2a22"); hull.addColorStop(1, "#140505");
      g.fillStyle = hull; g.strokeStyle = "rgba(255,140,110,.85)"; g.lineWidth = 2 * (w / 400);
      g.beginPath(); g.moveTo(sx - L * .5, sy); g.lineTo(sx - L * .2, sy - h * .09); g.lineTo(sx + L * .45, sy - h * .06); g.lineTo(sx + L * .55, sy + h * .02);
      g.lineTo(sx + L * .2, sy + h * .1); g.lineTo(sx - L * .3, sy + h * .07); g.closePath(); g.fill(); g.stroke();
      for (let i = 0; i < 6; i++) glowDot(g, sx - L * .25 + i * L * .13, sy + h * .02, 4 * (w / 400), "#ff5a3a", .5 + .5 * Math.sin(t * 6 + i));
      if (!s.m) s.m = field(10, 121, r => ({ x:r(), ph:r(), sp:.3 + r() * .3 }));
      g.lineCap = "round";
      for (const m of s.m) { const q = loop(t, m.sp, m.ph), x = m.x * w + q * w * .2, y = h * (q * 1.2 - .1);
        g.strokeStyle = `rgba(255,170,90,${(1 - q) * (.5 + .5 * k)})`; g.lineWidth = 2 * (w / 400); g.beginPath(); g.moveTo(x, y); g.lineTo(x - w * .05, y - h * .1); g.stroke(); }
    },
  },
  celebrate: {
    name:"Anniversary & TennoCon", where:"Origin System", blurb:"Celebration events with login gifts, alerts and free rewards.",
    match:/anniversary|tennocon|birthday|celebrat|devstream/i, colors:["#3a2a08", "#0b0802", "#ffd76a"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#1e1605", "#060401");
      for (let i = 0; i < 3; i++) { const a = Math.sin(t * .8 + i * 2) * .35, x = w * (.2 + i * .3);
        g.save(); g.translate(x, h); g.rotate(a); const gr = g.createLinearGradient(0, 0, 0, -h * 1.2);
        gr.addColorStop(0, `rgba(255,230,150,${.3 * (.5 + .5 * k)})`); gr.addColorStop(1, "rgba(255,230,150,0)"); g.fillStyle = gr;
        g.beginPath(); g.moveTo(-w * .03, 0); g.lineTo(w * .03, 0); g.lineTo(w * .14, -h * 1.2); g.lineTo(-w * .14, -h * 1.2); g.fill(); g.restore(); }
      if (!s.c) s.c = field(110, 131, r => ({ x:r(), ph:r(), sp:.12 + r() * .14, rot:r() * TAU, col:["#ffd76a", "#82e4ff", "#ff8ac4", "#ffffff"][Math.floor(r() * 4)] }));
      const burst = t < INTRO ? 1.8 : 1;
      for (const c of s.c) { const q = loop(t, c.sp * burst, c.ph), x = (c.x + Math.sin(t * 2 + c.rot) * .03) * w, y = h * (q * 1.15 - .08);
        g.save(); g.translate(x, y); g.rotate(c.rot + t * 3); g.scale(1, Math.sin(t * 6 + c.rot)); g.fillStyle = c.col; g.globalAlpha = .9;
        g.fillRect(-3 * (w / 400), -1.5 * (w / 400), 6 * (w / 400), 3 * (w / 400)); g.restore(); g.globalAlpha = 1; }
    },
  },
  kuva: {
    name:"Kuva Flood", where:"Kuva Fortress", blurb:"Kuva Floods and the Queens' forces.",
    match:/kuva|lich|sister|flood|queens/i, colors:["#420a10", "#0e0204", "#ff4a5a"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#22050a", "#070102");
      const level = h * (1 - .45 * ease(t / 1.8) - .04 * Math.sin(t * .8));
      for (let layer = 0; layer < 3; layer++) {
        g.fillStyle = ["rgba(160,15,30,.55)", "rgba(200,25,40,.55)", "rgba(240,60,70,.6)"][layer];
        g.beginPath(); g.moveTo(0, h);
        for (let x = 0; x <= w; x += w / 40) g.lineTo(x, level + layer * h * .05 + Math.sin(x / w * 7 + t * (1.4 + layer * .4) + layer) * h * .025);
        g.lineTo(w, h); g.fill(); }
      if (!s.b) s.b = field(30, 141, r => ({ x:r(), ph:r(), sp:.15 + r() * .2, sz:1 + r() * 3 }));
      for (const b of s.b) { const q = loop(t, b.sp, b.ph); const y = lerp(h, level, q);
        g.strokeStyle = `rgba(255,150,160,${(1 - q) * (.4 + .5 * k)})`; g.lineWidth = 1; g.beginPath(); g.arc(b.x * w, y, b.sz * (w / 400) * 2, 0, TAU); g.stroke(); }
    },
  },
  void: { // fallback for any event without its own theme
    name:"Event", where:"Origin System", blurb:"A limited-time event.",
    match:null, colors:["#122436", "#04080d", "#e2bb64"],
    draw(g, w, h, t, k, s){
      bg(g, w, h, "#0c1824", "#030508", [.5, .5, .6, "rgba(130,228,255,.18)"]);
      if (!s.p) s.p = field(70, 151, r => ({ a:r() * TAU, ph:r(), sp:.12 + r() * .2, gold:r() < .5 }));
      const cx = w / 2, cy = h / 2, M = Math.min(w, h), spin = t * .5;
      g.save(); g.translate(cx, cy); g.rotate(spin);
      g.strokeStyle = `rgba(226,187,100,${.5 + .3 * k})`; g.lineWidth = 1.5 * (w / 400);
      for (let i = 0; i < 3; i++) { const r = M * (.16 + i * .08) * ease(t / (1 + i * .3)); g.beginPath(); for (let j = 0; j <= 6; j++) { const a = j / 6 * TAU; g.lineTo(Math.cos(a) * r, Math.sin(a) * r); } g.stroke(); g.rotate(-spin * .8); }
      g.restore();
      diamond(g, cx, cy, M * .07 * ease(t / .8), "#e2bb64", .95, 0);
      for (const p of s.p) { const q = loop(t, p.sp * (t < INTRO ? 1.6 : 1), p.ph), d = q * M * .7;
        glowDot(g, cx + Math.cos(p.a) * d, cy + Math.sin(p.a) * d, 4 * (w / 400), p.gold ? "#e2bb64" : "#82e4ff", (1 - q) * (.4 + .6 * k)); }
    },
  },
};

/* ---------- matching and playback ---------- */
function themeFor(ev){
  const hay = `${ev.tag || ""} ${ev.description || ""} ${ev.tooltip || ""} ${ev.node || ""}`;
  for (const [key, th] of Object.entries(THEMES)) if (th.match && th.match.test(hay)) return key;
  return "void";
}
function size(c){
  const dpr = Math.min(2, window.devicePixelRatio || 1), w = c.clientWidth || 300, h = c.clientHeight || 160;
  if (c.width !== Math.round(w * dpr) || c.height !== Math.round(h * dpr)) { c.width = Math.round(w * dpr); c.height = Math.round(h * dpr); }
  const g = c.getContext("2d"); g.setTransform(dpr, 0, 0, dpr, 0, 0); return [g, w, h];
}
/* One still frame for a title card. */
function still(canvas, key){
  const th = THEMES[key] || THEMES.void; const [g, w, h] = size(canvas);
  try { th.draw(g, w, h, 3.4, .7, {}); } catch (e) {}
}
/* The tap animation. Finds its canvas by id every frame, so a page redraw doesn't stop it. */
let run = null;
function play(canvasId, key, reduced){
  stop();
  const th = THEMES[key] || THEMES.void, state = {}, t0 = performance.now();
  const frame = now => {
    const c = document.getElementById(canvasId);
    if (!c || !run) { run = null; return; }
    if (document.visibilityState === "visible") {
      const t = reduced ? 3.4 : (now - t0) / 1000;
      const k = t < INTRO ? 1 : .45 + .1 * Math.sin(t * .7);
      const [g, w, h] = size(c);
      try { th.draw(g, w, h, t, k, state); } catch (e) {}
      if (reduced) { run = null; return; }
    }
    run.id = requestAnimationFrame(frame);
  };
  run = { id:requestAnimationFrame(frame) };
}
function stop(){ if (run) cancelAnimationFrame(run.id); run = null; }

window.EventFX = { THEMES, themeFor, still, play, stop, running:() => !!run, INTRO };
})();
