import React, { useState } from 'react';
import { T } from './theme';
import { Card, Badge, Indicator, InfoRow, Btn } from './components';
import type { NodeRecord } from './types';
import { short, ago } from './utils';

interface Props {
  node: NodeRecord;
  onRemove: (nodeId: string) => void;
  isSelf: boolean;
}

export function NodeCard({ node, onRemove, isSelf }: Props) {
  const [confirmRemove, setConfirmRemove] = useState(false);

  return (
    <Card style={{ borderLeft: `2px solid ${node.online ? T.accent : T.danger}40` }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: T.textHi, fontFamily: T.display, marginBottom: 2 }}>
            {node.displayName || 'Unnamed node'}
            {isSelf && <span style={{ fontSize: 9, color: T.accent, marginLeft: 6, fontFamily: T.font }}>[THIS NODE]</span>}
          </div>
          <div style={{ fontSize: 10, color: T.textDim, fontFamily: T.font }}>{short(node.nodeId)}</div>
        </div>
        <Badge color={node.online ? T.accent : T.danger}>
          <Indicator color={node.online ? T.accent : T.danger} pulse={node.online}/>
          {node.online ? 'ONLINE' : 'OFFLINE'}
        </Badge>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 10 }}>
        <InfoRow label="Endpoints" value={node.endpoints?.join(', ') || '—'} />
        <InfoRow label="Capabilities" value={node.capabilities?.join(', ') || '—'} />
        <InfoRow label="WG pubkey" value={short(node.wireguardPubkey)} />
        <InfoRow label="Registered" value={ago(node.registeredAt)} />
      </div>

      {!isSelf && (
        <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
          {confirmRemove ? (
            <>
              <Btn small variant="ghost" onClick={() => setConfirmRemove(false)}>Cancel</Btn>
              <Btn small variant="danger" onClick={() => { onRemove(node.nodeId); setConfirmRemove(false); }}>
                Confirm Remove
              </Btn>
            </>
          ) : (
            <Btn small variant="danger" onClick={() => setConfirmRemove(true)}>Remove</Btn>
          )}
        </div>
      )}
    </Card>
  );
}