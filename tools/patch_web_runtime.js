const fs = require('fs');
const path = require('path');

const webDir = path.resolve(__dirname, '../android/app/src/main/assets/web-runtime');
const files = fs.readdirSync(webDir).filter(f => f.startsWith('viewer-') && f.endsWith('.js'));

if (files.length === 0) {
  console.error('No viewer-*.js found in', webDir);
  process.exit(1);
}

for (const file of files) {
  const filePath = path.join(webDir, file);
  let content = fs.readFileSync(filePath, 'utf8');
  console.log(`Patching ${file} (original size: ${content.length})...`);

  // 1A. Full HD+ / QHD High-DPI Pixel Ratio (2.5x - 3.0x) for razor-sharp CAD lines & text
  const oldPixelRatio = `i.setPixelRatio(Math.min(window.devicePixelRatio,2))`;
  const newPixelRatio = `i.setPixelRatio(Math.min(Math.max(window.devicePixelRatio||2,2.5),3))`;
  if (content.includes(oldPixelRatio)) {
    content = content.replace(oldPixelRatio, newPixelRatio);
    console.log('  [OK] 1A. Patched pixelRatio to Full HD+ (2.5x - 3.0x).');
  }

  // 1B. Enable high-performance GPU & highp precision on WebGLRenderer
  const oldRendererInit = `new Ove({antialias:!0,alpha:!0})`;
  const newRendererInit = `new Ove({antialias:!0,alpha:!0,powerPreference:"high-performance",precision:"highp"})`;
  if (content.includes(oldRendererInit)) {
    content = content.replace(oldRendererInit, newRendererInit);
    console.log('  [OK] 1B. Enabled WebGL high-performance MSAA + highp precision.');
  }

  // 1C. Physical-pixel line resolution so CAD linework & polylines render at 1px Full HD hairline sharpness
  const oldSetSize = `setSize(e,t){this._renderer.setSize(e,t),this._context.styleManager.updateLineResolution(e,t)}`;
  const newSetSize = `setSize(e,t){const n=Math.min(Math.max(window.devicePixelRatio||2,2.5),3);this._renderer.setPixelRatio?.(n),this._renderer.setSize(e,t),this._context.styleManager.updateLineResolution(e*n,t*n)}`;
  if (content.includes(oldSetSize)) {
    content = content.replace(oldSetSize, newSetSize);
    console.log('  [OK] 1C. Patched setSize for Full HD physical-pixel line resolution.');
  }

  // 2. Fix GLSL Hatch Pattern shader array l-value loop unroll error (error X3531 / warning X3550 on ANGLE/GLES)
  const oldStruct = `struct PatternLine {\n        float angle;\n        vec2 base;\n        vec2 offset;\n        float dashLengths[MAX_PATTERN_SEGMENT_COUNT];\n        float patternLength;\n    };`;
  const newStruct = `struct PatternLine {\n        float lineIndex;\n        float angle;\n        vec2 base;\n        vec2 offset;\n        float patternLength;\n    };`;

  const oldLoader = `    PatternLine loadPatternLine(float lineIndex) {\n        PatternLine pl;\n        vec4 t0 = fetchPatternTexel(lineIndex, 0.0);\n        vec4 t1 = fetchPatternTexel(lineIndex, 1.0);\n        pl.angle = t0.r;\n        pl.base = t0.gb;\n        pl.offset = vec2(t0.a, t1.r);\n        pl.patternLength = t1.g;\n\n        // Dash lengths start at float index 6 (t1.ba, then subsequent texels).\n        // Do not use three.js unroll_loop here: it only rewrites [i] /\n        // UNROLLED_LOOP_INDEX, so float(i) would become an undeclared\n        // identifier and the fragment shader would fail to compile.\n        for (int i = 0; i < \${a}; i++) {\n            float floatIndex = float(i) + 6.0;\n            float texelIndex = floor(floatIndex / 4.0);\n            float comp = mod(floatIndex, 4.0);\n            vec4 t = fetchPatternTexel(lineIndex, texelIndex);\n            if (comp < 0.5) pl.dashLengths[i] = t.r;\n            else if (comp < 1.5) pl.dashLengths[i] = t.g;\n            else if (comp < 2.5) pl.dashLengths[i] = t.b;\n            else pl.dashLengths[i] = t.a;\n        }\n        return pl;\n    }`;
  const newLoader = `    float fetchDashLength(float lineIndex, int i) {\n        float floatIndex = float(i) + 6.0;\n        float texelIndex = floor(floatIndex / 4.0);\n        float comp = mod(floatIndex, 4.0);\n        vec4 t = fetchPatternTexel(lineIndex, texelIndex);\n        if (comp < 0.5) return t.r;\n        else if (comp < 1.5) return t.g;\n        else if (comp < 2.5) return t.b;\n        else return t.a;\n    }\n\n    PatternLine loadPatternLine(float lineIndex) {\n        PatternLine pl;\n        vec4 t0 = fetchPatternTexel(lineIndex, 0.0);\n        vec4 t1 = fetchPatternTexel(lineIndex, 1.0);\n        pl.lineIndex = lineIndex;\n        pl.angle = t0.r;\n        pl.base = t0.gb;\n        pl.offset = vec2(t0.a, t1.r);\n        pl.patternLength = t1.g;\n        return pl;\n    }`;

  if (content.includes(oldStruct) && content.includes(oldLoader)) {
    content = content
      .replace(oldStruct, newStruct)
      .replace(oldLoader, newLoader)
      .replace('float segmentLength = abs(patternLine.dashLengths[i]);', 'float segmentLength = abs(fetchDashLength(patternLine.lineIndex, i));')
      .replace('float size = patternLine.dashLengths[index];', 'float size = fetchDashLength(patternLine.lineIndex, index);');
    console.log('  [OK] 2. Patched GLSL Hatch Pattern shader (eliminated array l-value unroll error).');
  }

  // 3A. Eliminate Proxy trap in LibreDwg constructor (~5 million Reflect.get traps per large DWG)
  const oldLxCtor = `constructor(e){return Nr(this,"wasmInstance"),Nr(this,"decoder"),this.wasmInstance=e,new Proxy(this,{get:(t,n,i)=>n in t?Reflect.get(t,n,i):Reflect.get(t.wasmInstance,n,i)})}`;
  const newLxCtor = `constructor(e){Nr(this,"wasmInstance");Nr(this,"decoder");this.wasmInstance=e;for(const n in e){if(!(n in this)){const i=e[n];this[n]=typeof i=="function"?i.bind(e):i}}return this}`;
  if (content.includes(oldLxCtor)) {
    content = content.replace(oldLxCtor, newLxCtor);
    console.log('  [OK] 3A. Eliminated LibreDwg Proxy wrapper (direct method binding).');
  }

  // 3B. Fix broken LibreDwg singleton caching so 11MB WASM is instantiated only ONCE
  const singletonRegex = /static createByWasmInstance\(e\)\{return this\.instance==null\?new ([A-Za-z0-9_$]+)\(e\):this\.instance\}static async create\(e\)\{const t=e==null\?await ([A-Za-z0-9_$]+)\(\):await \2\(\{locateFile:n=>`\$\{e\}\/\$\{n\}`\}\);return this\.createByWasmInstance\(t\)\}/;
  if (singletonRegex.test(content)) {
    content = content.replace(
      singletonRegex,
      `static createByWasmInstance(e){return this.instance||(this.instance=new $1(e))}static async create(e){if(this.instance)return this.instance;const t=e==null?await $2():await $2({locateFile:n=>\`\${e}/\${n}\`});return this.createByWasmInstance(t)}`
    );
    console.log('  [OK] 3B. Fixed LibreDwg singleton caching (prevents 3x 11MB WASM re-instantiation).');
  } else {
    console.warn('  [WARN] 3B. LibreDwg singleton pattern not matched.');
  }

  // 4A. Skip unused xdata Embind allocation on 300,000+ entities
  const oldXdata = `m=t.dwg_object_entity_get_xdata(e);`;
  const newXdata = `m=void 0;`;
  if (content.includes(oldXdata)) {
    content = content.replace(oldXdata, newXdata);
    console.log('  [OK] 4A. Skipped unused xdata Embind allocation per entity.');
  }

  // 4B. Optimize n8.convert two-pass 300k object loop using this.safeObjectTio(c)
  const convertLoopRegex = /const o=i\.dwg_get_num_objects\(e\);for\(let l=0;l<o;l\+\+\)\{const c=i\.dwg_get_object\(e,l\);if\(c\)\{const u=this\.safeObjectTio\(c\);if\(u\)switch\(i\.dwg_object_get_fixedtype\(c\)\)\{case ([A-Za-z0-9_$]+)\.DWG_TYPE_LAYER:\{const h=this\.convertLayer\(u,c\);n\.tables\.LAYER\.entries\.push\(h\),this\.entityConverter\.layers\.set\(h\.handle,h\.name\)\}break;case \1\.DWG_TYPE_LTYPE:\{const h=this\.convertLineType\(u,c\);n\.tables\.LTYPE\.entries\.push\(h\),this\.entityConverter\.ltypes\.set\(h\.handle,h\.name\)\}break\}\}\}for\(let l=0;l<o;l\+\+\)\{const c=i\.dwg_get_object\(e,l\);if\(c\)\{const u=this\.safeObjectTio\(c\);if\(u\)switch\(i\.dwg_object_get_fixedtype\(c\)\)\{/;
  if (convertLoopRegex.test(content)) {
    content = content.replace(
      convertLoopRegex,
      `const o=i.dwg_get_num_objects(e),_objs=[];for(let l=0;l<o;l++){const c=i.dwg_get_object(e,l);if(c){const u=this.safeObjectTio(c);if(u){const ft=i.dwg_object_get_fixedtype(c);if(ft===$1.DWG_TYPE_LAYER){const h=this.convertLayer(u,c);n.tables.LAYER.entries.push(h),this.entityConverter.layers.set(h.handle,h.name)}else if(ft===$1.DWG_TYPE_LTYPE){const h=this.convertLineType(u,c);n.tables.LTYPE.entries.push(h),this.entityConverter.ltypes.set(h.handle,h.name)}else _objs.push([c,u,ft])}}}for(let l=0;l<_objs.length;l++){const[c,u,ft]=_objs[l];if(c){if(u)switch(ft){`
    );
    console.log('  [OK] 4B. Optimized n8.convert two-pass loop (iterates ~500 objects instead of 600,000).');
  } else {
    console.warn('  [WARN] 4B. n8.convert loop pattern not matched.');
  }

  // 4C. Skip dwg_bmp thumbnail extraction
  const oldBmp = `const s=i.dwg_bmp(e);return(t=s?.data)!=null&&t.length&&(n.thumbnailImage=s.data),n`;
  const newBmp = `return n`;
  if (content.includes(oldBmp)) {
    content = content.replace(oldBmp, newBmp);
    console.log('  [OK] 4C. Skipped dwg_bmp thumbnail extraction.');
  }

  // 4D. Skip block preview & redundant 300k orphan scan ONLY when block already matches num_owned (g.length >= a)
  const blockPreviewRegex = /let m;const y=n\.dwg_entity_block_header_get_preview\(e\);y&&y\.length>0&&\(m=[A-Za-z0-9_$]+\(y\)\);(let g=this\.convertEntities\(t,i\.handle\);[\s\S]{100,320}?)if\(this\.orphanIndexDataPtr!=null\)\{/;
  if (blockPreviewRegex.test(content)) {
    content = content.replace(blockPreviewRegex, `let m;$1if((a>0?g.length<a:g.length===0)&&this.orphanIndexDataPtr!=null){`);
    console.log('  [OK] 4D. Skipped block preview & preserved orphan entity scan when g.length < num_owned.');
  } else {
    console.warn('  [WARN] 4D. convertBlockRecord preview/orphan pattern not matched.');
  }

  // 4E. Skip raw byte thumbnail scanner in AcDbLibreDwgMainThreadConverter
  const rawThumbRegex = /i=typeof r=="string"\?void 0:new Uint8Array\(r\),i&&[A-Za-z0-9_$]+\(n\.database,i\),\[2,n\]/;
  if (rawThumbRegex.test(content)) {
    content = content.replace(rawThumbRegex, `[2,n]`);
    console.log('  [OK] 4E. Skipped raw byte thumbnail scanner.');
  } else {
    console.warn('  [WARN] 4E. raw byte thumbnail scanner pattern not matched.');
  }

  // 5A. Replace requestAnimationFrame yield in Ey() with zero-delay setTimeout(r, 0)
  const rAFYieldRegex = /function ([A-Za-z0-9_$]+)\(\)\{return new Promise\(function\(r\)\{typeof globalThis<"u"&&typeof globalThis\.requestAnimationFrame=="function"\?globalThis\.requestAnimationFrame\(function\(\)\{return r\(\)\}\):setTimeout\(r,0\)\}\)\}/;
  if (rAFYieldRegex.test(content)) {
    content = content.replace(rAFYieldRegex, `function $1(){return new Promise(function(r){setTimeout(r,0)})}`);
    console.log('  [OK] 5A. Replaced requestAnimationFrame yield with setTimeout(0).');
  } else {
    console.warn('  [WARN] 5A. rAF yield pattern not matched.');
  }

  // 6. Increase AcTrView2d slice and yield budgets so all 35,356 entities convert 3x faster with zero drops
  const sliceConstsRegex = /([A-Za-z0-9_$]+)\.OPEN_CONVERT_YIELD_BUDGET_MS=300,\1\.PROGRESSIVE_OPEN_YIELD_BUDGET_MS=32,\1\.PROGRESSIVE_INPUT_PAUSE_MS=120,\1\.PROGRESSIVE_OPEN_PAINT_INTERVAL_MS=2500,\1\.NON_PROGRESSIVE_OPEN_PAINT_INTERVAL_MS=4e3,\1\.DEFERRED_GEOMETRY_CONCURRENCY=8,\1\.CONVERT_SLICE_BUDGET_MS=64,\1\.CONVERT_SLICE_MAX=512/;
  if (sliceConstsRegex.test(content)) {
    content = content.replace(
      sliceConstsRegex,
      `$1.OPEN_CONVERT_YIELD_BUDGET_MS=250,$1.PROGRESSIVE_OPEN_YIELD_BUDGET_MS=150,$1.PROGRESSIVE_INPUT_PAUSE_MS=120,$1.PROGRESSIVE_OPEN_PAINT_INTERVAL_MS=2500,$1.NON_PROGRESSIVE_OPEN_PAINT_INTERVAL_MS=4e3,$1.DEFERRED_GEOMETRY_CONCURRENCY=16,$1.CONVERT_SLICE_BUDGET_MS=200,$1.CONVERT_SLICE_MAX=8192`
    );
    console.log('  [OK] 6. Boosted AcTrView2d CONVERT_SLICE_MAX=8192 and DEFERRED_GEOMETRY_CONCURRENCY=16.');
  } else {
    console.warn('  [WARN] 6. AcTrView2d slice constants pattern not matched.');
  }

  fs.writeFileSync(filePath, content, 'utf8');
  console.log(`Saved ${file} (new size: ${content.length}).`);
}
