// Official Material Web components (https://github.com/material-components/material-web),
// bundled locally into material-web.js (Apache-2.0, licences kept at the end of the file).
import './material-web.js';

/* ---------------- i18n ---------------- */
const EN = {
  'nav.features': 'Features', 'nav.install': 'Install', 'nav.links': 'Links',
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
  'f3.title': 'Store theme', 'f3.soon': 'soon',
  'f3.text': 'Store and news run in a WebView. They load CSS from this site, so the theme can be updated without rebuilding the app.',
  'palette.title': 'Palette',
  'palette.text': 'Left: the original colour of the app theme. Right: the colour after the patch.',
  'warn.title': 'Save your Steam Guard recovery code before installing',
  'warn.text': 'The patched app is signed with a different key, so the official one must be uninstalled, and its Steam Guard data goes with it. The recovery code (R-xxxxx) is in Steam → Steam Guard.',
  'install.title': 'Installation', 'install.req': 'You will need:',
  'install.fonts': 'and static TTF fonts (for example',
  's1': 'Install dependencies', 's2': 'Pull all app APKs (base and splits)', 's3': 'Patch base.apk',
  's4': 'Sign all APKs with one key', 's5': 'Replace the official app',
  'install.after': 'After installing, sign in and set up Steam Guard again. To roll back at any time, uninstall the patched app and install Steam from Google Play.',
  'limits.title': 'Limitations',
  'l1': 'Tested on Steam 3.10.11 (Hermes v96)',
  'l2': 'Colours and fonts change; the layout stays the same',
  'l3': 'Re-apply the patch after every Steam update',
  'links.title': 'Links', 'ln1.t': 'Repository', 'ln2.t': 'Report a problem',
  'ln2.s': 'Attach a log: adb logcat -d',
  'ln3.s': 'Millennium theme the store theme is based on (MIT)',
  'ln4.s': 'Hermes bytecode parser',
  'footer': 'steamd is an unofficial project, not affiliated with Valve Corporation. Steam is a trademark of Valve Corporation. The script only modifies your own copy of the app. Do not distribute patched APKs.',
};
const TITLES = { ru: 'steamd — Material 3 для Steam на Android', en: 'steamd — Material 3 for Steam on Android' };

// Russian is the source text in the HTML; remember it once.
const RU = {};
const textNodes = document.querySelectorAll('[data-i18n]');
textNodes.forEach((el) => {
  RU[el.dataset.i18n] = el.hasAttribute('label') ? el.getAttribute('label') : el.textContent.trim();
});
document.querySelectorAll('[data-i18n-label]').forEach((el) => {
  RU[el.dataset.i18nLabel] = el.getAttribute('aria-label');
});

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
  renderPalette(lang);
  try { localStorage.setItem('steamd-lang', lang); } catch (e) {}
}

/* ---------------- theme ---------------- */
const THEME_ICON = { light: 'light_mode', dark: 'dark_mode', system: 'brightness_auto' };
function setTheme(t) {
  document.documentElement.dataset.theme = t;
  document.getElementById('theme-icon').textContent = THEME_ICON[t];
  try { localStorage.setItem('steamd-theme', t); } catch (e) {}
}

/* ---------------- palette (mirrors PALETTE in patch_steam.py) ---------------- */
const PALETTE = [
  ['#2A2C34', '#26272B', 'Фон', 'Surface'],
  ['#1F2127', '#1B1B1F', 'Фон: глубокий', 'Surface: deep'],
  ['#23252A', '#1F1F23', 'Фон: третичный', 'Surface: tertiary'],
  ['#3C3F49', '#3E4759', 'Нейтральный', 'Neutral'],
  ['#1A9FFF', '#8AB4F8', 'Акцент', 'Accent'],
  ['#DFE3E6', '#E3E2E6', 'Текст', 'Text'],
  ['#8B929A', '#C4C6D0', 'Вторичный текст', 'Secondary text'],
  ['#60636A', '#8E9099', 'Разделитель', 'Divider'],
];
function renderPalette(lang) {
  const box = document.getElementById('palette');
  box.replaceChildren(...PALETTE.map(([from, to, ru, en]) => {
    const d = document.createElement('div');
    d.className = 'swatch';
    d.innerHTML = `<span class="chip" style="background:${from}"></span><md-icon>arrow_forward</md-icon>`
      + `<span class="chip" style="background:${to}"></span><span>${to}</span><span class="name">${lang === 'en' ? en : ru}</span>`;
    d.title = `${from} → ${to}`;
    return d;
  }));
}

/* ---------------- menus ---------------- */
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

/* ---------------- demo code in the illustration ---------------- */
const ALPHABET = '23456789BCDFGHJKMNPQRTVWXY';
setInterval(() => {
  document.getElementById('demo-code').textContent =
    Array.from({ length: 5 }, () => ALPHABET[Math.floor(Math.random() * ALPHABET.length)]).join('');
}, 30000);

setTheme(document.documentElement.dataset.theme || 'system');
setLang(document.documentElement.lang === 'en' ? 'en' : 'ru');
