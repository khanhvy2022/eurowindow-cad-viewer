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

  // 1. Cap WebGL pixel ratio to 1.25 on mobile to avoid 23 Megapixels VRAM overload
  const oldPixelRatio = `i.setPixelRatio(Math.min(window.devicePixelRatio,2))`;
  const newPixelRatio = `i.setPixelRatio(Math.min(window.devicePixelRatio||1,1.25))`;
  if (content.includes(oldPixelRatio)) {
    content = content.replace(oldPixelRatio, newPixelRatio);
    console.log('  [OK] Patched pixelRatio cap to 1.25.');
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
    console.log('  [OK] Patched GLSL Hatch Pattern shader (eliminated array l-value unroll error).');
  } else {
    console.warn('  [WARN] Hatch Pattern shader pattern not matched.');
  }

  fs.writeFileSync(filePath, content, 'utf8');
  console.log(`Saved ${file} (new size: ${content.length}).`);
}
