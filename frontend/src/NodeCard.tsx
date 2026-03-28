import React, { useState } from 'react';
import { T } from './theme';
import { Card, Badge, Indicator, InfoRow, Btn } from './components';
import type { NodeRecord } from './types';
import { short, ago } from './utils';

interface Props {
  node: NodeRecord;
  onRemove: (nodeId: string) => void;
  onRegister: (nodeId: string) => void;
  isSelf: boolean;
}

export function NodeCard({ node, onRemove, onRegister, isSelf }: Props) {
  const [confirmRemove, setConfirmRemove] = useState(false);

  const borderColor = !node.registered ? T.warn : node.online ? T.accent : T.danger;

  return (
    <Card style={{ borderLeft: `2px solid ${borderColor}40` }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: T.textHi, fontFamily: T.display, marginBottom: 2 }}>
            {node.displayName || 'Unnamed node'}
            {isSelf && <span style={{ fontSize: 9, color: T.accent, marginLeft: 6, fontFamily: T.font }}>[THIS NODE]</span>}
          </div>
          <div style={{ fontSize: 10, color: T.textDim, fontFamily: T.font }}>{short(node.nodeId)}</div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
          <Badge color={node.online ? T.accent : T.danger}>
            <Indicator color={node.online ? T.accent : T.danger} pulse={node.online}/>
            {node.online ? 'ONLINE' : 'OFFLINE'}
          </Badge>
          {!node.registered && (
            <Badge color={T.warn}>DISCOVERED</Badge>
          )}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 10 }}>
        <InfoRow label="Endpoints" value={node.endpoints?.join(', ') || '—'} />
        <InfoRow label="Capabilities" value={node.capabilities?.join(', ') || '—'} />
        <InfoRow label="WG pubkey" value={short(node.wireguardPubkey)} />
        <InfoRow label="Registered" value={node.registered ? ago(node.registeredAt) : '—'} />
      </div>

      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
        {!node.registered && isSelf && (
          <Btn small variant="primary" onClick={() => onRegister(node.nodeId)}>
            Register
          </Btn>
        )}
        {node.registered && (
          confirmRemove ? (
            <>
              <Btn small variant="ghost" onClick={() => setConfirmRemove(false)}>Cancel</Btn>
              <Btn small variant="danger" onClick={() => { onRemove(node.nodeId); setConfirmRemove(false); }}>
                Confirm Remove
              </Btn>
            </>
          ) : (
            <Btn small variant="danger" onClick={() => setConfirmRemove(true)}>Remove</Btn>
          )
        )}
      </div>
    </Card>
  );
}