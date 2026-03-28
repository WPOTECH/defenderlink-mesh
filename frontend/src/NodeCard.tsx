import React from 'react';
import { T } from './theme';
import { Card, Badge, Indicator, InfoRow } from './components';
import type { NodeRecord } from './types';
import { short, ago } from './utils';

export function NodeCard({ node }: { node: NodeRecord }) {
  return (
    <Card style={{ borderLeft: `2px solid ${node.active ? T.accent : T.textDim}40` }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: T.textHi, fontFamily: T.display, marginBottom: 2 }}>
            {node.displayName || 'Unnamed node'}
          </div>
          <div style={{ fontSize: 10, color: T.textDim, fontFamily: T.font }}>{short(node.nodeId)}</div>
        </div>
        <Badge color={node.active ? T.accent : T.textDim}>
          <Indicator color={node.active ? T.accent : T.textDim} pulse={node.active} />
          {node.active ? 'ONLINE' : 'OFFLINE'}
        </Badge>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
        <InfoRow label="Endpoints" value={node.endpoints?.join(', ') || '\u2014'} />
        <InfoRow label="Capabilities" value={node.capabilities?.join(', ') || '\u2014'} />
        <InfoRow label="WG pubkey" value={short(node.wireguardPubkey)} />
        <InfoRow label="Registered" value={ago(node.registeredAt)} />
      </div>
    </Card>
  );
}
