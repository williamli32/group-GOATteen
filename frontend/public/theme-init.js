// Apply the theme before styles render, including when storage is blocked.
(() => {
  let theme;
  try {
    theme = localStorage.getItem('goatteen-theme');
  } catch {}
  if (theme !== 'light' && theme !== 'dark') {
    theme = window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  document.documentElement.setAttribute('data-theme', theme);
  document
    .querySelector('meta[name="theme-color"]')
    .setAttribute('content', theme === 'dark' ? '#101b18' : '#f5f6f3');
})();
