import React, { useState } from 'react';
import { T } from './theme';
import { Modal, ModalActions, Label, Input, Select, Btn, HintBox } from './components';
import type { NodeRecord, RegisterRequest, ExposeRequest } from './types';
import { short } from './utils';

/* ═══ Register Node Modal ═══ */
interface RegisterProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (req: RegisterRequest) => Promise<void>;
}

export function RegisterNodeModal({ isOpen, onClose, onSubmit }: RegisterProps) {
  const [name, setName] = useState('');
  const [endpoints, setEndpoints] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    setLoading(true);
    setError('');
    try {
      await onSubmit({
        displayName: name || undefined,
        endpoints: endpoints ? endpoints.split(',').map(s => s.trim()).filter(Boolean) : [],
        capabilities: ['tunnel'],
      });
      setName(''); setEndpoints(''); onClose();
    } catch (e: any) {
      setError(e.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Register this node">
      <Label>Display name</Label>
      <Input placeholder="e.g. polaris-server" value={name} onChange={e => setName(e.target.value)} />
      <div style={{ height: 14 }} />
      <Label>WireGuard endpoints (comma-separated)</Label>
      <Input placeholder="192.168.5.104:51820, 10.0.0.1:51820" value={endpoints} onChange={e => setEndpoints(e.target.value)} />
      <HintBox>
        Register this node in the mesh ledger. Your Ed25519 public key and WireGuard identity
        are signed and appended to the blockchain. Other nodes will discover your endpoints
        and can establish encrypted tunnels peer-to-peer.
      </HintBox>
      {error && <div style={{ marginTop: 10, fontSize: 10, color: T.danger }}>{error}</div>}
      <ModalActions>
        <Btn variant="ghost" onClick={onClose}>Cancel</Btn>
        <Btn variant="primary" onClick={submit} disabled={loading}>
          {loading ? 'Submitting...' : '\u25C6 Register Node'}
        </Btn>
      </ModalActions>
    </Modal>
  );
}

/* ═══ Expose Service Modal ═══ */
interface ExposeProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (req: ExposeRequest) => Promise<void>;
  nodes: NodeRecord[];
  myNodeId: string;
}

export function ExposeServiceModal({ isOpen, onClose, onSubmit, nodes, myNodeId }: ExposeProps) {
  const [svcId, setSvcId] = useState('');
  const [protocol, setProtocol] = useState('tcp');
  const [localBind, setLocalBind] = useState('');
  const [allowed, setAllowed] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const toggleNode = (nid: string) =>
    setAllowed(prev => prev.includes(nid) ? prev.filter(n => n !== nid) : [...prev, nid]);

  const otherNodes = nodes.filter(n => n.active && n.nodeId !== myNodeId);

  const submit = async () => {
    setLoading(true);
    setError('');
    try {
      await onSubmit({ serviceId: svcId, protocol, localBind, allowedNodes: allowed });
      setSvcId(''); setLocalBind(''); setAllowed([]); onClose();
    } catch (e: any) {
      setError(e.message || 'Expose failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Expose service to mesh">
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 12 }}>
        <div>
          <Label>Service ID</Label>
          <Input placeholder="postgres-prod" value={svcId} onChange={e => setSvcId(e.target.value)} />
        </div>
        <div>
          <Label>Protocol</Label>
          <Select value={protocol} onChange={e => setProtocol(e.target.value)} style={{ width: '100%' }}>
            <option value="tcp">TCP</option>
            <option value="udp">UDP</option>
          </Select>
        </div>
      </div>
      <div style={{ height: 14 }} />
      <Label>Local bind address (where the service runs)</Label>
      <Input placeholder="127.0.0.1:5432" value={localBind} onChange={e => setLocalBind(e.target.value)} />
      <div style={{ height: 14 }} />
      <Label>Authorized peers (click to toggle)</Label>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 4, minHeight: 32 }}>
        {otherNodes.map(n => (
          <button key={n.nodeId} onClick={() => toggleNode(n.nodeId)} style={{
            padding: '5px 12px', borderRadius: 3, fontSize: 10, fontFamily: T.font, fontWeight: 500,
            border: `1px solid ${allowed.includes(n.nodeId) ? T.accent + '55' : T.border}`,
            background: allowed.includes(n.nodeId) ? T.accentBg : T.bg1,
            color: allowed.includes(n.nodeId) ? T.accent : T.textDim,
            cursor: 'pointer', transition: 'all .12s ease',
          }}>
            {allowed.includes(n.nodeId) ? '\u2713 ' : ''}{n.displayName || short(n.nodeId)}
          </button>
        ))}
        {otherNodes.length === 0 && (
          <span style={{ fontSize: 11, color: T.textDim }}>No other nodes registered yet</span>
        )}
      </div>
      <HintBox>
        Expose a local service to selected peers. Only authorized nodes can establish tunnels
        to reach it. Each connection gets its own isolated WireGuard interface and keypair.
        The authorization is recorded in the blockchain ledger and enforced cryptographically.
      </HintBox>
      {error && <div style={{ marginTop: 10, fontSize: 10, color: T.danger }}>{error}</div>}
      <ModalActions>
        <Btn variant="ghost" onClick={onClose}>Cancel</Btn>
        <Btn variant="primary" onClick={submit} disabled={loading || !svcId || !localBind}>
          {loading ? 'Submitting...' : '\u25C6 Expose Service'}
        </Btn>
      </ModalActions>
    </Modal>
  );
}
