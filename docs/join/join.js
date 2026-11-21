const OCTET = '(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)';
const PORT = '([1-9]\\d{0,4})';
const ADDRESS = new RegExp(`^#${OCTET}\\.${OCTET}\\.${OCTET}\\.${OCTET}:${PORT}$`);

const isPrivate = (a, b) => a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);

export function tvAddress(hash) {
  const m = ADDRESS.exec(hash || '');
  if (!m) return null;
  const [a, b, c, d, port] = m.slice(1).map(Number);
  if (!isPrivate(a, b) || port > 65535) return null;
  return `${a}.${b}.${c}.${d}:${port}`;
}

export function asksFirst(ua) {
  return /Android/.test(ua) && /Chrome\/\d/.test(ua) && !/SamsungBrowser|Firefox/.test(ua);
}

async function probeTv(url) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 35_000);
  try {
    await fetch(url, { mode: 'no-cors', cache: 'no-store', targetAddressSpace: 'local', signal: ctrl.signal });
    return true;
  } catch {
    return false;
  } finally {
    clearTimeout(t);
  }
}

if (typeof document !== 'undefined') {
  const show = (view, url) => {
    document.querySelectorAll('[data-view]').forEach((s) => { s.hidden = s.dataset.view !== view; });
    const a = document.getElementById('anyway');
    if (url && a) a.href = url;
  };
  const go = async () => {
    const addr = tvAddress(location.hash);
    if (!addr) { show('no-tv'); return; }
    const url = `http://${addr}/`;
    if (!asksFirst(navigator.userAgent)) { location.replace(url); return; }
    show('asking', url);
    if (await probeTv(url)) location.replace(url);
    else show('blocked', url);
  };
  document.getElementById('again')?.addEventListener('click', go);
  go();
}
