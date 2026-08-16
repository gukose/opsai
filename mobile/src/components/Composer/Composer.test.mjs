import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync("src/components/Composer/Composer.tsx", "utf8");
const assistantCardSource = fs.readFileSync("src/components/Assistant/AssistantCard.tsx", "utf8");
const assistantHomeSource = fs.readFileSync("src/components/Assistant/AssistantHomeScreen.tsx", "utf8");

test("composer has one outer frame without segmented control dividers", () => {
  assert.match(source, /container:\s*\{[^}]*borderWidth:\s*1,/);
  assert.doesNotMatch(source, /border(?:Left|Right)Width/);
  assert.doesNotMatch(source, /opaiControl:\s*\{[^}]*backgroundColor/);
});

test("embedded actions remain borderless and share the composer background", () => {
  assert.match(source, /sendButton:\s*\{[^}]*backgroundColor:\s*"transparent"/);
  assert.match(source, /flatIcon:\s*\{[^}]*backgroundColor:\s*"transparent"/);
});

test("assistant card and composer share one subtle surface language without connectors", () => {
  for (const componentSource of [source, assistantCardSource]) {
    assert.match(componentSource, /borderWidth:\s*1,/);
    assert.match(componentSource, /borderColor:\s*colors\.cardBorder,/);
    assert.match(componentSource, /borderRadius:\s*radius\.lg,/);
    assert.match(componentSource, /backgroundColor:\s*colors\.surface,/);
    assert.match(componentSource, /\.\.\.shadow\.soft/);
  }
  assert.match(assistantCardSource, /borderBottomColor:\s*colors\.divider,/);
  assert.match(source, /borderTopColor:\s*"#cbd5e1",/);
  assert.doesNotMatch(source + assistantCardSource, /connector|border(?:Left|Right)Width/);
});

test("phone desktop and tablet keep assistant card and composer edges aligned", () => {
  assert.match(source, /marginHorizontal:\s*5,/);
  assert.match(assistantCardSource, /marginHorizontal:\s*5,/);
  assert.match(assistantHomeSource, /composerTablet:\s*\{\s*paddingHorizontal:\s*12/);
  assert.match(assistantHomeSource, /footerDesktop:\s*\{\s*paddingHorizontal:\s*24/);
});

test("composer identifies the same assistant context as the status card", () => {
  assert.match(source, /Assistant commands/);
  assert.match(source, /contextStatusText/);
  assert.doesNotMatch(source, /connector|vertical connector/);
});

test("assistant relationship cue uses short subtle side braces without a connector line", () => {
  const homeSource = fs.readFileSync("src/components/Assistant/AssistantHomeScreen.tsx", "utf8");
  assert.match(homeSource, /assistantRelationshipCue/);
  assert.match(homeSource, /relationshipBracketLeft/);
  assert.match(homeSource, /relationshipBracketRight/);
  assert.match(homeSource, /rgba\(148, 163, 184, 0\.46\)/);
  assert.doesNotMatch(homeSource, /relationshipCue.*borderTopWidth/);
});
