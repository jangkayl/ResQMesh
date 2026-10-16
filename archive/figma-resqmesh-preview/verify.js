// A lightweight offline Figma API stand-in. Run: node verify.js
const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

const page = { type: 'PAGE', children: [], selection: [], appendChild(node) { move(this, node); } };
function move(parent, node) {
  if (node.parent) node.parent.children = node.parent.children.filter(x => x !== node);
  parent.children.push(node); node.parent = parent;
}
function node(type) {
  const value = { type, name: '', children: [], x: 0, y: 0,
    resize(width, height) { this.width = width; this.height = height; },
    remove() { if (this.parent) this.parent.children = this.parent.children.filter(x => x !== this); },
    appendChild(child) { move(this, child); }
  };
  move(page, value);
  return value;
}
let closed = false;
const figma = {
  currentPage: page,
  createFrame: () => node('FRAME'),
  createRectangle: () => node('RECTANGLE'),
  createEllipse: () => node('ELLIPSE'),
  createText: () => node('TEXT'),
  loadFontAsync: async () => {},
  viewport: { scrollAndZoomIntoView: () => {} },
  notify: () => {},
  closePlugin: message => { assert(!message, message); closed = true; }
};
vm.runInNewContext(fs.readFileSync(__dirname + '/code.js', 'utf8'), { figma, console });
setImmediate(() => {
  assert(closed, 'Plugin did not finish');
  assert.strictEqual(page.children.length, 1, 'Expected one added parent frame');
  const root = page.children[0];
  const screens = root.children.filter(x => x.type === 'FRAME');
  assert.strictEqual(screens.length, 6, 'Expected six screen frames');
  for (const screen of screens) {
    assert.strictEqual(screen.width, 390);
    assert.strictEqual(screen.height, 844);
    assert(screen.children.some(x => x.type === 'TEXT'), `${screen.name} has no editable text`);
    assert(screen.children.some(x => x.type === 'RECTANGLE'), `${screen.name} has no editable shapes`);
    for (const text of screen.children.filter(x => x.type === 'TEXT')) {
      assert.strictEqual(text.textAlignVertical, 'CENTER', `${text.name} uses the wrong Figma text alignment property`);
      assert(!Object.prototype.hasOwnProperty.call(text, 'textVerticalAlign'));
    }
  }
  assert.strictEqual(page.selection[0], root);
  console.log(`Verified ${screens.length} editable 390x844 frames and ${screens.reduce((sum, x) => sum + x.children.length, 0)} editable child layers.`);
});
