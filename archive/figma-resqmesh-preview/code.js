// Static, editable recreation of the current Compose UI. No network or Figma reads.
// Each run appends one named group to the current page; existing layers are untouched.
const C = {
  bg: '#000000', surface: '#121212', raised: '#1E1E1E', border: '#2C2C2C',
  orange: '#FF5A00', orangeDim: '#3B2117', green: '#00E676', greenDim: '#123326',
  amber: '#FFAB00', amberDim: '#3A2C0F', red: '#FF2400', redDim: '#4A1C22',
  text: '#F5F5F5', muted: '#A0A0A0', faint: '#6B6B6B', black: '#000000',
  canvas: '#23262B', white: '#FFFFFF'
};
const W = 390, H = 844;
let fonts = {};
let createdRoot = null;
function rgb(hex) {
  const n = parseInt(hex.slice(1), 16);
  return { r: ((n >> 16) & 255) / 255, g: ((n >> 8) & 255) / 255, b: (n & 255) / 255 };
}
function solid(hex, opacity = 1) { return { type: 'SOLID', color: rgb(hex), opacity }; }
function put(parent, node, name, x, y, w, h) {
  parent.appendChild(node); node.name = name; node.x = x; node.y = y;
  node.resize(Math.max(0.01, w), Math.max(0.01, h));
  return node;
}
function box(parent, name, x, y, w, h, fill, radius = 0, stroke = null, strokeOpacity = 1) {
  const n = put(parent, figma.createRectangle(), name, x, y, w, h);
  n.fills = fill ? [solid(fill)] : [];
  n.cornerRadius = radius;
  if (stroke) { n.strokes = [solid(stroke, strokeOpacity)]; n.strokeWeight = 1; }
  return n;
}
function circle(parent, name, x, y, d, fill, stroke = null, strokeOpacity = 1) {
  const n = put(parent, figma.createEllipse(), name, x, y, d, d);
  n.fills = fill ? [solid(fill)] : [];
  if (stroke) { n.strokes = [solid(stroke, strokeOpacity)]; n.strokeWeight = 1; }
  return n;
}
function label(parent, name, value, x, y, w, size = 12, color = C.text, weight = 'Regular', mono = false, align = 'LEFT', h = 24) {
  const n = figma.createText();
  parent.appendChild(n); n.name = name; n.x = x; n.y = y;
  n.fontName = mono ? fonts.mono[weight] : fonts.body[weight];
  n.fontSize = size; n.fills = [solid(color)];
  n.textAlignHorizontal = align; n.textAlignVertical = 'CENTER';
  n.textAutoResize = 'NONE'; n.resize(w, h); n.characters = value;
  return n;
}
function line(parent, name, x1, y1, x2, y2, color, thickness = 1, opacity = 1) {
  const dx = x2 - x1, dy = y2 - y1, len = Math.sqrt(dx * dx + dy * dy);
  const n = box(parent, name, x1, y1, len, thickness, color, thickness / 2);
  n.rotation = Math.atan2(dy, dx) * 180 / Math.PI;
  n.opacity = opacity;
  return n;
}
function frame(parent, name, x, y, w, h, fill = C.bg, radius = 0) {
  const n = put(parent, figma.createFrame(), name, x, y, w, h);
  n.fills = [solid(fill)]; n.cornerRadius = radius; n.clipsContent = true;
  return n;
}
function pill(parent, name, value, x, y, w, h, fill, stroke, color = C.text, size = 10) {
  box(parent, name + ' / background', x, y, w, h, fill, Math.min(10, h / 2), stroke);
  label(parent, name + ' / label', value, x + 5, y, w - 10, size, color, 'Bold', true, 'CENTER', h);
}
function iconTile(parent, name, glyph, x, y, fill, color) {
  box(parent, name + ' / tile', x, y, 38, 38, fill, 12);
  label(parent, name + ' / icon', glyph, x, y + 1, 38, 19, color, 'Bold', false, 'CENTER', 36);
}
function nav(screen, selected) {
  box(screen, 'Navigation / floating island', 16, 748, 358, 68, C.raised, 34, C.border);
  const tabs = [{ name: 'Mission', x: 32, glyph: '◎' }, { name: 'Messages', x: 109, glyph: '▢' }, { name: 'Voice', x: 186, glyph: '≋' }];
  tabs.forEach(t => {
    const on = t.name === selected;
    if (on) box(screen, `Navigation / ${t.name} selected`, t.x, 756, 62, 52, C.orangeDim, 20, C.orange, 0.4);
    label(screen, `Navigation / ${t.name} icon`, t.glyph, t.x, 762, 62, 25, on ? C.orange : C.muted, 'Bold', false, 'CENTER', 26);
    if (on) circle(screen, `Navigation / ${t.name} indicator`, t.x + 29, 796, 4, C.orange);
  });
  box(screen, 'Navigation / divider', 258, 768, 1, 28, C.border);
  circle(screen, 'Navigation / SOS button', 282, 754, 56, C.red);
  label(screen, 'Navigation / SOS label', 'SOS', 282, 767, 56, 16, C.white, 'Bold', true, 'CENTER', 30);
}
function missionHeader(s, ready) {
  circle(s, 'Header / mesh status dot', 20, 27, 8, ready ? C.green : C.amber);
  label(s, 'Header / mesh status', ready ? 'DIRECT LINK READY' : 'SEARCHING FOR PEERS', 34, 20, 275, 10, ready ? C.green : C.amber, 'Bold', true, 'LEFT', 22);
  label(s, 'Header / identity', 'Alex Rivera', 20, 45, 285, 24, C.text, 'Bold', false, 'LEFT', 32);
  box(s, 'Header / profile button', 322, 25, 48, 48, C.surface, 14, C.border);
  circle(s, 'Header / profile head', 341, 35, 10, C.orange);
  box(s, 'Header / profile shoulders', 335, 49, 22, 12, C.orange, 7);
}
function readiness(s) {
  box(s, 'Readiness / card', 20, 106, 350, 142, C.surface, 20, C.green, 0.35);
  iconTile(s, 'Readiness / radio', '◎', 36, 122, C.greenDim, C.green);
  label(s, 'Readiness / heading', 'Nearby sharing is on', 90, 123, 260, 16, C.text, 'Bold', false, 'LEFT', 24);
  label(s, 'Readiness / description', 'Ready to find people.', 90, 147, 260, 12, C.muted, 'Regular', false, 'LEFT', 18);
  box(s, 'Readiness / location card', 36, 181, 318, 51, C.raised, 12, C.green, 0.35);
  circle(s, 'Readiness / location icon', 50, 195, 24, C.greenDim);
  circle(s, 'Readiness / location dot', 58, 203, 8, C.green);
  label(s, 'Readiness / location title', 'Location found', 84, 190, 250, 12, C.text, 'Bold', false, 'LEFT', 19);
  label(s, 'Readiness / location note', 'A last known location is available.', 84, 209, 252, 10, C.muted, 'Regular', false, 'LEFT', 16);
}
function metrics(s, ready) {
  const data = [
    ['DIRECT', ready ? '2' : '0', 'Direct peers', C.green],
    ['RELAY', ready ? '1' : '0', 'Multi-hop', C.orange],
    ['NEARBY', ready ? '3' : '0', 'Unconnected', C.amber]
  ];
  data.forEach((d, i) => {
    const x = 20 + i * 119;
    box(s, `Metric / ${d[0]} card`, x, 264, 112, 91, C.surface, 14, d[3], 0.25);
    label(s, `Metric / ${d[0]} label`, d[0], x + 10, 272, 82, 10, C.muted, 'Bold', true, 'LEFT', 18);
    circle(s, `Metric / ${d[0]} dot`, x + 95, 279, 7, d[3]);
    label(s, `Metric / ${d[0]} value`, d[1], x + 10, 294, 75, 24, C.text, 'Bold', true, 'LEFT', 31);
    label(s, `Metric / ${d[0]} subtitle`, d[2], x + 10, 329, 96, 10, C.muted, 'Regular', false, 'LEFT', 15);
  });
}
function graph(s, ready) {
  box(s, 'Network / card', 20, 371, 350, 302, C.raised, 20, C.border);
  circle(s, 'Network / hub icon', 36, 391, 23, ready ? C.greenDim : C.orangeDim);
  label(s, 'Network / hub glyph', '⌘', 36, 392, 23, 16, ready ? C.green : C.orange, 'Bold', false, 'CENTER', 22);
  label(s, 'Network / title', 'Your network', 68, 385, 230, 16, C.text, 'Bold', false, 'LEFT', 22);
  label(s, 'Network / state', ready ? '2 direct · 1 relayed · Tap to inspect' : 'Searching · Tap to inspect', 68, 408, 265, 11, C.muted, 'Regular', false, 'LEFT', 18);
  label(s, 'Network / chevron', '›', 334, 391, 20, 24, C.muted, 'Regular', false, 'CENTER', 28);
  box(s, 'Network / radar viewport', 36, 444, 318, 176, C.surface, 14, C.border);
  [70, 112, 154].forEach((d, i) => circle(s, `Network / ring ${i + 1}`, 195 - d / 2, 532 - d / 2, d, null, C.orange, i === 0 ? 0.22 : 0.1));
  if (ready) {
    line(s, 'Network / direct path A', 195, 532, 230, 532, C.orange, 2, 0.35);
    line(s, 'Network / direct path B', 195, 532, 162, 532, C.orange, 2, 0.35);
    circle(s, 'Network / direct peer A', 222, 524, 16, C.orange);
    circle(s, 'Network / direct peer B', 154, 524, 16, C.orange);
    circle(s, 'Network / relayed peer', 186, 453, 10, C.faint);
  }
  circle(s, 'Network / this device glow', 179, 516, 32, C.greenDim);
  circle(s, 'Network / this device', 185, 522, 20, C.green);
  if (ready) {
    label(s, 'Network / peers caption', 'LINKED:', 36, 632, 62, 10, C.muted, 'Bold', true, 'LEFT', 24);
    pill(s, 'Network / peer Bravo', 'BRAVO', 103, 632, 95, 24, C.greenDim, C.green, C.text, 10);
    pill(s, 'Network / peer Charlie', 'CHARLIE', 205, 632, 110, 24, C.greenDim, C.green, C.text, 10);
  } else {
    label(s, 'Network / no nodes', 'No direct peers yet', 38, 632, 280, 12, C.muted, 'Regular', false, 'LEFT', 24);
  }
}
function mission(s, ready) {
  missionHeader(s, ready); readiness(s); metrics(s, ready); graph(s, ready);
  box(s, 'Operations / messages card', 20, 689, 169, 112, C.raised, 16, C.border);
  box(s, 'Operations / voice card', 201, 689, 169, 112, C.raised, 16, C.border);
  iconTile(s, 'Operations / messages', '▢', 34, 702, C.orangeDim, C.orange);
  iconTile(s, 'Operations / voice', '≋', 215, 702, C.greenDim, C.green);
  label(s, 'Operations / messages title', 'Messages', 34, 747, 140, 15, C.text, 'Bold', false, 'LEFT', 23);
  label(s, 'Operations / voice title', 'Voice', 215, 747, 135, 15, C.text, 'Bold', false, 'LEFT', 23);
  nav(s, 'Mission');
}
function inboxHeader(s) {
  label(s, 'Header / title', 'Messages', 20, 20, 245, 30, C.text, 'Bold', false, 'LEFT', 40);
  label(s, 'Header / subtitle', 'Local conversations, even when the internet is gone.', 20, 58, 350, 12, C.muted, 'Regular', false, 'LEFT', 20);
  pill(s, 'Header / local mesh', 'LOCAL MESH', 278, 24, 92, 25, C.raised, C.border, C.muted, 9);
}
function community(s) {
  box(s, 'Community / card', 20, 100, 350, 127, C.raised, 20, C.border);
  iconTile(s, 'Community / broadcast', '◉', 36, 116, C.orangeDim, C.orange);
  circle(s, 'Community / live dot', 88, 121, 6, C.green);
  label(s, 'Community / title', 'Community', 100, 113, 186, 16, C.text, 'Bold', false, 'LEFT', 24);
  label(s, 'Community / caption', 'GLOBAL BROADCAST', 88, 140, 190, 9, C.orange, 'Bold', true, 'LEFT', 17);
  pill(s, 'Community / channel', 'CH 1', 299, 122, 54, 27, C.surface, C.border, C.muted, 10);
  box(s, 'Community / preview background', 36, 170, 318, 40, C.surface, 10);
  label(s, 'Community / preview', 'Channel 1 · Public Mesh', 46, 179, 280, 12, C.muted, 'Regular', false, 'LEFT', 22);
  label(s, 'Community / open', '›', 330, 177, 18, 20, C.muted, 'Regular', false, 'CENTER', 25);
}
function filters(s, count) {
  const entries = [['ALL', count, 20, 76], ['DIRECT', count ? 1 : 0, 102, 84], ['RELAYS', count ? 1 : 0, 192, 84], ['UNREAD', count ? 1 : 0, 282, 88]];
  entries.forEach((e, i) => pill(s, `Filter / ${e[0]}`, `${e[0]} (${e[1]})`, e[2], 245, e[3], 32, i === 0 ? C.orangeDim : C.raised, i === 0 ? C.orange : C.border, i === 0 ? C.orange : C.muted, 10));
}
function inboxRow(s, name, preview, status, time, unread, y) {
  const color = status === 'DIRECT LINK' ? C.green : status === 'MESH RELAY' ? C.orange : C.muted;
  box(s, `Conversation / ${name} card`, 20, y, 350, 92, C.raised, 14, C.border);
  circle(s, `Conversation / ${name} avatar`, 32, y + 15, 46, C.surface, color);
  label(s, `Conversation / ${name} initial`, name.charAt(0), 32, y + 20, 46, 17, C.text, 'Bold', false, 'CENTER', 34);
  circle(s, `Conversation / ${name} status dot`, 68, y + 49, 10, color);
  label(s, `Conversation / ${name} name`, name, 90, y + 12, 190, 14, C.text, 'Bold', false, 'LEFT', 24);
  label(s, `Conversation / ${name} time`, time, 290, y + 14, 65, 10, C.muted, 'Regular', true, 'RIGHT', 20);
  label(s, `Conversation / ${name} preview`, preview, 90, y + 38, 235, 12, C.muted, 'Regular', false, 'LEFT', 21);
  pill(s, `Conversation / ${name} route`, status, 90, y + 65, status === 'DIRECT LINK' ? 92 : 88, 18, status === 'DIRECT LINK' ? C.greenDim : C.orangeDim, color, color, 9);
  if (unread) { circle(s, `Conversation / ${name} unread badge`, 328, y + 59, 20, C.orange); label(s, `Conversation / ${name} unread count`, String(unread), 328, y + 60, 20, 10, C.black, 'Bold', true, 'CENTER', 18); }
}
function inbox(s, empty) {
  inboxHeader(s); community(s); filters(s, empty ? 0 : 2);
  label(s, 'Private / section title', 'Private', 20, 297, 240, 16, C.text, 'Bold', false, 'LEFT', 25);
  label(s, 'Private / count', empty ? '0 CHATS' : '2 CHATS', 295, 299, 75, 10, C.muted, 'Bold', true, 'RIGHT', 22);
  if (empty) {
    box(s, 'Empty inbox / card', 20, 337, 350, 238, C.raised, 20, C.border);
    circle(s, 'Empty inbox / icon background', 168, 362, 54, C.surface);
    label(s, 'Empty inbox / icon', '▢', 168, 367, 54, 26, C.orange, 'Bold', false, 'CENTER', 45);
    label(s, 'Empty inbox / title', 'No messages yet', 60, 434, 270, 16, C.text, 'Bold', false, 'CENTER', 25);
    label(s, 'Empty inbox / description', 'Start a chat with someone nearby.', 36, 462, 318, 12, C.muted, 'Regular', false, 'CENTER', 22);
    pill(s, 'Empty inbox / action', 'FIND NEARBY PEOPLE', 98, 510, 194, 38, C.orangeDim, C.orange, C.orange, 11);
  } else {
    inboxRow(s, 'Bravo', 'Can you see my messages?', 'DIRECT LINK', '9:41 AM', 2, 337);
    inboxRow(s, 'Charlie', 'I will check the east side.', 'MESH RELAY', '9:28 AM', 0, 435);
  }
  circle(s, 'New message / floating button', 314, 673, 56, C.orange);
  label(s, 'New message / plus', '+', 314, 680, 56, 26, C.black, 'Regular', false, 'CENTER', 42);
  nav(s, 'Messages');
}
function chatHeader(s) {
  box(s, 'Chat / top bar', 0, 0, 390, 78, C.surface);
  label(s, 'Chat / back', '‹', 8, 19, 42, 31, C.text, 'Regular', false, 'CENTER', 42);
  circle(s, 'Chat / avatar', 58, 17, 44, C.raised);
  label(s, 'Chat / avatar initial', 'B', 58, 20, 44, 18, C.text, 'Bold', false, 'CENTER', 38);
  label(s, 'Chat / recipient', 'Bravo', 111, 14, 225, 18, C.text, 'Bold', false, 'LEFT', 28);
  label(s, 'Chat / availability', 'Online (Direct)', 111, 42, 205, 12, C.green, 'Bold', false, 'LEFT', 20);
  label(s, 'Chat / delete', '×', 338, 23, 40, 25, C.muted, 'Regular', false, 'CENTER', 30);
}
function bubble(s, name, message, mine, y, status, time, wide = false) {
  const w = wide ? 245 : 224, x = mine ? 374 - w : 16, h = 74;
  box(s, `${name} / bubble`, x, y, w, h, mine ? C.orange : C.surface, 10);
  label(s, `${name} / message`, message, x + 12, y + 8, w - 24, 15, mine ? C.black : C.text, 'Regular', false, 'LEFT', 37);
  if (mine) label(s, `${name} / delivery`, status, x + 12, y + 53, w - 75, 11, C.black, 'Regular', false, 'RIGHT', 15);
  label(s, `${name} / time`, time, x + w - 62, y + 53, 50, 11, mine ? C.black : C.muted, 'Regular', false, 'RIGHT', 15);
}
function composer(s) {
  box(s, 'Composer / bottom region', 0, 761, 390, 83, C.bg);
  circle(s, 'Composer / add tool', 12, 779, 48, C.raised, C.border);
  label(s, 'Composer / plus', '+', 12, 782, 48, 26, C.orange, 'Regular', false, 'CENTER', 40);
  box(s, 'Composer / input', 68, 774, 252, 54, C.raised, 26, C.border);
  label(s, 'Composer / hint', 'Message Bravo', 84, 788, 200, 14, C.muted, 'Regular', false, 'LEFT', 24);
  circle(s, 'Composer / send', 330, 778, 48, C.orange);
  label(s, 'Composer / send arrow', '›', 330, 781, 48, 26, C.black, 'Bold', false, 'CENTER', 37);
}
function chat(s, failure) {
  chatHeader(s);
  bubble(s, 'Received 1', 'Morning. Are you nearby?', false, 385, '', '9:32 AM', true);
  bubble(s, 'Sent 1', 'Yes, I have a direct link.', true, 468, 'Delivered', '9:34 AM', true);
  bubble(s, 'Received 2', 'Great. Meet at the gate.', false, 551, '', '9:36 AM', true);
  if (failure) {
    bubble(s, 'Sent failed', 'On my way now.', true, 634, 'Failed', '9:38 AM');
  } else {
    bubble(s, 'Sent delivered', 'I am on my way.', true, 634, 'Delivered', '9:40 AM');
  }
  composer(s);
}
async function loadFont(family, style) {
  try { await figma.loadFontAsync({ family, style }); return { family, style }; }
  catch (_) { await figma.loadFontAsync({ family: 'Inter', style }); return { family: 'Inter', style }; }
}
async function main() {
  fonts = {
    body: { Regular: await loadFont('Roboto', 'Regular'), Bold: await loadFont('Roboto', 'Bold') },
    mono: { Regular: await loadFont('Roboto Mono', 'Regular'), Bold: await loadFont('Roboto Mono', 'Bold') }
  };
  const root = frame(figma.currentPage, 'ResQMesh current UI - Night - Mission + Messages', 0, 0, 1270, 1830, C.canvas);
  createdRoot = root;
  const defs = [
    ['01 Mission - direct peers', 20, 68, s => mission(s, true)],
    ['02 Mission - searching', 440, 68, s => mission(s, false)],
    ['03 Messages - conversations', 860, 68, s => inbox(s, false)],
    ['04 Messages - empty', 20, 944, s => inbox(s, true)],
    ['05 Chat - delivered', 440, 944, s => chat(s, false)],
    ['06 Chat - failed delivery', 860, 944, s => chat(s, true)]
  ];
  defs.forEach(([name, x, y, draw]) => {
    label(root, `${name} / frame caption`, name.toUpperCase(), x, y - 34, 390, 15, C.text, 'Bold', true, 'LEFT', 25);
    const s = frame(root, name, x, y, W, H, C.bg);
    draw(s);
  });
  figma.currentPage.selection = [root];
  figma.viewport.scrollAndZoomIntoView([root]);
  figma.notify('Created 6 editable ResQMesh Night frames on this page.');
  figma.closePlugin();
}
main().catch(error => {
  console.error(error);
  if (createdRoot) createdRoot.remove();
  figma.closePlugin('Preview creation failed: ' + String(error));
});
