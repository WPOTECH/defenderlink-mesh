export const T = {
  bg0: '#060a0e',
  bg1: '#0a1014',
  bg2: '#0e161c',
  bg3: '#131f28',
  border: '#1a2a20',
  borderHi: '#1f3a28',
  text: '#8fa89a',
  textHi: '#c8e0d0',
  textDim: '#4a6a55',
  accent: '#00ff88',
  accentDim: '#00cc6a',
  accentBg: '#00ff8808',
  danger: '#ff4444',
  dangerBg: '#ff444410',
  warn: '#ffaa22',
  warnBg: '#ffaa2210',
  info: '#44aaff',
  infoBg: '#44aaff10',
  purple: '#aa66ff',
  font: "'IBM Plex Mono', monospace",
  display: "'Oxanium', sans-serif",
} as const;

export const GLOBAL_CSS = `
*{margin:0;padding:0;box-sizing:border-box}
body{margin:0;background:${T.bg0};overflow-x:hidden}
::-webkit-scrollbar{width:5px}
::-webkit-scrollbar-track{background:${T.bg0}}
::-webkit-scrollbar-thumb{background:#1a2a1a;border-radius:3px}
input:focus,select:focus{outline:none;border-color:#00ff8855!important;box-shadow:0 0 0 1px #00ff8822}
button{cursor:pointer;transition:all .12s ease}
button:hover{filter:brightness(1.2)}
button:active{transform:scale(0.97)}
@keyframes fadeIn{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:translateY(0)}}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.5}}
`;
