import './material-web.js';
import { argbFromHex, hexFromArgb, Hct, SchemeTonalSpot, MaterialDynamicColors } from './color-utils.js';

const EN = {
  'nav.features': 'Features', 'nav.color': 'Colour', 'nav.install': 'Install', 'a11y.copy': 'Copy', 'nav.links': 'Links',
  'a11y.lang': 'Language', 'a11y.theme': 'Theme',
  'theme.light': 'Light', 'theme.dark': 'Dark', 'theme.system': 'System',
  'chip.unofficial': 'Unofficial project',
  'hero.title': 'Material 3 for Steam on Android',
  'hero.lead': 'steamd is a patch that recolours the official Steam app with a calm Material 3 palette, swaps the font and adds a custom theme for the store and news.',
  'hero.install': 'How to install', 'hero.source': 'Source code',
  'art.account': 'This account', 'art.copy': 'Tap to copy code', 'art.scan': 'Scan QR code',
  'art.news': 'News', 'art.shop': 'Shop',
  'features.title': 'What the patch changes',
  'f1.title': 'M3 palette',
  'f1.text': 'The app’s built-in theme gets dark tonal surfaces and a soft blue accent: menu, Steam Guard, navigation and headers.',
  'f2.title': 'Your font',
  'f2.text': 'Motiva Sans is replaced with any font that has the needed styles, e.g. Roboto. Weight and italics are matched automatically.',
  'f3.title': 'Store and news theme',
  'f3.text': 'Store and news run in a WebView. They load CSS from this site, so the theme can be updated without rebuilding the app.',
  'color.title': 'Colour',
  'color.text': 'Pick a seed colour: the whole Material You palette is generated from it, just like on Android 12+. The site recolours instantly and the patch command appears below. Use your wallpaper colour to match Steam to your system.',
  'seed.default': 'Default', 'seed.purple': 'Purple', 'seed.green': 'Green', 'seed.orange': 'Orange',
  'seed.pink': 'Pink', 'seed.teal': 'Teal', 'seed.custom': 'Custom colour',
  'color.dynamic': 'Fully automatic dynamic colours that follow your wallpaper need an app on the device. That is the next stage of the project.',
  'warn.title': 'Save your Steam Guard recovery code before installing',
  'warn.text': 'The patched app is signed with a different key, so the official one must be uninstalled, and its Steam Guard data goes with it. The recovery code (R-xxxxx) is in Steam → Steam Guard.',
  'install.title': 'Installation', 'install.req': 'You will need:',
  'install.fonts': 'and static TTF fonts (for example',
  's1': 'Install dependencies', 's2': 'Pull all app APKs (base and splits)', 's3': 'Patch base.apk (custom colour: see the Colour section)',
  's4': 'Sign all APKs with one key', 's5': 'Replace the official app',
  'install.after': 'After installing, sign in and set up Steam Guard again. To roll back at any time, uninstall the patched app and install Steam from Google Play.',
  'limits.title': 'Limitations',
  'l1': 'Tested on Steam 3.10.11 (Hermes v96)',
  'l2': 'Colours and fonts change; the layout stays the same',
  'l3': 'Re-apply the patch after every Steam update',
  'links.title': 'Links', 'ln1.t': 'Repository', 'ln2.t': 'Report a problem',
  'ln2.s': 'Attach a log: adb logcat -d',
  'ln3.s': 'Millennium theme that inspired the store theme (MIT)',
  'ln4.s': 'Hermes bytecode parser',
  'footer': 'steamd is an unofficial project, not affiliated with Valve Corporation. Steam is a trademark of Valve Corporation. The script only modifies your own copy of the app. Do not distribute patched APKs.',
};
const TITLES = { ru: 'steamd — Material 3 для Steam на Android', en: 'steamd — Material 3 for Steam on Android' };

const RU = {};
const textNodes = document.querySelectorAll('[data-i18n]');
textNodes.forEach((el) => {
  RU[el.dataset.i18n] = el.hasAttribute('label') ? el.getAttribute('label') : el.textContent.trim();
});
document.querySelectorAll('[data-i18n-label]').forEach((el) => {
  RU[el.dataset.i18nLabel] = el.getAttribute('aria-label');
});

function setPaletteLang(l) { lang = l; if (document.getElementById('palette')) renderPalette(); }
function setLang(lang) {
  const dict = lang === 'en' ? EN : RU;
  textNodes.forEach((el) => {
    const v = dict[el.dataset.i18n] ?? RU[el.dataset.i18n];
    if (el.hasAttribute('label')) el.setAttribute('label', v); else el.textContent = v;
  });
  document.querySelectorAll('[data-i18n-label]').forEach((el) => {
    el.setAttribute('aria-label', dict[el.dataset.i18nLabel] ?? RU[el.dataset.i18nLabel]);
  });
  document.documentElement.lang = lang;
  document.title = TITLES[lang];
  setPaletteLang(lang);
  try { localStorage.setItem('steamd-lang', lang); } catch (e) {}
}

const THEME_ICON = { light: 'light_mode', dark: 'dark_mode', system: 'brightness_auto' };
function setTheme(t) {
  document.documentElement.dataset.theme = t;
  document.getElementById('theme-icon').textContent = THEME_ICON[t];
  try { localStorage.setItem('steamd-theme', t); } catch (e) {}
}

const ROWS = [
  ['#2A2C34', '#26272B', 'surfaceContainer', 'Фон', 'Surface'],
  ['#1F2127', '#1B1B1F', 'surface', 'Фон: глубокий', 'Surface: deep'],
  ['#23252A', '#1F1F23', 'surfaceContainerLow', 'Фон: третичный', 'Surface: tertiary'],
  ['#3C3F49', '#3E4759', 'secondaryContainer', 'Нейтральный', 'Neutral'],
  ['#1A9FFF', '#8AB4F8', 'primary', 'Акцент', 'Accent'],
  ['#DFE3E6', '#E3E2E6', 'onSurface', 'Текст', 'Text'],
  ['#8B929A', '#C4C6D0', 'onSurfaceVariant', 'Вторичный текст', 'Secondary text'],
  ['#60636A', '#8E9099', 'outline', 'Разделитель', 'Divider'],
];

const TOKENS = ['primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer', 'secondary', 'onSecondary',
  'secondaryContainer', 'onSecondaryContainer', 'tertiary', 'tertiaryContainer', 'onTertiaryContainer', 'error',
  'errorContainer', 'onErrorContainer', 'background', 'onBackground', 'surface', 'onSurface', 'surfaceVariant',
  'onSurfaceVariant', 'outline', 'outlineVariant', 'surfaceContainerLowest', 'surfaceContainerLow',
  'surfaceContainer', 'surfaceContainerHigh', 'surfaceContainerHighest', 'inverseSurface', 'inverseOnSurface'];
const kebab = (n) => n.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase());
let seed = '';
let lang = 'ru';

function scheme(hex, dark) {
  return new SchemeTonalSpot(Hct.fromInt(argbFromHex(hex)), dark, 0);
}
function role(sch, name) {
  return hexFromArgb(MaterialDynamicColors[name].getArgb(sch)).toUpperCase();
}

function applySeed(hex) {
  seed = hex;
  let style = document.getElementById('seed-style');
  if (!style) { style = document.createElement('style'); style.id = 'seed-style'; document.head.appendChild(style); }
  if (!hex) {
    style.textContent = '';
  } else {
    const css = (sch) => TOKENS.map((t) => `--md-sys-color-${kebab(t)}:${role(sch, t)};`).join('');
    const light = css(scheme(hex, false)), dark = css(scheme(hex, true));
    style.textContent = `:root[data-theme="light"]{${light}}:root[data-theme="dark"]{${dark}}`
      + `@media (prefers-color-scheme: light){:root[data-theme="system"]{${light}}}`
      + `@media (prefers-color-scheme: dark){:root[data-theme="system"]{${dark}}}`;
  }
  document.querySelectorAll('#seed-chips md-filter-chip').forEach((c) => { c.selected = c.dataset.seed === hex; });
  if (hex) document.getElementById('seed-input').value = hex.toLowerCase();
  document.getElementById('seed-cmd').textContent =
    'python patch/patch_steam.py apk/base.apk out/base.apk --fonts fonts/' + (hex ? ` --seed "${hex}"` : '');
  renderPalette();
  try { localStorage.setItem('steamd-seed', hex); } catch (e) {}
}

function renderPalette() {
  const sch = seed ? scheme(seed, true) : null;
  const box = document.getElementById('palette');
  box.replaceChildren(...ROWS.map(([from, def, r, ru, en]) => {
    const to = sch ? role(sch, r) : def;
    const d = document.createElement('div');
    d.className = 'swatch';
    d.innerHTML = `<span class="chip" style="background:${from}"></span><md-icon>arrow_forward</md-icon>`
      + `<span class="chip" style="background:${to}"></span><span>${to}</span><span class="name">${lang === 'en' ? en : ru}</span>`;
    d.title = `${from} → ${to}`;
    return d;
  }));
}

document.getElementById('seed-chips').addEventListener('click', (e) => {
  const chip = e.target.closest('md-filter-chip');
  if (chip) applySeed(chip.dataset.seed);
});
document.getElementById('seed-input').addEventListener('input', (e) => applySeed(e.target.value.toUpperCase()));
document.getElementById('copy-cmd').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(document.getElementById('seed-cmd').textContent); } catch (e) {}
  const icon = document.querySelector('#copy-cmd md-icon');
  icon.textContent = 'check'; setTimeout(() => { icon.textContent = 'content_copy'; }, 1500);
});

function bindMenu(btnId, menuId) {
  const btn = document.getElementById(btnId), menu = document.getElementById(menuId);
  btn.addEventListener('click', () => { menu.open = !menu.open; });
  return menu;
}
bindMenu('lang-btn', 'lang-menu').addEventListener('close-menu', (e) => {
  const lang = e.detail.initiator?.dataset.lang; if (lang) setLang(lang);
});
bindMenu('theme-btn', 'theme-menu').addEventListener('close-menu', (e) => {
  const t = e.detail.initiator?.dataset.themeValue; if (t) setTheme(t);
});

const ALPHABET = '23456789BCDFGHJKMNPQRTVWXY';
setInterval(() => {
  document.getElementById('demo-code').textContent =
    Array.from({ length: 5 }, () => ALPHABET[Math.floor(Math.random() * ALPHABET.length)]).join('');
}, 30000);

setTheme(document.documentElement.dataset.theme || 'system');
let savedSeed = '';
try { savedSeed = localStorage.getItem('steamd-seed') || ''; } catch (e) {}
applySeed(/^#[0-9A-F]{6}$/i.test(savedSeed) ? savedSeed.toUpperCase() : '');
setLang(document.documentElement.lang === 'en' ? 'en' : 'ru');
