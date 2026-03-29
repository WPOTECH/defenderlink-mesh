import React, { useEffect, type CSSProperties, type ReactNode } from 'react';
import { T } from './theme';

/* ═══ Indicator dot ═══ */
export function Indicator({ color, pulse = false }: { color: string; pulse?: boolean }) {
  return <span style={{
    display: 'inline-block', width: 7, height: 7, borderRadius: '50%',
    background: color, boxShadow: `0 0 6px ${color}55`,
    animation: pulse ? 'pulse 2s ease infinite' : 'none',
  }} />;
}

/* ═══ Badge ═══ */
export function Badge({ children, color = T.accent }: { children: ReactNode; color?: string }) {
  return <span style={{
    display: 'inline-flex', alignItems: 'center', gap: 4, padding: '2px 8px', borderRadius: 3,
    fontSize: 10, fontWeight: 600, letterSpacing: 0.8, fontFamily: T.font,
    color, background: color + '15', border: `1px solid ${color}25`,
  }}>{children}</span>;
}

/* ═══ Card ═══ */
export function Card({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  return <div style={{
    background: T.bg2, border: `1px solid ${T.border}`, borderRadius: 6,
    padding: 16, animation: 'fadeIn .3s ease', ...style,
  }}>{children}</div>;
}

/* ═══ Stat box ═══ */
export function StatBox({ label, value, accent, icon }: {
  label: string; value: string | number; accent?: string; icon?: string;
}) {
  return (
    <div style={{
      background: T.bg1, border: `1px solid ${T.border}`, borderRadius: 6,
      padding: '14px 16px', borderLeft: `2px solid ${accent || T.accent}`,
    }}>
      <div style={{
        fontSize: 10, color: T.textDim, textTransform: 'uppercase', letterSpacing: 1.5,
        fontFamily: T.font, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 6,
      }}>
        {icon && <span style={{ fontSize: 12 }}>{icon}</span>}{label}
      </div>
      <div style={{
        fontSize: 26, fontWeight: 700, color: accent || T.accent,
        fontFamily: T.display, fontVariantNumeric: 'tabular-nums',
      }}>{value}</div>
    </div>
  );
}

/* ═══ Section header ═══ */
export function SectionHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      marginBottom: 14, paddingBottom: 8, borderBottom: `1px solid ${T.border}`,
    }}>
      <span style={{
        fontSize: 11, textTransform: 'uppercase', letterSpacing: 2.5,
        color: T.textDim, fontWeight: 600, fontFamily: T.font,
      }}>{title}</span>
      <div style={{ display: 'flex', gap: 8 }}>{children}</div>
    </div>
  );
}

/* ═══ Button ═══ */
type BtnVariant = 'primary' | 'danger' | 'ghost' | 'default';
export function Btn({ children, variant = 'default', small, onClick, disabled, style: sx }: {
  children: ReactNode; variant?: BtnVariant; small?: boolean;
  onClick?: () => void; disabled?: boolean; style?: CSSProperties;
}) {
  const base: CSSProperties = {
    padding: small ? '4px 10px' : '7px 14px', borderRadius: 4, border: 'none',
    fontSize: small ? 10 : 11, fontWeight: 600, fontFamily: T.font,
    letterSpacing: 0.4, opacity: disabled ? 0.5 : 1,
  };
  const variants: Record<BtnVariant, CSSProperties> = {
    primary: { ...base, background: T.accent, color: '#000' },
    danger: { ...base, background: 'transparent', color: T.danger, border: `1px solid ${T.danger}30` },
    ghost: { ...base, background: 'transparent', color: T.text, border: `1px solid ${T.border}` },
    default: { ...base, background: T.bg3, color: T.text },
  };
  return <button style={{ ...variants[variant], ...sx }} onClick={onClick} disabled={disabled}>{children}</button>;
}

/* ═══ Input ═══ */
export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} style={{
    width: '100%', padding: '9px 12px', borderRadius: 4, border: `1px solid ${T.border}`,
    background: T.bg1, color: T.textHi, fontSize: 13, fontFamily: T.font,
    boxSizing: 'border-box' as const, transition: 'border-color .15s', ...(props.style || {}),
  }} />;
}

/* ═══ Select ═══ */
export function Select({ children, ...props }: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} style={{
    padding: '9px 12px', borderRadius: 4, border: `1px solid ${T.border}`,
    background: T.bg1, color: T.textHi, fontSize: 13, fontFamily: T.font,
    ...(props.style || {}),
  }}>{children}</select>;
}

/* ═══ Label ═══ */
export function Label({ children }: { children: ReactNode }) {
  return <label style={{
    display: 'block', fontSize: 10, color: T.textDim, textTransform: 'uppercase',
    letterSpacing: 1, marginBottom: 6, fontFamily: T.font, fontWeight: 500,
  }}>{children}</label>;
}

/* ═══ InfoRow ═══ */
export function InfoRow({ label, value }: { label: string; value: string | number | null | undefined }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      <span style={{ fontSize: 9, color: T.textDim, textTransform: 'uppercase', letterSpacing: 1, fontFamily: T.font }}>{label}</span>
      <span style={{ fontSize: 12, color: T.text, fontFamily: T.font, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{value ?? '\u2014'}</span>
    </div>
  );
}

/* ═══ Hint box ═══ */
export function HintBox({ children }: { children: ReactNode }) {
  return <div style={{
    padding: '10px 14px', borderRadius: 4, background: T.accentBg,
    border: `1px solid ${T.accent}15`, fontSize: 10, color: T.accentDim,
    lineHeight: 1.6, marginTop: 14, fontFamily: T.font,
  }}>{children}</div>;
}

/* ═══ Empty state ═══ */
export function Empty({ text }: { text: string }) {
  return (
    <div style={{ textAlign: 'center', padding: '40px 20px', color: T.textDim }}>
      <div style={{ fontSize: 28, opacity: 0.15, marginBottom: 8 }}>{'\u25C6'}</div>
      <div style={{ fontSize: 11 }}>{text}</div>
    </div>
  );
}

/* ═══ Toast ═══ */
export function Toast({ message, type = 'success', onDismiss }: {
  message: string; type?: 'success' | 'error'; onDismiss: () => void;
}) {
  useEffect(() => { const t = setTimeout(onDismiss, 60000); return () => clearTimeout(t); }, [onDismiss]);
  const color = type === 'error' ? T.danger : T.accent;
  return <div style={{
    position: 'fixed', bottom: 20, right: 20, padding: '10px 18px', borderRadius: 4,
    fontSize: 11, fontFamily: T.font, fontWeight: 500, zIndex: 2000,
    animation: 'fadeIn .2s ease',
    background: type === 'error' ? T.dangerBg : T.accentBg,
    border: `1px solid ${color}30`, color,
  }}>{message}</div>;
}

/* ═══ Modal ═══ */
export function Modal({ isOpen, onClose, title, children }: {
  isOpen: boolean; onClose: () => void; title: string; children: ReactNode;
}) {
  if (!isOpen) return null;
  return (
    <div onClick={onClose} style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,.75)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      zIndex: 1000, backdropFilter: 'blur(4px)',
    }}>
      <div onClick={e => e.stopPropagation()} style={{
        background: T.bg2, border: `1px solid ${T.borderHi}`, borderRadius: 8,
        padding: 28, width: 520, maxWidth: '92vw', animation: 'fadeIn .2s ease',
      }}>
        <div style={{
          fontSize: 15, fontWeight: 700, color: T.textHi, fontFamily: T.display,
          marginBottom: 20, display: 'flex', alignItems: 'center', gap: 8,
        }}>
          <span style={{ color: T.accent }}>{'\u25C6'}</span>{title}
        </div>
        {children}
      </div>
    </div>
  );
}

/* ═══ ModalActions ═══ */
export function ModalActions({ children }: { children: ReactNode }) {
  return <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>{children}</div>;
}

/* ═══ Scanline overlay ═══ */
export function ScanlineOverlay() {
  return <div style={{
    position: 'fixed', inset: 0, pointerEvents: 'none', zIndex: 9999, opacity: 0.025,
    background: 'repeating-linear-gradient(0deg,transparent,transparent 2px,rgba(0,255,136,0.1) 2px,rgba(0,255,136,0.1) 4px)',
  }} />;
}
